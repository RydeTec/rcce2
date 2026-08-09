from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import shlex
import shutil
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
        self.temp = tempfile.TemporaryDirectory(
            dir=None if os.name == "nt" else "/home/ryan/.codex/tmp"
        )
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
        directories = [{**base, "path": "/".join(["data", f"root-{root}"] + [f"d{i}" for i in range(29)] + ["file"])} for root in range(68)]
        directories.append({**base, "path": "/".join(["data", "root-68"] + [f"d{i}" for i in range(7)] + ["file"])})
        cases = [
            ([{**base, "path": f"data/{index:04d}"} for index in range(4097)], "file count"),
            ([{**base, "size": 32 * 1024 * 1024 + 1}], "per-file"),
            ([{**base, "path": f"data/total-{index}", "size": 32 * 1024 * 1024} for index in range(17)], "total bytes"),
            ([{**base, "path": "/".join(["data"] + [f"d{i}" for i in range(32)])}], "depth"),
            ([{**base, "path": "data/" + "x" * 256}], "component"),
            ([{**base, "path": "/".join(["data"] + ["x" * 255] * 8)}], "path bytes"),
            (directories, "directory count"),
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

    def test_impossible_python_timestamp_is_rejected(self) -> None:
        with self.assertRaisesRegex(MaterializationError, "real canonical UTC"):
            materialize_fixture(
                repo=self.repo, revision=self.revision, source_tree="data", tier="default",
                output=self.root / "bad-date-output", manifest_path=self.root / "bad-date-manifest.json",
                metadata_path=self.root / "bad-date-metadata.json", captured_at="2026-02-30T12:00:00Z",
            )

    def test_low_free_space_rejects_before_staging(self) -> None:
        usage = shutil._ntuple_diskusage(total=10_000, used=9_999, free=1)
        with mock.patch.object(materializer.shutil, "disk_usage", return_value=usage):
            with self.assertRaisesRegex(MaterializationError, "free space"):
                materialize_fixture(
                    repo=self.repo, revision=self.revision, source_tree="data", tier="default",
                    output=self.root / "low-space-output", manifest_path=self.root / "low-space-manifest.json",
                    metadata_path=self.root / "low-space-metadata.json", captured_at="2026-07-21T12:00:00Z",
                )
        self.assertEqual(list(self.root.glob(".*stage-*")), [])

    def test_stage_json_removes_owned_temp_on_write_or_fsync_failure(self) -> None:
        target = self.root / "staged.json"
        for failing in ("dump", "fsync"):
            patcher = mock.patch.object(materializer.json, "dump", side_effect=OSError("dump failed")) if failing == "dump" else mock.patch.object(materializer.os, "fsync", side_effect=OSError("fsync failed"))
            with self.subTest(failing=failing), patcher, self.assertRaises(OSError):
                materializer._stage_json(target, {"kind": failing})
            self.assertEqual(list(self.root.glob(".staged.json.stage-*")), [])

    def test_later_promotion_failure_reports_and_preserves_published_targets(self) -> None:
        real_publish = materializer._publish_noreplace
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
                real_publish(Path(source), Path(target))

            with self.subTest(fail_at=fail_at), mock.patch.object(materializer, "_publish_noreplace", side_effect=fail_boundary):
                expected = "promotion failed" if fail_at == 1 else "partial publication; manual cleanup required"
                with self.assertRaisesRegex(MaterializationError, expected):
                    materialize_fixture(
                        repo=self.repo, revision=self.revision, source_tree="data", tier="default",
                        output=output, manifest_path=manifest, metadata_path=metadata,
                        captured_at="2026-07-21T12:00:00Z",
                    )
            for published in (output, manifest, metadata)[: fail_at - 1]:
                self.assertTrue(published.exists())
            for unpublished in (output, manifest, metadata)[fail_at - 1 :]:
                self.assertFalse(unpublished.exists())
            self.assertEqual(list(self.root.glob(".*stage-*")), [])

    def test_concurrent_targets_and_prior_publications_are_preserved(self) -> None:
        real_publish = materializer._publish_noreplace
        for conflict_at in (1, 2, 3):
            output = self.root / f"concurrent-output-{conflict_at}"
            manifest = self.root / f"concurrent-manifest-{conflict_at}.json"
            metadata = self.root / f"concurrent-metadata-{conflict_at}.json"
            targets = (output, manifest, metadata)
            calls = 0

            def create_conflict(source: Path, target: Path) -> None:
                nonlocal calls
                calls += 1
                if calls == conflict_at:
                    if target == output:
                        target.mkdir()
                        (target / "external.txt").write_text("external", encoding="utf-8")
                    else:
                        target.write_text("external", encoding="utf-8")
                real_publish(source, target)

            with self.subTest(conflict_at=conflict_at), mock.patch.object(materializer, "_publish_noreplace", side_effect=create_conflict):
                expected = "already exists" if conflict_at == 1 else "partial publication; manual cleanup required"
                with self.assertRaisesRegex(MaterializationError, expected):
                    materialize_fixture(
                        repo=self.repo, revision=self.revision, source_tree="data", tier="default",
                        output=output, manifest_path=manifest, metadata_path=metadata,
                        captured_at="2026-07-21T12:00:00Z",
                    )
            conflict = targets[conflict_at - 1]
            self.assertTrue(conflict.exists())
            if conflict.is_file():
                self.assertEqual(conflict.read_text(encoding="utf-8"), "external")
            else:
                self.assertEqual((conflict / "external.txt").read_text(encoding="utf-8"), "external")
            for earlier in targets[: conflict_at - 1]:
                self.assertTrue(earlier.exists())

    def test_replaced_published_output_is_preserved_when_later_publication_fails(self) -> None:
        output = self.root / "replaced-output"
        manifest = self.root / "replaced-manifest.json"
        metadata = self.root / "replaced-metadata.json"
        real_publish = materializer._publish_noreplace
        calls = 0

        def replace_after_output_identity(source: Path, target: Path) -> None:
            nonlocal calls
            calls += 1
            if calls == 2:
                output.rename(self.root / "concurrently-moved-original")
                output.mkdir()
                (output / "external.txt").write_text("replacement", encoding="utf-8")
                raise OSError("later manifest publication failed")
            real_publish(source, target)

        with mock.patch.object(materializer, "_publish_noreplace", side_effect=replace_after_output_identity):
            with self.assertRaisesRegex(MaterializationError, "partial publication; manual cleanup required"):
                materialize_fixture(
                    repo=self.repo, revision=self.revision, source_tree="data", tier="default",
                    output=output, manifest_path=manifest, metadata_path=metadata,
                    captured_at="2026-07-21T12:00:00Z",
                )
        self.assertEqual((output / "external.txt").read_text(encoding="utf-8"), "replacement")
        self.assertFalse(manifest.exists())
        self.assertFalse(metadata.exists())

    def test_post_publish_identity_failure_reports_published_target_without_deleting_it(self) -> None:
        output = self.root / "identity-output"
        manifest = self.root / "identity-manifest.json"
        metadata = self.root / "identity-metadata.json"
        real_identity = materializer._stable_identity
        calls = 0

        def fail_target_identity(path: Path) -> tuple[int, int, int]:
            nonlocal calls
            calls += 1
            if calls == 2:
                raise MaterializationError("injected post-publish identity failure")
            return real_identity(path)

        with mock.patch.object(materializer, "_stable_identity", side_effect=fail_target_identity):
            with self.assertRaisesRegex(MaterializationError, "partial publication; manual cleanup required.*identity-output"):
                materialize_fixture(
                    repo=self.repo, revision=self.revision, source_tree="data", tier="default",
                    output=output, manifest_path=manifest, metadata_path=metadata,
                    captured_at="2026-07-21T12:00:00Z",
                )
        self.assertTrue(output.exists())
        self.assertFalse(manifest.exists())
        self.assertFalse(metadata.exists())

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
