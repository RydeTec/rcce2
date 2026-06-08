//! Reusable login flow (generic over [`Transport`]): account create/verify,
//! character create, then a fresh connection for `P_StartGame`. Mirrors the
//! reference client's two-connection menu→game handshake. World packets that
//! arrive during the StartGame wait are buffered and returned for the caller to
//! apply to its [`World`](crate::world::World).

use std::thread::sleep;
use std::time::{Duration, Instant};

use rcce_net::auth::{encrypt_email, md5_hex};
use rcce_net::codec::{MsgReader, MsgWriter};
use rcce_net::{packet_id as pk, RecvMessage, Transport};

use crate::fetch::CharacterSheet;

pub struct Credentials {
    pub username: String,
    pub password: String,
    pub email: String,
}

/// One character on an account, as packed in the `P_VerifyAccount` /
/// `P_DeleteCharacter` 'Y'/list response (`ServerNet.bb:3001`): name, the actor
/// template id, gender, and the appearance selections.
#[derive(Debug, Clone)]
pub struct CharInfo {
    pub name: String,
    pub actor_id: u16,
    pub gender: u8,
    pub face: u8,
    pub hair: u8,
    pub beard: u8,
    pub body: u8,
}

/// Parse a packed character-list blob (the bytes AFTER any sentinel). Each
/// record: `nameLen:u8 name`, `actorID:u16`, `gender:u8`, `face:u8`, `hair:u8`,
/// `beard:u8`, `body:u8`. Stops at the first short/garbage record.
pub fn parse_char_list(data: &[u8]) -> Vec<CharInfo> {
    let mut r = MsgReader::new(data);
    let mut out = Vec::new();
    while r.remaining() > 0 {
        let Some(name) = r.str8() else { break };
        let (Some(actor_id), Some(gender)) = (r.u16(), r.u8()) else { break };
        let face = r.u8().unwrap_or(0);
        let hair = r.u8().unwrap_or(0);
        let beard = r.u8().unwrap_or(0);
        let body = r.u8().unwrap_or(0);
        out.push(CharInfo { name, actor_id, gender, face, hair, beard, body });
    }
    out
}

/// Re-query the account's character list (a fresh `P_VerifyAccount`). Used after
/// a create/delete to refresh the on-screen roster.
fn verify_list<T: Transport>(t: &mut T, peer: i32, user: &str, md5: &str) -> Result<Vec<CharInfo>, String> {
    let mut w = MsgWriter::new();
    w.str8(user).str8(md5);
    t.send(peer, pk::VERIFY_ACCOUNT, w.as_slice(), true);
    let resp = pump(t, 1500);
    let m = resp
        .into_iter()
        .find(|m| matches!(sentinel(m), 'Y' | 'P' | 'N' | 'B' | 'L'))
        .ok_or("no VerifyAccount response")?;
    match sentinel(&m) {
        'Y' => Ok(parse_char_list(&m.data[1..])),
        'B' => Err("account banned".into()),
        // 'L' = a session is already active. 'P' is the server's GENERIC
        // auth-failure code (wrong password / no account / truncated /
        // throttled — "auth before disclosure", ServerNet.bb:2441) and must NOT
        // be shown as "already online" — that was the bug that made a rejected
        // password read as a stuck session.
        'L' => Err("account already online".into()),
        _ => Err("wrong username or password".into()),
    }
}

/// **Step 1 of the interactive flow.** Connect, ensure the account exists
/// (CreateAccount is idempotent), verify the password, and return the live
/// connection plus the character roster. Keep `peer` open for create/delete and
/// the eventual [`enter_world`]. Maps server sentinels to human errors.
pub fn account_login<T: Transport>(
    t: &mut T,
    host: &str,
    port: u16,
    c: &Credentials,
) -> Result<(i32, Vec<CharInfo>), String> {
    let md5 = md5_hex(&c.password);
    let peer = t.connect(host, port).map_err(|e| format!("connect: {e}"))?;
    // CreateAccount — 'N' just means it already exists (idempotent).
    let mut w = MsgWriter::new();
    w.str8(&c.username)
        .str8(&md5)
        .u8(c.email.len() as u8)
        .raw(&encrypt_email(&c.email));
    t.send(peer, pk::CREATE_ACCOUNT, w.as_slice(), true);
    let _ = pump(t, 1000);
    let chars = verify_list(t, peer, &c.username, &md5)?;
    Ok((peer, chars))
}

