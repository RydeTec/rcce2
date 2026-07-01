# RCCE2 Headless Rust Server (`server-rs`)

A headless, GUI-less reimplementation of the RealmCrafter Community Edition game
server (`bin/Server.exe`) in Rust — a **drop-in, Linux-container-deployable**
authoritative server that speaks the exact ENet wire protocol the shipped clients
(`bin/Client.exe`, `bin/ClientRS.exe`) use. It reads the same on-disk project
data (`data/`) as the Blitz server and aims for behavioral parity with it.

Parity status, design notes, and the cycle log live in
[`docs/rust-server/`](../docs/rust-server/) — start with
[`PARITY.md`](../docs/rust-server/PARITY.md) and
[`STATE.md`](../docs/rust-server/STATE.md).

## Build & test

```sh
cd server-rs
cargo build --release            # → target/release/rcce-server
cargo test --workspace           # full suite (unit + real-ENet e2e)
cargo clippy --workspace --all-targets -- -D warnings
```

Or, from the repo root, build all the Rust apps (client + server) at once:

```sh
./compile.sh -r                  # → bin/ClientRS + bin/ServerRS  (compile.bat -r on Windows)
./compile.sh -e -t -r            # only the Rust apps (skip the Blitz engine + tools)
```

CI gates the server on **ubuntu-latest** (the deploy target) on every PR — build,
the full test suite, and clippy `-D warnings` (see `.github/workflows/ci.yml`).

## Run

```sh
# From the repo root, with the project data/ alongside:
RCCE_DATA=./data ./server-rs/target/release/rcce-server
```

Logs go to stdout (timestamped); there is no window. The server boots an ENet
host on the configured UDP port and runs the authoritative tick loop. Point a
client at it (`RCCE_HOST`/`RCCE_PORT` for `bin/ClientRS.exe`, or the in-game
server list for `bin/Client.exe`).

### Configuration

Defaults come from `data/Server Data/Misc.dat`; env vars override:

| Env var | Default | Meaning |
|---|---|---|
| `RCCE_DATA` | `data` (resolved near the binary/cwd) | Project data directory |
| `RCCE_PORT` | `25000` (from `Misc.dat`) | Authoritative UDP game port |
| `RCCE_PEERS` | `5000` | Max concurrent connections |
| `RCCE_ALLOW_ACCOUNT_CREATION` | from `Misc.dat` | Allow `P_CreateAccount` |

### Graceful shutdown

The server installs SIGTERM/SIGINT handlers (the signals a container runtime
sends on stop/redeploy). On either, the tick loop finishes its current iteration,
**flushes all in-memory account state to disk**, and exits cleanly — no data loss
on shutdown. (Periodic autosave also bounds loss during normal operation.)

## Deploy (Docker)

The image is multi-stage (build → `debian:bookworm-slim`), runs as a non-root
user, and exposes the UDP game port. **Build context is the repo root** (the
server workspace path-depends on the sibling `client-rs` crates):

```sh
docker build -f server-rs/Dockerfile -t rcce-server .
docker run --rm -p 25000:25000/udp -v "$(pwd)/data:/data" rcce-server
```

> The server **writes** into the data tree (`Accounts.dat`, character saves,
> script files). For an isolated test run, mount a **copy** of `data/` rather
> than the source tree.

## Workspace layout

| Crate | Role |
|---|---|
| `rcce-server` | The binary: packet dispatch, world/tick loop, BVM host, NPC AI |
| `rcce-server-core` | Domain model: areas, characters, combat, factions, catalogs |
| `rcce-server-accounts` | Password hashing, flat-file account store, login throttle |
| `rcce-script` | RSL (RealmCrafter scripting) lexer/parser/interpreter |
| `rcce-server-net` | ENet host wrapper (bind/poll/send) |

Shared wire/format crates (`enet-sys`, `rcce-net`, `rcce-data`) are path-deps
from the sibling `client-rs/` workspace, so the client and server cannot desync
their view of the wire and on-disk formats.
