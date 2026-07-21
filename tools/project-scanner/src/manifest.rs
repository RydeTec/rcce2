use crate::error::ScanError;
use crate::fs::{Budget, Inventory, Root};
use jsonschema::{Draft, JSONSchema};
use rcce_project::ReadAssurance;
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;
use unicode_normalization::UnicodeNormalization;

const ABSOLUTE_MANIFEST_LIMIT: u64 = 64 * 1024 * 1024;
const ACCEPTED_SCHEMA_BYTES: &[u8] = include_bytes!("../../../test-data/projects/schema-v1.json");
const ACCEPTED_SCHEMA_SHA256: &str =
    "765ce754a762a43d77783a48eb0c4197ac9029fb4d68eeb4cf76a23e12df284e";
const STATE_CLASS_ORDER: [&str; 7] = [
    "PublicClient",
    "ServerConfig",
    "Secret",
    "DynamicPrivate",
    "EditorMetadata",
    "AuthoringSource",
    "Unknown",
];

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct ProjectScan {
    pub id: String,
    pub availability: String,
    pub files: u64,
    pub bytes: u64,
    pub state_classes: Vec<String>,
    pub canaries: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct ManifestScan {
    pub projects: Vec<ProjectScan>,
}

pub(crate) fn scan(
    manifest_path: &Path,
    registry_path: Option<&Path>,
) -> Result<ManifestScan, ScanError> {
    scan_with_assurance(
        manifest_path,
        registry_path,
        ReadAssurance::BaselineQuarantine,
    )
}

pub(crate) fn scan_with_assurance(
    manifest_path: &Path,
    registry_path: Option<&Path>,
    assurance: ReadAssurance,
) -> Result<ManifestScan, ScanError> {
    scan_with_assurance_and_before_content(manifest_path, registry_path, assurance, || {})
}

fn scan_with_assurance_and_before_content(
    manifest_path: &Path,
    registry_path: Option<&Path>,
    assurance: ReadAssurance,
    before_content: impl FnOnce(),
) -> Result<ManifestScan, ScanError> {
    let parent = manifest_path
        .parent()
        .filter(|path| !path.as_os_str().is_empty())
        .unwrap_or_else(|| Path::new("."));
    let name = manifest_path
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| {
            ScanError::Semantic("manifest path must have one portable UTF-8 filename".to_owned())
        })?;
    let root = Root::open_with_assurance(parent, assurance)?;
    before_content();
    let manifest_bytes = root.read_component(name, ABSOLUTE_MANIFEST_LIMIT)?;
    let manifest_text = std::str::from_utf8(&manifest_bytes)
        .map_err(|_| ScanError::ManifestToml("manifest is not UTF-8".to_owned()))?;
    let manifest_toml: toml::Value = toml::from_str(manifest_text).map_err(|_| {
        ScanError::ManifestToml("syntax rejected without echoing source bytes".to_owned())
    })?;
    let manifest_json = serde_json::to_value(manifest_toml)
        .map_err(|error| ScanError::ManifestToml(error.to_string()))?;
    reject_marker_like_values(&manifest_json)?;

    let schema_name = string_at(&manifest_json, "schema")?;
    validate_portable_path(schema_name, true)?;
    if schema_name != "schema-v1.json" {
        return Err(ScanError::SchemaDefinition(
            "manifest must name the accepted schema-v1.json authority".to_owned(),
        ));
    }
    let schema_bytes = root.read_component(schema_name, ABSOLUTE_MANIFEST_LIMIT)?;
    let accepted_digest = hex::decode(ACCEPTED_SCHEMA_SHA256)
        .expect("accepted schema digest constant must be valid hex");
    if Sha256::digest(ACCEPTED_SCHEMA_BYTES).as_slice() != accepted_digest
        || Sha256::digest(&schema_bytes).as_slice() != accepted_digest
        || schema_bytes != ACCEPTED_SCHEMA_BYTES
    {
        return Err(ScanError::SchemaDefinition(
            "sibling schema bytes differ from the binary-pinned accepted schema".to_owned(),
        ));
    }
    let mut schema_json: Value = serde_json::from_slice(ACCEPTED_SCHEMA_BYTES).map_err(|_| {
        ScanError::SchemaDefinition("binary-pinned accepted schema cannot be decoded".to_owned())
    })?;
    // jsonschema 0.18 requires an absolute resolution base. The checked-in
    // schema intentionally has the portable relative `$id` `schema-v1.json`;
    // substitute an inert base in memory without changing any schema rule.
    if schema_json.get("$id").and_then(Value::as_str) == Some("schema-v1.json") {
        schema_json["$id"] = Value::String("https://rcce.invalid/schema-v1.json".to_owned());
    }
    let mut options = JSONSchema::options();
    options
        .with_draft(Draft::Draft202012)
        .should_validate_formats(true);
    let compiled = options
        .compile(&schema_json)
        .map_err(|error| ScanError::SchemaDefinition(error.to_string()))?;
    if let Err(errors) = compiled.validate(&manifest_json) {
        let summary = errors
            .take(8)
            .map(|error| format!("{}: rejected by schema rule", error.instance_path))
            .collect::<Vec<_>>()
            .join("; ");
        return Err(ScanError::SchemaValidation(summary));
    }

    let projects = array_at(&manifest_json, "projects")?;
    let mut ids = BTreeSet::new();
    let mut roots = BTreeSet::new();
    let mut normalized_roots = BTreeSet::new();
    let has_declared_canaries = projects
        .iter()
        .any(|project| !array_at(project, "canaries").unwrap_or(&[]).is_empty());
    let registry = match registry_path {
        Some(path) => Some(crate::registry::Registry::load_with_assurance(
            path, assurance,
        )?),
        None if has_declared_canaries => {
            return Err(ScanError::Semantic(
                "canary-bearing manifest requires explicit --canary-registry".to_owned(),
            ));
        }
        None => None,
    };
    let mut scans = Vec::with_capacity(projects.len());
    for project in projects {
        let id = string_at(project, "id")?;
        let fixture_root = string_at(project, "fixture_root")?;
        if !ids.insert(id.to_owned()) {
            return Err(ScanError::Semantic(format!("duplicate project id {id}")));
        }
        if !roots.insert(fixture_root.to_owned())
            || !normalized_roots.insert(normalized_key(fixture_root))
        {
            return Err(ScanError::Semantic(format!(
                "duplicate or normalized/case-colliding fixture root {fixture_root}"
            )));
        }
        validate_portable_path(fixture_root, true)?;
        validate_project_semantics(project)?;
        let canonical_len = serde_json::to_vec(project)
            .map_err(|error| ScanError::Semantic(error.to_string()))?
            .len();
        let budget = budget(project)?;
        if u64::try_from(canonical_len).unwrap_or(u64::MAX)
            > u64_at(object_at(project, "budget")?, "max_manifest_bytes")?
        {
            return Err(ScanError::ResourceLimit {
                path: id.to_owned(),
                limit: "max_manifest_bytes",
            });
        }
        let availability = string_at(project, "availability")?;
        if availability != "ready" {
            if root.component_exists(fixture_root)? {
                return Err(ScanError::Integrity {
                    path: fixture_root.to_owned(),
                    reason: "fixture root exists for a non-ready project",
                });
            }
            scans.push(ProjectScan {
                id: id.to_owned(),
                availability: availability.to_owned(),
                files: 0,
                bytes: 0,
                state_classes: strings_at(project, "included_state_classes")?,
                canaries: 0,
            });
            continue;
        }
        let inventory = root.inventory(fixture_root, budget)?;
        validate_inventory(project, &inventory)?;
        let canary_count = validate_canaries(
            project,
            &root,
            fixture_root,
            &inventory,
            registry.as_ref(),
            budget.max_single_file_bytes,
        )?;
        scans.push(ProjectScan {
            id: id.to_owned(),
            availability: availability.to_owned(),
            files: u64::try_from(inventory.files.len()).unwrap_or(u64::MAX),
            bytes: inventory.bytes,
            state_classes: strings_at(project, "included_state_classes")?,
            canaries: canary_count,
        });
    }
    Ok(ManifestScan { projects: scans })
}

