//! Live-world session layer + `P_StartGame` (entering the game).
//!
//! This is the minimal "enter the world" slice: it authenticates, refuses a
//! second concurrent login, allocates a runtime id, records a [`WorldSession`]
//! for the peer, and sends the client its runtime id, action-bar contents, the
//! login message, and its XP-bar level — enough for the client to transition
//! into the world standing as its character.
//!
//! Deferred (the next world phase): spawning the actor into a live **area
//! instance**, broadcasting `P_NewActor` to other players + streaming existing
//! actors to the joiner, the per-tick `P_StandardUpdate` position relay, NPC
//! spawns, and the `"Login"` BVM script. Until then the joiner is in the world
//! but the world has no other actors yet.

use std::collections::HashMap;

use rcce_net::codec::{MsgReader, MsgWriter};
use rcce_server_accounts::password;
use rcce_server_accounts::store::AccountStore;
use rcce_server_accounts::throttle::LoginThrottle;
use rcce_server_core::area::Area;
use rcce_server_core::character::Character;
use rcce_server_core::world::RuntimeIdAllocator;

use crate::config::ServerConfig;

/// Type bytes (`Packets.bb`).
pub const P_CHANGE_AREA: u8 = 9;
pub const P_WEATHER_CHANGE: u8 = 17;
pub const P_REPOSITION_ACTOR: u8 = 49;
pub const P_NEW_ACTOR: u8 = 11;
pub const P_START_GAME: u8 = 12;
pub const P_ACTOR_GONE: u8 = 13;
pub const P_STANDARD_UPDATE: u8 = 14;
pub const P_CHAT_MESSAGE: u8 = 16;
pub const P_ATTACK_ACTOR: u8 = 18;
pub const P_ACTOR_DEAD: u8 = 19;
pub const P_STAT_UPDATE: u8 = 22;
pub const P_QUEST_LOG: u8 = 23;
pub const P_DIALOG: u8 = 21;
pub const P_RIGHT_CLICK: u8 = 20;
pub const P_DISMOUNT: u8 = 47;
pub const P_XP_UPDATE: u8 = 32;
pub const P_INVENTORY_UPDATE: u8 = 15;
pub const P_CREATE_EMITTER: u8 = 28;
pub const P_SOUND: u8 = 29;
pub const P_GOLD_CHANGE: u8 = 24;
pub const P_SPELL_UPDATE: u8 = 27;
pub const P_KNOWN_SPELL_UPDATE: u8 = 26;
pub const P_NAME_CHANGE: u8 = 25;
pub const P_APPEARANCE_UPDATE: u8 = 39;
pub const P_KICKED_PLAYER: u8 = 60;
pub const P_SCREEN_FLASH: u8 = 33;
pub const P_BUBBLE_MESSAGE: u8 = 52;
pub const P_ANIMATE_ACTOR: u8 = 30;
pub const P_MUSIC: u8 = 34;
pub const P_FLOATING_NUMBER: u8 = 48;
pub const P_SPEECH: u8 = 50;
pub const P_JUMP: u8 = 46;
pub const P_ITEM_SCRIPT: u8 = 43;
pub const P_ITEM_HEALTH: u8 = 45;
pub const P_PROGRESS_BAR: u8 = 51;
pub const P_SCRIPT_INPUT: u8 = 53;
pub const P_ACTION_BAR_UPDATE: u8 = 31;
pub const P_OPEN_TRADING: u8 = 35;
pub const P_ACTOR_EFFECT: u8 = 36;
pub const P_PROJECTILE: u8 = 37;
pub const P_PARTY_UPDATE: u8 = 38;
pub const P_CLOSE_TRADING: u8 = 40;
pub const P_UPDATE_TRADING: u8 = 41;
pub const P_TRADE: u8 = 62;
pub const P_EAT_ITEM: u8 = 44;
pub const P_EXAMINE: u8 = 61;

/// Equipment inventory slots (`Inventories.bb`): weapon/shield/hat/chest.
const EQUIP_SLOTS: [usize; 4] = [0, 1, 2, 3];
/// "No item" sentinel in the equipment id fields.
const NO_ITEM: u16 = 65535;

/// Encode an actor as the `P_NewActor` / zone-introduction payload —
/// `ActorInstanceToString$` (`Actors.bb:1121`). `genders == 0` means the race
/// has selectable gender, so the gender byte is included (the decoder keys off
/// the same race flag). `health_stat`/`speed_stat` index the 40 attributes
/// (project-configured via `Fixed Attributes.dat`). All multi-byte fields are
/// little-endian.
#[allow(clippy::too_many_arguments)]
pub fn actor_to_wire(
    server_area: u32,
    runtime_id: u16,
    x: f32,
    y: f32,
    z: f32,
    yaw: f32,
    is_player: bool,
    c: &Character,
    genders: u8,
    health_stat: usize,
    speed_stat: usize,
) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u32(server_area);
    w.u16(runtime_id);
    w.u16(c.level as u16);
    w.i32(c.xp);
    w.u16(c.actor_id);
    w.f32(x);
    w.f32(y);
    w.f32(z);
    w.f32(yaw);
    w.u8(if is_player { 1 } else { 0 });
    w.str8(&c.name);
    w.str8(&c.tag);
    if genders == 0 {
        w.u8(c.gender);
    }
    w.u16(c.reputation as u16);
    w.u16(c.face_tex as u16);
    w.u16(c.hair as u16);
    w.u16(c.body_tex as u16);
    w.u16(c.beard as u16);
    let av = |i: usize| c.attributes.value.get(i).copied().unwrap_or(0) as u16;
    let am = |i: usize| c.attributes.maximum.get(i).copied().unwrap_or(0) as u16;
    w.u16(av(speed_stat));
    w.u16(am(speed_stat));
    w.u16(av(health_stat));
    w.u16(am(health_stat));
    for slot in EQUIP_SLOTS {
        let id = c
            .inventory
            .get(slot)
            .and_then(|s| s.item.as_ref())
            .map(|it| it.item_id)
            .unwrap_or(NO_ITEM);
        w.u16(id);
    }
    w.u8(c.home_faction);
    for i in 0..100 {
        w.u8(c.faction_ratings.get(i).copied().unwrap_or(0));
    }
    w.into_bytes()
}

const ACTION_BAR_SLOTS: usize = 36;

/// `WorldCoordMax#` — positions outside ±this (or NaN/Inf) clamp to 0.
fn clamp_world_coord(v: f32) -> f32 {
    if v > -100_000.0 && v < 100_000.0 {
        v
    } else {
        0.0
    }
}

/// How long a warp suppresses the warped player's inbound `P_StandardUpdate`s
/// when no warp-completion ack arrives (see `ignore_update_until_ms`). The
/// Rust client acks `P_ChangeArea`/`P_RepositionActor` (client-rs `world.rs`
/// `on_change_area`/`on_reposition_actor`), so its window closes at ~one RTT;
/// this deadline is the fallback for the Blitz client, which never acks (its
/// send is commented out at `ClientNet.bb:1779`) — invisible there because
/// Blitz's `P_ChangeArea` triggers a full zone reload during which the client
/// sends nothing. The stale packets were all sent before the client received
/// the warp, so ~one RTT + the ~9 Hz update cadence bounds them.
pub const WARP_IGNORE_UPDATE_MS: u64 = 500;

/// A logged-in player's live session.
#[derive(Clone, Debug)]
pub struct WorldSession {
    pub user: String,
    pub char_slot: u8,
    pub runtime_id: u16,
    pub area: String,
    pub x: f32,
    pub y: f32,
    pub z: f32,
    /// Movement intent from the last `P_StandardUpdate` (relayed to peers).
    pub dest_x: f32,
    pub dest_z: f32,
    pub is_running: u8,
    pub walking_backward: u8,
    /// Last attack time (ms) for the combat-delay gate.
    pub last_attack_ms: u64,
    /// The portal `(area, portalName)` the player currently occupies, if any —
    /// edge-detection so a portal fires once on entry and the destination portal
    /// it warps onto doesn't immediately bounce them back (parity with the
    /// `LastPortal`/`PortalLockTime` lock in `Server.bb:613`).
    pub in_portal: Option<(String, String)>,
    /// Runtime id of the actor this player is riding (`AI\Mount`), or 0 if not
    /// mounted. Relayed in every `P_StandardUpdate` at offset 21 so peers attach
    /// the mount to the rider (`ClientNet.bb:1509`).
    pub mount_rid: u16,
    /// The player's current target (`AI\AITarget`), or 0 — set when they attack
    /// (`P_AttackActor`, `ServerNet.bb:1612`). Read by `ActorTarget`/`/assist`.
    pub target_rid: u16,
    /// `AI\IgnoreUpdate` (`Actors.bb:196`) as a millisecond deadline, 0 = off.
    /// While `now < deadline` the server drops this peer's inbound
    /// `P_StandardUpdate`s — they were sent before the client processed the
    /// warp and carry the stale pre-warp position (the Rofar 8/16/2007 fix,
    /// `GameServer.bb:4`). Cleared by the client's `P_ChangeArea` /
    /// `P_RepositionActor` warp-completion ack (`ServerNet.bb:727-737`) — the
    /// Rust client sends both (client-rs `on_change_area` /
    /// `on_reposition_actor`), closing the window at ~one RTT.
    /// Deviation from Blitz: Blitz's flag has no expiry, but the Blitz client
    /// never sends the ack (its send is commented out at `ClientNet.bb:1779` —
    /// which is why Blitz also disabled the *set*), so a bare wait-for-ack
    /// would freeze its movement forever. The deadline bounds the suppression
    /// for non-acking clients instead (see [`WARP_IGNORE_UPDATE_MS`]).
    pub ignore_update_until_ms: u64,
}

/// Build the outbound `P_StandardUpdate` for a session — the per-tick movement
/// relay (`GameServer.bb:1117-1125`, the non-self/non-flying case):
/// `[u16 runtimeId][f32 x][f32 z][u8 run][u8 back][f32 destX][f32 destZ][u16 mount=0]`.
pub fn standard_update_to_wire(s: &WorldSession) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u16(s.runtime_id);
    w.f32(s.x);
    w.f32(s.z);
    w.u8(s.is_running);
    w.u8(s.walking_backward);
    w.f32(s.dest_x);
    w.f32(s.dest_z);
    w.u16(s.mount_rid); // mount runtime id (0 = not mounted)
    w.into_bytes()
}

/// Build the outbound `P_StandardUpdate` for an NPC chase step — same wire shape
/// as [`standard_update_to_wire`] but from raw fields (NPCs aren't sessions).
/// `walking_backward` and `mount` are always 0 for NPCs.
pub fn npc_standard_update_to_wire(
    rid: u16,
    x: f32,
    z: f32,
    is_running: u8,
    dest_x: f32,
    dest_z: f32,
) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u16(rid);
    w.f32(x);
    w.f32(z);
    w.u8(is_running);
    w.u8(0);
    w.f32(dest_x);
    w.f32(dest_z);
    w.u16(0);
    w.into_bytes()
}

/// `P_ActorGone` payload: the departing actor's runtime id as **4 bytes**
/// (`RCE_StrFromInt$(RuntimeID)` default length, `GameServer.bb:1371`).
pub fn actor_gone_payload(runtime_id: u16) -> Vec<u8> {
    (runtime_id as u32).to_le_bytes().to_vec()
}

/// `P_ChangeArea` payload — tells a warping player it has entered a new zone
/// (`GameServer.bb:1330-1331`):
/// `[f32 X][f32 Y][f32 Z][f32 Yaw][u8 PvP][i16 Gravity][u32 ServerArea]
///  [u8 Weather][u8 areaNameLen][areaName]`.
pub fn change_area_payload(
    x: f32,
    y: f32,
    z: f32,
    yaw: f32,
    pvp: u8,
    gravity: i16,
    server_area: u32,
    weather: u8,
    area_name: &str,
) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.f32(x);
    w.f32(y);
    w.f32(z);
    w.f32(yaw);
    w.u8(pvp);
    w.u16(gravity as u16); // 2-byte LE, matches RCE_StrFromInt$(Gravity, 2)
    w.u32(server_area);
    w.u8(weather);
    w.str8(area_name);
    w.into_bytes()
}

/// `P_RepositionActor` payload — a same-area warp tells other players the actor
/// jumped position (`GameServer.bb:1377`):
/// `[u16 RuntimeID][f32 X][f32 Y][f32 Z][u8 0]`.
pub fn reposition_payload(runtime_id: u16, x: f32, y: f32, z: f32) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u16(runtime_id);
    w.f32(x);
    w.f32(y);
    w.f32(z);
    w.u8(0);
    w.into_bytes()
}

/// `P_RepositionActor "M"` (`BVM_MOVEACTOR`, `ScriptingCommands.bb:815`):
/// `[b'M'][u16 rid][f32 x][f32 y][f32 z][u8 flag]`. This is the sub-byte-first
/// form the client actually decodes (`ClientNet.bb:185` / the Rust client's
/// `on_reposition_actor`) — distinct from the legacy [`reposition_payload`],
/// which omits the sub byte (faithfully mirroring the Blitz SetArea-warp quirk).
pub fn reposition_move_payload(rid: u16, x: f32, y: f32, z: f32, flag: u8) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(b'M');
    w.u16(rid);
    w.f32(x);
    w.f32(y);
    w.f32(z);
    w.u8(flag);
    w.into_bytes()
}

/// `P_RepositionActor "R"` (`BVM_ROTATEACTOR`): `[b'R'][u16 rid][f32 yaw]`.
pub fn reposition_rotate_payload(rid: u16, yaw: f32) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(b'R');
    w.u16(rid);
    w.f32(yaw);
    w.into_bytes()
}

/// The live world: runtime-id allocation + per-peer sessions + the
/// account→peer "logged on" index (re-enables the `"L"`/session gates).
#[derive(Default)]
pub struct World {
    allocator: RuntimeIdAllocator,
    /// peer id → session.
    sessions: HashMap<u32, WorldSession>,
    /// uppercased username → peer id (one concurrent session per account).
    logged_on: HashMap<String, u32>,
    /// peer id → runtime ids it has already been told about (`P_NewActor` sent).
    known: HashMap<u32, std::collections::HashSet<u16>>,
    /// area name → synthetic `ServerArea` id (the client only needs a stable
    /// per-zone grouping value; we mint one per distinct area).
    area_ids: HashMap<String, u32>,
    next_area_id: u32,
}

impl World {
    pub fn new() -> Self {
        Self::default()
    }

    /// Whether this account currently has a live session.
    pub fn is_logged_on(&self, user: &str) -> bool {
        self.logged_on.contains_key(&user.to_uppercase())
    }

    /// The peer currently logged into `user`'s account, if any (for the
    /// `RequesterOwnsAccountSession` check on `P_ChangePassword`).
    pub fn logged_on_peer(&self, user: &str) -> Option<u32> {
        self.logged_on.get(&user.to_uppercase()).copied()
    }

    /// The session for a peer, if any.
    pub fn session(&self, peer: u32) -> Option<&WorldSession> {
        self.sessions.get(&peer)
    }

    /// The peer whose player actor has this runtime id, if any (for resolving a
    /// script's actor handle back to a connection).
    pub fn peer_for_runtime(&self, rid: u16) -> Option<u32> {
        self.sessions.iter().find(|(_, s)| s.runtime_id == rid).map(|(p, _)| *p)
    }

    /// The session whose player actor has this runtime id, if any.
    pub fn session_for_runtime(&self, rid: u16) -> Option<&WorldSession> {
        self.sessions.values().find(|s| s.runtime_id == rid)
    }

    /// Update a peer's authoritative position + movement intent (clamped).
    /// Returns false if the peer has no live session.
    #[allow(clippy::too_many_arguments)]
    pub fn update_movement(
        &mut self,
        peer: u32,
        x: f32,
        y: f32,
        z: f32,
        dest_x: f32,
        dest_z: f32,
        is_running: u8,
        walking_backward: u8,
    ) -> bool {
        match self.sessions.get_mut(&peer) {
            Some(s) => {
                s.x = clamp_world_coord(x);
                s.y = clamp_world_coord(y);
                s.z = clamp_world_coord(z);
                s.dest_x = clamp_world_coord(dest_x);
                s.dest_z = clamp_world_coord(dest_z);
                // Players cannot run backwards (anti-cheat, `ServerNet.bb:1840`).
                s.walking_backward = walking_backward;
                s.is_running = if walking_backward != 0 { 0 } else { is_running };
                true
            }
            None => false,
        }
    }

    /// All sessions in `area` other than `except_peer` — the broadcast set for a
    /// position/spawn update (used by the per-tick relay, next phase).
    pub fn peers_in_area<'a>(
        &'a self,
        area: &'a str,
        except_peer: u32,
    ) -> impl Iterator<Item = (u32, &'a WorldSession)> + 'a {
        self.sessions
            .iter()
            .filter(move |(p, s)| **p != except_peer && s.area == area)
            .map(|(p, s)| (*p, s))
    }

    /// Number of players currently in the world.
    pub fn online_count(&self) -> usize {
        self.sessions.len()
    }

    /// Tear down a peer's session (disconnect/logout): free its runtime id and
    /// clear the indices. The gone runtime id is forgotten from every other
    /// peer's known-set so a reused id is re-introduced.
    pub fn logout(&mut self, peer: u32) -> Option<WorldSession> {
        let session = self.sessions.remove(&peer)?;
        self.allocator.free(session.runtime_id);
        self.logged_on.remove(&session.user.to_uppercase());
        self.known.remove(&peer);
        for set in self.known.values_mut() {
            set.remove(&session.runtime_id);
        }
        Some(session)
    }

    /// Snapshot of (peer, session) pairs — used by the broadcast pass so it can
    /// mutate the known-sets without holding a borrow on `sessions`.
    pub fn session_snapshot(&self) -> Vec<(u32, WorldSession)> {
        self.sessions.iter().map(|(p, s)| (*p, s.clone())).collect()
    }

    /// Stable synthetic `ServerArea` id for an area name (minted on first use).
    pub fn area_id(&mut self, area: &str) -> u32 {
        if let Some(id) = self.area_ids.get(area) {
            return *id;
        }
        self.next_area_id += 1;
        let id = self.next_area_id;
        self.area_ids.insert(area.to_string(), id);
        id
    }

    /// Whether `peer` has already been told about runtime id `rid`.
    pub fn knows(&self, peer: u32, rid: u16) -> bool {
        self.known.get(&peer).is_some_and(|s| s.contains(&rid))
    }

    /// Record that `peer` now knows about runtime id `rid`.
    pub fn mark_known(&mut self, peer: u32, rid: u16) {
        self.known.entry(peer).or_default().insert(rid);
    }

    /// Record a peer's last attack time (combat-delay gate).
    pub fn set_last_attack(&mut self, peer: u32, now_ms: u64) {
        if let Some(s) = self.sessions.get_mut(&peer) {
            s.last_attack_ms = now_ms;
        }
    }

    /// Forget a runtime id from every peer's known-set (when an NPC dies, so a
    /// reused id is re-introduced).
    pub fn forget_runtime(&mut self, rid: u16) {
        for set in self.known.values_mut() {
            set.remove(&rid);
        }
    }

    /// Clear everything `peer` knows — on a zone change, so it is re-introduced
    /// to the new area's actors by the next broadcast pass.
    pub fn clear_known(&mut self, peer: u32) {
        if let Some(set) = self.known.get_mut(&peer) {
            set.clear();
        }
    }

    /// Arm the warp-update suppression window (`AI\IgnoreUpdate = 1`): drop the
    /// peer's inbound `P_StandardUpdate`s until `deadline_ms` or the client's
    /// warp-completion ack, whichever comes first (see the field doc).
    pub fn set_ignore_update_until(&mut self, peer: u32, deadline_ms: u64) {
        if let Some(s) = self.sessions.get_mut(&peer) {
            s.ignore_update_until_ms = deadline_ms;
        }
    }

    /// Warp-completion ack (`AI\IgnoreUpdate = 0`, `ServerNet.bb:730/:737`) —
    /// the client finished applying a `P_ChangeArea` / `P_RepositionActor`, so
    /// its standard updates are trustworthy again.
    pub fn clear_ignore_update(&mut self, peer: u32) {
        if let Some(s) = self.sessions.get_mut(&peer) {
            s.ignore_update_until_ms = 0;
        }
    }

    /// Record which portal a peer currently occupies (portal edge-detection).
    pub fn set_in_portal(&mut self, peer: u32, portal: Option<(String, String)>) {
        if let Some(s) = self.sessions.get_mut(&peer) {
            s.in_portal = portal;
        }
    }

    /// Move a peer's session to a new area + position (a warp / `SetArea`).
    /// Sets the authoritative position and zeroes movement intent so the actor
    /// arrives standing still. Returns false if the peer has no live session.
    pub fn warp_session(&mut self, peer: u32, area: String, x: f32, y: f32, z: f32) -> bool {
        match self.sessions.get_mut(&peer) {
            Some(s) => {
                s.area = area;
                s.x = clamp_world_coord(x);
                s.y = clamp_world_coord(y);
                s.z = clamp_world_coord(z);
                s.dest_x = s.x;
                s.dest_z = s.z;
                s.is_running = 0;
                s.walking_backward = 0;
                true
            }
            None => false,
        }
    }

    /// Set a session's position (same-area script move, `BVM_MOVEACTOR`); dest
    /// snaps to the new position. Returns the session's area for the broadcast,
    /// or `None` if the peer has no session. Coords are pre-clamped by the caller.
    pub fn move_session(&mut self, peer: u32, x: f32, y: f32, z: f32) -> Option<String> {
        let s = self.sessions.get_mut(&peer)?;
        s.x = x;
        s.y = y;
        s.z = z;
        s.dest_x = x;
        s.dest_z = z;
        Some(s.area.clone())
    }

    /// Set a session's movement destination only (`BVM_SETACTORDESTINATION`).
    pub fn set_session_dest(&mut self, peer: u32, x: f32, z: f32) {
        if let Some(s) = self.sessions.get_mut(&peer) {
            s.dest_x = x;
            s.dest_z = z;
        }
    }

    /// Set (or clear with 0) the mount this player is riding.
    pub fn set_mount(&mut self, peer: u32, mount_rid: u16) {
        if let Some(s) = self.sessions.get_mut(&peer) {
            s.mount_rid = mount_rid;
        }
    }

    /// The mount runtime id a player is riding (0 = not mounted).
    pub fn mount_of(&self, peer: u32) -> u16 {
        self.sessions.get(&peer).map(|s| s.mount_rid).unwrap_or(0)
    }

    /// Set the player's current target (`AI\AITarget`); 0 clears it.
    pub fn set_player_target(&mut self, peer: u32, rid: u16) {
        if let Some(s) = self.sessions.get_mut(&peer) {
            s.target_rid = rid;
        }
    }

    /// The target a player runtime id currently has (`AI\AITarget`), or 0.
    pub fn target_of_runtime(&self, rid: u16) -> u16 {
        self.sessions.values().find(|s| s.runtime_id == rid).map(|s| s.target_rid).unwrap_or(0)
    }

    /// Is this NPC runtime id currently being ridden by some player?
    pub fn is_ridden(&self, npc_rid: u16) -> bool {
        npc_rid != 0 && self.sessions.values().any(|s| s.mount_rid == npc_rid)
    }

    /// The runtime id of the player riding this NPC (0 = unridden) — the
    /// inverse of the rider's `mount_rid` link, for the `ActorRider` BVM.
    pub fn rider_of(&self, npc_rid: u16) -> u16 {
        if npc_rid == 0 {
            return 0;
        }
        self.sessions
            .values()
            .find(|s| s.mount_rid == npc_rid)
            .map(|s| s.runtime_id)
            .unwrap_or(0)
    }

    /// The rider peer + position for a ridden NPC (for the per-tick glue), if any.
    pub fn rider_pos_of(&self, npc_rid: u16) -> Option<(f32, f32, f32)> {
        if npc_rid == 0 {
            return None;
        }
        self.sessions.values().find(|s| s.mount_rid == npc_rid).map(|s| (s.x, s.y, s.z))
    }

    /// Clear the mount of any rider riding this NPC (called when the NPC is
    /// freed/killed, so a dead mount's id doesn't dangle on the wire — parity
    /// with Blitz clearing `Rider` on `FreeActorInstance`).
    pub fn clear_riders_of(&mut self, npc_rid: u16) {
        if npc_rid == 0 {
            return;
        }
        for s in self.sessions.values_mut() {
            if s.mount_rid == npc_rid {
                s.mount_rid = 0;
            }
        }
    }

    /// Each ridden NPC's runtime id paired with its rider's current position —
    /// for the per-tick mount glue (`GameServer.bb:746`, mount snaps to rider).
    pub fn mounts_with_rider_pos(&self) -> Vec<(u16, f32, f32, f32)> {
        self.sessions
            .values()
            .filter(|s| s.mount_rid != 0)
            .map(|s| (s.mount_rid, s.x, s.y, s.z))
            .collect()
    }

    /// Allocate a runtime id from the shared pool (players + NPCs).
    pub fn alloc_runtime(&mut self) -> u16 {
        self.allocator.alloc()
    }

    /// Release a runtime id back to the shared pool.
    pub fn free_runtime(&mut self, rid: u16) {
        self.allocator.free(rid);
    }

    /// The distinct areas that currently have at least one online player.
    pub fn active_areas(&self) -> Vec<String> {
        let mut areas: Vec<String> = self.sessions.values().map(|s| s.area.clone()).collect();
        areas.sort();
        areas.dedup();
        areas
    }
}

fn read_field(r: &mut MsgReader) -> Option<Vec<u8>> {
    let n = r.u8()? as usize;
    Some(r.bytes(n)?.to_vec())
}

/// Handle `P_StartGame` (`ServerNet.bb:2121`). Inbound: `[str user][str pass]
/// [u8 slot]`. On success: establishes the session and returns the action-bar
/// packets + runtime-id + login-message + XP-bar replies. Otherwise `"N"`.
pub fn handle_start_game(
    payload: &[u8],
    store: &mut AccountStore,
    throttle: &mut LoginThrottle,
    world: &mut World,
    config: &ServerConfig,
    peer: u32,
    now_ms: u64,
) -> Vec<(u8, Vec<u8>)> {
    let fail = || vec![(P_START_GAME, b"N".to_vec())];

    let mut r = MsgReader::new(payload);
    let Some(user) = read_field(&mut r) else {
        password::verify_password("", "");
        throttle.record(peer, false, now_ms);
        return fail();
    };
    let pass = read_field(&mut r).unwrap_or_default();
    let slot = r.u8();

    if !throttle.ok(peer, now_ms) {
        return fail();
    }
    let user_s = String::from_utf8_lossy(&user).into_owned();
    let pass_s = String::from_utf8_lossy(&pass).into_owned();

    // Already logged in elsewhere → refuse (Blitz gates on `A\LoggedOn = -1`).
    if world.is_logged_on(&user_s) {
        throttle.record(peer, false, now_ms);
        return fail();
    }

    let acct = store.find(&user_s);
    let ok = acct
        .map(|a| !a.is_banned && !a.pass.is_empty() && !pass.is_empty() && password::verify_password(&a.pass, &pass_s))
        .unwrap_or(false);
    if !ok {
        if acct.is_none() || pass.is_empty() {
            password::verify_password("", &pass_s);
        }
        throttle.record(peer, false, now_ms);
        return fail();
    }

    // Slot + character existence.
    let Some(slot) = slot else {
        throttle.record(peer, false, now_ms);
        return fail();
    };
    let acct = store.find(&user_s).expect("verified above");
    if slot >= 10 || (slot as usize) >= acct.characters.len() {
        throttle.record(peer, false, now_ms);
        return fail();
    }
    let rec = &acct.characters[slot as usize];
    let actor = &rec.actor;

    // The saved area must still exist (`FindArea = Null → refuse`).
    if Area::load(&config.data_dir, &actor.area).is_none() {
        throttle.record(peer, false, now_ms);
        return fail();
    }

    // Allocate the runtime id and establish the session.
    let runtime_id = world.allocator.alloc();
    world.sessions.insert(
        peer,
        WorldSession {
            user: user_s.clone(),
            char_slot: slot,
            runtime_id,
            area: actor.area.clone(),
            x: actor.x,
            y: actor.y,
            z: actor.z,
            dest_x: actor.x,
            dest_z: actor.z,
            is_running: 0,
            walking_backward: 0,
            last_attack_ms: 0,
            in_portal: None,
            mount_rid: 0,
            target_rid: 0,
            ignore_update_until_ms: 0,
        },
    );
    world.logged_on.insert(user_s.to_uppercase(), peer);
    throttle.record(peer, true, now_ms);

    // Build the replies.
    let mut out: Vec<(u8, Vec<u8>)> = Vec::new();

    // P_ChangeArea FIRST — parity with `SetArea` on login (`ServerNet.bb:2187`).
    // This tells the client which zone to load + render and spawns the player
    // into the world; without it the client shows no terrain and never starts
    // the actor/update loop (the "no terrain, frozen actors" symptom).
    {
        let area = Area::load(&config.data_dir, &actor.area);
        let (pvp, gravity) = area.as_ref().map(|a| (a.pvp, a.gravity)).unwrap_or((0, 0));
        let server_area = world.area_id(&actor.area);
        out.push((
            P_CHANGE_AREA,
            change_area_payload(actor.x, actor.y, actor.z, 0.0, pvp, gravity, server_area, 0, &actor.area),
        ));
    }

    // Action bar: 12 packets, each `[u8 startSlot][3× (u16 len + slot bytes)]`.
    let mut group: Vec<u8> = Vec::new();
    for i in 0..ACTION_BAR_SLOTS {
        let s = rec.action_bar.get(i).map(String::as_str).unwrap_or("");
        group.extend_from_slice(&(s.len() as u16).to_le_bytes());
        group.extend_from_slice(s.as_bytes());
        if (i + 1) % 3 == 0 {
            let mut p = vec![(i - 2) as u8]; // start slot of this group
            p.extend_from_slice(&group);
            out.push((P_START_GAME, p));
            group.clear();
        }
    }

    // Runtime id (the client's own actor handle) — the 2-byte main reply.
    out.push((P_START_GAME, runtime_id.to_le_bytes().to_vec()));

    // Login message + XP-bar level.
    let login_message = std::env::var("RCCE_LOGIN_MESSAGE").unwrap_or_default();
    let mut chat = vec![254u8];
    chat.extend_from_slice(login_message.as_bytes());
    out.push((P_CHAT_MESSAGE, chat));

    out.push((P_XP_UPDATE, vec![b'B', actor.xp_bar_level]));

    out
}

/// Handle `P_StandardUpdate` (`ServerNet.bb:1817`) — the client's per-frame
/// position report. Inbound (little-endian floats): `[f32 destX][f32 destZ]
/// [f32 newY][f32 newX][f32 newZ][u8 isRunning][u8 walkingBackward]`. Updates
/// the peer's authoritative position; no direct reply (the position is relayed
/// to other players by the per-tick broadcast — next phase).
///
/// Deferred vs. Blitz: the per-packet speed-hack clamp (bounds the position
/// delta by the actor's Speed attribute × elapsed time) needs per-actor timing
/// + the Speed stat; for now positions are only `ClampWorldCoord`-sanitised.
pub fn handle_standard_update(
    payload: &[u8],
    world: &mut World,
    peer: u32,
    now_ms: u64,
) -> Vec<(u8, Vec<u8>)> {
    // Only an in-world player may move; ignore otherwise.
    let Some(sess) = world.session(peer) else {
        return Vec::new();
    };
    // Warp-update suppression (`If AI\IgnoreUpdate = 0`, `ServerNet.bb:1821`):
    // while the client is completing a warp its in-flight updates carry the
    // stale pre-warp position — don't let them yank the actor back. Still echo
    // the (post-warp) authoritative position so the client reconciles onto it.
    if sess.ignore_update_until_ms > now_ms {
        return vec![(P_STANDARD_UPDATE, standard_update_to_wire(sess))];
    }
    let mut r = MsgReader::new(payload);
    // [destX][destZ][newY][newX][newZ][isRunning][walkingBackward].
    let dest_x = r.f32();
    let dest_z = r.f32();
    let new_y = r.f32();
    let new_x = r.f32();
    let new_z = r.f32();
    let is_running = r.u8().unwrap_or(0);
    let walking_backward = r.u8().unwrap_or(0);
    if let (Some(y), Some(x), Some(z), Some(dx), Some(dz)) = (new_y, new_x, new_z, dest_x, dest_z) {
        world.update_movement(peer, x, y, z, dx, dz, is_running, walking_backward);
    }
    // Echo the authoritative position back to the SENDER. The client reconciles
    // its own `me_x`/`me_z` from this echo (`ClientNet`/`on_standard_update`,
    // `rid == my_runtime_id`); without it the local player snaps back to spawn
    // every frame ("stuck in place as if the server were offline"). The Blitz
    // server's per-tick update includes the actor's own client.
    match world.session(peer) {
        Some(s) => vec![(P_STANDARD_UPDATE, standard_update_to_wire(s))],
        None => Vec::new(),
    }
}

