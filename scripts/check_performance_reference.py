#!/usr/bin/env python3
"""Validate the versioned Super Editor performance reference contract."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import math
import re
import os
import shutil
import subprocess
import stat
import sys
import tempfile
import tomllib
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONTRACT = ROOT / "docs/compat/performance-reference-v1.toml"
DEFAULT_SCHEMA = ROOT / "docs/compat/performance-reference-schema-v1.json"
EVIDENCE_ROOT = ROOT / "docs/compat/performance-reference-evidence"
MAX_EVIDENCE_FILES = 4096
MAX_EVIDENCE_DIRECTORIES = 4096
MAX_EVIDENCE_FILE_BYTES = 64 * 1024 * 1024
MAX_EVIDENCE_TOTAL_BYTES = 512 * 1024 * 1024
MAX_TYPED_JSON_BYTES = 8 * 1024 * 1024
MAX_JSON_DEPTH = 64
MAX_MANIFEST_ENTRIES = 100_000
MAX_RELATIVE_PATH_DEPTH = 64
MAX_PATH_COMPONENT_BYTES = 255
MAX_RELATIVE_PATH_BYTES = 4096

FIXED_GATES = {
    "visible-open-progress": ("project_open_visible_progress_latency", "maximum", 250.0, "all-approved", "all-approved"),
    "default-project-ready": ("default_project_ready_latency", "maximum", 5000.0, "default", "all-approved"),
    "lens-focus-feedback": ("lens_or_focus_feedback_latency", "p95", 100.0, "all-approved", "all-approved"),
    "incremental-diagnostics": ("incremental_diagnostics_latency", "p95", 250.0, "all-approved", "all-approved"),
    "cancellation-acknowledgement": ("cancellation_acknowledgement_latency", "maximum", 250.0, "all-approved", "all-approved"),
    "one-viewport-frame-time": ("interactive_world_viewport_frame_time", "p95", 16.67, "reference", "all-approved"),
}

EXPECTED_BUDGETS = {
    **{
        f"memory-{tier}-{kind}": (
            "memory", tier, f"{'peak' if kind == 'peak' else 'steady_state'}_working_set_bytes",
            "maximum", "bytes", "m1-selection",
        )
        for tier in ("small", "default", "large")
        for kind in ("peak", "steady")
    },
    **{
        f"commit-duration-{tier}": (
            "commit-duration", tier, "representative_adr0005_commit_duration",
            "p95", "milliseconds", "pre-write-m2",
        )
        for tier in ("small", "default", "large")
    },
}

MACHINE_INTEGER_FIELDS = {
    "physical_cores", "logical_processors", "ram_bytes", "display_width_pixels",
    "display_height_pixels", "display_refresh_hz", "applied_dpi", "storage_device_bytes",
}
MACHINE_STRING_FIELDS = {
    "os_name", "os_version", "os_build", "kernel_version", "architecture", "cpu_model",
    "gpu_model", "gpu_backend", "gpu_driver_version", "storage_model", "storage_media_type",
    "filesystem", "display_physical_size", "power_plan", "power_source", "rustc", "storage_path_binding",
}


def _is_type(value: Any, wanted: str) -> bool:
    if wanted == "object":
        return isinstance(value, dict)
    if wanted == "array":
        return isinstance(value, list)
    if wanted == "string":
        return isinstance(value, str)
    if wanted == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if wanted == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    return True


def _is_finite_number(value: Any) -> bool:
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        return False
    try:
        return math.isfinite(float(value))
    except (OverflowError, TypeError, ValueError):
        return False


def _schema_errors(value: Any, rule: dict[str, Any], root: dict[str, Any], where: str = "$") -> list[str]:
    errors: list[str] = []
    if "$ref" in rule:
        target: Any = root
        for part in rule["$ref"].removeprefix("#/").split("/"):
            target = target[part]
        errors.extend(_schema_errors(value, target, root, where))
        rule = {key: child for key, child in rule.items() if key != "$ref"}
    for child in rule.get("allOf", []):
        errors.extend(_schema_errors(value, child, root, where))
    if "anyOf" in rule and all(_schema_errors(value, child, root, where) for child in rule["anyOf"]):
        errors.append(f"{where}: does not satisfy any allowed schema")
    if "not" in rule and not _schema_errors(value, rule["not"], root, where):
        errors.append(f"{where}: satisfies a forbidden schema")
    if "if" in rule and not _schema_errors(value, rule["if"], root, where) and "then" in rule:
        errors.extend(_schema_errors(value, rule["then"], root, where))
    if "type" in rule and not _is_type(value, rule["type"]):
        return [f"{where}: expected {rule['type']}"]
    if "const" in rule and value != rule["const"]:
        errors.append(f"{where}: expected fixed value {rule['const']!r}")
    if "enum" in rule and value not in rule["enum"]:
        errors.append(f"{where}: unsupported value {value!r}")
    if isinstance(value, dict):
        required = set(rule.get("required", []))
        missing = sorted(required - value.keys())
        errors.extend(f"{where}: missing required property {name}" for name in missing)
        properties = rule.get("properties", {})
        if rule.get("additionalProperties") is False:
            errors.extend(f"{where}: unknown property {name}" for name in sorted(value.keys() - properties.keys()))
        for name, child in value.items():
            if name in properties:
                errors.extend(_schema_errors(child, properties[name], root, f"{where}.{name}"))
    if isinstance(value, list):
        if len(value) < rule.get("minItems", 0):
            errors.append(f"{where}: requires at least {rule['minItems']} items")
        if "maxItems" in rule and len(value) > rule["maxItems"]:
            errors.append(f"{where}: permits at most {rule['maxItems']} items")
        if rule.get("uniqueItems"):
            normalized = [json.dumps(item, sort_keys=True) for item in value]
            if len(normalized) != len(set(normalized)):
                errors.append(f"{where}: items must be unique")
        if "items" in rule:
            for index, child in enumerate(value):
                errors.extend(_schema_errors(child, rule["items"], root, f"{where}[{index}]"))
    if isinstance(value, str):
        if len(value) < rule.get("minLength", 0):
            errors.append(f"{where}: string is too short")
        if "pattern" in rule and re.search(rule["pattern"], value) is None:
            errors.append(f"{where}: does not match {rule['pattern']}")
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if not _is_finite_number(value):
            errors.append(f"{where}: number must be finite")
            return errors
        if "minimum" in rule and value < rule["minimum"]:
            errors.append(f"{where}: must be >= {rule['minimum']}")
        if "maximum" in rule and value > rule["maximum"]:
            errors.append(f"{where}: must be <= {rule['maximum']}")
        if "exclusiveMinimum" in rule and value <= rule["exclusiveMinimum"]:
            errors.append(f"{where}: must be > {rule['exclusiveMinimum']}")
        if "exclusiveMaximum" in rule and value >= rule["exclusiveMaximum"]:
            errors.append(f"{where}: must be < {rule['exclusiveMaximum']}")
    return errors


def _duplicates(rows: list[dict[str, Any]], label: str) -> list[str]:
    ids = [row.get("id") if isinstance(row, dict) else None for row in rows]
    return [f"{label}: duplicate id {item}" for item in sorted({item for item in ids if ids.count(item) > 1})]


def _is_placeholder(value: str) -> bool:
    lowered = value.lower()
    return (
        not value
        or value.startswith("<")
        or any(token in lowered for token in ("unavailable", "unassigned", "not yet implemented", "validated harness"))
    )


def _sample_statistic(samples: list[float], statistic: str) -> float:
    if statistic == "maximum":
        return max(samples)
    ordered = sorted(samples)
    return ordered[max(0, math.ceil(0.95 * len(ordered)) - 1)]


def _subject_canonical_sha256(subject_kind: str, subject: dict[str, Any], contract: dict[str, Any]) -> str:
    if subject_kind == "record":
        projection: dict[str, Any] = {
            key: copy.deepcopy(contract[key])
            for key in (
                "schema_version", "schema", "record_id", "record_revision", "approval_policy_revision",
                "authority_revision", "approval_validation_authority", "status", "hash_algorithm", "fixture_hash_domain",
                "blocking_reasons", "protocols", "gates",
            )
        }
        projection["subjects"] = {
            collection: [
                _subject_canonical_sha256(
                    "machine" if collection == "machines" else "fixture" if collection == "fixtures" else ("memory-budget" if item["budget_type"] == "memory" else "commit-budget"),
                    item,
                    contract,
                )
                for item in contract[collection]
            ]
            for collection in ("machines", "fixtures", "budgets")
        }
    else:
        projection = copy.deepcopy(subject)
    encoded = _canonical_json_bytes(projection)
    return hashlib.sha256(encoded).hexdigest()


def _approval_subject_is_current(
    approval: dict[str, Any], subject_kind: str, subject: dict[str, Any], contract: dict[str, Any]
) -> bool:
    try:
        return approval.get("subject_canonical_sha256") == _subject_canonical_sha256(subject_kind, subject, contract)
    except (TypeError, ValueError):
        return False


def _approval_envelope(approval: dict[str, Any]) -> dict[str, Any]:
    """Canonical, separately hashable record; it deliberately excludes its own hash/id."""
    return {
        "schema_version": 1,
        "kind": "approval-record",
        "subject_id": approval["subject_id"],
        "artifact_revision": approval["artifact_revision"],
        "captured_at": approval["approved_at"],
        "payload": {
            "approval_id": approval["id"],
            "subject_kind": approval["subject_kind"],
            "decision": "approved",
            "reviewer_identity": approval["reviewer_identity"],
            "reviewer_role": approval["reviewer_role"],
            "subject_canonical_sha256": approval["subject_canonical_sha256"],
            "subject_evidence_bindings": approval["subject_evidence_bindings"],
        },
    }


def _canonical_json_bytes(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False, allow_nan=False).encode("utf-8")


def _json_depth(value: Any) -> int:
    if isinstance(value, dict):
        return 1 + max((_json_depth(child) for child in value.values()), default=0)
    if isinstance(value, list):
        return 1 + max((_json_depth(child) for child in value), default=0)
    return 0


def _strict_json_loads(blob: bytes, max_bytes: int = MAX_TYPED_JSON_BYTES) -> Any:
    if len(blob) > max_bytes:
        raise ValueError(f"typed JSON exceeds {max_bytes} bytes")
    nesting = 0
    in_string = False
    escaped = False
    for byte in blob:
        if in_string:
            if escaped:
                escaped = False
            elif byte == 0x5C:
                escaped = True
            elif byte == 0x22:
                in_string = False
        elif byte == 0x22:
            in_string = True
        elif byte in (0x5B, 0x7B):
            nesting += 1
            if nesting > MAX_JSON_DEPTH:
                raise ValueError(f"JSON nesting exceeds maximum depth {MAX_JSON_DEPTH}")
        elif byte in (0x5D, 0x7D):
            nesting -= 1

    def object_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"duplicate JSON object key: {key}")
            result[key] = value
        return result

    def reject_constant(value: str) -> None:
        raise ValueError(f"non-standard JSON constant: {value}")

    try:
        value = json.loads(blob.decode("utf-8"), object_pairs_hook=object_pairs, parse_constant=reject_constant)
    except RecursionError:
        raise ValueError("JSON parser recursion limit exceeded") from None
    try:
        depth = _json_depth(value)
    except RecursionError:
        raise ValueError("JSON depth walk recursion limit exceeded") from None
    if depth > MAX_JSON_DEPTH:
        raise ValueError(f"typed JSON exceeds maximum depth {MAX_JSON_DEPTH}")
    return value


def _require_object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{path}: expected object")
    return value


def _require_list(value: Any, path: str) -> list[Any]:
    if not isinstance(value, list):
        raise ValueError(f"{path}: expected array")
    return value


def _require_string(value: Any, path: str) -> str:
    if not isinstance(value, str):
        raise ValueError(f"{path}: expected string")
    return value


def _require_integer(value: Any, path: str) -> int:
    if not isinstance(value, int) or isinstance(value, bool):
        raise ValueError(f"{path}: expected integer")
    return value


def _validate_typed_artifact_shape(kind: str, value: Any, path: str) -> None:
    root = _require_object(value, path)
    if kind == "machine-profile":
        _require_string(root.get("capture_kind"), f"{path}.capture_kind")
        for index, item in enumerate(_require_list(root.get("limitations"), f"{path}.limitations")):
            _require_string(item, f"{path}.limitations[{index}]")
        observed = _require_object(root.get("observed"), f"{path}.observed")
        unknown = sorted(set(observed) - MACHINE_INTEGER_FIELDS - MACHINE_STRING_FIELDS)
        if unknown:
            raise ValueError(f"{path}.observed: unknown fields {unknown}")
        for key, item in observed.items():
            if key in MACHINE_INTEGER_FIELDS:
                if _require_integer(item, f"{path}.observed.{key}") < 1:
                    raise ValueError(f"{path}.observed.{key}: must be >= 1")
            else:
                _require_string(item, f"{path}.observed.{key}")
        return
    if kind == "raw-samples":
        bindings = _require_object(root.get("bindings"), f"{path}.bindings")
        for key, item in bindings.items():
            _require_string(item, f"{path}.bindings.{key}")
        _require_list(root.get("samples"), f"{path}.samples")
        return

    for key in ("schema_version", "artifact_revision"):
        _require_integer(root.get(key), f"{path}.{key}")
    for key in ("kind", "subject_id", "captured_at"):
        _require_string(root.get(key), f"{path}.{key}")
    body = _require_object(root.get("payload"), f"{path}.payload")
    if kind in {"fixture-content-identity", "fixture-materialization"}:
        for key in ("hash_domain", "tree_sha256"):
            _require_string(body.get(key), f"{path}.payload.{key}")
        for key in ("file_count", "byte_count"):
            _require_integer(body.get(key), f"{path}.payload.{key}")
        files = _require_list(body.get("files"), f"{path}.payload.files")
        for index, item in enumerate(files):
            entry = _require_object(item, f"{path}.payload.files[{index}]")
            _require_string(entry.get("path"), f"{path}.payload.files[{index}].path")
            _require_integer(entry.get("size"), f"{path}.payload.files[{index}].size")
            _require_string(entry.get("sha256"), f"{path}.payload.files[{index}].sha256")
        if kind == "fixture-materialization":
            _require_string(body.get("source_revision"), f"{path}.payload.source_revision")
            _require_string(body.get("command"), f"{path}.payload.command")
    elif kind == "fixture-license-review":
        for key in ("decision", "spdx", "redistribution_scope"):
            _require_string(body.get(key), f"{path}.payload.{key}")
    elif kind == "fixture-consent-review":
        for key in ("decision", "consenting_identity", "scope"):
            _require_string(body.get(key), f"{path}.payload.{key}")
    elif kind == "fixture-sensitivity-review":
        for key in ("decision", "reviewer_identity"):
            _require_string(body.get(key), f"{path}.payload.{key}")
        for index, item in enumerate(_require_list(body.get("findings"), f"{path}.payload.findings")):
            _require_string(item, f"{path}.payload.findings[{index}]")
    elif kind == "protocol-proof":
        for key in ("protocol_id", "tool", "tool_sha256", "command", "observed_result"):
            _require_string(body.get(key), f"{path}.payload.{key}")
    elif kind == "approval-record":
        for key in ("approval_id", "subject_kind", "decision", "reviewer_identity", "reviewer_role", "subject_canonical_sha256"):
            _require_string(body.get(key), f"{path}.payload.{key}")
        bindings = _require_list(body.get("subject_evidence_bindings"), f"{path}.payload.subject_evidence_bindings")
        for index, item in enumerate(bindings):
            binding = _require_object(item, f"{path}.payload.subject_evidence_bindings[{index}]")
            _require_string(binding.get("evidence_id"), f"{path}.payload.subject_evidence_bindings[{index}].evidence_id")
            _require_string(binding.get("sha256"), f"{path}.payload.subject_evidence_bindings[{index}].sha256")


def _canonical_evidence_path(value: str) -> tuple[str, ...]:
    if not isinstance(value, str) or not value or "\\" in value or value.startswith("/") or value.endswith("/"):
        raise ValueError("evidence path must be canonical relative POSIX")
    parts = tuple(value.split("/"))
    if any(part in {"", ".", ".."} for part in parts) or "/".join(parts) != value:
        raise ValueError("evidence path must be canonical relative POSIX")
    if len(parts) > MAX_RELATIVE_PATH_DEPTH:
        raise ValueError(f"evidence path exceeds {MAX_RELATIVE_PATH_DEPTH} components")
    if any(len(part.encode("utf-8")) > MAX_PATH_COMPONENT_BYTES for part in parts):
        raise ValueError(f"evidence path component exceeds {MAX_PATH_COMPONENT_BYTES} UTF-8 bytes")
    if len(value.encode("utf-8")) > MAX_RELATIVE_PATH_BYTES:
        raise ValueError(f"evidence path exceeds {MAX_RELATIVE_PATH_BYTES} UTF-8 bytes")
    return parts


def _is_reparse(metadata: os.stat_result) -> bool:
    return stat.S_ISLNK(metadata.st_mode) or bool(getattr(metadata, "st_file_attributes", 0) & 0x400)


def _identity(metadata: os.stat_result) -> tuple[int, int, int, int, int, int]:
    return (
        metadata.st_dev, metadata.st_ino, metadata.st_size,
        metadata.st_mtime_ns, metadata.st_ctime_ns, metadata.st_nlink,
    )


@dataclass(frozen=True)
class _TreeSnapshot:
    files: frozenset[str]
    directories: frozenset[str]
    file_identities: tuple[tuple[str, tuple[int, int, int, int, int, int]], ...]
    directory_identities: tuple[tuple[str, tuple[int, int, int, int, int, int]], ...]
    root_identity: tuple[int, int, int, int, int, int]

    def file_identity(self, path: str) -> tuple[int, int, int, int, int, int] | None:
        return dict(self.file_identities).get(path)


class _EvidenceTree:
    """One retained, closed-world evidence authority with no-follow reads."""

    def __init__(self, root: Path, before_component_open: Any = None):
        self.root = root.absolute()
        self.root_fd: int | None = None
        self.component_fds: list[int] = []
        self.component_names: list[str] = []
        self.retained_file_fds: list[tuple[str, int]] = []
        if os.open not in os.supports_dir_fd or not hasattr(os, "O_NOFOLLOW"):
            raise ValueError("platform lacks descriptor-relative no-follow evidence reads")
        anchor = Path(self.root.anchor)
        directory_fd = os.open(anchor, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        self.component_fds.append(directory_fd)
        try:
            parts = self.root.parts[1:]
            for index, component in enumerate(parts):
                expected = os.stat(component, dir_fd=directory_fd, follow_symlinks=False)
                if _is_reparse(expected) or not stat.S_ISDIR(expected.st_mode):
                    raise ValueError("evidence root ancestry must contain only direct non-reparse directories")
                if before_component_open:
                    before_component_open(index, component)
                child = os.open(component, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory_fd)
                metadata = os.fstat(child)
                if (_is_reparse(metadata) or not stat.S_ISDIR(metadata.st_mode)
                        or (expected.st_dev, expected.st_ino) != (metadata.st_dev, metadata.st_ino)):
                    os.close(child)
                    raise ValueError("evidence root component changed between no-follow stat and open")
                directory_fd = child
                self.component_fds.append(child)
                self.component_names.append(component)
            self.root_fd = directory_fd
        except BaseException:
            self.close()
            raise

    def close(self) -> None:
        for _path, file_fd in self.retained_file_fds:
            os.close(file_fd)
        self.retained_file_fds = []
        for directory_fd in reversed(self.component_fds):
            os.close(directory_fd)
        self.component_fds = []
        self.component_names = []
        self.root_fd = None

    def __enter__(self) -> _EvidenceTree:
        return self

    def __exit__(self, *_args: object) -> None:
        self.close()

    def namespace_is_stable(self) -> bool:
        if self.root_fd is None:
            return False
        for parent_fd, name, child_fd in zip(self.component_fds, self.component_names, self.component_fds[1:]):
            try:
                named = os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
                opened = os.fstat(child_fd)
            except OSError:
                return False
            if (_is_reparse(named) or not stat.S_ISDIR(named.st_mode)
                    or (named.st_dev, named.st_ino) != (opened.st_dev, opened.st_ino)):
                return False
        return True

    def inventory(self, after_directory_list: Any = None) -> _TreeSnapshot:
        if self.root_fd is None:
            raise ValueError("evidence tree is closed")
        files: set[str] = set()
        directories: set[str] = set()
        file_identities: dict[str, tuple[int, int, int, int, int, int]] = {}
        directory_identities: dict[str, tuple[int, int, int, int, int, int]] = {}
        self._inventory_iterative(files, directories, file_identities, directory_identities, after_directory_list)
        snapshot = _TreeSnapshot(
            frozenset(files), frozenset(directories), tuple(sorted(file_identities.items())),
            tuple(sorted(directory_identities.items())), _identity(os.fstat(self.root_fd)),
        )
        if len(snapshot.files) > MAX_EVIDENCE_FILES:
            raise ValueError(f"evidence tree exceeds {MAX_EVIDENCE_FILES} files")
        if len(snapshot.directories) > MAX_EVIDENCE_DIRECTORIES:
            raise ValueError(f"evidence tree exceeds {MAX_EVIDENCE_DIRECTORIES} directories")
        sizes = [identity[2] for _path, identity in snapshot.file_identities]
        if any(size > MAX_EVIDENCE_FILE_BYTES for size in sizes):
            raise ValueError(f"evidence file exceeds {MAX_EVIDENCE_FILE_BYTES} bytes")
        if sum(sizes) > MAX_EVIDENCE_TOTAL_BYTES:
            raise ValueError(f"evidence tree exceeds {MAX_EVIDENCE_TOTAL_BYTES} total bytes")
        return snapshot

    def _open_relative_directory(self, parts: tuple[str, ...]) -> int:
        if self.root_fd is None:
            raise ValueError("evidence tree is closed")
        directory_fd = os.dup(self.root_fd)
        try:
            for component in parts:
                child = os.open(component, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory_fd)
                os.close(directory_fd)
                directory_fd = child
            return directory_fd
        except BaseException:
            os.close(directory_fd)
            raise

    def _inventory_iterative(
        self, files: set[str], directories: set[str],
        file_identities: dict[str, tuple[int, int, int, int, int, int]],
        directory_identities: dict[str, tuple[int, int, int, int, int, int]],
        after_directory_list: Any = None,
    ) -> None:
        root_device = os.fstat(self.root_fd).st_dev if self.root_fd is not None else -1
        pending: list[tuple[str, ...]] = [()]
        while pending:
            prefix = pending.pop()
            if len(prefix) > MAX_RELATIVE_PATH_DEPTH:
                raise ValueError(f"evidence directory depth exceeds {MAX_RELATIVE_PATH_DEPTH}")
            directory_fd = self._open_relative_directory(prefix)
            try:
                directory_before = os.fstat(directory_fd)
                if prefix:
                    relative_directory = "/".join(prefix)
                    if directory_identities.get(relative_directory) != _identity(directory_before):
                        raise ValueError("evidence directory changed during iterative inventory")
                names = sorted(os.listdir(directory_fd))
                if after_directory_list:
                    after_directory_list(prefix)
                for name in names:
                    child_parts = prefix + (name,)
                    relative = "/".join(child_parts)
                    _canonical_evidence_path(relative)
                    metadata = os.stat(name, dir_fd=directory_fd, follow_symlinks=False)
                    if _is_reparse(metadata):
                        raise ValueError("symlink or reparse evidence object rejected")
                    if metadata.st_dev != root_device:
                        raise ValueError("nested filesystem device transition rejected")
                    if stat.S_ISDIR(metadata.st_mode):
                        if len(child_parts) > MAX_RELATIVE_PATH_DEPTH:
                            raise ValueError(f"evidence directory depth exceeds {MAX_RELATIVE_PATH_DEPTH}")
                        child = os.open(name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory_fd)
                        try:
                            opened = os.fstat(child)
                            if (metadata.st_dev, metadata.st_ino) != (opened.st_dev, opened.st_ino):
                                raise ValueError("evidence directory changed between no-follow stat and open")
                            directories.add(relative)
                            directory_identities[relative] = _identity(opened)
                            if len(directories) > MAX_EVIDENCE_DIRECTORIES:
                                raise ValueError(f"evidence tree exceeds {MAX_EVIDENCE_DIRECTORIES} directories")
                            pending.append(child_parts)
                        finally:
                            os.close(child)
                    elif stat.S_ISREG(metadata.st_mode):
                        files.add(relative)
                        file_identities[relative] = _identity(metadata)
                        if len(files) > MAX_EVIDENCE_FILES:
                            raise ValueError(f"evidence tree exceeds {MAX_EVIDENCE_FILES} files")
                    else:
                        raise ValueError("non-regular evidence object rejected")
                if _identity(directory_before) != _identity(os.fstat(directory_fd)):
                    raise ValueError("evidence directory changed after captured listing")
            finally:
                os.close(directory_fd)

    def directories_match(self, snapshot: _TreeSnapshot) -> bool:
        if self.root_fd is None or _identity(os.fstat(self.root_fd)) != snapshot.root_identity:
            return False
        for relative, expected in snapshot.directory_identities:
            try:
                parts = _canonical_evidence_path(relative)
                directory_fd = self._open_relative_directory(parts)
                try:
                    if _identity(os.fstat(directory_fd)) != expected:
                        return False
                finally:
                    os.close(directory_fd)
            except (OSError, ValueError):
                return False
        return True
    def read_file(
        self, value: str, snapshot: _TreeSnapshot, before_open: Any = None, buffer: bool = True,
    ) -> tuple[str, int, bytes | None]:
        parts = _canonical_evidence_path(value)
        if self.root_fd is None:
            raise ValueError("evidence tree is closed")

        directory_fd = os.dup(self.root_fd)
        try:
            for part in parts[:-1]:
                child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory_fd)
                os.close(directory_fd)
                directory_fd = child
            if before_open:
                before_open()
            file_fd = os.open(parts[-1], os.O_RDONLY | os.O_NOFOLLOW, dir_fd=directory_fd)
            try:
                before = os.fstat(file_fd)
                if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1:
                    raise ValueError("evidence must be a singly linked regular file")
                opened_identity = _identity(before)
                if snapshot.file_identity(value) != opened_identity:
                    raise ValueError("evidence file changed after closed-world inventory")
                hasher = hashlib.sha256()
                length = 0
                buffered = bytearray() if buffer else None
                while chunk := os.read(file_fd, 64 * 1024):
                    length += len(chunk)
                    if length > MAX_EVIDENCE_FILE_BYTES:
                        raise ValueError(f"evidence file exceeds {MAX_EVIDENCE_FILE_BYTES} bytes")
                    hasher.update(chunk)
                    if buffered is not None:
                        if length > MAX_TYPED_JSON_BYTES:
                            raise ValueError(f"typed JSON exceeds {MAX_TYPED_JSON_BYTES} bytes")
                        buffered.extend(chunk)
                after = os.fstat(file_fd)
                identity_before = opened_identity
                identity_after = (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns, after.st_nlink)
                if identity_before != identity_after or length != before.st_size:
                    raise ValueError("evidence changed during retained-handle read")
                self.retained_file_fds.append((value, file_fd))
                file_fd = -1
                return hasher.hexdigest(), length, bytes(buffered) if buffered is not None else None
            finally:
                if file_fd >= 0:
                    os.close(file_fd)
        finally:
            os.close(directory_fd)


def _corpus_tree_sha256(files: Any) -> str:
    files = _require_list(files, "fixture manifest files")
    if len(files) > MAX_MANIFEST_ENTRIES:
        raise ValueError(f"fixture manifest exceeds {MAX_MANIFEST_ENTRIES} entries")
    hasher = hashlib.sha256(b"RCCE-CORPUS-TREE-V1\0")
    previous = ""
    for index, item in enumerate(files):
        file = _require_object(item, f"fixture manifest files[{index}]")
        path = file.get("path")
        size = file.get("size")
        digest = file.get("sha256")
        _canonical_evidence_path(path)
        if (path <= previous or not isinstance(size, int) or isinstance(size, bool)
                or size < 0 or size > 2**64 - 1
                or not re.fullmatch(r"[0-9a-f]{64}", str(digest))):
            raise ValueError("fixture file manifest must be sorted, unique, finite, and content-addressed")
        encoded = path.encode("utf-8")
        hasher.update(len(encoded).to_bytes(8, "little"))
        hasher.update(encoded)
        hasher.update(size.to_bytes(8, "little"))
        hasher.update(bytes.fromhex(digest))
        previous = path
    return hasher.hexdigest()


def validate_contract(
    data: dict[str, Any] | None = None,
    schema: dict[str, Any] | None = None,
    contract_path: Path = DEFAULT_CONTRACT,
    schema_path: Path = DEFAULT_SCHEMA,
    evidence_root: Path = EVIDENCE_ROOT,
    skip_schema: bool = False,
    before_final_evidence_snapshot: Any = None,
) -> list[str]:
    if data is None:
        with contract_path.open("rb") as handle:
            data = tomllib.load(handle)
    if schema is None:
        schema = json.loads(schema_path.read_text(encoding="utf-8"))

    errors = [] if skip_schema else _schema_errors(data, schema, schema)
    for collection in ("approvals", "evidence", "machines", "fixtures", "gates", "budgets"):
        rows = data.get(collection, [])
        if isinstance(rows, list):
            errors.extend(_duplicates(rows, collection))
    if errors:
        return errors
    if data["status"] == "approved" and not sys.platform.startswith("linux"):
        errors.append("record: approved records require the Linux descriptor-validation backend")

    evidence_by_id = {row["id"]: row for row in data["evidence"]}
    evidence_ids = set(evidence_by_id)
    evidence_payloads: dict[str, Any] = {}
    evidence_blobs: dict[str, bytes] = {}
    declared_paths: set[str] = set()
    declared_directories: set[str] = set()
    try:
        with _EvidenceTree(evidence_root) as evidence_tree:
            for item in data["evidence"]:
                try:
                    _canonical_evidence_path(item["path"])
                except ValueError as exc:
                    errors.append(f"evidence {item['id']}: {exc}")
                    continue
                if item["path"] in declared_paths:
                    errors.append(f"evidence {item['id']}: duplicate declared evidence path")
                declared_paths.add(item["path"])
                path_parts = item["path"].split("/")
                declared_directories.update("/".join(path_parts[:index]) for index in range(1, len(path_parts)))
            # Observation phases are deliberately separate: the initial snapshot is immutable;
            # retained read handles bind hashed bytes to its file identities; a separately built
            # final snapshot binds paths plus file/directory identities at acceptance; retained
            # ancestry handles then prove the named root path still reaches the opened tree.
            initial_snapshot = evidence_tree.inventory()
            actual_paths = initial_snapshot.files
            actual_directories = initial_snapshot.directories
            if actual_paths != declared_paths:
                undeclared = sorted(actual_paths - declared_paths)
                missing = sorted(declared_paths - actual_paths)
                if undeclared:
                    errors.append(f"evidence tree: undeclared files {undeclared}")
                if missing:
                    errors.append(f"evidence tree: declared files absent {missing}")
            if actual_directories != declared_directories:
                undeclared = sorted(actual_directories - declared_directories)
                missing = sorted(declared_directories - actual_directories)
                if undeclared:
                    errors.append(f"evidence tree: undeclared directories {undeclared}")
                if missing:
                    errors.append(f"evidence tree: declared parent directories absent {missing}")
            for item in data["evidence"]:
                if item["path"] not in actual_paths:
                    continue
                digest, length, blob = evidence_tree.read_file(
                    item["path"], initial_snapshot, buffer=item["kind"] != "fixture-source-identity",
                )
                if digest != item["sha256"]:
                    errors.append(f"evidence {item['id']}: SHA-256 mismatch (actual {digest})")
                if length != item["bytes"]:
                    errors.append(f"evidence {item['id']}: byte count mismatch (actual {length})")
                if item["kind"] != "fixture-source-identity":
                    evidence_blobs[item["id"]] = blob or b""
                    try:
                        parsed = _strict_json_loads(blob or b"")
                        _validate_typed_artifact_shape(item["kind"], parsed, f"evidence[{item['id']}]")
                        evidence_payloads[item["id"]] = parsed
                    except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
                        errors.append(f"evidence {item['id']}: typed artifact rejected ({exc})")
            if before_final_evidence_snapshot:
                before_final_evidence_snapshot()
            final_snapshot = evidence_tree.inventory()
            if final_snapshot != initial_snapshot:
                errors.append("evidence tree: paths or identities changed during validation")
            if not evidence_tree.namespace_is_stable():
                errors.append("evidence tree: root namespace changed during validation")
            if not evidence_tree.directories_match(final_snapshot):
                errors.append("evidence tree: final directory identities changed before acceptance")
            for retained_path, retained_fd in evidence_tree.retained_file_fds:
                retained_identity = _identity(os.fstat(retained_fd))
                if (initial_snapshot.file_identity(retained_path) != retained_identity
                        or final_snapshot.file_identity(retained_path) != retained_identity):
                    errors.append("evidence tree: retained evidence identity changed during validation")
    except (OSError, ValueError) as exc:
        errors.append(f"evidence tree: {exc}")

    def envelope_matches(evidence_id: str) -> bool:
        evidence = evidence_by_id.get(evidence_id)
        if evidence is None:
            return False
        payload = evidence_payloads.get(evidence_id)
        return (
            isinstance(payload, dict)
            and payload.get("schema_version") == 1
            and payload.get("kind") == evidence["kind"]
            and payload.get("subject_id") == evidence["subject_id"]
            and payload.get("artifact_revision") == evidence["artifact_revision"]
            and payload.get("captured_at") == evidence["captured_at"]
        )

    reference_fields = {
        "machines": ("evidence_ids", "approval_ids"),
        "fixtures": ("identity_evidence_ids", "license_evidence_ids", "consent_evidence_ids", "sensitivity_evidence_ids", "materialization_evidence_ids", "approval_ids"),
        "budgets": ("raw_sample_evidence_ids", "protocol_evidence_ids", "approval_ids"),
    }
    approval_ids = {row["id"] for row in data["approvals"]}
    for collection, fields in reference_fields.items():
        for item in data[collection]:
            for field in fields:
                known = approval_ids if field == "approval_ids" else evidence_ids
                missing_refs = sorted(set(item[field]) - known)
                if missing_refs:
                    errors.append(f"{collection} {item['id']}: unknown {field} {missing_refs}")
    for protocol_id, protocol in data["protocols"].items():
        missing_refs = sorted(set(protocol["evidence_ids"]) - evidence_ids)
        if missing_refs:
            errors.append(f"protocol {protocol_id}: unknown evidence ids {missing_refs}")
        if protocol["status"] == "validated":
            command = protocol.get("reset_command_template", protocol.get("command_template", ""))
            for evidence_id in protocol["evidence_ids"]:
                evidence = evidence_by_id.get(evidence_id)
                if evidence is None:
                    continue
                payload = evidence_payloads.get(evidence_id)
                expected_keys = {"schema_version", "kind", "subject_id", "artifact_revision", "captured_at", "payload"}
                expected_body = {
                    "protocol_id": protocol_id,
                    "tool": payload.get("payload", {}).get("tool") if isinstance(payload, dict) else None,
                    "tool_sha256": payload.get("payload", {}).get("tool_sha256") if isinstance(payload, dict) else None,
                    "command": command,
                    "observed_result": "passed",
                }
                if (evidence["kind"] != "protocol-proof" or not envelope_matches(evidence_id)
                        or set(payload) != expected_keys or payload.get("payload") != expected_body
                        or not re.fullmatch(r"[0-9a-f]{64}", str(expected_body["tool_sha256"]))
                        or _is_placeholder(str(expected_body["tool"]))):
                    errors.append(f"protocol {protocol_id}: evidence {evidence_id} is not a complete executable proof")

    approvals_by_id = {row["id"]: row for row in data["approvals"]}
    for approval in data["approvals"]:
        bindings = approval["subject_evidence_bindings"]
        binding_ids = [binding["evidence_id"] for binding in bindings]
        if len(binding_ids) != len(set(binding_ids)):
            errors.append(f"approval {approval['id']}: duplicate evidence binding")
        for binding in bindings:
            evidence = evidence_by_id.get(binding["evidence_id"])
            if evidence is None or evidence["sha256"] != binding["sha256"]:
                errors.append(f"approval {approval['id']}: evidence binding hash is absent or stale")
        record = evidence_by_id.get(approval["approval_record_evidence_id"])
        if (record is None or record["kind"] != "approval-record"
                or record["sha256"] != approval["approval_record_sha256"]
                or record["subject_id"] != approval["subject_id"]
                or record["artifact_revision"] != approval["artifact_revision"]
                or record["captured_at"] != approval["approved_at"]):
            errors.append(f"approval {approval['id']}: exact approval-record evidence is missing")
        if approval["approval_record_evidence_id"] in binding_ids:
            errors.append(f"approval {approval['id']}: approval record cannot bind its own hash")
        payload = evidence_payloads.get(approval["approval_record_evidence_id"])
        expected_payload = _approval_envelope(approval)
        if payload != expected_payload:
            errors.append(f"approval {approval['id']}: approval-record body does not exactly reproduce the typed decision")
        elif evidence_blobs.get(approval["approval_record_evidence_id"]) != _canonical_json_bytes(expected_payload):
            errors.append(f"approval {approval['id']}: approval-record bytes are not canonical UTF-8 JSON")
        if approval["artifact_revision"] != data["record_revision"]:
            errors.append(f"approval {approval['id']}: artifact revision is stale")
        if approval["subject_kind"] == "record":
            subject_approved = approval["subject_id"] == data["record_id"] and data["status"] == "approved"
        elif approval["subject_kind"] == "machine":
            subject_approved = any(item["id"] == approval["subject_id"] and item["status"] == "approved" for item in data["machines"])
        elif approval["subject_kind"] == "fixture":
            subject_approved = any(item["id"] == approval["subject_id"] and item["status"] == "approved" for item in data["fixtures"])
        else:
            wanted_type = "memory" if approval["subject_kind"] == "memory-budget" else "commit-duration"
            subject_approved = any(item["id"] == approval["subject_id"] and item["budget_type"] == wanted_type and item["status"] == "approved-measured" for item in data["budgets"])
        if not subject_approved:
            errors.append(f"approval {approval['id']}: subject is absent, mismatched, or not approved")
        if approval["subject_kind"] != "record":
            collections = data["machines"] + data["fixtures"] + data["budgets"]
            subject = next((item for item in collections if item["id"] == approval["subject_id"]), None)
            if subject is None or approval["id"] not in subject["approval_ids"]:
                errors.append(f"approval {approval['id']}: approved subject does not reference this decision")
    used_approval_records = [approval["approval_record_evidence_id"] for approval in data["approvals"]]
    declared_approval_records = [item["id"] for item in data["evidence"] if item["kind"] == "approval-record"]
    if sorted(used_approval_records) != sorted(declared_approval_records):
        errors.append("approvals: every approval-record evidence must belong to exactly one typed approval")

    def require_approval(
        subject_kind: str,
        subject_id: str,
        referenced: list[str],
        required_evidence: set[str],
    ) -> None:
        if not referenced:
            errors.append(f"{subject_kind} {subject_id}: approval record is required")
            return
        for approval_id in referenced:
            approval = approvals_by_id.get(approval_id)
            if approval is None:
                continue
            if approval["subject_kind"] != subject_kind or approval["subject_id"] != subject_id:
                errors.append(f"{subject_kind} {subject_id}: approval {approval_id} targets another subject")
                continue
            if subject_kind == "record":
                subject = data
            elif subject_kind == "machine":
                subject = next(item for item in data["machines"] if item["id"] == subject_id)
            elif subject_kind == "fixture":
                subject = next(item for item in data["fixtures"] if item["id"] == subject_id)
            else:
                subject = next(item for item in data["budgets"] if item["id"] == subject_id)
            if not _approval_subject_is_current(approval, subject_kind, subject, data):
                errors.append(f"{subject_kind} {subject_id}: approval {approval_id} has a stale canonical subject digest")
            bound = {binding["evidence_id"] for binding in approval["subject_evidence_bindings"]}
            if not required_evidence.issubset(bound):
                errors.append(f"{subject_kind} {subject_id}: approval {approval_id} omits required evidence hashes")

    machine_required = (
        "os_name", "os_version", "os_build", "kernel_version", "architecture", "cpu_model", "physical_cores",
        "logical_processors", "ram_bytes", "gpu_model", "gpu_backend", "gpu_driver_version",
        "storage_model", "storage_media_type", "filesystem", "display_width_pixels",
        "display_height_pixels", "display_refresh_hz", "applied_dpi", "display_physical_size",
        "power_plan", "power_source", "rustc", "storage_path_binding",
    )
    placeholders = {"", "unavailable", "unknown", "unassigned"}
    for machine in data["machines"]:
        if machine["status"] == "approved":
            unavailable = [name for name in machine_required if machine[name] in placeholders or machine[name] == 0]
            profile_ids = [item for item in machine["evidence_ids"] if evidence_by_id.get(item, {}).get("kind") == "machine-profile"]
            profile_complete = False
            for evidence_id in profile_ids:
                profile = evidence_payloads.get(evidence_id)
                if isinstance(profile, dict):
                    profile_complete = (
                        profile.get("capture_kind") == "approved-reference-profile"
                        and profile.get("limitations") == []
                        and isinstance(profile.get("observed"), dict)
                        and all(machine[field] == profile["observed"].get(field) for field in machine_required)
                    )
            if machine["missing_fields"] or unavailable or not profile_complete:
                errors.append(f"machine {machine['id']}: unsupported approval; profile is incomplete or unhashed")
            require_approval("machine", machine["id"], machine["approval_ids"], set(machine["evidence_ids"]))

    tiers = [fixture["tier"] for fixture in data["fixtures"]]
    if sorted(tiers) != ["default", "large", "small"]:
        errors.append("fixtures: exactly one small, default, and large tier is required")
    for fixture in data["fixtures"]:
        if fixture["status"] == "approved":
            evidence_sets = {
                "fixture-content-identity": fixture["identity_evidence_ids"],
                "fixture-license-review": fixture["license_evidence_ids"],
                "fixture-consent-review": fixture["consent_evidence_ids"],
                "fixture-sensitivity-review": fixture["sensitivity_evidence_ids"],
                "fixture-materialization": fixture["materialization_evidence_ids"],
            }
            compatible = all(ids and all(evidence_by_id.get(item, {}).get("kind") == kind for item in ids) for kind, ids in evidence_sets.items())
            statuses = (fixture["license_status"], fixture["consent_status"], fixture["sensitivity_status"], fixture["materialization_status"])
            if (not re.fullmatch(r"[0-9a-f]{64}", fixture["content_sha256"]) or fixture["file_count"] <= 0
                    or fixture["byte_count"] <= 0 or fixture["missing_fields"] or not compatible
                    or statuses != ("approved", "approved", "approved", "approved")
                    or _is_placeholder(fixture["materialization_protocol"])
                    or _is_placeholder(fixture["materialization_command"])):
                errors.append(f"fixture {fixture['id']}: unsupported approval; materialized content evidence is incomplete")
            expected_payloads = {
                "fixture-content-identity": None,
                "fixture-license-review": {"decision": "approved", "spdx": None, "redistribution_scope": None},
                "fixture-consent-review": {"decision": "approved", "consenting_identity": None, "scope": None},
                "fixture-sensitivity-review": {"decision": "approved", "reviewer_identity": None, "findings": []},
                "fixture-materialization": None,
            }
            for kind, ids in evidence_sets.items():
                for evidence_id in ids:
                    payload = evidence_payloads.get(evidence_id)
                    body = payload.get("payload", {}) if isinstance(payload, dict) else {}
                    expected = expected_payloads[kind]
                    complete = envelope_matches(evidence_id) and set(payload) == {"schema_version", "kind", "subject_id", "artifact_revision", "captured_at", "payload"} and evidence_by_id.get(evidence_id, {}).get("subject_id") == fixture["id"]
                    if kind == "fixture-license-review":
                        complete = complete and set(body) == set(expected) and body.get("decision") == "approved" and not _is_placeholder(str(body.get("spdx", ""))) and not _is_placeholder(str(body.get("redistribution_scope", "")))
                    elif kind == "fixture-consent-review":
                        complete = complete and set(body) == set(expected) and body.get("decision") == "approved" and not _is_placeholder(str(body.get("consenting_identity", ""))) and not _is_placeholder(str(body.get("scope", "")))
                    elif kind == "fixture-sensitivity-review":
                        complete = complete and set(body) == set(expected) and body.get("decision") == "approved" and not _is_placeholder(str(body.get("reviewer_identity", ""))) and isinstance(body.get("findings"), list)
                    else:
                        expected_keys = {"hash_domain", "files", "tree_sha256", "file_count", "byte_count"}
                        if kind == "fixture-materialization":
                            expected_keys |= {"source_revision", "command"}
                        try:
                            files = body.get("files") if isinstance(body, dict) else None
                            recomputed = _corpus_tree_sha256(files) if isinstance(files, list) else ""
                            complete = complete and set(body) == expected_keys
                            complete = complete and body.get("hash_domain") == data["fixture_hash_domain"]
                            complete = complete and body.get("tree_sha256") == recomputed == fixture["content_sha256"]
                            complete = complete and body.get("file_count") == len(files) == fixture["file_count"]
                            complete = complete and body.get("byte_count") == sum(item["size"] for item in files) == fixture["byte_count"]
                            if kind == "fixture-materialization":
                                complete = complete and body.get("source_revision") == fixture["source_revision"] and body.get("command") == fixture["materialization_command"]
                        except (KeyError, TypeError, ValueError):
                            complete = False
                    if not complete:
                        errors.append(f"fixture {fixture['id']}: {kind} evidence {evidence_id} is structurally incomplete")
            required = set().union(*evidence_sets.values())
            require_approval("fixture", fixture["id"], fixture["approval_ids"], required)

    gates = {gate["id"]: gate for gate in data["gates"]}
    if set(gates) != set(FIXED_GATES):
        errors.append("gates: ids do not exactly match the six inherited accepted-program gates")
    else:
        for gate_id, (metric, statistic, threshold, fixture_scope, machine_scope) in FIXED_GATES.items():
            gate = gates[gate_id]
            expected = (metric, statistic, "lte", threshold, "milliseconds", fixture_scope, machine_scope, "inherited-accepted-program")
            actual = (gate["metric"], gate["statistic"], gate["direction"], gate["threshold"], gate["unit"], gate["fixture_scope"], gate["machine_scope"], gate["authority"])
            if actual != expected:
                errors.append(f"gate {gate_id}: inherited value changed")

    budgets = {budget["id"]: budget for budget in data["budgets"]}
    if set(budgets) != set(EXPECTED_BUDGETS):
        errors.append("budgets: exactly six memory and three commit-duration tier budgets are required")
    for budget in data["budgets"]:
        expected_semantics = EXPECTED_BUDGETS.get(budget["id"])
        actual_semantics = tuple(budget[field] for field in ("budget_type", "tier", "metric", "statistic", "unit", "phase"))
        if expected_semantics != actual_semantics:
            errors.append(f"budget {budget['id']}: fixed budget semantics changed")
        is_commit = budget["budget_type"] == "commit-duration"
        if is_commit and (budget["phase"] != "pre-write-m2" or budget["metric"] != "representative_adr0005_commit_duration"):
            errors.append(f"budget {budget['id']}: commit duration must remain a representative pre-write M2 gate")
        if budget["status"] == "approved-measured":
            protocol_id = "commit" if is_commit else "memory"
            required_protocols = set(data["protocols"]["cache"]["evidence_ids"] + data["protocols"][protocol_id]["evidence_ids"])
            protocol_compatible = (
                data["protocols"]["cache"]["status"] == "validated"
                and data["protocols"][protocol_id]["status"] == "validated"
                and not _is_placeholder(data["protocols"]["cache"]["reset_command_template"])
                and not _is_placeholder(data["protocols"][protocol_id]["command_template"])
                and required_protocols
                and required_protocols.issubset(set(budget["protocol_evidence_ids"]))
                and all(evidence_by_id.get(item, {}).get("kind") == "protocol-proof" for item in budget["protocol_evidence_ids"])
            )
            raw_ids = budget["raw_sample_evidence_ids"]
            if not protocol_compatible or any(evidence_by_id.get(item, {}).get("kind") != "raw-samples" for item in raw_ids):
                errors.append(f"budget {budget['id']}: protocol or raw-sample evidence kind is incompatible")
            observed: list[float] = []
            seen_cache: set[str] = set()
            for evidence_id in raw_ids:
                raw = evidence_payloads.get(evidence_id)
                if not isinstance(raw, dict):
                    errors.append(f"budget {budget['id']}: raw sample evidence {evidence_id} is not structured JSON")
                    continue
                expected_keys = {"schema_version", "kind", "subject_id", "artifact_revision", "captured_at", "bindings", "samples"}
                bindings = raw.get("bindings", {})
                samples = raw.get("samples", [])
                expected_bindings = {
                    "trace_sha256": budget["trace_sha256"], "source_revision": budget["source_revision"],
                    "lock_sha256": budget["lock_sha256"], "fixture_id": budget["fixture_id"],
                    "fixture_sha256": budget["fixture_sha256"], "machine_id": budget["machine_id"],
                    "cache_state": bindings.get("cache_state"), "run_protocol_id": budget["run_protocol_id"],
                    "run_command": budget["run_command"], "metric": budget["metric"], "unit": budget["unit"],
                }
                valid_numbers = isinstance(samples, list) and len(samples) == budget["sample_count_per_cache"] and all(_is_finite_number(value) and value >= 0 for value in samples)
                if (set(raw) != expected_keys or raw.get("schema_version") != 1 or raw.get("kind") != "raw-samples"
                        or raw.get("subject_id") != budget["id"] or raw.get("artifact_revision") != data["record_revision"]
                        or not envelope_matches(evidence_id)
                        or bindings != expected_bindings or not valid_numbers or bindings.get("cache_state") not in {"cold", "warm"}):
                    errors.append(f"budget {budget['id']}: raw sample evidence {evidence_id} has incomplete or stale run bindings")
                    continue
                seen_cache.add(bindings["cache_state"])
                observed.append(_sample_statistic([float(value) for value in samples], budget["statistic"]))
            if seen_cache != {"cold", "warm"} or not observed or budget["observed_value"] != max(observed):
                errors.append(f"budget {budget['id']}: cold/warm raw samples do not recompute the recorded statistic")
            if not _is_finite_number(budget["threshold_value"]) or not _is_finite_number(budget["observed_value"]):
                errors.append(f"budget {budget['id']}: threshold and observed values must be finite")
            expected_outcome = "pass" if budget["observed_value"] <= budget["threshold_value"] else "fail"
            if budget.get("outcome") != expected_outcome:
                errors.append(f"budget {budget['id']}: explicit outcome contradicts observed value and threshold")
            fixture = next((item for item in data["fixtures"] if item["id"] == budget["fixture_id"]), None)
            machine = next((item for item in data["machines"] if item["id"] == budget["machine_id"]), None)
            if (fixture is None or fixture["status"] != "approved" or fixture["content_sha256"] != budget["fixture_sha256"]
                    or fixture["tier"] != budget["tier"] or machine is None or machine["status"] != "approved"):
                errors.append(f"budget {budget['id']}: fixture or machine binding is not approved and exact")
            approval_kind = "commit-budget" if is_commit else "memory-budget"
            require_approval(approval_kind, budget["id"], budget["approval_ids"], set(raw_ids) | set(budget["protocol_evidence_ids"]))
        else:
            measured_fields = {"threshold_value", "observed_value", "outcome", "sample_count_per_cache", "trace_sha256", "source_revision", "lock_sha256", "fixture_id", "fixture_sha256", "machine_id", "cache_states", "run_protocol_id", "run_command"}
            if measured_fields.intersection(budget) or budget["raw_sample_evidence_ids"] or budget["protocol_evidence_ids"] or budget["approval_ids"]:
                errors.append(f"budget {budget['id']}: unmeasured/deferred budget must not carry thresholds or samples")
        if is_commit and budget["status"] == "not-measured":
            errors.append(f"budget {budget['id']}: commit status must explicitly defer to pre-write M2")
        if not is_commit and budget["status"] == "deferred-to-pre-write-m2":
            errors.append(f"budget {budget['id']}: M1 memory budget cannot be deferred to M2")

    if "ADR-0005" not in data["protocols"]["commit"]["semantic_requirement"]:
        errors.append("protocols.commit: representative ADR-0005 semantics must be named")
    if data["status"] == "approved":
        unsupported = (
            bool(data["blocking_reasons"])
            or any(machine["status"] != "approved" for machine in data["machines"])
            or any(fixture["status"] != "approved" for fixture in data["fixtures"])
            or any(budget["budget_type"] == "memory" and budget["status"] != "approved-measured" for budget in data["budgets"])
        )
        if unsupported:
            errors.append("record: unsupported approval while machines, fixtures, memory budgets, or blockers remain incomplete")
        required_evidence = set().union(
            *(set(machine["evidence_ids"]) for machine in data["machines"]),
            *(set(fixture[field]) for fixture in data["fixtures"] for field in ("identity_evidence_ids", "license_evidence_ids", "consent_evidence_ids", "sensitivity_evidence_ids", "materialization_evidence_ids")),
            *(set(budget["raw_sample_evidence_ids"] + budget["protocol_evidence_ids"]) for budget in data["budgets"] if budget["budget_type"] == "memory"),
            *(set(protocol["evidence_ids"]) for protocol in data["protocols"].values()),
            {approval["approval_record_evidence_id"] for approval in data["approvals"] if approval["subject_kind"] != "record"},
        )
        record_approvals = [approval["id"] for approval in data["approvals"] if approval["subject_kind"] == "record" and approval["subject_id"] == data["record_id"]]
        require_approval("record", data["record_id"], record_approvals, required_evidence)
    elif not data["blocking_reasons"]:
        errors.append("record: blocked status requires explicit blocking reasons")
    return errors
def run_self_tests() -> int:
    """Adversarial mutation tests for the contract validator."""
    with DEFAULT_CONTRACT.open("rb") as handle:
        canonical = tomllib.load(handle)
    schema = json.loads(DEFAULT_SCHEMA.read_text(encoding="utf-8"))
    tests: list[tuple[str, Any]] = []
    optional_checks = 0

    def mutation(name: str, change: Any) -> None:
        candidate = copy.deepcopy(canonical)
        change(candidate)
        tests.append((name, candidate))

    mutation("unknown field", lambda d: d.update({"surprise": True}))
    mutation("missing required field", lambda d: d.pop("record_id"))
    mutation("duplicate id", lambda d: d["evidence"].append(copy.deepcopy(d["evidence"][0])))
    mutation("nonobject evidence row", lambda d: d["evidence"].append(1))
    mutation("unhashed evidence", lambda d: d["evidence"][0].update({"sha256": ""}))
    mutation("unsupported record approval", lambda d: d.update({"status": "approved"}))
    mutation("unsupported machine approval", lambda d: d["machines"][0].update({"status": "approved"}))
    mutation("unsupported fixture approval", lambda d: d["fixtures"][1].update({"status": "approved"}))
    mutation("fixture file count maximum", lambda d: d["fixtures"][0].update({"file_count": 100001}))
    mutation("changed inherited gate", lambda d: d["gates"][0].update({"threshold": 251.0}))
    mutation("changed inherited gate scope", lambda d: d["gates"][0].update({"fixture_scope": "default"}))
    mutation("fixed budget semantic swap", lambda d: d["budgets"][0].update({"tier": "default"}))
    mutation("unsupported measured budget", lambda d: d["budgets"][0].update({"status": "approved-measured", "threshold_value": 1.0}))

    label_only = copy.deepcopy(canonical)
    label_only["machines"][0].update({
        "status": "approved",
        "gpu_backend": "claimed-backend",
        "display_physical_size": "claimed-display",
        "power_source": "claimed-power",
        "storage_path_binding": "claimed-storage",
        "missing_fields": [],
    })
    tests.append(("label-only machine promotion", label_only))

    placeholder_harness = copy.deepcopy(canonical)
    placeholder_harness["budgets"][0].update({
        "status": "approved-measured",
        "threshold_value": 1.0,
        "observed_value": 1.0,
        "sample_count_per_cache": 10,
        "trace_sha256": "1" * 64,
        "source_revision": "2" * 40,
        "lock_sha256": "3" * 64,
        "fixture_id": "small-v1",
        "fixture_sha256": "4" * 64,
        "machine_id": "windows-primary-candidate",
        "cache_states": ["cold", "warm"],
        "run_protocol_id": "claimed-protocol",
        "run_command": "<validated harness> --claimed",
        "raw_sample_evidence_ids": ["claimed-cold", "claimed-warm"],
        "protocol_evidence_ids": ["claimed-protocol-proof"],
        "approval_ids": ["claimed-approval"],
    })
    tests.append(("placeholder-harness budget promotion", placeholder_harness))

    failures: list[str] = []
    canonical_errors = validate_contract(canonical, schema)
    if canonical_errors:
        failures.append(f"canonical contract rejected: {canonical_errors}")
    for name, candidate in tests:
        if not validate_contract(candidate, schema):
            failures.append(f"mutation was accepted: {name}")
    for name, candidate in tests[-2:]:
        if not validate_contract(candidate, schema, skip_schema=True):
            failures.append(f"custom semantics accepted adversarial promotion: {name}")
        if not _schema_errors(candidate, schema, schema):
            failures.append(f"JSON schema accepted adversarial promotion: {name}")
    semantic_swap = next(candidate for name, candidate in tests if name == "fixed budget semantic swap")
    if not validate_contract(semantic_swap, schema, skip_schema=True):
        failures.append("custom semantics accepted fixed budget semantic swap")
    if not _schema_errors(semantic_swap, schema, schema):
        failures.append("JSON schema accepted fixed budget semantic swap")
    with tempfile.TemporaryDirectory() as directory:
        temporary = Path(directory)
        outside = temporary / "outside"
        outside.mkdir()
        body = outside / "body.txt"
        body.write_bytes(b"linked evidence")
        (temporary / "linked").symlink_to(outside, target_is_directory=True)
        linked = copy.deepcopy(canonical)
        linked["evidence"][0].update(
            path="linked/body.txt",
            sha256=hashlib.sha256(body.read_bytes()).hexdigest(),
            bytes=len(body.read_bytes()),
        )
        linked_errors = validate_contract(linked, schema, evidence_root=temporary)
        if not any("symlink or reparse" in error for error in linked_errors):
            failures.append(f"directory-symlink evidence ancestor was not specifically rejected: {linked_errors}")
    with tempfile.TemporaryDirectory() as directory:
        temporary = Path(directory)
        (temporary / "extra.json").write_text("{}", encoding="utf-8")
        undeclared_errors = validate_contract(canonical, schema, evidence_root=temporary)
        if not any("undeclared files" in error for error in undeclared_errors):
            failures.append(f"undeclared evidence file was not rejected: {undeclared_errors}")
    with tempfile.TemporaryDirectory() as directory:
        temporary = Path(directory)
        (temporary / "victim").write_bytes(b"before")
        (temporary / "replacement").write_bytes(b"after")
        with _EvidenceTree(temporary) as tree:
            snapshot = tree.inventory()
            def swap() -> None:
                (temporary / "victim").unlink()
                (temporary / "victim").symlink_to(temporary / "replacement")
            try:
                tree.read_file("victim", snapshot, before_open=swap)
                failures.append("evidence path swap was accepted")
            except (OSError, ValueError):
                pass
    for mutation_kind in ("add", "remove", "replace"):
        with tempfile.TemporaryDirectory(dir="/tmp") as directory:
            temporary = Path(directory)
            nested = temporary / "nested"
            nested.mkdir()
            victim = nested / "victim.json"
            victim.write_bytes(b"{}")
            mutated = False
            def mutate_after_nested_list(prefix: tuple[str, ...]) -> None:
                nonlocal mutated
                if prefix == ("nested",) and not mutated:
                    if mutation_kind == "add":
                        (nested / "late.json").write_bytes(b"{}")
                    elif mutation_kind == "remove":
                        victim.unlink()
                    else:
                        victim.unlink()
                        victim.write_bytes(b"{}")
                    mutated = True
            try:
                with _EvidenceTree(temporary) as tree:
                    tree.inventory(after_directory_list=mutate_after_nested_list)
                failures.append(f"late nested directory {mutation_kind} after captured listing was accepted")
            except (OSError, ValueError):
                pass
    with tempfile.TemporaryDirectory() as directory:
        base = Path(directory) / "anchor"
        parent = base / "parent"
        evidence = parent / "evidence"
        outside = base / "outside"
        evidence.mkdir(parents=True)
        outside.mkdir()
        swapped = False
        def swap_parent(_index: int, component: str) -> None:
            nonlocal swapped
            if component == "parent" and not swapped:
                parent.rename(base / "parent-original")
                parent.symlink_to(outside, target_is_directory=True)
                swapped = True
        try:
            _EvidenceTree(evidence, before_component_open=swap_parent)
            failures.append("synchronized parent rename-to-symlink attack was accepted")
        except (OSError, ValueError):
            pass
    with tempfile.TemporaryDirectory() as directory:
        base = Path(directory) / "anchor"
        evidence = base / "evidence"
        outside = base / "outside"
        evidence.mkdir(parents=True)
        outside.mkdir()
        swapped = False
        def swap_root(_index: int, component: str) -> None:
            nonlocal swapped
            if component == "evidence" and not swapped:
                evidence.rename(base / "evidence-original")
                outside.rename(evidence)
                swapped = True
        try:
            _EvidenceTree(evidence, before_component_open=swap_root)
            failures.append("synchronized direct-root replacement attack was accepted")
        except (OSError, ValueError):
            pass
    manifest = [{"path": "data/file.bin", "size": 3, "sha256": hashlib.sha256(b"abc").hexdigest()}]
    known_tree = _corpus_tree_sha256(manifest)
    if known_tree != "53d414841d41df124861b70ead8c56bb6eb69c05e3e4f418136ecc500979815d":
        failures.append(f"RCCE-CORPUS-TREE-V1 known vector changed: {known_tree}")
    changed_manifest = copy.deepcopy(manifest)
    changed_manifest[0]["size"] = 4
    if _corpus_tree_sha256(changed_manifest) == known_tree:
        failures.append("corpus manifest mutation did not change the tree digest")
    oversized_manifest = copy.deepcopy(manifest)
    oversized_manifest[0]["size"] = 2**64
    try:
        _corpus_tree_sha256(oversized_manifest)
        failures.append("manifest size outside encoded u64 range was accepted")
    except ValueError:
        pass
    try:
        _corpus_tree_sha256([manifest[0]] * (MAX_MANIFEST_ENTRIES + 1))
        failures.append("over-limit fixture manifest entry count was accepted")
    except ValueError:
        pass
    for label, body in (
        ("duplicate JSON key", b'{"x":1,"x":2}'),
        ("non-standard JSON constant", b'{"x":NaN}'),
    ):
        try:
            _strict_json_loads(body)
            failures.append(f"{label} was accepted")
        except ValueError:
            pass
    try:
        _strict_json_loads(b"{}", max_bytes=1)
        failures.append("over-limit typed JSON byte count was accepted")
    except ValueError:
        pass
    deep: Any = 0
    for _ in range(MAX_JSON_DEPTH + 1):
        deep = [deep]
    try:
        _strict_json_loads(_canonical_json_bytes(deep))
        failures.append("over-limit typed JSON depth was accepted")
    except ValueError:
        pass
    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        oversized = Path(directory) / "oversized"
        with oversized.open("wb") as handle:
            handle.truncate(MAX_EVIDENCE_FILE_BYTES + 1)
        try:
            with _EvidenceTree(Path(directory)) as tree:
                tree.inventory()
            failures.append("over-limit evidence file size was accepted")
        except ValueError:
            pass
    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        for index in range(9):
            with (Path(directory) / f"sparse-{index}").open("wb") as handle:
                handle.truncate(MAX_EVIDENCE_FILE_BYTES)
        try:
            with _EvidenceTree(Path(directory)) as tree:
                tree.inventory()
            failures.append("over-limit evidence total size was accepted")
        except ValueError:
            pass
    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        for index in range(MAX_EVIDENCE_FILES + 1):
            (Path(directory) / f"f-{index}").touch()
        try:
            with _EvidenceTree(Path(directory)) as tree:
                tree.inventory()
            failures.append("over-limit evidence file count was accepted")
        except ValueError:
            pass
    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        for index in range(MAX_EVIDENCE_DIRECTORIES + 1):
            (Path(directory) / f"d-{index}").mkdir()
        try:
            with _EvidenceTree(Path(directory)) as tree:
                tree.inventory()
            failures.append("over-limit evidence directory count was accepted")
        except ValueError:
            pass
    for label, hostile_path in (
        ("relative path depth", "/".join(["d"] * (MAX_RELATIVE_PATH_DEPTH + 1))),
        ("path component bytes", "x" * (MAX_PATH_COMPONENT_BYTES + 1)),
    ):
        try:
            _canonical_evidence_path(hostile_path)
            failures.append(f"over-limit {label} was accepted")
        except ValueError:
            pass
    deep_directory_parent = Path(tempfile.mkdtemp(dir="/tmp"))
    try:
        root = deep_directory_parent / "evidence"
        root.mkdir()
        directory_fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            for _ in range(1100):
                os.mkdir("d", dir_fd=directory_fd)
                child = os.open("d", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory_fd)
                os.close(directory_fd)
                directory_fd = child
        finally:
            os.close(directory_fd)
        deep_directory_errors = validate_contract(canonical, schema, evidence_root=root)
        if not any("exceeds 64 components" in error or "directory depth exceeds 64" in error for error in deep_directory_errors):
            failures.append(f"1100-level evidence directory did not produce deterministic validation error: {deep_directory_errors}")
    finally:
        subprocess.run(["find", str(deep_directory_parent), "-depth", "-delete"], check=True, capture_output=True)

    cap_eff = 0
    if sys.platform.startswith("linux") and Path("/proc/self/status").exists():
        match = re.search(r"^CapEff:\s*([0-9a-fA-F]+)$", Path("/proc/self/status").read_text(), re.MULTILINE)
        cap_eff = int(match.group(1), 16) if match else 0
    if (cap_eff & (1 << 21)) and shutil.which("mount") and shutil.which("umount"):
        with tempfile.TemporaryDirectory(dir="/tmp") as directory:
            root = Path(directory) / "evidence"
            nested = root / "nested-device"
            nested.mkdir(parents=True)
            mounted = subprocess.run(["mount", "-t", "tmpfs", "tmpfs", str(nested)], capture_output=True).returncode == 0
            if mounted:
                try:
                    try:
                        with _EvidenceTree(root) as tree:
                            tree.inventory()
                        failures.append("nested filesystem-device transition was accepted")
                    except ValueError:
                        pass
                    optional_checks += 1
                finally:
                    subprocess.run(["umount", str(nested)], check=False, capture_output=True)
    original_schema_errors = _schema_errors
    def unexpected_internal_recursion(*_args: Any, **_kwargs: Any) -> list[str]:
        raise RecursionError("synthetic implementation defect")
    globals()["_schema_errors"] = unexpected_internal_recursion
    try:
        try:
            validate_contract(canonical, schema)
            failures.append("unexpected internal RecursionError was masked as validation output")
        except RecursionError:
            pass
    finally:
        globals()["_schema_errors"] = original_schema_errors
    budget_subject = copy.deepcopy(canonical["budgets"][0])
    budget_subject.update(status="approved-measured", threshold_value=100.0)
    approval = {"subject_canonical_sha256": _subject_canonical_sha256("memory-budget", budget_subject, canonical)}
    budget_subject["threshold_value"] = 101.0
    if _approval_subject_is_current(approval, "memory-budget", budget_subject, canonical):
        failures.append("approval remained current after threshold mutation")
    machine_subject = copy.deepcopy(canonical["machines"][0])
    approval = {"subject_canonical_sha256": _subject_canonical_sha256("machine", machine_subject, canonical)}
    machine_subject["gpu_model"] = "mutated"
    if _approval_subject_is_current(approval, "machine", machine_subject, canonical):
        failures.append("approval remained current after machine-field mutation")

    def generated_approved_contract(evidence_root: Path) -> dict[str, Any]:
        generated = copy.deepcopy(canonical)
        generated["status"] = "approved"
        generated["blocking_reasons"] = []
        generated["approvals"] = []
        generated["evidence"] = []
        timestamp = "2026-07-21T12:00:00Z"

        def add_evidence(evidence_id: str, kind: str, subject_id: str, payload: Any) -> str:
            relative = f"generated/{evidence_id}.json"
            path = evidence_root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            blob = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
            path.write_bytes(blob)
            digest = hashlib.sha256(blob).hexdigest()
            generated["evidence"].append({
                "id": evidence_id, "path": relative, "sha256": digest, "bytes": len(blob), "kind": kind,
                "subject_id": subject_id, "artifact_revision": generated["record_revision"], "captured_at": timestamp,
            })
            return evidence_id

        def envelope(kind: str, subject_id: str, body: dict[str, Any]) -> dict[str, Any]:
            return {"schema_version": 1, "kind": kind, "subject_id": subject_id,
                    "artifact_revision": generated["record_revision"], "captured_at": timestamp, "payload": body}

        protocol_ids: dict[str, str] = {}
        for protocol_id in ("ui", "cache", "memory"):
            protocol = generated["protocols"][protocol_id]
            protocol["status"] = "validated"
            if protocol_id == "ui":
                protocol["tool"] = "generated-ui-harness"
                protocol["command_template"] = "generated-ui-harness --run"
                command = protocol["command_template"]
            elif protocol_id == "cache":
                protocol["reset_command_template"] = "generated-cache-reset --run"
                command = protocol["reset_command_template"]
            else:
                protocol["command_template"] = "generated-memory-harness --run"
                command = protocol["command_template"]
            evidence_id = f"protocol-{protocol_id}-proof"
            body = {"protocol_id": protocol_id, "tool": f"generated-{protocol_id}-tool", "tool_sha256": "a" * 64,
                    "command": command, "observed_result": "passed"}
            add_evidence(evidence_id, "protocol-proof", protocol_id, envelope("protocol-proof", protocol_id, body))
            protocol["evidence_ids"] = [evidence_id]
            protocol_ids[protocol_id] = evidence_id

        machine = generated["machines"][0]
        machine.update(status="approved", gpu_backend="Vulkan", display_physical_size="24-inch",
                       power_source="AC", storage_path_binding="C:/RCCE/data", missing_fields=[])
        profile_id = "generated-machine-profile"
        observed = {field: machine[field] for field in (
            "os_name", "os_version", "os_build", "kernel_version", "architecture", "cpu_model", "physical_cores",
            "logical_processors", "ram_bytes", "gpu_model", "gpu_backend", "gpu_driver_version", "storage_model",
            "storage_media_type", "filesystem", "display_width_pixels", "display_height_pixels", "display_refresh_hz",
            "applied_dpi", "display_physical_size", "power_plan", "power_source", "rustc", "storage_path_binding",
        )}
        add_evidence(profile_id, "machine-profile", machine["id"],
                     {"capture_kind": "approved-reference-profile", "limitations": [], "observed": observed})
        machine["evidence_ids"] = [profile_id]

        fixture_evidence: dict[str, list[str]] = {}
        for fixture in generated["fixtures"]:
            content = f"generated-{fixture['tier']}".encode()
            files = [{"path": "data/project.bin", "size": len(content), "sha256": hashlib.sha256(content).hexdigest()}]
            tree_digest = _corpus_tree_sha256(files)
            fixture.update(status="approved", source_kind="generated-self-test", source_locator="generated/self-test",
                           source_revision="b" * 40, source_git_tree_sha1="b" * 40, content_sha256=tree_digest,
                           materialization_protocol="generated-corpus-materializer-v1",
                           materialization_command=f"generated-materialize --tier {fixture['tier']}",
                           license_status="approved", consent_status="approved", sensitivity_status="approved",
                           materialization_status="approved", file_count=1, byte_count=len(content), missing_fields=[])
            base = {"hash_domain": generated["fixture_hash_domain"], "files": files, "tree_sha256": tree_digest,
                    "file_count": 1, "byte_count": len(content)}
            specs = [
                ("identity", "fixture-content-identity", base),
                ("license", "fixture-license-review", {"decision": "approved", "spdx": "CC0-1.0", "redistribution_scope": "test-only"}),
                ("consent", "fixture-consent-review", {"decision": "approved", "consenting_identity": "test-owner", "scope": "test-only"}),
                ("sensitivity", "fixture-sensitivity-review", {"decision": "approved", "reviewer_identity": "test-reviewer", "findings": []}),
                ("materialization", "fixture-materialization", {**base, "source_revision": fixture["source_revision"], "command": fixture["materialization_command"]}),
            ]
            ids: list[str] = []
            for label, kind, body in specs:
                evidence_id = f"{fixture['id']}-{label}"
                add_evidence(evidence_id, kind, fixture["id"], envelope(kind, fixture["id"], body))
                fixture[f"{label}_evidence_ids" if label != "identity" else "identity_evidence_ids"] = [evidence_id]
                ids.append(evidence_id)
            fixture_evidence[fixture["id"]] = ids

        fixture_by_tier = {fixture["tier"]: fixture for fixture in generated["fixtures"]}
        for budget in generated["budgets"]:
            if budget["budget_type"] != "memory":
                continue
            fixture = fixture_by_tier[budget["tier"]]
            budget.update(status="approved-measured", threshold_value=200.0, observed_value=100.0, outcome="pass",
                          sample_count_per_cache=10, trace_sha256="c" * 64, source_revision="d" * 40,
                          lock_sha256="e" * 64, fixture_id=fixture["id"], fixture_sha256=fixture["content_sha256"],
                          machine_id=machine["id"], cache_states=["cold", "warm"], run_protocol_id="memory",
                          run_command=generated["protocols"]["memory"]["command_template"])
            raw_ids = []
            for cache_state in ("cold", "warm"):
                evidence_id = f"{budget['id']}-{cache_state}"
                bindings = {"trace_sha256": budget["trace_sha256"], "source_revision": budget["source_revision"],
                            "lock_sha256": budget["lock_sha256"], "fixture_id": budget["fixture_id"],
                            "fixture_sha256": budget["fixture_sha256"], "machine_id": budget["machine_id"],
                            "cache_state": cache_state, "run_protocol_id": budget["run_protocol_id"],
                            "run_command": budget["run_command"], "metric": budget["metric"], "unit": budget["unit"]}
                raw = {"schema_version": 1, "kind": "raw-samples", "subject_id": budget["id"],
                       "artifact_revision": generated["record_revision"], "captured_at": timestamp,
                       "bindings": bindings, "samples": [100] * 10}
                add_evidence(evidence_id, "raw-samples", budget["id"], raw)
                raw_ids.append(evidence_id)
            budget["raw_sample_evidence_ids"] = raw_ids
            budget["protocol_evidence_ids"] = [protocol_ids["cache"], protocol_ids["memory"]]

        def approve(kind: str, subject: dict[str, Any], bindings: list[str]) -> str:
            approval_id = f"approve-{subject['id']}"
            subject["approval_ids"] = [approval_id]
            approval = {"id": approval_id, "subject_kind": kind, "subject_id": subject["id"], "decision": "approved",
                        "reviewer_identity": "generated-independent-reviewer", "reviewer_role": "self-test adversary",
                        "approved_at": timestamp, "artifact_revision": generated["record_revision"],
                        "subject_canonical_sha256": _subject_canonical_sha256(kind, subject, generated),
                        "approval_record_evidence_id": "", "approval_record_sha256": "",
                        "subject_evidence_bindings": [{"evidence_id": item, "sha256": next(e["sha256"] for e in generated["evidence"] if e["id"] == item)} for item in sorted(set(bindings))]}
            record_id = f"{approval_id}-record"
            add_evidence(record_id, "approval-record", subject["id"], _approval_envelope(approval))
            record = next(item for item in generated["evidence"] if item["id"] == record_id)
            approval["approval_record_evidence_id"] = record_id
            approval["approval_record_sha256"] = record["sha256"]
            generated["approvals"].append(approval)
            return record_id

        subordinate_records = [approve("machine", machine, machine["evidence_ids"])]
        for fixture in generated["fixtures"]:
            subordinate_records.append(approve("fixture", fixture, fixture_evidence[fixture["id"]]))
        for budget in generated["budgets"]:
            if budget["budget_type"] == "memory":
                subordinate_records.append(approve("memory-budget", budget, budget["raw_sample_evidence_ids"] + budget["protocol_evidence_ids"]))

        record_bindings = [item["id"] for item in generated["evidence"] if item["kind"] != "approval-record"] + subordinate_records
        record_approval = {"id": "approve-performance-record", "subject_kind": "record", "subject_id": generated["record_id"],
                           "decision": "approved", "reviewer_identity": "generated-independent-reviewer",
                           "reviewer_role": "self-test adversary", "approved_at": timestamp,
                           "artifact_revision": generated["record_revision"],
                           "subject_canonical_sha256": _subject_canonical_sha256("record", generated, generated),
                           "approval_record_evidence_id": "", "approval_record_sha256": "",
                           "subject_evidence_bindings": [{"evidence_id": item, "sha256": next(e["sha256"] for e in generated["evidence"] if e["id"] == item)} for item in sorted(set(record_bindings))]}
        record_id = "approve-performance-record-record"
        add_evidence(record_id, "approval-record", generated["record_id"], _approval_envelope(record_approval))
        record_evidence = next(item for item in generated["evidence"] if item["id"] == record_id)
        record_approval["approval_record_evidence_id"] = record_id
        record_approval["approval_record_sha256"] = record_evidence["sha256"]
        generated["approvals"].append(record_approval)
        return generated

    def replace_evidence_bytes(generated: dict[str, Any], evidence_root: Path, evidence_id: str, blob: bytes) -> None:
        evidence = next(item for item in generated["evidence"] if item["id"] == evidence_id)
        (evidence_root / evidence["path"]).write_bytes(blob)
        evidence["sha256"] = hashlib.sha256(blob).hexdigest()
        evidence["bytes"] = len(blob)

    def rewrite_approval_record(generated: dict[str, Any], evidence_root: Path, approval: dict[str, Any]) -> None:
        record_id = approval["approval_record_evidence_id"]
        replace_evidence_bytes(generated, evidence_root, record_id, _canonical_json_bytes(_approval_envelope(approval)))
        record = next(item for item in generated["evidence"] if item["id"] == record_id)
        approval["approval_record_sha256"] = record["sha256"]

    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        generated = generated_approved_contract(Path(directory))
        generated_errors = validate_contract(generated, schema, evidence_root=Path(directory))
        if generated_errors:
            failures.append(f"generated complete approved record rejected: {generated_errors}")
        target = Path(directory) / generated["evidence"][0]["path"]
        original_file = Path(directory).parent / f"{Path(directory).name}-original-file"
        replacement_bytes = target.read_bytes()
        def replace_file_after_reads() -> None:
            target.rename(original_file)
            target.write_bytes(replacement_bytes)
        file_replacement_errors = validate_contract(
            generated, schema, evidence_root=Path(directory),
            before_final_evidence_snapshot=replace_file_after_reads,
        )
        if not any("paths or identities changed" in error for error in file_replacement_errors):
            failures.append(f"post-read same-content file replacement was not rejected: {file_replacement_errors}")
        original_file.unlink()

        generated_directory = Path(directory) / "generated"
        replacement_directory = Path(directory).parent / f"{Path(directory).name}-replacement-directory"
        original_directory = Path(directory).parent / f"{Path(directory).name}-original-directory"
        shutil.copytree(generated_directory, replacement_directory)
        def replace_directory_after_reads() -> None:
            generated_directory.rename(original_directory)
            replacement_directory.rename(generated_directory)
        directory_replacement_errors = validate_contract(
            generated, schema, evidence_root=Path(directory),
            before_final_evidence_snapshot=replace_directory_after_reads,
        )
        if not any("paths or identities changed" in error for error in directory_replacement_errors):
            failures.append(f"post-read same-content directory replacement was not rejected: {directory_replacement_errors}")
        if original_directory.exists():
            shutil.rmtree(original_directory)
        if replacement_directory.exists():
            shutil.rmtree(replacement_directory)
        empty_directory = Path(directory) / "generated" / "undeclared-empty"
        empty_directory.mkdir()
        empty_directory_errors = validate_contract(generated, schema, evidence_root=Path(directory))
        if not any("undeclared directories" in error for error in empty_directory_errors):
            failures.append(f"undeclared empty evidence directory was not rejected: {empty_directory_errors}")
        empty_directory.rmdir()
        nan_contract = copy.deepcopy(generated)
        nan_contract["budgets"][0]["threshold_value"] = float("nan")
        nan_errors = validate_contract(nan_contract, schema, evidence_root=Path(directory), skip_schema=True)
        if not any("threshold and observed values must be finite" in error for error in nan_errors):
            failures.append(f"NaN threshold was not rejected by custom semantics: {nan_errors}")
        wrong_outcome = copy.deepcopy(generated)
        wrong_outcome["budgets"][0]["outcome"] = "fail"
        outcome_errors = validate_contract(wrong_outcome, schema, evidence_root=Path(directory), skip_schema=True)
        if not any("explicit outcome contradicts" in error for error in outcome_errors):
            failures.append(f"contradictory explicit outcome was not rejected by custom semantics: {outcome_errors}")
        raw_nan = copy.deepcopy(generated)
        budget = raw_nan["budgets"][0]
        evidence_id = budget["raw_sample_evidence_ids"][0]
        evidence = next(item for item in raw_nan["evidence"] if item["id"] == evidence_id)
        evidence_path = Path(directory) / evidence["path"]
        raw = json.loads(evidence_path.read_text(encoding="utf-8"))
        raw["samples"][0] = float("nan")
        blob = json.dumps(raw, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        evidence_path.write_bytes(blob)
        evidence["sha256"] = hashlib.sha256(blob).hexdigest()
        evidence["bytes"] = len(blob)
        budget_approval = next(item for item in raw_nan["approvals"] if item["subject_id"] == budget["id"])
        next(binding for binding in budget_approval["subject_evidence_bindings"] if binding["evidence_id"] == evidence_id)["sha256"] = evidence["sha256"]
        approval_evidence = next(item for item in raw_nan["evidence"] if item["id"] == budget_approval["approval_record_evidence_id"])
        approval_path = Path(directory) / approval_evidence["path"]
        approval_blob = json.dumps(_approval_envelope(budget_approval), sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        approval_path.write_bytes(approval_blob)
        approval_evidence["sha256"] = hashlib.sha256(approval_blob).hexdigest()
        approval_evidence["bytes"] = len(approval_blob)
        budget_approval["approval_record_sha256"] = approval_evidence["sha256"]
        raw_nan_errors = validate_contract(raw_nan, schema, evidence_root=Path(directory), skip_schema=True)
        if not any("non-standard JSON constant" in error for error in raw_nan_errors):
            failures.append(f"NaN raw sample was not rejected by custom semantics: {raw_nan_errors}")
        record_approval = next(item for item in raw_nan["approvals"] if item["subject_kind"] == "record")
        record_evidence = next(item for item in raw_nan["evidence"] if item["id"] == record_approval["approval_record_evidence_id"])
        record_path = Path(directory) / record_evidence["path"]
        pretty = json.dumps(_approval_envelope(record_approval), sort_keys=True, indent=2, ensure_ascii=False).encode("utf-8")
        record_path.write_bytes(pretty)
        record_evidence["sha256"] = hashlib.sha256(pretty).hexdigest()
        record_evidence["bytes"] = len(pretty)
        record_approval["approval_record_sha256"] = record_evidence["sha256"]
        noncanonical_errors = validate_contract(raw_nan, schema, evidence_root=Path(directory), skip_schema=True)
        if not any("approval-record bytes are not canonical" in error for error in noncanonical_errors):
            failures.append(f"noncanonical approval-record encoding was not rejected: {noncanonical_errors}")

    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        evidence_root = Path(directory)
        hostile = generated_approved_contract(evidence_root)
        profile_id = hostile["machines"][0]["evidence_ids"][0]
        deeply_nested = ("[" * 2000 + "0" + "]" * 2000).encode("utf-8")
        replace_evidence_bytes(hostile, evidence_root, profile_id, deeply_nested)
        recursion_errors = validate_contract(hostile, schema, evidence_root=evidence_root)
        if not any("JSON nesting exceeds maximum depth" in error for error in recursion_errors):
            failures.append(f"deep JSON did not produce deterministic full-validator error: {recursion_errors}")

    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        evidence_root = Path(directory)
        hostile = generated_approved_contract(evidence_root)
        fixture = hostile["fixtures"][0]
        identity_id = fixture["identity_evidence_ids"][0]
        identity_evidence = next(item for item in hostile["evidence"] if item["id"] == identity_id)
        identity_path = evidence_root / identity_evidence["path"]
        identity_payload = _strict_json_loads(identity_path.read_bytes())
        identity_payload["payload"]["files"][0]["size"] = 2**64
        replace_evidence_bytes(hostile, evidence_root, identity_id, _canonical_json_bytes(identity_payload))
        manifest_errors = validate_contract(hostile, schema, evidence_root=evidence_root)
        if not any("fixture-content-identity" in error and "structurally incomplete" in error for error in manifest_errors):
            failures.append(f"oversized manifest u64 did not produce full-validator error: {manifest_errors}")

    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        evidence_root = Path(directory)
        hostile = generated_approved_contract(evidence_root)
        budget = hostile["budgets"][0]
        raw_id = budget["raw_sample_evidence_ids"][0]
        raw_evidence = next(item for item in hostile["evidence"] if item["id"] == raw_id)
        raw_path = evidence_root / raw_evidence["path"]
        raw_payload = _strict_json_loads(raw_path.read_bytes())
        raw_payload["samples"][0] = 10**400
        replace_evidence_bytes(hostile, evidence_root, raw_id, _canonical_json_bytes(raw_payload))
        numeric_errors = validate_contract(hostile, schema, evidence_root=evidence_root, skip_schema=True)
        if not any("raw sample evidence" in error and "incomplete or stale" in error for error in numeric_errors):
            failures.append(f"huge raw-sample integer did not produce full-validator error: {numeric_errors}")

    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        evidence_root = Path(directory)
        hostile = generated_approved_contract(evidence_root)
        hostile["budgets"][0]["threshold_value"] = 10**400
        numeric_errors = validate_contract(hostile, schema, evidence_root=evidence_root, skip_schema=True)
        if not any("threshold and observed values must be finite" in error for error in numeric_errors):
            failures.append(f"huge threshold integer did not produce full-validator error: {numeric_errors}")

    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        evidence_root = Path(directory)
        hostile = generated_approved_contract(evidence_root)
        machine = hostile["machines"][0]
        profile_id = machine["evidence_ids"][0]
        profile_evidence = next(item for item in hostile["evidence"] if item["id"] == profile_id)
        profile_path = evidence_root / profile_evidence["path"]
        profile = _strict_json_loads(profile_path.read_bytes())
        profile["observed"]["physical_cores"] = str(machine["physical_cores"])
        replace_evidence_bytes(hostile, evidence_root, profile_id, _canonical_json_bytes(profile))
        machine_approval = next(item for item in hostile["approvals"] if item["subject_kind"] == "machine")
        next(binding for binding in machine_approval["subject_evidence_bindings"] if binding["evidence_id"] == profile_id)["sha256"] = profile_evidence["sha256"]
        rewrite_approval_record(hostile, evidence_root, machine_approval)
        machine_record = next(item for item in hostile["evidence"] if item["id"] == machine_approval["approval_record_evidence_id"])
        record_approval = next(item for item in hostile["approvals"] if item["subject_kind"] == "record")
        next(binding for binding in record_approval["subject_evidence_bindings"] if binding["evidence_id"] == machine_record["id"])["sha256"] = machine_record["sha256"]
        rewrite_approval_record(hostile, evidence_root, record_approval)
        machine_shape_errors = validate_contract(hostile, schema, evidence_root=evidence_root)
        if not any("observed.physical_cores: expected integer" in error for error in machine_shape_errors):
            failures.append(f"rebound machine-profile numeric string was not rejected: {machine_shape_errors}")

    def malformed_shape(kind: str, value: Any) -> Any:
        if kind == "machine-profile":
            return []
        if kind in {"protocol-proof", "fixture-license-review", "fixture-consent-review"}:
            value["payload"] = [] if kind != "fixture-consent-review" else 7
        elif kind == "fixture-content-identity":
            value["payload"]["files"] = [1]
        elif kind == "fixture-sensitivity-review":
            value["payload"]["findings"] = {}
        elif kind == "fixture-materialization":
            value["payload"]["files"] = {}
        elif kind == "raw-samples":
            value["bindings"] = []
        elif kind == "approval-record":
            value["payload"]["subject_evidence_bindings"] = [1]
        return value

    shape_expectations = {
        "machine-profile": "expected object",
        "protocol-proof": ".payload: expected object",
        "fixture-content-identity": ".payload.files[0]: expected object",
        "fixture-license-review": ".payload: expected object",
        "fixture-consent-review": ".payload: expected object",
        "fixture-sensitivity-review": ".payload.findings: expected array",
        "fixture-materialization": ".payload.files: expected array",
        "raw-samples": ".bindings: expected object",
        "approval-record": ".payload.subject_evidence_bindings[0]: expected object",
    }
    for kind, expected_error in shape_expectations.items():
        with tempfile.TemporaryDirectory(dir="/tmp") as directory:
            evidence_root = Path(directory)
            hostile = generated_approved_contract(evidence_root)
            evidence = next(item for item in hostile["evidence"] if item["kind"] == kind)
            path = evidence_root / evidence["path"]
            parsed = _strict_json_loads(path.read_bytes())
            malformed = malformed_shape(kind, parsed)
            replace_evidence_bytes(hostile, evidence_root, evidence["id"], _canonical_json_bytes(malformed))
            shape_errors = validate_contract(hostile, schema, evidence_root=evidence_root)
            if not any(expected_error in error and f"evidence[{evidence['id']}]" in error for error in shape_errors):
                failures.append(f"malformed {kind} nested shape escaped or lacked path: {shape_errors}")
    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1
    print(f"performance reference self-tests: {len(tests) + 55 + optional_checks} passed, 0 failed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--subject-digest", metavar="KIND:ID")
    parser.add_argument("--approval-envelope", metavar="APPROVAL_ID")
    parser.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    parser.add_argument("--schema", type=Path, default=DEFAULT_SCHEMA)
    args = parser.parse_args()
    if args.self_test:
        return run_self_tests()
    try:
        with args.contract.open("rb") as handle:
            contract = tomllib.load(handle)
        if args.subject_digest:
            kind, separator, subject_id = args.subject_digest.partition(":")
            if not separator or kind not in {"record", "machine", "fixture", "memory-budget", "commit-budget"}:
                raise ValueError("--subject-digest requires KIND:ID")
            if kind == "record":
                subject = contract if subject_id == contract["record_id"] else None
            else:
                collection = "machines" if kind == "machine" else "fixtures" if kind == "fixture" else "budgets"
                subject = next((item for item in contract[collection] if item["id"] == subject_id), None)
            if subject is None:
                raise ValueError(f"subject not found: {args.subject_digest}")
            print(_subject_canonical_sha256(kind, subject, contract))
            return 0
        if args.approval_envelope:
            approval = next((item for item in contract["approvals"] if item["id"] == args.approval_envelope), None)
            if approval is None:
                raise ValueError(f"approval not found: {args.approval_envelope}")
            sys.stdout.buffer.write(_canonical_json_bytes(_approval_envelope(approval)))
            return 0
        errors = validate_contract(contract, contract_path=args.contract, schema_path=args.schema)
    except (OSError, ValueError, tomllib.TOMLDecodeError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("performance reference: valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