fn reject_marker_like_values(value: &Value) -> Result<(), ScanError> {
    match value {
        Value::String(value) if value.contains("RCCE_CANARY_V1__") => Err(ScanError::Semantic(
            "marker-like manifest identifier, path, or transform rejected before interpolation"
                .to_owned(),
        )),
        Value::Array(values) => {
            for value in values {
                reject_marker_like_values(value)?;
            }
            Ok(())
        }
        Value::Object(values) => {
            for (key, value) in values {
                if key.contains("RCCE_CANARY_V1__") {
                    return Err(ScanError::Semantic(
                        "marker-like manifest field rejected before interpolation".to_owned(),
                    ));
                }
                reject_marker_like_values(value)?;
            }
            Ok(())
        }
        _ => Ok(()),
    }
}

fn validate_project_semantics(project: &Value) -> Result<(), ScanError> {
    let id = string_at(project, "id")?;
    let files = array_at(project, "files")?;
    let transforms = strings_at(object_at(project, "sanitization")?, "transform_ids")?
        .into_iter()
        .collect::<BTreeSet<_>>();
    let mut file_ids = BTreeSet::new();
    let mut paths = BTreeSet::new();
    let mut normalized_paths = BTreeSet::new();
    let mut union = BTreeSet::new();
    let mut sizes = BTreeMap::new();
    for file in files {
        let file_id = string_at(file, "id")?;
        let path = string_at(file, "path")?;
        validate_portable_path(path, false)?;
        if !file_ids.insert(file_id.to_owned()) {
            return Err(ScanError::Semantic(format!(
                "{id}: duplicate file id {file_id}"
            )));
        }
        if !paths.insert(path.to_owned()) || !normalized_paths.insert(normalized_key(path)) {
            return Err(ScanError::Semantic(format!(
                "{id}: duplicate or normalized/case-colliding file path {path}"
            )));
        }
        let primary = string_at(file, "primary_state_class")?;
        union.insert(primary.to_owned());
        let additional = strings_at(file, "additional_state_classes")?;
        if additional.iter().any(|class| class == primary)
            || !additional.windows(2).all(|pair| pair[0] < pair[1])
        {
            return Err(ScanError::Semantic(format!(
                "{id}/{file_id}: additional classes must be unique, lexical, and exclude primary"
            )));
        }
        union.extend(additional);
        for transform in strings_at(file, "transform_ids")? {
            if !transforms.contains(&transform) {
                return Err(ScanError::Semantic(format!(
                    "{id}/{file_id}: unknown transform {transform}"
                )));
            }
        }
        sizes.insert(file_id.to_owned(), u64_at(file, "size_bytes")?);
    }
    let included = strings_at(project, "included_state_classes")?;
    if !in_state_order(&included) || included.iter().cloned().collect::<BTreeSet<_>>() != union {
        return Err(ScanError::Semantic(format!(
            "{id}: included state classes are not the canonical file-class union"
        )));
    }
    let excluded = strings_at(project, "excluded_state_classes")?;
    if !in_state_order(&excluded) || excluded.iter().any(|class| union.contains(class)) {
        return Err(ScanError::Semantic(format!(
            "{id}: excluded state classes are not canonical or overlap included classes"
        )));
    }

    let mut artifact_ids = BTreeSet::new();
    for artifact in array_at(project, "artifacts")? {
        let artifact_id = string_at(artifact, "id")?;
        if !artifact_ids.insert(artifact_id.to_owned()) {
            return Err(ScanError::Semantic(format!(
                "{id}: duplicate artifact id {artifact_id}"
            )));
        }
        let file_id = string_at(artifact, "file_id")?;
        let file_size = *sizes.get(file_id).ok_or_else(|| {
            ScanError::Semantic(format!("{id}/{artifact_id}: unknown file {file_id}"))
        })?;
        let mut prior_end = 0_u64;
        for range in array_at(artifact, "byte_ranges")? {
            let start = u64_at(range, "start")?;
            let end = u64_at(range, "end_exclusive")?;
            if start > end
                || end > file_size
                || start < prior_end
                || (start == end && start != file_size)
            {
                return Err(ScanError::Semantic(format!(
                    "{id}/{artifact_id}: invalid or overlapping byte range"
                )));
            }
            if string_at(artifact, "kind")? == "non-utf8-content" && start == end {
                return Err(ScanError::Semantic(format!(
                    "{id}/{artifact_id}: non-UTF-8 range cannot be empty"
                )));
            }
            prior_end = end;
        }
    }
    let mut canary_ids = BTreeSet::new();
    for canary in array_at(project, "canaries")? {
        let canary_id = string_at(canary, "id")?;
        if !canary_ids.insert(canary_id.to_owned()) {
            return Err(ScanError::Semantic(format!(
                "{id}: duplicate canary id {canary_id}"
            )));
        }
        let file_id = string_at(canary, "file_id")?;
        if !file_ids.contains(file_id) {
            return Err(ScanError::Semantic(format!(
                "{id}/{canary_id}: unknown file {file_id}"
            )));
        }
        let min = u64_at(canary, "expected_min_occurrences")?;
        let max = u64_at(canary, "expected_max_occurrences")?;
        if min > max {
            return Err(ScanError::Semantic(format!(
                "{id}/{canary_id}: occurrence minimum exceeds maximum"
            )));
        }
    }
    Ok(())
}

