//! Real-time scene renderer for a window surface. Owns the pipeline, a depth
//! buffer, and a per-frame camera uniform; the scene drawables are uploaded
//! once (or whenever the scene changes) and re-drawn each frame with an updated
//! view-projection. Shares the textured pipeline with the offscreen PNG path
//! via [`crate::gpu`], so both look identical.

use std::collections::HashMap;
use std::rc::Rc;

use wgpu::util::DeviceExt;

use rcce_data::{B3dModel, Image};

use crate::gpu::{self, ActorSkin, Drawable, IndexCache, Pipeline, SkinPipeline, SkyPipeline, TexCache, Uniforms};
use crate::scene::SceneInstance;

/// A skinned actor instance for the GPU linear-blend-skinning path. The static
/// mesh is uploaded once (keyed by `key`); the pose comes from `frame`.
pub struct SkinnedInstance<'a> {
    /// Appearance key (e.g. `tmpl:gender`) — keys the cached static geometry.
    pub key: &'a str,
    /// The actor template model (with bones + skin weights).
    pub model: &'a B3dModel,
    /// Per-mesh textures (aligns to `model.meshes`).
    pub textures: &'a [Option<Image>],
    /// Animation frame (None = bind pose).
    pub frame: Option<f32>,
    /// Column-major instance model matrix (world transform).
    pub transform: [f32; 16],
    /// Tint colour (multiplied with the texture).
    pub color: [f32; 3],
}

/// One skinned mesh draw: shared static geometry + the per-actor uniform slot.
struct SkinnedDrawable {
    vbuf: Rc<wgpu::Buffer>,
    ibuf: Rc<wgpu::Buffer>,
    n_idx: u32,
    tex: Rc<wgpu::BindGroup>,
    actor: usize, // index into actor_pool
}

/// The six view-frustum planes (left, right, bottom, top, near, far) as
/// `[a, b, c, d]` with the normal pointing INTO the frustum and normalised, so
/// the signed distance of a point is `a*x + b*y + c*z + d`. Extracted (Gribb–
/// Hartmann) from a column-major view-projection matrix where `clip = vp*world`,
/// so `clip.<axis> == m.row(axis) · world`. wgpu/D3D clip volume: x,y ∈ [-w,w],
/// z ∈ [0,w] — hence the near plane is `row2` (z ≥ 0), not `row3 + row2`.
fn frustum_planes(view_proj: &[f32; 16]) -> [[f32; 4]; 6] {
    let m = glam::Mat4::from_cols_array(view_proj);
    let (r0, r1, r2, r3) = (m.row(0), m.row(1), m.row(2), m.row(3));
    let raw = [r3 + r0, r3 - r0, r3 + r1, r3 - r1, r2, r3 - r2];
    let mut planes = [[0.0f32; 4]; 6];
    for (i, p) in raw.iter().enumerate() {
        let len = glam::Vec3::new(p.x, p.y, p.z).length().max(1e-20);
        planes[i] = [p.x / len, p.y / len, p.z / len, p.w / len];
    }
    planes
}

/// Conservative sphere-vs-frustum test: returns `false` only when the sphere is
/// fully outside at least one plane. Never wrongly culls a visible object (a
/// sphere straddling a plane stays in), so the rendered image is unchanged.
fn sphere_in_frustum(planes: &[[f32; 4]; 6], c: &[f32; 3], r: f32) -> bool {
    planes
        .iter()
        .all(|p| p[0] * c[0] + p[1] * c[1] + p[2] * c[2] + p[3] >= -r)
}

