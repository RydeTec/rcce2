# Native Windows scanner evidence

This record is the durable native-Windows evidence for the M0 project scanner.
It reports commands that were executed, not inferred cross-compilation results.

## Provenance

- Evidence date: `2026-07-20`.
- Source repository head: `ed715767661da61dc657a294a0dc0ed362019815`.
- Scanner Git tree: `c2fd81de9729d847f31ee6ef9d7975e0ae183335`.
- Scanner per-file-hash manifest aggregate:
  `e52066f86d5e5039a4e3592b23030ce3bc2810fdf7be891bf294e519a6a3bf43`.
  It is the SHA-256 of the `sha256sum` manifest generated in
  `git ls-tree -r --name-only` path order from the source head above; each
  manifest line is `<content SHA-256><two spaces><repository path>`.
- Host: `Microsoft Windows [Version 10.0.22631.6199]`, `x86_64-pc-windows-msvc`.
- Toolchain: `rustc 1.85.0 (4d91de4e4 2025-02-17)` and `cargo 1.85.0 (d73d2caf9 2024-12-31)`.
- Direct toolchain launcher: `C:\Users\dyanr\.cargo\bin\rustup.exe`.
- Native working copy and command cwd:
  `C:\Users\dyanr\AppData\Local\Temp\rcce-project-scanner-evidence-ed715767`.

The named evidence directory was newly created and did not replace an existing
directory. The copy contained only `tools/project-scanner/{Cargo.toml,Cargo.lock,
README.md,src/*.rs}`, `test-data/projects/{manifest.toml,schema-v1.json}`, and
`test-data/canaries/{registry-v1.toml,validate_registry.py,fixtures/*.txt}` from
the source head above. It excluded the Linux `target/` tree. Cargo created a new
native-Windows `tools/project-scanner/target/` beneath the evidence directory;
no source project or legacy application was opened or mutated.

## Exact commands and observed results

Each command ran after this exact cwd selection:

```bat
cd /D C:\Users\dyanr\AppData\Local\Temp\rcce-project-scanner-evidence-ed715767
```

| Gate | Exact command | Exit | Observed result |
|---|---|---:|---|
| Tests | `C:\Users\dyanr\.cargo\bin\rustup.exe run 1.85.0 cargo test --manifest-path tools\project-scanner\Cargo.toml --locked -- --nocapture` | `0` | Literal library summary: `test result: ok. 25 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out`; main and doc-test summaries were also `0/0` and successful. Junction-root, junction-descendant, junction-swap, persistent-hardlink, schema-pin, redaction, closed-P07-oracle, and hostile manifest tests ran natively. |
| Clippy | `C:\Users\dyanr\.cargo\bin\rustup.exe run 1.85.0 cargo clippy --manifest-path tools\project-scanner\Cargo.toml --all-targets --locked -- -D warnings` | `0` | Completed the dev profile with no warning or error. |
| Build | `C:\Users\dyanr\.cargo\bin\rustup.exe run 1.85.0 cargo build --manifest-path tools\project-scanner\Cargo.toml --locked` | `0` | Native debug build completed. |
| Capability | `tools\project-scanner\target\debug\rcce-project-scanner.exe --manifest test-data\projects\manifest.toml --platform-capability` | `0` | Exact output: `platform=windows;nofollow=descriptor-relative-reparse-safe;volume-identity=required;hardlink-static=reject;hardlink-transient=unavailable`. |
| Strict transient-link requirement | `tools\project-scanner\target\debug\rcce-project-scanner.exe --manifest test-data\projects\manifest.toml --require-transient-hardlink-detection` | `2` | Expected fail-closed output: `scan rejected: platform unsupported: transient hardlink detection is unavailable on this backend`. |

The host/toolchain observations used:

```bat
ver
C:\Users\dyanr\.cargo\bin\rustup.exe run 1.85.0 rustc -Vv
C:\Users\dyanr\.cargo\bin\rustup.exe run 1.85.0 cargo -V
```

All three exited `0`.

## Limits

- The Windows backend proves reparse-safe descriptor-relative traversal,
  persistent-hardlink rejection, volume identity, and the executed hostile
  cases above. It does not prove detection of a hardlink created and removed
  during a read; the machine-readable capability says `unavailable`, and the
  strict requirement fails closed with exit `2`.
- The checked-in manifest currently declares three non-ready projects, so the
  direct capability commands are not evidence that a representative ready
  legacy project was inventoried. Ready/hostile temporary fixtures were exercised
  by the native test suite.
- This run did not execute `cargo fmt` on Windows. Formatting is covered by the
  repository-side stable `cargo fmt --check` gate; Rust `1.85.0` rustfmt was not
  installed in the earlier scanner environment and was not installed here.
- No legacy editor, generator, client, server, database utility, or third-party
  converter was launched. File presence is not evidence of runnability or safe
  mutation behavior.
