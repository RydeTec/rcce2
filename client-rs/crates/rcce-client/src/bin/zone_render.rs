//! Offline zone renderer: load a project zone's area file + assets and render
//! its scenery (props/terrain meshes) to a PNG — no running server required.
//! This is the verifiable artifact for the scenery pipeline: it exercises the
//! exact same `AreaScenery::parse` → `AssetStore` → `render_scene_png` path the
//! live client uses, but standalone.
//!
//!   cargo run -p rcce-client --bin zone_render --release -- "Plains" [out.png]
//!
//! `RCCE_DATA` overrides the project `data/` directory.

use std::collections::HashMap;
use std::rc::Rc;

use rcce_data::{AreaScenery, B3dModel, Image};

fn main() {
    let mut args = std::env::args().skip(1);
    let zone = args.next().unwrap_or_else(|| "Plains".to_string());
    let out = args.next().unwrap_or_else(|| format!("zone_{}.png", sanitize(&zone)));

    let data_root = std::env::var("RCCE_DATA")
        .unwrap_or_else(|_| r"C:\Users\dyanr\Desktop\rcce2\data".to_string());

    let mut store = match rcce_client::assets::AssetStore::load(&data_root) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("[zone_render] assets: {e}");
            std::process::exit(1);
        }
    };

    let area_path = std::path::Path::new(&data_root)
        .join("Areas")
        .join(format!("{zone}.dat"));
    let bytes = match std::fs::read(&area_path) {
        Ok(b) => b,
        Err(e) => {
            eprintln!("[zone_render] {}: {e}", area_path.display());
            std::process::exit(1);
        }
    };
    let scenery = match AreaScenery::parse(&bytes) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("[zone_render] parse: {e}");
            std::process::exit(1);
        }
    };
    println!("[zone_render] '{zone}': {} scenery objects", scenery.sceneries.len());
    let env = scenery.env.clone();
    println!(
        "[zone_render] env: fog {:?} near {:.0} far {:.0} ambient {:?} outdoors {}",
        env.fog_color, env.fog_near, env.fog_far, env.ambient, env.outdoors
    );

    // Resolve + dedupe models/textures by (mesh, retexture).
    let mut models: Vec<Rc<B3dModel>> = Vec::new();
    let mut textures: Vec<Vec<Option<Image>>> = Vec::new();
    let mut dedup: HashMap<String, usize> = HashMap::new();
    // (idx, pos, rot radians, scale)
    let mut placements: Vec<(usize, [f32; 3], [f32; 3], [f32; 3])> = Vec::new();

    let mut resolved = 0usize;
    for s in &scenery.sceneries {
        let key = format!("{}:{}", s.mesh_id, s.texture_id);
        let idx = match dedup.get(&key) {
            Some(&i) => i,
            None => {
                let Some(m) = store.mesh_model(s.mesh_id) else { continue };
                let tex = store.scenery_textures(s.mesh_id, s.texture_id);
                let i = models.len();
                models.push(m);
                textures.push(tex);
                dedup.insert(key, i);
                i
            }
        };
        let rot = [s.rot[0].to_radians(), s.rot[1].to_radians(), s.rot[2].to_radians()];
        placements.push((idx, s.pos, rot, s.scale));
        resolved += 1;
    }
    println!(
        "[zone_render] resolved {resolved}/{} placements across {} unique meshes",
        scenery.sceneries.len(),
        models.len()
    );
    if placements.is_empty() {
        eprintln!("[zone_render] nothing to render");
        std::process::exit(1);
    }

    // Frame the whole zone: centroid + horizontal extent of the placements.
    let mut min = [f32::MAX; 3];
    let mut max = [f32::MIN; 3];
    for &(_, p, _, _) in &placements {
        for k in 0..3 {
            min[k] = min[k].min(p[k]);
            max[k] = max[k].max(p[k]);
        }
    }
    let center = [(min[0] + max[0]) * 0.5, (min[1] + max[1]) * 0.5, (min[2] + max[2]) * 0.5];
    let span = ((max[0] - min[0]).powi(2) + (max[2] - min[2]).powi(2)).sqrt().max(50.0);
    let ground_y = min[1];

    // Elevated 3/4 view looking down at the zone centre.
    let eye = [
        center[0] + span * 0.55,
        ground_y + span * 0.65,
        center[2] + span * 0.85,
    ];
    let target = [center[0], ground_y + span * 0.05, center[2]];

    let instances: Vec<rcce_render::SceneInstance> = placements
        .iter()
        .map(|&(idx, pos, rot, scale)| rcce_render::SceneInstance {
            model: &models[idx],
            textures: &textures[idx],
            lightmaps: &[],
            translation: pos,
            rot,
            scale,
            color: [1.0, 1.0, 1.0],
        })
        .collect();

    // Optional day/night phase (RCCE_PHASE 0=midnight, 0.5=noon) modulates the
    // sky/fog + ambient, same as the live client.
    let phase = std::env::var("RCCE_PHASE").ok().and_then(|s| s.parse::<f32>().ok()).unwrap_or(0.5);
    let sky = rcce_client::daynight::daynight(phase);
    let fog = rcce_client::daynight::modulate(env.fog_color, &sky);
    let ambient = rcce_client::daynight::modulate(env.ambient, &sky);
    println!("[zone_render] day/night phase {phase} → fog {fog:?}");

    // Resolve the area's sky texture for the textured skydome.
    let sky_tex = if env.sky_tex_id != 65535 {
        let t = store
            .texture_path(env.sky_tex_id)
            .and_then(|p| rcce_data::texture::load(&p))
            .map(|img| (img.width, img.height, img.rgba));
        println!("[zone_render] sky_tex_id {} -> {}", env.sky_tex_id, if t.is_some() { "loaded" } else { "unresolved" });
        t
    } else {
        None
    };
    let cloud_tex = if env.cloud_tex_id != 65535 {
        let t = store
            .texture_path(env.cloud_tex_id)
            .and_then(|p| rcce_data::texture::load(&p))
            .map(|img| (img.width, img.height, img.rgba));
        println!("[zone_render] cloud_tex_id {} -> {}", env.cloud_tex_id, if t.is_some() { "loaded" } else { "unresolved" });
        t
    } else {
        None
    };
    let stars_tex = if env.stars_tex_id != 65535 {
        let t = store
            .texture_path(env.stars_tex_id)
            .and_then(|p| rcce_data::texture::load(&p))
            .map(|img| (img.width, img.height, img.rgba));
        println!("[zone_render] stars_tex_id {} -> {}", env.stars_tex_id, if t.is_some() { "loaded" } else { "unresolved" });
        t
    } else {
        None
    };
    let night = rcce_client::daynight::night_factor(phase);
    println!("[zone_render] night factor {night:.2}");

    match rcce_render::render_scene_png(&instances, eye, target, ground_y, fog, env.fog_near, env.fog_far, ambient, env.light_dir, 1600, 1000, &out, sky_tex, cloud_tex, stars_tex, night) {
        Ok(adapter) => println!(
            "[zone_render] rendered {} instances via {adapter} -> {out}",
            instances.len()
        ),
        Err(e) => {
            eprintln!("[zone_render] render failed: {e}");
            std::process::exit(1);
        }
    }
}

fn sanitize(s: &str) -> String {
    s.chars()
        .map(|c| if c.is_alphanumeric() { c } else { '_' })
        .collect()
}
