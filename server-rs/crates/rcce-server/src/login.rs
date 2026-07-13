//! Login / account packet handlers — parity with the `P_CreateAccount` and
//! `P_VerifyAccount` cases in `ServerNet.bb` (~2365-2551).
//!
//! Wire payloads (all strings are 1-byte-length-prefixed; ints little-endian):
//! - `P_CreateAccount` in `[str user][str pass(md5)][str email(caesar)]`, out
//!   `"Y"` created / `"N"` invalid-or-dup / no reply if disabled.
//! - `P_VerifyAccount` in `[str user][str pass(md5)]`, out `"Y"`+charlist /
//!   `"P"` auth-fail / `"B"` banned / `"L"` online.
//!
//! Soft-fail throughout: a truncated/garbage packet never panics — it collapses
//! to the generic failure reply, matching the server's never-crash-on-wire-data
//! contract.

use rcce_net::codec::{MsgReader, MsgWriter};
use rcce_server_accounts::password;
use rcce_server_accounts::store::{Account, AccountStore};
use rcce_server_accounts::throttle::LoginThrottle;

/// `P_CreateAccount` / `P_VerifyAccount` / `P_DeleteCharacter` type bytes
/// (`Packets.bb`).
pub const P_CREATE_ACCOUNT: u8 = 1;
pub const P_VERIFY_ACCOUNT: u8 = 2;
pub const P_CHANGE_PASSWORD: u8 = 6;
pub const P_DELETE_CHARACTER: u8 = 5;

/// Build the character-select list bytes for an account — per character:
/// `[u8 nameLen][name][u16 actorId][u8 gender][u8 face][u8 hair][u8 beard][u8 body]`
/// (`ServerNet.bb:2541-2544` / `3022-3027`). No prefix; callers prepend `"Y"`
/// for `P_VerifyAccount` and send it bare for `P_DeleteCharacter`.
pub fn char_summary(account: &Account) -> Vec<u8> {
    let mut w = MsgWriter::new();
    for rec in &account.characters {
        let a = &rec.actor;
        // Names are server-validated to <=32 printable ASCII bytes at creation.
        w.str8(&a.name);
        w.u16(a.actor_id);
        w.u8(a.gender);
        w.u8(a.face_tex as u8);
        w.u8(a.hair as u8);
        w.u8(a.beard as u8);
        w.u8(a.body_tex as u8);
    }
    w.into_bytes()
}

/// Read one 1-byte-length-prefixed field as raw bytes (avoids premature UTF-8
/// mangling of the Caesar-encrypted email / extended-byte usernames).
fn read_field(r: &mut MsgReader) -> Option<Vec<u8>> {
    let n = r.u8()? as usize;
    Some(r.bytes(n)?.to_vec())
}

/// Peek the leading username field of a packet (the first 1-byte-len field) —
/// used to resolve the owning session before dispatching `P_ChangePassword`.
pub fn peek_user(payload: &[u8]) -> Option<String> {
    let mut r = MsgReader::new(payload);
    read_field(&mut r).map(|u| String::from_utf8_lossy(&u).into_owned())
}

/// Decrypt the email field — `Encrypt$(s, 1)` in `Server.bb:842`: reverse the
/// byte order and add 26 to each byte (the inverse of the client's encrypt).
fn decrypt_email(enc: &[u8]) -> Vec<u8> {
    enc.iter().rev().map(|b| b.wrapping_add(26)).collect()
}

/// Username charset: `[0-9 A-Z a-z _]` or byte >= 192 (`ServerNet.bb:2390`).
fn valid_username_byte(c: u8) -> bool {
    c.is_ascii_digit() || c.is_ascii_uppercase() || c.is_ascii_lowercase() || c == b'_' || c >= 192
}

/// Password charset: username set plus `.` (`ServerNet.bb:2395`).
fn valid_password_byte(c: u8) -> bool {
    valid_username_byte(c) || c == b'.'
}

/// Email charset: `[0-9 @ A-Z a-z * + - . = _]` or byte >= 192
/// (`ServerNet.bb:2400`; note 64-90 covers `@` and `A-Z`).
fn valid_email_byte(c: u8) -> bool {
    c.is_ascii_digit()
        || (64..=90).contains(&c)
        || c.is_ascii_lowercase()
        || matches!(c, b'*' | b'+' | b'-' | b'.' | b'=' | b'_')
        || c >= 192
}

