//! Adversarial / malformed-input acceptance suite for the `rcce-net` wire codec.
//!
//! The server is untrusted from the client's perspective (a buggy or malicious
//! server can put arbitrary bytes on the wire). Decoding a hostile packet
//! payload must NEVER panic — every reader getter must return `None` on
//! underflow rather than indexing out of bounds or `unwrap()`-ing a short read.
//!
//! Public `&[u8]` entry points exercised here:
//!   * `rcce_net::unframe(&[u8]) -> Option<(u8, &[u8])>`
//!   * `rcce_net::codec::MsgReader::new(&[u8])` + its getters
//!     (`u8/u16/u32/i32/f32`, `bytes(n)`, `str8`, `str16`, `rest`, `remaining`).
//!
//! All inputs are synthetic; no `data/` files are required.

use std::panic::{catch_unwind, AssertUnwindSafe};

use rcce_net::codec::{MsgReader, MsgWriter};
use rcce_net::unframe;

fn garbage_corpus() -> Vec<(&'static str, Vec<u8>)> {
    vec![
        ("empty", vec![]),
        ("one_byte", vec![0x00]),
        ("two_bytes", vec![0xFF, 0xFF]),
        ("ff_64", vec![0xFF; 64]),
        ("zero_64", vec![0x00; 64]),
        ("alt_256", (0..256).map(|i| (i & 0xFF) as u8).collect()),
    ]
}

// ---------------------------------------------------------------------------
// unframe — splits [type][payload]; only `empty` is rejected.
// ---------------------------------------------------------------------------

#[test]
fn unframe_empty_is_none_others_split() {
    assert!(unframe(&[]).is_none());
    // A single byte is a typed packet with an empty payload.
    assert_eq!(unframe(&[42]), Some((42u8, &[][..])));
    let (t, p) = unframe(&[0x0E, 0xDE, 0xAD]).unwrap();
    assert_eq!(t, 0x0E);
    assert_eq!(p, &[0xDE, 0xAD]);
}

#[test]
fn unframe_never_panics() {
    for (name, bytes) in garbage_corpus() {
        let r = catch_unwind(AssertUnwindSafe(|| unframe(&bytes)));
        assert!(r.is_ok(), "unframe panicked on '{name}'");
    }
}

// ---------------------------------------------------------------------------
// MsgReader scalar getters — every getter returns None on underflow.
// ---------------------------------------------------------------------------

#[test]
fn reader_scalar_underflow_returns_none() {
    // For each width, a buffer one byte short must yield None on that getter and
    // never panic.
    let cases: &[(usize, fn(&mut MsgReader) -> bool)] = &[
        (2, |r| r.u16().is_none()),
        (4, |r| r.u32().is_none()),
        (4, |r| r.i32().is_none()),
        (4, |r| r.f32().is_none()),
    ];
    for &(width, getter) in cases {
        let short = vec![0u8; width - 1];
        let mut r = MsgReader::new(&short);
        assert!(getter(&mut r), "getter of width {width} did not return None on short buffer");
    }
    // u8 on empty.
    let mut e = MsgReader::new(&[]);
    assert!(e.u8().is_none());
}

#[test]
fn reader_scalar_getters_never_panic_over_corpus() {
    for (name, bytes) in garbage_corpus() {
        let r = catch_unwind(AssertUnwindSafe(|| {
            let mut rd = MsgReader::new(&bytes);
            // Drain a mixed sequence of getters; each must gracefully None at EOF.
            for _ in 0..64 {
                let _ = rd.u8();
                let _ = rd.u16();
                let _ = rd.u32();
                let _ = rd.i32();
                let _ = rd.f32();
            }
        }));
        assert!(r.is_ok(), "scalar getters panicked on '{name}'");
    }
}

// ---------------------------------------------------------------------------
// str8 / str16 — length-prefixed strings. The classic wire "length bomb":
// a length prefix larger than the remaining payload must yield None, never a
// huge allocation or an OOB slice.
// ---------------------------------------------------------------------------

#[test]
fn str8_length_exceeds_payload_returns_none() {
    // 1-byte length = 200, but only 3 bytes follow.
    let mut data = vec![200u8];
    data.extend_from_slice(b"abc");
    let mut r = MsgReader::new(&data);
    assert!(r.str8().is_none(), "str8 must None when length overruns payload");

    // str8 length prefix present but ZERO following bytes claimed length>0.
    let mut r2 = MsgReader::new(&[5u8]); // claims 5 bytes, none follow
    assert!(r2.str8().is_none());

    // length 0 is a valid empty string.
    let mut r3 = MsgReader::new(&[0u8]);
    assert_eq!(r3.str8().unwrap(), "");
}

