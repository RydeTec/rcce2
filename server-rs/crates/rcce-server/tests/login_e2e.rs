//! End-to-end login test: a **real ENet client** (`enet-sys::EnetTransport`, the
//! exact transport `bin/ClientRS.exe` uses) connects to a live `EnetHostServer`
//! running the real `ServerState::dispatch`, creates an account, logs in, and
//! fails a wrong-password login — all over UDP loopback with the genuine RCCE2
//! ENet fork framing. This is the closest in-process proxy for "a client
//! connects and authenticates against the Rust server" without the GUI client.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};

use enet_sys::EnetTransport;
use rcce_net::{frame, RecvMessage, Transport};
use rcce_server::config::ServerConfig;
use rcce_server::state::{ServerState, Target};
use rcce_server_accounts::store::AccountStore;
use rcce_server_net::{EnetHostServer, PeerId, ServerEvent};

const P_CREATE_ACCOUNT: u8 = 1;
const P_VERIFY_ACCOUNT: u8 = 2;
const P_FETCH_CHARACTER: u8 = 3;
const P_CREATE_CHARACTER: u8 = 4;
const P_START_GAME: u8 = 12;
const P_CHANGE_PASSWORD: u8 = 6;
const MD5_HELLO: &str = "5d41402abc4b2a76b9719d911017c592";
/// md5("world") — the new password used by the change-password e2e test.
const MD5_WORLD: &str = "7d793037a0760186574b0282f2f435e7";

/// 1-byte-length-prefixed field, as the client packs username/password/email.
fn field(b: &[u8]) -> Vec<u8> {
    let mut v = vec![b.len() as u8];
    v.extend_from_slice(b);
    v
}

/// Encrypt an email the way the client does (server decrypts via reverse + +26,
/// so the client form is reverse + −26).
fn client_encrypt_email(plain: &[u8]) -> Vec<u8> {
    plain.iter().rev().map(|b| b.wrapping_sub(26)).collect()
}

/// Poll the client transport until one message arrives or `timeout` elapses.
fn recv_one(client: &mut EnetTransport, timeout: Duration) -> Option<RecvMessage> {
    let start = Instant::now();
    while start.elapsed() < timeout {
        if let Some(m) = client.poll().into_iter().next() {
            return Some(m);
        }
        thread::sleep(Duration::from_millis(5));
    }
    None
}

/// Drain client messages until one satisfies `pred` (returned) or `timeout`
/// elapses (None). Accumulates everything seen so a multi-packet burst (the
/// enter-world reply is ~15 packets) doesn't drop the one we want.
fn recv_until(
    client: &mut EnetTransport,
    timeout: Duration,
    mut pred: impl FnMut(&RecvMessage) -> bool,
) -> Option<RecvMessage> {
    let start = Instant::now();
    while start.elapsed() < timeout {
        for m in client.poll() {
            if pred(&m) {
                return Some(m);
            }
        }
        thread::sleep(Duration::from_millis(5));
    }
    None
}

