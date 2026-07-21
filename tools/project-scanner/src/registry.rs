use crate::error::ScanError;
use serde::Deserialize;
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

const STATE_CLASSES: [&str; 7] = [
    "PublicClient",
    "ServerConfig",
    "Secret",
    "DynamicPrivate",
    "EditorMetadata",
    "AuthoringSource",
    "Unknown",
];
const OPERATIONS: [&str; 8] = [
    "clone",
    "backup",
    "client-package",
    "server-package",
    "diagnostics",
    "migration",
    "playtest-snapshot",
    "publication",
];

type ExpectedCell = (&'static str, Option<&'static str>);
const EXPECTED_OPERATION_TABLE: [[ExpectedCell; 7]; 8] = [
    [
        ("include", None),
        ("include", None),
        ("conditional", Some("include-secret")),
        ("conditional", Some("include-dynamic-private")),
        ("conditional", Some("include-editor-metadata")),
        ("include", None),
        ("conditional", Some("preserve-unknown")),
    ],
    [
        ("include", None),
        ("include", None),
        ("conditional", Some("include-secret")),
        ("conditional", Some("include-dynamic-private")),
        ("conditional", Some("include-editor-metadata")),
        ("include", None),
        ("conditional", Some("preserve-unknown")),
    ],
    [
        ("include", None),
        ("deny", None),
        ("deny", None),
        ("deny", None),
        ("deny", None),
        ("deny", None),
        ("deny", None),
    ],
    [
        ("include", None),
        ("include", None),
        ("conditional", Some("include-secret")),
        ("conditional", Some("include-dynamic-private")),
        ("deny", None),
        ("conditional", Some("include-runtime-authoring-source")),
        ("deny", None),
    ],
    [
        ("derive-only", None),
        ("derive-only", None),
        ("deny", None),
        ("derive-only", None),
        ("derive-only", None),
        ("derive-only", None),
        ("deny", None),
    ],
    [
        ("include", None),
        ("include", None),
        ("conditional", Some("include-secret")),
        ("conditional", Some("include-dynamic-private")),
        ("conditional", Some("include-editor-metadata")),
        ("include", None),
        ("conditional", Some("preserve-unknown")),
    ],
    [
        ("include", None),
        ("include", None),
        ("deny", None),
        ("deny", None),
        ("deny", None),
        ("conditional", Some("include-runtime-authoring-source")),
        ("deny", None),
    ],
    [
        ("dispatch", None),
        ("dispatch", None),
        ("dispatch", None),
        ("dispatch", None),
        ("dispatch", None),
        ("dispatch", None),
        ("dispatch", None),
    ],
];

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RawRegistry {
    schema_version: u64,
    registry_id: String,
    registry_version: u64,
    token_domain: String,
    token_algorithm: String,
    state_class_order: Vec<String>,
    operation_order: Vec<String>,
    classification_contract: RawClassificationContract,
    support_files: Vec<RawSupportFile>,
    operations: Vec<RawOperation>,
    permission_policies: Vec<RawPolicy>,
    canaries: Vec<RawCanary>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RawClassificationContract {
    rule_id: String,
    rule_version: u64,
    primary_source: String,
    required_primary_count: u64,
    constraint_mode: String,
    missing_primary: String,
    multiple_primary: String,
    conflicting_primary: String,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RawSupportFile {
    path: String,
    role: String,
    version: u64,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RawOperation {
    id: String,
    description: String,
    rules: BTreeMap<String, RawRule>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RawRule {
    disposition: String,
    option: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RawPolicy {
    id: String,
    state_class: String,
    description: String,
    assertions: Vec<RawAssertion>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RawAssertion {
    operation: String,
    options: Vec<String>,
    expected: String,
}

#[derive(Debug, Deserialize, Clone)]
#[serde(deny_unknown_fields)]
struct RawCanary {
    id: String,
    fixture_id: String,
    fixture_path: String,
    state_class: String,
    role: String,
    permission_policy_id: String,
    expected_occurrences: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct KnownMarker {
    pub id: String,
    pub marker: String,
    pub fixture_id: String,
    pub fixture_path: String,
    pub expected_occurrences: u64,
}

#[derive(Debug)]
pub(crate) struct Registry {
    id: String,
    version: u64,
    domain: String,
    policies: BTreeSet<String>,
    canaries: BTreeMap<String, RawCanary>,
}

impl Registry {
    pub(crate) fn load(path: &Path) -> Result<Self, ScanError> {
        let absolute = if path.is_absolute() {
            path.to_owned()
        } else {
            std::env::current_dir()
                .map_err(|error| ScanError::io("resolve registry working directory", ".", error))?
                .join(path)
        };
        let parent = absolute
            .parent()
            .filter(|path| !path.as_os_str().is_empty())
            .unwrap_or_else(|| Path::new("."));
        let name = absolute
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| {
                ScanError::Semantic("registry path must have one UTF-8 filename".to_owned())
            })?;
        let root = crate::fs::Root::open(parent)?;
        let registry_directory = parent
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| {
                ScanError::Semantic(
                    "registry must be contained by one UTF-8 directory component".to_owned(),
                )
            })?;
        let registry_parent = parent.parent().ok_or_else(|| {
            ScanError::Semantic("registry directory must have a parent".to_owned())
        })?;
        let inventory = crate::fs::Root::open(registry_parent)?.inventory(
            registry_directory,
            crate::fs::Budget {
                max_bytes: 2 * 1024 * 1024,
                max_files: 8,
                max_single_file_bytes: 1024 * 1024,
                max_directories: 2,
                max_depth: 2,
                max_path_bytes: 512,
                max_component_bytes: 255,
            },
        )?;
        let bytes = root.read_component(name, 1024 * 1024)?;
        let text = std::str::from_utf8(&bytes)
            .map_err(|_| ScanError::Semantic("canary registry is not UTF-8".to_owned()))?;
        let raw: RawRegistry = toml::from_str(text).map_err(|_| {
            ScanError::Semantic(
                "canary registry violates closed v1 TOML contract; source bytes redacted"
                    .to_owned(),
            )
        })?;
        let registry = Self::validate(raw, &root, name)?;
        registry.validate_closed_tree(&root, &inventory)?;
        Ok(registry)
    }

    fn validate(
        raw: RawRegistry,
        root: &crate::fs::Root,
        registry_name: &str,
    ) -> Result<Self, ScanError> {
        if raw.schema_version != 1
            || raw.registry_id != "rcce-p07-canaries"
            || raw.registry_version != 1
            || raw.token_domain != "RCCE-P07-CANARY-V1"
            || raw.token_algorithm != "sha256"
            || raw
                .state_class_order
                .iter()
                .map(String::as_str)
                .ne(STATE_CLASSES)
            || raw
                .operation_order
                .iter()
                .map(String::as_str)
                .ne(OPERATIONS)
            || raw.classification_contract.rule_id != "rcce-state-classification"
            || raw.classification_contract.rule_version != 1
            || raw.classification_contract.primary_source != "versioned-inventory-rule"
            || raw.classification_contract.required_primary_count != 1
            || raw.classification_contract.constraint_mode != "additive"
            || raw.classification_contract.missing_primary != "reject"
            || raw.classification_contract.multiple_primary != "reject"
            || raw.classification_contract.conflicting_primary != "reject"
            || registry_name != "registry-v1.toml"
        {
            return Err(ScanError::Semantic(
                "unsupported canary registry v1 identity or ordering".to_owned(),
            ));
        }

        let support_files: Vec<_> = raw
            .support_files
            .iter()
            .map(|file| (file.path.as_str(), file.role.as_str(), file.version))
            .collect();
        if support_files
            != [
                ("registry-v1.toml", "registry", 1),
                ("validate_registry.py", "validator", 1),
            ]
        {
            return Err(ScanError::Semantic(
                "registry support-file closure is invalid".to_owned(),
            ));
        }

        if raw.operations.len() != OPERATIONS.len() {
            return Err(ScanError::Semantic(
                "registry operation set is incomplete".to_owned(),
            ));
        }
        let mut allowed_options = BTreeMap::<String, BTreeSet<String>>::new();
        for (operation_index, (operation, expected_id)) in
            raw.operations.iter().zip(OPERATIONS).enumerate()
        {
            if operation.id != expected_id
                || operation.description.trim().is_empty()
                || operation.description.trim() != operation.description
                || operation.rules.len() != STATE_CLASSES.len()
            {
                return Err(ScanError::Semantic(
                    "registry operation identity, description, or class closure is invalid"
                        .to_owned(),
                ));
            }
            let mut operation_options = BTreeSet::new();
            for (class_index, state_class) in STATE_CLASSES.iter().copied().enumerate() {
                let rule = operation.rules.get(state_class).ok_or_else(|| {
                    ScanError::Semantic("registry operation omits a state class".to_owned())
                })?;
                if (rule.disposition.as_str(), rule.option.as_deref())
                    != EXPECTED_OPERATION_TABLE[operation_index][class_index]
                {
                    return Err(ScanError::Semantic(
                        "registry operation/class disposition or option differs from canonical v1 table"
                            .to_owned(),
                    ));
                }
                match rule.disposition.as_str() {
                    "conditional" => {
                        let option = rule
                            .option
                            .as_deref()
                            .filter(|value| valid_id(value))
                            .ok_or_else(|| {
                                ScanError::Semantic(
                                    "conditional registry rule lacks a valid option".to_owned(),
                                )
                            })?;
                        if !operation_options.insert(option.to_owned()) {
                            return Err(ScanError::Semantic(
                                "registry operation repeats a conditional option".to_owned(),
                            ));
                        }
                    }
                    "include" | "deny" | "derive-only" | "dispatch" if rule.option.is_none() => {}
                    _ => {
                        return Err(ScanError::Semantic(
                            "registry rule has an invalid disposition/option shape".to_owned(),
                        ))
                    }
                }
            }
            if operation.id == "publication"
                && operation
                    .rules
                    .values()
                    .any(|rule| rule.disposition != "dispatch")
            {
                return Err(ScanError::Semantic(
                    "publication registry rules must dispatch".to_owned(),
                ));
            }
            if operation.id != "publication"
                && operation
                    .rules
                    .values()
                    .any(|rule| rule.disposition == "dispatch")
            {
                return Err(ScanError::Semantic(
                    "dispatch is reserved to publication".to_owned(),
                ));
            }
            allowed_options.insert(operation.id.clone(), operation_options);
        }

        let operations = raw
            .operations
            .iter()
            .map(|operation| (operation.id.as_str(), operation))
            .collect::<BTreeMap<_, _>>();
        verify_classification_cases(&raw.operations)?;

        if raw.permission_policies.len() != 2 {
            return Err(ScanError::Semantic(
                "registry must contain exactly two sensitive policies".to_owned(),
            ));
        }
        let mut policies = BTreeSet::new();
        let mut policy_classes = BTreeMap::new();
        for policy in &raw.permission_policies {
            if !valid_id(&policy.id)
                || !matches!(policy.state_class.as_str(), "Secret" | "DynamicPrivate")
                || policy.description.trim().is_empty()
                || !policies.insert(policy.id.clone())
                || policy.assertions.is_empty()
            {
                return Err(ScanError::Semantic(
                    "registry permission policy is invalid or duplicated".to_owned(),
                ));
            }
            let mut assertions = BTreeSet::new();
            for assertion in &policy.assertions {
                let Some(operation) = operations.get(assertion.operation.as_str()) else {
                    return Err(ScanError::Semantic(
                        "registry policy assertion names an unknown operation".to_owned(),
                    ));
                };
                let options = assertion.options.iter().cloned().collect::<BTreeSet<_>>();
                let sorted_unique = assertion.options.windows(2).all(|pair| pair[0] < pair[1])
                    && options.len() == assertion.options.len();
                let allowed = &allowed_options[assertion.operation.as_str()];
                let rule = &operation.rules[policy.state_class.as_str()];
                let expected = if rule.disposition == "include"
                    || (rule.disposition == "conditional"
                        && rule
                            .option
                            .as_ref()
                            .is_some_and(|option| options.contains(option)))
                {
                    "present"
                } else {
                    "absent"
                };
                if !sorted_unique
                    || !options.is_subset(allowed)
                    || assertion.expected != expected
                    || !assertions.insert((assertion.operation.clone(), assertion.options.clone()))
                {
                    return Err(ScanError::Semantic(
                        "registry policy assertion is invalid or duplicated".to_owned(),
                    ));
                }
            }
            let mut expected_assertions = BTreeSet::new();
            for operation in &raw.operations {
                let rule = &operation.rules[policy.state_class.as_str()];
                let default = if rule.disposition == "include" {
                    "present"
                } else {
                    "absent"
                };
                expected_assertions.insert((operation.id.clone(), Vec::new(), default.to_owned()));
                if let Some(option) = &rule.option {
                    expected_assertions.insert((
                        operation.id.clone(),
                        vec![option.clone()],
                        "present".to_owned(),
                    ));
                }
            }
            let actual_assertions: BTreeSet<(String, Vec<String>, String)> = policy
                .assertions
                .iter()
                .map(|assertion| {
                    (
                        assertion.operation.clone(),
                        assertion.options.clone(),
                        assertion.expected.clone(),
                    )
                })
                .collect();
            if actual_assertions != expected_assertions {
                return Err(ScanError::Semantic(
                    "registry policy assertions differ from the canonical v1 rule-derived table"
                        .to_owned(),
                ));
            }
            policy_classes.insert(policy.id.clone(), policy.state_class.clone());
        }
        if policy_classes
            != BTreeMap::from([
                (
                    "dynamic-private-explicit-internal".to_owned(),
                    "DynamicPrivate".to_owned(),
                ),
                ("secret-never-project".to_owned(), "Secret".to_owned()),
            ])
        {
            return Err(ScanError::Semantic(
                "registry policy identities or state classes drifted".to_owned(),
            ));
        }

        if raw.canaries.len() != 2 {
            return Err(ScanError::Semantic(
                "registry must contain exactly two seed canaries".to_owned(),
            ));
        }
        let mut canaries = BTreeMap::new();
        let mut fixture_paths = BTreeSet::new();
        let mut marker_tokens = BTreeSet::new();
        for canary in raw.canaries {
            let id = canary.id.clone();
            if !valid_id(&id)
                || !valid_id(&canary.fixture_id)
                || !valid_id(&canary.role)
                || !valid_id(&canary.permission_policy_id)
                || canary.fixture_path.is_empty()
                || !safe_fixture_path(&canary.fixture_path)
                || canary.expected_occurrences != 1
                || !matches!(canary.state_class.as_str(), "Secret" | "DynamicPrivate")
                || !policies.contains(&canary.permission_policy_id)
                || policy_classes[&canary.permission_policy_id] != canary.state_class
                || !fixture_paths.insert(canary.fixture_path.clone())
                || canaries.insert(id, canary).is_some()
            {
                return Err(ScanError::Semantic(
                    "registry canary is invalid or duplicated".to_owned(),
                ));
            }
        }
        let registry = Self {
            id: raw.registry_id,
            version: raw.registry_version,
            domain: raw.token_domain,
            policies,
            canaries,
        };
        let expected_canaries = BTreeMap::from([
            (
                "p07-dynamic-private-runtime-sentinel-v1",
                (
                    "p07-policy-seed-v1",
                    "fixtures/dynamic-private-runtime-sentinel-v1.txt",
                    "DynamicPrivate",
                    "runtime-private-sentinel",
                    "dynamic-private-explicit-internal",
                ),
            ),
            (
                "p07-secret-credential-sentinel-v1",
                (
                    "p07-policy-seed-v1",
                    "fixtures/secret-credential-sentinel-v1.txt",
                    "Secret",
                    "credential-sentinel",
                    "secret-never-project",
                ),
            ),
        ]);
        for (id, expected) in expected_canaries {
            let canary = registry.canaries.get(id).ok_or_else(|| {
                ScanError::Semantic("registry required seed-canary identity is absent".to_owned())
            })?;
            if (
                canary.fixture_id.as_str(),
                canary.fixture_path.as_str(),
                canary.state_class.as_str(),
                canary.role.as_str(),
                canary.permission_policy_id.as_str(),
            ) != expected
            {
                return Err(ScanError::Semantic(
                    "registry seed-canary identity/class/placement drifted".to_owned(),
                ));
            }
            let marker = registry.derive_marker(canary);
            if !marker_tokens.insert(marker.clone()) {
                return Err(ScanError::Semantic(
                    "registry seed-canary marker collision".to_owned(),
                ));
            }
            let body = root.read_fixture_file(
                "fixtures",
                canary
                    .fixture_path
                    .strip_prefix("fixtures/")
                    .expect("validated prefix"),
                64 * 1024,
            )?;
            if body != registry.expected_fixture(canary, &marker) {
                return Err(ScanError::Semantic(
                    "registry seed fixture differs from the exact synthetic envelope".to_owned(),
                ));
            }
        }
        Ok(registry)
    }

    pub(crate) fn require_identity(&self, id: &str, version: u64) -> Result<(), ScanError> {
        if id != self.id || version != self.version {
            return Err(ScanError::Semantic(
                "manifest canary registry identity/version mismatch".to_owned(),
            ));
        }
        Ok(())
    }

    pub(crate) fn marker(
        &self,
        id: &str,
        policy_id: &str,
        class: &str,
        fixture_id: &str,
        fixture_path: &str,
    ) -> Result<(String, u64), ScanError> {
        if !self.policies.contains(policy_id) {
            return Err(ScanError::Canary {
                id: id.to_owned(),
                reason: "permission policy is absent from registry",
            });
        }
        let canary = self.canaries.get(id).ok_or_else(|| ScanError::Canary {
            id: id.to_owned(),
            reason: "canary is absent from registry",
        })?;
        if canary.permission_policy_id != policy_id
            || canary.state_class != class
            || canary.fixture_id != fixture_id
            || canary.fixture_path != fixture_path
        {
            return Err(ScanError::Canary {
                id: id.to_owned(),
                reason: "manifest placement disagrees with registry",
            });
        }
        Ok((self.derive_marker(canary), canary.expected_occurrences))
    }

    pub(crate) fn markers(&self) -> Vec<KnownMarker> {
        self.canaries
            .values()
            .map(|canary| KnownMarker {
                id: canary.id.clone(),
                marker: self.derive_marker(canary),
                fixture_id: canary.fixture_id.clone(),
                fixture_path: canary.fixture_path.clone(),
                expected_occurrences: canary.expected_occurrences,
            })
            .collect()
    }

    fn derive_marker(&self, canary: &RawCanary) -> String {
        let version = self.version.to_string();
        let fields = [
            self.domain.as_str(),
            self.id.as_str(),
            version.as_str(),
            canary.fixture_id.as_str(),
            canary.id.as_str(),
            canary.state_class.as_str(),
            canary.role.as_str(),
        ];
        let mut hasher = Sha256::new();
        for (index, field) in fields.iter().enumerate() {
            if index != 0 {
                hasher.update([0]);
            }
            hasher.update(field.as_bytes());
        }
        let class_token = if canary.state_class == "Secret" {
            "SECRET"
        } else {
            "DYNAMIC_PRIVATE"
        };
        format!(
            "RCCE_CANARY_V1__{class_token}__{}__{}__{}",
            canary.fixture_id,
            canary.id,
            hex::encode(hasher.finalize())
        )
    }

    fn expected_fixture(&self, canary: &RawCanary, marker: &str) -> Vec<u8> {
        let authority = if canary.state_class == "Secret" {
            "credential_material=ABSENT_BY_DESIGN"
        } else {
            "runtime_authority=NONE"
        };
        format!(
            "RCCE-P07-SYNTHETIC-CANARY-V1\nregistry_id={}\nregistry_version={}\nfixture_id={}\ncanary_id={}\nstate_class={}\nrole={}\nmarker={}\n{}\nservice_endpoint=fixture.invalid:0\noperational=false\n",
            self.id,
            self.version,
            canary.fixture_id,
            canary.id,
            canary.state_class,
            canary.role,
            marker,
            authority
        )
        .into_bytes()
    }

    fn validate_closed_tree(
        &self,
        root: &crate::fs::Root,
        inventory: &crate::fs::Inventory,
    ) -> Result<(), ScanError> {
        let expected_files = BTreeSet::from([
            "registry-v1.toml".to_owned(),
            "validate_registry.py".to_owned(),
            "fixtures/secret-credential-sentinel-v1.txt".to_owned(),
            "fixtures/dynamic-private-runtime-sentinel-v1.txt".to_owned(),
        ]);
        let observed_files = inventory
            .files
            .iter()
            .map(|file| file.path.clone())
            .collect::<BTreeSet<_>>();
        if inventory.directories != 1 || observed_files != expected_files {
            return Err(ScanError::Semantic(
                "registry tree membership differs from closed v1 support/fixture set".to_owned(),
            ));
        }
        let markers = self.markers();
        let mut observed = BTreeMap::<String, Vec<String>>::new();
        for file in &inventory.files {
            if file
                .path
                .split('/')
                .any(|component| contains_complete_marker(component.as_bytes()))
            {
                return Err(ScanError::Semantic(
                    "complete canary marker is forbidden in a registry path".to_owned(),
                ));
            }
            let body = if let Some((directory, path)) = file.path.split_once('/') {
                root.read_fixture_file(directory, path, 1024 * 1024)?
            } else {
                root.read_component(&file.path, 1024 * 1024)?
            };
            for token in complete_markers(&body) {
                let marker = markers
                    .iter()
                    .find(|marker| marker.marker.as_bytes() == token)
                    .ok_or_else(|| {
                        ScanError::Semantic(
                            "registry tree contains an undeclared complete marker".to_owned(),
                        )
                    })?;
                observed
                    .entry(marker.id.clone())
                    .or_default()
                    .push(file.path.clone());
            }
        }
        for marker in markers {
            if observed.get(&marker.id) != Some(&vec![marker.fixture_path.clone()]) {
                return Err(ScanError::Semantic(
                    "registry marker is absent, duplicated, or outside its declared fixture"
                        .to_owned(),
                ));
            }
        }
        Ok(())
    }
}

fn valid_id(value: &str) -> bool {
    let mut bytes = value.bytes();
    matches!(bytes.next(), Some(first) if first.is_ascii_lowercase() || first.is_ascii_digit())
        && bytes.all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'-')
}

fn safe_fixture_path(value: &str) -> bool {
    let mut components = value.split('/');
    matches!(components.next(), Some("fixtures"))
        && components.clone().next().is_some()
        && components.all(|component| {
            !component.is_empty()
                && component != "."
                && component != ".."
                && !component.contains('\\')
                && !component.contains(':')
        })
}

fn complete_markers(bytes: &[u8]) -> Vec<&[u8]> {
    const PREFIX: &[u8] = b"RCCE_CANARY_V1__";
    let mut found = Vec::new();
    let mut search = 0;
    while search + PREFIX.len() <= bytes.len() {
        let Some(relative) = bytes[search..]
            .windows(PREFIX.len())
            .position(|window| window == PREFIX)
        else {
            break;
        };
        let start = search + relative;
        let mut cursor = start + PREFIX.len();
        let class = [b"SECRET__".as_slice(), b"DYNAMIC_PRIVATE__".as_slice()]
            .into_iter()
            .find(|class| bytes[cursor..].starts_with(class));
        let Some(class) = class else {
            search = start + 1;
            continue;
        };
        cursor += class.len();
        let Some(after_fixture) = marker_id_end(bytes, cursor) else {
            search = start + 1;
            continue;
        };
        cursor = after_fixture;
        let Some(after_id) = marker_id_end(bytes, cursor) else {
            search = start + 1;
            continue;
        };
        cursor = after_id;
        let end = cursor.saturating_add(64);
        if end <= bytes.len()
            && bytes[cursor..end]
                .iter()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(byte))
        {
            found.push(&bytes[start..end]);
        }
        search = start + 1;
    }
    found
}

fn marker_id_end(bytes: &[u8], start: usize) -> Option<usize> {
    let separator = bytes[start..]
        .windows(2)
        .position(|window| window == b"__")?;
    if separator == 0
        || !bytes[start..start + separator]
            .iter()
            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || *byte == b'-')
    {
        return None;
    }
    Some(start + separator + 2)
}

fn contains_complete_marker(bytes: &[u8]) -> bool {
    !complete_markers(bytes).is_empty()
}

fn resolve_classification(
    primary_candidates: &[&str],
    additional: &[&str],
    inherited: &[&str],
) -> Result<Vec<String>, ScanError> {
    if primary_candidates.len() != 1 || !STATE_CLASSES.contains(&primary_candidates[0]) {
        return Err(ScanError::Semantic(
            "classification must resolve exactly one valid primary".to_owned(),
        ));
    }
    if additional
        .iter()
        .chain(inherited)
        .any(|class| !STATE_CLASSES.contains(class))
    {
        return Err(ScanError::Semantic(
            "classification contains an invalid additive constraint".to_owned(),
        ));
    }
    let primary = primary_candidates[0];
    let mut classes = vec![primary.to_owned()];
    classes.extend(
        STATE_CLASSES
            .iter()
            .copied()
            .filter(|class| {
                *class != primary
                    && additional
                        .iter()
                        .chain(inherited)
                        .any(|candidate| candidate == class)
            })
            .map(str::to_owned),
    );
    Ok(classes)
}

fn class_decision(operation: &RawOperation, classes: &[String], options: &[&str]) -> &'static str {
    let outcomes = classes
        .iter()
        .map(|class| {
            let rule = &operation.rules[class.as_str()];
            if rule.disposition == "conditional" {
                if rule
                    .option
                    .as_deref()
                    .is_some_and(|option| options.contains(&option))
                {
                    "include"
                } else {
                    "deny"
                }
            } else {
                match rule.disposition.as_str() {
                    "include" => "include",
                    "deny" => "deny",
                    "derive-only" => "derive-only",
                    "dispatch" => "dispatch",
                    _ => "invalid",
                }
            }
        })
        .collect::<BTreeSet<_>>();
    if outcomes == BTreeSet::from(["dispatch"]) {
        "dispatch"
    } else if outcomes.contains("dispatch") {
        "reject"
    } else if outcomes.contains("deny") {
        if outcomes == BTreeSet::from(["deny"]) {
            "absent"
        } else {
            "reject"
        }
    } else if outcomes == BTreeSet::from(["derive-only"]) {
        "derived"
    } else if outcomes.contains("derive-only") {
        "reject"
    } else {
        "present"
    }
}

fn verify_classification_cases(operations: &[RawOperation]) -> Result<(), ScanError> {
    let operation_map = operations
        .iter()
        .map(|operation| (operation.id.as_str(), operation))
        .collect::<BTreeMap<_, _>>();
    let cases: [(&str, &[&str], &[&str], &[&str], &[&str], &str); 12] = [
        ("clone", &["PublicClient"], &["Secret"], &[], &[], "reject"),
        (
            "clone",
            &["PublicClient"],
            &["Secret"],
            &[],
            &["include-secret"],
            "present",
        ),
        (
            "client-package",
            &["PublicClient"],
            &["Secret"],
            &[],
            &[],
            "reject",
        ),
        (
            "client-package",
            &["Secret"],
            &["DynamicPrivate"],
            &[],
            &[],
            "absent",
        ),
        (
            "server-package",
            &["PublicClient"],
            &["Unknown"],
            &[],
            &[],
            "reject",
        ),
        (
            "server-package",
            &["ServerConfig"],
            &[],
            &["Secret"],
            &[],
            "reject",
        ),
        (
            "server-package",
            &["ServerConfig"],
            &[],
            &["Secret"],
            &["include-secret"],
            "present",
        ),
        (
            "diagnostics",
            &["PublicClient"],
            &[],
            &["DynamicPrivate"],
            &[],
            "derived",
        ),
        (
            "migration",
            &["AuthoringSource"],
            &["Unknown"],
            &[],
            &[],
            "reject",
        ),
        (
            "migration",
            &["AuthoringSource"],
            &["Unknown"],
            &[],
            &["preserve-unknown"],
            "present",
        ),
        (
            "publication",
            &["PublicClient"],
            &["ServerConfig"],
            &[],
            &[],
            "dispatch",
        ),
        (
            "clone",
            &["PublicClient"],
            &["PublicClient"],
            &["PublicClient"],
            &[],
            "present",
        ),
    ];
    for (operation, primary, additional, inherited, options, expected) in cases {
        let classes = resolve_classification(primary, additional, inherited)?;
        if class_decision(operation_map[operation], &classes, options) != expected {
            return Err(ScanError::Semantic(
                "canonical v1 mixed-classification case failed".to_owned(),
            ));
        }
    }
    let rejected: [(&[&str], &[&str], &[&str]); 5] = [
        (&[], &[], &[]),
        (&["PublicClient", "PublicClient"], &[], &[]),
        (&["PublicClient", "Secret"], &[], &[]),
        (&["NotAClass"], &[], &[]),
        (&["PublicClient"], &["NotAClass"], &[]),
    ];
    if rejected.iter().any(|(primary, additional, inherited)| {
        resolve_classification(primary, additional, inherited).is_ok()
    }) {
        return Err(ScanError::Semantic(
            "canonical v1 rejected-classification case was accepted".to_owned(),
        ));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    fn registry_path() -> std::path::PathBuf {
        Path::new(env!("CARGO_MANIFEST_DIR")).join("../../test-data/canaries/registry-v1.toml")
    }

    fn write_registry_tree(source: &str) -> tempfile::TempDir {
        let temp = tempfile::tempdir().unwrap();
        fs::create_dir(temp.path().join("fixtures")).unwrap();
        fs::write(temp.path().join("registry-v1.toml"), source).unwrap();
        fs::copy(
            registry_path()
                .parent()
                .unwrap()
                .join("validate_registry.py"),
            temp.path().join("validate_registry.py"),
        )
        .unwrap();
        for name in [
            "secret-credential-sentinel-v1.txt",
            "dynamic-private-runtime-sentinel-v1.txt",
        ] {
            fs::copy(
                registry_path()
                    .parent()
                    .unwrap()
                    .join("fixtures")
                    .join(name),
                temp.path().join("fixtures").join(name),
            )
            .unwrap();
        }
        temp
    }

    fn assert_semantic_rejection(source: String) {
        let temp = write_registry_tree(&source);
        let error = Registry::load(&temp.path().join("registry-v1.toml")).unwrap_err();
        assert!(matches!(error, ScanError::Semantic(_)));
    }

    fn mutate_operation(source: &str, operation: &str, from: &str, to: &str) -> String {
        let start = source
            .find(&format!("[[operations]]\nid = \"{operation}\""))
            .unwrap();
        let end = source[start + 1..]
            .find("[[operations]]")
            .map_or(source.len(), |relative| start + 1 + relative);
        let mut result = source.to_owned();
        let changed = source[start..end].replacen(from, to, 1);
        result.replace_range(start..end, &changed);
        result
    }

    #[test]
    fn parses_real_closed_p07_registry_and_matches_seed_fixtures() {
        let registry = Registry::load(&registry_path()).unwrap();
        let markers = registry.markers();
        assert_eq!(markers.len(), 2);
        for marker in markers {
            let fixture = registry_path().parent().unwrap().join(&marker.fixture_path);
            let body = fs::read_to_string(fixture).unwrap();
            assert_eq!(
                body.matches(&marker.marker).count() as u64,
                marker.expected_occurrences
            );
        }
    }

    #[test]
    fn rejects_unknown_registry_fields_without_echoing_source() {
        let temp = tempfile::tempdir().unwrap();
        let secret = "REGISTRY_PARSE_ERROR_SECRET_9df2";
        let original = fs::read_to_string(registry_path()).unwrap();
        let hostile = original.replacen(
            "schema_version = 1",
            &format!("schema_version = 1\nunknown_root = \"{secret}\""),
            1,
        );
        let path = temp.path().join("registry-v1.toml");
        fs::write(&path, hostile).unwrap();
        let message = Registry::load(&path).unwrap_err().to_string();
        assert!(message.contains("closed v1 TOML contract"));
        assert!(!message.contains(secret));
    }

    #[test]
    fn rejects_p07_semantic_registry_mutations() {
        let original = fs::read_to_string(registry_path()).unwrap();
        let cases = [
            original.replacen(
                "state_class_order = [\"PublicClient\", \"ServerConfig\"",
                "state_class_order = [\"ServerConfig\", \"PublicClient\"",
                1,
            ),
            original.replacen("expected = \"absent\"", "expected = \"present\"", 1),
            original.replacen(
                "options = [\"include-secret\"]",
                "options = [\"include-secret\", \"include-dynamic-private\"]",
                1,
            ),
            original.replacen(
                "operation = \"backup\"\noptions = []",
                "operation = \"clone\"\noptions = []",
                1,
            ),
            original.replacen(
                "expected_occurrences = 1",
                "expected_occurrences = 2",
                1,
            ),
            original.replacen(
                "fixture_path = \"fixtures/dynamic-private-runtime-sentinel-v1.txt\"",
                "fixture_path = \"fixtures/secret-credential-sentinel-v1.txt\"",
                1,
            ),
            original.replacen(
                "[[permission_policies]]\nid = \"dynamic-private-explicit-internal\"\nstate_class = \"DynamicPrivate\"",
                "[[permission_policies]]\nid = \"dynamic-private-explicit-internal\"\nstate_class = \"Secret\"",
                1,
            ),
            format!(
                "{original}\n[[permission_policies]]\nid = \"third-policy\"\nstate_class = \"Secret\"\ndescription = \"extra\"\nassertions = []\n"
            ),
            original.replacen(
                "constraint_mode = \"additive\"",
                "constraint_mode = \"replace\"",
                1,
            ),
            original.replacen(
                "path = \"validate_registry.py\"",
                "path = \"validator.py\"",
                1,
            ),
            mutate_operation(
                &original,
                "clone",
                "[operations.rules.PublicClient]\ndisposition = \"include\"",
                "[operations.rules.PublicClient]\ndisposition = \"dispatch\"",
            ),
            mutate_operation(
                &original,
                "clone",
                "[operations.rules.ServerConfig]\ndisposition = \"include\"",
                "[operations.rules.ServerConfig]\ndisposition = \"conditional\"\noption = \"include-secret\"",
            ),
            mutate_operation(
                &original,
                "client-package",
                "[operations.rules.Secret]\ndisposition = \"deny\"",
                "[operations.rules.Secret]\ndisposition = \"include\"",
            ),
            mutate_operation(
                &original,
                "diagnostics",
                "[operations.rules.PublicClient]\ndisposition = \"derive-only\"",
                "[operations.rules.PublicClient]\ndisposition = \"include\"",
            ),
        ];
        for source in cases {
            assert_semantic_rejection(source);
        }
    }

    #[test]
    fn rejects_client_package_server_config_bypass() {
        let original = fs::read_to_string(registry_path()).unwrap();
        let source = mutate_operation(
            &original,
            "client-package",
            "[operations.rules.ServerConfig]\ndisposition = \"deny\"",
            "[operations.rules.ServerConfig]\ndisposition = \"include\"",
        );
        let temp = write_registry_tree(&source);
        let error = Registry::load(&temp.path().join("registry-v1.toml")).unwrap_err();
        assert!(error.to_string().contains("canonical v1 table"));
    }

    #[test]
    fn rejects_registry_tree_membership_and_undeclared_complete_markers() {
        let original = fs::read_to_string(registry_path()).unwrap();
        let temp = write_registry_tree(&original);
        fs::write(temp.path().join("fixtures/extra.txt"), b"benign").unwrap();
        let error = Registry::load(&temp.path().join("registry-v1.toml")).unwrap_err();
        assert!(error.to_string().contains("tree membership"));

        let temp = write_registry_tree(&original);
        let marker = format!("RCCE_CANARY_V1__SECRET__x__y__{}", "a".repeat(64));
        let validator = temp.path().join("validate_registry.py");
        let mut body = fs::read(&validator).unwrap();
        body.extend_from_slice(marker.as_bytes());
        fs::write(&validator, body).unwrap();
        let error = Registry::load(&temp.path().join("registry-v1.toml")).unwrap_err();
        assert!(error.to_string().contains("undeclared complete marker"));
        assert!(!error.to_string().contains(&marker));
    }

    #[test]
    fn rejects_mutated_seed_fixture_envelope() {
        let original = fs::read_to_string(registry_path()).unwrap();
        let temp = write_registry_tree(&original);
        let fixture = temp
            .path()
            .join("fixtures/secret-credential-sentinel-v1.txt");
        let body = fs::read_to_string(&fixture)
            .unwrap()
            .replace("operational=false", "operational=true");
        fs::write(&fixture, body).unwrap();
        let error = Registry::load(&temp.path().join("registry-v1.toml")).unwrap_err();
        assert!(error.to_string().contains("synthetic envelope"));
    }
}
