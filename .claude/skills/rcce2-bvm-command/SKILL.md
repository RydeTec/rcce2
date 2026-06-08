---
name: rcce2-bvm-command
description: Add, modify, or audit a BVM scripting command in rcce2. Invoke whenever the user wants to expose a new built-in function to Blitz game scripts (the BVM_* function family in ScriptingCommands.bb), tweak the dispatch table in RC_Standard_Invoker.bb, fix a BVM bounds check, change a function's privilege gating, or audit a scripting-engine vulnerability. Critical to know: BVM opcodes are assigned ALPHABETICALLY by function name. Inserting a single new function in the middle of the alphabet shifts every subsequent opcode by 1, requiring renumbering hundreds of dispatch cases — easy to corrupt the table by hand. This skill documents the safe insertion procedure, the privilege gating rules, the bounds-check patterns, and the SAFE-APPEND ordering trick that lets you avoid renumbering.
---

# rcce2 BVM scripting command

## The two files

The rcce2 BVM (Blitz Virtual Machine) lets game data scripts (under `data/Server Data/Scripts/`) call native code. Two files implement this:

- **[src/Modules/ScriptingCommands.bb](../../../src/Modules/ScriptingCommands.bb)** — the native implementations. Each exposed command is a `Function BVM_NAME[%/$/#](...)` returning the BVM stack value.
- **[src/Modules/RC_Standard_Invoker.bb](../../../src/Modules/RC_Standard_Invoker.bb)** — two things:
  1. **Declaration block** (lines ~25 to ~460) — a long `s = s + "Function NAME<BVM_NAME>%(PARAMS)"+Chr(10)` literal that the BVM command-set parser ingests to build its symbol table and assign opcodes.
  2. **Dispatch block** (lines ~462 to ~1700) — a `Select Case opcode` switch that pops args from the BVM stack, calls the native `BVM_NAME` function, and pushes the result back.

The declaration string is parsed by `BVM_CreateCommandSet` at runtime; it **sorts the declarations alphabetically by name** and assigns sequential opcode numbers starting from 256. The dispatch `Case N` numbers must match this sorted order.

## The alphabetical-opcode trap

Adding a new function called `BVM_NEAREST_ACTOR_IN_RADIUS` in declaration order looks innocent. But because the BVM sorts alphabetically by name, "NEAREST_*" sorts between "NAME" (currently opcode 436) and "NEWQUEST" (currently opcode 437). Inserting it shifts every opcode from 437 upward by 1.

If you insert a new declaration and just add a new `Case 579` at the end of the dispatch (where the highest existing case currently sits), you will appear to compile fine — but every script that calls anything past "NAME" alphabetically will silently call the wrong native function. The dispatch table is now out of sync with the command-set parser.

**Verifying the alphabetical claim**: in the current dispatch, Case 436 = `BVM_NAME`, 437 = `BVM_NEWQUEST`, 438 = `BVM_NEXTACTOR`, 439 = `BVM_NEXTACTORINZONE`, 440 = `BVM_OPENFILE`, 441 = `BVM_OPENTRADING`. Pure alphabetical order. The same pattern holds across the whole range (256–578).

## Two safe insertion strategies

### Strategy A: SAFE-APPEND ordering (preferred for small additions)

Name your new function so it sorts at the **end** of the existing range. The current alphabetical maximum is `BVM_ZONEOUTDOORS` (opcode 578). Anything that sorts after "ZONEOUTDOORS" lands at opcode 579+ without renumbering.

In practice this means prefixing or naming such that alphabetical order is preserved. A common technique: use a `Z`-prefixed namespace for new helpers that don't naturally land at the end. Less ideal for readability but completely safe.

Example for the `BVM_NEAREST_ACTOR_IN_RADIUS` / `BVM_NEAREST_WAYPOINT` proposal: rename to `BVM_ZNEAREST_ACTOR_IN_RADIUS` and `BVM_ZNEAREST_WAYPOINT`. They land at the end, no renumbering.

Trade-off: a slightly uglier name in exchange for a small, mechanical, low-risk PR.

### Strategy B: Full renumber (when the natural name matters)

If the natural name belongs in the middle and you'd rather not prefix:

1. Insert the new declaration line in alphabetical position in the `s = s + ...` block.
2. Shift every dispatch `Case N` from the new insertion point upward by `+1` (or `+K` if inserting K functions).
3. Insert the new `Case` entries at the correct alphabetical position in the dispatch.
4. Re-test thoroughly — even one off-by-one in the renumber silently mis-dispatches every later command.

