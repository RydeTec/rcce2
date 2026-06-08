//! `Game Data/Fixed Attributes.dat` — the five "fixed" attribute-slot indices a
//! project assigns: which of the 40 attribute slots plays the role of Health,
//! Energy, Breath, Strength, and Speed. The Blitz client reads this at startup
//! (`Client.bb:172-184`) into `HealthStat`/`EnergyStat`/`BreathStat`/
//! `StrengthStat`/`SpeedStat`, and the server reads the same file.
//!
//! These are **project-configurable** — a server owner can put Health on any of
//! the 40 slots. The shipped default project uses `Health=0, Strength=2,
//! Speed=4` (Energy/Breath unselected), so the indices are genuinely
//! non-contiguous and must be read, never assumed to be index 0.
//!
//! Layout: 5 little-endian `u16`, in order Health, Energy, Breath, Strength,
//! Speed. `65535` means "no slot assigned" for that role. A value outside the
//! valid attribute range (`>= 40`) is also treated as unassigned (soft-fail
//! instead of producing an out-of-range index).

use crate::reader::{BlitzReader, ReadError};

/// The five project-assigned attribute-slot indices. `None` = that role has no
/// attribute slot in this project (`65535` on disk, or an out-of-range value).
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct FixedAttributes {
    pub health: Option<u8>,
    pub energy: Option<u8>,
    pub breath: Option<u8>,
    pub strength: Option<u8>,
    pub speed: Option<u8>,
}

impl FixedAttributes {
    /// Attribute slots are indexed `0..ATTR_COUNT`.
    pub const ATTR_COUNT: u16 = 40;

    pub fn parse(data: &[u8]) -> Result<FixedAttributes, ReadError> {
        let mut r = BlitzReader::new(data);
        // 65535 = unassigned; anything outside 0..40 is an invalid slot → None.
        let to_slot = |v: u16| (v < Self::ATTR_COUNT).then_some(v as u8);
        Ok(FixedAttributes {
            health: to_slot(r.read_short_u()?),
            energy: to_slot(r.read_short_u()?),
            breath: to_slot(r.read_short_u()?),
            strength: to_slot(r.read_short_u()?),
            speed: to_slot(r.read_short_u()?),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn bytes(vals: [u16; 5]) -> Vec<u8> {
        vals.iter().flat_map(|v| v.to_le_bytes()).collect()
    }

    #[test]
    fn parses_indices_and_unassigned() {
        // Mirrors the shape of the shipped default project.
        let f = FixedAttributes::parse(&bytes([3, 65535, 65535, 2, 4])).unwrap();
        assert_eq!(f.health, Some(3));
        assert_eq!(f.energy, None); // 65535 → unassigned
        assert_eq!(f.breath, None);
        assert_eq!(f.strength, Some(2));
        assert_eq!(f.speed, Some(4));
    }

    #[test]
    fn default_project_health_is_zero() {
        let f = FixedAttributes::parse(&bytes([0, 65535, 65535, 2, 4])).unwrap();
        assert_eq!(f.health, Some(0));
    }

    #[test]
    fn out_of_range_index_is_unassigned() {
        // >= 40 (but not 65535) is an invalid slot → None, never a bad index.
        let f = FixedAttributes::parse(&bytes([40, 100, 0, 0, 0])).unwrap();
        assert_eq!(f.health, None);
        assert_eq!(f.energy, None);
        assert_eq!(f.breath, Some(0));
    }

    #[test]
    fn truncated_does_not_panic_and_errors() {
        assert!(FixedAttributes::parse(&[0u8; 9]).is_err()); // 9 < 10 bytes
        assert!(FixedAttributes::parse(&[]).is_err());
    }
}
