//! Optional bloom / HDR-style glow post-process (a *beyond-Blitz* extra). When
//! enabled (`RCCE_BLOOM`), the world renders into an offscreen `scene` texture
//! instead of straight to the surface; a bright-pass extracts the brightest
//! pixels (sun glints, fire/magic particles, water highlights), a small separable
//! Gaussian blurs them at half resolution, and a final pass composites
//! `scene + bloom*intensity` to the surface — so bright things bleed a soft glow.
//!
//! Default-OFF: when disabled the renderer keeps its exact prior path (world
//! resolves straight to the surface), so it never costs fps unless opted in.
//!
//! All per-pass constants (blur direction, texel size, threshold, intensity) are
//! baked into four tiny static uniform buffers at build/resize time — there are
//! no per-frame uniform writes, and updating one buffer between passes in a single
//! encoder would be a bug (only the last write would be visible).

/// Per-pass constants. `repr(C)` matching the WGSL `U` block (std140: two vec2 +
/// two f32 + a vec2 pad = 32 bytes).
#[repr(C)]
#[derive(Clone, Copy, bytemuck::Pod, bytemuck::Zeroable)]
struct BloomU {
    texel: [f32; 2],
    dir: [f32; 2],
    threshold: f32,
    intensity: f32,
    _pad: [f32; 2],
}

const SHADER: &str = r#"
struct U { texel: vec2<f32>, dir: vec2<f32>, threshold: f32, intensity: f32, pad: vec2<f32> };
@group(0) @binding(0) var<uniform> u: U;
@group(0) @binding(1) var tex: texture_2d<f32>;
@group(0) @binding(2) var samp: sampler;

struct VO { @builtin(position) pos: vec4<f32>, @location(0) uv: vec2<f32> };
@vertex fn vs(@builtin(vertex_index) vi: u32) -> VO {
    var p = array<vec2<f32>, 3>(vec2<f32>(-1.0, -1.0), vec2<f32>(3.0, -1.0), vec2<f32>(-1.0, 3.0));
    var o: VO;
    let xy = p[vi];
    o.pos = vec4<f32>(xy, 0.0, 1.0);
    o.uv = vec2<f32>((xy.x + 1.0) * 0.5, 1.0 - (xy.y + 1.0) * 0.5);
    return o;
}

// Bright-pass: keep only pixels above the luminance threshold (soft knee).
@fragment fn fs_bright(in: VO) -> @location(0) vec4<f32> {
    let c = textureSample(tex, samp, in.uv).rgb;
    let l = dot(c, vec3<f32>(0.2126, 0.7152, 0.0722));
    let k = smoothstep(u.threshold, u.threshold + 0.4, l);
    return vec4<f32>(c * k, 1.0);
}

// Separable 9-tap Gaussian (direction + texel from the uniform).
@fragment fn fs_blur(in: VO) -> @location(0) vec4<f32> {
    let o = u.dir * u.texel;
    var col = textureSample(tex, samp, in.uv).rgb * 0.227027;
    col = col + textureSample(tex, samp, in.uv + o * 1.3846).rgb * 0.316216;
    col = col + textureSample(tex, samp, in.uv - o * 1.3846).rgb * 0.316216;
    col = col + textureSample(tex, samp, in.uv + o * 3.2308).rgb * 0.070270;
    col = col + textureSample(tex, samp, in.uv - o * 3.2308).rgb * 0.070270;
    return vec4<f32>(col, 1.0);
}
"#;

// Composite samples TWO textures, so it has its own bind-group layout + shader.
const COMP_SHADER: &str = r#"
struct U { texel: vec2<f32>, dir: vec2<f32>, threshold: f32, intensity: f32, pad: vec2<f32> };
@group(0) @binding(0) var<uniform> u: U;
@group(0) @binding(1) var scene: texture_2d<f32>;
@group(0) @binding(2) var bloomt: texture_2d<f32>;
@group(0) @binding(3) var samp: sampler;
struct VO { @builtin(position) pos: vec4<f32>, @location(0) uv: vec2<f32> };
@vertex fn vs(@builtin(vertex_index) vi: u32) -> VO {
    var p = array<vec2<f32>, 3>(vec2<f32>(-1.0, -1.0), vec2<f32>(3.0, -1.0), vec2<f32>(-1.0, 3.0));
    var o: VO;
    let xy = p[vi];
    o.pos = vec4<f32>(xy, 0.0, 1.0);
    o.uv = vec2<f32>((xy.x + 1.0) * 0.5, 1.0 - (xy.y + 1.0) * 0.5);
    return o;
}
@fragment fn fs(in: VO) -> @location(0) vec4<f32> {
    let s = textureSample(scene, samp, in.uv).rgb;
    let b = textureSample(bloomt, samp, in.uv).rgb;
    return vec4<f32>(s + b * u.intensity, 1.0);
}
"#;

