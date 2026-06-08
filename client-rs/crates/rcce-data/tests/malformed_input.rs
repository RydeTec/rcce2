//! Adversarial / malformed-input acceptance suite for the `rcce-data` on-disk
//! parsers.
//!
//! Core invariant under test (see repo `CLAUDE.md`, "Soft-fail on
//! server-controlled data"): **parsing malformed or hostile bytes must NEVER
//! panic.** A parser panic is a full client crash. Every public parse/decode
//! entry point must, for garbage / truncated / count-bomb input, return an
//! `Err`/`None`/graceful value instead of panicking, indexing out of bounds,
//! `unwrap()`-ing a short read, or multiply-overflowing a `count * size`
//! allocation.
//!
//! These are all synthetic byte arrays built in-test; the suite needs no files
//! from `data/` and runs on a clean checkout.
//!
//! Where a parser is *documented* infallible/no-panic, the test simply calls it
//! inside `catch_unwind` and fails on a captured panic. Where a parser returns
//! `Result`/`Option`, we additionally assert the error/None.

use std::panic::{catch_unwind, AssertUnwindSafe};

use rcce_data::{
    actors::ActorCatalog,
    anim::AnimSetCatalog,
    area::AreaScenery,
    attributes::AttributeNames,
    b3d::B3dModel,
    catalog::{MeshCatalog, MusicCatalog, SoundCatalog, TextureCatalog, CATALOG_SLOTS},
    emitter::EmitterConfig,
    interface::InterfaceLayout,
    items::ItemCatalog,
    money::MoneyConfig,
    reader::BlitzReader,
    texture::{decode_bmp, decode_dds, decode_jpeg, decode_png, decode_tga},
};

// ---------------------------------------------------------------------------
// Shared corpus of "hostile" byte slices that every parser must survive.
// ---------------------------------------------------------------------------

/// A small bundle of generically-malformed inputs. None of these is a valid
/// file of any format; a robust parser degrades gracefully on all of them.
fn garbage_corpus() -> Vec<(&'static str, Vec<u8>)> {
    vec![
        ("empty", vec![]),
        ("one_byte", vec![0x7F]),
        ("two_bytes", vec![0xFF, 0x00]),
        ("ff_64", vec![0xFF; 64]),
        ("zero_64", vec![0x00; 64]),
        ("ascii_64", vec![b'A'; 64]),
        ("ff_4096", vec![0xFF; 4096]),
        ("alt_256", (0..256).map(|i| (i & 0xFF) as u8).collect()),
    ]
}

/// Run `f` on each corpus input under `catch_unwind`; any panic is a hard
/// failure with the input name. `f`'s own return value is ignored — the point
/// is "does not panic". Used for parsers whose graceful return is checked
/// separately (or that are infallible).
fn assert_no_panic_over_corpus<F>(label: &str, f: F)
where
    F: Fn(&[u8]),
{
    for (name, bytes) in garbage_corpus() {
        let r = catch_unwind(AssertUnwindSafe(|| f(&bytes)));
        assert!(
            r.is_ok(),
            "{label}: PANICKED on malformed input '{name}' ({} bytes) — soft-fail violation",
            bytes.len()
        );
    }
}

// ===========================================================================
// reader::BlitzReader — the bounds-checking foundation everything else sits on.
// ===========================================================================

#[test]
fn blitz_reader_short_reads_error_never_panic() {
    // Each getter on an under-length buffer must return Err, not panic / index OOB.
    let inputs: Vec<Vec<u8>> = vec![vec![], vec![0x01], vec![0x01, 0x02], vec![0x01, 0x02, 0x03]];
    for bytes in inputs {
        let r = catch_unwind(AssertUnwindSafe(|| {
            // byte ok only with >=1; everything wider must Err on the short ones.
            let mut a = BlitzReader::new(&bytes);
            let _ = a.read_byte();
            let mut b = BlitzReader::new(&bytes);
            let _ = b.read_short();
            let mut c = BlitzReader::new(&bytes);
            let _ = c.read_short_u();
            let mut d = BlitzReader::new(&bytes);
            let _ = d.read_int();
            let mut e = BlitzReader::new(&bytes);
            let _ = e.read_float();
            let mut f = BlitzReader::new(&bytes);
            let _ = f.read_tag();
        }));
        assert!(r.is_ok(), "BlitzReader panicked on {} bytes", bytes.len());
    }

    // Concrete error assertions on an empty buffer.
    let mut r = BlitzReader::new(&[]);
    assert!(r.read_int().is_err());
    assert!(r.read_float().is_err());
    assert!(r.read_tag().is_err());
}

