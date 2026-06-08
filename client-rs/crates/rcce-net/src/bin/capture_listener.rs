//! Raw UDP capture: binds the server port and hex-dumps the first datagrams
//! any client sends. Used to read the SHIPPED `RCEnet.dll`'s real ENet
//! handshake bytes (by pointing the genuine `Client.exe` at it) and diff them
//! against the stock-ENet connect our `rusty_enet` probe emits.
//!
//! Steps:
//!   1. Stop the real server (free UDP 25000).
//!   2. cargo run -p rcce-net --bin capture-listener -- 0.0.0.0:25000
//!   3. Launch the genuine Client.exe and attempt to connect to localhost.
//!   4. Compare the captured connect bytes to the stock connect logged by
//!      `wire-probe` (header: peerID u16, sentTime u16; command 0x82 0xFF ...).
//!
//! No reply is sent — the client won't complete a session, but the first
//! datagram (the ENet CONNECT command) is all we need.

use std::net::UdpSocket;
use std::time::{Duration, Instant};

fn hexdump(bytes: &[u8]) -> String {
    bytes
        .chunks(16)
        .map(|c| c.iter().map(|b| format!("{b:02x}")).collect::<Vec<_>>().join(" "))
        .collect::<Vec<_>>()
        .join("\n         ")
}

fn main() {
    let bind = std::env::args().nth(1).unwrap_or_else(|| "0.0.0.0:25000".to_string());
    let secs: u64 = std::env::args().nth(2).and_then(|s| s.parse().ok()).unwrap_or(120);

    let sock = UdpSocket::bind(&bind).unwrap_or_else(|e| {
        eprintln!("[capture] cannot bind {bind}: {e}\n(is the real server still holding the port? stop it first.)");
        std::process::exit(2);
    });
    sock.set_read_timeout(Some(Duration::from_millis(250))).unwrap();
    println!("[capture] listening on {bind} for {secs}s — now launch the real Client.exe and connect to localhost");

    let mut buf = [0u8; 4096];
    let mut n_pkts = 0usize;
    let deadline = Instant::now() + Duration::from_secs(secs);
    while Instant::now() < deadline {
        match sock.recv_from(&mut buf) {
            Ok((len, src)) => {
                n_pkts += 1;
                let d = &buf[..len];
                println!("\n[capture] #{n_pkts} from {src}  {len} bytes");
                // Decode the ENet header the same way stock does, for quick diff.
                if len >= 4 {
                    let peer_id = u16::from_be_bytes([d[0], d[1]]);
                    let sent_time = u16::from_be_bytes([d[2], d[3]]);
                    println!(
                        "         hdr: peerID=0x{peer_id:04x} (flags: sent_time={}, compressed={}) sentTime=0x{sent_time:04x}",
                        peer_id & 0x8000 != 0,
                        peer_id & 0x4000 != 0,
                    );
                    if len >= 8 {
                        println!(
                            "         cmd: 0x{:02x} (base {}, ack={}) channelID=0x{:02x} relSeq=0x{:04x}",
                            d[4], d[4] & 0x0f, d[4] & 0x80 != 0, d[5],
                            u16::from_be_bytes([d[6], d[7]])
                        );
                    }
                }
                println!("         raw: {}", hexdump(d));
                if n_pkts >= 8 {
                    println!("\n[capture] captured {n_pkts} packets — enough; stopping.");
                    break;
                }
            }
            Err(_) => continue, // read timeout; keep waiting
        }
    }
    println!("\n[capture] done ({n_pkts} packets). Diff the connect header above against the stock one from wire-probe.");
}
