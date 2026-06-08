//! `P_OpenTrading` (id 35) parsing — the vendor / trade window the server sends
//! when a player opens trade with an NPC or scenery object.
//!
//! Layout (reference parser `ClientNet.bb:631-668`): a 1-byte kind (`'N'` NPC,
//! `'S'` scenery, `'P'` player) then, for N/S, up to 32 offers of
//! `ItemInstance(83) · amount u16 · serverTradeID u32` (89 bytes each). A `'P'`
//! window carries no offers in the open packet (the player fills their side
//! interactively). All little-endian.

use rcce_net::codec::MsgReader;

/// Length of a serialized ItemInstance (`Items.bb:66`).
const ITEM_INSTANCE_LEN: usize = 83;
/// The client caps the vendor table at 32 slots (`TradeItems` is `Dim(31)`).
const MAX_OFFERS: usize = 32;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TradeKind {
    Npc,
    Scenery,
    Player,
}

/// One item a vendor is offering.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TradeOffer {
    pub item_id: u16,
    pub amount: u16,
    /// Server-side trade slot id, echoed back when buying.
    pub server_trade_id: u32,
}

/// An open trade/vendor window.
#[derive(Debug, Clone, PartialEq)]
pub struct TradeWindow {
    pub kind: TradeKind,
    pub offers: Vec<TradeOffer>,
}

impl TradeWindow {
    /// Parse a `P_OpenTrading` body. Returns `None` only on an empty packet.
    pub fn parse(d: &[u8]) -> Option<TradeWindow> {
        let kind = match d.first()? {
            b'N' => TradeKind::Npc,
            b'S' => TradeKind::Scenery,
            _ => TradeKind::Player, // 'P' (or anything else) → player trade
        };
        let mut offers = Vec::new();
        if matches!(kind, TradeKind::Npc | TradeKind::Scenery) {
            let mut r = MsgReader::new(&d[1..]);
            while offers.len() < MAX_OFFERS {
                let Some(item) = r.bytes(ITEM_INSTANCE_LEN) else { break };
                let (Some(amount), Some(server_trade_id)) = (r.u16(), r.u32()) else {
                    break;
                };
                let item_id = u16::from_le_bytes([item[0], item[1]]);
                offers.push(TradeOffer { item_id, amount, server_trade_id });
            }
        }
        Some(TradeWindow { kind, offers })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rcce_net::codec::MsgWriter;

    fn item_instance(id: u16) -> Vec<u8> {
        let mut w = MsgWriter::new();
        w.u16(id);
        for _ in 0..40 {
            w.u16(5000);
        }
        w.u8(100); // health
        w.into_bytes()
    }

    #[test]
    fn parse_npc_offers() {
        let mut w = MsgWriter::new();
        w.raw(b"N");
        w.raw(&item_instance(42)).u16(1).u32(1001); // sword, x1, trade id 1001
        w.raw(&item_instance(7)).u16(99).u32(1002); // potions, x99, id 1002
        let tw = TradeWindow::parse(w.as_slice()).unwrap();
        assert_eq!(tw.kind, TradeKind::Npc);
        assert_eq!(tw.offers.len(), 2);
        assert_eq!(tw.offers[0], TradeOffer { item_id: 42, amount: 1, server_trade_id: 1001 });
        assert_eq!(tw.offers[1], TradeOffer { item_id: 7, amount: 99, server_trade_id: 1002 });
    }

    #[test]
    fn scenery_kind() {
        let mut w = MsgWriter::new();
        w.raw(b"S").raw(&item_instance(5)).u16(2).u32(9);
        let tw = TradeWindow::parse(w.as_slice()).unwrap();
        assert_eq!(tw.kind, TradeKind::Scenery);
        assert_eq!(tw.offers.len(), 1);
    }

    #[test]
    fn player_trade_has_no_offers() {
        let tw = TradeWindow::parse(b"P").unwrap();
        assert_eq!(tw.kind, TradeKind::Player);
        assert!(tw.offers.is_empty());
    }

    #[test]
    fn truncated_offer_stops_cleanly() {
        let mut w = MsgWriter::new();
        w.raw(b"N").raw(&item_instance(1)).u16(1).u32(1); // one good offer
        w.raw(&[0u8; 40]); // a partial second offer (< 89 bytes)
        let tw = TradeWindow::parse(w.as_slice()).unwrap();
        assert_eq!(tw.offers.len(), 1);
    }

    #[test]
    fn empty_packet_is_none() {
        assert!(TradeWindow::parse(&[]).is_none());
    }
}
