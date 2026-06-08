//! Client→server packet builders (the send side).

use rcce_net::codec::MsgWriter;

/// Build a `P_StandardUpdate` movement payload (the client send side,
/// `ClientNet.bb:1801`): `DestX, DestZ, Y, X, Z` (f32 LE) then `IsRunning,
/// WalkingBackward` (u8). The server (ServerNet.bb:1796) trusts the claimed
/// X/Z within a speed limit and moves the actor toward Dest. Sent unreliable
/// (`RCE_Send` defaults `ReliableFlag = 0`).
pub fn movement_packet(
    dest_x: f32,
    dest_z: f32,
    y: f32,
    x: f32,
    z: f32,
    running: bool,
    walking_backward: bool,
) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.f32(dest_x)
        .f32(dest_z)
        .f32(y)
        .f32(x)
        .f32(z)
        .u8(running as u8)
        .u8(walking_backward as u8);
    w.into_bytes()
}

/// Build a `P_SpellUpdate` cast request (`Interface3D.bb:1121-1128`): `"F"` +
/// spell id (u16) + an optional target RuntimeID (u16). The target bytes are
/// omitted when there's no target — the server tolerates a missing target
/// rather than the client sending a stale handle. Sent reliable.
pub fn cast_packet(spell_id: u16, target: Option<u16>) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(b'F').u16(spell_id);
    if let Some(rid) = target {
        w.u16(rid);
    }
    w.into_bytes()
}

/// `P_SpellUpdate "M"` (ServerNet.bb:1143) — request to memorise the known-spell
/// at index `known_num` (0..999). The server starts a `MemorisingSpell` timer
/// when its global `RequireMemorise` is set, then fills a `MemorisedSpells` slot.
/// Sent reliable. SPL-4.
pub fn memorise_packet(known_num: u16) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(b'M').u16(known_num);
    w.into_bytes()
}

/// `P_SpellUpdate "U"` (ServerNet.bb:1135) — un-memorise the known-spell index
/// `num`, clearing its `MemorisedSpells` slot server-side. Sent reliable. SPL-4.
pub fn unmemorise_packet(num: u16) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(b'U').u16(num);
    w.into_bytes()
}

/// `P_ActionBarUpdate "S"` (ServerNet.bb:1105) — persist a spell on hotbar slot
/// `slot` (0..35) for the logged-in character: `"S"` + slot byte + spell NAME
/// (the rest of the packet, server-capped to 255 bytes). The server keys the bar
/// by spell name (not id), so the caller passes the resolved name. Sent reliable.
pub fn action_bar_spell_packet(slot: u8, name: &str) -> Vec<u8> {
    let mut v = Vec::with_capacity(2 + name.len());
    v.push(b'S');
    v.push(slot);
    v.extend_from_slice(name.as_bytes());
    v
}

/// `P_ActionBarUpdate "I"` (ServerNet.bb:1120) — persist an item on hotbar slot
/// `slot`: `"I"` + slot byte + item id (2-byte LE, the same bytes the server
/// re-stores via `Mid$(data,3,2)`). Sent reliable.
pub fn action_bar_item_packet(slot: u8, item_id: u16) -> Vec<u8> {
    let mut v = Vec::with_capacity(4);
    v.push(b'I');
    v.push(slot);
    v.extend_from_slice(&item_id.to_le_bytes());
    v
}

/// `P_ActionBarUpdate "N"` (ServerNet.bb:1122) — clear hotbar slot `slot`,
/// storing "" server-side. `"N"` + slot byte. Sent reliable.
pub fn action_bar_clear_packet(slot: u8) -> Vec<u8> {
    vec![b'N', slot]
}

/// Build a `P_InventoryUpdate` pickup request (`ServerNet.bb:1611`): `"P"` +
/// DroppedItem handle (u32) + target inventory slot (u8). The server validates
/// same-area + distance + slot, then replies `"R"` to the picker and `"P"` to
/// everyone else. Sent reliable.
pub fn pickup_packet(handle: u32, slot: u8) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(b'P').u32(handle).u8(slot);
    w.into_bytes()
}

