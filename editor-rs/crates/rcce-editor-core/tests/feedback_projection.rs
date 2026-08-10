use rcce_editor_core::{
    load_feedback_project, FeedbackAcceptedFileDeltaKind, FeedbackAcceptedSnapshotDelta,
    FeedbackActorCount, FeedbackEvidence, FeedbackFindTarget, FeedbackFingerprintPeerTarget,
    FeedbackMediaStatus, FeedbackObservationEvidence, FeedbackProject, FeedbackScriptFamily,
    FeedbackVaultFacet, FeedbackZoneStatus, Lens,
};
use std::{collections::HashSet, fs, path::PathBuf, time::SystemTime};

fn fixture() -> PathBuf {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    let root = std::env::temp_dir().join(format!(
        "rcce-feedback-projection-{}-{nonce}",
        std::process::id()
    ));
    fs::create_dir_all(root.join("Server Data/Scripts")).expect("script fixture directory");
    fs::create_dir_all(root.join("Meshes/Actors")).expect("asset fixture directory");
    fs::create_dir_all(root.join("Areas")).expect("world fixture directory");
    fs::write(root.join("Server Data/Actors.dat"), b"actors").expect("actor fixture");
    fs::write(root.join("Server Data/Scripts/Quest.rsl"), b"Wait(1)").expect("script fixture");
    fs::write(root.join("Meshes/Actors/Hero.b3d"), b"mesh").expect("mesh fixture");
    fs::write(root.join("Areas/Start.dat"), b"area").expect("area fixture");
    fs::write(root.join("mystery.bin"), b"unknown").expect("unknown fixture");
    root
}

fn project() -> (PathBuf, FeedbackProject) {
    let path = fixture();
    let project = load_feedback_project(path.clone(), |_| {}).expect("fixture snapshot");
    (path, project)
}

fn fingerprint_peer_fixture() -> PathBuf {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    let root = std::env::temp_dir().join(format!(
        "rcce-feedback-fingerprint-peers-{}-{nonce}",
        std::process::id()
    ));
    for directory in [
        "Areas",
        "Meshes/Actors",
        "Server Data/Scripts",
        "Server Data",
    ] {
        fs::create_dir_all(root.join(directory)).expect("fingerprint peer fixture directory");
    }
    for path in [
        "Areas/Shared.dat",
        "Meshes/Actors/Hero.b3d",
        "Server Data/Actors.dat",
        "Server Data/Scripts/Quest.rsl",
    ] {
        fs::write(root.join(path), b"shared accepted bytes").expect("shared peer fixture");
    }
    for path in ["Areas/Empty.dat", "Server Data/Scripts/Empty.rsl"] {
        fs::write(root.join(path), b"").expect("zero-byte peer fixture");
    }
    fs::write(root.join("Server Data/Singleton.dat"), b"singleton").expect("singleton fixture");
    root
}

fn delta_fixture(label: &str) -> PathBuf {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    let root = std::env::temp_dir().join(format!(
        "rcce-feedback-delta-{label}-{}-{nonce}",
        std::process::id()
    ));
    fs::create_dir_all(&root).expect("delta fixture directory");
    root
}

fn assert_actor_mesh_threads_resolve(project: &FeedbackProject) {
    let actors = &project.actor_catalog().actors;
    let meshes = &project.asset_catalog().meshes;

    for actor in actors {
        match actor.base_mesh {
            Some(mesh_id) => {
                let mesh = meshes
                    .iter()
                    .find(|mesh| mesh.mesh_id == mesh_id)
                    .expect("every actor base-mesh route resolves");
                assert!(mesh
                    .actors
                    .iter()
                    .any(|backlink| backlink.actor_id == actor.actor_id));
            }
            None => assert!(meshes.iter().all(|mesh| mesh
                .actors
                .iter()
                .all(|backlink| { backlink.actor_id != actor.actor_id }))),
        }
    }

    for mesh in meshes {
        assert!(mesh
            .actors
            .windows(2)
            .all(|pair| pair[0].actor_id < pair[1].actor_id));
        for backlink in &mesh.actors {
            let actor = actors
                .iter()
                .find(|actor| actor.actor_id == backlink.actor_id)
                .expect("every mesh backlink route resolves");
            assert_eq!(actor.base_mesh, Some(mesh.mesh_id));
        }
    }
}

fn zone_fixture() -> PathBuf {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    let root = std::env::temp_dir().join(format!(
        "rcce-feedback-zones-{}-{nonce}",
        std::process::id()
    ));
    fs::create_dir_all(root.join("Areas/Nested")).expect("visual area fixture directory");
    fs::create_dir_all(root.join("Server Data/Areas")).expect("gameplay area fixture directory");
    fs::write(root.join("Areas/Paired.dat"), b"visual").expect("paired visual area");
    fs::write(root.join("Server Data/Areas/paired.dat"), b"gameplay")
        .expect("paired gameplay area");
    fs::write(root.join("Areas/Visual Only.dat"), b"visual-only").expect("visual-only area");
    fs::write(
        root.join("Server Data/Areas/Gameplay Only.dat"),
        b"gameplay-only",
    )
    .expect("gameplay-only area");
    fs::write(root.join("Areas/Nested/Ignored.dat"), b"nested").expect("nested non-zone file");
    fs::write(root.join("Areas/notes.txt"), b"notes").expect("non-dat area file");
    fs::write(root.join("Areas/éabc"), b"unicode-not-dat").expect("unicode non-dat area file");
    fs::write(root.join("Areas/Étoile.dat"), b"unicode-visual").expect("unicode visual area");
    fs::write(
        root.join("Server Data/Areas/Étoile.dat"),
        b"unicode-gameplay",
    )
    .expect("unicode gameplay area");
    root
}

