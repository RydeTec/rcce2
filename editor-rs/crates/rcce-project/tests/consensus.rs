use rcce_project::{
    ActorCountEvidence, ActorMediaAvailability, ConsensusLevel, MetadataBudget, ProjectRoot,
    ProjectSnapshot, ReadAssurance, ScanControl,
};
use std::{
    fs,
    path::{Path, PathBuf},
    time::SystemTime,
};

fn fixture(name: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../test-data/consensus")
        .join(name)
        .canonicalize()
        .unwrap()
}

fn snapshot(root: &ProjectRoot) -> ProjectSnapshot {
    ProjectSnapshot::load(
        root,
        MetadataBudget {
            max_entries: 32,
            max_directories: 16,
            max_depth: 8,
            max_path_bytes: 256,
            max_component_bytes: 64,
            max_declared_bytes: 1_000_000,
            max_single_file_bytes: 300_000,
        },
        ReadAssurance::BaselineQuarantine,
        || ScanControl::Continue,
        |_| {},
    )
    .unwrap()
}

fn load(name: &str) -> rcce_project::ActorMediaConsensus {
    let root = ProjectRoot::open_explicit(&fixture(name)).unwrap();
    snapshot(&root)
        .load_actor_media_consensus(&root, || ScanControl::Continue)
        .unwrap()
}

fn load_from_data_root(name: &str) -> rcce_project::ActorMediaConsensus {
    let root = ProjectRoot::open_explicit(&fixture(name).join("Data")).unwrap();
    snapshot(&root)
        .load_actor_media_consensus(&root, || ScanControl::Continue)
        .unwrap()
}

fn temporary_root(label: &str) -> PathBuf {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    std::env::temp_dir().join(format!(
        "rcce-consensus-{label}-{}-{nonce}",
        std::process::id()
    ))
}

fn copy_happy_actor_slice(destination: &Path) {
    let source = fixture("happy").join("Data");
    fs::create_dir_all(destination.join("Server Data")).expect("server data directory");
    fs::create_dir_all(destination.join("Game Data")).expect("game data directory");
    fs::create_dir_all(destination.join("Meshes")).expect("meshes directory");
    fs::copy(
        source.join("Server Data/Actors.dat"),
        destination.join("Server Data/Actors.dat"),
    )
    .expect("actor catalog copy");
    fs::copy(
        source.join("Game Data/Meshes.dat"),
        destination.join("Game Data/Meshes.dat"),
    )
    .expect("mesh catalog copy");
    fs::copy(
        source.join("Meshes/Hero.b3d"),
        destination.join("Meshes/Hero.b3d"),
    )
    .expect("physical mesh copy");
}

fn load_modified_happy(
    label: &str,
    mutate: impl FnOnce(&mut Vec<u8>),
) -> rcce_project::ActorMediaConsensus {
    let path = temporary_root(label);
    copy_happy_actor_slice(&path);
    let catalog_path = path.join("Game Data/Meshes.dat");
    let mut catalog = fs::read(&catalog_path).expect("mesh catalog read");
    mutate(&mut catalog);
    fs::write(&catalog_path, catalog).expect("mesh catalog mutation");
    let root = ProjectRoot::open_explicit(&path).expect("modified fixture root");
    let result = snapshot(&root)
        .load_actor_media_consensus(&root, || ScanControl::Continue)
        .expect("modified fixture consensus");
    fs::remove_dir_all(path).expect("modified fixture cleanup");
    result
}

#[test]
fn consensus_accepts_an_explicit_data_root_without_changing_canonical_paths() {
    let result = load_from_data_root("happy");

    assert_eq!(result.level(), ConsensusLevel::Consensus);
    assert_eq!(result.actor_count(), ActorCountEvidence::Agreed(4));
    assert_eq!(result.actors().len(), 4);
    assert_eq!(
        result.actors()[1].physical_inventory_path.as_deref(),
        Some("Data/Meshes/Hero.b3d")
    );
    assert_eq!(
        result.actors()[2].availability,
        ActorMediaAvailability::MissingCatalog
    );
    assert_eq!(
        result.actors()[3].availability,
        ActorMediaAvailability::MissingPhysical
    );
}

