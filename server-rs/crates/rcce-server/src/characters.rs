//! Character-creation handler — parity with `P_CreateCharacter`
//! (`ServerNet.bb:2735-2969`).
//!
//! Inbound (after `[str user][str pass]`): `[u16 actorId][u8 gender][u8 face]
//! [u8 hair][u8 beard][u8 body][40× u8 attrPoints][name…]` — the name sits at
//! password-end **+47** (2 + 5 + 40), matching the validation read at `:2762`.
//! Replies `"Y"` created / `"I"` invalid name / `"N"` (bad password, no slot,
//! bad ActorID, missing start area, attribute cheat).

use rcce_net::codec::{MsgReader, MsgWriter};
use rcce_server_accounts::password;
use rcce_server_accounts::store::AccountStore;
use rcce_server_accounts::throttle::LoginThrottle;
use rcce_server_core::area::Area;
use rcce_server_core::record::CharacterRecord;
use rcce_server_core::ActorCatalog;

use crate::config::ServerConfig;

/// `P_CreateCharacter` / `P_FetchCharacter` type bytes (`Packets.bb`).
pub const P_CREATE_CHARACTER: u8 = 4;
pub const P_FETCH_CHARACTER: u8 = 3;

const ATTR_POINT_BYTES: usize = 40;
/// C3 inventory fragment threshold (`999 - ItemInstanceStringLength()`).
const C3_FRAGMENT_AT: usize = 999 - 83;
/// Q quest-log fragment threshold.
const Q_FRAGMENT_AT: usize = 700;

fn read_field(r: &mut MsgReader) -> Option<Vec<u8>> {
    let n = r.u8()? as usize;
    Some(r.bytes(n)?.to_vec())
}

/// True if every byte is printable ASCII (space..`~`).
fn name_charset_ok(name: &[u8]) -> bool {
    name.iter().all(|&c| (32..=126).contains(&c))
}

/// Banned-name substring filter (`Names Filter.txt`). Fails **open** (accepts)
/// if the file is missing/unreadable, matching the Blitz soft-fail.
fn name_is_banned(data_dir: &std::path::Path, name_upper: &str) -> bool {
    let path = data_dir.join("Server Data").join("Names Filter.txt");
    let Ok(content) = std::fs::read_to_string(path) else {
        return false;
    };
    for line in content.lines() {
        let b = line.trim();
        if b.is_empty() || b.starts_with(';') {
            continue;
        }
        if name_upper.contains(&b.to_uppercase()) {
            return true;
        }
    }
    false
}

/// Whether any existing character (any account) already uses this name.
fn name_taken(store: &AccountStore, name_upper: &str) -> bool {
    store
        .iter()
        .any(|a| a.characters.iter().any(|r| r.actor.name.to_uppercase() == name_upper))
}

