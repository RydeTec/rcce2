# P_VerifyAccount

**Direction:** C -> S (login request), S -> C (result + character-list reply)
**Numeric ID:** 2 ([Packets.bb:3](../../../src/Modules/Packets.bb#L3))
**Client send site:** [MainMenu.bb:813](../../../src/Modules/MainMenu.bb#L813) (the "Log in" button handler; reply read at [MainMenu.bb:829-844](../../../src/Modules/MainMenu.bb#L829))
**Server handler:** [ServerNet.bb:2513](../../../src/Modules/ServerNet.bb#L2513) (`Case P_VerifyAccount`) — the canonical bounds-check / auth-before-disclosure example.

## Purpose

The login packet. A peer submits a username and the MD5 of a typed password; the server verifies the password against the salted-SHA-256-at-rest record and, on success, replies with the account's character list. This is the gate that turns a connected-but-anonymous peer into an authenticated session.

It is the most security-sensitive packet in the account cluster: it is pre-auth, attacker-controllable, and its reply historically leaked whether a username existed, whether it was banned, and whether it was already online. PR [#264](https://github.com/RydeTec/rcce2/pull/264) collapsed those oracles (see Historical bugs).

A large MySQL variant of this handler is present but **commented out** ([ServerNet.bb:2518-2551](../../../src/Modules/ServerNet.bb#L2518)); only the in-memory `Account`-list path is live.

## Field layout

Request body, built at [MainMenu.bb:811-812](../../../src/Modules/MainMenu.bb#L811) (`MD5Pass$ = MD5$(Pass$)`):

| # | Field | Width | Sender write (MainMenu.bb) | Receiver read (ServerNet.bb) |
|---|---|---|---|---|
| 1 | Username length | 1 byte | `RCE_StrFromInt$(Len(Name$), 1)` | `RCE_IntFromStr(Left$(M\MessageData$, 1))` -> `UsernameLen` [:2514](../../../src/Modules/ServerNet.bb#L2514) |
| 2 | Username | `UsernameLen` bytes | `Name$` (concat) | `Mid$(M\MessageData$, 2, UsernameLen)` [:2515](../../../src/Modules/ServerNet.bb#L2515) |
| 3 | Password (MD5 hex) length | 1 byte | `RCE_StrFromInt$(Len(MD5Pass$), 1)` | `RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))` where `Offset = 2 + UsernameLen` [:2576-2577](../../../src/Modules/ServerNet.bb#L2576) |
| 4 | Password (MD5 hex) | `PwdLen` bytes | `Mid$(M\MessageData$, Offset + 1, PwdLen)` [:2591](../../../src/Modules/ServerNet.bb#L2591) / [:2603](../../../src/Modules/ServerNet.bb#L2603) |

All length prefixes are 1 byte; sender and receiver widths match.

### Reply

| Reply byte(s) | Meaning | Server emit | Client mapping ([MainMenu.bb:835-843](../../../src/Modules/MainMenu.bb#L835)) |
|---|---|---|---|
| `"Y"` + char blocks | Success — password verified, not banned, not online | [ServerNet.bb:2641](../../../src/Modules/ServerNet.bb#L2641) | `Result = 1`, `CharList$ = Right$(...)` -> proceed to `P_FetchActors` |
| `"P"` | Auth failure (any cause — see below) | [:2565](../../../src/Modules/ServerNet.bb#L2565) / [:2593](../../../src/Modules/ServerNet.bb#L2593) / [:2611](../../../src/Modules/ServerNet.bb#L2611) | `Result = -1` -> `LS_InvalidPassword` |
| `"B"` | Banned (**only after** password verifies) | [:2620](../../../src/Modules/ServerNet.bb#L2620) | `Result = 0` -> `LS_YouAreBanned` |
| `"L"` | Already logged in (**only after** password verifies) | [:2626](../../../src/Modules/ServerNet.bb#L2626) | `Result = -3` -> `LS_AccountAlreadyConnected` |
| `"N"` | *legacy only* — not emitted by the post-collapse server | (none in current source) | folded into the same `Result = -1` branch as `"P"` for legacy-server compat ([MainMenu.bb:830-836](../../../src/Modules/MainMenu.bb#L830)) |

The `"Y"` reply is followed by up to 10 character blocks ([:2633-2640](../../../src/Modules/ServerNet.bb#L2633)), each: `1B name-len + name + 2B ActorID + 1B Gender + 1B FaceTex + 1B Hair + 1B Beard + 1B BodyTex`.

## Validation requirements (server-side)

The handler implements an **auth-before-disclosure** state machine. Branch order:

1. **Rate-limit gate** — `If Not LoginAttemptOk(M\FromID)` ([:2564](../../../src/Modules/ServerNet.bb#L2564)) -> reply `"P"` (same code as a normal failure, so the throttle is not itself an oracle). The throttle is per-source `FromID`: 5 failures in a 60s window ([AccountsServer.bb:64-89](../../../src/Modules/AccountsServer.bb#L64)). A success resets the counter via `LoginAttemptRecord(..., True)`.
2. **Find account without disclosing the result** ([:2568-2574](../../../src/Modules/ServerNet.bb#L2568)) — case-insensitive (`Upper$`) scan into a local `FoundA`; no reply is emitted yet.
3. **Truncated / empty-password guard** ([:2584](../../../src/Modules/ServerNet.bb#L2584)) — `If FoundA = Null Or PwdLen < 1`. Rejects the 1-byte packet whose empty password would otherwise match any account historically stored with an empty `Pass$`. **Crucially this path still calls `VerifyPassword%("", ...)`** ([:2591](../../../src/Modules/ServerNet.bb#L2591)) to pay the SHA-256 cost, so the no-account / truncated path is timing-indistinguishable from a wrong-password path. Records a failed attempt and replies `"P"`.
4. **Empty stored hash -> fail** ([:2600-2601](../../../src/Modules/ServerNet.bb#L2600)) — an account whose `Pass$` is `""` can never authenticate.
5. **Password verify** ([:2603](../../../src/Modules/ServerNet.bb#L2603)) — `VerifyPassword%(FoundA\Pass$, suppliedMD5)`. Accepts both legacy raw-MD5 and v1 `$1$<salt>$<sha256>` records; uses `ConstantTimeStrEq` (no first-differing-byte short-circuit). Wrong password -> record failure, reply `"P"` ([:2606-2611](../../../src/Modules/ServerNet.bb#L2606)).
6. **Banned check** ([:2612](../../../src/Modules/ServerNet.bb#L2612)) — only reached after the password verifies, so `"B"` is disclosed only to the legitimate owner. Counted as a failed attempt for throttle bookkeeping ([:2619](../../../src/Modules/ServerNet.bb#L2619)).
7. **Already-online check** ([:2621](../../../src/Modules/ServerNet.bb#L2621)) — `FoundA\LoggedOn <> -1` -> `"L"`, again only post-verify.
8. **Success** ([:2627-2641](../../../src/Modules/ServerNet.bb#L2627)) — **lazy-migrate** a legacy on-disk hash to v1 and commit it through `SaveAccounts()` before recording success, then build and send the character list. A failed commit restores the verified legacy hash and retains the same successful reply, so the next successful login can retry the at-rest hardening without changing packet framing.

The local-hoist of the verify result (`Local PwdOk%`, [:2599](../../../src/Modules/ServerNet.bb#L2599)) exists to dodge a BlitzForge parser rejection of `Or Not <call>` inside an `ElseIf` condition (PR `4f212035`).

## Anti-cheat / abuse surface

This packet is the front line for credential attacks.

- **Credential stuffing / brute force — defended by the per-source throttle.** `LoginAttemptOk` caps a single `FromID` at 5 failures / 60s ([AccountsServer.bb:64-65](../../../src/Modules/AccountsServer.bb#L64)). Note the throttle keys on `M\FromID` (the ENet peer), so an attacker controlling many source connections is throttled per-connection, not globally — distributed stuffing is only partially mitigated.
- **Username / ban / presence enumeration — defended (post PR #264).** Every failure mode collapses to `"P"`; `"B"` and `"L"` are emitted **only after** the password verifies. A pre-auth attacker therefore cannot use the reply code to learn whether a username is registered, banned, or online.
- **Timing oracle — defended (post PR #267).** `VerifyPassword%` always runs a dummy SHA-256 on the no-account / malformed path ([PasswordHash.bb:278](../../../src/Modules/PasswordHash.bb#L278), with an explicit "do not remove" warning at [:275](../../../src/Modules/PasswordHash.bb#L275)), so wall-clock delta no longer discriminates "account doesn't exist" from "wrong password." Both compare paths use `ConstantTimeStrEq`.
- **Wire replay — NOT defended.** The wire carries `MD5$(password)`; a sniffer who captures one login packet can replay it indefinitely. The salted-SHA-256-at-rest format protects `Accounts.dat` theft, not the wire. TLS / challenge-response is out of scope (see [PasswordHash.bb:14-17](../../../src/Modules/PasswordHash.bb#L14)).
- **Truncated-packet matching — defended.** The `PwdLen < 1` guard ([:2584](../../../src/Modules/ServerNet.bb#L2584)) blocks the empty-password-matches-empty-stored-hash trick, and pays the hash cost anyway to stay timing-uniform.

## Historical bugs / PR references

| PR / commit | Fixed |
|---|---|
| PR [#75](https://github.com/RydeTec/rcce2/pull/75) (`4b27d204`) | Added the initial `LoginAttemptOk` rate-limit to throttle credential stuffing. |
| PR [#118](https://github.com/RydeTec/rcce2/pull/118) (`88aaf393`) | Salted-SHA-256-at-rest migration + lazy upgrade on first successful login. |
| PR [#264](https://github.com/RydeTec/rcce2/pull/264) (`31a88955`, `2cc350f1`) | Collapsed the username / ban / presence enumeration oracle: single `"P"` failure code; `"B"`/`"L"` gated behind password verification. Pinned by [`AccountEnumerationTest.bb`](../../../src/Tests/Modules/AccountEnumerationTest.bb). |
| PR [#267](https://github.com/RydeTec/rcce2/pull/267) (`4572e854`, `d6dd78b0`) | Closed the timing oracle: constant-time compare + dummy SHA-256 on the no-account path. The `DummyOut$` assignment carries a reinforced "do not remove" warning. |
| PR [#268](https://github.com/RydeTec/rcce2/pull/268) (`82410986`) | Extended the throttle to the four sibling auth handlers (`P_StartGame`, `P_FetchCharacter`, `P_CreateCharacter`, `P_DeleteCharacter`) so they can't be used to pump the dummy-hash CPU cost at line rate. (`P_ChangePassword` was **not** included — see [`P_ChangePassword`](P_ChangePassword.md).) |

The post-collapse state machine is mirrored verbatim in [`AccountEnumerationTest.bb`](../../../src/Tests/Modules/AccountEnumerationTest.bb) (`VerifyAccountResponse$`): any production change must update both copies.

## Related packets

- [`P_CreateAccount`](P_CreateAccount.md) — registration; same username/password framing.
- [`P_ChangePassword`](P_ChangePassword.md) — sibling auth handler with the same enumeration threat model (closed by PR [#265](https://github.com/RydeTec/rcce2/pull/265)).
- `P_StartGame` ([ServerNet.bb:2214](../../../src/Modules/ServerNet.bb#L2214)) — re-verifies the same username/MD5 before entering a character into the world; shares the throttle and uses the same durable legacy-password migration helper on a successful authentication.
- [`P_FetchActors`](../index.md) — the client's next request after a `"Y"` reply, to download actor/item/attribute tables.

## See also

- [`../encoding.md`](../encoding.md) — `RCE_StrFromInt$` / `RCE_IntFromStr`, 1-byte length prefixes.
- [`../handler-conventions.md`](../handler-conventions.md) — bounds-then-deref, auth-before-disclosure, soft-fail discipline.
- [`PasswordHash.bb`](../../../src/Modules/PasswordHash.bb) — `VerifyPassword%`, `ConstantTimeStrEq`, `UpgradePasswordIfLegacy$`, dummy-hash timing-uniformity contract.
- [`AccountsServer.bb`](../../../src/Modules/AccountsServer.bb#L64) — `LoginAttemptOk` / `LoginAttemptRecord` throttle.