/// `P_RightClick` (`Interface3D.bb:668`): the target actor's RuntimeID (u16).
/// Triggers the NPC's RightClick script server-side — a vendor replies with
/// `P_OpenTrading`, a dialog NPC with chat output. Sent reliable.
pub fn right_click_packet(runtime_id: u16) -> Vec<u8> {
    runtime_id.to_le_bytes().to_vec()
}

/// `P_Examine` (`Interface3D.bb:694`): the target actor's RuntimeID (u16).
/// Runs the NPC's Examine script; the reply comes back as chat/output text.
/// Sent reliable.
pub fn examine_packet(runtime_id: u16) -> Vec<u8> {
    runtime_id.to_le_bytes().to_vec()
}

/// `P_Dialog` option selection (`Interface3D.bb:1581`): `"O"` + the dialog's
/// script handle (u32) + the chosen option index (u8). Tells the NPC's paused
/// `Main` script which branch the player picked. Sent reliable.
pub fn dialog_option_packet(script_handle: u32, opt: u8) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(b'O').u32(script_handle).u8(opt);
    w.into_bytes()
}

/// `P_ScriptInput` reply (`Interface3D.bb:1595`): the dialog's script handle
/// (u32) + the user's typed text (raw, no length prefix — the server reads the
/// rest). Sent reliable when the user submits; ESC-cancel sends nothing.
pub fn script_input_reply(script_handle: u32, text: &str) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u32(script_handle);
    let mut b = w.into_bytes();
    b.extend_from_slice(text.as_bytes());
    b
}

/// Number of vendor/inventory slots in a trade basket (client `Dim(31)`).
const TRADE_SLOTS: usize = 32;

/// Build a `P_OpenTrading` buy/sell confirmation (`Interface3D.bb:2335-2350`):
/// 32 "his" slots of `ServerTradeID i32 + amount u16` (the items being bought;
/// unused slots are `-1, 0`), then 32 "mine" slots of `backpackSlot u8 +
/// amount u16` (items being sold; unused `0, 0`). Sent reliable.
pub fn trade_confirm_packet(buys: &[(u32, u16)], sells: &[(u8, u16)]) -> Vec<u8> {
    let mut w = MsgWriter::new();
    for i in 0..TRADE_SLOTS {
        match buys.get(i) {
            Some(&(id, amt)) => {
                w.u32(id).u16(amt);
            }
            None => {
                w.u32(0xFFFF_FFFF).u16(0); // -1 sentinel = empty slot
            }
        }
    }
    for i in 0..TRADE_SLOTS {
        match sells.get(i) {
            Some(&(slot, amt)) => {
                w.u8(slot).u16(amt);
            }
            None => {
                w.u8(0).u16(0);
            }
        }
    }
    w.into_bytes()
}

/// Build a `P_InventoryUpdate` move/swap request (`ServerNet.bb:1740`): `"S"`
/// (swap) or `"A"` (add/merge) + the player's RuntimeID u16 + source slot u8 +
/// dest slot u8 + amount u16 (0 = whole stack). Equipping is a swap from a
/// backpack slot to the matching equipment slot. Sent reliable.
pub fn inv_move_packet(runtime_id: u16, from_slot: u8, to_slot: u8, amount: u16, swap: bool) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(if swap { b'S' } else { b'A' });
    w.u16(runtime_id).u8(from_slot).u8(to_slot).u16(amount);
    w.into_bytes()
}

/// Build a `P_InventoryUpdate` drop request (`ServerNet.bb:1671`): `"D"` +
/// inventory slot u8 + amount u16. The server drops the item to the floor (a
/// world DroppedItem) and "T"-takes it from our inventory. Sent reliable.
pub fn inv_drop_packet(slot: u8, amount: u16) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(b'D').u8(slot).u16(amount);
    w.into_bytes()
}

