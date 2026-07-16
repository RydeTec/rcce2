# P_ChangePassword

**Direction:** C -> S (request), S -> C (single-byte result reply)
**Numeric ID:** 6 ([Packets.bb:7](../../../src/Modules/Packets.bb#L7))
**Client send site:** **none in the current engine** — see "No live sender" below. The only sender in the tree is the legacy snapshot at [Tools/Modules_old/ServerNet.bb:1728](../../../src/Tools/Modules_old/ServerNet.bb#L1728) (not built).
**Server handler:** [ServerNet.bb:2533](../../../src/Modules/ServerNet.bb#L2533) (`Case P_ChangePassword`)

## Purpose

Rotate the password on an existing account. The requester sends a username, the MD5 of the current password, and the MD5 of the new password; the server verifies the current password **and** that the requester currently holds the account's live session, then stores the new password in the salted-SHA-256 v1 format.

The session check ([`RequesterOwnsAccountSession`](../../../src/Modules/AccountsServer.bb#L124)) is what distinguishes this from a pure pre-auth packet: although any peer can *send* it, the change only commits if the sender is the account's currently-logged-in connection. Without that gate a captured/replayed packet carrying the (replayable) MD5 could permanently steal the account.

### No live sender

Grep of `src/Modules` finds `P_ChangePassword` only in [Packets.bb](../../../src/Modules/Packets.bb#L7) (the constant) and [ServerNet.bb](../../../src/Modules/ServerNet.bb#L2533) (the handler). The current [MainMenu.bb](../../../src/Modules/MainMenu.bb) has **no** "change password" UI or `RCE_Send(..., P_ChangePassword, ...)` call — the only client-side sender is the un-built legacy copy under `Tools/Modules_old/`. The handler is therefore live and hardened but currently unreachable from the shipping client. The field layout below is reconstructed from the handler's reads and the legacy sender's writes (the two agree on framing); a future client that re-adds the feature must match it.

## Field layout

Reconstructed from the server reads ([ServerNet.bb:2534-2557](../../../src/Modules/ServerNet.bb#L2534)) and the legacy sender ([Tools/Modules_old/ServerNet.bb:1715-1727](../../../src/Tools/Modules_old/ServerNet.bb#L1715)). Both password fields are MD5 hex (`MD5$(plaintext)`), per the account cluster's convention.

| # | Field | Width | Sender write (legacy) | Receiver read (ServerNet.bb) |
|---|---|---|---|---|
| 1 | Username length | 1 byte | `RCE_StrFromInt$(Len(Name$), 1)` | `RCE_IntFromStr(Left$(M\MessageData$, 1))` -> `UsernameLen` [:2534](../../../src/Modules/ServerNet.bb#L2534) |
| 2 | Username | `UsernameLen` bytes | `Name$` (concat) | `Mid$(M\MessageData$, 2, UsernameLen)` [:2535](../../../src/Modules/ServerNet.bb#L2535) |
| 3 | Current password (MD5 hex) length | 1 byte | `RCE_StrFromInt$(Len(OldMD5$), 1)` | `RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))` where `Offset = 2 + UsernameLen` [:2540-2541](../../../src/Modules/ServerNet.bb#L2540) |
| 4 | Current password (MD5 hex) | `PwdLen` bytes | `OldMD5$` (concat) | `Mid$(M\MessageData$, Offset + 1, PwdLen)` [:2552](../../../src/Modules/ServerNet.bb#L2552) |
| 5 | New password (MD5 hex) length | 1 byte | `RCE_StrFromInt$(Len(NewMD5$), 1)` | `RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))` after advancing `Offset = Offset + 1 + PwdLen` past the current-password block |
| 6 | New password (MD5 hex) | `PwdLen` (re-read) bytes | `NewMD5$` (concat) | `Mid$(M\MessageData$, Offset + 1, PwdLen)` [:2557](../../../src/Modules/ServerNet.bb#L2557) |

All length prefixes are 1 byte; the sender writes match fields 1-6.

**Cursor progression (field 5).** After reading the current-password length at `Offset`, the handler advances that same cursor with `Offset = Offset + 1 + PwdLen`. The new-password length prefix therefore begins after the username block, current-password length byte, and current-password bytes. This preserves the existing wire format for every username length and prevents the new password from being decoded from username/current-password bytes. The legacy unbuilt snapshot still contains its historical offset defect; it is not part of the shipping handler or this packet-format-preserving fix.

### Reply

| Reply byte | Meaning | Server emit | Client mapping |
|---|---|---|---|
| `"Y"` | Password changed | [ServerNet.bb:2558](../../../src/Modules/ServerNet.bb#L2558) | (no live client; legacy showed success) |
| `"P"` | Any failure — wrong password, not session owner, empty stored hash, truncated packet, **or username not found** | [:2562](../../../src/Modules/ServerNet.bb#L2562) / [:2586](../../../src/Modules/ServerNet.bb#L2586) | (no live client) |

The legacy server additionally sent `"N"` for "account not found" ([Tools/Modules_old/ServerNet.bb:1740](../../../src/Tools/Modules_old/ServerNet.bb#L1740)); the current handler collapses that into `"P"` to close the enumeration oracle (PR [#265](https://github.com/RydeTec/rcce2/pull/265)).

## Validation requirements (server-side)

1. **Account lookup** ([:2538-2539](../../../src/Modules/ServerNet.bb#L2538)) — case-insensitive (`Upper$`) scan.
2. **Combined verify gate** ([:2552](../../../src/Modules/ServerNet.bb#L2552)) — a single `And` chain that must all hold to commit:
   - `PwdLen >= 1` — rejects truncated / empty-password packets (an empty supplied password would otherwise match an account historically stored with an empty `Pass$`).
   - `A\Pass$ <> ""` — rejects accounts with an empty stored hash.
   - `VerifyPassword%(A\Pass$, currentMD5)` — current password verifies (constant-time, both legacy MD5 and v1 accepted).
   - `RequesterOwnsAccountSession(A, M\FromID)` — the requester is the account's currently-logged-in connection ([AccountsServer.bb:124-133](../../../src/Modules/AccountsServer.bb#L124)). This is the replay/theft mitigation.
3. **On success** ([:2557](../../../src/Modules/ServerNet.bb#L2557)) — `A\Pass$ = HashPassword$(newMD5)` stores the new password in v1 salted format immediately (not the raw client MD5); reply `"Y"`.
4. **On any failure of the gate** ([:2562](../../../src/Modules/ServerNet.bb#L2562)) — reply `"P"`.
5. **Account-not-found path** ([:2582-2586](../../../src/Modules/ServerNet.bb#L2582)) — `If Exists = False`, the handler still reads the current-password field and calls `VerifyPassword%("", ...)` to pay the SHA-256 cost (timing-uniform with the found-account-wrong-password path), then replies `"P"` — the same code as a credential failure, so the reply does not betray whether the username is registered.

## Anti-cheat / abuse surface

- **Account theft via replay — defended.** `RequesterOwnsAccountSession` means a captured packet (carrying the replayable MD5) cannot change the password unless the attacker also currently holds the victim's live session. PR [#76](https://github.com/RydeTec/rcce2/pull/76) (`756ad1ab`) added this gate.
- **Username enumeration — defended (post PR #265).** Found-but-failed and not-found both reply `"P"`; the no-account path pays the dummy SHA-256 cost so timing is uniform. Pinned by [`ChangePasswordEnumerationTest.bb`](../../../src/Tests/Modules/ChangePasswordEnumerationTest.bb).
- **No `LoginAttemptOk` throttle — NOT defended.** Unlike `P_VerifyAccount`, `P_StartGame`, `P_FetchCharacter`, `P_CreateCharacter`, and `P_DeleteCharacter`, this handler has **no** per-source rate limit (verified: no `LoginAttemptOk` call between [:2533](../../../src/Modules/ServerNet.bb#L2533) and the next handler at [:2590](../../../src/Modules/ServerNet.bb#L2590)). Because every code path — including not-found — calls `VerifyPassword%`, which runs a full SHA-256, an attacker can pump the server's hashing CPU at line rate with bogus `P_ChangePassword` packets. This is exactly the line-rate dummy-hash DoS that PR [#268](https://github.com/RydeTec/rcce2/pull/268) closed for the other auth handlers, and it was not extended here. The session gate prevents *theft*, not the *cost-amplification* DoS.
- **Wire replay of the new password — NOT defended.** As with the whole cluster, the MD5 travels on the wire; a sniffer learns the new password's MD5. Out of scope without TLS.

## Historical bugs / PR references

| PR / commit | Relevance |
|---|---|
| PR [#76](https://github.com/RydeTec/rcce2/pull/76) (`756ad1ab`) | Added the `RequesterOwnsAccountSession` gate — a replayed/captured packet can no longer steal an account by knowing the (broken-MD5) hash. |
| PR [#118](https://github.com/RydeTec/rcce2/pull/118) (`88aaf393`) | New password stored as salted SHA-256 v1 (`HashPassword$`) rather than raw client MD5. |
| PR [#265](https://github.com/RydeTec/rcce2/pull/265) (`03ccdc99`) | Collapsed the `"N"` (not-found) vs `"P"` (auth-fail) enumeration oracle into a single `"P"`, plus dummy-hash on the not-found path. Sibling fix to PR [#264](https://github.com/RydeTec/rcce2/pull/264) for `P_VerifyAccount`. Pinned by [`ChangePasswordEnumerationTest.bb`](../../../src/Tests/Modules/ChangePasswordEnumerationTest.bb). |
| PR [#268](https://github.com/RydeTec/rcce2/pull/268) (`82410986`) | Extended the throttle to four sibling handlers — **excluding** `P_ChangePassword`. The throttle gap above is current behavior. |

The post-collapse state machine is mirrored in [`ChangePasswordEnumerationTest.bb`](../../../src/Tests/Modules/ChangePasswordEnumerationTest.bb) (`ChangePasswordResponse$`); production changes must update both copies.

## Related packets

- [`P_VerifyAccount`](P_VerifyAccount.md) — the login handler this one mirrors for the enumeration-oracle fix.
- [`P_CreateAccount`](P_CreateAccount.md) — registration; same username/MD5 framing, also missing the throttle.
- `P_DeleteCharacter` ([ServerNet.bb:2951](../../../src/Modules/ServerNet.bb#L2951)) — the other handler gated by `RequesterOwnsAccountSession` (PR #76).

## See also

- [`../encoding.md`](../encoding.md) — `RCE_StrFromInt$` / `RCE_IntFromStr`, 1-byte length prefixes.
- [`../handler-conventions.md`](../handler-conventions.md) — auth-before-disclosure, bounds-then-deref, soft-fail.
- [`AccountsServer.bb`'s `RequesterOwnsAccountSession`](../../../src/Modules/AccountsServer.bb#L124) — the session-ownership gate.
- [`PasswordHash.bb`](../../../src/Modules/PasswordHash.bb) — `VerifyPassword%`, `HashPassword$`, timing-uniformity contract.
