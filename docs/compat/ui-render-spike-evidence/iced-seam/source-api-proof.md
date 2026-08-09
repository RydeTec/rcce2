# Registry source/API proof

Inspected registry package: `iced_wgpu-0.13.5`.

- `src/lib.rs:53` publicly re-exports its `wgpu` dependency: `pub use wgpu;`.
- `src/engine.rs:22-28` defines `Engine::new` with
  `&wgpu::Adapter`, `&wgpu::Device`, `&wgpu::Queue`, `wgpu::TextureFormat`, and
  `Option<Antialiasing>`.
- `src/lib.rs:112-123` defines `Renderer::present` with its `&wgpu::Device`,
  `&wgpu::Queue`, `&mut wgpu::CommandEncoder`, and `&wgpu::TextureView`. The negative fixture
  calls this actual renderer method with the corresponding wgpu 22 resources.
- normalized `Cargo.toml:90-91` declares `[dependencies.wgpu] version = "0.19"`.
- `rcce-render/src/world_view.rs:215` defines `WorldView::new` with the workspace's
  `&wgpu::Device` and `wgpu::TextureFormat`; the probe lock resolves that path to wgpu 22.1.0.

SHA-256 registry-source bindings:

```text
93693d16e20579a177274594a1ce4088d885eb4c77ce01723e3f0b332a6f263f  iced_wgpu-0.13.5/src/lib.rs
195200dbd6b2d7c3ce87e2808af81ae997758a760a3650c81d99c60348e5b798  iced_wgpu-0.13.5/src/engine.rs
543153a73e8ceba8a0fb5bbff2966e818e7773f32905ae627aaab735ab8550d1  iced_wgpu-0.13.5/Cargo.toml
```

The compile-fail fixture is the stronger proof: rustc independently resolves the concrete types
and reports that they come from two different versions of `wgpu`.
