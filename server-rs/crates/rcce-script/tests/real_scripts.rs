//! Parse-coverage gate: every shipped `.rsl` script must parse. Parity depends
//! on 0 parse failures across `data/Server Data/Scripts` (docs/rust-server/
//! PARITY.md, Cycle 96), so a parser regression fails this test rather than
//! passing CI silently. The per-file breakdown is still printed for diagnostics.

use std::path::PathBuf;

#[test]
fn parse_all_shipped_scripts() {
    let dir = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../data/Server Data/Scripts");
    if !dir.is_dir() {
        eprintln!("skip: no scripts dir at {dir:?}");
        return;
    }
    let (mut ok, mut fail) = (0u32, 0u32);
    let mut fails = Vec::new();
    for entry in std::fs::read_dir(&dir).unwrap().flatten() {
        let p = entry.path();
        if p.extension().and_then(|s| s.to_str()) != Some("rsl") {
            continue;
        }
        let src = std::fs::read_to_string(&p).unwrap_or_default();
        match rcce_script::parser::parse(&src) {
            Ok(_) => ok += 1,
            Err(err) => {
                fail += 1;
                fails.push((p.file_name().unwrap().to_string_lossy().into_owned(), err));
            }
        }
    }
    eprintln!("RSL parse coverage: {ok} ok / {} total", ok + fail);
    for (f, e) in fails.iter().take(12) {
        eprintln!("  FAIL {f}: {e}");
    }
    assert!(ok > 0, "no .rsl scripts found in {dir:?} — coverage gate ran against nothing");
    assert_eq!(
        fail, 0,
        "{fail} shipped script(s) failed to parse (see FAIL lines above) — parity requires 0 parse failures"
    );
}
