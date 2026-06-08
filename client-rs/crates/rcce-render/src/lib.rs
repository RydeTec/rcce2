//! wgpu renderer for the RCCE2 client. Headless-capable so frames can be
//! produced and verified without an interactive window.
//!
//! Step 1 (this commit): prove wgpu builds for `i686-pc-windows-msvc` and finds
//! a GPU adapter. Real offscreen rendering of the world state follows once the
//! target is confirmed viable.

pub mod bloom;
pub mod font;
pub mod gpu;
pub mod overlay;
pub mod render;
pub mod render3d;
pub mod scene;
pub mod world_view;
pub use overlay::{project, unproject_ground, Overlay};
pub use render::{render_markers_png, Marker};
pub use render3d::render_model_png;
pub use scene::{render_scene_png, render_skinned_png, SceneInstance};
pub use world_view::{save_texture_png, view_proj, SkinnedInstance, WorldView};

use pollster::block_on;

/// Enumerate the GPU and return a human-readable description of the adapter
/// wgpu would render with, or an error if none is available.
pub fn probe_gpu() -> Result<String, String> {
    let instance = wgpu::Instance::default();
    let adapter = block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
        power_preference: wgpu::PowerPreference::HighPerformance,
        force_fallback_adapter: false,
        compatible_surface: None,
    }))
    .ok_or_else(|| "no GPU adapter found".to_string())?;

    let info = adapter.get_info();
    // Actually open a device too — adapter enumeration can succeed where device
    // creation fails, so this is the real "can we render here" check.
    let (_device, _queue) = block_on(adapter.request_device(
        &wgpu::DeviceDescriptor {
            label: Some("rcce-render probe"),
            required_features: wgpu::Features::empty(),
            required_limits: wgpu::Limits::downlevel_defaults()
                .using_resolution(adapter.limits()),
            memory_hints: wgpu::MemoryHints::Performance,
        },
        None,
    ))
    .map_err(|e| format!("request_device failed: {e}"))?;

    Ok(format!(
        "adapter='{}' backend={:?} type={:?} driver='{}'",
        info.name, info.backend, info.device_type, info.driver
    ))
}
