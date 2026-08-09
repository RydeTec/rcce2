use rcce_project::{
    MetadataBudget, MetadataLocation, ProjectRoot, ProjectShape, ProjectSnapshot, ReadAssurance,
    ReadBudget, RootControl, RootErrorCode, ScanControl, SnapshotError, SnapshotProgress,
    StateClass,
};
use std::fs;

fn budget() -> MetadataBudget {
    MetadataBudget {
        max_entries: 128,
        max_directories: 32,
        max_depth: 8,
        max_path_bytes: 512,
        max_component_bytes: 128,
        max_declared_bytes: 2 * 1024 * 1024,
        max_single_file_bytes: 1024 * 1024,
    }
}

fn load(root: &ProjectRoot) -> (ProjectSnapshot, Vec<SnapshotProgress>) {
    let mut events = Vec::new();
    let snapshot = ProjectSnapshot::load(
        root,
        budget(),
        ReadAssurance::BaselineQuarantine,
        || ScanControl::Continue,
        |event| events.push(event.clone()),
    )
    .unwrap();
    (snapshot, events)
}

#[test]
fn inventories_unknowns_by_path_and_is_deterministic() {
    let temp = tempfile::tempdir().unwrap();
    fs::create_dir_all(temp.path().join("Data/Server Data")).unwrap();
    fs::write(temp.path().join("Data/Server Data/Actors.dat"), b"equal").unwrap();
    fs::write(temp.path().join("Data/Server Data/Items.dat"), b"equal").unwrap();
    fs::write(temp.path().join("ordinary.opaque"), b"unknown").unwrap();
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let (first, events) = load(&root);
    let (second, second_events) = load(&root);
    assert_eq!(first, second);
    assert_eq!(events, second_events);
    assert_eq!(first.inventory().files.len(), 3);
    assert_eq!(first.inventory().shape, ProjectShape::AuthoringProject);
    assert_eq!(
        first.inventory().files[0].path,
        "Data/Server Data/Actors.dat"
    );
    assert_eq!(
        first.inventory().files[1].path,
        "Data/Server Data/Items.dat"
    );
    assert_eq!(
        first.inventory().files[0].fingerprint,
        first.inventory().files[1].fingerprint
    );
    assert_ne!(
        first.inventory().files[0].path,
        first.inventory().files[1].path
    );
    assert_eq!(
        first.inventory().files[2].classification.state,
        StateClass::Unknown
    );
}

#[test]
fn cancellation_returns_no_partial_snapshot_or_speculative_progress() {
    use std::cell::Cell;
    let temp = tempfile::tempdir().unwrap();
    fs::write(temp.path().join("large.bin"), vec![7_u8; 200_000]).unwrap();
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let metadata_seen = Cell::new(false);
    let read_calls = Cell::new(0_u64);
    let mut events = Vec::new();
    let result = ProjectSnapshot::load(
        &root,
        budget(),
        ReadAssurance::BaselineQuarantine,
        || {
            if metadata_seen.get() {
                read_calls.set(read_calls.get() + 1);
            }
            if metadata_seen.get() && read_calls.get() == 2 {
                ScanControl::Cancel
            } else {
                ScanControl::Continue
            }
        },
        |event| {
            if matches!(event, SnapshotProgress::MetadataAccepted { .. }) {
                metadata_seen.set(true);
            }
            events.push(event.clone());
        },
    );
    assert!(matches!(result, Err(SnapshotError::Cancelled)));
    assert!(events.iter().all(|event| !matches!(
        event,
        SnapshotProgress::FileAccepted { .. } | SnapshotProgress::Complete { .. }
    )));
}

#[test]
fn cancellation_during_enumeration_publishes_no_progress() {
    let temp = tempfile::tempdir().unwrap();
    for index in 0..10 {
        fs::write(temp.path().join(format!("{index}.bin")), b"x").unwrap();
    }
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let mut calls = 0_u64;
    let mut events = Vec::new();
    let result = ProjectSnapshot::load(
        &root,
        budget(),
        ReadAssurance::BaselineQuarantine,
        || {
            calls += 1;
            if calls == 4 {
                ScanControl::Cancel
            } else {
                ScanControl::Continue
            }
        },
        |event| events.push(event.clone()),
    );
    assert!(matches!(result, Err(SnapshotError::Cancelled)));
    assert!(events.is_empty());
}

