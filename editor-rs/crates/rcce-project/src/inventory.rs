use crate::classification::{
    Classification, ConstraintEvidence, StateClass, PROJECT_FORMAT_RULES_VERSION,
};
use crate::fingerprint::{SourceFingerprint, TreeFingerprint};
use crate::root::{MetadataLocation, RootCapabilities, RootIdentity, UnavailableKind};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FileProvenance {
    pub root: RootIdentity,
    pub path: String,
    pub source: SourceFingerprint,
    pub rule_table_version: u32,
    pub family: Option<&'static str>,
    pub classes: Vec<StateClass>,
    pub constraints: Vec<ConstraintEvidence>,
    pub matrix_evidence: &'static str,
    pub capabilities: RootCapabilities,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct InventoryFile {
    pub path: String,
    pub size: u64,
    pub fingerprint: SourceFingerprint,
    pub classification: Classification,
    pub provenance: FileProvenance,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UnavailableEntry {
    pub location: MetadataLocation,
    pub reason: UnavailableKind,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProjectShape {
    Empty,
    AuthoringProject,
    IncompleteProject,
    RuntimeProjectionOnly,
    RuntimeOutputOnly,
    UnknownOnly,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ProjectInventory {
    pub files: Vec<InventoryFile>,
    pub unavailable: Vec<UnavailableEntry>,
    pub tree_fingerprint: TreeFingerprint,
    pub shape: ProjectShape,
}

impl ProjectInventory {
    pub(crate) fn from_parts(
        mut files: Vec<InventoryFile>,
        mut unavailable: Vec<UnavailableEntry>,
    ) -> Self {
        files.sort_by(|a, b| a.path.as_bytes().cmp(b.path.as_bytes()));
        unavailable.sort_by(|a, b| a.location.cmp(&b.location));
        let tree_fingerprint = TreeFingerprint::from_entries(
            files
                .iter()
                .map(|file| (file.path.as_str(), file.size, file.fingerprint)),
        );
        let shape = shape(&files, unavailable.len());
        Self {
            files,
            unavailable,
            tree_fingerprint,
            shape,
        }
    }
}

fn shape(files: &[InventoryFile], unavailable: usize) -> ProjectShape {
    if files.is_empty() {
        return if unavailable == 0 {
            ProjectShape::Empty
        } else {
            ProjectShape::UnknownOnly
        };
    }
    if files
        .iter()
        .all(|file| file.path.starts_with("Game/") || file.path.starts_with("Server/"))
    {
        return ProjectShape::RuntimeProjectionOnly;
    }
    if files.iter().all(|file| {
        file.classification
            .classes
            .contains(&crate::classification::StateClass::DynamicPrivate)
    }) {
        return ProjectShape::RuntimeOutputOnly;
    }
    if files.iter().any(|file| file.classification.authoring_input) {
        return ProjectShape::AuthoringProject;
    }
    if files.iter().any(|file| file.path.starts_with("Data/")) {
        ProjectShape::IncompleteProject
    } else {
        ProjectShape::UnknownOnly
    }
}

pub(crate) fn provenance(
    root: RootIdentity,
    capabilities: RootCapabilities,
    path: String,
    source: SourceFingerprint,
    classification: &Classification,
) -> FileProvenance {
    FileProvenance {
        root,
        path,
        source,
        rule_table_version: PROJECT_FORMAT_RULES_VERSION,
        family: classification.family,
        classes: classification.classes.clone(),
        constraints: classification.constraints.clone(),
        matrix_evidence: "docs/compat/project-format-matrix.md",
        capabilities,
    }
}
