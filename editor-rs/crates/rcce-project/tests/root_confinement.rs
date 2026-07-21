use rcce_project::{
    CapabilityAvailability, ProjectRelativePath, ProjectRoot, ReadAssurance, ReadBudget,
    RootErrorCode, WalkBudget,
};
use std::fs;
use std::path::Path;

fn root(path: &Path) -> ProjectRoot {
    ProjectRoot::open_explicit(path).expect("temporary absolute root must open")
}

fn walk_budget() -> WalkBudget {
    WalkBudget {
        max_bytes: 4 * 1024,
        max_files: 8,
        max_single_file_bytes: 1024,
        max_directories: 8,
        max_depth: 4,
        max_path_bytes: 256,
        max_component_bytes: 64,
    }
}

#[test]
fn requires_an_explicit_absolute_root_and_holds_stable_identity() {
    let error = match ProjectRoot::open_explicit(Path::new("relative-project")) {
        Ok(_) => panic!("relative root unexpectedly opened"),
        Err(error) => error,
    };
    assert_eq!(error.code(), RootErrorCode::InvalidRoot);

    let first = tempfile::tempdir().unwrap();
    let second = tempfile::tempdir().unwrap();
    let first_root = root(first.path());
    assert_eq!(first_root.identity(), first_root.identity());
    assert_ne!(first_root.identity(), root(second.path()).identity());
}

#[test]
fn rejects_host_path_and_portable_alias_forms_lexically() {
    let rejected = [
        "",
        ".",
        "..",
        "../escape",
        "safe/../escape",
        "/absolute",
        "//server/share",
        r"\\server\share",
        r"\\?\C:\device",
        r"C:\absolute",
        "C:drive-relative",
        "file:stream",
        "mixed\\separator/file",
        "trailing.",
        "trailing ",
        "CON",
        "aux.txt",
        "COM1.dat",
        "safe//empty",
    ];
    for value in rejected {
        let error = ProjectRelativePath::parse(value).unwrap_err();
        assert_eq!(error.code(), RootErrorCode::InvalidPath, "{value:?}");
        if !value.is_empty() {
            assert!(!error.to_string().contains(value));
        }
    }
    assert!(ProjectRelativePath::parse("Data/Server Data/Actors.dat").is_ok());
}

#[test]
fn rejects_windows_superscript_device_aliases_lexically() {
    for prefix in ["COM", "LPT"] {
        for digit in ['¹', '²', '³'] {
            for suffix in ["", ".dat"] {
                let value = format!("{prefix}{digit}{suffix}");
                let error = ProjectRelativePath::parse(&value).unwrap_err();
                assert_eq!(error.code(), RootErrorCode::InvalidPath, "{value:?}");
                assert!(!error.to_string().contains(&value));
            }
        }
    }
}

#[cfg(unix)]
#[test]
fn rejects_non_utf8_host_path_input() {
    use std::ffi::OsStr;
    use std::os::unix::ffi::OsStrExt;
    let value = Path::new(OsStr::from_bytes(b"Data/invalid-\xff"));
    let error = ProjectRelativePath::from_path(value).unwrap_err();
    assert_eq!(error.code(), RootErrorCode::InvalidPath);
}

#[test]
fn reads_and_walks_only_accepted_regular_files_with_budgets() {
    let temp = tempfile::tempdir().unwrap();
    fs::write(temp.path().join("manifest.toml"), b"manifest").unwrap();
    fs::create_dir(temp.path().join("fixture")).unwrap();
    fs::write(temp.path().join("fixture/a.dat"), b"abc").unwrap();
    let root = root(temp.path());

    let bytes = root
        .read(
            &ProjectRelativePath::parse("manifest.toml").unwrap(),
            ReadBudget { max_bytes: 8 },
            ReadAssurance::BaselineQuarantine,
        )
        .unwrap();
    assert_eq!(bytes.as_slice(), b"manifest");
    let walk = root
        .walk(
            &ProjectRelativePath::parse("fixture").unwrap(),
            walk_budget(),
            ReadAssurance::BaselineQuarantine,
        )
        .unwrap();
    assert_eq!(walk.files.len(), 1);
    assert_eq!(walk.files[0].path, "a.dat");
    assert_eq!(walk.bytes, 3);

    let error = root
        .read(
            &ProjectRelativePath::parse("manifest.toml").unwrap(),
            ReadBudget { max_bytes: 7 },
            ReadAssurance::BaselineQuarantine,
        )
        .unwrap_err();
    assert_eq!(error.code(), RootErrorCode::ResourceLimit);
}

