//! Faction data — `Factions.dat` (`Actors.bb:1267` `LoadFactions`).
//!
//! The file is 100 faction names (4-byte-LE-length strings, bounded 256) then a
//! 100×100 byte grid of default ratings: `FactionDefaultRatings(from, to)` is
//! how faction `from` rates faction `to` (0 = hostile … ~100 neutral … 200
//! friendly). An NPC's per-actor `FactionRatings[]` row starts as a copy of its
//! home faction's grid row (`Actors.bb:664`), and the combat/aggro code reads
//! `FactionRatings[targetHomeFaction]` — so the grid row `[home][target]` is the
//! authoritative "does `home` consider a `target`-faction actor an enemy" lookup.
//!
//! Soft-fails: a missing/short file yields an all-zero grid (Blitz's `Dim`
//! default), and out-of-range indices clamp to 0 — never panics.

use crate::blitz_io::Reader;

/// Number of factions (`Dim FactionDefaultRatings(99, 99)` → 0..99 = 100).
pub const FACTION_COUNT: usize = 100;

/// The shipped faction grid + names. `ratings[from][to]`.
#[derive(Clone, Debug)]
pub struct FactionData {
    pub names: Vec<String>,
    /// `ratings[from][to]` — how faction `from` rates faction `to`.
    ratings: Vec<u8>,
}

impl Default for FactionData {
    fn default() -> Self {
        Self {
            names: vec![String::new(); FACTION_COUNT],
            ratings: vec![0u8; FACTION_COUNT * FACTION_COUNT],
        }
    }
}

impl FactionData {
    /// Parse `Factions.dat` bytes. A short/truncated stream keeps whatever was
    /// read and leaves the rest at the all-zero default (soft-fail).
    pub fn parse(data: &[u8]) -> Self {
        let mut out = Self::default();
        let mut r = Reader::new(data);
        for slot in out.names.iter_mut() {
            match r.string(256) {
                Some(s) => *slot = s,
                None => return out,
            }
        }
        for cell in out.ratings.iter_mut() {
            match r.u8() {
                Some(b) => *cell = b,
                None => return out,
            }
        }
        out
    }

    /// Load from a path; a missing/unreadable file yields the all-zero default.
    pub fn load(path: impl AsRef<std::path::Path>) -> Self {
        match std::fs::read(path) {
            Ok(bytes) => Self::parse(&bytes),
            Err(_) => Self::default(),
        }
    }

    /// Faction index for a name (case-insensitive), if any.
    pub fn index_of(&self, name: &str) -> Option<usize> {
        self.names.iter().position(|n| n.eq_ignore_ascii_case(name))
    }

    /// Faction name at an index (empty string if out of range / unnamed).
    pub fn name(&self, i: usize) -> &str {
        self.names.get(i).map(String::as_str).unwrap_or("")
    }

    /// `FactionDefaultRatings(from, to)` — how `from` rates `to`. Out-of-range
    /// indices return 0 (hostile), matching the unset `Dim` default.
    pub fn rating(&self, from: u8, to: u8) -> u8 {
        let (f, t) = (from as usize, to as usize);
        if f >= FACTION_COUNT || t >= FACTION_COUNT {
            return 0;
        }
        self.ratings[f * FACTION_COUNT + t]
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::blitz_io::Writer;

    #[test]
    fn parse_roundtrips_names_and_grid() {
        let mut w = Writer::new();
        for i in 0..FACTION_COUNT {
            w.string(if i == 0 { "Townsfolk" } else { "" });
        }
        // grid: rating(from,to) = (from*3 + to) as u8
        for from in 0..FACTION_COUNT {
            for to in 0..FACTION_COUNT {
                w.u8(((from * 3 + to) as u8).min(200));
            }
        }
        let fd = FactionData::parse(&w.into_bytes());
        assert_eq!(fd.names[0], "Townsfolk");
        assert_eq!(fd.rating(0, 0), 0);
        assert_eq!(fd.rating(2, 5), ((2 * 3 + 5) as u8));
        // Out-of-range is hostile-default 0.
        assert_eq!(fd.rating(200, 0), 0);
        assert_eq!(fd.rating(0, 200), 0);
    }

    #[test]
    fn missing_or_short_file_is_all_zero() {
        let fd = FactionData::parse(&[]);
        assert_eq!(fd.rating(3, 7), 0);
        assert_eq!(fd.names.len(), FACTION_COUNT);
    }
}
