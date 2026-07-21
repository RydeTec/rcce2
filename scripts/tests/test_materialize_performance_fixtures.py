from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import unittest
from unittest import mock

import scripts.materialize_performance_fixtures as materializer
from scripts.materialize_performance_fixtures import MaterializationError, materialize_fixture


def _git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=repo, check=True, capture_output=True, text=True
    ).stdout.strip()


def _tree_digest(files: list[dict[str, object]]) -> str:
    digest = hashlib.sha256(b"RCCE-CORPUS-TREE-V1\0")
    for entry in files:
        path = str(entry["path"]).encode("utf-8")
        digest.update(len(path).to_bytes(8, "little"))
        digest.update(path)
        digest.update(int(entry["size"]).to_bytes(8, "little"))
        digest.update(bytes.fromhex(str(entry["sha256"])))
    return digest.hexdigest()


class MaterializerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        _git(self.repo, "init", "-q")
        _git(self.repo, "config", "user.email", "fixture-test@example.invalid")
        _git(self.repo, "config", "user.name", "Fixture Test")
        (self.repo / "data" / "nested").mkdir(parents=True)
        (self.repo / "data" / "alpha.txt").write_bytes(b"alpha\n")
        (self.repo / "data" / "nested" / "beta.bin").write_bytes(b"\x00\x01beta")
        (self.repo / "outside.txt").write_text("not part of the source tree\n", encoding="utf-8")
        _git(self.repo, "add", ".")
        _git(self.repo, "commit", "-qm", "fixture source")
        self.revision = _git(self.repo, "rev-parse", "HEAD")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def materialize(self, tier: str, **kwargs: object) -> tuple[Path, dict, dict]:
        output = self.root / f"out-{tier}-{len(list(self.root.glob('out-*')))}"
        manifest = self.root / f"manifest-{tier}-{output.name}.json"
        metadata = self.root / f"metadata-{tier}-{output.name}.json"
        result = materialize_fixture(
            repo=self.repo,
            revision=self.revision,
            source_tree="data",
            tier=tier,
            output=output,
            manifest_path=manifest,
            metadata_path=metadata,
            captured_at="2026-07-21T12:00:00Z",
            **kwargs,
        )
        self.assertEqual(result, json.loads(manifest.read_text(encoding="utf-8")))
        return output, result, json.loads(metadata.read_text(encoding="utf-8"))

    def test_default_reads_exact_git_revision_and_emits_validator_digest(self) -> None:
        (self.repo / "data" / "alpha.txt").write_bytes(b"dirty working tree\n")
        output, artifact, metadata = self.materialize("default")

        self.assertEqual((output / "data" / "alpha.txt").read_bytes(), b"alpha\n")
        self.assertFalse((output / "outside.txt").exists())
        files = artifact["payload"]["files"]
        self.assertEqual([entry["path"] for entry in files], ["data/alpha.txt", "data/nested/beta.bin"])
        self.assertEqual(artifact["payload"]["tree_sha256"], _tree_digest(files))
        self.assertEqual(metadata["source"]["revision"], self.revision)
        self.assertEqual(metadata["transform"]["kind"], "exact-git-tree")
        self.assertEqual(metadata["review_status"], "pending-human-review")

    def test_small_and_large_remain_unavailable(self) -> None:
        for tier in ("small", "large"):
            with self.subTest(tier=tier), self.assertRaisesRegex(
                MaterializationError, "only the exact default Git tree"
            ):
                self.materialize(tier)

    def test_portable_path_rules_reject_collisions_and_windows_hazards(self) -> None:
        bad_path_sets = [
            ["data/Case.txt", "data/case.txt"],
            ["data/é.txt", "data/e\u0301.txt"],
            ["data/CON.txt"],
            ["data/name:stream"],
            ["data/control\x01.txt"],
            ["data/trailing. "],
        ]
        for paths in bad_path_sets:
            with self.subTest(paths=paths), self.assertRaises(MaterializationError):
                materializer._validate_portable_paths([{"path": path, "size": 1} for path in paths])

    def test_default_limits_are_enforced_before_output(self) -> None:
        base = {"path": "data/a", "size": 1}
        cases = [
            ([{**base, "path": f"data/{index:04d}"} for index in range(4097)], "file count"),
            ([{**base, "size": 32 * 1024 * 1024 + 1}], "per-file"),
            ([{**base, "path": f"data/total-{index}", "size": 32 * 1024 * 1024} for index in range(17)], "total bytes"),
            ([{**base, "path": "data/" + "/".join(f"d{i}" for i in range(64))}], "depth"),
            ([{**base, "path": "data/" + "x" * 256}], "component"),
            ([{**base, "path": "/".join(["data"] + ["x" * 70] * 59)}], "path bytes"),
            ([{**base, "path": "/".join(["data", f"root-{root}"] + [f"d{i}" for i in range(61)] + ["file"])} for root in range(67)], "directory count"),
        ]
        for entries, reason in cases:
            with self.subTest(reason=reason), self.assertRaisesRegex(MaterializationError, reason):
                materializer._enforce_default_limits(entries)

    def test_stream_copy_never_requests_a_whole_large_blob(self) -> None:
        class TrackingReader(io.BytesIO):
            def __init__(self, value: bytes):
                super().__init__(value)
                self.requests: list[int] = []

            def read(self, size: int = -1) -> bytes:
                self.requests.append(size)
                return super().read(size)

        source = TrackingReader(b"x" * (materializer.STREAM_CHUNK_BYTES * 3 + 17))
        destination = io.BytesIO()
        digest, count = materializer._copy_stream(source, destination, len(source.getvalue()))
        self.assertEqual(count, len(source.getvalue()))
        self.assertEqual(digest, hashlib.sha256(source.getvalue()).hexdigest())
        self.assertLessEqual(max(source.requests), materializer.STREAM_CHUNK_BYTES)

    def test_recorded_command_is_safely_quoted_and_replayable(self) -> None:
        _, artifact, _ = self.materialize("default")
        argv = shlex.split(artifact["payload"]["command"])
        for flag in ("--repo", "--revision", "--source-tree", "--tier", "--output", "--manifest", "--metadata", "--captured-at"):
            self.assertIn(flag, argv)
        self.assertEqual(argv[argv.index("--revision") + 1], self.revision)

    def test_promotion_failure_rolls_back_all_owned_targets(self) -> None:
        real_replace = os.replace
        for fail_at in (1, 2, 3):
            output = self.root / f"rollback-output-{fail_at}"
            manifest = self.root / f"rollback-manifest-{fail_at}.json"
            metadata = self.root / f"rollback-metadata-{fail_at}.json"
            calls = 0

            def fail_boundary(source: object, target: object) -> None:
                nonlocal calls
                calls += 1
                if calls == fail_at:
                    raise OSError("injected promotion failure")
                real_replace(source, target)

            with self.subTest(fail_at=fail_at), mock.patch.object(materializer.os, "replace", side_effect=fail_boundary):
                with self.assertRaisesRegex(MaterializationError, "promotion failed"):
                    materialize_fixture(
                        repo=self.repo, revision=self.revision, source_tree="data", tier="default",
                        output=output, manifest_path=manifest, metadata_path=metadata,
                        captured_at="2026-07-21T12:00:00Z",
                    )
            self.assertFalse(output.exists())
            self.assertFalse(manifest.exists())
            self.assertFalse(metadata.exists())
            self.assertEqual(list(self.root.glob(".*stage-*")), [])

    @unittest.skipUnless(hasattr(os, "symlink"), "symlinks unavailable")
    def test_rejects_symlink_entries_without_materializing_any_bytes(self) -> None:
        os.symlink("alpha.txt", self.repo / "data" / "link.txt")
        _git(self.repo, "add", "data/link.txt")
        _git(self.repo, "commit", "-qm", "add link")
        revision = _git(self.repo, "rev-parse", "HEAD")
        output = self.root / "out-link"

        with self.assertRaisesRegex(MaterializationError, "non-regular Git entry"):
            materialize_fixture(
                repo=self.repo,
                revision=revision,
                source_tree="data",
                tier="default",
                output=output,
                manifest_path=self.root / "link-manifest.json",
                metadata_path=self.root / "link-metadata.json",
                captured_at="2026-07-21T12:00:00Z",
            )

        self.assertFalse(output.exists())

    def test_rejects_output_inside_repository_source_tree(self) -> None:
        with self.assertRaisesRegex(MaterializationError, "output must not be inside"):
            materialize_fixture(
                repo=self.repo,
                revision=self.revision,
                source_tree="data",
                tier="default",
                output=self.repo / "generated",
                manifest_path=self.root / "bad-manifest.json",
                metadata_path=self.root / "bad-metadata.json",
                captured_at="2026-07-21T12:00:00Z",
            )

    def test_rejects_evidence_targets_that_would_mutate_or_contaminate_fixture_bytes(self) -> None:
        output = self.root / "out-evidence-placement"
        with self.assertRaisesRegex(MaterializationError, "evidence targets must be outside"):
            materialize_fixture(
                repo=self.repo,
                revision=self.revision,
                source_tree="data",
                tier="default",
                output=output,
                manifest_path=output / "manifest.json",
                metadata_path=self.root / "metadata.json",
                captured_at="2026-07-21T12:00:00Z",
            )
        with self.assertRaisesRegex(MaterializationError, "evidence targets must not be inside"):
            materialize_fixture(
                repo=self.repo,
                revision=self.revision,
                source_tree="data",
                tier="default",
                output=output,
                manifest_path=self.repo / "data" / "manifest.json",
                metadata_path=self.root / "metadata.json",
                captured_at="2026-07-21T12:00:00Z",
            )


if __name__ == "__main__":
    unittest.main()
