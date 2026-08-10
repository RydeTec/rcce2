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

Sparse zero-offset media slots remain visible legacy topology and do not alone
downgrade that accepted actor/base-mesh slice. Consensus still fails closed for
aliases, invalid or decode-failed offsets, skipped records, parser disagreement
or incompleteness, duplicate actor IDs, unsafe raw identities, and ambiguous
physical resolution. An actor that references an unused slot is reported as
`MissingCatalog`; exact physical paths appear only for uniquely resolved
accepted inventory identities. This scoped consensus does not establish global
media-catalog completeness, validity, provenance, orphan status, repair safety,
or writer authority.

The consensus gate also compares the exact raw 16-bit base-mesh slot-0 value
reported for each exact actor ID by the client and server actor parsers. Server
signed values are compared by their unchanged bit pattern, so `-1` and client
`65535` are the same observed `0xFFFF` identity. A missing counterpart,
duplicate ID, incomplete parse, ID-set difference, or slot-0 mismatch keeps the
whole slice provisional and withholds physical paths and reference diagnostics.
This comparison says nothing about slots 1–7, full parser equality, an
independent server media catalog, runtime appearance, compatibility, or health.

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

The Assets `FILES` fallback partitions that accepted inventory by exact observed
path root: All, Meshes, Textures, Sounds, Music, Emitter Configs, UI, and an
exclusive Other complement. Matching is ASCII-insensitive only for the exact
immediate `Data/<Root>/` prefix; embedded names, lookalike roots, and bare root
paths are not promoted into a named facet. Each facet reports its whole accepted
file count and accepted source-file byte total independently of the path filter,
while the filtered row list retains exact raw paths and is fixed-height and
virtualized without an additional cap. External exact Asset file routes clear
the filter and open All before selecting the path. Failed or in-flight project
replacement preserves the prior facet and rows, same-root Reload reconciles the
selected facet atomically, and successful Open resets to All. These labels are
path-root inventory evidence only: they do not establish file type, registry or
numeric media membership, reference/use/orphan status, completeness, validity,
compatibility, previewability, source lineage, provenance, cleanup advice, disk
allocation, runtime or memory cost, or a performance hotspot. Other means only
outside the listed exact roots and does not mean unknown, unsupported, unhealthy,
or disposable.

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

The Vault lens defaults to a State Boundaries map over the accepted path
classification evidence. Every accepted Vault path appears exactly once in
raw-path order, while overlapping Secret, Dynamic Private, and Server Config
facets preserve every applicable label; paths outside those focused labels
remain reachable through Unknown / Other and the exhaustive Files view. The
existing inspector shows exact path, size, format family, compatibility,
state-class labels, and source fingerprint without displaying file contents.
`Secret` means classified for secret-handling policy, not proven credentials;
Unknown / Other never means safe or disposable, and backup-looking paths are
not promoted to authoritative or recoverable. This is not credential
detection, validation, project health, administration, cleanup, repair,
redaction proof, or a backup/publish allowlist.

`KNOWN OBSERVATIONS` opens one project-wide navigator over the accepted
diagnostic slices. Rows are ordered and exhaustive within three explicitly
named evidence classes: consensus actor/media diagnostics, filename-pairing
zone observations, and script-inventory observations. Coverage cards keep
provisional actor diagnostics visibly withheld and unavailable actor evidence
visibly unavailable; an empty list says only that the current accepted
coverage has no observations, never that the project is healthy. Every row
retains its exact code, message, and raw identity. Actor and zone rows route to
their accepted semantic identity, while script-inventory rows route to the
exact accepted physical path rather than inventing an active script identity.
Activation is revalidated against the current snapshot and participates in the
same bounded Return trail. The list is virtualized and uncapped. It is not a
complete validator, health score, severity model, repair tool, or claim about
unparsed zone or script meaning.