fn script_fixture() -> PathBuf {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    let root = std::env::temp_dir().join(format!(
        "rcce-feedback-scripts-{}-{nonce}",
        std::process::id()
    ));
    fs::create_dir_all(root.join("Server Data/Scripts/Nested")).expect("script fixture directory");
    fs::write(
        root.join("Server Data/Scripts/Click_Merchant.rsl"),
        b"source",
    )
    .expect("active click source");
    fs::write(
        root.join("Server Data/Scripts/click_merchant.RCM"),
        b"module",
    )
    .expect("same-stem module adjunct");
    fs::write(
        root.join("Server Data/Scripts/CLICK_MERCHANT.rcscript"),
        b"alternate",
    )
    .expect("same-stem alternate adjunct");
    fs::write(root.join("Server Data/Scripts/Spell_Fire.rsl"), b"spell").expect("spell source");
    fs::write(root.join("Server Data/Scripts/Utility.rsl"), b"utility").expect("other source");
    fs::write(root.join("Server Data/Scripts/orphan.rcm"), b"orphan").expect("unanchored adjunct");
    fs::write(
        root.join("Server Data/Scripts/Nested/Ignored.rsl"),
        b"nested",
    )
    .expect("nested non-catalog source");
    fs::write(root.join("Server Data/Scripts/notes.txt"), b"notes")
        .expect("unrelated script-directory file");
    fs::write(root.join("Server Data/Scripts/éabc"), b"unicode")
        .expect("unicode extensionless file");
    root
}

fn vault_fixture() -> PathBuf {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    let root = std::env::temp_dir().join(format!(
        "rcce-feedback-vault-{}-{nonce}",
        std::process::id()
    ));
    fs::create_dir_all(root.join("Server Data")).expect("vault fixture directory");
    for path in [
        "Accounts.dat",
        "Accounts.dat.bak",
        "Dropped Items.dat",
        "MySQL.dat",
        "MySQL.dat.example",
        "Names Filter.txt",
        "Privileged Scripts.dat",
        "Superglobals.dat",
        "Superglobals.dat.bak",
    ] {
        fs::write(root.join("Server Data").join(path), path.as_bytes())
            .expect("vault fixture file");
    }
    root
}

