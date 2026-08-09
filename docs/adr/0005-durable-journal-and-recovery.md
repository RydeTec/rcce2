# ADR 0005 — Durable journal and truthful recovery

- **Status:** Accepted
- **Date:** 2026-07-20

## Context and evidence

RCCE operations such as Settings, paired zones, imports plus registration,
rename, conversion, and publication cross files. Per-file temporary promotion
does not make the group atomic. Process termination, power loss, disk exhaustion,
antivirus/file locks, or an external editor can interrupt between promotions.
External database actions have still weaker rollback guarantees. A transaction
label without a persisted state machine would overclaim safety.

## Decision

`rcce-storage` executes a precomputed commit plan over opaque validated byte
artifacts. Before mutation the plan contains:

- logical command/action identity;
- project-root capability identity;
- ordered targets and original fingerprints;
- retained original bytes or recovery-copy identities;
- same-volume temporary paths, artifact checksums, and optional reparse checks;
- expected output fingerprints and promotion order.

The project-local journal uses a versioned persisted state machine. At minimum,
each plan/target distinguishes `Planned`, `OriginalCaptured`, `RecoveryDurable`,
`TempWritten`, `TempDurable`, `PreconditionVerified`, `PromotionAuthorized`,
`Promoted`, `DirectoryDurable`, and `Complete`, plus explicit
failure/interruption observations. `RecoveryDurable` has one recorded mode:

- `InlineOriginalDurable`: for non-`Secret` targets only, exact original bytes,
  length, and checksum are
  embedded in the protected journal payload; its file data and metadata, plus
  any containing-directory entry created or replaced by that write, are durably
  flushed;
- `RecoveryCopyDurable`: a recovery copy is created through the confined handle,
  byte-for-byte verified against the original, its file data and metadata and
  newly created containing-directory entry are durably flushed, it is assigned
  an opaque recovery identity, and that identity/checksum is recorded in a
  durably flushed journal transition; or
- `OriginalAbsentDurable`: the target did not exist, and its absent identity and
  precondition are durably recorded for a create operation.

A `Secret`-class target never uses `InlineOriginalDurable`: neither original nor
replacement secret bytes may appear in the journal. Outside the canonical
target before replacement, its original bytes exist as an additional durable/
persistent exact-byte copy only in an opaque, restrictively permissioned
`RecoveryCopyDurable` artifact; the journal contains only its opaque identity,
length, and checksum. This persistent-copy restriction permits exact original
bytes in transient memory only under a proven `SecretMemoryAuthority`. That
authority is granted after mutation authorization and size preflight and owns
one bounded, single-owner, non-copying, zeroizing buffer. The buffer is locked
and non-pageable and excluded from core and application/OS crash dumps using tested
platform primitives; cloning, implicit copies, serialization, formatting, and
access outside its narrow construction API are forbidden. If the selected
platform cannot prove memory locking/non-pageability, dump exclusion, the size
bound, single ownership, and normal/unwind zeroization hooks, Secret mutation is
unsupported rather than silently degraded.

ADR-0003's `SpeculativeReadGate` fills the complete bounded Secret buffer, runs
all applicable post-read checks, and either rejects and zeroizes it or returns an
accepted buffer. No recovery/temp filesystem artifact is opened, created, or
written from those bytes before successful gate completion. Only afterward may
the still-authorized accepted buffer construct the protected recovery artifact
and compute its checksum. After the artifact is durably written, the owner
zeroizes the original buffer; readback verification reuses the same zeroizing
owner and compares a cryptographic digest, so no second process-memory copy is
introduced. The buffer is never journaled, logged, named, cached, rendered,
placed in a manifest, sent through a callback/plugin, or retained for reuse.

The staged Secret replacement output is also opaque-named, restrictively
permissioned, excluded from inspection and every projection, and governed by
the same bounded retention, cleanup, and cleanup-failure reporting as the
recovery artifact. Transient process-private Secret bytes used to construct or
verify it follow the same authorization, bounds, prohibited-sink, and
`SecretMemoryAuthority` lifecycle. Forced termination, kernel failure, or power
loss cannot run a userspace zeroization hook; non-pageable/dump-excluded storage
prevents sanctioned swap/pagefile and dump copies, but residual physical-memory,
firmware, hypervisor, or forensic recovery limits are disclosed rather than
claimed erased.

Journal updates use one crash-consistent format: an append-only sequence of
checksummed frames. The immutable header identifies format version, journal and
operation identities. Every frame contains a monotonically increasing sequence,
previous-frame digest, payload length, payload checksum, state payload, and
frame checksum/trailer. A frame becomes authoritative only after its complete
bytes and file metadata are durably flushed. Recovery accepts the longest
contiguous chain from the header, ignores an incomplete or checksum-invalid
torn tail, and blocks automatic recovery on a sequence/link/checksum failure
before the tail. Compaction, if introduced, writes and verifies a complete new
journal and atomically promotes it with file and containing-directory durability;
in-place rewrite is forbidden.

