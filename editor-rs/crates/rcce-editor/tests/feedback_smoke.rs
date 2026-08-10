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
    for directory in [
        "Meshes",
        "Textures",
        "Sounds",
        "Music",
        "Emitter Configs",
        "UI",
        "Imports/Meshes",
    ] {
        fs::create_dir_all(root.join(directory)).expect("asset root fixture directory");
    }
    fs::write(root.join("Server Data/Actors.dat"), b"actors").expect("fixture actor file");
    fs::write(root.join("Areas/Shared.dat"), b"actors").expect("fixture peer file");
    for (path, bytes) in [
        ("Meshes/Hero.b3d", b"mesh".as_slice()),
        ("Textures/Icon.png", b"texture".as_slice()),
        ("Sounds/Tone.wav", b"sound".as_slice()),
        ("Music/Theme.ogg", b"music".as_slice()),
        ("Emitter Configs/Glow.rpc", b"emitter".as_slice()),
        ("UI/Panel.png", b"ui".as_slice()),
        ("Imports/Meshes/Observed.bin", b"other".as_slice()),
    ] {
        fs::write(root.join(path), bytes).expect("asset fixture file");
    }
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
    assert!(String::from_utf8_lossy(&valid.stdout).contains("[super-editor-mvp] ready: 9 files"));
    let valid_stdout = String::from_utf8_lossy(&valid.stdout);
    assert!(valid_stdout.contains("actors="));
    assert!(valid_stdout.contains("actor_evidence="));
    assert!(valid_stdout.contains("actor_reference_issues="));
    assert!(valid_stdout.contains("actor_base_meshes="));
    assert!(valid_stdout.contains("actor_resolved_base_meshes=0"));
    assert!(valid_stdout.contains("asset_files=7"));
    assert!(valid_stdout.contains("asset_file_bytes=35"));
    assert!(valid_stdout.contains("asset_meshes=1"));
    assert!(valid_stdout.contains("asset_mesh_bytes=4"));
    assert!(valid_stdout.contains("asset_textures=1"));
    assert!(valid_stdout.contains("asset_texture_bytes=7"));
    assert!(valid_stdout.contains("asset_sounds=1"));
    assert!(valid_stdout.contains("asset_sound_bytes=5"));
    assert!(valid_stdout.contains("asset_music=1"));
    assert!(valid_stdout.contains("asset_music_bytes=5"));
    assert!(valid_stdout.contains("asset_emitter_configs=1"));
    assert!(valid_stdout.contains("asset_emitter_config_bytes=7"));
    assert!(valid_stdout.contains("asset_ui=1"));
    assert!(valid_stdout.contains("asset_ui_bytes=2"));
    assert!(valid_stdout.contains("asset_other=1"));
    assert!(valid_stdout.contains("asset_other_bytes=5"));
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
