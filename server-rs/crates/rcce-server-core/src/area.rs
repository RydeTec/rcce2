//! Server-side area loader — parity with `ServerLoadArea` (`ServerAreas.bb:281`).
//!
//! Reads `Data/Server Data/Areas/<name>.dat`. The full server area carries
//! terrain, scenery, waypoints, spawns, water, and triggers; this parser reads
//! the prefix the **character/spawn** path needs right now — the header and the
//! **portal table** (a new character's start position comes from its race's
//! `StartPortal` in its `StartArea`). The remaining sections (spawns, water,
//! …) are parsed lazily as the world simulation needs them, so `parse` stops
//! cleanly once the portals are read.

use crate::blitz_io::Reader;

const TRIGGER_COUNT: usize = 150;
const WAYPOINT_COUNT: usize = 2000;
const PORTAL_COUNT: usize = 100;
const SPAWN_COUNT: usize = 1000;
const MAX_SCRIPT: u32 = 1024;
const MAX_NAME: u32 = 256;

/// One portal: a named teleport/spawn point with a world position.
#[derive(Clone, Debug, PartialEq)]
pub struct Portal {
    pub name: String,
    pub link_area: String,
    pub link_name: String,
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub size: f32,
    pub yaw: f32,
}

/// One spawn point: which actor (race) spawns, where (waypoint), and the
/// timing/limits (`ServerAreas.bb:328-350`). Scripts are consumed but not yet
/// retained (they feed the BVM engine, a later phase).
#[derive(Clone, Debug, PartialEq)]
pub struct SpawnPoint {
    /// Actor template id to spawn (`< 0` / empty slot means "no spawn").
    pub actor_id: i16,
    /// Index into `waypoints` for the spawn position.
    pub waypoint: i16,
    pub size: f32,
    /// Max concurrent spawns from this point.
    pub max: i16,
    /// Seconds between spawns.
    pub frequency: i16,
    pub range: f32,
    /// Script run when this NPC is right-clicked (`SpawnActorScript$`,
    /// `ServerNet.bb:1485` → `ThreadScript(A2\Script$, "Main", clicker, npc)`).
    pub actor_script: String,
    /// Script run when this NPC dies (`SpawnDeathScript$`).
    pub death_script: String,
}

/// The header + portal table + waypoints + spawn table of a server area.
#[derive(Clone, Debug, PartialEq)]
pub struct Area {
    pub name: String,
    pub weather_chance: [u8; 5],
    pub entry_script: String,
    pub exit_script: String,
    pub pvp: u8,
    pub gravity: i16,
    pub outdoors: u8,
    pub weather_link: String,
    /// Up to 100 portals (unnamed slots have an empty `name`).
    pub portals: Vec<Portal>,
    /// 2000 waypoint positions `[x, y, z]` (spawn + patrol points).
    pub waypoints: Vec<[f32; 3]>,
    /// 2000 waypoint graph edges (parallel to `waypoints`) — drives patrol.
    pub waypoint_graph: Vec<WaypointEdge>,
    /// 1000 spawn-point slots (most are empty — `actor_id < 0`).
    pub spawns: Vec<SpawnPoint>,
    /// `ServerWater` volumes (drowning / damage zones). Empty for older area
    /// files that predate the water section.
    pub waters: Vec<WaterVolume>,
    /// Up to 150 trigger volumes (proximity script-fire). Most have an empty
    /// `script`; kept at their slot index so `LastTrigger` stays stable.
    pub triggers: Vec<TriggerVolume>,
}

/// A proximity trigger (`Server.bb:636`): fires `script`/`method` once when an
/// actor enters its `size`-radius sphere. Empty `script` = inert slot.
#[derive(Clone, Debug, PartialEq, Default)]
pub struct TriggerVolume {
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub size: f32,
    pub script: String,
    pub method: String,
}

/// A `ServerWater` volume (`ServerAreas.bb:39`): an axis-aligned box from
/// `(x, z)` to `(x+width, z+depth)` with its surface at `y`. An actor is
/// underwater when horizontally inside and below `y + 0.5`.
#[derive(Clone, Copy, Debug, PartialEq, Default)]
pub struct WaterVolume {
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub width: f32,
    pub depth: f32,
    pub damage: i16,
    pub damage_type: i16,
}

