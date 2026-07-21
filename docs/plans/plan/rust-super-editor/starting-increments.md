# Starting implementation increments

These are the first four implementation increments. Each increment is an umbrella over the independently accepted capability packets declared in its milestone workbook; it is not itself a review unit. Together they establish evidence, a headless project boundary, a disposable technology decision, and the production read-only shell. They do not authorize project mutation.

## Sequence

| Increment | Depends on | Result |
|---|---|---|
| `SE-0001` | Canonical specification accepted | Accepted `SE-M0-P01..P07` compatibility laboratory packets |
| `SE-0002` | M0 accepted | Accepted `SE-M1-P01..P05` headless project-platform packets |
| `SE-0003` | `SE-M1-P01` and M0 budgets accepted | Accepted `SE-M1-P06` disposable UI/render/accessibility decision |
| `SE-0004` | `SE-M1-P01..P06` accepted | Accepted `SE-M1-P07..P09` production five-lens shell, diagnostic and interaction evidence |

`SE-0002` and `SE-0003` may overlap after `SE-M1-P01`; the spike does not wait for all headless packets and may use a contract stub. `SE-0004` waits for every M1 foundation packet. Only one packet owns shared workspace manifests or `rcce-render` APIs at a time.

---

## `SE-0001` — Evidence baseline and decision scaffolding increment

### Identity

- **Milestone coverage**: M0 tasks 1–8 through `SE-M0-P01..P07`.
- **Increment owner**: unassigned; the handoff agent records the program lead, and each child packet requires a named implementation owner and path lease before `Ready`.
- **Status**: Planned; `SE-M0-P01` is first assignable, but no packet is `Ready` while ownership is unassigned.
- **Mutation class**: documentation, corpus metadata and test harness only; no product/project writes.

### Child capability packets

| Packet | Owner/lease before start | Initial status | Packet-specific proof |
|---|---|---|---|
| `SE-M0-P01` | Required; `project-format-matrix.md` | Planned; first assignable | Format census and sampled source evidence |
| `SE-M0-P02` | Required; `editor-capability-matrix.md` | Planned | All 19 applications and capability families reconciled |
| `SE-M0-P03` | Required; Rust baseline/parity files | Planned | Exact build/test/Clippy commands and literal results |
| `SE-M0-P04` | Required; corpus policy/manifest | Planned | Sanitization, provenance, licenses and immutable-source policy |
| `SE-M0-P05` | Required; scanner and hostile fixtures | Planned | No-follow inventory/hash proof on links/reparse points |
| `SE-M0-P06` | Required; bounded ADR files | Planned | Each decision independently reviewed |
| `SE-M0-P07` | Required; state matrix/canaries | Planned | Artifact-class allowlists and presence/absence tests |

No child advances because another child in the increment passed. Dependencies are those in the M0 workbook.

### Outcome

Create the evidence system that later packets must satisfy. Record what the legacy applications and current Rust runtimes actually do before selecting production architecture or declaring compatibility.

### Owned paths

- `docs/compat/project-format-matrix.md`
- `docs/compat/editor-capability-matrix.md`
- `docs/compat/state-classification-matrix.md`
- `docs/compat/parity-dependency-ledger.md`
- `docs/compat/rust-baselines.md`
- `docs/adr/` for the bounded ADRs named by M0
- `test-data/projects/README.md`
- `test-data/projects/manifest.toml`
- scanner implementation path selected by the root/scanner ADR; its tests and hostile fixtures are leased with `SE-M0-P05`

If any path already exists, preserve its conventions and narrow ownership before editing.

### Baseline to record

1. Record exact repository head, submodule state, operating system and Rust `1.85.0` toolchain.
2. Run and record the existing Rust workspace tests independently:
   - `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo test --manifest-path client-rs/Cargo.toml --workspace --locked`
   - `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo test --manifest-path server-rs/Cargo.toml --workspace --locked`
   - `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo clippy --manifest-path client-rs/Cargo.toml --workspace --all-targets --locked -- -D warnings`
   - `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo clippy --manifest-path server-rs/Cargo.toml --workspace --all-targets --locked -- -D warnings`
   - `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo build --manifest-path client-rs/Cargo.toml --workspace --locked`
   - `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo build --manifest-path server-rs/Cargo.toml --workspace --locked`
3. Record whether the BlitzForge compiler and each legacy editor executable are present and runnable; absence is evidence, not an inferred pass/fail.
4. Inventory GUE, Loom, Project Manager and every `src/Tools/*.bb` executable against the capability matrix with source references and observable evidence.
5. Inventory all project-read/write formats and runtime consumers with source references.

### Work

