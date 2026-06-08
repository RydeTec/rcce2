//! Confirms wgpu works on this target. `cargo run -p rcce-render --bin gpu-probe
//! --target i686-pc-windows-msvc`.

fn main() {
    match rcce_render::probe_gpu() {
        Ok(desc) => println!("[gpu-probe] OK — {desc}"),
        Err(e) => {
            eprintln!("[gpu-probe] FAIL — {e}");
            std::process::exit(1);
        }
    }
}
