# ADR 0003 — Project-root confinement and no-follow link policy

- **Status:** Accepted
- **Date:** 2026-07-20

## Context and evidence

Legacy projects contain persisted relative paths, filenames used as identity,
import sources, script-owned files, editor metadata, runtime secrets, and output
trees. These strings are untrusted host-path input. A lexical `starts_with`
check does not prevent traversal, Windows drive/device/UNC paths, alternate data
streams, reserved names, case or normalization collisions, or link swaps.
Clone, backup, migration, publication, import, rename, delete, preview, and
recovery can otherwise escape the selected project or copy disallowed state.

**Correction from implementation evidence (2026-07-20).** The P05 scanner can
reject a pre-existing hardlink before reading its content. A synchronized Linux
fixture also created and removed a hardlink during an ordinary unprivileged
read: the inode `ctime` changed, the scan failed, and no inventory or digest was
accepted or published, but the kernel may already have returned file bytes
before the post-read check detected the race. This evidence corrects the earlier
overbroad implication that ordinary file APIs can guarantee literal no-read
against a fully transient concurrent alias. It does not relax the outside-root
boundary or permit raced bytes to become trusted output.

At this revision P05 reports no usable transient change-time signal for the
Windows backend. Windows can still reject a multiply linked file observed by
the pre-read check, but it has not proved detection when an alias is created and
removed wholly within the read window. Consequently neither an ordinary
transient-race guarantee nor a strong no-read guarantee is inferred on Windows.

## Decision

Every project filesystem action begins with a canonical, opened project-root
capability. Callers pass typed project-relative paths, not host path strings.
Resolution rejects:

- absolute, drive-relative, UNC, device, and extended-length forms;
- `.`/`..` traversal, embedded separators in a path component, empty forbidden
  components, reserved names, trailing-dot/space aliases, and alternate data
  streams;
- case-folding or normalization collisions within an operation;
- source/destination aliases by file identity.

Inventory and all mutation/projection operations do not follow symlinks,
junctions, reparse points, or mount links by default; validation and preview do
not read through them. A regular file whose observed link count is greater than
one is treated as an unresolved hardlink and its content is not
read, hashed, previewed, copied, exported, or mutated by default. The scanner
may report only non-content metadata needed to diagnose that refusal. This
conservative rule avoids claiming that every other hardlink can be enumerated
and proven inside the project on all supported filesystems. Any future opt-in
must prove the complete alias set and private-copy breaking without content
disclosure or cross-boundary mutation.

Every content-read phase that lacks independently proven strong authority uses
the same baseline `SpeculativeReadGate`. This includes inventory parsing and
validation, recovery capture and mutation preimages, hashing, previews,
diagnostic content, clone/export/publication/projection source reads, import
inspection, and copy sources. The portable baseline opens through the no-follow root capability,
captures file identity, link count, and size before content access, reads only
into bounded process-private scratch state, then repeats identity/link-count/size
observations before releasing any result. It rejects a multiply linked file at
either check and rejects any identity, link-count, or size mutation.

`SpeculativeReadGate::TransientRaceDetection` is a separate, optional capability
layer. It adds a platform/filesystem change-time token and relevant namespace
signal proven by synchronized tests to change when an alias or path is created
and removed while restoring the baseline identity, link count, and size. The
layer repeats those signals after the read and rejects any change. A backend may
advertise this capability only for the exact filesystem/platform combinations
that pass the race suite; baseline success never implies it.

Until the post-read checks pass, raw bytes, parsed values, digests, previews,
diagnostic excerpts, and copy/projection artifacts remain quarantined behind a
side-effect-free gate: they are not persisted, named in a filesystem, sent to a
callback or plugin, cached, logged, rendered, journaled, or used to mutate model
or project state. If an operation cannot buffer its speculative result without
such a side effect, it requires strong authority or is unsupported. Every
detected baseline identity/link-count/size mutation, or any change detected by
`TransientRaceDetection`, zeroizes/discards the scratch result and fails the
operation before accepted, persisted, or published output. A multiply linked
file observed by either check is denied.
The same rule applies when the transient link has already been removed and the
post-read link count is again one but `TransientRaceDetection` observes its
change signal. Only a generic, non-content failure diagnosis may be emitted
after quarantine disposal.

