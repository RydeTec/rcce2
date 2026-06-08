# Contributing to RCCE 2

Thanks for taking a look. This file is the human-facing companion to [`CLAUDE.md`](CLAUDE.md) (the agent-facing version). If you read CLAUDE.md and find it more useful for your particular question, use it — the two are kept consistent.

Quick links:

- **Build**: `compile.bat` (Windows) or `./compile.sh` (macOS)
- **Test**: `test.bat` or `./test.sh` — see [Running tests](#running-tests)
- **Ask**: [GitHub Discussions](https://github.com/RydeTec/rcce2/discussions), the [Forum](https://realmcrafter.boards.net/), or Discord (invite via the RCCE Project Manager)
- **Report a bug**: [Open a bug report](../../issues/new?template=bug_report.yml) — please fill the template

---

## Workflow at a glance

1. Fork (external) or clone (with push access) and branch from **`develop`**.
2. Make focused changes — small first PRs are encouraged. One coherent change per PR.
3. Run `compile.bat` (or `compile.bat -t` for engine-only) before each commit. Verify the engine + all four targets build clean.
4. Run `test.bat`. All tests must pass.
5. Open the PR targeting `develop`. Releases are PRs from `develop` → `master`.

Never push directly to `develop` or `master`.

---

## Building

The build script vendors the BlitzForge compiler as a git submodule under [`compiler/BlitzForge`](compiler/BlitzForge). On a fresh clone:

```sh
git submodule update --init --recursive
```

Then:

```powershell
# Windows
compile.bat              # full build: engine (Server/Client/GUE/PM) + 7 editor tools
compile.bat -t           # engine only — fastest iteration loop (~10× faster than full)
compile.bat -b           # also rebuild BlitzForge from source (slow, MSBuild)
compile.bat -e           # skip engine, build tools only

# macOS / Linux (alpha)
./compile.sh
```

**Watch for**: `compile.bat -t` skips the Tools targets. Several modules under `src/Modules/` (like `Media.bb`, `b3dfile.bb`, `MD5.bb`) are shared between the engine *and* the Tools. If you touched a shared module, run the full `compile.bat` before pushing — otherwise CI will fail on a Tools target you didn't notice.

**Do not** `compile.bat 2>&1 | grep -iE "error|fail"`. The BlitzForge compiler's diagnostic format is `"<file>":<line>:<col>:<line>:<col>:<message>` — the literal words "error" and "fail" don't appear in the diagnostic line, so grep-filtering for them suppresses real errors and gives a false-green signal. Use exit code (`compile.bat; echo $?`) or scan unfiltered output.

---

## Running tests

```powershell
test.bat                 # run every test file under src/Tests/
test.bat ItemsTest       # run only files whose basename contains "ItemsTest"
./test.sh ItemsTest      # macOS / Linux equivalent
```

The runner prints `[RUN ]` / `[PASS]` / `[FAIL]` per file plus an end-of-run summary. CI calls `test.bat` with no args and only checks the exit code.

**Known intermittent flake**: `ItemsTest.bb` sometimes fails with `Stack overflow!` for unrelated PRs. If your CI failure mentions *only* that error and your change doesn't touch items/inventory/serialization, retry with `gh pr close <N> && sleep 3 && gh pr reopen <N>`. Two retries is plenty; if it still fails, investigate. The single-file local rerun shape is `test.bat ItemsTest`.

Test files use **`Strict` + `EnableGC`** at the top with **inline stubs** for dependencies — they cannot `Include` `Server.bb` or other heavy entry points without dragging the entire network/world graph in. See [`src/Tests/Modules/ItemsTest.bb`](src/Tests/Modules/ItemsTest.bb) for the canonical pattern: declare the types the module under test references, then `Include "Modules/<module>.bb"`. The `rcce2-test-writing` agent skill in [`.claude/skills/`](.claude/skills/) has a longer walkthrough.

---

## Commits and pull requests

- **Branch prefix**: pick the type — `feat/`, `fix/`, `refactor/`, `modernize/`, `ux/`, `devex/`, `docs/`, `security/`, `test/`, `align/`. Branch from `develop`.
- **Commit messages**: explain *why*, not just *what*. Multi-line commit messages with motivation + alternatives considered are welcome on non-trivial changes.
- **AI-assisted commits**: include the trailer below so the contribution is attributable and auditable. Replace the model identifier with the model that helped you.
  ```
  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  ```
- **PR target**: `develop`. Promote out of draft when ready for review.
- **Merge style**: merge commits, not squash. The maintainer-side merge command is `gh pr merge <N> --merge --admin --delete-branch`. **Squash merge is not used** because we keep the per-commit narrative for security/reliability audits.
- **PR description**: fill the [PR template](.github/PULL_REQUEST_TEMPLATE.md). Reviewers look for a clear "why", a test plan, and an honest enumeration of risks / deferred work.

---

## Code conventions

These are the rules that are easy to miss but matter for safety. They're enforced by review, not by the compiler.

### Soft-fail on server-controlled data

Server packet handlers and client renderers MUST NOT call `RuntimeError` on values they read from the wire or from save files. A single malformed packet or a missing mesh ID would crash the entire server process and disconnect every other player. Pattern:

```basic
If Result = False
    WriteLog(MainLog, "Handler: bad value, dropping (context: " + ctx + ")")
    SafeFreeActorInstance(A)
    Return
EndIf
```

### Bounds-check before array index

Any value read from a packet or save file used as an array index must be range-checked first. `ActorList` is `Dim`ed `[65535]`, but a client-supplied ActorID also needs an `<> Null` check on the slot (most slots are empty). Pattern:

```basic
If ActorID < 0 Or ActorID > 65535 Or ActorList(ActorID) = Null
    WriteLog(MainLog, "rejecting invalid ActorID " + ActorID)
    Return
EndIf
```

### Null discipline on handle lookups

`Object.X(handle)` returns `Null` for stale or invalid handles — it does NOT error. Any deref on the result without a `<> Null` check is a crash waiting for a freed-but-unreferenced handle. Pattern:

```basic
AI.ActorInstance = Object.ActorInstance(SomeHandle)
If AI = Null
    SomeHandle = 0   ; clear the source global so next tick doesn't repeat
    Return
EndIf
```

### Atomic writes for persistent files

Never write to a final on-disk file directly. Use [`SafeWriteOpen$` / `SafeWriteCommit%`](src/Modules/Logging.bb) so a crash mid-write doesn't truncate the previous (good) copy:

```basic
Local TempPath$ = SafeWriteOpen$(FinalPath$)
Local F = WriteFile(TempPath$)
; ... WriteX(F, ...) ...
SafeWriteCommit%(TempPath$, FinalPath$, F)
```

Apply to any author-irreplaceable data file. Append-only logs and regenerable caches don't need it.

### Float sanitisation at the BVM / wire boundary

Script-supplied or wire-supplied floats that flow into actor state and get broadcast to clients have to clamp NaN/Inf at the boundary, not at the downstream readers. Two helpers in [`src/Modules/RCEnet.bb`](src/Modules/RCEnet.bb):

- `ClampWorldCoord#(v#)` — for X/Y/Z positions and destinations.
- `ClampSaneFloat#(v#)` — for non-position floats (yaw, anim speed, UI dims).

A single NaN poisons spatial code on every receiving client.

### Iterator-during-iteration

Blitz3D's `For X = Each Type` iterator advances via the deleted element's "next" pointer on each `Next`. Calling `Delete X` (or `FreeActorInstance(X)`) inside the loop body corrupts the cursor. Three established fixes:

1. **After-cursor walk** — capture `XNext = After X` *before* the Delete. Works when the body only deletes the current element.
2. **Deferred kill list** — collect into a side type, process after the loop. Right tool when the body might delete *multiple* elements including ones past the cursor.
3. **Restart-on-Delete** — re-enter the For loop after every Delete. Right tool when recursion might delete elements past the captured After pointer.

See [CLAUDE.md](CLAUDE.md#iterator-during-iteration-hazards-blitz3d-for-each--delete) for the patterns in full.

### Privilege gating in BVM commands

New `BVM_*` functions that mutate global server state, open host resources, or replicate the effect of an already-gated peer must guard with `BVM_RequirePrivileged()` at the top of the function body. Actor-state mutators that are safe when targeting the script's own actor use `BVM_RequireSelfOrPrivileged(handle)` — **but only when the target is the script's owning entity, not when clicker-driven scripts pass `SI\AI = Handle(clicker)`**. The gating section in [CLAUDE.md](CLAUDE.md#privilege-gating-in-bvm-commands) lists the four categories and the clicker-handle trap in detail.

---

## Blitz3D / BlitzForge gotchas

These trip everyone up at least once:

- **`Dim arr(N)` and `Field arr[N]` allocate `N+1` slots, indexed `0..N` inclusive**. `For i = 0 To N` is correct, *not* `0 To N-1`. Your training data from base Blitz3D probably has the C-style semantics wrong.
- **BVM opcode ordering**: opcodes in [`RC_Standard_Invoker.bb`](src/Modules/RC_Standard_Invoker.bb) are alphabetical by function name. Inserting `BVM_NEAREST_*` between `BVM_NAME` (case 436) and `BVM_NEWQUEST` (case 437) shifts every subsequent case number. Don't manually renumber.
- **File encoding**: `.bb` files are UTF-8 without BOM. PowerShell's `Set-Content` / `Out-File` default to UTF-16 LE; pass `-Encoding utf8` if you must use them.
- **`Local` shadowing a `Global`**: legal but a code smell. Strict mode catches it.
- **`Continue` keyword**: BlitzForge added it but earlier versions had a code-gen bug inside `Select Case` inside `For`. Safe in current BlitzForge — but if you hit a weird control-flow bug in that exact shape, suspect it.
- **`Or Not <function-call>` in an `ElseIf`**: parses as `Expecting expression Got: Not`. Hoist the call to a `Local` boolean: `Local Ok% = MyFn() : If Not Ok ...`.

---

## Documentation contributions

Easy first PRs:

- The [`docs/modules/`](docs/modules) directory has 17 placeholder pages that are advertised in [`docs/reference.md`](docs/reference.md) as documented but contain only a 149-byte HTML comment. Pick one (per PR), read the source, and write a reference page. [`docs/modules/projectiles.md`](docs/modules/projectiles.md) and [`docs/modules/logging.md`](docs/modules/logging.md) are recently-filled examples to model on.
- [`docs/`](docs) generally needs cross-links: many pages exist but reference each other inconsistently.
- [`CLAUDE.md`](CLAUDE.md) is the agent-facing source of truth. If you discover a stale claim there, fix it — and consider whether the same point belongs in this file too.

### Generated docs

Two doc artifacts are generated from source; do not edit by hand. After touching the corresponding source files, regenerate and commit the result alongside your change.

* **BVM scripting reference** — [`docs/bvm-reference.md`](docs/bvm-reference.md) generated from [`src/Modules/RC_Standard_Invoker.bb`](src/Modules/RC_Standard_Invoker.bb) and [`src/Modules/ScriptingCommands.bb`](src/Modules/ScriptingCommands.bb):
  ```bash
  ./scripts/gen_bvm_reference.sh
  ```

* **Wire-protocol catalog** — [`docs/protocol/index.md`](docs/protocol/index.md) generated from [`src/Modules/Packets.bb`](src/Modules/Packets.bb), [`src/Modules/ServerNet.bb`](src/Modules/ServerNet.bb), and [`src/Modules/ClientNet.bb`](src/Modules/ClientNet.bb):
  ```bash
  ./scripts/gen_packet_index.sh
  ```

Both scripts accept `--check` mode (exit non-zero if the doc is stale) for use in pre-commit hooks or CI; neither is yet wired into the GitHub Actions workflow.

Per-packet detail pages under [`docs/protocol/packets/`](docs/protocol/packets/) are hand-written and incrementally filled. Adding one is a good first PR — pick an under-documented packet from the index table where the "Detail" column shows `—`.

---

## Where to ask

| What | Where |
|---|---|
| Quick question / "is this a known issue?" | [GitHub Discussions](https://github.com/RydeTec/rcce2/discussions) |
| Bug report with repro | [New issue → Bug report](../../issues/new?template=bug_report.yml) |
| Feature idea | [New issue → Feature request](../../issues/new?template=feature_request.yml) |
| Real-time chat | Forum or Discord — invite from the **RCCE Project Manager** |
| Maintainer-only request (push access, sensitive disclosure) | DM `@RydeTec` |

---

## License

The first-party license posture is being finalised. Existing third-party components carry their own licenses (see [`extras/`](extras/) and the [`compiler/BlitzForge`](compiler/BlitzForge) submodule). By opening a PR you grant the project the right to redistribute your contribution under the license the project ships under when it's published. If you have concerns about that, raise them in [Discussions](https://github.com/RydeTec/rcce2/discussions) before contributing.
