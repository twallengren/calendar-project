#!/usr/bin/env python3
"""Synchronize the candidate manifest's IDs/ranges from calendar source coverage."""

import argparse
import json
import os


def scalar(line):
    return line.split(":", 1)[1].split("#", 1)[0].strip().strip("'\"")


def calendar(path):
    calendar_id = None
    coverage = False
    start = end = None
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("id:"):
                calendar_id = scalar(line)
            elif line == "  coverage:\n":
                coverage = True
            elif coverage and line.startswith("    from:"):
                start = scalar(line)
            elif coverage and line.startswith("    to:"):
                end = scalar(line)
            elif coverage and line.strip() and not line.startswith("    "):
                coverage = False
    if not calendar_id or not start or not end:
        raise ValueError("{} must declare id and metadata.coverage from/to".format(path))
    return calendar_id, start, end


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--calendars", default="calendars")
parser.add_argument("--blessed", default="blessed")
args = parser.parse_args()
manifest_path = os.path.join(args.blessed, "manifest.json")
with open(manifest_path, encoding="utf-8") as handle:
    manifest = json.load(handle)
old = manifest.get("calendars", {})
new = {}
for filename in sorted(os.listdir(args.calendars)):
    if not filename.endswith((".yaml", ".yml")):
        continue
    calendar_id, start, end = calendar(os.path.join(args.calendars, filename))
    if calendar_id in new:
        raise ValueError("duplicate calendar id " + calendar_id)
    entry = dict(old.get(calendar_id, {}))
    entry["range_start"] = start
    entry["range_end"] = end
    new[calendar_id] = entry
manifest["calendars"] = new
with open(manifest_path, "w", encoding="utf-8") as handle:
    json.dump(manifest, handle, indent=2)
    handle.write("\n")