fn validate_inventory(project: &Value, inventory: &Inventory) -> Result<(), ScanError> {
    let id = string_at(project, "id")?;
    let mut declared = BTreeMap::new();
    for file in array_at(project, "files")? {
        let path = string_at(file, "path")?.to_owned();
        let digest = decode_sha256(string_at(file, "sha256")?)?;
        declared.insert(path, (u64_at(file, "size_bytes")?, digest));
    }
    if declared.len() != inventory.files.len() {
        return Err(ScanError::Integrity {
            path: id.to_owned(),
            reason: "manifest/filesystem membership count differs",
        });
    }
    for file in &inventory.files {
        let Some((size, digest)) = declared.remove(&file.path) else {
            return Err(ScanError::Integrity {
                path: file.path.clone(),
                reason: "filesystem file is undeclared",
            });
        };
        if size != file.size {
            return Err(ScanError::Integrity {
                path: file.path.clone(),
                reason: "declared size differs",
            });
        }
        if digest != file.sha256 {
            return Err(ScanError::Integrity {
                path: file.path.clone(),
                reason: "SHA-256 differs",
            });
        }
    }
    if !declared.is_empty() {
        return Err(ScanError::Integrity {
            path: id.to_owned(),
            reason: "declared file is absent",
        });
    }
    let hashes = object_at(project, "hashes")?;
    if u64_at(hashes, "materialized_bytes")? != inventory.bytes
        || u64_at(hashes, "materialized_files")?
            != u64::try_from(inventory.files.len()).unwrap_or(u64::MAX)
        || decode_sha256(string_at(hashes, "tree_sha256")?)? != tree_hash(inventory)
    {
        return Err(ScanError::Integrity {
            path: id.to_owned(),
            reason: "aggregate counts or tree digest differ",
        });
    }
    Ok(())
}