`OriginalCaptured` alone is not promotion authority. A target cannot reach
`PromotionAuthorized` until one of the durable recovery modes above exists and
remains verifiable.

For this ADR, durable creation of a journal or recovery-copy file means both
that the file's data and required metadata have reached durable storage and that
the containing directory has been durably flushed after creation. A journal
update that creates, renames, or replaces a directory entry likewise includes
the containing-directory flush. A platform adapter may substitute only a
documented, executable-tested equivalent guarantee. If neither directory flush
nor an equivalent guarantee is available, the state does not become durable,
`RecoveryDurable` and `PromotionAuthorized` remain unreachable, and the editor
truthfully reports that guarantee-bearing mutation is unsupported. It may offer
inventory, export, or an explicit manual-backup workflow, but must not relabel
that fallback as journaled recovery.

Ordering is:

1. create the journal/plan, durably flush its file data and metadata, then
   durably flush the journal containing directory;
2. after Secret-target mutation authorization, size preflight, and proven
   `SecretMemoryAuthority`, let ADR-0003's applicable read gate capture the
   complete original into its bounded buffer, finish post-read checks, and
   accept the buffer; on rejection, zeroize and stop, or record target absence;
3. for a non-`Secret` target, durably embed and flush those bytes in the journal
   payload, including directory durability if its entry is created or replaced;
   only after a `Secret` buffer is accepted, create and write the protected
   recovery artifact, zeroize/reuse the sole owner for digest readback
   verification, durably flush the recovery file's data and metadata, durably
   flush the recovery-copy containing directory, and record only its opaque
   identity, length, and checksum in the journal; only then persist
   `RecoveryDurable`;
4. write, validate, and durably flush same-volume temporary output;
5. acquire `ReplaceAuthority`, then recheck target identity and fingerprint
   through the confined handle while holding that authority;
6. without releasing `ReplaceAuthority`, persist and durably flush
   `PreconditionVerified`, then persist and durably flush `PromotionAuthorized`
   only if `RecoveryDurable` and `TempDurable` still validate;
7. atomically replace the target and durably flush its parent directory without
   releasing `ReplaceAuthority`, then release it;
8. persist the target result before advancing;
9. mark the plan complete only after all required targets and directory states
   are durable.

On reopen, recovery classifies every original, expected output, temp, recovery
copy, and current fingerprint before offering retry, compensation, preserve-copy,
or inspection. Compensation is offered only while current bytes still match the
output owned by the interrupted action. Resume and restart are distinct.

`ReplaceAuthority` is an OS-enforced exclusive mutation lease covering the
target from the final identity/fingerprint check through replacement and parent-
directory durability, or an OS/filesystem conditional-replace primitive that
atomically compares an unforgeable version identity while replacing. Advisory
locks and check-then-rename do not qualify. If the platform cannot prove either
form against pre-existing and newly opened hostile writers, strong
no-lost-update mutation is unsupported: the target never reaches
`PromotionAuthorized`, and the editor offers only non-mutating/export/manual-
backup alternatives with the limitation disclosed.

External operations never enter this journal as rollback-guaranteed filesystem
steps; their audit links to reconciliation and explicit compensation metadata.
Descriptive journal fields, paths, command descriptions, reports, manifests,
identities, and logs never contain secret values. Exact original bytes from a
`Secret`-class target may persist outside the pre-replacement canonical target
only in the protected recovery artifact when the user explicitly authorized
that target's mutation; exact replacement bytes may persist only in its
protected temporary artifact and eventual canonical target. Authorized bounded
process-private scratch is transient, side-effect-free, and zeroized as defined
above; it is not another durable copy.
Both artifacts use restrictive platform permissions, opaque/redacted names and
identities, default inspection/projection/output exclusion, and the documented
secure cleanup and retention procedure after durable completion or explicit
recovery resolution. Cleanup failure is itself redacted, leaves recovery
visible as unresolved without exposing content, and blocks any false claim of
sanitization. Physical erasure limits of journaling filesystems, copy-on-write
storage, and SSDs are disclosed rather than overclaimed.

## Alternatives considered

1. Rename all temporaries sequentially and call it atomic. Rejected because the
   group has observable intermediate states.
2. Keep recovery state only in memory. Rejected because termination erases the
   only evidence needed after restart.
3. Automatically restore every original on reopen. Rejected because it can
   overwrite valid post-failure user or external changes.
4. Use a database transaction for project files. Rejected because the project
   remains a legacy filesystem tree consumed by independent applications.

## Consequences

- Multi-file actions are logically grouped and recoverable, not universally
  atomic.
- Platform-specific flush and metadata/ACL behavior must be documented and
  tested; unsupported durability guarantees are disclosed.
- Recovery may require user choice when fingerprints do not match an owned
  state.
- Journals and recovery copies are editor metadata governed by ADR-0007.
- Secret-bearing recovery can require protected storage and explicit cleanup;
  ordinary journal inspection never displays its contents.