#[test]
fn consensus_rejects_a_root_with_both_project_and_data_layouts() {
    let path = temporary_root("ambiguous-layout");
    copy_happy_actor_slice(&path);
    copy_happy_actor_slice(&path.join("Data"));

    let root = ProjectRoot::open_explicit(&path).expect("ambiguous fixture root");
    let error = snapshot(&root)
        .load_actor_media_consensus(&root, || ScanControl::Continue)
        .expect_err("two actor layouts must not be mixed or selected implicitly");

    assert_eq!(error, rcce_project::ConsensusLoadError::AmbiguousInventory);
    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn happy_consensus_is_exactly_four_and_resolves_none_present_and_physical() {
    let result = load("happy");
    assert_eq!(result.level(), ConsensusLevel::Consensus);
    assert!(result.catalog_topology().has_gaps);
    assert_eq!(result.actor_count(), ActorCountEvidence::Agreed(4));
    assert_eq!(result.actors().len(), 4);
    assert_eq!(
        result.actors()[0].availability,
        ActorMediaAvailability::NoBaseMesh
    );
    assert_eq!(result.actors()[0].base_mesh, None);
    assert_eq!(
        result.actors()[1].availability,
        ActorMediaAvailability::Present
    );
    assert_eq!(
        result.actors()[2].availability,
        ActorMediaAvailability::MissingCatalog
    );
    assert_eq!(
        result.actors()[3].availability,
        ActorMediaAvailability::MissingPhysical
    );
}

#[test]
fn catalog_and_physical_absence_are_distinct_deterministic_outcomes() {
    let missing_catalog = load("missing-catalog");
    assert!(missing_catalog
        .actors()
        .iter()
        .filter(|actor| actor.base_mesh.is_some())
        .all(|actor| actor.availability == ActorMediaAvailability::MissingCatalog));
    let missing_physical = load("missing-physical");
    assert_eq!(
        missing_physical
            .actors()
            .iter()
            .filter(|actor| actor.availability == ActorMediaAvailability::MissingPhysical)
            .count(),
        2
    );
    assert_eq!(
        missing_physical
            .actors()
            .iter()
            .filter(|actor| actor.availability == ActorMediaAvailability::MissingCatalog)
            .count(),
        1
    );
    assert!(missing_physical
        .actors()
        .windows(2)
        .all(|pair| pair[0].actor_id < pair[1].actor_id));
}

#[test]
fn unique_portable_case_resolution_uses_accepted_inventory_spelling() {
    let result = load("unique-case");
    assert_eq!(result.level(), ConsensusLevel::Consensus);
    assert_eq!(
        result.actors()[1].availability,
        ActorMediaAvailability::Present
    );
    assert_eq!(
        result.actors()[3].availability,
        ActorMediaAvailability::Present
    );
    assert_eq!(
        result.actors()[1].physical_inventory_path.as_deref(),
        Some("Data/Meshes/hErO.B3D")
    );
}

#[test]
fn disagreement_non_utf8_truncation_and_catalog_topology_remain_provisional() {
    let result = load("provisional");
    assert_eq!(result.level(), ConsensusLevel::Provisional);
    assert_eq!(
        result.actor_count(),
        ActorCountEvidence::Disagreed {
            client: 4,
            server: 2
        }
    );
    assert!(result
        .actors()
        .iter()
        .all(|actor| actor.availability == ActorMediaAvailability::Provisional));
    let raw = result
        .actors()
        .iter()
        .find(|actor| actor.actor_id == 2)
        .unwrap()
        .race
        .as_ref()
        .unwrap();
    assert_eq!(raw.raw_bytes(), b"raw-\xff-name");
    assert!(raw.best_effort_display().is_lossy());
    assert!(result.catalog_topology().has_aliases);
    assert!(result.catalog_topology().has_gaps);
    assert!(result.catalog_topology().has_invalid_offsets);
    assert!(
        result
            .catalog_topology()
            .has_catalog_entries_unreferenced_by_actor_base_slice
    );
}

#[test]
fn alias_invalid_offset_and_decode_failure_each_remain_provisional() {
    let alias = load_modified_happy("alias-only", |catalog| {
        let source = catalog[7 * 4..8 * 4].to_vec();
        catalog[8 * 4..9 * 4].copy_from_slice(&source);
    });
    assert_eq!(alias.level(), ConsensusLevel::Provisional);
    assert!(alias.catalog_topology().has_aliases);
    assert!(!alias.catalog_topology().has_invalid_offsets);

    let invalid = load_modified_happy("invalid-offset-only", |catalog| {
        catalog[8 * 4..9 * 4].copy_from_slice(&(-1_i32).to_le_bytes());
    });
    let decode_failed = load_modified_happy("decode-failed-only", |catalog| {
        let offset = i32::from_le_bytes(catalog[7 * 4..8 * 4].try_into().unwrap()) as usize;
        let filename_length = offset + 19;
        catalog[filename_length..filename_length + 4].copy_from_slice(&1_000_000_u32.to_le_bytes());
    });
    for (name, result) in [
        ("invalid-offset-only", invalid),
        ("decode-failed-only", decode_failed),
    ] {
        assert_eq!(result.level(), ConsensusLevel::Provisional, "{name}");
        assert!(!result.catalog_topology().has_aliases, "{name}");
        assert!(result.catalog_topology().has_invalid_offsets, "{name}");
        assert!(result.actors().iter().all(|actor| {
            actor.availability == ActorMediaAvailability::Provisional
                && actor.physical_inventory_path.is_none()
        }));
    }
}

#[test]
fn nested_legacy_mesh_paths_project_without_changing_raw_evidence() {
    let nested = load("nested-paths");
    assert_eq!(nested.level(), ConsensusLevel::Consensus);
    assert_eq!(
        nested.actors()[0].availability,
        ActorMediaAvailability::Present
    );
    assert_eq!(
        nested.actors()[0].physical_inventory_path.as_deref(),
        Some("Data/Meshes/Creatures/Wolf.b3d")
    );
    assert_eq!(
        nested.actors()[1].availability,
        ActorMediaAvailability::MissingPhysical
    );

    let traversal = load("nested-traversal");
    assert_eq!(traversal.level(), ConsensusLevel::Provisional);
    assert_eq!(
        traversal.actors()[0].availability,
        ActorMediaAvailability::Provisional
    );
    let bytes =
        std::fs::read(fixture("nested-traversal").join("Data/Game Data/Meshes.dat")).unwrap();
    let mesh = rcce_data::MeshCatalog::parse_with_evidence(&bytes).unwrap();
    let record = &mesh.records[0];
    assert_eq!(
        &bytes[record.filename_span.start..record.filename_span.end],
        b"..\\Outside.b3d"
    );
}

#[test]
fn extra_catalog_media_is_only_unreferenced_by_this_actor_base_slice() {
    let result = load("nested-paths");
    assert_eq!(result.level(), ConsensusLevel::Consensus);
    assert!(
        result
            .catalog_topology()
            .has_catalog_entries_unreferenced_by_actor_base_slice
    );
}

#[test]
fn duplicate_actor_ids_cannot_be_reported_as_consensus() {
    let result = load("duplicate-id");
    assert_eq!(result.level(), ConsensusLevel::Provisional);
    assert_eq!(result.actor_count(), ActorCountEvidence::Agreed(2));
    assert_eq!(result.actors().len(), 1);
    assert_eq!(result.actors()[0].actor_id, 12);
}

#[test]
fn binding_rejects_another_root_and_cancellation_without_a_document() {
    let happy_root = ProjectRoot::open_explicit(&fixture("happy")).unwrap();
    let snapshot = snapshot(&happy_root);
    let other_root = ProjectRoot::open_explicit(&fixture("missing-catalog")).unwrap();
    assert!(snapshot
        .load_actor_media_consensus(&other_root, || ScanControl::Continue)
        .is_err());
    assert!(snapshot
        .load_actor_media_consensus(&happy_root, || ScanControl::Cancel)
        .is_err());
}
