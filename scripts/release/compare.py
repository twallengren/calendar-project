#!/usr/bin/env python3
"""Compare a release candidate with an immutable published dataset.

This comparator is deliberately separate from ``tools ci-diff``.  ``ci-diff``
answers whether the specs reproduce the working tree.  This script answers the
release question: how does a fully generated candidate differ from the last
published release?
"""

from __future__ import annotations

import argparse
import collections
import csv
import datetime as dt
import json
import os
import sys
from typing import Any, Dict, Iterable, Mapping, Sequence, Tuple

SEVERITIES = {"NONE": 0, "PATCH": 1, "MINOR": 2, "MAJOR": 3}
EXIT_CODES = {"NONE": 0, "PATCH": 1, "MINOR": 1, "MAJOR": 2}
IDENTITY_FIELDS = ("calendar_id", "kind", "timezone", "mic")
DESCRIPTIVE_FIELDS = ("calendar_name", "description")
SEMANTIC_DETAIL_FIELDS = (
    "event_details",
    "chronology",
    "chronology_profile",
    "native_profile",
    "provider",
)
CSV_FIELDS = (
    "date",
    "type",
    "description",
    "key",
    "source_module",
    "observed_from",
    "close_time",
    "status",
)


def _load_json(path: str) -> Dict[str, Any]:
    with open(path, encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError("{} must contain a JSON object".format(path))
    return value


def _manifest(root: str) -> Dict[str, Any]:
    return _load_json(os.path.join(root, "manifest.json"))


def _metadata(root: str, calendar_id: str) -> Dict[str, Any]:
    return _load_json(os.path.join(root, calendar_id, "metadata.json"))


def _header(root: str, calendar_id: str) -> Tuple[str, ...]:
    path = os.path.join(root, calendar_id, "events.csv")
    with open(path, newline="", encoding="utf-8") as handle:
        fields = csv.DictReader(handle).fieldnames
    if not fields or not set(("date", "type", "description")).issubset(fields):
        raise ValueError("{} has unsupported header {}".format(path, fields))
    unknown = set(fields) - set(CSV_FIELDS)
    if unknown:
        raise ValueError("{} has unknown published fields {}".format(path, sorted(unknown)))
    return tuple(fields)


def _rows(
    root: str, calendar_id: str, start: str, end: str, fields: Tuple[str, ...]
) -> collections.Counter:
    path = os.path.join(root, calendar_id, "events.csv")
    result: collections.Counter = collections.Counter()
    with open(path, newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None or not set(fields).issubset(reader.fieldnames):
            raise ValueError("{} does not contain fields {}".format(path, list(fields)))
        for row in reader:
            date = row["date"]
            if start <= date <= end:
                # A tuple of every published field preserves close time, status,
                # provenance and duplicate multiplicity.
                result[tuple(row[field] for field in fields)] += 1
    return result


def _range(entry: Mapping[str, Any], metadata: Mapping[str, Any]) -> Tuple[str, str]:
    start = str(entry.get("range_start") or metadata.get("range_start") or "")
    end = str(entry.get("range_end") or metadata.get("range_end") or "")
    try:
        dt.date.fromisoformat(start)
        dt.date.fromisoformat(end)
    except ValueError as error:
        raise ValueError("invalid published range {}..{}".format(start, end)) from error
    if start > end:
        raise ValueError("published range starts after it ends: {}..{}".format(start, end))
    return start, end


def _raise(current: str, proposed: str) -> str:
    return proposed if SEVERITIES[proposed] > SEVERITIES[current] else current


def compare(baseline_root: str, candidate_root: str) -> Dict[str, Any]:
    baseline = _manifest(baseline_root)
    candidate = _manifest(candidate_root)
    old_calendars = baseline.get("calendars", {})
    new_calendars = candidate.get("calendars", {})
    if not isinstance(old_calendars, dict) or not isinstance(new_calendars, dict):
        raise ValueError("manifest calendars must be objects")

    overall = "NONE"
    reports: Dict[str, Any] = {}
    for calendar_id in sorted(set(old_calendars) | set(new_calendars)):
        reasons = []
        severity = "NONE"
        if calendar_id not in new_calendars:
            severity = "MAJOR"
            reasons.append("calendar removed")
        elif calendar_id not in old_calendars:
            severity = "MINOR"
            reasons.append("calendar added")
        else:
            old_meta = _metadata(baseline_root, calendar_id)
            new_meta = _metadata(candidate_root, calendar_id)
            old_start, old_end = _range(old_calendars[calendar_id], old_meta)
            new_start, new_end = _range(new_calendars[calendar_id], new_meta)

            if new_start > old_start or new_end < old_end:
                severity = _raise(severity, "MAJOR")
                reasons.append(
                    "coverage contracted from {}..{} to {}..{}".format(
                        old_start, old_end, new_start, new_end
                    )
                )
            elif new_start < old_start or new_end > old_end:
                severity = _raise(severity, "MINOR")
                reasons.append(
                    "coverage extended from {}..{} to {}..{}".format(
                        old_start, old_end, new_start, new_end
                    )
                )

            overlap_start = max(old_start, new_start)
            overlap_end = min(old_end, new_end)
            old_fields = _header(baseline_root, calendar_id)
            new_fields = _header(candidate_root, calendar_id)
            missing_fields = set(old_fields) - set(new_fields)
            added_fields = set(new_fields) - set(old_fields)
            if missing_fields:
                severity = _raise(severity, "MAJOR")
                reasons.append("published CSV fields removed: {}".format(sorted(missing_fields)))
            if added_fields:
                severity = _raise(severity, "MINOR")
                reasons.append("published CSV fields added: {}".format(sorted(added_fields)))
            if overlap_start <= overlap_end:
                comparable_fields = tuple(field for field in old_fields if field in new_fields)
                old_rows = _rows(
                    baseline_root, calendar_id, overlap_start, overlap_end, comparable_fields
                )
                new_rows = _rows(
                    candidate_root, calendar_id, overlap_start, overlap_end, comparable_fields
                )
                if old_rows != new_rows:
                    severity = _raise(severity, "MAJOR")
                    removed = sum((old_rows - new_rows).values())
                    added = sum((new_rows - old_rows).values())
                    reasons.append(
                        "published records changed inside existing coverage "
                        "({} removed, {} added)".format(removed, added)
                    )

            for field in IDENTITY_FIELDS:
                if field not in old_meta and field in new_meta:
                    severity = _raise(severity, "MINOR")
                    reasons.append("identity metadata added: {}".format(field))
                elif field in old_meta and old_meta.get(field) != new_meta.get(field):
                    severity = _raise(severity, "MAJOR")
                    reasons.append("identity metadata changed: {}".format(field))
            old_aliases = set(old_meta.get("aliases") or [])
            new_aliases = set(new_meta.get("aliases") or [])
            if old_aliases - new_aliases:
                severity = _raise(severity, "MAJOR")
                reasons.append("calendar aliases removed")
            if new_aliases - old_aliases:
                severity = _raise(severity, "MINOR")
                reasons.append("calendar aliases added")
            for field in DESCRIPTIVE_FIELDS:
                if old_meta.get(field) != new_meta.get(field):
                    severity = _raise(severity, "PATCH")
                    reasons.append("descriptive metadata changed: {}".format(field))
            for field in SEMANTIC_DETAIL_FIELDS:
                if field not in old_meta and field in new_meta:
                    severity = _raise(severity, "MINOR")
                    reasons.append("published semantic metadata added: {}".format(field))
                elif field in old_meta and field not in new_meta:
                    severity = _raise(severity, "MAJOR")
                    reasons.append("published semantic metadata removed: {}".format(field))
                elif old_meta.get(field) != new_meta.get(field):
                    severity = _raise(severity, "MAJOR")
                    reasons.append("published semantic metadata changed: {}".format(field))
            old_coverage = old_meta.get("coverage") or {}
            new_coverage = new_meta.get("coverage") or {}
            if not old_coverage and new_coverage:
                severity = _raise(severity, "MINOR")
                reasons.append("explicit coverage metadata added")
            elif old_coverage and not new_coverage:
                severity = _raise(severity, "MAJOR")
                reasons.append("explicit coverage metadata removed")
            elif old_coverage != new_coverage:
                old_verified = old_coverage.get("verified_through")
                new_verified = new_coverage.get("verified_through")
                if old_verified and (not new_verified or new_verified < old_verified):
                    severity = _raise(severity, "MAJOR")
                    reasons.append("verified coverage contracted")
                elif new_verified and (not old_verified or new_verified > old_verified):
                    severity = _raise(severity, "MINOR")
                    reasons.append("verified coverage extended")
                else:
                    severity = _raise(severity, "MAJOR")
                    reasons.append("coverage semantics changed")

        reports[calendar_id] = {"severity": severity, "reasons": reasons}
        overall = _raise(overall, severity)

    old_aliases = baseline.get("aliases", {}) or {}
    new_aliases = candidate.get("aliases", {}) or {}
    alias_reasons = []
    for alias in sorted(set(old_aliases) | set(new_aliases)):
        if alias not in new_aliases or (
            alias in old_aliases and old_aliases[alias] != new_aliases[alias]
        ):
            overall = _raise(overall, "MAJOR")
            alias_reasons.append("alias {} removed or retargeted".format(alias))
        elif alias not in old_aliases:
            overall = _raise(overall, "MINOR")
            alias_reasons.append("alias {} added".format(alias))

    return {
        "schema_version": "1.0",
        "severity": overall,
        "baseline_data_version": baseline.get("release_version", {}).get("semantic"),
        "candidate_data_version": candidate.get("release_version", {}).get("semantic"),
        "calendars": reports,
        "aliases": alias_reasons,
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--output")
    args = parser.parse_args(argv)
    try:
        report = compare(args.baseline, args.candidate)
    except (OSError, ValueError, json.JSONDecodeError, csv.Error) as error:
        print("release comparison failed: {}".format(error), file=sys.stderr)
        return 3
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        with open(args.output, "w", encoding="utf-8") as handle:
            handle.write(rendered)
    else:
        sys.stdout.write(rendered)
    return EXIT_CODES[report["severity"]]


if __name__ == "__main__":
    sys.exit(main())