A workflow that requires the stronger literal guarantee that no bytes can be
read while a transient outside-root alias exists must first establish an
independently proven immutable/private intake, or a platform snapshot/freeze
whose semantics exclude link creation and removal for the full access window.
The proof and authority must precede content access and remain held through
acceptance. If the platform cannot provide or executable tests cannot prove
that primitive, the strong content-read capability is unsupported; an ordinary
speculative read may not be relabeled as strong mode.

Clone, migration, publication, and external import destinations use separately
selected root capabilities under the same rules. Promotion uses already
validated directory/file handles where the platform permits and rechecks file
identity immediately before replacement; it does not resolve the original
untrusted string again.

ADR-0005 consumes this contract in two distinct phases. Original/recovery
capture and mutation-preimage reads must pass `SpeculativeReadGate` before bytes
may enter a recovery artifact or before `RecoveryDurable` can be recorded. A
detected race discards the preimage and aborts without promotion. After an
accepted preimage, ADR-0005's `ReplaceAuthority` independently rechecks the exact
identity/fingerprint and remains held across authorization, replacement, and
directory durability. Thus this gate prevents speculative recovery bytes from
escaping, while `ReplaceAuthority` prevents a later change from being lost; one
mechanism does not substitute for the other.

The Linux `ctime` behavior observed by P05 supports the optional transient-race
detection contract on that tested filesystem. P05 has not established an
equivalent Windows signal, so `SpeculativeReadGate::TransientRaceDetection` is
classified `Unsupported` on Windows until executable evidence changes that
record. The reduced Windows baseline may perform non-content inventory, reject
an observed pre-existing or post-read hardlink, and reject identity/link-count/
size mutations with no accepted or published result. Capability reports expose
`BaselineReadGate=Available`, `TransientRaceDetection=Unsupported`, and whether
strong authority is available; they never collapse these into a generic safe
read label.

On Windows, content-producing inventory/validation, recovery or mutation-
preimage capture, hashing, preview, diagnostic extraction, clone, export,
publication/projection, import inspection, and copy-source reads require either
proven `TransientRaceDetection` or independently proven strong authority when
the hostile transient-alias threat is in scope. Without either, that operation
is unavailable. A detected baseline mutation still fails closed with all raw
and derived output discarded, but this does not imply detection of a fully
transient create-and-remove race.

## Alternatives considered

1. Canonicalize a path and compare string prefixes. Rejected because time-of-
   check/time-of-use link swaps and platform aliases remain.
2. Follow links during read-only inventory. Rejected because previews, hashes,
   and diagnostics would disclose outside-root data and later output could copy
   it.
3. Trust paths from known legacy formats. Rejected because project files and
   imported archives are attacker-controlled inputs.
4. Claim ordinary pre/post metadata checks prevent every transient read.
   Rejected because implementation evidence proves they detect and discard a
   synchronized race but cannot retract bytes already returned by the kernel.

## Consequences

- Some legacy projects with intentional links open with diagnostics and linked
  content remains unavailable by default.
- Every filesystem service, including scanner and storage recovery, shares one
  root-capability boundary.
- External file selection is explicit and separate from project-relative
  resolution.
- Platform backends must prove Windows reparse and alias behavior as well as
  Unix symlink/mount behavior.
- Every unauthoritative content-read phase uses one side-effect-free speculative
  quarantine contract, not a scanner-specific approximation.
- On a backend with proven transient-race detection, the layered gate
  guarantees that detected races produce no accepted or published content
  result; it does not claim literal no-read for a transient alias.
