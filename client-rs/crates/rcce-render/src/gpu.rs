//! Shared GPU primitives for the textured-mesh renderers: the vertex format,
//! the WGSL shader, normal computation, the render pipeline, and per-instance
//! drawable building. Both the offscreen PNG path (`scene::render_scene_png`)
//! and the real-time window (`world_view::ScenePipeline`) build on these so the
//! two stay visually identical.

use std::collections::HashMap;
use std::rc::Rc;

use bytemuck::{Pod, Zeroable};
use glam::{Mat3, Mat4, Vec3};
use wgpu::util::DeviceExt;

use rcce_data::{B3dMesh, Image};

use crate::scene::SceneInstance;

/// Cache of uploaded texture bind groups, keyed by a caller-supplied string
/// (e.g. an actor's appearance). Lets per-frame actor rebuilds reuse the
/// already-uploaded skin instead of re-uploading every frame.
pub type TexCache = HashMap<String, Rc<wgpu::BindGroup>>;

/// Count of GPU texture uploads (mip-chain build + `create_texture`) performed by
/// [`Pipeline::upload_tex`]. Used by `RCCE_TEXSTATS` to confirm the static/water
/// texture cache actually dedups uploads (static scenery sharing a texture, and
/// water — which is rebuilt every frame — re-uploading its texture each frame).
pub static TEX_UPLOADS: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);

/// Cheap content fingerprint for a `(texture, lightmap)` pair so identical
/// textures share one GPU upload. Dims + byte length + a spread of sampled bytes
/// — collision-proof for distinct real textures, far cheaper than repeating the
/// upload + mip-chain build. `RCCE_NOTEXCACHE` bypasses the cache (A/B).
fn tex_cache_key(tex: Option<&Image>, lm: Option<&Image>) -> String {
    fn part(img: Option<&Image>) -> String {
        match img {
            None => "_".into(),
            Some(i) => {
                let n = i.rgba.len();
                let mut h: u64 = 0xcbf29ce484222325;
                let step = (n / 24).max(1);
                let mut k = 0;
                while k < n {
                    h = (h ^ i.rgba[k] as u64).wrapping_mul(0x100000001b3);
                    k += step;
                }
                format!("{}x{}.{}.{:x}", i.width, i.height, n, h)
            }
        }
    }
    format!("{}|{}", part(tex), part(lm))
}

/// Fetch a cached texture bind group by content key, building (and counting) it on
/// a miss. `RCCE_NOTEXCACHE` forces a fresh build every time.
fn cached_tex_bind(
    device: &wgpu::Device,
    queue: &wgpu::Queue,
    pipeline: &Pipeline,
    cache: &mut TexCache,
    tex: Option<&Image>,
    lm: Option<&Image>,
) -> Rc<wgpu::BindGroup> {
    if std::env::var_os("RCCE_NOTEXCACHE").is_some() {
        return Rc::new(pipeline.texture_bind_lm(device, queue, tex, lm));
    }
    cache
        .entry(tex_cache_key(tex, lm))
        .or_insert_with(|| Rc::new(pipeline.texture_bind_lm(device, queue, tex, lm)))
        .clone()
}

pub const COLOR_FORMAT: wgpu::TextureFormat = wgpu::TextureFormat::Rgba8Unorm;
pub const DEPTH_FORMAT: wgpu::TextureFormat = wgpu::TextureFormat::Depth32Float;

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
pub struct Vertex {
    pub pos: [f32; 3],
    pub normal: [f32; 3],
    pub uv: [f32; 2],
    /// Second UV set, for the lightmap sample. `[0,0]` when the mesh has no
    /// lightmap (the bound lightmap is then a 1×1 grey no-op, so the value is
    /// irrelevant).
    pub uv2: [f32; 2],
    /// RGBA vertex color. RGB modulates the texture (terrain splat tinting);
    /// **alpha is the splat blend weight** for the alpha pass (1.0 = opaque).
    pub color: [f32; 4],
}

/// Strength of the zone's directional light relative to its ambient floor.
pub const LIGHT_INTENSITY: f32 = 0.6;

/// Max dynamic point lights the shader accumulates per frame. The nearest this
/// many to the camera are uploaded each frame (a zone may place far more).
pub const MAX_LIGHTS: usize = 16;

/// A placed dynamic point light (LightModels mesh / scenery light setting):
/// world position, reach, and colour. CPU side, selected + packed per frame.
#[derive(Clone, Copy, Debug)]
pub struct PointLight {
    pub pos: [f32; 3],
    pub range: f32,
    /// Linear RGB, already normalised to 0..1 (× any brightness gain).
    pub color: [f32; 3],
}

/// GPU layout of one point light: `(x,y,z,range)` + `(r,g,b,_)`, std140-aligned.
#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
pub struct GpuLight {
    pub pos_range: [f32; 4],
    pub color: [f32; 4],
}

/// Camera + atmosphere uniform. Field order matches the WGSL `U` block: each
/// vec3 packs a trailing scalar into its 16-byte slot (eye|fog_near,
/// fog_color|fog_far, ambient|light_intensity, light_dir|pad), so the struct is
/// a tight 128 bytes with no implicit padding.
#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
pub struct Uniforms {
    pub mvp: [f32; 16],
    /// World → sun light clip space, for the shadow-map lookup. Identity-ish
    /// (a far/degenerate matrix) leaves everything lit (the offscreen path).
    pub light_vp: [f32; 16],
    pub eye: [f32; 3],
    pub fog_near: f32,
    pub fog_color: [f32; 3],
    pub fog_far: f32,
    pub ambient: [f32; 3],
    pub light_intensity: f32,
    pub light_dir: [f32; 3],
    pub num_lights: f32,
    pub lights: [GpuLight; MAX_LIGHTS],
}

impl Uniforms {
    pub fn new(
        mvp: [f32; 16],
        eye: [f32; 3],
        fog_color: [f32; 3],
        fog_near: f32,
        fog_far: f32,
        ambient: [f32; 3],
        light_dir: [f32; 3],
        light_vp: [f32; 16],
    ) -> Uniforms {
        Uniforms {
            mvp,
            light_vp,
            eye,
            fog_near,
            fog_color,
            fog_far,
            ambient,
            light_intensity: LIGHT_INTENSITY,
            light_dir,
            num_lights: 0.0,
            lights: [GpuLight { pos_range: [0.0; 4], color: [0.0; 4] }; MAX_LIGHTS],
        }
    }
}

/// Sun shadow-map resolution (square). 2048² gives crisp camera-region shadows.
pub const SHADOW_DIM: u32 = 2048;

/// Renders shadow casters from the sun's POV into the shadow map (depth). It
/// alpha-tests the base texture, so cut-out foliage (tree canopies, grass) casts
/// its real silhouette — not just the opaque trunk. Reuses the scene `Vertex`
/// layout (position + uv) and the scene texture bind group.
pub struct ShadowPipeline {
    pub pipeline: wgpu::RenderPipeline,
    pub bgl: wgpu::BindGroupLayout,
}

const SHADOW_SHADER: &str = r#"
struct LU { light_vp: mat4x4<f32> };
@group(0) @binding(0) var<uniform> lu: LU;
@group(1) @binding(0) var tex: texture_2d<f32>;
@group(1) @binding(1) var samp: sampler;
struct VO { @builtin(position) clip: vec4<f32>, @location(0) uv: vec2<f32> };
@vertex fn vs(@location(0) pos: vec3<f32>, @location(2) uv: vec2<f32>) -> VO {
    var o: VO;
    o.clip = lu.light_vp * vec4<f32>(pos, 1.0);
    o.uv = uv;
    return o;
}
@fragment fn fs(in: VO) {
    // Alpha-test so foliage casts its cut-out shape (opaque texels keep a≈1).
    if (textureSample(tex, samp, in.uv).a < 0.5) { discard; }
}
"#;

impl ShadowPipeline {
    pub fn new(device: &wgpu::Device, bgl_texture: &wgpu::BindGroupLayout) -> ShadowPipeline {
        let bgl = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("shadow-u"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::VERTEX,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: None,
                },
                count: None,
            }],
        });
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("shadow"),
            source: wgpu::ShaderSource::Wgsl(SHADOW_SHADER.into()),
        });
        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: None,
            bind_group_layouts: &[&bgl, bgl_texture],
            push_constant_ranges: &[],
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("shadow"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs",
                compilation_options: Default::default(),
                buffers: &[wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<Vertex>() as u64,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    // position (loc 0, offset 0) + uv (loc 2, offset 24).
                    attributes: &[
                        wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x3, offset: 0, shader_location: 0 },
                        wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x2, offset: 24, shader_location: 2 },
                    ],
                }],
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: "fs",
                compilation_options: Default::default(),
                targets: &[], // depth only — the fs just alpha-discards
            }),
            primitive: wgpu::PrimitiveState { cull_mode: None, ..Default::default() },
            depth_stencil: Some(wgpu::DepthStencilState {
                format: DEPTH_FORMAT,
                depth_write_enabled: true,
                depth_compare: wgpu::CompareFunction::Less,
                stencil: Default::default(),
                // Constant + slope-scaled bias to cut shadow acne at the source.
                bias: wgpu::DepthBiasState { constant: 2, slope_scale: 2.0, clamp: 0.0 },
            }),
            multisample: msaa(1), // shadow map is always single-sampled
            multiview: None,
            cache: None,
        });
        ShadowPipeline { pipeline, bgl }
    }
}

/// Depth-only shadow caster for GPU-skinned actors: the same linear-blend skin as
/// the main skin pipeline, but writing only depth into the sun's shadow map (so
/// GPU-skinned actors cast shadows like the CPU-skinned/static casters). Reuses
/// the shadow light-VP layout (group 0), the texture layout (group 1, for the
/// alpha-test cut-out), and the per-actor pose layout (group 2).
pub struct SkinShadowPipeline {
    pub pipeline: wgpu::RenderPipeline,
}

const SKIN_SHADOW_SHADER: &str = r#"
struct LU { light_vp: mat4x4<f32> };
@group(0) @binding(0) var<uniform> lu: LU;
@group(1) @binding(0) var tex: texture_2d<f32>;
@group(1) @binding(1) var samp: sampler;
struct Actor { bones: array<mat4x4<f32>, 64>, model: mat4x4<f32>, color: vec4<f32> };
@group(2) @binding(0) var<uniform> a: Actor;
struct VO { @builtin(position) clip: vec4<f32>, @location(0) uv: vec2<f32> };
@vertex fn vs(
    @location(0) pos: vec3<f32>,
    @location(2) uv: vec2<f32>,
    @location(3) bones: vec4<u32>,
    @location(4) weights: vec4<f32>,
) -> VO {
    let wsum = weights.x + weights.y + weights.z + weights.w;
    var sp = vec3<f32>(0.0);
    if (wsum > 0.0001) {
        for (var i = 0u; i < 4u; i = i + 1u) {
            let w = weights[i];
            if (w > 0.0) { sp = sp + w * (a.bones[bones[i]] * vec4<f32>(pos, 1.0)).xyz; }
        }
    } else {
        sp = pos;
    }
    var o: VO;
    o.clip = lu.light_vp * (a.model * vec4<f32>(sp, 1.0));
    o.uv = uv;
    return o;
}
@fragment fn fs(in: VO) {
    if (textureSample(tex, samp, in.uv).a < 0.5) { discard; }
}
"#;

