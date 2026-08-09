# Rust client and server baseline

## Scope and evidence rules

This is the `SE-M0-P03` execution baseline required by canonical task 3. It
records what was observed at one repository revision; it is not a claim that
either runtime has reached parity. Commands marked **executed** ran in the
isolated packet worktree. Statements marked **inspected** come from the linked
source or configuration. Statements marked **reported** are claims in a parity
document that were not reproduced by this packet.

- **Evidence date:** `2026-07-20`
- **Repository commit:** `2d09d0992757a6beb9acf0a77978fdd5637a4006`
- **Worktree:** Linux worktree on Ubuntu `24.04.4 LTS`, WSL2 kernel
  `6.18.33.2-microsoft-standard-WSL2`, `x86_64`
- **Mutation boundary:** build outputs were allowed; no project data, source
  corpus, dependency installation, or shipping executable was changed or run.
- **Authoritative commands:** the six commands in the gate table below. A
  diagnostic subset is recorded separately and never substitutes for a failed
  workspace command.

The repository pins Rust `1.85.0` and the `clippy` component in
[`rust-toolchain.toml`](../../rust-toolchain.toml). Both workspace manifests
declare `rust-version = "1.85"` through their workspace package settings
([client](../../client-rs/Cargo.toml), [server](../../server-rs/Cargo.toml)).
The exact executed binaries reported:

```text
rustc 1.85.0 (4d91de4e4 2025-02-17)
cargo 1.85.0 (d73d2caf9 2024-12-31)
host: x86_64-unknown-linux-gnu
```

The three repository submodules were uninitialized in this worktree: `git
submodule status --recursive` prefixed each recorded SHA with `-`.

| Submodule | Recorded gitlink | Worktree state |
|---|---|---|
| `compiler/BlitzForge` | `bdbfd9ff154b2a65b8c458b52bec524b23e4e81e` | Uninitialized |
| `extras/reshade` | `a43429537c54927e8f660563a80361c30c3be831` | Uninitialized |
| `extras/vscode-blitz-forge` | `5129152b80c9dbdbbaa819f0879edd16cdf1c1b2` | Uninitialized |

Consequently the packet did not treat absent BlitzForge output as a compile
failure.

## Exact gate results

Every command below was invoked from the repository root through
`/home/ryan/.cargo/bin/rustup run 1.85.0`. Exit codes are process exit codes,
not inferred from filtered output.

| Workspace | Gate | Exact command after `rustup run 1.85.0` | Exit | Recorded result |
|---|---|---|---:|---|
| Client | Test | `cargo test --manifest-path client-rs/Cargo.toml --workspace --locked` | `101` | Build stopped in `alsa-sys 0.3.1`: `Package 'alsa', required by 'virtual:world', not found`; no workspace test result was produced. |
| Client | Clippy | `cargo clippy --manifest-path client-rs/Cargo.toml --workspace --all-targets --locked -- -D warnings` | `101` | Build stopped at the same missing `alsa.pc` probe; no complete all-target lint result was produced. |
| Client | Build | `cargo build --manifest-path client-rs/Cargo.toml --workspace --locked` | `101` | Build stopped at the same missing `alsa.pc` probe; no complete workspace build was produced. |
| Server | Test | `cargo test --manifest-path server-rs/Cargo.toml --workspace --locked` | `0` | Computed aggregate from 14 Cargo `test result:` summaries: `267 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out` across unit, integration, real-script, real-ENet, and doc-test binaries. |
| Server | Clippy | `cargo clippy --manifest-path server-rs/Cargo.toml --workspace --all-targets --locked -- -D warnings` | `0` | `Finished 'dev' profile [unoptimized + debuginfo] target(s) in 18.09s`; no warning or error was emitted. |
| Server | Build | `cargo build --manifest-path server-rs/Cargo.toml --workspace --locked` | `0` | `Finished 'dev' profile [unoptimized + debuginfo] target(s) in 0.08s`. |

`BASELINE: client workspace has 3 environment-blocked gates (test, Clippy,
build: exit 101, missing ALSA development metadata); server workspace has 0
gate failures (267 tests passed, strict Clippy passed, build passed).`

### Client diagnostic subset

To distinguish an unavailable audio-dependent application build from the
reusable data/network/render crates, this additional command was executed:

```text
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo test \
  --manifest-path client-rs/Cargo.toml \
  -p rcce-data -p rcce-net -p rcce-render --locked
```

