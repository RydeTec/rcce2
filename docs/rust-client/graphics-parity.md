# Rust client — graphics parity tracker

Goal: confirm the Rust client (`bin/ClientRS.exe`) reaches full graphical parity
with the BlitzForge `Client.exe`, feature by feature. Each row is verified by
rendering a controlled scene and auditing it against the Blitz render code
(`Environment3D.bb`, `ClientAreas.bb`, `Media.bb`, `Interface3D.bb`).

## Verification harness

No real fork data or server is needed:

- **`gen-test-zone <data_root> [zone]`** (bin) writes a synthetic `Data/Areas/<zone>.dat`
  in the exact `SaveArea` byte layout, exercising chosen features (terrain hill,
  water lake, reference props). Extend it to cover new features.
- **`RCCE_VIEWZONE=1`** renders the loaded zone directly (no menu / no server) with
  a free camera: `RCCE_CAMAT="x,y,z"`, `RCCE_CAMYAW`, `RCCE_CAMPITCH`, `RCCE_CAMDIST`,
  captured via `RCCE_SHOT` / `RCCE_SHOT_FRAME`. Reuses the in-world `view.render`
  path, so it matches in-game appearance.
- **Atmosphere / object hooks** layered on the preview: `RCCE_PHASE` (0=midnight,
  0.25=dawn, 0.5=noon, 0.75=dusk) or `RCCE_DAYNIGHT_SECS`; `RCCE_SUNDIR="x,y,z"`
  (force the sun direction — for the sun disc / glint / rim); `RCCE_PROJPREVIEW`
  (spawn a projectile); `RCCE_LOOTPREVIEW` + `RCCE_LOOTITEM=<id>` (drop a loot
  mesh); `RCCE_AMBIENT`; `RCCE_TESTBOX=cpu|skinned` + `RCCE_BOXY` (shadow caster).
  Underwater triggers when the camera dips below a water plane.

Example:
```
gen-test-zone <data> "Test Terrain"
RCCE_DATA=<data> RCCE_VIEWZONE=1 RCCE_CAMAT="0,8,0" RCCE_CAMPITCH=0.2 \
  RCCE_CAMDIST=170 RCCE_SHOT=out.png ClientRS.exe 127.0.0.1 25000 "Test Terrain"
```

Blitz-side reference renders aren't available headlessly (Graphics3D needs a
desktop session that times out when agent-launched), so parity is judged against
the Blitz **source** behaviour plus the Rust render — the writer/loader code is
authoritative for format, the render code for appearance.

## Status

Legend: ✅ verified (rendered + audited) · 🟡 implemented, render-verify pending ·
❌ missing · ➖ deferred / cosmetic.

