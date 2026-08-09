# Rust client/server parity dependency ledger

## Purpose and use

This is the `SE-M0-P03` dependency control required by canonical task 8. It
records the client/server work that each super-editor milestone consumes. It
does not assign work to an unnamed person or convert a future integration into
an assumption.

The baseline is [revision `2d09d099` on
2026-07-20](rust-baselines.md). The client and server parity contracts remain
owned by their existing workstreams; the editor consumes only accepted public
APIs, fixtures, and scorecard evidence. Every row has a stable dependency ID so
later packet evidence can update it without rewriting the meaning of another
row.

## Status and ownership vocabulary

- **Satisfied at baseline:** the exact evidence named in the row exists at the
  baseline revision. This does not satisfy a later semantic or runtime gate.
- **Open:** a bounded dependency is known but not yet evidenced.
- **Blocked:** the dependent editor gate must not pass until the named evidence
  exists.
- **Conditional:** required only when the described capability/ADR branch is
  selected.
- **Human-gated:** automated evidence cannot replace the named observation.

“Client owner” and “server owner” below mean the responsible workstream and
owned paths, not a named individual. **Named human owners are unassigned at the
baseline.** The program lead must record a person/agent lease in an execution
packet before edits to `client-rs/`, `server-rs/`, or their parity documents.

Common workstream boundaries:

| Workstream | Current owned surface | Evidence authority |
|---|---|---|
| Client parity | `client-rs/`, `docs/rust-client/` | Current criterion bodies and exact-head tests/render/live evidence |
| Server parity | `server-rs/`, `docs/rust-server/` | Current criterion bodies and exact-head tests/live/container evidence |
| Shared format/wire | `client-rs/crates/rcce-data`, `rcce-net`, `enet-sys` plus server consumers | Cross-workspace fixtures and both consumer gates; path location does not make this client-only |
| Editor integration | Future `editor-rs/` and editor compatibility evidence | Accepted capability packet; cannot locally reinterpret an open runtime contract |

## Baseline-wide blockers

| ID | Dependency | Current evidence/status | Responsible workstream | Required clearing evidence |
|---|---|---|---|---|
| `PD-BASE-C-GATES` | Exact client workspace test, strict Clippy, and build baseline | **Blocked locally:** all three exit `101` because `alsa.pc` is absent; [baseline](rust-baselines.md) | Client parity; named owner unassigned | Exact-head runs of all three canonical commands in a declared environment with the existing Linux audio prerequisite; exit `0`, counts/results attached |
| `PD-BASE-S-GATES` | Exact server workspace test, strict Clippy, and build baseline | **Satisfied at baseline:** 267/0 tests, Clippy `0`, build `0` | Server parity; named owner unassigned | Refresh after any consumed shared/server change; Docker/package smoke remains separate |
| `PD-BASE-C-SCORE` | Current client parity scorecard | **Open:** mechanical labels are 87 `DONE`, 28 `PARTIAL`, 3 `DEFERRED`, but they are not 87 verified facts: `NET-2` is `DONE` while asserting contradicted big-endian integers; prose aggregate is also stale | Client parity; named owner unassigned | Criterion-level revision with exact evidence; reconcile `NET-2` after byte-exact consensus fixtures; milestones cite required IDs, never an aggregate percentage |
| `PD-BASE-S-SCORE` | Current server parity scorecard | **Open:** 29 `DONE`, 4 `PARTIAL`, 1 `HUMAN-GATED`; summary/divergence prose has bounded inconsistencies | Server parity; named owner unassigned | Criterion-level revision; stale phase/divergence totals reconciled; required partial/human items resolved or explicitly accepted by the consuming gate |
| `PD-BASE-WIRE` | One authoritative payload endianness/string contract | **Blocked for editor reuse:** `codec.rs` methods use LE while its reader/writer comments say BE; client `ACCEPTANCE.md` `NET-2` and client `PLAN.md` say BE integers/LE floats; server `PLAN.md` says BE/LE in its reuse table but LE/LE in its locked invariant | Shared format/wire; named owner unassigned | Byte-exact client/server/editor consensus fixtures over integers, floats, `str8`, and `str16`; only after they pass, reconcile the source comments, client `NET-2`, client plan, and both conflicting server-plan rows |
| `PD-BASE-NATIVE` | Pure-Rust runtime transport | **Open:** `enet-sys` compiles vendored C and `rcenet-ffi-probe` targets a native 32-bit DLL | Shared transport + client/server parity; named owner unassigned | M10-target transport contract and clean dependency audit with no required native ENet/C bridge |

