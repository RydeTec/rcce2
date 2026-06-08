//! Verifies the movement SEND path: client A walks (sends P_StandardUpdate),
//! client B observes. Two accounts, one process.
//!
//! SOLVED (was a SERVER bug, not a client bug). This now PASSES once the server
//! carries the FindActorInstanceFromRNID O(n)-fallback fix (see below).
//!
//! Root cause, proven by temporarily logging M\FromID server-side: the server
//! resolves a packet's sender with `FindActorInstanceFromRNID(M\FromID)` where
//! `M\FromID = RCE_GetMessageConnection() = iSender = (int)Event.peer` — a heap
//! POINTER (e.g. 269070368), STABLE per connection (a client's StartGame and its
//! movement carry the SAME value). But the O(1) `ActorByRNID` index requires
//! `RNID <= MaxRNID(5000)`: FindActorInstanceFromRNID (Actors.bb:265) returns
//! Null for RNID>5000, and StartGame's `If M\FromID<=MaxRNID` guard
//! (ServerNet.bb:2156) skips populating ActorByRNID for the pointer. So the
//! O(1) refactor is incompatible with the DLL's pointer iSender — client
//! movement was dropped for EVERY client (NPCs still move; they're server
//! driven). The client's send is a FAITHFUL port of ClientNet.bb:1795-1798
//! (payload order + UNRELIABLE channel-2).
//!
//! Fix (server, Actors.bb FindActorInstanceFromRNID): keep O(1) for small ids,
//! else O(n) scan of the unconditionally-set \RNID field. Result: PASS — B
//! observes A walk (dX ~79). Alt fix: RCEnet `iSender = peer->incomingPeerID`.
//!
//!   cargo run --release -p rcce-client --bin move-test -- [host] [port]

use std::thread::sleep;
use std::time::{Duration, Instant};

use enet_sys::EnetTransport;
use rcce_net::{packet_id as pk, Transport};

use rcce_client::login::{login, Credentials};
use rcce_client::net::movement_packet;
use rcce_client::world::World;

fn creds(user: &str) -> Credentials {
    Credentials {
        username: user.to_string(),
        password: "rustpass".to_string(),
        email: "rust@bot.com".to_string(),
    }
}

/// Pump a transport into its world for `ms`.
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

    // Client A.
    let mut ta = EnetTransport::new();
    let a = login(&mut ta, &host, port, &creds("rustbot")).expect("A login");
    let mut wa = World { my_runtime_id: a.runtime_id, ..Default::default() };
    for m in &a.world_packets {
        wa.apply(m);
    }
    settle(&mut ta, &mut wa, 800);
    println!(
        "[move] A rnid={} pos=({:.1}, {:.1}, {:.1})",
        a.runtime_id, wa.me_x, wa.me_y, wa.me_z
    );

    // Client B (separate account) — observer.
    let mut tb = EnetTransport::new();
    let b = login(&mut tb, &host, port, &creds("rustbot2")).expect("B login");
    let mut wb = World { my_runtime_id: b.runtime_id, ..Default::default() };
    for m in &b.world_packets {
        wb.apply(m);
    }
    settle(&mut tb, &mut wb, 1200); // let B receive A's P_NewActor
    let a_rnid = a.runtime_id;
    let b_start = wb.actors.get(&a_rnid).map(|x| (x.x, x.z));
    let dump = |w: &World, label: &str| {
        let mut v: Vec<_> = w.actors.values().collect();
        v.sort_by_key(|x| x.runtime_id);
        for act in v {
            println!(
                "[move]   {label}: rnid {} '{}' pos=({:.1},{:.1})",
                act.runtime_id, act.name, act.x, act.z
            );
        }
    };
    println!(
        "[move] B rnid={} sees {} actor(s); A(rnid {a_rnid}) start = {:?}",
        b.runtime_id,
        wb.actors.len(),
        b_start
    );
    dump(&wb, "B start");

    // A walks in +X, sending movement each tick; B keeps observing.
    let (mut ax, az, ay) = (wa.me_x, wa.me_z, wa.me_y);
    let start_ax = ax;
    // Steps under the server's 2.0-unit per-packet clamp floor are always
    // accepted; larger steps get rejected (held) when packets bunch up.
    for _ in 0..40 {
        ax += 1.6;
        let p = movement_packet(ax + 15.0, az, ay, ax, az, true, false);
        // Match the real client (ClientNet.bb:1798): P_StandardUpdate is sent
        // UNRELIABLE (no trailing True → channel 2). Do NOT locally set wa.me_x:
        // the server echoes the player's own authoritative position back.
        ta.send(a.peer, pk::STANDARD_UPDATE, &p, false);
        for m in ta.poll() {
            wa.apply(&m);
        }
        sleep(Duration::from_millis(140));
        for m in ta.poll() {
            wa.apply(&m);
        }
        for m in tb.poll() {
            wb.apply(&m);
        }
    }
    settle(&mut ta, &mut wa, 400);
    settle(&mut tb, &mut wb, 600);
    println!(
        "[move] server-authoritative A position (from A's own echo): me_x={:.1} (claimed up to {:.1})",
        wa.me_x, ax
    );

    let b_end = wb.actors.get(&a_rnid).map(|x| (x.x, x.z));
    println!("[move] A claimed-walk X {:.1} -> {:.1}", start_ax, ax);
    dump(&wb, "B end  ");
    println!("[move] B sees A end = {:?}", b_end);

    ta.disconnect(a.peer);
    tb.disconnect(b.peer);

    match (b_start, b_end) {
        (Some((sx, _)), Some((ex, _))) => {
            let dx = ex - sx;
            println!("[move] B observed A move dX = {:.1}", dx);
            if dx.abs() > 5.0 {
                println!("[move] RESULT: PASS — the server accepted A's movement and broadcast it to B.");
            } else {
                eprintln!("[move] RESULT: FAIL — A did not visibly move to B.");
                std::process::exit(1);
            }
        }
        _ => {
            eprintln!("[move] RESULT: FAIL — B never saw actor A (rnid {a_rnid}).");
            std::process::exit(1);
        }
    }
}
