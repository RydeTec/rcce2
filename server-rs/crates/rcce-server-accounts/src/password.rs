//! Password hashing — byte-for-byte parity with `src/Modules/PasswordHash.bb`.
//!
//! Storage formats (identical to the Blitz server, so `Accounts.dat` records
//! interoperate both directions):
//! - Legacy: `<32 lowercase hex>` — the raw MD5 the client sends on the wire.
//! - v1: `$1$<salt-16>$<sha256-64-hex>`, hash = `SHA256(salt_ascii ++ client_md5_ascii)`.
//!
//! [`verify_password`] accepts both; [`hash_password`] always emits v1;
//! [`upgrade_password_if_legacy`] performs the lazy on-login migration.
//!
//! The wire protocol is unchanged — the client still sends `MD5(password)` as a
//! 32-char hex string; this module only wraps the *at-rest* copy. (Wire replay
//! is still possible; that needs TLS — out of scope, same as the Blitz server.)

use sha2::{Digest, Sha256};

/// `PWHASH_VERSION_TAG$` — the v1 record prefix.
const VERSION_TAG: &[u8] = b"$1$";
/// `PWHASH_SALT_LEN%` — salt length in characters.
const SALT_LEN: usize = 16;
/// Total v1 record length: `"$1$"` (3) + salt (16) + `"$"` (1) + hash (64).
const V1_LEN: usize = 3 + SALT_LEN + 1 + 64;
/// `GenerateSalt$` alphabet — URL-safe, no `$`/whitespace so the record stays
/// splittable. Order matches the Blitz literal (upper, lower, digits); order is
/// irrelevant to correctness since selection is uniform-random.
const SALT_ALPHABET: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
/// `PWHASH_DUMMY_SALT$` — fixed salt for the timing-uniform dummy hash on the
/// no-account / malformed-record path. Never compared against a real record.
const DUMMY_SALT: &[u8] = b"rcce2_dummy_salt";

/// SHA-256 of `msg`, as a 64-char lowercase hex string. Matches `SHA256Hex$`.
pub fn sha256_hex(msg: &[u8]) -> String {
    let digest = Sha256::digest(msg);
    let mut out = String::with_capacity(64);
    for b in digest {
        // Lowercase hex, big-endian — identical to PWHASH_HexWord$.
        out.push(char::from_digit((b >> 4) as u32, 16).unwrap());
        out.push(char::from_digit((b & 0xf) as u32, 16).unwrap());
    }
    out
}

/// Constant-time byte-string equality — port of `ConstantTimeStrEq%`.
///
/// Iterates the full length of the longer input (no early exit on first
/// differing byte) and folds a length mismatch into the running diff, so the
/// time-to-result depends on neither the position of the first mismatch nor the
/// length relationship — the canonical hash-compare timing-oracle mitigation.
pub fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    let mut diff: u32 = if a.len() != b.len() { 1 } else { 0 };
    let max = a.len().max(b.len());
    for i in 0..max {
        let av = a.get(i).copied().unwrap_or(0) as u32;
        let bv = b.get(i).copied().unwrap_or(0) as u32;
        diff |= av ^ bv;
    }
    diff == 0
}

/// Generate a 16-char salt from `[A-Za-z0-9]` using the OS CSPRNG.
///
/// Returns `Err` only if the OS RNG is unavailable (effectively impossible on
/// the Linux container target). Rejection sampling avoids the modulo bias a
/// bare `byte % 62` would introduce.
pub fn generate_salt() -> Result<String, getrandom::Error> {
    let n = SALT_ALPHABET.len() as u8; // 62
    // Largest multiple of n that fits in a u8; bytes >= this are rejected so
    // every alphabet index is equally likely.
    let limit = 256 / n as u16 * n as u16; // 248
    let mut out = String::with_capacity(SALT_LEN);
    let mut buf = [0u8; 32];
    let mut idx = buf.len(); // force an initial refill
    while out.len() < SALT_LEN {
        if idx >= buf.len() {
            getrandom::getrandom(&mut buf)?;
            idx = 0;
        }
        let byte = buf[idx];
        idx += 1;
        if (byte as u16) < limit {
            out.push(SALT_ALPHABET[(byte % n) as usize] as char);
        }
    }
    Ok(out)
}

