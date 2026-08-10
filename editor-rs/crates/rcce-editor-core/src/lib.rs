//! GUI-independent editor orchestration boundary.

mod feedback;

pub use feedback::{
    load_feedback_project, FeedbackActor, FeedbackActorCatalog, FeedbackActorCount,
    FeedbackActorMesh, FeedbackActorReference, FeedbackAssetCatalog, FeedbackDiagnostic,
    FeedbackEntry, FeedbackEvidence, FeedbackFindResult, FeedbackFindTarget, FeedbackFocusTarget,
    FeedbackLoadProgress, FeedbackMediaStatus, FeedbackObservation, FeedbackObservationEvidence,
    FeedbackObservationIndex, FeedbackProject, FeedbackReturnTrail, FeedbackScript,
    FeedbackScriptCatalog, FeedbackScriptDiagnostic, FeedbackScriptFamily, FeedbackVaultCatalog,
    FeedbackVaultEntry, FeedbackVaultFacet, FeedbackZone, FeedbackZoneCatalog,
    FeedbackZoneDiagnostic, FeedbackZoneStatus, Lens, FEEDBACK_RETURN_TRAIL_CAPACITY,
};
