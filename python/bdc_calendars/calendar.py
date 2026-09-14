"""
The query API over a calendar.

This is a direct port of the ``Query API`` section of ``spec/SPEC.md`` and of
``com.bdc.stream.DateStream`` in the Java toolchain: the same primitives, the
same derived operations, the same edge semantics. Anything answered here is
answered identically by ``tools query`` on the same release of the data.

Out-of-range contract
---------------------
Every calendar has a :attr:`~BusinessCalendar.range`: the window in which the
absence of a closure row means "open" rather than "unknown". Outside it
:meth:`~BusinessCalendar.status` returns ``"UNKNOWN"`` (it never raises, so it
is the safe way to probe an unfamiliar date) and every other operation raises
:class:`~bdc_calendars.errors.OutsideCoverageError`.
"""

from __future__ import annotations

import datetime as _dt
import importlib.util
from typing import List, NamedTuple, Optional, Sequence, Tuple, Union

from . import _loader
from ._loader import Event
from ._weekend import DAY_NAMES, WeekendPolicy
from .errors import OutsideCoverageError, UnresolvedDateError
from .trust import (
    CLOSED,
    COMPLETENESS_SCOPES,
    EARLY_CLOSE,
    INCOMPLETE,
    OPEN,
    PROJECTED as QUALITY_PROJECTED,
    UNKNOWN as STATE_UNKNOWN,
    CoverageInterval,
    DayAssessment,
    EventDetails,
)

__all__ = [
    "BusinessCalendar",
    "SingleCalendar",
    "JointCalendar",
    "DateRange",
    "Event",
    "get_calendar",
    "get_joint_calendar",
    "list_calendars",
]

CONFIRMED = "CONFIRMED"
PROJECTED = "PROJECTED"
UNKNOWN = "UNKNOWN"

#: Bounded searches never walk further than this many days for a single business day.
MAX_SEARCH_DAYS = 366

DateLike = Union[_dt.date, _dt.datetime, str]


class DateRange(NamedTuple):
    """A closed date window. Unpacks as ``(from, to)``."""

    start: _dt.date
    end: _dt.date

    def contains(self, date: _dt.date) -> bool:
        return self.start <= date <= self.end

    def __str__(self) -> str:
        return "{} to {}".format(self.start, self.end)


def _as_date(value: DateLike, argument: str = "date") -> _dt.date:
    """Coerces the usual date spellings (``date``, ``datetime``, ISO string)."""
    if isinstance(value, _dt.datetime):
        return value.date()
    if isinstance(value, _dt.date):
        return value
    if isinstance(value, str):
        return _dt.date.fromisoformat(value)
    # pandas.Timestamp, numpy.datetime64 and anything else that knows its date
    to_pydatetime = getattr(value, "to_pydatetime", None)
    if to_pydatetime is not None:
        return to_pydatetime().date()
    raise TypeError("{} must be a date, datetime or ISO string, got {!r}".format(argument, value))


