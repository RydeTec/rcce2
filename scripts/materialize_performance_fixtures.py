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
import ctypes
import datetime
import errno
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
from typing import Any
import unicodedata


HASH_DOMAIN = "RCCE-CORPUS-TREE-V1"
MAX_MANIFEST_ENTRIES = 100_000
MAX_DEFAULT_FILES = 4_096
MAX_DEFAULT_DIRECTORIES = 2_048
MAX_DEFAULT_BYTES = 512 * 1024 * 1024
MAX_DEFAULT_FILE_BYTES = 32 * 1024 * 1024
MAX_PATH_DEPTH = 32
MAX_PATH_BYTES = 2_048
MAX_COMPONENT_BYTES = 255
STREAM_CHUNK_BYTES = 1024 * 1024
FREE_SPACE_MARGIN_BYTES = 64 * 1024 * 1024
TIMESTAMP_RE = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z")
WINDOWS_RESERVED = {"con", "prn", "aux", "nul", *(f"com{i}" for i in range(1, 10)), *(f"lpt{i}" for i in range(1, 10))}


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

    def copy(self, oid: str, expected_size: int, destination: Any) -> tuple[str, int]:
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
        except (BrokenPipeError, OSError, ValueError):
            raise MaterializationError(f"Git batch object read failed for {oid}") from None
        if size != expected_size:
            raise MaterializationError(f"Git blob size changed for {oid}")
        digest, count = _copy_stream(process.stdout, destination, size)
        if process.stdout.read(1) != b"\n":
            raise MaterializationError(f"Git batch object framing failed for {oid}")
        return digest, count

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
    if len(parts) > MAX_PATH_DEPTH:
        raise MaterializationError(f"Git path depth exceeds {MAX_PATH_DEPTH}: {value!r}")
    if len(encoded) > MAX_PATH_BYTES:
        raise MaterializationError(f"Git path bytes exceed {MAX_PATH_BYTES}: {value!r}")
    if any(len(part.encode("utf-8")) > MAX_COMPONENT_BYTES for part in parts):
        raise MaterializationError(f"Git path component exceeds {MAX_COMPONENT_BYTES} bytes: {value!r}")
    return value


def _copy_stream(source: Any, destination: Any, size: int) -> tuple[str, int]:
    digest = hashlib.sha256()
    remaining = size
    count = 0
    while remaining:
        chunk = source.read(min(STREAM_CHUNK_BYTES, remaining))
        if not chunk:
            raise MaterializationError("Git blob ended before its declared size")
        destination.write(chunk)
        digest.update(chunk)
        count += len(chunk)
        remaining -= len(chunk)
    return digest.hexdigest(), count


def _validate_portable_paths(entries: list[dict[str, Any]]) -> None:
    portable: dict[str, str] = {}
    for entry in entries:
        path = _canonical_manifest_path(entry["path"])
        normalized_parts: list[str] = []
        for component in path.split("/"):
            if unicodedata.normalize("NFC", component) != component:
                raise MaterializationError(f"Git path is not NFC-normalized: {path!r}")
            if any(ord(character) < 32 or ord(character) == 127 for character in component):
                raise MaterializationError(f"Git path contains a control character: {path!r}")
            if any(character in '<>:"|?*' for character in component):
                raise MaterializationError(f"Git path contains a Windows-forbidden character: {path!r}")
            if component.endswith((".", " ")):
                raise MaterializationError(f"Git path has a trailing dot or space: {path!r}")
            if component.split(".", 1)[0].casefold() in WINDOWS_RESERVED:
                raise MaterializationError(f"Git path contains a reserved Windows name: {path!r}")
            normalized_parts.append(unicodedata.normalize("NFC", component).casefold())
        key = "/".join(normalized_parts)
        previous = portable.get(key)
        if previous is not None and previous != path:
            raise MaterializationError(f"Git paths collide under portable case/Unicode rules: {previous!r}, {path!r}")
        portable[key] = path


def _enforce_default_limits(entries: list[dict[str, Any]]) -> None:
    if len(entries) > MAX_MANIFEST_ENTRIES or len(entries) > MAX_DEFAULT_FILES:
        raise MaterializationError(f"default fixture file count exceeds {MAX_DEFAULT_FILES}")
    directories: set[str] = set()
    total_bytes = 0
    for entry in entries:
        path = _canonical_manifest_path(entry["path"])
        size = entry["size"]
        if size > MAX_DEFAULT_FILE_BYTES:
            raise MaterializationError(f"default fixture per-file limit exceeds {MAX_DEFAULT_FILE_BYTES} bytes: {path}")
        total_bytes += size
        parts = path.split("/")
        if len(parts) > MAX_PATH_DEPTH:
            raise MaterializationError(f"default fixture depth exceeds {MAX_PATH_DEPTH}: {path}")
        directories.update("/".join(parts[:index]) for index in range(1, len(parts)))
    if total_bytes > MAX_DEFAULT_BYTES:
        raise MaterializationError(f"default fixture total bytes exceed {MAX_DEFAULT_BYTES}")
    if len(directories) > MAX_DEFAULT_DIRECTORIES:
        raise MaterializationError(f"default fixture directory count exceeds {MAX_DEFAULT_DIRECTORIES}")


def _validate_timestamp(value: str) -> None:
    if not TIMESTAMP_RE.fullmatch(value):
        raise MaterializationError("captured-at must be a real canonical UTC timestamp like 2026-07-21T12:00:00Z")
    try:
        parsed = datetime.datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=datetime.timezone.utc)
    except ValueError:
        raise MaterializationError("captured-at must be a real canonical UTC timestamp like 2026-07-21T12:00:00Z") from None
    if parsed.strftime("%Y-%m-%dT%H:%M:%SZ") != value:
        raise MaterializationError("captured-at must be a real canonical UTC timestamp like 2026-07-21T12:00:00Z")


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


