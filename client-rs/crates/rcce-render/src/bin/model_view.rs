//! Render a real `.b3d` model (textured) to a PNG. Resolves each mesh's texture
//! by basename against the mesh dir + the project texture dirs, decodes it
//! (BMP/PNG), and renders. `cargo run --release -p rcce-render --bin model-view
//! -- <file.b3d> [out.png]`

use std::path::{Path, PathBuf};

use rcce_data::{texture, B3dModel, Image};

fn main() {
    let mut args = std::env::args().skip(1);
    let path = args.next().unwrap_or_else(|| {
        r"C:\Users\dyanr\Desktop\rcce2\data\Meshes\Actors\Humans\Male_02.b3d".to_string()
    });
    let out = args.next().unwrap_or_else(|| "model.png".to_string());

    let bytes = std::fs::read(&path).unwrap_or_else(|e| {
        eprintln!("[model-view] cannot read {path}: {e}");
        std::process::exit(2);
    });
    let model = B3dModel::parse(&bytes).unwrap_or_else(|e| {
        eprintln!("[model-view] parse failed: {e}");
        std::process::exit(1);
    });
    println!(
        "[model-view] {path}: {} meshes, {} verts, {} tris, {} textures",
        model.meshes.len(),
        model.vertex_count(),
        model.triangle_count(),
        model.textures.len(),
    );

    // Texture search roots: the mesh's own directory first, then the project's
    // Textures / Meshes trees (found by walking up to the `data` dir).
    let mesh_path = Path::new(&path);
    let mut roots: Vec<PathBuf> = Vec::new();
    if let Some(dir) = mesh_path.parent() {
        roots.push(dir.to_path_buf());
    }
    if let Some(data) = mesh_path
        .ancestors()
        .find(|a| a.file_name().map(|n| n.eq_ignore_ascii_case("data")).unwrap_or(false))
    {
        roots.push(data.join("Textures"));
        roots.push(data.join("Meshes"));
    }

    let textures: Vec<Option<Image>> = model
        .meshes
        .iter()
        .map(|m| {
            m.texture
                .as_ref()
                .and_then(|name| texture::find_texture(&roots, name))
                .and_then(|p| texture::load(&p))
        })
        .collect();

    for (i, (m, t)) in model.meshes.iter().zip(&textures).enumerate() {
        println!(
            "[model-view]   mesh {i}: brush {}, texture {:?} -> {}",
            m.brush_id,
            m.texture.as_deref().map(texture::basename),
            match t {
                Some(img) => format!("loaded {}x{}", img.width, img.height),
                None => "not loaded".to_string(),
            }
        );
    }

    match rcce_render::render_model_png(&model, &textures, 0.6, 900, 900, &out) {
        Ok(adapter) => println!("[model-view] rendered via {adapter} -> {out}"),
        Err(e) => {
            eprintln!("[model-view] render failed: {e}");
            std::process::exit(1);
        }
    }
}