class BusinessCalendar:
    """
    A calendar made queryable: the surface every binding mirrors.

    Subclasses supply the primitives (:attr:`calendar_id`, :attr:`range`,
    :attr:`verified_through`, :meth:`events_on`, :meth:`events_in_range` and
    :meth:`is_business_day`) and inherit every derived operation from here, so
    single and joint calendars answer identically.
    """

    # --- Identity and bounds -------------------------------------------------

    @property
    def calendar_id(self) -> str:
        """The calendar this instance answers for."""
        raise NotImplementedError

    @property
    def range(self) -> DateRange:
        """The window this calendar can answer in, as ``(from, to)``."""
        raise NotImplementedError

    @property
    def verified_through(self) -> Optional[_dt.date]:
        """
        The last date checked against authoritative sources, when declared.

        Dates after it are :data:`PROJECTED`, whatever the underlying rows say.
        """
        raise NotImplementedError

    @property
    def coverage_intervals(self) -> Sequence[CoverageInterval]:
        """Explicit scope-specific quality intervals; empty for legacy artifacts."""
        return ()

    # --- Core queries --------------------------------------------------------

    def events_on(self, date: DateLike) -> List[Event]:
        """Every event on a date, possibly empty."""
        raise NotImplementedError

    def events_in_range(self, start: DateLike, end: DateLike) -> List[Event]:
        """Every event with ``start <= date <= end``, ordered by date."""
        raise NotImplementedError

    def is_business_day(self, date: DateLike) -> bool:
        """
        Whether the market trades on this date.

        True when the date is neither a weekend under the calendar's
        (effective-dated) weekend policy nor carries a ``CLOSED`` event. An
        ``EARLY_CLOSE`` day *is* a business day.
        """
        raise NotImplementedError

    def event_on(self, date: DateLike) -> Optional[Event]:
        """The first of :meth:`events_on`, or ``None``."""
        events = self.events_on(date)
        return events[0] if events else None

    # --- Navigation ----------------------------------------------------------

    def next_business_day(self, date: DateLike) -> _dt.date:
        """
        The first business day *strictly after* ``date``.

        :raises OutsideCoverageError: if the search leaves :attr:`range`
        :raises RuntimeError: if no business day is found within 366 days
        """
        candidate = _as_date(date) + _dt.timedelta(days=1)
        for _ in range(MAX_SEARCH_DAYS):
            if self.is_business_day(candidate):
                return candidate
            candidate += _dt.timedelta(days=1)
        raise RuntimeError(
            "No business day within {} days after {}".format(MAX_SEARCH_DAYS, _as_date(date))
        )

    def previous_business_day(self, date: DateLike) -> _dt.date:
        """
        The last business day *strictly before* ``date``.

        :raises OutsideCoverageError: if the search leaves :attr:`range`
        :raises RuntimeError: if no business day is found within 366 days
        """
        candidate = _as_date(date) - _dt.timedelta(days=1)
        for _ in range(MAX_SEARCH_DAYS):
            if self.is_business_day(candidate):
                return candidate
            candidate -= _dt.timedelta(days=1)
        raise RuntimeError(
            "No business day within {} days before {}".format(MAX_SEARCH_DAYS, _as_date(date))
        )

    #: Alias matching the spelling used in ``spec/SPEC.md`` and the Java API.
    prev_business_day = previous_business_day

    def add_business_days(self, date: DateLike, n: int) -> _dt.date:
        """
        The ``n``-th business day from ``date`` (the spec's ``nth_business_day``).

        Walks day by day, counting business days, forward for ``n > 0`` and
        backward for ``n < 0``. ``n == 0`` returns ``date`` unchanged whether or
        not it is a business day. The starting date is never counted, so T+1
        from a Friday is the following Monday.

        :raises OutsideCoverageError: if the walk leaves :attr:`range`
        :raises RuntimeError: if the walk exceeds ``366 * |n| + 366`` days
        """
        current = _as_date(date)
        if n == 0:
            return current
        remaining = abs(n)
        forward = n > 0
        step = _dt.timedelta(days=1 if forward else -1)
        guard = MAX_SEARCH_DAYS * remaining + MAX_SEARCH_DAYS
        while remaining > 0:
            if guard <= 0:
                raise RuntimeError(
                    "Could not find {} business days {} {}".format(
                        abs(n), "after" if forward else "before", _as_date(date)
                    )
                )
            guard -= 1
            current += step
            if self.is_business_day(current):
                remaining -= 1
        return current

    #: Alias matching the spelling used in ``spec/SPEC.md`` and the Java API.
    nth_business_day = add_business_days

    # --- Counting ------------------------------------------------------------

    def business_days_between(self, start: DateLike, end: DateLike) -> int:
        """
        The number of business days in ``[start, end]``, **both endpoints included**.

        This is the spec's ``business_days_in_range``; the inclusive endpoints
        are deliberate, so ``business_days_between(d, d)`` is 1 on a trading day.
        """
        first = _as_date(start, "start")
        last = _as_date(end, "end")
        if first > last:
            raise ValueError("from must not be after to")
        count = 0
        day = first
        one = _dt.timedelta(days=1)
        while day <= last:
            if self.is_business_day(day):
                count += 1
            day += one
        return count

    #: Alias matching the spelling used in ``spec/SPEC.md`` and the Java API.
    business_days_in_range = business_days_between

    def event_count_in_range(self, start: DateLike, end: DateLike) -> int:
        """The number of events in ``[start, end]``, weekend rows included."""
        first = _as_date(start, "start")
        last = _as_date(end, "end")
        if first > last:
            raise ValueError("from must not be after to")
        day = first
        one = _dt.timedelta(days=1)
        while day <= last:
            self._require_resolved(day)
            day += one
        return len(self.events_in_range(first, last))

    # --- Session detail ------------------------------------------------------

    def close_time(self, date: DateLike) -> Optional[_dt.time]:
        """
        The local close time of a shortened session, or ``None``.

        Where several early closes apply the earliest wins. The time is local to
        the calendar's declared :attr:`timezone`; a regular session and a fully
        closed date both answer ``None`` (use :meth:`is_business_day` to tell
        them apart).
        """
        day = _as_date(date)
        self._require_resolved(day)
        times = [
            event.close_time
            for event in self.events_on(day)
            if event.type == "EARLY_CLOSE" and event.close_time is not None
        ]
        return min(times) if times else None

    def is_early_close(self, date: DateLike) -> bool:
        """
        Whether the date is a shortened session.

        Equivalent to ``close_time(date) is not None``. A fully closed date is
        not an early close: ``CLOSED`` beats ``EARLY_CLOSE`` on the same date.
        """
        return self.close_time(date) is not None

    def status(self, date: DateLike) -> str:
        """
        How much confidence the data for this date deserves. Never raises.

        * ``"UNKNOWN"`` when the date lies outside :attr:`range`;
        * ``"PROJECTED"`` when the date is after :attr:`verified_through`,
          whatever the rows say, or when any event on the date is projected;
        * ``"CONFIRMED"`` otherwise.
        """
        day = _as_date(date)
        if not self.range.contains(day):
            return UNKNOWN
        if self.coverage_intervals:
            return self.assessment(day).effective_confidence
        verified = self.verified_through
        if verified is not None and day > verified:
            return PROJECTED
        for event in self.events_on(day):
            if event.status == PROJECTED:
                return PROJECTED
        return CONFIRMED

    def assessment(self, date: DateLike) -> DayAssessment:
        """Return actual, scheduled and completeness state without raising."""
        day = _as_date(date)
        if not self.range.contains(day):
            return DayAssessment(
                day,
                STATE_UNKNOWN,
                STATE_UNKNOWN,
                UNKNOWN,
                {scope: INCOMPLETE for scope in COMPLETENESS_SCOPES},
                [],
                [],
            )
        events = self.events_on(day)
        scheduled = _scheduled_state(events)
        explicit = bool(self.coverage_intervals)
        completeness = {}
        evidence = []
        for scope in COMPLETENESS_SCOPES:
            matching = [
                interval
                for interval in self.coverage_intervals
                if interval.scope == scope and interval.contains(day)
            ]
            qualities = [interval.quality for interval in matching]
            quality = (
                INCOMPLETE
                if INCOMPLETE in qualities
                else QUALITY_PROJECTED
                if QUALITY_PROJECTED in qualities
                else qualities[0]
                if qualities
                else QUALITY_PROJECTED
                if not explicit
                else INCOMPLETE
            )
            completeness[scope] = quality
            for interval in matching:
                for evidence_id in interval.evidence_ids:
                    if evidence_id not in evidence:
                        evidence.append(evidence_id)
        incomplete = INCOMPLETE in completeness.values()
        raw_projected = any(event.status == PROJECTED for event in events)
        if incomplete:
            confidence = UNKNOWN
        elif QUALITY_PROJECTED in completeness.values() or raw_projected:
            confidence = PROJECTED
        else:
            confidence = CONFIRMED
        if not explicit:
            confidence = (
                PROJECTED
                if (self.verified_through is not None and day > self.verified_through)
                or raw_projected
                else CONFIRMED
            )
        details = [
            detail._replace(effective_status=confidence)
            for detail in self.event_details_on(day)
        ]
        evidence = sorted(set(evidence).union(*(set(detail.evidence_ids) for detail in details)))
        return DayAssessment(
            day,
            STATE_UNKNOWN if incomplete else scheduled,
            scheduled,
            confidence,
            completeness,
            evidence,
            details,
        )

    # --- Convenience ---------------------------------------------------------

    def holidays(self, year: int) -> List[Event]:
        """
        Every ``CLOSED`` event in a calendar year, ordered by date.

        Weekends are not holidays and are not returned; a closure that fell on a
        weekend is not published as a separate closure either.

        :raises OutsideCoverageError: if the year is not fully inside :attr:`range`
        """
        first = _dt.date(year, 1, 1)
        last = _dt.date(year, 12, 31)
        return [e for e in self.events_in_range(first, last) if e.type == "CLOSED"]

    # --- exchange_calendars-style aliases ------------------------------------

    def is_session(self, date: DateLike) -> bool:
        """exchange_calendars spelling of :meth:`is_business_day`."""
        return self.is_business_day(date)

    def next_session(self, date: DateLike) -> _dt.date:
        """exchange_calendars spelling of :meth:`next_business_day`."""
        return self.next_business_day(date)

    def previous_session(self, date: DateLike) -> _dt.date:
        """exchange_calendars spelling of :meth:`previous_business_day`."""
        return self.previous_business_day(date)

    def sessions_in_range(self, start: DateLike, end: DateLike) -> List[_dt.date]:
        """Every business day in ``[start, end]``, both endpoints included."""
        first = _as_date(start, "start")
        last = _as_date(end, "end")
        if first > last:
            raise ValueError("from must not be after to")
        sessions: List[_dt.date] = []
        day = first
        one = _dt.timedelta(days=1)
        while day <= last:
            if self.is_business_day(day):
                sessions.append(day)
            day += one
        return sessions

    # --- Internals -----------------------------------------------------------

    def _check_range(self, date: _dt.date) -> None:
        window = self.range
        if not window.contains(date):
            raise OutsideCoverageError(self.calendar_id, date, window.start, window.end)

    def event_details_on(self, date: DateLike) -> List[EventDetails]:
        """Raw retained provenance; assessment() supplies effective confidence."""
        return [EventDetails(event, event.status, event.status, [], None, None, None,
            [event.observed_from, event.date] if event.observed_from else [])
            for event in self.events_on(date)]

    def _require_resolved(self, date: _dt.date) -> None:
        assessment = self.assessment(date)
        if assessment.state != STATE_UNKNOWN:
            return
        self._check_range(date)
        incomplete = [
            scope for scope, quality in assessment.completeness.items() if quality == INCOMPLETE
        ]
        raise UnresolvedDateError(
            self.calendar_id, date, self.range.start, self.range.end, incomplete
        )

    def __repr__(self) -> str:
        return "<{} {} {}>".format(type(self).__name__, self.calendar_id, self.range)


