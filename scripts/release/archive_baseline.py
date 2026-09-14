#!/usr/bin/env python3
"""Archive the authenticated published baseline without rewriting existing history."""

import argparse
import filecmp
import json
import os
import re
import shutil
import tempfile


def publication_entry(evidence, calendar_ids):
    asset = evidence.get("asset", {})
    entry = {
        "data_version": evidence["data_version"],
        "published_at": evidence["published_at"],
        "observed_current_at": evidence["observed_current_at"],
        "source_sha": evidence["source_sha"],
        "tag": evidence["tag"],
        "asset_sha256": asset["sha256"],
        "release_url": evidence["release_url"],
        "atomic_dataset": True,
        "calendar_ids": list(calendar_ids),
    }
    for key in (
        "data_version",
        "published_at",
        "observed_current_at",
        "source_sha",
        "tag",
        "asset_sha256",
        "release_url",
    ):
        value = entry[key]
        if not isinstance(value, str) or not value:
            raise ValueError("baseline evidence has invalid publication field " + key)
    if not re.fullmatch(r"[0-9a-f]{40}", entry["source_sha"]):
        raise ValueError("baseline evidence source_sha must be a full lowercase Git SHA")
    if not calendar_ids or list(calendar_ids) != sorted(set(calendar_ids)):
        raise ValueError("authenticated baseline calendar inventory is invalid")
    return entry


def publication_ledger(history, entry):
    path = os.path.join(history, "publications.json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as handle:
            ledger = json.load(handle)
    else:
        ledger = {"schema_version": "1.0", "releases": []}
    if ledger.get("schema_version") != "1.0" or not isinstance(ledger.get("releases"), list):
        raise ValueError("unsupported publication ledger")
    versions = [release.get("data_version") for release in ledger["releases"]]
    if len(versions) != len(set(versions)):
        raise ValueError("publication ledger contains duplicate data versions")
    matches = [
        release
        for release in ledger["releases"]
        if release.get("data_version") == entry["data_version"]
    ]
    if matches:
        if matches[0] != entry:
            raise ValueError(
                "publication ledger conflicts with authenticated release " + entry["data_version"]
            )
        return ledger, False
    ledger["releases"].append(entry)
    ledger["releases"].sort(
        key=lambda release: tuple(int(part) for part in release["data_version"].split("."))
    )
    return ledger, True


def write_ledger(history, ledger):
    os.makedirs(history, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(
        prefix="publications.", suffix=".tmp", dir=history
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(ledger, handle, indent=2, sort_keys=True)
            handle.write("\n")
        os.replace(temporary, os.path.join(history, "publications.json"))
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--baseline", required=True)
parser.add_argument("--evidence", required=True)
parser.add_argument("--history", default="release-history")
args = parser.parse_args()
with open(args.evidence, encoding="utf-8") as handle:
    evidence = json.load(handle)
with open(os.path.join(args.baseline, "manifest.json"), encoding="utf-8") as handle:
    calendars = sorted(json.load(handle)["calendars"])
entry = publication_entry(evidence, calendars)
ledger, ledger_changed = publication_ledger(args.history, entry)
version = evidence["data_version"]
short_sha = evidence["source_sha"][:7]
timestamp = evidence["published_at"].replace(":", "-")
version_id = "{}_{}_v{}".format(timestamp, short_sha, version)

for calendar_id in calendars:
    source = os.path.join(args.baseline, calendar_id)
    if not os.path.isdir(source):
        raise ValueError("authenticated baseline is missing calendar " + calendar_id)
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
if ledger_changed:
    write_ledger(args.history, ledger)
