#!/usr/bin/env python3
"""Synchronize a GitHub release, preserving matching assets and filling only missing ones."""

import hashlib
import json
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
                result[name] = (path, digest(path))
    path = os.path.join(publication, "checksums.txt")
    result["checksums.txt"] = (path, digest(path))
    path = os.path.join(publication, "build-receipt.json")
    result["build-receipt.json"] = (path, digest(path))
    return result


def synchronize(tag, publication, repository):
    wanted = expected(publication)
    release = json.loads(
        subprocess.run(
            ["gh", "api", "repos/{}/releases/tags/{}".format(repository, tag)],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    )
    actual = {asset["name"]: asset for asset in release["assets"]}
    unexpected = set(actual) - set(wanted) - {"maven-deployment.json"}
    if unexpected:
        raise ValueError("existing release has unexpected assets: {}".format(sorted(unexpected)))
    for name, (path, local_digest) in wanted.items():
        asset = actual.get(name)
        if asset is None:
            subprocess.run(["gh", "release", "upload", tag, path], check=True)
            continue
        remote_digest = asset.get("digest")
        if remote_digest:
            matches = remote_digest == "sha256:" + local_digest
        else:
            with tempfile.TemporaryDirectory() as temporary:
                subprocess.run(
                    ["gh", "release", "download", tag, "--pattern", name, "--dir", temporary],
                    check=True,
                )
                matches = digest(os.path.join(temporary, name)) == local_digest
        if not matches:
            raise ValueError("existing release asset has conflicting bytes: " + name)


if __name__ == "__main__":
    try:
        synchronize(sys.argv[1], sys.argv[2], os.environ["GITHUB_REPOSITORY"])
    except (KeyError, OSError, ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit("GitHub release synchronization failed: {}".format(error))
