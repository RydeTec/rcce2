# RCCE compatibility corpus policy

- **Packet:** `SE-M0-P04`
- **Canonical task:** 4
- **Manifest:** [`manifest.toml`](manifest.toml)
- **Machine schema:** [`schema-v1.json`](schema-v1.json)
- **Evidence base:** repository commit `2d09d0992757a6beb9acf0a77978fdd5637a4006`
- **Initial state:** policy and unavailable/pending declarations only; no project snapshot is present

## Purpose

This directory is the chain-of-custody boundary for project snapshots used to
prove legacy compatibility. A fixture here is a copied and sanitized derivative,
not a creator's working project and not permission to redistribute its source.
The corpus has three named scales—small, default, and large—so correctness and
performance claims can identify the exact evidence they exercised.

The initial manifest deliberately declares all three entries unavailable or
pending. The repository's `data/` tree is evidence about the default project, but
it is not an approved corpus fixture: this repository has no tracked root license
file at the evidence commit, and no sanitization or redistribution review has
been recorded. No private or third-party project was copied for this packet.

## Non-negotiable boundary

```text
creator or repository source (never tested in place)
    -> isolated no-follow intake copy (not a test fixture)
        -> reviewed, sanitized, hashed corpus snapshot (immutable source)
            -> per-test disposable workspace (the only writable copy)
```

| Boundary | May be read | May be written | Persistent role |
|---|---|---|---|
| Original source | Only during an authorized intake | Never | Creator/repository authority |
| Intake copy | By the bounded sanitizer and reviewer | Sanitizer only | Temporary quarantine; never committed |
| Corpus snapshot | By scanners and tests | Never | Immutable evidence source |
| Test workspace | By the test that created it | Yes | Disposable; removed after evidence collection |

Tests never mutate the source corpus and never receive a writable corpus root.
Every test that needs mutation first
copies one declared `ready` fixture into a fresh temporary directory outside this
directory, rejects links during that copy, and writes only through that disposable
root. Test setup records the source tree hash, verifies the copied tree, and the
test harness re-hashes the corpus source after execution. A changed source hash is
a test failure even when the functional assertion passed. Hard-linking, reflinking
without guaranteed copy-on-write isolation, overlaying, or bind-mounting a corpus
source as a writable workspace does not satisfy this rule.

## Fixture lifecycle

An entry's `availability` is one of:

| Value | Meaning |
|---|---|
| `unavailable` | No candidate with adequate provenance, consent, or scope has been selected. Its fixture directory must be absent. |
| `pending-sanitization` | A candidate source is named, but its reviewed derivative does not exist. Its fixture directory must be absent. |
| `quarantined` | An intake copy exists outside the repository and is under review. Its fixture directory must be absent. |
| `ready` | The copied derivative, complete file inventory, hashes, reviews, and permissions are present and agree. |
| `withdrawn` | Former fixture bytes have been removed because consent, license, provenance, or sensitivity approval no longer holds. |

Only `ready` entries are test inputs. A scanner treats a directory for any other
state as an undeclared artifact and fails closed. Moving to `ready` requires:

1. immutable source identity and provenance;
2. affirmative consent and redistribution evidence for the exact bytes and use;
3. license review, including every bundled third-party asset;
4. a no-follow intake and complete state/sensitivity classification;
5. approved sanitization with a transform log;
6. a complete regular-file inventory and byte hashes;
7. named compatibility expectations grounded in the format matrix; and
8. independent reviewer identity and review time.

A consent or license withdrawal moves the entry to `withdrawn` and removes its
fixture bytes in a separately reviewed, recoverable change. The manifest keeps a
non-sensitive withdrawal reason, evidence references, reviewer and UTC review
time plus the former aggregate hash and byte/file counts. Historical reports may
retain that same fixture ID and former evidence identity, but not the withdrawn
bytes or a private source locator.

### Public-history consent and withdrawal limits

A fixture may enter this public Git repository only when consent explicitly
covers permanent public version-control history for the exact sanitized bytes and
is not revocable for copies already made. Approval acknowledges that commits,
forks, clones, caches, mirrors, release archives, CI artifacts, and downstream
copies may remain after the file disappears from the current tree. A project
offered under revocable consent is never committed here; it belongs in separately
authorized access-controlled external fixture storage with its own retention and
deletion enforcement.

