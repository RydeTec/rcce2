# RCCE project corpus scanner

`rcce-project-scanner` is the bounded M0 evidence scanner for
[`test-data/projects/manifest.toml`](../../test-data/projects/manifest.toml). It
does not parse legacy project formats and exposes no write, repair, copy, or
conversion operation. It validates the corpus declaration, inventories ready
fixtures, hashes exact bytes, and rejects anything that cannot be proven to be a
singly linked regular file beneath the selected corpus directory.

## Run

From the repository root with the accepted Rust toolchain:

```sh
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo run \
  --manifest-path tools/project-scanner/Cargo.toml --locked -- \
  --manifest test-data/projects/manifest.toml
```

When a ready project declares P07 canaries, the versioned registry is an
explicit second capability rather than an implicitly discovered host path:

```sh
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo run \
  --manifest-path tools/project-scanner/Cargo.toml --locked -- \
  --manifest test-data/projects/manifest.toml \
  --canary-registry test-data/canaries/registry-v1.toml
```

The initial P04 manifest contains no ready fixtures and therefore needs no
registry. Its successful result proves only that the closed schema, semantic
cross-record rules, declared absent roots, and budgets agree.

## Validation sequence

The scanner applies these gates in order:

1. Open the selected corpus directory as a directory descriptor. Open
   `manifest.toml` and its local schema as single components with no-follow,
   single-link checks and the absolute 64 MiB metadata ceiling before reading.
   The binary embeds the accepted schema bytes and pins SHA-256
   `765ce754a762a43d77783a48eb0c4197ac9029fb4d68eeb4cf76a23e12df284e`;
   the sibling must match exactly, and only the embedded bytes are compiled.
2. Decode UTF-8 TOML, validate its equivalent JSON data against the checked-in
   draft-2020-12 schema, and apply cross-record rules the schema cannot express:
   unique/collision-free identities, canonical state-class unions, transform and
   artifact references/ranges, occurrence bounds, and portable paths.
3. Require every non-ready fixture root to be absent. For a ready root, enumerate
   descriptor-relative from its opened handle. `lstat`/`openat(O_NOFOLLOW)`
   metadata accounts for every path, component, directory, file, byte and depth
   ceiling before any fixture file body is opened.
4. Reject symbolic links/reparse points, mount or volume boundaries, hardlinks (`nlink != 1`), sockets,
   devices, FIFOs and every non-regular object. Reopen each directory component
   relative to the fixture handle, then compare the opened file identity, link
   count and size with enumeration before hashing. Recheck them after hashing.
   Linux control and fixture reads also compare inode change-time seconds and
   nanoseconds from pre-stat through open and after read, closing transient-link
   create/remove windows before either file body is accepted.
5. Compare membership both ways, per-file size/SHA-256, aggregate counts and the
   `RCCE-CORPUS-TREE-V1` tree digest.
6. Load only an explicitly supplied P07 registry, when present. Resolve
   the closed v1 structure and its exact classification/support identities. A
   single typed canonical oracle exact-compares all 56 cells of the eight-operation
   by seven-class disposition/option table; the same table derives all policy
   assertions and evaluates the 12 accepted plus five rejected classification
   cases. The validator also checks two named sensitive policies,
   default and conditional assertion coverage, and two class-matched seed
   canaries. Assertion results are recomputed from operation rules; seed fixture
   envelopes and their exact single occurrences are independently verified. The
   registry directory is a closed tree containing only its two support files,
   `fixtures/`, and the two declared fixture bodies; complete marker tokens are
   forbidden in paths and outside their one declared fixture.
   Then resolve registry/version/canary/policy/placement and derive the marker from the
   accepted P07 NUL-delimited SHA-256 contract, reread the owning file through
   the confined handle, recheck its inventory hash, and enforce the exact
   registry occurrence count. Every path component and every inventoried body is
   also checked for registered or canary-like marker prefixes, including when no
   manifest canary is declared. Errors name IDs but never echo marker or body bytes.

The scanner never repairs a path, follows a target, falls back to the process
working directory, or trusts an extension as state classification. State classes
come from the manifest after its schema and semantic relationships validate.
Marker-like manifest keys/values are rejected before schema or semantic error
interpolation. Error display/debug output centrally replaces every marker-bearing
identifier, path, OS message, or location with a fixed redaction label.

## Platform truth

Linux uses descriptor-relative `openat`, `O_NOFOLLOW`, `fstatat`, file identities,
link counts, and mandatory `statx` mount IDs. Missing mount identity fails closed;
same-device bind mounts are never treated as proved-safe. The hostile suite covers
symlinks, hardlinks, transient hardlink creation/removal during a read, file and
directory swaps, outside-root marker non-observation, FIFOs/sockets, traversal,
mount injection, and every resource ceiling. A real bind-mount test runs when the
host grants mount permission and otherwise reports an explicit skip.

Windows opens the selected root handle with `FILE_FLAG_OPEN_REPARSE_POINT`, then
uses capability-directory no-follow opens for every descendant. It compares
volume serials, file indexes, link counts and sizes. The native Windows suite
executes direct-root junction, descendant junction, junction-swap, persistent
hardlink and outside-body non-observation cases. It does **not** prove detection
of a hardlink created and removed during the read: this backend exposes no file
change-time signal after the link count returns to one.

One concurrency boundary is deliberately narrow: pre/post identity, link-count,
size, and Linux inode-change-time checks detect a transient hardlink and suppress
the report/hash, but cannot undo bytes already read into scanner-private memory.
No ordinary regular-file API can prevent another process from calling `link(2)`.
Callers requiring the stronger literal “no read during any concurrent namespace
mutation” guarantee must first place intake in an immutable or private namespace;
the scanner does not claim that stronger guarantee.

`--platform-capability` emits semicolon-delimited `key=value` fields. In
particular, Linux reports
`hardlink-transient=detect-reject-no-publication`, while Windows reports
`hardlink-transient=unavailable`. A workflow whose acceptance requires the
stronger detection boundary must pass `--require-transient-hardlink-detection`;
the scanner fails closed before manifest access on Windows and other unsupported
backends.

Print the compiled capability statement with:

```sh
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo run \
  --manifest-path tools/project-scanner/Cargo.toml --locked -- \
  --manifest test-data/projects/manifest.toml --platform-capability
```

## Verification

```sh
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo fmt \
  --manifest-path tools/project-scanner/Cargo.toml --all -- --check
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo test \
  --manifest-path tools/project-scanner/Cargo.toml --locked
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo clippy \
  --manifest-path tools/project-scanner/Cargo.toml --all-targets --locked -- -D warnings
/home/ryan/.cargo/bin/rustup run 1.85.0 cargo build \
  --manifest-path tools/project-scanner/Cargo.toml --locked
```

The lockfile pins transitive `jsonschema` URL/IDNA/time releases because the
newest releases in those broad upstream ranges require Rust newer than the
repository's accepted 1.85 minimum.
