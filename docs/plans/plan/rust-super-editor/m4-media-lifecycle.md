# M4 — Media lifecycle

## Identity

- **Canonical tasks**: `44, 45, 46, 47, 48, 49`
- **Depends on**: accepted M2 storage/commands and the M3 records that reference media
- **Status**: Planned

## Outcome

The editor treats media as both catalog records and project-root files. Import, preview, reference repair, rename, replacement, and deletion preserve legacy registry semantics and filesystem safety as one logically grouped, journaled, recoverable command.

## Work packages

| Packet | Canonical task | Deliverable | Acceptance evidence |
|---|---:|---|---|
| `M4-MEDIA-MODEL` | 44 | Typed registry/file identity, topology, reference and usage model | Consensus fixtures cover duplicate IDs/names, missing files, unknown rows and directory case |
| `M4-IMPORT` | 45 | Root-confined staged import with registry update in one command | Collision/interruption tests classify partial outcomes and preserve originals for safe recovery |
| `M4-LIFECYCLE` | 46 | Rename, replace, relink, repair and guarded delete commands | Recovery offers retry/compensation only while fingerprints match; usage graph blocks unsafe deletion |
| `M4-PREVIEW` | 47 | Image, mesh and audio preview adapters with explicit unsupported states | Fixture previews and resource-lifecycle tests pass without mutating project data |
| `M4-BATCH` | 48 | Deterministic batch import and collision-resolution policy | Same input and choices yield the same catalog IDs, names and files |
| `M4-REGRESSION` | 49 | Cross-application and legacy-consumer regression suite | Saved projects reopen with matching registry order, IDs, references and asset bytes |

## Primary implementation surfaces

- `editor-rs/crates/rcce-project/src/media.rs` owns catalog/file parsing and stable identity.
- `editor-rs/crates/rcce-editor-core/src/commands/media.rs` owns lifecycle commands and inverses.
- `editor-rs/crates/rcce-validation/src/media.rs` owns missing, duplicate and dangling-reference diagnostics.
- `editor-rs/crates/rcce-render/` and a narrow audio adapter own preview resources; neither owns persistence.
- `test-data/projects/` supplies collision, alias, missing-file, corrupt-preview and cross-platform-case fixtures.

## Required sequencing

1. Prove catalog topology and path rules before implementing import.
2. Implement single-asset import and rollback before batch behavior.
3. Build the usage graph before enabling rename, replacement or deletion.
4. Keep preview decoding read-only and failure-contained.
5. Run legacy and Rust consumer checks before advancing any media row to compatibility level I3.

## Verification

- Parser/writer round trips preserve ordering, sentinels, opaque records and untouched file bytes.
- Fault injection covers staging, registry write, file promotion and journal recovery boundaries, including truthful partial-state reopen classification.
- Root-confinement tests cover traversal, symlinks/junctions, case aliases and destination collisions.
- Preview tests prove corrupt or unsupported assets return diagnostics rather than crashing or writing.
- Batch tests prove deterministic assignment and one logically grouped recoverable command transaction without claiming cross-file atomicity.

## Exit gate

All supported media classes have accepted lifecycle packets, no unresolved dangling-reference mutation path remains, and the compatibility matrix records legacy/Rust consumer evidence for every writable registry.

## Non-goals

- Re-encoding assets merely to normalize them.
- Hiding unsupported proprietary formats behind a generic “success.”
- Deleting referenced media through a force flag without an explicit repair plan.