/// A waypoint's outgoing graph edges (`Next/Prev`) + dwell time, for patrol
/// (`GameServer.bb:896-911`). `next_*`/`prev` are waypoint indices; `< 0` or
/// `> 1999` means "no edge".
#[derive(Clone, Copy, Debug, PartialEq, Default)]
pub struct WaypointEdge {
    pub next_a: i16,
    pub next_b: i16,
    pub prev: i16,
    /// Seconds to pause on arrival (`WaypointPause`).
    pub pause: i32,
}

impl Area {
    /// Parse `name`'s area from raw `.dat` bytes (header through the portal
    /// table). Returns `None` on stream underflow (soft-fail).
    pub fn parse(name: &str, data: &[u8]) -> Option<Area> {
        let mut r = Reader::new(data);

        let mut weather_chance = [0u8; 5];
        for w in weather_chance.iter_mut() {
            *w = r.u8()?;
        }
        let entry_script = r.string(MAX_SCRIPT)?;
        let exit_script = r.string(MAX_SCRIPT)?;
        let pvp = r.u8()?;
        let gravity = r.i16()?;
        let outdoors = r.u8()?;
        let weather_link = r.string(MAX_NAME)?;

        // Triggers: X/Y/Z/Size (4 f32) + script(1024) + method(256). Retained for
        // proximity script-fire; empty-script slots are kept for index stability.
        let mut triggers = Vec::with_capacity(TRIGGER_COUNT);
        for _ in 0..TRIGGER_COUNT {
            let x = r.f32()?;
            let y = r.f32()?;
            let z = r.f32()?;
            let size = r.f32()?;
            let script = r.string(MAX_SCRIPT)?;
            let method = r.string(MAX_NAME)?;
            triggers.push(TriggerVolume { x, y, z, size, script, method });
        }
        // Waypoints — keep [x,y,z] positions + the Next/Prev/pause graph edges.
        let mut waypoints = Vec::with_capacity(WAYPOINT_COUNT);
        let mut waypoint_graph = Vec::with_capacity(WAYPOINT_COUNT);
        for _ in 0..WAYPOINT_COUNT {
            let x = r.f32()?;
            let y = r.f32()?;
            let z = r.f32()?;
            let next_a = r.i16()?;
            let next_b = r.i16()?;
            let prev = r.i16()?;
            let pause = r.i32()?;
            waypoints.push([x, y, z]);
            waypoint_graph.push(WaypointEdge { next_a, next_b, prev, pause });
        }
        // Portals.
        let mut portals = Vec::with_capacity(PORTAL_COUNT);
        for _ in 0..PORTAL_COUNT {
            let name = r.string(MAX_NAME)?;
            let link_area = r.string(MAX_NAME)?;
            let link_name = r.string(MAX_NAME)?;
            let x = r.f32()?;
            let y = r.f32()?;
            let z = r.f32()?;
            let size = r.f32()?;
            let yaw = r.f32()?;
            portals.push(Portal {
                name,
                link_area,
                link_name,
                x,
                y,
                z,
                size,
                yaw,
            });
        }
        // Spawn table: actor + waypoint + size + 3 scripts + max + freq + range.
        let mut spawns = Vec::with_capacity(SPAWN_COUNT);
        for _ in 0..SPAWN_COUNT {
            let actor_id = r.i16()?;
            let mut waypoint = r.i16()?;
            if !(0..=1999).contains(&waypoint) {
                waypoint = 0;
            }
            let size = r.f32()?;
            r.string(MAX_SCRIPT)?; // spawn script (run on spawn — not yet used)
            let actor_script = r.string(MAX_SCRIPT)?; // spawn-actor (right-click) script
            let death_script = r.string(MAX_SCRIPT)?; // spawn-death script
            let max = r.i16()?;
            let frequency = r.i16()?;
            let range = r.f32()?;
            spawns.push(SpawnPoint {
                actor_id,
                waypoint,
                size,
                max,
                frequency,
                range,
                actor_script,
                death_script,
            });
        }

        // Water volumes (after spawns): count + per record [x,y,z,width,depth]
        // (f32) + [damage][damageType] (i16). Tolerant: a missing/short section
        // (older area files) yields no water rather than failing the parse.
        let mut waters = Vec::new();
        if let Some(count) = r.i16() {
            for _ in 0..count.max(0) {
                let (Some(x), Some(y), Some(z), Some(width), Some(depth), Some(damage), Some(damage_type)) =
                    (r.f32(), r.f32(), r.f32(), r.f32(), r.f32(), r.i16(), r.i16())
                else {
                    break;
                };
                waters.push(WaterVolume { x, y, z, width, depth, damage, damage_type });
            }
        }

        Some(Area {
            name: name.to_string(),
            weather_chance,
            entry_script,
            exit_script,
            pvp,
            gravity,
            outdoors,
            weather_link,
            portals,
            waypoints,
            waypoint_graph,
            spawns,
            waters,
            triggers,
        })
    }

