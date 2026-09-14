#!/usr/bin/env python3
"""Verify existing Maven coordinates and build a bundle containing only missing ones."""

import argparse
import hashlib
import json
import sys
import urllib.error
import urllib.request
import zipfile

CENTRAL = "https://repo1.maven.org/maven2/"
CANONICAL_SUFFIXES = (".pom", ".jar", ".module")


class IncompleteRemote(RuntimeError):
    pass


def fetch(url):
    try:
        with urllib.request.urlopen(url) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def plan(bundle_path, coordinates, fetcher=fetch):
    result = {"completed": [], "missing": []}
    with zipfile.ZipFile(bundle_path) as bundle:
        names = bundle.namelist()
        for artifact, version in coordinates:
            prefix = "io/github/twallengren/{}/{}/".format(artifact, version)
            entries = [name for name in names if name.startswith(prefix) and not name.endswith("/")]
            if not entries:
                raise ValueError("bundle does not contain {}:{}".format(artifact, version))
            pom_name = "{}{}-{}.pom".format(prefix, artifact, version)
            if fetcher(CENTRAL + pom_name) is None:
                result["missing"].append((artifact, version))
                continue
            for name in entries:
                if not name.endswith(CANONICAL_SUFFIXES):
                    continue
                remote = fetcher(CENTRAL + name)
                if remote is None:
                    raise IncompleteRemote("published coordinate is not fully visible: " + name)
                if remote != bundle.read(name):
                    raise ValueError("published Maven artifact conflicts with bundle: " + name)
            result["completed"].append((artifact, version))
    return result


def filtered_bundle(source, output, missing):
    prefixes = [
        "io/github/twallengren/{}/{}/".format(artifact, version)
        for artifact, version in missing
    ]
    with zipfile.ZipFile(source) as old, zipfile.ZipFile(
        output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
    ) as new:
        for name in sorted(old.namelist()):
            if name.endswith("/") or not any(name.startswith(prefix) for prefix in prefixes):
                continue
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            new.writestr(info, old.read(name))


def digest(path):
    value = hashlib.sha256()
    with open(path, "rb") as handle:
        value.update(handle.read())
    return "sha256:" + value.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle")
    parser.add_argument("output")
    parser.add_argument("--core-version", required=True)
    parser.add_argument("--data-version", required=True)
    args = parser.parse_args()
    try:
        outcome = plan(
            args.bundle,
            (
                ("bdc-calendar-core", args.core_version),
                ("bdc-calendar-data", args.data_version),
            ),
        )
    except IncompleteRemote as error:
        print(str(error), file=sys.stderr)
        return 2
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 1
    if outcome["missing"]:
        filtered_bundle(args.bundle, args.output, outcome["missing"])
    print(json.dumps({
        "upload_required": bool(outcome["missing"]),
        "completed": ["{}:{}".format(*item) for item in outcome["completed"]],
        "missing": ["{}:{}".format(*item) for item in outcome["missing"]],
        "bundle_sha256": digest(args.output) if outcome["missing"] else None,
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