impl SkinShadowPipeline {
    pub fn new(
        device: &wgpu::Device,
        bgl_light: &wgpu::BindGroupLayout,
        bgl_texture: &wgpu::BindGroupLayout,
        bgl_actor: &wgpu::BindGroupLayout,
    ) -> SkinShadowPipeline {
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("skin-shadow"),
            source: wgpu::ShaderSource::Wgsl(SKIN_SHADOW_SHADER.into()),
        });
        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: None,
            bind_group_layouts: &[bgl_light, bgl_texture, bgl_actor],
            push_constant_ranges: &[],
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("skin-shadow"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs",
                compilation_options: Default::default(),
                buffers: &[wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<SkinnedVertex>() as u64,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    // pos (loc 0 @0), uv (loc 2 @24), bones (loc 3 @32), weights (loc 4 @48).
                    attributes: &[
                        wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x3, offset: 0, shader_location: 0 },
                        wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x2, offset: 24, shader_location: 2 },
                        wgpu::VertexAttribute { format: wgpu::VertexFormat::Uint32x4, offset: 32, shader_location: 3 },
                        wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x4, offset: 48, shader_location: 4 },
                    ],
                }],
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: "fs",
                compilation_options: Default::default(),
                targets: &[], // depth only
            }),
            primitive: wgpu::PrimitiveState { cull_mode: None, ..Default::default() },
            depth_stencil: Some(wgpu::DepthStencilState {
                format: DEPTH_FORMAT,
                depth_write_enabled: true,
                depth_compare: wgpu::CompareFunction::Less,
                stencil: Default::default(),
                bias: wgpu::DepthBiasState { constant: 2, slope_scale: 2.0, clamp: 0.0 },
            }),
            multisample: msaa(1), // shadow map is always single-sampled
            multiview: None,
            cache: None,
        });
        SkinShadowPipeline { pipeline }
    }
}

/// MSAA state for the world-pass pipelines (`count` samples per pixel). `count`
/// of 1 is the no-MSAA default; 2/4/8 anti-alias scenery/foliage silhouettes.
fn msaa(count: u32) -> wgpu::MultisampleState {
    wgpu::MultisampleState { count, ..Default::default() }
}

/// As [`msaa`], but with alpha-to-coverage on (when multisampling). The opaque
/// scene + skinned-actor pipelines alpha-test cut-out foliage/hair; A2C turns the
/// fragment's alpha into an MSAA coverage mask so those cut-out silhouettes get
/// anti-aliased too (plain MSAA only smooths geometric triangle edges). Solid
/// meshes output alpha 1 → full coverage → unchanged. No effect at `count` 1.
fn msaa_a2c(count: u32) -> wgpu::MultisampleState {
    wgpu::MultisampleState { count, alpha_to_coverage_enabled: count > 1, ..Default::default() }
}

/// Unlit textured billboard pipeline for particles: vertex colour × texture, no
/// lighting/fog, depth-tested but no depth write (particles don't occlude). Two
/// variants for the emitter blend modes: additive (fire/glow) and alpha (smoke).
pub struct ParticlePipeline {
    pub add: wgpu::RenderPipeline,
    pub alpha: wgpu::RenderPipeline,
}

const PARTICLE_SHADER: &str = r#"
struct U { mvp: mat4x4<f32> };
@group(0) @binding(0) var<uniform> u: U;
@group(1) @binding(0) var tex: texture_2d<f32>;
@group(1) @binding(1) var samp: sampler;
struct VO { @builtin(position) clip: vec4<f32>, @location(0) uv: vec2<f32>, @location(1) color: vec4<f32> };
@vertex fn vs(@location(0) pos: vec3<f32>, @location(2) uv: vec2<f32>, @location(4) color: vec4<f32>) -> VO {
    var o: VO;
    o.clip = u.mvp * vec4<f32>(pos, 1.0);
    o.uv = uv;
    o.color = color;
    return o;
}
@fragment fn fs(in: VO) -> @location(0) vec4<f32> {
    return textureSample(tex, samp, in.uv) * in.color;
}
"#;

impl ParticlePipeline {
    pub fn new(
        device: &wgpu::Device,
        color_format: wgpu::TextureFormat,
        bgl_uniform: &wgpu::BindGroupLayout,
        bgl_texture: &wgpu::BindGroupLayout,
        sample_count: u32,
    ) -> ParticlePipeline {
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("particle"),
            source: wgpu::ShaderSource::Wgsl(PARTICLE_SHADER.into()),
        });
        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: None,
            bind_group_layouts: &[bgl_uniform, bgl_texture],
            push_constant_ranges: &[],
        });
        let make = |blend: wgpu::BlendState| {
            device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
                label: Some("particle"),
                layout: Some(&layout),
                vertex: wgpu::VertexState {
                    module: &shader,
                    entry_point: "vs",
                    compilation_options: Default::default(),
                    buffers: &[wgpu::VertexBufferLayout {
                        array_stride: std::mem::size_of::<Vertex>() as u64,
                        step_mode: wgpu::VertexStepMode::Vertex,
                        // position (0), uv (offset 24, loc 2), colour (offset 40, loc 4).
                        attributes: &[
                            wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x3, offset: 0, shader_location: 0 },
                            wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x2, offset: 24, shader_location: 2 },
                            wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x4, offset: 40, shader_location: 4 },
                        ],
                    }],
                },
                fragment: Some(wgpu::FragmentState {
                    module: &shader,
                    entry_point: "fs",
                    compilation_options: Default::default(),
                    targets: &[Some(wgpu::ColorTargetState {
                        format: color_format,
                        blend: Some(blend),
                        write_mask: wgpu::ColorWrites::ALL,
                    })],
                }),
                primitive: wgpu::PrimitiveState { cull_mode: None, ..Default::default() },
                depth_stencil: Some(wgpu::DepthStencilState {
                    format: DEPTH_FORMAT,
                    depth_write_enabled: false,
                    depth_compare: wgpu::CompareFunction::LessEqual,
                    stencil: Default::default(),
                    bias: Default::default(),
                }),
                multisample: msaa(sample_count),
                multiview: None,
                cache: None,
            })
        };
        let additive = wgpu::BlendState {
            color: wgpu::BlendComponent { src_factor: wgpu::BlendFactor::SrcAlpha, dst_factor: wgpu::BlendFactor::One, operation: wgpu::BlendOperation::Add },
            alpha: wgpu::BlendComponent { src_factor: wgpu::BlendFactor::One, dst_factor: wgpu::BlendFactor::One, operation: wgpu::BlendOperation::Add },
        };
        ParticlePipeline {
            add: make(additive),
            alpha: make(wgpu::BlendState::ALPHA_BLENDING),
        }
    }
}

/// Water surface pipeline — a richer look than a flat textured plane (better than
/// Blitz's scrolling texture). Adds a **Fresnel sky reflection** (water brightens
/// toward the sky/fog colour at grazing angles and is clearer looking straight
/// down) and **shimmering ripples** (a procedural normal driven by the surface's
/// already-scrolling UV, so it animates with no time uniform). Convention-free:
/// it uses only the view direction and the surface normal, never the sun
/// direction. Alpha-blended, depth-tested, no depth write (like the old water).
pub struct WaterPipeline {
    pub pipeline: wgpu::RenderPipeline,
}

const WATER_SHADER: &str = r#"
struct Light { pos_range: vec4<f32>, color: vec4<f32> };
struct U {
    mvp: mat4x4<f32>,
    light_vp: mat4x4<f32>,
    eye: vec3<f32>,
    fog_near: f32,
    fog_color: vec3<f32>,
    fog_far: f32,
    ambient: vec3<f32>,
    light_intensity: f32,
    light_dir: vec3<f32>,
    num_lights: f32,
    lights: array<Light, 16>,
};
@group(0) @binding(0) var<uniform> u: U;
@group(1) @binding(0) var tex: texture_2d<f32>;
@group(1) @binding(1) var samp: sampler;
struct VO { @builtin(position) clip: vec4<f32>, @location(0) uv: vec2<f32>, @location(1) color: vec4<f32>, @location(2) world: vec3<f32> };
@vertex fn vs(@location(0) pos: vec3<f32>, @location(1) normal: vec3<f32>, @location(2) uv: vec2<f32>, @location(3) uv2: vec2<f32>, @location(4) color: vec4<f32>) -> VO {
    var o: VO;
    o.clip = u.mvp * vec4<f32>(pos, 1.0); // positions are baked in world space
    o.uv = uv;
    o.color = color;
    o.world = pos;
    return o;
}
@fragment fn fs(in: VO) -> @location(0) vec4<f32> {
    // Procedural ripple normal. The plane's UV scrolls every frame (baked offset),
    // so these wave sums drift → the highlights shimmer without a time uniform.
    let s = 0.07;
    let rx = sin(in.uv.x * 40.0 + in.uv.y * 13.0) + sin(in.uv.x * 17.0 - in.uv.y * 31.0);
    let rz = cos(in.uv.y * 37.0 - in.uv.x * 11.0) + sin(in.uv.x * 23.0 + in.uv.y * 19.0);
    let N = normalize(vec3<f32>(rx * s, 1.0, rz * s));
    let V = normalize(u.eye - in.world);
    // Fresnel: ~0 looking straight down (clear), ~1 at grazing angles (reflective).
    let fres = pow(1.0 - clamp(dot(N, V), 0.0, 1.0), 5.0);
    let base = textureSample(tex, samp, in.uv) * in.color;
    // Reflect the sky/fog colour; brighten toward it at grazing angles.
    let rgb = mix(base.rgb, u.fog_color, clamp(fres * 0.7, 0.0, 1.0));
    // A touch more opaque edge-on (water hides the bottom at grazing angles).
    let a = clamp(base.a + fres * 0.25, 0.0, 1.0);
    // Sun specular glint: a Blinn-Phong highlight toward the sun, broken into
    // sparkles by a finer, higher-frequency ripple normal (so it shimmers across
    // the surface as the UV scrolls, not one smooth patch). `light_dir` is the
    // day/night sun (shared world uniform); fade as the sun nears the horizon so
    // there's no sparkle at night.
    let fx = sin(in.uv.x * 90.0 - in.uv.y * 71.0) + sin(in.uv.x * 131.0 + in.uv.y * 53.0);
    let fz = cos(in.uv.y * 113.0 + in.uv.x * 61.0) + sin(in.uv.x * 83.0 - in.uv.y * 97.0);
    let Ns = normalize(vec3<f32>((rx + fx) * s, 1.0, (rz + fz) * s));
    let L = normalize(u.light_dir);
    let H = normalize(L + V);
    let sun_up = smoothstep(0.0, 0.30, L.y);
    let spec = pow(max(dot(Ns, H), 0.0), 70.0) * sun_up;
    let glint = vec3<f32>(1.0, 0.96, 0.86) * spec * 1.1;
    return vec4<f32>(rgb + glint, a);
}
"#;

