use std::{fs, path::PathBuf, process::Command, time::SystemTime};

fn fixture() -> PathBuf {
    let nonce = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("system clock")
        .as_nanos();
    let root =
        std::env::temp_dir().join(format!("rcce-editor-smoke-{}-{nonce}", std::process::id()));
    fs::create_dir_all(root.join("Server Data")).expect("fixture directory");
    fs::create_dir_all(root.join("Areas")).expect("peer fixture directory");
    fs::write(root.join("Server Data/Actors.dat"), b"actors").expect("fixture actor file");
    fs::write(root.join("Areas/Shared.dat"), b"actors").expect("fixture peer file");
    root
}

#[test]
fn smoke_contract_distinguishes_openable_and_missing_roots() {
    let root = fixture();
    let valid = Command::new(env!("CARGO_BIN_EXE_rcce-editor"))
        .args([
            "--project",
            root.to_str().expect("UTF-8 root"),
            "--smoke-exit",
        ])
        .output()
        .expect("valid smoke command");
    assert!(valid.status.success());
    assert!(String::from_utf8_lossy(&valid.stdout).contains("[super-editor-mvp] ready: 2 files"));
    let valid_stdout = String::from_utf8_lossy(&valid.stdout);
    assert!(valid_stdout.contains("actors="));
    assert!(valid_stdout.contains("actor_evidence="));
    assert!(valid_stdout.contains("actor_reference_issues="));
    assert!(valid_stdout.contains("actor_base_meshes="));
    assert!(valid_stdout.contains("zones="));
    assert!(valid_stdout.contains("zone_pairing_issues="));
    assert!(valid_stdout.contains("scripts="));
    assert!(valid_stdout.contains("script_adjuncts="));
    assert!(valid_stdout.contains("script_inventory_issues="));
    assert!(valid_stdout.contains("known_observations="));
    assert!(valid_stdout.contains("actor_diagnostics="));
    assert!(valid_stdout.contains("vault_files="));
    assert!(valid_stdout.contains("vault_secret_labeled="));
    assert!(valid_stdout.contains("vault_dynamic_private_labeled="));
    assert!(valid_stdout.contains("vault_server_config_labeled="));
    assert!(valid_stdout.contains("vault_other="));
    assert!(valid_stdout.contains("fingerprint_peer_groups=1"));
    assert!(valid_stdout.contains("fingerprint_peer_paths=2"));

    let missing = root.join("missing");
    let invalid = Command::new(env!("CARGO_BIN_EXE_rcce-editor"))
        .args([
            "--project",
            missing.to_str().expect("UTF-8 missing root"),
            "--smoke-exit",
        ])
        .output()
        .expect("invalid smoke command");
    assert_eq!(invalid.status.code(), Some(2));
    assert!(String::from_utf8_lossy(&invalid.stderr).contains("[super-editor-mvp] failed:"));

    fs::remove_dir_all(root).expect("fixture cleanup");
}