class SingleCalendar(BusinessCalendar):
    """One published calendar, answered from the data bundled in this wheel."""

    def __init__(self, data: "_loader.CalendarData") -> None:
        self._data = data

    # --- Identity and bounds -------------------------------------------------

    @property
    def calendar_id(self) -> str:
        return self._data.calendar_id

    @property
    def name(self) -> str:
        """The calendar's human-readable name."""
        return self._data.name

    @property
    def kind(self) -> str:
        """``market`` for a tradable venue, ``base`` for a building block."""
        return self._data.kind

    @property
    def timezone(self) -> Optional[str]:
        """The IANA zone id close times are expressed in."""
        return self._data.timezone

    @property
    def weekend_policy(self) -> WeekendPolicy:
        """The effective-dated weekend definition weekend rows are rebuilt from."""
        return self._data.weekend_policy

    @property
    def metadata(self) -> dict:
        """The published ``metadata.json`` for this calendar."""
        return dict(self._data.metadata)

    @property
    def range(self) -> DateRange:
        return DateRange(self._data.range_from, self._data.range_to)

    @property
    def verified_through(self) -> Optional[_dt.date]:
        return self._data.verified_through

    @property
    def coverage_intervals(self) -> Sequence[CoverageInterval]:
        return self._data.coverage_intervals

    # --- Core queries --------------------------------------------------------

    def events_on(self, date: DateLike) -> List[Event]:
        day = _as_date(date)
        self._check_range(day)
        return self._events_on_unchecked(day)

    def event_details_on(self, date: DateLike) -> List[EventDetails]:
        day = _as_date(date)
        self._check_range(day)
        if day not in self._data.details_by_date:
            return super().event_details_on(day)
        details = list(self._data.details_by_date[day])
        details.extend(detail for detail in super().event_details_on(day) if detail.event.type == "WEEKEND")
        return details

    def _events_on_unchecked(self, day: _dt.date) -> List[Event]:
        # Weekend rows are the ones dropped from the shipped data. The artifact
        # carries a WEEKEND row on exactly the policy weekends that no closure
        # already covers (a closure wins over the weekend row where both apply,
        # a NOTABLE sits alongside it), and the sync script verifies that rule
        # holds for the published data, so this reproduces it row for row.
        rows = list(self._data.events_by_date.get(day) or ())
        if self._is_weekend_row(day, rows):
            rows.append(
                Event(
                    date=day,
                    type="WEEKEND",
                    description=DAY_NAMES[day.weekday()].capitalize(),
                    key="weekend",
                    source_module="weekend_policy",
                    observed_from=None,
                    close_time=None,
                    status=CONFIRMED,
                )
            )
        return rows

    def _is_weekend_row(self, day: _dt.date, rows: Sequence[Event]) -> bool:
        if not self._data.weekend_policy.is_weekend(day):
            return False
        return not any(event.type == "CLOSED" for event in rows)

    def events_in_range(self, start: DateLike, end: DateLike) -> List[Event]:
        first = _as_date(start, "start")
        last = _as_date(end, "end")
        if first > last:
            raise ValueError("from must not be after to")
        self._check_range(first)
        self._check_range(last)
        events: List[Event] = []
        day = first
        one = _dt.timedelta(days=1)
        while day <= last:
            events.extend(self._events_on_unchecked(day))
            day += one
        return events

    def is_business_day(self, date: DateLike) -> bool:
        day = _as_date(date)
        self._check_range(day)
        self._require_resolved(day)
        if self._data.weekend_policy.is_weekend(day):
            return False
        rows = self._data.events_by_date.get(day)
        return not rows or not any(event.type == "CLOSED" for event in rows)


