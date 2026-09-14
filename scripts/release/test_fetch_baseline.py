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
