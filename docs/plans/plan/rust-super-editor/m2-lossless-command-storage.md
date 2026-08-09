# M2 — Lossless codecs, command/storage, and project shell

## Identity

- **Canonical tasks**: 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33
- **Status**: `Planned`
- **Depends on**: M1 accepted
- **Blocks**: semantic authoring, media, world, administration, publication

## Outcome

Selected legacy formats can round-trip losslessly and accept bounded semantic edits through typed commands. All project writes flow through root-confined, journaled, crash-recoverable storage. The Rust project shell safely creates, remembers, renames, clones, and backs up projects.

## Work packages

| Packet | Tasks | Deliverable | Depends on |
|---|---:|---|---|
| `SE-M2-P01` Shared raw IO | 20 | One byte-faithful reader/writer contract with downstream compatibility | M1 provenance |
| `SE-M2-P02` General provenance | 21 | Full `LegacyDocument<T>` with spans/topology/unknowns | P01 |
| `SE-M2-P03` Indexed topology | 22 | Index cells/aliases/gaps/orphans/invalid-slot model | P02 |
| `SE-M2-P04` First I2 writers | 23 | Small bounded no-op writers with byte identity | P02; P03 for catalogs |
| `SE-M2-P05` Command model | 24, 29 | Commands, inverse/replay, Ledger event model | M1 core |
| `SE-M2-P06` Storage boundary | 25, 26 | Opaque artifacts, retained originals, durable journal/promotions | Root ADR, P05 |
| `SE-M2-P07` Crash recovery | 27 | Kill-at-boundary harness and deterministic reopen classification | P06 |
| `SE-M2-P08` Conflicts | 28 | Per-file recheck and safe merge/overwrite policy | P06 |
| `SE-M2-P09` Create/recent | 30, 31 | Template creation, recent roots, invalid-root behavior | P06 |
| `SE-M2-P10` Rename/clone/backup | 32, 33 | Source-preserving project operations and adversarial tests | P06, state classes |

## Required writer sequence

1. Money/fixed attributes/damage-name candidates establish the I2 pattern.
2. Catalog writers remain blocked until topology packet acceptance.
3. An I2 writer does not imply I3 mutation support.
4. Each I3 field edit becomes its own capability packet with mutation-local byte evidence.

## Storage invariants

- `rcce-storage` depends on no format/domain crate.
- Temp and destination share a filesystem/volume when atomic replacement is claimed.
- Original bytes/recovery copies exist before first promotion.
- Journal state, temp bytes, replacement, and directory durability follow the accepted ADR ordering.
- Recovery never overwrites an externally changed fingerprint.
- Metadata namespace ownership/version is proven before journal creation.

## Verification

- Byte identity and mutation-local diffs over normal, malformed, aliased, and noncanonical fixtures.
- Subprocess termination at every durable boundary on Windows and Linux.
- Concurrent mutation between promotions cannot lose either version.
- Project shell refuses non-empty/alias/link destinations and preserves source hashes.
- Secret/dynamic-state disclosures and permissions match the output-classification matrix.

## Exit gate

- At least the chosen starter formats reach I2 and one bounded field reaches I3.
- Command/Ledger, storage, recovery, conflict, and project-shell packets are accepted.
- M3 packets may start only for formats with explicit compatibility prerequisites.

## Non-goals

- No blanket write enablement, catalog canonicalization, or format migration.
