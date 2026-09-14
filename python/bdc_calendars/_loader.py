"""
Loading of the bundled calendar data.

The data ships inside the wheel under ``bdc_calendars/data``:

``manifest.json``
    the release version, the calendar index and the exchange-code alias table

``<CALENDAR_ID>/metadata.json``
    the calendar's metadata as published in ``blessed/``, plus the resolved
    ``weekend_policy`` block

``<CALENDAR_ID>/holidays.csv``
    every non-weekend event row (CLOSED, EARLY_CLOSE, NOTABLE); weekend rows
    are reconstructed from ``weekend_policy``
"""

from __future__ import annotations

import csv
import datetime as _dt
import json
from typing import Any, Dict, List, Mapping, NamedTuple, Optional, Tuple

from ._weekend import WeekendPolicy
from .errors import CalendarNotFoundError
from .trust import CoverageInterval, EventDetails, NativeDate
from collections import defaultdict, deque

try:  # Python >= 3.9
    from importlib.resources import files as _files
except ImportError:  # pragma: no cover - Python 3.8 and older are unsupported
    _files = None  # type: ignore[assignment]

__all__ = ["Event", "manifest", "resolve_id", "calendar_ids", "load_calendar"]


class Event(NamedTuple):
    """One calendar event: a closure, an early close, a notable day or a weekend."""

    date: _dt.date
    """The ISO (Gregorian) civil date, in the market's own timezone."""

    type: str
    """``CLOSED``, ``EARLY_CLOSE``, ``NOTABLE``, ``PERIOD_MARKER`` or ``WEEKEND``."""

    description: str
    """Human-readable name, e.g. ``Christmas Day``."""

    key: Optional[str] = None
    """The event-source key it was generated from."""

    source_module: Optional[str] = None
    """The module (or ``weekend_policy``) that contributed the event."""

    observed_from: Optional[_dt.date] = None
    """The unshifted date, when an observance rule moved the closure."""

    close_time: Optional[_dt.time] = None
    """Local close time of a shortened session (``EARLY_CLOSE`` only)."""

    status: str = "CONFIRMED"
    """``CONFIRMED`` or ``PROJECTED`` — how the row itself was derived."""

_manifest_cache: Optional[Dict[str, Any]] = None
_calendar_cache: Dict[str, "CalendarData"] = {}


def _read_text(*parts: str) -> str:
    # Anchored on the package itself rather than on ``bdc_calendars.data``:
    # the data directory carries no ``__init__.py``, and treating it as a
    # (namespace) package of its own is not portable back to Python 3.9.
    resource = _files(__package__) / "data"
    for part in parts:
        resource = resource / part
    return resource.read_text(encoding="utf-8")


def manifest() -> Mapping[str, Any]:
    """The bundled ``manifest.json``, read once and cached."""
    global _manifest_cache
    if _manifest_cache is None:
        _manifest_cache = json.loads(_read_text("manifest.json"))
    return _manifest_cache


def calendar_ids() -> List[str]:
    """Every bundled calendar id, sorted."""
    return sorted(manifest()["calendars"])


def aliases() -> Mapping[str, str]:
    """The alias table (exchange_calendars MICs and other spellings)."""
    return manifest().get("aliases", {})


def resolve_id(calendar_id: str) -> str:
    """
    Maps a user-supplied id onto a bundled calendar id.

    Accepts the canonical ids (``US-NYSE``), the exchange_calendars MIC aliases
    (``XNYS``) and any case/underscore spelling of either.
    """
    if not isinstance(calendar_id, str) or not calendar_id.strip():
        raise CalendarNotFoundError(str(calendar_id), calendar_ids())
    raw = calendar_id.strip()
    known = manifest()["calendars"]
    alias_table = aliases()
    for candidate in _spellings(raw):
        if candidate in known:
            return candidate
        if candidate in alias_table:
            return alias_table[candidate]
    raise CalendarNotFoundError(raw, calendar_ids() + sorted(alias_table))


def _spellings(raw: str) -> Tuple[str, ...]:
    upper = raw.upper()
    return (raw, upper, upper.replace("_", "-"), upper.replace("-", "_"))


