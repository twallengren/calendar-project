import csv
import json
import os
import tempfile
import unittest

import compare


HEADER = list(compare.CSV_FIELDS)


class ReleaseCompareTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.old = os.path.join(self.temporary.name, "old")
        self.new = os.path.join(self.temporary.name, "new")

    def tearDown(self):
        self.temporary.cleanup()

    def write_dataset(
        self,
        root,
        calendars,
        aliases=None,
        version="1.0.0",
    ):
        os.makedirs(root)
        manifest_calendars = {}
        for calendar_id, spec in calendars.items():
            target = os.path.join(root, calendar_id)
            os.makedirs(target)
            start = spec.get("start", "2025-01-01")
            end = spec.get("end", "2025-12-31")
            metadata = {
                "calendar_id": calendar_id,
                "calendar_name": spec.get("name", calendar_id),
                "kind": spec.get("kind", "market"),
                "timezone": spec.get("timezone", "UTC"),
                "mic": spec.get("mic", "XXXX"),
                "aliases": spec.get("aliases", []),
                "range_start": start,
                "range_end": end,
                "generated_at": spec.get("generated_at", "2025-01-01T00:00:00Z"),
            }
            with open(os.path.join(target, "metadata.json"), "w", encoding="utf-8") as handle:
                json.dump(metadata, handle)
            with open(
                os.path.join(target, "events.csv"), "w", newline="", encoding="utf-8"
            ) as handle:
                writer = csv.DictWriter(handle, fieldnames=HEADER)
                writer.writeheader()
                writer.writerows(spec.get("rows", []))
            manifest_calendars[calendar_id] = {
                "range_start": start,
                "range_end": end,
                "event_count": len(spec.get("rows", [])),
                "checksum": "ignored",
            }
        with open(os.path.join(root, "manifest.json"), "w", encoding="utf-8") as handle:
            json.dump(
                {
                    "calendars": manifest_calendars,
                    "aliases": aliases or {},
                    "release_version": {"semantic": version},
                    "blessed_at": "ignored",
                },
                handle,
            )

    @staticmethod
    def row(**changes):
        row = dict.fromkeys(HEADER, "")
        row.update(
            date="2025-05-01",
            type="CLOSED",
            description="Holiday, with comma",
            key="holiday",
            source_module="authority-1",
            status="CONFIRMED",
        )
        row.update(changes)
        return row

    def severity(self):
        return compare.compare(self.old, self.new)["severity"]

    def test_incidental_metadata_and_row_order_are_none(self):
        rows = [self.row(), self.row(key="second")]
        self.write_dataset(self.old, {"CAL": {"rows": rows}})
        self.write_dataset(
            self.new,
            {"CAL": {"rows": list(reversed(rows)), "generated_at": "2030-02-03T00:00:00Z"}},
        )
        self.assertEqual("NONE", self.severity())

    def test_duplicate_multiplicity_and_every_field_are_major(self):
        row = self.row()
        changed = self.row(close_time="13:00")
        self.write_dataset(self.old, {"CAL": {"rows": [row, row]}})
        self.write_dataset(self.new, {"CAL": {"rows": [row, changed]}})
        self.assertEqual("MAJOR", self.severity())

    def test_extension_and_new_alias_are_minor(self):
        self.write_dataset(self.old, {"CAL": {"end": "2025-12-31"}})
        self.write_dataset(
            self.new,
            {"CAL": {"end": "2026-12-31", "aliases": ["NEW"]}},
            aliases={"NEW": "CAL"},
        )
        self.assertEqual("MINOR", self.severity())

    def test_change_only_outside_old_coverage_is_minor(self):
        self.write_dataset(self.old, {"CAL": {"end": "2025-12-31"}})
        self.write_dataset(
            self.new,
            {
                "CAL": {
                    "end": "2026-12-31",
                    "rows": [self.row(date="2026-06-01")],
                }
            },
        )
        self.assertEqual("MINOR", self.severity())

    def test_contraction_removal_and_alias_retarget_are_major(self):
        self.write_dataset(
            self.old,
            {"CAL": {}, "GONE": {}},
            aliases={"X": "CAL"},
        )
        self.write_dataset(
            self.new,
            {"CAL": {"start": "2025-02-01"}},
            aliases={"X": "OTHER"},
        )
        self.assertEqual("MAJOR", self.severity())

    def test_descriptive_metadata_is_patch(self):
        self.write_dataset(self.old, {"CAL": {"name": "Old"}})
        self.write_dataset(self.new, {"CAL": {"name": "New"}})
        self.assertEqual("PATCH", self.severity())

    def test_legacy_three_column_baseline_compares_its_complete_published_record(self):
        row = self.row()
        self.write_dataset(self.old, {"CAL": {"rows": [row]}})
        old_csv = os.path.join(self.old, "CAL", "events.csv")
        with open(old_csv, "w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=HEADER[:3], extrasaction="ignore")
            writer.writeheader()
            writer.writerow(row)
        self.write_dataset(self.new, {"CAL": {"rows": [self.row(close_time="13:00")]}})
        self.assertEqual("MINOR", self.severity())


if __name__ == "__main__":
    unittest.main()
