<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Radar.bb**

Client-side minimap renderer. Builds a top-down screenshot of the current zone at boot, overlays a fog-of-war texture that's revealed as the player moves, and persists discovered fog to disk per zone (`Data\Areas\Radar\<AreaName>.rdr`).

The module is self-contained: no engine dependencies beyond `GY_Cam` (the 2D HUD camera), `Cam` (the 3D world camera), and `For Each Scenery` for fullbright-on-snapshot. Designed to be droppable into any project that has those three handles.

## Conceptual overview

### The four texture stack

```
Z-order  Entity                Texture          Purpose
-3005    Radar_Ent1            Radar_Tex1       Top-down zone snapshot (loaded once per zone)
-3007    Radar_Ent2            Radar_Tex2       Fog-of-war overlay (mutates per-tick on movement)
-3008    Radar_BorderEnt       Radar_BorderTex  Outer border (default-generated or custom .png)
-3009    Radar_PlayerEnt       Radar_PlayerTex  Player-position marker (default-generated or custom .png)
```

The three overlay entities are parented to `Radar_Ent1` (see `EntityParent` calls at [`Radar.bb:146-148`](../../src/Modules/Radar.bb#L146)); `Radar_Ent1` itself is parented to `GY_Cam` (the 2D HUD camera) via the `Radar_CreateQuadr(GY_Cam)` allocation at [`Radar.bb:118`](../../src/Modules/Radar.bb#L118). Hide/show on `Radar_Ent1` propagates to all three children. `EntityOrder` Z values are chosen so the player marker is always on top and the snapshot is bottom-most.

`Radar_TexSize = 512` is the per-texture pixel size — all four textures are 512×512 regardless of zone size. The snapshot is downscaled to fit; the fog/border/player overlays are computed at this resolution directly.

### Zone-load flow (`Load_Radar`)

1. Hide the 3D world cameras (`Cam`, `GY_Cam`) so they don't render into the snapshot.
2. Strip path + extension from `AreaName$`.
3. Spawn a temporary camera straight down at the world origin, and four pivot points (N/S/E/W) at `Y = 5000`.
4. Loop-translate everything upward until `LinePick` from each pivot reaches the floor without hitting scenery — gives the camera the height needed to see the whole zone in one shot.
5. Record `Radar_WorldSize` as `EntityY * 1.7` (the camera height × an empirical scaling factor).
6. Set every `Scenery` entity to `EntityFX 1+8` (fullbright + no fog) for the snapshot, render once, copy backbuffer → `Radar_Tex1`, restore `EntityFX 0`. **The screen-sized scratch image is freed inline** (PR audit comment notes the ~8 MB-per-zone leak prior to the fix; with 20 zones that's 160 MB of orphaned image data in Blitz3D's 2 GB address space).
7. Load `<area>.rdr` if it exists (preserves fog progression from a previous play session); otherwise create a fresh fog texture via `Create_Radar_Fog`.
8. Build the four quad meshes, apply textures, parent everything to `Radar_Ent1`, position+scale into the HUD.

This is per-zone, called from [`ClientNet.bb`](clientnet.md)'s zone-load packet handler ([`ClientNet.bb:1738`](../../src/Modules/ClientNet.bb#L1738)). The matching `Save_Radar_Fog` call lives in the zone-leave path ([`ClientNet.bb:1682`](../../src/Modules/ClientNet.bb#L1682)). `Unload_Radar` is invoked at client-shutdown time from [`ClientLoaders.bb:372`](../../src/Modules/ClientLoaders.bb#L372). The leak fix is critical because zone changes happen every gameplay session — pre-fix accumulation hit memory pressure in long sessions.

### Per-tick reveal (`UpdateRadar`)

Called every frame with the player's actor `ent` and an `areashow` reveal-radius (default 50). Skipped when:

- The player hasn't moved (`EntityX/Z` unchanged since last call).
- The fog-update timer (`Radar_Update_Time = 600` ms) hasn't elapsed since the last fog mutation.

When it does run:

1. Map world coordinates to texture coordinates via `(-EntityX * Radar_TexSize) / Radar_WorldSize` (Z axis uses positive, X negated because the snapshot is taken from above with X axis flipped).
2. Walk a square `2×areashow` pixel region centered on the player, blacken any pixel within radius `areashow` (Euclidean distance test) in `Radar_Tex2`.
3. Position the player-marker entity at the texture-mapped (X, Y).

**Origin-spawn guard:** `Radar_WorldSize = 0` is the offline / uninitialized case. The audit-comment block at [`Radar.bb:324-328`](../../src/Modules/Radar.bb#L324) records that pre-fix, the math produced `Inf` (division by zero), which then propagated into `PositionEntity` and corrupted the player marker. The guard now short-circuits to `plrx# = 0.0 / plry# = 0.0` in that case.

### Fog persistence (`Save_Radar_Fog`)

The fog texture is persisted to `Data\Areas\Radar\<area>.rdr` so the player's exploration carries across sessions. The save shape is one `WriteInt` per pixel (262144 ints = 1 MB per zone).

**Atomic write via `SafeWriteOpen` / `SafeWriteCommit`** ([`Radar.bb:216-238`](../../src/Modules/Radar.bb#L216)). The audit comment records that the previous implementation:
- Opened the file via `WriteFile` but never `CloseFile`d it — one handle leaked per save call.
- Wrote directly to the production path with no temp / atomic-rename — a crash mid-save would leave a truncated `.rdr`.

Both bugs are closed: `SafeWriteCommit` owns the close, atomic-promotes the temp to the production path, and demotes the previous `.rdr` to `.rdr.bak` (CLAUDE.md → "Atomic writes" canonical pattern).

### Other API surface

| Function | Purpose |
|---|---|
| `Position_Radar(X, Y, Width, Height)` | Re-place + re-scale the HUD overlay. Called on resolution change. |
| `Show_Radar` / `Hide_Radar` | Toggle visibility of the whole stack (via `Radar_Ent1` — children inherit). |
| `Reset_Radar` | Re-fill the fog texture with the interior-fog noise pattern (clears discovery). |
| `Clear_Radar` | Set the fog texture to fully transparent black (reveals everything). For debug / DM tooling. |
| `Unload_Radar` | Free all four textures + entities. Called by `Load_Radar` before building a new stack. |
| `Create_Radar_Fog(Interior)` | Build a fresh fog texture. `Interior = True` paints the grey-noise pattern (`Radar_FColor ± 20/10`); `False` returns an empty allocation that `Load_Radar_Fog` then overwrites. |
| `Load_Radar_Fog(R_FN$)` | Read a `.rdr` file pixel-by-pixel into a new fog texture. |
| `Radar_CreateBorderTex` / `Radar_CreatePlayerTex` | Procedurally generate the default border and player-marker textures (used when no custom `.png` is loaded). |
| `Radar_CreateQuadr(P)` | Build a 1×1 quad mesh parented to `P` — copy of `GY_CreateQuad` inlined to keep this module dependency-free. |

## Conventions for new code touching this module

- **`Radar_WorldSize = 0` is the offline sentinel** — guard divides before using it. The single existing site is at [`Radar.bb:329`](../../src/Modules/Radar.bb#L329).
- **All `WriteFile` to `.rdr` goes through `SafeWriteOpen` / `SafeWriteCommit`** — never write directly. The atomic-rename + `.bak` retention is non-optional.
- **All textures and entities are `FreeTexture` / `FreeEntity`-d in `Unload_Radar`** with `<> 0` guards — pattern is "if the handle exists, free it; zero the handle". New globals follow the same shape.
- **Scratch images (`TempImage`) must be `FreeImage`d before the function returns** — per the zone-load leak fix audit comment at [`Radar.bb:91-95`](../../src/Modules/Radar.bb#L91). This is the canonical example.
- **`SetBuffer TextureBuffer(...)` / `LockBuffer` blocks must always have a matching `UnlockBuffer` + `SetBuffer BackBuffer()`** — leaving the buffer locked corrupts subsequent rendering for the rest of the frame.

## Related modules

- [`ClientNet.bb`](clientnet.md) — calls `Load_Radar` on zone-enter (~line 1738) and `Save_Radar_Fog` on zone-leave (~line 1682). The actual zone-change packet dispatch lives here.
- [`ClientLoaders.bb`](clientloaders.md) — calls `Unload_Radar` at client shutdown (~line 372).
- [`Logging.bb`](logging.md) — provides `SafeWriteOpen$` / `SafeWriteCommit%` (the atomic-write helpers).
- [`Environment.bb`](environment.md) / [`Environment3D.bb`](environment3d.md) — own `GY_Cam` (the 2D HUD camera) and `Cam` (the 3D world camera).

## See also

- CLAUDE.md → "Atomic writes" — canonical `SafeWriteOpen` / `SafeWriteCommit` pattern.
- CLAUDE.md → "Float sanitisation at the BVM / wire boundary" — adjacent concern; `Radar_WorldSize = 0` divide-guard is one of the `/0` sites covered by an earlier sweep.
- [`reference_safewrite_migration_template.md`](../../../../.claude/projects/C--Users-dyanr-Desktop-rcce2/memory/reference_safewrite_migration_template.md) (agent memory) — the per-site migration checklist that `Save_Radar_Fog` follows.

* * *

The legacy function-by-function reference for this module has not been generated. The conceptual overview above is the primary reference; consult the source at [`src/Modules/Radar.bb`](../../src/Modules/Radar.bb) for full signatures.
