use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

const EXPECTED_CRATES: [&str; 8] = [
    "rcce-admin",
    "rcce-editor",
    "rcce-editor-core",
    "rcce-migrate",
    "rcce-project",
    "rcce-project-cli",
    "rcce-storage",
    "rcce-validation",
];

const EXPECTED_FILES: [&str; 21] = [
    ".gitignore",
    "Cargo.lock",
    "Cargo.toml",
    "README.md",
    "crates/rcce-admin/Cargo.toml",
    "crates/rcce-admin/src/lib.rs",
    "crates/rcce-editor-core/Cargo.toml",
    "crates/rcce-editor-core/src/lib.rs",
    "crates/rcce-editor-core/tests/skeleton_contract.rs",
    "crates/rcce-editor/Cargo.toml",
    "crates/rcce-editor/src/main.rs",
    "crates/rcce-migrate/Cargo.toml",
    "crates/rcce-migrate/src/lib.rs",
    "crates/rcce-project-cli/Cargo.toml",
    "crates/rcce-project-cli/src/main.rs",
    "crates/rcce-project/Cargo.toml",
    "crates/rcce-project/src/lib.rs",
    "crates/rcce-storage/Cargo.toml",
    "crates/rcce-storage/src/lib.rs",
    "crates/rcce-validation/Cargo.toml",
    "crates/rcce-validation/src/lib.rs",
];

fn workspace_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../..")
}

fn collect_files(directory: &Path, root: &Path, output: &mut Vec<String>) {
    for entry in fs::read_dir(directory).expect("workspace directory must be readable") {
        let path = entry.expect("workspace entry must be readable").path();
        if path == root.join("target") {
            continue;
        }
        if path.is_dir() {
            collect_files(&path, root, output);
        } else {
            output.push(
                path.strip_prefix(root)
                    .expect("workspace file must remain below root")
                    .to_string_lossy()
                    .replace('\\', "/"),
            );
        }
    }
}

fn source_files(directory: &Path, output: &mut Vec<PathBuf>) {
    for entry in fs::read_dir(directory).expect("production source directory must be readable") {
        let path = entry.expect("source entry must be readable").path();
        if path.is_dir() {
            source_files(&path, output);
        } else if path.extension().is_some_and(|extension| extension == "rs") {
            output.push(path);
        }
    }
}

fn metadata() -> Value {
    let output = Command::new(env!("CARGO"))
        .args([
            "metadata",
            "--manifest-path",
            workspace_root()
                .join("Cargo.toml")
                .to_str()
                .expect("workspace path must be UTF-8"),
            "--locked",
            "--no-deps",
            "--format-version",
            "1",
        ])
        .output()
        .expect("cargo metadata must execute");
    assert!(
        output.status.success(),
        "cargo metadata failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    serde_json::from_slice(&output.stdout).expect("cargo metadata must be valid JSON")
}

fn expected_normal_dependencies() -> BTreeMap<&'static str, BTreeSet<&'static str>> {
    BTreeMap::from([
        ("rcce-admin", BTreeSet::new()),
        ("rcce-editor", BTreeSet::from(["rcce-editor-core"])),
        (
            "rcce-editor-core",
            BTreeSet::from(["rcce-project", "rcce-validation"]),
        ),
        (
            "rcce-migrate",
            BTreeSet::from(["rcce-project", "rcce-storage"]),
        ),
        ("rcce-project", BTreeSet::new()),
        ("rcce-project-cli", BTreeSet::new()),
        ("rcce-storage", BTreeSet::new()),
        ("rcce-validation", BTreeSet::from(["rcce-project"])),
    ])
}

fn expected_targets() -> BTreeMap<&'static str, BTreeSet<(&'static str, &'static str, &'static str)>>
{
    BTreeMap::from([
        (
            "rcce-admin",
            BTreeSet::from([("rcce_admin", "lib", "crates/rcce-admin/src/lib.rs")]),
        ),
        (
            "rcce-editor",
            BTreeSet::from([("rcce-editor", "bin", "crates/rcce-editor/src/main.rs")]),
        ),
        (
            "rcce-editor-core",
            BTreeSet::from([
                (
                    "rcce_editor_core",
                    "lib",
                    "crates/rcce-editor-core/src/lib.rs",
                ),
                (
                    "skeleton_contract",
                    "test",
                    "crates/rcce-editor-core/tests/skeleton_contract.rs",
                ),
            ]),
        ),
        (
            "rcce-migrate",
            BTreeSet::from([("rcce_migrate", "lib", "crates/rcce-migrate/src/lib.rs")]),
        ),
        (
            "rcce-project",
            BTreeSet::from([("rcce_project", "lib", "crates/rcce-project/src/lib.rs")]),
        ),
        (
            "rcce-project-cli",
            BTreeSet::from([("rcce-project", "bin", "crates/rcce-project-cli/src/main.rs")]),
        ),
        (
            "rcce-storage",
            BTreeSet::from([("rcce_storage", "lib", "crates/rcce-storage/src/lib.rs")]),
        ),
        (
            "rcce-validation",
            BTreeSet::from([(
                "rcce_validation",
                "lib",
                "crates/rcce-validation/src/lib.rs",
            )]),
        ),
    ])
}

