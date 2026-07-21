# ADR 0006 — UI/render spike decision protocol

- **Status:** Accepted (protocol); selection pending
- **Date:** 2026-07-20

## Context and evidence

The intended editor needs dense desktop tooling, large virtualized catalogs,
keyboard and assistive-technology operation, high-DPI text, native dialogs, and
multiple live `rcce-render` viewports. The client already owns productive
`winit`/`wgpu` rendering code, but no editor workspace or executable UI/render
spike exists at this revision. Therefore no UI framework or compositor contract
has executable acceptance evidence.

The current recommendation—`egui`/`egui-wgpu` over shared `winit`/`wgpu`—is a
hypothesis, not a selected production dependency.

## Decision

Framework selection is made only by a disposable, bounded executable spike.
The first candidate is `egui`/`egui-wgpu`. If it fails a mandatory gate, `iced`
and `Slint` are evaluated through the same harness and evidence rubric.

The spike must prove:

- one `wgpu` instance/adapter/device/queue shared by UI and renderer;
- deterministic compositing through either caller-owned encoder recording or
  returned viewport textures;
- two simultaneous live viewports, overlays, picking readback, resize and 200%
  DPI, and device-loss recovery;
- docking/panels, a large virtualized catalog, keyboard traversal, visible
  focus, native file selection, and layout at 1024 px desktop width;
- Windows UI Automation semantics sufficient for Narrator to announce
  controls, state, progress, and errors;
- WCAG 2.2 AA contrast, reduced-motion behavior, and no mouse-only M1 flow;
- named open/input/diagnostic/cancellation/memory/viewport budgets on recorded
  reference fixtures and hardware;
- unchanged client screenshot and renderer contract tests.

The initial quantitative gates inherited from the accepted program are visible
progress within 250 ms during project open, the default fixture ready within
5 s, lens/focus feedback within 100 ms p95, incremental diagnostics within
250 ms p95, cancellation acknowledged within 250 ms, and one interactive world
viewport sustaining p95 frame time at or below 16.67 ms (equivalently p5 FPS at
or above 60). M0 records small/default/large fixtures,
reference hardware, and measured memory and commit-duration budgets before M1
exit. A gate changes only through recorded measurement and an ADR update; a
candidate cannot waive it locally.

Every candidate runs the same versioned harness and immutable scripted event
trace against content-addressed small/default/large fixtures. The evidence
records fixture hashes; source revision and dependency lock hash; OS, kernel,
drivers, CPU, GPU, RAM, display/DPI, and power mode; cold and warm cache state;
warm-up count; measured-run count and duration; input event sequence and seed;
and trace/tool versions. Each performance result reports raw samples plus p50
and p95 frame time (or equivalently p50 and p5 FPS, with the direction named),
separately for one and two simultaneously active viewports. Memory reports both
peak working set and steady state after a fixed idle/interaction window. Runs
that omit a field are `Not run`, not comparable evidence.

The spike lives outside production composition roots and may be deleted. Domain
and command state must remain in headless crates; framework widget state cannot
become the project model. A short evidence report records candidate versions,
platform, hardware, commands, traces/screenshots, pass/fail per gate, and final
selection or rejection.

## Alternatives considered

1. Select `egui` from familiarity or design screenshots. Rejected because
   accessibility, device sharing, and performance are executable properties.
2. Build a custom widget toolkit. Rejected as an initial choice because it adds
   focus, accessibility, text, and DPI obligations without evidence of need.
3. Embed renderer and authoritative client state together. Rejected because the
   editor requires reusable viewports, not client-session globals.
4. Compare frameworks with separate demos. Rejected because unequal harnesses
   make results non-comparable.

## Consequences

- Production UI work does not begin until the spike records a passing choice.
- Recommended dependencies can change without changing headless architecture.
- Failure of all candidates leaves the selection pending rather than silently
  waiving accessibility or renderer gates.
- Renderer seam work exposed by the spike remains separately reviewed shared
  crate work.

## Invariants

- No framework is called selected while this ADR retains “selection pending.”
- Every candidate faces the same mandatory harness and budgets.
- Project truth, commands, validation, and storage remain headless.
- One device/queue and two-viewport evidence is mandatory, not aspirational.
- Performance claims are reproducible from a fixture hash, machine profile,
  cache state, warm-up, run count, scripted events, and raw measurements.
- Accessibility evidence includes an observed UIA/Narrator path, not widget-name
  assertions alone.
- Client renderer regressions gate acceptance.

## Verification and exit evidence

At this revision: **UNEXECUTED**. `editor-rs/` and a UI spike are absent, so no
candidate has passed.

Selection requires a committed evidence report with exact commands and outputs,
screenshots/manual observations, UI Automation inspection, Narrator transcript,
100%/200% DPI evidence, device-loss exercise, GPU/resource ordering evidence,
performance traces, and client-render test results. Independent review attacks
accessibility, hidden second-device creation, coupling, and cherry-picked
hardware. The report includes one gate table whose rows are the mandatory gates
above and whose values are `Pass`, `Fail`, or `Not run`; any `Fail` or `Not run`
keeps that candidate unselected. Only then is this ADR or a superseding
selection ADR updated to name the production stack. The report includes one-
and two-viewport p95 frame time or p5 FPS, plus peak and steady-state memory,
for cold and warm cache runs on every named reference machine.

## Dependencies

- [`rcce-render` current workspace](../../client-rs/crates/rcce-render)
- [Rust client plan](../rust-client/PLAN.md)
- [Canonical UI spike requirements](../plans/plan/rust-super-editor-engine-migration.md#7-ui-technology-is-validated-by-a-bounded-spike)

## Supersession and change process

The selected stack is recorded by a new ADR or an evidence-backed status update
that preserves this protocol and links the report. Changing the stack later
requires rerunning the same mandatory gates plus migration impact on production
UI and shared renderer consumers.