1. Create matrix schemas with stable row IDs, owners, compatibility levels, evidence links and status vocabulary.
2. Classify each state/output surface as project-authored, runtime-generated, external, cache/derived, or tool metadata.
3. Build a sanitized corpus manifest containing origin/provenance, redistribution status, sensitivity review, expected consumers, and hashes. Do not copy private credentials/accounts into fixtures.
4. Add a no-follow corpus scanner that validates manifest membership, hashes, required metadata and secret canaries without reading linked targets. Its hostile suite covers symlinks, junctions/reparse points where available, swap attempts and links to canary content outside the copied fixture root.
5. Write the decision records named by canonical task 6: compatibility ladder, raw-string representation, root/path and link rules, command-only mutation plus durable Ledger/journal, UI/render device ownership, owned/versioned metadata namespace, plugin capabilities, and workspace convergence/dependency direction.
6. Record client/server gaps as dependencies rather than solving them in editor code.

### Verification and evidence

- Both Rust workspace baselines have command, exit code and concise output attached.
- Every capability and format row has at least one inspected source citation; observable claims additionally cite a run, fixture or captured artifact.
- The ADR-selected scanner command passes on the sanitized corpus, fails when a hash or required manifest field is deliberately altered, and proves outside-root linked canary bytes were never read.
- A fresh reviewer samples every application and format family, finds no missing shipping executable, and challenges unsupported parity claims.

### Rollback

These child packets create documentation/harness files only. Roll back one bounded child change; none may alter source projects, databases or build outputs.

### Done

All seven child packets are separately accepted and M0 exit criteria are met; all later packets can cite stable matrix rows and fixtures; unresolved evidence is visibly `Unknown`, not green.

---

## `SE-0002` — Headless root-confined project-platform increment

### Identity

- **Milestone coverage**: first headless portion of M1 tasks 9–15.
- **Depends on**: accepted M0, then the per-child dependencies in the M1 workbook.
- **Increment owner**: unassigned; each child requires an implementation owner and non-overlapping path lease.
- **Status**: Planned; after M0, only `SE-M1-P01` is first eligible for owner/lease assignment and then `Ready`.
- **Mutation class**: read-only production code.

### Child capability packets

| Packet | Scope | Owner/status rule |
|---|---|---|
| `SE-M1-P01` | Full additive workspace skeleton, task 9 | Owner leases workspace manifest and all crate stubs before `Ready` |
| `SE-M1-P02` | Root capability and hostile path suite, task 10 | Separate security review required |
| `SE-M1-P03` | Project inventory and cancellable snapshot, tasks 11 and 15 | Starts after P01/P02 |
| `SE-M1-P04` | Stable identities, `RawLegacyString`, minimal `LegacyDocument`, tasks 12–13 | Starts after P01 |
| `SE-M1-P05` | Actor/media plus all selected-domain consensus fixtures, task 14 | Starts after P04; accepted before authoritative diagnostics |

Acceptance is recorded per child. This increment is complete only when P01–P05 are all accepted.

### Outcome

Given an explicitly selected project root, a reusable Rust API inventories recognized project files, records stable identity/provenance/fingerprints, classifies state, and rejects paths escaping the root. It exposes no write API.

### Expected paths

- `editor-rs/Cargo.toml` and a generated `editor-rs/Cargo.lock`
- stub crates from the canonical physical layout: `rcce-editor`, `rcce-editor-core`, `rcce-project`, `rcce-validation`, `rcce-storage`, `rcce-migrate`, `rcce-admin`, and `rcce-project-cli`
- non-project stubs compile but expose no persistence, migration, administration or UI behavior
- `editor-rs/crates/rcce-project/Cargo.toml`
- `editor-rs/crates/rcce-project/src/{lib,root,inventory,identity,fingerprint,classification,snapshot}.rs`
- `editor-rs/crates/rcce-project/src/{raw_legacy_string,legacy_document}.rs`
- `editor-rs/crates/rcce-project/tests/{root_confinement,inventory,identity,raw_legacy_string,consensus}.rs`
- named actor/media sentinel, malformed and non-UTF8 fixtures under `editor-rs/test-data/consensus/`, plus any additional domain used by the first diagnostic
- approved shared dependencies on `client-rs/crates/rcce-data` only through the M0 direction ADR

Names may change only through a recorded packet amendment; boundaries and proof remain required.

### RED proof

Before implementation, add failing tests for:

- traversal, absolute, drive-relative, UNC/device, reserved-name, alternate-data-stream, embedded-separator and project-root escape;
- symlink/junction/reparse/mount escape, target/link swap and outside-root read races where the platform permits creating them;
- normalized/case-colliding and Windows separator aliases;
- non-UTF-8 or undecodable names with a platform-appropriate explicit result;
- duplicate logical identity and fingerprint collision handling;
- raw byte/display-decoding separation, negative/sentinel values, malformed rows and client/server/editor semantic disagreement;
- known project, incomplete project, empty directory and runtime-output-only directory classification.
- cancellation and deterministic progress ordering for aggregate snapshot loading.

