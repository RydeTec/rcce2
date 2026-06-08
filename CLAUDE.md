# CLAUDE.md — RCCE 2 (RealmCrafter Community Edition)

Orientation for Claude agents working in this repo. User-facing docs live in [ReadMe.md](ReadMe.md) and [docs/](docs/); this file is the developer's-eye view that the README skips.

## What this repo is

Server + Client + tools for an open-source MMORPG engine, written in the **BlitzForge** language (a modernized Blitz3D fork — *not* base Blitz3D). The compiler is vendored as a git submodule at [compiler/BlitzForge](compiler/BlitzForge).

The five shipping executables built from `src/`:

| Source | Output | Purpose |
|---|---|---|
| [src/Server.bb](src/Server.bb) | `bin/Server.exe` | Authoritative game server |
| [src/Client.bb](src/Client.bb) | `bin/Client.exe` | Game client |
| [src/GUE.bb](src/GUE.bb) | `bin/GUE.exe` | Graphical world editor (the established editor) |
| [src/Loom.bb](src/Loom.bb) | `bin/Loom.exe` | Loom alpha — parallel redesigned editor; **read [docs/loom/README.md](docs/loom/README.md) before touching** |
| [src/Project Manager.bb](src/Project%20Manager.bb) | `Project Manager.exe` | Project launcher — launches GUE or Loom for the active project |

Plus seven editor tools under `src/Tools/` (RC Architect, Terrain/Cave/Rock/Tree editors, Gubbin Tool).

## Skills available in this repo

Always check `.claude/skills/` for relevant skills before working in a specialty area. The user-invocable list shows them at session start; the most load-bearing ones:

- **blitzforge-language** — invoke whenever writing or reviewing any `.bb` file. Your training data is base Blitz3D from 2003; BlitzForge added Strict, GC, inheritance, methods, `BBList`, `Async/Await`, `Try/Catch`. Without this skill you *will* write outdated code.
- **rcce2-packet-handler** — invoke before touching [ServerNet.bb](src/Modules/ServerNet.bb) or [ClientNet.bb](src/Modules/ClientNet.bb). Wire encoding, bounds-checking, soft-fail patterns.
- **rcce2-bvm-command** — invoke before adding/modifying a `BVM_*` function in [ScriptingCommands.bb](src/Modules/ScriptingCommands.bb). The dispatch in [RC_Standard_Invoker.bb](src/Modules/RC_Standard_Invoker.bb) is alphabetically opcode-ordered and has a 142-case renumber trap.
- **rcce2-test-writing** — invoke before adding a test under `src/Tests/`. Test files are `Strict`+`EnableGC`+inline-stubbed and must not pull in network/world deps.

## Loom (alpha redesigned editor)

A parallel editor to GUE, shipping as `bin/Loom.exe`. Built around the design concept *"every reference between entities is a clickable thread."* Read-only in the alpha; editing is a beta concern.

**Before touching any `src/Loom.bb` or `src/Modules/Loom/*` file, read [docs/loom/README.md](docs/loom/README.md).** It explains what Loom is supposed to be, separate from what it currently is.

| Doc | When to read it |
|---|---|
| [docs/loom/README.md](docs/loom/README.md) | First. The north star and what's shipped. |
| [docs/loom/architecture.md](docs/loom/architecture.md) | Before adding or restructuring a Loom module. Module map, state conventions, BlitzForge gotchas. |
| [docs/loom/roadmap.md](docs/loom/roadmap.md) | Before picking what to build next. Shipped / next-up / deferred (with reasons). |
| [docs/loom/decisions/](docs/loom/decisions/) | Before arguing with an existing choice. ADRs for custom-draw-vs-F-UI, read-only-alpha, the zone-only pivot, deferred 3D viewport. |
| [docs/loom/prototype/](docs/loom/prototype/) | When sizing a new visual surface. The literal Claude Design HTML/JSX/CSS bundle. Open `Loom - World Editor.html` in a browser. |

Loom reuses GUE's data modules (`Items.bb`, `Actors.bb`, `Spells.bb`, `ServerAreas.bb`, etc.) via `Include`, so it can't drift from GUE's view of the project on disk. Anything that touches a data module needs to keep both editors compiling — `compile.bat` builds all five engine targets and CI runs the same.

The Loom UI is custom-drawn on top of Blitz3D's 2D primitives (`Color`/`Rect`/`Line`/`Text`) wrapped in `Modules/Loom/Theme.bb`. F-UI is **not** Included by Loom — see [docs/loom/decisions/001-custom-draw-not-fui.md](docs/loom/decisions/001-custom-draw-not-fui.md) for the rationale, including specific F-UI quirks that derailed the earlier retrofit attempts.

## Build and test

Always run from the repo root.

