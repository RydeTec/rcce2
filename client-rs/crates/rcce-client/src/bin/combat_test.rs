//! Verifies the combat path: A walks to the nearest NPC and attacks it
//! (P_AttackActor), then checks the target took damage (its Health, mirrored
//! from P_StatUpdate, dropped) and/or a hit was broadcast (combat_events).
//! Relies on the gameplay-attribution fix (PR #462).
//!
//!   cargo run -p rcce-client --bin combat-test --release -- [host] [port]

use std::thread::sleep;
use std::time::{Duration, Instant};

use enet_sys::EnetTransport;
use rcce_net::{packet_id as pk, Transport};

use rcce_client::login::{login, Credentials};
use rcce_client::net::movement_packet;
use rcce_client::world::World;

fn pump(t: &mut EnetTransport, w: &mut World, ms: u64) {
    let end = Instant::now() + Duration::from_millis(ms);
    while Instant::now() < end {
        for m in t.poll() {
            w.apply(&m);
        }
        sleep(Duration::from_millis(20));
    }
}

fn nearest(w: &World) -> Option<u16> {
    let (mx, mz) = (w.me_x, w.me_z);
    w.actors
        .values()
        .filter(|a| a.alive)
        .min_by(|a, b| {
            let da = (a.x - mx).powi(2) + (a.z - mz).powi(2);
            let db = (b.x - mx).powi(2) + (b.z - mz).powi(2);
            da.total_cmp(&db)
        })
        .map(|a| a.runtime_id)
}

fn main() {
    let mut args = std::env::args().skip(1);
    let host = args.next().unwrap_or_else(|| "127.0.0.1".to_string());
    let port: u16 = args.next().and_then(|s| s.parse().ok()).unwrap_or(25000);

    let mut t = EnetTransport::new();
    let o = login(&mut t, &host, port, &Credentials {
        username: "rustbot".into(),
        password: "rustpass".into(),
        email: "rust@bot.com".into(),
    })
    .expect("login");
    let mut w = World { my_runtime_id: o.runtime_id, ..Default::default() };
    for m in &o.world_packets { w.apply(m); }
    pump(&mut t, &mut w, 800);

    let Some(tgt) = nearest(&w) else {
        eprintln!("[combat] no actors to target");
        std::process::exit(1);
    };
    let tname = w.actors.get(&tgt).map(|a| a.name.clone()).unwrap_or_default();
    let hp0 = w.actors.get(&tgt).map(|a| a.health).unwrap_or(0);
    let hpmax = w.actors.get(&tgt).map(|a| a.health_max).unwrap_or(0);
    println!("[combat] target rnid {tgt} '{tname}' HP {hp0}/{hpmax}; me=({:.0},{:.0})", w.me_x, w.me_z);

    // Walk to the target (chase its live position) for up to ~16s.
    let deadline = Instant::now() + Duration::from_secs(16);
    loop {
        let Some(a) = w.actors.get(&tgt) else { break };
        let (tx, tz) = (a.x, a.z);
        let (dx, dz) = (tx - w.me_x, tz - w.me_z);
        let dist = (dx * dx + dz * dz).sqrt();
        if dist < 4.0 || Instant::now() >= deadline {
            println!("[combat] reached target (dist {dist:.1})");
            break;
        }
        let p = movement_packet(tx, tz, w.me_y, w.me_x, w.me_z, true, false);
        t.send(o.peer, pk::STANDARD_UPDATE, &p, false);
        pump(&mut t, &mut w, 140);
    }

    // Attack for ~4s.
    println!("[combat] attacking rnid {tgt} ...");
    let atk_end = Instant::now() + Duration::from_secs(4);
    while Instant::now() < atk_end {
        t.send(o.peer, pk::ATTACK_ACTOR, &tgt.to_le_bytes(), true);
        pump(&mut t, &mut w, 250);
    }
    pump(&mut t, &mut w, 600);

    let hp1 = w.actors.get(&tgt).map(|a| a.health).unwrap_or(0);
    let dead = w.actors.get(&tgt).map(|a| !a.alive).unwrap_or(true);
    let hits = w.combat_events.iter().filter(|e| e.target == tgt).count();
    println!("[combat] target HP {hp0} -> {hp1}; dead={dead}; hits seen={hits}; combat_events={}", w.combat_events.len());
    t.disconnect(o.peer);

    if hp1 < hp0 || dead || hits > 0 {
        println!("[combat] RESULT: PASS — attack landed (HP dropped / hit / death).");
    } else {
        eprintln!("[combat] RESULT: FAIL — no damage/hit observed (may be out of range or unarmed no-op).");
        std::process::exit(1);
    }
}