Commit/capture the failing output or, if repository policy keeps a single final commit, preserve the exact pre-implementation command/output in packet evidence.

### Work

1. Create the complete additive workspace/crate skeleton from the canonical physical layout, compatible with Rust `1.85.0` and repository licensing/lint policy; non-owning crates remain compiling stubs.
2. Implement a root capability that is constructed from an explicit directory and joins only validated project-relative paths.
3. Return structured path errors with no lossy fallback to the process working directory.
4. Inventory recognized files without opening unknown files as trusted formats.
5. Assign stable in-memory document identities and provenance (path, source fingerprint, parser/version, classification).
6. Implement `RawLegacyString` and the minimal `LegacyDocument` envelope without using display-decoded text for identity, paths or serialization.
7. Add actor/media client-server-editor consensus fixtures covering sentinels, malformed and non-UTF8 data; disagreements remain provisional I1 dependencies.
8. Integrate only consensus-proven existing `rcce-data` reads; record disagreements as diagnostics/dependencies.
9. Implement `ProjectSnapshot` aggregate loading with cancellation and deterministic progress events.
10. Keep construction and querying thread-safe enough for later background loading without coupling to a GUI executor.

### Verification

- `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo fmt --manifest-path editor-rs/Cargo.toml --all -- --check`
- `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo clippy --manifest-path editor-rs/Cargo.toml --workspace --all-targets -- -D warnings`
- `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo test --manifest-path editor-rs/Cargo.toml --workspace --locked`
- Existing client/server Rust tests remain at their recorded baseline.
- Tests run on Windows and Linux for path semantics; platform skips name the unavailable primitive.
- Public API review confirms no write/delete/rename method exists.

### Rollback

Revert the affected child packet only. Since the increment is read-only and not wired into shipping entry points, rollback has no project-state migration; later accepted children must be revalidated if a prerequisite API is reverted.

### Independent review focus

Attempt root escape and confused-deputy paths, challenge identity stability, inspect dependency direction, and confirm runtime-generated/external state is not mistaken for project-authored state.

### Done

All five child packets are independently accepted. `SE-0004` may then consume a stable read-only inventory/raw-consensus API; no M1 exit is claimed yet.

---

## `SE-0003` — Disposable UI/render/accessibility spike

### Identity

- **Milestone coverage**: M1 task 16 only.
- **Depends on**: accepted `SE-M1-P01`, M0 UI/render questions and approved budgets; it may overlap P02–P05 using a read-only contract stub.
- **Owner/lease**: unassigned; required for `editor-rs/spikes/ui-render/` and its evidence file before `Ready`.
- **Status**: Planned.
- **Mutation class**: disposable spike; no production coupling.

### Outcome

Produce evidence for one desktop shell approach that can host document navigation, high-DPI/accessibility behavior and at least two embedded 3D viewports while sharing one `wgpu` device/queue through explicit `rcce-render` integration seams.

### Owned paths

- `editor-rs/spikes/ui-render/`
- `docs/compat/ui-render-spike.md` plus referenced captures
- no production `rcce-editor` crate in this packet
- changes to `client-rs/crates/rcce-render` require a separately reviewed public-seam amendment and must preserve client tests

### Experiment

1. Evaluate the leading Rust desktop immediate-mode stack compatible with the current `wgpu` generation; record alternatives and rejection evidence.
2. Render two independently navigable viewport textures from one device/queue.
3. Demonstrate deterministic selection/picking and resize/device-loss handling.
4. Exercise 100%, 150% and 200% scale, keyboard-only navigation, focus visibility, screen-reader-accessible names/roles for representative controls, and a 1024×768 constrained layout.
5. Measure idle CPU, frame pacing, viewport resize latency and representative memory use on a documented machine; record measurements rather than universal thresholds unless the canonical spec defines them.
6. Show a large synthetic project tree/list without blocking the event loop.

### Verification and decision

- `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo run --manifest-path editor-rs/spikes/ui-render/Cargo.toml --release` launches the evidence build.
- Automated tests cover picking IDs and non-GPU state; a documented manual protocol covers DPI, accessibility and device recovery.
- Captures identify commit, GPU/driver, OS, scale and scenario.
- The decision record states: accepted stack and constraints, failed criteria, production seams required, and which spike code must be discarded.
- A fresh reviewer attempts keyboard traps, inaccessible custom-drawn controls, multiple-device creation and hidden client regressions.

### Rollback

The spike is isolated. Delete/revert its directory and evidence amendment; no shipping manifest may depend on it.

### Done

A production stack is accepted with evidence, or the packet is rejected with a bounded next experiment. “Looks good” is not an acceptance result.

---

## `SE-0004` — Production read-only Ledger increment

### Identity

