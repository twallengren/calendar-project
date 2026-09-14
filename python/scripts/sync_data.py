#!/usr/bin/env python3
"""
Copies the published calendar artifacts from ``blessed/`` into the Python package.

Run from anywhere in the repository::

    python python/scripts/sync_data.py

For each calendar in ``blessed/manifest.json`` it writes:

``python/bdc_calendars/data/<ID>/holidays.csv``
    every non-weekend row of ``blessed/<ID>/events.csv`` (CLOSED, EARLY_CLOSE,
    NOTABLE, PERIOD_MARKER). Weekend rows are ~85% of the artifact and carry no
    information the weekend policy does not already hold, so they are dropped
    and rebuilt at query time.

``python/bdc_calendars/data/<ID>/metadata.json``
    the published ``metadata.json`` plus a ``weekend_policy`` block lifted from
    ``blessed/<ID>/resolved.yaml``.

It also writes ``data/manifest.json`` (release version, calendar index, alias
table) and ``bdc_calendars/_version.py``.

Before writing anything it *verifies* that reconstructing weekends from the
policy reproduces the generated WEEKEND rows exactly, and that weekend dates
and event dates are disjoint. A mismatch aborts the sync: the package must
never disagree with the published artifact.

This script has no third-party dependencies (it parses the one YAML block it
needs itself), so it runs on a bare Python in CI.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import os
import shutil
import sys
from typing import Dict, List, Optional, Tuple

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PACKAGE_DIR = os.path.join(REPO_ROOT, "python", "bdc_calendars")
DATA_DIR = os.path.join(PACKAGE_DIR, "data")
BLESSED_DIR = os.path.join(REPO_ROOT, "blessed")

DAY_NAMES = (
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
    "SUNDAY",
)
DAY_INDEX = {name: i for i, name in enumerate(DAY_NAMES)}

WEEKEND_TYPE = "WEEKEND"
CSV_HEADER = [
    "date",
    "type",
    "description",
    "key",
    "source_module",
    "observed_from",
    "close_time",
    "status",
]


# --- The one YAML block we need ---------------------------------------------


def parse_weekend_policy(resolved_yaml: str) -> Dict:
    """
    Extracts the ``weekend_policy`` block from a resolved calendar YAML.

    The emitter writes exactly one shape, so this parses that shape rather than
    pulling in a YAML dependency::

        weekend_policy:
          days:
          - SATURDAY
          - SUNDAY
          periods:
          - days:
            - SUNDAY
            to: 1952-05-30
          - days:
            - SATURDAY
            - SUNDAY
            from: 1933-07-29
            to: 1933-08-26

    Anything else raises, so a change in the emitter fails the sync loudly.
    """
    lines = resolved_yaml.splitlines()
    try:
        start = next(i for i, line in enumerate(lines) if line == "weekend_policy:")
    except StopIteration:
        return {"days": [], "periods": []}

    block: List[str] = []
    for line in lines[start + 1 :]:
        if line.strip() == "":
            continue
        if not line.startswith(" "):
            break
        block.append(line)

    days: List[str] = []
    periods: List[Dict] = []
    section: Optional[str] = None
    current: Optional[Dict] = None

    for line in block:
        if line == "  days:":
            section = "days"
        elif line == "  periods:":
            section = "periods"
        elif section == "days" and line.startswith("  - "):
            days.append(_day(line[4:]))
        elif section == "periods":
            if line.startswith("  - "):
                current = {"days": [], "from": None, "to": None}
                periods.append(current)
                _period_entry(current, line[4:])
            elif line.startswith("    - "):
                assert current is not None
                current["days"].append(_day(line[6:]))
            elif line.startswith("    "):
                assert current is not None
                _period_entry(current, line[4:])
            else:
                raise ValueError("Unparsable weekend_policy line: {!r}".format(line))
        else:
            raise ValueError("Unparsable weekend_policy line: {!r}".format(line))

    return {"days": days, "periods": periods}


def _period_entry(period: Dict, text: str) -> None:
    text = text.strip()
    if text == "days:":
        return
    key, _, value = text.partition(":")
    key = key.strip()
    value = value.strip()
    if key not in ("from", "to"):
        raise ValueError("Unexpected weekend period key: {!r}".format(key))
    period[key] = value or None


def _day(text: str) -> str:
    name = text.strip().lstrip("- ").strip().upper()
    if name not in DAY_INDEX:
        raise ValueError("Unknown weekday in weekend_policy: {!r}".format(text))
    return name


# --- Weekend reconstruction check -------------------------------------------


def weekend_days_on(policy: Dict, day: dt.date) -> set:
    """Mirrors ``WeekendPolicy.daysOn``: the last matching period wins."""
    periods = policy["periods"]
    if not periods:
        return {DAY_INDEX[d] for d in policy["days"]}
    for period in reversed(periods):
        start = _date(period.get("from"))
        end = _date(period.get("to"))
        if start is not None and day < start:
            continue
        if end is not None and day > end:
            continue
        return {DAY_INDEX[d] for d in period["days"]}
    return set()


def _date(value: Optional[str]) -> Optional[dt.date]:
    return dt.date.fromisoformat(value) if value else None


def verify_weekends(
    calendar_id: str,
    policy: Dict,
    rows: List[Dict[str, str]],
    range_start: dt.date,
    range_end: dt.date,
) -> None:
    """
    Fails loudly unless the policy lets the package answer exactly as the artifact does.

    The emitter writes a WEEKEND row on every weekend date *except* those where
    a closure already applies — a closure wins over the weekend row when both
    fall on the same day (Eid spanning a Saudi weekend, say), while a NOTABLE
    row sits alongside one. So the rule that makes reconstruction lossless is::

        WEEKEND row on D  <=>  D is a policy weekend and D carries no CLOSED row

    which is checked here exactly, in both directions. It also makes the two
    business-day tests identical: "no WEEKEND row and no CLOSED row" (the
    artifact's) and "not a policy weekend and no CLOSED row" (this package's).
    """
    problems = []

    published = {_date(r["date"]) for r in rows if r["type"] == WEEKEND_TYPE}
    closed = {_date(r["date"]) for r in rows if r["type"] == "CLOSED"}
    for kind, dates in (("WEEKEND", published), ("CLOSED", closed)):
        count = sum(1 for r in rows if r["type"] == kind)
        if count != len(dates):
            problems.append("{} dates carry more than one {} row".format(count - len(dates), kind))

    weekends = set()
    day = range_start
    one = dt.timedelta(days=1)
    while day <= range_end:
        if day.weekday() in weekend_days_on(policy, day):
            weekends.add(day)
        day += one

    expected = weekends - closed
    missing = sorted(expected - published)
    extra = sorted(published - expected)
    if missing:
        problems.append(
            "{} policy weekends have no WEEKEND row and no closure (e.g. {})".format(
                len(missing), missing[:5]
            )
        )
    if extra:
        problems.append(
            "{} WEEKEND rows the policy would not produce (e.g. {})".format(len(extra), extra[:5])
        )
    if problems:
        raise SystemExit(
            "{}: weekend policy does not match the published artifact:\n  - ".format(calendar_id)
            + "\n  - ".join(problems)
        )

    # The published descriptions must match what the package synthesizes.
    for row in rows:
        if row["type"] != WEEKEND_TYPE:
            continue
        day = _date(row["date"])
        expected = DAY_NAMES[day.weekday()].capitalize()
        if row["description"] != expected:
            raise SystemExit(
                "{}: WEEKEND row on {} says {!r}, package would synthesize {!r}".format(
                    calendar_id, day, row["description"], expected
                )
            )
        if (row["key"], row["source_module"], row["status"]) != (
            "weekend",
            "weekend_policy",
            "CONFIRMED",
        ):
            raise SystemExit(
                "{}: unexpected WEEKEND row shape on {}: {!r}".format(calendar_id, day, row)
            )


# --- Sync --------------------------------------------------------------------


def read_rows(path: str) -> List[Dict[str, str]]:
    with open(path, newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != CSV_HEADER:
            raise SystemExit(
                "{}: unexpected header {!r}, expected {!r}".format(
                    path, reader.fieldnames, CSV_HEADER
                )
            )
        return list(reader)


def sync_calendar(calendar_id: str) -> Tuple[int, int]:
    source = os.path.join(BLESSED_DIR, calendar_id)
    target = os.path.join(DATA_DIR, calendar_id)
    os.makedirs(target, exist_ok=True)

    with open(os.path.join(source, "metadata.json"), encoding="utf-8") as handle:
        metadata = json.load(handle)
    with open(os.path.join(source, "resolved.yaml"), encoding="utf-8") as handle:
        policy = parse_weekend_policy(handle.read())

    rows = read_rows(os.path.join(source, "events.csv"))
    verify_weekends(
        calendar_id,
        policy,
        rows,
        _date(metadata["range_start"]),
        _date(metadata["range_end"]),
    )

    kept = [r for r in rows if r["type"] != WEEKEND_TYPE]
    kept.sort(key=lambda r: (r["date"], r["type"], r["key"]))
    with open(os.path.join(target, "holidays.csv"), "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_HEADER, lineterminator="\n")
        writer.writeheader()
        writer.writerows(kept)

    metadata["weekend_policy"] = policy
    with open(os.path.join(target, "metadata.json"), "w", encoding="utf-8") as handle:
        json.dump(metadata, handle, indent=2, sort_keys=True)
        handle.write("\n")

    return len(rows), len(kept)


def write_manifest(blessed_manifest: Dict, calendars: List[str]) -> Dict:
    release = blessed_manifest["release_version"]
    # blessed/manifest.json's own "aliases" map (written by `tools manifest`, derived from every
    # calendar's metadata.json: mic plus any aliases) is the one source of truth for exchange-code
    # spellings; this package just republishes it rather than hand-maintaining a copy.
    aliases = blessed_manifest.get("aliases", {})
    manifest = {
        "schema_version": "1.0",
        "data_version": release["semantic"],
        "data_git_sha": release["git_sha"],
        "generation_date": release["generation_date"],
        "blessed_at": blessed_manifest.get("blessed_at"),
        "calendars": {
            cal: {
                "kind": blessed_manifest["calendars"][cal].get("kind", "market"),
                "range_start": blessed_manifest["calendars"][cal]["range_start"],
                "range_end": blessed_manifest["calendars"][cal]["range_end"],
            }
            for cal in calendars
        },
        "aliases": dict(sorted(aliases.items())),
    }
    with open(os.path.join(DATA_DIR, "manifest.json"), "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, sort_keys=True)
        handle.write("\n")
    return manifest


def write_version(manifest: Dict) -> str:
    major, minor, _patch = manifest["data_version"].split(".")
    version = "0.{}.{}".format(major, minor)
    contents = (
        '"""Generated by python/scripts/sync_data.py. Do not edit by hand."""\n'
        "\n"
        "#: Package version: 0.<data major>.<data minor>.\n"
        '__version__ = "{version}"\n'
        "\n"
        "#: Release version of the bundled calendar data.\n"
        'DATA_VERSION = "{data_version}"\n'
        "\n"
        "#: calendar-project commit the data was generated from.\n"
        'DATA_GIT_SHA = "{git_sha}"\n'
        "\n"
        "#: Date the data was generated.\n"
        'DATA_GENERATION_DATE = "{generation_date}"\n'
    ).format(
        version=version,
        data_version=manifest["data_version"],
        git_sha=manifest["data_git_sha"],
        generation_date=manifest["generation_date"],
    )
    with open(os.path.join(PACKAGE_DIR, "_version.py"), "w", encoding="utf-8") as handle:
        handle.write(contents)
    return version


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the bundled data matches blessed/ without rewriting it",
    )
    args = parser.parse_args(argv)

    with open(os.path.join(BLESSED_DIR, "manifest.json"), encoding="utf-8") as handle:
        blessed_manifest = json.load(handle)

    calendars = sorted(blessed_manifest["calendars"])
    for alias, target in blessed_manifest.get("aliases", {}).items():
        if target not in calendars:
            raise SystemExit("Alias {} points at unknown calendar {}".format(alias, target))

    if args.check:
        before = _snapshot()

    # Drop calendar directories that are no longer published.
    if os.path.isdir(DATA_DIR):
        for name in os.listdir(DATA_DIR):
            path = os.path.join(DATA_DIR, name)
            if os.path.isdir(path) and name not in calendars:
                shutil.rmtree(path)
    os.makedirs(DATA_DIR, exist_ok=True)

    total_kept = 0
    for calendar_id in calendars:
        published, kept = sync_calendar(calendar_id)
        total_kept += kept
        print(
            "  {:<24} {:>6} published rows -> {:>5} non-weekend rows".format(
                calendar_id, published, kept
            )
        )

    manifest = write_manifest(blessed_manifest, calendars)
    version = write_version(manifest)
    print(
        "Synced {} calendars, {} rows; data v{} (sha {}), package v{}".format(
            len(calendars),
            total_kept,
            manifest["data_version"],
            manifest["data_git_sha"][:7],
            version,
        )
    )

    if args.check:
        after = _snapshot()
        if before != after:
            changed = sorted(set(before) ^ set(after)) or sorted(
                k for k in after if before.get(k) != after[k]
            )
            print(
                "Bundled data is out of date with blessed/; "
                "run python/scripts/sync_data.py and commit:\n  "
                + "\n  ".join(changed),
                file=sys.stderr,
            )
            return 1
        print("Bundled data is up to date with blessed/.")
    return 0


def _snapshot() -> Dict[str, bytes]:
    snapshot: Dict[str, bytes] = {}
    for root, _dirs, names in os.walk(DATA_DIR):
        for name in names:
            path = os.path.join(root, name)
            with open(path, "rb") as handle:
                snapshot[os.path.relpath(path, DATA_DIR)] = handle.read()
    version_file = os.path.join(PACKAGE_DIR, "_version.py")
    if os.path.exists(version_file):
        with open(version_file, "rb") as handle:
            snapshot["_version.py"] = handle.read()
    return snapshot


if __name__ == "__main__":
    sys.exit(main())
