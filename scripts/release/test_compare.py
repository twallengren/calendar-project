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
            for field in ("coverage", "event_details", "chronology_profile"):
                if field in spec:
                    metadata[field] = spec[field]
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

    def test_event_details_are_counted_order_independently_and_filtered_to_overlap(self):
        details = [
            {"date": "2025-05-01", "key": "a", "evidence": "one"},
            {"date": "2025-05-01", "key": "b", "evidence": "two"},
        ]
        self.write_dataset(self.old, {"CAL": {"event_details": details}})
        self.write_dataset(
            self.new,
            {
                "CAL": {
                    "end": "2026-12-31",
                    "event_details": list(reversed(details))
                    + [{"date": "2026-05-01", "key": "new", "evidence": "three"}],
                }
            },
        )
        self.assertEqual("MINOR", self.severity())

    def test_event_detail_attribution_change_inside_overlap_is_major(self):
        self.write_dataset(
            self.old,
            {"CAL": {"event_details": [{"date": "2025-05-01", "evidence": "old"}]}},
        )
        self.write_dataset(
            self.new,
            {"CAL": {"event_details": [{"date": "2025-05-01", "evidence": "new"}]}},
        )
        self.assertEqual("MAJOR", self.severity())

    def test_quality_contraction_wins_over_verified_extension(self):
        self.write_dataset(
            self.old,
            {"CAL": {"coverage": {"from": "2025-01-01", "to": "2025-12-31", "verified_through": "2025-06-01", "scheduled": {"quality": "VERIFIED"}}}},
        )
        self.write_dataset(
            self.new,
            {"CAL": {"coverage": {"from": "2025-01-01", "to": "2025-12-31", "verified_through": "2025-12-01", "scheduled": {"quality": "INCOMPLETE"}}}},
        )
        self.assertEqual("MAJOR", self.severity())

    def test_new_explicit_incomplete_quality_is_major_for_legacy_coverage(self):
        self.write_dataset(self.old, {"CAL": {}})
        self.write_dataset(
            self.new,
            {"CAL": {"coverage": {"from": "2025-01-01", "to": "2025-12-31", "scheduled": {"quality": "INCOMPLETE"}}}},
        )
        self.assertEqual("MAJOR", self.severity())

    def test_pure_coverage_extension_stays_minor(self):
        coverage = {"from": "2025-01-01", "to": "2025-12-31", "verified_through": "2025-06-01"}
        self.write_dataset(self.old, {"CAL": {"coverage": coverage}})
        extended = dict(coverage, to="2026-12-31")
        self.write_dataset(
            self.new, {"CAL": {"end": "2026-12-31", "coverage": extended}}
        )
        self.assertEqual("MINOR", self.severity())

    def test_source_document_only_change_is_patch(self):
        self.write_dataset(self.old, {"CAL": {}})
        self.write_dataset(self.new, {"CAL": {}})
        old_sources = os.path.join(self.temporary.name, "old-sources")
        new_sources = os.path.join(self.temporary.name, "new-sources")
        os.makedirs(old_sources)
        os.makedirs(new_sources)
        for root, text in ((old_sources, "Old citation\n"), (new_sources, "New citation\n")):
            with open(os.path.join(root, "register.md"), "w", encoding="utf-8") as handle:
                handle.write(text)
        report = compare.compare(self.old, self.new, old_sources, new_sources)
        self.assertEqual("PATCH", report["severity"])


if __name__ == "__main__":
    unittest.main()
