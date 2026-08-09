# ADR 0001 — Compatibility ladder and support disposition

- **Status:** Accepted
- **Date:** 2026-07-20

## Context and evidence

RCCE project families differ in identity, encoding, topology, state authority,
and current Rust coverage. The format census records sparse numeric catalogs,
filename-identified documents, offset tables, fixed-position configuration,
runtime-private state, secrets, and opaque specialist formats. Existing Rust
parsers are useful runtime readers, but tolerant parsing is not evidence that a
document can be rewritten without loss.

The [project-format matrix](../compat/project-format-matrix.md) currently finds
no editor document at lossless `I2`, semantic-write `I3`, or conversion `I4`.
The [capability matrix](../compat/editor-capability-matrix.md) also contains
real Unknowns; an unknown application or format cannot be declared replaced by
naming a future feature.

## Decision

Every durable family advances independently through this compatibility ladder:

| Level | Proven capability | Authoring authority |
|---|---|---|
| `I0 Inventory` | The family is discovered, classified, and preserved as opaque bytes where applicable. | None |
| `I1 Read` | Known fields are parsed for display or runtime use. | None |
| `I2 Lossless` | An unchanged parse/write is byte-identical, including unknown bytes and topology. | No semantic edits |
| `I3 Semantic write` | Declared field mutations preserve unrelated bytes, identities, topology, and invariants. | Only the proven fields |
| `I4 Convert` | A versioned direction has tested mapping, verification, and downgrade boundaries. | Only the declared direction |

Runtime-tolerant and editor-authoritative modes are separate. A tolerant prefix
parse, default substitution, clamp, or skipped malformed record may support a
runtime, but it cannot authorize an editor write.

Compatibility level and product support disposition are orthogonal. The
versioned disposition values are:

- `undecided`;
- `supported-target`;
- `unsupported-with-reason(reason, evidence, approval)`.

An `I0` family is not implicitly unsupported. Unsupported families remain in
inventory, preservation, disclosure, conversion, and retirement accounting.

## Alternatives considered

1. Treat any successful Rust parse as editable. Rejected because semantic
   models can erase malformed tails, unknown bytes, order, aliases, gaps, and
   original scalar bits.
2. Mark opaque/unknown families unsupported immediately. Rejected because lack
   of evidence is not a product disposition and would hide retirement gaps.
3. Use one project-wide compatibility version. Rejected because families mature
   independently and mixed-level legacy projects are normal.

## Consequences

- Write controls remain disabled below the required `I3` row and name the
  blocking evidence.
- Counts and diagnostics based only on tolerant `I1` data are marked provisional
  unless consensus and provenance evidence exists.
- Repair and normalization are explicit commands, never side effects of open or
  no-op save.
- Capability retirement requires both format evidence and an approved support
  disposition.

## Invariants

- Levels are monotonic claims backed by retained evidence, not roadmap labels.
- A higher level includes every lower-level obligation for that family.
- Sparse holes, sentinels, persisted identities, unknown bytes, and source
  topology remain part of compatibility unless a named conversion changes them.
- Matrix rows never advance through inference from another family.
- `unsupported-with-reason` never deletes or conceals the family from reports.

## Verification and exit evidence

For each promoted row, attach exact fixtures, tool/version, commands, hashes,
expected and observed results, and an independent review verdict. `I2` requires
byte comparison across unchanged round trips. `I3` additionally requires
mutation-local diffs, invalid/malformed preservation behavior, and all relevant
Rust client/server/editor semantic consumers. `I4` requires immutable-source
conversion, destination verification, idempotence, and declared downgrade proof.

This ADR exits M0 when matrix tooling uses these exact levels and dispositions,
and rejects a synthetic attempt to infer `I2` from an `I1` parser test.

## Dependencies

- [Project-format compatibility matrix](../compat/project-format-matrix.md)
- [Editor capability matrix](../compat/editor-capability-matrix.md)
- [Canonical specification](../plans/plan/rust-super-editor-engine-migration.md)

## Supersession and change process

Changing a level definition or disposition vocabulary requires a new ADR and a
matrix migration that preserves prior claims and evidence. Adding evidence to a
row does not supersede this ADR.