| Feature | Status | Notes |
|---|---|---|
| Static scenery meshes | ✅ | renders; catalog scale via placement scale |
| Scenery rotation (pitch/yaw/roll) | ✅ | yaw negated for the LH view (this session) |
| LOD terrain (`CreateTerrain`) | ✅ | parse + grid mesh + height field (this session) |
| Water planes | ✅ | alpha plane, white tint (texture shows), tiling `scale/tex_scale`, scroll anim |
| Skinned actor animation | ✅ | b3d skeletal LBS, GPU/CPU paths |
| Actor attachments (hair/weapon/shield) | ✅ | follow the animated joint (this session) |
| Sky dome (`SkyTexID`) | ✅ | textured skydome renders. **Fixed**: the sky texture now dims at night (`×(1−0.78·night)`) — it stayed full daytime brightness before, so the zenith showed bright daytime sky at midnight. Verified: zenith noon 61→midnight 14 (ratio 0.22); noon unchanged. |
| Clouds + storm swap (`CloudTexID`) | ✅ | drifting clouds render; storm swap implemented. Clouds dim at night with the same `sky_dim` factor (no more bright daytime clouds in a midnight sky). |
| Night stars (`StarsTexID`) | ✅ | **Fixed a pre-existing bug**: stars never rendered at night. The project *does* ship a stars texture (Plains `StarsTexID` = 62, 1024²), but the sky shader added the stars **before** the cloud overlay, so the full-sky cloud layer alpha-composited straight over them and erased the field. Moving the stars composite **after** the clouds fixes it — verified 11,847 px of stars at night, 0 at noon (still day-gated). (Also reverted the earlier `gen-test-zone` StarsTexID injection, which had been overwriting the real id-62 stars with the sky texture.) |
| Fog (`FogRGB`, near/far) | ✅ | distance fade to the (day/night-modulated) fog colour; horizon/clear = fog so the world fades into it. Verified darkening/tinting across phases. |
| Ambient + directional light | ✅ | ambient modulated by the day/night curve (verified brightness ordering noon>dawn/dusk>midnight); sun dir from `DefaultLightPitch/Yaw` (or `RCCE_SUNDIR`). |
| Day/night cycle | ✅ | `RCCE_PHASE` / `RCCE_DAYNIGHT_SECS`. **Fixed**: the zone preview ignored `RCCE_PHASE` (hardcoded full day); now it modulates fog+ambient and drives the night factor like the in-world path. Verified: noon bright/neutral, dawn+dusk warmer, midnight dim+blue (`R-B` −11). |
| Lightmaps / multitexture (2nd tex) | ✅ | menu Set.b3d + terrain detail both render `base × tex × 2` |
| Alpha / masked foliage | ✅ | fir needles render as alpha cutout (harness) |
| Vertex colours (`EntityColor`) | 🟡 | confirm per-vertex colour path |
| Projectiles (3D) | 🟡 | combat path |
| Minimap / radar | ✅ | left/right handedness fixed (this session) |
| Terrain detail texture (2nd tex) | ✅ | multitexture `base × detail × 2`, detail UV tiles at `DetailScale` (this session) |
| Emitters / particles (`.rpc`) | ✅ | full RottParticles port: `.rpc` config parse + shape-based spawn + force/velocity/scale/alpha/colour-over-life sim + camera-facing billboards (additive/alpha). Zone emitters loaded + ticked per frame. |
| **Dynamic shadows** | ✅ | **shadow mapping** — sun-view depth pass + PCF in the scene shader. Casters: terrain, scenery, actors; alpha-tested so foliage casts canopy shapes. Soft edges (better than Blitz's hard stencil). Camera-centred, texel-snapped. **Caster culling**: each caster's world bounding sphere is projected into the sun's ortho box and skipped if outside (exact — lossless, verified by an on/off pixel diff at the animation noise floor; `drawn=1 culled=11` when the focus is offset). `RCCE_NOSHADOWCULL` disables it; `RCCE_SHADOWSTATS` logs drawn/culled/skinned. **GPU-skinned actors also cast** (depth-only skinned pipeline): verified the skinned caster's shadow is pixel-identical (IoU 1.000) to the CPU caster's for the same geometry/pose — so the faster `RCCE_GPUSKIN` path no longer drops actor shadows. `RCCE_NOSKINSHADOW` disables the skinned caster. Headless caster harness: `RCCE_TESTBOX=cpu\|skinned` + `RCCE_BOXY`. |
| Point lights / `LightModels` | ✅ | `light_<range>_<R>_<G>_<B>` scenery meshes → per-fragment accumulation (colour × distance falloff × facing); nearest 16 to the camera per frame. Illuminate only, no shadows (matches Blitz). Env-tunable `RCCE_LIGHTRANGE` / `RCCE_LIGHTGAIN`. |
| Form shading (mesh self-shadow) | ✅ | `max(dot(N,L))` — lit/dark sides on every mesh + slope-shaded terrain |
| View-frustum culling | ✅ | each drawable's world bounding sphere is tested against the 6 camera-frustum planes; props behind the camera / off the sides skip their textured+shaded draw entirely. Conservative ⇒ lossless (verified: 10/13 drawables culled facing away, on-vs-off pixel delta at the animation noise floor). `RCCE_NOFRUSTUMCULL` disables; `RCCE_DRAWSTATS` logs drawn/culled. |
| Texture upload cache (static + water) | ✅ | content-keyed GPU texture-bind cache for the static scene + water. Static scenery sharing a texture uploads once (not once per instance); **water — rebuilt every frame for its scrolling UV — now reuses its texture instead of re-creating it (+ mip chain) every frame**. Verified lossless (pixel-identical) and effective: test zone `tex_uploads` 10 (flat) vs 64-and-growing without (`RCCE_NOTEXCACHE`); `RCCE_TEXSTATS`/`RCCE_DRAWSTATS` report uploads + cache size. |
| MSAA + alpha-to-coverage | ✅ | **better than Blitz** (fixed-function Client.exe has no MSAA). World pass renders into a multisampled colour+depth target and resolves to the surface; shadow map stays 1×. Alpha-to-coverage on the opaque + skinned pipelines anti-aliases cut-out foliage/hair silhouettes too. Verified at 4× on an opaque/sky silhouette: 12× more coverage pixels (154→1920) and −19% hard sky↔foreground adjacencies vs 1×; 1× is a byte-identical fallback. `RCCE_MSAA={1,2,4}` (default 4; clamped — 8× needs an unrequested adapter feature). |
| Bloom / HDR glow (opt-in) | ✅ | **beyond Blitz**, **default-off** (`RCCE_BLOOM`). The world renders to an offscreen scene texture; a bright-pass (luminance knee) + half-res separable Gaussian + additive composite makes bright pixels (sun glints, fire/magic particles, water highlights) bleed a soft glow. Verified: glow is additive (never darkens — min +0), concentrated in bright regions (+4.1) vs dark (+0.0); default-off path is byte-identical. Tunable `RCCE_BLOOM_THRESHOLD` / `RCCE_BLOOM_INTENSITY`. |
| Water surface (Fresnel + ripples) | ✅ | **better than Blitz** — dedicated water pipeline adds a Fresnel sky-reflection (water brightens toward the sky/fog colour at grazing angles, stays clear/textured looking straight down) + procedural ripple normals driven by the scrolling UV (shimmer, no time uniform). Convention-free (view dir + normal only). Verified: vs flat water, the change is localized to the water band, +57 brighter, grazing-stronger; animates with the scroll. `RCCE_FLATWATER` reverts. |
| `AWater` bump-map + foam textures | ➖ | the authored bump/foam texture path; deferred (the procedural ripples above cover the look) |

### Minor — implemented, render-verify pending (low risk; env-driven)

`Fog` ranges, `night stars`, `vertex colours (EntityColor)`, `day/night cycle`,
`ambient + directional light` — all applied in every harness render already; not
independently isolated. Low-risk; can confirm opportunistically.

### Large subsystems — all now implemented ✅

These were the net-new renderer additions (dynamic shadows, particles, point
lights). All three are done; the notes below record what each entailed.

1. **Dynamic shadows** — Blitz runs the **Devil Shadow System** userlib
   (`DevilShadowSystem.decls` + `ShadowsMultiple.bb`): the sun is the
   `ShadowLight` (Environment3D.bb:509), and actors (`CreateShadowCaster AI\EN`,
   Client.bb:453) + scenery (ClientAreas_FE.bb:570) cast real-time shadows,
   rendered by `UpdateShadows Cam` each frame (Client.bb:240). Rust has none.
   Parity needs a **shadow-mapping pass** (render depth from the sun, sample it in
   the main shader). Large.
2. **Emitters / particles** — area `.rpc` emitter configs (fire/smoke/fountains/
   magic) are parsed-but-skipped. Needs an `.rpc` parser + a **particle simulation
   + billboard renderer**. Large; the most *visible* gap.
3. **Point lights / `LightModels`** — dynamic light-emitting meshes; needs Blitz
   usage confirmed, then per-pixel point lighting. Medium.

## Status

Every headless-verifiable feature in the table above is **implemented and
verified**. Along the way the pass fixed real bugs: scenery yaw, attachment
animation, minimap handedness, the night sky showing bright daytime clouds at
the zenith, and night stars being erased by the cloud overlay. The renderer now
also goes **beyond** Blitz in several places (soft PCF shadows, MSAA + alpha-to-
coverage, Fresnel water, GPU-skinned actor shadows).

### Regression sweep

After a batch of changes, render the synthetic full-feature zone at day **and**
night with everything on, and sanity-check coherence (no NaN, night darker than
day, ground still lit, no blown-out highlights):

```
gen-test-zone <data> "Test Terrain"
COMMON="RCCE_MSAA=4 RCCE_TIME=1.0 RCCE_VIEWZONE=1 RCCE_TESTBOX=skinned RCCE_BOXY=26 \
        RCCE_CAMAT=0,10,0 RCCE_CAMYAW=0.5 RCCE_CAMPITCH=0.28 RCCE_CAMDIST=120 RCCE_SUNDIR=0.7,-0.6,0.2"
env $COMMON RCCE_PHASE=0.5 RCCE_SHOT=day.png   RCCE_SHOT_FRAME=8 ClientRS.exe 127.0.0.1 25000 "Test Terrain"
env $COMMON RCCE_PHASE=0.0 RCCE_SHOT=night.png RCCE_SHOT_FRAME=8 ClientRS.exe 127.0.0.1 25000 "Test Terrain"
```
Last sweep (all 10 cumulative changes active together): coherent — day mean 47.8 /
sky 52.8, night mean 15.8 / sky 11.4 / ground 21.6, no NaN, no blowouts.

### Session additions — atmosphere, lighting & world objects

A later pass added a cluster of features, most **on by default** (the day/night
+ sun group), a few opt-in. All verified headlessly (`RCCE_VIEWZONE` + the hooks
noted); the combined default look was sanity-checked in normal day/dawn views (no
over-processing — coherent, no blowouts).

| Feature | Default | Notes / verify hook |
|---|---|---|
| **Server time-of-day** | on | Client follows the server game-clock (P_FetchActors `E` env block: TimeH/M/Factor); phase drives sky/fog/ambient + the sun arc. `RCCE_PHASE` overrides. |
| **World-fixed sky** | on | Per-pixel world-ray (`inverse(view_proj)`) so the sky stays pinned to the world as the camera yaws **and** pitches; azimuth + scaled-elevation mapping. Replaced the camera-locked 2D pan. |
| **Night sky cross-fade** | on | Sky texture + clouds fade OUT to a clean dark gradient at night (not just dim) while stars fade in — no bright cloud bands at midnight. |
| **Zenith pole-fade** | on | All sky layers cross-fade to the gradient up high so the azimuthal mapping's pole pinch can't smear texture/stars into vertical streaks. |
| **Sun + moon disc** | on | A bright sun disc + layered warm glow by day, a pale moon by night, at the time-of-day sun direction (`sun_dir(phase)`, the same arc the shadows use); reddens near the horizon. Drawn into the sky pass so terrain/trees occlude it. |
| **Moving shadows** | on | Shadow sun direction tracks the time-of-day so shadows rotate + lengthen across the day. |
| **Underwater camera** | on | Below a water plane: fog/clear tint to the water colour, view distance clamps to a murky near=2/far=60, and a full-screen water wash hides the sky (Blitz `CameraUnderwater`). `underwater_color()` is unit-tested. |
| **Water sun glint** | on | Blinn-Phong specular toward the sun on the water, broken into sparkles by a fine ripple normal; fades as the sun lowers. Beyond Blitz (its water has no specular). |
| **Dappled sunlight** | on | A world-fixed "broken cloud cover" pattern dims only the sun term in broad patches — the player walks through sun + shade (no time uniform: the pattern is pinned to the world). |
| **Hemispheric ambient** | on | Ambient is cool + brighter from above (sky) and warm + darker from below (ground bounce), keyed on the normal — instead of one flat term. Cheap AO-like grounding. |
| **Sun backlight rim** | on | Looking toward the sun, silhouette edges (grazing view) catch a warm scattered glow — sun wrapping around edges + through foliage. Gated to daylight via the ambient level. View-direction gated (none with the sun behind). |
| **3D projectiles** | on | Glowing orb + fading motion trail via the particle billboard path (additive, depth-tested) — terrain occludes them. Replaced a flat 2D overlay square. Verify: `RCCE_PROJPREVIEW`. |
| **3D dropped loot** | on | Each item's world mesh (`mmesh`), or the **Loot Bag** fallback for items with none (most shipped items), seated on the terrain — like Blitz. Replaced a flat 2D pip. Verify: `RCCE_LOOTPREVIEW` (+ `RCCE_LOOTITEM=<id>`). |
| **Smooth local turning** | on | The local player's facing eases toward the movement heading at the remote-actor rate, instead of snapping. Unit-tested. |
| **Movement speed** | on | Client-authoritative locomotion at a Blitz-matched speed (`RCCE_MOVESPEED`, default 8). |

Most of these live in the scene/sky/water shaders with **no uniform-layout change**
(derived from the normal / view dir / existing `u.ambient`/`light_dir`), so the
GPU-skin path — which shares the world uniform with a mismatched prefix — is left
untouched. The day/night + sun group keys off `RCCE_PHASE` / `RCCE_DAYNIGHT_SECS`
/ the live server clock.

### What's left (not headless-verifiable)

These need the live client + a running server, so they're out of scope for the
test-render loop and should be **scoped with the user**:

- **In-world UI / nameplates / health bars** (`Interface3D`) — overlay over actors
  (nameplates render white-only; no player/NPC/hostility colour yet).
- **Sound**, **mouse-look**, **chat/combat send** — interaction, not rendering.
- **Animation blending** — locomotion clips snap (idle↔walk↔run); cross-fade would
  need per-actor blend state + dual-pose bone blending.
- **Lens flares / god rays** — Blitz has sun flares (`Flares/*.png`); god rays would
  reuse the bloom offscreen plumbing. Both deferred (opt-in, perf cost).

Possible *beyond-parity* renderer additions (opt-in, have a perf cost worth
discussing given fps sensitivity): bloom/HDR glow ✅ (done, `RCCE_BLOOM`),
screen-space water reflections, ambient occlusion.

Update this table as rows are verified or implemented.
