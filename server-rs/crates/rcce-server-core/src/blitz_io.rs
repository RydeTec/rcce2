//! Blitz3D file stream codec — the byte layout `WriteByte`/`WriteShort`/
//! `WriteInt`/`WriteFloat`/`WriteString` and their `Read*` inverses produce.
//!
//! All integers/floats are **little-endian** (Blitz native on x86). Strings are
//! a **4-byte LE length prefix** + raw bytes (`WriteString`; the wire uses a
//! 1-byte prefix instead — that lives in `rcce-net`). Every read is bounded and
//! returns `None` on underflow / an over-cap string length, so a corrupt or
//! hostile `Accounts.dat` can never panic or over-allocate the server.

/// Append-only writer producing the Blitz stream byte layout.
#[derive(Default, Debug)]
pub struct Writer {
    pub buf: Vec<u8>,
}

impl Writer {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn u8(&mut self, v: u8) -> &mut Self {
        self.buf.push(v);
        self
    }
    /// `WriteShort` (16-bit). Use the `i16`/`u16` variants per field signedness;
    /// the bytes are identical either way.
    pub fn i16(&mut self, v: i16) -> &mut Self {
        self.buf.extend_from_slice(&v.to_le_bytes());
        self
    }
    pub fn u16(&mut self, v: u16) -> &mut Self {
        self.buf.extend_from_slice(&v.to_le_bytes());
        self
    }
    /// `WriteInt` (32-bit).
    pub fn i32(&mut self, v: i32) -> &mut Self {
        self.buf.extend_from_slice(&v.to_le_bytes());
        self
    }
    pub fn u32(&mut self, v: u32) -> &mut Self {
        self.buf.extend_from_slice(&v.to_le_bytes());
        self
    }
    /// `WriteFloat` (IEEE-754 single, LE).
    pub fn f32(&mut self, v: f32) -> &mut Self {
        self.buf.extend_from_slice(&v.to_le_bytes());
        self
    }
    /// `WriteString`: 4-byte LE length prefix + raw bytes.
    pub fn string(&mut self, s: &str) -> &mut Self {
        self.u32(s.len() as u32);
        self.buf.extend_from_slice(s.as_bytes());
        self
    }
    pub fn into_bytes(self) -> Vec<u8> {
        self.buf
    }
}

/// Forward-only bounded reader over a Blitz stream. Every getter returns `None`
/// on underflow (the soft-fail signal — the caller stops cleanly).
#[derive(Debug)]
pub struct Reader<'a> {
    data: &'a [u8],
    pos: usize,
}

impl<'a> Reader<'a> {
    pub fn new(data: &'a [u8]) -> Self {
        Self { data, pos: 0 }
    }
    pub fn pos(&self) -> usize {
        self.pos
    }
    pub fn at_end(&self) -> bool {
        self.pos >= self.data.len()
    }
    pub fn remaining(&self) -> usize {
        self.data.len().saturating_sub(self.pos)
    }
    fn take(&mut self, n: usize) -> Option<&'a [u8]> {
        let end = self.pos.checked_add(n)?;
        let s = self.data.get(self.pos..end)?;
        self.pos = end;
        Some(s)
    }
    pub fn u8(&mut self) -> Option<u8> {
        self.take(1).map(|b| b[0])
    }
    pub fn i16(&mut self) -> Option<i16> {
        self.take(2).map(|b| i16::from_le_bytes([b[0], b[1]]))
    }
    pub fn u16(&mut self) -> Option<u16> {
        self.take(2).map(|b| u16::from_le_bytes([b[0], b[1]]))
    }
    pub fn i32(&mut self) -> Option<i32> {
        self.take(4).map(|b| i32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }
    pub fn u32(&mut self) -> Option<u32> {
        self.take(4).map(|b| u32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }
    pub fn f32(&mut self) -> Option<f32> {
        self.take(4).map(|b| f32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }
    /// `ReadBoundedString$`: 4-byte LE length, rejected (→ `None`) if it exceeds
    /// `max` or runs past EOF. Lossy UTF-8 (the field is raw bytes).
    pub fn string(&mut self, max: u32) -> Option<String> {
        let len = self.u32()?;
        if len > max {
            return None;
        }
        let b = self.take(len as usize)?;
        Some(String::from_utf8_lossy(b).into_owned())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip_primitives() {
        let mut w = Writer::new();
        w.u8(0xAB)
            .i16(-1234)
            .u16(65535)
            .i32(-100000)
            .u32(0x41434354)
            .f32(3.5)
            .string("héllo"); // multibyte to exercise byte length
        let bytes = w.into_bytes();

        let mut r = Reader::new(&bytes);
        assert_eq!(r.u8(), Some(0xAB));
        assert_eq!(r.i16(), Some(-1234));
        assert_eq!(r.u16(), Some(65535));
        assert_eq!(r.i32(), Some(-100000));
        assert_eq!(r.u32(), Some(0x41434354));
        assert_eq!(r.f32(), Some(3.5));
        assert_eq!(r.string(256).as_deref(), Some("héllo"));
        assert!(r.at_end());
    }

    #[test]
    fn underflow_is_none() {
        let mut r = Reader::new(&[0x01]);
        assert_eq!(r.u8(), Some(1));
        assert_eq!(r.u8(), None);
        assert_eq!(r.i32(), None);
    }

    #[test]
    fn oversize_string_len_is_none() {
        let mut w = Writer::new();
        w.u32(1_000_000); // claims a megabyte but no data follows
        let bytes = w.into_bytes();
        let mut r = Reader::new(&bytes);
        assert_eq!(r.string(256), None);
    }

    #[test]
    fn short_string_value_is_le_length() {
        let mut w = Writer::new();
        w.string("AB");
        // 4-byte LE length 2 then 'A','B'.
        assert_eq!(w.into_bytes(), vec![2, 0, 0, 0, b'A', b'B']);
    }
}
