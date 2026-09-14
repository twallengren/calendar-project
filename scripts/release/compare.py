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
import hashlib
import os
import sys
from typing import Any, Dict, Iterable, Mapping, Sequence, Tuple

SEVERITIES = {"NONE": 0, "PATCH": 1, "MINOR": 2, "MAJOR": 3}
EXIT_CODES = {"NONE": 0, "PATCH": 1, "MINOR": 1, "MAJOR": 2}
IDENTITY_FIELDS = ("calendar_id", "kind", "timezone", "mic")
DESCRIPTIVE_FIELDS = ("calendar_name", "description")
SEMANTIC_DETAIL_FIELDS = (
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


def _project_rows(
    rows: collections.Counter,
    source_fields: Tuple[str, ...],
    projected_fields: Tuple[str, ...],
) -> collections.Counter:
    indexes = tuple(source_fields.index(field) for field in projected_fields)
    result: collections.Counter = collections.Counter()
    for record, count in rows.items():
        result[tuple(record[index] for index in indexes)] += count
    return result


def _record_entries(
    rows: collections.Counter, fields: Tuple[str, ...]
) -> list[Dict[str, Any]]:
    return [
        {
            "count": rows[record],
            "record": dict(zip(fields, record)),
        }
        for record in sorted(rows)
        if rows[record]
    ]


def _allocate_projected_delta(
    rows: collections.Counter,
    source_fields: Tuple[str, ...],
    projected_fields: Tuple[str, ...],
    projected_delta: collections.Counter,
) -> collections.Counter:
    """Select complete side-specific records for an already-computed projected delta."""
    remaining = projected_delta.copy()
    indexes = tuple(source_fields.index(field) for field in projected_fields)
    result: collections.Counter = collections.Counter()
    for record in sorted(rows):
        projected = tuple(record[index] for index in indexes)
        count = min(rows[record], remaining[projected])
        if count:
            result[record] += count
            remaining[projected] -= count
    if sum(remaining.values()):
        raise AssertionError("failed to allocate projected record delta")
    return result


def _change_block(
    old_rows: collections.Counter,
    old_fields: Tuple[str, ...],
    new_rows: collections.Counter,
    new_fields: Tuple[str, ...],
    compared_fields: Tuple[str, ...] | None,
) -> Dict[str, Any]:
    if compared_fields is None:
        old_only = old_rows
        new_only = new_rows
    else:
        old_projected = _project_rows(old_rows, old_fields, compared_fields)
        new_projected = _project_rows(new_rows, new_fields, compared_fields)
        old_only = _allocate_projected_delta(
            old_rows, old_fields, compared_fields, old_projected - new_projected
        )
        new_only = _allocate_projected_delta(
            new_rows, new_fields, compared_fields, new_projected - old_projected
        )

    old_entries = _record_entries(old_only, old_fields)
    new_entries = _record_entries(new_only, new_fields)
    return {
        "old_only_count": sum(item["count"] for item in old_entries),
        "new_only_count": sum(item["count"] for item in new_entries),
        "old_only_by_type": _counts_by_type(old_entries),
        "new_only_by_type": _counts_by_type(new_entries),
        "old_only": old_entries,
        "new_only": new_entries,
    }


def _counts_by_type(entries: list[Dict[str, Any]]) -> Dict[str, int]:
    counts: collections.Counter = collections.Counter()
    for item in entries:
        event_type = item["record"].get("type", "")
        counts[event_type] += item["count"]
    return dict(sorted(counts.items()))


def _has_record_changes(block: Mapping[str, Any]) -> bool:
    return bool(block["old_only_count"] or block["new_only_count"])


def _effective_metadata_value(
    metadata: Mapping[str, Any], manifest_entry: Mapping[str, Any], field: str
) -> Any:
    """Read identity from metadata, falling back to its same-release manifest entry."""
    value = metadata.get(field)
    return value if value is not None else manifest_entry.get(field)


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


def _event_details(value: Any, start: str, end: str) -> collections.Counter:
    result: collections.Counter = collections.Counter()
    if not isinstance(value, list):
        return result
    for item in value:
        if not isinstance(item, dict):
            raise ValueError("event_details entries must be objects")
        date = item.get("date") or item.get("iso_date")
        if date is not None and not (start <= str(date) <= end):
            continue
        result[json.dumps(item, sort_keys=True, separators=(",", ":"))] += 1
    return result


def _flatten(value: Any, prefix: str = "") -> Dict[str, Any]:
    if not isinstance(value, dict):
        return {prefix: value}
    result = {}
    for key in sorted(value):
        path = "{}.{}".format(prefix, key) if prefix else key
        result.update(_flatten(value[key], path))
    return result


def _quality_rank(value: Any) -> int | None:
    if not isinstance(value, str):
        return None
    return {
        "INCOMPLETE": 0,
        "UNKNOWN": 0,
        "PROJECTED": 1,
        "COMPLETE": 2,
        "VERIFIED": 2,
        "CONFIRMED": 2,
    }.get(value.upper())


def _compare_coverage(old: Mapping[str, Any], new: Mapping[str, Any]) -> Tuple[str, list]:
    severity = "NONE"
    reasons = []
    if old and not new:
        return "MAJOR", ["explicit coverage metadata removed"]
    old_verified = old.get("verified_through")
    new_verified = new.get("verified_through")
    if old_verified and (not new_verified or new_verified < old_verified):
        severity = _raise(severity, "MAJOR")
        reasons.append("verified coverage contracted")
    elif new_verified and (not old_verified or new_verified > old_verified):
        severity = _raise(severity, "MINOR")
        reasons.append("verified coverage extended")

    ignored = {"from", "to", "verified_through"}
    old_details = _flatten({key: value for key, value in old.items() if key not in ignored})
    new_details = _flatten({key: value for key, value in new.items() if key not in ignored})
    for key in sorted(set(old_details) | set(new_details)):
        before = old_details.get(key)
        after = new_details.get(key)
        if before == after:
            continue
        before_rank = _quality_rank(before)
        after_rank = _quality_rank(after)
        if after_rank == 0 or (before_rank is not None and (after_rank is None or after_rank < before_rank)):
            severity = _raise(severity, "MAJOR")
            reasons.append("coverage quality contracted: {}".format(key))
        elif before is None:
            severity = _raise(severity, "MINOR")
            reasons.append("coverage metadata added: {}".format(key))
        else:
            severity = _raise(severity, "MAJOR")
            reasons.append("coverage semantics changed: {}".format(key))
    if not old and new and not reasons:
        severity = _raise(severity, "MINOR")
        reasons.append("explicit coverage metadata added")
    return severity, reasons


def _source_snapshot(root: str | None) -> Dict[str, str]:
    result = {}
    if not root or not os.path.isdir(root):
        return result
    for directory, names, files in os.walk(root):
        names.sort()
        for name in sorted(files):
            path = os.path.join(directory, name)
            relative = os.path.relpath(path, root).replace(os.sep, "/")
            with open(path, "rb") as handle:
                raw = handle.read()
            if name.endswith((".md", ".txt", ".json", ".yaml", ".yml", ".csv")):
                try:
                    text = raw.decode("utf-8").replace("\r\n", "\n")
                except UnicodeDecodeError:
                    # Authoritative originals may intentionally retain their
                    # published encoding (for example, HKO Big5 tables).
                    # Their semantic identity is therefore their exact bytes.
                    pass
                else:
                    raw = ("\n".join(line.rstrip() for line in text.splitlines()) + "\n").encode()
            result[relative] = hashlib.sha256(raw).hexdigest()
    return result


def compare(
    baseline_root: str,
    candidate_root: str,
    baseline_sources: str | None = None,
    candidate_sources: str | None = None,
) -> Dict[str, Any]:
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
        old_entry = old_calendars.get(calendar_id)
        new_entry = new_calendars.get(calendar_id)
        old_meta = _metadata(baseline_root, calendar_id) if old_entry is not None else {}
        new_meta = _metadata(candidate_root, calendar_id) if new_entry is not None else {}
        old_range = _range(old_entry, old_meta) if old_entry is not None else None
        new_range = _range(new_entry, new_meta) if new_entry is not None else None
        old_fields = _header(baseline_root, calendar_id) if old_entry is not None else ()
        new_fields = _header(candidate_root, calendar_id) if new_entry is not None else ()
        old_rows = (
            _rows(baseline_root, calendar_id, old_range[0], old_range[1], old_fields)
            if old_range is not None
            else collections.Counter()
        )
        new_rows = (
            _rows(candidate_root, calendar_id, new_range[0], new_range[1], new_fields)
            if new_range is not None
            else collections.Counter()
        )
        record_changes: Dict[str, Any] = {
            "compared_fields": [],
            "schema_changes": {"removed_fields": [], "added_fields": []},
        }
        if calendar_id not in new_calendars:
            severity = "MAJOR"
            reasons.append("calendar removed")
            record_changes["outside_overlap"] = _change_block(
                old_rows, old_fields, collections.Counter(), (), None
            )
        elif calendar_id not in old_calendars:
            severity = "MINOR"
            reasons.append("calendar added")
            record_changes["outside_overlap"] = _change_block(
                collections.Counter(), (), new_rows, new_fields, None
            )
        else:
            old_start, old_end = old_range
            new_start, new_end = new_range

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
            missing_fields = set(old_fields) - set(new_fields)
            added_fields = set(new_fields) - set(old_fields)
            record_changes["schema_changes"] = {
                "removed_fields": sorted(missing_fields),
                "added_fields": sorted(added_fields),
            }
            if missing_fields or added_fields:
                record_changes["comparison_note"] = (
                    "Record deltas are computed only on compared_fields. Within groups whose "
                    "records are indistinguishable on those fields, side-specific complete-record "
                    "allocations are deterministic representatives, not inferred modification pairs."
                )
            if missing_fields:
                severity = _raise(severity, "MAJOR")
                reasons.append("published CSV fields removed: {}".format(sorted(missing_fields)))
            if added_fields:
                severity = _raise(severity, "MINOR")
                reasons.append("published CSV fields added: {}".format(sorted(added_fields)))
            if overlap_start <= overlap_end:
                comparable_fields = tuple(field for field in old_fields if field in new_fields)
                record_changes["compared_fields"] = list(comparable_fields)
                old_overlap_rows = _rows(
                    baseline_root, calendar_id, overlap_start, overlap_end, old_fields
                )
                new_overlap_rows = _rows(
                    candidate_root, calendar_id, overlap_start, overlap_end, new_fields
                )
                within_overlap = _change_block(
                    old_overlap_rows,
                    old_fields,
                    new_overlap_rows,
                    new_fields,
                    comparable_fields,
                )
                record_changes["within_overlap"] = {
                    "from": overlap_start,
                    "to": overlap_end,
                    **within_overlap,
                }
                if _has_record_changes(within_overlap):
                    severity = _raise(severity, "MAJOR")
                    removed = within_overlap["old_only_count"]
                    added = within_overlap["new_only_count"]
                    reasons.append(
                        "published records changed inside existing coverage "
                        "({} removed, {} added)".format(removed, added)
                    )

                outside_overlap = _change_block(
                    old_rows - old_overlap_rows,
                    old_fields,
                    new_rows - new_overlap_rows,
                    new_fields,
                    None,
                )
                if _has_record_changes(outside_overlap):
                    record_changes["outside_overlap"] = outside_overlap

                old_details = _event_details(old_meta.get("event_details"), overlap_start, overlap_end)
                new_details = _event_details(new_meta.get("event_details"), overlap_start, overlap_end)
                if "event_details" not in old_meta and "event_details" in new_meta:
                    severity = _raise(severity, "MINOR")
                    reasons.append("published event provenance added")
                elif "event_details" in old_meta and "event_details" not in new_meta:
                    severity = _raise(severity, "MAJOR")
                    reasons.append("published event provenance removed")
                elif old_details != new_details:
                    severity = _raise(severity, "MAJOR")
                    reasons.append("published event provenance changed inside existing coverage")

            else:
                record_changes["outside_overlap"] = _change_block(
                    old_rows, old_fields, new_rows, new_fields, None
                )

            for field in IDENTITY_FIELDS:
                before = _effective_metadata_value(old_meta, old_entry, field)
                after = _effective_metadata_value(new_meta, new_entry, field)
                if before is None and after is not None:
                    severity = _raise(severity, "MINOR")
                    reasons.append("identity metadata added: {}".format(field))
                elif before != after:
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
            coverage_severity, coverage_reasons = _compare_coverage(
                old_meta.get("coverage") or {}, new_meta.get("coverage") or {}
            )
            severity = _raise(severity, coverage_severity)
            reasons.extend(coverage_reasons)

        has_schema_changes = any(record_changes["schema_changes"].values())
        has_row_changes = any(
            _has_record_changes(record_changes[key])
            for key in ("within_overlap", "outside_overlap")
            if key in record_changes
        )
        reports[calendar_id] = {"severity": severity, "reasons": reasons}
        if has_schema_changes or has_row_changes:
            reports[calendar_id]["record_changes"] = record_changes
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

    source_reasons = []
    baseline_sources_available = bool(baseline_sources and os.path.isdir(baseline_sources))
    candidate_sources_available = bool(candidate_sources and os.path.isdir(candidate_sources))
    if not baseline_sources_available:
        source_reasons.append("baseline source evidence unavailable")
    if not candidate_sources_available:
        source_reasons.append("candidate source evidence unavailable")
    if (
        baseline_sources_available
        and candidate_sources_available
        and _source_snapshot(baseline_sources) != _source_snapshot(candidate_sources)
    ):
        overall = _raise(overall, "PATCH")
        source_reasons.append("canonical source documentation changed")

    return {
        "schema_version": "1.0",
        "severity": overall,
        "baseline_data_version": baseline.get("release_version", {}).get("semantic"),
        "candidate_data_version": candidate.get("release_version", {}).get("semantic"),
        "calendars": reports,
        "aliases": alias_reasons,
        "sources": source_reasons,
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--output")
    parser.add_argument("--baseline-sources")
    parser.add_argument("--candidate-sources")
    args = parser.parse_args(argv)
    try:
        report = compare(
            args.baseline, args.candidate, args.baseline_sources, args.candidate_sources
        )
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
