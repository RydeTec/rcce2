//! Headless verification of the 2D overlay: clear a target, draw some bars and
//! rectangles via rcce_render::Overlay, read back to a PNG. Confirms the
//! screen-space quad pipeline (pixel coords, alpha blend) without a window.
//!
//!   cargo run -p rcce-client --bin overlay_test --release -- [out.png]

use std::io::BufWriter;

fn main() {
    let out = std::env::args().nth(1).unwrap_or_else(|| "overlay_test.png".to_string());
    let (w, h) = (640u32, 360u32);

    let instance = wgpu::Instance::default();
    let adapter = pollster::block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
        power_preference: wgpu::PowerPreference::HighPerformance,
        force_fallback_adapter: false,
        compatible_surface: None,
    }))
    .expect("adapter");
    let (device, queue) = pollster::block_on(adapter.request_device(
        &wgpu::DeviceDescriptor {
            label: Some("overlay-test"),
            required_features: wgpu::Features::empty(),
            required_limits: wgpu::Limits::downlevel_defaults()
                .using_resolution(adapter.limits()),
            memory_hints: wgpu::MemoryHints::Performance,
        },
        None,
    ))
    .expect("device");

    let format = wgpu::TextureFormat::Rgba8Unorm;
    let target = device.create_texture(&wgpu::TextureDescriptor {
        label: Some("t"),
        size: wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 },
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
        view_formats: &[],
    });
    let view = target.create_view(&Default::default());

    // Clear pass (sky), then overlay draws over it.
    let mut enc = device.create_command_encoder(&Default::default());
    {
        enc.begin_render_pass(&wgpu::RenderPassDescriptor {
            label: Some("clear"),
            color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                view: &view,
                resolve_target: None,
                ops: wgpu::Operations {
                    load: wgpu::LoadOp::Clear(wgpu::Color { r: 0.45, g: 0.62, b: 0.82, a: 1.0 }),
                    store: wgpu::StoreOp::Store,
                },
            })],
            depth_stencil_attachment: None,
            timestamp_writes: None,
            occlusion_query_set: None,
        });
    }
    queue.submit(Some(enc.finish()));

    let mut overlay = rcce_render::Overlay::new(&device, format);
    // A HUD panel, three health bars at different fills, and a target marker.
    overlay.rect(8.0, 8.0, 240.0, 56.0, [0.0, 0.0, 0.0, 0.5]);
    overlay.bar(120.0, 80.0, 80.0, 8.0, 1.0, [0.2, 0.85, 0.2, 1.0]);
    overlay.bar(300.0, 140.0, 80.0, 8.0, 0.55, [0.9, 0.8, 0.1, 1.0]);
    overlay.bar(420.0, 220.0, 80.0, 8.0, 0.2, [0.9, 0.2, 0.2, 1.0]);
    overlay.rect(317.0, 175.0, 6.0, 6.0, [1.0, 1.0, 1.0, 0.9]);
    // Font verification: render the printable charset.
    overlay.text_shadow(16.0, 16.0, 2.0, "RCCE2 Rust Client", [1.0, 1.0, 1.0, 1.0]);
    overlay.text(16.0, 250.0, 2.0, "ABCDEFGHIJKLMNOPQRSTUVWXYZ", [1.0, 1.0, 0.6, 1.0]);
    overlay.text(16.0, 274.0, 2.0, "abcdefghijklmnopqrstuvwxyz", [0.7, 1.0, 0.7, 1.0]);
    overlay.text(16.0, 298.0, 2.0, "0123456789 .,:;!?'-/()[]+=", [0.7, 0.8, 1.0, 1.0]);

    // Floating damage numbers at different damage-types and ages (alpha + rise
    // come from the same Floater math the live client uses).
    let palette: [[f32; 3]; 6] = [
        [1.0, 0.85, 0.30], [1.0, 0.45, 0.20], [0.50, 0.80, 1.0],
        [0.70, 1.0, 0.40], [0.85, 0.50, 1.0], [1.0, 0.40, 0.40],
    ];
    let samples: [(u16, u8, f32); 4] =
        [(120, 0, 0.0), (45, 1, 0.4), (310, 2, 0.8), (7, 5, 1.1)];
    for (i, (dmg, dt, age)) in samples.iter().enumerate() {
        let fl = rcce_client::floaters::Floater { rid: 0, damage: *dmg, damage_type: *dt, t0: 0.0 };
        let c = palette[*dt as usize % 6];
        let base_x = 300.0 + i as f32 * 78.0;
        overlay.text_shadow(base_x, 190.0 - fl.rise(*age), 1.5, &dmg.to_string(), [c[0], c[1], c[2], fl.alpha(*age)]);
    }
    // Representative inventory/spellbook panel (same primitives the live
    // client uses; data here is mocked since the bare test character is empty).
    {
        let white = [1.0, 1.0, 1.0, 1.0];
        let dim = [0.6, 0.6, 0.6, 1.0];
        let (pw, ph) = (300.0, 250.0);
        let (px, py) = (320.0, 40.0);
        overlay.rect(px, py, pw, ph, [0.05, 0.06, 0.10, 0.9]);
        overlay.rect(px, py, pw, 22.0, [0.15, 0.18, 0.28, 0.96]);
        overlay.text_shadow(px + 10.0, py + 6.0, 1.5, "Character", white);
        overlay.text(px + pw - 78.0, py + 7.0, 1.0, "[I] close", dim);
        let mut y = py + 30.0;
        overlay.text_shadow(px + 10.0, y, 1.0, "Lv 7   1240 gold   3200 xp", [1.0, 0.88, 0.4, 1.0]);
        y += 18.0;
        overlay.text_shadow(px + 10.0, y, 1.0, "Equipped (2)", [0.7, 1.0, 0.8, 1.0]);
        y += 14.0;
        for line in ["Weapon: Sword", "Chest: Iron Mail"] {
            overlay.text(px + 18.0, y, 1.0, line, white);
            y += 12.0;
        }
        y += 6.0;
        overlay.text_shadow(px + 10.0, y, 1.0, "Backpack (2)   1-9 drop, Shift equip", [0.7, 0.85, 1.0, 1.0]);
        y += 14.0;
        for line in ["1. Shield", "2. Health Potion  x5"] {
            overlay.text(px + 18.0, y, 1.0, line, white);
            y += 12.0;
        }
        y += 8.0;
        overlay.text_shadow(px + 10.0, y, 1.0, "Spells (2)", [0.85, 0.7, 1.0, 1.0]);
        y += 14.0;
        for line in ["Fireball (L3) *", "Heal (L1)"] {
            overlay.text(px + 18.0, y, 1.0, line, white);
            y += 12.0;
        }
    }

    // Minimap/radar mock: player-relative blips around the centre (same
    // world_to_radar projection the live client uses).
    {
        use rcce_client::radar::world_to_radar;
        let r = 64.0f32;
        let (cx, cy) = (10.0 + r, 10.0 + r + 120.0);
        let yaw = 0.4f32;
        let range = 140.0;
        overlay.rect(cx - r - 4.0, cy - r - 4.0, (r + 4.0) * 2.0, (r + 4.0) * 2.0, [0.0, 0.0, 0.0, 0.55]);
        overlay.rect(cx - 1.0, cy - r * 0.5, 2.0, r * 0.5, [0.4, 0.8, 0.4, 0.7]);
        overlay.rect(cx - 2.0, cy - 2.0, 4.0, 4.0, [0.6, 1.0, 0.6, 1.0]);
        // (dx, dz, colour): an NPC ahead, a player to the side, a far one clipped.
        let blips = [
            (0.0f32, -40.0f32, [0.95, 0.35, 0.35, 1.0]),
            (60.0, 20.0, [0.4, 0.7, 1.0, 1.0]),
            (-50.0, -30.0, [1.0, 0.85, 0.2, 1.0]),
            (500.0, 0.0, [1.0, 1.0, 1.0, 1.0]), // out of range → not drawn
        ];
        for (dx, dz, col) in blips {
            if let Some((ox, oy)) = world_to_radar(dx, dz, yaw, range, r) {
                overlay.rect(cx + ox - 2.0, cy + oy - 2.0, 4.0, 4.0, col);
            }
        }
        for (dx, dz) in [(10.0f32, 10.0f32), (-20.0, 40.0)] {
            if let Some((ox, oy)) = world_to_radar(dx, dz, yaw, range, r) {
                overlay.rect(cx + ox - 1.5, cy + oy - 1.5, 3.0, 3.0, [1.0, 0.85, 0.3, 1.0]);
            }
        }
    }

    // Status-effect pills mock (P_ActorEffect buffs/debuffs).
    {
        let mut ex = 10.0f32;
        let ey = 152.0f32;
        for name in ["Poison", "Blessed", "Haste"] {
            let tw = rcce_render::font::text_width(name, 1.0);
            let pillw = tw + 10.0;
            overlay.rect(ex, ey, pillw, 14.0, [0.32, 0.16, 0.36, 0.82]);
            overlay.text_shadow(ex + 5.0, ey + 2.0, 1.0, name, [1.0, 0.85, 1.0, 1.0]);
            ex += pillw + 4.0;
        }
    }

    // Weather mock: rain streaks on the right half (run the sim a few frames
    // so particles spread out).
    {
        use rcce_client::weather::{Weather, WeatherSystem};
        let mut ws = WeatherSystem::new(160);
        for _ in 0..20 {
            ws.update(0.03, w as f32, h as f32, Weather::Rain);
        }
        for p in ws.particles() {
            if p.x > 430.0 {
                overlay.rect(p.x, p.y, 1.5, 9.0, [0.6, 0.7, 0.9, 0.55]);
            }
        }
    }

    // Vendor panel mock (P_OpenTrading layout): name left, price right.
    {
        let white = [1.0, 1.0, 1.0, 1.0];
        let dim = [0.6, 0.6, 0.6, 1.0];
        let (pw, ph) = (220.0, 130.0);
        let (px, py) = (90.0, 250.0);
        overlay.rect(px, py, pw, ph, [0.07, 0.06, 0.05, 0.92]);
        overlay.rect(px, py, pw, 22.0, [0.28, 0.22, 0.12, 0.96]);
        overlay.text_shadow(px + 10.0, py + 6.0, 1.5, "Vendor", white);
        overlay.text(px + pw - 80.0, py + 7.0, 1.0, "[Esc] close", dim);
        let mut y = py + 30.0;
        overlay.text(px + 10.0, y, 1.0, "Press 1-9 to buy:", dim);
        y += 14.0;
        for (name, price) in [("1. Sword", "10g"), ("2. Shield", "10g"), ("3. Health Potion x5", "2g")] {
            overlay.text(px + 12.0, y, 1.0, name, white);
            let pwid = rcce_render::font::text_width(price, 1.0);
            overlay.text(px + pw - pwid - 12.0, y, 1.0, price, [1.0, 0.88, 0.4, 1.0]);
            y += 14.0;
        }
    }

    // Action bar mock: number-keyed spell slots, one mid-cooldown.
    {
        let white = [1.0, 1.0, 1.0, 1.0];
        let (slot, gap) = (30.0f32, 4.0f32);
        let names = ["Fire", "Heal", "Bolt", "Ward"];
        let total = names.len() as f32 * (slot + gap) - gap;
        let x0 = (w as f32 - total) * 0.5;
        let y0 = h as f32 - slot - 64.0;
        for (i, name) in names.iter().enumerate() {
            let x = x0 + i as f32 * (slot + gap);
            overlay.rect(x, y0, slot, slot, [0.1, 0.1, 0.16, 0.82]);
            if i == 1 {
                overlay.rect(x, y0, slot, slot * 0.55, [0.0, 0.0, 0.0, 0.6]); // cooldown
            }
            overlay.text_shadow(x + 2.0, y0 + 1.0, 1.0, &format!("{}", i + 1), [1.0, 1.0, 0.6, 1.0]);
            overlay.text(x + 2.0, y0 + slot - 9.0, 1.0, name, white);
        }
    }

    // Textured-quad verification: a 2x2 RGBA texture (R G / B W) stretched to a
    // 64x64 quad. Confirms the textured pipeline samples + UV-maps correctly
    // (the GUI-icon path the live HUD uses). Self-contained — no data/ needed.
    let tex2x2: [u8; 16] = [
        255, 0, 0, 255, 0, 255, 0, 255, // row 0: red, green
        0, 0, 255, 255, 255, 255, 255, 255, // row 1: blue, white
    ];
    overlay.register_texture(&device, &queue, "test2x2", 2, 2, &tex2x2);
    let (qx, qy, qs) = (540.0f32, 20.0f32, 64.0f32);
    overlay.image(qx, qy, qs, qs, "test2x2", [1.0, 1.0, 1.0, 1.0]);

    // Layer-order check (interleaved draw list): a coloured rect drawn AFTER a
    // textured quad must sit ON TOP of it. Texture, then an opaque magenta rect
    // over its centre — the overlap must read magenta, not the texture.
    let (lx, ly, lsz) = (540.0f32, 100.0f32, 64.0f32);
    overlay.image(lx, ly, lsz, lsz, "test2x2", [1.0, 1.0, 1.0, 1.0]);
    overlay.rect(lx + 16.0, ly + 16.0, 32.0, 32.0, [1.0, 0.0, 1.0, 1.0]);

    overlay.render(&device, &queue, &view, w as f32, h as f32);

    // Readback → PNG.
    let bpp = 4u32;
    let unpadded = w * bpp;
    let padded = unpadded.div_ceil(256) * 256;
    let rb = device.create_buffer(&wgpu::BufferDescriptor {
        label: Some("rb"),
        size: (padded * h) as u64,
        usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
        mapped_at_creation: false,
    });
    let mut enc = device.create_command_encoder(&Default::default());
    enc.copy_texture_to_buffer(
        wgpu::ImageCopyTexture {
            texture: &target,
            mip_level: 0,
            origin: wgpu::Origin3d::ZERO,
            aspect: wgpu::TextureAspect::All,
        },
        wgpu::ImageCopyBuffer {
            buffer: &rb,
            layout: wgpu::ImageDataLayout {
                offset: 0,
                bytes_per_row: Some(padded),
                rows_per_image: Some(h),
            },
        },
        wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 },
    );
    queue.submit(Some(enc.finish()));

    let slice = rb.slice(..);
    let (tx, rx) = std::sync::mpsc::channel();
    slice.map_async(wgpu::MapMode::Read, move |r| { let _ = tx.send(r); });
    device.poll(wgpu::Maintain::Wait);
    rx.recv().unwrap().unwrap();
    let data = slice.get_mapped_range();
    let mut rgba = Vec::with_capacity((unpadded * h) as usize);
    for row in 0..h {
        let s = (row * padded) as usize;
        rgba.extend_from_slice(&data[s..s + unpadded as usize]);
    }
    drop(data);
    rb.unmap();

    let file = std::fs::File::create(&out).unwrap();
    let mut enc = png::Encoder::new(BufWriter::new(file), w, h);
    enc.set_color(png::ColorType::Rgba);
    enc.set_depth(png::BitDepth::Eight);
    enc.write_header().unwrap().write_image_data(&rgba).unwrap();
    println!("[overlay_test] wrote {out}");

    // Verify the textured quad's four quadrants sampled the right texels (a few
    // px inside each corner; ClampToEdge keeps corners near the pure texel).
    let px = |x: u32, y: u32| -> [u8; 3] {
        let i = ((y * w + x) * 4) as usize;
        [rgba[i], rgba[i + 1], rgba[i + 2]]
    };
    let (ix, iy, is) = (qx as u32, qy as u32, qs as u32);
    let tl = px(ix + 6, iy + 6);
    let tr = px(ix + is - 6, iy + 6);
    let bl = px(ix + 6, iy + is - 6);
    let br = px(ix + is - 6, iy + is - 6);
    println!("[overlay_test] textured quad corners: TL={tl:?} TR={tr:?} BL={bl:?} BR={br:?}");
    assert!(tl[0] > 150 && tl[0] > tl[1] && tl[0] > tl[2], "top-left should be red, got {tl:?}");
    assert!(tr[1] > 150 && tr[1] > tr[0] && tr[1] > tr[2], "top-right should be green, got {tr:?}");
    assert!(bl[2] > 150 && bl[2] > bl[0] && bl[2] > bl[1], "bottom-left should be blue, got {bl:?}");
    assert!(br[0] > 150 && br[1] > 150 && br[2] > 150, "bottom-right should be white, got {br:?}");
    println!("[overlay_test] textured-quad sampling OK");

    // Layer order: the magenta rect drawn after the texture must be on top.
    let centre = px(lx as u32 + 32, ly as u32 + 32);
    println!("[overlay_test] layer-order overlap pixel: {centre:?}");
    assert!(
        centre[0] > 150 && centre[1] < 100 && centre[2] > 150,
        "rect drawn after a texture must be on top (expected magenta), got {centre:?}"
    );
    println!("[overlay_test] interleaved layer-order OK");
}
