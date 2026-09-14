#!/usr/bin/env python3
"""Write and verify the immutable inputs and generated hashes for a release PR."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import subprocess
import sys
from typing import Any, Dict, Sequence

SHA = re.compile(r"^[0-9a-f]{40}$")
SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")


def _load_json(path: str) -> Dict[str, Any]:
    with open(path, encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError("{} must contain a JSON object".format(path))
    return value


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
    for directory, names, files in os.walk("python/tests/fixtures"):
        names.sort()
        paths.extend(os.path.join(directory, name) for name in sorted(files))
    return {path.replace(os.sep, "/"): digest(path) for path in paths if os.path.isfile(path)}


def tree_digest(root: str) -> str:
    if os.path.isfile(root):
        return digest(root)
    value = hashlib.sha256()
    if not os.path.isdir(root):
        return "sha256:" + value.hexdigest()
    for directory, names, files in os.walk(root):
        names.sort()
        for name in sorted(files):
            path = os.path.join(directory, name)
            relative = os.path.relpath(path, root).replace(os.sep, "/")
            value.update(relative.encode("utf-8") + b"\0" + digest(path).encode("ascii") + b"\n")
    return "sha256:" + value.hexdigest()


def tracked_tree_digest(root: str, excludes: tuple[str, ...] = ()) -> str:
    value = hashlib.sha256()
    files = subprocess.check_output(["git", "ls-files", "--", root], text=True).splitlines()
    for path in sorted(files):
        relative = os.path.relpath(path, root).replace(os.sep, "/")
        if any(relative == excluded or relative.startswith(excluded + "/") for excluded in excludes):
            continue
        value.update(path.encode("utf-8") + b"\0" + digest(path).encode("ascii") + b"\n")
    return "sha256:" + value.hexdigest()


def input_hashes() -> Dict[str, str]:
    roots = (
        "calendars",
        "modules",
        "chronologies",
        "sources",
        "spec",
        "tools/src/main/java",
        "tools/src/main/java-generated",
        "tools/src/main/resources",
        "core/src/main",
        "scripts/release",
        "scripts/bless.sh",
        "python/scripts",
        "python/pyproject.toml",
        ".github/workflows/release.yml",
        ".github/workflows/release-pr.yml",
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "core/build.gradle.kts",
        "data/build.gradle.kts",
        "tools/build.gradle.kts",
        "gradle/wrapper/gradle-wrapper.properties",
        "gradle/verification-metadata.xml",
        "release/versions.json",
        "LICENSE",
        "DATA_LICENSE",
        "NOTICE",
    )
    hashes = {root: tracked_tree_digest(root) for root in roots}
    hashes["python/bdc_calendars"] = tracked_tree_digest(
        "python/bdc_calendars", excludes=("data", "_version.py")
    )
    return hashes


def read_versions(path: str) -> Dict[str, Any]:
    with open(path, encoding="utf-8") as handle:
        versions = json.load(handle)
    for key in ("data", "java_core", "python"):
        value = versions.get(key)
        if not isinstance(value, str) or not SEMVER.fullmatch(value):
            raise ValueError("{} has invalid {} version {!r}".format(path, key, value))
    wire = versions.get("wire_schema")
    if not isinstance(wire, dict) or set(wire) != {"current", "served"}:
        raise ValueError("{} has invalid wire_schema declaration".format(path))
    current = wire.get("current")
    served = wire.get("served")
    if (
        not isinstance(current, str)
        or not SEMVER.fullmatch(current)
        or not isinstance(served, list)
        or not served
        or any(not isinstance(value, str) or not SEMVER.fullmatch(value) for value in served)
        or len(set(served)) != len(served)
        or current not in served
    ):
        raise ValueError("{} has invalid wire_schema versions".format(path))
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
            "wire_schema": versions["wire_schema"],
        },
        "impact": {
            "severity": impact["severity"],
            "report": os.path.relpath(args.impact),
            "sha256": digest(args.impact),
        },
        "artifacts": artifact_hashes(args.artifacts),
        "supporting_artifacts": supporting_hashes(),
        "history_sha256": tree_digest("release-history"),
        "inputs": input_hashes(),
    }


def verify(path: str, artifacts: str, impact: str) -> None:
    with open(path, encoding="utf-8") as handle:
        descriptor = json.load(handle)
    if descriptor.get("schema_version") != "1.0":
        raise ValueError("unsupported release descriptor schema")
    if not SHA.fullmatch(str(descriptor.get("source_sha", ""))):
        raise ValueError("release descriptor has invalid source SHA")
    generated_at = str(descriptor.get("generation_timestamp", ""))
    if not generated_at.endswith("Z"):
        raise ValueError("release descriptor has invalid UTC generation timestamp")
    dt.datetime.fromisoformat(generated_at[:-1] + "+00:00")
    versions = read_versions("release/versions.json")
    declared = descriptor.get("versions", {})
    if (
        declared.get("data") != versions["data"]
        or declared.get("java_data") != versions["data"]
        or declared.get("java_core") != versions["java_core"]
        or declared.get("python") != versions["python"]
        or declared.get("wire_schema") != versions["wire_schema"]
    ):
        raise ValueError("release descriptor versions do not match release/versions.json")
    expected = artifact_hashes(artifacts)
    if descriptor.get("artifacts") != expected:
        raise ValueError("release descriptor artifact hashes do not match the working tree")
    if descriptor.get("supporting_artifacts") != supporting_hashes():
        raise ValueError("release descriptor supporting artifact hashes do not match the working tree")
    if descriptor.get("history_sha256") != tree_digest("release-history"):
        raise ValueError("release descriptor does not match immutable release history")
    if descriptor.get("inputs") != input_hashes():
        raise ValueError("release descriptor reproducibility inputs do not match the working tree")
    if descriptor.get("impact", {}).get("sha256") != digest(impact):
        raise ValueError("release descriptor impact hash does not match the working tree")
    evidence = descriptor.get("baseline", {}).get("evidence")
    if not evidence or descriptor["baseline"].get("evidence_sha256") != digest(evidence):
        raise ValueError("release descriptor baseline evidence hash does not match the working tree")
    with open(evidence, encoding="utf-8") as handle:
        evidence_value = json.load(handle)
    baseline = descriptor["baseline"]
    if (
        evidence_value.get("tag") != baseline.get("ref")
        or evidence_value.get("tag_commit") != baseline.get("commit")
        or evidence_value.get("data_version") != baseline.get("data_version")
    ):
        raise ValueError("release descriptor baseline fields do not match authenticated evidence")
    tagged = subprocess.check_output(
        ["git", "rev-parse", "{}^{{commit}}".format(baseline["ref"])], text=True
    ).strip()
    if tagged != baseline["commit"]:
        raise ValueError("release baseline tag no longer resolves to the declared commit")
    with open(impact, encoding="utf-8") as handle:
        impact_value = json.load(handle)
    if impact_value.get("severity") not in ("PATCH", "MINOR", "MAJOR"):
        raise ValueError("release impact has invalid severity")
    if descriptor.get("impact", {}).get("severity") != impact_value.get("severity"):
        raise ValueError("release descriptor severity does not match impact report")
    if (
        impact_value.get("baseline_data_version") != baseline.get("data_version")
        or impact_value.get("candidate_data_version") != declared["data"]
    ):
        raise ValueError("impact report versions do not match release descriptor")
    manifest = _load_json(os.path.join(artifacts, "manifest.json"))
    release = manifest.get("release_version", {})
    if release.get("semantic") != declared["data"] or release.get("git_sha") != descriptor["source_sha"]:
        raise ValueError("blessed manifest version/source do not match release descriptor")
    for calendar_id in manifest.get("calendars", {}):
        metadata = _load_json(os.path.join(artifacts, calendar_id, "metadata.json"))
        source = metadata.get("source_version", {})
        if source.get("semantic") != declared["data"] or source.get("git_sha") != descriptor["source_sha"]:
            raise ValueError("{} metadata version/source does not match descriptor".format(calendar_id))


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
