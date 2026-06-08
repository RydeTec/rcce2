//! Verifies the P_FetchCharacter pipeline live: logs in (which now requests the
//! character sheet on connection #1) and prints the parsed stats, inventory,
//! and spells.
//!
//!   cargo run -p rcce-client --bin fetch-test --release -- [host] [port]

use enet_sys::EnetTransport;
use rcce_net::Transport;

use rcce_client::login::{login, Credentials};

fn main() {
    let mut args = std::env::args().skip(1);
    let host = args.next().unwrap_or_else(|| "127.0.0.1".to_string());
    let port: u16 = args.next().and_then(|s| s.parse().ok()).unwrap_or(25000);

    let mut t = EnetTransport::new();
    let outcome = login(&mut t, &host, port, &Credentials {
        username: "rustbot".into(),
        password: "rustpass".into(),
        email: "rust@bot.com".into(),
    })
    .expect("login");

    println!("[fetch] in world, RuntimeID={}", outcome.runtime_id);

    let Some(s) = outcome.sheet else {
        eprintln!("[fetch] RESULT: no character sheet returned (server didn't answer P_FetchCharacter)");
        t.disconnect(outcome.peer);
        std::process::exit(1);
    };

    println!(
        "[fetch] gold={} rep={} level={} xp={} faction={} attrs={} done={}",
        s.gold, s.reputation, s.level, s.xp, s.home_faction, s.attributes.len(), s.done
    );
    if let Some((v, m)) = s.attributes.first() {
        println!("[fetch] attribute[0] (Health) = {v}/{m}");
    }
    println!("[fetch] inventory: {} item(s)", s.inventory.len());
    for it in &s.inventory {
        println!("  slot {:>2}  item #{:<5} x{:<4} hp {}", it.slot, it.item_id, it.amount, it.health);
    }
    println!("[fetch] spells: {} known", s.spells.len());
    for sp in &s.spells {
        println!(
            "  #{:<4} L{:<2} {}{}",
            sp.id, sp.level, sp.name, if sp.memorised { "  [memorised]" } else { "" }
        );
    }

    t.disconnect(outcome.peer);
    println!("[fetch] RESULT: PASS — character sheet parsed (done={}).", s.done);
}