impl WaterPipeline {
    pub fn new(
        device: &wgpu::Device,
        color_format: wgpu::TextureFormat,
        bgl_uniform: &wgpu::BindGroupLayout,
        bgl_texture: &wgpu::BindGroupLayout,
        sample_count: u32,
    ) -> WaterPipeline {
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("water"),
            source: wgpu::ShaderSource::Wgsl(WATER_SHADER.into()),
        });
        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: None,
            bind_group_layouts: &[bgl_uniform, bgl_texture],
            push_constant_ranges: &[],
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("water"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs",
                compilation_options: Default::default(),
                buffers: &[wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<Vertex>() as u64,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    attributes: &wgpu::vertex_attr_array![0 => Float32x3, 1 => Float32x3, 2 => Float32x2, 3 => Float32x2, 4 => Float32x4],
                }],
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: "fs",
                compilation_options: Default::default(),
                targets: &[Some(wgpu::ColorTargetState {
                    format: color_format,
                    blend: Some(wgpu::BlendState::ALPHA_BLENDING),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
            }),
            primitive: wgpu::PrimitiveState { cull_mode: None, ..Default::default() },
            depth_stencil: Some(wgpu::DepthStencilState {
                format: DEPTH_FORMAT,
                depth_write_enabled: false,
                depth_compare: wgpu::CompareFunction::LessEqual,
                stencil: Default::default(),
                bias: Default::default(),
            }),
            multisample: msaa(sample_count),
            multiview: None,
            cache: None,
        });
        WaterPipeline { pipeline }
    }
}

pub const SHADER: &str = r#"
struct Light { pos_range: vec4<f32>, color: vec4<f32> };
struct U {
    mvp: mat4x4<f32>,
    light_vp: mat4x4<f32>,
    eye: vec3<f32>,
    fog_near: f32,
    fog_color: vec3<f32>,
    fog_far: f32,
    ambient: vec3<f32>,
    light_intensity: f32,
    light_dir: vec3<f32>,
    num_lights: f32,
    lights: array<Light, 16>,
};
@group(0) @binding(0) var<uniform> u: U;
// Sun shadow map (depth from the light's POV) + its comparison sampler. The
// offscreen path binds a 1×1 depth=1 default, so the test is always "lit".
@group(0) @binding(1) var shadow_map: texture_depth_2d;
@group(0) @binding(2) var shadow_samp: sampler_comparison;
@group(1) @binding(0) var tex: texture_2d<f32>;
@group(1) @binding(1) var samp: sampler;
// Baked lightmap (the brush's 2nd texture slot), sampled with the 2nd UV set.
// Meshes with no lightmap bind a 1×1 grey 0.5 default, so `lm * 2.0` = 1.0 and
// they're unaffected; real lightmaps apply Blitz-style multiply2x.
@group(1) @binding(2) var lmtex: texture_2d<f32>;
struct VsOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) normal: vec3<f32>,
    @location(1) uv: vec2<f32>,
    @location(2) color: vec4<f32>,
    @location(3) world: vec3<f32>,
    @location(4) uv2: vec2<f32>,
};
@vertex fn vs(@location(0) pos: vec3<f32>, @location(1) normal: vec3<f32>, @location(2) uv: vec2<f32>, @location(3) uv2: vec2<f32>, @location(4) color: vec4<f32>) -> VsOut {
    var o: VsOut;
    o.clip = u.mvp * vec4<f32>(pos, 1.0);
    o.normal = normal;
    o.uv = uv;
    o.color = color;
    o.world = pos;
    o.uv2 = uv2;
    return o;
}
// Sun-shadow factor at a world point: 1 = lit, ~0 = shadowed. Projects into the
// light's clip space, then 3×3 PCF compares against the shadow map (soft edges —
// better than Blitz's hard stencil shadows). Points outside the map stay lit, so
// distant geometry past the shadow region isn't wrongly darkened. `ndl` slopes
// the depth bias to kill acne on grazing surfaces.
fn sun_shadow(world: vec3<f32>, ndl: f32) -> f32 {
    let lc = u.light_vp * vec4<f32>(world, 1.0);
    let ndc = lc.xyz / lc.w;
    let uv = vec2<f32>(ndc.x * 0.5 + 0.5, ndc.y * -0.5 + 0.5);
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0 || ndc.z > 1.0 || ndc.z < 0.0) {
        return 1.0;
    }
    let bias = max(0.0015 * (1.0 - ndl), 0.0004);
    let texel = 1.0 / 2048.0;
    var sum = 0.0;
    for (var x = -1; x <= 1; x = x + 1) {
        for (var y = -1; y <= 1; y = y + 1) {
            let off = vec2<f32>(f32(x), f32(y)) * texel;
            sum = sum + textureSampleCompareLevel(shadow_map, shadow_samp, uv + off, ndc.z - bias);
        }
    }
    return sum / 9.0;
}
// Dappled sunlight ("broken cloud cover"): a soft, world-fixed pattern over
// world XZ that dims the SUN term in broad patches — so the ground isn't lit
// flat-uniform, and the player walks through sun and shade as they move (the
// pattern is pinned to the world, not the camera, so the motion is free — no
// time uniform needed). Returns a lit-fraction in [1-strength, 1]; only the
// directional term is affected (ambient + point lights are untouched), like a
// real cloud shadow. Cheap rotated-sin fbm → soft, non-stripey patches.
fn cloud_dapple(p: vec2<f32>) -> f32 {
    let a = sin(p.x * 0.9 + p.y * 0.5) + sin(p.x * 0.4 - p.y * 1.1);
    let b = sin(p.x * 1.7 - p.y * 0.8) + sin(p.x * 0.6 + p.y * 1.9);
    let n = (a + b * 0.5) / 3.0;            // ~[-1, 1]
    let mask = smoothstep(-0.35, 0.55, n);  // soft, biased toward lit
    return mix(0.48, 1.0, mask);            // shaded patches keep ~48% sun
}
@fragment fn fs(in: VsOut) -> @location(0) vec4<f32> {
    let c = textureSample(tex, samp, in.uv);
    if (c.a < 0.5) { discard; }
    let N = normalize(in.normal);
    let L = normalize(u.light_dir);
    // Form shading: the side facing the sun gets the directional light, the side
    // facing away falls to ambient only (a real lit/dark gradient on the object,
    // not flat). `sh` (the cast-shadow map) darkens the lit term further. The
    // zone ambient is the floor, so nothing goes fully black.
    let ndl = max(dot(N, L), 0.0);
    let sh = sun_shadow(in.world, ndl);
    let diff = ndl * u.light_intensity * sh * cloud_dapple(in.world.xz * 0.05);
    // Dynamic point lights (torches, braziers, glowing props): add their colour
    // by distance falloff and the surface's facing, on top of sun + ambient.
    // They illuminate but don't cast shadows (matching Blitz's point lights).
    var point = vec3<f32>(0.0);
    let nlights = u32(u.num_lights);
    for (var i = 0u; i < nlights; i = i + 1u) {
        let pr = u.lights[i].pos_range;
        let to_l = pr.xyz - in.world;
        let d = length(to_l);
        if (d < pr.w) {
            let a = 1.0 - d / pr.w; // smooth quadratic falloff to the range edge
            let pndl = max(dot(N, to_l / max(d, 0.001)), 0.0);
            point = point + u.lights[i].color.rgb * (a * a) * pndl;
        }
    }
    // Hemispheric ambient: skylight is cool + a touch brighter from ABOVE and
    // warmer + darker from BELOW (ground bounce), instead of one flat term — so
    // up-facing surfaces catch a cool sky glow and undersides fall into a warmer
    // shade, giving objects real grounded depth. Centred so the overall brightness
    // at a vertical face ~matches the old flat ambient. (Beyond Blitz's flat term.)
    let up = N.y * 0.5 + 0.5;                                 // 1 up .. 0 down
    let sky_amb = u.ambient * vec3<f32>(0.90, 1.02, 1.32);   // cool + brighter (sky)
    let gnd_amb = u.ambient * vec3<f32>(1.02, 0.84, 0.60);   // warm + darker (ground bounce)
    let amb = mix(gnd_amb, sky_amb, up);
    let shade = amb + vec3<f32>(diff) + point;
    // Baked lightmap (1.0 for non-lightmapped meshes via the grey default).
    let lm = textureSample(lmtex, samp, in.uv2).rgb * 2.0;
    let lit = c.rgb * in.color.rgb * shade * lm;
    // Sun backlight rim: when looking TOWARD the sun (the view ray into the scene
    // aligns with the sun), the silhouette edges of geometry (grazing view) catch a
    // warm scattered glow — sunlight wrapping around object edges and through
    // foliage. Tied to daylight via the ambient level so it doesn't glow at night
    // (no day-factor uniform needed). Beyond Blitz.
    let V = normalize(u.eye - in.world);
    let dayish = smoothstep(0.15, 0.40, dot(u.ambient, vec3<f32>(0.333)));
    let toward = max(dot(L, -V), 0.0);                       // 1 = looking at the sun
    let edge = pow(1.0 - max(dot(N, V), 0.0), 5.0);          // tight silhouette grazing
    let rim = vec3<f32>(1.0, 0.86, 0.62) * pow(toward, 6.0) * edge * dayish * 0.35;
    // Distance fog toward the sky/fog colour.
    let dist = distance(in.world, u.eye);
    let f = clamp((dist - u.fog_near) / max(u.fog_far - u.fog_near, 1.0), 0.0, 1.0);
    // Alpha = texture-alpha × vertex-alpha (1.0 for opaque meshes → no blend).
    return vec4<f32>(mix(lit + rim, u.fog_color, f), c.a * in.color.a);
}
"#;

/// Zenith (top-of-sky) colour derived from the horizon/fog colour: bluer and a
/// touch darker, so the gradient reads as sky. Clamped to [0,1].
pub fn sky_zenith(horizon: [f32; 3]) -> [f32; 3] {
    [
        (horizon[0] * 0.45).clamp(0.0, 1.0),
        (horizon[1] * 0.6 + 0.05).clamp(0.0, 1.0),
        (horizon[2] * 0.85 + 0.18).clamp(0.0, 1.0),
    ]
}

/// Fullscreen sky drawn at the far plane (no depth write) so the world's
/// geometry renders over it. Falls back to a vertical fog gradient; when a sky
/// texture is set (the area's `SkyTexID`) it's sampled by screen UV, panned
/// horizontally with the camera yaw, and faded into the gradient at the horizon.
pub struct SkyPipeline {
    pipeline: wgpu::RenderPipeline,
    buf: wgpu::Buffer,
    bind: wgpu::BindGroup,
    tex_bgl: wgpu::BindGroupLayout,
    sampler: wgpu::Sampler,
    tex_bind: wgpu::BindGroup,
    has_texture: bool,
    cloud_bind: wgpu::BindGroup,
    has_clouds: bool,
    stars_bind: wgpu::BindGroup,
    has_stars: bool,
}