#[test]
fn blitz_reader_string_length_bomb_errors() {
    // A length prefix claiming i32::MAX bytes must Err (StringTooLong), never try
    // to allocate/index that many. This is the canonical "count bomb" at the
    // string primitive: a naive `take(len)` would index far past the slice.
    let mut buf = Vec::new();
    buf.extend_from_slice(&i32::MAX.to_le_bytes());
    buf.extend_from_slice(b"only a few bytes follow");
    let mut r = BlitzReader::new(&buf);
    assert!(r.read_string(260).is_err(), "huge string length must error");

    // A length within `max` but past the actual data must Err (UnexpectedEof),
    // not panic on the slice index.
    let mut buf2 = Vec::new();
    buf2.extend_from_slice(&200i32.to_le_bytes()); // <= max 260, but no bytes follow
    let mut r2 = BlitzReader::new(&buf2);
    assert!(r2.read_string(260).is_err());

    // A NEGATIVE length must be treated as empty, never as a giant unsigned size.
    let neg = (-1000i32).to_le_bytes();
    let mut r3 = BlitzReader::new(&neg);
    assert_eq!(r3.read_string(260).unwrap(), "");
}

#[test]
fn blitz_reader_cstr_unterminated_is_bounded() {
    // An unterminated cstr (no NUL) must stop at `max`, not run off the end.
    let bytes = vec![b'x'; 1000];
    let mut r = BlitzReader::new(&bytes);
    let s = r.read_cstr(16).expect("cstr should bound, not error");
    assert_eq!(s.len(), 16, "cstr must cap at max when unterminated");

    // cstr on empty input errors (can't read the first byte), no panic.
    let mut e = BlitzReader::new(&[]);
    assert!(e.read_cstr(16).is_err());
}

#[test]
fn blitz_reader_seek_out_of_bounds_errors() {
    let mut r = BlitzReader::new(&[0u8; 4]);
    assert!(r.seek(5).is_err(), "seek past end must error");
    assert!(r.seek(4).is_ok(), "seek to exactly len is allowed (EOF cursor)");
}

// ===========================================================================
// catalog::{Mesh,Texture,Music,Sound}Catalog — 65535-slot index + records.
// ===========================================================================

const INDEX_BYTES: usize = CATALOG_SLOTS * 4;

#[test]
fn catalogs_reject_short_index() {
    // Anything smaller than the 262_140-byte index must error, not index OOB.
    for n in [0usize, 1, 64, INDEX_BYTES - 1] {
        let data = vec![0u8; n];
        assert!(MeshCatalog::parse(&data).is_err(), "mesh n={n}");
        assert!(TextureCatalog::parse(&data).is_err(), "tex n={n}");
        assert!(MusicCatalog::parse(&data).is_err(), "music n={n}");
        assert!(SoundCatalog::parse(&data).is_err(), "sound n={n}");
    }
}

#[test]
fn catalogs_do_not_panic_on_garbage() {
    // Full corpus including the over-index 4096-byte case (still < index, must Err),
    // plus an all-0xFF *index-sized* buffer (every slot points to a bogus offset).
    assert_no_panic_over_corpus("MeshCatalog", |b| {
        let _ = MeshCatalog::parse(b);
    });
    assert_no_panic_over_corpus("TextureCatalog", |b| {
        let _ = TextureCatalog::parse(b);
    });
    assert_no_panic_over_corpus("MusicCatalog", |b| {
        let _ = MusicCatalog::parse(b);
    });
    assert_no_panic_over_corpus("SoundCatalog", |b| {
        let _ = SoundCatalog::parse(b);
    });
}

#[test]
fn catalog_offset_bomb_index_into_void_does_not_panic() {
    // A structurally-valid index whose every slot points WAY past EOF. A naive
    // reader would seek+read out of bounds. Must skip the slot, not panic.
    let mut data = vec![0u8; INDEX_BYTES];
    // slot 7 -> offset 0x7FFFFFF0 (huge but positive); slot 9 -> just past EOF.
    data[7 * 4..7 * 4 + 4].copy_from_slice(&0x7FFF_FFF0i32.to_le_bytes());
    let near_eof = (INDEX_BYTES as i32) + 1;
    data[9 * 4..9 * 4 + 4].copy_from_slice(&near_eof.to_le_bytes());
    // slot 11 -> a negative offset (must be treated as "skip", never as index).
    data[11 * 4..11 * 4 + 4].copy_from_slice(&(-5i32).to_le_bytes());

    let m = MeshCatalog::parse(&data).expect("index present");
    assert!(m.value.get(7).is_none(), "bad-offset slot must not resolve");
    assert!(m.value.get(9).is_none());
    assert!(m.value.get(11).is_none());
    // Texture/Music/Sound take the same offset path.
    assert!(TextureCatalog::parse(&data).is_ok());
    assert!(MusicCatalog::parse(&data).is_ok());
    assert!(SoundCatalog::parse(&data).is_ok());
}