## M1 — Read-only project platform

| ID | Client/server dependency | Current status | Owner | Gate consumed by editor |
|---|---|---|---|---|
| `PD-M1-C-DATA` | `rcce-data` public read APIs for only the diagnostic domains, including sentinel, malformed, non-UTF8, raw-string, and topology behavior | **Blocked pending P05 acceptance:** candidate [actor/base-mesh fixtures](actor-media-consensus.md) add spans/completion and keep disagreements Provisional; selected rows remain I1 and other diagnostic domains are not covered | Shared format/data; named owner unassigned | Accept the exact `SE-M1-P05` aggregate; later domains still require their own fixture packets before authoritative diagnostics |
| `PD-M1-S-DATA` | Server semantic view of the same selected domains (`rcce-server-core` and direct `rcce-data` consumers) | **Blocked pending P05 acceptance:** candidate fixture compares exact server/client/editor actor counts and preserves the high-ID `4` versus `2` split | Server parity + shared format/data; named owner unassigned | Accept the exact P05 aggregate; differences remain dependency evidence rather than editor normalization |
| `PD-M1-C-RENDER` | Reusable renderer/device/resource seam for the disposable UI spike and eventual editor viewport | **Open:** `rcce-render` exists, but no editor-owned viewport contract is accepted | Client renderer + editor spike; named owners unassigned | `SE-M1-P06` proves one device/queue, dual textures, picking, resize/high-DPI, device loss, accessibility, and regression against the client contract |
| `PD-M1-FANOUT` | Client and server exact-head gates after any shared-crate change | **Blocked by `PD-BASE-C-GATES`** | Client/server parity | M1 shared changes cannot be accepted until both recorded baselines are met or an environment-only exception is explicitly reviewed with an exact CI run |

**M1 milestone gate:** selected-domain consensus and renderer boundaries may be
narrower than full runtime parity, but they may not be inferred from a passing
parser test. All four rows above and the M0 ADR/corpus prerequisites must be
accepted before M1 exits.

## M2 — Lossless codecs, commands, and storage

| ID | Client/server dependency | Current status | Owner | Gate consumed by editor |
|---|---|---|---|---|
| `PD-M2-RAW-IO` | One byte-faithful raw reader/writer contract across client `rcce-data` and server `rcce-server-core` primitives | **Blocked:** current owners are split; current client layer is predominantly read-only and server has separate writers | Shared format/data + server core; named owners unassigned | Golden primitives and overlapping document fixtures pass in client, server, and editor before ownership moves |
| `PD-M2-NOOP` | Runtime consumers accept exact no-op writer output for each starter I2 format | **Blocked:** no qualifying Rust project-format writer at baseline | Client/server parity per selected format | Each format advances independently from I1 to I2 with byte identity, reparse, and both consumer gates |
| `PD-M2-SEMANTIC` | Runtime consumers accept the first bounded I3 mutation without relying on canonicalized unrelated bytes | **Blocked:** depends on topology/provenance and selected writer | Client/server parity per selected format | Exact editor-written fixture produces declared client and server meanings; unrelated raw regions remain stable |
| `PD-M2-FANOUT` | Shared dependency/API changes run client, server, and editor test/build/Clippy matrices | **Blocked by `PD-BASE-C-GATES` and future editor workspace** | All three workstreams | CI records exact lock/API fixture revision and passes all affected matrices |

## M3 — Canonical records and relationships