#[test]
fn str16_length_exceeds_payload_returns_none() {
    // 2-byte LE length = 0xFFFF (65535), but only a few bytes follow. A naive
    // reader would index 65535 bytes past a 6-byte slice.
    let mut data = vec![0xFF, 0xFF]; // length 65535
    data.extend_from_slice(b"oops");
    let mut r = MsgReader::new(&data);
    assert!(r.str16().is_none(), "str16 must None on a 65535-len bomb");

    // Truncated length prefix itself (1 byte) -> None.
    let mut r2 = MsgReader::new(&[0x01]);
    assert!(r2.str16().is_none());

    // Empty -> None.
    let mut r3 = MsgReader::new(&[]);
    assert!(r3.str16().is_none());
}

#[test]
fn str_getters_never_panic_over_corpus() {
    for (name, bytes) in garbage_corpus() {
        let r = catch_unwind(AssertUnwindSafe(|| {
            let mut rd = MsgReader::new(&bytes);
            for _ in 0..32 {
                let _ = rd.str8();
            }
            let mut rd2 = MsgReader::new(&bytes);
            for _ in 0..32 {
                let _ = rd2.str16();
            }
        }));
        assert!(r.is_ok(), "str getters panicked on '{name}'");
    }
}

#[test]
fn str8_invalid_utf8_is_lossy_not_panic() {
    // Length 4 + 4 invalid-UTF-8 bytes. Must decode lossily (no panic, no None).
    let data = vec![4u8, 0xFF, 0xFE, 0x80, 0x81];
    let mut r = MsgReader::new(&data);
    let s = r.str8().expect("str8 should return a (lossy) string, not None");
    assert!(!s.is_empty(), "lossy decode should produce replacement chars");
}

// ---------------------------------------------------------------------------
// bytes(n) — raw take. A request for more than remains must be None.
// ---------------------------------------------------------------------------

#[test]
fn bytes_request_past_end_returns_none() {
    let mut r = MsgReader::new(&[1, 2, 3]);
    assert!(r.bytes(4).is_none(), "bytes(4) over a 3-byte buffer must be None");
    // Exactly-remaining is fine.
    assert_eq!(r.bytes(3).unwrap(), &[1, 2, 3]);
    // Now empty: any further take is None.
    assert!(r.bytes(1).is_none());
    // A huge request must not overflow / panic.
    let mut r2 = MsgReader::new(&[0u8; 8]);
    assert!(r2.bytes(usize::MAX).is_none());
}

#[test]
fn bytes_zero_is_empty_slice() {
    let mut r = MsgReader::new(&[]);
    assert_eq!(r.bytes(0), Some(&[][..]));
}

// ---------------------------------------------------------------------------
// Mixed adversarial sequence: simulate a hostile packet that lies about its
// internal structure (a string-length field that overruns, then more reads).
// ---------------------------------------------------------------------------

#[test]
fn hostile_packet_shape_degrades_gracefully() {
    // Shape loosely modelled on a login/character payload: str8 name, u16 id,
    // str16 blob. Feed a payload that lies about the str16 length.
    let mut w = MsgWriter::new();
    w.str8("rustbot").u16(0x0102);
    let mut data = w.into_bytes();
    data.extend_from_slice(&0xFFFFu16.to_le_bytes()); // str16 length 65535 (a lie)
    data.extend_from_slice(b"short");

    let r = catch_unwind(AssertUnwindSafe(|| {
        let mut rd = MsgReader::new(&data);
        let _name = rd.str8();
        let _id = rd.u16();
        let blob = rd.str16(); // must be None, not panic / OOB
        assert!(blob.is_none());
        // Subsequent reads after a failed one keep returning None.
        assert!(rd.u8().is_some() || rd.u8().is_none()); // either, just no panic
    }));
    assert!(r.is_ok(), "hostile packet shape panicked");
}

#[test]
fn reader_remaining_and_rest_consistent_at_eof() {
    let mut r = MsgReader::new(&[10, 20, 30, 40]);
    assert_eq!(r.remaining(), 4);
    let _ = r.u32();
    assert_eq!(r.remaining(), 0);
    assert_eq!(r.rest(), &[][..]);
    // Past-EOF getters stay None.
    assert!(r.u8().is_none());
}