const SKY_SHADER: &str = r#"
struct SkyU {
    top: vec4<f32>,
    bottom: vec4<f32>,
    params: vec4<f32>,
    params2: vec4<f32>,
    inv_vp: mat4x4<f32>, // inverse(view_proj) — unprojects a pixel to a world ray
    eye: vec4<f32>,      // camera world position (xyz)
    sun: vec4<f32>,      // sun/celestial direction (xyz, normalized; points toward the sun)
};
@group(0) @binding(0) var<uniform> sky: SkyU;
@group(1) @binding(0) var skytex: texture_2d<f32>;
@group(1) @binding(1) var skysamp: sampler;
@group(2) @binding(0) var cloudtex: texture_2d<f32>;
@group(2) @binding(1) var cloudsamp: sampler;
@group(3) @binding(0) var starstex: texture_2d<f32>;
@group(3) @binding(1) var starssamp: sampler;
struct VO { @builtin(position) pos: vec4<f32>, @location(0) ndc: vec2<f32> };
@vertex fn vs(@builtin(vertex_index) vi: u32) -> VO {
    var p = array<vec2<f32>, 3>(vec2<f32>(-1.0, -1.0), vec2<f32>(3.0, -1.0), vec2<f32>(-1.0, 3.0));
    var o: VO;
    let xy = p[vi];
    o.pos = vec4<f32>(xy, 1.0, 1.0); // z = 1 → far plane
    o.ndc = xy;                      // pass clip-space xy for per-pixel ray unprojection
    return o;
}
// Hash a 3D cell index to [0,1) — cheap, decorrelated per axis.
fn hash31(p: vec3<f32>) -> f32 {
    var q = fract(p * 0.3183099 + vec3<f32>(0.71, 0.113, 0.419));
    q = q + dot(q, q.yzx + 19.19);
    return fract((q.x + q.y) * q.z);
}
// Procedural starfield from the WORLD ray direction: sparse bright points placed
// in a fine angular grid. Pole-safe — it's a function of the 3D direction, not a
// pinned 2D texture — so it fills the overhead sky with NO pole streaks. Returns
// additive intensity (0 = empty cell). `dir` should be the normalized view ray.
fn proc_stars(dir: vec3<f32>) -> f32 {
    let cell = floor(dir * 260.0);
    let h = hash31(cell);
    let present = step(0.9875, h);          // ~1.25% of cells host a star
    let b = fract(h * 113.7);               // decorrelated per-star brightness
    return present * (0.35 + 0.65 * b);
}
@fragment fn fs(i: VO) -> @location(0) vec4<f32> {
    // Fully WORLD-FIXED sky: a per-pixel world ray (unproject through
    // inverse(view_proj)) drives both axes, so the sky stays put as the camera
    // yaws AND pitches. Horizontal = world azimuth. Vertical = world elevation,
    // but SCALED (÷0.5) so the gradient/texture spread across the commonly-visible
    // sky band (horizon..~30°) instead of being squished across the full 90° dome
    // — that squish was the "thin band way up high". `sky_t` plays the role the old
    // screen-t did, but is world-locked.
    let far = sky.inv_vp * vec4<f32>(i.ndc.x, i.ndc.y, 1.0, 1.0);
    let ray = normalize(far.xyz / far.w - sky.eye.xyz);
    let az = atan2(ray.x, ray.z) * 0.15915494 + 0.5;   // world azimuth → [0,1)
    let sky_t = clamp(ray.y / 0.5, 0.0, 1.0);           // 0 horizon .. 1 by ~30° up
    // Texture V. The sky panoramics are skybox-style: the TOP ~half is sky+clouds
    // and the BOTTOM half is solid BLACK (below-horizon filler). Map ONLY the sky
    // half (v 0 at the zenith → ~0.47 at the horizon-cloud line) across the dome so
    // the black bottom is never sampled — that black was showing as a dark band
    // between the clouds and the horizon. `SKY_HORIZON_V` is the texture row where
    // sky meets black.
    let tv = (1.0 - sky_t) * 0.47;                       // sky-half only (skip black bottom)
    let grad = mix(sky.bottom.rgb, sky.top.rgb, sky_t);
    var col = grad;
    // The daytime sky texture + clouds FADE OUT as night falls (cross-fading to
    // the dark gradient + stars), like Blitz — instead of just dimming, which left
    // bright cloud bands in the night sky. `day_vis` = 1 by day → 0 at deep night
    // (the inverse of the star/night factor).
    let day_vis = 1.0 - sky.params2.y;
    // Pin fade — THE pole-streak fix. `sky_t` saturates at ray.y >= 0.5, pinning
    // the texture's V row (tv = 0) across the ENTIRE upper sky; sampling that pinned
    // row while the azimuth keeps varying smears it into vertical streaks. So fade
    // EVERY textured layer (sky texture, clouds, AND stars) to the clean gradient as
    // `sky_t` -> 1, i.e. by the pin point itself — nothing textured is ever sampled
    // in the pinned region, so the streaks cannot form. Keyed on `sky_t` (the
    // pinning coordinate), NOT on ray.y: a ray.y threshold above 0.5 leaves an
    // unfaded, already-pinned band (the bug — stars formerly faded only at ray.y
    // 0.82, so the whole 0.5..0.82 band streaked at night). Detail therefore lives
    // only in the horizon..~30° band where V still varies and the mapping behaves.
    let pin_fade = 1.0 - smoothstep(0.70, 0.99, sky_t);
    if (sky.params.x >= 0.5) {
        let uv = vec2<f32>(az + sky.params.y, tv);
        let tex = textureSample(skytex, skysamp, uv).rgb;
        // Reach the horizon: only a tiny fade into the fog in the lowest few degrees
        // (so the sky meets the fogged distant terrain smoothly) instead of the old
        // wide ramp that left a gradient gap. pin_fade keeps the zenith clean.
        let h = smoothstep(0.0, 0.07, sky_t) * pin_fade * day_vis;
        col = mix(grad, tex, h);
    }
    if (sky.params.z >= 0.5) {
        let cuv = vec2<f32>(az + sky.params.w, tv);
        let c = textureSample(cloudtex, cloudsamp, cuv);
        let fade = smoothstep(0.12, 0.45, sky_t) * pin_fade * day_vis;
        col = mix(col, c.rgb, c.a * fade);
    }
    if (sky.params2.x >= 0.5 && sky.params2.y > 0.01) {
        // Stars (additive), composited after clouds. Same pin fade: stars live in
        // the horizon..mid band and are gone by the pin point, so the prominent
        // night streaks (the user's report) cannot appear. Overhead resolves to the
        // clean dark gradient.
        let suv = vec2<f32>(az + sky.params2.z, tv);
        let s = textureSample(starstex, starssamp, suv).rgb;
        let sfade = smoothstep(0.05, 0.40, sky_t) * pin_fade;
        col = col + s * sky.params2.y * sfade;
    }
    // Procedural OVERHEAD stars (pole-safe): the texture stars above only live in
    // the horizon band (they fade out by the pin point), leaving the zenith empty.
    // Fill it with a world-ray starfield that fades IN as the texture stars fade
    // OUT — gated on ray.y (NOT sky_t, which is saturated above the pin), so the
    // two crossfade cleanly into continuous coverage from horizon to zenith. Pole-
    // safe by construction, so no streaks. Night only (× the night factor).
    if (sky.params2.y > 0.01) {
        let pstar = proc_stars(ray) * smoothstep(0.33, 0.52, ray.y);
        col = col + vec3<f32>(0.90, 0.93, 1.0) * pstar * sky.params2.y;
    }
    // Celestial body at the sun direction (the same arc the shadows use): a bright
    // disc + warm glow by day, a pale-cool moon by night, cross-fading at dawn/dusk
    // via day_vis / night. Composited into the sky (far plane, no depth write) so
    // hills and trees correctly occlude it. `cd` = how directly the view ray points
    // at the body; the disc is a soft cosine threshold, the glow a wide cosine power.
    let sd = normalize(sky.sun.xyz);
    let cd = dot(ray, sd);
    // Fade the body out if it dips below the horizon (sun_dir clamps it just above,
    // so this mostly guards degenerate directions).
    let above = smoothstep(-0.08, 0.04, sd.y);
    // Warm/redden the disc as it nears the horizon (dawn/dusk).
    let low = 1.0 - smoothstep(0.06, 0.45, sd.y);
    let sun_col = mix(vec3<f32>(1.0, 0.96, 0.84), vec3<f32>(1.0, 0.5, 0.24), low);
    // Day sun: a bright disc (~3.5° radius, soft edge) plus a layered glow — a tight
    // hot corona, a medium falloff, and a wide faint sky-brightening near the sun.
    let sun_disc = smoothstep(0.9965, 0.9988, cd) * 1.4;
    let sun_glow = pow(max(cd, 0.0), 360.0) * 0.9
                 + pow(max(cd, 0.0), 30.0) * 0.30
                 + pow(max(cd, 0.0), 6.0) * 0.07;
    col = col + sun_col * (sun_disc + sun_glow) * day_vis * above;
    // Night moon: a smaller, pale-cool disc with a soft halo (no wide brightening).
    let moon_disc = smoothstep(0.9978, 0.9993, cd) * 1.0;
    let moon_glow = pow(max(cd, 0.0), 420.0) * 0.45 + pow(max(cd, 0.0), 70.0) * 0.12;
    col = col + vec3<f32>(0.82, 0.88, 1.0) * (moon_disc + moon_glow) * sky.params2.y * above;
    // Sunset / sunrise horizon glow: when the sun is LOW (`low`), the sky near the
    // sun's AZIMUTH at the horizon glows warm (low-sun forward scattering), fading
    // with azimuthal distance from the sun and with elevation. `low` keeps it off
    // at noon (sun high); `day_vis` keeps it at dawn/dusk and off at deep night.
    // Additive warm band — turns the flat dawn/dusk tint into a directional glow.
    let rh = normalize(vec2<f32>(ray.x, ray.z) + vec2<f32>(1.0e-5, 0.0));
    let sh = normalize(vec2<f32>(sd.x, sd.z) + vec2<f32>(1.0e-5, 0.0));
    let azi = max(dot(rh, sh), 0.0);                        // 1 toward the sun's azimuth
    let horiz = 1.0 - smoothstep(0.0, 0.32, ray.y);         // 1 at horizon → 0 up high
    let sunset = pow(azi, 3.0) * horiz * low * day_vis;
    col = col + vec3<f32>(1.0, 0.52, 0.22) * sunset * 0.7;
    return vec4<f32>(col, 1.0);
}
"#;

