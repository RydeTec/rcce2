# M6 — Specialist authoring

## Identity

- **Canonical tasks**: `60, 61, 62, 63, 64, 65, 66, 67`
- **Depends on**: accepted media lifecycle and paired-world integration points
- **Status**: Planned

## Outcome

Each legacy specialist tool is replaced by an evidenced Rust capability, an intentionally isolated converter/plugin, or an explicit unsupported disposition. Shared algorithms live below the editor UI and preserve source lineage and file compatibility.

## Work packages

| Packet | Task | Deliverable | Acceptance evidence |
|---|---:|---|---|
| `M6-CLASSIFY` | 60 | Format/algorithm/license classification for every specialist tool | Matrix cites source, samples, runtime consumers and chosen disposition |
| `M6-PLUGIN-HOST` | 61 | Permissioned out-of-process plugin/converter boundary | Capability grants, timeout/crash isolation, versioning and untrusted-output validation tests |
| `M6-MESH-CORE` | 62 | Reusable mesh inspection/transform/export core | Golden geometry, material, hierarchy and unknown-data tests |
| `M6-TERRAIN` | 63 | Terrain authoring and export capability | Height/material/scale seam fixtures load in editor and Rust client |
| `M6-GENERATORS` | 64 | Separate Architect, Cave, Rock and Tree packets | Each tool has independent fixtures, controls-to-output proof and rollback |
| `M6-GUBBIN` | 65 | Gubbin attachment authoring and preview | Bone/slot/transform records round-trip and render on representative actors |
| `M6-AUX-MEDIA` | 66 | Font, image and audio specialist operations | Deterministic output, metadata preservation and decoder failure tests |
| `M6-OPAQUE` | 67 | Opaque/proprietary capability disposition packets | Unsupported rows remain disabled with reason, evidence and migration route |

## Decomposition constraints

- `M6-GENERATORS` produces four independently reviewable packets: `ARCHITECT`, `CAVE`, `ROCK`, and `TREE`.
- `M6-TERRAIN` is a packet family: source codec/round trip; `.mbr` brush/palette and Terrain settings; sculpt/material/hole operations; vegetation/lighting operations; deterministic runtime export; and zone linkage. Each advances independently.
- `M6-AUX-MEDIA` splits into `M6-FONT`, `M6-IMAGE`, and `M6-AUDIO`; no combined acceptance hides an unsupported pipeline.
- Algorithms extracted from BlitzForge/C++ record their source file, governing license, behavioral fixtures and Rust replacement owner.
- Native libraries are not linked into the final Rust engine merely to avoid documenting an unsupported format.
- Plugins run out of process with explicit read/write roots, operation grants, resource limits and structured results.
- Plugin output re-enters through the same parser, validator, command and storage paths as built-in output.

## Primary implementation surfaces

- Pure algorithm crates under `editor-rs/crates/` with no UI dependency.
- Narrow adapters in `rcce-editor-core`; presentation remains in `rcce-editor`.
- Plugin protocol/schema and a reference test plugin under `editor-rs/plugins/`.
- Golden fixtures under `test-data/specialist/` with provenance and redistribution status.

## Verification

- Differential tests compare legacy output where the legacy tool is runnable.
- Metamorphic/property tests cover algorithms where exact bytes are not stable.
- Rust client rendering/load tests consume exported assets and placements.
- Plugin tests cover denial, crash, timeout, malformed result and partial-output cleanup.
- Every capability row ends as native, plugin/converter, or unsupported—never “assumed.”

## Exit gate

Every M6 specialist-tool capability row has an accepted disposition, every replacement output passes its runtime consumer gate, and no required specialist authoring/export path depends on BlitzForge or C++ execution. Cross-suite closure and final native-dependency removal remain M9/M10 gates.

## Non-goals

- One mega-packet claiming all generators are equivalent.
- In-process loading of arbitrary native extensions.
- Reverse engineering proprietary formats without legal/provenance review.
