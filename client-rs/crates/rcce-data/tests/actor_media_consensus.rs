use rcce_data::{ActorCatalog, ActorParseCompletion, MeshCatalog, MeshSlotDisposition};
use std::path::{Path, PathBuf};

fn fixture(name: &str, relative: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../../editor-rs/test-data/consensus")
        .join(name)
        .join(relative)
}

#[test]
fn happy_actor_view_is_four_complete_records_and_preserves_no_mesh() {
    let bytes = std::fs::read(fixture("happy", "Data/Server Data/Actors.dat")).unwrap();
    let parsed = ActorCatalog::parse_with_evidence(&bytes);
    assert_eq!(parsed.value.templates.len(), 4);
    assert_eq!(parsed.records.len(), 4);
    assert_eq!(parsed.completion, ActorParseCompletion::Complete);
    assert_eq!(parsed.value.mesh_for(1, 0), None);
    assert!(parsed
        .records
        .windows(2)
        .all(|pair| pair[0].span.end <= pair[1].span.start));
}

#[test]
fn provisional_actor_view_retains_raw_non_utf8_and_truncated_tail() {
    let bytes = std::fs::read(fixture("provisional", "Data/Server Data/Actors.dat")).unwrap();
    let parsed = ActorCatalog::parse_with_evidence(&bytes);
    assert_eq!(parsed.value.templates.len(), 4);
    assert!(matches!(
        parsed.completion,
        ActorParseCompletion::Truncated { .. }
    ));
    let actor = parsed.records.iter().find(|record| record.id == 2).unwrap();
    assert_eq!(
        &bytes[actor.race_span.start..actor.race_span.end],
        b"raw-\xff-name"
    );
    assert!(parsed.value.templates[&2].race.contains('\u{fffd}'));
}

#[test]
fn mesh_evidence_preserves_alias_gap_outside_slice_invalid_and_raw_filename() {
    let bytes = std::fs::read(fixture("provisional", "Data/Game Data/Meshes.dat")).unwrap();
    let parsed = MeshCatalog::parse_with_evidence(&bytes).unwrap();
    assert_eq!(parsed.slot(8), MeshSlotDisposition::Alias { target: 7 });
    assert_eq!(parsed.slot(9), MeshSlotDisposition::Gap);
    assert_eq!(parsed.slot(11), MeshSlotDisposition::InvalidOffset(-1));
    let record = parsed.records.iter().find(|record| record.id == 7).unwrap();
    assert_eq!(
        &bytes[record.filename_span.start..record.filename_span.end],
        b"mesh-\xff.b3d"
    );
    assert!(
        parsed.value.get(10).is_some(),
        "unreferenced catalog entry remains visible"
    );
}

#[test]
fn malformed_shared_offset_preserves_alias_topology() {
    let mut bytes = vec![0_u8; 65_535 * 4];
    let malformed_offset = i32::try_from(bytes.len()).unwrap().to_le_bytes();
    bytes[7 * 4..7 * 4 + 4].copy_from_slice(&malformed_offset);
    bytes[8 * 4..8 * 4 + 4].copy_from_slice(&malformed_offset);

    let parsed = MeshCatalog::parse_with_evidence(&bytes).unwrap();
    assert_eq!(
        parsed.slot(7),
        MeshSlotDisposition::DecodeFailed {
            offset: bytes.len()
        }
    );
    assert_eq!(parsed.slot(8), MeshSlotDisposition::Alias { target: 7 });
}
