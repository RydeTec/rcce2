# RCCE state classification and output-allowlist matrix

- **Packet:** `SE-M0-P07`
- **Matrix version:** `1.0.0`
- **Canonical task:** 7
- **Evidence base:** repository commit `a33cb8cb512820580e1d89f39862b7a75fbe08db`
- **Machine authority:** [`registry-v1.toml`](../../test-data/canaries/registry-v1.toml), `registry_id = "rcce-p07-canaries"`, version 1
- **Inspected:** 2026-07-20

## Purpose and evidence boundary

This matrix describes how the Rust editor program distinguishes authoring truth,
runtime state, secrets, tool state, and outward projections before any bytes are
copied. It gives clone, backup, packaging, diagnostics, migration, playtest, and
publication one shared vocabulary. It does not claim that the production
classifier or package assembler exists yet.

The organizing model is a three-gate customs ledger:

1. **Identity gate:** the no-follow project-root boundary decides whether an
   object may be inspected at all.
2. **Authority gate:** one or more of the seven state classes describes who owns
   the bytes and what retaining them means.
3. **Destination gate:** the selected operation and explicit options decide
   whether the unchanged bytes may be present, must be absent, or may contribute
   only to newly derived and reviewed output.

This order matters. Classification never makes a symlink, hardlink, device,
outside-root path, or source/destination alias safe. The root policy refuses
those objects before reading their content ([ADR-0003](../adr/0003-project-root-confinement-and-link-policy.md)).