#[test]
fn catalog_record_truncated_string_skips_not_panics() {
    // Valid index, one slot points to a record whose string length prefix claims
    // more bytes than exist. The slot must be skipped (logged), parse succeeds.
    let mut data = vec![0u8; INDEX_BYTES];
    let rec_off = INDEX_BYTES as i32;
    data[3 * 4..3 * 4 + 4].copy_from_slice(&rec_off.to_le_bytes());
    // Music record = just a string. Length prefix = 9999, but no bytes follow.
    data.extend_from_slice(&9999i32.to_le_bytes());
    let parsed = MusicCatalog::parse(&data).expect("index present");
    assert!(parsed.value.get(3).is_none(), "truncated record must be skipped");
    assert_eq!(parsed.value.entries.len(), 0);
}

// ===========================================================================
// b3d::B3dModel — the deepest/riskiest format (nested length-tagged chunks).
// ===========================================================================

/// Build a minimal `BB3D` container: magic + size + version, then `body`.
fn b3d_container(body: &[u8]) -> Vec<u8> {
    let mut v = Vec::new();
    v.extend_from_slice(b"BB3D");
    // size = remaining bytes after this i32 (version + body)
    let size = (4 + body.len()) as i32;
    v.extend_from_slice(&size.to_le_bytes());
    v.extend_from_slice(&1i32.to_le_bytes()); // version
    v.extend_from_slice(body);
    v
}

#[test]
fn b3d_bad_magic_errors() {
    assert!(B3dModel::parse(b"NOTB").is_err());
    assert!(B3dModel::parse(b"XXXXYYYY").is_err());
}

#[test]
fn b3d_garbage_does_not_panic() {
    assert_no_panic_over_corpus("B3dModel", |b| {
        let _ = B3dModel::parse(b);
    });
}

#[test]
fn b3d_truncated_header_does_not_panic() {
    // "BB3D" then a size, cut off before the version int.
    let mut v = Vec::new();
    v.extend_from_slice(b"BB3D");
    v.extend_from_slice(&100i32.to_le_bytes()); // claims 100 bytes
                                                // (no version, no body)
    let r = catch_unwind(AssertUnwindSafe(|| B3dModel::parse(&v)));
    assert!(r.is_ok(), "B3d truncated header panicked");
    assert!(r.unwrap().is_err());
}

#[test]
fn b3d_chunk_size_overruns_container_does_not_panic() {
    // A NODE chunk whose declared size is enormous (far past the file). The chunk
    // walker clamps chunk_end to the container end; a naive seek would jump OOB.
    let mut body = Vec::new();
    body.extend_from_slice(b"NODE");
    body.extend_from_slice(&0x7FFF_FFFFi32.to_le_bytes()); // absurd chunk size
    body.extend_from_slice(b"\0"); // an empty node name then nothing
    let file = b3d_container(&body);
    let r = catch_unwind(AssertUnwindSafe(|| B3dModel::parse(&file)));
    assert!(r.is_ok(), "B3d oversized chunk panicked");
    // Whatever it returns (Ok with empty meshes or Err) is fine; not panicking is.
}

#[test]
fn b3d_negative_chunk_size_does_not_panic() {
    // chunk_header does `.max(0)` on the size; a negative size must become 0,
    // not a giant unsigned seek.
    let mut body = Vec::new();
    body.extend_from_slice(b"TEXS");
    body.extend_from_slice(&(-1i32).to_le_bytes()); // negative size
    body.extend_from_slice(&[0xFF; 16]);
    let file = b3d_container(&body);
    let r = catch_unwind(AssertUnwindSafe(|| B3dModel::parse(&file)));
    assert!(r.is_ok(), "B3d negative chunk size panicked");
}

#[test]
fn b3d_truncated_mid_node_does_not_panic() {
    // A well-formed header + a NODE chunk header that promises more body than is
    // present (the node's float transform block is cut off mid-record).
    let mut body = Vec::new();
    body.extend_from_slice(b"NODE");
    body.extend_from_slice(&200i32.to_le_bytes()); // promises 200 bytes
    body.extend_from_slice(b"Joint\0"); // name
    body.extend_from_slice(&1.0f32.to_le_bytes()); // px ... then truncated
    let file = b3d_container(&body);
    let r = catch_unwind(AssertUnwindSafe(|| B3dModel::parse(&file)));
    assert!(r.is_ok(), "B3d truncated NODE panicked");
}

