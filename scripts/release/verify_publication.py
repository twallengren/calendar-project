#!/usr/bin/env python3
"""Verify a persisted build artifact before a retry reuses it."""

import hashlib
import json
import os
import sys


def digest(path):
    value = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


root, commit = sys.argv[1:]
with open(os.path.join(root, "dataset", "release.json"), encoding="utf-8") as handle:
    descriptor = json.load(handle)
if descriptor.get("release_commit") != commit:
    raise SystemExit("persisted publication belongs to a different release commit")
with open(os.path.join(root, "checksums.txt"), encoding="utf-8") as handle:
    for line in handle:
        wanted, relative = line.rstrip("\n").split("  ", 1)
        if not relative.startswith("publication/"):
            raise SystemExit("invalid persisted checksum path " + relative)
        path = os.path.join(root, relative[len("publication/") :])
        if not os.path.isfile(path) or digest(path) != wanted:
            raise SystemExit("persisted publication checksum mismatch: " + relative)
print("Verified persisted build for " + commit)
