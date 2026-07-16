# Phase 1 — Account / Login / Character Protocol Spec  *(DRAFT — verify against source)*

> **Provenance & trust:** this brief was produced by a research subagent reading
> `Packets.bb`, `AccountsServer.bb`, `PasswordHash.bb`, `ServerNet.bb`,
> `Actors.bb`, `RCEnet.bb`. It is a **map to implement from, not a verified
> contract.** Every byte-offset, the claimed `Accounts.dat` v1 magic header, the
> exact `ActorInstance` field order, and the multipart `C1/C3/S/Q/F` character
> payloads **must be re-checked against the cited `file:line` while writing each
> handler** — a single wrong offset corrupts every downstream field (the cardinal
> wire risk). Treat the PR numbers / test-file names as leads to confirm, not
> facts. When the implementation disagrees with this doc, the source wins; fix
> the doc.
>
> Verification method per handler: write the Rust decoder, then round-trip a
> real packet captured from `bin/ClientRS.exe` (or `Client.exe`) against the Rust
> server and confirm the reply matches what the Blitz server sends for the same
> input. Byte-exact on-disk compat is proven by loading an `Accounts.dat` the
> Blitz server wrote and verifying an existing password.

---

(Agent spec follows verbatim; the caveat above governs it.)

# RCCE2 Account/Login/Character Management — Spec

## Wire primitives
- Ints **big-endian** via `RCE_StrFromInt$(num, N)`, N ∈ {1,2,4}. `RCE_IntFromStr` zero-fills a 4-byte bank → a 2-byte field is unsigned 0..65535; signed needs the ≥32768 sign-extend (`RCE_SignedShortFromStr`).
- Floats IEEE-754 single (4 bytes). **NOTE: the agent claims wire floats are big-endian here; the client port's locked invariant says wire floats are LITTLE-endian. RESOLVE THIS against `RCE_StrFromFloat$`/`RCE_FloatFromStr#` (RCEnet.bb:108-123) before trusting any float field.**
- **Wire** strings: 1-byte length prefix + bytes. **File** strings: 4-byte length prefix (Blitz `WriteString`/`ReadString`); `ReadBoundedString$(F, Max)` caps allocation.
- Packet framing: `[type:u8][payload]`, one message per ENet packet (confirmed Cycle 1).

## Persistence — `Data/Server Data/Accounts.dat`
- Header (v1): magic `u32` `0x41434354` ("ACCT") + version `u8`=1. v0 legacy has no magic. **VERIFY the magic + that v0 detection works.**
- Per account: Username, Password, Email (all 4B-len strings, max 256), IsDM u8, IsBanned u8, Ignore$ (max 4096), CharCount u8 (clamp ≤10), then per character: ActorInstance + QuestLog + ActionBar.
- Atomic write: `SafeWriteOpen(final)→tmp`, write, `SafeWriteCommit(tmp, final, handle)` (close, verify non-empty, rename final→.bak, rename tmp→final). **Parity with Logging.bb SafeWrite* — match exactly.**
- `ActorInstance` (per character): ~80 fields — ActorID i16, Area/Name/Tag strings, TeamID i32, X/Y/Z f32 (ClampWorldCoord), Gender u8, XP i32, XPBarLevel u8, Level i16, FaceTex/Hair/Beard/BodyTex i16, Attributes[40]×(val i16+max i16), Resistances[20] i16, Inventory items, Script/DeathScript strings, Reputation i16 (signed), Gold i32, NumberOfSlaves u8, HomeFaction u8, FactionRatings[100] u8, ScriptGlobals[10] strings, KnownSpells[1000] i16, SpellLevels[1000] i16, MemorisedSpells[10] i16, then v1: LastPortalAreaName$/LastPortal i16/LastPortalTime i32, then recursive slave chain. **VERIFY exact order + widths against Actors.bb WriteActorInstance/ReadActorInstance (~324-594).**
- QuestLog: 500 entries × (name 4B-str max1024 + status 4B-str max1024). ActionBar: 36 slots × (4B-str max256).

