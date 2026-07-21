# RCCE Rust editor workspace

This additive Rust 1.85 workspace reserves the production crate boundaries for
the RCCE super editor. At `SE-M1-P01` it is intentionally a compiling skeleton:
it opens no project, exposes no mutation API, selects no GUI framework, and is
not connected to the shipping build or packaging scripts.

## Packet baseline

At accepted M0 repository head
`3d1003742a2ed9b5cb5f94658532d6540ec18c8e`, `editor-rs` did not exist. The
pre-implementation command

```text
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo metadata --manifest-path editor-rs/Cargo.toml --no-deps --format-version 1
```

exited `101` with:

```text
error: manifest path `editor-rs/Cargo.toml` does not exist
```

The observed toolchain was `rustc 1.85.0 (4d91de4e4 2025-02-17)` and
`cargo 1.85.0 (d73d2caf9 2024-12-31)` on `x86_64-unknown-linux-gnu`.

## Crate responsibilities

| Crate | Reserved responsibility at later packets | Current behavior |
|---|---|---|
| `rcce-editor` | Desktop composition; the only production crate allowed to acquire a GUI framework | Headless placeholder binary only |
| `rcce-editor-core` | GUI-independent session, query, selection, and diagnostic orchestration | Empty library boundary |
| `rcce-project` | Root-confined project model, inventory, identity, and legacy document interpretation | Empty library boundary |
| `rcce-validation` | Pure diagnostics over project evidence | Empty library boundary |
| `rcce-storage` | Format-agnostic command storage after write-capable milestones authorize it | Empty library boundary; no persistence API |
| `rcce-migrate` | Explicit conversion planning and execution after authorization | Empty library boundary; no migration behavior |
| `rcce-admin` | Permissioned external administration behind separately reviewed adapters | Empty library boundary; no administration behavior |
| `rcce-project-cli` | Sole installed project and migration CLI composition root after service boundaries are authorized | Zero-dependency safe help/version/read-only placeholder |

## Dependency direction

The workspace manifests reserve one-way composition edges:

```text
rcce-editor -> rcce-editor-core -> rcce-validation -> rcce-project
                              \-> rcce-project

rcce-migrate -> rcce-project + rcce-storage

rcce-project-cli (no service dependencies in this packet)
rcce-admin       (no dependencies in this packet)
rcce-storage     (no dependencies in this packet)
```

`rcce-project` is the only editor crate permitted to establish a path
dependency on the existing `client-rs/crates/rcce-data` shared-format crate.
This skeleton does not add that edge because it has no consensus-proven read to
consume yet. Shared logic must not be copied into this workspace. Renderer,
network, and script-analysis seams likewise require the packet and
cross-consumer evidence named by ADR 0009.

Headless crates cannot depend on GUI frameworks. Authoritative server mutable
state cannot be linked into the editor process. Dependency flow is from
composition toward capabilities; project/model crates never depend on desktop,
CLI, migration, administration, or storage composition.

## Read-only boundary

The current production sources implement no project-relative write, delete,
rename, create, repair, conversion, database, subprocess-launch, or persistence
API. The contract suite includes a common-token smoke check for accidental
introduction of those operations; that heuristic is regression evidence, not
an AST analysis, syscall sandbox, or complete security proof. Later packets may
add root-confined reads. Write-capable APIs remain an M2-or-later concern and
require command, journal, recovery, and authorization contracts.

The installed binary name `rcce-project` is reserved by the
`rcce-project-cli` package. In this packet it accepts only no arguments,
`--help`/`-h`, and `--version`/`-V`; every other argument fails without opening
or changing anything. It has no direct dependency on project, validation,
storage, migration, or administration services. Those composition edges arrive
only with the later packet that authorizes and exercises each boundary.

## Verification

From the repository root:

```text
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo fmt --manifest-path editor-rs/Cargo.toml --all -- --check
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo test --manifest-path editor-rs/Cargo.toml --workspace --locked
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo clippy --manifest-path editor-rs/Cargo.toml --workspace --all-targets --locked -- -D warnings
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo build --manifest-path editor-rs/Cargo.toml --workspace --locked
```

The `skeleton_contract` test parses Cargo's metadata JSON to freeze the
eight-package census, all normal and target-specific dependency edges, renamed,
optional and build dependencies, publication/features, and exact target/source
topology. It also verifies the CLI binary name and forbids GUI dependencies
outside the desktop crate. A separately named heuristic smoke test searches
production sources for common mutation/process tokens; it is not presented as
a complete capability or security boundary.