#[test]
fn requested_unavailable_assurance_fails_before_content_access() {
    use std::time::{Duration, UNIX_EPOCH};
    let temp = tempfile::tempdir().unwrap();
    let file_path = temp.path().join("secret.dat");
    fs::write(&file_path, b"outside-canary-never-released").unwrap();
    let sentinel = UNIX_EPOCH + Duration::from_secs(946_684_800);
    fs::File::options()
        .write(true)
        .open(&file_path)
        .unwrap()
        .set_times(
            fs::FileTimes::new()
                .set_accessed(sentinel)
                .set_modified(sentinel),
        )
        .unwrap();
    let before = fs::metadata(&file_path).unwrap().accessed().unwrap();
    let root = root(temp.path());
    assert_eq!(
        root.capabilities().strong_no_read,
        CapabilityAvailability::Unavailable
    );
    let error = root
        .read(
            &ProjectRelativePath::parse("secret.dat").unwrap(),
            ReadBudget { max_bytes: 1024 },
            ReadAssurance::StrongNoRead,
        )
        .unwrap_err();
    assert_eq!(error.code(), RootErrorCode::AssuranceUnavailable);
    assert_eq!(
        fs::metadata(&file_path).unwrap().accessed().unwrap(),
        before
    );
}

#[test]
fn transient_race_capability_is_separate_from_baseline_and_strong_modes() {
    let temp = tempfile::tempdir().unwrap();
    fs::write(temp.path().join("file.dat"), b"safe").unwrap();
    let root = root(temp.path());
    assert_eq!(
        root.capabilities().baseline_quarantine,
        CapabilityAvailability::Available
    );
    assert_eq!(
        root.capabilities().strong_no_read,
        CapabilityAvailability::Unavailable
    );
    let read = root.read(
        &ProjectRelativePath::parse("file.dat").unwrap(),
        ReadBudget { max_bytes: 4 },
        ReadAssurance::TransientRaceDetection,
    );
    match root.capabilities().transient_race_detection {
        CapabilityAvailability::Available => assert!(read.is_ok()),
        CapabilityAvailability::Unavailable => {
            assert_eq!(
                read.unwrap_err().code(),
                RootErrorCode::AssuranceUnavailable
            );
        }
    }
}

#[test]
fn rejects_non_exact_case_and_nfc_alias_selection_for_root_direct_reads() {
    for (actual, selected) in [
        ("Manifest.toml", "manifest.toml"),
        ("caf\u{e9}.dat", "cafe\u{301}.dat"),
    ] {
        let temp = tempfile::tempdir().unwrap();
        fs::write(temp.path().join(actual), b"actual").unwrap();
        let error = root(temp.path())
            .read(
                &ProjectRelativePath::parse(selected).unwrap(),
                ReadBudget { max_bytes: 16 },
                ReadAssurance::BaselineQuarantine,
            )
            .unwrap_err();
        assert_eq!(error.code(), RootErrorCode::UnsafeObject);
    }
}

#[test]
fn rejects_non_exact_case_and_nfc_alias_selection_for_walk_roots() {
    for (actual, selected) in [("Fixture", "fixture"), ("caf\u{e9}", "cafe\u{301}")] {
        let temp = tempfile::tempdir().unwrap();
        fs::create_dir(temp.path().join(actual)).unwrap();
        let error = root(temp.path())
            .walk(
                &ProjectRelativePath::parse(selected).unwrap(),
                walk_budget(),
                ReadAssurance::BaselineQuarantine,
            )
            .unwrap_err();
        assert_eq!(error.code(), RootErrorCode::UnsafeObject);
    }
}

#[test]
fn rejects_non_exact_case_and_nfc_alias_selection_at_nested_read_components() {
    for (actual, selected) in [("Nested", "nested"), ("caf\u{e9}", "cafe\u{301}")] {
        let temp = tempfile::tempdir().unwrap();
        fs::create_dir_all(temp.path().join("fixture").join(actual)).unwrap();
        fs::write(
            temp.path().join("fixture").join(actual).join("file.dat"),
            b"actual",
        )
        .unwrap();
        let error = root(temp.path())
            .read(
                &ProjectRelativePath::parse(&format!("fixture/{selected}/file.dat")).unwrap(),
                ReadBudget { max_bytes: 16 },
                ReadAssurance::BaselineQuarantine,
            )
            .unwrap_err();
        assert_eq!(error.code(), RootErrorCode::UnsafeObject);

        let leaf = tempfile::tempdir().unwrap();
        fs::create_dir_all(leaf.path().join("fixture/nested")).unwrap();
        fs::write(leaf.path().join("fixture/nested").join(actual), b"actual").unwrap();
        let error = root(leaf.path())
            .read(
                &ProjectRelativePath::parse(&format!("fixture/nested/{selected}")).unwrap(),
                ReadBudget { max_bytes: 16 },
                ReadAssurance::BaselineQuarantine,
            )
            .unwrap_err();
        assert_eq!(error.code(), RootErrorCode::UnsafeObject);
    }
}

#[cfg(unix)]
#[test]
fn rejects_case_and_nfc_aliases_for_root_direct_reads() {
    for (selected, alias) in [
        ("Manifest.toml", "manifest.toml"),
        ("caf\u{e9}.dat", "cafe\u{301}.dat"),
    ] {
        let temp = tempfile::tempdir().unwrap();
        fs::write(temp.path().join(selected), b"selected").unwrap();
        fs::write(temp.path().join(alias), b"alias").unwrap();
        let error = root(temp.path())
            .read(
                &ProjectRelativePath::parse(selected).unwrap(),
                ReadBudget { max_bytes: 16 },
                ReadAssurance::BaselineQuarantine,
            )
            .unwrap_err();
        assert_eq!(error.code(), RootErrorCode::UnsafeObject);
    }
}