/// Bloom post-process resources + pipelines. Present only when `RCCE_BLOOM` is set.
pub struct Bloom {
    format: wgpu::TextureFormat,
    threshold: f32,
    intensity: f32,
    w: u32,
    h: u32,
    sampler: wgpu::Sampler,
    bgl_1: wgpu::BindGroupLayout,
    bgl_2: wgpu::BindGroupLayout,
    bright_pl: wgpu::RenderPipeline,
    blur_pl: wgpu::RenderPipeline,
    comp_pl: wgpu::RenderPipeline,
    // Per-resolution resources (rebuilt on resize).
    scene_view: wgpu::TextureView,
    bloom_a: wgpu::TextureView,
    bloom_b: wgpu::TextureView,
    // Static per-pass uniform buffers (baked at build/resize).
    u_bright: wgpu::Buffer,
    u_blur_h: wgpu::Buffer,
    u_blur_v: wgpu::Buffer,
    u_comp: wgpu::Buffer,
    bg_bright: wgpu::BindGroup, // scene -> bloom_a
    bg_blur_h: wgpu::BindGroup, // bloom_a -> bloom_b
    bg_blur_v: wgpu::BindGroup, // bloom_b -> bloom_a
    bg_comp: wgpu::BindGroup,   // scene + bloom_a -> surface
}

