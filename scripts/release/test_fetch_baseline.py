import csv
import json
import tempfile
import unittest
from pathlib import Path

import fetch_baseline


class FetchBaselineTest(unittest.TestCase):
    def test_synthesized_manifest_counts_multiline_csv_records(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            calendar = root / "TEST"
            calendar.mkdir()
            (calendar / "metadata.json").write_text(
                json.dumps({"kind": "market", "range_start": "2020-01-01", "range_end": "2020-12-31"}),
                encoding="utf-8",
            )
            with (calendar / "events.csv").open("w", encoding="utf-8", newline="") as handle:
                writer = csv.writer(handle, lineterminator="\n")
                writer.writerow(["date", "type", "description"])
                writer.writerow(["2020-01-01", "CLOSED", "first line\nsecond line"])
            fetch_baseline.synthesize_manifest(
                str(root), "11.0.0", "a" * 40, "2026-01-01T00:00:00Z"
            )
            manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(1, manifest["calendars"]["TEST"]["event_count"])


if __name__ == "__main__":
    unittest.main()


class SelectDatasetAssetsTest(unittest.TestCase):
    def test_dataset_archive_is_chosen_beside_the_python_sdist(self):
        assets = [
            {"name": "bdc_calendars-0.12.0.tar.gz"},
            {"name": "bdc-calendars-12.0.0.zip"},
            {"name": "bdc-calendars-12.0.0.tar.gz"},
            {"name": "checksums.txt"},
        ]
        archive, checksums = fetch_baseline.select_dataset_assets(assets)
        self.assertEqual(archive["name"], "bdc-calendars-12.0.0.tar.gz")
        self.assertEqual(checksums["name"], "checksums.txt")

    def test_missing_or_ambiguous_dataset_archive_is_rejected(self):
        with self.assertRaises(SystemExit):
            fetch_baseline.select_dataset_assets(
                [{"name": "bdc_calendars-0.12.0.tar.gz"}, {"name": "checksums.txt"}]
            )
        with self.assertRaises(SystemExit):
            fetch_baseline.select_dataset_assets(
                [
                    {"name": "bdc-calendars-12.0.0.tar.gz"},
                    {"name": "bdc-calendars-11.0.0.tar.gz"},
                    {"name": "checksums.txt"},
                ]
            )
        with self.assertRaises(SystemExit):
            fetch_baseline.select_dataset_assets([{"name": "bdc-calendars-12.0.0.tar.gz"}])
