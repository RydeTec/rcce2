//! GUI-independent editor orchestration boundary.

mod feedback;

pub use feedback::{
    load_feedback_project, FeedbackEntry, FeedbackLoadProgress, FeedbackProject, Lens,
};