#[test]
fn metadata_enumeration_checkpoints_do_not_scale_with_file_body_size() {
    let mut counts = Vec::new();
    for size in [1, 256 * 1024] {
        let temp = tempfile::tempdir().unwrap();
        fs::write(temp.path().join("body.bin"), vec![b'x'; size]).unwrap();
        let root = ProjectRoot::open_explicit(temp.path()).unwrap();
        let mut calls = 0_u64;
        let metadata = root
            .metadata_controlled(budget(), ReadAssurance::BaselineQuarantine, || {
                calls += 1;
                RootControl::Continue
            })
            .unwrap();
        assert_eq!(metadata.entries.len(), 1);
        counts.push(calls);
    }
    assert_eq!(counts[0], counts[1]);
}

#[test]
fn retained_identity_rejects_replacements_and_reads_same_object_at_snapshot_time() {
    for replacement in [b"x".as_slice(), b"WXYZ".as_slice()] {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("bound.bin");
        fs::write(&path, b"ABCD").unwrap();
        let root = ProjectRoot::open_explicit(temp.path()).unwrap();
        let metadata = root
            .metadata(budget(), ReadAssurance::BaselineQuarantine)
            .unwrap();
        let entry = metadata.entries.iter().find(|entry| matches!(entry.location, MetadataLocation::Portable(ref path) if path == "bound.bin")).unwrap();
        fs::remove_file(&path).unwrap();
        fs::write(&path, replacement).unwrap();
        let error = root
            .read_enumerated_controlled(
                entry,
                ReadBudget { max_bytes: 16 },
                ReadAssurance::BaselineQuarantine,
                || RootControl::Continue,
            )
            .unwrap_err();
        assert_eq!(error.code(), RootErrorCode::Integrity);
    }
    let temp = tempfile::tempdir().unwrap();
    let path = temp.path().join("bound.bin");
    fs::write(&path, b"ABCD").unwrap();
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let metadata = root
        .metadata(budget(), ReadAssurance::BaselineQuarantine)
        .unwrap();
    let entry = metadata.entries.iter().find(|entry| matches!(entry.location, MetadataLocation::Portable(ref path) if path == "bound.bin")).unwrap();
    fs::write(&path, b"WXYZ").unwrap();
    let result = root.read_enumerated_controlled(
        entry,
        ReadBudget { max_bytes: 16 },
        ReadAssurance::BaselineQuarantine,
        || RootControl::Continue,
    );
    #[cfg(unix)]
    assert_eq!(result.unwrap_err().code(), RootErrorCode::Integrity);
    #[cfg(windows)]
    match result {
        Ok(accepted) => assert_eq!(accepted.bytes().as_slice(), b"WXYZ"),
        Err(error) => assert_eq!(error.code(), RootErrorCode::Integrity),
    }
}

#[test]
fn root_read_cancels_between_actual_bounded_chunks_without_returning_bytes() {
    let temp = tempfile::tempdir().unwrap();
    fs::write(temp.path().join("large.bin"), vec![9_u8; 200_000]).unwrap();
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let metadata = root
        .metadata(budget(), ReadAssurance::BaselineQuarantine)
        .unwrap();
    let entry = metadata
        .entries
        .iter()
        .find(|entry| matches!(entry.kind, rcce_project::MetadataKind::File { .. }))
        .unwrap();
    let mut calls = 0;
    let error = root
        .read_enumerated_controlled(
            entry,
            ReadBudget { max_bytes: 300_000 },
            ReadAssurance::BaselineQuarantine,
            || {
                calls += 1;
                if calls == 2 {
                    RootControl::Cancel
                } else {
                    RootControl::Continue
                }
            },
        )
        .unwrap_err();
    assert_eq!(error.code(), RootErrorCode::Cancelled);
}

#[test]
fn enumerated_reads_are_repeatable_after_success_and_cancellation() {
    let temp = tempfile::tempdir().unwrap();
    let body = vec![9_u8; 200_000];
    fs::write(temp.path().join("repeatable.bin"), &body).unwrap();
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let metadata = root
        .metadata(budget(), ReadAssurance::BaselineQuarantine)
        .unwrap();
    let entry = metadata
        .entries
        .iter()
        .find(|entry| matches!(entry.kind, rcce_project::MetadataKind::File { .. }))
        .unwrap();
    for _ in 0..2 {
        let accepted = root
            .read_enumerated_controlled(
                entry,
                ReadBudget { max_bytes: 300_000 },
                ReadAssurance::BaselineQuarantine,
                || RootControl::Continue,
            )
            .unwrap();
        assert_eq!(accepted.bytes().as_slice(), body);
    }
    let mut calls = 0_u64;
    let cancelled = root
        .read_enumerated_controlled(
            entry,
            ReadBudget { max_bytes: 300_000 },
            ReadAssurance::BaselineQuarantine,
            || {
                calls += 1;
                if calls == 2 {
                    RootControl::Cancel
                } else {
                    RootControl::Continue
                }
            },
        )
        .unwrap_err();
    assert_eq!(cancelled.code(), RootErrorCode::Cancelled);
    let retried = root
        .read_enumerated_controlled(
            entry,
            ReadBudget { max_bytes: 300_000 },
            ReadAssurance::BaselineQuarantine,
            || RootControl::Continue,
        )
        .unwrap();
    assert_eq!(retried.bytes().as_slice(), body);
}

