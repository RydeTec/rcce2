# ADR 007 — World mode is editable for scenery (only)

**Status:** Accepted (beta)
**Date:** 2026-07-14
**Supersedes:** the "scenery mesh painting / placement" line of ADR 004's deferred list, and the read-only-*for-scenery* stance implied by ADR 002 (which is otherwise superseded by the beta editing work).

## Context

Loom's zone viewport has two modes (ADR 004 Phase C): **schematic** (portal/trigger/spawn/waypoint markers over a flat editor floor) and **world** (the zone's real terrain / scenery / water loaded by `LoadAreaData`). A follow-up made the *schematic gameplay entities* editable in world mode too — you can drag a portal onto the real terrain — persisting through `ServerSaveArea` (the gameplay `.dat`).

Scenery is a different animal. Scenery mesh instances are **visual world data**: they exist only after `LoadAreaData` populates the `Each Scenery` list, and they persist through **`SaveArea`** — the whole-visual-area serializer that rewrites terrain heightmaps, water, collision boxes, emitters, sound zones **and** scenery in one pass. Two structural problems blocked reusing the gameplay-entity path:

1. **`SaveArea` was unreachable from Loom.** It lived in `ClientAreas.bb`, which Loom deliberately does not `Include` (it drags in the Gooey/F-UI stack — see "Why no ClientAreas.bb Include" in `architecture.md`).
2. **`SaveArea` is a whole-area rewrite from live entities.** Calling it when the world is *not* fully loaded would serialize empty `Each <Type>` lists and **zero every section on disk** — terrain, water, scenery, all of it. The gameplay `ZoneSaved` flag drives `ServerSaveArea` on Ctrl+S / Save All at any time, including in schematic mode where no visual data is loaded. Wiring scenery into that shared flag would eventually fire `SaveArea` against an unloaded world.

## Decision

**World mode is now editable for scenery meshes — add, move, delete, select — and nothing else.** Terrain heightmap sculpting, water-volume editing, and weather/environment sub-tabs remain deferred.

Two guardrails make this safe:

1. **`SaveArea` relocated to the shared data-only module** (`Modules/AreaLoader.bb`, "Phase D"), a verbatim move mirroring how `LoadAreaData` was carved out of `ClientAreas.bb` in ADR 004 Phase B. GUE `Include`s `AreaLoader.bb` before `ClientAreas.bb`, so its `SaveArea` calls resolve to the relocated definition unchanged — no behavior change for GUE. Loom can now reach the serializer without the Gooey coupling.

2. **A separate dirty flag + a world-loaded gate.** Scenery edits flip a dedicated `SceneryDirty` global, **never** the shared `ZoneSaved`. Persisting goes through `Loom_SaveScenery`, which **refuses to call `SaveArea` unless `VPWorldLoaded` (and `VPWorldMode`) are true** — a no-op + warning toast otherwise, never a partial write. So a gameplay edit can never trigger the visual-area save, and the visual-area save can never run against an unloaded world.

Scenery editing is entered via an explicit **"add scenery"** pill (world mode only) that opens a `MeshCatalog` picker and arms a placement brush. It is a distinct interaction mode (`ScnAddMode`) so it never collides with the world-mode gameplay-marker editing that shares the same buttons.

## Rationale

- **Why relocate `SaveArea` rather than author a scenery-only writer?** The scenery block is a count-prefixed variable-length section inside a larger file; hand-writing a partial serializer would risk drift from GUE's format and could not safely rewrite just one section of the file. Reusing the exact serializer GUE uses is the no-format-drift path, and the ADR-004 Phase B precedent already established "move the data-only function into the shared module" as the clean way to share it.

- **Why a separate `SceneryDirty` flag?** The shared `ZoneSaved` conflates two saves with very different blast radii: `ServerSaveArea` (gameplay `.dat`, always safe) and `SaveArea` (visual `.dat`, catastrophic if the world isn't loaded). Keeping the flags separate means the always-safe gameplay path and the guarded visual path can never trigger each other. This is the single most important safety property of the change.

- **Why scenery-only, not terrain/water?** Scenery is discrete instances (place a tree, move a rock) — cheap to model as "create entity, set fields, reposition." Terrain sculpting and water volumes are continuous-surface / brush-stroke subsystems with their own tooling, undo semantics, and performance concerns. They deserve their own design pass and are not required for the "populate a zone with props" workflow this change targets.

- **Editor pickability vs. persisted collision.** New scenery is created with collision type 0 (parity with GUE's place-from-browser default → `GetEntityType` serializes 0) but `EntityPickMode 2` for editor selection. Pick mode is a runtime property `SaveArea` does not serialize, so this is a pure editor affordance that changes no on-disk data. Loaded scenery is force-set pickable on world load for the same reason.

## Consequences

**Good:**
- Designers can place, move, and delete scenery meshes directly on the rendered terrain, then persist with one "save scenery" click — the core "dress the zone" loop, previously GUE-only.
- The whole-area `SaveArea` is now reachable by any future data-only consumer (a headless zone tool, a batch re-saver) without the Gooey coupling.

**Costs / limits:**
- Scenery edits live in memory until "save scenery" is clicked; leaving world mode (or switching zones) discards unsaved scenery edits — Loom warns via toast. This is deliberate: it keeps the world-loaded gate airtight rather than trying to auto-persist on mode change.
- Scenery is **not** wired into Ctrl+S / Save All / the exit-prompt (those drive `ZoneSaved` → `ServerSaveArea` only). A designer must use the dedicated pill. This asymmetry is the price of the safety separation and is documented in the hint bar.
- Terrain heightmap sculpting, water-volume editing, weather/environment sub-tabs, and a distinct scenery-**texture**-paint surface remain deferred (see `roadmap.md`).