For a single insertion that lands at position M, you need to bump cases `M..578` to `M+1..579`. That's typically 100+ cases to renumber. **Don't do this by hand** — write a small script that bumps every `Case N` line where N is in the bump range, then visually verify.

**Heuristic for the choice**: if `K` (number of cases to shift) is more than ~5, prefer Strategy A. The renumber risk is too high for the gain.

## Native function shape

```basic
Function BVM_DOTHETHING%(Param1%, Param2$, Param3#)
    ; Step 1: privilege check (see below)
    If Not BVM_RequirePrivileged() Then Return

    ; Step 2: resolve & null-check handles
    Local target.ActorInstance = Object.ActorInstance(Param1%)
    If target = Null Then Return -1

    ; Step 3: bounds check on raw values
    If Param3# < 0.0 Or Param3# > 1000.0 Then Param3# = 1000.0

    ; Step 4: do the work
    Local result = doIt(target, Param2$, Param3#)
    Return result
End Function
```

### Return type sigil matters

- `Function BVM_NAME%(...)` returns an int (BVM pushes int).
- `Function BVM_NAME$(...)` returns a string.
- `Function BVM_NAME#(...)` returns a float.
- `Function BVM_NAME(...)` (no sigil) — void; no push.

The sigil in the declaration string must match: `s = s + "Function NAME<BVM_NAME>%(PARAM1%, PARAM2$)"+Chr(10)`.

### Parameter sigils

Same rules — `%` int, `$` string, `#` float. Default values are supported: `PARAM3% = 1`.

## Dispatch case shape

```basic
Case 579
    fparam2# = BVM_PopFloat()
    sparam1$ = BVM_PopString()
    iparam0% = BVM_PopInt()
    BVM_PushInt(BVM_DOTHETHING(iparam0%, sparam1$, fparam2#))
```

**Critical: args pop in reverse declared order.** The BVM stack is LIFO. If declaration is `BVM_DOTHETHING%(Param1%, Param2$, Param3#)`, the stack has Param3 on top, then Param2, then Param1. Pop in reverse, then call in declared order.

If the function is void (no return), use `BVM_DOTHETHING(...)` directly without `BVM_PushInt(...)`. For string return, use `BVM_PushString(...)`. For float, `BVM_PushFloat(...)`.

## Privilege gating

The default for most BVM commands is **non-privileged**: any NPC's right-click/examine/use/trade script runs with the *clicker's* actor handle. Without privilege checks, an NPC could call `BVM_BANPLAYER(SCRIPT_ACTOR)` and ban the player who right-clicked it.

Three gating functions exist in [ScriptingCommands.bb](../../../src/Modules/ScriptingCommands.bb):

### `BVM_RequirePrivileged%()` — admin-only ops

Use for: `BVM_BANPLAYER`, `BVM_KICKPLAYER`, `BVM_SETGOLD`, `BVM_WARP`, faction mutators, anything that affects global state.

```basic
Function BVM_BANPLAYER(Param%)
    If Not BVM_RequirePrivileged() Then Return
    Actor.ActorInstance = Object.ActorInstance(Param)
    If Actor <> Null
        A.Account = Object.Account(Actor\Account)
        If A <> Null Then A\IsBanned = 1
    EndIf
End Function
```

This passes only when the currently-executing script's `SI\Privileged` flag is set. That flag is set by the calling context — currently only `/script` chat commands from a GM and a few other admin paths.

### `BVM_RequireSelfOrPrivileged%(Param1%)` — actor mutators that are safe for self

Use for: `BVM_SETACTORLEVEL`, `BVM_MOVETO`, attribute mutators — anything that takes an actor handle but is fine when the script is mutating its own actor or its context.

```basic
Function BVM_MOVETO(Param1%, Param2#, Param3#, Param4#)
    If Not BVM_RequireSelfOrPrivileged(Param1%) Then Return
    Local A.ActorInstance = Object.ActorInstance(Param1%)
    If A <> Null Then
        A\X# = Param2# : A\Y# = Param3# : A\Z# = Param4#
    EndIf
End Function
```

