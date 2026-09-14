#!/usr/bin/env python3
"""Describe an independent core/Python release over unchanged published data."""

from __future__ import annotations

import argparse
import filecmp
import json
import os
import re
import subprocess
import sys


SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")


def version(value):
    if not isinstance(value, str) or not SEMVER.fullmatch(value):
        raise ValueError("invalid semantic version {!r}".format(value))
    return tuple(int(part) for part in value.split("."))


def previous_versions(revision):
    payload = subprocess.check_output(
        ["git", "show", "{}:release/versions.json".format(revision)], text=True
    )
    return json.loads(payload)


def published_files(root, manifest):
    paths = ["manifest.json"]
    for calendar_id in sorted(manifest["calendars"]):
        directory = os.path.join(root, calendar_id)
        if not os.path.isdir(directory):
            raise ValueError("published dataset is missing calendar " + calendar_id)
        for base, names, files in os.walk(directory):
            names.sort()
            for name in sorted(files):
                paths.append(os.path.relpath(os.path.join(base, name), root).replace(os.sep, "/"))
    return paths


def verify_unchanged_dataset(baseline, candidate):
    with open(os.path.join(baseline, "manifest.json"), encoding="utf-8") as handle:
        baseline_manifest = json.load(handle)
    with open(os.path.join(candidate, "manifest.json"), encoding="utf-8") as handle:
        candidate_manifest = json.load(handle)
    baseline_paths = published_files(baseline, baseline_manifest)
    candidate_paths = published_files(candidate, candidate_manifest)
    if baseline_paths != candidate_paths:
        raise ValueError("software-only release would change published dataset membership")
    for relative in baseline_paths:
        if not filecmp.cmp(
            os.path.join(baseline, relative), os.path.join(candidate, relative), shallow=False
        ):
            raise ValueError("software-only release would change published dataset " + relative)
    return baseline_manifest


def impact(current, previous, baseline_version):
    if current.get("data") != baseline_version or previous.get("data") != baseline_version:
        raise ValueError("software-only release cannot change the dataset version")
    changes = {}
    for key in ("java_core", "python"):
        old, new = previous.get(key), current.get(key)
        if old != new:
            if version(new) <= version(old):
                raise ValueError("{} version must increase for a software release".format(key))
            changes[key] = {"from": old, "to": new}
    if current.get("wire_schema") != previous.get("wire_schema"):
        if not changes:
            raise ValueError("wire schema changes require a core or Python version increase")
        changes["wire_schema"] = {
            "from": previous.get("wire_schema"),
            "to": current.get("wire_schema"),
        }
    if not changes:
        raise ValueError("software-only source changes require an explicit core or Python version increase")
    package_changes = [key for key in ("java_core", "python") if key in changes]
    if package_changes == ["java_core"]:
        tag = "core-v" + current["java_core"]
    elif package_changes == ["python"]:
        tag = "python-v" + current["python"]
    else:
        tag = "software-core-v{}-python-v{}".format(current["java_core"], current["python"])
    return {
        "schema_version": "1.0",
        "release_kind": "SOFTWARE",
        "release_tag": tag,
        "severity": "NONE",
        "baseline_data_version": baseline_version,
        "candidate_data_version": baseline_version,
        "software_changes": changes,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", default="blessed")
    parser.add_argument("--previous-revision", default="HEAD^")
    parser.add_argument("--versions", default="release/versions.json")
    parser.add_argument("--output", default="release/impact.json")
    args = parser.parse_args()
    try:
        manifest = verify_unchanged_dataset(args.baseline, args.candidate)
        baseline_version = manifest["release_version"]["semantic"]
        with open(args.versions, encoding="utf-8") as handle:
            current = json.load(handle)
        result = impact(current, previous_versions(args.previous_revision), baseline_version)
        with open(args.output, "w", encoding="utf-8") as handle:
            json.dump(result, handle, indent=2, sort_keys=True)
            handle.write("\n")
    except (OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print("software release preparation failed: {}".format(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
