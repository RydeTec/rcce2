# UI/render/accessibility decision spike — egui revision 2

## Outcome

The egui 0.29.1 candidate is rejected and no production UI is selected. The executable proves
several useful compositor seams, but ADR-0006 permits selection only when every mandatory gate
passes. This revision contains Fail and NotRun results, including a measured frame-time failure,
missing docking, incomplete device-loss evidence, and unexecuted assistive-technology and real
project-operation gates.

The subsequent compile-only seams also rejected iced 0.13.1 and Slint 1.13.1. Iced's supported
renderer requires wgpu 0.19 resources; Slint's supported winit/FemtoVG-wgpu backend selector and
texture import require wgpu 26 resources. Neither accepts the existing `rcce-render` wgpu 22 instance/device
family without an excluded second GPU owner, bridge, or production renderer migration. P06 now
returns to architecture review; no fallback framework was selected.

Slint explicitly marks this wgpu 26 API unstable and subject to change in a future minor release;
the seam result is bound to the exact 1.13.1 source and lock rather than generalized permanently.

This remains a disposable spike under `editor-rs/spikes/ui-render/`. It imports the existing
`rcce-render::WorldView`; it does not change production renderer or client source.

## Candidate and compatibility context

The executed candidate is egui/egui-winit/egui-wgpu 0.29.1 over winit 0.30.13 and wgpu 22.1.0
using Rust 1.85. Iced 0.13.1 supports Rust 1.80 but its renderer declares wgpu 0.19. Iced 0.14
requires Rust 1.88 and wgpu 27. Slint 1.12.1 exposes a wgpu 24 feature; Slint 1.13.1 supports
Rust 1.85 and exposes a wgpu 26 feature. Exact MSRV, license, and dependency-tree evidence is in
`ui-render-spike-evidence/dependency-compatibility.txt`. These facts describe experiment seams;
they do not establish product fitness for any unexecuted candidate.

## Revised harness

The executable reads and validates the checked-in `traces/standard-v1.json`; it rejects viewport
or scale cases outside that schedule. The embedded standard and checked-in trace now share seed
1380143941, and an exact test prevents future drift. A standard case executes 300 warmup frames
followed by 1,800 measured frames. Raw samples label their phase, and percentiles use measured
frames only.

The harness owns one wgpu instance, surface, adapter, device, and queue. One or two independently
sized `WorldView` values render into returned textures. Each viewport has separate camera yaw,
accessible orbit controls, a spike-local R32Uint ID target, and a compositor-drawn yellow
crosshair. The 100,000-row catalog constructs only visible rows. Reduced motion now freezes
frame-driven camera animation while preserving explicit orbit input; exact snapshot-vs-input
tests cover selection, independent yaw, and the reduced-motion transition.

Each run writes environment, trace, phase-labelled frames, picks, summary, screenshot plus
sidecar, conservative gate snapshot, and a SHA-256 provenance record. Every retained run carries
the same executable-source, Cargo.lock, and trace identities in its environment, summary, memory
summary, screenshot sidecar, and provenance record. The aggregate evidence manifest additionally
binds the reviewed gate table and every retained case file.

The executable-source aggregate is deliberately defined over executable inputs only: Cargo.toml,
the Rust modules compiled into `rcce-ui-render-spike`, and its WGSL shader. Tests, the matrix
runner, reports, adjudication files, raw evidence, and the standalone provenance utility are not
inputs to the measured executable and are excluded. This lets evidence generation change without
changing the measured identity. The frozen matrix identities are:

- executable source: `8de40bf480449196f1ffa8c52d674646f07637a9fc7c2a537bf434e4de3eab48`
- Cargo.lock: `c55eca8575eb1646598c04dc8b2b1f6c9333e24de0b45ec905ab0c9d035826f4`
- trace: `01d3cdae1a416cf1abb0a8f1dd3de93e7a0a340ab28713ed27af1f2538d10301`

## Windows release matrix

The reference available for this run was Windows 11 Pro 10.0.22631 on an Intel i5-8500 (six
logical processors), 17,111,252,992 bytes RAM, and NVIDIA GeForce GTX 1660 Ti driver
32.0.15.8088. The active display was 1920x1080 at 60 Hz and the active scheme was Smart Game
Boost Power Plan (GUID `1ddc7869-3e92-4139-a109-4dde7ae6d2a8`). wgpu selected Vulkan. The
harness window was 1024x768 at native scale factor 1.0; egui scale was deterministically overridden
to 100%, 150%, and 200% because alternate physical DPI displays were unavailable.

All six warm-cache release cases completed 2,100 frames. Memory was sampled every 100 ms; steady
working set is the mean of the last 20 samples. The cold-cache matrix and accepted M0
fixture/budget artifact were not captured, so the memory matrix
is useful raw evidence but not protocol-complete.

