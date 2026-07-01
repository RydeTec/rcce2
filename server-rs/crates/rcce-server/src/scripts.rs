//! Game-side scripting integration: load the `.rsl` content scripts at boot and
//! provide the [`Host`] — the `BVM_*` command surface backed by `ServerState` —
//! that the `rcce-script` interpreter calls back into.
//!
//! Privilege model (root `CLAUDE.md`): scripts named in
//! `Data/Server Data/Privileged Scripts.dat` run **privileged** and may call
//! gated commands (`ChangeGold`, `GiveItem`, …); others (clicker-driven
//! `Examine`/`RightClick`) are unprivileged and those calls no-op. The flag is
//! per-script-run, set from the allowlist.

use std::collections::{HashMap, HashSet};
use std::path::Path;

use rcce_script::ast::Program;
use rcce_script::{builtin, Host, Value};
use rcce_server_accounts::store::AccountStore;
use rcce_server_core::record::CharacterRecord;
use rcce_server_core::ActorCatalog;

use crate::spawn::SpawnManager;
use crate::state::Outgoing;
use crate::world::{self, World};

/// Module name from a `Using "RC_Core.rcm"` path (stem, lowercased).
fn module_name(u: &str) -> Option<String> {
    std::path::Path::new(u).file_stem().map(|s| s.to_string_lossy().to_lowercase())
}

/// `P_Dialog` wire encoders (`ScriptingCommands.bb:3265-3299`). The dialog
/// window/text/options the dialog scripts (via RC_Core) send to the client.
/// Ready for the async-wait integration that drives them; pure + tested.
pub mod dialog {
    /// `"N" + [u32 hSI][u16 ctxRuntimeId][u16 bgTexId] + title` — open a dialog.
    pub fn open(hsi: u32, ctx_runtime_id: u16, bg_tex_id: u16, title: &str) -> Vec<u8> {
        let mut p = vec![b'N'];
        p.extend_from_slice(&hsi.to_le_bytes());
        p.extend_from_slice(&ctx_runtime_id.to_le_bytes());
        p.extend_from_slice(&bg_tex_id.to_le_bytes());
        p.extend_from_slice(title.as_bytes());
        p
    }
    /// `"T" + [u8 r][u8 g][u8 b][u32 dhandle] + message` — append a text line.
    pub fn output(r: u8, g: u8, b: u8, dhandle: u32, message: &str) -> Vec<u8> {
        let mut p = vec![b'T', r, g, b];
        p.extend_from_slice(&dhandle.to_le_bytes());
        p.extend_from_slice(message.as_bytes());
        p
    }
    /// `"C" + [u32 dhandle]` — close the dialog.
    pub fn close(dhandle: u32) -> Vec<u8> {
        let mut p = vec![b'C'];
        p.extend_from_slice(&dhandle.to_le_bytes());
        p
    }
    /// `"O" + [u32 dhandle] + (per option: [u8 len][option])` — present up to 9
    /// clickable options.
    pub fn input(dhandle: u32, options: &[&str]) -> Vec<u8> {
        let mut p = vec![b'O'];
        p.extend_from_slice(&dhandle.to_le_bytes());
        for opt in options.iter().take(9) {
            if opt.is_empty() {
                break;
            }
            p.push(opt.len().min(255) as u8);
            p.extend_from_slice(opt.as_bytes());
        }
        p
    }

    /// `P_ScriptInput` free-text prompt (server→client), `RCE_SendInput`
    /// (ScriptingCommands.bb:3302): `[u32 hSI][u8 iType][u16 titleLen][title][prompt]`.
    pub fn script_input(hsi: u32, itype: u8, title: &str, prompt: &str) -> Vec<u8> {
        let mut p = Vec::new();
        p.extend_from_slice(&hsi.to_le_bytes());
        p.push(itype);
        p.extend_from_slice(&(title.len() as u16).to_le_bytes());
        p.extend_from_slice(title.as_bytes());
        p.extend_from_slice(prompt.as_bytes());
        p
    }
}

/// All parsed content scripts (by lowercase name) + the privileged allowlist.
#[derive(Default)]
pub struct ScriptRegistry {
    scripts: HashMap<String, Program>,
    privileged: HashSet<String>,
}

impl ScriptRegistry {
    /// Parse every `.rsl` under `<data_dir>/Server Data/Scripts/` and load the
    /// privileged allowlist. Returns `(registry, parse_failure_count)`.
    pub fn load(data_dir: &Path) -> (Self, usize) {
        let server = data_dir.join("Server Data");
        let dir = server.join("Scripts");
        let mut scripts = HashMap::new();
        let mut failures = 0;
        if let Ok(rd) = std::fs::read_dir(&dir) {
            for entry in rd.flatten() {
                let p = entry.path();
                if p.extension().and_then(|s| s.to_str()) != Some("rsl") {
                    continue;
                }
                let Some(stem) = p.file_stem().map(|s| s.to_string_lossy().to_lowercase()) else {
                    continue;
                };
                let Ok(src) = std::fs::read_to_string(&p) else {
                    continue;
                };
                match rcce_script::parser::parse(&src) {
                    Ok(prog) => {
                        scripts.insert(stem, prog);
                    }
                    Err(_) => failures += 1,
                }
            }
        }
        // Privileged allowlist (one script name per line; `;` comments).
        let mut privileged = HashSet::new();
        if let Ok(txt) = std::fs::read_to_string(server.join("Privileged Scripts.dat")) {
            for line in txt.lines() {
                let l = line.trim();
                if !l.is_empty() && !l.starts_with(';') {
                    privileged.insert(l.to_lowercase());
                }
            }
        }
        (Self { scripts, privileged }, failures)
    }

    pub fn get(&self, name: &str) -> Option<&Program> {
        self.scripts.get(&name.to_lowercase())
    }

    /// A runnable program for `name`: its own functions plus those of every
    /// module it `Using`s (transitively — `RC_Core` etc.), so std-lib helpers
    /// like `OpenDialog`/`DialogOutput` resolve. The script's own functions win
    /// on a name collision (they're appended last; the interpreter's table keeps
    /// the last). Returns an owned `Program` (cheap clone of small ASTs).
    pub fn linked_program(&self, name: &str) -> Option<Program> {
        let main = self.get(name)?;
        let mut funcs: Vec<rcce_script::ast::Function> = Vec::new();
        let mut seen: HashSet<String> = HashSet::new();
        seen.insert(name.to_lowercase());
        // BFS over imports; collect their functions first (main overrides).
        let mut queue: Vec<String> = main.uses.iter().filter_map(|u| module_name(u)).collect();
        while let Some(m) = queue.pop() {
            if !seen.insert(m.clone()) {
                continue;
            }
            if let Some(imp) = self.scripts.get(&m) {
                funcs.extend(imp.functions.iter().cloned());
                for u in &imp.uses {
                    if let Some(mn) = module_name(u) {
                        queue.push(mn);
                    }
                }
            }
        }
        funcs.extend(main.functions.iter().cloned()); // main last → wins
        Some(Program {
            uses: main.uses.clone(),
            globals: main.globals.clone(),
            functions: funcs,
        })
    }
    pub fn is_privileged(&self, name: &str) -> bool {
        self.privileged.contains(&name.to_lowercase())
    }
    pub fn len(&self) -> usize {
        self.scripts.len()
    }
    pub fn is_empty(&self) -> bool {
        self.scripts.is_empty()
    }
}

/// A message from a running script thread to the main loop.
pub enum ScriptMsg {
    /// A `BVM_*` call to execute against `ServerState`; the result goes back on
    /// `reply` (blocking the script thread until it does — which is how
    /// `GetWaitResult` suspends for a player's dialog response).
    Call {
        name: String,
        args: Vec<rcce_script::Value>,
        reply: std::sync::mpsc::Sender<rcce_script::Value>,
    },
    /// The script function returned.
    Done,
}