#[test]
fn b3d_deeply_nested_nodes_do_not_stack_overflow() {
    // NODE parsing recurses on child NODEs. A long chain of nested NODE chunks
    // exercises the recursion depth; a runaway recursion would stack-overflow
    // (which surfaces as a process abort, not a catchable panic — so if this
    // test ever "passes by crashing", investigate). We keep depth modest (256)
    // to document the recursion without risking the test process.
    fn nested(depth: usize) -> Vec<u8> {
        // innermost: an empty node (name NUL + 10 floats of transform)
        let mut inner = Vec::new();
        inner.extend_from_slice(b"\0"); // empty name
        for _ in 0..10 {
            inner.extend_from_slice(&0.0f32.to_le_bytes());
        }
        let mut cur = inner;
        for _ in 0..depth {
            let mut node = Vec::new();
            node.extend_from_slice(b"\0"); // name
            for _ in 0..10 {
                node.extend_from_slice(&0.0f32.to_le_bytes());
            }
            // wrap `cur` as a child NODE chunk
            let mut child = Vec::new();
            child.extend_from_slice(b"NODE");
            child.extend_from_slice(&(cur.len() as i32).to_le_bytes());
            child.extend_from_slice(&cur);
            node.extend_from_slice(&child);
            cur = node;
        }
        // top-level NODE chunk
        let mut body = Vec::new();
        body.extend_from_slice(b"NODE");
        body.extend_from_slice(&(cur.len() as i32).to_le_bytes());
        body.extend_from_slice(&cur);
        body
    }
    let file = b3d_container(&nested(256));
    let r = catch_unwind(AssertUnwindSafe(|| B3dModel::parse(&file)));
    assert!(r.is_ok(), "B3d deep nesting panicked");
}

// ===========================================================================
// actors::ActorCatalog — back-to-back variable-length records to EOF.
// ===========================================================================

#[test]
fn actors_garbage_does_not_panic_and_returns_ok() {
    // ActorCatalog stops at the first bad record; on pure garbage it yields an
    // empty/partial catalog, never panics.
    assert_no_panic_over_corpus("ActorCatalog", |b| {
        let _ = ActorCatalog::parse(b);
    });
    // Concrete: empty input parses to an empty catalog (Ok).
    let cat = ActorCatalog::parse(&[]).expect("empty actors");
    assert!(cat.templates.is_empty());
}

#[test]
fn actors_truncated_record_stops_cleanly() {
    // A record that starts (id short) but is cut off in the first string must not
    // panic and must yield no completed templates.
    let mut data = Vec::new();
    data.extend_from_slice(&5u16.to_le_bytes()); // id
    data.extend_from_slice(&9_000_000i32.to_le_bytes()); // race string len: huge
    let cat = ActorCatalog::parse(&data).expect("parse");
    assert!(cat.templates.is_empty(), "truncated record must not complete");
}

// ===========================================================================
// anim::AnimSetCatalog — back-to-back records, 150 clips each.
// ===========================================================================

#[test]
fn anim_garbage_does_not_panic() {
    assert_no_panic_over_corpus("AnimSetCatalog", |b| {
        let _ = AnimSetCatalog::parse(b);
    });
    let cat = AnimSetCatalog::parse(&[]).expect("empty anim");
    assert!(cat.sets.is_empty());
}

#[test]
fn anim_truncated_mid_clip_stops_cleanly() {
    // id + set-name then a clip whose name length is bogus. The 150-clip loop
    // must abort the set (not panic) when a string read fails.
    let mut data = Vec::new();
    data.extend_from_slice(&1u16.to_le_bytes()); // set id
    data.extend_from_slice(&0i32.to_le_bytes()); // set name "" (len 0)
    data.extend_from_slice(&50_000_000i32.to_le_bytes()); // first clip name len: huge
    let cat = AnimSetCatalog::parse(&data).expect("parse");
    assert!(cat.sets.is_empty(), "an aborted set must not be inserted");
}

// ===========================================================================
// area::AreaScenery — 41-byte header, then count-prefixed scenery/water/terrain.
// ===========================================================================

#[test]
fn area_garbage_does_not_panic() {
    assert_no_panic_over_corpus("AreaScenery", |b| {
        let _ = AreaScenery::parse(b);
    });
}

#[test]
fn area_truncated_header_errors_or_empty_no_panic() {
    // Inputs shorter than the 41-byte header + 2-byte count. parse() seeks to 41
    // then reads the scenery count; a buffer < 43 bytes must error on the count
    // read, not panic.
    for n in [0usize, 1, 10, 41, 42] {
        let data = vec![0u8; n];
        let r = catch_unwind(AssertUnwindSafe(|| AreaScenery::parse(&data)));
        assert!(r.is_ok(), "area n={n} panicked");
    }
}

