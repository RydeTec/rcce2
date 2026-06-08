//! Offscreen 3D scene renderer: many model instances (each at a world
//! position/rotation/scale, with per-mesh textures and a fallback tint) plus a
//! ground plane, from an explicit camera, to a PNG. Shares the textured,
//! fog-aware pipeline with the real-time window via [`crate::gpu`] — so the
//! headless PNG and the live window render identically.

use std::io::BufWriter;

use glam::{Mat4, Vec3};
use pollster::block_on;

use rcce_data::{B3dModel, Image};

use crate::gpu::{self, Pipeline, Uniforms};

/// One model placed in the world. `textures` aligns to `model.meshes`.
pub struct SceneInstance<'a> {
    pub model: &'a B3dModel,
    pub textures: &'a [Option<Image>],
    /// Per-mesh baked lightmaps (aligns to `model.meshes`), multiplied onto the
    /// base texture. Pass `&[]` for non-lightmapped instances (actors, most
    /// scenery) — each mesh then gets a grey no-op default.
    pub lightmaps: &'a [Option<Image>],
    pub translation: [f32; 3],
    /// Pitch, yaw, roll in radians (X, Y, Z). Actors use `[0, yaw, 0]`;
    /// scenery carries all three from the area file.
    pub rot: [f32; 3],
    /// Per-axis world scale. Actors pass `[s, s, s]`; scenery is non-uniform.
    pub scale: [f32; 3],
    /// Fallback/tint colour (multiplied with the texture; shows through where a
    /// mesh has no texture).
    pub color: [f32; 3],
}

/// Render the scene to a PNG with distance fog. `eye`/`target` define the
/// camera; `fog_color` is also the sky/clear colour.
#[allow(clippy::too_many_arguments)]
/// A 1×1 depth texture + comparison sampler for the offscreen path's shadow-map
/// bindings (the scene shader requires them). Paired with [`no_shadow_vp`] — the
/// degenerate light matrix below keeps the shadow test out-of-bounds, so this
/// texture is never actually sampled.
fn default_shadow(device: &wgpu::Device) -> (wgpu::TextureView, wgpu::Sampler) {
    let tex = device
        .create_texture(&wgpu::TextureDescriptor {
            label: Some("shadow-default"),
            size: wgpu::Extent3d { width: 1, height: 1, depth_or_array_layers: 1 },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: gpu::DEPTH_FORMAT,
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::TEXTURE_BINDING,
            view_formats: &[],
        })
        .create_view(&Default::default());
    let samp = device.create_sampler(&wgpu::SamplerDescriptor {
        label: Some("shadow-default-cmp"),
        compare: Some(wgpu::CompareFunction::LessEqual),
        ..Default::default()
    });
    (tex, samp)
}

/// A degenerate light view-proj that maps every point to clip `(0,0,10,1)` →
/// `ndc.z = 10`, outside `[0,1]`, so the scene shader's shadow test always
/// returns "lit". Used by the offscreen PNG renderers (no real shadow map).
fn no_shadow_vp() -> [f32; 16] {
    let mut m = [0.0f32; 16];
    m[14] = 10.0; // w_axis.z → clip.z
    m[15] = 1.0; // w_axis.w → clip.w
    m
}