This passes when (a) the script is privileged, OR (b) Param1 matches `SI\AI` (the script's owning actor) or `SI\AIContext` (the context actor that triggered the script). Lets NPCs move themselves without letting them move random players.

### No gating — pure reads

`BVM_PLAYERISGM`, `BVM_ACTORX`, `BVM_NEXTACTORINZONE` etc. — pure reads of state that scripts always need access to. No gate. Even hostile NPC scripts can read; they just can't mutate.

### Which to choose

Ask: "if a malicious NPC's `Examine` script calls this with the clicker's handle, what's the damage?"

- No damage (pure read) → no gate.
- Damage only to the script's own state → `BVM_RequireSelfOrPrivileged(target_handle)`.
- Damage to anyone → `BVM_RequirePrivileged()`.

## Bounds checks for client-controlled values

Scripts can be edited by anyone with project-author access, but they're still data — they're parsed at server start and run in the same process. Bad scripts shouldn't crash the server.

```basic
Function BVM_GETKNOWNSPELL$(Param1%, Param2%)
    Actor.ActorInstance = Object.ActorInstance(Param1%)
    If Actor = Null Then Return ""

    ; Bounds-check the index before reading from a fixed array
    If Param2% < 0 Or Param2% > 999 Then Return ""

    Local SpellID = Actor\KnownSpells[Param2%]
    If SpellID < 0 Or SpellID > 999 Then Return ""

    If SpellList(SpellID) = Null Then Return ""
    Return SpellList(SpellID)\Name$
End Function
```

`KnownSpells[]` is dimensioned `[999]` (1000 slots), and any script can pass any Param2 value. The check chain is: bounds the index, bounds the value read, Null-check the lookup. Skipping any step is a server crash.

## Stack discipline

If your function has multiple early-return paths, **every path must leave the stack in the right state**:

- Functions declared `%` must always push an int (use `BVM_PushInt(0)` for the default).
- Functions declared `$` must always push a string (`BVM_PushString("")`).
- Void functions must not push anything.

The dispatch `Case` block in `RC_Standard_Invoker.bb` handles the push for you — but the function itself must return something for the dispatch to push. Missing returns silently produce 0/"" which is usually fine, but be deliberate.

## Logging

Use `BVM_ScriptLog(msg$)` for messages tied to script execution (logs include the script name and execution context). Use `WriteLog(MainLog, msg$)` for server-level messages that aren't script-attributed.

Don't `RuntimeError` for bad script input. Log and return a sentinel value (-1, 0, "") — the script will see the error in its result and can handle it.

## Compile + test

```powershell
.\compile.bat -t        # builds Server (where BVM commands run)
```

Strict-mode catches signature mismatches between the declaration string and the native function. If `Function BVM_X%(P1%, P2$)` declares two params but the native takes three, compile fails. The dispatch `Case` is your other line of defense; double-check the pop order matches the declared param order in reverse.

There are no per-BVM-function tests yet (the test framework can't easily mock script execution). End-to-end is "compile, run server, run a script that exercises the command."

## Reading the dispatch table

To find where a specific BVM is dispatched:

```bash
# Find the Case for BVM_NEXTACTORINZONE
grep -n "BVM_NEXTACTORINZONE" src/Modules/RC_Standard_Invoker.bb
# 203:    s = s + "Function NEXTACTORINZONE<BVM_NEXTACTORINZONE>%(PARAM1%)"+Chr(10)
# 1115:                  BVM_PushInt(BVM_NEXTACTORINZONE(iparam0%))
```

Two hits: declaration (line 203) and dispatch (line 1115). The `Case` number for the dispatch tells you the opcode — useful when verifying you haven't broken the table.

## Checklist before committing

- [ ] Native function in [ScriptingCommands.bb](../../../src/Modules/ScriptingCommands.bb) follows the privilege check → resolve handle → bounds check → work pattern.
- [ ] Declaration string in [RC_Standard_Invoker.bb](../../../src/Modules/RC_Standard_Invoker.bb) is in correct alphabetical position OR named to safe-append at end.
- [ ] Dispatch `Case N` pops args in reverse-declared order, calls in declared order.
- [ ] Dispatch case number is correct (alphabetical position relative to existing commands).
- [ ] Return-type sigil matches everywhere (declaration string, native function, dispatch push).
- [ ] No `RuntimeError` paths reachable from script input — only `Return sentinel` + `BVM_ScriptLog`.
- [ ] `compile.bat -t` clean.
- [ ] If you renumbered, you spot-checked at least 3 cases from the bumped range to confirm they still call the right function.
