# Rust Client — Exhaustive Parity Delta vs Blitz `Client.exe`

**Date:** 2026-06-01 · **Branch:** `coreyrdean/blissful-knuth-1accbd`
**TARGET:** `bin/Client.exe` (BlitzForge — `src/Client.bb` + `src/Modules/*.bb`)
**SUBJECT:** `bin/ClientRS.exe` (Rust/wgpu — `client-rs/crates/*`)

This is a feature-by-feature audit of how far the Rust reimplementation is from the Blitz
reference, produced by six parallel cross-codebase audits (one per subsystem). Every row cites
the Blitz source behavior and the Rust evidence (file:line). Status legend:

> **Regression verification — 2026-06-02 (after 18 parity commits, head `7933be13`).** Fresh
> `cargo build --release -p rcce-client`: **zero warnings**. Tests: **148 green** (42 rcce-data +
> 80 rcce-client lib + 26 client-window bin). Full boot sequence captured headless and read,
> no regressions: **EULA** (`EULA.PNG` backdrop + license text + Accept/Decline), **Login**
> (`Login.PNG` backdrop + account field), **Sound Options** (volume bar + mute + Tab→Controls),
> **Controls** (full keybind reference), **in-world** (textured terrain, both actors grounded &
> correctly-facing, full HUD). The original user-reported defects — mirrored world, smeared
> terrain, floating actors, no blending, stuck dialog / ESC-kills-client — are all resolved.

| Status | Meaning |
|---|---|
| **DONE** | Behavior matches the Blitz reference (verified against source + evidence). |
| **PARTIAL** | Implemented but incomplete — missing sub-cases, wrong thresholds, or degraded fidelity. |
| **DIVERGENT** | Implemented but behaves differently from the reference (often an intentional substitute). |
| **MISSING** | Not implemented at all. |

> Many MISSING rows are **content-gated** — they need a world feature (water volume, mountable
> NPC, second connected player, scripted emote) that the starter `data/` project doesn't exercise.
> Those are tagged **[content-gated]** and are *not* the same class of gap as a missing core system.

---

## 1. Scorecard

| Subsystem | DONE | PARTIAL | DIVERGENT | MISSING | Features | Weighted parity¹ |
|---|---:|---:|---:|---:|---:|---:|
| Rendering | 16 | 13 | 4 | 22 | 55 | ~43% |
| Networking (packets) | 46 | 2 | 1 | 28 | 77 | ~62% |
| UI / HUD / Menus | 25 | 18 | 2 | 12 | 57 | ~61% |
| Movement / Camera / Animation | 5 | 13 | 6 | 7 | 31 | ~44% |
| Combat / Spells / Items / Inv / Trade | 22 | 16 | 7 | 27 | 72 | ~46% |
| Audio / Weather / Input / Loc / Misc | 14 | 8 | 7 | 15 | 44 | ~46% |
| **TOTAL** | **128** | **70** | **27** | **111** | **336** | **~51%** |

¹ Weighted parity = (DONE·1 + PARTIAL·0.5 + DIVERGENT·0.25) / Features. A rough "how-much-of-the-target-behavior-exists" number; DIVERGENT scores low because the behavior is present but wrong.

**Headline read:** the *plumbing* is strong — login, world-state replication, movement echo,
the packet codec, and the core render path all work, which is why the client is playable. The
distance to target is concentrated in (a) **environment richness** (water, 3D particle emitters,
multitexture/lightmaps, sky bodies), (b) **the front-of-game shell** (EULA, loading screen,
options/control-remap menus, menu music), (c) **interaction depth** (mouse-carry inventory, full
trade, item-use beyond eating, ranged combat), and (d) a small set of **functional blockers**
that hurt out of proportion to their count.

---

## 2. Top blockers (fix these first — highest impact per unit effort)

1. ~~**In-world ESC kills the client.**~~ **FIXED** (commit `834196c5`, UI-ESC). `esc_layer` precedence
   fn closes the topmost open layer; only exits when nothing is open.
2. ~~**NPC script-input / progress-bar does nothing.**~~ **FIXED** (TGT-8). `P_ScriptInput` (free-text
   modal + reply) and `P_ProgressBar` (`C`/`U`/`D` + create-ack) now parse, render, and close via ESC.
   *(The NPC **dialog** window — `P_Dialog` — was already DONE, TGT-5; the remaining stuck-window path
   was the unhandled `P_ScriptInput`.)*
