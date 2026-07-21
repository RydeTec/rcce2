use rcce_project::{
    ActorCountEvidence, ActorMediaAvailability, ConsensusLevel, MetadataBudget, ProjectRoot,
    ProjectSnapshot, ReadAssurance, ScanControl,
};
use std::path::{Path, PathBuf};

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

#[test]
fn happy_consensus_is_exactly_four_and_resolves_none_present_and_physical() {
    let result = load("happy");
    assert_eq!(result.level(), ConsensusLevel::Consensus);
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
