<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**MD5.bb**

Pure-Blitz MD5 hash implementation, used **only as a client-side password pre-hash** before the password is sent over the wire and re-hashed server-side with salted SHA-256.

**MD5 is cryptographically broken.** It is **not** the production password defense — that is [`PasswordHash.bb`](../../src/Modules/PasswordHash.bb)'s salted SHA-256 path. The audit comment in [`AccountsServer.bb:121`](../../src/Modules/AccountsServer.bb#L121) explicitly calls it "broken-MD5"; the role here is:

1. Avoid sending the user's plaintext password over the wire.
2. Provide a stable identifier for the server to salted SHA-256-hash and store.

That is *all* MD5 buys you in this codebase. New code paths should not adopt MD5 for anything stronger than this legacy compatibility role.

## Conceptual overview

### Public surface — one function

```basic
Function MD5$(sMessage$)
    ; ... block-pad and run the 64-step MD5 rounds ...
    Return Lower(WordToHex$(MD5_a) + WordToHex$(MD5_b) + WordToHex$(MD5_c) + WordToHex$(MD5_d))
End Function
```

`MD5$(s)` returns a 32-character lowercase hex digest of the input string. The output format is fixed and matches the de-facto MD5 specification (RFC 1321 lowercase hex serialization).

The remaining 10 functions in this file are internal helpers (`MD5_F` / `_G` / `_H` / `_I` / `_FF` / `_GG` / `_HH` / `_II` / `RotateLeft` / `WordToHex$`) implementing the four-round compression schedule. Do not call them directly from outside this file.

### How callers use it

```basic
; MainMenu.bb login flow (line 804, 869, 1193)
MD5Pass$ = MD5$(Pass$)                            ; hash the user's typed plaintext
Pa$ = RCE_StrFromInt$(Len(Name$), 1) + Name$ + RCE_StrFromInt$(Len(MD5Pass$), 1) + MD5Pass$
RCE_Send(Connection, PeerToHost, P_VerifyAccount, Pa$, True)
```

The 32-hex-char MD5 digest is what travels in `P_VerifyAccount` / `P_CreateAccount` / `P_ChangePassword`. The server then takes that digest as the "password" input and runs it through salted SHA-256 + per-account salt for storage and constant-time comparison. From the wire's perspective, the MD5 hex string IS the password — clients that bypass MainMenu and send raw plaintext would be authenticated against an MD5-hash of the stored salted SHA-256 verifier, which won't match.

### Why MD5 specifically (history)

The MD5 wrapper predates the salted SHA-256 + constant-time-compare + login-throttle work (iterations #37 / #42-#45). At the time, the database stored the raw MD5 output, and the wire-level "don't ship plaintext" was the whole defense. The current design retains MD5 only because:

- Re-rolling the client to ship plaintext would break every existing account record (which stores salted SHA-256(MD5(plaintext))).
- Re-rolling the database verifiers to salted SHA-256(plaintext) would require a forced password reset for every user.

Migration to salted SHA-256-only (drop the MD5 wrapper, store salted SHA-256(plaintext)) is a real follow-up but requires a coordinated client+server roll with a credential-migration window.

## Implementation notes

- **Pure Blitz3D integer math** — no DLL dependency. The whole algorithm runs in 32-bit signed Blitz ints via `Shl` / `Shr` / `And` / `Or` / `Xor` / `~`. The magic constants in the source comments (`&HD76AA478`, etc.) are the canonical MD5 T-table values reinterpreted as signed 32-bit ints (negative values where the high bit is set).
- **`Dim MD5_x(0)` at module scope** — a module-global scratch array that `MD5$` re-`Dim`s to `BlockNum * 16 - 1` slots each call. **Not thread-safe** — concurrent calls (none exist today, but be aware) would corrupt each other.
- **No early-exit on empty input** — `MD5$("")` returns the MD5 of the empty string (`d41d8cd98f00b204e9800998ecf8427e`). This is the spec-correct behavior.
- **No string-length cap** — `BlockNum = ((Len(s) + 8) Shr 6) + 1` scales linearly. A 1 MB input would `Dim MD5_x(262143)` slots, which Blitz3D can handle but no production call site sends inputs anywhere near that size.

## Conventions for new code touching this module

- **Don't use `MD5$` for new security primitives.** Anything new that needs a hash should use [`PasswordHash.bb`](../../src/Modules/PasswordHash.bb)'s salted SHA-256 path or a different module's MD5-replacement (filename hashing, deterministic-asset-ID generation, etc. — none of those exist today; they'd be added separately).
- **Don't call the internal helpers (`MD5_F` / `MD5_FF` / etc.) from outside this file.** They're not part of the public contract.
- **Don't add helpers that mutate `MD5_x` outside `MD5$`.** The scratch array is owned by `MD5$` for the duration of one call.
- **Migration to salted-SHA-256-only is non-trivial.** The three `MD5$(Pass$)` call sites in [`MainMenu.bb`](mainmenu.md) are the *client-side* deletions; on the server side [`PasswordHash.bb`](../../src/Modules/PasswordHash.bb)'s `SHA256Hex$(Salt + ClientMD5)` shape would also need to change, every stored verifier (`$1$<salt>$<sha256-64-hex>` in the account record) would need re-hashing during a forced-reset window, and the `MD5.bb` include itself dropped from [`Client.bb:149`](../../src/Client.bb#L149). The "drop the wrapper" framing is the client-side picture only.

## Related modules

- [`MainMenu.bb`](mainmenu.md) — the **only** caller. Three sites at MainMenu.bb:804, :869, :1193 wrap the user's typed password before sending `P_VerifyAccount` / `P_CreateAccount` / `P_ChangePassword`.
- [`PasswordHash.bb`](../../src/Modules/PasswordHash.bb) — the actual production defense. Server-side. salted SHA-256 + constant-time-compare + dummy-hash path. The `MD5$` output is its input.
- [`AccountsServer.bb`](accountsserver.md) — flat-file account path; the audit comment at line 121 explicitly documents the "broken-MD5" role.

## See also

- CLAUDE.md (no specific section — MD5 is a legacy primitive, not a project-wide convention).
- The 2004-era MD5 collision attacks: not relevant for password-input pre-hashing (preimage resistance is still ~2^128 for now), but adopting MD5 for *any other purpose* would be a real vulnerability.

* * *

This module's surface is one function (`MD5$`); the source at [`src/Modules/MD5.bb`](../../src/Modules/MD5.bb) is the reference. No additional Reference section below.