`withdrawn` therefore means unavailable from the current corpus tree and future
normal test runs. It does not claim erasure from immutable history or third-party
copies. A legal demand or security exposure escalates beyond ordinary withdrawal:
stop distribution and tests, quarantine evidence, rotate/invalidate any exposed
credential, coordinate repository-history and hosted-cache/artifact purges,
notify known downstream custodians, and record what could and could not be
removed. History rewriting is a separate destructive operation requiring its own
authority, and no policy promises deletion from unknown clones.

## Scale definitions and checkout budgets

These are corpus-storage ceilings, not product-performance targets. A source that
exceeds a ceiling is not silently trimmed; the manifest budget is changed with
evidence or a smaller, still representative snapshot is separately consented.

| Tier | Intended evidence | Maximum bytes | Maximum regular files | Maximum one file |
|---|---|---:|---:|---:|
| `small` | Fast structural, malformed, raw-byte, and focused semantic tests | 4 MiB | 256 | 2 MiB |
| `default` | Sanitized derivative of the shipped example project and broad consumer smoke | 512 MiB | 4,096 | 32 MiB |
| `large` | Consented high-cardinality/media/performance behavior | 4 GiB | 50,000 | 512 MiB |

Enumeration limits are also tier-specific:

| Tier | Directories | Nesting depth | UTF-8 path bytes | UTF-8 component bytes | Canonical project-manifest bytes |
|---|---:|---:|---:|---:|---:|
| `small` | 128 | 12 | 1,024 | 255 | 256 KiB |
| `default` | 2,048 | 32 | 2,048 | 255 | 8 MiB |
| `large` | 20,000 | 64 | 4,096 | 255 | 64 MiB |

Before parsing, the scanner rejects `manifest.toml` when filesystem metadata says
it exceeds the schema's absolute 64 MiB envelope. After schema validation, it
serializes each parsed project entry as compact canonical JSON (UTF-8, keys sorted,
no insignificant whitespace) and applies that entry's manifest-byte ceiling.
This measurement is independent of TOML comments or formatting.

Before opening any fixture file body, no-follow enumeration uses directory-entry
and `lstat`/reparse metadata to account for directory count, depth relative to the
fixture root, UTF-8 encoded path/component lengths, regular-file count, declared
file sizes, aggregate bytes, and largest file. Every limit is checked before
descending into the entry or opening its content; the first excess fails closed.
Only after a complete link-safe enumeration fits all ceilings may hashing or
format parsing open regular-file bodies. Counters use checked unsigned arithmetic,
so overflow is rejection rather than wraparound.

The fixture root is depth zero and is not included in `max_directories`; each
descendant directory counts once, and an immediate child is depth one.
`max_path_bytes` measures the complete fixture-relative portable path encoded as
UTF-8 with `/` separators. `max_component_bytes` measures each individual UTF-8
name without a separator. The canonical project-manifest measurement includes the
entire parsed project object—files, artifacts, canaries, and reviews—not the
top-level envelope or sibling project entries.

The checked-in `data/` candidate was measured at the evidence commit as
351,551,965 regular-file bytes across 1,180 files, so it fits the declared
default ceiling. That measurement is not a fixture hash and does not promote the
pending entry. Performance packets separately record reference hardware, actual
materialized sizes, memory budgets, and timings; a tier name alone proves none of
those things.

## Intake and sanitization record

Intake is a bounded copy, never a scan of an arbitrary host parent. The operator
selects one source root, records its stable identity, and copies regular files
without following or materializing links. The intake rejects before reading a
target when it encounters:

- symbolic links, Windows junctions/reparse points, mount links, or bind mounts;
- hard-linked files (`link count > 1`) unless first copied into private regular
  files by a separately reviewed intake mechanism;
- absolute, drive-relative, UNC/device, alternate-data-stream, traversal,
  reserved-name, embedded-separator, or normalized/case-colliding identities;
- sockets, devices, FIFOs, or any filesystem object other than a directory or
  regular file; or
- a path or file that exceeds the entry's declared budget.

Rejection records only bounded metadata needed for diagnosis. It never opens the
linked target, copies outside-root bytes, or substitutes the target's contents.
The P05 scanner and hostile suite provide executable proof of this rule; this
policy does not claim that proof exists yet.

