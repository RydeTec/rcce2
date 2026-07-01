//! NPC spawning — the per-area spawn tables (`Server.bb:540+` zone update).
//!
//! Minimal first slice: when an area becomes active (a player is present), its
//! spawn points are populated up to their `max`, each NPC placed at its spawn
//! waypoint. NPCs are **static** for now (AI movement, scripts, respawn-on-death
//! and the frequency timer are follow-ups); the goal here is a *populated* world
//! the player can see.

use std::collections::{HashMap, HashSet};
use std::path::Path;

use rcce_server_core::area::Area;
use rcce_server_core::ActorCatalog;

use crate::world::World;

/// A live non-player actor in the world.
#[derive(Clone, Debug)]
pub struct NpcActor {
    pub runtime_id: u16,
    pub actor_id: u16,
    pub area: String,
    pub x: f32,
    pub y: f32,
    pub z: f32,
    /// Current / max health (from the template's Health attribute slot).
    pub hp: i32,
    pub hp_max: i32,
    /// The peer this NPC is retaliating against (set when attacked), if any.
    pub target_peer: Option<u32>,
    /// Last time this NPC attacked (ms) — its own combat-delay gate.
    pub last_attack_ms: u64,
    /// Script fired when a player right-clicks this NPC (`SpawnActorScript$`),
    /// empty if the spawn has none.
    pub script: String,
    /// Script fired when this NPC dies (`SpawnDeathScript$`).
    pub death_script: String,
    /// Vendor stock `(item_id, amount)` — items this NPC sells via the
    /// `OpenTrading` shop window. Empty for non-vendors.
    pub stock: Vec<(u16, i16)>,
}

/// One spawn slot's live respawn bookkeeping — the Rust analogue of Blitz's
/// `Instances[j]\Spawned[i]` / `SpawnLast[i]` plus the per-slot template/timing
/// (`Server.bb:546-577`). One per populated `(area, spawn-index)` pair.
#[derive(Clone, Debug)]
struct SpawnSlot {
    area: String,
    actor_id: u16,
    waypoint: [f32; 3],
    /// Max concurrent live NPCs from this slot (`SpawnMax`).
    max: i16,
    /// Seconds between (re)spawns once below `max` (`SpawnFrequency`).
    frequency_secs: i16,
    /// Auto-movement radius around the home waypoint (`SpawnRange`). `>= 5.0`
    /// means the NPC wanders freely within this radius (`GameServer.bb:879`);
    /// smaller means waypoint-graph patrol along the `Next/Prev` edges.
    range: f32,
    /// Home waypoint index (`SpawnWaypoint`) — the patrol graph start.
    waypoint_index: i16,
    /// Currently-live count from this slot (`Spawned[i]`).
    spawned: i16,
    /// Last (re)spawn time, ms; `0` means "timer not started" (`SpawnLast[i]`).
    last_spawn_ms: u64,
    script: String,
    death_script: String,
    /// Cached starting HP for spawns from this slot (template Health attr).
    hp: i32,
    hp_max: i32,
}

/// Owns the live NPCs and which areas have been populated.
#[derive(Default)]
pub struct SpawnManager {
    /// area name → parsed area (None = load failed; cached either way).
    area_cache: HashMap<String, Option<Area>>,
    npcs: Vec<NpcActor>,
    populated: HashSet<String>,
    /// Respawn bookkeeping, one entry per populated spawn slot.
    slots: Vec<SpawnSlot>,
    /// Live NPC runtime id → index into `slots` (so death decrements the right
    /// slot's `spawned`). NPCs staged via [`insert_npc`] are absent here.
    npc_source: HashMap<u16, usize>,
    /// Current wander destination per idle NPC (`runtime_id → (x, z)`); a new one
    /// is picked when the NPC arrives (`GameServer.bb:880-881`).
    npc_dest: HashMap<u16, (f32, f32)>,
    /// Current patrol waypoint index per patrol NPC (`runtime_id → waypoint`).
    npc_patrol: HashMap<u16, i16>,
    /// Pet leader per NPC (`pet runtime_id → leader runtime_id`) — the pet follows
    /// that actor (`AI_Pet`).
    npc_leader: HashMap<u16, u16>,
}