/// Handle `P_CreateCharacter`. `now_ms` is the throttle clock.
pub fn handle_create_character(
    payload: &[u8],
    store: &mut AccountStore,
    throttle: &mut LoginThrottle,
    catalog: &ActorCatalog,
    config: &ServerConfig,
    peer: u32,
    now_ms: u64,
) -> (u8, Vec<u8>) {
    let deny = (P_CREATE_CHARACTER, b"N".to_vec());
    let invalid_name = (P_CREATE_CHARACTER, b"I".to_vec());

    let mut r = MsgReader::new(payload);
    let Some(user) = read_field(&mut r) else {
        password::verify_password("", "");
        throttle.record(peer, false, now_ms);
        return deny;
    };
    let pass = read_field(&mut r).unwrap_or_default();

    if !throttle.ok(peer, now_ms) {
        return deny;
    }
    let user_s = String::from_utf8_lossy(&user).into_owned();
    let pass_s = String::from_utf8_lossy(&pass).into_owned();

    // Password verify (auth-before-anything). No-account pays the dummy hash.
    let found = store.find(&user_s).is_some();
    if !found || pass.is_empty() {
        password::verify_password("", &pass_s);
        throttle.record(peer, false, now_ms);
        return deny;
    }
    let pwd_ok = store
        .find(&user_s)
        .map(|a| !a.pass.is_empty() && password::verify_password(&a.pass, &pass_s))
        .unwrap_or(false);
    if !pwd_ok {
        throttle.record(peer, false, now_ms);
        return deny;
    }

    // Fixed fields then the name (the rest of the packet).
    let (Some(actor_id), Some(gender), Some(face), Some(hair), Some(beard), Some(body)) =
        (r.u16(), r.u8(), r.u8(), r.u8(), r.u8(), r.u8())
    else {
        return deny;
    };
    let Some(attr_points) = r.bytes(ATTR_POINT_BYTES).map(<[u8]>::to_vec) else {
        return deny;
    };
    let name_bytes = r.rest().to_vec();

    // Name validation (length, charset, banned filter, uniqueness). The Blitz
    // handler validates the uppercased name but stores the original case.
    let name_upper = String::from_utf8_lossy(&name_bytes).to_uppercase();
    if name_bytes.is_empty()
        || name_bytes.len() > 32
        || !name_charset_ok(&name_bytes)
        || name_is_banned(&config.data_dir, &name_upper)
        || name_taken(store, &name_upper)
    {
        // Name failures don't count against the throttle (Blitz slot/name
        // branch sends "I"/"N" without LoginAttemptRecord).
        return invalid_name;
    }

    // ActorID must be a known race.
    let Some(template) = catalog.get(actor_id) else {
        return deny;
    };

    // Build the character from the template, overlay the player's choices.
    let mut c = template.new_character();
    c.gender = if gender > 1 { 0 } else { gender };
    c.face_tex = if face > 4 { 0 } else { face as i16 };
    c.hair = if hair > 4 { 0 } else { hair as i16 };
    c.beard = if beard > 4 { 0 } else { beard as i16 };
    c.body_tex = if body > 4 { 0 } else { body as i16 };
    c.area = template.start_area.clone();
    c.gold = config.start_gold;
    c.reputation = config.start_reputation as i16;

    // Start position from the race's StartPortal in its StartArea. A missing
    // area (misconfigured race) rejects cleanly (Blitz `FindArea = Null → "N"`).
    let Some(area) = Area::load(&config.data_dir, &template.start_area) else {
        return deny;
    };
    if let Some(portal) = area.find_portal(&template.start_portal) {
        c.x = portal.x;
        c.y = portal.y;
        c.z = portal.z;
        c.last_portal_area_name = area.name.clone();
        // LastPortal index = portal's slot in the table.
        if let Some(idx) = area.portals.iter().position(|p| std::ptr::eq(p, portal)) {
            c.last_portal = idx as i16;
        }
    }

    // Attribute points, if the ruleset grants any.
    if config.attribute_assignment > 0 {
        let total: u32 = attr_points.iter().map(|&b| b as u32).sum();
        if total > config.attribute_assignment as u32 {
            return deny; // cheat: spent more than allowed
        }
        for (i, &pts) in attr_points.iter().enumerate() {
            if let Some(v) = c.attributes.value.get_mut(i) {
                *v += pts as i16;
            }
        }
    }

    c.name = String::from_utf8_lossy(&name_bytes).into_owned();

    // Free-slot / max-character check, then persist.
    let max = config.max_account_chars.min(10) as usize;
    let acct = store.find_mut(&user_s).expect("account verified above");
    if acct.characters.len() >= max {
        return deny;
    }
    acct.characters.push(CharacterRecord::new(c));
    throttle.record(peer, true, now_ms);
    if let Err(e) = store.save() {
        eprintln!("[characters] CreateCharacter: save failed: {e}");
    }
    (P_CREATE_CHARACTER, b"Y".to_vec())
}

