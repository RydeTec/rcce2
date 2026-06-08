//! `rcce-data` — parsers for the on-disk project files an RCCE2 client must
//! read. The GUE editor keeps writing these formats; this crate reads them
//! unchanged so the Rust client is a true drop-in. No format is ever modified.
//!
//! See `docs/rust-client/PLAN.md` for the full porting plan. Phase 1 covers the
//! indexed media catalogs; B3D meshes, area `.dat`, and `Accounts.dat` saves
//! land next in this crate.

pub mod actors;
pub mod anim;
pub mod area;
pub mod attributes;
pub mod b3d;
pub mod emitter;
pub mod catalog;
pub mod fixed_attributes;
pub mod interface;
pub mod items;
pub mod money;
pub mod reader;
pub mod texture;

pub use actors::{ActorCatalog, ActorTemplate};
pub use anim::{AnimClip, AnimSet, AnimSetCatalog};
pub use area::{AreaEnv, AreaScenery, EmitterPlacement, SceneryPlacement, TerrainPatch, WaterPlane};
pub use emitter::EmitterConfig;
pub use b3d::{B3dAnim, B3dBone, B3dKey, B3dMesh, B3dModel};
pub use texture::Image;
pub use catalog::{
    MeshCatalog, MeshEntry, MusicCatalog, MusicEntry, ParsedCatalog, SoundCatalog, SoundEntry,
    TextureCatalog, TextureEntry, CATALOG_SLOTS,
};
pub use attributes::{AttributeDef, AttributeNames};
pub use fixed_attributes::FixedAttributes;
pub use items::{equip_slot, equip_slot_name, ItemCatalog, ItemDef};
pub use interface::{IComp, InterfaceLayout};
pub use money::MoneyConfig;
pub use reader::{BlitzReader, ReadError};

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    /// Repo root, three levels up from this crate's manifest
    /// (`client-rs/crates/rcce-data` → worktree root). The real `data/` tree
    /// lives there.
    fn repo_root() -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .expect("repo root above client-rs/crates/rcce-data")
            .to_path_buf()
    }

    #[test]
    fn blitz_reader_byte_orders() {
        // 0x01 byte, short 0x0002 LE, int 0x00000003 LE, float 1.0 LE.
        let mut buf = vec![0x01u8];
        buf.extend_from_slice(&2i16.to_le_bytes());
        buf.extend_from_slice(&3i32.to_le_bytes());
        buf.extend_from_slice(&1.0f32.to_le_bytes());
        // length-prefixed string "hi": 4-byte LE len + bytes.
        buf.extend_from_slice(&2i32.to_le_bytes());
        buf.extend_from_slice(b"hi");

        let mut r = BlitzReader::new(&buf);
        assert_eq!(r.read_byte().unwrap(), 1);
        assert_eq!(r.read_short().unwrap(), 2);
        assert_eq!(r.read_int().unwrap(), 3);
        assert_eq!(r.read_float().unwrap(), 1.0);
        assert_eq!(r.read_string(260).unwrap(), "hi");
        assert!(r.eof());
    }

    #[test]
    fn reader_rejects_overlong_string() {
        let mut buf = Vec::new();
        buf.extend_from_slice(&999i32.to_le_bytes()); // claims 999 bytes
        let mut r = BlitzReader::new(&buf);
        assert!(matches!(
            r.read_string(260),
            Err(ReadError::StringTooLong { len: 999, max: 260 })
        ));
    }

    #[test]
    fn reader_negative_or_zero_string_is_empty() {
        for len in [0i32, -1i32] {
            let buf = len.to_le_bytes();
            let mut r = BlitzReader::new(&buf);
            assert_eq!(r.read_string(260).unwrap(), "");
        }
    }

    /// Ground-truth test: parse the real `Interface.dat` and dump the key HUD
    /// positions so the Rust client can match them.
    #[test]
    fn parse_real_interface_dat() {
        let path = repo_root().join("data/Game Data/Interface.dat");
        let Ok(bytes) = std::fs::read(&path) else {
            eprintln!("skipping: {} not present", path.display());
            return;
        };
        let l = interface::InterfaceLayout::parse(&bytes).expect("Interface.dat parse");
        eprintln!("Interface.dat ({} bytes):", bytes.len());
        eprintln!("  chat   {:?}", l.chat);
        eprintln!("  radar  {:?}", l.radar);
        eprintln!("  compass {:?}", l.compass);
        eprintln!("  buffs  {:?}", l.buffs);
        eprintln!("  inv_window {:?}", l.inventory_window);
        for i in [0usize, 1, 2] {
            eprintln!("  attr[{i}] {:?}", l.attributes[i]);
        }
        eprintln!("  inv_drop {:?}", l.inventory_drop);
        eprintln!("  inv_eat  {:?}", l.inventory_eat);
        eprintln!("  inv_gold {:?}", l.inventory_gold);
        for (i, b) in l.inventory_buttons.iter().enumerate() {
            eprintln!("  inv_btn[{i:>2}] x={:.4} y={:.4} w={:.4} h={:.4}", b.x, b.y, b.w, b.h);
        }
        // Sanity: fractional coords in [0,1].
        for a in &l.attributes {
            assert!((0.0..=1.0).contains(&a.x) && (0.0..=1.0).contains(&a.y), "attr off-screen: {a:?}");
        }
    }

    /// Ground-truth test: parse the real `Items.dat` shipped in `data/`. Walks
    /// the variable-length records to EOF; every item must have a non-empty name
    /// and an id, which only holds if record boundaries stayed aligned.
    #[test]
    fn parse_real_items_dat() {
        let path = repo_root().join("data/Server Data/Items.dat");
        let Ok(bytes) = std::fs::read(&path) else {
            eprintln!("skipping: {} not present", path.display());
            return;
        };
        let cat = items::ItemCatalog::parse(&bytes);
        eprintln!("Items.dat: {} items", cat.items.len());
        assert!(!cat.items.is_empty(), "Items.dat parsed zero items");
        // Resolve each item's inventory thumbnail through the texture catalog so
        // we know whether the Rust client can draw real per-item slot icons.
        let texcat = std::fs::read(repo_root().join("data/Game Data/Textures.dat"))
            .ok()
            .and_then(|b| catalog::TextureCatalog::parse(&b).ok())
            .map(|p| p.value);
        for it in &cat.items {
            assert!(!it.name.is_empty(), "item #{} has empty name", it.id);
            let thumb = it.thumbnail_tex_id;
            let resolved = texcat
                .as_ref()
                .filter(|_| thumb >= 0)
                .and_then(|tc| tc.get(thumb as u16))
                .map(|e| e.filename.clone());
            eprintln!(
                "  #{:<4} {:<20} type {} value {} mass {} dmg {} armour {} thumb={} -> {:?}",
                it.id, it.name, it.item_type, it.value, it.mass, it.weapon_damage, it.armour_level, thumb, resolved
            );
        }
    }

    /// Ground-truth test: parse the real `Attributes.dat` and dump the named
    /// (non-empty) attribute slots so the character panel can label them.
    #[test]
    fn parse_real_attributes_dat() {
        let path = repo_root().join("data/Server Data/Attributes.dat");
        let Ok(bytes) = std::fs::read(&path) else {
            eprintln!("skipping: {} not present", path.display());
            return;
        };
        let a = attributes::AttributeNames::parse(&bytes).expect("Attributes.dat parse");
        assert_eq!(a.attrs.len(), 40);
        eprintln!("Attributes.dat ({} bytes), assignment {}:", bytes.len(), a.assignment);
        for (i, d) in a.attrs.iter().enumerate() {
            if !d.name.is_empty() {
                eprintln!("  [{i:>2}] {:<16} skill={} hidden={}", d.name, d.is_skill, d.hidden);
            }
        }
        // Index 0 is Health by engine convention.
        assert!(a.name(0).is_some(), "slot 0 (Health) should be named");
    }

    /// Ground-truth test: parse the real `Meshes.dat` shipped in `data/`.
    /// Skips (does not fail) when the file is absent so the suite still runs
    /// in checkouts without the data tree.
    #[test]
    fn parse_real_meshes_dat() {
        let path = repo_root().join("data/Game Data/Meshes.dat");
        let Ok(bytes) = std::fs::read(&path) else {
            eprintln!("skipping: {} not present", path.display());
            return;
        };

        let parsed = MeshCatalog::parse(&bytes).expect("Meshes.dat should parse");
        let cat = &parsed.value;

        // The file is index (262_140 bytes) + records, so it must exceed the
        // bare index and contain at least one populated slot.
        assert!(
            bytes.len() >= CATALOG_SLOTS * 4,
            "file smaller than the 65535-slot index"
        );
        assert!(
            !cat.entries.is_empty(),
            "expected at least one mesh entry in the shipped catalog"
        );

        // Every decoded entry must be sane: a non-empty filename, finite
        // scale, no path traversal (the loader rejects `..` at Media.bb:832).
        for e in &cat.entries {
            assert!(!e.filename.is_empty(), "id {} has empty filename", e.id);
            assert!(e.scale.is_finite(), "id {} has non-finite scale", e.id);
            assert!(
                !e.filename.contains(".."),
                "id {} filename has traversal: {}",
                e.id,
                e.filename
            );
        }

        eprintln!(
            "parsed {} mesh entries ({} slots skipped); first: {:?}",
            cat.entries.len(),
            parsed.skipped.len(),
            cat.entries.first()
        );
    }

    /// Parse real `.b3d` models shipped in `data/Meshes/` and sanity-check the
    /// geometry. Skips gracefully if the data tree isn't present.
    #[test]
    fn parse_real_b3d_models() {
        let candidates = [
            "data/Meshes/Actors/Animals/rat.b3d",
            "data/Meshes/Actors/Animals/stag.b3d",
            "data/Meshes/Actors/Humans/Male_02.b3d",
        ];
        let root = repo_root();
        let mut parsed_any = false;
        for rel in candidates {
            let path = root.join(rel);
            let Ok(bytes) = std::fs::read(&path) else {
                continue;
            };
            parsed_any = true;
            let model = B3dModel::parse(&bytes)
                .unwrap_or_else(|e| panic!("{rel}: parse failed: {e}"));
            let vtx = model.vertex_count();
            let tris = model.triangle_count();
            assert!(!model.meshes.is_empty(), "{rel}: no meshes");
            assert!(vtx > 0, "{rel}: no vertices");
            assert!(tris > 0, "{rel}: no triangles");
            // Every index must reference a real vertex within its mesh, and
            // positions must be finite.
            for m in &model.meshes {
                for p in &m.positions {
                    assert!(p.iter().all(|c| c.is_finite()), "{rel}: non-finite vertex");
                }
                let n = m.positions.len() as u32;
                for &i in &m.indices {
                    assert!(i < n, "{rel}: index {i} out of range (verts {n})");
                }
            }
            eprintln!("{rel}: {} meshes, {vtx} verts, {tris} tris", model.meshes.len());
        }
        if !parsed_any {
            eprintln!("skipping: no .b3d files present under data/Meshes/");
        }
    }

    /// Verify the data-driven asset chain end to end: parse `Actors.dat`,
    /// resolve each actor's base mesh through the `Meshes.dat` catalog to a
    /// `.b3d` path, and confirm that path actually loads.
    #[test]
    fn actor_to_mesh_to_b3d_chain() {
        let root = repo_root();
        let Ok(actors_bytes) = std::fs::read(root.join("data/Server Data/Actors.dat")) else {
            eprintln!("skipping: no Actors.dat");
            return;
        };
        let Ok(mesh_bytes) = std::fs::read(root.join("data/Game Data/Meshes.dat")) else {
            eprintln!("skipping: no Meshes.dat");
            return;
        };
        let actors = ActorCatalog::parse(&actors_bytes).expect("Actors.dat parse");
        let meshes = MeshCatalog::parse(&mesh_bytes).expect("Meshes.dat parse").value;
        assert!(!actors.templates.is_empty(), "no actor templates");

        let mut resolved = 0;
        for t in actors.templates.values() {
            assert!(t.scale.is_finite() && t.scale > 0.0, "actor {} bad scale", t.id);
            let Some(mesh_id) = actors.mesh_for(t.id, 0) else {
                continue;
            };
            let Some(entry) = meshes.get(mesh_id) else {
                continue;
            };
            let path = root.join("data/Meshes").join(entry.filename.replace('\\', "/"));
            if let Ok(b3d_bytes) = std::fs::read(&path) {
                let model = B3dModel::parse(&b3d_bytes)
                    .unwrap_or_else(|e| panic!("actor '{}' mesh {}: {e}", t.race, entry.filename));
                assert!(model.vertex_count() > 0);
                resolved += 1;
                eprintln!(
                    "actor #{} '{}' -> mesh {} '{}' -> {} verts",
                    t.id, t.race, mesh_id, entry.filename, model.vertex_count()
                );
            }
        }
        eprintln!(
            "{} actor templates, {} resolved full chain to a loadable .b3d",
            actors.templates.len(),
            resolved
        );
        assert!(resolved > 0, "no actor resolved to a loadable mesh");
    }

    /// Parse real client area files and sanity-check the scenery list: a sane
    /// count, and mesh ids that resolve through the mesh catalog to loadable
    /// `.b3d` files (confirms the 41-byte header offset is correct).
    #[test]
    fn parse_real_emitter_configs() {
        let root = repo_root();
        let mut any = false;
        for name in ["Flame", "Fireball", "fountain", "Rain", "Snow", "Waterfall", "Blood"] {
            let path = root.join(format!("data/Emitter Configs/{name}.rpc"));
            let Ok(bytes) = std::fs::read(&path) else { continue };
            any = true;
            let c = emitter::EmitterConfig::parse(&bytes).unwrap_or_else(|e| panic!("{name}: {e}"));
            // A misaligned parse yields garbage — sane bounds are the format check.
            assert!(c.max_particles > 0 && c.max_particles < 1_000_000, "{name}: max {}", c.max_particles);
            assert!(c.lifespan > 0 && c.lifespan < 1_000_000, "{name}: life {}", c.lifespan);
            assert!((1..=3).contains(&c.shape), "{name}: shape {}", c.shape);
            assert!((1..=3).contains(&c.blend_mode), "{name}: blend {}", c.blend_mode);
            assert!(c.scale_start.is_finite() && c.alpha_start.is_finite(), "{name}: non-finite");
            eprintln!(
                "{name}: max={} ppf={} life={} shape={} blend={} scale={}->{} a={}->{} col={:?} tex={}x{}",
                c.max_particles, c.particles_per_frame, c.lifespan, c.shape, c.blend_mode,
                c.scale_start, c.scale_change, c.alpha_start, c.alpha_change, c.color_start, c.tex_across, c.tex_down
            );
        }
        if !any {
            eprintln!("skipping: no emitter configs");
        }
    }

    #[test]
    fn parse_real_area_scenery() {
        let root = repo_root();
        let Ok(mesh_bytes) = std::fs::read(root.join("data/Game Data/Meshes.dat")) else {
            eprintln!("skipping: no Meshes.dat");
            return;
        };
        let meshes = MeshCatalog::parse(&mesh_bytes).expect("meshes").value;

        let mut any = false;
        for zone in ["Plains", "Test Zone", "Northern Shrine"] {
            let path = root.join(format!("data/Areas/{zone}.dat"));
            let Ok(bytes) = std::fs::read(&path) else { continue };
            any = true;
            let area = area::AreaScenery::parse(&bytes)
                .unwrap_or_else(|e| panic!("{zone}: {e}"));
            assert!(
                area.sceneries.len() < 100_000,
                "{zone}: implausible scenery count {}",
                area.sceneries.len()
            );
            let mut resolved = 0;
            for s in &area.sceneries {
                assert!(s.pos.iter().all(|c| c.is_finite()), "{zone}: non-finite pos");
                assert!(s.scale.iter().all(|c| c.is_finite()), "{zone}: non-finite scale");
                if meshes.get(s.mesh_id).is_some() {
                    resolved += 1;
                }
            }
            eprintln!(
                "{zone}: {} scenery objects, {resolved} with catalog meshes; first: {:?}",
                area.sceneries.len(),
                area.sceneries.first().map(|s| (s.mesh_id, s.pos))
            );
            // LOD-terrain parse rides on a correct colbox/emitter skip: a
            // misaligned skip yields a garbage terrain count, so a sane count is
            // itself the alignment check. Each patch's grid must be exactly
            // (N+1)².
            assert!(area.terrains.len() < 4096, "{zone}: implausible terrain count {}", area.terrains.len());
            for t in &area.terrains {
                assert_eq!(t.heights.len(), (t.grid as usize + 1).pow(2), "{zone}: terrain grid mismatch");
                assert!(t.heights.iter().all(|h| h.is_finite()), "{zone}: non-finite terrain height");
                assert!(t.pos.iter().all(|c| c.is_finite()) && t.rot.iter().all(|c| c.is_finite()), "{zone}: non-finite terrain xform");
            }
            eprintln!("{zone}: {} water plane(s), {} LOD terrain(s)", area.waters.len(), area.terrains.len());
            // Sky texture resolution (for the skydome port).
            let sky = area.env.sky_tex_id;
            let sky_file = std::fs::read(root.join("data/Game Data/Textures.dat"))
                .ok()
                .and_then(|b| catalog::TextureCatalog::parse(&b).ok())
                .filter(|_| sky != 65535)
                .and_then(|p| p.value.get(sky).map(|e| e.filename.clone()));
            eprintln!("{zone}: sky_tex_id={sky} -> {sky_file:?}  fog={:?}", area.env.fog_color);
            let resolve = |id: u16| -> Option<String> {
                std::fs::read(root.join("data/Game Data/Textures.dat"))
                    .ok()
                    .and_then(|b| catalog::TextureCatalog::parse(&b).ok())
                    .filter(|_| id != 65535)
                    .and_then(|p| p.value.get(id).map(|e| e.filename.clone()))
            };
            eprintln!(
                "{zone}: cloud={} -> {:?}  stars={} -> {:?}",
                area.env.cloud_tex_id, resolve(area.env.cloud_tex_id),
                area.env.stars_tex_id, resolve(area.env.stars_tex_id)
            );
            // If there's scenery at all, most should resolve to real meshes.
            if !area.sceneries.is_empty() {
                assert!(
                    resolved * 2 >= area.sceneries.len(),
                    "{zone}: only {resolved}/{} scenery meshes resolved — header offset likely wrong",
                    area.sceneries.len()
                );
            }
        }
        if !any {
            eprintln!("skipping: no area .dat files present");
        }
    }

    /// Parse a real animated actor `.b3d` and verify the skeleton/animation
    /// decode matches the raw chunk counts (see the `b3d_probe` bin): Male_02
    /// has 32 nodes (31 with skin weights), 1599 weights, 24920 keyframes, and
    /// a 1539-frame anim. Skips if the file is absent.
    #[test]
    fn parse_skeleton_and_animation() {
        let path = repo_root().join("data/Meshes/Actors/Humans/Male_02.b3d");
        let Ok(bytes) = std::fs::read(&path) else {
            eprintln!("skipping: {} not present", path.display());
            return;
        };
        let model = B3dModel::parse(&bytes).expect("Male_02 parse");
        assert_eq!(model.bones.len(), 32, "node/bone count");
        // Total weights/keyframes match the raw chunk walk (b3d_probe). Some
        // BONE chunks are empty structural bones, so "bones with weights" (21)
        // is < the 31 BONE chunks — the totals are the real invariant.
        assert_eq!(model.weight_count(), 1599, "total skin weights");
        assert_eq!(model.keyframe_count(), 24920, "total keyframes");
        let anim = model.anim.expect("anim header");
        assert_eq!(anim.frames, 1539);
        assert!((anim.fps - 15.0).abs() < 1e-3);

        // Every weight references a valid vertex of the single mesh; parents
        // precede children; inverse_bind · bind_world ≈ identity.
        let verts = model.meshes.iter().map(|m| m.positions.len()).max().unwrap_or(0) as u32;
        for (i, b) in model.bones.iter().enumerate() {
            if let Some(p) = b.parent {
                assert!(p < i, "bone {i} parent {p} not before it");
            }
            for &(vid, _) in &b.weights {
                assert!(vid < verts, "bone '{}' weight vid {vid} >= {verts}", b.name);
            }
        }
        eprintln!(
            "Male_02: {} bones, {} weights, {} keyframes, {} frames @ {}fps",
            model.bones.len(), model.weight_count(), model.keyframe_count(),
            anim.frames, anim.fps
        );
    }

    /// Parse the real `Animations.dat` and verify the Player set's named clips
    /// (Idle/Walk/Run) decode with sane frame ranges. Skips if absent.
    #[test]
    fn parse_animation_sets() {
        let path = repo_root().join("data/Game Data/Animations.dat");
        let Ok(bytes) = std::fs::read(&path) else {
            eprintln!("skipping: {} not present", path.display());
            return;
        };
        let cat = anim::AnimSetCatalog::parse(&bytes).expect("Animations.dat parse");
        assert!(!cat.sets.is_empty(), "no anim sets");
        // The Player set (id 0) carries the human clips.
        let player = cat.get(0).expect("player anim set 0");
        let walk = player.clip("Walk").expect("Walk clip");
        let run = player.clip("Run").expect("Run clip");
        let idle = player.find(&["Idle"]).expect("an Idle clip");
        assert!(walk.start >= 0 && walk.end >= walk.start, "walk range");
        assert!(run.end >= run.start, "run range");
        assert!(idle.end >= idle.start, "idle range");
        // All clip ranges must sit inside a plausible timeline bound.
        for c in &player.clips {
            assert!(c.start >= 0 && c.end < 100_000, "clip '{}' insane range", c.name);
        }
        eprintln!(
            "Player set: {} clips; Walk[{}..{}] Run[{}..{}] Idle[{}..{}]",
            player.clips.len(), walk.start, walk.end, run.start, run.end, idle.start, idle.end
        );
    }

    /// Skinning correctness: posing at the bind pose must reproduce the bind
    /// geometry exactly (skin matrices are identity), and posing at a real frame
    /// must actually move vertices. Skips if the model is absent.
    #[test]
    fn skinning_bind_is_identity_and_frame_deforms() {
        let path = repo_root().join("data/Meshes/Actors/Humans/Male_02.b3d");
        let Ok(bytes) = std::fs::read(&path) else {
            eprintln!("skipping: {} not present", path.display());
            return;
        };
        let model = B3dModel::parse(&bytes).expect("parse");

        // Bind pose == original geometry (within fp tolerance).
        let bind = model.posed_meshes(None);
        assert_eq!(bind.len(), model.meshes.len());
        let mut max_err = 0.0f32;
        for (a, b) in bind.iter().zip(&model.meshes) {
            assert_eq!(a.positions.len(), b.positions.len());
            for (p, q) in a.positions.iter().zip(&b.positions) {
                for k in 0..3 {
                    max_err = max_err.max((p[k] - q[k]).abs());
                }
            }
        }
        assert!(max_err < 1e-2, "bind-pose skin drifted from bind geometry: {max_err}");

        // A real frame must move at least some vertices a non-trivial amount.
        let posed = model.posed_meshes(Some(2.0));
        let mut moved = 0usize;
        for (a, b) in posed.iter().zip(&model.meshes) {
            for (p, q) in a.positions.iter().zip(&b.positions) {
                let d = ((p[0] - q[0]).powi(2) + (p[1] - q[1]).powi(2) + (p[2] - q[2]).powi(2)).sqrt();
                if d > 0.1 {
                    moved += 1;
                }
            }
        }
        assert!(moved > 100, "frame 2 barely deformed the mesh ({moved} verts moved)");
        eprintln!("skinning: bind max_err={max_err:.2e}, frame-2 moved {moved} verts");

        // Regression guard for the conjugate-quaternion bug: a valid animated
        // pose stays within the body's silhouette — the distortion splayed
        // limbs far outside it, inflating the bounding box. Frame 10 (mid-Walk)
        // must not exceed 1.6x the bind extent in any axis.
        let bbox = |meshes: &[B3dMesh]| {
            let mut lo = [f32::MAX; 3];
            let mut hi = [f32::MIN; 3];
            for m in meshes {
                for p in &m.positions {
                    for k in 0..3 {
                        lo[k] = lo[k].min(p[k]);
                        hi[k] = hi[k].max(p[k]);
                    }
                }
            }
            [hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]]
        };
        let bind_ext = bbox(&model.meshes);
        let walk_ext = bbox(&model.posed_meshes(Some(10.0)));
        for k in 0..3 {
            assert!(
                walk_ext[k] <= bind_ext[k] * 1.6 + 1.0,
                "frame-10 axis {k} extent {} >> bind {} — skinning distortion regressed",
                walk_ext[k], bind_ext[k]
            );
        }
    }

    /// Does the Human actor's selected body/face texture resolve through
    /// Textures.dat to a real, loadable skin image? (Decides whether the actor
    /// texture system can replace the b3d UV-guide textures.)
    #[test]
    fn actor_skin_resolves_via_texture_catalog() {
        let root = repo_root();
        let (Ok(ab), Ok(tb)) = (
            std::fs::read(root.join("data/Server Data/Actors.dat")),
            std::fs::read(root.join("data/Game Data/Textures.dat")),
        ) else {
            eprintln!("skipping: missing Actors.dat / Textures.dat");
            return;
        };
        let actors = ActorCatalog::parse(&ab).expect("actors");
        let texcat = TextureCatalog::parse(&tb).expect("textures").value;
        eprintln!("Textures.dat: {} entries", texcat.entries.len());
        for t in actors.templates.values() {
            let body0 = t.male_body_ids[0];
            let face0 = t.male_face_ids[0];
            let resolve = |id: u16| -> String {
                if id == 65535 {
                    return "(none)".into();
                }
                match texcat.get(id) {
                    Some(e) => {
                        let p = root.join("data/Textures").join(e.filename.replace('\\', "/"));
                        format!("{} [{}]", e.filename, if p.exists() { "exists" } else { "MISSING" })
                    }
                    None => format!("id {id} not in catalog"),
                }
            };
            eprintln!(
                "actor '{}': body[0]={} -> {}, face[0]={} -> {}",
                t.race, body0, resolve(body0), face0, resolve(face0)
            );
        }
    }
}
