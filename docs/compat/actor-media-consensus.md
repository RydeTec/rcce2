# Actor/base-mesh consensus fixture contract

`SE-M1-P05` covers only `PF-CAN-001` Actors.dat, `PF-MED-001` Meshes.dat,
and the `PF-MED-005` physical `Data/Meshes` lookup needed by the first
read-only actor-count/base-mesh slice. All other data families remain outside
this packet.

The closed corpus lives at `editor-rs/test-data/consensus/`. It is generated
from literals by `scripts/gen_actor_media_consensus.py`, is MIT licensed, and
contains no copied repository bytes or real project data. `manifest.json`
records expected outcomes and half-open byte ranges; `SHA256SUMS` covers every
other corpus file. The generator enforces at most 32 files, 300,000 bytes per file,
2,000,000 aggregate bytes, deterministic paths/content, and no links.

## Accepted candidate meanings

- `happy`: the client, server, and editor observe exactly four actor records;
  one base mesh `65535` is `None`, one is present, one references unused sparse
  slot `8`, and one has a catalog record whose physical file is absent. The
  unused slot remains observed topology but does not by itself downgrade this
  accepted actor/base-mesh slice.
- `missing-catalog`: actor bytes remain readable and non-sentinel base meshes
  report `MissingCatalog`.
- `missing-physical`: catalog records remain readable and report
  `MissingPhysical` only when their exact raw filename is valid UTF-8,
  component-valid, and absent from accepted inventory evidence.
- `unique-case`: a unique case/NFC inventory key resolves to the accepted
  inventory spelling without rewriting the persisted catalog bytes.
- `nested-*`: legacy backslash-separated nested filenames project to validated
  portable components for lookup; raw catalog bytes remain unchanged, missing
  nested files remain distinct, and traversal stays provisional.
- `nested-paths` also includes extra media reported only as unreferenced by
  this selected actor-base slice. P05 does not infer that it is globally orphaned.
- `provisional`: client/server high actor-ID disagreement, raw non-UTF8 actor
  and mesh strings, a truncated actor tail, and alias/invalid mesh topology
  remain visible and force `Provisional`. Sparse zero-offset unused slots remain
  visible through `has_gaps` but are not alone provisional evidence.

Physical lookup begins with the exact raw filename span in Meshes.dat. It never
uses lossy display text. Legacy backslashes are normalized only in a separate
lookup projection before component validation. Non-UTF8 bytes, invalid
components, traversal forms, case/NFC collisions, or unavailable inventory
objects yield `Provisional`, not `MissingPhysical`.

Before this slice can be `Consensus`, the client and server actor parsers must
also report the same raw 16-bit slot-0 base-mesh value for every exact actor ID.
Comparison is by actor ID rather than parser iteration order and preserves the
server value's bit pattern (`i16 as u16`), so server `-1` and client `65535`
agree as raw `0xFFFF`. Incomplete parses, missing or duplicate counterparts,
different actor-ID sets, and any slot-0 mismatch are not accepted consensus.
Mismatch evidence retains the exact actor ID and both raw values in ascending
actor-ID order. Slots 1–7 are deliberately not compared by this packet, and
slot-0 agreement is not full parser, catalog, rendering, or semantic agreement.

## Binding and support boundary

The editor exposes only a domain-specific loader on `ProjectSnapshot`. It
internally selects the fixed Actors.dat and optional Meshes.dat inventory
records, requires the same `RootIdentity`, and reopens only files matched by
immutable enumeration identity tokens through `ProjectRoot`. Each no-follow
reopen verifies platform identity, size, and change token before a cancellable,
bounded read; the accepted bytes must then match the exact size and canonical
P03 SHA-256. The loader binds fixed parser identities through the crate-private
P04 `LegacyDocument` constructor.
Callers cannot provide bytes, paths, inventory records, parser identities, or
a generic document type.

This packet remains I1 and read-only. Consensus is scoped to the accepted
actor/base-mesh slice; it does not establish complete catalog validity, orphan
status, provenance, parser completeness, repair safety, or writer authority.
It adds no normalization, I2 promotion, or general media-topology model.
