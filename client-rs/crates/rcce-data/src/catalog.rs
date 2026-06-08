//! Indexed media catalogs: `Meshes.dat`, `Textures.dat`, `Sounds.dat`,
//! `Music.dat`.
//!
//! All four share the same shape (verified in `src/Modules/Media.bb`):
//!
//! ```text
//! [ index: 65535 little-endian i32 file offsets, one per ID 0..=65534 ]
//! [ data:  variable-length records, pointed at by the index           ]
//! ```
//!
//! Index slot `ID` holds the absolute file offset of that ID's record, or `0`
//! if the slot is unused (`GetMesh`/`GetTexture` both treat offset `0` as
//! "empty" — `Media.bb:808`). The per-record layout differs per catalog; see
//! each parser below. The index is read with `SeekFile F, ID * 4`
//! (`Media.bb:806`), so the header is exactly `65535 * 4 = 262_140` bytes.

use crate::reader::{BlitzReader, ReadError};

/// Number of addressable IDs (0..=65534). The index has one i32 per ID.
pub const CATALOG_SLOTS: usize = 65535;
const INDEX_BYTES: usize = CATALOG_SLOTS * 4;

/// One mesh record from `Meshes.dat`.
///
/// Layout per record (`Media.bb:817-823`, in order):
/// `IsAnim:u8, Scale:f32, X:f32, Y:f32, Z:f32, Shader:i16, Filename:str(260)`.
/// The on-disk filename is relative to `Data\Meshes\`.
#[derive(Debug, Clone, PartialEq)]
pub struct MeshEntry {
    pub id: u16,
    pub is_anim: bool,
    pub scale: f32,
    pub offset: [f32; 3],
    pub shader: i16,
    /// Path relative to `Data/Meshes/`, as stored (Windows separators).
    pub filename: String,
}

/// A parsed `Meshes.dat`: only the populated slots, in ascending ID order.
#[derive(Debug, Clone, Default)]
pub struct MeshCatalog {
    pub entries: Vec<MeshEntry>,
}

impl MeshCatalog {
    /// Parse a whole `Meshes.dat` image.
    ///
    /// Walks all 65535 index slots, follows each non-zero offset, and decodes
    /// the record. A record that fails to decode (truncated / bad length) is
    /// skipped rather than aborting the whole catalog — this matches the
    /// engine, where `GetMesh` simply returns 0 for a bad entry and the rest of
    /// the world still loads. Skips are returned for the caller to log.
    pub fn parse(data: &[u8]) -> Result<ParsedCatalog<MeshCatalog>, ReadError> {
        if data.len() < INDEX_BYTES {
            return Err(ReadError::UnexpectedEof {
                offset: 0,
                needed: INDEX_BYTES,
                available: data.len(),
            });
        }
        let mut entries = Vec::new();
        let mut skipped = Vec::new();

        for id in 0..CATALOG_SLOTS {
            let i = id * 4;
            let offset = i32::from_le_bytes([data[i], data[i + 1], data[i + 2], data[i + 3]]);
            if offset == 0 {
                continue; // unused slot
            }
            if offset < 0 {
                skipped.push((id as u16, "negative offset"));
                continue;
            }
            match Self::parse_record(data, id as u16, offset as usize) {
                Ok(entry) => entries.push(entry),
                Err(_) => skipped.push((id as u16, "record decode failed")),
            }
        }

        Ok(ParsedCatalog {
            value: MeshCatalog { entries },
            skipped,
        })
    }

    fn parse_record(data: &[u8], id: u16, offset: usize) -> Result<MeshEntry, ReadError> {
        let mut r = BlitzReader::new(data);
        r.seek(offset)?;
        let is_anim = r.read_byte()? != 0;
        let scale = r.read_float()?;
        let x = r.read_float()?;
        let y = r.read_float()?;
        let z = r.read_float()?;
        let shader = r.read_short()?;
        let filename = r.read_string(260)?;
        Ok(MeshEntry {
            id,
            is_anim,
            scale,
            offset: [x, y, z],
            shader,
            filename,
        })
    }

    /// Look up an entry by ID (linear; entries are ID-sorted so callers that
    /// need random access should build a map). Returns `None` for empty slots.
    pub fn get(&self, id: u16) -> Option<&MeshEntry> {
        self.entries.iter().find(|e| e.id == id)
    }
}