/// The [`Host`] used on a script THREAD — it doesn't touch `ServerState`; every
/// command is marshalled to the main loop over a channel and the thread blocks
/// for the reply. A blocking command (`GetWaitResult` while a dialog is pending)
/// just isn't replied to until the player responds — the suspend mechanism.
pub struct MarshalHost {
    pub tx: std::sync::mpsc::Sender<ScriptMsg>,
}

impl Host for MarshalHost {
    fn call(&mut self, name: &str, args: &[rcce_script::Value]) -> rcce_script::Value {
        let (rtx, rrx) = std::sync::mpsc::channel();
        if self
            .tx
            .send(ScriptMsg::Call { name: name.to_string(), args: args.to_vec(), reply: rtx })
            .is_err()
        {
            return rcce_script::Value::Int(0);
        }
        rrx.recv().unwrap_or(rcce_script::Value::Int(0))
    }
}

/// Decode an inbound `P_Dialog` response (client→server) into the `WaitResult`
/// string the script's `GetWaitResult` should return (`ServerNet.bb:1300-1318`):
/// `"N"`+hSI+handle → the handle; `"T"`+hSI → `"0"`; `"O"`+hSI+choice → choice.
pub fn decode_dialog_response(payload: &[u8]) -> Option<String> {
    if payload.len() < 5 {
        return None;
    }
    match payload[0] {
        b'N' => {
            // handle = little-endian int of the bytes after the 4-byte hSI.
            let mut h = 0u32;
            for (i, &b) in payload[5..].iter().take(4).enumerate() {
                h |= (b as u32) << (8 * i);
            }
            Some(h.to_string())
        }
        b'T' => Some("0".to_string()),
        b'O' => Some(payload.get(5).copied().unwrap_or(0).to_string()),
        _ => None,
    }
}

/// The `BVM_*` command host for one script run.
pub struct ScriptHost<'a> {
    pub world: &'a World,
    pub accounts: &'a mut AccountStore,
    pub spawns: &'a SpawnManager,
    pub catalog: &'a ActorCatalog,
    /// Attribute slot names (`Attributes.dat`) for name→index resolution.
    pub attr_names: &'a rcce_data::attributes::AttributeNames,
    /// Server RNG (for `Rand`).
    pub rng: &'a mut rcce_server_core::rng::Rng,
    pub actor: i64,
    pub ctx: i64,
    /// Whether this script run may call privileged (gated) commands.
    pub privileged: bool,
    /// Set when a command mutated persistent account state (→ flush).
    pub dirty: bool,
    pub out: Vec<Outgoing>,
}