/// Build a `P_EatItem` use request (`Interface3D.bb:4142` UseItem): inventory
/// slot u8 + amount u16. The server consumes the food/ingredient in that slot
/// and applies its effects. Sent reliable. (Server gates by item type, but the
/// client should only send this for Potion/Ingredient items.)
pub fn eat_item_packet(slot: u8, amount: u16) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(slot).u16(amount);
    w.into_bytes()
}

/// Build a `P_ItemScript` use request (`Interface3D.bb:4160,4208-4216` UseItem):
/// inventory slot u8, plus the selected target's RuntimeID u16 when one exists.
/// The server runs the item's `Use` script (with the optional target actor); the
/// server-side handler tolerates a missing/`Null` target, so an untargeted use
/// just omits the 2 bytes (matching the stale-handle pattern). Sent reliable.
/// Covers I_Image and I_Other/I_Ring — everything `UseItem` routes past eating.
pub fn item_script_packet(slot: u8, target: Option<u16>) -> Vec<u8> {
    let mut w = MsgWriter::new();
    w.u8(slot);
    if let Some(rid) = target {
        w.u16(rid);
    }
    w.into_bytes()
}

/// Build a `P_OpenTrading` close (`Interface3D.bb:2303`): an empty body tells
/// the server the trade window was dismissed. Sent reliable.
pub fn trade_close_packet() -> Vec<u8> {
    Vec::new()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn trade_confirm_one_buy_layout() {
        let p = trade_confirm_packet(&[(1001, 3)], &[]);
        // 32*(4+2) + 32*(1+2) = 192 + 96 = 288 bytes.
        assert_eq!(p.len(), 288);
        // Slot 0 buy: id 1001 (LE u32) + amount 3 (LE u16).
        assert_eq!(&p[0..6], &[0xE9, 0x03, 0, 0, 3, 0]);
        // Slot 1 buy: the -1 sentinel + 0.
        assert_eq!(&p[6..12], &[0xFF, 0xFF, 0xFF, 0xFF, 0, 0]);
        // First "mine" sell slot (offset 192): empty 0,0,0.
        assert_eq!(&p[192..195], &[0, 0, 0]);
    }

    #[test]
    fn trade_confirm_batched_buys_and_sells() {
        // The whole vendor basket goes out as ONE confirm (the server ends trading
        // after a single confirm): two buys + two sells in the same packet.
        let p = trade_confirm_packet(&[(1001, 2), (1002, 1)], &[(14, 3), (15, 1)]);
        assert_eq!(p.len(), 288);
        assert_eq!(&p[0..6], &[0xE9, 0x03, 0, 0, 2, 0]); // buy0: id 1001 amt 2
        assert_eq!(&p[6..12], &[0xEA, 0x03, 0, 0, 1, 0]); // buy1: id 1002 amt 1
        assert_eq!(&p[12..18], &[0xFF, 0xFF, 0xFF, 0xFF, 0, 0]); // buy2: empty
        assert_eq!(&p[192..195], &[14, 3, 0]); // sell0: slot 14 amt 3
        assert_eq!(&p[195..198], &[15, 1, 0]); // sell1: slot 15 amt 1
    }

    #[test]
    fn trade_confirm_with_sell() {
        let p = trade_confirm_packet(&[], &[(14, 5)]);
        assert_eq!(p.len(), 288);
        // Sell slot 0 at byte 192: backpack slot 14 + amount 5 (LE u16).
        assert_eq!(&p[192..195], &[14, 5, 0]);
    }

    #[test]
    fn trade_close_is_empty() {
        assert!(trade_close_packet().is_empty());
    }

    #[test]
    fn action_bar_spell_layout() {
        // Server parse: type = Left$(data,1), slot = Mid$(data,2,1) [byte 1],
        // name = Mid$(data,3) [bytes 2..]. (ServerNet.bb:1102-1119)
        let p = action_bar_spell_packet(5, "Fireball");
        assert_eq!(p[0], b'S');
        assert_eq!(p[1], 5);
        assert_eq!(&p[2..], b"Fireball");
    }

    #[test]
    fn action_bar_item_layout() {
        // Server parse: type 'I', slot byte 1, item id = Mid$(data,3,2) [bytes 2..4]
        // little-endian. (ServerNet.bb:1120)
        let p = action_bar_item_packet(3, 1000);
        assert_eq!(p, vec![b'I', 3, 0xE8, 0x03]);
    }

    #[test]
    fn action_bar_clear_layout() {
        let p = action_bar_clear_packet(7);
        assert_eq!(p, vec![b'N', 7]);
    }

    #[test]
    fn memorise_packet_layout() {
        // 'M' + known_num as LE u16 (matches cast_packet's "F"+u16 framing).
        assert_eq!(memorise_packet(0x0102), vec![b'M', 0x02, 0x01]);
        assert_eq!(memorise_packet(7), vec![b'M', 7, 0]);
        // 'U' + num as LE u16.
        assert_eq!(unmemorise_packet(0x0102), vec![b'U', 0x02, 0x01]);
        assert_eq!(unmemorise_packet(3), vec![b'U', 3, 0]);
    }

    #[test]
    fn inv_drop_layout() {
        // "D" + slot 14 + amount 1 (LE u16).
        assert_eq!(inv_drop_packet(14, 1), vec![b'D', 14, 1, 0]);
        assert_eq!(inv_drop_packet(3, 0x0102), vec![b'D', 3, 0x02, 0x01]);
    }

    #[test]
    fn eat_item_layout() {
        // P_EatItem body: slot u8 + amount u16 (LE), no sub-type char.
        assert_eq!(eat_item_packet(20, 1), vec![20, 1, 0]);
        assert_eq!(eat_item_packet(45, 0x0102), vec![45, 0x02, 0x01]);
    }

    #[test]
    fn item_script_layout() {
        // Untargeted use: just the slot byte (server tolerates no target).
        assert_eq!(item_script_packet(20, None), vec![20]);
        // Targeted use: slot u8 + target RuntimeID u16 (LE).
        assert_eq!(item_script_packet(20, Some(0x0102)), vec![20, 0x02, 0x01]);
        assert_eq!(item_script_packet(45, Some(7)), vec![45, 7, 0]);
    }

    #[test]
    fn inv_move_layout() {
        // Swap: "S" + rid 7 (LE) + from 14 + to 0 + amount 0 (LE).
        assert_eq!(inv_move_packet(7, 14, 0, 0, true), vec![b'S', 7, 0, 14, 0, 0, 0]);
        // Add: "A" + rid 7 + from 14 + to 20 + amount 5.
        assert_eq!(inv_move_packet(7, 14, 20, 5, false), vec![b'A', 7, 0, 14, 20, 5, 0]);
    }

    #[test]
    fn interact_packets_are_le_runtime_id() {
        assert_eq!(right_click_packet(7), vec![7, 0]);
        assert_eq!(examine_packet(0x0102), vec![0x02, 0x01]);
    }

    #[test]
    fn cast_without_target_omits_rid() {
        // "F" + spell id 101 (LE), no target → 3 bytes.
        assert_eq!(cast_packet(101, None), vec![b'F', 101, 0]);
    }

    #[test]
    fn cast_with_target_appends_rid() {
        // "F" + spell 101 + target 7 (both LE u16) → 5 bytes.
        assert_eq!(cast_packet(101, Some(7)), vec![b'F', 101, 0, 7, 0]);
    }

    #[test]
    fn pickup_layout() {
        // "P" + handle 0x01020304 (LE) + slot 14.
        assert_eq!(pickup_packet(0x0102_0304, 14), vec![b'P', 0x04, 0x03, 0x02, 0x01, 14]);
    }
}
