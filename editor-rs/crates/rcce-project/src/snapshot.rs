use crate::classification::classify;
use crate::fingerprint::SourceFingerprint;
use crate::inventory::{provenance, InventoryFile, ProjectInventory, UnavailableEntry};
use crate::root::{
    MetadataBudget, MetadataEntry, MetadataKind, MetadataLocation, ProjectRoot, ReadAssurance,
    ReadBudget, RootControl, RootError, RootErrorCode,
};
use std::sync::Arc;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScanControl {
    Continue,
    Cancel,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SnapshotProgress {
    MetadataAccepted {
        entries: u64,
        files: u64,
        unavailable: u64,
    },
    FileAccepted {
        path: String,
        completed_files: u64,
        completed_bytes: u64,
        total_files: u64,
    },
    Complete {
        files: u64,
        unavailable: u64,
        bytes: u64,
    },
}

#[derive(Debug)]
pub enum SnapshotError {
    Root(RootError),
    Cancelled,
    MetadataChanged,
}

impl From<RootError> for SnapshotError {
    fn from(value: RootError) -> Self {
        Self::Root(value)
    }
}

#[derive(Debug, Clone)]
pub struct ProjectSnapshot {
    inventory: ProjectInventory,
    // Metadata entries retain only immutable enumeration identity tokens. The
    // backend deliberately closes every enumerated file before returning.
    pub(crate) bound_entries: Arc<[MetadataEntry]>,
}

impl PartialEq for ProjectSnapshot {
    fn eq(&self, other: &Self) -> bool {
        self.inventory == other.inventory
    }
}

impl Eq for ProjectSnapshot {}

impl ProjectSnapshot {
    pub fn load(
        root: &ProjectRoot,
        budget: MetadataBudget,
        assurance: ReadAssurance,
        mut control: impl FnMut() -> ScanControl,
        mut progress: impl FnMut(&SnapshotProgress),
    ) -> Result<Self, SnapshotError> {
        if control() == ScanControl::Cancel {
            return Err(SnapshotError::Cancelled);
        }
        let metadata = match root.metadata_controlled(budget, assurance, || match control() {
            ScanControl::Continue => RootControl::Continue,
            ScanControl::Cancel => RootControl::Cancel,
        }) {
            Ok(metadata) => metadata,
            Err(error) if error.code() == RootErrorCode::Cancelled => {
                return Err(SnapshotError::Cancelled)
            }
            Err(error) => return Err(SnapshotError::Root(error)),
        };
        let total_files = metadata
            .entries
            .iter()
            .filter(|entry| matches!(entry.kind, MetadataKind::File { .. }))
            .count() as u64;
        let unavailable_count = metadata
            .entries
            .iter()
            .filter(|entry| matches!(entry.kind, MetadataKind::Unavailable(_)))
            .count() as u64;
        progress(&SnapshotProgress::MetadataAccepted {
            entries: metadata.entries.len() as u64,
            files: total_files,
            unavailable: unavailable_count,
        });
        let mut files = Vec::with_capacity(total_files as usize);
        let mut unavailable = Vec::with_capacity(unavailable_count as usize);
        let mut completed_bytes = 0_u64;
        for entry in &metadata.entries {
            match entry.kind {
                MetadataKind::File { size } => {
                    let path = match &entry.location {
                        MetadataLocation::Portable(path) => path.clone(),
                        MetadataLocation::Opaque(_) => return Err(SnapshotError::MetadataChanged),
                    };
                    let accepted = match root.read_enumerated_controlled(
                        entry,
                        ReadBudget { max_bytes: size },
                        assurance,
                        || match control() {
                            ScanControl::Continue => RootControl::Continue,
                            ScanControl::Cancel => RootControl::Cancel,
                        },
                    ) {
                        Ok(accepted) => accepted,
                        Err(error) if error.code() == RootErrorCode::Cancelled => {
                            return Err(SnapshotError::Cancelled)
                        }
                        Err(error) => return Err(SnapshotError::Root(error)),
                    };
                    let mut hasher = sha2::Sha256::new();
                    use sha2::Digest;
                    for chunk in accepted.bytes().as_slice().chunks(64 * 1024) {
                        if control() == ScanControl::Cancel {
                            return Err(SnapshotError::Cancelled);
                        }
                        hasher.update(chunk);
                    }
                    let accepted_size = accepted.observed_size();
                    let fingerprint = SourceFingerprint(hasher.finalize().into());
                    let classification = classify(&path);
                    let file_provenance = provenance(
                        root.identity(),
                        root.capabilities(),
                        path.clone(),
                        fingerprint,
                        &classification,
                    );
                    files.push(InventoryFile {
                        path: path.clone(),
                        size: accepted_size,
                        fingerprint,
                        classification,
                        provenance: file_provenance,
                    });
                    completed_bytes = completed_bytes
                        .checked_add(accepted_size)
                        .ok_or(SnapshotError::MetadataChanged)?;
                    progress(&SnapshotProgress::FileAccepted {
                        path,
                        completed_files: files.len() as u64,
                        completed_bytes,
                        total_files,
                    });
                }
                MetadataKind::Unavailable(reason) => unavailable.push(UnavailableEntry {
                    location: entry.location.clone(),
                    reason,
                }),
                MetadataKind::Directory => {}
            }
        }
        if completed_bytes != metadata.declared_bytes {
            return Err(SnapshotError::MetadataChanged);
        }
        progress(&SnapshotProgress::Complete {
            files: files.len() as u64,
            unavailable: unavailable.len() as u64,
            bytes: completed_bytes,
        });
        Ok(Self {
            inventory: ProjectInventory::from_parts(files, unavailable),
            bound_entries: metadata.entries.into(),
        })
    }

    #[must_use]
    pub fn inventory(&self) -> &ProjectInventory {
        &self.inventory
    }
}
