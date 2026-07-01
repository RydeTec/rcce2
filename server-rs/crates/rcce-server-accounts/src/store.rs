//! Account model + flat-file persistence — parity with `AccountsServer.bb`
//! `SaveAccounts` / `LoadAccounts` and the `Accounts.dat` on-disk format.
//!
//! On-disk layout (v1), all integers/lengths **little-endian** (Blitz file
//! convention), strings = 4-byte-LE length prefix + raw bytes (`WriteString`):
//! ```text
//! [u32 magic 0x41434354 "ACCT"][u8 version=1]
//! repeated per account:
//!   [str User][str Pass][str Email][u8 IsDM][u8 IsBanned][str Ignore][u8 CharCount]
//!   CharCount × CharacterRecord( ActorInstance + 500×Quest + 36×ActionBar )
//! ```
//! A file with no magic header is legacy **v0** (same per-account record, no
//! header, and no per-character portal triad). `ReadBoundedString` caps every
//! length so a corrupt prefix can't trigger a giant allocation. The codec is the
//! shared `rcce-server-core::blitz_io` (one source of byte-layout truth).

use std::io::{self, Write};
use std::path::{Path, PathBuf};

use rcce_server_core::blitz_io::{Reader, Writer};
use rcce_server_core::record::{read_record, write_record, CharacterRecord};

use crate::password;

/// `ACCOUNTS_MAGIC` — `"ACCT"` as `0x41434354` (little-endian u32 on disk).
const ACCOUNTS_MAGIC: u32 = 0x4143_4354;
/// `ACCOUNTS_VERSION_CURRENT`.
const ACCOUNTS_VERSION_CURRENT: u8 = 1;

/// Caps mirroring `ReadBoundedString$` call sites in `LoadAccounts`.
const MAX_USER_PASS_EMAIL: u32 = 256;
const MAX_IGNORE: u32 = 4096;
/// `If Chars > 10 Then Chars = 10`.
const MAX_CHARS: u8 = 10;

/// `LoginAttemptMaxFailures` / `LoginAttemptWindowMs`.
pub const LOGIN_ATTEMPT_MAX_FAILURES: u32 = 5;
pub const LOGIN_ATTEMPT_WINDOW_MS: u64 = 60_000;

/// One account record. `logged_on` is runtime state (always reset to `-1` on
/// load, like `AccountsServer.bb:331`) and is not persisted.
#[derive(Clone, Debug, PartialEq)]
pub struct Account {
    pub user: String,
    /// Stored password record: `$1$<salt>$<hash>` (v1) or legacy raw MD5.
    pub pass: String,
    pub email: String,
    pub is_dm: bool,
    pub is_banned: bool,
    pub ignore: String,
    /// Characters in slot order (index = character slot, 0..=9).
    pub characters: Vec<CharacterRecord>,
}

impl Account {
    /// Create an account from a client-supplied MD5 (the wire form). The stored
    /// password is wrapped in salted-SHA-256 v1, matching `AddAccount`.
    pub fn new(user: &str, client_md5: &str, email: &str) -> Result<Self, getrandom::Error> {
        Ok(Self {
            user: user.to_string(),
            pass: password::hash_password(client_md5)?,
            email: email.to_string(),
            is_dm: false,
            is_banned: false,
            ignore: String::new(),
            characters: Vec::new(),
        })
    }
}

/// In-memory account set with flat-file persistence.
#[derive(Debug, Default)]
pub struct AccountStore {
    accounts: Vec<Account>,
    path: PathBuf,
}

impl AccountStore {
    /// Load accounts from `Accounts.dat` at `path`. Missing/empty file → empty
    /// store. Never panics on a corrupt file — it stops at the first unreadable
    /// record, keeping whatever parsed cleanly.
    pub fn load(path: impl AsRef<Path>) -> io::Result<Self> {
        let path = path.as_ref().to_path_buf();
        let bytes = match std::fs::read(&path) {
            Ok(b) => b,
            Err(e) if e.kind() == io::ErrorKind::NotFound => Vec::new(),
            Err(e) => return Err(e),
        };
        let mut accounts = Vec::new();
        if !bytes.is_empty() {
            Self::parse_into(&bytes, &mut accounts);
        }
        Ok(Self { accounts, path })
    }