impl ScriptHost<'_> {
    /// (username, character slot) of the player actor with this runtime id.
    fn player_loc(&self, rid: u16) -> Option<(String, usize)> {
        self.world.session_for_runtime(rid).map(|s| (s.user.clone(), s.char_slot as usize))
    }

    fn with_char<R>(&self, rid: u16, f: impl FnOnce(&CharacterRecord) -> R) -> Option<R> {
        let (u, s) = self.player_loc(rid)?;
        self.accounts.find(&u).and_then(|a| a.characters.get(s)).map(f)
    }

    fn with_char_mut<R>(&mut self, rid: u16, f: impl FnOnce(&mut CharacterRecord) -> R) -> Option<R> {
        let (u, s) = self.player_loc(rid)?;
        let r = self.accounts.find_mut(&u).and_then(|a| a.characters.get_mut(s)).map(f);
        if r.is_some() {
            self.dirty = true;
        }
        r
    }

    /// Broadcast `P_GoldChange` to the actor's player, if online — `"U"+i32` for
    /// a gain, `"D"+i32(abs)` for a loss (`BVM_CHANGEGOLD`, `ScriptingCommands.bb:2664`).
    /// Sends the **requested** change (matches Blitz, which encodes `Change` even
    /// when the wallet clamped at 0). No-op for a zero change or a non-player.
    fn send_gold_change(&mut self, rid: u16, change: i32) {
        if change == 0 {
            return;
        }
        if let Some(peer) = self.world.peer_for_runtime(rid) {
            let mut p = Vec::with_capacity(5);
            p.push(if change > 0 { b'U' } else { b'D' });
            p.extend_from_slice(&change.unsigned_abs().to_le_bytes());
            self.out.push(Outgoing::peer(peer, world::P_GOLD_CHANGE, p));
        }
    }

    /// Send a `P_QuestLog` packet to the actor's player, if online. `'N'`/`'U'`
    /// carry `[u8 nameLen][name][u16 statusLen][status]`; `'D'` carries the raw
    /// name (`ScriptingCommands.bb:2211-2213`). The status is a Blitz byte string
    /// (3 flag bytes + description) held as Latin-1 chars; the wire form is each
    /// char's low byte (`quest_status_to_bytes`), so the 3 flag bytes + ASCII
    /// description encode faithfully.
    fn send_quest_log(&mut self, rid: u16, sub: u8, name: &str, status: &str) {
        let Some(peer) = self.world.peer_for_runtime(rid) else {
            return;
        };
        let mut p = vec![sub];
        if sub == b'D' {
            p.extend_from_slice(name.as_bytes());
        } else {
            let nb = name.as_bytes();
            let sb = quest_status_to_bytes(status);
            p.push(nb.len() as u8);
            p.extend_from_slice(nb);
            p.extend_from_slice(&(sb.len() as u16).to_le_bytes());
            p.extend_from_slice(&sb);
        }
        self.out.push(Outgoing::peer(peer, world::P_QUEST_LOG, p));
    }

    /// Broadcast `P_NameChange` for an actor (`SetName`/`SetTag`,
    /// `ScriptingCommands.bb:2566`): `[u16 rid][u8 nameLen][name][tag]` to its
    /// same-area players.
    fn send_name_change(&mut self, rid: u16) {
        let Some((name, tag)) = self.with_char(rid, |r| (r.actor.name.clone(), r.actor.tag.clone())) else {
            return;
        };
        let Some(area) = self.world.session_for_runtime(rid).map(|s| s.area.clone()) else {
            return;
        };
        let mut p = Vec::new();
        p.extend_from_slice(&rid.to_le_bytes());
        p.push(name.len().min(255) as u8);
        p.extend_from_slice(name.as_bytes());
        p.extend_from_slice(tag.as_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area {
                self.out.push(Outgoing::peer(pb, world::P_NAME_CHANGE, p.clone()));
            }
        }
    }

    /// Broadcast a `P_AppearanceUpdate` for one appearance field —
    /// `[sub][u16 rid][u8 value]` to the actor's same-area players. `sub` is the
    /// Blitz field code (`'G'` gender, `'D'` hair/beard, `'F'` face, `'B'` body).
    fn send_appearance(&mut self, rid: u16, sub: u8, value: u8) {
        let Some(area) = self.world.session_for_runtime(rid).map(|s| s.area.clone()) else {
            return;
        };
        let mut p = vec![sub];
        p.extend_from_slice(&rid.to_le_bytes());
        p.push(value);
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area {
                self.out.push(Outgoing::peer(pb, world::P_APPEARANCE_UPDATE, p.clone()));
            }
        }
    }

    /// Read an attribute value (current/max) by name; `None` if the actor isn't
    /// a player or the name is unknown.
    fn attr_value(&self, rid: u16, name: &str, maximum: bool) -> Option<i32> {
        let idx = self.attr_names.index_of(name)?;
        self.with_char(rid, |r| {
            let arr = if maximum { &r.actor.attributes.maximum } else { &r.actor.attributes.value };
            arr.get(idx).copied().unwrap_or(0) as i32
        })
    }

    /// Set an attribute's current value (clamped to `[0, max]`), then broadcast
    /// the `P_StatUpdate "A"` to the actor's same-area peers so clients update.
    /// No-op if the actor isn't a player or the name is unknown.
    fn set_attr(&mut self, rid: u16, name: &str, new_val: i32) {
        let Some(idx) = self.attr_names.index_of(name) else {
            return;
        };
        let clamped = self.with_char_mut(rid, |r| {
            let max = r.actor.attributes.maximum.get(idx).copied().unwrap_or(0) as i32;
            let v = new_val.clamp(0, max.max(0)) as i16;
            if let Some(slot) = r.actor.attributes.value.get_mut(idx) {
                *slot = v;
            }
            v
        });
        if let Some(value) = clamped {
            self.broadcast_stat(rid, idx, value);
        }
    }

    /// Broadcast a `P_StatUpdate "A"` (`[u16 rid][u8 idx][u16 value]`) to every
    /// player sharing the actor's area (mirrors the combat HP-update path).
    fn broadcast_stat(&mut self, rid: u16, idx: usize, value: i16) {
        let Some(area) = self.world.session_for_runtime(rid).map(|s| s.area.clone()) else {
            return;
        };
        let mut a = vec![b'A'];
        a.extend_from_slice(&rid.to_le_bytes());
        a.push(idx as u8);
        a.extend_from_slice(&(value as u16).to_le_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area {
                self.out.push(Outgoing::peer(pb, world::P_STAT_UPDATE, a.clone()));
            }
        }
    }

    /// Set an attribute's MAXIMUM by name (`BVM_SETMAXATTRIBUTE`); broadcast
    /// `P_StatUpdate "M"` (`[u16 rid][u8 idx][u16 max]`) to the actor's same-area
    /// players. No-op for a non-player / unknown name.
    fn set_max_attr(&mut self, rid: u16, name: &str, new_max: i32) {
        let Some(idx) = self.attr_names.index_of(name) else {
            return;
        };
        let applied = self.with_char_mut(rid, |r| {
            let v = new_max.max(0) as i16;
            if let Some(slot) = r.actor.attributes.maximum.get_mut(idx) {
                *slot = v;
            }
            v
        });
        let Some(value) = applied else {
            return;
        };
        let Some(area) = self.world.session_for_runtime(rid).map(|s| s.area.clone()) else {
            return;
        };
        let mut m = vec![b'M'];
        m.extend_from_slice(&rid.to_le_bytes());
        m.push(idx as u8);
        m.extend_from_slice(&(value as u16).to_le_bytes());
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area {
                self.out.push(Outgoing::peer(pb, world::P_STAT_UPDATE, m.clone()));
            }
        }
    }

    /// Privilege gate for commands that mutate one actor's state: allowed if the
    /// target IS the script's own actor (self), or the script is privileged
    /// (`BVM_RequireSelfOrPrivileged`). Blocks a non-priv clicker-driven script
    /// (Examine/Trade/RightClick/ItemScript, where `self.actor` = the clicker)
    /// from mutating a THIRD actor it was handed via a global.
    fn require_self_or_privileged(&self, target_rid: i64) -> bool {
        self.privileged || target_rid == self.actor
    }

    /// The area an actor (player or NPC) is in, for same-area broadcasts.
    fn actor_area(&self, rid: u16) -> Option<String> {
        self.world
            .session_for_runtime(rid)
            .map(|s| s.area.clone())
            .or_else(|| self.spawns.npc(rid).map(|n| n.area.clone()))
    }

    /// Send a packet to every player in `area`.
    fn broadcast_to_area(&mut self, area: &str, msg_type: u8, payload: Vec<u8>) {
        for (pb, sb) in self.world.session_snapshot() {
            if sb.area == area {
                self.out.push(Outgoing::peer(pb, msg_type, payload.clone()));
            }
        }
    }

    fn name_of(&self, rid: u16) -> String {
        if let Some(rec) = self.with_char(rid, |r| r.actor.name.clone()) {
            return rec;
        }
        if let Some(npc) = self.spawns.npc(rid) {
            if let Some(t) = self.catalog.get(npc.actor_id) {
                return t.race.clone();
            }
        }
        String::new()
    }

    /// World position of an actor (player session or NPC).
    fn actor_pos(&self, rid: u16) -> Option<(f32, f32, f32)> {
        if let Some(s) = self.world.session_for_runtime(rid) {
            return Some((s.x, s.y, s.z));
        }
        self.spawns.npc(rid).map(|n| (n.x, n.y, n.z))
    }

    /// Sorted runtime ids of all actors (players + NPCs) in `area` — the iteration
    /// order for `FirstActorInZone`/`NextActorInZone`.
    fn zone_rids(&self, area: &str) -> Vec<u16> {
        let mut v: Vec<u16> = self
            .world
            .session_snapshot()
            .iter()
            .filter(|(_, s)| s.area.eq_ignore_ascii_case(area))
            .map(|(_, s)| s.runtime_id)
            .collect();
        v.extend(self.spawns.all_npcs().filter(|n| n.area.eq_ignore_ascii_case(area)).map(|n| n.runtime_id));
        v.sort_unstable();
        v
    }

    /// `FindActor(name, type)` (`ScriptingCommands.bb:27`): first actor whose name
    /// matches, filtered by type (1 = player, 2 = NPC, 3 = either). Returns its
    /// runtime id, or 0.
    fn find_actor(&self, name: &str, atype: i64) -> i64 {
        if name.is_empty() {
            return 0;
        }
        if atype == 1 || atype == 3 {
            for (_, s) in self.world.session_snapshot() {
                let n = self
                    .accounts
                    .find(&s.user)
                    .and_then(|a| a.characters.get(s.char_slot as usize))
                    .map(|r| r.actor.name.clone())
                    .unwrap_or_default();
                if n.eq_ignore_ascii_case(name) {
                    return s.runtime_id as i64;
                }
            }
        }
        if atype == 2 || atype == 3 {
            for npc in self.spawns.all_npcs() {
                let n = self.catalog.get(npc.actor_id).map(|t| t.race.as_str()).unwrap_or("");
                if n.eq_ignore_ascii_case(name) {
                    return npc.runtime_id as i64;
                }
            }
        }
        0
    }

    /// The actor's template id (player char or NPC).
    fn actor_template_id(&self, rid: u16) -> Option<u16> {
        self.with_char(rid, |r| r.actor.actor_id)
            .or_else(|| self.spawns.npc(rid).map(|n| n.actor_id))
    }

    /// The actor's template race / class string (player char or NPC). `class`
    /// when `class_field`, else `race`.
    fn race_class_of(&self, rid: u16, class_field: bool) -> String {
        let aid = self
            .with_char(rid, |r| r.actor.actor_id)
            .or_else(|| self.spawns.npc(rid).map(|n| n.actor_id));
        aid.and_then(|id| self.catalog.get(id))
            .map(|t| if class_field { t.class.clone() } else { t.race.clone() })
            .unwrap_or_default()
    }

    /// `ACTORGENDER` (`ScriptingCommands.bb:1096`): 3 for a genderless race
    /// (`Genders==3`), else 1 (male, stored 0) / 2 (female, stored ≠0).
    fn actor_gender(&self, rid: u16) -> i64 {
        let Some((gender, aid)) = self.with_char(rid, |r| (r.actor.gender, r.actor.actor_id)) else {
            return 0;
        };
        if gender == 0 {
            if self.catalog.get(aid).map(|t| t.genders == 3).unwrap_or(false) {
                3
            } else {
                1
            }
        } else {
            2
        }
    }

    /// The account `is_dm` flag for a player rid (`PlayerIsGM`/`PlayerIsDM` both
    /// read it; `is_banned` for `PlayerIsBanned`).
    fn account_flag(&self, rid: u16, banned: bool) -> bool {
        let Some((u, _)) = self.player_loc(rid) else {
            return false;
        };
        self.accounts
            .find(&u)
            .map(|a| if banned { a.is_banned } else { a.is_dm })
            .unwrap_or(false)
    }
}