/// Handle `P_CreateAccount`. Returns the reply `(type, payload)`, or `None` when
/// account creation is disabled (the Blitz handler sends nothing in that case).
/// On success the account is appended and persisted via [`AccountStore::save`].
pub fn handle_create_account(
    payload: &[u8],
    store: &mut AccountStore,
    allow_account_creation: bool,
) -> Option<(u8, Vec<u8>)> {
    if !allow_account_creation {
        return None;
    }
    let deny = || Some((P_CREATE_ACCOUNT, b"N".to_vec()));

    let mut r = MsgReader::new(payload);
    let (Some(user), Some(pass), Some(email_enc)) =
        (read_field(&mut r), read_field(&mut r), read_field(&mut r))
    else {
        return deny(); // truncated packet → invalid
    };
    let email = decrypt_email(&email_enc);

    let user_s = String::from_utf8_lossy(&user).into_owned();
    if store.exists(&user_s) {
        return deny();
    }
    let valid = user.iter().all(|&c| valid_username_byte(c))
        && pass.iter().all(|&c| valid_password_byte(c))
        && email.iter().all(|&c| valid_email_byte(c))
        && user.len() <= 50
        && pass.len() <= 50
        && email.len() <= 200;
    if !valid {
        return deny();
    }

    let pass_s = String::from_utf8_lossy(&pass).into_owned();
    let email_s = String::from_utf8_lossy(&email).into_owned();
    match Account::new(&user_s, &pass_s, &email_s) {
        Ok(account) => {
            if let Err(e) = store.transaction(|store| store.push(account)) {
                eprintln!("[login] AddAccount: save failed: {e}");
                return deny();
            }
            Some((P_CREATE_ACCOUNT, b"Y".to_vec()))
        }
        // RNG failure during salt generation — treat as transient failure.
        Err(_) => deny(),
    }
}

/// Handle `P_VerifyAccount` (login). Returns the reply `(type, payload)`.
///
/// `now_ms` is a monotonic millisecond clock for the throttle. Session/online
/// gating (`"L"`) is deferred until `P_StartGame` tracks live sessions; until
/// then a verified account always returns its (currently always-empty) character
/// list.
pub fn handle_verify_account(
    payload: &[u8],
    store: &mut AccountStore,
    throttle: &mut LoginThrottle,
    peer: u32,
    now_ms: u64,
) -> (u8, Vec<u8>) {
    let p = |bytes: &[u8]| (P_VERIFY_ACCOUNT, bytes.to_vec());

    let mut r = MsgReader::new(payload);
    let Some(user) = read_field(&mut r) else {
        // Truncated before the username — pay the hash cost, record, fail.
        password::verify_password("", "");
        throttle.record(peer, false, now_ms);
        return p(b"P");
    };
    // Missing/empty password field is allowed to decode to empty (plen<1 path).
    let pass = read_field(&mut r).unwrap_or_default();

    if !throttle.ok(peer, now_ms) {
        return p(b"P");
    }

    let user_s = String::from_utf8_lossy(&user).into_owned();
    let pass_s = String::from_utf8_lossy(&pass).into_owned();

    // Find account without disclosing the result yet (auth-before-disclosure).
    let found = store.find(&user_s).cloned();
    if found.is_none() || pass.is_empty() {
        // No account / truncated password → pay the dummy hash, record, "P".
        password::verify_password("", &pass_s);
        throttle.record(peer, false, now_ms);
        return p(b"P");
    }
    let acct = found.unwrap();

    let pwd_ok = !acct.pass.is_empty() && password::verify_password(&acct.pass, &pass_s);
    if !pwd_ok {
        throttle.record(peer, false, now_ms);
        return p(b"P");
    }
    if acct.is_banned {
        // Only disclosed after the password verifies.
        throttle.record(peer, false, now_ms);
        return p(b"B");
    }
    // (Online "L" check deferred — no live-session tracking yet.)

    // Success.
    throttle.record(peer, true, now_ms);

    // Lazy-migrate a legacy MD5 record to v1 only when the atomic save can
    // commit it. Login itself remains successful when this optional upgrade
    // cannot persist.
    if password::password_is_legacy(&acct.pass) {
        if let Ok(upgraded) = password::upgrade_password_if_legacy(&acct.pass, &pass_s) {
            if store.find(&user_s).is_some() {
                if let Err(e) = store.transaction(|store| {
                    store
                        .find_mut(&user_s)
                        .expect("account verified above")
                        .pass = upgraded;
                }) {
                    eprintln!("[login] password upgrade: save failed: {e}");
                }
            }
        }
    }

    // Character list: "Y" + per-character summary. A fresh account has none, so
    // the client shows an empty character-select; once P_CreateCharacter lands,
    // its characters enumerate here.
    let mut payload = vec![b'Y'];
    payload.extend_from_slice(&char_summary(&acct));
    (P_VERIFY_ACCOUNT, payload)
}