    /// Load and parse `<data_dir>/Server Data/Areas/<name>.dat`. Returns `None`
    /// if the file is missing or malformed.
    pub fn load(data_dir: impl AsRef<std::path::Path>, name: &str) -> Option<Area> {
        let path = data_dir
            .as_ref()
            .join("Server Data")
            .join("Areas")
            .join(format!("{name}.dat"));
        let bytes = std::fs::read(path).ok()?;
        Area::parse(name, &bytes)
    }

    /// Find a portal by name (case-insensitive, matching `Upper$` compares).
    pub fn find_portal(&self, portal_name: &str) -> Option<&Portal> {
        self.portals
            .iter()
            .find(|p| p.name.eq_ignore_ascii_case(portal_name))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn data_dir() -> std::path::PathBuf {
        std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../../../data")
    }

    #[test]
    fn parses_real_area_file() {
        let dir = data_dir();
        if !dir.join("Server Data/Areas/Plains.dat").exists() {
            eprintln!("skipping: Plains.dat not present");
            return;
        }
        let area = Area::load(&dir, "Plains").expect("Plains should parse");
        assert_eq!(area.name, "Plains");
        assert_eq!(area.portals.len(), 100);
        assert_eq!(area.waypoints.len(), 2000);
        assert_eq!(area.spawns.len(), 1000);
        // A real edited zone should have at least one active spawn point.
        let active = area.spawns.iter().filter(|s| s.actor_id >= 0).count();
        eprintln!(
            "Plains: {active} active spawn point(s); first = {:?}",
            area.spawns.iter().find(|s| s.actor_id >= 0)
        );
        // At least one named portal should exist in a real, edited zone.
        let named = area.portals.iter().filter(|p| !p.name.is_empty()).count();
        eprintln!("Plains: {named} named portal(s); first named = {:?}",
            area.portals.iter().find(|p| !p.name.is_empty()).map(|p| (&p.name, p.x, p.y, p.z)));
        assert!(named >= 1, "expected at least one named portal in Plains");
    }

    #[test]
    fn truncated_area_is_none() {
        assert!(Area::parse("x", &[0u8; 10]).is_none());
    }

    #[test]
    fn find_portal_is_case_insensitive() {
        let area = Area {
            name: "Z".into(),
            weather_chance: [0; 5],
            entry_script: String::new(),
            exit_script: String::new(),
            pvp: 0,
            gravity: 0,
            outdoors: 1,
            weather_link: String::new(),
            portals: vec![Portal {
                name: "Start".into(),
                link_area: String::new(),
                link_name: String::new(),
                x: 1.0,
                y: 2.0,
                z: 3.0,
                size: 4.0,
                yaw: 0.0,
            }],
            waypoints: vec![],
            waypoint_graph: vec![],
            spawns: vec![],
            waters: vec![],
            triggers: vec![],
        };
        assert!(area.find_portal("start").is_some());
        assert_eq!(area.find_portal("START").unwrap().x, 1.0);
        assert!(area.find_portal("nope").is_none());
    }
}