/// Produce a v1 storage record for a client-supplied MD5 hex string. Matches
/// `HashPassword$`. Hashes whatever bytes it is given (no MD5-shape validation,
/// same as the Blitz helper).
pub fn hash_password(client_md5: &str) -> Result<String, getrandom::Error> {
    let salt = generate_salt()?;
    let mut input = Vec::with_capacity(salt.len() + client_md5.len());
    input.extend_from_slice(salt.as_bytes());
    input.extend_from_slice(client_md5.as_bytes());
    Ok(format!("$1${salt}${}", sha256_hex(&input)))
}

/// Compare a stored record to an incoming client MD5. Accepts both legacy raw
/// MD5 and v1 `$1$<salt>$<hash>`. Port of `VerifyPassword%`.
///
/// Timing-uniformity contract (do not "optimize away" the dummy hash): every
/// call pays the SHA-256 cost regardless of input shape, and both compare paths
/// use [`constant_time_eq`]. Removing the dummy hash re-opens the no-account /
/// malformed-record timing oracle.
pub fn verify_password(stored: &str, client_md5: &str) -> bool {
    // Always pay the cost up front. Discard the result, but keep the
    // computation from being elided.
    let mut dummy_input = Vec::with_capacity(DUMMY_SALT.len() + client_md5.len());
    dummy_input.extend_from_slice(DUMMY_SALT);
    dummy_input.extend_from_slice(client_md5.as_bytes());
    let dummy = sha256_hex(&dummy_input);
    std::hint::black_box(&dummy);

    let sb = stored.as_bytes();

    // v1 stored format: $1$<salt-16>$<hash-64>.
    if sb.starts_with(VERSION_TAG) {
        if sb.len() != V1_LEN {
            return false;
        }
        let salt = &sb[3..3 + SALT_LEN]; // [3..19]
        if sb[3 + SALT_LEN] != b'$' {
            // separator at index 19
            return false;
        }
        let stored_hash = &sb[3 + SALT_LEN + 1..]; // [20..84], 64 bytes
        let mut input = Vec::with_capacity(salt.len() + client_md5.len());
        input.extend_from_slice(salt);
        input.extend_from_slice(client_md5.as_bytes());
        return constant_time_eq(sha256_hex(&input).as_bytes(), stored_hash);
    }

    // Legacy plain-MD5 record.
    if sb.is_empty() {
        return false;
    }
    constant_time_eq(sb, client_md5.as_bytes())
}

/// True iff `stored` is legacy raw-MD5 (and should upgrade on next save).
/// Port of `PasswordIsLegacy%`.
pub fn password_is_legacy(stored: &str) -> bool {
    !stored.is_empty() && !stored.as_bytes().starts_with(VERSION_TAG)
}

