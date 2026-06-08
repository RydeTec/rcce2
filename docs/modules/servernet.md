<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**ServerNet.bb**

This module is the server-side wire dispatcher: a single ~3000-line `UpdateNetwork()` function whose body is a giant `Select Case` on `P_*` packet type. Every inbound packet from every connected client lands here. 29 server-side handlers cover account, character, inventory, combat, chat, scripting, and update-distribution flows; see the [wire-protocol reference](../protocol/index.md) for the full catalog with line refs.

For the wire-encoding primitives every handler reads off the wire, see [protocol/encoding.md](../protocol/encoding.md). For the disciplines every handler must follow (soft-fail, bounds-check, handle-Null, float clamp, iterator-during-iteration, sibling-protection asymmetry, privilege gating), see [protocol/handler-conventions.md](../protocol/handler-conventions.md).

## Architecture

Every `Case P_X` branch follows the same shape:

1. **Identify sender**: `AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)` — O(1) since PR [#282](https://github.com/RydeTec/rcce2/pull/282).
2. **Validate**: Null-check `AI`, `Len` of `M\MessageData$`, bounds on slot/ID indices, target-area same as sender (where relevant).
3. **Mutate authoritative state**.
4. **Broadcast**: send `RCE_Send` to affected clients. The 7 chat/per-tick broadcast loops now walk `FirstOnlinePlayer` instead of `Each ActorInstance` (PR [#283](https://github.com/RydeTec/rcce2/pull/283)).

## Validation patterns

The 5 disciplines from [handler-conventions.md](../protocol/handler-conventions.md):

1. **Soft-fail** — `WriteLog` + `Return` on bad input, NEVER `RuntimeError`.
2. **Bounds-check** before array index.
3. **Handle-lookup Null discipline** — `Object.X(handle) <> Null` before deref.
4. **Float sanitisation** — `ClampWorldCoord#` / `ClampSaneFloat#` at the wire boundary.
5. **Iterator-during-iteration safety** — after-cursor walk, deferred kill list, or restart-on-Delete pattern.

## Privilege-sensitive handlers

Four handlers spawn user scripts based on client input:

* `P_RightClick` (~line 1419)
* `P_Examine` (~line 1482)
* `P_Trade` (~line 1523)
* `P_ItemScript` (~line 1358)

All four pass `Handle(clicker)` as the script's `SI\AI`. This means any `BVM_RequireSelfOrPrivileged(Param1)` gate against the target parameter does **not** block clicker exploits — the clicker IS the "self." Recent hardening sweeps (PRs [#260](https://github.com/RydeTec/rcce2/pull/260), [#276](https://github.com/RydeTec/rcce2/pull/276)) corrected several misuses; see the four privilege-gate categories in [CLAUDE.md](../../CLAUDE.md).

## Authentication flow

Six auth-related handlers form an interlocked state machine hardened across PRs [#264](https://github.com/RydeTec/rcce2/pull/264)–[#268](https://github.com/RydeTec/rcce2/pull/268):

| Handler | Purpose | Hardening |
|---|---|---|
| `P_CreateAccount` (2309) | Register new account | LoginAttemptOk/Record rate limit |
| `P_VerifyAccount` (2363) | Username / password check | State-machine collapse: "P" response for every failure mode; ban / loggedon disclosure only after password verifies. Constant-time `ConstantTimeStrEq` to prevent timing oracle. |
| `P_ChangePassword` (2498) | Password rotation | Same collapse + rate limit. |
| `P_FetchCharacter` (2555) | Load saved character | LoginAttemptOk gate; ban check. |
| `P_CreateCharacter` (2679) | New character | Same. |
| `P_DeleteCharacter` (2883) | Delete character | Same. |

See [`AccountEnumerationTest.bb`](../../src/Tests/Modules/AccountEnumerationTest.bb) / [`ChangePasswordEnumerationTest.bb`](../../src/Tests/Modules/ChangePasswordEnumerationTest.bb) for the pinned response-code state machine.

## Performance — O(1) sender resolution

Since PR [#282](https://github.com/RydeTec/rcce2/pull/282), `FindActorInstanceFromRNID(M\FromID)` is O(1) bounds-checked array lookup via `Dim ActorByRNID.ActorInstance(MaxRNID)`. The maintenance hooks live at:

* `P_StartGame` login ([line 2122](../../src/Modules/ServerNet.bb#L2122)): `ActorByRNID(M\FromID) = A\Character[Number]`
* `RCE_PlayerTimedOut/HasLeft/Kicked` disconnect path ([line 1848](../../src/Modules/ServerNet.bb#L1848); clear at [line 1980](../../src/Modules/ServerNet.bb#L1980)): clear slot. Note: there is no `P_Disconnect` packet — the underlying RottNet library raises one of these three connection-loss messages.
* `FreeActorInstance` (Actors.bb): clear slot defensively

Broadcast loops (7 of them) walk a `FirstOnlinePlayer` linked list maintained at the same lifecycle hooks — PR [#283](https://github.com/RydeTec/rcce2/pull/283).

## See also

* [protocol/index.md](../protocol/index.md) — wire-protocol catalog (auto-generated)
* [protocol/encoding.md](../protocol/encoding.md) — `RCE_StrFromInt$` / `ClampWorldCoord#` / strings
* [protocol/handler-conventions.md](../protocol/handler-conventions.md) — the five disciplines
* [packets.md](packets.md) — per-packet purpose summary
* [clientnet.md](clientnet.md) — client-side dispatch
* [scripting.md](scripting.md) — BVM script lifecycle and privilege model
* [`../../CLAUDE.md`](../../CLAUDE.md) — agent-facing dev guide