class JointCalendar(BusinessCalendar):
    """
    Several calendars queried as one: open only when *every* member trades.

    "Intersection of trading days" and "union of closures" are the same
    predicate, so there is exactly one constructor for it:
    :func:`get_joint_calendar`. A different predicate (for example "open in any
    member") would be a different constructor; none is defined.
    """

    def __init__(self, members: Sequence[BusinessCalendar]) -> None:
        self._members: Tuple[BusinessCalendar, ...] = tuple(members)
        self._calendar_id = "+".join(m.calendar_id for m in self._members)
        start = _dt.date.min
        end = _dt.date.max
        for member in self._members:
            window = member.range
            if window.start > start:
                start = window.start
            if window.end < end:
                end = window.end
        if start > end:
            raise ValueError(
                "Calendars {} have no overlapping covered range: {}".format(
                    self._calendar_id,
                    ["{} {}".format(m.calendar_id, m.range) for m in self._members],
                )
            )
        self._range = DateRange(start, end)

    @property
    def members(self) -> Tuple[BusinessCalendar, ...]:
        """The member calendars, in the order they were given."""
        return self._members

    def closed_members(self, date: DateLike) -> List[BusinessCalendar]:
        """The members that do not trade on the given date, in member order."""
        day = _as_date(date)
        self._check_range(day)
        return [m for m in self._members if not m.is_business_day(day)]

    # --- Identity and bounds -------------------------------------------------

    @property
    def calendar_id(self) -> str:
        return self._calendar_id

    @property
    def range(self) -> DateRange:
        return self._range

    @property
    def verified_through(self) -> Optional[_dt.date]:
        declared = [m.verified_through for m in self._members if m.verified_through is not None]
        return min(declared) if declared else None

    # --- Core queries --------------------------------------------------------

    def events_on(self, date: DateLike) -> List[Event]:
        day = _as_date(date)
        self._check_range(day)
        events: List[Event] = []
        for member in self._members:
            for event in member.events_on(day):
                events.append(_qualify(member, event))
        return events

    def events_in_range(self, start: DateLike, end: DateLike) -> List[Event]:
        first = _as_date(start, "start")
        last = _as_date(end, "end")
        if first > last:
            raise ValueError("from must not be after to")
        self._check_range(first)
        self._check_range(last)
        events: List[Event] = []
        for member in self._members:
            for event in member.events_in_range(first, last):
                events.append(_qualify(member, event))
        events.sort(key=lambda e: e.date)  # list.sort is stable
        return events

    def is_business_day(self, date: DateLike) -> bool:
        day = _as_date(date)
        self._check_range(day)
        self._require_resolved(day)
        return all(member.is_business_day(day) for member in self._members)

    def close_time(self, date: DateLike) -> Optional[_dt.time]:
        day = _as_date(date)
        self._check_range(day)
        times = [t for t in (m.close_time(day) for m in self._members) if t is not None]
        return min(times) if times else None

    def status(self, date: DateLike) -> str:
        day = _as_date(date)
        projected = False
        for member in self._members:
            member_status = member.status(day)
            if member_status == UNKNOWN:
                return UNKNOWN
            if member_status == PROJECTED:
                projected = True
        return PROJECTED if projected else CONFIRMED

    def assessment(self, date: DateLike) -> DayAssessment:
        day = _as_date(date)
        if not self.range.contains(day):
            return super().assessment(day)
        assessments = [member.assessment(day) for member in self._members]
        completeness = {}
        evidence = []
        for scope in COMPLETENESS_SCOPES:
            qualities = [item.completeness[scope] for item in assessments]
            completeness[scope] = (
                INCOMPLETE
                if INCOMPLETE in qualities
                else QUALITY_PROJECTED if QUALITY_PROJECTED in qualities else "VERIFIED"
            )
        for item in assessments:
            for evidence_id in item.evidence_ids:
                if evidence_id not in evidence:
                    evidence.append(evidence_id)
        scheduled = (
            CLOSED
            if any(item.scheduled_state == CLOSED for item in assessments)
            else EARLY_CLOSE
            if any(item.scheduled_state == EARLY_CLOSE for item in assessments)
            else OPEN
        )
        confidence = (
            UNKNOWN
            if any(item.effective_confidence == UNKNOWN for item in assessments)
            else PROJECTED
            if any(item.effective_confidence == PROJECTED for item in assessments)
            else CONFIRMED
        )
        details = []
        for member, item in zip(self._members, assessments):
            for detail in item.events:
                details.append(detail._replace(event=_qualify(member, detail.event)))
        return DayAssessment(
            day,
            STATE_UNKNOWN if confidence == UNKNOWN else scheduled,
            scheduled,
            confidence,
            completeness,
            evidence,
            details,
        )


