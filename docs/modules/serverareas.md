<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**ServerAreas.bb**

The server's world-zone substrate. Defines the three area Types (`Area`, `AreaInstance`, `ServerWater`), the file-backed area load/save pipeline (`ServerLoadArea` / `ServerSaveArea`), the per-area `FirstWater` and per-instance `FirstInZone` chain heads that high-traffic broadcast paths walk, and the weather-roll loop. Everything the server needs to know about a zone in memory lives here; the per-tick consumption of that data lives in [`GameServer.bb`](gameserver.md).

[`Actors.bb`](actors.md) holds `Actor` / `ActorInstance` (the entities that populate `AreaInstance`); [`GameServer.bb`](gameserver.md) consumes `Area` / `AreaInstance` / `ServerWater` per tick (weather emission, water damage check, spawn machinery); [`ServerNet.bb`](servernet.md) reads `AreaInstance` for `/warp` / `/warpother` routing and zone-bound broadcast.

## Conceptual overview

### The three Types — template, instance, water

| Type | Role | Lifetime | Where stored |
|---|---|---|---|
| **`Area`** | Zone **template**. One per named zone. Holds 150 trigger volumes, 2000 waypoints, 100 portals, 1000 spawn slots, 5 weather-chance buckets, gravity, PvP flag, `Outdoors` flag, `WeatherLink$` (name of linked-weather zone) + `WeatherLinkArea` (resolved pointer), entry/exit scripts, plus an `Instances.AreaInstance[99]` array of up to 100 live instances. Mostly static — loaded from `Data\Server Data\Areas\<Name>.dat`. | Server boot → shutdown (or `ServerUnloadArea`). | Global `For Each Area` collection. Find by name via [`FindArea`](#findarea). |
| **`AreaInstance`** | A **live copy** of an `Area` (instance #0 = default zone; #1..99 = parallel instances). Holds the runtime state that varies per instance: current weather, weather countdown, per-spawn `Spawned[]` / `SpawnLast[]` counters, and the head of the `FirstInZone` actor chain. | Created by `ServerCreateAreaInstance`; destroyed with the owning `Area`. | `Area\Instances[0..99]`. The default instance #0 is always created by `ServerCreateArea` / `ServerLoadArea`. |
| **`ServerWater`** | A volumetric water region inside an `Area` — position, width/depth box, periodic damage value + damage type. Multiple per area. The per-tick underwater-damage check in [`GameServer.bb`](gameserver.md) walks the area's chain to apply damage to actors standing inside the volume. | Created in `ServerLoadArea` from the area `.dat`; freed in `ServerUnloadArea`. | Global `For Each ServerWater` collection **and** per-area `Area\FirstWater → NextWater` chain (both — see below). |

### Chain heads on this module's Types

`ServerAreas.bb` hosts **two** chain heads consumed by per-tick broadcast paths. Both replace `For Each ... If <area filter>` walks with `O(N-in-area)` walks.

| Chain head | Stored on | Link Field | Walked by |
|---|---|---|---|
| `AreaInstance\FirstInZone` | `AreaInstance` | `ActorInstance\NextInZone` (defined in [`Actors.bb`](actors.md)) | The local `UpdateWeather` loop above (weather emission); [`GameServer.bb`](gameserver.md)'s per-tick zone-bound broadcast paths; any "tell every actor in this zone" code. Maintained by `SetArea` (in [`GameServer.bb`](gameserver.md), the engine-tick rebinder) and the `CreateActorInstance` / `FreeActorInstance` helpers in [`Actors.bb`](actors.md). |
| `Area\FirstWater` | `Area` | `ServerWater\NextWater` | [`GameServer.bb`](gameserver.md)'s per-tick underwater-damage check (grep `AInstance\Area\FirstWater`). Maintained by `ServerLoadArea` (head-insert at load) and `ServerUnloadArea` (full chain teardown). |

The global `For Each ServerWater` collection still owns every record (creation is `New ServerWater`; teardown is `Delete W`); the per-area chain is an O(1)-lookup index into the subset belonging to one area. Per-tick code paths walk only the chain — no `If W\Area = Self` filter — which is the latency win.

### The `Area` template field layout

The `Area` Type is wide on purpose — it's the in-memory mirror of the on-disk `.dat` format. The four large array families that dominate it:

| Family | Slots | Per-slot Fields | Notes |
|---|---|---|---|
| Triggers | 150 (`[0..149]`) | `TriggerX/Y/Z`, `TriggerSize`, `TriggerScript$`, `TriggerMethod$` | Sphere volumes that fire a BVM script when entered. Empty slot = zero-radius. |
| Waypoints | 2000 (`[0..1999]`) | `WaypointX/Y/Z`, `PrevWaypoint`, `NextWaypointA/B`, `WaypointPause` | Graph of patrol nodes. `*A`/`*B` Next gives forked patrol routes. Sentinel `2005` = no connection (≥ 2000, out of range). |
| Portals | 100 (`[0..99]`) | `PortalName$`, `PortalLinkArea$`, `PortalLinkName$`, `PortalX/Y/Z/Size/Yaw` | Cross-area teleport points. Player walks into volume → warped to `<PortalLinkArea, PortalLinkName>`. |
| Spawn points | 1000 (`[0..999]`) | `SpawnActor`, `SpawnWaypoint`, `SpawnSize`, `SpawnScript$`, `SpawnActorScript$`, `SpawnDeathScript$`, `SpawnFrequency`, `SpawnMax`, `SpawnRange` | NPC spawn definitions. `SpawnActor` = `Actor\ID` of the template; `SpawnMax` caps simultaneous spawns; `SpawnFrequency` (seconds) gates respawn cadence. Per-instance counters (`AreaInstance\Spawned[]` / `SpawnLast[]`) track live state. |

The `Field[N]` declarations are Blitz3D-inclusive — `Field WaypointX#[1999]` allocates **2000** slots indexed `0..1999`. The standard `For i = 0 To 1999` iteration matches; `0 To 1998` skips the last slot (CLAUDE.md → "Gotchas" / "Blitz3D array semantics").

### Lifecycle: load → in-memory mutate → save → unload

The canonical area lifecycle for a running server:

1. **Boot**: For each `.dat` in `Data\Server Data\Areas\`, [`ServerLoadArea`](#serverloadarea) parses the file into a `New Area` (which auto-registers in the global `For Each Area` collection), then calls `ServerCreateAreaInstance(A, 0)` to make instance #0. Returns the `Area`.
2. **Runtime mutation**: Editor tools (GUE, RC Architect) mutate `Area` fields directly via BVM calls or admin commands. Per-instance state (`CurrentWeather`, `Spawned[]`) ticks in [`GameServer.bb`](gameserver.md).
3. **Save**: Admin `/saveareas` (or a periodic checkpoint) calls [`ServerSaveArea`](#serversavearea) per area. **Atomic via `SafeWriteOpen` / `SafeWriteCommit`** — writes `<area>.dat.tmp`, then on success demotes the existing `<area>.dat` to `<area>.dat.bak` and promotes the temp. A mid-write crash never leaves a truncated area file (CLAUDE.md → "Atomic writes").
4. **Unload**: [`ServerUnloadArea`](#serverunloadarea) walks the per-area water chain (`A\FirstWater → NextWater`), `Delete`s each `ServerWater`, then `Delete`s the `Area`. The `Instances.AreaInstance[99]` array is left to GC at process exit (single instances are not explicitly Delete'd here — known cosmetic leak; benign because unload is rare and Blitz3D process-exit collects them).

### Wire-supplied bounds discipline (CLAUDE.md ↔ this module)

`ServerLoadArea` is a primary participant in the data-loader hardening sweep (CLAUDE.md → "Bounds checks before array index"):

- **All `ReadString$` calls go through `ReadBoundedString$(F, cap)`** — 1024 caps for scripts, 256 caps for short identifiers. Prevents an attacker who tampered with a `.dat` file from triggering a multi-gigabyte allocation via a wild `ReadInt` length prefix. Same shape as the PR #149 sweep across other data loaders.
- **`W\DamageType` clamped to `[0..19]`** — the per-tick water-damage path indexes the actor's `AI\Resistances[DamageType]` (a `Field[19]` on `ActorInstance`, defined in [`Actors.bb`](actors.md), **not** on `Area`); a tampered `.dat` with a negative or out-of-range type would read out-of-bounds at the actor side (Blitz3D `Field[]` is **not** bounds-checked at access; out-of-range reads return adjacent memory). Source-of-truth comment on the clamp lives in [`ServerLoadArea`](#serverloadarea). (Note: the inline source comment at `ServerAreas.bb:267-268` refers to `A\Resistances` — `A` there means the actor, not the `Area` template; the shorthand is misleading.)

If you add a new `Field[N]` array to `Area` and a new corresponding `ReadX` call to `ServerLoadArea`, follow these two patterns or the wire-driven RuntimeError sweep gains a new gap.

### Weather

`UpdateWeather` (called from [`GameServer.bb`](gameserver.md)'s per-tick loop) decrements `CurrentWeatherTime`; when it hits 0, picks a new weather code from `WeatherChance[0..4]` probabilities (or copies from a linked area's `Instances[0]`), then broadcasts `P_WeatherChange` to every actor in the zone by walking `FirstInZone`. This is the only weather path; the client just renders the most-recently-received code.

The linked-area mechanism (`WeatherLink$` / `WeatherLinkArea`) is a content-authoring convenience for keeping adjacent outdoor zones in weather sync — the linked area's `Instances[0]` is the authoritative source.

## Conventions for new code touching this module

- **Always atomic-write area files via `SafeWriteOpen` / `SafeWriteCommit`** — never `WriteFile` directly to a production `.dat`. The canonical shape is `ServerSaveArea` (grep `SafeWriteOpen.*TempPath` in `ServerAreas.bb`). Areas hold all the irreplaceable spawn/waypoint/script content; a 0-byte save was historically meaningful data loss.
- **Maintain both the global `Each ServerWater` collection AND the per-area chain** at allocation and free time — head-insert into `A\FirstWater` immediately after `W\Area = A`. Walking only the chain in per-tick code is the latency win; touching only the global Each in load/save is the simpler shape.
- **Bound every `ReadString$` from a `.dat` file** via `ReadBoundedString$(F, cap)` — see the PR #149 sweep and the `ServerLoadArea` audit comment block. Wire-supplied length-prefixed strings are the canonical OOM vector.
- **Clamp every numeric `Read*` from a `.dat` file that feeds an array index** to the destination's valid range. `Field[N]` is `0..N` inclusive; reads outside that range return adjacent memory without erroring.
- **`FindArea` returns `Null` when the name doesn't match** — callers must check before deref (CLAUDE.md → "Handle-lookup Null discipline" applies to type-pool lookups too).
- **`Area\Instances[ID]` may be `Null` for non-default IDs** — the default instance #0 is always created by `ServerCreateArea` / `ServerLoadArea`, but instances `1..99` exist only when explicitly created. Check `<> Null` before `\` deref.
- **Mid-warp `AI\ServerArea` race** — actors transitioning between areas briefly have a stale `AreaInstance` handle. The standard recovery for broadcast loops that need `Object.AreaInstance(AI\ServerArea)` to succeed is to skip the per-tick broadcast (the next tick after `SetArea` settles will reach the actor again). PRs [#154](https://github.com/RydeTec/rcce2/pull/154) / [#155](https://github.com/RydeTec/rcce2/pull/155) / [#176](https://github.com/RydeTec/rcce2/pull/176) / [#182](https://github.com/RydeTec/rcce2/pull/182)–[#188](https://github.com/RydeTec/rcce2/pull/188) cover the sweep.

## Related modules

- [`Actors.bb`](actors.md) — owns `ActorInstance` (the entity that populates `AreaInstance`) and the `NextInZone` Field that links into this module's `FirstInZone` chain head.
- [`GameServer.bb`](gameserver.md) — per-tick consumer **and** owner of the `SetArea(AI, NewArea, Instance, ...)` rebinder that maintains the `FirstInZone` chain on actor transitions. Calls `UpdateWeather`, walks `Area\FirstWater` for water damage, ticks the spawn machinery from `Area\SpawnFrequency` / `AreaInstance\SpawnLast`.
- [`ServerNet.bb`](servernet.md) — packet handlers that read `AreaInstance` for `/warp` / `/warpother` / `/whozone` routing and zone-bound chat broadcast.
- [`ScriptingCommands.bb`](scriptingcommands.md) — BVM functions that read or mutate `Area` fields from script context (waypoint queries, portal info, spawn manipulation).
- [`Logging.bb`](logging.md) — provides the `SafeWriteOpen` / `SafeWriteCommit` helpers that `ServerSaveArea` uses for atomic file replacement.

## See also

- [`P_WeatherChange`](../protocol/packets/README.md) — the wire format `UpdateWeather` emits (one byte of weather code per area Handle). No dedicated detail page yet — see the packet README for the catalog.
- CLAUDE.md → "Atomic writes" — the canonical `SafeWriteOpen` / `SafeWriteCommit` pattern.
- CLAUDE.md → "Bounds checks before array index" — the wire-supplied / file-supplied index discipline.
- CLAUDE.md → "Handle-lookup Null discipline" — applies to `Object.AreaInstance(AI\ServerArea)` checks.
- [`reference_safewrite_migration_template.md`](../../../../.claude/projects/C--Users-dyanr-Desktop-rcce2/memory/reference_safewrite_migration_template.md) (agent memory) — the per-site migration checklist for new save paths.

* * *

## Reference

The legacy function-by-function reference for this module has not been generated. The conceptual overview above is the primary reference; consult the source at [`src/Modules/ServerAreas.bb`](../../src/Modules/ServerAreas.bb) for full signatures.

### Functions

- <a id="updateweather"></a>**`UpdateWeather(A.AreaInstance)`** — ticks `CurrentWeatherTime`; on rollover picks new weather (own roll or linked-area copy) and broadcasts `P_WeatherChange` to every actor in the zone via `FirstInZone` walk.
- <a id="servercreatearea"></a>**`ServerCreateArea.Area()`** — allocates a blank `Area` with waypoint sentinels (`2005`) and default `SpawnFrequency = 10` / `Gravity = 300`, then makes the default `AreaInstance` #0. Returns the new `Area`.
- <a id="servercreateareainstance"></a>**`ServerCreateAreaInstance.AreaInstance(Ar.Area, ID)`** — adds a new instance under `Ar\Instances[ID]`, seeds `SpawnLast[]` with the current `MilliSecs()`. Returns the instance.
- <a id="findarea"></a>**`FindArea.Area(Name$)`** — case-insensitive linear lookup over `For Each Area`. Returns `Null` if no match.
- <a id="serverunloadarea"></a>**`ServerUnloadArea(A.Area)`** — walks `A\FirstWater → NextWater`, `Delete`s each `ServerWater`, clears `A\FirstWater`, then `Delete A`. The instance array is left for process-exit GC (cosmetic leak; benign).
- <a id="serverloadarea"></a>**`ServerLoadArea.Area(Name$)`** — opens `Data\Server Data\Areas\<Name>.dat`, parses the full `Area` layout (5 weather chances, all 150 triggers, 2000 waypoints, 100 portals, 1000 spawn points, N `ServerWater` records), creates instance #0, returns the `Area`. All `ReadString$` calls go through `ReadBoundedString$` for bounds safety; `ServerWater\DamageType` is clamped to `[0..19]`.
- <a id="serversavearea"></a>**`ServerSaveArea(A.Area)`** — writes the area to `<name>.dat` via `SafeWriteOpen` / `SafeWriteCommit` atomic-rename. Walks `A\FirstWater` twice (count, then write) instead of two global `Each ServerWater` filtered loops. Returns `True` on success; `False` on `WriteFile` failure (returned early before any write) or `SafeWriteCommit` failure (temp written but not promoted — see `Logging.bb` for the rollback path).
- <a id="servercopyarea"></a>**`ServerCopyArea.Area(A.Area)`** — deep-copies an `Area` template (all arrays, all scalars) into a new `Area` named `"Copied zone"`. Used by editor tools for zone duplication. Does **not** copy the per-area water chain or any `AreaInstance` runtime state.
