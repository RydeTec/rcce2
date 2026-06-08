# Rust Client Port — Parity Plan

Goal: a **true drop-in replacement for `bin/Client.exe`** in Rust on wgpu/winit, connecting to the **unmodified** RCCE2 server and reading the **unchanged** project files. The port is additive under `client-rs/`; no `.bb` or `data/` file is modified.

This plan is **parity-driven**. The acceptance spec — [`ACCEPTANCE.md`](ACCEPTANCE.md) — is the contract; every phase below maps to a set of acceptance criterion IDs and states how each is verified. "Parity reached" = every non-`DEFERRED` criterion is `DONE` (PNG/test/live evidence).

## Status (2026-06-01) — Phase 0 complete, parity gaps catalogued

The earlier vertical-slice port (login → live zone → walk → remote players → chat → inventory/HUD → environment/audio) is functional and shipping as `bin/ClientRS.exe` (`compile.bat -r`). But a live play-test exposed that it is **not yet a drop-in**: the menu orbits the gameplay zone instead of loading the real menu scene, the local player doesn't animate while moving, there's no click-to-move, and you can't context-menu / dialog / attack actors.

Phase 0 (this iteration) replaced the optimistic "all phases DONE" framing with an evidence-based audit: see the parity scorecard at the foot of `ACCEPTANCE.md` (~30 DONE / ~33 PARTIAL / ~22 MISSING). The phases below attack the gaps in dependency + user-value order.

### What is already DONE (foundation — do not regress)
Transport (NET-1..3), data parsers + ~113 tests (NET-2), the login/char-select *flow* and dataset streaming (MENU-1,3,4,5,7,8), third-person camera (CAM-1), remote-actor animation (ANIM-2), inventory/equipment (INV-1,2,4,6,8), spell casting + cooldowns (SPL-2,3), chat send/receive (CHAT-1), HUD at real Interface.dat coords (HUD-1,2,5,6,7), day/night + weather + sky + audio + radar (ENV-1,2,3, AUD-1,3,5, RAD-1), zone warp (ZON-1), headless tooling (TOOL-1,2,3). These are the platform the parity work builds on.

---

## Locked decisions