#[test]
fn area_scenery_count_bomb_does_not_panic() {
    // Valid 41-byte header, then a scenery count of 65535 but NO record bytes.
    // The per-record reads must error out and stop, not panic / index OOB. (The
    // count is u16 so Vec::with_capacity(65535) is bounded — the risk is the
    // unguarded per-field reads against a truncated body.)
    let mut data = vec![0u8; 41];
    data.extend_from_slice(&65535u16.to_le_bytes()); // claims 65535 sceneries
    let r = catch_unwind(AssertUnwindSafe(|| AreaScenery::parse(&data)));
    assert!(r.is_ok(), "area scenery-count bomb panicked");
    // Graceful outcome: parse returns Err (UnexpectedEof on the first record's
    // missing bytes) — the important property is that it errors rather than
    // panicking or fabricating 65535 sceneries. If a future impl instead returns
    // Ok, the scenery list must be empty.
    match r.unwrap() {
        Ok(area) => assert!(
            area.sceneries.is_empty(),
            "no record bytes -> no sceneries should be decoded"
        ),
        Err(_) => {} // erroring out on the truncated body is also graceful
    }
}

/// AREA TERRAIN GRID BOMB. `AreaScenery::parse` reads the LOD-terrain `grid`
/// field as an `i32` (then `as u32`) straight off disk and computes
/// `verts = (grid + 1) * (grid + 1)`, then calls `Vec::with_capacity(verts)`
/// BEFORE reading a single height float (`area.rs:275-276`). A hostile/corrupt
/// area `.dat` declaring `grid = i32::MAX` makes `verts ≈ 4.6e18`, and
/// `Vec::with_capacity` PANICS with "capacity overflow" (the value exceeds
/// `isize::MAX`). The terrain block is wrapped in a closure whose `Result` is
/// discarded (`let _ = parsed`), but a `panic!` is NOT a `Result` — it
/// propagates out of `parse` and crashes the client. A negative i32 grid (e.g.
/// `0xFFFFFFFF as u32 = 4.29e9`) triggers the same.
///
/// FIXED: `AreaScenery::parse` now rejects an implausible terrain `grid`
/// (negative or > 4096) with `ReadError::CountTooLarge` BEFORE the
/// `(grid+1)^2` allocation, so it soft-fails instead of panicking. This test
/// gates that fix.
#[test]
fn area_terrain_grid_bomb_does_not_oom_or_panic() {
    // The terrain block computes verts = (grid+1)^2 from a wire i32 and
    // Vec::with_capacity(verts) BEFORE reading any height. A hostile `grid` near
    // i32::MAX makes (grid+1)^2 a ~4.6e18 capacity request -> capacity-overflow
    // panic / OOM in a naive impl. We drive the parser all the way to the terrain
    // block (empty scenery/water/colbox/emitter) then feed a giant grid.
    let mut data = vec![0u8; 41]; // zeroed header
    let push_u16 = |d: &mut Vec<u8>, v: u16| d.extend_from_slice(&v.to_le_bytes());
    let push_i32 = |d: &mut Vec<u8>, v: i32| d.extend_from_slice(&v.to_le_bytes());
    push_u16(&mut data, 0); // scenery count = 0
    push_u16(&mut data, 0); // water count = 0
    push_u16(&mut data, 0); // collision-box count = 0
    push_u16(&mut data, 0); // emitter count = 0
    push_u16(&mut data, 1); // ONE terrain
    push_u16(&mut data, 0); // base tex
    push_u16(&mut data, 0); // detail tex
    push_i32(&mut data, 0x7FFF_FFFF); // grid = i32::MAX -> (grid+1)^2 overflows usize math
                                      // (no height bytes follow)
    let r = catch_unwind(AssertUnwindSafe(|| AreaScenery::parse(&data)));
    assert!(
        r.is_ok(),
        "area terrain grid-bomb panicked/OOM'd — Vec::with_capacity((grid+1)^2) is unguarded"
    );
}

// ===========================================================================
// attributes::AttributeNames — assignment byte + 40 records.
// ===========================================================================

#[test]
fn attributes_garbage_does_not_panic() {
    assert_no_panic_over_corpus("AttributeNames", |b| {
        let _ = AttributeNames::parse(b);
    });
}

#[test]
fn attributes_truncated_errors() {
    // assignment byte present but the 40-record body cut off mid-string.
    let mut data = vec![0u8]; // assignment
    data.extend_from_slice(&9_000_000i32.to_le_bytes()); // first name len: huge
    assert!(AttributeNames::parse(&data).is_err(), "truncated attrs must error");
    // Empty input: can't even read the assignment byte.
    assert!(AttributeNames::parse(&[]).is_err());
}

// ===========================================================================
// emitter::EmitterConfig — fixed field sequence.
// ===========================================================================

