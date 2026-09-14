"""
Stdio MCP server exposing the ``bdc-calendars`` Query API to AI agents.

Every tool below is a thin wrapper over :mod:`bdc_calendars` (the same package
described in ``python/README.md`` and, ultimately, the ``Query API`` section
of ``spec/SPEC.md``): the data is offline, deterministic and reproducible for
a given package release. Calendar ids accept both native ids (``US-NYSE``)
and exchange_calendars MIC aliases (``XNYS``); dates are ISO ``YYYY-MM-DD``
strings.

Every result is a JSON-serialisable ``dict`` that carries ``calendar_id`` and
``data_version``, plus ``status``/``verified_through`` where a specific date
is involved. Errors (an unknown calendar id, a date outside the calendar's
covered range, a malformed date) are never raised across the MCP boundary:
they come back as ``{"error": "<ExceptionType>", "message": "..."}`` so an
agent can recover — for example by calling :func:`list_calendars` to find a
valid id, or :func:`status` to check a date before asking about it.

The ``mcp`` SDK is imported lazily (inside :func:`create_server`, called from
:func:`main`) so that importing :mod:`bdc_calendars` itself never requires it;
only running this server does, via the ``mcp`` extra:
``pip install "bdc-calendars[mcp]"``.
"""

from __future__ import annotations

import datetime as _dt
from typing import Any, Dict, List, Optional

import bdc_calendars as bdc
from bdc_calendars._loader import Event
from bdc_calendars._weekend import DAY_NAMES, WeekendPolicy

__all__ = ["create_server", "main"]

# Exceptions the underlying API raises for bad input; every tool catches these
# (and only these) and turns them into a structured error result instead of
# letting them propagate.
_QUERY_ERRORS = (bdc.CalendarNotFoundError, bdc.OutsideCoverageError, ValueError, TypeError)


# --- Serialisation helpers ----------------------------------------------------


def _error(exc: Exception) -> Dict[str, str]:
    return {"error": type(exc).__name__, "message": str(exc)}


def _iso(value: Optional[_dt.date]) -> Optional[str]:
    return value.isoformat() if value is not None else None


def _parse_date(value: Any, argument: str = "date") -> _dt.date:
    if not isinstance(value, str):
        raise TypeError(
            "{} must be an ISO date string (YYYY-MM-DD), got {!r}".format(argument, value)
        )
    try:
        return _dt.date.fromisoformat(value)
    except ValueError as exc:
        raise ValueError(
            "{} is not a valid ISO date (YYYY-MM-DD): {!r}".format(argument, value)
        ) from exc


def _event_dict(event: Event) -> Dict[str, Any]:
    return {
        "date": _iso(event.date),
        "type": event.type,
        "description": event.description,
        "close_time": event.close_time.isoformat() if event.close_time else None,
        "status": event.status,
        "key": event.key,
        "source_module": event.source_module,
        "observed_from": _iso(event.observed_from),
    }


def _assessment_dict(assessment: bdc.DayAssessment) -> Dict[str, Any]:
    return {
        "date": _iso(assessment.date),
        "state": assessment.state,
        "scheduled_state": assessment.scheduled_state,
        "effective_confidence": assessment.effective_confidence,
        "completeness": dict(assessment.completeness),
        "evidence_ids": list(assessment.evidence_ids),
        "events": [
            {
                **_event_dict(detail.event),
                "raw_status": detail.raw_status,
                "effective_status": detail.effective_status,
                "evidence_ids": list(detail.evidence_ids),
                "nominal_native_date": detail.nominal_native_date._asdict()
                if detail.nominal_native_date
                else None,
                "chronology_profile": detail.chronology_profile,
                "chronology_provider": detail.chronology_provider,
                "observation_lineage": [_iso(day) for day in detail.observation_lineage],
            }
            for detail in assessment.events
        ],
    }


def _weekend_policy_dict(policy: WeekendPolicy) -> Dict[str, Any]:
    return {
        "days": sorted(DAY_NAMES[d] for d in policy.days),
        "periods": [
            {
                "days": sorted(DAY_NAMES[d] for d in period.days),
                "from": _iso(period.start),
                "to": _iso(period.end),
            }
            for period in policy.periods
        ],
    }


def _aliases_for(calendar_id: str) -> List[str]:
    return sorted(alias for alias, target in bdc.list_aliases().items() if target == calendar_id)


