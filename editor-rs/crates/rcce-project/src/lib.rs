//! Root-confined RCCE project-model boundary.

pub mod root;

pub use root::{
    AcceptedBytes, CapabilityAvailability, ProjectRelativePath, ProjectRoot, ReadAssurance,
    ReadBudget, RootCapabilities, RootError, RootErrorCode, RootIdentity, WalkBudget, WalkFile,
    WalkResult,
};
