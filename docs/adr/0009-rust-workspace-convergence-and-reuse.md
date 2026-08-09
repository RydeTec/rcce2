# ADR 0009 — Rust workspace convergence and crate reuse

- **Status:** Accepted
- **Date:** 2026-07-20

## Context and evidence

The repository has two productive Rust workspaces. `client-rs` owns
`rcce-data`, `rcce-net`, `rcce-render`, `enet-sys`, and the client. `server-rs`
owns server net/core/accounts/script/runtime crates and already consumes
`rcce-data`, `rcce-net`, and `enet-sys` through path dependencies. Both declare
Rust 1.85 and compatible lint policies, but have separate manifests and
lockfiles. Server-core also contains overlapping project-format interpretations.

Moving everything before editor development would interrupt active parity work;
copying crates into an editor workspace would create immediate semantic drift.

## Decision

Create the editor as an additive `editor-rs` workspace that consumes existing
crates through path dependencies. It does not copy, fork, or vendor shared RCCE
logic. Initial logical responsibilities are:

- reuse/evolve `rcce-data` for shared legacy codecs and provenance;
- reuse/evolve `rcce-render` for client/editor rendering seams;
- reuse `rcce-net` only for explicit playtest/protocol needs;
- reuse/evolve `rcce-script` for headless RSL analysis;
- compare and converge server-core format interpretations through shared
  contract fixtures rather than choosing one implementation by location.

Editor-specific crates remain under `editor-rs`: desktop composition,
headless editor core, project model/capabilities, validation, format-agnostic
storage, migration library, permissioned administration, and CLI composition.
Only the desktop composition crate may depend on a UI framework.

A crate moves to a shared root or a root workspace only when at least two
applications consume it, duplicate interpretations have equivalence fixtures,
the move is an isolated reviewed migration, all consumers build/test/Clippy at
the exact moved revision, and rollback is a straightforward path reversal.

Every shared-crate change fans CI out across affected client, server, and editor
workspaces. Changes stay additive until consumers migrate explicitly. The
installed `rcce-project` executable is the sole project/migration CLI; migration
is a library, not a competing binary.

## Alternatives considered

1. Consolidate into one root workspace immediately. Rejected because directory,
   lockfile, feature, and CI churn would block capability evidence.
2. Copy shared crates into `editor-rs`. Rejected because bug fixes and format
   semantics would diverge.
3. Make the editor depend on server/client binary crates. Rejected because UI,
   authoritative runtime, and project services need explicit library seams.
4. Never converge workspaces. Rejected because duplicated format truth and
   repeated dependency resolution would remain permanent.

## Consequences

- The first editor increments are additive and can proceed without repository-
  wide moves.
- Shared-crate edits have a broader but truthful CI cost.
- Some temporary cross-workspace path dependencies remain.
- Convergence is evidence-driven and independently reversible.

## Invariants

- There is one implementation of any shared contract after convergence; no
  copied editor fork is accepted.
- UI dependencies do not enter headless project, validation, storage, migration,
  admin, format, renderer-core, network, or script-analysis crates.
- Authoritative server mutable state is not linked into the editor UI process.
- Shared format changes run semantic/golden fixtures for every consumer.
- Workspace moves do not combine behavioral rewrites with path relocation.
- Rust 1.85 remains the minimum until a separate toolchain-policy change.

## Verification and exit evidence

Before the additive editor workspace lands, record exact `cargo test`, strict
Clippy, and build baselines for both existing workspaces. Each shared dependency
change runs those gates plus editor gates and cross-consumer fixture tests. A
convergence move additionally proves lockfile resolution, feature unification,
target-specific dependencies, packaging scripts, compile entry points, and
reversible path changes on supported platforms. CI verifies that a shared-crate
edit cannot pass while an affected consumer is unbuilt.

## Dependencies

- [`client-rs/Cargo.toml`](../../client-rs/Cargo.toml)
- [`server-rs/Cargo.toml`](../../server-rs/Cargo.toml)
- [Rust client plan](../rust-client/PLAN.md)
- [Rust server plan](../rust-server/PLAN.md)
- Future Rust baseline packet `SE-M0-P03`

## Supersession and change process

The eventual root/shared workspace layout requires a new ADR with measured
benefit, migration order, consumer gates, and rollback. Ordinary additive path
dependency changes do not supersede this record.
