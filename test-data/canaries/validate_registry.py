#!/usr/bin/env python3
"""Validate the versioned P07 state/output policy and synthetic canaries."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import tempfile
import tomllib


STATE_CLASSES = [
    "PublicClient",
    "ServerConfig",
    "Secret",
    "DynamicPrivate",
    "EditorMetadata",
    "AuthoringSource",
    "Unknown",
]
OPERATIONS = [
    "clone",
    "backup",
    "client-package",
    "server-package",
    "diagnostics",
    "migration",
    "playtest-snapshot",
    "publication",
]
ROOT_KEYS = {
    "schema_version",
    "registry_id",
    "registry_version",
    "token_domain",
    "token_algorithm",
    "state_class_order",
    "operation_order",
    "classification_contract",
    "support_files",
    "operations",
    "permission_policies",
    "canaries",
}
ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")
MARKER_RE = re.compile(
    rb"RCCE_CANARY_V1__(?:SECRET|DYNAMIC_PRIVATE)__"
    rb"[a-z0-9-]+__[a-z0-9-]+__[0-9a-f]{64}"
)


class ValidationError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def exact_keys(value: dict, expected: set[str], context: str) -> None:
    actual = set(value)
    require(actual == expected, f"{context}: keys {sorted(actual)} != {sorted(expected)}")


def regular_file_bytes(path: Path) -> bytes:
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode), f"{path}: not a regular file")
    require(info.st_nlink == 1, f"{path}: link count is {info.st_nlink}, expected 1")
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(path, flags)
    try:
        opened = os.fstat(descriptor)
        require(stat.S_ISREG(opened.st_mode), f"{path}: opened object is not regular")
        require(opened.st_nlink == 1, f"{path}: opened link count is {opened.st_nlink}, expected 1")
        require(
            (opened.st_dev, opened.st_ino) == (info.st_dev, info.st_ino),
            f"{path}: identity changed before open",
        )
        chunks: list[bytes] = []
        while chunk := os.read(descriptor, 65536):
            chunks.append(chunk)
        content = b"".join(chunks)
        require(len(content) == opened.st_size, f"{path}: size changed while reading")
        return content
    finally:
        os.close(descriptor)


def enumerate_closed_tree(root: Path) -> tuple[set[str], set[str], dict[str, Path]]:
    root_info = root.lstat()
    require(stat.S_ISDIR(root_info.st_mode), f"{root}: registry root is not a directory")
    observed_directories: set[str] = set()
    observed_files: set[str] = set()
    file_paths: dict[str, Path] = {}
    pending: list[tuple[Path, tuple[str, ...]]] = [(root, ())]
    while pending:
        directory, parent_parts = pending.pop()
        with os.scandir(directory) as entries:
            for entry in entries:
                parts = (*parent_parts, entry.name)
                relative = PurePosixPath(*parts).as_posix()
                try:
                    encoded_components = [component.encode("utf-8", "strict") for component in parts]
                except UnicodeEncodeError as error:
                    raise ValidationError(f"{relative}: path is not portable UTF-8") from error
                require(
                    all(MARKER_RE.search(component) is None for component in encoded_components),
                    f"{relative}: canary marker is forbidden in a path component",
                )
                info = entry.stat(follow_symlinks=False)
                if stat.S_ISDIR(info.st_mode):
                    observed_directories.add(relative)
                    pending.append((Path(entry.path), parts))
                elif stat.S_ISREG(info.st_mode):
                    observed_files.add(relative)
                    file_paths[relative] = Path(entry.path)
                else:
                    raise ValidationError(f"{relative}: registry tree permits only directories and regular files")
    return observed_directories, observed_files, file_paths


def expected_marker(root: dict, canary: dict) -> str:
    fields = [
        root["token_domain"],
        root["registry_id"],
        str(root["registry_version"]),
        canary["fixture_id"],
        canary["id"],
        canary["state_class"],
        canary["role"],
    ]
    digest = hashlib.sha256("\0".join(fields).encode("utf-8")).hexdigest()
    class_code = "SECRET" if canary["state_class"] == "Secret" else "DYNAMIC_PRIVATE"
    return (
        f"RCCE_CANARY_V1__{class_code}__{canary['fixture_id']}__"
        f"{canary['id']}__{digest}"
    )


def expected_fixture(root: dict, canary: dict) -> bytes:
    lines = [
        "RCCE-P07-SYNTHETIC-CANARY-V1",
        f"registry_id={root['registry_id']}",
        f"registry_version={root['registry_version']}",
        f"fixture_id={canary['fixture_id']}",
        f"canary_id={canary['id']}",
        f"state_class={canary['state_class']}",
        f"role={canary['role']}",
        f"marker={expected_marker(root, canary)}",
    ]
    if canary["state_class"] == "Secret":
        lines.append("credential_material=ABSENT_BY_DESIGN")
    else:
        lines.append("runtime_authority=NONE")
    lines.extend(["service_endpoint=fixture.invalid:0", "operational=false"])
    return ("\n".join(lines) + "\n").encode("utf-8")


def class_decision(operation: dict, classes: list[str], options: set[str]) -> str:
    outcomes: list[str] = []
    for state_class in classes:
        rule = operation["rules"][state_class]
        disposition = rule["disposition"]
        if disposition == "conditional":
            outcomes.append("include" if rule["option"] in options else "deny")
        else:
            outcomes.append(disposition)
    distinct = set(outcomes)
    if distinct == {"dispatch"}:
        return "dispatch"
    if "dispatch" in distinct:
        return "reject"
    if "deny" in distinct:
        return "reject" if distinct - {"deny"} else "absent"
    if distinct == {"derive-only"}:
        return "derived"
    if "derive-only" in distinct:
        return "reject"
    return "present"


def resolve_classification(
    primary_candidates: list[str],
    additional_classes: list[str],
    inherited_classes: list[str],
) -> tuple[str, list[str]]:
    require(len(primary_candidates) == 1, "classification must have exactly one primary candidate")
    primary = primary_candidates[0]
    require(primary in STATE_CLASSES, f"classification has invalid primary {primary}")
    constraints = additional_classes + inherited_classes
    require(all(state_class in STATE_CLASSES for state_class in constraints), "classification has invalid additional class")
    additional = sorted(set(constraints) - {primary}, key=STATE_CLASSES.index)
    return primary, additional


def rendered_disposition(rule: dict) -> str:
    disposition = rule["disposition"]
    if disposition == "include":
        return "Include"
    if disposition == "deny":
        return "Exclude"
    if disposition == "derive-only":
        return "Derive only"
    if disposition == "dispatch":
        return "Dispatch"
    return f"Conditional(`{rule['option']}`)"


def validate_matrix_document(matrix_path: Path, registry_path: Path) -> None:
    matrix = regular_file_bytes(matrix_path).decode("utf-8")
    root = tomllib.loads(regular_file_bytes(registry_path).decode("utf-8"))
    require('`registry_id = "rcce-p07-canaries"`, version 1' in matrix, "matrix registry identity/version mismatch")
    require(
        "contract `rcce-state-classification` version 1" in matrix,
        "matrix classification contract identity/version mismatch",
    )
    header = "| State class | Clone | Backup | Client package | Server package | Diagnostics | Migration | Playtest snapshot | Publication |"
    require(matrix.count(header) == 1, "matrix must contain one exact operation-table header")
    operation_map = {operation["id"]: operation for operation in root["operations"]}
    lines = matrix.splitlines()
    for state_class in STATE_CLASSES:
        cells = [f"`{state_class}`"]
        cells.extend(
            rendered_disposition(operation_map[operation_id]["rules"][state_class])
            for operation_id in OPERATIONS
        )
        expected = "| " + " | ".join(cells) + " |"
        require(lines.count(expected) == 1, f"matrix operation row drifted for {state_class}")


def validate_registry(registry_path: Path) -> tuple[int, int, int]:
    registry_bytes = regular_file_bytes(registry_path)
    try:
        root = tomllib.loads(registry_bytes.decode("utf-8"))
    except (UnicodeDecodeError, tomllib.TOMLDecodeError) as error:
        raise ValidationError(f"{registry_path}: invalid UTF-8 TOML: {error}") from error

    exact_keys(root, ROOT_KEYS, "registry")
    require(root["schema_version"] == 1, "schema_version must be 1")
    require(root["registry_id"] == "rcce-p07-canaries", "unexpected registry_id")
    require(root["registry_version"] == 1, "registry_version must be 1")
    require(root["token_domain"] == "RCCE-P07-CANARY-V1", "unexpected token_domain")
    require(root["token_algorithm"] == "sha256", "unexpected token_algorithm")
    require(root["state_class_order"] == STATE_CLASSES, "state classes missing or out of order")
    require(root["operation_order"] == OPERATIONS, "operations missing or out of order")
    classification_contract = root["classification_contract"]
    exact_keys(
        classification_contract,
        {
            "rule_id",
            "rule_version",
            "primary_source",
            "required_primary_count",
            "constraint_mode",
            "missing_primary",
            "multiple_primary",
            "conflicting_primary",
        },
        "classification_contract",
    )
    require(classification_contract["rule_id"] == "rcce-state-classification", "unexpected classification rule_id")
    require(classification_contract["rule_version"] == 1, "classification rule_version must be 1")
    require(classification_contract["primary_source"] == "versioned-inventory-rule", "unexpected primary source")
    require(classification_contract["required_primary_count"] == 1, "primary cardinality must be exactly one")
    require(classification_contract["constraint_mode"] == "additive", "classification constraints must be additive")
    require(
        all(
            classification_contract[key] == "reject"
            for key in ("missing_primary", "multiple_primary", "conflicting_primary")
        ),
        "ambiguous primary selection must reject",
    )
    support_files = root["support_files"]
    require(len(support_files) == 2, "exactly two versioned support files are required")
    support_map: dict[str, dict] = {}
    for support_file in support_files:
        exact_keys(support_file, {"path", "role", "version"}, f"support file {support_file.get('path')}")
        require(support_file["path"] not in support_map, f"duplicate support path {support_file['path']}")
        require(PurePosixPath(support_file["path"]).parts == (support_file["path"],), f"unsafe support path {support_file['path']}")
        require(support_file["version"] == 1, f"support file {support_file['path']}: version must be 1")
        support_map[support_file["path"]] = support_file
    require(
        {(path, entry["role"]) for path, entry in support_map.items()}
        == {("registry-v1.toml", "registry"), ("validate_registry.py", "validator")},
        "support file identities or roles drifted",
    )
    require(registry_path.name == "registry-v1.toml", "registry must use its declared support path")

    operations = root["operations"]
    require([entry.get("id") for entry in operations] == OPERATIONS, "operation records missing or out of order")
    operation_map: dict[str, dict] = {}
    allowed_options: dict[str, set[str]] = {}
    for operation in operations:
        exact_keys(operation, {"id", "description", "rules"}, f"operation {operation.get('id')}")
        require(operation["description"].strip() == operation["description"], f"operation {operation['id']}: bad description")
        exact_keys(operation["rules"], set(STATE_CLASSES), f"operation {operation['id']} rules")
        options: set[str] = set()
        for state_class, rule in operation["rules"].items():
            disposition = rule.get("disposition")
            if disposition == "conditional":
                exact_keys(rule, {"disposition", "option"}, f"{operation['id']}/{state_class}")
                require(ID_RE.fullmatch(rule["option"]) is not None, f"{operation['id']}/{state_class}: invalid option")
                require(rule["option"] not in options, f"{operation['id']}: duplicate option {rule['option']}")
                options.add(rule["option"])
            else:
                exact_keys(rule, {"disposition"}, f"{operation['id']}/{state_class}")
                require(disposition in {"include", "deny", "derive-only", "dispatch"}, f"{operation['id']}/{state_class}: bad disposition")
        if operation["id"] == "publication":
            require(all(rule["disposition"] == "dispatch" for rule in operation["rules"].values()), "publication must dispatch only")
        else:
            require(all(rule["disposition"] != "dispatch" for rule in operation["rules"].values()), f"{operation['id']}: dispatch is reserved")
        operation_map[operation["id"]] = operation
        allowed_options[operation["id"]] = options

    require(operation_map["client-package"]["rules"]["Secret"]["disposition"] == "deny", "client package must deny Secret")
    require(operation_map["client-package"]["rules"]["DynamicPrivate"]["disposition"] == "deny", "client package must deny DynamicPrivate")
    require(operation_map["diagnostics"]["rules"]["Secret"]["disposition"] == "deny", "diagnostics must deny Secret")
    require(all(rule["disposition"] not in {"include", "conditional"} for rule in operation_map["diagnostics"]["rules"].values()), "diagnostics may not copy source bytes")

    policies = root["permission_policies"]
    require(len(policies) == 2, "exactly two sensitive canary policies are required")
    policy_map: dict[str, dict] = {}
    for policy in policies:
        exact_keys(policy, {"id", "state_class", "description", "assertions"}, f"policy {policy.get('id')}")
        require(ID_RE.fullmatch(policy["id"]) is not None, f"policy {policy['id']}: invalid id")
        require(policy["id"] not in policy_map, f"duplicate policy {policy['id']}")
        require(policy["state_class"] in {"Secret", "DynamicPrivate"}, f"policy {policy['id']}: invalid class")
        seen_assertions: set[tuple[str, tuple[str, ...]]] = set()
        default_operations: set[str] = set()
        positive_operations: set[str] = set()
        for assertion in policy["assertions"]:
            exact_keys(assertion, {"operation", "options", "expected"}, f"policy {policy['id']} assertion")
            operation_id = assertion["operation"]
            require(operation_id in operation_map, f"policy {policy['id']}: unknown operation {operation_id}")
            require(assertion["options"] == sorted(set(assertion["options"])), f"policy {policy['id']}/{operation_id}: options must be unique and sorted")
            require(set(assertion["options"]) <= allowed_options[operation_id], f"policy {policy['id']}/{operation_id}: unknown option")
            key = (operation_id, tuple(assertion["options"]))
            require(key not in seen_assertions, f"policy {policy['id']}: duplicate assertion {key}")
            seen_assertions.add(key)
            expected = "present" if class_decision(operation_map[operation_id], [policy["state_class"]], set(assertion["options"])) == "present" else "absent"
            require(assertion["expected"] == expected, f"policy {policy['id']}/{operation_id}: expected {assertion['expected']} but rules produce {expected}")
            if not assertion["options"]:
                default_operations.add(operation_id)
            if assertion["expected"] == "present":
                positive_operations.add(operation_id)
        require(default_operations == set(OPERATIONS), f"policy {policy['id']}: missing default assertions")
        conditional_operations = {
            operation_id
            for operation_id, operation in operation_map.items()
            if operation["rules"][policy["state_class"]]["disposition"] == "conditional"
        }
        require(positive_operations == conditional_operations, f"policy {policy['id']}: missing or excess conditional presence proof")
        policy_map[policy["id"]] = policy
    require("secret-never-project" in policy_map, "P04 permission policy secret-never-project is missing")

    canaries = root["canaries"]
    require(len(canaries) == 2, "exactly two seed canaries are required")
    canary_root = registry_path.parent
    canary_ids: set[str] = set()
    fixture_paths: set[str] = set()
    markers: dict[bytes, Path] = {}
    for canary in canaries:
        exact_keys(
            canary,
            {"id", "fixture_id", "fixture_path", "state_class", "role", "permission_policy_id", "expected_occurrences"},
            f"canary {canary.get('id')}",
        )
        for key in ("id", "fixture_id", "role", "permission_policy_id"):
            require(ID_RE.fullmatch(canary[key]) is not None, f"canary {canary['id']}: invalid {key}")
        require(canary["id"] not in canary_ids, f"duplicate canary id {canary['id']}")
        require(canary["fixture_path"] not in fixture_paths, f"duplicate fixture path {canary['fixture_path']}")
        canary_ids.add(canary["id"])
        fixture_paths.add(canary["fixture_path"])
        require(canary["state_class"] in {"Secret", "DynamicPrivate"}, f"canary {canary['id']}: invalid class")
        require(canary["expected_occurrences"] == 1, f"canary {canary['id']}: seed must occur exactly once")
        policy = policy_map.get(canary["permission_policy_id"])
        require(policy is not None, f"canary {canary['id']}: unknown policy")
        require(policy["state_class"] == canary["state_class"], f"canary {canary['id']}: policy class mismatch")
        relative = PurePosixPath(canary["fixture_path"])
        require(not relative.is_absolute() and ".." not in relative.parts and "." not in relative.parts, f"canary {canary['id']}: unsafe fixture path")
        require(relative.parts[0] == "fixtures", f"canary {canary['id']}: fixture must be below fixtures/")
        fixture = canary_root.joinpath(*relative.parts)
        content = regular_file_bytes(fixture)
        require(content == expected_fixture(root, canary), f"canary {canary['id']}: fixture is not the exact synthetic envelope")
        marker = expected_marker(root, canary).encode("ascii")
        require(content.count(marker) == 1, f"canary {canary['id']}: marker occurrence mismatch")
        require(marker not in markers, f"canary {canary['id']}: token collision")
        markers[marker] = fixture

    allowed_files = set(support_map) | fixture_paths
    allowed_directories: set[str] = set()
    for allowed_file in allowed_files:
        for parent in PurePosixPath(allowed_file).parents:
            if parent != PurePosixPath("."):
                allowed_directories.add(parent.as_posix())
    observed_directories, observed_files, observed_file_paths = enumerate_closed_tree(canary_root)
    require(
        observed_directories == allowed_directories,
        f"registry directory membership mismatch: observed={sorted(observed_directories)}, allowed={sorted(allowed_directories)}",
    )
    require(
        observed_files == allowed_files,
        f"registry file membership mismatch: observed={sorted(observed_files)}, allowed={sorted(allowed_files)}",
    )

    observed: dict[bytes, list[Path]] = {}
    for relative in sorted(observed_file_paths):
        path = observed_file_paths[relative]
        content = regular_file_bytes(path)
        for marker in MARKER_RE.findall(content):
            observed.setdefault(marker, []).append(path)
    require(set(observed) == set(markers), "undeclared, missing, or malformed canary marker found")
    for marker, locations in observed.items():
        require(locations == [markers[marker]], f"marker occurs outside its declared fixture: {locations}")

    mixed_cases = [
        ("clone", ["PublicClient"], ["Secret"], [], set(), "reject"),
        ("clone", ["PublicClient"], ["Secret"], [], {"include-secret"}, "present"),
        ("client-package", ["PublicClient"], ["Secret"], [], set(), "reject"),
        ("client-package", ["Secret"], ["DynamicPrivate"], [], set(), "absent"),
        ("server-package", ["PublicClient"], ["Unknown"], [], set(), "reject"),
        ("server-package", ["ServerConfig"], [], ["Secret"], set(), "reject"),
        ("server-package", ["ServerConfig"], [], ["Secret"], {"include-secret"}, "present"),
        ("diagnostics", ["PublicClient"], [], ["DynamicPrivate"], set(), "derived"),
        ("migration", ["AuthoringSource"], ["Unknown"], [], set(), "reject"),
        ("migration", ["AuthoringSource"], ["Unknown"], [], {"preserve-unknown"}, "present"),
        ("publication", ["PublicClient"], ["ServerConfig"], [], set(), "dispatch"),
        ("clone", ["PublicClient"], ["PublicClient"], ["PublicClient"], set(), "present"),
    ]
    for operation_id, primary_candidates, additional, inherited, options, expected in mixed_cases:
        primary, resolved_additional = resolve_classification(primary_candidates, additional, inherited)
        classes = [primary, *resolved_additional]
        actual = class_decision(operation_map[operation_id], classes, options)
        require(actual == expected, f"mixed case {operation_id}/{classes}/{sorted(options)}: {actual} != {expected}")

    rejected_classifications = [
        ([], [], []),
        (["PublicClient", "PublicClient"], [], []),
        (["PublicClient", "Secret"], [], []),
        (["NotAClass"], [], []),
        (["PublicClient"], ["NotAClass"], []),
    ]
    for primary_candidates, additional, inherited in rejected_classifications:
        try:
            resolve_classification(primary_candidates, additional, inherited)
        except ValidationError:
            pass
        else:
            raise ValidationError(
                f"classification failure case was accepted: {primary_candidates}/{additional}/{inherited}"
            )

    return len(operations), len(policies), len(canaries), len(mixed_cases) + len(rejected_classifications)


def run_negative_self_tests(registry_path: Path) -> int:
    cases = 0
    source_root = tomllib.loads(regular_file_bytes(registry_path).decode("utf-8"))
    secret_canary = next(canary for canary in source_root["canaries"] if canary["state_class"] == "Secret")
    secret_marker = expected_marker(source_root, secret_canary)
    with tempfile.TemporaryDirectory(prefix="rcce-p07-canaries-") as temp_name:
        copied_root = Path(temp_name) / "canaries"
        shutil.copytree(registry_path.parent, copied_root)
        copied_registry = copied_root / registry_path.name

        original = copied_registry.read_text(encoding="utf-8")
        copied_registry.write_text(original.replace('state_class_order = ["PublicClient", "ServerConfig",', 'state_class_order = ["ServerConfig", "PublicClient",', 1), encoding="utf-8")
        try:
            validate_registry(copied_registry)
        except ValidationError:
            cases += 1
        else:
            raise ValidationError("negative self-test accepted reordered state classes")

        copied_registry.write_text(original.replace('expected = "absent"', 'expected = "present"', 1), encoding="utf-8")
        try:
            validate_registry(copied_registry)
        except ValidationError:
            cases += 1
        else:
            raise ValidationError("negative self-test accepted a false presence assertion")

        copied_registry.write_text(original, encoding="utf-8")
        secret_fixture = copied_root / "fixtures/secret-credential-sentinel-v1.txt"
        secret_fixture.write_bytes(secret_fixture.read_bytes().replace(b"operational=false", b"operational=true"))
        try:
            validate_registry(copied_registry)
        except ValidationError:
            cases += 1
        else:
            raise ValidationError("negative self-test accepted an operational canary")

        shutil.rmtree(copied_root)
        shutil.copytree(registry_path.parent, copied_root)
        copied_registry = copied_root / registry_path.name
        secret_fixture = copied_root / "fixtures/secret-credential-sentinel-v1.txt"
        secret_fixture.unlink()
        secret_fixture.symlink_to("dynamic-private-runtime-sentinel-v1.txt")
        try:
            validate_registry(copied_registry)
        except (OSError, ValidationError):
            cases += 1
        else:
            raise ValidationError("negative self-test accepted a linked canary fixture")

        shutil.rmtree(copied_root)
        shutil.copytree(registry_path.parent, copied_root)
        copied_registry = copied_root / registry_path.name
        (copied_root / "fixtures/undeclared-secret-shaped.txt").write_bytes(
            b"pass" + b"word=" + b"synthetic-secret-shaped-but-not-an-rcce-marker\n"
        )
        try:
            validate_registry(copied_registry)
        except ValidationError:
            cases += 1
        else:
            raise ValidationError("negative self-test accepted an undeclared secret-shaped file")

        shutil.rmtree(copied_root)
        shutil.copytree(registry_path.parent, copied_root)
        copied_registry = copied_root / registry_path.name
        (copied_root / "fixtures" / secret_marker).write_bytes(b"benign content\n")
        try:
            validate_registry(copied_registry)
        except ValidationError:
            cases += 1
        else:
            raise ValidationError("negative self-test accepted a canary token in a filename")
    return cases


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "registry",
        nargs="?",
        type=Path,
        default=Path(__file__).with_name("registry-v1.toml"),
    )
    parser.add_argument(
        "--matrix",
        type=Path,
        default=Path(__file__).parents[2] / "docs/compat/state-classification-matrix.md",
    )
    parser.add_argument("--self-test", action="store_true", help="also run hostile in-memory/temp-tree mutations")
    arguments = parser.parse_args()
    try:
        operation_count, policy_count, canary_count, classification_case_count = validate_registry(arguments.registry)
        validate_matrix_document(arguments.matrix, arguments.registry)
        negative_count = run_negative_self_tests(arguments.registry) if arguments.self_test else 0
    except (OSError, ValidationError) as error:
        print(f"P07 canary registry validation failed: {error}")
        return 1
    print(
        "P07 canary registry validation passed: "
        f"{operation_count} operations, {len(STATE_CLASSES)} state classes, "
        f"{policy_count} policies, {canary_count} canaries, "
        f"{classification_case_count} classification cases, {negative_count} hostile file cases"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
