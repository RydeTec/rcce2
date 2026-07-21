# Slint 1.13.1 one-device seam disposition

**Disposition: rejected at the compile-time seam.** No full UI/render harness was built.

The bounded question was whether Slint 1.13.1's supported wgpu integration could consume the
exact instance, adapter, device, queue, and caller-owned texture already used by `rcce-render`
(wgpu 22.1.0). The probe enables Slint's winit, FemtoVG-wgpu, and accessibility features and
threads the manual configuration through the documented `BackendSelector::require_wgpu_26`
entry point. That unstable API exposes wgpu 26.0.1 resource types. Rust treats those resource
types as distinct, and the preserved diagnostics reject all five resources.

| Gate | Result | Evidence |
|---|---:|---|
| Exact Rust 1.85, locked probe builds | Pass | `seam_contract`: 5 passed |
| `WorldView::new` accepts the existing wgpu 22 device | Pass | positive compile contract |
| Supported Slint winit/FemtoVG-wgpu/accessibility graph compiles | Pass | positive wgpu 26 backend-selector contract |
| Slint `BackendSelector::require_wgpu_26(Manual)` accepts the existing resources | **Fail** | E0308 for all four fields |
| Slint `Image::try_from` accepts a caller-owned wgpu 22 texture | **Fail** | E0277 for `wgpu22::Texture` |
| Two `WorldView`s share one Slint renderer/device | NotRun | impossible after the one-resource seam failed |
| Integrates without a second device, unsafe bridge, or production renderer migration | **Fail** | supported Slint API requires wgpu 26 types |
| Runtime identity/ownership counters | NotRun | runtime seam was not compiled; stop rule applied |
| Full viewport, accessibility, performance, and Windows matrix | NotRun | candidate rejected before harness |
| `rcce-render` regression baseline | Pass | 10 passed, 0 failed before and after probe |

The two-viewport requirement is therefore falsified for this candidate under the stated
constraints: an integration that cannot consume one existing wgpu 22 device or one caller-owned
texture cannot consume two `WorldView`s backed by those resources. Making it work would require
at least one excluded architectural change: a second wgpu 26 GPU owner, an unsafe/raw-handle
bridge, a maintained Slint renderer port to wgpu 22, or migration of the production renderer.

The first generated lock selected transitive patches whose declared MSRVs exceed Rust 1.85. The
lock was narrowed only as needed: image 0.25.9, moxcms 0.7.11, smol_str 0.3.2,
typed-index-collections 3.2.3, url 2.5.4, idna 1.0.3, idna_adapter 1.2.1, ICU
collections/locale/normalizer/provider 2.1.1, and ICU properties 2.1.2. These pins let the exact
1.85 compiler test the core seam. Enabling the supported winit/accessibility graph additionally
required wayland-protocols 0.32.12, zbus/zbus-macros 5.7.1, zbus-names 4.2.0, zbus-xml 5.0.2,
zvariant/zvariant-derive 5.5.2, and zvariant-utils 3.2.0. These pins do not change the wgpu type
mismatch.

Slint labels this wgpu 26 feature unstable and warns that it may be removed or changed in a future
minor release. The failed result is therefore bound to Slint 1.13.1 rather than asserted as a
permanent framework contract.

Slint's licensing choices still require a separate project-policy decision before any production
selection. That policy question was not reached and does not substitute for the failed compile
contract.