pub struct WorldView {
    pipeline: Pipeline,
    skin: SkinPipeline,
    sky: SkyPipeline,
    /// Surface colour format (for offscreen screenshot captures).
    color_format: wgpu::TextureFormat,
    uniform_buf: wgpu::Buffer,
    bind0: wgpu::BindGroup,
    depth: wgpu::TextureView,
    /// MSAA sample count for the world pass (1 = off). The shadow pass is always 1×.
    sample_count: u32,
    /// Multisampled colour target (present only when `sample_count > 1`); resolved
    /// into the surface view each frame.
    msaa_color: Option<wgpu::TextureView>,
    /// Sun shadow map: a depth-only pipeline renders casters from the light's POV
    /// into `shadow_tex`, which the scene shader then PCF-samples (via `bind0`).
    shadow_pipeline: gpu::ShadowPipeline,
    /// Depth-only skinned caster — lets GPU-skinned actors cast shadows too.
    skin_shadow_pipeline: gpu::SkinShadowPipeline,
    shadow_tex: wgpu::TextureView,
    light_buf: wgpu::Buffer,
    light_bind: wgpu::BindGroup,
    /// Static geometry (terrain/scenery), uploaded once.
    statics: Vec<Drawable>,
    /// Water surfaces — rebuilt per frame with a scrolling UV offset so the
    /// surface animates (Blitz `PositionTexture(U, V)`). Drawn in the alpha pass.
    water: Vec<Drawable>,
    /// Per-frame geometry (actors), rebuilt as they move/animate.
    dynamics: Vec<Drawable>,
    /// Cached actor texture binds (keyed by appearance) so per-frame rebuilds
    /// reuse the upload instead of re-sending skins to the GPU every frame.
    tex_cache: TexCache,
    /// Cached constant index buffers (keyed like the textures) so per-frame
    /// rebuilds don't recreate the topology buffer each tick.
    idx_cache: IndexCache,
    /// Static skinned geometry per `key:mesh` (vbuf + ibuf + n_idx + texture),
    /// uploaded ONCE — the GPU skinning path poses it via the per-actor uniform.
    skin_static: HashMap<String, (Rc<wgpu::Buffer>, Rc<wgpu::Buffer>, u32, Rc<wgpu::BindGroup>)>,
    /// Reusable per-actor skinning uniform buffers + binds (grown as needed).
    actor_pool: Vec<(wgpu::Buffer, wgpu::BindGroup)>,
    /// This frame's skinned draws.
    skinned: Vec<SkinnedDrawable>,
    /// All of the zone's placed point lights (set on zone load); the nearest
    /// `MAX_LIGHTS` to the camera are uploaded each frame.
    zone_lights: Vec<gpu::PointLight>,
    /// Particle billboard pipeline + this frame's batches (rebuilt each frame).
    particle_pipeline: gpu::ParticlePipeline,
    particles: Vec<ParticleBatch>,
    /// Water surface pipeline (Fresnel sky-reflection + procedural ripples).
    water_pipeline: gpu::WaterPipeline,
    /// Optional bloom/glow post-process (RCCE_BLOOM; None by default → zero cost).
    bloom: Option<crate::bloom::Bloom>,
    /// Content-keyed GPU texture cache for the static scene + water. Statics dedup
    /// identical scenery textures (one upload, not one per instance); water — which
    /// is rebuilt every frame for its scrolling UV — reuses its upload instead of
    /// re-creating the texture (+ mip chain) every frame. Cleared on `set_scene`.
    static_tex_cache: TexCache,
}

/// One frame's particle geometry for an emitter: its texture bind + blend +
/// camera-facing billboard vertices.
struct ParticleBatch {
    tex_bind: wgpu::BindGroup,
    add: bool,
    vbuf: wgpu::Buffer,
    n: u32,
}

fn make_depth(device: &wgpu::Device, w: u32, h: u32, sample_count: u32) -> wgpu::TextureView {
    device
        .create_texture(&wgpu::TextureDescriptor {
            label: Some("depth"),
            size: wgpu::Extent3d { width: w.max(1), height: h.max(1), depth_or_array_layers: 1 },
            mip_level_count: 1,
            sample_count,
            dimension: wgpu::TextureDimension::D2,
            format: gpu::DEPTH_FORMAT,
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            view_formats: &[],
        })
        .create_view(&Default::default())
}

/// The multisampled colour target the world pass renders into when MSAA is on
/// (`sample_count > 1`); it is resolved into the single-sampled surface/screenshot
/// view at the end of the pass. `None` when MSAA is off (render direct to view).
fn make_msaa_color(device: &wgpu::Device, format: wgpu::TextureFormat, w: u32, h: u32, sample_count: u32) -> Option<wgpu::TextureView> {
    if sample_count <= 1 {
        return None;
    }
    Some(
        device
            .create_texture(&wgpu::TextureDescriptor {
                label: Some("msaa-color"),
                size: wgpu::Extent3d { width: w.max(1), height: h.max(1), depth_or_array_layers: 1 },
                mip_level_count: 1,
                sample_count,
                dimension: wgpu::TextureDimension::D2,
                format,
                usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
                view_formats: &[],
            })
            .create_view(&Default::default()),
    )
}

/// Anti-aliasing sample count from `RCCE_MSAA` (default 4×; `1` disables). Clamped
/// to {1,2,4} — the range the WebGPU spec guarantees for renderable formats like
/// Bgra8Unorm. 8× needs `TEXTURE_ADAPTER_SPECIFIC_FORMAT_FEATURES` (not requested),
/// so it would fail pipeline validation; we never return it.
fn msaa_sample_count() -> u32 {
    match std::env::var("RCCE_MSAA").ok().and_then(|s| s.trim().parse::<u32>().ok()) {
        Some(1) => 1,
        Some(2) => 2,
        _ => 4,
    }
}

