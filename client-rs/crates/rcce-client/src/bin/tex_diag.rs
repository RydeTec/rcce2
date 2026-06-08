//! Texture-resolution diagnostic: for a zone, list each unique scenery mesh,
//! its b3d sub-mesh texture names, and whether each resolves to a file on disk.
//! Pinpoints why some scenery renders untextured (white).
//!
//!   cargo run -p rcce-client --bin tex_diag --release -- "Plains"

use rcce_data::{texture, AreaScenery, B3dModel, MeshCatalog};

fn main() {
    let zone = std::env::args().nth(1).unwrap_or_else(|| "Plains".to_string());
    let data_root = std::env::var("RCCE_DATA")
        .unwrap_or_else(|_| r"C:\Users\dyanr\Desktop\rcce2\data".to_string());
    let root = std::path::PathBuf::from(&data_root);

    let meshes = MeshCatalog::parse(&std::fs::read(root.join("Game Data/Meshes.dat")).unwrap())
        .unwrap()
        .value;
    let area = AreaScenery::parse(&std::fs::read(root.join(format!("Areas/{zone}.dat"))).unwrap())
        .unwrap();

    let mut seen = std::collections::HashSet::new();
    for s in &area.sceneries {
        if !seen.insert(s.mesh_id) {
            continue;
        }
        let Some(entry) = meshes.get(s.mesh_id) else {
            println!("mesh {} : NOT IN CATALOG", s.mesh_id);
            continue;
        };
        let rel = entry.filename.replace('\\', "/");
        let mesh_path = root.join("Meshes").join(&rel);
        let Ok(bytes) = std::fs::read(&mesh_path) else {
            println!("mesh {} '{}' : FILE MISSING", s.mesh_id, rel);
            continue;
        };
        let Ok(model) = B3dModel::parse(&bytes) else {
            println!("mesh {} '{}' : PARSE FAIL", s.mesh_id, rel);
            continue;
        };

        let dir = mesh_path.parent().unwrap().to_path_buf();
        let roots = vec![dir.clone(), root.join("Textures"), root.join("Meshes")];

        println!("\nmesh {} '{}' ({} sub-meshes)", s.mesh_id, rel, model.meshes.len());
        // model.textures = the b3d TEXS chunk (all texture refs); per-mesh
        // m.texture is the resolved name for that sub-mesh.
        for (i, m) in model.meshes.iter().enumerate() {
            match &m.texture {
                None => println!("  [{i}] (no texture ref)"),
                Some(name) => {
                    let base = texture::basename(name);
                    let found = texture::find_texture(&roots, name);
                    let loaded = found.as_ref().and_then(|p| texture::load(p)).is_some();
                    let f = m.texture_flag;
                    println!(
                        "  [{i}] flags={f}(mask={} alpha={}) b3d='{}' base='{}' -> {}",
                        (f & 4) != 0,
                        (f & 2) != 0,
                        name,
                        base,
                        match (&found, loaded) {
                            (Some(p), true) => format!("OK {}", p.display()),
                            (Some(p), false) => format!("FOUND-but-DECODE-FAIL {}", p.display()),
                            (None, _) => "UNRESOLVED".to_string(),
                        }
                    );
                }
            }
        }
    }
}