/// Blitz byte-string form of a quest status — Latin-1 low byte per char, so the
/// 3 leading flag bytes (0..255) and an ASCII description encode faithfully.
fn quest_status_to_bytes(s: &str) -> Vec<u8> {
    s.chars().map(|c| c as u32 as u8).collect()
}

/// Build a quest status: 3 flag bytes (`Param4/5/6`, Latin-1) + description.
fn quest_status_string(s1: u8, s2: u8, s3: u8, desc: &str) -> String {
    let mut s = String::with_capacity(3 + desc.len());
    s.push(s1 as char);
    s.push(s2 as char);
    s.push(s3 as char);
    s.push_str(desc);
    s
}

/// The fixed "completed" status (`Chr 255,225,100,254`, `ScriptingCommands.bb:2251`).
fn quest_completed_status() -> String {
    [255u8, 225, 100, 254].iter().map(|&b| b as char).collect()
}

impl Host for ScriptHost<'_> {
    fn call(&mut self, name: &str, args: &[Value]) -> Value {
        if let Some(v) = builtin(name, args) {
            return v;
        }
        let arg_i = |i: usize| args.get(i).map(|v| v.to_int()).unwrap_or(0);
        let arg_s = |i: usize| args.get(i).map(|v| v.to_string_value()).unwrap_or_default();
        let rid0 = || arg_i(0) as u16;

        match name.to_lowercase().as_str() {
            "actor" => Value::Int(self.actor),
            "contextactor" => Value::Int(self.ctx),
            "name" => Value::Str(self.name_of(args.first().map(|v| v.to_int()).unwrap_or(self.actor) as u16)),
            "tag" => Value::Str(self.with_char(rid0(), |r| r.actor.tag.clone()).unwrap_or_default()),
            // Actor reads (ungated): position, identity, account flags, distance.
            "actorx" => Value::Float(self.actor_pos(rid0()).map(|p| p.0 as f64).unwrap_or(0.0)),
            "actory" => Value::Float(self.actor_pos(rid0()).map(|p| p.1 as f64).unwrap_or(0.0)),
            "actorz" => Value::Float(self.actor_pos(rid0()).map(|p| p.2 as f64).unwrap_or(0.0)),
            "actorlevel" => Value::Int(self.with_char(rid0(), |r| r.actor.level as i64).unwrap_or(0)),
            "race" => Value::Str(self.race_class_of(rid0(), false)),
            "class" => Value::Str(self.race_class_of(rid0(), true)),
            "actorgender" => Value::Int(self.actor_gender(rid0())),
            // Appearance reads (1-based on the script side, stored 0-based).
            "actorhair" => Value::Int(self.with_char(rid0(), |r| r.actor.hair as i64 + 1).unwrap_or(0)),
            "actorbeard" => Value::Int(self.with_char(rid0(), |r| r.actor.beard as i64 + 1).unwrap_or(0)),
            "actorface" => Value::Int(self.with_char(rid0(), |r| r.actor.face_tex as i64 + 1).unwrap_or(0)),
            "actorclothes" => Value::Int(self.with_char(rid0(), |r| r.actor.body_tex as i64 + 1).unwrap_or(0)),
            // Appearance setters (privileged — clicker-griefing rebrand). Param2
            // is 1-based; stored 0-based + clamped. Hair/beard only apply to
            // gender 0 (the gendered appearance arrays). Wire matches the Blitz
            // server byte-for-byte (incl. its `"D"`-for-hair quirk).
            "setactorgender" => {
                if self.privileged {
                    let rid = rid0();
                    let mut g = (arg_i(1) - 1).clamp(0, 1) as i16;
                    let genders = self.actor_template_id(rid).and_then(|id| self.catalog.get(id)).map(|t| t.genders).unwrap_or(0);
                    if genders == 2 { g = 1; } else if genders == 1 || genders == 3 { g = 0; }
                    self.with_char_mut(rid, |r| r.actor.gender = g as u8);
                    self.send_appearance(rid, b'G', g as u8);
                }
                Value::Int(0)
            }
            "setactorhair" | "setactorbeard" => {
                if self.privileged {
                    let rid = rid0();
                    let v = (arg_i(1) - 1).clamp(0, 4) as i16;
                    if self.with_char(rid, |r| r.actor.gender == 0).unwrap_or(false) {
                        let beard = name == "setactorbeard";
                        self.with_char_mut(rid, |r| if beard { r.actor.beard = v } else { r.actor.hair = v });
                        self.send_appearance(rid, b'D', v as u8);
                    }
                }
                Value::Int(0)
            }
            "setactorface" => {
                if self.privileged {
                    let rid = rid0();
                    let v = (arg_i(1) - 1).clamp(0, 4) as i16;
                    self.with_char_mut(rid, |r| r.actor.face_tex = v);
                    self.send_appearance(rid, b'F', v as u8);
                }
                Value::Int(0)
            }
            "setactorclothes" => {
                if self.privileged {
                    let rid = rid0();
                    let v = (arg_i(1) - 1).clamp(0, 4) as i16;
                    self.with_char_mut(rid, |r| r.actor.body_tex = v);
                    self.send_appearance(rid, b'B', v as u8);
                }
                Value::Int(0)
            }
            // RunTimeError — Blitz can fatally crash the server; in a headless
            // port we never honour that (it'd be a DoS). Always a safe no-op;
            // the message would be logged in a fuller impl.
            "runtimeerror" => Value::Int(0),
            // ActorID is the actor's TEMPLATE id; ActorIDFromInstance is the same
            // but returns -1 (not 0) for a missing actor (Blitz parity).
            "actorid" => Value::Int(self.actor_template_id(rid0()).unwrap_or(0) as i64),
            "actoridfrominstance" => Value::Int(self.actor_template_id(rid0()).map(|v| v as i64).unwrap_or(-1)),
            // Zone-instance BVMs — the port models one instance (index 0) per
            // area. ActorZoneInstance → 0; Create/Remove are no-ops returning the
            // base instance (multi-instance zones unsupported — noted in PARITY).
            "actorzoneinstance" => Value::Int(0),
            "createzoneinstance" => Value::Int(0),
            "removezoneinstance" => Value::Int(0),
            "actoraggressiveness" => Value::Int(
                self.actor_template_id(rid0())
                    .and_then(|id| self.catalog.get(id))
                    .map(|t| t.aggressiveness as i64)
                    .unwrap_or(0),
            ),
            "actorxpmultiplier" => Value::Int(
                self.actor_template_id(rid0())
                    .and_then(|id| self.catalog.get(id))
                    .map(|t| t.xp_multiplier as i64)
                    .unwrap_or(0),
            ),
            // Movement destination (player session).
            "actordestinationx" => Value::Float(
                self.world.session_for_runtime(rid0()).map(|s| s.dest_x as f64).unwrap_or(0.0),
            ),
            "actordestinationz" => Value::Float(
                self.world.session_for_runtime(rid0()).map(|s| s.dest_z as f64).unwrap_or(0.0),
            ),
            // PlayerInGame: 1 if the actor is a live player.
            "playeringame" => Value::Int(if self.world.session_for_runtime(rid0()).is_some() { 1 } else { 0 }),
            // ActorMount / ActorRider — the port has no mount subsystem (there is
            // no BVM or packet to mount, so no actor is ever mounted); the reads
            // are 0, which is faithful for the only reachable (unmounted) state.
            "actormount" | "actorrider" => Value::Int(0),
            "playeraccountname" => Value::Str(
                self.player_loc(rid0()).map(|(u, _)| u).unwrap_or_default(),
            ),
            "playeraccountemail" => Value::Str(
                self.player_loc(rid0())
                    .and_then(|(u, _)| self.accounts.find(&u).map(|a| a.email.clone()))
                    .unwrap_or_default(),
            ),
            // ActorIsHuman: 1 if the actor is a player (has a session).
            "actorishuman" => Value::Int(if self.world.session_for_runtime(rid0()).is_some() { 1 } else { 0 }),
            "playerisdm" | "playerisgm" => Value::Int(if self.account_flag(rid0(), false) { 1 } else { 0 }),
            "playerisbanned" => Value::Int(if self.account_flag(rid0(), true) { 1 } else { 0 }),
            // ActorDistance(a, b): 3D distance between two actors (0 if either gone).
            "actordistance" => {
                let (a, b) = (self.actor_pos(rid0()), self.actor_pos(arg_i(1) as u16));
                match (a, b) {
                    (Some(p), Some(q)) => {
                        let (dx, dy, dz) = (p.0 - q.0, p.1 - q.1, p.2 - q.2);
                        Value::Float((dx * dx + dy * dy + dz * dz).sqrt() as f64)
                    }
                    _ => Value::Float(0.0),
                }
            }
            // PlayersInZone(name) / ActorsInZone(name) — population counts (the
            // latter adds NPCs). Case-insensitive zone name.
            "playersinzone" => {
                let zone = arg_s(0);
                let n = self
                    .world
                    .session_snapshot()
                    .iter()
                    .filter(|(_, s)| s.area.eq_ignore_ascii_case(&zone))
                    .count();
                Value::Int(n as i64)
            }
            "actorsinzone" => {
                let zone = arg_s(0);
                let players = self
                    .world
                    .session_snapshot()
                    .iter()
                    .filter(|(_, s)| s.area.eq_ignore_ascii_case(&zone))
                    .count();
                let npcs = self.spawns.all_npcs().filter(|n| n.area.eq_ignore_ascii_case(&zone)).count();
                Value::Int((players + npcs) as i64)
            }
            // FindActor(name, type=3) → a matching actor's runtime id.
            "findactor" => {
                let atype = args.get(1).map(|v| v.to_int()).unwrap_or(3);
                let atype = if (1..=3).contains(&atype) { atype } else { 3 };
                Value::Int(self.find_actor(&arg_s(0), atype))
            }
            // FirstActorInZone(zone) / NextActorInZone(rid) — iterate a zone's
            // actors in ascending-runtime-id order.
            "firstactorinzone" => {
                let zone = arg_s(0);
                Value::Int(self.zone_rids(&zone).first().map(|&r| r as i64).unwrap_or(0))
            }
            // NextActor(rid) — the next actor (player or NPC) globally, in
            // ascending-runtime-id order; 0 past the end. (FirstActor = the
            // lowest; callers seed iteration with NextActor(0).)
            "nextactor" => {
                let cur = rid0();
                let mut all: Vec<u16> = self
                    .world
                    .session_snapshot()
                    .iter()
                    .map(|(_, s)| s.runtime_id)
                    .collect();
                all.extend(self.spawns.all_npcs().map(|n| n.runtime_id));
                all.sort_unstable();
                Value::Int(all.into_iter().find(|&r| r > cur).map(|r| r as i64).unwrap_or(0))
            }
            "nextactorinzone" => {
                let cur = rid0();
                let next = self
                    .actor_area(cur)
                    .map(|area| self.zone_rids(&area))
                    .and_then(|rids| {
                        rids.iter().position(|&r| r == cur).and_then(|i| rids.get(i + 1).copied())
                    })
                    .map(|r| r as i64)
                    .unwrap_or(0);
                Value::Int(next)
            }
            // SetName / SetTag — privileged (clicker-griefing rebrand); broadcast
            // P_NameChange. DeQuote strips surrounding quotes on the name.
            "setname" => {
                if self.privileged {
                    let (rid, name) = (rid0(), arg_s(1));
                    let name = name.trim_matches('"').to_string();
                    self.with_char_mut(rid, |r| r.actor.name = name);
                    self.send_name_change(rid);
                }
                Value::Int(0)
            }
            "settag" => {
                if self.privileged {
                    let (rid, tag) = (rid0(), arg_s(1));
                    self.with_char_mut(rid, |r| r.actor.tag = tag);
                    self.send_name_change(rid);
                }
                Value::Int(0)
            }

            // Output(actor, text, r, g, b) → P_ChatMessage `[250][rgb][text]`.
            "output" => {
                let rid = rid0();
                let text = arg_s(1);
                let (r, g, b) = (
                    args.get(2).map(|v| v.to_int()).unwrap_or(255) as u8,
                    args.get(3).map(|v| v.to_int()).unwrap_or(255) as u8,
                    args.get(4).map(|v| v.to_int()).unwrap_or(255) as u8,
                );
                if let Some(peer) = self.world.peer_for_runtime(rid) {
                    let mut p = vec![250u8, r, g, b];
                    p.extend_from_slice(text.as_bytes());
                    self.out.push(Outgoing::peer(peer, world::P_CHAT_MESSAGE, p));
                }
                Value::Int(0)
            }

            // --- Character state reads (ungated). ---
            // `Money` is an alias of `Gold` in Blitz (both read `Actor\Gold`,
            // `ScriptingCommands.bb:2644`).
            "gold" | "money" => Value::Int(self.with_char(rid0(), |r| r.actor.gold as i64).unwrap_or(0)),
            "level" => Value::Int(self.with_char(rid0(), |r| r.actor.level as i64).unwrap_or(0)),
            "xp" | "actorxp" => Value::Int(self.with_char(rid0(), |r| r.actor.xp as i64).unwrap_or(0)),

            // ActorGlobal(actor, idx) — per-character script-global string (quest flags).
            "actorglobal" => {
                let idx = arg_i(1) as usize;
                Value::Str(
                    self.with_char(rid0(), |r| r.actor.script_globals.get(idx).cloned().unwrap_or_default())
                        .unwrap_or_default(),
                )
            }
            // Self-or-privileged (`BVM_SETACTORGLOBAL`): a non-priv script may
            // only write its OWN actor's globals; otherwise a clicker-driven
            // script could overwrite a third player's quest/progression flags.
            "setactorglobal" => {
                if self.require_self_or_privileged(arg_i(0)) {
                    let (rid, idx, val) = (rid0(), arg_i(1) as usize, arg_s(2));
                    self.with_char_mut(rid, |r| {
                        if let Some(g) = r.actor.script_globals.get_mut(idx) {
                            *g = val;
                        }
                    });
                }
                Value::Int(0)
            }

            // --- Quest log (NewQuest/UpdateQuest/CompleteQuest/DeleteQuest +
            // QuestStatus/QuestComplete, ScriptingCommands.bb:2181-2330). Quest
            // entries live on the player's character (r.quests, max 500); each
            // mutation broadcasts a P_QuestLog packet so the client log updates.
            // The status byte-string is `[3 flag bytes][description]`. ---

            // QuestStatus(actor, name) → the description (Blitz `Mid(status, 4)`
            // skips the 3 flag bytes).
            "queststatus" => {
                let qname = arg_s(1);
                let status = self
                    .with_char(rid0(), |r| {
                        r.quests
                            .iter()
                            .find(|q| q.name.eq_ignore_ascii_case(&qname))
                            .map(|q| q.status.clone())
                            .unwrap_or_default()
                    })
                    .unwrap_or_default();
                Value::Str(status.chars().skip(3).collect())
            }
            // QuestComplete(actor, name) → 1 if the status is the completed sentinel.
            "questcomplete" => {
                let qname = arg_s(1);
                let sentinel = quest_completed_status();
                let done = self
                    .with_char(rid0(), |r| {
                        r.quests
                            .iter()
                            .find(|q| q.name.eq_ignore_ascii_case(&qname))
                            .map(|q| q.status == sentinel)
                            .unwrap_or(false)
                    })
                    .unwrap_or(false);
                Value::Int(if done { 1 } else { 0 })
            }
            // NewQuest(actor, name, desc, s1=255, s2=255, s3=255) — add if absent
            // and a slot is free; broadcast P_QuestLog "N". Ungated (Blitz).
            "newquest" => {
                let (rid, name, desc) = (rid0(), arg_s(1), arg_s(2));
                let s1 = args.get(3).map(|v| v.to_int()).unwrap_or(255) as u8;
                let s2 = args.get(4).map(|v| v.to_int()).unwrap_or(255) as u8;
                let s3 = args.get(5).map(|v| v.to_int()).unwrap_or(255) as u8;
                let status = quest_status_string(s1, s2, s3, &desc);
                let added = self
                    .with_char_mut(rid, |r| {
                        if r.quests.iter().any(|q| q.name.eq_ignore_ascii_case(&name)) {
                            return false;
                        }
                        if let Some(q) = r.quests.iter_mut().find(|q| q.name.is_empty()) {
                            q.name = name.clone();
                            q.status = status.clone();
                            true
                        } else {
                            false
                        }
                    })
                    .unwrap_or(false);
                if added {
                    self.send_quest_log(rid, b'N', &name, &status);
                }
                Value::Int(0)
            }
            // UpdateQuest(actor, name, desc, s1=255, s2=255, s3=255) — update an
            // existing entry; broadcast P_QuestLog "U". Ungated.
            "updatequest" => {
                let (rid, name, desc) = (rid0(), arg_s(1), arg_s(2));
                let s1 = args.get(3).map(|v| v.to_int()).unwrap_or(255) as u8;
                let s2 = args.get(4).map(|v| v.to_int()).unwrap_or(255) as u8;
                let s3 = args.get(5).map(|v| v.to_int()).unwrap_or(255) as u8;
                let status = quest_status_string(s1, s2, s3, &desc);
                let updated = self
                    .with_char_mut(rid, |r| {
                        if let Some(q) = r.quests.iter_mut().find(|q| q.name.eq_ignore_ascii_case(&name)) {
                            q.status = status.clone();
                            true
                        } else {
                            false
                        }
                    })
                    .unwrap_or(false);
                if updated {
                    self.send_quest_log(rid, b'U', &name, &status);
                }
                Value::Int(0)
            }
            // CompleteQuest(actor, name) — set the entry to the completed sentinel;
            // broadcast P_QuestLog "U". Ungated.
            "completequest" => {
                let (rid, name) = (rid0(), arg_s(1));
                let status = quest_completed_status();
                let updated = self
                    .with_char_mut(rid, |r| {
                        if let Some(q) = r.quests.iter_mut().find(|q| q.name.eq_ignore_ascii_case(&name)) {
                            q.status = status.clone();
                            true
                        } else {
                            false
                        }
                    })
                    .unwrap_or(false);
                if updated {
                    self.send_quest_log(rid, b'U', &name, &status);
                }
                Value::Int(0)
            }
            // DeleteQuest(actor, name) — wipe the entry; broadcast P_QuestLog "D".
            // PRIVILEGED: an equivalent-effect bypass of the quest mutators (a
            // clicker script could erase a player's progress) — full-priv only.
            "deletequest" => {
                if self.privileged {
                    let (rid, name) = (rid0(), arg_s(1));
                    let deleted = self
                        .with_char_mut(rid, |r| {
                            if let Some(q) = r.quests.iter_mut().find(|q| q.name.eq_ignore_ascii_case(&name)) {
                                q.name.clear();
                                q.status.clear();
                                true
                            } else {
                                false
                            }
                        })
                        .unwrap_or(false);
                    if deleted {
                        self.send_quest_log(rid, b'D', &name, "");
                    }
                }
                Value::Int(0)
            }

            // --- Privileged (gated) commands. ---
            // ChangeGold/ChangeMoney(actor, delta) — mutate the wallet (clamp ≥0)
            // and broadcast P_GoldChange. `ChangeMoney` is an identical alias
            // (`ScriptingCommands.bb:2675`). Privileged: a delta is an
            // equivalent-effect bypass of SetGold, so it shares the gate.
            "changegold" | "changemoney" => {
                if self.privileged {
                    let (rid, delta) = (rid0(), arg_i(1) as i32);
                    self.with_char_mut(rid, |r| {
                        r.actor.gold = (r.actor.gold + delta).max(0);
                    });
                    self.send_gold_change(rid, delta);
                }
                Value::Int(0)
            }
            // SetGold/SetMoney(actor, amount) — absolute wallet set; broadcast the
            // implied delta. Blitz does NOT clamp the absolute set (only Change*
            // clamps), so match it. `SetMoney` is an identical alias
            // (`ScriptingCommands.bb:2696/2714`); both privileged.
            "setgold" | "setmoney" => {
                if self.privileged {
                    let (rid, amount) = (rid0(), arg_i(1) as i32);
                    let change = self.with_char_mut(rid, |r| {
                        let change = amount - r.actor.gold;
                        r.actor.gold = amount;
                        change
                    });
                    if let Some(change) = change {
                        self.send_gold_change(rid, change);
                    }
                }
                Value::Int(0)
            }

            // --- Attributes (Health/Energy/Strength/skills…). ---
            // Reads are ungated; writes (`Set`/`Change`) are privileged — parity
            // with `BVM_SETATTRIBUTE` (a Health write ≤0 is an equivalent-effect
            // bypass of the gated KillActor, so the whole setter is privileged).
            // `Attribute` is the read BVM (`BVM_ATTRIBUTE`); `GetAttribute` is an
            // alias used by some scripts. Both read the current value.
            "attribute" | "getattribute" => {
                Value::Int(self.attr_value(rid0(), &arg_s(1), false).unwrap_or(0) as i64)
            }
            "maxattribute" => Value::Int(self.attr_value(rid0(), &arg_s(1), true).unwrap_or(0) as i64),
            "setattribute" => {
                if self.privileged {
                    let (rid, name, val) = (rid0(), arg_s(1), arg_i(2) as i32);
                    self.set_attr(rid, &name, val);
                }
                Value::Int(0)
            }
            "changeattribute" => {
                if self.privileged {
                    let (rid, name, delta) = (rid0(), arg_s(1), arg_i(2) as i32);
                    let cur = self.attr_value(rid, &name, false).unwrap_or(0);
                    self.set_attr(rid, &name, cur + delta);
                }
                Value::Int(0)
            }
            // Max-attribute writers — privileged (a max-HP/Speed/Energy nerf is a
            // one-shot brick vector; full-priv parity with SETATTRIBUTE).
            "setmaxattribute" => {
                if self.privileged {
                    let (rid, name, val) = (rid0(), arg_s(1), arg_i(2) as i32);
                    self.set_max_attr(rid, &name, val);
                }
                Value::Int(0)
            }
            "changemaxattribute" => {
                if self.privileged {
                    let (rid, name, delta) = (rid0(), arg_s(1), arg_i(2) as i32);
                    let cur = self.attr_value(rid, &name, true).unwrap_or(0);
                    self.set_max_attr(rid, &name, cur + delta);
                }
                Value::Int(0)
            }

            // Reputation read (ungated) + set (privileged — reputation gates
            // vendor/quest/zone access, a brick vector; full-priv parity).
            "reputation" => Value::Int(self.with_char(rid0(), |r| r.actor.reputation as i64).unwrap_or(0)),
            "setreputation" => {
                if self.privileged {
                    let (rid, val) = (rid0(), arg_i(1) as i16);
                    self.with_char_mut(rid, |r| r.actor.reputation = val);
                }
                Value::Int(0)
            }

            // Rand(lo, hi) — inclusive (Blitz `Rand`). Ungated.
            "rand" => Value::Int(self.rng.range(arg_i(0) as i32, arg_i(1) as i32) as i64),

            // AnimateActor(actor, anim, speed, loop=0) → P_AnimateActor
            // `[u16 rid][u8 loop][f32 speed][anim]` to area. Cosmetic, ungated.
            "animateactor" => {
                let rid = rid0();
                if let Some(area) = self.actor_area(rid) {
                    let anim = arg_s(1);
                    let speed = args.get(2).map(|v| v.to_float() as f32).unwrap_or(0.0);
                    let speed = if speed > -1.0e9 && speed < 1.0e9 { speed } else { 0.0 };
                    let loopf = arg_i(3) as u8;
                    let mut p = Vec::new();
                    p.extend_from_slice(&rid.to_le_bytes());
                    p.push(loopf);
                    p.extend_from_slice(&speed.to_le_bytes());
                    p.extend_from_slice(anim.as_bytes());
                    self.broadcast_to_area(&area, world::P_ANIMATE_ACTOR, p);
                }
                Value::Int(0)
            }

            // CreateFloatingNumber(actor, amount, r=255, g=255, b=255) →
            // P_FloatingNumber `[u16 rid][i32 amount][u8 r][u8 g][u8 b]` to area.
            "createfloatingnumber" => {
                let rid = rid0();
                if let Some(area) = self.actor_area(rid) {
                    let amount = arg_i(1) as i32;
                    let r = args.get(2).map(|v| v.to_int()).unwrap_or(255) as u8;
                    let g = args.get(3).map(|v| v.to_int()).unwrap_or(255) as u8;
                    let b = args.get(4).map(|v| v.to_int()).unwrap_or(255) as u8;
                    let mut p = Vec::new();
                    p.extend_from_slice(&rid.to_le_bytes());
                    p.extend_from_slice(&amount.to_le_bytes());
                    p.push(r);
                    p.push(g);
                    p.push(b);
                    self.broadcast_to_area(&area, world::P_FLOATING_NUMBER, p);
                }
                Value::Int(0)
            }

            // PlayMusic(actor, musicId, toAll=0) → P_Music `[u16 id]` (area or
            // just the actor). PlaySpeech(actor, speechId=0) → P_Speech
            // `[u16 id][u16 rid]` (always to area). Cosmetic, ungated.
            "playmusic" => {
                let rid = rid0();
                let id = arg_i(1) as u16;
                let to_all = arg_i(2) != 0;
                let p = id.to_le_bytes().to_vec();
                if to_all {
                    if let Some(area) = self.actor_area(rid) {
                        self.broadcast_to_area(&area, world::P_MUSIC, p);
                    }
                } else if let Some(peer) = self.world.peer_for_runtime(rid) {
                    self.out.push(Outgoing::peer(peer, world::P_MUSIC, p));
                }
                Value::Int(0)
            }
            // BubbleOutput(actor, text, r=255,g=255,b=255) → P_BubbleMessage
            // `[u16 rid][r][g][b][text]` to the area. Cosmetic, ungated.
            "bubbleoutput" => {
                let rid = rid0();
                if let Some(area) = self.actor_area(rid) {
                    let text = arg_s(1);
                    let r = args.get(2).map(|v| v.to_int()).unwrap_or(255) as u8;
                    let g = args.get(3).map(|v| v.to_int()).unwrap_or(255) as u8;
                    let b = args.get(4).map(|v| v.to_int()).unwrap_or(255) as u8;
                    let mut p = Vec::new();
                    p.extend_from_slice(&rid.to_le_bytes());
                    p.push(r);
                    p.push(g);
                    p.push(b);
                    p.extend_from_slice(text.as_bytes());
                    self.broadcast_to_area(&area, world::P_BUBBLE_MESSAGE, p);
                }
                Value::Int(0)
            }
            // ScreenFlash(actor, r, g, b, alpha, length, texId=0) → P_ScreenFlash
            // `[r][g][b][alpha][i32 length][u16 texId]` to the owner only.
            "screenflash" => {
                let rid = rid0();
                if let Some(peer) = self.world.peer_for_runtime(rid) {
                    let r = arg_i(1) as u8;
                    let g = arg_i(2) as u8;
                    let b = arg_i(3) as u8;
                    let alpha = arg_i(4) as u8;
                    let length = (arg_i(5).clamp(0, 30_000)) as i32;
                    let tex = args.get(6).map(|v| v.to_int()).unwrap_or(0) as u16;
                    let mut p = vec![r, g, b, alpha];
                    p.extend_from_slice(&length.to_le_bytes());
                    p.extend_from_slice(&tex.to_le_bytes());
                    self.out.push(Outgoing::peer(peer, world::P_SCREEN_FLASH, p));
                }
                Value::Int(0)
            }
            // UpdateXPBar(actor, level) — set + notify the owner (P_XPUpdate "B").
            "updatexpbar" => {
                let rid = rid0();
                let level = arg_i(1) as u8;
                self.with_char_mut(rid, |r| r.actor.xp_bar_level = level);
                if let Some(peer) = self.world.peer_for_runtime(rid) {
                    self.out.push(Outgoing::peer(peer, world::P_XP_UPDATE, vec![b'B', level]));
                }
                Value::Int(0)
            }
            // BackpackCount(actor, num) — amount stacked in backpack slot.
            // Blitz is 1-based: `Num = Param2 - 1`, slot = SlotI_Backpack + Num
            // (BVM_BACKPACKCOUNT, ScriptingCommands.bb:1286). num=1 → slot 14.
            "backpackcount" => {
                let n = arg_i(1) - 1;
                if n < 0 {
                    return Value::Int(0);
                }
                let slot = 14 + n as usize;
                Value::Int(
                    self.with_char(rid0(), |r| {
                        r.actor.inventory.get(slot).map(|sl| sl.amount as i64).unwrap_or(0)
                    })
                    .unwrap_or(0),
                )
            }
            // DeQuote(s) — strip all double-quotes (BVM_DEQUOTE: Replace(s, Chr$(34), "")).
            "dequote" => Value::Str(arg_s(0).replace('"', "")),
            // FullTrim(s) — strip leading + trailing whitespace.
            "fulltrim" => Value::Str(arg_s(0).trim().to_string()),
            // Split(s, num, delim=",") — return the num-th (1-based) field, or "".
            "split" => {
                let s = arg_s(0);
                let n = arg_i(1);
                let delim = {
                    let d = arg_s(2);
                    if d.is_empty() { ",".to_string() } else { d }
                };
                let field = if n >= 1 {
                    s.split(delim.as_str()).nth((n - 1) as usize).unwrap_or("").to_string()
                } else {
                    String::new()
                };
                Value::Str(field)
            }
            // ScriptPathIsSafe(name) — path-traversal guard (BVM_ScriptPathIsSafe):
            // reject empty, "..", leading slash, drive-colon, control bytes.
            "scriptpathissafe" => {
                let name = arg_s(0);
                let unsafe_path = name.is_empty()
                    || name.contains("..")
                    || name.starts_with('\\')
                    || name.starts_with('/')
                    || (name.len() >= 2 && name.as_bytes()[1] == b':')
                    || name.bytes().any(|c| c < 32 || c == 127);
                Value::Int((!unsafe_path) as i64)
            }
            // GoTo / GoToIf — deliberately unsupported in Blitz too (BVM_GOTO /
            // BVM_GOTOIF just log "no longer supported"); faithful no-op.
            "goto" | "gotoif" => Value::Int(0),
            "playspeech" => {
                let rid = rid0();
                if let Some(area) = self.actor_area(rid) {
                    let id = arg_i(1) as u16;
                    let mut p = Vec::new();
                    p.extend_from_slice(&id.to_le_bytes());
                    p.extend_from_slice(&rid.to_le_bytes());
                    self.broadcast_to_area(&area, world::P_SPEECH, p);
                }
                Value::Int(0)
            }

            // PlaySound(actor, soundId, toAll) → P_Sound `[u16 soundId][u16 rid]`,
            // to the area when toAll, else just the actor's own client.
            "playsound" => {
                let rid = rid0();
                let sound_id = arg_i(1) as u16;
                let to_all = arg_i(2) != 0;
                let mut p = Vec::new();
                p.extend_from_slice(&sound_id.to_le_bytes());
                p.extend_from_slice(&rid.to_le_bytes());
                if to_all {
                    if let Some(area) = self.actor_area(rid) {
                        self.broadcast_to_area(&area, world::P_SOUND, p);
                    }
                } else if let Some(peer) = self.world.peer_for_runtime(rid) {
                    self.out.push(Outgoing::peer(peer, world::P_SOUND, p));
                }
                Value::Int(0)
            }

            // CreateEmitter(actor, name, texId, time, x, y, z) → P_CreateEmitter
            // `[u16 texId][i32 time][u16 rid][f32 x][f32 y][f32 z][name]` to area.
            "createemitter" => {
                let rid = rid0();
                if let Some(area) = self.actor_area(rid) {
                    let name = arg_s(1);
                    let tex_id = arg_i(2) as u16;
                    let time = (arg_i(3).clamp(0, 60_000)) as i32;
                    let fget = |i: usize| args.get(i).map(|v| v.to_float() as f32).unwrap_or(0.0);
                    let mut p = Vec::new();
                    p.extend_from_slice(&tex_id.to_le_bytes());
                    p.extend_from_slice(&time.to_le_bytes());
                    p.extend_from_slice(&rid.to_le_bytes());
                    p.extend_from_slice(&fget(4).to_le_bytes());
                    p.extend_from_slice(&fget(5).to_le_bytes());
                    p.extend_from_slice(&fget(6).to_le_bytes());
                    p.extend_from_slice(name.as_bytes());
                    self.broadcast_to_area(&area, world::P_CREATE_EMITTER, p);
                }
                Value::Int(0)
            }

            // PlayParticleEffect — still a cosmetic no-op (no shipped caller).
            "playparticleeffect" => Value::Int(0),

            // --- Dialog + wait plumbing. ---
            // RC_Core resolves an actor to its RNID (we use the runtime id as
            // both the script handle and the send key) and the context actor's
            // runtime id; for our model these are pass-throughs.
            "getrnid" | "getruntimeid" => Value::Int(arg_i(0)),
            // The script-instance handle (used as the dialog handle on the wire).
            "hsi" => Value::Int(self.actor),
            "setwaiting" | "scriptlog" => Value::Int(0),
            // The async result. Empty = "still waiting" — the suspend/resume
            // model (next phase) fills this from the player's P_Dialog response.
            "getwaitresult" => Value::Str(String::new()),

            // Dialog packet senders → P_Dialog (the dialog window/text/options).
            "rce_sendopendialog" => {
                // (host, arnid, ctxRuntimeId, bgTexId, title)
                let arnid = arg_i(1) as u16;
                if let Some(peer) = self.world.peer_for_runtime(arnid) {
                    let p = dialog::open(self.actor as u32, arg_i(2) as u16, arg_i(3) as u16, &arg_s(4));
                    self.out.push(Outgoing::peer(peer, world::P_DIALOG, p));
                }
                Value::Int(0)
            }
            "rce_senddialogoutput" => {
                // (host, arnid, r, g, b, dhandle, message)
                let arnid = arg_i(1) as u16;
                if let Some(peer) = self.world.peer_for_runtime(arnid) {
                    let p = dialog::output(
                        arg_i(2) as u8, arg_i(3) as u8, arg_i(4) as u8,
                        arg_i(5) as u32, &arg_s(6),
                    );
                    self.out.push(Outgoing::peer(peer, world::P_DIALOG, p));
                }
                Value::Int(0)
            }
            "rce_sendclosedialog" => {
                let arnid = arg_i(1) as u16;
                if let Some(peer) = self.world.peer_for_runtime(arnid) {
                    self.out.push(Outgoing::peer(peer, world::P_DIALOG, dialog::close(arg_i(2) as u32)));
                }
                Value::Int(0)
            }
            "rce_senddialoginput" => {
                // (host, arnid, dhandle, options, delim)
                let arnid = arg_i(1) as u16;
                if let Some(peer) = self.world.peer_for_runtime(arnid) {
                    let delim = {
                        let d = arg_s(4);
                        if d.is_empty() { ",".to_string() } else { d }
                    };
                    let opts_s = arg_s(3);
                    let opts: Vec<&str> = opts_s.split(&delim).collect();
                    self.out.push(Outgoing::peer(peer, world::P_DIALOG, dialog::input(arg_i(2) as u32, &opts)));
                }
                Value::Int(0)
            }
            // RCE_SendInput(host, arnid, iType, title, prompt) → P_ScriptInput
            // prompt. The free-text input box the RC_Core `Input` helper opens;
            // the client replies with P_ScriptInput (resumed via the wait). hSI
            // round-trips (the response handler resumes by peer, not handle).
            "rce_sendinput" => {
                let arnid = arg_i(1) as u16;
                if let Some(peer) = self.world.peer_for_runtime(arnid) {
                    let p = dialog::script_input(arnid as u32, arg_i(2) as u8, &arg_s(3), &arg_s(4));
                    self.out.push(Outgoing::peer(peer, world::P_SCRIPT_INPUT, p));
                }
                Value::Int(0)
            }

            // Unknown / not-yet-ported command → 0 (the Blitz invoker default).
            _ => Value::Int(0),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn data_dir() -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../data")
    }

    #[test]
    fn linked_program_merges_rc_core_functions() {
        let dir = data_dir();
        if !dir.join("Server Data/Scripts/RC_Core.rsl").exists() {
            eprintln!("skipping: no RC_Core.rsl");
            return;
        }
        let (reg, _failures) = ScriptRegistry::load(&dir);
        // Default.rsl does `Using "RC_Core.rcm"`, so its linked program must
        // include RC_Core's std-lib helpers (e.g. OpenDialog).
        if reg.get("Default").is_none() || reg.get("RC_Core").is_none() {
            eprintln!("skipping: Default/RC_Core did not parse");
            return;
        }
        let linked = reg.linked_program("Default").unwrap();
        assert!(
            linked.functions.iter().any(|f| f.name.eq_ignore_ascii_case("OpenDialog")),
            "linked Default should include RC_Core's OpenDialog"
        );
        // And the script's own Examine is still present.
        assert!(linked.functions.iter().any(|f| f.name.eq_ignore_ascii_case("Examine")));
    }

    #[test]
    fn dialog_wire_encoders() {
        // open: "N" + hSI(4) + ctx(2) + bg(2) + title.
        let o = dialog::open(7, 0x0102, 0xFFFF, "Hi");
        assert_eq!(o[0], b'N');
        assert_eq!(&o[1..5], &7u32.to_le_bytes());
        assert_eq!(&o[5..7], &0x0102u16.to_le_bytes());
        assert_eq!(&o[7..9], &0xFFFFu16.to_le_bytes());
        assert_eq!(&o[9..], b"Hi");
        // output: "T" + rgb + dhandle(4) + msg.
        let t = dialog::output(10, 20, 30, 7, "txt");
        assert_eq!(&t[0..4], &[b'T', 10, 20, 30]);
        assert_eq!(&t[4..8], &7u32.to_le_bytes());
        assert_eq!(&t[8..], b"txt");
        // input: "O" + dhandle(4) + [len+opt]…
        let i = dialog::input(7, &["Hello", "Close"]);
        assert_eq!(i[0], b'O');
        assert_eq!(&i[1..5], &7u32.to_le_bytes());
        assert_eq!(i[5], 5);
        assert_eq!(&i[6..11], b"Hello");
        assert_eq!(i[11], 5);
        assert_eq!(&i[12..17], b"Close");
        // close: "C" + dhandle(4).
        assert_eq!(dialog::close(7), {
            let mut v = vec![b'C'];
            v.extend_from_slice(&7u32.to_le_bytes());
            v
        });
    }

    #[test]
    fn privileged_allowlist_loads() {
        let dir = data_dir();
        let (reg, _) = ScriptRegistry::load(&dir);
        // The shipped allowlist includes "In-game Commands" (per CLAUDE.md).
        if dir.join("Server Data/Privileged Scripts.dat").exists() {
            assert!(reg.is_privileged("In-game Commands") || reg.is_privileged("Login"));
        }
    }
}
