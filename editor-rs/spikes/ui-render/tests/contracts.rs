use rcce_ui_render_spike::{
    evidence::{CandidateDecision, GateStatus},
    harness::{percentile, physical_extent, TraceConfig},
    picking::expected_pick_id,
    state::{camera_angle, HarnessSnapshot, InputAction},
};

#[test]
fn percentiles_are_stable_and_directional() {
    let samples = [9.0, 1.0, 5.0, 3.0, 7.0];
    assert_eq!(percentile(&samples, 0.50), Some(5.0));
    assert_eq!(percentile(&samples, 0.95), Some(9.0));
    assert_eq!(percentile(&[], 0.95), None);
}

#[test]
fn input_actions_produce_exact_snapshot_deltas() {
    let before = HarnessSnapshot::default();
    let mut after = before.clone();
    after.apply(InputAction::SelectNext);
    after.apply(InputAction::OrbitRight(1));
    after.apply(InputAction::SetReducedMotion(true));
    assert_eq!(before.selected_row, 0);
    assert_eq!(before.camera_yaw, [0.0, 0.0]);
    assert!(!before.reduced_motion);
    assert_eq!(after.selected_row, 1);
    assert_eq!(after.camera_yaw, [0.0, 0.25]);
    assert!(after.reduced_motion);
}

#[test]
fn reduced_motion_freezes_automatic_camera_but_preserves_explicit_input() {
    assert_ne!(
        camera_angle(0, 0, false, 0.0),
        camera_angle(60, 0, false, 0.0)
    );
    assert_eq!(
        camera_angle(0, 0, true, 0.25),
        camera_angle(60, 0, true, 0.25)
    );
    assert_ne!(
        camera_angle(60, 0, true, 0.0),
        camera_angle(60, 0, true, 0.25)
    );
}

#[test]
fn dpi_extent_uses_physical_pixels_and_never_reaches_zero() {
    assert_eq!(physical_extent([512.0, 384.0], 2.0), [1024, 768]);
    assert_eq!(physical_extent([0.0, 0.0], 1.5), [1, 1]);
}

#[test]
fn standard_trace_exercises_the_declared_scale() {
    let trace = TraceConfig::load_checked(std::path::Path::new("traces/standard-v1.json")).unwrap();
    assert_eq!(trace.catalog_rows, 100_000);
    assert_eq!(trace.viewport_counts, vec![1, 2]);
    assert_eq!(trace.dpi_percent, vec![100, 150, 200]);
    assert!(trace.measured_frames >= 1_800);
}

#[test]
fn checked_in_trace_matches_the_embedded_standard_without_seed_drift() {
    let disk = TraceConfig::load_checked(std::path::Path::new("traces/standard-v1.json")).unwrap();
    assert_eq!(disk, TraceConfig::standard());
}

#[test]
fn picking_ids_are_viewport_and_half_specific() {
    assert_eq!(expected_pick_id(0, 0, 640), 101);
    assert_eq!(expected_pick_id(0, 639, 640), 102);
    assert_eq!(expected_pick_id(1, 0, 640), 201);
    assert_eq!(expected_pick_id(1, 639, 640), 202);
}

#[test]
fn any_fail_or_not_run_keeps_the_candidate_unselected() {
    let all_pass = CandidateDecision::new(vec![GateStatus::Pass, GateStatus::Pass]);
    assert!(all_pass.selected());

    let fail = CandidateDecision::new(vec![GateStatus::Pass, GateStatus::Fail]);
    assert!(!fail.selected());

    let not_run = CandidateDecision::new(vec![GateStatus::Pass, GateStatus::NotRun]);
    assert!(!not_run.selected());
}