/// **Create a character** on the open login connection, then return the
/// refreshed roster. `actor_id` is a playable template; appearance selections
/// default to 0.
pub fn create_char<T: Transport>(
    t: &mut T,
    peer: i32,
    user: &str,
    md5: &str,
    actor_id: u16,
    name: &str,
) -> Result<Vec<CharInfo>, String> {
    let mut w = MsgWriter::new();
    w.str8(user).str8(md5);
    w.u16(actor_id).u8(0).u8(0).u8(0).u8(0).u8(0);
    w.raw(&[0u8; 40]);
    w.raw(name.as_bytes());
    t.send(peer, pk::CREATE_CHARACTER, w.as_slice(), true);
    match pump(t, 1500)
        .iter()
        .find(|m| matches!(sentinel(m), 'Y' | 'I' | 'N'))
        .map(sentinel)
    {
        Some('Y') => verify_list(t, peer, user, md5),
        Some('I') => Err("that name is taken".into()),
        _ => Err("character creation rejected".into()),
    }
}

/// **Delete a character** by slot index, returning the refreshed roster. The
/// server only honours this for the session that owns the account, so it may be
/// rejected pre-game (returns an error the UI surfaces).
pub fn delete_char<T: Transport>(
    t: &mut T,
    peer: i32,
    user: &str,
    md5: &str,
    index: u8,
) -> Result<Vec<CharInfo>, String> {
    let mut w = MsgWriter::new();
    w.str8(user).str8(md5).u8(index);
    t.send(peer, pk::DELETE_CHARACTER, w.as_slice(), true);
    let m = pump(t, 1500)
        .into_iter()
        .find(|m| m.msg_type == pk::DELETE_CHARACTER)
        .ok_or("no delete response")?;
    if sentinel(&m) == 'N' && m.data.len() == 1 {
        return Err("delete rejected by server".into());
    }
    Ok(parse_char_list(&m.data))
}

/// **Final step.** Leave the menu connection and open the game connection for
/// character `index`: fetch the sheet on the menu connection, disconnect, then
/// `P_StartGame` on a fresh connection. Mirrors [`login`]'s conn#2 handshake.
pub fn enter_world<T: Transport>(
    t: &mut T,
    menu_peer: i32,
    host: &str,
    port: u16,
    user: &str,
    md5: &str,
    index: u8,
) -> Result<LoginOutcome, String> {
    let sheet = fetch_character(t, menu_peer, user, md5, index);
    t.disconnect(menu_peer);
    sleep(Duration::from_millis(300));

    let peer2 = t.connect(host, port).map_err(|e| format!("conn#2: {e}"))?;
    let mut w = MsgWriter::new();
    w.str8(user).str8(md5).u8(index);
    t.send(peer2, pk::START_GAME, w.as_slice(), true);

    let mut runtime_id = 0u16;
    let mut replies = 0;
    let mut world_packets = Vec::new();
    let mut action_bar = Vec::new();
    let deadline = Instant::now() + Duration::from_secs(5);
    while Instant::now() < deadline && replies < 13 {
        for m in t.poll() {
            if m.msg_type == pk::START_GAME {
                if sentinel(&m) == 'N' && m.data.len() == 1 {
                    return Err("StartGame rejected ('N')".into());
                }
                if m.data.len() <= 2 {
                    runtime_id = MsgReader::new(&m.data).u16().unwrap_or(0);
                } else {
                    // Action-bar chunk (3 persisted slots); the 2-byte runtime-id
                    // packet is the only short `P_StartGame`, so len > 2 == a chunk.
                    parse_action_bar_chunk(&m.data, &mut action_bar);
                }
                replies += 1;
            } else {
                world_packets.push(m);
            }
        }
        sleep(Duration::from_millis(20));
    }
    if runtime_id == 0 {
        return Err("no RuntimeID assigned".into());
    }
    Ok(LoginOutcome { runtime_id, peer: peer2, world_packets, sheet, action_bar })
}

/// One persisted action-bar slot as stored server-side: a spell (keyed by name)
/// or an item (by id). Items aren't placeable on the Rust hotbar yet, so the
/// caller currently ignores `Item` slots (parsed for completeness / future use).
#[derive(Debug, Clone)]
pub enum ActionSlot {
    Spell(String),
    Item(u16),
}

/// Parse one `P_StartGame` action-bar chunk (ServerNet.bb:2170-2177): a 1-byte
/// start slot index, then 3 × [2-byte-LE length + slot bytes]. Each slot is ""
/// (empty), `"S"+name`, or `"I"+2-byte item id`. Non-empty slots are appended to
/// `out` as `(slot_index, ActionSlot)`. Read with raw `bytes()` (not `str16`) so
/// the item id's non-UTF-8 bytes survive. Tolerant of a short/garbled chunk.
fn parse_action_bar_chunk(data: &[u8], out: &mut Vec<(usize, ActionSlot)>) {
    let mut r = MsgReader::new(data);
    let Some(start) = r.u8() else { return };
    for k in 0..3usize {
        let Some(len) = r.u16() else { return };
        let Some(bytes) = r.bytes(len as usize) else { return };
        if bytes.is_empty() {
            continue;
        }
        let idx = start as usize + k;
        match bytes[0] {
            b'S' => out.push((idx, ActionSlot::Spell(String::from_utf8_lossy(&bytes[1..]).into_owned()))),
            b'I' if bytes.len() >= 3 => out.push((idx, ActionSlot::Item(u16::from_le_bytes([bytes[1], bytes[2]])))),
            _ => {}
        }
    }
}

