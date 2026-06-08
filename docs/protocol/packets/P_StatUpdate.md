# P_StatUpdate

**Direction:** S → C only (no client-emitted form)
**Numeric ID:** 22
**Server emit helpers:** [`UpdateAttribute`](../../../src/Server.bb#L969), [`UpdateAttributeMax`](../../../src/Server.bb#L988), [`UpdateReputation`](../../../src/Server.bb#L1005) (all in [Server.bb](../../../src/Server.bb))
**Client handler:** [ClientNet.bb:990](../../../src/Modules/ClientNet.bb#L990)

## Purpose

The actor-stat broadcast channel. Every server-authoritative change to an actor's `Attributes\Value[]`, `Attributes\Maximum[]`, or `Reputation` is replicated to clients through this packet so HUD bars, faction-rating displays, and observer health-bars stay in sync. It is also the channel `P_AttackActor`'s "O" (observer) sub-code relies on — observers receive a swing animation but **not** the damage number; they pick up the victim's new HP from the next `P_StatUpdate` "A" broadcast for `HealthStat`.

There is no client-emitted form. Mutation requests flow through `/command` chat ([`P_ChatMessage`](P_ChatMessage.md)) or BVM script calls and bottom out in the server's `Update*` helpers.

## Field layout

Three sub-codes, all share the leading 3-byte header (1-byte sub-code + 2-byte target `RuntimeID`):

| Sub-code | Total | Layout | Purpose |
|---|---|---|---|
| `"A"` | 6 bytes | `1B sub + 2B RuntimeID + 1B Attribute + 2B Value` | Current-value attribute update — written to `A\Attributes\Value[Attribute]`. |
| `"M"` | 6 bytes | `1B sub + 2B RuntimeID + 1B Attribute + 2B Value` | Maximum attribute update — written to `A\Attributes\Maximum[Attribute]`. |
| `"R"` | 5 bytes | `1B sub + 2B RuntimeID + 2B Value` | Reputation update — written to `A\Reputation`. **No attribute byte** (only one reputation per actor). |

The 2-byte `Value` is signed and can carry negative attribute values (the wire format does not clamp — the server may send 0 or any in-range int16 for the broadcast).

## Two emit patterns: broadcast vs. single-recipient

The same opcode is emitted with two different audience strategies, driven by **attribute importance**:

- **Important attributes — broadcast to all in zone**. `HealthStat`, `SpeedStat`, `EnergyStat` (and the `R` reputation channel) go through the `Update*` helpers in `Server.bb`, which walk `AInstance\FirstInZone` and send to every player with `RNID > 0`. Every observer's render of the target's HP bar / movement speed needs the update.
- **Unimportant attributes — single-recipient to the target only**. Strength, Wisdom, Toughness, custom stats — these only matter for the target's own HUD. The BVM and DM dispatch sites at [ScriptingCommands.bb:2240-2243](../../../src/Modules/ScriptingCommands.bb#L2240), [:2277-2280](../../../src/Modules/ScriptingCommands.bb#L2277), [:2306-2309](../../../src/Modules/ScriptingCommands.bb#L2306), [:2333-2336](../../../src/Modules/ScriptingCommands.bb#L2333), and [ServerNet.bb:357](../../../src/Modules/ServerNet.bb#L357) / [:371](../../../src/Modules/ServerNet.bb#L371) skip the broadcast and `RCE_Send(Host, Actor\RNID, P_StatUpdate, "A" + Pa$, True)` direct to the actor only.

The breath-update sites in [GameServer.bb:770](../../../src/Modules/GameServer.bb#L770) / [:805](../../../src/Modules/GameServer.bb#L805) are also single-recipient — observers don't need to see a player's breath ticking down.

The dispatching `If Attribute = HealthStat Or Attribute = SpeedStat Or Attribute = EnergyStat` lives at six sites — four BVM mutators in `ScriptingCommands.bb` (`BVM_SETATTRIBUTE`, `BVM_CHANGEATTRIBUTE`, `BVM_SETMAXATTRIBUTE`, `BVM_CHANGEMAXATTRIBUTE`) and the two DM-chat handlers in `ServerNet.bb` (`/setattribute`, `/setattributemax`, inside `Case LanguageString$(LS_SCSetAttribute)` / `LS_SCSetAttributeMax`). All six must stay in lockstep with the canonical importance list (`HealthStat` / `SpeedStat` / `EnergyStat`). Adding a new "important" attribute means touching every one of them.

## Validation requirements

### Server-side (emit)

1. **`AI\RNID > 0 Or AI\RNID = -1`** — players (RNID > 0) and NPCs (RNID = -1) both broadcast; non-actor entities skip. Pure-NPC actors with RNID = 0 are not broadcast (no observer needs them).
2. **`Attribute > -1`** — `FindAttribute(name$)` returns -1 on unknown name. **Critical**: must bail before the array index. The pre-fix bug in `BVM_CHANGEMAXATTRIBUTE` had the `If Attribute > -1` guard around only the read; the unconditional write at `Actor\Attributes\Maximum[Attribute] = ...` OOB'd at `[-1]` when the attribute name was mistyped. See audit comment at [ScriptingCommands.bb:2319-2324](../../../src/Modules/ScriptingCommands.bb#L2319).
3. **`AInstance <> Null`** — the broadcast helpers in `Server.bb` do `AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea) : If AInstance <> Null Then ...` before walking `FirstInZone`. An actor mid-warp (`SetArea` rebinding zones) returns Null from `Object.AreaInstance(...)`; the stat update for that tick simply doesn't broadcast — the actor's `Attributes\Value[]` was already updated locally and the next tick after `SetArea` settles will reach everyone. PRs [#154](https://github.com/RydeTec/RCCE2/pull/154) / [#155](https://github.com/RydeTec/rcce2/pull/155) / [#176](https://github.com/RydeTec/rcce2/pull/176) / [#182](https://github.com/RydeTec/rcce2/pull/182)–[#188](https://github.com/RydeTec/rcce2/pull/188) covered this discipline.

### Client-side (decode) — [ClientNet.bb:990-1011](../../../src/Modules/ClientNet.bb#L990)

1. **`A <> Null`** — `RuntimeIDList(RuntimeID)` returns Null if the server names a `RuntimeID` the client hasn't created an `ActorInstance` for yet (race on actor spawn — server's `P_NewActor` and `P_StatUpdate` may arrive in either order). Bare `A\Attributes\Value[...]` deref would crash; the guard at [:997](../../../src/Modules/ClientNet.bb#L997) drops the packet instead.
2. **`Attribute >= 0 And Attribute < 40`** — the wire byte holds 0..255 but `A\Attributes\Value` / `Maximum` are `Field[39]` (40 slots). A wild attribute index would OOB the Field. The bounds check is at [:1000](../../../src/Modules/ClientNet.bb#L1000) (for "A") and [:1005](../../../src/Modules/ClientNet.bb#L1005) (for "M"). The "R" sub-code has no attribute byte and writes a single scalar (`A\Reputation`).
3. **Unknown sub-code → silent drop** — the `ElseIf` chain has no `Else` branch; a malformed first byte just no-ops.

## Anti-cheat surface

`P_StatUpdate` is **server-authoritative output only** — the client cannot send a `P_StatUpdate` to the server (no `Case P_StatUpdate` exists in `ServerNet.bb`). The relevant attack surface is the **mutation request** side:

- Chat-driven via `/setattribute` / `/setattributemax` — DM-gated at [ServerNet.bb:348-378](../../../src/Modules/ServerNet.bb#L348). See [`P_ChatMessage`](P_ChatMessage.md) for the Account-Null discipline.
- Script-driven via `BVM_SETATTRIBUTE` / `BVM_CHANGEATTRIBUTE` — `BVM_RequirePrivileged()` gated at [ScriptingCommands.bb:2224](../../../src/Modules/ScriptingCommands.bb#L2224) / [:2256](../../../src/Modules/ScriptingCommands.bb#L2256). The gate is full-priv (not self-or-priv) because the HealthStat branch falls through to `KillActor(Actor, Null)` — see CLAUDE.md "BVM clicker-handle trap" for why self-or-priv would have been wrong.

## Historical bugs / PR references

| PR | Fixed |
|---|---|
| Audit pre-PR | `BVM_CHANGEMAXATTRIBUTE` write was outside the `If Attribute > -1` guard — typo'd attribute name → `Maximum[-1]` OOB write. Audit comment at [ScriptingCommands.bb:2319-2324](../../../src/Modules/ScriptingCommands.bb#L2319). |
| PR [#138](https://github.com/RydeTec/rcce2/pull/138)–[#144](https://github.com/RydeTec/rcce2/pull/144) | Client-side bounds check `Attribute >= 0 And Attribute < 40` at [ClientNet.bb:1000](../../../src/Modules/ClientNet.bb#L1000) — pre-fix, a wild wire byte Field-OOB'd the receiver. |
| PRs [#154](https://github.com/RydeTec/rcce2/pull/154) / [#176](https://github.com/RydeTec/rcce2/pull/176) / [#182](https://github.com/RydeTec/rcce2/pull/182)–[#188](https://github.com/RydeTec/rcce2/pull/188) | `Object.AreaInstance(AI\ServerArea)` Null discipline in `Update*` helpers — mid-warp actors no longer crash the broadcast loop. |
| BVM privilege gating | `SETATTRIBUTE` / `CHANGEATTRIBUTE` are full-priv (not self-or-priv) because the HealthStat fall-through to `KillActor` would otherwise be reachable from clicker-driven scripts. See CLAUDE.md → "BVM clicker-handle trap". |

## Related packets

- [`P_AttackActor`](P_AttackActor.md) — observer "O" sub-code intentionally omits damage payload; observers re-sync HP via the next `P_StatUpdate` "A" broadcast.
- [`P_ChatMessage`](P_ChatMessage.md) — `/setattribute` / `/setattributemax` slash commands are the chat-driven path into `UpdateAttribute` / `UpdateAttributeMax`.
- [`P_FetchCharacter`](../index.md) — the initial-load packet emits the entire `Attributes\Value` + `Maximum` table in one shot (40 pairs, 160 bytes) under sub-code "C1"; subsequent updates ride `P_StatUpdate`.
- [`P_ActorDead`](../index.md) — emitted when `Attributes\Value[HealthStat] <= 0` after a `P_StatUpdate` "A" broadcast.

## See also

- [`../encoding.md`](../encoding.md) — wire-encoding primitives (`RCE_StrFromInt$` / `RCE_IntFromStr`).
- [`../handler-conventions.md`](../handler-conventions.md) — `AreaInstance` Null guard pattern, bounds-then-deref, soft-fail discipline.
- [`Server.bb`'s `UpdateAttribute` family](../../../src/Server.bb#L969) — the canonical broadcast helpers; new server code that mutates important attributes must route through these, not direct-write.
