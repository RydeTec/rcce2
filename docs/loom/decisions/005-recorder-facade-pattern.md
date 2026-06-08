# ADR 005: Module-level recorder facade for cross-cutting events

## Context

As Loom grew from the read-only alpha into the beta, multiple modules acquired the need to **record events that span the application**:

- **Timeline.bb** records every in-memory mutation (edit / toggle / create / delete) so Ctrl+H can show the session history with click-to-revert.
- **Recents.bb** records every entity focus so Ctrl+R can show recently-touched entities across sessions.

The callers are everywhere. Edits flow from `Composer::commitEdit` (text), `Composer::toggleRow` (bool), and `Palette::jumpToResult` (picker mode). Creates flow from `EntityFactory_Create*`. Deletes flow from `EntityFactory_Delete*`. Focuses flow from `Threads::focus` and `Threads::jump` — which are themselves called by Browser card clicks, chip clicks, palette navigator-mode commits, BrokenRefs jumps, Atlas node clicks, and Recents modal clicks.

The naïve approach would be to thread a `timeline.Timeline` reference (and `recents.Recents` reference) through every call site that might need to record. That's a lot of plumbing — at the limit, every Method that mutates state would need both refs added to its signature, and the constructor chain in `Loom.bb` would need to set them at every level. Worse, some callers (`Threads::focus`) live in modules that are **Included before** the recorder module, so the recorder Type wouldn't even be in scope at the call site.

## Decision

Each recorder module exposes a **module-level facade** of free functions backed by a `Global Loom<Module>.Type` pointer that `Loom.bb` sets at construction:

```basic
// In Timeline.bb
Global LoomTimeline.Timeline = Null

Function Timeline_RecordEdit(kind$, refID%, fieldId$, oldValue$, newValue$, label$)
    If LoomTimeline = Null Then Return
    Timeline::record(LoomTimeline, TLE_EDIT, kind, refID, fieldId, oldValue, newValue, label)
End Function

// In Loom.bb create.Loom
self\timeline = New Timeline()
LoomTimeline = self\timeline    // facade is live from here on
```

Callers invoke the free function and never see the singleton:

```basic
// In Composer::commitEdit
Timeline_RecordEdit(k, id, fid, oldVal, val, Threads::lookupName(self\threads, k, id))

// In EntityFactory_CreateActor
Timeline_RecordCreate("actor", A\ID, A\Race$ + " [" + A\Class$ + "]")

// In Threads::focus (Included BEFORE Recents.bb!)
Recents_Record(kind$, refID, Threads::lookupName(self, kind$, refID))
```

Same shape applies to Recents (`LoomRecents.Recents` + `Recents_Record`).

## Consequences

### Positive

- **Caller signatures stay clean.** No new reference parameters threading through `commitEdit` / `Create*` / `Delete*` / `focus` / `jump`.
- **Forward references work.** Threads.bb is Included before Recents.bb in Loom.bb's chain, but `Recents_Record` is resolved at whole-program link time so the call compiles fine. The Type isn't needed at the call site — only the free function name is.
- **Defensive against early-boot calls.** Every facade function null-checks the singleton (`If LoomTimeline = Null Then Return`). The first few focus calls before `Loom.bb` wires the pointer are silent no-ops — no crash, no special-case at the call site.
- **Extension is local.** Adding a new recorder doesn't require touching any caller's signature; the new module declares its facade + singleton, and callers gradually adopt by adding a one-liner.

### Negative

- **One global per recorder.** Loom isn't a multi-instance app, but in principle this shape couples the recorder modules to "there is exactly one Loom in this process." Acceptable trade-off; we don't run multiple Looms.
- **Initialization-order discipline.** Facade callers may run before `Loom.bb` sets the pointer. We rely on the null-check inside each facade function to handle the early-boot window. A more pedantic alternative would be to defer the call site invocation until after wiring, but that complicates every caller for a window measured in microseconds.
- **Hides ownership.** Reading a caller in isolation — `Timeline_RecordEdit(...)` — doesn't reveal where the singleton came from. The facade is a documented convention; readers have to know to look in `Loom.bb` for the wiring + the recorder module's header for the singleton.

## Alternatives considered

- **Pass the Timeline/Recents ref through every call site.** Rejected for the plumbing cost (every Composer Method, every EntityFactory function, every Threads Method gains two new parameters).
- **Put recorders on the Threads Type.** Conflates focus-state with edit-history; Threads has no business owning a 200-entry ring buffer. Rejected for cohesion.
- **Subscribe / publish via a generic event bus.** Overkill for two consumers. The facade is the smallest shape that does the job.

## When to add a new recorder

Use this pattern when:
- The recorder needs to observe events from 3+ unrelated call sites.
- The recorder's lifecycle matches the app's lifecycle (single instance, lives for the whole session).
- The caller would otherwise need a new constructor parameter / setter just to dispatch one event.

Don't use this pattern for module-private state — that's what Type Fields are for. The facade is specifically for app-singleton modules with many callers.

## Related

- ADR 006 — modal stacking + Esc priority chain (the other cross-cutting concern that emerged alongside this one).
- `Modules/Loom/Timeline.bb` — `LoomTimeline` + `Timeline_Record*`.
- `Modules/Loom/Recents.bb` — `LoomRecents` + `Recents_Record`.
