---
name: rcce2-packet-handler
description: Add, modify, or harden a packet handler in rcce2's ServerNet.bb or ClientNet.bb. Invoke whenever the work involves the wire protocol — adding a new P_* packet type, fixing a Mid$ offset bug, hardening a handler against malformed input, adding a Case branch to the giant Select Case on packet type, or any code that calls RCE_Send / RCE_StrFromInt$ / RCE_IntFromStr. The wire encoding has subtle conventions (length-prefixed strings, fixed-byte integer fields, paired sender/receiver byte counts) and the server is a single shared process — one unchecked Mid$ or one unguarded array index is a one-packet client-to-server DoS for every connected player. This skill encodes the established encoding, validation, and soft-fail patterns from the existing handlers.
---

# rcce2 packet handler

The rcce2 server is a single shared process. Every packet handler runs in the same loop, and any `RuntimeError` from any handler crashes the server for every connected player. This skill captures the established patterns for adding or modifying handlers safely.

## Where things live

- [src/Modules/Packets.bb](../../../src/Modules/Packets.bb) — all `Const P_* = N` packet type definitions. New packet types get added here first.
- [src/Modules/RCEnet.bb](../../../src/Modules/RCEnet.bb) — wire encoding helpers (`RCE_StrFromInt$`, `RCE_IntFromStr`, `RCE_Send`, `RCE_FloatFromStr#`, `RCE_StrFromFloat$`). Read this once to understand the byte-level format.
- [src/Modules/ServerNet.bb](../../../src/Modules/ServerNet.bb) — server's packet dispatch. Large `Select Case PacketType` around line 80+. Each `Case P_X` is a handler.
- [src/Modules/ClientNet.bb](../../../src/Modules/ClientNet.bb) — client's packet dispatch. Same shape as ServerNet but client-side.

## Wire encoding rules

### Integer fields are fixed-byte

`RCE_StrFromInt$(num, length=4)` packs `num` into `length` bytes via the `RCE_ConvertBank` (8 bytes). The receiver must read **exactly the same length** with `Mid$(MessageData$, offset, length)`. The bank is 8 bytes total, so:

- Lengths 1, 2, 4 work normally.
- Length 8 works (max).
- Length > 8 silently truncates — the bank doesn't grow. Don't ever pass `length > 8`.

```basic
// Sender:
Local payload$ = "U" + RCE_StrFromInt$(slot, 1) + RCE_StrFromInt$(amount, 2)

// Receiver:
Local opcode$ = Left$(M\MessageData$, 1)            // "U"
Local slot   = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))   // matches Length=1 above
Local amount = RCE_IntFromStr(Mid$(M\MessageData$, 3, 2))   // matches Length=2 above
```

**Common bug**: sender writes `RCE_StrFromInt$(x, 2)` (2 bytes) but receiver reads `RCE_IntFromStr(Mid$(M\MessageData$, offset, 4))` (4 bytes). Receiver overshoots into the next field, corrupting everything downstream. Always pair the byte counts.

### Floats use the same pattern with `RCE_FloatFromStr#` and `RCE_StrFromFloat$`

Always 4 bytes (IEEE 754 single).

### Strings are length-prefixed

The convention: a 1- or 2-byte length, then the bytes:

```basic
// Sender:
Local payload$ = RCE_StrFromInt$(Len(name$), 1) + name$

// Receiver:
Local nameLen = RCE_IntFromStr(Mid$(M\MessageData$, offset, 1))
Local name$   = Mid$(M\MessageData$, offset + 1, nameLen)
```

**Use `ReadBoundedString$(buf$, offset, maxLen)`** if it exists for the operation — it caps `nameLen` to `maxLen` so a hostile sender can't claim a string longer than the buffer (which would `Mid$` past the end and return garbage). When adding a string-receiving handler, write or reuse a bounded helper rather than raw `Mid$`.

### Multi-byte integers are big-endian via Bank

`PokeInt b, 0, num` writes machine-byte-order, then `PeekByte b, i` reads byte-by-byte from offset 0 upward. The reverse (`RCE_StrFromInt$`) builds the string by prepending each byte (offset 0 first, then offset 1, ...) — this gives a fixed wire byte order regardless of host endianness. Don't try to micro-optimize this; the helpers handle it.

