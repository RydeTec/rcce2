# Candidate seam experiment disposition

The egui 0.29.1, iced 0.13.1, and Slint 1.13.1 candidates are rejected. Iced failed its
compile-time one-device seam on wgpu 0.19.4. Slint failed the same bounded contract because its
supported winit/FemtoVG-wgpu `BackendSelector::require_wgpu_26` and texture-import APIs require wgpu 26.0.1 rather than
`rcce-render`'s wgpu 22.1.0 resources. With both fallback candidates rejected, the next step is
architecture review, not an implicit renderer migration or another unbounded framework trial.

## Iced 0.13.1 go/no-go seam

**Completed: no-go.** See `iced-seam/seam-report.md`. The preserved compiler contract rejects the
shared Adapter, Device, Queue, and TextureView, so the stop rule was applied before a full harness.

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

**Completed: no-go.** See `slint-seam/seam-report.md`. With winit, FemtoVG-wgpu, and accessibility
enabled, the supported backend-selector path rejects the existing instance, adapter, device, and
queue, and the image conversion rejects the existing texture. The stop rule was applied before a
full harness. The wgpu 26 API is explicitly unstable and exact to 1.13.1. Slint's declared license choices would
still require a project policy decision before production selection, but that policy question
does not substitute for the failed executable gate.

## Evidence and stopping rule

Each seam probe records exact versions, dependency tree, compiler output, source/lock hashes, and
whether a second GPU owner was created. A candidate that passes the seam gets the full release
one/two-viewport, 100/150/200 scaling, warm/cold, memory, UIA/Narrator, keyboard, docking, dialog,
contrast, reduced-motion, project-operation budget, and renderer-regression matrix. A candidate
with any Fail or NotRun remains unselected. Both alternatives failed the one-device seam, so P06
returns to architecture review rather than migrating the production renderer implicitly. No
further candidate is selected by this document.
