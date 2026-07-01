//! RCCE2 headless server (Rust) — thin tick loop over the `rcce_server` library.
//!
//! Boots a listening ENet host on the configured port and runs the single-
//! threaded tick loop (`Server.bb`'s `RCE_Update` → dispatch → world update).
//! Currently implements the login surface (Phase 1): a real client can connect,
//! create an account, and verify (log in). World state and gameplay are the
//! subsequent phases in `docs/rust-server/PLAN.md`.
//!
//! Headless by design — logs go to stdout (and, later, the same `Data\Logs`
//! files the Blitz server writes); there is no window. Run it in a Linux
//! container and point `bin/ClientRS.exe` / `bin/Client.exe` at it.
//!
//! Config: `Data/Server Data/Misc.dat` (port, account-creation flag, …) with
//! env overrides — `RCCE_DATA`, `RCCE_PORT`, `RCCE_PEERS`,
//! `RCCE_ALLOW_ACCOUNT_CREATION`.

use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use rcce_net::frame;
use rcce_server::config::ServerConfig;
use rcce_server::state::{ServerState, Target};
use rcce_server::packet_names;
use rcce_server_accounts::store::AccountStore;
use rcce_server_net::{EnetHostServer, PeerId, ServerEvent};

/// `RCE_StartHost(ServerPort, "", 5000, …)` peer cap.
const DEFAULT_MAX_PEERS: usize = 5000;
/// Tick budget: block this long for network events, then run a simulation tick.
const TICK_TIMEOUT_MS: u32 = 5;

fn main() {
    let config = ServerConfig::load();
    let max_peers = env_usize("RCCE_PEERS", DEFAULT_MAX_PEERS);

    log(&format!(
        "RCCE2 Rust server starting — data dir {:?}, port {}, account-creation {}, max peers {max_peers}",
        config.data_dir, config.port, config.allow_account_creation
    ));

    let accounts = AccountStore::load(config.accounts_path()).unwrap_or_else(|e| {
        log(&format!("Accounts load failed ({e}); starting empty"));
        AccountStore::default()
    });
    log(&format!("Loaded {} account(s)", accounts.len()));

    let catalog = rcce_server_core::ActorCatalog::load(
        config.data_dir.join("Server Data").join("Actors.dat"),
    );
    log(&format!("Loaded {} actor template(s)", catalog.len()));

    let port = config.port;
    let mut host = match EnetHostServer::bind(port, max_peers) {
        Some(h) => h,
        None => {
            log(&format!(
                "** Could not open port {port} — server shut down ** (port in use, or ENet init failed)"
            ));
            std::process::exit(1);
        }
    };

    let mut state = ServerState::new(config, accounts, catalog);
    log(&format!("Loaded {} content script(s)", state.scripts.len()));
    install_shutdown_handler();
    log(&format!("Listening on UDP {port}. Waiting for clients… (Ctrl-C / SIGTERM to stop)"));

    /// Movement-relay cadence (positions broadcast to same-area peers).
    const POS_RELAY_INTERVAL: Duration = Duration::from_millis(100);
    let mut last_pos_relay = Instant::now();

    while !SHUTDOWN.load(std::sync::atomic::Ordering::Relaxed) {
        for event in host.poll(TICK_TIMEOUT_MS) {
            handle_event(&host, &mut state, event);
        }

        // Live-world broadcast: introduce online players to their same-area
        // peers (P_NewActor). Idempotent — only genuinely new introductions.
        for (target, rtype, rbody) in state.collect_world_broadcasts() {
            send_reply(&host, PeerId(target), rtype, &rbody);
        }

        // Advance the in-game clock (drives the time-read BVMs).
        state.tick_clock();

        // Drive async content scripts (dialog/quests) + emit their packets.
        for out in state.pump_scripts() {
            if let Target::Peer(p) = out.target {
                if std::env::var_os("RCCE_LOGSCRIPT").is_some() {
                    let sub = out.payload.first().copied().unwrap_or(0) as char;
                    log(&format!(
                        "SCRIPT-SEND peer {p}  {} ({})  '{}'  {} byte(s)",
                        packet_names::name(out.msg_type), out.msg_type, sub, out.payload.len()
                    ));
                }
                send_reply(&host, PeerId(p), out.msg_type, &out.payload);
            }
        }

        // KickPlayer: disconnect peers a script asked to kick (after the
        // P_KickedPlayer packet above is flushed).
        let kicks = state.take_pending_kicks();
        if !kicks.is_empty() {
            host.flush();
            for p in kicks {
                host.disconnect(PeerId(p));
                log(&format!("KICK peer {p} (script KickPlayer)"));
            }
        }

        // NPC AI: creatures that were attacked strike back.
        let ai_now = state.now_ms();
        for out in state.collect_npc_attacks(ai_now) {
            // All NPC-attack packets are peer-targeted.
            if let Target::Peer(p) = out.target {
                send_reply(&host, PeerId(p), out.msg_type, &out.payload);
            }
        }

        // Expire timed attribute buffs (potion effects) whose duration elapsed.
        for out in state.check_effects() {
            if let Target::Peer(p) = out.target {
                send_reply(&host, PeerId(p), out.msg_type, &out.payload);
            }
        }

        // Portal traversal: players who walked into a linked portal warp zones.
        for out in state.check_portals() {
            if let Target::Peer(p) = out.target {
                send_reply(&host, PeerId(p), out.msg_type, &out.payload);
            }
        }

        // Movement relay: same-area players' positions, throttled. NPC chase
        // movement runs on the same cadence (its step size is per-call) and
        // broadcasts the moved NPCs' positions to same-area players.
        if last_pos_relay.elapsed() >= POS_RELAY_INTERVAL {
            // Aggro scan first, so a newly-engaged NPC chases this same tick.
            state.run_npc_aggro();
            for out in state.collect_npc_movement() {
                if let Target::Peer(p) = out.target {
                    send_reply(&host, PeerId(p), out.msg_type, &out.payload);
                }
            }
            for out in state.collect_npc_wander() {
                if let Target::Peer(p) = out.target {
                    send_reply(&host, PeerId(p), out.msg_type, &out.payload);
                }
            }
            for out in state.collect_npc_patrol() {
                if let Target::Peer(p) = out.target {
                    send_reply(&host, PeerId(p), out.msg_type, &out.payload);
                }
            }
            for out in state.collect_npc_pet_follow() {
                if let Target::Peer(p) = out.target {
                    send_reply(&host, PeerId(p), out.msg_type, &out.payload);
                }
            }
            // Mount glue: ridden NPCs snap to their rider's position (after the AI
            // collectors, which skip ridden NPCs).
            for out in state.collect_mount_glue() {
                if let Target::Peer(p) = out.target {
                    send_reply(&host, PeerId(p), out.msg_type, &out.payload);
                }
            }
            // Run-stamina drain (owner-only energy updates).
            for out in state.collect_energy_drain() {
                if let Target::Peer(p) = out.target {
                    send_reply(&host, PeerId(p), out.msg_type, &out.payload);
                }
            }
            // Proximity triggers (fire content scripts on zone entry).
            state.run_triggers();
            // Underwater breath drain + drowning.
            for out in state.collect_breath() {
                if let Target::Peer(p) = out.target {
                    send_reply(&host, PeerId(p), out.msg_type, &out.payload);
                }
            }
            for (target, rtype, rbody) in state.collect_position_broadcasts() {
                send_reply(&host, PeerId(target), rtype, &rbody);
            }
            last_pos_relay = Instant::now();
        }

        // Periodically flush in-memory account mutations (combat XP, etc.) so a
        // container stop loses at most the save interval.
        if state.maybe_persist() {
            log("Flushed accounts to disk");
        }

        host.flush();
    }

    // Graceful shutdown (SIGTERM/SIGINT): persist any in-memory account state so
    // a container stop / redeploy loses nothing, then exit cleanly.
    log("Shutting down — flushing accounts…");
    state.maybe_persist_force();
    host.flush();
    log("Shutdown complete.");
}

