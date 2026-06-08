//! Headless RCCE2 client: logs into the live server and maintains live game
//! state from the packet stream, printing it as it evolves. This is the
//! networking+state spine the wgpu renderer will draw from.
//!
//!   cargo run -p rcce-client --target i686-pc-windows-msvc \
//!       -- "C:\Users\dyanr\Desktop\rcce2\bin\RCEnet.dll" 127.0.0.1 25000 [seconds]

use std::thread::sleep;
use std::time::{Duration, Instant};

use enet_sys::EnetTransport;
use rcce_net::Transport;

use rcce_client::login::{login, Credentials};
use rcce_client::world::World;

fn main() {
    // Args: [host] [port] [seconds]. Transport is the compiled-in ENet fork
    // (enet-sys) — no DLL path needed; this binary is 64-bit.
    let mut args = std::env::args().skip(1);
    let host = args.next().unwrap_or_else(|| "127.0.0.1".to_string());
    let port: u16 = args.next().and_then(|s| s.parse().ok()).unwrap_or(25000);
    let run_secs: u64 = args.next().and_then(|s| s.parse().ok()).unwrap_or(12);

    let mut t = EnetTransport::new();
    let creds = Credentials {
        username: "rustbot".to_string(),
        password: "rustpass".to_string(),
        email: "rust@bot.com".to_string(),
    };

    println!("[client] logging in to {host}:{port} ...");
    let outcome = match login(&mut t, &host, port, &creds) {
        Ok(o) => o,
        Err(e) => {
            eprintln!("[client] login failed: {e}");
            std::process::exit(1);
        }
    };
    println!("[client] ✓ in world, RuntimeID={}", outcome.runtime_id);

    // Load assets up front so the packet decoder knows each template's gender
    // mode (decides whether P_NewActor carries a gender byte) before any actor
    // packet is applied. Kept for the render pass too.
    let data_root = std::env::var("RCCE_DATA")
        .unwrap_or_else(|_| r"C:\Users\dyanr\Desktop\rcce2\data".to_string());
    let mut store_opt = rcce_client::assets::AssetStore::load(&data_root)
        .map_err(|e| eprintln!("[client] assets: {e}"))
        .ok();

    let mut world = World {
        my_runtime_id: outcome.runtime_id,
        template_genders: store_opt
            .as_ref()
            .map(|s| s.template_genders())
            .unwrap_or_default(),
        ..Default::default()
    };
    for m in &outcome.world_packets {
        world.apply(m);
    }

    // Live loop: apply packets, print evolving state on a cadence.
    let end = Instant::now() + Duration::from_secs(run_secs);
    let mut last_print = Instant::now() - Duration::from_secs(2);
    let mut chat_seen = 0usize;
    let mut updates = 0u64;

    while Instant::now() < end {
        for m in t.poll() {
            updates += 1;
            world.apply(&m);
        }
        if last_print.elapsed() >= Duration::from_millis(1500) {
            last_print = Instant::now();
            println!(
                "\n[client] zone='{}' (id {}) pvp={} weather={} | me=({:.1}, {:.1}, {:.1}) | {} other actor(s)",
                world.zone.name,
                world.zone.area_id,
                world.zone.pvp,
                world.zone.weather,
                world.me_x,
                world.me_y,
                world.me_z,
                world.actors.len(),
            );
            let mut listed: Vec<_> = world.actors.values().collect();
            listed.sort_by_key(|a| a.runtime_id);
            for a in listed.iter().take(8) {
                let kind = if a.is_player { "player" } else { "npc" };
                let moving = if a.is_running { " running" } else { "" };
                println!(
                    "           #{:<5} {:<14} tmpl={:<3} {:<6} pos=({:.1}, {:.1}){}",
                    a.runtime_id, a.name, a.template_id, kind, a.x, a.z, moving
                );
            }
            while chat_seen < world.chat.len() {
                println!("           chat> {}", world.chat[chat_seen].0);
                chat_seen += 1;
            }
        }
        sleep(Duration::from_millis(30));
    }

    println!(
        "\n[client] done — applied {updates} packets. Final: zone '{}', {} actors.",
        world.zone.name,
        world.actors.len()
    );

    t.disconnect(outcome.peer);

    // ---- Render the live world as a real 3D scene (actors as their models) --
    let Some(mut store) = store_opt.take() else {
        eprintln!("[client] assets unavailable; skipping scene render");
        return;
    };

    // Shared model + texture pools; placements reference them by index. Models
    // are deduped by a string key so 250 identical trees share one upload.
    let mut models: Vec<std::rc::Rc<rcce_data::B3dModel>> = Vec::new();
    let mut textures: Vec<Vec<Option<rcce_data::Image>>> = Vec::new();
    let mut dedup: std::collections::HashMap<String, usize> = std::collections::HashMap::new();
    // (model idx, world pos, rot[pitch,yaw,roll] radians, color, scale[3])
    let mut placements: Vec<(usize, [f32; 3], [f32; 3], [f32; 3], [f32; 3])> = Vec::new();

    let ground_y = 0.0f32;

    // --- Actors (the local player + every tracked actor) ---------------------
    #[allow(clippy::too_many_arguments)]
    let add_actor = |store: &mut rcce_client::assets::AssetStore,
                         models: &mut Vec<std::rc::Rc<rcce_data::B3dModel>>,
                         textures: &mut Vec<Vec<Option<rcce_data::Image>>>,
                         dedup: &mut std::collections::HashMap<String, usize>,
                         placements: &mut Vec<(usize, [f32; 3], [f32; 3], [f32; 3], [f32; 3])>,
                         tmpl: u16,
                         gender: u8,
                         face: u8,
                         body: u8,
                         hair: u8,
                         beard: u8,
                         frame: Option<f32>,
                         pos: [f32; 3],
                         yaw: f32,
                         color: [f32; 3]| {
        // Model varies by gender + animation frame; textures by face/body.
        let fkey = frame.map(|f| f.round() as i32).unwrap_or(-1);
        let key = format!("actor:{tmpl}:{gender}:{face}:{body}:{fkey}");
        let idx = match dedup.get(&key) {
            Some(&i) => i,
            None => {
                let Some(src) = store.actor_model(tmpl, gender) else { return };
                // Pose the body via skinning at `frame` (None = bind).
                let m = if frame.is_some() {
                    std::rc::Rc::new(rcce_data::B3dModel {
                        meshes: src.posed_meshes(frame),
                        textures: src.textures.clone(),
                        tex_flags: src.tex_flags.clone(),
                        brushes: src.brushes.clone(),
                        bones: src.bones.clone(),
                        anim: src.anim,
                        ..Default::default()
                    })
                } else {
                    src
                };
                let tex = store.actor_textures(tmpl, gender, face, body);
                let i = models.len();
                models.push(m);
                textures.push(tex);
                dedup.insert(key, i);
                i
            }
        };
        let scale = store.actor_render_scale(tmpl, gender).unwrap_or(0.05);
        let yaw_r = yaw.to_radians();
        // Seat the model's feet on the ground.
        let (min, _max) = models[idx].bounds();
        let body_trans = [pos[0], ground_y - min[1] * scale, pos[2]];
        placements.push((idx, body_trans, [0.0, yaw_r, 0.0], color, [scale, scale, scale]));

        // Hair + beard attached at the body's "Head" joint (fallback: model top
        // centre). Each attachment carries the body's color tint.
        let head = models[idx]
            .joint_pos("Head")
            .unwrap_or([0.0, models[idx].bounds().1[1], 0.0]);
        for att in store.actor_attachments(tmpl, gender, hair, beard) {
            let akey = format!("attach:{}", att.mesh_id);
            let aidx = match dedup.get(&akey) {
                Some(&i) => i,
                None => {
                    let i = models.len();
                    models.push(att.model.clone());
                    textures.push(att.textures.clone());
                    dedup.insert(akey, i);
                    i
                }
            };
            let (t, r, s) =
                rcce_client::assets::attachment_placement(body_trans, yaw_r, scale, head, &att);
            placements.push((aidx, t, r, color, s));
        }
    };

    // Animation frame for an actor: pick the clip by movement state and sample
    // it at a per-actor phase (so a crowd isn't in lockstep). Falls back to bind
    // (None) if the actor has no anim set / clip.
    let pose_frame = |store: &rcce_client::assets::AssetStore,
                      tmpl: u16,
                      gender: u8,
                      rid: u16,
                      moving: bool,
                      running: bool| -> Option<f32> {
        let names: &[&str] = if running {
            &["Run"]
        } else if moving {
            &["Walk"]
        } else {
            &["Idle", "Sit idle"]
        };
        let clip = store.actor_clip(tmpl, gender, names)?;
        // Static snapshot: spread actors across the clip by runtime id so a
        // crowd isn't frozen on the same frame. (The real-time window will
        // advance by dt*fps via assets::clip_frame instead.)
        let len = (clip.end - clip.start).max(0) as f32;
        let off = if len > 0.0 {
            ((rid as f32) * 2.0).rem_euclid(len + 1.0)
        } else {
            0.0
        };
        Some(clip.start as f32 + off)
    };

    let me_frame = pose_frame(&store, 0, world.me_gender, world.my_runtime_id, false, false);
    add_actor(&mut store, &mut models, &mut textures, &mut dedup, &mut placements, 0, world.me_gender, world.me_face_tex, world.me_body_tex, 0, 0, me_frame, [world.me_x, world.me_y, world.me_z], world.me_yaw, [0.85, 0.95, 0.85]);
    let actor_player_idx = placements.first().map(|p| p.0);
    for a in world.actors.values() {
        let color = if a.is_player { [0.85, 0.9, 1.0] } else { [1.0, 1.0, 1.0] };
        let dx = a.dest_x - a.x;
        let dz = a.dest_z - a.z;
        let moving = (dx * dx + dz * dz) > 1.0;
        let frame = pose_frame(&store, a.template_id, a.gender, a.runtime_id, moving, a.is_running);
        add_actor(&mut store, &mut models, &mut textures, &mut dedup, &mut placements, a.template_id, a.gender, a.face_tex, a.body_tex, a.hair, a.beard, frame, [a.x, a.y, a.z], a.yaw, color);
    }
    let actor_count = placements.len();

    // --- Scenery (props/terrain meshes that fill the zone) -------------------
    let scenery_count = load_zone_scenery(
        &data_root, &world.zone.name, &mut store, &mut models, &mut textures, &mut dedup, &mut placements,
    );
    println!("[client] scene: {actor_count} actor(s), {scenery_count} scenery object(s)");

    if placements.is_empty() {
        eprintln!("[client] nothing resolved to render; skipping scene render");
        return;
    }

    let instances: Vec<rcce_render::SceneInstance> = placements
        .iter()
        .map(|&(idx, pos, rot, color, scale)| rcce_render::SceneInstance {
            model: &models[idx],
            textures: &textures[idx],
            lightmaps: &[],
            translation: pos,
            rot,
            scale,
            color,
        })
        .collect();

    // Third-person camera framing the local player (placement 0).
    let p_pos = placements[0].1;
    let p_idx = actor_player_idx.unwrap_or(placements[0].0);
    let p_scale = placements[0].4[1];
    let (pmin, pmax) = models[p_idx].bounds();
    let player_h = ((pmax[1] - pmin[1]) * p_scale).max(0.5);
    let d = player_h * 3.2;
    let eye = [p_pos[0] + d * 0.65, ground_y + player_h * 2.3, p_pos[2] + d * 0.9];
    let target = [p_pos[0], ground_y + player_h * 0.55, p_pos[2]];

    let out = "rcce_world3d.png";
    match rcce_render::render_scene_png(&instances, eye, target, ground_y, [0.45, 0.62, 0.82], 1.0e6, 2.0e6, [0.4, 0.4, 0.4], [0.4, 0.85, 0.35], 1200, 900, out, None, None, None, 0.0) {
        Ok(adapter) => println!(
            "[client] rendered 3D world ({} instances) via {adapter} -> {out}",
            instances.len()
        ),
        Err(e) => eprintln!("[client] scene render failed: {e}"),
    }
}

