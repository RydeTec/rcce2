<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**ScriptingCommands.bb**

This module is the **implementation half of the in-game scripting surface**. It holds the bodies of every `BVM_*` native function that `.rsl` / `.rcscript` content scripts can call — 222 functions across ~3,300 lines covering actor / item / spell / party / world / I/O / chat / persistence / SQL / UDP / quest. The companion files are:

- [Scripting.bb](scripting.md) — the runtime half (script-source compilation, `ScriptInstance` lifecycle, `ThreadScript` dispatch).
- [RC_Standard_Invoker.bb](../../src/Modules/RC_Standard_Invoker.bb) — the opcode-dispatch table mapping BVM bytecode to the native function pointers defined here. **Modifying signatures in either file requires the other to stay in lockstep** — see "Adding a new BVM function" below.
- [BVM scripting reference](../bvm-reference.md) — auto-generated per-function catalog with current gate status. Look here first when documenting a specific function.

This page is the **conceptual overview** of how the file is structured and what disciplines apply when adding or editing functions. For per-function API, use the BVM reference.

## File structure

`ScriptingCommands.bb` is a long flat file — 222 `Function BVM_<NAME>(...)` definitions in roughly-grouped sections. There is no `Type` declaration in the file; all state lives in the actor / item / spell / etc. modules. The functions are pure work-units called by the BVM runtime.

