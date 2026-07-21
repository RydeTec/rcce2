# Performance-reference evidence preparation

This packet prepares candidate evidence for the C01 performance contract. It
does not approve a fixture, reference machine, protocol, budget, or aggregate
record, and it does not add project fixture bytes to Git.

## Deterministic fixture preparation

`scripts/materialize_performance_fixtures.py` reads an exact revision through
the Git object database. It never reads the working `data/` directory. Before
creating output, it rejects symlinks, submodules, non-regular entries,
non-canonical paths, output anywhere under the repository, and evidence
files inside either the source tree or materialized fixture.
It also rejects case-folding or Unicode-normalization collisions, Windows
reserved names and characters, controls, trailing dots/spaces, and corpus trees
outside the canonical 4,096-file, 4,096-directory, 512 MiB total, 32 MiB per
file, path-depth, path-byte, and component-byte bounds. Blob reads are streamed
in 1 MiB chunks.

Run it only with disposable output and separate evidence paths:

```bash
python3 scripts/materialize_performance_fixtures.py \
  --repo . \
  --revision <full-commit-id> \
  --source-tree data \
  --tier default \
  --output <disposable-fixture-directory> \
  --manifest <candidate-materialization.json> \
  --metadata <candidate-preparation.json> \
  --captured-at <whole-second-UTC-timestamp>
```

The output directory, manifest, and metadata are staged independently and
promoted as one owned transaction; a failure at any promotion boundary removes
all targets created by that attempt. The manifest records the fully quoted,
replayable command. It is a typed `fixture-materialization` artifact with a sorted file
list and an `RCCE-CORPUS-TREE-V1` digest compatible with the performance
reference validator. The separate preparation metadata makes the source,
transform, topology, and pending review status explicit.

- `default` preserves every source path and byte from the exact Git tree.
- `small` and `large` are rejected and remain unavailable. A future transform
  must be schema-aware, openable as an RCCE project, and separately reviewed;
  path-hash subsets and namespaced byte replicas are not valid substitutes.

At base revision `94ed5adbd9adf94d88f8ff6b0e9432285af8d925`, an executed disposable
default materialization produced 1,180 files, 351,551,965 bytes, and candidate
tree digest `7be96d271706dc483d5497f274c0d72c6701d0cbc2b6d7b6ab41314586ef527e`.
The disposable bytes were moved to trash after verification. This observation
is preparation evidence only; it does not satisfy license, consent,
sensitivity, materialization, representativeness, or approval gates.

## Native Windows candidate profile

`scripts/capture_windows_performance_reference.ps1` runs in native Windows
PowerShell. The GPU backend is a required value emitted by the future
measurement harness; the script never guesses it from the adapter or OS. The
benchmark path must already exist on NTFS and is bound to its observed drive,
partition, disk number, and disk model.

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File scripts\capture_windows_performance_reference.ps1 `
  -OutputPath <new-candidate-profile.json> `
  -BenchmarkStoragePath <existing-NTFS-benchmark-directory> `
  -GpuBackend <backend-reported-by-measurement-harness> `
  -GpuAdapterName <adapter-name-reported-by-measurement-harness> `
  -GpuAdapterDriverVersion <driver-version-reported-by-measurement-harness> `
  -CapturedAtUtc <whole-second-UTC-timestamp>
```

The adapter name and driver version must match exactly one native
`Win32_VideoController`; the script does not select the first GPU. Storage
media type uses a `Get-PhysicalDisk` device association with a documented
`Win32_DiskDrive` fallback. The profile records desktop AC/no-battery observation, EDID availability
without retaining EDID identifiers or raw bytes, the exact capture commands,
and storage binding. Output is written to an owned `CreateNew` temporary file,
flushed, then promoted without overwriting an existing target. Timestamps are
parsed as real canonical UTC calendar values. It collects no account, environment, credential,
clipboard, file-content, or network-profile data. It performs no reboot, cache
reset, benchmark, memory measurement, or approval action. Cold-cache control
and harness validation therefore remain explicit blockers.

## Verification

```bash
python3 -m unittest -v \
  scripts.tests.test_materialize_performance_fixtures \
  scripts.tests.test_windows_performance_reference_capture
python3 scripts/check_performance_reference.py --self-test
python3 scripts/check_performance_reference.py
git diff --check
```

When native Windows PowerShell is unavailable, the PowerShell self-test is
reported as skipped rather than treated as native execution evidence.
