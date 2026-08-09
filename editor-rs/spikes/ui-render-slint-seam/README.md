# Slint 1.13.1 one-device seam probe

This disposable crate tests one question: can Slint 1.13.1's supported wgpu integration consume
the existing instance, adapter, device, queue, and render texture used by `rcce-render` 0.1.0?

It deliberately does not create a GPU instance or device. The manifest enables Slint's supported
winit + FemtoVG-wgpu renderer path and accessibility integration. The positive contracts prove
that `WorldView::new` consumes wgpu 22.1.0 and that Slint's documented
`BackendSelector::require_wgpu_26(WGPUConfiguration::Manual { ... })` and `Image::try_from` paths
consume wgpu 26 types. The compile-fail contract passes RCCE's exact wgpu 22 resource types through
that same backend selector and image API, preserving rustc's nominal-type diagnostics.

The candidate is rejected if that boundary does not compile. Creating a second wgpu 26 device,
using an unsafe raw-handle bridge, patching Slint's renderer, or migrating the production renderer
is outside this bounded probe and would violate its one-device acceptance condition.

Slint marks the wgpu 26 integration unstable: it is feature-gated and may change or disappear in
a future minor release. This probe evaluates that documented 1.13.1 API exactly; it does not claim
a stable compatibility contract across Slint releases.
