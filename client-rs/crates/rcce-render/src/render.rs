//! Offscreen top-down renderer: draws world markers through a real wgpu
//! pipeline and writes a PNG. This proves the GPU path end-to-end and gives a
//! visual of the live game state without needing an interactive window. The
//! 3D world renderer replaces this top-down view later; the wgpu plumbing
//! (device, pipeline, vertex buffer, offscreen target, readback) is the same.

use std::io::BufWriter;

use bytemuck::{Pod, Zeroable};
use pollster::block_on;
use wgpu::util::DeviceExt;

/// A thing to draw on the map, in world X/Z coordinates.
pub struct Marker {
    pub x: f32,
    pub z: f32,
    /// Half-size of the square, in world units.
    pub size: f32,
    pub color: [f32; 3],
}

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct Vertex {
    pos: [f32; 2], // NDC
    color: [f32; 3],
}

const SHADER: &str = r#"
struct VsOut { @builtin(position) pos: vec4<f32>, @location(0) color: vec3<f32> };
@vertex fn vs(@location(0) pos: vec2<f32>, @location(1) color: vec3<f32>) -> VsOut {
    var o: VsOut;
    o.pos = vec4<f32>(pos, 0.0, 1.0);
    o.color = color;
    return o;
}
@fragment fn fs(in: VsOut) -> @location(0) vec4<f32> { return vec4<f32>(in.color, 1.0); }
"#;

