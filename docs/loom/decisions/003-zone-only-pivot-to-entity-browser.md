# ADR 003 — Pivot from zone-only browser to everything-browser with threads

**Status:** Accepted (alpha)
**Date:** 2026-05-26

## Context

The first attempt at Loom (PRs #292 / #293 / #294 / #295) shipped a four-PR stack:

| PR | Slice |
|---|---|
| #292 | Skeleton + Project Manager launcher (merged) |
| #293 | Atlas (zone picker) + data loading (closed) |
| #294 | Top-down 2D zone map showing waypoints / spawns / triggers / portals (closed) |
| #295 | Composer panel for the zone sub-entities clicked in the map (closed) |

The shipped result let you pick a zone in the atlas, see its waypoints/spawns/triggers/portals on a map, click one, and see its field set in the composer.

The user's feedback was:

> "Needs to be more useful as right now it doesn't really make much sense in its current iteration. It can't do anything."

## The diagnosis

The alpha viewed **one corner of zone metadata** and skipped everything that actually makes a project interesting — the actors, items, spells, factions. The project's content was loaded into memory but never surfaced. From the brief user-stories list:

- "Authoring a creature" — *not addressed* (actors invisible)
- "Designing a weapon" — *not addressed* (items invisible)
- "Tuning a spell" — *not addressed* (spells invisible)
- "Building a place" — *partially addressed* (zone metadata visible, but only the spatial sub-entities, not the actors/items placed in it)

Worse, the design's centerpiece — *"every reference becomes a visible thread"* — was the only Loom-distinctive concept that didn't ship. The zone map had its own click-to-select interaction, but it never followed a reference *to* another entity. There was no jumping, no threading, no back-stack navigation.

## Decision

Replace the zone-only browse model with an **everything-browser** plus **clickable thread chips**. Specifically:

- The boot surface becomes a generalized browser with six categories (Actors / Items / Spells / Zones / Factions / Animation Sets) instead of a zones-only atlas.
- The composer renders every entity kind, not just the four zone sub-entities.
- Every reference field (actor's faction, zone's portal targets, faction's member roster, animset's user roster) renders as a clickable thread chip.
- Clicking a chip pushes the current focus onto a back stack and refocuses on the target.
- Esc walks back through the trail.

Land as a single PR off develop ([#296](https://github.com/RydeTec/rcce2/pull/296)), supersede the three closed PRs above. The skeleton (#292) stays — it's still the right foundation.

## Rationale

The original four-PR design was scope-decomposed by *technology* (atlas, then zone map, then composer) rather than by *user value*. Shipped end-to-end, it answered "can Loom render zone metadata?" — a true answer, but not a useful one.

The new shape decomposes by *user value*: "can I browse my content and follow the relationships between things?" That answers the alpha question the design was actually meant to test.

## Consequences

**Good:**
- Loom now surfaces the project's actual content, not just one geometric corner.
- Threads — the design's most distinctive concept — actually ship. The hero flow ("actor → faction → member → another actor → Esc back") works.
- The zone composer is still there as one of the six kinds, so nothing was lost.

**Bad:**
- The 2D zone map (the literal spatial view of where things are placed in the world) didn't survive the pivot. Zones in the new composer show metadata (portal targets, spawn count) but not spatial layout.
- About 1,000 lines of code (Atlas.bb, ZoneMap.bb, the per-zone Composer.bb) were thrown away. The new Browser/Composer/Threads modules are larger and structurally different.
- Three closed PRs in the git history. They're not lost (PR descriptions and code archived on GitHub), but the linearity of the history is broken.

## What would force a re-evaluation

- **Users explicitly want the spatial zone view back.** The card-grid view of zones is functional but not spatial. If "where in the world is X" becomes a real workflow, the spatial atlas comes back as a Zones-tab alternate view (probably a toggle, since the grid is also useful for sorting).
- **A new entity kind is added to rcce2** that doesn't fit the card-grid pattern (e.g. a graph of script call relationships). New kind, new browser surface, possibly new composer rendering pattern.

## See also

- [ADR 001](001-custom-draw-not-fui.md) — the F-UI question, which the pivot didn't change
- [roadmap.md](../roadmap.md) for what comes next on top of the pivoted alpha
