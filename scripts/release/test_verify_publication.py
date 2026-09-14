import json
import os
import tempfile
import unittest

import verify_publication


COMMIT = "a" * 40


class VerifyPublicationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = os.path.join(self.temporary.name, "publication")
        os.mkdir(self.root)
        for name in verify_publication.DELIVERABLE_DIRECTORIES:
            os.mkdir(os.path.join(self.root, name))
            with open(os.path.join(self.root, name, "payload"), "wb") as handle:
                handle.write(name.encode("ascii"))
        self.checkout = os.path.join(self.temporary.name, "release.json")
        with open(self.checkout, "wb") as handle:
            handle.write(b"{}\n")
        with open(os.path.join(self.root, "dataset", "release.json"), "wb") as handle:
            handle.write(b"{}\n")
        self._write_manifest()

    def tearDown(self):
        self.temporary.cleanup()

    def _write_manifest(self):
        paths = []
        for top in verify_publication.DELIVERABLE_DIRECTORIES:
            directory = os.path.join(self.root, top)
            for base, _, names in os.walk(directory):
                for name in names:
                    path = os.path.join(base, name)
                    relative = os.path.relpath(path, self.root).replace(os.sep, "/")
                    paths.append(("publication/" + relative, path))
        checksums = os.path.join(self.root, "checksums.txt")
        with open(checksums, "w", encoding="utf-8") as handle:
            for relative, path in sorted(paths):
                handle.write("{}  {}\n".format(verify_publication.digest(path), relative))
        receipt = {
            "schema_version": "1.0",
            "release_commit": COMMIT,
            "descriptor_sha256": "sha256:" + verify_publication.digest(self.checkout),
            "checksums_sha256": "sha256:" + verify_publication.digest(checksums),
        }
        with open(os.path.join(self.root, "build-receipt.json"), "w", encoding="utf-8") as handle:
            json.dump(receipt, handle)

    def _rebind_checksums(self):
        checksums = os.path.join(self.root, "checksums.txt")
        receipt_path = os.path.join(self.root, "build-receipt.json")
        with open(receipt_path, encoding="utf-8") as handle:
            receipt = json.load(handle)
        receipt["checksums_sha256"] = "sha256:" + verify_publication.digest(checksums)
        with open(receipt_path, "w", encoding="utf-8") as handle:
            json.dump(receipt, handle)

    def test_accepts_complete_bound_publication(self):
        verify_publication.verify(self.root, COMMIT, self.checkout)

    def test_rejects_unlisted_pages_file(self):
        with open(os.path.join(self.root, "pages", "injected.html"), "w", encoding="utf-8") as handle:
            handle.write("injected")
        with self.assertRaisesRegex(ValueError, "membership mismatch"):
            verify_publication.verify(self.root, COMMIT, self.checkout)

    def test_rejects_missing_dataset_file(self):
        os.remove(os.path.join(self.root, "dataset", "payload"))
        with self.assertRaisesRegex(ValueError, "membership mismatch"):
            verify_publication.verify(self.root, COMMIT, self.checkout)

    def test_rejects_escaping_checksum_path(self):
        checksums = os.path.join(self.root, "checksums.txt")
        with open(checksums, "a", encoding="utf-8") as handle:
            handle.write("{}  publication/pages/../../../escape\n".format("0" * 64))
        self._rebind_checksums()
        with self.assertRaisesRegex(ValueError, "unsafe persisted checksum path"):
            verify_publication.verify(self.root, COMMIT, self.checkout)

    def test_rejects_tampered_checksum_manifest(self):
        with open(os.path.join(self.root, "checksums.txt"), "a", encoding="utf-8") as handle:
            handle.write("\n")
        with self.assertRaisesRegex(ValueError, "checksum manifest"):
            verify_publication.verify(self.root, COMMIT, self.checkout)

    def test_rejects_descriptor_from_other_checkout(self):
        with open(self.checkout, "w", encoding="utf-8") as handle:
            handle.write('{"different":true}\n')
        with self.assertRaisesRegex(ValueError, "checked-out commit"):
            verify_publication.verify(self.root, COMMIT, self.checkout)


if __name__ == "__main__":
    unittest.main()