The project-format matrix is the inspected inventory and supplies examples; its
slash-separated classes describe a family spanning multiple authorities, not
permission to choose the least restrictive label
([format-matrix vocabulary](project-format-matrix.md#purpose-and-interpretation)).
The versioned TOML registry is the machine-readable authority for operation
rules, sensitive marker policies, and deterministic seed-canary identities.

## Vocabulary

### Output dispositions

| Disposition | Meaning for exact source bytes |
|---|---|
| `Include` | Bytes are in the operation's ordinary allowlist after root, provenance, license, fingerprint, and destination checks. |
| `Conditional(option)` | Bytes are absent unless the named option receives separate preflight disclosure and authorization. Absence makes clone/backup/migration explicitly partial where restoration would need them. |
| `Derive only` | Source bytes and marker tokens stay absent. A bounded transformation may emit newly classified, reviewed output; redaction is not a relabeling of the source. |
| `Exclude` | Source bytes are outside this output shape. If they appear in the assembled output, verification rejects the output rather than silently deleting after release. |
| `Dispatch` | The operation does not define a byte projection. It must resolve to another named profile first. |

An allowlist is necessary but not sufficient. It does not bypass compatibility
level, consumer need, provenance/license, path confinement, source fingerprint,
artifact-specific overrides, or destination permissions.

### The seven authoritative classes

| Class | Descriptive boundary | Recognition evidence and examples | Default risk when misclassified |
|---|---|---|---|
| `PublicClient` | Runtime material intended to reach an untrusted game client. Public means distributable by project policy; it does not establish a repository license. | Client-consumed assets/configuration and the `Game/` projection. Some families span this and private source, such as attachment names ([`PF-CFG-009`](project-format-matrix.md#split-project-and-runtime-configuration)). | A private source, server rule, or credential can be published because a client consumes a related derivative. |
| `ServerConfig` | Server runtime configuration or support material that is not live account/world state. Fields may also be `Secret`. | Server-only UI resources, update manifests, privileged-script policy, and the non-secret portions of database configuration ([`PF-CFG-014`](project-format-matrix.md#split-project-and-runtime-configuration), [`PF-ADM-001`](project-format-matrix.md#scripts-security-configuration-and-external-administration)). | Client disclosure or deployment-specific configuration copied into an unrelated environment. |
| `Secret` | Credential, verifier, token, private key, or equivalent value whose disclosure is itself harmful. The class applies even when another class describes the containing file's purpose. | Remembered login material, database credentials, and account verifiers ([`PF-CFG-012`](project-format-matrix.md#split-project-and-runtime-configuration), [`PF-DYN-001`](project-format-matrix.md#runtime-private-generated-and-projection-families)). | Values leak through packages, names, logs, manifests, diagnostics, histories, or recovery descriptions. |
| `DynamicPrivate` | State generated or mutated by a running client/server/session and not ordinary authoring truth. It can contain personal, operational, or live-world data. | Accounts/characters, drops, superglobals, script files, logs, radar ownership, and remembered-session state ([runtime-private rows](project-format-matrix.md#runtime-private-generated-and-projection-families)). | A playtest overwrites production state, client media includes private state, or a clone unexpectedly impersonates a live server. |
| `EditorMetadata` | Tool/editor state that does not define runtime gameplay while legacy compatibility is supported. Some subtrees are durable; others are disposable. | Loom recents/atlas/chrome, project-manager recents, tool support, generated debug views, and the proposed owned namespace ([editor metadata rows](project-format-matrix.md#editor-metadata-and-project-shell-state), [ADR-0007](../adr/0007-owned-versioned-editor-metadata.md)). | Cache or host paths are mistaken for authoring truth; unresolved recovery state is copied as harmless preferences. |
| `AuthoringSource` | Creator-owned editable source or canonical project state, including material that may need a derived runtime projection. | Canonical records, world documents, scripts, procedural source, high-fidelity assets, and source-absent specialist documents ([canonical rows](project-format-matrix.md#canonical-records-and-singleton-documents), [specialist rows](project-format-matrix.md#specialist-editable-source-and-derived-assets)). | A client/server package leaks high-fidelity source or a migration discards the only editable lineage. |
| `Unknown` | An inventoried object whose producer, consumer, sensitivity, ownership, or copy semantics remain unproved. Unknown is a positive classification, not missing metadata. | Residual files and arbitrary script-owned artifacts ([unknown rows](project-format-matrix.md#residual-and-currently-unknown-durable-families)). | Uninspected bytes inherit a permissive rule by extension, location, or neighboring files. |

## Classification resolution

Classification is additive, but primary selection is not heuristic. Registry
contract `rcce-state-classification` version 1 requires the versioned inventory
rule set to nominate exactly one primary class for each file. A missing match,
two equally specific/tied matches (even if they name the same class), or
conflicting primary matches fails classification and applies `Unknown`-
equivalent output restrictions. Only an explicit fallback inventory rule may
nominate `Unknown` as the primary. The consumer never chooses by list order,
permissiveness, or file extension. Zero or more recognized detector,
runtime-ownership, and artifact-inheritance constraints become additional
classes. They cannot nominate or replace the primary.

The resolved record retains the nominated primary, removes that same class from
the additional set, deduplicates additional constraints, and evaluates output
permission against the complete primary-plus-additional set. Serialization uses
the owning schema's stable class order. This normalization does not erase the
evidence trail: the inventory-rule ID/version, matched rule, and every additive
constraint source remain reportable.

The resolution trail is:

1. reject unsafe filesystem identities and objects without reading linked
   content;
2. apply the most specific inspected path, record, or field classification from
   the format matrix and a versioned inventory rule;
3. add `Secret` when a recognized secret field or approved detector identifies
   secret material—another label cannot downgrade it;
4. add `DynamicPrivate` when runtime ownership is established;
5. make temp, backup, and recovery objects inherit every class and sensitivity
   of their owning final target;
6. apply `EditorMetadata` only after its ownership/version marker or exact legacy
   path contract is recognized; a directory name alone is insufficient; and
7. add `Unknown` whenever the producer, consumer, sensitivity, boundary, or
   copy semantics remain unresolved.

There is no total “highest class wins” ordering. `Secret`, `DynamicPrivate`, and
`Unknown` are independent restrictive constraints. For an unsplittable mixed-
class file:

- it is present only when every class permits unchanged bytes under the same
  operation/options;
- it is absent when every class excludes unchanged bytes;
- it blocks the selected file when some classes require presence while another
  excludes it; and
- a format-aware split is a separate, versioned transformation with exact source
  preservation, output reparse/validation, and a new classification. Copying
  selected byte ranges is not an implicit split.

Examples: a `PublicClient + Secret` file cannot enter a client package; an
`AuthoringSource + Unknown` file blocks ordinary migration until
`preserve-unknown` is disclosed or a reviewed transform separates it; a
`Secret + ServerConfig` file requires the secret condition in a server package.

## Operation allowlist

The table is an exact rendering of registry version 1. The seven requested
operations are clone, backup, client package, server package, diagnostics,
migration, and publication. `playtest-snapshot` is included because the
canonical plan names it as a separate projection with a distinct private-state
boundary ([plan contract](../plans/plan/rust-super-editor-engine-migration.md#state-classification-and-output-allowlists)).

| State class | Clone | Backup | Client package | Server package | Diagnostics | Migration | Playtest snapshot | Publication |
|---|---|---|---|---|---|---|---|---|
| `PublicClient` | Include | Include | Include | Include | Derive only | Include | Include | Dispatch |
| `ServerConfig` | Include | Include | Exclude | Include | Derive only | Include | Include | Dispatch |
| `Secret` | Conditional(`include-secret`) | Conditional(`include-secret`) | Exclude | Conditional(`include-secret`) | Exclude | Conditional(`include-secret`) | Exclude | Dispatch |
| `DynamicPrivate` | Conditional(`include-dynamic-private`) | Conditional(`include-dynamic-private`) | Exclude | Conditional(`include-dynamic-private`) | Derive only | Conditional(`include-dynamic-private`) | Exclude | Dispatch |
| `EditorMetadata` | Conditional(`include-editor-metadata`) | Conditional(`include-editor-metadata`) | Exclude | Exclude | Derive only | Conditional(`include-editor-metadata`) | Exclude | Dispatch |
| `AuthoringSource` | Include | Include | Exclude | Conditional(`include-runtime-authoring-source`) | Derive only | Include | Conditional(`include-runtime-authoring-source`) | Dispatch |
| `Unknown` | Conditional(`preserve-unknown`) | Conditional(`preserve-unknown`) | Exclude | Exclude | Exclude | Conditional(`preserve-unknown`) | Exclude | Dispatch |

### Operation envelopes

**Clone.** A clone is a new authoring tree, not a runtime package. Public,
server-config, and authoring-source bytes are ordinary candidates. Secret,
dynamic-private, editor metadata, and opaque unknowns are separate disclosed
choices. The output records omissions; without opted-in state it cannot claim
to be a complete identity-preserving clone.

**Backup.** A backup uses the same class envelope as clone, but its report names
whether the selected profile is fully restorative. Sensitive inclusion requires
restrictive destination permissions and values never appear in a report. Active
temp/recovery state is handled by the artifact overrides below, not swept into a
normal archive.

**Client package.** Only exact `PublicClient` bytes are eligible. Server config,
secrets, dynamic/private state, editor metadata, source, and unknowns are absent.
If a runtime-required file is mixed with a disallowed class and no reviewed
split exists, packaging blocks instead of leaking or shipping an incomplete
file.

**Server package.** Public runtime material and non-secret server configuration
are ordinary candidates. Runtime-required source, credentials, and an existing
private-state seed are three independent preflight choices. Editor metadata and
unknowns remain absent. A package that omits credentials/private state is valid
only when the deployment supplies or initializes them through a declared
channel.

**Diagnostics.** A diagnostic bundle never copies source-class bytes directly.
Recognized non-secret classes may inform a bounded redaction/aggregation step;
its result receives a new class and sensitivity review. Secret and Unknown
content and names remain absent. Selecting a “redacted attachment” selects the
derived artifact, not the underlying secret-bearing source
([canonical requirement](../plans/plan/rust-super-editor-engine-migration.md#state-classification-and-output-allowlists)).

**Migration.** Migration targets a separate root and preserves authoring meaning.
Secret, dynamic-private, metadata, and opaque preservation are independent
disclosures. `preserve-unknown` means byte-preserve without interpretation; it
does not grant semantic writer authority. Reports contain checksums and opaque
identities, never sensitive values
([conversion contract](../plans/plan/rust-super-editor-engine-migration.md#conversion-contract)).

**Playtest snapshot.** A snapshot starts with empty, isolated dynamic/private
outputs and no source credentials or editor state. Public and server-config
material are included; runtime-required source is explicit. Child processes
write only to declared snapshot output roots and cannot merge runtime state back
into authoring truth implicitly.

**Publication.** `publication` is a dispatch boundary, not a permissive union of
client and server rules. Preflight must select exactly `client-package` or
`server-package`; the selected profile becomes the byte authority. Publication
reports are derived metadata and do not carry source markers.

## Artifact-specific overrides

Class permission never promotes an operational artifact that has a stricter
lifecycle rule.

| Artifact | Classification and output behavior |
|---|---|
| Legacy sibling `.tmp`, `.bak`, `.bak.tmp` | Inherit the final target's complete class set. Inventory classifies the interrupted state without choosing a winner. Clone, package, diagnostics, migration, playtest, and publication do not copy it as ordinary state. Unresolved recovery blocks claims of a clean/full source snapshot. |
| Rust journal frames and descriptive history | Descriptive frames are `EditorMetadata`, with target identities/classes recorded without secret values. An `InlineOriginalDurable` payload also inherits the non-secret target's complete class set because it contains exact source bytes. Durable journals may enter an explicitly selected internal backup only after every payload and recovery state is classified; never client/server/diagnostic/playtest output. |
| Secret-bearing recovery and staged temp | Inherit `Secret` plus the target's other classes. They are permitted only inside the explicitly authorized, restrictively permissioned recovery lifecycle; all output operations in this matrix exclude them. Names, IDs, reports, and logs remain opaque/redacted ([ADR-0005](../adr/0005-durable-journal-and-recovery.md)). |
| Cache and generated debug dumps | `EditorMetadata`, evictable or regenerable, never compatibility authority. Excluded from runtime projections and migration unless a future row proves a durable authored meaning. |
| Layout, atlas, recents, ownership marker | `EditorMetadata`; internal copy is conditional. Host paths and copied-project identities are reviewed independently. The candidate `Data/.rcce/` namespace remains Proposed, so its path is not yet writable merely because this matrix classifies it. |
| Runtime logs, screenshots, crash dumps | `DynamicPrivate` plus `Secret` or `Unknown` when content warrants. Raw bytes are not a diagnostic bundle; selected diagnostics are newly derived, bounded, and rescanned. |
| Projection/migration/diagnostic manifests and reports | Newly derived metadata. They list row IDs, counts, hashes, decisions, and redacted opaque identities only. Secret values, marker tokens, unredacted private content, raw host paths, and secret-bearing filenames are forbidden. |
| Existing `Game/` and `Server/` output trees | Generated projections, not authoring inputs. A new projection is assembled from a pinned committed snapshot; it does not recursively republish the previous output tree ([`PF-GEN-003`](project-format-matrix.md#runtime-private-generated-and-projection-families)). |

## Exact output evidence

Every operation report is expected to make these observable without embedding
classified values:

- source snapshot identity/fingerprint and destination profile;
- the exact registry ID/version and selected operation/options;
- included, excluded, conditionally omitted, derived, and blocking path counts by
  format-row ID and complete state-class set;
- artifact overrides and unresolved recovery/Unknown decisions;
- pre-assembly and recursive post-assembly marker-scan results; and
- restrictive-permission verification where sensitive bytes are authorized.

The sensitive-marker assertions are exact:

| Marker class | Default clone / backup / migration | Explicit internal option | Client package | Default server package | Explicit server option | Diagnostics | Playtest | Publication report |
|---|---|---|---|---|---|---|---|---|
| `Secret` | Absent | Present only with `include-secret` | Absent | Absent | Present only with `include-secret` | Absent | Absent | Absent |
| `DynamicPrivate` | Absent | Present only with `include-dynamic-private` | Absent | Absent | Present only with `include-dynamic-private` | Absent | Absent | Absent |

“Present” means the unchanged selected fixture bytes occur within their declared
bounds and the destination permission check passed. “Absent” means a recursive
byte scan finds zero occurrences in files, archives, manifests, logs, names, and
sidecars. An unexpected occurrence rejects the output. A missing required
occurrence rejects an explicitly selected sensitive-copy test. Physical-erasure
claims are outside this marker proof.

## Versioned synthetic-canary contract

### Registry and policy identities

The machine registry is
[`test-data/canaries/registry-v1.toml`](../../test-data/canaries/registry-v1.toml):

- registry `rcce-p07-canaries`, version 1;
- permission policy `secret-never-project` for the `Secret` seed, matching the
  exact identity already reserved by the P04 corpus contract
  ([P04 example](../../test-data/projects/README.md#sensitive-values-and-canaries));
- permission policy `dynamic-private-explicit-internal` for the
  `DynamicPrivate` seed; and
- one declared fixture path and exact occurrence for each seed; and
- versioned support-file declarations for `registry-v1.toml` and
  `validate_registry.py`. These declarations plus the canary fixture paths
  derive the entire permitted file set; their parent paths derive the entire
  permitted directory set.

The initial P04 project manifest correctly retains `canary_registry.status =
"unavailable"` because its three corpus roles are not materialized and contain no
canary records. A future ready entry that uses these markers changes that record
to `verified`, names registry ID/version/evidence, and records a canary with the
same canary and permission-policy identities. This packet does not edit or
prematurely advance the accepted corpus manifest.

### Deterministic marker design

For each canary, the registry validator joins these UTF-8 fields with NUL:

```text
token_domain, registry_id, decimal registry_version, fixture_id,
canary_id, state_class, role
```

It hashes the result with SHA-256 and emits literal ASCII:

```text
RCCE_CANARY_V1__<SECRET|DYNAMIC_PRIVATE>__<fixture_id>__<canary_id>__<64-lowercase-hex>
```

The full digest makes independently named fixture roles collision-resistant;
fixture ID and canary ID make occurrences attributable without treating the
token as confidential. Tokens are public test sentinels, not authentication
material.

The two checked-in envelopes use an RCCE-P07-only magic that no legacy/Rust
project or service loader consumes. They contain no username, password, verifier,
key, token, account record, or live runtime authority. The only endpoint is the
IANA-reserved `.invalid` domain at port 0, and `operational=false` is part of the
exact validated bytes. They are therefore scanner inputs, not credentials that a
real service can authenticate. Format-specific future canaries need separate
proof that their synthetically shaped field is non-operational; copying a seed
token into an active credential field does not inherit this claim.

### Collision, rotation, and retirement

- IDs are unique within an immutable registry version; fixture paths are unique,
  regular, single-linked files below `fixtures/`.
- The validator recomputes every token, compares the entire fixture envelope,
  and enumerates the complete canary registry tree without following links. It
  accepts only the exact registry-declared fixture and versioned support files,
  their derived parent directories, and regular-file/directory object types.
  An undeclared file or directory, link, socket, device, FIFO, or other object
  fails before the tree can pass.
- Literal markers are scanned in every allowed file's bytes and in every UTF-8
  relative path component. A marker hidden in a filename is a leak even when the
  file content is benign. Undeclared files fail membership independently of
  marker syntax, so secret-shaped content without the RCCE marker cannot evade
  closure checks.
- A semantic or token-domain change creates a new registry version and new
  canary IDs. Existing versioned tokens are never silently repointed.
- Retired tokens remain in the scanner's detection set until every retained
  artifact and supported output version has been proven clear. Rotation changes
  attribution, not the requirement to detect old leaks.
- A collision, unexpected occurrence, changed envelope, linked fixture, unknown
  policy, or false presence assertion fails closed.

## Verification contract

Run the registry and its six hostile mutations with:

```bash
python3 test-data/canaries/validate_registry.py --self-test
```

The validator checks the closed root/operation/policy/canary shapes, exact class
and operation order, versioned primary-selection contract, P04 policy identity,
operation rules, default and authorized presence assertions, mixed
primary/additional/inherited outcomes, deterministic bytes, unique markers,
single-link regular files, path confinement, exact file/directory membership,
content and filename marker channels, and tree-wide marker membership.
Its 17 classification cases cover permissive/restrictive mixtures, inherited
constraints, duplicate normalization, and rejection of missing, tied,
conflicting, or invalid primary evidence. Six hostile file cases separately
reject reordered classes, a false presence assertion, an operationalized
envelope, a linked fixture, an undeclared regular file containing secret-shaped
bytes without an RCCE marker, and benign content whose filename is the full
Secret marker.

P05's no-follow corpus scanner remains the independent consumer of this registry.
Its future `--canary-registry` path must parse the same ID/version/token derivation
and compare literal marker bytes. Passing this P07 validator does not prove P05
root/link/hash behavior or a production projection assembler.

## Known limits and follow-on evidence

1. No checked-in project corpus entry is `ready`; the seeds prove policy
   mechanics, not compatibility with a real credential/account/log format.
2. No production `rcce-project` classifier, clone, backup, migration, diagnostic,
   playtest, or package assembler exists. Later packet tests must prove the
   matrix against copied fixtures and recursively scanned archives.
3. Path-level splitting is incomplete wherever the format matrix currently uses
   slash-separated families. Those files remain mixed and restrictive until a
   specific inventory rule is accepted.
4. Windows ACLs, hidden-directory behavior, archive permission retention,
   junction/reparse refusal, and physical cleanup limitations remain executable
   gates. This document does not promote Proposed ADR-0007.
5. `Unknown` may conceal secrets. Outward outputs exclude it; internal
   `preserve-unknown` copies disclose that sensitivity is unresolved and never
   render its bytes in reports.
6. A state-class allowlist cannot establish redistribution rights, compatibility
   level, runtime necessity, or semantic safety. Those remain independent gates.

## Packet completion evidence

- Baseline before edit: this matrix and `test-data/canaries/` were absent on the
  packet branch at `a33cb8cb512820580e1d89f39862b7a75fbe08db`.
- The matrix contains all seven authoritative classes exactly and a separate
  rule for every required operation plus the canonical playtest snapshot.
- Machine artifacts contain only two deterministic, explicitly non-operational
  synthetic envelopes; no project, account, credential, runtime-private, or
  third-party bytes were copied.
- Verification output and exact file hashes are recorded at packet freeze; until
  then this section describes the acceptance contract, not a passing claim.
