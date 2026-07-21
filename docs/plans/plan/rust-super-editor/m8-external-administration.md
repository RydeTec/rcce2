# M8 — External administration

## Identity

- **Canonical tasks**: `75, 76, 77, 78, 79, 80`
- **Depends on**: M2 commands/storage and M3 project identity; external account identity is introduced in M8 and never becomes `ProjectModel` identity
- **Status**: Planned

## Outcome

Database configuration and account administration are modeled separately. Local project-secret handling can reach compatibility before any live external mutation is enabled; remote operations are permissioned, auditable and reconciled after uncertainty.

## Work packages

| Packet | Task | Deliverable | Acceptance evidence |
|---|---:|---|---|
| `M8-CONFIG` | 75 | Lossless `MySQL.dat` configuration model and secret-safe writer | I2/I3 fixtures preserve unknowns; canary presence/absence matches the artifact-class allowlist |
| `M8-OPERATIONS` | 76 | One packet per external operation with authorization and idempotency contract | Create/update/delete/reset/list operations have distinct request/result models and policy tests |
| `M8-OUTCOMES` | 77 | `Succeeded`/`FailedBeforeApply`/`OutcomeUnknown` model plus reconciliation | Timeout-after-commit tests never report a false success or blindly retry destructive work |
| `M8-FAKES` | 78 | Deterministic fake adapters and contract suite | CI covers success, denial, duplicate, timeout, disconnect and partial-response scenarios |
| `M8-LIVE` | 79 | Opt-in disposable live-database tests | Isolated credentials/schema, explicit environment gate and guaranteed cleanup |
| `M8-GUARDRAILS` | 80 | Default-disabled UI/CLI policy for unavailable or unsafe administration | Operations remain unavailable until capability, authorization and connectivity are proven |

## External-operation contract

Before any task-76 mutation packet, `M8-ACCOUNT-IDENTITY` defines the adapter-neutral external account key, lookup result, version/precondition semantics, ambiguity/not-found behavior and redacted display projection. The target account identity is distinct from the authenticated principal performing the operation. Neither credentials nor external account rows become project-authored state or `ProjectModel` records.

Every remote mutation declares:

- authenticated principal and required permission;
- target identity and precondition/version when available;
- idempotency behavior or explicit non-repeatability;
- redacted audit fields;
- `Succeeded`, `FailedBeforeApply`, or `OutcomeUnknown` result;
- reconciliation query for `OutcomeUnknown`;
- compensating action where the backing system supports one.

Local configuration writes use the normal command/storage journal. Remote outcomes are recorded as append-only audit events and are never represented as undoable local commands unless a real compensating operation has succeeded.

## Primary implementation surfaces

- `editor-rs/crates/rcce-project/src/mysql_config.rs` — project-file compatibility.
- `editor-rs/crates/rcce-admin/` — ports, operation models, policies and reconciliation.
- Adapter crates/features isolated from `rcce-editor-core` domain behavior.
- Secret policy tests spanning tracing, diagnostics, Ledger, crash reports, protected recovery copies and publication outputs.

## Verification

- Golden and round-trip tests prove local config compatibility separately from DB behavior.
- Contract tests run identically against fake and opted-in live adapters.
- Fault injection covers connection loss before send, after send and after server commit.
- Authorization tests fail closed and produce redacted audit evidence.
- Canary tests require plaintext absence from Ledger/history/journal metadata/logs/crash reports/diagnostics/public outputs. Explicitly authorized project files and recovery copies may retain exact legacy bytes only with restrictive permissions, redacted manifests/names and disclosed inclusion.

## Exit gate

Supported configuration is losslessly writable; every exposed external operation has accepted permission, outcome and reconciliation evidence; and unavailable capabilities are visibly disabled rather than approximated.

## Non-goals

- Making remote database mutations participate in local Ctrl+Z.
- Shipping live database credentials in fixtures or defaults.
- Treating transport timeout as proof that an operation did not apply.
