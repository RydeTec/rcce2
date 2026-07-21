# ADR 0004 — Commands as the only project mutation path

- **Status:** Accepted
- **Date:** 2026-07-20

## Context and evidence

The legacy suite mixes deferred saves, immediate fixed-offset writes,
whole-document rewrites, media registration, physical file operations, generated
outputs, and external database mutations. One visible action may touch multiple
files or authorities, while current UI widgets can write directly. Reproducing
that coupling would prevent reliable undo, preflight, conflict detection,
recovery, validation, and an honest Ledger.

## Decision

Every editor-originated state change is represented by a typed
`ProjectCommand`. UI widgets, renderer interactions, importers, generators, and
plugins may propose intent but cannot write project state directly.

A command records:

- stable target identity and user-visible intent;
- precondition fingerprints and expected affected files;
- model changes and validation/reference consequences;
- persistence class: `PendingProject`, `ImmediateProject`, `External`, or
  `Projection`;
- reversible in-memory inverse where meaningful;
- authorization, recovery, compensation, and audit metadata.

Command application changes the working model and history. Commit planning
produces validated byte artifacts separately. `rcce-storage` promotes opaque
artifacts and does not depend on UI or domain types.

An external effect begins as an `External`-class `ProjectCommand`, but applying
that command changes only the working model by creating a typed pending intent;
it does not contact the external authority. Commit preflight resolves the exact
authority/resource, validates inputs and permissions without secret-bearing
audit fields, obtains explicit authorization, assigns an operation identity and
idempotency key where supported, and durably records `Prepared` then
`Authorized`. Only the headless `ExternalOperation` service may dispatch it,
after durably recording `Pending`.

The service records `Succeeded`, `FailedBeforeApply`, or `OutcomeUnknown` plus
redacted provider evidence. `OutcomeUnknown` must enter `Reconciling`; it cannot
be retried until an authority-specific read proves `ReconciledApplied` or
`ReconciledNotApplied`. Retry is a new audited attempt linked to the original
intent and is allowed only after `FailedBeforeApply` or `ReconciledNotApplied`;
the service re-runs authorization and preflight and reuses an idempotency key
only when the provider contract proves that safe. Undo before `Pending` may
remove the un-dispatched working intent. After `Pending`, undo never claims
remote rollback: it may propose a separately authorized compensation operation
linked in the audit when the authority supports one. Projection commands create
publication or diagnostic outputs through explicit allowlists without changing
authoring truth.

## Alternatives considered

1. Let each editor surface own save logic. Rejected because lenses would create
   competing state authorities and inconsistent recovery.
2. Wrap direct writes with an event log. Rejected because logging after the
   mutation cannot supply preflight, inverse, conflict, or durable recovery.
3. Treat database actions as ordinary commands. Rejected because remote commit
   and rollback truth cannot be represented by a filesystem undo stack.

## Consequences

- The Ledger is driven by real command/storage/external events.
- Undo/redo covers working-model commands, not committed external effects.
- Save, import, rename, delete, paired-zone changes, settings, publication, and
  generators share preflight and audit semantics.
- The command boundary adds ceremony to small edits but eliminates hidden writes.

## Invariants

- No UI, renderer, plugin, or domain model writes canonical project files.
- Commands replay deterministically against matching preconditions.
- Undo restores model invariants; it does not claim disk or external rollback.
- Every affected persisted identity and resource is visible before commit.
- Validation and reverse references update after apply/inverse and fully before
  commit or publication.
- External `OutcomeUnknown` is never shown as clean failure or blindly replayed.
- Every dispatched external attempt has durable authorization, preflight,
  pending, outcome, reconciliation, retry, and compensation audit links.

## Verification and exit evidence

Tests prove apply/inverse/replay, deterministic command sequences, stable
diagnostic deltas, inbound-reference preflight, reset/reload semantics, and
serialization of command descriptions without secrets. Integration flows cover
inventory → command → write plan → commit/recovery, paired documents, import plus
catalog registration, rename policies, projection allowlists, and external
denied authorization, failed preflight, timeout-before/after apply,
reconciliation to both applied and not-applied, safe retry, unsupported
idempotency, pre-dispatch undo, and post-dispatch compensation. A static
architecture check rejects direct filesystem mutation imports outside approved
storage, runtime, and fixture code and rejects external-provider calls outside
`ExternalOperation` adapters.

## Dependencies

- [ADR-0002](0002-raw-legacy-strings-and-provenance.md)
- [ADR-0003](0003-project-root-confinement-and-link-policy.md)
- [ADR-0005](0005-durable-journal-and-recovery.md)

## Supersession and change process

Any second mutation route requires a new ADR proving equivalent preflight,
audit, conflict, security, and recovery semantics. Adding a command type or
persistence class within these invariants does not supersede this record.
