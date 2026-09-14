import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from unittest import mock

import verify_github_release as subject


class GithubReleaseResumeTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = self.temporary.name
        for name in ("assets", "java", "python"):
            os.makedirs(os.path.join(self.root, name))
        self.asset = os.path.join(self.root, "assets", "dataset.zip")
        with open(self.asset, "wb") as handle:
            handle.write(b"dataset")
        with open(os.path.join(self.root, "checksums.txt"), "wb") as handle:
            handle.write(b"checksums")
        with open(os.path.join(self.root, "build-receipt.json"), "wb") as handle:
            handle.write(b"receipt")

    def tearDown(self):
        self.temporary.cleanup()

    def test_partial_release_uploads_only_missing_assets(self):
        checksum = hashlib.sha256(b"checksums").hexdigest()
        receipt = hashlib.sha256(b"receipt").hexdigest()
        response = subprocess.CompletedProcess([], 0, stdout=json.dumps({"assets": [
            {"name": "checksums.txt", "digest": "sha256:" + checksum},
            {"name": "build-receipt.json", "digest": "sha256:" + receipt},
        ]}))
        with mock.patch.object(subject.subprocess, "run", side_effect=[response, mock.DEFAULT]) as run:
            subject.synchronize("v12.0.0", self.root, "owner/repo")
        upload = run.call_args_list[1].args[0]
        self.assertEqual(["gh", "release", "upload", "v12.0.0", self.asset], upload)

    def test_conflicting_existing_asset_is_rejected(self):
        response = subprocess.CompletedProcess(
            [],
            0,
            stdout=json.dumps(
                {
                    "assets": [
                        {"name": "dataset.zip", "digest": "sha256:" + "0" * 64},
                        {
                            "name": "checksums.txt",
                            "digest": "sha256:" + hashlib.sha256(b"checksums").hexdigest(),
                        },
                        {
                            "name": "build-receipt.json",
                            "digest": "sha256:" + hashlib.sha256(b"receipt").hexdigest(),
                        },
                    ]
                }
            ),
        )
        with mock.patch.object(subject.subprocess, "run", return_value=response):
            with self.assertRaisesRegex(ValueError, "conflicting bytes"):
                subject.synchronize("v12.0.0", self.root, "owner/repo")


if __name__ == "__main__":
    unittest.main()