/// Load `Data/Areas/<zone>.dat`, resolve each scenery placement to a model +
/// textures (deduped by mesh id), and append world-space placements. Returns
/// the number of objects added. Missing/unparsable area files are non-fatal.
#[allow(clippy::too_many_arguments)]
fn load_zone_scenery(
    data_root: &str,
    zone_name: &str,
    store: &mut rcce_client::assets::AssetStore,
    models: &mut Vec<std::rc::Rc<rcce_data::B3dModel>>,
    textures: &mut Vec<Vec<Option<rcce_data::Image>>>,
    dedup: &mut std::collections::HashMap<String, usize>,
    placements: &mut Vec<(usize, [f32; 3], [f32; 3], [f32; 3], [f32; 3])>,
) -> usize {
    if zone_name.is_empty() {
        return 0;
    }
    let path = std::path::Path::new(data_root)
        .join("Areas")
        .join(format!("{zone_name}.dat"));
    let Ok(bytes) = std::fs::read(&path) else {
        eprintln!("[client] no area file at {}", path.display());
        return 0;
    };
    let scenery = match rcce_data::AreaScenery::parse(&bytes) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("[client] area parse failed: {e}");
            return 0;
        }
    };
    let mut added = 0;
    for s in &scenery.sceneries {
        let key = format!("scenery:{}:{}", s.mesh_id, s.texture_id);
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
        // Negate yaw for the left-handed render frame (see client_window's
        // `scenery_rot_radians`); pitch/roll are correct as-is.
        let rot = [s.rot[0].to_radians(), -s.rot[1].to_radians(), s.rot[2].to_radians()];
        placements.push((idx, s.pos, rot, [1.0, 1.0, 1.0], s.scale));
        added += 1;
    }
    added
}
