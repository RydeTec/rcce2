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

const EXPECTED_FILES: [&str; 29] = [
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
    "crates/rcce-project/src/classification.rs",
    "crates/rcce-project/src/fingerprint.rs",
    "crates/rcce-project/src/inventory.rs",
    "crates/rcce-project/src/lib.rs",
    "crates/rcce-project/src/root/backend.rs",
    "crates/rcce-project/src/root/mod.rs",
    "crates/rcce-project/src/snapshot.rs",
    "crates/rcce-project/tests/inventory.rs",
    "crates/rcce-project/tests/root_confinement.rs",
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

fn without_backend_test_modules(text: &str) -> String {
    const TEST_MODULE: &str = "    #[cfg(test)]\n    mod tests {";
    const UNIX_MODULE: &str = "#[cfg(unix)]\nmod unix {";
    let (through_windows, unix) = text
        .split_once(UNIX_MODULE)
        .expect("shared backend must retain explicit Unix module");
    let windows_production = through_windows
        .split_once(TEST_MODULE)
        .expect("shared backend must retain Windows hostile tests")
        .0;
    let unix_production = unix
        .split_once(TEST_MODULE)
        .expect("shared backend must retain Unix hostile tests")
        .0;
    format!("{windows_production}{UNIX_MODULE}{unix_production}")
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
        (
            "rcce-project",
            BTreeSet::from(["sha2", "unicode-normalization"]),
        ),
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
            BTreeSet::from([
                ("rcce_project", "lib", "crates/rcce-project/src/lib.rs"),
                (
                    "inventory",
                    "test",
                    "crates/rcce-project/tests/inventory.rs",
                ),
                (
                    "root_confinement",
                    "test",
                    "crates/rcce-project/tests/root_confinement.rs",
                ),
            ]),
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
        let actual_manifest = Path::new(
            package["manifest_path"]
                .as_str()
                .expect("manifest path must be text"),
        )
        .canonicalize()
        .expect("metadata manifest path must resolve");
        assert_eq!(actual_manifest, expected_manifest);

        let mut normal_dependencies = BTreeSet::new();
        let mut target_dependencies = BTreeSet::new();
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
                    if let Some(target) = dependency["target"].as_str() {
                        target_dependencies.insert((dependency_name, target));
                    } else {
                        normal_dependencies.insert(dependency_name);
                    }
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
        let expected_target = if name == "rcce-project" {
            BTreeSet::from([
                ("cap-fs-ext", "cfg(windows)"),
                ("cap-std", "cfg(windows)"),
                ("rustix", "cfg(unix)"),
                ("windows-sys", "cfg(windows)"),
            ])
        } else {
            BTreeSet::new()
        };
        assert_eq!(
            target_dependencies, expected_target,
            "unexpected target-specific dependency edge for {name}"
        );
        let expected_dev = match name {
            "rcce-editor-core" => BTreeSet::from(["serde_json"]),
            "rcce-project" => BTreeSet::from(["hex", "tempfile"]),
            _ => BTreeSet::new(),
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
            let source_path = Path::new(
                target["src_path"]
                    .as_str()
                    .expect("target source path must be text"),
            )
            .canonicalize()
            .expect("target source path must resolve");
            let source = source_path
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
    const COMMON_MUTATION_OR_PROCESS_TOKENS: [&str; 13] = [
        "fs::write",
        "File::create",
        ".write(true)",
        ".append(true)",
        ".create(true)",
        ".truncate(true)",
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
            let text = if source.ends_with("root/backend.rs") {
                without_backend_test_modules(&text)
            } else {
                text
            };
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