impl WorldView {
    pub fn new(device: &wgpu::Device, color_format: wgpu::TextureFormat, w: u32, h: u32) -> WorldView {
        let sample_count = msaa_sample_count();
        let pipeline = Pipeline::new(device, color_format, sample_count);
        let skin = SkinPipeline::new(device, color_format, &pipeline, sample_count);
        let sky = SkyPipeline::new(device, color_format, sample_count);
        let particle_pipeline = gpu::ParticlePipeline::new(device, color_format, &pipeline.bgl_uniform, &pipeline.bgl_texture, sample_count);
        let water_pipeline = gpu::WaterPipeline::new(device, color_format, &pipeline.bgl_uniform, &pipeline.bgl_texture, sample_count);
        let uniform_buf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("u"),
            contents: bytemuck::bytes_of(&Uniforms::new(
                [0.0; 16], [0.0; 3], [0.0; 3], 1.0, 2.0, [0.5; 3], [0.0, 1.0, 0.0],
                glam::Mat4::IDENTITY.to_cols_array(),
            )),
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
        });
        // Sun shadow map: depth texture (rendered into + sampled) + a comparison
        // sampler (linear → hardware 2×2 PCF, on top of the shader's 3×3).
        let shadow_pipeline = gpu::ShadowPipeline::new(device, &pipeline.bgl_texture);
        let skin_shadow_pipeline = gpu::SkinShadowPipeline::new(device, &shadow_pipeline.bgl, &pipeline.bgl_texture, &skin.bgl_actor);
        let shadow_tex = device
            .create_texture(&wgpu::TextureDescriptor {
                label: Some("shadow-map"),
                size: wgpu::Extent3d { width: gpu::SHADOW_DIM, height: gpu::SHADOW_DIM, depth_or_array_layers: 1 },
                mip_level_count: 1,
                sample_count: 1,
                dimension: wgpu::TextureDimension::D2,
                format: gpu::DEPTH_FORMAT,
                usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::TEXTURE_BINDING,
                view_formats: &[],
            })
            .create_view(&Default::default());
        let shadow_sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            label: Some("shadow-cmp"),
            address_mode_u: wgpu::AddressMode::ClampToEdge,
            address_mode_v: wgpu::AddressMode::ClampToEdge,
            mag_filter: wgpu::FilterMode::Linear,
            min_filter: wgpu::FilterMode::Linear,
            compare: Some(wgpu::CompareFunction::LessEqual),
            ..Default::default()
        });
        let light_buf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("light-vp"),
            contents: bytemuck::cast_slice(&glam::Mat4::IDENTITY.to_cols_array()),
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
        });
        let light_bind = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("light"),
            layout: &shadow_pipeline.bgl,
            entries: &[wgpu::BindGroupEntry { binding: 0, resource: light_buf.as_entire_binding() }],
        });
        let bind0 = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: None,
            layout: &pipeline.bgl_uniform,
            entries: &[
                wgpu::BindGroupEntry { binding: 0, resource: uniform_buf.as_entire_binding() },
                wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::TextureView(&shadow_tex) },
                wgpu::BindGroupEntry { binding: 2, resource: wgpu::BindingResource::Sampler(&shadow_sampler) },
            ],
        });
        WorldView {
            pipeline,
            skin,
            sky,
            color_format,
            uniform_buf,
            bind0,
            depth: make_depth(device, w, h, sample_count),
            sample_count,
            msaa_color: make_msaa_color(device, color_format, w, h, sample_count),
            shadow_pipeline,
            skin_shadow_pipeline,
            shadow_tex,
            light_buf,
            light_bind,
            statics: Vec::new(),
            water: Vec::new(),
            dynamics: Vec::new(),
            tex_cache: TexCache::new(),
            idx_cache: IndexCache::new(),
            skin_static: HashMap::new(),
            actor_pool: Vec::new(),
            skinned: Vec::new(),
            zone_lights: Vec::new(),
            particle_pipeline,
            particles: Vec::new(),
            water_pipeline,
            bloom: crate::bloom::Bloom::maybe_new(device, color_format, w, h),
            static_tex_cache: TexCache::new(),
        }
    }

    /// Replace the zone's placed point lights (called on zone load).
    pub fn set_lights(&mut self, lights: &[gpu::PointLight]) {
        self.zone_lights = lights.to_vec();
    }

    /// Replace this frame's particle billboards: `(texture, additive?, verts)` per
    /// emitter. Verts are camera-facing quads (6 per particle). Rebuilt each frame.
    pub fn set_particles(&mut self, device: &wgpu::Device, queue: &wgpu::Queue, batches: &[(Option<Image>, bool, Vec<gpu::Vertex>)]) {
        self.particles.clear();
        for (img, add, verts) in batches {
            if verts.is_empty() {
                continue;
            }
            let vbuf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
                label: Some("particles"),
                contents: bytemuck::cast_slice(verts),
                usage: wgpu::BufferUsages::VERTEX,
            });
            let tex_bind = self.pipeline.texture_bind(device, queue, img.as_ref());
            self.particles.push(ParticleBatch { tex_bind, add: *add, vbuf, n: verts.len() as u32 });
        }
    }

    /// Replace the per-frame SKINNED actors (GPU linear-blend skinning). The
    /// static mesh for each appearance is built once and cached; per frame only
    /// each actor's small bone-palette uniform is written — no CPU re-skin or
    /// vertex re-upload. Pair with [`set_dynamic`](Self::set_dynamic) for the
    /// unskinned attachments (hair/gear).
    pub fn set_skinned(&mut self, device: &wgpu::Device, queue: &wgpu::Queue, instances: &[SkinnedInstance]) {
        self.skinned.clear();
        // Ensure a reusable uniform buffer/bind per actor instance.
        while self.actor_pool.len() < instances.len() {
            self.actor_pool.push(self.skin.make_actor(device));
        }
        for (ai, inst) in instances.iter().enumerate() {
            // Build + cache this appearance's static skinned meshes once.
            let first_key = format!("{}:0", inst.key);
            if !self.skin_static.contains_key(&first_key) {
                let (ids, wts) = inst.model.skin_attributes();
                for (mi, mesh) in inst.model.meshes.iter().enumerate() {
                    if mesh.positions.is_empty() || mesh.indices.is_empty() {
                        continue;
                    }
                    let vbuf = Rc::new(gpu::build_skinned_vbuf(device, mesh, &ids, &wts));
                    let ibuf = Rc::new(device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
                        label: Some("skin-i"),
                        contents: bytemuck::cast_slice(&mesh.indices),
                        usage: wgpu::BufferUsages::INDEX,
                    }));
                    let tex = Rc::new(self.pipeline.texture_bind(device, queue, inst.textures.get(mi).and_then(|t| t.as_ref())));
                    self.skin_static.insert(format!("{}:{}", inst.key, mi), (vbuf, ibuf, mesh.indices.len() as u32, tex));
                }
            }
            // Update this actor's pose uniform (palette + model + colour).
            let skin = ActorSkin::new(&inst.model.bone_palette(inst.frame), inst.transform, inst.color);
            self.skin.update_actor(queue, &self.actor_pool[ai].0, &skin);
            // Queue a draw per mesh, sharing the actor's uniform slot.
            for mi in 0..inst.model.meshes.len() {
                if let Some((vbuf, ibuf, n_idx, tex)) = self.skin_static.get(&format!("{}:{}", inst.key, mi)) {
                    self.skinned.push(SkinnedDrawable {
                        vbuf: vbuf.clone(),
                        ibuf: ibuf.clone(),
                        n_idx: *n_idx,
                        tex: tex.clone(),
                        actor: ai,
                    });
                }
            }
        }
    }

    /// Replace the static scene geometry (terrain/scenery + ground plane).
    pub fn set_scene(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        instances: &[SceneInstance],
        ground_y: f32,
    ) {
        // New zone: drop the previous zone's cached textures so stale entries can't
        // be reused, then build (deduping identical scenery textures).
        self.static_tex_cache.clear();
        self.statics = gpu::build_drawables(device, queue, &self.pipeline, instances, ground_y, &mut self.static_tex_cache);
    }

    /// Replace the dynamic (per-frame) geometry — actors. `keys[i]` identifies
    /// instance `i`'s appearance so its texture upload is cached across frames.
    /// No ground plane.
    pub fn set_dynamic(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        instances: &[SceneInstance],
        keys: &[String],
    ) {
        self.dynamics = gpu::build_actor_drawables_cached(
            device,
            queue,
            &self.pipeline,
            instances,
            keys,
            &mut self.tex_cache,
            &mut self.idx_cache,
        );
    }

    /// Replace the water surfaces (rebuilt per frame so their scrolling UV offset
    /// animates). No ground plane (`f32::NAN`).
    pub fn set_water(&mut self, device: &wgpu::Device, queue: &wgpu::Queue, instances: &[SceneInstance]) {
        // Reuse the static cache (NOT cleared here): the scrolling UV changes the
        // vertex buffer every frame, but the water texture is constant — so it
        // uploads once and every later frame is a cache hit.
        self.water = gpu::build_drawables(device, queue, &self.pipeline, instances, f32::NAN, &mut self.static_tex_cache);
    }

    pub fn drawable_count(&self) -> usize {
        self.statics.len() + self.water.len() + self.dynamics.len() + self.skinned.len()
    }

    pub fn resize(&mut self, device: &wgpu::Device, w: u32, h: u32) {
        self.depth = make_depth(device, w, h, self.sample_count);
        self.msaa_color = make_msaa_color(device, self.color_format, w, h, self.sample_count);
        if let Some(b) = &mut self.bloom {
            b.resize(device, w, h);
        }
    }

    /// Draw the scene to `view` with the camera + atmosphere. `eye` is the
    /// camera position (for fog distance); `fog_*` define the distance fog.
    #[allow(clippy::too_many_arguments)]
    /// Upload the area's sky texture (RGBA8) for the textured skydome; pass it on
    /// zone load. Clearing reverts to the plain gradient.
    pub fn set_sky_texture(&mut self, device: &wgpu::Device, queue: &wgpu::Queue, width: u32, height: u32, rgba: &[u8]) {
        self.sky.set_texture(device, queue, width, height, rgba);
    }
    pub fn clear_sky_texture(&mut self) {
        self.sky.clear_texture();
    }
    pub fn set_cloud_texture(&mut self, device: &wgpu::Device, queue: &wgpu::Queue, width: u32, height: u32, rgba: &[u8]) {
        self.sky.set_cloud_texture(device, queue, width, height, rgba);
    }
    pub fn clear_cloud_texture(&mut self) {
        self.sky.clear_clouds();
    }
    pub fn set_stars_texture(&mut self, device: &wgpu::Device, queue: &wgpu::Queue, width: u32, height: u32, rgba: &[u8]) {
        self.sky.set_stars_texture(device, queue, width, height, rgba);
    }
    pub fn clear_stars_texture(&mut self) {
        self.sky.clear_stars();
    }

    #[allow(clippy::too_many_arguments)]
    pub fn render(
        &self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        view: &wgpu::TextureView,
        view_proj: [f32; 16],
        eye: [f32; 3],
        fog_color: [f32; 3],
        fog_near: f32,
        fog_far: f32,
        ambient: [f32; 3],
        light_dir: [f32; 3],
        clear: wgpu::Color,
        sky_yaw: f32,
        sky_time: f32,
        sky_night: f32,
        shadow_center: [f32; 3],
    ) {
        // Sun light view-proj: an orthographic box along the light direction,
        // centred on what the camera looks at (`shadow_center`), sized to cover
        // the near scene. Snapped to shadow-texels to stop edge shimmer as the
        // centre moves.
        // Half-extent of the orthographic shadow region (world units). Shared by
        // the light projection below and the shadow-pass caster cull, so a caster
        // is culled on exactly the box it would have been rasterised into.
        const S: f32 = 170.0;
        let light_vp = {
            use glam::{Mat4, Vec3};
            let mut ld = Vec3::from(light_dir);
            ld = if ld.length_squared() < 0.05 { Vec3::new(0.35, 1.0, 0.25).normalize() } else { ld.normalize() };
            const D: f32 = 400.0; // light distance from centre
            let up = if ld.y.abs() > 0.99 { Vec3::Z } else { Vec3::Y };
            // Stabilise against edge crawl: snap the shadow centre to whole shadow
            // texels along the two axes perpendicular to the light. Done in the
            // light's ROTATION frame (eye at origin) — snapping in the full view
            // would do nothing, since the view re-centres on the target every
            // frame, leaving it at (0,0). Snapping here moves the centre by
            // whole-texel steps in WORLD space, so the map covers the same world
            // texels frame to frame and the shadow edges stop shimmering.
            let rot = Mat4::look_at_lh(Vec3::ZERO, -ld, up);
            let texel = (2.0 * S) / gpu::SHADOW_DIM as f32;
            let cls = rot.transform_point3(Vec3::from(shadow_center));
            let snapped_ls = Vec3::new((cls.x / texel).round() * texel, (cls.y / texel).round() * texel, cls.z);
            let center = rot.inverse().transform_point3(snapped_ls);
            let view = Mat4::look_at_lh(center + ld * D, center, up);
            let proj = Mat4::orthographic_lh(-S, S, -S, S, 1.0, D * 2.0);
            (proj * view).to_cols_array()
        };
        queue.write_buffer(&self.light_buf, 0, bytemuck::cast_slice(&light_vp));
        let mut u = Uniforms::new(view_proj, eye, fog_color, fog_near, fog_far, ambient, light_dir, light_vp);
        // Upload the nearest point lights to the focus point (the shadow centre).
        if !self.zone_lights.is_empty() {
            let c = glam::Vec3::from(shadow_center);
            let d2 = |l: &gpu::PointLight| (glam::Vec3::from(l.pos) - c).length_squared();
            let mut order: Vec<usize> = (0..self.zone_lights.len()).collect();
            order.sort_by(|&a, &b| d2(&self.zone_lights[a]).total_cmp(&d2(&self.zone_lights[b])));
            let n = order.len().min(gpu::MAX_LIGHTS);
            u.num_lights = n as f32;
            for (slot, &li) in order.iter().take(n).enumerate() {
                let l = &self.zone_lights[li];
                u.lights[slot] = gpu::GpuLight {
                    pos_range: [l.pos[0], l.pos[1], l.pos[2], l.range],
                    color: [l.color[0], l.color[1], l.color[2], 0.0],
                };
            }
        }
        queue.write_buffer(&self.uniform_buf, 0, bytemuck::bytes_of(&u));
        // Sky gradient: horizon = fog colour (so the world fades into it),
        // zenith bluer/darker. Then the per-frame yaw pans the sky texture and
        // drives the cloud drift.
        self.sky.set_colors(queue, gpu::sky_zenith(fog_color), fog_color);
        self.sky.set_frame(queue, sky_yaw, sky_time, sky_night);
        // World-fixed sky: hand the inverse view-projection + eye to the sky shader
        // so it casts a per-pixel world ray (the sky stays pinned to the world as
        // the camera turns/pitches, instead of the old camera-locked 2D pan).
        let inv_vp = glam::Mat4::from_cols_array(&view_proj).inverse().to_cols_array();
        // `light_dir` points toward the sun — hand it to the sky so the sun disc /
        // moon is drawn at the same place the shadows are cast from.
        self.sky.set_camera(queue, &inv_vp, eye, light_dir);
        let mut enc = device.create_command_encoder(&Default::default());
        // Shadow pass: render opaque casters (terrain + scenery + CPU-skinned
        // actors) from the sun's POV into the shadow map. Depth only.
        {
            let mut sp = enc.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("shadow"),
                color_attachments: &[],
                depth_stencil_attachment: Some(wgpu::RenderPassDepthStencilAttachment {
                    view: &self.shadow_tex,
                    depth_ops: Some(wgpu::Operations { load: wgpu::LoadOp::Clear(1.0), store: wgpu::StoreOp::Store }),
                    stencil_ops: None,
                }),
                timestamp_writes: None,
                occlusion_query_set: None,
            });
            sp.set_pipeline(&self.shadow_pipeline.pipeline);
            sp.set_bind_group(0, &self.light_bind, &[]);
            // Caster cull: a drawable only contributes to the shadow map if its
            // world bounding sphere projects into the sun's ortho box. Project the
            // centre by `light_vp` (ortho ⇒ w = 1, so clip-space == NDC) and keep it
            // when its NDC xy is within ±(1 + radius/S) — the radius expressed in
            // NDC units, since the box half-extent S maps to 1.0. Exact: a sphere
            // outside that band cannot rasterise a single shadow texel, so the
            // shadow map is byte-identical with the cull on or off. z is left
            // unculled so tall occluders between the sun and the box still cast.
            let lvp = glam::Mat4::from_cols_array(&light_vp);
            // Escape hatch: `RCCE_NOSHADOWCULL` draws every caster (proves the cull
            // is visually lossless when the two renders diff to zero).
            let cull_on = std::env::var_os("RCCE_NOSHADOWCULL").is_none();
            let in_shadow_box = |c: &[f32; 3], r: f32| -> bool {
                if !cull_on {
                    return true;
                }
                let clip = lvp * glam::Vec4::new(c[0], c[1], c[2], 1.0);
                let m = 1.0 + r / S;
                clip.x.abs() <= m && clip.y.abs() <= m
            };
            // All casters — opaque (terrain/props/actors) and alpha (foliage); the
            // shadow fs alpha-tests so cut-out canopies cast their real shape.
            let (mut drawn, mut culled) = (0u32, 0u32);
            for d in self.statics.iter().chain(self.dynamics.iter()) {
                if !in_shadow_box(&d.center, d.radius) {
                    culled += 1;
                    continue;
                }
                drawn += 1;
                sp.set_bind_group(1, &d.tex_bind, &[]);
                sp.set_vertex_buffer(0, d.vbuf.slice(..));
                sp.set_index_buffer(d.ibuf.slice(..), wgpu::IndexFormat::Uint32);
                sp.draw_indexed(0..d.n_idx, 0, 0..1);
            }
            // GPU-skinned actors cast shadows too: same linear-blend skin, depth
            // only. (The CPU-skin path already casts via `dynamics` above; this
            // covers the faster GPU-skin path so it isn't a silent regression.)
            if !self.skinned.is_empty() && std::env::var_os("RCCE_NOSKINSHADOW").is_none() {
                sp.set_pipeline(&self.skin_shadow_pipeline.pipeline);
                sp.set_bind_group(0, &self.light_bind, &[]);
                for sd in &self.skinned {
                    sp.set_bind_group(1, &sd.tex, &[]);
                    sp.set_bind_group(2, &self.actor_pool[sd.actor].1, &[]);
                    sp.set_vertex_buffer(0, sd.vbuf.slice(..));
                    sp.set_index_buffer(sd.ibuf.slice(..), wgpu::IndexFormat::Uint32);
                    sp.draw_indexed(0..sd.n_idx, 0, 0..1);
                }
            }
            if std::env::var_os("RCCE_SHADOWSTATS").is_some() {
                eprintln!("[shadow] casters drawn={drawn} culled={culled} skinned={}", self.skinned.len());
            }
        }
        {
            // When bloom is on, the world renders into an offscreen `scene` texture
            // (so it can be sampled by the post-process); a final composite writes
            // the glowing result to the surface. Off (default) → straight to `view`.
            let world_dst: &wgpu::TextureView = self.bloom.as_ref().map(|b| b.scene_view()).unwrap_or(view);
            // With MSAA the pass renders into the multisampled colour target and
            // resolves into the single-sampled destination; without it, render
            // direct to the destination (exact pre-MSAA path).
            let (color_view, resolve_target) = match &self.msaa_color {
                Some(msaa) => (msaa, Some(world_dst)),
                None => (world_dst, None),
            };
            let mut rp = enc.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("world"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: color_view,
                    resolve_target,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(clear),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                depth_stencil_attachment: Some(wgpu::RenderPassDepthStencilAttachment {
                    view: &self.depth,
                    depth_ops: Some(wgpu::Operations {
                        load: wgpu::LoadOp::Clear(1.0),
                        store: wgpu::StoreOp::Store,
                    }),
                    stencil_ops: None,
                }),
                timestamp_writes: None,
                occlusion_query_set: None,
            });
            self.sky.draw(&mut rp); // behind the world (far plane, no depth write)
            // View-frustum cull: a drawable is submitted only when its world
            // bounding sphere is inside the camera frustum. Conservative, so the
            // image is unchanged; it just skips the textured+shaded draw of props
            // behind the camera or off the sides. `RCCE_NOFRUSTUMCULL` disables it.
            let frustum = frustum_planes(&view_proj);
            let fcull = std::env::var_os("RCCE_NOFRUSTUMCULL").is_none();
            let visible = |d: &Drawable| !fcull || sphere_in_frustum(&frustum, &d.center, d.radius);
            let (mut wdrawn, mut wculled) = (0u32, 0u32);
            // 1) Opaque pass: terrain base + props (everything except the splat
            //    overlays). Depth write on.
            rp.set_pipeline(&self.pipeline.pipeline);
            rp.set_bind_group(0, &self.bind0, &[]);
            for d in self.statics.iter().chain(self.dynamics.iter()) {
                if d.alpha {
                    continue;
                }
                if !visible(d) {
                    wculled += 1;
                    continue;
                }
                wdrawn += 1;
                rp.set_bind_group(1, &d.tex_bind, &[]);
                rp.set_vertex_buffer(0, d.vbuf.slice(..));
                rp.set_index_buffer(d.ibuf.slice(..), wgpu::IndexFormat::Uint32);
                rp.draw_indexed(0..d.n_idx, 0, 0..1);
            }
            // 2) GPU-skinned actors: same camera uniform (group 0), per-actor pose
            //    uniform (group 2). The vertex shader skins the static mesh.
            if !self.skinned.is_empty() {
                rp.set_pipeline(&self.skin.pipeline);
                rp.set_bind_group(0, &self.bind0, &[]);
                for sd in &self.skinned {
                    rp.set_bind_group(1, &sd.tex, &[]);
                    rp.set_bind_group(2, &self.actor_pool[sd.actor].1, &[]);
                    rp.set_vertex_buffer(0, sd.vbuf.slice(..));
                    rp.set_index_buffer(sd.ibuf.slice(..), wgpu::IndexFormat::Uint32);
                    rp.draw_indexed(0..sd.n_idx, 0, 0..1);
                }
            }
            // 3) Alpha pass: terrain splat overlays blended over the opaque base
            //    by vertex alpha (paths fade into grass). LessEqual + no depth
            //    write so they layer on the base but don't occlude the actors.
            rp.set_pipeline(&self.pipeline.alpha_pipeline);
            rp.set_bind_group(0, &self.bind0, &[]);
            for d in self.statics.iter().chain(self.dynamics.iter()) {
                if !d.alpha {
                    continue;
                }
                if !visible(d) {
                    wculled += 1;
                    continue;
                }
                wdrawn += 1;
                rp.set_bind_group(1, &d.tex_bind, &[]);
                rp.set_vertex_buffer(0, d.vbuf.slice(..));
                rp.set_index_buffer(d.ibuf.slice(..), wgpu::IndexFormat::Uint32);
                rp.draw_indexed(0..d.n_idx, 0, 0..1);
            }
            // 4) Water surfaces — dedicated pipeline (Fresnel sky reflection +
            //    procedural ripples), alpha-blended over terrain + splats.
            //    RCCE_FLATWATER falls back to the old flat alpha pipeline (A/B).
            if std::env::var_os("RCCE_FLATWATER").is_some() {
                rp.set_pipeline(&self.pipeline.alpha_pipeline);
            } else {
                rp.set_pipeline(&self.water_pipeline.pipeline);
            }
            rp.set_bind_group(0, &self.bind0, &[]);
            for d in &self.water {
                if !visible(d) {
                    wculled += 1;
                    continue;
                }
                wdrawn += 1;
                rp.set_bind_group(1, &d.tex_bind, &[]);
                rp.set_vertex_buffer(0, d.vbuf.slice(..));
                rp.set_index_buffer(d.ibuf.slice(..), wgpu::IndexFormat::Uint32);
                rp.draw_indexed(0..d.n_idx, 0, 0..1);
            }
            if std::env::var_os("RCCE_DRAWSTATS").is_some() {
                let uploads = gpu::TEX_UPLOADS.load(std::sync::atomic::Ordering::Relaxed);
                eprintln!("[world] drawables drawn={wdrawn} culled={wculled} tex_uploads={uploads} tex_cache={}", self.static_tex_cache.len());
            }
            // 5) Particles: unlit camera-facing billboards, additive/alpha blended,
            //    depth-tested against the world but writing no depth.
            if !self.particles.is_empty() {
                rp.set_bind_group(0, &self.bind0, &[]);
                for b in &self.particles {
                    rp.set_pipeline(if b.add { &self.particle_pipeline.add } else { &self.particle_pipeline.alpha });
                    rp.set_bind_group(1, &b.tex_bind, &[]);
                    rp.set_vertex_buffer(0, b.vbuf.slice(..));
                    rp.draw(0..b.n, 0..1);
                }
            }
        }
        // Bloom post-process: bright-pass + blur the offscreen scene and composite
        // the glow onto the surface. No-op (and the world drew straight to `view`)
        // when bloom is off.
        if let Some(b) = &self.bloom {
            b.write_uniforms(queue);
            b.run(&mut enc, view);
        }
        queue.submit(Some(enc.finish()));
    }

    /// Render one frame of the live world to an offscreen texture and save it as
    /// a PNG — a headless screenshot of exactly what the window shows (same
    /// pipelines, same `set_scene`/`set_dynamic`/`set_skinned` state, same
    /// camera + atmosphere args as [`render`](Self::render)). For verifying the
    /// live render (actor placement, foliage cutout, camera framing) without a
    /// visible window. `w`/`h` should match the depth buffer (the window size).
    #[allow(clippy::too_many_arguments)]
    pub fn capture_png(
        &self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        w: u32,
        h: u32,
        view_proj: [f32; 16],
        eye: [f32; 3],
        fog_color: [f32; 3],
        fog_near: f32,
        fog_far: f32,
        ambient: [f32; 3],
        light_dir: [f32; 3],
        clear: wgpu::Color,
        sky_yaw: f32,
        sky_time: f32,
        sky_night: f32,
        path: &str,
    ) -> Result<(), String> {
        let tex = device.create_texture(&wgpu::TextureDescriptor {
            label: Some("screenshot"),
            size: wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: self.color_format,
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
            view_formats: &[],
        });
        let view = tex.create_view(&Default::default());
        // Reuse the exact live render path, but target the offscreen view.
        self.render(
            device, queue, &view, view_proj, eye, fog_color, fog_near, fog_far, ambient,
            light_dir, clear, sky_yaw, sky_time, sky_night, eye,
        );
        save_texture_png(device, queue, &tex, w, h, self.color_format, path)
    }
}

