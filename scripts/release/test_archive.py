import hashlib
import os
import sys
import tempfile
import unittest

import archive


class DeterministicArchiveTest(unittest.TestCase):
    def test_archives_do_not_depend_on_source_mtime_or_creation_order(self):
        with tempfile.TemporaryDirectory() as temporary:
            first = os.path.join(temporary, "first")
            second = os.path.join(temporary, "second")
            os.makedirs(first)
            os.makedirs(second)
            for root, names in ((first, ("b", "a")), (second, ("a", "b"))):
                for name in names:
                    path = os.path.join(root, name)
                    with open(path, "wb") as handle:
                        handle.write((name + "\n").encode())
                    os.utime(path, (100 if root == first else 900000, 100 if root == first else 900000))
            archive.create_tar(first, os.path.join(temporary, "one.tar.gz"))
            archive.create_tar(second, os.path.join(temporary, "two.tar.gz"))
            archive.create_zip(first, os.path.join(temporary, "one.zip"))
            archive.create_zip(second, os.path.join(temporary, "two.zip"))
            for suffix in ("tar.gz", "zip"):
                with open(os.path.join(temporary, "one." + suffix), "rb") as one:
                    one_hash = hashlib.sha256(one.read()).digest()
                with open(os.path.join(temporary, "two." + suffix), "rb") as two:
                    two_hash = hashlib.sha256(two.read()).digest()
                self.assertEqual(one_hash, two_hash)


if __name__ == "__main__":
    unittest.main()
