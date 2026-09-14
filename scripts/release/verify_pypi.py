#!/usr/bin/env python3
"""Fail if PyPI already has this version with different distribution bytes."""

import hashlib
import json
import os
import sys
import urllib.error
import urllib.request

version, directory = sys.argv[1:]
url = "https://pypi.org/pypi/bdc-calendars/{}/json".format(version)
try:
    with urllib.request.urlopen(url) as response:
        remote = json.load(response)
except urllib.error.HTTPError as error:
    if error.code == 404:
        print("PyPI version is available.")
        sys.exit(0)
    raise

expected = {}
for name in os.listdir(directory):
    path = os.path.join(directory, name)
    if os.path.isfile(path):
        with open(path, "rb") as handle:
            expected[name] = hashlib.sha256(handle.read()).hexdigest()
actual = {item["filename"]: item["digests"]["sha256"] for item in remote["urls"]}
if actual != expected:
    raise SystemExit(
        "conflicting PyPI version {}: local files do not match published files".format(version)
    )
print("PyPI version already contains byte-identical files; publication may be skipped.")
