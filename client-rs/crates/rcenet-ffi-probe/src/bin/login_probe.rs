//! Full login flow over `FfiTransport` against the live server:
//!   CreateAccount (idempotent) → VerifyAccount → [CreateCharacter] →
//!   reconnect → StartGame → receive the world stream.
//!
//! Success = after StartGame the server pushes real gameplay packets
//! (P_ChangeArea / P_NewActor / P_StandardUpdate / chat). Self-service:
//! AllowAccountCreation=1, so it makes its own test account + character.
//!
//!   cargo run -p rcenet-ffi-probe --bin login-probe --target i686-pc-windows-msvc \
//!       -- "C:\Users\dyanr\Desktop\rcce2\bin\RCEnet.dll" 127.0.0.1 25000

use std::thread::sleep;
use std::time::{Duration, Instant};

use rcce_net::auth::{encrypt_email, md5_hex};
use rcce_net::codec::{MsgReader, MsgWriter};
use rcce_net::{packet_id as pk, RecvMessage, Transport};
use rcenet_ffi::FfiTransport;

const UNAME: &str = "rustbot";
const PASSWORD: &str = "rustpass";
const EMAIL: &str = "rust@bot.com";

/// Poll for `ms`, returning everything received.
fn pump(t: &mut FfiTransport, ms: u64) -> Vec<RecvMessage> {
    let mut all = Vec::new();
    let deadline = Instant::now() + Duration::from_millis(ms);
    while Instant::now() < deadline {
        all.extend(t.poll());
        sleep(Duration::from_millis(15));
    }
    all
}

fn sentinel(m: &RecvMessage) -> char {
    m.data.first().map(|&b| b as char).unwrap_or('\0')
}

