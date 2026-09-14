import json
import os
import tempfile
import unittest

import descriptor


class DescriptorVerificationTest(unittest.TestCase):
    def test_descriptor_version_drift_is_rejected_before_publication(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = os.path.join(temporary, "release.json")
            with open(path, "w", encoding="utf-8") as handle:
                json.dump(
                    {
                        "schema_version": "1.0",
                        "source_sha": "a" * 40,
                        "generation_timestamp": "2026-01-01T00:00:00Z",
                        "versions": {
                            "data": "999.0.0",
                            "java_data": "999.0.0",
                            "java_core": "999.0.0",
                            "python": "999.0.0",
                            "wire_schema": "999.0.0",
                        },
                    },
                    handle,
                )
            with self.assertRaisesRegex(ValueError, "versions"):
                descriptor.verify(path, "blessed", "release/impact.json")


if __name__ == "__main__":
    unittest.main()
