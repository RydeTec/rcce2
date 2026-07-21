# Next-candidate experiment plan

The egui 0.29.1 candidate remains rejected. The next bounded experiment is iced 0.13.1 because it
supports Rust 1.85, followed by Slint 1.13.1 only if iced cannot satisfy the protocol. This order
is an experiment order, not a framework preference.

## Iced 0.13.1 go/no-go seam

The first task is a compile-only, disposable seam probe under a sibling spike directory. Iced's
`iced_wgpu` 0.13.5 declares wgpu 0.19 while `rcce-render` currently exposes wgpu 22 types. The
probe must attempt to pass the exact same adapter/device/queue and returned `WorldView` texture to
the Iced compositor without a second adapter/device/queue and without changing production crates.

The probe stops and rejects iced 0.13.1 if either condition is true:

1. Iced requires its own wgpu 0.19 device/queue or hidden renderer-owned device.
2. Sharing requires a production renderer migration, unsafe raw-handle bridge, or replacement of
   Iced's normal renderer substantial enough that accessibility/performance evidence would no
   longer describe Iced.

If the seam compiles with one device/queue, port the immutable `standard-v1.json` trace, pure
snapshot/input tests, R32Uint picking shader, overlay, provenance schema, and Windows matrix runner
without changing their semantics. Then execute every row in `observed-gates.json`; no egui result
may be borrowed.

## Slint 1.13.1 fallback

If iced is rejected at the seam, run the same compile-only test with Slint 1.13.1 and its
`unstable-wgpu-26` surface. Slint supports Rust 1.85 but its renderer aliases wgpu 26, so the same
single-device and no-production-migration stop conditions apply. Its declared license choices also
require a project policy decision before any production selection, but license policy does not
substitute for executable gates.

## Evidence and stopping rule

Each seam probe records exact versions, dependency tree, compiler output, source/lock hashes, and
whether a second GPU owner was created. A candidate that passes the seam gets the full release
one/two-viewport, 100/150/200 scaling, warm/cold, memory, UIA/Narrator, keyboard, docking, dialog,
contrast, reduced-motion, project-operation budget, and renderer-regression matrix. A candidate
with any Fail or NotRun remains unselected. If both alternatives fail the one-device seam, P06
returns to architecture review rather than migrating the production renderer implicitly.
