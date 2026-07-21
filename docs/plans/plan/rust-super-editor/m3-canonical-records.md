# M3 — Canonical records and relationships

## Identity

- **Canonical tasks**: 34, 35, 36, 37, 38, 39, 40, 41, 42, 43
- **Status**: `Planned`
- **Depends on**: M2 command/storage accepted; per-format I2 prerequisites
- **Blocks**: media/world/specialist parity completion and publication

## Outcome

Every canonical collection and global setting is authored through small, evidence-bearing domain packets. Sparse IDs, unknown bytes, inbound references, settings write groups, and client/server semantics remain correct.

## Domain packet register

| Packet family | Domains | Minimum prerequisite |
|---|---|---|
| `SE-M3-ACTOR-*` | Actors, appearance, speech slots, attributes/resistances | Actor I2 + client/server consensus |
| `SE-M3-ITEM-*` | Items, equipment metadata, attribute modifiers | Item I2 + media identity stubs |
| `SE-M3-SPELL-*` | Spells/abilities and script references | Spell I2 + script identity |
| `SE-M3-PROJ-*` | Projectiles and emitter/damage/media refs | Projectile I2 |
| `SE-M3-FACTION-*` | Names and directed 100×100 matrix | Faction I2 + sparse slot policy |
| `SE-M3-ANIM-*` | Sets and 150 clip slots | Animation I2 |
| `SE-M3-ATTR-*` | 40 attributes and semantic mappings | All consumer mappings inventoried |
| `SE-M3-DAMAGE-*` | 20 types and policy refs | Damage I2 |
| `SE-M3-ENV-*` | Calendar, months, seasons, suns/moons | Environment/Suns independent I2 |
| `SE-M3-UI-*` | Interface layout and 46 inventory buttons | Interface I2 + preview fixture |
| `SE-M3-PARTICLE-*` | Emitter configs and name identity | RPC I2 + filename policy |
| `SE-M3-SETTINGS-*` | Misc/Hosts/Other/Money/Damage/Attributes groups | Every affected file I3 + storage group |

## Cross-cutting work packages

| Packet | Tasks | Deliverable |
|---|---:|---|
| `SE-M3-P01` Packet issuance | 34, 43 | One packet per matrix row; no mega-change |
| `SE-M3-P02` Shared ownership | 35 | Reconcile duplicate Rust models only after consumer contracts |
| `SE-M3-P03` Sparse allocation | 36 | Explicit allocation/reuse/sentinel library and tests |
| `SE-M3-P04` Lifecycle commands | 37 | Create/copy/edit/delete with inbound preflight |
| `SE-M3-P05` Named identity | 38 | Emitter/script rename; zone rename deferred to M5 |
| `SE-M3-P06` Validation graph | 39 | Stable diagnostics/source spans by reference row |
| `SE-M3-P07` Consumer proof | 40, 42 | Client/server/editor semantic fixtures and CI fan-out |
| `SE-M3-P08` Settings grouping | 41 | True affected-file plan and failure injection per family |

### Loom relationship-intelligence packet

`SE-M3-RELATIONSHIPS` is a separately accepted tasks 38–39 packet. It generalizes the M1 reference index across every consensus-proven record domain and owns:

- unified find by stable identity, name/path and supported field values;
- focus/back navigation from a result, thread or diagnostic to its owning record without losing the prior context;
- typed inbound/outbound thread edges with source provenance and broken-target states;
- project-health/stat projections derived from the same diagnostics/reference evidence;
- cross-lens query APIs consumed later by world atlas, media usage, script references and rename plans;
- keyboard/accessibility behavior and performance budgets for global search, thread expansion and focus transitions.

It does not infer relationships from display strings or claim completeness for provisional domains. Each domain packet registers its edge producers and fixtures independently.

## Packet execution order

1. Lowest-coupling names/settings formats validate the packet machinery.
2. Identity catalogs and sparse allocation precede dependent records.
3. Actors/items/spells/projectiles advance only with their referenced domain status visible.
4. Environment/Suns and Settings exercise real multi-file recovery.
5. Interface/particle preview arrives only with deterministic fixtures.

## Verification

- RED writer test per field group, then byte/semantic GREEN.
- Create/copy/delete/rename tests include inbound-reference outcomes.
- No-op and one-field edits retain raw strings, float bits, unknown bytes, and topology.
- Settings failures at every file boundary retain truthful pending/recovery state.
- All shared-crate changes run all three workspace matrices.

## Exit gate

- Every canonical matrix row has an accepted disposition and required I3 behavior.
- No zone rename is implemented here.
- Rust client and server load every editor-written canonical fixture through declared shared semantics.

## Non-goals

- Media file lifecycle, paired zone documents, specialist source formats, live server administration.