/// Handle `P_ChatMessage` (`ServerNet.bb:186`) — the general "say" channel.
/// Inbound: raw message text. A non-empty message that isn't a `/`…`\` command
/// is wrapped as `"<Name> message"` and broadcast to every player in the
/// sender's area (the sender included, matching the Blitz `FirstInZone` walk).
///
/// Deferred: the `/`…`\` command family (kick, ignore, party, warp, DM tools,
/// the `In-game Commands` script dispatch) — those need the BVM engine + a
/// command table; a command currently produces no broadcast.
pub fn handle_chat_message(
    payload: &[u8],
    world: &World,
    store: &AccountStore,
    peer: u32,
) -> Vec<crate::state::Outgoing> {
    let Some(sess) = world.session(peer) else {
        return Vec::new();
    };
    if payload.is_empty() {
        return Vec::new();
    }
    // Commands deferred (need the BVM engine / command table).
    if payload[0] == b'/' || payload[0] == b'\\' {
        return Vec::new();
    }
    let msg = String::from_utf8_lossy(payload);
    let name = store
        .find(&sess.user)
        .and_then(|a| a.characters.get(sess.char_slot as usize))
        .map(|r| r.actor.name.clone())
        .unwrap_or_default();
    let line = format!("<{name}> {msg}").into_bytes();
    let area = sess.area.clone();

    world
        .session_snapshot()
        .into_iter()
        .filter(|(_, s)| s.area == area)
        .map(|(p, _)| crate::state::Outgoing::peer(p, P_CHAT_MESSAGE, line.clone()))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use rcce_server_accounts::store::Account;
    use rcce_server_core::character::Character;
    use rcce_server_core::record::CharacterRecord;
    use std::path::PathBuf;

    const MD5: &str = "5d41402abc4b2a76b9719d911017c592";

    fn data_dir() -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../data")
    }

    fn config_for(dir: PathBuf) -> ServerConfig {
        ServerConfig {
            port: 25000,
            allow_account_creation: true,
            max_account_chars: 4,
            start_gold: 5000,
            start_reputation: 50,
            attribute_assignment: 0,
            data_dir: dir,
        }
    }

    fn tmp_store(name: &str) -> AccountStore {
        let mut p = std::env::temp_dir();
        p.push(format!("rcce_startgame_test_{name}.dat"));
        let _ = std::fs::remove_file(&p);
        AccountStore::load(&p).unwrap()
    }

    fn field(b: &[u8]) -> Vec<u8> {
        let mut v = vec![b.len() as u8];
        v.extend_from_slice(b);
        v
    }

    /// Build an account whose character is in a real, loadable start area.
    fn account_in_real_area(dir: &std::path::Path) -> Option<Account> {
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let template = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(dir, &t.start_area).is_some())?;
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template.id;
        c.name = "Boromir".into();
        c.area = template.start_area.clone();
        c.xp_bar_level = 3;
        acct.characters.push(CharacterRecord::new(c));
        Some(acct)
    }

    fn start_packet(user: &str, md5: &str, slot: u8) -> Vec<u8> {
        let mut p = field(user.as_bytes());
        p.extend_from_slice(&field(md5.as_bytes()));
        p.push(slot);
        p
    }

    #[test]
    fn start_game_enters_world_and_assigns_runtime_id() {
        let dir = data_dir();
        let Some(acct) = account_in_real_area(&dir) else {
            eprintln!("skipping: no playable race with loadable start area");
            return;
        };
        let mut store = tmp_store("enter");
        store.push(acct);
        let mut throttle = LoginThrottle::new();
        let mut world = World::new();
        let config = config_for(dir);

        let replies = handle_start_game(
            &start_packet("hero", MD5, 0),
            &mut store,
            &mut throttle,
            &mut world,
            &config,
            7,
            0,
        );
        // Session established for peer 7.
        assert!(world.is_logged_on("hero"));
        assert_eq!(world.online_count(), 1);
        let sess = world.session(7).unwrap();
        assert_eq!(sess.char_slot, 0);

        // P_ChangeArea (zone load) + 12 action-bar packets + runtime-id + chat
        // + xp = 16 replies.
        assert_eq!(replies.len(), 1 + 12 + 1 + 1 + 1);
        // The first reply is the P_ChangeArea that loads the player's zone.
        assert_eq!(replies[0].0, P_CHANGE_AREA);
        // The runtime-id reply is the 2-byte P_StartGame packet matching the
        // session's runtime id (after the change-area + 12 action-bar packets).
        let rid_reply = &replies[13];
        assert_eq!(rid_reply.0, P_START_GAME);
        assert_eq!(rid_reply.1, sess.runtime_id.to_le_bytes().to_vec());
        // XP-bar reply carries the character's level (3).
        assert_eq!(replies.last().unwrap(), &(P_XP_UPDATE, vec![b'B', 3]));
    }

    #[test]
    fn actor_to_wire_layout_is_byte_exact() {
        let mut c = Character::blank();
        c.actor_id = 0x0708;
        c.name = "Hero".into();
        c.tag = "tg".into();
        c.level = 5;
        c.xp = 1000;
        c.gender = 1;
        c.reputation = -7;
        c.face_tex = 2;
        c.home_faction = 3;
        c.attributes.value[0] = 80; // health value (health_stat=0)
        c.attributes.maximum[0] = 100;
        c.attributes.value[4] = 12; // speed value (speed_stat=4)
        c.attributes.maximum[4] = 20;
        c.inventory[0].item = Some(rcce_server_core::ItemInstance::new(42)); // weapon
        c.faction_ratings[0] = 200;

        // genders==0 → gender byte included.
        let bytes = actor_to_wire(0xAABBCCDD, 0x1234, 1.0, 2.0, 3.0, 0.0, true, &c, 0, 0, 4);

        // ServerArea (u32 LE), RuntimeID (u16 LE), Level (u16), XP (i32), ActorID (u16).
        assert_eq!(&bytes[0..4], &0xAABBCCDDu32.to_le_bytes());
        assert_eq!(&bytes[4..6], &0x1234u16.to_le_bytes());
        assert_eq!(&bytes[6..8], &5u16.to_le_bytes());
        assert_eq!(&bytes[8..12], &1000i32.to_le_bytes());
        assert_eq!(&bytes[12..14], &0x0708u16.to_le_bytes());
        // X/Y/Z/Yaw floats.
        assert_eq!(&bytes[14..18], &1.0f32.to_le_bytes());
        assert_eq!(&bytes[26..30], &0.0f32.to_le_bytes());
        // isPlayer, then name (str8), tag (str8).
        let mut o = 30;
        assert_eq!(bytes[o], 1);
        o += 1;
        assert_eq!(bytes[o], 4);
        assert_eq!(&bytes[o + 1..o + 5], b"Hero");
        o += 5;
        assert_eq!(bytes[o], 2);
        assert_eq!(&bytes[o + 1..o + 3], b"tg");
        o += 3;
        // gender byte (genders==0), then reputation i16.
        assert_eq!(bytes[o], 1);
        o += 1;
        assert_eq!(&bytes[o..o + 2], &(-7i16 as u16).to_le_bytes());

        // The 100 faction ratings are the last 100 bytes; home_faction precedes them.
        assert_eq!(bytes[bytes.len() - 101], 3); // home_faction
        assert_eq!(bytes[bytes.len() - 100], 200); // faction_ratings[0]

        // genders!=0 → no gender byte → one byte shorter.
        let bytes_no_gender = actor_to_wire(0, 0, 0.0, 0.0, 0.0, 0.0, true, &c, 1, 0, 4);
        assert_eq!(bytes_no_gender.len(), bytes.len() - 1);
    }

    #[test]
    fn standard_update_moves_the_session() {
        let dir = data_dir();
        let Some(acct) = account_in_real_area(&dir) else {
            return;
        };
        let mut store = tmp_store("move");
        store.push(acct);
        let mut throttle = LoginThrottle::new();
        let mut world = World::new();
        let config = config_for(dir);
        handle_start_game(&start_packet("hero", MD5, 0), &mut store, &mut throttle, &mut world, &config, 7, 0);

        // Build a P_StandardUpdate body: destX, destZ, newY, newX, newZ, run, back.
        let mut p = Vec::new();
        for f in [10.0f32, 20.0, 5.0, 30.0, 40.0] {
            p.extend_from_slice(&f.to_le_bytes());
        }
        p.push(1); // is_running
        p.push(0); // walking_backward
        let reply = handle_standard_update(&p, &mut world, 7, 0);
        // The server echoes the authoritative position back to the sender so the
        // client reconciles its own me_x/me_z (without this the player is stuck).
        assert_eq!(reply.len(), 1);
        assert_eq!(reply[0].0, P_STANDARD_UPDATE);
        // Echo carries [u16 rid][f32 x=30][f32 z=40]…
        let echo = &reply[0].1;
        let rid = world.session(7).unwrap().runtime_id;
        assert_eq!(u16::from_le_bytes([echo[0], echo[1]]), rid);
        assert_eq!(f32::from_le_bytes([echo[2], echo[3], echo[4], echo[5]]), 30.0);
        assert_eq!(f32::from_le_bytes([echo[6], echo[7], echo[8], echo[9]]), 40.0);

        let s = world.session(7).unwrap();
        assert_eq!(s.x, 30.0);
        assert_eq!(s.y, 5.0);
        assert_eq!(s.z, 40.0);
    }

    #[test]
    fn two_players_in_same_area_get_introduced_once() {
        use crate::state::ServerState;
        let dir = data_dir();
        let Some(_) = account_in_real_area(&dir) else {
            return;
        };
        // Two accounts, each with a character in the same real start area.
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let template = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
            .unwrap();
        let mut store = tmp_store("introduce");
        for user in ["alice", "bob"] {
            let mut acct = Account::new(user, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template.id;
            c.name = user.to_string();
            c.area = template.start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let config = config_for(dir.clone());
        let mut state = ServerState::new(config, store, catalog);

        // Both enter the world (peers 1 and 2).
        let now = 0;
        let a = world_login(&mut state, "alice", 1, now);
        assert!(a, "alice entered");
        let b = world_login(&mut state, "bob", 2, now);
        assert!(b, "bob entered");

        // First broadcast: alice introduced to bob and bob to alice (plus any
        // NPCs in the zone). Every packet is a P_NewActor.
        let intros = state.collect_world_broadcasts();
        assert!(intros.iter().all(|(_, t, _)| *t == P_NEW_ACTOR));
        // The runtime id is at bytes [4..6] (after the 4-byte ServerArea).
        let rid_of = |w: &[u8]| u16::from_le_bytes([w[4], w[5]]);
        let alice_rid = state.world.session(1).unwrap().runtime_id;
        let bob_rid = state.world.session(2).unwrap().runtime_id;
        // bob (peer 2) learns about alice; alice (peer 1) learns about bob.
        assert!(intros.iter().any(|(p, _, w)| *p == 2 && rid_of(w) == alice_rid));
        assert!(intros.iter().any(|(p, _, w)| *p == 1 && rid_of(w) == bob_rid));

        // Idempotent: a second pass introduces nothing new (players + NPCs all known).
        assert!(state.collect_world_broadcasts().is_empty());

        // alice disconnects → her runtime id is forgotten everywhere; if she
        // re-enters she is re-introduced.
        state.on_disconnect(1);
        let _ = world_login(&mut state, "alice", 3, now);
        let reintros = state.collect_world_broadcasts();
        // bob (peer 2) learns about alice's new session (peer 3); alice learns bob.
        assert!(!reintros.is_empty());
    }

    #[test]
    fn position_relay_and_departure() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let mut store = tmp_store("relay");
        for user in ["alice", "bob"] {
            let mut acct = Account::new(user, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template.id;
            c.name = user.to_string();
            c.area = template.start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let config = config_for(dir.clone());
        let mut state = ServerState::new(config, store, catalog);
        world_login(&mut state, "alice", 1, 0);
        world_login(&mut state, "bob", 2, 0);

        // alice moves; the relay sends her position to bob.
        let mut mv = Vec::new();
        for f in [50.0f32, 60.0, 5.0, 70.0, 80.0] {
            mv.extend_from_slice(&f.to_le_bytes());
        }
        mv.push(1); // running
        mv.push(0);
        handle_standard_update(&mv, &mut state.world, 1, 0);

        let relay = state.collect_position_broadcasts();
        // Each of the 2 players relays to the 1 other → 2 packets.
        assert_eq!(relay.len(), 2);
        // The packet to bob (peer 2) carries alice's runtime id + new X (70).
        let to_bob = relay.iter().find(|(p, _, _)| *p == 2).unwrap();
        assert_eq!(to_bob.1, P_STANDARD_UPDATE);
        let alice_rid = state.world.session(1).unwrap().runtime_id;
        assert_eq!(&to_bob.2[0..2], &alice_rid.to_le_bytes());
        assert_eq!(&to_bob.2[2..6], &70.0f32.to_le_bytes()); // X
        assert_eq!(&to_bob.2[6..10], &80.0f32.to_le_bytes()); // Z

        // alice disconnects → bob gets a P_ActorGone with alice's runtime id (4 bytes).
        let gone = state.on_disconnect(1);
        assert_eq!(gone.len(), 1);
        assert_eq!(gone[0].0, 2); // to bob
        assert_eq!(gone[0].1, P_ACTOR_GONE);
        assert_eq!(gone[0].2, (alice_rid as u32).to_le_bytes().to_vec());
    }

    #[test]
    fn player_attacks_and_kills_an_npc() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let mut store = tmp_store("attack");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template.id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        c.level = 1;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);

        // Enter the world; this populates the area's NPCs (via a broadcast pass).
        let pkt = start_packet("hero", MD5, 0);
        let replies = handle_start_game(
            &pkt, &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0,
        );
        assert!(replies.len() > 1, "entered world");
        let _ = state.collect_world_broadcasts(); // populate NPCs

        // Find an NPC in the player's area.
        let area = state.world.session(1).unwrap().area.clone();
        // Spawn an NPC whose template has a positive XP multiplier, co-located
        // with the hero, so the kill deterministically awards XP. (Relying on the
        // area's first NPC was flaky: a 0-multiplier NPC's only XP is the random
        // 0..20 term, which can roll 0 → a spurious `xp > 0` failure.)
        let Some(mob_id) = state.catalog.templates.values().find(|t| t.xp_multiplier > 0).map(|t| t.id)
        else {
            eprintln!("skipping: no template with a positive XP multiplier");
            return;
        };
        let npc_rid = state.world.alloc_runtime();
        let start_hp = 5i32;
        state.spawns.insert_npc(crate::spawn::NpcActor {
            runtime_id: npc_rid, actor_id: mob_id, area: area.clone(),
            x: 0.0, y: 0.0, z: 0.0, hp: start_hp, hp_max: start_hp,
            target_peer: None, last_attack_ms: 0,
            script: String::new(), death_script: String::new(), stock: Vec::new(),
        });
        // A defender's resistance must feed the live melee path, not merely the
        // pure formula. Make any landed hit deterministic: 30,000 mitigation
        // floors it to one point, while an ignored resistance would deal far more.
        state.accounts.find_mut("hero").unwrap().characters[0].actor.attributes.value[state.strength_stat] = 30_000;
        state.catalog.templates.get_mut(&mob_id).unwrap().resistances.fill(30_000);

        // Attack repeatedly (advancing the clock past the combat delay each time)
        // until the NPC dies. Min damage is 1, so this always terminates.
        let attack = {
            let mut p = vec![];
            p.extend_from_slice(&npc_rid.to_le_bytes());
            p
        };
        let mut killed = false;
        let mut now = 0u64;
        for _ in 0..(start_hp.max(1) as u64 + 5) {
            now += state.combat_delay as u64 + 1;
            let outs = state.handle_attack(1, &attack, now);
            // Every swing yields at least the "H" feedback to the attacker.
            assert!(outs.iter().any(|o| o.msg_type == P_ATTACK_ACTOR));
            let damage = outs
                .iter()
                .find(|o| o.msg_type == P_ATTACK_ACTOR && o.payload.first() == Some(&b'H'))
                .map(|o| u16::from_le_bytes([o.payload[3], o.payload[4]]).saturating_sub(1))
                .unwrap();
            if damage > 0 {
                assert_eq!(damage, 1, "NPC defender resistance applies to a live hit");
            }
            if outs.iter().any(|o| o.msg_type == P_ACTOR_DEAD) {
                killed = true;
                break;
            }
        }
        assert!(killed, "the NPC (hp {start_hp}) should die under repeated attacks");
        // The NPC is removed.
        assert!(state.spawns.npc(npc_rid).is_none());
        // The killer earned XP.
        assert!(state.accounts.find("hero").unwrap().characters[0].actor.xp > 0);
    }

    #[test]
    fn killing_an_npc_fires_its_death_script() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Click_Test.rsl").exists() {
            eprintln!("skipping: no Click_Test.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("death_script");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        c.level = 1;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Click_Test").is_none() {
            eprintln!("skipping: Click_Test.rsl did not parse");
            return;
        }
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();

        // Stage a low-HP, co-located NPC carrying a death script.
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: template_id,
            area,
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 1,
            hp_max: 1,
            target_peer: None,
            last_attack_ms: 0,
            script: String::new(),
            death_script: "Click_Test".into(),
            stock: Vec::new(),
        });

        let attack = npc_rid.to_le_bytes().to_vec();
        let mut now = 0u64;
        let mut killed = false;
        for _ in 0..16 {
            now += state.combat_delay as u64 + 1;
            let outs = state.handle_attack(1, &attack, now);
            if outs.iter().any(|o| o.msg_type == P_ACTOR_DEAD) {
                killed = true;
                break;
            }
        }
        assert!(killed, "the hp-1 NPC should die");
        // Two async scripts spawn on a kill now: the NPC's death script
        // (Click_Test) AND the LevelUp script the XP grant fires (GiveXP →
        // ThreadScript "LevelUp"). Both are real running scripts.
        assert_eq!(state.running_script_count(), 2, "death + LevelUp scripts fire on kill");
    }

    #[test]
    fn npc_retaliates_and_damages_player() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let mut store = tmp_store("retaliate");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template.id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        c.attributes.value[0] = 100; // health slot (shipped default Health = 0)
        c.attributes.maximum[0] = 100;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);

        let pkt = start_packet("hero", MD5, 0);
        handle_start_game(&pkt, &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let _ = state.collect_world_broadcasts(); // spawn NPCs

        let area = state.world.session(1).unwrap().area.clone();
        let Some(npc_rid) = state.spawns.npcs_in_area(&area).map(|n| n.runtime_id).next() else {
            eprintln!("skipping: no NPCs");
            return;
        };

        // Put the player in melee range of the NPC (NPCs only swing once within
        // CheckDist — see the range gate in collect_npc_attacks), then make the
        // NPC retaliate and run the AI tick.
        let npos = state.spawns.npc(npc_rid).map(|n| (n.x, n.y, n.z)).unwrap();
        state.world.warp_session(1, area.clone(), npos.0, npos.1, npos.2);
        let npc_actor_id = state.spawns.npc(npc_rid).unwrap().actor_id;
        state.catalog.templates.get_mut(&npc_actor_id).unwrap().attr_value[state.strength_stat] = 30_000;
        state.accounts.find_mut("hero").unwrap().characters[0].actor.resistances.fill(30_000);
        state.spawns.set_target(npc_rid, 1);
        let hp_before = state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[state.health_stat];
        let outs = state.collect_npc_attacks(state.combat_delay as u64 + 1);

        // The player gets an HP update + a "Y" damage packet.
        assert!(outs.iter().any(|o| o.msg_type == P_STAT_UPDATE), "HP update broadcast");
        assert!(
            outs.iter().any(|o| o.msg_type == P_ATTACK_ACTOR && o.payload.first() == Some(&b'Y')),
            "damage feedback to the victim"
        );
        // The player's HP did not increase (it dropped, unless the NPC missed).
        let mut hp_after = state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[state.health_stat];
        for tick in 2..=40 {
            if hp_after < hp_before {
                break;
            }
            state.collect_npc_attacks(tick * (state.combat_delay as u64 + 1));
            hp_after = state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[state.health_stat];
        }
        assert_eq!(hp_after, hp_before - 1, "player defender resistance applies to an NPC live hit");
    }

    #[test]
    fn npc_chases_distant_target_and_holds_fire_until_in_range() {
        use crate::spawn::NpcActor;
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("chase");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        c.attributes.value[0] = 100;
        c.attributes.maximum[0] = 100;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        // Park the player at a known origin.
        state.world.warp_session(1, area.clone(), 0.0, 0.0, 0.0);

        // Stage an NPC 100 units away, already targeting the player.
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: template_id,
            area: area.clone(),
            x: 100.0,
            y: 0.0,
            z: 0.0,
            hp: 100,
            hp_max: 100,
            target_peer: Some(1),
            last_attack_ms: 0,
            script: String::new(),
            death_script: String::new(),
            stock: Vec::new(),
        });

        // Far away → the swing is held; the NPC steps closer instead.
        let attacks = state.collect_npc_attacks(state.combat_delay as u64 + 1);
        assert!(
            !attacks.iter().any(|o| o.payload.first() == Some(&b'Y')),
            "no damage lands while the target is out of melee range"
        );
        let x0 = state.spawns.npc(npc_rid).unwrap().x;
        let moves = state.collect_npc_movement();
        let x1 = state.spawns.npc(npc_rid).unwrap().x;
        assert!(x1 < x0, "NPC stepped toward the player (x {x0} -> {x1})");
        assert!(
            moves.iter().any(|o| o.msg_type == P_STANDARD_UPDATE),
            "the chase step is broadcast so clients animate it"
        );

        // Drive movement until the NPC reaches melee range (bounded loop).
        for _ in 0..2000 {
            state.collect_npc_movement();
            let n = state.spawns.npc(npc_rid).unwrap();
            let dx = n.x - 0.0;
            if dx * dx <= 64.0 {
                break;
            }
        }
        // Now in range → the swing lands.
        let attacks2 = state.collect_npc_attacks(state.combat_delay as u64 * 4 + 1);
        assert!(
            attacks2.iter().any(|o| o.payload.first() == Some(&b'Y')),
            "the NPC swings once it has closed to melee range"
        );
    }

    #[test]
    fn actor_effect_bvms_apply_and_revert() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("effectbvm");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let hidx = state.health_stat;
        let Some(attr) = state.attr_names.name(hidx).map(str::to_string) else {
            eprintln!("skipping: no attribute name at the health slot");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;
        // Known baseline at the affected attribute.
        state.accounts.find_mut("hero").unwrap().characters[0].actor.attributes.value[hidx] = 100;
        let value_now = |state: &ServerState| state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[hidx];

        let pump = |state: &mut ServerState| -> Vec<crate::state::Outgoing> {
            let mut all = Vec::new();
            for _ in 0..200 {
                all.extend(state.pump_scripts());
                if state.running_script_count() == 0 {
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
            all
        };

        // AddActorEffect → +10 to the attribute + a P_ActorEffect 'A' icon.
        let add = format!("Function Main()\n\tp = Actor()\n\tAddActorEffect(p, \"Bless\", \"{attr}\", 10, 60, 0)\nEnd Function\n");
        state.start_inline_script(&add, "Main", rid, 0, 1, true);
        let outs = pump(&mut state);
        assert_eq!(value_now(&state), 110, "the effect shifted the attribute by +10");
        assert!(
            outs.iter().any(|o| o.msg_type == P_ACTOR_EFFECT && o.payload.first() == Some(&b'A')),
            "AddActorEffect broadcasts the buff icon"
        );

        // DeleteActorEffect → reverts the delta + a P_ActorEffect 'R'.
        let del = "Function Main()\n\tp = Actor()\n\tDeleteActorEffect(p, \"Bless\")\nEnd Function\n";
        state.start_inline_script(del, "Main", rid, 0, 1, true);
        let outs2 = pump(&mut state);
        assert_eq!(value_now(&state), 100, "deleting the effect reverts the delta");
        assert!(
            outs2.iter().any(|o| o.msg_type == P_ACTOR_EFFECT && o.payload.first() == Some(&b'R')),
            "DeleteActorEffect broadcasts the removal"
        );
    }

    #[test]
    fn move_rotate_bvms_reposition_and_respect_self_gate() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("moveactor");
        for name in ["hero", "ally"] {
            let mut acct = Account::new(name, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = name.into();
            c.area = start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("ally", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);
        let hero_rid = state.world.session(1).unwrap().runtime_id;
        let ally_rid = state.world.session(2).unwrap().runtime_id;

        let pump = |state: &mut ServerState| -> Vec<crate::state::Outgoing> {
            let mut all = Vec::new();
            for _ in 0..200 {
                all.extend(state.pump_scripts());
                if state.running_script_count() == 0 {
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
            all
        };

        // Hero (unprivileged) moves + rotates ITSELF — allowed via the self gate.
        let self_src = "Function Main()\n\tp = Actor()\n\tMoveActor(p, 50.0, 0.0, 60.0)\n\tRotateActor(p, 1.5)\nEnd Function\n";
        state.start_inline_script(self_src, "Main", hero_rid, 0, 1, false);
        let outs = pump(&mut state);
        let s = state.world.session(1).unwrap();
        assert!((s.x - 50.0).abs() < 0.5 && (s.z - 60.0).abs() < 0.5, "hero moved to (50,60); got ({},{})", s.x, s.z);
        // The same-area ally is told via P_RepositionActor 'M' and 'R' for hero.
        let reposn: Vec<&crate::state::Outgoing> = outs
            .iter()
            .filter(|o| o.msg_type == P_REPOSITION_ACTOR && matches!(o.target, crate::state::Target::Peer(2)))
            .collect();
        assert!(reposn.iter().any(|o| o.payload.first() == Some(&b'M')), "ally gets the move");
        assert!(reposn.iter().any(|o| o.payload.first() == Some(&b'R')), "ally gets the rotate");

        // Hero (unprivileged) tries to move the ALLY — refused by the self gate.
        let ally_x0 = state.world.session(2).unwrap().x;
        let evil_src = format!("Function Main()\n\tMoveActor({ally_rid}, 999.0, 0.0, 999.0)\nEnd Function\n");
        state.start_inline_script(&evil_src, "Main", hero_rid, 0, 1, false);
        pump(&mut state);
        assert_eq!(
            state.world.session(2).unwrap().x,
            ally_x0,
            "an unprivileged script cannot move another actor"
        );
    }

    #[test]
    fn party_forms_and_shares_xp() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("party");
        for name in ["Hero", "Ally"] {
            let mut acct = Account::new(name, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = name.into();
            c.area = start_area.clone();
            c.level = 5; // high enough that LevelUp won't reset xp
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("Hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("Ally", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);
        let hero_rid = state.world.session(1).unwrap().runtime_id;

        // Hero parties with Ally via the built-in command.
        let outs = state.dispatch(1, P_CHAT_MESSAGE, b"/party Ally");
        assert!(
            outs.iter().any(|o| o.msg_type == P_CHAT_MESSAGE && String::from_utf8_lossy(&o.payload).contains("partied")),
            "the /party command confirms the join"
        );

        // A script reads the party size + grants party XP (split 5/5).
        let src = format!("Function Main()\n\tp = Actor()\n\tSetActorGlobal(p, 0, CountPartyMembers(p))\n\tGiveXP({hero_rid}, 10, 1)\nEnd Function\n");
        state.start_inline_script(&src, "Main", hero_rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert_eq!(state.accounts.find("Hero").unwrap().characters[0].actor.script_globals[0], "2", "CountPartyMembers is 2");
        assert_eq!(state.accounts.find("Hero").unwrap().characters[0].actor.xp, 5, "Hero got half the XP");
        assert_eq!(state.accounts.find("Ally").unwrap().characters[0].actor.xp, 5, "Ally got the other half");
    }

    // P_PartyUpdate roster broadcast (SendPartyUpdate, ServerNet.bb:3121): forming
    // a party sends every member the OTHER members' names ([len u8][name] each);
    // a member leaving sends the survivors their new (possibly empty) roster.
    #[test]
    fn party_update_broadcasts_roster_on_join_and_disconnect() {
        use crate::state::{ServerState, Target};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("party_roster");
        for name in ["Hero", "Ally"] {
            let mut acct = Account::new(name, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = name.into();
            c.area = start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("Hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("Ally", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);

        // Decode a P_PartyUpdate payload into the list of names it carries.
        let decode = |payload: &[u8]| -> Vec<String> {
            let mut names = Vec::new();
            let mut off = 0usize;
            while off < payload.len() {
                let len = payload[off] as usize;
                off += 1;
                if len == 0 || off + len > payload.len() {
                    break;
                }
                names.push(String::from_utf8_lossy(&payload[off..off + len]).into_owned());
                off += len;
            }
            names
        };

        // Hero parties with Ally → each member gets a roster of the OTHER member.
        let outs = state.dispatch(1, P_CHAT_MESSAGE, b"/party Ally");
        let hero_roster = outs
            .iter()
            .find(|o| o.msg_type == P_PARTY_UPDATE && matches!(o.target, Target::Peer(1)))
            .map(|o| decode(&o.payload));
        let ally_roster = outs
            .iter()
            .find(|o| o.msg_type == P_PARTY_UPDATE && matches!(o.target, Target::Peer(2)))
            .map(|o| decode(&o.payload));
        assert_eq!(hero_roster, Some(vec!["Ally".to_string()]), "Hero's roster lists Ally, not himself");
        assert_eq!(ally_roster, Some(vec!["Hero".to_string()]), "Ally's roster lists Hero, not herself");

        // Ally disconnects → Hero (now alone) receives an updated, empty roster.
        let gone = state.on_disconnect(2);
        let hero_update = gone
            .iter()
            .find(|(p, t, _)| *p == 1 && *t == P_PARTY_UPDATE)
            .map(|(_, _, payload)| decode(payload));
        assert_eq!(
            hero_update,
            Some(Vec::<String>::new()),
            "the lone survivor's roster is cleared when their partner disconnects"
        );
    }

    // Per-area weather (UpdateWeather, ServerAreas.bb:70): the band roll picks
    // weather from WeatherChance; a timer expiry broadcasts P_WeatherChange to the
    // area's players and resets the timer; a warp carries the area's current
    // weather in the P_ChangeArea byte.
    #[test]
    fn weather_rolls_broadcasts_and_rides_change_area() {
        use crate::state::{ServerState, Target};

        // --- roll_weather band selection (pure) ---
        assert_eq!(ServerState::roll_weather(&[100, 0, 0, 0, 0], 1), 1, "band 0 -> weather 1");
        assert_eq!(ServerState::roll_weather(&[100, 0, 0, 0, 0], 99), 1);
        assert_eq!(ServerState::roll_weather(&[50, 50, 0, 0, 0], 10), 1, "first 50 -> 1");
        assert_eq!(ServerState::roll_weather(&[50, 50, 0, 0, 0], 60), 2, "second 50 -> 2");
        assert_eq!(ServerState::roll_weather(&[0, 0, 0, 0, 0], 50), 0, "no chances -> clear");
        assert_eq!(ServerState::roll_weather(&[10, 0, 0, 0, 0], 50), 0, "roll past the bands -> clear");
        assert_eq!(ServerState::roll_weather(&[0, 0, 100, 0, 0], 40), 3, "band 2 -> weather 3");

        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("weather");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        let area_id = state.world.area_id(&area);

        // --- tick_weather broadcasts P_WeatherChange on a timer expiry ---
        state.area_weather.insert(area.clone(), (0, 1)); // timer about to hit 0
        let outs = state.tick_weather();
        let wc = outs
            .iter()
            .find(|o| o.msg_type == P_WEATHER_CHANGE && matches!(o.target, Target::Peer(1)))
            .expect("same-area player receives P_WeatherChange when the weather rolls");
        assert_eq!(wc.payload.len(), 5, "P_WeatherChange = [areaId u32][weather u8]");
        assert_eq!(&wc.payload[0..4], &area_id.to_le_bytes(), "payload leads with the area id");
        let (_, timer) = state.area_weather[&area];
        assert!((2500..=10000).contains(&timer), "timer reset into the Rand(2500,10000) band, not spamming");
        // A second immediate tick does not broadcast (timer hasn't expired again).
        assert!(
            state.tick_weather().iter().all(|o| o.msg_type != P_WEATHER_CHANGE),
            "no further broadcast until the timer expires again"
        );

        // --- a warp carries the destination's current weather in P_ChangeArea ---
        state.area_weather.insert(area.clone(), (3, 5000)); // pin the area to weather 3
        let rid = state.world.session(1).unwrap().runtime_id;
        let warp_outs = state.warp_actor(rid, &area, "nonexistent-portal-falls-back-to-origin");
        let ca = warp_outs
            .iter()
            .find(|o| o.msg_type == P_CHANGE_AREA)
            .expect("warp emits P_ChangeArea");
        assert_eq!(ca.payload[23], 3, "P_ChangeArea carries the area's current weather (byte 23)");
    }

    // Built-in social chat commands (ServerNet.bb:388-475): /me (same area), /yell
    // (all online), /gm (DMs only), /pm (named target), /p (other party members).
    #[test]
    fn social_chat_commands_route_to_the_right_recipients() {
        use crate::state::{Outgoing, ServerState, Target};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("social");
        for name in ["Hero", "Ally", "Other"] {
            let mut acct = Account::new(name, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = name.into();
            c.area = start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        // Deterministic command words (ME/YELL/GM/P/PM) regardless of the shipped
        // Language.txt (the loader itself is unit-tested in language.rs).
        state.language = crate::language::Language::default();
        for (name, peer) in [("Hero", 1u32), ("Ally", 2), ("Other", 3)] {
            handle_start_game(&start_packet(name, MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, peer, 0);
        }
        // Hero + Other are DMs; Ally is not. Other stands in a different area.
        state.accounts.find_mut("Hero").unwrap().is_dm = true;
        state.accounts.find_mut("Other").unwrap().is_dm = true;
        state.world.warp_session(3, "Elsewhere".to_string(), 0.0, 0.0, 0.0);

        let recips = |outs: &[Outgoing]| -> Vec<u32> {
            let mut v: Vec<u32> = outs
                .iter()
                .filter(|o| o.msg_type == P_CHAT_MESSAGE)
                .filter_map(|o| match o.target {
                    Target::Peer(p) => Some(p),
                    _ => None,
                })
                .collect();
            v.sort();
            v
        };

        // /pm reaches ONLY the named target, as "<sender>: <msg>" (purple 252).
        let outs = state.dispatch(1, P_CHAT_MESSAGE, b"/pm Ally,secret");
        assert_eq!(recips(&outs), vec![2], "/pm reaches only the target");
        let pm = outs.iter().find(|o| o.msg_type == P_CHAT_MESSAGE).unwrap();
        assert_eq!(pm.payload[0], 252, "/pm is purple (252)");
        assert_eq!(&pm.payload[1..], b"Hero: secret", "/pm body is '<sender>: <msg>'");

        // /me reaches the sender's area only (Hero + Ally), not Other.
        assert_eq!(recips(&state.dispatch(1, P_CHAT_MESSAGE, b"/me waves")), vec![1, 2], "/me is same-area incl. self");

        // /yell reaches everyone online.
        assert_eq!(recips(&state.dispatch(1, P_CHAT_MESSAGE, b"/yell hi")), vec![1, 2, 3], "/yell is global");

        // /gm reaches only DM accounts (Hero + Other), not Ally; a non-DM /gm no-ops.
        assert_eq!(recips(&state.dispatch(1, P_CHAT_MESSAGE, b"/gm alert")), vec![1, 3], "/gm is DM-only");
        assert!(recips(&state.dispatch(2, P_CHAT_MESSAGE, b"/gm nope")).is_empty(), "non-DM /gm is refused");

        // /p reaches other party members only (not the sender).
        let _ = state.dispatch(1, P_CHAT_MESSAGE, b"/party Ally");
        assert_eq!(recips(&state.dispatch(1, P_CHAT_MESSAGE, b"/p ready")), vec![2], "/p is other party members only");
    }

    // Built-in DM chat commands (ServerNet.bb:201-389): /xp /gold /setattribute
    // /setattributemax /kick /script — each gated on Account\IsDM.
    #[test]
    fn dm_chat_commands_gate_on_is_dm_and_apply() {
        use crate::state::{ServerState, Target};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("dmcmd");
        for name in ["Hero", "Ally"] {
            let mut acct = Account::new(name, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = name.into();
            c.area = start_area.clone();
            c.level = 50; // high enough that /xp doesn't trigger a LevelUp XP reset
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        state.language = crate::language::Language::default();
        for (name, peer) in [("Hero", 1u32), ("Ally", 2)] {
            handle_start_game(&start_packet(name, MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, peer, 0);
        }
        state.accounts.find_mut("Hero").unwrap().is_dm = true; // Hero is a DM; Ally is not.

        // /xp: DM grants XP to self; non-DM refused.
        let xp0 = state.accounts.find("Hero").unwrap().characters[0].actor.xp;
        state.dispatch(1, P_CHAT_MESSAGE, b"/xp 100");
        assert!(state.accounts.find("Hero").unwrap().characters[0].actor.xp > xp0, "DM /xp grants XP");
        let ally_xp0 = state.accounts.find("Ally").unwrap().characters[0].actor.xp;
        state.dispatch(2, P_CHAT_MESSAGE, b"/xp 100");
        assert_eq!(state.accounts.find("Ally").unwrap().characters[0].actor.xp, ally_xp0, "non-DM /xp refused");

        // /gold: DM adds gold to self + P_GoldChange; non-DM refused.
        let gold0 = state.accounts.find("Hero").unwrap().characters[0].actor.gold;
        let outs = state.dispatch(1, P_CHAT_MESSAGE, b"/gold 50");
        assert_eq!(state.accounts.find("Hero").unwrap().characters[0].actor.gold, gold0 + 50, "DM /gold adds gold");
        assert!(
            outs.iter().any(|o| o.msg_type == P_GOLD_CHANGE && matches!(o.target, Target::Peer(1))),
            "/gold notifies the owner"
        );
        let ally_gold0 = state.accounts.find("Ally").unwrap().characters[0].actor.gold;
        state.dispatch(2, P_CHAT_MESSAGE, b"/gold 50");
        assert_eq!(state.accounts.find("Ally").unwrap().characters[0].actor.gold, ally_gold0, "non-DM /gold refused");

        // /setattribute + /setattributemax against a real attribute name.
        let attr_names = std::fs::read(dir.join("Server Data/Attributes.dat"))
            .ok()
            .and_then(|b| rcce_data::attributes::AttributeNames::parse(&b).ok())
            .unwrap_or_default();
        let attr_len = state.accounts.find("Hero").unwrap().characters[0].actor.attributes.maximum.len();
        let attr = (0..attr_len)
            .find(|&i| attr_names.name(i).map(|n| !n.is_empty()).unwrap_or(false))
            .map(|i| (i, attr_names.name(i).unwrap().to_string()));
        if let Some((idx, attr)) = attr {
            state.accounts.find_mut("Hero").unwrap().characters[0].actor.attributes.maximum[idx] = 1000;
            let set = format!("/setattribute {attr},42");
            let outs = state.dispatch(1, P_CHAT_MESSAGE, set.as_bytes());
            assert_eq!(state.accounts.find("Hero").unwrap().characters[0].actor.attributes.value[idx], 42, "DM /setattribute sets the value");
            assert!(
                outs.iter().any(|o| o.msg_type == P_STAT_UPDATE && o.payload.first() == Some(&b'A')),
                "/setattribute broadcasts P_StatUpdate 'A'"
            );
            let ally_before = state.accounts.find("Ally").unwrap().characters[0].actor.attributes.value[idx];
            state.dispatch(2, P_CHAT_MESSAGE, set.as_bytes());
            assert_eq!(state.accounts.find("Ally").unwrap().characters[0].actor.attributes.value[idx], ally_before, "non-DM /setattribute refused");

            let setmax = format!("/setattributemax {attr},500");
            let outs = state.dispatch(1, P_CHAT_MESSAGE, setmax.as_bytes());
            assert_eq!(state.accounts.find("Hero").unwrap().characters[0].actor.attributes.maximum[idx], 500, "DM /setattributemax sets the max");
            assert!(
                outs.iter().any(|o| o.msg_type == P_STAT_UPDATE && o.payload.first() == Some(&b'M')),
                "/setattributemax broadcasts P_StatUpdate 'M'"
            );

            let overflowing_setmax = format!("/setattributemax {attr},32768");
            let outs = state.dispatch(1, P_CHAT_MESSAGE, overflowing_setmax.as_bytes());
            assert_eq!(
                state.accounts.find("Hero").unwrap().characters[0].actor.attributes.maximum[idx],
                i16::MAX,
                "/setattributemax saturates at the signed persistence maximum"
            );
            assert!(
                outs.iter().any(|o| {
                    o.msg_type == P_STAT_UPDATE
                        && o.payload.len() == 6
                        && o.payload[0] == b'M'
                        && o.payload[4..6] == i16::MAX.to_le_bytes()
                }),
                "/setattributemax broadcasts the saturated P_StatUpdate 'M' maximum"
            );
        }

        // /script: DM spawns the named script privileged; non-DM refused.
        if state.scripts.get("In-game Commands").is_some() {
            let before = state.running_script_count();
            state.dispatch(1, P_CHAT_MESSAGE, b"/script In-game Commands,Loc");
            assert!(state.running_script_count() > before, "DM /script spawns the named script");
            let held = state.running_script_count();
            state.dispatch(2, P_CHAT_MESSAGE, b"/script In-game Commands,Loc");
            assert_eq!(state.running_script_count(), held, "non-DM /script refused");
        }

        // /kick: DM kicks a named player (P_KickedPlayer + queued disconnect); non-DM refused.
        let outs = state.dispatch(1, P_CHAT_MESSAGE, b"/kick Ally");
        assert!(
            outs.iter().any(|o| o.msg_type == P_KICKED_PLAYER && matches!(o.target, Target::Peer(2))),
            "DM /kick sends P_KickedPlayer to the target"
        );
        assert!(state.take_pending_kicks().contains(&2), "DM /kick queues the target's disconnect");
        assert!(
            !state.dispatch(2, P_CHAT_MESSAGE, b"/kick Hero").iter().any(|o| o.msg_type == P_KICKED_PLAYER),
            "non-DM /kick refused"
        );
    }

    // PvP combat damage (ServerNet.bb:1610-1612): in a PvP area a melee swing
    // damages a player defender (H/Y/HP-update feedback + Death on a killing blow);
    // outside a PvP area the swing is refused.
    #[test]
    fn pvp_damage_applies_in_pvp_area_and_is_refused_in_non_pvp() {
        use crate::state::{ServerState, Target};
        let dir = data_dir();
        if Area::load(&dir, "Plains").map(|a| a.pvp).unwrap_or(0) == 0 {
            return; // this project's Plains isn't PvP
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let tid = template.id;
        let mut store = tmp_store("pvpdmg");
        for (u, name) in [("alice", "Alice"), ("bob", "Bob")] {
            let mut acct = Account::new(u, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = tid;
            c.name = name.into();
            c.area = "Plains".into();
            c.level = 50;
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let hs = state.health_stat;
        // Give both plenty of HP; arm Alice so the swing deals real damage.
        for u in ["alice", "bob"] {
            let rec = state.accounts.find_mut(u).unwrap().characters.get_mut(0).unwrap();
            if let Some(m) = rec.actor.attributes.maximum.get_mut(hs) { *m = 300; }
            if let Some(v) = rec.actor.attributes.value.get_mut(hs) { *v = 300; }
        }
        state.accounts.find_mut("alice").unwrap().characters[0].actor.attributes.value[state.strength_stat] = 30_000;
        state.accounts.find_mut("bob").unwrap().characters[0].actor.resistances.fill(30_000);
        if let Some(wid) = state.items.items.iter().find(|d| d.item_type == 1).map(|d| d.id) {
            state.accounts.find_mut("alice").unwrap().characters[0].actor.inventory[0].item =
                Some(rcce_server_core::item::ItemInstance::new(wid));
        }
        handle_start_game(&start_packet("alice", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("bob", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);
        if state.world.session(1).is_none() || state.world.session(2).is_none() {
            return; // Plains didn't accept the players (data variance)
        }
        let bob = state.world.session(2).unwrap().runtime_id;
        let step = state.combat_delay as u64 + 100;
        let mut now = step;
        let hp = |s: &ServerState| s.accounts.find("bob").unwrap().characters[0].actor.attributes.value[hs];

        // A PvP swing produces the H (attacker) / Y (defender) / HP-update feedback
        // (present on a hit OR a miss).
        let first = state.handle_attack(1, &bob.to_le_bytes(), now);
        now += step;
        assert!(first.iter().any(|o| o.msg_type == P_ATTACK_ACTOR && o.payload.first() == Some(&b'H') && matches!(o.target, Target::Sender)), "attacker gets 'H' feedback");
        assert!(first.iter().any(|o| o.msg_type == P_ATTACK_ACTOR && o.payload.first() == Some(&b'Y') && matches!(o.target, Target::Peer(2))), "defender gets 'Y' feedback");
        assert!(first.iter().any(|o| o.msg_type == P_STAT_UPDATE && matches!(o.target, Target::Peer(2))), "defender receives an HP update");

        // Damage eventually lands (swings can miss).
        let hp0 = hp(&state);
        for _ in 0..40 {
            let before = hp(&state);
            if before < hp0 { break; }
            state.handle_attack(1, &bob.to_le_bytes(), now);
            now += step;
            let after = hp(&state);
            if after < before {
                assert_eq!(after, before - 1, "player defender resistance applies to a PvP live hit");
                break;
            }
        }
        assert!(hp(&state) < hp0, "PvP damage eventually drops the defender's HP");

        // Outside a PvP area the swing is refused (no damage, no packets).
        state.world.warp_session(1, "NoPvPZone".to_string(), 0.0, 0.0, 0.0);
        state.world.warp_session(2, "NoPvPZone".to_string(), 0.0, 0.0, 0.0);
        let hp_np = hp(&state);
        let refused = state.handle_attack(1, &bob.to_le_bytes(), now);
        now += step;
        assert!(refused.is_empty(), "the swing is refused outside a PvP area");
        assert_eq!(hp(&state), hp_np, "no PvP damage in a non-PvP area");

        // A killing blow drops the defender to 0 HP and fires the Death path.
        state.world.warp_session(1, "Plains".to_string(), 0.0, 0.0, 0.0);
        state.world.warp_session(2, "Plains".to_string(), 0.0, 0.0, 0.0);
        state.accounts.find_mut("bob").unwrap().characters[0].actor.attributes.value[hs] = 1;
        let scripts_before = state.running_script_count();
        for _ in 0..40 {
            if hp(&state) == 0 { break; }
            state.handle_attack(1, &bob.to_le_bytes(), now);
            now += step;
        }
        assert_eq!(hp(&state), 0, "a killing blow drops the defender to 0 HP");
        if state.scripts.get("Death").is_some() {
            assert!(state.running_script_count() > scripts_before, "killing blow fires the Death script");
        }
    }

    // The Startup content script runs at server boot (Server.bb:296). Its privilege
    // is allowlist-based (like fire_hook_async): privileged only if the operator
    // lists Startup in Privileged Scripts.dat (Blitz's ThreadScript default is
    // non-privileged). So an allowlisted Startup's SetSuperGlobal applies; a
    // non-allowlisted one is refused.
    #[test]
    fn startup_script_runs_at_boot_with_allowlist_privilege() {
        use crate::state::ServerState;
        // Boot a temp data dir whose Startup.rsl sets super-global 0; `allowlist`
        // controls whether Startup is on Privileged Scripts.dat. Returns the value
        // of super-global 0 after the boot script runs.
        let boot = |allowlist: bool| -> String {
            let tmp = std::env::temp_dir().join(format!("rcce_startup_{}_{allowlist}", std::process::id()));
            let scripts_dir = tmp.join("Server Data").join("Scripts");
            let _ = std::fs::remove_dir_all(&tmp);
            std::fs::create_dir_all(&scripts_dir).unwrap();
            std::fs::write(
                scripts_dir.join("Startup.rsl"),
                "Function Main()\n\tSetSuperGlobal(0, \"booted\")\nEnd Function\n",
            )
            .unwrap();
            if allowlist {
                std::fs::write(tmp.join("Server Data").join("Privileged Scripts.dat"), "Startup\n").unwrap();
            }
            let store = tmp_store("startup");
            let mut state = ServerState::new(config_for(tmp.clone()), store, rcce_server_core::ActorCatalog::default());
            // The test authors a known-good Startup.rsl, so a link failure is a real
            // regression (fail), not an expected skip.
            assert!(state.scripts.get("Startup").is_some(), "the test's Startup.rsl linked");
            assert_eq!(state.scripts.is_privileged("Startup"), allowlist, "Startup privilege tracks the allowlist");
            state.run_startup();
            assert!(state.running_script_count() > 0, "boot spawns the Startup script");
            // Drive the async boot script to completion (generous wall-clock deadline
            // so full-suite CPU contention can't starve the poll).
            let deadline = std::time::Instant::now() + std::time::Duration::from_secs(10);
            loop {
                state.pump_scripts();
                if state.running_script_count() == 0 || std::time::Instant::now() >= deadline {
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
            let g = state.super_global(0).to_string();
            let _ = std::fs::remove_dir_all(&tmp);
            g
        };
        // Allowlisted → privileged → SetSuperGlobal applies.
        assert_eq!(boot(true), "booted", "an allowlisted Startup runs privileged and sets the super-global");
        // Not allowlisted → non-privileged (Blitz default) → SetSuperGlobal is refused.
        assert_eq!(boot(false), "", "a non-allowlisted Startup runs non-privileged; SetSuperGlobal is refused");
    }

    #[test]
    fn slash_command_routes_to_in_game_commands_script() {
        use crate::state::ServerState;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/In-game Commands.rsl").exists() {
            eprintln!("skipping: no In-game Commands.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("slash");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("In-game Commands").is_none() {
            eprintln!("skipping: In-game Commands did not link");
            return;
        }
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);

        // `/loc` → the In-game Commands `Loc` function Outputs the position.
        let immediate = state.dispatch(1, P_CHAT_MESSAGE, b"/loc");
        assert!(immediate.is_empty(), "the command fires a script; no immediate reply");
        assert_eq!(state.running_script_count(), 1, "the In-game Commands script spawned");

        let mut outputs = Vec::new();
        for _ in 0..200 {
            outputs.extend(state.pump_scripts());
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        // `Loc` calls Output(...) → P_ChatMessage with the 250 colour-prefix.
        let said: Vec<String> = outputs
            .iter()
            .filter(|o| o.msg_type == P_CHAT_MESSAGE && o.payload.first() == Some(&250))
            .map(|o| String::from_utf8_lossy(&o.payload[4..]).into_owned())
            .collect();
        assert!(
            said.iter().any(|s| s.contains("X:")),
            "/loc produced a position Output; got {said:?}"
        );

        // A non-chat (plain) message still broadcasts as area chat, not a command.
        let chat = state.dispatch(1, P_CHAT_MESSAGE, b"hello there");
        assert!(
            chat.iter().any(|o| o.msg_type == P_CHAT_MESSAGE
                && String::from_utf8_lossy(&o.payload).contains("hello there")),
            "plain chat is still broadcast"
        );
    }

    #[test]
    fn jump_relays_action_bar_persists_and_script_packets_softfail() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("packets");
        for name in ["hero", "ally"] {
            let mut acct = Account::new(name, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = name.into();
            c.area = start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("ally", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);
        let rid1 = state.world.session(1).unwrap().runtime_id;

        // P_Jump from peer 1 → peer 2 (same area) is told peer 1's runtime id.
        let jumps = state.dispatch(1, P_JUMP, &[]);
        assert!(
            jumps.iter().any(|o| o.msg_type == P_JUMP
                && matches!(o.target, crate::state::Target::Peer(2))
                && o.payload == rid1.to_le_bytes()),
            "a jump relays the jumper's runtime id to the same-area peer"
        );
        assert!(
            !jumps.iter().any(|o| matches!(o.target, crate::state::Target::Peer(1))),
            "the jumper does not get its own jump echoed"
        );

        // P_ActionBarUpdate: 'S' stores a spell slot, 'N' clears it.
        let mut sset = vec![b'S', 3];
        sset.extend_from_slice(b"Fireball");
        state.dispatch(1, P_ACTION_BAR_UPDATE, &sset);
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].action_bar[3],
            "SFireball",
            "the hotbar spell slot persisted"
        );
        state.dispatch(1, P_ACTION_BAR_UPDATE, &[b'N', 3]);
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].action_bar[3],
            "",
            "clearing the slot persisted"
        );

        // P_ScriptInput / P_ProgressBar with no suspended script: soft-fail, no
        // panic, no reply.
        assert!(state.dispatch(1, P_SCRIPT_INPUT, &[1, 0, 0, 0]).is_empty());
        assert!(state.dispatch(1, P_PROGRESS_BAR, &[b'C', 1, 0, 0, 0, 5, 0, 0, 0]).is_empty());
        // Truncated payloads don't panic either.
        assert!(state.dispatch(1, P_SCRIPT_INPUT, &[]).is_empty());
        assert!(state.dispatch(1, P_ACTION_BAR_UPDATE, b"S").is_empty());
    }

    #[test]
    fn ability_bvms_grant_and_gate_level() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(player_t) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let player_id = player_t.id;
        let start_area = player_t.start_area.clone();
        let mut store = tmp_store("abilitybvm");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = player_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let Some(spell) = state.spells_catalog.spells.first().cloned() else {
            eprintln!("skipping: no spells in Spells.dat");
            return;
        };
        let spell_name = spell.name.clone();
        let spell_id = spell.id;
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        let level_of = |state: &ServerState| -> i16 {
            let rec = &state.accounts.find("hero").unwrap().characters[0];
            rec.actor
                .spell_levels
                .iter()
                .zip(&rec.actor.known_spells)
                .find(|(&lv, &id)| lv > 0 && id == spell_id as i16)
                .map(|(&lv, _)| lv)
                .unwrap_or(0)
        };
        let run = |state: &mut ServerState, src: &str, privileged: bool| -> Vec<crate::state::Outgoing> {
            state.start_inline_script(src, "Main", rid, 0, 1, privileged);
            let mut all = Vec::new();
            for _ in 0..200 {
                all.extend(state.pump_scripts());
                if state.running_script_count() == 0 {
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
            all
        };

        // AddAbility is ungated — even an unprivileged script grants the spell.
        let add_src = format!("Function Main()\n\tp = Actor()\n\tAddAbility(p, \"{spell_name}\", 3)\nEnd Function\n");
        let outs = run(&mut state, &add_src, false);
        assert_eq!(level_of(&state), 3, "AddAbility grants the spell at the given level");
        assert!(
            outs.iter().any(|o| o.msg_type == P_KNOWN_SPELL_UPDATE && o.payload.first() == Some(&b'A')),
            "AddAbility broadcasts P_KnownSpellUpdate 'A'"
        );

        // SetAbilityLevel is privileged: a privileged script raises it...
        let set_src = format!("Function Main()\n\tp = Actor()\n\tSetAbilityLevel(p, \"{spell_name}\", 7)\nEnd Function\n");
        run(&mut state, &set_src, true);
        assert_eq!(level_of(&state), 7, "a privileged SetAbilityLevel applies");

        // ...but an unprivileged one is refused (the brick-vector gate).
        let nerf_src = format!("Function Main()\n\tp = Actor()\n\tSetAbilityLevel(p, \"{spell_name}\", 1)\nEnd Function\n");
        run(&mut state, &nerf_src, false);
        assert_eq!(level_of(&state), 7, "an unprivileged SetAbilityLevel is gated out");
    }

    #[test]
    fn spawn_bvm_creates_npc_and_spawnitem_drops_ground_item() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(player_t) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        // Any non-playable template to spawn as a creature.
        let Some(mob_t) = catalog.templates.values().find(|t| !t.playable) else {
            return;
        };
        let mob_id = mob_t.id;
        let player_id = player_t.id;
        let start_area = player_t.start_area.clone();
        let mut store = tmp_store("spawnbvm");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = player_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();

        // Pick a real item name (skip the SpawnItem half if Items.dat is empty).
        let item_name = state.items.items.first().map(|i| i.name.clone());
        let n_before = state.spawns.npcs_in_area(&area).count();

        let item_line = match &item_name {
            Some(n) => format!("\tSpawnItem(\"{}\", 2, \"{}\", 1.0, 0.0, 1.0)\n", n, area),
            None => String::new(),
        };
        let src = format!(
            "Function Main()\n\tSpawn({mob_id}, \"{area}\", 5.0, 0.0, 5.0, \"\", \"\")\n{item_line}End Function\n"
        );
        state.start_inline_script(&src, "Main", state.world.session(1).unwrap().runtime_id, 0, 1, false);

        let mut drop_seen = false;
        for _ in 0..200 {
            let outs = state.pump_scripts();
            if outs.iter().any(|o| o.msg_type == P_INVENTORY_UPDATE && o.payload.first() == Some(&b'D')) {
                drop_seen = true;
            }
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }

        // Spawn created a live NPC of the requested template in the area.
        let n_after = state.spawns.npcs_in_area(&area).count();
        assert_eq!(n_after, n_before + 1, "Spawn added one NPC to the area");
        assert!(
            state.spawns.npcs_in_area(&area).any(|npc| npc.actor_id == mob_id),
            "the spawned NPC has the requested template id"
        );
        // SpawnItem dropped a ground item broadcast to the same-area player.
        if item_name.is_some() {
            assert!(drop_seen, "SpawnItem broadcasts a P_InventoryUpdate 'D' ground item");
        }
    }

    #[test]
    fn quest_bvms_create_update_complete_and_report() {
        use crate::state::ServerState;
        use rcce_script::{Host, Value};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("questbvm");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id as i64;

        let mut host = crate::scripts::ScriptHost {
            world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
            catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
            actor: rid, ctx: 0, privileged: false, dirty: false, out: Vec::new(),
        };
        let q = "The Lost Ring";
        // NewQuest → entry created, P_QuestLog "N", QuestStatus returns the desc.
        host.call("newquest", &[Value::Int(rid), Value::Str(q.into()), Value::Str("Find the ring".into())]);
        assert!(
            host.out.iter().any(|o| o.msg_type == P_QUEST_LOG && o.payload.first() == Some(&b'N')),
            "NewQuest broadcasts P_QuestLog 'N'"
        );
        let status = host.call("queststatus", &[Value::Int(rid), Value::Str(q.into())]).to_string_value();
        assert_eq!(status, "Find the ring", "QuestStatus returns the description (past the 3 flag bytes)");
        // Not complete yet.
        assert_eq!(host.call("questcomplete", &[Value::Int(rid), Value::Str(q.into())]).to_int(), 0);

        // Duplicate NewQuest is a no-op (one entry only).
        host.out.clear();
        host.call("newquest", &[Value::Int(rid), Value::Str(q.into()), Value::Str("dupe".into())]);
        assert!(host.out.is_empty(), "a duplicate NewQuest does not re-add or broadcast");

        // UpdateQuest changes the description.
        host.call("updatequest", &[Value::Int(rid), Value::Str(q.into()), Value::Str("Ring is in the well".into())]);
        let status2 = host.call("queststatus", &[Value::Int(rid), Value::Str(q.into())]).to_string_value();
        assert_eq!(status2, "Ring is in the well");

        // CompleteQuest → QuestComplete now reports 1.
        host.call("completequest", &[Value::Int(rid), Value::Str(q.into())]);
        assert_eq!(
            host.call("questcomplete", &[Value::Int(rid), Value::Str(q.into())]).to_int(),
            1,
            "CompleteQuest marks the quest complete"
        );
    }

    #[test]
    fn actor_iteration_and_set_item_health() {
        use crate::state::ServerState;
        use rcce_script::{Host, Value};
        use rcce_server_core::character::InventorySlot;
        use rcce_server_core::ItemInstance;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("iter");
        for name in ["Hero", "Ally"] {
            let mut acct = Account::new(name, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = name.into();
            c.area = start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let item = state.items.items.first().cloned();
        handle_start_game(&start_packet("Hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("Ally", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);
        let area = state.world.session(1).unwrap().area.clone();
        let hero_rid = state.world.session(1).unwrap().runtime_id;
        let ally_rid = state.world.session(2).unwrap().runtime_id;

        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
                catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
                actor: hero_rid as i64, ctx: 0, privileged: false, dirty: false, out: Vec::new(),
            };
            assert_eq!(host.call("findactor", &[Value::Str("Ally".into()), Value::Int(1)]).to_int(), ally_rid as i64, "FindActor by name");
            // Iteration covers both players, in ascending rid order.
            let first = host.call("firstactorinzone", &[Value::Str(area.clone())]).to_int();
            let next = host.call("nextactorinzone", &[Value::Int(first)]).to_int();
            let mut seen = vec![first, next];
            seen.sort_unstable();
            assert_eq!(seen, vec![hero_rid.min(ally_rid) as i64, hero_rid.max(ally_rid) as i64], "First→Next covers both actors");
        }

        // SetItemHealth on an equipped item (privileged).
        if let Some(item) = item {
            {
                let rec = &mut state.accounts.find_mut("Hero").unwrap().characters[0];
                if rec.actor.inventory.is_empty() {
                    rec.actor.inventory.resize(46, InventorySlot::default());
                }
                rec.actor.inventory[0] = InventorySlot { item: Some(ItemInstance::new(item.id)), amount: 1 };
            }
            let src = "Function Main()\n\tp = Actor()\n\tSetItemHealth(ActorWeapon(p), 50)\nEnd Function\n";
            state.start_inline_script(src, "Main", hero_rid, 0, 1, true);
            for _ in 0..200 {
                state.pump_scripts();
                if state.running_script_count() == 0 { break; }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
            assert_eq!(
                state.accounts.find("Hero").unwrap().characters[0].actor.inventory[0].item.as_ref().unwrap().item_health,
                50,
                "SetItemHealth set the equipped item's durability"
            );
        }
    }

    #[test]
    fn equip_and_item_property_reads_via_handle() {
        use crate::state::ServerState;
        use rcce_server_core::character::InventorySlot;
        use rcce_server_core::ItemInstance;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("itemreads");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let Some(item) = state.items.items.first().cloned() else {
            eprintln!("skipping: no items in Items.dat");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;
        // Equip the item in the weapon slot (0).
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            if rec.actor.inventory.is_empty() {
                rec.actor.inventory.resize(46, InventorySlot::default());
            }
            let mut inst = ItemInstance::new(item.id);
            inst.item_health = 77;
            rec.actor.inventory[0] = InventorySlot { item: Some(inst), amount: 1 };
        }

        // ActorWeapon(p) → handle; ItemName/ItemID/ItemHealth(handle) → stash.
        let src = "Function Main()\n\tp = Actor()\n\tw = ActorWeapon(p)\n\tSetActorGlobal(p, 0, ItemName(w))\n\tSetActorGlobal(p, 1, ItemID(w))\n\tSetActorGlobal(p, 2, ItemHealth(w))\nEnd Function\n";
        state.start_inline_script(src, "Main", rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        let g = &state.accounts.find("hero").unwrap().characters[0].actor.script_globals;
        assert_eq!(g[0], item.name, "ItemName(ActorWeapon) resolves the equipped item's name");
        assert_eq!(g[1], item.id.to_string(), "ItemID resolves the template id");
        assert_eq!(g[2], "77", "ItemHealth reads the per-instance durability");

        // An empty slot → handle 0 → empty name.
        let src2 = "Function Main()\n\tp = Actor()\n\ts = ActorShield(p)\n\tSetActorGlobal(p, 3, ItemName(s))\nEnd Function\n";
        state.start_inline_script(src2, "Main", rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.script_globals[3],
            "",
            "an empty equipment slot yields a null item handle"
        );
    }

    #[test]
    fn threadexecute_chains_into_another_script() {
        use crate::state::ServerState;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/In-game Commands.rsl").exists() {
            eprintln!("skipping: no In-game Commands.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("threadexec");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("In-game Commands").is_none() {
            return;
        }
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        // A script that ThreadExecutes the In-game Commands `Loc` function, which
        // Outputs the actor's position.
        let src = format!("Function Main()\n\tThreadExecute(\"In-game Commands\", \"Loc\", {rid}, 0, \"\")\nEnd Function\n");
        state.start_inline_script(&src, "Main", rid, 0, 1, true);
        let mut outputs = Vec::new();
        for _ in 0..200 {
            outputs.extend(state.pump_scripts());
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        let said: Vec<String> = outputs
            .iter()
            .filter(|o| o.msg_type == P_CHAT_MESSAGE && o.payload.first() == Some(&250))
            .map(|o| String::from_utf8_lossy(&o.payload[4..]).into_owned())
            .collect();
        assert!(
            said.iter().any(|s| s.contains("X:")),
            "ThreadExecute ran the chained Loc script (position Output); got {said:?}"
        );
    }

    #[test]
    fn underwater_breath_drains_and_drowns() {
        use crate::state::ServerState;
        let dir = data_dir();
        // Find an area with a water volume.
        let areas_dir = dir.join("Server Data/Areas");
        let Ok(entries) = std::fs::read_dir(&areas_dir) else { return };
        let mut water_area = None;
        for e in entries.flatten() {
            let p = e.path();
            if p.extension().and_then(|x| x.to_str()) != Some("dat") { continue; }
            let name = p.file_stem().unwrap().to_string_lossy().to_string();
            if let Some(a) = Area::load(&dir, &name) {
                if let Some(w) = a.waters.first() {
                    water_area = Some((name, *w));
                    break;
                }
            }
        }
        let Some((area_name, w)) = water_area else {
            eprintln!("skipping: no area has a water volume");
            return;
        };

        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable && Area::load(&dir, &t.start_area).is_some()) else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("breath");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let Some(bidx) = state.breath_stat else {
            eprintln!("skipping: project has no Breath stat");
            return;
        };
        let hidx = state.health_stat;
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        // Place the player submerged inside the water volume.
        let (px, py, pz) = (w.x + w.width / 2.0, w.y - 5.0, w.z + w.depth / 2.0);
        state.world.warp_session(1, area_name.clone(), px, py, pz);
        // Out of breath, one hit from death.
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.attributes.value[bidx] = 0;
            rec.actor.attributes.maximum[hidx] = 100;
            rec.actor.attributes.value[hidx] = 1;
        }

        // First tick arms the 1 Hz gate (no drain yet).
        let _ = state.collect_breath();
        std::thread::sleep(std::time::Duration::from_millis(1100));
        // Now the drain fires: breath already 0 → health 1 → 0 → drown.
        let outs = state.collect_breath();
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[hidx], 0, "drowning took the last HP");
        assert!(
            outs.iter().any(|o| o.msg_type == P_STAT_UPDATE),
            "the health drop is broadcast"
        );
        // The drown ran the Death path (a script spawned).
        assert!(state.running_script_count() >= 1, "drowning fires the Death script");
    }

    #[test]
    fn energy_drains_while_running_owner_only() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("energy");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let Some(eidx) = state.energy_stat else {
            eprintln!("skipping: project has no Energy stat configured");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        state.accounts.find_mut("hero").unwrap().characters[0].actor.attributes.value[eidx] = 10;

        // Not running yet → no drain.
        assert!(state.collect_energy_drain().is_empty(), "no drain when standing still");

        // Mark running via a P_StandardUpdate (run byte = 1).
        let mut upd = Vec::new();
        for _ in 0..5 {
            upd.extend_from_slice(&0f32.to_le_bytes()); // destX, destZ, newY, newX, newZ
        }
        upd.push(1); // running
        upd.push(0); // walking_backward
        state.dispatch(1, P_STANDARD_UPDATE, &upd);

        // One drain → energy 9, owner-only P_StatUpdate.
        let outs = state.collect_energy_drain();
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[eidx], 9);
        assert!(
            outs.iter().any(|o| o.msg_type == P_STAT_UPDATE && matches!(o.target, crate::state::Target::Peer(1))),
            "the running player gets an energy update"
        );

        // Drain to 0 and confirm it stops there (no negative, no further updates).
        for _ in 0..20 {
            state.collect_energy_drain();
        }
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[eidx], 0, "energy floors at 0");
        assert!(state.collect_energy_drain().is_empty(), "no drain once energy is exhausted");
    }

    #[test]
    fn waitspeak_and_waititem_resume_on_events() {
        use crate::state::ServerState;
        use rcce_server_core::character::InventorySlot;
        use rcce_server_core::ItemInstance;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("waitevt");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let Some(item) = state.items.items.first().cloned() else { return };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;
        let pump_a_few = |state: &mut ServerState| {
            for _ in 0..5 {
                state.pump_scripts();
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
        };

        // --- WaitSpeak: park until the player speaks. ---
        let s_src = "Function Main()\n\tp = Actor()\n\tSetWaitSpeak(p, p)\n\tSetWaiting(1)\n\tr = GetWaitResult()\n\tSetActorGlobal(p, 1, \"spoke\")\nEnd Function\n";
        state.start_inline_script(s_src, "Main", rid, 0, 1, true);
        pump_a_few(&mut state);
        assert_ne!(state.accounts.find("hero").unwrap().characters[0].actor.script_globals[1], "spoke", "WaitSpeak parks until the player speaks");
        state.dispatch(1, P_CHAT_MESSAGE, b"hello"); // the speak event
        for _ in 0..100 { state.pump_scripts(); if state.running_script_count() == 0 { break; } std::thread::sleep(std::time::Duration::from_millis(1)); }
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.script_globals[1], "spoke", "WaitSpeak resumed on chat");

        // --- WaitSpeak: retain a chat event that wins the race before the
        // script reaches GetWaitResult. The handshake signal persists while
        // the script is held, so this cannot miss a faster script thread.
        let early_s_src = "Function Main()\n\tp = Actor()\n\tSetWaitSpeak(p, p)\n\tSetWaiting(1)\n\tr = GetWaitResult()\n\tSetActorGlobal(p, 3, \"early\")\nEnd Function\n";
        state.start_inline_script(early_s_src, "Main", rid, 0, 1, true);
        let armed = state.hold_last_waitspeak_arm();
        let mut armed_before_park = false;
        for _ in 0..100 {
            state.pump_scripts();
            if armed.try_recv().is_ok() {
                armed_before_park = true;
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert!(armed_before_park, "WaitSpeak arms before GetWaitResult parks");
        state.dispatch(1, P_CHAT_MESSAGE, b"early");
        state.release_last_waitspeak_arm();
        for _ in 0..100 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.script_globals[3], "early", "WaitSpeak retains an early chat event");

        // --- WaitItem: park until the player holds the item. ---
        let i_src = format!("Function Main()\n\tp = Actor()\n\tSetWaitItem(p, \"{}\", 1)\n\tSetWaiting(1)\n\tr = GetWaitResult()\n\tSetActorGlobal(p, 2, \"got\")\nEnd Function\n", item.name);
        state.start_inline_script(&i_src, "Main", rid, 0, 1, true);
        pump_a_few(&mut state);
        assert_ne!(state.accounts.find("hero").unwrap().characters[0].actor.script_globals[2], "got", "WaitItem parks until the item arrives");
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            if rec.actor.inventory.is_empty() { rec.actor.inventory.resize(46, InventorySlot::default()); }
            rec.actor.inventory[14] = InventorySlot { item: Some(ItemInstance::new(item.id)), amount: 1 };
        }
        for _ in 0..100 { state.pump_scripts(); if state.running_script_count() == 0 { break; } std::thread::sleep(std::time::Duration::from_millis(1)); }
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.script_globals[2], "got", "WaitItem resumed once the item arrived");
    }

    #[test]
    fn waittime_suspends_then_resumes_the_script() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("waittime");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        // Park on a 30 ms timer (wait_start 0 → fires once ≥30 ms of uptime),
        // then continue and prove the resume by writing a global.
        let src = "Function Main()\n\tp = Actor()\n\tSetWaitTime(30)\n\tSetWaitStart(0)\n\tSetWaiting(1)\n\tr = GetWaitResult()\n\tSetActorGlobal(p, 0, \"resumed\")\nEnd Function\n";
        state.start_inline_script(src, "Main", rid, 0, 1, true);
        // It must NOT finish immediately (it's parked).
        for _ in 0..5 {
            state.pump_scripts();
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        // Drive to completion (timer expires, script resumes + finishes).
        let mut finished = false;
        for _ in 0..300 {
            state.pump_scripts();
            if state.running_script_count() == 0 {
                finished = true;
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert!(finished, "the WaitTime script resumed and finished");
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.script_globals[0],
            "resumed",
            "the script continued past GetWaitResult after the timer"
        );
    }

    #[test]
    fn script_parameter_bvm_splits_param_string() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("param");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        // A command-style script reading Parameter(0)/(1) of "Sword, 5".
        let src = "Function Main()\n\tp = Actor()\n\tSetActorGlobal(p, 0, Parameter(0))\n\tSetActorGlobal(p, 1, Parameter(1))\nEnd Function\n";
        state.start_inline_script(src, "Main", rid, 0, 1, true);
        state.set_last_script_param("Sword, 5"); // (slash-command path sets this)
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        let g = &state.accounts.find("hero").unwrap().characters[0].actor.script_globals;
        assert_eq!(g[0], "Sword", "Parameter(0) is the first comma-separated arg (trimmed)");
        assert_eq!(g[1], "5", "Parameter(1) is the second arg");
    }

    #[test]
    fn superglobals_roundtrip_and_zone_counts() {
        use crate::state::ServerState;
        use rcce_script::{Host, Value};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("superglobal");
        for name in ["hero", "ally"] {
            let mut acct = Account::new(name, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = name.into();
            c.area = start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("ally", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);
        let area = state.world.session(1).unwrap().area.clone();
        let rid = state.world.session(1).unwrap().runtime_id;

        // Zone counts via ScriptHost: two players present.
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
                catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
                actor: rid as i64, ctx: 0, privileged: false, dirty: false, out: Vec::new(),
            };
            assert_eq!(host.call("playersinzone", &[Value::Str(area.clone())]).to_int(), 2, "two players in the zone");
            assert!(host.call("actorsinzone", &[Value::Str(area.clone())]).to_int() >= 2, "actors ≥ players");
        }

        // Super-global round-trip: a privileged script sets slot 5 and reads it
        // back into an actor-global.
        let src = "Function Main()\n\tp = Actor()\n\tSetSuperGlobal(5, \"hello\")\n\tSetActorGlobal(p, 0, GetSuperGlobal(5))\nEnd Function\n";
        state.start_inline_script(src, "Main", rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.script_globals[0],
            "hello",
            "SetSuperGlobal/GetSuperGlobal round-trips server-wide state"
        );

        // Unprivileged SetSuperGlobal is refused (the value stays "hello").
        let evil = "Function Main()\n\tSetSuperGlobal(5, \"hacked\")\nEnd Function\n";
        state.start_inline_script(evil, "Main", rid, 0, 1, false);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        // Read it back through a privileged getter.
        let check = "Function Main()\n\tp = Actor()\n\tSetActorGlobal(p, 1, GetSuperGlobal(5))\nEnd Function\n";
        state.start_inline_script(check, "Main", rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.script_globals[1],
            "hello",
            "unprivileged SetSuperGlobal is gated out"
        );
    }

    #[test]
    fn actor_read_bvms_return_real_values() {
        use crate::state::ServerState;
        use rcce_script::{Host, Value};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let race = template.race.clone();
        let start_area = template.start_area.clone();
        let mut store = tmp_store("actorreads");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        acct.is_dm = true;
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        c.level = 5;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        state.world.warp_session(1, area, 10.0, 2.0, 30.0);
        let rid = state.world.session(1).unwrap().runtime_id as i64;

        let mut host = crate::scripts::ScriptHost {
            world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
            catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
            actor: rid, ctx: 0, privileged: false, dirty: false, out: Vec::new(),
        };
        assert_eq!(host.call("actorx", &[Value::Int(rid)]).to_float() as f32, 10.0, "ActorX reads the live position (was 0)");
        assert_eq!(host.call("actory", &[Value::Int(rid)]).to_float() as f32, 2.0);
        assert_eq!(host.call("actorz", &[Value::Int(rid)]).to_float() as f32, 30.0);
        assert_eq!(host.call("actorlevel", &[Value::Int(rid)]).to_int(), 5);
        assert_eq!(host.call("race", &[Value::Int(rid)]).to_string_value(), race);
        assert_eq!(host.call("actorishuman", &[Value::Int(rid)]).to_int(), 1, "a player is human");
        assert_eq!(host.call("playerisdm", &[Value::Int(rid)]).to_int(), 1, "DM flag read");
        assert_eq!(host.call("actordistance", &[Value::Int(rid), Value::Int(rid)]).to_float() as f32, 0.0, "self-distance is 0");
    }

    #[test]
    fn appearance_reads_nextactor_and_runtimeerror() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("appread");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        c.hair = 2;
        c.beard = 1;
        c.face_tex = 3;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        let src = "Function Main()\n\tp = Actor()\n\tSetActorGlobal(p, 0, ActorHair(p))\n\tSetActorGlobal(p, 1, ActorBeard(p))\n\tSetActorGlobal(p, 2, ActorFace(p))\n\tSetActorGlobal(p, 3, NextActor(0))\n\tSetActorGlobal(p, 4, RunTimeError(\"oops\"))\nEnd Function\n";
        state.start_inline_script(src, "Main", rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        let g = &state.accounts.find("hero").unwrap().characters[0].actor.script_globals;
        assert_eq!(g[0], "3", "ActorHair is 1-based (stored 2 → 3)");
        assert_eq!(g[1], "2", "ActorBeard 1-based (1 → 2)");
        assert_eq!(g[2], "4", "ActorFace 1-based (3 → 4)");
        assert_eq!(g[3], rid.to_string(), "NextActor(0) is the lowest actor (the player)");
        assert_eq!(g[4], "0", "RunTimeError is a safe no-op (never crashes the headless server)");
    }

    #[test]
    fn zone_instance_read_stubs() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("zoneinst");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        let rid = state.world.session(1).unwrap().runtime_id;

        let src = format!("Function Main()\n\tp = Actor()\n\tSetActorGlobal(p, 0, ActorZoneInstance(p))\n\tSetActorGlobal(p, 1, ZoneInstanceExists(\"{area}\", 0))\n\tSetActorGlobal(p, 2, ZoneInstanceExists(\"{area}\", 1))\n\tSetActorGlobal(p, 3, ActorIDFromInstance(p))\nEnd Function\n");
        state.start_inline_script(&src, "Main", rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        let g = &state.accounts.find("hero").unwrap().characters[0].actor.script_globals;
        assert_eq!(g[0], "0", "ActorZoneInstance is the base instance 0");
        assert_eq!(g[1], "1", "instance 0 of a real zone exists");
        assert_eq!(g[2], "0", "instance 1 does not exist (single-instance model)");
        assert_eq!(g[3], template_id.to_string(), "ActorIDFromInstance is the template id");
    }

    #[test]
    fn assorted_actor_and_world_reads() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("reads2");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        let src = "Function Main()\n\tp = Actor()\n\tSetActorGlobal(p, 0, ActorID(p))\n\tSetActorGlobal(p, 1, PlayerInGame(p))\n\tSetActorGlobal(p, 2, PlayerAccountName(p))\n\tSetActorGlobal(p, 3, Season())\n\tSetActorGlobal(p, 4, GetArmourLevel(p))\nEnd Function\n";
        state.start_inline_script(src, "Main", rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        let g = &state.accounts.find("hero").unwrap().characters[0].actor.script_globals;
        assert_eq!(g[0], template_id.to_string(), "ActorID is the template id");
        assert_eq!(g[1], "1", "PlayerInGame is 1 for a live player");
        assert_eq!(g[2], "hero", "PlayerAccountName is the account user");
        assert_eq!(g[3], "Spring", "Season at day 0 is Spring");
        assert_eq!(g[4], "0", "GetArmourLevel is 0 with nothing equipped");
    }

    #[test]
    fn game_clock_reads_and_savestate() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("clock");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        // Read Hour()/Minute() into globals, and run SaveState() (privileged).
        let src = "Function Main()\n\tp = Actor()\n\tSetActorGlobal(p, 0, Hour())\n\tSetActorGlobal(p, 1, Minute())\n\tSaveState()\nEnd Function\n";
        state.start_inline_script(src, "Main", rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        let g = &state.accounts.find("hero").unwrap().characters[0].actor.script_globals;
        assert_eq!(g[0], "12", "Hour() reads the default noon game clock");
        assert_eq!(g[1], "0", "Minute() reads the game clock");

        // The clock advances internally (cascade logic) without panicking.
        state.tick_clock();
    }

    #[test]
    fn ban_sets_flag_and_kick_queues_disconnect() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("bankick");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        let run = |state: &mut ServerState, src: &str, privileged: bool| -> Vec<crate::state::Outgoing> {
            state.start_inline_script(src, "Main", rid, 0, 1, privileged);
            let mut all = Vec::new();
            for _ in 0..200 {
                all.extend(state.pump_scripts());
                if state.running_script_count() == 0 { break; }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
            all
        };

        // Unprivileged BanPlayer is refused.
        let ban_src = format!("Function Main()\n\tBanPlayer({rid})\nEnd Function\n");
        run(&mut state, &ban_src, false);
        assert!(!state.accounts.find("hero").unwrap().is_banned, "unprivileged BanPlayer is gated out");

        // Privileged BanPlayer sets the account ban flag.
        run(&mut state, &ban_src, true);
        assert!(state.accounts.find("hero").unwrap().is_banned, "privileged BanPlayer sets the ban flag");

        // Privileged KickPlayer sends P_KickedPlayer + queues the disconnect.
        let kick_src = format!("Function Main()\n\tKickPlayer({rid})\nEnd Function\n");
        let outs = run(&mut state, &kick_src, true);
        assert!(
            outs.iter().any(|o| o.msg_type == P_KICKED_PLAYER && matches!(o.target, crate::state::Target::Peer(1))),
            "KickPlayer sends P_KickedPlayer to the player"
        );
        assert_eq!(state.take_pending_kicks(), vec![1], "the peer is queued for disconnect");
    }

    #[test]
    fn changeactor_morphs_template_and_broadcasts() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(player_t) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let player_id = player_t.id;
        // A different template to morph into.
        let Some(other_id) = catalog.templates.keys().copied().find(|&id| id != player_id) else {
            return;
        };
        let start_area = player_t.start_area.clone();
        let mut store = tmp_store("changeactor");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = player_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        let run = |state: &mut ServerState, src: &str, privileged: bool| -> Vec<crate::state::Outgoing> {
            state.start_inline_script(src, "Main", rid, 0, 1, privileged);
            let mut all = Vec::new();
            for _ in 0..200 {
                all.extend(state.pump_scripts());
                if state.running_script_count() == 0 { break; }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
            all
        };

        // Privileged ChangeActor morphs the template + broadcasts "C".
        let src = format!("Function Main()\n\tp = Actor()\n\tChangeActor(p, {other_id})\nEnd Function\n");
        let outs = run(&mut state, &src, true);
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.actor_id, other_id, "the morph applied");
        assert!(
            outs.iter().any(|o| o.msg_type == P_APPEARANCE_UPDATE && o.payload.first() == Some(&b'C')),
            "ChangeActor broadcasts P_AppearanceUpdate 'C'"
        );

        // Unprivileged ChangeActor is refused (morph back attempt fails).
        let evil = format!("Function Main()\n\tp = Actor()\n\tChangeActor(p, {player_id})\nEnd Function\n");
        run(&mut state, &evil, false);
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.actor_id,
            other_id,
            "an unprivileged ChangeActor is gated out"
        );
    }

    #[test]
    fn killactor_bvm_kills_npc_and_gates() {
        use crate::spawn::NpcActor;
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("killactor");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        let rid = state.world.session(1).unwrap().runtime_id;

        let stage_npc = |state: &mut ServerState| -> u16 {
            let nr = state.world.alloc_runtime();
            state.spawns.insert_npc(NpcActor {
                runtime_id: nr, actor_id: template_id, area: area.clone(),
                x: 0.0, y: 0.0, z: 0.0, hp: 50, hp_max: 50,
                target_peer: None, last_attack_ms: 0,
                script: String::new(), death_script: String::new(), stock: Vec::new(),
            });
            nr
        };
        let pump = |state: &mut ServerState| {
            for _ in 0..200 {
                state.pump_scripts();
                if state.running_script_count() == 0 { break; }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
        };

        // Privileged KillActor removes the NPC outright (HP 50 → dead).
        let victim = stage_npc(&mut state);
        let src = format!("Function Main()\n\tKillActor({victim}, 0)\nEnd Function\n");
        state.start_inline_script(&src, "Main", rid, 0, 1, true);
        pump(&mut state);
        assert!(state.spawns.npc(victim).is_none(), "privileged KillActor removes the NPC");

        // Unprivileged KillActor is refused — the NPC survives.
        let survivor = stage_npc(&mut state);
        let evil = format!("Function Main()\n\tKillActor({survivor}, 0)\nEnd Function\n");
        state.start_inline_script(&evil, "Main", rid, 0, 1, false);
        pump(&mut state);
        assert!(state.spawns.npc(survivor).is_some(), "unprivileged KillActor is gated out");
    }

    #[test]
    fn bubble_screenflash_xpbar_bvms() {
        use crate::state::ServerState;
        use rcce_script::{Host, Value};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("bubble");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id as i64;

        let mut host = crate::scripts::ScriptHost {
            world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
            catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
            actor: rid, ctx: 0, privileged: false, dirty: false, out: Vec::new(),
        };
        host.call("bubbleoutput", &[Value::Int(rid), Value::Str("hi!".into())]);
        host.call("screenflash", &[Value::Int(rid), Value::Int(255), Value::Int(0), Value::Int(0), Value::Int(128), Value::Int(500)]);
        host.call("updatexpbar", &[Value::Int(rid), Value::Int(7)]);
        assert_eq!(host.call("backpackcount", &[Value::Int(rid), Value::Int(0)]).to_int(), 0);

        let types: Vec<u8> = host.out.iter().map(|o| o.msg_type).collect();
        assert!(types.contains(&P_BUBBLE_MESSAGE), "BubbleOutput broadcast");
        assert!(types.contains(&P_SCREEN_FLASH), "ScreenFlash sent");
        assert!(
            host.out.iter().any(|o| o.msg_type == P_XP_UPDATE && o.payload == vec![b'B', 7]),
            "UpdateXPBar sends the 'B' level"
        );
        drop(host);
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.xp_bar_level, 7, "xp bar level persisted");
    }

    #[test]
    fn cosmetic_bvms_broadcast_their_packets() {
        use crate::state::ServerState;
        use rcce_script::{Host, Value};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("cosmetic");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id as i64;

        // All cosmetic — ungated (privileged: false).
        let mut host = crate::scripts::ScriptHost {
            world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
            catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
            actor: rid, ctx: 0, privileged: false, dirty: false, out: Vec::new(),
        };
        host.call("animateactor", &[Value::Int(rid), Value::Str("Wave".into()), Value::Float(0.2), Value::Int(0)]);
        host.call("createfloatingnumber", &[Value::Int(rid), Value::Int(42)]);
        host.call("playmusic", &[Value::Int(rid), Value::Int(5), Value::Int(1)]);
        host.call("playspeech", &[Value::Int(rid), Value::Int(3)]);

        let types: Vec<u8> = host.out.iter().map(|o| o.msg_type).collect();
        assert!(types.contains(&P_ANIMATE_ACTOR), "AnimateActor broadcast");
        assert!(types.contains(&P_FLOATING_NUMBER), "CreateFloatingNumber broadcast");
        assert!(types.contains(&P_MUSIC), "PlayMusic broadcast");
        assert!(types.contains(&P_SPEECH), "PlaySpeech broadcast");
        // FloatingNumber payload: [u16 rid][i32 amount][rgb].
        let fln = host.out.iter().find(|o| o.msg_type == P_FLOATING_NUMBER).unwrap();
        assert_eq!(u16::from_le_bytes([fln.payload[0], fln.payload[1]]), rid as u16);
        assert_eq!(i32::from_le_bytes([fln.payload[2], fln.payload[3], fln.payload[4], fln.payload[5]]), 42);
    }

    #[test]
    fn name_tag_bvms_broadcast_and_gate() {
        use crate::state::ServerState;
        use rcce_script::{Host, Value};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("nametag");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id as i64;

        // Privileged SetName/SetTag mutate + broadcast P_NameChange.
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
                catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
                actor: rid, ctx: 0, privileged: true, dirty: false, out: Vec::new(),
            };
            host.call("setname", &[Value::Int(rid), Value::Str("\"Sir Hero\"".into())]);
            host.call("settag", &[Value::Int(rid), Value::Str("[GM]".into())]);
            assert_eq!(host.call("tag", &[Value::Int(rid)]).to_string_value(), "[GM]");
            assert!(
                host.out.iter().any(|o| o.msg_type == P_NAME_CHANGE),
                "name/tag change broadcasts P_NameChange"
            );
        }
        // DeQuote stripped the surrounding quotes.
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.name, "Sir Hero");
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.tag, "[GM]");

        // Unprivileged SetName is refused (clicker-rebrand gate).
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
                catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
                actor: rid, ctx: 0, privileged: false, dirty: false, out: Vec::new(),
            };
            host.call("setname", &[Value::Int(rid), Value::Str("Hacked".into())]);
        }
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.name,
            "Sir Hero",
            "unprivileged SetName is gated out"
        );
    }

    #[test]
    fn resistance_and_zone_read_bvms() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("reszone");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        let rid = state.world.session(1).unwrap().runtime_id;

        let pump = |state: &mut ServerState| {
            for _ in 0..200 {
                state.pump_scripts();
                if state.running_script_count() == 0 {
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
        };

        // Resistance (if Damage.dat has named types): privileged set, unpriv refused.
        if let Some(dmg) = state.damage_types.names.first().filter(|n| !n.is_empty()).cloned() {
            let src = format!("Function Main()\n\tp = Actor()\n\tSetResistance(p, \"{dmg}\", -50)\nEnd Function\n");
            state.start_inline_script(&src, "Main", rid, 0, 1, true);
            pump(&mut state);
            assert_eq!(
                state.accounts.find("hero").unwrap().characters[0].actor.resistances[0],
                -50,
                "privileged SetResistance applied"
            );
            let evil = format!("Function Main()\n\tp = Actor()\n\tSetResistance(p, \"{dmg}\", 100)\nEnd Function\n");
            state.start_inline_script(&evil, "Main", rid, 0, 1, false);
            pump(&mut state);
            assert_eq!(
                state.accounts.find("hero").unwrap().characters[0].actor.resistances[0],
                -50,
                "unprivileged SetResistance is gated out"
            );
        }

        // ActorZone read: stash it into a script-global and verify the area name.
        let zsrc = "Function Main()\n\tp = Actor()\n\tSetActorGlobal(p, 0, ActorZone(p))\nEnd Function\n";
        state.start_inline_script(zsrc, "Main", rid, 0, 1, false);
        pump(&mut state);
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.script_globals[0],
            area,
            "ActorZone returns the actor's current area name"
        );
    }

    #[test]
    fn faction_bvms_mutate_ratings_and_gate() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("factionbvm");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        // Find a named faction + its index from the loaded grid.
        let mut fidx = None;
        for i in 0..100 {
            if !state.factions.name(i).is_empty() {
                fidx = Some((i, state.factions.name(i).to_string()));
                break;
            }
        }
        let Some((idx, fname)) = fidx else {
            eprintln!("skipping: no named factions in Factions.dat");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        let pump = |state: &mut ServerState| {
            for _ in 0..200 {
                state.pump_scripts();
                if state.running_script_count() == 0 {
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
        };
        let rating = |state: &ServerState| -> u8 {
            state.accounts.find("hero").unwrap().characters[0].actor.faction_ratings[idx]
        };

        // Privileged SetFactionRating(p, faction, 50) → stored 150 (value+100).
        let src = format!("Function Main()\n\tp = Actor()\n\tSetFactionRating(p, \"{fname}\", 50)\nEnd Function\n");
        state.start_inline_script(&src, "Main", rid, 0, 1, true);
        pump(&mut state);
        assert_eq!(rating(&state), 150, "SetFactionRating stores value+100");

        // Unprivileged SetFactionRating is refused.
        let evil = format!("Function Main()\n\tp = Actor()\n\tSetFactionRating(p, \"{fname}\", -100)\nEnd Function\n");
        state.start_inline_script(&evil, "Main", rid, 0, 1, false);
        pump(&mut state);
        assert_eq!(rating(&state), 150, "unprivileged SetFactionRating is gated out");
    }

    #[test]
    fn maxattr_and_reputation_bvms_gate_and_mutate() {
        use crate::state::ServerState;
        use rcce_script::{Host, Value};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("maxattr");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let hidx = state.health_stat;
        let Some(attr) = state.attr_names.name(hidx).map(str::to_string) else {
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        state.accounts.find_mut("hero").unwrap().characters[0].actor.attributes.maximum[hidx] = 100;
        let rid = state.world.session(1).unwrap().runtime_id as i64;

        // Privileged: direct and delta maximum writes saturate safely.
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
                catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
                actor: rid, ctx: 0, privileged: true, dirty: false, out: Vec::new(),
            };
            host.call("setmaxattribute", &[Value::Int(rid), Value::Str(attr.clone()), Value::Int(100)]);
            host.call("changemaxattribute", &[Value::Int(rid), Value::Str(attr.clone()), Value::Int(i32::MAX as i64)]);
            host.call("setmaxattribute", &[Value::Int(rid), Value::Str(attr.clone()), Value::Int(32768)]);
            host.call("setreputation", &[Value::Int(rid), Value::Int(-500)]);
            let rep = host.call("reputation", &[Value::Int(rid)]).to_int();
            assert_eq!(rep, -500, "Reputation read reflects the set");
            assert!(
                host.out.iter().any(|o| {
                    o.msg_type == P_STAT_UPDATE
                        && o.payload.len() == 6
                        && o.payload[0] == b'M'
                        && o.payload[4..6] == i16::MAX.to_le_bytes()
                }),
                "SetMaxAttribute broadcasts the saturated 'M' stat update"
            );
        }
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.attributes.maximum[hidx],
            i16::MAX,
            "max attribute saturates at the signed persistence maximum"
        );
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.reputation, -500);

        // Unprivileged: both writes are refused (brick-vector gate).
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
                catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
                actor: rid, ctx: 0, privileged: false, dirty: false, out: Vec::new(),
            };
            host.call("setmaxattribute", &[Value::Int(rid), Value::Str(attr.clone()), Value::Int(50)]);
            host.call("setreputation", &[Value::Int(rid), Value::Int(999)]);
        }
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.attributes.maximum[hidx],
            i16::MAX,
            "unprivileged SetMaxAttribute is gated out"
        );
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.reputation,
            -500,
            "unprivileged SetReputation is gated out"
        );
    }

    #[test]
    fn gold_bvms_set_change_and_broadcast() {
        use crate::state::ServerState;
        use rcce_script::{Host, Value};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("goldbvm");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id as i64;

        let mut host = crate::scripts::ScriptHost {
            world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
            catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
            actor: rid, ctx: 0, privileged: true, dirty: false, out: Vec::new(),
        };
        // SetGold(actor, 500) → wallet 500, P_GoldChange "U" + 500.
        host.call("setgold", &[Value::Int(rid), Value::Int(500)]);
        // ChangeMoney(actor, -200) → wallet 300, P_GoldChange "D" + 200.
        host.call("changemoney", &[Value::Int(rid), Value::Int(-200)]);
        // Money(actor) reads the same field as Gold.
        let money = host.call("money", &[Value::Int(rid)]).to_int();
        assert_eq!(money, 300, "Money is an alias of Gold");

        let golds: Vec<_> = host.out.iter().filter(|o| o.msg_type == P_GOLD_CHANGE).collect();
        assert_eq!(golds.len(), 2, "two wallet changes broadcast P_GoldChange");
        assert_eq!(golds[0].payload[0], b'U', "set 500 from 0 is a gain");
        assert_eq!(u32::from_le_bytes(golds[0].payload[1..5].try_into().unwrap()), 500);
        assert_eq!(golds[1].payload[0], b'D', "−200 is a loss");
        assert_eq!(u32::from_le_bytes(golds[1].payload[1..5].try_into().unwrap()), 200);
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.gold, 300);
    }

    #[test]
    fn killing_grants_xp_and_levelup_script_advances_level() {
        use crate::spawn::NpcActor;
        use crate::state::ServerState;
        let dir = data_dir();
        if state_skip_no_levelup(&dir) {
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(player_t) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        // An NPC race with a positive XP multiplier → a kill grants ≥1 XP.
        let Some(mob_t) = catalog.templates.values().find(|t| t.xp_multiplier > 0) else {
            eprintln!("skipping: no positive-XP-multiplier template");
            return;
        };
        let mob_id = mob_t.id;
        let player_id = player_t.id;
        let start_area = player_t.start_area.clone();
        let mut store = tmp_store("levelup");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = player_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("LevelUp").is_none() {
            eprintln!("skipping: LevelUp.rsl did not parse");
            return;
        }
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        state.world.warp_session(1, area.clone(), 0.0, 0.0, 0.0);
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.level, 0, "starts at level 0");

        // Stage a one-HP mob at the player and kill it.
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: mob_id,
            area: area.clone(),
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 1,
            hp_max: 1,
            target_peer: None,
            last_attack_ms: 0,
            script: String::new(),
            death_script: String::new(),
            stock: Vec::new(),
        });
        let attack = npc_rid.to_le_bytes().to_vec();
        let mut now = 0u64;
        for _ in 0..16 {
            now += state.combat_delay as u64 + 1;
            let outs = state.handle_attack(1, &attack, now);
            if outs.iter().any(|o| o.msg_type == P_ACTOR_DEAD) {
                break;
            }
        }
        // Drive the LevelUp script to completion; it applies SetActorLevel.
        let mut leveled_pkt = false;
        for _ in 0..200 {
            let outs = state.pump_scripts();
            if outs.iter().any(|o| {
                o.msg_type == P_XP_UPDATE && o.payload.first() == Some(&b'U')
            }) {
                leveled_pkt = true;
            }
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        let lvl = state.accounts.find("hero").unwrap().characters[0].actor.level;
        assert!(lvl >= 1, "the LevelUp script should advance the player past level 0 (got {lvl})");
        assert!(leveled_pkt, "SetActorLevel broadcasts a P_XPUpdate 'U' to the player");
    }

    /// Skip the level-up test when the data dir lacks `LevelUp.rsl`.
    fn state_skip_no_levelup(dir: &std::path::Path) -> bool {
        !dir.join("Server Data/Scripts/LevelUp.rsl").exists()
    }

    #[test]
    fn setactortarget_and_aistate_roundtrip() {
        use crate::spawn::NpcActor;
        use crate::state::ServerState;
        use rcce_server_core::faction::FactionData;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("aistate");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        let hero_rid = state.world.session(1).unwrap().runtime_id;
        // Hostile grid so the target is valid.
        state.factions = FactionData::default();

        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid, actor_id: template_id, area: area.clone(),
            x: 0.0, y: 0.0, z: 0.0, hp: 50, hp_max: 50,
            target_peer: None, last_attack_ms: 0,
            script: String::new(), death_script: String::new(), stock: Vec::new(),
        });

        let src = format!(
            "Function Main()\n\tp = Actor()\n\tSetActorTarget({npc_rid}, {hero_rid})\n\tSetActorGlobal(p, 0, ActorTarget({npc_rid}))\n\tSetActorAIState({npc_rid}, 5)\n\tSetActorGlobal(p, 1, ActorAIState({npc_rid}))\nEnd Function\n"
        );
        state.start_inline_script(&src, "Main", hero_rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert_eq!(state.spawns.npc(npc_rid).unwrap().target_peer, Some(1), "SetActorTarget set the NPC's target");
        let g = &state.accounts.find("hero").unwrap().characters[0].actor.script_globals;
        assert_eq!(g[0], hero_rid.to_string(), "ActorTarget reads back the player runtime id");
        assert_eq!(g[1], "5", "ActorAIState round-trips the set value");
    }

    #[test]
    fn pet_follows_its_leader() {
        use crate::spawn::NpcActor;
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("pet");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        state.world.warp_session(1, area.clone(), 0.0, 0.0, 0.0);
        let hero_rid = state.world.session(1).unwrap().runtime_id;

        // Stage an NPC far from the player.
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid, actor_id: template_id, area: area.clone(),
            x: 100.0, y: 0.0, z: 0.0, hp: 50, hp_max: 50,
            target_peer: None, last_attack_ms: 0,
            script: String::new(), death_script: String::new(), stock: Vec::new(),
        });

        // SetLeader(npc, hero) via a privileged script.
        let src = format!("Function Main()\n\tSetLeader({npc_rid}, {hero_rid})\nEnd Function\n");
        state.start_inline_script(&src, "Main", hero_rid, 0, 1, true);
        for _ in 0..200 {
            state.pump_scripts();
            if state.running_script_count() == 0 { break; }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert_eq!(state.spawns.npc_leader(npc_rid), Some(hero_rid), "the NPC is now a pet of the player");
        assert_eq!(state.spawns.pet_count(hero_rid), 1, "ActorPets counts the pet");

        // The pet follows: it steps toward the player + broadcasts.
        let x0 = state.spawns.npc(npc_rid).unwrap().x;
        let mut moved = false;
        let mut broadcast = false;
        for _ in 0..100 {
            let outs = state.collect_npc_pet_follow();
            if outs.iter().any(|o| o.msg_type == P_STANDARD_UPDATE) {
                broadcast = true;
            }
            if state.spawns.npc(npc_rid).unwrap().x < x0 - 0.5 {
                moved = true;
                break;
            }
        }
        assert!(moved, "the pet should walk toward its leader");
        assert!(broadcast, "pet movement is broadcast");
    }

    #[test]
    fn attacked_npc_calls_nearby_allies_for_help() {
        use crate::spawn::NpcActor;
        use crate::state::ServerState;
        use rcce_server_core::blitz_io::Writer;
        use rcce_server_core::faction::FactionData;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(player_t) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        // Two of the same aggressive race so they rate each other friendly.
        let Some(aggr_t) = catalog.templates.values().find(|t| t.aggressiveness == 2) else {
            eprintln!("skipping: no aggressive template");
            return;
        };
        let aggr_id = aggr_t.id;
        let player_id = player_t.id;
        let start_area = player_t.start_area.clone();

        let mut store = tmp_store("help");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = player_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        c.attributes.value[0] = 100;
        c.attributes.maximum[0] = 100;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        state.world.warp_session(1, area.clone(), 0.0, 0.0, 0.0);

        // Friendly grid (all 200 ≥ 190): same-faction NPCs help each other.
        let mut w = Writer::new();
        for _ in 0..100 {
            w.string("");
        }
        for _ in 0..10_000 {
            w.u8(200);
        }
        state.factions = FactionData::parse(&w.into_bytes());

        // Stage the attacked NPC + a nearby ally, both idle, both at the origin.
        let mut rids = Vec::new();
        for _ in 0..2 {
            let rid = state.world.alloc_runtime();
            state.spawns.insert_npc(NpcActor {
                runtime_id: rid,
                actor_id: aggr_id,
                area: area.clone(),
                x: 1.0,
                y: 0.0,
                z: 1.0,
                // A zero resistance intentionally gives this template 100 fewer
                // armour-equivalent points than neutral. Keep this fixture alive
                // through one legal hit so it can observe retaliation and help.
                hp: 10_000,
                hp_max: 10_000,
                target_peer: None,
                last_attack_ms: 0,
                script: String::new(),
                death_script: String::new(),
                stock: Vec::new(),
            });
            rids.push(rid);
        }
        let (attacked, ally) = (rids[0], rids[1]);

        // The player attacks the first NPC.
        let attack = attacked.to_le_bytes().to_vec();
        let _ = state.handle_attack(1, &attack, state.combat_delay as u64 + 1);

        assert_eq!(
            state.spawns.npc(attacked).unwrap().target_peer,
            Some(1),
            "the attacked NPC retaliates"
        );
        assert_eq!(
            state.spawns.npc(ally).unwrap().target_peer,
            Some(1),
            "a nearby allied NPC joins the fight (AICallForHelp)"
        );
    }

    #[test]
    fn aggressive_npc_aggros_disliked_player_but_spares_allies() {
        use crate::spawn::NpcActor;
        use crate::state::ServerState;
        use rcce_server_core::blitz_io::Writer;
        use rcce_server_core::faction::FactionData;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        // A player race to control + an aggressive (Aggressiveness==2) NPC race.
        let Some(player_t) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let Some(aggr_t) = catalog.templates.values().find(|t| t.aggressiveness == 2) else {
            eprintln!("skipping: no aggressive template in catalog");
            return;
        };
        let aggr_id = aggr_t.id;
        let aggr_range = aggr_t.aggressive_range as f32;
        let player_id = player_t.id;
        let start_area = player_t.start_area.clone();

        let mut store = tmp_store("aggro");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = player_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        state.world.warp_session(1, area.clone(), 0.0, 0.0, 0.0);

        // Stage the aggressive NPC just inside aggro range of the player.
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: aggr_id,
            area: area.clone(),
            x: aggr_range * 0.5,
            y: 0.0,
            z: 0.0,
            hp: 100,
            hp_max: 100,
            target_peer: None,
            last_attack_ms: 0,
            script: String::new(),
            death_script: String::new(),
            stock: Vec::new(),
        });

        // Allied grid (every rating 200 ≥ 150): the NPC spares the player.
        let mut w = Writer::new();
        for _ in 0..100 {
            w.string("");
        }
        for _ in 0..10_000 {
            w.u8(200);
        }
        state.factions = FactionData::parse(&w.into_bytes());
        state.run_npc_aggro();
        assert!(
            state.spawns.npc(npc_rid).unwrap().target_peer.is_none(),
            "an NPC strongly allied to the player's faction does not aggro"
        );

        // Hostile grid (all-zero default < 150): the NPC engages.
        state.factions = FactionData::default();
        state.run_npc_aggro();
        assert_eq!(
            state.spawns.npc(npc_rid).unwrap().target_peer,
            Some(1),
            "an aggressive NPC engages a disliked player in range"
        );

        // Out of range → no aggro even when hostile.
        state.spawns.clear_target(npc_rid);
        state.world.warp_session(1, area.clone(), aggr_range * 10.0, 0.0, 0.0);
        state.run_npc_aggro();
        assert!(
            state.spawns.npc(npc_rid).unwrap().target_peer.is_none(),
            "a player beyond AggressiveRange is not aggroed"
        );
    }

    #[test]
    fn proximity_trigger_fires_its_script_on_entry() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable && Area::load(&dir, &t.start_area).is_some()) else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("trigger");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);

        // Find an area + trigger whose script is loaded in the registry.
        let areas_dir = dir.join("Server Data/Areas");
        let Ok(entries) = std::fs::read_dir(&areas_dir) else { return };
        let mut found = None;
        for e in entries.flatten() {
            let p = e.path();
            if p.extension().and_then(|x| x.to_str()) != Some("dat") { continue; }
            let name = p.file_stem().unwrap().to_string_lossy().to_string();
            if let Some(a) = Area::load(&dir, &name) {
                for t in a.triggers.iter() {
                    if !t.script.is_empty() && state.scripts.get(&t.script).is_some() {
                        found = Some((name.clone(), t.x, t.y, t.z));
                        break;
                    }
                }
            }
            if found.is_some() { break; }
        }
        let Some((area_name, tx, ty, tz)) = found else {
            eprintln!("skipping: no area has a scripted trigger with a loaded script");
            return;
        };

        // Stand the player at the trigger centre and run the per-tick check.
        state.world.warp_session(1, area_name.clone(), tx, ty, tz);
        let before = state.running_script_count();
        state.run_triggers();
        assert!(state.running_script_count() > before, "entering the trigger fired its script");
        // Re-running without moving does NOT re-fire (edge-triggered).
        let after_first = state.running_script_count();
        state.run_triggers();
        assert_eq!(state.running_script_count(), after_first, "the trigger does not re-fire while inside");
    }

    #[test]
    fn patrol_npc_walks_the_waypoint_graph() {
        use crate::state::ServerState;
        let dir = data_dir();
        let areas_dir = dir.join("Server Data/Areas");
        let Ok(entries) = std::fs::read_dir(&areas_dir) else {
            return;
        };
        // Find an area with a patrol spawn (range < 5) whose start waypoint has a
        // valid next edge leading to a *different* position (so movement happens).
        let mut found: Option<String> = None;
        for e in entries.flatten() {
            let p = e.path();
            if p.extension().and_then(|x| x.to_str()) != Some("dat") {
                continue;
            }
            let name = p.file_stem().unwrap().to_string_lossy().to_string();
            let Some(area) = Area::load(&dir, &name) else { continue };
            let ok = area.spawns.iter().any(|s| {
                if s.actor_id < 0 || s.max <= 0 || s.range >= 5.0 {
                    return false;
                }
                let wi = s.waypoint as usize;
                let (Some(start), Some(edge)) = (area.waypoints.get(wi), area.waypoint_graph.get(wi)) else {
                    return false;
                };
                for &n in &[edge.next_a, edge.next_b, edge.prev] {
                    if (0..2000).contains(&n) {
                        if let Some(dest) = area.waypoints.get(n as usize) {
                            if (dest[0] - start[0]).abs() > 2.0 || (dest[2] - start[2]).abs() > 2.0 {
                                return true;
                            }
                        }
                    }
                }
                false
            });
            if ok {
                found = Some(name);
                break;
            }
        }
        let Some(area_name) = found else {
            eprintln!("skipping: no patrol spawn with a movable waypoint edge");
            return;
        };

        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable && Area::load(&dir, &t.start_area).is_some()) else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("patrol");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        state.world.warp_session(1, area_name.clone(), 0.0, 0.0, 0.0);
        let _ = state.collect_world_broadcasts(); // populate the patrol area's NPCs

        let patrollers = state.spawns.patrollers();
        let Some(&(rid, ..)) = patrollers.first() else {
            eprintln!("skipping: no patroller spawned");
            return;
        };
        let (x0, z0) = state.spawns.npc(rid).map(|n| (n.x, n.z)).unwrap();

        let mut moved = false;
        let mut broadcast = false;
        for _ in 0..50 {
            let outs = state.collect_npc_patrol();
            if outs.iter().any(|o| o.msg_type == P_STANDARD_UPDATE) {
                broadcast = true;
            }
            let (x, z) = state.spawns.npc(rid).map(|n| (n.x, n.z)).unwrap();
            if (x - x0).abs() > 0.01 || (z - z0).abs() > 0.01 {
                moved = true;
                break;
            }
        }
        assert!(moved, "the patrol NPC should walk toward its next waypoint");
        assert!(broadcast, "patrol movement is broadcast");
    }

    #[test]
    fn idle_npc_wanders_within_its_spawn_range() {
        use crate::state::ServerState;
        let dir = data_dir();
        let areas_dir = dir.join("Server Data/Areas");
        let Ok(entries) = std::fs::read_dir(&areas_dir) else {
            eprintln!("skipping: no Areas dir");
            return;
        };
        // Find the first area with a wandering spawn (range >= 5, a live slot).
        let mut wander_area: Option<String> = None;
        for e in entries.flatten() {
            let p = e.path();
            if p.extension().and_then(|x| x.to_str()) != Some("dat") {
                continue;
            }
            let name = p.file_stem().unwrap().to_string_lossy().to_string();
            if let Some(area) = Area::load(&dir, &name) {
                if area.spawns.iter().any(|s| s.actor_id >= 0 && s.max > 0 && s.range >= 5.0) {
                    wander_area = Some(name);
                    break;
                }
            }
        }
        let Some(area_name) = wander_area else {
            eprintln!("skipping: no area has a wandering spawn");
            return;
        };

        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("wander");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        // Move the player into the wandering area so the wander step has an
        // audience (broadcasts only go to same-area players).
        state.world.warp_session(1, area_name.clone(), 0.0, 0.0, 0.0);
        let _ = state.collect_world_broadcasts(); // populates the area's NPCs

        // There is at least one idle wanderer.
        let wanderers = state.spawns.wanderers();
        assert!(!wanderers.is_empty(), "the area should have an idle wanderer");
        let rid = wanderers[0].0;
        let (x0, z0) = state.spawns.npc(rid).map(|n| (n.x, n.z)).unwrap();

        // Run the wander step repeatedly: the NPC picks a destination and walks
        // toward it, broadcasting its movement. (Bounded loop; assert it moved.)
        let mut moved = false;
        let mut any_broadcast = false;
        for _ in 0..50 {
            let outs = state.collect_npc_wander();
            if outs.iter().any(|o| o.msg_type == P_STANDARD_UPDATE) {
                any_broadcast = true;
            }
            let (x, z) = state.spawns.npc(rid).map(|n| (n.x, n.z)).unwrap();
            if (x - x0).abs() > 0.01 || (z - z0).abs() > 0.01 {
                moved = true;
                break;
            }
        }
        assert!(moved, "the idle NPC should wander from its spawn point");
        assert!(any_broadcast, "wander movement is broadcast to nearby players");

        // The NPC stays within a sane radius of its home (range + a step of
        // slack) — it doesn't run off to infinity.
        let (hx, hz, range) = state.spawns.wander_home(rid).unwrap();
        let (fx, fz) = state.spawns.npc(rid).map(|n| (n.x, n.z)).unwrap();
        let bound = range + 5.0;
        assert!((fx - hx).abs() <= bound && (fz - hz).abs() <= bound, "stays near home");
    }

    #[test]
    fn login_hook_runs_and_sends_welcome_output() {
        use crate::state::ServerState;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Login.rsl").exists() {
            eprintln!("skipping: no Login.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let mut store = tmp_store("login_hook");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template.id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Login").is_none() {
            eprintln!("skipping: Login.rsl did not parse");
            return;
        }

        // Drive P_StartGame through dispatch (which fires the Login hook).
        let outs = state.dispatch(1, P_START_GAME, &start_packet("hero", MD5, 0));
        // The Login script Output(...)s a welcome → at least one P_ChatMessage
        // colored-output packet to the player.
        let welcomes: Vec<_> = outs
            .iter()
            .filter(|o| o.msg_type == P_CHAT_MESSAGE && o.payload.first() == Some(&250))
            .collect();
        assert!(!welcomes.is_empty(), "Login should Output a welcome message");
        let text = String::from_utf8_lossy(&welcomes[0].payload[4..]);
        assert!(text.contains("Welcome"), "got: {text}");
    }

    #[test]
    fn async_dialog_runs_end_to_end() {
        use crate::state::ServerState;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Click_Test.rsl").exists() {
            eprintln!("skipping: no Click_Test.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let mut store = tmp_store("async_dialog");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template.id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Click_Test").is_none() {
            eprintln!("skipping: Click_Test.rsl did not parse");
            return;
        }
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        // Right-click the (synthetic) NPC → run Click_Test.Main async.
        state.fire_hook_async("Click_Test", "Main", rid, 0, 1);

        // Drive the dialog: pump (advance + emit packets, suspend at waits),
        // resume each wait with "1" (the open handle / option 1), until done.
        // Deadline outlasts the script's DoEvents(2000) ~2s busy-wait with margin;
        // we break the instant the script finishes, so the common case is ~2s.
        let mut dialog_packets: Vec<Vec<u8>> = Vec::new();
        let deadline = std::time::Instant::now() + Duration::from_secs(5);
        while std::time::Instant::now() < deadline {
            for out in state.pump_scripts() {
                if out.msg_type == P_DIALOG {
                    dialog_packets.push(out.payload);
                }
            }
            state.resume_script(1, "1".to_string());
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }

        assert_eq!(state.running_script_count(), 0, "the dialog script should run to completion");
        // An "N" open packet and a "T" text packet were sent…
        assert!(dialog_packets.iter().any(|p| p.first() == Some(&b'N')), "dialog opened");
        // …and choosing option 1 ran the branch that outputs "Hello There tester!".
        let branch = dialog_packets.iter().any(|p| {
            p.first() == Some(&b'T') && String::from_utf8_lossy(p).contains("Hello There tester")
        });
        assert!(branch, "option-1 branch DialogOutput should be sent; packets: {}", dialog_packets.len());
    }

    #[test]
    fn right_click_fires_npc_script_only_when_in_range_and_same_area() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Click_Test.rsl").exists() {
            eprintln!("skipping: no Click_Test.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("right_click");
        let mut acct = Account::new("clicker", MD5, "c@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Clicker".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Click_Test").is_none() {
            eprintln!("skipping: Click_Test.rsl did not parse");
            return;
        }
        handle_start_game(&start_packet("clicker", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();

        // Stage a scripted NPC co-located with the player (player spawns at 0,0,0).
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: template_id,
            area: area.clone(),
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 1,
            hp_max: 1,
            target_peer: None,
            last_attack_ms: 0,
            script: "Click_Test".into(),
            death_script: String::new(),
            stock: Vec::new(),
        });
        let wire = npc_rid.to_le_bytes();

        // Out of range: move the NPC far away → no script fires.
        state.spawns.npc(npc_rid); // (sanity: exists)
        let far_npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: far_npc_rid,
            actor_id: template_id,
            area: area.clone(),
            x: 10_000.0,
            y: 0.0,
            z: 10_000.0,
            hp: 1,
            hp_max: 1,
            target_peer: None,
            last_attack_ms: 0,
            script: "Click_Test".into(),
            death_script: String::new(),
            stock: Vec::new(),
        });
        state.handle_right_click(1, &far_npc_rid.to_le_bytes());
        assert_eq!(state.running_script_count(), 0, "out-of-range right-click must not fire");

        // Wrong area: same position but a different area → no script fires.
        let other_area_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: other_area_rid,
            actor_id: template_id,
            area: "##nowhere##".into(),
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 1,
            hp_max: 1,
            target_peer: None,
            last_attack_ms: 0,
            script: "Click_Test".into(),
            death_script: String::new(),
            stock: Vec::new(),
        });
        state.handle_right_click(1, &other_area_rid.to_le_bytes());
        assert_eq!(state.running_script_count(), 0, "cross-area right-click must not fire");

        // In range + same area: the NPC's script fires.
        state.handle_right_click(1, &wire);
        assert_eq!(state.running_script_count(), 1, "in-range same-area right-click fires the NPC script");

        // Idempotent while the conversation is live: a second click doesn't double-fire.
        state.handle_right_click(1, &wire);
        assert_eq!(state.running_script_count(), 1, "duplicate right-click must not start a second script");
    }

    #[test]
    fn script_commands_mutate_state_and_respect_privilege() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let mut store = tmp_store("cmds");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template.id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        c.gold = 10;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        let src = r#"Function Test()
	p = Actor()
	SetActorGlobal(p, 0, "flag-on")
	NewQuest(p, "MyQuest", "started")
	ChangeGold(p, 100)
