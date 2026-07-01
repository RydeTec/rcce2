//! `ItemInstance` stream serialization — parity with `WriteItemInstance` /
//! `ReadItemInstance` (`Items.bb:134-176`).
//!
//! A present item is 83 bytes: `[u16 itemId][40× u16 (attrValue+5000)][u8 health]`.
//! An empty slot is the 2-byte sentinel `u16 65535` (`WriteShort 65535`). The
//! `+5000` bias keeps small negative attribute deltas non-negative in the
//! 16-bit field; read subtracts it back.

use crate::blitz_io::{Reader, Writer};

/// Attribute-delta bias applied on the wire/disk (`+5000` / `-5000`).
const ATTR_BIAS: i32 = 5000;
/// Empty-slot sentinel (`WriteShort 65535`).
const NO_ITEM: u16 = 65535;
/// Per-instance attribute count.
pub const ITEM_ATTR_COUNT: usize = 40;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ItemInstance {
    pub item_id: u16,
    /// 40 per-instance attribute deltas (already de-biased).
    pub attr_values: Vec<i16>,
    pub item_health: u8,
}

impl ItemInstance {
    /// A bare instance of `item_id` with zeroed attributes and full health.
    pub fn new(item_id: u16) -> Self {
        Self {
            item_id,
            attr_values: vec![0; ITEM_ATTR_COUNT],
            item_health: 100,
        }
    }

    /// The 83-byte **wire** form (`ItemInstanceToString$`, `Items.bb:73`): little-
    /// endian `[u16 itemId][40× u16 (attrValue+5000)][u8 health]`. Distinct from
    /// the stream form only in that the wire form is always present (no null
    /// sentinel — the caller sends nothing for an empty slot).
    pub fn to_wire_bytes(&self) -> Vec<u8> {
        let mut b = Vec::with_capacity(83);
        b.extend_from_slice(&self.item_id.to_le_bytes());
        for i in 0..ITEM_ATTR_COUNT {
            let v = self.attr_values.get(i).copied().unwrap_or(0) as i32 + ATTR_BIAS;
            b.extend_from_slice(&(v as u16).to_le_bytes());
        }
        b.push(self.item_health);
        b
    }
}

/// Write one inventory item (or empty slot) — `WriteItemInstance`.
pub fn write_item(w: &mut Writer, item: &Option<ItemInstance>) {
    match item {
        None => {
            w.u16(NO_ITEM);
        }
        Some(it) => {
            w.u16(it.item_id);
            for i in 0..ITEM_ATTR_COUNT {
                let v = it.attr_values.get(i).copied().unwrap_or(0) as i32 + ATTR_BIAS;
                w.u16(v as u16);
            }
            w.u8(it.item_health);
        }
    }
}

/// Read one inventory item — `ReadItemInstance`. Returns:
/// - `Some(None)` for an empty slot (sentinel),
/// - `Some(Some(item))` for a present item,
/// - `None` on stream underflow (soft-fail; caller stops).
pub fn read_item(r: &mut Reader) -> Option<Option<ItemInstance>> {
    let id = r.u16()?;
    if id == NO_ITEM {
        return Some(None);
    }
    let mut attr_values = Vec::with_capacity(ITEM_ATTR_COUNT);
    for _ in 0..ITEM_ATTR_COUNT {
        let raw = r.u16()? as i32 - ATTR_BIAS;
        attr_values.push(raw as i16);
    }
    let item_health = r.u8()?;
    Some(Some(ItemInstance {
        item_id: id,
        attr_values,
        item_health,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn present_item_is_83_bytes_and_roundtrips() {
        let mut it = ItemInstance::new(1234);
        it.attr_values[0] = -3;
        it.attr_values[39] = 250;
        it.item_health = 77;

        let mut w = Writer::new();
        write_item(&mut w, &Some(it.clone()));
        let bytes = w.into_bytes();
        assert_eq!(bytes.len(), 2 + 40 * 2 + 1, "present item must be 83 bytes");

        let mut r = Reader::new(&bytes);
        assert_eq!(read_item(&mut r), Some(Some(it)));
        assert!(r.at_end());
    }

    #[test]
    fn empty_slot_is_2_bytes_sentinel() {
        let mut w = Writer::new();
        write_item(&mut w, &None);
        let bytes = w.into_bytes();
        assert_eq!(bytes, vec![0xFF, 0xFF]);
        let mut r = Reader::new(&bytes);
        assert_eq!(read_item(&mut r), Some(None));
    }

    #[test]
    fn truncated_item_is_none() {
        // Non-sentinel id then a truncated attribute block.
        let bytes = [0x01, 0x00, 0x00];
        let mut r = Reader::new(&bytes);
        assert_eq!(read_item(&mut r), None);
    }
}