class CalendarData:
    """The parsed contents of one bundled calendar directory."""

    __slots__ = (
        "calendar_id",
        "metadata",
        "events_by_date",
        "details_by_date",
        "weekend_policy",
        "range_from",
        "range_to",
        "verified_through",
        "coverage_intervals",
        "timezone",
        "name",
        "kind",
    )

    def __init__(self, calendar_id: str, metadata: Mapping[str, Any], rows: List[Event]) -> None:
        self.calendar_id = calendar_id
        self.metadata = dict(metadata)
        self.events_by_date: Dict[_dt.date, List[Event]] = {}
        for event in rows:
            self.events_by_date.setdefault(event.date, []).append(event)
        self.details_by_date = {}
        if "event_details" in metadata:
            remaining = defaultdict(deque)
            for detail in metadata["event_details"]:
                remaining[_event_from_row(detail)].append(detail)
            for event in rows:
                if not remaining[event]:
                    raise ValueError("Missing event provenance occurrence: {!r}".format(event))
                detail = remaining[event].popleft()
                native = detail.get("nominal_native_date")
                value = EventDetails(event, event.status, event.status, list(detail.get("evidence_ids", [])),
                    NativeDate(**native) if native else None, detail.get("chronology_profile"),
                    detail.get("chronology_provider"), [_date(x) for x in detail.get("observation_lineage", [])])
                self.details_by_date.setdefault(event.date, []).append(value)
            if any(remaining.values()):
                raise ValueError("Event provenance contains extra occurrences")
        self.weekend_policy = WeekendPolicy.from_json(metadata.get("weekend_policy"))
        self.range_from = _date(metadata["range_start"])
        self.range_to = _date(metadata["range_end"])
        coverage = metadata.get("coverage")
        if coverage is None:
            coverage = {}
        if not isinstance(coverage, dict):
            raise ValueError("coverage must be an object")
        verified = coverage.get("verified_through")
        self.verified_through = _date(verified) if verified else None
        self.coverage_intervals = tuple(_coverage_intervals(coverage.get("quality")))
        self.timezone = metadata.get("timezone")
        self.name = metadata.get("calendar_name", calendar_id)
        self.kind = metadata.get("kind", "market")


def load_calendar(calendar_id: str) -> CalendarData:
    """Loads (and caches) the data for one calendar; ``calendar_id`` must be canonical."""
    cached = _calendar_cache.get(calendar_id)
    if cached is not None:
        return cached
    metadata = json.loads(_read_text(calendar_id, "metadata.json"))
    rows = _parse_events(_read_text(calendar_id, "holidays.csv"))
    data = CalendarData(calendar_id, metadata, rows)
    _calendar_cache[calendar_id] = data
    return data


def _parse_events(text: str) -> List[Event]:
    reader = csv.DictReader(text.splitlines())
    events: List[Event] = []
    for row in reader:
        events.append(_event_from_row(row))
    events.sort(key=lambda e: e.date)
    return events


def _event_from_row(row) -> Event:
    return Event(date=_date(row["date"]), type=row["type"], description=row.get("description") or "",
        key=row.get("key") or None, source_module=row.get("source_module") or None,
        observed_from=_date(row["observed_from"]) if row.get("observed_from") else None,
        close_time=_time(row["close_time"]) if row.get("close_time") else None,
        status=row.get("status") or "CONFIRMED")


def _date(value: str) -> _dt.date:
    return _dt.date.fromisoformat(value)


def _time(value: str) -> _dt.time:
    return _dt.time.fromisoformat(value)


def _coverage_intervals(rows: Any) -> List[CoverageInterval]:
    if rows is None:
        return []
    if not isinstance(rows, list):
        raise ValueError("coverage.quality must be an array")
    result = []
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("coverage.quality entries must be objects")
        evidence = row.get("evidence_ids", row.get("evidenceIds", []))
        if not isinstance(evidence, list):
            raise ValueError("coverage evidence_ids must be an array")
        result.append(
            CoverageInterval(
                scope=row["scope"],
                start=_date(row["from"]),
                end=_date(row["to"]),
                quality=row["quality"],
                evidence_ids=list(evidence),
            )
        )
    return result