impl SpawnManager {
    pub fn new() -> Self {
        Self::default()
    }

    /// Populate an area's NPCs once (idempotent). Runtime ids come from the
    /// shared world pool so they never collide with players. No-op if the area
    /// is already populated or fails to load.
    pub fn ensure_area(
        &mut self,
        area_name: &str,
        data_dir: &Path,
        catalog: &ActorCatalog,
        health_stat: usize,
        world: &mut World,
    ) {
        if self.populated.contains(area_name) {
            return;
        }
        self.populated.insert(area_name.to_string());

        let area = self
            .area_cache
            .entry(area_name.to_string())
            .or_insert_with(|| Area::load(data_dir, area_name));
        // Clone the spawn/waypoint data so the cache borrow ends before we
        // mutate `self.npcs` / `world`.
        let Some(area) = area else {
            return;
        };
        let spawns = area.spawns.clone();
        let waypoints = area.waypoints.clone();

        for sp in &spawns {
            // Empty slot (no actor) or zero cap → nothing spawns here.
            if sp.actor_id < 0 || sp.max <= 0 {
                continue;
            }
            let wp = waypoints
                .get(sp.waypoint as usize)
                .copied()
                .unwrap_or([0.0, 0.0, 0.0]);
            // Starting HP from the race template's Health attribute slot.
            let (hp, hp_max) = catalog
                .get(sp.actor_id as u16)
                .map(|t| {
                    (
                        t.attr_value.get(health_stat).copied().unwrap_or(0) as i32,
                        t.attr_maximum.get(health_stat).copied().unwrap_or(0) as i32,
                    )
                })
                .unwrap_or((1, 1));
            // Register the slot's respawn bookkeeping, then do the initial fill.
            // (Blitz populates gradually over the frequency timer; we fill to
            // `max` up front so a player entering a fresh zone sees a populated
            // world immediately, then the timer maintains the population on
            // death — a superset of the Blitz behaviour, logged in PARITY.md.)
            let slot_idx = self.slots.len();
            self.slots.push(SpawnSlot {
                area: area_name.to_string(),
                actor_id: sp.actor_id as u16,
                waypoint: wp,
                max: sp.max,
                frequency_secs: sp.frequency,
                range: sp.range,
                waypoint_index: sp.waypoint,
                spawned: 0,
                last_spawn_ms: 0,
                script: sp.actor_script.clone(),
                death_script: sp.death_script.clone(),
                hp,
                hp_max,
            });
            for _ in 0..sp.max {
                self.spawn_one(slot_idx, world);
            }
        }
    }

    /// Spawn a single NPC from `slot_idx`: allocate a runtime id, place it at the
    /// slot's waypoint, link it back to the slot (so death decrements the right
    /// counter), and bump the slot's live count. Caller updates `last_spawn_ms`.
    fn spawn_one(&mut self, slot_idx: usize, world: &mut World) {
        let (actor_id, area, wp, hp, hp_max, script, death_script) = {
            let s = &self.slots[slot_idx];
            (
                s.actor_id,
                s.area.clone(),
                s.waypoint,
                s.hp,
                s.hp_max,
                s.script.clone(),
                s.death_script.clone(),
            )
        };
        let runtime_id = world.alloc_runtime();
        self.npcs.push(NpcActor {
            runtime_id,
            actor_id,
            area,
            x: wp[0],
            y: wp[1],
            z: wp[2],
            hp,
            hp_max,
            target_peer: None,
            last_attack_ms: 0,
            script,
            death_script,
            stock: Vec::new(),
        });
        self.npc_source.insert(runtime_id, slot_idx);
        self.slots[slot_idx].spawned += 1;
    }

