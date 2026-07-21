# Rust super editor architecture decisions

This directory is the decision spine for the Rust super editor program. The
canonical program specification defines the outcome and milestone sequence;
these records fix the architectural boundaries that later capability packets
must preserve or deliberately supersede.

## Status vocabulary

- `Accepted` — binding for implementation until superseded.
- `Accepted (protocol); selection pending` — the evaluation contract is
  binding, but the evaluated technology has not yet passed its gate.
- `Proposed` — concrete decision awaiting named executable evidence.
- `Superseded` — retained for history and linked to its replacement.

An ADR status does not promote any format row's compatibility level. The
[`project-format compatibility matrix`](../compat/project-format-matrix.md)
remains the authority for `I0` through `I4` evidence.

## Decision index

| ADR | Decision | Status | Primary dependencies |
|---|---|---|---|
| [0001](0001-compatibility-ladder-and-support-disposition.md) | Compatibility ladder and support disposition | Accepted | Format and capability matrices |
| [0002](0002-raw-legacy-strings-and-provenance.md) | Raw legacy strings and document provenance | Accepted | ADR-0001 |
| [0003](0003-project-root-confinement-and-link-policy.md) | Project-root confinement and no-follow link policy | Accepted | State classifications |
| [0004](0004-command-only-project-mutations.md) | Commands as the only project mutation path | Accepted | ADR-0002, ADR-0003 |
| [0005](0005-durable-journal-and-recovery.md) | Durable journal and truthful recovery | Accepted | ADR-0003, ADR-0004 |
| [0006](0006-ui-render-spike-decision-protocol.md) | UI/render spike decision protocol | Accepted (protocol); selection pending | Current `rcce-render` |
| [0007](0007-owned-versioned-editor-metadata.md) | Owned and versioned editor metadata namespace | Proposed | ADR-0003, ADR-0005 |
| [0008](0008-plugin-capability-and-security-model.md) | Plugin capability and security model | Accepted | ADR-0003, ADR-0004, ADR-0005 |
| [0009](0009-rust-workspace-convergence-and-reuse.md) | Rust workspace convergence and crate reuse | Accepted | Existing client/server workspaces |

## Reading order

Read ADR-0001 first: it defines what compatibility claims mean. ADR-0002 and
ADR-0003 define the data and filesystem trust boundaries. ADR-0004 and ADR-0005
define mutation and durability. ADR-0006 governs the pending executable UI
choice. ADR-0007 and ADR-0008 govern editor-owned state and untrusted extension
work. ADR-0009 governs how these capabilities enter the existing Rust platform.

## Change control

Accepted records are changed only by a new numbered ADR that names the record
it supersedes, preserves the old file, and states migration and compatibility
effects. Clarifications that do not alter decisions may amend an ADR, but must
update its evidence and change history. Proposed records become Accepted only
when their own verification section is satisfied and the status is updated in
both the record and this index.

## Authorities

- [Canonical Rust migration specification](../plans/plan/rust-super-editor-engine-migration.md)
- [M0 compatibility laboratory](../plans/plan/rust-super-editor/m0-compatibility-laboratory.md)
- [Project-format compatibility matrix](../compat/project-format-matrix.md)
- [Editor capability and retirement matrix](../compat/editor-capability-matrix.md)
- [Editor-suite master specification](../editor-suite-master-spec.md)
