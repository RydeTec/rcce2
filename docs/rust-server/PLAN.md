# Rust Server Port — Parity Plan

Goal: a **headless, Linux-container-deployable Rust server** at full feature parity with the BlitzForge `bin/Server.exe`, wire-compatible with the **unmodified** clients (`bin/Client.exe` and `bin/ClientRS.exe`) reading the **unchanged** project `data/`. The port is additive under `server-rs/`; no `.bb` or `data/` file is modified.

**Ultimate acceptance (the user's words):** run the server headless in a Linux container in the cloud and connect to it via the clients and play the game as if the Blitz server were running.

The Blitz `Server.exe` is a GUI (Windows BlitzMax-style) app whose window only shows logs/lock controls; the **game logic is headless-able**. The port drops the GUI entirely (a log to stdout + structured log files replaces the window) and keeps the authoritative simulation.

This plan mirrors the precedent at [`docs/rust-client/PLAN.md`](../rust-client/PLAN.md), which produced `bin/ClientRS.exe`. It is **parity-driven**: [`ACCEPTANCE.md`](ACCEPTANCE.md) is the contract; "parity reached" = every non-`DEFERRED` criterion is `DONE` with test/live evidence (a real client logs in, enters the world, and plays).

## Reuse — the head start from the client port

The client port already built three crates that are **server/client shared** and reused here via path dependency (no copy, no fork):

| Crate | Path | What the server reuses |
|---|---|---|
| `enet-sys` | `client-rs/crates/enet-sys` | The vendored RCCE2 **ENet fork** C source (8-byte fork header), compiled static + cross-platform (`vendor/unix.c`). Same FFI used in **host/listen mode** (`enet_host_create(&addr, max_peers, …)`) instead of client/connect mode. This is the single biggest de-risk: the wire transport is byte-identical to what the clients already talk to. |
| `rcce-net` | `client-rs/crates/rcce-net` | Wire **codec** (`Writer`/`Reader`: `str8`/`str16`/big-endian ints/LE floats), `frame`/`unframe`, auth/MD5 helpers. Server encodes the mirror of what the client decodes. |
| `rcce-data` | `client-rs/crates/rcce-data` | On-disk **format parsers**: `.dat` catalogs (items/actors/spells/areas/attributes/interface/language), B3D, area files, saves. The server loads the **same project files** the client renders. |

New crates live under `server-rs/crates/`. Shared crates stay in `client-rs/` so a single edit can't silently desync client and server views of the wire/formats.

## Architecture decision (2026-06-16, inferred → to confirm by live test)

- **Separate `server-rs/` workspace** with path deps to the three shared `client-rs/crates/*`. Mirrors `client-rs/`, keeps the server build self-contained for the container image, and avoids restructuring the shipped client workspace.
- **Single-threaded authoritative tick loop** (matches `Server.bb`): pump ENet → dispatch inbound packets (`ServerNet` parity) → update one zone per tick (weather/spawns/AI/portals/projectiles) → flush. Blitz is single-threaded; replicating that ordering is the safest path to behavioral parity. Concurrency is a later optimization, not a parity requirement.
- **Default port 25000** (`Server.bb:264-265`, falls back to 25000 when the config reads 0). Configurable via the same `Server Data` config the Blitz server reads, plus a `RCCE_PORT` env override for containers.
- **No MySQL initially.** The Blitz server's `MySQL.bb` include is commented out (`Server.bb:36`); persistence is the flat-file `Data\Accounts\` + `Data\Characters\` + `Data\Server Data\` tree. The port matches that file-backed persistence first; SQL is `DEFERRED`.

## Crate layout (target)

```
server-rs/
├── Cargo.toml                      # workspace; path deps → ../client-rs/crates/{enet-sys,rcce-net,rcce-data}
└── crates/
    ├── rcce-server-net/            # ENet HOST wrapper (listen, N peers, poll→events, send) + packet framing
    ├── rcce-server-core/           # game state: actors, areas, inventories, items, spells, projectiles, environment
    ├── rcce-server-accounts/       # accounts/auth/character persistence (flat-file, PasswordHash parity)
    ├── rcce-script/                # BVM bytecode engine (briskvm) + BVM_* command library + opcode dispatch
    └── server/                     # the `rcce-server` binary: config load → host → tick loop → graceful shutdown
```

## Phases

Ordered so each unblocks the next and front-loads the login→enter-world→move→see-others path (the spine of "can I connect and play"). Each phase commits independently. After every Rust edit: `cargo build --release` (zero warnings) + `cargo test` (green). The **end-to-end gate** for the milestone phases is a real client (`bin/ClientRS.exe` headless, or `bin/Client.exe`) completing the path against the Rust server.

### Phase 0 — Foundation: bootable listening host  → SRV-BOOT, NET-HOST
Stand up `server-rs/` workspace + `rcce-server-net` + `server` binary. Bind the ENet host on the configured port, service events, log Connect/Receive/Disconnect with payload sizes. **Proves the transport accepts a real client's connection** — the highest-risk layer, cleared first.
- **Verify:** `cargo build` clean; binary boots and binds 25000; point `bin/ClientRS.exe` at it and confirm the server logs a CONNECT + inbound bytes (the client's first handshake). *(This cycle delivers the compiling/bootable skeleton; the live-CONNECT observation is the Phase-0 exit gate.)*

### Phase 1 — Accounts, login, character select  → ACC-1..N, LOGIN-1..N
Decode the menu-socket protocol: `P_CreateAccount`, `P_VerifyAccount`, `P_FetchCharacter`, `P_CreateCharacter`, `P_DeleteCharacter`, `P_ChangePassword`. Flat-file account/character persistence with `PasswordHash.bb` parity (salted SHA-256 v1) + atomic writes (`SafeWriteOpen`/`Commit` parity). Dataset streaming (`P_FetchActors`/`P_FetchItems`/`P_FetchUpdateFiles`).
- **Verify:** a client creates an account, logs in, sees/creates/deletes a character — live, against the Rust server.

### Phase 2 — Enter world: zones, actors, the standard update  → WORLD-1..N
Load areas (`ServerAreas` parity) and actor catalog (`Actors`), spawn the player actor, `P_StartGame`, `P_NewActor`/`P_ActorGone`, `P_StandardUpdate` (position/anim broadcast), `P_ChangeArea` warp. The per-tick zone update (spawns, weather, portals).
- **Verify:** a client enters the world, sees terrain + its own actor, and the server broadcasts position; two clients see each other move.

### Phase 3 — Interaction: chat, inventory, combat, spells  → PLAY-1..N
`P_ChatMessage`, `P_InventoryUpdate` (equip/drop/pickup), `P_AttackActor`/`P_ActorDead`/`P_FloatingNumber` (combat + damage formula parity), `P_SpellUpdate`/`P_Projectile` (casting/cooldowns), `P_RightClick`/`P_Examine`/`P_Trade`/`P_Dialog`, stat/XP/gold/quest updates.
- **Verify:** live — chat round-trips, items equip, an attack kills a stag and drops loot, a spell projectile lands.

### Phase 4 — Scripting engine (BVM)  → SCRIPT-1..N
Port `briskvm` (the bytecode VM), the `BVM_*` command library (`ScriptingCommands.bb`), and the opcode dispatch (`RC_Standard_Invoker.bb`) — **with the privilege-gating model intact** (see root `CLAUDE.md` §"Privilege gating"). Run the shipped `data/` content scripts (NPC dialog, quests, spell templates, in-game commands).
- **Verify:** the shipped content scripts (`In-game Commands`, marriage, spell templates) execute with identical observable effects; the privileged-script allowlist (`Privileged Scripts.dat`) is honored.

### Phase 5 — Updates server + container packaging  → DEPLOY-1..N
`UpdatesServer.bb` lock/unlock + file-update channel parity. Dockerfile (Linux, no display), config via env/mounted `data/`, graceful shutdown (save environment + dropped items + log everyone off, matching `Server.bb` shutdown). Document the cloud-run.
- **Verify:** the container runs headless on Linux; a remote client connects over the network and plays a full session.

## Locked invariants (inherited from the client port — do NOT relitigate)

| Invariant | Value | Source |
|---|---|---|
| Byte order | **wire little-endian ints AND floats** (verified: `rcce-net/src/codec.rs:1-10` — `RCE_StrFromInt$`'s prepend loop cancels back to native LE; the working client decodes LE; an earlier "big-endian" note was wrong); file LE; **wire str = 1-byte len, file str = 4-byte len** | `rcce-net` codec (ground truth — the live client uses it) |
| Transport | RCCE2 ENet **fork** (8-byte header), vendored C in `enet-sys`; server uses **host mode** | `enet-sys/src/lib.rs` |
| Default port | 25000 | `Server.bb:264-265` |
| Cooldowns | keyed by spell ID 0-999 | client `PLAN.md` |
| Persistence | flat-file (`Data/Accounts`, `Data/Characters`, `Data/Server Data`); MySQL DEFERRED | `Server.bb:36` (MySQL include commented) |

## Hard-parity risks to watch (catalogue, refine per phase)

1. **The BVM engine** (~7k lines: `briskvm` + `ScriptingCommands` + `RC_Standard_Invoker`) is the largest and most security-sensitive surface. Privilege gating must port 1:1 or it becomes a remote-exploit regression. Treat as its own phase with adversarial review.
2. **Soft-fail discipline** — every wire-driven value is attacker-controlled; the Rust server must never panic on bad input (the Blitz server `WriteLog + drop + continue`s). Rust's type system helps (`Option`/`Result`), but the *policy* (drop the packet, keep the world alive) must be explicit. No `.unwrap()` on wire data.
3. **Iterator-during-iteration** Blitz hazards become Rust borrow-checker constraints — generally *easier* in Rust, but the deferred-kill-list semantics (`DeferKillActor`) must be preserved for ordering parity.
4. **Float sanitisation** at the wire/script boundary (`ClampWorldCoord`/`ClampSaneFloat`) — NaN/Inf clamps must port or a single bad float poisons spatial code on every receiving client.
5. **Generator-staleness** — the server doesn't regenerate `docs/protocol` or `docs/bvm-reference`; those stay sourced from the `.bb`. The Rust server must match the `.bb` wire/BVM definitions, which those docs already describe.
