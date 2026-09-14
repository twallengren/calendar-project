#!/usr/bin/env python3
"""Verify every byte in a persisted publication before a retry reuses it."""

from __future__ import annotations

import hashlib
import json
import os
import re
import sys
from pathlib import PurePosixPath


DELIVERABLE_DIRECTORIES = ("dataset", "assets", "java", "python", "maven", "pages")
ROOT_FILES = ("build-receipt.json", "checksums.txt")
CHECKSUM_LINE = re.compile(r"^([0-9a-f]{64})  (publication/(.+))$")


def digest(path: str) -> str:
    value = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def _publication_files(root: str) -> set[str]:
    expected_entries = set(DELIVERABLE_DIRECTORIES) | set(ROOT_FILES)
    entries = set(os.listdir(root))
    if entries != expected_entries:
        unexpected = sorted(entries - expected_entries)
        missing = sorted(expected_entries - entries)
        raise ValueError(
            "persisted publication has unexpected or missing root entries "
            "(unexpected={!r}, missing={!r})".format(unexpected, missing)
        )
    for name in ROOT_FILES:
        path = os.path.join(root, name)
        if not os.path.isfile(path) or os.path.islink(path):
            raise ValueError("persisted publication root file is missing or unsafe: " + name)
    files: set[str] = set()
    for top in DELIVERABLE_DIRECTORIES:
        directory_root = os.path.join(root, top)
        if not os.path.isdir(directory_root) or os.path.islink(directory_root):
            raise ValueError("persisted publication directory is missing or unsafe: " + top)
        for directory, names, entries in os.walk(directory_root):
            names.sort()
            if any(os.path.islink(os.path.join(directory, name)) for name in names):
                raise ValueError("persisted publication contains a directory symlink")
            for name in sorted(entries):
                path = os.path.join(directory, name)
                if not os.path.isfile(path) or os.path.islink(path):
                    raise ValueError("persisted publication contains a non-regular file")
                relative = os.path.relpath(path, root).replace(os.sep, "/")
                files.add("publication/" + relative)
    return files


def _declared_checksums(root: str) -> dict[str, str]:
    declared: dict[str, str] = {}
    with open(os.path.join(root, "checksums.txt"), encoding="utf-8") as handle:
        for line_number, raw in enumerate(handle, start=1):
            line = raw.rstrip("\n")
            match = CHECKSUM_LINE.fullmatch(line)
            if not match:
                raise ValueError("malformed persisted checksum line {}".format(line_number))
            wanted, relative, _ = match.groups()
            parts = PurePosixPath(relative).parts
            if (
                "\\" in relative
                or str(PurePosixPath(relative)) != relative
                or parts[:1] != ("publication",)
                or len(parts) < 3
                or parts[1] not in DELIVERABLE_DIRECTORIES
                or any(part in ("", ".", "..") for part in parts)
            ):
                raise ValueError("unsafe persisted checksum path " + relative)
            if relative in declared:
                raise ValueError("duplicate persisted checksum path " + relative)
            declared[relative] = wanted
    return declared


def verify(root: str, commit: str, checkout_descriptor: str = "release/release.json") -> None:
    if os.path.islink(root) or not os.path.isdir(root):
        raise ValueError("persisted publication root is missing or unsafe")
    receipt_path = os.path.join(root, "build-receipt.json")
    checksums_path = os.path.join(root, "checksums.txt")
    with open(receipt_path, encoding="utf-8") as handle:
        receipt = json.load(handle)
    if receipt.get("schema_version") != "1.0" or set(receipt) != {
        "schema_version",
        "release_commit",
        "descriptor_sha256",
        "checksums_sha256",
    }:
        raise ValueError("persisted publication has an invalid build receipt")
    if receipt.get("release_commit") != commit:
        raise ValueError("persisted publication belongs to a different release commit")
    descriptor_path = os.path.join(root, "dataset", "release.json")
    descriptor_digest = digest(descriptor_path)
    if receipt.get("descriptor_sha256") != "sha256:" + descriptor_digest:
        raise ValueError("persisted publication descriptor does not match its build receipt")
    if descriptor_digest != digest(checkout_descriptor):
        raise ValueError("persisted publication descriptor does not match the checked-out commit")
    if receipt.get("checksums_sha256") != "sha256:" + digest(checksums_path):
        raise ValueError("persisted publication checksum manifest does not match its build receipt")

    actual = _publication_files(root)
    declared = _declared_checksums(root)
    if set(declared) != actual:
        unexpected = sorted(set(declared) - actual)
        missing = sorted(actual - set(declared))
        raise ValueError(
            "persisted checksum membership mismatch (unexpected={!r}, missing={!r})".format(
                unexpected, missing
            )
        )
    for relative, wanted in sorted(declared.items()):
        path = os.path.join(root, *PurePosixPath(relative).parts[1:])
        if digest(path) != wanted:
            raise ValueError("persisted publication checksum mismatch: " + relative)


def main() -> int:
    try:
        root, commit = sys.argv[1:]
        verify(root, commit)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print("publication verification failed: {}".format(error), file=sys.stderr)
        return 1
    print("Verified persisted build for " + commit)
    return 0


if __name__ == "__main__":
    sys.exit(main())
