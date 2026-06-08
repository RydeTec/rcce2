<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**RottParticles.bb**

The particle emitter substrate. Owns the three core Types (`RP_Emitter` — live emitter, `RP_Particle` — one 4-vertex billboard sprite, `RP_EmitterConfig` — template), the per-frame `RP_Update` mover that advances every live particle in lockstep, the file-backed `.rpc` config format with its 40-field positional schema, and ~40 `RP_Config*` setters covering every tweakable parameter (velocity, force, color, alpha, scale, lifespan, texture animation, shape, blend mode).

Every visual effect in the game that flickers, sparkles, smokes, or trails — projectile contrails, spell impacts, environmental fog patches, fire/torch/torch-flame, etc. — is one or more `RP_Emitter` instances driven by a config that the content authoring tool (RC Architect) saved to `Data\Emitter Configs\<name>.rpc`. The runtime loads those `.rpc` files lazily as the scene demands.

The "Rott" prefix is a project-history artifact (the emitter system predates the BlitzForge fork). All functions are namespaced `RP_*` to avoid collision with engine code.

## Conceptual overview

### Three Types

| Type | Allocated by | Lifetime | Purpose |
|---|---|---|---|
| `RP_EmitterConfig` | `RP_CreateEmitterConfig` (in-memory) or `RP_LoadEmitterConfig` (from disk) | Reusable template — one config can drive many emitters | Holds ~40 fields: spawn rate, particle lifetime, initial velocity (with random spread), constant force, scale animation, color start + per-frame delta, alpha start + delta, texture handle + tiling + animation speed, blend mode, shape (Sphere/Cylinder/Box) + dimensions, FaceEntity (camera handle for billboarding). The full schema is the [`RP_EmitterConfig` Type definition](../../src/Modules/RottParticles.bb#L50). |
| `RP_Emitter` | `RP_CreateEmitter(Configuration, Scale=1.0)` | One per active in-world emitter | Holds `MeshEN` (the dynamic mesh that holds particle quads), `EmitterEN` (the parent pivot for position/rotation), `Config` (pointer to a `RP_EmitterConfig`), `Enabled` (gating the spawn loop), `KillMode` (0 = normal, 1..4 = pending-free states), `ToSpawn` (per-frame spawn debt), `ActiveParticles` (count for fast iteration shortcut), `Scale#`. |
| `RP_Particle` | Pre-allocated by `RP_CreateParticle` (called in a loop from `RP_CreateEmitter` up to `MaxParticles`) | Recycled via `InUse` flag during normal per-frame operation; `Delete`d only at emitter teardown by `RP_FreeEmitter` | Per-particle physics state: `FirstVertex` (the first of 4 quad verts on the parent mesh's surface), position `X/Y/Z`, velocity `VX/VY/VZ`, force `FX/FY/FZ`, color `R/G/B`, alpha `A`, scale, time-to-live, texture frame + per-frame change rate. |

### Three constant families

| Family | Constants | Used by |
|---|---|---|
| **Emitter shape** | `RP_Sphere = 1`, `RP_Cylinder = 2`, `RP_Box = 3` | `RP_EmitterConfig\Shape`; the `Select Case` in `RP_SpawnParticle` ([RottParticles.bb:258](../../src/Modules/RottParticles.bb#L258)) picks the initial position distribution. |
| **Velocity calculation mode** | `RP_Normal = 1`, `RP_ShapeBased = 2`, `RP_HeavilyShapeBased = 3` | `RP_EmitterConfig\VShapeBased`. `Normal` = use the raw `VelocityX/Y/Z` + `VelocityRndX/Y/Z` jitter; `ShapeBased` = bias the velocity sign by particle position so motion radiates outward from the shape; `HeavilyShapeBased` = bias the velocity *magnitude* by normalized position too (stronger directional bias). |
| **Force shaping** | `RP_Linear = 1`, `RP_Spherical = 2` | `RP_EmitterConfig\ForceShaping`. Affects how the `ForceMod` per-axis modifier scales the constant force. |

### `RP_Update(Delta = 1.0)` — the per-frame loop

Called every tick (typically from [`Server.bb`](../../src/Server.bb) or [`Client.bb`](../../src/Client.bb)'s main update). Two phases:

1. **Emitter walk (after-cursor pattern).** [`RottParticles.bb:80-114`](../../src/Modules/RottParticles.bb#L80). For each `RP_Emitter`:
   - Synchronize `MeshEN` position to `EmitterEN` (the parent pivot's world position).
   - If enabled: set `ToSpawn = Ceil(ParticlesPerFrame * Delta)` (debt counter — `RP_SpawnParticle` decrements it).
   - If in kill mode (1..4) and `ActiveParticles = 0`: `RP_FreeEmitter` with the matching free flags (see `RP_KillEmitter` below).
   - If disabled and idle: `HideEntity` the mesh.

   **After-cursor pattern** ([CLAUDE.md → Iterator-during-iteration hazards](../../CLAUDE.md)): `RP_FreeEmitter` deletes the current emitter mid-iteration; a naive `For Each` cursor would deref the freed instance's `next` on the following step. The `ENext = After E` capture before the kill branch closes this hazard. Same shape as [`Projectiles3D.bb`](projectiles3d.md)'s `UpdateProjectiles`.

2. **Particle walk.** `For P.RP_Particle = Each RP_Particle` — advances physics: integrates velocity into position; applies force-modifier per the `ForceShaping` mode; updates color/alpha/scale by the per-frame delta; decrements `TimeToLive`; recycles dead particles back into the `InUse = False` pool; rewrites the four vertices of the particle's quad on the parent mesh's surface. There is no after-cursor pattern here because particles are recycled (`InUse` toggled), not deleted.

### The emitter handle convention — `EntityName$(ID)` lookup

The function signatures `RP_EnableEmitter(ID)`, `RP_DisableEmitter(ID)`, `RP_HideEmitter(ID)`, `RP_ShowEmitter(ID)`, `RP_ScaleEmitter(ID)`, `RP_KillEmitter(ID, ...)`, `RP_FreeEmitter(ID, ...)`, `RP_EmitterActiveParticles(ID)` all take an `ID` argument that is **a Blitz3D entity handle** (the `RP_Emitter\EmitterEN` pivot), not a `Handle(E)` of the Type instance. The lookup goes through `Object.RP_Emitter(EntityName$(ID))` — the emitter's pivot entity carries the `Handle(E)` as its `EntityName$`, which the function converts back to the Type via `Object.RP_Emitter`.

This is unusual. The reason is that callers (projectile code, spell rendering, etc.) want to manipulate the emitter via the same entity handle they're already passing around for positioning/parenting (e.g. `EntityParent emitterID, projectileMeshID`), so the API takes that handle and does the indirection internally. Be aware:

- `RP_EmitterConfig` lookups use the standard `Object.RP_EmitterConfig(ID)` shape — `ID` here is a real `Handle()`.
- `RP_Emitter` lookups always go via `EntityName$(ID)` — `ID` here is the `EmitterEN` Blitz entity.

### `.rpc` config file format (positional)

[`RP_SaveEmitterConfig`](#rp_saveemitterconfig) and [`RP_LoadEmitterConfig`](#rp_loademitterconfig) read/write 40 fields in a fixed positional order. The schema is:

```
Int    MaxParticles, ParticlesPerFrame
Int    TexAcross, TexDown                       ; texture-atlas tiling (1×1 = no atlas)
Int    RndStartFrame                            ; True = pick a random atlas tile per spawn
Int    TexAnimSpeed                             ; frames-per-frame for texture cycling
Int    VShapeBased                              ; RP_Normal / RP_ShapeBased / RP_HeavilyShapeBased
Float  VelocityX, Y, Z                          ; base initial velocity
Float  VelocityRndX, Y, Z                       ; ± jitter
Float  ForceX, Y, Z                             ; constant force (gravity, wind, etc.)
Float  ScaleStart, ScaleChange                  ; size + per-frame size delta
Int    Lifespan                                 ; frames
Float  AlphaStart, AlphaChange
Int    BlendMode                                ; 3 = additive, 1 = multiply, 2 = alpha-blend
Int    Shape                                    ; RP_Sphere / RP_Cylinder / RP_Box
Float  MinRadius, MaxRadius                     ; Sphere / Cylinder
Float  Width, Height, Depth                     ; Box (and Cylinder uses Depth for length)
Int    ShapeAxis                                ; Cylinder only — 1=X, 2=Y, 3=Z
Short  DefaultTextureID                         ; Realm Crafter specific — index into Media.bb texture registry
Float  ForceModX, Y, Z                          ; force modifier (added in later)
Int    ForceShaping                             ; RP_Linear / RP_Spherical
Byte   RStart, GStart, BStart                   ; initial colour
Float  RChange, GChange, BChange                ; per-frame colour delta
```

**Format note — not SafeWrite.** `RP_SaveEmitterConfig` uses plain `WriteFile` ([`RottParticles.bb:1126`](../../src/Modules/RottParticles.bb#L1126)) — no atomic-rename, no `.bak` retention. A crash mid-save leaves a truncated `.rpc`; subsequent loads `ReadInt` past EOF and get zero-filled fields (Blitz3D doesn't error on past-EOF reads). This is a known candidate for a `SafeWriteOpen` / `SafeWriteCommit` migration following the [`reference_safewrite_migration_template.md`](../../../../.claude/projects/C--Users-dyanr-Desktop-rcce2/memory/reference_safewrite_migration_template.md) pattern. Not yet done.

**`Name$`** is derived from the file path at load time (`RP_LoadEmitterConfig` strips the directory and extension at [`RottParticles.bb:1188-1195`](../../src/Modules/RottParticles.bb#L1188)) — it's not persisted in the file. Save preserves the path-derived name on load round-trips.

### `RP_KillEmitter` — graceful vs hard free

| Function | Behavior |
|---|---|
| `RP_KillEmitter(ID, FreeConfig=False, FreeTex=False)` | **Graceful.** Sets `E\KillMode = 1..4` depending on the flag combination; `RP_Update` then waits for `ActiveParticles = 0` (i.e. every live particle to finish its lifetime) before invoking `RP_FreeEmitter` with the same flag combination. Use this for visual continuity — particles already in flight complete their animation. The 4 modes encode the (FreeConfig, FreeTex) pair: 1=(F,F), 2=(T,T), 3=(T,F), 4=(F,T). |
| `RP_FreeEmitter(ID, FreeConfig=False, FreeTex=False)` | **Hard.** Immediately deletes every `RP_Particle` belonging to this emitter, frees the `MeshEN` + `EmitterEN` Blitz entities, and `Delete`s the `RP_Emitter`. Optionally frees the config (`FreeConfig=True`) and texture (`FreeTex=True`). Use this when the emitter must vanish immediately (e.g. zone change, player disconnect). |
| `RP_FreeEmitterConfig(ID, FreeTex)` | Free a config — but defensively walks every other config sharing the same `Texture` handle and zeros their `Texture` field before `FreeTexture`, so a shared texture isn't yanked out from under a sibling config. Same defensive walk in `RP_FreeEmitter(ID, FreeConfig=False, FreeTex=True)`. |
| `RP_Clear(Configs=True, Textures=True)` | Frees **every** live emitter using the after-cursor pattern. Same hazard as `RP_Update`'s emitter walk — `RP_FreeEmitter` is called mid-iteration, so `ENext = After E` capture is required. The audit comment at [`RottParticles.bb:1403-1406`](../../src/Modules/RottParticles.bb#L1403) documents the trigger (zone change with multiple active emitters). |

### `RP_Config*` setter family — ~40 functions

The bulk of the file is small per-field setter functions: `RP_ConfigLifespan(ID, Lifespan)`, `RP_ConfigSpawnRate(ID, SpawnRate)`, `RP_ConfigVelocityX(ID, VelocityX#)` through `RP_ConfigBlendMode(ID, Blend)`. Each takes a config `ID` (Handle), looks up the `RP_EmitterConfig` via `Object.RP_EmitterConfig(ID)`, sets the corresponding field, returns `True` / `False`. All are mechanically identical — RC Architect's emitter editor UI is the primary consumer.

Three shape-specific setters (`RP_ConfigShapeSphere`, `RP_ConfigShapeCylinder`, `RP_ConfigShapeBox`) bundle the `Shape` + `MinRadius/MaxRadius/Width/Height/Depth/ShapeAxis` field updates into single calls for editor convenience.

`RP_ConfigTexture(ID, Texture, TilesX, TilesY, FreePreviousTexture = True)` is the only setter with a destructive side: by default it `FreeTexture`s the previous texture handle if no other emitter is using it (caller controls via `FreePreviousTexture`).

### Per-particle UV math + `/0` guard

`RP_SetParticleFrame(P, Frame)` computes UV coords for the particle's atlas tile via `1.0 / TexAcross` / `1.0 / TexDown`. A misconfigured (or wire-tampered) `.rpc` with `TexAcross = 0` or `TexDown = 0` would crash on `Frame Mod 0` and the float divides. The guard at [`RottParticles.bb:411-417`](../../src/Modules/RottParticles.bb#L411) bails to full-texture UV space (0,0 frame, full UV range) instead — particle still renders but does no atlas math. Same family of boundary-input defenses as CLAUDE.md → "Float sanitisation at the BVM / wire boundary" (here applied to file-supplied rather than wire-supplied data).

## Conventions for new code touching this module

- **Use `RP_KillEmitter` over `RP_FreeEmitter` for player-visible effects** — particles in flight should complete their lifetimes for visual continuity. Hard-free is for hard-cut transitions (zone change, disconnect).
- **`Object.RP_Emitter(EntityName$(ID))` is the lookup for runtime emitter handles**; `Object.RP_EmitterConfig(ID)` is the lookup for config handles. Don't accidentally use `Object.RP_Emitter(ID)` directly with a config ID — it will return Null because the EntityName$ indirection is the contract.
- **The 40-field `.rpc` schema is positional.** Adding a new field requires updating `RP_SaveEmitterConfig`, `RP_LoadEmitterConfig`, *and* the `RP_CopyEmitterConfig` deep-copy at [`RottParticles.bb:1045-1098`](../../src/Modules/RottParticles.bb#L1045). Missing any one drops the field on save / round-trip.
- **`RP_Particle` instances are recycled during normal per-frame operation, not freed.** The `InUse = False` flag is the "dead" state, and `RP_SpawnParticle` flips it back to `True` for a respawn. The exception is emitter teardown — `RP_FreeEmitter` ([`RottParticles.bb:1390-1392`](../../src/Modules/RottParticles.bb#L1390)) walks `For P = Each RP_Particle / If P\E = E Then Delete P`, deleting every particle owned by the dying emitter. New per-particle fields that need a reset on respawn should be reset in `RP_SpawnParticle` ([`RottParticles.bb:241`](../../src/Modules/RottParticles.bb#L241)), not in `RP_CreateParticle` (which runs once at slot pre-allocation in `RP_CreateEmitter`).
- **Both `RP_Update`'s emitter walk and `RP_Clear` use the after-cursor pattern.** Any new function that walks `For Each RP_Emitter` AND can free emitters mid-walk must do the same `First / After / While <> Null` shape.
- **The texture-share defensive walk** in `RP_FreeEmitterConfig` and `RP_FreeEmitter(..., FreeTex=True)` is the canonical pattern for "free a resource that might be shared across siblings." Replicate it if you add another shared-resource field.
- **`RP_SaveEmitterConfig` is a SafeWrite migration candidate.** Direct `WriteFile` to production path is the legacy shape; adopting `SafeWriteOpen` / `SafeWriteCommit` would close the truncated-on-crash failure mode. Follow the memory template.

## Related modules

- [`Projectiles3D.bb`](projectiles3d.md) — heavy consumer. `CreateProjectile` calls `RP_LoadEmitterConfig` + `RP_CreateEmitter` (up to two emitters per projectile); `FreeProjectileInstance` calls `RP_KillEmitter`. The after-cursor walk pattern in this module is the second canonical example after `UpdateProjectiles`.
- [`Spells.bb`](spells.md) — spawns visual effects via the same config-load / emitter-create / kill-on-end shape.
- [`Media.bb`](media.md) — provides the underlying `Texture` handles consumed by `RP_ConfigTexture`. `RP_FreeEmitterConfig` calls Blitz `FreeTexture` directly, not `UnloadTexture` — so freeing an emitter's texture does **not** clear `Media.bb`'s `LoadedTextures(ID)` cache slot. This is a known asymmetry; consumers who want full media-cache invalidation must call `UnloadTexture` themselves afterward.
- [`Client.bb`](../../src/Client.bb) — calls `RP_Update(Delta)` once per main-loop frame.
- [`Server.bb`](../../src/Server.bb) — also calls `RP_Update` for any server-side emitters (rare; mostly client-only feature).
- [`Logging.bb`](logging.md) — provides `SafeWriteOpen` / `SafeWriteCommit` (not yet adopted here — see migration candidate above).

## See also

- CLAUDE.md → "Iterator-during-iteration hazards" — `RP_Update` and `RP_Clear` are canonical after-cursor examples.
- CLAUDE.md → "Atomic writes" — `RP_SaveEmitterConfig` is a candidate migration site.
- CLAUDE.md → "Float sanitisation at the BVM / wire boundary" — `RP_SetParticleFrame`'s `/0` guard is the same family of defenses.
- [`projectiles3d.md`](projectiles3d.md) — the canonical consumer.

* * *

The legacy function-by-function reference for this module has not been generated. The conceptual overview above is the primary reference; consult the source at [`src/Modules/RottParticles.bb`](../../src/Modules/RottParticles.bb) for full signatures.

The module exports 55 functions across the families: 1 update loop (`RP_Update`); 4 particle internals (`RP_CreateParticle`, `RP_SpawnParticle`, `RP_SetParticleFrame`, `RP_UpdateParticleVertices`); ~40 `RP_Config*` setters covering every emitter parameter; 5 config-lifecycle functions (`RP_CreateEmitterConfig`, `RP_CopyEmitterConfig`, `RP_FreeEmitterConfig`, `RP_SaveEmitterConfig`, `RP_LoadEmitterConfig`); 8 emitter-lifecycle functions (`RP_CreateEmitter`, `RP_EnableEmitter`, `RP_DisableEmitter`, `RP_HideEmitter`, `RP_ShowEmitter`, `RP_ScaleEmitter`, `RP_KillEmitter`, `RP_FreeEmitter`); 1 query (`RP_EmitterActiveParticles`); 1 sweep (`RP_Clear`).

### <a id="rp_saveemitterconfig"></a>`RP_SaveEmitterConfig(ID, File$)`

Write the config to `File$` (typically `.rpc` extension under `Data\Emitter Configs\`). Returns `True` on success, `False` on bad ID or `WriteFile` failure. **Not atomic** — see migration candidate note above.

### <a id="rp_loademitterconfig"></a>`RP_LoadEmitterConfig(File$, Texture, FaceEntity)`

Read the config from `File$`. `Texture` is the Blitz texture handle to bind (NOT loaded from the file — caller's responsibility, typically via `GetTexture` from [`Media.bb`](media.md)). `FaceEntity` is the camera handle for billboarding. Returns a `Handle(C)` to the new `RP_EmitterConfig` on success, `False` on `ReadFile` failure. Name is derived from `File$`'s basename.