/// Result of parsing a catalog: the value plus any slots that were skipped
/// because their record failed to decode (logged, not fatal — see `parse`).
#[derive(Debug, Clone)]
pub struct ParsedCatalog<T> {
    pub value: T,
    pub skipped: Vec<(u16, &'static str)>,
}

/// One texture record from `Textures.dat` (`Media.bb:904`): `Flags:i16` then a
/// length-prefixed `Filename` (relative to `Data/Textures/`). Same 65535-slot
/// i32 index as `Meshes.dat`.
#[derive(Debug, Clone, PartialEq)]
pub struct TextureEntry {
    pub id: u16,
    pub flags: i16,
    /// Path relative to `Data/Textures/`, as stored (Windows separators).
    pub filename: String,
}

/// A parsed `Textures.dat`: only populated slots, ascending by id.
#[derive(Debug, Clone, Default)]
pub struct TextureCatalog {
    pub entries: Vec<TextureEntry>,
}

impl TextureCatalog {
    pub fn parse(data: &[u8]) -> Result<ParsedCatalog<TextureCatalog>, ReadError> {
        if data.len() < INDEX_BYTES {
            return Err(ReadError::UnexpectedEof {
                offset: 0,
                needed: INDEX_BYTES,
                available: data.len(),
            });
        }
        let mut entries = Vec::new();
        let mut skipped = Vec::new();
        for id in 0..CATALOG_SLOTS {
            let i = id * 4;
            let offset = i32::from_le_bytes([data[i], data[i + 1], data[i + 2], data[i + 3]]);
            if offset <= 0 {
                if offset < 0 {
                    skipped.push((id as u16, "negative offset"));
                }
                continue;
            }
            let mut r = BlitzReader::new(data);
            let parsed = (|| {
                r.seek(offset as usize)?;
                let flags = r.read_short()?;
                let filename = r.read_string(260)?;
                Ok::<_, ReadError>(TextureEntry {
                    id: id as u16,
                    flags,
                    filename,
                })
            })();
            match parsed {
                Ok(e) => entries.push(e),
                Err(_) => skipped.push((id as u16, "record decode failed")),
            }
        }
        Ok(ParsedCatalog {
            value: TextureCatalog { entries },
            skipped,
        })
    }

    pub fn get(&self, id: u16) -> Option<&TextureEntry> {
        self.entries.iter().find(|e| e.id == id)
    }
}

/// One music record from `Music.dat` (`Media.bb:671-672`): just a length-
/// prefixed `Filename` relative to `Data/Music/`. Same 65535-slot i32 index as
/// the other catalogs.
#[derive(Debug, Clone, PartialEq)]
pub struct MusicEntry {
    pub id: u16,
    /// Path relative to `Data/Music/`, as stored (Windows separators).
    pub filename: String,
}

/// A parsed `Music.dat`: only populated slots, ascending by id.
#[derive(Debug, Clone, Default)]
pub struct MusicCatalog {
    pub entries: Vec<MusicEntry>,
}

impl MusicCatalog {
    pub fn parse(data: &[u8]) -> Result<ParsedCatalog<MusicCatalog>, ReadError> {
        if data.len() < INDEX_BYTES {
            return Err(ReadError::UnexpectedEof {
                offset: 0,
                needed: INDEX_BYTES,
                available: data.len(),
            });
        }
        let mut entries = Vec::new();
        let mut skipped = Vec::new();
        for id in 0..CATALOG_SLOTS {
            let i = id * 4;
            let offset = i32::from_le_bytes([data[i], data[i + 1], data[i + 2], data[i + 3]]);
            if offset <= 0 {
                if offset < 0 {
                    skipped.push((id as u16, "negative offset"));
                }
                continue;
            }
            let mut r = BlitzReader::new(data);
            let parsed = (|| {
                r.seek(offset as usize)?;
                let filename = r.read_string(260)?;
                Ok::<_, ReadError>(MusicEntry { id: id as u16, filename })
            })();
            match parsed {
                Ok(e) if !e.filename.is_empty() => entries.push(e),
                Ok(_) => skipped.push((id as u16, "empty filename")),
                Err(_) => skipped.push((id as u16, "record decode failed")),
            }
        }
        Ok(ParsedCatalog {
            value: MusicCatalog { entries },
            skipped,
        })
    }

    pub fn get(&self, id: u16) -> Option<&MusicEntry> {
        self.entries.iter().find(|e| e.id == id)
    }
}

/// One sound record from `Sounds.dat` (`Media.bb` `GetSoundName$`): a length-
/// prefixed `Filename` relative to `Data/Sounds/`. Same 65535-slot i32 index as
/// the other catalogs. The stored filename may end in a `chr(1)` marker byte
/// that flags the sound as 3D/positional (`ClientNet.bb:743`
/// `Asc(Right$(Name$,1))=True`); callers strip it before using the name as a path.
#[derive(Debug, Clone, PartialEq)]
pub struct SoundEntry {
    pub id: u16,
    /// Path relative to `Data/Sounds/`, as stored (may carry a trailing 3D marker).
    pub filename: String,
}

impl SoundEntry {
    /// True when the stored filename ends in the `chr(1)` 3D/positional marker.
    pub fn is_3d(&self) -> bool {
        self.filename.as_bytes().last() == Some(&1)
    }