#[test]
fn emitter_garbage_does_not_panic() {
    assert_no_panic_over_corpus("EmitterConfig", |b| {
        let _ = EmitterConfig::parse(b);
    });
}

#[test]
fn emitter_truncated_errors() {
    // A few ints then EOF mid-field must Err, not panic.
    let mut data = Vec::new();
    data.extend_from_slice(&100i32.to_le_bytes()); // max_particles
    data.extend_from_slice(&1i32.to_le_bytes()); // particles_per_frame
    data.extend_from_slice(&[0x00, 0x01]); // partial next field
    assert!(EmitterConfig::parse(&data).is_err());
    assert!(EmitterConfig::parse(&[]).is_err());
    assert!(EmitterConfig::parse(&[0xFF; 7]).is_err());
}

// ===========================================================================
// interface::InterfaceLayout — fixed component sequence.
// ===========================================================================

#[test]
fn interface_garbage_does_not_panic() {
    assert_no_panic_over_corpus("InterfaceLayout", |b| {
        let _ = InterfaceLayout::parse(b);
    });
}

#[test]
fn interface_truncated_errors() {
    // The layout needs ~1.4 KB; anything short must Err (mirrors the in-crate
    // rejects_truncated test but over a wider range).
    for n in [0usize, 10, 23, 100, 500] {
        assert!(InterfaceLayout::parse(&vec![0u8; n]).is_err(), "iface n={n}");
    }
}

// ===========================================================================
// money::MoneyConfig — 4 strings + 3 shorts.
// ===========================================================================

#[test]
fn money_garbage_does_not_panic() {
    assert_no_panic_over_corpus("MoneyConfig", |b| {
        let _ = MoneyConfig::parse(b);
    });
}

#[test]
fn money_truncated_errors() {
    assert!(MoneyConfig::parse(&[]).is_err());
    // A valid first string then EOF before the second.
    let mut data = Vec::new();
    data.extend_from_slice(&6i32.to_le_bytes());
    data.extend_from_slice(b"Copper");
    assert!(MoneyConfig::parse(&data).is_err());
    // An over-long name length must Err (StringTooLong), not allocate.
    let mut bomb = Vec::new();
    bomb.extend_from_slice(&1_000_000i32.to_le_bytes());
    assert!(MoneyConfig::parse(&bomb).is_err());
}

#[test]
fn money_format_never_panics_on_extreme_amounts() {
    // format() does integer division by tier multipliers; a degenerate config
    // (zero multipliers) must not divide-by-zero. The impl guards with `.max(1)`.
    let cfg = MoneyConfig {
        name1: "a".into(),
        name2: "b".into(),
        name3: "c".into(),
        name4: "d".into(),
        mult2: 0,
        mult3: 0,
        mult4: 0,
    };
    let r = catch_unwind(AssertUnwindSafe(|| {
        let _ = cfg.format(i64::MAX);
        let _ = cfg.format(i64::MIN);
        let _ = cfg.format(0);
    }));
    assert!(r.is_ok(), "MoneyConfig::format panicked / divided by zero");
}

// ===========================================================================
// items::ItemCatalog — INFALLIBLE (no Result). Must simply return on garbage.
// ===========================================================================

#[test]
fn items_infallible_parse_never_panics() {
    // The public contract: parse() returns an ItemCatalog (possibly empty) for
    // ANY input, never panicking. Run the full corpus plus targeted truncations.
    assert_no_panic_over_corpus("ItemCatalog", |b| {
        let _ = ItemCatalog::parse(b);
    });
}

#[test]
fn items_empty_and_garbage_yield_empty_catalog() {
    assert_eq!(ItemCatalog::parse(&[]).items.len(), 0);
    // A lone negative id short is treated as corrupt -> empty.
    let neg = (-1i16).to_le_bytes();
    assert_eq!(ItemCatalog::parse(&neg).items.len(), 0);
    // All-0xFF: first short is negative id -> stop immediately, empty.
    assert_eq!(ItemCatalog::parse(&[0xFF; 64]).items.len(), 0);
}

#[test]
fn items_truncated_record_stops_without_panic() {
    // A valid id + name then a bogus string length deep in the record. Must stop,
    // keeping zero completed items, never panic.
    let mut data = Vec::new();
    data.extend_from_slice(&3i16.to_le_bytes()); // id
    data.extend_from_slice(&5i32.to_le_bytes()); // name len 5
    data.extend_from_slice(b"Sword");
    data.extend_from_slice(&9_000_000i32.to_le_bytes()); // excl_race len: huge -> abort
    let r = catch_unwind(AssertUnwindSafe(|| ItemCatalog::parse(&data)));
    assert!(r.is_ok(), "ItemCatalog panicked on truncated record");
    assert_eq!(r.unwrap().items.len(), 0);
}

