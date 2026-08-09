use serde::{Deserialize, Serialize};

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "kebab-case")]
pub enum GateStatus {
    Pass,
    Fail,
    NotRun,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct GateResult {
    pub id: String,
    pub status: GateStatus,
    pub evidence: String,
}

#[derive(Clone, Debug)]
pub struct CandidateDecision {
    statuses: Vec<GateStatus>,
}

impl CandidateDecision {
    #[must_use]
    pub fn new(statuses: Vec<GateStatus>) -> Self {
        Self { statuses }
    }

    #[must_use]
    pub fn selected(&self) -> bool {
        !self.statuses.is_empty()
            && self
                .statuses
                .iter()
                .all(|status| *status == GateStatus::Pass)
    }
}

#[must_use]
pub fn current_gate_results() -> Vec<GateResult> {
    [
        ("single-device-queue", GateStatus::NotRun),
        ("deterministic-compositing", GateStatus::NotRun),
        ("two-live-viewports", GateStatus::NotRun),
        ("visible-overlays", GateStatus::NotRun),
        ("picking-readback", GateStatus::NotRun),
        ("resize-and-dpi", GateStatus::NotRun),
        ("device-loss-recovery", GateStatus::NotRun),
        ("docking-and-panels", GateStatus::NotRun),
        ("large-virtualized-catalog", GateStatus::NotRun),
        ("keyboard-traversal", GateStatus::NotRun),
        ("visible-focus", GateStatus::NotRun),
        ("windows-uia-narrator", GateStatus::NotRun),
        ("native-file-selection", GateStatus::NotRun),
        ("wcag-aa-contrast", GateStatus::NotRun),
        ("reduced-motion", GateStatus::NotRun),
        ("no-mouse-only-m1-flow", GateStatus::NotRun),
        ("layout-at-1024", GateStatus::NotRun),
        ("open-visible-progress-250ms", GateStatus::NotRun),
        ("default-ready-5s", GateStatus::NotRun),
        ("input-feedback-100ms-p95", GateStatus::NotRun),
        ("diagnostic-250ms-p95", GateStatus::NotRun),
        ("cancellation-250ms", GateStatus::NotRun),
        ("memory-one-two-viewport-matrix", GateStatus::NotRun),
        ("frame-time-one-two-viewport-matrix", GateStatus::NotRun),
        ("client-render-regression", GateStatus::NotRun),
    ]
    .into_iter()
    .map(|(id, status)| GateResult {
        id: id.to_owned(),
        status,
        evidence: "No committed runtime evidence for this machine/scenario.".to_owned(),
    })
    .collect()
}
