# ADR 0002 — Raw legacy strings and document provenance

- **Status:** Accepted
- **Date:** 2026-07-20

## Context and evidence

Legacy RCCE strings are length-prefixed byte sequences or lines whose encoding
is not uniformly declared. Persisted identity can depend on those bytes: media
paths, script and method names, area names, portal links, and filename-derived
records cannot safely use a replacement-character display string as authority.
The format matrix also records text files with unknown BOM, newline,
trailing-newline, extra-line, and non-UTF8 behavior. Binary records contain
unchanged floats whose original bits can matter to lossless proof, while media
catalogs contain raw offsets, aliases, gaps, orphans, and record order that a
semantic map does not retain.

## Decision

The shared project-format layer represents every legacy string with a
`RawLegacyString`-equivalent value containing:

- exact source bytes;
- source span and containing document identity;
- declared field encoding/byte constraints when known;
- a best-effort, non-authoritative display decoding;
- edited bytes only after representability validation;
- diagnostics for invalid, ambiguous, or lossy display.

Reference comparison, filesystem resolution, and serialization use validated
raw or canonical forms, never display text. Editing rejects unrepresentable
values unless a separately accepted conversion defines a new encoding.

Every loaded legacy document carries a provenance envelope: original bytes and
fingerprint, parsed spans, field-to-span lineage, skipped/unknown regions,
parser diagnostics, format row and level, and writer authority. Text provenance
also retains BOM, line endings, trailing newline, and unrelated raw lines.
Binary provenance retains unchanged scalar bits. Offset catalogs retain raw
index cells, record spans, alias groups, gaps, order, and unreachable data.

## Alternatives considered

1. Decode all strings as UTF-8 with replacement. Rejected because display loss
   would alter identity and paths.
2. Preserve only whole-file originals and regenerate on edit. Rejected because
   a bounded edit still needs lineage to preserve unrelated bytes.
3. Canonicalize text and catalogs on first save. Rejected because ordinary
   authoring would become an undeclared conversion.

## Consequences

- Domain types expose display text without pretending it is serialization
  authority.
- Writers patch declared spans or rebuild only under format-specific `I2`/`I3`
  contracts.
- Diagnostics can point to exact raw locations and explain why a field is
  read-only.
- Provenance consumes memory; large documents may retain immutable shared byte
  storage and spans rather than copied buffers.

## Invariants

- Unedited bytes round-trip exactly at `I2` and above.
- Display decoding never creates a filesystem path or persisted identity.
- An edit changes only declared spans plus structurally required bytes proved by
  that writer.
- Unknown regions and topology survive unrelated edits.
- Tolerant runtime normalization is recorded as a diagnostic, not written back
  implicitly.
- Provenance is invalidated or refreshed after every successful commit.

## Verification and exit evidence

Fixtures cover NUL/control bytes, non-UTF8 sequences, embedded separators,
case/normalization collisions, BOMs, CRLF/LF, missing final newline, duplicate
and malformed records, float signed zero/NaN payloads, catalog aliases,
out-of-order spans, gaps, orphans, and truncated tails. Verification includes
byte-identical no-op output and mutation-local byte diffs. Client, server, and
editor semantic consumers compare results on the same accepted fixtures.

## Dependencies

- [ADR-0001](0001-compatibility-ladder-and-support-disposition.md)
- [Project-format matrix](../compat/project-format-matrix.md)

## Supersession and change process

Changing authoritative encoding, identity comparison, or provenance retention
requires a new ADR plus explicit conversion and cross-consumer evidence. A new
format may use native strings only within its declared version boundary.