def _qualify(member: BusinessCalendar, event: Event) -> Event:
    """Re-labels a member event so its origin stays visible in the joint stream."""
    if event.source_module is None:
        source = member.calendar_id
    else:
        source = "{}/{}".format(member.calendar_id, event.source_module)
    return event._replace(source_module=source)


def _scheduled_state(events: Sequence[Event]) -> str:
    if any(event.type in ("CLOSED", "WEEKEND") for event in events):
        return CLOSED
    if any(event.type == "EARLY_CLOSE" for event in events):
        return EARLY_CLOSE
    return OPEN


# --- Factories ---------------------------------------------------------------

_INSTANCES: dict = {}


def get_calendar(calendar_id: str) -> SingleCalendar:
    """
    The calendar for an id.

    Accepts the canonical ids (``US-NYSE``), the exchange_calendars MIC aliases
    (``XNYS``, ``XSAU``) and any case spelling of either. Instances are cached,
    so repeated calls with the same id return the same object.

    :raises CalendarNotFoundError: if no bundled calendar matches
    """
    resolved = _loader.resolve_id(calendar_id)
    cached = _INSTANCES.get(resolved)
    if cached is None:
        cached = SingleCalendar(_loader.load_calendar(resolved))
        _INSTANCES[resolved] = cached
    return cached