| Active viewports | Scale override | Measured frames | Frame p50 | Frame p95 | Peak working set | Steady working set |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 100% | 1,800 | 10.98 ms | 50.28 ms | 309,522,432 B | 305,727,078 B |
| 1 | 150% | 1,800 | 12.58 ms | 50.25 ms | 302,661,632 B | 297,245,082 B |
| 1 | 200% | 1,800 | 11.30 ms | 50.29 ms | 303,452,160 B | 298,444,595 B |
| 2 | 100% | 1,800 | 11.71 ms | 50.29 ms | 304,754,688 B | 301,101,261 B |
| 2 | 150% | 1,800 | 10.16 ms | 50.28 ms | 306,114,560 B | 300,513,485 B |
| 2 | 200% | 1,800 | 7.25 ms | 50.30 ms | 306,802,688 B | 301,783,450 B |

Every p95 result exceeds ADR-0006's inherited 16.67 ms one-viewport gate. The frame-time gate is
therefore Fail; missing cold-cache evidence is not used to soften that observed failure. No M0
memory threshold is invented.

## Authoritative ADR-0006 gate table

`ui-render-spike-evidence/observed-gates.json` is the machine-readable authority. The executable's
per-run `gate-snapshot.json` remains conservative pre-adjudication data and never asserts that an
unobserved case ran.

| Gate | Status | Evidence boundary |
|---|---|---|
| One shared device/queue | Pass | Inspected ownership plus executed Windows traces |
| Deterministic compositing | Pass | Fixed returned-texture/world-before-UI ordering and addressed screenshots |
| Two live viewports | Pass | Three 2,100-frame two-viewport cases |
| Visible overlays | Pass | Crosshairs visible in retained screenshots |
| Picking readback | Pass | IDs 101/102 and 201/202 match independent expectations |
| Resize and 200% DPI | NotRun | Scale matrix ran; live resize did not |
| Device-loss recovery | Fail | Reconstruction resumed; registered callback was not observed |
| Docking and panels | Fail | Resizable panels exist; docking/detachment/persistence do not |
| 100,000-row virtual catalog | Pass | Only 15–35 visible rows constructed |
| Keyboard traversal | NotRun | Handlers exist; no native traversal transcript |
| Visible focus | NotRun | Styling exists; no native focus-order observation |
| UIA/Narrator | NotRun | AccessKit wiring compiled; no UIA tree or Narrator transcript |
| Native file selection | NotRun | rfd compiled; dialog was not operated |
| WCAG 2.2 AA contrast | NotRun | No measured ratios/review |
| Reduced motion | Pass | Exact executed camera/snapshot tests |
| No mouse-only M1 flow | NotRun | Complete M1 flows are absent/unobserved |
| Layout at 1024 px | Pass | Retained 1024x768 screenshots |
| Open progress within 250 ms | NotRun | No real project-open operation |
| Default project ready within 5 s | NotRun | No accepted fixture or real open |
| Input feedback within 100 ms p95 | NotRun | No native input-latency samples |
| Incremental diagnostics within 250 ms p95 | NotRun | No diagnostic engine/trace |
| Cancellation within 250 ms | NotRun | No cancellable project operation |
| One/two viewport cold/warm memory matrix | NotRun | Warm raw matrix exists; cold and accepted budget/fixtures are missing |
| One/two viewport frame-time matrix | Fail | Warm release p95 50.25–50.30 ms vs 16.67 ms gate |
| Client renderer contract regression | Pass | Rust 1.85 `rcce-render` tests 10/10 |
| Client screenshot regression | NotRun | No baseline/current client screenshot comparison |
| Independent viewport navigation/input | NotRun | Separate state/tests exist; native input not automated |
| Evidence provenance completeness | Fail | Hash/environment bindings exist; accepted fixture hashes and cold-cache evidence remain missing |
| Linux runtime (supplemental) | Fail | Ten frames written, then exit 139 after EGL/ZINK errors |

Aggregate: **9 Pass / 5 Fail / 15 NotRun**. `selected=false`; disposition is
`rejected-pending-next-candidate`.

## Verification

The pinned Rust 1.85 contract suite now contains eight tests, including checked-in trace equality,
exact input snapshot deltas, reduced-motion behavior, picking IDs, DPI extent, scale schedule,
percentiles, and fail-closed selection. The existing `rcce-render` suite passed 10/10. Native
Windows release runs and raw artifacts are under `ui-render-spike-evidence/revision-v3/`.

The current WSL Wayland rerun selected llvmpipe, wrote ten frames, printed its completion marker,
then terminated with exit 139 after EGL DRI2/ZINK errors. Linux runtime remains Fail. Exact output
is retained in `linux-runtime.txt`.

## Candidate seam disposition

The bounded fallback results are in `ui-render-spike-evidence/next-candidate-plan.md`, with compiler
evidence under `iced-seam/` and `slint-seam/`. Both candidates failed the shared-resource seam, so
the program returns to architecture review. The full trace and ADR-0006 matrix remain available
for any future candidate that first passes the one-device contract; no egui result is borrowed.