impl SkyPipeline {
    pub fn new(device: &wgpu::Device, color_format: wgpu::TextureFormat, sample_count: u32) -> SkyPipeline {
        let bgl = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("sky-u"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::FRAGMENT,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: None,
                },
                count: None,
            }],
        });
        let buf = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("sky"),
            size: 160, // four vec4 + inv_vp(mat4) + eye(vec4) + sun(vec4)
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        let bind = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: None,
            layout: &bgl,
            entries: &[wgpu::BindGroupEntry { binding: 0, resource: buf.as_entire_binding() }],
        });
        // Texture+sampler bind group (group 1). Starts as a 1×1 white default so
        // the pipeline always has a valid binding; params.x gates its use.
        let tex_bgl = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("sky-tex-bgl"),
            entries: &[
                wgpu::BindGroupLayoutEntry {
                    binding: 0,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Float { filterable: true },
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    count: None,
                },
                wgpu::BindGroupLayoutEntry {
                    binding: 1,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::Filtering),
                    count: None,
                },
            ],
        });
        let sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            label: Some("sky-sampler"),
            address_mode_u: wgpu::AddressMode::Repeat,
            address_mode_v: wgpu::AddressMode::ClampToEdge,
            address_mode_w: wgpu::AddressMode::ClampToEdge,
            mag_filter: wgpu::FilterMode::Linear,
            min_filter: wgpu::FilterMode::Linear,
            ..Default::default()
        });
        // 1×1 defaults — never sampled (params gates each), so no upload needed.
        let tex_bind = make_sky_tex_bind(device, None, &tex_bgl, &sampler, 1, 1, None);
        let cloud_bind = make_sky_tex_bind(device, None, &tex_bgl, &sampler, 1, 1, None);
        let stars_bind = make_sky_tex_bind(device, None, &tex_bgl, &sampler, 1, 1, None);
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("sky"),
            source: wgpu::ShaderSource::Wgsl(SKY_SHADER.into()),
        });
        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: None,
            bind_group_layouts: &[&bgl, &tex_bgl, &tex_bgl, &tex_bgl], // groups 1-3 share the tex layout
            push_constant_ranges: &[],
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("sky"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs",
                compilation_options: Default::default(),
                buffers: &[],
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: "fs",
                compilation_options: Default::default(),
                targets: &[Some(color_format.into())],
            }),
            primitive: wgpu::PrimitiveState::default(),
            // Behind everything: never writes depth, always passes so it fills
            // the cleared frame; geometry (depth Less) then draws on top.
            depth_stencil: Some(wgpu::DepthStencilState {
                format: DEPTH_FORMAT,
                depth_write_enabled: false,
                depth_compare: wgpu::CompareFunction::Always,
                stencil: Default::default(),
                bias: Default::default(),
            }),
            multisample: msaa(sample_count),
            multiview: None,
            cache: None,
        });
        SkyPipeline {
            pipeline,
            buf,
            bind,
            tex_bgl,
            sampler,
            tex_bind,
            has_texture: false,
            cloud_bind,
            has_clouds: false,
            stars_bind,
            has_stars: false,
        }
    }

    pub fn set_colors(&self, queue: &wgpu::Queue, top: [f32; 3], bottom: [f32; 3]) {
        let data: [f32; 8] = [top[0], top[1], top[2], 1.0, bottom[0], bottom[1], bottom[2], 1.0];
        queue.write_buffer(&self.buf, 0, bytemuck::cast_slice(&data));
    }

    /// Per-frame params. The sky is now WORLD-FIXED via the per-pixel ray (see the
    /// shader + [`set_camera`](Self::set_camera)), so the camera `yaw` no longer
    /// drives the offsets — they are slow TIME drifts (clouds drift, stars wheel
    /// slowly) layered on top of the world-azimuth sampling. `yaw` is kept in the
    /// signature for call-site compatibility but unused.
    pub fn set_frame(&self, queue: &wgpu::Queue, _yaw: f32, time: f32, night: f32) {
        let has_sky = if self.has_texture { 1.0 } else { 0.0 };
        let has_clouds = if self.has_clouds { 1.0 } else { 0.0 };
        let has_stars = if self.has_stars { 1.0 } else { 0.0 };
        let sky_off = 0.0; // sky texture is static in world space
        let cloud_off = time * 0.004; // clouds drift
        let star_off = time * 0.0006; // stars wheel very slowly
        // params = [has_sky, sky_off, has_clouds, cloud_off];
        // params2 = [has_stars, night, star_off, _].
        let params: [f32; 8] = [
            has_sky, sky_off, has_clouds, cloud_off,
            has_stars, night.clamp(0.0, 1.0), star_off, 0.0,
        ];
        queue.write_buffer(&self.buf, 32, bytemuck::cast_slice(&params));
    }

    /// Per-frame camera + sun state for the world-fixed sky: `inv_view_proj`
    /// (column-major, = `inverse(view_proj)`), the camera world position, and the
    /// sun/celestial direction (points toward the sun; the same arc the shadows
    /// use). The shader unprojects each pixel to a world ray and draws a sun disc /
    /// moon where the ray points at `sun`.
    pub fn set_camera(&self, queue: &wgpu::Queue, inv_view_proj: &[f32; 16], eye: [f32; 3], sun: [f32; 3]) {
        // Normalize the sun direction here so the shader can assume a unit vector
        // (a degenerate/zero dir falls back to straight up, harmless).
        let len = (sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]).sqrt();
        let s = if len > 1e-4 { [sun[0] / len, sun[1] / len, sun[2] / len] } else { [0.0, 1.0, 0.0] };
        let mut data = [0.0f32; 24];
        data[..16].copy_from_slice(inv_view_proj);
        data[16] = eye[0];
        data[17] = eye[1];
        data[18] = eye[2];
        data[19] = 1.0;
        data[20] = s[0];
        data[21] = s[1];
        data[22] = s[2];
        data[23] = 1.0;
        queue.write_buffer(&self.buf, 64, bytemuck::cast_slice(&data));
    }

    /// Upload the area's sky texture (RGBA8). Replaces any previous one and
    /// enables textured-sky rendering.
    pub fn set_texture(&mut self, device: &wgpu::Device, queue: &wgpu::Queue, width: u32, height: u32, rgba: &[u8]) {
        if width == 0 || height == 0 || rgba.len() < (width * height * 4) as usize {
            return;
        }
        self.tex_bind = make_sky_tex_bind(device, Some(queue), &self.tex_bgl, &self.sampler, width, height, Some(rgba));
        self.has_texture = true;
    }

    /// Clear the sky texture (revert to the plain gradient).
    pub fn clear_texture(&mut self) {
        self.has_texture = false;
    }

    /// Upload the area's cloud texture (RGBA8 with alpha) for the drifting cloud
    /// overlay. Replaces any previous one and enables clouds.
    pub fn set_cloud_texture(&mut self, device: &wgpu::Device, queue: &wgpu::Queue, width: u32, height: u32, rgba: &[u8]) {
        if width == 0 || height == 0 || rgba.len() < (width * height * 4) as usize {
            return;
        }
        self.cloud_bind = make_sky_tex_bind(device, Some(queue), &self.tex_bgl, &self.sampler, width, height, Some(rgba));
        self.has_clouds = true;
    }

    /// Clear the cloud overlay.
    pub fn clear_clouds(&mut self) {
        self.has_clouds = false;
    }

    /// Upload the area's night-stars texture (RGBA8). Shown additively, gated by
    /// the night factor passed to [`set_frame`].
    pub fn set_stars_texture(&mut self, device: &wgpu::Device, queue: &wgpu::Queue, width: u32, height: u32, rgba: &[u8]) {
        if width == 0 || height == 0 || rgba.len() < (width * height * 4) as usize {
            return;
        }
        self.stars_bind = make_sky_tex_bind(device, Some(queue), &self.tex_bgl, &self.sampler, width, height, Some(rgba));
        self.has_stars = true;
    }

    /// Clear the stars overlay.
    pub fn clear_stars(&mut self) {
        self.has_stars = false;
    }

    pub fn draw<'a>(&'a self, rp: &mut wgpu::RenderPass<'a>) {
        rp.set_pipeline(&self.pipeline);
        rp.set_bind_group(0, &self.bind, &[]);
        rp.set_bind_group(1, &self.tex_bind, &[]);
        rp.set_bind_group(2, &self.cloud_bind, &[]);
        rp.set_bind_group(3, &self.stars_bind, &[]);
        rp.draw(0..3, 0..1);
    }
}

/// Build a sky texture bind group; uploads `rgba` when a queue + pixels are
/// given (the 1×1 default passes `None`/`None` and is never sampled). The bind
/// group retains the texture via its view, so the handle can be dropped here.
fn make_sky_tex_bind(
    device: &wgpu::Device,
    queue: Option<&wgpu::Queue>,
    bgl: &wgpu::BindGroupLayout,
    sampler: &wgpu::Sampler,
    width: u32,
    height: u32,
    rgba: Option<&[u8]>,
) -> wgpu::BindGroup {
    let tex = device.create_texture(&wgpu::TextureDescriptor {
        label: Some("sky-tex"),
        size: wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format: wgpu::TextureFormat::Rgba8Unorm,
        usage: wgpu::TextureUsages::TEXTURE_BINDING | wgpu::TextureUsages::COPY_DST,
        view_formats: &[],
    });
    if let (Some(queue), Some(rgba)) = (queue, rgba) {
        queue.write_texture(
            wgpu::ImageCopyTexture {
                texture: &tex,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            rgba,
            wgpu::ImageDataLayout { offset: 0, bytes_per_row: Some(width * 4), rows_per_image: Some(height) },
            wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
        );
    }
    let view = tex.create_view(&Default::default());
    device.create_bind_group(&wgpu::BindGroupDescriptor {
        label: Some("sky-tex-bind"),
        layout: bgl,
        entries: &[
            wgpu::BindGroupEntry { binding: 0, resource: wgpu::BindingResource::TextureView(&view) },
            wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::Sampler(sampler) },
        ],
    })
}

/// Per-mesh normals (model space), computed from faces if absent.
pub fn mesh_normals(mesh: &B3dMesh) -> Vec<Vec3> {
    let has_n = mesh.normals.len() == mesh.positions.len();
    let mut normals: Vec<Vec3> = if has_n {
        mesh.normals.iter().map(|n| Vec3::from(*n)).collect()
    } else {
        vec![Vec3::ZERO; mesh.positions.len()]
    };
    if !has_n {
        for tri in mesh.indices.chunks_exact(3) {
            let (a, b, c) = (tri[0] as usize, tri[1] as usize, tri[2] as usize);
            let pa = Vec3::from(mesh.positions[a]);
            let fnv =
                (Vec3::from(mesh.positions[b]) - pa).cross(Vec3::from(mesh.positions[c]) - pa);
            normals[a] += fnv;
            normals[b] += fnv;
            normals[c] += fnv;
        }
        for n in &mut normals {
            *n = if n.length_squared() > 1e-12 {
                n.normalize()
            } else {
                Vec3::Y
            };
        }
    }
    normals
}

/// Bind-group layouts + sampler + pipeline for the textured shader, targeting
/// `color_format` with a `Depth32Float` depth buffer.
/// Generate a full RGBA8 mip chain by 2× box-filter downsampling. Element 0 is
/// the source (`w`×`h`); each subsequent level halves each dimension (floored,
/// min 1) down to 1×1. Edges clamp on odd dimensions. Pure — unit-tested.
fn rgba_mip_chain(rgba: &[u8], w: u32, h: u32) -> Vec<(u32, u32, Vec<u8>)> {
    let mut levels: Vec<(u32, u32, Vec<u8>)> = vec![(w.max(1), h.max(1), rgba.to_vec())];
    loop {
        let (pw, ph, prev) = levels.last().unwrap();
        let (pw, ph) = (*pw, *ph);
        if pw <= 1 && ph <= 1 {
            break;
        }
        let (nw, nh) = ((pw / 2).max(1), (ph / 2).max(1));
        let mut next = vec![0u8; (nw as usize) * (nh as usize) * 4];
        let at = |sx: u32, sy: u32| ((sy * pw + sx) * 4) as usize;
        for y in 0..nh {
            let (sy0, sy1) = ((2 * y).min(ph - 1), (2 * y + 1).min(ph - 1));
            for x in 0..nw {
                let (sx0, sx1) = ((2 * x).min(pw - 1), (2 * x + 1).min(pw - 1));
                let o = ((y * nw + x) * 4) as usize;
                for c in 0..4 {
                    let s = prev[at(sx0, sy0) + c] as u32
                        + prev[at(sx1, sy0) + c] as u32
                        + prev[at(sx0, sy1) + c] as u32
                        + prev[at(sx1, sy1) + c] as u32;
                    next[o + c] = (s / 4) as u8;
                }
            }
        }
        levels.push((nw, nh, next));
    }
    levels
}

