# P_StandardUpdate

**Direction:** Both (C→S movement input; S→C per-tick position broadcast)
**Numeric ID:** 14
**Server handler:** [ServerNet.bb:1761](../../../src/Modules/ServerNet.bb#L1761)
**Server broadcast emitter:** [GameServer.bb:1078-1112](../../../src/Modules/GameServer.bb#L1078)
**Client handler:** [ClientNet.bb:1490](../../../src/Modules/ClientNet.bb#L1490)

## Purpose

The per-tick movement broadcast. The highest-network-volume packet in the protocol — every connected player sends one to the server per movement-tick, and the server broadcasts every visible actor's position to every nearby player. The "standard" in the name is "what every actor needs every tick": position, destination, locomotion state.

**No sub-codes.** A single fixed-shape packet per direction. The conditional fields appear on the S→C side based on actor properties (mount presence, is-you, Environment_Fly).

## Field layout

### C → S — "my movement input this tick"

The client sends its intended destination + current sampled position. The server validates with `ClampWorldCoord` and a speed-hack delta clamp.

| Offset | Width | Type | Field | Notes |
|---|---|---|---|---|
| 1 | 4 | Float | `DestX#` | Where the client wants to be heading (movement input). Clamped via `ClampWorldCoord#`. |
| 5 | 4 | Float | `DestZ#` | Same. |
| 9 | 4 | Float | `NewY#` | Vertical sample. Clamped; written directly to `AI\Y#`. |
| 13 | 4 | Float | `NewX#` | Current X (the client's sampled position). Clamped; passes the speed-hack check before being committed to `AI\X#`. |
| 17 | 4 | Float | `NewZ#` | Current Z. Same. |
| 21 | 1 | Int | `IsRunning` | Locomotion state. Force-cleared when `WalkingBackward = True` (anti-cheat). |
| 22 | 1 | Int | `WalkingBackward` | Locomotion state. |

**Total packet length:** 22 bytes after the sub-code byte (there is no sub-code byte; all 22 bytes are payload).

### S → C — "actor X moved to position Y"

The server broadcasts this for each nearby actor on each broadcast tick (per `GameServer.bb`'s `UpdateActorInstances` per-tick scheduler). Three tail-shape conditionals:

| Offset | Width | Type | Field | Always present? |
|---|---|---|---|---|
| 1 | 2 | Int | `A2\RuntimeID` | The actor being updated. Yes. |
| 3 | 4 | Float | `A2\X#` | Authoritative X. Yes. |
| 7 | 4 | Float | `A2\Z#` | Authoritative Z. Yes. |
| 11 | 1 | Int | `A2\IsRunning` | Locomotion. Yes. |
| 12 | 1 | Int | `A2\WalkingBackward` | Locomotion. Yes. |
| 13 | 4 | Float | `A2\DestX#` | Server-side destination. Yes. |
| 17 | 4 | Float | `A2\DestZ#` | Same. Yes. |
| 21 | 2 | Int | `A2\Mount\RuntimeID` (or 0) | Mount RuntimeID, or `0` if no mount. Yes. |
| 23 | 2 | Int | `A2\Attributes\Value[EnergyStat]` | **Only when `A2 = AI`** (the receiving player is the actor being updated) AND `EnergyStat > -1`. Self-energy passback. |
| 23 | 4 | Float | `A2\Y#` (or interpolated `YPos#`) | **Only when `A2\Actor\Environment = Environment_Fly`** AND `A2 <> AI`. Flying-actor Y. For AI in patrol mode, server interpolates Y between OldY and waypoint Y. |

**The "23 or 23" overlap is intentional:** the two conditional tails are mutually exclusive (you only get Energy on your own update; you only get Y on a remote flying actor's update). The client at [ClientNet.bb:1538](../../../src/Modules/ClientNet.bb#L1538) reads offset 23 as a 4B float ONLY in the `Environment_Fly` branch; the self-Energy passback was commented out in the client (see ClientNet.bb:1545) so currently arrives but is ignored.

## Validation requirements

### Float clamp on every position field (C→S)

All five floats (`DestX`, `DestZ`, `NewY`, `NewX`, `NewZ`) pass through `ClampWorldCoord#`, which rejects NaN/Inf via the canonical comparison trick (see [encoding.md](../encoding.md)). PR series #237-#239 unified the prior "Y-only filter, X/Z raw" inconsistency.

A single NaN in any of these would poison every receiving client's spatial code (collision, `EntityDistance#`, LOD culling, AI targeting).

### Anti-cheat: speed-hack delta clamp on `NewX` / `NewZ`

[ServerNet.bb:1796-1827](../../../src/Modules/ServerNet.bb#L1796). Per-packet position delta is bounded against the actor's Speed attribute scaled by elapsed milliseconds:

```basic
MaxUnitsPerMs# = 0.15 * (SpeedAttr# + 0.5)  ; matches GameServer's 1.5 base unit/tick
MaxDelta# = MaxUnitsPerMs# * Float#(ElapsedMs)
If MaxDelta# < 2.0 Then MaxDelta# = 2.0      ; floor for short-tick lag tolerance
If MoveDist# > MaxDelta# Then ...            ; hold at prior position
```

Without this, the client could teleport arbitrarily within `ClampWorldCoord` bounds between updates, defeating PvP positioning and movement-based triggers. The 2.0 floor + `SpeedAttr + 0.5` scaling covers lag spikes, collision shove-back, and the 3× tolerance the server uses for AI movement.

The first update of a session (or after a >5s lag spike) accepts and re-baselines `LastPosUpdateMs`, so the clamp never fires on legitimate re-sync.

### Anti-cheat: backward-running prevention

[ServerNet.bb:1784](../../../src/Modules/ServerNet.bb#L1784) — `If AI\WalkingBackward = True Then AI\IsRunning = False`. Without this, the client could set both flags and the server would let the actor run backwards (animation+speed mismatch — invisible-frame teleport opportunity).

### Rider-mount synchronisation

When `AI\Mount <> Null`, the mount's position is force-set to the rider's position after every update ([ServerNet.bb:1832-1842](../../../src/Modules/ServerNet.bb#L1832)). The mount is also a server-side ActorInstance; without this, the mount drifts from the rider on every tick.

### `AI\Rider = Null` precondition

[ServerNet.bb:1767](../../../src/Modules/ServerNet.bb#L1767) — only the rider can update; if `AI` is a mount with a rider, the rider's `P_StandardUpdate` will move both. Without this gate, a malformed packet could move the mount independently of its rider.

### `AI\IgnoreUpdate = 0` precondition

[ServerNet.bb:1765](../../../src/Modules/ServerNet.bb#L1765) — set by `P_ChangeArea` and `P_RepositionActor` while the client is mid-warp. Prevents in-flight updates from clobbering the server's reposition while the client is still rendering it.

## Server broadcast scheduler

[GameServer.bb's `UpdateActorInstances`](../../../src/Modules/GameServer.bb#L1044) walks `FirstOnlinePlayer` (engine-wide chain, added PR [#283](https://github.com/RydeTec/rcce2/pull/283)) and for each online recipient walks `AInstance\FirstInZone` (per-area chain) to broadcast every visible actor's update. Three distance bands:

| Band | Condition | Update? |
|---|---|---|
| Close | `ActorDistance# < UpdateDistance` | Always |
| Mid | `UpdateDistance ≤ ActorDistance# ≤ UpdateFarDistance` | Only if `AlsoUpdateMiddleRange = 1` (timing-gated) |
| Far | `ActorDistance# > UpdateFarDistance` | Never |

Mid-range updates skip every other tick to halve bandwidth while keeping the remote visible. `AlsoUpdateMiddleRange` flips at `LastCompleteUpdate` boundaries.

## Historical bugs / PR references

| PR | Fixed |
|---|---|
| [#237](https://github.com/RydeTec/rcce2/pull/237) – [#239](https://github.com/RydeTec/rcce2/pull/239) | Unified ClampWorldCoord across all five position floats (was Y-only previously) |
| [#270](https://github.com/RydeTec/rcce2/pull/270) / [#272](https://github.com/RydeTec/rcce2/pull/272) | Underwater-damage check converted to per-Area chain (touches the same per-tick loop) |
| [#277](https://github.com/RydeTec/rcce2/pull/277) | Null-guard on stale `ServerWater` handle inside the per-tick loop (was a zero-sentinel phantom-damage bug, not a crash, due to BlitzForge's non-short-circuit `And` + zero-sentinel semantics) |
| [#282](https://github.com/RydeTec/rcce2/pull/282) | `FindActorInstanceFromRNID(M\FromID)` -- which P_StandardUpdate calls on every inbound packet -- went O(N) -> O(1) via the ActorByRNID index |
| [#283](https://github.com/RydeTec/rcce2/pull/283) | The server-side broadcast loop walks `FirstOnlinePlayer` instead of `Each ActorInstance / If A\RNID > 0` |
| Speed-hack delta clamp (date pre-#237) | The `MaxUnitsPerMs#` calculation and 5s lag-spike re-baseline rule |

## Related packets

- [`P_ChangeArea`](../index.md) — sets `AI\IgnoreUpdate = 1` while warp is in flight
- [`P_RepositionActor`](../index.md) — server-initiated reposition; same IgnoreUpdate gate
- [`P_StatUpdate`](../index.md) — Energy stat sometimes hitches a ride on the S→C tail but P_StatUpdate is the canonical Energy update channel
- [`P_NewActor` / `P_ActorGone`](../index.md) — area-entry / area-exit gating that determines whether a remote actor is in the recipient's per-tick broadcast set

## See also

- [`../encoding.md`](../encoding.md) — `RCE_StrFromFloat$`, `ClampWorldCoord#`, NaN-catching comparison trick
- [`../handler-conventions.md`](../handler-conventions.md) — float sanitisation, anti-cheat patterns
- [`../../modules/servernet.md`](../../modules/servernet.md) — P_StandardUpdate's place in the auth/lifecycle dispatch
- [`../../modules/gameserver.md`](../../modules/gameserver.md) (if/when filled) — the per-tick broadcast scheduler this packet drives
