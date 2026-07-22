use rcce_editor_core::{load_feedback_project, FeedbackProject, Lens};
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