/// Handle `P_ChangePassword` (`ServerNet.bb:2554`). Inbound:
/// `[str user][str oldPass(md5)][str newPass(md5)]`. Verifies the old password
/// **and** that the requester owns the account's live session (`owner_peer` =
/// the peer currently logged into this account, if any), then stores the new
/// password in the v1 salted format. Reply `"Y"` on success, `"P"` otherwise.
///
/// Auth-before-disclosure: an unknown account, a wrong old password, or a
/// requester who isn't the logged-in owner all return the **same** `"P"` so the
/// reply can't enumerate accounts (parity with the Blitz fix at `:2596`).
pub fn handle_change_password(
    payload: &[u8],
    store: &mut AccountStore,
    requester_peer: u32,
    owner_peer: Option<u32>,
) -> (u8, Vec<u8>) {
    let p = (P_CHANGE_PASSWORD, b"P".to_vec());
    let mut r = MsgReader::new(payload);
    let (Some(user), Some(old), Some(new)) =
        (read_field(&mut r), read_field(&mut r), read_field(&mut r))
    else {
        password::verify_password("", "");
        return p;
    };
    let user_s = String::from_utf8_lossy(&user).into_owned();
    let old_s = String::from_utf8_lossy(&old).into_owned();
    let new_s = String::from_utf8_lossy(&new).into_owned();

    let Some(acct) = store.find(&user_s).cloned() else {
        password::verify_password("", &old_s); // timing-uniform
        return p;
    };
    // Old password must verify, the new password must be non-empty, and the
    // requester must be the account's currently-logged-in session.
    let pwd_ok = !acct.pass.is_empty() && password::verify_password(&acct.pass, &old_s);
    let owns_session = owner_peer == Some(requester_peer);
    if !pwd_ok || new_s.is_empty() || !owns_session {
        return p;
    }
    let Ok(hashed) = password::hash_password(&new_s) else {
        return p;
    };
    if let Err(e) = store.transaction(|store| {
        let account = store.find_mut(&user_s).expect("account verified above");
        account.pass = hashed;
    }) {
        eprintln!("[login] change-password: save failed: {e}");
        return p;
    }
    (P_CHANGE_PASSWORD, b"Y".to_vec())
}