#[cfg(unix)]
#[test]
fn rejects_case_and_nfc_aliases_for_selected_walk_roots() {
    for (selected, alias) in [("Fixture", "fixture"), ("caf\u{e9}", "cafe\u{301}")] {
        let temp = tempfile::tempdir().unwrap();
        fs::create_dir(temp.path().join(selected)).unwrap();
        fs::create_dir(temp.path().join(alias)).unwrap();
        let error = root(temp.path())
            .walk(
                &ProjectRelativePath::parse(selected).unwrap(),
                walk_budget(),
                ReadAssurance::BaselineQuarantine,
            )
            .unwrap_err();
        assert_eq!(error.code(), RootErrorCode::UnsafeObject);
    }
}

#[cfg(unix)]
#[test]
fn rejects_case_and_nfc_aliases_at_every_nested_direct_read_component() {
    for (selected, alias) in [("Nested", "nested"), ("caf\u{e9}", "cafe\u{301}")] {
        let temp = tempfile::tempdir().unwrap();
        fs::create_dir_all(temp.path().join("fixture").join(selected)).unwrap();
        fs::create_dir(temp.path().join("fixture").join(alias)).unwrap();
        fs::write(
            temp.path().join("fixture").join(selected).join("file.dat"),
            b"selected",
        )
        .unwrap();
        let error = root(temp.path())
            .read(
                &ProjectRelativePath::parse(&format!("fixture/{selected}/file.dat")).unwrap(),
                ReadBudget { max_bytes: 16 },
                ReadAssurance::BaselineQuarantine,
            )
            .unwrap_err();
        assert_eq!(error.code(), RootErrorCode::UnsafeObject);

        let leaf = tempfile::tempdir().unwrap();
        fs::create_dir_all(leaf.path().join("fixture/nested")).unwrap();
        fs::write(
            leaf.path().join("fixture/nested").join(selected),
            b"selected",
        )
        .unwrap();
        fs::write(leaf.path().join("fixture/nested").join(alias), b"alias").unwrap();
        let error = root(leaf.path())
            .read(
                &ProjectRelativePath::parse(&format!("fixture/nested/{selected}")).unwrap(),
                ReadBudget { max_bytes: 16 },
                ReadAssurance::BaselineQuarantine,
            )
            .unwrap_err();
        assert_eq!(error.code(), RootErrorCode::UnsafeObject);
    }
}

#[cfg(unix)]
#[test]
fn rejects_symlink_and_hardlink_without_disclosing_outside_canary() {
    use sha2::{Digest, Sha256};
    use std::os::unix::fs::symlink;
    let temp = tempfile::tempdir().unwrap();
    fs::create_dir(temp.path().join("fixture")).unwrap();
    let marker = "OUTSIDE_ROOT_CANARY_9d14b7";
    let outside = temp.path().join("outside-secret");
    fs::write(&outside, marker).unwrap();
    symlink(&outside, temp.path().join("fixture/link")).unwrap();
    let root = root(temp.path());
    let error = root
        .walk(
            &ProjectRelativePath::parse("fixture").unwrap(),
            walk_budget(),
            ReadAssurance::BaselineQuarantine,
        )
        .unwrap_err();
    assert_eq!(error.code(), RootErrorCode::UnsafeObject);
    assert!(!error.to_string().contains(marker));
    assert!(!error
        .to_string()
        .contains(&hex::encode(Sha256::digest(marker.as_bytes()))));

    fs::remove_file(temp.path().join("fixture/link")).unwrap();
    fs::hard_link(&outside, temp.path().join("fixture/alias")).unwrap();
    let error = root
        .walk(
            &ProjectRelativePath::parse("fixture").unwrap(),
            walk_budget(),
            ReadAssurance::BaselineQuarantine,
        )
        .unwrap_err();
    assert_eq!(error.code(), RootErrorCode::UnsafeObject);
    assert!(!error.to_string().contains(marker));
}

#[cfg(unix)]
#[test]
fn rejects_case_and_unicode_normalization_collisions() {
    for names in [
        ("Actor.dat", "actor.dat"),
        ("caf\u{e9}.dat", "cafe\u{301}.dat"),
    ] {
        let temp = tempfile::tempdir().unwrap();
        fs::create_dir(temp.path().join("fixture")).unwrap();
        fs::write(temp.path().join("fixture").join(names.0), b"a").unwrap();
        fs::write(temp.path().join("fixture").join(names.1), b"b").unwrap();
        let error = root(temp.path())
            .walk(
                &ProjectRelativePath::parse("fixture").unwrap(),
                walk_budget(),
                ReadAssurance::BaselineQuarantine,
            )
            .unwrap_err();
        assert_eq!(error.code(), RootErrorCode::UnsafeObject);
    }
}
