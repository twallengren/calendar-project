#!/usr/bin/env python3
"""Archive the authenticated published baseline without rewriting existing history."""

import argparse
import filecmp
import json
import os
import shutil

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--baseline", required=True)
parser.add_argument("--evidence", required=True)
parser.add_argument("--history", default="release-history")
args = parser.parse_args()
with open(args.evidence, encoding="utf-8") as handle:
    evidence = json.load(handle)
version = evidence["data_version"]
short_sha = evidence["source_sha"][:7]
timestamp = evidence["published_at"].replace(":", "-")
version_id = "{}_{}_v{}".format(timestamp, short_sha, version)

with open(os.path.join(args.baseline, "manifest.json"), encoding="utf-8") as handle:
    calendars = sorted(json.load(handle)["calendars"])
for calendar_id in calendars:
    source = os.path.join(args.baseline, calendar_id)
    parent = os.path.join(args.history, calendar_id)
    matches = []
    if os.path.isdir(parent):
        matches = [
            os.path.join(parent, name)
            for name in os.listdir(parent)
            if name.endswith("_{}_v{}".format(short_sha, version))
        ]
    if len(matches) > 1:
        raise SystemExit("multiple history snapshots claim {} {}".format(calendar_id, version))
    if matches:
        existing = matches[0]
        for name in os.listdir(source):
            old = os.path.join(existing, name)
            if not os.path.isfile(old) or not filecmp.cmp(os.path.join(source, name), old, shallow=False):
                raise SystemExit(
                    "history conflicts with authenticated release: {}/{}".format(calendar_id, name)
                )
        print("Verified existing history for {} {}".format(calendar_id, version))
        continue
    os.makedirs(parent, exist_ok=True)
    target = os.path.join(parent, version_id)
    shutil.copytree(source, target)
    print("Archived authenticated {} {} to {}".format(calendar_id, version, target))