## Password hash (PasswordHash.bb)
- v1 format: `$1$<16-char salt [A-Za-z0-9]>$<64-char lowercase sha256 hex>` (total 84 chars).
- Compute: client sends `MD5(password)` (32 hex) on wire; server stores `SHA256(salt_bytes + md5_ascii_bytes)` hex. **VERIFY whether the SHA input is the salt+md5-ascii or salt+md5-bytes — critical for on-disk compat.**
- Legacy: raw 32-char MD5, no `$1$`. Verify by direct compare.
- `VerifyPassword(stored, client_md5)`: v1 path recomputes + `ConstantTimeStrEq`; legacy path constant-time compares; **malformed/not-found path STILL runs a dummy SHA256 (timing-uniform) — security invariant, do not optimize away.**
- `UpgradePasswordIfLegacy`: on successful legacy login, rehash to v1 and atomically persist it in the authentication path. If that save fails, restore the verified legacy hash in memory so a later successful login can retry the migration.

## The 6 handlers (ServerNet.bb ~2365-3054) — replies
All inbound start: `[u8 ulen][username][u8 plen][md5password]`. Auth-before-disclosure: find account silently, verify password (pay dummy hash on miss), only then disclose ban/online.
- **P_CreateAccount (1):** + email (1B-len, Caesar-obfuscated via `Encrypt$`). Gate `AllowAccountCreation` (no reply if off). Validate charset+length, dup-check. Reply `"Y"`/`"N"`. **No throttle.**
- **P_VerifyAccount (2):** throttle (`LoginAttemptOk`, 5 fails/60s). Reply `"P"` (any auth fail), `"B"` (banned, post-verify), `"L"` (already on, post-verify), `"Y"+charlist`. Charlist per char: `[1B namelen][name][2B ActorID][1B Gender][1B Face][1B Hair][1B Beard][1B Body]`.
- **P_FetchCharacter (3):** + `[u8 slot]`. Throttle. Multipart reply: `"C1"`(stats: Gold i32, Rep i16, Level i16, XP i32, HomeFaction u8, 40×(attr val+max i16)), `"C3"`(inventory, fragmented >999B), `"S"`(spells, fragmented >1000B), `"Q"`(quests, fragmented >700B), `"F"`(questcount i16 + spellcount i16). Reply `"N"` on fail.
- **P_CreateCharacter (4):** + 47 reserved bytes + ActorID u16 + Gender/Face/Hair/Beard/Body u8 (clamp) + optional 40 attr-point bytes (if `AttributeAssignment>0`, validate sum) + name (rest of packet). Validate name (1-32, printable ASCII 32-126, banned-filter `Names Filter.txt`, global uniqueness). Validate ActorID in `ActorList`, StartArea exists. Reply `"Y"`/`"N"`/`"I"`(bad name). Atomic save.
- **P_DeleteCharacter (5):** + `[u8 slot]`. Throttle + password verify + **session gate `RequesterOwnsAccountSession(A, FromID)`**. Delete + shift slots down + null slot 9. Reply `"N"` on fail, else new charlist. Atomic save.
- **P_ChangePassword (6):** + new-password (1B-len md5). Verify current + session gate (no throttle). Store `HashPassword(new_md5)`. Reply `"Y"`/`"P"`. **Latent offset bug in the .bb (recomputes offset as `2+plen` dropping `ulen+1`) — masked because no live client sends this; implement the CORRECT offset `2+ulen+1+plen`.**

## Login state
- `Account`: User$, Pass$, Email$, IsDM, IsBanned, LoggedOn (-1 logged out / 0..9 slot), Character[10], QuestLog[10], ActionBar[10], Ignore$.
- `LoginAttempt{FromID, Failures, WindowStart}`: 5 fails/60s, reset on success. Used by handlers 2,3,4,5 (NOT 1,6).

## First implementation order (lowest risk → unblock)
1. Wire primitives + `ConstantTimeStrEq` (unit-test big-endian, 1B/4B string len).
2. PasswordHash v1 + legacy + verify + dummy-hash + upgrade (unit-test against a known `Accounts.dat` record).
3. `Accounts.dat` load (read existing file the Blitz server wrote → proves byte-compat) then save (round-trip).
4. P_VerifyAccount (the spine) → live login from a real client.
5. P_CreateAccount, P_FetchCharacter, P_CreateCharacter, P_DeleteCharacter, P_ChangePassword.
