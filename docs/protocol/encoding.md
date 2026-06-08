# Wire-encoding primitives

The RCCE 2 wire protocol uses a small set of encoding primitives defined in
[`src/Modules/RCEnet.bb`](../../src/Modules/RCEnet.bb). Every packet handler in
[`ServerNet.bb`](../../src/Modules/ServerNet.bb) and
[`ClientNet.bb`](../../src/Modules/ClientNet.bb) reads off the wire using these
helpers; mismatched byte counts are the #1 source of "packet works in dev but
breaks against the deployed client" bugs.

## Integers — fixed-byte width via Bank

```basic
RCE_StrFromInt$(num, length = 4)   ; encode `num` into `length` bytes
RCE_IntFromStr(s$)                 ; decode a 1-, 2-, 3-, or 4-byte string into an Int
```

* Default length is 4 bytes. Most fields use 1, 2, or 4 byte widths depending
  on the value range.
* The Bank backing the encoder is **4 bytes wide** (verified at
  [`RCEnet.bb:2`](../../src/Modules/RCEnet.bb#L2): `Global RCE_ConvertBank = CreateBank(4)`).
  `length > 4` writes past the end of the Bank and produces undefined
  bytes — Blitz3D's `PokeInt` does not bounds-check.
* Sender and receiver byte counts MUST match. A 2-byte field written as
  `RCE_StrFromInt$(x, 2)` is read back as `Mid$(MessageData$, offset, 2)`;
  passing the wrong count corrupts every downstream field in the same packet
  because the offsets shift.

### Sigil-byte field width conventions

The engine has settled on a few widely-used widths:

| Field | Width | Reason |
|---|---|---|
| Slot index (inventory, ability bar) | 1 byte | 0..255 covers `Slots_Inventory = 45` with headroom. |
| Stack count / quantity | 2 bytes | -32768..32767 (signed). The signedness is exploitable — every quantity field MUST bounds-check before use; see `feedback_sibling_protection_asymmetry` and PR [#276](https://github.com/RydeTec/rcce2/pull/276). |
| RuntimeID, RNID | 2 bytes | Server caps players at 5000; the 2-byte range is more than enough. |
| Actor ID / item template ID | 2 bytes | `ActorList` is `Dim`ed 0..65535. |
| Position / float coordinates | 4 bytes (`RCE_StrFromFloat$`) | IEEE float, full range. **Must clamp with `ClampWorldCoord#` before broadcast.** See "Float sanitisation" below. |
| Length prefix (strings) | 4 bytes | See `RCE_StrToWireStr$` / `ReadBoundedString$` below. |

## Floats — IEEE round-trip

```basic
RCE_StrFromFloat$(f#)     ; encode 4-byte IEEE single
RCE_FloatFromStr#(s$)     ; decode 4-byte IEEE single
```

**NaN and Inf are wire-reachable.** Any float read from a client packet or a
BVM script must be clamped before being broadcast or written to actor state:

```basic
ClampWorldCoord#(v#)     ; positions / destinations -- clamps to ±WorldCoordMax#, rejects NaN/Inf
ClampSaneFloat#(v#)      ; non-position floats (yaw, anim speed, UI dims) -- clamps to ±1e9, rejects NaN/Inf
```

Both helpers work by `If v > -MAX And v < MAX Then Return v` — the comparison
fails on NaN (which is unordered against any finite value), so the clamp
catches NaN automatically. There is no `IsNaN` primitive in Blitz; the
comparison trick is the canonical approach.

See PR [#237](https://github.com/RydeTec/rcce2/pull/237) – [#239](https://github.com/RydeTec/rcce2/pull/239)
for the BVM-side sweep that hardened `BVM_MOVEACTOR`, `BVM_ROTATEACTOR`,
`BVM_SETACTORDESTINATION`, `BVM_SPAWN`, `BVM_SPAWNITEM`, `BVM_ANIMATEACTOR`,
`BVM_CREATEEMITTER`. The ServerNet `P_InventoryUpdate "D"` (drop-item) handler
at [lines 1654-1656](../../src/Modules/ServerNet.bb#L1654) is the original
template for the wire-side clamp.

## Strings — length-prefixed

The engine uses three string-encoding shapes; pick by context:

1. **Length-prefixed via `ReadBoundedString$`** — the standard inbound shape.
   A 4-byte length prefix, then that many bytes. The reader bounds-checks the
   length before allocating and refuses anything outside `[0, MaxLen]`.
   ```basic
   s$ = ReadBoundedString$(F, 1024)   ; max 1KB per field
   ```
   Without the bound, a 4-byte length of `0x7FFFFFFF` allocates 2GB and silently
   zero-fills past EOF. See `feedback_compile_verification_no_grep_filter` for
   the historical pattern. PR [#149](https://github.com/RydeTec/rcce2/pull/149)
   established the sweep.

2. **Inline `Mid$` slicing** — for short fields with known fixed widths,
   `Mid$(M\MessageData$, offset, N)` is the common shape. The handler must
   `If Len(M\MessageData$) >= offset + N - 1` before slicing, or the slice
   returns "" and `RCE_IntFromStr("")` returns 0 (a sentinel that's often
   confusable with a legitimate value).

3. **Outbound via concatenation** — `Pa$ = RCE_StrFromInt$(x, 2) + RCE_StrFromInt$(y, 4)
   + s$`. The receiver MUST know the byte counts to parse.

## Soft-fail discipline

Server packet handlers (and client renderers reading server data) must
**not** `RuntimeError(...)` on wire-supplied values. A single malformed
packet would crash the entire server process and disconnect every other
player. Soft-fail shape:

```basic
If <validation fails>
    WriteLog(MainLog, "Handler: bad value, dropping (context: ...)")
    SafeFreeActorInstance(A)   ; or whatever cleanup is appropriate
    Return                     ; or `Exit` from the dispatch Case
EndIf
```

See [handler-conventions.md](handler-conventions.md) for the full rubric.

## See also

* [handler-conventions.md](handler-conventions.md) — bounds-check, handle-Null discipline, iterator-during-iteration patterns
* [index.md](index.md) — catalog of all 56 packets
* [`../../src/Modules/RCEnet.bb`](../../src/Modules/RCEnet.bb) — the encoding helpers
* [`../../CLAUDE.md`](../../CLAUDE.md) — "Wire encoding", "Float sanitisation", "Soft-fail on server-controlled data"