#[cfg(windows)]
#[test]
fn retained_windows_reads_are_positionally_concurrent() {
    let temp = tempfile::tempdir().unwrap();
    let body = vec![3_u8; 200_000];
    fs::write(temp.path().join("concurrent.bin"), &body).unwrap();
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let metadata = root
        .metadata(budget(), ReadAssurance::BaselineQuarantine)
        .unwrap();
    let entry = metadata
        .entries
        .iter()
        .find(|entry| matches!(entry.kind, rcce_project::MetadataKind::File { .. }))
        .unwrap();
    std::thread::scope(|scope| {
        let reads = (0..2)
            .map(|_| {
                scope.spawn(|| {
                    root.read_enumerated_controlled(
                        entry,
                        ReadBudget { max_bytes: 300_000 },
                        ReadAssurance::BaselineQuarantine,
                        || RootControl::Continue,
                    )
                    .unwrap()
                })
            })
            .collect::<Vec<_>>();
        for read in reads {
            assert_eq!(read.join().unwrap().bytes().as_slice(), body);
        }
    });
}

#[cfg(unix)]
#[test]
fn unsafe_and_non_utf8_entries_are_metadata_only_and_do_not_hide_safe_files() {
    use std::os::unix::ffi::OsStringExt;
    use std::os::unix::fs::symlink;
    let temp = tempfile::tempdir().unwrap();
    let outside = tempfile::tempdir().unwrap();
    fs::write(outside.path().join("canary"), b"do-not-read").unwrap();
    fs::write(temp.path().join("safe.bin"), b"safe").unwrap();
    symlink(outside.path().join("canary"), temp.path().join("link")).unwrap();
    fs::write(
        temp.path()
            .join(std::ffi::OsString::from_vec(vec![0xff, b'x'])),
        b"opaque",
    )
    .unwrap();
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let (snapshot, _) = load(&root);
    assert_eq!(snapshot.inventory().files.len(), 1);
    assert_eq!(snapshot.inventory().unavailable.len(), 2);
    assert!(snapshot
        .inventory()
        .unavailable
        .iter()
        .any(|entry| matches!(entry.location, MetadataLocation::Opaque(_))));
}

#[cfg(unix)]
#[test]
fn opaque_unix_names_obey_component_and_aggregate_path_ceilings() {
    use std::os::unix::ffi::OsStringExt;

    let component_root = tempfile::tempdir().unwrap();
    fs::write(
        component_root
            .path()
            .join(std::ffi::OsString::from_vec(vec![0xff, b'a', b'b'])),
        b"opaque",
    )
    .unwrap();
    let mut component_budget = budget();
    component_budget.max_component_bytes = 2;
    let component_error = ProjectRoot::open_explicit(component_root.path())
        .unwrap()
        .metadata(component_budget, ReadAssurance::BaselineQuarantine)
        .unwrap_err();
    assert_eq!(component_error.code(), RootErrorCode::ResourceLimit);

    let path_root = tempfile::tempdir().unwrap();
    fs::create_dir(path_root.path().join("dir")).unwrap();
    fs::write(
        path_root
            .path()
            .join("dir")
            .join(std::ffi::OsString::from_vec(vec![0xff])),
        b"opaque",
    )
    .unwrap();
    let mut path_budget = budget();
    path_budget.max_component_bytes = 8;
    path_budget.max_path_bytes = 4;
    let path_error = ProjectRoot::open_explicit(path_root.path())
        .unwrap()
        .metadata(path_budget, ReadAssurance::BaselineQuarantine)
        .unwrap_err();
    assert_eq!(path_error.code(), RootErrorCode::ResourceLimit);
}

