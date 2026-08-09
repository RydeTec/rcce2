use rcce_server_core::{ActorCatalog, ActorParseCompletion};
use std::path::{Path, PathBuf};

fn fixture(name: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../../editor-rs/test-data/consensus")
        .join(name)
        .join("Data/Server Data/Actors.dat")
}

#[test]
fn happy_server_view_is_four_complete_records_and_preserves_no_mesh() {
    let bytes = std::fs::read(fixture("happy")).unwrap();
    let parsed = ActorCatalog::parse_with_evidence(&bytes);
    assert_eq!(parsed.value.len(), 4);
    assert_eq!(parsed.records.len(), 4);
    assert_eq!(parsed.completion, ActorParseCompletion::Complete);
    assert_eq!(parsed.value.get(1).unwrap().mesh_ids[0], -1);
}

#[test]
fn provisional_server_view_preserves_high_id_disagreement_evidence() {
    let bytes = std::fs::read(fixture("provisional")).unwrap();
    let parsed = ActorCatalog::parse_with_evidence(&bytes);
    assert_eq!(parsed.value.len(), 2);
    assert_eq!(
        parsed.completion,
        ActorParseCompletion::NegativeIdTerminator {
            offset: parsed.records[1].span.end,
            raw: 65535
        }
    );
    let actor = &parsed.records[1];
    assert_eq!(
        &bytes[actor.race_span.start..actor.race_span.end],
        b"raw-\xff-name"
    );
    assert!(parsed.value.get(2).unwrap().race.contains('\u{fffd}'));
}