#[cfg(test)]
mod mip_tests {
    use super::rgba_mip_chain;

    #[test]
    fn mip_chain_dims_and_average() {
        // 2×2 of distinct greys averages to one mid-grey texel at level 1.
        let src = vec![
            0, 0, 0, 255, 100, 100, 100, 255, 200, 200, 200, 255, 255, 255, 255, 255,
        ];
        let mips = rgba_mip_chain(&src, 2, 2);
        assert_eq!(mips.len(), 2); // 2×2 -> 1×1
        assert_eq!((mips[1].0, mips[1].1), (1, 1));
        // (0+100+200+255)/4 = 138 (integer).
        assert_eq!(mips[1].2, vec![138, 138, 138, 255]);
    }

    #[test]
    fn mip_chain_full_to_1x1() {
        // 8×4 should produce levels 8×4, 4×2, 2×1, 1×1.
        let src = vec![128u8; 8 * 4 * 4];
        let mips = rgba_mip_chain(&src, 8, 4);
        let dims: Vec<_> = mips.iter().map(|(w, h, _)| (*w, *h)).collect();
        assert_eq!(dims, vec![(8, 4), (4, 2), (2, 1), (1, 1)]);
        // Averaging a constant image stays constant.
        assert!(mips.last().unwrap().2.iter().all(|&b| b == 128));
    }
}

pub struct Pipeline {
    pub pipeline: wgpu::RenderPipeline,
    /// Alpha-blended variant for terrain splat overlays: SrcAlpha blend, depth
    /// test `LessEqual` (so co-planar overlays draw over the base), depth write
    /// off (overlays don't occlude actors/each other). Drawn after the opaque
    /// pass + actors.
    pub alpha_pipeline: wgpu::RenderPipeline,
    pub bgl_uniform: wgpu::BindGroupLayout,
    pub bgl_texture: wgpu::BindGroupLayout,
    pub sampler: wgpu::Sampler,
}

impl Pipeline {
    pub fn new(device: &wgpu::Device, color_format: wgpu::TextureFormat, sample_count: u32) -> Pipeline {
        let bgl_uniform = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("u"),
            entries: &[
                wgpu::BindGroupLayoutEntry {
                    binding: 0,
                    visibility: wgpu::ShaderStages::VERTEX_FRAGMENT,
                    ty: wgpu::BindingType::Buffer {
                        ty: wgpu::BufferBindingType::Uniform,
                        has_dynamic_offset: false,
                        min_binding_size: None,
                    },
                    count: None,
                },
                // Sun shadow map (depth) + comparison sampler — per frame, shared
                // by the scene + skinned pipelines.
                wgpu::BindGroupLayoutEntry {
                    binding: 1,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Depth,
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    count: None,
                },
                wgpu::BindGroupLayoutEntry {
                    binding: 2,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::Comparison),
                    count: None,
                },
            ],
        });
        let bgl_texture = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("tex"),
            entries: &[
                wgpu::BindGroupLayoutEntry {
                    binding: 0,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Float { filterable: true },
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    count: None,
                },
                wgpu::BindGroupLayoutEntry {
                    binding: 1,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::Filtering),
                    count: None,
                },
                // Lightmap texture (binding 2) — sampled with `samp`. A 1×1 grey
                // default for non-lightmapped meshes.
                wgpu::BindGroupLayoutEntry {
                    binding: 2,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Float { filterable: true },
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    count: None,
                },
            ],
        });
        // Trilinear + anisotropic filtering (the real client runs aniso x4).
        // `mipmap_filter: Linear` + a full mip chain (built in `texture_bind`)
        // stops distant/oblique terrain & scenery from aliasing into mud, and
        // `anisotropy_clamp` keeps grazing-angle surfaces sharp.
        let sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            address_mode_u: wgpu::AddressMode::Repeat,
            address_mode_v: wgpu::AddressMode::Repeat,
            mag_filter: wgpu::FilterMode::Linear,
            min_filter: wgpu::FilterMode::Linear,
            mipmap_filter: wgpu::FilterMode::Linear,
            anisotropy_clamp: 8,
            ..Default::default()
        });
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("scene"),
            source: wgpu::ShaderSource::Wgsl(SHADER.into()),
        });
        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: None,
            bind_group_layouts: &[&bgl_uniform, &bgl_texture],
            push_constant_ranges: &[],
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("scene"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs",
                compilation_options: Default::default(),
                buffers: &[wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<Vertex>() as u64,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    attributes: &wgpu::vertex_attr_array![0 => Float32x3, 1 => Float32x3, 2 => Float32x2, 3 => Float32x2, 4 => Float32x4],
                }],
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: "fs",
                compilation_options: Default::default(),
                targets: &[Some(color_format.into())],
            }),
            primitive: wgpu::PrimitiveState { cull_mode: None, ..Default::default() },
            depth_stencil: Some(wgpu::DepthStencilState {
                format: DEPTH_FORMAT,
                depth_write_enabled: true,
                depth_compare: wgpu::CompareFunction::Less,
                stencil: Default::default(),
                bias: Default::default(),
            }),
            multisample: msaa_a2c(sample_count),
            multiview: None,
            cache: None,
        });
        // Alpha-blended overlay variant (terrain splat): same shader/layout, but
        // SrcAlpha blending, depth test LessEqual + no depth write.
        let alpha_pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("scene-alpha"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs",
                compilation_options: Default::default(),
                buffers: &[wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<Vertex>() as u64,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    attributes: &wgpu::vertex_attr_array![0 => Float32x3, 1 => Float32x3, 2 => Float32x2, 3 => Float32x2, 4 => Float32x4],
                }],
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: "fs",
                compilation_options: Default::default(),
                targets: &[Some(wgpu::ColorTargetState {
                    format: color_format,
                    blend: Some(wgpu::BlendState::ALPHA_BLENDING),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
            }),
            primitive: wgpu::PrimitiveState { cull_mode: None, ..Default::default() },
            depth_stencil: Some(wgpu::DepthStencilState {
                format: DEPTH_FORMAT,
                depth_write_enabled: false,
                depth_compare: wgpu::CompareFunction::LessEqual,
                stencil: Default::default(),
                bias: Default::default(),
            }),
            multisample: msaa(sample_count),
            multiview: None,
            cache: None,
        });
        Pipeline {
            pipeline,
            alpha_pipeline,
            bgl_uniform,
            bgl_texture,
            sampler,
        }
    }

    /// Upload an `Image` (or a 1×1 fallback of `default_rgba`) as a mipped
    /// RGBA8 texture and return its view. The texture is kept alive by the view
    /// (wgpu views retain their texture), so it survives into the bind group.
    fn upload_tex(
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        img: Option<&Image>,
        default_rgba: [u8; 4],
    ) -> wgpu::TextureView {
        TEX_UPLOADS.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        let (w, h, data): (u32, u32, Vec<u8>) = match img {
            Some(i) if i.width > 0 && i.height > 0 => (i.width, i.height, i.rgba.clone()),
            _ => (1, 1, default_rgba.to_vec()),
        };
        // Build a full RGBA8 mip chain (level 0 = source) so the trilinear +
        // anisotropic sampler has something to sample — without mips, distant
        // terrain/scenery aliases into shimmer/mud. wgpu has no auto mip-gen.
        let mips = rgba_mip_chain(&data, w, h);
        let tex = device.create_texture(&wgpu::TextureDescriptor {
            label: Some("tex"),
            size: wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 },
            mip_level_count: mips.len() as u32,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::Rgba8Unorm,
            usage: wgpu::TextureUsages::TEXTURE_BINDING | wgpu::TextureUsages::COPY_DST,
            view_formats: &[],
        });
        for (level, (lw, lh, ldata)) in mips.iter().enumerate() {
            queue.write_texture(
                wgpu::ImageCopyTexture {
                    texture: &tex,
                    mip_level: level as u32,
                    origin: wgpu::Origin3d::ZERO,
                    aspect: wgpu::TextureAspect::All,
                },
                ldata,
                wgpu::ImageDataLayout {
                    offset: 0,
                    bytes_per_row: Some(lw * 4),
                    rows_per_image: Some(*lh),
                },
                wgpu::Extent3d { width: *lw, height: *lh, depth_or_array_layers: 1 },
            );
        }
        // Default view spans all mip levels.
        tex.create_view(&Default::default())
    }

    /// Upload an `Image` (or a 1×1 white fallback) as a texture bind group, with
    /// no lightmap (the common case — non-lightmapped meshes get a grey default).
    pub fn texture_bind(
        &self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        img: Option<&Image>,
    ) -> wgpu::BindGroup {
        self.texture_bind_lm(device, queue, img, None)
    }

    /// As [`texture_bind`](Self::texture_bind), plus an optional baked lightmap
    /// (the brush's 2nd texture slot). Absent → a 1×1 grey 0.5 so the shader's
    /// `lm * 2.0` resolves to 1.0 (no effect).
    pub fn texture_bind_lm(
        &self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        img: Option<&Image>,
        lightmap: Option<&Image>,
    ) -> wgpu::BindGroup {
        let view = Self::upload_tex(device, queue, img, [255, 255, 255, 255]);
        let lm_view = Self::upload_tex(device, queue, lightmap, [128, 128, 128, 255]);
        device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: None,
            layout: &self.bgl_texture,
            entries: &[
                wgpu::BindGroupEntry { binding: 0, resource: wgpu::BindingResource::TextureView(&view) },
                wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::Sampler(&self.sampler) },
                wgpu::BindGroupEntry { binding: 2, resource: wgpu::BindingResource::TextureView(&lm_view) },
            ],
        })
    }
}

// ── GPU skinning ───────────────────────────────────────────────────────────
// Upload each actor template mesh's STATIC vbuf once (bind-pose verts + per-
// vertex bone ids/weights); per frame write only the actor uniform (the bone
// matrix palette + model transform + colour). The vertex shader does linear-
// blend skinning, so no CPU re-skin / vertex re-upload per frame.

/// Max bones in the per-actor uniform palette. 64 mat4x4 = 4 KB (well under the
/// 64 KB uniform limit); actor skeletons are far smaller.
pub const MAX_BONES: usize = 64;

/// A skinned mesh vertex: bind-pose position/normal/uv + up to 4 bone indices
/// and weights (from [`rcce_data::B3dModel::skin_attributes`]).
#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
pub struct SkinnedVertex {
    pub pos: [f32; 3],
    pub normal: [f32; 3],
    pub uv: [f32; 2],
    pub bones: [u32; 4],
    pub weights: [f32; 4],
}

/// Per-actor skinning uniform: the column-major bone palette
/// ([`rcce_data::B3dModel::bone_palette`]), the instance model matrix
/// (column-major), and the actor tint. Matches the WGSL `Actor` block.
#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
pub struct ActorSkin {
    pub bones: [[f32; 16]; MAX_BONES],
    pub model: [f32; 16],
    pub color: [f32; 4],
}