Functions are roughly grouped by theme but the file has no enforced sectioning — names interleave in places. Use the **landmark functions** below as navigation anchors (grep `^Function BVM_<NAME>` for exact lines; line numbers drift, names don't):

| Theme | Landmark functions |
|---|---|
| Privilege-gate helpers (internal — not advertised in `RC_Standard_Invoker.bb`'s contract) | `BVM_RequirePrivileged`, `BVM_RequireSelfOrPrivileged`, `BVM_ScriptPathIsSafe`, `BVM_SetWaitResult` |
| Actor lifecycle | `BVM_SPAWN`, `BVM_KILLACTOR`, `BVM_SETLEADER` |
| Actor appearance | `BVM_SETACTORGENDER`, `BVM_SETACTORBEARD`, `BVM_SETACTORHAIR`, `BVM_SETACTORFACE`, `BVM_SETACTORCLOTHES` |
| Actor movement + animation | `BVM_MOVEACTOR`, `BVM_ROTATEACTOR`, `BVM_SETACTORDESTINATION`, `BVM_ANIMATEACTOR`, `BVM_SETACTORTARGET`, `BVM_SETACTORAISTATE` |
| Items + inventory | `BVM_SPAWNITEM`, `BVM_GIVEITEM`, `BVM_HASITEM`, `BVM_SETITEMHEALTH`, `BVM_ITEMATTRIBUTE` |
| Spells + abilities | `BVM_ADDABILITY`, `BVM_DELETEABILITY`, `BVM_SETABILITYLEVEL`, `BVM_ABILITYLEVEL`, `BVM_ABILITYKNOWN`, `BVM_ABILITYMEMORISED` |
| Attributes | `BVM_SETATTRIBUTE`, `BVM_CHANGEATTRIBUTE`, `BVM_SETMAXATTRIBUTE`, `BVM_CHANGEMAXATTRIBUTE`, `BVM_ATTRIBUTE`, `BVM_MAXATTRIBUTE` |
| Factions + reputation + resistance | `BVM_SETFACTIONRATING`, `BVM_CHANGEFACTIONRATING`, `BVM_SETHOMEFACTION`, `BVM_SETREPUTATION`, `BVM_SETRESISTANCE` |
| Currency + progression | `BVM_SETGOLD`, `BVM_CHANGEGOLD`, `BVM_SETMONEY`, `BVM_CHANGEMONEY`, `BVM_GIVEXP`, `BVM_GIVEKILLXP`, `BVM_SETACTORLEVEL` |
| Party + trading | `BVM_OPENTRADING`, `BVM_COUNTPARTYMEMBERS`, `BVM_PARTYMEMBER` |
| Quest + script-wait state | `BVM_NEWQUEST`, `BVM_UPDATEQUEST`, `BVM_COMPLETEQUEST`, `BVM_DELETEQUEST`, `BVM_QUESTSTATUS`, `BVM_QUESTCOMPLETE`, `BVM_SETWAITKILL`, `BVM_SETWAITSPEAK`, `BVM_SETWAITITEM`, `BVM_GETWAITRESULT` |
| World + zone | `BVM_WARP`, `BVM_CREATEZONEINSTANCE`, `BVM_REMOVEZONEINSTANCE`, `BVM_ZONEINSTANCEEXISTS`, `BVM_ACTORSINZONE`, `BVM_FIRSTACTORINZONE`, `BVM_NEXTACTORINZONE` |
| Output / debug | `BVM_OUTPUT`, `BVM_BUBBLEOUTPUT`, `BVM_CREATEFLOATINGNUMBER`, `BVM_DEBUGLOG`, `BVM_SCRIPTLOG`, `BVM_RUNTIMEERROR` |
| Persistence (MySQL) | `BVM_MYSQLQUERY`, `BVM_MYSQLNUMROWS`, `BVM_MYSQLFETCHROW`, `BVM_MYSQLGETVAR`, `BVM_MYSQLFREEQUERY`, `BVM_MYSQLFREEROW`, `BVM_SQLACCOUNTID`, `BVM_SQLACTORID` |
| UDP networking | `BVM_CreateUDPStream`, `BVM_SendUDPMsg`, `BVM_RecvUDPMsg`, `BVM_CloseUDPStream` |
| Filesystem (path-traversal guarded) | `BVM_DELETEFILE`, `BVM_WRITEFILE`, `BVM_OPENFILE`, `BVM_APPENDFILE`, `BVM_CREATEDIR`, `BVM_FILESIZE`, `BVM_FILETYPE` |
| Utility | `BVM_SPLIT`, `BVM_HOUR`, `BVM_MINUTE`, `BVM_THREADEXECUTE`, `BVM_PERSISTENT`, `BVM_SAVESTATE` |

For any specific function, grep `^Function BVM_<NAME>` directly. The auto-generated [BVM reference](../bvm-reference.md) catalog has every signature + current gate.

## Privilege gating — the load-bearing security invariant

**52 functions** are `BVM_RequirePrivileged`-gated (verified by `grep -cE "If Not BVM_RequirePrivileged" src/Modules/ScriptingCommands.bb`); **4 functions** use `BVM_RequireSelfOrPrivileged`. The remainder are ungated (pure-read getters, cosmetic mutators, or per-tick NPC helpers that are caller-trusted).

The four privilege-gate categories the codebase enforces (CLAUDE.md → "Privilege gating in BVM commands" has the canonical statement):

1. **Resource-opening entry points** — sockets, file I/O, arbitrary SQL: must be `Privileged`. Non-priv scripts cannot open host resources.
2. **Handle-walking helpers** for those resources (`BVM_MYSQLNUMROWS`, `BVM_MYSQLFETCHROW`, etc.): must be `Privileged` once the entry points are. Otherwise a non-priv script could receive a handle via `SCRIPTGLOBAL` and walk privileged data.
3. **Fatal-failure entry points** (`BVM_RUNTIMEERROR`): must be `Privileged`. Otherwise any clicker script could crash the server.
4. **Equivalent-effect bypasses** — when a `BVM_SET*` is gated, a sibling `BVM_CHANGE*` / `BVM_GIVE*` / per-attribute / per-max `BVM_SET*` that produces the same observable effect needs **the same gate, not a downgraded `SelfOrPrivileged`**. The clicker-handle trap section below explains why.

### The clicker-handle trap

Every audit of privilege gates needs to internalise this invariant:

> For Examine / Trade / RightClick / ItemScript spawns, [`ServerNet.bb`](servernet.md) calls `ThreadScript(script, method, Handle(clicker), Handle(NPC))`, so the spawned script's `SI\AI = Handle(clicker)` — **not** `Handle(NPC)`.

Consequence: `BVM_RequireSelfOrPrivileged(Param1)` does **not** block clicker exploits when `Param1` is the target the clicker would attack. The clicker IS "self" and passes the gate. PRs [#300](https://github.com/RydeTec/rcce2/pull/300), [#301](https://github.com/RydeTec/rcce2/pull/301), [#304](https://github.com/RydeTec/rcce2/pull/304) and earlier swept the asymmetric pairs to fix this.

The currently-gated brick-vector cluster (CLAUDE.md "Pairs to keep in lockstep"):

| Function | Bypass-of | Threat shape |
|---|---|---|
| `BVM_CHANGEGOLD` | `BVM_SETGOLD` | Currency mutation |
| `BVM_CHANGEMONEY` | `BVM_SETMONEY` | Currency mutation |
| `BVM_GIVEXP` / `BVM_GIVEKILLXP` | `BVM_SETACTORLEVEL` | XP triggers `LevelUp` ThreadScript |
| `BVM_SETATTRIBUTE` / `BVM_CHANGEATTRIBUTE` | `BVM_KILLACTOR` | HealthStat branch falls through to `KillActor` |
| `BVM_SETMAXATTRIBUTE` / `BVM_CHANGEMAXATTRIBUTE` | (brick vector) | `SetMaxAttribute(player, "Health", 1)` → permanent 1 HP → next damage kills |
| `BVM_SETREPUTATION` | `BVM_SETHOMEFACTION` | Faction-gated content lockout |
| `BVM_SETLEADER` | (pet recruitment) | `SetLeader(SomeWorldGuard, clicker)` recruits world NPCs as private pets |
| `BVM_SETABILITYLEVEL` | `BVM_SETATTRIBUTE` | Zero out chosen ability; iterate spell list to brick the entire combat toolkit |
| `BVM_SETITEMHEALTH` | (item brick) | Zero durability on equipped gear |
| `BVM_SETRESISTANCE` | `BVM_SETFACTIONRATING` | `(clicker, "Fire", -100)` → catastrophic damage; `(clicker, "Fire", 100)` → PvE invulnerability |
| `BVM_REMOVEZONEINSTANCE` | (admin-only) | Destroy area instances |

The full regression-test contract lives in [`src/Tests/Modules/BVMPrivilegeGateTest.bb`](../../src/Tests/Modules/BVMPrivilegeGateTest.bb) — every newly-gated function has a `testXGateBlocksBrickingOwnAITarget` case proving full-priv is the correct choice (and that self-or-priv would have been wrong).

## Dead-API surface

Two functions live as commented-out stubs (`;Function BVM_<NAME>(...)`) — the underlying feature was disabled at the data-model level but the contract entries in `RC_Standard_Invoker.bb` stay alive for opcode stability:

- **`BVM_SETOWNER`** and **`BVM_SCENERYOWNER`** (grep `;Function BVM_SETOWNER` / `;Function BVM_SCENERYOWNER` in this file) — the `OwnedScenery` type was removed from [ServerAreas.bb](serverareas.md) alongside its supporting code. PR [#297](https://github.com/RydeTec/rcce2/pull/297) added a stack-balance sentinel push to SCENERYOWNER's dispatch case (which had been popping 3 args + pushing nothing → silent stack corruption in caller expressions) and cross-linked all five dead-API sites with audit comments.

Do not remove the contract entries without an opcode-renumber audit of every `Case >= 501` in `RC_Standard_Invoker.bb`. The dispatch is keyed by opcode number; removal shifts every BVM alphabetically after `SCENERYOWNER` / `SETOWNER`.

## Float / integer hardening at the BVM boundary

Script-supplied numerics that flow into actor state and get broadcast to clients are clamped at the BVM boundary, not the downstream readers (CLAUDE.md → "Float sanitisation at the BVM / wire boundary"):

- `ClampWorldCoord#(v#)` — X/Y/Z positions and destinations (rejects NaN/Inf + clamps to `±WorldCoordMax#`).
- `ClampSaneFloat#(v#)` — non-position floats (yaw, animation speed, UI dims, emitter offsets) — rejects NaN/Inf + clamps to `±1e9`.

The covering sweep landed across `BVM_MOVEACTOR` / `BVM_ROTATEACTOR` / `BVM_SETACTORDESTINATION` / `BVM_SPAWN` / `BVM_SPAWNITEM` / `BVM_ANIMATEACTOR` / `BVM_CREATEEMITTER` (PRs #237–#239 era). A single NaN broadcast position poisons spatial code (collision, LOD culling, `EntityDistance#`) on every receiving client.

Integer-side: bounds-check-before-array-index is universal. `BVM_SETACTORGENDER`, `BVM_SETACTORBEARD`, `BVM_SETACTORHAIR`, `BVM_SETACTORFACE`, `BVM_SETACTORCLOTHES` all bound their appearance indices before subscripting the appearance arrays. `BVM_SPAWN` and `BVM_ACTORXPMULTIPLIER` bound `ActorList` indices before deref. `BVM_SETATTRIBUTE` family bounds the attribute index against the 40-slot Field — see [P_StatUpdate.md](../protocol/packets/P_StatUpdate.md) for the wire-side mirror.

## Handle-Null discipline

Most functions take an actor / item / script-instance handle as `Param1`. The canonical entry pattern is:

```basic
Function BVM_FOO(Param1%, ...)
    [If Not BVM_RequirePrivileged() Then Return]    ; if gated
    Actor.ActorInstance = Object.ActorInstance(Param1%)
    If Actor <> Null
        ; ... do work; safe to deref Actor\Field freely ...
    EndIf
End Function
```

`Object.ActorInstance(handle)` returns Null for stale or invalid handles — it does not error. Every body must check `<> Null` before any field access. CLAUDE.md → "Handle-lookup Null discipline" has the full pattern.

The same shape applies to `Object.ItemInstance`, `Object.ScriptInstance`, `Object.Account`, `Object.AreaInstance`, `Object.DroppedItem`. The latter (`AreaInstance` lookup off `AI\ServerArea`) is the mid-warp Null case PRs #154 / #176 / #182–#188 swept across the whole codebase.

## Adding a new BVM function

Three files must change in lockstep when adding a function:

1. **[src/Modules/ScriptingCommands.bb](../../src/Modules/ScriptingCommands.bb)** — add the `Function BVM_<NAME>(...)` body.
2. **[src/Modules/RC_Standard_Invoker.bb](../../src/Modules/RC_Standard_Invoker.bb)** — add the `s = s + "Function <NAME><BVM_<NAME>>(...args with defaults)"+Chr(10)` line in the runtime-string contract **and** add the matching `Case <N>: <pop args>: BVM_<NAME>(args) [: BVM_PushInt(result)]` block in the dispatch Select.
3. **[src/RC_Standard.bcs](../../src/RC_Standard.bcs)** — add the parallel `Function <NAME><BVM_<NAME>>(...)` line in the compile-time twin so BlitzForge can resolve the call at content-script compile time.

**The opcode `<N>` is not user-chosen** — the BlitzForge command-set parser assigns opcodes alphabetically by function name. The dispatch table's existing Case numbers reflect the current alphabetical ordering; inserting a new name shifts every Case downstream. The `.bb_bak1` / `.bb_bak2` files in source control are leftover snapshots; do not edit them.

See the `rcce2-bvm-command` skill in `.claude/skills/` for the safe insertion procedure (the alphabetical-shift trap is the most common gotcha).

### Privilege-gate decision tree

When the new function does anything beyond a pure-read getter:

1. **Does it open a host resource (file, socket, SQL)?** → `BVM_RequirePrivileged()`.
2. **Does it terminate the server (RuntimeError-equivalent)?** → `BVM_RequirePrivileged()`.
3. **Does it produce the same observable effect as an already-gated `SET*` / `KILLACTOR` / etc.?** → match the peer's gate, **do not downgrade**.
4. **Does it mutate state that affects a non-self target the clicker can name?** → `BVM_RequirePrivileged()` (NOT `SelfOrPrivileged` — see clicker-handle trap above).
5. **Is it called per-tick by NPC AI where `SI\AI = NPC`?** → `BVM_RequireSelfOrPrivileged(Param1)` is appropriate (this is the engine-tick spawn shape).
6. **Otherwise** (pure-read getter, cosmetic mutator with no side effect, no clicker-reachable target): ungated is fine — but ensure the impl has handle-Null discipline.

Every newly-gated function should also get a `Mock<NAME>` + 3-4 tests in [`src/Tests/Modules/BVMPrivilegeGateTest.bb`](../../src/Tests/Modules/BVMPrivilegeGateTest.bb), including the load-bearing `testXGateBlocksBrickingOwnAITarget` case (proves the clicker-shape that defeats SelfOrPrivileged is blocked).

## Notable historical hardening

| Sweep | What was fixed |
|---|---|
| Privilege-gating bypass cluster (PRs #260 / #237–#239 era) | Initial 7-function gate sweep documented in the test file header |
| Float sanitisation (PRs #237–#239) | `ClampWorldCoord` / `ClampSaneFloat` added to BVM_MOVE/ROTATE/SPAWN/etc. |
| Iterator hazards (PR era ~#240s–#259) | After-cursor walks in `BVM_REFRESHSCRIPTS` / `BVM_REMOVEZONEINSTANCE` and the `Delete` / `For-Each` pattern across the BVM dispatch. See `CLAUDE.md` → "Iterator-during-iteration hazards" for the canonical pattern. |
| MySQL row/query gate (PRs #233 / #234) | UDP family + MySQL handle-walkers gated to Privileged |
| SETMAXATTRIBUTE / CHANGEMAXATTRIBUTE ([PR #300](https://github.com/RydeTec/rcce2/pull/300)) | Brick vector — siblings of the already-gated SET/CHANGE pair |
| Faction / leader / ability / item-health / resistance ([PR #301](https://github.com/RydeTec/rcce2/pull/301)) | 5 more brick-vector gates closed |
| Spell race/class UX ([PR #304](https://github.com/RydeTec/rcce2/pull/304)) | P_SpellUpdate F-handler emit fix (not in this file but related) |

## Related modules

- [Scripting.bb](scripting.md) — script-source compilation + `ScriptInstance` lifecycle.
- [BVM scripting reference](../bvm-reference.md) — auto-generated per-function catalog with gate status.
- [`src/Modules/RC_Standard_Invoker.bb`](../../src/Modules/RC_Standard_Invoker.bb) — opcode-dispatch table (not yet documented as its own page; see commented sections in the file for layout).
- [`src/RC_Standard.bcs`](../../src/RC_Standard.bcs) — compile-time twin of the runtime contract string.
- [ServerNet.bb](servernet.md) — packet handlers that spawn scripts (`P_RightClick` / `P_Examine` / `P_Trade` / `P_ItemScript` / `P_ChatMessage` for `/script`).
- [`src/Tests/Modules/BVMPrivilegeGateTest.bb`](../../src/Tests/Modules/BVMPrivilegeGateTest.bb) — regression contract for every newly-gated function.
- CLAUDE.md → "Privilege gating in BVM commands" — canonical statement of the four gate categories.