    /// Parse the file bytes, appending each cleanly-read account. Stops
    /// (soft-fail) at the first malformed account/character record.
    fn parse_into(bytes: &[u8], out: &mut Vec<Account>) {
        let mut r = Reader::new(bytes);
        // Header: magic + version (v1), else the stream is legacy v0 starting at
        // byte 0 with no portal triad in character records.
        let has_portal_triad = if bytes.len() >= 5
            && u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]) == ACCOUNTS_MAGIC
        {
            r.u32();
            let version = r.u8().unwrap_or(0);
            if version > ACCOUNTS_VERSION_CURRENT {
                return; // unknown future format — abort, like LoadAccounts
            }
            version >= 1
        } else {
            false // v0; reader remains at offset 0
        };

        while !r.at_end() {
            let Some(user) = r.string(MAX_USER_PASS_EMAIL) else {
                break;
            };
            let (Some(pass), Some(email), Some(is_dm), Some(is_banned), Some(ignore), Some(chars)) = (
                r.string(MAX_USER_PASS_EMAIL),
                r.string(MAX_USER_PASS_EMAIL),
                r.u8(),
                r.u8(),
                r.string(MAX_IGNORE),
                r.u8(),
            ) else {
                break;
            };
            let chars = chars.min(MAX_CHARS);
            let mut characters = Vec::with_capacity(chars as usize);
            let mut ok = true;
            for _ in 0..chars {
                match read_record(&mut r, has_portal_triad) {
                    Some(rec) => characters.push(rec),
                    None => {
                        ok = false;
                        break;
                    }
                }
            }
            out.push(Account {
                user,
                pass,
                email,
                is_dm: is_dm != 0,
                is_banned: is_banned != 0,
                ignore,
                characters,
            });
            if !ok {
                break; // a truncated character block ends the load cleanly
            }
        }
    }

    /// Atomically persist all accounts as a v1 `Accounts.dat` (temp → fsync →
    /// `.bak` → rename), parity with `SaveAccounts` / `SafeWriteCommit`.
    pub fn save(&self) -> io::Result<()> {
        let mut w = Writer::new();
        w.u32(ACCOUNTS_MAGIC);
        w.u8(ACCOUNTS_VERSION_CURRENT);
        for a in &self.accounts {
            w.string(&a.user);
            w.string(&a.pass);
            w.string(&a.email);
            w.u8(a.is_dm as u8);
            w.u8(a.is_banned as u8);
            w.string(&a.ignore);
            let count = a.characters.len().min(MAX_CHARS as usize) as u8;
            w.u8(count);
            for rec in a.characters.iter().take(count as usize) {
                write_record(&mut w, rec);
            }
        }
        atomic_write(&self.path, &w.into_bytes())
    }

    /// Case-insensitive lookup (`Upper$(User$)` scan in the handlers).
    pub fn find(&self, user: &str) -> Option<&Account> {
        self.accounts.iter().find(|a| a.user.eq_ignore_ascii_case(user))
    }

    pub fn find_mut(&mut self, user: &str) -> Option<&mut Account> {
        self.accounts.iter_mut().find(|a| a.user.eq_ignore_ascii_case(user))
    }

    /// True if an account with this username (case-insensitive) already exists.
    pub fn exists(&self, user: &str) -> bool {
        self.find(user).is_some()
    }

    /// Append a new account. Does not persist — call [`save`](Self::save).
    pub fn push(&mut self, account: Account) {
        self.accounts.push(account);
    }

    pub fn len(&self) -> usize {
        self.accounts.len()
    }

    pub fn is_empty(&self) -> bool {
        self.accounts.is_empty()
    }

    pub fn iter(&self) -> impl Iterator<Item = &Account> {
        self.accounts.iter()
    }
}

/// Atomic file replace: write `data` to `<path>.tmp`, fsync, promote current to
/// `<path>.bak`, rename temp to `path`. Parity with `SafeWriteOpen`/`Commit`.
fn atomic_write(path: &Path, data: &[u8]) -> io::Result<()> {
    let tmp = path_with_suffix(path, ".tmp");
    {
        let mut f = std::fs::File::create(&tmp)?;
        f.write_all(data)?;
        f.sync_all()?;
    }
    if path.exists() {
        // Best-effort backup, matching SafeWriteCommit's intent (the temp is the
        // source of truth; a failed backup rename must not abort the commit).
        let _ = std::fs::rename(path, path_with_suffix(path, ".bak"));
    }
    std::fs::rename(&tmp, path)
}

fn path_with_suffix(path: &Path, suffix: &str) -> PathBuf {
    let mut s = path.as_os_str().to_os_string();
    s.push(suffix);
    PathBuf::from(s)
}

#[cfg(test)]
mod tests {
    use super::*;
    use rcce_server_core::character::Character;