| ID | Client/server dependency | Current status | Owner | Gate consumed by editor |
|---|---|---|---|---|
| `PD-M3-DOMAINS` | Criterion-level semantic consensus for actors, items, spells, projectiles, factions, animations, attributes, damage, environment/suns, interface, particles, and grouped settings | **Blocked:** parser coverage varies; the format matrix records per-row gaps and no blanket I3 readiness | Shared format/data, server core, and each runtime consumer; named owners unassigned | Each domain packet cites its own sentinel/malformed/topology fixtures and both runtime results; no milestone aggregate substitutes |
| `PD-M3-IDENTITY` | Shared stable IDs/sentinels/reference meanings do not break runtime APIs | **Blocked:** Rust types are duplicated and some current parsers lose legacy representation | Shared format/data + server core | Ownership moves only after client/server/editor contract tests pass; numeric identity and sparse gaps remain intact |
| `PD-M3-WRITTEN` | Rust client and server load every editor-written canonical fixture through declared shared semantics | **Blocked:** editor writers do not exist | Client/server parity per domain | Task 40/42 cross-consumer fixtures pass at exact shared API revision; current runtime scorecard regressions remain separate |
| `PD-M3-SCRIPTS` | Script filename and callable-method identity used by spell/item/actor references | **Open:** server parser/runtime exists; editor analysis identity does not | Server script + shared project identity; named owners unassigned | Rename/reference packets use raw project identity and a versioned script-analysis contract; M7 owns authoring/runtime expansion |

## M4 — Media lifecycle

| ID | Client/server dependency | Current status | Owner | Gate consumed by editor |
|---|---|---|---|---|
| `PD-M4-CATALOG` | Client/server agree on media catalog slots, flags, paths, scale/offset, aliases/gaps, and orphan behavior | **Blocked:** accepted format rows include current sound-layout and emitter-field disagreements plus topology loss | Shared `rcce-data`, server consumers, editor media model; named owners unassigned | Byte/semantic consensus fixtures cover all writable media catalogs before I3 lifecycle is enabled |
| `PD-M4-PREVIEW` | Reusable image/B3D decode/render and a narrow sound/music audition boundary | **Open:** render/decoders exist; audio is coupled to `rcce-client` and requires platform audio metadata here | Client renderer/audio; named owner unassigned | Read-only adapters pass corrupt/resource-lifecycle fixtures and client regressions; preview owns no persistence |
| `PD-M4-CONSUME` | Runtime clients load imported/relinked/replaced assets and retain registry identity | **Blocked:** no editor output yet | Client parity; server parity where catalogs affect gameplay | Exact editor-produced registry/files load; unsupported codec/flag cases remain disabled and named |
| `PD-M4-FANOUT` | Media model/API changes preserve client/server builds and declared behavior | **Blocked by baseline/future implementation** | Shared format/data + both runtime workstreams | Exact-head client/server/editor gates and media consumer fixtures pass for every advanced row |

## M5 — Paired world authoring

| ID | Client/server dependency | Current status | Owner | Gate consumed by editor |
|---|---|---|---|---|
| `PD-M5-VISUAL` | Client visual-area parser/renderer consumes editor-written scenery, terrain placement, water, emitters, volumes, lighting, and picking data | **Blocked:** read paths exist for a subset; no lossless visual writer or shared scene adapter is accepted | Client data/render + editor world; named owners unassigned | Per-subsystem fixture loads and headless render/picking evidence pass within declared tolerances |
| `PD-M5-GAMEPLAY` | Server gameplay-area parser consumes editor-written portals, waypoints, triggers, spawns, policies, and paired identity | **Blocked:** server read semantics exist for a subset; no editor writer/consensus set | Server core/world; named owner unassigned | Each gameplay subsystem fixture loads and produces the declared authoritative semantics |
| `PD-M5-PAIR` | Client/server agree which visual/gameplay files constitute a zone and how rename/reference updates are observed | **Blocked:** current formats are independent files with asymmetric legacy behavior | Shared project identity + both runtimes | Visual and gameplay schemas/writers pass separately, then grouped recovery and renamed-project runtime tests pass |
| `PD-M5-RUNTIME` | Exact saved world corpus opens in both Rust runtimes without editor-only state | **Blocked:** output does not exist | Client/server parity | `M5-RUNTIME-PROOF` records exact binaries, fixture fingerprint, load results, and behavior; editor metadata is optional, never required runtime state |

## M6 — Specialist authoring

