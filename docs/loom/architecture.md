# Loom architecture

How the code is shaped and why. Read [README.md](README.md) first for what Loom is supposed to *do*; this doc covers *how it's built*.

## Module map

```
src/
├── Loom.bb                          entry point — boots Blitz3D, loads
│                                    project data, runs the main loop
└── Modules/
    ├── (existing GUE data modules — Items.bb, Actors.bb, etc.)
    │                                read-only consumed; not modified
    │                                except for narrow setter helpers
    │                                (SetFactionName, DeleteXTemplate)
    └── Loom/
        ├── Theme.bb                 color palette + 2D drawing primitives
        ├── Threads.bb               focus state + back stack + chip primitive
        ├── Browser.bb               brand strip + tab bar + filter input +
        │                            card grid + Tools card grid
        ├── Composer.bb              right-side property panel per kind;
        │                            edit / save / delete / discard actions
        ├── Palette.bb               Ctrl+K find-anywhere; also picker
        │                            mode for ref-field editing
        ├── Ribbon.bb                top "Validation Conscience" strip:
        │                            dirty badges + broken-ref count + totals
        ├── BrokenRefs.bb            modal listing every dangling reference
        │                            (clicked from the Ribbon's red count)
        ├── Atlas.bb                 zone spatial view (FR force-directed
        │                            graph from portal links)
        ├── Timeline.bb              Ctrl+H session edit history with
        │                            click-to-revert
        ├── Recents.bb               Ctrl+R per-project persisted recently-
        │                            focused entities (Data/Loom/recents.txt)
        ├── Tools.bb                 launcher catalog for GUE's standalone
        │                            editors (Architect / Terrain / etc.)
        └── EntityFactory.bb         create / delete dispatch wrapping
                                     GUE's Create* + (new) DeleteXTemplate
```

### One-line module summaries

