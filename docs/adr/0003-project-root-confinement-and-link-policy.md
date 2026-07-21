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
junctions, reparse points, or mount links by default. Validation and preview do
not read linked outside-root content. A regular file whose observed link count
is greater than one is treated as an unresolved hardlink and its content is not
read, hashed, previewed, copied, exported, or mutated by default. The scanner
may report only non-content metadata needed to diagnose that refusal. This
conservative rule avoids claiming that every other hardlink can be enumerated
and proven inside the project on all supported filesystems. Any future opt-in
must prove the complete alias set and private-copy breaking without content
disclosure or cross-boundary mutation.

Clone, migration, publication, and external import destinations use separately
selected root capabilities under the same rules. Promotion uses already
validated directory/file handles where the platform permits and rechecks file
identity immediately before replacement; it does not resolve the original
untrusted string again.

## Alternatives considered

1. Canonicalize a path and compare string prefixes. Rejected because time-of-
   check/time-of-use link swaps and platform aliases remain.
2. Follow links during read-only inventory. Rejected because previews, hashes,
   and diagnostics would disclose outside-root data and later output could copy
   it.
3. Trust paths from known legacy formats. Rejected because project files and
   imported archives are attacker-controlled inputs.

## Consequences

- Some legacy projects with intentional links open with diagnostics and linked
  content remains unavailable by default.
- Every filesystem service, including scanner and storage recovery, shares one
  root-capability boundary.
- External file selection is explicit and separate from project-relative
  resolution.
- Platform backends must prove Windows reparse and alias behavior as well as
  Unix symlink/mount behavior.

## Invariants

- No project-supplied string becomes an ambient host path.
- No default scan, read, preview, hash, copy, or write follows a link.
- Multiply linked regular files are content-inaccessible by default, including
  for read-only hashing, preview, diagnostics, clone, and publication.
- A checked target cannot silently change identity before mutation.
- Source and destination capabilities cannot resolve to the same object.
- Operations enumerate exact owned paths; rollback never deletes paths it did
  not create or whose current fingerprint has changed.

## Verification and exit evidence

Hostile fixtures exercise traversal, absolute/UNC/device/ADS forms, reserved
names, mixed separators, case and Unicode normalization collisions, symlinks,
junctions, reparse points, mount links, hardlinks, link swaps, and aliasing
source/destination roots. Unique outside-root canaries prove no content is read,
hashed, copied, modified, or disclosed. Tests run on Windows and a supported
Unix platform with explicit filesystem and tool versions. Hardlink canaries
cover an alias outside the root, aliases wholly inside it, and a concurrent link
creation; all remain content-inaccessible under the default policy.

## Dependencies

- [Project-format matrix state classes](../compat/project-format-matrix.md)
- [Canonical specification security boundary](../plans/plan/rust-super-editor-engine-migration.md#9-project-root-confinement-is-a-security-boundary)

## Supersession and change process

Any exception that permits link following, host paths, or hardlinked mutation
requires a new threat-model ADR, explicit user authority, output allowlist
effects, and hostile cross-platform tests. Convenience alone is insufficient.