/// Render `markers` (centered on `center` world coord, showing a `span`-unit
/// window) to a `width`x`height` PNG at `path`. Returns the adapter name used.
pub fn render_markers_png(
    markers: &[Marker],
    center: (f32, f32),
    span: f32,
    width: u32,
    height: u32,
    path: &str,
) -> Result<String, String> {
    // World X/Z → NDC. Z maps to screen Y (flipped so +Z is "up" on the map).
    let half = (span * 0.5).max(1.0);
    let aspect = width as f32 / height as f32;
    let to_ndc = |wx: f32, wz: f32| -> [f32; 2] {
        let nx = (wx - center.0) / (half * aspect);
        let ny = -(wz - center.1) / half;
        [nx, ny]
    };

    // Two triangles per marker.
    let mut verts: Vec<Vertex> = Vec::with_capacity(markers.len() * 6);
    for m in markers {
        let c0 = to_ndc(m.x - m.size, m.z - m.size);
        let c1 = to_ndc(m.x + m.size, m.z - m.size);
        let c2 = to_ndc(m.x + m.size, m.z + m.size);
        let c3 = to_ndc(m.x - m.size, m.z + m.size);
        for p in [c0, c1, c2, c0, c2, c3] {
            verts.push(Vertex {
                pos: p,
                color: m.color,
            });
        }
    }
    if verts.is_empty() {
        // Avoid an empty draw; push one degenerate offscreen tri.
        verts.push(Vertex {
            pos: [2.0, 2.0],
            color: [0.0; 3],
        });
        verts.push(Vertex {
            pos: [2.0, 2.0],
            color: [0.0; 3],
        });
        verts.push(Vertex {
            pos: [2.0, 2.0],
            color: [0.0; 3],
        });
    }

    let instance = wgpu::Instance::default();
    let adapter = block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
        power_preference: wgpu::PowerPreference::HighPerformance,
        force_fallback_adapter: false,
        compatible_surface: None,
    }))
    .ok_or("no GPU adapter")?;
    let adapter_name = adapter.get_info().name;
    let (device, queue) = block_on(adapter.request_device(
        &wgpu::DeviceDescriptor {
            label: Some("rcce-render"),
            required_features: wgpu::Features::empty(),
            required_limits: wgpu::Limits::downlevel_defaults()
                .using_resolution(adapter.limits()),
            memory_hints: wgpu::MemoryHints::Performance,
        },
        None,
    ))
    .map_err(|e| format!("request_device: {e}"))?;

    let format = wgpu::TextureFormat::Rgba8Unorm;
    let target = device.create_texture(&wgpu::TextureDescriptor {
        label: Some("target"),
        size: wgpu::Extent3d {
            width,
            height,
            depth_or_array_layers: 1,
        },
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
        view_formats: &[],
    });
    let view = target.create_view(&Default::default());

    let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
        label: Some("markers"),
        source: wgpu::ShaderSource::Wgsl(SHADER.into()),
    });
    let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
        label: None,
        bind_group_layouts: &[],
        push_constant_ranges: &[],
    });
    let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
        label: Some("markers"),
        layout: Some(&layout),
        vertex: wgpu::VertexState {
            module: &shader,
            entry_point: "vs",
            compilation_options: Default::default(),
            buffers: &[wgpu::VertexBufferLayout {
                array_stride: std::mem::size_of::<Vertex>() as u64,
                step_mode: wgpu::VertexStepMode::Vertex,
                attributes: &wgpu::vertex_attr_array![0 => Float32x2, 1 => Float32x3],
            }],
        },
        fragment: Some(wgpu::FragmentState {
            module: &shader,
            entry_point: "fs",
            compilation_options: Default::default(),
            targets: &[Some(format.into())],
        }),
        primitive: wgpu::PrimitiveState::default(),
        depth_stencil: None,
        multisample: wgpu::MultisampleState::default(),
        multiview: None,
        cache: None,
    });

    let vbuf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
        label: Some("verts"),
        contents: bytemuck::cast_slice(&verts),
        usage: wgpu::BufferUsages::VERTEX,
    });

    // Readback buffer (rows padded to 256).
    let bpp = 4u32;
    let unpadded = width * bpp;
    let padded = unpadded.div_ceil(256) * 256;
    let readback = device.create_buffer(&wgpu::BufferDescriptor {
        label: Some("readback"),
        size: (padded * height) as u64,
        usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
        mapped_at_creation: false,
    });

    let mut enc = device.create_command_encoder(&Default::default());
    {
        let mut rp = enc.begin_render_pass(&wgpu::RenderPassDescriptor {
            label: Some("pass"),
            color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                view: &view,
                resolve_target: None,
                ops: wgpu::Operations {
                    load: wgpu::LoadOp::Clear(wgpu::Color {
                        r: 0.06,
                        g: 0.10,
                        b: 0.16,
                        a: 1.0,
                    }),
                    store: wgpu::StoreOp::Store,
                },
            })],
            depth_stencil_attachment: None,
            timestamp_writes: None,
            occlusion_query_set: None,
        });
        rp.set_pipeline(&pipeline);
        rp.set_vertex_buffer(0, vbuf.slice(..));
        rp.draw(0..verts.len() as u32, 0..1);
    }
    enc.copy_texture_to_buffer(
        wgpu::ImageCopyTexture {
            texture: &target,
            mip_level: 0,
            origin: wgpu::Origin3d::ZERO,
            aspect: wgpu::TextureAspect::All,
        },
        wgpu::ImageCopyBuffer {
            buffer: &readback,
            layout: wgpu::ImageDataLayout {
                offset: 0,
                bytes_per_row: Some(padded),
                rows_per_image: Some(height),
            },
        },
        wgpu::Extent3d {
            width,
            height,
            depth_or_array_layers: 1,
        },
    );
    queue.submit(Some(enc.finish()));

    // Map + read.
    let slice = readback.slice(..);
    let (tx, rx) = std::sync::mpsc::channel();
    slice.map_async(wgpu::MapMode::Read, move |r| {
        let _ = tx.send(r);
    });
    device.poll(wgpu::Maintain::Wait);
    rx.recv()
        .map_err(|e| e.to_string())?
        .map_err(|e| format!("map: {e:?}"))?;
    let data = slice.get_mapped_range();
    let mut rgba = Vec::with_capacity((unpadded * height) as usize);
    for row in 0..height {
        let start = (row * padded) as usize;
        rgba.extend_from_slice(&data[start..start + unpadded as usize]);
    }
    drop(data);
    readback.unmap();

    let file = std::fs::File::create(path).map_err(|e| e.to_string())?;
    let mut encoder = png::Encoder::new(BufWriter::new(file), width, height);
    encoder.set_color(png::ColorType::Rgba);
    encoder.set_depth(png::BitDepth::Eight);
    let mut writer = encoder.write_header().map_err(|e| e.to_string())?;
    writer
        .write_image_data(&rgba)
        .map_err(|e| e.to_string())?;

    Ok(adapter_name)
}