impl ActorSkin {
    /// Build from a column-major bone palette (truncated/padded to MAX_BONES),
    /// a column-major model matrix, and a colour.
    pub fn new(palette: &[[f32; 16]], model: [f32; 16], color: [f32; 3]) -> ActorSkin {
        let mut bones = [IDENTITY16; MAX_BONES];
        for (i, m) in palette.iter().take(MAX_BONES).enumerate() {
            bones[i] = *m;
        }
        ActorSkin { bones, model, color: [color[0], color[1], color[2], 1.0] }
    }
}

/// Column-major 4×4 identity (for the default bone palette).
const IDENTITY16: [f32; 16] = [
    1.0, 0.0, 0.0, 0.0, //
    0.0, 1.0, 0.0, 0.0, //
    0.0, 0.0, 1.0, 0.0, //
    0.0, 0.0, 0.0, 1.0,
];

const SKIN_SHADER: &str = r#"
struct U {
    mvp: mat4x4<f32>,
    // MUST mirror the shared `Uniforms` layout (this binds the same group-0 buffer,
    // `bind0`): `light_vp` sits between `mvp` and `eye`. Omitting it shifted every
    // field below by 64 bytes, so the lighting read the light-VP matrix bytes —
    // ambient/light_dir ≈ 0 → GPU-skinned actors (RCCE_GPUSKIN) rendered BLACK.
    // Unused here (no shadow sampling on the skin pass) but required for alignment.
    light_vp: mat4x4<f32>,
    eye: vec3<f32>,
    fog_near: f32,
    fog_color: vec3<f32>,
    fog_far: f32,
    ambient: vec3<f32>,
    light_intensity: f32,
    light_dir: vec3<f32>,
    _pad: f32,
};
@group(0) @binding(0) var<uniform> u: U;
// Sun shadow map (same group-0 bind0 the scene pass uses) so GPU-skinned actors
// RECEIVE shadows like the CPU path does — they darken in shade instead of staying
// fully sunlit. Requires `light_vp` in U (added above) for the projection.
@group(0) @binding(1) var shadow_map: texture_depth_2d;
@group(0) @binding(2) var shadow_samp: sampler_comparison;
@group(1) @binding(0) var tex: texture_2d<f32>;
@group(1) @binding(1) var samp: sampler;
struct Actor {
    bones: array<mat4x4<f32>, 64>,
    model: mat4x4<f32>,
    color: vec4<f32>,
};
@group(2) @binding(0) var<uniform> a: Actor;
// PCF sun-shadow factor (1 = lit, ~0 = shadowed) — mirrors the scene shader.
fn sun_shadow(world: vec3<f32>, ndl: f32) -> f32 {
    let lc = u.light_vp * vec4<f32>(world, 1.0);
    let ndc = lc.xyz / lc.w;
    let uv = vec2<f32>(ndc.x * 0.5 + 0.5, ndc.y * -0.5 + 0.5);
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0 || ndc.z > 1.0 || ndc.z < 0.0) {
        return 1.0;
    }
    let bias = max(0.0015 * (1.0 - ndl), 0.0004);
    let texel = 1.0 / 2048.0;
    var sum = 0.0;
    for (var x = -1; x <= 1; x = x + 1) {
        for (var y = -1; y <= 1; y = y + 1) {
            let off = vec2<f32>(f32(x), f32(y)) * texel;
            sum = sum + textureSampleCompareLevel(shadow_map, shadow_samp, uv + off, ndc.z - bias);
        }
    }
    return sum / 9.0;
}
struct VsOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) normal: vec3<f32>,
    @location(1) uv: vec2<f32>,
    @location(2) color: vec3<f32>,
    @location(3) world: vec3<f32>,
};
@vertex fn vs(
    @location(0) pos: vec3<f32>,
    @location(1) normal: vec3<f32>,
    @location(2) uv: vec2<f32>,
    @location(3) bones: vec4<u32>,
    @location(4) weights: vec4<f32>,
) -> VsOut {
    let wsum = weights.x + weights.y + weights.z + weights.w;
    var sp = vec3<f32>(0.0);
    var sn = vec3<f32>(0.0);
    if (wsum > 0.0001) {
        for (var i = 0u; i < 4u; i = i + 1u) {
            let w = weights[i];
            if (w > 0.0) {
                let m = a.bones[bones[i]];
                sp = sp + w * (m * vec4<f32>(pos, 1.0)).xyz;
                sn = sn + w * (m * vec4<f32>(normal, 0.0)).xyz;
            }
        }
    } else {
        sp = pos;
        sn = normal;
    }
    let world = a.model * vec4<f32>(sp, 1.0);
    var o: VsOut;
    o.clip = u.mvp * world;
    o.normal = (a.model * vec4<f32>(sn, 0.0)).xyz;
    o.uv = uv;
    o.color = a.color.rgb;
    o.world = world.xyz;
    return o;
}
@fragment fn fs(in: VsOut) -> @location(0) vec4<f32> {
    let c = textureSample(tex, samp, in.uv);
    if (c.a < 0.5) { discard; }
    let N = normalize(in.normal);
    let L = normalize(u.light_dir);
    // Double-sided (abs) diffuse for thin actor meshes/hair, now darkened by the
    // sun-shadow map so GPU-skinned actors fall into shade like the CPU path.
    let ndl = abs(dot(N, L));
    let diff = ndl * u.light_intensity * sun_shadow(in.world, ndl);
    let shade = u.ambient + vec3<f32>(diff);
    let lit = c.rgb * in.color * shade;
    let dist = distance(in.world, u.eye);
    let f = clamp((dist - u.fog_near) / max(u.fog_far - u.fog_near, 1.0), 0.0, 1.0);
    return vec4<f32>(mix(lit, u.fog_color, f), 1.0);
}
"#;

/// GPU linear-blend-skinning pipeline. Reuses the camera (group 0) and texture
/// (group 1) layouts from the base [`Pipeline`]; adds the per-actor skinning
/// uniform (group 2).
pub struct SkinPipeline {
    pub pipeline: wgpu::RenderPipeline,
    pub bgl_actor: wgpu::BindGroupLayout,
}

impl SkinPipeline {
    pub fn new(device: &wgpu::Device, color_format: wgpu::TextureFormat, base: &Pipeline, sample_count: u32) -> SkinPipeline {
        let bgl_actor = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("actor-skin"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::VERTEX,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: None,
                },
                count: None,
            }],
        });
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("skin"),
            source: wgpu::ShaderSource::Wgsl(SKIN_SHADER.into()),
        });
        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("skin"),
            bind_group_layouts: &[&base.bgl_uniform, &base.bgl_texture, &bgl_actor],
            push_constant_ranges: &[],
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("skin"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs",
                compilation_options: Default::default(),
                buffers: &[wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<SkinnedVertex>() as u64,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    attributes: &wgpu::vertex_attr_array![0 => Float32x3, 1 => Float32x3, 2 => Float32x2, 3 => Uint32x4, 4 => Float32x4],
                }],
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: "fs",
                compilation_options: Default::default(),
                targets: &[Some(color_format.into())],
            }),
            primitive: wgpu::PrimitiveState { cull_mode: None, ..Default::default() },
            depth_stencil: Some(wgpu::DepthStencilState {
                format: DEPTH_FORMAT,
                depth_write_enabled: true,
                depth_compare: wgpu::CompareFunction::Less,
                stencil: Default::default(),
                bias: Default::default(),
            }),
            multisample: msaa_a2c(sample_count),
            multiview: None,
            cache: None,
        });
        SkinPipeline { pipeline, bgl_actor }
    }

    /// Create the per-actor uniform buffer + bind group (group 2). Update it each
    /// frame with [`update_actor`](Self::update_actor).
    pub fn make_actor(&self, device: &wgpu::Device) -> (wgpu::Buffer, wgpu::BindGroup) {
        let buf = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("actor-skin"),
            size: std::mem::size_of::<ActorSkin>() as u64,
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        let bind = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("actor-skin"),
            layout: &self.bgl_actor,
            entries: &[wgpu::BindGroupEntry { binding: 0, resource: buf.as_entire_binding() }],
        });
        (buf, bind)
    }

    pub fn update_actor(&self, queue: &wgpu::Queue, buf: &wgpu::Buffer, skin: &ActorSkin) {
        queue.write_buffer(buf, 0, bytemuck::bytes_of(skin));
    }
}

/// Build the static skinned vertex buffer for `mesh` (bind-pose pos/normal/uv +
/// the per-vertex bone ids/weights). Uploaded once; the pose comes from the
/// per-frame bone palette in the shader.
pub fn build_skinned_vbuf(
    device: &wgpu::Device,
    mesh: &B3dMesh,
    bone_ids: &[[u32; 4]],
    weights: &[[f32; 4]],
) -> wgpu::Buffer {
    let normals = mesh_normals(mesh);
    let has_uv = mesh.uvs.len() == mesh.positions.len();
    let verts: Vec<SkinnedVertex> = mesh
        .positions
        .iter()
        .enumerate()
        .map(|(i, p)| SkinnedVertex {
            pos: *p,
            normal: normals[i].into(),
            uv: if has_uv { mesh.uvs[i] } else { [0.0, 0.0] },
            bones: bone_ids.get(i).copied().unwrap_or([0; 4]),
            weights: weights.get(i).copied().unwrap_or([0.0; 4]),
        })
        .collect();
    device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
        label: Some("skin-v"),
        contents: bytemuck::cast_slice(&verts),
        usage: wgpu::BufferUsages::VERTEX,
    })
}

/// A single uploaded mesh ready to draw. `tex_bind` and `ibuf` are
/// reference-counted so dynamic rebuilds can share cached uploads — the index
/// (topology) buffer is constant across animation frames, so it's cached and
/// reused instead of recreated every rebuild.
pub struct Drawable {
    pub vbuf: wgpu::Buffer,
    pub ibuf: Rc<wgpu::Buffer>,
    pub n_idx: u32,
    pub tex_bind: Rc<wgpu::BindGroup>,
    /// True for terrain splat-overlay surfaces (partial vertex alpha) — drawn in
    /// a second alpha-blended pass over the opaque base so paths blend into grass.
    pub alpha: bool,
    /// World-space bounding sphere (centre + radius) of the baked geometry. Used by
    /// the shadow pass to cull casters whose sphere can't project into the sun's
    /// orthographic shadow box. Cheap to compute (once at bake time for statics).
    pub center: [f32; 3],
    pub radius: f32,
}

/// World-space bounding sphere of `mesh` after the instance transform — the same
/// transform `bake_verts` applies, so the sphere matches the baked positions.
fn mesh_world_bounds(nrot: Mat3, scale: Vec3, trans: Vec3, mesh: &B3dMesh) -> ([f32; 3], f32) {
    if mesh.positions.is_empty() {
        return (trans.into(), 0.0);
    }
    let (mut lo, mut hi) = (Vec3::splat(f32::MAX), Vec3::splat(f32::MIN));
    for p in &mesh.positions {
        let w = trans + nrot * (Vec3::from(*p) * scale);
        lo = lo.min(w);
        hi = hi.max(w);
    }
    let c = (lo + hi) * 0.5;
    ((c).into(), (hi - c).length())
}