```powershell
# Windows (PowerShell or cmd)
.\compile.bat              # build engine + all tools
.\compile.bat -t           # build engine only (skip tools — ~10x faster)
.\compile.bat -b           # also rebuild BlitzForge (slow, MSBuild)
.\compile.bat -e           # skip engine, build tools only
.\compile.bat -r           # also build the Rust client → bin\ClientRS.exe (needs cargo; skips if absent)
.\compile.bat -e -t -r     # build ONLY the Rust client (skip engine + tools)
.\test.bat                 # compile + run every Strict test under src/Tests/
.\test.bat ItemsTest       # run only files whose basename contains "ItemsTest"

# macOS (Apple Silicon, alpha)
./compile.sh
./test.sh
./test.sh ItemsTest        # same single-file substring filter as Windows
```

`test.bat` / `test.sh` print a `[RUN ]` / `[PASS]` / `[FAIL]` marker per file and an end-of-run `Ran N files: P passed, F failed.` summary with a bulleted list of any failing files. CI still calls the runner with no args and only checks the exit code, so the default behavior is unchanged. The positional substring filter is the documented way to reproduce the known intermittent `ItemsTest.bb` flake locally without re-running the whole suite — see the **Known intermittent flake** note under [CI](#ci-githubworkflowsciyml) below.

After any change to a `.bb` file under `src/`, run `compile.bat -t` and confirm clean compile before committing. `Local`-shadowing-a-`Global`, missing field, wrong sigil — Strict mode catches all of these at compile time.

**Rust client (`bin\ClientRS.exe`).** `compile.bat -r` / `compile.sh -r` `cargo build --release`s the `client-rs` workspace's `client-window` binary and copies it to `bin\ClientRS.exe` — a self-contained 64-bit drop-in alongside the Blitz `bin\Client.exe` (the two coexist; `-r` never touches the Blitz client). The flag is opt-in (a clean wgpu build is ~1–2 min) and skips gracefully with a message if `cargo` isn't on `PATH`, so CI without Rust is unaffected. `ClientRS.exe` finds the project `data\` via `RCCE_DATA`, else a `data\` dir next to or above its own location (so `bin\..\data` resolves) or the working directory. It boots into a **login screen** (account/password) → **character select** (create / delete / enter world), then connects to a running `bin\Server.exe -UNLOCK` and renders the live world. See `client-rs/` and the **Rust client port** memory for the feature set.

Runtime env vars (all optional): `RCCE_DATA` (project `data\` dir), `RCCE_USER` (pre-fills the login account, default `rustbot`), `RCCE_AUTOLOGIN` (skip the login screen, log straight into the world — implied by `RCCE_BENCH`/`RCCE_AUTOWALK`), `RCCE_AUTOSUBMIT` / `RCCE_AUTOENTER` (headless test hooks: auto-submit the login / auto-enter the first character, for screenshotting the menu→world path), `RCCE_AUTOWALK` (headless movement self-test), `RCCE_CPUSKIN` (force the legacy CPU skinning path; GPU linear-blend skinning for actor bodies is now the **default** — faster + smoother — with a per-actor CPU fallback when an appearance can't be skinned), `RCCE_GPUSKIN` (now a no-op opt-in, kept for back-compat), `RCCE_BENCH=N` (after a 120-frame warmup, time N frames, print `[bench] avg fps …`, exit — measure with vs. without `RCCE_CPUSKIN` to compare), `RCCE_SHOT=<path>` / `RCCE_SHOT_FRAME=N` (capture the live world or menu to a PNG at frame N and exit — headless render verification), `RCCE_PHASE` / `RCCE_DAYNIGHT_SECS` (pin / set the day-night cycle), `RCCE_WEAPON_MESH` (force a weapon mesh on every actor, for gear-attach verification).

The compile target (`Server.exe`, `Client.exe`, `GUE.exe`, `Project Manager.exe`) depends on which top-level `.bb` includes the file. Most modules are included by Server *and* Client, so check both compile.

**Run full `compile.bat` (no `-t`) when touching modules that the Tools also include.** `-t` skips `src/Tools/*.bb` for speed (~10× faster), but several utility modules are shared between the engine and the Tools — `src/Modules/Media.bb`, `src/Modules/b3dfile.bb`, `src/Modules/MD5.bb`, and others. CI runs the full compile, so a change that adds an unresolved reference inside `Media.bb` (e.g. calling a helper from `Logging.bb` that the Tools don't include) will pass `-t` locally but fail CI on the Tools target. Quick rule of thumb: if you touched anything in `src/Modules/` that doesn't have `Server`/`Client` in the filename, prefer the full compile.

## Repo layout

```
rcce2/
├── src/                          # all engine source — your primary work area
│   ├── Server.bb · Client.bb     # entry points (include cascade — start here)
│   ├── GUE.bb · Loom.bb · Project Manager.bb
│   ├── Modules/                  # ~70 .bb files; see "Module map" below
│   │   └── Loom/                 # Loom-specific UI modules (Theme, Threads, Browser, Composer)
│   ├── Tools/                    # standalone editor utilities (each .bb → .exe)
│   └── Tests/                    # Strict-mode test files (see test skill)
├── compiler/BlitzForge/          # SUBMODULE — compiler + runtime (C++17)
├── extras/vscode-blitz-forge/    # SUBMODULE — VS Code language extension
├── extras/reshade/               # SUBMODULE — post-processing
├── data/                         # default game project (worlds, scripts, assets)
├── docs/                         # user-facing engine + scripting docs
│   └── loom/                     # Loom design intent, architecture, roadmap, ADRs, prototype bundle
├── bin/                          # compiled binaries + vendored DLLs (gitignored .exe)
├── scripts/                      # cross-platform build helpers
├── .claude/skills/               # agent skills for specialty work
├── compile.bat · compile.sh      # build scripts (read these for flags)
├── test.bat · test.sh
└── ReadMe.md                     # user-facing overview
```

## Module map (`src/Modules/`)

The ~70 module files split into rough categories. Names overloaded with `Server*` or `Client*` are split intentionally — same name without a prefix means it's shared.

| Category | Files |
|---|---|
| **Wire / packets** | [RCEnet.bb](src/Modules/RCEnet.bb), [Packets.bb](src/Modules/Packets.bb), [ServerNet.bb](src/Modules/ServerNet.bb) (huge `Select Case` packet dispatch), [ClientNet.bb](src/Modules/ClientNet.bb) |
| **World / areas** | [Actors.bb](src/Modules/Actors.bb), [ServerAreas.bb](src/Modules/ServerAreas.bb), [ClientAreas.bb](src/Modules/ClientAreas.bb), [GameServer.bb](src/Modules/GameServer.bb), [Environment.bb](src/Modules/Environment.bb), [Environment3D.bb](src/Modules/Environment3D.bb) |
| **Items / combat / spells** | [Items.bb](src/Modules/Items.bb), [Inventories.bb](src/Modules/Inventories.bb), [Spells.bb](src/Modules/Spells.bb), [Projectiles.bb](src/Modules/Projectiles.bb), [ClientCombat.bb](src/Modules/ClientCombat.bb) |
| **Persistence / auth** | [AccountsServer.bb](src/Modules/AccountsServer.bb), [PasswordHash.bb](src/Modules/PasswordHash.bb), [MySQL.bb](src/Modules/MySQL.bb), [Logging.bb](src/Modules/Logging.bb) (`SafeWriteOpen$` / `SafeWriteCommit%` atomic write helpers) |
| **BVM scripting** | [Scripting.bb](src/Modules/Scripting.bb), [ScriptingCommands.bb](src/Modules/ScriptingCommands.bb) (native `BVM_*` functions), [RC_Standard_Invoker.bb](src/Modules/RC_Standard_Invoker.bb) (opcode dispatch table) |
| **UI** | [Gooey.bb](src/Modules/Gooey.bb), [F-UI.bb](src/Modules/F-UI.bb), [Interface.bb](src/Modules/Interface.bb), [Interface3D.bb](src/Modules/Interface3D.bb), [MainMenu.bb](src/Modules/MainMenu.bb) |
| **3D / media** | [Actors3D.bb](src/Modules/Actors3D.bb), [Animations.bb](src/Modules/Animations.bb), [Projectiles3D.bb](src/Modules/Projectiles3D.bb), [Media.bb](src/Modules/Media.bb) |
| **Misc** | [Language.bb](src/Modules/Language.bb), [Radar.bb](src/Modules/Radar.bb), [b3dfile.bb](src/Modules/b3dfile.bb), [MD5.bb](src/Modules/MD5.bb) |

Subdirectories under `Modules/` (`Framework/`, `Graphics/`, `Helpers/`, `IO/`, `Project Manager/`, `Traits/`) hold smaller utility groups.

## Conventions you must follow

### Wire encoding ([RCEnet.bb](src/Modules/RCEnet.bb))

`RCE_StrFromInt$(num, length=4)` packs an integer into `length` bytes (big-endian-ish via Bank). `RCE_IntFromStr(s$)` reverses it. Every packet field uses these. The bundled `length` is critical — a 2-byte ID written as 4 bytes will corrupt every subsequent field. Always pair sender's `RCE_StrFromInt$(x, N)` with receiver's `Mid$(MessageData$, offset, N)` of the same `N`.

### Atomic writes ([Logging.bb](src/Modules/Logging.bb))

Never write to a final on-disk file directly. Use:

```basic
Local TempPath$ = SafeWriteOpen$(FinalPath$)
Local F = WriteFile(TempPath$)
; ... WriteX(F, ...) ...
SafeWriteCommit%(TempPath$, FinalPath$, F)   ; closes F + atomic rename
```

This prevents corruption if the process crashes mid-write. Apply to any persistent data file.

### Soft-fail on server-controlled data

Server packet handlers and client renderers must **not** call `RuntimeError` on values they read from the wire or from save files. A single malformed packet or a missing mesh ID would crash the entire process and disconnect every other player. Recovery pattern:

```basic
If Result = False
    WriteLog(MainLog, "Handler: bad value, dropping (context: " + ctx + ")")
    SafeFreeActorInstance(A)   ; or appropriate cleanup
    Return                     ; or skip the rest of the Case
EndIf
```

See the `Soft-fail` series of merged PRs (#128–#134 for the original cluster, #138–#144 for the follow-up rounds covering `CreateActorInstance(ActorList(...))`, `PreLoadSpawns`, missing-`Head`-joint, character-select preview, and stale `PlayerTarget` handles, #306/#307/#308/#309 for the final sweep covering MainMenu mesh-load, names-filter file, ClientAreas zone-load mesh/emitter, and the `P_ActorGone` Me-self crash) — the audit comment block in each fix explains the threat model. As of #309 there are **no remaining wire-driven RuntimeError sites** in the client or server packet-handler paths; every recoverable failure soft-fails through `WriteLog + drop + continue`.

**Me-only-RuntimeError exception.** A small set of failure paths preserve `RuntimeError(...)` even after the soft-fail sweep, *only* for the local-player case where there's no recoverable state: `ClientNet.bb:295` / `:306` / `:331` (Me's own actor mesh fails to load, or Me's mesh is missing a `Head` joint — there's no body to control). These are gated `If AI <> Me ... Else RuntimeError`, and the audit comment at `ClientNet.bb:284-290` records the intentional preservation. New code touching the actor-mesh load path on the client should follow the same shape — soft-fail for `AI <> Me`, hard-fail only when Me has no usable body.

### Bounds checks before array index

Any value read from a packet or save file used as an array index must be range-checked first. `ActorList` is `Dim`ed `[65535]`, but a client-supplied ActorID also needs `<> Null` check on the slot (most slots are empty). Pattern in [ServerNet.bb:2398](src/Modules/ServerNet.bb#L2398):

```basic
If ActorID < 0 Or ActorID > 65535 Or ActorList(ActorID) = Null
    WriteLog(MainLog, "rejecting invalid ActorID " + ActorID)
    RCE_Send(Host, M\FromID, P_..., "N", True)
    Exists = True : Exit
EndIf
```

### Handle-lookup Null discipline

`Object.X(handle)` returns `Null` for stale or invalid handles — it does not error. Any deref on the result without a `<> Null` check is a crash waiting for a freed-but-unreferenced handle. This applies on both sides:

- **Server**: handles read off the wire (`Object.ActorInstance(RCE_IntFromStr(...))`, `Object.ScriptInstance(...)`, `Object.DroppedItem(...)`). The client can send any 4 bytes.
- **Client globals**: `PlayerTarget`, `CharInteract`, and similar "currently selected" handles. Cleanup at the source side (`SafeFreeActorInstance` clears `PlayerTarget`, `P_ActorDead` / `P_ActorGone` clear it on remote death/zone-leave) catches most paths but there is no compiler enforcement. Code that runs **every frame** (`UpdateCombat`, the selection-highlight `LinePick`) is the highest-impact site for a stale-handle crash — one missed cleanup is a guaranteed crash on the next tick.

Pattern:

```basic
AI.ActorInstance = Object.ActorInstance(SomeHandle)
If AI = Null
    ; Clear the source global so the next iteration doesn't hit this again.
    SomeHandle = 0
    ; Skip the work that needed AI; don't crash.
    Return       ; or `: Continue`, depending on the loop
EndIf
; ... deref AI freely below ...
```

For outbound packets that include an optional target (`P_SpellUpdate "F"`, `P_ItemScript`), the server-side handlers already tolerate a missing target, so the client should send the packet **without** the target bytes rather than crash:

```basic
If PlayerTarget > 0
    AI.ActorInstance = Object.ActorInstance(PlayerTarget)
    If AI <> Null Then Pa$ = Pa$ + RCE_StrFromInt$(AI\RuntimeID, 2)
EndIf
RCE_Send(Connection, PeerToHost, P_X, Pa$, True)
```

See PRs [#144](https://github.com/RydeTec/rcce2/pull/144) (every-frame `PlayerTarget` cluster) and the audit history of `Object.ActorInstance` callers under `src/Modules/` for the established pattern.

The same shape applies to `AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)` lookups: an actor with a stale `ServerArea` (mid-warp, freed zone, brief window during `SetArea` re-binding) makes the lookup return Null. The standard recovery is to skip the broadcast loop / per-tick update that needed it — the actor's in-memory state (HP, position, attribute changes) has already applied; only the network propagation drops, and the next tick after `SetArea` settles will reach the actor again. PRs [#154](https://github.com/RydeTec/rcce2/pull/154) / [#155](https://github.com/RydeTec/rcce2/pull/155) / [#176](https://github.com/RydeTec/rcce2/pull/176) / [#182](https://github.com/RydeTec/rcce2/pull/182)–[#188](https://github.com/RydeTec/rcce2/pull/188) cover the full sweep across `GameServer.bb` / `ScriptingCommands.bb` / `ServerNet.bb` / `Server.bb`.

### Float sanitisation at the BVM / wire boundary

Script-supplied or wire-supplied floats that flow into actor state and get broadcast to clients have to clamp NaN/Inf at the boundary, not at the downstream readers. The two helpers in [RCEnet.bb](src/Modules/RCEnet.bb):

- `ClampWorldCoord#(v#)` — rejects NaN/Inf and clamps to `±WorldCoordMax#`. Use for X/Y/Z positions and destinations.
- `ClampSaneFloat#(v#)` — rejects NaN/Inf and clamps to `±1e9`. Use for non-position floats (yaw, animation speed, UI dims, emitter offsets that are actor-relative).

Both work by `If v > -MAX And v < MAX Then Return v` — the comparison rejects NaN because NaN is unordered. **Don't** try to "check for NaN" some other way; Blitz has no `IsNaN` primitive and the comparison trick is the canonical approach.

Pattern (`BVM_MOVEACTOR`):

```basic
Actor\X# = ClampWorldCoord#(Param2#)
Actor\Y# = ClampWorldCoord#(Param3#)
Actor\Z# = ClampWorldCoord#(Param4#)
```

A single NaN in a broadcast position poisons spatial code (collision, LOD culling, `EntityDistance#`) on every receiving client; NaN yaw poisons rotation matrices; NaN anim speed locks up the animation timer for that actor on every receiver. The BVM sweep (#237–#239) covered `BVM_MOVEACTOR`, `BVM_ROTATEACTOR`, `BVM_SETACTORDESTINATION`, `BVM_SPAWN`, `BVM_SPAWNITEM`, `BVM_ANIMATEACTOR`, `BVM_CREATEEMITTER`. The server's `P_InventoryUpdate "D"` drop-item handler (ServerNet.bb ~1467) is the original template.

### Iterator-during-iteration hazards (Blitz3D `For Each` + `Delete`)

Blitz3D's `For X = Each Type` iterator advances via the deleted element's "next" pointer on each `Next`. Calling `Delete X` (or `FreeActorInstance(X)` / `Delete PausedScript` etc.) inside the loop body corrupts the cursor for the next iteration.

Three established fixes, in order of preference:

1. **After-cursor walk** — capture `XNext = After X` *before* the Delete. Works when the body only deletes the current element. Pattern in `Scripting.bb:204`, `ServerNet.bb:1310`, `GameServer.bb:163`:

    ```basic
    Local PS.PausedScript = First PausedScript
    Local PSNext.PausedScript = Null
    While PS <> Null
        PSNext = After PS
        ; ... maybe Delete PS ...
        PS = PSNext
    Wend
    ```

2. **Deferred kill list** — collect into a side type, process after the loop. Pattern in `GameServer.bb`'s `DeferKillActor` / `ProcessPendingKills`. Right tool when the loop body might delete *multiple* actors (or other types) including ones past the cursor.

3. **Restart-on-Delete** — re-enter the For loop after every Delete. Right tool when the body recurses and the recursion can delete elements past the outer cursor's captured `After` pointer (the After-walk's invariant breaks). Pattern in `Actors.bb`'s `FreeActorInstanceSlaves` (PR #246):

    ```basic
    Local Found = True
    While Found
        Found = False
        For A2.ActorInstance = Each ActorInstance
            If A2\Leader = A
                Found = True
                FreeActorInstanceSlaves(A2)  ; recursive
                FreeActorInstance(A2)        ; Deletes A2
                Exit                          ; fresh iterator next outer pass
            EndIf
        Next
    Wend
    ```

    O(n×items) worst case but cheap (body is a field comparison) and safe under recursive deletion of arbitrary list positions.

### Strict-mode tests

Test files under [src/Tests/](src/Tests/) use `Strict` + `EnableGC` at the top. They cannot include `Server.bb` or other heavy entry points (would pull in network/world deps). Instead they `Include` the one module under test and *inline-stub* its missing dependencies — see [src/Tests/Modules/ItemsTest.bb:8-49](src/Tests/Modules/ItemsTest.bb#L8) for the canonical pattern. The **rcce2-test-writing** skill walks through this.

### Privilege gating in BVM commands

BVM functions that mutate global server state (`BVM_BANPLAYER`, `BVM_SETGOLD`, `BVM_WARP`, faction mutators) must guard with `If Not BVM_RequirePrivileged() Then Return` at the top. Functions that take an actor handle but are safe when targeting the script's own actor use `BVM_RequireSelfOrPrivileged(handle)` instead. Without these, any NPC's right-click script can ban its own clicker.

Four categories beyond the obvious mutator gate:

1. **Anything that opens a host-side resource** (needs `BVM_RequirePrivileged()`). UDP sockets (PR #233 — `BVM_CreateUDPStream` / `SendUDPMsg` / `RecvUDPMsg` / etc.), file system (`BVM_DELETEFILE` / `BVM_WRITEFILE` / `BVM_OPENFILE` / `BVM_APPENDFILE` / `BVM_CREATEDIR`), and arbitrary SQL (`BVM_MYSQLQUERY`). Without the gate, any NPC's right-click script could open sockets, write arbitrary files, or run arbitrary SQL using the server's permissions.
2. **Handle-walking helpers for those resources** (needs `BVM_RequirePrivileged()`). Once an entry-point that creates a handle is gated, the row/free family that walks the same handle space (PR #234 — `BVM_MYSQLNUMROWS` / `MYSQLFETCHROW` / `MYSQLGETVAR` / `MYSQLFREEQUERY` / `MYSQLFREEROW`) must be gated too. Otherwise a non-priv script can receive a handle via `SCRIPTGLOBAL`/`SUPERGLOBAL` passing and walk privileged data, or free a handle the server itself is using (LoadCharacter, SaveActor) by guessing the integer.
3. **Fatal-failure entry points** (needs `BVM_RequirePrivileged()`). `BVM_RUNTIMEERROR` (PR series, see audit comment in the function) lets the caller `RuntimeError()` the entire server process. Gate it so non-priv scripts log + return instead.
4. **Equivalent-effect bypasses of an already-gated function** (use the same gate as the peer — and **don't downgrade**: if the peer uses `RequirePrivileged`, the bypass needs `RequirePrivileged` too, not `RequireSelfOrPrivileged`). If `BVM_SET*` is gated and `BVM_CHANGE*` / `BVM_GIVE*` / a per-attribute `BVM_SET*` produces the same observable outcome (currency change, progression, target death, zone teardown), the unguarded variant fully defeats the gate. Grep before assuming a gate is sufficient: every `Set*` with a Change*/Give*/per-attribute peer is a candidate. Threat: a non-priv NPC's RightClick / Examine / Trade / ItemScript reaches the same effect via the unguarded name. Pairs to keep in lockstep (post-sweep): `BVM_SETGOLD` / `BVM_CHANGEGOLD`, `BVM_SETMONEY` / `BVM_CHANGEMONEY`, `BVM_SETACTORLEVEL` / `BVM_GIVEXP` / `BVM_GIVEKILLXP` (XP path triggers the LevelUp ThreadScript), `BVM_KILLACTOR` / `BVM_SETATTRIBUTE` / `BVM_CHANGEATTRIBUTE` (the HealthStat branch calls `KillActor(...)` when Value[Health] <= 0), `BVM_SETMAXATTRIBUTE` / `BVM_CHANGEMAXATTRIBUTE` (no `KillActor` fall-through but still a brick vector — `SetMaxAttribute(player, "Health", 1)` permanently nerfs max HP to 1 so the next damage tick kills, `(player, "Speed", 0)` locks in place, `(player, "Energy", 0)` disables spells), `BVM_SETREPUTATION` (reputation drives faction-interaction gating — `SetReputation(clicker, -10000)` bricks reputation-gated vendor / quest / zone access), `BVM_SETLEADER` (function-body guard restricts Param1 to NPCs so player-as-pet is impossible, but Param2 can be the clicker — `SetLeader(SomeWorldGuard, clicker)` recruits world NPCs as private pets), `BVM_SETABILITYLEVEL` (`SetAbilityLevel(clicker, "<spell>", 0)` zeros out an ability; iteration over the spell list bricks the entire combat toolkit), `BVM_SETITEMHEALTH` (`SetItemHealth(clickerEquippedSword, 0)` bricks durability — iterate `Inventory\Items[]` to gut all gear in one click; note Param1 is an ItemInstance handle so self-or-priv doesn't apply at all, full-priv is the only sensible gate), `BVM_SETRESISTANCE` (consumed by the combat damage formula the same way `FactionRatings[]` is — `SetResistance(clicker, "Fire", -100)` → catastrophic damage taken; `(clicker, "Fire", 100)` → invulnerable in PvE; symmetric to the already-gated `SETFACTIONRATING`), `BVM_SETACTORGENDER` / `BVM_SETACTORBEARD` / `BVM_SETACTORHAIR` / `BVM_SETACTORFACE` / `BVM_SETACTORCLOTHES` (cosmetic-tier griefing — same shape, consistent gating across the appearance cluster — no shipped content scripts in `data/` use these). All families use `BVM_RequirePrivileged()` — same as their `SET*` / `KILLACTOR` peer.

