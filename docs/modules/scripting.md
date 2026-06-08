<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Scripting.bb**

This module is the runtime half of the in-game scripting system: it compiles `.rsl` / `.rcscript` source files into BVM (Blitz Virtual Machine) bytecode at server boot, spawns and dispatches script instances (`ScriptInstance`) in response to game events, and manages the per-instance lifecycle (run, wait, resume, free). The native function surface that scripts can call — 222 `BVM_*` functions covering actor / item / spell / party / world / I/O / chat / persistence — is implemented in [ScriptingCommands.bb](scriptingcommands.md) and catalogued in the auto-generated [BVM scripting reference](../bvm-reference.md). The opcode-dispatch glue that maps BVM bytecode to those native function pointers lives in [RC_Standard_Invoker.bb](rc_standard_invoker.md).

This page is an overview; see the BVM reference for the per-function API.

## Script source files and compilation

Server boot walks `Data\Server Data\Scripts\` and compiles each `.rsl` / `.rcscript` file into a `ScriptSource` record:

* Each file becomes one `ScriptSource` keyed by case-insensitive base name (e.g. `Default.rsl` → `"Default"`).
* The BVM compiles the source against the command set defined in [RC_Standard_Invoker.bb](rc_standard_invoker.md) — that file is the single source of truth for which `BVM_*` natives are visible and what their signatures look like.
* Compilation failures log to `MainLog` (`"Script Foo does not exist"`, parse errors, etc.) and the script does not register; subsequent `ThreadScript` calls against it become no-ops.

The five built-in entry-point names content authors implement most often:

| File | Entry method | Triggered by |
|---|---|---|
| `Default.rsl` | `Examine`, `Trade`, `RightClick`, `Attack`, `Death`, `LevelUp` | clicker / combat events on actors with no per-actor script |
| `Login.rsl` | `Main` | account login completion |
| `Death.rsl` | `Main` | player death |
| `LevelUp.rsl` | `Main` | XP threshold crossed |
| `In-game Commands.rsl` | `Main(Command$, Params$)` | unknown `/slash` commands (fallback; the 30 built-in commands at [ServerNet.bb P_ChatMessage](servernet.md) take precedence) |

Per-actor `Actor\Script$` / `Actor\DeathScript$` override the Default for individual NPCs.

## Script instance lifecycle

A `ScriptInstance` is one running execution of a script's entry method. It carries:

* The compiled bytecode (shared via the parent `ScriptSource`).
* `AI` — the spawning actor (clicker for Examine / Trade / RightClick / ItemScript; NPC for engine ticks).
* `AIContext` — the target actor (the NPC for Examine / Trade / RightClick; usually `Null` for engine ticks).
* `Privileged` — set when the spawning context is the server itself (Login, Death, LevelUp, engine ticks) or a DM-initiated invocation. Determines whether the gate-checked `BVM_*` calls in [ScriptingCommands.bb](scriptingcommands.md) execute or short-circuit.
* `WaitResult$`, `WaitReason`, etc. — set when the script issues a `Wait` opcode (e.g. `WaitSpeak`); the `PausedScript` walk in `Scripting.bb` resumes it when the matching event fires.

`ThreadScript(Name$, Func$, ActorHandle, ContextActorHandle, Param$, Privileged%)` is the canonical entry point. It allocates a `ThreadScript` work item; the main loop's `UpdateScripts()` pass promotes it to a live `ScriptInstance` and starts execution.

## Privilege model and the clicker-handle trap

The most important security invariant in the script-spawn surface:

> For Examine / Trade / RightClick / ItemScript spawns, `SI\AI = Handle(clicker)` — not `Handle(NPC)`.

This means any `BVM_RequireSelfOrPrivileged(Param1)` gate where `Param1` is the target-actor parameter does NOT block clicker exploits: the clicker is the "self" and passes the gate trivially. The recently-merged hardening sweeps (#260, #237–#239) corrected several such mistakes by upgrading them to `BVM_RequirePrivileged()`.

The four privilege-gate categories enforced today (see `CLAUDE.md` for the full text):

1. **Resource-opening entry points** — sockets, file I/O, arbitrary SQL: must be `Privileged`.
2. **Handle-walking helpers** for those resources — must be `Privileged` too once the entry points are gated.
3. **Fatal-failure entry points** like `BVM_RUNTIMEERROR`: must be `Privileged` (otherwise any script can crash the server).
4. **Equivalent-effect bypasses** — when `BVM_SET*` is gated and a sibling `BVM_CHANGE*` / `BVM_GIVE*` / per-attribute `BVM_SET*` produces the same observable effect, the bypass needs the same gate (not a downgraded `SelfOrPrivileged`).

The [BVM scripting reference](../bvm-reference.md) shows each function's gate. When auditing, watch for the asymmetric pattern: a sibling `Privileged` next to an ungated peer is almost always a bug.

## Float and integer hardening at the BVM boundary

Script-supplied or wire-supplied numerics that flow into actor state get clamped at the BVM boundary, not the downstream readers:

* `ClampWorldCoord#(v#)` — for X/Y/Z positions and destinations.
* `ClampSaneFloat#(v#)` — for non-position floats (yaw, animation speed, UI dims).

Both reject NaN/Inf and clamp to a sane range. Without these, a single NaN broadcast position poisons spatial code (collision, LOD culling, `EntityDistance#`) on every receiving client. See `CLAUDE.md`'s "Float sanitisation at the BVM / wire boundary" for the threat model and which BVMs the sweep covers.

## Soft-fail discipline

Script-side errors should NOT call `RuntimeError(...)` — that crashes the server, disconnecting every other player. Either:

* `Throw New ErrorDTO("descriptive message")` and let the script's own `TryCatch` handle it, or
* Log via `WriteLog(MainLog, ...)` and return a safe sentinel (`Null`, `0`, `""`).

`BVM_RUNTIMEERROR` is intentionally gated to `Privileged` so non-priv scripts can't exploit this to take down the server.

## Iterator-during-iteration hazards

A `ScriptInstance` whose body Deletes itself (or its peers via `BVM_THREADEXECUTE` chains) while the engine is iterating the global list is the canonical iterator-Delete hazard. `Scripting.bb` uses the established **after-cursor walk** pattern: capture `SNext = After S` before `Delete S`. See `CLAUDE.md`'s "Iterator-during-iteration hazards" section.

## Related modules

* [RC_Standard_Invoker.bb](rc_standard_invoker.md) — opcode-dispatch table mapping BVM bytecode to native function pointers.
* [ScriptingCommands.bb](scriptingcommands.md) — 222 `BVM_*` function bodies with privilege gates.
* [BVM scripting reference](../bvm-reference.md) — auto-generated catalog of every BVM function, signature, and gate.
* [ServerNet.bb](servernet.md) — packet handlers that spawn scripts (`P_RightClick`, `P_Examine`, `P_Trade`, `P_ItemScript`, `P_ChatMessage`).
