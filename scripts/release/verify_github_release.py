#!/usr/bin/env python3
"""Reject a partial/conflicting retry; accept an already complete GitHub release."""

import hashlib
import os
import subprocess
import sys
import tempfile


def digest(path):
    value = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def expected(publication):
    result = {}
    for relative in ("assets", "java", "python"):
        root = os.path.join(publication, relative)
        for name in os.listdir(root):
            path = os.path.join(root, name)
            if os.path.isfile(path):
                if name in result:
                    raise ValueError("duplicate release asset name " + name)
                result[name] = digest(path)
    result["checksums.txt"] = digest(os.path.join(publication, "checksums.txt"))
    return result


tag, publication = sys.argv[1:]
wanted = expected(publication)
with tempfile.TemporaryDirectory() as temporary:
    subprocess.run(
        ["gh", "release", "download", tag, "--dir", temporary],
        check=True,
    )
    actual = {
        name: digest(os.path.join(temporary, name))
        for name in os.listdir(temporary)
        if os.path.isfile(os.path.join(temporary, name))
    }
    # A successful/partial Maven step persists its deployment ID as a release
    # asset so a later workflow run resumes instead of uploading another bundle.
    actual.pop("maven-deployment.json", None)
if actual != wanted:
    missing = sorted(set(wanted) - set(actual))
    extra = sorted(set(actual) - set(wanted))
    changed = sorted(name for name in set(actual) & set(wanted) if actual[name] != wanted[name])
    raise SystemExit(
        "conflicting existing release: missing={}, extra={}, changed={}".format(
            missing, extra, changed
        )
    )
print("Existing GitHub release is byte-identical; skipping upload.")
