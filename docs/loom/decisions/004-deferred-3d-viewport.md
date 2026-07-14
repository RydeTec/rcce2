# ADR 004 — Defer literal 3D zone viewport

**Status:** Accepted (alpha)
**Date:** 2026-05-26

## Context

The Loom design's prototype shows a stylized 2D "world scene" with a fake 3/4 perspective — actors as sprites, scenery as illustrated trees, portals as glowing rings. It's the most visually striking surface in the prototype. A literal-minded port would render the zone in real 3D using Blitz3D's engine (the same engine GUE's Zones tab uses for its `FUI_View` viewport).

The natural seam is `ClientAreas.bb`'s `LoadArea(Name$, CameraEN, DisplayItems, UpdateRottNet)` function — the canonical client-side zone loader. It opens `Data\Areas\<name>.dat`, loads the zone's `.b3d` mesh, applies textures, places scenery instances, hooks up emitters, and binds the result to a camera.

## What `LoadArea` actually drags in

Reading the function (~400 lines):

```basic
Function LoadArea(Name$, CameraEN, DisplayItems = False, UpdateRottNet = False)
    ...
    LoadProgressBar = GY_CreateProgressBar(...)        // Gooey lib
    LoadScreen = CreateMesh(GY_Cam)                    // Gooey camera global
    If ResolutionType = 1 ...                          // GUE setup global
    PLoadMusic = LoadSound("Data\Music\" + GetMusicName$(LoadingMusicID), False)
                                                       // GetMusicName$ from GUE.bb
    ...
    Tex = GetTexture(LoadingTexID)                     // Media.bb (already shared)
    If RandomImages > 0 ...                            // GUE setup global
    ...
```

It transitively pulls in:

- **Gooey** (`Modules/Gooey.bb`, ~5k lines) — a separate UI lib that GUE uses for 3D-camera-based widgets. Sets up `GY_Cam`, `GY_CreateProgressBar`, `GY_UpdateProgressBar`, etc.
- **`GetFilename$`** — a 9-line helper defined inside `GUE.bb` itself, **not** in any shared module. (`Function GetFilename$(Path$)` at `GUE.bb:9703`.)
- **`GetMusicName$`** — defined in `Media.bb`, fine, but used inside `LoadArea` to play loading music — which Loom doesn't want.
- **`ResolutionType`**, **`RandomImages`**, **`LoadProgressBar`**, **`LoadScreen`** — GUE setup globals.
- A separate "loading screen" rendering pass that captures `RenderWorld` mid-load and shows the progress bar.

Including `ClientAreas.bb` from Loom would mean either:

1. Include `Gooey.bb` and define every missing GUE global as stubs in `Loom.bb` — drags in 5k+ lines of code Loom doesn't otherwise need, plus a parallel set of `GY_*` globals.
2. Extract `GetFilename$` to a shared module (cheap) and stub the rest — still drags in Gooey + the loading-screen render path.
3. Rewrite `LoadArea` to factor out the data-loading from the UI overhead — meaningful refactor of GUE's hot path; risky.
4. Write a parallel Loom-side `.dat` parser — easy to start, but duplicates the format-parsing logic. Risks drift if the `.dat` format ever changes.

## Decision

**Defer the 3D viewport.** Zone composer shows zone metadata as text + portal-target chips. No 3D mesh rendering in the alpha.

## Rationale

- The Loom design's "world scene" was itself 2D SVG with a fake perspective — the design medium never assumed real 3D. A list-of-things + thread chips is closer to the design's intent than a buggy 3D viewport.
- The thread-chip composer is genuinely more useful for the alpha's stated job ("read your world through Loom's lens, follow the references") than a 3D camera spin would be.
- The dependency unwinding is its own multi-PR project. Worth doing for beta editing (when Loom needs to be a real spatial editor), not worth doing for an alpha viewer.
- The intermediate 2D zone-map I built in the closed #294 was a halfway-house that also wasn't useful enough — see [ADR 003](003-zone-only-pivot-to-entity-browser.md). Better to skip the viewport entirely and bias the alpha toward content browsing.

## Consequences

**Good:**
- Loom doesn't pull in `Gooey.bb` or any other UI substrate. It stays small (~2.4 MB binary).
- The data-loading boundary stays clean — Loom reads through pure data loaders (`LoadActors`, `ServerLoadArea`, etc.) with no UI side effects.
- Zone composer is fast and reliable (no mesh load means no per-zone-switch latency).