/// Handle `P_FetchCharacter` (`ServerNet.bb:2611-2732`) — load full character
/// detail for character-select. Inbound: `[str user][str pass][u8 slot]`.
/// Replies with the multipart `C1`(stats) / `C3`(inventory) / `Q`(quests) /
/// `F`(counts) sequence, or a single `"N"` on failure.
///
/// Deferred: the `S`(spells) packet needs a server-side spell catalog
/// (`Spells.dat`) for each spell's name/texture/recharge — not yet ported. A
/// freshly created character has no spells, so the spell count is 0 and no `S`
/// packet is sent (correct for the current flow); characters loaded with spells
/// will under-report until the spell catalog lands.
pub fn handle_fetch_character(
    payload: &[u8],
    store: &mut AccountStore,
    throttle: &mut LoginThrottle,
    peer: u32,
    now_ms: u64,
) -> Vec<(u8, Vec<u8>)> {
    let fail = || vec![(P_FETCH_CHARACTER, b"N".to_vec())];

    let mut r = MsgReader::new(payload);
    let Some(user) = read_field(&mut r) else {
        password::verify_password("", "");
        throttle.record(peer, false, now_ms);
        return fail();
    };
    let pass = read_field(&mut r).unwrap_or_default();
    let slot = r.u8();

    if !throttle.ok(peer, now_ms) {
        return fail();
    }
    let user_s = String::from_utf8_lossy(&user).into_owned();
    let pass_s = String::from_utf8_lossy(&pass).into_owned();

    let acct = store.find(&user_s);
    let ok = acct
        .map(|a| {
            !a.is_banned && !a.pass.is_empty() && !pass.is_empty() && password::verify_password(&a.pass, &pass_s)
        })
        .unwrap_or(false);
    if !ok {
        if acct.is_none() || pass.is_empty() {
            password::verify_password("", &pass_s); // dummy-hash timing parity
        }
        throttle.record(peer, false, now_ms);
        return fail();
    }
    let acct = store.find(&user_s).expect("verified above");

    let Some(slot) = slot else {
        throttle.record(peer, false, now_ms);
        return fail();
    };
    if slot >= 10 || (slot as usize) >= acct.characters.len() {
        throttle.record(peer, false, now_ms);
        return fail();
    }
    let rec = &acct.characters[slot as usize];
    let a = &rec.actor;
    let mut out: Vec<(u8, Vec<u8>)> = Vec::new();

    // C1 — core stats.
    // MsgWriter has no signed-16 helper; `x as u16` yields identical bytes.
    let mut w = MsgWriter::new();
    w.raw(b"C1")
        .i32(a.gold)
        .u16(a.reputation as u16)
        .u16(a.level as u16)
        .i32(a.xp)
        .u8(a.home_faction);
    for i in 0..40 {
        w.u16(a.attributes.value.get(i).copied().unwrap_or(0) as u16);
        w.u16(a.attributes.maximum.get(i).copied().unwrap_or(0) as u16);
    }
    out.push((P_FETCH_CHARACTER, w.into_bytes()));

    // C3 — inventory (present slot → `[u8 i][83B item][i16 amount]`, empty → `99`).
    let mut buf: Vec<u8> = Vec::new();
    for (i, slot_) in a.inventory.iter().enumerate() {
        match &slot_.item {
            Some(item) => {
                buf.push(i as u8);
                buf.extend_from_slice(&item.to_wire_bytes());
                buf.extend_from_slice(&slot_.amount.to_le_bytes());
            }
            None => buf.push(99),
        }
        if buf.len() > C3_FRAGMENT_AT {
            out.push((P_FETCH_CHARACTER, prefixed(b"C3", &buf)));
            buf.clear();
        }
    }
    if !buf.is_empty() {
        out.push((P_FETCH_CHARACTER, prefixed(b"C3", &buf)));
    }

    // S — spells deferred (see fn doc); spells_done stays 0.
    let spells_done: u16 = 0;

    // Q — quest log (`[u8 nameLen][name][u16 statusLen][status]`).
    let mut qbuf: Vec<u8> = Vec::new();
    let mut num: u16 = 0;
    for q in &rec.quests {
        if q.name.is_empty() {
            continue;
        }
        num = num.wrapping_add(1);
        qbuf.push(q.name.len().min(255) as u8);
        qbuf.extend_from_slice(q.name.as_bytes());
        qbuf.extend_from_slice(&(q.status.len() as u16).to_le_bytes());
        qbuf.extend_from_slice(q.status.as_bytes());
        if qbuf.len() > Q_FRAGMENT_AT {
            out.push((P_FETCH_CHARACTER, prefixed(b"Q", &qbuf)));
            qbuf.clear();
        }
    }
    if !qbuf.is_empty() {
        out.push((P_FETCH_CHARACTER, prefixed(b"Q", &qbuf)));
    }

    // F — final counts.
    let mut f = b"F".to_vec();
    f.extend_from_slice(&num.to_le_bytes());
    f.extend_from_slice(&spells_done.to_le_bytes());
    out.push((P_FETCH_CHARACTER, f));

    throttle.record(peer, true, now_ms);
    out
}

