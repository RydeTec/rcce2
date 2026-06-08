//! Wire codec for packet PAYLOADS (the bytes after the 1-byte type).
//!
//! **Little-endian** integers/floats. `RCE_StrFromInt$` (RCEnet.bb:85) pokes the
//! value into a bank (native LE on x86) then emits bytes with a `Length-1..0`
//! loop that PREPENDS each — which cancels back to native byte order, i.e. the
//! wire is little-endian. (An earlier analysis mis-read this as big-endian; the
//! world-model decode proved it LE — runtime id 1792 was byte-swapped 7.)
//! `RCE_StrFromFloat$` is the same shape → LE IEEE-754. So the wire and the
//! `.dat` file format share little-endian; the only split is the string length
//! prefix: wire = **1-byte** (`str8`), files = 4-byte.

/// Builds a big-endian payload.
#[derive(Default)]
pub struct MsgWriter {
    buf: Vec<u8>,
}

impl MsgWriter {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn u8(&mut self, v: u8) -> &mut Self {
        self.buf.push(v);
        self
    }
    pub fn u16(&mut self, v: u16) -> &mut Self {
        self.buf.extend_from_slice(&v.to_le_bytes());
        self
    }
    pub fn u32(&mut self, v: u32) -> &mut Self {
        self.buf.extend_from_slice(&v.to_le_bytes());
        self
    }
    pub fn i32(&mut self, v: i32) -> &mut Self {
        self.buf.extend_from_slice(&v.to_le_bytes());
        self
    }
    pub fn f32(&mut self, v: f32) -> &mut Self {
        self.buf.extend_from_slice(&v.to_le_bytes());
        self
    }
    /// 1-byte length prefix + bytes (the account/login string convention).
    /// Panics in debug if `s` exceeds 255 bytes (callers validate length).
    pub fn str8(&mut self, s: &str) -> &mut Self {
        debug_assert!(s.len() <= 255, "str8 too long: {}", s.len());
        self.buf.push(s.len() as u8);
        self.buf.extend_from_slice(s.as_bytes());
        self
    }
    pub fn raw(&mut self, b: &[u8]) -> &mut Self {
        self.buf.extend_from_slice(b);
        self
    }
    pub fn into_bytes(self) -> Vec<u8> {
        self.buf
    }
    pub fn as_slice(&self) -> &[u8] {
        &self.buf
    }
}

/// Reads a big-endian payload. Every getter returns `None` on underflow.
pub struct MsgReader<'a> {
    data: &'a [u8],
    pos: usize,
}

impl<'a> MsgReader<'a> {
    pub fn new(data: &'a [u8]) -> Self {
        Self { data, pos: 0 }
    }
    pub fn remaining(&self) -> usize {
        self.data.len().saturating_sub(self.pos)
    }
    fn take(&mut self, n: usize) -> Option<&'a [u8]> {
        let end = self.pos.checked_add(n)?;
        if end > self.data.len() {
            return None;
        }
        let s = &self.data[self.pos..end];
        self.pos = end;
        Some(s)
    }
    pub fn u8(&mut self) -> Option<u8> {
        self.take(1).map(|b| b[0])
    }
    pub fn u16(&mut self) -> Option<u16> {
        self.take(2).map(|b| u16::from_le_bytes([b[0], b[1]]))
    }
    pub fn u32(&mut self) -> Option<u32> {
        self.take(4)
            .map(|b| u32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }
    pub fn i32(&mut self) -> Option<i32> {
        self.take(4)
            .map(|b| i32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }
    pub fn f32(&mut self) -> Option<f32> {
        self.take(4)
            .map(|b| f32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }
    /// Read `n` raw bytes, advancing the cursor. `None` if fewer remain.
    pub fn bytes(&mut self, n: usize) -> Option<&'a [u8]> {
        self.take(n)
    }
    /// 1-byte-length-prefixed string (lossy UTF-8).
    pub fn str8(&mut self) -> Option<String> {
        let n = self.u8()? as usize;
        let b = self.take(n)?;
        Some(String::from_utf8_lossy(b).into_owned())
    }
    /// 2-byte-(LE)-length-prefixed string (lossy UTF-8). Used by the
    /// `P_FetchCharacter` spell/quest blocks (`RCE_StrFromInt$(Len, 2)`).
    pub fn str16(&mut self) -> Option<String> {
        let n = self.u16()? as usize;
        let b = self.take(n)?;
        Some(String::from_utf8_lossy(b).into_owned())
    }
    pub fn rest(&self) -> &'a [u8] {
        &self.data[self.pos..]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn writer_reader_roundtrip() {
        let mut w = MsgWriter::new();
        w.str8("alice").u16(0x1234).u8(7).str8("z");
        let bytes = w.into_bytes();
        // 1+5 + 2 + 1 + 1+1
        assert_eq!(bytes.len(), 11);
        let mut r = MsgReader::new(&bytes);
        assert_eq!(r.str8().unwrap(), "alice");
        assert_eq!(r.u16().unwrap(), 0x1234);
        assert_eq!(r.u8().unwrap(), 7);
        assert_eq!(r.str8().unwrap(), "z");
        assert_eq!(r.remaining(), 0);
    }

    #[test]
    fn reader_underflow_is_none() {
        let mut r = MsgReader::new(&[0x01]);
        assert!(r.u16().is_none());
    }
}
