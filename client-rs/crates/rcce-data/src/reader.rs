//! Blitz3D-compatible binary file reader.
//!
//! CRITICAL byte-order rule, verified against the source:
//!
//! * **File I/O** (`ReadByte`/`ReadShort`/`ReadInt`/`ReadFloat` in Blitz3D) is
//!   **little-endian** native x86 — this is what every `.dat` catalog and save
//!   file uses. This module reads little-endian.
//! * The **wire protocol** (`RCE_StrFromInt$` in `src/Modules/RCEnet.bb`) is
//!   **big-endian**. That lives in `rcce-net`, NOT here. Do not mix them.
//!
//! String fields in `.dat` files are length-prefixed with a **4-byte LE int**
//! length followed by that many raw bytes — see `MediaReadFilename$`
//! (`src/Modules/Media.bb:23`). `ReadShort` is signed 16-bit. `ReadByte` is an
//! unsigned octet (Blitz returns 0..255).

/// Errors surfaced while decoding a Blitz binary file.
#[derive(Debug, thiserror::Error)]
pub enum ReadError {
    #[error("unexpected end of data: needed {needed} byte(s) at offset {offset}, {available} available")]
    UnexpectedEof {
        offset: usize,
        needed: usize,
        available: usize,
    },
    #[error("seek to {target} is out of bounds (len {len})")]
    SeekOutOfBounds { target: usize, len: usize },
    #[error("string length {len} exceeds the maximum {max} (corrupt or hostile file)")]
    StringTooLong { len: i32, max: usize },
    #[error("declared count/size {count} exceeds the maximum {max} (corrupt or hostile file)")]
    CountTooLarge { count: i64, max: usize },
}

/// A cursor over an in-memory Blitz binary file.
///
/// Mirrors Blitz's stateful file handle: reads advance the position, and
/// [`seek`](Self::seek) jumps to an absolute offset like `SeekFile`.
pub struct BlitzReader<'a> {
    data: &'a [u8],
    pos: usize,
}

impl<'a> BlitzReader<'a> {
    pub fn new(data: &'a [u8]) -> Self {
        Self { data, pos: 0 }
    }

    #[inline]
    pub fn position(&self) -> usize {
        self.pos
    }

    #[inline]
    pub fn len(&self) -> usize {
        self.data.len()
    }

    #[inline]
    pub fn is_empty(&self) -> bool {
        self.data.is_empty()
    }

    /// True once the cursor has consumed all bytes — the analogue of `Eof(F)`.
    #[inline]
    pub fn eof(&self) -> bool {
        self.pos >= self.data.len()
    }

    /// Absolute seek, matching `SeekFile F, target`.
    pub fn seek(&mut self, target: usize) -> Result<(), ReadError> {
        if target > self.data.len() {
            return Err(ReadError::SeekOutOfBounds {
                target,
                len: self.data.len(),
            });
        }
        self.pos = target;
        Ok(())
    }

    fn take(&mut self, n: usize) -> Result<&'a [u8], ReadError> {
        let end = self.pos.checked_add(n).ok_or(ReadError::UnexpectedEof {
            offset: self.pos,
            needed: n,
            available: 0,
        })?;
        if end > self.data.len() {
            return Err(ReadError::UnexpectedEof {
                offset: self.pos,
                needed: n,
                available: self.data.len().saturating_sub(self.pos),
            });
        }
        let slice = &self.data[self.pos..end];
        self.pos = end;
        Ok(slice)
    }

    /// `ReadByte` — one unsigned octet (Blitz returns 0..255).
    pub fn read_byte(&mut self) -> Result<u8, ReadError> {
        Ok(self.take(1)?[0])
    }

    /// `ReadShort` — signed 16-bit little-endian.
    pub fn read_short(&mut self) -> Result<i16, ReadError> {
        let b = self.take(2)?;
        Ok(i16::from_le_bytes([b[0], b[1]]))
    }

    /// `ReadShort` reinterpreted as unsigned — common for IDs (0..65535).
    pub fn read_short_u(&mut self) -> Result<u16, ReadError> {
        let b = self.take(2)?;
        Ok(u16::from_le_bytes([b[0], b[1]]))
    }

    /// `ReadInt` — signed 32-bit little-endian.
    pub fn read_int(&mut self) -> Result<i32, ReadError> {
        let b = self.take(4)?;
        Ok(i32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }

    /// `ReadFloat` — IEEE-754 single precision, little-endian.
    pub fn read_float(&mut self) -> Result<f32, ReadError> {
        let b = self.take(4)?;
        Ok(f32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }

    /// A 4-byte ASCII chunk tag (e.g. `BB3D`, `MESH`), not length-prefixed.
    pub fn read_tag(&mut self) -> Result<[u8; 4], ReadError> {
        let b = self.take(4)?;
        Ok([b[0], b[1], b[2], b[3]])
    }

    /// A NUL-terminated string (B3D format uses these, unlike the length-prefixed
    /// `.dat` strings). Bytes are decoded lossily as UTF-8. `max` bounds a
    /// corrupt/unterminated string.
    pub fn read_cstr(&mut self, max: usize) -> Result<String, ReadError> {
        let mut bytes = Vec::new();
        loop {
            let b = self.read_byte()?;
            if b == 0 {
                break;
            }
            bytes.push(b);
            if bytes.len() >= max {
                break;
            }
        }
        Ok(String::from_utf8_lossy(&bytes).into_owned())
    }

    /// Length-prefixed string: 4-byte LE int length, then that many bytes.
    ///
    /// Matches `MediaReadFilename$` (`Media.bb:23`): a negative or
    /// over-`max` length yields an empty string (the loader treats a corrupt
    /// prefix as "no name") rather than erroring, EXCEPT we surface
    /// [`ReadError::StringTooLong`] so callers can decide. Pass the same `max`
    /// the source uses for the field (e.g. 260 for filenames, 256 for names,
    /// 1024 for scripts, 4096 for descriptions).
    ///
    /// Bytes are decoded lossily as UTF-8 (the source treats them as raw
    /// Latin-1/ASCII; lossy keeps non-ASCII bytes visible rather than failing).
    pub fn read_string(&mut self, max: usize) -> Result<String, ReadError> {
        let len = self.read_int()?;
        if len <= 0 {
            return Ok(String::new());
        }
        if len as usize > max {
            return Err(ReadError::StringTooLong { len, max });
        }
        let bytes = self.take(len as usize)?;
        Ok(String::from_utf8_lossy(bytes).into_owned())
    }
}
