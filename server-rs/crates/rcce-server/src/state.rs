//! Authoritative server state + packet dispatch.
//!
//! The dispatch here is the single source of truth shared by the binary's tick
//! loop (`main.rs`) and the end-to-end integration test, so the test exercises
//! the real handler path rather than a reimplementation.

use std::time::Instant;

use rcce_server_accounts::store::AccountStore;
use rcce_server_accounts::throttle::LoginThrottle;
use rcce_script::Host as _;
use rcce_server_core::{combat, ActorCatalog};

use crate::config::ServerConfig;
use crate::world::World;
use crate::{characters, login, world};

/// Squared interaction radius for right-click / examine (`Actors.bb:31`
/// `Const InteractDist = 400`, "radius of 20").
const INTERACT_DIST: f32 = 400.0;

/// First backpack inventory slot (`Inventories.bb:29` `SlotI_Backpack = 14`);
/// slots 0..13 are equipment/rings, 14..45 are the 32 backpack slots.
const SLOT_BACKPACK: usize = 14;

/// Clamp a world coordinate, rejecting NaN/Inf (parity with `ClampWorldCoord#`)
/// so a poisoned position can't be persisted into a dropped item + broadcast.
fn clamp_coord(v: f32) -> f32 {
    if v > -100_000.0 && v < 100_000.0 {
        v
    } else {
        0.0
    }
}

/// High tag bit marking a synthetic item-instance handle (see `equip_handle`),
/// so it can't be mistaken for a plain actor runtime id (which are < 65536).
const ITEM_HANDLE_TAG: i64 = 0x4000_0000;

/// An open script file handle (Blitz `OpenFile`/`WriteFile`/`AppendFile`/`ReadFile`).
/// The shipped scripts only do line-based text I/O (ReadLine/WriteLine/Eof), so a
/// read handle preloads the file as lines + a cursor, and a write/append handle
/// buffers output flushed atomically on `CloseFile`. Sandboxed to the
/// `Server Data/Script Files/` directory (Blitz `RCScriptFiles$`).
struct ScriptFile {
    /// Resolved on-disk path inside the script-files sandbox.
    path: std::path::PathBuf,
    /// Lines available to `ReadLine` (empty for a fresh `WriteFile`).
    lines: Vec<String>,
    /// Read cursor into `lines`.
    read_pos: usize,
    /// Pending output buffer; `Some` iff the handle was opened for write/append.
    /// Flushed to `path` atomically on close.
    out: Option<String>,
}

/// Split file content into `ReadLine`-able lines: break on `\n`, strip a
/// trailing `\r` (CRLF tolerance), and drop the spurious empty element a
/// trailing newline produces so `Eof` is reached right after the last real
/// line (matches Blitz `ReadLine`/`Eof`).
pub(crate) fn split_file_lines(content: &str) -> Vec<String> {
    let mut v: Vec<String> = content
        .split('\n')
        .map(|l| l.strip_suffix('\r').unwrap_or(l).to_string())
        .collect();
    if v.last().map(|s| s.is_empty()).unwrap_or(false) {
        v.pop();
    }
    v
}

/// A spell mid-memorisation (Blitz `MemorisingSpell`, `Spells.bb:17`). When
/// `RequireMemorise` is on, `P_SpellUpdate "M"` queues one of these; the spell
/// only lands in a free `MemorisedSpells` slot 6000 ms later (`Server.bb:720`).
struct PendingMemorise {
    /// The memorising player's peer id (resolved to char at commit, like Blitz
    /// re-checks `MS\AI` for a logout race).
    peer: u32,
    /// Known-spell number being memorised (the `"M"` payload's u16).
    spell_num: i16,
    /// `now_ms()` when the memorise was requested.
    created_ms: u64,
}

/// Blitz `ScriptPathIsSafe` (`ScriptingCommands.bb:1954`): reject empty, `..`
/// traversal, absolute paths, drive-colon, and control bytes.
pub(crate) fn script_path_is_safe(name: &str) -> bool {
    !(name.is_empty()
        || name.contains("..")
        || name.starts_with('\\')
        || name.starts_with('/')
        || (name.len() >= 2 && name.as_bytes()[1] == b':')
        || name.bytes().any(|c| c < 32 || c == 127))
}

/// Reject NaN/Inf and clamp to `±1e9` (parity with `ClampSaneFloat#`) — for
/// non-position floats like yaw (a NaN yaw poisons rotation matrices).
fn sane_float(v: f32) -> f32 {
    if v > -1.0e9 && v < 1.0e9 {
        v
    } else {
        0.0
    }
}

/// `(username, char slot)` of the live player on `peer`, if any.
fn sess_user_slot(world: &World, peer: u32) -> Option<(String, usize)> {
    world.session(peer).map(|s| (s.user.clone(), s.char_slot as usize))
}

/// Build a `P_GoldChange` packet: `"U" + amount` for a gain, `"D" + amount` for
/// a loss (`ServerNet.bb:929`).
fn gold_change_packet(delta: i32) -> Vec<u8> {
    let mut p = vec![if delta >= 0 { b'U' } else { b'D' }];
    p.extend_from_slice(&(delta.unsigned_abs()).to_le_bytes());
    p
}

/// Build a `P_StatUpdate "A"` value-update: `[u16 rid][u8 attrIdx][u16 value]`.
fn stat_update_a(rid: u16, idx: usize, value: i16) -> Vec<u8> {
    let mut s = vec![b'A'];
    s.extend_from_slice(&rid.to_le_bytes());
    s.push(idx as u8);
    s.extend_from_slice(&(value as u16).to_le_bytes());
    s
}

/// Where a reply packet goes.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Target {
    /// Back to the peer that sent the triggering message.
    Sender,
    /// To a specific peer (broadcasts: chat, combat effects, spawns).
    Peer(u32),
}

/// One outgoing packet: a target, a message type, and its payload.
#[derive(Clone, Debug)]
pub struct Outgoing {
    pub target: Target,
    pub msg_type: u8,
    pub payload: Vec<u8>,
}

impl Outgoing {
    pub fn sender(msg_type: u8, payload: Vec<u8>) -> Self {
        Self { target: Target::Sender, msg_type, payload }
    }
    pub fn peer(peer: u32, msg_type: u8, payload: Vec<u8>) -> Self {
        Self { target: Target::Peer(peer), msg_type, payload }
    }
}

/// Wrap sender-targeted `(type, payload)` replies as [`Outgoing`]s.
fn to_sender(replies: Vec<(u8, Vec<u8>)>) -> Vec<Outgoing> {
    replies.into_iter().map(|(t, p)| Outgoing::sender(t, p)).collect()
}

/// A content script running on its own thread (the async-wait model). Its
/// `BVM_*` calls are marshalled to the main loop via `cmd_rx`; a blocking
/// `GetWaitResult` (while a dialog is open) parks the held `waiting_reply` until
/// the player's `P_Dialog` response resumes it.
struct RunningScript {
    cmd_rx: std::sync::mpsc::Receiver<crate::scripts::ScriptMsg>,
    handle: Option<std::thread::JoinHandle<()>>,
    actor: u16,
    ctx: u16,
    privileged: bool,
    /// The player connection this script's actor belongs to (the default resume
    /// key + script-ownership).
    peer: u32,
    /// The peer whose response resumes the CURRENT wait. Equals `peer` for the
    /// normal case (a script dialogs its own player); set to a *different*
    /// player's peer when the script opens a dialog/input on another actor
    /// (e.g. the marriage priest asking the intended spouse) — that player's
    /// reply then resumes the script. Updated on each `RCE_SendDialogInput`/
    /// `RCE_SendInput`; defaults back to `peer`.
    wait_peer: u32,
    /// Whether the script has armed a wait (`SetWaiting(1)`), so the next
    /// `GetWaitResult` should block while `wait_result` is empty.
    waiting: bool,
    /// The stored `WaitResult` (the Blitz `SI\WaitResult$`). `GetWaitResult`
    /// returns this; it persists across reads until the next `SetWaiting(1)`
    /// arms a fresh wait. Set by a `P_Dialog` response (resume).
    wait_result: String,
    /// A response that arrived BEFORE the script reached its wait (the client can
    /// reply to a dialog packet before the script thread has run `SetWaiting`+
    /// `GetWaitResult`). Buffered here and applied by the next `GetWaitResult`, so
    /// the response isn't lost to the race (`SetWaiting` would otherwise clear it).
    pending_response: Option<String>,
    /// The held reply for a suspended `GetWaitResult`.
    waiting_reply: Option<std::sync::mpsc::Sender<rcce_script::Value>>,
    /// The script's `Param$` (`ThreadScript`'s 5th arg) — the comma-separated
    /// argument string `Parameter(n)` splits. Slash-commands pass the text after
    /// the command; most other spawns leave it empty.
    param: String,
    /// `WaitTime` duration (ms) + start (`SetWaitStart`, ms). When the script is
    /// parked and `now - wait_start >= wait_time`, the pump loop resumes it.
    wait_time: u64,
    wait_start: u64,
    /// `WaitKill` target runtime id (0 = none). The kill path resumes a script
    /// parked on this when the target dies.
    wait_kill: u16,
    /// `WaitSpeak` actor runtime id (0 = none) — resumed by the chat path when
    /// that actor speaks.
    wait_speak: u16,
    /// `WaitItem` `(actor rid, item name, amount)` — polled each pump; resumed
    /// once the actor holds the amount.
    wait_item: Option<(u16, String, i32)>,
}

/// An item lying on the ground (`DroppedItem`) — created by a drop, claimed by a
/// pickup. The `handle` is the wire id the client sends back to pick it up.
#[derive(Clone, Debug)]
struct DroppedItem {
    handle: u32,
    item: rcce_server_core::item::ItemInstance,
    amount: i16,
    x: f32,
    z: f32,
    area: String,
}

/// An item granted by `GiveItem` and awaiting the client's slot choice (`"G"`
/// give-protocol). Keyed by a minted handle; claimed by the client's `"G"` reply.
#[derive(Clone, Debug)]
struct AssignedItem {
    handle: u32,
    item_id: u16,
    amount: i16,
    peer: u32,
    /// `true` = a free `GiveItem` grant (claimable via the `"G"` reply);
    /// `false` = paid vendor stock (only obtainable through the buy path, so it
    /// can't be claimed for free).
    free: bool,
}

/// A timed attribute buff/debuff applied by a potion/ingredient (`ActorEffect`).
/// Its `deltas` are added to the owner's attributes on creation and subtracted
/// when it expires.
#[derive(Clone, Debug)]
struct ActorEffect {
    handle: u32,
    peer: u32,
    rid: u16,
    /// Effect name (for `AddActorEffect`/`DeleteActorEffect`/`ActorHasEffect`
    /// dedup + lookup); the potion path passes the item name.
    name: String,
    deltas: Vec<i16>,
    expire_at_ms: u64,
}

/// One side of a player↔player trade: the partner peer, the gold + inventory
/// slots this player is offering, and whether they've locked in their accept.
#[derive(Clone, Debug, Default)]
struct PlayerTrade {
    partner: u32,
    /// Signed gold reported in this player's accept packet (`+` = paying, `-` =
    /// receiving; both sides must mirror — `ServerNet.bb:964`).
    accept_gold: i32,
    /// Server-authoritative offered items `(inventory slot, amount)`
    /// (`TradeOfferedAmount`), accumulated from each `P_UpdateTrading`.
    item_offers: Vec<(usize, i16)>,
    accepted: bool,
}

/// Everything the single-threaded tick loop owns and mutates.
pub struct ServerState {
    pub config: ServerConfig,
    pub accounts: AccountStore,
    pub throttle: LoginThrottle,
    /// Actor race templates (`Actors.dat`) — for character creation + the world.
    pub catalog: ActorCatalog,
    /// Live-world sessions + runtime-id allocation.
    pub world: World,
    /// Live NPCs + per-area spawn population.
    pub spawns: crate::spawn::SpawnManager,
    /// Parsed `.rsl` content scripts (NPC dialog, quests, event hooks).
    pub scripts: crate::scripts::ScriptRegistry,
    /// Scripts currently executing on their own threads (async-wait / dialog).
    running_scripts: Vec<RunningScript>,
    /// Attribute slot used for Health (project-configured, default 0).
    pub health_stat: usize,
    /// Attribute slot used for Speed (project-configured, default 4).
    pub speed_stat: usize,
    /// Attribute slot used for Strength (project-configured, default 2).
    pub strength_stat: usize,
    /// Attribute slot used for Energy (stamina), if the project configures one —
    /// drives the run-stamina drain. `None` → no energy economy.
    pub energy_stat: Option<usize>,
    /// Attribute slot used for Breath, if configured — drives underwater drowning.
    pub breath_stat: Option<usize>,
    /// Per-peer "underwater since" timestamp (ms) — gates the 1 Hz breath drain.
    underwater_since: std::collections::HashMap<u32, u64>,
    /// Per-peer current trigger index (`LastTrigger`, -1 = none) — edge-detection
    /// so a proximity trigger fires once on entry.
    last_trigger: std::collections::HashMap<u32, i32>,
    /// Party roster: `peer → party_id` (absent = solo) + `party_id → member
    /// peers`. Formed by `/party <name>`; drives party XP-share + the party reads.
    party_of: std::collections::HashMap<u32, u32>,
    parties: std::collections::HashMap<u32, Vec<u32>>,
    next_party_id: u32,
    /// NPC AI-state value set by `SetActorAIState` (`runtime_id → mode`). The
    /// port derives AI behaviour from target/leader/range, so this round-trips
    /// for `ActorAIState` reads but doesn't itself drive movement.
    npc_aistate: std::collections::HashMap<u16, i32>,
    // Actor group / TeamID. The Blitz `Character\TeamID` has no field in the
    // port's flat-file Character, so the SetActorGroup/ActorGroup pair stores
    // it out-of-band here (rid -> group). No wire broadcast (Blitz sends none).
    actor_group: std::collections::HashMap<u16, i32>,
    // Open script-file handles (OpenFile/WriteFile/AppendFile/ReadFile → handle).
    script_files: std::collections::HashMap<i32, ScriptFile>,
    // Next handle to hand out (monotonic; never reuses a live handle).
    next_file_handle: i32,
    // Spells mid-memorisation (the 6s `MemorisingSpell` queue), committed in the
    // tick loop. Only used when `require_memorise` is on.
    pending_memorise: Vec<PendingMemorise>,
    // Cached `Area\PvP` flag by area name (loaded on first attack into the area),
    // so the player-vs-player target gate doesn't re-parse the area file.
    area_pvp_cache: std::collections::HashMap<String, bool>,
    /// Peers a script asked to kick (`KickPlayer`) — drained + disconnected by
    /// the tick loop.
    pending_kicks: Vec<u32>,
    /// In-game clock (`Environment.bb`): hour 0..23, minute 0..59, day, year.
    /// Advances one game-minute every `60000/time_factor` ms.
    game_time: (i32, i32, i32, i32),
    time_factor: i32,
    /// `now_ms()` of the last game-minute advance.
    last_minute_ms: u64,
    /// Attribute slot display names (`Attributes.dat`) — for `SetAttribute` /
    /// `GetAttribute` name→index resolution in scripts.
    pub attr_names: rcce_data::attributes::AttributeNames,
    /// Item definitions (`Items.dat`) — for `P_EatItem` (item type + use-script).
    pub items: rcce_data::items::ItemCatalog,
    /// Spell definitions (`Spells.dat`) — for `P_SpellUpdate` (cast → use-script).
    pub spells_catalog: rcce_data::spells::SpellCatalog,
    /// Projectile definitions (`Projectiles.dat`) — for `FireProjectile` visuals.
    pub projectiles: rcce_data::projectiles::ProjectileCatalog,
    /// Faction default-ratings grid (`Factions.dat`) — drives aggro targeting
    /// and the combat-engagement gate.
    pub factions: rcce_server_core::faction::FactionData,
    /// Damage-type names (`Damage.dat`) — for `SetResistance`/`Resistance`
    /// name→index resolution.
    pub damage_types: rcce_data::damage::DamageTypes,
    /// Localized slash-command words (`Language.txt`) for the chat-command dispatch.
    pub language: crate::language::Language,
    /// Lazily-loaded area files, cached for the per-tick portal check.
    area_cache: std::collections::HashMap<String, Option<rcce_server_core::area::Area>>,
    /// Per-area runtime weather: `(current weather byte 0..=5, countdown timer in
    /// tick_weather calls)`. Lazily created; mirrors Blitz `AreaInstance`
    /// `CurrentWeather`/`CurrentWeatherTime` (`ServerAreas.bb:70`).
    pub area_weather: std::collections::HashMap<String, (u8, i32)>,
    /// Items lying on the ground (drop/pickup), keyed by a minted wire handle.
    dropped_items: Vec<DroppedItem>,
    /// Next `DroppedItem` handle (monotonic; never 0).
    next_drop_handle: u32,
    /// Per-peer spell cooldowns: `(lastCastMs, spellId → readyAtMs)` — the
    /// `LastSpellFireMs` 100 ms floor + per-spell `SpellCharge`/`RechargeTime`.
    spell_cooldowns: std::collections::HashMap<u32, (u64, std::collections::HashMap<u16, u64>)>,
    /// Items granted by `GiveItem`, awaiting the client's slot choice.
    assigned_items: Vec<AssignedItem>,
    /// Peers currently in an NPC-vendor trade (`IsTrading == 1`).
    trading: std::collections::HashSet<u32>,
    /// Active timed attribute buffs (`ActorEffect`), reverted on expiry.
    active_effects: Vec<ActorEffect>,
    /// In-progress player↔player trades, keyed by each participant's peer.
    player_trades: std::collections::HashMap<u32, PlayerTrade>,
    /// `CombatDelay` ms between attacks (`Misc.dat`).
    pub combat_delay: i64,
    /// `CombatFormula` (1/2/3; `Misc.dat`).
    pub combat_formula: u8,
    /// `WeaponDamage` toggle (`Misc.dat` @12) — wear the attacker's equipped
    /// weapon by 1 durability on a 1-in-5 roll per swing, notifying the owner via
    /// `P_ItemHealth`. OFF in the shipped project.
    pub weapon_damage_on: bool,
    /// `ArmourDamage` toggle (`Misc.dat` @13) — wear the defender's equipped
    /// armour the same way. ON in the shipped project.
    pub armour_damage_on: bool,
    /// `RequireMemorise` (`Misc.dat`) — must a spell be memorised before casting?
    pub require_memorise: bool,
    /// Server-wide script globals (`SuperGlobals$`, 100 slots) — shared mutable
    /// state across all scripts/players.
    super_globals: Vec<String>,
    /// Server randomness (combat rolls, XP variance).
    pub rng: rcce_server_core::rng::Rng,
    /// Monotonic clock origin for the login throttle.
    start: Instant,
    /// In-memory account mutations not yet flushed to disk (e.g. combat XP).
    accounts_dirty: bool,
    /// Last periodic account flush.
    last_save: Instant,
}

impl ServerState {
    pub fn new(config: ServerConfig, accounts: AccountStore, catalog: ActorCatalog) -> Self {
        // Health/Speed attribute slots from Fixed Attributes.dat (shipped
        // default Health=0, Speed=4); fall back to those if absent/unparseable.
        let (health_stat, speed_stat, strength_stat, energy_stat, breath_stat) = std::fs::read(
            config.data_dir.join("Server Data").join("Fixed Attributes.dat"),
        )
        .ok()
        .and_then(|b| rcce_data::fixed_attributes::FixedAttributes::parse(&b).ok())
        .map(|fa| {
            (
                fa.health.unwrap_or(0) as usize,
                fa.speed.unwrap_or(4) as usize,
                fa.strength.unwrap_or(2) as usize,
                fa.energy.map(|e| e as usize),
                fa.breath.map(|b| b as usize),
            )
        })
        .unwrap_or((0, 4, 2, None, None));

        // Attribute slot names (Attributes.dat) for SetAttribute/GetAttribute
        // name lookups; empty (all-None) if absent → those BVMs soft-fail.
        let attr_names = std::fs::read(
            config.data_dir.join("Server Data").join("Attributes.dat"),
        )
        .ok()
        .and_then(|b| rcce_data::attributes::AttributeNames::parse(&b).ok())
        .unwrap_or_default();

        // Item catalog (Items.dat) — empty if absent → P_EatItem soft-fails.
        let items = std::fs::read(config.data_dir.join("Server Data").join("Items.dat"))
            .ok()
            .map(|b| rcce_data::items::ItemCatalog::parse(&b))
            .unwrap_or_default();

        // Spell catalog (Spells.dat) — empty if absent → P_SpellUpdate soft-fails.
        let spells_catalog = std::fs::read(config.data_dir.join("Server Data").join("Spells.dat"))
            .ok()
            .map(|b| rcce_data::spells::SpellCatalog::parse(&b))
            .unwrap_or_default();

        // Projectile catalog (Projectiles.dat) — empty → FireProjectile no-ops.
        let projectiles = std::fs::read(config.data_dir.join("Server Data").join("Projectiles.dat"))
            .ok()
            .map(|b| rcce_data::projectiles::ProjectileCatalog::parse(&b))
            .unwrap_or_default();

        // Faction grid (Factions.dat) — all-zero (hostile) default if absent,
        // matching Blitz's unset `Dim FactionDefaultRatings`.
        let factions = rcce_server_core::faction::FactionData::load(
            config.data_dir.join("Server Data").join("Factions.dat"),
        );

        // Damage-type names (Damage.dat) — empty if absent → resistance BVMs
        // soft-fail on the name lookup.
        let damage_types = std::fs::read(config.data_dir.join("Server Data").join("Damage.dat"))
            .ok()
            .map(|b| rcce_data::damage::DamageTypes::parse(&b))
            .unwrap_or_default();

        // Localized slash-command words (Language.txt) for chat-command dispatch.
        let language = crate::language::Language::load(
            config.data_dir.join("Server Data").join("Language.txt"),
        );

        // CombatDelay (i16 @9) + CombatFormula (u8 @11) + WeaponDamage (u8 @12) +
        // ArmourDamage (u8 @13) + RequireMemorise (u8 @21) from Misc.dat
        // (`Server.bb:255-266`).
        let (combat_delay, combat_formula, weapon_damage_on, armour_damage_on, require_memorise) =
            std::fs::read(config.data_dir.join("Server Data").join("Misc.dat"))
                .ok()
                .filter(|b| b.len() >= 22)
                .map(|b| {
                    (
                        i16::from_le_bytes([b[9], b[10]]) as i64,
                        b[11],
                        b[12] != 0,
                        b[13] != 0,
                        b[21] != 0,
                    )
                })
                .unwrap_or((1000, 1, false, false, false));

        let scripts = crate::scripts::ScriptRegistry::load(&config.data_dir).0;

        // Seed the game RNG from wall-clock nanos (non-cryptographic).
        let seed = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_nanos() as u64)
            .unwrap_or(0x1234_5678);

