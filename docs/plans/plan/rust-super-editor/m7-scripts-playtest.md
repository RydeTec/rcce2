# M7 — Scripts and isolated playtest

## Identity

- **Canonical tasks**: `68, 69, 70, 71, 72, 73, 74`
- **Depends on**: M3 project identities; write/promotion features also depend on M2
- **Status**: Planned

## Outcome

RSL authoring is backed by the same Rust script semantics used by the server, and playtest runs in disposable runtime state. Test activity cannot mutate source projects, production databases or undeclared external resources.

## Work packages

| Packet | Task | Deliverable | Acceptance evidence |
|---|---:|---|---|
| `M7-SCRIPT-API` | 68 | Token/AST/symbol/call/diagnostic APIs exposed from `rcce-script` without editor forks | Parser, symbol, call, diagnostic and version tests run in server and editor workspaces |
| `M7-WORKSPACE` | 69 | Lossless script documents plus browse/edit/search/definition/reference/BVM-doc/template workspace | Round trips retain text conventions; fixture navigation and BVM/template evidence pass |
| `M7-HARNESS` | 70 | Capability-confined, process-isolated untrusted-script harness | Host filesystem/network/database denial and source immutability survive malicious, failed and crashed tests |
| `M7-TRANSPORT` | 71 | Server host-transport contract retaining ENet as its first backend | Fake/native interoperability plus startup/readiness/shutdown tests; no editor dependency on private server internals |
| `M7-PLAYTEST` | 72 | Launch, observe and stop Rust client/server sessions | Repeatable smoke scenario produces structured session results and clean teardown |
| `M7-EVIDENCE` | 73 | Logs, screenshots, runtime diagnostics and artifact bundle | Evidence identifies exact binaries, project fingerprint, commands and outcomes |
| `M7-PROMOTION` | 74 | Explicit reviewed promotion from sandbox outputs to source | Default is discard; promotion is diffed, validated, commanded and recoverable |

## Isolation contract

- The untrusted-script harness copies/materializes only an allowed project subset into a unique disposable root and exposes a separate bounded scratch capability.
- Host filesystem access outside those capabilities, outbound network, real database access, process spawning, and undeclared secrets are denied by default; database behavior uses a fake adapter.
- Script execution has explicit time, memory/output and child-process limits plus malicious-script denial tests.
- Full client/server playtest is a distinct mode. It receives only separately declared loopback/runtime capabilities and never inherits broader script-test access.
- Runtime-generated accounts, logs, caches and databases are redirected outside the source tree.
- Server readiness is a structured signal, not a fixed sleep.
- Stop is graceful then bounded-forceful; both paths record process and artifact cleanup.
- Network listeners default to loopback and ephemeral ports.
- Promotion is a separate command transaction and never a side effect of stopping playtest.

`M7-WORKSPACE` is itself a small packet family: lossless RSL document persistence; static analysis/language services; unified-search/thread registration; BVM documentation; and templates. `M7-HARNESS`, `M7-TRANSPORT`, and `M7-PLAYTEST` remain separate review units and cannot be accepted from one end-to-end demo.

## Primary implementation surfaces

- `server-rs/crates/rcce-script/` — reusable analysis/runtime contracts.
- `server-rs/crates/rcce-server-net/` — owned local startup/transport seam where required.
- `client-rs/crates/rcce-net/` — protocol-compatible local connection support.
- `editor-rs/crates/rcce-playtest/` — harness/orchestration, with no UI dependency.
- `editor-rs/crates/rcce-editor/` — presentation of controls and evidence only.

## Verification

- Script-analysis parity tests use the same corpus against server and editor consumers.
- The unchanged host-transport contract suite runs against fake and native ENet backends before playtest depends on it.
- Sandbox tests hash source trees before/after successful, failed and killed sessions and prove filesystem escape, outbound network and real-database attempts are denied.
- Process tests cover port conflict, server crash, client crash, timeout and orphan cleanup.
- A representative create-character/enter-world scenario records structured evidence.
- Promotion tests prove only the reviewed diff reaches command/storage persistence.

## Exit gate

The editor can author and diagnose supported scripts using shared Rust semantics, run an isolated Rust client/server playtest, and prove the source project remains unchanged unless a separate promotion command is accepted.

## Non-goals

- Embedding the authoritative server loop inside the editor process.
- Connecting playtest to a production account database by default.
- Treating logs or screenshots alone as gameplay/protocol parity.