## Invariants

- No target promotion occurs before a durable plan, durable validated temp, and
  a verifiable `RecoveryDurable` state containing exact original bytes, a
  verified durable recovery copy, or a durably recorded absent target.
- A newly created journal or recovery copy is not durable until both its file
  data/metadata and its containing-directory entry are durable; unsupported
  directory durability blocks `RecoveryDurable` and `PromotionAuthorized`.
- Journal recovery uses only a verified contiguous frame chain; a torn tail is
  ignored and an invalid committed prefix blocks automatic action.
- Every target fingerprint is rechecked under enforceable `ReplaceAuthority`,
  which remains held through replacement and directory durability.
- Recovery never overwrites or removes an unrecognized current fingerprint.
- Rollback deletes only journal-owned paths that remain journal-owned by
  identity and fingerprint.
- Completion is persisted only after required file and parent-directory
  durability steps.
- Cross-authority external effects are never described as filesystem rollback.
- Secret values appear persistently only in explicitly authorized protected
  recovery, temporary, and canonical target bytes. They may exist transiently
  only under `SecretMemoryAuthority` in one authorized bounded, locked and
  non-pageable, dump-excluded, non-copying process-private owner. Normal return,
  rejection, error, cancellation, and unwind paths invoke and verify zeroization;
  abrupt termination/power-loss residual physical-memory limits are disclosed.
  Secret values never enter journal payloads or descriptive fields, names,
  manifests, callbacks/plugins, caches, reports, diagnostics, or logs.

## Verification and exit evidence

Deterministic fault injection and subprocess-kill tests stop immediately before
and after every transition and durability boundary, and at byte offsets inside
the journal header and each frame's sequence, previous digest, length, payload,
checksums, and trailer. Boundaries include journal creation, journal file data/
metadata flush, journal containing-directory flush, original capture,
non-secret inline-original file flush and any journal-entry directory flush,
recovery-copy creation, byte verification, recovery-copy file data/metadata flush,
recovery-copy containing-directory flush, recovery identity/checksum journal
flush and any journal-entry directory flush, `RecoveryDurable`, temp
write/validation/flush, `ReplaceAuthority` acquisition, identity/fingerprint
recheck under that authority, every transition flush while it remains held,
`PromotionAuthorized`, target replacement, target containing-directory flush,
authority release, result flush, and completion.
No killed run reaches a promoted target without independently verified recovery
material. Tests include multiple targets, external edits between promotions,
missing/changed/corrupt recovery material, missing/changed temps, stale journals,
disk-full and permission failures, and repeated reopen/recovery. Unsupported
directory-flush fixtures prove the durable states remain unreachable, no target
is promoted, the limitation is disclosed, and only non-journaled fallback
actions are offered. Secret-canary tests verify authorization,
restrictive permissions, opaque naming, and absence of secret bytes from every
journal frame, descriptive field, log, diagnostic, manifest, and output.
Canaries verify secret bytes occur only in the authorized temp, recovery, and
canonical target artifacts plus instrumented authorized transient buffers;
prove capability denial when locking/non-pageability, dump exclusion, bounded
allocation, single ownership, non-copying construction, or zeroization hooks are
unavailable. Successful fixtures prove allocation occurs only after authorization
and size preflight, the complete read and postchecks finish before any recovery/
temp artifact is opened or written, and only the accepted buffer constructs the
artifact. Instrumented ownership/copy checks and heap/scratch inspection prove
no duplicate process-memory buffer. Zeroization hooks are observed on success,
gate rejection, authorization denial after preflight, verification failure,
cancellation, ordinary error, unwind, rollback, abandonment, retention expiry,
and injected cleanup failure. Journal frames, logs, callbacks/plugins,
caches, names/manifests, diagnostics, and cleanup-failure reports never receive
the canary. Crash-dump configuration tests prove exclusion or deny the capability;
swap/pagefile tests prove the selected locking primitive or deny it. Forced-kill
and power-loss reports do not claim hook execution or physical-memory erasure.
Failed persistent-artifact cleanup remains reported without leaking the canary.
Torn writes at every frame offset recover
only the last valid chain. A hostile writer holding an old handle and another
opening during transition flushes must either be excluded by `ReplaceAuthority`
or cause promotion to remain unsupported; the final bytes may never silently
lose its write. Every observed state maps to a truthful action set, and recovery
is idempotent. Platform tests record filesystem, OS, metadata, ACL, secure-
cleanup limitations, flush behavior, and replacement-authority primitive.

## Dependencies

- [ADR-0003](0003-project-root-confinement-and-link-policy.md)
- [ADR-0004](0004-command-only-project-mutations.md)
- [ADR-0007](0007-owned-versioned-editor-metadata.md)

## Supersession and change process

Changing state names, ordering, ownership, or durability guarantees requires a
new versioned journal ADR and backward recovery reader. Existing incomplete
journals remain readable until their projects have been safely resolved.