fn validate_canaries(
    project: &Value,
    root: &Root,
    fixture: &str,
    inventory: &Inventory,
    registry: Option<&crate::registry::Registry>,
    max_file_bytes: u64,
) -> Result<u64, ScanError> {
    let canaries = array_at(project, "canaries")?;
    let files_by_id = array_at(project, "files")?
        .iter()
        .map(|file| Ok((string_at(file, "id")?.to_owned(), file)))
        .collect::<Result<BTreeMap<_, _>, ScanError>>()?;
    let inventory_by_path = inventory
        .files
        .iter()
        .map(|file| (file.path.as_str(), file))
        .collect::<BTreeMap<_, _>>();

    let known = registry.map_or_else(Vec::new, crate::registry::Registry::markers);
    let known_by_id = known
        .iter()
        .map(|marker| (marker.id.as_str(), marker))
        .collect::<BTreeMap<_, _>>();
    let mut expected = BTreeMap::<String, (String, String, u64)>::new();
    let registry_identity = if canaries.is_empty() {
        None
    } else {
        let registry = registry
            .ok_or_else(|| ScanError::Semantic("canary registry was not loaded".to_owned()))?;
        let registry_ref = object_at(project, "canary_registry")?;
        let id = string_at(registry_ref, "id")?;
        registry.require_identity(id, u64_at(registry_ref, "version")?)?;
        Some((registry, id))
    };

    for canary in canaries {
        let id = string_at(canary, "id")?;
        let (registry, registry_id) =
            registry_identity.expect("non-empty canary list loads registry");
        if string_at(canary, "registry_id")? != registry_id {
            return Err(ScanError::Canary {
                id: id.to_owned(),
                reason: "registry id disagrees with project registry reference",
            });
        }
        let file = files_by_id
            .get(string_at(canary, "file_id")?)
            .ok_or_else(|| ScanError::Canary {
                id: id.to_owned(),
                reason: "file reference is absent",
            })?;
        if string_at(file, "sensitivity")? != "synthetic-canary" {
            return Err(ScanError::Canary {
                id: id.to_owned(),
                reason: "owning file is not classified synthetic-canary",
            });
        }
        let class = string_at(canary, "state_class")?;
        let primary = string_at(file, "primary_state_class")?;
        let additional = strings_at(file, "additional_state_classes")?;
        if class != primary && !additional.iter().any(|candidate| candidate == class) {
            return Err(ScanError::Canary {
                id: id.to_owned(),
                reason: "state class is absent from owning file",
            });
        }
        let path = string_at(file, "path")?;
        let (marker, registry_occurrences) = registry.marker(
            id,
            string_at(canary, "permission_policy_id")?,
            class,
            string_at(project, "id")?,
            path,
        )?;
        if u64_at(canary, "expected_min_occurrences")? != registry_occurrences
            || u64_at(canary, "expected_max_occurrences")? != registry_occurrences
        {
            return Err(ScanError::Canary {
                id: id.to_owned(),
                reason: "manifest occurrence bounds disagree with registry",
            });
        }
        if !known_by_id.contains_key(id) {
            return Err(ScanError::Canary {
                id: id.to_owned(),
                reason: "declared marker is absent from closed registry",
            });
        }
        expected.insert(
            id.to_owned(),
            (marker, path.to_owned(), registry_occurrences),
        );
    }

    let mut observed = BTreeMap::<String, BTreeMap<String, u64>>::new();
    for file in &inventory.files {
        for component in file.path.split('/') {
            scan_marker_surface(
                component.as_bytes(),
                &format!("path:{}", file.path),
                &known,
                &mut observed,
            )?;
        }
        let body = root.read_fixture_file(fixture, &file.path, max_file_bytes)?;
        let inventory_file = inventory_by_path
            .get(file.path.as_str())
            .expect("inventory map is built from this exact inventory");
        if Sha256::digest(&body).as_slice() != inventory_file.sha256 {
            return Err(ScanError::Integrity {
                path: file.path.clone(),
                reason: "file changed between inventory and global canary closure scan",
            });
        }
        scan_marker_surface(&body, &format!("body:{}", file.path), &known, &mut observed)?;
    }

    for (id, locations) in &observed {
        let Some((_, expected_path, expected_count)) = expected.get(id) else {
            return Err(ScanError::Canary {
                id: id.clone(),
                reason: "registered marker occurs without a manifest declaration",
            });
        };
        let owning_location = format!("body:{expected_path}");
        let total = locations
            .values()
            .try_fold(0_u64, |sum, value| sum.checked_add(*value))
            .ok_or_else(|| ScanError::Canary {
                id: id.clone(),
                reason: "global occurrence counter overflow",
            })?;
        if total != *expected_count
            || locations.get(&owning_location).copied().unwrap_or(0) != *expected_count
            || locations.len() != 1
        {
            return Err(ScanError::Canary {
                id: id.clone(),
                reason: "global marker placement/count differs from the one declared owning body",
            });
        }
    }
    for id in expected.keys() {
        if !observed.contains_key(id) {
            return Err(ScanError::Canary {
                id: id.clone(),
                reason: "declared marker is globally absent",
            });
        }
    }
    Ok(u64::try_from(canaries.len()).unwrap_or(u64::MAX))
}

fn scan_marker_surface(
    bytes: &[u8],
    location: &str,
    known: &[crate::registry::KnownMarker],
    observed: &mut BTreeMap<String, BTreeMap<String, u64>>,
) -> Result<(), ScanError> {
    const PREFIX: &[u8] = b"RCCE_CANARY_V1__";
    let mut offset = 0;
    while offset + PREFIX.len() <= bytes.len() {
        let Some(relative) = bytes[offset..]
            .windows(PREFIX.len())
            .position(|window| window == PREFIX)
        else {
            break;
        };
        let start = offset + relative;
        let Some(marker) = known
            .iter()
            .find(|candidate| bytes[start..].starts_with(candidate.marker.as_bytes()))
        else {
            return Err(ScanError::Canary {
                id: "undeclared-marker".to_owned(),
                reason: "unregistered canary-like marker prefix occurs in a path or body",
            });
        };
        let by_location = observed.entry(marker.id.clone()).or_default();
        let count = by_location.entry(location.to_owned()).or_default();
        *count = count.checked_add(1).ok_or_else(|| ScanError::Canary {
            id: marker.id.clone(),
            reason: "global occurrence counter overflow",
        })?;
        offset = start + marker.marker.len();
    }
    Ok(())
}

fn tree_hash(inventory: &Inventory) -> [u8; 32] {
    let mut hasher = Sha256::new();
    hasher.update(b"RCCE-CORPUS-TREE-V1\0");
    for file in &inventory.files {
        hasher.update(
            u64::try_from(file.path.len())
                .unwrap_or(u64::MAX)
                .to_le_bytes(),
        );
        hasher.update(file.path.as_bytes());
        hasher.update(file.size.to_le_bytes());
        hasher.update(file.sha256);
    }
    hasher.finalize().into()
}

fn budget(project: &Value) -> Result<Budget, ScanError> {
    let value = object_at(project, "budget")?;
    Ok(Budget {
        max_bytes: u64_at(value, "max_bytes")?,
        max_files: u64_at(value, "max_files")?,
        max_single_file_bytes: u64_at(value, "max_single_file_bytes")?,
        max_directories: u64_at(value, "max_directories")?,
        max_depth: u64_at(value, "max_depth")?,
        max_path_bytes: u64_at(value, "max_path_bytes")?,
        max_component_bytes: u64_at(value, "max_component_bytes")?,
    })
}