    fn tmp_path(name: &str) -> PathBuf {
        let mut p = std::env::temp_dir();
        p.push(format!("rcce_accounts_test_{name}.dat"));
        let _ = std::fs::remove_file(&p);
        let _ = std::fs::remove_file(path_with_suffix(&p, ".bak"));
        p
    }

    const MD5: &str = "5d41402abc4b2a76b9719d911017c592";

    #[test]
    fn save_writes_v1_magic_header() {
        let path = tmp_path("magic");
        let mut store = AccountStore { accounts: vec![], path: path.clone() };
        store.push(Account::new("alice", MD5, "a@b.com").unwrap());
        store.save().unwrap();
        let bytes = std::fs::read(&path).unwrap();
        assert_eq!(&bytes[0..4], &ACCOUNTS_MAGIC.to_le_bytes());
        assert_eq!(bytes[4], ACCOUNTS_VERSION_CURRENT);
        std::fs::remove_file(&path).ok();
    }

    #[test]
    fn save_then_load_roundtrip_no_chars() {
        let path = tmp_path("roundtrip");
        let a1 = Account::new("Alice", MD5, "alice@x.com").unwrap();
        let mut a2 = Account::new("bob", "00000000000000000000000000000000", "bob@y.com").unwrap();
        a2.is_dm = true;
        a2.is_banned = true;
        a2.ignore = "carol,dave".to_string();
        let store = AccountStore { accounts: vec![a1.clone(), a2.clone()], path: path.clone() };
        store.save().unwrap();
        let loaded = AccountStore::load(&path).unwrap();
        assert_eq!(loaded.iter().cloned().collect::<Vec<_>>(), vec![a1, a2]);
        std::fs::remove_file(&path).ok();
    }

    #[test]
    fn save_then_load_roundtrip_with_characters() {
        let path = tmp_path("withchars");
        let mut acct = Account::new("hero", MD5, "h@x.com").unwrap();
        let mut a = Character::blank();
        a.actor_id = 5;
        a.name = "Thorin".into();
        a.gold = 1234;
        a.level = 10;
        acct.characters.push(CharacterRecord::new(a));
        let mut a2 = Character::blank();
        a2.actor_id = 9;
        a2.name = "Mage".into();
        acct.characters.push(CharacterRecord::new(a2));

        let store = AccountStore { accounts: vec![acct.clone()], path: path.clone() };
        store.save().unwrap();
        let loaded = AccountStore::load(&path).unwrap();
        assert_eq!(loaded.len(), 1);
        let back = loaded.find("hero").unwrap();
        assert_eq!(back.characters.len(), 2);
        assert_eq!(back.characters[0].actor.name, "Thorin");
        assert_eq!(back.characters[0].actor.gold, 1234);
        assert_eq!(back.characters[1].actor.name, "Mage");
        assert_eq!(back, &acct);
        std::fs::remove_file(&path).ok();
    }

    #[test]
    fn load_missing_file_is_empty() {
        let loaded = AccountStore::load(tmp_path("missing_never_created")).unwrap();
        assert!(loaded.is_empty());
    }

    #[test]
    fn loaded_password_still_verifies() {
        let path = tmp_path("verify");
        let store = AccountStore {
            accounts: vec![Account::new("alice", MD5, "a@b.com").unwrap()],
            path: path.clone(),
        };
        store.save().unwrap();
        let loaded = AccountStore::load(&path).unwrap();
        let acct = loaded.find("ALICE").expect("case-insensitive find");
        assert!(password::verify_password(&acct.pass, MD5));
        assert!(!password::verify_password(&acct.pass, "ffffffffffffffffffffffffffffffff"));
        std::fs::remove_file(&path).ok();
    }

    #[test]
    fn corrupt_length_prefix_soft_fails() {
        let mut buf = Vec::new();
        buf.extend_from_slice(&ACCOUNTS_MAGIC.to_le_bytes());
        buf.push(ACCOUNTS_VERSION_CURRENT);
        buf.extend_from_slice(&0xFFFF_FFFFu32.to_le_bytes()); // absurd username length
        let mut out = Vec::new();
        AccountStore::parse_into(&buf, &mut out);
        assert!(out.is_empty());
    }

    #[test]
    fn exists_is_case_insensitive() {
        let store = AccountStore {
            accounts: vec![Account::new("Alice", "abc", "a@b.com").unwrap()],
            path: PathBuf::new(),
        };
        assert!(store.exists("alice"));
        assert!(store.exists("ALICE"));
        assert!(!store.exists("bob"));
    }
}