/// Whether a mesh is a splat overlay: it carries per-vertex colors and some are
/// not fully opaque. The opaque base surface (alpha all 1) and colourless props
/// render in the normal opaque pass.
pub fn mesh_is_alpha_overlay(mesh: &B3dMesh) -> bool {
    !mesh.colors.is_empty() && mesh.colors.iter().any(|c| c[3] < 0.99)
}

/// Cache of constant index (topology) buffers, keyed like [`TexCache`].
pub type IndexCache = HashMap<String, Rc<wgpu::Buffer>>;

/// Bake one mesh into world-space vertex/index buffers (no texture). The
/// instance's rotation/scale/translation are applied per vertex.
fn bake_mesh(
    device: &wgpu::Device,
    nrot: Mat3,
    scale: Vec3,
    trans: Vec3,
    color: [f32; 3],
    mesh: &B3dMesh,
) -> (wgpu::Buffer, Rc<wgpu::Buffer>, u32) {
    let vbuf = bake_verts(device, nrot, scale, trans, color, mesh);
    let ibuf = Rc::new(bake_indices(device, mesh));
    (vbuf, ibuf, mesh.indices.len() as u32)
}

/// World-space vertex buffer for `mesh` (positions transformed by the instance).
/// Rebuilt every frame for animated meshes (positions change).
fn bake_verts(device: &wgpu::Device, nrot: Mat3, scale: Vec3, trans: Vec3, color: [f32; 3], mesh: &B3dMesh) -> wgpu::Buffer {
    let normals = mesh_normals(mesh);
    let has_uv = mesh.uvs.len() == mesh.positions.len();
    let has_uv2 = mesh.uvs2.len() == mesh.positions.len();
    // Texcoord transform from the texture's TEXS scale/offset (engine
    // `ScaleTexture`): u' = u*sx + tx. Terrain textures tile via this — without
    // it the 0..1 UVs stretch one texture across the whole ground (the smear).
    let (sx, sy) = (mesh.uv_scale[0], mesh.uv_scale[1]);
    let (tx, ty) = (mesh.uv_offset[0], mesh.uv_offset[1]);
    // Per-vertex colors (terrain splat painting): RGB tints the texture, alpha is
    // the blend weight (the alpha pass composites overlays over the opaque base).
    let has_color = mesh.colors.len() == mesh.positions.len();
    let verts: Vec<Vertex> = mesh
        .positions
        .iter()
        .enumerate()
        .map(|(i, p)| {
            let world = trans + nrot * (Vec3::from(*p) * scale);
            let uv = if has_uv {
                let u = mesh.uvs[i];
                [u[0] * sx + tx, u[1] * sy + ty]
            } else {
                [0.0, 0.0]
            };
            // Lightmap UV (raw — no TEXS transform; the lightmap owns the 2nd set).
            let uv2 = if has_uv2 { mesh.uvs2[i] } else { [0.0, 0.0] };
            let col = if has_color {
                let c = mesh.colors[i];
                [color[0] * c[0], color[1] * c[1], color[2] * c[2], c[3]]
            } else {
                [color[0], color[1], color[2], 1.0]
            };
            Vertex {
                pos: world.into(),
                normal: (nrot * normals[i]).into(),
                uv,
                uv2,
                color: col,
            }
        })
        .collect();
    device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
        label: Some("v"),
        contents: bytemuck::cast_slice(&verts),
        usage: wgpu::BufferUsages::VERTEX,
    })
}

/// Index (topology) buffer for `mesh` — constant across animation frames.
fn bake_indices(device: &wgpu::Device, mesh: &B3dMesh) -> wgpu::Buffer {
    device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
        label: Some("i"),
        contents: bytemuck::cast_slice(&mesh.indices),
        usage: wgpu::BufferUsages::INDEX,
    })
}

/// Instance rotation matrix (Y·X·Z) from `rot` radians `[pitch, yaw, roll]`,
/// matching Blitz `RotateEntity`'s yaw·pitch·roll order. The caller supplies
/// `rot` already in the render frame (e.g. scenery negates yaw for the
/// left-handed view — see `scenery_rot_radians`).
fn inst_nrot(rot: [f32; 3]) -> Mat3 {
    Mat3::from_mat4(
        Mat4::from_rotation_y(rot[1]) * Mat4::from_rotation_x(rot[0]) * Mat4::from_rotation_z(rot[2]),
    )
}

/// Build dynamic actor drawables, reusing cached texture binds keyed by
/// `keys[i]` (one per instance). Geometry (posed verts) is rebuilt every call;
/// only the texture upload is cached. No ground plane.
pub fn build_actor_drawables_cached(
    device: &wgpu::Device,
    queue: &wgpu::Queue,
    pipeline: &Pipeline,
    instances: &[SceneInstance],
    keys: &[String],
    cache: &mut TexCache,
    idx_cache: &mut IndexCache,
) -> Vec<Drawable> {
    let mut drawables = Vec::new();
    for (ii, inst) in instances.iter().enumerate() {
        let nrot = inst_nrot(inst.rot);
        let scale = Vec3::from(inst.scale);
        let trans = Vec3::from(inst.translation);
        let key = keys.get(ii).map(String::as_str).unwrap_or("");
        for (mi, mesh) in inst.model.meshes.iter().enumerate() {
            if mesh.positions.is_empty() || mesh.indices.is_empty() {
                continue;
            }
            // Posed vertices change every frame; the index topology is constant,
            // so cache + reuse the index buffer instead of recreating it.
            let vbuf = bake_verts(device, nrot, scale, trans, inst.color, mesh);
            let ckey = format!("{key}:{mi}");
            let ibuf = idx_cache
                .entry(ckey.clone())
                .or_insert_with(|| Rc::new(bake_indices(device, mesh)))
                .clone();
            let n_idx = mesh.indices.len() as u32;
            let tex = inst.textures.get(mi).and_then(|t| t.as_ref());
            let tex_bind = cache
                .entry(ckey)
                .or_insert_with(|| Rc::new(pipeline.texture_bind(device, queue, tex)))
                .clone();
            let (center, radius) = mesh_world_bounds(nrot, scale, trans, mesh);
            drawables.push(Drawable { vbuf, ibuf, n_idx, tex_bind, alpha: mesh_is_alpha_overlay(mesh), center, radius });
        }
    }
    drawables
}

/// Bake every (instance, mesh) into a world-space [`Drawable`] (positions
/// transformed by the instance's rot/scale/translation). A dark ground plane
/// spanning the instances is appended (untextured, tinted) as the terrain base —
/// pass a non-finite `ground_y` (e.g. `f32::NAN`) to skip it for interior scenes
/// (the menu set) that carry their own floor and shouldn't show a green base.
pub fn build_drawables(
    device: &wgpu::Device,
    queue: &wgpu::Queue,
    pipeline: &Pipeline,
    instances: &[SceneInstance],
    ground_y: f32,
    cache: &mut TexCache,
) -> Vec<Drawable> {
    let (mut drawables, min, max) = build_instance_drawables(device, queue, pipeline, instances, cache);

    // Ground plane spanning the instances (skipped when ground_y is non-finite).
    if min.x <= max.x && ground_y.is_finite() {
        let pad = (max - min).length().max(20.0) * 0.4;
        let (gx0, gx1) = (min.x - pad, max.x + pad);
        let (gz0, gz1) = (min.z - pad, max.z + pad);
        let gcol = [0.13, 0.18, 0.14, 1.0];
        let n = [0.0, 1.0, 0.0];
        let v = |x: f32, z: f32| Vertex { pos: [x, ground_y, z], normal: n, uv: [0.0, 0.0], uv2: [0.0, 0.0], color: gcol };
        let verts = [v(gx0, gz0), v(gx1, gz0), v(gx1, gz1), v(gx0, gz1)];
        let idx: [u32; 6] = [0, 1, 2, 0, 2, 3];
        let vbuf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("gv"),
            contents: bytemuck::cast_slice(&verts),
            usage: wgpu::BufferUsages::VERTEX,
        });
        let ibuf = Rc::new(device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("gi"),
            contents: bytemuck::cast_slice(&idx),
            usage: wgpu::BufferUsages::INDEX,
        }));
        let gc = Vec3::new((gx0 + gx1) * 0.5, ground_y, (gz0 + gz1) * 0.5);
        let gr = (Vec3::new(gx1, ground_y, gz1) - gc).length();
        drawables.push(Drawable {
            vbuf,
            ibuf,
            n_idx: 6,
            tex_bind: cached_tex_bind(device, queue, pipeline, cache, None, None),
            alpha: false,
            center: gc.into(),
            radius: gr,
        });
    }
    drawables
}

/// Core: bake every (instance, mesh) into a world-space drawable; also returns
/// the bounding box (min,max) of the instance translations.
fn build_instance_drawables(
    device: &wgpu::Device,
    queue: &wgpu::Queue,
    pipeline: &Pipeline,
    instances: &[SceneInstance],
    cache: &mut TexCache,
) -> (Vec<Drawable>, Vec3, Vec3) {
    let mut drawables = Vec::new();
    let (mut min, mut max) = (Vec3::splat(f32::MAX), Vec3::splat(f32::MIN));

    for inst in instances {
        let nrot = inst_nrot(inst.rot);
        let scale = Vec3::from(inst.scale);
        let trans = Vec3::from(inst.translation);
        min = min.min(trans);
        max = max.max(trans);
        for (mi, mesh) in inst.model.meshes.iter().enumerate() {
            if mesh.positions.is_empty() || mesh.indices.is_empty() {
                continue;
            }
            let (vbuf, ibuf, n_idx) = bake_mesh(device, nrot, scale, trans, inst.color, mesh);
            let tex = inst.textures.get(mi).and_then(|t| t.as_ref());
            let lm = inst.lightmaps.get(mi).and_then(|t| t.as_ref());
            let (center, radius) = mesh_world_bounds(nrot, scale, trans, mesh);
            drawables.push(Drawable {
                vbuf,
                ibuf,
                n_idx,
                tex_bind: cached_tex_bind(device, queue, pipeline, cache, tex, lm),
                alpha: mesh_is_alpha_overlay(mesh),
                center,
                radius,
            });
        }
    }
    (drawables, min, max)
}

#[cfg(test)]
mod tests {
    use super::sky_zenith;

    #[test]
    fn zenith_is_bluer_and_darker_than_horizon() {
        // A typical pale-blue horizon/fog colour.
        let horizon = [0.45, 0.62, 0.82];
        let z = sky_zenith(horizon);
        // Bluer: blue channel dominates more than at the horizon.
        assert!(z[2] > z[0] && z[2] > z[1], "zenith should be blue-dominant: {z:?}");
        // Darker in red/green than the horizon (gradient brightens toward it).
        assert!(z[0] < horizon[0] && z[1] < horizon[1], "{z:?} vs {horizon:?}");
        // Stays in range.
        assert!(z.iter().all(|c| (0.0..=1.0).contains(c)));
    }

    #[test]
    fn zenith_clamps() {
        let z = sky_zenith([2.0, 2.0, 2.0]);
        assert!(z.iter().all(|c| *c <= 1.0));
    }
}
