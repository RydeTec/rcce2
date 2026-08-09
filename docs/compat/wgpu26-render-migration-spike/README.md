# wgpu 26 rcce-render migration spike

## Disposition

`Stage1Feasible` for the isolated `rcce-render` crate only. This is a disposable
compile/runtime feasibility branch, not approval to migrate the production
renderer or select a UI framework.

The spike moves `rcce-render` from wgpu 22 to exact wgpu 26.0.1 without changing
its device topology, requested feature set, downlevel limits, or texture
formats. Result-based failure handling is preserved, and errors newly returned
by wgpu 26 are propagated. Cargo continues to resolve wgpu 22.1.0 for
`rcce-client`; no client manifest or call site was changed.

## Evidence summary

- Executed baseline at base `94ed5adbd9adf94d88f8ff6b0e9432285af8d925`:
  `cargo test -p rcce-render --all-targets --locked` passed 10/10.
- Executed RED: pinning the renderer to wgpu 26.0.1 exposed 80 compiler errors
  across the expected eight renderer source files.
- Executed GREEN at migration commit
  `01361496e3fc8847cab7702cdffde44a817fecec`: all-target tests passed 10/10,
  strict clippy passed, and the GPU probe opened a Vulkan llvmpipe device.
- Executed render comparison: the exact-base wgpu 22 and migrated wgpu 26
  offscreen renders of `data/Meshes/Props/houses_single.b3d` were byte- and
  pixel-identical. Both PNGs were 900x900 RGBA with SHA-256
  `7afed19d0e8d847083386c1b4433fc32e63c6e90e1ab094285a20bf90f555a2c`.

## Boundary and limitations

- Windows and `i686-pc-windows-msvc` are unverified because that target is not
  installed for Rust 1.85.0 in this WSL environment.
- The WSL runtime proof used CPU llvmpipe, not the reference workstation's
  physical Windows GPU.
- A full `rcce-client` check is not a Stage 1 gate and stopped earlier on the
  environment's missing ALSA development package. The client still speaks the
  wgpu 22 type contract and must be a separately approved Stage 2 migration.
- Exact-toolchain rustfmt was unavailable. Separate `git diff --check` runs
  passed for the base-to-migration and migration-to-evidence ranges; no
  component was installed unattended.

## Stop rule for production

Do not integrate the migration commit into the program branch. A production
migration requires explicit approval, a separately leased client/API stage,
Windows 32-bit compilation, physical-GPU runtime/capture evidence, and fresh
independent review.