/// Set on SIGTERM/SIGINT; the tick loop checks it each iteration and exits
/// gracefully (final account flush) rather than being killed mid-tick.
static SHUTDOWN: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

#[cfg(unix)]
fn install_shutdown_handler() {
    // SAFETY: the handler only stores into a `static AtomicBool` — an
    // async-signal-safe operation. Registered for SIGTERM (container stop) and
    // SIGINT (Ctrl-C).
    extern "C" fn on_signal(_sig: libc::c_int) {
        SHUTDOWN.store(true, std::sync::atomic::Ordering::Relaxed);
    }
    // Cast through a fn POINTER (not the fn item) to avoid the fn-to-int lint.
    let handler = on_signal as extern "C" fn(libc::c_int) as libc::sighandler_t;
    unsafe {
        libc::signal(libc::SIGTERM, handler);
        libc::signal(libc::SIGINT, handler);
    }
}

#[cfg(not(unix))]
fn install_shutdown_handler() {
    // Non-Unix (not the production target): rely on default Ctrl-C handling.
}

fn handle_event(host: &EnetHostServer, state: &mut ServerState, event: ServerEvent) {
    match event {
        ServerEvent::Connect(peer) => log(&format!(
            "CONNECT  {peer:?}  ({} peer(s) now connected)",
            host.peer_count()
        )),
        ServerEvent::Disconnect(peer) => {
            for (target, rtype, rbody) in state.on_disconnect(peer.0) {
                send_reply(host, PeerId(target), rtype, &rbody);
            }
            log(&format!(
                "DISCONNECT {peer:?}  ({} peer(s) remain, {} online)",
                host.peer_count(),
                state.world.online_count()
            ));
        }
        ServerEvent::Receive { peer, channel, data } => {
            // Each ENet packet is one framed message `[type:u8][payload]`.
            let Some((msg_type, payload)) = rcce_net::unframe(&data) else {
                log(&format!("RECEIVE  {peer:?}  ch{channel}  empty/malformed — dropped"));
                return;
            };
            log(&format!(
                "RECEIVE  {peer:?}  {} ({})  {} payload byte(s){}",
                packet_names::name(msg_type),
                msg_type,
                payload.len(),
                if ServerState::is_handled(msg_type) { "" } else { "  [unhandled]" }
            ));
            for out in state.dispatch(peer.0, msg_type, payload) {
                let target = match out.target {
                    Target::Sender => peer,
                    Target::Peer(p) => PeerId(p),
                };
                send_reply(host, target, out.msg_type, &out.payload);
            }
        }
    }
}

/// Frame `[type][payload]` and send reliably (account/login replies are always
/// reliable, matching `RCE_Send(..., True)`).
fn send_reply(host: &EnetHostServer, peer: PeerId, msg_type: u8, payload: &[u8]) {
    if !host.send(peer, 0, &frame(msg_type, payload), true) {
        log(&format!("send_reply: peer {peer:?} gone, reply dropped"));
    }
}

fn env_usize(key: &str, default: usize) -> usize {
    std::env::var(key).ok().and_then(|v| v.parse().ok()).unwrap_or(default)
}

/// Minimal timestamped stdout log. A later phase routes this to the `Data\Logs`
/// files the Blitz server writes (atomic-write parity).
fn log(msg: &str) {
    let secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    println!("[{secs}] {msg}");
}
