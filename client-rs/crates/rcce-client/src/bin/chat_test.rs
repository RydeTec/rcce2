//! Verifies the chat SEND path: client A sends a P_ChatMessage, client B (a
//! second account in the same zone) observes it. Uses the same send the window
//! uses (raw text bytes, reliable). Relies on the gameplay-attribution fix
//! (PR #462) — before it, the server dropped the chat like it dropped movement.
//!
//!   cargo run -p rcce-client --bin chat-test --release -- [host] [port]

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
    for m in &a.world_packets { wa.apply(m); }
    settle(&mut ta, &mut wa, 600);

    let mut tb = EnetTransport::new();
    let b = login(&mut tb, &host, port, &creds("rustbot2")).expect("B login");
    let mut wb = World { my_runtime_id: b.runtime_id, ..Default::default() };
    for m in &b.world_packets { wb.apply(m); }
    settle(&mut tb, &mut wb, 1000);
    let before = wb.chat.len();
    println!("[chat] A rnid={} B rnid={}; B chat lines so far: {before}", a.runtime_id, b.runtime_id);

    let msg = "hello from the rust client";
    println!("[chat] A sends: {msg:?}");
    ta.send(a.peer, pk::CHAT_MESSAGE, msg.as_bytes(), true);
    for _ in 0..3 { ta.poll(); sleep(Duration::from_millis(60)); }

    settle(&mut tb, &mut wb, 1500);
    println!("[chat] B chat after: {} line(s)", wb.chat.len());
    for line in wb.chat.iter() {
        println!("[chat]   B saw> {}", line.0);
    }

    ta.disconnect(a.peer);
    tb.disconnect(b.peer);

    let got = wb.chat.iter().any(|l| l.0.contains("hello from the rust client"));
    if got {
        println!("[chat] RESULT: PASS — B received A's chat message.");
    } else {
        eprintln!("[chat] RESULT: FAIL — B did not receive A's message.");
        std::process::exit(1);
    }
}
