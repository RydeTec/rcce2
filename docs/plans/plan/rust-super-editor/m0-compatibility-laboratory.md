# M0 — Compatibility laboratory

## Identity

- **Canonical tasks**: 1, 2, 3, 4, 5, 6, 7, 8
- **Status**: `Planned`
- **Depends on**: accepted canonical specification
- **Blocks**: every implementation milestone

## Outcome

RCCE has a reproducible evidence laboratory: every known project format and editor capability is inventoried, current Rust baselines are recorded, test projects are safely classified, key architectural decisions are queued, secrets/projections have policy, and every later milestone has named client/server dependencies.

## Entry conditions

- The canonical specification hash and review verdict are recorded.
- No project corpus is modified in place.
- Owners understand that matrix completeness is a release control, not documentation polish.

## Work packages

| Packet | Canonical tasks | Deliverable | Depends on |
|---|---:|---|---|
| `SE-M0-P01` Format matrix | 1 | `docs/compat/project-format-matrix.md` with one row per durable family | — |
| `SE-M0-P02` Capability matrix | 2 | `docs/compat/editor-capability-matrix.md` with all 19 applications and retirement evidence | — |
| `SE-M0-P03` Rust baselines | 3, 8 | Exact client/server build/test/Clippy/parity dependency ledger | P01 |
| `SE-M0-P04` Corpus policy | 4 | Sanitization, manifests, source immutability, fixture licenses/provenance | P01 |
| `SE-M0-P05` Safe scanner | 5 | Read-only no-follow inventory/hash scanner over copied fixtures | P04, root ADR draft |
| `SE-M0-P06` ADR set | 6 | Decision records named by canonical task 6 | P01, P02 |
| `SE-M0-P07` State classes | 7 | Output allowlists and secret/dynamic canary scheme | P01 |

## Exact initial paths

- `docs/compat/project-format-matrix.md`
- `docs/compat/editor-capability-matrix.md`
- `docs/compat/state-classification-matrix.md`
- `docs/compat/parity-dependency-ledger.md`
- `docs/compat/rust-baselines.md`
- `docs/adr/README.md` and numbered ADRs
- `test-data/projects/README.md`
- `test-data/projects/manifest.toml`
- Scanner path chosen by ADR; no production editor crate is implied by M0

## Verification

- Matrix census matches the master spec’s 19 applications, twelve capability families, durable state families, identity/capacity table, and evidence limits.
- Baseline commands run with one explicit Rust toolchain and report literal exit codes/results.
- Scanner hostile fixtures include symlink/junction/reparse paths and prove no linked content is read.
- Secret canaries are unique and machine-detectable without containing real credentials.
- A traceability check proves canonical tasks 1–8 are represented.

## Exit gate

- All seven packets are independently accepted.
- Unknowns remain explicitly unknown; no matrix row is promoted from evidence by inference.
- `SE-0002` prerequisites are satisfied.

## Non-goals

- No editor workspace, project writer, UI, format repair, or project conversion.

## Risks to review

- Matrices that list files but omit identity/topology or consumers.
- Baselines that hide existing failures or mix Rust toolchains.
- A scanner that follows links while claiming to be read-only-safe.
