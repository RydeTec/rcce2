# RCCE UI/render decision spike

This is a disposable decision harness, not production editor architecture. It keeps its own
window loop, UI renderer, picking shader, evidence recorder, and Cargo workspace under this
directory. It imports `rcce-render::WorldView` but does not modify the client renderer.

The harness presents two independently sized `WorldView` textures through one wgpu device and
queue, a 100,000-row virtualized catalog, GPU ID-buffer picking, AccessKit wiring, a Windows
native file picker, and a destructive test-device reconstruction path. It always reports the
candidate as unselected while any mandatory observation is Fail or NotRun.

Run the deterministic contracts with Rust 1.85:

```sh
rustup run 1.85.0 cargo test --manifest-path editor-rs/spikes/ui-render/Cargo.toml --all-targets --locked
```

Run an evidence trace:

```sh
rustup run 1.85.0 cargo run --manifest-path editor-rs/spikes/ui-render/Cargo.toml --locked -- \
  --trace editor-rs/spikes/ui-render/traces/standard-v1.json \
  --viewport-count 2 --scale-percent 200 \
  --output editor-rs/spikes/ui-render/evidence-out/run
```

Without `--frames`, the executable schedules the trace's 300 warmup and 1,800 measured frames.
Viewport count and deterministic scale override must be members of the checked-in trace matrix.

`evidence-out/` and `target/` are ignored. Only reviewed, machine-labelled results belong in
`docs/compat/ui-render-spike-evidence/`.
