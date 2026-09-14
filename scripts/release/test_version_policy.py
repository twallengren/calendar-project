import unittest

import version_policy


class VersionPolicyTest(unittest.TestCase):
    def test_exact_bumps(self):
        self.assertEqual((12, 0, 0), version_policy.expected("11.7.9", "MAJOR"))
        self.assertEqual((11, 8, 0), version_policy.expected("11.7.9", "MINOR"))
        self.assertEqual((11, 7, 10), version_policy.expected("11.7.9", "PATCH"))

    def test_invalid_severity_fails(self):
        with self.assertRaises(ValueError):
            version_policy.expected("11.0.0", "NONE")


if __name__ == "__main__":
    unittest.main()
