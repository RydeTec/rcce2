//! Auth helpers for the login handshake.

/// The password the client puts on the wire: **lowercase 32-char MD5 hex** of
/// the plaintext (`MD5$()` in `MD5.bb`; used at `MainMenu.bb:804`). Standard
/// MD5 hex equals BlitzForge's per-word little-endian `WordToHex$` output.
pub fn md5_hex(password: &str) -> String {
    format!("{:x}", md5::compute(password))
}

/// `Encrypt$(s, -1)` from `Server.bb:839` / `Client.bb:1032`: a length-preserving
/// obfuscation used ONLY for the account-creation email field. Each byte is
/// shifted by `-26` and the whole string is reversed (the source prepends each
/// transformed char). The server reverses it with `Encrypt$(.., 1)`.
pub fn encrypt_email(email: &str) -> Vec<u8> {
    email
        .as_bytes()
        .iter()
        .rev()
        .map(|&b| b.wrapping_sub(26))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn md5_matches_known_vector() {
        // RFC 1321 test vector.
        assert_eq!(md5_hex("abc"), "900150983cd24fb0d6963f7d28e17f72");
        assert_eq!(md5_hex(""), "d41d8cd98f00b204e9800998ecf8427e");
    }

    #[test]
    fn encrypt_is_reversible_and_length_preserving() {
        // Decrypt = reverse + (+26) per byte, mirroring Encrypt$(.., 1).
        let email = "rust@bot.com";
        let enc = encrypt_email(email);
        assert_eq!(enc.len(), email.len());
        let dec: Vec<u8> = enc.iter().rev().map(|&b| b.wrapping_add(26)).collect();
        assert_eq!(dec, email.as_bytes());
    }
}
