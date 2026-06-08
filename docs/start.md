# Getting Started

RealmCrafter: Community Edition no longer uses the old Blitz3D / BlitzPlus
workflow described in earlier docs. The live repository builds through the
vendored [`BlitzForge`](../compiler/BlitzForge) toolchain and the root-level
build scripts in this repo.

If you only want to try RCCE, download a packaged release from GitHub. This
page is for contributors and source builders working from the repository.

## Clone The Repository

Clone with submodules so the BlitzForge compiler and bundled extras are present:

```bash
git clone --recurse-submodules https://github.com/RydeTec/rcce2.git
cd rcce2
```

If you already cloned without submodules:

```bash
git submodule update --init --recursive
```

## Know The Source Tree

The main source entrypoints live under `src/`:

- `src/Client.bb` - game client
- `src/Server.bb` - game server
- `src/GUE.bb` - Game Unified Editor
- `src/Project Manager.bb` - launcher and packaging entrypoint
- `src/Modules/` - shared engine and editor modules
- `src/Tests/` - Blitz test sources compiled by the test runners

Game content, scripts, and sample project data live under [`data/`](../data),
including `data/Server Data/Scripts/` for gameplay scripting. Compiled outputs
land in [`bin/`](../bin) plus the root-level `Project Manager` executable.

## Build From Source

### Windows

Use the batch scripts from the repository root:

```bat
compile.bat
compile.bat -b
publish.bat
test.bat
```

### macOS / Linux

Use the shell scripts from the repository root:

```bash
./scripts/bootstrap_macos.sh
./compile.sh
./compile.sh -b
./publish.sh
./test.sh
```

`bootstrap_macos.sh` is required on macOS before the first source build. The
script name is historical; it prepares the Unix-side toolchain used by the
current shell workflow.

macOS support is currently alpha because the underlying BlitzForge runtime is
still incomplete there. Treat the macOS path as a development and feedback
surface, not a production release target.

### Useful Build Flags

The build scripts share the same intent:

- `-b` / `--blitz` rebuild BlitzForge itself
- `-e` / `--skip-engine` skip the main RCCE engine build
- `-t` / `--skip-tools` skip the editor/tool builds

## Run What You Built

Successful builds produce the main applications from the repo root:

- `Project Manager.exe` on Windows, or `Project Manager` on macOS
- `bin/Client(.exe)`
- `bin/Server(.exe)`
- `bin/GUE(.exe)`

Use Project Manager when you want the normal launcher flow. Use the binaries in
`bin/` when you want to run the client, server, or editor directly.

## Run Tests

`test.bat` and `./test.sh` compile each `.bb` file under `src/Tests/` with
BlitzForge test mode. Run the test script from the repo root before opening a
pull request.

## Typical Contributor Flow

1. Clone with submodules.
2. Build with `compile.bat` or `./compile.sh`.
3. Run `test.bat` or `./test.sh`.
4. Launch `Project Manager` or the relevant binary in `bin/`.
5. Make focused changes in `src/`, `data/`, or `docs/`.

## Useful Orientation Notes

- Running the client in debug mode uses a windowed display, which makes
  multi-client local testing easier.
- Server-side gameplay code often refers to abilities as spells and zones as
  areas.
- If you are tracing gameplay or content behavior, inspect
  `data/Server Data/Scripts/` alongside the engine sources in `src/`.

## Where To Go Next

- See [`index.md`](index.md) for the docs landing page.
- See [`reference.md`](reference.md) for module-level documentation.
- See [`formats.md`](formats.md) for on-disk data format notes.