Sanitization happens in quarantine and produces a new tree. It does not rewrite
the original or the future immutable fixture in place. Every transform has an ID,
affected raw path, state class, reason, method/version, and before/after byte
hashes in the intake review record. The public manifest records the non-sensitive
transform ID and sanitized hash; private source hashes or locators remain outside
the repository when consent does not cover their disclosure.

### Sensitive values and canaries

The review searches both recognized fields and opaque bytes for credentials,
account/password verifiers, database connection material, tokens, private keys,
email addresses, personal names, public IPs, absolute host paths, chat/log/script
output, and creator-specific identifiers. Filename and metadata channels are
reviewed as well as file bodies.

- Real `Secret` bytes are never committed.
- Secret-bearing files are either omitted or transformed with a format-aware
  sanitizer that preserves required record topology and unrelated bytes.
- Replacement values are deterministic, non-operational test values. They carry
  fixture-unique canary IDs from the P07 canary registry so projection tests can
  prove inclusion or exclusion without embedding real credentials.
- Each manifest canary record names its fixture file, `Secret` or
  `DynamicPrivate` state class,
  expected occurrence bounds, and a permission-policy ID in the versioned P07
  registry. The registry—not prose or a guessed filename—defines permitted and
  forbidden projections. The manifest does not call a password, token, DSN, or
  hash "sanitized" merely because it looks synthetic.
- A canary is public test data, never accepted by a real service, and never reused
  between fixture roles. An unexpected occurrence or missing required occurrence
  fails the corpus scan.

The initial manifest has no canary records because P07 has not seeded its registry
and no fixture bytes exist. Empty canary lists are valid only while an entry is not
`ready`, or when an approved review proves the ready fixture contains no
secret/dynamic-state test case.

### Raw bytes, malformed data, and unknown artifacts

Sanitization is byte-aware. It does not decode and re-encode an entire text or
binary document merely to replace one field. Unrelated non-UTF-8 bytes, BOM,
line endings, trailing newline state, embedded NUL/control bytes, float bit
patterns, sparse cells, aliases, gaps, orphan records, unknown chunks, and
malformed tails remain byte-identical unless a logged transform explicitly owns
them.

Content hashing is over raw bytes. Persisted path/name strings inside legacy
documents are content and retain their exact bytes. If the sanitizer cannot
identify and replace sensitive content without destroying unknown topology, the
artifact is excluded rather than normalized.

Checked-in corpus filenames themselves use portable UTF-8 so the same manifest
can be checked out on Windows and Unix. A source with a non-UTF-8 or otherwise
non-portable filesystem name is not renamed and presented as identity-preserved;
it remains unavailable with the limitation recorded until a platform-specific
fixture strategy is accepted. Hostile filesystem-name tests belong to P05's
generated, disposable suite rather than a misleading cross-platform snapshot.

Malformed and partial artifacts are useful evidence and are not repaired during
intake. A typed artifact record identifies the owning manifest file, exact byte
range, diagnostic, boundary meaning, and evidence. `non-utf8-content` refers only
to bytes inside that file; corpus filesystem names remain portable UTF-8 as
described above. Each artifact starts at the compatibility level actually
evidenced—normally `I0 Inventory`. An unknown format is retained only after a
byte-level sensitivity/license review; otherwise it is omitted and the omission
is recorded. Neither retention nor a passing tolerant parser authorizes a write.

## State-class handling

State-class names are exactly those in
[`docs/compat/project-format-matrix.md`](../../docs/compat/project-format-matrix.md):

| State class | Corpus handling |
|---|---|
| `PublicClient` | Eligible after provenance/license review and ordinary sensitivity review. |
| `ServerConfig` | Eligible only after hostnames, keys, DSNs, paths, and deployment-specific values are replaced or absent. |
| `Secret` | Real bytes forbidden. Only explicitly declared, non-operational P07 canaries may appear. |
| `DynamicPrivate` | Excluded by default; eligible only as a separately consented, fully synthetic/sanitized test case with a declared purpose. |
| `EditorMetadata` | Eligible only when its host paths, project identity, schema ownership, and copy semantics are the subject of the fixture. |
| `AuthoringSource` | Eligible when creator consent and every embedded asset license permit repository test redistribution. |
| `Unknown` | Fail closed until sensitivity and redistribution review; if retained, preserve opaque bytes and expect no more than the evidenced level. |

Every ready file has one primary class and may list additional classes when the
format matrix says the family spans authorities. No class is inferred solely from
extension. Recovery files inherit the class and sensitivity of their owning final
file. Projection outputs are not source fixtures unless a packet explicitly
needs them and declares their source snapshot and output allowlist.

