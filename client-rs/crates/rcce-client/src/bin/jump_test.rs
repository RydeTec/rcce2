//! Verifies the P_Jump wire round-trip (MOVE-7/ANIM-7): client A sends a P_Jump
//! (empty payload — the server identifies the jumper by FromID), and client B (a
//! second account in the same zone) observes it land in `World.jumps[A_rid]` via
//! the `on_jump` handler. Like chat-test, this relies on the gameplay-attribution
//! fix (PR #462) so the server attributes A's packet and broadcasts the 2-byte
//! RuntimeID to B.
//!
//!   cargo run -p rcce-client --bin jump-test --release -- [host] [port]

use std::thread::sleep;
use std::time::{Duration, Instant};

use enet_sys::EnetTransport;
use rcce_net::{packet_id as pk, Transport};

use rcce_client::login::{login, Credentials};
use rcce_client::world::World;

fn creds(user: &str) -> Credentials {
    Credentials {
        username: user.to_string(),
        password: "rustpass".to_string(),
        email: "rust@bot.com".to_string(),
    }
}

fn settle(t: &mut EnetTransport, w: &mut World, ms: u64) {
    let end = Instant::now() + Duration::from_millis(ms);
    while Instant::now() < end {
        for m in t.poll() {
            w.apply(&m);
        }
        sleep(Duration::from_millis(20));
    }
}

fn main() {
    let mut args = std::env::args().skip(1);
    let host = args.next().unwrap_or_else(|| "127.0.0.1".to_string());
    let port: u16 = args.next().and_then(|s| s.parse().ok()).unwrap_or(25000);

    let mut ta = EnetTransport::new();
    let a = login(&mut ta, &host, port, &creds("rustbot")).expect("A login");
    let mut wa = World { my_runtime_id: a.runtime_id, ..Default::default() };
    for m in &a.world_packets {
        wa.apply(m);
    }
    settle(&mut ta, &mut wa, 600);

    let mut tb = EnetTransport::new();
    let b = login(&mut tb, &host, port, &creds("rustbot2")).expect("B login");
    let mut wb = World { my_runtime_id: b.runtime_id, ..Default::default() };
    for m in &b.world_packets {
        wb.apply(m);
    }
    settle(&mut tb, &mut wb, 1000);
    println!("[jump] A rnid={} B rnid={}", a.runtime_id, b.runtime_id);

    // A jumps: empty payload, reliable — exactly what the window sends on J.
    println!("[jump] A sends P_Jump");
    ta.send(a.peer, pk::JUMP, &[], true);
    for _ in 0..3 {
        ta.poll();
        sleep(Duration::from_millis(60));
    }

    settle(&mut tb, &mut wb, 1200);
    let got = wb.jumps.contains_key(&a.runtime_id);
    println!("[jump] B world.jumps keys: {:?}", wb.jumps.keys().collect::<Vec<_>>());

    ta.disconnect(a.peer);
    tb.disconnect(b.peer);

    if got {
        println!("[jump] RESULT: PASS — B received A's P_Jump (world.jumps populated).");
    } else {
        eprintln!("[jump] RESULT: FAIL — B did not register A's jump.");
        std::process::exit(1);
    }
}