/// Handle `P_DeleteCharacter` (`ServerNet.bb:2972-3054`). Inbound:
/// `[str user][str pass][u8 slot]`. On success deletes the slot, shifts the
/// remaining characters down, persists, and replies with the **new character
/// list (no `"Y"` prefix)**; otherwise `"N"`.
///
/// Deferred vs. Blitz: the `RequesterOwnsAccountSession` gate is omitted because
/// no live in-world sessions exist yet (it would always fail). Re-add it with
/// `P_StartGame` session tracking — until then this gates on password only.
pub fn handle_delete_character(
    payload: &[u8],
    store: &mut AccountStore,
    throttle: &mut LoginThrottle,
    peer: u32,
    now_ms: u64,
) -> (u8, Vec<u8>) {
    let deny = (P_DELETE_CHARACTER, b"N".to_vec());

    let mut r = MsgReader::new(payload);
    let Some(user) = read_field(&mut r) else {
        password::verify_password("", "");
        throttle.record(peer, false, now_ms);
        return deny;
    };
    let pass = read_field(&mut r).unwrap_or_default();
    let slot = r.u8(); // Blitz reads Asc(""); we require the byte to be present.

    if !throttle.ok(peer, now_ms) {
        return deny;
    }
    let user_s = String::from_utf8_lossy(&user).into_owned();
    let pass_s = String::from_utf8_lossy(&pass).into_owned();

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

    // Slot bounds (`Number > -1 And Number < 10`). A missing slot byte or an
    // out-of-range slot replies "N" without recording a throttle failure
    // (matching the Blitz slot-invalid branch).
    let Some(slot) = slot else {
        return deny;
    };
    if slot >= 10 {
        return deny;
    }

    throttle.record(peer, true, now_ms);
    let summary = match store.transaction(|store| {
        let account = store.find_mut(&user_s).expect("account found above");
        // Characters are stored compacted in slot order, so removing index
        // `slot` shifts the rest down exactly like the Blitz slot shift.
        // Deleting an empty slot is a no-op (Blitz guards it with Null).
        if (slot as usize) < account.characters.len() {
            account.characters.remove(slot as usize);
        }
        char_summary(account)
    }) {
        Ok(summary) => summary,
        Err(e) => {
            eprintln!("[login] DeleteCharacter: save failed: {e}");
            return deny;
        }
    };
    (P_DELETE_CHARACTER, summary)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn tmp_store(name: &str) -> AccountStore {
        let mut p = std::env::temp_dir();
        p.push(format!("rcce_login_test_{name}.dat"));
        let _ = std::fs::remove_file(&p);
        // Load from the (absent) path so the store remembers it for save().
        AccountStore::load(&p).unwrap()
    }

    /// A path whose parent does not exist makes `AccountStore::save` fail
    /// without relying on host permissions or a full disk.
    fn failing_store(name: &str) -> AccountStore {
        let mut parent = std::env::temp_dir();
        parent.push(format!("rcce_login_save_failure_{name}_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&parent);
        AccountStore::load(parent.join("Accounts.dat")).unwrap()
    }

    /// Encode a 1-byte-length-prefixed field.
    fn field(b: &[u8]) -> Vec<u8> {
        let mut v = vec![b.len() as u8];
        v.extend_from_slice(b);
        v
    }

    const MD5_HELLO: &str = "5d41402abc4b2a76b9719d911017c592";

    fn legacy_account() -> Account {
        Account {
            user: "hero".into(),
            pass: MD5_HELLO.into(),
            email: "h@x.com".into(),
            is_dm: false,
            is_banned: false,
            ignore: String::new(),
            characters: Vec::new(),
        }
    }

    #[test]
    fn create_then_verify_succeeds() {
        let mut store = tmp_store("create_verify");
        let mut throttle = LoginThrottle::new();

        // Email "a@b.com" Caesar-encrypted the way the client sends it: the
        // server decrypts via reverse + (+26), so the client form is
        // reverse + (-26). Build it so decrypt_email recovers "a@b.com".
        let email_plain = b"a@b.com";
        let enc: Vec<u8> = email_plain.iter().rev().map(|b| b.wrapping_sub(26)).collect();

        let mut create = Vec::new();
        create.extend_from_slice(&field(b"alice"));
        create.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        create.extend_from_slice(&field(&enc));
        let reply = handle_create_account(&create, &mut store, true).unwrap();
        assert_eq!(reply, (P_CREATE_ACCOUNT, b"Y".to_vec()));
        assert!(store.exists("alice"));
        // Email decrypted and stored correctly.
        assert_eq!(store.find("alice").unwrap().email, "a@b.com");

        // Now log in with the right password → "Y" (+ empty char list).
        let mut verify = Vec::new();
        verify.extend_from_slice(&field(b"alice"));
        verify.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        let (t, body) = handle_verify_account(&verify, &mut store, &mut throttle, 1, 0);
        assert_eq!(t, P_VERIFY_ACCOUNT);
        assert_eq!(body, b"Y"); // no characters yet

        // Wrong password → "P".
        let mut bad = Vec::new();
        bad.extend_from_slice(&field(b"alice"));
        bad.extend_from_slice(&field(b"ffffffffffffffffffffffffffffffff"));
        let (_, body) = handle_verify_account(&bad, &mut store, &mut throttle, 2, 0);
        assert_eq!(body, b"P");

        std::fs::remove_file(store_path("create_verify")).ok();
    }

    fn store_path(name: &str) -> PathBuf {
        let mut p = std::env::temp_dir();
        p.push(format!("rcce_login_test_{name}.dat"));
        p
    }

    #[test]
    fn verify_unknown_account_is_p() {
        let mut store = tmp_store("unknown");
        let mut throttle = LoginThrottle::new();
        let mut verify = Vec::new();
        verify.extend_from_slice(&field(b"nobody"));
        verify.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        let (_, body) = handle_verify_account(&verify, &mut store, &mut throttle, 1, 0);
        assert_eq!(body, b"P");
    }

    #[test]
    fn verify_truncated_packet_is_p_no_panic() {
        let mut store = tmp_store("trunc");
        let mut throttle = LoginThrottle::new();
        // Empty payload, and a length byte that overruns.
        assert_eq!(handle_verify_account(&[], &mut store, &mut throttle, 1, 0).1, b"P");
        assert_eq!(handle_verify_account(&[5, b'a'], &mut store, &mut throttle, 1, 0).1, b"P");
    }

    #[test]
    fn create_disabled_no_reply() {
        let mut store = tmp_store("disabled");
        let create = field(b"bob");
        assert!(handle_create_account(&create, &mut store, false).is_none());
    }

    #[test]
    fn create_duplicate_is_n() {
        let mut store = tmp_store("dup");
        store.push(Account::new("dave", MD5_HELLO, "d@e.com").unwrap());
        let enc: Vec<u8> = b"x@y.com".iter().rev().map(|b| b.wrapping_sub(26)).collect();
        let mut create = Vec::new();
        create.extend_from_slice(&field(b"DAVE")); // case-insensitive dup
        create.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        create.extend_from_slice(&field(&enc));
        assert_eq!(
            handle_create_account(&create, &mut store, true).unwrap().1,
            b"N"
        );
    }

    #[test]
    fn create_account_save_failure_returns_n_and_rolls_back() {
        let mut store = failing_store("create");
        let enc: Vec<u8> = b"a@b.com".iter().rev().map(|b| b.wrapping_sub(26)).collect();
        let mut create = field(b"alice");
        create.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        create.extend_from_slice(&field(&enc));

        assert_eq!(handle_create_account(&create, &mut store, true).unwrap().1, b"N");
        assert!(!store.exists("alice"));
    }

    fn account_with_chars(user: &str, chars: &[(u16, &str)]) -> rcce_server_accounts::store::Account {
        use rcce_server_core::character::Character;
        use rcce_server_core::record::CharacterRecord;
        let mut acct = Account::new(user, MD5_HELLO, "h@x.com").unwrap();
        for &(id, name) in chars {
            let mut c = Character::blank();
            c.actor_id = id;
            c.name = name.to_string();
            acct.characters.push(CharacterRecord::new(c));
        }
        acct
    }

    #[test]
    fn verify_returns_character_list() {
        use rcce_server_core::character::Character;
        use rcce_server_core::record::CharacterRecord;
        let mut store = tmp_store("verifylist");
        let mut acct = Account::new("hero", MD5_HELLO, "h@x.com").unwrap();
        let mut c = Character::blank();
        c.actor_id = 0x0102;
        c.name = "Xy".into();
        c.gender = 1;
        c.face_tex = 2;
        c.hair = 3;
        c.beard = 4;
        c.body_tex = 0;
        acct.characters.push(CharacterRecord::new(c));
        store.push(acct);

        let mut throttle = LoginThrottle::new();
        let mut verify = field(b"hero");
        verify.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        let (_, body) = handle_verify_account(&verify, &mut store, &mut throttle, 1, 0);
        assert_eq!(body[0], b'Y');
        assert_eq!(body[1], 2); // name length
        assert_eq!(&body[2..4], b"Xy");
        assert_eq!(&body[4..6], &[0x02, 0x01]); // u16 0x0102 little-endian
        assert_eq!(&body[6..11], &[1, 2, 3, 4, 0]); // gender,face,hair,beard,body
    }

    #[test]
    fn delete_character_removes_slot_and_returns_list() {
        let mut store = tmp_store("delete");
        store.push(account_with_chars("hero", &[(5, "Aaa"), (6, "Bbb")]));
        let mut throttle = LoginThrottle::new();

        let mut del = field(b"hero");
        del.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        del.push(0u8); // delete slot 0
        let (t, body) = handle_delete_character(&del, &mut store, &mut throttle, 1, 0);
        assert_eq!(t, P_DELETE_CHARACTER);
        // The remaining character "Bbb" only — no "Y" prefix on delete replies.
        assert_eq!(body[0], 3); // name length
        assert_eq!(&body[1..4], b"Bbb");
        assert_eq!(store.find("hero").unwrap().characters.len(), 1);
        assert_eq!(store.find("hero").unwrap().characters[0].actor.name, "Bbb");
    }

    #[test]
    fn delete_character_save_failure_returns_n_and_rolls_back() {
        let mut store = failing_store("delete");
        store.push(account_with_chars("hero", &[(5, "Aaa")]));
        let mut throttle = LoginThrottle::new();
        let mut del = field(b"hero");
        del.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        del.push(0);

        assert_eq!(handle_delete_character(&del, &mut store, &mut throttle, 1, 0).1, b"N");
        assert_eq!(store.find("hero").unwrap().characters.len(), 1);
    }

    #[test]
    fn delete_wrong_password_is_n_and_keeps_chars() {
        let mut store = tmp_store("delete_bad");
        store.push(account_with_chars("hero", &[(5, "Aaa")]));
        let mut throttle = LoginThrottle::new();
        let mut del = field(b"hero");
        del.extend_from_slice(&field(b"ffffffffffffffffffffffffffffffff"));
        del.push(0u8);
        let (_, body) = handle_delete_character(&del, &mut store, &mut throttle, 1, 0);
        assert_eq!(body, b"N");
        assert_eq!(store.find("hero").unwrap().characters.len(), 1);
    }

    #[test]
    fn delete_bad_slot_is_n() {
        let mut store = tmp_store("delete_slot");
        store.push(account_with_chars("hero", &[(5, "Aaa")]));
        let mut throttle = LoginThrottle::new();
        let mut del = field(b"hero");
        del.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        del.push(10u8); // slot out of range (>=10)
        assert_eq!(handle_delete_character(&del, &mut store, &mut throttle, 1, 0).1, b"N");
        assert_eq!(store.find("hero").unwrap().characters.len(), 1);
    }

    #[test]
    fn create_invalid_username_is_n() {
        let mut store = tmp_store("invalid");
        // Space (0x20) is not a valid username byte.
        let enc: Vec<u8> = b"x@y.com".iter().rev().map(|b| b.wrapping_sub(26)).collect();
        let mut create = Vec::new();
        create.extend_from_slice(&field(b"bad name"));
        create.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        create.extend_from_slice(&field(&enc));
        assert_eq!(
            handle_create_account(&create, &mut store, true).unwrap().1,
            b"N"
        );
    }

    #[test]
    fn change_password_save_failure_returns_p_and_rolls_back() {
        let mut store = failing_store("password");
        store.push(Account::new("hero", MD5_HELLO, "h@x.com").unwrap());
        let old_hash = store.find("hero").unwrap().pass.clone();
        let mut change = field(b"hero");
        change.extend_from_slice(&field(MD5_HELLO.as_bytes()));
        change.extend_from_slice(&field(b"ffffffffffffffffffffffffffffffff"));

        assert_eq!(handle_change_password(&change, &mut store, 7, Some(7)).1, b"P");
        assert_eq!(store.find("hero").unwrap().pass, old_hash);
    }

    #[test]
    fn legacy_password_upgrade_save_failure_keeps_legacy_hash() {
        let mut store = failing_store("legacy_upgrade");
        store.push(legacy_account());
        let mut throttle = LoginThrottle::new();
        let mut verify = field(b"hero");
        verify.extend_from_slice(&field(MD5_HELLO.as_bytes()));

        assert_eq!(
            handle_verify_account(&verify, &mut store, &mut throttle, 1, 0).1,
            b"Y"
        );
        assert_eq!(store.find("hero").unwrap().pass, MD5_HELLO);
    }

    #[test]
    fn legacy_password_upgrade_persists_after_successful_login() {
        let mut store = tmp_store("legacy_upgrade_persists");
        store.push(legacy_account());
        store.save().unwrap();
        let mut throttle = LoginThrottle::new();
        let mut verify = field(b"hero");
        verify.extend_from_slice(&field(MD5_HELLO.as_bytes()));

        assert_eq!(
            handle_verify_account(&verify, &mut store, &mut throttle, 1, 0).1,
            b"Y"
        );
        let persisted = AccountStore::load(store_path("legacy_upgrade_persists")).unwrap();
        let hash = &persisted.find("hero").unwrap().pass;
        assert!(hash.starts_with("$1$"));
        assert!(password::verify_password(hash, MD5_HELLO));
        std::fs::remove_file(store_path("legacy_upgrade_persists")).ok();
    }
}