The only class values are, in canonical order: `PublicClient`, `ServerConfig`,
`Secret`, `DynamicPrivate`, `EditorMetadata`, `AuthoringSource`, and `Unknown`.
Both project-level class arrays are unique and sorted in that order. For every
entry, `included_state_classes` is exactly the union of every file's primary and
additional classes; an entry with no files therefore has an empty included array.
`excluded_state_classes` is not the exhaustive complement. It is the
policy-selected subset deliberately forbidden from that snapshot, and it must be
disjoint from the included union. Unlisted classes are merely absent, not proven
safe, required, or intentionally excluded.

## Manifest contract

`manifest.toml` is UTF-8 TOML with `schema_version = 1`. Unknown top-level,
project, nested-status, file, artifact, canary, or byte-range keys are rejected
for this version so a misspelled security field cannot be ignored. Parsers reject
duplicate project IDs, fixture roots, file paths, and case/normalization-colliding
paths.

[`schema-v1.json`](schema-v1.json) is the machine-readable authority for every
key, primitive type, required field, closed object, status branch, and
availability/tier conditional in schema v1. TOML is decoded to the equivalent
JSON data model and validated against that local schema before any fixture root is
enumerated. The scanner additionally enforces cross-record constraints that JSON
Schema cannot express: unique IDs/paths, references, canonical class order,
class unions/disjointness, range ordering/bounds, occurrence ordering, transform
membership, exact aggregate counts, and the tree digest.

Each `[[projects]]` entry always includes:

- stable `id`, exactly one `tier`, `availability`, and project-relative
  `fixture_root` beneath this directory;
- purpose, expected consumers, expected compatibility declarations, and size
  ceilings;
- provenance, license, consent, sensitivity-review, sanitizer, and hash status;
- included/excluded state classes and closed `files`, `artifacts`, and `canaries`
  record arrays, even when those arrays are empty;
- one `canary_registry` reference and one `withdrawal` record.

Nested status values are closed vocabularies in schema v1. `provenance.status`
is `unavailable`, `candidate-identified`, or `verified`; `license.status`,
`consent.status`, and `sensitivity_review.status` are `pending`, `approved`, or
`rejected`; `sanitization.status` is `not-started`, `in-review`, `approved`, or
`rejected`; and `hashes.status` is `unavailable`, `verified`, or `withdrawn`. An
`approved` review names its reviewer, UTC review time, and evidence; a `verified`
hash set contains the required counts and digest. Placeholder strings such as
`unassigned` and `unavailable` are permitted only while the enclosing entry is not
`ready`.

The complete nested-object branches are summarized below; the linked schema fixes
their JSON/TOML types, enum values, minimums, formats, and
`additionalProperties = false` rules.

| Object and status | Exact keys beyond the object name |
|---|---|
| `budget` | `max_bytes`, `max_files`, `max_single_file_bytes`, `max_directories`, `max_depth`, `max_path_bytes`, `max_component_bytes`, `max_manifest_bytes`—all positive integers and exact tier constants |
| `provenance.unavailable` | `status`, `source_kind`, `source_locator`, `unavailable_reason` |
| `provenance.candidate-identified` | `status`, `source_kind`, `source_locator`, `source_revision`, non-negative `source_bytes`, non-negative `source_files` |
| `provenance.verified` | Candidate keys plus non-empty `evidence`, `reviewed_by`, `reviewed_at_utc`; file count is positive |
| `license.pending` | `status`, `spdx`, `redistribution = "not-approved"`, `evidence` |
| `license.approved` | `status`, `spdx`, approved `redistribution`, `scope`, non-empty `evidence`, `reviewed_by`, `reviewed_at_utc` |
| `license.rejected` | `status`, `spdx`, `redistribution = "not-approved"`, `reason`, non-empty `evidence`, `reviewed_by`, `reviewed_at_utc` |
| `consent.pending` | `status`, `scope = "none"`, `evidence` |
| `consent.approved` | `status`, `scope`, `history_scope = "permanent-public-vcs"`, `revocable = false`, opaque `subject_id`, non-empty `evidence`, `approved_by`, `approved_at_utc` |
| `consent.rejected` | `status`, `scope = "none"`, `reason`, non-empty `evidence`, `reviewed_by`, `reviewed_at_utc` |
| `sensitivity_review.pending` | `status`, `evidence` |
| `sensitivity_review.approved` | `status`, `method`, non-empty `evidence`, `reviewed_by`, `reviewed_at_utc` |
| `sensitivity_review.rejected` | `status`, `reason`, non-empty `evidence`, `reviewed_by`, `reviewed_at_utc` |
| `sanitization.not-started` | `status`, `sanitizer_id`, unique `transform_ids` |
| `sanitization.in-review` | `status`, `sanitizer_id`, `sanitizer_version`, unique `transform_ids`, non-empty `evidence` |
| `sanitization.approved` | In-review keys plus `reviewed_by`, `reviewed_at_utc` |
| `sanitization.rejected` | `status`, `sanitizer_id`, `reason`, unique `transform_ids`, non-empty `evidence`, `reviewed_by`, `reviewed_at_utc` |