#[test]
fn client_creates_account_and_logs_in() {
    // Bind the host in this thread (also runs the one-time enet_initialize),
    // trying a few ports so a busy one doesn't fail the test.
    let mut bound = None;
    for port in 27650u16..27670 {
        if let Some(h) = EnetHostServer::bind(port, 16) {
            bound = Some((h, port));
            break;
        }
    }
    let (host, port) = bound.expect("could not bind any test port 27650..27670");

    // Temp account store so we don't touch the real data/ tree.
    let mut store_path = std::env::temp_dir();
    store_path.push(format!("rcce_e2e_accounts_{port}.dat"));
    let _ = std::fs::remove_file(&store_path);
    let _ = std::fs::remove_file({
        let mut p = store_path.clone().into_os_string();
        p.push(".bak");
        std::path::PathBuf::from(p)
    });
    let accounts = AccountStore::load(&store_path).unwrap();

    let config = ServerConfig {
        port,
        allow_account_creation: true,
        max_account_chars: 4,
        start_gold: 0,
        start_reputation: 0,
        attribute_assignment: 0,
        data_dir: std::env::temp_dir(),
    };
    // Login e2e doesn't exercise character creation, so an empty catalog is fine.
    let mut state = ServerState::new(config, accounts, rcce_server_core::ActorCatalog::default());

    // Run the server tick loop on its own thread (host is Send by construction —
    // single-owner raw handles, never shared).
    let stop = Arc::new(AtomicBool::new(false));
    let stop_srv = stop.clone();
    let server = thread::spawn(move || {
        let mut host = host;
        loop {
            for ev in host.poll(5) {
                if let ServerEvent::Receive { peer, data, .. } = ev {
                    if let Some((t, payload)) = rcce_net::unframe(&data) {
                        for out in state.dispatch(peer.0, t, payload) {
                            // login e2e only triggers Sender-targeted replies.
                            host.send(peer, 0, &frame(out.msg_type, &out.payload), true);
                        }
                    }
                }
            }
            host.flush();
            if stop_srv.load(Ordering::Relaxed) {
                break;
            }
        }
    });

    // --- Real ENet client connects over loopback. -------------------------
    let mut client = EnetTransport::new();
    let dest = client
        .connect("127.0.0.1", port)
        .expect("ENet client failed to connect to the Rust server");

    // Create account "alice" / md5("hello") / "a@b.com".
    let mut create = Vec::new();
    create.extend_from_slice(&field(b"alice"));
    create.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    create.extend_from_slice(&field(&client_encrypt_email(b"a@b.com")));
    client.send(dest, P_CREATE_ACCOUNT, &create, true);
    let reply = recv_one(&mut client, Duration::from_secs(3)).expect("no P_CreateAccount reply");
    assert_eq!(reply.msg_type, P_CREATE_ACCOUNT);
    assert_eq!(reply.data, b"Y", "account creation should succeed");

    // Log in with the correct password → "Y" (+ empty char list).
    let mut verify = Vec::new();
    verify.extend_from_slice(&field(b"alice"));
    verify.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    client.send(dest, P_VERIFY_ACCOUNT, &verify, true);
    let reply = recv_one(&mut client, Duration::from_secs(3)).expect("no P_VerifyAccount reply");
    assert_eq!(reply.msg_type, P_VERIFY_ACCOUNT);
    assert_eq!(reply.data, b"Y", "login with correct password should succeed");

    // Wrong password → "P".
    let mut bad = Vec::new();
    bad.extend_from_slice(&field(b"alice"));
    bad.extend_from_slice(&field(b"ffffffffffffffffffffffffffffffff"));
    client.send(dest, P_VERIFY_ACCOUNT, &bad, true);
    let reply = recv_one(&mut client, Duration::from_secs(3)).expect("no wrong-password reply");
    assert_eq!(reply.data, b"P", "wrong password should be rejected with P");

    // Teardown.
    client.disconnect_immediate();
    stop.store(true, Ordering::Relaxed);
    server.join().expect("server thread panicked");
    let _ = std::fs::remove_file(&store_path);
}

