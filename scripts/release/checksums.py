#!/usr/bin/env python3
"""Print stable SHA-256 entries for all regular files below the arguments."""

import hashlib
import os
import sys


def digest(path):
    value = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


files = []
for root in sys.argv[1:]:
    for directory, names, entries in os.walk(root):
        names.sort()
        for name in sorted(entries):
            path = os.path.join(directory, name)
            files.append(path)
for path in sorted(files):
    print("{}  {}".format(digest(path), path.replace(os.sep, "/")))