def _calendar_summary(cal: bdc.SingleCalendar) -> Dict[str, Any]:
    return {
        "calendar_id": cal.calendar_id,
        "data_version": bdc.data_version,
        "name": cal.name,
        "kind": cal.kind,
        "timezone": cal.timezone,
        "coverage": {"from": _iso(cal.range.start), "to": _iso(cal.range.end)},
        "verified_through": _iso(cal.verified_through),
        "aliases": _aliases_for(cal.calendar_id),
    }


def _member_ids(cal: bdc.BusinessCalendar) -> List[str]:
    if isinstance(cal, bdc.JointCalendar):
        return [m.calendar_id for m in cal.members]
    return [cal.calendar_id]


def _closed_member_ids(cal: bdc.BusinessCalendar, day: _dt.date) -> List[str]:
    if isinstance(cal, bdc.JointCalendar):
        return [m.calendar_id for m in cal.closed_members(day)]
    return [] if cal.is_business_day(day) else [cal.calendar_id]


# --- Server construction -------------------------------------------------------


def create_server() -> "Any":
    """
    Builds the configured :class:`mcp.server.fastmcp.FastMCP` instance.

    Imports the ``mcp`` SDK on first call, so importing this module (or
    :mod:`bdc_calendars` generally) never requires it to be installed.
    """
    try:
        from mcp.server.fastmcp import FastMCP
    except ImportError as exc:  # pragma: no cover - exercised via test skip
        raise ImportError(
            "bdc-calendars-mcp needs the optional 'mcp' extra: "
            'pip install "bdc-calendars[mcp]"'
        ) from exc

    app = FastMCP(
        "bdc-calendars",
        instructions=(
            "Trading and business-day calendar lookups backed by the bdc-calendars "
            "package: offline, deterministic data generated from cited YAML specs. "
            "Call list_calendars() to discover valid `calendar` ids (native ids like "
            "US-NYSE, or exchange_calendars MIC aliases like XNYS both work). Dates "
            "are ISO strings (YYYY-MM-DD). Errors are returned as "
            '{"error": ..., "message": ...} instead of being raised, so recover '
            "(e.g. re-check the calendar id, or call status() to probe a date) "
            "rather than giving up."
        ),
    )

    @app.tool()
    def list_calendars() -> Dict[str, Any]:
        """
        List every calendar bundled with this release of bdc-calendars.

        For each calendar: its id, human-readable name, kind (``market`` or
        ``base``), IANA timezone, covered date range, ``verified_through``
        date and any known aliases (exchange_calendars MICs, etc). Call this
        first to discover valid ``calendar`` arguments for the other tools.

        Example question: "What calendars are available, and which ones cover
        Saudi Arabia?"
        """
        calendars = [_calendar_summary(bdc.get_calendar(cid)) for cid in bdc.list_calendars()]
        return {"data_version": bdc.data_version, "calendars": calendars}

    @app.tool()
    def calendar_info(calendar: str) -> Dict[str, Any]:
        """
        Full metadata for one calendar.

        Includes everything in :func:`list_calendars`, plus any sources cited
        in the calendar's package metadata (when present) and its effective-
        dated weekend policy (the weekday(s) treated as weekend, and the
        periods over which that has changed, e.g. Saudi Arabia's move from a
        Thursday/Friday to a Friday/Saturday weekend in 2013).

        Example question: "What weekend policy does US-NYSE use, and has it
        ever changed?"
        """
        try:
            cal = bdc.get_calendar(calendar)
        except _QUERY_ERRORS as exc:
            return _error(exc)
        info = _calendar_summary(cal)
        info["sources"] = cal.metadata.get("sources")
        info["weekend_policy"] = _weekend_policy_dict(cal.weekend_policy)
        info["coverage_quality"] = [
            {
                "scope": interval.scope,
                "from": _iso(interval.start),
                "to": _iso(interval.end),
                "quality": interval.quality,
                "evidence_ids": interval.evidence_ids,
            }
            for interval in cal.coverage_intervals
        ]
        return info

    @app.tool()
    def assess_day(calendar: str, date: str) -> Dict[str, Any]:
        """Explain actual and scheduled state, confidence, completeness and evidence for a date."""
        try:
            cal = bdc.get_calendar(calendar)
            day = _parse_date(date)
            assessment = cal.assessment(day)
        except _QUERY_ERRORS as exc:
            return _error(exc)
        return {
            "calendar_id": cal.calendar_id,
            "data_version": bdc.data_version,
            **_assessment_dict(assessment),
        }

    @app.tool()
    def is_business_day(calendar: str, date: str) -> Dict[str, Any]:
        """
        Whether ``calendar`` trades on ``date`` (ISO ``YYYY-MM-DD``).

        True unless the date is a weekend under the calendar's effective-dated
        weekend policy or carries a ``CLOSED`` event; an ``EARLY_CLOSE`` day is
        still a business day. Returns the events on that date (type,
        description, close_time, status) and, when the answer is false, a
        short ``reason`` for the closure.

        Example question: "Does the US-NYSE trade on 2021-12-31?"
        """
        try:
            cal = bdc.get_calendar(calendar)
            day = _parse_date(date)
            open_today = cal.is_business_day(day)
            events = [_event_dict(e) for e in cal.events_on(day)]
        except _QUERY_ERRORS as exc:
            return _error(exc)
        result = {
            "calendar_id": cal.calendar_id,
            "data_version": bdc.data_version,
            "date": _iso(day),
            "is_business_day": open_today,
            "status": cal.status(day),
            "verified_through": _iso(cal.verified_through),
            "events": events,
        }
        if not open_today:
            result["reason"] = events[0]["description"] if events else "weekend"
        return result

    @app.tool()
    def next_business_day(calendar: str, date: str) -> Dict[str, Any]:
        """
        The first business day strictly after ``date`` on ``calendar``.

        Example question: "What's the next US-NYSE trading day after
        2025-12-25?"
        """
        try:
            cal = bdc.get_calendar(calendar)
            day = _parse_date(date)
            result = cal.next_business_day(day)
        except _QUERY_ERRORS + (RuntimeError,) as exc:
            return _error(exc)
        return {
            "calendar_id": cal.calendar_id,
            "data_version": bdc.data_version,
            "date": _iso(day),
            "next_business_day": _iso(result),
            "status": cal.status(result),
            "verified_through": _iso(cal.verified_through),
        }

    @app.tool()
    def previous_business_day(calendar: str, date: str) -> Dict[str, Any]:
        """
        The last business day strictly before ``date`` on ``calendar``.

        Example question: "What was the last US-NYSE trading day before
        2026-01-01?"
        """
        try:
            cal = bdc.get_calendar(calendar)
            day = _parse_date(date)
            result = cal.previous_business_day(day)
        except _QUERY_ERRORS + (RuntimeError,) as exc:
            return _error(exc)
        return {
            "calendar_id": cal.calendar_id,
            "data_version": bdc.data_version,
            "date": _iso(day),
            "previous_business_day": _iso(result),
            "status": cal.status(result),
            "verified_through": _iso(cal.verified_through),
        }

    @app.tool()
    def add_business_days(calendar: str, date: str, n: int) -> Dict[str, Any]:
        """
        The ``n``-th business day from ``date`` on ``calendar``.

        Walks forward for ``n > 0`` and backward for ``n < 0``, counting only
        business days; ``n = 0`` returns ``date`` unchanged whether or not it
        is a business day. The starting date is never counted, so this is the
        settlement primitive: T+2 from a trade date is
        ``add_business_days(calendar, trade_date, 2)``.

        Example question: "If a US-NYSE trade happens on 2025-11-26, what date
        is T+2 settlement?"
        """
        try:
            cal = bdc.get_calendar(calendar)
            day = _parse_date(date)
            result = cal.add_business_days(day, n)
        except _QUERY_ERRORS + (RuntimeError,) as exc:
            return _error(exc)
        return {
            "calendar_id": cal.calendar_id,
            "data_version": bdc.data_version,
            "date": _iso(day),
            "n": n,
            "result": _iso(result),
            "status": cal.status(result),
            "verified_through": _iso(cal.verified_through),
        }

    @app.tool()
    def business_days_between(calendar: str, start: str, end: str) -> Dict[str, Any]:
        """
        Count of business days on ``calendar`` in ``[start, end]``, **both
        endpoints included** (so the count for a single trading day is 1, not
        0).

        Example question: "How many US-NYSE trading days were there in March
        2026?"
        """
        try:
            cal = bdc.get_calendar(calendar)
            first = _parse_date(start, "start")
            last = _parse_date(end, "end")
            count = cal.business_days_between(first, last)
        except _QUERY_ERRORS as exc:
            return _error(exc)
        return {
            "calendar_id": cal.calendar_id,
            "data_version": bdc.data_version,
            "start": _iso(first),
            "end": _iso(last),
            "business_days": count,
            "verified_through": _iso(cal.verified_through),
        }

    @app.tool()
    def holidays_in_range(
        calendar: str, start: str, end: str, include_early_closes: bool = True
    ) -> Dict[str, Any]:
        """
        Every event on ``calendar`` in ``[start, end]`` except ``WEEKEND``
        rows, ordered by date (closures, early closes and notable days). Set
        ``include_early_closes=False`` to drop ``EARLY_CLOSE`` rows too and
        list only full closures and notable days.

        Example question: "What holidays and early closes does US-NYSE
        observe in 2026?"
        """
        try:
            cal = bdc.get_calendar(calendar)
            first = _parse_date(start, "start")
            last = _parse_date(end, "end")
            events = [
                _event_dict(e)
                for e in cal.events_in_range(first, last)
                if e.type != "WEEKEND" and (include_early_closes or e.type != "EARLY_CLOSE")
            ]
        except _QUERY_ERRORS as exc:
            return _error(exc)
        return {
            "calendar_id": cal.calendar_id,
            "data_version": bdc.data_version,
            "start": _iso(first),
            "end": _iso(last),
            "include_early_closes": include_early_closes,
            "events": events,
        }

    @app.tool()
    def is_early_close(calendar: str, date: str) -> Dict[str, Any]:
        """
        Whether ``date`` is a shortened session on ``calendar``, and its local
        close time when it is (``None`` for both a regular session and a
        fully closed date — see ``is_business_day`` to tell those apart).

        Example question: "Does the US-NYSE close early on 2027-11-26, and if
        so, at what time?"
        """
        try:
            cal = bdc.get_calendar(calendar)
            day = _parse_date(date)
            close_time = cal.close_time(day)
        except _QUERY_ERRORS as exc:
            return _error(exc)
        return {
            "calendar_id": cal.calendar_id,
            "data_version": bdc.data_version,
            "date": _iso(day),
            "is_early_close": close_time is not None,
            "close_time": close_time.isoformat() if close_time else None,
            "status": cal.status(day),
            "verified_through": _iso(cal.verified_through),
        }

    @app.tool()
    def joint_settlement_date(
        calendars: List[str], trade_date: str, t_plus: int
    ) -> Dict[str, Any]:
        """
        Settlement date T+``t_plus`` business days from ``trade_date`` on the
        joint calendar formed from ``calendars``: a trade settles only on a
        day every member market trades (the intersection of their trading
        days). Mirrors ``query <cals> --settlement T+N --from <date>``: also
        lists, for each day between the trade date and the settlement date,
        which member calendars were closed.

        Example question: "A trade between US-NYSE and SA-TADAWUL happens on
        2026-02-25; what date does it settle on at T+2, and which market was
        closed on the days in between?"
        """
        try:
            if not calendars:
                raise ValueError("joint_settlement_date needs at least one calendar")
            joint = bdc.get_joint_calendar(*calendars)
            trade = _parse_date(trade_date, "trade_date")
            settlement = joint.add_business_days(trade, t_plus)
            intervening: List[Dict[str, Any]] = []
            day = trade + _dt.timedelta(days=1)
            one = _dt.timedelta(days=1)
            while day <= settlement:
                intervening.append(
                    {"date": _iso(day), "closed_members": _closed_member_ids(joint, day)}
                )
                day += one
        except _QUERY_ERRORS + (RuntimeError,) as exc:
            return _error(exc)
        return {
            "calendar_id": joint.calendar_id,
            "data_version": bdc.data_version,
            "calendars": _member_ids(joint),
            "trade_date": _iso(trade),
            "t_plus": t_plus,
            "settlement_date": _iso(settlement),
            "status": joint.status(settlement),
            "verified_through": _iso(joint.verified_through),
            "intervening_days": intervening,
        }

    @app.tool()
    def status(calendar: str, date: str) -> Dict[str, Any]:
        """
        How much confidence the data for ``date`` on ``calendar`` deserves:
        ``CONFIRMED`` (checked against an authoritative source), ``PROJECTED``
        (after the calendar's ``verified_through``, or derived from a rule
        rather than a published schedule) or ``UNKNOWN`` (the date is outside
        the calendar's covered range). Never errors on a valid calendar and
        date — the safe way to probe an unfamiliar date before calling the
        other tools.

        Example question: "How reliable is the SA-TADAWUL data for
        2028-01-01?"
        """
        try:
            cal = bdc.get_calendar(calendar)
            day = _parse_date(date)
        except _QUERY_ERRORS as exc:
            return _error(exc)
        return {
            "calendar_id": cal.calendar_id,
            "data_version": bdc.data_version,
            "date": _iso(day),
            "status": cal.status(day),
            "verified_through": _iso(cal.verified_through),
        }

    return app


def main() -> None:
    """Entry point for the ``bdc-calendars-mcp`` console script (stdio transport)."""
    create_server().run()


if __name__ == "__main__":
    main()