def _stage_json(path: Path, value: dict[str, Any]) -> Path:
    parent = path.parent.resolve(strict=True)
    descriptor, temporary = tempfile.mkstemp(prefix=f".{path.name}.stage-", dir=parent)
    temporary_path = Path(temporary)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            json.dump(value, stream, sort_keys=True, indent=2, ensure_ascii=False)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        return temporary_path
    except Exception:
        temporary_path.unlink(missing_ok=True)
        raise


def _remove_owned(path: Path) -> None:
    if path.is_dir() and not path.is_symlink():
        shutil.rmtree(path)
    elif path.exists() or path.is_symlink():
        path.unlink()


def _publish_noreplace(source: Path, target: Path) -> None:
    """Atomically publish one staged path without replacing a concurrent target."""
    if sys.platform.startswith("linux"):
        libc = ctypes.CDLL(None, use_errno=True)
        renameat2 = getattr(libc, "renameat2", None)
        if renameat2 is None:
            raise MaterializationError("atomic no-replace publication is unsupported on this Linux runtime")
        renameat2.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
        renameat2.restype = ctypes.c_int
        result = renameat2(-100, os.fsencode(source), -100, os.fsencode(target), 1)
        if result == 0:
            return
        error = ctypes.get_errno()
        if error == errno.EEXIST:
            raise MaterializationError(f"publication target already exists: {target}")
        if error in {errno.ENOSYS, errno.EINVAL, errno.ENOTSUP}:
            raise MaterializationError("atomic no-replace publication is unsupported by this filesystem/runtime")
        raise MaterializationError(f"atomic no-replace publication failed: {os.strerror(error)}")
    if os.name == "nt":
        try:
            os.rename(source, target)
        except FileExistsError:
            raise MaterializationError(f"publication target already exists: {target}") from None
        return
    raise MaterializationError("atomic no-replace publication is unsupported on this platform")


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
) -> dict[str, Any]:
    repo = repo.resolve(strict=True)
    if not (repo / ".git").exists() and not str(_run_git(repo, "rev-parse", "--git-dir")).strip():
        raise MaterializationError("repo is not a Git worktree")
    source_tree = _canonical_source_tree(source_tree)
    if tier != "default":
        raise MaterializationError(
            "only the exact default Git tree is supported; small and large remain unavailable pending schema-aware openable transforms"
        )
    _validate_timestamp(captured_at)
    output = output.absolute()
    source_worktree = (repo / Path(*source_tree.split("/"))).absolute()
    resolved_output = output.resolve(strict=False)
    resolved_source = source_worktree.resolve(strict=False)
    if resolved_output == repo or repo in resolved_output.parents:
        raise MaterializationError("output must not be inside the repository")
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
    _validate_portable_paths(source_entries)
    _enforce_default_limits(source_entries)
    transform = {
        "kind": "exact-git-tree",
        "topology": "source paths preserved",
        "permission_handling": "regular-file bytes preserved; executable mode not reproduced",
    }
    stage_parent = output.parent.resolve(strict=True)
    manifest_path.parent.resolve(strict=True)
    metadata_path.parent.resolve(strict=True)
    required_space = sum(entry["size"] for entry in source_entries) + FREE_SPACE_MARGIN_BYTES
    free_space = shutil.disk_usage(stage_parent).free
    if free_space < required_space:
        raise MaterializationError(
            f"insufficient free space before staging: need {required_space} bytes "
            f"(exact fixture bytes plus {FREE_SPACE_MARGIN_BYTES}-byte safety margin), have {free_space}"
        )
    stage = Path(tempfile.mkdtemp(prefix=f".{output.name}.stage-", dir=stage_parent))
    manifest_stage: Path | None = None
    metadata_stage: Path | None = None
    manifest_files: list[dict[str, Any]] = []
    try:
        with _GitBlobReader(repo) as blob_reader:
            for entry in source_entries:
                destination = stage.joinpath(*entry["path"].split("/"))
                destination.parent.mkdir(parents=True, exist_ok=True)
                with destination.open("xb") as stream:
                    digest, count = blob_reader.copy(entry["oid"], entry["size"], stream)
                manifest_files.append({
                    "path": entry["path"],
                    "size": count,
                    "sha256": digest,
                })
        tree_sha256 = _corpus_tree_sha256(manifest_files)
        command = shlex.join([
            "python3", str(Path(__file__).resolve()),
            "--repo", str(repo), "--revision", commit,
            "--source-tree", source_tree, "--tier", tier,
            "--output", str(output), "--manifest", str(manifest_path.absolute()),
            "--metadata", str(metadata_path.absolute()), "--captured-at", captured_at,
        ])
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
            "representativeness": "repository-default-candidate",
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
        manifest_stage = _stage_json(manifest_path, artifact)
        metadata_stage = _stage_json(metadata_path, metadata)
        promotions = [(stage, output), (manifest_stage, manifest_path), (metadata_stage, metadata_path)]
        promoted: list[Path] = []
        try:
            for staged, target in promotions:
                _publish_noreplace(staged, target)
                promoted.append(target)
        except (OSError, MaterializationError) as exc:
            for target in reversed(promoted):
                _remove_owned(target)
            if isinstance(exc, MaterializationError):
                raise
            raise MaterializationError(f"promotion failed; all owned targets rolled back: {exc}") from None
        return artifact
    except Exception:
        for owned in (stage, manifest_stage, metadata_stage):
            if owned is not None:
                _remove_owned(owned)
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
