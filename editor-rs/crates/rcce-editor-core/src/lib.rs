//! GUI-independent editor orchestration boundary.

mod feedback;

pub use feedback::{
    load_feedback_project, FeedbackActor, FeedbackActorCatalog, FeedbackActorCount,
    FeedbackActorMesh, FeedbackActorReference, FeedbackAssetCatalog, FeedbackDiagnostic,
    FeedbackEntry, FeedbackEvidence, FeedbackFindResult, FeedbackFindTarget, FeedbackLoadProgress,
    FeedbackMediaStatus, FeedbackProject, FeedbackScript, FeedbackScriptCatalog,
    FeedbackScriptDiagnostic, FeedbackScriptFamily, FeedbackZone, FeedbackZoneCatalog,
    FeedbackZoneDiagnostic, FeedbackZoneStatus, Lens,
};
