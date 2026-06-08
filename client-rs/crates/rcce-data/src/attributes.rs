//! `Attributes.dat` (Server Data) — the project's 40 attribute slot names plus
//! skill / hidden flags, loaded by `Actors.bb` `LoadAttributes`. The slot *names*
//! live here; which slot plays a role like Health/Energy/Strength/Speed is
//! project-configurable and read separately from `Game Data/Fixed Attributes.dat`
//! (see [`crate::fixed_attributes`]) — do NOT assume Health is index 0.
//!
//! Layout: `AttributeAssignment` (u8), then 40 records of `Name`
//! (4-byte-length-prefixed file string) + `IsSkill` (u8) + `Hidden` (u8).

use crate::reader::{BlitzReader, ReadError};

/// One attribute slot: its display name and flags.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct AttributeDef {
    pub name: String,
    pub is_skill: bool,
    pub hidden: bool,
}

/// The parsed `Attributes.dat` (40 slots).
#[derive(Debug, Clone, Default)]
pub struct AttributeNames {
    pub assignment: u8,
    pub attrs: Vec<AttributeDef>,
}

impl AttributeNames {
    pub const COUNT: usize = 40;

    pub fn parse(data: &[u8]) -> Result<AttributeNames, ReadError> {
        let mut r = BlitzReader::new(data);
        let assignment = r.read_byte()?;
        let mut attrs = Vec::with_capacity(Self::COUNT);
        for _ in 0..Self::COUNT {
            let name = r.read_string(256)?;
            let is_skill = r.read_byte()? != 0;
            let hidden = r.read_byte()? != 0;
            attrs.push(AttributeDef { name, is_skill, hidden });
        }
        Ok(AttributeNames { assignment, attrs })
    }

    /// Display name for slot `i`, or `None` if out of range / empty.
    pub fn name(&self, i: usize) -> Option<&str> {
        self.attrs.get(i).map(|a| a.name.as_str()).filter(|s| !s.is_empty())
    }

    /// Whether slot `i` is flagged hidden (don't show in the character panel).
    pub fn hidden(&self, i: usize) -> bool {
        self.attrs.get(i).map(|a| a.hidden).unwrap_or(true)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn put_str(out: &mut Vec<u8>, s: &str) {
        out.extend_from_slice(&(s.len() as u32).to_le_bytes());
        out.extend_from_slice(s.as_bytes());
    }

    #[test]
    fn parses_named_and_flags() {
        let mut data = vec![0u8]; // assignment
        let names = ["Health", "Energy", "Strength"];
        for i in 0..AttributeNames::COUNT {
            put_str(&mut data, names.get(i).copied().unwrap_or(""));
            data.push(if i == 2 { 1 } else { 0 }); // Strength is a skill
            data.push(if i == 5 { 1 } else { 0 }); // slot 5 hidden
        }
        let a = AttributeNames::parse(&data).expect("parse");
        assert_eq!(a.attrs.len(), 40);
        assert_eq!(a.name(0), Some("Health"));
        assert_eq!(a.name(2), Some("Strength"));
        assert!(a.attrs[2].is_skill);
        assert_eq!(a.name(3), None); // empty slot
        assert!(a.hidden(5));
    }
}
