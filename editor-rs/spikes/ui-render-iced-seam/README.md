# Iced 0.13.1 one-device seam probe

This disposable crate tests one question: can iced 0.13.1's normal wgpu renderer consume the
existing adapter, device, queue, and caller-owned render texture used by `rcce-render` 0.1.0?

It deliberately does not create a GPU instance or device. The positive contract proves that
`WorldView::new` consumes wgpu 22.1.0. The compile-fail contract passes those exact resource types
to iced's renderer API and preserves rustc's nominal-type diagnostics. The runtime `TypeId`
checks independently show that the adapter/device/queue/texture-view types differ.

The candidate is rejected if that boundary does not compile. Creating a second wgpu 0.19 device,
using an unsafe raw-handle bridge, patching iced's renderer, or migrating the production renderer
is outside this bounded probe and would violate its one-device acceptance condition.