| ID | Client/server dependency | Current status | Owner | Gate consumed by editor |
|---|---|---|---|---|
| `PD-M6-ASSETS` | Rust client decodes/renders specialist runtime outputs (terrain, B3D, gubbin attachments, fonts/images/audio) | **Blocked per capability:** generic B3D/image reads exist, but private source and export parity are not established | Client data/render plus each specialist packet; named owners unassigned | Each independently accepted output is loaded/rendered by the client with golden or metamorphic evidence |
| `PD-M6-WORLD` | Specialist terrain/export output retains M5 zone linkage and server-visible gameplay shell semantics where applicable | **Blocked:** depends on accepted M5 schemas and tool-specific evidence | Editor world + server core where gameplay files change | Exact exported project passes paired-world runtime proof; a visual-only tool cannot silently mutate gameplay state |
| `PD-M6-NATIVE` | No required specialist runtime path introduces a new native/C++ dependency | **Open:** legacy/source-absent tools still require disposition; final audit is M10 | Specialist/editor + client packaging; named owners unassigned | Every row is native Rust, isolated converter/plugin, intentionally unsupported, or optional non-required; required native dependency keeps gate red |
| `PD-M6-SERVER` | Server dependency for purely visual assets | **Conditional:** none beyond format/world regression when the output has no authoritative semantics | Server parity | Packet explicitly records “no server consumer” with inspected evidence, or supplies a server fixture when gameplay state is affected |

## M7 — Scripts and isolated playtest

| ID | Client/server dependency | Current status | Owner | Gate consumed by editor |
|---|---|---|---|---|
| `PD-M7-SCRIPT-API` | Reusable token/AST/symbol/call/diagnostic/version API from `rcce-script` without linking mutable server state | **Blocked:** parser/interpreter exists, but accepted editor analysis API/version contract does not | Server script workstream; named owner unassigned | Same corpus and analysis fixtures pass in server and editor; privilege/runtime-only operations remain separated |
| `PD-M7-TRANSPORT` | Host-transport/startup/readiness/shutdown contract usable by an out-of-process playtest orchestrator | **Blocked:** current native ENet backend works for server tests, but no accepted fake/native port contract exists | Server net + shared transport; named owners unassigned | Fake and native contract suites pass; editor depends only on the port and structured readiness, not private server state |
| `PD-M7-CLIENT` | Client supports deterministic disposable-root launch, loopback/ephemeral endpoint, evidence hooks, and bounded termination | **Open:** headless hooks exist; complete editor-owned session contract is not accepted | Client parity + editor playtest; named owners unassigned | Representative launch/observe/stop scenario records structured result and leaves source hashes unchanged |
| `PD-M7-SERVER` | Server redirects all account/character/script/log/runtime writes away from source and shuts down cleanly | **Open:** server accepts `RCCE_DATA` and shutdown tests exist; isolation against the full state allowlist is not yet proven | Server parity + editor playtest; named owners unassigned | Malicious, failed, killed, and normal sessions prove source immutability and declared output confinement |
| `PD-M7-PARITY` | Required playtest protocol/gameplay criteria | **Blocked:** client has 28 partial rows; server has 4 partial plus a human-gated live criterion | Client/server parity | `M7-PLAYTEST` names the subset required by its smoke scenario and passes it; it may not claim whole-runtime parity from one demo |

## M8 — External administration

| ID | Client/server dependency | Current status | Owner | Gate consumed by editor |
|---|---|---|---|---|
| `PD-M8-CONFIG` | Runtime interpretation of `MySQL.dat` and secret-preserving compatibility | **Blocked:** legacy artifact semantics are incomplete; current Rust server documents MySQL as disabled/unavailable for shipped config | Server configuration + editor admin; named owners unassigned | Lossless config fixtures plus explicit runtime enabled/disabled behavior; no credential appears in non-authorized artifacts |
| `PD-M8-ACCOUNT` | Adapter-neutral external account identity and operations distinct from flat-file `rcce-server-accounts` | **Blocked:** no accepted external adapter/identity contract exists | Server/admin workstream; named owner unassigned | Identity, authorization, idempotency, truthful outcomes, reconciliation, and redacted audit contracts pass fakes before live enablement |
| `PD-M8-LIVE` | Opt-in disposable database integration | **Conditional and blocked:** required only for operations exposed as supported | Admin/server integration; named owner unassigned | Explicit environment gate, disposable credentials/schema, timeout-after-commit reconciliation, concurrency, and cleanup pass |
| `PD-M8-CLIENT` | Game-client dependency | **Conditional:** none for server-side administration unless an operation changes a client-visible account contract | Client parity | Each operation states whether wire/client behavior changes; if yes, exact client fixture/live evidence is required, otherwise “none” is inspected and recorded |

