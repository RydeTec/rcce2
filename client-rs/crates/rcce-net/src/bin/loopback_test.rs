//! Bisection harness: a `rusty_enet` server and client in one process, over
//! real UDP loopback. Proves the probe's connect/service/framing logic and the
//! `rusty_enet` API usage are correct — independent of the C `RCEnet.dll`.
//!
//! * PASS → our transport code is correct; any failure against the real server
//!   is a Rust↔C-ENet interop issue (version/protocol), not our bug.
//! * FAIL → our `rusty_enet` usage is wrong; fix here first.
//!
//!     cargo run -p rcce-net --bin loopback-test

use std::net::UdpSocket;
use std::time::{Duration, Instant};

use rusty_enet::{Event, Host, HostSettings, Packet};

use rcce_net::{frame, unframe, CHANNEL_RELIABLE, CONNECT_CHANNELS};

fn main() {
    let server_sock = UdpSocket::bind("127.0.0.1:0").expect("bind server");
    let server_addr = server_sock.local_addr().unwrap();
    let mut server = Host::new(
        server_sock,
        HostSettings {
            peer_limit: 4,
            ..Default::default()
        },
    )
    .expect("server host");

    let client_sock = UdpSocket::bind("127.0.0.1:0").expect("bind client");
    let mut client = Host::new(
        client_sock,
        HostSettings {
            peer_limit: 1,
            ..Default::default()
        },
    )
    .expect("client host");
    client
        .connect(server_addr, CONNECT_CHANNELS, 0)
        .expect("connect");
    println!("[loopback] client -> {server_addr}");

    const TEST_TYPE: u8 = 7;
    const TEST_PAYLOAD: &[u8] = &[0xDE, 0xAD, 0xBE, 0xEF];

    let mut client_connected = false;
    let mut server_saw_connect = false;
    let mut server_got_packet = false;

    let deadline = Instant::now() + Duration::from_secs(3);
    while Instant::now() < deadline && !server_got_packet {
        while let Ok(Some(event)) = server.service() {
            match event {
                Event::Connect { .. } => {
                    server_saw_connect = true;
                    println!("[loopback] server: CONNECT");
                }
                Event::Receive { packet, channel_id, .. } => {
                    if let Some((ty, payload)) = unframe(packet.data()) {
                        println!("[loopback] server: RECV ch={channel_id} type={ty} payload={payload:?}");
                        if ty == TEST_TYPE && payload == TEST_PAYLOAD {
                            server_got_packet = true;
                        }
                    }
                }
                Event::Disconnect { .. } => {}
            }
        }
        while let Ok(Some(event)) = client.service() {
            if let Event::Connect { peer, .. } = event {
                client_connected = true;
                println!("[loopback] client: CONNECT — sending framed test packet");
                let pkt = frame(TEST_TYPE, TEST_PAYLOAD);
                peer.send(CHANNEL_RELIABLE, &Packet::reliable(pkt.as_slice()))
                    .expect("send");
            }
        }
        std::thread::sleep(Duration::from_millis(5));
    }

    println!(
        "\n[loopback] client_connected={client_connected} server_saw_connect={server_saw_connect} server_got_packet={server_got_packet}"
    );
    if client_connected && server_saw_connect && server_got_packet {
        println!("[loopback] RESULT: PASS — transport code + framing are correct.");
        std::process::exit(0);
    } else {
        println!("[loopback] RESULT: FAIL — bug is in our rusty_enet usage, not C interop.");
        std::process::exit(1);
    }
}