    /// Per-tick respawn check (`Server.bb:546-577`): for every spawn slot below
    /// its cap, start (or honour) the frequency timer and respawn one NPC once
    /// `frequency` seconds have elapsed since the last spawn; reset the timer
    /// while a slot is full. Newly spawned NPCs are introduced to nearby players
    /// by the next `collect_world_broadcasts` pass. Returns the count spawned.
    pub fn tick_respawn(&mut self, now_ms: u64, world: &mut World) -> usize {
        let mut spawned_now = 0;
        for i in 0..self.slots.len() {
            let (spawned, max, freq, last) = {
                let s = &self.slots[i];
                (s.spawned, s.max, s.frequency_secs, s.last_spawn_ms)
            };
            if spawned < max {
                if last == 0 {
                    // Start the timer (a slot just dropped below cap on a death).
                    self.slots[i].last_spawn_ms = now_ms;
                } else if now_ms.saturating_sub(last) > freq.max(0) as u64 * 1000 {
                    self.spawn_one(i, world);
                    self.slots[i].last_spawn_ms = now_ms;
                    spawned_now += 1;
                }
            } else {
                // Full: hold the timer reset so respawn waits a fresh interval
                // after the next death (Blitz's `Else: SpawnLast[i] = 0`).
                self.slots[i].last_spawn_ms = 0;
            }
        }
        spawned_now
    }

    /// Apply `damage` to the NPC with this runtime id. Returns `Some(new_hp)` if
    /// the NPC exists, else `None`. (Removal of dead NPCs is the caller's job so
    /// it can broadcast the death first.)
    pub fn damage_npc(&mut self, runtime_id: u16, damage: i32) -> Option<i32> {
        let npc = self.npcs.iter_mut().find(|n| n.runtime_id == runtime_id)?;
        npc.hp -= damage;
        Some(npc.hp)
    }

    /// Look up a live NPC by runtime id.
    pub fn npc(&self, runtime_id: u16) -> Option<&NpcActor> {
        self.npcs.iter().find(|n| n.runtime_id == runtime_id)
    }

    /// Change an NPC's template id (`BVM_CHANGEACTOR` morph).
    pub fn set_npc_actor(&mut self, runtime_id: u16, actor_id: u16) {
        if let Some(n) = self.npcs.iter_mut().find(|n| n.runtime_id == runtime_id) {
            n.actor_id = actor_id;
        }
    }

    /// Set an NPC's position absolutely (`BVM_MOVEACTOR`).
    pub fn set_npc_pos(&mut self, runtime_id: u16, x: f32, y: f32, z: f32) {
        if let Some(n) = self.npcs.iter_mut().find(|n| n.runtime_id == runtime_id) {
            n.x = x;
            n.y = y;
            n.z = z;
        }
    }

    /// Set an NPC's current HP absolutely (script `SetAttribute(npc,"Health",x)`).
    pub fn set_npc_hp(&mut self, runtime_id: u16, hp: i32) {
        if let Some(n) = self.npcs.iter_mut().find(|n| n.runtime_id == runtime_id) {
            n.hp = hp;
        }
    }

    /// The peer an NPC is currently targeting, if any (`ActorTarget`).
    pub fn npc_target(&self, runtime_id: u16) -> Option<u32> {
        self.npc(runtime_id).and_then(|n| n.target_peer)
    }

    /// Make an NPC retaliate against a peer (set when the peer attacks it).
    pub fn set_target(&mut self, runtime_id: u16, peer: u32) {
        if let Some(n) = self.npcs.iter_mut().find(|n| n.runtime_id == runtime_id) {
            n.target_peer = Some(peer);
        }
    }

    /// NPCs that are ready to attack their target this tick (have a target and
    /// their own combat delay has elapsed). Returns `(rid, target_peer, actor_id,
    /// area)`; the caller resolves + applies and calls [`mark_attacked`].
    pub fn pending_attacks(&self, now_ms: u64, combat_delay: i64) -> Vec<(u16, u32, u16, String)> {
        self.npcs
            .iter()
            .filter_map(|n| {
                let target = n.target_peer?;
                if (now_ms.saturating_sub(n.last_attack_ms) as i64) >= combat_delay {
                    Some((n.runtime_id, target, n.actor_id, n.area.clone()))
                } else {
                    None
                }
            })
            .collect()
    }

    /// Record that an NPC attacked (resets its combat-delay timer).
    pub fn mark_attacked(&mut self, runtime_id: u16, now_ms: u64) {
        if let Some(n) = self.npcs.iter_mut().find(|n| n.runtime_id == runtime_id) {
            n.last_attack_ms = now_ms;
        }
    }

