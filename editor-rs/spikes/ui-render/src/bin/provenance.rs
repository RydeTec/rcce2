use rcce_ui_render_spike::provenance::{cargo_lock_sha256, executable_source_sha256, hash_bytes};
use serde::Serialize;
use std::{
    collections::BTreeMap,
    env, fs, io,
    path::{Path, PathBuf},
};

#[derive(Serialize)]
struct Manifest {
    schema: u32,
    algorithm: &'static str,
    executable_source_sha256: String,
    cargo_lock_sha256: String,
    trace_sha256: String,
    bindings: BTreeMap<String, String>,
}

fn hash(path: &Path) -> io::Result<String> {
    fs::read(path).map(|bytes| hash_bytes(&bytes))
}

fn collect(
    root: &Path,
    path: &Path,
    output: &Path,
    bindings: &mut BTreeMap<String, String>,
) -> io::Result<()> {
    let mut entries: Vec<_> = fs::read_dir(path)?.collect::<Result<_, _>>()?;
    entries.sort_by_key(std::fs::DirEntry::file_name);
    for entry in entries {
        let candidate = entry.path();
        if candidate == output {
            continue;
        }
        if candidate.is_dir() {
            collect(root, &candidate, output, bindings)?;
        } else {
            let relative = candidate.strip_prefix(root).map_err(io::Error::other)?;
            bindings.insert(
                relative.to_string_lossy().replace('\\', "/"),
                hash(&candidate)?,
            );
        }
    }
    Ok(())
}

fn main() -> Result<(), String> {
    let values: Vec<_> = env::args_os().skip(1).map(PathBuf::from).collect();
    let [root, output] = values.as_slice() else {
        return Err("usage: provenance <evidence-root> <output-json>".to_owned());
    };
    let mut bindings = BTreeMap::new();
    collect(root, root, output, &mut bindings).map_err(|error| error.to_string())?;
    let manifest = Manifest {
        schema: 1,
        algorithm: "sha256",
        executable_source_sha256: executable_source_sha256(),
        cargo_lock_sha256: cargo_lock_sha256(),
        trace_sha256: hash_bytes(include_bytes!("../../traces/standard-v1.json")),
        bindings,
    };
    fs::write(
        output,
        serde_json::to_vec_pretty(&manifest).map_err(|error| error.to_string())?,
    )
    .map_err(|error| error.to_string())
}
