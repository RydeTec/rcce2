//! Confirms the vendored ENet C compiled + links + runs (any target/bitness).

fn main() {
    let init = enet_sys::smoke();
    println!("[enet-smoke] enet_initialize={init}");
    if init != 0 {
        eprintln!("[enet-smoke] FAIL: enet_initialize returned {init}");
        std::process::exit(1);
    }
    println!("[enet-smoke] OK — vendored ENet fork builds, links, and runs (64-bit native).");
}
