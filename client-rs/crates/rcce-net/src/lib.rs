//! RCCE2 wire protocol — transport + packet codec.
//!
//! Wire contract, verified against `extras/RCEnet_DLL/RCEnet/main.cpp`:
//!
//! * Transport is **standard ENet**. The client connects with up to 254/255
//!   channels (`enet_host_connect(host, &addr, 254, NULL)`).
//! * Every ENet packet is framed `[MessageType: u8][payload: N bytes]`
//!   (`Data[0] = MessageType`; receiver reads `data[0]` as the type).
//! * **Reliable** messages go on ENet channel [`CHANNEL_RELIABLE`] (1),
//!   **unreliable** on [`CHANNEL_UNRELIABLE`] (2). Channel 0 is unused.
//! * Immediately after the ENet CONNECT event the client sends a single
//!   [`MSG_NEW_CLIENT`] (type 0, empty, reliable) packet to announce itself.
//! * The DLL synthesizes type 201 locally on a peer disconnect; it is never a
//!   real wire packet (the client must not trust a remote-claimed 200/201/202 —
//!   see `ClientNet.bb:45`).
//!
//! NOTE the byte-order split: the *payload* fields use **big-endian**
//! (`RCE_StrFromInt$`, see `rcce-net` codec — forthcoming), which is the
//! OPPOSITE of the little-endian `.dat`/save files handled by `rcce-data`.

/// ENet channel for reliable messages (`enet_peer_send(peer, 1, ...)`).
pub const CHANNEL_RELIABLE: u8 = 1;
/// ENet channel for unreliable messages (`enet_peer_send(peer, 2, ...)`).
pub const CHANNEL_UNRELIABLE: u8 = 2;
/// Channel count requested on connect (matches the DLL's 254).
pub const CONNECT_CHANNELS: usize = 254;

pub mod auth;
pub mod codec;
pub mod transport;
pub use transport::{RecvMessage, Transport, TransportError};

/// Server packet type bytes (`P_*` in `Packets.bb`). Only the ones the login
/// flow and early gameplay touch are listed here; extend as handlers land.
pub mod packet_id {
    pub const CREATE_ACCOUNT: u8 = 1;
    pub const VERIFY_ACCOUNT: u8 = 2;
    pub const FETCH_CHARACTER: u8 = 3;
    pub const CREATE_CHARACTER: u8 = 4;
    pub const DELETE_CHARACTER: u8 = 5;
    pub const FETCH_ACTORS: u8 = 7;
    pub const CHANGE_AREA: u8 = 9;
    pub const NEW_ACTOR: u8 = 11;
    pub const START_GAME: u8 = 12;
    pub const ACTOR_GONE: u8 = 13;
    pub const STANDARD_UPDATE: u8 = 14;
    pub const INVENTORY_UPDATE: u8 = 15;
    pub const CHAT_MESSAGE: u8 = 16;
    pub const WEATHER_CHANGE: u8 = 17;
    pub const SOUND: u8 = 29;
    pub const MUSIC: u8 = 34;
    pub const SPEECH: u8 = 50;
    pub const ATTACK_ACTOR: u8 = 18;
    pub const ACTOR_DEAD: u8 = 19;
    pub const RIGHT_CLICK: u8 = 20;
    pub const DIALOG: u8 = 21;
    pub const QUEST_LOG: u8 = 23;
    pub const STAT_UPDATE: u8 = 22;
    pub const GOLD_CHANGE: u8 = 24;
    pub const NAME_CHANGE: u8 = 25;
    pub const KNOWN_SPELL_UPDATE: u8 = 26;
    pub const SPELL_UPDATE: u8 = 27;
    /// Per-character action-bar slot assignment (`P_ActionBarUpdate`,
    /// ServerNet.bb:1098). Client→server persists one hotbar slot:
    /// `"S"+slot_byte+spellname` / `"I"+slot_byte+itemid2` / `"N"+slot_byte`.
    pub const ACTION_BAR_UPDATE: u8 = 31;
    pub const XP_UPDATE: u8 = 32;
    pub const SCREEN_FLASH: u8 = 33;
    pub const OPEN_TRADING: u8 = 35;
    pub const ACTOR_EFFECT: u8 = 36;
    pub const PROJECTILE: u8 = 37;
    pub const PARTY_UPDATE: u8 = 38;
    pub const ITEM_SCRIPT: u8 = 43;
    pub const EAT_ITEM: u8 = 44;
    pub const JUMP: u8 = 46;
    pub const PROGRESS_BAR: u8 = 51;
    pub const BUBBLE_MESSAGE: u8 = 52;
    pub const SCRIPT_INPUT: u8 = 53;
    pub const EXAMINE: u8 = 61;
    pub const TRADE: u8 = 62;
}

/// Type 0: the empty reliable packet the client sends on connect.
pub const MSG_NEW_CLIENT: u8 = 0;

/// Locally-synthesized sentinel message types (never trusted from the wire).
pub const MSG_PLAYER_TIMED_OUT: u8 = 200;
pub const MSG_PLAYER_HAS_LEFT: u8 = 201;
pub const MSG_PLAYER_KICKED: u8 = 202;

/// Frame a message for the wire: `[type][payload]`.
pub fn frame(msg_type: u8, payload: &[u8]) -> Vec<u8> {
    let mut buf = Vec::with_capacity(payload.len() + 1);
    buf.push(msg_type);
    buf.extend_from_slice(payload);
    buf
}

/// Split a received ENet packet into `(type, payload)`. Returns `None` for an
/// empty packet (the DLL always writes at least the 1-byte type).
pub fn unframe(data: &[u8]) -> Option<(u8, &[u8])> {
    data.split_first().map(|(&t, rest)| (t, rest))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn frame_roundtrip() {
        let f = frame(14, &[0xDE, 0xAD]);
        assert_eq!(f, vec![14, 0xDE, 0xAD]);
        assert_eq!(unframe(&f), Some((14, &[0xDE, 0xAD][..])));
    }

    #[test]
    fn new_client_is_empty() {
        let f = frame(MSG_NEW_CLIENT, &[]);
        assert_eq!(f, vec![0]);
        assert_eq!(unframe(&f), Some((0, &[][..])));
    }

    #[test]
    fn unframe_empty_is_none() {
        assert_eq!(unframe(&[]), None);
    }
}
