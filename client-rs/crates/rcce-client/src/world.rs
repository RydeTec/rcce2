//! Live game-state model, fed by the server packet stream.
//!
//! Packet payload layouts decoded from the reference client's parse code
//! (`ClientNet.bb`) and the server serializer (`Actors.bb::ActorInstanceToString`).
//! All multi-byte fields are **little-endian** (handled by `MsgReader`).

use std::collections::HashMap;

use rcce_net::codec::{MsgReader, MsgWriter};
use rcce_net::{packet_id as pk, RecvMessage};

/// How long a remote actor plays its jump animation after a `P_Jump` (ANIM-7).
/// Roughly the airborne duration of the local jump arc.
pub const JUMP_ANIM_SECS: f32 = 0.5;

// Combat-voice `Speech_*` slots queued into `pending_combat_sounds` (the App
// resolves them per actor template+gender). Tied to the rcce-data source of truth.
const SPEECH_ATTACK1: u8 = rcce_data::actors::speech::ATTACK1 as u8;
const SPEECH_HIT1: u8 = rcce_data::actors::speech::HIT1 as u8;
const SPEECH_DEATH: u8 = rcce_data::actors::speech::DEATH as u8;

/// How long a remote actor plays its attack swing after a `P_AttackActor`
/// broadcast (CBT-3). Matches the local player's `me_attack_until` ~0.8 s window.
pub const ATTACK_ANIM_SECS: f32 = 0.8;

/// An open NPC dialog window (`P_Dialog` "N"/"T"/"O"/"C"). Server-driven: the
/// NPC's `Main` script pushes a title, text lines, and option lines; the client
/// echoes "N"/"T" acks (via `pending_sends`) so the script advances. One active
/// dialog at a time (matches typical play). ref ClientNet.bb:1027-1068.
#[derive(Debug, Clone, Default)]
pub struct Dialog {
    pub script_handle: u32,
    pub runtime_id: u16,
    pub title: String,
    pub lines: Vec<(String, [f32; 4])>,
    pub options: Vec<String>,
}

/// A scripted free-text input dialog (`P_ScriptInput`, id 53). The server's
/// `TextInput` script command opens this; the user types into `text` and
/// submits (Enter / Accept), which sends `[4]scriptHandle + text` back. ESC
/// cancels without replying. ref ClientNet.bb:1020-1024, Interface3D.bb:1594.
#[derive(Debug, Clone, Default)]
pub struct ScriptInput {
    pub script_handle: u32,
    /// Render the typed text masked (password-style) when set.
    pub masked: bool,
    pub title: String,
    pub prompt: String,
    /// The user's in-progress reply.
    pub text: String,
}

/// A scripted progress bar (`P_ProgressBar`, id 51): a server-driven labelled
/// bar at fractional screen coords. `client_handle` is what we echo on create
/// so the server can address later update/delete. ref ClientNet.bb:151-177.
#[derive(Debug, Clone, Default)]
pub struct ProgressBar {
    pub client_handle: u32,
    pub color: [f32; 3],
    pub x: f32,
    pub y: f32,
    pub w: f32,
    pub h: f32,
    pub max: u16,
    pub value: u16,
    pub text: String,
}

/// An in-flight projectile (`P_Projectile`, id 37). Spawns at the source actor,
/// flies toward the target (homing → the target actor's live position, else a
/// snapshot taken at spawn) and is removed on impact (within 2 units). Rendered
/// as a billboard at its projected screen position. ref ClientNet.bb:217-238.
#[derive(Debug, Clone)]
pub struct Projectile {
    pub x: f32,
    pub y: f32,
    pub z: f32,
    /// Homing target's runtime id (only when `homing`); 0 otherwise.
    pub target_rid: u16,
    pub tx: f32,
    pub ty: f32,
    pub tz: f32,
    pub homing: bool,
    /// World units per second.
    pub speed: f32,
    /// Stable id assigned at spawn, so the App can keep one following particle
    /// emitter per projectile across frames (the projectiles Vec has no key).
    pub id: u32,
    /// The projectile's particle-emitter config name (`Emitter2$` on the wire — the
    /// distinctive effect, e.g. "Fireball"/"Snow"/"Flame"; Projectiles.dat) and its
    /// billboard texture id (`TexID2`). Empty name → no emitter (the plain glow only).
    pub emitter: String,
    pub emitter_tex: u16,
    /// The projectile's base emitter (`Emitter1$`, e.g. "Default") + its texture
    /// (`TexID1`), attached alongside `emitter` like Blitz. Empty → not rendered.
    pub emitter1: String,
    pub emitter1_tex: u16,
}

/// A screen-flash effect (`P_ScreenFlash`, id 33): a full-screen colour that
/// fades out over `length` seconds. ref ClientNet.bb:679-686, Client.bb:1112.
#[derive(Debug, Clone, Copy)]
pub struct ScreenFlash {
    pub color: [f32; 3],
    /// Initial alpha (0..1).
    pub alpha: f32,
    /// Fade duration in seconds.
    pub length: f32,
}

/// A known spell tracked live via `P_KnownSpellUpdate` (SPL-7): id + name +
/// rank/level. The full record (icon/recharge/desc) is in the P_FetchCharacter
/// sheet; this is the live add/remove/level state. Displayed name-sorted, but the
/// memorise/unmemorise wire index must be the SERVER's `KnownSpells[]` array index
/// (`known_index`) — the protocol doesn't carry it, so it's tracked as the
/// add/receive order (server `AddSpell` fills the first free slot, dense from 0).
#[derive(Debug, Clone, Default)]
pub struct KnownSpell {
    pub id: u16,
    pub name: String,
    pub level: u16,
    /// Server `KnownSpells[]` index (add order), sent on memorise/unmemorise.
    pub known_index: u16,
}

/// A quest-log entry (`P_QuestLog`, QST-1): name + a coloured status line and a
/// completed flag.
#[derive(Debug, Clone, Default)]
pub struct Quest {
    pub name: String,
    pub status: String,
    pub color: [f32; 4],
    pub completed: bool,
}

/// Parse a `P_QuestLog` status blob: 3 RGB bytes, an optional `254` completed
/// marker, then the status text. Returns `(text, colour, completed)`. Pure —
/// unit-tested.
pub fn parse_quest_status(raw: &[u8]) -> (String, [f32; 4], bool) {
    if raw.len() < 3 {
        let t: String = raw.iter().filter(|&&b| b >= 32).map(|&b| b as char).collect();
        return (t.trim().to_string(), [1.0, 1.0, 1.0, 1.0], false);
    }
    let color = [raw[0] as f32 / 255.0, raw[1] as f32 / 255.0, raw[2] as f32 / 255.0, 1.0];
    let rest = &raw[3..];
    let completed = rest.first() == Some(&254);
    let text_bytes = if completed { &rest[1..] } else { rest };
    let text: String = text_bytes.iter().filter(|&&b| b >= 32).map(|&b| b as char).collect();
    (text.trim().to_string(), color, completed)
}

/// One actor instance in the current zone (player or NPC).
/// A combat hit reported by `P_AttackActor`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CombatEvent {
    pub target: u16,
    /// Who dealt the hit (CBT-5 chat-line style needs the attacker's name for
    /// incoming hits). `my_runtime_id` for the local player's own swings.
    pub attacker: u16,
    pub damage: u16,
    /// Damage-type index (maps to a name via Damage.dat).
    pub damage_type: u8,
}

/// A server-spawned particle emitter request (`P_CreateEmitter`), resolved by the
/// App against the AssetStore (`.rpc` config + texture).
#[derive(Debug, Clone, PartialEq)]
pub struct PendingEmitter {
    /// Billboard texture catalog id.
    pub tex_id: u16,
    /// Lifetime in milliseconds (the emitter stops spawning after this).
    pub lifetime_ms: u32,
    /// World spawn position.
    pub pos: [f32; 3],
    /// Emitter config name (indexes `Data/Emitter Configs/<name>.rpc`). Validated
    /// against path traversal at the receive site.
    pub name: String,
    /// Actor RuntimeID this emitter is attached to (it follows the actor each
    /// frame, and `pos` is an actor-local offset). `None` when the server sent
    /// RuntimeID 0 (world-anchored — `pos` is then an absolute world position).
    pub attach: Option<u16>,
}

/// A server-commanded animation override (`P_AnimateActor`). Blitz `Animate`
/// mode 3 plays the clip once from the start, holds its last frame, and only
/// reverts when the actor next moves — so we track time **since the clip began**
/// (to play it from frame 0, not a global-time phase) and its natural length.
#[derive(Debug, Clone, PartialEq)]
pub struct ServerAnim {
    pub name: String,
    /// Seconds since the clip started; drives the play-once frame in `build_actors`
    /// (clip start + elapsed·rate, clamped to the last frame = the held pose).
    pub elapsed: f32,
    /// The clip's natural play-once length in seconds. Until `elapsed` reaches it
    /// the clip is still playing (movement can't cancel); after, the end pose holds
    /// until the actor moves.
    pub duration: f32,
}

#[derive(Debug, Clone, Default)]
pub struct Actor {
    pub runtime_id: u16,
    pub template_id: u16,
    /// Actor level, from `P_NewActor` and kept live by `P_XPUpdate "L"`. Shown on
    /// the nameplate and the target panel (Blitz tracks it as `AI\Level` and shows
    /// it in the CharInteract window).
    pub level: u16,
    /// Actor reputation (signed; from `P_NewActor`). Shown in the target panel —
    /// Blitz's CharInteract window shows `LS_Reputation + AI\Reputation`.
    pub reputation: i16,
    pub name: String,
    pub tag: String,
    pub is_player: bool,
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub yaw: f32,
    /// Smoothed render position/facing — eased toward `x`/`z` and the
    /// movement-derived heading each frame (see [`World::interpolate`]) so actors
    /// glide between the ~9 Hz `P_StandardUpdate` echoes instead of teleporting.
    /// The body renders at these; `x`/`z`/`yaw` stay the authoritative state.
    pub render_x: f32,
    pub render_z: f32,
    pub render_yaw: f32,
    /// Buffered authoritative positions `[time, x, z]` for time-based render
    /// interpolation — the body renders at `now - RENDER_DELAY` interpolated
    /// across these (see [`World::tick_movement`]).
    pub samples: Vec<[f32; 3]>,
    pub dest_x: f32,
    pub dest_z: f32,
    pub is_running: bool,
    pub walk_back: bool,
    pub mount_id: u16,
    pub alive: bool,
    /// Appearance from P_NewActor: gender (0 male / 1 female) and the 0..4
    /// face/body/hair/beard selection indices into the actor template's id
    /// arrays. Drive which skin + hair/beard mesh this actor draws.
    pub gender: u8,
    pub face_tex: u8,
    pub body_tex: u8,
    pub hair: u8,
    pub beard: u8,
    /// Health value/maximum from P_NewActor (spawn HP; the bar fraction).
    pub health: i16,
    pub health_max: i16,
    /// Attribute index → (value, maximum), as delivered by P_StatUpdate.
    /// Sparse — only attributes the server has sent. Health/Energy/etc. indices
    /// come from Fixed Attributes.dat (the caller maps them).
    pub attributes: HashMap<u8, (i16, i16)>,
    /// Equipped gear item ids from P_InventoryUpdate "O": [weapon, shield,
    /// chest, hat]. 65535 = nothing in that slot. The foundation for attaching
    /// gear meshes; for now the weapon name shows on the nameplate.
    pub equipped: [u16; 4],
}

/// Current zone metadata (from `P_ChangeArea`).
#[derive(Debug, Default, Clone)]
pub struct Zone {
    pub area_id: u32,
    pub name: String,
    pub pvp: bool,
    pub gravity_raw: u16,
    pub weather: u8,
}

/// Everything the client knows about the running game.
#[derive(Debug, Default)]
pub struct World {
    pub my_runtime_id: u16,
    /// Set when the server sends `P_KickedPlayer` — the App tears the world
    /// session down and returns to the login screen with a "kicked" message
    /// (Blitz: `RCE_Disconnect` + `OnLostConnection`, ClientNet.bb:1780).
    pub kicked: bool,
    pub me_x: f32,
    pub me_y: f32,
    pub me_z: f32,
    pub me_yaw: f32,
    /// Smoothed render position for the local player (eased toward `me_x`/`me_z`),
    /// so the body and the camera following it glide between server echoes instead
    /// of snapping. `me_yaw` (the visual facing) is also eased toward the movement
    /// heading in `tick_movement` — at the same rate as remote actors — so the body
    /// turns smoothly instead of snapping.
    pub me_render_x: f32,
    pub me_render_z: f32,
    /// Cleared until the first authoritative position arrives, so interpolation
    /// snaps (not glides) into the spawn/zone position.
    pub me_render_init: bool,
    /// Local player's buffered authoritative positions `[time, x, z]` for
    /// time-based render interpolation (see [`World::tick_movement`]).
    pub me_samples: Vec<[f32; 3]>,
    /// Local player's actor template id (= race), from our own P_NewActor. The
    /// in-world body mesh/scale/hair are resolved from this (like every other
    /// actor); without it the local body fell back to template 0, rendering the
    /// wrong race for any non-default-race character.
    pub me_actor_id: u16,
    /// Local player's appearance (from our own P_NewActor).
    pub me_gender: u8,
    pub me_face_tex: u8,
    pub me_body_tex: u8,
    pub me_hair: u8,
    pub me_beard: u8,
    pub me_health: i16,
    pub me_health_max: i16,
    /// Which attribute slot carries Health for this project (read from
    /// `Game Data/Fixed Attributes.dat` at enter-world; default 0). The server's
    /// `P_StatUpdate` reports HP under this slot index, so it must match the
    /// project — a hardcoded 0 froze HP bars on any project where Health != 0.
    pub health_stat: u8,
    /// Template gender mode (`Actors.dat` `Genders`) keyed by template id.
    /// Populated by the host before applying packets so `on_new_actor` knows
    /// whether the wire carries a gender byte (only when mode == 0). Empty map
    /// ⇒ assume 0 (byte present), the players-and-most-NPCs default.
    pub template_genders: HashMap<u16, u8>,
    /// The project's localizable string table (`Game Data/Language.txt`),
    /// threaded in at enter-world. Empty (the `Default`) → chat strings use
    /// their hardcoded-English fallback. See [`World::lang`].
    pub language: rcce_data::Language,
    /// Player-chat speech bubbles (`Other.dat` UseBubbles > 1), threaded in at
    /// enter-world. When true, a plain say (`"<Name> text"`) shows as a bubble over
    /// the speaker in addition to the chat log.
    pub bubbles_enabled: bool,
    /// Chat-bubble text colour for other players (`Other.dat` BubblesR/G/B; readable
    /// fallback when unset). The local player's own bubble is always blue.
    pub bubble_color: [f32; 4],
    /// The local player's character name, for matching their own `"<Name>"` say to
    /// `my_runtime_id` (so it bubbles over Me in blue). Empty until enter-world.
    pub me_name: String,
    pub zone: Zone,
    /// Other actors keyed by runtime id (excludes the local player).
    pub actors: HashMap<u16, Actor>,
    /// Recent chat lines (control-byte channel prefixes stripped).
    /// Recent chat lines with their colour (from the `P_ChatMessage` sentinel).
    pub chat: Vec<(String, [f32; 4])>,
    // Local player progression / stats.
    pub me_xp: i32,
    pub me_xp_bar: u8,
    /// Local player level. Seeded from the login sheet (`enter_outcome`) and kept
    /// live by `P_XPUpdate "U"` (level changed → XP resets to 0). The character
    /// sheet panel reads this, not the frozen login snapshot.
    pub me_level: u16,
    /// Local player reputation (signed). Seeded from the login sheet and kept live
    /// by `P_StatUpdate "R"` (which the handler previously dropped).
    pub me_reputation: i32,
    pub me_gold: i32,
    /// Server day/night clock from the `P_FetchActors` `"E"` env block. When
    /// `time_known`, the client advances it locally (one game-minute = `60000 /
    /// time_factor` ms) and the renderer drives day/night from `day_phase()`
    /// instead of the local noon default — so dusk/night follow the server.
    pub time_known: bool,
    /// Game minutes since midnight (0..1440), advanced each frame.
    pub time_minutes: f32,
    /// Server `TimeFactor` (game-minutes pace); `60000/TimeFactor` ms per game-min.
    pub time_factor: u32,
    pub me_attributes: HashMap<u8, (i16, i16)>,
    /// Recent combat hits (from P_AttackActor).
    pub combat_events: Vec<CombatEvent>,
    /// Server-driven floating numbers (`P_FloatingNumber` /
    /// `BVM_CreateFloatingNumber`): `(rid, amount, [r,g,b])`. The App drains these
    /// into the floater system with the server's explicit colour — these are the
    /// script-driven popups (heals, custom text) the client can't compute itself,
    /// distinct from the locally-derived combat-damage floaters. Mirrors Blitz
    /// `CreateFloatingNumber` (ClientNet.bb:213).
    pub pending_floaters: Vec<(u16, i32, [u8; 3])>,
    /// Items dropped in the world (P_InventoryUpdate "D"), keyed by the
    /// server's DroppedItem handle. Removed on pickup ("P"/"R").
    pub dropped_items: HashMap<u32, DroppedItem>,
    /// The open vendor/trade window, if any (P_OpenTrading).
    pub current_trade: Option<crate::trade::TradeWindow>,
    /// Player↔player trade state (the partner's offers + my staged offers), live
    /// only while a `TradeKind::Player` window is open. Set when `OPEN_TRADING`
    /// parses a player trade, driven by `P_UpdateTrading`, cleared on
    /// `P_CloseTrading`. `None` for vendor (NPC/scenery) trades.
    pub player_trade: Option<crate::trade::PlayerTrade>,
    /// The open NPC dialog window, if any (P_Dialog). See [`Dialog`].
    pub dialog: Option<Dialog>,
    /// The open scripted free-text input dialog, if any (P_ScriptInput). The
    /// user types into `text` and submits; reply is `[4]scriptHandle + text`.
    /// See [`ScriptInput`]. ref ClientNet.bb:1020-1024.
    pub script_input: Option<ScriptInput>,
    /// Scripted progress bars (P_ProgressBar "C"/"U"/"D"), keyed by the
    /// client-allocated handle we echo back on create. See [`ProgressBar`].
    pub progress_bars: Vec<ProgressBar>,
    /// Monotonic allocator for progress-bar client handles (the Blitz client
    /// returns its local gadget handle; we mint our own and the server keys
    /// later U/D on it). Starts at 1 so 0 stays "none".
    pub next_pbar_handle: u32,
    /// In-flight projectiles (P_Projectile). See [`Projectile`].
    pub projectiles: Vec<Projectile>,
    /// Monotonic id source for projectiles (so the App can track one following
    /// particle emitter per projectile). Wraps; collisions are harmless (transient).
    pub next_proj_id: u32,
    /// A pending screen flash (P_ScreenFlash), drained by the renderer.
    pub flash: Option<ScreenFlash>,
    /// Live known-spell list maintained by P_KnownSpellUpdate. See [`KnownSpell`].
    pub known_spells: Vec<KnownSpell>,
    /// Chat bubbles to show over actors (P_BubbleMessage): (rid, text, colour).
    /// Drained by the renderer, which times the fade. CHAT-4.
    pub pending_bubbles: Vec<(u16, String, [f32; 4])>,
    /// Quest-log entries maintained by P_QuestLog (N/U/D). See [`Quest`].
    pub quests: Vec<Quest>,
    /// Party member names (P_PartyUpdate): up to 6 others, empty slots dropped.
    pub party: Vec<String>,
    /// Remote actors currently mid-jump (ANIM-7): rid → seconds of jump anim
    /// left. Set by `on_jump` from `P_Jump`, ticked down each frame; while
    /// present the actor renders the Jump clip + a vertical hop in `build_actors`.
    pub jumps: HashMap<u16, f32>,
    /// Remote actors currently mid-attack-swing (CBT-3): rid → seconds of attack
    /// clip left. Set by `on_attack_actor` for the attacker in a `P_AttackActor`
    /// `'Y'`/broadcast, ticked down each frame; while present the actor renders
    /// its attack clip in `build_actors`. (The local player uses `me_attack_until`.)
    pub attack_anims: HashMap<u16, f32>,
    /// `(rid, anim_name)` server-commanded animation intents from `P_AnimateActor`
    /// (emotes, scripted poses). The App drains these, resolves the named clip +
    /// its duration via the AssetStore (which `World` can't reach), and installs a
    /// timed override in `server_anims`. Mirrors Blitz `PlayAnimation(A, 3, …)`
    /// (ClientNet.bb:726).
    pub pending_anims: Vec<(u16, String)>,
    /// Active server-commanded animations: rid → `(clip name, seconds left)`. While
    /// present, `build_actors` plays the named clip on that actor (priority just
    /// below death) instead of locomotion; `tick_server_anims` expires it after the
    /// clip's natural length, reverting to idle/walk (Blitz mode-3 plays once).
    pub server_anims: HashMap<u16, ServerAnim>,
    /// Server-spawned particle emitters (`P_CreateEmitter`): the App drains these,
    /// loads each one's `.rpc` config + texture (which live in the AssetStore), and
    /// adds a finite-lifetime emitter to the live particle sim. Mirrors Blitz
    /// `ScriptedEmitter` (ClientNet.bb:772).
    pub pending_emitters: Vec<PendingEmitter>,
    /// The local player's inventory, keyed by slot (0..13 equipment, 14..45
    /// backpack). Seeded from the P_FetchCharacter sheet, then kept live by
    /// P_InventoryUpdate G/T/H/R. BTreeMap so the panel iterates in slot order.
    pub me_inventory: std::collections::BTreeMap<u8, crate::fetch::InvItem>,
    /// Outbound packets the apply() logic needs to send (e.g. the "GY" accept
    /// for a given item). The host drains this after each poll.
    pub pending_sends: Vec<(u8, Vec<u8>)>,
    /// Active status effects (buffs/debuffs) on the local player, from
    /// P_ActorEffect. Shown as a HUD icon row.
    pub active_effects: Vec<ActiveEffect>,
    /// Sound ids to play one-shot, from `P_Sound`/`P_Speech` (AUD-4/AUD-5). The
    /// App drains these to `audio.play_oneshot` each frame. 2D playback for the
    /// alpha; the `P_Speech`/3D positional attenuation is a noted follow-up.
    pub pending_sounds: Vec<u16>,
    /// Pending combat voice sounds as `(runtime_id, Speech_* slot)` intents: an
    /// actor's Attack/Hit/Death cry. The handlers can't resolve the sound id here
    /// (the speech ids live in the actor *template* in the AssetStore, which the
    /// `World` doesn't hold), so the App drains these, looks the actor up by
    /// template+gender, resolves via `ActorCatalog::speech_id`, and plays. Mirrors
    /// Blitz `PlayActorSound` on combat (ClientNet.bb:1094/1122/1166).
    pub pending_combat_sounds: Vec<(u16, u8)>,
    /// Speech slots for the LOCAL player's own combat voice (Attack on a swing,
    /// Hit when struck). `pending_combat_sounds` resolves via the `actors` map,
    /// which excludes `Me`, so these are kept separate — the App resolves them
    /// against `me_actor_id` + `me_gender`. Mirrors Blitz `PlayActorSound(Me, …)`
    /// (ClientNet.bb:1122 Attack, :1166 Hit).
    pub pending_self_sounds: Vec<u8>,
    /// `(item_id, amount)` of items the local player just picked up off the
    /// ground (`P_InventoryUpdate "R"`). The App drains these, resolves the item
    /// NAME (which lives in the AssetStore, not `World`) + the localized prefix,
    /// and pushes a green "Picked up item: <name> (xN)" chat line — Blitz parity
    /// (ClientNet.bb:1349/1351).
    pub pending_pickup_toasts: Vec<(u16, u16)>,
    /// A pending mid-zone music switch (`P_Music`, AUD-1): the App applies it via
    /// `audio.set_music`, replacing the looping track. `None` when unchanged.
    pub pending_music: Option<u16>,
}