3. ~~**No camera zoom of any kind.**~~ **FIXED** (CAM-3). `cam_dist` field + `MouseWheel` handler +
   `-`/`=` keys, clamped [5,50] via `zoom_step`, threaded into the third-person boom.
4. ~~**No client-side body yaw (`me_yaw`).**~~ **FIXED** (MOVE-FACE). The local body now predicts and
   faces its movement direction every frame (`heading_from_dir`, degrees); also fixed a latent
   degrees-as-radians unit bug in `first_person_view`/`snap_camera` (CAM-4/CAM-5). *(Turn keys still
   steer the camera, not the body — the camera-relative scheme is intentional; movement-facing was the
   load-bearing gap.)*
5. ~~**Item-use / ranged combat.**~~ **DONE (fully closed).** Item-use beyond `P_EatItem` sends
   `P_ItemScript` (`ITEM_SCRIPT=43`); ranged-weapon `MaxRange` works (`effective_attack_range`,
   ranged → `range−0.5` when item-health > 0); and the I_Image `WItemWindow` popup now renders the
   item image in a centred ESC-closeable modal (PNG-verified). Talk paths covered (chat send exists).
6. **Front-of-game shell — substantially addressed.** Menu music (`Menu.ogg`) **DONE**; the **EULA /
   license screen DONE** (`Mode::Eula` — the user-flagged "license agreement on startup", PNG-verified).
   The **Sound options** (`Mode::Options`, F1), **Controls reference** (`Mode::Controls`, Tab), and the
   **menu backdrop art** (EULA→`EULA.PNG`, Login/Options/Controls→`Login.PNG`) are now **DONE** (all
   PNG-verified; Login works because it has no 3D char). Still open: Graphics options, keybind *remapping*,
   backdrop behind the CharSelect 3D scene, Set.b3d menu diorama, Server-Selector. (Loading screen skipped — fast sync loads.)

---

## 3. Rendering (16 / 13 / 4 / 22)

Headline structural gap: **`area.rs:163` only parses scenery placements.** Water volumes, ColBoxes,
3D Emitters, LOD Terrains, and Sound blocks live in the same `.dat` and are **never read** — this one
omission accounts for missing water, 3D particles, and LOD heightmap terrain simultaneously.

| Feature | Blitz (src) | Status | Gap | Rust evidence |
|---|---|---|---|---|
| Textured terrain | LoadArea mesh + brush | DONE | Texscale tiling fixed (6a47f81d) | `world_view.rs`, `b3d.rs` |
| Terrain vertex-alpha splat blend | FX_VERTEXALPHA overlays | DONE | Two-pass opaque+alpha (1166bb96) | `gpu.rs alpha_pipeline` |
| Mipmaps + anisotropy | engine default | DONE | CPU mip chain + aniso 8 (80215326) | `gpu.rs texture_bind` |
| Scenery placement | LoadArea scenery loop | DONE | — | `area.rs:163` |
| Skinned animated actors | b3d BONE/KEYS/ANIM | DONE | CPU LBS; quat-conjugate fix | `b3d.rs`, scene |
| Third-person camera (LH) | gxscene | DONE | Mirror fixed (08a499be) | `world_view.rs` |
| Daylight default | Environment | DONE | Default phase fixed (e4c8957c) | `daynight.rs` |
| Fog (day/night) | FogNear/Far | PARTIAL | Per-*weather* fog targets missing | `world_view.rs` |
| Multitexture / lightmap / BUMPED stages | brush slots 2-8 | MISSING | b3d keeps only first brush slot | `b3d.rs resolve_textures` |
| Water surface | Water planes | DONE | `area.rs` now parses the water block after the scenery list (`WaterPlane`: tex/scale/pos/size/rgb/opacity, ClientAreas.bb:546); each renders as a flat textured, alpha-blended quad (`water_quad`) seated at its Y, UVs tiled `size/TexScale` (Blitz `ScaleTexture`). No RGB tint — Blitz never `EntityColor`s the water entity, so the surface is the texture at `opacity` alpha. Now **animated**: rendered per-frame via a dedicated water pass with a scrolling UV offset (Blitz PositionTexture, U+=0.0075/s V+=0.021/s) so the surface drifts. Reflection/refraction + bump/foam (Blitz AWater=1) + wave subdivision still not done (cosmetic). | `area.rs` / `load_zone_static` |
| 3D particle emitters | RP_ emitters | MISSING | Rust draws 2D screen rects | `weather.rs` |
| LOD heightmap terrain | LOD Terrains block | MISSING | Not parsed | `area.rs:163` |
| Shadows | shadow projection | MISSING | None | — |
| MSAA / AA | engine | MISSING | None | `gpu.rs` |
| Suns / moons / lens-flare / god-rays | sky bodies | MISSING | None | — |
| Storm-cloud texture swap | EntityTexture | DONE | (also weather audit) | `client_window.rs:3004` |
| Attachment anim-follow | bone-bound mesh | DIVERGENT | Binds to bind-pose joint, not animated | actor render |
| Day/night clock | 60000/TimeFactor | DIVERGENT | Synthetic cosine, no server time/seasons | `daynight.rs` |