End Function
"#;
        let prog = rcce_script::parser::parse(src).unwrap();

        // Privileged run: all three mutations take effect.
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world,
                accounts: &mut state.accounts,
                spawns: &state.spawns,
                catalog: &state.catalog,
                attr_names: &state.attr_names,
                rng: &mut state.rng,
                actor: rid as i64,
                ctx: 0,
                privileged: true,
                dirty: false,
                out: Vec::new(),
            };
            rcce_script::run_function(&prog, &mut host, "Test", vec![]);
            assert!(host.dirty);
        }
        let ch = &state.accounts.find("hero").unwrap().characters[0];
        assert_eq!(ch.actor.script_globals[0], "flag-on");
        // NewQuest stores [3 flag bytes] + description; QuestStatus (and the
        // wire) read the description past those 3 bytes.
        let q = ch.quests.iter().find(|q| q.name == "MyQuest").unwrap();
        assert_eq!(q.status.chars().skip(3).collect::<String>(), "started");
        assert_eq!(ch.actor.gold, 110); // 10 + 100 (privileged ChangeGold)

        // Unprivileged run: ChangeGold no-ops (gold unchanged), but globals still set.
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world,
                accounts: &mut state.accounts,
                spawns: &state.spawns,
                catalog: &state.catalog,
                attr_names: &state.attr_names,
                rng: &mut state.rng,
                actor: rid as i64,
                ctx: 0,
                privileged: false,
                dirty: false,
                out: Vec::new(),
            };
            rcce_script::run_function(&prog, &mut host, "Test", vec![]);
        }
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.gold, 110); // unchanged
    }

    #[test]
    fn attribute_commands_read_write_and_gate() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("attrs");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let Some(hidx) = state.attr_names.index_of("Health") else {
            eprintln!("skipping: Attributes.dat has no 'Health' slot");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        // Give the character a known Health window: max 100, current 100.
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.attributes.maximum[hidx] = 100;
            rec.actor.attributes.value[hidx] = 100;
        }

        // Privileged: SetAttribute clamps to [0,max] and ChangeAttribute adds.
        let src = "Function Test()\n  SetAttribute(Actor(), \"Health\", 40)\n  ChangeAttribute(Actor(), \"Health\", -15)\nEnd Function\n";
        let prog = rcce_script::parser::parse(src).unwrap();
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world,
                accounts: &mut state.accounts,
                spawns: &state.spawns,
                catalog: &state.catalog,
                attr_names: &state.attr_names,
                rng: &mut state.rng,
                actor: state.world.session(1).unwrap().runtime_id as i64,
                ctx: 0,
                privileged: true,
                dirty: false,
                out: Vec::new(),
            };
            rcce_script::run_function(&prog, &mut host, "Test", vec![]);
            // A stat broadcast was emitted for the local player.
            assert!(host.out.iter().any(|o| o.msg_type == P_STAT_UPDATE), "SetAttribute broadcasts P_StatUpdate");
        }
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[hidx], 25, "40 then -15 = 25");

        // Unprivileged: the write is refused (value unchanged at 25).
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world,
                accounts: &mut state.accounts,
                spawns: &state.spawns,
                catalog: &state.catalog,
                attr_names: &state.attr_names,
                rng: &mut state.rng,
                actor: state.world.session(1).unwrap().runtime_id as i64,
                ctx: 0,
                privileged: false,
                dirty: false,
                out: Vec::new(),
            };
            rcce_script::run_function(&prog, &mut host, "Test", vec![]);
        }
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[hidx], 25, "unprivileged SetAttribute is a no-op");
    }

    #[test]
    fn player_death_fires_the_death_script_and_respawns_with_half_health() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Death.rsl").exists() {
            eprintln!("skipping: no Death.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("player_death");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        c.level = 1;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Death").is_none() {
            eprintln!("skipping: Death.rsl did not parse");
            return;
        }
        let Some(hidx) = state.attr_names.index_of("Health") else {
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();

        // Give the player a known max and 1 HP, so the next NPC hit is lethal.
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.attributes.maximum[hidx] = 100;
            rec.actor.attributes.value[hidx] = 1;
        }
        // Stage an NPC already targeting the player.
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: template_id,
            area,
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 100,
            hp_max: 100,
            target_peer: Some(1),
            last_attack_ms: 0,
            script: String::new(),
            death_script: String::new(),
            stock: Vec::new(),
        });
        // Put the player in melee range of the staged NPC at the origin so the
        // range gate (collect_npc_attacks) lets the swing land.
        let parea = state.world.session(1).unwrap().area.clone();
        state.world.warp_session(1, parea, 0.0, 0.0, 0.0);

        // Let the NPC attack until the player dies and the Death script fires.
        let mut now = 0u64;
        let mut fired = false;
        for _ in 0..32 {
            now += state.combat_delay as u64 + 1;
            state.collect_npc_attacks(now);
            if state.running_script_count() >= 1 {
                fired = true;
                break;
            }
        }
        assert!(fired, "the Death script should fire when the player is killed");
        // No NPC still targets the dead player (corpse isn't re-killed).
        assert!(state.spawns.npc(npc_rid).unwrap().target_peer.is_none(), "death clears NPC targets");

        // Pump the Death script to completion (it Outputs, waits, restores HP).
        let deadline = std::time::Instant::now() + Duration::from_secs(5);
        while std::time::Instant::now() < deadline {
            state.pump_scripts();
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }
        assert_eq!(state.running_script_count(), 0, "Death script runs to completion");
        // Respawned at half of max health (Death: SetAttribute Health = max/2).
        let hp = state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[hidx];
        assert_eq!(hp, 50, "Death restores HP to max/2 (100/2)");
        // Death warps the player to the Plains "Begin" portal (respawn-at-town).
        if Area::load(&dir, "Plains").is_some() {
            assert!(
                state.world.session(1).unwrap().area.eq_ignore_ascii_case("Plains"),
                "Death warps respawn to Plains; got '{}'",
                state.world.session(1).unwrap().area
            );
        }
    }

    #[test]
    fn killing_a_looted_npc_grants_the_killer_loot() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/MonsterLoot.rsl").exists() {
            eprintln!("skipping: no MonsterLoot.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("loot");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        c.level = 1;
        c.gold = 0;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("MonsterLoot").is_none() {
            eprintln!("skipping: MonsterLoot.rsl did not parse");
            return;
        }
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();

        // A 1-HP NPC that drops MonsterLoot on death, co-located with the player.
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: template_id,
            area,
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 1,
            hp_max: 1,
            target_peer: None,
            last_attack_ms: 0,
            script: String::new(),
            death_script: "MonsterLoot".into(),
            stock: Vec::new(),
        });

        // Kill it.
        let attack = npc_rid.to_le_bytes().to_vec();
        let mut now = 0u64;
        let mut killed = false;
        for _ in 0..16 {
            now += state.combat_delay as u64 + 1;
            if state.handle_attack(1, &attack, now).iter().any(|o| o.msg_type == P_ACTOR_DEAD) {
                killed = true;
                break;
            }
        }
        assert!(killed, "the 1-HP NPC should die");

        // Drive MonsterLoot to completion (ChangeGold always grants gold).
        let deadline = std::time::Instant::now() + Duration::from_secs(3);
        while std::time::Instant::now() < deadline {
            state.pump_scripts();
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }
        assert!(
            state.accounts.find("hero").unwrap().characters[0].actor.gold > 0,
            "killing a looted NPC grants the killer gold via its death script"
        );
    }

    #[test]
    fn merchant_dialog_emits_options_with_live_client_responses() {
        // Reproduce the LIVE flow: respond to each P_Dialog packet exactly as the
        // real client does (handle_dialog_response with wire bytes), not the
        // resume_script("1") shortcut, and assert the "O" options packet appears.
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Click_Merchant.rsl").exists() {
            eprintln!("skipping: no Click_Merchant.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("merchant_live");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        c.gold = 100;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Click_Merchant").is_none() {
            return;
        }
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid, actor_id: template_id, area, x: 0.0, y: 0.0, z: 0.0,
            hp: 1, hp_max: 1, target_peer: None, last_attack_ms: 0,
            script: "Click_Merchant".into(), death_script: String::new(), stock: Vec::new(),
        });

        state.handle_right_click(1, &npc_rid.to_le_bytes());
        let mut saw_options = false;
        let mut script_handle = [0u8; 4];
        let deadline = std::time::Instant::now() + Duration::from_secs(3);
        while std::time::Instant::now() < deadline && !saw_options {
            for o in state.pump_scripts() {
                if o.msg_type != crate::world::P_DIALOG {
                    continue;
                }
                match o.payload.first() {
                    Some(b'N') => {
                        script_handle.copy_from_slice(&o.payload[1..5]);
                        // Client reply: "N" + scriptHandle + dhandle(=scriptHandle).
                        let mut r = vec![b'N'];
                        r.extend_from_slice(&script_handle);
                        r.extend_from_slice(&script_handle);
                        state.handle_dialog_response(1, &r);
                    }
                    Some(b'T') => {
                        let mut r = vec![b'T'];
                        r.extend_from_slice(&script_handle);
                        state.handle_dialog_response(1, &r);
                    }
                    Some(b'O') => {
                        // Options packet: "O" + dhandle(4) + [len][opt]… — must have ≥1 option.
                        assert!(o.payload.len() > 5, "options packet carries option text");
                        saw_options = true;
                    }
                    _ => {}
                }
            }
            std::thread::sleep(Duration::from_millis(1));
        }
        assert!(saw_options, "the merchant dialog must emit an 'O' options packet (live response pattern)");
    }

    #[test]
    fn buying_from_the_dialog_merchant_charges_gold_and_grants_the_item() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Click_Merchant.rsl").exists() {
            eprintln!("skipping: no Click_Merchant.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("merchant");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        c.gold = 100;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Click_Merchant").is_none() {
            eprintln!("skipping: Click_Merchant.rsl did not parse");
            return;
        }
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();

        // A co-located merchant NPC whose right-click runs Click_Merchant.
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: template_id,
            area,
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 1,
            hp_max: 1,
            target_peer: None,
            last_attack_ms: 0,
            script: "Click_Merchant".into(),
            death_script: String::new(),
            stock: Vec::new(),
        });

        // Right-click the merchant → dialog shop. Resume each wait with "1"
        // (open handle, output acks, and DialogInput choice 1 = Potion of Healing).
        state.handle_right_click(1, &npc_rid.to_le_bytes());
        let mut give_handle: Option<u32> = None;
        let mut give_item_id: Option<u16> = None;
        let deadline = std::time::Instant::now() + Duration::from_secs(5);
        while std::time::Instant::now() < deadline {
            for o in state.pump_scripts() {
                if o.msg_type == P_INVENTORY_UPDATE && o.payload.first() == Some(&b'G') && o.payload.len() >= 7 {
                    give_handle = Some(u32::from_le_bytes([o.payload[1], o.payload[2], o.payload[3], o.payload[4]]));
                    give_item_id = Some(u16::from_le_bytes([o.payload[5], o.payload[6]]));
                }
            }
            state.resume_script(1, "1".to_string());
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }

        // The merchant charged 25 gold (ChangeGold) and offered an item (GiveItem).
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.gold, 75,
            "buying charged 25 gold"
        );
        let handle = give_handle.expect("the merchant offered an item via GiveItem");
        let item_id = give_item_id.unwrap();

        // Client accepts the item into a backpack slot.
        let mut reply = vec![b'G', b'Y'];
        reply.extend_from_slice(&handle.to_le_bytes());
        reply.push(14u8);
        state.handle_inventory_update(1, &reply);
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.inventory[14].item.as_ref().map(|i| i.item_id),
            Some(item_id),
            "the bought item lands in the inventory"
        );
    }

    #[test]
    fn two_players_trade_items_and_gold() {
        use crate::state::ServerState;
        use rcce_server_core::item::ItemInstance;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Default.rsl").exists() {
            eprintln!("skipping: no Default.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("p2ptrade");
        for (name, email) in [("hero", "h@x.com"), ("rival", "r@x.com")] {
            let mut acct = Account::new(name, MD5, email).unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = name.into();
            c.area = start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Default").is_none() {
            return;
        }
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("rival", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);
        let rival_rid = state.world.session(2).unwrap().runtime_id;

        // Items go in backpack slot 0 = inventory slot 14 (SlotI_Backpack).
        // Hero: item 5, 100 gold. Rival: item 6, 50 gold.
        {
            let h = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            h.actor.inventory[14].item = Some(ItemInstance::new(5));
            h.actor.inventory[14].amount = 1;
            h.actor.gold = 100;
        }
        {
            let r = &mut state.accounts.find_mut("rival").unwrap().characters[0];
            r.actor.inventory[14].item = Some(ItemInstance::new(6));
            r.actor.inventory[14].amount = 1;
            r.actor.gold = 50;
        }

        // Hero initiates trade with rival → Default.Trade → OpenTrading pairs them.
        state.handle_trade(1, &rival_rid.to_le_bytes());
        let deadline = std::time::Instant::now() + Duration::from_secs(3);
        while std::time::Instant::now() < deadline {
            state.pump_scripts();
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }

        // Each offers their item: P_UpdateTrading [u8 backpackSlot][u16 amount].
        // Inventory slot 8 = backpack slot 0 (SlotI_Backpack).
        state.handle_update_trading(1, &[0u8, 1, 0]);
        state.handle_update_trading(2, &[0u8, 1, 0]);

        // Accept: hero pays 30 (accept_gold +30); rival receives (mirror −30).
        let hero_accept = 30i32.to_le_bytes().to_vec();
        let rival_accept = (-30i32).to_le_bytes().to_vec();
        state.handle_open_trading(1, &hero_accept);
        let out = state.handle_open_trading(2, &rival_accept);
        assert!(
            out.iter().any(|o| o.msg_type == crate::world::P_CLOSE_TRADING),
            "completing a trade closes both windows"
        );

        // Hero: lost item 5 + 30 gold, gained item 6. Rival: mirror.
        let h = &state.accounts.find("hero").unwrap().characters[0].actor;
        let r = &state.accounts.find("rival").unwrap().characters[0].actor;
        assert_eq!(h.gold, 70, "hero paid 30 gold");
        assert_eq!(r.gold, 80, "rival received 30 gold");
        assert!(h.inventory.iter().any(|s| s.item.as_ref().map(|i| i.item_id) == Some(6)), "hero got rival's item");
        assert!(!h.inventory.iter().any(|s| s.item.as_ref().map(|i| i.item_id) == Some(5)), "hero gave away their item");
        assert!(r.inventory.iter().any(|s| s.item.as_ref().map(|i| i.item_id) == Some(5)), "rival got hero's item");
        assert!(!r.inventory.iter().any(|s| s.item.as_ref().map(|i| i.item_id) == Some(6)), "rival gave away their item");
    }

    #[test]
    fn buying_from_a_stocked_vendor_charges_gold_and_grants_the_item() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Default.rsl").exists() {
            eprintln!("skipping: no Default.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("vendorbuy");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        c.gold = 1000;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Default").is_none() {
            return;
        }
        let Some((item_id, value)) = state
            .items
            .items
            .iter()
            .find(|d| d.value > 0 && d.value <= 400)
            .map(|d| (d.id, d.value))
        else {
            eprintln!("skipping: no affordable valued item");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();

        // A co-located vendor stocking 5 of the item.
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: template_id,
            area,
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 1,
            hp_max: 1,
            target_peer: None,
            last_attack_ms: 0,
            script: String::new(),
            death_script: String::new(),
            stock: vec![(item_id, 5)],
        });

        // Trade → Default.Trade → OpenTrading sends the stocked window.
        state.handle_trade(1, &npc_rid.to_le_bytes());
        let mut window: Option<Vec<u8>> = None;
        let deadline = std::time::Instant::now() + Duration::from_secs(3);
        while std::time::Instant::now() < deadline {
            for o in state.pump_scripts() {
                if o.msg_type == crate::world::P_OPEN_TRADING && o.payload.len() > 2 {
                    window = Some(o.payload);
                }
            }
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }
        let window = window.expect("vendor opened a stocked window");
        // "11" + [83-byte item][u16 amount][u32 handle] → handle at offset 87.
        assert!(window.len() >= 91, "window carries the stock entry");
        let handle = u32::from_le_bytes([window[87], window[88], window[89], window[90]]);

        // Accept the trade BUYING 2 of the stocked item (bought entry at offset 0).
        let mut buy = vec![0u8; 288];
        buy[0..4].copy_from_slice(&handle.to_le_bytes());
        buy[4..6].copy_from_slice(&2u16.to_le_bytes());
        let out = state.handle_open_trading(1, &buy);

        assert!(
            out.iter().any(|o| o.msg_type == crate::world::P_GOLD_CHANGE),
            "buying sends a P_GoldChange"
        );
        let rec = &state.accounts.find("hero").unwrap().characters[0];
        assert_eq!(rec.actor.gold, 1000 - value * 2, "charged value × 2");
        assert!(
            rec.actor.inventory.iter().any(|s| s.item.as_ref().map(|i| i.item_id) == Some(item_id)),
            "the bought item is in the inventory"
        );

        // The paid handle can't be claimed for free via the GiveItem reply.
        let mut free_grab = vec![b'G', b'Y'];
        free_grab.extend_from_slice(&handle.to_le_bytes());
        free_grab.push(20u8);
        state.handle_inventory_update(1, &free_grab);
        assert!(
            state.accounts.find("hero").unwrap().characters[0].actor.inventory[20].item.is_none(),
            "vendor stock can't be claimed for free through the GiveItem reply"
        );
    }

    #[test]
    fn trading_with_a_vendor_sells_items_for_gold() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        use rcce_server_core::item::ItemInstance;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Default.rsl").exists() {
            eprintln!("skipping: no Default.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("trade");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        c.gold = 0;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Default").is_none() {
            eprintln!("skipping: Default.rsl did not parse");
            return;
        }
        let Some((item_id, value)) = state
            .items
            .items
            .iter()
            .find(|d| d.value > 0)
            .map(|d| (d.id, d.value))
        else {
            eprintln!("skipping: no item with positive value");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();

        // Give the player 2 sellable items in slot 8; stage a co-located vendor NPC.
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.inventory[8].item = Some(ItemInstance::new(item_id));
            rec.actor.inventory[8].amount = 2;
        }
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: template_id,
            area,
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 1,
            hp_max: 1,
            target_peer: None,
            last_attack_ms: 0,
            script: String::new(),
            death_script: String::new(),
            stock: Vec::new(),
        });

        // Trade the vendor → Default.Trade → OpenTrading opens the window.
        state.handle_trade(1, &npc_rid.to_le_bytes());
        let mut opened = false;
        let deadline = std::time::Instant::now() + Duration::from_secs(3);
        while std::time::Instant::now() < deadline {
            for o in state.pump_scripts() {
                if o.msg_type == crate::world::P_OPEN_TRADING {
                    opened = true;
                }
            }
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }
        assert!(opened, "trading a vendor opens the P_OpenTrading window");

        // Accept the trade selling 2 from slot 8: sold entries start at byte 192.
        let mut sell = vec![0u8; 288];
        sell[192] = 8; // slot
        sell[193..195].copy_from_slice(&2u16.to_le_bytes()); // amount
        let out = state.handle_open_trading(1, &sell);

        // Gold credited, item gone, P_GoldChange sent.
        assert!(
            out.iter().any(|o| o.msg_type == crate::world::P_GOLD_CHANGE),
            "selling sends a P_GoldChange"
        );
        let rec = &state.accounts.find("hero").unwrap().characters[0];
        assert_eq!(rec.actor.gold, value * 2, "gold = item value × amount sold");
        assert!(rec.actor.inventory[8].item.is_none(), "sold items leave the inventory");
    }

    #[test]
    fn give_item_offers_then_places_the_item_on_client_reply() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("giveitem");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let Some((item_name, item_id)) = state
            .items
            .items
            .iter()
            .find(|d| !d.name.is_empty())
            .map(|d| (d.name.clone(), d.id))
        else {
            eprintln!("skipping: no named items in Items.dat");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        // GiveItem → the server offers it via a "G" packet (handle + id + amount).
        let offer = state.give_item(rid, &item_name, 2);
        let g = offer
            .iter()
            .find(|o| o.msg_type == P_INVENTORY_UPDATE && o.payload.first() == Some(&b'G'))
            .expect("GiveItem sends a G offer");
        let handle = u32::from_le_bytes([g.payload[1], g.payload[2], g.payload[3], g.payload[4]]);
        let offered_id = u16::from_le_bytes([g.payload[5], g.payload[6]]);
        assert_eq!(offered_id, item_id, "offer names the granted item");

        // Client replies "G" + Y + handle + backpack slot 14.
        let mut reply = vec![b'G', b'Y'];
        reply.extend_from_slice(&handle.to_le_bytes());
        reply.push(14u8);
        state.handle_inventory_update(1, &reply);

        let rec = &state.accounts.find("hero").unwrap().characters[0];
        assert_eq!(
            rec.actor.inventory[14].item.as_ref().map(|i| i.item_id),
            Some(item_id),
            "the granted item is placed in the chosen slot"
        );
        assert_eq!(rec.actor.inventory[14].amount, 2, "with the granted amount");

        // A second reply for the now-consumed handle does nothing.
        let before = state.accounts.find("hero").unwrap().characters[0].actor.inventory[15].item.clone();
        let mut reply2 = vec![b'G', b'Y'];
        reply2.extend_from_slice(&handle.to_le_bytes());
        reply2.push(15u8);
        state.handle_inventory_update(1, &reply2);
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.inventory[15].item, before,
            "a consumed give-handle can't be replayed into another slot"
        );
    }

    #[test]
    fn adding_merges_matching_stacks() {
        use crate::state::ServerState;
        use rcce_server_core::item::ItemInstance;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("stackmerge");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        // Two stacks of the SAME item in slots 8 and 9, and a different item in 10.
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.inventory[8].item = Some(ItemInstance::new(5));
            rec.actor.inventory[8].amount = 2;
            rec.actor.inventory[9].item = Some(ItemInstance::new(5));
            rec.actor.inventory[9].amount = 3;
            rec.actor.inventory[10].item = Some(ItemInstance::new(6));
            rec.actor.inventory[10].amount = 1;
        }

        // Merge all of slot 8 into slot 9.
        let mut pkt = vec![b'A'];
        pkt.extend_from_slice(&rid.to_le_bytes());
        pkt.push(8); // from
        pkt.push(9); // to
        pkt.extend_from_slice(&2u16.to_le_bytes()); // amount
        state.handle_inventory_update(1, &pkt);

        let rec = &state.accounts.find("hero").unwrap().characters[0];
        assert_eq!(rec.actor.inventory[9].amount, 5, "stacks merged (3 + 2)");
        assert!(rec.actor.inventory[8].item.is_none(), "source stack emptied");

        // Merging into a DIFFERENT item must be rejected (slot 9 stays at 5).
        let mut bad = vec![b'A'];
        bad.extend_from_slice(&rid.to_le_bytes());
        bad.push(10); // from (different item)
        bad.push(9); // to
        bad.extend_from_slice(&1u16.to_le_bytes());
        state.handle_inventory_update(1, &bad);
        let rec = &state.accounts.find("hero").unwrap().characters[0];
        assert_eq!(rec.actor.inventory[9].amount, 5, "can't merge different items");
        assert!(rec.actor.inventory[10].item.is_some(), "the mismatched source is untouched");
    }

    #[test]
    fn item_script_enforces_race_and_class_restrictions() {
        use crate::state::ServerState;
        use rcce_server_core::item::ItemInstance;

        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let template = catalog
            .templates
            .values()
            .find(|t| t.playable)
            .cloned()
            .expect("shipped data has a playable actor template");
        let mut store = tmp_store("item_script_restrictions");
        let mut account = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut character = Character::blank();
        character.actor_id = template.id;
        character.name = "Hero".into();
        character.area = template.start_area.clone();
        account.characters.push(CharacterRecord::new(character));
        store.push(account);

        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Click_Test").is_none() {
            eprintln!("skipping: Click_Test.rsl did not parse");
            return;
        }

        let mut base = state.items.items.first().cloned().expect("Items.dat is populated");
        base.script = "Click_Test".into();
        base.smethod = "Main".into();

        let mut wrong_race = base.clone();
        wrong_race.id = 65_010;
        wrong_race.excl_race = format!("not-{}", template.race);
        wrong_race.excl_class = template.class.clone();

        let mut wrong_class = base.clone();
        wrong_class.id = 65_011;
        wrong_class.excl_race = template.race.clone();
        wrong_class.excl_class = format!("not-{}", template.class);

        let mut matching = base;
        matching.id = 65_012;
        matching.excl_race = template.race.to_ascii_uppercase();
        matching.excl_class = template.class.to_ascii_lowercase();
        state.items.items.extend([wrong_race, wrong_class, matching]);

        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let use_slot = |state: &mut ServerState, item_id| {
            let inventory = &mut state.accounts.find_mut("hero").unwrap().characters[0].actor.inventory;
            inventory[14].item = Some(ItemInstance::new(item_id));
            inventory[14].amount = 1;
            state.handle_item_script(1, &[14]);
        };

        use_slot(&mut state, 65_010);
        assert_eq!(state.running_script_count(), 0, "wrong-race item use must not start a script");

        use_slot(&mut state, 65_011);
        assert_eq!(state.running_script_count(), 0, "wrong-class item use must not start a script");

        use_slot(&mut state, 65_012);
        assert_eq!(state.running_script_count(), 1, "matching restrictions still start the item script");
    }

    #[test]
    fn swapping_two_inventory_slots_moves_the_items() {
        use crate::state::ServerState;
        use rcce_server_core::item::ItemInstance;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("swap");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        let Some(weapon_id) = state.items.items.iter().find(|item| item.item_type == 1).map(|item| item.id) else {
            eprintln!("skipping: no weapon in Items.dat");
            return;
        };
        state.catalog.templates.get_mut(&template_id).unwrap().inventory_slots |= 1;

        // A weapon in backpack slot 14, slot 0 (weapon) empty.
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.inventory[14].item = Some(ItemInstance::new(weapon_id));
            rec.actor.inventory[14].amount = 1;
        }

        // Swap backpack slot 14 ↔ slot 0 (equip it).
        let mut pkt = vec![b'S'];
        pkt.extend_from_slice(&rid.to_le_bytes()); // own runtime id
        pkt.push(14); // slotA
        pkt.push(0); // slotB
        pkt.extend_from_slice(&0u16.to_le_bytes()); // amount 0 = whole-slot
        state.handle_inventory_update(1, &pkt);

        let rec = &state.accounts.find("hero").unwrap().characters[0];
        assert!(rec.actor.inventory[0].item.is_some(), "item moved to slot 0");
        assert_eq!(rec.actor.inventory[0].item.as_ref().unwrap().item_id, weapon_id);
        assert!(rec.actor.inventory[14].item.is_none(), "slot 14 is now empty");

        // A swap targeting someone else's runtime id is rejected.
        let mut bad = vec![b'S'];
        bad.extend_from_slice(&(rid.wrapping_add(1)).to_le_bytes());
        bad.push(0);
        bad.push(14);
        bad.extend_from_slice(&0u16.to_le_bytes());
        state.handle_inventory_update(1, &bad);
        assert!(
            state.accounts.find("hero").unwrap().characters[0].actor.inventory[0].item.is_some(),
            "a swap for another actor's id must not touch this player's inventory"
        );
    }

    #[test]
    fn client_controlled_equipment_placement_obeys_blitz_slot_rules() {
        use crate::state::{ServerState, Target};
        use rcce_server_core::item::ItemInstance;

        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let template = catalog.templates.values().find(|t| t.playable).cloned().expect("shipped data has a playable actor template");
        let mut store = tmp_store("equipment_placement");
        let mut account = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut character = Character::blank();
        character.actor_id = template.id;
        character.name = "Hero".into();
        character.area = template.start_area.clone();
        account.characters.push(CharacterRecord::new(character));
        store.push(account);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        // Keep this test independent from which concrete items the starter
        // project happens to ship: placement only needs the catalog metadata.
        let mut weapon = state.items.items.first().cloned().expect("Items.dat is populated");
        weapon.id = 65_000;
        weapon.name = "Placement test weapon".into();
        weapon.item_type = 1;
        weapon.slot_type = 1;
        weapon.stackable = true;
        weapon.excl_race.clear();
        weapon.excl_class.clear();
        let mut wrong_race = weapon.clone();
        wrong_race.id = 65_001;
        wrong_race.name = "Wrong race weapon".into();
        wrong_race.excl_race = "Not this race".into();
        let mut wrong_class = weapon.clone();
        wrong_class.id = 65_002;
        wrong_class.name = "Wrong class weapon".into();
        wrong_class.excl_class = "Not this class".into();
        let mut race_override = weapon.clone();
        race_override.id = 65_003;
        race_override.name = "Race override weapon".into();
        race_override.excl_race = template.race.clone();
        race_override.excl_class = "Not this class".into();
        state.items.items.extend([weapon.clone(), wrong_race, wrong_class, race_override]);
        state.catalog.templates.get_mut(&template.id).unwrap().inventory_slots = 0x7ff;

        let swap = |state: &mut ServerState, from: u8, to: u8| {
            let mut packet = vec![b'S'];
            packet.extend_from_slice(&rid.to_le_bytes());
            packet.extend_from_slice(&[from, to, 0, 0]);
            state.handle_inventory_update(1, &packet);
        };

        // A compatible single weapon can still be equipped.
        {
            let inventory = &mut state.accounts.find_mut("hero").unwrap().characters[0].actor.inventory;
            inventory[14].item = Some(ItemInstance::new(weapon.id));
            inventory[14].amount = 1;
        }
        swap(&mut state, 14, 0);
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.inventory[0].item.as_ref().map(|item| item.item_id), Some(weapon.id), "a compatible single item equips");

        // Every client-controlled equipment route must reject an incompatible
        // item/slot, disabled slot, restriction mismatch, and stacked item.
        for (item_id, amount, enabled, destination, label) in [
            (weapon.id, 1, 0x7ff, 1, "wrong equipment slot"),
            (weapon.id, 1, 0, 0, "disabled equipment slot"),
            (65_001, 1, 0x7ff, 0, "exclusive race mismatch"),
            (65_002, 1, 0x7ff, 0, "exclusive class mismatch"),
            (weapon.id, 2, 0x7ff, 0, "stacked equipment item"),
        ] {
            state.catalog.templates.get_mut(&template.id).unwrap().inventory_slots = enabled;
            let inventory = &mut state.accounts.find_mut("hero").unwrap().characters[0].actor.inventory;
            inventory[0] = Default::default();
            inventory[1] = Default::default();
            inventory[14].item = Some(ItemInstance::new(item_id));
            inventory[14].amount = amount;
            swap(&mut state, 14, destination);
            let inventory = &state.accounts.find("hero").unwrap().characters[0].actor.inventory;
            assert_eq!(inventory[14].item.as_ref().map(|item| item.item_id), Some(item_id), "{label}");
            assert_eq!(inventory[14].amount, amount, "{label}");
            assert!(inventory[destination as usize].item.is_none(), "{label}");
        }
        state.catalog.templates.get_mut(&template.id).unwrap().inventory_slots = 0x7ff;

        // Blitz's ActorHasSlot grants a matching ExclusiveRace item an early
        // override: it may use an otherwise disabled slot and ignores its
        // class restriction. Keep this compatibility rule on the shared
        // client-controlled placement path.
        state.catalog.templates.get_mut(&template.id).unwrap().inventory_slots = 0;
        {
            let inventory = &mut state.accounts.find_mut("hero").unwrap().characters[0].actor.inventory;
            inventory[0] = Default::default();
            inventory[14].item = Some(ItemInstance::new(65_003));
            inventory[14].amount = 1;
        }
        swap(&mut state, 14, 0);
        let inventory = &state.accounts.find("hero").unwrap().characters[0].actor.inventory;
        assert_eq!(inventory[0].item.as_ref().map(|item| item.item_id), Some(65_003), "a matching ExclusiveRace item overrides class and disabled-slot checks");
        assert!(inventory[14].item.is_none());
        state.catalog.templates.get_mut(&template.id).unwrap().inventory_slots = 0x7ff;

        // A GiveItem reply cannot forge a weapon into the shield slot, while a
        // backpack reply continues to work.
        let offer = state.give_item(rid, &weapon.name, 1);
        let give_handle = u32::from_le_bytes(offer.iter().find(|outgoing| outgoing.payload.first() == Some(&b'G')).expect("GiveItem offers the test item").payload[1..5].try_into().unwrap());
        let mut forged_give = vec![b'G', b'Y'];
        forged_give.extend_from_slice(&give_handle.to_le_bytes());
        forged_give.push(1);
        state.handle_inventory_update(1, &forged_give);
        assert!(state.accounts.find("hero").unwrap().characters[0].actor.inventory[1].item.is_none(), "GiveItem cannot place a weapon in the shield slot");

        // Equipment never holds a stack, including when a GiveItem reply names
        // an existing matching item instead of an empty slot.
        {
            let inventory = &mut state.accounts.find_mut("hero").unwrap().characters[0].actor.inventory;
            inventory[0].item = Some(ItemInstance::new(weapon.id));
            inventory[0].amount = 1;
        }
        let offer = state.give_item(rid, &weapon.name, 1);
        let stacked_give_handle = u32::from_le_bytes(offer.iter().find(|outgoing| outgoing.payload.first() == Some(&b'G')).expect("GiveItem offers a stack attempt").payload[1..5].try_into().unwrap());
        let mut stacked_give = vec![b'G', b'Y'];
        stacked_give.extend_from_slice(&stacked_give_handle.to_le_bytes());
        stacked_give.push(0);
        state.handle_inventory_update(1, &stacked_give);
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.inventory[0].amount, 1, "GiveItem cannot create an equipment stack");

        let offer = state.give_item(rid, &weapon.name, 1);
        let backpack_give_handle = u32::from_le_bytes(offer.iter().find(|outgoing| outgoing.payload.first() == Some(&b'G')).expect("GiveItem offers a second test item").payload[1..5].try_into().unwrap());
        let mut backpack_give = vec![b'G', b'Y'];
        backpack_give.extend_from_slice(&backpack_give_handle.to_le_bytes());
        backpack_give.push(15);
        state.handle_inventory_update(1, &backpack_give);
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.inventory[15].item.as_ref().map(|item| item.item_id), Some(weapon.id), "GiveItem still accepts a valid backpack destination");

        // Drop a legal backpack item, then forge the pickup destination to the
        // shield slot. The item must remain on the ground and no pickup reply is sent.
        {
            let inventory = &mut state.accounts.find_mut("hero").unwrap().characters[0].actor.inventory;
            inventory[14].item = Some(ItemInstance::new(weapon.id));
            inventory[14].amount = 1;
        }
        let drop = state.handle_inventory_update(1, &[b'D', 14, 1, 0]);
        let dropped_handle = u32::from_le_bytes(drop.iter().find(|outgoing| outgoing.payload.first() == Some(&b'D')).expect("drop broadcasts the item").payload[15..19].try_into().unwrap());
        let mut forged_pickup = vec![b'P'];
        forged_pickup.extend_from_slice(&dropped_handle.to_le_bytes());
        forged_pickup.push(1);
        let pickup = state.handle_inventory_update(1, &forged_pickup);
        assert!(pickup.iter().all(|outgoing| !(outgoing.payload.first() == Some(&b'R') && matches!(outgoing.target, Target::Peer(1)))), "pickup cannot place a weapon in the shield slot");
        assert!(state.accounts.find("hero").unwrap().characters[0].actor.inventory[1].item.is_none(), "forged pickup leaves equipment unchanged");
    }

    #[test]
    fn drop_and_pickup_moves_an_item_through_the_ground() {
        use crate::state::{ServerState, Target};
        use rcce_server_core::item::ItemInstance;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("drop_pickup");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);

        // Put 3 of an item in backpack slot 14.
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.inventory[14].item = Some(ItemInstance::new(1));
            rec.actor.inventory[14].amount = 3;
        }

        // Drop 2 from slot 14.
        let drop = vec![b'D', 14u8, 2u8, 0u8];
        let drop_out = state.handle_inventory_update(1, &drop);
        // The drop is broadcast as "D" to the area; extract the ground handle.
        let dbroadcast = drop_out
            .iter()
            .find(|o| o.msg_type == P_INVENTORY_UPDATE && o.payload.first() == Some(&b'D'))
            .expect("drop broadcasts a D packet");
        let handle = u32::from_le_bytes([
            dbroadcast.payload[15],
            dbroadcast.payload[16],
            dbroadcast.payload[17],
            dbroadcast.payload[18],
        ]);
        // Slot 14 now holds 1.
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.inventory[14].amount, 1);

        // Pick it up into empty backpack slot 15.
        let mut pick = vec![b'P'];
        pick.extend_from_slice(&handle.to_le_bytes());
        pick.push(15u8);
        let pick_out = state.handle_inventory_update(1, &pick);
        // "R" reply to the picker (handle + slot).
        assert!(
            pick_out.iter().any(|o| o.msg_type == P_INVENTORY_UPDATE
                && o.payload.first() == Some(&b'R')
                && matches!(o.target, Target::Peer(1))),
            "pickup replies with R to the picker"
        );
        // Slot 15 now holds the picked-up stack (amount 2).
        let rec = &state.accounts.find("hero").unwrap().characters[0];
        assert!(rec.actor.inventory[15].item.is_some(), "item landed in slot 15");
        assert_eq!(rec.actor.inventory[15].amount, 2, "picked up the dropped amount");

        // The ground item is gone — a second pickup does nothing.
        let again = state.handle_inventory_update(1, &pick);
        assert!(again.is_empty(), "the ground item was consumed by the first pickup");
    }

    #[test]
    fn equipped_weapon_and_armour_feed_combat() {
        use crate::state::ServerState;
        use rcce_server_core::item::ItemInstance;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let state = ServerState::new(config_for(dir.clone()), tmp_store("equip"), catalog);
        if state.items.items.is_empty() {
            eprintln!("skipping: no Items.dat");
            return;
        }
        let weapon = state
            .items
            .items
            .iter()
            .find(|d| d.item_type == 1 && d.weapon_damage > 0)
            .map(|d| (d.id, d.weapon_damage, d.weapon_damage_type));
        let armour = state
            .items
            .items
            .iter()
            .find(|d| d.item_type == 2 && d.armour_level > 0)
            .map(|d| (d.id, d.armour_level));

        // Unarmed / unarmoured baseline.
        let blank = Character::blank();
        assert!(ServerState::equipped_weapon(&state.items, &blank).is_none());
        assert_eq!(ServerState::equipped_armour(&state.items, &blank), 0);

        if let Some((wid, wdmg, wtype)) = weapon {
            let mut c = Character::blank();
            c.inventory[0].item = Some(ItemInstance::new(wid));
            let (got_dmg, got_type) =
                ServerState::equipped_weapon(&state.items, &c).expect("equipped weapon resolves");
            assert_eq!(got_dmg, wdmg as i32, "weapon feeds its damage");
            assert_eq!(got_type, wtype as u8, "weapon feeds its damage type");

            // A broken weapon (item_health 0) is treated as unarmed.
            let mut broken = Character::blank();
            let mut inst = ItemInstance::new(wid);
            inst.item_health = 0;
            broken.inventory[0].item = Some(inst);
            assert!(
                ServerState::equipped_weapon(&state.items, &broken).is_none(),
                "a broken weapon does not contribute damage"
            );
        } else {
            eprintln!("note: no weapon with positive damage in Items.dat");
        }

        if let Some((aid, alvl)) = armour {
            let mut c = Character::blank();
            c.inventory[1].item = Some(ItemInstance::new(aid)); // shield slot
            assert_eq!(
                ServerState::equipped_armour(&state.items, &c),
                alvl as i32,
                "armour feeds GetArmourLevel"
            );
        } else {
            eprintln!("note: no armour with positive level in Items.dat");
        }
    }

    // Combat durability wear (GameServer.bb:536-570): a player's equipped weapon
    // wears on the swings they make (P_ItemHealth to the attacker), and their
    // equipped armour wears on the swings they take (P_ItemHealth to the defender).
    // Broken (0-health) gear stops wearing and stops contributing to combat.
    #[test]
    fn combat_wear_decrements_gear_and_notifies_the_owner() {
        use crate::spawn::NpcActor;
        use crate::state::{ServerState, Target};
        use rcce_server_core::item::ItemInstance;

        // --- The wear primitive guards the u8 against underflow (no combat). ---
        {
            let mut c = Character::blank();
            let mut inst = ItemInstance::new(1);
            inst.item_health = 1;
            c.inventory[0].item = Some(inst);
            assert_eq!(ServerState::wear_item(&mut c, 0), Some(0), "1 → 0");
            assert_eq!(ServerState::wear_item(&mut c, 0), None, "0 stays 0 (no wrap to 255)");
            assert_eq!(c.inventory[0].item.as_ref().unwrap().item_health, 0);
            assert_eq!(ServerState::wear_item(&mut c, 5), None, "empty slot → None");
        }

        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            eprintln!("skipping: no playable template with a loadable start area");
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();

        let mut store = tmp_store("wear");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        c.attributes.value[0] = 30000; // survive many NPC swings (health slot 0)
        c.attributes.maximum[0] = 30000;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.items.items.is_empty() {
            eprintln!("skipping: no Items.dat");
            return;
        }
        let Some(weapon_id) = state.items.items.iter().find(|d| d.item_type == 1).map(|d| d.id) else {
            eprintln!("skipping: no weapon in Items.dat");
            return;
        };
        let Some(armour_id) = state.items.items.iter().find(|d| d.item_type == 2).map(|d| d.id) else {
            eprintln!("skipping: no armour in Items.dat");
            return;
        };

        // Force both toggles on + a fixed RNG seed so the 1-in-5 rolls are
        // deterministic regardless of the shipped Misc.dat (weapon OFF, armour ON).
        state.weapon_damage_on = true;
        state.armour_damage_on = true;
        state.rng = rcce_server_core::rng::Rng::new(0x00C0_FFEE);

        // Equip a full-durability weapon (slot 0) + shield (slot 1).
        {
            let rec = state.accounts.find_mut("hero").unwrap().characters.get_mut(0).unwrap();
            let mut w = ItemInstance::new(weapon_id);
            w.item_health = 100;
            let mut a = ItemInstance::new(armour_id);
            a.item_health = 100;
            rec.actor.inventory[0].item = Some(w);
            rec.actor.inventory[1].item = Some(a);
        }

        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        state.world.warp_session(1, area.clone(), 0.0, 0.0, 0.0);

        // --- Weapon wear: the hero attacks a durable NPC many times. ---
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid, actor_id: template_id, area: area.clone(),
            x: 0.0, y: 0.0, z: 0.0, hp: 1_000_000, hp_max: 1_000_000,
            target_peer: None, last_attack_ms: 0,
            script: String::new(), death_script: String::new(), stock: Vec::new(),
        });
        let attack = npc_rid.to_le_bytes().to_vec();
        let mut weapon_pkt_health: Option<u16> = None;
        let mut now = 0u64;
        for _ in 0..300 {
            now += state.combat_delay as u64 + 1;
            for o in state.handle_attack(1, &attack, now) {
                if o.msg_type == P_ITEM_HEALTH && matches!(o.target, Target::Sender) {
                    assert_eq!(o.payload.len(), 3, "P_ItemHealth = slot(u8) + health(u16)");
                    assert_eq!(o.payload[0], 0, "weapon wear reports SlotI_Weapon = 0");
                    weapon_pkt_health = Some(u16::from_le_bytes([o.payload[1], o.payload[2]]));
                }
            }
        }
        let weapon_health = state.accounts.find("hero").unwrap().characters[0]
            .actor.inventory[0].item.as_ref().unwrap().item_health;
        assert!(weapon_health < 100, "the attacker's weapon wore down (now {weapon_health})");
        assert_eq!(weapon_pkt_health, Some(weapon_health as u16), "last P_ItemHealth matched the worn weapon");
        assert!(state.spawns.npc(npc_rid).is_some(), "the durable NPC survived — wear came from swings, not a kill");

        // --- Armour wear: an NPC swings at the co-located hero many times. ---
        let mob_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: mob_rid, actor_id: template_id, area: area.clone(),
            x: 0.0, y: 0.0, z: 0.0, hp: 1_000_000, hp_max: 1_000_000,
            target_peer: Some(1), last_attack_ms: 0,
            script: String::new(), death_script: String::new(), stock: Vec::new(),
        });
        let mut armour_pkt: Option<(u8, u16)> = None;
        let mut t = now;
        for _ in 0..300 {
            t += state.combat_delay as u64 + 1;
            // Keep the hero alive so the NPC keeps swinging (no death → no target clear).
            if let Some(rec) = state.accounts.find_mut("hero").unwrap().characters.get_mut(0) {
                rec.actor.attributes.value[state.health_stat] = 30000;
            }
            for o in state.collect_npc_attacks(t) {
                if o.msg_type == P_ITEM_HEALTH && matches!(o.target, Target::Peer(1)) {
                    assert_eq!(o.payload.len(), 3);
                    armour_pkt = Some((o.payload[0], u16::from_le_bytes([o.payload[1], o.payload[2]])));
                }
            }
        }
        let armour_health = state.accounts.find("hero").unwrap().characters[0]
            .actor.inventory[1].item.as_ref().unwrap().item_health;
        assert!(armour_health < 100, "the defender's armour wore down (now {armour_health})");
        let (worn_slot, worn_health) = armour_pkt.expect("armour wear notified the owner via P_ItemHealth");
        assert_eq!(worn_slot, 1, "shield is SlotI_Shield = 1 (only equipped armour slot)");
        assert_eq!(worn_health, armour_health as u16, "last P_ItemHealth matched the worn shield");
    }

    #[test]
    fn a_timed_buff_potion_applies_then_reverts_its_attribute_deltas() {
        use crate::state::ServerState;
        use rcce_server_core::item::ItemInstance;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("buff");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        // A potion/ingredient with a timed duration.
        let Some(potion_id) = state
            .items
            .items
            .iter()
            .find(|d| (d.item_type == 4 || d.item_type == 5) && d.eat_effects_length > 0)
            .map(|d| d.id)
        else {
            eprintln!("skipping: no timed-effect potion in Items.dat");
            return;
        };
        let sidx = state.strength_stat;
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);

        // A potion instance carrying a +10 Strength buff, in slot 8.
        let base_str;
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.attributes.value[sidx] = 20;
            base_str = rec.actor.attributes.value[sidx];
            let mut inst = ItemInstance::new(potion_id);
            inst.attr_values[sidx] = 10;
            rec.actor.inventory[8].item = Some(inst);
            rec.actor.inventory[8].amount = 1;
        }

        // Drink it: the buff applies immediately (+10 Strength) + a P_ActorEffect.
        let mut pkt = vec![8u8];
        pkt.extend_from_slice(&1u16.to_le_bytes());
        let out = state.handle_eat_item(1, &pkt);
        assert!(
            out.iter().any(|o| o.msg_type == crate::world::P_ACTOR_EFFECT && o.payload.first() == Some(&b'A')),
            "drinking a buff potion creates an ActorEffect"
        );
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[sidx],
            base_str + 10,
            "the buff raised Strength"
        );

        // On disconnect (mid-buff), the delta is reverted (not baked into the save).
        state.on_disconnect(1);
        assert_eq!(
            state.accounts.find("hero").unwrap().characters[0].actor.attributes.value[sidx],
            base_str,
            "the buff is reverted so it isn't persisted permanently"
        );
    }

    #[test]
    fn eating_a_health_potion_heals_the_player_and_consumes_one() {
        use crate::state::ServerState;
        use rcce_server_core::item::ItemInstance;
        use rcce_server_core::character::InventorySlot;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Item_HealthPotion.rsl").exists() {
            eprintln!("skipping: no Item_HealthPotion.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("eat_item");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        // Find the health-potion item (type 4, use-script Item_HealthPotion).
        let Some(potion_id) = state
            .items
            .items
            .iter()
            .find(|d| d.item_type == 4 && d.script.eq_ignore_ascii_case("Item_HealthPotion"))
            .map(|d| d.id)
        else {
            eprintln!("skipping: no Item_HealthPotion potion in Items.dat");
            return;
        };
        if state.scripts.get("Item_HealthPotion").is_none() {
            eprintln!("skipping: Item_HealthPotion.rsl did not parse");
            return;
        }
        let Some(hidx) = state.attr_names.index_of("Health") else {
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);

        // Wound the player (max 100, current 10) and give them 2 potions in slot 0.
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.attributes.maximum[hidx] = 100;
            rec.actor.attributes.value[hidx] = 10;
            if rec.actor.inventory.is_empty() {
                rec.actor.inventory.push(InventorySlot::default());
            }
            rec.actor.inventory[0] = InventorySlot { item: Some(ItemInstance::new(potion_id)), amount: 2 };
        }

        // P_EatItem: slot 0, amount 1.
        let mut pkt = vec![0u8]; // slot
        pkt.extend_from_slice(&1u16.to_le_bytes()); // amount
        state.handle_eat_item(1, &pkt);

        // Pump the potion script to completion (heals via SetAttribute).
        let deadline = std::time::Instant::now() + Duration::from_secs(3);
        while std::time::Instant::now() < deadline {
            state.pump_scripts();
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }
        assert_eq!(state.running_script_count(), 0, "potion script runs to completion");

        let rec = &state.accounts.find("hero").unwrap().characters[0];
        // Healed by max/2 = 50 → 10 + 50 = 60.
        assert_eq!(rec.actor.attributes.value[hidx], 60, "health potion heals max/2");
        // One potion consumed (2 → 1).
        assert_eq!(rec.actor.inventory[0].amount, 1, "one potion consumed");
        assert!(rec.actor.inventory[0].item.is_some(), "stack not emptied");
    }

    #[test]
    fn a_race_restricted_spell_is_rejected_for_the_wrong_race() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        // Load a throwaway state just to read the spell catalog.
        let probe = ServerState::new(config_for(dir.clone()), tmp_store("excl_probe"), catalog);
        // A spell restricted to some race, and a playable template of a DIFFERENT race.
        let restricted = probe
            .spells_catalog
            .spells
            .iter()
            .find(|s| !s.exclusive_race.is_empty() && !s.script.is_empty())
            .map(|s| (s.id, s.exclusive_race.clone()));
        let Some((spell_id, req_race)) = restricted else {
            eprintln!("skipping: no race-restricted spell in Spells.dat");
            return;
        };
        let catalog2 = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog2
            .templates
            .values()
            .find(|t| t.playable && !t.race.eq_ignore_ascii_case(&req_race))
        else {
            eprintln!("skipping: no playable race other than '{req_race}'");
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("excl");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog2);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        state.accounts.find_mut("hero").unwrap().characters[0].actor.known_spells[0] = spell_id as i16;
        state.accounts.find_mut("hero").unwrap().characters[0].actor.memorised_spells[0] = spell_id as i16;

        let mut cast = vec![b'F'];
        cast.extend_from_slice(&spell_id.to_le_bytes());
        let out = state.handle_spell_update(1, &cast);
        assert!(
            out.iter().any(|o| o.msg_type == P_CHAT_MESSAGE),
            "a race-restricted spell cast by the wrong race is rejected with a message"
        );
        assert_eq!(state.running_script_count(), 0, "the restricted spell must not fire");
    }

    #[test]
    fn a_spell_on_cooldown_is_rejected_until_recharged() {
        use crate::state::ServerState;
        use std::time::Duration;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("cooldown");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        // A spell with a recharge long enough to outlast the 100 ms cast floor.
        let Some(spell_id) = state
            .spells_catalog
            .spells
            .iter()
            .find(|s| s.recharge_time > 200 && !s.script.is_empty())
            .map(|s| s.id)
        else {
            eprintln!("skipping: no spell with recharge_time > 200 in Spells.dat");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        state.accounts.find_mut("hero").unwrap().characters[0].actor.known_spells[0] = spell_id as i16;
        state.accounts.find_mut("hero").unwrap().characters[0].actor.memorised_spells[0] = spell_id as i16;

        let mut cast = vec![b'F'];
        cast.extend_from_slice(&spell_id.to_le_bytes());

        // First cast is allowed (no "not recharged" message).
        let first = state.handle_spell_update(1, &cast);
        assert!(
            !first.iter().any(|o| o.msg_type == P_CHAT_MESSAGE),
            "first cast should be allowed"
        );

        // Wait past the 100 ms global floor but within the spell's recharge.
        std::thread::sleep(Duration::from_millis(120));
        let second = state.handle_spell_update(1, &cast);
        assert!(
            second.iter().any(|o| {
                o.msg_type == P_CHAT_MESSAGE && String::from_utf8_lossy(&o.payload).contains("recharged")
            }),
            "a recast within the recharge window should be rejected as not recharged"
        );
    }

    #[test]
    fn casting_fireball_at_an_npc_damages_and_kills_it() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Spell_Fireball.rsl").exists() {
            eprintln!("skipping: no Spell_Fireball.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("cast_fireball");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let Some(fireball_id) = state
            .spells_catalog
            .spells
            .iter()
            .find(|s| s.script.eq_ignore_ascii_case("Spell_Fireball"))
            .map(|s| s.id)
        else {
            eprintln!("skipping: no Spell_Fireball in Spells.dat");
            return;
        };
        if state.scripts.get("Spell_Fireball").is_none() {
            eprintln!("skipping: Spell_Fireball.rsl did not parse");
            return;
        }
        let Some(midx) = state.attr_names.index_of("Mana") else {
            eprintln!("skipping: no Mana attribute");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();

        // Give the caster Mana + teach them Fireball.
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.attributes.maximum[midx] = 100;
            rec.actor.attributes.value[midx] = 100;
            rec.actor.known_spells[0] = fireball_id as i16;
            rec.actor.memorised_spells[0] = fireball_id as i16;
        }
        // A low-HP NPC co-located with the caster (Fireball does 30-60 → lethal).
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: template_id,
            area,
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 10,
            hp_max: 10,
            target_peer: None,
            last_attack_ms: 0,
            script: String::new(),
            death_script: String::new(),
            stock: Vec::new(),
        });

        // P_SpellUpdate "F" + fireball id + target rid.
        let mut pkt = vec![b'F'];
        pkt.extend_from_slice(&fireball_id.to_le_bytes());
        pkt.extend_from_slice(&npc_rid.to_le_bytes());
        state.handle_spell_update(1, &pkt);

        // Pump the spell (DoEvents + SetAttribute(Target,Health,...) → kill).
        let mut killed = false;
        let deadline = std::time::Instant::now() + Duration::from_secs(4);
        while std::time::Instant::now() < deadline {
            state.pump_scripts();
            if state.spawns.npc(npc_rid).is_none() {
                killed = true;
                break;
            }
            if state.running_script_count() == 0 && state.spawns.npc(npc_rid).is_some() {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }
        assert!(killed, "Fireball should damage the NPC to death and remove it");
    }

    #[test]
    fn casting_a_projectile_spell_broadcasts_the_visual() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Spell_Fireball.rsl").exists() {
            eprintln!("skipping: no Spell_Fireball.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("projectile");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        // Spell_Fireball fires the "Fireball" projectile — skip if absent from data.
        if state.projectiles.get_by_name("Fireball").is_none() {
            eprintln!("skipping: no 'Fireball' projectile in Projectiles.dat");
            return;
        }
        let Some(fireball_id) = state
            .spells_catalog
            .spells
            .iter()
            .find(|s| s.script.eq_ignore_ascii_case("Spell_Fireball"))
            .map(|s| s.id)
        else {
            return;
        };
        if state.scripts.get("Spell_Fireball").is_none() {
            return;
        }
        let Some(midx) = state.attr_names.index_of("Mana") else { return };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.attributes.maximum[midx] = 100;
            rec.actor.attributes.value[midx] = 100;
            rec.actor.known_spells[0] = fireball_id as i16;
            rec.actor.memorised_spells[0] = fireball_id as i16;
        }
        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid,
            actor_id: template_id,
            area,
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 100,
            hp_max: 100,
            target_peer: None,
            last_attack_ms: 0,
            script: String::new(),
            death_script: String::new(),
            stock: Vec::new(),
        });

        let mut pkt = vec![b'F'];
        pkt.extend_from_slice(&fireball_id.to_le_bytes());
        pkt.extend_from_slice(&npc_rid.to_le_bytes());
        state.handle_spell_update(1, &pkt);

        let mut saw_projectile = false;
        let deadline = std::time::Instant::now() + Duration::from_secs(4);
        while std::time::Instant::now() < deadline {
            for o in state.pump_scripts() {
                if o.msg_type == crate::world::P_PROJECTILE {
                    saw_projectile = true;
                }
            }
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }
        assert!(saw_projectile, "casting Fireball broadcasts a P_Projectile visual to the area");
    }

    #[test]
    fn memorise_gates_casting_when_required() {
        use crate::state::ServerState;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Spell_Heal.rsl").exists() {
            eprintln!("skipping: no Spell_Heal.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("memorise");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let Some(heal_id) = state
            .spells_catalog
            .spells
            .iter()
            .find(|s| s.script.eq_ignore_ascii_case("Spell_Heal"))
            .map(|s| s.id)
        else {
            eprintln!("skipping: no Spell_Heal in Spells.dat");
            return;
        };
        if state.scripts.get("Spell_Heal").is_none() {
            return;
        }
        // RequireMemorise is ON in the shipped Misc.dat (byte[21]=1); set it here
        // explicitly so the test is independent of the data file.
        state.require_memorise = true;
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        state.accounts.find_mut("hero").unwrap().characters[0].actor.known_spells[0] = heal_id as i16;

        let mut cast = vec![b'F'];
        cast.extend_from_slice(&heal_id.to_le_bytes());

        // Known but not memorised → can't cast.
        state.handle_spell_update(1, &cast);
        assert_eq!(state.running_script_count(), 0, "an unmemorised spell can't be cast when RequireMemorise");

        // Memorise it → queued, but the 6 s MemorisingSpell timer hasn't elapsed,
        // so an immediate cast still fails (Blitz Server.bb:720 commits at +6000ms).
        let mut memo = vec![b'M'];
        memo.extend_from_slice(&heal_id.to_le_bytes());
        state.handle_spell_update(1, &memo);
        state.handle_spell_update(1, &cast);
        assert_eq!(state.running_script_count(), 0, "the memorise hasn't committed yet (6s delay)");

        // Fast-forward past the 6 s window and pump → the spell commits → it casts.
        state.force_memorise_ready();
        state.pump_scripts();
        state.handle_spell_update(1, &cast);
        assert!(state.running_script_count() >= 1, "a committed memorised spell casts");

        // Drain the running spell.
        let deadline = std::time::Instant::now() + Duration::from_secs(3);
        while std::time::Instant::now() < deadline {
            state.pump_scripts();
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }

        // Unmemorise → blocked again (memorise gate precedes the cooldown check).
        let mut unmemo = vec![b'U'];
        unmemo.extend_from_slice(&heal_id.to_le_bytes());
        state.handle_spell_update(1, &unmemo);
        state.handle_spell_update(1, &cast);
        assert_eq!(state.running_script_count(), 0, "unmemorising blocks casting again");
    }

    #[test]
    fn casting_heal_broadcasts_a_sound_to_the_area() {
        use crate::state::ServerState;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Spell_Heal.rsl").exists() {
            eprintln!("skipping: no Spell_Heal.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("sound");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        let Some(heal_id) = state
            .spells_catalog
            .spells
            .iter()
            .find(|s| s.script.eq_ignore_ascii_case("Spell_Heal"))
            .map(|s| s.id)
        else {
            return;
        };
        if state.scripts.get("Spell_Heal").is_none() {
            return;
        }
        let Some(midx) = state.attr_names.index_of("Mana") else { return };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.attributes.maximum[midx] = 100;
            rec.actor.attributes.value[midx] = 100;
            rec.actor.known_spells[0] = heal_id as i16;
            rec.actor.memorised_spells[0] = heal_id as i16;
        }

        let mut cast = vec![b'F'];
        cast.extend_from_slice(&heal_id.to_le_bytes());
        state.handle_spell_update(1, &cast);

        let mut saw_sound = false;
        let deadline = std::time::Instant::now() + Duration::from_secs(3);
        while std::time::Instant::now() < deadline {
            for o in state.pump_scripts() {
                if o.msg_type == crate::world::P_SOUND {
                    saw_sound = true;
                }
            }
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }
        assert!(saw_sound, "Spell_Heal's PlaySound broadcasts a P_Sound to the area");
    }

    #[test]
    fn casting_heal_restores_health_and_consumes_mana() {
        use crate::state::ServerState;
        use std::time::Duration;
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/Spell_Heal.rsl").exists() {
            eprintln!("skipping: no Spell_Heal.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("cast_heal");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        // Find the Heal spell (use-script Spell_Heal).
        let Some(heal_id) = state
            .spells_catalog
            .spells
            .iter()
            .find(|s| s.script.eq_ignore_ascii_case("Spell_Heal"))
            .map(|s| s.id)
        else {
            eprintln!("skipping: no Spell_Heal spell in Spells.dat");
            return;
        };
        if state.scripts.get("Spell_Heal").is_none() {
            eprintln!("skipping: Spell_Heal.rsl did not parse");
            return;
        }
        let (Some(hidx), Some(midx)) =
            (state.attr_names.index_of("Health"), state.attr_names.index_of("Mana"))
        else {
            eprintln!("skipping: Attributes.dat lacks Health/Mana");
            return;
        };
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);

        // Wound the caster (50/100 HP), give Mana 100/100, and teach them Heal.
        {
            let rec = &mut state.accounts.find_mut("hero").unwrap().characters[0];
            rec.actor.attributes.maximum[hidx] = 100;
            rec.actor.attributes.value[hidx] = 50;
            rec.actor.attributes.maximum[midx] = 100;
            rec.actor.attributes.value[midx] = 100;
            rec.actor.known_spells[0] = heal_id as i16;
            rec.actor.memorised_spells[0] = heal_id as i16;
        }

        // P_SpellUpdate "F" + spell id (no target → self-heal).
        let mut pkt = vec![b'F'];
        pkt.extend_from_slice(&heal_id.to_le_bytes());
        state.handle_spell_update(1, &pkt);

        // Pump the spell script to completion.
        let deadline = std::time::Instant::now() + Duration::from_secs(3);
        while std::time::Instant::now() < deadline {
            state.pump_scripts();
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(1));
        }
        assert_eq!(state.running_script_count(), 0, "spell script runs to completion");

        let rec = &state.accounts.find("hero").unwrap().characters[0];
        let hp = rec.actor.attributes.value[hidx];
        let mana = rec.actor.attributes.value[midx];
        assert!(hp > 50 && hp <= 100, "Heal restored health (50 → {hp})");
        assert_eq!(mana, 95, "Heal consumed 5 mana (100 → {mana})");
    }

    #[test]
    fn walking_into_a_portal_warps_to_the_linked_area() {
        use crate::state::{ServerState, Target};
        let dir = data_dir();
        // Find any area with a portal linking to a loadable destination area.
        let mut found: Option<(String, rcce_server_core::area::Portal, String)> = None;
        if let Ok(entries) = std::fs::read_dir(dir.join("Server Data/Areas")) {
            for e in entries.flatten() {
                let p = e.path();
                if p.extension().and_then(|s| s.to_str()) != Some("dat") {
                    continue;
                }
                let Some(name) = p.file_stem().and_then(|s| s.to_str()) else { continue };
                let Some(area) = Area::load(&dir, name) else { continue };
                if let Some(portal) = area.portals.iter().find(|pt| {
                    !pt.link_area.is_empty() && Area::load(&dir, &pt.link_area).is_some()
                }) {
                    let link = portal.link_area.clone();
                    found = Some((area.name.clone(), portal.clone(), link));
                    break;
                }
            }
        }
        let Some((src_area, portal, link_area)) = found else {
            eprintln!("skipping: no linked portal found in data/");
            return;
        };
        let canonical_link = Area::load(&dir, &link_area).map(|a| a.name).unwrap_or(link_area);

        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("portal_walk");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        // Stand the player on the linked portal in the source area.
        state.world.warp_session(1, src_area.clone(), portal.x, portal.y, portal.z);

        let outs = state.check_portals();
        // Warped: P_ChangeArea sent + session now in the linked area.
        assert!(
            outs.iter().any(|o| o.msg_type == P_CHANGE_AREA && matches!(o.target, Target::Peer(1))),
            "stepping into the portal should send P_ChangeArea"
        );
        let s = state.world.session(1).unwrap();
        assert!(
            s.area.eq_ignore_ascii_case(&canonical_link),
            "player should be in the linked area '{canonical_link}'; got '{}'",
            s.area
        );
        assert_eq!(rid, state.world.session(1).unwrap().runtime_id, "runtime id is stable across the warp");

        // Idempotent: standing still (now on the destination portal) doesn't bounce back.
        let again = state.check_portals();
        assert!(
            again.is_empty() || !state.world.session(1).unwrap().area.eq_ignore_ascii_case(&src_area),
            "the destination portal must not immediately warp the player back"
        );
    }

    #[test]
    fn warp_changes_player_area_and_sends_change_area() {
        use crate::state::{ServerState, Target};
        let dir = data_dir();
        let Some(plains) = Area::load(&dir, "Plains") else {
            eprintln!("skipping: no Plains.dat");
            return;
        };
        let Some(portal) = plains.portals.iter().find(|p| !p.name.is_empty()) else {
            eprintln!("skipping: Plains has no named portal");
            return;
        };
        let portal_name = portal.name.clone();
        let (pgx, pgz) = (portal.x, portal.z);
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        // Pick a playable template whose start area is NOT Plains, so this is a
        // genuine cross-area warp; fall back to any playable template.
        let template = catalog
            .templates
            .values()
            .find(|t| t.playable && !t.start_area.eq_ignore_ascii_case("Plains") && Area::load(&dir, &t.start_area).is_some())
            .or_else(|| catalog.templates.values().find(|t| t.playable && Area::load(&dir, &t.start_area).is_some()));
        let Some(template) = template else { return };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("warp");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        let outs = state.warp_actor(rid, "Plains", &portal_name);
        // The warping player is told it changed zone.
        assert!(
            outs.iter().any(|o| o.msg_type == P_CHANGE_AREA
                && matches!(o.target, Target::Peer(1))),
            "P_ChangeArea sent to the warping player"
        );
        // The session moved to Plains, at the portal position.
        let s = state.world.session(1).unwrap();
        assert!(s.area.eq_ignore_ascii_case("Plains"), "session area is Plains; got '{}'", s.area);
        assert_eq!(s.x, pgx, "warped to the portal X");
        assert_eq!(s.z, pgz, "warped to the portal Z");
    }

    /// `IgnoreUpdate` warp-update suppression (`GameServer.bb:4`,
    /// `ServerNet.bb:727-737,1821`): a warp arms the flag, inbound
    /// `P_StandardUpdate`s are dropped (echoing the post-warp position) while
    /// armed, the client's `P_ChangeArea`/`P_RepositionActor` ack clears it,
    /// and — the port's non-acking-client safeguard — the window also expires
    /// on its own so a client that never acks is not frozen forever.
    #[test]
    fn warp_arms_ignore_update_and_ack_or_expiry_clears_it() {
        use crate::state::ServerState;
        let dir = data_dir();
        let Some(plains) = Area::load(&dir, "Plains") else {
            eprintln!("skipping: no Plains.dat");
            return;
        };
        let Some(portal) = plains.portals.iter().find(|p| !p.name.is_empty()) else {
            eprintln!("skipping: Plains has no named portal");
            return;
        };
        let portal_name = portal.name.clone();
        let (pgx, pgz) = (portal.x, portal.z);
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("ignoreupd");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;
        assert_eq!(state.world.session(1).unwrap().ignore_update_until_ms, 0, "login does not arm the flag");

        // Warp → the suppression window is armed.
        state.warp_actor(rid, "Plains", &portal_name);
        let deadline = state.world.session(1).unwrap().ignore_update_until_ms;
        assert!(deadline > 0, "warp arms IgnoreUpdate");

        // Re-arm with a far deadline so the dispatch-path assertions below are
        // immune to wall-clock stalls (dispatch uses real elapsed ms); the
        // warp's own arming is already asserted above and the deadline
        // comparison itself is covered deterministically at the end.
        state.world.set_ignore_update_until(1, u64::MAX);

        // A stale in-flight update (pre-warp coordinates) is dropped: the
        // session stays at the warp destination, and the echo carries the
        // authoritative post-warp position so the client reconciles onto it.
        let mut stale = Vec::new();
        for f in [1.0f32, 2.0, 3.0, 4.0, 5.0] {
            stale.extend_from_slice(&f.to_le_bytes());
        }
        stale.extend_from_slice(&[0, 0]);
        let outs = state.dispatch(1, P_STANDARD_UPDATE, &stale);
        let s = state.world.session(1).unwrap();
        assert_eq!((s.x, s.z), (pgx, pgz), "stale update must not yank the player back");
        assert_eq!(outs.len(), 1);
        let echo = &outs[0].payload;
        assert_eq!(f32::from_le_bytes([echo[2], echo[3], echo[4], echo[5]]), pgx, "echo corrects to the warp X");

        // The client acks the zone change → the flag clears (ServerNet.bb:737)…
        state.dispatch(1, P_CHANGE_AREA, &[]);
        assert_eq!(state.world.session(1).unwrap().ignore_update_until_ms, 0, "ack clears IgnoreUpdate");
        // …and the same update now moves the session.
        state.dispatch(1, P_STANDARD_UPDATE, &stale);
        let s = state.world.session(1).unwrap();
        assert_eq!((s.x, s.z), (4.0, 5.0), "post-ack updates apply again");

        // The P_RepositionActor ack clears it too (ServerNet.bb:730).
        state.world.set_ignore_update_until(1, u64::MAX);
        state.dispatch(1, P_REPOSITION_ACTOR, &[]);
        assert_eq!(state.world.session(1).unwrap().ignore_update_until_ms, 0);

        // No-ack fallback: once now >= deadline the window has expired and
        // movement applies (neither shipped client sends the ack — the Blitz
        // client's send is commented out at ClientNet.bb:1779).
        state.world.set_ignore_update_until(1, 500);
        let mut mv = Vec::new();
        for f in [7.0f32, 8.0, 9.0, 10.0, 11.0] {
            mv.extend_from_slice(&f.to_le_bytes());
        }
        mv.extend_from_slice(&[0, 0]);
        handle_standard_update(&mv, &mut state.world, 1, 499);
        let s = state.world.session(1).unwrap();
        assert_eq!((s.x, s.z), (4.0, 5.0), "still suppressed just before the deadline");
        handle_standard_update(&mv, &mut state.world, 1, 500);
        let s = state.world.session(1).unwrap();
        assert_eq!((s.x, s.z), (10.0, 11.0), "expired window lets updates through");
    }

    #[test]
    fn disconnect_persists_position_and_area() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("persistpos");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);

        // The player walks/warps elsewhere (session-only change).
        state.world.warp_session(1, "Northshore".into(), 123.0, 5.0, 456.0);
        // Pre-disconnect: the STORED character still has its creation area/pos.
        assert_ne!(state.accounts.find("hero").unwrap().characters[0].actor.area, "Northshore");

        state.on_disconnect(1);

        // Post-disconnect: the live position/area was written back to the char.
        let ch = &state.accounts.find("hero").unwrap().characters[0].actor;
        assert_eq!(ch.area, "Northshore", "area persisted on logout");
        assert_eq!(ch.x, 123.0, "x persisted");
        assert_eq!(ch.z, 456.0, "z persisted");
    }

    #[test]
    fn setactorglobal_is_gated_to_self_or_privileged() {
        use crate::state::ServerState;
        use rcce_script::{Host, Value};
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable && Area::load(&dir, &t.start_area).is_some()) else {
            return;
        };
        let template_id = template.id;
        let start_area = template.start_area.clone();
        let mut store = tmp_store("globalgate");
        for name in ["hero", "victim"] {
            let mut acct = Account::new(name, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = name.into();
            c.area = start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("victim", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);
        let hero_rid = state.world.session(1).unwrap().runtime_id as i64;
        let victim_rid = state.world.session(2).unwrap().runtime_id as i64;

        // An UNPRIVILEGED script whose actor is the hero tries to write the
        // VICTIM's global → must be blocked.
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
                catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
                actor: hero_rid, ctx: 0, privileged: false, dirty: false, out: Vec::new(),
            };
            host.call("setactorglobal", &[Value::Int(victim_rid), Value::Int(0), Value::Str("hacked".into())]);
            // And its OWN global → allowed.
            host.call("setactorglobal", &[Value::Int(hero_rid), Value::Int(0), Value::Str("mine".into())]);
        }
        assert_eq!(state.accounts.find("victim").unwrap().characters[0].actor.script_globals[0], "", "non-priv script can't write another actor's global");
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.script_globals[0], "mine", "a script may write its own actor's global");

        // A PRIVILEGED script may write the victim's global.
        {
            let mut host = crate::scripts::ScriptHost {
                world: &state.world, accounts: &mut state.accounts, spawns: &state.spawns,
                catalog: &state.catalog, attr_names: &state.attr_names, rng: &mut state.rng,
                actor: hero_rid, ctx: 0, privileged: true, dirty: false, out: Vec::new(),
            };
            host.call("setactorglobal", &[Value::Int(victim_rid), Value::Int(0), Value::Str("granted".into())]);
        }
        assert_eq!(state.accounts.find("victim").unwrap().characters[0].actor.script_globals[0], "granted", "a privileged script may write any actor's global");
    }

    #[test]
    fn examine_npc_runs_the_default_script_and_outputs_its_name() {
        use crate::state::ServerState;
        let dir = data_dir();
        // Requires the shipped Default.rsl (with an Examine function).
        if !dir.join("Server Data/Scripts/Default.rsl").exists() {
            eprintln!("skipping: no Default.rsl");
            return;
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let mut store = tmp_store("examine");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template.id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Default").is_none() {
            eprintln!("skipping: Default.rsl did not parse");
            return;
        }

        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let _ = state.collect_world_broadcasts(); // spawn NPCs

        let area = state.world.session(1).unwrap().area.clone();
        let Some(npc_rid) = state.spawns.npcs_in_area(&area).map(|n| n.runtime_id).next() else {
            eprintln!("skipping: no NPCs");
            return;
        };
        let npc_race = state.catalog.get(state.spawns.npc(npc_rid).unwrap().actor_id).unwrap().race.clone();

        // Examine the NPC: fires Default.Examine → Output(Actor(), "This is a "+Name(Target)).
        let mut pkt = Vec::new();
        pkt.extend_from_slice(&npc_rid.to_le_bytes());
        let outs = state.handle_examine(1, &pkt);

        // A P_ChatMessage to the clicker (peer 1) with the colored-output prefix
        // and the NPC's race name.
        let chat = outs.iter().find(|o| o.msg_type == P_CHAT_MESSAGE);
        assert!(chat.is_some(), "examine should produce a chat output");
        let chat = chat.unwrap();
        assert!(matches!(chat.target, crate::state::Target::Peer(1)));
        assert_eq!(chat.payload[0], 250); // colored-output marker
        let text = String::from_utf8_lossy(&chat.payload[4..]);
        assert_eq!(text, format!("This is a {npc_race}"));
    }

    #[test]
    fn chat_broadcasts_to_same_area_with_name() {
        use crate::state::Target;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let mut store = tmp_store("chat");
        for user in ["alice", "bob"] {
            let mut acct = Account::new(user, MD5, "x@y.com").unwrap();
            let mut c = Character::blank();
            c.actor_id = template.id;
            c.name = if user == "alice" { "Alice".into() } else { "Bob".into() };
            c.area = template.start_area.clone();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut throttle = LoginThrottle::new();
        let mut world = World::new();
        let config = config_for(dir.clone());
        handle_start_game(&start_packet("alice", MD5, 0), &mut store, &mut throttle, &mut world, &config, 1, 0);
        handle_start_game(&start_packet("bob", MD5, 0), &mut store, &mut throttle, &mut world, &config, 2, 0);

        // Alice says "hello". Broadcast to both peers (sender included) as "<Alice> hello".
        let outs = handle_chat_message(b"hello", &world, &store, 1);
        assert_eq!(outs.len(), 2);
        assert!(outs.iter().all(|o| o.msg_type == P_CHAT_MESSAGE));
        assert!(outs.iter().all(|o| o.payload == b"<Alice> hello"));
        let targets: Vec<_> = outs.iter().map(|o| o.target).collect();
        assert!(targets.contains(&Target::Peer(1)) && targets.contains(&Target::Peer(2)));

        // A command ("/help") produces no broadcast (deferred).
        assert!(handle_chat_message(b"/help", &world, &store, 1).is_empty());
        // A non-session peer can't chat.
        assert!(handle_chat_message(b"hi", &world, &store, 99).is_empty());
    }

    /// Test helper: drive a P_StartGame for `user` on `peer`; returns success.
    fn world_login(state: &mut crate::state::ServerState, user: &str, peer: u32, now: u64) -> bool {
        let pkt = start_packet(user, MD5, 0);
        let replies = handle_start_game(
            &pkt,
            &mut state.accounts,
            &mut state.throttle,
            &mut state.world,
            &state.config,
            peer,
            now,
        );
        replies.len() > 1 // >1 means not the single "N" failure
    }

    #[test]
    fn standard_update_from_non_session_is_ignored() {
        let mut world = World::new();
        let mut p = Vec::new();
        for f in [1.0f32, 2.0, 3.0, 4.0, 5.0] {
            p.extend_from_slice(&f.to_le_bytes());
        }
        p.push(0);
        p.push(0);
        assert!(handle_standard_update(&p, &mut world, 99, 0).is_empty());
    }

    #[test]
    fn nan_position_clamps_to_zero() {
        let dir = data_dir();
        let Some(acct) = account_in_real_area(&dir) else {
            return;
        };
        let mut store = tmp_store("nan");
        store.push(acct);
        let mut throttle = LoginThrottle::new();
        let mut world = World::new();
        let config = config_for(dir);
        handle_start_game(&start_packet("hero", MD5, 0), &mut store, &mut throttle, &mut world, &config, 7, 0);
        let mut p = Vec::new();
        for f in [0.0f32, 0.0, f32::NAN, f32::INFINITY, 1e9] {
            p.extend_from_slice(&f.to_le_bytes());
        }
        p.push(0);
        p.push(0);
        handle_standard_update(&p, &mut world, 7, 0);
        let s = world.session(7).unwrap();
        assert_eq!(s.y, 0.0);
        assert_eq!(s.x, 0.0);
        assert_eq!(s.z, 0.0);
    }

    #[test]
    fn second_login_is_refused() {
        let dir = data_dir();
        let Some(acct) = account_in_real_area(&dir) else {
            return;
        };
        let mut store = tmp_store("double");
        store.push(acct);
        let mut throttle = LoginThrottle::new();
        let mut world = World::new();
        let config = config_for(dir);

        handle_start_game(&start_packet("hero", MD5, 0), &mut store, &mut throttle, &mut world, &config, 1, 0);
        // Second concurrent login (different peer) → refused "N".
        let again = handle_start_game(&start_packet("hero", MD5, 0), &mut store, &mut throttle, &mut world, &config, 2, 0);
        assert_eq!(again, vec![(P_START_GAME, b"N".to_vec())]);
        // After logout, login succeeds again.
        world.logout(1);
        let third = handle_start_game(&start_packet("hero", MD5, 0), &mut store, &mut throttle, &mut world, &config, 3, 0);
        assert!(third.len() > 1);
    }

    #[test]
    fn wrong_password_is_n() {
        let dir = data_dir();
        let Some(acct) = account_in_real_area(&dir) else {
            return;
        };
        let mut store = tmp_store("badpw");
        store.push(acct);
        let mut throttle = LoginThrottle::new();
        let mut world = World::new();
        let config = config_for(dir);
        let replies = handle_start_game(
            &start_packet("hero", "ffffffffffffffffffffffffffffffff", 0),
            &mut store,
            &mut throttle,
            &mut world,
            &config,
            1,
            0,
        );
        assert_eq!(replies, vec![(P_START_GAME, b"N".to_vec())]);
        assert!(!world.is_logged_on("hero"));
    }

    #[test]
    fn appearance_and_group_bvms_mutate_and_gate() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(player_t) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let player_id = player_t.id;
        let start_area = player_t.start_area.clone();
        let mut store = tmp_store("appearancebvm");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = player_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        c.gender = 0;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;

        let run = |state: &mut ServerState, src: &str, privileged: bool| -> Vec<crate::state::Outgoing> {
            state.start_inline_script(src, "Main", rid, 0, 1, privileged);
            let mut all = Vec::new();
            for _ in 0..200 {
                all.extend(state.pump_scripts());
                if state.running_script_count() == 0 {
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
            all
        };
        let hair_of = |state: &ServerState| -> i16 {
            state.accounts.find("hero").unwrap().characters[0].actor.hair
        };

        // SetActorHair (gender 0) mutates the field and broadcasts a 'D' appearance
        // packet — privileged.
        let outs = run(&mut state, "Function Main()\n\tSetActorHair(Actor(), 3)\nEnd Function\n", true);
        assert_eq!(hair_of(&state), 2, "SetActorHair stores 0-based (Param2-1)");
        assert!(
            outs.iter().any(|o| o.msg_type == P_APPEARANCE_UPDATE && o.payload.first() == Some(&b'D')),
            "SetActorHair broadcasts P_AppearanceUpdate 'D'"
        );

        // Unprivileged SetActorHair is gated out (cosmetic-grief vector).
        run(&mut state, "Function Main()\n\tSetActorHair(Actor(), 1)\nEnd Function\n", false);
        assert_eq!(hair_of(&state), 2, "an unprivileged SetActorHair is refused");

        // SetActorClothes broadcasts a 'B' packet and stores 0-based.
        let outs = run(&mut state, "Function Main()\n\tSetActorClothes(Actor(), 2)\nEnd Function\n", true);
        assert_eq!(state.accounts.find("hero").unwrap().characters[0].actor.body_tex, 1);
        assert!(outs.iter().any(|o| o.msg_type == P_APPEARANCE_UPDATE && o.payload.first() == Some(&b'B')));

        // SetActorGroup (privileged) stores a TeamID; ActorGroup reads it back.
        // Reset hair to a known value, then drive ActorGroup's value into an
        // observable: only if ActorGroup==42 does the script change hair.
        run(&mut state, "Function Main()\n\tSetActorHair(Actor(), 1)\nEnd Function\n", true);
        assert_eq!(hair_of(&state), 0);
        run(&mut state, "Function Main()\n\tSetActorGroup(Actor(), 42)\nEnd Function\n", true);
        run(
            &mut state,
            "Function Main()\n\tIf ActorGroup(Actor()) = 42 Then SetActorHair(Actor(), 5)\nEnd Function\n",
            true,
        );
        assert_eq!(hair_of(&state), 4, "ActorGroup read returns the stored TeamID");

        // Unprivileged SetActorGroup is gated (no change to the stored group).
        run(&mut state, "Function Main()\n\tSetActorGroup(Actor(), 7)\nEnd Function\n", false);
        run(
            &mut state,
            "Function Main()\n\tIf ActorGroup(Actor()) = 42 Then SetActorHair(Actor(), 3)\nEnd Function\n",
            true,
        );
        assert_eq!(hair_of(&state), 2, "an unprivileged SetActorGroup did not overwrite the group");
    }

    #[test]
    fn string_helper_and_backpack_bvms() {
        use crate::state::ServerState;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(player_t) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let player_id = player_t.id;
        let start_area = player_t.start_area.clone();
        let mut store = tmp_store("stringbvm");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = player_id;
        c.name = "Hero".into();
        c.area = start_area.clone();
        c.gender = 0;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;
        let run = |state: &mut ServerState, src: &str| {
            state.start_inline_script(src, "Main", rid, 0, 1, true);
            for _ in 0..200 {
                state.pump_scripts();
                if state.running_script_count() == 0 { break; }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
        };
        let face_of = |state: &ServerState| -> i16 {
            state.accounts.find("hero").unwrap().characters[0].actor.face_tex
        };

        // Split("a,b,c", 2) == "b" — drive the result into an observable face change.
        run(&mut state, "Function Main()\n\tIf Split(\"a,b,c\", 2) = \"b\" Then SetActorFace(Actor(), 3)\nEnd Function\n");
        assert_eq!(face_of(&state), 2, "Split returns the 2nd (1-based) field");

        // DeQuote strips quotes; FullTrim strips whitespace — chained observably.
        run(&mut state, "Function Main()\n\tIf DeQuote(Chr(34) + \"x\" + Chr(34)) = \"x\" Then SetActorFace(Actor(), 5)\nEnd Function\n");
        assert_eq!(face_of(&state), 4, "DeQuote removes double-quotes");
        run(&mut state, "Function Main()\n\tIf FullTrim(\"  hi  \") = \"hi\" Then SetActorFace(Actor(), 2)\nEnd Function\n");
        assert_eq!(face_of(&state), 1, "FullTrim strips surrounding whitespace");

        // ScriptPathIsSafe rejects traversal, accepts a clean name.
        run(&mut state, "Function Main()\n\tIf ScriptPathIsSafe(\"mail.dat\") = 1 Then SetActorFace(Actor(), 4)\nEnd Function\n");
        assert_eq!(face_of(&state), 3, "ScriptPathIsSafe accepts a clean relative name");
        run(&mut state, "Function Main()\n\tIf ScriptPathIsSafe(\"../etc/passwd\") = 0 Then SetActorFace(Actor(), 1)\nEnd Function\n");
        assert_eq!(face_of(&state), 0, "ScriptPathIsSafe rejects a '..' traversal");

        // ActorBackpack on an empty backpack slot returns 0 (no item).
        run(&mut state, "Function Main()\n\tIf ActorBackpack(Actor(), 1) = 0 Then SetActorFace(Actor(), 3)\nEnd Function\n");
        assert_eq!(face_of(&state), 2, "ActorBackpack returns 0 for an empty slot");
    }

    #[test]
    fn mount_subsystem_rides_glues_and_dismounts() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let player_id = template.id;
        let start_area = template.start_area.clone();
        // A rideable template, if the project ships one — used to exercise the
        // right-click mount gate end-to-end. Falls back to the playable id (the
        // right-click branch then won't mount, and we drive set_mount directly).
        let rideable_id = catalog.templates.values().find(|t| t.rideable).map(|t| t.id);

        let mut store = tmp_store("mount");
        let mut acct = Account::new("rider", MD5, "r@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = player_id;
        c.name = "Rider".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("rider", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();

        // Stage a scriptless mount co-located with the rider (both at 0,0,0).
        let mount_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: mount_rid,
            actor_id: rideable_id.unwrap_or(player_id),
            area: area.clone(),
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 10,
            hp_max: 10,
            target_peer: None,
            last_attack_ms: 0,
            script: String::new(), // scriptless → eligible for the ride branch
            death_script: String::new(),
            stock: Vec::new(),
        });

        if rideable_id.is_some() {
            // Right-click the rideable NPC → it becomes the rider's mount.
            state.handle_right_click(1, &mount_rid.to_le_bytes());
            assert_eq!(state.world.mount_of(1), mount_rid, "right-clicking a rideable NPC mounts it");
        } else {
            // No rideable template shipped: a scriptless non-rideable right-click
            // must NOT mount (the gate holds), then drive the mount directly.
            state.handle_right_click(1, &mount_rid.to_le_bytes());
            assert_eq!(state.world.mount_of(1), 0, "a non-rideable NPC is not mountable");
            state.world.set_mount(1, mount_rid);
        }

        // The mount id rides in the rider's standard update at byte offset 20.
        let wire = super::standard_update_to_wire(state.world.session(1).unwrap());
        assert_eq!(u16::from_le_bytes([wire[20], wire[21]]), mount_rid, "standard-update carries the mount id");

        // The mount is now "ridden" → AI collectors skip it; the glue snaps it to
        // the rider. Move the rider, run glue, and the NPC follows.
        state.world.move_session(1, 50.0, 0.0, 60.0);
        let _ = state.collect_mount_glue();
        let n = state.spawns.npc(mount_rid).unwrap();
        assert!((n.x - 50.0).abs() < 0.01 && (n.z - 60.0).abs() < 0.01, "the mount glues to the rider");

        // Mounted riders don't burn stamina: set running + energy, drain → unchanged.
        if let Some(eidx) = state.energy_stat {
            state.world.sessions.get_mut(&1).unwrap().is_running = 1;
            state.accounts.find_mut("rider").unwrap().characters[0].actor.attributes.value[eidx] = 100;
            state.collect_energy_drain();
            assert_eq!(
                state.accounts.find("rider").unwrap().characters[0].actor.attributes.value[eidx],
                100,
                "a mounted rider's energy doesn't drain"
            );
        }

        // Dismount → relationship cleared, standard update no longer carries it.
        state.handle_dismount(1);
        assert_eq!(state.world.mount_of(1), 0, "dismount clears the mount");
        let wire = super::standard_update_to_wire(state.world.session(1).unwrap());
        assert_eq!(u16::from_le_bytes([wire[20], wire[21]]), 0, "post-dismount standard-update has no mount");
    }

    /// The mount pieces layered on top of the subsystem test above: the
    /// `ActorMount`/`ActorRider` BVM reads track the live mount link, and the
    /// mount/dismount paths fire the shipped `Mount.rsl` hooks the way Blitz
    /// does (`ThreadScript("Mount","Mount"/"Dismount", rider, mount)`,
    /// `ServerNet.bb:1502`/`:1806`) — including the Dismount un-clip nudge
    /// that moves the rider +5 Z out of the mount mesh.
    #[test]
    fn mount_hooks_fire_shipped_script_and_bvm_reads_track_state() {
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        use std::time::Duration;
        let dir = data_dir();
        let mut catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let player_id = template.id;
        let start_area = template.start_area.clone();
        // The shipped project ships no rideable template (Cycle-95 finding), which
        // would leave the right-click mount branch — the Mount hook's only
        // initiation path — unexercised. Force one so the hook coverage is
        // deterministic, not data-dependent.
        catalog.templates.get_mut(&player_id).unwrap().rideable = true;

        let mut store = tmp_store("mounthooks");
        let mut acct = Account::new("rider", MD5, "r@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = player_id;
        c.name = "Rider".into();
        c.area = start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("Mount").is_none() {
            eprintln!("skipping: no shipped Mount.rsl");
            return;
        }
        handle_start_game(&start_packet("rider", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let rid = state.world.session(1).unwrap().runtime_id;
        let area = state.world.session(1).unwrap().area.clone();

        // Inline-script driver + observable, per the string-helper test.
        let run = |state: &mut ServerState, src: &str| {
            state.start_inline_script(src, "Main", rid, 0, 1, true);
            let deadline = std::time::Instant::now() + Duration::from_secs(5);
            while std::time::Instant::now() < deadline {
                state.pump_scripts();
                if state.running_script_count() == 0 {
                    break;
                }
                std::thread::sleep(Duration::from_millis(1));
            }
        };
        let face_of = |state: &ServerState| -> i16 {
            state.accounts.find("rider").unwrap().characters[0].actor.face_tex
        };
        // Pump until every running script (a fired hook) completes.
        let drain = |state: &mut ServerState| {
            let deadline = std::time::Instant::now() + Duration::from_secs(5);
            while state.running_script_count() > 0 && std::time::Instant::now() < deadline {
                state.pump_scripts();
                std::thread::sleep(Duration::from_millis(1));
            }
        };

        // Stage a scriptless mount co-located with the rider.
        let mount_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: mount_rid,
            actor_id: player_id, // forced rideable above
            area: area.clone(),
            x: 0.0,
            y: 0.0,
            z: 0.0,
            hp: 10,
            hp_max: 10,
            target_peer: None,
            last_attack_ms: 0,
            script: String::new(),
            death_script: String::new(),
            stock: Vec::new(),
        });

        // Unmounted: both reads are 0 — drive them into an observable face change.
        run(&mut state, &format!("Function Main()\n\tIf ActorMount(Actor()) = 0 And ActorRider({mount_rid}) = 0 Then SetActorFace(Actor(), 3)\nEnd Function\n"));
        assert_eq!(face_of(&state), 2, "ActorMount/ActorRider read 0 while unmounted");

        // Mount through the right-click branch — proving the Mount hook fires.
        state.handle_right_click(1, &mount_rid.to_le_bytes());
        assert_eq!(state.world.mount_of(1), mount_rid, "right-clicking a rideable NPC mounts it");
        assert_eq!(state.running_script_count(), 1, "mounting fires Mount.Mount (ServerNet.bb:1502)");
        drain(&mut state);
        assert_eq!(state.running_script_count(), 0, "the shipped Mount() runs to completion");

        // Mounted: ActorMount(rider) → the mount's rid, ActorRider(mount) → the rider's.
        run(&mut state, &format!("Function Main()\n\tIf ActorMount(Actor()) = {mount_rid} And ActorRider({mount_rid}) = {rid} Then SetActorFace(Actor(), 5)\nEnd Function\n"));
        assert_eq!(face_of(&state), 4, "ActorMount/ActorRider read the live mount link");

        // Dismount fires Mount.Dismount (ServerNet.bb:1806); the shipped script
        // nudges the rider +5 Z (after a DoEvents(100) settle) to un-clip them.
        let z_before = state.world.session(1).unwrap().z;
        state.handle_dismount(1);
        assert_eq!(state.world.mount_of(1), 0, "dismount clears the mount");
        assert_eq!(state.running_script_count(), 1, "dismounting fires Mount.Dismount (ServerNet.bb:1806)");
        drain(&mut state);
        assert_eq!(state.running_script_count(), 0, "the shipped Dismount runs to completion");
        let z_after = state.world.session(1).unwrap().z;
        assert!(
            (z_after - (z_before + 5.0)).abs() < 0.01,
            "the shipped Dismount un-clip nudge moves the rider +5 Z (z {z_before} -> {z_after})"
        );

        // Post-dismount: both reads are 0 again.
        run(&mut state, &format!("Function Main()\n\tIf ActorMount(Actor()) = 0 And ActorRider({mount_rid}) = 0 Then SetActorFace(Actor(), 1)\nEnd Function\n"));
        assert_eq!(face_of(&state), 0, "ActorMount/ActorRider read 0 after dismount");
    }

    #[test]
    fn change_password_verifies_old_and_session_ownership() {
        use crate::login::P_CHANGE_PASSWORD;
        use crate::state::ServerState;
        use rcce_server_accounts::password;
        const NEW_MD5: &str = "0123456789abcdef0123456789abcdef";

        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let mut store = tmp_store("changepw");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template.id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);

        // Build `[str user][str old][str new]` with 1-byte length prefixes.
        let pkt = |user: &str, old: &str, new: &str| {
            let mut p = Vec::new();
            for f in [user, old, new] {
                p.push(f.len() as u8);
                p.extend_from_slice(f.as_bytes());
            }
            p
        };

        // Not logged in → ownership check fails → "P", password unchanged.
        let out = state.dispatch(1, P_CHANGE_PASSWORD, &pkt("hero", MD5, NEW_MD5));
        assert_eq!(out[0].payload, b"P", "change-password refused when the requester isn't the live session");
        assert!(password::verify_password(&state.accounts.find("hero").unwrap().pass, MD5), "password unchanged");

        // Log the account in on peer 1, then change it from peer 1 → "Y".
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let out = state.dispatch(1, P_CHANGE_PASSWORD, &pkt("hero", MD5, NEW_MD5));
        assert_eq!(out[0].payload, b"Y", "owner with correct old password succeeds");
        let stored = state.accounts.find("hero").unwrap().pass.clone();
        assert!(password::verify_password(&stored, NEW_MD5), "new password now verifies");
        assert!(!password::verify_password(&stored, MD5), "old password no longer works");

        // Wrong old password → "P".
        let out = state.dispatch(1, P_CHANGE_PASSWORD, &pkt("hero", MD5, "ffffffffffffffffffffffffffffffff"));
        assert_eq!(out[0].payload, b"P", "wrong old password is refused");

        // A different peer (not the owner) is refused even with the right old pw.
        let out = state.dispatch(2, P_CHANGE_PASSWORD, &pkt("hero", NEW_MD5, MD5));
        assert_eq!(out[0].payload, b"P", "a non-owner session can't change the password");
    }

    #[test]
    fn two_player_marriage_completes_end_to_end() {
        // The full shipped marriage ceremony driven end-to-end on the Rust engine:
        // two players, A targets B, A walks the priest's dialogs, **B is asked and
        // accepts via a cross-player dialog**, A names the union via a free-text
        // Input, and both end up married (ActorGlobal "1|…"). Exercises the two
        // capabilities added this cycle: cross-player dialog routing + the Input
        // prompt. Restores the gitignored TakenNames.dat so the real data/ is left
        // untouched.
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        use std::time::{Duration, Instant};
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/marriage.rsl").exists()
            || !dir.join("Server Data/Scripts/RC_Core.rsl").exists()
        {
            eprintln!("skipping: marriage.rsl / RC_Core.rsl not present");
            return;
        }
        let taken = dir.join("Server Data/Script Files/TakenNames.dat");
        let taken_backup = std::fs::read(&taken).ok();

        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let area0 = template.start_area.clone();
        let mut store = tmp_store("marry2");
        for (u, e) in [("groom", "g@x.com"), ("bride", "b@x.com")] {
            let mut acct = Account::new(u, MD5, e).unwrap();
            let mut c = Character::blank();
            c.actor_id = template_id;
            c.name = if u == "groom" { "Groom".into() } else { "Bride".into() };
            c.area = area0.clone();
            c.gold = 50_000;
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        if state.scripts.get("marriage").is_none() {
            return;
        }
        handle_start_game(&start_packet("groom", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("bride", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);
        let a_rid = state.world.session(1).unwrap().runtime_id; // groom (peer 1)
        let b_rid = state.world.session(2).unwrap().runtime_id; // bride (peer 2)
        let b_peer = 2u32;

        // Priest NPC (the dialog context actor).
        let priest = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: priest, actor_id: template_id, area: area0, x: 0.0, y: 0.0, z: 0.0,
            hp: 1, hp_max: 1, target_peer: None, last_attack_ms: 0,
            script: String::new(), death_script: String::new(), stock: Vec::new(),
        });

        // Groom targets the bride (the in-PvP-zone attack-target the script reads
        // via ActorTarget — set directly here).
        state.world.set_player_target(1, b_rid);

        // Fire the marriage ceremony for the groom, priest = context.
        state.fire_hook_async("marriage", "Main", a_rid, priest, 1);

        // Drive the dialog/input flow for ~5s: answer each prompt for whichever
        // player it targets. Option 1 = "Yes" throughout; the free-text Input gets
        // a unique last name.
        let lname = format!("Tplr{}", std::process::id() % 100000);
        let deadline = Instant::now() + Duration::from_secs(5);
        while Instant::now() < deadline && state.running_script_count() > 0 {
            for o in state.pump_scripts() {
                let peer = match o.target {
                    crate::state::Target::Peer(p) => p,
                    _ => continue,
                };
                if o.msg_type == crate::world::P_DIALOG {
                    match o.payload.first() {
                        Some(b'N') => {
                            // Open: reply "N" + handle(4) + dhandle(4) (echo handle).
                            let mut r = vec![b'N'];
                            r.extend_from_slice(&o.payload[1..5]);
                            r.extend_from_slice(&o.payload[1..5]);
                            state.handle_dialog_response(peer, &r);
                        }
                        Some(b'T') => {
                            // Text output also awaits an ack (RC_Core DialogOutput
                            // waits on GetWaitResult): reply "T" + handle(4).
                            let mut r = vec![b'T'];
                            r.extend_from_slice(&o.payload[1..5]);
                            state.handle_dialog_response(peer, &r);
                        }
                        Some(b'O') => {
                            // Options: reply "O" + handle(4) + selected option (1 = Yes).
                            let mut r = vec![b'O'];
                            r.extend_from_slice(&o.payload[1..5]);
                            r.push(1);
                            state.handle_dialog_response(peer, &r);
                        }
                        _ => {} // 'C' close: no reply
                    }
                } else if o.msg_type == crate::world::P_SCRIPT_INPUT {
                    // Free-text Input prompt → reply [handle(4)][text].
                    let mut r = vec![0u8; 4];
                    r.extend_from_slice(lname.as_bytes());
                    state.handle_script_input(peer, &r);
                }
            }
            std::thread::sleep(Duration::from_millis(1));
        }

        // Both players are now married: ActorGlobal slot 1 begins "1|".
        let married = |state: &ServerState, user: &str| -> bool {
            state
                .accounts
                .find(user)
                .and_then(|a| a.characters.first())
                .map(|r| r.actor.script_globals.get(1).map(|g| g.starts_with("1|")).unwrap_or(false))
                .unwrap_or(false)
        };
        let groom_married = married(&state, "groom");
        let bride_married = married(&state, "bride");

        // Restore the gitignored TakenNames.dat before asserting.
        match taken_backup {
            Some(b) => { let _ = std::fs::write(&taken, b); }
            None => { let _ = std::fs::remove_file(&taken); }
        }
        let _ = (a_rid, b_peer);

        assert!(groom_married, "groom is married after the ceremony");
        assert!(bride_married, "bride is married (cross-player dialog accepted)");
    }

    #[test]
    fn attacking_sets_the_players_actortarget() {
        // Blitz `P_AttackActor` sets `AI\AITarget = A2` (ServerNet.bb:1612) so
        // `ActorTarget(player)` / `/assist` can read the player's current target.
        // The port previously tracked only the NPC's retaliation target, so
        // `ActorTarget(playerRid)` was always 0.
        use crate::state::ServerState;
        use crate::spawn::NpcActor;
        let dir = data_dir();
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some())
        else {
            return;
        };
        let template_id = template.id;
        let mut store = tmp_store("aitarget");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = template_id;
        c.name = "Hero".into();
        c.area = template.start_area.clone();
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("hero", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        let area = state.world.session(1).unwrap().area.clone();
        let hero_rid = state.world.session(1).unwrap().runtime_id;

        let npc_rid = state.world.alloc_runtime();
        state.spawns.insert_npc(NpcActor {
            runtime_id: npc_rid, actor_id: template_id, area, x: 0.0, y: 0.0, z: 0.0,
            hp: 50, hp_max: 50, target_peer: None, last_attack_ms: 0,
            script: String::new(), death_script: String::new(), stock: Vec::new(),
        });

        assert_eq!(state.world.target_of_runtime(hero_rid), 0, "no target before attacking");
        // now_ms past the combat-delay gate (it rejects swings within ~1s of the
        // last, and a fresh test's monotonic clock is still near 0).
        state.handle_attack(1, &npc_rid.to_le_bytes(), 10_000);
        assert_eq!(
            state.world.target_of_runtime(hero_rid), npc_rid,
            "attacking sets the player's AITarget"
        );
    }

    #[test]
    fn attacking_a_player_in_a_pvp_area_sets_the_target() {
        // Blitz sets the attacker's AITarget on a player target only in a PvP zone
        // (`A2\RNID < 0 Or Area\PvP`, ServerNet.bb:1612). Plains ships pvp=1, so an
        // attack there acquires the target (this is how the marriage ceremony's
        // target-selection works); a non-PvP zone leaves it unset.
        use crate::state::ServerState;
        let dir = data_dir();
        if Area::load(&dir, "Plains").map(|a| a.pvp).unwrap_or(0) == 0 {
            return; // project's Plains isn't PvP — nothing to assert
        }
        let catalog = rcce_server_core::ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let Some(template) = catalog.templates.values().find(|t| t.playable) else { return };
        let tid = template.id;
        let mut store = tmp_store("pvptarget");
        for (u, e) in [("alice", "a@x.com"), ("bob", "b@x.com")] {
            let mut acct = Account::new(u, MD5, e).unwrap();
            let mut c = Character::blank();
            c.actor_id = tid;
            c.name = if u == "alice" { "Alice".into() } else { "Bob".into() };
            c.area = "Plains".into();
            acct.characters.push(CharacterRecord::new(c));
            store.push(acct);
        }
        let mut state = ServerState::new(config_for(dir.clone()), store, catalog);
        handle_start_game(&start_packet("alice", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 1, 0);
        handle_start_game(&start_packet("bob", MD5, 0), &mut state.accounts, &mut state.throttle, &mut state.world, &state.config, 2, 0);
        if state.world.session(1).is_none() || state.world.session(2).is_none() {
            return; // Plains didn't accept the players (data variance)
        }
        let alice = state.world.session(1).unwrap().runtime_id;
        let bob = state.world.session(2).unwrap().runtime_id;

        assert_eq!(state.world.target_of_runtime(alice), 0, "no target before attacking");
        state.handle_attack(1, &bob.to_le_bytes(), 10_000);
        assert_eq!(
            state.world.target_of_runtime(alice), bob,
            "attacking a player in a PvP area sets the attacker's AITarget"
        );
    }

    #[test]
    fn shipped_marriage_script_runs_and_does_file_io_on_the_rust_engine() {
        // The actual shipped marriage.rsl, executed end-to-end on the Rust BVM
        // engine: its opening block does real file I/O (`FileSize`/`WriteFile`/
        // `CloseFile` on TakenNames.dat) before any dialog, then the `Money < 10000`
        // check returns cleanly for a null actor. Proves the shipped content script
        // RUNS (not just parses) + its file-stream calls work — the foundation of
        // the marriage/mail features. Writes only into a TEMP data dir.
        use crate::state::ServerState;
        let real = data_dir();
        let Ok(src) = std::fs::read_to_string(real.join("Server Data/Scripts/marriage.rsl")) else {
            eprintln!("skipping: no marriage.rsl");
            return;
        };
        let tmp = std::env::temp_dir().join(format!("rcce_marriage_{}", std::process::id()));
        let sandbox = tmp.join("Server Data").join("Script Files");
        let _ = std::fs::remove_dir_all(&tmp);
        std::fs::create_dir_all(&sandbox).unwrap();

        let store = tmp_store("marriage");
        let mut state = ServerState::new(config_for(tmp.clone()), store, rcce_server_core::ActorCatalog::default());

        // marriage is on the privileged allowlist (so WriteFile is permitted);
        // fire it privileged, actor/ctx = 0 (the file block precedes Actor()).
        state.start_inline_script(&src, "Main", 0, 0, 1, true);
        for _ in 0..300 {
            state.pump_scripts();
            if state.running_script_count() == 0 {
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(1));
        }

        assert!(
            sandbox.join("TakenNames.dat").exists(),
            "the shipped marriage script created TakenNames.dat via WriteFile on the Rust engine"
        );
        let _ = std::fs::remove_dir_all(&tmp);
    }

    #[test]
    fn script_file_helpers_are_pure_and_correct() {
        use crate::state::{script_path_is_safe, split_file_lines};
        // Path-traversal guard.
        assert!(script_path_is_safe("TakenNames.dat"));
        assert!(script_path_is_safe("sub/mail.dat"));
        assert!(!script_path_is_safe(""));
        assert!(!script_path_is_safe("../escape"));
        assert!(!script_path_is_safe("/etc/passwd"));
        assert!(!script_path_is_safe("C:\\Windows"));
        assert!(!script_path_is_safe("bad\u{0}byte"));
        // Line splitting: CRLF tolerance + no spurious trailing empty line.
        assert_eq!(split_file_lines("a\nb\n"), vec!["a", "b"]);
        assert_eq!(split_file_lines("a\r\nb"), vec!["a", "b"]);
        assert_eq!(split_file_lines(""), Vec::<String>::new());
        assert_eq!(split_file_lines("a\n\n"), vec!["a", ""]);
    }

    #[test]
    fn file_stream_bvms_round_trip_on_disk() {
        use crate::state::ServerState;
        // A dedicated temp data dir — NEVER the real project data/ (these BVMs
        // write files). Build the `Server Data/Script Files/` sandbox by hand.
        let tmp = std::env::temp_dir().join(format!("rcce_filebvm_{}", std::process::id()));
        let sandbox = tmp.join("Server Data").join("Script Files");
        let _ = std::fs::remove_dir_all(&tmp);
        std::fs::create_dir_all(&sandbox).unwrap();

        let catalog = rcce_server_core::ActorCatalog::load(data_dir().join("Server Data/Actors.dat"));
        let store = tmp_store("filebvm");
        let mut state = ServerState::new(config_for(tmp.clone()), store, catalog);

        let run = |state: &mut ServerState, src: &str, privileged: bool| {
            state.start_inline_script(src, "Main", 0, 0, 1, privileged);
            for _ in 0..200 {
                state.pump_scripts();
                if state.running_script_count() == 0 { break; }
                std::thread::sleep(std::time::Duration::from_millis(1));
            }
        };

        // Write two lines, append a third, read all three back into out.dat, and
        // record Eof-after-last into eof.dat. Privileged (openers are gated).
        let src = "Function Main()\n\
            \tFH = WriteFile(\"data.dat\")\n\
            \tWriteLine(FH, \"alpha\")\n\
            \tWriteLine(FH, \"beta\")\n\
            \tCloseFile(FH)\n\
            \tAF = AppendFile(\"data.dat\")\n\
            \tWriteLine(AF, \"gamma\")\n\
            \tCloseFile(AF)\n\
            \tR = OpenFile(\"data.dat\")\n\
            \tOut = WriteFile(\"out.dat\")\n\
            \tWriteLine(Out, ReadLine(R))\n\
            \tWriteLine(Out, ReadLine(R))\n\
            \tWriteLine(Out, ReadLine(R))\n\
            \tE = WriteFile(\"eof.dat\")\n\
            \tWriteLine(E, EoF(R))\n\
            \tCloseFile(E)\n\
            \tCloseFile(R)\n\
            \tCloseFile(Out)\n\
            End Function\n";
        run(&mut state, src, true);

        let out = std::fs::read_to_string(sandbox.join("out.dat")).unwrap_or_default();
        assert_eq!(out, "alpha\nbeta\ngamma\n", "append + readline round-trip");
        let eof = std::fs::read_to_string(sandbox.join("eof.dat")).unwrap_or_default();
        assert_eq!(eof, "1\n", "Eof is 1 after the last line is read");

        // FileType/FileSize reads, then DeleteFile. data.dat is "alpha\nbeta\ngamma\n"
        // = 17 bytes; type 1 (file).
        let meta_src = "Function Main()\n\
            \tM = WriteFile(\"meta.dat\")\n\
            \tWriteLine(M, FileType(\"data.dat\"))\n\
            \tWriteLine(M, FileSize(\"data.dat\"))\n\
            \tCloseFile(M)\n\
            \tDeleteFile(\"data.dat\")\n\
            End Function\n";
        run(&mut state, meta_src, true);
        let meta = std::fs::read_to_string(sandbox.join("meta.dat")).unwrap_or_default();
        assert_eq!(meta, "1\n17\n", "FileType=1 (file), FileSize=17 bytes");
        assert!(!sandbox.join("data.dat").exists(), "DeleteFile removed the file");

        // Unprivileged WriteFile is gated → handle 0, no file written.
        run(&mut state, "Function Main()\n\tF = WriteFile(\"nope.dat\")\nEnd Function\n", false);
        assert!(!sandbox.join("nope.dat").exists(), "unprivileged WriteFile is refused");

        let _ = std::fs::remove_dir_all(&tmp);
    }
}