/// A buff/debuff on the local player (P_ActorEffect "A").
#[derive(Debug, Clone, PartialEq)]
pub struct ActiveEffect {
    pub id: u32,
    pub texture_id: u16,
    pub name: String,
}

/// An item lying on the ground, from `P_InventoryUpdate "D"`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct DroppedItem {
    pub handle: u32,
    pub item_id: u16,
    pub amount: u16,
    pub health: u8,
    pub x: f32,
    pub y: f32,
    pub z: f32,
}

/// Render interpolation delay (seconds). The render position is the buffered
/// authoritative positions sampled at `now - RENDER_DELAY`, so it interpolates
/// between two real samples — smooth regardless of frame timing or echo-cadence
/// jitter, with no velocity guessing. ~1× the ~9 Hz echo interval: small enough
/// the lag is slight, large enough to bracket the updates. (Env-tunable.)
pub const RENDER_DELAY: f32 = 0.13;
/// Facing turn rate (per second) toward the travel heading.
const YAW_RATE: f32 = 9.0;
/// Position jump (world units) above which a new sample is a teleport: the
/// buffer is reset so the render snaps there instead of sliding across the map.
const ACTOR_SNAP_DIST: f32 = 30.0;
/// Seconds to extrapolate past the newest sample when `now - delay` runs ahead
/// of it (a hitch / paused echoes), before holding. Kept short so the overshoot
/// that causes a snap-back when the real echo lands is small.
const MAX_EXTRAP: f32 = 0.10;
/// Max buffered samples per entity (~1 s of history at ~9 Hz).
const MAX_SAMPLES: usize = 12;
/// Low-pass rate (per second) easing the render toward the interpolation target.
/// The server reports running movement in uneven per-echo bursts (1×/2×/3× the
/// base step over equal time windows); easing absorbs those velocity spikes —
/// and the extrapolation snap-backs — into a smooth catch-up instead of visible
/// jumps. High enough that already-smooth walking is barely lagged.
const SMOOTH_RATE: f32 = 12.0;

/// Effective render delay — `RENDER_DELAY`, overridable at runtime with
/// `RCCE_RENDERDELAY` (seconds) for tuning the smoothness/lag trade-off.
fn render_delay() -> f32 {
    std::env::var("RCCE_RENDERDELAY").ok().and_then(|s| s.parse().ok()).unwrap_or(RENDER_DELAY)
}

/// Local-player reconcile rate (per second) easing the predicted render position
/// toward the newest authoritative position. Gentle: the input prediction
/// carries the smooth motion; this only corrects drift and absorbs the
/// per-echo bursts so they don't snap. Tuned (with `SPEED_WINDOW`) to the lowest
/// render-velocity variation across a headless run sweep — CoV 0.36 vs 0.59 at
/// the first-cut 6.0/0.6.
const ME_RECON_RATE: f32 = 4.0;
/// Window (seconds) over which the local player's smooth speed is averaged. Long
/// enough to span several of the server's burst-alias cycles so the prediction
/// speed stays steady (and keeps pace, minimising the reconcile's burst-chasing)
/// — short enough to still track genuine speed changes within ~1.5 s.
const SPEED_WINDOW: f32 = 1.5;

/// Effective low-pass rate — `SMOOTH_RATE`, overridable with `RCCE_SMOOTHRATE`.
fn smooth_rate() -> f32 {
    std::env::var("RCCE_SMOOTHRATE").ok().and_then(|s| s.parse().ok()).unwrap_or(SMOOTH_RATE)
}

/// Effective local-player reconcile rate — `ME_RECON_RATE`, env `RCCE_MERECON`.
fn me_recon_rate() -> f32 {
    std::env::var("RCCE_MERECON").ok().and_then(|s| s.parse().ok()).unwrap_or(ME_RECON_RATE)
}

/// Client-authoritative base move speed (units/sec). Running doubles it (Blitz's
/// `(1 + IsRunning)` move-distance factor). Kept safely under the server's
/// speed-hack clamp (`~150·(SpeedAttr+0.5)` u/s). Tunable for feel via
/// `RCCE_MOVESPEED` — this is the value to adjust if running/walking feels off.
const CLIENT_MOVE_SPEED: f32 = 8.0;
fn client_move_speed(running: bool) -> f32 {
    let base = std::env::var("RCCE_MOVESPEED").ok().and_then(|s| s.parse::<f32>().ok()).unwrap_or(CLIENT_MOVE_SPEED);
    base * if running { 2.0 } else { 1.0 }
}

/// Reconcile deadzone (units): in client-authoritative mode the local body leads
/// freely within this distance of the server position, so normal network lag (the
/// echo trailing our reported position) doesn't drag us back. Sized to exceed the
/// position the server is behind by between sends (~`run_speed × send_interval`,
/// ≈ 92 u/s × 0.11 s ≈ 10 u). Larger divergences (speed-hack clamp / collision /
/// warp) still ease/snap to the server. Tunable for high-latency links.
fn me_deadzone() -> f32 {
    // Auto-scale with the run speed (~the server-trails-us distance between sends:
    // run_speed × ~0.16 s) so changing RCCE_MOVESPEED doesn't also need a deadzone
    // retune; `RCCE_MEDEADZONE` overrides for high-latency links.
    std::env::var("RCCE_MEDEADZONE")
        .ok()
        .and_then(|s| s.parse::<f32>().ok())
        .unwrap_or_else(|| client_move_speed(true) * 0.16 + 2.0)
}

/// Effective speed-averaging window — `SPEED_WINDOW`, env `RCCE_SPEEDWIN`.
fn speed_window() -> f32 {
    std::env::var("RCCE_SPEEDWIN").ok().and_then(|s| s.parse().ok()).unwrap_or(SPEED_WINDOW)
}

/// Smooth speed (units/sec) over the last `SPEED_WINDOW` of buffered samples —
/// averages out the server's per-echo burst aliasing so the local-player
/// prediction moves at a steady speed instead of reproducing the lurches.
fn buffer_avg_speed(buf: &[[f32; 3]], _now: f32) -> f32 {
    let n = buf.len();
    if n < 2 {
        return 0.0;
    }
    let win = speed_window();
    let newest = buf[n - 1];
    let mut i = n - 1;
    while i > 0 && newest[0] - buf[i - 1][0] < win {
        i -= 1;
    }
    let oldest = buf[i];
    let span = newest[0] - oldest[0];
    if span < 1e-3 {
        return 0.0;
    }
    let dist = ((newest[1] - oldest[1]).powi(2) + (newest[2] - oldest[2]).powi(2)).sqrt();
    dist / span
}

/// Ease `cur` toward `target` by factor `k`, snapping on a teleport-scale jump.
fn ease_pos(cur: &mut f32, target: f32, k: f32) {
    if (target - *cur).abs() > ACTOR_SNAP_DIST {
        *cur = target;
    } else {
        *cur += (target - *cur) * k;
    }
}

/// Ease an angle (degrees) toward `target` along the shortest arc.
fn ease_yaw(cur: &mut f32, target: f32, k: f32) {
    let mut d = (target - *cur) % 360.0;
    if d > 180.0 {
        d -= 360.0;
    } else if d < -180.0 {
        d += 360.0;
    }
    *cur += d * k;
}

/// Append `[now, x, z]` to a position buffer when the authoritative position has
/// changed; reset the buffer on a teleport-sized jump (so the render snaps, not
/// slides); drop the oldest when full.
fn push_sample(buf: &mut Vec<[f32; 3]>, now: f32, x: f32, z: f32) {
    if let Some(&[_, lx, lz]) = buf.last() {
        let d2 = (lx - x) * (lx - x) + (lz - z) * (lz - z);
        if d2 > ACTOR_SNAP_DIST * ACTOR_SNAP_DIST {
            buf.clear(); // teleport — start a fresh trail so we snap
        } else if (lx - x).abs() < 1e-3 && (lz - z).abs() < 1e-3 {
            return; // no movement → no new sample
        }
    }
    buf.push([now, x, z]);
    if buf.len() > MAX_SAMPLES {
        buf.remove(0);
    }
}

/// Sample the buffered trail at time `t`: lerp between the two samples that
/// bracket `t`; before the first, hold it; past the last, extrapolate from the
/// final pair (capped at `MAX_EXTRAP`). Returns `(x, z, vx, vz)` — the velocity
/// of the active segment, for facing.
fn interp_at(buf: &[[f32; 3]], t: f32) -> Option<(f32, f32, f32, f32)> {
    let n = buf.len();
    if n == 0 {
        return None;
    }
    if n == 1 || t <= buf[0][0] {
        return Some((buf[0][1], buf[0][2], 0.0, 0.0));
    }
    let last = buf[n - 1];
    if t >= last[0] {
        let p = buf[n - 2];
        let seg = (last[0] - p[0]).max(1e-3);
        let (vx, vz) = ((last[1] - p[1]) / seg, (last[2] - p[2]) / seg);
        let ahead = (t - last[0]).min(MAX_EXTRAP);
        return Some((last[1] + vx * ahead, last[2] + vz * ahead, vx, vz));
    }
    for i in 0..n - 1 {
        let (a, b) = (buf[i], buf[i + 1]);
        if t >= a[0] && t <= b[0] {
            let seg = (b[0] - a[0]).max(1e-3);
            let f = (t - a[0]) / seg;
            let (vx, vz) = ((b[1] - a[1]) / seg, (b[2] - a[2]) / seg);
            return Some((a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, vx, vz));
        }
    }
    Some((last[1], last[2], 0.0, 0.0))
}

impl World {
    /// Smooth all actor motion by **time-based interpolation** — the standard
    /// networked-movement approach, robust to frame-time and echo-cadence jitter
    /// (which surge-stalled the velocity-extrapolation attempts). Each
    /// authoritative position is buffered with its arrival time `now`; the body
    /// renders at `now - RENDER_DELAY`, interpolating between the two buffered
    /// samples that bracket it. No velocity estimate, no prediction/reconcile
    /// fight. Facing follows the interpolated motion. `dt` is only for the yaw
    /// ease. Applies to the local player and every actor alike.
    pub fn tick_movement(&mut self, now: f32, dt: f32, dir: [f32; 2], moving: bool, running: bool) {
        let t = now - render_delay();
        let dtc = dt.clamp(0.0, 0.1);
        let ky = 1.0 - (-YAW_RATE * dtc).exp();
        let kp = 1.0 - (-smooth_rate() * dtc).exp();
        let first = self.me_samples.is_empty();
        push_sample(&mut self.me_samples, now, self.me_x, self.me_z);
        self.me_render_init = true;
        let mag = (dir[0] * dir[0] + dir[1] * dir[1]).sqrt();
        if first {
            self.me_render_x = self.me_x;
            self.me_render_z = self.me_z;
        } else if std::env::var_os("RCCE_SERVERMOVE").is_some() {
            // Legacy server-driven prediction: advance at the echo-derived speed
            // and reconcile toward the authoritative echo every frame.
            if moving && mag > 1e-4 {
                let spd = buffer_avg_speed(&self.me_samples, now);
                self.me_render_x += dir[0] / mag * spd * dtc;
                self.me_render_z += dir[1] / mag * spd * dtc;
            }
            let kr = 1.0 - (-me_recon_rate() * dtc).exp();
            ease_pos(&mut self.me_render_x, self.me_x, kr);
            ease_pos(&mut self.me_render_z, self.me_z, kr);
        } else {
            // Client-authoritative (like Blitz): move the local body at a fixed
            // speed each frame and report it; the server accepts it (speed-hack
            // clamp). This makes movement instant + full-speed instead of paced by
            // the request-dest/echo round-trip ("takes forever"). Only correct
            // toward the server position when it diverges past a deadzone, so
            // normal lag doesn't rubber-band us, but clamp/collision/warp do.
            if moving && mag > 1e-4 {
                let spd = client_move_speed(running);
                self.me_render_x += dir[0] / mag * spd * dtc;
                self.me_render_z += dir[1] / mag * spd * dtc;
            }
            let (dx, dz) = (self.me_x - self.me_render_x, self.me_z - self.me_render_z);
            let err = (dx * dx + dz * dz).sqrt();
            if err > ACTOR_SNAP_DIST {
                self.me_render_x = self.me_x; // warp / teleport
                self.me_render_z = self.me_z;
            } else if err > me_deadzone() {
                let kr = 1.0 - (-me_recon_rate() * dtc).exp(); // clamp/collision
                self.me_render_x += dx * kr;
                self.me_render_z += dz * kr;
            }
        }
        // Ease the LOCAL player's facing toward the movement heading, with the same
        // rate as remote actors (below), instead of snapping. Idle keeps the last
        // facing. The movement direction itself uses `dir`, not `me_yaw`, so this is
        // purely the visual body rotation — third-person turns now glide.
        if moving && mag > 1e-4 {
            ease_yaw(&mut self.me_yaw, (-dir[0]).atan2(-dir[1]).to_degrees(), ky);
        }
        for a in self.actors.values_mut() {
            let first = a.samples.is_empty();
            push_sample(&mut a.samples, now, a.x, a.z);
            if first {
                a.render_x = a.x;
                a.render_z = a.z;
            } else if let Some((x, z, vx, vz)) = interp_at(&a.samples, t) {
                ease_pos(&mut a.render_x, x, kp);
                ease_pos(&mut a.render_z, z, kp);
                if vx * vx + vz * vz > 0.5 {
                    ease_yaw(&mut a.render_yaw, (-vx).atan2(-vz).to_degrees(), ky);
                }
            }
        }
    }

    /// Apply one received message, mutating state. Unknown types are ignored.
    pub fn apply(&mut self, m: &RecvMessage) {
        match m.msg_type {
            pk::CHANGE_AREA => self.on_change_area(&m.data),
            pk::NEW_ACTOR => self.on_new_actor(&m.data),
            pk::STANDARD_UPDATE => self.on_standard_update(&m.data),
            pk::ACTOR_GONE => self.on_actor_gone(&m.data),
            pk::CHAT_MESSAGE => self.on_chat(&m.data),
            pk::XP_UPDATE => self.on_xp_update(&m.data),
            pk::GOLD_CHANGE => self.on_gold_change(&m.data),
            pk::STAT_UPDATE => self.on_stat_update(&m.data),
            pk::ACTOR_DEAD => self.on_actor_dead(&m.data),
            pk::ATTACK_ACTOR => self.on_attack_actor(&m.data),
            pk::NAME_CHANGE => self.on_name_change(&m.data),
            pk::INVENTORY_UPDATE => self.on_inventory_update(&m.data),
            pk::ACTOR_EFFECT => self.on_actor_effect(&m.data),
            pk::WEATHER_CHANGE => self.on_weather_change(&m.data),
            pk::SOUND => self.on_sound(&m.data),
            pk::SPEECH => self.on_speech(&m.data),
            pk::MUSIC => self.on_music(&m.data),
            pk::OPEN_TRADING => {
                self.current_trade = crate::trade::TradeWindow::parse(&m.data);
                // A player↔player trade opens an empty offer board both sides fill
                // in via P_UpdateTrading; vendor trades have no such board.
                self.player_trade = match &self.current_trade {
                    Some(t) if t.kind == crate::trade::TradeKind::Player => {
                        Some(crate::trade::PlayerTrade::default())
                    }
                    _ => None,
                };
            }
            pk::UPDATE_TRADING => {
                if let Some(pt) = self.player_trade.as_mut() {
                    pt.apply_his_update(&m.data);
                }
            }
            pk::CLOSE_TRADING => {
                self.current_trade = None;
                self.player_trade = None;
            }
            pk::DIALOG => self.on_dialog(&m.data),
            pk::SCRIPT_INPUT => self.on_script_input(&m.data),
            pk::PROGRESS_BAR => self.on_progress_bar(&m.data),
            pk::PROJECTILE => self.on_projectile(&m.data),
            pk::SCREEN_FLASH => self.on_screen_flash(&m.data),
            pk::KNOWN_SPELL_UPDATE => self.on_known_spell_update(&m.data),
            pk::BUBBLE_MESSAGE => self.on_bubble_message(&m.data),
            pk::QUEST_LOG => self.on_quest_log(&m.data),
            pk::PARTY_UPDATE => self.on_party_update(&m.data),
            pk::JUMP => self.on_jump(&m.data),
            pk::FETCH_ACTORS => self.on_fetch_actors(&m.data),
            pk::FLOATING_NUMBER => self.on_floating_number(&m.data),
            pk::APPEARANCE_UPDATE => self.on_appearance_update(&m.data),
            pk::REPOSITION_ACTOR => self.on_reposition_actor(&m.data),
            pk::ANIMATE_ACTOR => self.on_animate_actor(&m.data),
            pk::ITEM_HEALTH => self.on_item_health(&m.data),
            pk::SELECT_SCENERY => self.on_select_scenery(&m.data),
            pk::CREATE_EMITTER => self.on_create_emitter(&m.data),
            // The server kicked us (admin/ban/dup-login). Flag it; the App tears
            // down the session and returns to the login screen. Empty payload.
            pk::KICKED_PLAYER => self.kicked = true,
            _ => {}
        }
    }

    /// `P_ChangeArea` (ClientNet.bb:1627): X,Y,Z,Yaw f32 · PvP u8 · Gravity u16
    /// · AreaID u32 · Weather u8 · nameLen u8 · name.
    fn on_change_area(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        self.me_x = r.f32().unwrap_or(0.0);
        self.me_y = r.f32().unwrap_or(0.0);
        self.me_z = r.f32().unwrap_or(0.0);
        self.me_yaw = r.f32().unwrap_or(0.0);
        let pvp = r.u8().unwrap_or(0) != 0;
        let gravity_raw = r.u16().unwrap_or(200);
        let area_id = r.u32().unwrap_or(0);
        let weather = r.u8().unwrap_or(0);
        let name = r.str8().unwrap_or_default();
        // A zone change invalidates the old actor set (the server re-announces
        // everyone via P_NewActor for the new zone).
        self.actors.clear();
        // Close any interaction window tied to an entity in the OLD zone — you
        // can't trade with / talk to / answer a script prompt from an NPC or
        // partner you just warped away from. The server cancels these
        // server-side on warp, but the local panel lingers if the warp packet
        // races (or precedes) the close, leaving a stale window. (Reviewer-
        // flagged for trades on #477/#479; dialog + script_input are the same
        // class.) World/projectile state is NOT cleared here — like actors, the
        // server re-announces/re-simulates it for the new zone.
        self.current_trade = None;
        self.player_trade = None;
        self.dialog = None;
        self.script_input = None;
        self.zone = Zone {
            area_id,
            name,
            pvp,
            gravity_raw,
            weather,
        };
        // Warp-completion ack: tell the server we've applied the zone change so
        // it stops suppressing our position updates (`AI\IgnoreUpdate = 0`,
        // ServerNet.bb:737). Echoes the received payload — the exact send the
        // Blitz client has commented out at ClientNet.bb:1779 (`RCE_Send(...,
        // P_ChangeArea, M\MessageData$, ...)`); the server ignores the payload.
        // Queued via pending_sends (drained each frame, like the "GY" accept).
        self.pending_sends.push((pk::CHANGE_AREA, d.to_vec()));
    }

    /// `P_NewActor` = `ActorInstanceToString` (Actors.bb:1057): ServerArea u32 ·
    /// RuntimeID u16 · Level u16 · XP u32 · TemplateID u16 · X,Y,Z,Yaw f32 ·
    /// isPlayer u8 · nameLen u8 · name · tagLen u8 · tag · **gender u8 (only if
    /// the template's Genders mode == 0)** · Reputation i16 · FaceTex u16 ·
    /// Hair u16 · BodyTex u16 · Beard u16 · (stats/equipment/factions, ignored).
    fn on_new_actor(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let _server_area = r.u32();
        let Some(runtime_id) = r.u16() else { return };
        let level = r.u16().unwrap_or(0);
        let _xp = r.u32();
        let template_id = r.u16().unwrap_or(0);
        let x = r.f32().unwrap_or(0.0);
        let y = r.f32().unwrap_or(0.0);
        let z = r.f32().unwrap_or(0.0);
        let yaw = r.f32().unwrap_or(0.0);
        let is_player = r.u8().unwrap_or(0) != 0;
        let name = r.str8().unwrap_or_default();
        let tag = r.str8().unwrap_or_default();

        // Gender byte is present only when the template is player-selectable
        // (mode 0). For mode 1 it's male (0); mode 2 it's female (1).
        let mode = self.template_genders.get(&template_id).copied().unwrap_or(0);
        let gender = if mode == 0 {
            (r.u8().unwrap_or(0)).min(1)
        } else if mode == 2 {
            1
        } else {
            0
        };
        // Reputation is a SIGNED short on the wire (Blitz reads it via
        // RCE_SignedShortFromStr, Actors.bb:1170) — a hostile-faction actor can be
        // negative. Cast u16→i16 to sign-extend; reading it unsigned would show a
        // huge positive for any negative reputation.
        let reputation = r.u16().unwrap_or(0) as i16;
        let clamp4 = |v: Option<u16>| v.unwrap_or(0).min(4) as u8;
        let face_tex = clamp4(r.u16());
        let hair = clamp4(r.u16());
        let body_tex = clamp4(r.u16());
        let beard = clamp4(r.u16());
        // Speed (value, max) then Health (value, max). Speed is unused — the
        // render speed is estimated from successive positions instead (the spawn
        // Speed value is 0 here; the real value arrives via P_StatUpdate).
        let _speed = (r.u16(), r.u16());
        let health = r.u16().unwrap_or(0) as i16;
        let health_max = r.u16().unwrap_or(0) as i16;

        if runtime_id == self.my_runtime_id {
            self.me_x = x;
            self.me_y = y;
            self.me_z = z;
            self.me_yaw = yaw;
            // Snap the render position to spawn (interpolation glides from here on).
            self.me_render_x = x;
            self.me_render_z = z;
            self.me_render_init = true;
            self.me_samples.clear(); // fresh trail at spawn (snaps, then interpolates)
            // Capture our own template (race) so the in-world body renders with
            // the correct race mesh — the server sends it in our P_NewActor.
            self.me_actor_id = template_id;
            self.me_gender = gender;
            self.me_face_tex = face_tex;
            self.me_body_tex = body_tex;
            self.me_hair = hair;
            self.me_beard = beard;
            self.me_health = health;
            self.me_health_max = health_max;
            // Our own name, so a "<Name>" say bubbles over Me (CHAT bubbles).
            self.me_name = name;
            return; // don't list ourselves among "other actors"
        }
        self.actors.insert(
            runtime_id,
            Actor {
                runtime_id,
                template_id,
                level,
                reputation,
                name,
                tag,
                is_player,
                x,
                y,
                z,
                yaw,
                render_x: x,
                render_z: z,
                render_yaw: yaw,
                samples: Vec::new(),
                dest_x: x,
                dest_z: z,
                alive: true,
                gender,
                face_tex,
                body_tex,
                hair,
                beard,
                health,
                health_max,
                equipped: [0xFFFF; 4], // nothing equipped until an "O" update
                ..Default::default()
            },
        );
    }

