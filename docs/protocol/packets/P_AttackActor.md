# P_AttackActor

**Direction:** Both (C→S "I'm attacking this target"; S→C "the attack resolved")
**Numeric ID:** 18
**Server handler:** [ServerNet.bb:1548](../../../src/Modules/ServerNet.bb#L1548)
**Server attack engine:** [GameServer.bb:300](../../../src/Modules/GameServer.bb#L300) (`ActorAttack`)
**Client handler:** [ClientNet.bb:1109](../../../src/Modules/ClientNet.bb#L1109)

## Purpose

The combat packet. Carries melee + ranged-projectile attack initiation from client to server, and the resolved attack (hit / miss / observer-visible swing) from server to client. The server runs the entire damage formula, faction check, weapon-range check, and HP mutation; the client only animates and updates its HUD.

## Field layout

### C → S — "I'm attacking RuntimeID X"

A fixed 2-byte payload, no sub-code. The client tags the target by `RuntimeID`; the server resolves to `ActorInstance` via `RuntimeIDList`.

| Offset | Width | Type | Field | Notes |
|---|---|---|---|---|
| 1 | 2 | Int | Target `RuntimeID` | The actor the client wants to hit. Resolved server-side via `RuntimeIDList`. |

**Total: 2 bytes.** Validated by `Len(M\MessageData$) = 2` ([ServerNet.bb:1550](../../../src/Modules/ServerNet.bb#L1550)) — packets of any other length are silently dropped.

### S → C — three sub-codes covering attacker, victim, and observers

| Sub-code | Recipient | Layout | Purpose |
|---|---|---|---|
| `"H"` | Attacker (`A1\RNID`) | `1B sub + 2B Victim\RuntimeID + 2B Damage+1 + 1B DamageType` | "I hit them" — for the attacker's HUD (damage numbers, attack animation). |
| `"Y"` | Victim (`A2\RNID`) | `1B sub + 2B Attacker\RuntimeID + 2B Damage+1 + 1B DamageType` | "They hit me" — for the victim's HUD (incoming damage, parry/hit animation). |
| `"O"` | All other players in the same area | `1B sub + 2B Attacker\RuntimeID + 2B Victim\RuntimeID` (no damage payload) | Observer swing animation. Observers re-sync HP via the next `P_StatUpdate`, so the damage isn't replicated on this channel. Subtle: the ClientNet "Else" branch doesn't decode a fresh `Damage` from the wire — in non-Strict `UpdateNetwork()`, `Damage` is an implicit function-scope variable that persists across `Select Case` iterations within one call, so it reads whatever the prior `H`/`Y` packet (or zero, on the first call) left there. Current behaviour is benign because `P_StatUpdate` re-syncs HP authoritatively. |

**Damage+1 encoding:** The 2-byte `Damage` field carries `Damage + 1` so a value of 0 on the wire means "miss" (rendered as a parry animation). The wire field can be negative (signed 2-byte = -32768..32767), which lets the server signal a miss explicitly. Client subtracts 1 to recover the actual damage at [ClientNet.bb:1117](../../../src/Modules/ClientNet.bb#L1117) / [:1145](../../../src/Modules/ClientNet.bb#L1145).

**Damage type bounds:** The 1-byte `DamageType` is clamped client-side to `[0, 19]` before indexing into `DamageTypes$` ([ClientNet.bb:1121-1123](../../../src/Modules/ClientNet.bb#L1121)). A malformed packet with a wild value falls back to type 0 instead of crashing the client.

## Validation requirements

### C → S handler ([ServerNet.bb:1548-1571](../../../src/Modules/ServerNet.bb#L1548))

Six gates, all required to fire:

1. **Sender validity**: `AI <> Null` (FindActorInstanceFromRNID resolves the sender).
2. **Packet shape**: `Len(M\MessageData$) = 2`.
3. **Combat delay**: `MilliSecs() - AI\LastAttack >= CombatDelay`. Prevents attack-spam cheating; `AI\LastAttack` is set on every successful attack in `ActorAttack`.
4. **Not riding a mount**: `AI\Mount = Null`. Mounted players can't attack (intentional gameplay constraint).
5. **Same-area gate** (added PR [#276](https://github.com/RydeTec/rcce2/pull/276)): both attacker and victim must be in the same `AreaInstance`. Resolved via `Object.AreaInstance(AI\ServerArea)` and `Object.AreaInstance(A2\ServerArea)`; the dual lookup guards both sides against stale `ServerArea` mid-portal.
6. **PvP / NPC permission**: `A2\RNID < 0 Or AInstance\Area\PvP = True`. NPCs (RNID -1) are always attackable; players are only attackable in PvP-enabled areas.

### `ActorAttack` damage engine ([GameServer.bb:300-600+](../../../src/Modules/GameServer.bb#L300))

The damage engine itself runs additional checks:

- **Already-dead target**: `If A2\Attributes\Value[HealthStat] <= 0 Then Return False`. Without this, two attackers landing in the same tick both saw HP > 0, both subtracted, both called `KillActor` against freed memory (double-XP + use-after-free).
- **Both Aggressiveness ≠ 3**: NPCs with `Aggressiveness = 3` are non-combatant (typed mobs / vendors).
- **Faction rating**: `A1\FactionRatings[A2\HomeFaction] > 150` blocks the attack (friendly faction).
- **Range check**: melee uses `7.0 + A1\Actor\Radius + A2\Actor\Radius` squared. Ranged projectile uses `weapon.Range + A1.Radius + A2.Radius` squared.

### Hit chance + damage formula (4 variants, selected by `CombatFormula` global)

| `CombatFormula` | Hit chance | Damage formula |
|---|---|---|
| `1` (Normal) | 90% | `weapon.Damage ± strength-rolled bonus`, critical 1/10 (×2). Armour subtracts `GetArmourLevel + Resistances[DamageType] - 100 + ToughnessStat / 8`. |
| `2` (No strength bonus) | 90% | `weapon.Damage` flat. Critical 1/10 (×2). Same armour formula. |
| `3` (Multiplied) | 90% | `weapon.Damage × Strength`. Critical 1/10. |
| `4` (Attack script) | N/A | Delegates to a `ThreadScript("Attack", "Main", attacker, victim)` — content authors implement the formula in `.rsl`. No range/damage check server-side. |

`CombatFormula` is a global set at server boot from project config. The attack-script variant (4) is the modder hook for completely custom combat.

### Broadcast pattern

After damage is applied, the server emits three packets:

1. To attacker (if `A1\RNID > 0`): `"H" + victim_rid + damage + dtype`
2. To victim (if `A2\RNID > 0`): `"Y" + attacker_rid + damage + dtype`
3. To all others in the same `AInstance\FirstInZone` chain: `"O" + attacker_rid + victim_rid` (no damage)

The "O" loop ([GameServer.bb:575-584](../../../src/Modules/GameServer.bb#L575)) skips A1 and A2 (they already got their personalised packet) and skips Null-AreaInstance (stale-area mid-portal).

## Anti-cheat surface

`P_AttackActor` is one of the most security-sensitive packets — a single attack hit/miss decision drives PvP outcomes. The validation requirements above cover the known attack surface; the recent same-area gate (#276) was the most-recently-added defence (specifically against cross-area packet injection that would have bypassed PvP rules).

The handler is **NOT** privilege-gated like the BVM clicker handlers — combat is the player's privilege; the gate is "are you allowed to fight this target?" not "are you allowed to call this function?".

## Historical bugs / PR references

| PR | Fixed |
|---|---|
| [#276](https://github.com/RydeTec/rcce2/pull/276) | Same-area gate (cross-area injection prevention) |
| Two-attackers-same-tick fix (pre-PR) | The already-dead target guard at GameServer.bb:308 — prevents double KillActor + use-after-free |
| Defensive AInstance Null check | The broadcast loop at GameServer.bb:575 skips when AInstance is Null (mid-warp race) |
| [#282](https://github.com/RydeTec/rcce2/pull/282) | `FindActorInstanceFromRNID(M\FromID)` -- O(1) sender resolution |
| [#283](https://github.com/RydeTec/rcce2/pull/283) | Per-area `FirstInZone` chain walk in the observer broadcast loop |
| [#287](https://github.com/RydeTec/rcce2/pull/287) | Pet-aggro broadcast (`ActorAttack`'s pet recruitment) now walks the per-leader `FirstSlave` chain |

## Related packets

- [`P_StandardUpdate`](P_StandardUpdate.md) — movement, including the speed-hack clamp that prevents teleport-into-range attacks
- [`P_StatUpdate`](../index.md) — broadcasts HP changes; observers see victim's HP drop via this rather than the "O" P_AttackActor
- [`P_ActorDead`](../index.md) — broadcast when victim's HP drops ≤ 0
- [`P_Projectile`](../index.md) — projectile-launch broadcast (separate from this packet; ranged attacks emit both)
- [`P_ActorEffect`](../index.md) — debuff / status effects that combat triggers

## See also

- [`../encoding.md`](../encoding.md) — `RCE_StrFromInt$` byte widths
- [`../handler-conventions.md`](../handler-conventions.md) — soft-fail discipline, bounds-check, same-area gate pattern
- [`../../modules/servernet.md`](../../modules/servernet.md) — P_AttackActor's place in the dispatch
- [`GameServer.bb`'s `ActorAttack` function](../../../src/Modules/GameServer.bb#L300) — the full damage engine