    /// The filename with any trailing 3D-marker byte removed (usable as a path).
    pub fn clean_name(&self) -> &str {
        self.filename.strip_suffix('\u{1}').unwrap_or(&self.filename)
    }
}

/// A parsed `Sounds.dat`: only populated slots, ascending by id.
#[derive(Debug, Clone, Default)]
pub struct SoundCatalog {
    pub entries: Vec<SoundEntry>,
}

impl SoundCatalog {
    pub fn parse(data: &[u8]) -> Result<ParsedCatalog<SoundCatalog>, ReadError> {
        if data.len() < INDEX_BYTES {
            return Err(ReadError::UnexpectedEof {
                offset: 0,
                needed: INDEX_BYTES,
                available: data.len(),
            });
        }
        let mut entries = Vec::new();
        let mut skipped = Vec::new();
        for id in 0..CATALOG_SLOTS {
            let i = id * 4;
            let offset = i32::from_le_bytes([data[i], data[i + 1], data[i + 2], data[i + 3]]);
            if offset <= 0 {
                if offset < 0 {
                    skipped.push((id as u16, "negative offset"));
                }
                continue;
            }
            let mut r = BlitzReader::new(data);
            let parsed = (|| {
                r.seek(offset as usize)?;
                let filename = r.read_string(260)?;
                Ok::<_, ReadError>(SoundEntry { id: id as u16, filename })
            })();
            match parsed {
                Ok(e) if !e.filename.is_empty() => entries.push(e),
                Ok(_) => skipped.push((id as u16, "empty filename")),
                Err(_) => skipped.push((id as u16, "record decode failed")),
            }
        }
        Ok(ParsedCatalog {
            value: SoundCatalog { entries },
            skipped,
        })
    }

    pub fn get(&self, id: u16) -> Option<&SoundEntry> {
        self.entries.iter().find(|e| e.id == id)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn music_catalog_resolves_one_entry() {
        // Build a synthetic Music.dat: zeroed index + one record at id 5.
        let mut data = vec![0u8; INDEX_BYTES];
        let record_off = INDEX_BYTES as i32;
        // index[5] = record_off
        data[5 * 4..5 * 4 + 4].copy_from_slice(&record_off.to_le_bytes());
        // record = Blitz string (4-byte LE len + bytes)
        let name = b"Tribal/Tribal.ogg";
        data.extend_from_slice(&(name.len() as i32).to_le_bytes());
        data.extend_from_slice(name);

        let cat = MusicCatalog::parse(&data).expect("parse").value;
        assert_eq!(cat.entries.len(), 1);
        let e = cat.get(5).expect("id 5");
        assert_eq!(e.filename, "Tribal/Tribal.ogg");
        assert!(cat.get(6).is_none());
    }

    #[test]
    fn sound_catalog_parses_and_marks_3d() {
        // Synthetic Sounds.dat: id 3 = a 2D sound, id 4 = a 3D sound (trailing chr(1)).
        let mut data = vec![0u8; INDEX_BYTES];
        let rec3 = INDEX_BYTES as i32;
        data[3 * 4..3 * 4 + 4].copy_from_slice(&rec3.to_le_bytes());
        let n3 = b"Combat/Hit.ogg";
        data.extend_from_slice(&(n3.len() as i32).to_le_bytes());
        data.extend_from_slice(n3);
        let rec4 = data.len() as i32;
        data[4 * 4..4 * 4 + 4].copy_from_slice(&rec4.to_le_bytes());
        let n4 = b"Combat/Swing.ogg\x01"; // trailing 3D marker
        data.extend_from_slice(&(n4.len() as i32).to_le_bytes());
        data.extend_from_slice(n4);

        let cat = SoundCatalog::parse(&data).expect("parse").value;
        let two_d = cat.get(3).expect("id 3");
        assert!(!two_d.is_3d());
        assert_eq!(two_d.clean_name(), "Combat/Hit.ogg");
        let three_d = cat.get(4).expect("id 4");
        assert!(three_d.is_3d());
        assert_eq!(three_d.clean_name(), "Combat/Swing.ogg"); // marker stripped
    }

    #[test]
    fn empty_index_yields_no_entries() {
        // The shipped starter project's Music.dat is exactly this: index only.
        let data = vec![0u8; INDEX_BYTES];
        let cat = MusicCatalog::parse(&data).expect("parse").value;
        assert!(cat.entries.is_empty());
    }

    #[test]
    fn truncated_index_errors() {
        let data = vec![0u8; INDEX_BYTES - 1];
        assert!(MusicCatalog::parse(&data).is_err());
    }
}
