import unittest

import package_version_policy


class PackageVersionPolicyTest(unittest.TestCase):
    def test_dataset_change_requires_new_python_distribution(self):
        with self.assertRaisesRegex(ValueError, "new Python package"):
            package_version_policy.validate_dataset_release(
                {"python": "0.12.0"}, {"python": "0.12.0"}
            )
        package_version_policy.validate_dataset_release(
            {"python": "0.13.0"}, {"python": "0.12.0"}
        )


if __name__ == "__main__":
    unittest.main()