/// Copy a rendered texture back to the CPU and write it as a PNG (swizzling
/// BGRA surfaces to RGBA). The texture must have been created with `COPY_SRC`.
/// Shared by [`WorldView::capture_png`] and the client's menu screenshot.
pub fn save_texture_png(
    device: &wgpu::Device,
    queue: &wgpu::Queue,
    tex: &wgpu::Texture,
    w: u32,
    h: u32,
    format: wgpu::TextureFormat,
    path: &str,
) -> Result<(), String> {
    let bpp = 4u32;
    let unpadded = w * bpp;
    let padded = unpadded.div_ceil(256) * 256;
    let readback = device.create_buffer(&wgpu::BufferDescriptor {
        label: Some("shot-rb"),
        size: (padded * h) as u64,
        usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
        mapped_at_creation: false,
    });
    let mut enc = device.create_command_encoder(&Default::default());
    enc.copy_texture_to_buffer(
        wgpu::ImageCopyTexture {
            texture: tex,
            mip_level: 0,
            origin: wgpu::Origin3d::ZERO,
            aspect: wgpu::TextureAspect::All,
        },
        wgpu::ImageCopyBuffer {
            buffer: &readback,
            layout: wgpu::ImageDataLayout {
                offset: 0,
                bytes_per_row: Some(padded),
                rows_per_image: Some(h),
            },
        },
        wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 },
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
    let bgra = matches!(
        format,
        wgpu::TextureFormat::Bgra8Unorm | wgpu::TextureFormat::Bgra8UnormSrgb
    );
    let mut rgba = Vec::with_capacity((unpadded * h) as usize);
    for row in 0..h {
        let start = (row * padded) as usize;
        let line = &data[start..start + unpadded as usize];
        if bgra {
            for px in line.chunks_exact(4) {
                rgba.extend_from_slice(&[px[2], px[1], px[0], px[3]]);
            }
        } else {
            rgba.extend_from_slice(line);
        }
    }
    drop(data);
    readback.unmap();

    let file = std::fs::File::create(path).map_err(|e| e.to_string())?;
    let mut encoder = png::Encoder::new(std::io::BufWriter::new(file), w, h);
    encoder.set_color(png::ColorType::Rgba);
    encoder.set_depth(png::BitDepth::Eight);
    let mut wr = encoder.write_header().map_err(|e| e.to_string())?;
    wr.write_image_data(&rgba).map_err(|e| e.to_string())?;
    Ok(())
}

