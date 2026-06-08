# Packet handler conventions

The patterns every server-side packet handler in
[`ServerNet.bb`](../../src/Modules/ServerNet.bb) must follow. The same
disciplines apply to client-side handlers in
[`ClientNet.bb`](../../src/Modules/ClientNet.bb) when reading data the
server-side might have corrupted (e.g. broken save files, missing meshes).

For the wire-encoding primitives this layer sits on top of, see
[encoding.md](encoding.md).

## The five disciplines

### 1. Soft-fail on server-controlled data

A single `RuntimeError(...)` on a malformed packet field disconnects every
player on the server. Every validation failure that the wire could induce
must log + skip, never abort.

```basic
If Result = False
    WriteLog(MainLog, "Handler: bad value, dropping (context: " + ctx + ")")
    SafeFreeActorInstance(A)
    Return
EndIf
```

`RuntimeError(...)` is reserved for invariant violations — bugs in our own
code, not in the wire input. Examples of legitimate `RuntimeError`: a
`Const`-declared array bound exceeded, an internal data structure invariant
failure that indicates corrupted memory.

### 2. Bounds-check before array index

Any value read from the wire (or from a save file) used as an array index
must be range-checked first. `ActorList` is `Dim`ed `[65535]`, but a
client-supplied ActorID also needs `<> Null` check on the slot — most slots
are empty.

```basic
If ActorID < 0 Or ActorID > 65535 Or ActorList(ActorID) = Null
    WriteLog(MainLog, "rejecting invalid ActorID " + ActorID)
    RCE_Send(Host, M\FromID, P_..., "N", True)
    Exists = True : Exit
EndIf
```

The bound MUST cover negative values too. Wire integers can be signed
(`RCE_IntFromStr` returns a signed 32-bit), and the 2-byte signed range
includes -32768..-1.

### 3. Handle-lookup Null discipline

`Object.X(handle)` returns Null for stale or invalid handles — it does
**not** error. Any deref on the result without `<> Null` is a wait-time
bug — eventually a freed-but-unreferenced handle wins the race.

```basic
AI.ActorInstance = Object.ActorInstance(SomeHandle)
If AI = Null
    SomeHandle = 0       ; Clear the source so next tick doesn't retry.
    Return
EndIf
; Deref AI freely below.
```

**Critical subtlety** (verified iteration #15, PR [#277](https://github.com/RydeTec/rcce2/pull/277)):
in BlitzForge release builds the `__bbNullObjEx` instrumentation is debug-
gated, so Null derefs read a **zero-sentinel** rather than crashing. The
threat shape is **silent phantom data**, not a server crash. The
canonical guard `If X <> Null And X\Field > 0` works in production because:

1. BlitzForge's `And` is non-short-circuit — `X\Field` IS evaluated even
   when X is Null.
2. The zero-sentinel makes `X\Field` read as 0.
3. `0 > 0` is False, so the bitwise-And is False, so the If body skips.

The pattern is correct AND has 17+ sibling sites across the codebase. But
the reasoning is non-obvious and contradicts short-circuit intuition. The
threat shape to look for in audits is **silent zero-sentinel propagation**
(Null → 0 → wrong arithmetic), not a crash.

### 4. Float sanitisation at the BVM / wire boundary

Floats from the wire or from BVM scripts that flow into actor state and get
broadcast to clients MUST clamp at the boundary, not at the downstream
readers. See [encoding.md#floats](encoding.md) for the two helpers
(`ClampWorldCoord#`, `ClampSaneFloat#`) and their rationale.

A single NaN in a broadcast position poisons spatial code (collision, LOD
culling, `EntityDistance#`) on every receiving client. NaN yaw poisons
rotation matrices. NaN animation speed locks the animation timer for that
actor on every receiver.

### 5. Iterator-during-iteration hazards

Blitz3D's `For X = Each Type` iterator advances via the deleted element's
"next" pointer on each `Next`. Calling `Delete X` (or `FreeActorInstance(X)`
/ `Delete PausedScript` etc.) inside the loop body corrupts the cursor for
the next iteration.

Three established fixes (see CLAUDE.md for the full discussion):

1. **After-cursor walk** — capture `XNext = After X` *before* `Delete`.
   Works when the body only deletes the current element.
2. **Deferred kill list** — collect into a side type, process after the
   loop. Use when the body might delete multiple actors including ones past
   the cursor.
3. **Restart-on-Delete** — re-enter the `For` loop after every `Delete`.
   Use when the body recurses and the recursion can delete elements past
   the outer cursor's captured `After` pointer.

## Sibling-protection asymmetry

A specific anti-pattern that has bitten the codebase repeatedly: when one
packet handler / primitive has a guard and its sibling doesn't, the
asymmetry is almost always a bug — the original fix landed for the specific
exploit that was reported, and the family of related sites was missed.

Recent examples:

* `InventorySwap` had `If Amount < 1 Or Amount > Amounts[SlotA]` (line 152)
  but `InventoryAdd` did not — negative `Amount` produced an unbounded
  duplication path. PR [#276](https://github.com/RydeTec/rcce2/pull/276).
* `P_RightClick` had `Dist# < InteractDist` range gate but `P_Examine`,
  `P_Trade`, `P_ItemScript` did not — cross-area script-trigger surface.
  PR [#276](https://github.com/RydeTec/rcce2/pull/276).
* `P_AttackActor` had `AInstance = TInstance` same-area gate but the three
  sibling Default-script entry points did not.
  PR [#276](https://github.com/RydeTec/rcce2/pull/276).

When auditing a handler: grep for sibling handlers that take the same
kind of input and look for guards present on one but missing on others.
This is the highest-payoff recon signal.

The recon signal is simple: every guarded primitive's family of related
sites should be audited for the same guard.

## Privilege gating (script-spawning handlers only)

The four handlers that spawn user scripts based on packet input are
specifically lockdown-sensitive:

* `P_RightClick` (~line 1419)
* `P_Examine` (~line 1482)
* `P_Trade` (~line 1523)
* `P_ItemScript` (~line 1358)

These hand off to `ThreadScript(...)` with `SI\AI = Handle(clicker)` — NOT
`Handle(NPC)`. This means any `BVM_RequireSelfOrPrivileged(Param1)` gate
where `Param1` is the target-actor parameter does NOT block clicker
exploits: the clicker IS the "self" and passes the gate trivially.

See the four privilege-gate categories in [`../../CLAUDE.md`](../../CLAUDE.md)
for the full threat model.

## See also

* [encoding.md](encoding.md) — wire-encoding primitives
* [index.md](index.md) — catalog of all 56 packets
* [`../../CLAUDE.md`](../../CLAUDE.md) — agent-facing dev guide
* [`../modules/scripting.md`](../modules/scripting.md) — BVM script lifecycle and privilege model
* [`../bvm-reference.md`](../bvm-reference.md) — auto-generated BVM function catalog with gates
