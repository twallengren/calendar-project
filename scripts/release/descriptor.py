#!/usr/bin/env python3
"""Write and verify the immutable inputs and generated hashes for a release PR."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import sys
from typing import Any, Dict, Sequence

SHA = re.compile(r"^[0-9a-f]{40}$")
SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")


def digest(path: str) -> str:
    value = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(chunk)
    return "sha256:" + value.hexdigest()


def artifact_hashes(root: str) -> Dict[str, str]:
    hashes = {}
    allowed_calendars = None
    manifest_path = os.path.join(root, "manifest.json")
    if os.path.isfile(manifest_path):
        with open(manifest_path, encoding="utf-8") as handle:
            allowed_calendars = set(json.load(handle).get("calendars", {}))
    for directory, names, files in os.walk(root):
        names.sort()
        for name in sorted(files):
            if name.endswith(".tmp"):
                continue
            path = os.path.join(directory, name)
            relative = os.path.relpath(path, root)
            top = relative.split(os.sep, 1)[0]
            if allowed_calendars is not None and top != "manifest.json" and top not in allowed_calendars:
                continue
            hashes[relative] = digest(path)
    return hashes


def supporting_hashes() -> Dict[str, str]:
    paths = [
        "python/bdc_calendars/_version.py",
        "tools/src/main/resources/site/settlement-fixture.json",
    ]
    for directory, names, files in os.walk("python/bdc_calendars/data"):
        names.sort()
        paths.extend(os.path.join(directory, name) for name in sorted(files))
    return {path.replace(os.sep, "/"): digest(path) for path in paths if os.path.isfile(path)}


def read_versions(path: str) -> Dict[str, str]:
    with open(path, encoding="utf-8") as handle:
        versions = json.load(handle)
    for key in ("data", "java_core", "python"):
        value = versions.get(key)
        if not isinstance(value, str) or not SEMVER.fullmatch(value):
            raise ValueError("{} has invalid {} version {!r}".format(path, key, value))
    return versions


def build(args: argparse.Namespace) -> Dict[str, Any]:
    if not SHA.fullmatch(args.baseline_commit) or not SHA.fullmatch(args.source_sha):
        raise ValueError("baseline commit and source SHA must be full 40-character Git SHAs")
    if not args.generated_at.endswith("Z"):
        raise ValueError("generation timestamp must be a UTC instant ending in Z")
    dt.datetime.fromisoformat(args.generated_at[:-1] + "+00:00")
    versions = read_versions(args.versions)
    with open(args.baseline_evidence, encoding="utf-8") as handle:
        evidence = json.load(handle)
    if (
        evidence.get("tag") != args.baseline_ref
        or evidence.get("tag_commit") != args.baseline_commit
        or evidence.get("data_version") != args.baseline_version
    ):
        raise ValueError("baseline evidence does not match the requested baseline")
    with open(args.impact, encoding="utf-8") as handle:
        impact = json.load(handle)
    if impact["severity"] == "NONE":
        raise ValueError("refusing to describe a release with no publishable change")
    if impact.get("baseline_data_version") != args.baseline_version:
        raise ValueError("impact report does not describe the requested baseline version")
    if versions["data"] != args.data_version:
        raise ValueError("release/versions.json data version differs from --data-version")
    return {
        "schema_version": "1.0",
        "baseline": {
            "ref": args.baseline_ref,
            "commit": args.baseline_commit,
            "data_version": args.baseline_version,
            "evidence": os.path.relpath(args.baseline_evidence),
            "evidence_sha256": digest(args.baseline_evidence),
        },
        "source_sha": args.source_sha,
        "generation_timestamp": args.generated_at,
        "versions": {
            "data": args.data_version,
            "java_core": versions["java_core"],
            "java_data": args.data_version,
            "python": versions["python"],
        },
        "impact": {
            "severity": impact["severity"],
            "report": os.path.relpath(args.impact),
            "sha256": digest(args.impact),
        },
        "artifacts": artifact_hashes(args.artifacts),
        "supporting_artifacts": supporting_hashes(),
    }


def verify(path: str, artifacts: str, impact: str) -> None:
    with open(path, encoding="utf-8") as handle:
        descriptor = json.load(handle)
    expected = artifact_hashes(artifacts)
    if descriptor.get("artifacts") != expected:
        raise ValueError("release descriptor artifact hashes do not match the working tree")
    if descriptor.get("supporting_artifacts") != supporting_hashes():
        raise ValueError("release descriptor supporting artifact hashes do not match the working tree")
    if descriptor.get("impact", {}).get("sha256") != digest(impact):
        raise ValueError("release descriptor impact hash does not match the working tree")
    evidence = descriptor.get("baseline", {}).get("evidence")
    if not evidence or descriptor["baseline"].get("evidence_sha256") != digest(evidence):
        raise ValueError("release descriptor baseline evidence hash does not match the working tree")


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--output", default="release/release.json")
    parser.add_argument("--artifacts", default="blessed")
    parser.add_argument("--impact", default="release/impact.json")
    parser.add_argument("--versions", default="release/versions.json")
    parser.add_argument("--baseline-ref")
    parser.add_argument("--baseline-commit")
    parser.add_argument("--baseline-version")
    parser.add_argument("--baseline-evidence")
    parser.add_argument("--source-sha")
    parser.add_argument("--generated-at")
    parser.add_argument("--data-version")
    args = parser.parse_args(argv)
    try:
        if args.verify:
            verify(args.output, args.artifacts, args.impact)
        else:
            required = (
                "baseline_ref",
                "baseline_commit",
                "baseline_version",
                "baseline_evidence",
                "source_sha",
                "generated_at",
                "data_version",
            )
            missing = [name for name in required if not getattr(args, name)]
            if missing:
                parser.error("missing arguments: " + ", ".join(missing))
            value = build(args)
            with open(args.output, "w", encoding="utf-8") as handle:
                json.dump(value, handle, indent=2, sort_keys=True)
                handle.write("\n")
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print("release descriptor failed: {}".format(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
