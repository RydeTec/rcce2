# Loom prototype bundle

**This is the literal visual reference for Loom.** Not buildable from this repo, not part of the engine build, not consumed by any Loom code. Preserved here so future agents (human or AI) can see what Loom was designed to feel like at its most ambitious.

## What's in this directory

| File / directory | What it is |
|---|---|
| `Loom - World Editor.html` | The entry HTML. Open in any modern browser to render the prototype. |
| `styles.css`, `styles-surfaces.css` | The prototype's stylesheet. ~1,500 lines covering panels, chips, ribbons, modals. |
| `design-system/colors_and_type.css` | The full design palette as CSS custom properties. The source of truth for the color tokens in `src/Modules/Loom/Theme.bb`. |
| `src/*.jsx` | React 18 + JSX source (compiled in-browser via Babel-standalone). `app.jsx` is the state machine; `world-scene.jsx` is the spatial canvas; `composer.jsx` is the right-side panel; `threads.jsx` is the chip overlay; `aux-surfaces.jsx` covers the command palette + conscience drawer + timeline + walk-in modal. |
| `tweaks-panel.jsx` | The bundler's runtime config panel — lets the prototype switch aesthetic / toggle features at runtime. |
| `screenshots/` | Iteration screenshots the design agent captured while building the prototype. Numbered roughly in build order. |
| `design-session-transcript.md` | The original design session transcript — the back-and-forth with the Claude Design tool that produced the prototype. Read this for the *intent* behind each visual decision. |
| `HANDOFF-README.md` | The original bundle README that came with the Claude Design handoff. Tells coding agents how to read the bundle. |

## How to view it

```
cd docs/loom/prototype
# open Loom\ -\ World\ Editor.html in any browser
```

The prototype boots into the "Hollow's Edge" sample scene. Click entities, follow threads in the composer, press Ctrl+K for the command palette, click "Walk in" for the playtest modal, scrub the timeline along the bottom, toggle aesthetic between Tool / Balanced / In-world in the tweaks panel.

## How this maps to shipped Loom code

| Prototype surface | Shipped in alpha? | Where |
|---|---|---|
| Browser categories | ✅ | `src/Modules/Loom/Browser.bb` |
| Composer with thread chips | ✅ | `src/Modules/Loom/Composer.bb`, `Threads.bb` |
| Thread back-stack navigation | ✅ | `Threads.bb` |
| Color palette + brass / parchment / arcane / stone tokens | ✅ | `Theme.bb` |
| Spatial scene view (SVG actors / scenery) | ❌ deferred | see [decisions/004-deferred-3d-viewport.md](../decisions/004-deferred-3d-viewport.md) |
| Command palette (Ctrl+K) | ❌ next-up | see [roadmap.md](../roadmap.md) #1 |
| Validation conscience ribbon | ❌ deferred | see [roadmap.md](../roadmap.md) |
| World atlas (spatial zone map) | ❌ deferred | see [roadmap.md](../roadmap.md) #5 |
| Session timeline scrubber | ❌ deferred | needs editing first |
| Walk-in playtest modal | ❌ deferred | needs server bridge |
| Tweaks panel (aesthetic toggle) | ❌ won't build | not load-bearing for the alpha |

## Why preserve the bundle in-repo

The prototype is an irreplaceable artifact. It captures decisions about *visual rhythm, ornamental restraint, the specific feel of a brass divider against a stone-dark panel* that would be expensive to re-derive from a written brief. When a future PR is sizing a new surface (e.g. the command palette) the right question is *"what would this look like in the prototype?"* — and the answer should be a click away.

Without preservation, the prototype lives only in `/tmp/design-pkg/` on the machine that fetched it, which is wiped between Claude sessions and not visible to any other agent or human.

## Editing the prototype

**Don't.** It's frozen — a record of the design at the moment of handoff. If the design evolves, the right move is to either:

1. Open a new design session with the Claude Design tool, export a new bundle, copy it in as `prototype-v2/` (and update [README.md](../README.md) to point at the new north star)
2. Make focused tweaks to the design tokens in `design-system/colors_and_type.css` only, with an ADR explaining why the prototype is drifting from its original capture

In either case the original bundle stays as a checkpoint.
