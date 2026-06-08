//! Offline actor renderer: load one actor template and render its assembled
//! body + hair + beard to a PNG — no server. Verifies the appearance pipeline
//! (gender mesh, face/body skin, head-attached hair/beard) standalone.
//!
//!   cargo run -p rcce-client --bin actor_render --release -- <templateId> [gender] [out.png]
//!
//! gender: 0 male (default) / 1 female. `RCCE_DATA` overrides the data root.

use rcce_client::assets::{attachment_placement, AssetStore};
use rcce_data::{B3dModel, Image};
use std::rc::Rc;

fn main() {
    // Args: <templateId> [gender] [frame|"bind"] [out.png]
    // Args: <templateId> [gender] [bind | <frameNumber> | <clipName>] [out.png]
    // Default (no frame arg) poses the actor mid-Idle.
    let mut args = std::env::args().skip(1);
    let tmpl: u16 = args.next().and_then(|s| s.parse().ok()).unwrap_or(0);
    let gender: u8 = args.next().and_then(|s| s.parse().ok()).unwrap_or(0);
    let frame_arg = args.next();
    let out = args.next().unwrap_or_else(|| {
        let tag = frame_arg.as_deref().unwrap_or("idle").replace(' ', "_");
        format!("actor_{tmpl}_g{gender}_{tag}.png")
    });

    let data_root = std::env::var("RCCE_DATA")
        .unwrap_or_else(|_| r"C:\Users\dyanr\Desktop\rcce2\data".to_string());
    let mut store = match AssetStore::load(&data_root) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("[actor_render] assets: {e}");
            std::process::exit(1);
        }
    };

    let Some(src) = store.actor_model(tmpl, gender) else {
        eprintln!("[actor_render] no body model for template {tmpl} gender {gender}");
        std::process::exit(1);
    };
    let fps = src.anim.map(|a| a.fps).unwrap_or(15.0);

    // Resolve the frame: "bind" → bind pose; a number → that frame; a clip name
    // (or nothing → "Idle") → the clip's mid-frame via the actor's anim set.
    let frame: Option<f32> = match frame_arg.as_deref() {
        Some("bind") => None,
        Some(s) if s.parse::<f32>().is_ok() => Some(s.parse().unwrap()),
        other => {
            let name = other.unwrap_or("Idle");
            match store.actor_clip(tmpl, gender, &[name]) {
                Some(c) => {
                    // Mid-clip: half the clip's duration in.
                    let mid = (c.end - c.start).max(0) as f32 * 0.5 / fps.max(0.001);
                    let f = rcce_client::assets::clip_frame(c, fps, mid);
                    println!("[actor_render] clip '{}' [{}..{}] -> frame {f:.1}", c.name, c.start, c.end);
                    Some(f)
                }
                None => {
                    eprintln!("[actor_render] no '{name}' clip for this actor; using bind");
                    None
                }
            }
        }
    };
    // Pose the body (linear-blend skinning) at `frame`; None = bind pose.
    if let Some(a) = &src.anim {
        println!(
            "[actor_render] anim: {} frames @ {}fps, {} bones; rendering frame {:?}",
            a.frames, a.fps, src.bones.len(), frame
        );
    }
    let body = Rc::new(B3dModel {
        meshes: src.posed_meshes(frame),
        textures: src.textures.clone(),
        tex_flags: src.tex_flags.clone(),
        brushes: src.brushes.clone(),
        bones: src.bones.clone(),
        anim: src.anim,
        ..Default::default()
    });
    let scale = store.actor_render_scale(tmpl, gender).unwrap_or(0.05);
    let body_tex = store.actor_textures(tmpl, gender, 0, 0);
    let ground_y = 0.0f32;
    let (bmin, bmax) = body.bounds();
    let body_trans = [0.0, ground_y - bmin[1] * scale, 0.0];
    let head = body
        .joint_pos("Head")
        .unwrap_or([0.0, bmax[1], 0.0]);
    println!(
        "[actor_render] template {tmpl} gender {gender}: scale {scale:.4}, head joint {head:?}"
    );

    // Pools, mirroring the live client's render assembly.
    let mut models: Vec<Rc<B3dModel>> = vec![body.clone()];
    let mut textures: Vec<Vec<Option<Image>>> = vec![body_tex];
    // (model idx, translation, rot, scale)
    let mut place: Vec<(usize, [f32; 3], [f32; 3], [f32; 3])> = vec![(
        0,
        body_trans,
        [0.0, 0.0, 0.0],
        [scale, scale, scale],
    )];

    for att in store.actor_attachments(tmpl, gender, 0, 0) {
        let (t, r, s) = attachment_placement(body_trans, 0.0, scale, head, &att);
        let idx = models.len();
        println!(
            "[actor_render]   attachment mesh {} ({} sub-meshes) at {t:?} scale {s:?}",
            att.mesh_id,
            att.model.meshes.len()
        );
        models.push(att.model);
        textures.push(att.textures);
        place.push((idx, t, r, s));
    }

    println!("[actor_render] R_Hand joint: {:?}", body.joint_pos("R_Hand"));
    // Optional equipped weapon: RCCE_WEAPON_ITEM=<item id> hangs the item's
    // mmesh at the R_Hand joint (the engine's weapon-attach point). RCCE_WEAPON_
    // MESH=<mesh id> attaches a mesh directly (to verify the mechanism when no
    // shipped item carries a world mesh).
    if let Some(mesh_id) = std::env::var("RCCE_WEAPON_MESH").ok().and_then(|s| s.parse::<u16>().ok()) {
        if let Some(att) = store.gear_attachment_mesh(mesh_id) {
            let hand = body.joint_pos("R_Hand").unwrap_or(head);
            let (t, r, s) = attachment_placement(body_trans, 0.0, scale, hand, &att);
            let idx = models.len();
            println!("[actor_render]   direct weapon mesh {mesh_id} at R_Hand {hand:?}");
            models.push(att.model);
            textures.push(att.textures);
            place.push((idx, t, r, s));
        }
    }
    if let Some(item_id) = std::env::var("RCCE_WEAPON_ITEM").ok().and_then(|s| s.parse::<u16>().ok()) {
        match store.gear_attachment(item_id) {
            Some(att) => {
                let hand = body.joint_pos("R_Hand").unwrap_or(head);
                let (t, r, s) = attachment_placement(body_trans, 0.0, scale, hand, &att);
                let idx = models.len();
                println!(
                    "[actor_render]   weapon item {item_id} → mesh {} ({} sub-meshes) at R_Hand {hand:?}",
                    att.mesh_id,
                    att.model.meshes.len()
                );
                models.push(att.model);
                textures.push(att.textures);
                place.push((idx, t, r, s));
            }
            None => println!("[actor_render]   weapon item {item_id}: no mmesh/mesh"),
        }
    }

    let instances: Vec<rcce_render::SceneInstance> = place
        .iter()
        .map(|&(idx, t, r, s)| rcce_render::SceneInstance {
            model: &models[idx],
            textures: &textures[idx],
            lightmaps: &[],
            translation: t,
            rot: r,
            scale: s,
            color: [1.0, 1.0, 1.0],
        })
        .collect();

    // Frame the full body height from slightly in front and above.
    let h = ((bmax[1] - bmin[1]) * scale).max(0.5);
    let eye = [h * 1.4, ground_y + h * 0.75, h * 2.4];
    let target = [0.0, ground_y + h * 0.55, 0.0];

    // GPU-skinning path (RCCE_GPUSKIN): render the BODY via the hardware LBS
    // pipeline (static mesh + per-frame bone palette in the shader) and compare
    // the pose to the CPU render. Attachments are omitted here (body verifies the
    // skinning).
    if std::env::var("RCCE_GPUSKIN").is_ok() {
        match rcce_render::render_skinned_png(
            &src, &textures[0], frame, body_trans, [0.0, 0.0, 0.0], [scale, scale, scale],
            [1.0, 1.0, 1.0], eye, target, [0.45, 0.62, 0.82], 1.0e6, 2.0e6,
            [0.4, 0.4, 0.4], [0.4, 0.85, 0.35], 900, 1200, &out,
        ) {
            Ok(a) => {
                println!("[actor_render] GPU-SKINNED body via {a} -> {out}");
                return;
            }
            Err(e) => eprintln!("[actor_render] GPU skin failed ({e}); falling back to CPU"),
        }
    }

    // No fog for a close-up actor render (far plane way beyond the model).
    match rcce_render::render_scene_png(&instances, eye, target, ground_y, [0.45, 0.62, 0.82], 1.0e6, 2.0e6, [0.4, 0.4, 0.4], [0.4, 0.85, 0.35], 900, 1200, &out, None, None, None, 0.0) {
        Ok(adapter) => println!(
            "[actor_render] rendered {} instances via {adapter} -> {out}",
            instances.len()
        ),
        Err(e) => {
            eprintln!("[actor_render] render failed: {e}");
            std::process::exit(1);
        }
    }
}