#[test]
fn cargo_metadata_pins_packages_dependencies_features_publication_and_targets() {
    const GUI_PACKAGES: [&str; 7] = ["egui", "eframe", "iced", "slint", "tauri", "winit", "gtk"];

    let root = workspace_root()
        .canonicalize()
        .expect("workspace must exist");
    let document = metadata();
    let packages = document["packages"]
        .as_array()
        .expect("metadata packages must be an array");
    let expected_dependencies = expected_normal_dependencies();
    let expected_targets = expected_targets();
    let mut actual_names = BTreeSet::new();

    assert_eq!(packages.len(), EXPECTED_CRATES.len());
    for package in packages {
        let name = package["name"].as_str().expect("package name must be text");
        actual_names.insert(name);
        assert_eq!(package["version"], "0.1.0", "unexpected version for {name}");
        assert_eq!(package["edition"], "2021", "unexpected edition for {name}");
        assert_eq!(
            package["rust_version"], "1.85",
            "unexpected MSRV for {name}"
        );
        assert_eq!(package["license"], "MIT", "unexpected license for {name}");
        assert_eq!(
            package["publish"].as_array().map(Vec::len),
            Some(0),
            "{name} must be publish=false"
        );
        assert_eq!(
            package["features"].as_object().map(serde_json::Map::len),
            Some(0),
            "{name} must expose no features at P01"
        );

        let expected_manifest = root.join("crates").join(name).join("Cargo.toml");
        assert_eq!(
            Path::new(
                package["manifest_path"]
                    .as_str()
                    .expect("manifest path must be text")
            ),
            expected_manifest
        );

        let mut normal_dependencies = BTreeSet::new();
        let mut dev_dependencies = BTreeSet::new();
        for dependency in package["dependencies"]
            .as_array()
            .expect("dependencies must be an array")
        {
            let dependency_name = dependency["name"]
                .as_str()
                .expect("dependency name must be text");
            assert_eq!(
                dependency["rename"],
                Value::Null,
                "renamed dependency in {name}"
            );
            assert_eq!(
                dependency["target"],
                Value::Null,
                "target-specific dependency in {name}"
            );
            assert_eq!(
                dependency["optional"], false,
                "optional dependency in {name}"
            );
            assert_ne!(dependency["kind"], "build", "build dependency in {name}");
            if name != "rcce-editor" {
                assert!(
                    !GUI_PACKAGES.contains(&dependency_name),
                    "headless package {name} depends on GUI package {dependency_name}"
                );
            }
            match dependency["kind"].as_str() {
                None => {
                    normal_dependencies.insert(dependency_name);
                }
                Some("dev") => {
                    dev_dependencies.insert(dependency_name);
                }
                other => panic!("unexpected dependency kind {other:?} in {name}"),
            }
        }
        assert_eq!(
            normal_dependencies, expected_dependencies[name],
            "unexpected normal dependency edge for {name}"
        );
        let expected_dev = if name == "rcce-editor-core" {
            BTreeSet::from(["serde_json"])
        } else {
            BTreeSet::new()
        };
        assert_eq!(
            dev_dependencies, expected_dev,
            "unexpected dev dependency in {name}"
        );

        let mut actual_targets = BTreeSet::new();
        for target in package["targets"]
            .as_array()
            .expect("targets must be an array")
        {
            let kinds = target["kind"]
                .as_array()
                .expect("target kind must be an array");
            assert_eq!(kinds.len(), 1, "{name} target must have one exact kind");
            let kind = kinds[0].as_str().expect("target kind must be text");
            assert_ne!(kind, "custom-build", "custom build target in {name}");
            let source = Path::new(
                target["src_path"]
                    .as_str()
                    .expect("target source path must be text"),
            )
            .strip_prefix(&root)
            .expect("target source must remain in workspace")
            .to_string_lossy()
            .replace('\\', "/");
            actual_targets.insert((
                target["name"].as_str().expect("target name must be text"),
                kind,
                source,
            ));
        }
        let actual_targets = actual_targets
            .iter()
            .map(|(target_name, kind, source)| (*target_name, *kind, source.as_str()))
            .collect::<BTreeSet<_>>();
        assert_eq!(
            actual_targets, expected_targets[name],
            "unexpected target for {name}"
        );
    }

    assert_eq!(actual_names, EXPECTED_CRATES.into_iter().collect());
    assert!(expected_dependencies["rcce-project-cli"].is_empty());
}

#[test]
fn workspace_file_topology_is_exact_and_has_no_build_or_ffi_sources() {
    let root = workspace_root();
    let mut files = Vec::new();
    collect_files(&root, &root, &mut files);
    files.sort();
    assert_eq!(files, EXPECTED_FILES);
    assert!(files.iter().all(|path| {
        let lower = path.to_ascii_lowercase();
        !lower.ends_with("build.rs")
            && ![".c", ".cc", ".cpp", ".h", ".hpp"]
                .iter()
                .any(|extension| lower.ends_with(extension))
    }));
}

#[test]
fn production_sources_pass_common_mutation_token_smoke() {
    // This is deliberately a heuristic regression smoke, not an AST/capability
    // proof or a substitute for the root confinement and syscall tests in P02.
    const COMMON_MUTATION_OR_PROCESS_TOKENS: [&str; 10] = [
        "fs::write",
        "File::create",
        "OpenOptions",
        "remove_file",
        "remove_dir",
        "create_dir",
        "set_len(",
        "persist(",
        "Command::new",
        "std::process::Command",
    ];

    let root = workspace_root();
    for crate_name in EXPECTED_CRATES {
        let mut sources = Vec::new();
        source_files(
            &root.join("crates").join(crate_name).join("src"),
            &mut sources,
        );
        for source in sources {
            let text = fs::read_to_string(&source).expect("production source must be readable");
            for token in COMMON_MUTATION_OR_PROCESS_TOKENS {
                assert!(
                    !text.contains(token),
                    "{} unexpectedly contains smoke token {token}",
                    source.display()
                );
            }
        }
    }
}