impl Bloom {
    /// Build bloom resources, or `None` if `RCCE_BLOOM` is unset (default).
    pub fn maybe_new(device: &wgpu::Device, format: wgpu::TextureFormat, w: u32, h: u32) -> Option<Bloom> {
        if std::env::var_os("RCCE_BLOOM").is_none() {
            return None;
        }
        let envf = |k: &str, d: f32| std::env::var(k).ok().and_then(|s| s.trim().parse().ok()).unwrap_or(d);
        let threshold = envf("RCCE_BLOOM_THRESHOLD", 0.72);
        let intensity = envf("RCCE_BLOOM_INTENSITY", 0.65);

        let sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            label: Some("bloom-samp"),
            address_mode_u: wgpu::AddressMode::ClampToEdge,
            address_mode_v: wgpu::AddressMode::ClampToEdge,
            mag_filter: wgpu::FilterMode::Linear,
            min_filter: wgpu::FilterMode::Linear,
            ..Default::default()
        });
        let tex_entry = |binding| wgpu::BindGroupLayoutEntry {
            binding,
            visibility: wgpu::ShaderStages::FRAGMENT,
            ty: wgpu::BindingType::Texture {
                sample_type: wgpu::TextureSampleType::Float { filterable: true },
                view_dimension: wgpu::TextureViewDimension::D2,
                multisampled: false,
            },
            count: None,
        };
        let u_entry = wgpu::BindGroupLayoutEntry {
            binding: 0,
            visibility: wgpu::ShaderStages::FRAGMENT,
            ty: wgpu::BindingType::Buffer { ty: wgpu::BufferBindingType::Uniform, has_dynamic_offset: false, min_binding_size: None },
            count: None,
        };
        let samp_entry = |binding| wgpu::BindGroupLayoutEntry {
            binding,
            visibility: wgpu::ShaderStages::FRAGMENT,
            ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::Filtering),
            count: None,
        };
        let bgl_1 = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("bloom-1tex"),
            entries: &[u_entry.clone(), tex_entry(1), samp_entry(2)],
        });
        let bgl_2 = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("bloom-2tex"),
            entries: &[u_entry, tex_entry(1), tex_entry(2), samp_entry(3)],
        });

        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor { label: Some("bloom"), source: wgpu::ShaderSource::Wgsl(SHADER.into()) });
        let comp_shader = device.create_shader_module(wgpu::ShaderModuleDescriptor { label: Some("bloom-comp"), source: wgpu::ShaderSource::Wgsl(COMP_SHADER.into()) });
        let make = |bgl: &wgpu::BindGroupLayout, module: &wgpu::ShaderModule, fs: &str| {
            let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor { label: None, bind_group_layouts: &[bgl], push_constant_ranges: &[] });
            device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
                label: Some("bloom-pl"),
                layout: Some(&layout),
                vertex: wgpu::VertexState { module, entry_point: "vs", compilation_options: Default::default(), buffers: &[] },
                fragment: Some(wgpu::FragmentState {
                    module,
                    entry_point: fs,
                    compilation_options: Default::default(),
                    targets: &[Some(format.into())],
                }),
                primitive: wgpu::PrimitiveState::default(),
                depth_stencil: None,
                multisample: wgpu::MultisampleState::default(),
                multiview: None,
                cache: None,
            })
        };
        let bright_pl = make(&bgl_1, &shader, "fs_bright");
        let blur_pl = make(&bgl_1, &shader, "fs_blur");
        let comp_pl = make(&bgl_2, &comp_shader, "fs");

        let u_bright = device.create_buffer(&wgpu::BufferDescriptor { label: Some("u-bright"), size: 32, usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST, mapped_at_creation: false });
        let u_blur_h = device.create_buffer(&wgpu::BufferDescriptor { label: Some("u-blurh"), size: 32, usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST, mapped_at_creation: false });
        let u_blur_v = device.create_buffer(&wgpu::BufferDescriptor { label: Some("u-blurv"), size: 32, usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST, mapped_at_creation: false });
        let u_comp = device.create_buffer(&wgpu::BufferDescriptor { label: Some("u-comp"), size: 32, usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST, mapped_at_creation: false });

        // Placeholder views replaced immediately by `build_targets`.
        let (scene_view, bloom_a, bloom_b, bg_bright, bg_blur_h, bg_blur_v, bg_comp) =
            Self::build_targets(device, format, &bgl_1, &bgl_2, &sampler, &u_bright, &u_blur_h, &u_blur_v, &u_comp, threshold, intensity, w, h);

        Some(Bloom {
            format, threshold, intensity, w: w.max(1), h: h.max(1), sampler, bgl_1, bgl_2, bright_pl, blur_pl, comp_pl,
            scene_view, bloom_a, bloom_b, u_bright, u_blur_h, u_blur_v, u_comp,
            bg_bright, bg_blur_h, bg_blur_v, bg_comp,
        })
    }

    #[allow(clippy::too_many_arguments)]
    fn build_targets(
        device: &wgpu::Device, format: wgpu::TextureFormat,
        bgl_1: &wgpu::BindGroupLayout, bgl_2: &wgpu::BindGroupLayout, sampler: &wgpu::Sampler,
        u_bright: &wgpu::Buffer, u_blur_h: &wgpu::Buffer, u_blur_v: &wgpu::Buffer, u_comp: &wgpu::Buffer,
        threshold: f32, intensity: f32, w: u32, h: u32,
    ) -> (wgpu::TextureView, wgpu::TextureView, wgpu::TextureView, wgpu::BindGroup, wgpu::BindGroup, wgpu::BindGroup, wgpu::BindGroup) {
        let (w, h) = (w.max(1), h.max(1));
        let (hw, hh) = ((w / 2).max(1), (h / 2).max(1));
        let mk = |label, tw, th| device
            .create_texture(&wgpu::TextureDescriptor {
                label: Some(label),
                size: wgpu::Extent3d { width: tw, height: th, depth_or_array_layers: 1 },
                mip_level_count: 1,
                sample_count: 1,
                dimension: wgpu::TextureDimension::D2,
                format,
                usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::TEXTURE_BINDING,
                view_formats: &[],
            })
            .create_view(&Default::default());
        let scene_view = mk("bloom-scene", w, h);
        let bloom_a = mk("bloom-a", hw, hh);
        let bloom_b = mk("bloom-b", hw, hh);
        // Uniform *contents* are written separately by `write_uniforms` (needs a
        // queue); here we only (re)bind the buffers to the new texture views.
        let _ = (threshold, intensity);

        let bg_1 = |u: &wgpu::Buffer, t: &wgpu::TextureView| device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: None,
            layout: bgl_1,
            entries: &[
                wgpu::BindGroupEntry { binding: 0, resource: u.as_entire_binding() },
                wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::TextureView(t) },
                wgpu::BindGroupEntry { binding: 2, resource: wgpu::BindingResource::Sampler(sampler) },
            ],
        });
        let bg_bright = bg_1(u_bright, &scene_view);
        let bg_blur_h = bg_1(u_blur_h, &bloom_a);
        let bg_blur_v = bg_1(u_blur_v, &bloom_b);
        let bg_comp = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: None,
            layout: bgl_2,
            entries: &[
                wgpu::BindGroupEntry { binding: 0, resource: u_comp.as_entire_binding() },
                wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::TextureView(&scene_view) },
                wgpu::BindGroupEntry { binding: 2, resource: wgpu::BindingResource::TextureView(&bloom_a) },
                wgpu::BindGroupEntry { binding: 3, resource: wgpu::BindingResource::Sampler(sampler) },
            ],
        });
        (scene_view, bloom_a, bloom_b, bg_bright, bg_blur_h, bg_blur_v, bg_comp)
    }

    /// Write the four static per-pass uniforms for the current resolution. Called
    /// each frame from `render` (cheap — four 32-byte writes — and avoids needing a
    /// queue at construction time).
    pub fn write_uniforms(&self, queue: &wgpu::Queue) {
        let (w, h) = (self.w, self.h);
        let (hw, hh) = ((w / 2).max(1) as f32, (h / 2).max(1) as f32);
        let half_texel = [1.0 / hw, 1.0 / hh];
        queue.write_buffer(&self.u_bright, 0, bytemuck::bytes_of(&BloomU { texel: [1.0 / w as f32, 1.0 / h as f32], dir: [0.0, 0.0], threshold: self.threshold, intensity: 0.0, _pad: [0.0; 2] }));
        queue.write_buffer(&self.u_blur_h, 0, bytemuck::bytes_of(&BloomU { texel: half_texel, dir: [1.0, 0.0], threshold: 0.0, intensity: 0.0, _pad: [0.0; 2] }));
        queue.write_buffer(&self.u_blur_v, 0, bytemuck::bytes_of(&BloomU { texel: half_texel, dir: [0.0, 1.0], threshold: 0.0, intensity: 0.0, _pad: [0.0; 2] }));
        queue.write_buffer(&self.u_comp, 0, bytemuck::bytes_of(&BloomU { texel: [0.0, 0.0], dir: [0.0, 0.0], threshold: 0.0, intensity: self.intensity, _pad: [0.0; 2] }));
    }

    /// Recreate the per-resolution targets + bind groups (call on window resize).
    pub fn resize(&mut self, device: &wgpu::Device, w: u32, h: u32) {
        let (sv, ba, bb, bgr, bgh, bgv, bgc) = Self::build_targets(
            device, self.format, &self.bgl_1, &self.bgl_2, &self.sampler,
            &self.u_bright, &self.u_blur_h, &self.u_blur_v, &self.u_comp, self.threshold, self.intensity, w, h,
        );
        self.scene_view = sv; self.bloom_a = ba; self.bloom_b = bb;
        self.bg_bright = bgr; self.bg_blur_h = bgh; self.bg_blur_v = bgv; self.bg_comp = bgc;
        self.w = w.max(1); self.h = h.max(1);
    }

    /// The texture the world pass should target (resolve into) when bloom is on.
    pub fn scene_view(&self) -> &wgpu::TextureView {
        &self.scene_view
    }

    /// Run bright-pass + separable blur + composite, writing the glowing result to
    /// `surface`. Records into `enc`; the world must already have rendered into
    /// [`scene_view`](Self::scene_view).
    pub fn run(&self, enc: &mut wgpu::CommandEncoder, surface: &wgpu::TextureView) {
        let pass = |enc: &mut wgpu::CommandEncoder, target: &wgpu::TextureView, pl: &wgpu::RenderPipeline, bg: &wgpu::BindGroup| {
            let mut rp = enc.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("bloom-pass"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: target,
                    resolve_target: None,
                    ops: wgpu::Operations { load: wgpu::LoadOp::Clear(wgpu::Color::BLACK), store: wgpu::StoreOp::Store },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
            });
            rp.set_pipeline(pl);
            rp.set_bind_group(0, bg, &[]);
            rp.draw(0..3, 0..1);
        };
        pass(enc, &self.bloom_a, &self.bright_pl, &self.bg_bright); // scene -> bloom_a (bright)
        pass(enc, &self.bloom_b, &self.blur_pl, &self.bg_blur_h); // bloom_a -> bloom_b (H)
        pass(enc, &self.bloom_a, &self.blur_pl, &self.bg_blur_v); // bloom_b -> bloom_a (V)
        pass(enc, surface, &self.comp_pl, &self.bg_comp); // scene + bloom_a -> surface
    }
}
