# ADR 001 — Custom 2D drawing through Theme.bb, not F-UI

**Status:** Accepted (alpha)
**Date:** 2026-05-26

## Context

Loom needs a UI shell. The rcce2 codebase already has F-UI (`src/Modules/F-UI.bb`, ~24k lines), the custom Blitz3D-based widget toolkit GUE renders through. Using F-UI would mean Loom inherits the visual language GUE has (flat stone-gray panels, system fonts, hard-edged borders).

The Loom design specified the opposite: dark-fantasy aesthetic with vertical gradients, ornamented brass rules, parchment-colored body text, glowing arcane-blue accents, custom typography (MedievalSharp / Cinzel / Cormorant Garamond). The design's visual identity is load-bearing — it's a large part of what makes the redesign feel like an alternative to GUE rather than a rearrangement.

## What we tried first

The previous round (closed PRs #271 / #273 / #274 / #275) attempted to retrofit Loom concepts into GUE's existing F-UI surface. It hit several F-UI quirks:

- **Window `M_HIDE` is a no-op** for windows created with `WS_CLOSEBUTTON`. Modal hide-on-close fails silently; the modal stays visible until the user clicks the X button manually.
- **Listbox and combobox have `M_GETSELECTED` and `M_GETINDEX` swapped.** ComboBox `M_GETSELECTED` returns the active item *handle*; ListBox `M_GETSELECTED` returns the active item *index*. Generic widget-walk code has to branch.
- **`Tab M_SETINDEX` has a nested-iterator bug** — calling it from outside F-UI's own click path doesn't reliably switch the visible tab. (The internal handler uses `For tabp.TabPage = Each TabPage` with both an outer and inner loop sharing the iterator variable name; the inner loop corrupts the outer's state in non-Strict Blitz.)
- **F-UI's visual primitives can't express the design's aesthetic.** No gradients, no glows, no transparency, no custom fonts beyond the default Blitz one. Even after fighting through the quirks, the result wouldn't look like the design.

Each quirk was solvable individually; cumulatively they were a losing game.

## Decision

Loom paints its own UI surfaces directly on top of Blitz3D's 2D primitives (`Color`, `Rect`, `Line`, `Text`), wrapped in `Modules/Loom/Theme.bb` helpers (`LoomFill`, `LoomGradientV`, `LoomHRule`, `LoomText`, `LoomTextCentered`).

F-UI is **not Included by Loom** at all in the alpha. Hit-testing is inline (`MouseX() / MouseY() / MouseHit(1)` against per-frame-computed rects).

## Consequences

**Good:**
- The Loom aesthetic is achievable. Vertical gradients via row-by-row interpolated rects. Brass rules via stacked `LoomHRule` calls. Thread-chip hover via simple border swap.
- Zero F-UI bugs are inherited. The Loom modules are 1,200 lines total; everything that happens on screen is in those files.
- The implementation is straightforward to read — no event dispatch table, no gadget handle juggling, no widget-rebuild side effects.

**Bad:**
- Custom font loading is deferred — Loom uses the Blitz default font (Arial-ish bitmap). The design specifies MedievalSharp / Cinzel / Cormorant Garamond. Adding them needs `LoadFont` + bundling .ttf files + a per-widget font selector. Low priority but on the list.
- Text input (when editing lands) will need F-UI after all — Blitz3D's `Input$` is modal-blocking. F-UI's TextBox widget is the obvious answer when that PR happens.
- No transparency / alpha blending in 2D — we fake softness with brass-on-stone hierarchy instead of glows. The prototype's literal SVG glows aren't reproducible.

## What would force a re-evaluation

- **Loom needs a widget F-UI provides that's expensive to rewrite.** Most likely candidate: a working text input. When that lands, F-UI gets pulled in *for text inputs only*. The rest of the chrome stays custom-drawn.
- **A new toolkit appears** (e.g. a Blitz3D fork that supports CSS-like styling). Doesn't exist today.
- **The design changes** to a utilitarian look that F-UI could express. Not on the horizon.

## See also

- [decisions/003-zone-only-pivot-to-entity-browser.md](003-zone-only-pivot-to-entity-browser.md) — the bigger retrofit-vs-rebuild decision that this one sits inside
- The closed retrofit PRs ([#271](https://github.com/RydeTec/rcce2/pull/271), [#273](https://github.com/RydeTec/rcce2/pull/273), [#274](https://github.com/RydeTec/rcce2/pull/274), [#275](https://github.com/RydeTec/rcce2/pull/275)) document the specific F-UI quirks encountered, with file:line references