        Self {
            config,
            accounts,
            throttle: LoginThrottle::new(),
            catalog,
            world: World::new(),
            spawns: crate::spawn::SpawnManager::new(),
            scripts,
            running_scripts: Vec::new(),
            health_stat,
            speed_stat,
            strength_stat,
            energy_stat,
            breath_stat,
            underwater_since: std::collections::HashMap::new(),
            last_trigger: std::collections::HashMap::new(),
            party_of: std::collections::HashMap::new(),
            parties: std::collections::HashMap::new(),
            next_party_id: 1,
            npc_aistate: std::collections::HashMap::new(),
            actor_group: std::collections::HashMap::new(),
            script_files: std::collections::HashMap::new(),
            next_file_handle: 1,
            pending_memorise: Vec::new(),
            area_pvp_cache: std::collections::HashMap::new(),
            pending_kicks: Vec::new(),
            game_time: (12, 0, 0, 0), // noon, day 0 (Environment.bb default)
            time_factor: 10,
            last_minute_ms: 0,
            attr_names,
            items,
            spells_catalog,
            projectiles,
            factions,
            damage_types,
            language,
            area_cache: std::collections::HashMap::new(),
            area_weather: std::collections::HashMap::new(),
            dropped_items: Vec::new(),
            next_drop_handle: 1,
            spell_cooldowns: std::collections::HashMap::new(),
            assigned_items: Vec::new(),
            trading: std::collections::HashSet::new(),
            active_effects: Vec::new(),
            player_trades: std::collections::HashMap::new(),
            combat_delay,
            combat_formula,
            weapon_damage_on,
            armour_damage_on,
            require_memorise,
            super_globals: vec![String::new(); 100],
            rng: rcce_server_core::rng::Rng::new(seed),
            start: Instant::now(),
            accounts_dirty: false,
            last_save: Instant::now(),
        }
    }

    /// Flush accounts to disk if there are unsaved mutations and the save
    /// interval has elapsed. Returns true if it saved. Bounds data loss (combat
    /// XP, etc.) on an unexpected container stop to at most this interval.
    /// Force an immediate account save (`BVM_SAVESTATE`): sync live positions +
    /// write now, ignoring the periodic interval. Returns whether it saved.
    pub fn maybe_persist_force(&mut self) -> bool {
        for (peer, _) in self.world.session_snapshot() {
            self.persist_session_position(peer);
        }
        match self.accounts.save() {
            Ok(()) => {
                self.accounts_dirty = false;
                self.last_save = Instant::now();
                true
            }
            Err(e) => {
                eprintln!("[persist] SaveState save failed: {e}");
                false
            }
        }
    }

    pub fn maybe_persist(&mut self) -> bool {
        const SAVE_INTERVAL: std::time::Duration = std::time::Duration::from_secs(30);
        if self.accounts_dirty && self.last_save.elapsed() >= SAVE_INTERVAL {
            // Sync every online player's live position into their stored char so
            // the periodic save preserves location (not just the disconnect path).
            for (peer, _) in self.world.session_snapshot() {
                self.persist_session_position(peer);
            }
            match self.accounts.save() {
                Ok(()) => {
                    self.accounts_dirty = false;
                    self.last_save = Instant::now();
                    return true;
                }
                Err(e) => eprintln!("[persist] periodic account save failed: {e}"),
            }
        }
        false
    }

    /// Tear down a peer's world session when it disconnects. Returns the
    /// `P_ActorGone` packets to send to the departing player's same-area peers
    /// (computed before the session is removed). Frees the runtime id + clears
    /// the indices.
    pub fn on_disconnect(&mut self, peer_id: u32) -> Vec<(u32, u8, Vec<u8>)> {
        let mut out = Vec::new();
        // Leave any party + drop per-peer tick state.
        if let Some(pid) = self.party_of.remove(&peer_id) {
            // Recompute the roster after this peer leaves, then notify survivors.
            let survivors: Vec<u32> = if let Some(v) = self.parties.get_mut(&pid) {
                v.retain(|&m| m != peer_id);
                v.clone()
            } else {
                Vec::new()
            };
            if survivors.len() <= 1 {
                // A party of one dissolves (its lone member is unpartied).
                for &m in &survivors {
                    self.party_of.remove(&m);
                }
                self.parties.remove(&pid);
            }
            // Push each survivor its updated roster (a now-lone survivor gets an
            // empty roster, clearing their party panel) — parity with the Blitz
            // SendPartyUpdate fired on a member leaving.
            for &s in &survivors {
                let payload = self.party_update_payload_for(s, &survivors);
                out.push((s, world::P_PARTY_UPDATE, payload));
            }
        }
        self.underwater_since.remove(&peer_id);
        self.last_trigger.remove(&peer_id);
        if let Some(gone) = self.world.session(peer_id).cloned() {
            let payload = world::actor_gone_payload(gone.runtime_id);
            for (peer_b, _) in self.world.peers_in_area(&gone.area, peer_id) {
                out.push((peer_b, world::P_ACTOR_GONE, payload.clone()));
            }
        }
        // Revert active buffs BEFORE logout (revert needs the live session to
        // resolve the character) so a mid-buff disconnect doesn't bake the
        // temporary stat gain into the save.
        let mine: Vec<ActorEffect> =
            self.active_effects.iter().filter(|e| e.peer == peer_id).cloned().collect();
        self.active_effects.retain(|e| e.peer != peer_id);
        for e in &mine {
            let _ = self.revert_effect(e);
        }
        // Persist the player's live position + area into the stored character so
        // they reload where they logged out — movement/warp only mutate the
        // session, never the saved char (parity with the Blitz logout writeback).
        self.persist_session_position(peer_id);
        self.world.logout(peer_id);
        self.spell_cooldowns.remove(&peer_id);
        self.assigned_items.retain(|a| a.peer != peer_id);
        self.trading.remove(&peer_id);
        // Cancel any in-progress player trade this peer was part of.
        if let Some(t) = self.player_trades.remove(&peer_id) {
            self.player_trades.remove(&t.partner);
            out.push((t.partner, world::P_CLOSE_TRADING, Vec::new()));
        }
        out
    }

    /// Copy a peer's live session position + area into its stored character (so
    /// a logout/autosave preserves where the player actually is). No-op if the
    /// peer has no live session.
    fn persist_session_position(&mut self, peer_id: u32) {
        let Some(s) = self.world.session(peer_id).cloned() else {
            return;
        };
        if let Some(rec) = self
            .accounts
            .find_mut(&s.user)
            .and_then(|a| a.characters.get_mut(s.char_slot as usize))
        {
            rec.actor.x = s.x;
            rec.actor.y = s.y;
            rec.actor.z = s.z;
            rec.actor.area = s.area;
            self.accounts_dirty = true;
        }
    }

    /// The equipped weapon's `(damage, damageType)` (`SlotI_Weapon = 0`), if a
    /// non-broken weapon is equipped (`item_health > 0`, item type 1); else
    /// `None` (unarmed). Taken as the catalog + character so callers can hold a
    /// separate borrow of `accounts`.
    pub(crate) fn equipped_weapon(
        items: &rcce_data::items::ItemCatalog,
        c: &rcce_server_core::character::Character,
    ) -> Option<(i32, u8)> {
        let item = c.inventory.first()?.item.as_ref()?;
        if item.item_health == 0 {
            return None;
        }
        let def = items.get(item.item_id)?;
        if def.item_type != 1 {
            return None;
        }
        Some((def.weapon_damage as i32, def.weapon_damage_type as u8))
    }

    /// Equipped armour points — sum of armour pieces (item type 2, `item_health
    /// > 0`) in equipment slots 1..=7 (`GetArmourLevel`, `Inventories.bb:58`).
    pub(crate) fn equipped_armour(
        items: &rcce_data::items::ItemCatalog,
        c: &rcce_server_core::character::Character,
    ) -> i32 {
        let mut ap = 0;
        for slot in 1..=7usize {
            if let Some(Some(item)) = c.inventory.get(slot).map(|s| s.item.as_ref()) {
                if item.item_health > 0 {
                    if let Some(def) = items.get(item.item_id) {
                        if def.item_type == 2 {
                            ap += def.armour_level as i32;
                        }
                    }
                }
            }
        }
        ap
    }

    /// Decrement the durability of the item in `slot` of `c`'s inventory by 1,
    /// returning the new health, or `None` if the slot is empty or the item is
    /// already broken. Guards the `u8` against underflow — a wrap to 255 inside a
    /// server tick would be a silent corruption. Mirrors the combat-wear decrement
    /// `…\ItemHealth = …\ItemHealth - 1` (`GameServer.bb:541`/`561`).
    pub(crate) fn wear_item(
        c: &mut rcce_server_core::character::Character,
        slot: usize,
    ) -> Option<u8> {
        let item = c.inventory.get_mut(slot)?.item.as_mut()?;
        if item.item_health == 0 {
            return None;
        }
        item.item_health -= 1;
        Some(item.item_health)
    }

    /// One combat durability roll on `slot` of a player's character: if the slot
    /// holds an unbroken item, a 1-in-5 roll (`Rand(1,5)=1`, `GameServer.bb:540`/`560`)
    /// wears it by 1 and returns the new health; otherwise `None`. The RNG is only
    /// consulted when there is an unbroken item to wear (Blitz rolls *inside* the
    /// `<> Null` / `ItemHealth > 0` guards), so an empty slot never perturbs the
    /// shared combat RNG stream.
    fn try_wear(&mut self, user: &str, char_slot: usize, slot: usize) -> Option<u8> {
        let wearable = self
            .accounts
            .find(user)
            .and_then(|a| a.characters.get(char_slot))
            .and_then(|r| r.actor.inventory.get(slot))
            .and_then(|s| s.item.as_ref())
            .map(|it| it.item_health > 0)
            .unwrap_or(false);
        if !wearable || self.rng.range(1, 5) != 1 {
            return None;
        }
        let rec = self.accounts.find_mut(user)?.characters.get_mut(char_slot)?;
        Self::wear_item(&mut rec.actor, slot)
    }

    /// Handle `P_AttackActor` against an NPC: combat-delay gate → roll the melee
    /// formula → apply damage → emit the `"H"` damage feedback to the attacker,
    /// the `"O"` swing to same-area players, and on death `P_ActorDead` + XP +
    /// NPC removal. Player-vs-player and ranged/projectile attacks are deferred.
    /// `Area\PvP` for an area name, cached after the first (file) load.
    fn area_is_pvp(&mut self, area: &str) -> bool {
        if let Some(&v) = self.area_pvp_cache.get(area) {
            return v;
        }
        let v = rcce_server_core::area::Area::load(&self.config.data_dir, area)
            .map(|a| a.pvp != 0)
            .unwrap_or(false);
        self.area_pvp_cache.insert(area.to_string(), v);
        v
    }

    pub fn handle_attack(&mut self, peer: u32, payload: &[u8], now_ms: u64) -> Vec<Outgoing> {
        if payload.len() < 2 {
            return Vec::new();
        }
        let target_rid = u16::from_le_bytes([payload[0], payload[1]]);
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        // Combat-delay gate.
        if (now_ms.saturating_sub(sess.last_attack_ms) as i64) < self.combat_delay {
            return Vec::new();
        }
        // Attacker stats from the live character — needed by both the PvP and NPC
        // paths. Computed as owned values inside a block so the account borrow ends
        // before the mutating attack paths below.
        let (strength, weapon_damage, damage_type) = {
            let Some(acct) = self.accounts.find(&sess.user) else {
                return Vec::new();
            };
            let Some(rec) = acct.characters.get(sess.char_slot as usize) else {
                return Vec::new();
            };
            let actor = &rec.actor;
            let strength = actor.attributes.value.get(self.strength_stat).copied().unwrap_or(0) as i32;
            let default_dtype = self.catalog.get(actor.actor_id).map(|t| t.default_damage_type).unwrap_or(0);
            // Equipped weapon (slot 0) drives damage + its damage type; unarmed
            // falls back to strength-based damage + the race's default type.
            match Self::equipped_weapon(&self.items, actor) {
                Some((wd, wt)) => (strength, Some(wd), wt),
                None => (strength, None, default_dtype),
            }
        };

        // Player-vs-player: attacking another player is only allowed in a PvP area
        // (Blitz `If A2\RNID < 0 Or Area\PvP`, ServerNet.bb:1610). There the same
        // melee swing applies damage to the defender (`ActorAttack`) with the
        // attacker's AITarget set; outside a PvP area (or cross-area) it is refused.
        // Attacks on NPCs fall through to the NPC path below.
        if target_rid != sess.runtime_id {
            if let Some(tsess) = self.world.session_for_runtime(target_rid).cloned() {
                if tsess.area == sess.area && self.area_is_pvp(&sess.area) {
                    return self.pvp_attack(peer, &sess, &tsess, strength, weapon_damage, damage_type, now_ms);
                }
                return Vec::new();
            }
        }

        // Target must be a live NPC in the same area.
        let Some(npc) = self.spawns.npc(target_rid) else {
            return Vec::new();
        };
        if npc.area != sess.area {
            return Vec::new();
        }

        // Roll + resolve (unarmed / no armour for now — equipment is a follow-up).
        let rolls = combat::Rolls {
            to_hit: self.rng.rand(100) as u32,
            roll_5_8: self.rng.range(5, 8),
            roll_n5_5: self.rng.range(-5, 5),
            crit_roll: self.rng.rand(10) as u32,
        };
        let input = combat::SwingInput {
            strength,
            weapon_damage,
            armour: 0, // NPCs carry no equipped armour in the port (innate only)
            resistance: 100,
            toughness: None,
        };
        let swing = combat::melee_swing(self.combat_formula, &input, &rolls);
        self.world.set_last_attack(peer, now_ms);

        let damage = match swing {
            combat::SwingResult::Miss => -1,
            combat::SwingResult::Hit { damage, .. } => damage,
        };
        if damage > 0 {
            self.spawns.damage_npc(target_rid, damage);
        }
        // The NPC now retaliates against this attacker.
        self.spawns.set_target(target_rid, peer);
        // Record the attacker's own target (`AI\AITarget = A2`, ServerNet.bb:1612)
        // so `ActorTarget(player)` / `/assist` can read it.
        self.world.set_player_target(peer, target_rid);
        // If it's a defensive/aggressive NPC, rally nearby allies onto the
        // attacker (AICallForHelp, GameServer.bb:289-298).
        let attacked_aggr = self
            .spawns
            .npc(target_rid)
            .map(|n| n.actor_id)
            .and_then(|aid| self.catalog.get(aid))
            .map(|t| t.aggressiveness)
            .unwrap_or(0);
        if attacked_aggr == 1 || attacked_aggr == 2 {
            self.npc_call_for_help(target_rid, peer);
        }

        let mut out = Vec::new();
        // "H": damage feedback to the attacker — `[u16 targetRid][u16 dmg+1][u8 type]`.
        let mut h = vec![b'H'];
        h.extend_from_slice(&target_rid.to_le_bytes());
        h.extend_from_slice(&((damage + 1) as u16).to_le_bytes());
        h.push(damage_type);
        out.push(Outgoing::sender(world::P_ATTACK_ACTOR, h));
        // "O": the swing, to other same-area players — `[u16 attackerRid][u16 targetRid]`.
        let mut o = vec![b'O'];
        o.extend_from_slice(&sess.runtime_id.to_le_bytes());
        o.extend_from_slice(&target_rid.to_le_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if pb != peer && sb.area == sess.area {
                out.push(Outgoing::peer(pb, world::P_ATTACK_ACTOR, o.clone()));
            }
        }

        // Weapon durability wear (GameServer.bb:536-549): the attacker's equipped
        // weapon (SlotI_Weapon = 0) loses 1 durability on a 1-in-5 roll per swing
        // when the WeaponDamage toggle is on, notifying the owner via P_ItemHealth
        // (slot u8 + health u16). A broken (0-health) weapon is skipped and already
        // reads as unarmed (equipped_weapon → None). The NPC defender carries no
        // equipped armour in the port, so armour wear has no target on this path.
        if self.weapon_damage_on {
            if let Some(new_health) = self.try_wear(&sess.user, sess.char_slot as usize, 0) {
                self.accounts_dirty = true;
                let mut p = vec![0u8];
                p.extend_from_slice(&(new_health as u16).to_le_bytes());
                out.push(Outgoing::sender(world::P_ITEM_HEALTH, p));
            }
        }

        // Death.
        if self.spawns.npc(target_rid).map(|n| n.hp <= 0).unwrap_or(false) {
            let mut deaths =
                self.kill_npc(target_rid, sess.runtime_id, &sess.user, sess.char_slot as usize, peer);
            out.append(&mut deaths);
        }
        out
    }

    /// Player-vs-player melee (`ServerNet.bb:1610-1612`, `ActorAttack` on a player
    /// target in a PvP area): apply the attacker's swing to the defender player's
    /// HP (their equipped armour mitigates + wears; the attacker's weapon wears),
    /// send the `"H"`/`"Y"`/`"O"` feedback + the defender's HP update, and route a
    /// killing blow through the player Death path. Only reached for a same-area
    /// PvP attack (the caller checked `area_is_pvp`).
    fn pvp_attack(
        &mut self,
        peer: u32,
        sess: &crate::world::WorldSession,
        tsess: &crate::world::WorldSession,
        strength: i32,
        weapon_damage: Option<i32>,
        damage_type: u8,
        now_ms: u64,
    ) -> Vec<Outgoing> {
        let target_rid = tsess.runtime_id;
        // The defender's equipped armour mitigates the incoming hit.
        let defender_armour = match self
            .accounts
            .find(&tsess.user)
            .and_then(|a| a.characters.get(tsess.char_slot as usize))
        {
            Some(rec) => Self::equipped_armour(&self.items, &rec.actor),
            None => 0,
        };
        let rolls = combat::Rolls {
            to_hit: self.rng.rand(100) as u32,
            roll_5_8: self.rng.range(5, 8),
            roll_n5_5: self.rng.range(-5, 5),
            crit_roll: self.rng.rand(10) as u32,
        };
        let input = combat::SwingInput {
            strength,
            weapon_damage,
            armour: defender_armour,
            resistance: 100, // per-actor resistances unmodelled in the melee path (parity with collect_npc_attacks)
            toughness: None,
        };
        let swing = combat::melee_swing(self.combat_formula, &input, &rolls);
        self.world.set_last_attack(peer, now_ms);
        self.world.set_player_target(peer, target_rid);
        let damage = match swing {
            combat::SwingResult::Miss => -1,
            combat::SwingResult::Hit { damage, .. } => damage,
        };

        // Apply to the defender's HP (floors at 0).
        let mut new_hp = 0i32;
        let mut hp_before = 0i32;
        if let Some(rec) = self
            .accounts
            .find_mut(&tsess.user)
            .and_then(|a| a.characters.get_mut(tsess.char_slot as usize))
        {
            hp_before = rec.actor.attributes.value.get(self.health_stat).copied().unwrap_or(0) as i32;
            if damage > 0 {
                if let Some(hp) = rec.actor.attributes.value.get_mut(self.health_stat) {
                    *hp = (*hp as i32 - damage).max(0) as i16;
                }
            }
            new_hp = rec.actor.attributes.value.get(self.health_stat).copied().unwrap_or(0) as i32;
            self.accounts_dirty = true;
        }

        let defender_peer = self.world.peer_for_runtime(target_rid);
        let mut out = Vec::new();
        // "H": damage feedback to the attacker — `[u16 defenderRid][u16 dmg+1][u8 type]`.
        let mut h = vec![b'H'];
        h.extend_from_slice(&target_rid.to_le_bytes());
        h.extend_from_slice(&((damage + 1) as u16).to_le_bytes());
        h.push(damage_type);
        out.push(Outgoing::sender(world::P_ATTACK_ACTOR, h));
        // "Y": damage feedback to the defender — `[u16 attackerRid][u16 dmg+1][u8 type]`.
        if let Some(dp) = defender_peer {
            let mut y = vec![b'Y'];
            y.extend_from_slice(&sess.runtime_id.to_le_bytes());
            y.extend_from_slice(&((damage + 1) as u16).to_le_bytes());
            y.push(damage_type);
            out.push(Outgoing::peer(dp, world::P_ATTACK_ACTOR, y));
        }
        // HP update ("A") to everyone in the area; "O" swing to bystanders.
        let mut a = vec![b'A'];
        a.extend_from_slice(&target_rid.to_le_bytes());
        a.push(self.health_stat as u8);
        a.extend_from_slice(&(new_hp as u16).to_le_bytes());
        let mut o = vec![b'O'];
        o.extend_from_slice(&sess.runtime_id.to_le_bytes());
        o.extend_from_slice(&target_rid.to_le_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == sess.area {
                out.push(Outgoing::peer(pb, world::P_STAT_UPDATE, a.clone()));
                if pb != peer && Some(pb) != defender_peer {
                    out.push(Outgoing::peer(pb, world::P_ATTACK_ACTOR, o.clone()));
                }
            }
        }

        // Durability wear: the attacker's weapon (slot 0) + the defender's armour
        // (slots 1..=7), same toggles/roll as the PvE paths (GameServer.bb:536-570).
        if self.weapon_damage_on {
            if let Some(nh) = self.try_wear(&sess.user, sess.char_slot as usize, 0) {
                self.accounts_dirty = true;
                let mut p = vec![0u8];
                p.extend_from_slice(&(nh as u16).to_le_bytes());
                out.push(Outgoing::sender(world::P_ITEM_HEALTH, p));
            }
        }
        if self.armour_damage_on {
            for slot in 1..=7usize {
                if let Some(nh) = self.try_wear(&tsess.user, tsess.char_slot as usize, slot) {
                    self.accounts_dirty = true;
                    if let Some(dp) = defender_peer {
                        let mut p = vec![slot as u8];
                        p.extend_from_slice(&(nh as u16).to_le_bytes());
                        out.push(Outgoing::peer(dp, world::P_ITEM_HEALTH, p));
                    }
                }
            }
        }

        // Killing blow (alive → 0 this swing) → the player Death path: clear NPC
        // targets on the corpse + fire the Death script (victim = Actor(), attacker
        // = ContextActor()), mirroring collect_npc_attacks. The Death script handles
        // respawn (HP restore + Warp).
        if hp_before > 0 && new_hp <= 0 {
            if let Some(dp) = defender_peer {
                self.spawns.clear_targets_on_peer(dp);
                self.fire_hook_async("Death", "Main", target_rid, sess.runtime_id, dp);
            }
        }
        out
    }

    /// Kill an NPC: broadcast `P_ActorDead` to its area, award XP to the killer
    /// (a player; NPC level treated as 0 → diff floored to 1), fire its
    /// `SpawnDeathScript$` (`killer = Actor()`, NPC = `ContextActor()`), and
    /// remove it from the world. Shared by melee (`handle_attack`) and
    /// script/spell damage that drops an NPC to 0 HP.
    fn kill_npc(
        &mut self,
        npc_rid: u16,
        killer_rid: u16,
        killer_user: &str,
        killer_slot: usize,
        killer_peer: u32,
    ) -> Vec<Outgoing> {
        let mut out = Vec::new();
        let Some(npc) = self.spawns.npc(npc_rid) else {
            return out;
        };
        let npc_actor_id = npc.actor_id;
        let area = npc.area.clone();
        let death_script = npc.death_script.clone();

        // P_ActorDead to same-area peers.
        let mut dead = Vec::new();
        dead.extend_from_slice(&npc_rid.to_le_bytes());
        dead.extend_from_slice(&killer_rid.to_le_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area {
                out.push(Outgoing::peer(pb, world::P_ACTOR_DEAD, dead.clone()));
            }
        }

        // XP to the killer (if a live player character).
        let killer_level = self
            .accounts
            .find(killer_user)
            .and_then(|a| a.characters.get(killer_slot))
            .map(|r| r.actor.level as i32)
            .unwrap_or(0);
        let xpmult = self.catalog.get(npc_actor_id).map(|t| t.xp_multiplier).unwrap_or(0);
        let diff = (0 - killer_level).max(1);
        let xp = diff * xpmult + self.rng.range(0, 20);
        // Route through GiveXP so a kill also notifies the player (P_XPUpdate)
        // and fires the LevelUp script — previously XP was added silently.
        let mut xp_pkts = self.give_xp(killer_rid, xp);
        out.append(&mut xp_pkts);

        self.spawns.remove_npc(npc_rid);
        self.world.clear_riders_of(npc_rid);
        self.world.free_runtime(npc_rid);
        self.world.forget_runtime(npc_rid);
        // Resume any script that was WaitKill-ing this actor.
        self.resume_kill_waits(npc_rid);
        if !death_script.is_empty() {
            self.fire_hook_async(&death_script, "Main", killer_rid, npc_rid, killer_peer);
        }
        out
    }

    /// `BVM_CHANGEACTOR` (`ScriptingCommands.bb:456`): morph an actor to a
    /// different template id (validated against the catalog); force gender for
    /// single-gender races; broadcast `P_AppearanceUpdate "C"` (`[u16 rid][u16
    /// newId]`) to the area so observers reload the mesh. Privileged (caller
    /// gates). Works on players + NPCs.
    fn change_actor(&mut self, rid: u16, new_actor_id: u16) -> Vec<Outgoing> {
        let Some(template) = self.catalog.get(new_actor_id) else {
            return Vec::new();
        };
        let genders = template.genders;
        // Apply the morph (player char or NPC).
        let area = if let Some(peer) = self.world.peer_for_runtime(rid) {
            let Some((u, s, area)) = self
                .world
                .session(peer)
                .map(|x| (x.user.clone(), x.char_slot as usize, x.area.clone()))
            else {
                return Vec::new();
            };
            if let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
                rec.actor.actor_id = new_actor_id;
                if genders == 2 && rec.actor.gender != 1 {
                    rec.actor.gender = 1;
                } else if (genders == 1 || genders == 3) && rec.actor.gender != 0 {
                    rec.actor.gender = 0;
                }
                self.accounts_dirty = true;
            }
            area
        } else if let Some(npc) = self.spawns.npc(rid) {
            let area = npc.area.clone();
            self.spawns.set_npc_actor(rid, new_actor_id);
            area
        } else {
            return Vec::new();
        };
        let mut p = vec![b'C'];
        p.extend_from_slice(&rid.to_le_bytes());
        p.extend_from_slice(&new_actor_id.to_le_bytes());
        self.broadcast_area(&area, world::P_APPEARANCE_UPDATE, &p)
    }

    /// `BVM_KILLACTOR` (`ScriptingCommands.bb:443`): kill an actor. An NPC routes
    /// through [`kill_npc`] (XP to the killer if a player). A **player** has HP
    /// zeroed + broadcast, NPC targets cleared, and the `Death` script fired
    /// (Actor = victim, context = killer) — the same path as a combat killing
    /// blow. Privileged (caller gates).
    fn kill_actor(&mut self, target_rid: u16, killer_rid: u16) -> Vec<Outgoing> {
        // NPC target → the existing NPC death path.
        if self.spawns.npc(target_rid).is_some() {
            let (ku, ks, kp) = match self.world.peer_for_runtime(killer_rid) {
                Some(p) => {
                    let (u, s) = self.player_loc(killer_rid).unwrap_or_default();
                    (u, s, p)
                }
                None => (String::new(), 0, 0),
            };
            return self.kill_npc(target_rid, killer_rid, &ku, ks, kp);
        }
        // Player target.
        let Some(peer) = self.world.peer_for_runtime(target_rid) else {
            return Vec::new();
        };
        let Some((u, s, area)) = self
            .world
            .session(peer)
            .map(|x| (x.user.clone(), x.char_slot as usize, x.area.clone()))
        else {
            return Vec::new();
        };
        let hp_before = match self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
            Some(rec) => {
                let before = rec.actor.attributes.value.get(self.health_stat).copied().unwrap_or(0);
                if let Some(h) = rec.actor.attributes.value.get_mut(self.health_stat) {
                    *h = 0;
                }
                self.accounts_dirty = true;
                before
            }
            None => return Vec::new(),
        };
        let mut out = Vec::new();
        let mut a = vec![b'A'];
        a.extend_from_slice(&target_rid.to_le_bytes());
        a.push(self.health_stat as u8);
        a.extend_from_slice(&0u16.to_le_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area {
                out.push(Outgoing::peer(pb, world::P_STAT_UPDATE, a.clone()));
            }
        }
        // Run the Death path once (only if it was alive).
        if hp_before > 0 {
            self.spawns.clear_targets_on_peer(peer);
            self.fire_hook_async("Death", "Main", target_rid, killer_rid, peer);
        }
        out
    }

    /// Apply an absolute HP value to an NPC (script `SetAttribute(npc,"Health",x)`),
    /// clamp to `[0,max]`, broadcast the `P_StatUpdate "A"`, and run the kill path
    /// if it drops to 0. `killer_peer` resolves the killer's character for XP.
    fn npc_set_health(&mut self, npc_rid: u16, new_hp: i32, killer_rid: u16, killer_peer: u32) -> Vec<Outgoing> {
        let mut out = Vec::new();
        let Some((area, hp_max)) = self.spawns.npc(npc_rid).map(|n| (n.area.clone(), n.hp_max)) else {
            return out;
        };
        let clamped = new_hp.clamp(0, hp_max.max(0));
        self.spawns.set_npc_hp(npc_rid, clamped);

        // "A" health update to same-area players.
        let mut a = vec![b'A'];
        a.extend_from_slice(&npc_rid.to_le_bytes());
        a.push(self.health_stat as u8);
        a.extend_from_slice(&(clamped as u16).to_le_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area {
                out.push(Outgoing::peer(pb, world::P_STAT_UPDATE, a.clone()));
            }
        }

        if clamped <= 0 {
            let (ku, ks) = self
                .world
                .session(killer_peer)
                .map(|s| (s.user.clone(), s.char_slot as usize))
                .unwrap_or_default();
            let mut deaths = self.kill_npc(npc_rid, killer_rid, &ku, ks, killer_peer);
            out.append(&mut deaths);
        }
        out
    }

    /// The peers in `peer`'s party (including itself), or just `[peer]` if solo.
    fn party_members(&self, peer: u32) -> Vec<u32> {
        match self.party_of.get(&peer) {
            Some(pid) => self.parties.get(pid).cloned().unwrap_or_else(|| vec![peer]),
            None => vec![peer],
        }
    }

    /// A peer's in-game character name (`ActorInstance\Name$`) — the display name
    /// the party roster shows, or empty if the peer has no live session/character.
    fn player_name(&self, peer: u32) -> String {
        let Some(sess) = self.world.session(peer) else {
            return String::new();
        };
        self.accounts
            .find(&sess.user)
            .and_then(|a| a.characters.get(sess.char_slot as usize))
            .map(|r| r.actor.name.clone())
            .unwrap_or_default()
    }

    /// Build the `P_PartyUpdate` payload for one `member`: each OTHER member's
    /// name as `[len u8][name]` (Blitz excludes self via `j <> i`, `SendPartyUpdate`
    /// `ServerNet.bb:3128-3130`). An empty payload (the member is now alone) clears
    /// that player's roster panel.
    fn party_update_payload_for(&self, member: u32, members: &[u32]) -> Vec<u8> {
        let mut p = Vec::new();
        for &other in members {
            if other == member {
                continue;
            }
            let name = self.player_name(other);
            let nb = name.as_bytes();
            let n = nb.len().min(255);
            p.push(n as u8);
            p.extend_from_slice(&nb[..n]);
        }
        p
    }

    /// Send every member of party `pid` its current roster (`SendPartyUpdate`,
    /// `ServerNet.bb:3121`); called whenever the membership changes.
    fn send_party_update(&self, pid: u32) -> Vec<Outgoing> {
        let Some(members) = self.parties.get(&pid) else {
            return Vec::new();
        };
        members
            .iter()
            .map(|&m| Outgoing::peer(m, world::P_PARTY_UPDATE, self.party_update_payload_for(m, members)))
            .collect()
    }

    /// `/party <name>`: put the inviter and the named same-area player into one
    /// party (immediate add — the Blitz invite/accept handshake is simplified to
    /// a direct join; noted in PARITY.md). Returns a chat-feedback packet.
    fn join_party(&mut self, inviter_peer: u32, target_name: &str) -> Vec<Outgoing> {
        let Some(isess) = self.world.session(inviter_peer).cloned() else {
            return Vec::new();
        };
        // Find a same-area player by character name.
        let target = self.world.session_snapshot().into_iter().find(|(p, s)| {
            *p != inviter_peer
                && s.area == isess.area
                && self
                    .accounts
                    .find(&s.user)
                    .and_then(|a| a.characters.get(s.char_slot as usize))
                    .map(|r| r.actor.name.eq_ignore_ascii_case(target_name))
                    .unwrap_or(false)
        });
        let Some((target_peer, _)) = target else {
            let mut p = vec![254u8];
            p.extend_from_slice(format!("No nearby player named '{target_name}'.").as_bytes());
            return vec![Outgoing::peer(inviter_peer, world::P_CHAT_MESSAGE, p)];
        };
        // Use the inviter's party if it has one, else mint a new one.
        let pid = match self.party_of.get(&inviter_peer).copied() {
            Some(pid) => pid,
            None => {
                let pid = self.next_party_id;
                self.next_party_id += 1;
                self.party_of.insert(inviter_peer, pid);
                self.parties.insert(pid, vec![inviter_peer]);
                pid
            }
        };
        // Move the target into that party.
        if let Some(old) = self.party_of.insert(target_peer, pid) {
            if let Some(v) = self.parties.get_mut(&old) {
                v.retain(|&m| m != target_peer);
            }
        }
        self.parties.entry(pid).or_default().push(target_peer);
        // Broadcast the new roster to every member (inviter + joiner + any prior
        // members) — SendPartyUpdate (ServerNet.bb:3121).
        let mut out = self.send_party_update(pid);
        let mut p = vec![253u8];
        p.extend_from_slice(format!("You are now partied with {target_name}.").as_bytes());
        out.push(Outgoing::peer(inviter_peer, world::P_CHAT_MESSAGE, p));
        out
    }

    /// Grant XP to a player actor (`GiveXP`, `GameServer.bb:57`): add to the
    /// character, notify the player (`P_XPUpdate "M"` + 4-byte amount), and fire
    /// the privileged `LevelUp` script (which applies level-ups via
    /// `SetActorLevel`). Returns the `P_XPUpdate` packet, if the actor is a live
    /// player. Party-sharing is unported (no party system yet — noted in
    /// PARITY.md); negative-XP is allowed (Blitz adds unbounded).
    fn give_xp(&mut self, rid: u16, xp: i32) -> Vec<Outgoing> {
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return Vec::new();
        };
        // Party share: split among same-area party members (GiveXP, IgnoreParty=0).
        let area = self.world.session(peer).map(|s| s.area.clone());
        let in_area: Vec<u32> = self
            .party_members(peer)
            .into_iter()
            .filter(|&m| self.world.session(m).map(|s| Some(&s.area) == area.as_ref()).unwrap_or(false))
            .collect();
        if in_area.len() > 1 {
            let n = in_area.len() as i32;
            let share = xp / n;
            let remainder = xp % n;
            let mut out = Vec::new();
            for &m in &in_area {
                let m_xp = if m == peer { share + remainder } else { share };
                if let Some(mr) = self.world.session(m).map(|s| s.runtime_id) {
                    out.extend(self.give_xp_solo(mr, m_xp));
                }
            }
            return out;
        }
        self.give_xp_solo(rid, xp)
    }

    /// Grant XP to a single player (no party split) — the inner of [`give_xp`].
    fn give_xp_solo(&mut self, rid: u16, xp: i32) -> Vec<Outgoing> {
        let mut out = Vec::new();
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return out;
        };
        let Some((u, s)) = self
            .world
            .session(peer)
            .map(|sess| (sess.user.clone(), sess.char_slot as usize))
        else {
            return out;
        };
        match self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
            Some(rec) => {
                rec.actor.xp += xp;
                self.accounts_dirty = true;
            }
            None => return out,
        }
        let mut p = vec![b'M'];
        p.extend_from_slice(&xp.to_le_bytes());
        out.push(Outgoing::peer(peer, world::P_XP_UPDATE, p));
        // The LevelUp script is allowlisted → runs privileged → may SetActorLevel.
        self.fire_hook_async("LevelUp", "Main", rid, 0, peer);
        out
    }

    /// `(level, actor_id)` for a runtime id — a player (from its character) or an
    /// NPC (level 0, template id). `(0, 0)` if neither. Used by `GiveKillXP` to
    /// read the slain actor's level + XP-multiplier source.
    fn actor_level_and_template(&self, rid: u16) -> (i32, u16) {
        if let Some(peer) = self.world.peer_for_runtime(rid) {
            if let Some((u, s)) = self
                .world
                .session(peer)
                .map(|sess| (sess.user.clone(), sess.char_slot as usize))
            {
                if let Some(rec) = self.accounts.find(&u).and_then(|a| a.characters.get(s)) {
                    return (rec.actor.level as i32, rec.actor.actor_id);
                }
            }
        }
        if let Some(npc) = self.spawns.npc(rid) {
            return (0, npc.actor_id);
        }
        (0, 0)
    }

    /// `SetActorLevel` (`ScriptingCommands.bb:2118`): zero the player's XP, set
    /// its level, notify the player (`P_XPUpdate "U"` + 2-byte level) and the
    /// other same-area players (`"L"` + rid + level). Player-only.
    fn set_actor_level(&mut self, rid: u16, level: i16) -> Vec<Outgoing> {
        let mut out = Vec::new();
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return out;
        };
        let Some((u, s, area)) = self
            .world
            .session(peer)
            .map(|sess| (sess.user.clone(), sess.char_slot as usize, sess.area.clone()))
        else {
            return out;
        };
        match self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
            Some(rec) => {
                rec.actor.xp = 0;
                rec.actor.level = level;
                self.accounts_dirty = true;
            }
            None => return out,
        }
        let mut p = vec![b'U'];
        p.extend_from_slice(&level.to_le_bytes());
        out.push(Outgoing::peer(peer, world::P_XP_UPDATE, p));
        let mut l = vec![b'L'];
        l.extend_from_slice(&rid.to_le_bytes());
        l.extend_from_slice(&level.to_le_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area && pb != peer {
                out.push(Outgoing::peer(pb, world::P_XP_UPDATE, l.clone()));
            }
        }
        out
    }

    /// Resolve a runtime id to its player `(user, char_slot)`, if it's a live
    /// player session.
    fn player_loc(&self, rid: u16) -> Option<(String, usize)> {
        let peer = self.world.peer_for_runtime(rid)?;
        let sess = self.world.session(peer)?;
        Some((sess.user.clone(), sess.char_slot as usize))
    }

    /// `BVM_SETFACTIONRATING`/`CHANGEFACTIONRATING` (`ScriptingCommands.bb:1812`):
    /// set or adjust a player's rating toward a named faction (stored `value+100`,
    /// clamped 0..200). Returns whether it applied. Player-only.
    fn set_faction_rating(&mut self, rid: u16, faction: &str, value: i32, is_delta: bool) -> bool {
        let Some(idx) = self.factions.index_of(faction) else {
            return false;
        };
        let Some((u, s)) = self.player_loc(rid) else {
            return false;
        };
        let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) else {
            return false;
        };
        if let Some(slot) = rec.actor.faction_ratings.get_mut(idx) {
            let newv = if is_delta { *slot as i32 + value } else { value + 100 };
            *slot = newv.clamp(0, 200) as u8;
            self.accounts_dirty = true;
        }
        true
    }

    /// `BVM_SETHOMEFACTION` (`ScriptingCommands.bb:1856`): set a player's home
    /// faction by name. Player-only.
    fn set_home_faction(&mut self, rid: u16, faction: &str) {
        let Some(idx) = self.factions.index_of(faction) else {
            return;
        };
        let Some((u, s)) = self.player_loc(rid) else {
            return;
        };
        if let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
            rec.actor.home_faction = idx as u8;
            self.accounts_dirty = true;
        }
    }

    /// `BVM_FACTIONRATING` (`ScriptingCommands.bb:1883`): a player's rating toward
    /// a named faction, as the script sees it (stored value − 100).
    fn faction_rating(&self, rid: u16, faction: &str) -> i64 {
        let Some(idx) = self.factions.index_of(faction) else {
            return 0;
        };
        let Some((u, s)) = self.player_loc(rid) else {
            return 0;
        };
        self.accounts
            .find(&u)
            .and_then(|a| a.characters.get(s))
            .and_then(|rec| rec.actor.faction_ratings.get(idx))
            .map(|&v| v as i64 - 100)
            .unwrap_or(0)
    }

    /// `BVM_HOMEFACTION$` (`ScriptingCommands.bb:1873`): a player's home faction name.
    fn home_faction_name(&self, rid: u16) -> String {
        let Some((u, s)) = self.player_loc(rid) else {
            return String::new();
        };
        self.accounts
            .find(&u)
            .and_then(|a| a.characters.get(s))
            .map(|rec| self.factions.name(rec.actor.home_faction as usize).to_string())
            .unwrap_or_default()
    }

    // ----- Script file-stream subsystem (BVM_OPENFILE/READFILE/WRITEFILE/… + the
    // Blitz-core stream ops ReadLine/WriteLine/Eof/CloseFile the interpreter routes
    // to host.call). Sandboxed to `Server Data/Script Files/`. Every op soft-fails
    // (returns 0/empty / no-op) on a bad path or IO error — never panics. -----

    /// Resolve a sandboxed script-file path, or `None` if the name is unsafe.
    fn script_file_path(&self, name: &str) -> Option<std::path::PathBuf> {
        if !script_path_is_safe(name) {
            return None;
        }
        let base = self.config.data_dir.join("Server Data").join("Script Files");
        Some(base.join(name.replace('\\', "/")))
    }

    /// Open a script file for reading (`OpenFile`/`ReadFile`): load its lines.
    /// Returns a new handle, or 0 if missing/unsafe.
    fn open_script_read(&mut self, name: &str) -> i64 {
        let Some(path) = self.script_file_path(name) else { return 0 };
        let Ok(content) = std::fs::read_to_string(&path) else { return 0 };
        let h = self.next_file_handle;
        self.next_file_handle += 1;
        self.script_files.insert(
            h,
            ScriptFile { path, lines: split_file_lines(&content), read_pos: 0, out: None },
        );
        h as i64
    }

    /// `WriteFile` (truncate) / `AppendFile` (preload existing). Returns a new
    /// write handle, or 0 on unsafe path (or, for append, a missing file —
    /// matching Blitz `AppendFile`'s OpenFile-returns-0 bail).
    fn open_script_write(&mut self, name: &str, append: bool) -> i64 {
        let Some(path) = self.script_file_path(name) else { return 0 };
        let buf = if append {
            match std::fs::read_to_string(&path) {
                Ok(c) => c,
                Err(_) => return 0,
            }
        } else {
            String::new()
        };
        let h = self.next_file_handle;
        self.next_file_handle += 1;
        self.script_files.insert(
            h,
            ScriptFile { path, lines: Vec::new(), read_pos: 0, out: Some(buf) },
        );
        h as i64
    }

    /// `CloseFile`: flush a write/append buffer atomically (temp + rename, parity
    /// with `SafeWriteCommit`) and drop the handle.
    fn close_script_file(&mut self, handle: i64) {
        if let Some(f) = self.script_files.remove(&(handle as i32)) {
            if let Some(buf) = f.out {
                let tmp = f.path.with_extension("scriptwrite.tmp");
                if std::fs::write(&tmp, buf.as_bytes()).is_ok() {
                    let _ = std::fs::rename(&tmp, &f.path);
                } else {
                    let _ = std::fs::remove_file(&tmp);
                }
            }
        }
    }

    /// Synthetic item-instance handle for `(rid, slot)` if that inventory slot
    /// holds an item, else 0. The port has no item-handle registry, so equipment
    /// reads (`ActorWeapon`) encode `(rid, slot)` into a **tagged** handle that
    /// the item-property reads (`ItemName`/`ItemValue`/…) decode — distinguishable
    /// from a plain actor runtime id by the high tag bit.
    fn equip_handle(&self, rid: u16, slot: usize) -> i64 {
        let Some((u, s)) = self.player_loc(rid) else {
            return 0;
        };
        let has = self
            .accounts
            .find(&u)
            .and_then(|a| a.characters.get(s))
            .and_then(|rec| rec.actor.inventory.get(slot))
            .map(|sl| sl.item.is_some())
            .unwrap_or(false);
        if has {
            ITEM_HANDLE_TAG | ((rid as i64) << 8) | (slot as i64)
        } else {
            0
        }
    }

    /// Decode an item handle from [`equip_handle`] → `(item template id, instance
    /// health)`, or `None` if not an item handle / the slot is empty.
    fn item_at_handle(&self, handle: i64) -> Option<(u16, u8)> {
        if handle & ITEM_HANDLE_TAG == 0 {
            return None;
        }
        let rid = ((handle >> 8) & 0xFFFF) as u16;
        let slot = (handle & 0xFF) as usize;
        let (u, s) = self.player_loc(rid)?;
        let rec = self.accounts.find(&u).and_then(|a| a.characters.get(s))?;
        let item = rec.actor.inventory.get(slot)?.item.as_ref()?;
        Some((item.item_id, item.item_health))
    }

    /// `BVM_SETITEMHEALTH` (`ScriptingCommands.bb:938`): set the per-instance
    /// durability of the item referenced by `handle`. Privileged (caller gates) —
    /// Param1 is an item handle, so full-priv (not self-or-priv).
    fn set_item_health(&mut self, handle: i64, value: i32) {
        if handle & ITEM_HANDLE_TAG == 0 {
            return;
        }
        let rid = ((handle >> 8) & 0xFFFF) as u16;
        let slot = (handle & 0xFF) as usize;
        let Some((u, s)) = self.player_loc(rid) else {
            return;
        };
        if let Some(item) = self
            .accounts
            .find_mut(&u)
            .and_then(|a| a.characters.get_mut(s))
            .and_then(|rec| rec.actor.inventory.get_mut(slot))
            .and_then(|sl| sl.item.as_mut())
        {
            item.item_health = value.clamp(0, 255) as u8;
            self.accounts_dirty = true;
        }
    }

    /// `BVM_HASITEM` (`ScriptingCommands.bb:3002`): whether a player holds at
    /// least `count` of the named item across their inventory.
    fn has_item(&self, rid: u16, item_name: &str, count: i32) -> bool {
        let Some(item_id) = self
            .items
            .items
            .iter()
            .find(|i| i.name.eq_ignore_ascii_case(item_name))
            .map(|i| i.id)
        else {
            return false;
        };
        let Some((u, s)) = self.player_loc(rid) else {
            return false;
        };
        let total: i32 = self
            .accounts
            .find(&u)
            .and_then(|a| a.characters.get(s))
            .map(|rec| {
                rec.actor
                    .inventory
                    .iter()
                    .filter(|sl| sl.item.as_ref().map(|it| it.item_id == item_id).unwrap_or(false))
                    .map(|sl| sl.amount as i32)
                    .sum()
            })
            .unwrap_or(0);
        total >= count.max(1)
    }

    /// Damage-type index for a name (case-insensitive).
    fn damage_type_index(&self, name: &str) -> Option<usize> {
        self.damage_types.names.iter().position(|n| n.eq_ignore_ascii_case(name))
    }

    /// `BVM_SETRESISTANCE` (`ScriptingCommands.bb:2352`): set a player's
    /// resistance to a named damage type. Player-only. Privileged (caller gates).
    fn set_resistance(&mut self, rid: u16, dmg_name: &str, value: i32) {
        let Some(idx) = self.damage_type_index(dmg_name) else {
            return;
        };
        let Some((u, s)) = self.player_loc(rid) else {
            return;
        };
        if let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
            if let Some(slot) = rec.actor.resistances.get_mut(idx) {
                *slot = value as i16;
                self.accounts_dirty = true;
            }
        }
    }

    /// `BVM_RESISTANCE` (`ScriptingCommands.bb:2371`): a player's resistance to a
    /// named damage type.
    fn resistance(&self, rid: u16, dmg_name: &str) -> i64 {
        let Some(idx) = self.damage_type_index(dmg_name) else {
            return 0;
        };
        let Some((u, s)) = self.player_loc(rid) else {
            return 0;
        };
        self.accounts
            .find(&u)
            .and_then(|a| a.characters.get(s))
            .and_then(|rec| rec.actor.resistances.get(idx))
            .map(|&v| v as i64)
            .unwrap_or(0)
    }

    /// Whether a named area is outdoors (cached) — `BVM_ACTOROUTDOORS` /
    /// `BVM_ZONEOUTDOORS`.
    fn area_outdoors(&mut self, name: &str) -> bool {
        if !self.area_cache.contains_key(name) {
            let loaded = rcce_server_core::area::Area::load(&self.config.data_dir, name);
            self.area_cache.insert(name.to_string(), loaded);
        }
        self.area_cache
            .get(name)
            .and_then(|a| a.as_ref())
            .map(|a| a.outdoors != 0)
            .unwrap_or(false)
    }

    /// Resolve a spell by name → `(id, recharge_ms, canonical_name)`.
    fn spell_by_name(&self, name: &str) -> Option<(u16, i32, String)> {
        self.spells_catalog
            .spells
            .iter()
            .find(|s| s.name.eq_ignore_ascii_case(name))
            .map(|s| (s.id, s.recharge_time, s.name.clone()))
    }

    /// Whether a player knows a spell (any slot with `level > 0` and the matching
    /// id). The `name → id` lookup is the caller's; this compares by id.
    fn ability_known(&self, rid: u16, spell_id: u16) -> bool {
        let Some((u, s)) = self.player_loc(rid) else {
            return false;
        };
        self.accounts
            .find(&u)
            .and_then(|a| a.characters.get(s))
            .map(|rec| {
                rec.actor
                    .spell_levels
                    .iter()
                    .zip(&rec.actor.known_spells)
                    .any(|(&lv, &id)| lv > 0 && id == spell_id as i16)
            })
            .unwrap_or(false)
    }

    /// `AddSpell` / `BVM_ADDABILITY` (`Actors.bb:1447`): grant a spell at `lvl`
    /// into a free slot if not already known; broadcast `P_KnownSpellUpdate "A"`.
    /// Returns the broadcast packet(s). The thumbnail-tex/description fields the
    /// port's spell parser discards are sent as 0/empty (the spell is castable;
    /// only the client toolbar art is incomplete — same gap as P_FetchCharacter).
    fn add_ability(&mut self, rid: u16, name: &str, lvl: i16) -> Vec<Outgoing> {
        let mut out = Vec::new();
        let lvl = if lvl <= 0 { 1 } else { lvl };
        let Some((spell_id, recharge, sname)) = self.spell_by_name(name) else {
            return out;
        };
        let Some((u, s)) = self.player_loc(rid) else {
            return out;
        };
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return out;
        };
        let added = self
            .accounts
            .find_mut(&u)
            .and_then(|a| a.characters.get_mut(s))
            .map(|rec| {
                let known = rec
                    .actor
                    .spell_levels
                    .iter()
                    .zip(&rec.actor.known_spells)
                    .any(|(&lv, &id)| lv > 0 && id == spell_id as i16);
                if known {
                    return false;
                }
                if let Some(i) = rec.actor.spell_levels.iter().position(|&lv| lv <= 0) {
                    rec.actor.known_spells[i] = spell_id as i16;
                    rec.actor.spell_levels[i] = lvl;
                    true
                } else {
                    false
                }
            })
            .unwrap_or(false);
        if added {
            self.accounts_dirty = true;
            let mut p = vec![b'A'];
            p.extend_from_slice(&(lvl as u16).to_le_bytes());
            p.extend_from_slice(&spell_id.to_le_bytes());
            p.extend_from_slice(&0u16.to_le_bytes()); // thumbnail tex (not retained)
            p.extend_from_slice(&(recharge as u16).to_le_bytes());
            p.extend_from_slice(&(sname.len() as u16).to_le_bytes());
            p.extend_from_slice(sname.as_bytes());
            p.extend_from_slice(&0u16.to_le_bytes()); // description (not retained)
            p.push(0);
            out.push(Outgoing::peer(peer, world::P_KNOWN_SPELL_UPDATE, p));
        }
        out
    }

    /// `BVM_SETABILITYLEVEL` (`ScriptingCommands.bb:1650`): set a known spell's
    /// level; broadcast `P_KnownSpellUpdate "L"` + `[i32 lvl][name]`. Privileged
    /// (caller gates). Returns the broadcast packet(s).
    fn set_ability_level(&mut self, rid: u16, name: &str, lvl: i16) -> Vec<Outgoing> {
        let mut out = Vec::new();
        let Some((spell_id, _, sname)) = self.spell_by_name(name) else {
            return out;
        };
        let Some((u, s)) = self.player_loc(rid) else {
            return out;
        };
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return out;
        };
        let found = self
            .accounts
            .find_mut(&u)
            .and_then(|a| a.characters.get_mut(s))
            .map(|rec| {
                if let Some(i) = rec
                    .actor
                    .spell_levels
                    .iter()
                    .zip(&rec.actor.known_spells)
                    .position(|(&lv, &id)| lv > 0 && id == spell_id as i16)
                {
                    rec.actor.spell_levels[i] = lvl;
                    true
                } else {
                    false
                }
            })
            .unwrap_or(false);
        if found {
            self.accounts_dirty = true;
            let mut p = vec![b'L'];
            p.extend_from_slice(&(lvl as i32).to_le_bytes());
            p.extend_from_slice(sname.as_bytes());
            out.push(Outgoing::peer(peer, world::P_KNOWN_SPELL_UPDATE, p));
        }
        out
    }

    /// `DeleteSpell` / `BVM_DELETEABILITY` (`Actors.bb:1475`): strip a known
    /// spell (clear its slot + any memorise pointing at it); broadcast
    /// `P_KnownSpellUpdate "D"` + name. Privileged (caller gates).
    fn delete_ability(&mut self, rid: u16, name: &str) -> Vec<Outgoing> {
        let mut out = Vec::new();
        let Some((spell_id, _, sname)) = self.spell_by_name(name) else {
            return out;
        };
        let Some((u, s)) = self.player_loc(rid) else {
            return out;
        };
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return out;
        };
        let removed = self
            .accounts
            .find_mut(&u)
            .and_then(|a| a.characters.get_mut(s))
            .map(|rec| {
                if let Some(i) = rec
                    .actor
                    .spell_levels
                    .iter()
                    .zip(&rec.actor.known_spells)
                    .position(|(&lv, &id)| lv > 0 && id == spell_id as i16)
                {
                    rec.actor.known_spells[i] = 0;
                    rec.actor.spell_levels[i] = 0;
                    // Clear a memorise slot pointing at this known-spell slot.
                    for m in rec.actor.memorised_spells.iter_mut() {
                        if *m == i as i16 {
                            *m = 5000;
                        }
                    }
                    true
                } else {
                    false
                }
            })
            .unwrap_or(false);
        if removed {
            self.accounts_dirty = true;
            let mut p = vec![b'D'];
            p.extend_from_slice(sname.as_bytes());
            out.push(Outgoing::peer(peer, world::P_KNOWN_SPELL_UPDATE, p));
        }
        out
    }

    /// `BVM_ADDACTOREFFECT` (`ScriptingCommands.bb:1431`): apply a named timed
    /// effect that shifts one attribute by `value` for `length_secs`. An existing
    /// same-name effect on the actor is reverted first (re-apply). Player-only
    /// (the effect machinery is session-based). Ungated (matches Blitz).
    fn add_actor_effect(
        &mut self,
        rid: u16,
        name: &str,
        attr_name: &str,
        value: i32,
        length_secs: i32,
        icon: u16,
    ) -> Vec<Outgoing> {
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return Vec::new();
        };
        let Some(idx) = self.attr_names.index_of(attr_name) else {
            return Vec::new();
        };
        let mut out = Vec::new();
        // Re-apply: revert any existing same-name effect on this actor first.
        if let Some(pos) = self
            .active_effects
            .iter()
            .position(|e| e.peer == peer && e.name.eq_ignore_ascii_case(name))
        {
            let old = self.active_effects.remove(pos);
            out.extend(self.revert_effect(&old));
        }
        let mut deltas = vec![0i16; 40];
        if let Some(slot) = deltas.get_mut(idx) {
            *slot = value as i16;
        }
        let dur = (length_secs.max(0) as u64) * 1000;
        out.extend(self.apply_effect(rid, peer, name.to_string(), deltas, dur, icon));
        out
    }

    /// `BVM_DELETEACTOREFFECT` (`ScriptingCommands.bb:1476`): revert + remove a
    /// named effect on the actor. Player-only. Ungated.
    fn delete_actor_effect(&mut self, rid: u16, name: &str) -> Vec<Outgoing> {
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return Vec::new();
        };
        if let Some(pos) = self
            .active_effects
            .iter()
            .position(|e| e.peer == peer && e.name.eq_ignore_ascii_case(name))
        {
            let e = self.active_effects.remove(pos);
            return self.revert_effect(&e);
        }
        Vec::new()
    }

    /// `BVM_ACTORHASEFFECT` (`ScriptingCommands.bb:1503`): 1 if the actor has a
    /// live named effect.
    fn actor_has_effect(&self, rid: u16, name: &str) -> bool {
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return false;
        };
        self.active_effects
            .iter()
            .any(|e| e.peer == peer && e.name.eq_ignore_ascii_case(name))
    }

    /// The area an actor (player or NPC) is currently in.
    fn actor_area(&self, rid: u16) -> Option<String> {
        if let Some(peer) = self.world.peer_for_runtime(rid) {
            if let Some(s) = self.world.session(peer) {
                return Some(s.area.clone());
            }
        }
        self.spawns.npc(rid).map(|n| n.area.clone())
    }

    /// `BVM_MOVEACTOR` (`ScriptingCommands.bb:815`): teleport an actor (player or
    /// NPC) to `(x,y,z)` and broadcast `P_RepositionActor "M"` to its area. Coords
    /// clamped. Returns the broadcast packets.
    fn move_actor(&mut self, rid: u16, x: f32, y: f32, z: f32, flag: u8) -> Vec<Outgoing> {
        let (x, y, z) = (clamp_coord(x), clamp_coord(y), clamp_coord(z));
        let area = if let Some(peer) = self.world.peer_for_runtime(rid) {
            self.world.move_session(peer, x, y, z)
        } else if self.spawns.npc(rid).is_some() {
            self.spawns.set_npc_pos(rid, x, y, z);
            self.spawns.npc(rid).map(|n| n.area.clone())
        } else {
            None
        };
        let Some(area) = area else {
            return Vec::new();
        };
        let wire = world::reposition_move_payload(rid, x, y, z, flag);
        self.broadcast_area(&area, world::P_REPOSITION_ACTOR, &wire)
    }

    /// `BVM_ROTATEACTOR` (`ScriptingCommands.bb:795`): broadcast a new yaw for an
    /// actor as `P_RepositionActor "R"`. Yaw is sanitised; the server doesn't
    /// store yaw (it's render-only), so this is broadcast-only.
    fn rotate_actor(&mut self, rid: u16, yaw: f32) -> Vec<Outgoing> {
        let Some(area) = self.actor_area(rid) else {
            return Vec::new();
        };
        let wire = world::reposition_rotate_payload(rid, sane_float(yaw));
        self.broadcast_area(&area, world::P_REPOSITION_ACTOR, &wire)
    }

    /// `BVM_SETACTORDESTINATION` (`ScriptingCommands.bb:716`): set an actor's
    /// movement destination (player session or NPC wander target). No broadcast
    /// (the per-tick movement relay carries it). Coords clamped.
    fn set_actor_destination(&mut self, rid: u16, x: f32, z: f32) {
        let (x, z) = (clamp_coord(x), clamp_coord(z));
        if let Some(peer) = self.world.peer_for_runtime(rid) {
            self.world.set_session_dest(peer, x, z);
        } else if self.spawns.npc(rid).is_some() {
            self.spawns.set_npc_dest(rid, x, z);
        }
    }

    /// Broadcast `(msg_type, body)` to every player in `area`.
    fn broadcast_area(&self, area: &str, msg_type: u8, body: &[u8]) -> Vec<Outgoing> {
        self.world
            .session_snapshot()
            .into_iter()
            .filter(|(_, s)| s.area == area)
            .map(|(p, _)| Outgoing::peer(p, msg_type, body.to_vec()))
            .collect()
    }

    /// Whether `name` resolves to a loadable area (cached). Used to gate
    /// script-driven spawns to real zones (Blitz's `For Ar = Each Area` match).
    fn area_exists(&mut self, name: &str) -> bool {
        if !self.area_cache.contains_key(name) {
            let loaded = rcce_server_core::area::Area::load(&self.config.data_dir, name);
            self.area_cache.insert(name.to_string(), loaded);
        }
        self.area_cache.get(name).map(|a| a.is_some()).unwrap_or(false)
    }

    /// `BVM_SPAWN` (`ScriptingCommands.bb:746`): create a live NPC of template
    /// `actor_id` at `(x,y,z)` in `area`, with the given right-click / death
    /// scripts. Returns its runtime id (handle), or 0 if the template or area is
    /// unknown. The NPC is introduced to same-area players by the next
    /// `collect_world_broadcasts`. Ungated (matches Blitz). Coords are clamped.
    fn spawn_scripted_npc(
        &mut self,
        actor_id: u16,
        area: &str,
        pos: (f32, f32, f32),
        script: String,
        death_script: String,
    ) -> u16 {
        let (x, y, z) = pos;
        let (hp, hp_max) = match self.catalog.get(actor_id) {
            Some(t) => (
                t.attr_value.get(self.health_stat).copied().unwrap_or(1) as i32,
                t.attr_maximum.get(self.health_stat).copied().unwrap_or(1) as i32,
            ),
            None => return 0,
        };
        if !self.area_exists(area) {
            return 0;
        }
        let rid = self.world.alloc_runtime();
        self.spawns.add_npc(crate::spawn::NpcActor {
            runtime_id: rid,
            actor_id,
            area: area.to_string(),
            x: clamp_coord(x),
            y: clamp_coord(y),
            z: clamp_coord(z),
            hp,
            hp_max,
            target_peer: None,
            last_attack_ms: 0,
            script,
            death_script,
            stock: Vec::new(),
        });
        rid
    }

    /// `BVM_SPAWNITEM` (`ScriptingCommands.bb:494`): drop `amount` of the item
    /// named `item_name` on the ground at `(x,y,z)` in `area` and broadcast
    /// `P_InventoryUpdate "D"` to same-area players (the same ground-item flow as
    /// a player drop). No-op for an unknown item/area or non-positive amount.
    /// Ungated. Returns the broadcast packets.
    fn spawn_ground_item(
        &mut self,
        item_name: &str,
        amount: i16,
        area: &str,
        x: f32,
        y: f32,
        z: f32,
    ) -> Vec<Outgoing> {
        let mut out = Vec::new();
        if amount <= 0 {
            return out;
        }
        let Some(item_id) = self
            .items
            .items
            .iter()
            .find(|i| i.name.eq_ignore_ascii_case(item_name))
            .map(|i| i.id)
        else {
            return out;
        };
        if !self.area_exists(area) {
            return out;
        }
        let (x, y, z) = (clamp_coord(x), clamp_coord(y), clamp_coord(z));
        let item = rcce_server_core::ItemInstance::new(item_id);
        let handle = self.next_drop_handle;
        self.next_drop_handle = self.next_drop_handle.wrapping_add(1).max(1);
        self.dropped_items.push(DroppedItem {
            handle,
            item: item.clone(),
            amount,
            x,
            z,
            area: area.to_string(),
        });
        let mut p = vec![b'D'];
        p.extend_from_slice(&(amount as u16).to_le_bytes());
        p.extend_from_slice(&x.to_le_bytes());
        p.extend_from_slice(&y.to_le_bytes());
        p.extend_from_slice(&z.to_le_bytes());
        p.extend_from_slice(&handle.to_le_bytes());
        p.extend_from_slice(&item.to_wire_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area {
                out.push(Outgoing::peer(pb, world::P_INVENTORY_UPDATE, p.clone()));
            }
        }
        out
    }

    /// Run a script event hook: look up `script` and run its `func` with the
    /// given actor / context-actor runtime ids, returning the packets the
    /// script's commands produced (e.g. `Output` → `P_ChatMessage`).
    pub fn fire_hook(&mut self, script: &str, func: &str, actor_rid: u16, ctx_rid: u16) -> Vec<Outgoing> {
        // Link in the script's `Using` modules (RC_Core std-lib etc.) so its
        // helper calls resolve. Owned, so the borrow of `self.scripts` ends here.
        let Some(prog) = self.scripts.linked_program(script) else {
            return Vec::new();
        };
        let privileged = self.scripts.is_privileged(script);
        let mut host = crate::scripts::ScriptHost {
            world: &self.world,
            accounts: &mut self.accounts,
            spawns: &self.spawns,
            catalog: &self.catalog,
            attr_names: &self.attr_names,
            rng: &mut self.rng,
            actor: actor_rid as i64,
            ctx: ctx_rid as i64,
            privileged,
            dirty: false,
            out: Vec::new(),
        };
        rcce_script::run_function(&prog, &mut host, func, Vec::new());
        let dirty = host.dirty;
        let out = host.out;
        if dirty {
            self.accounts_dirty = true;
        }
        out
    }

    /// Fire a script hook **asynchronously** on its own thread — for scripts
    /// that may block on player input (dialog/quests). The script's `BVM_*`
    /// calls are pumped + executed by [`pump_scripts`](Self::pump_scripts) each
    /// tick; its outgoing packets surface there, not here.
    pub fn fire_hook_async(&mut self, script: &str, func: &str, actor_rid: u16, ctx_rid: u16, peer: u32) {
        let Some(prog) = self.scripts.linked_program(script) else {
            return;
        };
        // Engine-initiated spawn → the allowlist may elevate privilege.
        let privileged = self.scripts.is_privileged(script);
        self.spawn_program(prog, func, (actor_rid, ctx_rid, peer), privileged, String::new());
    }

    /// Spawn a linked program on its own thread + register it as a running script
    /// with explicit privilege + `Param$`. `ids` is `(actor, ctx, peer)`. Shared
    /// by `fire_hook_async` (allowlist privilege) and `BVM_THREADEXECUTE` (caller's
    /// privilege, no re-elevation).
    fn spawn_program(
        &mut self,
        prog: rcce_script::ast::Program,
        func: &str,
        ids: (u16, u16, u32),
        privileged: bool,
        param: String,
    ) {
        let (actor_rid, ctx_rid, peer) = ids;
        let func = func.to_string();
        let (tx, rx) = std::sync::mpsc::channel();
        let handle = std::thread::spawn(move || {
            let mut host = crate::scripts::MarshalHost { tx };
            rcce_script::run_function(&prog, &mut host, &func, Vec::new());
            let _ = host.tx.send(crate::scripts::ScriptMsg::Done);
        });
        self.running_scripts.push(RunningScript {
            cmd_rx: rx,
            handle: Some(handle),
            actor: actor_rid,
            ctx: ctx_rid,
            privileged,
            peer,
            wait_peer: peer,
            waiting: false,
            wait_result: String::new(),
            pending_response: None,
            waiting_reply: None,
            param,
            wait_time: 0,
            wait_start: 0,
            wait_kill: 0,
            wait_speak: 0,
            wait_item: None,
        });
    }

    /// Test-only: start an **inline** RSL program (parsed from source) through the
    /// same async pump path as [`fire_hook_async`], so pump-intercepted BVMs
    /// (`Spawn`, `Warp`, `GiveXP`, …) can be exercised without a shipped `.rsl`.
    #[cfg(test)]
    pub fn start_inline_script(
        &mut self,
        src: &str,
        func: &str,
        actor_rid: u16,
        ctx_rid: u16,
        peer: u32,
        privileged: bool,
    ) {
        let prog = rcce_script::parser::parse(src).expect("inline test script parses");
        let func = func.to_string();
        let (tx, rx) = std::sync::mpsc::channel();
        let handle = std::thread::spawn(move || {
            let mut host = crate::scripts::MarshalHost { tx };
            rcce_script::run_function(&prog, &mut host, &func, Vec::new());
            let _ = host.tx.send(crate::scripts::ScriptMsg::Done);
        });
        self.running_scripts.push(RunningScript {
            cmd_rx: rx,
            handle: Some(handle),
            actor: actor_rid,
            ctx: ctx_rid,
            privileged,
            peer,
            wait_peer: peer,
            waiting: false,
            wait_result: String::new(),
            pending_response: None,
            waiting_reply: None,
            param: String::new(),
            wait_time: 0,
            wait_start: 0,
            wait_kill: 0,
            wait_speak: 0,
            wait_item: None,
        });
    }

    /// Test-only: set the most-recently-started script's `Param$` (the
    /// slash-command path sets it from the command's argument text).
    #[cfg(test)]
    pub fn set_last_script_param(&mut self, param: &str) {
        if let Some(rs) = self.running_scripts.last_mut() {
            rs.param = param.to_string();
        }
    }

    /// Drive all running scripts: execute their pending `BVM_*` calls against
    /// the live world, returning the packets they produced. A script that hits a
    /// blocking `GetWaitResult` is parked (its reply held) until
    /// [`resume_script`](Self::resume_script). Finished scripts are reaped.
    pub fn pump_scripts(&mut self) -> Vec<Outgoing> {
        use crate::scripts::ScriptMsg;
        let mut out = Vec::new();
        // WaitTime: resume scripts parked on a timer whose duration has elapsed.
        let now = self.now_ms();
        // Commit any spell memorisations whose 6 s timer has elapsed.
        self.commit_memorise(now);
        for rs in self.running_scripts.iter_mut() {
            if rs.waiting_reply.is_some()
                && rs.wait_time > 0
                && now.saturating_sub(rs.wait_start) >= rs.wait_time
            {
                if let Some(reply) = rs.waiting_reply.take() {
                    rs.wait_result = "1".to_string();
                    let _ = reply.send(rcce_script::Value::Str("1".to_string()));
                    rs.wait_time = 0;
                }
            }
        }
        // WaitItem: resume parked scripts whose actor now holds the required item.
        let item_checks: Vec<(usize, u16, String, i32)> = self
            .running_scripts
            .iter()
            .enumerate()
            .filter_map(|(idx, rs)| {
                rs.waiting_reply
                    .is_some()
                    .then(|| rs.wait_item.as_ref().map(|(r, n, a)| (idx, *r, n.clone(), *a)))
                    .flatten()
            })
            .collect();
        for (idx, rid, name, amt) in item_checks {
            if self.has_item(rid, &name, amt) {
                if let Some(reply) = self.running_scripts[idx].waiting_reply.take() {
                    self.running_scripts[idx].wait_result = "1".to_string();
                    let _ = reply.send(rcce_script::Value::Str("1".to_string()));
                    self.running_scripts[idx].wait_item = None;
                }
            }
        }
        let mut i = 0;
        while i < self.running_scripts.len() {
            let mut finished = false;
            // Bounded per-tick work so one script can't starve the loop.
            for _ in 0..100_000 {
                match self.running_scripts[i].cmd_rx.try_recv() {
                    Ok(ScriptMsg::Call { name, args, reply }) => {
                        let lname = name.to_lowercase();
                        // Cross-player dialog routing: every RC_Core dialog/input
                        // helper that arms a wait (`OpenDialog`/`DialogOutput`/
                        // `DialogInput`/`Input`) takes the TARGET actor as arg 1,
                        // then waits for that player's reply. Record that player's
                        // peer so their response — not the script owner's — resumes
                        // it. Equals the owner for the normal self-dialog case, so
                        // existing single-player dialogs are unaffected; only a
                        // dialog opened on ANOTHER player (the marriage priest
                        // asking the intended spouse) differs.
                        if matches!(
                            lname.as_str(),
                            "rce_sendopendialog"
                                | "rce_senddialogoutput"
                                | "rce_senddialoginput"
                                | "rce_sendinput"
                        ) {
                            let owner = self.running_scripts[i].peer;
                            let arnid = args.get(1).map(|v| v.to_int()).unwrap_or(0) as u16;
                            self.running_scripts[i].wait_peer =
                                self.world.peer_for_runtime(arnid).unwrap_or(owner);
                        }
                        // The wait commands touch per-script wait state (held in
                        // RunningScript), so they're handled here, not in ScriptHost.
                        match lname.as_str() {
                            // Parameter(n): the n-th comma-separated field of this
                            // script's Param$ (per-script state, like the waits).
                            "parameter" => {
                                let n = args.first().map(|v| v.to_int()).unwrap_or(0).max(0) as usize;
                                let field = self.running_scripts[i]
                                    .param
                                    .split(',')
                                    .nth(n)
                                    .unwrap_or("")
                                    .trim()
                                    .to_string();
                                let _ = reply.send(rcce_script::Value::Str(field));
                                continue;
                            }
                            // Event-wait primitives (per-script state). WaitTime
                            // (timer) + WaitKill (kill event) drive a resume; the
                            // others set their fields (Speak/Item hooks deferred).
                            "setwaittime" => {
                                self.running_scripts[i].wait_time = args.first().map(|v| v.to_int()).unwrap_or(0).max(0) as u64;
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "setwaitstart" => {
                                self.running_scripts[i].wait_start = args.first().map(|v| v.to_int()).unwrap_or(0).max(0) as u64;
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "setwaitkill" => {
                                self.running_scripts[i].wait_kill = args.get(1).map(|v| v.to_int()).unwrap_or(0) as u16;
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "setwaitresult" => {
                                self.running_scripts[i].wait_result =
                                    args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "setwaitspeak" => {
                                self.running_scripts[i].wait_speak = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "setwaititem" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let amount = args.get(2).map(|v| v.to_int()).unwrap_or(1) as i32;
                                self.running_scripts[i].wait_item = Some((rid, name, amount));
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "setwaitinfo" => {
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "setwaiting" => {
                                let rs = &mut self.running_scripts[i];
                                rs.waiting = args.first().map(|v| v.to_int()).unwrap_or(0) != 0;
                                if rs.waiting {
                                    rs.wait_result.clear(); // arm a fresh wait
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "getwaitresult" => {
                                let rs = &mut self.running_scripts[i];
                                if rs.wait_result.is_empty() {
                                    // Apply a response that arrived before the wait
                                    // (the race fix) — once, to this wait.
                                    if let Some(p) = rs.pending_response.take() {
                                        rs.wait_result = p;
                                    }
                                }
                                if !rs.wait_result.is_empty() {
                                    let _ = reply.send(rcce_script::Value::Str(rs.wait_result.clone()));
                                } else if rs.waiting {
                                    rs.waiting_reply = Some(reply); // suspend
                                    break;
                                } else {
                                    let _ = reply.send(rcce_script::Value::Str(String::new()));
                                }
                                continue;
                            }
                            // NPC-target attribute commands: NPCs live in
                            // `spawns` (mutated here, not in the immutable-world
                            // ScriptHost). A read returns the NPC's HP; a write to
                            // Health applies damage + the kill path. Player-target
                            // attribute commands fall through to ScriptHost below.
                            "attribute" | "getattribute" | "maxattribute"
                                if self
                                    .spawns
                                    .npc(args.first().map(|v| v.to_int()).unwrap_or(0) as u16)
                                    .is_some() =>
                            {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let npc = self.spawns.npc(rid).unwrap();
                                let val = if name.eq_ignore_ascii_case("Health") {
                                    if lname == "maxattribute" { npc.hp_max } else { npc.hp }
                                } else {
                                    0
                                };
                                let _ = reply.send(rcce_script::Value::Int(val as i64));
                                continue;
                            }
                            "setattribute" | "changeattribute"
                                if self
                                    .spawns
                                    .npc(args.first().map(|v| v.to_int()).unwrap_or(0) as u16)
                                    .is_some() =>
                            {
                                // Privileged-gated like the player path; only NPC
                                // Health is mutable (NPCs track HP, not the full
                                // attribute set).
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                    if name.eq_ignore_ascii_case("Health") {
                                        let arg2 = args.get(2).map(|v| v.to_int()).unwrap_or(0) as i32;
                                        let cur = self.spawns.npc(rid).map(|n| n.hp).unwrap_or(0);
                                        let new_hp = if lname == "changeattribute" { cur + arg2 } else { arg2 };
                                        let (killer_rid, killer_peer) = {
                                            let rs = &self.running_scripts[i];
                                            (rs.actor, rs.peer)
                                        };
                                        let mut pkts = self.npc_set_health(rid, new_hp, killer_rid, killer_peer);
                                        out.append(&mut pkts);
                                    }
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // Progression: GiveXP / GiveKillXP / SetActorLevel
                            // mutate the player AND fire the LevelUp script /
                            // broadcast P_XPUpdate, so they're handled here (not
                            // the immutable-world ScriptHost). All privileged —
                            // parity with the gated BVM_SETACTORLEVEL (XP grants
                            // are an equivalent-effect bypass).
                            "givexp" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let xp = args.get(1).map(|v| v.to_int()).unwrap_or(0) as i32;
                                    let mut pkts = self.give_xp(rid, xp);
                                    out.append(&mut pkts);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "givekillxp" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let killed = args.get(1).map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let recv_level = self.actor_level_and_template(rid).0;
                                    let (killed_level, killed_aid) = self.actor_level_and_template(killed);
                                    let xpmult =
                                        self.catalog.get(killed_aid).map(|t| t.xp_multiplier).unwrap_or(0);
                                    let diff = (killed_level - recv_level).max(1);
                                    let xp = diff * xpmult + self.rng.range(0, 20);
                                    let mut pkts = self.give_xp(rid, xp);
                                    out.append(&mut pkts);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "setactorlevel" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let level = args.get(1).map(|v| v.to_int()).unwrap_or(0) as i16;
                                    let mut pkts = self.set_actor_level(rid, level);
                                    out.append(&mut pkts);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // ThreadExecute(name, func, actor=0, ctx=0, param="")
                            // — spawn another script with the CALLER's privilege
                            // (script-chained → no allowlist re-elevation, per the
                            // CLAUDE.md hSI=0 carve-out).
                            "threadexecute" => {
                                let name = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                let func = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let actor = args.get(2).map(|v| v.to_int()).unwrap_or(0) as u16;
                                let ctx = args.get(3).map(|v| v.to_int()).unwrap_or(0) as u16;
                                let param = args.get(4).map(|v| v.to_string_value()).unwrap_or_default();
                                let caller_priv = self.running_scripts[i].privileged;
                                if let Some(prog) = self.scripts.linked_program(&name) {
                                    let f = if func.is_empty() { "Main".to_string() } else { func };
                                    let peer = self.world.peer_for_runtime(actor).unwrap_or(0);
                                    self.spawn_program(prog, &f, (actor, ctx, peer), caller_priv, param);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // Spawn(actorId, area, x, y, z, script, deathScript,
                            // instance) — create a live NPC; returns its handle.
                            // SpawnItem(itemName, amount, area, x, y, z, instance)
                            // — drop a ground item. Both mutate the world and
                            // broadcast, so they're handled here. Ungated (Blitz).
                            "spawn" => {
                                let actor_id = args.first().map(|v| v.to_int()).unwrap_or(-1);
                                let area = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let x = args.get(2).map(|v| v.to_float()).unwrap_or(0.0) as f32;
                                let y = args.get(3).map(|v| v.to_float()).unwrap_or(0.0) as f32;
                                let z = args.get(4).map(|v| v.to_float()).unwrap_or(0.0) as f32;
                                let script = args.get(5).map(|v| v.to_string_value()).unwrap_or_default();
                                let death = args.get(6).map(|v| v.to_string_value()).unwrap_or_default();
                                let rid = if (0..=65535).contains(&actor_id) {
                                    self.spawn_scripted_npc(actor_id as u16, &area, (x, y, z), script, death)
                                } else {
                                    0
                                };
                                let _ = reply.send(rcce_script::Value::Int(rid as i64));
                                continue;
                            }
                            "spawnitem" => {
                                let name = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                let amount = args.get(1).map(|v| v.to_int()).unwrap_or(0) as i16;
                                let area = args.get(2).map(|v| v.to_string_value()).unwrap_or_default();
                                let x = args.get(3).map(|v| v.to_float()).unwrap_or(0.0) as f32;
                                let y = args.get(4).map(|v| v.to_float()).unwrap_or(0.0) as f32;
                                let z = args.get(5).map(|v| v.to_float()).unwrap_or(0.0) as f32;
                                let mut pkts = self.spawn_ground_item(&name, amount, &area, x, y, z);
                                out.append(&mut pkts);
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // SetActorTarget(npc, target=0) — privileged: make an
                            // NPC target a player (cleared if 0 / a strongly-allied
                            // target). ActorTarget reads it back. SetActorAIState/
                            // ActorAIState round-trip a mode value (the port derives
                            // behaviour, so it doesn't drive movement — noted).
                            // ActorCallForHelp rallies allies onto the NPC's target.
                            "setactortarget" => {
                                if self.running_scripts[i].privileged {
                                    let npc = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let target = args.get(1).map(|v| v.to_int()).unwrap_or(0) as u16;
                                    if self.spawns.npc(npc).is_some() {
                                        if target == 0 {
                                            self.spawns.clear_target(npc);
                                        } else if let Some(peer) = self.world.peer_for_runtime(target) {
                                            // Faction gate: only target a disliked actor.
                                            let npc_home = self
                                                .spawns
                                                .npc(npc)
                                                .and_then(|n| self.catalog.get(n.actor_id))
                                                .map(|t| t.default_faction)
                                                .unwrap_or(0);
                                            let tgt_home = self
                                                .world
                                                .session(peer)
                                                .and_then(|sess| self.accounts.find(&sess.user).and_then(|a| a.characters.get(sess.char_slot as usize)))
                                                .map(|r| r.actor.home_faction)
                                                .unwrap_or(0);
                                            if self.factions.rating(tgt_home, npc_home) < 150 {
                                                self.spawns.set_target(npc, peer);
                                            }
                                        }
                                    }
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "actortarget" => {
                                let who = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                // Player target (AI\AITarget) first: a player's
                                // current target, validated to still exist (clears
                                // a stale handle to 0). Falls back to an NPC's
                                // retaliation target.
                                let player_t = self.world.target_of_runtime(who);
                                let rid = if player_t != 0
                                    && (self.world.session_for_runtime(player_t).is_some()
                                        || self.spawns.npc(player_t).is_some())
                                {
                                    player_t
                                } else {
                                    self.spawns
                                        .npc_target(who)
                                        .and_then(|p| self.world.session(p).map(|s| s.runtime_id))
                                        .unwrap_or(0)
                                };
                                let _ = reply.send(rcce_script::Value::Int(rid as i64));
                                continue;
                            }
                            "setactoraistate" => {
                                if self.running_scripts[i].privileged {
                                    let npc = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let mode = args.get(1).map(|v| v.to_int()).unwrap_or(0) as i32;
                                    self.npc_aistate.insert(npc, mode);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "actoraistate" => {
                                let npc = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let _ = reply.send(rcce_script::Value::Int(
                                    self.npc_aistate.get(&npc).copied().unwrap_or(0) as i64,
                                ));
                                continue;
                            }
                            "actorcallforhelp" => {
                                let npc = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                if let Some(peer) = self.spawns.npc_target(npc) {
                                    self.npc_call_for_help(npc, peer);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "setactorgroup" => {
                                // Privileged (no shipped callers; brick/grief
                                // vector — closed by PR #325 on the Blitz side).
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let grp = args.get(1).map(|v| v.to_int()).unwrap_or(0) as i32;
                                    self.actor_group.insert(rid, grp);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "actorgroup" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let _ = reply.send(rcce_script::Value::Int(
                                    self.actor_group.get(&rid).copied().unwrap_or(0) as i64,
                                ));
                                continue;
                            }
                            // --- Host-resource BVMs the sandboxed headless port
                            // does not provide. Each is privileged on the Blitz
                            // side; the faithful behavior for the shipped config
                            // is "unavailable": openers/queries fail (0), string
                            // accessors return empty. MySQL is compiled out of the
                            // shipped Blitz server (Server.bb:36), so its faithful
                            // result is also failure/empty. Implemented as explicit
                            // arms (not a silent fall-through) so the dispatch set
                            // matches the Blitz command table 1:1.
                            "mysqlquery" | "mysqlnumrows" | "mysqlfetchrow"
                            | "mysqlfreequery" | "mysqlfreerow"
                            | "createudpstream" | "closeudpstream" | "sendudpmsg"
                            | "recvudpmsg" | "udpmsgport" | "udpstreamport"
                            | "udptimeouts"
                            | "counthostips" | "hostip"
                            | "sqlaccountid" | "sqlactorid" => {
                                let _ = self.running_scripts[i].privileged;
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "mysqlgetvar" | "udpmsgip" | "udpstreamip" | "dottedip" => {
                                let _ = self.running_scripts[i].privileged;
                                let _ = reply.send(rcce_script::Value::Str(String::new()));
                                continue;
                            }
                            // --- Script file-stream subsystem. Sandboxed to
                            // `Server Data/Script Files/`; every op soft-fails on a
                            // bad path / IO error. Privilege parity with the Blitz
                            // BVM_* gates: read/filesize/filetype are ungated (path
                            // check only), the mutating openers + delete/createdir
                            // require privileged. Shipped marriage/mail/globals
                            // content drives these.
                            "readfile" => {
                                let name = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                let h = self.open_script_read(&name);
                                let _ = reply.send(rcce_script::Value::Int(h));
                                continue;
                            }
                            "openfile" => {
                                let h = if self.running_scripts[i].privileged {
                                    let name = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                    self.open_script_read(&name)
                                } else {
                                    0
                                };
                                let _ = reply.send(rcce_script::Value::Int(h));
                                continue;
                            }
                            "writefile" | "appendfile" => {
                                let h = if self.running_scripts[i].privileged {
                                    let name = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                    self.open_script_write(&name, lname == "appendfile")
                                } else {
                                    0
                                };
                                let _ = reply.send(rcce_script::Value::Int(h));
                                continue;
                            }
                            "closefile" => {
                                let handle = args.first().map(|v| v.to_int()).unwrap_or(0);
                                self.close_script_file(handle);
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "readline" => {
                                let handle = args.first().map(|v| v.to_int()).unwrap_or(0) as i32;
                                let line = self
                                    .script_files
                                    .get_mut(&handle)
                                    .and_then(|f| {
                                        let l = f.lines.get(f.read_pos).cloned();
                                        if l.is_some() {
                                            f.read_pos += 1;
                                        }
                                        l
                                    })
                                    .unwrap_or_default();
                                let _ = reply.send(rcce_script::Value::Str(line));
                                continue;
                            }
                            "writeline" => {
                                let handle = args.first().map(|v| v.to_int()).unwrap_or(0) as i32;
                                let s = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                if let Some(f) = self.script_files.get_mut(&handle) {
                                    if let Some(buf) = f.out.as_mut() {
                                        buf.push_str(&s);
                                        buf.push('\n');
                                    }
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "eof" => {
                                let handle = args.first().map(|v| v.to_int()).unwrap_or(0) as i32;
                                // Eof = 1 when there's nothing left to read (also for
                                // a write handle or an unknown handle).
                                let at_eof = self
                                    .script_files
                                    .get(&handle)
                                    .map(|f| f.read_pos >= f.lines.len())
                                    .unwrap_or(true);
                                let _ = reply.send(rcce_script::Value::Int(at_eof as i64));
                                continue;
                            }
                            "filesize" => {
                                let name = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                let sz = self
                                    .script_file_path(&name)
                                    .and_then(|p| std::fs::metadata(p).ok())
                                    .map(|m| m.len() as i64)
                                    .unwrap_or(0);
                                let _ = reply.send(rcce_script::Value::Int(sz));
                                continue;
                            }
                            "filetype" => {
                                // Blitz FileType: 0 = none, 1 = file, 2 = dir.
                                let name = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                let t = match self.script_file_path(&name).and_then(|p| std::fs::metadata(p).ok()) {
                                    Some(m) if m.is_dir() => 2,
                                    Some(_) => 1,
                                    None => 0,
                                };
                                let _ = reply.send(rcce_script::Value::Int(t));
                                continue;
                            }
                            "deletefile" => {
                                if self.running_scripts[i].privileged {
                                    let name = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                    if let Some(p) = self.script_file_path(&name) {
                                        let _ = std::fs::remove_file(p);
                                    }
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "createdir" => {
                                if self.running_scripts[i].privileged {
                                    let name = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                    if let Some(p) = self.script_file_path(&name) {
                                        let _ = std::fs::create_dir_all(p);
                                    }
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // SetLeader(npc, leader) — privileged. Make an NPC a
                            // pet that follows `leader`. Only NPCs can be pets
                            // (a player target is ignored). ActorLeader/ActorPets
                            // are read-only.
                            "setleader" => {
                                if self.running_scripts[i].privileged {
                                    let npc = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let leader = args.get(1).map(|v| v.to_int()).unwrap_or(0) as u16;
                                    if self.spawns.npc(npc).is_some() {
                                        self.spawns.set_leader(npc, leader);
                                    }
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "actorleader" => {
                                let npc = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let l = self.spawns.npc_leader(npc).unwrap_or(0);
                                let _ = reply.send(rcce_script::Value::Int(l as i64));
                                continue;
                            }
                            "actorpets" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let _ = reply.send(rcce_script::Value::Int(self.spawns.pet_count(rid) as i64));
                                continue;
                            }
                            // BanPlayer(actor) — privileged: set the account's
                            // ban flag (login rejects it with "B"). KickPlayer
                            // (actor) — send P_KickedPlayer + queue a disconnect.
                            "banplayer" | "kickplayer" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    if let Some(peer) = self.world.peer_for_runtime(rid) {
                                        if lname == "banplayer" {
                                            if let Some((u, _)) = self.player_loc(rid) {
                                                if let Some(acct) = self.accounts.find_mut(&u) {
                                                    acct.is_banned = true;
                                                    self.accounts_dirty = true;
                                                }
                                            }
                                        } else {
                                            out.push(Outgoing::peer(peer, world::P_KICKED_PLAYER, Vec::new()));
                                            self.pending_kicks.push(peer);
                                        }
                                    }
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // ChangeActor(actor, newId) — privileged template
                            // morph + P_AppearanceUpdate "C" broadcast.
                            "changeactor" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let new_id = args.get(1).map(|v| v.to_int()).unwrap_or(-1);
                                    if (0..=65535).contains(&new_id) {
                                        let mut pkts = self.change_actor(rid, new_id as u16);
                                        out.append(&mut pkts);
                                    }
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // KillActor(target, killer=0) — privileged (a script
                            // one-shot is a brick vector). Player → Death path;
                            // NPC → kill path.
                            "killactor" => {
                                if self.running_scripts[i].privileged {
                                    let target = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let killer = args.get(1).map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let mut pkts = self.kill_actor(target, killer);
                                    out.append(&mut pkts);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // Faction mutators — drive the combat-engagement gate,
                            // so all privileged (a flipped rating makes a guard
                            // friendly / a player invisible to aggro). Reads
                            // ungated. Need the faction grid (ServerState).
                            "setfactionrating" | "changefactionrating" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let faction = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                    let value = args.get(2).map(|v| v.to_int()).unwrap_or(0) as i32;
                                    self.set_faction_rating(rid, &faction, value, lname == "changefactionrating");
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "sethomefaction" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let faction = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                    self.set_home_faction(rid, &faction);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "factionrating" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let faction = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let _ = reply.send(rcce_script::Value::Int(self.faction_rating(rid, &faction)));
                                continue;
                            }
                            "homefaction" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let _ = reply.send(rcce_script::Value::Str(self.home_faction_name(rid)));
                                continue;
                            }
                            // Equipped-armour total + faction reads + underwater.
                            "getarmourlevel" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let armour = if let Some((u, s)) = self.player_loc(rid) {
                                    if let Some(rec) = self.accounts.find(&u).and_then(|a| a.characters.get(s)) {
                                        Self::equipped_armour(&self.items, &rec.actor) as i64
                                    } else {
                                        0
                                    }
                                } else {
                                    0
                                };
                                let _ = reply.send(rcce_script::Value::Int(armour));
                                continue;
                            }
                            "getfaction" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let home = self
                                    .player_loc(rid)
                                    .and_then(|(u, s)| {
                                        self.accounts.find(&u).and_then(|a| a.characters.get(s)).map(|r| r.actor.home_faction)
                                    })
                                    .or_else(|| {
                                        self.spawns.npc(rid).and_then(|n| self.catalog.get(n.actor_id)).map(|t| t.default_faction)
                                    })
                                    .unwrap_or(0);
                                let _ = reply.send(rcce_script::Value::Str(self.factions.name(home as usize).to_string()));
                                continue;
                            }
                            "defaultfactionrating" => {
                                let from = args.first().map(|v| v.to_int()).unwrap_or(0) as u8;
                                let to = args.get(1).map(|v| v.to_int()).unwrap_or(0) as u8;
                                let _ = reply.send(rcce_script::Value::Int(self.factions.rating(from, to) as i64 - 100));
                                continue;
                            }
                            // ActorInTrigger(actor, triggerIdx) — is the player
                            // inside that trigger's sphere right now?
                            "actorintrigger" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let tidx = args.get(1).map(|v| v.to_int()).unwrap_or(-1);
                                let pos = self.world.session_for_runtime(rid).map(|s| (s.area.clone(), s.x, s.y, s.z));
                                let inside = if let Some((area, x, y, z)) = pos {
                                    self.cached_area(&area)
                                        .and_then(|a| usize::try_from(tidx).ok().and_then(|i| a.triggers.get(i)))
                                        .map(|t| {
                                            let (dx, dy, dz) = (x - t.x, y - t.y, z - t.z);
                                            !t.script.is_empty() && dx * dx + dy * dy + dz * dz < t.size * t.size
                                        })
                                        .unwrap_or(false)
                                } else {
                                    false
                                };
                                let _ = reply.send(rcce_script::Value::Int(if inside { 1 } else { 0 }));
                                continue;
                            }
                            "actorunderwater" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let pos = self.world.session_for_runtime(rid).map(|s| (s.area.clone(), s.x, s.y, s.z));
                                let under = if let Some((area, x, y, z)) = pos {
                                    let waters = self.cached_area(&area).map(|a| a.waters.clone()).unwrap_or_default();
                                    waters.iter().any(|w| {
                                        y < w.y + 0.5 && x > w.x && x < w.x + w.width && z > w.z && z < w.z + w.depth
                                    })
                                } else {
                                    false
                                };
                                let _ = reply.send(rcce_script::Value::Int(if under { 1 } else { 0 }));
                                continue;
                            }
                            // Season / Month from the game clock (approximate —
                            // quarter/twelfth of a 365-day year; the real tables
                            // are in Environment.dat, deferred).
                            "season" => {
                                let s = match (self.game_time.2 / 91).clamp(0, 3) {
                                    0 => "Spring",
                                    1 => "Summer",
                                    2 => "Autumn",
                                    _ => "Winter",
                                };
                                let _ = reply.send(rcce_script::Value::Str(s.to_string()));
                                continue;
                            }
                            "month" => {
                                const MONTHS: [&str; 12] = [
                                    "January", "February", "March", "April", "May", "June",
                                    "July", "August", "September", "October", "November", "December",
                                ];
                                let mi = (self.game_time.2 / 30).clamp(0, 11) as usize;
                                let _ = reply.send(rcce_script::Value::Str(MONTHS[mi].to_string()));
                                continue;
                            }
                            // Party reads — member count + the n-th member's rid.
                            "countpartymembers" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let n = self
                                    .world
                                    .peer_for_runtime(rid)
                                    .map(|p| self.party_members(p).len())
                                    .unwrap_or(1);
                                let _ = reply.send(rcce_script::Value::Int(n as i64));
                                continue;
                            }
                            "partymember" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let idx = args.get(1).map(|v| v.to_int()).unwrap_or(0).max(0) as usize;
                                let member_rid = self
                                    .world
                                    .peer_for_runtime(rid)
                                    .and_then(|p| self.party_members(p).get(idx).copied())
                                    .and_then(|mp| self.world.session(mp).map(|s| s.runtime_id))
                                    .unwrap_or(0);
                                let _ = reply.send(rcce_script::Value::Int(member_rid as i64));
                                continue;
                            }
                            // In-game clock reads (game-time, not wall-clock).
                            "hour" => {
                                let _ = reply.send(rcce_script::Value::Int(self.game_time.0 as i64));
                                continue;
                            }
                            "minute" => {
                                let _ = reply.send(rcce_script::Value::Int(self.game_time.1 as i64));
                                continue;
                            }
                            "day" => {
                                let _ = reply.send(rcce_script::Value::Int(self.game_time.2 as i64));
                                continue;
                            }
                            "year" => {
                                let _ = reply.send(rcce_script::Value::Int(self.game_time.3 as i64));
                                continue;
                            }
                            // SaveState — privileged: force an immediate persist
                            // (accounts). Persistent / RefreshScripts: lifecycle/
                            // admin flags that are no-ops in the threaded model.
                            "savestate" => {
                                if self.running_scripts[i].privileged {
                                    self.accounts_dirty = true;
                                    let _ = self.maybe_persist_force();
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "persistent" | "refreshscripts" => {
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // SuperGlobals — server-wide shared script state (100
                            // slots). SetSuperGlobal privileged (no actor to scope
                            // to; poisons global state); GetSuperGlobal ungated.
                            "setsuperglobal" => {
                                if self.running_scripts[i].privileged {
                                    let idx = args.first().map(|v| v.to_int()).unwrap_or(-1);
                                    let val = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                    if (0..100).contains(&idx) {
                                        self.super_globals[idx as usize] = val;
                                    }
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "getsuperglobal" => {
                                let idx = args.first().map(|v| v.to_int()).unwrap_or(-1);
                                let v = if (0..100).contains(&idx) {
                                    self.super_globals[idx as usize].clone()
                                } else {
                                    String::new()
                                };
                                let _ = reply.send(rcce_script::Value::Str(v));
                                continue;
                            }
                            // Equipment-slot reads → an item handle for the slot
                            // (0 if empty), consumed by the item-property reads.
                            "actorweapon" | "actorshield" | "actorhat" | "actorchest"
                            | "actorhands" | "actorbelt" | "actorlegs" | "actorfeet"
                            | "actorring" | "actoramulet" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let slot = match lname.as_str() {
                                    "actorweapon" => 0,
                                    "actorshield" => 1,
                                    "actorhat" => 2,
                                    "actorchest" => 3,
                                    "actorhands" => 4,
                                    "actorbelt" => 5,
                                    "actorlegs" => 6,
                                    "actorfeet" => 7,
                                    "actorring" => 8,
                                    "actoramulet" => 12,
                                    _ => 0,
                                };
                                let _ = reply.send(rcce_script::Value::Int(self.equip_handle(rid, slot)));
                                continue;
                            }
                            // ActorBackpack(actor, num) — item handle in backpack
                            // slot (1-based; BVM_ACTORBACKPACK ScriptingCommands.bb:1266).
                            // num=1 → slot SlotI_Backpack(14). 0 = empty/out-of-range.
                            "actorbackpack" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let n = args.get(1).map(|v| v.to_int()).unwrap_or(0) - 1;
                                let h = if n < 0 { 0 } else { self.equip_handle(rid, 14 + n as usize) };
                                let _ = reply.send(rcce_script::Value::Int(h));
                                continue;
                            }
                            // Item-property reads — decode the handle → template
                            // properties (from the item catalog) + instance health.
                            "itemid" | "itemname" | "itemvalue" | "itemmass"
                            | "itemdamage" | "itemarmor" | "itemdamagetype" | "itemhealth"
                            | "itemrange" | "itemweapontype" | "itemmiscdata" => {
                                let handle = args.first().map(|v| v.to_int()).unwrap_or(0);
                                let info = self.item_at_handle(handle);
                                let def = info.and_then(|(id, _)| self.items.get(id));
                                let val = match lname.as_str() {
                                    // MiscData is discarded by the item parser, so
                                    // the faithful read is the empty string.
                                    "itemmiscdata" => rcce_script::Value::Str(String::new()),
                                    "itemid" => rcce_script::Value::Int(info.map(|(id, _)| id as i64).unwrap_or(0)),
                                    "itemhealth" => rcce_script::Value::Int(info.map(|(_, h)| h as i64).unwrap_or(0)),
                                    "itemname" => rcce_script::Value::Str(def.map(|d| d.name.clone()).unwrap_or_default()),
                                    "itemvalue" => rcce_script::Value::Int(def.map(|d| d.value as i64).unwrap_or(0)),
                                    "itemmass" => rcce_script::Value::Int(def.map(|d| d.mass as i64).unwrap_or(0)),
                                    "itemdamage" => rcce_script::Value::Int(def.map(|d| d.weapon_damage as i64).unwrap_or(0)),
                                    "itemarmor" => rcce_script::Value::Int(def.map(|d| d.armour_level as i64).unwrap_or(0)),
                                    "itemrange" => rcce_script::Value::Float(def.map(|d| d.weapon_range as f64).unwrap_or(0.0)),
                                    "itemweapontype" => rcce_script::Value::Int(def.map(|d| d.weapon_wtype as i64).unwrap_or(0)),
                                    "itemdamagetype" => rcce_script::Value::Str(
                                        def.map(|d| self.damage_types.name(d.weapon_damage_type).unwrap_or("").to_string())
                                            .unwrap_or_default(),
                                    ),
                                    _ => rcce_script::Value::Int(0),
                                };
                                let _ = reply.send(val);
                                continue;
                            }
                            // ItemAttribute(itemHandle, attrName) — a per-instance
                            // attribute delta on the item (not the catalog).
                            "itemattribute" => {
                                let handle = args.first().map(|v| v.to_int()).unwrap_or(0);
                                let attr = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let val = if handle & ITEM_HANDLE_TAG != 0 {
                                    if let Some(idx) = self.attr_names.index_of(&attr) {
                                        let rid = ((handle >> 8) & 0xFFFF) as u16;
                                        let slot = (handle & 0xFF) as usize;
                                        self.player_loc(rid)
                                            .and_then(|(u, s)| self.accounts.find(&u).and_then(|a| a.characters.get(s)))
                                            .and_then(|rec| rec.actor.inventory.get(slot))
                                            .and_then(|sl| sl.item.as_ref())
                                            .and_then(|it| it.attr_values.get(idx))
                                            .map(|&v| v as i64)
                                            .unwrap_or(0)
                                    } else {
                                        0
                                    }
                                } else {
                                    0
                                };
                                let _ = reply.send(rcce_script::Value::Int(val));
                                continue;
                            }
                            // SetItemHealth(itemHandle, value) — privileged (item
                            // handle → no self scope; full-priv per CLAUDE.md).
                            "setitemhealth" => {
                                if self.running_scripts[i].privileged {
                                    let handle = args.first().map(|v| v.to_int()).unwrap_or(0);
                                    let value = args.get(1).map(|v| v.to_int()).unwrap_or(0) as i32;
                                    self.set_item_health(handle, value);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // HasItem(actor, name, count=1) — inventory query
                            // (needs the item catalog). Ungated read.
                            "hasitem" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let count = args.get(2).map(|v| v.to_int()).unwrap_or(1) as i32;
                                let has = self.has_item(rid, &name, count);
                                let _ = reply.send(rcce_script::Value::Int(if has { 1 } else { 0 }));
                                continue;
                            }
                            // Resistance — drives the combat damage formula, so the
                            // setter is privileged (brick vector); read ungated.
                            "setresistance" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let dmg = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                    let value = args.get(2).map(|v| v.to_int()).unwrap_or(0) as i32;
                                    self.set_resistance(rid, &dmg, value);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "resistance" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let dmg = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let _ = reply.send(rcce_script::Value::Int(self.resistance(rid, &dmg)));
                                continue;
                            }
                            // Zone reads — actor's zone name + outdoor flags.
                            "actorzone" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let _ = reply.send(rcce_script::Value::Str(self.actor_area(rid).unwrap_or_default()));
                                continue;
                            }
                            "actoroutdoors" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let out_b = self
                                    .actor_area(rid)
                                    .map(|a| self.area_outdoors(&a))
                                    .unwrap_or(false);
                                let _ = reply.send(rcce_script::Value::Int(if out_b { 1 } else { 0 }));
                                continue;
                            }
                            // ZoneInstanceExists(zone, instance) — only instance 0
                            // exists in the single-instance model.
                            "zoneinstanceexists" => {
                                let zone = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                let instance = args.get(1).map(|v| v.to_int()).unwrap_or(0);
                                let exists = instance == 0 && self.area_exists(&zone);
                                let _ = reply.send(rcce_script::Value::Int(if exists { 1 } else { 0 }));
                                continue;
                            }
                            "zoneoutdoors" => {
                                let area = args.first().map(|v| v.to_string_value()).unwrap_or_default();
                                let out_b = self.area_outdoors(&area);
                                let _ = reply.send(rcce_script::Value::Int(if out_b { 1 } else { 0 }));
                                continue;
                            }
                            // Timed effects (buffs/debuffs) over the existing
                            // ActorEffect machinery. Ungated (matches Blitz —
                            // temporary + auto-reverting). Player-only.
                            "addactoreffect" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let attr = args.get(2).map(|v| v.to_string_value()).unwrap_or_default();
                                let value = args.get(3).map(|v| v.to_int()).unwrap_or(0) as i32;
                                let length = args.get(4).map(|v| v.to_int()).unwrap_or(0) as i32;
                                let icon = args.get(5).map(|v| v.to_int()).unwrap_or(0).max(0) as u16;
                                let mut pkts = self.add_actor_effect(rid, &name, &attr, value, length, icon);
                                out.append(&mut pkts);
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "deleteactoreffect" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let mut pkts = self.delete_actor_effect(rid, &name);
                                out.append(&mut pkts);
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "actorhaseffect" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let has = self.actor_has_effect(rid, &name);
                                let _ = reply.send(rcce_script::Value::Int(if has { 1 } else { 0 }));
                                continue;
                            }
                            // Movement: MoveActor / RotateActor /
                            // SetActorDestination — mutate actor position/dest +
                            // broadcast P_RepositionActor. RequireSelfOrPrivileged
                            // (an actor may move itself; otherwise privileged) —
                            // the self shortcut is correct for engine-tick NPC
                            // spawns (SI\AI = the NPC).
                            "moveactor" | "rotateactor" | "setactordestination" => {
                                let target = args.first().map(|v| v.to_int()).unwrap_or(-1);
                                let rs = &self.running_scripts[i];
                                let allowed = rs.privileged || target == rs.actor as i64;
                                if allowed && (0..=65535).contains(&target) {
                                    let rid = target as u16;
                                    let p2 = args.get(1).map(|v| v.to_float()).unwrap_or(0.0) as f32;
                                    let mut pkts = match lname.as_str() {
                                        "moveactor" => {
                                            let y = args.get(2).map(|v| v.to_float()).unwrap_or(0.0) as f32;
                                            let z = args.get(3).map(|v| v.to_float()).unwrap_or(0.0) as f32;
                                            let f5 = args.get(4).map(|v| v.to_int()).unwrap_or(0) as u8;
                                            self.move_actor(rid, p2, y, z, f5)
                                        }
                                        "rotateactor" => self.rotate_actor(rid, p2),
                                        _ => {
                                            let z = args.get(2).map(|v| v.to_float()).unwrap_or(0.0) as f32;
                                            self.set_actor_destination(rid, p2, z);
                                            Vec::new()
                                        }
                                    };
                                    out.append(&mut pkts);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // Abilities (spell knowledge). Reads are ungated;
                            // AddAbility is ungated (trainers grant spells);
                            // SetAbilityLevel/DeleteAbility are privileged (zeroing
                            // or stripping an ability bricks the combat toolkit).
                            // All need the spell catalog (ServerState), so here.
                            "abilityknown" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let known = self
                                    .spell_by_name(&name)
                                    .map(|(id, _, _)| self.ability_known(rid, id))
                                    .unwrap_or(false);
                                let _ = reply.send(rcce_script::Value::Int(if known { 1 } else { 0 }));
                                continue;
                            }
                            "abilitylevel" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let lvl = self
                                    .spell_by_name(&name)
                                    .and_then(|(id, _, _)| {
                                        let (u, s) = self.player_loc(rid)?;
                                        let rec = self.accounts.find(&u).and_then(|a| a.characters.get(s))?;
                                        rec.actor
                                            .spell_levels
                                            .iter()
                                            .zip(&rec.actor.known_spells)
                                            .find(|(&lv, &sid)| lv > 0 && sid == id as i16)
                                            .map(|(&lv, _)| lv as i64)
                                    })
                                    .unwrap_or(0);
                                let _ = reply.send(rcce_script::Value::Int(lvl));
                                continue;
                            }
                            "abilitymemorised" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let memd = self
                                    .spell_by_name(&name)
                                    .and_then(|(id, _, _)| {
                                        let (u, s) = self.player_loc(rid)?;
                                        let rec = self.accounts.find(&u).and_then(|a| a.characters.get(s))?;
                                        // memorised_spells hold slot indices into known_spells.
                                        Some(rec.actor.memorised_spells.iter().any(|&m| {
                                            m != 5000
                                                && rec
                                                    .actor
                                                    .known_spells
                                                    .get(m as usize)
                                                    .map(|&sid| sid == id as i16)
                                                    .unwrap_or(false)
                                        }))
                                    })
                                    .unwrap_or(false);
                                let _ = reply.send(rcce_script::Value::Int(if memd { 1 } else { 0 }));
                                continue;
                            }
                            "addability" => {
                                let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                let lvl = args.get(2).map(|v| v.to_int()).unwrap_or(1) as i16;
                                let mut pkts = self.add_ability(rid, &name, lvl);
                                out.append(&mut pkts);
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "setabilitylevel" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                    let lvl = args.get(2).map(|v| v.to_int()).unwrap_or(0) as i16;
                                    let mut pkts = self.set_ability_level(rid, &name, lvl);
                                    out.append(&mut pkts);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            "deleteability" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                    let mut pkts = self.delete_ability(rid, &name);
                                    out.append(&mut pkts);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // Warp mutates world state (area/known-sets), which the
                            // immutable-world ScriptHost can't do — handle it here.
                            // Privileged-gated, parity with BVM_WARP.
                            "warp" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let area = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                    let portal = args.get(2).map(|v| v.to_string_value()).unwrap_or_default();
                                    let mut pkts = self.warp_actor(rid, &area, &portal);
                                    out.append(&mut pkts);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // FireProjectile broadcasts the visual to same-area
                            // players (needs the projectile catalog + world).
                            // Self-or-privileged (parity with BVM_FIREPROJECTILE).
                            "fireprojectile" => {
                                let src = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                if self.running_scripts[i].privileged || src == self.running_scripts[i].actor {
                                    let tgt = args.get(1).map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let name = args.get(2).map(|v| v.to_string_value()).unwrap_or_default();
                                    let mut pkts = self.fire_projectile(src, tgt, &name);
                                    out.append(&mut pkts);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // OpenTrading opens the vendor window — sends a packet
                            // + sets trade state, so it's handled here. Ungated
                            // (BVM_OPENTRADING has no privilege gate).
                            "opentrading" => {
                                let actor_rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                let ctx_rid = args.get(1).map(|v| v.to_int()).unwrap_or(0) as u16;
                                let mut pkts = self.open_trading(actor_rid, ctx_rid);
                                out.append(&mut pkts);
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            // GiveItem grants an item by name — needs the item
                            // catalog + the assigned-item registry (ServerState),
                            // so it's handled here, not in ScriptHost. Privileged.
                            "giveitem" => {
                                if self.running_scripts[i].privileged {
                                    let rid = args.first().map(|v| v.to_int()).unwrap_or(0) as u16;
                                    let name = args.get(1).map(|v| v.to_string_value()).unwrap_or_default();
                                    let amount = args.get(2).map(|v| v.to_int()).unwrap_or(1) as i16;
                                    let mut pkts = self.give_item(rid, &name, amount);
                                    out.append(&mut pkts);
                                }
                                let _ = reply.send(rcce_script::Value::Int(0));
                                continue;
                            }
                            _ => {}
                        }
                        let (actor, ctx, priv_) = {
                            let rs = &self.running_scripts[i];
                            (rs.actor, rs.ctx, rs.privileged)
                        };
                        let mut host = crate::scripts::ScriptHost {
                            world: &self.world,
                            accounts: &mut self.accounts,
                            spawns: &self.spawns,
                            catalog: &self.catalog,
                            attr_names: &self.attr_names,
                            rng: &mut self.rng,
                            actor: actor as i64,
                            ctx: ctx as i64,
                            privileged: priv_,
                            dirty: false,
                            out: Vec::new(),
                        };
                        let val = host.call(&name, &args);
                        let dirty = host.dirty;
                        out.append(&mut host.out);
                        if dirty {
                            self.accounts_dirty = true;
                        }
                        let _ = reply.send(val);
                    }
                    Ok(ScriptMsg::Done) => {
                        finished = true;
                        break;
                    }
                    Err(std::sync::mpsc::TryRecvError::Empty) => break,
                    Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                        finished = true;
                        break;
                    }
                }
            }
            if finished {
                let rs = self.running_scripts.remove(i);
                if let Some(h) = rs.handle {
                    let _ = h.join();
                }
            } else {
                i += 1;
            }
        }
        out
    }

    /// Resume a player's suspended script with a `WaitResult` (from a `P_Dialog`
    /// response). Decodes the inbound dialog packet and unblocks the parked
    /// `GetWaitResult`. Returns no packets directly (the resumed script's output
    /// surfaces in the next [`pump_scripts`](Self::pump_scripts)).
    pub fn handle_dialog_response(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if let Some(result) = crate::scripts::decode_dialog_response(payload) {
            self.resume_script(peer, result);
        }
        Vec::new()
    }

    /// `P_ScriptInput` (`ServerNet.bb:1321`): a free-text reply to a script
    /// suspended on `Input`. Wire = `[u32 scriptHandle][text]`; we resume the
    /// peer's suspended script with the text (the handle is per-peer-implicit in
    /// our one-suspended-script-per-peer model, as with dialog).
    pub fn handle_script_input(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 4 {
            return Vec::new();
        }
        let text = String::from_utf8_lossy(&payload[4..]).into_owned();
        self.resume_script(peer, text);
        Vec::new()
    }

    /// `P_ProgressBar` (`ServerNet.bb:1289`): `'C'` completes a timed action —
    /// resume the suspended script with the completion value. Wire =
    /// `['C'][u32 scriptHandle][i32 value]`.
    pub fn handle_progress_bar(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.first() != Some(&b'C') {
            return Vec::new();
        }
        let value = if payload.len() >= 9 {
            i32::from_le_bytes([payload[5], payload[6], payload[7], payload[8]])
        } else {
            0
        };
        self.resume_script(peer, value.to_string());
        Vec::new()
    }

    /// `P_Jump` (`ServerNet.bb:1080`): rebroadcast the jumper's runtime id (2
    /// bytes) to same-area players so they animate the jump. (Sender excluded —
    /// the client predicts its own jump; every other relay in the port excludes
    /// the sender too. Blitz's literal walk includes the jumper.)
    pub fn handle_jump(&mut self, peer: u32, _payload: &[u8]) -> Vec<Outgoing> {
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        let body = sess.runtime_id.to_le_bytes().to_vec();
        let mut out = Vec::new();
        for (pb, sb) in self.world.session_snapshot() {
            if pb != peer && sb.area == sess.area {
                out.push(Outgoing::peer(pb, world::P_JUMP, body.clone()));
            }
        }
        out
    }

    /// `P_ActionBarUpdate` (`ServerNet.bb:1098`): persist a hotbar slot edit on
    /// the player's stored character. Wire = `[type][u8 slot][data]`: `'S'`+name
    /// (spell), `'I'`+2-byte item id, `'N'` (clear). The stored slot string is
    /// `type + data`, re-read on the next `P_StartGame`.
    pub fn handle_action_bar_update(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 2 {
            return Vec::new();
        }
        let slot = payload[1] as usize;
        if slot >= 36 {
            return Vec::new();
        }
        let kind = payload[0];
        let new_slot = match kind {
            b'S' => {
                let mut name = String::from_utf8_lossy(&payload[2..]).into_owned();
                if name.len() > 255 {
                    name.truncate(255);
                }
                format!("S{name}")
            }
            b'I' if payload.len() >= 4 => {
                // 2 raw id bytes → Latin-1 chars so the slot round-trips.
                format!("I{}{}", payload[2] as char, payload[3] as char)
            }
            b'N' => String::new(),
            _ => return Vec::new(),
        };
        let Some((u, s)) = self.player_loc_for_peer(peer) else {
            return Vec::new();
        };
        if let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
            if let Some(slot_ref) = rec.action_bar.get_mut(slot) {
                *slot_ref = new_slot;
                self.accounts_dirty = true;
            }
        }
        Vec::new()
    }

    /// `P_ChatMessage` entry: a leading `/` or `\` is a slash-command, otherwise
    /// it's area chat (the existing free-fn broadcast). Split out so commands can
    /// fire content scripts (which the immutable free fn can't).
    pub fn handle_chat(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if matches!(payload.first(), Some(b'/') | Some(b'\\')) {
            return self.handle_slash_command(peer, payload);
        }
        // WaitSpeak: a script may be parked until this player speaks.
        if let Some(rid) = self.world.session(peer).map(|s| s.runtime_id) {
            self.resume_speak_waits(rid);
        }
        world::handle_chat_message(payload, &self.world, &self.accounts, peer)
    }

    /// Slash-command dispatch (`ServerNet.bb:190`). Parse `[/\]COMMAND[ PARAMS]`;
    /// route the (uppercased) command to the project's `In-game Commands` script
    /// as the **function name** (allowlisted → runs privileged), matching Blitz's
    /// `Default` case. Unknown command (no such script) → an error-coloured chat
    /// reply. _Built-in DM/social commands (kick/ignore/party) and script `Param$`
    /// passing are deferred — the shipped demo commands take no params; noted in
    /// PARITY.md._
    fn handle_slash_command(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        let raw = String::from_utf8_lossy(&payload[1..]);
        let raw = raw.trim_start();
        let (command, params) = match raw.find(' ') {
            Some(i) => (raw[..i].to_uppercase(), raw[i + 1..].trim().to_string()),
            None => (raw.to_uppercase(), String::new()),
        };
        if command.is_empty() {
            return Vec::new();
        }
        // Built-in /party command (forms a party with a nearby player).
        if command == "PARTY" && !params.is_empty() {
            return self.join_party(peer, &params);
        }
        // Built-in social chat commands (ServerNet.bb:388-475), matched against the
        // localized command words (Language.txt IDs 203-208; Blitz defaults
        // ME/YELL/GM/G/P/PM). GuildSay ("G") is intentionally skipped — the port has
        // no guild/TeamID membership system (noted in PARITY.md).
        {
            use crate::language::{
                LS_SC_GMSAY, LS_SC_GOLD, LS_SC_KICK, LS_SC_ME, LS_SC_PARTYSAY, LS_SC_PMSAY,
                LS_SC_SCRIPT, LS_SC_SETATTRIBUTE, LS_SC_SETATTRIBUTEMAX, LS_SC_XP, LS_SC_YELL,
            };
            let cmd = command.as_str();
            // Social set (any player).
            if cmd == self.language.get(LS_SC_ME) {
                return self.chat_emote(peer, &params);
            }
            if cmd == self.language.get(LS_SC_YELL) {
                return self.chat_yell(peer, &params);
            }
            if cmd == self.language.get(LS_SC_GMSAY) {
                return self.chat_gmsay(peer, &params);
            }
            if cmd == self.language.get(LS_SC_PARTYSAY) {
                return self.chat_partysay(peer, &params);
            }
            if cmd == self.language.get(LS_SC_PMSAY) {
                return self.chat_pm(peer, &params);
            }
            // DM set (each handler gates on Account\IsDM; ServerNet.bb:201-389).
            if cmd == self.language.get(LS_SC_KICK) {
                return self.chat_kick(peer, &params);
            }
            if cmd == self.language.get(LS_SC_XP) {
                return self.chat_xp(peer, &params);
            }
            if cmd == self.language.get(LS_SC_GOLD) {
                return self.chat_gold(peer, &params);
            }
            if cmd == self.language.get(LS_SC_SETATTRIBUTE) {
                return self.chat_setattribute(peer, &params);
            }
            if cmd == self.language.get(LS_SC_SETATTRIBUTEMAX) {
                return self.chat_setattributemax(peer, &params);
            }
            if cmd == self.language.get(LS_SC_SCRIPT) {
                return self.chat_script(peer, &params);
            }
        }
        let Some(rid) = self.world.session(peer).map(|s| s.runtime_id) else {
            return Vec::new();
        };
        if self.scripts.get("In-game Commands").is_some() {
            let before = self.running_scripts.len();
            self.fire_hook_async("In-game Commands", &command, rid, 0, peer);
            // Pass the command's argument string as the script's Param$.
            if self.running_scripts.len() > before {
                if let Some(rs) = self.running_scripts.last_mut() {
                    rs.param = params;
                }
            }
            Vec::new()
        } else {
            let mut p = vec![254u8];
            p.extend_from_slice(
                format!("Unknown command: /{}.  Type /help for a list.", command.to_lowercase())
                    .as_bytes(),
            );
            vec![Outgoing::peer(peer, world::P_CHAT_MESSAGE, p)]
        }
    }

    /// Whether a peer's account is a DM (`Account\IsDM`) — drives `/gm` and the
    /// DM command gate.
    fn peer_is_dm(&self, peer: u32) -> bool {
        self.world
            .session(peer)
            .and_then(|s| self.accounts.find(&s.user))
            .map(|a| a.is_dm)
            .unwrap_or(false)
    }

    /// `/me` (`ServerNet.bb:390`): an emote to every player in the sender's area
    /// (including the sender). Purple (252).
    fn chat_emote(&self, peer: u32, params: &str) -> Vec<Outgoing> {
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        let name = self.player_name(peer);
        let mut p = vec![252u8];
        p.extend_from_slice(format!("* {name} {params}").as_bytes());
        self.world
            .session_snapshot()
            .into_iter()
            .filter(|(_, s)| s.area == sess.area)
            .map(|(pb, _)| Outgoing::peer(pb, world::P_CHAT_MESSAGE, p.clone()))
            .collect()
    }

    /// `/yell` (`ServerNet.bb:405`): to every online player (all areas, including
    /// the sender). Red (253).
    fn chat_yell(&self, peer: u32, params: &str) -> Vec<Outgoing> {
        let name = self.player_name(peer);
        let mut p = vec![253u8];
        p.extend_from_slice(format!("<{name}> {params}").as_bytes());
        self.world
            .session_snapshot()
            .into_iter()
            .map(|(pb, _)| Outgoing::peer(pb, world::P_CHAT_MESSAGE, p.clone()))
            .collect()
    }

    /// `/gm` (`ServerNet.bb:428`): a DM-only broadcast to every online DM. No-op if
    /// the sender isn't a DM. Yellow (254).
    fn chat_gmsay(&self, peer: u32, params: &str) -> Vec<Outgoing> {
        if !self.peer_is_dm(peer) {
            return Vec::new();
        }
        let name = self.player_name(peer);
        let mut p = vec![254u8];
        p.extend_from_slice(format!("<GM> <{name}> {params}").as_bytes());
        self.world
            .session_snapshot()
            .into_iter()
            .filter(|(pb, _)| self.peer_is_dm(*pb))
            .map(|(pb, _)| Outgoing::peer(pb, world::P_CHAT_MESSAGE, p.clone()))
            .collect()
    }

    /// `/p` (`ServerNet.bb:452`): to the sender's other party members. Green (251).
    fn chat_partysay(&self, peer: u32, params: &str) -> Vec<Outgoing> {
        let name = self.player_name(peer);
        let mut p = vec![251u8];
        p.extend_from_slice(format!("<PARTY> <{name}> {params}").as_bytes());
        self.party_members(peer)
            .into_iter()
            .filter(|&m| m != peer)
            .map(|m| Outgoing::peer(m, world::P_CHAT_MESSAGE, p.clone()))
            .collect()
    }

    /// `/pm Target,message` (`ServerNet.bb:462`): a private message to the named
    /// online player. Purple (252). No-op if the target isn't found or the params
    /// lack a comma. (The message keeps any further commas, vs Blitz's `Split` which
    /// truncates at the next comma — a deliberate, benign lenience.)
    fn chat_pm(&self, peer: u32, params: &str) -> Vec<Outgoing> {
        let Some((target_name, msg)) = params.split_once(',') else {
            return Vec::new();
        };
        let (target_name, msg) = (target_name.trim(), msg.trim());
        let target_peer = self.world.session_snapshot().into_iter().find(|(_, s)| {
            self.accounts
                .find(&s.user)
                .and_then(|a| a.characters.get(s.char_slot as usize))
                .map(|r| r.actor.name.eq_ignore_ascii_case(target_name))
                .unwrap_or(false)
        });
        let Some((tp, _)) = target_peer else {
            return Vec::new();
        };
        let name = self.player_name(peer);
        let mut p = vec![252u8];
        p.extend_from_slice(format!("{name}: {msg}").as_bytes());
        vec![Outgoing::peer(tp, world::P_CHAT_MESSAGE, p)]
    }

    /// `/kick <name>` (`ServerNet.bb:201`, DM-gated): kick the named online player
    /// (`P_KickedPlayer` + queue the disconnect).
    fn chat_kick(&mut self, peer: u32, params: &str) -> Vec<Outgoing> {
        if !self.peer_is_dm(peer) {
            return Vec::new();
        }
        let target = params.trim();
        let target_peer = self.world.session_snapshot().into_iter().find(|(_, s)| {
            self.accounts
                .find(&s.user)
                .and_then(|a| a.characters.get(s.char_slot as usize))
                .map(|r| r.actor.name.eq_ignore_ascii_case(target))
                .unwrap_or(false)
        });
        let Some((tp, _)) = target_peer else {
            return Vec::new();
        };
        self.pending_kicks.push(tp);
        vec![Outgoing::peer(tp, world::P_KICKED_PLAYER, Vec::new())]
    }

    /// `/xp <amount>` (`ServerNet.bb:336`, DM-gated): grant XP to the sender.
    fn chat_xp(&mut self, peer: u32, params: &str) -> Vec<Outgoing> {
        if !self.peer_is_dm(peer) {
            return Vec::new();
        }
        let amount = params.trim().parse::<i32>().unwrap_or(0);
        let Some(rid) = self.world.session(peer).map(|s| s.runtime_id) else {
            return Vec::new();
        };
        self.give_xp(rid, amount)
    }

    /// `/gold <amount>` (`ServerNet.bb:339`, DM-gated): add gold to the sender +
    /// notify (`P_GoldChange` "U"/"D" + abs amount). Stored gold floors at 0.
    fn chat_gold(&mut self, peer: u32, params: &str) -> Vec<Outgoing> {
        if !self.peer_is_dm(peer) {
            return Vec::new();
        }
        let change = params.trim().parse::<i32>().unwrap_or(0);
        let Some((user, slot)) = self.player_loc_for_peer(peer) else {
            return Vec::new();
        };
        if let Some(rec) = self.accounts.find_mut(&user).and_then(|a| a.characters.get_mut(slot)) {
            rec.actor.gold = (rec.actor.gold as i64 + change as i64).clamp(0, i32::MAX as i64) as i32;
            self.accounts_dirty = true;
        }
        vec![Outgoing::peer(peer, world::P_GOLD_CHANGE, gold_change_packet(change))]
    }

    /// `/setattribute Name,Value` (`ServerNet.bb:351`, DM-gated): set one of the
    /// sender's attribute values (clamped to `[0, max]`) + broadcast.
    fn chat_setattribute(&mut self, peer: u32, params: &str) -> Vec<Outgoing> {
        if !self.peer_is_dm(peer) {
            return Vec::new();
        }
        let Some((name, value)) = params.split_once(',') else {
            return Vec::new();
        };
        let value = value.trim().parse::<i32>().unwrap_or(0);
        let Some(rid) = self.world.session(peer).map(|s| s.runtime_id) else {
            return Vec::new();
        };
        self.set_attribute_value(rid, name.trim(), value)
    }

    /// `/setattributemax Name,Value` (`ServerNet.bb:365`, DM-gated).
    fn chat_setattributemax(&mut self, peer: u32, params: &str) -> Vec<Outgoing> {
        if !self.peer_is_dm(peer) {
            return Vec::new();
        }
        let Some((name, value)) = params.split_once(',') else {
            return Vec::new();
        };
        let value = value.trim().parse::<i32>().unwrap_or(0);
        let Some(rid) = self.world.session(peer).map(|s| s.runtime_id) else {
            return Vec::new();
        };
        self.set_attribute_max(rid, name.trim(), value)
    }

    /// `/script Name,Method` (`ServerNet.bb:379`, DM-gated): spawn the named content
    /// script **privileged** (the DM is verified, so it may call gated BVMs). The
    /// script runs async; its packets emit via `pump_scripts`.
    fn chat_script(&mut self, peer: u32, params: &str) -> Vec<Outgoing> {
        if !self.peer_is_dm(peer) {
            return Vec::new();
        }
        let Some((name, method)) = params.split_once(',') else {
            return Vec::new();
        };
        let Some(rid) = self.world.session(peer).map(|s| s.runtime_id) else {
            return Vec::new();
        };
        let Some(prog) = self.scripts.linked_program(name.trim()) else {
            return Vec::new();
        };
        self.spawn_program(prog, method.trim(), (rid, 0, peer), true, String::new());
        Vec::new()
    }

    /// Set an attribute value by name for a player rid (clamped to `[0, max]`) +
    /// broadcast `P_StatUpdate "A"` to same-area players (mirrors the BVM path).
    fn set_attribute_value(&mut self, rid: u16, name: &str, new_val: i32) -> Vec<Outgoing> {
        let Some(idx) = self.attr_names.index_of(name) else {
            return Vec::new();
        };
        let Some((user, slot)) = self.player_loc(rid) else {
            return Vec::new();
        };
        let value = {
            let Some(rec) = self.accounts.find_mut(&user).and_then(|a| a.characters.get_mut(slot)) else {
                return Vec::new();
            };
            let max = rec.actor.attributes.maximum.get(idx).copied().unwrap_or(0) as i32;
            let v = new_val.clamp(0, max.max(0)) as i16;
            if let Some(s) = rec.actor.attributes.value.get_mut(idx) {
                *s = v;
            }
            v
        };
        self.accounts_dirty = true;
        self.broadcast_stat_update(rid, idx, value, b'A')
    }

    /// Set an attribute MAXIMUM by name for a player rid + broadcast `P_StatUpdate "M"`.
    fn set_attribute_max(&mut self, rid: u16, name: &str, new_max: i32) -> Vec<Outgoing> {
        let Some(idx) = self.attr_names.index_of(name) else {
            return Vec::new();
        };
        let Some((user, slot)) = self.player_loc(rid) else {
            return Vec::new();
        };
        let value = {
            let Some(rec) = self.accounts.find_mut(&user).and_then(|a| a.characters.get_mut(slot)) else {
                return Vec::new();
            };
            let v = new_max.max(0) as i16;
            if let Some(s) = rec.actor.attributes.maximum.get_mut(idx) {
                *s = v;
            }
            v
        };
        self.accounts_dirty = true;
        self.broadcast_stat_update(rid, idx, value, b'M')
    }

    /// Broadcast a `P_StatUpdate` (`[sub][u16 rid][u8 idx][u16 value]`) to every
    /// player sharing the actor's area (`sub` = `'A'` value / `'M'` max).
    fn broadcast_stat_update(&self, rid: u16, idx: usize, value: i16, sub: u8) -> Vec<Outgoing> {
        let Some(area) = self.world.session_for_runtime(rid).map(|s| s.area.clone()) else {
            return Vec::new();
        };
        let mut a = vec![sub];
        a.extend_from_slice(&rid.to_le_bytes());
        a.push(idx as u8);
        a.extend_from_slice(&(value as u16).to_le_bytes());
        self.world
            .session_snapshot()
            .into_iter()
            .filter(|(_, s)| s.area == area)
            .map(|(pb, _)| Outgoing::peer(pb, world::P_STAT_UPDATE, a.clone()))
            .collect()
    }

    /// `(user, char_slot)` for a peer's live session.
    fn player_loc_for_peer(&self, peer: u32) -> Option<(String, usize)> {
        let sess = self.world.session(peer)?;
        Some((sess.user.clone(), sess.char_slot as usize))
    }

    /// Resolve a `P_ItemScript` target runtime id to a valid same-area actor
    /// within `InteractDist` of the clicker; 0 if absent / cross-area / too far
    /// (`ServerNet.bb:1402` gate). 0 input → 0 (self-use, no target).
    fn resolve_item_target(&self, target: u16, clicker: &crate::world::WorldSession) -> u16 {
        if target == 0 {
            return 0;
        }
        let pos = self
            .world
            .session_for_runtime(target)
            .map(|s| (s.area.clone(), s.x, s.z))
            .or_else(|| self.spawns.npc(target).map(|n| (n.area.clone(), n.x, n.z)));
        let Some((area, tx, tz)) = pos else {
            return 0;
        };
        if area != clicker.area {
            return 0;
        }
        let dx = clicker.x - tx;
        let dz = clicker.z - tz;
        if dx * dx + dz * dz >= INTERACT_DIST {
            return 0;
        }
        target
    }

    /// `P_ItemScript` (`ServerNet.bb:1393`): a player uses (activates) an
    /// inventory item that has a use-script — distinct from `P_EatItem`
    /// (potions). Wire = `[u8 slot]` (self) or `[u8 slot][u16 targetRid]`. Runs
    /// the item's `Script$`/`SMethod$` with `actor = clicker`, `context = target`.
    /// Exclusivity (class/race) isn't enforced — the item parser omits those
    /// fields, same as `handle_eat_item`.
    pub fn handle_item_script(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.is_empty() {
            return Vec::new();
        }
        let slot = payload[0] as usize;
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        // Optional target (length 3) — same-area + range gated; drop on a bad one.
        let ctx_rid = if payload.len() >= 3 {
            let target = u16::from_le_bytes([payload[1], payload[2]]);
            let resolved = self.resolve_item_target(target, &sess);
            if resolved == 0 && target != 0 {
                return Vec::new();
            }
            resolved
        } else {
            0
        };
        // Validate the slot has a stocked item with a use-script.
        let (script, method) = {
            let Some(rec) = self
                .accounts
                .find(&sess.user)
                .and_then(|a| a.characters.get(sess.char_slot as usize))
            else {
                return Vec::new();
            };
            let Some(islot) = rec.actor.inventory.get(slot) else {
                return Vec::new();
            };
            let item = match &islot.item {
                Some(it) if islot.amount > 0 => it,
                _ => return Vec::new(),
            };
            let Some(def) = self.items.get(item.item_id) else {
                return Vec::new();
            };
            if def.script.is_empty() {
                return Vec::new();
            }
            let method = if def.smethod.is_empty() {
                "Main".to_string()
            } else {
                def.smethod.clone()
            };
            (def.script.clone(), method)
        };
        self.fire_hook_async(&script, &method, sess.runtime_id, ctx_rid, peer);
        Vec::new()
    }

    /// Unblock the parked `GetWaitResult` of `peer`'s suspended script with
    /// `result`. (`pub` so tests can drive the dialog loop.)
    pub fn resume_script(&mut self, peer: u32, result: String) {
        // Find by peer only — NOT by `waiting`: the client can reply to a dialog
        // packet before the script thread has reached `SetWaiting`/`GetWaitResult`.
        // Prefer a parked (already-waiting) script if one exists.
        // Match on `wait_peer` (the player the current dialog/input was sent to),
        // which equals the owner `peer` except for a cross-player dialog.
        let idx = self
            .running_scripts
            .iter()
            .position(|r| r.wait_peer == peer && r.waiting_reply.is_some())
            .or_else(|| self.running_scripts.iter().position(|r| r.wait_peer == peer));
        if let Some(i) = idx {
            let rs = &mut self.running_scripts[i];
            if let Some(reply) = rs.waiting_reply.take() {
                // Already parked on GetWaitResult — deliver immediately.
                rs.wait_result = result.clone();
                let _ = reply.send(rcce_script::Value::Str(result));
            } else {
                // Arrived before the wait — buffer; the next GetWaitResult (after
                // the script's SetWaiting clears wait_result) consumes it.
                rs.pending_response = Some(result);
            }
        }
    }

    /// Resume any script parked on `WaitSpeak` for `speaker_rid` (it spoke).
    fn resume_speak_waits(&mut self, speaker_rid: u16) {
        for rs in self.running_scripts.iter_mut() {
            if rs.wait_speak == speaker_rid && speaker_rid != 0 && rs.waiting_reply.is_some() {
                if let Some(reply) = rs.waiting_reply.take() {
                    rs.wait_result = "1".to_string();
                    let _ = reply.send(rcce_script::Value::Str("1".to_string()));
                    rs.wait_speak = 0;
                }
            }
        }
    }

    /// Resume any script parked on `WaitKill` for `killed_rid` (the target died).
    fn resume_kill_waits(&mut self, killed_rid: u16) {
        for rs in self.running_scripts.iter_mut() {
            if rs.wait_kill == killed_rid && rs.waiting_reply.is_some() {
                if let Some(reply) = rs.waiting_reply.take() {
                    rs.wait_result = "1".to_string();
                    let _ = reply.send(rcce_script::Value::Str("1".to_string()));
                    rs.wait_kill = 0;
                }
            }
        }
    }

    /// Drain the peers a `KickPlayer` script asked to disconnect — the tick loop
    /// calls this and disconnects each (after the `P_KickedPlayer` packet flushes).
    pub fn take_pending_kicks(&mut self) -> Vec<u32> {
        std::mem::take(&mut self.pending_kicks)
    }

    /// Number of scripts currently running (for tests / diagnostics).
    pub fn running_script_count(&self) -> usize {
        self.running_scripts.len()
    }

    /// Handle `P_Examine` (`ServerNet.bb:1529`): the clicker examines a target
    /// actor → run the `Examine` script function with actor = clicker, context
    /// = target. (Until per-actor script assignment is ported, the shipped
    /// `Default` script's `Examine` runs — it `Output`s the target's name.)
    pub fn handle_examine(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 2 {
            return Vec::new();
        }
        let target_rid = u16::from_le_bytes([payload[0], payload[1]]);
        let Some(actor_rid) = self.world.session(peer).map(|s| s.runtime_id) else {
            return Vec::new();
        };
        self.fire_hook("Default", "Examine", actor_rid, target_rid)
    }

    /// `P_RightClick` (`ServerNet.bb:1454`): a player right-clicks an actor. If
    /// the target is a same-area, in-range NPC with a `SpawnActorScript$`, fire
    /// that script's `Main` async (clicker = `Actor()`, NPC = `ContextActor()`) —
    /// the gateway to dialog / vendor / quest interactions. The dialog packets it
    /// produces flow out through [`pump_scripts`](Self::pump_scripts).
    ///
    /// Same-area + range gate mirrors the Blitz handler: without it a wire-
    /// injecting client could trigger any actor's script from anywhere on the
    /// shard. Mount/PausedScript-resume branches are deferred.
    pub fn handle_right_click(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 2 {
            return Vec::new();
        }
        let target_rid = u16::from_le_bytes([payload[0], payload[1]]);
        let Some(clicker) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        let Some(npc) = self.spawns.npc(target_rid) else {
            return Vec::new();
        };
        // Same-area gate.
        if npc.area != clicker.area {
            return Vec::new();
        }
        // Range gate: squared planar distance < InteractDist (Actors.bb:31).
        let dx = clicker.x - npc.x;
        let dz = clicker.z - npc.z;
        if dx * dx + dz * dz >= INTERACT_DIST {
            return Vec::new();
        }
        let clicker_rid = clicker.runtime_id;
        let npc_actor_id = npc.actor_id;
        let script = npc.script.clone();
        // Scriptless target: Blitz's right-click `ElseIf A2\Actor\Rideable` branch
        // (ServerNet.bb:1488). Mount the actor if it's rideable, not already
        // leashed to someone else, and the clicker isn't already mounted.
        if script.is_empty() {
            let rideable = self.catalog.get(npc_actor_id).map(|t| t.rideable).unwrap_or(false);
            let leader_ok = match self.spawns.npc_leader(target_rid) {
                None => true,
                Some(l) => l == clicker_rid,
            };
            if rideable && leader_ok && self.world.mount_of(peer) == 0 {
                self.world.set_mount(peer, target_rid);
                // Stop the mount's own AI + clear any target (Blitz sets AI_Wait);
                // collectors skip a ridden NPC and the per-tick glue tracks it.
                self.spawns.clear_target(target_rid);
                // Blitz fires the shipped Mount script on mount —
                // `ThreadScript("Mount", "Mount", Handle(AI), Handle(A2))`
                // (`ServerNet.bb:1502`), actor = rider, ctx = mount.
                self.fire_hook_async("Mount", "Mount", clicker_rid, target_rid, peer);
            }
            return Vec::new();
        }
        // Don't re-fire while this (clicker, NPC) conversation is already live —
        // parity with the `Si\AI = Handle(AI) And Si\AIContext = Handle(A2)` check.
        if self
            .running_scripts
            .iter()
            .any(|r| r.actor == clicker_rid && r.ctx == target_rid)
        {
            return Vec::new();
        }
        self.fire_hook_async(&script, "Main", clicker_rid, target_rid, peer);
        Vec::new()
    }

    /// `P_Dismount` (`=47`, `ServerNet.bb:1802`): the rider gets off their mount.
    /// Clears the mount relationship and parks the mount where it stands (Blitz
    /// resets `DestX/Z` to its position + restores `AI_Patrol` for an NPC mount).
    /// The cleared `mount_rid` propagates to peers on the next standard-update
    /// relay (offset 21 → 0), so clients detach the mount.
    pub fn handle_dismount(&mut self, peer: u32) -> Vec<Outgoing> {
        let mount_rid = self.world.mount_of(peer);
        if mount_rid == 0 {
            return Vec::new();
        }
        // Blitz fires the shipped Mount script's Dismount before clearing the
        // link — `ThreadScript("Mount", "Dismount", Handle(AI), Handle(AI\Mount))`
        // (`ServerNet.bb:1806`), actor = rider, ctx = ex-mount. The shipped
        // `Mount.rsl` nudges the rider +5 Z to un-clip them from the mount mesh.
        if let Some(rider_rid) = self.world.session(peer).map(|s| s.runtime_id) {
            self.fire_hook_async("Mount", "Dismount", rider_rid, mount_rid, peer);
        }
        self.world.set_mount(peer, 0);
        // Park the mount at its current spot so it doesn't resume a stale walk.
        if let Some(n) = self.spawns.npc(mount_rid) {
            let (x, z) = (n.x, n.z);
            self.spawns.set_npc_dest(mount_rid, x, z);
        }
        Vec::new()
    }

    /// `P_Trade` (`=62`, `ServerNet.bb:1570`): a player initiates trade with a
    /// same-area, in-range NPC. Fires the `Default.Trade` script (caster =
    /// `Actor()`, NPC = `ContextActor()`), which calls `OpenTrading` → the vendor
    /// window. Same gates as right-click.
    pub fn handle_trade(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 2 {
            return Vec::new();
        }
        let target_rid = u16::from_le_bytes([payload[0], payload[1]]);
        let Some(clicker) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        // The target may be an NPC vendor or another player.
        let Some((tarea, tx, tz)) = self
            .spawns
            .npc(target_rid)
            .map(|n| (n.area.clone(), n.x, n.z))
            .or_else(|| self.world.session_for_runtime(target_rid).map(|s| (s.area.clone(), s.x, s.z)))
        else {
            return Vec::new();
        };
        if tarea != clicker.area {
            return Vec::new();
        }
        let (dx, dz) = (clicker.x - tx, clicker.z - tz);
        if dx * dx + dz * dz >= INTERACT_DIST {
            return Vec::new();
        }
        self.fire_hook_async("Default", "Trade", clicker.runtime_id, target_rid, peer);
        Vec::new()
    }

    /// `OpenTrading(actor, npc)` (`BVM_OPENTRADING`) — open the vendor window for
    /// a player↔NPC trade. Marks the player trading (`IsTrading = 1`) and sends
    /// `P_OpenTrading "11"` + the vendor's stock. NPCs carry no stock in the port
    /// yet, so the window opens empty (sell-only). Player↔player trade deferred.
    fn open_trading(&mut self, actor_rid: u16, ctx_rid: u16) -> Vec<Outgoing> {
        let Some(peer) = self.world.peer_for_runtime(actor_rid) else {
            return Vec::new();
        };
        // NPC vendor: open the shop window with the vendor's stock (each item:
        // 83-byte instance + amount + a paid assigned handle the buy path uses).
        if let Some(stock) = self.spawns.npc(ctx_rid).map(|n| n.stock.clone()) {
            self.trading.insert(peer);
            let mut window = b"11".to_vec();
            for (item_id, amount) in stock {
                if amount <= 0 {
                    continue;
                }
                let handle = self.next_drop_handle;
                self.next_drop_handle = self.next_drop_handle.wrapping_add(1).max(1);
                self.assigned_items.push(AssignedItem { handle, item_id, amount, peer, free: false });
                let inst = rcce_server_core::item::ItemInstance::new(item_id);
                window.extend_from_slice(&inst.to_wire_bytes());
                window.extend_from_slice(&(amount as u16).to_le_bytes());
                window.extend_from_slice(&handle.to_le_bytes());
            }
            return vec![Outgoing::peer(peer, world::P_OPEN_TRADING, window)];
        }
        // Player↔player: ctx is another online player. Pair them + invite both.
        if let Some(partner) = self.world.peer_for_runtime(ctx_rid) {
            if partner != peer
                && !self.player_trades.contains_key(&peer)
                && !self.player_trades.contains_key(&partner)
            {
                self.player_trades.insert(peer, PlayerTrade { partner, ..Default::default() });
                self.player_trades.insert(partner, PlayerTrade { partner: peer, ..Default::default() });
                let invite = |to: u32| Outgoing::peer(to, world::P_CHAT_MESSAGE, {
                    let mut m = vec![254u8];
                    m.extend_from_slice(b"Trade request opened.");
                    m
                });
                return vec![invite(peer), invite(partner)];
            }
        }
        Vec::new()
    }

    /// `P_UpdateTrading` (`=41`, `ServerNet.bb:786`): the player sets the offered
    /// amount for **one** backpack slot — `[u8 backpackSlot(0..31)][u16 amount]`
    /// (the real client sends one of these per item; the inventory slot is
    /// `backpackSlot + SlotI_Backpack`). The amount is clamped to the stack the
    /// player actually holds (`TradeOfferedAmount`, anti-dupe). Re-offering clears
    /// both accepts. Verified against the client encoder (`Interface3D.bb:2404`).
    pub fn handle_update_trading(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 3 || !self.player_trades.contains_key(&peer) {
            return Vec::new();
        }
        let backpack_slot = payload[0] as usize;
        if backpack_slot > 31 {
            return Vec::new();
        }
        let inv_slot = backpack_slot + SLOT_BACKPACK; // SlotI_Backpack = 14
        let amount = u16::from_le_bytes([payload[1], payload[2]]) as i16;
        // Clamp to the stack actually held in that slot.
        let held = self
            .world
            .session(peer)
            .and_then(|s| {
                self.accounts
                    .find(&s.user)
                    .and_then(|a| a.characters.get(s.char_slot as usize))
                    .and_then(|r| r.actor.inventory.get(inv_slot))
                    .map(|islot| islot.amount)
            })
            .unwrap_or(0);
        let amt = amount.clamp(0, held);

        let partner = match self.player_trades.get_mut(&peer) {
            Some(t) => {
                t.item_offers.retain(|(s, _)| *s != inv_slot);
                if amt > 0 {
                    t.item_offers.push((inv_slot, amt));
                }
                t.accepted = false;
                t.partner
            }
            None => return Vec::new(),
        };
        if let Some(p) = self.player_trades.get_mut(&partner) {
            p.accepted = false;
        }
        Vec::new()
    }

    /// Accept a player↔player trade. The accept packet's first 4 bytes are this
    /// player's signed gold (`ServerNet.bb:964`). When both sides have accepted,
    /// execute the atomic swap.
    fn accept_player_trade(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        let gold = if payload.len() >= 4 {
            i32::from_le_bytes([payload[0], payload[1], payload[2], payload[3]])
        } else {
            0
        };
        let partner = match self.player_trades.get_mut(&peer) {
            Some(t) => {
                t.accept_gold = gold;
                t.accepted = true;
                t.partner
            }
            None => return Vec::new(),
        };
        let both = self
            .player_trades
            .get(&partner)
            .map(|t| t.accepted && t.partner == peer)
            .unwrap_or(false);
        if !both {
            return Vec::new();
        }
        self.complete_player_trade(peer, partner)
    }

    /// Execute a confirmed player↔player trade: move each side's offered items to
    /// the other and settle the gold difference, clamped to what each player
    /// actually holds (anti-dupe/scam, parity with the server-authoritative
    /// `TradeOfferedAmount` clamp). Sends `P_GoldChange` + `P_CloseTrading`.
    fn complete_player_trade(&mut self, a: u32, b: u32) -> Vec<Outgoing> {
        let mut out = Vec::new();
        let (a_trade, b_trade) = match (self.player_trades.get(&a), self.player_trades.get(&b)) {
            (Some(x), Some(y)) => (x.clone(), y.clone()),
            _ => return out,
        };
        // Resolve both characters' (user, slot).
        let Some((au, asl)) = sess_user_slot(&self.world, a) else { return out };
        let Some((bu, bsl)) = sess_user_slot(&self.world, b) else { return out };

        // Gold: both sides must report mirror values, and the payer must afford
        // it (`ServerNet.bb:964-968`). `a2cost` is the net gold a gains / b loses.
        let a2cost = b_trade.accept_gold;
        if a_trade.accept_gold != -a2cost {
            // Disagreement on the gold flow — abort the trade, close cleanly.
            self.player_trades.remove(&a);
            self.player_trades.remove(&b);
            out.push(Outgoing::peer(a, world::P_CLOSE_TRADING, Vec::new()));
            out.push(Outgoing::peer(b, world::P_CLOSE_TRADING, Vec::new()));
            return out;
        }
        if (a2cost > 0 && a2cost > self.gold_of(&bu, bsl))
            || (a2cost < 0 && -a2cost > self.gold_of(&au, asl))
        {
            self.player_trades.remove(&a);
            self.player_trades.remove(&b);
            out.push(Outgoing::peer(a, world::P_CLOSE_TRADING, Vec::new()));
            out.push(Outgoing::peer(b, world::P_CLOSE_TRADING, Vec::new()));
            return out;
        }

        // Take each side's offered items (clamped) out of their inventory.
        let a_items = self.take_offered(&au, asl, &a_trade.item_offers);
        let b_items = self.take_offered(&bu, bsl, &b_trade.item_offers);
        // Deposit the partner's items.
        for (id, attrs, amt) in b_items {
            self.deposit_item(&au, asl, id, attrs, amt);
        }
        for (id, attrs, amt) in a_items {
            self.deposit_item(&bu, bsl, id, attrs, amt);
        }

        // Settle the net gold (a gains a2cost, b loses it).
        self.add_gold(&au, asl, a2cost);
        self.add_gold(&bu, bsl, -a2cost);
        out.push(Outgoing::peer(a, world::P_GOLD_CHANGE, gold_change_packet(a2cost)));
        out.push(Outgoing::peer(b, world::P_GOLD_CHANGE, gold_change_packet(-a2cost)));

        // Close both sides.
        self.player_trades.remove(&a);
        self.player_trades.remove(&b);
        out.push(Outgoing::peer(a, world::P_CLOSE_TRADING, Vec::new()));
        out.push(Outgoing::peer(b, world::P_CLOSE_TRADING, Vec::new()));
        self.accounts_dirty = true;
        out
    }

    /// Remove the offered `(slot, amount)` items from a character (clamped to the
    /// stack held), returning `(item_id, attr_values, amount)` for each.
    fn take_offered(&mut self, user: &str, slot_idx: usize, offers: &[(usize, i16)]) -> Vec<(u16, Vec<i16>, i16)> {
        let mut taken = Vec::new();
        let Some(rec) = self.accounts.find_mut(user).and_then(|a| a.characters.get_mut(slot_idx)) else {
            return taken;
        };
        for &(slot, amount) in offers {
            if let Some(islot) = rec.actor.inventory.get_mut(slot) {
                if let Some(item) = islot.item.clone() {
                    let take = amount.min(islot.amount).max(0);
                    if take > 0 {
                        islot.amount -= take;
                        if islot.amount <= 0 {
                            islot.item = None;
                            islot.amount = 0;
                        }
                        taken.push((item.item_id, item.attr_values, take));
                    }
                }
            }
        }
        taken
    }

    /// Add an item stack to a character's first empty backpack slot (or merge).
    fn deposit_item(&mut self, user: &str, slot_idx: usize, item_id: u16, attrs: Vec<i16>, amount: i16) {
        let Some(rec) = self.accounts.find_mut(user).and_then(|a| a.characters.get_mut(slot_idx)) else {
            return;
        };
        let inv = &mut rec.actor.inventory;
        // Prefer merging onto a matching backpack stack.
        for slot in inv.iter_mut().skip(SLOT_BACKPACK) {
            if slot.item.as_ref().map(|i| i.item_id) == Some(item_id) {
                slot.amount = slot.amount.saturating_add(amount);
                return;
            }
        }
        // Else first empty backpack slot.
        for slot in inv.iter_mut().skip(SLOT_BACKPACK) {
            if slot.item.is_none() {
                let mut inst = rcce_server_core::item::ItemInstance::new(item_id);
                inst.attr_values = attrs;
                slot.item = Some(inst);
                slot.amount = amount;
                return;
            }
        }
        // No room — the item is lost (the client UI prevents over-capacity offers).
    }

    fn gold_of(&self, user: &str, slot_idx: usize) -> i32 {
        self.accounts.find(user).and_then(|a| a.characters.get(slot_idx)).map(|r| r.actor.gold).unwrap_or(0)
    }

    fn add_gold(&mut self, user: &str, slot_idx: usize, delta: i32) {
        if let Some(rec) = self.accounts.find_mut(user).and_then(|a| a.characters.get_mut(slot_idx)) {
            rec.actor.gold = (rec.actor.gold + delta).max(0);
        }
    }

    /// `P_OpenTrading` (`=35`, `ServerNet.bb:823`): the player accepted the
    /// vendor window. Implements the **sell** path — sold items at offset 192
    /// (`[u8 slot][u16 amount]` × 32) are removed from the inventory and their
    /// `Value` summed into gold; the gain is sent as `P_GoldChange "U"`. The buy
    /// path (vendor stock → assigned items) is deferred. Closes the trade.
    pub fn handle_open_trading(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        // A player↔player participant accepting their side.
        if self.player_trades.contains_key(&peer) {
            return self.accept_player_trade(peer, payload);
        }
        if !self.trading.remove(&peer) || payload.is_empty() {
            return Vec::new();
        }
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        let (su, ssl) = (sess.user.clone(), sess.char_slot as usize);
        let mut out = Vec::new();

        // BUY: bought items at offset 0 — 32 entries of [u32 handle][u16 amount].
        // Each handle is a paid vendor assigned item; deduct its value × amount
        // and deposit the item, if the player can afford it.
        for i in 0..32usize {
            let off = i * 6;
            if off + 6 > payload.len() {
                break;
            }
            let handle = u32::from_le_bytes([payload[off], payload[off + 1], payload[off + 2], payload[off + 3]]);
            let want = u16::from_le_bytes([payload[off + 4], payload[off + 5]]) as i16;
            if handle == 0 || want <= 0 {
                continue;
            }
            let Some(idx) = self
                .assigned_items
                .iter()
                .position(|a| a.handle == handle && a.peer == peer && !a.free)
            else {
                continue;
            };
            let (item_id, avail) = (self.assigned_items[idx].item_id, self.assigned_items[idx].amount);
            let buy = want.min(avail);
            let value = self.items.get(item_id).map(|d| d.value as i64).unwrap_or(0);
            let cost = value * buy as i64;
            if buy <= 0 || cost > self.gold_of(&su, ssl) as i64 {
                continue;
            }
            self.add_gold(&su, ssl, -(cost as i32));
            out.push(Outgoing::peer(peer, world::P_GOLD_CHANGE, gold_change_packet(-(cost as i32))));
            self.deposit_item(&su, ssl, item_id, vec![0; 40], buy);
            self.assigned_items[idx].amount -= buy;
            if self.assigned_items[idx].amount <= 0 {
                self.assigned_items.remove(idx);
            }
        }
        // The window closed — drop this peer's remaining (unsold) vendor stock.
        self.assigned_items.retain(|a| a.peer != peer || a.free);

        let mut gold_gain: i64 = 0;
        // Sold items: 32 entries of [u8 slot][u16 amount] starting at byte 192.
        for i in 0..32usize {
            let off = 192 + i * 3;
            if off + 3 > payload.len() {
                break;
            }
            let slot = payload[off] as usize;
            let amount = u16::from_le_bytes([payload[off + 1], payload[off + 2]]) as i16;
            if slot == 0 || amount <= 0 {
                continue; // slot 0 (weapon) / empty entry — Blitz requires SlotID > 0
            }
            // Resolve the item's unit value, then remove the amount.
            let value = {
                let item_id = self
                    .accounts
                    .find(&sess.user)
                    .and_then(|a| a.characters.get(sess.char_slot as usize))
                    .and_then(|r| r.actor.inventory.get(slot))
                    .and_then(|s| s.item.as_ref().map(|it| it.item_id));
                item_id.and_then(|id| self.items.get(id)).map(|d| d.value as i64).unwrap_or(0)
            };
            let sold = self
                .accounts
                .find_mut(&sess.user)
                .and_then(|a| a.characters.get_mut(sess.char_slot as usize))
                .and_then(|r| r.actor.inventory.get_mut(slot))
                .map(|islot| {
                    if islot.item.is_none() {
                        return 0i16;
                    }
                    let take = amount.min(islot.amount);
                    islot.amount -= take;
                    if islot.amount <= 0 {
                        islot.item = None;
                        islot.amount = 0;
                    }
                    take
                })
                .unwrap_or(0);
            gold_gain += value * sold as i64;
        }
        if gold_gain > 0 {
            // Credit the sale gold + tell the client (P_GoldChange "U" + amount).
            if let Some(rec) = self
                .accounts
                .find_mut(&su)
                .and_then(|a| a.characters.get_mut(ssl))
            {
                rec.actor.gold = (rec.actor.gold as i64 + gold_gain).min(i32::MAX as i64) as i32;
                self.accounts_dirty = true;
            }
            let mut p = vec![b'U'];
            p.extend_from_slice(&(gold_gain.min(u32::MAX as i64) as u32).to_le_bytes());
            out.push(Outgoing::peer(peer, world::P_GOLD_CHANGE, p));
        }
        out
    }

    /// Warp a player actor to `area_name`'s `portal_name` portal — the script
    /// `Warp` BVM / `SetArea` (`GameServer.bb:1163`). Moves the session, tells
    /// the player it changed zone (`P_ChangeArea`), removes it from the old
    /// area's peers (`P_ActorGone`) and resets known-sets so the next broadcast
    /// pass re-introduces it in the new area. A same-area warp instead
    /// repositions it for the other players (`P_RepositionActor`). Returns the
    /// packets to send. No-op (logged) if the actor isn't a live player or the
    /// destination area can't be loaded (parity with `SetArea`'s Null-area bail).
    pub fn warp_actor(&mut self, rid: u16, area_name: &str, portal_name: &str) -> Vec<Outgoing> {
        let mut out = Vec::new();
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return out;
        };
        let old_area = self.world.session(peer).map(|s| s.area.clone()).unwrap_or_default();
        let Some(area) = rcce_server_core::area::Area::load(&self.config.data_dir, area_name) else {
            // Unknown destination — refuse rather than strand the player at origin.
            return out;
        };
        // Destination position: the named portal, else origin.
        let dest_portal = area.portals.iter().find(|p| p.name.eq_ignore_ascii_case(portal_name));
        let (px, py, pz, pyaw) = dest_portal
            .map(|p| (p.x, p.y, p.z, p.yaw))
            .unwrap_or((0.0, 0.0, 0.0, 0.0));
        let resolved_portal = dest_portal.map(|p| p.name.clone());
        let changed_area = !old_area.eq_ignore_ascii_case(&area.name);

        // Tell the old area's players the actor left.
        if changed_area {
            let gone = world::actor_gone_payload(rid);
            for (pb, sb) in self.world.session_snapshot() {
                if pb != peer && sb.area == old_area {
                    out.push(Outgoing::peer(pb, world::P_ACTOR_GONE, gone.clone()));
                }
            }
        }

        // Move the session; mint/stable zone id for the destination.
        self.world.warp_session(peer, area.name.clone(), px, py, pz);
        // Mark the destination portal as occupied so the per-tick portal check
        // doesn't immediately bounce the player back through it.
        self.world.set_in_portal(peer, resolved_portal.map(|n| (area.name.clone(), n)));
        let server_area = self.world.area_id(&area.name);

        if changed_area {
            // Re-learn the new area's actors; let everyone re-see the warper.
            self.world.clear_known(peer);
            self.world.forget_runtime(rid);
        } else {
            // Same area: tell the other players the actor jumped position.
            let repo = world::reposition_payload(rid, px, py, pz);
            for (pb, sb) in self.world.session_snapshot() {
                if pb != peer && sb.area == area.name {
                    out.push(Outgoing::peer(pb, world::P_REPOSITION_ACTOR, repo.clone()));
                }
            }
        }

        // Tell the warping player it changed zone, carrying the destination's
        // current weather in the P_ChangeArea byte (Blitz sends area weather on entry).
        let weather = self.ensure_weather(&area.name);
        out.push(Outgoing::peer(
            peer,
            world::P_CHANGE_AREA,
            world::change_area_payload(px, py, pz, pyaw, area.pvp, area.gravity, server_area, weather, &area.name),
        ));
        out
    }

    /// Pick a weather byte (0 = clear, 1..=5) from an area's `WeatherChance[5]`
    /// cumulative probability bands given a `Rand(1,100)` roll — Blitz
    /// `UpdateWeather` (`ServerAreas.bb:84-92`). Chances that don't sum to 100
    /// leave the remainder clear (0).
    pub(crate) fn roll_weather(chance: &[u8; 5], roll: i32) -> u8 {
        let mut min = 0i32;
        for (i, &c) in chance.iter().enumerate() {
            if c > 0 {
                let max = min + c as i32;
                if roll >= min && roll < max {
                    return (i + 1) as u8;
                }
                min = max;
            }
        }
        0
    }

    /// An area's `WeatherChance[5]` from the (cached) area file; all-zero if the
    /// area can't be loaded.
    fn area_weather_chance(&mut self, area: &str) -> [u8; 5] {
        if !self.area_cache.contains_key(area) {
            let loaded = rcce_server_core::area::Area::load(&self.config.data_dir, area);
            self.area_cache.insert(area.to_string(), loaded);
        }
        self.area_cache
            .get(area)
            .and_then(|a| a.as_ref())
            .map(|a| a.weather_chance)
            .unwrap_or([0; 5])
    }

    /// Current weather byte for `area`, lazily initialising its runtime state to
    /// `(clear, timer 0)` so the next `tick_weather` rolls it (Blitz `AreaInstance`
    /// starts `CurrentWeather=0`/`CurrentWeatherTime=0`).
    fn ensure_weather(&mut self, area: &str) -> u8 {
        self.area_weather.entry(area.to_string()).or_insert((0, 0)).0
    }

    /// Advance per-area weather (`UpdateWeather`, `ServerAreas.bb:70`): for every
    /// area that currently has a player, count its timer down; on expiry roll new
    /// weather from the area's `WeatherChance` bands, reset the timer to
    /// `Rand(2500,10000)`, and broadcast `P_WeatherChange` (`[areaId u32][weather u8]`)
    /// to that area's players. Player-less areas are left dormant.
    pub fn tick_weather(&mut self) -> Vec<Outgoing> {
        let sessions = self.world.session_snapshot();
        let mut areas: Vec<String> = Vec::new();
        for (_, s) in &sessions {
            if !areas.iter().any(|a| a == &s.area) {
                areas.push(s.area.clone());
            }
        }
        let mut out = Vec::new();
        for area in areas {
            let (cur, timer0) = self.area_weather.get(&area).copied().unwrap_or((0, 0));
            let mut timer = timer0 - 1;
            let current = if timer <= 0 {
                let chance = self.area_weather_chance(&area);
                let roll = self.rng.rand(100);
                let w = Self::roll_weather(&chance, roll);
                timer = self.rng.range(2500, 10000);
                let area_id = self.world.area_id(&area);
                let mut p = area_id.to_le_bytes().to_vec();
                p.push(w);
                for (pb, sb) in &sessions {
                    if sb.area == area {
                        out.push(Outgoing::peer(*pb, world::P_WEATHER_CHANGE, p.clone()));
                    }
                }
                w
            } else {
                cur
            };
            self.area_weather.insert(area, (current, timer));
        }
        out
    }

    /// Portals for an area (cached). Empty if the area can't be loaded.
    fn area_portals(&mut self, name: &str) -> Vec<rcce_server_core::area::Portal> {
        if !self.area_cache.contains_key(name) {
            let loaded = rcce_server_core::area::Area::load(&self.config.data_dir, name);
            self.area_cache.insert(name.to_string(), loaded);
        }
        self.area_cache
            .get(name)
            .and_then(|a| a.as_ref())
            .map(|a| a.portals.clone())
            .unwrap_or_default()
    }

    /// Per-tick portal traversal (`Server.bb:584`): a player standing inside a
    /// linked portal is warped to its destination. Edge-triggered via the
    /// session's `in_portal` so it fires once on entry and the destination portal
    /// doesn't bounce them back. Returns the warp packets to send.
    pub fn check_portals(&mut self) -> Vec<Outgoing> {
        let mut out = Vec::new();
        for (peer, sess) in self.world.session_snapshot() {
            let portals = self.area_portals(&sess.area);
            // The first linked portal the player is inside (squared distance).
            let occupied = portals.iter().find(|p| {
                if p.link_area.is_empty() || p.name.is_empty() {
                    return false;
                }
                let (dx, dy, dz) = (sess.x - p.x, sess.y - p.y, sess.z - p.z);
                dx * dx + dy * dy + dz * dz < p.size * p.size
            });
            match occupied {
                Some(p) => {
                    let here = (sess.area.clone(), p.name.clone());
                    // Already standing in this portal (just warped onto it, or
                    // mid-traversal) → don't re-fire.
                    if self.world.session(peer).and_then(|s| s.in_portal.clone()) == Some(here.clone()) {
                        continue;
                    }
                    // Entering a new portal → warp through its link.
                    let pkts = self.warp_actor(sess.runtime_id, &p.link_area, &p.link_name);
                    let warped = !pkts.is_empty();
                    out.extend(pkts);
                    // On success, warp_actor set in_portal to the destination. If
                    // the warp failed (unloadable link area), mark the source
                    // portal so we don't re-fire it every tick.
                    if !warped {
                        self.world.set_in_portal(peer, Some(here));
                    }
                }
                None => {
                    // Left all portals — clear so re-entry fires again.
                    if self.world.session(peer).and_then(|s| s.in_portal.clone()).is_some() {
                        self.world.set_in_portal(peer, None);
                    }
                }
            }
        }
        out
    }

    /// `P_EatItem` (`=44`, `ServerNet.bb:1326`): a player drinks/eats an
    /// inventory item. Validates the slot/amount, confirms it's a potion (4) or
    /// ingredient (5), runs its use-script (`Item\Script$` — e.g.
    /// `Item_HealthPotion` heals via `SetAttribute`; the script's packets flow
    /// out through [`pump_scripts`](Self::pump_scripts)), then consumes the
    /// item. The timed `ActorEffect` buff (attribute deltas + `P_ActorEffect`)
    /// is deferred — the instant effect comes from the script.
    pub fn handle_eat_item(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 3 {
            return Vec::new();
        }
        let slot = payload[0] as usize;
        let amount = u16::from_le_bytes([payload[1], payload[2]]) as i16;
        if amount <= 0 {
            return Vec::new();
        }
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        // Read the slot's item id + per-instance attribute deltas, then release.
        let (item_id, item_attrs) = {
            let Some(rec) = self
                .accounts
                .find(&sess.user)
                .and_then(|a| a.characters.get(sess.char_slot as usize))
            else {
                return Vec::new();
            };
            let Some(islot) = rec.actor.inventory.get(slot) else {
                return Vec::new();
            };
            match &islot.item {
                Some(item) if islot.amount >= amount => (item.item_id, item.attr_values.clone()),
                _ => return Vec::new(),
            }
        };
        // Must be a potion / ingredient with a use-script.
        let Some(def) = self.items.get(item_id) else {
            return Vec::new();
        };
        if def.item_type != 4 && def.item_type != 5 {
            return Vec::new();
        }
        let script = def.script.clone();
        let method = if def.smethod.is_empty() { "Main" } else { def.smethod.as_str() }.to_string();
        let eat_len = def.eat_effects_length;
        let effect_name = def.name.clone();
        let effect_icon = def.thumbnail_tex_id.max(0) as u16;

        // Consume the item (decrement; clear the slot when the stack empties).
        if let Some(islot) = self
            .accounts
            .find_mut(&sess.user)
            .and_then(|a| a.characters.get_mut(sess.char_slot as usize))
            .and_then(|r| r.actor.inventory.get_mut(slot))
        {
            islot.amount -= amount;
            if islot.amount <= 0 {
                islot.item = None;
                islot.amount = 0;
            }
        }
        self.accounts_dirty = true;

        // Run the use-script (drinker = Actor(), no context actor).
        if !script.is_empty() {
            self.fire_hook_async(&script, &method, sess.runtime_id, 0, peer);
        }
        // Timed buff: a potion with per-instance attribute deltas + a duration
        // applies an ActorEffect that expires later (`P_EatItem` buff branch).
        if eat_len > 0 && item_attrs.iter().any(|&v| v != 0) {
            return self.apply_effect(sess.runtime_id, peer, effect_name, item_attrs, eat_len as u64 * 1000, effect_icon);
        }
        Vec::new()
    }

    /// Apply a timed attribute buff (`ActorEffect`): add `deltas` to the owner's
    /// attributes now, register it to revert after `duration_ms`, and emit the
    /// `P_ActorEffect "A"` (icon) + `P_StatUpdate` packets so the client reflects
    /// the changed stats. Reverted by [`check_effects`](Self::check_effects).
    fn apply_effect(
        &mut self,
        rid: u16,
        peer: u32,
        name: String,
        deltas: Vec<i16>,
        duration_ms: u64,
        icon: u16,
    ) -> Vec<Outgoing> {
        let now = self.now_ms();
        let mut out = Vec::new();
        let mut changed: Vec<(usize, i16)> = Vec::new();
        let (u, s) = (sess_user_slot(&self.world, peer)).unwrap_or_default();
        if let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
            for (i, &d) in deltas.iter().enumerate() {
                if d != 0 {
                    if let Some(v) = rec.actor.attributes.value.get_mut(i) {
                        *v = v.saturating_add(d);
                        changed.push((i, *v));
                    }
                }
            }
            self.accounts_dirty = true;
        }
        if changed.is_empty() {
            return out;
        }
        let handle = self.next_drop_handle;
        self.next_drop_handle = self.next_drop_handle.wrapping_add(1).max(1);
        self.active_effects.push(ActorEffect {
            handle,
            peer,
            rid,
            name,
            deltas,
            expire_at_ms: now + duration_ms,
        });
        // "A" create (handle + icon) — the client's buff icon.
        let mut a = vec![b'A'];
        a.extend_from_slice(&handle.to_le_bytes());
        a.extend_from_slice(&icon.to_le_bytes());
        out.push(Outgoing::peer(peer, world::P_ACTOR_EFFECT, a));
        for (idx, newval) in changed {
            out.push(Outgoing::peer(peer, world::P_STAT_UPDATE, stat_update_a(rid, idx, newval)));
        }
        out
    }

    /// Per-tick: expire timed buffs whose duration has elapsed — revert their
    /// deltas, tell the client (`P_ActorEffect "R"` + `P_StatUpdate`). Called
    /// from the main loop.
    pub fn check_effects(&mut self) -> Vec<Outgoing> {
        let now = self.now_ms();
        let mut expired = Vec::new();
        let mut i = 0;
        while i < self.active_effects.len() {
            if self.active_effects[i].expire_at_ms <= now {
                expired.push(self.active_effects.remove(i));
            } else {
                i += 1;
            }
        }
        let mut out = Vec::new();
        for e in expired {
            out.extend(self.revert_effect(&e));
        }
        out
    }

    /// Revert one effect's deltas on its owner + emit the removal packets.
    fn revert_effect(&mut self, e: &ActorEffect) -> Vec<Outgoing> {
        let mut out = Vec::new();
        let mut changed: Vec<(usize, i16)> = Vec::new();
        let (u, s) = (sess_user_slot(&self.world, e.peer)).unwrap_or_default();
        if let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
            for (i, &d) in e.deltas.iter().enumerate() {
                if d != 0 {
                    if let Some(v) = rec.actor.attributes.value.get_mut(i) {
                        *v = v.saturating_sub(d);
                        changed.push((i, *v));
                    }
                }
            }
            self.accounts_dirty = true;
        }
        let mut r = vec![b'R'];
        r.extend_from_slice(&e.handle.to_le_bytes());
        out.push(Outgoing::peer(e.peer, world::P_ACTOR_EFFECT, r));
        for (idx, newval) in changed {
            out.push(Outgoing::peer(e.peer, world::P_STAT_UPDATE, stat_update_a(e.rid, idx, newval)));
        }
        out
    }

    /// `P_InventoryUpdate` (`=15`, `ServerNet.bb:1620`). Handles the drop (`"D"`)
    /// and pickup (`"P"`) subcommands — items on the ground. Swap/sort and the
    /// shop/trade `"G"`/`"T"` sub-protocol are deferred.
    pub fn handle_inventory_update(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        match payload.first() {
            Some(b'D') => self.handle_drop_item(peer, payload),
            Some(b'P') => self.handle_pickup_item(peer, payload),
            Some(b'S') => self.handle_swap_item(peer, payload),
            Some(b'A') => self.handle_add_item(peer, payload),
            Some(b'G') => self.handle_give_item_reply(peer, payload),
            _ => Vec::new(),
        }
    }

    /// Stack-merge `"A" + [u16 runtimeId][u8 from][u8 to][u16 amount]`
    /// (`InventoryAdd`, `Inventories.bb`): move `amount` of an item from `from`
    /// onto a **matching** stack in `to` (same item id), capped to the slot
    /// ceiling. Own inventory only. No reply (client-predicted).
    fn handle_add_item(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 7 {
            return Vec::new();
        }
        let target_rid = u16::from_le_bytes([payload[1], payload[2]]);
        let from = payload[3] as usize;
        let to = payload[4] as usize;
        let amount = u16::from_le_bytes([payload[5], payload[6]]) as i16;
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        if target_rid != sess.runtime_id || from == to || amount <= 0 {
            return Vec::new();
        }
        if let Some(rec) = self
            .accounts
            .find_mut(&sess.user)
            .and_then(|a| a.characters.get_mut(sess.char_slot as usize))
        {
            let inv = &mut rec.actor.inventory;
            if from >= inv.len() || to >= inv.len() {
                return Vec::new();
            }
            let from_id = inv[from].item.as_ref().map(|i| i.item_id);
            let to_id = inv[to].item.as_ref().map(|i| i.item_id);
            // Both slots must hold the same item to merge.
            if from_id.is_none() || from_id != to_id {
                return Vec::new();
            }
            let move_amt = amount.min(inv[from].amount).min((i16::MAX - inv[to].amount).max(0));
            if move_amt <= 0 {
                return Vec::new();
            }
            inv[to].amount += move_amt;
            inv[from].amount -= move_amt;
            if inv[from].amount <= 0 {
                inv[from].item = None;
                inv[from].amount = 0;
            }
            self.accounts_dirty = true;
        }
        Vec::new()
    }

    /// `GiveItem(actor, name, amount)` (`BVM_GIVEITEM`) — grant a player an item
    /// by name. Mints an assigned item and asks the client where to put it via
    /// `"G" + [u32 handle][u16 itemId][u16 amount]` (`ServerNet.bb:2915`); the
    /// client replies with [`handle_give_item_reply`]. Returns the packet(s).
    /// No-op if the item name is unknown or the actor isn't a live player.
    pub fn give_item(&mut self, actor_rid: u16, name: &str, amount: i16) -> Vec<Outgoing> {
        if amount <= 0 {
            return Vec::new();
        }
        let Some(peer) = self.world.peer_for_runtime(actor_rid) else {
            return Vec::new();
        };
        let Some(item_id) = self
            .items
            .items
            .iter()
            .find(|d| d.name.eq_ignore_ascii_case(name))
            .map(|d| d.id)
        else {
            return Vec::new();
        };
        let handle = self.next_drop_handle;
        self.next_drop_handle = self.next_drop_handle.wrapping_add(1).max(1);
        self.assigned_items.push(AssignedItem { handle, item_id, amount, peer, free: true });

        let mut p = vec![b'G'];
        p.extend_from_slice(&handle.to_le_bytes());
        p.extend_from_slice(&item_id.to_le_bytes());
        p.extend_from_slice(&(amount as u16).to_le_bytes());
        vec![Outgoing::peer(peer, world::P_INVENTORY_UPDATE, p)]
    }

    /// Client's reply to a `GiveItem` offer: `"G" + [u8 Y/N][u32 handle][u8 slot]`
    /// (`ServerNet.bb:1719`). On `Y`, place the assigned item into the chosen slot
    /// (empty, or stack onto a matching item); on `N` (or anything else), discard.
    fn handle_give_item_reply(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 7 {
            return Vec::new();
        }
        let accept = payload[1] == b'Y';
        let handle = u32::from_le_bytes([payload[2], payload[3], payload[4], payload[5]]);
        let slot = payload[6] as usize;
        let Some(idx) = self.assigned_items.iter().position(|a| a.handle == handle && a.peer == peer && a.free)
        else {
            return Vec::new(); // unknown handle, or paid vendor stock (buy-path only)
        };
        let assigned = self.assigned_items.remove(idx);
        if !accept {
            return Vec::new();
        }
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        if let Some(islot) = self
            .accounts
            .find_mut(&sess.user)
            .and_then(|a| a.characters.get_mut(sess.char_slot as usize))
            .and_then(|r| r.actor.inventory.get_mut(slot))
        {
            match &mut islot.item {
                None => {
                    islot.item = Some(rcce_server_core::item::ItemInstance::new(assigned.item_id));
                    islot.amount = assigned.amount;
                    self.accounts_dirty = true;
                }
                Some(existing) if existing.item_id == assigned.item_id => {
                    islot.amount = islot.amount.saturating_add(assigned.amount);
                    self.accounts_dirty = true;
                }
                _ => {} // occupied by a different item — drop the grant (client chose badly)
            }
        }
        Vec::new()
    }

    /// Swap two inventory slots `"S" + [u16 runtimeId][u8 slotA][u8 slotB][u16
    /// amount]` (`InventorySwap`, `Inventories.bb`). Supports the whole-slot swap
    /// (`amount == 0`) for the player's own inventory — the equip / rearrange
    /// path. Partial-amount moves, pet inventories, and equip-slot compatibility
    /// (`SlotsMatch`/`ActorHasSlot`) are deferred. No reply (the client predicts
    /// its own inventory; the equipped-appearance broadcast is a follow-up).
    fn handle_swap_item(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 7 {
            return Vec::new();
        }
        let target_rid = u16::from_le_bytes([payload[1], payload[2]]);
        let slot_a = payload[3] as usize;
        let slot_b = payload[4] as usize;
        let amount = u16::from_le_bytes([payload[5], payload[6]]);
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        // Own inventory only (no pet/other-actor inventory manipulation).
        if target_rid != sess.runtime_id || slot_a == slot_b || amount != 0 {
            return Vec::new();
        }
        if let Some(rec) = self
            .accounts
            .find_mut(&sess.user)
            .and_then(|a| a.characters.get_mut(sess.char_slot as usize))
        {
            let inv = &mut rec.actor.inventory;
            if slot_a < inv.len() && slot_b < inv.len() {
                inv.swap(slot_a, slot_b);
                self.accounts_dirty = true;
            }
        }
        Vec::new()
    }

    /// Drop `"D" + [u8 slot][u16 amount]`: remove from the inventory slot, place
    /// a `DroppedItem` at the player's feet, and broadcast `"D"` to the area
    /// (`[u16 amount][f32 x/y/z][u32 handle][83-byte item]`).
    fn handle_drop_item(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 4 {
            return Vec::new();
        }
        let slot = payload[1] as usize;
        let amount = u16::from_le_bytes([payload[2], payload[3]]) as i16;
        if amount <= 0 {
            return Vec::new();
        }
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        // Remove the item (clamped to the stack) and capture it.
        let dropped = {
            let Some(islot) = self
                .accounts
                .find_mut(&sess.user)
                .and_then(|a| a.characters.get_mut(sess.char_slot as usize))
                .and_then(|r| r.actor.inventory.get_mut(slot))
            else {
                return Vec::new();
            };
            let Some(item) = islot.item.clone() else {
                return Vec::new();
            };
            let take = amount.min(islot.amount);
            if take <= 0 {
                return Vec::new();
            }
            islot.amount -= take;
            if islot.amount <= 0 {
                islot.item = None;
                islot.amount = 0;
            }
            (item, take)
        };
        self.accounts_dirty = true;

        let handle = self.next_drop_handle;
        self.next_drop_handle = self.next_drop_handle.wrapping_add(1).max(1);
        let (x, y, z) = (clamp_coord(sess.x), clamp_coord(sess.y), clamp_coord(sess.z));
        self.dropped_items.push(DroppedItem {
            handle,
            item: dropped.0.clone(),
            amount: dropped.1,
            x,
            z,
            area: sess.area.clone(),
        });

        // Broadcast the new ground item to same-area players.
        let mut p = vec![b'D'];
        p.extend_from_slice(&(dropped.1 as u16).to_le_bytes());
        p.extend_from_slice(&x.to_le_bytes());
        p.extend_from_slice(&y.to_le_bytes());
        p.extend_from_slice(&z.to_le_bytes());
        p.extend_from_slice(&handle.to_le_bytes());
        p.extend_from_slice(&dropped.0.to_wire_bytes());
        let mut out = Vec::new();
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == sess.area {
                out.push(Outgoing::peer(pb, world::P_INVENTORY_UPDATE, p.clone()));
            }
        }
        out
    }

    /// Pickup `"P" + [u32 handle][u8 slot]`: claim a ground item into an empty
    /// inventory slot if same-area + in range; reply `"R"` to the picker and
    /// broadcast `"P"` (gone) to others.
    fn handle_pickup_item(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        if payload.len() < 6 {
            return Vec::new();
        }
        let handle = u32::from_le_bytes([payload[1], payload[2], payload[3], payload[4]]);
        let slot = payload[5] as usize;
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };
        let Some(idx) = self.dropped_items.iter().position(|d| d.handle == handle) else {
            return Vec::new();
        };
        // Same-area + range gate (real distance ≤ InteractDist-radius + 50).
        {
            let d = &self.dropped_items[idx];
            if d.area != sess.area {
                return Vec::new();
            }
            let (dx, dz) = (sess.x - d.x, sess.z - d.z);
            if (dx * dx + dz * dz).sqrt() > 450.0 {
                return Vec::new();
            }
        }
        // Place into the requested slot only if it's empty + in range.
        let placed = {
            let d = self.dropped_items[idx].clone();
            match self
                .accounts
                .find_mut(&sess.user)
                .and_then(|a| a.characters.get_mut(sess.char_slot as usize))
                .and_then(|r| r.actor.inventory.get_mut(slot))
            {
                Some(islot) if islot.item.is_none() => {
                    islot.item = Some(d.item);
                    islot.amount = d.amount;
                    true
                }
                _ => false,
            }
        };
        if !placed {
            return Vec::new();
        }
        self.accounts_dirty = true;
        self.dropped_items.remove(idx);

        let mut out = Vec::new();
        // "R" to the picker (handle + slot).
        let mut r = vec![b'R'];
        r.extend_from_slice(&handle.to_le_bytes());
        r.push(slot as u8);
        out.push(Outgoing::peer(peer, world::P_INVENTORY_UPDATE, r));
        // "P" (gone) to other same-area players.
        let mut gone = vec![b'P'];
        gone.extend_from_slice(&handle.to_le_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if pb != peer && sb.area == sess.area {
                out.push(Outgoing::peer(pb, world::P_INVENTORY_UPDATE, gone.clone()));
            }
        }
        out
    }

    /// `P_SpellUpdate` (`=27`, `ServerNet.bb:1130`). Handles the `"F"` (fire)
    /// subcommand: a player casts a known spell at an optional target. Resolves
    /// the spell → its `Spells.dat` use-script and runs it (caster = `Actor()`,
    /// target = `ContextActor()`), parity with `ThreadScript(Sp\Script$,
    /// Sp\SMethod$, caster, target, level)`. The script applies the effect via
    /// the attribute BVMs (`Spell_Heal` heals self; PvP `Spell_Fireball` damages
    /// a player target). Memorise/unmemorise (`"M"`/`"U"`), cooldown bookkeeping,
    /// race/class exclusivity, and **NPC-target attribute damage** (NPCs don't
    /// hold mutable attributes in the port yet) are deferred.
    pub fn handle_spell_update(&mut self, peer: u32, payload: &[u8]) -> Vec<Outgoing> {
        // Memorise / unmemorise subcommands (only meaningful when RequireMemorise).
        match payload.first() {
            Some(b'M') => return self.handle_memorise(peer, payload, true),
            Some(b'U') => return self.handle_memorise(peer, payload, false),
            Some(b'F') => {}
            _ => return Vec::new(),
        }
        if payload.len() < 3 {
            return Vec::new();
        }
        let spell_num = u16::from_le_bytes([payload[1], payload[2]]);
        let target_rid = if payload.len() >= 5 {
            u16::from_le_bytes([payload[3], payload[4]])
        } else {
            0
        };
        let Some(sess) = self.world.session(peer).cloned() else {
            return Vec::new();
        };

        // The caster must KNOW the spell (anti-cheat — can't cast arbitrary ids).
        let (knows, memorised) = self
            .accounts
            .find(&sess.user)
            .and_then(|a| a.characters.get(sess.char_slot as usize))
            .map(|r| {
                (
                    r.actor.known_spells.contains(&(spell_num as i16)),
                    r.actor.memorised_spells.contains(&(spell_num as i16)),
                )
            })
            .unwrap_or((false, false));
        if !knows {
            return Vec::new();
        }
        // When the server requires memorisation, the spell must be memorised.
        if self.require_memorise && !memorised {
            return Vec::new();
        }

        // Resolve the spell → its use-script.
        let Some(def) = self.spells_catalog.get(spell_num) else {
            return Vec::new();
        };
        if def.script.is_empty() {
            return Vec::new();
        }
        let script = def.script.clone();
        let method = if def.smethod.is_empty() { "Main" } else { def.smethod.as_str() }.to_string();
        let recharge = def.recharge_time.max(0) as u64;
        let excl_race = def.exclusive_race.clone();
        let excl_class = def.exclusive_class.clone();

        // Race / class restriction (`ServerNet.bb:1245`): a spell taught to the
        // wrong race/class (via script or a stale save) must not fire.
        let (caster_race, caster_class) = {
            let actor_id = self
                .accounts
                .find(&sess.user)
                .and_then(|a| a.characters.get(sess.char_slot as usize))
                .map(|r| r.actor.actor_id);
            actor_id
                .and_then(|id| self.catalog.get(id))
                .map(|t| (t.race.clone(), t.class.clone()))
                .unwrap_or_default()
        };
        if !excl_race.is_empty() && !caster_race.eq_ignore_ascii_case(&excl_race) {
            let mut msg = vec![253u8];
            msg.extend_from_slice(format!("{excl_race} only.").as_bytes());
            return vec![Outgoing::peer(peer, world::P_CHAT_MESSAGE, msg)];
        }
        if !excl_class.is_empty() && !caster_class.eq_ignore_ascii_case(&excl_class) {
            let mut msg = vec![253u8];
            msg.extend_from_slice(format!("{excl_class} only.").as_bytes());
            return vec![Outgoing::peer(peer, world::P_CHAT_MESSAGE, msg)];
        }

        // Cooldown: a 100 ms global cast floor + the spell's own recharge.
        let now = self.now_ms();
        let cd = self.spell_cooldowns.entry(peer).or_insert_with(|| (0, std::collections::HashMap::new()));
        // 100 ms floor only applies once the peer has cast before (cd.0 != 0) —
        // Blitz's `MilliSecs() - LastSpellFireMs` is huge for the first cast.
        if cd.0 != 0 && now.saturating_sub(cd.0) < 100 {
            return Vec::new(); // too fast — drop silently (parity)
        }
        if cd.1.get(&spell_num).is_some_and(|&ready| now < ready) {
            // Not recharged → tell the caster (P_ChatMessage, Chr$(253) prefix).
            let mut msg = vec![253u8];
            msg.extend_from_slice(b"Ability not recharged.");
            return vec![Outgoing::peer(peer, world::P_CHAT_MESSAGE, msg)];
        }
        cd.0 = now.max(1); // never 0, so the floor engages on subsequent casts
        cd.1.insert(spell_num, now + recharge);

        // Target: alive/area validation is left to the script (it guards with
        // Attribute(Target,"Health")<=0); here we only require same-area presence.
        let ctx_rid = self.resolve_spell_target(target_rid, &sess.area);

        self.fire_hook_async(&script, &method, sess.runtime_id, ctx_rid, peer);
        Vec::new()
    }

    /// `FireProjectile(source, target, name)` (`BVM_FIREPROJECTILE`) — broadcast
    /// the visual `P_Projectile` (`=37`) to the source's same-area players so they
    /// render the flying projectile (`GameServer.bb:256`). Cosmetic: the damage
    /// is already applied by the spell script. No-op if the projectile name is
    /// unknown or the source has no resolvable area.
    fn fire_projectile(&mut self, source_rid: u16, target_rid: u16, name: &str) -> Vec<Outgoing> {
        let Some(def) = self.projectiles.get_by_name(name) else {
            return Vec::new();
        };
        let Some(area) = self
            .world
            .session_for_runtime(source_rid)
            .map(|s| s.area.clone())
            .or_else(|| self.spawns.npc(source_rid).map(|n| n.area.clone()))
        else {
            return Vec::new();
        };
        // `[u16 src][u16 tgt][u16 mesh][u16 e1tex][u16 e2tex][u8 homing][u8 speed]
        //  [u8 e1len][e1][e2]`.
        let mut p = Vec::new();
        p.extend_from_slice(&source_rid.to_le_bytes());
        p.extend_from_slice(&target_rid.to_le_bytes());
        p.extend_from_slice(&(def.mesh_id as u16).to_le_bytes());
        p.extend_from_slice(&(def.emitter1_tex as u16).to_le_bytes());
        p.extend_from_slice(&(def.emitter2_tex as u16).to_le_bytes());
        p.push(def.homing);
        p.push(def.speed);
        p.push(def.emitter1.len().min(255) as u8);
        p.extend_from_slice(def.emitter1.as_bytes());
        p.extend_from_slice(def.emitter2.as_bytes());

        let mut out = Vec::new();
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area {
                out.push(Outgoing::peer(pb, world::P_PROJECTILE, p.clone()));
            }
        }
        out
    }

    /// `P_SpellUpdate "M"/"U"` (`ServerNet.bb:1135`): memorise (`memo=true`) or
    /// unmemorise a known spell. Both are gated on `RequireMemorise` (Blitz wraps
    /// both cases in `If RequireMemorise`). `"M" + [u16 spellNum]` queues a 6000 ms
    /// `MemorisingSpell` (committed in the tick loop by [`commit_memorise`], where
    /// the known-check + free-slot search happen); `"U"` clears the slot holding
    /// that spell immediately. When `RequireMemorise` is off, both are no-ops —
    /// casting doesn't consult the memorise slots.
    fn handle_memorise(&mut self, peer: u32, payload: &[u8], memo: bool) -> Vec<Outgoing> {
        if payload.len() < 3 || !self.require_memorise {
            return Vec::new();
        }
        let spell_num = u16::from_le_bytes([payload[1], payload[2]]) as i16;
        if memo {
            // Blitz queues a New MemorisingSpell with CreatedTime; the known-check
            // and slot assignment are deferred to the 6 s commit. (Blitz bounds the
            // value to its 0..999 SpellLevels index; the port stores the spell ID
            // itself in `memorised_spells` and validates via `known_spells` at
            // commit, so here we only reject a negative — a u16 > 32767 on the
            // wire.) Dedupe an already-pending request (client double-send).
            if spell_num >= 0
                && !self.pending_memorise.iter().any(|p| p.peer == peer && p.spell_num == spell_num)
            {
                let created_ms = self.now_ms();
                self.pending_memorise.push(PendingMemorise { peer, spell_num, created_ms });
            }
            return Vec::new();
        }
        // Unmemorise: clear the slot holding this spell (Blitz sets it to 5000).
        const EMPTY: i16 = 5000;
        let Some((u, s)) = sess_user_slot(&self.world, peer) else {
            return Vec::new();
        };
        if let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
            if let Some(slot) = rec.actor.memorised_spells.iter_mut().find(|m| **m == spell_num) {
                *slot = EMPTY;
                self.accounts_dirty = true;
            }
        }
        Vec::new()
    }

    /// Commit `MemorisingSpell` records older than 6000 ms (`Server.bb:720`): if
    /// the player still knows the spell and has a free `MemorisedSpells` slot,
    /// drop it in. Records are removed once their window elapses, committed or
    /// not (a stale peer / now-unknown spell just drops). Called every tick.
    fn commit_memorise(&mut self, now: u64) {
        if self.pending_memorise.is_empty() {
            return;
        }
        const EMPTY: i16 = 5000;
        let mut still_pending = Vec::new();
        for p in std::mem::take(&mut self.pending_memorise) {
            if now.saturating_sub(p.created_ms) < 6000 {
                still_pending.push(p);
                continue;
            }
            let Some((u, s)) = sess_user_slot(&self.world, p.peer) else { continue };
            if let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
                let knows = rec.actor.known_spells.contains(&p.spell_num);
                let already = rec.actor.memorised_spells.contains(&p.spell_num);
                if knows && !already {
                    if let Some(slot) = rec.actor.memorised_spells.iter_mut().find(|m| **m == EMPTY) {
                        *slot = p.spell_num;
                        self.accounts_dirty = true;
                    }
                }
            }
        }
        self.pending_memorise = still_pending;
    }

    /// Test hook: commit every pending memorise now, as if its 6 s window had
    /// elapsed — avoids a real 6 s sleep (the monotonic clock can't be wound
    /// forward, and in a sub-second test `now_ms()` is well under 6000).
    #[cfg(test)]
    pub fn force_memorise_ready(&mut self) {
        self.commit_memorise(u64::MAX);
    }

    /// A spell/interaction target is valid only if it's a player or NPC in the
    /// caster's area; otherwise the context actor is 0 (the script no-ops on it).
    fn resolve_spell_target(&self, rid: u16, caster_area: &str) -> u16 {
        if rid == 0 {
            return 0;
        }
        if let Some(s) = self.world.session_for_runtime(rid) {
            return if s.area == caster_area { rid } else { 0 };
        }
        if let Some(npc) = self.spawns.npc(rid) {
            return if npc.area == caster_area { rid } else { 0 };
        }
        0
    }

    /// Per-tick NPC AI: every NPC with a target (it was attacked) whose combat
    /// delay has elapsed attacks that player — damages their character HP and
    /// broadcasts `P_StatUpdate "A"` (the player sees their health drop), the
    /// `"Y"` damage feedback to the victim, and the `"O"` swing to bystanders.
    ///
    /// Deferred: NPC movement/chase (the NPC attacks from its spawn if the
    /// player is in the zone; range/pathing is a follow-up), player death &
    /// respawn (HP floors at 0 for now), and aggressive-on-sight targeting.
    pub fn collect_npc_attacks(&mut self, now_ms: u64) -> Vec<Outgoing> {
        let mut out = Vec::new();
        for (npc_rid, target_peer, npc_actor_id, area) in
            self.spawns.pending_attacks(now_ms, self.combat_delay)
        {
            let Some(tsess) = self.world.session(target_peer).cloned() else {
                self.spawns.clear_target(npc_rid);
                continue;
            };
            if tsess.area != area {
                self.spawns.clear_target(npc_rid);
                continue;
            }
            // Melee range gate (GameServer.bb:959-973): only swing when within
            // CheckDist of the target; out of range, keep the target and let
            // collect_npc_movement close the distance (don't mark_attacked, so
            // the swing fires the moment the NPC arrives).
            let target_actor_id = self
                .accounts
                .find(&tsess.user)
                .and_then(|a| a.characters.get(tsess.char_slot as usize))
                .map(|r| r.actor.actor_id)
                .unwrap_or(0);
            let check = self.melee_check_dist(npc_actor_id, target_actor_id);
            if let Some((nx, nz)) = self.spawns.npc(npc_rid).map(|n| (n.x, n.z)) {
                let dx = nx - tsess.x;
                let dz = nz - tsess.z;
                if dx * dx + dz * dz > check * check {
                    continue;
                }
            }
            let (strength, dtype) = self
                .catalog
                .get(npc_actor_id)
                .map(|t| {
                    (
                        t.attr_value.get(self.strength_stat).copied().unwrap_or(0) as i32,
                        t.default_damage_type,
                    )
                })
                .unwrap_or((0, 0));
            let rolls = combat::Rolls {
                to_hit: self.rng.rand(100) as u32,
                roll_5_8: self.rng.range(5, 8),
                roll_n5_5: self.rng.range(-5, 5),
                crit_roll: self.rng.rand(10) as u32,
            };
            // The victim's equipped armour mitigates the incoming hit.
            let victim_armour = match self
                .accounts
                .find(&tsess.user)
                .and_then(|a| a.characters.get(tsess.char_slot as usize))
            {
                Some(rec) => Self::equipped_armour(&self.items, &rec.actor),
                None => 0,
            };
            let input = combat::SwingInput {
                strength,
                weapon_damage: None,
                armour: victim_armour,
                resistance: 100,
                toughness: None,
            };
            let swing = combat::melee_swing(self.combat_formula, &input, &rolls);
            self.spawns.mark_attacked(npc_rid, now_ms);
            let damage = match swing {
                combat::SwingResult::Miss => -1,
                combat::SwingResult::Hit { damage, .. } => damage,
            };

            // Apply to the victim's character HP (floors at 0).
            let mut new_hp = 0i32;
            let mut hp_before = 0i32;
            if let Some(acct) = self.accounts.find_mut(&tsess.user) {
                if let Some(rec) = acct.characters.get_mut(tsess.char_slot as usize) {
                    hp_before = rec.actor.attributes.value.get(self.health_stat).copied().unwrap_or(0) as i32;
                    if damage > 0 {
                        if let Some(hp) = rec.actor.attributes.value.get_mut(self.health_stat) {
                            *hp = (*hp as i32 - damage).max(0) as i16;
                        }
                    }
                    new_hp = rec.actor.attributes.value.get(self.health_stat).copied().unwrap_or(0) as i32;
                }
            }

            // Armour durability wear (GameServer.bb:551-570): the DEFENDER's
            // equipped armour (slots SlotI_Shield..SlotI_Feet = 1..=7) each wears 1
            // on a 1-in-5 roll per incoming swing when the ArmourDamage toggle is
            // on, notifying the owner via P_ItemHealth (slot u8 + health u16). PR
            // #574 fixed the wear target from the attacker (A1) to the defender (A2);
            // the attacking NPC carries no equipped weapon so weapon wear no-ops here.
            if self.armour_damage_on {
                for slot in 1..=7usize {
                    if let Some(new_health) = self.try_wear(&tsess.user, tsess.char_slot as usize, slot) {
                        self.accounts_dirty = true;
                        let mut p = vec![slot as u8];
                        p.extend_from_slice(&(new_health as u16).to_le_bytes());
                        out.push(Outgoing::peer(target_peer, world::P_ITEM_HEALTH, p));
                    }
                }
            }

            // "Y" damage feedback to the victim.
            let mut y = vec![b'Y'];
            y.extend_from_slice(&npc_rid.to_le_bytes());
            y.extend_from_slice(&((damage + 1) as u16).to_le_bytes());
            y.push(dtype);
            out.push(Outgoing::peer(target_peer, world::P_ATTACK_ACTOR, y));

            // HP update to everyone in the area (`"A"` = value update).
            let mut a = vec![b'A'];
            a.extend_from_slice(&tsess.runtime_id.to_le_bytes());
            a.push(self.health_stat as u8);
            a.extend_from_slice(&(new_hp as u16).to_le_bytes());
            // Swing to bystanders.
            let mut o = vec![b'O'];
            o.extend_from_slice(&npc_rid.to_le_bytes());
            o.extend_from_slice(&tsess.runtime_id.to_le_bytes());
            for (pb, sb) in self.world.session_snapshot() {
                if sb.area == area {
                    out.push(Outgoing::peer(pb, world::P_STAT_UPDATE, a.clone()));
                    if pb != target_peer {
                        out.push(Outgoing::peer(pb, world::P_ATTACK_ACTOR, o.clone()));
                    }
                }
            }

            // Player death: the killing blow (alive → 0 this tick) runs the
            // "Death" script (player = Actor(), the killer NPC = ContextActor()),
            // parity with `KillActor`'s human branch (`GameServer.bb:194`). The
            // Death script handles respawn (Output + HP restore via SetAttribute
            // + Warp). Clear every NPC's target on this peer so the corpse isn't
            // re-killed each tick (parity with the `AITarget = Null` sweep).
            if hp_before > 0 && new_hp <= 0 {
                self.spawns.clear_targets_on_peer(target_peer);
                self.fire_hook_async("Death", "Main", tsess.runtime_id, npc_rid, target_peer);
            }
        }
        out
    }

    /// Per-tick aggro-on-sight (`AILookForTargets`, `GameServer.bb:1481-1507`):
    /// every idle NPC whose template `Aggressiveness == 2` scans the players in
    /// its area and targets the first one it dislikes (faction grid rating of the
    /// player's home faction `< 150`) within `AggressiveRange`. Setting the
    /// target hands it to [`collect_npc_movement`] (chase) + [`collect_npc_attacks`]
    /// (swing in range). Mutates targets only — emits no packets.
    ///
    /// Divergences from Blitz: only **players** can be targets (the target model
    /// is a peer id, so NPC-vs-NPC aggro isn't representable yet), and the scan
    /// runs every relay tick rather than Blitz's random ~1/10-tick gate (aggros
    /// a little sooner — functionally equivalent).
    pub fn run_npc_aggro(&mut self) {
        for (rid, area, actor_id, nx, ny, nz) in self.spawns.idlers() {
            if self.world.is_ridden(rid) {
                continue; // a ridden mount doesn't aggro
            }
            let Some(t) = self.catalog.get(actor_id) else {
                continue;
            };
            if t.aggressiveness != 2 {
                continue;
            }
            let npc_home = t.default_faction;
            let range_sq = (t.aggressive_range as f32) * (t.aggressive_range as f32);
            // Find the first disliked player in range.
            let mut chosen: Option<u32> = None;
            for (peer, sess) in self.world.session_snapshot() {
                if sess.area != area {
                    continue;
                }
                let player_home = self
                    .accounts
                    .find(&sess.user)
                    .and_then(|a| a.characters.get(sess.char_slot as usize))
                    .map(|r| r.actor.home_faction)
                    .unwrap_or(0);
                if self.factions.rating(npc_home, player_home) >= 150 {
                    continue; // strongly allied → not a target
                }
                let dx = nx - sess.x;
                let dy = ny - sess.y;
                let dz = nz - sess.z;
                if dx * dx + dy * dy + dz * dz < range_sq {
                    chosen = Some(peer);
                    break;
                }
            }
            if let Some(peer) = chosen {
                self.spawns.set_target(rid, peer);
            }
        }
    }

    /// `AICallForHelp` (`GameServer.bb:1510-1537`): when an NPC is attacked and
    /// turns hostile, nearby allied NPCs pile onto the same player. A recruit
    /// qualifies if it is idle, `Aggressiveness` is 1 or 2, it rates the caller's
    /// home faction friendly (grid `>= 190`), and it is within **its own**
    /// `AggressiveRange` of the caller. (No pet exclusion yet — the leader/owner
    /// model is unported; noted in PARITY.md.)
    fn npc_call_for_help(&mut self, caller_rid: u16, target_peer: u32) {
        let (caller_area, cx, cy, cz, caller_actor_id) = match self.spawns.npc(caller_rid) {
            Some(c) => (c.area.clone(), c.x, c.y, c.z, c.actor_id),
            None => return,
        };
        let caller_home = self
            .catalog
            .get(caller_actor_id)
            .map(|t| t.default_faction)
            .unwrap_or(0);
        let mut recruits = Vec::new();
        for (rid, area, actor_id, x, y, z) in self.spawns.idlers() {
            if rid == caller_rid || area != caller_area {
                continue;
            }
            let Some(t) = self.catalog.get(actor_id) else {
                continue;
            };
            if t.aggressiveness != 1 && t.aggressiveness != 2 {
                continue;
            }
            if self.factions.rating(t.default_faction, caller_home) < 190 {
                continue; // not friendly enough to help
            }
            let range_sq = (t.aggressive_range as f32) * (t.aggressive_range as f32);
            let dx = x - cx;
            let dy = y - cy;
            let dz = z - cz;
            if dx * dx + dy * dy + dz * dz < range_sq {
                recruits.push(rid);
            }
        }
        for rid in recruits {
            self.spawns.set_target(rid, target_peer);
        }
    }

    /// Per-tick pet follow (`GameServer.bb:983-1010`, `AI_Pet`): every pet steps
    /// toward its leader's position (running while out of `CheckDist`, stopping
    /// when close) and broadcasts `P_StandardUpdate`. A pet whose leader is gone /
    /// in another zone is left in place. Run on the movement-relay cadence.
    pub fn collect_npc_pet_follow(&mut self) -> Vec<Outgoing> {
        let mut out = Vec::new();
        for (rid, area, actor_id) in self.spawns.pets() {
            if self.world.is_ridden(rid) {
                continue; // a ridden mount doesn't run its own AI
            }
            let Some(leader) = self.spawns.npc_leader(rid) else {
                continue;
            };
            // Leader position (player session or NPC) — must be same-area.
            let leader_pos = self
                .world
                .session_for_runtime(leader)
                .filter(|s| s.area == area)
                .map(|s| (s.x, s.z, s.is_running))
                .or_else(|| {
                    self.spawns
                        .npc(leader)
                        .filter(|n| n.area == area)
                        .map(|n| (n.x, n.z, 1u8))
                });
            let Some((lx, lz, running)) = leader_pos else {
                continue;
            };
            // Follow stop-distance (Blitz `5 + radii`; a fixed buffer here since
            // the port doesn't carry per-actor radii at this site).
            let check = 8.0;
            let (sv, sm) = self
                .catalog
                .get(actor_id)
                .map(|t| {
                    (
                        t.attr_value.get(self.speed_stat).copied().unwrap_or(0) as f32,
                        t.attr_maximum.get(self.speed_stat).copied().unwrap_or(0) as f32,
                    )
                })
                .unwrap_or((0.0, 0.0));
            let ratio = if sm > 0.0 { sv / sm } else { 1.0 };
            // Match the leader's pace (run if they run).
            let step = 1.5 * ratio * if running != 0 { 2.0 } else { 1.0 };
            let Some((nx, nz, in_range)) = self.spawns.step_npc_toward(rid, lx, lz, step, check) else {
                continue;
            };
            let wire = world::npc_standard_update_to_wire(
                rid,
                nx,
                nz,
                if in_range { 0 } else { 1 },
                lx,
                lz,
            );
            for (pb, sb) in self.world.session_snapshot() {
                if sb.area == area {
                    out.push(Outgoing::peer(pb, world::P_STANDARD_UPDATE, wire.clone()));
                }
            }
        }
        out
    }

    /// Melee `CheckDist` between two actor templates (`GameServer.bb:959`):
    /// `4.0 + attackerRadius + targetRadius`. Compared against the **squared**
    /// distance by callers.
    fn melee_check_dist(&self, attacker_actor_id: u16, target_actor_id: u16) -> f32 {
        let ar = self.catalog.get(attacker_actor_id).map(|t| t.radius).unwrap_or(0.0);
        let tr = self.catalog.get(target_actor_id).map(|t| t.radius).unwrap_or(0.0);
        4.0 + ar + tr
    }

    /// Per-tick NPC chase movement (`GameServer.bb:937-975` + `738-743`): every
    /// NPC with a live target steps toward it at the actor's Speed (running while
    /// out of melee range), and the new position is broadcast to same-area
    /// players as `P_StandardUpdate` so clients animate the chase. NPCs in melee
    /// range stop moving (their swing is handled by [`collect_npc_attacks`]);
    /// targets that left the zone are dropped. Run on the movement-relay cadence.
    ///
    /// Speed is Blitz's `1.5 * SpeedValue/SpeedMax` (×2 while running) applied
    /// once per call, so absolute chase speed tracks the caller's cadence rather
    /// than Blitz's main-loop rate — a known timing approximation (PARITY.md).
    pub fn collect_npc_movement(&mut self) -> Vec<Outgoing> {
        let mut out = Vec::new();
        for (rid, area, actor_id) in self.spawns.chasers() {
            if self.world.is_ridden(rid) {
                continue; // a ridden mount doesn't run its own AI
            }
            let Some(target_peer) = self.spawns.npc(rid).and_then(|n| n.target_peer) else {
                continue;
            };
            let Some(tsess) = self.world.session(target_peer).cloned() else {
                self.spawns.clear_target(rid);
                continue;
            };
            if tsess.area != area {
                self.spawns.clear_target(rid);
                continue;
            }
            let target_actor_id = self
                .accounts
                .find(&tsess.user)
                .and_then(|a| a.characters.get(tsess.char_slot as usize))
                .map(|r| r.actor.actor_id)
                .unwrap_or(0);
            let check = self.melee_check_dist(actor_id, target_actor_id);
            // Speed from the NPC template's Speed attribute (Blitz ratio form).
            let (sv, sm) = self
                .catalog
                .get(actor_id)
                .map(|t| {
                    (
                        t.attr_value.get(self.speed_stat).copied().unwrap_or(0) as f32,
                        t.attr_maximum.get(self.speed_stat).copied().unwrap_or(0) as f32,
                    )
                })
                .unwrap_or((0.0, 0.0));
            let ratio = if sm > 0.0 { sv / sm } else { 1.0 };
            let step = 1.5 * ratio * 2.0; // chase always runs (Speed# * 2)
            let Some((nx, nz, in_range)) =
                self.spawns.step_npc_toward(rid, tsess.x, tsess.z, step, check)
            else {
                continue;
            };
            let (dest_x, dest_z, is_running) = if in_range {
                (nx, nz, 0u8)
            } else {
                (tsess.x, tsess.z, 1u8)
            };
            let wire = world::npc_standard_update_to_wire(rid, nx, nz, is_running, dest_x, dest_z);
            for (pb, sb) in self.world.session_snapshot() {
                if sb.area == area {
                    out.push(Outgoing::peer(pb, world::P_STANDARD_UPDATE, wire.clone()));
                }
            }
        }
        out
    }

    /// Per-tick idle-NPC wander (`GameServer.bb:861-924`, the auto-move branch):
    /// every idle NPC whose spawn slot has a wander radius (`SpawnRange >= 5.0`)
    /// roams within that radius of its home — on arrival it picks a fresh random
    /// destination and walks toward it, and the move is broadcast as
    /// `P_StandardUpdate` so clients animate ambient creatures (the "stag walking
    /// around"). Only NPCs that actually moved this tick are broadcast. Run on
    /// the movement-relay cadence; targeting NPCs chase instead (see
    /// [`collect_npc_movement`]). Waypoint-graph patrol is deferred (the area
    /// parser doesn't retain the graph edges yet).
    pub fn collect_npc_wander(&mut self) -> Vec<Outgoing> {
        let mut out = Vec::new();
        for (rid, area, actor_id, x0, z0) in self.spawns.wanderers() {
            if self.world.is_ridden(rid) {
                continue; // a ridden mount doesn't run its own AI
            }
            let Some((home_x, home_z, range)) = self.spawns.wander_home(rid) else {
                continue;
            };
            // Choose a new destination on arrival (or first tick).
            let need_new = match self.spawns.npc_dest(rid) {
                None => true,
                Some((dx, dz)) => {
                    let ax = (x0 - dx).abs();
                    let az = (z0 - dz).abs();
                    ax <= 2.0 && az <= 2.0
                }
            };
            if need_new {
                let nx = home_x + self.rng.frange(-range, range);
                let nz = home_z + self.rng.frange(-range, range);
                self.spawns.set_npc_dest(rid, nx, nz);
            }
            let Some((dest_x, dest_z)) = self.spawns.npc_dest(rid) else {
                continue;
            };
            // Walk speed (no ×2 run multiplier for ambient wander).
            let (sv, sm) = self
                .catalog
                .get(actor_id)
                .map(|t| {
                    (
                        t.attr_value.get(self.speed_stat).copied().unwrap_or(0) as f32,
                        t.attr_maximum.get(self.speed_stat).copied().unwrap_or(0) as f32,
                    )
                })
                .unwrap_or((0.0, 0.0));
            let ratio = if sm > 0.0 { sv / sm } else { 1.0 };
            let step = 1.5 * ratio;
            let Some((nx, nz, _)) = self.spawns.step_npc_toward(rid, dest_x, dest_z, step, 0.0) else {
                continue;
            };
            // Only broadcast a genuine move (skip the arrival deadband).
            if (nx - x0).abs() < 0.001 && (nz - z0).abs() < 0.001 {
                continue;
            }
            let wire = world::npc_standard_update_to_wire(rid, nx, nz, 0, dest_x, dest_z);
            for (pb, sb) in self.world.session_snapshot() {
                if sb.area == area {
                    out.push(Outgoing::peer(pb, world::P_STANDARD_UPDATE, wire.clone()));
                }
            }
        }
        out
    }

    /// Per-tick proximity-trigger fire (`Server.bb:636-656`): for each player,
    /// find the area trigger (with a script) whose `size`-radius sphere they're
    /// inside; if it's a fresh entry (`LastTrigger` differs), fire its
    /// `script`/`method` with `actor = player`, `Param$ = trigger index`. Run on
    /// the relay cadence. The spawned scripts emit via `pump_scripts`.
    pub fn run_triggers(&mut self) {
        for (peer, sess) in self.world.session_snapshot() {
            let triggers = self.cached_area(&sess.area).map(|a| a.triggers.clone()).unwrap_or_default();
            let mut in_trigger = -1i32;
            for (idx, t) in triggers.iter().enumerate() {
                if t.script.is_empty() {
                    continue;
                }
                let dx = sess.x - t.x;
                let dy = sess.y - t.y;
                let dz = sess.z - t.z;
                if dx * dx + dy * dy + dz * dz < t.size * t.size {
                    in_trigger = idx as i32;
                    if self.last_trigger.get(&peer).copied().unwrap_or(-1) != idx as i32 {
                        if let Some(prog) = self.scripts.linked_program(&t.script) {
                            let privileged = self.scripts.is_privileged(&t.script);
                            let method = if t.method.is_empty() { "Main".to_string() } else { t.method.clone() };
                            self.spawn_program(prog, &method, (sess.runtime_id, 0, peer), privileged, idx.to_string());
                        }
                    }
                    break;
                }
            }
            self.last_trigger.insert(peer, in_trigger);
        }
    }

    /// Per-tick underwater breath drain + drowning (`GameServer.bb:759-839`):
    /// a player inside a water volume and submerged > 2 units loses 1 Breath/sec;
    /// at 0 Breath they lose 1 Health/sec; at 0 Health they drown (the `Death`
    /// path). Out of water, Breath regenerates occasionally. Updates are
    /// owner-only (Breath/Health are owner UI stats; the Health drop also goes to
    /// the area). No-op if the project has no Breath stat. Run on the relay
    /// cadence; the per-player 1 Hz gate uses `underwater_since`.
    pub fn collect_breath(&mut self) -> Vec<Outgoing> {
        let Some(bidx) = self.breath_stat else {
            return Vec::new();
        };
        let now = self.now_ms();
        let mut out = Vec::new();
        for (peer, sess) in self.world.session_snapshot() {
            // Underwater? (horizontally inside a volume + below its surface.)
            let waters = self.cached_area(&sess.area).map(|a| a.waters.clone()).unwrap_or_default();
            let dist_under = waters.iter().find_map(|w| {
                if sess.y < w.y + 0.5
                    && sess.x > w.x
                    && sess.x < w.x + w.width
                    && sess.z > w.z
                    && sess.z < w.z + w.depth
                {
                    Some(w.y - sess.y)
                } else {
                    None
                }
            });
            let (u, s) = (sess.user.clone(), sess.char_slot as usize);

            match dist_under {
                None => {
                    self.underwater_since.remove(&peer);
                    // Regenerate breath occasionally (Rand 1..10 == 1).
                    if self.rng.range(1, 10) == 1 {
                        if let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
                            let cur = rec.actor.attributes.value.get(bidx).copied().unwrap_or(0);
                            let max = rec.actor.attributes.maximum.get(bidx).copied().unwrap_or(0);
                            if cur < max {
                                if let Some(slot) = rec.actor.attributes.value.get_mut(bidx) {
                                    *slot = cur + 1;
                                }
                                self.accounts_dirty = true;
                                out.push(Outgoing::peer(peer, world::P_STAT_UPDATE, stat_update_a(sess.runtime_id, bidx, cur + 1)));
                            }
                        }
                    }
                }
                Some(du) => {
                    let since = *self.underwater_since.entry(peer).or_insert(now);
                    if now.saturating_sub(since) < 1000 {
                        continue;
                    }
                    self.underwater_since.insert(peer, since + 1000);
                    if du <= 2.0 {
                        continue; // surface-skimming — no drain
                    }
                    // Drain breath; at 0, drain health; at 0 health, drown.
                    let mut drowned = false;
                    if let Some(rec) = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
                        let breath = rec.actor.attributes.value.get(bidx).copied().unwrap_or(0);
                        if breath > 0 {
                            if let Some(slot) = rec.actor.attributes.value.get_mut(bidx) {
                                *slot = breath - 1;
                            }
                            self.accounts_dirty = true;
                            out.push(Outgoing::peer(peer, world::P_STAT_UPDATE, stat_update_a(sess.runtime_id, bidx, breath - 1)));
                        } else {
                            let hp = rec.actor.attributes.value.get(self.health_stat).copied().unwrap_or(0);
                            let nh = (hp - 1).max(0);
                            if let Some(slot) = rec.actor.attributes.value.get_mut(self.health_stat) {
                                *slot = nh;
                            }
                            self.accounts_dirty = true;
                            // Health drop → tell the area.
                            let a = stat_update_a(sess.runtime_id, self.health_stat, nh);
                            for (pb, sb) in self.world.session_snapshot() {
                                if sb.area == sess.area {
                                    out.push(Outgoing::peer(pb, world::P_STAT_UPDATE, a.clone()));
                                }
                            }
                            drowned = nh <= 0;
                        }
                    }
                    if drowned {
                        self.underwater_since.remove(&peer);
                        let mut deaths = self.kill_actor(sess.runtime_id, 0);
                        out.append(&mut deaths);
                    }
                }
            }
        }
        out
    }

    /// Per-tick run-stamina drain (`GameServer.bb:726-737`): each online player
    /// whose last `P_StandardUpdate` said `running` loses 1 Energy, and the new
    /// value is sent to **that player only** (`P_StatUpdate "A"` — energy is an
    /// owner UI stat, so no area flood). Caps at 0; no-op if the project has no
    /// Energy stat. Run on the movement-relay cadence, so the absolute drain rate
    /// tracks that cadence (a timing approximation, like NPC movement).
    pub fn collect_energy_drain(&mut self) -> Vec<Outgoing> {
        let Some(eidx) = self.energy_stat else {
            return Vec::new();
        };
        let mut out = Vec::new();
        for (peer, sess) in self.world.session_snapshot() {
            if sess.is_running == 0 {
                continue;
            }
            // Mounted riders don't burn stamina (Blitz gates the drain on
            // `AI\Mount = Null`, GameServer.bb:729).
            if sess.mount_rid != 0 {
                continue;
            }
            let (u, s) = (sess.user.clone(), sess.char_slot as usize);
            let new_e = match self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)) {
                Some(rec) => {
                    let e = rec.actor.attributes.value.get(eidx).copied().unwrap_or(0);
                    if e <= 0 {
                        continue;
                    }
                    let ne = e - 1;
                    if let Some(slot) = rec.actor.attributes.value.get_mut(eidx) {
                        *slot = ne;
                    }
                    self.accounts_dirty = true;
                    ne
                }
                None => continue,
            };
            // Owner-only energy update.
            let mut a = vec![b'A'];
            a.extend_from_slice(&sess.runtime_id.to_le_bytes());
            a.push(eidx as u8);
            a.extend_from_slice(&(new_e as u16).to_le_bytes());
            out.push(Outgoing::peer(peer, world::P_STAT_UPDATE, a));
        }
        out
    }

    /// A cached parsed area (loads + caches on first use).
    fn cached_area(&mut self, name: &str) -> Option<&rcce_server_core::area::Area> {
        if !self.area_cache.contains_key(name) {
            let loaded = rcce_server_core::area::Area::load(&self.config.data_dir, name);
            self.area_cache.insert(name.to_string(), loaded);
        }
        self.area_cache.get(name).and_then(|a| a.as_ref())
    }

    /// Per-tick waypoint patrol (`GameServer.bb:861-924`, the follow-waypoints
    /// branch): idle NPCs on patrol slots (`SpawnRange < 5`) walk the waypoint
    /// graph — on reaching the current waypoint they pick the next via the
    /// `Next-A/Next-B` edges (random, falling back to the other / `Prev`), and
    /// step toward it, broadcasting `P_StandardUpdate`. No valid next edge → they
    /// hold position. Run on the movement-relay cadence (speed is cadence-
    /// approximate, like wander/chase).
    /// Per-tick mount glue (`GameServer.bb:744-748`): a ridden NPC snaps to its
    /// rider's position and broadcasts a standard update there, so peers see the
    /// mount move with the rider (the client also attaches it locally on the
    /// rider's update). Run after the AI collectors, which already skip ridden
    /// NPCs.
    pub fn collect_mount_glue(&mut self) -> Vec<Outgoing> {
        let mut out = Vec::new();
        for (rid, x, y, z) in self.world.mounts_with_rider_pos() {
            // The mount must still exist (it could have been freed); skip if gone.
            let Some(area) = self.spawns.npc(rid).map(|n| n.area.clone()) else {
                continue;
            };
            self.spawns.set_npc_pos(rid, x, y, z);
            let wire = world::npc_standard_update_to_wire(rid, x, z, 0, x, z);
            for (pb, sb) in self.world.session_snapshot() {
                if sb.area == area {
                    out.push(Outgoing::peer(pb, world::P_STANDARD_UPDATE, wire.clone()));
                }
            }
        }
        out
    }

    pub fn collect_npc_patrol(&mut self) -> Vec<Outgoing> {
        let mut out = Vec::new();
        for (rid, area, actor_id, x, z, start_wp) in self.spawns.patrollers() {
            if self.world.is_ridden(rid) {
                continue; // a ridden mount doesn't run its own AI
            }
            let cur_wp = self.spawns.patrol_waypoint(rid).unwrap_or(start_wp);
            // Current waypoint position + its graph edges.
            let Some((pos, edge)) = self.cached_area(&area).and_then(|a| {
                let i = cur_wp as usize;
                Some((*a.waypoints.get(i)?, *a.waypoint_graph.get(i)?))
            }) else {
                continue;
            };
            let (wx, wz) = (pos[0], pos[2]);
            let reached = (x - wx).abs() <= 2.0 && (z - wz).abs() <= 2.0;
            let (tx, tz) = if reached {
                // Pick the next waypoint via the graph (random A/B, fallbacks).
                let valid = |w: i16| (0..2000).contains(&w);
                let first = if self.rng.range(1, 2) == 1 { edge.next_a } else { edge.next_b };
                let alt = if first == edge.next_a { edge.next_b } else { edge.next_a };
                let next = if valid(first) {
                    first
                } else if valid(alt) {
                    alt
                } else if valid(edge.prev) {
                    edge.prev
                } else {
                    cur_wp // dead end → hold
                };
                self.spawns.set_patrol_waypoint(rid, next);
                let npos = self
                    .cached_area(&area)
                    .and_then(|a| a.waypoints.get(next as usize).copied())
                    .unwrap_or(pos);
                (npos[0], npos[2])
            } else {
                (wx, wz)
            };
            // Walk speed (no run multiplier) from the template Speed attribute.
            let (sv, sm) = self
                .catalog
                .get(actor_id)
                .map(|t| {
                    (
                        t.attr_value.get(self.speed_stat).copied().unwrap_or(0) as f32,
                        t.attr_maximum.get(self.speed_stat).copied().unwrap_or(0) as f32,
                    )
                })
                .unwrap_or((0.0, 0.0));
            let ratio = if sm > 0.0 { sv / sm } else { 1.0 };
            let step = 1.5 * ratio;
            if let Some((nx, nz, _)) = self.spawns.step_npc_toward(rid, tx, tz, step, 0.0) {
                if (nx - x).abs() > 0.001 || (nz - z).abs() > 0.001 {
                    let wire = world::npc_standard_update_to_wire(rid, nx, nz, 0, tx, tz);
                    for (pb, sb) in self.world.session_snapshot() {
                        if sb.area == area {
                            out.push(Outgoing::peer(pb, world::P_STANDARD_UPDATE, wire.clone()));
                        }
                    }
                }
            }
        }
        out
    }

    /// Per-tick movement relay: each online player's current position is sent to
    /// its same-area peers (`P_StandardUpdate`). The caller throttles the
    /// cadence; only peers already introduced (via [`collect_world_broadcasts`])
    /// will act on these. Returned as `(target_peer, msg_type, payload)`.
    pub fn collect_position_broadcasts(&self) -> Vec<(u32, u8, Vec<u8>)> {
        let mut out = Vec::new();
        let sessions = self.world.session_snapshot();
        for (peer_a, sa) in &sessions {
            let wire = world::standard_update_to_wire(sa);
            for (peer_b, sb) in &sessions {
                if peer_b != peer_a && sb.area == sa.area {
                    out.push((*peer_b, world::P_STANDARD_UPDATE, wire.clone()));
                }
            }
        }
        out
    }

    /// Collect the `P_NewActor` introductions owed this tick: for every online
    /// player, ensure each same-area peer has been told about it. Idempotent —
    /// once a peer knows an actor it isn't re-sent — so this is cheap to call
    /// every tick and only returns genuinely new introductions. Returned as
    /// `(target_peer, msg_type, payload)`.
    ///
    /// Movement relay (per-tick `P_StandardUpdate`/`P_RepositionActor` outbound)
    /// and `P_ActorGone` on departure are the immediate next step; this is the
    /// introduction half (two players entering the same zone see each other).
    pub fn collect_world_broadcasts(&mut self) -> Vec<(u32, u8, Vec<u8>)> {
        let mut out = Vec::new();
        let sessions = self.world.session_snapshot();

        // Populate NPCs for every area that has a player present.
        for area in self.world.active_areas() {
            self.spawns.ensure_area(
                &area,
                &self.config.data_dir,
                &self.catalog,
                self.health_stat,
                &mut self.world,
            );
        }
        // Respawn timer: refill spawn slots that dropped below their cap on a
        // death (Server.bb:546-577). Newly spawned NPCs are introduced to
        // nearby players by the same pass below.
        let respawn_now = self.now_ms();
        self.spawns.tick_respawn(respawn_now, &mut self.world);

        // --- Introduce players to each other. ---
        for (peer_a, sa) in &sessions {
            let Some(acct) = self.accounts.find(&sa.user) else {
                continue;
            };
            let Some(rec) = acct.characters.get(sa.char_slot as usize) else {
                continue;
            };
            let actor = &rec.actor;
            let genders = self.catalog.get(actor.actor_id).map(|t| t.genders).unwrap_or(0);
            let area_id = self.world.area_id(&sa.area);
            let wire = world::actor_to_wire(
                area_id, sa.runtime_id, sa.x, sa.y, sa.z, 0.0, true, actor, genders,
                self.health_stat, self.speed_stat,
            );
            for (peer_b, sb) in &sessions {
                if peer_b == peer_a || sb.area != sa.area {
                    continue;
                }
                if !self.world.knows(*peer_b, sa.runtime_id) {
                    out.push((*peer_b, world::P_NEW_ACTOR, wire.clone()));
                    self.world.mark_known(*peer_b, sa.runtime_id);
                }
            }
        }

        // --- Introduce NPCs to each player in their area. ---
        for (peer_b, sb) in &sessions {
            let area_id = self.world.area_id(&sb.area);
            // Build the introductions first (immutable borrow of self.spawns),
            // then mark known (mutable borrow of self.world) — disjoint fields.
            let intros: Vec<(u16, Vec<u8>)> = self
                .spawns
                .npcs_in_area(&sb.area)
                .filter(|npc| !self.world.knows(*peer_b, npc.runtime_id))
                .filter_map(|npc| {
                    let t = self.catalog.get(npc.actor_id)?;
                    let ch = t.new_character();
                    let wire = world::actor_to_wire(
                        area_id, npc.runtime_id, npc.x, npc.y, npc.z, 0.0, false, &ch,
                        t.genders, self.health_stat, self.speed_stat,
                    );
                    Some((npc.runtime_id, wire))
                })
                .collect();
            for (rid, wire) in intros {
                out.push((*peer_b, world::P_NEW_ACTOR, wire));
                self.world.mark_known(*peer_b, rid);
            }
        }
        out
    }

    /// Advance the in-game clock (`UpdateEnvironment`, `Environment.bb:203`): one
    /// game-minute per `60000/time_factor` ms of real time, cascading to hours /
    /// days / years. Call each tick from the main loop. Time-of-day is not
    /// broadcast (clients run their own visual clock); this drives the time-read
    /// BVMs so time-gated content works.
    pub fn tick_clock(&mut self) {
        let now = self.now_ms();
        if self.last_minute_ms == 0 {
            self.last_minute_ms = now;
            return;
        }
        let min_factor = (60_000 / self.time_factor.max(1)) as u64;
        if min_factor == 0 || now.saturating_sub(self.last_minute_ms) < min_factor {
            return;
        }
        self.last_minute_ms = now;
        let (ref mut h, ref mut m, ref mut d, ref mut y) = self.game_time;
        *m += 1;
        if *m > 59 {
            *m = 0;
            *h += 1;
            if *h > 23 {
                *h = 0;
                *d += 1;
                if *d > 364 {
                    *d = 0;
                    *y += 1;
                }
            }
        }
    }

    /// Milliseconds since this state was created (throttle clock).
    pub fn now_ms(&self) -> u64 {
        self.start.elapsed().as_millis() as u64
    }

    /// Dispatch one decoded message from `peer_id`. Returns the reply packets to
    /// send back, each `(msg_type, payload)` — usually one, but a handful (e.g.
    /// `P_FetchCharacter`) are **multipart**. An empty vec means no reply
    /// (account-creation disabled, or an unhandled type). Never panics on wire
    /// data — handlers soft-fail to a generic reply.
    pub fn dispatch(&mut self, peer_id: u32, msg_type: u8, payload: &[u8]) -> Vec<Outgoing> {
        let now = self.now_ms();
        match msg_type {
            login::P_CREATE_ACCOUNT => to_sender(
                login::handle_create_account(payload, &mut self.accounts, self.config.allow_account_creation)
                    .into_iter()
                    .collect(),
            ),
            login::P_VERIFY_ACCOUNT => to_sender(vec![login::handle_verify_account(
                payload,
                &mut self.accounts,
                &mut self.throttle,
                peer_id,
                now,
            )]),
            login::P_DELETE_CHARACTER => to_sender(vec![login::handle_delete_character(
                payload,
                &mut self.accounts,
                &mut self.throttle,
                peer_id,
                now,
            )]),
            characters::P_CREATE_CHARACTER => to_sender(vec![characters::handle_create_character(
                payload,
                &mut self.accounts,
                &mut self.throttle,
                &self.catalog,
                &self.config,
                peer_id,
                now,
            )]),
            characters::P_FETCH_CHARACTER => to_sender(characters::handle_fetch_character(
                payload,
                &mut self.accounts,
                &mut self.throttle,
                peer_id,
                now,
            )),
            world::P_START_GAME => {
                let mut out = to_sender(world::handle_start_game(
                    payload,
                    &mut self.accounts,
                    &mut self.throttle,
                    &mut self.world,
                    &self.config,
                    peer_id,
                    now,
                ));
                // If the player actually entered the world, run the `Login`
                // script (welcome message, first-login starting kit, …).
                if let Some(rid) = self.world.session(peer_id).map(|s| s.runtime_id) {
                    out.extend(self.fire_hook("Login", "Main", rid, 0));
                }
                // Send the area's current weather (Blitz sends P_WeatherChange on
                // entry, ServerNet.bb:642); the preceding P_ChangeArea set the
                // client's area_id, which this packet is gated on.
                if let Some(area) = self.world.session(peer_id).map(|s| s.area.clone()) {
                    let w = self.ensure_weather(&area);
                    let area_id = self.world.area_id(&area);
                    let mut p = area_id.to_le_bytes().to_vec();
                    p.push(w);
                    out.push(Outgoing::sender(world::P_WEATHER_CHANGE, p));
                }
                out
            }
            world::P_STANDARD_UPDATE => to_sender(world::handle_standard_update(payload, &mut self.world, peer_id)),
            world::P_CHAT_MESSAGE => self.handle_chat(peer_id, payload),
            world::P_ATTACK_ACTOR => self.handle_attack(peer_id, payload, now),
            world::P_EXAMINE => self.handle_examine(peer_id, payload),
            world::P_SPELL_UPDATE => self.handle_spell_update(peer_id, payload),
            world::P_INVENTORY_UPDATE => self.handle_inventory_update(peer_id, payload),
            world::P_EAT_ITEM => self.handle_eat_item(peer_id, payload),
            world::P_TRADE => self.handle_trade(peer_id, payload),
            world::P_OPEN_TRADING => self.handle_open_trading(peer_id, payload),
            world::P_UPDATE_TRADING => self.handle_update_trading(peer_id, payload),
            login::P_CHANGE_PASSWORD => {
                let owner = login::peek_user(payload).and_then(|u| self.world.logged_on_peer(&u));
                to_sender(vec![login::handle_change_password(payload, &mut self.accounts, peer_id, owner)])
            }
            world::P_RIGHT_CLICK => self.handle_right_click(peer_id, payload),
            world::P_DISMOUNT => self.handle_dismount(peer_id),
            // Warp-completion acks (`ServerNet.bb:1796`/`:1802`): the client signals
            // it finished a reposition / zone change. Blitz clears `IgnoreUpdate`
            // here; the port doesn't suppress updates during a warp, so there's no
            // state to clear — accept them as no-ops so they aren't logged as
            // unhandled.
            world::P_CHANGE_AREA | world::P_REPOSITION_ACTOR => Vec::new(),
            world::P_DIALOG => self.handle_dialog_response(peer_id, payload),
            world::P_SCRIPT_INPUT => self.handle_script_input(peer_id, payload),
            world::P_PROGRESS_BAR => self.handle_progress_bar(peer_id, payload),
            world::P_JUMP => self.handle_jump(peer_id, payload),
            world::P_ACTION_BAR_UPDATE => self.handle_action_bar_update(peer_id, payload),
            world::P_ITEM_SCRIPT => self.handle_item_script(peer_id, payload),
            _ => Vec::new(),
        }
    }

    /// True if `msg_type` has a real handler (vs. just being traced). Lets the
    /// caller log unhandled packets distinctly from handled-but-no-reply ones.
    pub fn is_handled(msg_type: u8) -> bool {
        matches!(
            msg_type,
            login::P_CREATE_ACCOUNT
                | login::P_VERIFY_ACCOUNT
                | login::P_DELETE_CHARACTER
                | characters::P_CREATE_CHARACTER
                | characters::P_FETCH_CHARACTER
                | world::P_START_GAME
                | world::P_STANDARD_UPDATE
                | world::P_CHAT_MESSAGE
                | world::P_ATTACK_ACTOR
                | world::P_EXAMINE
                | world::P_SPELL_UPDATE
                | world::P_INVENTORY_UPDATE
                | world::P_EAT_ITEM
                | world::P_TRADE
                | world::P_OPEN_TRADING
                | world::P_UPDATE_TRADING
                | login::P_CHANGE_PASSWORD
                | world::P_RIGHT_CLICK
                | world::P_DISMOUNT
                | world::P_CHANGE_AREA
                | world::P_REPOSITION_ACTOR
                | world::P_DIALOG
                | world::P_SCRIPT_INPUT
                | world::P_PROGRESS_BAR
                | world::P_JUMP
                | world::P_ACTION_BAR_UPDATE
                | world::P_ITEM_SCRIPT
        )
    }
}
