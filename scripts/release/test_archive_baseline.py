import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("archive_baseline.py")


class ArchiveBaselineTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.baseline = self.root / "baseline"
        calendar = self.baseline / "TEST"
        calendar.mkdir(parents=True)
        (calendar / "events.csv").write_text("date,type\n", encoding="utf-8")
        (calendar / "metadata.json").write_text("{}\n", encoding="utf-8")
        (self.baseline / "manifest.json").write_text(
            json.dumps({"calendars": {"TEST": {}}}) + "\n", encoding="utf-8"
        )
        self.evidence = self.root / "evidence.json"
        self.entry = {
            "data_version": "11.0.0",
            "published_at": "2026-02-16T21:43:23Z",
            "observed_current_at": "2026-09-14T10:00:00Z",
            "source_sha": "3" * 40,
            "tag": "v11.0.0",
            "release_url": "https://github.example/releases/v11.0.0",
            "asset": {"sha256": "sha256:" + "a" * 64},
        }
        self._write_evidence()

    def tearDown(self):
        self.temporary.cleanup()

    def _write_evidence(self):
        self.evidence.write_text(json.dumps(self.entry) + "\n", encoding="utf-8")

    def _run(self, check=True):
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--baseline",
                str(self.baseline),
                "--evidence",
                str(self.evidence),
                "--history",
                str(self.root / "history"),
            ],
            check=check,
            capture_output=True,
            text=True,
        )

    def test_writes_evidenced_ledger_and_is_idempotent(self):
        self._run()
        ledger_path = self.root / "history" / "publications.json"
        first = ledger_path.read_bytes()
        ledger = json.loads(first)
        self.assertEqual("1.0", ledger["schema_version"])
        self.assertEqual("2026-09-14T10:00:00Z", ledger["releases"][0]["observed_current_at"])
        self.assertTrue(ledger["releases"][0]["atomic_dataset"])
        self.assertEqual(["TEST"], ledger["releases"][0]["calendar_ids"])
        self.entry["observed_current_at"] = "2026-09-15T10:00:00Z"
        self._write_evidence()
        self._run()
        self.assertEqual(first, ledger_path.read_bytes())

    def test_rejects_conflicting_version_before_copying_snapshot(self):
        history = self.root / "history"
        history.mkdir()
        conflicting = {
            "schema_version": "1.0",
            "releases": [
                {
                    "data_version": "11.0.0",
                    "published_at": "2020-01-01T00:00:00Z",
                }
            ],
        }
        (history / "publications.json").write_text(json.dumps(conflicting), encoding="utf-8")
        result = self._run(check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("conflicts with authenticated release", result.stderr)
        self.assertFalse((history / "TEST").exists())


if __name__ == "__main__":
    unittest.main()
