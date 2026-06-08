//! 2D screen-space overlay: alpha-blended coloured rectangles drawn over the 3D
//! scene (HUD, health bars, nameplate backings, target markers). Pixel
//! coordinates with the origin at the top-left; converted to NDC at draw time
//! from the current framebuffer size. No depth — drawn after the world pass with
//! `LoadOp::Load`. (A bitmap-font text layer builds on this next.)

use bytemuck::{Pod, Zeroable};
use wgpu::util::DeviceExt;

/// Project a world point to screen pixels (top-left origin) using the
/// **column-major** view-projection matrix from [`crate::view_proj`] — the same
/// `clip = vp * world` the GPU uses (`gpu.rs`), so projected nameplates / picks
/// land exactly on the rendered geometry. Returns `None` if behind the camera.
pub fn project(vp: &[f32; 16], world: [f32; 3], screen_w: f32, screen_h: f32) -> Option<(f32, f32)> {
    let (x, y, z) = (world[0], world[1], world[2]);
    // clip.r = Σ_c M[r][c]·world_c, with M column-major so M[r][c] = vp[c*4 + r].
    let cx = vp[0] * x + vp[4] * y + vp[8] * z + vp[12];
    let cy = vp[1] * x + vp[5] * y + vp[9] * z + vp[13];
    let cw = vp[3] * x + vp[7] * y + vp[11] * z + vp[15];
    if cw <= 0.0001 {
        return None;
    }
    let ndc_x = cx / cw;
    let ndc_y = cy / cw;
    Some((
        (ndc_x * 0.5 + 0.5) * screen_w,
        (1.0 - (ndc_y * 0.5 + 0.5)) * screen_h,
    ))
}

/// Unproject a screen pixel (top-left origin) to the world point where the
/// camera ray through it crosses the horizontal plane `y = plane_y`. `vp` is the
/// column-major view-projection produced by [`crate::view_proj`] — the same
/// matrix the GPU renders with (`clip = vp * world`), so the result lands under
/// the rendered ground pixel. Depth is the wgpu `[0,1]` convention. Returns
/// `None` if the ray is (near-)parallel to the plane or the hit is behind the
/// camera. Used for click-to-move: a left-click on terrain → walk-there point.
pub fn unproject_ground(
    vp: &[f32; 16],
    screen_w: f32,
    screen_h: f32,
    px: f32,
    py: f32,
    plane_y: f32,
) -> Option<[f32; 3]> {
    use glam::{Mat4, Vec4};
    let inv = Mat4::from_cols_array(vp).inverse();
    let ndc_x = px / screen_w * 2.0 - 1.0;
    let ndc_y = 1.0 - py / screen_h * 2.0;
    let unproj = |ndc_z: f32| -> glam::Vec3 {
        let p = inv * Vec4::new(ndc_x, ndc_y, ndc_z, 1.0);
        p.truncate() / p.w
    };
    let near = unproj(0.0);
    let far = unproj(1.0);
    let dir = far - near;
    if dir.y.abs() < 1e-6 {
        return None;
    }
    let t = (plane_y - near.y) / dir.y;
    if t < 0.0 {
        return None;
    }
    let hit = near + dir * t;
    Some([hit.x, hit.y, hit.z])
}

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct V2 {
    pos: [f32; 2],
    color: [f32; 4],
}

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct TV {
    pos: [f32; 2],
    uv: [f32; 2],
    color: [f32; 4],
}

const SHADER: &str = r#"
struct VsOut { @builtin(position) clip: vec4<f32>, @location(0) color: vec4<f32> };
@vertex fn vs(@location(0) pos: vec2<f32>, @location(1) color: vec4<f32>) -> VsOut {
    var o: VsOut;
    o.clip = vec4<f32>(pos, 0.0, 1.0);
    o.color = color;
    return o;
}
@fragment fn fs(in: VsOut) -> @location(0) vec4<f32> { return in.color; }
"#;