*(Full 55-row table retained in audit transcript; condensed here to the load-bearing rows + every MISSING system.)*

---

## 4. Networking — packet coverage (46 / 2 / 1 / 28 of 77)

Wire codec confirmed **little-endian both sides**; login flow fully covered. Gaps are concentrated
in outbound interaction and inbound live-update packets.

**Outbound MISSING (client can't initiate):**
`P_ChatMessage` (talk / slash-commands), `P_AttackActor` *(present but range-limited)*,
`P_ActionBarUpdate`, `P_Trade` / `P_UpdateTrading` (P2P trade), `P_ScriptInput` reply.

**Inbound MISSING (server events ignored):**
`P_AppearanceUpdate`, `P_AnimateActor`, `P_RepositionActor`, `P_Sound`, `P_Speech`, `P_Music`,
`P_CreateEmitter`, `P_KickedPlayer`, `P_StatUpdate "R"`, `P_ScriptInput`, `P_ProgressBar`.

| Class | Status | Notes |
|---|---|---|
| Login / char-select / enter-world | DONE | Full handshake, account, char CRUD |
| P_StandardUpdate (move echo) | PARTIAL | 110ms throttle vs 200; WalkBack byte hardcoded false |
| P_Projectile / P_AttackActor (in) | DONE | Decode correct |
| P_Sound / P_Speech / P_Music (in) | MISSING | No dispatch case → silent combat/cast/speech |
| P_AnimateActor / P_CreateEmitter (in) | MISSING | Scripted anim/particles ignored |
| P_UpdateTrading | MISSING | Player-trade window never syncs |
| Day/night time sync | DIVERGENT | Client-synthetic, ignores server clock |

---

## 5. UI / HUD / Menus (25 / 18 / 2 / 12)

HUD at real `Interface.dat` coords (vitals/chat/minimap/buffs/action-bar/inventory-grid) — strong.
Gaps are the front-of-game shell and modal dialogs.

| Feature | Status | Gap |
|---|---|---|
| HUD layout at real coords | DONE | Matches Interface.dat (memory note) |
| Inventory / quest / party / spellbook panels | DONE | PANEL TEMPLATE |
| **In-world ESC** | DIVERGENT | **Kills client with any panel open — top blocker** |
| **NPC dialog / P_ScriptInput** | MISSING | **Clicking option does nothing — top blocker** |
| P_ProgressBar dialog | MISSING | No render |
| EULA / license screen | MISSING | Front-of-game |
| Loading screen | MISSING | Synchronous zone load |
| Options menus (Graphics/Control/Sound/Other) | MISSING | No settings UI at all |
| Menu backdrop art + Set.b3d diorama | DONE | EULA/Login/Options/Controls draw their real backdrop PNGs; the `Data\Meshes\Character Set\Set.b3d` diorama (32 meshes) renders as the 3D backdrop behind every menu screen, framed to match the Blitz char-select reference: the camera looks **into** the furnished room (table / rug / tapestry / framed picture) with the character full-body, front-facing, on the right. **Scale correction:** Blitz's literal `ScaleEntity 30` over-scaled the set ~20× because the RCCE Rust pipeline runs at a much smaller world scale (actor render scale ~0.05); the set is now scaled `MENU_SET_SCALE`=1.0 with its origin derived from the scale to keep the character on the rug (`origin = char − scale·MENU_SET_RUG`), Blitz fog (`0,51,102`, range 300/5200). Camera/char framing baked in `MENU_CAM_*` / `MENU_CHAR_Y`, all override-tunable via `RCCE_SETSCALE`/`RCCE_SETY`/`RCCE_CHARY`/`RCCE_MENUDIST`/`RCCE_MENUEYEH`/`RCCE_MENUTGTH`/`RCCE_MENULAT`/`RCCE_MENUANG`. Char-select roster panel moved flush-**left** + tall (Blitz layout) so the character fills the open right area, centered and full-body. Background set to a dark near-neutral (was dark-blue, which showed as a **teal triangle** in the void past the floor edge); ambient lowered + warmed with a frontal key light (`RCCE_MENUBG`/`MENUAMB`/`MENULX/LY/LZ`). The interior also **skips the auto green terrain ground plane** (`build_drawables` now takes a non-finite `ground_y` to skip it) — that dark-green base was showing as a void past the set's own floor edge; the set carries its own floor. **Character seating:** the body is seated on the set's actual floor, not a guessed anchor Y — a height field is built from the set's ground triangles and the character stands on the **lowest** surface under its X/Z (`HeightField::lowest_at` — the rug, since `height_at` returns the highest and would pick the ceiling vault at ~6.3). The rug is at Y≈−0.04; the old centered anchor put the feet at ≈−1.33, sinking the lower legs ~1.3 below the floor (the reported clipping). `MENU_CHAR_Y` is now only the camera's vertical focus base (framing the seated body full-length with headroom), not the seat height. **Baked lightmap — DONE:** the scene renderer now samples the brushes' 2nd `BRUS` texture slot (the baked lightmap, e.g. `cset_lightmap.png`) with the mesh's 2nd VRTS UV set and multiplies it onto the base (`lm*2`, Blitz multiply2x), so the menu room shows its real warm baked lighting (31/32 set meshes are lightmapped). Non-lightmapped meshes (world scenery, actors) bind a 1×1 grey `0.5` default → `lm*2 = 1.0`, unchanged. Spans `rcce-data` (b3d `uvs2` + 2nd-slot `lightmap` resolve), `rcce-client` (`mesh_by_path` lightmap load), `rcce-render` (`Vertex.uv2`, `bgl_texture` binding 2, `texture_bind_lm`, `SceneInstance.lightmaps`, shader). |
| Server status / selector | MISSING | — |
| Action bar slot-assign UI / paging | MISSING | Fires on number keys, no slots model |
| Action bar key binding | DIVERGENT | Digits 1-9, reference uses F-keys / assignable |

---

## 6. Movement / Camera / Animation (5 / 13 / 6 / 7)

Core architectural divergence: **Blitz projects a destination along the character's own yaw**;
**Rust uses camera-relative WASD velocity** and never writes a local `me_yaw`. That single design
choice produces most of the DIVERGENT ratings.

| Feature | Status | Gap |
|---|---|---|
| Click-to-move + stop-at-2.0 | DONE | — |
| Jump physics + P_Jump | DONE | — |
| MMB snap-behind | DONE | — |
| Idle fidget (1/1000) | DONE | 2 clips vs random range |
| Local jump anim | DONE | — |
| Forward/back/turn keys | DIVERGENT | Camera-relative, not dest-projection along body yaw |
| Strafe A/D | DIVERGENT | *Added* behavior absent from target |
| Local gravity / ground-Y | DIVERGENT | Terrain-sampled, not gravity sim |
| Clip hysteresis (CurrentSeq) | DIVERGENT | Re-derives from elapsed each rebuild |
| **Camera zoom** | MISSING | **No wheel/keyboard zoom; dist=13 hardcoded — top blocker** |
| Actor movement (Blitz `UpdateActorInstances` parity) | DONE | Positions are server-echo-only (~9 Hz). The first pass (exponential ease toward the stale echo) still stuttered. Now matches Blitz's dead-reckoning model via **velocity extrapolation**: estimate each actor's velocity from successive `P_StandardUpdate` positions and extrapolate the render position along it between echoes (continuous motion at the server's true speed — no Speed-stat needed; the spawn Speed value is 0 anyway), gently reconcile toward the authoritative position (`RECON_RATE`), and face the travel direction. The local player extrapolates in the **input** direction at the estimated speed for instant response, stopping the moment input releases. Velocity clamped + lightly smoothed against spawn/teleport spikes; >30u jumps snap. Camera + body both read the render position (lockstep, no rubberband). `dyn_hash` keys on the render position ×16 so CPU-skin rebuilds track the glide while moving. ~60 fps. Surge-stall fix: reconcile toward the **extrapolated** server position (`echo + v·t_echo`, a smoothly-moving target) — reconciling toward the frozen-between-echoes echo made it lurch point-to-point. Velocity now uses the **real** echo interval (`t_echo`), not a guessed 0.11 (the estimate had been ~1.5× off). Verified: render tracks within ~0.5u, speed estimate matches the server. |
| Remote actor facing (rotation) | DONE | `P_StandardUpdate` omits yaw, so NPCs (e.g. the stag) never turned. `render_yaw` is now eased toward the travel heading (`-dx atan2 -dz` from `dest − pos`) while moving. |
| Nameplate / target reticle height | DONE | Projected the raw server `a.y` (collision pivot, well above the body) so the HP bar / name / selection reticle floated high over the actor. Now project the terrain-seated feet (`height_at(render_x, render_z)`) at the smoothed render x/z, matching the rendered body. |
| Camera scenery-collision LinePick | PARTIAL | Occluder spheres substitute |
| First-person at Head joint + pitch ease | PARTIAL | Fixed eye height, no pitch ease |
| Fly/swim up-down keys | MISSING | [content-gated] Space rebound to attack |
| Swim / mounted-rider anims | MISSING | [content-gated] no water/mount state |
| **P_AnimateActor (emote/scripted)** | MISSING | Remote scripted anims never play |
| Seasons | MISSING | No season state |

---

## 7. Combat / Spells / Items / Inventory / Trade (22 / 16 / 7 / 27)

| Feature | Status | Gap |
|---|---|---|
| Melee auto-attack loop | DONE | Faithful chase→swing→wait |
| Attack-send P_AttackActor | DONE | RuntimeID LE u16 |
| Projectile homing / impact / speed | DONE | Math matches |
| Memorise / un-memorise (M/U) | DONE | SPL-4 |
| Known-spells A/D/L | DONE | SPL-7 |
| Equip + mesh attach | DONE | R_Hand/L_Hand |
| Drop / pickup / durability | DONE | — |
| 46-slot equip+backpack model | DONE | — |
| **Ranged-weapon MaxRange** | MISSING | **Ranged classes can't attack at range — melee 4.5 hardcoded** |
| **Item use beyond eat** | MISSING | Image-item + script-item (`P_ItemScript`) paths absent |
| DamageInfoStyle chat-line ("You hit X for N") | DONE | `Combat.dat` style byte; style 2 → green/red/blue chat lines (out/in/miss), suppresses floaters; live-PNG-verified |
| Incoming/outgoing damage colour | DIVERGENT | Coloured by damage-type, not hit direction |
| SpellCharge server contract | DIVERGENT | Client-only timer, ignores server SpellCharge[] |
| RequireMemorise=false (cast known directly) | MISSING | Always assumes memorise required |
| Cast from action-bar slots / F-keys | DIVERGENT | Digit keys → Nth memorised |
| Own-cast visual FX | MISSING | [SPL-8] no own-cast effect |
| Stack rules (Ctrl=all / Shift=1 / amount dialog) | MISSING | Fixed amount 1 |
| Stack 16-bit ceiling | PARTIAL | Caps at 65535, not 32767 |
| Mouse-carry inventory (cursor item, swap, split) | MISSING | Direct equip/drop only |
| Trade — sell side / amounts / cost / P2P sync | MISSING | Buy-only; `P_UpdateTrading` unhandled |
| Blood-spurt / parry / hit-react / death-msg | MISSING | Cosmetic combat feedback |
| Attack anim by weapon type; remote attackers | PARTIAL | **Remote attackers now animate** (CBT-3: `attack_anims` set from `P_AttackActor` `'Y'`/broadcast → swing clip); weapon-specific clip selection + target hit-anim/blood/parry still generic/missing |

---

## 8. Audio / Weather / Input / Localization / Misc (14 / 8 / 7 / 15)

| Feature | Status | Gap |
|---|---|---|
| Zone music loop + free-prior | DONE | (zone-env driven) |
| Volume / mute controls | DONE | `[` `]` `M` |
| Weather rain/snow + storm audio | DONE | Wind loop + thunder |
| Lightning + screen flash | DONE | ENV-5/6 |
| Cloud texture swap | DONE | — |
| **Live P_Music / P_Sound / P_Speech (in)** | DONE (2D) | **Now dispatched** — `SOUND=29`/`MUSIC=34`/`SPEECH=50` parse + play via new `SoundCatalog`; one-shots 2D, music switch replaces the loop. Remaining: 3D positional attenuation, sound zones (AUD-2) |
| **Sound zones (radius/3D/repeat/fade)** | MISSING | No SoundZone type at all (AUD-2 was PARTIAL — actually absent) |
| **Menu music (Menu.ogg)** | MISSING | Absent (MENU-10 was PARTIAL — absent) |
| Footstep selection | PARTIAL | Time cadence only; no gender/wet-dry/3D/underwater |
| 3D audio attenuation/panning | MISSING | rodio 2D mono gain only |
| Per-weather fog/cloud-alpha transitions | MISSING | Fog & Wind weather collapse to Clear |
| Rain/snow particles | DIVERGENT | 2D screen-space, not 3D world emitters |
| **Control-remap UI / controls.dat / invert-mouse** | MISSING | All keybinds hardcoded; several diverge from defaults (Jump=J not Space, view-mode/FP on different keys) |
| **LS_* localization (227 strings)** | MISSING | UI hardcoded English; no Language.dat |
| Chat slash-commands | DONE | Raw text forwarded |
| FPS / polygon counter | MISSING | Bench-only stdout |
| Screenshot key | DIVERGENT | Env-var only, no in-game hotkey |

---

## 9. What "reaching target" actually requires

Grouped by effort class, in suggested order:

**A. Functional blockers (small, high-impact):**
ESC-closes-panel-not-client · NPC dialog (`P_ScriptInput`/`P_ProgressBar`) · camera zoom (wheel+keyboard) ·
local `me_yaw` body rotation. *Days, not weeks. Unblocks core interaction + fixes the user-visible "stuck/quit" bug.*

**B. Front-of-game shell:**
EULA → loading screen → menu music + Set.b3d diorama → options menus (Graphics/Control/Sound) →
control-remap + controls.dat. *Closes most of the "feels lower quality" gap.*

**C. Environment richness (the `.dat` parser is the keystone):**
Extend `area.rs` to parse Water / ColBoxes / Emitters / LOD Terrains / Sound blocks → unlocks water+reflection,
3D particle emitters, sound zones, LOD terrain in one structural change. Then multitexture/lightmap brush
slots, sky bodies, shadows.

**D. Interaction depth:**
Mouse-carry inventory (swap/split/amount-dialog) · full trade (sell/amounts/cost/`P_UpdateTrading`) ·
item-use beyond eat (`P_ItemScript`, image items) · ranged-weapon MaxRange · inbound
`P_Sound`/`P_Speech`/`P_Music`/`P_AnimateActor`/`P_CreateEmitter` dispatch · SpellCharge server contract.

**E. Localization:** LS_* string table (227 entries) — mechanical but broad.

**F. Content-gated [deferred]:** swim/fly/mount/underwater camera, 2-player trade sync, seasons —
need world content the starter project lacks; verify when content exists.

---

## 10. Method & caveats

- Six parallel cross-codebase audits, each citing Blitz source line + Rust evidence line.
- Where this report and `ACCEPTANCE.md` disagree, the audits found ACCEPTANCE **optimistic** on:
  CAM-3 zoom (MISSING not PARTIAL), AUD-1/2/4 audio (sound zones + inbound sound packets absent),
  MENU-10 menu music (absent not PARTIAL), AUD-3 footsteps (no selection logic). Those rows should be downgraded.
- "Weighted parity %" is a coarse aggregate, not a release gate. The honest one-line summary: **the client
  is playable and the networking/world-replication core is largely correct, but it renders and plays at
  roughly half the reference's feature surface — the deficit is breadth (environment, shell, interaction
  depth), not a broken core.**