#[test]
fn projects_real_inventory_into_five_lenses() {
    let (path, project) = project();

    assert_eq!(project.total_files(), 5);
    assert_eq!(project.shape, "AuthoringProject");
    assert_eq!(project.entries(Lens::Records).len(), 2);
    assert_eq!(project.entries(Lens::World).len(), 1);
    assert_eq!(project.entries(Lens::Assets).len(), 1);
    assert_eq!(project.entries(Lens::Scripts).len(), 1);
    assert_eq!(project.entries(Lens::Vault).len(), 0);
    assert_eq!(project.unclassified_files(), 1);
    assert_eq!(
        Lens::ALL
            .iter()
            .map(|lens| project.entries(*lens).len())
            .sum::<usize>(),
        project.total_files()
    );
    let actor = project
        .entries(Lens::Records)
        .iter()
        .find(|entry| entry.path == "Data/Server Data/Actors.dat")
        .expect("actor record remains reachable");
    assert_eq!(actor.family, Some("PF-CAN-001"));
    assert!(project
        .entries(Lens::Records)
        .iter()
        .any(|entry| entry.path == "Data/mystery.bin"));

    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn indexes_exact_accepted_source_fingerprint_peers_without_inference() {
    let path = fingerprint_peer_fixture();
    let project = load_feedback_project(path.clone(), |_| {}).expect("fingerprint peer project");

    assert_eq!(project.fingerprint_peer_group_count(), 2);
    assert_eq!(project.fingerprint_peer_path_count(), 6);

    let group = project
        .fingerprint_group_for_path("Data/Meshes/Actors/Hero.b3d")
        .expect("shared fingerprint group");
    assert_eq!(group.source_sha256.len(), 64);
    let members = group
        .members()
        .iter()
        .map(|locator| {
            let entry = project
                .entry(*locator)
                .expect("compact locator resolves in the accepted snapshot");
            (locator.lens, entry.path.as_str(), entry.size)
        })
        .collect::<Vec<_>>();
    assert_eq!(
        members,
        vec![
            (Lens::World, "Data/Areas/Shared.dat", 21),
            (Lens::Assets, "Data/Meshes/Actors/Hero.b3d", 21),
            (Lens::Records, "Data/Server Data/Actors.dat", 21),
            (Lens::Scripts, "Data/Server Data/Scripts/Quest.rsl", 21,),
        ]
    );

    let zero_group = project
        .fingerprint_group_for_path("Data/Areas/Empty.dat")
        .expect("zero-byte accepted paths remain evidence");
    assert_eq!(zero_group.members().len(), 2);
    assert!(zero_group
        .members()
        .iter()
        .all(|locator| project.entry(*locator).is_some_and(|entry| entry.size == 0)));
    assert!(project
        .fingerprint_group_for_path("Data/Server Data/Singleton.dat")
        .is_none());

    let target = FeedbackFingerprintPeerTarget {
        source_sha256: group.source_sha256.clone(),
        lens: Lens::World,
        path: "Data/Areas/Shared.dat".to_owned(),
    };
    assert!(project.contains_fingerprint_peer_target(&target));
    assert!(
        !project.contains_fingerprint_peer_target(&FeedbackFingerprintPeerTarget {
            source_sha256: "0".repeat(64),
            ..target.clone()
        })
    );
    assert!(
        !project.contains_fingerprint_peer_target(&FeedbackFingerprintPeerTarget {
            lens: Lens::Records,
            ..target
        })
    );

    fs::remove_dir_all(path).expect("fingerprint fixture cleanup");
}

#[test]
fn fingerprint_peer_groups_are_exhaustive_beyond_one_viewport() {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    let path = std::env::temp_dir().join(format!(
        "rcce-feedback-fingerprint-many-{}-{nonce}",
        std::process::id()
    ));
    fs::create_dir_all(path.join("Meshes/Peers")).expect("large peer fixture directory");
    for index in 0..300 {
        fs::write(
            path.join(format!("Meshes/Peers/peer-{index:03}.bin")),
            b"same accepted fingerprint",
        )
        .expect("large peer fixture file");
    }

    let project = load_feedback_project(path.clone(), |_| {}).expect("large peer project");
    let group = project
        .fingerprint_group_for_path("Data/Meshes/Peers/peer-000.bin")
        .expect("large peer group");
    assert_eq!(project.fingerprint_peer_group_count(), 1);
    assert_eq!(project.fingerprint_peer_path_count(), 300);
    assert_eq!(group.members().len(), 300);
    assert_eq!(
        project.entry(group.members()[299]).expect("last peer").path,
        "Data/Meshes/Peers/peer-299.bin"
    );

    fs::remove_dir_all(path).expect("large peer fixture cleanup");
}

#[test]
fn projects_vault_state_boundaries_without_collapsing_overlapping_labels() {
    let path = vault_fixture();
    let project = load_feedback_project(path.clone(), |_| {}).expect("vault feedback project");
    let catalog = project.vault_catalog();

    assert_eq!(
        catalog
            .entries
            .iter()
            .map(|entry| entry.path.as_str())
            .collect::<Vec<_>>(),
        vec![
            "Data/Server Data/Accounts.dat",
            "Data/Server Data/Accounts.dat.bak",
            "Data/Server Data/Dropped Items.dat",
            "Data/Server Data/MySQL.dat",
            "Data/Server Data/MySQL.dat.example",
            "Data/Server Data/Names Filter.txt",
            "Data/Server Data/Privileged Scripts.dat",
            "Data/Server Data/Superglobals.dat",
            "Data/Server Data/Superglobals.dat.bak",
        ]
    );
    assert_eq!(catalog.entries.len(), 9);
    assert_eq!(catalog.count(FeedbackVaultFacet::Secret), 3);
    assert_eq!(catalog.count(FeedbackVaultFacet::DynamicPrivate), 5);
    assert_eq!(catalog.count(FeedbackVaultFacet::ServerConfig), 3);
    assert_eq!(catalog.count(FeedbackVaultFacet::Other), 1);

    let accounts = catalog
        .entries
        .iter()
        .find(|entry| entry.path.ends_with("Accounts.dat"))
        .expect("accounts row");
    assert_eq!(
        accounts.facets,
        vec![
            FeedbackVaultFacet::Secret,
            FeedbackVaultFacet::DynamicPrivate,
        ]
    );
    let mysql = catalog
        .entries
        .iter()
        .find(|entry| entry.path.ends_with("MySQL.dat"))
        .expect("mysql configuration row");
    assert_eq!(
        mysql.facets,
        vec![FeedbackVaultFacet::Secret, FeedbackVaultFacet::ServerConfig,]
    );
    let example = catalog
        .entries
        .iter()
        .find(|entry| entry.path.ends_with("MySQL.dat.example"))
        .expect("mysql example row");
    assert_eq!(example.facets, vec![FeedbackVaultFacet::Other]);
    assert_eq!(
        project
            .entries(Lens::Vault)
            .iter()
            .find(|entry| entry.path == example.path)
            .expect("exact accepted entry")
            .classes,
        vec!["unknown"]
    );

    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn vault_state_boundary_projection_is_exhaustive_beyond_one_viewport() {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    let path = std::env::temp_dir().join(format!(
        "rcce-feedback-vault-many-{}-{nonce}",
        std::process::id()
    ));
    fs::create_dir_all(path.join("Server Data")).expect("large vault fixture directory");
    for index in 0..300 {
        fs::write(
            path.join(format!("Server Data/Accounts shard {index:03}.dat")),
            index.to_string(),
        )
        .expect("large vault fixture file");
    }

    let project = load_feedback_project(path.clone(), |_| {}).expect("large vault project");
    let catalog = project.vault_catalog();
    assert_eq!(catalog.entries.len(), 300);
    assert_eq!(catalog.count(FeedbackVaultFacet::Other), 300);
    assert_eq!(
        catalog.entries.first().expect("first row").path,
        "Data/Server Data/Accounts shard 000.dat"
    );
    assert_eq!(
        catalog.entries.last().expect("last row").path,
        "Data/Server Data/Accounts shard 299.dat"
    );

    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn preserves_observed_sizes_and_never_invents_entries() {
    let (path, project) = project();
    let observed = project
        .all_entries()
        .map(|entry| (entry.path.as_str(), entry.size))
        .collect::<Vec<_>>();

    assert_eq!(observed.len(), 5);
    assert!(observed.contains(&("Data/Meshes/Actors/Hero.b3d", 4)));
    assert!(observed.contains(&("Data/Server Data/Actors.dat", 6)));
    assert!(observed.contains(&("Data/mystery.bin", 7)));

    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn projects_consensus_proven_actor_catalog_and_live_media_diagnostics() {
    let data_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../test-data/consensus/happy/Data")
        .canonicalize()
        .expect("consensus fixture data root");
    let project = load_feedback_project(data_root, |_| {}).expect("feedback consensus project");
    let catalog = project.actor_catalog();

    assert_eq!(catalog.evidence, FeedbackEvidence::Consensus);
    assert_eq!(catalog.count, FeedbackActorCount::Agreed(4));
    assert_eq!(catalog.actors.len(), 4);
    assert_eq!(catalog.actors[0].race, "NoMesh");
    assert_eq!(
        catalog.actors[0].media_status,
        FeedbackMediaStatus::NoBaseMesh
    );
    assert_eq!(catalog.actors[1].media_status, FeedbackMediaStatus::Present);
    assert_eq!(
        catalog.actors[1].physical_path.as_deref(),
        Some("Data/Meshes/Hero.b3d")
    );
    assert_eq!(
        catalog.actors[2].media_status,
        FeedbackMediaStatus::MissingCatalog
    );
    assert_eq!(
        catalog.actors[3].media_status,
        FeedbackMediaStatus::MissingPhysical
    );
    assert_eq!(catalog.diagnostics.len(), 2);
    assert_eq!(
        catalog.diagnostics[0].code,
        "RCCE-ACTOR-MESH-CATALOG-MISSING"
    );
    assert_eq!(catalog.diagnostics[0].actor_id, 3);
    assert_eq!(catalog.diagnostics[1].code, "RCCE-ACTOR-MESH-FILE-MISSING");
    assert_eq!(catalog.diagnostics[1].actor_id, 4);
}

#[test]
fn provisional_actor_catalog_remains_browsable_without_asserted_media_health() {
    let data_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../test-data/consensus/provisional/Data")
        .canonicalize()
        .expect("provisional fixture data root");
    let project = load_feedback_project(data_root, |_| {}).expect("provisional feedback project");
    let catalog = project.actor_catalog();

    assert_eq!(catalog.evidence, FeedbackEvidence::Provisional);
    assert_eq!(
        catalog.count,
        FeedbackActorCount::Disagreed {
            client: 4,
            server: 2
        }
    );
    assert!(!catalog.actors.is_empty());
    assert!(catalog.actors.iter().all(|actor| {
        actor.media_status == FeedbackMediaStatus::Provisional && actor.physical_path.is_none()
    }));
    assert!(catalog.diagnostics.is_empty());
}

#[test]
fn projects_consensus_actor_base_mesh_relationships_without_inventing_assets() {
    let data_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../test-data/consensus/happy/Data")
        .canonicalize()
        .expect("consensus fixture data root");
    let project = load_feedback_project(data_root, |_| {}).expect("feedback consensus project");
    assert_actor_mesh_threads_resolve(&project);
    let catalog = project.asset_catalog();

    assert_eq!(catalog.evidence, FeedbackEvidence::Consensus);
    assert_eq!(catalog.meshes.len(), 3);
    assert!(catalog
        .meshes
        .windows(2)
        .all(|pair| pair[0].mesh_id < pair[1].mesh_id));
    assert!(catalog.meshes.iter().all(|mesh| !mesh.actors.is_empty()));
    let mut referenced_actor_ids = catalog
        .meshes
        .iter()
        .flat_map(|mesh| mesh.actors.iter())
        .map(|actor| actor.actor_id)
        .collect::<Vec<_>>();
    referenced_actor_ids.sort_unstable();
    assert_eq!(referenced_actor_ids, vec![2, 3, 4]);

    let present = catalog
        .meshes
        .iter()
        .find(|mesh| mesh.media_status == FeedbackMediaStatus::Present)
        .expect("present actor base mesh");
    assert_eq!(
        present.physical_path.as_deref(),
        Some("Data/Meshes/Hero.b3d")
    );
    assert!(catalog
        .meshes
        .iter()
        .any(|mesh| mesh.media_status == FeedbackMediaStatus::MissingCatalog));
    assert!(catalog
        .meshes
        .iter()
        .any(|mesh| mesh.media_status == FeedbackMediaStatus::MissingPhysical));
}

#[test]
fn provisional_actor_base_mesh_relationships_withhold_media_conclusions() {
    let data_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../test-data/consensus/provisional/Data")
        .canonicalize()
        .expect("provisional fixture data root");
    let project = load_feedback_project(data_root, |_| {}).expect("provisional feedback project");
    assert_actor_mesh_threads_resolve(&project);
    let catalog = project.asset_catalog();

    assert_eq!(catalog.evidence, FeedbackEvidence::Provisional);
    assert!(!catalog.meshes.is_empty());
    assert!(catalog.meshes.iter().all(|mesh| {
        mesh.media_status == FeedbackMediaStatus::Provisional
            && mesh.physical_path.is_none()
            && !mesh.actors.is_empty()
    }));
}

#[test]
fn projects_filename_derived_paired_zones_and_missing_half_observations() {
    let path = zone_fixture();
    let project = load_feedback_project(path.clone(), |_| {}).expect("zone feedback project");
    let catalog = project.zone_catalog();

    assert_eq!(catalog.zones.len(), 4);
    assert_eq!(catalog.diagnostics.len(), 2);

    let paired = catalog
        .zones
        .iter()
        .find(|zone| zone.name == "Paired")
        .expect("case-insensitive paired zone");
    assert_eq!(paired.status, FeedbackZoneStatus::Paired);
    assert_eq!(paired.visual_path.as_deref(), Some("Data/Areas/Paired.dat"));
    assert_eq!(
        paired.gameplay_path.as_deref(),
        Some("Data/Server Data/Areas/paired.dat")
    );
    assert_eq!(paired.visual_size, Some(6));
    assert_eq!(paired.gameplay_size, Some(8));

    let visual_only = catalog
        .zones
        .iter()
        .find(|zone| zone.name == "Visual Only")
        .expect("visual-only zone");
    assert_eq!(visual_only.status, FeedbackZoneStatus::VisualOnly);
    assert_eq!(visual_only.gameplay_path, None);

    let gameplay_only = catalog
        .zones
        .iter()
        .find(|zone| zone.name == "Gameplay Only")
        .expect("gameplay-only zone");
    assert_eq!(gameplay_only.status, FeedbackZoneStatus::GameplayOnly);
    assert_eq!(gameplay_only.visual_path, None);

    assert_eq!(catalog.diagnostics[0].code, "RCCE-ZONE-VISUAL-HALF-MISSING");
    assert_eq!(catalog.diagnostics[0].zone_name, "Gameplay Only");
    assert_eq!(
        catalog.diagnostics[1].code,
        "RCCE-ZONE-GAMEPLAY-HALF-MISSING"
    );
    assert_eq!(catalog.diagnostics[1].zone_name, "Visual Only");
    assert!(catalog.zones.iter().all(|zone| zone.name != "Ignored"));
    assert!(catalog.zones.iter().all(|zone| zone.name != "éabc"));
    let unicode = catalog
        .zones
        .iter()
        .find(|zone| zone.name == "Étoile")
        .expect("unicode dat zone identity");
    assert_eq!(unicode.status, FeedbackZoneStatus::Paired);

    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn projects_active_script_sources_with_observed_same_stem_adjuncts() {
    let path = script_fixture();
    let project = load_feedback_project(path.clone(), |_| {}).expect("script feedback project");
    let catalog = project.script_catalog();

    assert_eq!(catalog.scripts.len(), 3);
    assert_eq!(catalog.adjunct_files, 3);
    assert_eq!(catalog.diagnostics.len(), 1);

    let click = catalog
        .scripts
        .iter()
        .find(|script| script.name == "Click_Merchant")
        .expect("active source spelling anchors identity");
    assert_eq!(click.family, FeedbackScriptFamily::Click);
    assert_eq!(
        click.source_path,
        "Data/Server Data/Scripts/Click_Merchant.rsl"
    );
    assert_eq!(click.source_size, 6);
    assert_eq!(
        click.module_path.as_deref(),
        Some("Data/Server Data/Scripts/click_merchant.RCM")
    );
    assert_eq!(click.module_size, Some(6));
    assert_eq!(
        click.alternate_path.as_deref(),
        Some("Data/Server Data/Scripts/CLICK_MERCHANT.rcscript")
    );
    assert_eq!(click.alternate_size, Some(9));

    assert_eq!(
        catalog
            .scripts
            .iter()
            .find(|script| script.name == "Spell_Fire")
            .expect("spell source")
            .family,
        FeedbackScriptFamily::Spell
    );
    assert_eq!(
        catalog
            .scripts
            .iter()
            .find(|script| script.name == "Utility")
            .expect("uncategorized source")
            .family,
        FeedbackScriptFamily::Other
    );
    assert!(catalog
        .scripts
        .iter()
        .all(|script| script.name != "Ignored"));

    assert_eq!(
        catalog.diagnostics[0].code,
        "RCCE-SCRIPT-ADJUNCT-WITHOUT-SOURCE"
    );
    assert_eq!(catalog.diagnostics[0].script_name, "orphan");
    assert_eq!(
        catalog.diagnostics[0].path,
        "Data/Server Data/Scripts/orphan.rcm"
    );

    let observations = project.observation_index();
    assert_eq!(observations.actor_evidence, FeedbackEvidence::Unavailable);
    assert_eq!(observations.actor_issues, 0);
    assert_eq!(observations.zone_observations, 0);
    assert_eq!(observations.script_observations, 1);
    assert_eq!(observations.observations.len(), 1);
    assert_eq!(
        observations.observations[0].evidence,
        FeedbackObservationEvidence::ScriptInventory
    );
    assert_eq!(
        observations.observations[0].target,
        FeedbackFindTarget::File {
            lens: Lens::Scripts,
            path: "Data/Server Data/Scripts/orphan.rcm".to_owned(),
        }
    );
    assert!(project.contains_focus_target(&observations.observations[0].target));

    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn observations_preserve_evidence_boundaries_and_exact_domain_order() {
    let data_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../test-data/consensus/happy/Data")
        .canonicalize()
        .expect("consensus fixture data root");
    let actor_project =
        load_feedback_project(data_root, |_| {}).expect("feedback consensus project");
    let actor_observations = actor_project.observation_index();

    assert_eq!(
        actor_observations.actor_evidence,
        FeedbackEvidence::Consensus
    );
    assert_eq!(actor_observations.actor_issues, 2);
    assert_eq!(actor_observations.zone_observations, 0);
    assert_eq!(actor_observations.script_observations, 0);
    assert!(actor_observations.observations.iter().all(|observation| {
        observation.evidence == FeedbackObservationEvidence::ConsensusDiagnostic
            && actor_project.contains_focus_target(&observation.target)
    }));
    assert!(actor_observations
        .observations
        .windows(2)
        .all(|pair| pair[0].raw_identity.as_bytes() < pair[1].raw_identity.as_bytes()));

    let path = zone_fixture();
    let zone_project = load_feedback_project(path.clone(), |_| {}).expect("zone feedback project");
    let zone_observations = zone_project.observation_index();
    assert_eq!(
        zone_observations.actor_evidence,
        FeedbackEvidence::Unavailable
    );
    assert_eq!(zone_observations.actor_issues, 0);
    assert_eq!(zone_observations.zone_observations, 2);
    assert_eq!(zone_observations.script_observations, 0);
    assert_eq!(zone_observations.observations.len(), 2);
    assert!(zone_observations.observations.iter().all(|observation| {
        observation.evidence == FeedbackObservationEvidence::FilenamePairing
            && zone_project.contains_focus_target(&observation.target)
    }));
    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn provisional_actor_observations_withhold_diagnostics_without_calling_the_slice_clear() {
    let data_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../test-data/consensus/provisional/Data")
        .canonicalize()
        .expect("provisional fixture data root");
    let project = load_feedback_project(data_root, |_| {}).expect("provisional feedback project");
    let observations = project.observation_index();

    assert_eq!(observations.actor_evidence, FeedbackEvidence::Provisional);
    assert_eq!(observations.actor_issues, 0);
    assert!(observations.observations.iter().all(|observation| {
        observation.evidence != FeedbackObservationEvidence::ConsensusDiagnostic
    }));
}

#[test]
fn script_collision_quarantine_never_invents_targets_for_unaccepted_paths() {
    let path = script_fixture();
    fs::write(
        path.join("Server Data/Scripts/CLICK_MERCHANT.RSL"),
        b"case collision",
    )
    .expect("source case collision");
    fs::write(
        path.join("Server Data/Scripts/Spell_Fire.RCM"),
        b"module one",
    )
    .expect("first adjunct collision");
    fs::write(
        path.join("Server Data/Scripts/spell_fire.rcm"),
        b"module two",
    )
    .expect("second adjunct collision");
    let project = load_feedback_project(path.clone(), |_| {}).expect("script collision project");
    let index = project.observation_index();

    assert!(project.unavailable >= 4);
    assert_eq!(index.script_observations, 3);
    assert_eq!(index.observations.len(), 3);
    assert!(index.observations.iter().all(|observation| {
        observation.evidence == FeedbackObservationEvidence::ScriptInventory
            && matches!(
                observation.target,
                FeedbackFindTarget::File {
                    lens: Lens::Scripts,
                    ..
                }
            )
            && project.contains_focus_target(&observation.target)
    }));
    assert!(index
        .observations
        .windows(2)
        .all(|pair| pair[0].raw_identity.as_bytes() < pair[1].raw_identity.as_bytes()));
    assert!(index
        .observations
        .iter()
        .all(|observation| { !matches!(observation.target, FeedbackFindTarget::Script { .. }) }));
    assert!(index.observations.iter().all(|observation| {
        observation.raw_identity != "Data/Server Data/Scripts/CLICK_MERCHANT.RSL"
            && observation.raw_identity != "Data/Server Data/Scripts/Click_Merchant.rsl"
            && observation.raw_identity != "Data/Server Data/Scripts/Spell_Fire.RCM"
            && observation.raw_identity != "Data/Server Data/Scripts/spell_fire.rcm"
    }));

    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn observation_index_is_uncapped_and_exhaustive_for_large_script_inventory() {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    let path = std::env::temp_dir().join(format!(
        "rcce-feedback-observations-large-{}-{nonce}",
        std::process::id()
    ));
    fs::create_dir_all(path.join("Server Data/Scripts")).expect("large script fixture");
    for index in 0..257 {
        fs::write(
            path.join(format!("Server Data/Scripts/orphan_{index:03}.rcm")),
            b"adjunct",
        )
        .expect("orphan adjunct");
    }

    let project = load_feedback_project(path.clone(), |_| {}).expect("large observation project");
    let observations = project.observation_index();
    assert_eq!(observations.script_observations, 257);
    assert_eq!(observations.observations.len(), 257);
    assert!(observations
        .observations
        .iter()
        .all(|observation| project.contains_focus_target(&observation.target)));

    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn find_anywhere_indexes_every_accepted_identity_kind_without_collapsing_routes() {
    let data_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../test-data/consensus/happy/Data")
        .canonicalize()
        .expect("consensus fixture data root");
    let project = load_feedback_project(data_root, |_| {}).expect("feedback consensus project");
    let expected_candidates = project.total_files()
        + project.actor_catalog().actors.len()
        + project.asset_catalog().meshes.len()
        + project.zone_catalog().zones.len()
        + project.script_catalog().scripts.len();
    assert_eq!(project.find_candidate_count(), expected_candidates);
    assert!(project.find_anywhere("").is_empty());
    assert!(project.find_anywhere("   ").is_empty());

    let actor = &project.actor_catalog().actors[1];
    let actor_results = project.find_anywhere(&format!(" Actor #{} ", actor.actor_id));
    assert_eq!(
        actor_results.first().map(|result| &result.target),
        Some(&FeedbackFindTarget::Actor {
            actor_id: actor.actor_id,
        })
    );

    let mesh_id = project.asset_catalog().meshes[0].mesh_id;
    let mesh_results = project.find_anywhere(&format!("mesh #{mesh_id}"));
    assert_eq!(
        mesh_results.first().map(|result| &result.target),
        Some(&FeedbackFindTarget::Mesh { mesh_id })
    );

    let file_path = "Data/Meshes/Hero.b3d";
    let file_results = project.find_anywhere(file_path);
    assert_eq!(
        file_results.first().map(|result| &result.target),
        Some(&FeedbackFindTarget::File {
            lens: Lens::Assets,
            path: file_path.to_owned(),
        })
    );

    let repeated = project.find_anywhere(&actor.race.to_lowercase());
    assert_eq!(repeated, project.find_anywhere(&actor.race.to_uppercase()));
    assert!(matches!(
        repeated.first().map(|result| &result.target),
        Some(FeedbackFindTarget::Actor { .. })
    ));
    let unique_targets = repeated
        .iter()
        .map(|result| result.target.clone())
        .collect::<HashSet<_>>();
    assert_eq!(unique_targets.len(), repeated.len());
}

#[test]
fn find_anywhere_preserves_unicode_zone_identity_and_distinct_script_file_routes() {
    let zone_path = zone_fixture();
    let zone_project =
        load_feedback_project(zone_path.clone(), |_| {}).expect("zone feedback project");
    let zone_results = zone_project.find_anywhere(" éTOILE.DAT ");
    assert_eq!(
        zone_results.first().map(|result| &result.target),
        Some(&FeedbackFindTarget::Zone {
            name: "Étoile".to_owned(),
        })
    );
    fs::remove_dir_all(zone_path).expect("zone fixture cleanup");

    let script_path = script_fixture();
    let script_project =
        load_feedback_project(script_path.clone(), |_| {}).expect("script feedback project");
    let source_path = "Data/Server Data/Scripts/Click_Merchant.rsl";
    let source_results = script_project.find_anywhere(source_path);
    assert_eq!(
        source_results
            .iter()
            .take(2)
            .map(|result| result.target.clone())
            .collect::<Vec<_>>(),
        vec![
            FeedbackFindTarget::Script {
                source_path: source_path.to_owned(),
            },
            FeedbackFindTarget::File {
                lens: Lens::Scripts,
                path: source_path.to_owned(),
            },
        ]
    );
    let adjunct_results = script_project.find_anywhere("click_merchant.RCM");
    assert!(adjunct_results.iter().any(|result| {
        result.target
            == FeedbackFindTarget::File {
                lens: Lens::Scripts,
                path: "Data/Server Data/Scripts/click_merchant.RCM".to_owned(),
            }
    }));
    assert!(adjunct_results
        .iter()
        .all(|result| !matches!(result.target, FeedbackFindTarget::Script { .. })));
    assert_eq!(
        script_project.find_anywhere("Data/").len(),
        script_project.total_files() + script_project.script_catalog().scripts.len()
    );
    fs::remove_dir_all(script_path).expect("script fixture cleanup");
}

#[test]
fn find_anywhere_keeps_provisional_raw_routes_and_excludes_unavailable_semantic_routes() {
    let provisional_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../test-data/consensus/provisional/Data")
        .canonicalize()
        .expect("provisional fixture data root");
    let provisional =
        load_feedback_project(provisional_root, |_| {}).expect("provisional feedback project");
    assert!(provisional.find_anywhere("actor").iter().any(|result| {
        matches!(result.target, FeedbackFindTarget::Actor { .. })
            && result.detail.to_ascii_lowercase().contains("provisional")
    }));
    assert!(provisional.find_anywhere("mesh").iter().any(|result| {
        matches!(result.target, FeedbackFindTarget::Mesh { .. })
            && result.detail.to_ascii_lowercase().contains("provisional")
    }));

    let path = fixture();
    fs::remove_file(path.join("Server Data/Actors.dat")).expect("remove actor source");
    let unavailable =
        load_feedback_project(path.clone(), |_| {}).expect("actor-unavailable feedback project");
    assert_eq!(
        unavailable.actor_catalog().evidence,
        FeedbackEvidence::Unavailable
    );
    assert!(unavailable
        .find_anywhere("actor")
        .iter()
        .all(|result| !matches!(result.target, FeedbackFindTarget::Actor { .. })));
    assert!(unavailable
        .find_anywhere("mesh")
        .iter()
        .all(|result| !matches!(result.target, FeedbackFindTarget::Mesh { .. })));
    fs::remove_dir_all(path).expect("fixture cleanup");
}

#[test]
fn accepted_snapshot_delta_is_grouped_exact_and_never_infers_a_rename() {
    let root = delta_fixture("grouped");
    fs::write(root.join("A-prior-only.dat"), b"prior").expect("prior-only file");
    fs::write(root.join("B-changed.dat"), b"before").expect("changed file before");
    fs::write(root.join("C-Rename.dat"), b"rename-shaped").expect("rename-shaped prior file");
    fs::write(root.join("same.dat"), b"same").expect("unchanged file");
    let prior = load_feedback_project(root.clone(), |_| {}).expect("prior accepted snapshot");

    fs::remove_file(root.join("A-prior-only.dat")).expect("remove prior-only fixture file");
    fs::write(root.join("B-changed.dat"), b"after").expect("changed file after");
    fs::rename(root.join("C-Rename.dat"), root.join("c-rename.dat"))
        .expect("rename-shaped fixture mutation");
    fs::write(root.join("a-new.dat"), b"new").expect("new fixture file");
    let current = load_feedback_project(root.clone(), |_| {}).expect("current accepted snapshot");

    let delta = FeedbackAcceptedSnapshotDelta::between_same_root(&prior, &current)
        .expect("same-root comparison");
    assert_eq!(delta.prior_files, 4);
    assert_eq!(delta.current_files, 4);
    assert_eq!(
        delta
            .entries
            .iter()
            .map(|entry| (entry.kind, entry.path.as_str()))
            .collect::<Vec<_>>(),
        vec![
            (
                FeedbackAcceptedFileDeltaKind::NewlyAccepted,
                "Data/a-new.dat"
            ),
            (
                FeedbackAcceptedFileDeltaKind::NewlyAccepted,
                "Data/c-rename.dat"
            ),
            (
                FeedbackAcceptedFileDeltaKind::NoLongerAccepted,
                "Data/A-prior-only.dat",
            ),
            (
                FeedbackAcceptedFileDeltaKind::NoLongerAccepted,
                "Data/C-Rename.dat",
            ),
            (
                FeedbackAcceptedFileDeltaKind::ContentFingerprintChanged,
                "Data/B-changed.dat",
            ),
        ]
    );
    assert_eq!(delta.count(FeedbackAcceptedFileDeltaKind::NewlyAccepted), 2);
    assert_eq!(
        delta.count(FeedbackAcceptedFileDeltaKind::NoLongerAccepted),
        2
    );
    assert_eq!(
        delta.count(FeedbackAcceptedFileDeltaKind::ContentFingerprintChanged),
        1
    );
    assert_eq!(
        FeedbackAcceptedFileDeltaKind::ALL.map(FeedbackAcceptedFileDeltaKind::label),
        [
            "NEWLY ACCEPTED",
            "NO LONGER ACCEPTED",
            "CONTENT FINGERPRINT CHANGED",
        ]
    );
    assert_eq!(delta.range(None), 0..5);
    assert_eq!(
        delta.range(Some(FeedbackAcceptedFileDeltaKind::NewlyAccepted)),
        0..2
    );
    assert_eq!(
        delta.range(Some(FeedbackAcceptedFileDeltaKind::NoLongerAccepted)),
        2..4
    );
    assert_eq!(
        delta.range(Some(
            FeedbackAcceptedFileDeltaKind::ContentFingerprintChanged
        )),
        4..5
    );
    assert!(delta
        .entries
        .iter()
        .all(|entry| entry.path != "Data/same.dat"));

    let changed = delta
        .entries
        .iter()
        .find(|entry| entry.path == "Data/B-changed.dat")
        .expect("changed row");
    assert_ne!(changed.prior_source_sha256, changed.current_source_sha256);
    assert_eq!(
        changed.current_target(),
        Some(FeedbackFindTarget::File {
            lens: Lens::Records,
            path: "Data/B-changed.dat".to_owned(),
        })
    );
    assert!(delta
        .entries
        .iter()
        .filter(|entry| entry.kind == FeedbackAcceptedFileDeltaKind::NoLongerAccepted)
        .all(|entry| entry.current_target().is_none() && entry.current_source_sha256.is_none()));
    assert!(delta
        .entries
        .iter()
        .filter(|entry| entry.kind == FeedbackAcceptedFileDeltaKind::NewlyAccepted)
        .all(|entry| entry.current_target().is_some() && entry.prior_source_sha256.is_none()));

    fs::remove_dir_all(root).expect("delta fixture cleanup");
}

#[test]
fn accepted_snapshot_delta_distinguishes_empty_from_unavailable_and_refuses_cross_root() {
    let root = delta_fixture("empty");
    fs::write(root.join("same.dat"), b"same").expect("same fixture file");
    let prior = load_feedback_project(root.clone(), |_| {}).expect("prior accepted snapshot");
    let current = load_feedback_project(root.clone(), |_| {}).expect("current accepted snapshot");
    let delta = FeedbackAcceptedSnapshotDelta::between_same_root(&prior, &current)
        .expect("available empty same-root comparison");
    assert!(delta.entries.is_empty());

    let other_root = delta_fixture("cross-root");
    fs::write(other_root.join("same.dat"), b"same").expect("cross-root fixture file");
    let other = load_feedback_project(other_root.clone(), |_| {}).expect("other accepted snapshot");
    assert!(FeedbackAcceptedSnapshotDelta::between_same_root(&prior, &other).is_none());

    fs::remove_dir_all(root).expect("delta fixture cleanup");
    fs::remove_dir_all(other_root).expect("cross-root fixture cleanup");
}

#[test]
fn accepted_snapshot_delta_is_uncapped_for_large_accepted_pairs() {
    let root = delta_fixture("large");
    let prior = load_feedback_project(root.clone(), |_| {}).expect("empty prior snapshot");
    for index in (0..301).rev() {
        fs::write(root.join(format!("new-{index:03}.dat")), index.to_string())
            .expect("large delta fixture file");
    }
    let current = load_feedback_project(root.clone(), |_| {}).expect("large current snapshot");
    let delta = FeedbackAcceptedSnapshotDelta::between_same_root(&prior, &current)
        .expect("large same-root comparison");

    assert_eq!(delta.entries.len(), 301);
    assert!(delta
        .entries
        .iter()
        .all(|entry| entry.kind == FeedbackAcceptedFileDeltaKind::NewlyAccepted));
    assert!(delta
        .entries
        .windows(2)
        .all(|pair| { pair[0].path.as_bytes() < pair[1].path.as_bytes() }));
    assert!(delta
        .entries
        .iter()
        .all(|entry| entry.current_target().is_some()));

    fs::remove_dir_all(root).expect("large delta fixture cleanup");
}

#[cfg(unix)]
#[test]
fn accepted_snapshot_delta_never_invents_a_target_for_a_quarantined_alias() {
    use std::os::unix::fs::symlink;

    let root = delta_fixture("quarantined-alias");
    let outside = delta_fixture("quarantined-alias-outside");
    fs::write(root.join("Alias.dat"), b"accepted before").expect("prior accepted file");
    fs::write(outside.join("outside.dat"), b"outside").expect("outside alias target");
    let prior = load_feedback_project(root.clone(), |_| {}).expect("prior accepted snapshot");

    fs::remove_file(root.join("Alias.dat")).expect("replace prior file with alias");
    symlink(outside.join("outside.dat"), root.join("Alias.dat")).expect("unsafe alias fixture");
    let current = load_feedback_project(root.clone(), |_| {}).expect("current accepted snapshot");
    assert!(current.unavailable >= 1);
    let delta = FeedbackAcceptedSnapshotDelta::between_same_root(&prior, &current)
        .expect("same-root alias comparison");

    assert_eq!(delta.entries.len(), 1);
    assert_eq!(
        delta.entries[0].kind,
        FeedbackAcceptedFileDeltaKind::NoLongerAccepted
    );
    assert_eq!(delta.entries[0].path, "Data/Alias.dat");
    assert!(delta.entries[0].current_target().is_none());

    fs::remove_dir_all(root).expect("alias fixture cleanup");
    fs::remove_dir_all(outside).expect("outside fixture cleanup");
}