Every accepted file inspector also reports accepted source-fingerprint peers
from the current snapshot. Exact full SHA-256 equality is the only grouping
signal; singleton files say that no other accepted path shares the fingerprint,
while grouped files expose every other exact path in raw-byte order with its
lens and observed size. Peer rows are fixed-height and virtualized without an
additional cap, and activation revalidates the fingerprint, lens, and path
before opening the exact Files view through the bounded Return trail. Zero-byte
accepted paths remain eligible evidence. A shared accepted source fingerprint
does not establish provenance, semantic equivalence, redundancy,
replaceability, deletion advice, or any content-derived relationship. The
surface does not inspect content beyond the source fingerprint already accepted
by the snapshot and adds no deduplication, cleanup, repair, or write path.

`FIND ANYTHING` (`Ctrl+K`) opens a cross-lens palette over the currently
accepted snapshot. It trims the query and applies Unicode lowercasing for
literal matching over every exact inventory path plus the accepted raw actor
IDs, actor-referenced mesh IDs, filename-derived zone identities, and active
`.rsl` identities. This is not full Unicode case folding. Script and
File results remain distinct even when they share the same source path, and
each result opens the exact lens/subview that owns its raw identity. The list
is exhaustive and scrollable; it is not a fuzzy, content, diagnostic,
provenance, or command search. Activation re-resolves the target against the
current accepted snapshot, so a stale result cannot redirect to a normalized
or inferred replacement.

`RETURN` (`Alt+Left`) unwinds a session-local trail of at most 32 prior raw
focus identities. Only successful Find Anything activation, Known Observations
activation, accepted source-fingerprint peer activation, and observed
actorâ†”base-mesh thread traversal add an origin;
ordinary lens, subview, card, diagnostic, and filter interaction neither adds
nor clears entries. Return
revalidates each entry against the accepted snapshot, skips stale or current
focus, and routes through the same exact lens/subview clearing rules without
adding the destination just left. Same-root Reload preserves only identities
that still resolve, while opening another project clears the trail even when
raw IDs happen to coincide. This in-memory convenience is not project or
browser history, persisted recents, a timeline, undo/redo, or accessibility
acceptance.

`RELOAD SNAPSHOT` re-inventories the currently accepted project through the
same confined read-only loader. The prior accepted project, root, lens, filter,
and focus remain visible while the replacement loads. Success swaps the full
projection atomically and retains raw file, actor, mesh, zone, and script focus
only when the identity still resolves; failure or a disconnected loader keeps
the prior accepted session visible and reports the failed refresh. Reload is
explicit rather than automatic: this surface does not watch the filesystem,
merge concurrent changes, repair data, or provide live synchronization.

After a successful same-root Reload, `LAST RELOAD DELTA` compares the current
accepted snapshot with the immediately prior accepted snapshot. It emits three
disjoint evidence groups in deterministic raw-byte path order: Newly Accepted,
No Longer Accepted, and Content Fingerprint Changed. Newly accepted does not
mean created; no longer accepted does not mean deleted or intentionally
removed; an old/new path pair never asserts rename; and a fingerprint change
means only that the accepted source SHA-256 differs at the same exact path.
Current-side rows revalidate and open the exact lens Files view through the
bounded Return trail, while prior-only rows remain noninteractive. The panel
retains only the latest successful comparison, distinguishes no comparison
from an available empty comparison, survives failed or in-flight loads, and
clears after successful Open. It is exhaustive only within the two accepted
snapshots and their existing loader ceilings. It is not a watcher, project or
filesystem history, VCS diff, attribution/audit log, semantic parser, health
or synchronization verdict, conflict detector, repair path, undo, or write
surface.

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
| `rcce-editor` | Desktop composition; the only production crate allowed to acquire a GUI framework | Replaceable feedback-MVP host with five read-only lenses, safe atomic snapshot reload, a one-generation accepted-snapshot delta, exact cross-lens routing, contextual accepted source-fingerprint peers, an evidence-labeled observations navigator, a provisional find-anywhere palette, and a bounded session-local return action |
| `rcce-editor-core` | GUI-independent session, query, selection, and diagnostic orchestration | Real inventory and semantic projections plus immutable deterministic reload-delta, source-fingerprint-peer, observation, and search indexes and bounded raw-focus return-trail state, with no write authority |
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