// ===========================================================================
// texture decoders — decode_dds / decode_tga / decode_jpeg / decode_bmp /
// decode_png. Each returns Option<Image>; None on bad/unsupported input.
// ===========================================================================

#[test]
fn texture_decoders_garbage_return_none_no_panic() {
    for (name, bytes) in garbage_corpus() {
        for (dec_name, dec) in [
            ("dds", decode_dds as fn(&[u8]) -> _),
            ("tga", decode_tga),
            ("jpeg", decode_jpeg),
            ("bmp", decode_bmp),
            ("png", decode_png),
        ] {
            let r = catch_unwind(AssertUnwindSafe(|| dec(&bytes)));
            assert!(
                r.is_ok(),
                "decode_{dec_name} PANICKED on garbage '{name}' ({} bytes)",
                bytes.len()
            );
            // Garbage is never a valid image.
            assert!(
                r.unwrap().is_none(),
                "decode_{dec_name} accepted garbage '{name}' as an image"
            );
        }
    }
}

#[test]
fn dds_dimension_bomb_returns_none() {
    // Valid "DDS " magic + 128-byte header declaring 100000x100000 (> the
    // documented 16384 clamp). Must return None (rejected), never allocate
    // 100000*100000*4 bytes or panic.
    let mut b = vec![0u8; 128];
    b[0..4].copy_from_slice(b"DDS ");
    b[12..16].copy_from_slice(&100_000u32.to_le_bytes()); // height
    b[16..20].copy_from_slice(&100_000u32.to_le_bytes()); // width
    b[84..88].copy_from_slice(b"DXT1");
    let r = catch_unwind(AssertUnwindSafe(|| decode_dds(&b)));
    assert!(r.is_ok(), "decode_dds dimension bomb panicked");
    assert!(r.unwrap().is_none(), "decode_dds must reject >16384 dims");
}

#[test]
fn dds_dimension_bomb_zero_dims_returns_none() {
    let mut b = vec![0u8; 128];
    b[0..4].copy_from_slice(b"DDS ");
    b[12..16].copy_from_slice(&0u32.to_le_bytes()); // height 0
    b[16..20].copy_from_slice(&0u32.to_le_bytes()); // width 0
    b[84..88].copy_from_slice(b"DXT1");
    assert!(decode_dds(&b).is_none());
}

#[test]
fn dds_truncated_block_data_returns_none() {
    // Valid 4x4 DXT1 header but the 8-byte color block is missing.
    let mut b = vec![0u8; 128];
    b[0..4].copy_from_slice(b"DDS ");
    b[12..16].copy_from_slice(&4u32.to_le_bytes());
    b[16..20].copy_from_slice(&4u32.to_le_bytes());
    b[84..88].copy_from_slice(b"DXT1");
    // (no block bytes appended -> data.len() < bw*bh*block_bytes)
    assert!(decode_dds(&b).is_none(), "truncated DXT1 must return None");
}

#[test]
fn bmp_dimension_bomb_returns_none() {
    // Valid "BM" + header declaring 100000x100000 @ 32bpp, but only the 54-byte
    // header present. decode_bmp checks `b.len() < data_offset + stride*height`
    // FIRST, so it must return None without allocating width*height*4.
    let mut b = vec![0u8; 54];
    b[0] = b'B';
    b[1] = b'M';
    b[10] = 54; // data offset
    b[14] = 40; // header size
    b[18..22].copy_from_slice(&100_000i32.to_le_bytes()); // width
    b[22..26].copy_from_slice(&100_000i32.to_le_bytes()); // height
    b[28] = 32; // bpp
    let r = catch_unwind(AssertUnwindSafe(|| decode_bmp(&b)));
    assert!(r.is_ok(), "decode_bmp dimension bomb panicked");
    assert!(r.unwrap().is_none(), "decode_bmp must reject when data is absent");
}

#[test]
fn bmp_negative_or_zero_height_handled() {
    // height == 0 is invalid; a negative height is "top-down" (valid in BMP). Both
    // must be handled without panicking.
    let mut b = vec![0u8; 54];
    b[0] = b'B';
    b[1] = b'M';
    b[10] = 54;
    b[14] = 40;
    b[18..22].copy_from_slice(&2i32.to_le_bytes()); // width 2
    b[22..26].copy_from_slice(&0i32.to_le_bytes()); // height 0 -> invalid
    b[28] = 24;
    assert!(decode_bmp(&b).is_none(), "zero height must be rejected");
}

#[test]
fn bmp_bad_bpp_returns_none() {
    let mut b = vec![0u8; 54];
    b[0] = b'B';
    b[1] = b'M';
    b[10] = 54;
    b[14] = 40;
    b[18] = 2;
    b[22] = 2;
    b[28] = 8; // 8bpp unsupported
    assert!(decode_bmp(&b).is_none());
}

