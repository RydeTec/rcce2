//! Root-confined RCCE project-model boundary.

pub mod classification;
pub mod fingerprint;
pub mod identity;
pub mod inventory;
pub mod legacy;
pub mod root;
pub mod snapshot;

pub use classification::{
    classify, matrix_applicability, Classification, CompatibilityLevel, ConstraintEvidence,
    MatrixApplicability, StateClass, MATRIX_FAMILY_IDS, PROJECT_FORMAT_RULES_VERSION,
};
pub use fingerprint::{SourceFingerprint, TreeFingerprint};
pub use identity::{
    ActorId, EmitterName, IdentityKind, InvalidIdentity, ItemId, LegacyReference, MediaId,
    ScriptPath, ZoneName,
};
pub use inventory::{
    FileProvenance, InventoryFile, ProjectInventory, ProjectShape, UnavailableEntry,
};
pub use legacy::{
    ByteSpan, Diagnostic, DiagnosticCode, DiagnosticSeverity, DocumentIdentity, DocumentProvenance,
    EvidenceStatus, LegacyDisplay, LegacyDocument, LegacyDocumentError, LegacyEncoding,
    LegacyStringConstraints, NoWriteAuthority, ParserIdentity, RawLegacyString, SpanError,
};
pub use snapshot::{ProjectSnapshot, ScanControl, SnapshotError, SnapshotProgress};

pub use root::{
    AcceptedBytes, AcceptedFile, CapabilityAvailability, MetadataBudget, MetadataEntry,
    MetadataKind, MetadataLocation, MetadataResult, ProjectRelativePath, ProjectRoot,
    ReadAssurance, ReadBudget, RootCapabilities, RootControl, RootError, RootErrorCode,
    RootIdentity, UnavailableKind, WalkBudget, WalkFile, WalkResult,
};