**Spawn-side privileged-script allowlist (PR #329):** the carve-out cited as fix path (b) in earlier revisions of this document. `Scripting.bb`'s `LoadPrivilegedScripts` reads a server-controlled allowlist from `Data\Server Data\Privileged Scripts.dat` (one script `Name$` per line) at server boot. `ThreadScript` then elevates `Privileged` to `True` at spawn time for any script whose name is on the list — **elevation only, never demote** (a caller that passes `Privileged=1` explicitly keeps it). The elevation also gates on `hSI = 0` — i.e. only fires when the spawn is engine-initiated (chat dispatch, spell-cast, right-click, NPC-init), **not** when invoked via `BVM_THREADEXECUTE` from inside another script. Without that gate, a hostile non-priv script could call `ThreadExecute("In-game Commands", "ItemPack", victim, ...)` and inherit the elevation via the allowlisted name; the `hSI = 0` check correctly separates engine-initiated spawns (no script currently running) from script-chained spawns.

This is the right shape because it puts the trust decision at the spawn boundary (where you know which content script is running) rather than per-BVM (where you'd have to enumerate every privileged effect). The shipped allowlist contains the five scripts that need it: `In-game Commands`, `AOE Damage Spell Template`, `marriage`, `Click_marriage`, `Spawn_Test`. New content scripts that need privileged BVMs from non-priv spawns get added by editing the .dat file (which requires a server restart — there is no `/reloadprivilegedscripts` admin command).

Trust caveat: the .dat is in `Server Data/` (not client-writable), but anyone editing it must read the listed scripts end-to-end since elevation grants ALL gated BVMs, not just the four (`SETACTORAISTATE` / `SETACTORTARGET` / `SETNAME` / `SETTAG`) that motivated the carve-out. With this in place all four BVMs in the previously-deferred "Known ungated brick / griefing surface" cluster are now `BVM_RequirePrivileged()`-gated; `BVM_SETACTORGROUP` (the fifth) was closed earlier by PR #325 with no allowlist needed (no shipped callers). **Do not pick `BVM_RequireSelfOrPrivileged(Param1%)` reflexively for "actor-state mutations".** For clicker-driven script spawns (`Examine` / `Trade` / `RightClick` / `ItemScript`), [ServerNet.bb](src/Modules/ServerNet.bb) calls `ThreadScript(script, method, Handle(clicker), Handle(NPC))`, so `SI\AI = Handle(clicker)` — a self-or-priv gate would pass `Param1 = clicker_handle` and the lethal call goes through. The `Self` shortcut is correct only for engine-tick spawns where `SI\AI = Handle(NPC_owning_the_tick)` (see `BVM_MOVEACTOR` / `BVM_ROTATEACTOR` / `BVM_SETACTORDESTINATION`); for any path where the gate's job is "block the clicker from being a target," it has to be `RequirePrivileged`. Quest-reward NPCs that need to grant gold/XP/etc. must spawn their reward script with the privileged flag, or route through `BVM_OPENTRADING` for player-driven transactions.

The compound rule: if the function reaches out to a resource the host owns (kernel handle, file descriptor, socket, database connection), can terminate the server, or replicates the effect of an already-gated peer, it's privileged.

## Workflow

### Branching + PRs

- Branch from `develop`. PRs target `develop`. Releases are `develop → master` PRs.
- Commits include the trailer `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` when authored with Claude.
- PRs merge via `gh pr merge <N> --merge --admin --delete-branch`. **Squash merge is not used.**
- Never push directly to `develop` or `master`.

### CI ([.github/workflows/ci.yml](.github/workflows/ci.yml))

- Build step caches BlitzForge binaries keyed on the submodule SHA. Most PRs hit cache.
- Test step runs every file under `src/Tests/` and exits 1 on first failure.
- **Generator-staleness gate**: after the test step, CI runs `bash scripts/gen_packet_index.sh --check` and `bash scripts/gen_bvm_reference.sh --check`. Both auto-regenerate the two reference docs ([`docs/protocol/index.md`](docs/protocol/index.md), [`docs/bvm-reference.md`](docs/bvm-reference.md)) from source and exit non-zero if the committed file no longer matches. If you touch any of [`src/Modules/ServerNet.bb`](src/Modules/ServerNet.bb), [`src/Modules/ClientNet.bb`](src/Modules/ClientNet.bb), [`src/Modules/Packets.bb`](src/Modules/Packets.bb), [`src/Modules/RC_Standard_Invoker.bb`](src/Modules/RC_Standard_Invoker.bb), or [`src/Modules/ScriptingCommands.bb`](src/Modules/ScriptingCommands.bb), rerun the two generators and commit the regenerated docs before pushing — CI will fail with a diff message otherwise.
- **Known intermittent flake**: `ItemsTest.bb` sometimes fails with `Stack overflow!` for unrelated PRs. If a PR's CI fails *only* with that error and your change doesn't touch items/inventory/serialization, retry via close + reopen of the PR:
  ```bash
  gh pr close <N> && sleep 3 && gh pr reopen <N>
  ```
  Two retries is plenty; if it still fails, investigate.

### Git submodules

`compiler/BlitzForge`, `extras/vscode-blitz-forge`, `extras/reshade` are submodules.

```bash
# After cloning:
git submodule update --init --recursive

# Updating: stage submodule pointer like any other file, but never `git submodule update --remote`
# unless you intend to bump the version. The BlitzForge submodule moves frequently;
# bump it in a dedicated "BlitzForge bump" PR (see merged PR history for examples).
```

## Gotchas

- **Blitz3D array semantics**: `Field arr[N]` and `Dim arr(N)` both allocate **`N+1` slots, indexed `0..N` inclusive**. `For i = 0 To N` is correct, not `0 To N-1`. Frequent source of "off-by-one" misreads.
- **BVM opcode ordering**: opcodes in [RC_Standard_Invoker.bb](src/Modules/RC_Standard_Invoker.bb) are assigned by the BlitzForge command-set parser in **alphabetical order of function name**. Inserting `BVM_NEAREST_*` between `BVM_NAME` (case 436) and `BVM_NEWQUEST` (case 437) shifts every subsequent case number. Don't manually renumber unless you mean to; the **rcce2-bvm-command** skill covers the safe insertion procedure.
- **Stale `.bb_bak1` / `.bb_bak2` files**: gitignored snapshots from a legacy IDE. Never edit them; they are not the source of truth. If you see one in a diff, something is wrong.
- **Backup file naming**: same convention applies to `ServerNet.bb_bak2` etc. — ignore.
- **PowerShell vs Bash**: both work via tools (PowerShell for `.bat` build scripts, Bash for git/gh). `gh.exe` lives at `/c/Program Files/GitHub CLI/gh.exe` in Bash — `gh` alone isn't on PATH in MSYS.
- **Encoding**: write `.bb` files as UTF-8 without BOM. `Set-Content` and `Out-File` default to UTF-16 LE; use `-Encoding utf8` if you must use them. Prefer the Write tool, which is correct by default.
- **`Continue` keyword**: BlitzForge added it but it was buggy in earlier versions — see commit `78f3204` "Fix RC Terrain Editor use of continue keyword". Safe to use now in current BlitzForge, but be wary inside `Select Case` inside `For` (test the specific shape).
- **`Local` shadowing `Global`**: Strict mode flags this. Many legacy modules in `src/Modules/` aren't Strict — they tolerate the pattern but it's still a code smell.
- **`.cursor/` and `compiler/IDEs/Visual Studio Code.url`**: local IDE state, not tracked. Don't `git add` them.

## Memory + skills hygiene for you (the agent)

When you discover something **non-obvious, project-specific, and durable** while working, save it as a memory (see your auto-memory instructions). Examples of save-worthy:

- A subtle invariant: "the Bank used by `RCE_StrFromInt$` is **4 bytes** (verified at [RCEnet.bb:2](src/Modules/RCEnet.bb#L2): `CreateBank(4)`); `Length > 4` writes past the Bank with undefined behavior. `PokeInt` does not bounds-check."
- A workflow tip the user explicitly confirmed.
- A surprising consequence: "ScriptInstance handles can become stale across `BVM_THREADEXECUTE` boundaries; always re-resolve via `Object.ScriptInstance(hSI)`."

Do not save things derivable from reading the code. The memory store is at `C:\Users\dyanr\.claude\projects\C--Users-dyanr-Desktop-rcce2\memory\`.

## What's NOT in this repo

- The game's actual content (worlds, scripted quests, custom art). The `data/` directory has a starter project but real content lives in user installations.
- BlitzForge compiler source — it's in the [submodule](compiler/BlitzForge). Has its own [CLAUDE.md](compiler/BlitzForge/CLAUDE.md) for C++ work.
- Documentation site / wiki — that's on `realmcrafter.fandom.com`, not in-repo.