| Decision | Choice | Rationale |
|---|---|---|
| Graphics/windowing | **wgpu + winit** | 1:1 onto Blitz3D's entity/camera/RenderWorld model; cross-platform; WGSL. |
| Transport | **pure-Rust 64-bit ENet** (`enet-sys`) | FFI skipped; full login + world-state verified live (PR #462). |
| Byte order | **wire big-endian ints / LE floats; file LE; wire str 1-byte-len, file str 4-byte-len** | The single highest-risk correctness invariant; encoded in `rcce-net`/`rcce-data`. |
| Movement model | **destination-based** (`DestX/DestZ` + dist>2.0 walk threshold), shared by local + remote | Matches `Client.bb:546-728`; the local player must use the same path the remote actors already use. |
| Cooldowns | **keyed by spell ID 0-999** | Server decrements by spell ID; any other key desyncs (`Interface3D.bb:386`). |

---

## Phases

Ordered so each phase unblocks the next and front-loads the four play-test gaps (highest user-visible value). Each phase commits independently; after every `.bb` edit run `compile.bat -t` (clean = no `:line:col:`), after every Rust edit `cargo build --release` (zero warnings) + `cargo test` (green), build via `compile.bat -e -t -r` → `bin\ClientRS.exe` (kill any running `client-window.exe` first).

### Phase 1 — Local player locomotion animation  → ANIM-1, ANIM-3, ANIM-9
**Why first:** smallest, highest-visibility fix; the root cause is a single hardcoded `moving=false, running=false` at `client_window.rs:682`. Unblocks the felt quality of every subsequent movement feature.
- Drive the local player's `moving`/`is_running` from its own dest-delta + run flag, exactly as remote actors already do (`client_window.rs:684-688`).
- Confirm the clip-switch hysteresis (don't restart a playing clip every frame) and the `Animations.dat` per-clip speed normalization (`AnimStart/AnimEnd/AnimSpeed`).
- **Verify:** `RCCE_AUTOWALK` + `RCCE_SHOT=walk.png RCCE_SHOT_FRAME=200` → read PNG, confirm the player's legs are in a walk/run pose mid-stride, not idle. `anim_probe` for the speed table. `cargo test` green.

### Phase 2 — Click-to-move  → MOVE-5, MOVE-6, MOVE-1..4 alignment
**Why:** the core interaction the play-test flagged; depends on a working ground raycast that Phase 4 (actor picking) also needs.
- Add a ground/terrain raycast: on left-click with the world un-occluded by HUD and `GetTarget$==""` (terrain/scenery, not actor), `SetDestination(Me, hitX, hitZ, hitY)`; show a click marker at the hit point; support hold-to-move (repeat each frame).
- Reconcile WASD onto the destination-projection model (`KeyboardMoveDistance#*(1+IsRunning)` ahead of facing) so keyboard and click share one path.
- Double-click ground → set running; double-click actor → run to it.
- **Verify:** live on `Server.exe -UNLOCK` — click a ground point, character paths there and animates (Phase 1); `RCCE_SHOT` before/after shows position change. `move_test` bin.

### Phase 3 — Dedicated 3D menu scene  → MENU-SCENE, MENU-2, MENU-9, MENU-10
**Why:** first thing a user sees; currently the most obviously "wrong" surface. Independent of gameplay code.
- Replace the gameplay-zone spectator orbit (`render_menu`) with: dark-blue fogged void (`fog 0,51,102`, range 300-5200); a full-screen backdrop quad per screen textured from `Data\Textures\Menu\{Login,Character Selection,EULA}.png`; optional `Set.b3d` diorama at world `(-210,-35,-145)` scale 30; and the **selected character's actor mesh** at world `(30, ground-adjusted, 100)` playing `Anim_Idle`, camera ~150u back (offset −40 X), dollying to the head/chest joint on selection.
- Wire the two-phase connect (menu socket `"X"` → disconnect → game socket) and menu music.
- **Verify:** `RCCE_AUTOSUBMIT` (→ char-select) + `RCCE_SHOT=menu.png RCCE_SHOT_FRAME=60` → read PNG, confirm a backdrop image + a posed 3D character, **not** terrain/zone geometry.

### Phase 4 — Targeting, context menu, NPC dialog  → TGT-1, TGT-2, TGT-3, TGT-4, TGT-5, TGT-6, TGT-7
**Why:** gates combat (Phase 5) and most NPC interaction; reuses Phase 2's raycast for actor picking.
- Left-click actor → set `PlayerTarget`, show `ActorSelectEN` ground decal, open the Char-Interaction window (target HP/faction/level/reputation), follow it each frame.
- Single-click actor → "Actions" context menu at cursor (Interact/Move-To, Attack if attackable, Examine, Trade if `TradeMode>0`); each button sends its packet (`P_RightClick`/set AttackTarget/`P_Examine`/`P_Trade`). Re-bind RMB so mouse-look doesn't eat the menu (or move the menu to the Blitz single-left-click trigger).
- Render `P_Dialog` N/T/O/C: dialog window with wrapped text + green clickable options; option click → `P_Dialog "O" [4]scriptHandle [1]opt`. Add `TextInput` + `P_ProgressBar`.
- Cycle-target key.
- **Verify:** live — left-click the other human → highlight + target HUD; click an NPC → dialog window with selectable options that advance the script; context menu shows Attack on the stag.

### Phase 5 — Combat loop + combat animations + death  → CBT-1, CBT-2, CBT-3, CBT-5, CBT-6, ANIM-7, ANIM-8
**Why:** depends on Phase 4 targeting; the third play-test gap ("attack the stag").
- `AttackTarget=True` (from context menu / attack key / dbl-click) drives an auto-attack loop: range gate (melee 4.0 / ranged weapon−0.5), chase out-of-range, stop+face in-range, send `P_AttackActor` on `CombatDelay` cooldown.
- Render the `P_AttackActor` broadcast: attacker attack-anim, target hit-anim/parry, HP subtract, blood emitter; chat-line damage style. Death (`P_ActorDead`): random death anim + fade + clear target.
- Jump (MOVE-7) + jump anim (ANIM-7) folded in here (shared anim plumbing).
- **Verify:** live — select the stag, attack, it loses HP and plays hit/death anims and dies; floating numbers already work (CBT-4).

### Phase 6 — Spellbook, action-bar completeness, spell effects  → SPL-1, SPL-4, SPL-5, SPL-6, SPL-7, SPL-8, PRJ-1
- Full spellbook window (memorised + known pages), memorise/un-memorise with progress bar, action-bar assign/clear/paging + F-key fire, incoming `P_KnownSpellUpdate`, action-bar load from `P_StartGame`.
- Projectiles (`P_Projectile`): spawn/homing/impact + emitters — also serves spell visuals.
- **Verify:** live cast from spellbook + action bar; `RCCE_SHOT` of an in-flight projectile.

### Phase 7 — Chat/quests/party/trade completeness  → CHAT-2..4, QST-1,2, PTY-1,2, TRD-1..4, HUD-3,4,8
- Chat colors + scrollback + bubbles; quest log + `P_QuestLog`; party window + `P_PartyUpdate`; full trade window (sell side + player↔player + Amount dialog); money display + character sheet + function-button toggles.
- **Verify:** live (some need a 2nd player); `RCCE_SHOT` of each panel.

### Phase 8 — Environment completeness  → ENV-4, ENV-5, ENV-6, CAM-4,5,6, AUD-2,4, ANIM-4,5,6, ZON-2
- Water plane + scrolling UV + collision + underwater camera/anim; lightning; screen flash; first-person + MMB camera; sound-zone parsing + combat SFX; swim/ride/idle-fidget anims; same-AreaName re-warp handling.
- **Verify:** live in a water/storm zone; `RCCE_SHOT`.

### Phase 9 — Drop-in cutover  → TOOL-4
- Once every non-DEFERRED criterion is DONE: optional rename path + `Project Manager.exe` launch. Sign-off only.

---

## Verification doctrine

Per the Praxis evidence ladder, a criterion flips to `DONE` only with evidence at or above the strength its risk demands:
- **Visual** criteria → headless `RCCE_SHOT` PNG that is **read and confirmed** to match (never claim a visual pass not seen), or a live run.
- **Protocol/data** criteria → `cargo test` round-trip + a live exchange against `Server.exe -UNLOCK`.
- **Interaction** criteria → live run with the documented input → observed result.

Update the status tags in `ACCEPTANCE.md` as criteria pass, citing the evidence. GPU linear-blend skinning is now the **default** actor path (flipped in a dedicated perf increment after verifying pose parity day+night and bench ≥ the CPU path); `RCCE_CPUSKIN` forces the legacy CPU path. Do not change this default as part of *parity* work — only in a dedicated, separately-verified perf change.

## Workspace

`client-rs/` Cargo workspace. Crates: `rcce-data` (parsers), `rcce-net` (ENet + packet codecs), `rcce-render` (wgpu pipelines, overlay, sky/skin, world_view), `rcce-client` (assets, world, audio, HUD, login, `client-window` bin + headless probe bins). `cd client-rs && cargo test`; build the client via `compile.bat -r` (or `-e -t -r` for rust-only).
