//! Drive the SHIPPED `bin/RCEnet.dll` to emit its own ENet connect handshake,
//! so a UDP capture listener on the target port records the exact bytes. This
//! is the ground-truth the pure-Rust `rusty_enet` connect must match.
//!
//! Calling convention: `BBDECL = extern "C" __declspec(dllexport)` with no
//! `/Gz` override and undecorated export names (no `@N`) → **cdecl** → Rust
//! `extern "C"`.
//!
//! Usage (must be the 32-bit build — the DLL is x86):
//!   1. start the capture listener:  cargo run -p rcce-net --bin capture-listener -- 0.0.0.0:25000
//!   2. cargo run -p rcenet-ffi-probe --target i686-pc-windows-msvc -- <dll_path> 127.0.0.1 25000

use std::ffi::CString;
use std::os::raw::{c_char, c_int};

type RceConnect = unsafe extern "C" fn(
    host: *const c_char,
    host_port: c_int,
    my_port: c_int,
    my_name: *const c_char,
    my_data: *const c_char,
    log_file: *const c_char,
    append: c_int,
) -> c_int;

fn main() {
    let mut args = std::env::args().skip(1);
    let dll_path = args
        .next()
        .unwrap_or_else(|| r"C:\Users\dyanr\Desktop\rcce2\bin\RCEnet.dll".to_string());
    let host = args.next().unwrap_or_else(|| "127.0.0.1".to_string());
    let port: c_int = args.next().and_then(|s| s.parse().ok()).unwrap_or(25000);

    println!("[ffi] loading {dll_path}");
    println!("[ffi] will RCE_Connect -> {host}:{port} (blocks ~5s waiting for a reply)");

    // SAFETY: loading a known DLL and calling a function whose signature we
    // verified against the in-repo source (main.h / main.cpp). cdecl ABI.
    unsafe {
        let lib = match libloading::Library::new(&dll_path) {
            Ok(l) => l,
            Err(e) => {
                eprintln!("[ffi] FAILED to load DLL: {e}");
                eprintln!("[ffi] (is this the 32-bit build? the DLL is x86. build with --target i686-pc-windows-msvc)");
                std::process::exit(2);
            }
        };
        let rce_connect: libloading::Symbol<RceConnect> = match lib.get(b"RCE_Connect\0") {
            Ok(s) => s,
            Err(e) => {
                eprintln!("[ffi] FAILED to find RCE_Connect: {e}");
                std::process::exit(2);
            }
        };

        let c_host = CString::new(host).unwrap();
        let c_name = CString::new("ffiprobe").unwrap();
        let c_data = CString::new("").unwrap();
        let c_log = CString::new(r"Data\Logs\ffi_probe_connection.txt").unwrap();

        println!("[ffi] calling RCE_Connect...");
        let ret = rce_connect(
            c_host.as_ptr(),
            port,
            0,
            c_name.as_ptr(),
            c_data.as_ptr(),
            c_log.as_ptr(),
            0, // Append = false
        );
        // Return: peer pointer cast to int on success, or negative error
        // (-1 host_create, -2 timeout/no-connect, -4 no-peer). Against a
        // non-replying capture listener we expect -2 — but the connect bytes
        // were already transmitted, which is all the capture needs.
        println!("[ffi] RCE_Connect returned: {ret}");
        match ret {
            -1 => println!("[ffi]   (-1 = enet_host_create failed)"),
            -2 => println!("[ffi]   (-2 = no VERIFY_CONNECT within 5s — EXPECTED vs a capture listener; bytes were still sent)"),
            -4 => println!("[ffi]   (-4 = enet_host_connect returned no peer)"),
            n if n > 0 => println!("[ffi]   (>0 = CONNECTED — a real ENet host replied!)"),
            _ => {}
        }
    }
    println!("[ffi] done — check the capture listener output for the DLL's connect bytes.");
}