`expected_consumers` is a non-empty, unique array of stable lowercase IDs.
`expected_compatibility` is a non-empty, unique array of closed
`{ selector, level, evidence }` records. A non-ready placeholder may use selector
`*`; a ready entry names only exact `PF-...` rows. Every evidence string is
non-empty. The schema requires exactly one small, default, and large entry;
semantic uniqueness of their IDs and roots is checked separately.

The canary-registry record has exactly one of two closed shapes:

- `unavailable`: `status` only;
- `verified`: `status`, `id`, `version`, and `evidence` only.

For `verified`, the registry ID is non-empty, version is a positive integer, and
evidence is non-empty. Every canary's `registry_id` and `permission_policy_id`
must resolve in that exact version.

The withdrawal record likewise has exactly one of two closed shapes:

- `not-withdrawn`: `status` only;
- `withdrawn`: `status`, `reason`, `evidence`, `reviewed_by`, and
  `reviewed_at_utc` only.

For `withdrawn`, reason, evidence, and reviewer are non-empty and the review time
is RFC 3339 UTC. `availability = "withdrawn"` if and only if
`withdrawal.status = "withdrawn"`; every other availability uses the status-only
`not-withdrawn` shape.

Conditional rules keep placeholders honest:

- `unavailable` and `pending-sanitization` entries have no fixture directory,
  aggregate hash, or file records; their `hashes.status` is `unavailable`.
- `quarantined` entries expose no quarantine locator or source secrets here.
- `ready` entries require `materialized_bytes`, `materialized_files`, and
  `tree_sha256` in their hash record, at least one `[[projects.files]]` record,
  affirmative license, consent, and sensitivity reviews, and non-empty evidence
  references. Their
  included state-class list equals the union of their file records, the excluded
  list is disjoint as defined above, and their
  compatibility declarations name exact format rows rather than the initial `*`
  placeholder.
- `withdrawn` entries have no fixture directory or file/artifact/canary records.
  Their `withdrawal.status` is `withdrawn`; their hash record retains the former
  aggregate hash and former byte/file counts with explicit `former_` field names.

The hash record has exactly one of these closed shapes:

- `unavailable`: `status`, `algorithm` only;
- `verified`: `status`, `algorithm`, `tree_sha256`, `materialized_bytes`, and
  `materialized_files`;
- `withdrawn`: `status`, `algorithm`, `former_tree_sha256`,
  `former_materialized_bytes`, and `former_materialized_files`.

Digests are 64 lowercase hexadecimal characters and counts are non-negative
integers. For `verified`, the file count equals the number of file records, bytes
equal their size sum, and the tree digest recomputes from them. For `withdrawn`,
the former file count is positive and the former values match the last verified
record cited by the withdrawal evidence; they describe removed bytes and do not
assert those bytes still exist. `ready` pairs only with `verified`; `withdrawn`
pairs only with `withdrawn`; every other availability pairs with `unavailable`.

A file record contains:

```toml
[[projects.files]]
id = "misc-dat"
path = "Data/Game Data/Misc.dat"
size_bytes = 123
sha256 = "<64 lowercase hex characters>"
primary_state_class = "AuthoringSource"
additional_state_classes = []
sensitivity = "reviewed-clear" # reviewed-clear | sanitized | synthetic-canary
expected_level = "I0 Inventory"          # I0..I4 vocabulary from the matrix
format_rows = ["PF-CFG-001"]
transform_ids = []
```