const TEX_SHADER: &str = r#"
struct VsOut { @builtin(position) clip: vec4<f32>, @location(0) uv: vec2<f32>, @location(1) color: vec4<f32> };
@vertex fn vs(@location(0) pos: vec2<f32>, @location(1) uv: vec2<f32>, @location(2) color: vec4<f32>) -> VsOut {
    var o: VsOut;
    o.clip = vec4<f32>(pos, 0.0, 1.0);
    o.uv = uv;
    o.color = color;
    return o;
}
@group(0) @binding(0) var t: texture_2d<f32>;
@group(0) @binding(1) var s: sampler;
@fragment fn fs(in: VsOut) -> @location(0) vec4<f32> {
    return textureSample(t, s, in.uv) * in.color;
}
"#;

/// One queued draw command. Commands are emitted in submission order so z-order
/// follows call order — a coloured rect drawn after a textured quad sits on top
/// of it (lets slot numbers / cooldown shading layer over a slot texture).
enum Cmd {
    /// Filled rect: x, y, w, h, RGBA.
    Rect(f32, f32, f32, f32, [f32; 4]),
    /// Textured quad: x, y, w, h, uv (u0,v0,u1,v1), tint, texture key.
    Tex(f32, f32, f32, f32, [f32; 4], [f32; 4], String),
}

/// Accumulates draw commands, then emits them over a target view in one pass,
/// interleaving coloured and textured runs in submission order.
pub struct Overlay {
    pipeline: wgpu::RenderPipeline,
    cmds: Vec<Cmd>,
    // Textured-quad layer (GUI icons, the XP bar, item icons).
    tex_pipeline: wgpu::RenderPipeline,
    tex_layout: wgpu::BindGroupLayout,
    sampler: wgpu::Sampler,
    textures: std::collections::HashMap<String, wgpu::BindGroup>,
}

