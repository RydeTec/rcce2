from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

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

    def test_small_selection_is_deterministic_and_records_limits(self) -> None:
        first_output, first, first_metadata = self.materialize(
            "small", small_max_files=1, small_max_bytes=1024
        )
        second_output, second, second_metadata = self.materialize(
            "small", small_max_files=1, small_max_bytes=1024
        )

        self.assertEqual(first["payload"], second["payload"])
        self.assertEqual(
            sorted(path.relative_to(first_output).as_posix() for path in first_output.rglob("*") if path.is_file()),
            sorted(path.relative_to(second_output).as_posix() for path in second_output.rglob("*") if path.is_file()),
        )
        self.assertEqual(first_metadata["transform"]["kind"], "stable-path-hash-subset")
        self.assertEqual(first_metadata["transform"]["max_files"], 1)
        self.assertEqual(second_metadata["representativeness"], "pending-human-review")

    def test_large_replication_is_explicit_and_pending_representativeness_review(self) -> None:
        output, artifact, metadata = self.materialize("large", large_replicas=2)

        paths = [entry["path"] for entry in artifact["payload"]["files"]]
        self.assertEqual(len(paths), 4)
        self.assertTrue(all(path.startswith(("replica-000/", "replica-001/")) for path in paths))
        self.assertEqual(metadata["transform"]["kind"], "namespaced-byte-exact-replication")
        self.assertEqual(metadata["transform"]["replicas"], 2)
        self.assertEqual(metadata["representativeness"], "pending-human-review")
        self.assertEqual((output / "replica-001" / "data" / "alpha.txt").read_bytes(), b"alpha\n")

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
                output=self.repo / "data" / "generated",
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