- **Milestone coverage**: M1 tasks 17–19.
- **Depends on**: accepted `SE-M1-P01..P06`.
- **Increment owner**: unassigned; P07–P09 each require a named owner and lease before `Ready`.
- **Status**: Planned; P07 becomes eligible for owner/lease assignment only after every foundation dependency passes.
- **Mutation class**: read-only production UI.

### Child capability packets

| Packet | Scope | Acceptance boundary |
|---|---|---|
| `SE-M1-P07` | Production shell and five real-query lenses, task 17 | Shell/lenses and performance budgets; no diagnostic credit |
| `SE-M1-P08` | First consensus-proven reference diagnostic, task 18 | Dynamic diagnostic and stable navigation; no screenshot-only credit |
| `SE-M1-P09` | Interaction/accessibility evidence, task 19 | Headless captures plus keyboard/UIA/Narrator/high-DPI protocol |

Each child is separately reviewed. P08 depends on P07 and consensus fixtures; P09 depends on P07/P08.

### Outcome

Launch a production editor shell with the five accepted lenses over honest real/provisional project queries, explicitly open a project, show one consensus-proven real count, and surface one live referential diagnostic with navigation. No project write path exists.

### Expected paths

- `editor-rs/crates/rcce-editor-core/` for load/session/query state without GUI types
- `editor-rs/crates/rcce-validation/` for structured diagnostic/rule APIs
- `editor-rs/crates/rcce-editor/` for the desktop binary and presentation
- `editor-rs/crates/rcce-editor/tests/` for headless state/integration coverage
- shared renderer adapter only through the seam accepted by `SE-0003`

### Selected vertical slice

The default slice is: actor definition count plus “actor base mesh references a missing media catalog/file” diagnostics. It crosses project inventory, an existing data parser, stable identity, validation and navigation without requiring a writer. Before implementation, `SE-0001/2` evidence must prove the relevant actor sentinel/count and media lookup semantics. If it does not, substitute the next consensus-proven matrix row and record the change before coding.

### RED proof

Add failing integration/state tests proving:

- opening a representative fixture produces the expected real count;
- a missing reference yields a stable diagnostic with source and target identity;
- repairing the fixture outside the app and reloading removes the diagnostic;
- selecting the diagnostic navigates/focuses the owning record;
- cancel/open failure preserves the prior session and does not fall back to a working-directory project;
- large synthetic inventory loading does not block core session progress.
- Records, World, Assets, Scripts and Vault each render a real project query or an explicitly provisional/unsupported state rather than seeded demo data.

### Work

1. Build the production shell from the accepted stack, with explicit Open Project and recent-project provenance.
2. Keep session/load/selection/diagnostic state in `rcce-editor-core`; UI sends intents and renders projections.
3. Implement Records, World, Assets, Scripts and Vault as five views of the same project snapshot. Each uses a real inventory/query, and every unproven value is visibly provisional or unsupported.
4. Query the `rcce-project` inventory and consensus parser for the selected count.
5. Implement the diagnostic as a reusable validation rule with stable IDs/severity/source locations.
6. Wire diagnostic navigation through stable project/document/record identity, including a lens change where the target requires it.
7. Add accessible names, roles, focus order, keyboard activation and a narrow-layout fallback for all shipped controls.
8. Display unsupported/unknown data honestly and keep all mutation commands absent or disabled with a reason.

### Verification

- `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo fmt --manifest-path editor-rs/Cargo.toml --all -- --check`
- `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo clippy --manifest-path editor-rs/Cargo.toml --workspace --all-targets -- -D warnings`
- `/home/ryan/.cargo/bin/rustup run 1.85.0 cargo test --manifest-path editor-rs/Cargo.toml --workspace --locked`
- Existing client/server Rust suites remain at baseline.
- Automated fixture tests prove count, diagnostic lifecycle and navigation.
- Manual acceptance covers explicit project open, keyboard-only flow, 1024×768, 100/150/200% scale and the platform accessibility inspector/screen reader.
- Measured M0 budgets pass for visible open progress, default-project readiness, lens/focus feedback, diagnostics, cancellation, memory and the representative viewport; failures block M1 or amend the budget ADR with evidence.
- Runtime observation confirms no project file timestamp/content changes during open, browse, diagnostics or close.

### Rollback

Revert one child packet and revalidate its dependents. The project spine remains useful and read-only; no project conversion or recovery is required.

### Independent review focus

Try to trigger implicit writes, stale diagnostics, identity drift, inaccessible controls, event-loop blocking and client-render regressions. Confirm the displayed claim comes from project data, not a hard-coded demo model.

### Done

P07–P09 are independently accepted; all five lenses are real/provisional project views; the count and dynamic diagnostic are navigable; performance/accessibility evidence passes; and the entire flow is demonstrably read-only. Only then does M1 exit.