fn prefixed(tag: &[u8], body: &[u8]) -> Vec<u8> {
    let mut p = Vec::with_capacity(tag.len() + body.len());
    p.extend_from_slice(tag);
    p.extend_from_slice(body);
    p
}

#[cfg(test)]
mod tests {
    use super::*;
    use rcce_server_accounts::store::Account;
    use std::path::PathBuf;

    const MD5: &str = "5d41402abc4b2a76b9719d911017c592";

    fn data_dir() -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../data")
    }

    fn tmp_store(name: &str) -> AccountStore {
        let mut p = std::env::temp_dir();
        p.push(format!("rcce_createchar_test_{name}.dat"));
        let _ = std::fs::remove_file(&p);
        AccountStore::load(&p).unwrap()
    }

    fn field(b: &[u8]) -> Vec<u8> {
        let mut v = vec![b.len() as u8];
        v.extend_from_slice(b);
        v
    }

    fn config_for(dir: PathBuf) -> ServerConfig {
        ServerConfig {
            port: 25000,
            allow_account_creation: true,
            max_account_chars: 4,
            start_gold: 5000,
            start_reputation: 50,
            attribute_assignment: 0,
            data_dir: dir,
        }
    }

    /// Build a P_CreateCharacter payload body (after user/pass).
    fn create_packet(user: &str, md5: &str, actor_id: u16, name: &[u8]) -> Vec<u8> {
        let mut p = field(user.as_bytes());
        p.extend_from_slice(&field(md5.as_bytes()));
        p.extend_from_slice(&actor_id.to_le_bytes()); // u16 LE
        p.extend_from_slice(&[0u8, 0, 0, 0, 0]); // gender, face, hair, beard, body
        p.extend_from_slice(&[0u8; ATTR_POINT_BYTES]); // 40 attribute points
        p.extend_from_slice(name);
        p
    }

    #[test]
    fn create_character_against_real_data() {
        let dir = data_dir();
        let catalog = ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        if catalog.is_empty() {
            eprintln!("skipping: no Actors.dat");
            return;
        }
        // Pick a playable race that has a loadable start area.
        let template = catalog.templates.values().find(|t| {
            t.playable && Area::load(&dir, &t.start_area).is_some()
        });
        let Some(template) = template else {
            eprintln!("skipping: no playable race with a loadable start area");
            return;
        };
        let actor_id = template.id;

        let mut store = tmp_store("real");
        store.push(Account::new("hero", MD5, "h@x.com").unwrap());
        let mut throttle = LoginThrottle::new();
        let config = config_for(dir.clone());

        let pkt = create_packet("hero", MD5, actor_id, b"Aragorn");
        let (t, body) = handle_create_character(&pkt, &mut store, &mut throttle, &catalog, &config, 1, 0);
        assert_eq!(t, P_CREATE_CHARACTER);
        assert_eq!(body, b"Y", "creation should succeed for a valid playable race");

        let acct = store.find("hero").unwrap();
        assert_eq!(acct.characters.len(), 1);
        let ch = &acct.characters[0].actor;
        assert_eq!(ch.name, "Aragorn");
        assert_eq!(ch.actor_id, actor_id);
        assert_eq!(ch.gold, 5000);
        assert_eq!(ch.reputation, 50);
        assert_eq!(ch.area, template.start_area);
        // Persisted + reloads with the character intact.
        let reloaded = AccountStore::load({
            let mut p = std::env::temp_dir();
            p.push("rcce_createchar_test_real.dat");
            p
        })
        .unwrap();
        assert_eq!(reloaded.find("hero").unwrap().characters[0].actor.name, "Aragorn");
    }

    #[test]
    fn invalid_name_is_i() {
        let dir = data_dir();
        let catalog = ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let actor_id = catalog.templates.keys().copied().next().unwrap_or(0);
        let mut store = tmp_store("badname");
        store.push(Account::new("hero", MD5, "h@x.com").unwrap());
        let mut throttle = LoginThrottle::new();
        let config = config_for(dir);
        // Name with a control byte (newline) → invalid charset.
        let pkt = create_packet("hero", MD5, actor_id, b"Bad\nName");
        assert_eq!(
            handle_create_character(&pkt, &mut store, &mut throttle, &catalog, &config, 1, 0).1,
            b"I"
        );
    }

    #[test]
    fn bad_actor_id_is_n() {
        let dir = data_dir();
        let catalog = ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let mut store = tmp_store("badactor");
        store.push(Account::new("hero", MD5, "h@x.com").unwrap());
        let mut throttle = LoginThrottle::new();
        let config = config_for(dir);
        // 65000 is not a real race id.
        let pkt = create_packet("hero", MD5, 65000, b"Legolas");
        assert_eq!(
            handle_create_character(&pkt, &mut store, &mut throttle, &catalog, &config, 1, 0).1,
            b"N"
        );
    }

    #[test]
    fn fetch_character_returns_c1_stats_and_f_counts() {
        let dir = data_dir();
        let catalog = ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let template = catalog
            .templates
            .values()
            .find(|t| t.playable && Area::load(&dir, &t.start_area).is_some());
        let Some(template) = template else {
            eprintln!("skipping: no playable race with loadable start area");
            return;
        };
        let mut store = tmp_store("fetch");
        store.push(Account::new("hero", MD5, "h@x.com").unwrap());
        let mut throttle = LoginThrottle::new();
        let config = config_for(dir.clone());
        let create = create_packet("hero", MD5, template.id, b"Frodo");
        handle_create_character(&create, &mut store, &mut throttle, &catalog, &config, 1, 0);

        let mut fetch = field(b"hero");
        fetch.extend_from_slice(&field(MD5.as_bytes()));
        fetch.push(0u8); // slot 0
        let replies = handle_fetch_character(&fetch, &mut store, &mut throttle, 1, 0);

        // First packet is C1; gold (i32 LE after the 2-byte "C1" tag) is StartGold.
        assert_eq!(&replies[0].1[0..2], b"C1");
        let gold = i32::from_le_bytes(replies[0].1[2..6].try_into().unwrap());
        assert_eq!(gold, 5000);
        // A C3 inventory packet is present (46 empty slots → one "C3" frame).
        assert!(replies.iter().any(|(_, p)| p.starts_with(b"C3")));
        // Last packet is F with quest-count 0 and spell-count 0.
        let last = replies.last().unwrap();
        assert_eq!(last.1[0], b'F');
        assert_eq!(&last.1[1..3], &[0, 0]); // num quests
        assert_eq!(&last.1[3..5], &[0, 0]); // spells done
    }

    #[test]
    fn fetch_bad_slot_is_n() {
        let mut store = tmp_store("fetchbad");
        store.push(Account::new("hero", MD5, "h@x.com").unwrap());
        let mut throttle = LoginThrottle::new();
        let mut fetch = field(b"hero");
        fetch.extend_from_slice(&field(MD5.as_bytes()));
        fetch.push(3u8); // slot 3, but account has no characters
        let replies = handle_fetch_character(&fetch, &mut store, &mut throttle, 1, 0);
        assert_eq!(replies, vec![(P_FETCH_CHARACTER, b"N".to_vec())]);
    }

    #[test]
    fn wrong_password_is_n() {
        let dir = data_dir();
        let catalog = ActorCatalog::load(dir.join("Server Data/Actors.dat"));
        let mut store = tmp_store("badpw");
        store.push(Account::new("hero", MD5, "h@x.com").unwrap());
        let mut throttle = LoginThrottle::new();
        let config = config_for(dir);
        let pkt = create_packet("hero", "ffffffffffffffffffffffffffffffff", 0, b"Gimli");
        assert_eq!(
            handle_create_character(&pkt, &mut store, &mut throttle, &catalog, &config, 1, 0).1,
            b"N"
        );
    }
}