pub struct LoginOutcome {
    /// Server-assigned runtime id for the local player.
    pub runtime_id: u16,
    /// The live (game) connection handle — keep it for sending.
    pub peer: i32,
    /// World packets received during the StartGame wait (apply these first).
    pub world_packets: Vec<RecvMessage>,
    /// Character sheet (stats/inventory/spells) from `P_FetchCharacter`, if the
    /// fetch on connection #1 succeeded. `None` if the server didn't answer.
    pub sheet: Option<CharacterSheet>,
    /// Persisted action-bar slots streamed during `P_StartGame` login (the 12
    /// 3-slot chunks). `(slot_index, slot)`; the caller resolves spell names to
    /// ids against the sheet/known spells. Empty if the server sent none.
    pub action_bar: Vec<(usize, ActionSlot)>,
}

/// Request + collect the `P_FetchCharacter` stream on connection #1 for
/// character `index`. Non-fatal: returns `None` if the server doesn't answer in
/// time (e.g. an older build), so login proceeds regardless.
fn fetch_character<T: Transport>(
    t: &mut T,
    peer: i32,
    user: &str,
    md5: &str,
    index: u8,
) -> Option<CharacterSheet> {
    let mut w = MsgWriter::new();
    w.str8(user).str8(md5).u8(index);
    t.send(peer, pk::FETCH_CHARACTER, w.as_slice(), true);

    let mut sheet = CharacterSheet::default();
    let deadline = Instant::now() + Duration::from_secs(3);
    while Instant::now() < deadline && !sheet.done {
        for m in t.poll() {
            if m.msg_type == pk::FETCH_CHARACTER {
                sheet.apply_packet(&m.data);
            }
        }
        sleep(Duration::from_millis(15));
    }
    // Only surface a sheet we actually received data for.
    if sheet.done || sheet.level > 0 || !sheet.inventory.is_empty() || !sheet.spells.is_empty() {
        Some(sheet)
    } else {
        None
    }
}

fn pump<T: Transport>(t: &mut T, ms: u64) -> Vec<RecvMessage> {
    let mut all = Vec::new();
    let deadline = Instant::now() + Duration::from_millis(ms);
    while Instant::now() < deadline {
        all.extend(t.poll());
        sleep(Duration::from_millis(15));
    }
    all
}

fn sentinel(m: &RecvMessage) -> char {
    m.data.first().map(|&b| b as char).unwrap_or('\0')
}