/// Migrate a *verified* legacy entry to v1. Returns the new record, or `stored`
/// unchanged if already v1 / empty. Port of `UpgradePasswordIfLegacy$`.
///
/// Only call after a successful [`verify_password`] against the same
/// `client_md5`, else an unverified credential gets stamped. Returns `Err` only
/// on RNG failure (see [`generate_salt`]).
pub fn upgrade_password_if_legacy(
    stored: &str,
    client_md5: &str,
) -> Result<String, getrandom::Error> {
    if password_is_legacy(stored) {
        hash_password(client_md5)
    } else {
        Ok(stored.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // --- SHA-256 known-answer vectors (ported from PasswordHashTest.bb, which
    // sources them from RFC 6234 / FIPS 180-4). Passing these proves the `sha2`
    // crate output is byte-identical to the Blitz hand-rolled SHA256Hex$.

    #[test]
    fn sha256_empty() {
        assert_eq!(
            sha256_hex(b""),
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
    }

    #[test]
    fn sha256_abc() {
        assert_eq!(
            sha256_hex(b"abc"),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }

    #[test]
    fn sha256_quickbrownfox() {
        assert_eq!(
            sha256_hex(b"The quick brown fox jumps over the lazy dog"),
            "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592"
        );
    }

    #[test]
    fn sha256_56byte_boundary() {
        assert_eq!(
            sha256_hex(b"abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
        );
    }

    #[test]
    fn sha256_one_block_long() {
        let msg = vec![b'a'; 100];
        assert_eq!(
            sha256_hex(&msg),
            "2816597888e4a0d3a36b82b83316ab32680eb8f00f8cd3b904d681246d285a0e"
        );
    }

    // --- v1 round-trip + legacy acceptance (PasswordHashTest.bb).

    const MD5_HELLO: &str = "5d41402abc4b2a76b9719d911017c592"; // md5("hello")

    #[test]
    fn hashpassword_roundtrip() {
        let stored = hash_password(MD5_HELLO).unwrap();
        assert!(stored.starts_with("$1$"));
        assert_eq!(stored.len(), 3 + 16 + 1 + 64);
        assert!(verify_password(&stored, MD5_HELLO));
        assert!(!verify_password(&stored, "00000000000000000000000000000000"));
    }

    #[test]
    fn hashpassword_unique_salt() {
        let a = hash_password(MD5_HELLO).unwrap();
        let b = hash_password(MD5_HELLO).unwrap();
        assert_ne!(a, b);
    }

    #[test]
    fn verify_accepts_legacy_md5() {
        assert!(verify_password(MD5_HELLO, MD5_HELLO));
        assert!(!verify_password(MD5_HELLO, "5d41402abc4b2a76b9719d911017c593"));
    }

    #[test]
    fn verify_rejects_empty() {
        assert!(!verify_password("", MD5_HELLO));
        assert!(!verify_password(MD5_HELLO, ""));
    }

    #[test]
    fn verify_rejects_malformed_v1() {
        assert!(!verify_password("$1$short", MD5_HELLO));
        assert!(!verify_password(
            "$1$abcdefghijklmnopXffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            MD5_HELLO
        ));
    }

    #[test]
    fn test_password_is_legacy() {
        assert!(password_is_legacy(MD5_HELLO));
        assert!(!password_is_legacy(&hash_password(MD5_HELLO).unwrap()));
        assert!(!password_is_legacy(""));
    }

    /// A v1 record produced by THIS code must verify, and (the cross-impl
    /// contract) a v1 record with a hand-computed hash matching the Blitz
    /// formula `SHA256(salt ++ md5)` must verify — proving on-disk interop.
    #[test]
    fn v1_record_matches_blitz_formula() {
        let salt = "ABCDEFGHIJKLMNOP";
        let hash = sha256_hex(format!("{salt}{MD5_HELLO}").as_bytes());
        let record = format!("$1${salt}${hash}");
        assert_eq!(record.len(), V1_LEN);
        assert!(verify_password(&record, MD5_HELLO));
        assert!(!verify_password(&record, "00000000000000000000000000000000"));
    }

    #[test]
    fn upgrade_legacy_then_verifies() {
        let upgraded = upgrade_password_if_legacy(MD5_HELLO, MD5_HELLO).unwrap();
        assert!(upgraded.starts_with("$1$"));
        assert!(verify_password(&upgraded, MD5_HELLO));
        // Already-v1 is returned unchanged.
        let again = upgrade_password_if_legacy(&upgraded, MD5_HELLO).unwrap();
        assert_eq!(again, upgraded);
    }

    // --- ConstantTimeStrEq parity (PasswordHashTest.bb).

    #[test]
    fn consttime_identical() {
        assert!(constant_time_eq(b"", b""));
        assert!(constant_time_eq(b"a", b"a"));
        assert!(constant_time_eq(b"hello", b"hello"));
        let hex = b"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assert!(constant_time_eq(hex, hex));
    }

    #[test]
    fn consttime_length_mismatch() {
        assert!(!constant_time_eq(b"abc", b"abcd"));
        assert!(!constant_time_eq(b"abcd", b"abc"));
        assert!(!constant_time_eq(b"", b"x"));
        assert!(!constant_time_eq(b"x", b""));
    }

    #[test]
    fn consttime_diff_positions() {
        assert!(!constant_time_eq(b"aaaaaaaaaaaaaaaa", b"Xaaaaaaaaaaaaaaa"));
        assert!(!constant_time_eq(b"aaaaaaaaaaaaaaaa", b"aaaaaaaXaaaaaaaa"));
        assert!(!constant_time_eq(b"aaaaaaaaaaaaaaaa", b"aaaaaaaaaaaaaaaX"));
    }

    #[test]
    fn consttime_realistic_hex_diff() {
        let a = b"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        let b = b"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ae";
        assert!(!constant_time_eq(a, b));
        assert!(constant_time_eq(a, a));
    }

    #[test]
    fn salt_is_alphabet_only_and_right_length() {
        let salt = generate_salt().unwrap();
        assert_eq!(salt.len(), SALT_LEN);
        assert!(salt.bytes().all(|c| SALT_ALPHABET.contains(&c)));
    }
}