#[test]
fn tga_truncated_pixel_data_returns_none() {
    // Type 2 (uncompressed) TGA header declaring 8x8x32 but with no pixel bytes.
    // The `b.len() < end` check must catch it -> None, no panic.
    let mut b = vec![0u8; 18];
    b[2] = 2; // uncompressed true-color
    b[12] = 8; // width 8
    b[14] = 8; // height 8
    b[16] = 32; // bpp
    let r = catch_unwind(AssertUnwindSafe(|| decode_tga(&b)));
    assert!(r.is_ok(), "decode_tga truncated pixels panicked");
    assert!(r.unwrap().is_none(), "truncated TGA pixels must return None");
}

#[test]
fn tga_rle_runaway_does_not_panic() {
    // RLE (type 10) TGA whose RLE packets claim more pixels than data provides.
    // The decoder must bail (None) at the bounds check, not index OOB.
    let mut b = vec![0u8; 18];
    b[2] = 10; // RLE
    b[12] = 16; // width 16
    b[14] = 16; // height 16 -> 256 px
    b[16] = 32; // bpp
                // A single RLE packet header claiming a 128-run, but no pixel bytes follow.
    b.push(0x80 | 0x7F); // run of 128
    let r = catch_unwind(AssertUnwindSafe(|| decode_tga(&b)));
    assert!(r.is_ok(), "decode_tga RLE runaway panicked");
    assert!(r.unwrap().is_none());
}

#[test]
fn tga_bad_bpp_and_colormap_return_none() {
    let mut b = vec![0u8; 18];
    b[2] = 2;
    b[12] = 4;
    b[14] = 4;
    b[16] = 8; // 8bpp unsupported
    assert!(decode_tga(&b).is_none());

    let mut c = vec![0u8; 18];
    c[1] = 1; // colormap present -> unsupported
    c[2] = 2;
    c[12] = 4;
    c[14] = 4;
    c[16] = 32;
    assert!(decode_tga(&c).is_none());
}

/// TGA DIMENSION BOMB. `decode_tga` allocates `vec![0u8; width*height*bytes_pp]`
/// at the TOP of the function — BEFORE any data-length validation — and (unlike
/// `decode_dds`) applies NO max-dimension clamp. A TGA header declaring the
/// maximum 65535x65535 @ 32bpp forces a ~17 GB zero-filled allocation, which
/// OOM-aborts the process (an uncatchable abort, not a Rust panic). This is a
/// genuine soft-fail violation: a hostile/corrupt .tga from `data/` (or any
/// texture path) crashes the client.
///
/// FIXED: `decode_tga` now clamps width/height to 16384 (matching `decode_dds`)
/// BEFORE the `vec![0u8; w*h*bpp]` allocation, so an absurd header returns
/// `None` instead of OOM-aborting. This test gates that fix.
#[test]
fn tga_dimension_bomb_should_return_none() {
    // 65535 x 65535 @ 32bpp => ~17.18 GB requested allocation.
    let mut b = vec![0u8; 18];
    b[2] = 2; // uncompressed true-color
    b[12..14].copy_from_slice(&65535u16.to_le_bytes()); // width
    b[14..16].copy_from_slice(&65535u16.to_le_bytes()); // height
    b[16] = 32; // bpp
    let r = catch_unwind(AssertUnwindSafe(|| decode_tga(&b)));
    // If decode_tga were fixed, it would return None here without OOM.
    assert!(r.is_ok(), "decode_tga panicked on dimension bomb");
    assert!(
        r.unwrap().is_none(),
        "decode_tga must reject an absurd 65535x65535 header"
    );
}

#[test]
fn png_garbage_returns_none() {
    // A valid PNG magic but truncated stream -> None via the png crate's error
    // path, never a panic.
    let png_magic = [0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
    let mut b = png_magic.to_vec();
    b.extend_from_slice(&[0xFF; 32]); // junk after the signature
    let r = catch_unwind(AssertUnwindSafe(|| decode_png(&b)));
    assert!(r.is_ok(), "decode_png panicked on truncated stream");
    assert!(r.unwrap().is_none());
}

#[test]
fn jpeg_garbage_returns_none() {
    // SOI marker then junk -> the jpeg_decoder errors out to None.
    let mut b = vec![0xFF, 0xD8]; // JPEG SOI
    b.extend_from_slice(&[0xFF; 32]);
    let r = catch_unwind(AssertUnwindSafe(|| decode_jpeg(&b)));
    assert!(r.is_ok(), "decode_jpeg panicked on junk");
    assert!(r.unwrap().is_none());
}