/// Run the full login flow against `host:port`. On success the player is in the
/// world with a runtime id; the returned `peer` is the live connection.
pub fn login<T: Transport>(
    t: &mut T,
    host: &str,
    port: u16,
    c: &Credentials,
) -> Result<LoginOutcome, String> {
    let md5 = md5_hex(&c.password);

    // ---- Connection #1: account + character ----
    let peer = t.connect(host, port).map_err(|e| format!("conn#1: {e}"))?;

    // CreateAccount (idempotent: "N" just means it already exists).
    let mut w = MsgWriter::new();
    w.str8(&c.username)
        .str8(&md5)
        .u8(c.email.len() as u8)
        .raw(&encrypt_email(&c.email));
    t.send(peer, pk::CREATE_ACCOUNT, w.as_slice(), true);
    let _ = pump(t, 1200);

    // VerifyAccount → 'Y' + packed character records.
    let mut w = MsgWriter::new();
    w.str8(&c.username).str8(&md5);
    t.send(peer, pk::VERIFY_ACCOUNT, w.as_slice(), true);
    let resp = pump(t, 1500);
    let m = resp
        .into_iter()
        .find(|m| matches!(sentinel(m), 'Y' | 'P' | 'N' | 'B' | 'L'))
        .ok_or("no VerifyAccount response")?;
    let char_count = match sentinel(&m) {
        'Y' => {
            let mut r = MsgReader::new(&m.data[1..]);
            let mut n = 0;
            while r.remaining() > 0 {
                if r.str8().is_none() {
                    break;
                }
                let (actor, gender) = (r.u16(), r.u8());
                let _appearance = (r.u8(), r.u8(), r.u8(), r.u8());
                if actor.is_none() || gender.is_none() {
                    break;
                }
                n += 1;
            }
            n
        }
        c => return Err(format!("VerifyAccount failed (sentinel '{c}')")),
    };

    // CreateCharacter if the account has none. Always send the 40-byte attribute
    // block — the server reads the name at a hardcoded Offset+47.
    if char_count == 0 {
        let mut created = false;
        'actors: for actor_id in 0u16..=20 {
            for suffix in 0..3 {
                let name = if suffix == 0 {
                    "Rustaroo".to_string()
                } else {
                    format!("Rustaroo{suffix}")
                };
                let mut w = MsgWriter::new();
                w.str8(&c.username).str8(&md5);
                w.u16(actor_id).u8(0).u8(0).u8(0).u8(0).u8(0);
                w.raw(&[0u8; 40]);
                w.raw(name.as_bytes());
                t.send(peer, pk::CREATE_CHARACTER, w.as_slice(), true);
                match pump(t, 1500)
                    .iter()
                    .find(|m| matches!(sentinel(m), 'Y' | 'I' | 'N'))
                    .map(sentinel)
                {
                    Some('Y') => {
                        created = true;
                        break 'actors;
                    }
                    Some('I') => continue,
                    Some('N') => break,
                    _ => {}
                }
            }
        }
        if !created {
            return Err("could not create a character".into());
        }
    }

    // Fetch the character sheet (stats/inventory/spells) while conn #1 is still
    // open. Non-fatal — the world is entered regardless.
    let sheet = fetch_character(t, peer, &c.username, &md5, 0);

    t.disconnect(peer);
    sleep(Duration::from_millis(300));

    // ---- Connection #2: enter the world ----
    let peer2 = t.connect(host, port).map_err(|e| format!("conn#2: {e}"))?;
    let mut w = MsgWriter::new();
    w.str8(&c.username).str8(&md5).u8(0); // character index 0
    t.send(peer2, pk::START_GAME, w.as_slice(), true);

    let mut runtime_id = 0u16;
    let mut replies = 0;
    let mut world_packets = Vec::new();
    let mut action_bar = Vec::new();
    let deadline = Instant::now() + Duration::from_secs(5);
    while Instant::now() < deadline && replies < 13 {
        for m in t.poll() {
            if m.msg_type == pk::START_GAME {
                if sentinel(&m) == 'N' && m.data.len() == 1 {
                    return Err("StartGame rejected ('N')".into());
                }
                if m.data.len() <= 2 {
                    runtime_id = MsgReader::new(&m.data).u16().unwrap_or(0);
                } else {
                    parse_action_bar_chunk(&m.data, &mut action_bar);
                }
                replies += 1;
            } else {
                world_packets.push(m);
            }
        }
        sleep(Duration::from_millis(20));
    }
    if runtime_id == 0 {
        return Err("no RuntimeID assigned".into());
    }
    Ok(LoginOutcome {
        runtime_id,
        peer: peer2,
        world_packets,
        sheet,
        action_bar,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use rcce_net::codec::MsgWriter;

    /// Build one chunk the way the server does (ServerNet.bb:2170-2177): a start
    /// slot byte then 3 × [2-byte-LE length + slot bytes].
    fn build_chunk(start: u8, slots: [&[u8]; 3]) -> Vec<u8> {
        let mut w = MsgWriter::new();
        w.u8(start);
        for s in slots {
            w.u16(s.len() as u16).raw(s);
        }
        w.into_bytes()
    }

    #[test]
    fn action_bar_chunk_roundtrip() {
        // slot 3 = Fireball, slot 4 = empty, slot 5 = item id 1000.
        let item = [b'I', 0xE8, 0x03]; // "I" + 1000 LE
        let chunk = build_chunk(3, [b"SFireball", b"", &item]);
        let mut out = Vec::new();
        parse_action_bar_chunk(&chunk, &mut out);
        assert_eq!(out.len(), 2, "empty slot is skipped");
        match &out[0] {
            (3, ActionSlot::Spell(n)) => assert_eq!(n, "Fireball"),
            other => panic!("slot0 wrong: {other:?}"),
        }
        match &out[1] {
            (5, ActionSlot::Item(id)) => assert_eq!(*id, 1000),
            other => panic!("slot2 wrong: {other:?}"),
        }
    }

    #[test]
    fn action_bar_chunk_truncated_is_safe() {
        // A chunk that claims a 9-byte slot but is cut short must not panic and
        // must yield nothing past the truncation.
        let mut bad = vec![0u8]; // start = 0
        bad.extend_from_slice(&9u16.to_le_bytes()); // len 9
        bad.extend_from_slice(b"Fire"); // only 4 bytes present
        let mut out = Vec::new();
        parse_action_bar_chunk(&bad, &mut out);
        assert!(out.is_empty(), "truncated slot yields nothing, no panic");
    }
}