pub fn render_scene_png(
    instances: &[SceneInstance],
    eye: [f32; 3],
    target: [f32; 3],
    ground_y: f32,
    fog_color: [f32; 3],
    fog_near: f32,
    fog_far: f32,
    ambient: [f32; 3],
    light_dir: [f32; 3],
    width: u32,
    height: u32,
    path: &str,
    // Optional sky texture (w, h, RGBA8) for the textured skydome; None keeps
    // the plain gradient.
    sky_tex: Option<(u32, u32, Vec<u8>)>,
    // Optional cloud texture (w, h, RGBA8 with alpha) for the cloud overlay.
    cloud_tex: Option<(u32, u32, Vec<u8>)>,
    // Optional night-stars texture + a night factor (0 day .. 1 deep night).
    stars_tex: Option<(u32, u32, Vec<u8>)>,
    night: f32,
) -> Result<String, String> {
    let aspect = width as f32 / height as f32;
    let proj = Mat4::perspective_rh(50f32.to_radians(), aspect, 1.0, 100_000.0);
    let view = Mat4::look_at_rh(Vec3::from(eye), Vec3::from(target), Vec3::Y);
    let uniforms = Uniforms::new(
        (proj * view).to_cols_array(),
        eye,
        fog_color,
        fog_near,
        fog_far,
        ambient,
        light_dir,
        no_shadow_vp(),
    );

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
            label: Some("scene"),
            required_features: wgpu::Features::empty(),
            required_limits: wgpu::Limits::downlevel_defaults()
                .using_resolution(adapter.limits()),
            memory_hints: wgpu::MemoryHints::Performance,
        },
        None,
    ))
    .map_err(|e| format!("request_device: {e}"))?;

    let color_format = gpu::COLOR_FORMAT;
    let target_tex = device.create_texture(&wgpu::TextureDescriptor {
        label: Some("color"),
        size: wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format: color_format,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
        view_formats: &[],
    });
    let color_view = target_tex.create_view(&Default::default());
    let depth = device.create_texture(&wgpu::TextureDescriptor {
        label: Some("depth"),
        size: wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format: gpu::DEPTH_FORMAT,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
        view_formats: &[],
    });
    let depth_view = depth.create_view(&Default::default());

    // Shared pipeline + per-instance drawables (with ground plane).
    let pipeline = Pipeline::new(&device, color_format, 1);
    let mut sky = gpu::SkyPipeline::new(&device, color_format, 1);
    sky.set_colors(&queue, gpu::sky_zenith(fog_color), fog_color);
    if let Some((w, h, rgba)) = &sky_tex {
        sky.set_texture(&device, &queue, *w, *h, rgba);
    }
    if let Some((w, h, rgba)) = &cloud_tex {
        sky.set_cloud_texture(&device, &queue, *w, *h, rgba);
    }
    if let Some((w, h, rgba)) = &stars_tex {
        sky.set_stars_texture(&device, &queue, *w, *h, rgba);
    }
    sky.set_frame(&queue, 0.0, 0.0, night); // still image → no yaw pan / drift
    // World-fixed sky needs the camera unprojection (else the ray is degenerate).
    let inv_vp = (proj * view).inverse().to_cols_array();
    sky.set_camera(&queue, &inv_vp, eye, light_dir);
    let ubuf = device.create_buffer_init_uniform(&uniforms);
    let (sh_tex, sh_samp) = default_shadow(&device);
    let bind0 = device.create_bind_group(&wgpu::BindGroupDescriptor {
        label: None,
        layout: &pipeline.bgl_uniform,
        entries: &[
            wgpu::BindGroupEntry { binding: 0, resource: ubuf.as_entire_binding() },
            wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::TextureView(&sh_tex) },
            wgpu::BindGroupEntry { binding: 2, resource: wgpu::BindingResource::Sampler(&sh_samp) },
        ],
    });
    let drawables = gpu::build_drawables(&device, &queue, &pipeline, instances, ground_y, &mut gpu::TexCache::new());
    if drawables.is_empty() {
        return Err("empty scene".into());
    }

    let bpp = 4u32;
    let unpadded = width * bpp;
    let padded = unpadded.div_ceil(256) * 256;
    let readback = device.create_buffer(&wgpu::BufferDescriptor {
        label: Some("rb"),
        size: (padded * height) as u64,
        usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
        mapped_at_creation: false,
    });

    let mut enc = device.create_command_encoder(&Default::default());
    {
        let mut rp = enc.begin_render_pass(&wgpu::RenderPassDescriptor {
            label: Some("pass"),
            color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                view: &color_view,
                resolve_target: None,
                ops: wgpu::Operations {
                    load: wgpu::LoadOp::Clear(wgpu::Color {
                        r: fog_color[0] as f64,
                        g: fog_color[1] as f64,
                        b: fog_color[2] as f64,
                        a: 1.0,
                    }),
                    store: wgpu::StoreOp::Store,
                },
            })],
            depth_stencil_attachment: Some(wgpu::RenderPassDepthStencilAttachment {
                view: &depth_view,
                depth_ops: Some(wgpu::Operations {
                    load: wgpu::LoadOp::Clear(1.0),
                    store: wgpu::StoreOp::Store,
                }),
                stencil_ops: None,
            }),
            timestamp_writes: None,
            occlusion_query_set: None,
        });
        sky.draw(&mut rp); // gradient sky behind the world
        rp.set_pipeline(&pipeline.pipeline);
        rp.set_bind_group(0, &bind0, &[]);
        for d in &drawables {
            rp.set_bind_group(1, &d.tex_bind, &[]);
            rp.set_vertex_buffer(0, d.vbuf.slice(..));
            rp.set_index_buffer(d.ibuf.slice(..), wgpu::IndexFormat::Uint32);
            rp.draw_indexed(0..d.n_idx, 0, 0..1);
        }
    }
    enc.copy_texture_to_buffer(
        wgpu::ImageCopyTexture {
            texture: &target_tex,
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
        wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
    );
    queue.submit(Some(enc.finish()));

    let slice = readback.slice(..);
    let (tx, rx) = std::sync::mpsc::channel();
    slice.map_async(wgpu::MapMode::Read, move |r| {
        let _ = tx.send(r);
    });
    device.poll(wgpu::Maintain::Wait);
    rx.recv().map_err(|e| e.to_string())?.map_err(|e| format!("map: {e:?}"))?;
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
    let mut w = encoder.write_header().map_err(|e| e.to_string())?;
    w.write_image_data(&rgba).map_err(|e| e.to_string())?;

    Ok(adapter_name)
}

