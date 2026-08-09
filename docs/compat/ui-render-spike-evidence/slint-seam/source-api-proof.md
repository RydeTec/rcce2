# Registry source/API proof

Inspected registry packages: `slint-1.13.1`, `i-slint-core-1.13.1`,
`i-slint-backend-selector-1.13.1`, and `i-slint-backend-winit-1.13.1`.

- `slint/lib.rs:446-464` exposes the supported `slint::wgpu_26` integration and documents both
  `WGPUConfiguration::Manual` and `Image::try_from<wgpu::Texture>()` as the same-device paths.
- `slint/lib.rs:540` re-exports the wgpu 26 API module.
- `i-slint-core/graphics/wgpu_26.rs:13` re-exports its `wgpu_26` dependency as `wgpu`.
- `i-slint-core/graphics/wgpu_26.rs:80-92` defines `WGPUConfiguration::Manual` with wgpu 26
  `Instance`, `Adapter`, `Device`, and `Queue` fields. The negative fixture constructs this actual
  public variant with the corresponding wgpu 22 resources.
- `i-slint-core/graphics/wgpu_26.rs:104-122` implements `TryFrom<wgpu_26::Texture>` for
  `slint::Image`. The negative fixture calls the same API with a wgpu 22 texture.
- `i-slint-backend-selector/api.rs:114-130` defines the documented
  `BackendSelector::require_wgpu_26` entry point. Both the positive and negative fixtures thread
  their manual configurations through this function.
- `i-slint-backend-winit/lib.rs:385-400,443-450` binds a requested WGPU26 configuration to the
  enabled FemtoVG-wgpu renderer path. The probe manifest also enables winit accessibility support.
- normalized `slint/Cargo.toml:158-162` binds `unstable-wgpu-26` to `dep:wgpu-26`.
- `rcce-render/src/world_view.rs:215` defines `WorldView::new` with the workspace's
  `&wgpu::Device` and `wgpu::TextureFormat`; the probe lock resolves that path to wgpu 22.1.0.

SHA-256 source bindings:

```text
f780cdaa40348fd52957e466af869c3a3b78d2d4e9467b651dc4463d8625cafa  slint-1.13.1/Cargo.toml
2379e0ae0ec1cd5bec8a9420ff6df23970e50ac669eb0196eaac570d772244cb  slint-1.13.1/lib.rs
23b9cb4f41a92fd13bf22d4a2d48c8496181a878123ec6ed97c32fd53543a91b  i-slint-core-1.13.1/graphics/wgpu_26.rs
c6a24ecf9f090f27d99f57fc8752f33256ccc7058ff3955bc390a5c33c8277f3  i-slint-backend-selector-1.13.1/api.rs
6ff14d8cc73043eebb19a8e2e5fd560f37e0591595c3353e5fbaf99340a26edb  i-slint-backend-winit-1.13.1/Cargo.toml
2a64d617cef7ee9a4b00b15e0e915df3383ab96eeece2811e06d7a539a017354  i-slint-backend-winit-1.13.1/lib.rs
43194571cdb19b3ec0fca3a6087beafce25183858f7abd7be3af4e7f790b3a69  wgpu-26.0.1/Cargo.toml
52c1906d42f5a1ff88e8ab4572d1767252ba6bd0e2f832f3975384ca2c4c4ed2  wgpu-22.1.0/Cargo.toml
0b00639860b398a7eac5e1f468f136e7d15da5c8bd9c16ef5d2f0f1f4b311038  client-rs/crates/rcce-render/src/world_view.rs
```

The compile-fail fixture is the stronger proof: rustc independently resolves the concrete types
and rejects the four manual-configuration resources before the supported backend selector can be
formed, plus the texture import.

The wgpu 26 API is explicitly unstable in Slint 1.13.1: its source says the feature may be removed
or changed in a future minor release. This evidence describes the exact 1.13.1 surface only.