### `RCE_Send` signature

```basic
RCE_Send(Connection, PeerID, P_Type, Payload$, ReliableFlag)
```

- `Connection` — `Host` (server) or `PeerToHost` (client).
- `PeerID` — destination peer ID. On server, `M\FromID` is the sender; you typically send back to the same ID or to `AI\RNID` of a specific actor. On client, always `PeerToHost`.
- `P_Type` — one of the `Const P_*` values from `Packets.bb`.
- `Payload$` — the encoded payload. Leading byte is conventionally a sub-opcode for handlers that multiplex (e.g., `P_AppearanceUpdate` uses `"C"`, `"G"`, `"D"`, `"H"`, `"F"` for the field being updated).
- `ReliableFlag` — `True` for state-mutating packets, `False` for high-frequency lossy updates (position, animation).

## Anatomy of a server packet handler

```basic
Case P_SomeAction
    ; Step 1: find the sending actor
    AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
    If AI <> Null
        ; Step 2: subdivide by leading opcode if applicable
        Select Left$(M\MessageData$, 1)
            Case "X"
                ; Step 3: length-check BEFORE reading
                If Len(M\MessageData$) < 5 Then ; need 1 opcode + 4-byte field
                    WriteLog(MainLog, "P_SomeAction X: truncated packet, dropping")
                    ; just drop — no reply, no crash
                Else
                    ; Step 4: read fields with matched byte counts
                    Local handle = RCE_IntFromStr(Mid$(M\MessageData$, 2, 4))

                    ; Step 5: bounds + Null check before dereference
                    Local target.ActorInstance = Object.ActorInstance(handle)
                    If target = Null Then
                        WriteLog(MainLog, "P_SomeAction X: bad handle, dropping")
                    Else
                        ; Step 6: authorize — does AI have the right to act on target?
                        If target\ServerArea <> AI\ServerArea Then
                            WriteLog(MainLog, "P_SomeAction X: cross-area, refusing")
                        Else
                            ; Step 7: do the work
                            DoTheThing(AI, target)
                        EndIf
                    EndIf
                EndIf
        End Select
    EndIf
```

The steps in order:

1. **Find sender by RNID** — `FindActorInstanceFromRNID(M\FromID)`. Null means stale or unauth; bail.
2. **Sub-opcode dispatch** if the packet multiplexes.
3. **Length check** before reading. `Mid$(s, large_offset)` returns "" rather than erroring, and `RCE_IntFromStr("")` returns 0 — silent corruption.
4. **Match byte counts** with the sender's `RCE_StrFromInt$` calls.
5. **Bounds and Null check** before dereferencing arrays or handles. The client can send any byte values. `Object.X(handle)` returns Null for stale/invalid handles.
6. **Authorize**: same area? owns the item? is target alive? Combat sends to dead actors are use-after-free if you skip this.
7. **Then** do the work.

## The "client-can-crash-server" trap

Every handler shares the server process. A `RuntimeError`, a Null deref, or an out-of-range array index in any handler kills every connected player. The audit history has many examples; the most recently shipped:

- **[PR #132](https://github.com/RydeTec/rcce2/pull/132)** — `P_CreateCharacter` passed a client-supplied 2-byte ActorID straight into `ActorList(id)` → `CreateActorInstance(Null)` → `RuntimeError`. **One crafted packet** crashed the server. Fix: bounds-check + `ActorList(id) <> Null` before use.
- **[PR #133](https://github.com/RydeTec/rcce2/pull/133)** — `SetArea` dereferenced `Ar` without Null check; a saved character whose Area$ was deleted from data files crashed the server at login. Fix: defensive guard at top of `SetArea`, plus validate at `P_StartGame` call site before flipping login status.
- **[PR #134](https://github.com/RydeTec/rcce2/pull/134)** — `/warpother` DM chat command had no `Ar <> Null` check after `FindArea` (the sibling `/warp` did). DM typo crashed the server. Fix: mirror the sibling guard.

The recovery patterns:

### Invalid handle / stale reference → log + skip

```basic
Local target.ActorInstance = Object.ActorInstance(handle)
If target = Null
    WriteLog(MainLog, "P_Whatever: stale actor handle " + handle + ", dropping")
    ; no reply needed; just drop
    ; (continue to next packet — the surrounding `For M = Each MessageBuffer` loop handles iteration)
EndIf
```

### Invalid client-supplied list index → reject with response

```basic
If ActorID < 0 Or ActorID > 65535 Or ActorList(ActorID) = Null
    WriteLog(MainLog, "P_CreateCharacter: invalid ActorID " + ActorID + " from '" + A\User$ + "'")
    RCE_Send(Host, M\FromID, P_CreateCharacter, "N", True)   ; tell client the request failed
    Exists = True : Exit                                       ; bail out of the surrounding For loop
EndIf
```

### Missing optional referenced object → log + continue without it

```basic
Local Ar.Area = FindArea(name$)
If Ar = Null
    WriteLog(MainLog, "P_Warp: area '" + name$ + "' not found, ignoring")
    ; don't touch the actor; just skip
Else
    SetArea(actor, Ar, 0, -1, -1, x#, y#, z#)
EndIf
```

### State must be cleaned up if you bail

If the partial setup already created objects (allocated an actor slot, opened a file), free/close them before bailing:

```basic
A\Character[FreeSlot] = CreateActorInstance(ActorList(ActorID))
; ... 50 lines of setup ...
If somethingWrong
    FreeActorInstance(A\Character[FreeSlot])           // cleanup
    A\Character[FreeSlot] = Null                       // clear pointer
    RCE_Send(Host, M\FromID, P_CreateCharacter, "N", True)
    Exists = True : Exit
EndIf
```

See the `AttributeAssignment` cheat check in `P_CreateCharacter` ([ServerNet.bb around line 2443](../../../src/Modules/ServerNet.bb)) for the canonical bail-with-cleanup pattern.

## Adding a brand-new packet type

1. Add `Const P_NewThing = NNN` to [Packets.bb](../../../src/Modules/Packets.bb). Pick the next unused number; both server and client read the same file, so they agree.
2. **Sender** — wherever the action originates (UI button, NPC script, game tick), call `RCE_Send(Host, target, P_NewThing, payload$, reliable)`.
3. **Receiver** — add `Case P_NewThing` to the appropriate `Select Case` in [ServerNet.bb](../../../src/Modules/ServerNet.bb) or [ClientNet.bb](../../../src/Modules/ClientNet.bb).
4. Follow the validation steps above for the receive side.
5. If it's a server-side handler that mutates persistent state, save through an atomic write (see [Logging.bb](../../../src/Modules/Logging.bb)'s `SafeWriteOpen$` / `SafeWriteCommit%`).
6. Test by running `compile.bat -t` (builds both Server and Client) and verifying the round trip in a manual session.

## Client-side soft-fail (rendering)

Client-side mesh/animation/UI handlers face the same DoS surface from a hostile or out-of-sync server. The pattern is the same: log + skip + free any partial entity. Examples from merged PRs:

- **[PR #128](https://github.com/RydeTec/rcce2/pull/128)** — `P_NewActor` mesh load failure now does `WriteLog + SafeFreeActorInstance + Return` instead of `RuntimeError`. Actor stays invisible to this client until they re-enter the zone; the rest of the world keeps rendering.
- **[PR #129](https://github.com/RydeTec/rcce2/pull/129)** — equipment/gubbin mesh failures soft-fail to "unequipped" instead of crashing.
- **[PR #130](https://github.com/RydeTec/rcce2/pull/130)** — `P_AppearanceUpdate` C/G (race/gender change) mesh failures soft-fail for other actors; the player's own avatar still hard-errors (no reconnect-state recovery yet).

## Things to double-check before committing

- [ ] Sender's `RCE_StrFromInt$` byte counts match receiver's `Mid$` byte counts.
- [ ] Every value read from the wire is bounds/Null-checked before being used as an index or dereferenced.
- [ ] No `RuntimeError` calls on wire-derived data. Use `WriteLog` + soft-fail recovery.
- [ ] If the handler allocates partial state, the bail path frees it.
- [ ] Packet length is checked against the minimum needed bytes before reading the last field.
- [ ] `compile.bat -t` builds Server and Client cleanly.
- [ ] If your change spans both sides, both sides agree on the new packet format (no version skew).
