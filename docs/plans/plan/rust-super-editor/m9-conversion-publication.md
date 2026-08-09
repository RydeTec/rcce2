# M9 — Conditional conversion and publication

## Identity

- **Canonical tasks**: `81, 82, 83, 84, 85, 86, 87, 88`
- **Depends on**: accepted M6, M7 and M8 disposition evidence
- **Status**: Planned

## Outcome

Projects can be inspected, validated, repaired and published through scriptable Rust workflows. Legacy-native preservation remains the default; a new format exists only if a prior ADR proves it necessary and defines reversible conversion.

## Work packages

| Packet | Task | Deliverable | Acceptance evidence |
|---|---:|---|---|
| `M9-CLI` | 81 | Sole `rcce-project` CLI root for inspect, validate, verify, backup, clone and conditional migrate, with repair/publish only after their owning packets pass | Stable exit codes, machine-readable output and fixture-driven integration tests |
| `M9-BRANCH` | 82 | Enforced no-conversion vs accepted-ADR execution branch | CI proves conversion code and UI are absent/disabled without the ADR feature gate |
| `M9-CONVERSION` | 83 | Conditional side-by-side converter and manifest | Only runs on accepted-ADR branch; source remains intact and outputs are reproducible |
| `M9-CONV-TESTS` | 84 | Golden, property, idempotence and downgrade/readback tests | Every supported source class has declared result and loss report |
| `M9-PUBLISH` | 85 | Deterministic publication pipeline with manifest and rollback | Failed publication cannot replace last known-good output |
| `M9-PARITY-GATES` | 86 | Automated capability/format/client/server gate aggregation | Publication fails on unmet required rows and cites exact evidence |
| `M9-SECRET-SCAN` | 87 | Secrets and sensitive-artifact scanning across outputs | Known canary secrets are detected in all publication surfaces |
| `M9-REHEARSAL` | 88 | Full representative-corpus migration/publication rehearsal | Reproducible clean run with checksums, runtime smoke tests and disposition report |

## Mandatory branch behavior

### Default: no accepted new-format ADR

- `convert` reports that conversion is unnecessary/unavailable.
- Projects remain legacy-native and writers preserve their existing contracts.
- Publication packages legacy-compatible files for Rust runtimes.

### Conditional: accepted new-format ADR exists

- Conversion is explicit, side-by-side and non-destructive.
- The manifest records source fingerprint, tool/version, decisions, warnings, losses and output fingerprint.
- Re-running with identical inputs/options is idempotent.
- Downgrade or legacy-readback behavior is implemented exactly as the ADR declares; unsupported downgrade is a visible gated limitation.

## Primary implementation surfaces

- `editor-rs/crates/rcce-project-cli/` for the sole `rcce-project` command-line composition root over the same core APIs as the UI.
- `editor-rs/crates/rcce-publication/` for manifests, staging and promotion.
- Optional conversion crate/feature created only after the ADR gate is satisfied.
- `test-data/projects/` representative corpus and redacted rehearsal outputs.

## Verification

- CLI integration tests assert stdout/stderr schema, exit code and zero mutation under `--dry-run`.
- Publication fault injection covers staging, validation, manifest, promotion and restart.
- Reproducibility tests compare output manifests and hashes on clean runs.
- Rust client/server smoke tests consume published outputs, not editor working files.
- Secret scans cover source maps, logs, manifests, backups and diagnostic bundles.

## Exit gate

The complete representative corpus passes the applicable legacy-native or ADR-governed conversion path, publishes deterministically, passes Rust runtime gates, and produces an auditable loss/disposition report with no unresolved required capability.

## Non-goals

- Introducing a new project format as cleanup or convenience.
- In-place conversion of the only project copy.
- Publishing despite a failed parity row under a warning-only escape hatch.