    /// `P_StandardUpdate` (ClientNet.bb:1490): RuntimeID u16 · X f32 · Z f32 ·
    /// IsRunning u8 · WalkBack u8 · DestX f32 · DestZ f32 · Mount u16. (22 bytes
    /// for ground actors; flying actors append a Y f32.)
    fn on_standard_update(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let Some(rid) = r.u16() else { return };
        let x = r.f32().unwrap_or(0.0);
        let z = r.f32().unwrap_or(0.0);
        let is_running = r.u8().unwrap_or(0) != 0;
        let walk_back = r.u8().unwrap_or(0) != 0;
        let dest_x = r.f32().unwrap_or(x);
        let dest_z = r.f32().unwrap_or(z);
        let mount_id = r.u16().unwrap_or(0);
        if rid == self.my_runtime_id {
            self.me_x = x;
            self.me_z = z;
        }
        if let Some(a) = self.actors.get_mut(&rid) {
            a.x = x;
            a.z = z;
            a.is_running = is_running;
            a.walk_back = walk_back;
            a.dest_x = dest_x;
            a.dest_z = dest_z;
            a.mount_id = mount_id;
        }
    }

    /// `P_ActorGone`: a runtime id that has left the zone.
    fn on_actor_gone(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        if let Some(rid) = r.u16() {
            self.actors.remove(&rid);
        }
    }

    /// A localizable client string by `LS_*` index, or the hardcoded English
    /// `default` when the project ships no `Language.txt` (or leaves the entry
    /// blank). Lets chat strings honor a project's translation while the default
    /// project (English) renders byte-identically.
    fn lang<'a>(&'a self, idx: usize, default: &'a str) -> &'a str {
        self.language.get_or(idx, default)
    }

    /// `P_XPUpdate` (ClientNet.bb:689):
    ///   `'B'` + barLevel(u8)  — XP bar position.
    ///   `'M'` + xpGain(i32)   — XP points received (added to total).
    ///   `'U'` + level(u16)    — MY level changed (XP resets to 0).
    /// `'U'` was previously dropped, so the character sheet's Level stayed frozen
    /// at the login snapshot all session. The server sends the level as a 2-byte
    /// field (ScriptingCommands.bb:2085) — read u16, not i32, or the parse fails
    /// and the level silently never updates. (`'L'`, another actor's level, is
    /// not handled: the Rust client has no per-actor level display to feed.)
    fn on_xp_update(&mut self, d: &[u8]) {
        match d.first() {
            Some(b'B') => {
                if let Some(&bar) = d.get(1) {
                    self.me_xp_bar = bar;
                }
            }
            Some(b'M') => {
                if let Some(gain) = MsgReader::new(&d[1..]).i32() {
                    self.me_xp = self.me_xp.saturating_add(gain);
                    // Blitz toast (ClientNet.bb:698): "<N> experience points
                    // received!" in warm gold (255,225,100 → LS_XPReceived).
                    // DELIBERATE deviation from Blitz (which prints it for ANY
                    // value): gate on a positive gain. The server legitimately
                    // sends gain==0 as normal traffic — party-split integer
                    // division `PartyXP = XP / Members` is 0 when XP < Members
                    // (GameServer.bb:80→106), and BVM_GIVEXP passes any value
                    // through — so Blitz spams "0 experience points received!" on
                    // small-kill party splits. Suppressing the zero/penalty toast
                    // is a quieter, genuine improvement; me_xp itself still tracks
                    // every gain (the saturating_add above is unconditional).
                    if gain > 0 {
                        let msg = format!(
                            "{gain} {}",
                            self.lang(rcce_data::language::ls::XP_RECEIVED, "experience points received!")
                        );
                        self.chat.push((msg, [1.0, 0.882, 0.392, 1.0]));
                    }
                }
            }
            Some(b'U') => {
                if let Some(level) = MsgReader::new(&d[1..]).u16() {
                    self.me_level = level;
                    self.me_xp = 0;
                }
            }
            // Another actor's level changed (ClientNet.bb:699): RuntimeID(u16) +
            // Level(u16). Keep the actor's displayed level live on level-up.
            Some(b'L') => {
                let mut r = MsgReader::new(&d[1..]);
                if let (Some(rid), Some(level)) = (r.u16(), r.u16()) {
                    if let Some(a) = self.actors.get_mut(&rid) {
                        a.level = level;
                    }
                }
            }
            _ => {}
        }
    }

    /// `P_GoldChange` (ClientNet.bb:947): byte0 `'D'`=decrease (else increase),
    /// then amount(i32). Clamped at 0.
    fn on_gold_change(&mut self, d: &[u8]) {
        let decrease = d.first() == Some(&b'D');
        if let Some(amount) = MsgReader::new(&d[1.min(d.len())..]).i32() {
            // Saturating arithmetic: a hostile/huge wire amount must not overflow
            // (a debug panic / release wraparound). `saturating_neg` also guards
            // the `i32::MIN` negate. Gold is server-authoritative, so clamping the
            // displayed total is the correct soft-fail. (me_xp already does this.)
            let delta = if decrease { amount.saturating_neg() } else { amount };
            self.me_gold = self.me_gold.saturating_add(delta).max(0);
        }
    }

    /// `P_StatUpdate` (ClientNet.bb:996):
    ///   `'A'`/`'M'` + rid(u16) + attrIndex(u8) + value(u16) — attribute value/max.
    ///   `'R'` + rid(u16) + reputation(i16 signed) — reputation (NO attr byte).
    /// The `'R'` case was previously dropped (and the doc mislabelled it
    /// "resistances"), so the character sheet's Reputation stayed frozen at login.
    fn on_stat_update(&mut self, d: &[u8]) {
        let kind = match d.first() {
            Some(&k) => k,
            None => return,
        };
        // 'R' has a distinct layout (no attribute byte) and is signed — handle it
        // before the attribute parse below.
        if kind == b'R' {
            let mut r = MsgReader::new(&d[1..]);
            if let (Some(rid), Some(rep)) = (r.u16(), r.u16()) {
                // Reinterpret the 16-bit field as signed (reputation can be
                // negative) — mirrors RCE_SignedShortFromStr. The server broadcasts
                // a player's reputation change to everyone in the zone (Server.bb
                // UpdateReputation), so apply it to a remote actor too, keeping the
                // target panel live (Blitz ClientNet.bb:1014 sets it for any rid).
                if rid == self.my_runtime_id {
                    self.me_reputation = rep as i16 as i32;
                } else if let Some(a) = self.actors.get_mut(&rid) {
                    a.reputation = rep as i16;
                }
            }
            return;
        }
        let mut r = MsgReader::new(&d[1..]);
        let (Some(rid), Some(attr), Some(val)) = (r.u16(), r.u8(), r.u16()) else {
            return;
        };
        if attr >= 40 {
            return;
        }
        let val = val as i16;
        // Mirror the Health attribute onto the actor's health field so the HP
        // bars reflect live combat damage. Which slot is Health is project-
        // configurable (Fixed Attributes.dat → `health_stat`), NOT always 0.
        let health_stat = self.health_stat;
        if rid == self.my_runtime_id {
            let e = self.me_attributes.entry(attr).or_default();
            match kind {
                b'A' => e.0 = val,
                b'M' => e.1 = val,
                _ => {}
            }
            if attr == health_stat {
                match kind {
                    b'A' => self.me_health = val,
                    b'M' => self.me_health_max = val,
                    _ => {}
                }
            }
        } else if let Some(a) = self.actors.get_mut(&rid) {
            let e = a.attributes.entry(attr).or_default();
            match kind {
                b'A' => e.0 = val,
                b'M' => e.1 = val,
                _ => {}
            }
            if attr == health_stat {
                match kind {
                    b'A' => a.health = val,
                    b'M' => a.health_max = val,
                    _ => {}
                }
            }
        }
    }

    /// `P_ActorDead` (ClientNet.bb:1071): RuntimeID(u16) of the actor that died.
    /// `P_ActorDead` (ClientNet.bb:1071): `[2]deadRID [+ [2]killerRID]`. Marks the
    /// actor dead (it holds the death pose via `DEATH_CLIP`) and — faithful to the
    /// engine — emits a green "You killed <name>!" chat line **only when the local
    /// player is the killer** (CBT-6). Third-party deaths are silent, as in Blitz.
    fn on_actor_dead(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let Some(rid) = r.u16() else { return };
        let killer = r.u16();
        let name = self
            .actors
            .get(&rid)
            .map(|a| {
                let n = a.name.trim();
                if n.is_empty() { "Someone".to_string() } else { n.to_string() }
            })
            .unwrap_or_else(|| "Someone".to_string());
        if let Some(a) = self.actors.get_mut(&rid) {
            a.alive = false;
            // The dying actor cries out (Death). Only for a known actor (so a
            // P_ActorDead for an unknown rid queues nothing).
            self.pending_combat_sounds.push((rid, SPEECH_DEATH));
        }
        if killer == Some(self.my_runtime_id) {
            // Blitz: `LanguageString$(LS_YouKilled) + " " + Name$ + "!"` (green,
            // ClientNet.bb:1091) → "You killed: <name>!". The default keeps the
            // colon to match Blitz (the prior hardcoded line dropped it).
            let msg = format!("{} {name}!", self.lang(rcce_data::language::ls::YOU_KILLED, "You killed:"));
            self.chat.push((msg, [0.3, 1.0, 0.3, 1.0]));
        }
    }

    /// `P_AttackActor` (ClientNet.bb:1115): byte0 subtype + RID(u16) + tail.
    /// - `'H'` I hit RID(=target): rawDamage(u16,−1) + dtype(u8) → feedback floater.
    /// - `'Y'` RID(=attacker) hit me: rawDamage + dtype → attacker plays its swing
    ///   (CBT-3) + a damage floater on me.
    /// - else  RID(=attacker) hit someone else (broadcast): attacker plays its swing.
    ///
    /// The local player's own swing is animated client-side (`me_attack_until`);
    /// this adds the previously-missing **remote** attacker animation.
    fn on_attack_actor(&mut self, d: &[u8]) {
        let Some(&sub) = d.first() else { return };
        let mut r = MsgReader::new(&d[1..]);
        let Some(rid) = r.u16() else { return };
        match sub {
            b'H' => {
                // RID is the target I hit (I am the attacker).
                let (Some(raw_dmg), Some(dtype)) = (r.u16(), r.u8()) else { return };
                let damage = raw_dmg.saturating_sub(1);
                // I grunt (Attack) on every swing I land a 'H' for — unconditional,
                // matching Blitz `PlayActorSound(Me, Speech_Attack*)` at the top of
                // the 'H' branch (ClientNet.bb:1122).
                self.pending_self_sounds.push(SPEECH_ATTACK1);
                self.combat_events.push(CombatEvent {
                    target: rid,
                    attacker: self.my_runtime_id,
                    damage,
                    damage_type: dtype,
                });
                // The target cries out in PAIN (Hit) only on a CONNECTING hit —
                // a parried / 0-damage swing draws no pain cry. Matches Blitz
                // ClientNet.bb:1135 (`PlayActorSound(A, Speech_Hit*)` is inside
                // `If Damage > 0`, :1131).
                if damage > 0 {
                    self.pending_combat_sounds.push((rid, SPEECH_HIT1));
                }
            }
            b'Y' => {
                // RID is the attacker who hit (or swung at) me. Blitz gates the
                // attacker's swing animation, its Attack grunt, AND the floater
                // on Damage != 0 (Damage = raw_dmg - 1): they fire on a landed
                // hit (Damage > 0) and on a miss (Damage < 0, i.e. raw_dmg 0),
                // but NOT on the exactly-0-damage sentinel (raw_dmg 1 → Damage 0,
                // where Blitz's `If/ElseIf` has no `Else`, so nothing happens).
                // ClientNet.bb:1161-1182.
                let (Some(raw_dmg), Some(dtype)) = (r.u16(), r.u8()) else { return };
                if raw_dmg != 1 {
                    self.attack_anims.insert(rid, ATTACK_ANIM_SECS);
                    self.pending_combat_sounds.push((rid, SPEECH_ATTACK1));
                    // I cry out (Hit) only on a CONNECTING blow (Damage > 0), NOT a
                    // miss (raw_dmg 0 → Damage < 0). Tighter than the attacker's
                    // swing gate above. Blitz `PlayActorSound(Me, Speech_Hit*)` is
                    // inside `If Damage > 0` (ClientNet.bb:1166).
                    if raw_dmg.saturating_sub(1) > 0 {
                        self.pending_self_sounds.push(SPEECH_HIT1);
                    }
                    self.combat_events.push(CombatEvent {
                        target: self.my_runtime_id,
                        attacker: rid,
                        damage: raw_dmg.saturating_sub(1),
                        damage_type: dtype,
                    });
                }
            }
            _ => {
                // Broadcast: RID is the attacker (target is RID2, not needed here):
                // animate + voice its swing (Attack1).
                self.attack_anims.insert(rid, ATTACK_ANIM_SECS);
                self.pending_combat_sounds.push((rid, SPEECH_ATTACK1));
            }
        }
    }

    /// Tick down remote attack-swing timers (CBT-3), dropping elapsed ones.
    pub fn tick_attack_anims(&mut self, dt: f32) {
        if self.attack_anims.is_empty() {
            return;
        }
        self.attack_anims.retain(|_, t| {
            *t -= dt;
            *t > 0.0
        });
    }