The explicit primary class makes classification deterministic; additional classes
are unique, exclude the primary, and are ordered lexically. `id` is unique within
the project and is the target of artifact and canary references. All shown file
keys are required;
`sensitivity` is exactly `reviewed-clear`, `sanitized`, or `synthetic-canary`.

Artifact evidence uses a closed record:

```toml
[[projects.artifacts]]
id = "misc-malformed-tail"
file_id = "misc-dat"
kind = "malformed" # malformed | unknown-format | non-utf8-content
byte_ranges = [{ start = 117, end_exclusive = 123 }]
diagnostic = "Parser stops at an incomplete final field; no repair is authorized."
boundary = "Known prefix ends at byte 117; bytes 117..123 are retained opaque."
evidence = ["<source, test, or review reference>"]
```

Ranges are in bounds, ordered, and non-overlapping. Normally `start` is less than
`end_exclusive`; a zero-width range is permitted only at EOF to represent a
truncation boundary whose expected missing bytes are named by the diagnostic.
`malformed` requires the failing boundary; `unknown-format` covers the full file
or names the known/unknown boundary; `non-utf8-content` names non-empty exact
content byte ranges and its diagnostic records the attempted or known
interpretation. Evidence is never empty.

Canary placement uses another closed record while the marker and projection
permissions remain owned by the versioned P07 registry:

```toml
[[projects.canaries]]
id = "synthetic-account-verifier"
registry_id = "rcce-p07-canaries"
permission_policy_id = "secret-never-project"
file_id = "accounts-dat"
state_class = "Secret"
expected_min_occurrences = 1
expected_max_occurrences = 1
```

`state_class` is exactly `Secret` or `DynamicPrivate`; occurrence bounds are
positive integers with minimum no greater than maximum. The referenced file's
`sensitivity` is `synthetic-canary`.

Within one project, file, artifact, and canary IDs are unique in their respective
namespaces. File paths are unique. Every artifact/canary `file_id` resolves to one
file; every file `transform_id` resolves in `sanitization.transform_ids`; every
canary's class occurs in its file classification and its registry/policy resolves
at the declared registry version. Artifact IDs listed in review/test evidence and
canary IDs reported by the scanner must resolve exactly once. Undeclared files,
artifacts, canaries, transforms, and unexpected canary occurrences fail closed.

Paths are project-relative portable UTF-8, use `/` as the manifest separator,
and must not contain `.`, `..`, an absolute root, an empty component, a Windows
reserved name, a trailing dot/space, a colon, backslash, control character, or
NUL. The scanner uses the exact manifest string only after root-confined path
validation and rejects normalization/case collisions; it does not repair names.

### Hashes

Every regular file uses lowercase SHA-256 over its exact bytes. The aggregate
`tree_sha256` is SHA-256 over this byte stream:

1. ASCII domain `RCCE-CORPUS-TREE-V1` followed by NUL;
2. file records sorted by their UTF-8 path bytes;
3. for each record: path length as unsigned 64-bit little endian, UTF-8 path bytes,
   size as unsigned 64-bit little endian, and the 32 decoded digest bytes.

Directories, timestamps, permissions, and TOML ordering are not
hashed. Links and non-regular objects are rejected, not hashed. The scanner
verifies membership both ways: every manifest file exists exactly once and every
filesystem regular file below the fixture root is declared.

## Refresh and review

A refresh creates a new fixture ID or explicit manifest revision; it never
silently replaces bytes behind an accepted hash. The change records source
revision, intake/sanitizer versions, file and aggregate hash deltas, state-class
and compatibility expectation deltas, license/consent evidence, and why the new
sample is more representative. One author performs intake; a different reviewer
checks chain of custody, licenses, sensitivity, link rejection, transforms,
manifest membership, and hashes before `ready` is accepted.

Review expires immediately when new evidence invalidates consent, license, or
distribution authority; a scanner finds an undeclared file/canary; the fixture
hash changes; or a new sensitive interpretation applies to retained unknown
bytes. Ordinary source-project changes do not mutate an existing fixture: they
propose a new reviewed snapshot.

## What the initial manifest proves

It proves only that the three corpus roles, safety fields, compatibility
expectations, closed schema, and storage/enumeration ceilings are declared without
inventing or copying fixtures. It does not prove scanner behavior, sanitization,
format compatibility, client/server/editor consensus, or permission to
redistribute any project. Those claims remain gated on later ready entries and
executable evidence.
