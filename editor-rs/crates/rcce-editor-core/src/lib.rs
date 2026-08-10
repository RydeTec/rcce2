//! GUI-independent editor orchestration boundary.

mod feedback;

pub use feedback::{
    load_feedback_project, FeedbackAcceptedFileDelta, FeedbackAcceptedFileDeltaKind,
    FeedbackAcceptedParentGroup, FeedbackAcceptedParentPeerTarget, FeedbackAcceptedSnapshotDelta,
    FeedbackActor, FeedbackActorBaseMeshSlotZeroEvidence, FeedbackActorCatalog, FeedbackActorCount,
    FeedbackActorMesh, FeedbackActorReference, FeedbackAssetCatalog, FeedbackAssetPathCatalog,
    FeedbackAssetPathFacet, FeedbackDiagnostic, FeedbackEntry, FeedbackEntryLocator,
    FeedbackEvidence, FeedbackFindResult, FeedbackFindTarget, FeedbackFingerprintGroup,
    FeedbackFingerprintPeerTarget, FeedbackFocusTarget, FeedbackLoadProgress, FeedbackMediaStatus,
    FeedbackObservation, FeedbackObservationEvidence, FeedbackObservationIndex, FeedbackProject,
    FeedbackReturnTrail, FeedbackScript, FeedbackScriptCatalog, FeedbackScriptDiagnostic,
    FeedbackScriptFamily, FeedbackVaultCatalog, FeedbackVaultEntry, FeedbackVaultFacet,
    FeedbackZone, FeedbackZoneCatalog, FeedbackZoneDiagnostic, FeedbackZoneStatus, Lens,
    FEEDBACK_RETURN_TRAIL_CAPACITY,
};