impl Overlay {
    pub fn new(device: &wgpu::Device, color_format: wgpu::TextureFormat) -> Overlay {
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("overlay"),
            source: wgpu::ShaderSource::Wgsl(SHADER.into()),
        });
        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: None,
            bind_group_layouts: &[],
            push_constant_ranges: &[],
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("overlay"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs",
                compilation_options: Default::default(),
                buffers: &[wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<V2>() as u64,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    attributes: &wgpu::vertex_attr_array![0 => Float32x2, 1 => Float32x4],
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
            primitive: wgpu::PrimitiveState::default(),
            depth_stencil: None,
            multisample: wgpu::MultisampleState::default(),
            multiview: None,
            cache: None,
        });
        // Textured-quad pipeline (GUI .bmp icons, XP bar, item icons).
        let tex_shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("overlay-tex"),
            source: wgpu::ShaderSource::Wgsl(TEX_SHADER.into()),
        });
        let tex_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("overlay-tex-bgl"),
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
            label: Some("overlay-sampler"),
            address_mode_u: wgpu::AddressMode::ClampToEdge,
            address_mode_v: wgpu::AddressMode::ClampToEdge,
            address_mode_w: wgpu::AddressMode::ClampToEdge,
            mag_filter: wgpu::FilterMode::Linear,
            min_filter: wgpu::FilterMode::Linear,
            ..Default::default()
        });
        let tex_pl = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: None,
            bind_group_layouts: &[&tex_layout],
            push_constant_ranges: &[],
        });
        let tex_pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("overlay-tex"),
            layout: Some(&tex_pl),
            vertex: wgpu::VertexState {
                module: &tex_shader,
                entry_point: "vs",
                compilation_options: Default::default(),
                buffers: &[wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<TV>() as u64,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    attributes: &wgpu::vertex_attr_array![0 => Float32x2, 1 => Float32x2, 2 => Float32x4],
                }],
            },
            fragment: Some(wgpu::FragmentState {
                module: &tex_shader,
                entry_point: "fs",
                compilation_options: Default::default(),
                targets: &[Some(wgpu::ColorTargetState {
                    format: color_format,
                    blend: Some(wgpu::BlendState::ALPHA_BLENDING),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
            }),
            primitive: wgpu::PrimitiveState::default(),
            depth_stencil: None,
            multisample: wgpu::MultisampleState::default(),
            multiview: None,
            cache: None,
        });

        Overlay {
            pipeline,
            cmds: Vec::new(),
            tex_pipeline,
            tex_layout,
            sampler,
            textures: std::collections::HashMap::new(),
        }
    }

    pub fn clear(&mut self) {
        self.cmds.clear();
    }

    /// Upload a decoded GUI image once under `key`, building its bind group.
    /// Re-registering the same key replaces it. Skips empty images.
    pub fn register_texture(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        key: &str,
        width: u32,
        height: u32,
        rgba: &[u8],
    ) {
        if width == 0 || height == 0 || rgba.len() < (width * height * 4) as usize {
            return;
        }
        let tex = device.create_texture(&wgpu::TextureDescriptor {
            label: Some("overlay-gui-tex"),
            size: wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::Rgba8Unorm,
            usage: wgpu::TextureUsages::TEXTURE_BINDING | wgpu::TextureUsages::COPY_DST,
            view_formats: &[],
        });
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
        let view = tex.create_view(&Default::default());
        let bg = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("overlay-gui-bg"),
            layout: &self.tex_layout,
            entries: &[
                wgpu::BindGroupEntry { binding: 0, resource: wgpu::BindingResource::TextureView(&view) },
                wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::Sampler(&self.sampler) },
            ],
        });
        self.textures.insert(key.to_string(), bg);
    }

    /// Whether a texture is registered under `key`.
    pub fn has_texture(&self, key: &str) -> bool {
        self.textures.contains_key(key)
    }

    /// Queue a textured quad (full texture). Tint multiplies the sample
    /// (`[1,1,1,1]` = unmodified). No-op if `key` isn't registered (the caller
    /// can fall back to a text label / coloured rect).
    pub fn image(&mut self, x: f32, y: f32, w: f32, h: f32, key: &str, tint: [f32; 4]) {
        if self.textures.contains_key(key) {
            self.cmds.push(Cmd::Tex(x, y, w, h, [0.0, 0.0, 1.0, 1.0], tint, key.to_string()));
        }
    }

    /// Queue a textured quad sampling a sub-rect (uv in 0..1) of the texture.
    pub fn image_uv(&mut self, x: f32, y: f32, w: f32, h: f32, key: &str, uv: [f32; 4], tint: [f32; 4]) {
        if self.textures.contains_key(key) {
            self.cmds.push(Cmd::Tex(x, y, w, h, uv, tint, key.to_string()));
        }
    }

    /// Queue a filled rectangle (top-left origin, pixels, RGBA 0..1).
    pub fn rect(&mut self, x: f32, y: f32, w: f32, h: f32, color: [f32; 4]) {
        self.cmds.push(Cmd::Rect(x, y, w, h, color));
    }

    /// Draw `s` as 8×8 bitmap glyphs (one quad per lit pixel) at `scale` px per
    /// font pixel, top-left at `(x, y)`. Newlines advance a line.
    pub fn text(&mut self, x: f32, y: f32, scale: f32, s: &str, color: [f32; 4]) {
        let (mut cx, mut cy) = (x, y);
        for ch in s.chars() {
            if ch == '\n' {
                cx = x;
                cy += 9.0 * scale;
                continue;
            }
            let g = crate::font::glyph(ch as u8);
            for (row, bits) in g.iter().enumerate() {
                for col in 0..8u8 {
                    if bits & (1 << col) != 0 {
                        self.rect(cx + col as f32 * scale, cy + row as f32 * scale, scale, scale, color);
                    }
                }
            }
            cx += 9.0 * scale;
        }
    }

    /// `text` with a 1px dark drop-shadow for legibility over the 3D scene.
    pub fn text_shadow(&mut self, x: f32, y: f32, scale: f32, s: &str, color: [f32; 4]) {
        self.text(x + scale, y + scale, scale, s, [0.0, 0.0, 0.0, 0.7]);
        self.text(x, y, scale, s, color);
    }

    /// A `frac`-filled bar: dark background + a coloured foreground (e.g. HP).
    pub fn bar(&mut self, x: f32, y: f32, w: f32, h: f32, frac: f32, color: [f32; 4]) {
        self.rect(x - 1.0, y - 1.0, w + 2.0, h + 2.0, [0.0, 0.0, 0.0, 0.6]);
        let f = frac.clamp(0.0, 1.0);
        if f > 0.0 {
            self.rect(x, y, w * f, h, color);
        }
    }

    /// Draw all queued commands over `view` (loads existing contents), in
    /// submission order so z-order follows call order. Coloured rects and
    /// textured quads each accumulate into their own vertex buffer; a list of
    /// runs records the order and the per-run vertex range, and the render pass
    /// replays the runs. Consecutive same-kind (and same-texture) commands batch
    /// into one run. Clears the queue. No-op if nothing queued.
    pub fn render(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        view: &wgpu::TextureView,
        screen_w: f32,
        screen_h: f32,
    ) {
        if self.cmds.is_empty() {
            return;
        }
        let (sw, sh) = (screen_w.max(1.0), screen_h.max(1.0));
        let to_ndc = |px: f32, py: f32| [px / sw * 2.0 - 1.0, 1.0 - py / sh * 2.0];

        // A run is a contiguous range of vertices of one kind, drawn together.
        enum Run {
            Colored { start: u32, count: u32 },
            Textured { key: String, start: u32, count: u32 },
        }
        let mut cverts: Vec<V2> = Vec::new();
        let mut tverts: Vec<TV> = Vec::new();
        let mut runs: Vec<Run> = Vec::new();

        for cmd in &self.cmds {
            match cmd {
                Cmd::Rect(x, y, w, h, c) => {
                    // Extend the current run if it's also coloured, else open one.
                    let start = cverts.len() as u32;
                    let tl = to_ndc(*x, *y);
                    let tr = to_ndc(*x + *w, *y);
                    let br = to_ndc(*x + *w, *y + *h);
                    let bl = to_ndc(*x, *y + *h);
                    for p in [tl, tr, br, tl, br, bl] {
                        cverts.push(V2 { pos: p, color: *c });
                    }
                    match runs.last_mut() {
                        Some(Run::Colored { count, .. }) => *count += 6,
                        _ => runs.push(Run::Colored { start, count: 6 }),
                    }
                }
                Cmd::Tex(x, y, w, h, uv, c, key) => {
                    let start = tverts.len() as u32;
                    let [u0, v0, u1, v1] = *uv;
                    let tl = (to_ndc(*x, *y), [u0, v0]);
                    let tr = (to_ndc(*x + *w, *y), [u1, v0]);
                    let br = (to_ndc(*x + *w, *y + *h), [u1, v1]);
                    let bl = (to_ndc(*x, *y + *h), [u0, v1]);
                    for (p, uv) in [tl, tr, br, tl, br, bl] {
                        tverts.push(TV { pos: p, uv, color: *c });
                    }
                    // Batch only if the previous run is the SAME texture.
                    match runs.last_mut() {
                        Some(Run::Textured { key: k, count, .. }) if k == key => *count += 6,
                        _ => runs.push(Run::Textured { key: key.clone(), start, count: 6 }),
                    }
                }
            }
        }

        // Never hand wgpu a zero-length buffer.
        let cvbuf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("overlay-v"),
            contents: if cverts.is_empty() { &[0u8; 4] } else { bytemuck::cast_slice(&cverts) },
            usage: wgpu::BufferUsages::VERTEX,
        });
        let tvbuf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("overlay-tv"),
            contents: if tverts.is_empty() { &[0u8; 4] } else { bytemuck::cast_slice(&tverts) },
            usage: wgpu::BufferUsages::VERTEX,
        });

        let mut enc = device.create_command_encoder(&Default::default());
        {
            let mut rp = enc.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("overlay"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view,
                    resolve_target: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Load,
                        store: wgpu::StoreOp::Store,
                    },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
            });
            // Replay runs in submission order — z-order follows call order.
            for run in &runs {
                match run {
                    Run::Colored { start, count } => {
                        rp.set_pipeline(&self.pipeline);
                        rp.set_vertex_buffer(0, cvbuf.slice(..));
                        rp.draw(*start..(*start + *count), 0..1);
                    }
                    Run::Textured { key, start, count } => {
                        if let Some(bg) = self.textures.get(key) {
                            rp.set_pipeline(&self.tex_pipeline);
                            rp.set_vertex_buffer(0, tvbuf.slice(..));
                            rp.set_bind_group(0, bg, &[]);
                            rp.draw(*start..(*start + *count), 0..1);
                        }
                    }
                }
            }
        }
        queue.submit(Some(enc.finish()));
        self.clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // Angled overhead camera (eye above + behind the origin) looking at the
    // ground origin. A click at screen-centre must unproject to ≈(0,0,0) on the
    // y=0 plane; screen X must increase with world X (no roll); a click below
    // centre (lower on screen, top-left origin) must land nearer the camera
    // (greater Z, the eye's side). These pin the screen→world mapping that
    // click-to-move relies on, independent of any GPU.
    #[test]
    fn unproject_ground_centre_and_axes() {
        let vp = crate::view_proj([0.0, 50.0, 30.0], [0.0, 0.0, 0.0], 1.0);
        let (w, h) = (800.0, 600.0);

        let centre = unproject_ground(&vp, w, h, w * 0.5, h * 0.5, 0.0).expect("centre hits ground");
        assert!(centre[1].abs() < 1e-2, "hit is on the y=0 plane: {centre:?}");
        assert!(centre[0].abs() < 2.0 && centre[2].abs() < 2.0, "centre ~origin: {centre:?}");

        // The view is **left-handed** (matches the Blitz world). This test camera
        // at (0,50,30) looks toward −Z; viewing along −Z in a LH frame puts +X on
        // the viewer's left, so a screen point further RIGHT maps to a SMALLER
        // world X. (The gameplay rear-follow camera looks +Z, where screen-right →
        // +X — which is why NPCs land on the correct side vs the real client.)
        let left = unproject_ground(&vp, w, h, w * 0.25, h * 0.5, 0.0).expect("left hits ground");
        let right = unproject_ground(&vp, w, h, w * 0.75, h * 0.5, 0.0).expect("right hits ground");
        assert!(right[0] < left[0], "LH, −Z-facing cam: screen-right maps to −X: {left:?} {right:?}");

        let down = unproject_ground(&vp, w, h, w * 0.5, h * 0.75, 0.0).expect("down hits ground");
        assert!(down[2] > centre[2], "below centre lands nearer the camera (+Z): {down:?}");
    }

    // `project` must agree with the GPU matrix (clip = vp*world): the camera's
    // look-target lands at screen centre, and a point off the look axis lands
    // off-centre. This catches a row/column-major (transpose) mismatch — the
    // exact bug that misplaces nameplates and actor picking.
    #[test]
    fn project_centre_and_offaxis() {
        // Angled overhead camera (so the y=0 ground plane is not edge-on).
        let vp = crate::view_proj([0.0, 50.0, 30.0], [0.0, 0.0, 0.0], 1.0);
        let (w, h) = (800.0, 600.0);
        // The look-target must land at screen centre — the key transpose-catcher
        // (a row/column swap would not map the look point to the centre).
        let (cx, cy) = project(&vp, [0.0, 0.0, 0.0], w, h).expect("target projects");
        assert!((cx - w * 0.5).abs() < 1.0 && (cy - h * 0.5).abs() < 1.0, "look-target at centre: {cx},{cy}");
        // project is the inverse of unproject_ground (already tested): unproject a
        // screen pixel to the ground, re-project it, get the same pixel back.
        let g = unproject_ground(&vp, w, h, 520.0, 360.0, 0.0).expect("ground hit");
        let (rx, ry) = project(&vp, g, w, h).expect("reproject");
        assert!((rx - 520.0).abs() < 1.0 && (ry - 360.0).abs() < 1.0, "round-trip {rx},{ry}");
        // A world point off the look axis is not at the screen centre.
        let (ox, _) = project(&vp, [10.0, 0.0, 0.0], w, h).expect("offset projects");
        assert!((ox - w * 0.5).abs() > 5.0, "off-axis point is off-centre: {ox}");
    }

    // A ray that can't reach the plane (looking up, plane below) returns None.
    #[test]
    fn unproject_ground_miss() {
        // Eye at y=10 with a slight Z offset, looking UP toward +Y; the y=0
        // plane is behind the ray, so there is no forward intersection.
        let vp = crate::view_proj([0.0, 10.0, 1.0], [0.0, 40.0, 0.0], 1.0);
        assert!(unproject_ground(&vp, 800.0, 600.0, 400.0, 300.0, 0.0).is_none());
    }
}
