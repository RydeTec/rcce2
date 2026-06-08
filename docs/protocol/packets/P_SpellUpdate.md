# P_SpellUpdate

**Direction:** C → S only (no server-emitted form)
**Numeric ID:** 27
**Server handler:** [ServerNet.bb:1121](../../../src/Modules/ServerNet.bb#L1121)
**Client emit sites:** F (fire) at [Interface3D.bb:1097](../../../src/Modules/Interface3D.bb#L1097) (action-bar, no stale-target guard — see below) / [:1227](../../../src/Modules/Interface3D.bb#L1227) (action-bar with guard) / [:1514](../../../src/Modules/Interface3D.bb#L1514) (spell-book hotkey); U (unmemorise) at [:1341](../../../src/Modules/Interface3D.bb#L1341); M (memorise) at [:1452](../../../src/Modules/Interface3D.bb#L1452)

## Purpose

The player-spell-lifecycle channel. Three sub-codes cover the three things a player can do with a spell: memorise it, unmemorise it, or fire it. The fire path is the most-hardened handler in the codebase — combat-relevant, exploit-magnet, and reachable by every client every tick. Multiple security PRs have layered defences on the F sub-code over time.

There is no server-emitted form. Server-authored spell-level changes ride [`P_KnownSpellUpdate`](../index.md); damage results from a cast ride [`P_AttackActor`](P_AttackActor.md) / [`P_StatUpdate`](P_StatUpdate.md).

## Field layout

| Sub-code | Total | Layout | Direction |
|---|---|---|---|
| `"U"` | 3 bytes | `1B sub + 2B KnownSpellNum` | C → S — player wants to unmemorise slot. |
| `"M"` | 3 bytes | `1B sub + 2B KnownSpellNum` | C → S — player wants to memorise slot. |
| `"F"` | 3 or 5 bytes | `1B sub + 2B SpellID + [2B TargetRuntimeID]` | C → S — player firing the spell, optionally at a target. |

The `KnownSpellNum` for U / M is the slot index into the player's `Me\MemorisedSpells[]` (server-side `AI\MemorisedSpells[0..9]` — a 10-slot table holding `KnownSpells` indices). The `SpellID` for F is the slot index into `AI\KnownSpells[0..999]` (a 1000-slot inventory of every spell the player has learned).

`F`'s optional `TargetRuntimeID` is present only when the client has a `PlayerTarget`. All three emit sites in [`Interface3D.bb`](../../../src/Modules/Interface3D.bb) append the target bytes only inside an `If PlayerTarget > 0 ... If AI <> Null` guard — a stale `PlayerTarget` handle sends the cast untargeted rather than crashing the client (see audit comment at [Interface3D.bb:1221-1224](../../../src/Modules/Interface3D.bb#L1221)). The server's F handler tolerates the missing-target form because `Context = Null` flows naturally through the rest of the cast pipeline.

## Validation requirements — F (fire) sub-code

The F handler at [ServerNet.bb:1143-1253](../../../src/Modules/ServerNet.bb#L1143) is the security-sensitive one. **Eleven** gates layer on top of each other; missing any one was historically a real exploit.

### Wire-level gates

1. **Sender validity**: `AI = FindActorInstanceFromRNID(M\FromID)`; bails on Null.
2. **Target-bytes presence**: `Len(M\MessageData$) >= 5` before reading `Mid$(..., 4, 2)` for the target. The pre-fix bug used `Len > 3`, which admitted 4-byte packets whose `Mid$` read past end-of-string returned an empty/truncated RuntimeID that aliased to actor 0. See audit comment at [ServerNet.bb:1147-1153](../../../src/Modules/ServerNet.bb#L1147).

### Target-validity gates

3. **Stale-target rejection**: `If Context\Attributes\Value[HealthStat] <= 0 Then Context = Null`. A `RuntimeIDList` lookup can return a "live" pointer to an actor whose `FreeActorInstance` is queued in `PendingKill` — casting at it would spawn a spell script against freed memory. Treat HP-zero as already-dead. PR [#103](https://github.com/RydeTec/rcce2/pull/103) (Track OO).
4. **Cross-area rejection**: `If Context\ServerArea <> AI\ServerArea Then Context = Null`. A client whose view is area-local can still send any `RuntimeID` from any area; the server reject prevents cross-zone spell-snipe. Same PR.

### Spell-resolution gates

5. **Known-spell lookup**: walk `AI\KnownSpells[0..999]` for the wire `Num`. The wire field is the spell-ID; the array index is what `SpellCharge[]` / `SpellLevels[]` use, so the conversion `Num → array index` happens at the cast site.
6. **SpellID range check**: `If SpellID >= 0 And SpellID <= 999` before using SpellID as an array subscript. Bounds-check-before-array-index discipline.
7. **`SpellsList(SpellID) <> Null`**: a stale character save (admin deleted the spell between sessions) or corrupted KnownSpells slot would otherwise deref Null. `P_FetchCharacter` already prunes stale entries at character-select but the cast-site guard is defense-in-depth. PR [#166](https://github.com/RydeTec/rcce2/pull/166). On hit, prune the slot (`AI\KnownSpells[Num] = 0 : AI\SpellLevels[Num] = 0`) and bail via `Goto SkipSpellCast`.

### Memorisation + cooldown gates

8. **`RequireMemorise` gate**: if the global flag is True, the spell must be in `AI\MemorisedSpells[0..9]`. If False, every known spell is castable. (`RequireMemorise` is a server config flag, set at boot from the project file.)
9. **Per-actor 100ms floor**: `If NowMs - AI\LastSpellFireMs < 100 Then [silent drop]`. Without this, a spell with `RechargeTime = 0` could be cast every `UpdateNetwork` tick (effectively unbounded). PR [d5c36e8](https://github.com/RydeTec/rcce2/commit/d5c36e8). The 100ms floor is per-actor, so two different actors can cast in the same 100ms window.
10. **Per-spell cooldown**: `If AI\SpellCharge[SpellID] > 0 Then [send LS_AbilityNotRecharged]`. **Important**: `SpellCharge` is keyed by `SpellID` (the underlying spell-list index), not by the per-actor `KnownSpells` slot index. The pre-fix bug stored cooldowns at two different slot-index spaces — the same physical spell had two independent cooldowns and toggling `RequireMemorise` or re-memorising the same spell into a different slot let the player double-cast. PR [d5c36e8](https://github.com/RydeTec/rcce2/commit/d5c36e8) unified the keying.

### Privilege / restriction gates

11. **ExclusiveRace / ExclusiveClass**: when `Len(Sp\ExclusiveRace$) > 0`, the gate compares `If Upper$(AI\Actor\Race$) <> Upper$(Sp\ExclusiveRace$) Then SpellAllowed = False` (read-only compare against the spell's authored field; no mutation). Same shape for `ExclusiveClass$`. Editor-exposed in GUE, persisted by `SaveSpells` / `LoadSpells`, but the cast path never enforced them pre-fix — a paladin-only Smite taught to a thief via script or recovered from a stale save would fire normally. PR [04dd8ac](https://github.com/RydeTec/rcce2/commit/04dd8ac) (Tier 1 silent-defects sweep). The check mirrors the item-eat gate in [`P_EatItem`](../index.md). On failure, the handler emits a reason-specific message via `LS_RaceOnly` or `LS_ClassOnly` (the same tooltip strings item descriptions use for race/class restrictions at [Interface3D.bb:1938+](../../../src/Modules/Interface3D.bb#L1938)) — the player sees e.g. \"Paladin race only\" instead of the legacy misleading \"ability not recharged\".

On all 11 gates passing, the handler calls `ThreadScript(Sp\Script$, Sp\SMethod$, Handle(AI), Handle(Context), AI\SpellLevels[Num])` to spawn the spell's behaviour script, then sets the per-spell + per-actor cooldown timestamps.

## Validation requirements — U / M sub-codes

The U (unmemorise) and M (memorise) handlers at [ServerNet.bb:1126-1141](../../../src/Modules/ServerNet.bb#L1126) are much simpler than F:

- **`RequireMemorise` gate**: both U and M are guarded `If RequireMemorise` — no-op when the server is configured for free-cast.
- **U sentinel**: matching `AI\MemorisedSpells[i] = Num` is replaced with `5000` (well outside the valid 0..999 spell-ID range — reads as "empty slot" everywhere downstream).
- **M range check**: `If MS\KnownNum < 0 Or MS\KnownNum > 999 Then Delete MS`. Without this, an out-of-range memorise request would create a `MemorisingSpell` instance pinned to nothing; subsequent timer ticks would race-walk an invalid slot.

The `MemorisingSpell` Type ([Spells.bb:17-21](../../../src/Modules/Spells.bb#L17)) is a server-side queue entry — a `MemorisingSpell` per in-progress memorisation, holding `AI` / `KnownNum` / `CreatedTime`. The server's per-tick `For MS = Each MemorisingSpell` walk completes memorisations after the configured delay elapses (see [`Spells.bb`](../../../src/Modules/Spells.bb) for the timer logic).

## Client-side cooldown mirror

Each of the three client F-emit sites also sets `Me\SpellCharge[Num] = Sp\RechargeTime` immediately after sending the packet. This is a **predictive client-side cooldown** — the client decrements the visual cooldown without waiting for server confirmation. If the server rejects the cast (gate 9 or 10), the client's prediction is wrong and the user sees a cooldown that doesn't match server state until the next `P_KnownSpellUpdate` resyncs. Out of scope for this packet; documented for cross-reference.

## Anti-cheat surface

`P_SpellUpdate` is one of the highest-stakes packets — combat damage, healing, buff/debuff. The 11 F-handler gates above are the entire surface; every one was added to close a specific exploit. The key established disciplines:

- **Bounds before deref** for `KnownSpells[Num]`, `SpellCharge[SpellID]`, `MemorisedSpells[i]`.
- **Null after `RuntimeIDList(...)`** + dead/cross-area checks before using `Context`.
- **`SpellsList(SpellID)` Null guard** at the cast site (not just at character-load).
- **Cooldown keyed by SpellID, not slot-index** (single source of truth).
- **Per-actor 100ms floor** (zero-RechargeTime spam prevention).
- **ExclusiveRace / ExclusiveClass** enforced at cast, not just authored.

The handler does **NOT** privilege-gate via `BVM_RequirePrivileged` — casting is the player's privilege; the gates are "is this a legal cast?" not "are you allowed to spawn a spell script?". (The spell's `ThreadScript` spawn uses the default non-privileged flag — see CLAUDE.md "Privilege gating in BVM commands" for the privileged-vs-not distinction.)

## Historical bugs / PR references

| PR / Commit | Fixed |
|---|---|
| Pre-PR (4-byte packet bug) | `Len > 3` admitted 4-byte F packets that aliased target to actor 0; corrected to `Len >= 5`. |
| [#103](https://github.com/RydeTec/rcce2/pull/103) (Track OO) | Stale-target rejection (HP-zero = freed-but-queued) + cross-area target rejection. |
| [d5c36e8](https://github.com/RydeTec/rcce2/commit/d5c36e8) | Unified SpellCharge keying on SpellID (was dual-cooldown-space bug); added per-actor 100ms LastSpellFireMs floor. |
| [#166](https://github.com/RydeTec/rcce2/pull/166) (aa8abbf) | Null-guard `SpellsList(SpellID)` at cast site + prune stale slot. |
| [04dd8ac](https://github.com/RydeTec/rcce2/commit/04dd8ac) (Tier 1 silent-defects) | ExclusiveRace / ExclusiveClass enforced at cast (was authored-but-not-enforced). |
| Audit comments | Six inline audit comment clusters at lines [1147](../../../src/Modules/ServerNet.bb#L1147) / [1157](../../../src/Modules/ServerNet.bb#L1157) / [1180](../../../src/Modules/ServerNet.bb#L1180) / [1190](../../../src/Modules/ServerNet.bb#L1190) / [1212](../../../src/Modules/ServerNet.bb#L1212) / [1226](../../../src/Modules/ServerNet.bb#L1226) capture the threat model for each gate so future contributors don't undo them. |

## Related packets

- [`P_AttackActor`](P_AttackActor.md) — direct melee/ranged attack; spells go through this channel instead of P_AttackActor (different damage origin, different cooldown machinery).
- [`P_StatUpdate`](P_StatUpdate.md) — broadcasts HP / attribute changes triggered by spell scripts.
- [`P_KnownSpellUpdate`](../index.md) — S→C, broadcasts changes to `AI\KnownSpells[]` / `AI\SpellLevels[]` (e.g. from `BVM_SETABILITYLEVEL`); resyncs the client's `Me\SpellLevels` after a server-authoritative change.
- [`P_ChatMessage`](P_ChatMessage.md) — used for the `LS_AbilityNotRecharged` error reply (Chr$(253) red text).
- [`P_FetchCharacter`](../index.md) — initial-load packet that prunes stale `KnownSpells` entries before the player enters game.

## See also

- [`../encoding.md`](../encoding.md) — wire-encoding primitives (`RCE_StrFromInt$` / `RCE_IntFromStr`).
- [`../handler-conventions.md`](../handler-conventions.md) — bounds-before-deref, Null-after-lookup, stale-handle disciplines that this handler exemplifies.
- [`Spells.bb`'s `MemorisingSpell` Type + processor](../../../src/Modules/Spells.bb#L17) — the server-side memorise queue + timer.
- CLAUDE.md → "Handle-lookup Null discipline" — the pattern this handler follows for `Object.ActorInstance` results.
