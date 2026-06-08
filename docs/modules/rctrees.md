<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**RCTrees.bb**

Tree and grass renderer. Splits a tree mesh into a trunk + multiple branch sub-meshes based on the texture-name substring `"branch"`, allocates a per-branch state record for spring-oscillator sway animation along all three axes, hides distant trees outside the configured display range, recolors branches and grass by season, and re-tunes sway amplitude/speed per weather.

Original credit per the source-file header (line 2): **created and coded by Jeff Frazier (rifraf), 2005-2006.** Pre-dates BlitzForge; the API is the original `Camel_Case` Blitz3D style with non-`Strict`-mode globals.

> **Current-build consumer status.** The runtime game client (`Client.bb` / `ClientAreas.bb`) does NOT currently call `LoadTree` / `LoadGrass` — the load sites at [`ClientAreas.bb:351`](../../src/Modules/ClientAreas.bb#L351) and `:356` are commented out. Live consumers are the world editor (`GUE.bb`), the Terrain Editor variant `ClientAreasTE.bb`, and `Tools/RC Terrain Editor.bb` (which calls `updatetrees` 4x per frame at [3420-3423](../../src/Tools/RC%20Terrain%20Editor.bb#L3420)). `ClientAreas.bb` retains the defensive `UnloadTrees(False)` call at line 986 for editor-tool integration. This module is therefore primarily an **editor-side** feature today; documenting it for future re-integration or content-pipeline work.

## Conceptual overview

### Three Types

| Type | Purpose | Field summary |
|---|---|---|
| `Tree` | One animated tree | `MainEnt` / `MainSurf` / `MainBrush` / `MainTex` (trunk), `MaxBranches`, `Branchent[400]` / `Branchsurf[400]` / `Branchbrush[400]` / `BranchTex[400]` (branch sub-meshes), 3-axis `SwayDir / SwayValue# / SwayPower#` per-branch arrays for the oscillator state, `distance#`, `inview`, `Evergreen` (gate season recolor), `swingstyle` (stored as `1` if the caller passed `0`, else `4` — see "Source quirk" below; the anchor is selected separately from the input arg). The `Maxbranches = 400` `Const` is the per-tree branch cap. |
| `RcGrass` | One grass mesh | `ent`, `surface`, `brush`, `tex`, `texname$`, `evergreen`. Lighter than `Tree` — no per-vertex animation; grass animates via texture-rotation in `updategrass`. |
| `GrassTextures` | Texture-share cache built by `clumpgrass` | `Brush`, `Texname$`, `tex`. Deduplicates `RcGrass` brushes so all grass entries with the same texture name share one `Brush` handle — major draw-call win when a zone has many of the same grass tile. |

### Module-scope globals

| Global | Purpose |
|---|---|
| `TREE_SEASON` / `TREE_OLDSEASON` | Current and last-applied season (0..11); change between them triggers a recolor pass via `Tree_SetSeason`. |
| `TREE_WEATHER` / `TREE_OLDWEATHER` | Current weather ID; mismatch triggers `Tree_Changeweather`. |
| `TreeSwayMax_X/Y/Z#` | Per-axis sway amplitude. Set by `Tree_Changeweather` from the `weather_wind_swaymax#(weather, axis)` 2D `Dim` table. |
| `SwayPower_X/Y/Z#` | Per-axis sway *speed* (radians/frame). Same — driven by `weather_wind_swayspeed#`. |
| `TreeChange = 75` / `TreeAnimationRange = 200` / `TreeDisplayRange = 220` | LOD knobs. `TreeChange` is the per-frame random period for distance recompute (1-in-`TreeChange` chance per tree per frame); `TreeAnimationRange` = below this distance, branches sway; `TreeDisplayRange` = above this distance, tree is hidden. `updatetrees` re-tunes `TreeDisplayRange = fogfarnow + 20` and `TreeAnimationRange = fogfarnow * .6` per frame from the fog-far globalconfigured elsewhere. |
| `GrassFadeNear# = 50.0` / `GrassFadeFar# = 60.0` | `EntityAutoFade` range for grass. |
| `GrassSwayPower = 13` | Multiplier applied to grass texture-rotation animation. |
| `Max_Grass_Clumps = 100` | Cap on `grassCLUMP(...)` (Dim array used by the deduplication pass, but no current site reads or writes it — vestigial from an earlier `clumpgrass` shape). |
| `seasoncolor_file$` | `Data\Game Data\RCTE.dat` — persistent per-season RGB triples (12 seasons × 3 bytes). |

### Six weather slots × three sway axes

```basic
Dim weather_wind_swaymax#(7, 3)     ; per-weather × per-axis amplitude
Dim weather_wind_swayspeed#(7, 3)   ; per-weather × per-axis speed
```

`Dim X(7, 3)` allocates `8 × 4` slots — but only `(0..5, 1..3)` are populated. The six used weather indices are the `W_Sun = 0` through `W_Wind = 5` `Const`s declared in [`Environment.bb:2-7`](../../src/Modules/Environment.bb#L2); slots 6 and 7 of the first dimension and slot 0 of the second dimension are unused (Blitz3D `Dim X(N)` is inclusive — see CLAUDE.md → "Gotchas" → "Blitz3D array semantics").

`tree_setvalues()` ([RCTrees.bb:583-657](../../src/Modules/RCTrees.bb#L583)) populates the 2D table with hard-coded per-weather wind profiles. Some samples:

| Weather | X sway max / speed | Y / speed | Z / speed |
|---|---|---|---|
| `W_SUN` | 3 / 0.05 | 3 / 0.05 | 3 / 0.05 (calm baseline) |
| `W_STORM` | 5 / 0.3 | 4 / 0.3 | 7 / 0.4 (most violent) |
| `W_WIND` | 6 / 0.2 | 6 / 0.1 | 7 / 0.2 |

### Trunk-vs-branch splitting at load

`LoadTree(Tfile$, Evergreen, ConvertEnt=0, SwingStyle=0)` ([RCTrees.bb:128](../../src/Modules/RCTrees.bb#L128)) is the constructor. Either loads a `.b3d` from disk via `LoadMesh(Tfile$)` or accepts an already-loaded entity via `ConvertEnt > 0` (the path GUE / ClientAreasTE use — scenery is already loaded as a `Scenery` entity; this function repurposes the mesh).

For each surface of the input mesh:

1. Read the brush texture name via `TextureName$(GetBrushTexture(GetSurfaceBrush(surf)))`.
2. If the name **does not** contain the substring `"branch"`: this surface is part of the trunk. The first such surface seeds `RT\MainENT` / `RT\MainSurf` / `RT\MainBrush`. Subsequent trunk surfaces are *dropped* (the `Else` branch is commented out — only one trunk brush per tree). `BrushFX rt\mainbrush, 2` (full-bright) is applied.
3. If the name **contains** `"branch"`: this surface becomes a new branch sub-mesh. `BrushFX rt\branchbrush, 16+2` (full-bright + leaves-style alpha test).

The triangle-by-triangle copy loop (RCTrees.bb:190-244) rebuilds vertices and triangles into the split surfaces, applying per-vertex random color offsets (`coloroff = Rand(-65, 5)`) for cheap visual variation.

After surfaces are built, each branch is anchored using `swingstyle`:

- `swingstyle = 0` → `CenterMesh` (sway pivot at branch geometric center)
- `swingstyle = 1` → `HangMesh` (pivot at branch top — for downward-hanging foliage)
- `swingstyle = 2` → `StandMesh` (pivot at branch base — for upright branches)

The `Tree\swingstyle` field stores either `1` or `4` (note: NOT the input `swingstyle` value directly) — `1` if the caller passed `0`, `4` otherwise. `updatetrees` divides sway oscillator output by this value (`(sx/rt\swingstyle) * tdelta`), so `swingstyle = 4` yields quartered sway amplitude.

> **Source quirk worth noting:** the `Tree\swingstyle` field stores `1` or `4`, but the swing-style **anchor** (`CenterMesh` / `HangMesh` / `StandMesh`) is picked from the input `swingstyle` argument via a `Select Case 0..2` at RCTrees.bb:275-282 — i.e. **the input argument's value space doesn't match the field's value space.** Code path: caller passes `swingstyle = 1`, the Select Case picks `Hangmesh` (top-anchored), and `rt\swingstyle = 4` is stored (because input was `<> 0`). Future maintenance touching this should be careful — the input arg and the stored field are semantically different.

### Per-frame: `updatetrees(camera_ent, tdelta = 1.0)`

[RCTrees.bb:301-438](../../src/Modules/RCTrees.bb#L301). Called every frame from the editor consumers. Phases:

1. **Sync to engine state.** Multiplies `tdelta * 3` (3x the engine delta — empirical tuning), calls `updategrass`, sets `Tree_season = currentseason` and `Tree_weather = currentweather` from engine globals.
2. **Weather + season change detection.** If the cached `tree_oldweather <> tree_weather` or `TREE_OLDSEASON <> TREE_SEASON`, re-tunes via `Tree_changeweather` / `tree_setseason`.
3. **LOD distance refresh** — `trnd = Rand(1, 10000)`; if `trnd < 250` (2.5% chance per tree per frame), recompute `RT\Distance#` via `distance(MAINENT, FROM_ENT)`. This is a deliberate cost-amortization — full distance recomputation per tree per frame is wasteful, so each tree's distance is updated every ~40 frames on average.
4. **For each `Tree`:** if `Distance <= TreeDisplayRange And inview = 1`, show + animate. If `Distance <= TreeAnimationRange`, run the 3-axis spring oscillator on each branch (clamping at `± TreeSwayMax_X/Y/Z#`, reversing direction at the limits, periodically randomizing `swaypower#`). Otherwise hide the mesh.

The spring oscillator state machine per axis: `swaydir` is 1 (positive) or 2 (negative); `swayvalue#` accumulates `±swaypower#` per frame; on hitting `±TreeSwayMax#`, `swaydir` flips. Output rotation is `(swayvalue / swingstyle) * tdelta`.

`updategrass(Gdelta, Grasswind)` ([RCTrees.bb:811-820](../../src/Modules/RCTrees.bb#L811)) is much simpler: maintains a global `GrassPos` angle (mod 3600 = 10-degree resolution full revolution), looks up `lcos#(GrassPos) * .015 * 360` from the pre-computed cosine table, calls `TransTex` on every distinct grass texture to rotate it. No per-instance per-frame work — texture rotation is global.

### Pre-computed trig LUTs

```basic
Dim lcos#(3700)
Dim lsin#(3700)
For i = 0 To 3600
    lsin#(i) = Sin(Float(i) / 10.0)
    lcos#(i) = Cos(Float(i) / 10.0)
Next
```

3601 slots covering 0..360° at 0.1° resolution. Indexed by the integer `GrassPos`. The `(3700)` `Dim` size gives 100 slots of head-room past `3600` so off-by-one misuses don't OOB-read.

### Grass deduplication: `clumpgrass`

[RCTrees.bb:668-705](../../src/Modules/RCTrees.bb#L668). Walks every `RcGrass` and builds a `GrassTextures` cache keyed by texture name. After: every `RcGrass` whose texture name matches an existing cache entry shares the cache entry's `Brush` handle (via `PaintMesh rcg\ent, gt\brush`). Closes a real perf issue: pre-clump, a zone with 200 grass tufts using 10 unique textures would issue 200 brush-swap calls per frame; post-clump, 10.

The freed-but-still-referenced texture/brush is the classic refcount edge — `FreeBrush rcg\brush` / `FreeTexture rcg\tex` on the per-grass handle is safe because the cached `gt\brush` / `gt\tex` is a fresh `GetBrushTexture` lookup, not an alias.

### Season system

`Tree_SetSeason(sn)` recolors every non-evergreen `Tree` and `RcGrass` to the season's RGB triple from `season_red(0..11)` / `season_green(0..11)` / `season_blue(0..11)`. The 12 seasons (the engine has more than 4) are loaded from `Data\Game Data\RCTE.dat` by `tree_setvalues` — or generated with random defaults and persisted if the file is missing.

Persistence shape: 12 × 3 × `Int = 144 bytes`. **Plain `WriteFile`, not `SafeWriteOpen` / `SafeWriteCommit`.** A crash during write leaves a truncated file; subsequent `ReadInt` past EOF zero-fills (Blitz3D doesn't error on past-EOF reads), so missing seasons get `0,0,0` (black). This is a SafeWrite migration candidate — same pattern as `RP_SaveEmitterConfig` in [`RottParticles.bb`](rottparticles.md).

## Conventions for new code touching this module

- **Branch surfaces are identified by texture-name substring `"branch"`.** Authoring a tree mesh where branches don't have "branch" in the texture name collapses the whole tree into a trunk-only entity with no sway. This is undocumented in the source and a future content-pipeline rewrite should formalize the contract.
- **`Tree\swingstyle` field stores `1` or `4`, NOT the input arg's value.** Input value space is `0..2` (the `Select Case` at line 275-282 picks `CenterMesh` / `HangMesh` / `StandMesh`). Don't compare `rt\swingstyle` against the input arg's range — they're different semantically.
- **`tree_setvalues` writes `Data\Game Data\RCTE.dat` via plain `WriteFile`.** Atomic-write migration is a candidate; the file is small (144 bytes) and a corrupt write only loses the random defaults, so the impact is low.
- **`updatetrees` reads engine globals `currentseason`, `currentweather`, `fogfarnow`** — added at module scope by the broader engine. Not declared in this file. If the engine ever stops setting them, `updatetrees` silently degrades (zero everywhere).
- **`distance(e1, e2)` is XZ-only** (2D ground-plane distance, ignoring Y) — not Euclidean. Documented by the function body's `Sqr#((EntityX - EntityX)^2 + (EntityZ - EntityZ)^2)`. Aerial trees would compute "near" distances even when far above/below the camera.
- **Maxbranches = 400 is a `Const` per-tree cap.** A tree with > 400 branch surfaces silently drops the overflow (the surface loop continues but `RT\Branchent[bcount]` indexes past the field's declared size 0..400, which Blitz3D doesn't bounds-check — writing past the field's `[400]` allocates more slots dynamically, but cap-aware code in `updatetrees` only loops `For bn = 1 To rt\maxbranches`).
- **`UnloadTrees(False)` is the safe defensive call** — keeps the underlying entities alive (caller owns them) and just deletes the Type instances. `UnloadTrees(True)` is the destructive variant that also `FreeEntity`s the mesh handles. Use `False` when the meshes are part of a `Scenery` collection the caller wants to retain.

## Related modules

- [`GUE.bb`](../../src/GUE.bb) — world editor; the primary `LoadTree` / `LoadGrass` caller at [9408-9412](../../src/GUE.bb#L9408).
- [`ClientAreas.bb`](clientareas.md) — runtime client zone-load. Calls `UnloadTrees(False)` defensively at line 986 but does NOT load trees (load sites commented out at lines 351/356).
- [`ClientAreasTE.bb`](../../src/Modules/ClientAreasTE.bb) — Terrain Editor's `ClientAreas` variant; active `LoadTree` / `LoadGrass` callers at lines 303/308.
- [`Tools/RC Terrain Editor.bb`](../../src/Tools/RC%20Terrain%20Editor.bb) — calls `updatetrees(Cam)` 4× per frame at lines 3420-3423 (intentional — the redraw cycle batches 4 updates per render to make the editor's preview interactive).
- [`Environment.bb`](environment.md) / [`Environment3D.bb`](environment3d.md) — define `currentseason`, `currentweather`, `fogfarnow`, and the `W_*` weather constants that this module consumes.

## See also

- CLAUDE.md → "Atomic writes" — `tree_setvalues`' `WriteFile` on `RCTE.dat` is a migration candidate.
- CLAUDE.md → "Gotchas" → "Blitz3D array semantics" — `Dim X(N)` allocates `N+1` slots; relevant for `Dim weather_wind_swaymax#(7, 3)` (8 × 4 slots).
- [`rottparticles.md`](rottparticles.md) — sibling-style "graphics subsystem with a non-atomic save format" with the same migration-candidate flag.

* * *

The legacy function-by-function reference for this module has not been generated. The conceptual overview above is the primary reference; consult the source at [`src/Modules/RCTrees.bb`](../../src/Modules/RCTrees.bb) for full signatures.

The module exports 25 functions. Notable groups:

- **Tree lifecycle:** `LoadTree` ([RCTrees.bb:128](../../src/Modules/RCTrees.bb#L128)), `Deletetree` ([:480](../../src/Modules/RCTrees.bb#L480)), `UnloadTrees` ([:490](../../src/Modules/RCTrees.bb#L490)), `Droptree` ([:441](../../src/Modules/RCTrees.bb#L441)) — drop-via-LinePick onto terrain.
- **Per-frame:** `updatetrees` ([:301](../../src/Modules/RCTrees.bb#L301)), `updategrass` ([:811](../../src/Modules/RCTrees.bb#L811)).
- **Anchor helpers:** `CenterMesh` ([:461](../../src/Modules/RCTrees.bb#L461)), `StandMesh` ([:465](../../src/Modules/RCTrees.bb#L465)), `HangMesh` ([:469](../../src/Modules/RCTrees.bb#L469)) — anchor a branch mesh's pivot.
- **Season/weather:** `Tree_SetSeason` ([:512](../../src/Modules/RCTrees.bb#L512)), `Tree_Changeweather` ([:659](../../src/Modules/RCTrees.bb#L659)), `tree_setvalues` ([:583](../../src/Modules/RCTrees.bb#L583)).
- **Grass:** `LoadGrass` ([:562](../../src/Modules/RCTrees.bb#L562)), `clumpgrass` ([:668](../../src/Modules/RCTrees.bb#L668)), `ColorGrass` ([:762](../../src/Modules/RCTrees.bb#L762)), `Lightmapgrass` ([:822](../../src/Modules/RCTrees.bb#L822)), `TransTex` ([:803](../../src/Modules/RCTrees.bb#L803)).
- **Visibility:** `SetTreePickmode`, `Tree_Hideall`, `Tree_Showall`, `tree_autofade`.
- **Utility:** `CountTrees`, `fps`, `distance`, `xForm`.
