# Per-packet detail pages

This directory holds hand-written detail pages for individual wire packets — sub-codes, field layouts, validation requirements, source-of-truth links to the handlers.

Pages are incrementally filled. The catalog at [`../index.md`](../index.md) shows which packets have detail pages (linked in the "Detail" column) and which still need one (`—`).

Adding a per-packet page is a good first PR. Recommended high-traffic candidates to fill first:

- `P_InventoryUpdate.md` — 4+ sub-codes ("A", "S", "D", "R"), inventory transfer mechanics
- `P_StandardUpdate.md` — per-tick movement broadcast, the dominant network volume
- `P_AttackActor.md` — combat math and damage broadcast
- `P_SpellUpdate.md` — multi-sub-code spell lifecycle ("L", "F", etc.)
- `P_ChatMessage.md` — multi-prefix dispatch (Chr$(252) / Chr$(253) / Chr$(254) channel codes)

## Page template

```markdown
# P_<Name>

**Direction:** C→S / S→C / Both
**Numeric ID:** <id from Packets.bb>
**Server handler:** [ServerNet.bb:LINE](../../../src/Modules/ServerNet.bb#L<line>)
**Client handler:** [ClientNet.bb:LINE](../../../src/Modules/ClientNet.bb#L<line>) (if applicable)

## Purpose

One paragraph: what this packet does and when it fires.

## Sub-codes (if any)

| Sub-code | Direction | Purpose |
|---|---|---|
| `"X"` | C→S | ... |

## Field layout

| Offset | Width | Type | Name | Notes |
|---|---|---|---|---|
| 0 | 1 | Int | SubCode | ASCII char |
| 1 | 2 | Int | RuntimeID | `RCE_StrFromInt$(rid, 2)` |
| ... | | | | |

## Validation requirements

Bounds / Null / clamp / privilege requirements. Link to relevant patterns in
[`../handler-conventions.md`](../handler-conventions.md).

## See also

* The handler line refs above
* `../encoding.md` for the wire primitives
```

See `../handler-conventions.md` for the disciplines every handler should implement against the packet's field layout.
