#!/usr/bin/env python3
"""Generate the closed, synthetic SE-M1-P05 actor/media consensus corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import struct
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "editor-rs" / "test-data" / "consensus"
MAX_FILES = 32
MAX_FILE_BYTES = 300_000
MAX_TOTAL_BYTES = 2_000_000


def string(raw: bytes) -> bytes:
    return struct.pack("<I", len(raw)) + raw


def actor(actor_id: int, race: bytes, mesh: int) -> tuple[bytes, dict[str, list[int]]]:
    out = bytearray(struct.pack("<H", actor_id))
    fields: dict[str, list[int]] = {}
    for name, raw, maximum in (
        ("race", race, 256),
        ("class", b"Fixture", 256),
        ("description", b"MIT synthetic actor", 4096),
        ("start_area", b"FixtureZone", 256),
        ("start_portal", b"Start", 256),
    ):
        assert len(raw) <= maximum
        start = len(out) + 4
        out += string(raw)
        fields[name] = [start, start + len(raw)]
    out += struct.pack("<HHff", 1, 1, 1.0, 0.5)
    out += struct.pack("<8H", mesh, 65535, *([65535] * 6))
    out += struct.pack("<35H", *([65535] * 35))
    out += struct.pack("<32H", *([65535] * 32))
    out += struct.pack("<h", 0)
    out += struct.pack("<80h", *([10, 10] * 40))
    out += struct.pack("<20h", *([0] * 20))
    out += struct.pack("<BBBBiBBiBBiB", 0, 1, 0, 0, 0, 0, 3, 16, 0, 0, 100, 0)
    return bytes(out), fields


def actors(records: list[tuple[int, bytes, int]], truncated: bool = False) -> tuple[bytes, list[dict[str, object]]]:
    out = bytearray()
    ranges = []
    for actor_id, race, mesh in records:
        start = len(out)
        record, fields = actor(actor_id, race, mesh)
        out += record
        ranges.append({
            "id": actor_id,
            "record": [start, len(out)],
            "race": [start + fields["race"][0], start + fields["race"][1]],
        })
    if truncated:
        out += struct.pack("<H", 99) + struct.pack("<I", 12) + b"tail"
    return bytes(out), ranges


def mesh_record(filename: bytes) -> tuple[bytes, tuple[int, int]]:
    prefix = struct.pack("<Bffffh", 0, 1.0, 0.0, 0.0, 0.0, 0)
    start = len(prefix) + 4
    record = prefix + string(filename)
    return record, (start, start + len(filename))


def meshes(entries: list[tuple[int, bytes]], aliases: list[tuple[int, int]] = (), invalid: list[int] = ()) -> tuple[bytes, list[dict[str, object]]]:
    index = bytearray(65535 * 4)
    body = bytearray()
    ranges = []
    offsets: dict[int, int] = {}
    for media_id, filename in entries:
        offset = len(index) + len(body)
        record, filename_range = mesh_record(filename)
        struct.pack_into("<i", index, media_id * 4, offset)
        offsets[media_id] = offset
        ranges.append({
            "id": media_id,
            "record": [offset, offset + len(record)],
            "filename": [offset + filename_range[0], offset + filename_range[1]],
        })
        body += record
    for alias_id, target_id in aliases:
        struct.pack_into("<i", index, alias_id * 4, offsets[target_id])
    for media_id in invalid:
        struct.pack_into("<i", index, media_id * 4, -1)
    return bytes(index + body), ranges


def build() -> dict[str, bytes]:
    files: dict[str, bytes] = {}
    manifest: dict[str, object] = {
        "schema": "rcce-se-m1-p05-actor-media-consensus-v1",
        "license": "MIT",
        "provenance": "Entirely synthetic; generated from literals without reading repository data or real projects.",
        "limits": {"max_files": MAX_FILES, "max_file_bytes": MAX_FILE_BYTES, "max_total_bytes": MAX_TOTAL_BYTES},
        "fixtures": {},
    }
    happy_records = [(1, b"NoMesh", 65535), (2, b"Alpha", 7), (3, b"MissingSlot", 9), (4, b"Beta", 8)]
    actor_bytes, actor_ranges = actors(happy_records)
    mesh_bytes, mesh_ranges = meshes([(7, b"Hero.b3d"), (8, b"Mage.b3d")])

    def add_project(name: str, *, include_catalog: bool, physical: dict[str, bytes], catalog: bytes = mesh_bytes, records=actor_ranges, actors_image=actor_bytes, outcome: str) -> None:
        prefix = f"{name}/"
        files[prefix + "Data/Server Data/Actors.dat"] = actors_image
        if include_catalog:
            files[prefix + "Data/Game Data/Meshes.dat"] = catalog
        for path, content in physical.items():
            files[prefix + path] = content
        manifest["fixtures"][name] = {
            "expected": outcome,
            "actor_records": records,
            "mesh_records": mesh_ranges if include_catalog else [],
        }

    add_project("happy", include_catalog=True, physical={"Data/Meshes/Hero.b3d": b"MIT-SYNTHETIC-HERO\n"}, outcome="four actors; actor 1 base mesh None; actor 2 present; actor 3 missing catalog slot; actor 4 missing physical")
    add_project("missing-catalog", include_catalog=False, physical={}, outcome="catalog absent")
    add_project("missing-physical", include_catalog=True, physical={}, outcome="catalog present; physical files absent")
    add_project("unique-case", include_catalog=True, physical={"Data/Meshes/hErO.B3D": b"MIT-SYNTHETIC-HERO\n", "Data/Meshes/MAGE.B3D": b"MIT-SYNTHETIC-MAGE\n"}, outcome="unique portable case resolution")

    duplicate_actors, duplicate_ranges = actors([(12, b"First", 65535), (12, b"Second", 65535)])
    add_project(
        "duplicate-id",
        include_catalog=False,
        physical={},
        records=duplicate_ranges,
        actors_image=duplicate_actors,
        outcome="duplicate actor IDs remain record-count evidence but force provisional consensus",
    )

    nested_actors, nested_actor_ranges = actors(
        [(20, b"NestedPresent", 20), (21, b"NestedMissing", 21)]
    )
    nested_meshes, nested_mesh_ranges = meshes(
        [(20, b"Creatures\\Wolf.b3d"), (21, b"Props\\Absent.b3d"), (22, b"Other.b3d")]
    )
    add_project(
        "nested-paths",
        include_catalog=True,
        physical={"Data/Meshes/Creatures/Wolf.b3d": b"MIT-SYNTHETIC-WOLF\n"},
        catalog=nested_meshes,
        records=nested_actor_ranges,
        actors_image=nested_actors,
        outcome="nested backslash present/missing lookups plus entry 22 unreferenced by selected actor-base evidence",
    )
    manifest["fixtures"]["nested-paths"]["mesh_records"] = nested_mesh_ranges

    traversal_actors, traversal_actor_ranges = actors([(22, b"Traversal", 22)])
    traversal_meshes, traversal_mesh_ranges = meshes([(22, b"..\\Outside.b3d")])
    add_project(
        "nested-traversal",
        include_catalog=True,
        physical={},
        catalog=traversal_meshes,
        records=traversal_actor_ranges,
        actors_image=traversal_actors,
        outcome="legacy traversal path preserves raw bytes and remains provisional",
    )
    manifest["fixtures"]["nested-traversal"]["mesh_records"] = traversal_mesh_ranges

    provisional_actors, provisional_ranges = actors(
        [(1, b"Before", 7), (2, b"raw-\xff-name", 7), (65535, b"High", 7), (3, b"After", 7)],
        truncated=True,
    )
    provisional_meshes, provisional_mesh_ranges = meshes(
        [(7, b"mesh-\xff.b3d"), (10, b"outside-slice.b3d")], aliases=[(8, 7)], invalid=[11]
    )
    add_project(
        "provisional",
        include_catalog=True,
        physical={"Data/Meshes/outside-slice.b3d": b"MIT-SYNTHETIC-OUTSIDE-SLICE\n"},
        catalog=provisional_meshes,
        records=provisional_ranges,
        actors_image=provisional_actors,
        outcome="preserve high-id disagreement, non-UTF8 raw/display split, truncated tail, alias, gap, selected-slice-unreferenced entry, and invalid offset",
    )
    manifest["fixtures"]["provisional"]["mesh_records"] = provisional_mesh_ranges
    manifest_bytes = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()
    files["manifest.json"] = manifest_bytes
    readme = """# SE-M1-P05 actor/media consensus fixtures

