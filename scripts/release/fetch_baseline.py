#!/usr/bin/env python3
"""Fetch and authenticate the dataset users actually received in a GitHub release."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import tarfile
import tempfile
import urllib.request


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def request_json(url, token):
    request = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    if token:
        request.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(request) as response:
        return json.load(response)


def download(url):
    with urllib.request.urlopen(url) as response:
        return response.read()


def safe_extract(payload, output):
    os.makedirs(output, exist_ok=True)
    with tempfile.NamedTemporaryFile() as temporary:
        temporary.write(payload)
        temporary.flush()
        with tarfile.open(temporary.name, "r:gz") as archive:
            for member in archive.getmembers():
                relative = member.name.lstrip("./")
                if not relative or member.isdir():
                    continue
                target = os.path.realpath(os.path.join(output, relative))
                if os.path.commonpath((os.path.realpath(output), target)) != os.path.realpath(output):
                    raise ValueError("archive path escapes output: " + member.name)
                if not member.isfile():
                    raise ValueError("release archive contains a non-regular file: " + member.name)
                os.makedirs(os.path.dirname(target), exist_ok=True)
                source = archive.extractfile(member)
                if source is None:
                    raise ValueError("cannot read archive member " + member.name)
                with source, open(target, "wb") as handle:
                    handle.write(source.read())


def synthesize_manifest(output, version, git_sha, published_at):
    calendars = {}
    aliases = {}
    for calendar_id in sorted(os.listdir(output)):
        target = os.path.join(output, calendar_id)
        metadata_path = os.path.join(target, "metadata.json")
        events_path = os.path.join(target, "events.csv")
        if not os.path.isdir(target) or not os.path.isfile(metadata_path):
            continue
        with open(metadata_path, encoding="utf-8") as handle:
            metadata = json.load(handle)
        with open(events_path, "rb") as handle:
            digest = hashlib.sha256(handle.read()).hexdigest()
        with open(events_path, encoding="utf-8") as handle:
            count = sum(1 for _line in handle) - 1
        calendars[calendar_id] = {
            "kind": metadata.get("kind", "market"),
            "range_start": metadata["range_start"],
            "range_end": metadata["range_end"],
            "event_count": count,
            "checksum": "sha256:" + digest,
        }
        if metadata.get("mic"):
            aliases[metadata["mic"]] = calendar_id
        for alias in metadata.get("aliases") or []:
            aliases[alias] = calendar_id
    manifest = {
        "schema_version": "1.0",
        "blessed_at": published_at,
        "calendars": calendars,
        "aliases": dict(sorted(aliases.items())),
        "release_version": {
            "semantic": version,
            "git_sha": git_sha,
            "generation_date": published_at[:10],
        },
    }
    with open(os.path.join(output, "manifest.json"), "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, sort_keys=True)
        handle.write("\n")


def prove_tag_equivalence(output, tag):
    checked = 0
    for directory, _names, files in os.walk(output):
        for name in files:
            if name == "manifest.json":
                continue
            path = os.path.join(directory, name)
            relative = os.path.relpath(path, output).replace(os.sep, "/")
            tagged = subprocess.run(
                ["git", "show", "{}:blessed/{}".format(tag, relative)],
                check=True,
                capture_output=True,
            ).stdout
            with open(path, "rb") as handle:
                if tagged != handle.read():
                    raise ValueError("published asset differs from tag at " + relative)
            checked += 1
    return checked


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--tag-commit", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--prove-tag-equivalence", action="store_true")
    args = parser.parse_args()
    if subprocess.check_output(
        ["git", "rev-parse", "{}^{{commit}}".format(args.tag)], text=True
    ).strip() != args.tag_commit:
        raise SystemExit("--tag-commit does not match the local immutable tag")
    token = os.environ.get("GITHUB_TOKEN", "")
    url = "https://api.github.com/repos/{}/releases/tags/{}".format(args.repository, args.tag)
    release = request_json(url, token)
    archives = [a for a in release["assets"] if a["name"].endswith(".tar.gz")]
    checksums = [a for a in release["assets"] if a["name"] == "checksums.txt"]
    if len(archives) != 1 or len(checksums) != 1:
        raise SystemExit("published release must contain one dataset tar.gz and checksums.txt")
    archive, checksum_asset = archives[0], checksums[0]
    payload = download(archive["browser_download_url"])
    actual = sha256(payload)
    declared = str(archive.get("digest", ""))
    if declared != "sha256:" + actual:
        raise SystemExit("release API archive digest mismatch")
    checksum_payload = download(checksum_asset["browser_download_url"])
    checksum_lines = checksum_payload.decode("utf-8").splitlines()
    expected = [line.split()[0] for line in checksum_lines if archive["name"] in line]
    if expected != [actual]:
        raise SystemExit("checksums.txt does not authenticate the selected archive")
    safe_extract(payload, args.output)

    versions = set()
    source_shas = set()
    for name in os.listdir(args.output):
        metadata_path = os.path.join(args.output, name, "metadata.json")
        if os.path.isfile(metadata_path):
            with open(metadata_path, encoding="utf-8") as handle:
                source = json.load(handle)["source_version"]
            versions.add(source["semantic"])
            source_shas.add(source["git_sha"])
    if len(versions) != 1 or len(source_shas) != 1:
        raise SystemExit("published calendars disagree on release version or source SHA")
    version = versions.pop()
    source_sha = source_shas.pop()
    tag_files = prove_tag_equivalence(args.output, args.tag) if args.prove_tag_equivalence else None
    synthesize_manifest(args.output, version, source_sha, release["published_at"])
    evidence = {
        "schema_version": "1.0",
        "repository": args.repository,
        "release_id": release["id"],
        "tag": args.tag,
        "tag_commit": args.tag_commit,
        "published_at": release["published_at"],
        "asset": {
            "id": archive["id"],
            "name": archive["name"],
            "size": archive["size"],
            "sha256": "sha256:" + actual,
            "url": archive["browser_download_url"],
        },
        "checksums_asset": {
            "id": checksum_asset["id"],
            "sha256": "sha256:" + sha256(checksum_payload),
        },
        "data_version": version,
        "source_sha": source_sha,
        "tag_equivalent_files": tag_files,
    }
    with open(args.evidence, "w", encoding="utf-8") as handle:
        json.dump(evidence, handle, indent=2, sort_keys=True)
        handle.write("\n")


if __name__ == "__main__":
    main()
