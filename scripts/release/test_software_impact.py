import json
import tempfile
import unittest
from pathlib import Path

import software_impact


class SoftwareImpactTest(unittest.TestCase):
    def versions(self, core="12.0.0", python="0.12.0", data="12.0.0"):
        return {
            "data": data,
            "java_core": core,
            "python": python,
            "wire_schema": {"current": "2.0.0", "served": ["1.0.0", "2.0.0"]},
        }

    def test_core_and_python_versions_are_independent(self):
        previous = self.versions()
        current = self.versions(core="12.0.1")
        result = software_impact.impact(current, previous, "12.0.0")
        self.assertEqual("core-v12.0.1", result["release_tag"])
        self.assertEqual({"from": "12.0.0", "to": "12.0.1"}, result["software_changes"]["java_core"])

        current = self.versions(python="0.13.0")
        result = software_impact.impact(current, previous, "12.0.0")
        self.assertEqual("python-v0.13.0", result["release_tag"])

    def test_dataset_change_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "dataset version"):
            software_impact.impact(self.versions(data="13.0.0"), self.versions(), "12.0.0")

    def test_version_must_increase(self):
        with self.assertRaisesRegex(ValueError, "must increase"):
            software_impact.impact(
                self.versions(core="11.9.0"), self.versions(), "12.0.0"
            )

    def test_no_explicit_package_bump_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "explicit"):
            software_impact.impact(self.versions(), self.versions(), "12.0.0")

    def test_software_release_requires_byte_identical_published_dataset(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            baseline = root / "baseline"
            candidate = root / "candidate"
            manifest = {
                "release_version": {"semantic": "12.0.0"},
                "calendars": {"TEST": {}},
            }
            for directory in (baseline, candidate):
                (directory / "TEST").mkdir(parents=True)
                (directory / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
                (directory / "TEST" / "events.csv").write_text("date,type\n", encoding="utf-8")
            software_impact.verify_unchanged_dataset(str(baseline), str(candidate))
            (candidate / "TEST" / "events.csv").write_text(
                "date,type\n2026-01-01,CLOSED\n", encoding="utf-8"
            )
            with self.assertRaisesRegex(ValueError, "events.csv"):
                software_impact.verify_unchanged_dataset(str(baseline), str(candidate))


if __name__ == "__main__":
    unittest.main()