    /// Step an NPC toward `(tx, tz)` by at most `step` world units, matching
    /// Blitz's L1-normalised move (`GameServer.bb:738-743`): only moves when the
    /// remaining distance exceeds the 0.5-unit deadband. Returns the NPC's new
    /// `(x, z)` and whether it is now within `stop_dist` of the target (so the
    /// caller can stop chasing and start swinging). `None` if the NPC is gone.
    pub fn step_npc_toward(
        &mut self,
        runtime_id: u16,
        tx: f32,
        tz: f32,
        step: f32,
        stop_dist: f32,
    ) -> Option<(f32, f32, bool)> {
        let n = self.npcs.iter_mut().find(|n| n.runtime_id == runtime_id)?;
        let xdist = tx - n.x;
        let zdist = tz - n.z;
        let dist = (xdist * xdist + zdist * zdist).sqrt();
        let in_range = dist <= stop_dist;
        // Blitz moves only when off-target by more than the 0.5 deadband, and
        // never past the stop distance (don't overrun into the target).
        if !in_range && (xdist.abs() > 0.5 || zdist.abs() > 0.5) && step > 0.0 {
            let denom = xdist.abs() + zdist.abs();
            if denom > 0.0 {
                n.x += (xdist / denom) * step;
                n.z += (zdist / denom) * step;
            }
        }
        Some((n.x, n.z, in_range))
    }

    /// Clear an NPC's target (its target left / died).
    pub fn clear_target(&mut self, runtime_id: u16) {
        if let Some(n) = self.npcs.iter_mut().find(|n| n.runtime_id == runtime_id) {
            n.target_peer = None;
        }
    }

    /// Clear every NPC that is targeting `peer` (the player died / left) — parity
    /// with `KillActor`'s `For A2: If A2\AITarget = A Then A2\AITarget = Null`.
    pub fn clear_targets_on_peer(&mut self, peer: u32) {
        for n in self.npcs.iter_mut() {
            if n.target_peer == Some(peer) {
                n.target_peer = None;
            }
        }
    }

    /// Remove a dead NPC; returns it (so the caller can free its runtime id).
    /// Decrements its source spawn slot's live count so the respawn timer
    /// (`tick_respawn`) refills the zone (`GameServer.bb:233`).
    pub fn remove_npc(&mut self, runtime_id: u16) -> Option<NpcActor> {
        let i = self.npcs.iter().position(|n| n.runtime_id == runtime_id)?;
        if let Some(slot_idx) = self.npc_source.remove(&runtime_id) {
            if let Some(slot) = self.slots.get_mut(slot_idx) {
                slot.spawned = (slot.spawned - 1).max(0);
            }
        }
        self.npc_dest.remove(&runtime_id);
        self.npc_patrol.remove(&runtime_id);
        self.npc_leader.remove(&runtime_id);
        // Any pets following the removed actor lose their leader.
        self.npc_leader.retain(|_, &mut l| l != runtime_id);
        Some(self.npcs.swap_remove(i))
    }