**Bad:**
- The most visually striking surface in the prototype isn't reproduced. Users opening Loom expecting "see my world" get "see metadata about my world's zones" instead.
- "Click a portal to follow it to the target zone" works in Loom but the user can't *see* either zone — it's all text.
- When a beta user is placing scenery / waypoints / spawns in a zone and wants to *see* their placement, they have to switch back to GUE.

## What unlocks the viewport

The right unblock is **extracting `LoadArea`'s data path from its UI overhead** as a refactor inside the engine (not a Loom PR). Approximate shape:

1. **Phase A** — Extract `GetFilename$` to `src/Modules/Path.bb` (or similar shared utility). Trivial; one PR. GUE updates its callers. **Shipped** (PR #429).
2. **Phase B** — Carve `LoadArea` into `LoadAreaData(Name$)` (parses .dat, builds the in-memory mesh + scenery instances) and `LoadAreaUI(...)` (does the progress bar + loading screen). GUE calls both; Loom calls just `LoadAreaData`. **Shipped**, with one delta from the sketch above: instead of two sequential functions (which couldn't show progress *during* the load), the data path moved to `src/Modules/AreaLoader.bb` as `LoadAreaData(Name$, CameraEN, DisplayItems, UpdateRottNet)`, and the UI comes back in through three hook functions the including target must define — `AreaLoadBegin(DisplayItems)`, `AreaLoadProgress(Pct)`, `AreaLoadEnd()`. GUE's Gooey implementations (progress bar, loading screen, loading music) live in `ClientAreas.bb`, which also keeps the signature-identical `LoadArea` wrapper and `SaveArea`. `UnloadArea`, `SetViewDistance`, `ChunkTerrain` and the area Types moved with the data path. The no-Gooey property is pinned by `src/Tests/Modules/AreaLoaderGooeyFreeTest.bb`, which Includes `AreaLoader.bb` with pure data stubs and no UI modules — adding a `GY_*` reference to the module breaks that test's compile.
3. **Phase C** — Loom gets a `WorldView.bb` module that takes a loaded Area and renders it through its own camera. Reuses the same mesh entities GUE uses (no double-loading). Loom Includes `AreaLoader.bb` and provides its own (no-op or custom-drawn) `AreaLoad*` hooks. **Shipped**, as a "world" mode inside the existing `Modules/Loom/ZoneViewport.bb` rather than a separate `WorldView.bb` (the viewport already owned the camera, orbit/fly controls, marker overlay, and composer integration — a parallel module would have duplicated all of it). `Loom.bb` Includes `RottParticles.bb` + `RCTrees.bb` + `AreaLoader.bb` (both particle/tree modules turned out UI-free; no stubs needed) and declares the `C_*` collision constants AreaLoader references (only `Client.bb` declared them — GUE silently reads 0, a separate latent finding). Implementation notes that mattered: (a) the schematic scene lives at y=20000 for camera isolation while `LoadAreaData` places geometry at real coords — the viewport rides a mode-dependent Y offset (`VPSceneYOff#`) instead of re-parenting the world; (b) `LoadAreaData` unconditionally textures the `SkyEN/CloudEN/StarsEN` globals, which Client/GUE create elsewhere — Loom creates hidden placeholder spheres; (c) zones without a visual `Data\Areas\<name>.dat` soft-fail back to schematic with a toast; (d) the loader's camera-fog/ambient side effects are restored on unload; (e) ground-plane editing interactions (add/drag-move) are schematic-mode-only — in world mode the pick ray lands on terrain and the handlers no-op.

Phases A, B and C are all shipped. **Follow-up: editing against real terrain in world mode (pick-target rework) is now also shipped** — the `Loom_PickGround` helper in `ZoneViewport.bb` unifies the add / drag-move / delete pick target across both modes (flat `VPGround` floor in schematic, real `LoadAreaData` terrain/scenery in world), new entities land at the picked terrain height (`Loom_PlaceY#`), and the world-mode read-only gates are removed. Writes still route through `ServerSaveArea` unchanged. Remaining follow-ups: terrain heightmap sculpting, scenery mesh placement/painting, water-volume editing, weather/environment sub-tabs (each its own large subsystem), and sky/weather render fidelity if the viewport ever wants it.

Estimated total: 3 PRs, none of them in Loom proper. The Loom viewport ships as a fourth PR after those land.

## What would force a re-evaluation

- **A user demand for "I need to see my zone in 3D from Loom."** Then unblock phases A-C above.
- **`LoadArea`'s implementation changes** in a way that simplifies the UI coupling (unlikely; it's stable).
- **Loom needs the mesh data for non-rendering reasons** (e.g. "validate that placed scenery has a real mesh asset"). Same dependency, same unblock.
