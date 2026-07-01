//! Standalone smoke client — connects to a **running** RCCE2 Rust server (e.g.
//! the headless Linux container) over real ENet/UDP and drives the full playable
//! entry path: create account → log in → create character → enter world →
//! receive the live world's NPC introductions → attack an NPC. Exits 0 only if
//! every step succeeds.
//!
//! Usage (from the repo root, with the project `data/` for the actor catalog):
//!   RCCE_DATA=./data cargo run -p rcce-server --example smoke_client -- 127.0.0.1 25000
//!
//! This is the "connect to the container and play" proof the Blitz client would
//! perform, minus the GUI: it speaks the exact wire protocol `bin/ClientRS.exe`
//! speaks, against an out-of-process server.

use std::time::{Duration, Instant};

use enet_sys::EnetTransport;
use rcce_net::{RecvMessage, Transport};

const P_CREATE_ACCOUNT: u8 = 1;
const P_VERIFY_ACCOUNT: u8 = 2;
const P_FETCH_CHARACTER: u8 = 3;
const P_CREATE_CHARACTER: u8 = 4;
const P_NEW_ACTOR: u8 = 11;
const P_START_GAME: u8 = 12;
const P_ATTACK_ACTOR: u8 = 18;
const MD5_HELLO: &str = "5d41402abc4b2a76b9719d911017c592";

fn field(b: &[u8]) -> Vec<u8> {
    let mut v = vec![b.len() as u8];
    v.extend_from_slice(b);
    v
}

fn client_encrypt_email(plain: &[u8]) -> Vec<u8> {
    plain.iter().rev().map(|b| b.wrapping_sub(26)).collect()
}

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
        std::thread::sleep(Duration::from_millis(5));
    }
    None
}

fn fail(msg: &str) -> ! {
    eprintln!("SMOKE FAIL: {msg}");
    std::process::exit(1);
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let host = args.get(1).cloned().unwrap_or_else(|| "127.0.0.1".to_string());
    let port: u16 = args.get(2).and_then(|s| s.parse().ok()).unwrap_or(25000);

    // Actor catalog (read-only) for a valid playable race id + its start area.
    let data_dir = std::path::PathBuf::from(
        std::env::var("RCCE_DATA").unwrap_or_else(|_| "data".to_string()),
    );
    let catalog = rcce_server_core::ActorCatalog::load(data_dir.join("Server Data/Actors.dat"));
    let Some((actor_id, start_area)) = catalog
        .templates
        .values()
        .find(|t| t.playable && rcce_server_core::area::Area::load(&data_dir, &t.start_area).is_some())
        .map(|t| (t.id, t.start_area.clone()))
    else {
        fail("no playable race with a loadable start area in the catalog");
    };
    let start_area_has_npcs = rcce_server_core::area::Area::load(&data_dir, &start_area)
        .map(|a| a.spawns.iter().any(|s| s.actor_id >= 0 && s.max > 0))
        .unwrap_or(false);

    println!("[smoke] connecting to {host}:{port} over ENet/UDP…");
    let mut client = EnetTransport::new();
    let dest = match client.connect(&host, port) {
        Ok(d) => d,
        Err(e) => fail(&format!("ENet connect failed: {e}")),
    };

    // A unique-ish account so re-runs don't collide in a persisted store.
    let user = format!("smoke{}", std::process::id());

    // 1) Create account.
    let mut create = field(user.as_bytes());
    create.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    create.extend_from_slice(&field(&client_encrypt_email(b"s@x.com")));
    client.send(dest, P_CREATE_ACCOUNT, &create, true);
    match recv_until(&mut client, Duration::from_secs(5), |m| m.msg_type == P_CREATE_ACCOUNT) {
        Some(m) if m.data == b"Y" => println!("[smoke] ✓ account created"),
        Some(m) => fail(&format!("create-account rejected: {:?}", m.data)),
        None => fail("no create-account reply (server not reachable?)"),
    }

    // 2) Log in.
    let mut verify = field(user.as_bytes());
    verify.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    client.send(dest, P_VERIFY_ACCOUNT, &verify, true);
    match recv_until(&mut client, Duration::from_secs(5), |m| m.msg_type == P_VERIFY_ACCOUNT) {
        Some(m) if m.data == b"Y" => println!("[smoke] ✓ logged in"),
        Some(m) => fail(&format!("login rejected: {:?}", m.data)),
        None => fail("no login reply"),
    }

    // 3) Create character.
    let mut cc = field(user.as_bytes());
    cc.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    cc.extend_from_slice(&actor_id.to_le_bytes());
    cc.extend_from_slice(&[0u8; 5]); // gender/face/hair/beard/body
    cc.extend_from_slice(&[0u8; 40]); // attribute points
    // Unique per-process name — character names are globally unique on the server.
    let char_name = format!("Smk{}", std::process::id());
    cc.extend_from_slice(char_name.as_bytes());
    client.send(dest, P_CREATE_CHARACTER, &cc, true);
    match recv_until(&mut client, Duration::from_secs(5), |m| m.msg_type == P_CREATE_CHARACTER) {
        Some(m) if m.data == b"Y" => println!("[smoke] ✓ character created"),
        Some(m) => fail(&format!("create-character rejected: {:?}", m.data)),
        None => fail("no create-character reply"),
    }

    // 4) Fetch the character list.
    let mut fc = field(user.as_bytes());
    fc.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    fc.push(0);
    client.send(dest, P_FETCH_CHARACTER, &fc, true);
    match recv_until(&mut client, Duration::from_secs(5), |m| m.msg_type == P_FETCH_CHARACTER) {
        Some(m) if m.data != b"N" => println!("[smoke] ✓ character fetched"),
        Some(_) => fail("character slot reported empty after creation"),
        None => fail("no fetch-character reply"),
    }

    // 5) Enter the world + receive the live world.
    let mut sg = field(user.as_bytes());
    sg.extend_from_slice(&field(MD5_HELLO.as_bytes()));
    sg.push(0);
    client.send(dest, P_START_GAME, &sg, true);
    let mut runtime_id: Option<u16> = None;
    let mut npc_rid: Option<u16> = None;
    let start = Instant::now();
    while start.elapsed() < Duration::from_secs(8) {
        for m in client.poll() {
            if m.msg_type == P_START_GAME && m.data.len() == 2 {
                runtime_id = Some(u16::from_le_bytes([m.data[0], m.data[1]]));
            } else if m.msg_type == P_NEW_ACTOR && m.data.len() >= 6 && npc_rid.is_none() {
                npc_rid = Some(u16::from_le_bytes([m.data[4], m.data[5]]));
            }
        }
        if runtime_id.is_some() && (npc_rid.is_some() || !start_area_has_npcs) {
            break;
        }
        std::thread::sleep(Duration::from_millis(5));
    }
    match runtime_id {
        Some(rid) => println!("[smoke] ✓ entered world as runtime id {rid}"),
        None => fail("never received the P_StartGame runtime id — did not enter the world"),
    }
    if start_area_has_npcs {
        match npc_rid {
            Some(rid) => println!("[smoke] ✓ live world streamed NPC (runtime id {rid})"),
            None => fail("start area has NPCs but none were streamed to the client"),
        }
    }

    // 6) Attack an NPC (resend past the combat-delay gate).
    if let Some(target) = npc_rid {
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
                std::thread::sleep(Duration::from_millis(5));
            }
        }
        if hit {
            println!("[smoke] ✓ attacked NPC {target} and received combat feedback");
        } else {
            fail("attack produced no combat feedback");
        }
    }

    client.disconnect_immediate();
    println!("[smoke] ALL STEPS PASSED — played against {host}:{port}");
}
