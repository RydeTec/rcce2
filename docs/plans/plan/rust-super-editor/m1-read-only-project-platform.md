# M1 — Read-only project platform

## Identity

- **Canonical tasks**: 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19
- **Status**: `Planned`
- **Depends on**: M0 accepted
- **Blocks**: every write-capable packet

## Outcome

The Rust editor can open a legacy project through a root-confined, provenance-aware model and display one production-quality, read-only Ledger slice whose counts and diagnostics are either consensus-proven or visibly provisional.

## Work packages

| Packet | Canonical tasks | Deliverable | Depends on |
|---|---:|---|---|
| `SE-M1-P01` Workspace skeleton | 9 | Additive `editor-rs` headless/UI crate skeleton | M0-P03, workspace ADR |
| `SE-M1-P02` Root capability | 10 | Cross-platform root resolver plus hostile path/link tests | M0-P06 |
| `SE-M1-P03` Project inventory | 11, 15 | Discovery, state classes, fingerprints, progress/cancellation | P01, P02 |
| `SE-M1-P04` Identity/provenance | 12, 13 | Typed identities, `RawLegacyString`, minimal `LegacyDocument` | P01 |
| `SE-M1-P05` Consensus fixtures | 14 | Client/server/editor sentinel, malformed, and non-UTF8 contract fixtures | P04 |
| `SE-M1-P06` UI/render spike | 16 | Disposable framework/compositing/accessibility/performance decision | P01, M0 budgets |
| `SE-M1-P07` Production shell | 17 | Five lenses over real read-only project queries | P03, P06 |
| `SE-M1-P08` First diagnostic | 18 | Dynamic reference index for one consensus-proven domain | P05, P07 |
| `SE-M1-P09` Interaction evidence | 19 | Screenshots and keyboard/UIA/Narrator/high-DPI smoke | P07, P08 |

## Exact crate/path targets

- `editor-rs/Cargo.toml`
- `editor-rs/crates/rcce-project/{Cargo.toml,src/...}`
- `editor-rs/crates/rcce-editor-core/{Cargo.toml,src/...}`
- `editor-rs/crates/rcce-validation/{Cargo.toml,src/...}`
- `editor-rs/crates/rcce-storage/` stub with no write API exposed yet
- `editor-rs/crates/rcce-editor/{Cargo.toml,src/...}`
- `editor-rs/spikes/ui-render/`
- `editor-rs/test-data/` may reference copied root corpus fixtures but never originals

## Interface contracts established

- `ProjectRoot`/root-capability type is required for all project-relative resolution.
- `ProjectSnapshot` owns inventory fingerprints and compatibility/status evidence.
- `RawLegacyString` exposes raw bytes and display text as distinct operations.
- Domain identities are non-interchangeable newtypes.
- `Diagnostic` has stable code, severity, source span/identity, evidence status, and no repair action in M1.

## Verification

- No project write syscalls are reachable from production M1 flows.
- Hostile path fixtures prove no outside-root reads.
- Existing Rust client/server suites run when shared crate APIs change.
- UI spike proves one device/queue, two viewports, picking, resizing, device loss, 1024 px layout, keyboard completion, UIA/Narrator, and budgets.
- A rendered count/diagnostic changes when the copied fixture changes and resets on reload; it is not seeded UI state.

## Exit gate

- All nine packets accepted.
- Default project opens on reference hardware within approved budgets.
- No diagnostic is presented as authoritative without consensus fixtures.
- The editor remains read-only and M2 write APIs are absent/disabled.

## Non-goals

- No persistence, repair button, import, project creation, or legacy-tool launch.