    /// Live NPCs in `area`.
    pub fn npcs_in_area<'a>(&'a self, area: &'a str) -> impl Iterator<Item = &'a NpcActor> + 'a {
        self.npcs.iter().filter(move |n| n.area == area)
    }

    /// `(runtime_id, area, actor_id)` for every NPC currently chasing a target —
    /// the work-list for the per-tick chase step.
    pub fn chasers(&self) -> Vec<(u16, String, u16)> {
        self.npcs
            .iter()
            .filter(|n| n.target_peer.is_some())
            .map(|n| (n.runtime_id, n.area.clone(), n.actor_id))
            .collect()
    }

    /// `(runtime_id, area, actor_id, x, z)` for every idle NPC (no target) whose
    /// spawn slot has a wander radius (`SpawnRange >= 5.0`) — the work-list for
    /// the per-tick wander step. NPCs staged via [`insert_npc`] (no slot) and
    /// patrol-only slots are excluded.
    pub fn wanderers(&self) -> Vec<(u16, String, u16, f32, f32)> {
        self.npcs
            .iter()
            .filter(|n| n.target_peer.is_none())
            .filter(|n| {
                self.npc_source
                    .get(&n.runtime_id)
                    .and_then(|&i| self.slots.get(i))
                    .map(|s| s.range >= 5.0)
                    .unwrap_or(false)
            })
            .map(|n| (n.runtime_id, n.area.clone(), n.actor_id, n.x, n.z))
            .collect()
    }

    /// `(runtime_id, area, actor_id, x, y, z)` for every idle NPC (no target) —
    /// the work-list for the per-tick aggro scan.
    pub fn idlers(&self) -> Vec<(u16, String, u16, f32, f32, f32)> {
        self.npcs
            .iter()
            .filter(|n| n.target_peer.is_none())
            .map(|n| (n.runtime_id, n.area.clone(), n.actor_id, n.x, n.y, n.z))
            .collect()
    }

    /// `(runtime_id, area, actor_id, x, z, start_waypoint)` for every idle NPC
    /// whose spawn slot is a **patrol** slot (`SpawnRange < 5.0`) — they follow
    /// the waypoint graph rather than wandering.
    pub fn patrollers(&self) -> Vec<(u16, String, u16, f32, f32, i16)> {
        self.npcs
            .iter()
            .filter(|n| n.target_peer.is_none())
            .filter_map(|n| {
                let slot = self.npc_source.get(&n.runtime_id).and_then(|&i| self.slots.get(i))?;
                if slot.range >= 5.0 {
                    return None;
                }
                Some((n.runtime_id, n.area.clone(), n.actor_id, n.x, n.z, slot.waypoint_index))
            })
            .collect()
    }

    /// This NPC's current patrol waypoint index, if set.
    pub fn patrol_waypoint(&self, rid: u16) -> Option<i16> {
        self.npc_patrol.get(&rid).copied()
    }

    /// Make `npc` a pet of `leader` (`BVM_SETLEADER`): the NPC follows the leader
    /// and stops counting toward its spawn slot (so the zone refills a
    /// replacement, parity with `SourceSP = -1`). `leader == 0` clears it.
    pub fn set_leader(&mut self, npc: u16, leader: u16) {
        if leader == 0 {
            self.npc_leader.remove(&npc);
            return;
        }
        self.npc_leader.insert(npc, leader);
        // Detach from the spawn slot: decrement its live count + forget the link.
        if let Some(slot_idx) = self.npc_source.remove(&npc) {
            if let Some(slot) = self.slots.get_mut(slot_idx) {
                slot.spawned = (slot.spawned - 1).max(0);
            }
        }
    }

    /// This NPC's pet leader, if it has one.
    pub fn npc_leader(&self, npc: u16) -> Option<u16> {
        self.npc_leader.get(&npc).copied()
    }

    /// `(runtime_id, area, actor_id)` for every NPC that is a pet (has a leader).
    pub fn pets(&self) -> Vec<(u16, String, u16)> {
        self.npcs
            .iter()
            .filter(|n| self.npc_leader.contains_key(&n.runtime_id))
            .map(|n| (n.runtime_id, n.area.clone(), n.actor_id))
            .collect()
    }

    /// How many pets follow `leader`.
    pub fn pet_count(&self, leader: u16) -> usize {
        self.npc_leader.values().filter(|&&l| l == leader).count()
    }

    /// Set this NPC's current patrol waypoint index.
    pub fn set_patrol_waypoint(&mut self, rid: u16, wp: i16) {
        self.npc_patrol.insert(rid, wp);
    }

    /// The wander home `(x, z)` and `range` for an NPC's source slot, if it has
    /// one with a wander radius.
    pub fn wander_home(&self, runtime_id: u16) -> Option<(f32, f32, f32)> {
        let slot = self.npc_source.get(&runtime_id).and_then(|&i| self.slots.get(i))?;
        if slot.range < 5.0 {
            return None;
        }
        Some((slot.waypoint[0], slot.waypoint[2], slot.range))
    }

    /// This NPC's current wander destination, if one has been chosen.
    pub fn npc_dest(&self, runtime_id: u16) -> Option<(f32, f32)> {
        self.npc_dest.get(&runtime_id).copied()
    }

    /// Set this NPC's wander destination.
    pub fn set_npc_dest(&mut self, runtime_id: u16, x: f32, z: f32) {
        self.npc_dest.insert(runtime_id, (x, z));
    }

    /// Every live NPC (for name lookup / zone iteration).
    pub fn all_npcs(&self) -> impl Iterator<Item = &NpcActor> + '_ {
        self.npcs.iter()
    }

    pub fn npc_count(&self) -> usize {
        self.npcs.len()
    }

    /// Add a script-spawned NPC (`BVM_SPAWN`). Not linked to a spawn slot, so it
    /// does not count toward respawn — a scripted spawn is one-shot (Blitz spawns
    /// it with `AIMode = AI_Wait` and no `SourceSP`).
    pub fn add_npc(&mut self, npc: NpcActor) {
        self.npcs.push(npc);
    }

    /// Stage a pre-built NPC directly (tests only — avoids needing a full area
    /// file with a scripted spawn slot).
    #[cfg(test)]
    pub fn insert_npc(&mut self, npc: NpcActor) {
        self.npcs.push(npc);
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
    fn populates_real_area_with_npcs() {
        let dir = data_dir();
        if !dir.join("Server Data/Areas/Plains.dat").exists() {
            eprintln!("skipping: Plains.dat absent");
            return;
        }
        let catalog = ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let mut world = World::new();
        let mut mgr = SpawnManager::new();
        mgr.ensure_area("Plains", &dir, &catalog, 0, &mut world);
        let n = mgr.npc_count();
        eprintln!("Plains spawned {n} NPC(s)");
        assert!(n >= 1, "Plains should spawn at least one NPC");
        // NPCs have HP from their template.
        assert!(mgr.npcs_in_area("Plains").all(|a| a.hp_max >= 1));
        // Idempotent: ensuring again spawns no more.
        mgr.ensure_area("Plains", &dir, &catalog, 0, &mut world);
        assert_eq!(mgr.npc_count(), n);
        // Every NPC has a unique runtime id.
        let mut ids: Vec<u16> = mgr.npcs_in_area("Plains").map(|a| a.runtime_id).collect();
        let total = ids.len();
        ids.sort();
        ids.dedup();
        assert_eq!(ids.len(), total, "runtime ids must be unique");
    }

    #[test]
    fn killed_npc_respawns_after_its_frequency_interval() {
        let dir = data_dir();
        if !dir.join("Server Data/Areas/Plains.dat").exists() {
            eprintln!("skipping: Plains.dat absent");
            return;
        }
        let catalog = ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let mut world = World::new();
        let mut mgr = SpawnManager::new();
        mgr.ensure_area("Plains", &dir, &catalog, 0, &mut world);
        let full = mgr.npc_count();
        if full == 0 {
            eprintln!("skipping: Plains spawned no NPCs");
            return;
        }
        // Kill one NPC: the zone is now below cap.
        let victim = mgr.npcs_in_area("Plains").next().unwrap().runtime_id;
        mgr.remove_npc(victim);
        assert_eq!(mgr.npc_count(), full - 1, "removal drops the live count");

        // The frequency timer for the freed slot: the first tick only *starts*
        // the timer (Blitz `SpawnLast = MilliSecs()`), it does not yet respawn.
        // (Use a nonzero clock — `0` is the "timer not started" sentinel.)
        let spawned = mgr.tick_respawn(1_000, &mut world);
        assert_eq!(spawned, 0, "first tick starts the timer, no respawn yet");
        assert_eq!(mgr.npc_count(), full - 1);

        // Well past any sane frequency*1000 → exactly one NPC respawns, back to
        // cap. A second far-future tick spawns nothing more (slot is full).
        let big = 10_000_000u64;
        let spawned2 = mgr.tick_respawn(big, &mut world);
        assert!(spawned2 >= 1, "respawn fires after the interval");
        assert_eq!(mgr.npc_count(), full, "zone refilled to its cap");
        let spawned3 = mgr.tick_respawn(big + big, &mut world);
        assert_eq!(spawned3, 0, "a full slot does not over-spawn");
        assert_eq!(mgr.npc_count(), full);
    }

    #[test]
    fn missing_area_spawns_nothing() {
        let catalog = ActorCatalog::default();
        let mut world = World::new();
        let mut mgr = SpawnManager::new();
        mgr.ensure_area("NoSuchZone", &data_dir(), &catalog, 0, &mut world);
        assert_eq!(mgr.npc_count(), 0);
    }
}