fn validate_portable_path(path: &str, single_component: bool) -> Result<(), ScanError> {
    if path.is_empty()
        || path.starts_with('/')
        || path.contains('\\')
        || path.contains(':')
        || path.bytes().any(|byte| byte < 0x20 || byte == 0x7f)
    {
        return Err(ScanError::Semantic(format!("unsafe portable path {path}")));
    }
    let components = path.split('/').collect::<Vec<_>>();
    if (single_component && components.len() != 1)
        || components.iter().any(|component| {
            component.is_empty()
                || *component == "."
                || *component == ".."
                || component.ends_with('.')
                || component.ends_with(' ')
                || reserved(component)
        })
    {
        return Err(ScanError::Semantic(format!("unsafe portable path {path}")));
    }
    Ok(())
}

fn reserved(component: &str) -> bool {
    let base = component
        .split('.')
        .next()
        .unwrap_or(component)
        .to_ascii_uppercase();
    matches!(base.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || (base.len() == 4
            && (base.starts_with("COM") || base.starts_with("LPT"))
            && matches!(base.as_bytes()[3], b'1'..=b'9'))
}

fn normalized_key(path: &str) -> String {
    path.nfc().flat_map(char::to_lowercase).collect()
}

fn in_state_order(classes: &[String]) -> bool {
    let indexes = classes
        .iter()
        .map(|class| {
            STATE_CLASS_ORDER
                .iter()
                .position(|candidate| candidate == class)
        })
        .collect::<Option<Vec<_>>>();
    indexes.is_some_and(|indexes| indexes.windows(2).all(|pair| pair[0] < pair[1]))
}

fn decode_sha256(value: &str) -> Result<[u8; 32], ScanError> {
    let bytes = hex::decode(value)
        .map_err(|_| ScanError::Semantic("invalid SHA-256 encoding".to_owned()))?;
    bytes
        .try_into()
        .map_err(|_| ScanError::Semantic("invalid SHA-256 length".to_owned()))
}

fn object_at<'a>(value: &'a Value, key: &str) -> Result<&'a Value, ScanError> {
    value
        .get(key)
        .filter(|item| item.is_object())
        .ok_or_else(|| ScanError::Semantic(format!("missing object {key}")))
}

fn array_at<'a>(value: &'a Value, key: &str) -> Result<&'a [Value], ScanError> {
    value
        .get(key)
        .and_then(Value::as_array)
        .map(Vec::as_slice)
        .ok_or_else(|| ScanError::Semantic(format!("missing array {key}")))
}

fn string_at<'a>(value: &'a Value, key: &str) -> Result<&'a str, ScanError> {
    value
        .get(key)
        .and_then(Value::as_str)
        .ok_or_else(|| ScanError::Semantic(format!("missing string {key}")))
}

fn u64_at(value: &Value, key: &str) -> Result<u64, ScanError> {
    value
        .get(key)
        .and_then(Value::as_u64)
        .ok_or_else(|| ScanError::Semantic(format!("missing unsigned integer {key}")))
}