#[cfg(unix)]
#[test]
fn constrained_opaque_unix_name_preserves_raw_evidence_and_safe_sibling() {
    use std::os::unix::ffi::OsStringExt;

    let temp = tempfile::tempdir().unwrap();
    let opaque = vec![0xff, b'x'];
    fs::write(
        temp.path()
            .join(std::ffi::OsString::from_vec(opaque.clone())),
        b"opaque",
    )
    .unwrap();
    fs::write(temp.path().join("ok"), b"safe").unwrap();
    let mut constrained = budget();
    constrained.max_component_bytes = 2;
    constrained.max_path_bytes = 4;
    let metadata = ProjectRoot::open_explicit(temp.path())
        .unwrap()
        .metadata(constrained, ReadAssurance::BaselineQuarantine)
        .unwrap();
    assert!(metadata
        .entries
        .iter()
        .any(|entry| entry.location == MetadataLocation::Opaque(vec![opaque.clone()])));
    assert!(metadata
        .entries
        .iter()
        .any(|entry| matches!(&entry.location, MetadataLocation::Portable(path) if path == "ok")));
}

#[cfg(windows)]
#[test]
fn opaque_windows_names_obey_component_and_aggregate_path_ceilings() {
    use std::os::windows::ffi::OsStringExt;

    let component_root = tempfile::tempdir().unwrap();
    fs::write(
        component_root
            .path()
            .join(std::ffi::OsString::from_wide(&[0xd800, b'a' as u16])),
        b"opaque",
    )
    .unwrap();
    let mut component_budget = budget();
    component_budget.max_component_bytes = 2;
    let component_error = ProjectRoot::open_explicit(component_root.path())
        .unwrap()
        .metadata(component_budget, ReadAssurance::BaselineQuarantine)
        .unwrap_err();
    assert_eq!(component_error.code(), RootErrorCode::ResourceLimit);

    let parent_root = tempfile::tempdir().unwrap();
    fs::create_dir(parent_root.path().join("aa")).unwrap();
    fs::write(
        parent_root
            .path()
            .join("aa")
            .join(std::ffi::OsString::from_wide(&[0xd800])),
        b"opaque",
    )
    .unwrap();
    let mut parent_budget = budget();
    parent_budget.max_component_bytes = 2;
    let parent_error = ProjectRoot::open_explicit(parent_root.path())
        .unwrap()
        .metadata(parent_budget, ReadAssurance::BaselineQuarantine)
        .unwrap_err();
    assert_eq!(parent_error.code(), RootErrorCode::ResourceLimit);

    let path_root = tempfile::tempdir().unwrap();
    fs::create_dir(path_root.path().join("d")).unwrap();
    fs::write(
        path_root
            .path()
            .join("d")
            .join(std::ffi::OsString::from_wide(&[0xd800])),
        b"opaque",
    )
    .unwrap();
    let mut path_budget = budget();
    path_budget.max_component_bytes = 4;
    path_budget.max_path_bytes = 4;
    let path_error = ProjectRoot::open_explicit(path_root.path())
        .unwrap()
        .metadata(path_budget, ReadAssurance::BaselineQuarantine)
        .unwrap_err();
    assert_eq!(path_error.code(), RootErrorCode::ResourceLimit);
}

#[cfg(windows)]
#[test]
fn constrained_opaque_windows_name_preserves_raw_evidence_and_safe_sibling() {
    use std::os::windows::ffi::OsStringExt;

    let temp = tempfile::tempdir().unwrap();
    fs::create_dir(temp.path().join("aa")).unwrap();
    fs::write(
        temp.path()
            .join("aa")
            .join(std::ffi::OsString::from_wide(&[0xd800])),
        b"opaque",
    )
    .unwrap();
    fs::write(temp.path().join("ok"), b"safe").unwrap();
    let mut constrained = budget();
    constrained.max_component_bytes = 4;
    constrained.max_path_bytes = 7;
    let metadata = ProjectRoot::open_explicit(temp.path())
        .unwrap()
        .metadata(constrained, ReadAssurance::BaselineQuarantine)
        .unwrap();
    assert!(metadata.entries.iter().any(|entry| {
        entry.location
            == MetadataLocation::Opaque(vec![vec![b'a', 0x00, b'a', 0x00], vec![0x00, 0xd8]])
    }));
    assert!(metadata
        .entries
        .iter()
        .any(|entry| matches!(&entry.location, MetadataLocation::Portable(path) if path == "ok")));
}

