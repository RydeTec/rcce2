use rcce_project::{
    classify, CompatibilityLevel, MetadataBudget, ProjectRoot, ProjectSnapshot, ReadAssurance,
    ScanControl, SnapshotProgress, StateClass,
};
use std::path::PathBuf;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Lens {
    Records,
    World,
    Assets,
    Scripts,
    Vault,
}

impl Lens {
    pub const ALL: [Self; 5] = [
        Self::Records,
        Self::World,
        Self::Assets,
        Self::Scripts,
        Self::Vault,
    ];

    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::Records => "Records",
            Self::World => "World",
            Self::Assets => "Assets",
            Self::Scripts => "Scripts",
            Self::Vault => "Vault",
        }
    }

    const fn index(self) -> usize {
        match self {
            Self::Records => 0,
            Self::World => 1,
            Self::Assets => 2,
            Self::Scripts => 3,
            Self::Vault => 4,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackEntry {
    pub path: String,
    pub size: u64,
    pub family: Option<&'static str>,
    pub compatibility: &'static str,
    pub classes: Vec<&'static str>,
    pub source_sha256: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackProject {
    pub data_root: PathBuf,
    pub shape: String,
    pub unavailable: usize,
    by_lens: [Vec<FeedbackEntry>; 5],
    unclassified_files: usize,
    total_files: usize,
    total_bytes: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FeedbackLoadProgress {
    Discovering,
    Inventory {
        entries: u64,
        files: u64,
        unavailable: u64,
    },
    Reading {
        path: String,
        completed_files: u64,
        total_files: u64,
    },
    Complete {
        files: u64,
        unavailable: u64,
        bytes: u64,
    },
}

pub fn load_feedback_project(
    data_root: PathBuf,
    mut progress: impl FnMut(FeedbackLoadProgress),
) -> Result<FeedbackProject, String> {
    progress(FeedbackLoadProgress::Discovering);
    let root = ProjectRoot::open_explicit(&data_root)
        .map_err(|error| format!("cannot open project data root: {:?}", error.code()))?;
    let snapshot = ProjectSnapshot::load(
        &root,
        MetadataBudget {
            max_entries: 10_000,
            max_directories: 2_000,
            max_depth: 64,
            max_path_bytes: 4_096,
            max_component_bytes: 255,
            max_declared_bytes: 1024 * 1024 * 1024,
            max_single_file_bytes: 256 * 1024 * 1024,
        },
        ReadAssurance::BaselineQuarantine,
        || ScanControl::Continue,
        |event| {
            progress(match event {
                SnapshotProgress::MetadataAccepted {
                    entries,
                    files,
                    unavailable,
                } => FeedbackLoadProgress::Inventory {
                    entries: *entries,
                    files: *files,
                    unavailable: *unavailable,
                },
                SnapshotProgress::FileAccepted {
                    path,
                    completed_files,
                    total_files,
                    ..
                } => FeedbackLoadProgress::Reading {
                    path: canonical_data_path(path),
                    completed_files: *completed_files,
                    total_files: *total_files,
                },
                SnapshotProgress::Complete {
                    files,
                    unavailable,
                    bytes,
                } => FeedbackLoadProgress::Complete {
                    files: *files,
                    unavailable: *unavailable,
                    bytes: *bytes,
                },
            });
        },
    )
    .map_err(|error| format!("project inventory failed: {error:?}"))?;
    Ok(FeedbackProject::from_data_root(data_root, &snapshot))
}

impl FeedbackProject {
    #[must_use]
    pub fn from_data_root(data_root: PathBuf, snapshot: &ProjectSnapshot) -> Self {
        let mut by_lens: [Vec<FeedbackEntry>; 5] = std::array::from_fn(|_| Vec::new());
        let mut unclassified_files = 0;
        let inventory = snapshot.inventory();
        let total_bytes = inventory.files.iter().map(|file| file.size).sum();
        let mut has_authoring_input = false;

        for file in &inventory.files {
            let path = canonical_data_path(&file.path);
            let classification = classify(&path);
            has_authoring_input |= classification.authoring_input;
            let entry = FeedbackEntry {
                path: path.clone(),
                size: file.size,
                family: classification.family,
                compatibility: compatibility_label(classification.compatibility),
                classes: classification
                    .classes
                    .iter()
                    .copied()
                    .map(class_label)
                    .collect(),
                source_sha256: hex(&file.fingerprint.0),
            };
            let lens = lens_for(&path).unwrap_or_else(|| {
                unclassified_files += 1;
                Lens::Records
            });
            by_lens[lens.index()].push(entry);
        }

        for entries in &mut by_lens {
            entries.sort_by(|left, right| left.path.as_bytes().cmp(right.path.as_bytes()));
        }
        Self {
            data_root,
            shape: if inventory.files.is_empty() {
                "Empty"
            } else if has_authoring_input {
                "AuthoringProject"
            } else {
                "UnknownOnly"
            }
            .to_owned(),
            unavailable: inventory.unavailable.len(),
            by_lens,
            unclassified_files,
            total_files: inventory.files.len(),
            total_bytes,
        }
    }

    #[must_use]
    pub const fn total_files(&self) -> usize {
        self.total_files
    }

    #[must_use]
    pub const fn total_bytes(&self) -> u64 {
        self.total_bytes
    }

    #[must_use]
    pub fn entries(&self, lens: Lens) -> &[FeedbackEntry] {
        &self.by_lens[lens.index()]
    }

    #[must_use]
    pub fn unclassified_files(&self) -> usize {
        self.unclassified_files
    }

    pub fn all_entries(&self) -> impl Iterator<Item = &FeedbackEntry> {
        self.by_lens.iter().flat_map(|entries| entries.iter())
    }
}

fn canonical_data_path(path: &str) -> String {
    if path
        .get(..5)
        .is_some_and(|prefix| prefix.eq_ignore_ascii_case("Data/"))
    {
        format!("Data/{}", &path[5..])
    } else {
        format!("Data/{path}")
    }
}

fn lens_for(path: &str) -> Option<Lens> {
    let path = path.to_ascii_lowercase();
    if path.contains("/server data/accounts")
        || path.contains("/server data/mysql")
        || path.contains("/server data/privileged scripts")
        || path.contains("/server data/names filter")
        || path.contains("/server data/dropped items")
        || path.contains("/server data/superglobals")
    {
        Some(Lens::Vault)
    } else if path.contains("/server data/scripts/")
        || path.contains("/server data/script files/")
        || path.ends_with(".rsl")
    {
        Some(Lens::Scripts)
    } else if path.contains("/areas/")
        || path.contains("/rcte/")
        || path.contains("/rctrees/")
        || path.contains("/rccaves/")
        || path.contains("/architect/")
    {
        Some(Lens::World)
    } else if path.contains("/meshes/")
        || path.contains("/textures/")
        || path.contains("/sounds/")
        || path.contains("/music/")
        || path.contains("/emitter configs/")
        || path.contains("/ui/")
    {
        Some(Lens::Assets)
    } else if (path.contains("/game data/") || path.contains("/server data/"))
        && (path.ends_with(".dat") || path.ends_with(".txt") || path.ends_with(".xml"))
    {
        Some(Lens::Records)
    } else {
        None
    }
}

const fn compatibility_label(level: CompatibilityLevel) -> &'static str {
    match level {
        CompatibilityLevel::InventoryOnly => "inventory only",
        CompatibilityLevel::Read => "read",
        CompatibilityLevel::Unknown => "unknown",
    }
}

const fn class_label(class: StateClass) -> &'static str {
    match class {
        StateClass::AuthoringSource => "authoring source",
        StateClass::EditorMetadata => "editor metadata",
        StateClass::DynamicPrivate => "dynamic private",
        StateClass::Secret => "secret",
        StateClass::PublicClient => "public client",
        StateClass::ServerConfig => "server config",
        StateClass::Unknown => "unknown",
    }
}

fn hex(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut output = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        output.push(char::from(HEX[usize::from(byte >> 4)]));
        output.push(char::from(HEX[usize::from(byte & 0x0f)]));
    }
    output
}
