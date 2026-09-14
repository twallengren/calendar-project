#!/usr/bin/env python3
"""Validate package-version changes against the last reviewed release descriptor."""

import argparse
import json
import os
import sys

from software_impact import version


def validate_dataset_release(current, previous):
    old = previous["python"]
    new = current["python"]
    if version(new) <= version(old):
        raise ValueError(
            "a changed bundled dataset requires a new Python package version "
            "(published {}, candidate {})".format(old, new)
        )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-descriptor", required=True)
    parser.add_argument("--versions", default="release/versions.json")
    args = parser.parse_args()
    if not os.path.isfile(args.baseline_descriptor):
        return 0  # Legacy releases did not record independent package versions.
    try:
        with open(args.baseline_descriptor, encoding="utf-8") as handle:
            previous = json.load(handle)["versions"]
        with open(args.versions, encoding="utf-8") as handle:
            current = json.load(handle)
        validate_dataset_release(current, previous)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print("package version policy failed: {}".format(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
