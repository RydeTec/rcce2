//! `Animations.dat` parser — the named animation-range table
//! (`Animations.bb::LoadAnimSets`). Each actor references an animation set by
//! id (`Actors.dat` `MAnimationSet`/`FAnimationSet`); the set maps animation
//! names ("Idle", "Walk", "Run", "Attack", …) to `[start,end]` frame ranges in
//! the actor's single packed animation timeline, plus a per-clip speed.
//!
//! Record layout (back-to-back until EOF):
//! `ID:i16 · Name:str · 150 × { Name:str · Start:i16 · End:i16 · Speed:f32 }`.

use std::collections::HashMap;

use crate::reader::{BlitzReader, ReadError};

/// Slots per set in the on-disk table (`AnimName$[149]` → 150 entries).
const CLIPS_PER_SET: usize = 150;

/// One named animation clip: a frame range in the packed timeline.
#[derive(Debug, Clone)]
pub struct AnimClip {
    pub name: String,
    pub start: i32,
    pub end: i32,
    pub speed: f32,
}

/// An animation set: the named clips for one actor animation rig. Only
/// populated (non-empty-name) slots are kept.
#[derive(Debug, Clone, Default)]
pub struct AnimSet {
    pub id: u16,
    pub name: String,
    pub clips: Vec<AnimClip>,
}

impl AnimSet {
    /// Exact (case-insensitive) clip lookup by name.
    pub fn clip(&self, name: &str) -> Option<&AnimClip> {
        self.clips.iter().find(|c| c.name.eq_ignore_ascii_case(name))
    }

    /// First clip whose name contains any of `needles` (case-insensitive),
    /// in `needles` priority order. Lets callers ask for an "idle"/"stand"
    /// clip without knowing the exact label a given set uses.
    pub fn find(&self, needles: &[&str]) -> Option<&AnimClip> {
        for n in needles {
            let nl = n.to_ascii_lowercase();
            if let Some(c) = self
                .clips
                .iter()
                .find(|c| c.name.to_ascii_lowercase().contains(&nl))
            {
                return Some(c);
            }
        }
        None
    }
}

/// All animation sets keyed by id.
#[derive(Debug, Clone, Default)]
pub struct AnimSetCatalog {
    pub sets: HashMap<u16, AnimSet>,
}

impl AnimSetCatalog {
    pub fn parse(data: &[u8]) -> Result<AnimSetCatalog, ReadError> {
        let mut r = BlitzReader::new(data);
        let mut sets = HashMap::new();
        while !r.eof() {
            match parse_set(&mut r) {
                Ok(set) => {
                    sets.insert(set.id, set);
                }
                Err(_) => break, // tail / corruption — stop, keep what parsed
            }
        }
        Ok(AnimSetCatalog { sets })
    }

    pub fn get(&self, id: u16) -> Option<&AnimSet> {
        self.sets.get(&id)
    }
}

fn parse_set(r: &mut BlitzReader) -> Result<AnimSet, ReadError> {
    let id = r.read_short_u()?;
    let name = r.read_string(256)?;
    let mut clips = Vec::new();
    for _ in 0..CLIPS_PER_SET {
        let cname = r.read_string(256)?;
        let start = r.read_short()? as i32;
        let end = r.read_short()? as i32;
        let speed = r.read_float()?;
        if !cname.is_empty() {
            clips.push(AnimClip {
                name: cname,
                start,
                end,
                speed,
            });
        }
    }
    Ok(AnimSet { id, name, clips })
}