- **`Loom.bb`** — Bootstrap globals + data-loader sequence (mirrors `GUE.bb`'s order) + defines `Type Loom` and constructs every sub-instance. Main loop is `While Loom::renderFrame(app) Wend`; returns False from `renderFrame` when Esc exits an empty state. Owns the **modal stacking + Esc priority chain** (see below) and the global keybinding dispatch (Ctrl+K / Ctrl+H / Ctrl+R).
- **`Theme.bb`** — Color tokens as `LOOM_STONE_900_R/G/B`-style constants (the design's full palette). Drawing primitives wrap Blitz's `Color/Rect/Line/Text` so callers paint through `LoomFill / LoomGradientV / LoomHRule / LoomText / LoomTextCentered`. Also exports `LOOM_TOP_RIBBON_H` so every surface shares the same y-offset for the Conscience Ribbon.
- **`Threads.bb`** — The centerpiece module. `Type Threads` owns `focusKind$`, `focusID%`, and a `backStack.BBList` of `LoomFocusEntry`. Methods: `focus`, `jump` (push back stack), `back`, `clearStack`, `lookupName$`, `renderChip%` (returns a tri-code: 0=no-op / 1=left-jumped / 2=right-picker-request). Every focus / jump call emits to `Recents_Record` so navigation lands in the recents list.
- **`Browser.bb`** — The boot surface. Seven categories (`Actors / Items / Spells / Zones / Factions / Animation Sets / Tools`); per-kind grid methods dispatched from `drawCardGrid`. Filter input live-filters; arrow keys move a brass-ringed selection cursor; Enter focuses. Tools category swaps in `drawToolsGrid` which dispatches `Tools_Launch` on click. Zones tab has a `Card | Atlas` toggle that swaps the grid for the `Atlas` surface.
- **`Composer.bb`** — Right-side panel when something's focused. Per-kind body renderers (`renderActor`, `renderItem`, …) with editable rows: `editableRow` (string), `editableIntRow`, `editableFloatRow`, `toggleRow` (click-to-flip pill), `chipRow` (thread chip with right-click → picker). Top-right action cluster: Save / Discard / Delete buttons (arm-confirm on Discard + Delete). `commitEdit` records to Timeline before writing.
- **`Palette.bb`** — Ctrl+K modal find-anywhere across every kind with prefix > substring scoring and arrow-key navigation. Also serves as **picker mode** for ref-field editing — when opened via `openAsPicker(kind, targetKind, targetID, targetFieldId)`, results are filtered by kind and selection writes via `Composer::writeField` instead of `Threads::jump`.
- **`Ribbon.bb`** — Top "Validation Conscience" strip (28px). Per-kind dirty badges (brass when dirty, click to Save), broken-ref count chip (clickable when > 0 → opens BrokenRefs modal), total entity counts. Recomputed once per frame via `Ribbon::recomputeCache`.
- **`BrokenRefs.bb`** — Modal that enumerates every dangling reference with diagnosis text and click-to-jump-to-source. Re-scans on every open (no staleness). Capped at 250 entries.
- **`Atlas.bb`** — Spatial zone-graph view. Fruchterman-Reingold force-directed layout derived from portal-link topology (no persisted world position). Layout rebuilds on zone add / delete; cached between frames.
- **`Timeline.bb`** — Ctrl+H session edit history; ring-buffered at 200 entries. Records every edit / toggle / create / delete; revert button on edits + toggles dispatches `Composer::writeField`. Module-level facade (`Timeline_Record{Edit,Toggle,Create,Delete}`) lets callers record without an instance ref.
- **`Recents.bb`** — Ctrl+R per-project persisted recently-focused entities (`Data/Loom/recents.txt`). Stable keys per kind (refID for typed entities, name for zones) survive Handle regeneration across sessions. Move-to-front insertion; cap 30.
- **`Tools.bb`** — Free-function catalog of GUE's seven standalone editors. `Tools_Init` registers at boot; `Tools_Launch` `ExecFile`s the .exe with the project's `Data/` folder as CWD. Missing-binary detection (`FileType <> 1`) so the Browser can render the card as broken.
- **`EntityFactory.bb`** — Free-function module wrapping the GUE constructors (`CreateActor`, `CreateItem`, `CreateSpell`, `ServerCreateArea`, `CreateAnimSet`) plus the new non-Strict `DeleteXTemplate` helpers (added to `Actors.bb` / `Items.bb` / `Spells.bb` / `Animations.bb` to work around the Strict-mode Dim-array-write trap). Records every create / delete to Timeline.

### Why Types with Methods (not prefixed free functions)

Loom's stateful UI modules own state — Browser owns the category and cursor, Composer owns edit / delete-arm state, Threads owns focus + back stack, Atlas owns the cached force-layout. The project's canonical OO convention is **`Type` with `Method`s called via `TypeName::method(self, args)`** for stateful modules; Loom follows that pattern. See [`.claude/skills/blitzforge-language/SKILL.md`](../../.claude/skills/blitzforge-language/SKILL.md) "Module architecture" section for the rule + canonical examples.

Stateless modules (`Theme`, `Tools`, `EntityFactory`) stay as free functions per the same skill's rule of thumb (no state → free functions are fine).

The top-level `Type Loom` owns every sub-instance:

```basic
Type Loom
    Field windowWidth%, windowHeight%
    Field projectName$
    Field threads.Threads
    Field browser.Browser
    Field composer.Composer
    Field palette.Palette
    Field ribbon.Ribbon
    Field atlas.Atlas
    Field timeline.Timeline
    Field brokenRefs.BrokenRefs
    Field recents.Recents

    Method create.Loom(w%, h%, name$)
        self\threads = New Threads()
        self\browser = New Browser(self\threads)
        self\composer = New Composer(self\threads)
        self\palette = New Palette(self\threads)
        // ... cross-link Composer <-> Palette (picker mode)
        // ... construct Ribbon, Atlas, Timeline, BrokenRefs, Recents
        // ... set module-level facade pointers (LoomTimeline, LoomRecents)
    End Method
End Type
```

Every UI module that needs the focus state holds a Threads reference passed at construction — no globals for shared focus state.

## The render loop + modal stacking

The frame paints back-to-front: Browser → Composer → Ribbon → (Timeline | BrokenRefs | Recents | Palette modals). Each modal consumes its own keys when open and returns True from `renderAndUpdate` so the outer Esc handler skips. Browser keyboard input is gated by `browserInput%` — disabled when any modal is open or the Composer is editing a field.

**Esc priority chain** (highest-priority handler wins each frame):

```
modal-eats > clear browser filter > pop Threads back stack > close composer > exit Loom
```

**Modifier-shortcut chain** (only when no modal is open):

```
Ctrl+K  -> Palette (navigator mode)
Ctrl+H  -> Timeline
Ctrl+R  -> Recents
```

**Mouse priorities**:

```
Composer right-click on chip  -> Palette (picker mode)
Composer right-click elsewhere -> no-op (consumed by chipRow guard)
Mouse left-click               -> normal hit-test
```

A `MouseHit(2)` is captured ONCE at `Composer::renderAndUpdate` and propagated through the per-kind renderers → chipRow → renderChip — consuming the press at the first chipRow would make only the first chip see the press.

## Recorder facade pattern

Three modules (`Timeline`, `Recents`) record events from many call sites (Composer / EntityFactory / Palette commit / Threads::focus). Threading an instance reference through every caller is noise; instead each module exposes a **module-level facade** of free functions backed by a `Global Loom<Module>.Type` pointer that `Loom.bb` sets at construction:

```basic
// In Timeline.bb
Global LoomTimeline.Timeline = Null
Function Timeline_RecordEdit(kind$, refID%, fieldId$, oldValue$, newValue$, label$)
    If LoomTimeline = Null Then Return
    Timeline::record(LoomTimeline, TLE_EDIT, ...)
End Function

// In Loom.bb create.Loom
self\timeline = New Timeline()
LoomTimeline = self\timeline
```

The facade is defensive about Null (early-boot calls before wiring don't crash). Callers (Composer, EntityFactory, Palette, Threads) call the facade and never know there's a singleton behind it. The same shape is used by `LoomRecents` / `Recents_Record`.

## Data flow

```
                       ┌──────────────────────────┐
                       │  Data .dat files on disk │
                       │  (under <project>/Data/) │
                       └─────┬────────────────┬───┘
                             │                ▲
              LoadActors,    │                │  SaveActors,
              LoadItems,     │                │  SaveItems,
              ...            ▼                │  ServerSaveArea,
        ┌──────────────────────────────────────┴──┐
        │  In-memory type instances + arrays:      │
        │  ActorList(N), ItemList(N), SpellsList,  │
        │  FactionNames$(99), Each Area / AnimSet  │
        └─────────┬────────────────────┬───────────┘
                  │ read by            │ written by GUE
                  │                    │ AND by Loom
                  ▼                    │ (Composer / EntityFactory)
        ┌────────────────────┐         │
        │  Loom UI modules   │─────────┘
        │  (Browser/Composer │
        │   /Palette/Ribbon/ │
        │   Atlas/Timeline/  │
        │   BrokenRefs/      │
        │   Recents)         │
        └─────────┬──────────┘
                  │
                  ▼
            paint to screen via Theme.bb primitives
```

**Key invariant:** Loom reads through the exact same `LoadX` functions GUE uses, so the two editors cannot drift in how they parse the file format. Writes go through the same `SaveX` functions for the bulk-serialized kinds (Spells / Items / Actors / Factions / AnimSets); zones use the per-file `ServerSaveArea(Area)` since each zone is its own `.dat`.

The few mutating helpers Loom needed that don't exist in GUE — `SetFactionName`, `DeleteActorTemplate`, `DeleteItemTemplate`, `DeleteSpellTemplate`, `DeleteAnimSetTemplate` — were added to the respective data modules as **non-Strict** functions. They live in non-Strict modules so they can write to `Dim`'d global arrays (the Strict-mode trap; see Gotchas below).

## Shared state (the vocabulary)

Lives as fields on the `Threads` instance, which is the source of truth shared between every UI module that needs to know what's focused (no globals for focus state):

| Field | Type | Meaning |
|---|---|---|
| `threads\focusKind$` | string | `"" \| "actor" \| "item" \| "spell" \| "zone" \| "faction" \| "animset"` |
| `threads\focusID%` | int | interpretation depends on `focusKind` — see below |
| `threads\backStack.BBList` | `BBList` of `LoomFocusEntry` | navigation trail; popped by Esc |

**`refID` payload per kind** — every Loom module uses these conventions; never deviate:

| Kind | `refID` payload | Stable across sessions? |
|---|---|---|
| `actor` | `Actor\ID` (array index into `ActorList`) | yes |
| `item` | `Item\ID` (array index into `ItemList`) | yes |
| `spell` | `Spell\ID` (array index into `SpellsList`) | yes |
| `zone` | `Handle(Area)` (round-trips via `Object.Area(handle)`) | **no** — regenerates per load |
| `faction` | `FactionNames$` array index 0..99 | yes |
| `animset` | `AnimSet\ID` | yes |

The zone-handle instability is why `Recents` persists zones by `Ar\Name$` instead of refID — see [Recents.bb](../../src/Modules/Loom/Recents.bb) "STABLE KEYS" comment.

## Edit / save / dirty-flag plumbing

The per-kind `*Saved` globals (`ItemsSaved`, `ActorsSaved`, `SpellsSaved`, `FactionsSaved`, `ZoneSaved`, `AnimsSaved`) are **shared with GUE** — `Loom.bb` redeclares the same set at lines 58-69 so writes from Loom's Composer / EntityFactory flip the same flags GUE inspects. `False` = unsaved changes pending; `True` = on-disk == in-memory.

The edit lifecycle:

1. **`Composer::beginEdit(kind, refID, fieldId, currentValue)`** — opens a text-entry session for one field. Captures `editOldValue` for the Timeline.
2. **Keystrokes pump into `editBuffer$`** via `Composer::pumpKeyboard`. Backspace + ASCII drain.
3. **`Composer::commitEdit`** — `writeField` dispatches per-kind; `markDirtyForKind` flips the `*Saved` global; `Timeline_RecordEdit` records if value changed.
4. **`Composer::commitSaveForKind(kind)`** — calls the GUE serializer (`SaveSpells` / `SaveItems` / `SaveActors` / `SaveFactions` / `SaveAnimSets`) or per-zone `ServerSaveArea(Area)`. Resets `*Saved` to `True`.
5. **Discard** — frees every in-memory instance via the new `DeleteXTemplate` helpers, re-runs the `LoadX` function. Zones use `ServerUnloadArea` + `ServerLoadArea(name)`.

Numeric edits go through `Composer::parseIntClamped` / `parseFloatClamped` with per-field `[lo, hi]` ranges so a typo can't poison a field. Garbage strings parse to 0 via `Int()` / `Float()` and get clamped to `lo`.

## Why custom-draw + not F-UI

See [decisions/001-custom-draw-not-fui.md](decisions/001-custom-draw-not-fui.md) for the full rationale. Short version: F-UI is built for utilitarian Windows-style gadgets and cannot render the gradient-heavy, ornament-heavy aesthetic the Loom design called for. We chose custom-draw and have not regretted it — the modal stack, the chip primitive, the ribbon, the atlas, and the timeline all paint through the same `Theme.bb` primitives and share visual language without any of F-UI's per-widget retrofit cost.

**F-UI is still not Included by Loom even though editing landed.** The composer's text-input uses a hand-rolled GetKey-drain pump (see `Composer::pumpKeyboard`) which costs maybe 30 LOC and gives full control over the cursor blink, color, and edit-arm state.

## Why no `ClientAreas.bb` Include

`ClientAreas.bb`'s `LoadArea` is the canonical 3D zone-mesh loader. It's transitively coupled to GUE's UI substrate: `GY_Cam` (Gooey's 3D camera), `GY_CreateProgressBar`, `ResolutionType`, `RandomImages`, `GetMusicName$`, `GetTexture`, `GetFilename$` (which is defined inside `GUE.bb` itself, not in a shared module). Pulling it in would lock Loom to GUE's UI substrate — exactly what Loom is supposed to decouple.

Concrete consequence: **Loom cannot render the 3D zone mesh.** Zone composer shows zone metadata as text + portal-target chips; the Atlas surface gives a 2D spatial view from the portal graph topology. See [decisions/004-deferred-3d-viewport.md](decisions/004-deferred-3d-viewport.md) for the path to fixing this (extract `GetFilename$` to a shared helper; either rewrite `LoadArea`'s data path with the GUI side ripped out, or build a Loom-side mesh loader).

## Data-model gotchas

The thread model assumes reference fields are typed pointers, but rcce2 often stores them as plain strings:

- **Actor → faction**: typed (index into `FactionNames$`). Thread works; editable via right-click → picker.
- **Actor → anim set**: typed (`AnimSet\ID`). Thread works; editable via right-click → picker.
- **Zone → portal target**: stored as `PortalLinkArea$[i]` *string*. Composer resolves the string to a zone `Handle` via `Composer::findZoneByName`; works for valid names, renders as broken-ref-red for unknown. Editable via right-click → picker (the picker writes the NAME, not the handle, since the wire format stores by name).
- **Faction → members**: not stored. Computed: walk `Each Actor` looking for `Ac\DefaultFaction = idx`. Cheap.
- **AnimSet → users**: same pattern — walk actors looking for M or F binding.
- **Item / Spell → script**: stored as `Item\Script$` / `Sp\Script$` string. No entity to jump to (scripts live as `.rsl` files on disk; not Loom's data model). Rendered as text; editable as a plain string field.
- **Spell → casters**: cannot be derived from the data model — casts come from scripts. No back-reference possible without grepping scripts.

When adding a new thread chip site, check: is the reference field a typed ID, or a string? Strings either resolve through a lookup (like zone names) or render as text (no thread).

## File-naming and convention rules

- All Loom-specific modules under `src/Modules/Loom/`
- All exported symbols prefixed by their module: `Browser_*`, `Composer_*`, `Threads_*`, `LoomTheme_*`, `Loom_*` for cross-module state
- Module-level facade functions follow `<Module>_<Verb>` (no double-colon): `Timeline_RecordEdit`, `Recents_Record`, `Tools_Launch`, `EntityFactory_Create`
- Color constants: `LOOM_STONE_900_R / _G / _B` (channel-separated so `LoomFill(x, y, w, h, ...)` calls don't need to unpack a single int)
- Layout constants module-prefixed: `BR_TOP_RIBBON`, `CMP_PAD`, `CHIP_PAD_X`, `PAL_MODAL_W`, `TIMELINE_ROW_H`
- New `.bb` files default to Strict. The exceptions — `Recents.bb` — note their reason in the file header (typically `WriteFile`/`WriteLine` BBStream typing that doesn't thread through `SafeWriteCommit%`'s int signature)

## Known BlitzForge gotchas hit during the alpha + beta

Documented here so the next agent doesn't re-discover them:

- **`..` line continuation is not supported.** Long function calls have to fit on one line. (`Mismatched brackets` is the parser error.)
- **`First` and `Last` are reserved.** Don't use them as variable names. We use `firstFound`, `prev`, `endIdx`.
- **`data` is reserved** (the `Data/Read/Restore` family). Don't use as a variable name.
- **`step` is reserved** (For-loop syntax). Atlas had to rename `step` → `arc`.
- **`pi` is reserved** (math constant). Ribbon had to rename `pi` → `portalIdx`.
- **`default` is reserved**. Composer's `parseIntClamped` had to rename its default param to `fallback`.
- **Single-line `For ... : If ... Then ... : Next` doesn't compose.** The `If ... Then ...` on the same line swallows the rest of the line up to the implicit `EndIf`, so the `: Next` becomes part of the IF body and the For has no Next. Always multi-line the body.
- **`New TypeName()` parens are required** in BlitzForge even with no args; bare `New TypeName` errors.
- **Type instances leak without `EnableGC`.** None of the Loom files use `EnableGC` (matches the project's canonical OO files). Every Type that holds heap state (`LoomFocusEntry`, `PaletteResult`, `TimelineEntry`, `BrokenRef`, `RecentEntry`, `AtlasNode`, `AtlasEdge`) has an explicit `Delete` in its clear / trim method.
- **`Strict` + reassigning a Method-scope `Local` from inside nested `If`/`For` blocks doesn't compile.** Error: `<varname> assignment should start with local, global or const modifier`. Reassigning at the same nesting level as the `Local` declaration is fine; reassigning from a deeper nested block (or from a sibling `Else If` branch after using it in an earlier branch) errors. **Workaround**: write to a **Field on the Type** instead (`self\latch = True` works at any depth). Loom hit this in `Browser::drawCardGrid`'s six-branch chain (refactored to per-kind grid methods), `Ribbon::recomputeCache` (moved counters to `self\cached*` Fields), `Timeline::drawOneEntry`'s action-glyph dispatch (extracted to `Timeline::actionGlyph`), and `Composer::parseFloatClamped`'s digit-counter loop (skipped the validator entirely).
- **`Strict` + writing to a `Dim`'d global array from inside a Method errors** the same way. `FactionNames$(idx) = value` from Loom's Strict Composer can't compile. Workaround: a non-Strict setter function (`SetFactionName`, `DeleteActorTemplate`, etc.) in the non-Strict data module. Routing through the setter is the established pattern.
- **`Strict` + `WriteFile` returns `BBStream`** which doesn't auto-convert to the int file handle that legacy IO helpers like `SafeWriteCommit%` take. `Recents.bb` drops Strict at the module level for this reason; an alternative would be `Local F.BBStream = WriteFile(...)` but the typing doesn't thread through `SafeWriteCommit`.
- **Tab gadget `M_SETINDEX` has a nested-iterator bug in F-UI.** Calling `FUI_SendMessage(TabMain, M_SETINDEX, 9)` doesn't reliably switch the visible tab when called from outside F-UI's own click path. Loom sidesteps it entirely by not using F-UI's Tab gadget — the browser's category tab bar is hand-drawn.
- **`MouseHit(2)` consume-once.** Calling `MouseHit(2)` inside a per-chip iteration would only give the first chip the right-click. Capture once at `Composer::renderAndUpdate` and propagate `rightClicked%` through the render path.
- **`KeyHit(N)` consume-once** has the same shape. Global keybindings (Ctrl+K / Ctrl+H / Ctrl+R) are captured at `Loom::renderFrame` BEFORE any modal's `pumpKeyboard` runs, so the modal-open keystroke doesn't dribble into the modal's own query.
