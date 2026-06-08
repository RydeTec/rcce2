# ADR 002 — Alpha is strictly read-only

**Status:** Accepted (alpha)
**Date:** 2026-05-26

## Context

The natural-feeling shape of an editor is "browse a thing, edit a thing, save the thing." The Loom alpha intentionally stops at "browse." Every field in the composer renders as a label and a value, no edit affordance, no click-to-modify. The footer of every composer page says "Read-only in alpha" so users aren't confused.

## Decision

Loom's alpha is **read-only by deliberate choice.** Editing is a separate phase that lands after the browse experience is proven and the supporting infrastructure (dirty tracking, save flow, validation, undo) is built.

## Rationale

Editing isn't just "make the values typeable." It drags in a chain of infrastructure decisions that each deserve their own design pass:

1. **Dirty tracking** — per-entity? Per-tab (like GUE's `ItemsSaved` / `ActorsSaved` globals)? Per-field? GUE uses 12 per-tab globals; Loom could either piggyback on those (so GUE notices Loom's edits) or maintain its own (cleaner but creates dual-source-of-truth problems).
2. **Save flow** — incremental ("save this one actor") or batch ("save all changed entities")? GUE batches; Loom could go either way.
3. **Validation** — many fields have bounded ranges, format constraints, or cross-entity invariants (a portal's target zone must exist, a script binding must reference a real `.rsl` file). Where does this live? Inline as the user types? On save? As a background pass like the design's "validation conscience" ribbon?
4. **Undo** — once a field is editable, "undo my last change" becomes a basic expectation. Loom has no undo system. Building one is its own multi-PR effort and the design called for an entire "session timeline scrubber" surface around it.
5. **Reference editing** — typing in a faction name is one thing; picking a different faction from a list is another. Pickers need their own UI. The thread-chip primitive can be extended to "shift-click to edit, click to jump," but the picker that opens is non-trivial.
6. **Concurrent-edit semantics** — if GUE is open on the same project, who wins? Files are locked by Windows during write; both editors would crash inelegantly without coordination.

Each of these is a real design question. Trying to ship them as a bundle with browse would make the alpha 3-5x larger and dilute the thing it's actually meant to prove (that the threads-and-browser surface feels useful).

## Consequences

**Good:**
- The alpha can ship in one PR and be evaluated on a single question: *"Does browsing my world with threads feel valuable?"*
- The infrastructure decisions above happen later with real usage data informing them.
- Read-only mode is itself a real use case (audit-only review of a project, onboarding a new collaborator to an existing world).

**Bad:**
- "Editor that can't edit" is a confusing pitch. The Project Manager button label is `Loom (Alpha)` to signal "this is not done." The composer footer line "Read-only in alpha" is the in-product reminder.
- The "save" button is conspicuously absent; users may try to find it.
- Future iteration will eventually need editing — and may discover that decisions made for the read-only shape (e.g. composer layout) don't generalize cleanly.

## What unlocks editing

Editing phase 1 (free-form text + numbers) is item #3 on [roadmap.md](../roadmap.md). It needs, at minimum:

- A dirty-flag mechanism (decision: piggyback on GUE's per-tab `*Saved` globals so the two editors see each other's edits)
- F-UI Included for `TextBox` widgets (the one place F-UI earns its keep — see [ADR 001](001-custom-draw-not-fui.md))
- A save affordance in the chrome (probably top-right next to the brand)
- A per-field validator harness (initially trivial: bounded-int range checks, required-field checks)

Phase 2 (reference-field editing via pickers) builds on the command palette ([roadmap.md](../roadmap.md) #1).

## What would force a re-evaluation

- **A user with a real project says "I'd actually use this if I could change X."** That's the signal that the read-only constraint has become more cost than benefit.
- **GUE develops a save-coordination bug** that requires reasoning about who-writes-when. Loom's read-only stance dodges this today but inherits the problem if it ever writes.
- **A new use case appears** that's primarily edit-driven (e.g. bulk find-and-replace of a renamed script across all bindings). At that point, editing is unavoidable and the rest of the infrastructure has to come with it.
