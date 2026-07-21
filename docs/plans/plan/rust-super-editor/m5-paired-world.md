# M5 — Paired world authoring

## Identity

- **Canonical tasks**: `50, 51, 52, 53, 54, 55, 56, 57, 58, 59`
- **Depends on**: accepted M3 canonical records and M4 media identities
- **Status**: Planned

## Outcome

Visual and gameplay zone files are edited as one logical world document. The 3D viewport, inspectors, references, validation and persistence share stable identities and cannot silently split the pair.

## Work packages

| Packet | Task | Deliverable | Acceptance evidence |
|---|---:|---|---|
| `M5-SCHEMA` | 50 | Independently proven visual schema, gameplay schema and cross-file identity map | Corpus round trips include unknown chunks, empty zones and asymmetric pairs |
| `M5-WRITERS` | 51 | Independent I2/I3 visual and gameplay writers before grouped persistence | Each writer passes alone; grouped failure reports/recoveries remain truthful at either boundary |
| `M5-RENDER` | 52 | Shared-renderer zone scene adapter | Client/editor fixture renders agree within declared tolerances |
| `M5-GIZMOS` | 53 | Selection, picking and transform command primitives | Deterministic pick/transform tests; undo and numeric entry produce identical state |
| `M5-LIFECYCLE` | 54 | Create, open, save, clone, backup and delete paired zones | No operation can leave only one member without a surfaced recovery state |
| `M5-RENAME` | 55 | Zone rename with reference updates | Journaled grouping updates declared consumers or surfaces a recoverable partial state; collision checks are deterministic |
| `M5-ATLAS` | 56 | Zone atlas and cross-zone reference validation | Broken portals, targets and spawn references are navigable diagnostics |
| `M5-FAILURE` | 57 | Pair-specific crash/conflict recovery harness | Recovery choices preserve originals and never guess between divergent external edits |
| `M5-RUNTIME-PROOF` | 58 | Rust client/server load and behavior proof | Exact saved fixtures load in both Rust runtimes with logged parity results |
| `M5-SUBSYSTEMS` | 59 | Independently accepted world-subsystem packets | Every capability-matrix row has its own tests and disposition evidence |

## Subsystem packet register

Task 59 is deliberately decomposed; these packets must not be collapsed into a single “world editor complete” claim:

- `M5-WORLD-SCENERY`: scenery placement, transforms, visibility and references.
- `M5-WORLD-TERRAIN`: terrain placement/material links; authoring algorithms remain M6.
- `M5-WORLD-WATER`: planes/volumes and environment coupling.
- `M5-WORLD-EMITTERS`: particle emitter instances and parameters.
- `M5-WORLD-VOLUMES`: collision, fog, sound and other typed volumes.
- `M5-WORLD-LIGHTING`: lights, ambient/environment links and runtime tolerances.
- `M5-WORLD-PORTALS`: paired-zone targets and rename behavior.
- `M5-WORLD-WAYPOINTS`: graph identity, links, deletion and validation.
- `M5-WORLD-TRIGGERS`: trigger geometry and script references.
- `M5-WORLD-SPAWNS`: actor/spawn records, patrol/ownership references and server parity.
- `M5-WORLD-ZONE-POLICY`: gameplay flags/rules, environment policy and other zone-level runtime behavior not represented by placed entities.
- `M5-WORLD-PICKING`: collision/picking rules shared by selection and placement.

Each subsystem advances only its own capability rows and declares any intentional limitation.

Tasks 50–51 are also packet families: `M5-SCHEMA-VISUAL`, `M5-SCHEMA-GAMEPLAY`, and `M5-PAIR-IDENTITY`; then `M5-WRITER-VISUAL-I2/I3` and `M5-WRITER-GAMEPLAY-I2/I3`. `M5-GROUPED-PERSISTENCE` begins only after both writer families pass and provides journaled/recoverable grouping without claiming cross-file atomicity.

## Primary implementation surfaces

- `editor-rs/crates/rcce-project/src/zones/` — lossless documents and pair identity.
- `editor-rs/crates/rcce-editor-core/src/commands/world/` — semantic mutations and inverses.
- `editor-rs/crates/rcce-validation/src/world/` — local and cross-zone diagnostics.
- `editor-rs/crates/rcce-render/` — shared scene assets and editor overlays through explicit APIs.
- `client-rs/` and `server-rs/` — parity tests only through owned public seams, not copied editor logic.

## Verification

- Golden and property tests cover parse/write/reparse for both files and their relationship.
- Failure injection covers first-file write, second-file write, journal update and process restart.
- Headless viewport tests cover picking IDs, transforms and scene synchronization.
- Rust client/server integration tests consume editor-produced fixtures directly.
- Each subsystem packet records baseline capability evidence and independent review.

## Exit gate

All paired lifecycle operations recover coherently; all world subsystem rows have accepted dispositions; and the Rust client/server load the resulting projects without an editor-only compatibility path.

## Non-goals

- Building terrain/mesh generation algorithms assigned to M6.
- Treating visual similarity screenshots as sufficient data or gameplay parity evidence.
- Letting editor-only scene data become required runtime state.
