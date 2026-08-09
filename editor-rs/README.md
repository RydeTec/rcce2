# RCCE Rust editor workspace

This additive Rust 1.85 workspace contains the headless project foundation and
the continuously runnable Super Editor feedback MVP. The MVP opens an explicit
RCCE `data` directory, inventories it through the confined read-only project
root, and projects the observed files into the Records, World, Assets, Scripts,
and Vault lenses. It exposes no mutation API and does not select a final UI or
renderer architecture; the current desktop host is a replaceable feedback
surface over GUI-independent state.

The Records lens also projects the accepted actor/media consensus slice into a
real actor catalog. It shows stable actor IDs, legacy race display names, base
mesh references, physical media resolution, and evidence status. Missing
catalog/file diagnostics appear only when the consensus layer marks the slice
authoritative; provisional projects remain browsable without presenting those
diagnostics as proven facts.

The Assets lens derives a relationship view from that same accepted
actor/media slice. It groups only actor-referenced base-mesh IDs, preserves the
raw numeric mesh identities, and exposes actor backlinks plus evidence-qualified
media paths and states. On provisional projects the mesh IDs and backlinks
remain visible while catalog and physical-file conclusions are withheld. This
is not a complete asset catalog and does not establish global orphan status;
the exhaustive Assets file atlas remains available beside the relationship
view. A selected actor with a raw base-mesh reference can follow that observed
relationship into Assets, and each ordered actor backlink can return to the
exact actor in Records. These focusable routes clear stale filters and
incompatible selections; they do not add parsed meaning, integrity enforcement,
history, repair, or broader relationship-graph evidence.

The World lens projects the two legacy area directories into a paired-zone
atlas. Zone identity remains the observed `.dat` filename stem; each card shows
the visual and gameplay files, their exact observed sizes, and whether one half
is absent. These are inventory observations only: the feedback surface does
not parse zone contents, infer semantic parity, or expose create/save/repair
actions. The exhaustive World file atlas remains available beside the zone
view.

The Scripts lens uses each immediate `.rsl` file as an active source identity.
Recognized literal `Click_`, `Init_`, `Item_`, `Quest_`, and `Spell_` filename
prefixes define five families; every other source is grouped as `Other`.
Same-stem `.rcm` and `.rcscript` files appear as adjacent
inventory observations with their exact paths and sizes. The surface does not
parse source, assert that an adjunct was generated from the source, or expose
script editing; the exhaustive Scripts file atlas remains available beside the
catalog.

`RELOAD SNAPSHOT` re-inventories the currently accepted project through the
same confined read-only loader. The prior accepted project, root, lens, filter,
and focus remain visible while the replacement loads. Success swaps the full
projection atomically and retains raw file, actor, mesh, zone, and script focus
only when the identity still resolves; failure or a disconnected loader keeps
the prior accepted session visible and reports the failed refresh. Reload is
explicit rather than automatic: this surface does not watch the filesystem,
merge concurrent changes, repair data, or provide live synchronization.

On Windows, launch the latest feedback build from the repository root:

```powershell
.\scripts\run_super_editor_mvp.ps1
```

Pass `-Project C:\path\to\project` to open another project or its `data`
directory. The launcher pins Rust 1.85 through `rustup`, uses a dedicated build
cache, and defaults to a release build. The first build is slower; subsequent
launches are incremental.

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
| `rcce-editor` | Desktop composition; the only production crate allowed to acquire a GUI framework | Replaceable feedback-MVP host with five read-only lenses, safe atomic snapshot reload, focused actor/base-mesh cross-navigation, and paired-zone and script-relationship surfaces |
| `rcce-editor-core` | GUI-independent session, query, selection, and diagnostic orchestration | Real inventory, actor/media consensus and base-mesh backlinks, filename-derived zone pairs, and active-source script grouping with no write authority |
| `rcce-project` | Root-confined project model, inventory, identity, and legacy document interpretation | Explicit read-only `ProjectRoot`, validated `ProjectRelativePath`, bounded read/walk, and truthful assurance reporting |
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
P02 does not add that edge because it has no consensus-proven format read to
consume yet. The existing compatibility corpus scanner instead reuses
`rcce-project`'s root capability, typed paths, assurance selection, reads, and
walks rather than retaining a second filesystem implementation. Shared format
logic must not be copied into this workspace. Renderer, network, and
script-analysis seams likewise require the packet and cross-consumer evidence
named by ADR 0009.

Headless crates cannot depend on GUI frameworks. Authoritative server mutable
state cannot be linked into the editor process. Dependency flow is from
composition toward capabilities; project/model crates never depend on desktop,
CLI, migration, administration, or storage composition.

## Read-only root capability

`ProjectRoot::open_explicit` accepts an explicitly selected absolute directory
and retains an opened root identity. Callers construct a
`ProjectRelativePath`, which rejects traversal, host-absolute, device/UNC,
alternate-stream, reserved-name, embedded-separator, and trailing-dot/space
forms before filesystem access. `ProjectRoot::read` quarantines bytes until
post-read identity and integrity checks pass, and `ProjectRoot::walk` returns a
deterministic SHA-256 inventory under explicit file, byte, directory, depth,
path, and component ceilings. Both operations reject links, multiply linked
files, alias collisions, boundary crossings, and changed identities rather than
publishing speculative content.

The assurance tiers are explicit and independently queryable:

- `BaselineQuarantine` is available on the native Unix and Windows backends.
- `TransientRaceDetection` is available only where the opened filesystem has
  executable evidence for the stronger change-detection boundary. The current
  Linux backend advertises it only for the proved ext-family filesystem;
  Windows and unproved filesystems report it unavailable.
- `StrongNoRead` is unavailable: no backend claims that it can prevent every
  read during arbitrary concurrent namespace mutation.

Requesting an unavailable assurance fails before content access. This is
platform truth, not an emulated success. The compatibility corpus scanner uses
the same capability and assurance contract for its project and separately
selected canary roots.

The current production sources still implement no project-relative write,
delete, rename, create, repair, conversion, database, subprocess-launch, or
persistence API. The contract suite includes a common-token smoke check for
accidental introduction of those operations; that heuristic is regression
evidence, not an AST analysis, syscall sandbox, or complete security proof.
Write-capable APIs remain an M2-or-later concern and require command, journal,
recovery, and authorization contracts.

`rcce-project` also exposes a read-only, root-confined inventory and immutable
snapshot. Ordinary unknown files remain visible, unsafe objects remain
content-inaccessible metadata, and accepted files receive source and
path-keyed tree fingerprints. Classification uses an explicit versioned rule
table; `Game/` and `Server/` projections are never authoring inputs.
Cancellation returns no partial authoritative snapshot. Metadata enumeration
does not read file bodies; on Windows it retains the already validated file
handle so the later cancellable snapshot read consumes each accepted body once.
Retained-handle reads use positional offsets, so success-success, cancel-retry,
and concurrent reads of the same enumerated entry all start at byte zero without
sharing a mutable cursor.
Namespace replacements are rejected against that retained object identity. On
Windows, an in-place change to the same object before its read can be the
point-in-time body captured by the snapshot when the platform timestamp is
unchanged; Unix change-stamp evidence rejects it. Size, link, identity, and
timestamp changes during the read are rejected before bytes are accepted.
Caller-supplied depth budgets are additionally capped at 64 levels to keep the
recursive descriptor/handle walk within a fixed stack-safety envelope. State
class ordering follows the explicit P07 registry order rather than Rust enum
declaration order, and tree identity canonicalizes path order while rejecting
duplicate accepted paths.

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
