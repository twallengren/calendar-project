import os
import tempfile
import unittest
import zipfile

import prepare_maven_upload as subject


class MavenUploadPlanTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.bundle = os.path.join(self.temporary.name, "bundle.zip")
        self.files = {
            "io/github/twallengren/bdc-calendar-core/12.0.0/bdc-calendar-core-12.0.0.pom": b"core pom",
            "io/github/twallengren/bdc-calendar-core/12.0.0/bdc-calendar-core-12.0.0.jar": b"core jar",
            "io/github/twallengren/bdc-calendar-core/12.0.0/bdc-calendar-core-12.0.0.jar.sha256": b"hash",
            "io/github/twallengren/bdc-calendar-data/12.1.0/bdc-calendar-data-12.1.0.pom": b"data pom",
            "io/github/twallengren/bdc-calendar-data/12.1.0/bdc-calendar-data-12.1.0.jar": b"data jar",
        }
        with zipfile.ZipFile(self.bundle, "w") as bundle:
            for name, value in self.files.items():
                bundle.writestr(name, value)

    def tearDown(self):
        self.temporary.cleanup()

    def test_existing_core_and_missing_data_uploads_only_data(self):
        def remote(url):
            name = url.removeprefix(subject.CENTRAL)
            return self.files.get(name) if "bdc-calendar-core" in name else None

        result = subject.plan(
            self.bundle,
            (("bdc-calendar-core", "12.0.0"), ("bdc-calendar-data", "12.1.0")),
            remote,
        )
        self.assertEqual([("bdc-calendar-core", "12.0.0")], result["completed"])
        output = os.path.join(self.temporary.name, "filtered.zip")
        subject.filtered_bundle(self.bundle, output, result["missing"])
        with zipfile.ZipFile(output) as filtered:
            self.assertTrue(filtered.namelist())
            self.assertTrue(all("bdc-calendar-data" in name for name in filtered.namelist()))

    def test_existing_coordinate_with_different_bytes_is_rejected(self):
        def remote(url):
            name = url.removeprefix(subject.CENTRAL)
            if name.endswith(".pom"):
                return self.files.get(name)
            if name.endswith(".jar"):
                return b"conflict"
            return None

        with self.assertRaisesRegex(ValueError, "conflicts"):
            subject.plan(self.bundle, (("bdc-calendar-core", "12.0.0"),), remote)

    def test_partially_visible_existing_coordinate_is_retryable(self):
        def remote(url):
            name = url.removeprefix(subject.CENTRAL)
            return self.files.get(name) if name.endswith(".pom") else None

        with self.assertRaises(subject.IncompleteRemote):
            subject.plan(self.bundle, (("bdc-calendar-core", "12.0.0"),), remote)


if __name__ == "__main__":
    unittest.main()