This closed corpus is generated entirely from literal synthetic data and is MIT licensed.
It contains no copied repository data, no real project data, and no links. `manifest.json`
records byte ranges and expected outcomes. Resource ceilings are enforced by the generator.

Run `python3 scripts/gen_actor_media_consensus.py` to regenerate or add `--check` to verify.
""".encode()
    files["README.md"] = readme
    sha_lines = [f"{hashlib.sha256(data).hexdigest()}  {path}" for path, data in sorted(files.items())]
    files["SHA256SUMS"] = ("\n".join(sha_lines) + "\n").encode()
    assert len(files) <= MAX_FILES
    assert all(len(data) <= MAX_FILE_BYTES for data in files.values())
    assert sum(map(len, files.values())) <= MAX_TOTAL_BYTES
    return files


def materialize(base: Path, files: dict[str, bytes]) -> None:
    for relative, content in files.items():
        target = base / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)


def current_files(base: Path) -> dict[str, bytes]:
    if base.is_symlink():
        raise ValueError("consensus corpus root must not be a link")
    if not base.exists():
        return {}
    files: dict[str, bytes] = {}
    for directory, names, filenames in os.walk(base, followlinks=False):
        directory_path = Path(directory)
        for name in [*names, *filenames]:
            path = directory_path / name
            if path.is_symlink():
                raise ValueError("consensus corpus must not contain links")
        for name in filenames:
            path = directory_path / name
            if not path.is_file():
                raise ValueError("consensus corpus must contain regular files only")
            relative = str(path.relative_to(base)).replace("\\", "/")
            files[relative] = path.read_bytes()
    return files


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = build()
    if args.check:
        actual = current_files(OUTPUT)
        if actual != expected:
            missing = sorted(expected.keys() - actual.keys())
            extra = sorted(actual.keys() - expected.keys())
            changed = sorted(path for path in expected.keys() & actual.keys() if expected[path] != actual[path])
            print(json.dumps({"missing": missing, "extra": extra, "changed": changed}, sort_keys=True))
            return 1
        print(f"actor-media consensus fixtures valid: {len(expected)} files")
        return 0
    with tempfile.TemporaryDirectory(dir=OUTPUT.parent if OUTPUT.parent.exists() else ROOT) as temporary:
        generated = Path(temporary) / "consensus"
        materialize(generated, expected)
        if OUTPUT.exists():
            for path in sorted(OUTPUT.rglob("*"), reverse=True):
                if path.is_file():
                    path.unlink()
                elif path.is_dir():
                    path.rmdir()
        OUTPUT.mkdir(parents=True, exist_ok=True)
        materialize(OUTPUT, expected)
    print(f"generated {len(expected)} files under {OUTPUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