#[test]
fn runtime_projection_and_empty_shapes_are_descriptive() {
    let empty = tempfile::tempdir().unwrap();
    assert_eq!(
        load(&ProjectRoot::open_explicit(empty.path()).unwrap())
            .0
            .inventory()
            .shape,
        ProjectShape::Empty
    );
    let projection = tempfile::tempdir().unwrap();
    fs::create_dir(projection.path().join("Game")).unwrap();
    fs::write(projection.path().join("Game/runtime.dat"), b"x").unwrap();
    let snapshot = load(&ProjectRoot::open_explicit(projection.path()).unwrap()).0;
    assert_eq!(
        snapshot.inventory().shape,
        ProjectShape::RuntimeProjectionOnly
    );
    assert!(!snapshot.inventory().files[0].classification.authoring_input);
}

#[test]
fn runtime_private_outputs_have_a_distinct_shape() {
    for relative in ["Data/Logs/server.txt", "Data/Server Data/Accounts.dat"] {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join(relative);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, b"runtime").unwrap();
        let snapshot = load(&ProjectRoot::open_explicit(temp.path()).unwrap()).0;
        assert_eq!(snapshot.inventory().shape, ProjectShape::RuntimeOutputOnly);
    }
}

#[test]
fn explicit_resource_budgets_fail_closed() {
    let temp = tempfile::tempdir().unwrap();
    fs::write(temp.path().join("one"), b"1").unwrap();
    fs::write(temp.path().join("two"), b"2").unwrap();
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let mut limited = budget();
    limited.max_entries = 1;
    assert!(matches!(
        ProjectSnapshot::load(
            &root,
            limited,
            ReadAssurance::BaselineQuarantine,
            || ScanControl::Continue,
            |_| {}
        ),
        Err(SnapshotError::Root(_))
    ));
}

#[cfg(unix)]
#[test]
fn aliases_hardlinks_and_special_files_are_visible_but_inaccessible() {
    use std::os::unix::net::UnixListener;
    let temp = tempfile::tempdir().unwrap();
    fs::write(temp.path().join("Case.dat"), b"a").unwrap();
    fs::write(temp.path().join("case.dat"), b"b").unwrap();
    fs::write(temp.path().join("owned"), b"hard").unwrap();
    fs::hard_link(temp.path().join("owned"), temp.path().join("alias")).unwrap();
    let _socket = UnixListener::bind(temp.path().join("socket")).unwrap();
    fs::write(temp.path().join("safe"), b"ok").unwrap();
    let snapshot = load(&ProjectRoot::open_explicit(temp.path()).unwrap()).0;
    assert_eq!(snapshot.inventory().files.len(), 1);
    assert_eq!(snapshot.inventory().files[0].path, "safe");
    assert_eq!(snapshot.inventory().unavailable.len(), 5);
    assert_eq!(snapshot.inventory().shape, ProjectShape::UnknownOnly);
}

#[test]
fn oversized_hardlinks_do_not_consume_accepted_content_budgets() {
    let temp = tempfile::tempdir().unwrap();
    fs::write(temp.path().join("oversized"), vec![b'x'; 64]).unwrap();
    fs::hard_link(
        temp.path().join("oversized"),
        temp.path().join("oversized-alias"),
    )
    .unwrap();
    fs::write(temp.path().join("safe"), b"ok").unwrap();
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let mut constrained = budget();
    constrained.max_single_file_bytes = 4;
    constrained.max_declared_bytes = 4;
    let snapshot = ProjectSnapshot::load(
        &root,
        constrained,
        ReadAssurance::BaselineQuarantine,
        || ScanControl::Continue,
        |_| {},
    )
    .unwrap();
    assert_eq!(snapshot.inventory().files.len(), 1);
    assert_eq!(snapshot.inventory().files[0].path, "safe");
    assert_eq!(snapshot.inventory().unavailable.len(), 2);
}

#[test]
fn unrestricted_requested_depth_still_hits_the_recursion_safety_cap() {
    let temp = tempfile::tempdir().unwrap();
    let mut current = temp.path().to_path_buf();
    for _ in 0..65 {
        current.push("d");
        fs::create_dir(&current).unwrap();
    }
    fs::write(current.join("leaf"), b"never reached").unwrap();
    let root = ProjectRoot::open_explicit(temp.path()).unwrap();
    let mut untrusted = budget();
    untrusted.max_entries = u64::MAX;
    untrusted.max_directories = u64::MAX;
    untrusted.max_depth = u64::MAX;
    untrusted.max_path_bytes = u64::MAX;
    let error = root
        .metadata(untrusted, ReadAssurance::BaselineQuarantine)
        .unwrap_err();
    assert_eq!(error.code(), RootErrorCode::ResourceLimit);
}
