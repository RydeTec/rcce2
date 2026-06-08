//! Generate a synthetic area file (`Data/Areas/<zone>.dat`) carrying a Blitz LOD
//! terrain (`CreateTerrain` block) plus a few reference props — so the terrain
//! pipeline can be render-verified without a real fork's zone. Emits the exact
//! `SaveArea` byte layout (ClientAreas.bb:820-957). The 41-byte env header is
//! copied from `Plains.dat` so sky/fog/light resolve to real textures.
//!
//! Usage: `gen-test-zone <data_root> [zone_name]`
//! Then view it as the menu backdrop (no server):
//!   `client-window 127.0.0.1 25000 "<zone_name>"` with `RCCE_DATA=<data_root>`.

use rcce_data::catalog::TextureCatalog;
use std::fs;
use std::path::PathBuf;

fn main() {
    let mut args = std::env::args().skip(1);
    let data_root = PathBuf::from(args.next().expect("usage: gen-test-zone <data_root> [zone]"));
    let zone = args.next().unwrap_or_else(|| "Test Terrain".to_string());

    let mut d: Vec<u8> = Vec::new();
    fn pu16(d: &mut Vec<u8>, v: u16) {
        d.extend_from_slice(&v.to_le_bytes());
    }
    fn pi32(d: &mut Vec<u8>, v: i32) {
        d.extend_from_slice(&v.to_le_bytes());
    }
    fn pf32(d: &mut Vec<u8>, v: f32) {
        d.extend_from_slice(&v.to_le_bytes());
    }
    fn pstr(d: &mut Vec<u8>, s: &str) {
        // Blitz WriteString: 4-byte length prefix + bytes.
        pi32(d, s.len() as i32);
        d.extend_from_slice(s.as_bytes());
    }

    // 1. Header: copy a real zone's 41-byte env prefix (valid sky/fog/light).
    let header = fs::read(data_root.join("Areas/Plains.dat")).expect("Plains.dat (for header)");
    d.extend_from_slice(&header[0..41]);

    // 2. Base terrain texture: prefer a detailed ground-ish texture that exists on
    //    disk (visible features make UV tiling obvious).
    let texcat = TextureCatalog::parse(&fs::read(data_root.join("Game Data/Textures.dat")).expect("Textures.dat"))
        .expect("texcat")
        .value;
    let exists = |fname: &str| data_root.join("Textures").join(fname.replace('\\', "/")).exists();
    // Prefer a real ground texture; never a UI/particle/effect texture.
    const GROUND_KW: [&str; 10] =
        ["grass", "dirt", "ground", "terr", "granite", "stone", "tile", "sand", "cobble", "mud"];
    const EXCLUDE_KW: [&str; 12] = [
        "particle", "flare", "gui", "menu", "spell", "sun", "moon", "shadow", "screen", "compass", "radar", "marker",
    ];
    let base_tex = texcat
        .entries
        .iter()
        .filter(|e| exists(&e.filename))
        .filter(|e| {
            let l = e.filename.to_lowercase();
            !EXCLUDE_KW.iter().any(|k| l.contains(k))
        })
        .min_by_key(|e| {
            let l = e.filename.to_lowercase();
            if GROUND_KW.iter().any(|k| l.contains(k)) {
                0
            } else {
                1
            }
        })
        .map(|e| {
            eprintln!("base terrain tex id {} = {}", e.id, e.filename);
            e.id
        })
        .unwrap_or(0);

    // No StarsTexID is injected: the project ships no real (black + white dots)
    // stars texture, and the renderer now draws a PROCEDURAL starfield at night
    // when a zone has none — so the test zone's night sky is starry on its own.

    // Terrain geometry: a 32-cell grid, 4 units/cell (128×128 world), centred on
    // the origin, with a smooth central hill ~12 units tall.
    let grid: i32 = 32;
    let cell = 4.0_f32;
    let stride = (grid + 1) as usize;
    let origin = [-(grid as f32) * cell * 0.5, 0.0, -(grid as f32) * cell * 0.5];
    let mut heights = vec![0.0f32; stride * stride];
    for x in 0..stride {
        for z in 0..stride {
            let fx = x as f32 / grid as f32 - 0.5;
            let fz = z as f32 / grid as f32 - 0.5;
            heights[x * stride + z] = 25.0 * (-(fx * fx + fz * fz) * 8.0).exp();
        }
    }
    let surface_y = |wx: f32, wz: f32| -> f32 {
        let gx = (((wx - origin[0]) / cell).round() as i32).clamp(0, grid) as usize;
        let gz = (((wz - origin[2]) / cell).round() as i32).clamp(0, grid) as usize;
        heights[gx * stride + gz]
    };

    // 3. Scenery: fir trees (mesh 42) as scale references, seated on the surface.
    let trees = [[-50.0f32, -50.0], [50.0, -50.0], [-50.0, 50.0], [50.0, 50.0], [0.0, 0.0]];
    pu16(&mut d, trees.len() as u16);
    for [tx, tz] in trees {
        pu16(&mut d, 42); // fir mesh
        pf32(&mut d, tx);
        pf32(&mut d, surface_y(tx, tz));
        pf32(&mut d, tz);
        for _ in 0..3 {
            pf32(&mut d, 0.0); // pitch/yaw/roll
        }
        for _ in 0..3 {
            pf32(&mut d, 0.04); // scale
        }
        d.push(0); // anim mode
        d.push(0); // scenery id
        pu16(&mut d, 65535); // texture id (default)
        d.push(0); // catch rain
        d.push(0); // entity type
        pstr(&mut d, ""); // lightmap
        pstr(&mut d, ""); // rcte
        d.push(0); // cast shadow
        d.push(0); // receive shadow
        d.push(0); // render range
    }

    // 4. One water plane (a lake around the hill base) + empty colboxes/emitters.
    let water_tex = texcat
        .entries
        .iter()
        .filter(|e| exists(&e.filename))
        .find(|e| e.filename.to_lowercase().contains("water"))
        .map(|e| e.id)
        .unwrap_or(base_tex);
    pu16(&mut d, 1); // 1 water plane
    pu16(&mut d, water_tex);
    pf32(&mut d, 10.0); // tex scale
    pf32(&mut d, 0.0); // x
    pf32(&mut d, 5.0); // y (low — laps the hill base)
    pf32(&mut d, 0.0); // z
    pf32(&mut d, 130.0); // scale x
    pf32(&mut d, 130.0); // scale z
    d.push(40); // R (editor-only)
    d.push(90); // G
    d.push(160); // B
    d.push(60); // opacity 0..100
    pu16(&mut d, 0); // colboxes
    pu16(&mut d, 0); // emitters

    // 5. One LOD terrain.
    pu16(&mut d, 1);
    pu16(&mut d, base_tex);
    pu16(&mut d, base_tex); // detail tex: reuse base at a finer scale (visible blend)
    pi32(&mut d, grid);
    for h in &heights {
        pf32(&mut d, *h); // (N+1)² heights, x outer / z inner (SaveArea order)
    }
    for v in origin {
        pf32(&mut d, v); // pos
    }
    for _ in 0..3 {
        pf32(&mut d, 0.0); // pitch/yaw/roll
    }
    pf32(&mut d, cell); // scale x
    pf32(&mut d, 1.0); // scale y
    pf32(&mut d, cell); // scale z
    pf32(&mut d, 96.0); // detail tex scale (fine — tiles ~96× across the terrain)
    pi32(&mut d, 1); // detail
    d.push(1); // morph
    d.push(0); // shading

    // 6. Empty sound zones (closes the file the way SaveArea does).
    pu16(&mut d, 0);

    let out = data_root.join(format!("Areas/{zone}.dat"));
    fs::write(&out, &d).expect("write area");
    eprintln!("wrote {} ({} bytes): {} trees, 1 terrain {grid}x{grid}", out.display(), d.len(), trees.len());
}