- Windows transient-race detection is currently `Unsupported`; only the reduced
  baseline capabilities are reported, and no transient or strong guarantee is
  inferred from observed pre-read denial.
- Strong no-read workflows require a separately proven immutable intake or
  snapshot/freeze capability and remain unavailable where none exists.

## Invariants

- No project-supplied string becomes an ambient host path.
- No default scan, read, preview, hash, copy, or write follows a link.
- Regular files observed with multiple links are denied before content access
  by default, including for hashing, preview, diagnostics, clone, and
  publication.
- Any baseline identity/link-count/size mutation or optional transient change-
  time/namespace signal invalidates and discards speculative bytes and digests
  before accepted, persisted, or published output.
- No raw or derived speculative result crosses a persistence, callback, plugin,
  cache, log, render, journal, model, or project-state boundary before all
  post-read checks pass.
- Literal no-read under a fully transient alias is claimed only while an
  independently proven immutable/private intake or snapshot/freeze authority is
  held; otherwise that capability is unsupported.
- A checked target cannot silently change identity before mutation.
- Source and destination capabilities cannot resolve to the same object.
- Operations enumerate exact owned paths; rollback never deletes paths it did
  not create or whose current fingerprint has changed.

## Verification and exit evidence

Hostile fixtures exercise traversal, absolute/UNC/device/ADS forms, reserved
names, mixed separators, case and Unicode normalization collisions, symlinks,
junctions, reparse points, mount links, hardlinks, link swaps, and aliasing
source/destination roots. Unique outside-root canaries prove no content is read,
hashed, copied, modified, or disclosed for links present at the pre-read check.
Tests run on Windows and a supported Unix platform with explicit filesystem and
tool versions. Hardlink canaries cover an alias outside the root and aliases
wholly inside it; both are denied before read while observed multiply linked.

A synchronized Linux race creates and removes a transient hardlink during each
operation-specific read gate: inventory/validation, recovery capture, mutation
preimage, standalone hash, preview, diagnostic extraction, clone, export,
publication/projection, import inspection, and copy source. Each fixture proves
the `ctime` change is detected; raw and derived scratch results are discarded;
no callback/plugin observes them; and no inventory, recovery artifact,
`RecoveryDurable` state, digest, preview, diagnostic content, cache, copy,
projection, model mutation, or other output is accepted, persisted, or
published. Recovery tests then prove a successful gate is still followed by
ADR-0005 fingerprint recheck under `ReplaceAuthority`, and that a hostile change
between those phases aborts promotion.

The synchronized Windows equivalent is retained as a pending evidence fixture,
but P05 currently reports no usable change-time signal. Tests therefore assert
that `SpeculativeReadGate::TransientRaceDetection` is `Unsupported`, operations
requiring it do not run without strong authority, and UI/API capability reports
explicitly distinguish the available baseline rejection checks from unsupported
transient-race detection and do not infer transient-safe or strong guarantees.
If a later backend
finds a candidate signal, the full operation matrix above must pass before the
classification changes.
Strong-mode tests separately prove the immutable/private-intake or snapshot/
freeze gate is established before the first read, rejects concurrent alias
creation for the entire access window, and fails closed as unsupported when the
primitive is absent or loses authority.

## Dependencies

- [Project-format matrix state classes](../compat/project-format-matrix.md)
- [Canonical specification security boundary](../plans/plan/rust-super-editor-engine-migration.md#9-project-root-confinement-is-a-security-boundary)
- Downstream consumer (not an ADR-0003 dependency):
  [ADR-0005](0005-durable-journal-and-recovery.md) consumes this read-gate
  contract and adds independent check-to-replace authority.

## Supersession and change process

Any exception that permits link following, host paths, or hardlinked mutation
requires a new threat-model ADR, explicit user authority, output allowlist
effects, and hostile cross-platform tests. Convenience alone is insufficient.
