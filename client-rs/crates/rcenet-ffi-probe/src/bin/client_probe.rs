//! End-to-end transport check: connect to the live server through
//! `FfiTransport`, then pump `poll()` and log every message the server sends.
//! Proves the full FFI connect + receive path against the real server.
//!
//!   cargo run -p rcenet-ffi-probe --bin client-probe --target i686-pc-windows-msvc \
//!       -- "C:\Users\dyanr\Desktop\rcce2\bin\RCEnet.dll" 127.0.0.1 25000

use std::thread::sleep;
use std::time::{Duration, Instant};

use rcce_net::Transport;
use rcenet_ffi::FfiTransport;

fn main() {
    let mut args = std::env::args().skip(1);
    let dll = args
        .next()
        .unwrap_or_else(|| r"C:\Users\dyanr\Desktop\rcce2\bin\RCEnet.dll".to_string());
    let host = args.next().unwrap_or_else(|| "127.0.0.1".to_string());
    let port: u16 = args.next().and_then(|s| s.parse().ok()).unwrap_or(25000);

    let mut t = match FfiTransport::load(&dll) {
        Ok(t) => t,
        Err(e) => {
            eprintln!("[client] {e}");
            std::process::exit(2);
        }
    };
    println!("[client] connecting to {host}:{port} ...");
    let peer = match t.connect(&host, port) {
        Ok(p) => p,
        Err(e) => {
            eprintln!("[client] connect failed: {e}");
            std::process::exit(1);
        }
    };
    println!("[client] ✓ connected, peer handle = {peer} (real ENet handshake via RCEnet.dll)");

    // Pump for a few seconds and log whatever the server pushes after a bare
    // connect (it sent our type-0 NewClient internally). Many servers stay
    // quiet until login; either way this exercises the full receive path.
    let mut total = 0usize;
    let deadline = Instant::now() + Duration::from_secs(4);
    while Instant::now() < deadline {
        for m in t.poll() {
            total += 1;
            let preview: Vec<u8> = m.data.iter().take(12).copied().collect();
            println!(
                "[client] ← type={} conn={} len={} first={:02x?}",
                m.msg_type, m.connection, m.data.len(), preview
            );
        }
        sleep(Duration::from_millis(50));
    }

    println!("[client] total messages received: {total}");
    t.disconnect(peer);
    println!("[client] disconnected. Transport (connect+poll+send+disconnect) exercised end-to-end.");
}
