#!/usr/bin/env python3
"""Prepare deterministic performance fixtures from an exact Git revision.

This tool reads blobs from Git's object database.  It never reads the working
``data/`` directory and rejects links or other non-regular tree entries before
creating output.  The resulting evidence remains a candidate until the
required license, consent, sensitivity, representativeness, and human approval
records exist.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
from typing import Any


HASH_DOMAIN = "RCCE-CORPUS-TREE-V1"
DEFAULT_SMALL_MAX_FILES = 128
DEFAULT_SMALL_MAX_BYTES = 32 * 1024 * 1024
DEFAULT_LARGE_REPLICAS = 4
MAX_MANIFEST_ENTRIES = 100_000
TIMESTAMP_RE = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z")


class MaterializationError(RuntimeError):
    """A fail-closed fixture preparation error."""


class _GitBlobReader:
    """One bounded-memory `git cat-file --batch` object reader."""

    def __init__(self, repo: Path):
        self.repo = repo
        self.process: subprocess.Popen[bytes] | None = None

    def __enter__(self) -> "_GitBlobReader":
        try:
            self.process = subprocess.Popen(
                ["git", "cat-file", "--batch"],
                cwd=self.repo,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
        except OSError as exc:
            raise MaterializationError(f"Git batch object reader failed to start: {exc}") from None
        return self

    def read(self, oid: str, expected_size: int) -> bytes:
        process = self.process
        if process is None or process.stdin is None or process.stdout is None:
            raise MaterializationError("Git batch object reader is not open")
        try:
            process.stdin.write(oid.encode("ascii") + b"\n")
            process.stdin.flush()
            header = process.stdout.readline().rstrip(b"\n").split(b" ")
            if len(header) != 3 or header[1] != b"blob":
                raise MaterializationError(f"Git batch object reader rejected {oid}")
            size = int(header[2])
            blob = process.stdout.read(size)
            terminator = process.stdout.read(1)
        except (BrokenPipeError, OSError, ValueError):
            raise MaterializationError(f"Git batch object read failed for {oid}") from None
        if size != expected_size or len(blob) != size or terminator != b"\n":
            raise MaterializationError(f"Git blob size changed for {oid}")
        return blob

    def __exit__(self, exc_type: Any, exc: Any, traceback: Any) -> None:
        process = self.process
        if process is None:
            return
        try:
            if process.stdin is not None:
                process.stdin.close()
            if exc_type is None:
                return_code = process.wait()
                if return_code != 0:
                    stderr = process.stderr.read().decode("utf-8", "replace") if process.stderr else ""
                    raise MaterializationError(f"Git batch object reader failed: {stderr.strip()}")
            else:
                process.kill()
                process.wait()
        finally:
            if process.stdout is not None:
                process.stdout.close()
            if process.stderr is not None:
                process.stderr.close()


def _run_git(repo: Path, *args: str, binary: bool = False) -> str | bytes:
    try:
        completed = subprocess.run(
            ["git", *args],
            cwd=repo,
            check=True,
            capture_output=True,
            text=not binary,
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        detail = ""
        if isinstance(exc, subprocess.CalledProcessError) and exc.stderr:
            detail = exc.stderr.decode("utf-8", "replace") if binary else exc.stderr
        raise MaterializationError(f"Git object read failed: {detail.strip()}") from None
    return completed.stdout


def _canonical_source_tree(value: str) -> str:
    if not value or "\\" in value or value.startswith("/") or value.endswith("/"):
        raise MaterializationError("source tree must be a canonical relative POSIX path")
    path = PurePosixPath(value)
    if any(part in {"", ".", ".."} for part in path.parts) or path.as_posix() != value:
        raise MaterializationError("source tree must be a canonical relative POSIX path")
    return value


def _canonical_manifest_path(value: str) -> str:
    if not value or "\\" in value or value.startswith("/") or value.endswith("/"):
        raise MaterializationError(f"non-canonical Git path: {value!r}")
    parts = value.split("/")
    if any(part in {"", ".", ".."} for part in parts):
        raise MaterializationError(f"non-canonical Git path: {value!r}")
    encoded = value.encode("utf-8")
    if len(parts) > 64 or len(encoded) > 4096 or any(len(part.encode("utf-8")) > 255 for part in parts):
        raise MaterializationError(f"Git path exceeds evidence limits: {value!r}")
    return value


def _resolve_commit(repo: Path, revision: str) -> str:
    resolved = str(_run_git(repo, "rev-parse", "--verify", f"{revision}^{{commit}}" )).strip()
    if not re.fullmatch(r"[0-9a-f]{40,64}", resolved):
        raise MaterializationError("revision did not resolve to a full Git commit object ID")
    return resolved


def _list_git_entries(repo: Path, commit: str, source_tree: str) -> list[dict[str, Any]]:
    # Resolve this exact tree before listing it. `ls-tree` and `cat-file` then
    # operate on objects, so a dirty worktree and filesystem links are irrelevant.
    tree_oid = str(_run_git(repo, "rev-parse", "--verify", f"{commit}:{source_tree}" )).strip()
    if not re.fullmatch(r"[0-9a-f]{40,64}", tree_oid):
        raise MaterializationError("source tree did not resolve to a Git object")
    raw = _run_git(repo, "ls-tree", "-r", "-z", "-l", tree_oid, binary=True)
    assert isinstance(raw, bytes)
    entries: list[dict[str, Any]] = []
    for record in raw.split(b"\0"):
        if not record:
            continue
        try:
            header, relative_bytes = record.split(b"\t", 1)
            mode_bytes, type_bytes, oid_bytes, size_bytes = header.split(b" ", 3)
            relative = relative_bytes.decode("utf-8")
            mode = mode_bytes.decode("ascii")
            object_type = type_bytes.decode("ascii")
            oid = oid_bytes.decode("ascii")
            size = int(size_bytes.decode("ascii"))
        except (UnicodeDecodeError, ValueError):
            raise MaterializationError("Git tree contains an unrepresentable entry") from None
        if object_type != "blob" or mode not in {"100644", "100755"}:
            raise MaterializationError(
                f"non-regular Git entry rejected before materialization: {relative!r} ({mode} {object_type})"
            )
        path = _canonical_manifest_path(f"{source_tree}/{relative}")
        entries.append({"path": path, "oid": oid, "size": size, "mode": mode})
    entries.sort(key=lambda entry: entry["path"])
    if not entries:
        raise MaterializationError("source tree contains no regular files")
    if len(entries) > MAX_MANIFEST_ENTRIES:
        raise MaterializationError(f"source tree exceeds {MAX_MANIFEST_ENTRIES} manifest entries")
    return entries


def _select_small(entries: list[dict[str, Any]], max_files: int, max_bytes: int) -> list[dict[str, Any]]:
    if max_files < 1 or max_bytes < 1:
        raise MaterializationError("small tier limits must be positive")
    ranked = sorted(entries, key=lambda entry: (hashlib.sha256(entry["path"].encode("utf-8")).digest(), entry["path"]))
    selected: list[dict[str, Any]] = []
    byte_count = 0
    for entry in ranked:
        if len(selected) >= max_files:
            break
        if byte_count + entry["size"] <= max_bytes:
            selected.append(entry)
            byte_count += entry["size"]
    if not selected:
        raise MaterializationError("small tier limits select no complete source file")
    return sorted(selected, key=lambda entry: entry["path"])


def _transform_entries(
    entries: list[dict[str, Any]],
    tier: str,
    small_max_files: int,
    small_max_bytes: int,
    large_replicas: int,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if tier == "default":
        return entries, {
            "kind": "exact-git-tree",
            "topology": "source paths preserved",
            "permission_handling": "regular-file bytes preserved; executable mode not reproduced",
        }
    if tier == "small":
        selected = _select_small(entries, small_max_files, small_max_bytes)
        return selected, {
            "kind": "stable-path-hash-subset",
            "topology": "selected source paths preserved",
            "ranking": "sha256(UTF-8 source path), then source path",
            "max_files": small_max_files,
            "max_bytes": small_max_bytes,
            "source_file_count": len(entries),
            "permission_handling": "regular-file bytes preserved; executable mode not reproduced",
        }
    if tier == "large":
        if large_replicas < 2:
            raise MaterializationError("large tier requires at least two replicas")
        if len(entries) * large_replicas > MAX_MANIFEST_ENTRIES:
            raise MaterializationError(f"large tier exceeds {MAX_MANIFEST_ENTRIES} manifest entries")
        transformed: list[dict[str, Any]] = []
        for replica in range(large_replicas):
            prefix = f"replica-{replica:03d}"
            for entry in entries:
                transformed.append({**entry, "path": f"{prefix}/{entry['path']}"})
        transformed.sort(key=lambda entry: entry["path"])
        return transformed, {
            "kind": "namespaced-byte-exact-replication",
            "topology": "replica-NNN/source-path",
            "replicas": large_replicas,
            "source_file_count": len(entries),
            "permission_handling": "regular-file bytes preserved; executable mode not reproduced",
        }
    raise MaterializationError(f"unsupported tier: {tier}")


def _corpus_tree_sha256(files: list[dict[str, Any]]) -> str:
    digest = hashlib.sha256(b"RCCE-CORPUS-TREE-V1\0")
    previous = ""
    for entry in files:
        path = _canonical_manifest_path(entry["path"])
        if path <= previous:
            raise MaterializationError("manifest paths must be sorted and unique")
        size = entry["size"]
        sha256 = entry["sha256"]
        if not isinstance(size, int) or size < 0 or size > 2**64 - 1:
            raise MaterializationError("manifest size is outside encoded u64 range")
        if not re.fullmatch(r"[0-9a-f]{64}", sha256):
            raise MaterializationError("manifest file digest is not lowercase SHA-256")
        encoded = path.encode("utf-8")
        digest.update(len(encoded).to_bytes(8, "little"))
        digest.update(encoded)
        digest.update(size.to_bytes(8, "little"))
        digest.update(bytes.fromhex(sha256))
        previous = path
    return digest.hexdigest()


def _write_json_new(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, sort_keys=True, indent=2, ensure_ascii=False)
        stream.write("\n")


def materialize_fixture(
    *,
    repo: Path,
    revision: str,
    source_tree: str,
    tier: str,
    output: Path,
    manifest_path: Path,
    metadata_path: Path,
    captured_at: str,
    small_max_files: int = DEFAULT_SMALL_MAX_FILES,
    small_max_bytes: int = DEFAULT_SMALL_MAX_BYTES,
    large_replicas: int = DEFAULT_LARGE_REPLICAS,
) -> dict[str, Any]:
    repo = repo.resolve(strict=True)
    if not (repo / ".git").exists() and not str(_run_git(repo, "rev-parse", "--git-dir")).strip():
        raise MaterializationError("repo is not a Git worktree")
    source_tree = _canonical_source_tree(source_tree)
    if not TIMESTAMP_RE.fullmatch(captured_at):
        raise MaterializationError("captured-at must be an explicit UTC timestamp like 2026-07-21T12:00:00Z")
    output = output.absolute()
    source_worktree = (repo / Path(*source_tree.split("/"))).absolute()
    resolved_output = output.resolve(strict=False)
    resolved_source = source_worktree.resolve(strict=False)
    if resolved_output == resolved_source or resolved_source in resolved_output.parents:
        raise MaterializationError("output must not be inside the repository source tree")
    resolved_manifest = manifest_path.absolute().resolve(strict=False)
    resolved_metadata = metadata_path.absolute().resolve(strict=False)
    for evidence_target in (resolved_manifest, resolved_metadata):
        if evidence_target == resolved_output or resolved_output in evidence_target.parents:
            raise MaterializationError("evidence targets must be outside materialized fixture bytes")
        if evidence_target == resolved_source or resolved_source in evidence_target.parents:
            raise MaterializationError("evidence targets must not be inside the repository source tree")
    if resolved_manifest == resolved_metadata:
        raise MaterializationError("manifest and metadata targets must be distinct")
    if output.exists() or manifest_path.exists() or metadata_path.exists():
        raise MaterializationError("output, manifest, and metadata targets must not already exist")

    commit = _resolve_commit(repo, revision)
    source_entries = _list_git_entries(repo, commit, source_tree)
    transformed, transform = _transform_entries(
        source_entries, tier, small_max_files, small_max_bytes, large_replicas
    )
    stage_parent = output.parent.resolve(strict=True)
    stage = Path(tempfile.mkdtemp(prefix=f".{output.name}.stage-", dir=stage_parent))
    manifest_files: list[dict[str, Any]] = []
    try:
        with _GitBlobReader(repo) as blob_reader:
            for entry in transformed:
                blob = blob_reader.read(entry["oid"], entry["size"])
                destination = stage.joinpath(*entry["path"].split("/"))
                destination.parent.mkdir(parents=True, exist_ok=True)
                with destination.open("xb") as stream:
                    stream.write(blob)
                manifest_files.append({
                    "path": entry["path"],
                    "size": len(blob),
                    "sha256": hashlib.sha256(blob).hexdigest(),
                })
        tree_sha256 = _corpus_tree_sha256(manifest_files)
        command = (
            "python3 scripts/materialize_performance_fixtures.py "
            f"--revision {commit} --source-tree {source_tree} --tier {tier}"
        )
        if tier == "small":
            command += f" --small-max-files {small_max_files} --small-max-bytes {small_max_bytes}"
        elif tier == "large":
            command += f" --large-replicas {large_replicas}"
        artifact = {
            "schema_version": 1,
            "kind": "fixture-materialization",
            "subject_id": f"{tier}-v1",
            "artifact_revision": 1,
            "captured_at": captured_at,
            "payload": {
                "hash_domain": HASH_DOMAIN,
                "files": manifest_files,
                "tree_sha256": tree_sha256,
                "file_count": len(manifest_files),
                "byte_count": sum(entry["size"] for entry in manifest_files),
                "source_revision": commit,
                "command": command,
            },
        }
        metadata = {
            "schema_version": 1,
            "kind": "fixture-materialization-preparation",
            "tier": tier,
            "review_status": "pending-human-review",
            "representativeness": "pending-human-review" if tier in {"small", "large"} else "repository-default-candidate",
            "source": {
                "kind": "git-object-database",
                "revision": commit,
                "tree": source_tree,
                "working_tree_bytes_read": False,
            },
            "transform": transform,
            "manifest": {
                "hash_domain": HASH_DOMAIN,
                "tree_sha256": tree_sha256,
                "file_count": len(manifest_files),
                "byte_count": sum(entry["size"] for entry in manifest_files),
            },
            "approval_prohibition": "This preparation artifact is not license, consent, sensitivity, representativeness, fixture, machine, budget, or aggregate approval.",
        }
        os.replace(stage, output)
        _write_json_new(manifest_path, artifact)
        _write_json_new(metadata_path, metadata)
        return artifact
    except Exception:
        if stage.exists():
            shutil.rmtree(stage)
        raise


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--revision", required=True)
    parser.add_argument("--source-tree", default="data")
    parser.add_argument("--tier", required=True, choices=("small", "default", "large"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--captured-at", required=True)
    parser.add_argument("--small-max-files", type=int, default=DEFAULT_SMALL_MAX_FILES)
    parser.add_argument("--small-max-bytes", type=int, default=DEFAULT_SMALL_MAX_BYTES)
    parser.add_argument("--large-replicas", type=int, default=DEFAULT_LARGE_REPLICAS)
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    try:
        artifact = materialize_fixture(
            repo=args.repo,
            revision=args.revision,
            source_tree=args.source_tree,
            tier=args.tier,
            output=args.output,
            manifest_path=args.manifest,
            metadata_path=args.metadata,
            captured_at=args.captured_at,
            small_max_files=args.small_max_files,
            small_max_bytes=args.small_max_bytes,
            large_replicas=args.large_replicas,
        )
    except MaterializationError as exc:
        print(f"materialization rejected: {exc}", file=os.sys.stderr)
        return 1
    print(
        "materialization candidate prepared: "
        f"{artifact['payload']['file_count']} files, {artifact['payload']['byte_count']} bytes, "
        f"RCCE-CORPUS-TREE-V1 {artifact['payload']['tree_sha256']}"
    )
    print("approval status: pending human review")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