/// View-projection matrix for a camera looking from `eye` at `target`.
///
/// **Left-handed** to match the Blitz3D world the server streams (Blitz uses a
/// LH coordinate system: +Z is forward/into-screen). Using RH matrices on LH
/// world data reflects the image — a horizontal mirror — so the world rendered
/// backwards (path/NPCs on the wrong side). `perspective_lh` keeps wgpu's [0,1]
/// depth; `cull_mode: None` on every pipeline means the winding flip is moot.
pub fn view_proj(eye: [f32; 3], target: [f32; 3], aspect: f32) -> [f32; 16] {
    use glam::{Mat4, Vec3};
    let proj = Mat4::perspective_lh(50f32.to_radians(), aspect, 1.0, 100_000.0);
    let view = Mat4::look_at_lh(Vec3::from(eye), Vec3::from(target), Vec3::Y);
    (proj * view).to_cols_array()
}

#[cfg(test)]
mod tests {
    use super::{frustum_planes, sphere_in_frustum, view_proj};

    // Camera at the origin looking toward +Z (the LH forward axis).
    fn cam() -> [[f32; 4]; 6] {
        frustum_planes(&view_proj([0.0, 0.0, 0.0], [0.0, 0.0, 1.0], 16.0 / 9.0))
    }

    #[test]
    fn point_ahead_is_inside_behind_is_outside() {
        let f = cam();
        // 50 units straight ahead — well inside the frustum.
        assert!(sphere_in_frustum(&f, &[0.0, 0.0, 50.0], 1.0));
        // 50 units behind the camera — fully outside (past the near plane).
        assert!(!sphere_in_frustum(&f, &[0.0, 0.0, -50.0], 1.0));
        // Far off to the side at close range — outside the lateral planes.
        assert!(!sphere_in_frustum(&f, &[500.0, 0.0, 5.0], 1.0));
    }

    #[test]
    fn large_radius_straddling_the_near_plane_stays_in() {
        let f = cam();
        // A huge sphere centred just behind the camera still overlaps the view
        // (like a zone-spanning terrain mesh) — must NOT be culled.
        assert!(sphere_in_frustum(&f, &[0.0, 0.0, -10.0], 1000.0));
    }

    #[test]
    fn planes_are_normalised() {
        let f = cam();
        for p in &f {
            let len = (p[0] * p[0] + p[1] * p[1] + p[2] * p[2]).sqrt();
            assert!((len - 1.0).abs() < 1e-4, "plane normal not unit: {len}");
        }
    }
}
