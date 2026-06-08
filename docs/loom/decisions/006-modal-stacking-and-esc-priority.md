# ADR 006: Modal stacking + Esc priority chain

## Context

The beta accumulated four full-screen modal overlays: Palette (Ctrl+K), Timeline (Ctrl+H), Recents (Ctrl+R), BrokenRefs (click the ribbon's count chip). Each dims the world behind itself and consumes keyboard input — text into a query, arrow keys to scroll, Esc to close. They share enough shape that a single "are we in a modal?" gate isn't sufficient — the rules for who-handles-what need to be explicit.

Esc is the single most-asked-for key. It needs to do one of seven things depending on UI state, and the answer "the highest-priority thing that applies" requires a documented chain.

## Decision

### Modal render + input ownership

Every modal exposes:
- `Module::isOpen%(self)` — read accessor used to gate other surfaces.
- `Module::openModal(self)` — sets `open = True`, `FlushKeys` to drop the open-keystroke buffer, scrolls to top.
- `Module::closeModal(self)` — sets `open = False`, clears transient state.
- `Module::renderAndUpdate%(self, sw, sh)` — paints + pumps input + returns `True` if the modal consumed input this frame.

`Loom::renderFrame` paints back-to-front: Browser → Composer → Ribbon → (modals). The modal `renderAndUpdate` calls return `modalAte%`; the outer Esc handler runs **only when no modal ate input that frame**.

### Browser input gate

The Browser's filter pump + nav pump are gated by `browserInput%`:

```basic
Local browserInput% = True
If Timeline::isOpen(self\timeline) = True Then browserInput = False
If Palette::isOpen(self\palette) = True Then browserInput = False
If BrokenRefs::isOpen(self\brokenRefs) = True Then browserInput = False
If Recents::isOpen(self\recents) = True Then browserInput = False
If Composer::isEditing(self\composer) = True Then browserInput = False
```

Browser still **paints** when a modal is open (its surface is what gets dimmed behind the modal), but its keyboard handlers don't fire. The Composer's edit pump shares the same gate logic — when the Composer is in field-edit mode, keystrokes go to the edit buffer, not the browser's filter or nav.

### Esc priority chain

In `Loom::renderFrame`, Esc is checked in this order. The first match wins; subsequent handlers don't see the key:

```
modal-ate (Timeline / Palette / Recents / BrokenRefs)
  >  clear browser filter
  >  pop Threads back stack
  >  close composer back to browser
  >  exit Loom
```

The fall-through is intentional. A user with a non-empty filter who hits Esc expects the filter to clear before any other action. With an empty filter but a non-empty back stack, Esc walks back. With both clear and the composer focused, Esc closes the composer. With nothing focused, Esc exits.

### Modifier-shortcut chain

Global shortcuts (Ctrl+K / Ctrl+H / Ctrl+R) are checked **before any modal's `renderAndUpdate`** so the open-keystroke doesn't dribble into the modal's own query buffer:

```basic
If Palette::isOpen(self\palette) = False And ...all-other-modals-closed...
    If (KeyDown(29) Or KeyDown(157)) And KeyHit(37)
        Palette::openModal(self\palette)
    Else If (KeyDown(29) Or KeyDown(157)) And KeyHit(35)
        Timeline::openModal(self\timeline)
    Else If (KeyDown(29) Or KeyDown(157)) And KeyHit(19)
        Recents::openModal(self\recents)
    EndIf
EndIf
```

`openModal`'s `FlushKeys` drops the K / H / R from the queue so the modal's `pumpKeyboard` doesn't see them. Without that, the user would open Recents and see "r" pre-typed into the scroll cursor's keyboard buffer.

Only one shortcut chain fires per frame (Else-If), so simultaneous Ctrl+K + Ctrl+H is impossible.

### Mutually-exclusive modal opens

Each `openModal` doesn't explicitly close the others (the surrounding gate already prevents two modals from being open at once). If a code path ever needs to switch modals atomically, the pattern is `A::closeModal(self\a); B::openModal(self\b)` — the close runs first so the gate check at the next frame sees a clean state.

## Consequences

### Positive

- **Predictable Esc.** Users can rely on Esc doing the most-local thing first. No surprise jumps.
- **No modal-vs-keystroke race.** The `FlushKeys` + isOpen-gate combination guarantees that opening a modal never feeds the opening keystroke into the modal's own input.
- **Modal authors don't need to coordinate.** Each modal owns its own keyboard pump + return-True semantics; the outer frame handles the precedence.
- **Browser stays responsive.** Even with a modal open, the browser's surface is visible behind the dim overlay — useful for context.

### Negative

- **Three levels of gating** (modal-renders + browser-input check + Esc-priority chain) means a new modal has touch points in three places. Adding a fifth modal requires updates to:
  - The `isOpen = False And ...` chain in the keybinding section
  - The `If <New>::isOpen() = True Then browserInput = False` line
  - The `<new>Ate%` capture and the `modalAte%` OR-fold
- **No modal-modal stacking.** If two modals were ever opened simultaneously (currently impossible by construction), Esc would only close the highest-priority one per frame. Acceptable since we don't have nested modals.

## When to use a different shape

- A **non-blocking overlay** (e.g. a transient tooltip) shouldn't be a modal — render it from the surface that owns it, don't add it to the modal stack.
- A **modeless tool window** (e.g. a docked properties panel) is closer to the Composer / Atlas pattern — paint inline, no overlay, no Esc-eat.
- The modal stack is for "the entire window is now in a different mode." If your surface doesn't need to dim the world behind it, it's not a modal.

## Related

- ADR 005 — module-level recorder facade (the other cross-cutting pattern).
- `src/Loom.bb` `Loom::renderFrame` — the canonical implementation.
