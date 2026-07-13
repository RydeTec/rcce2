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

/// One item staged in a player↔player trade.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PlayerTradeSlot {
    /// Trade slot id. For the partner's side this is the `slot` byte the server
    /// sends in `P_UpdateTrading` (`ServerTradeID`); for my side it is my backpack
    /// slot index.
    pub slot: u8,
    pub item_id: u16,
    pub amount: u16,
}

/// Clamp on the gold I add to a player trade — a held `+`/`-` key can't overflow
/// or produce an absurd `TradeCost` display. Blitz itself does not clamp; this is
/// a safety bound only (well beyond any real gold balance).
const COST_LIMIT: i32 = 1_000_000_000;

/// State of an open player↔player trade window. `his` holds the partner's offered
/// items (driven by inbound `P_UpdateTrading`); `mine` holds what I have staged
/// (echoed locally — the server forwards my offers to the partner, not back to me).
/// `cost` is the gold I add to the deal (Blitz `TradeCost`), adjusted locally and
/// sent only in the confirm packet — the engine does not live-sync cost.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct PlayerTrade {
    pub his: Vec<PlayerTradeSlot>,
    pub mine: Vec<PlayerTradeSlot>,
    /// Gold I contribute to the trade (positive) or demand from the partner
    /// (negative). Blitz `TradeCost`, reset to 0 when the window opens.
    pub cost: i32,
}

impl PlayerTrade {
    /// Apply an inbound `P_UpdateTrading` to the partner's side (ClientNet.bb:533):
    /// `slot u8 + amount u16 [+ 83-byte ItemInstance when amount>0]`. `amount==0`
    /// withdraws the offer in that slot; `amount>0` adds/updates it (the item id is
    /// the ItemInstance's leading u16). Malformed or short packets are ignored —
    /// the soft-fail discipline for server-controlled data.
    pub fn apply_his_update(&mut self, d: &[u8]) {
        let mut r = MsgReader::new(d);
        let (Some(slot), Some(amount)) = (r.u8(), r.u16()) else { return };
        if amount == 0 {
            self.his.retain(|o| o.slot != slot);
            return;
        }
        let Some(item) = r.bytes(ITEM_INSTANCE_LEN) else { return };
        let item_id = u16::from_le_bytes([item[0], item[1]]);
        // Upsert by slot: update an existing offer or append a new one. The engine
        // does remove-then-add into the first free slot; an upsert is equivalent
        // for the normal flow and tolerant of a repeated add for the same slot.
        if let Some(o) = self.his.iter_mut().find(|o| o.slot == slot) {
            o.item_id = item_id;
            o.amount = amount;
        } else {
            self.his.push(PlayerTradeSlot { slot, item_id, amount });
        }
    }

    /// Toggle one of my backpack items in/out of my offer (`mine`). Returns the
    /// amount to send in the outbound `P_UpdateTrading` offer: the staged amount
    /// when adding, `0` when withdrawing (the wire convention for "remove this
    /// slot"). The caller pairs this with `net::trade_offer_packet(slot, amount)`.
    pub fn toggle_mine(&mut self, slot: u8, item_id: u16, amount: u16) -> u16 {
        if let Some(pos) = self.mine.iter().position(|o| o.slot == slot) {
            self.mine.remove(pos);
            0
        } else {
            self.mine.push(PlayerTradeSlot { slot, item_id, amount });
            amount
        }
    }

    /// Whether one of my backpack slots is currently staged in my offer.
    pub fn mine_has(&self, slot: u8) -> bool {
        self.mine.iter().any(|o| o.slot == slot)
    }

