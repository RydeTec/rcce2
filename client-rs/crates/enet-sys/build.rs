//! Compile the vendored RCEnet ENet fork (vendor/*.c) into a static lib.
//! win32.c is `#ifdef WIN32`, unix.c is `#ifndef WIN32`, so both are listed and
//! the inactive one compiles to nothing.

fn main() {
    let windows = std::env::var("CARGO_CFG_WINDOWS").is_ok();

    let mut b = cc::Build::new();
    b.include("vendor/include");
    b.warnings(false);
    for f in [
        "callbacks.c",
        "host.c",
        "list.c",
        "packet.c",
        "peer.c",
        "protocol.c",
        "unix.c",
        "win32.c",
    ] {
        b.file(format!("vendor/{f}"));
    }
    if windows {
        // ENet's platform code keys off WIN32 (no underscore); MSVC only
        // guarantees _WIN32, so define WIN32 explicitly.
        b.define("WIN32", None);
    } else {
        // macOS and modern Linux/glibc provide socklen_t in <sys/socket.h>.
        // vendor/unix.c only falls back to its own `typedef int socklen_t;`
        // when HAS_SOCKLEN_T is undefined — and that fallback collides with
        // the system typedef, a hard error under clang. Signal that the
        // platform has it so ENet skips the conflicting definition. Every
        // other HAS_* guard safely takes its portable fallback branch (which
        // uses BSD socket APIs present on both macOS and Linux), so this is
        // the only flag the cc build needs on unix.
        b.define("HAS_SOCKLEN_T", None);
    }
    b.compile("rcenet_c");

    if windows {
        println!("cargo:rustc-link-lib=ws2_32");
        println!("cargo:rustc-link-lib=winmm");
    }

    println!("cargo:rerun-if-changed=vendor");
}
