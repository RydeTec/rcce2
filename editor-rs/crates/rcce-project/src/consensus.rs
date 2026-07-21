//! Sealed, read-only P03-to-P04 binding for the first actor/media consensus slice.

use crate::legacy::{
    ByteSpan, DocumentProvenance, LegacyDocument, LegacyEncoding, LegacyStringConstraints,
    ParserIdentity, RawLegacyString,
};
use crate::root::{
    MetadataKind, MetadataLocation, ProjectRelativePath, ProjectRoot, ReadAssurance, ReadBudget,
    RootControl, RootErrorCode,
};
use crate::{InventoryFile, ProjectSnapshot, ScanControl, SourceFingerprint};
use rcce_data::{
    ActorParseCompletion as ClientCompletion, ActorParseEvidence as ClientActors,
    MeshParseEvidence, MeshSlotDisposition,
};
use rcce_server_core::{
    ActorParseCompletion as ServerCompletion, ActorParseEvidence as ServerActors,
};
use std::collections::{BTreeMap, BTreeSet};
use std::sync::Arc;
use unicode_normalization::UnicodeNormalization;

const ACTORS_PATH: &str = "Data/Server Data/Actors.dat";
const MESHES_PATH: &str = "Data/Game Data/Meshes.dat";
const ACTORS_MAX_BYTES: u64 = 4 * 1024 * 1024;
const MESHES_MAX_BYTES: u64 = 16 * 1024 * 1024;
const ACTORS_PARSER: ParserIdentity = ParserIdentity::new("rcce-actors-consensus", 1);
const MESHES_PARSER: ParserIdentity = ParserIdentity::new("rcce-meshes-consensus", 1);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConsensusLevel {
    Consensus,
    Provisional,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ActorCountEvidence {
    Agreed(usize),
    Disagreed { client: usize, server: usize },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ActorMediaAvailability {
    NoBaseMesh,
    Present,
    MissingCatalog,
    MissingPhysical,
    Provisional,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct CatalogTopologyEvidence {
    pub has_aliases: bool,
    pub has_gaps: bool,
    pub has_invalid_offsets: bool,
    pub has_catalog_entries_unreferenced_by_actor_base_slice: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ActorMediaOutcome {
    pub actor_id: u16,
    pub base_mesh: Option<u16>,
    pub availability: ActorMediaAvailability,
    pub physical_inventory_path: Option<String>,
    pub race: Option<RawLegacyString>,
}

#[derive(Debug, Clone)]
pub struct ActorMediaConsensus {
    level: ConsensusLevel,
    actor_count: ActorCountEvidence,
    actors: Vec<ActorMediaOutcome>,
    topology: CatalogTopologyEvidence,
    _actors_document: LegacyDocument<ClientActors>,
    _meshes_document: Option<LegacyDocument<MeshParseEvidence>>,
}

impl ActorMediaConsensus {
    #[must_use]
    pub const fn level(&self) -> ConsensusLevel {
        self.level
    }

    #[must_use]
    pub const fn actor_count(&self) -> ActorCountEvidence {
        self.actor_count
    }

    #[must_use]
    pub fn actors(&self) -> &[ActorMediaOutcome] {
        &self.actors
    }

    #[must_use]
    pub const fn catalog_topology(&self) -> CatalogTopologyEvidence {
        self.topology
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConsensusLoadError {
    RootIdentityMismatch,
    MissingActors,
    AmbiguousInventory,
    BoundEntryMissing,
    ResourceLimit,
    Cancelled,
    Root(RootErrorCode),
    FingerprintMismatch,
    ParserRejected,
}

impl ProjectSnapshot {
    /// Load the fixed actor/base-mesh slice. Callers cannot select paths, bytes,
    /// inventory records, parser identities, or a generic document type.
    pub fn load_actor_media_consensus(
        &self,
        root: &ProjectRoot,
        mut control: impl FnMut() -> ScanControl,
    ) -> Result<ActorMediaConsensus, ConsensusLoadError> {
        let actor_file = self
            .select_inventory(ACTORS_PATH)?
            .ok_or(ConsensusLoadError::MissingActors)?;
        let actor_bytes = self.reread_bound(root, actor_file, ACTORS_MAX_BYTES, &mut control)?;
        let client = rcce_data::ActorCatalog::parse_with_evidence(&actor_bytes);
        let server = rcce_server_core::ActorCatalog::parse_with_evidence(&actor_bytes);
        let actor_document = LegacyDocument::from_inventory_binding(
            Arc::clone(&actor_bytes),
            client,
            DocumentProvenance::from_inventory(actor_file.clone(), ACTORS_PARSER),
        )
        .map_err(|_| ConsensusLoadError::FingerprintMismatch)?;

        let mesh_file = self.select_inventory(MESHES_PATH)?;
        let meshes_document = if let Some(file) = mesh_file {
            let bytes = self.reread_bound(root, file, MESHES_MAX_BYTES, &mut control)?;
            let parsed = rcce_data::MeshCatalog::parse_with_evidence(&bytes)
                .map_err(|_| ConsensusLoadError::ParserRejected)?;
            Some(
                LegacyDocument::from_inventory_binding(
                    bytes,
                    parsed,
                    DocumentProvenance::from_inventory(file.clone(), MESHES_PARSER),
                )
                .map_err(|_| ConsensusLoadError::FingerprintMismatch)?,
            )
        } else {
            None
        };

        Self::assemble_actor_media(actor_document, server, meshes_document, self)
    }

    fn select_inventory(
        &self,
        canonical: &str,
    ) -> Result<Option<&InventoryFile>, ConsensusLoadError> {
        let key = portable_path_key(canonical);
        let matches = self
            .inventory()
            .files
            .iter()
            .filter(|file| portable_path_key(&file.path) == key)
            .collect::<Vec<_>>();
        match matches.as_slice() {
            [] => Ok(None),
            [file] => Ok(Some(*file)),
            _ => Err(ConsensusLoadError::AmbiguousInventory),
        }
    }

    fn reread_bound(
        &self,
        root: &ProjectRoot,
        file: &InventoryFile,
        max_bytes: u64,
        control: &mut impl FnMut() -> ScanControl,
    ) -> Result<Arc<[u8]>, ConsensusLoadError> {
        if root.identity() != file.provenance.root {
            return Err(ConsensusLoadError::RootIdentityMismatch);
        }
        if file.size > max_bytes {
            return Err(ConsensusLoadError::ResourceLimit);
        }
        let entry = self
            .bound_entries
            .iter()
            .find(|entry| {
                matches!(&entry.location, MetadataLocation::Portable(path) if path == &file.path)
                    && entry.kind == MetadataKind::File { size: file.size }
            })
            .ok_or(ConsensusLoadError::BoundEntryMissing)?;
        let accepted = root
            .read_enumerated_controlled(
                entry,
                ReadBudget { max_bytes },
                ReadAssurance::BaselineQuarantine,
                || match control() {
                    ScanControl::Continue => RootControl::Continue,
                    ScanControl::Cancel => RootControl::Cancel,
                },
            )
            .map_err(|error| {
                if error.code() == RootErrorCode::Cancelled {
                    ConsensusLoadError::Cancelled
                } else {
                    ConsensusLoadError::Root(error.code())
                }
            })?;
        let bytes: Arc<[u8]> = accepted.bytes().as_slice().to_vec().into();
        if accepted.observed_size() != file.size
            || SourceFingerprint::from_bytes(&bytes) != file.fingerprint
        {
            return Err(ConsensusLoadError::FingerprintMismatch);
        }
        Ok(bytes)
    }

    fn assemble_actor_media(
        actor_document: LegacyDocument<ClientActors>,
        server: ServerActors,
        meshes_document: Option<LegacyDocument<MeshParseEvidence>>,
        snapshot: &ProjectSnapshot,
    ) -> Result<ActorMediaConsensus, ConsensusLoadError> {
        let client = actor_document.value();
        let client_count = client.records.len();
        let server_count = server.records.len();
        let actor_count = if client_count == server_count {
            ActorCountEvidence::Agreed(client_count)
        } else {
            ActorCountEvidence::Disagreed {
                client: client_count,
                server: server_count,
            }
        };
        let client_ids = client
            .records
            .iter()
            .map(|record| record.id)
            .collect::<BTreeSet<_>>();
        let server_ids = server
            .records
            .iter()
            .map(|record| record.id)
            .collect::<BTreeSet<_>>();
        let duplicate_actor_ids =
            client_ids.len() != client_count || server_ids.len() != server_count;
        let raw_actor_strings_are_utf8 = client.records.iter().all(|record| {
            std::str::from_utf8(
                &actor_document.original_bytes()[record.race_span.start..record.race_span.end],
            )
            .is_ok()
        });
        let mut provisional = client.completion != ClientCompletion::Complete
            || server.completion != ServerCompletion::Complete
            || client_ids != server_ids
            || duplicate_actor_ids
            || !raw_actor_strings_are_utf8;

        let referenced = client
            .value
            .templates
            .values()
            .filter_map(|actor| (actor.mesh_ids[0] != 65535).then_some(actor.mesh_ids[0]))
            .collect::<BTreeSet<_>>();
        let mut topology = CatalogTopologyEvidence::default();
        if let Some(document) = &meshes_document {
            let mesh = document.value();
            topology.has_aliases = mesh
                .occupied_slots()
                .iter()
                .any(|(_, slot)| matches!(slot, MeshSlotDisposition::Alias { .. }));
            topology.has_invalid_offsets = mesh.occupied_slots().iter().any(|(_, slot)| {
                matches!(
                    slot,
                    MeshSlotDisposition::InvalidOffset(_)
                        | MeshSlotDisposition::DecodeFailed { .. }
                )
            });
            let occupied = mesh
                .occupied_slots()
                .iter()
                .map(|(id, _)| *id)
                .collect::<BTreeSet<_>>();
            if let (Some(first), Some(last)) = (occupied.first(), occupied.last()) {
                topology.has_gaps = (*first..=*last).any(|id| !occupied.contains(&id));
            }
            topology.has_catalog_entries_unreferenced_by_actor_base_slice = mesh
                .value
                .entries
                .iter()
                .any(|entry| !referenced.contains(&entry.id));
            provisional |= topology.has_aliases
                || topology.has_gaps
                || topology.has_invalid_offsets
                || !mesh.skipped.is_empty();
        }

        let mut actors = BTreeMap::new();
        for record in &client.records {
            let template = &client.value.templates[&record.id];
            let race = actor_document
                .raw_string(
                    ByteSpan::from_bounds(
                        record.race_span.start as u64,
                        record.race_span.end as u64,
                    )
                    .map_err(|_| ConsensusLoadError::ParserRejected)?,
                    LegacyStringConstraints {
                        encoding: LegacyEncoding::UnspecifiedBytes,
                        maximum_bytes: Some(256),
                        diagnose_nul: true,
                        diagnose_control_bytes: true,
                    },
                )
                .map_err(|_| ConsensusLoadError::ParserRejected)?;
            let base_mesh = (template.mesh_ids[0] != 65535).then_some(template.mesh_ids[0]);
            let (availability, physical_inventory_path) = match (base_mesh, &meshes_document) {
                (None, _) => (ActorMediaAvailability::NoBaseMesh, None),
                (Some(_), None) => (ActorMediaAvailability::MissingCatalog, None),
                (Some(media_id), Some(document)) => resolve_physical(snapshot, document, media_id),
            };
            actors.insert(
                record.id,
                ActorMediaOutcome {
                    actor_id: record.id,
                    base_mesh,
                    availability,
                    physical_inventory_path,
                    race: Some(race),
                },
            );
        }
        let mut actors = actors.into_values().collect::<Vec<_>>();
        if provisional
            || actors
                .iter()
                .any(|actor| actor.availability == ActorMediaAvailability::Provisional)
        {
            provisional = true;
            for actor in &mut actors {
                actor.availability = ActorMediaAvailability::Provisional;
                actor.physical_inventory_path = None;
            }
        }
        Ok(ActorMediaConsensus {
            level: if provisional {
                ConsensusLevel::Provisional
            } else {
                ConsensusLevel::Consensus
            },
            actor_count,
            actors,
            topology,
            _actors_document: actor_document,
            _meshes_document: meshes_document,
        })
    }
}

fn resolve_physical(
    snapshot: &ProjectSnapshot,
    document: &LegacyDocument<MeshParseEvidence>,
    media_id: u16,
) -> (ActorMediaAvailability, Option<String>) {
    let Some(record) = document
        .value()
        .records
        .iter()
        .find(|record| record.id == media_id)
    else {
        return match document.value().slot(media_id) {
            MeshSlotDisposition::Gap => (ActorMediaAvailability::MissingCatalog, None),
            _ => (ActorMediaAvailability::Provisional, None),
        };
    };
    let raw = &document.original_bytes()[record.filename_span.start..record.filename_span.end];
    let Ok(filename) = std::str::from_utf8(raw) else {
        return (ActorMediaAvailability::Provisional, None);
    };
    // Meshes.dat stores legacy Windows-style separators. Preserve `raw` in
    // the bound document and derive a separate portable lookup projection.
    let projected_filename = filename.replace('\\', "/");
    let candidate = format!("Data/Meshes/{projected_filename}");
    if ProjectRelativePath::parse(&candidate).is_err() {
        return (ActorMediaAvailability::Provisional, None);
    }
    let key = portable_path_key(&candidate);
    let matches = snapshot
        .inventory()
        .files
        .iter()
        .filter(|file| portable_path_key(&file.path) == key)
        .collect::<Vec<_>>();
    if matches.len() == 1 {
        return (
            ActorMediaAvailability::Present,
            Some(matches[0].path.clone()),
        );
    }
    let unavailable = snapshot
        .inventory()
        .unavailable
        .iter()
        .any(|entry| match &entry.location {
            MetadataLocation::Portable(path) => portable_path_key(path) == key,
            MetadataLocation::Opaque(parts) => {
                let expected = candidate.split('/').map(str::as_bytes).collect::<Vec<_>>();
                parts.len() == expected.len()
                    && parts
                        .iter()
                        .zip(expected)
                        .all(|(actual, expected)| actual == expected)
            }
        });
    if matches.len() > 1 || unavailable {
        (ActorMediaAvailability::Provisional, None)
    } else {
        (ActorMediaAvailability::MissingPhysical, None)
    }
}

fn portable_path_key(path: &str) -> String {
    path.split('/')
        .map(|component| component.nfc().collect::<String>().to_lowercase())
        .collect::<Vec<_>>()
        .join("/")
}
