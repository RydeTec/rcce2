<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**MySQL.bb**

The MySQL persistence layer for accounts, characters, and all per-character runtime state (attributes, inventory, factions, resistances, spells, quest log, action bar). All character load/save traffic and most account management funnels through this module's `My_*` functions; the SQL stream itself is a single global handle (`hSQL`) opened by `Server.bb` at boot via the bundled `BlitzSQL.decls` userlib.

This module is **MySQL-specific**. The companion module [`AccountsServer.bb`](accountsserver.md) holds the flat-file account path that the server falls back to when the SQL stream is offline. Both paths converge on the in-memory `Account` / `ActorInstance` Types from [`Actors.bb`](actors.md).

## Conceptual overview

### Where the SQL stream lives

| Symbol | Where | Role |
|---|---|---|
| `Const MySQL = False` | [`src/Server.bb:109`](../../src/Server.bb#L109) | Compile-time toggle. **This is the canonical gate** — callers wrap their MySQL paths in `If MySQL = True Then ...` and fall through to the flat-file path in [`AccountsServer.bb`](accountsserver.md) when `False`. The default build is `False`. |
| `Global MY_Reason` | [`src/Server.bb:111`](../../src/Server.bb#L111) | File-global error-reason slot set by `My_LoadAccount` on every failure path; read by auth handlers in [`ServerNet.bb`](servernet.md) to pick the wire-side error code. |
| `Global hSQL = 0` | [`src/Server.bb:110`](../../src/Server.bb#L110) | Single SQL stream handle for the entire server when MySQL is enabled. `0` = no stream (set only at declaration; `OpenSQLStream` runtime failure is not currently guarded). |
| `OpenSQLStream(host, port, user, pass, database, flag)` | [`src/Server.bb:128`](../../src/Server.bb#L128) | Boot-time connect. Stored in `hSQL`. |
| `SQLQuery / SQLFetchRow / SQLRowCount / FreeSQLQuery / FreeSQLRow / ReadSQLField / ReadSQLField$ / CloseSQLStream / SQLFieldCount` | [`src/userlibs/BlitzSQL.decls`](../../src/userlibs/BlitzSQL.decls) → `BlitzSQL.dll` | The MySQL-query userlib primitives. Imperative: every `SQLQuery` returns a result handle that **must** be `FreeSQLQuery`-ed; every `SQLFetchRow` result must be `FreeSQLRow`-ed. |
| `SQLStart / SQLMakeInstance / BBThreadComplete / BBFreeThread / BBMakeContainer / BBDestroyContainer / BBSetInt / BBSetStr / BBSetFloat / BBGetInt / BBGetStr / BBGetFloat` | [`src/userlibs/BBThread.decls`](../../src/userlibs/BBThread.decls) → `SQLDLL.dll` | The threading primitives used by `My_NewActorInstance` to issue a batched character INSERT off the main tick. See "Threading model" below. |

All `My_*` functions in this module use `hSQL` directly — there is no connection-pool or per-thread connection. The single-connection model means the BBThread machinery below is mandatory for any query that would otherwise block the main tick.

### Reason-code constants

`My_LoadAccount` returns `Null` on every failure path and sets the file-global `MY_Reason` (declared at [`Server.bb:111`](../../src/Server.bb#L111)) to one of:

| Constant | Value | Meaning |
|---|---|---|
| `MY_WRONGLOGIN` | 2 | Password mismatch (after `Force = False` gate). |
| `MY_BANNED` | 3 | Account is banned. |
| `MY_NOACCOUNT` | 4 | No row with that `username` (after `Force = False` gate). |
| `MY_ACCOUNTLOGGEDIN` | 5 | Username found, account already has an in-memory `Account` Type with `LoggedOn <> -1`. |

Defined in [`Server.bb:112-115`](../../src/Server.bb#L112). Callers (auth handlers in [`ServerNet.bb`](servernet.md)) check `MY_Reason` immediately after a `Null` return to pick the wire-side error code.

### SQL-injection defense: `My_Escape$`

Every concatenated string field in this module's queries goes through `My_Escape$()` (the **only** safe path). The helper backslash-escapes the MySQL-special characters inside a single-quoted string literal: NUL → `\0`, LF → `\n`, CR → `\r`, Ctrl-Z → `\Z`, `"` → `\"`, `'` → `\'`, `\` → `\\`. The dispatch is per-input-character (one branch per char via `If/ElseIf`); the inserted backslashes are appended to the output and never re-scanned, so double-escaping is structurally impossible regardless of branch order.

The reach of this helper is broad — the audit-comment block at the top of [`MySQL.bb`](../../src/Modules/MySQL.bb) enumerates every player- or script-controlled string that ends up in a query:

- `username` / `password` / `email` at `P_CreateAccount` / `P_VerifyAccount`
- `name` at `P_CreateCharacter`
- `area` / `tag` / `script` / `dscript` stored in `rc_actorinstance` (the actor's `Area$` / `Tag$` / `Script$` / `DeathScript$` fields, all settable from BVM)
- `ScriptGlobals$` settable by player scripts via `BVM_SETGLOBAL`
- Quest entry name + status settable via `BVM_ADDQUESTENTRY`
- Action-bar slot text settable via `BVM_SETACTIONBARSLOT`

**Numeric fields (`Int`, `Float`) skip `My_Escape$`** — Blitz's native value-to-string conversion only emits digits / sign / decimal point and cannot escape the surrounding quotes. This is intentional and documented; do not wrap numeric concatenations in `My_Escape$`.

> **New code rule.** Any new `SQLQuery(hSQL, "... '" + X$ + "' ...")` site that takes a string argument from a player, script, or external source **must** wrap that argument in `My_Escape$()`. Greppable invariant: every single-quoted `'"+...+"'` pattern in this module either wraps a numeric value or runs through `My_Escape$`. If a future change adds a third shape, it's a security regression.

### The schema — `rc_*` tables

| Table | Per-row data | Per-actor row count | Written by |
|---|---|---|---|
| `rc_accounts` | username, password (PBKDF2-hashed), email, isdm, isbanned, ignore | 1 per account | `My_AddAccount` (insert); `My_SaveAccount` UPDATE was disabled (see `My_SaveAccount` comment) |
| `rc_actorinstance` | actorid (template ID), area, name, tag, teamid, x, y, z, gender, xp, level, face, hair, beard, body, script, dscript, rep, gold, slaves, homefaction, isslave, slot (parent's id for slaves), xpbarlev | 1 per character (player or slave) | `My_NewActorInstance` (insert via threaded `SQLMakeInstance`); `My_SaveActorInstance` (UPDATE) |
| `rc_attributes` | aval, amax | 40 per character | UPDATE in save loop (`For i = 0 To 39`) |
| `rc_items` | iid (item ID; `65535` = empty slot), iheal, iamnt | `Slots_Inventory + 1` per character | UPDATE in save loop |
| `rc_itemvals` | val | `40 * (Slots_Inventory + 1)` per character | UPDATE in save loop |
| `rc_factionratings` | facrat | 100 per character | UPDATE in save loop (`For i = 0 To 99`) |
| `rc_resistances` | resval | 20 per character | UPDATE in save loop (`For i = 0 To 19`) |
| `rc_spells` | known, level | 1000 per character | UPDATE in save loop (`For i = 0 To 999`) |
| `rc_memspells` | mem (spell slot index) | 10 per character | UPDATE in save loop |
| `rc_scripts` | glob (script global string) | 10 per character | UPDATE in save loop |
| `rc_questlog` | qname, qstat | 500 per character | UPDATE in save loop (slaves skip — `Q = Null`) |
| `rc_actionbar` | slot ("I" / "S" / ""), dat (int for "I", string for "S") | 36 per character | UPDATE in save loop (slaves skip — `C = Null`) |

The row-count layout is **pre-allocated, not append-on-demand**: `My_NewActorInstance` and its DLL-side `SQLMakeInstance` insert all 40 attribute rows / 100 faction rows / etc. with sequential auto-increment IDs, then `My_LoadActorInstance` walks the contiguous range starting at the first row's `id`. The per-actor `Attribute_ID`, `Faction_ID`, `Resistance_ID`, `Spell_ID`, `Script_ID`, `Memorised_ID`, `My_ID` (inventory) base IDs cache that first-row-`id` so subsequent UPDATE queries can address slots by `(base + offset)` arithmetic rather than per-row PK lookups.

> **Schema invariant.** A row in `rc_attributes` / `rc_resistances` / `rc_factionratings` / `rc_spells` / `rc_memspells` / `rc_scripts` is keyed by its auto-increment `id`, but its **logical index** within the character's slot set is `id - <base>`. The base is loaded from row #0 in `My_LoadActorInstance` (`If i = 0 Then A\Attribute_ID = ReadSQLField(...)`). A row hole (gap in the auto-increment sequence) would corrupt every subsequent slot. The DLL-side `SQLMakeInstance` ensures atomic batched inserts; do not handcraft INSERTs into these tables outside of `My_NewActorInstance`.

### Threading model — `BBThread` + `SQLMakeInstance`

The single-`hSQL`-connection model means a synchronous `INSERT` of an entire character on first save would stall the main tick for tens of milliseconds. `My_NewActorInstance` solves this by punting the work to a SQLDLL-side worker thread:

```
My_NewActorInstance(A, ...)
    │
    ├─ pack A's scalar fields into a `BBMakeContainer` blob (byc)
    ├─ thread_handle = SQLMakeInstance(byc)        ← non-blocking, returns handle
    ├─ T.BBThread = New BBThread; T\Hand = thread_handle; T\Cont = byc; ...
    └─ enqueue T into `For Each BBThread` collection
                       │
                       ▼ (later, on the main tick)
                       My_UpdateThreads()
                       │
                       └─ For T = Each BBThread
                              If BBThreadComplete(T\Hand)
                                  read back the per-row `id` columns the DLL filled in
                                  → A\My_ID, A\Attribute_ID, A\Inventory\My_ID, ...
                                  RCE_Send P_CreateCharacter "Y" to T\MsgID  (PC only)
                                  BBFreeThread(T\Hand); Delete T
```

The `BBThread` Type carries the DLL handle, the container, the actor's `Handle()`, the originating client's `MsgID` (for the `P_CreateCharacter "Y"` ack), and a handful of bookkeeping ints. The `For Each BBThread` walk in `My_UpdateThreads()` runs every tick from [`GameServer.bb`](gameserver.md).

**Thread-count cap:** `My_NewActorInstance` increments `BBThreadCount` and `RuntimeError`s at 17 ("Thread Limit Reached"). The DLL holds at most 16 concurrent worker threads. Exceeding it would be a real bug (e.g. login storm) — the `RuntimeError` is intentional fail-loud here, not a wire-driven soft-fail candidate.

### Character lifecycle

```
P_VerifyAccount          P_CreateCharacter        P_CreateCharacter          per-tick
   │                          (new)                   (existing slot)            │
   ▼                            │                            │                   │
My_LoadAccount(User, Pass)      ▼                            ▼          UpdateActorInstances
   │                     My_NewActorInstance ── thread ─►  My_LoadActorInstance      │
   │                                                          │                      ▼
   ├─ SELECT rc_accounts                                     loads rc_* tables    My_SaveActor (periodic)
   ├─ For Each Account (in-memory) — re-bind if found        + walks slaves         │
   │  (LoggedOn=-1 sentinel)                                 (recursive)           UPDATE all rc_* tables
   ├─ SELECT rc_actorinstance WHERE account_id=N AND isslave=0
   └─ For each row: My_LoadActorInstance into A\Character[i] (slots 0..9)
                                                                          P_LogOut / disconnect
                                                                                  │
                                                                                  ▼
                                                                          (auth handlers in
                                                                           ServerNet.bb call
                                                                           My_SaveAccount(A, True))

DM /deletechar
   │
   ▼
My_DeleteCharacter(A, slot)
   ├─ DELETE rc_actorinstance / rc_actionbar / rc_attributes / rc_factionratings
   ├─ DELETE rc_memspells / rc_questlog / rc_scripts / rc_spells / rc_resistances
   ├─ DELETE rc_items + rc_itemvals (per-item walk)
   └─ FreeActorInstance(A\Character[slot])
```

### Slave chain integration

Pets / slaves persist as `rc_actorinstance` rows with `isslave = 1` and `slot = <leader_id>`. Critical sequence:

- **At save** ([`My_SaveActorInstance`](#my_saveactorinstance) end): walks `A\FirstSlave → NextSlave` (the [`Actors.bb`](actors.md) chain) and recursively saves each slave with `IsSlave = True` and `Parent = A\My_ID`. The flat-file `SaveActor` path uses the same shape.
- **At load** ([`My_LoadActorInstance`](#my_loadactorinstance) end): runs `SELECT id FROM rc_actorinstance WHERE isslave='1' AND slot='<leader_id>'`, iterates, loads each slave via recursive `My_LoadActorInstance(..., Null, Null, AccountID)` (Null `Q` / `C` skips quest log + action bar — slaves don't have those), then calls `SlaveLink(leader, slave)` which maintains the `FirstSlave` chain and increments `NumberOfSlaves`.

The `NumberOfSlaves` Field is **reset to 0** at the start of `My_LoadActorInstance` for the leader (audit comment at [`MySQL.bb:645-652`](../../src/Modules/MySQL.bb#L645)). Each successful `SlaveLink` re-increments it. This means the stored `slaves` column is informational only — the canonical count is rebuilt from the actual rows that loaded successfully.

> **Source caveat — `My_LoadActorInstance` does NOT return `Null` on missing template.** The function checks `If ActorList(ActorID) = Null Then A.ActorInstance = New ActorInstance` and continues, returning a freshly-allocated `ActorInstance` with `A\Actor = Null` and only the raw SQL fields populated. The `If Slave <> Null` guard at [`MySQL.bb:923`](../../src/Modules/MySQL.bb#L923) **never fires for this path** — `SlaveLink` then runs on a template-less slave whose downstream rendering will fail on actor-mesh deref. This is a known silent data-integrity issue, not yet closed. Track as a follow-up; until then, garbage slave rows survive past load and into the world.

### Soft-fail / bounds discipline at load

`My_LoadActorInstance` is one of the load paths covered by the wire-supplied-bounds sweep (CLAUDE.md → "Bounds checks before array index"):

- **`A\HomeFaction`** clamped to `[0..99]` (`FactionNames$` / `FactionDefaultRatings` are `Dim`ed `(99)`; `FactionRatings` is `Field[99]`). A corrupt SQL row otherwise drives OOB reads on downstream consumers.
- **`A\MemorisedSpells[i]`** clamped to `[0..999]` with sentinel `5000` ("no spell"). The client deref is `KnownSpells[A\MemorisedSpells[i]]`; an OOB index there crashes the client.
- **`ItemList(ID) = Null` check** before `CreateItemInstance` — a missing template (item removed since save) drops the slot to `Null` with a `WriteLog` warning instead of crashing. The slot reset preserves the rest of the inventory.
- **`A\XPBarLevel` column-name fix** — historical typo `xbbarlev` silently returned 0 on every load (XP bar reset to empty on relog). Audit comment at the `XPBarLevel` read documents the catch.

These are inline in the load loops; the comment block at each clamp explains the threat.

### Connection to BVM privilege gating

[`ScriptingCommands.bb`](scriptingcommands.md)'s `BVM_MYSQL*` family — `BVM_MYSQLQUERY`, `BVM_MYSQLNUMROWS`, `BVM_MYSQLFETCHROW`, `BVM_MYSQLGETVAR`, `BVM_MYSQLFREEQUERY`, `BVM_MYSQLFREEROW` — exposes arbitrary SQL to script context. CLAUDE.md's privilege-gate category #2 (handle-walking helpers for host resources) covers these: once the entry-point (`BVM_MYSQLQUERY`) is gated, the row/free helpers must be gated too. Otherwise a non-priv script can guess an integer handle and walk privileged result rows the server itself is mid-iteration over. PR #234 closed this gap.

## Conventions for new code touching this module

- **Every string concatenation into a query goes through `My_Escape$`.** Numeric concatenations skip it. See the audit-comment block at the top of `MySQL.bb` for the full reach.
- **`SQLQuery / SQLFetchRow` results must be freed.** `FreeSQLQuery(...)` and `FreeSQLRow(...)` are mandatory; leaked rows accumulate in DLL memory.
- **Slot-base IDs are sacred.** Don't INSERT into `rc_attributes` / `rc_resistances` / `rc_factionratings` / `rc_spells` / `rc_memspells` / `rc_scripts` outside `My_NewActorInstance` — handcrafted INSERTs break the contiguous-id assumption that `My_LoadActorInstance` relies on.
- **New character-state fields** need: a column on `rc_actorinstance` (or a new per-character table with a base-id pattern), a `BBSetX` call in `My_NewActorInstance`, an UPDATE in `My_SaveActorInstance`, and a `ReadSQLField` in `My_LoadActorInstance`. Missing any one of these silently drops the field on relog.
- **Bound + clamp anything reading into an array index.** `ReadSQLField` returns raw column data with no range check; downstream `Field[N]` access is not bounds-checked at runtime.
- **`BBThreadCount = 17` is fail-loud `RuntimeError`** — not a soft-fail candidate. The DLL thread cap is a real ceiling, not a wire-corruption defense.
- **Use `If MySQL = True` as the SQL/flat-file gate.** The canonical pattern in [`ServerNet.bb`](servernet.md) (see ~line 2341, 2374, 2881, 2945) is `If MySQL = True Then <SQL path> Else <flat-file path>`. The `Const MySQL = False` at [`Server.bb:109`](../../src/Server.bb#L109) makes the flat-file path the default build. Do NOT gate on `If hSQL = 0` — the only existing site at [`MySQL.bb:182`](../../src/Modules/MySQL.bb#L182) is a `WriteLog` warning that does **not** fall back; the call below it would still hit `SQLQuery(hSQL=0, ...)`.

## Related modules

- [`AccountsServer.bb`](accountsserver.md) — the flat-file account path used when `hSQL = 0`. Identical `Account` Type contract; different persistence backend.
- [`Actors.bb`](actors.md) — owns `ActorInstance`, `Attributes`, `Inventory`, `Questlog`, `ActionBarData`, the `FirstSlave` chain, and `CreateActorInstance` / `FreeActorInstance` (called from `My_DeleteCharacter` for in-memory cleanup).
- [`Items.bb`](items.md) — `ItemList(...)` template registry; `CreateItemInstance` constructor; `Slots_Inventory` constant indirectly drives the `rc_items` row count.
- [`ServerNet.bb`](servernet.md) — packet handlers that call this module: `P_VerifyAccount` → `My_LoadAccount`; `P_CreateAccount` → `My_AddAccount`; `P_CreateCharacter` → `My_NewActorInstance` / `My_LoadActorInstance`; `P_LogOut` / disconnect → `My_SaveAccount`.
- [`ScriptingCommands.bb`](scriptingcommands.md) — `BVM_MYSQL*` family; privilege-gate category #2 (handle-walking helpers) applies.
- [`GameServer.bb`](gameserver.md) — per-tick caller of `My_UpdateThreads()`.
- [`Logging.bb`](logging.md) — `WriteLog(MainLog, ...)` used throughout for SQL error / threat events.

## See also

- CLAUDE.md → "Bounds checks before array index" — applies to every `ReadSQLField` that feeds an array index.
- CLAUDE.md → "Privilege gating in BVM commands" (category #2) — `BVM_MYSQL*` handle-walking discipline.
- CLAUDE.md → "Atomic writes" — does **not** apply to MySQL (the database is its own atomic unit); applies to the flat-file fallback in `AccountsServer.bb`.

* * *

## Reference

The legacy function-by-function reference has not been generated. The conceptual overview above is the primary reference; consult the source at [`src/Modules/MySQL.bb`](../../src/Modules/MySQL.bb) for full signatures.

### Functions

- <a id="my_escape"></a>**`My_Escape$(s$)`** — backslash-escape every MySQL-special character in `s$` for safe inclusion inside a single-quoted SQL literal. Backslash escaped first. **Every player- or script-controlled string in any query in this module MUST go through this.**
- <a id="my_updatethreads"></a>**`My_UpdateThreads()`** — per-tick caller polled from [`GameServer.bb`](gameserver.md). Walks `For Each BBThread`, picks up completed threads via `BBThreadComplete(T\Hand)`, reads back the per-row `id` columns the DLL filled in, sends `P_CreateCharacter "Y"` to the originating client (PC only), frees the thread.
- <a id="my_addaccount"></a>**`My_AddAccount(User$, Pass$, Email$)`** — INSERT into `rc_accounts`. **`End`s the server on failure** (legacy hard-fail; predates the soft-fail sweep).
- <a id="my_accountexists"></a>**`My_AccountExists(User$)`** — boolean SELECT for a username. Used by `P_CreateAccount` to reject duplicates.
- <a id="my_saveaccount"></a>**`My_SaveAccount(A.Account, SaveInstance)`** — the account-row UPDATE is **disabled** (commented out — see source comment about conflicts with DM/Banned flags set out-of-band). When `SaveInstance = True`, walks `A\Character[0..9]` and per-slot calls `My_SaveActorInstance`.
- <a id="my_loadaccount"></a>**`My_LoadAccount.Account(User$, Pass$, Force)`** — primary auth path. Sets `MY_Reason` to one of the `MY_*` codes on failure; returns `Null` on any failure. On success: reuses existing in-memory `Account` if found (via `For Each Account`), otherwise allocates a new one; loads all non-slave `rc_actorinstance` rows into `A\Character[0..9]` via recursive `My_LoadActorInstance`. `Force = True` bypasses password + ban gates (used by DM tooling).
- <a id="my_deletecharacter"></a>**`My_DeleteCharacter(A.Account, Number)`** — DELETE from 9 tables + per-item `rc_itemvals` walk + `FreeActorInstance` of the in-memory copy. Idempotent — guarded by `If A\Character[i] <> Null`.
- <a id="my_createaccountswindow"></a>**`My_CreateAccountsWindow.AccountsWindow()`** — alternate accounts-list GUI window (no DM/Ban/Delete buttons). Used by tooling that just needs the list.
- <a id="my_actorexists"></a>**`My_ActorExists(ActorName$)`** — boolean SELECT for a character name (case-insensitive `LIKE`). Used by `P_CreateCharacter` to reject duplicates.
- <a id="my_saveactorinstance"></a>**`My_SaveActorInstance(A.ActorInstance, Q.QuestLog, C.ActionbarData, IsSlave, AccountID, Parent)`** — full per-character UPDATE: 1 row in `rc_actorinstance` + 40 attributes + `Slots_Inventory + 1` items + 40 item-attrs per item + 100 faction ratings + 20 resistances + 1000 spells + 10 mem-spells + 10 script-globals + 500 quest entries (PC only) + 36 action-bar slots (PC only) + recursive walk of `A\FirstSlave` chain. `Parent` is the leader's `My_ID` for slave rows; `0` for PCs.
- <a id="my_newactorinstance"></a>**`My_NewActorInstance(A.ActorInstance, Q.Questlog, C.ActionbarData, IsSlave, AccountID, MsgID = 0)`** — threaded INSERT via SQLDLL. Packs A's scalar fields into a `BBMakeContainer` blob, calls `SQLMakeInstance` (returns thread handle), enqueues a `BBThread` for `My_UpdateThreads` to pick up later. `MsgID` is the originating client's PeerToHost ID for the `P_CreateCharacter "Y"` ack.
- <a id="my_loadactorinstance"></a>**`My_LoadActorInstance.ActorInstance(ActID, Q.Questlog, C.ActionBarData, AccountID)`** — single-row load. SELECTs from `rc_actorinstance`, allocates `ActorInstance` + `Attributes` + `Inventory` (or reuses an existing one if the actor's template `actorid` exists in `ActorList`), then walks the per-character tables in contiguous-`id` order populating slot arrays. Applies bounds clamps on `HomeFaction` and `MemorisedSpells[]`, Null-handles missing item templates, recursively loads slaves via `SlaveLink`. **Always returns a non-`Null` ActorInstance** — even when the template is missing it allocates a fresh empty instance (see "Source caveat" under "Slave chain integration"). Callers that need to detect missing templates must check `A\Actor <> Null` themselves.