/// The full playable-entry path over real ENet: a real client connects, creates
/// an account, logs in, **creates a character**, fetches its character list, and
/// **enters the world** — asserting the server replies with the `P_StartGame`
/// runtime-id packet over UDP loopback. This is the closest in-process proxy for
/// "log in and enter the world as if the Blitz server were running" without the
/// GUI client, exercising the real `dispatch` + world broadcasts + the actual
/// `Actors.dat` / area data.
#[test]
fn client_creates_character_and_enters_world_over_enet() {
    // Real project data (repo-root `data/`) so character creation + enter-world
    // hit the genuine Actors.dat / Areas, not a mock.
    let data_dir = std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../data");
    let catalog = rcce_server_core::ActorCatalog::load(data_dir.join("Server Data/Actors.dat"));
    // Need a playable race whose start area loads, else enter-world can't place us.
    let Some((actor_id, start_area)) = catalog
        .templates
        .values()
        .find(|t| {
            t.playable && rcce_server_core::area::Area::load(&data_dir, &t.start_area).is_some()
        })
        .map(|t| (t.id, t.start_area.clone()))
    else {
        eprintln!("skipping: no playable race with a loadable start area in data/");
        return;
    };
    // Does the start area spawn NPCs? If so, the live world should stream them
    // to the client as P_NewActor after it enters.
    let start_area_has_npcs = rcce_server_core::area::Area::load(&data_dir, &start_area)
        .map(|a| a.spawns.iter().any(|s| s.actor_id >= 0 && s.max > 0))
        .unwrap_or(false);

    let mut bound = None;
    for port in 27670u16..27700 {
        if let Some(h) = EnetHostServer::bind(port, 16) {
            bound = Some((h, port));
            break;
        }
    }
    let (host, port) = bound.expect("could not bind any test port 27670..27700");

    let mut store_path = std::env::temp_dir();
    store_path.push(format!("rcce_e2e_world_accounts_{port}.dat"));
    let _ = std::fs::remove_file(&store_path);
    let accounts = AccountStore::load(&store_path).unwrap();

    let config = ServerConfig {
        port,
        allow_account_creation: true,
        max_account_chars: 4,
        start_gold: 0,
        start_reputation: 0,
        attribute_assignment: 0,
        data_dir: data_dir.clone(),
    };
    let mut state = ServerState::new(config, accounts, catalog);

    // Full server tick loop: dispatch (Sender + Peer replies) + world broadcasts
    // + async scripts — exactly what `main.rs` runs.
    let stop = Arc::new(AtomicBool::new(false));
    let stop_srv = stop.clone();
    let server = thread::spawn(move || {
        let mut host = host;
        loop {
            for ev in host.poll(5) {
                if let ServerEvent::Receive { peer, data, .. } = ev {
                    if let Some((t, payload)) = rcce_net::unframe(&data) {
                        for out in state.dispatch(peer.0, t, payload) {
                            let target = match out.target {
                                Target::Sender => peer,
                                Target::Peer(p) => PeerId(p),
                            };
                            host.send(target, 0, &frame(out.msg_type, &out.payload), true);
                        }
                    }
                }
            }
            for (tp, mt, body) in state.collect_world_broadcasts() {
                host.send(PeerId(tp), 0, &frame(mt, &body), true);
            }
            for out in state.pump_scripts() {
                if let Target::Peer(p) = out.target {
                    host.send(PeerId(p), 0, &frame(out.msg_type, &out.payload), true);
                }
            }
            host.flush();
            if stop_srv.load(Ordering::Relaxed) {
                break;
            }
        }
    });

    let mut client = EnetTransport::new();
    let dest = client
        .connect("127.0.0.1", port)
        .expect("ENet client failed to connect to the Rust server");

    // Create account + log in.
    let mut create = Vec::new();
    create.extend_from_slice(&field(b"bob"));
    create.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    create.extend_from_slice(&field(&client_encrypt_email(b"bob@b.com")));
    client.send(dest, P_CREATE_ACCOUNT, &create, true);
    assert_eq!(
        recv_one(&mut client, Duration::from_secs(3)).expect("no create-account reply").data,
        b"Y"
    );

    let mut verify = Vec::new();
    verify.extend_from_slice(&field(b"bob"));
    verify.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    client.send(dest, P_VERIFY_ACCOUNT, &verify, true);
    assert_eq!(
        recv_one(&mut client, Duration::from_secs(3)).expect("no login reply").data,
        b"Y"
    );

    // Create a character: field(user)+field(md5)+u16 actor_id+5 appearance bytes
    // +40 attribute-point bytes + name.
    let mut cc = Vec::new();
    cc.extend_from_slice(&field(b"bob"));
    cc.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    cc.extend_from_slice(&actor_id.to_le_bytes());
    cc.extend_from_slice(&[0u8; 5]); // gender, face, hair, beard, body
    cc.extend_from_slice(&[0u8; 40]); // attribute points
    cc.extend_from_slice(b"Bob");
    client.send(dest, P_CREATE_CHARACTER, &cc, true);
    let cc_reply = recv_until(&mut client, Duration::from_secs(3), |m| m.msg_type == P_CREATE_CHARACTER)
        .expect("no create-character reply");
    assert_eq!(cc_reply.data, b"Y", "character creation should succeed over the wire");

    // Fetch the character list for slot 0 — proves it round-tripped to the store.
    let mut fc = Vec::new();
    fc.extend_from_slice(&field(b"bob"));
    fc.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    fc.push(0u8);
    client.send(dest, P_FETCH_CHARACTER, &fc, true);
    let fc_reply = recv_until(&mut client, Duration::from_secs(3), |m| m.msg_type == P_FETCH_CHARACTER)
        .expect("no fetch-character reply");
    assert_ne!(fc_reply.data, b"N", "the created character should be fetchable (not an empty slot)");

    // Enter the world (P_StartGame, slot 0). The server replies with a burst
    // including the 2-byte P_StartGame runtime-id packet — the "you're in the
    // world" signal the client transitions on.
    let mut sg = Vec::new();
    sg.extend_from_slice(&field(b"bob"));
    sg.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    sg.push(0u8);
    client.send(dest, P_START_GAME, &sg, true);
    // Drain everything the server sends for a few seconds (the enter-world burst
    // + the live world's NPC introductions arrive across several ticks). Collect
    // all of it so a single poll batch holding both doesn't drop either.
    const P_NEW_ACTOR: u8 = 11;
    let mut got_rid: Option<u16> = None;
    // First NPC runtime id seen (P_NewActor: [u32 serverArea][u16 runtimeId]…).
    let mut npc_rid: Option<u16> = None;
    let start = Instant::now();
    while start.elapsed() < Duration::from_secs(6) {
        for m in client.poll() {
            if m.msg_type == P_START_GAME && m.data.len() == 2 {
                got_rid = Some(u16::from_le_bytes([m.data[0], m.data[1]]));
            } else if m.msg_type == P_NEW_ACTOR && m.data.len() >= 6 && npc_rid.is_none() {
                npc_rid = Some(u16::from_le_bytes([m.data[4], m.data[5]]));
            }
        }
        // Stop early once we have everything we're waiting for.
        if got_rid.is_some() && (npc_rid.is_some() || !start_area_has_npcs) {
            break;
        }
        thread::sleep(Duration::from_millis(5));
    }
    let runtime_id =
        got_rid.expect("did not receive the P_StartGame runtime-id reply — never entered the world");
    assert!(runtime_id >= 1, "entered the world with a valid runtime id (got {runtime_id})");
    // The live world streams its actors to the client.
    if start_area_has_npcs {
        assert!(
            npc_rid.is_some(),
            "start area '{start_area}' has NPC spawns but the client received no P_NewActor — the live world isn't reaching the client"
        );
    }

    // Interactive gameplay: attack an NPC and confirm the server resolves combat
    // and sends the "H" hit-feedback back over the wire (P_AttackActor = 18). The
    // server's combat-delay gate (~1s) may reject the first swing, so resend
    // until one lands (real wall-clock advances past the gate).
    if let Some(target) = npc_rid {
        const P_ATTACK_ACTOR: u8 = 18;
        let mut hit = false;
        let start = Instant::now();
        while start.elapsed() < Duration::from_secs(6) && !hit {
            client.send(dest, P_ATTACK_ACTOR, &target.to_le_bytes(), true);
            let deadline = Instant::now() + Duration::from_millis(400);
            while Instant::now() < deadline && !hit {
                for m in client.poll() {
                    if m.msg_type == P_ATTACK_ACTOR && m.data.first() == Some(&b'H') {
                        hit = true;
                    }
                }
                thread::sleep(Duration::from_millis(5));
            }
        }
        assert!(
            hit,
            "attacking NPC {target} produced no 'H' combat feedback — combat isn't resolving over the wire"
        );
    }

    // Teardown.
    client.disconnect_immediate();
    stop.store(true, Ordering::Relaxed);
    server.join().expect("server thread panicked");
    let _ = std::fs::remove_file(&store_path);
}