    /// `P_NameChange` (ClientNet.bb:936): RID(u16) + nameLen(u8) + name + tag.
    fn on_name_change(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(rid), Some(name_len)) = (r.u16(), r.u8()) else {
            return;
        };
        let rest = r.rest();
        let n = (name_len as usize).min(rest.len());
        let name = String::from_utf8_lossy(&rest[..n]).into_owned();
        let tag = String::from_utf8_lossy(&rest[n..]).into_owned();
        if let Some(a) = self.actors.get_mut(&rid) {
            a.name = name;
            a.tag = tag;
        }
    }

    /// `P_InventoryUpdate` (ClientNet.bb:1277): a sub-typed family covering both
    /// world loot ("D"/"P") and the local player's own inventory ("R" received,
    /// "G" given, "T" taken, "H" health), keeping `me_inventory` live.
    fn on_inventory_update(&mut self, d: &[u8]) {
        match d.first() {
            // Item dropped in the world: amount u16, x/y/z f32, handle u32, then
            // the 83-byte ItemInstance (id = first u16, health = last byte).
            Some(b'D') => {
                let mut r = MsgReader::new(&d[1..]);
                let (Some(amount), Some(x), Some(y), Some(z), Some(handle)) =
                    (r.u16(), r.f32(), r.f32(), r.f32(), r.u32())
                else {
                    return;
                };
                let Some(item) = r.bytes(83) else { return };
                let item_id = u16::from_le_bytes([item[0], item[1]]);
                if item_id == 0xFFFF {
                    return; // no-item sentinel
                }
                let health = item[82];
                self.dropped_items
                    .insert(handle, DroppedItem { handle, item_id, amount, health, x, y, z });
            }
            // Someone else picked up a dropped item (handle u32) — remove it.
            Some(b'P') => {
                if let Some(h) = MsgReader::new(&d[1..]).u32() {
                    self.dropped_items.remove(&h);
                }
            }
            // I received a dropped item: handle u32 + slot u8. Move it from the
            // world into my inventory.
            Some(b'R') => {
                let mut r = MsgReader::new(&d[1..]);
                let (Some(handle), Some(slot)) = (r.u32(), r.u8()) else { return };
                if let Some(di) = self.dropped_items.remove(&handle) {
                    self.inv_add(slot, di.item_id, di.amount, di.health);
                    // Queue the "Picked up item: <name> (xN)" toast (resolved +
                    // localized App-side, since item names live in the catalog).
                    self.pending_pickup_toasts.push((di.item_id, di.amount));
                }
            }
            // Given an item: handle u32 + ItemID u16 + Amount u16. Place it in a
            // free/stackable slot and ACK with "GY" + handle + slot (or "GN").
            Some(b'G') => {
                let mut r = MsgReader::new(&d[1..]);
                let (Some(handle), Some(item_id), Some(amount)) = (r.u32(), r.u16(), r.u16()) else {
                    return;
                };
                if item_id == 0xFFFF {
                    return;
                }
                match self.inv_free_slot(item_id) {
                    Some(slot) => {
                        self.inv_add(slot, item_id, amount, 100);
                        let mut reply = b"GY".to_vec();
                        reply.extend_from_slice(&handle.to_le_bytes());
                        reply.push(slot);
                        self.pending_sends.push((pk::INVENTORY_UPDATE, reply));
                    }
                    None => {
                        let mut reply = b"GN".to_vec();
                        reply.extend_from_slice(&handle.to_le_bytes());
                        self.pending_sends.push((pk::INVENTORY_UPDATE, reply));
                    }
                }
            }
            // An item was taken from my inventory: slot u8 + amount u16.
            Some(b'T') => {
                let mut r = MsgReader::new(&d[1..]);
                let (Some(slot), Some(amount)) = (r.u8(), r.u16()) else { return };
                self.inv_remove(slot, amount);
            }
            // Equipped-gear update for an actor: rid u16 + weapon/shield/chest/
            // hat item ids (u16 each, 65535 = none) + 6 gubbin bytes (ignored).
            Some(b'O') => {
                let mut r = MsgReader::new(&d[1..]);
                let (Some(rid), Some(weapon), Some(shield), Some(chest), Some(hat)) =
                    (r.u16(), r.u16(), r.u16(), r.u16(), r.u16())
                else {
                    return;
                };
                if let Some(a) = self.actors.get_mut(&rid) {
                    a.equipped = [weapon, shield, chest, hat];
                }
            }
            // An item's health (durability) changed: slot u8 + health u8.
            Some(b'H') => {
                let mut r = MsgReader::new(&d[1..]);
                let (Some(slot), Some(health)) = (r.u8(), r.u8()) else { return };
                if let Some(it) = self.me_inventory.get_mut(&slot) {
                    it.health = health;
                }
            }
            _ => {}
        }
    }

    /// Add `amount` of `item_id` to inventory slot `slot`, stacking if the slot
    /// already holds the same item.
    fn inv_add(&mut self, slot: u8, item_id: u16, amount: u16, health: u8) {
        let e = self
            .me_inventory
            .entry(slot)
            .or_insert(crate::fetch::InvItem { slot, item_id, amount: 0, health });
        if e.item_id == item_id {
            e.amount = e.amount.saturating_add(amount);
            e.health = health;
        } else {
            *e = crate::fetch::InvItem { slot, item_id, amount, health };
        }
    }

    /// Remove up to `amount` from inventory `slot`, deleting the entry when the
    /// stack reaches zero. Mirrors the local side of Blitz `InventoryDrop`
    /// (Inventories.bb:86) and the inbound "T" (taken) handler — the server
    /// sends no "taken" echo to the *dropper*, so the client must remove its own
    /// dropped item locally or the slot icon lingers (and a later pickup phantom-
    /// duplicates into the next free slot).
    pub fn inv_remove(&mut self, slot: u8, amount: u16) {
        if let Some(it) = self.me_inventory.get_mut(&slot) {
            it.amount = it.amount.saturating_sub(amount);
            if it.amount == 0 {
                self.me_inventory.remove(&slot);
            }
        }
    }

    /// Pick a slot for an incoming item: an existing backpack slot holding the
    /// same item (to stack), else the first empty backpack slot (14..=45).
    fn inv_free_slot(&self, item_id: u16) -> Option<u8> {
        if let Some((&slot, _)) = self
            .me_inventory
            .iter()
            .find(|(&s, it)| s >= 14 && it.item_id == item_id)
        {
            return Some(slot);
        }
        (14u8..=45).find(|s| !self.me_inventory.contains_key(s))
    }

    /// `P_WeatherChange` (ClientNet.bb:1272): areaId u32 + weather u8. Applies
    /// only when it targets the area we're standing in.
    fn on_weather_change(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(area), Some(weather)) = (r.u32(), r.u8()) else {
            return;
        };
        if area == self.zone.area_id {
            self.zone.weather = weather;
        }
    }

    /// `P_FetchActors` arrives as several sentinel-tagged sub-packets; the one we
    /// want is the `"E"` Environment block: `Year(u32) Day(u16) TimeH(u8) TimeM(u8)
    /// TimeFactor(u8)` (seasons/months follow, ignored). Captures the server clock
    /// so day/night follows the world instead of the local noon default.
    fn on_fetch_actors(&mut self, d: &[u8]) {
        if d.first() != Some(&b'E') {
            return; // attributes/items/factions/actors blocks — not the env block
        }
        let mut r = MsgReader::new(&d[1..]);
        let (Some(_year), Some(_day), Some(th), Some(tm), Some(tf)) =
            (r.u32(), r.u16(), r.u8(), r.u8(), r.u8())
        else {
            return;
        };
        self.time_minutes = ((th as f32).clamp(0.0, 23.0) * 60.0 + (tm as f32).clamp(0.0, 59.0)).rem_euclid(1440.0);
        self.time_factor = (tf as u32).max(1); // server clamps to >=1; mirror it
        self.time_known = true;
    }

    /// Advance the local game clock by `dt` real seconds. One game-minute is
    /// `60000/TimeFactor` ms, so game-minutes/sec = `TimeFactor/60`.
    pub fn advance_time(&mut self, dt: f32) {
        if self.time_known {
            self.time_minutes = (self.time_minutes + dt * self.time_factor as f32 / 60.0).rem_euclid(1440.0);
        }
    }

    /// Day/night phase in `[0,1)` (0 = midnight, 0.25 = ~dawn, 0.5 = noon,
    /// 0.75 = ~dusk) from the server clock, or `None` if the clock is unknown.
    pub fn day_phase(&self) -> Option<f32> {
        if self.time_known {
            Some((self.time_minutes / 1440.0).rem_euclid(1.0))
        } else {
            None
        }
    }

    /// `P_Sound` (ClientNet.bb:739): `[2]soundID [+ [2]runtimeID]`. The optional
    /// runtime id is present only for sounds whose name carries the 3D marker;
    /// for the alpha we play every sound 2D, so we read just the id and queue it.
    /// (3D positional attenuation by the actor's position is a noted follow-up.)
    fn on_sound(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        if let Some(id) = r.u16() {
            self.pending_sounds.push(id);
        }
    }

    /// `P_Speech` (ClientNet.bb:733): `[2]soundID [2]runtimeID` — a positional
    /// actor sound. Queued as a 2D one-shot for the alpha (the actor-anchored 3D
    /// `PlayActorSound` is a follow-up; the rid is parsed but not yet used).
    fn on_speech(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        if let Some(id) = r.u16() {
            let _rid = r.u16();
            self.pending_sounds.push(id);
        }
    }

    /// `P_Music` (ClientNet.bb:758): `[2]musicID`. A mid-zone music switch — the
    /// App applies it via `audio.set_music`, which stops/frees the prior track
    /// and loops the new one (matching the Blitz channel-replace).
    fn on_music(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        if let Some(id) = r.u16() {
            self.pending_music = Some(id);
        }
    }

    /// `P_ActorEffect` (ClientNet.bb:493): the local player's status effects.
    /// "A" adds an effect (id u32, texture u16, name), "E" applies an attribute
    /// delta (att u8, amount i32), "R" removes an effect by id and undoes its
    /// 40×i32 attribute deltas.
    fn on_actor_effect(&mut self, d: &[u8]) {
        match d.first() {
            Some(b'A') => {
                let mut r = MsgReader::new(&d[1..]);
                let (Some(id), Some(texture_id)) = (r.u32(), r.u16()) else { return };
                let name = String::from_utf8_lossy(r.rest()).into_owned();
                self.active_effects.retain(|e| e.id != id);
                self.active_effects.push(ActiveEffect { id, texture_id, name });
            }
            Some(b'E') => {
                let mut r = MsgReader::new(&d[1..]);
                let (Some(att), Some(amount)) = (r.u8(), r.i32()) else { return };
                if att < 40 {
                    let e = self.me_attributes.entry(att).or_default();
                    e.0 = e.0.saturating_add(amount as i16);
                }
            }
            Some(b'R') => {
                let mut r = MsgReader::new(&d[1..]);
                let Some(id) = r.u32() else { return };
                self.active_effects.retain(|e| e.id != id);
                // Optional 40×i32 attribute-restore block (subtract the deltas).
                if d.len() >= 1 + 4 + 40 * 4 {
                    for i in 0..40u8 {
                        if let Some(amount) = r.i32() {
                            let e = self.me_attributes.entry(i).or_default();
                            e.0 = e.0.saturating_sub(amount as i16);
                        }
                    }
                }
            }
            _ => {}
        }
    }

    /// `P_ChatMessage`: an optional leading colour sentinel then text (CHAT-2,
    /// ClientNet.bb:1219). 254=yellow, 253=red, 252=purple, 251=green, 250 = the
    /// next 3 bytes as explicit RGB; otherwise white. A `<<…>>`-prefixed line
    /// (the local player's own) renders blue.
    fn on_chat(&mut self, d: &[u8]) {
        if d.is_empty() {
            return;
        }
        let (mut color, body): ([f32; 4], &[u8]) = match d[0] {
            254 => ([1.0, 1.0, 0.0, 1.0], &d[1..]),
            253 => ([1.0, 0.2, 0.2, 1.0], &d[1..]),
            252 => ([0.78, 0.04, 0.78, 1.0], &d[1..]),
            251 => ([0.08, 0.86, 0.2, 1.0], &d[1..]),
            250 if d.len() >= 4 => (
                [d[1] as f32 / 255.0, d[2] as f32 / 255.0, d[3] as f32 / 255.0, 1.0],
                &d[4..],
            ),
            _ => ([0.92, 0.92, 0.78, 1.0], d),
        };
        let text: String = body.iter().filter(|&&b| b >= 32).map(|&b| b as char).collect();
        let text = text.trim().to_string();
        if text.starts_with("<<") {
            color = [0.0, 0.5, 1.0, 1.0]; // local player's own line
        }
        // Player-chat speech bubble: a plain say arrives as "<Name> text" with NO
        // colour-code prefix (`d[0] == '<'`, ServerNet.bb:708). Colour-prefixed
        // yells/guild/etc. (250..=254) are NOT bubbled — matching Blitz, where only
        // the "Normal" branch bubbles (ClientNet.bb:1237). Shown over the speaker
        // when the project enables bubbles (UseBubbles > 1); the log line still shows.
        if self.bubbles_enabled && d[0] == b'<' {
            if let Some(bubble) = self.chat_bubble_for(&text) {
                self.pending_bubbles.push(bubble);
            }
        }
        if !text.is_empty() {
            self.chat.push((text, color));
        }
    }

    /// Resolve a plain say `"<Name> text"` to a bubble `(runtime id, message, colour)`
    /// when the speaker is a known actor (or the local player). `None` when the format
    /// doesn't match or the name is unknown — the say then only hits the chat log. The
    /// local player's own line bubbles over `my_runtime_id` in blue (Blitz `If AI = Me`);
    /// others use the project's bubble colour.
    fn chat_bubble_for(&self, text: &str) -> Option<(u16, String, [f32; 4])> {
        let rest = text.strip_prefix('<')?;
        let close = rest.find('>')?;
        let name = &rest[..close];
        if name.is_empty() {
            return None;
        }
        let msg = rest[close + 1..].trim_start().to_string();
        if msg.is_empty() {
            return None;
        }
        if !self.me_name.is_empty() && name == self.me_name {
            return Some((self.my_runtime_id, msg, [0.0, 0.5, 1.0, 1.0]));
        }
        let rid = self.actors.values().find(|a| a.name == name).map(|a| a.runtime_id)?;
        Some((rid, msg, self.bubble_color))
    }

    /// Handle an inbound `P_Dialog` (NPC dialog). Builds/updates `self.dialog`
    /// and queues the "N"/"T" acks the NPC `Main` script waits on. Soft-fails on
    /// a short/garbage payload. ref ClientNet.bb:1027-1068.
    fn on_dialog(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        match r.u8() {
            // New: [4]scriptHandle [2]runtimeID [2]bgTexID [n]title.
            Some(b'N') => {
                let (Some(script), Some(rid), Some(_bg)) = (r.u32(), r.u16(), r.u16()) else {
                    return;
                };
                let title = String::from_utf8_lossy(r.rest()).into_owned();
                self.dialog = Some(Dialog {
                    script_handle: script,
                    runtime_id: rid,
                    title,
                    lines: Vec::new(),
                    options: Vec::new(),
                });
                // Reply "N" + scriptHandle + our dialog handle (we reuse the
                // scriptHandle as the handle) so the server maps its script here.
                let mut w = MsgWriter::new();
                w.u8(b'N').u32(script).u32(script);
                self.pending_sends.push((pk::DIALOG, w.into_bytes()));
            }
            // Text: [1]R [1]G [1]B [4]dialogHandle [n]text.
            Some(b'T') => {
                let (Some(red), Some(green), Some(blue), Some(_dh)) =
                    (r.u8(), r.u8(), r.u8(), r.u32())
                else {
                    return;
                };
                let text = String::from_utf8_lossy(r.rest()).into_owned();
                if let Some(dl) = self.dialog.as_mut() {
                    let col = [red as f32 / 255.0, green as f32 / 255.0, blue as f32 / 255.0, 1.0];
                    dl.lines.push((text, col));
                    let mut w = MsgWriter::new();
                    w.u8(b'T').u32(dl.script_handle);
                    self.pending_sends.push((pk::DIALOG, w.into_bytes()));
                }
            }
            // Options: [4]dialogHandle then repeated [1]len [len]optionText.
            Some(b'O') => {
                if r.u32().is_none() {
                    return;
                }
                if let Some(dl) = self.dialog.as_mut() {
                    dl.options.clear();
                    while let Some(n) = r.u8() {
                        let Some(b) = r.bytes(n as usize) else { break };
                        dl.options.push(String::from_utf8_lossy(b).into_owned());
                    }
                }
            }
            // Close: [4]dialogHandle.
            Some(b'C') => self.dialog = None,
            _ => {}
        }
    }

    /// `P_ScriptInput` (ClientNet.bb:1020-1024): a scripted free-text prompt.
    /// Wire: `[4]scriptHandle [1]masked [2]titleLen [titleLen]title [..]prompt`.
    /// Opens the input dialog; the user's reply goes back as
    /// `[4]scriptHandle + text` (see `net::script_input_reply`).
    fn on_script_input(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(script), Some(masked), Some(title_len)) = (r.u32(), r.u8(), r.u16()) else {
            return;
        };
        let Some(title_b) = r.bytes(title_len as usize) else {
            return;
        };
        let title = String::from_utf8_lossy(title_b).into_owned();
        let prompt = String::from_utf8_lossy(r.rest()).into_owned();
        self.script_input = Some(ScriptInput {
            script_handle: script,
            masked: masked != 0,
            title,
            prompt,
            text: String::new(),
        });
    }

    /// `P_ProgressBar` (ClientNet.bb:151-177): scripted progress bars.
    /// - `"C"`: `[1]R [1]G [1]B [4]X [4]Y [4]W [4]H [4]serverToken [2]max [2]value [..]text`
    ///   → create a bar, mint a client handle, reply `"C" + serverToken + clientHandle`.
    /// - `"U"`: `[4]clientHandle [2]value` → update.
    /// - `"D"`: `[4]clientHandle` → remove.
    fn on_progress_bar(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        match r.u8() {
            Some(b'C') => {
                let (Some(red), Some(green), Some(blue)) = (r.u8(), r.u8(), r.u8()) else {
                    return;
                };
                let (Some(x), Some(y), Some(w), Some(h)) = (r.f32(), r.f32(), r.f32(), r.f32())
                else {
                    return;
                };
                let Some(server_token) = r.bytes(4).map(<[u8; 4]>::try_from).and_then(Result::ok)
                else {
                    return;
                };
                let (Some(max), Some(value)) = (r.u16(), r.u16()) else {
                    return;
                };
                let text = String::from_utf8_lossy(r.rest()).into_owned();
                self.next_pbar_handle = self.next_pbar_handle.max(1);
                let handle = self.next_pbar_handle;
                self.next_pbar_handle += 1;
                self.progress_bars.push(ProgressBar {
                    client_handle: handle,
                    color: [red as f32 / 255.0, green as f32 / 255.0, blue as f32 / 255.0],
                    x,
                    y,
                    w,
                    h,
                    max,
                    value,
                    text,
                });
                // Reply so the server can address later U/D to our handle.
                let mut reply = vec![b'C'];
                reply.extend_from_slice(&server_token);
                reply.extend_from_slice(&handle.to_le_bytes());
                self.pending_sends.push((pk::PROGRESS_BAR, reply));
            }
            Some(b'U') => {
                let (Some(handle), Some(value)) = (r.u32(), r.u16()) else {
                    return;
                };
                if let Some(b) = self.progress_bars.iter_mut().find(|b| b.client_handle == handle) {
                    b.value = value;
                }
            }
            Some(b'D') => {
                if let Some(handle) = r.u32() {
                    self.progress_bars.retain(|b| b.client_handle != handle);
                }
            }
            _ => {}
        }
    }

    /// Resolve a runtime id to a world position (self or a tracked actor).
    ///
    /// For the LOCAL player this is the **displayed** body position
    /// (`me_render_x/z`), not the raw server echo (`me_x/z`) — Blitz positions a
    /// projectile at `EntityX(Source\CollisionEN)` (Projectiles3D.bb:59), i.e.
    /// the entity the player sees. Under the client-authoritative movement model
    /// the echo lags the visible body by up to a network round-trip, so an
    /// own-cast projectile sourced from `me_x` would visibly detach from the
    /// caster while moving (SPL-8). Falls back to the echo before the first
    /// `tick_movement` primes the render position.
    fn actor_pos(&self, rid: u16) -> Option<[f32; 3]> {
        if rid == self.my_runtime_id {
            Some(self.me_display_pos())
        } else {
            self.actors.get(&rid).map(|a| [a.x, a.y, a.z])
        }
    }

    /// The local player's displayed body position (see `actor_pos`).
    fn me_display_pos(&self) -> [f32; 3] {
        if self.me_render_init {
            [self.me_render_x, self.me_y, self.me_render_z]
        } else {
            [self.me_x, self.me_y, self.me_z]
        }
    }

    /// Handle an inbound `P_Projectile`: spawn a projectile at the source actor
    /// flying toward the target. Soft-fails if either actor is unknown.
    /// ref ClientNet.bb:217-238.
    fn on_projectile(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(src), Some(tgt), Some(_mesh), Some(tex1), Some(tex2), Some(homing), Some(spd)) =
            (r.u16(), r.u16(), r.u16(), r.u16(), r.u16(), r.u8(), r.u8())
        else {
            return;
        };
        // Emitter config names (GameServer.bb:261): Emitter1$ is length-prefixed
        // (str8), Emitter2$ is the rest. Blitz attaches BOTH to the projectile —
        // Emitter1$ (e.g. "Default", the base trail) with TexID1, Emitter2$ (the
        // distinctive effect, e.g. "Fireball") with TexID2. Empty names → plain glow.
        let emitter1 = r.str8().unwrap_or_default().trim().to_string();
        let emitter2: String = String::from_utf8_lossy(r.rest()).into_owned();
        let emitter2 = emitter2.trim().to_string();
        let (Some(sp), Some(tp)) = (self.actor_pos(src), self.actor_pos(tgt)) else {
            return;
        };
        let id = self.next_proj_id;
        self.next_proj_id = self.next_proj_id.wrapping_add(1);
        // Blitz: Speed# = (serverSpeed/50)·2.0 units/frame@30fps → ·30 for /sec.
        let speed = (spd as f32 / 50.0) * 2.0 * 30.0;
        self.projectiles.push(Projectile {
            x: sp[0],
            y: sp[1] + 3.0,
            z: sp[2],
            target_rid: if homing != 0 { tgt } else { 0 },
            tx: tp[0],
            ty: tp[1] + 3.0,
            tz: tp[2],
            homing: homing != 0,
            speed,
            id,
            emitter: emitter2,
            emitter_tex: tex2,
            emitter1,
            emitter1_tex: tex1,
        });
    }

    /// `P_Jump` (ClientNet.bb:241): a 2-byte RID — a remote actor jumped. Start
    /// its jump-anim timer (ANIM-7). Skip our own RID; the local jump is driven
    /// by the App's physics integration, not this timer.
    fn on_jump(&mut self, d: &[u8]) {
        if let Some(rid) = MsgReader::new(d).u16() {
            if rid != self.my_runtime_id {
                self.jumps.insert(rid, JUMP_ANIM_SECS);
            }
        }
    }

    /// `P_FloatingNumber` (ClientNet.bb:205): RuntimeID(u16) · Amount(i32) ·
    /// R(u8) · G(u8) · B(u8). A script-driven floating number (heal popups,
    /// custom text) broadcast to everyone in the zone. We queue the intent; the
    /// App anchors it over the actor and animates it with the floater system.
    /// Soft-fail: a short/garbled packet queues nothing (no panic, no crash).
    fn on_floating_number(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(rid), Some(amount), Some(cr), Some(cg), Some(cb)) =
            (r.u16(), r.i32(), r.u8(), r.u8(), r.u8())
        else {
            return;
        };
        self.pending_floaters.push((rid, amount, [cr, cg, cb]));
    }

    /// `P_AppearanceUpdate` (ClientNet.bb:268): a live appearance change on an
    /// already-spawned actor. Layout: subtype(u8) · RuntimeID(u16) · value, where
    /// value is a u16 race id for "C" and a single u8 for the rest:
    ///   "C" race · "G" gender · "D" beard · "H" hair · "F" face · "B" body.
    ///
    /// We mutate the actor's appearance field in place (the local player's via the
    /// `me_*` mirror that the in-world body reads); the per-frame actor re-bake
    /// (`dyn_hash`, ~12 Hz) then rebuilds the mesh/skin with the new look — no
    /// explicit mesh reload needed. Soft-fails to nothing on a short packet, an
    /// unknown subtype, or an unknown actor (mirrors Blitz `If AI <> Null`).
    ///
    /// The wire bytes are clamped at the receive site exactly as Blitz does:
    /// gender to 0/1 (:323) and the cosmetic indices to 0..=4 (:351/:391/:402/:444,
    /// since beard/hair/face/body index `Field [4]` = 5-slot arrays).
    fn on_appearance_update(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(sub), Some(rid)) = (r.u8(), r.u16()) else {
            return;
        };
        let is_me = rid == self.my_runtime_id;
        match sub {
            b'C' => {
                // Race change: new template id (u16). Re-derive gender from the
                // NEW template's gender mode (Blitz ClientNet.bb:276-277: Genders
                // 2 → female, 1/3 → male, 0 → keep). `template_genders` holds the
                // raw Blitz Genders value (0 both · 1 male · 2 female · 3 none).
                let Some(id) = r.u16() else {
                    return;
                };
                let genders = self.template_genders.get(&id).copied().unwrap_or(0);
                let fix = |g: u8| -> u8 {
                    if genders == 2 {
                        1
                    } else if genders == 1 || genders == 3 {
                        0
                    } else {
                        g
                    }
                };
                if is_me {
                    self.me_actor_id = id;
                    self.me_gender = fix(self.me_gender);
                } else if let Some(a) = self.actors.get_mut(&rid) {
                    a.template_id = id;
                    a.gender = fix(a.gender);
                }
            }
            b'G' | b'D' | b'H' | b'F' | b'B' => {
                let Some(raw) = r.u8() else {
                    return;
                };
                let v = if sub == b'G' { raw.min(1) } else { raw.min(4) };
                if is_me {
                    match sub {
                        b'G' => self.me_gender = v,
                        b'D' => self.me_beard = v,
                        b'H' => self.me_hair = v,
                        b'F' => self.me_face_tex = v,
                        b'B' => self.me_body_tex = v,
                        _ => {}
                    }
                } else if let Some(a) = self.actors.get_mut(&rid) {
                    match sub {
                        b'G' => a.gender = v,
                        b'D' => a.beard = v,
                        b'H' => a.hair = v,
                        b'F' => a.face_tex = v,
                        b'B' => a.body_tex = v,
                        _ => {}
                    }
                }
            }
            _ => {}
        }
    }

    /// `P_RepositionActor` (ClientNet.bb:180): the server hard-teleports an actor
    /// (warp, knockback, GM/script move, anti-cheat correction). Layout:
    /// subtype(u8) · RuntimeID(u16) · then, for "M" (move) X·Y·Z(f32) + an
    /// optional camera/collision flag(u8); for the rotate subtype, Yaw(f32).
    ///
    /// Unlike the ~9 Hz `P_StandardUpdate` echo (which the client *interpolates*
    /// toward), this is an explicit snap — Blitz does `PositionEntity` +
    /// `ResetEntity`. So we set the authoritative position AND force the render
    /// position to it, clearing the interpolation trail, so the actor (or the
    /// local player) snaps instead of gliding across the zone. Soft-fails on a
    /// short packet or unknown actor (mirrors Blitz `If AI <> Null`).
    fn on_reposition_actor(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(sub), Some(rid)) = (r.u8(), r.u16()) else {
            return;
        };
        let is_me = rid == self.my_runtime_id;
        if sub == b'M' {
            let (Some(x), Some(y), Some(z)) = (r.f32(), r.f32(), r.f32()) else {
                return;
            };
            // The trailing byte is Blitz's MoveCamera / ignore-collision flag; the
            // Rust third-person camera follows the (now-snapped) render position,
            // so it snaps with the body. Consumed but not load-bearing here.
            let _flag = r.u8();
            if is_me {
                self.me_x = x;
                self.me_y = y;
                self.me_z = z;
                // Hard snap: render = authoritative, fresh trail so the next
                // tick_movement snaps (`first`) rather than reconciling/gliding.
                self.me_render_x = x;
                self.me_render_z = z;
                self.me_render_init = true;
                self.me_samples.clear();
            } else if let Some(a) = self.actors.get_mut(&rid) {
                a.x = x;
                a.y = y;
                a.z = z;
                a.dest_x = x;
                a.dest_z = z;
                a.render_x = x;
                a.render_z = z;
                a.samples.clear();
            }
        } else {
            let Some(yaw) = r.f32() else {
                return;
            };
            if is_me {
                self.me_yaw = yaw;
            } else if let Some(a) = self.actors.get_mut(&rid) {
                a.yaw = yaw;
                a.render_yaw = yaw;
            }
        }
        // Warp-completion ack, only when WE were the repositioned actor: tells
        // the server the move is applied so it stops suppressing our position
        // updates (`AI\IgnoreUpdate = 0`, ServerNet.bb:730 — the server ignores
        // the payload). Not sent for other actors' repositions: only our own
        // suppression window can be armed, and acking every bystander
        // reposition would be N-player packet noise.
        if is_me {
            self.pending_sends.push((pk::REPOSITION_ACTOR, d.to_vec()));
        }
    }

    /// `P_AnimateActor` (ClientNet.bb:713): the server commands an actor to play a
    /// named animation — an emote or a scripted pose. Layout: RuntimeID(u16) ·
    /// FixedSpeed(u8) · Speed(f32) · AnimName(string-to-end). We queue a
    /// `(rid, name)` intent; the App resolves the clip + its natural duration via
    /// the AssetStore (out of `World`'s reach) and installs a timed override.
    ///
    /// Speed/FixedSpeed are parsed (to reach the name) but not applied: Blitz's
    /// playback rate is framerate-coupled (`Animate` advances N frames per render
    /// tick), which can't be reproduced faithfully here, so the clip plays at its
    /// data-native rate — the deterministic, sensible choice. Soft-fails on a
    /// short packet or an empty name.
    fn on_animate_actor(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(rid), Some(_fixed), Some(_speed)) = (r.u16(), r.u8(), r.f32()) else {
            return;
        };
        let name = String::from_utf8_lossy(r.rest()).into_owned();
        if name.is_empty() {
            return;
        }
        // Pin a remote actor's destination to its current position, exactly as
        // Blitz does on receipt (ClientNet.bb:727-728). Otherwise a stale dest
        // left by the prior P_StandardUpdate (an actor that was walking when the
        // emote was issued) reads as "still moving", and tick_server_anims would
        // cancel the held end pose the instant the one-shot plays out. (Me isn't
        // in `actors`; its movement-cancel uses the live `me_moving` intent.)
        if let Some(a) = self.actors.get_mut(&rid) {
            a.dest_x = a.x;
            a.dest_z = a.z;
        }
        self.pending_anims.push((rid, name));
    }

    /// `P_CreateEmitter` (ClientNet.bb:772): the server spawns a particle emitter —
    /// a spell/ability/script visual. Layout: TexID(u16) · Time(u32, ms lifetime) ·
    /// RuntimeID(u16, attach target) · X·Y·Z(f32) · ConfigName(string-to-end). We
    /// queue the request; the App loads the `.rpc` config + texture and adds a
    /// finite emitter to the sim.
    ///
    /// The config name is validated against path traversal exactly as Blitz does
    /// (ClientNet.bb:785-797): a hostile server could otherwise pass `..\..\x` to
    /// escape `Data/Emitter Configs`. Reject empty / >240 chars, any byte outside
    /// printable ASCII, `:` `/` `\`, or a `..` substring. Soft-fails (queues
    /// nothing) on a bad name or short packet. A non-zero RuntimeID attaches the
    /// emitter to that actor (`pos` is then an actor-local offset it follows each
    /// frame); RuntimeID 0 = world-anchored (`pos` absolute).
    fn on_create_emitter(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(tex_id), Some(lifetime_ms), Some(rid)) = (r.u16(), r.u32(), r.u16()) else {
            return;
        };
        let (Some(x), Some(y), Some(z)) = (r.f32(), r.f32(), r.f32()) else {
            return;
        };
        let name = String::from_utf8_lossy(r.rest()).into_owned();
        if name.is_empty() || name.len() > 240 || name.contains("..") {
            return;
        }
        if name.bytes().any(|b| !(32..=126).contains(&b) || b == b':' || b == b'/' || b == b'\\') {
            return;
        }
        // RuntimeID 0 = world-anchored (BVM_CREATEEMITTER sends 0 when no actor is
        // given, ScriptingCommands.bb:1730); >0 = attach to that actor, with `pos`
        // an actor-local offset (ClientNet.bb:816 EntityParent + local PositionEntity).
        let attach = (rid != 0).then_some(rid);
        self.pending_emitters.push(PendingEmitter {
            tex_id,
            lifetime_ms,
            pos: [x, y, z],
            name,
            attach,
        });
    }

    /// `P_ItemHealth` (ClientNet.bb:249): the server reports a durability change
    /// on one of the local player's equipped items — the **combat** wear channel
    /// (GameServer.bb:544/560), distinct from the script-driven `P_InventoryUpdate`
    /// `"H"` sub-packet. Layout: slot(u8) · health(u16). The value is durability
    /// (0..100), so it fits the `u8` health field; clamp defensively. Keeping it
    /// live matters because `effective_attack_range` falls a ranged weapon back to
    /// melee once its `item_health` hits 0. Soft-fails on a short packet or an
    /// empty slot (mirrors Blitz `If … <> Null`).
    fn on_item_health(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(slot), Some(health)) = (r.u8(), r.u16()) else {
            return;
        };
        if let Some(it) = self.me_inventory.get_mut(&slot) {
            it.health = health.min(255) as u8;
        }
    }

    /// `P_SelectScenery` (ClientNet.bb:255): a stock/modified Blitz server confirms
    /// the player owns a prop they activated and echoes `Handle(u32)`, telling the
    /// client to play the prop's toggle animation. RCCE's server never sends this —
    /// its inbound case is commented out (ServerNet.bb:741) — so it only arrives from
    /// a legacy Blitz server. The local mesh animation is deferred (Rust scenery
    /// renders as static instances), so we soft-fail-read the handle and drop it
    /// rather than crash on an unexpected payload (the wire-safety discipline). Kept
    /// as an explicit arm, not `_ => {}`, to document the inbound half of the C2
    /// contract.
    fn on_select_scenery(&mut self, d: &[u8]) {
        let _ = MsgReader::new(d).u32();
    }

    /// Advance server-commanded animations and expire them the Blitz mode-3 way:
    /// the clip always plays through once (movement can't interrupt it), then its
    /// end pose holds until the actor next moves — at which point the override is
    /// dropped and locomotion takes over. `me_moving` is the local player's current
    /// movement intent; remote actors use their own dest-vs-position delta.
    pub fn tick_server_anims(&mut self, dt: f32, me_moving: bool) {
        if self.server_anims.is_empty() {
            return;
        }
        let actors = &self.actors;
        let my_rid = self.my_runtime_id;
        self.server_anims.retain(|&rid, sa| {
            sa.elapsed += dt;
            // Still playing the one-shot through → keep regardless of movement.
            if sa.elapsed < sa.duration {
                return true;
            }
            // Played out; hold the end pose until this actor moves.
            let moving = if rid == my_rid {
                me_moving
            } else if let Some(a) = actors.get(&rid) {
                let (dx, dz) = (a.dest_x - a.x, a.dest_z - a.z);
                dx * dx + dz * dz > 1.0
            } else {
                true // actor gone → drop the override
            };
            !moving
        });
    }

    /// Tick down remote jump-anim timers, dropping any that have elapsed.
    pub fn tick_jumps(&mut self, dt: f32) {
        if self.jumps.is_empty() {
            return;
        }
        self.jumps.retain(|_, t| {
            *t -= dt;
            *t > 0.0
        });
    }

    /// Advance every projectile toward its target and drop those that impact
    /// (within 2 units). Homing projectiles re-acquire the live target position.
    pub fn tick_projectiles(&mut self, dt: f32) {
        let my = self.my_runtime_id;
        // Homing at ME re-acquires the displayed body (same convention as the
        // spawn in `actor_pos`; Blitz tracks the CollisionEN entity,
        // Projectiles3D.bb:79-81).
        let me = self.me_display_pos();
        for p in &mut self.projectiles {
            if p.homing {
                let tp = if p.target_rid == my {
                    Some(me)
                } else {
                    self.actors.get(&p.target_rid).map(|a| [a.x, a.y, a.z])
                };
                if let Some(tp) = tp {
                    p.tx = tp[0];
                    p.ty = tp[1] + 3.0;
                    p.tz = tp[2];
                }
            }
            let (dx, dy, dz) = (p.tx - p.x, p.ty - p.y, p.tz - p.z);
            let dist = (dx * dx + dy * dy + dz * dz).sqrt();
            if dist > 0.001 {
                let step = (p.speed * dt).min(dist);
                p.x += dx / dist * step;
                p.y += dy / dist * step;
                p.z += dz / dist * step;
            }
        }
        self.projectiles.retain(|p| {
            let (dx, dy, dz) = (p.tx - p.x, p.ty - p.y, p.tz - p.z);
            (dx * dx + dy * dy + dz * dz).sqrt() > 2.0
        });
    }

    /// Handle `P_ScreenFlash`: `[1]R [1]G [1]B [1]alpha [4]lengthMs [2]texID`.
    /// Stores a pending flash the renderer drains + fades out. ref
    /// ClientNet.bb:679-686.
    fn on_screen_flash(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(red), Some(green), Some(blue), Some(alpha), Some(length_ms), Some(_tex)) =
            (r.u8(), r.u8(), r.u8(), r.u8(), r.u32(), r.u16())
        else {
            return;
        };
        self.flash = Some(ScreenFlash {
            color: [red as f32 / 255.0, green as f32 / 255.0, blue as f32 / 255.0],
            alpha: alpha as f32 / 255.0,
            length: (length_ms as f32 / 1000.0).max(0.05),
        });
    }

    /// Handle `P_KnownSpellUpdate` (SPL-7, ClientNet.bb:823-933): "A" adds a
    /// spell (level u16, id u16, thumb u16, recharge u16, name str16, …), "D"
    /// removes by name, "L" sets a spell's level (level u32 + name). Keeps the
    /// list sorted by name.
    fn on_known_spell_update(&mut self, d: &[u8]) {
        match d.first() {
            Some(b'A') => {
                let mut r = MsgReader::new(&d[1..]);
                let (Some(level), Some(id), Some(_thumb), Some(_recharge)) =
                    (r.u16(), r.u16(), r.u16(), r.u16())
                else {
                    return;
                };
                let name = r.str16().unwrap_or_default();
                if !name.is_empty() && !self.known_spells.iter().any(|s| s.id == id) {
                    // The server appended this spell at the next free KnownSpells[]
                    // slot; mirror that as the add-order index (the wire memorise
                    // index). Display order is sorted; the index is preserved.
                    let known_index = self.known_spells.len() as u16;
                    self.known_spells.push(KnownSpell { id, name, level, known_index });
                    self.known_spells.sort_by_key(|a| a.name.to_lowercase());
                }
            }
            Some(b'D') => {
                let name = String::from_utf8_lossy(&d[1..]).trim().to_uppercase();
                self.known_spells.retain(|s| s.name.to_uppercase() != name);
            }
            Some(b'L') => {
                let mut r = MsgReader::new(&d[1..]);
                let Some(level) = r.u32() else { return };
                let name = String::from_utf8_lossy(r.rest()).trim().to_uppercase();
                for s in &mut self.known_spells {
                    if s.name.to_uppercase() == name {
                        s.level = level as u16;
                    }
                }
            }
            _ => {}
        }
    }

    /// Handle `P_BubbleMessage` (CHAT-4, ClientNet.bb:1209): `[2]rid [1]R [1]G
    /// [1]B [n]text` — a speech bubble over the actor. Queued for the renderer.
    fn on_bubble_message(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(rid), Some(red), Some(green), Some(blue)) = (r.u16(), r.u8(), r.u8(), r.u8())
        else {
            return;
        };
        let text: String = r.rest().iter().filter(|&&b| b >= 32).map(|&b| b as char).collect();
        let text = text.trim().to_string();
        if !text.is_empty() {
            let col = [red as f32 / 255.0, green as f32 / 255.0, blue as f32 / 255.0, 1.0];
            self.pending_bubbles.push((rid, text, col));
        }
    }

    /// Handle `P_QuestLog` (QST-1/2, ClientNet.bb:955): "N" adds an entry
    /// (`nameLen u8 · name · statusLen u16 · statusBlob`), "U" updates a quest's
    /// status by name, "D" removes by name. The status blob is parsed by
    /// [`parse_quest_status`] (RGB + optional completed marker + text).
    fn on_quest_log(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        match r.u8() {
            Some(b'N') => {
                let Some(name) = r.str8() else { return };
                let Some(n) = r.u16() else { return };
                let Some(raw) = r.bytes(n as usize) else { return };
                let (status, color, completed) = parse_quest_status(raw);
                if !name.is_empty() && !self.quests.iter().any(|q| q.name.eq_ignore_ascii_case(&name)) {
                    self.quests.push(Quest { name, status, color, completed });
                }
            }
            Some(b'U') => {
                let Some(name) = r.str8() else { return };
                let Some(n) = r.u16() else { return };
                let Some(raw) = r.bytes(n as usize) else { return };
                let (status, color, completed) = parse_quest_status(raw);
                for q in &mut self.quests {
                    if q.name.eq_ignore_ascii_case(&name) {
                        q.status = status.clone();
                        q.color = color;
                        q.completed = completed;
                    }
                }
            }
            Some(b'D') => {
                let name = String::from_utf8_lossy(r.rest()).trim().to_uppercase();
                self.quests.retain(|q| q.name.to_uppercase() != name);
            }
            _ => {}
        }
    }

    /// Handle `P_PartyUpdate` (PTY-2, ClientNet.bb:483): 7 slots of `nameLen u8 ·
    /// name`; empty slots are dropped. Replaces the party member list.
    fn on_party_update(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let mut names = Vec::new();
        for _ in 0..7 {
            let Some(name) = r.str8() else { break };
            if !name.trim().is_empty() {
                names.push(name);
            }
        }
        self.party = names;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rcce_net::codec::MsgWriter;

    // The local player's facing EASES toward the movement heading (same rate as
    // remote actors) instead of snapping; idle holds the last facing.
    #[test]
    fn local_facing_eases_not_snaps() {
        std::env::remove_var("RCCE_SERVERMOVE");
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        w.me_yaw = 0.0;
        // Walking toward +x → heading −90°. After a tick the facing has moved PART
        // of the way, not snapped.
        w.tick_movement(0.0, 0.016, [1.0, 0.0], true, false);
        let before = w.me_yaw;
        w.tick_movement(0.05, 0.016, [1.0, 0.0], true, false);
        let after = w.me_yaw;
        assert!(after < before, "eased toward −90: {before} → {after}");
        assert!(after > -89.0, "did NOT snap to −90 in one tick: {after}");
        // Many ticks converge to the heading.
        for i in 0..300 {
            w.tick_movement(0.1 + i as f32 * 0.016, 0.016, [1.0, 0.0], true, false);
        }
        assert!((w.me_yaw + 90.0).abs() < 1.0, "converged to −90: {}", w.me_yaw);
        // Idle holds the facing (no movement → no rotation).
        let held = w.me_yaw;
        w.tick_movement(10.0, 0.016, [0.0, 0.0], false, false);
        assert!((w.me_yaw - held).abs() < 1e-4, "idle holds facing: {} vs {held}", w.me_yaw);
    }

    #[test]
    fn client_authoritative_move_leads_and_doesnt_rubberband() {
        std::env::remove_var("RCCE_SERVERMOVE");
        std::env::set_var("RCCE_MOVESPEED", "46");
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        // Prime: first tick snaps render to the (origin) server pos + seeds samples.
        w.tick_movement(0.0, 0.016, [0.0, 1.0], true, false);
        let start = w.me_render_z;
        // Walk forward (+z) for 0.1 s; server pos (me_x/z) stays at origin (no echo
        // yet). Client-authoritative ⇒ the body advances ~46·0.1 and is NOT dragged
        // back (within the deadzone).
        w.tick_movement(0.12, 0.1, [0.0, 1.0], true, false);
        let walked = w.me_render_z - start;
        assert!((walked - 4.6).abs() < 0.6, "walked {walked} (expected ~4.6)");
        // A teleport-scale server jump still snaps the body to the server position.
        w.me_z = 1000.0;
        w.tick_movement(0.20, 0.016, [0.0, 0.0], false, false);
        assert!((w.me_render_z - 1000.0).abs() < 0.01, "warp didn't snap: {}", w.me_render_z);
        std::env::remove_var("RCCE_MOVESPEED");
    }

    #[test]
    fn fetch_actors_env_block_sets_server_clock() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        // "E" + Year(u32) + Day(u16) + TimeH(u8) + TimeM(u8) + TimeFactor(u8),
        // then season/month bytes we ignore. 18:30 = dusk-ish.
        let mut p = vec![b'E'];
        p.extend_from_slice(&1000u32.to_le_bytes());
        p.extend_from_slice(&5u16.to_le_bytes());
        p.push(18); // TimeH
        p.push(30); // TimeM
        p.push(10); // TimeFactor
        p.extend_from_slice(&[0u8; 64]); // trailing seasons/months (ignored)
        w.apply(&msg(pk::FETCH_ACTORS, p));
        assert!(w.time_known);
        assert_eq!(w.time_factor, 10);
        let ph = w.day_phase().unwrap();
        assert!((ph - (18.0 * 60.0 + 30.0) / 1440.0).abs() < 1e-4, "phase {ph}");
        // Non-env sub-packets (e.g. "A" attributes) don't clobber the clock.
        w.apply(&msg(pk::FETCH_ACTORS, vec![b'A', 0, 0, 0]));
        assert!(w.time_known);
        // Advance ~6 real seconds → +1 game minute at TimeFactor 10.
        let before = w.time_minutes;
        w.advance_time(6.0);
        assert!((w.time_minutes - (before + 1.0)).abs() < 1e-3, "advanced to {}", w.time_minutes);
    }

    fn msg(t: u8, payload: Vec<u8>) -> RecvMessage {
        RecvMessage {
            msg_type: t,
            connection: 0,
            data: payload,
        }
    }

    #[test]
    fn dialog_new_text_options_close() {
        let mut w = World::default();
        // "N": scriptHandle, runtimeID, bgTexID, title.
        let mut p = MsgWriter::new();
        p.u8(b'N').u32(0x1122_3344).u16(5).u16(0).raw(b"Hail");
        w.apply(&msg(pk::DIALOG, p.into_bytes()));
        let dl = w.dialog.as_ref().expect("dialog created");
        assert_eq!((dl.script_handle, dl.runtime_id, dl.title.as_str()), (0x1122_3344, 5, "Hail"));
        // Reply "N" + scriptHandle + dialogHandle (== scriptHandle).
        let mut exp = MsgWriter::new();
        exp.u8(b'N').u32(0x1122_3344).u32(0x1122_3344);
        assert_eq!(w.pending_sends, vec![(pk::DIALOG, exp.into_bytes())]);
        w.pending_sends.clear();

        // "T": green text line + a "T" ack.
        let mut t = MsgWriter::new();
        t.u8(b'T').u8(0).u8(255).u8(0).u32(0x1122_3344).raw(b"Hello there");
        w.apply(&msg(pk::DIALOG, t.into_bytes()));
        assert_eq!(w.dialog.as_ref().unwrap().lines[0].0, "Hello there");
        let mut expt = MsgWriter::new();
        expt.u8(b'T').u32(0x1122_3344);
        assert_eq!(w.pending_sends.last().unwrap(), &(pk::DIALOG, expt.into_bytes()));

        // "O": two options.
        let mut o = MsgWriter::new();
        o.u8(b'O').u32(0x1122_3344).u8(3).raw(b"Yes").u8(2).raw(b"No");
        w.apply(&msg(pk::DIALOG, o.into_bytes()));
        assert_eq!(w.dialog.as_ref().unwrap().options, vec!["Yes".to_string(), "No".to_string()]);

        // "C": close.
        let mut c = MsgWriter::new();
        c.u8(b'C').u32(0x1122_3344);
        w.apply(&msg(pk::DIALOG, c.into_bytes()));
        assert!(w.dialog.is_none());
    }

    #[test]
    fn script_input_parse_and_reply() {
        let mut w = World::default();
        // P_ScriptInput: [4]scriptHandle [1]masked [2]titleLen [title][prompt].
        let mut p = MsgWriter::new();
        p.u32(0xDEAD_BEEF).u8(1).u16(11).raw(b"Enter name:").raw(b"Your hero?");
        w.apply(&msg(pk::SCRIPT_INPUT, p.into_bytes()));
        let si = w.script_input.as_ref().expect("script input opened");
        assert_eq!(si.script_handle, 0xDEAD_BEEF);
        assert!(si.masked);
        assert_eq!(si.title, "Enter name:");
        assert_eq!(si.prompt, "Your hero?");
        assert_eq!(si.text, "");
        // Reply framing: [4]scriptHandle + raw text (no length prefix).
        let reply = crate::net::script_input_reply(0xDEAD_BEEF, "Conan");
        let mut exp = 0xDEAD_BEEFu32.to_le_bytes().to_vec();
        exp.extend_from_slice(b"Conan");
        assert_eq!(reply, exp);
    }

    #[test]
    fn progress_bar_create_update_delete() {
        let mut w = World::default();
        // "C": R,G,B, X,Y,W,H f32, serverToken(4), max, value, text.
        let mut p = MsgWriter::new();
        p.u8(b'C').u8(200).u8(100).u8(50);
        p.f32(0.3).f32(0.4).f32(0.4).f32(0.05);
        p.raw(&[1, 2, 3, 4]); // server token, echoed back verbatim
        p.u16(100).u16(25).raw(b"Casting...");
        w.apply(&msg(pk::PROGRESS_BAR, p.into_bytes()));
        assert_eq!(w.progress_bars.len(), 1);
        let bar = &w.progress_bars[0];
        let handle = bar.client_handle;
        assert_eq!(handle, 1); // first handle minted
        assert_eq!((bar.max, bar.value), (100, 25));
        assert_eq!(bar.text, "Casting...");
        // Create-ack: "C" + serverToken(4) + clientHandle(4 LE).
        let mut exp = vec![b'C', 1, 2, 3, 4];
        exp.extend_from_slice(&handle.to_le_bytes());
        assert_eq!(w.pending_sends, vec![(pk::PROGRESS_BAR, exp)]);

        // "U": advance value by client handle.
        let mut u = MsgWriter::new();
        u.u8(b'U').u32(handle).u16(75);
        w.apply(&msg(pk::PROGRESS_BAR, u.into_bytes()));
        assert_eq!(w.progress_bars[0].value, 75);

        // "D": remove by client handle.
        let mut dd = MsgWriter::new();
        dd.u8(b'D').u32(handle);
        w.apply(&msg(pk::PROGRESS_BAR, dd.into_bytes()));
        assert!(w.progress_bars.is_empty());
    }

    #[test]
    fn actor_dead_kill_message() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        w.actors.insert(9, Actor { runtime_id: 9, name: "Goblin".into(), alive: true, ..Default::default() });
        w.actors.insert(8, Actor { runtime_id: 8, alive: true, ..Default::default() }); // unnamed

        // I (rid 1) killed the Goblin (rid 9): green "You killed: Goblin!"
        // (LS_YouKilled = "You killed:", matching Blitz ClientNet.bb:1091).
        let mut k = MsgWriter::new();
        k.u16(9).u16(1);
        w.apply(&msg(pk::ACTOR_DEAD, k.into_bytes()));
        assert!(!w.actors[&9].alive);
        assert_eq!(w.chat.last().unwrap().0, "You killed: Goblin!");

        // Unnamed actor killed by me → fallback name.
        let mut k2 = MsgWriter::new();
        k2.u16(8).u16(1);
        w.apply(&msg(pk::ACTOR_DEAD, k2.into_bytes()));
        assert_eq!(w.chat.last().unwrap().0, "You killed: Someone!");

        // A death I didn't cause (killer 7 ≠ me) → marked dead, no chat line.
        w.actors.insert(5, Actor { runtime_id: 5, name: "Rat".into(), alive: true, ..Default::default() });
        let before = w.chat.len();
        let mut k3 = MsgWriter::new();
        k3.u16(5).u16(7);
        w.apply(&msg(pk::ACTOR_DEAD, k3.into_bytes()));
        assert!(!w.actors[&5].alive);
        assert_eq!(w.chat.len(), before); // no message for third-party deaths

        // No killer field at all → dead, no message.
        let mut k4 = MsgWriter::new();
        k4.u16(9);
        let before2 = w.chat.len();
        w.apply(&msg(pk::ACTOR_DEAD, k4.into_bytes()));
        assert_eq!(w.chat.len(), before2);
    }

    // The XP + kill chat toasts honor a project's translated `Language.txt`
    // (the "customizable nature" goal). With no language set they use the
    // hardcoded English (covered by the other toast tests); here a translated
    // table flows through `World::lang` into the rendered strings.
    #[test]
    fn chat_toasts_honor_translated_language() {
        use rcce_data::language::ls;
        // A table where only the two LS indices we use are translated (parse
        // skips blanks, so fill 0..64 with placeholders to keep the indices).
        let mut lines: Vec<String> = (0..64).map(|i| format!("s{i}")).collect();
        lines[ls::XP_RECEIVED] = "Erfahrungspunkte erhalten!".into();
        lines[ls::YOU_KILLED] = "Du hast getoetet:".into();
        let language = rcce_data::Language::parse(&lines.join("\n"));
        assert_eq!(language.get(ls::XP_RECEIVED), Some("Erfahrungspunkte erhalten!"));

        let mut w = World { my_runtime_id: 1, language, ..Default::default() };

        // XP gain → "<N> <translated>".
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'M').i32(7); })));
        assert_eq!(w.chat.last().unwrap().0, "7 Erfahrungspunkte erhalten!");

        // Kill → "<translated> <name>!".
        w.actors.insert(9, Actor { runtime_id: 9, name: "Goblin".into(), alive: true, ..Default::default() });
        w.apply(&msg(pk::ACTOR_DEAD, pkt(|p| { p.u16(9).u16(1); })));
        assert_eq!(w.chat.last().unwrap().0, "Du hast getoetet: Goblin!");
    }

    #[test]
    fn attack_actor_subtypes() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        // 'H' I hit target 9 for 5 (raw 6): records a combat event on the target,
        // no attacker animation (the local swing is animated client-side).
        let mut h = MsgWriter::new();
        h.u8(b'H').u16(9).u16(6).u8(2);
        w.apply(&msg(pk::ATTACK_ACTOR, h.into_bytes()));
        assert_eq!(w.combat_events.last().unwrap().target, 9);
        assert_eq!(w.combat_events.last().unwrap().damage, 5);
        assert!(w.attack_anims.is_empty());

        // 'Y' actor 7 hit me: animates the attacker + a floater on me (rid 1).
        let mut y = MsgWriter::new();
        y.u8(b'Y').u16(7).u16(4).u8(0);
        w.apply(&msg(pk::ATTACK_ACTOR, y.into_bytes()));
        assert!((w.attack_anims.get(&7).copied().unwrap() - ATTACK_ANIM_SECS).abs() < 1e-6);
        assert_eq!(w.combat_events.last().unwrap().target, 1); // me
        assert_eq!(w.combat_events.last().unwrap().damage, 3);

        // Broadcast (other subtype): attacker (first rid) animates.
        let mut b = MsgWriter::new();
        b.u8(b'X').u16(12).u16(8); // attacker 12, target 8
        w.apply(&msg(pk::ATTACK_ACTOR, b.into_bytes()));
        assert!(w.attack_anims.contains_key(&12));

        // Tick expires the timers.
        w.tick_attack_anims(ATTACK_ANIM_SECS + 0.01);
        assert!(w.attack_anims.is_empty());
    }

    #[test]
    fn combat_voice_sound_intents() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        // Register an actor (rnid 50) so the death cry (which only fires for a
        // known actor) has a target.
        w.apply(&msg(
            pk::NEW_ACTOR,
            pkt(|p| {
                p.u32(0).u16(50).u16(1).u32(0).u16(3);
                p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
                p.u8(0).str8("Stag");
            }),
        ));

        // 'H' I hit target 9 → the target grunts (Hit1).
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'H').u16(9).u16(6).u8(2); })));
        // 'Y' attacker 7 hit me → attacker swings (Attack1).
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'Y').u16(7).u16(4).u8(0); })));
        // Broadcast: attacker 12 → swings (Attack1).
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'X').u16(12).u16(8); })));
        assert_eq!(
            w.pending_combat_sounds,
            vec![(9, SPEECH_HIT1), (7, SPEECH_ATTACK1), (12, SPEECH_ATTACK1)]
        );

        // Death of the known actor 50 → death cry; an unknown rid queues nothing.
        w.apply(&msg(pk::ACTOR_DEAD, pkt(|p| { p.u16(50); })));
        w.apply(&msg(pk::ACTOR_DEAD, pkt(|p| { p.u16(999); })));
        assert_eq!(w.pending_combat_sounds.last(), Some(&(50, SPEECH_DEATH)));
        assert_eq!(w.pending_combat_sounds.iter().filter(|(r, _)| *r == 999).count(), 0);
    }

    // The 'H' target Hit (pain) cry is gated on actual damage: a parried /
    // 0-damage swing (raw_dmg = 1 → damage 0) still records the floater but
    // queues NO pain cry. Matches Blitz ClientNet.bb:1131 (the cry is inside
    // `If Damage > 0`). The damaging case is covered by combat_voice_sound_intents.
    #[test]
    fn hit_cry_gated_on_damage() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        // 'H' I hit target 9 for 0 damage (raw 1): floater recorded, NO Hit cry.
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'H').u16(9).u16(1).u8(0); })));
        assert_eq!(w.combat_events.last().unwrap().target, 9);
        assert_eq!(w.combat_events.last().unwrap().damage, 0);
        assert!(w.pending_combat_sounds.is_empty(), "no pain cry on a 0-damage hit");

        // A damaging hit (raw 6 → damage 5) DOES queue the Hit cry.
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'H').u16(9).u16(6).u8(0); })));
        assert_eq!(w.pending_combat_sounds, vec![(9, SPEECH_HIT1)]);
    }

    // 'Y' (an attacker swung at me): Blitz fires the attacker's swing animation,
    // its Attack grunt, and the floater on Damage != 0 (a hit OR a miss), but
    // NOT on the exactly-0-damage sentinel (raw_dmg 1 → Damage 0). ClientNet.bb
    // :1161-1182 (no `Else`).
    #[test]
    fn y_attacker_swing_gated_on_zero_sentinel() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        // raw 1 → Damage 0 sentinel: nothing happens.
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'Y').u16(7).u16(1).u8(0); })));
        assert!(w.attack_anims.is_empty(), "no swing on the 0-damage sentinel");
        assert!(w.pending_combat_sounds.is_empty(), "no grunt on the 0-damage sentinel");
        assert!(w.combat_events.is_empty(), "no floater on the 0-damage sentinel");

        // raw 0 → Blitz Damage -1 (a MISS): attacker swings + grunts + a "0" floater.
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'Y').u16(7).u16(0).u8(0); })));
        assert!(w.attack_anims.contains_key(&7), "miss still shows the swing");
        assert_eq!(w.pending_combat_sounds, vec![(7, SPEECH_ATTACK1)]);
        assert_eq!(w.combat_events.last().unwrap().damage, 0);

        // raw 6 → Damage 5 (a landed hit): swing + grunt + real damage floater.
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'Y').u16(8).u16(6).u8(0); })));
        assert!(w.attack_anims.contains_key(&8));
        assert_eq!(w.combat_events.last().unwrap().damage, 5);
    }

    // The LOCAL player's own combat voice. `Me` isn't in the `actors` map, so
    // `pending_combat_sounds` can never carry Me — these go through the separate
    // `pending_self_sounds` queue, which the App resolves against `me_actor_id` +
    // `me_gender`. Two cases, matching the Blitz If/Else exactly:
    //   'H' (I land a swing on a target): I grunt (Attack) UNCONDITIONALLY — even
    //        a 0-damage / parried swing (Blitz `PlayActorSound(Me, Speech_Attack)`
    //        at the top of the 'H' branch, ClientNet.bb:1122).
    //   'Y' (an attacker swings at me): I cry out (Hit) ONLY on a connecting blow
    //        (Damage > 0), not on a miss or the 0-damage sentinel (Blitz cry is
    //        inside `If Damage > 0`, ClientNet.bb:1166).
    #[test]
    fn local_player_combat_voice() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };

        // 'H' raw 1 (0 damage / parried): I STILL grunt (Attack), but the target
        // takes no pain cry (combat_sounds stays empty — gated on damage).
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'H').u16(9).u16(1).u8(0); })));
        assert_eq!(w.pending_self_sounds, vec![SPEECH_ATTACK1], "I grunt on every swing I land");
        assert!(w.pending_combat_sounds.is_empty(), "no target pain cry on a 0-damage hit");

        // 'H' raw 6 (damage 5): a second Attack grunt for me; target gets its cry.
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'H').u16(9).u16(6).u8(0); })));
        assert_eq!(w.pending_self_sounds, vec![SPEECH_ATTACK1, SPEECH_ATTACK1]);
        assert_eq!(w.pending_combat_sounds, vec![(9, SPEECH_HIT1)]);

        // Fresh world for the receiving side ('Y').
        let mut w = World { my_runtime_id: 1, ..Default::default() };

        // 'Y' raw 1 (0-damage sentinel): nothing at all — no self Hit cry.
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'Y').u16(7).u16(1).u8(0); })));
        assert!(w.pending_self_sounds.is_empty(), "no Hit cry on the 0-damage sentinel");

        // 'Y' raw 0 (a MISS, Blitz Damage -1): attacker swings/grunts but I do
        // NOT cry out — the pain cry is gated on Damage > 0.
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'Y').u16(7).u16(0).u8(0); })));
        assert!(w.pending_self_sounds.is_empty(), "no Hit cry on a miss");
        assert_eq!(w.pending_combat_sounds, vec![(7, SPEECH_ATTACK1)], "attacker still grunts");

        // 'Y' raw 6 (damage 5, a landed blow): NOW I cry out (Hit).
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'Y').u16(8).u16(6).u8(0); })));
        assert_eq!(w.pending_self_sounds, vec![SPEECH_HIT1], "I cry out only on a connecting blow");
    }

    // P_FloatingNumber (script-driven, BVM_CreateFloatingNumber): RuntimeID u16,
    // signed Amount i32, then R/G/B u8. Queues a (rid, amount, [r,g,b]) intent for
    // the App to anchor + animate. A short/garbled packet queues nothing.
    #[test]
    fn floating_number_queues_intent() {
        let mut w = World::default();
        // A green heal "+250" over actor 7.
        w.apply(&msg(pk::FLOATING_NUMBER, pkt(|p| { p.u16(7).i32(250).u8(0).u8(255).u8(0); })));
        assert_eq!(w.pending_floaters, vec![(7, 250, [0, 255, 0])]);

        // A negative amount (signed i32 round-trips) in red over actor 12.
        w.apply(&msg(pk::FLOATING_NUMBER, pkt(|p| { p.u16(12).i32(-40).u8(255).u8(0).u8(0); })));
        assert_eq!(w.pending_floaters.last(), Some(&(12, -40, [255, 0, 0])));

        // A truncated packet (missing the colour bytes) queues nothing more.
        w.apply(&msg(pk::FLOATING_NUMBER, pkt(|p| { p.u16(9).i32(5); })));
        assert_eq!(w.pending_floaters.len(), 2, "truncated packet soft-fails");
    }

    // on_select_scenery (C2): RCCE's server never sends P_SelectScenery (its inbound
    // case is commented out, ServerNet.bb:741) and the local mesh animation is
    // deferred, so the handler's whole job is to accept the packet without crashing.
    // Verify it soft-fails on a full, an empty, and a truncated payload.
    #[test]
    fn select_scenery_inbound_soft_fails() {
        let mut w = World::default();
        w.apply(&msg(pk::SELECT_SCENERY, pkt(|p| { p.u32(0x0A0B_0C0D); }))); // full 4-byte handle
        w.apply(&msg(pk::SELECT_SCENERY, pkt(|_p| {}))); // empty payload
        w.apply(&msg(pk::SELECT_SCENERY, pkt(|p| { p.u8(1); }))); // truncated (<4 bytes)
        // No panic AND no observable side effect: the handler only discards a
        // bounds-checked read, so it must not touch actors or emit chat.
        assert!(w.actors.is_empty(), "select-scenery must not touch actors");
        assert!(w.chat.is_empty(), "select-scenery must not emit chat");
    }

    // Looting a dropped item ('R') moves it into inventory AND queues a
    // (item_id, amount) pickup-toast intent for the App to resolve into the
    // green "Picked up item: <name> (xN)" chat line — Blitz parity
    // (ClientNet.bb:1349). An 'R' for an unknown handle queues nothing.
    #[test]
    fn pickup_queues_toast_intent() {
        let mut w = World::default();
        // 'D': a stack of 3 of item 77 drops in the world at handle 9 (header
        // amount/x/y/z/handle, then the 83-byte ItemInstance: id u16 + 80 + health).
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| {
            p.u8(b'D').u16(3).f32(1.0).f32(2.0).f32(3.0).u32(9);
            p.u16(77);
            for _ in 0..80 {
                p.u8(0);
            }
            p.u8(100);
        })));
        assert!(w.dropped_items.contains_key(&9));

        // 'R': I pick it up into backpack slot 14.
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| { p.u8(b'R').u32(9).u8(14); })));
        assert!(!w.dropped_items.contains_key(&9), "removed from the world on pickup");
        assert_eq!(w.pending_pickup_toasts, vec![(77, 3)], "queued (item_id, amount)");

        // An 'R' for a handle that isn't in the world queues no toast.
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| { p.u8(b'R').u32(999).u8(0); })));
        assert_eq!(w.pending_pickup_toasts.len(), 1, "no toast for an unknown handle");
    }

    // Our own P_NewActor (rid == my_runtime_id) carries our actor template
    // (race); capture it into `me_actor_id` so the in-world body uses the right
    // race mesh. Previously the template was dropped and the body fell back to 0.
    #[test]
    fn own_new_actor_captures_race_template() {
        let mut w = World { my_runtime_id: 42, ..Default::default() };
        assert_eq!(w.me_actor_id, 0, "defaults to 0 before our spawn packet");
        // P_NewActor for ME (rid 42), template 7.
        w.apply(&msg(pk::NEW_ACTOR, pkt(|p| {
            p.u32(0).u16(42).u16(1).u32(0).u16(7); // area rid level xp template
            p.f32(1.0).f32(2.0).f32(3.0).f32(0.5); // x y z yaw
            p.u8(1).str8("MyChar"); // isPlayer name
        })));
        assert_eq!(w.me_actor_id, 7, "captured our race template from our own P_NewActor");
        // Another actor's P_NewActor must NOT overwrite our template.
        w.apply(&msg(pk::NEW_ACTOR, pkt(|p| {
            p.u32(0).u16(50).u16(1).u32(0).u16(3);
            p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
            p.u8(0).str8("Stag");
        })));
        assert_eq!(w.me_actor_id, 7, "an NPC's template doesn't change ours");
        assert_eq!(w.actors[&50].template_id, 3, "the NPC still records its own");
    }

    #[test]
    fn sound_speech_music_dispatch() {
        let mut w = World::default();
        // P_Sound: [2]soundID (+ optional rid, ignored for 2D alpha playback).
        let mut s = MsgWriter::new();
        s.u16(42);
        w.apply(&msg(pk::SOUND, s.into_bytes()));
        assert_eq!(w.pending_sounds, vec![42]);

        // P_Speech: [2]soundID [2]runtimeID → queues the sound (rid parsed, unused).
        let mut sp = MsgWriter::new();
        sp.u16(99).u16(7);
        w.apply(&msg(pk::SPEECH, sp.into_bytes()));
        assert_eq!(w.pending_sounds, vec![42, 99]);

        // P_Music: [2]musicID → pending switch.
        let mut mu = MsgWriter::new();
        mu.u16(5);
        w.apply(&msg(pk::MUSIC, mu.into_bytes()));
        assert_eq!(w.pending_music, Some(5));
        // A later P_Music supersedes the pending one.
        let mut mu2 = MsgWriter::new();
        mu2.u16(8);
        w.apply(&msg(pk::MUSIC, mu2.into_bytes()));
        assert_eq!(w.pending_music, Some(8));
    }

    #[test]
    fn projectile_spawn_move_impact() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        w.actors.insert(
            2,
            Actor { runtime_id: 2, x: 10.0, alive: true, ..Default::default() },
        );
        // P_Projectile: src=1(me) tgt=2 mesh/tex=0 homing=0 speed=50 emit1len=0.
        let mut p = MsgWriter::new();
        p.u16(1).u16(2).u16(0).u16(0).u16(0).u8(0).u8(50).u8(0);
        w.apply(&msg(pk::PROJECTILE, p.into_bytes()));
        assert_eq!(w.projectiles.len(), 1);
        assert!(w.projectiles[0].x.abs() < 0.01); // spawned at me (x=0)
        // speed = 50/50·2·30 = 60 u/s; 0.1s → ~6 units toward x=10.
        w.tick_projectiles(0.1);
        let x = w.projectiles[0].x;
        assert!((5.0..7.0).contains(&x), "moved to {x}");
        // Keep ticking until it impacts (within 2 of x=10) and is removed.
        for _ in 0..10 {
            w.tick_projectiles(0.1);
        }
        assert!(w.projectiles.is_empty(), "projectile impacted + removed");
    }

    #[test]
    fn screen_flash_parse() {
        let mut w = World::default();
        // R=255 G=0 B=0 alpha=128 length=2000ms tex=65535.
        let mut p = MsgWriter::new();
        p.u8(255).u8(0).u8(0).u8(128).u32(2000).u16(65535);
        w.apply(&msg(pk::SCREEN_FLASH, p.into_bytes()));
        let f = w.flash.expect("flash set");
        assert_eq!(f.color, [1.0, 0.0, 0.0]);
        assert!((f.alpha - 128.0 / 255.0).abs() < 1e-4);
        assert!((f.length - 2.0).abs() < 1e-4);
    }

    #[test]
    fn party_update_names() {
        let mut w = World::default();
        let mut p = MsgWriter::new();
        // 7 slots: "Alice", "Bob", then 5 empty (len 0).
        p.u8(5).raw(b"Alice").u8(3).raw(b"Bob");
        for _ in 0..5 {
            p.u8(0);
        }
        w.apply(&msg(pk::PARTY_UPDATE, p.into_bytes()));
        assert_eq!(w.party, vec!["Alice".to_string(), "Bob".to_string()]);
    }

    #[test]
    fn quest_log_add_update_delete() {
        let mut w = World::default();
        // "N" Find the Sword: status RGB(255,255,0) + "In progress".
        let mut status = vec![255u8, 255, 0];
        status.extend_from_slice(b"In progress");
        let mut p = MsgWriter::new();
        p.u8(b'N').u8(14).raw(b"Find the Sword").u16(status.len() as u16).raw(&status);
        w.apply(&msg(pk::QUEST_LOG, p.into_bytes()));
        assert_eq!(w.quests.len(), 1);
        assert_eq!(w.quests[0].name, "Find the Sword");
        assert_eq!(w.quests[0].status, "In progress");
        assert_eq!(w.quests[0].color, [1.0, 1.0, 0.0, 1.0]);
        assert!(!w.quests[0].completed);
        // "U" mark completed: RGB(0,255,0) + 254 marker + "Done".
        let mut st2 = vec![0u8, 255, 0, 254];
        st2.extend_from_slice(b"Done");
        let mut u = MsgWriter::new();
        u.u8(b'U').u8(14).raw(b"Find the Sword").u16(st2.len() as u16).raw(&st2);
        w.apply(&msg(pk::QUEST_LOG, u.into_bytes()));
        assert!(w.quests[0].completed && w.quests[0].status == "Done");
        // "D" delete (case-insensitive).
        let mut del = MsgWriter::new();
        del.u8(b'D').raw(b"FIND THE SWORD");
        w.apply(&msg(pk::QUEST_LOG, del.into_bytes()));
        assert!(w.quests.is_empty());
    }

    #[test]
    fn bubble_message_parse() {
        let mut w = World::default();
        // rid=9, RGB=(0,255,0), text="Hello!"
        let mut p = MsgWriter::new();
        p.u16(9).u8(0).u8(255).u8(0).raw(b"Hello!");
        w.apply(&msg(pk::BUBBLE_MESSAGE, p.into_bytes()));
        assert_eq!(w.pending_bubbles.len(), 1);
        let (rid, text, col) = &w.pending_bubbles[0];
        assert_eq!(*rid, 9);
        assert_eq!(text, "Hello!");
        assert_eq!(*col, [0.0, 1.0, 0.0, 1.0]);
    }

    // P_Projectile carries the projectile's emitter config names + tex ids after
    // the fixed header; we capture Emitter2 ("Fireball") + TexID2 so the App can
    // render the distinctive particle effect over the plain glow.
    #[test]
    fn projectile_captures_emitter() {
        let mut w = World::default();
        w.actors.insert(2, Actor { runtime_id: 2, alive: true, ..Default::default() });
        w.actors.insert(3, Actor { runtime_id: 3, x: 10.0, alive: true, ..Default::default() });
        // src=2 tgt=3 mesh=65535 tex1=0 tex2=7 homing=0 spd=65, Emitter1="Default", Emitter2="Fireball".
        let mut p = MsgWriter::new();
        p.u16(2).u16(3).u16(65535).u16(0).u16(7).u8(0).u8(65).str8("Default").raw(b"Fireball");
        w.apply(&msg(pk::PROJECTILE, p.into_bytes()));
        assert_eq!(w.projectiles.len(), 1);
        assert_eq!(w.projectiles[0].emitter, "Fireball");
        assert_eq!(w.projectiles[0].emitter_tex, 7);
        // Emitter1$ ("Default") + TexID1 (0) are also captured (the base trail).
        assert_eq!(w.projectiles[0].emitter1, "Default");
        assert_eq!(w.projectiles[0].emitter1_tex, 0);
        // Each spawn gets a distinct id.
        w.apply(&msg(pk::PROJECTILE, { let mut q = MsgWriter::new(); q.u16(2).u16(3).u16(65535).u16(0).u16(0).u8(0).u8(65).str8("").raw(b""); q.into_bytes() }));
        assert_ne!(w.projectiles[0].id, w.projectiles[1].id);
        assert_eq!(w.projectiles[1].emitter, "", "no emitter name → plain glow");
    }

    // SPL-8: the local player's own cast. Blitz has NO local spawn-on-cast — all
    // three client cast sites (Interface3D.bb:1128/:1258/:1543) only send
    // P_SpellUpdate "F"; the spell script's FireProjectile then broadcasts
    // P_Projectile to every player in the zone INCLUDING the caster
    // (GameServer.bb:257-266), and the client handler spawns for any source, Me
    // included (ClientNet.bb:217-238). This test walks that echo round-trip: the
    // caster's own P_Projectile (src == my rid) must spawn from the DISPLAYED
    // body position (Blitz sources it at EntityX(Source\CollisionEN),
    // Projectiles3D.bb:59 — not the lagged server echo), carry the spell's
    // emitter/texture template, home on the target, and reuse the PRJ-1
    // flight/impact machinery unchanged.
    #[test]
    fn own_cast_projectile_spawns_at_visible_body() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        // Server echo (me_x) lags the displayed body (me_render_x) while moving.
        w.me_x = 5.0;
        w.me_y = 2.0;
        w.me_z = 0.0;
        w.me_render_x = 6.5;
        w.me_render_z = 0.5;
        w.me_render_init = true;
        w.actors.insert(2, Actor { runtime_id: 2, x: 30.0, alive: true, ..Default::default() });
        // The outbound cast itself ("F" + [2]spellID + [2]targetRID,
        // Interface3D.bb:1128) is pinned by net::tests::cast_packet_*; here the
        // server has run the spell script and echoes the projectile back to us.
        // Echo field order per GameServer.bb:257-261: [2]src [2]tgt [2]mesh
        // [2]tex1 [2]tex2 [1]homing [1]speed [1]len+Emitter1 Emitter2.
        let mut p = MsgWriter::new();
        p.u16(1).u16(2).u16(65535).u16(3).u16(7).u8(1).u8(50).str8("Default").raw(b"Fireball");
        w.apply(&msg(pk::PROJECTILE, p.into_bytes()));
        assert_eq!(w.projectiles.len(), 1, "own-cast echo spawns a projectile");
        let pr = &w.projectiles[0];
        assert!((pr.x - 6.5).abs() < 1e-4 && (pr.z - 0.5).abs() < 1e-4, "spawns at the displayed body, not the echo (got {},{})", pr.x, pr.z);
        assert!((pr.y - 5.0).abs() < 1e-4, "spawn y = me_y + 3 (Blitz spawn-height offset)");
        assert!(pr.homing && pr.target_rid == 2, "homing on the cast target");
        assert_eq!((pr.emitter1.as_str(), pr.emitter1_tex), ("Default", 3));
        assert_eq!((pr.emitter.as_str(), pr.emitter_tex), ("Fireball", 7));
        // Same PRJ-1 machinery flies + impacts it (no own-cast special case).
        for _ in 0..20 {
            w.tick_projectiles(0.1);
        }
        assert!(w.projectiles.is_empty(), "own-cast projectile impacted + removed");
    }

    // SPL-8 soft-fail tier: a malformed own-cast echo must spawn nothing and
    // must not panic. Mirrors Blitz's `If TargetAI <> Null And AI <> Null`
    // (ClientNet.bb:225) and the workspace no-panic-on-wire-data invariant.
    #[test]
    fn own_cast_projectile_soft_fails() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        w.actors.insert(2, Actor { runtime_id: 2, x: 30.0, alive: true, ..Default::default() });
        // Truncated: header cut mid-field (missing speed byte onward).
        let mut short = MsgWriter::new();
        short.u16(1).u16(2).u16(0).u16(0).u16(0).u8(1);
        w.apply(&msg(pk::PROJECTILE, short.into_bytes()));
        assert!(w.projectiles.is_empty(), "truncated echo spawns nothing");
        // Unknown target rid: the caster resolves (me) but the target doesn't.
        let mut ghost = MsgWriter::new();
        ghost.u16(1).u16(999).u16(0).u16(0).u16(0).u8(0).u8(50).u8(0);
        w.apply(&msg(pk::PROJECTILE, ghost.into_bytes()));
        assert!(w.projectiles.is_empty(), "unknown target rid spawns nothing");
    }

    // A homing projectile targeting ME re-acquires the displayed body each tick
    // (same displayed-body convention as the own-cast spawn; Blitz tracks the
    // live CollisionEN entity, Projectiles3D.bb:79-81).
    #[test]
    fn homing_projectile_tracks_my_displayed_body() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        w.actors.insert(2, Actor { runtime_id: 2, x: 100.0, alive: true, ..Default::default() });
        w.me_render_init = true;
        let mut p = MsgWriter::new();
        p.u16(2).u16(1).u16(0).u16(0).u16(0).u8(1).u8(50).u8(0);
        w.apply(&msg(pk::PROJECTILE, p.into_bytes()));
        assert_eq!(w.projectiles.len(), 1);
        // The body glides to a new displayed position; the homing tick follows it.
        w.me_render_x = 40.0;
        w.me_render_z = 8.0;
        w.tick_projectiles(0.01);
        let pr = &w.projectiles[0];
        assert!((pr.tx - 40.0).abs() < 1e-4 && (pr.tz - 8.0).abs() < 1e-4, "re-acquired the displayed body (got {},{})", pr.tx, pr.tz);
    }

    // A plain say "<Name> text" (no colour-code prefix) becomes a speech bubble over
    // the speaker when the project enables bubbles — over Me in blue, others in the
    // project colour. Colour-prefixed messages, unknown names, and disabled bubbles
    // produce none. The chat log line always appears.
    #[test]
    fn plain_say_makes_a_bubble() {
        let mut w = World {
            my_runtime_id: 1,
            bubbles_enabled: true,
            bubble_color: [0.5, 0.5, 0.5, 1.0],
            me_name: "Hero".into(),
            ..Default::default()
        };
        w.actors.insert(7, Actor { runtime_id: 7, name: "Stag".into(), alive: true, ..Default::default() });

        // Another player's say → bubble over actor 7 in the project colour, + log line.
        w.on_chat(b"<Stag> hello there");
        assert_eq!(w.pending_bubbles.last(), Some(&(7u16, "hello there".to_string(), [0.5, 0.5, 0.5, 1.0])));
        assert!(w.chat.iter().any(|(t, _)| t.contains("hello there")), "still logged");

        // My own say → bubble over me (rid 1) in blue.
        w.on_chat(b"<Hero> hi");
        assert_eq!(w.pending_bubbles.last(), Some(&(1u16, "hi".to_string(), [0.0, 0.5, 1.0, 1.0])));

        let n = w.pending_bubbles.len();
        // Unknown speaker → no bubble.
        w.on_chat(b"<Ghost> boo");
        // A colour-prefixed yell (253) is NOT bubbled even with a valid "<Stag>".
        let mut yell = vec![253u8];
        yell.extend_from_slice(b"<Stag> YELL");
        w.on_chat(&yell);
        assert_eq!(w.pending_bubbles.len(), n, "unknown name + colour-prefixed → no bubble");

        // Disabled → no bubble.
        w.bubbles_enabled = false;
        w.on_chat(b"<Stag> quiet");
        assert_eq!(w.pending_bubbles.len(), n, "disabled → no bubble");
    }

    #[test]
    fn known_spell_add_remove_level() {
        let mut w = World::default();
        // "A" Heal (level 2, id 7): name as str16 (u16 len + bytes), empty desc, mem 0.
        let mut p = MsgWriter::new();
        p.u8(b'A').u16(2).u16(7).u16(0).u16(500).u16(4).raw(b"Heal").u16(0).u8(0);
        w.apply(&msg(pk::KNOWN_SPELL_UPDATE, p.into_bytes()));
        // "A" Fireball (level 1, id 5).
        let mut p2 = MsgWriter::new();
        p2.u8(b'A').u16(1).u16(5).u16(0).u16(1000).u16(8).raw(b"Fireball").u16(0).u8(0);
        w.apply(&msg(pk::KNOWN_SPELL_UPDATE, p2.into_bytes()));
        // Sorted by name → Fireball, Heal.
        assert_eq!(
            w.known_spells.iter().map(|s| s.name.as_str()).collect::<Vec<_>>(),
            ["Fireball", "Heal"]
        );
        // known_index reflects ADD order (Heal first = 0, Fireball second = 1), NOT
        // the sorted display position — so memorise sends the right server index.
        assert_eq!(w.known_spells.iter().find(|s| s.name == "Heal").unwrap().known_index, 0);
        assert_eq!(w.known_spells.iter().find(|s| s.name == "Fireball").unwrap().known_index, 1);
        // "L" Fireball → level 3.
        let mut l = MsgWriter::new();
        l.u8(b'L').u32(3).raw(b"FIREBALL");
        w.apply(&msg(pk::KNOWN_SPELL_UPDATE, l.into_bytes()));
        assert_eq!(w.known_spells.iter().find(|s| s.name == "Fireball").unwrap().level, 3);
        // "D" remove Heal.
        let mut del = MsgWriter::new();
        del.u8(b'D').raw(b"HEAL");
        w.apply(&msg(pk::KNOWN_SPELL_UPDATE, del.into_bytes()));
        assert_eq!(w.known_spells.len(), 1);
        assert_eq!(w.known_spells[0].name, "Fireball");
    }

    #[test]
    fn chat_colour_sentinels() {
        let mut w = World::default();
        w.apply(&msg(pk::CHAT_MESSAGE, vec![254, b'h', b'i'])); // yellow
        w.apply(&msg(pk::CHAT_MESSAGE, vec![253, b'r', b'e', b'd'])); // red
        w.apply(&msg(pk::CHAT_MESSAGE, vec![250, 10, 20, 30, b'x'])); // explicit RGB
        w.apply(&msg(pk::CHAT_MESSAGE, b"plain".to_vec())); // white (no sentinel)
        w.apply(&msg(pk::CHAT_MESSAGE, b"<<Me>> hi".to_vec())); // own line -> blue
        assert_eq!(w.chat.len(), 5);
        assert_eq!(w.chat[0], ("hi".to_string(), [1.0, 1.0, 0.0, 1.0]));
        assert_eq!(w.chat[1].1, [1.0, 0.2, 0.2, 1.0]);
        assert_eq!(w.chat[2], ("x".to_string(), [10.0 / 255.0, 20.0 / 255.0, 30.0 / 255.0, 1.0]));
        assert_eq!(w.chat[3].0, "plain");
        assert!(w.chat[4].0.starts_with("<<Me>>") && w.chat[4].1 == [0.0, 0.5, 1.0, 1.0]);
    }

    #[test]
    fn change_area_then_new_actor_then_update() {
        let mut w = World {
            my_runtime_id: 1792,
            ..Default::default()
        };

        // P_ChangeArea
        let mut p = MsgWriter::new();
        p.f32(10.0).f32(0.0).f32(20.0).f32(1.5); // x y z yaw
        p.u8(0).u16(200).u32(7).u8(0).str8("Plains"); // pvp grav areaid weather name
        w.apply(&msg(pk::CHANGE_AREA, p.into_bytes()));
        assert_eq!(w.zone.name, "Plains");
        assert_eq!(w.zone.area_id, 7);
        assert!((w.me_x - 10.0).abs() < 0.01);

        // P_NewActor (an NPC, runtime id 50)
        let mut p = MsgWriter::new();
        p.u32(7).u16(50).u16(1).u32(0).u16(3); // area rid level xp tmpl
        p.f32(15.0).f32(0.0).f32(25.0).f32(0.0); // x y z yaw
        p.u8(0).str8("Stag"); // isPlayer name
        w.apply(&msg(pk::NEW_ACTOR, p.into_bytes()));
        assert_eq!(w.actors.len(), 1);
        assert_eq!(w.actors[&50].name, "Stag");
        assert!(!w.actors[&50].is_player);

        // P_StandardUpdate moves the NPC
        let mut p = MsgWriter::new();
        p.u16(50).f32(16.0).f32(26.0).u8(1).u8(0).f32(18.0).f32(28.0).u16(0);
        w.apply(&msg(pk::STANDARD_UPDATE, p.into_bytes()));
        assert!((w.actors[&50].x - 16.0).abs() < 0.01);
        assert!(w.actors[&50].is_running);

        // P_ActorGone removes it
        let mut p = MsgWriter::new();
        p.u16(50);
        w.apply(&msg(pk::ACTOR_GONE, p.into_bytes()));
        assert!(w.actors.is_empty());
    }

    // A zone change must close every interaction window tied to an old-zone
    // entity (trade / dialog / script prompt). You can't trade with / talk to /
    // answer a prompt from an NPC or partner you just warped away from, so a
    // lingering panel is stale. Reviewer-flagged for trades (#477/#479); dialog
    // + script_input are the same class.
    #[test]
    fn change_area_closes_stale_interaction_windows() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        w.current_trade = Some(crate::trade::TradeWindow {
            kind: crate::trade::TradeKind::Player,
            offers: vec![],
        });
        w.player_trade = Some(crate::trade::PlayerTrade::default());
        w.dialog = Some(Dialog::default());
        w.script_input = Some(ScriptInput::default());

        let mut p = MsgWriter::new();
        p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
        p.u8(0).u16(200).u32(9).u8(0).str8("NewZone");
        w.apply(&msg(pk::CHANGE_AREA, p.into_bytes()));

        assert_eq!(w.zone.name, "NewZone", "zone changed");
        assert!(w.current_trade.is_none(), "vendor/trade window closed on warp");
        assert!(w.player_trade.is_none(), "player trade closed on warp");
        assert!(w.dialog.is_none(), "NPC dialog closed on warp");
        assert!(w.script_input.is_none(), "script prompt closed on warp");
    }

    /// Warp-completion acks: applying `P_ChangeArea` queues a `P_ChangeArea`
    /// echo, and applying a `P_RepositionActor` addressed to ME queues a
    /// `P_RepositionActor` echo — the sends Blitz has commented out at
    /// `ClientNet.bb:1779`, which clear the server's `IgnoreUpdate` warp
    /// suppression (`ServerNet.bb:727-737`). A bystander's reposition must NOT
    /// be acked (only our own suppression window can be armed).
    #[test]
    fn change_area_and_own_reposition_queue_warp_acks() {
        let mut w = World { my_runtime_id: 42, ..Default::default() };

        let mut p = MsgWriter::new();
        p.f32(1.0).f32(2.0).f32(3.0).f32(0.0);
        p.u8(0).u16(200).u32(5).u8(0).str8("Plains");
        let ca = p.into_bytes();
        w.apply(&msg(pk::CHANGE_AREA, ca.clone()));
        assert_eq!(w.pending_sends, vec![(pk::CHANGE_AREA, ca)], "zone change acked with the payload echo");
        w.pending_sends.clear();

        // Reposition addressed to me → acked.
        let me_move = pkt(|p| { p.u8(b'M').u16(42).f32(9.0).f32(1.0).f32(8.0).u8(0); });
        w.apply(&msg(pk::REPOSITION_ACTOR, me_move.clone()));
        assert_eq!(w.pending_sends, vec![(pk::REPOSITION_ACTOR, me_move)], "own reposition acked");
        w.pending_sends.clear();

        // Bystander reposition → applied silently, no ack.
        let other = pkt(|p| { p.u8(b'M').u16(7).f32(1.0).f32(1.0).f32(1.0).u8(0); });
        w.apply(&msg(pk::REPOSITION_ACTOR, other));
        assert!(w.pending_sends.is_empty(), "no ack for another actor's reposition");
    }

    /// Build a payload (the builders return `&mut Self`, so chaining
    /// `.into_bytes()` doesn't work — wrap in an owned writer).
    fn pkt(build: impl FnOnce(&mut MsgWriter)) -> Vec<u8> {
        let mut w = MsgWriter::new();
        build(&mut w);
        w.into_bytes()
    }

    #[test]
    fn xp_gold_stat_dead() {
        let mut w = World {
            my_runtime_id: 7,
            ..Default::default()
        };
        // Register an NPC (rnid 50).
        w.apply(&msg(
            pk::NEW_ACTOR,
            pkt(|p| {
                p.u32(0).u16(50).u16(1).u32(0).u16(3);
                p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
                p.u8(0).str8("Stag");
            }),
        ));
        assert!(w.actors[&50].alive);

        // XP: 'B' bar level, then 'M' xp gain.
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'B').u8(4); })));
        assert_eq!(w.me_xp_bar, 4);
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'M').i32(150); })));
        assert_eq!(w.me_xp, 150);
        // 'M' also pushes the Blitz "experience points received!" toast.
        assert!(w.chat.iter().any(|(t, _)| t == "150 experience points received!"));

        // Gold: increase then decrease, clamped at 0.
        w.apply(&msg(pk::GOLD_CHANGE, pkt(|p| { p.u8(b'I').i32(100); })));
        assert_eq!(w.me_gold, 100);
        w.apply(&msg(pk::GOLD_CHANGE, pkt(|p| { p.u8(b'D').i32(250); })));
        assert_eq!(w.me_gold, 0);

        // StatUpdate: NPC health value + max ('A'/'M', attr index 5).
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'A').u16(50).u8(5).u16(80); })));
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'M').u16(50).u8(5).u16(100); })));
        assert_eq!(w.actors[&50].attributes[&5], (80, 100));
        // StatUpdate for self goes to me_attributes.
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'A').u16(7).u8(5).u16(42); })));
        assert_eq!(w.me_attributes[&5], (42, 0));

        // Health is attr 0 → mirrored onto actor.health / me_health for the bars.
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'M').u16(50).u8(0).u16(120); })));
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'A').u16(50).u8(0).u16(75); })));
        assert_eq!((w.actors[&50].health, w.actors[&50].health_max), (75, 120));
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'A').u16(7).u8(0).u16(33); })));
        assert_eq!(w.me_health, 33);

        // ActorDead marks the NPC dead.
        w.apply(&msg(pk::ACTOR_DEAD, pkt(|p| { p.u16(50); })));
        assert!(!w.actors[&50].alive);
    }

    #[test]
    fn xp_update_my_level_change_resets_xp() {
        let mut w = World { my_runtime_id: 7, ..Default::default() };
        // Accumulate some XP first.
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'M').i32(150); })));
        assert_eq!(w.me_xp, 150);

        // 'U' = MY level changed → level updates and XP resets to 0. The server
        // sends the level as a 2-byte field (ScriptingCommands.bb:2085); reading
        // it as i32 would fail the parse and silently never update the level.
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'U').u16(5); })));
        assert_eq!(w.me_level, 5, "'U' sets my level");
        assert_eq!(w.me_xp, 0, "'U' resets XP to 0 (matches Me\\XP = 0)");

        // A truncated 'U' (no level bytes) is a safe no-op (soft-fail, no panic).
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'U'); })));
        assert_eq!(w.me_level, 5, "truncated 'U' leaves the level unchanged");
    }

    // The 'M' XP toast (Blitz ClientNet.bb:698) only fires for a real positive
    // gain — a 0 or negative gain (the malformed-input fuzzer feeds these) still
    // updates me_xp but must not push a confusing "0 / -N ... received!" line.
    #[test]
    fn xp_received_toast_only_on_positive_gain() {
        let mut w = World { my_runtime_id: 7, ..Default::default() };
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'M').i32(42); })));
        assert_eq!(w.me_xp, 42);
        assert_eq!(
            w.chat.iter().filter(|(t, _)| t.contains("experience points received")).count(),
            1,
            "a positive gain pushes exactly one toast"
        );
        // Warm gold (255,225,100), matching Blitz Output(...,255,225,100).
        let (_, col) = w.chat.iter().find(|(t, _)| t.contains("experience")).unwrap();
        assert_eq!(*col, [1.0, 0.882, 0.392, 1.0]);

        // Zero and negative gains update xp but push NO toast.
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'M').i32(0); })));
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'M').i32(-10); })));
        assert_eq!(w.me_xp, 32, "xp still tracks the raw gain (42 + 0 - 10)");
        assert_eq!(
            w.chat.iter().filter(|(t, _)| t.contains("experience points received")).count(),
            1,
            "no extra toast for the 0 / negative gains"
        );
    }

    #[test]
    fn stat_update_reputation_is_signed_and_self_only() {
        let mut w = World { my_runtime_id: 7, ..Default::default() };
        // 'R' = reputation: rid(u16) + reputation(i16 signed), NO attr byte.
        // Negative reputation must sign-extend (RCE_SignedShortFromStr).
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'R').u16(7).u16((-30i16) as u16); })));
        assert_eq!(w.me_reputation, -30, "negative reputation sign-extends");
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'R').u16(7).u16(450); })));
        assert_eq!(w.me_reputation, 450, "positive reputation");
        // An 'R' for a non-self rid updates THAT actor's reputation (the server
        // broadcasts a player's reputation change zone-wide) but never touches
        // mine. An unknown rid is a harmless no-op.
        w.apply(&msg(pk::NEW_ACTOR, pkt(|p| {
            p.u32(0).u16(50).u16(1).u32(0).u16(3);
            p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
            p.u8(1).str8("Rival").str8("");
            p.u8(0).u16(100); // gender byte + spawn reputation 100
        })));
        assert_eq!(w.actors[&50].reputation, 100);
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'R').u16(50).u16((-9i16) as u16); })));
        assert_eq!(w.actors[&50].reputation, -9, "remote actor reputation updates (signed)");
        assert_eq!(w.me_reputation, 450, "other actors' reputation leaves mine unchanged");
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'R').u16(999).u16(5); }))); // unknown rid
        assert_eq!(w.me_reputation, 450);
        // The 'R' layout (no attr byte) must not be misparsed as an attribute.
        assert!(w.me_attributes.is_empty(), "'R' does not write an attribute slot");
    }

    #[test]
    fn player_trade_lifecycle_via_dispatch() {
        use crate::trade::TradeKind;
        let mut w = World { my_runtime_id: 7, ..Default::default() };

        // A vendor OPEN_TRADING ('N') opens a window but NO player_trade board.
        let mut npc = MsgWriter::new();
        npc.u8(b'N');
        w.apply(&msg(pk::OPEN_TRADING, npc.into_bytes()));
        assert!(w.player_trade.is_none(), "vendor trade has no player board");

        // A player OPEN_TRADING ('P') opens an empty player_trade board.
        w.apply(&msg(pk::OPEN_TRADING, pkt(|p| { p.u8(b'P'); })));
        assert_eq!(w.current_trade.as_ref().map(|t| t.kind), Some(TradeKind::Player));
        assert!(w.player_trade.as_ref().is_some_and(|pt| pt.his.is_empty()));

        // P_UpdateTrading drives the partner's side: add item 42 x2 in slot 3.
        let mut add = MsgWriter::new();
        add.u8(3).u16(2).u16(42); // slot, amount, ItemInstance leading u16 (item id)
        for _ in 0..40 { add.u16(0); } // pad the 83-byte ItemInstance
        add.u8(0);
        w.apply(&msg(pk::UPDATE_TRADING, add.into_bytes()));
        let his = &w.player_trade.as_ref().unwrap().his;
        assert_eq!(his.len(), 1);
        assert_eq!((his[0].slot, his[0].item_id, his[0].amount), (3, 42, 2));

        // P_CloseTrading clears both the window and the player board.
        w.apply(&msg(pk::CLOSE_TRADING, Vec::new()));
        assert!(w.current_trade.is_none() && w.player_trade.is_none());

        // An UPDATE_TRADING with no open board is a safe no-op (soft-fail).
        w.apply(&msg(pk::UPDATE_TRADING, pkt(|p| { p.u8(3).u16(0); })));
        assert!(w.player_trade.is_none());
    }

    #[test]
    fn health_stat_is_project_configurable() {
        // A project that assigns Health to slot 3 (not 0). The server's
        // P_StatUpdate reports HP under slot 3, so the client must mirror THAT
        // onto me_health (the HP-bar source) and must NOT treat slot 0 as Health.
        // Regression for the old hardcoded `HEALTH_STAT = 0`, which froze HP bars
        // (combat damage invisible) on any project where Health != 0.
        let mut w = World { my_runtime_id: 1, health_stat: 3, ..Default::default() };
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'M').u16(1).u8(3).u16(200); })));
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'A').u16(1).u8(3).u16(150); })));
        assert_eq!((w.me_health, w.me_health_max), (150, 200), "HP mirrors slot 3");

        // Slot 0 is just another attribute here — it must not touch HP.
        w.apply(&msg(pk::STAT_UPDATE, pkt(|p| { p.u8(b'A').u16(1).u8(0).u16(9); })));
        assert_eq!(w.me_health, 150, "slot 0 must not touch HP when health_stat=3");
        assert_eq!(w.me_attributes[&0], (9, 0), "slot 0 still recorded as a normal attribute");
    }

    #[test]
    fn new_actor_appearance_both_gender_modes() {
        let mut w = World::default();
        // Template 3 = male-only (mode 1, NO gender byte); template 9 =
        // player-selectable (mode 0, gender byte present).
        w.template_genders.insert(3, 1);
        w.template_genders.insert(9, 0);

        // Mode-1 NPC: after name+tag, Reputation, then Face/Hair/Body/Beard.
        w.apply(&msg(
            pk::NEW_ACTOR,
            pkt(|p| {
                p.u32(0).u16(50).u16(1).u32(0).u16(3);
                p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
                p.u8(1).str8("Knight"); // isPlayer name
                p.str8("[Boss]"); // tag (no gender byte for mode 1)
                p.u16(0); // reputation
                p.u16(2).u16(1).u16(3).u16(4); // face hair body beard
            }),
        ));
        let a = &w.actors[&50];
        assert_eq!(a.tag, "[Boss]");
        assert_eq!(a.gender, 0); // mode 1 -> male
        assert_eq!((a.face_tex, a.hair, a.body_tex, a.beard), (2, 1, 3, 4));

        // Mode-0 player: gender byte = 1 (female) sits between tag and reputation.
        w.apply(&msg(
            pk::NEW_ACTOR,
            pkt(|p| {
                p.u32(0).u16(51).u16(1).u32(0).u16(9);
                p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
                p.u8(1).str8("Heroine");
                p.str8(""); // empty tag
                p.u8(1); // gender byte (female)
                p.u16(0); // reputation
                p.u16(4).u16(0).u16(1).u16(0); // face hair body beard
            }),
        ));
        let b = &w.actors[&51];
        assert_eq!(b.gender, 1);
        assert_eq!((b.face_tex, b.body_tex), (4, 1));
    }

    // P_AppearanceUpdate mutates a live actor's look in place; the per-frame
    // re-bake picks it up. Covers every subtype, the receive-site clamps, the
    // race-change gender re-derivation, the Me mirror, and the soft-fail paths.
    #[test]
    fn appearance_update_all_subtypes() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        w.template_genders.insert(3, 1); // male-only
        w.template_genders.insert(7, 2); // female-only
        w.template_genders.insert(9, 0); // player-selectable

        // Spawn actor 50 (template 3, male) with a known appearance.
        w.apply(&msg(pk::NEW_ACTOR, pkt(|p| {
            p.u32(0).u16(50).u16(1).u32(0).u16(3);
            p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
            p.u8(0).str8("Knight").str8("");
            p.u16(0); // reputation (no gender byte: mode 1)
            p.u16(2).u16(1).u16(3).u16(4); // face hair body beard
        })));
        assert_eq!((w.actors[&50].face_tex, w.actors[&50].hair, w.actors[&50].body_tex, w.actors[&50].beard), (2, 1, 3, 4));

        // Single-byte subtypes mutate the matching field; out-of-range clamps to 4.
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'F').u16(50).u8(3); })));
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'H').u16(50).u8(2); })));
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'B').u16(50).u8(200); }))); // clamp -> 4
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'D').u16(50).u8(0); })));
        assert_eq!((w.actors[&50].face_tex, w.actors[&50].hair, w.actors[&50].body_tex, w.actors[&50].beard), (3, 2, 4, 0));

        // Gender clamps to 0/1.
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'G').u16(50).u8(9); })));
        assert_eq!(w.actors[&50].gender, 1);

        // Race change "C" to a female-only template forces gender female (1).
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'C').u16(50).u16(7); })));
        assert_eq!(w.actors[&50].template_id, 7);
        assert_eq!(w.actors[&50].gender, 1);
        // Race change to a SELECTABLE template leaves gender as-is.
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'C').u16(50).u16(9); })));
        assert_eq!(w.actors[&50].template_id, 9);
        assert_eq!(w.actors[&50].gender, 1); // unchanged

        // The local player (rid == my_runtime_id) mutates the me_* mirror that
        // the in-world body reads.
        w.me_actor_id = 3;
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'H').u16(1).u8(4); })));
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'C').u16(1).u16(7); })));
        assert_eq!(w.me_hair, 4);
        assert_eq!(w.me_actor_id, 7);
        assert_eq!(w.me_gender, 1); // female-only template forced me female

        // Unknown actor and a truncated packet both soft-fail (no panic, no-op).
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'F').u16(999).u8(2); })));
        w.apply(&msg(pk::APPEARANCE_UPDATE, pkt(|p| { p.u8(b'F').u16(50); }))); // missing value
        assert_eq!(w.actors[&50].face_tex, 3, "truncated packet left face unchanged");
    }

    // P_RepositionActor hard-teleports an actor: the authoritative AND render
    // positions snap (no glide), the interpolation trail is cleared, and the
    // rotate subtype sets yaw. Covers actors, the local player, and soft-fails.
    #[test]
    fn reposition_actor_hard_snaps() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        w.apply(&msg(pk::NEW_ACTOR, pkt(|p| {
            p.u32(0).u16(50).u16(1).u32(0).u16(3);
            p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
            p.u8(0).str8("Stag").str8("");
        })));
        // Simulate the actor mid-glide: render lags the authoritative pos, trail buffered.
        {
            let a = w.actors.get_mut(&50).unwrap();
            a.render_x = -99.0;
            a.render_z = -99.0;
            a.samples.push([0.0, 0.0, 0.0]);
        }
        // "M" teleport to (120, 5, -80).
        w.apply(&msg(pk::REPOSITION_ACTOR, pkt(|p| { p.u8(b'M').u16(50).f32(120.0).f32(5.0).f32(-80.0).u8(0); })));
        let a = &w.actors[&50];
        assert_eq!((a.x, a.y, a.z), (120.0, 5.0, -80.0));
        assert_eq!((a.dest_x, a.dest_z), (120.0, -80.0));
        assert_eq!((a.render_x, a.render_z), (120.0, -80.0), "render hard-snaps, no glide");
        assert!(a.samples.is_empty(), "interpolation trail cleared on teleport");

        // Rotate subtype sets both the authoritative and render yaw.
        w.apply(&msg(pk::REPOSITION_ACTOR, pkt(|p| { p.u8(b'R').u16(50).f32(90.0); })));
        assert_eq!((w.actors[&50].yaw, w.actors[&50].render_yaw), (90.0, 90.0));

        // Local player teleport: me_* + me_render snap, the trail clears.
        w.me_render_x = 0.0;
        w.me_render_z = 0.0;
        w.me_samples.push([0.0, 0.0, 0.0]);
        w.apply(&msg(pk::REPOSITION_ACTOR, pkt(|p| { p.u8(b'M').u16(1).f32(50.0).f32(2.0).f32(60.0).u8(0); })));
        assert_eq!((w.me_x, w.me_y, w.me_z), (50.0, 2.0, 60.0));
        assert_eq!((w.me_render_x, w.me_render_z), (50.0, 60.0));
        assert!(w.me_samples.is_empty());

        // Unknown actor and a truncated packet both soft-fail (no panic, no-op).
        w.apply(&msg(pk::REPOSITION_ACTOR, pkt(|p| { p.u8(b'M').u16(999).f32(1.0).f32(1.0).f32(1.0).u8(0); })));
        w.apply(&msg(pk::REPOSITION_ACTOR, pkt(|p| { p.u8(b'M').u16(50).f32(7.0); }))); // missing Y/Z
        assert_eq!(w.actors[&50].x, 120.0, "truncated packet left position unchanged");
    }

    // P_AnimateActor queues a (rid, name) intent for the App to resolve into a
    // timed clip override; tick_server_anims expires active overrides. (The
    // clip/duration resolution lives App-side — it needs the AssetStore — so this
    // covers the wire parse + the expiry timer.)
    #[test]
    fn animate_actor_queues_intent_and_expires() {
        let mut w = World { my_runtime_id: 1, ..Default::default() };
        // rid 50, fixed-speed 0, speed 0.05, name "Wave" (string-to-end, no prefix).
        w.apply(&msg(pk::ANIMATE_ACTOR, pkt(|p| { p.u16(50).u8(0).f32(0.05).raw(b"Wave"); })));
        assert_eq!(w.pending_anims, vec![(50, "Wave".to_string())]);

        // An empty name queues nothing (no clip to play).
        w.apply(&msg(pk::ANIMATE_ACTOR, pkt(|p| { p.u16(50).u8(0).f32(0.05); })));
        assert_eq!(w.pending_anims.len(), 1, "empty name not queued");

        // A truncated packet (missing the speed f32) soft-fails.
        w.apply(&msg(pk::ANIMATE_ACTOR, pkt(|p| { p.u16(50).u8(0); })));
        assert_eq!(w.pending_anims.len(), 1, "truncated packet not queued");

        // Mode-3 lifecycle on the local player (rid 1): the clip plays through once
        // (movement can't interrupt), then the end pose holds while idle and only
        // reverts once the player moves.
        w.server_anims.insert(1, ServerAnim { name: "Wave".into(), elapsed: 0.0, duration: 0.5 });
        w.tick_server_anims(0.3, true); // mid-playthrough: movement is ignored
        assert!(w.server_anims.contains_key(&1), "plays through even while moving");
        w.tick_server_anims(0.3, false); // elapsed 0.6 > 0.5, idle → held
        assert!(w.server_anims.contains_key(&1), "holds the end pose while idle");
        w.tick_server_anims(0.1, true); // moving after the playthrough → cancel
        assert!(w.server_anims.is_empty(), "movement cancels the held pose");

        // A remote actor that was WALKING when told to emote: on_animate_actor
        // must pin dest=pos (Blitz ClientNet.bb:727-728) so its held end pose
        // isn't cancelled by the stale far-away dest the moment the clip plays out.
        w.apply(&msg(pk::NEW_ACTOR, pkt(|p| {
            p.u32(0).u16(60).u16(1).u32(0).u16(3);
            p.f32(10.0).f32(0.0).f32(20.0).f32(0.0);
            p.u8(0).str8("Walker").str8("");
        })));
        w.actors.get_mut(&60).unwrap().dest_x = 999.0; // stale far dest (was walking)
        w.actors.get_mut(&60).unwrap().dest_z = 999.0;
        w.apply(&msg(pk::ANIMATE_ACTOR, pkt(|p| { p.u16(60).u8(0).f32(0.05).raw(b"Bow"); })));
        assert_eq!(w.actors[&60].dest_x, 10.0, "dest pinned to pos on emote");
        assert_eq!(w.actors[&60].dest_z, 20.0);
        // Install the override and tick well past its duration while idle: the
        // pinned dest now reads as not-moving, so the end pose holds.
        w.server_anims.insert(60, ServerAnim { name: "Bow".into(), elapsed: 0.0, duration: 0.1 });
        w.tick_server_anims(0.5, false);
        assert!(w.server_anims.contains_key(&60), "held pose survives a pinned (idle) dest");
    }

    // P_CreateEmitter queues a (tex, lifetime, pos, name) intent for the App to
    // resolve into a live particle emitter. The config name is validated against
    // path traversal; bad names + short packets soft-fail.
    #[test]
    fn create_emitter_queues_and_validates() {
        let mut w = World::default();
        // Valid, world-anchored: tex 5, 2000ms, rid 0 → attach None, pos (10,1,20).
        w.apply(&msg(pk::CREATE_EMITTER, pkt(|p| { p.u16(5).u32(2000).u16(0).f32(10.0).f32(1.0).f32(20.0).raw(b"fire"); })));
        assert_eq!(
            w.pending_emitters,
            vec![PendingEmitter { tex_id: 5, lifetime_ms: 2000, pos: [10.0, 1.0, 20.0], name: "fire".into(), attach: None }]
        );

        // Valid, actor-attached: rid 42 → attach Some(42), pos is the local offset.
        w.apply(&msg(pk::CREATE_EMITTER, pkt(|p| { p.u16(5).u32(2000).u16(42).f32(0.0).f32(50.0).f32(0.0).raw(b"aura"); })));
        assert_eq!(w.pending_emitters[1].attach, Some(42), "rid>0 attaches to the actor");

        // Path traversal (".."), a slash, an empty name, and a truncated packet
        // all queue nothing.
        w.apply(&msg(pk::CREATE_EMITTER, pkt(|p| { p.u16(5).u32(2000).u16(0).f32(0.0).f32(0.0).f32(0.0).raw(b"..\\evil"); })));
        w.apply(&msg(pk::CREATE_EMITTER, pkt(|p| { p.u16(5).u32(2000).u16(0).f32(0.0).f32(0.0).f32(0.0).raw(b"a/b"); })));
        w.apply(&msg(pk::CREATE_EMITTER, pkt(|p| { p.u16(5).u32(2000).u16(0).f32(0.0).f32(0.0).f32(0.0); })));
        w.apply(&msg(pk::CREATE_EMITTER, pkt(|p| { p.u16(5).u32(2000).u16(0); })));
        assert_eq!(w.pending_emitters.len(), 2, "only the two valid emitters queued");
    }

    #[test]
    fn attack_and_rename() {
        let mut w = World::default();
        // Register an actor (rnid 50).
        w.apply(&msg(
            pk::NEW_ACTOR,
            pkt(|p| {
                p.u32(0).u16(50).u16(1).u32(0).u16(3);
                p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
                p.u8(0).str8("Stag");
            }),
        ));

        // P_AttackActor: 'H', target 50, raw damage 11 (-> 10), type 2.
        w.apply(&msg(pk::ATTACK_ACTOR, pkt(|p| { p.u8(b'H').u16(50).u16(11).u8(2); })));
        assert_eq!(
            w.combat_events.last().copied(),
            Some(CombatEvent { target: 50, attacker: w.my_runtime_id, damage: 10, damage_type: 2 })
        );

        // P_NameChange: rid 50, name "Boss", tag "[Elite]".
        w.apply(&msg(
            pk::NAME_CHANGE,
            pkt(|p| {
                p.u16(50).u8(4).raw(b"Boss").raw(b"[Elite]");
            }),
        ));
        assert_eq!(w.actors[&50].name, "Boss");
        assert_eq!(w.actors[&50].tag, "[Elite]");
    }

    #[test]
    fn dropped_item_spawn_and_pickup() {
        let mut w = World::default();
        // P_InventoryUpdate "D": amount u16, x/y/z f32, handle u32, then the
        // 83-byte ItemInstance (id = first u16).
        let drop = pkt(|p| {
            p.u8(b'D').u16(3).f32(12.0).f32(0.0).f32(34.0).u32(777);
            p.u16(42); // ItemInstance id
            p.raw(&[0u8; 81]); // rest of the 83-byte ItemInstance
        });
        w.apply(&msg(pk::INVENTORY_UPDATE, drop));
        assert_eq!(w.dropped_items.len(), 1);
        let di = w.dropped_items[&777];
        assert_eq!((di.item_id, di.amount), (42, 3));
        assert!((di.x - 12.0).abs() < 0.01 && (di.z - 34.0).abs() < 0.01);

        // "P" (someone else grabbed it) removes it by handle.
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| { p.u8(b'P').u32(777); })));
        assert!(w.dropped_items.is_empty());

        // A no-item-sentinel drop is ignored.
        let bad = pkt(|p| {
            p.u8(b'D').u16(1).f32(0.0).f32(0.0).f32(0.0).u32(9);
            p.u16(0xFFFF).raw(&[0u8; 81]);
        });
        w.apply(&msg(pk::INVENTORY_UPDATE, bad));
        assert!(w.dropped_items.is_empty());
    }

    #[test]
    fn inventory_give_take_health_sync() {
        let mut w = World::default();
        // "G" give: handle u32, item u16, amount u16 → free backpack slot + GY.
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| { p.u8(b'G').u32(99).u16(10).u16(2); })));
        assert_eq!(w.me_inventory.len(), 1);
        let (&slot, it) = w.me_inventory.iter().next().unwrap();
        assert_eq!(slot, 14); // first free backpack slot
        assert_eq!((it.item_id, it.amount), (10, 2));
        // Acked with "GY" + handle(LE) + slot.
        assert_eq!(w.pending_sends.len(), 1);
        assert_eq!(w.pending_sends[0].1, vec![b'G', b'Y', 99, 0, 0, 0, 14]);

        // Another give of the same item stacks into the same slot.
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| { p.u8(b'G').u32(1).u16(10).u16(3); })));
        assert_eq!(w.me_inventory.len(), 1);
        assert_eq!(w.me_inventory[&14].amount, 5);

        // "H" durability change on slot 14.
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| { p.u8(b'H').u8(14).u8(60); })));
        assert_eq!(w.me_inventory[&14].health, 60);

        // "T" take 2 → amount 3; take 3 more → slot removed.
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| { p.u8(b'T').u8(14).u16(2); })));
        assert_eq!(w.me_inventory[&14].amount, 3);
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| { p.u8(b'T').u8(14).u16(3); })));
        assert!(w.me_inventory.is_empty());
    }

    // P_ItemHealth is the combat durability channel (slot u8 + health u16),
    // separate from the script-driven P_InventoryUpdate "H" sub-packet. It updates
    // the local player's equipped item so effective_attack_range stays correct as
    // gear wears. Soft-fails on an empty slot or a short packet.
    #[test]
    fn item_health_combat_durability() {
        let mut w = World::default();
        // Seed an item at backpack slot 14 via a "G" give (health starts 100).
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| { p.u8(b'G').u32(1).u16(10).u16(1); })));
        assert_eq!(w.me_inventory[&14].health, 100);

        // Combat wear: slot 14 → health 73 (u16 on the wire).
        w.apply(&msg(pk::ITEM_HEALTH, pkt(|p| { p.u8(14).u16(73); })));
        assert_eq!(w.me_inventory[&14].health, 73);

        // A broken item reads 0 (effective_attack_range then drops a bow to melee).
        w.apply(&msg(pk::ITEM_HEALTH, pkt(|p| { p.u8(14).u16(0); })));
        assert_eq!(w.me_inventory[&14].health, 0);

        // An empty slot and a truncated packet both soft-fail (no panic, no-op).
        w.apply(&msg(pk::ITEM_HEALTH, pkt(|p| { p.u8(99).u16(50); })));
        w.apply(&msg(pk::ITEM_HEALTH, pkt(|p| { p.u8(14); }))); // missing health
        assert_eq!(w.me_inventory[&14].health, 0, "truncated packet left health unchanged");
    }

    // Actor level: captured from P_NewActor (previously dropped) and kept live by
    // the P_XPUpdate "L" sub-packet (another actor levelled up). Shown on the
    // nameplate + target panel.
    #[test]
    fn actor_level_captured_and_updated() {
        let mut w = World::default();
        // Spawn actor 50 at level 7 (level is the 2nd u16 after server_area+rid).
        w.apply(&msg(pk::NEW_ACTOR, pkt(|p| {
            p.u32(0).u16(50).u16(7).u32(0).u16(3);
            p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
            p.u8(0).str8("Stag").str8("");
        })));
        assert_eq!(w.actors[&50].level, 7, "spawn level captured");

        // P_XPUpdate "L": actor 50 levelled to 8.
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'L').u16(50).u16(8); })));
        assert_eq!(w.actors[&50].level, 8, "level-up applied");

        // "L" for an unknown actor and a truncated packet both soft-fail.
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'L').u16(999).u16(5); })));
        w.apply(&msg(pk::XP_UPDATE, pkt(|p| { p.u8(b'L').u16(50); }))); // missing level
        assert_eq!(w.actors[&50].level, 8, "unchanged on unknown actor / truncation");
    }

    // Actor reputation is captured from P_NewActor as a SIGNED short (a
    // hostile-faction actor can be negative). Shown in the target panel.
    #[test]
    fn actor_reputation_signed_capture() {
        let mut w = World::default();
        // template 3 is mode 0 (selectable) → a gender byte precedes reputation.
        // Negative reputation: -10000 = 0xD8F0 as the u16 wire bits.
        w.apply(&msg(pk::NEW_ACTOR, pkt(|p| {
            p.u32(0).u16(50).u16(1).u32(0).u16(3);
            p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
            p.u8(0).str8("Wolf").str8("");
            p.u8(0); // gender byte (mode 0)
            p.u16(0xD8F0); // reputation -10000
        })));
        assert_eq!(w.actors[&50].reputation, -10000, "negative reputation sign-extended");

        // Positive reputation round-trips unchanged.
        w.apply(&msg(pk::NEW_ACTOR, pkt(|p| {
            p.u32(0).u16(51).u16(1).u32(0).u16(3);
            p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
            p.u8(0).str8("Guard").str8("");
            p.u8(0);
            p.u16(250);
        })));
        assert_eq!(w.actors[&51].reputation, 250);
    }

    // P_KickedPlayer (empty payload) flags the session so the App tears it down
    // and returns to the login screen instead of freezing.
    #[test]
    fn kicked_player_sets_flag() {
        let mut w = World::default();
        assert!(!w.kicked);
        w.apply(&msg(pk::KICKED_PLAYER, pkt(|_p| {})));
        assert!(w.kicked, "P_KickedPlayer flags the session for teardown");
    }

    #[test]
    fn inventory_receive_from_dropped() {
        let mut w = World::default();
        // Drop an item in the world, then receive it into a slot.
        let drop = pkt(|p| {
            p.u8(b'D').u16(4).f32(0.0).f32(0.0).f32(0.0).u32(55);
            p.u16(7); // item id
            p.raw(&[0u8; 80]);
            p.u8(90); // ItemInstance health byte (offset 82)
        });
        w.apply(&msg(pk::INVENTORY_UPDATE, drop));
        assert_eq!(w.dropped_items.len(), 1);
        assert_eq!(w.dropped_items[&55].health, 90);

        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| { p.u8(b'R').u32(55).u8(20); })));
        assert!(w.dropped_items.is_empty());
        assert_eq!((w.me_inventory[&20].item_id, w.me_inventory[&20].amount, w.me_inventory[&20].health), (7, 4, 90));
    }

    #[test]
    fn equipped_update_sets_actor_gear() {
        let mut w = World::default();
        // Spawn an actor (rid 50).
        let mut p = MsgWriter::new();
        p.u32(7).u16(50).u16(1).u32(0).u16(3);
        p.f32(0.0).f32(0.0).f32(0.0).f32(0.0);
        p.u8(0).str8("Guard");
        w.apply(&msg(pk::NEW_ACTOR, p.into_bytes()));
        assert_eq!(w.actors[&50].equipped, [0xFFFF; 4]); // nothing yet

        // "O": rid 50, weapon 42, shield 65535, chest 7, hat 65535, + 6 gubbins.
        w.apply(&msg(pk::INVENTORY_UPDATE, pkt(|p| {
            p.u8(b'O').u16(50).u16(42).u16(0xFFFF).u16(7).u16(0xFFFF);
            p.raw(&[0u8; 6]);
        })));
        assert_eq!(w.actors[&50].equipped, [42, 0xFFFF, 7, 0xFFFF]);
    }

    #[test]
    fn actor_effect_add_modify_remove() {
        let mut w = World::default();
        // "A" add: id u32, texture u16, name.
        w.apply(&msg(pk::ACTOR_EFFECT, pkt(|p| { p.u8(b'A').u32(5).u16(10).raw(b"Poison"); })));
        assert_eq!(w.active_effects.len(), 1);
        assert_eq!(w.active_effects[0].name, "Poison");
        assert_eq!((w.active_effects[0].id, w.active_effects[0].texture_id), (5, 10));

        // "E" attribute delta: att 0, amount -30.
        w.apply(&msg(pk::ACTOR_EFFECT, pkt(|p| { p.u8(b'E').u8(0).i32(-30); })));
        assert_eq!(w.me_attributes[&0].0, -30);

        // Re-adding the same id replaces, not duplicates.
        w.apply(&msg(pk::ACTOR_EFFECT, pkt(|p| { p.u8(b'A').u32(5).u16(11).raw(b"Poison II"); })));
        assert_eq!(w.active_effects.len(), 1);
        assert_eq!(w.active_effects[0].name, "Poison II");

        // "R" remove by id (no restore block).
        w.apply(&msg(pk::ACTOR_EFFECT, pkt(|p| { p.u8(b'R').u32(5); })));
        assert!(w.active_effects.is_empty());
    }

    #[test]
    fn weather_change_only_for_current_area() {
        let mut w = World::default();
        w.zone.area_id = 7;
        w.zone.weather = 0;
        // A change for our area applies.
        w.apply(&msg(pk::WEATHER_CHANGE, pkt(|p| { p.u32(7).u8(1); })));
        assert_eq!(w.zone.weather, 1);
        // A change for a different area is ignored.
        w.apply(&msg(pk::WEATHER_CHANGE, pkt(|p| { p.u32(99).u8(2); })));
        assert_eq!(w.zone.weather, 1);
    }

    // Optimistic drop removal: the server sends the dropper no "taken" echo, so
    // the client must remove its own dropped item locally (Blitz InventoryDrop).
    // Without this the slot icon lingered and a pickup phantom-duplicated.
    #[test]
    fn inv_remove_decrements_and_clears_slot() {
        use crate::fetch::InvItem;
        let mut w = World::default();
        w.me_inventory.insert(14, InvItem { slot: 14, item_id: 7, amount: 5, health: 100 });

        // Partial drop from a stack: amount drops, the entry stays.
        w.inv_remove(14, 2);
        assert_eq!(w.me_inventory.get(&14).map(|it| it.amount), Some(3));

        // Dropping the rest clears the slot entirely (no lingering ghost icon),
        // freeing it for a subsequent pickup so nothing phantom-duplicates.
        w.inv_remove(14, 3);
        assert!(!w.me_inventory.contains_key(&14));
        assert_eq!(w.inv_free_slot(7), Some(14), "freed slot is reused, not skipped");

        // Over-drop and dropping an empty slot are safe (saturating, no panic).
        w.me_inventory.insert(20, InvItem { slot: 20, item_id: 9, amount: 1, health: 100 });
        w.inv_remove(20, 99);
        assert!(!w.me_inventory.contains_key(&20));
        w.inv_remove(20, 1); // already gone — no-op
    }
}