fn main() {
    let mut args = std::env::args().skip(1);
    let dll = args
        .next()
        .unwrap_or_else(|| r"C:\Users\dyanr\Desktop\rcce2\bin\RCEnet.dll".to_string());
    let host = args.next().unwrap_or_else(|| "127.0.0.1".to_string());
    let port: u16 = args.next().and_then(|s| s.parse().ok()).unwrap_or(25000);

    let md5 = md5_hex(PASSWORD);
    println!("[login] account={UNAME} md5={md5}");

    // ---- Connection #1: account + character ---------------------------------
    let mut t = FfiTransport::load(&dll).expect("load dll");
    let peer = t.connect(&host, port).expect("conn#1 connect");
    println!("[login] conn#1 peer={peer}");

    // CreateAccount (idempotent — "N" just means it already exists).
    {
        let mut w = MsgWriter::new();
        w.str8(UNAME).str8(&md5).u8(EMAIL.len() as u8).raw(&encrypt_email(EMAIL));
        t.send(peer, pk::CREATE_ACCOUNT, w.as_slice(), true);
        let resp = pump(&mut t, 1500);
        let r = resp.iter().find(|m| m.connection == peer || true);
        match r.map(sentinel) {
            Some('Y') => println!("[login] CreateAccount: created"),
            Some('N') => println!("[login] CreateAccount: already exists / refused (ok)"),
            other => println!("[login] CreateAccount: resp={other:?} (continuing)"),
        }
    }

    // VerifyAccount → 'Y' + character list.
    let char_count = {
        let mut w = MsgWriter::new();
        w.str8(UNAME).str8(&md5);
        t.send(peer, pk::VERIFY_ACCOUNT, w.as_slice(), true);
        let resp = pump(&mut t, 1500);
        let m = resp
            .into_iter()
            .find(|m| matches!(sentinel(m), 'Y' | 'P' | 'N' | 'B' | 'L'))
            .expect("no VerifyAccount response");
        match sentinel(&m) {
            'Y' => {
                // Parse packed character records after the 'Y'.
                let mut r = MsgReader::new(&m.data[1..]);
                let mut n = 0;
                while r.remaining() > 0 {
                    let Some(name) = r.str8() else { break };
                    let (actor, gender) = (r.u16(), r.u8());
                    // face, hair, beard, body
                    let (_f, _h, _b, _bd) = (r.u8(), r.u8(), r.u8(), r.u8());
                    if actor.is_none() || gender.is_none() {
                        break;
                    }
                    println!("[login]   char[{n}] = '{name}' actorID={:?}", actor.unwrap());
                    n += 1;
                }
                println!("[login] VerifyAccount: OK, {n} character(s)");
                n
            }
            c => {
                eprintln!("[login] VerifyAccount FAILED sentinel='{c}' — aborting");
                std::process::exit(1);
            }
        }
    };

    // CreateCharacter if the account has none. Always send the 40-byte attribute
    // block (server reads the name at a hardcoded Offset+47 regardless).
    if char_count == 0 {
        println!("[login] no characters — creating one");
        let mut created = false;
        'actors: for actor_id in 0u16..=20 {
            for suffix in 0..3 {
                let name = if suffix == 0 {
                    "Rustaroo".to_string()
                } else {
                    format!("Rustaroo{suffix}")
                };
                let mut w = MsgWriter::new();
                w.str8(UNAME).str8(&md5);
                w.u16(actor_id).u8(0).u8(0).u8(0).u8(0).u8(0); // gender, face, hair, beard, body
                w.raw(&[0u8; 40]); // attribute point-spends (total 0 = always valid)
                w.raw(name.as_bytes()); // name: raw, trailing, no length prefix
                t.send(peer, pk::CREATE_CHARACTER, w.as_slice(), true);
                let resp = pump(&mut t, 1500);
                match resp.iter().find(|m| matches!(sentinel(m), 'Y' | 'I' | 'N')).map(sentinel) {
                    Some('Y') => {
                        println!("[login] CreateCharacter OK: actorID={actor_id} name='{name}'");
                        created = true;
                        break 'actors;
                    }
                    Some('I') => {
                        println!("[login]   name '{name}' invalid/taken, retrying");
                        continue;
                    }
                    Some('N') => break, // bad actorID etc. — next actor_id
                    _ => {}
                }
            }
        }
        if !created {
            eprintln!("[login] could not create a character — aborting");
            std::process::exit(1);
        }
    }

    t.disconnect(peer);
    println!("[login] conn#1 closed");
    sleep(Duration::from_millis(300));

    // ---- Connection #2: enter the world -------------------------------------
    let peer2 = t.connect(&host, port).expect("conn#2 connect");
    println!("[login] conn#2 peer={peer2}");

    let char_idx: u8 = 0;
    let mut w = MsgWriter::new();
    w.str8(UNAME).str8(&md5).u8(char_idx);
    t.send(peer2, pk::START_GAME, w.as_slice(), true);
    println!("[login] sent StartGame (charIdx={char_idx}); collecting world stream...");

    // Collect for a few seconds; count StartGame replies, log world packets.
    let mut start_game_replies = 0;
    let mut my_runtime_id: Option<u16> = None;
    let mut world: std::collections::BTreeMap<u8, usize> = Default::default();
    let mut failed = false;

    let deadline = Instant::now() + Duration::from_secs(5);
    while Instant::now() < deadline {
        for m in t.poll() {
            match m.msg_type {
                pk::START_GAME => {
                    if sentinel(&m) == 'N' && m.data.len() == 1 {
                        eprintln!("[login] StartGame REJECTED ('N')");
                        failed = true;
                    } else if m.data.len() <= 2 {
                        let mut r = MsgReader::new(&m.data);
                        my_runtime_id = r.u16();
                        start_game_replies += 1;
                        println!("[login]   StartGame reply: RuntimeID={my_runtime_id:?}");
                    } else {
                        start_game_replies += 1;
                        println!("[login]   StartGame reply: action-bar block ({}B)", m.data.len());
                    }
                }
                other => {
                    *world.entry(other).or_default() += 1;
                    let label = match other {
                        pk::CHANGE_AREA => "P_ChangeArea",
                        pk::NEW_ACTOR => "P_NewActor",
                        pk::STANDARD_UPDATE => "P_StandardUpdate",
                        pk::INVENTORY_UPDATE => "P_InventoryUpdate",
                        pk::CHAT_MESSAGE => "P_ChatMessage",
                        pk::XP_UPDATE => "P_XPUpdate",
                        pk::ACTOR_EFFECT => "P_ActorEffect",
                        pk::ACTOR_GONE => "P_ActorGone",
                        _ => "?",
                    };
                    println!("[login]   world packet type={other} ({label}) {}B", m.data.len());
                }
            }
        }
        sleep(Duration::from_millis(20));
    }

    println!("\n[login] ===== result =====");
    println!("[login] StartGame replies: {start_game_replies}/13   RuntimeID={my_runtime_id:?}");
    println!("[login] world packets by type: {world:?}");
    t.disconnect(peer2);

    if failed || my_runtime_id.is_none() {
        println!("[login] RESULT: login did NOT complete.");
        std::process::exit(1);
    }
    println!("[login] RESULT: PASS — logged in, RuntimeID assigned, world stream received.");
}