    /// Adjust the gold I add to the deal (Blitz `TradeCost`; BCostUp/BCostDown at
    /// `Interface3D.bb:2305-2310` step ±1 per click). Positive = I give gold to the
    /// partner, negative = I demand gold from them. Sent as the `TradeCost i32`
    /// prefix of the confirm packet (`net::player_trade_confirm_packet`), never a
    /// live sync — the engine only reconciles cost when both sides confirm.
    /// Saturating + clamped so a held key can't overflow or overflow the display.
    pub fn adjust_cost(&mut self, delta: i32) {
        self.cost = self.cost.saturating_add(delta).clamp(-COST_LIMIT, COST_LIMIT);
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

    /// Build an inbound `P_UpdateTrading` "add" body: slot + amount + ItemInstance.
    fn his_add(slot: u8, amount: u16, item_id: u16) -> Vec<u8> {
        let mut w = MsgWriter::new();
        w.u8(slot).u16(amount).raw(&item_instance(item_id));
        w.into_bytes()
    }

    #[test]
    fn player_trade_add_update_remove() {
        let mut pt = PlayerTrade::default();
        pt.apply_his_update(&his_add(3, 2, 42));
        pt.apply_his_update(&his_add(5, 1, 7));
        assert_eq!(pt.his.len(), 2);
        assert_eq!(pt.his[0], PlayerTradeSlot { slot: 3, item_id: 42, amount: 2 });
        assert_eq!(pt.his[1], PlayerTradeSlot { slot: 5, item_id: 7, amount: 1 });

        // Re-offering the same slot upserts (amount/item updated, no duplicate).
        pt.apply_his_update(&his_add(3, 9, 99));
        assert_eq!(pt.his.len(), 2);
        assert_eq!(pt.his[0], PlayerTradeSlot { slot: 3, item_id: 99, amount: 9 });

        // amount==0 withdraws that slot (no ItemInstance bytes needed).
        let mut remove = MsgWriter::new();
        remove.u8(3).u16(0);
        pt.apply_his_update(remove.as_slice());
        assert_eq!(pt.his.len(), 1);
        assert_eq!(pt.his[0].slot, 5, "slot 3 withdrawn, slot 5 remains");
        // `mine` is untouched by inbound updates (it is staged locally).
        assert!(pt.mine.is_empty());
    }

    #[test]
    fn player_trade_toggle_mine() {
        let mut pt = PlayerTrade::default();
        // Stage backpack slot 14 (item 42, full stack of 5) → send amount 5.
        assert_eq!(pt.toggle_mine(14, 42, 5), 5);
        assert!(pt.mine_has(14));
        assert_eq!(pt.mine.len(), 1);
        assert_eq!(pt.mine[0], PlayerTradeSlot { slot: 14, item_id: 42, amount: 5 });
        // Stage a second slot.
        assert_eq!(pt.toggle_mine(15, 7, 1), 1);
        assert_eq!(pt.mine.len(), 2);
        // Toggling slot 14 again withdraws it → send amount 0, removed from mine.
        assert_eq!(pt.toggle_mine(14, 42, 5), 0);
        assert!(!pt.mine_has(14));
        assert_eq!(pt.mine.len(), 1);
        assert_eq!(pt.mine[0].slot, 15);
        // Inbound (his) is independent of my staging.
        assert!(pt.his.is_empty());
    }

    #[test]
    fn player_trade_cost_adjust() {
        let mut pt = PlayerTrade::default();
        assert_eq!(pt.cost, 0, "fresh player trade starts at 0 gold (TradeCost reset)");
        pt.adjust_cost(1);
        pt.adjust_cost(1);
        assert_eq!(pt.cost, 2, "two BCostUp clicks");
        pt.adjust_cost(-5);
        assert_eq!(pt.cost, -3, "BCostDown past zero → I demand gold from the partner");
        // A held key can't overflow: it saturates at the clamp, no panic.
        pt.adjust_cost(i32::MAX);
        assert_eq!(pt.cost, COST_LIMIT);
        pt.adjust_cost(i32::MIN);
        assert_eq!(pt.cost, -COST_LIMIT);
        // Cost lives on the state that resets each open: a fresh window is 0 again.
        assert_eq!(PlayerTrade::default().cost, 0);
    }

    #[test]
    fn player_trade_unknown_item_id_stored_without_deref() {
        // The soft-fallback for a since-removed / unknown item is split in two: the
        // STATE layer (this test) must record the raw id without ever looking it up,
        // and the RENDER layer falls back to a placeholder name/icon via
        // `AssetStore::item_name` / `item_icon_path` (exercised where those are
        // called, not here). This test pins the state-layer half only: an inbound
        // offer naming an id absent from every item DB is stored verbatim — no
        // lookup, no deref, so the sync cannot panic on a stale/unknown item.
        let mut pt = PlayerTrade::default();
        pt.apply_his_update(&his_add(0, 1, 65535)); // 65535: absent from any item DB
        assert_eq!(pt.his.len(), 1);
        assert_eq!(pt.his[0].item_id, 65535, "unknown id stored verbatim, no lookup");
    }

    #[test]
    fn player_trade_malformed_is_ignored() {
        let mut pt = PlayerTrade::default();
        // Too short for slot+amount.
        pt.apply_his_update(&[0x03]);
        // amount>0 but the ItemInstance is truncated → no offer added (no panic).
        let mut short = MsgWriter::new();
        short.u8(3).u16(2).raw(&[0u8; 10]);
        pt.apply_his_update(short.as_slice());
        assert!(pt.his.is_empty());
    }
}
