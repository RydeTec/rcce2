# ADR 0007 — Owned and versioned editor metadata namespace

- **Status:** Proposed
- **Date:** 2026-07-20

## Context and evidence

The editor needs durable journals, project identity, history, layout, caches,
and other private state that cannot safely enter legacy gameplay documents.
Loom currently writes unversioned files under `Data/Loom/`; those files do not
establish ownership, schema compatibility, copied-project identity, collision,
or recovery rules. An apparently available directory may instead be an ordinary
file, unowned directory, link/reparse point, or newer metadata schema.

The canonical plan identifies `Data/.rcce/` as the candidate namespace, but the
required Windows hidden-directory and packaging behavior has not yet been
executed. The path therefore remains proposed rather than accepted.

## Decision

Reserve `${PROJECT_ROOT}/Data/.rcce/` as the candidate editor-owned namespace.
The namespace is not writable until a root-confined atomic reservation creates
an ownership marker at `Data/.rcce/ownership.toml` containing at least:

- fixed namespace magic/owner identifier;
- metadata schema version;
- creating and last-writing tool versions;
- project identity and identity-generation method;
- creation timestamp and compatibility flags.

Subtrees are versioned by purpose, including `journal/`, `recovery/`, `history/`,
`layout/`, and `cache/`. Durable recovery state and user-authored editor state
are not put in an evictable cache. Gameplay/runtime meaning never depends on
this namespace while legacy compatibility is supported.

Descriptive metadata—including ownership markers, journal fields, history,
paths, identities, names, manifests, diagnostics, and logs—never contains secret
values. The sole exception is exact source bytes retained as protected recovery
material under ADR-0005 when the user explicitly authorizes mutation of a
`Secret`-class target. Secret-bearing original bytes live only in an opaque
artifact under `recovery/`; they are forbidden from journal payloads. Secret-
bearing replacement bytes may exist only in an equivalently protected opaque
temporary artifact until canonical promotion. Both artifact classes use
restrictive platform permissions, are never rendered by ordinary metadata
inspection, and are excluded from clone, publication, diagnostics, playtest
snapshots, and other outputs by default. Completion, rollback, abandonment, and
retention expiry run the documented secure-cleanup procedure and report any
cleanup failure or platform physical-erasure limitation without leaking names
or content.

Reservation occurs only when the candidate path is absent. A pre-existing file,
unowned or nonempty directory, link/reparse point, malformed marker, newer
schema, or copied-project identity mismatch opens inventory-only and blocks
metadata writes. Adoption and relocation are explicit commands with backup,
preflight, and recovery; they are never inferred from directory names.

Metadata upgrades preserve unknown keys and retain a verified backup. Clone,
backup, conversion, diagnostic bundle, playtest snapshot, client publication,
and server publication include or exclude each subtree through their own state-
class allowlists. Legacy and Rust runtimes ignore the namespace.

## Alternatives considered

1. Continue `Data/Loom/`. Rejected because the name binds metadata to a retired
   application and lacks ownership/version semantics.
2. Store all metadata in user profile state. Rejected because journals and
   project identity must travel with deliberate project copies and recover the
   correct tree; machine-private preferences may still live outside projects.
3. Put metadata at project root. Rejected because `Data/` is the established
   project-state boundary and publication filters already classify it.
4. Treat any existing `.rcce` directory as owned. Rejected because collision or
   hostile content would gain trusted status by name alone.

## Consequences

- The candidate remains read-only until the named cross-platform gates pass.
- Copies can detect identity mismatch instead of silently sharing journal state.
- Output operations must state whether metadata and which subtrees are included.
- Unknown future keys can survive older-tool reads/upgrades.
- Explicitly authorized secret-bearing recovery material is operationally
  distinct from descriptive metadata and receives restrictive lifecycle rules.

## Invariants

- Absence is the only state from which automatic reservation may occur.
- Ownership is proven by a valid marker under the same root capability, not by
  directory name.
- Newer schema and identity mismatch are never downgraded or rewritten silently.
- Recovery journals are durable, non-cache state governed by ADR-0005.
- Secret values are prohibited from descriptive metadata, history, journal
  payloads/fields, paths, names, manifests, reports, diagnostics, and logs.
  Exact secret-bearing source bytes are permitted only in explicitly authorized,
  protected recovery artifacts governed by ADR-0005; replacement bytes are
  permitted only in equivalently protected temp artifacts and canonical targets.
- Secret-bearing recovery and temp artifacts use restrictive permissions,
  opaque identities, default inspection/output exclusion, bounded retention,
  and documented secure cleanup.
- No runtime gameplay behavior depends on editor metadata.

## Verification and exit evidence

Before acceptance, fixtures must prove absence reservation, ordinary-file and
unowned-directory collision, nonempty collision, symlink/junction/reparse
refusal, malformed/newer marker, unknown-key retention, concurrent reservation,
copied-project identity mismatch, upgrade backup/recovery, and no overwrite of
pre-existing bytes. Windows tests must record hidden-directory visibility,
archive/clone behavior, antivirus interaction where observable, and current
client/server/publisher ignoring or filtering the namespace. Unix tests cover
dot-directory packaging and link policy. State-class canaries prove client and
diagnostic outputs exclude disallowed metadata.
Secret-canary fixtures additionally prove denial without explicit mutation
authorization, exact-byte recovery when authorized, restrictive permissions,
opaque names/identities, absence from every journal payload, descriptive field,
log, diagnostic, manifest, and output, and presence only in authorized temp,
recovery, and canonical target bytes. They exercise denial, success, rollback,
abandonment, expiry, cleanup failure, redacted failure reporting, and default
inspection/projection exclusion. Tests disclose when the underlying platform
cannot guarantee physical erasure.

## Dependencies

- [ADR-0003](0003-project-root-confinement-and-link-policy.md)
- [ADR-0005](0005-durable-journal-and-recovery.md)
- Future state-classification and output-allowlist packet `SE-M0-P07`

## Supersession and change process

Acceptance requires the evidence above and an index status update. If the
candidate path fails, a superseding ADR selects another namespace and records
migration for any test projects that already contain valid proposed markers.
Schema changes require versioned readers/upgraders; incompatible ownership
changes require a new ADR.