def get_joint_calendar(*calendars: Union[str, BusinessCalendar]) -> BusinessCalendar:
    """
    Several calendars queried as one: a date is a business day only when every
    member trades on it.

    Members may be ids, aliases or calendar objects. The joint of a single
    calendar *is* that calendar, so a one-member call returns it unwrapped.

    :raises ValueError: if no calendar is given or the member ranges do not overlap
    """
    if len(calendars) == 1 and isinstance(calendars[0], (list, tuple)):
        calendars = tuple(calendars[0])  # type: ignore[assignment]
    members: List[BusinessCalendar] = []
    for item in calendars:
        members.append(item if isinstance(item, BusinessCalendar) else get_calendar(item))
    if not members:
        raise ValueError("get_joint_calendar() needs at least one calendar")
    if len(members) == 1:
        return members[0]
    return JointCalendar(members)


def list_calendars() -> List[str]:
    """Every bundled calendar id, sorted."""
    return _loader.calendar_ids()


def list_aliases() -> dict:
    """The alias table: exchange code (``XNYS``) to calendar id (``US-NYSE``)."""
    return dict(_loader.aliases())


# --- Optional pandas integration ---------------------------------------------

_HAS_PANDAS = importlib.util.find_spec("pandas") is not None


def _schedule(self: BusinessCalendar):
    """
    A :class:`pandas.DataFrame` of every session in :attr:`range`.

    Indexed by session date, with ``close_time`` (a :class:`datetime.time`, or
    ``NaT`` for a regular session) and ``is_early_close``. Regular open and
    close times are not part of the published data, so — unlike
    exchange_calendars — only shortened sessions carry a time.

    Only available when pandas is installed (``pip install bdc-calendars[pandas]``).
    """
    import pandas as pd

    window = self.range
    sessions = self.sessions_in_range(window.start, window.end)
    closes = [self.close_time(day) for day in sessions]
    return pd.DataFrame(
        {
            "close_time": closes,
            "is_early_close": [c is not None for c in closes],
        },
        index=pd.DatetimeIndex(pd.to_datetime(sessions), name="session"),
    )


if _HAS_PANDAS:  # pragma: no cover - depends on the environment
    BusinessCalendar.schedule = property(_schedule)  # type: ignore[attr-defined]