## M9 — Conditional conversion and publication

| ID | Client/server dependency | Current status | Owner | Gate consumed by editor |
|---|---|---|---|---|
| `PD-M9-C-SCORE` | Named client scorecard required for published packages | **Blocked:** 28 current partial criteria and no exact-head full workspace baseline in this environment | Client parity; named owner unassigned | `M9-PARITY-GATES` names required criterion IDs and exact binary/fixture revision; all required rows pass at their demanded evidence tier |
| `PD-M9-S-SCORE` | Named server scorecard required for published packages | **Blocked:** 4 partial criteria and 1 human-gated criterion remain in the current contract | Server parity; named owner unassigned | Required criterion IDs pass or a higher-level product decision explicitly excludes a non-required path without relabeling it green |
| `PD-M9-PACKAGE` | Rust client/server production binaries, runtime dependencies, manifests, and clean startup from published output | **Blocked:** no editor publication pipeline or clean-package rehearsal exists | Client/server packaging + publication; named owners unassigned | Published artifacts, not worktrees, pass build, smoke, project load, protocol, secret, and deterministic-manifest checks |
| `PD-M9-FORMAT` | Both runtimes negotiate/read any ADR-approved new format; otherwise both remain legacy-native | **Conditional:** no accepted new-format ADR at baseline | Shared format/data + both runtimes | No conversion code when ADR absent; if present, side-by-side conversion and exact runtime readback/downgrade policy pass |

## M10 — Rust-only retirement

| ID | Client/server dependency | Current status | Owner | Gate consumed by editor |
|---|---|---|---|---|
| `PD-M10-COMPLETE-C` | Client named parity scorecard complete on representative legacy projects | **Blocked:** `PD-BASE-C-SCORE` and `PD-M9-C-SCORE` open | Client parity; named owner unassigned | Every required non-deferred criterion is `DONE` with current evidence; clean-machine client flow passes |
| `PD-M10-COMPLETE-S` | Server named parity scorecard complete on representative legacy projects | **Blocked:** partial and human-gated rows remain | Server parity; named owner unassigned | Required criteria and live acceptance pass at their declared tiers; contradictions in summary documents are reconciled |
| `PD-M10-TRANSPORT` | Client/server/editor/playtest interoperate without native ENet/C bridge | **Blocked:** current `enet-sys` uses vendored C; FFI probe remains native diagnostic | Shared transport + client/server parity; named owners unassigned | Pure-Rust transport contract, protocol fixtures, load/failure tests, package audit, and clean-machine session pass |
| `PD-M10-BUILD` | No required build/test/package/runtime path invokes BlitzForge, project-owned C/C++, or legacy applications | **Blocked:** current repository and packaging still include those paths; compiler submodule was uninitialized here, not retired | Build/release + all product workstreams; named owners unassigned | Entry-point dependency audit and clean-machine build/edit/play/admin/publish exercise pass without legacy toolchain |
| `PD-M10-APPROVAL` | Human approval for exact legacy removal targets and rollback archive | **Human-gated and not yet evaluable:** the exact target list and verified rollback artifact do not exist | Human product owner | Verified tag/archive and restore drill, exact target list, recorded approval applicable to those targets, then separate removal change and post-removal clean-machine proof |

## Milestone checkpoint protocol

Before any milestone exits, its packet owner must:

1. Re-run the exact affected client/server commands with Rust `1.85.0` (or a
   separately accepted toolchain-policy revision) and attach exit codes/counts.
2. Record the client/server acceptance-document blob hashes and the exact
   criterion IDs consumed; never cite an approximate aggregate as a gate.
3. Record the shared API/fixture revision used by all three consumers.
4. Change each applicable ledger row to `Satisfied` only from executed evidence.
   A missing API, fixture, owner, environment, or observation remains `Blocked`.
5. Add newly discovered runtime dependencies as new stable rows before editor
   implementation relies on them.
6. Preserve project sources: runtime/live tests use approved disposable copies,
   and all generated accounts, characters, logs, caches, databases, screenshots,
   and backups follow the state/output allowlist.

This ledger deliberately contains no percentage roll-up. One required blocked
row blocks the consuming gate regardless of the number of satisfied rows.
