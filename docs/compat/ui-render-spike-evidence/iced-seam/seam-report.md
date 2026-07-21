# Iced 0.13.1 one-device seam disposition

**Disposition: rejected at the compile-time seam.** No full UI/render harness was built.

The bounded question was whether iced 0.13.1's ordinary wgpu renderer could consume the exact
adapter, device, queue, and caller-owned texture views already used by `rcce-render` (wgpu
22.1.0). The locked graph instead contains iced_wgpu 0.13.5 on wgpu 0.19.4. Rust treats those GPU
resource types as distinct. The preserved E0308 diagnostic rejects all four resources.

| Gate | Result | Evidence |
|---|---:|---|
| Exact Rust 1.85, locked probe builds | Pass | `seam_contract`: 4 passed |
| `WorldView::new` accepts the existing wgpu 22 device | Pass | positive compile contract |
| iced consumes the same adapter/device/queue | **Fail** | E0308 for Adapter, Device, Queue |
| iced consumes a caller-owned wgpu 22 texture view | **Fail** | E0308 for TextureView |
| Two `WorldView`s share the one iced renderer/device | NotRun | impossible after the one-resource seam failed |
| Integrates without a second device, unsafe bridge, wgpu 19 runtime, or renderer migration | **Fail** | normal iced API requires wgpu 0.19 types |
| Runtime identity/ownership counters | NotRun | runtime seam was not compiled; stop rule applied |
| Full viewport, accessibility, performance, and Windows matrix | NotRun | candidate rejected before harness |
| `rcce-render` regression baseline | Pass | 10 passed, 0 failed before probe |

The two-viewport requirement is therefore falsified for this candidate under the stated
constraints: a renderer that cannot consume one existing wgpu 22 device or one caller-owned
texture cannot consume two `WorldView`s backed by those resources. Making it work would require
at least one excluded architectural change: a second wgpu 0.19 GPU owner, an unsafe/raw-handle
bridge, a maintained iced renderer port to wgpu 22, or migration of the production renderer.

The first generated lock selected `wayland-protocols` 0.32.13, which declares Rust 1.86. The
disposable lock was narrowed to 0.32.12 so the seam itself could be tested under exact Rust 1.85.
That pin resolves the probe MSRV issue but does not change the GPU type mismatch.

Offline verification is host-scoped. Locked Linux-host tests and `cargo metadata --no-deps`
complete offline. Full all-target locked metadata does not: the local Cargo cache lacks the
target-specific `android-activity` 0.6.1 package. No package was installed or downloaded to hide
that boundary.
