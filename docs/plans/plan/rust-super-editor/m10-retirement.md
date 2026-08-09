# M10 — Rust-only retirement

## Identity

- **Canonical tasks**: `89, 90, 91, 92, 93, 94, 95`
- **Depends on**: accepted M9 corpus rehearsal and all required matrix rows
- **Status**: Planned

## Outcome

The supported RCCE build, test, authoring, runtime and publication path is Rust-only. Legacy sources remain frozen and recoverable until objective gates and explicit human approval authorize their removal.

## Work packages

| Packet | Task | Deliverable | Acceptance evidence |
|---|---:|---|---|
| `M10-AUDIT` | 89 | Repository-wide required-path dependency audit | Build graph, scripts, CI, packaging and docs show no required BlitzForge/C++ dependency |
| `M10-TRANSPORT` | 90 | Pure-Rust protocol/runtime transport closure | Client/server/editor/playtest interoperate without legacy binaries or native bridge |
| `M10-DISPOSITIONS` | 91 | Final capability and format disposition ledger | Every row is native, converted, intentionally unsupported, or explicitly retained with owner/date |
| `M10-CLEAN-MACHINE` | 92 | Reproducible clean-machine build/test/edit/play/publish exercise | Recorded environment and commands succeed without preinstalled legacy toolchain |
| `M10-FREEZE` | 93 | Legacy edit freeze and rollback archive | Enforcement blocks new legacy dependencies; archive provenance and restore drill pass |
| `M10-SCORECARD` | 94 | Retirement scorecards and approval packet | All quantitative gates cite exact evidence and unresolved risks/limitations |
| `M10-REMOVE` | 95 | Separately authorized removal/archival change | Explicit human approval, recoverable tag/archive and post-removal clean-machine proof |

## Retirement scorecard

The approval packet must report, without averaging away gaps:

- project-format rows at their required compatibility level;
- editor-capability rows and disposition evidence;
- Rust client/server parity dependencies;
- representative-corpus results;
- clean-machine build, test, author, playtest and publish evidence;
- security/secret/plugin/external-operation gates;
- known unsupported behavior and its user impact;
- rollback archive identity and restoration procedure.

Any required red row blocks retirement. A partial capability cannot be reclassified as unsupported solely to make the scorecard green.

## Removal authorization and rollback

`M10-REMOVE` is not authorized by completing prior packets. Before deletion or submodule removal, the human approves the exact target list and rollback artifact. The rollback is a verified repository tag plus independently stored source/submodule identifiers and build instructions. Removal occurs in its own reviewable change, followed by the complete clean-machine exercise.

## Verification

- Dependency scans start from supported entry points rather than filename extensions alone.
- Clean-machine automation installs only documented Rust/system prerequisites.
- Representative old projects open, mutate, playtest and publish through the Rust path.
- Restore rehearsal proves the archive can reconstruct the frozen legacy state.
- Independent reviewers audit the evidence and attempt to find hidden native/legacy dependencies.

## Exit gate

All scorecards pass, clean-machine proof is accepted, rollback is verified, and the human explicitly authorizes the bounded removal. Only then may BlitzForge/C++ and superseded editor applications leave the supported product path.

## Non-goals

- Deleting historical source before approval.
- Claiming Rust-only because the main binaries compile while tests, tools or packaging still require legacy components.
- Erasing provenance, compatibility fixtures or migration documentation after retirement.
