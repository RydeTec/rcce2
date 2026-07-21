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
or above 60). The versioned
[`performance-reference-v1.toml`](../compat/performance-reference-v1.toml)
records small/default/large fixture and reference-machine status plus measured
memory budgets required before candidate selection and M1 exit. Its current
status is `blocked`; a candidate cannot treat unavailable fixtures, an
incomplete machine, or unmeasured memory as approval. Commit duration cannot be
measured truthfully by read-only M1: it is a mandatory pre-write M2 gate,
measured on the first representative ADR-0005 commit implementation before any
write API is authorized. A raw copy/rename/fsync microbenchmark is not a
substitute. A gate changes only through recorded measurement and an ADR update;
a candidate cannot waive it locally.

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

Status text has no approval authority by itself. An approved machine, fixture,
measured budget, or aggregate record carries a typed external approval record
with reviewer identity and role, UTC timestamp, reviewed artifact revision, and
exact SHA-256 subject-evidence bindings. The approval record is a separately
hashed artifact and is excluded from those bindings, avoiding a self-referential
hash. The validator can emit both the full semantic subject digest and the
canonical approval-record envelope before that envelope is hashed. Fixture
approval additionally binds license,
consent, sensitivity, materialization, and content-identity evidence. Measured
budgets bind immutable trace, source, dependency lock, fixture, machine, cache,
protocol, command, and raw-sample artifacts from which the recorded statistic
is recomputed. Each approval also binds the canonical digest of the complete
subject—including a budget's fixed identity semantics and threshold—so a later
field edit makes the decision stale. Candidate labels, placeholder commands, or
self-asserted `approved` fields do not satisfy this contract.

Evidence lives beneath one dedicated root and uses canonical POSIX relative
paths. Validation opens the root by a component-wise descriptor-relative
no-follow walk from a stable filesystem anchor, then inventories the complete
declared file and parent-directory set through retained handles. It rejects
undeclared files, empty/non-derived directories, and any symlink/reparse
component. An immutable initial snapshot binds every path and file/directory
identity; evidence bytes are read from retained file handles tied to that
snapshot; a separately captured final snapshot and retained ancestry check must
match before validation succeeds. Every opened directory is fstat-checked both
before and after its captured listing, and every final directory path is reopened
and identity-compared immediately before acceptance.
The authority is intentionally `linux-descriptor-v1`: approved records require
the Linux descriptor-validation backend, which may run locally or in CI. Native
Windows may capture the reference-machine profile and raw evidence, but those
exact bytes are transferred into the dedicated evidence tree for validation by
that backend. This boundary remains until an independently reviewed native
Windows handle/reparse-safe backend exists.
Fixture identity and materialization artifacts contain a sorted file manifest;
the validator recomputes `RCCE-CORPUS-TREE-V1` from path bytes, size, and file
SHA-256 rather than trusting a claimed aggregate. Numeric thresholds and raw
measurements must be finite, the recorded statistic is recomputed, and every
measured budget carries an explicit outcome consistent with its threshold.
Validation is resource-bounded: at most 4,096 evidence files and 4,096 evidence
directories, 64 relative path components, 255 UTF-8 bytes per component, 4,096
UTF-8 bytes per relative path, 64 MiB per file, 512 MiB total, 8 MiB per typed
JSON document, JSON depth 64, and 100,000 fixture manifest entries. The tree
walk is iterative and descriptor-relative. Evidence is streamed into SHA-256 and length counters; only
bounded typed JSON is buffered. JSON rejects duplicate keys and non-standard
constants. Every artifact kind crosses a typed shape boundary before semantic
field access: objects, arrays, strings, integers, manifest entries, bindings,
review payloads, and every machine-profile observed field are checked with
path-qualified errors and exact types/ranges. Approval records additionally require the exact canonical UTF-8 JSON
encoding emitted by the validator (sorted keys, compact separators, no trailing
newline), not merely an equivalent parsed object.

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
