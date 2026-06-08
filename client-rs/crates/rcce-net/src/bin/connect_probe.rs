//! Phase-0 transport spike: prove that pure-Rust ENet (`rusty_enet`, transpiled
//! from C → wire-compatible) can complete the ENet handshake against the real,
//! unmodified RCCE2 server, and exercise the `[type][payload]` framing.
//!
//! Run the server headless+unlocked first (from the main checkout):
//!     ./bin/Server.exe -UNLOCK            # opens UDP 25000
//! then:
//!     cargo run -p rcce-net --bin connect-probe -- 127.0.0.1:25000
//!
//! Success = a `Connect` event fires (handshake completed → wire-compatible).
//! The server won't push gameplay packets without a `P_StartGame` login, so an
//! absence of `Receive` events here is expected; that's the next milestone.

use std::net::{SocketAddr, UdpSocket};
use std::time::{Duration, Instant};

use rusty_enet::{Event, Host, HostSettings, Packet};

use rcce_net::{frame, unframe, CHANNEL_RELIABLE, CONNECT_CHANNELS, MSG_NEW_CLIENT};

fn main() {
    let addr: SocketAddr = std::env::args()
        .nth(1)
        .unwrap_or_else(|| "127.0.0.1:25000".to_string())
        .parse()
        .expect("usage: connect-probe <ip:port>");

    let run_secs: u64 = std::env::args()
        .nth(2)
        .and_then(|s| s.parse().ok())
        .unwrap_or(6);

    println!("[probe] target {addr}, running {run_secs}s");

    // Bind on loopback to match the peer's family for local servers. (For a
    // remote server, override via the bind addr if needed.)
    let bind = if addr.ip().is_loopback() { "127.0.0.1:0" } else { "0.0.0.0:0" };
    let socket = UdpSocket::bind(bind).expect("bind local udp");
    socket.set_nonblocking(true).expect("set nonblocking");
    println!("[probe] local socket {}", socket.local_addr().unwrap());

    let mut host = Host::new(
        socket,
        HostSettings {
            peer_limit: 1,
            ..Default::default() // channel_limit defaults to the ENet max (255)
        },
    )
    .expect("create host");

    // Initiate the ENet connect. Matches the DLL's 254-channel connect.
    host.connect(addr, CONNECT_CHANNELS, 0)
        .expect("no available peers");
    println!("[probe] connect initiated ({CONNECT_CHANNELS} channels); servicing...");

    let mut connected = false;
    let mut sent_new_client = false;
    let mut rx_count = 0usize;
    let mut rx_bytes = 0usize;
    let mut types_seen: Vec<u8> = Vec::new();

    let deadline = Instant::now() + Duration::from_secs(run_secs);
    'outer: while Instant::now() < deadline {
        loop {
            let event = match host.service() {
                Ok(Some(event)) => event,
                Ok(None) => break, // no more events this poll; sleep then re-service
                Err(e) => {
                    println!("[probe] ! service error: {e}");
                    break 'outer;
                }
            };
            match event {
                Event::Connect { peer, data } => {
                    connected = true;
                    println!(
                        "[probe] ✓ CONNECT  peer={:?} data={} — ENet handshake OK (wire-compatible)",
                        peer.id(),
                        data
                    );
                    // Announce ourselves exactly as the DLL does on connect:
                    // type 0, empty payload, reliable, channel 1.
                    let pkt = frame(MSG_NEW_CLIENT, &[]);
                    if let Err(e) = peer.send(CHANNEL_RELIABLE, &Packet::reliable(pkt.as_slice())) {
                        println!("[probe] ! failed to send NewClient: {e:?}");
                    } else {
                        sent_new_client = true;
                        println!("[probe] → sent NewClient (type 0, reliable, ch {CHANNEL_RELIABLE})");
                    }
                }
                Event::Receive {
                    channel_id,
                    packet,
                    ..
                } => {
                    rx_count += 1;
                    rx_bytes += packet.data().len();
                    if let Some((ty, payload)) = unframe(packet.data()) {
                        if !types_seen.contains(&ty) {
                            types_seen.push(ty);
                        }
                        println!(
                            "[probe] ← RECV  ch={channel_id} type={ty} payload={}B",
                            payload.len()
                        );
                    }
                }
                Event::Disconnect { data, .. } => {
                    println!("[probe] ✗ DISCONNECT data={data}");
                }
            }
        }
        std::thread::sleep(Duration::from_millis(10));
    }

    println!("\n[probe] ===== summary =====");
    println!("[probe] connected:        {connected}");
    println!("[probe] sent NewClient:   {sent_new_client}");
    println!("[probe] packets received: {rx_count} ({rx_bytes} bytes)");
    println!("[probe] packet types seen: {types_seen:?}");

    // Exit code reflects the spike's decisive criterion: did the handshake land?
    if connected {
        println!("[probe] RESULT: PASS — pure-Rust ENet is wire-compatible with the server.");
        std::process::exit(0);
    } else {
        println!("[probe] RESULT: FAIL — no Connect event (check server is up/unlocked on this port).");
        std::process::exit(1);
    }
}