/// `P_ChangePassword` over real ENet, end-to-end: a client enters the world
/// (establishing the live session the ownership check requires), changes its
/// password, then **reconnects and logs in with the NEW password** — the
/// integrated, over-the-wire proof that the Cycle-93 handler works against a
/// real client, not just `dispatch` unit calls.
#[test]
fn client_changes_password_over_enet() {
    let data_dir = std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../data");
    let catalog = rcce_server_core::ActorCatalog::load(data_dir.join("Server Data/Actors.dat"));
    let Some((actor_id, _)) = catalog
        .templates
        .values()
        .find(|t| t.playable && rcce_server_core::area::Area::load(&data_dir, &t.start_area).is_some())
        .map(|t| (t.id, t.start_area.clone()))
    else {
        eprintln!("skipping: no playable race with a loadable start area");
        return;
    };

    let mut bound = None;
    for port in 27700u16..27740 {
        if let Some(h) = EnetHostServer::bind(port, 16) {
            bound = Some((h, port));
            break;
        }
    }
    let (host, port) = bound.expect("could not bind any test port 27700..27740");

    let mut store_path = std::env::temp_dir();
    store_path.push(format!("rcce_e2e_changepw_accounts_{port}.dat"));
    let _ = std::fs::remove_file(&store_path);
    let accounts = AccountStore::load(&store_path).unwrap();
    let config = ServerConfig {
        port,
        allow_account_creation: true,
        max_account_chars: 4,
        start_gold: 0,
        start_reputation: 0,
        attribute_assignment: 0,
        data_dir: data_dir.clone(),
    };
    let mut state = ServerState::new(config, accounts, catalog);

    let stop = Arc::new(AtomicBool::new(false));
    let stop_srv = stop.clone();
    let server = thread::spawn(move || {
        let mut host = host;
        loop {
            for ev in host.poll(5) {
                if let ServerEvent::Receive { peer, data, .. } = ev {
                    if let Some((t, payload)) = rcce_net::unframe(&data) {
                        for out in state.dispatch(peer.0, t, payload) {
                            let target = match out.target {
                                Target::Sender => peer,
                                Target::Peer(p) => PeerId(p),
                            };
                            host.send(target, 0, &frame(out.msg_type, &out.payload), true);
                        }
                    }
                }
            }
            for (tp, mt, body) in state.collect_world_broadcasts() {
                host.send(PeerId(tp), 0, &frame(mt, &body), true);
            }
            for out in state.pump_scripts() {
                if let Target::Peer(p) = out.target {
                    host.send(PeerId(p), 0, &frame(out.msg_type, &out.payload), true);
                }
            }
            host.flush();
            if stop_srv.load(Ordering::Relaxed) {
                break;
            }
        }
    });

    // helper: a [str user][str pass] body
    let auth = |user: &[u8], pass: &str| {
        let mut v = field(user);
        v.extend_from_slice(&field(pass.as_bytes()));
        v
    };

    let mut client = EnetTransport::new();
    let dest = client.connect("127.0.0.1", port).expect("connect failed");

    // Create account + login.
    let mut create = auth(b"carol", MD5_HELLO);
    create.extend_from_slice(&field(&client_encrypt_email(b"c@c.com")));
    client.send(dest, P_CREATE_ACCOUNT, &create, true);
    assert_eq!(recv_one(&mut client, Duration::from_secs(3)).expect("no create reply").data, b"Y");
    client.send(dest, P_VERIFY_ACCOUNT, &auth(b"carol", MD5_HELLO), true);
    assert_eq!(recv_until(&mut client, Duration::from_secs(3), |m| m.msg_type == P_VERIFY_ACCOUNT).expect("no login").data[..1], *b"Y");

    // Create a character + enter the world (so the account holds a live session).
    let mut cc = auth(b"carol", MD5_HELLO);
    cc.extend_from_slice(&actor_id.to_le_bytes());
    cc.extend_from_slice(&[0u8; 5]);
    cc.extend_from_slice(&[0u8; 40]);
    cc.extend_from_slice(b"Carol");
    client.send(dest, P_CREATE_CHARACTER, &cc, true);
    assert_eq!(recv_until(&mut client, Duration::from_secs(3), |m| m.msg_type == P_CREATE_CHARACTER).expect("no cc reply").data, b"Y");

    let mut sg = auth(b"carol", MD5_HELLO);
    sg.push(0u8);
    client.send(dest, P_START_GAME, &sg, true);
    let entered = recv_until(&mut client, Duration::from_secs(5), |m| m.msg_type == P_START_GAME && m.data.len() == 2).is_some();
    assert!(entered, "must enter the world (establishes the session the ownership check needs)");

    // Change the password: [str user][str old][str new] → "Y".
    let mut cp = field(b"carol");
    cp.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    cp.extend_from_slice(&field(MD5_WORLD.as_bytes()));
    client.send(dest, P_CHANGE_PASSWORD, &cp, true);
    let cp_reply = recv_until(&mut client, Duration::from_secs(3), |m| m.msg_type == P_CHANGE_PASSWORD).expect("no change-password reply");
    assert_eq!(cp_reply.data, b"Y", "change-password succeeds for the logged-in owner with the correct old password");

    // Reconnect and prove the OLD password now fails and the NEW one works.
    client.disconnect_immediate();
    thread::sleep(Duration::from_millis(200));
    let mut c2 = EnetTransport::new();
    let dest2 = c2.connect("127.0.0.1", port).expect("reconnect failed");
    c2.send(dest2, P_VERIFY_ACCOUNT, &auth(b"carol", MD5_HELLO), true);
    assert_eq!(recv_until(&mut c2, Duration::from_secs(3), |m| m.msg_type == P_VERIFY_ACCOUNT).expect("no old-pw reply").data, b"P", "the OLD password no longer logs in");
    c2.send(dest2, P_VERIFY_ACCOUNT, &auth(b"carol", MD5_WORLD), true);
    assert_eq!(recv_until(&mut c2, Duration::from_secs(3), |m| m.msg_type == P_VERIFY_ACCOUNT).expect("no new-pw reply").data[..1], *b"Y", "the NEW password logs in");
    c2.disconnect_immediate();

    stop.store(true, Ordering::Relaxed);
    server.join().expect("server thread panicked");
    let _ = std::fs::remove_file(&store_path);
}