fn strings_at(value: &Value, key: &str) -> Result<Vec<String>, ScanError> {
    array_at(value, key)?
        .iter()
        .map(|item| {
            item.as_str()
                .map(str::to_owned)
                .ok_or_else(|| ScanError::Semantic(format!("{key} contains a non-string")))
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    use std::fs;

    const MANIFEST: &str = include_str!("../../../test-data/projects/manifest.toml");
    const SCHEMA: &str = include_str!("../../../test-data/projects/schema-v1.json");
    const P07_REGISTRY: &str = include_str!("../../../test-data/canaries/registry-v1.toml");
    const P07_SECRET_FIXTURE: &[u8] =
        include_bytes!("../../../test-data/canaries/fixtures/secret-credential-sentinel-v1.txt");
    const P07_DYNAMIC_FIXTURE: &[u8] = include_bytes!(
        "../../../test-data/canaries/fixtures/dynamic-private-runtime-sentinel-v1.txt"
    );
    const P07_VALIDATOR: &[u8] = include_bytes!("../../../test-data/canaries/validate_registry.py");

    fn tree_digest(path: &str, body: &[u8]) -> String {
        let file_hash: [u8; 32] = Sha256::digest(body).into();
        let mut hasher = Sha256::new();
        hasher.update(b"RCCE-CORPUS-TREE-V1\0");
        hasher.update(u64::try_from(path.len()).unwrap().to_le_bytes());
        hasher.update(path.as_bytes());
        hasher.update(u64::try_from(body.len()).unwrap().to_le_bytes());
        hasher.update(file_hash);
        hex::encode(hasher.finalize())
    }

    fn ready_manifest(root: &Path, body: &[u8], path: &str) -> Value {
        let toml_value: toml::Value = toml::from_str(MANIFEST).unwrap();
        let mut manifest = serde_json::to_value(toml_value).unwrap();
        let project = &mut manifest["projects"][0];
        project["availability"] = json!("ready");
        project["expected_compatibility"] = json!([{
            "selector": "PF-CFG-001", "level": "I0 Inventory", "evidence": "generated hostile scanner fixture"
        }]);
        project["included_state_classes"] = json!(["AuthoringSource"]);
        project["excluded_state_classes"] = json!(["Secret", "DynamicPrivate"]);
        project["files"] = json!([{
            "id": "sample-file", "path": path, "size_bytes": body.len(),
            "sha256": hex::encode(Sha256::digest(body)),
            "primary_state_class": "AuthoringSource", "additional_state_classes": [],
            "sensitivity": "reviewed-clear", "expected_level": "I0 Inventory",
            "format_rows": ["PF-CFG-001"], "transform_ids": []
        }]);
        project["provenance"] = json!({
            "status": "verified", "source_kind": "synthetic", "source_locator": "generated-test",
            "source_revision": "test-v1", "source_bytes": body.len(), "source_files": 1,
            "evidence": ["unit test"], "reviewed_by": "independent-test", "reviewed_at_utc": "2026-07-20T00:00:00Z"
        });
        project["license"] = json!({
            "status": "approved", "spdx": "CC0-1.0", "redistribution": "repository-test-only",
            "scope": "generated test bytes", "evidence": ["unit test"], "reviewed_by": "independent-test",
            "reviewed_at_utc": "2026-07-20T00:00:00Z"
        });
        project["consent"] = json!({
            "status": "approved", "scope": "generated test bytes", "history_scope": "permanent-public-vcs",
            "revocable": false, "subject_id": "synthetic-test", "evidence": ["unit test"],
            "approved_by": "test-author", "approved_at_utc": "2026-07-20T00:00:00Z"
        });
        project["sensitivity_review"] = json!({
            "status": "approved", "method": "synthetic construction", "evidence": ["unit test"],
            "reviewed_by": "independent-test", "reviewed_at_utc": "2026-07-20T00:00:00Z"
        });
        project["sanitization"] = json!({
            "status": "approved", "sanitizer_id": "synthetic-test", "sanitizer_version": "1",
            "transform_ids": [], "evidence": ["unit test"], "reviewed_by": "independent-test",
            "reviewed_at_utc": "2026-07-20T00:00:00Z"
        });
        project["hashes"] = json!({
            "status": "verified", "algorithm": "sha256", "tree_sha256": tree_digest(path, body),
            "materialized_bytes": body.len(), "materialized_files": 1
        });
        fs::create_dir_all(
            root.join("small-v1")
                .join(Path::new(path).parent().unwrap_or_else(|| Path::new(""))),
        )
        .unwrap();
        fs::write(root.join("small-v1").join(path), body).unwrap();
        manifest
    }

    fn write_corpus(root: &Path, manifest: &Value) -> std::path::PathBuf {
        fs::write(root.join("schema-v1.json"), SCHEMA).unwrap();
        let toml = toml::to_string(manifest).unwrap();
        fs::write(root.join("manifest.toml"), toml).unwrap();
        root.join("manifest.toml")
    }

    #[cfg(target_os = "linux")]
    #[test]
    fn strict_session_retains_one_root_across_filesystem_replacement() {
        use std::os::unix::fs::symlink;

        let shared_memory = Path::new("/dev/shm");
        if !shared_memory.is_dir() {
            eprintln!("SKIP: /dev/shm is unavailable for the replacement filesystem");
            return;
        }
        let parent = tempfile::tempdir().unwrap();
        let selected_root = parent.path().join("selected");
        let held_root = parent.path().join("held-open-root");
        fs::create_dir(&selected_root).unwrap();
        fs::write(selected_root.join("manifest.toml"), MANIFEST).unwrap();
        fs::write(selected_root.join("schema-v1.json"), SCHEMA).unwrap();
        let manifest = selected_root.join("manifest.toml");
        if crate::platform_capability(&manifest)
            .unwrap()
            .contains("hardlink-transient=unavailable")
        {
            eprintln!("SKIP: selected root lacks transient-race authority");
            return;
        }

        let replacement = tempfile::Builder::new()
            .prefix("rcce-unproved-replacement-")
            .tempdir_in(shared_memory)
            .unwrap();
        fs::write(
            replacement.path().join("manifest.toml"),
            b"replacement filesystem body must never be scanned",
        )
        .unwrap();
        fs::write(replacement.path().join("schema-v1.json"), b"{}").unwrap();
        assert!(
            crate::platform_capability(&replacement.path().join("manifest.toml"))
                .unwrap()
                .contains("hardlink-transient=unavailable")
        );

        let report = scan_with_assurance_and_before_content(
            &manifest,
            None,
            ReadAssurance::TransientRaceDetection,
            || {
                fs::rename(&selected_root, &held_root).unwrap();
                symlink(replacement.path(), &selected_root).unwrap();
            },
        )
        .unwrap();
        assert_eq!(report.projects.len(), 3);
        assert!(report.projects.iter().all(|project| project.files == 0));
    }

    #[test]
    fn validates_ready_membership_hash_and_tree_digest() {
        let temp = tempfile::tempdir().unwrap();
        let manifest = ready_manifest(temp.path(), b"exact legacy bytes", "Data/Game.dat");
        let report = scan(&write_corpus(temp.path(), &manifest), None).unwrap();
        assert_eq!(report.projects[0].files, 1);
        assert_eq!(report.projects[0].bytes, 18);
    }

    #[test]
    fn rejects_hash_and_membership_failures() {
        let temp = tempfile::tempdir().unwrap();
        let mut manifest = ready_manifest(temp.path(), b"bytes", "sample.dat");
        manifest["projects"][0]["files"][0]["sha256"] = json!("00".repeat(32));
        let error = scan(&write_corpus(temp.path(), &manifest), None).unwrap_err();
        assert!(error.to_string().contains("SHA-256 differs"));

        let temp = tempfile::tempdir().unwrap();
        let manifest = ready_manifest(temp.path(), b"bytes", "sample.dat");
        fs::write(temp.path().join("small-v1/undeclared.dat"), b"extra").unwrap();
        let error = scan(&write_corpus(temp.path(), &manifest), None).unwrap_err();
        assert!(error.to_string().contains("membership count differs"));
    }

    #[test]
    fn rejects_missing_required_field_and_traversal_path() {
        let temp = tempfile::tempdir().unwrap();
        let mut manifest = ready_manifest(temp.path(), b"bytes", "sample.dat");
        manifest.as_object_mut().unwrap().remove("manifest_id");
        let error = scan(&write_corpus(temp.path(), &manifest), None).unwrap_err();
        assert!(matches!(error, ScanError::SchemaValidation(_)));

        let temp = tempfile::tempdir().unwrap();
        let mut manifest = ready_manifest(temp.path(), b"bytes", "sample.dat");
        manifest["projects"][0]["files"][0]["path"] = json!("../outside");
        let error = scan(&write_corpus(temp.path(), &manifest), None).unwrap_err();
        assert!(error.to_string().contains("unsafe portable path"));
    }

    #[test]
    fn rejects_altered_sibling_schema_before_compilation() {
        let temp = tempfile::tempdir().unwrap();
        let manifest = ready_manifest(temp.path(), b"bytes", "sample.dat");
        let manifest_path = write_corpus(temp.path(), &manifest);
        fs::write(temp.path().join("schema-v1.json"), b"{}\n").unwrap();
        let error = scan(&manifest_path, None).unwrap_err();
        assert!(error.to_string().contains("binary-pinned accepted schema"));
    }

    #[test]
    fn redacts_marker_like_values_from_early_semantic_resource_and_io_failures() {
        let marker = format!("RCCE_CANARY_V1__SECRET__x__y__{}", "a".repeat(64));

        for field in ["fixture_root", "transform"] {
            let temp = tempfile::tempdir().unwrap();
            let mut manifest = ready_manifest(temp.path(), b"bytes", "sample.dat");
            if field == "fixture_root" {
                manifest["projects"][0]["fixture_root"] = json!(marker.clone());
            } else {
                manifest["projects"][0]["sanitization"]["transform_ids"] = json!([marker.clone()]);
            }
            let error = scan(&write_corpus(temp.path(), &manifest), None).unwrap_err();
            let message = error.to_string();
            assert!(message.contains("marker-like manifest"));
            assert!(!message.contains(&marker));
        }

        let temp = tempfile::tempdir().unwrap();
        let manifest = ready_manifest(temp.path(), b"bytes", "sample.dat");
        fs::write(
            temp.path().join("small-v1").join(&marker),
            vec![0_u8; 2 * 1024 * 1024 + 1],
        )
        .unwrap();
        let error = scan(&write_corpus(temp.path(), &manifest), None).unwrap_err();
        let message = error.to_string();
        assert!(message.contains("resource ceiling"));
        assert!(!message.contains(&marker));

        let missing = temp.path().join(&marker);
        let error = scan(&missing, None).unwrap_err();
        let message = error.to_string();
        assert!(message.contains("filesystem operation"));
        assert!(!message.contains(&marker));
    }

    #[test]
    fn non_ready_fixture_directory_fails_closed() {
        let temp = tempfile::tempdir().unwrap();
        fs::write(temp.path().join("schema-v1.json"), SCHEMA).unwrap();
        fs::create_dir(temp.path().join("small-v1")).unwrap();
        let value: toml::Value = toml::from_str(MANIFEST).unwrap();
        fs::write(
            temp.path().join("manifest.toml"),
            toml::to_string(&value).unwrap(),
        )
        .unwrap();
        let error = scan(&temp.path().join("manifest.toml"), None).unwrap_err();
        assert!(error.to_string().contains("non-ready project"));
    }

    fn p07_marker(fixture_id: &str, canary_id: &str, class: &str, role: &str) -> String {
        let fields = [
            "RCCE-P07-CANARY-V1",
            "rcce-p07-canaries",
            "1",
            fixture_id,
            canary_id,
            class,
            role,
        ];
        let mut hasher = Sha256::new();
        for (index, field) in fields.iter().enumerate() {
            if index != 0 {
                hasher.update([0]);
            }
            hasher.update(field.as_bytes());
        }
        format!(
            "RCCE_CANARY_V1__SECRET__{fixture_id}__{canary_id}__{}",
            hex::encode(hasher.finalize())
        )
    }

    fn registry_toml(path: &Path) -> std::path::PathBuf {
        let path = path.join("p07-registry");
        fs::create_dir(&path).unwrap();
        fs::create_dir(path.join("fixtures")).unwrap();
        fs::write(path.join("registry-v1.toml"), P07_REGISTRY).unwrap();
        fs::write(path.join("validate_registry.py"), P07_VALIDATOR).unwrap();
        fs::write(
            path.join("fixtures/secret-credential-sentinel-v1.txt"),
            P07_SECRET_FIXTURE,
        )
        .unwrap();
        fs::write(
            path.join("fixtures/dynamic-private-runtime-sentinel-v1.txt"),
            P07_DYNAMIC_FIXTURE,
        )
        .unwrap();
        path.join("registry-v1.toml")
    }

    fn add_file(manifest: &mut Value, root: &Path, id: &str, path: &str, body: &[u8]) {
        let project = &mut manifest["projects"][0];
        project["files"].as_array_mut().unwrap().push(json!({
            "id": id, "path": path, "size_bytes": body.len(),
            "sha256": hex::encode(Sha256::digest(body)),
            "primary_state_class": "Secret", "additional_state_classes": [],
            "sensitivity": "synthetic-canary", "expected_level": "I0 Inventory",
            "format_rows": ["PF-CFG-001"], "transform_ids": []
        }));
        let target = root.join("small-v1").join(path);
        fs::create_dir_all(target.parent().unwrap()).unwrap();
        fs::write(target, body).unwrap();
        refresh_hashes(manifest, root);
    }

    fn refresh_hashes(manifest: &mut Value, root: &Path) {
        let project = &mut manifest["projects"][0];
        let mut inventory_files = Vec::new();
        for file in project["files"].as_array_mut().unwrap() {
            let path = file["path"].as_str().unwrap().to_owned();
            let body = fs::read(root.join("small-v1").join(&path)).unwrap();
            let sha256: [u8; 32] = Sha256::digest(&body).into();
            file["size_bytes"] = json!(body.len());
            file["sha256"] = json!(hex::encode(sha256));
            inventory_files.push(crate::fs::InventoryFile {
                path,
                size: body.len() as u64,
                sha256,
            });
        }
        inventory_files.sort_by(|a, b| a.path.as_bytes().cmp(b.path.as_bytes()));
        let bytes = inventory_files.iter().map(|file| file.size).sum();
        let inventory = crate::fs::Inventory {
            files: inventory_files,
            directories: 0,
            bytes,
        };
        project["hashes"] = json!({
            "status": "verified", "algorithm": "sha256",
            "tree_sha256": hex::encode(tree_hash(&inventory)),
            "materialized_bytes": bytes, "materialized_files": inventory.files.len()
        });
    }

    fn canary_manifest(root: &Path, occurrences: usize) -> Value {
        let marker = p07_marker(
            "p07-policy-seed-v1",
            "p07-secret-credential-sentinel-v1",
            "Secret",
            "credential-sentinel",
        );
        let body = marker.repeat(occurrences);
        let mut manifest = ready_manifest(
            root,
            body.as_bytes(),
            "fixtures/secret-credential-sentinel-v1.txt",
        );
        let project = &mut manifest["projects"][0];
        project["id"] = json!("p07-policy-seed-v1");
        project["included_state_classes"] = json!(["Secret"]);
        project["excluded_state_classes"] = json!(["DynamicPrivate"]);
        project["files"][0]["primary_state_class"] = json!("Secret");
        project["files"][0]["sensitivity"] = json!("synthetic-canary");
        project["canary_registry"] = json!({
            "status": "verified", "id": "rcce-p07-canaries", "version": 1,
            "evidence": ["P07 generated registry contract"]
        });
        project["canaries"] = json!([{
            "id": "p07-secret-credential-sentinel-v1", "registry_id": "rcce-p07-canaries",
            "permission_policy_id": "secret-never-project", "file_id": "sample-file",
            "state_class": "Secret", "expected_min_occurrences": 1,
            "expected_max_occurrences": 1
        }]);
        manifest
    }

    #[test]
    fn validates_p07_literal_canary_and_requires_explicit_registry() {
        let temp = tempfile::tempdir().unwrap();
        let manifest = canary_manifest(temp.path(), 1);
        let manifest_path = write_corpus(temp.path(), &manifest);
        let missing = scan(&manifest_path, None).unwrap_err();
        assert!(missing
            .to_string()
            .contains("requires explicit --canary-registry"));
        let registry = registry_toml(temp.path());
        let report = scan(&manifest_path, Some(&registry)).unwrap();
        assert_eq!(report.projects[0].canaries, 1);
    }

    #[test]
    fn rejects_unexpected_canary_occurrence_without_echoing_marker() {
        let temp = tempfile::tempdir().unwrap();
        let manifest = canary_manifest(temp.path(), 2);
        let manifest_path = write_corpus(temp.path(), &manifest);
        let registry = registry_toml(temp.path());
        let error = scan(&manifest_path, Some(&registry)).unwrap_err();
        let message = error.to_string();
        assert!(message.contains("global marker placement/count"));
        assert!(!message.contains("RCCE_CANARY_V1__"));
    }

    #[test]
    fn rejects_registered_marker_duplicated_into_another_file_body() {
        let temp = tempfile::tempdir().unwrap();
        let mut manifest = canary_manifest(temp.path(), 1);
        let marker = fs::read(
            temp.path()
                .join("small-v1/fixtures/secret-credential-sentinel-v1.txt"),
        )
        .unwrap();
        add_file(
            &mut manifest,
            temp.path(),
            "duplicate-file",
            "Data/Duplicate.dat",
            &marker,
        );
        let manifest_path = write_corpus(temp.path(), &manifest);
        let registry = registry_toml(temp.path());
        let message = scan(&manifest_path, Some(&registry))
            .unwrap_err()
            .to_string();
        assert!(message.contains("global marker placement/count"));
        assert!(!message.contains("RCCE_CANARY_V1__"));
    }

    #[test]
    fn rejects_registered_marker_embedded_in_any_path_component() {
        let temp = tempfile::tempdir().unwrap();
        let mut manifest = canary_manifest(temp.path(), 1);
        let marker = String::from_utf8(
            fs::read(
                temp.path()
                    .join("small-v1/fixtures/secret-credential-sentinel-v1.txt"),
            )
            .unwrap(),
        )
        .unwrap();
        add_file(
            &mut manifest,
            temp.path(),
            "path-duplicate",
            &format!("Data/{marker}"),
            b"clear",
        );
        let manifest_path = write_corpus(temp.path(), &manifest);
        let registry = registry_toml(temp.path());
        let message = scan(&manifest_path, Some(&registry))
            .unwrap_err()
            .to_string();
        assert!(message.contains("marker-like manifest"));
        assert!(!message.contains("RCCE_CANARY_V1__"));
    }

    #[test]
    fn rejects_unregistered_marker_prefix_even_without_declarations() {
        let temp = tempfile::tempdir().unwrap();
        let body = b"RCCE_CANARY_V1__UNREGISTERED__never-echo";
        let manifest = ready_manifest(temp.path(), body, "sample.dat");
        let message = scan(&write_corpus(temp.path(), &manifest), None)
            .unwrap_err()
            .to_string();
        assert!(message.contains("unregistered canary-like marker"));
        assert!(!message.contains("never-echo"));
    }
}