It exited `0`. A computed aggregate from its 14 Cargo `test result:` summaries
is `147 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out`. This proves
only those three named packages at this revision. It does not prove
`rcce-client`, `enet-sys` all-target Clippy, the application binaries, or the
full client workspace gate.

## Why the client gate is unavailable here

**Inspected:** [`rcce-client/Cargo.toml`](../../client-rs/crates/rcce-client/Cargo.toml)
depends on `rodio`, whose Linux CPAL backend reaches `alsa-sys`. The CI workflow
explicitly installs `libasound2-dev` before it builds the Rust applications
([`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)). That system
package was absent in this WSL environment. The packet did not install it.

This is a platform prerequisite failure, not evidence of a Rust source failure
or a passing client baseline. Before a shared client/editor change is accepted,
the client workstream must attach an exact-head run in an environment with the
declared system prerequisites.

No client warning count is reported: compilation stopped before a complete
Clippy/build result, so “zero warnings” would be unsupported. The server Clippy
gate completed without warnings. Release builds, the Windows packaging wrapper,
the Linux packaging wrapper, Docker build/startup, live GUI sessions, headless
PNG inspection, and performance measurements were not run by this packet and
remain unverified at this revision.

## Workspace and dependency snapshot

### Client

**Inspected:** [`client-rs/Cargo.toml`](../../client-rs/Cargo.toml) contains six
workspace members: `rcce-data`, `rcce-net`, `rcenet-ffi-probe`, `rcce-client`,
`rcce-render`, and `enet-sys`. Lockfile blob at the baseline commit:
`29ffb46603c0bd27d4b3610444d1538d9cb4fcd4`.

- `rcce-data` parses a substantial legacy project subset, but the accepted
  [project-format matrix](project-format-matrix.md) records semantic-loss and
  writer gaps; successful parser tests do not promote any family to I2/I3.
- `rcce-render` owns `wgpu` rendering used by the client. Editor reuse requires
  an accepted public viewport/resource seam, not access to client application
  state.
- `rcce-net` implements payload integers and floats with little-endian methods
  in [`codec.rs`](../../client-rs/crates/rcce-net/src/codec.rs), while that same
  file's `MsgWriter` and `MsgReader` doc comments still say “big-endian.” The
  contradiction extends into the parity authorities: client criterion
  [`NET-2`](../rust-client/ACCEPTANCE.md#L282) says big-endian integers/LE
  floats and is labeled `DONE`; the [client parity plan](../rust-client/PLAN.md#L24)
  repeats that split; and the [server plan reuse table](../rust-server/PLAN.md#L18)
  says big-endian integers/LE floats while its later [locked byte-order
  invariant](../rust-server/PLAN.md#L75) says both are little-endian and calls
  the earlier note wrong. These are exact
  conflicting claims, not merely old wording. Byte-exact consensus fixtures
  must establish the contract first; then the shared source comments and all
  three parity rows must be reconciled before the editor consumes the codec.
- `enet-sys` compiles vendored C through `cc`; it is transitional and fails the
  final Rust-only transport condition even though it is a Rust crate boundary.
- `rcenet-ffi-probe` is explicitly an `i686-pc-windows-msvc` diagnostic for the
  32-bit DLL. It is not a portable editor dependency.

### Server

**Inspected:** [`server-rs/Cargo.toml`](../../server-rs/Cargo.toml) contains five
workspace members: `rcce-server-net`, `rcce-server-core`,
`rcce-server-accounts`, `rcce-script`, and `rcce-server`. Lockfile blob at the
baseline commit: `8a530f40ccabae380b56b9ed1b5b9734bf7a9f2d`.

The server consumes `enet-sys`, `rcce-net`, and `rcce-data` by relative path
from `client-rs`. This makes changes to those crates a client/server/editor
fan-out even though Cargo maintains two workspaces and lockfiles. The server
also has its own legacy IO and gameplay-domain interpretations in
`rcce-server-core`; M1–M3 must prove semantic consensus before moving ownership.

The [server README](../../server-rs/README.md) identifies Linux containers as
the deployment target and warns that the server writes account, character, and
script state beneath its data root. Any editor playtest must therefore use a
disposable copy, never a source project.

## Parity document revisions and limits

Document hashes make the snapshot reproducible even when descriptive dates are
stale.

| Workstream | Contract document | Baseline blob | Last change touching document | Inspected status at this blob |
|---|---|---|---|---|
| Client | [`ACCEPTANCE.md`](../rust-client/ACCEPTANCE.md) | `8535a3e56c9f4049c7fe119f0471262abcb46481` | `464db5a7bb5765718d0134d9c5c69f8ee2fd701b`, `2026-07-15` | Mechanical census of 118 unique status labels: 87 `DONE`, 28 `PARTIAL`, 3 `DEFERRED`, 0 `MISSING`. This is not a census of verified facts: `NET-2` is labeled `DONE` while asserting the contradicted big-endian-integer contract. The prose scorecard still says approximately 68/24 and is labeled a 2026-06-01 baseline; neither aggregate is a release gate until criterion claims are reconciled. |
| Client | [`PLAN.md`](../rust-client/PLAN.md) | `a84fa4b68d748a62b6c4cc90f70a5ad8f99ab644` | `ef09d867cbb9d0d032bf3072e7f452cb0dc7adaa`, `2026-06-07` | Defines parity as every non-`DEFERRED` criterion `DONE`; 28 current `PARTIAL` rows therefore keep client drop-in parity open. Its status prose predates many later acceptance edits. |
| Server | [`ACCEPTANCE.md`](../rust-server/ACCEPTANCE.md) | `773af49b195e649e3a7b2b565235ddf38101bedd` | `0790f6cc8703e42ee2813a4fb6d752ff17fa798c`, `2026-07-16` | Criterion bodies yield 29 `DONE`, 4 `PARTIAL`, 1 `HUMAN-GATED`. The phase-summary row for interaction is stale (it says 3/3 rather than the bodies' 5/1), while the total remains 29/4/1. |
| Server | [`PARITY.md`](../rust-server/PARITY.md) | `ae7410211c8b08c35a54b2ece0777e9a331dcf95` | `8e0a54e81e4c598fa8685bf3d0de5215c1254f92`, `2026-07-15` | Reports all functional parity closed plus a human-gated live step. The definitive table visibly lists ten divergence IDs (`R-2..R-5`, `R-7..R-12`) while its net sentence says eleven; consume named rows, not the aggregate. |
| Server | [`PLAN.md`](../rust-server/PLAN.md) | `1c6f94054412c31f3d829f7a67e91f4e93910797` | `9b48c25c9d61d3b33a2fdc3e63426bea917014b3`, `2026-06-18` | Calls for all non-deferred criteria to be `DONE` and a real-client session. Four current partial criteria and the human-gated GUI playtest remain explicit dependencies where a later editor gate requires full server parity. |

These inconsistencies are not repaired by this baseline packet because its
owned paths exclude the client/server parity documents. The dependency ledger
turns them into named owner actions instead of treating a headline as proof.

## Legacy executable observation

At this isolated revision the following expected locally built files were
absent: `compiler/BlitzForge/bin/blitzcc`, `bin/GUE.exe`, `bin/Loom.exe`,
`Project Manager.exe`, `bin/Client.exe`, `bin/Server.exe`, `bin/ClientRS.exe`,
and `bin/ServerRS.exe`. Several bundled third-party/source-absent tool
executables were present under `bin/tools/`, but none was launched because this
packet neither characterizes GUI tools nor permits project mutation.

Therefore this environment provides no legacy editor runtime comparison and no
current packaged Rust application smoke test. Source and artifact discovery in
the two accepted compatibility matrices remains the baseline for those
surfaces.

### Complete 19-application executable observation

M0 exit inspection refreshed the executable/artifact census at repository head
`ed715767661da61dc657a294a0dc0ed362019815`. IDs and names below are exactly the
19 rows in the [editor capability matrix](editor-capability-matrix.md#mechanical-19-by-12-census).
Presence was inspected with non-following regular-file checks and `stat`; no
legacy application was launched. The source-built output paths for IDs 1–9 are
the paths declared by [`compile.bat`](../../compile.bat#L82) and its `src/Tools`
filename loop ([line 119](../../compile.bat#L119)). `compiler/BlitzForge/bin/blitzcc`
was also absent, so this packet did not attempt to manufacture those outputs.

| ID | Application | Inspected executable/artifact | Present at head | Execution evidence | Runnability evidence |
|---:|---|---|:---:|---|---|
| 1 | Project Manager | `Project Manager.exe` | Absent | **Not run** | Not runnable from this checkout: expected executable and `blitzcc` compiler binary absent. |
| 2 | GUE | `bin/GUE.exe` | Absent | **Not run** | Not runnable from this checkout: expected executable and `blitzcc` compiler binary absent. |
| 3 | Loom | `bin/Loom.exe` | Absent | **Not run** | Not runnable from this checkout: expected executable and `blitzcc` compiler binary absent. |
| 4 | Gubbin Tool | `bin/tools/Gubbin Tool.exe` | Absent | **Not run** | Not runnable from this checkout: expected executable and `blitzcc` compiler binary absent. |
| 5 | RC Architect | `bin/tools/RC Architect.exe` | Absent | **Not run** | Not runnable from this checkout: expected executable and `blitzcc` compiler binary absent. |
| 6 | RC Caves Editor | `bin/tools/RC Caves Editor.exe` | Absent | **Not run** | Not runnable from this checkout: expected executable and `blitzcc` compiler binary absent. |
| 7 | RC Rock Editor | `bin/tools/RC Rock Editor.exe` | Absent | **Not run** | Not runnable from this checkout: expected executable and `blitzcc` compiler binary absent. |
| 8 | RC Terrain Editor | `bin/tools/RC Terrain Editor.exe` | Absent | **Not run** | Not runnable from this checkout: expected executable and `blitzcc` compiler binary absent. |
| 9 | RC Tree Editor | `bin/tools/RC Tree Editor.exe` | Absent | **Not run** | Not runnable from this checkout: expected executable and `blitzcc` compiler binary absent. |
| 10 | RC Spell Wizard | `bin/tools/RC Spell Wizard/RC Spell Wizard.exe` (`261632` bytes) | Present | **Not run** | File presence only; Windows loading, prerequisites, safe project selection, and mutation behavior were not assessed. |
| 11 | Script Crafters Workshop | `bin/tools/Script Crafters Workshop/Script Crafters Workshop.exe` (`911360` bytes) | Present | **Not run** | File presence only; runnability and project-write behavior were intentionally not exercised. |
| 12 | RC Scriptorama | `bin/tools/RC Scriptorama/RC Scriptorama.exe` (`878592` bytes) | Present | **Not run** | File presence only; runnability and multi-project mutation behavior were intentionally not exercised. |
| 13 | Font Generator | `bin/tools/FontGen/Font Generator.exe` (`1331200` bytes) | Present | **Not run** | File presence only; runnability and output-generation behavior were not assessed. |
| 14 | Freemake Audio Converter | `extras/Freemake/Freemake Audio Converter/FreemakeAudioConverter.exe` (`2097544` bytes) | Present | **Not run** | File presence only; third-party prerequisites, license/UI, codec behavior, and runnability were not assessed. |
| 15 | Plant Life | `bin/tools/Plant Life/Plant Life.exe` (`3313664` bytes) | Present | **Not run** | File presence only; runnability and generated-asset behavior were not assessed. |
| 16 | Tree Magik | `bin/tools/Tree Magik/Tree Magik.exe` (`2646016` bytes) | Present | **Not run** | File presence only; runnability and generated-asset behavior were not assessed. |
| 17 | RC Script Generator | `bin/tools/RC Script Generator/RCScriptGenerator.jar` (`68759` bytes) | Present | **Not run** | Archive presence only; JVM availability, launchability, clipboard behavior, and generated text were not assessed. |
| 18 | RC SkinCrafter | `bin/tools/RC SkinCrafter/RC SkinCrafter.exe` (`83968` bytes) | Present | **Not run** | File presence only; effective capability and runnability remain Unknown as recorded in the capability matrix. |
| 19 | MySQL Configure | `extras/MySQL Server/MySQL Configure.exe` (`204800` bytes) | Present | **Not run** | File presence only; deliberately not launched because it can write configuration and mutate accounts/database state. |

This table is a complete presence/runnability baseline, not behavioral parity:
`Present` never means runnable, safe, supported, or characterized. Exact native
Windows execution evidence belongs only to the read-only project scanner and is
recorded in [`WINDOWS-EVIDENCE.md`](../../tools/project-scanner/WINDOWS-EVIDENCE.md).

## Reproduction and refresh protocol

1. Use the repository commit and exact `rustup run 1.85.0` commands above.
2. Record OS/architecture, toolchain output, lockfile blobs, and every exit code.
3. Do not hide an environmental prerequisite failure behind a narrower package
   run; record both, labeled by scope.
4. Recompute criterion rows from the current acceptance documents and attach
   their blob hashes. Do not copy approximate scorecard prose forward.
5. Update the [parity dependency ledger](parity-dependency-ledger.md) whenever a
   shared API/fixture, required criterion, owner, or gate changes.
6. Never run client/server smoke tests against an authoritative project root;
   use a manifest-approved disposable copy and classify all runtime outputs.
