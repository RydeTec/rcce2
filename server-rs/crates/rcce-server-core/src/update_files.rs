//! Update-system file manifest — `Server Data/Files.dat`
//! (`UpdatesServer.bb:21` `LoadUpdateFiles`).
//!
//! A flat sequence of `[Name str][Checksum i32]` records read until EOF
//! (strings = 4-byte-LE-length + bytes, name bounded 260 = Windows MAX_PATH,
//! same cap as `ReadBoundedString$(F, 260)`). The Blitz server streams this
//! list to a stock Blitz client in reply to `P_FetchUpdateFiles`
//! (`ServerNet.bb:2242`); the client compares each checksum against
//! `CountChecksum` of its local copy and downloads mismatches from the HTTP
//! update host. The shipped `data/` has an empty `Files.dat` (no update files).
//!
//! Soft-fail: a trailing partial record is dropped (Blitz would fabricate a
//! `""`/`0` entry from its EOF reads — harmless but meaningless; the port
//! stops cleanly instead). Never panics.

use crate::blitz_io::Reader;

/// One update-manifest entry.
#[derive(Clone, Debug, PartialEq)]
pub struct UpdateFile {
    /// Path relative to the game dir (e.g. `Data\Textures\Foo.png`).
    pub name: String,
    /// 32-bit additive checksum of the file contents (`CountChecksum`).
    pub checksum: i32,
}

/// Parse `Files.dat` bytes into the manifest, in file order.
pub fn parse_update_files(data: &[u8]) -> Vec<UpdateFile> {
    let mut r = Reader::new(data);
    let mut files = Vec::new();
    while !r.at_end() {
        let Some(name) = r.string(260) else { break };
        let Some(checksum) = r.i32() else { break };
        files.push(UpdateFile { name, checksum });
    }
    files
}

/// Load from a path; missing/unreadable → empty manifest (`LoadUpdateFiles`'s
/// `If F = 0 Then Return 0`).
pub fn load_update_files(path: impl AsRef<std::path::Path>) -> Vec<UpdateFile> {
    match std::fs::read(path) {
        Ok(bytes) => parse_update_files(&bytes),
        Err(_) => Vec::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::blitz_io::Writer;

    #[test]
    fn parses_records_and_drops_partial_tail() {
        let mut w = Writer::new();
        w.string("Data\\Textures\\A.png").i32(12345);
        w.string("Data\\Music\\B.ogg").i32(-7);
        let mut bytes = w.into_bytes();
        assert_eq!(
            parse_update_files(&bytes),
            vec![
                UpdateFile { name: "Data\\Textures\\A.png".into(), checksum: 12345 },
                UpdateFile { name: "Data\\Music\\B.ogg".into(), checksum: -7 },
            ]
        );
        // Truncated third record (string only, checksum missing) is dropped.
        bytes.extend_from_slice(&3u32.to_le_bytes());
        bytes.extend_from_slice(b"C.x");
        assert_eq!(parse_update_files(&bytes).len(), 2);
        // Empty file → empty manifest (the shipped Files.dat).
        assert!(parse_update_files(&[]).is_empty());
    }
}
