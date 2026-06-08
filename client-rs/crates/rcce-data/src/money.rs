//! `Money.dat` — the currency-denomination config the real Blitz client loads
//! (`ClientLoaders.bb` `LoadGraphicsSettings`, the Money block). It names up to
//! four denominations (smallest→largest) and the integer multiplier between
//! each tier, so a flat base-unit amount can be rendered as
//! `"Platinum 12, Gold 34, Silver 56, Copper 78"` exactly like `Client.bb`'s
//! `Money$`.
//!
//! Byte layout (little-endian, same as every other `.dat`):
//! `Money1`(bounded str) `Money2`(str) `Money2x`(u16) `Money3`(str)
//! `Money3x`(u16) `Money4`(str) `Money4x`(u16). The stock file is
//! `Copper, Silver, 100, Gold, 100, Platinum, 100` (46 bytes). A denomination
//! whose name is empty is skipped by the formatter (mirrors `If MoneyN$ <> ""`).

use crate::reader::{BlitzReader, ReadError};

/// Parsed `Money.dat`: four denomination names (1 = smallest) + the three
/// tier multipliers (`Money2x`…`Money4x`). Names may be empty (tier disabled).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MoneyConfig {
    /// Smallest denomination name (e.g. `Copper`); always present.
    pub name1: String,
    pub name2: String,
    pub name3: String,
    pub name4: String,
    /// Base units per `name2` (e.g. 100 Copper = 1 Silver).
    pub mult2: u32,
    /// `name2` units per `name3`.
    pub mult3: u32,
    /// `name3` units per `name4`.
    pub mult4: u32,
}

impl Default for MoneyConfig {
    /// The stock RCCE currency, used when `Money.dat` is absent/unreadable so
    /// the HUD still renders a sensible amount instead of nothing.
    fn default() -> Self {
        MoneyConfig {
            name1: "Copper".into(),
            name2: "Silver".into(),
            name3: "Gold".into(),
            name4: "Platinum".into(),
            mult2: 100,
            mult3: 100,
            mult4: 100,
        }
    }
}

impl MoneyConfig {
    /// Parse `Money.dat` bytes. Bounded string reads (max 64) match
    /// `ReadBoundedString$`; shorts are unsigned u16 LE (`ReadShort`).
    pub fn parse(bytes: &[u8]) -> Result<MoneyConfig, ReadError> {
        let mut r = BlitzReader::new(bytes);
        let name1 = r.read_string(64)?;
        let name2 = r.read_string(64)?;
        let mult2 = r.read_short_u()? as u32;
        let name3 = r.read_string(64)?;
        let mult3 = r.read_short_u()? as u32;
        let name4 = r.read_string(64)?;
        let mult4 = r.read_short_u()? as u32;
        Ok(MoneyConfig { name1, name2, name3, name4, mult2, mult3, mult4 })
    }

    /// Render a flat base-unit `amount` as a denomination string, replicating
    /// `Client.bb`'s `Money$` exactly: each non-empty tier from largest to
    /// smallest emits `"<name> <count>, "`, the smallest emits `"<name> <rem>"`
    /// with no trailing comma. Tiers with an empty name are skipped. Counts can
    /// be zero (the reference does not suppress them).
    pub fn format(&self, amount: i64) -> String {
        let mut amount = amount.max(0);
        let mut out = String::new();
        let (m2, m3, m4) = (self.mult2 as i64, self.mult3 as i64, self.mult4 as i64);
        if !self.name4.is_empty() {
            let div = (m4 * m3 * m2).max(1);
            out.push_str(&format!("{} {}, ", self.name4, amount / div));
            amount %= div;
        }
        if !self.name3.is_empty() {
            let div = (m3 * m2).max(1);
            out.push_str(&format!("{} {}, ", self.name3, amount / div));
            amount %= div;
        }
        if !self.name2.is_empty() {
            let div = m2.max(1);
            out.push_str(&format!("{} {}, ", self.name2, amount / div));
            amount %= div;
        }
        out.push_str(&format!("{} {}", self.name1, amount));
        out
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The stock 46-byte `Money.dat` parses to Copper/Silver/Gold/Platinum @100.
    #[test]
    fn parse_stock_money_dat() {
        let mut b = Vec::new();
        let push_str = |b: &mut Vec<u8>, s: &str| {
            b.extend_from_slice(&(s.len() as u32).to_le_bytes());
            b.extend_from_slice(s.as_bytes());
        };
        push_str(&mut b, "Copper");
        push_str(&mut b, "Silver");
        b.extend_from_slice(&100u16.to_le_bytes());
        push_str(&mut b, "Gold");
        b.extend_from_slice(&100u16.to_le_bytes());
        push_str(&mut b, "Platinum");
        b.extend_from_slice(&100u16.to_le_bytes());
        assert_eq!(b.len(), 46);
        let cfg = MoneyConfig::parse(&b).unwrap();
        assert_eq!(cfg, MoneyConfig::default());
    }

    /// Formatting matches the reference `Money$`: every tier emitted, zeros kept.
    #[test]
    fn format_matches_reference() {
        let cfg = MoneyConfig::default();
        assert_eq!(cfg.format(12_34_56_78), "Platinum 12, Gold 34, Silver 56, Copper 78");
        // Small amounts still emit all tiers (reference does not suppress zeros).
        assert_eq!(cfg.format(5), "Platinum 0, Gold 0, Silver 0, Copper 5");
        assert_eq!(cfg.format(0), "Platinum 0, Gold 0, Silver 0, Copper 0");
        // Negative clamps to zero.
        assert_eq!(cfg.format(-7), "Platinum 0, Gold 0, Silver 0, Copper 0");
        // Exactly one platinum.
        assert_eq!(cfg.format(1_000_000), "Platinum 1, Gold 0, Silver 0, Copper 0");
    }

    /// A disabled top tier (empty name4) is skipped entirely.
    #[test]
    fn empty_tier_skipped() {
        let cfg = MoneyConfig { name4: String::new(), ..MoneyConfig::default() };
        assert_eq!(cfg.format(1_000_000), "Gold 100, Silver 0, Copper 0");
    }
}