/// Render ONE actor via the GPU linear-blend-skinning pipeline (the static mesh
/// is uploaded once; the pose comes from the per-frame bone palette in the
/// vertex shader). For verifying the GPU path against the CPU `posed_meshes`
/// result — the rendered pose must match.
#[allow(clippy::too_many_arguments)]
pub fn render_skinned_png(
    model: &B3dModel,
    textures: &[Option<Image>],
    frame: Option<f32>,
    translation: [f32; 3],
    rot: [f32; 3],
    scale: [f32; 3],
    color: [f32; 3],
    eye: [f32; 3],
    target: [f32; 3],
    fog_color: [f32; 3],
    fog_near: f32,
    fog_far: f32,
    ambient: [f32; 3],
    light_dir: [f32; 3],
    width: u32,
    height: u32,
    path: &str,
) -> Result<String, String> {
    use wgpu::util::DeviceExt;
    let aspect = width as f32 / height as f32;
    let proj = Mat4::perspective_rh(50f32.to_radians(), aspect, 1.0, 100_000.0);
    let view = Mat4::look_at_rh(Vec3::from(eye), Vec3::from(target), Vec3::Y);
    let uniforms = Uniforms::new((proj * view).to_cols_array(), eye, fog_color, fog_near, fog_far, ambient, light_dir, no_shadow_vp());

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
            label: Some("skin"),
            required_features: wgpu::Features::empty(),
            required_limits: wgpu::Limits::downlevel_defaults()
                .using_resolution(adapter.limits()),
            memory_hints: wgpu::MemoryHints::Performance,
        },
        None,
    ))
    .map_err(|e| format!("request_device: {e}"))?;

    let color_format = gpu::COLOR_FORMAT;
    let target_tex = device.create_texture(&wgpu::TextureDescriptor {
        label: Some("color"),
        size: wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format: color_format,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
        view_formats: &[],
    });
    let color_view = target_tex.create_view(&Default::default());
    let depth = device.create_texture(&wgpu::TextureDescriptor {
        label: Some("depth"),
        size: wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format: gpu::DEPTH_FORMAT,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
        view_formats: &[],
    });
    let depth_view = depth.create_view(&Default::default());

    let pipeline = Pipeline::new(&device, color_format, 1);
    let skin = gpu::SkinPipeline::new(&device, color_format, &pipeline, 1);
    let ubuf = device.create_buffer_init_uniform(&uniforms);
    let (sh_tex, sh_samp) = default_shadow(&device);
    let bind0 = device.create_bind_group(&wgpu::BindGroupDescriptor {
        label: None,
        layout: &pipeline.bgl_uniform,
        entries: &[
            wgpu::BindGroupEntry { binding: 0, resource: ubuf.as_entire_binding() },
            wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::TextureView(&sh_tex) },
            wgpu::BindGroupEntry { binding: 2, resource: wgpu::BindingResource::Sampler(&sh_samp) },
        ],
    });

    // Instance model matrix (Y·X·Z rotation, matching inst_nrot) and the
    // per-actor skinning uniform (column-major bone palette).
    let rotm = Mat4::from_rotation_y(rot[1]) * Mat4::from_rotation_x(rot[0]) * Mat4::from_rotation_z(rot[2]);
    let model_mat = (Mat4::from_translation(Vec3::from(translation)) * rotm * Mat4::from_scale(Vec3::from(scale))).to_cols_array();
    let palette = model.bone_palette(frame);
    let actor = gpu::ActorSkin::new(&palette, model_mat, color);
    let (abuf, abind) = skin.make_actor(&device);
    skin.update_actor(&queue, &abuf, &actor);

    // Static skinned geometry: one vbuf + ibuf + texture per mesh (uploaded once).
    let (bone_ids, weights) = model.skin_attributes();
    struct M {
        vbuf: wgpu::Buffer,
        ibuf: wgpu::Buffer,
        n_idx: u32,
        tex: wgpu::BindGroup,
    }
    let mut meshes: Vec<M> = Vec::new();
    for (mi, mesh) in model.meshes.iter().enumerate() {
        if mesh.positions.is_empty() || mesh.indices.is_empty() {
            continue;
        }
        let vbuf = gpu::build_skinned_vbuf(&device, mesh, &bone_ids, &weights);
        let ibuf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("skin-i"),
            contents: bytemuck::cast_slice(&mesh.indices),
            usage: wgpu::BufferUsages::INDEX,
        });
        let tex = pipeline.texture_bind(&device, &queue, textures.get(mi).and_then(|t| t.as_ref()));
        meshes.push(M { vbuf, ibuf, n_idx: mesh.indices.len() as u32, tex });
    }
    if meshes.is_empty() {
        return Err("no skinned meshes".into());
    }

    let bpp = 4u32;
    let unpadded = width * bpp;
    let padded = unpadded.div_ceil(256) * 256;
    let readback = device.create_buffer(&wgpu::BufferDescriptor {
        label: Some("rb"),
        size: (padded * height) as u64,
        usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
        mapped_at_creation: false,
    });

    let mut enc = device.create_command_encoder(&Default::default());
    {
        let mut rp = enc.begin_render_pass(&wgpu::RenderPassDescriptor {
            label: Some("skin-pass"),
            color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                view: &color_view,
                resolve_target: None,
                ops: wgpu::Operations {
                    load: wgpu::LoadOp::Clear(wgpu::Color {
                        r: fog_color[0] as f64,
                        g: fog_color[1] as f64,
                        b: fog_color[2] as f64,
                        a: 1.0,
                    }),
                    store: wgpu::StoreOp::Store,
                },
            })],
            depth_stencil_attachment: Some(wgpu::RenderPassDepthStencilAttachment {
                view: &depth_view,
                depth_ops: Some(wgpu::Operations { load: wgpu::LoadOp::Clear(1.0), store: wgpu::StoreOp::Store }),
                stencil_ops: None,
            }),
            timestamp_writes: None,
            occlusion_query_set: None,
        });
        rp.set_pipeline(&skin.pipeline);
        rp.set_bind_group(0, &bind0, &[]);
        rp.set_bind_group(2, &abind, &[]);
        for m in &meshes {
            rp.set_bind_group(1, &m.tex, &[]);
            rp.set_vertex_buffer(0, m.vbuf.slice(..));
            rp.set_index_buffer(m.ibuf.slice(..), wgpu::IndexFormat::Uint32);
            rp.draw_indexed(0..m.n_idx, 0, 0..1);
        }
    }
    enc.copy_texture_to_buffer(
        wgpu::ImageCopyTexture { texture: &target_tex, mip_level: 0, origin: wgpu::Origin3d::ZERO, aspect: wgpu::TextureAspect::All },
        wgpu::ImageCopyBuffer {
            buffer: &readback,
            layout: wgpu::ImageDataLayout { offset: 0, bytes_per_row: Some(padded), rows_per_image: Some(height) },
        },
        wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
    );
    queue.submit(Some(enc.finish()));

    let slice = readback.slice(..);
    let (tx, rx) = std::sync::mpsc::channel();
    slice.map_async(wgpu::MapMode::Read, move |r| {
        let _ = tx.send(r);
    });
    device.poll(wgpu::Maintain::Wait);
    rx.recv().map_err(|e| e.to_string())?.map_err(|e| format!("map: {e:?}"))?;
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
    let mut w = encoder.write_header().map_err(|e| e.to_string())?;
    w.write_image_data(&rgba).map_err(|e| e.to_string())?;
    Ok(adapter_name)
}

/// Small helper: a uniform buffer initialised with `u`.
trait DeviceUniformExt {
    fn create_buffer_init_uniform(&self, u: &Uniforms) -> wgpu::Buffer;
}
impl DeviceUniformExt for wgpu::Device {
    fn create_buffer_init_uniform(&self, u: &Uniforms) -> wgpu::Buffer {
        use wgpu::util::DeviceExt;
        self.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("u"),
            contents: bytemuck::bytes_of(u),
            usage: wgpu::BufferUsages::UNIFORM,
        })
    }
}
