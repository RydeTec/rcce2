use rcce_editor_core::{
    load_feedback_project, FeedbackActorCount, FeedbackEvidence, FeedbackMediaStatus,
    FeedbackProject, FeedbackScriptFamily, FeedbackZoneStatus, Lens,
};
use std::{fs, path::PathBuf, time::SystemTime};

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

    fs::remove_dir_all(path).expect("fixture cleanup");
}
