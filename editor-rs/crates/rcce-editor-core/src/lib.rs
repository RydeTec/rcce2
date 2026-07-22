//! GUI-independent editor orchestration boundary.

mod feedback;

pub use feedback::{
    load_feedback_project, FeedbackActor, FeedbackActorCatalog, FeedbackActorCount,
    FeedbackDiagnostic, FeedbackEntry, FeedbackEvidence, FeedbackLoadProgress, FeedbackMediaStatus,
    FeedbackProject, Lens,
};
