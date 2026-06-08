<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } -->

# macOS Apple Silicon Notes

RCCE can produce native Apple Silicon binaries through the vendored
[BlitzForge](../compiler/BlitzForge) toolchain, but the macOS runtime remains
alpha. Expect missing language/runtime coverage and editor/runtime breakage.
Use the macOS port for development and feedback, not for shipping a game.

## Build from source

Start from a fresh clone with submodules:

```bash
git clone --recurse-submodules https://github.com/RydeTec/rcce2.git
cd rcce2
```

If the submodules were not populated during clone:

```bash
git submodule update --init --recursive
```

Install the Homebrew/toolchain prerequisites that BlitzForge needs:

```bash
./scripts/bootstrap_macos.sh
```

That bootstrap script does not compile BlitzForge for you. Build the toolchain
first so `compiler/BlitzForge/bin/blitzcc` exists, then build RCCE itself:

```bash
./compile.sh -b
./compile.sh
```

Recommended follow-up checks:

```bash
./test.sh
./publish.sh
```

`./publish.sh` rebuilds the engine/tools, creates the `release/` directory, and
generates `release/MACOS_NOTES.txt` inside the packaged output. That generated
text file is release-bundle metadata, not a tracked source file in this
repository.

## What to expect

- The first `./compile.sh -b` run builds BlitzForge itself and can take a while.
- `./compile.sh` expects a working `compiler/BlitzForge/bin/blitzcc`; without
  it, the script exits and points you back to `./compile.sh -b`.
- `./test.sh` compiles the Blitz test suite from `src/Tests`; it is the best
  built-in source-level confidence check before packaging.
- `./publish.sh` is the path that emits the release-bundle `MACOS_NOTES.txt`
  file mentioned in older README text and release artifacts.

## Current compatibility position

- Native Apple Silicon support is alpha.
- The runtime/linker path is still incomplete.
- Many engine behaviors are not yet at Windows parity.
- Failures on fresh builds should be treated as bugs, not as proof the macOS
  path is production-ready.
