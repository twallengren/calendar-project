"""Financial date conventions shared by single and joint business calendars."""

from __future__ import annotations

import calendar as _calendar
import datetime as _dt
from enum import Enum
from typing import List, NamedTuple, Optional, Tuple

from .finance import (
    DayCount,
    Frequency,
    RollConvention,
    StubConvention,
    Tenor,
    TenorUnit,
    roll as _roll,
)


class BusinessDayConvention(str, Enum):
    UNADJUSTED = "UNADJUSTED"
    FOLLOWING = "FOLLOWING"
    MODIFIED_FOLLOWING = "MODIFIED_FOLLOWING"
    PRECEDING = "PRECEDING"
    MODIFIED_PRECEDING = "MODIFIED_PRECEDING"


class DateOperation(str, Enum):
    ADJUST = "ADJUST"
    BUSINESS_DAY_OFFSET = "BUSINESS_DAY_OFFSET"
    ADVANCE_MONTHS = "ADVANCE_MONTHS"
    LAST_BUSINESS_DAY_OF_MONTH = "LAST_BUSINESS_DAY_OF_MONTH"
    ADD_TENOR = "ADD_TENOR"


class DateOperationResult(NamedTuple):
    original_date: _dt.date
    result_date: _dt.date
    operation: str
    convention: Optional[str]
    business_day_offset: Optional[int]
    month_offset: Optional[int]
    preserve_end_of_month: bool
    effective_confidence: str
    examined_dates: Tuple[_dt.date, ...]
    #: Canonical tenor spelling for ``ADD_TENOR``; ``None`` for every other operation.
    tenor: Optional[str] = None


class YearFractionResult(NamedTuple):
    """A year fraction with its day count and, for BUS/252, the counted days' confidence."""

    start: _dt.date
    end: _dt.date
    day_count: str
    days: int
    year_fraction: float
    effective_confidence: str
    examined_dates: Tuple[_dt.date, ...]


class ScheduleDefinition(NamedTuple):
    """An unadjusted periodic schedule and the convention that adjusts its boundaries."""

    start: _dt.date
    end: _dt.date
    frequency: Frequency
    stub: StubConvention = StubConvention.SHORT_FINAL
    roll: RollConvention = RollConvention.DAY_OF_MONTH
    convention: BusinessDayConvention = BusinessDayConvention.MODIFIED_FOLLOWING


class SchedulePeriod(NamedTuple):
    unadjusted_start: _dt.date
    unadjusted_end: _dt.date
    start: _dt.date
    end: _dt.date
    stub: bool


class Schedule(NamedTuple):
    definition: ScheduleDefinition
    periods: Tuple[SchedulePeriod, ...]
    effective_confidence: str
    examined_dates: Tuple[_dt.date, ...]

    @property
    def dates(self) -> List[_dt.date]:
        if not self.periods:
            return []
        return [self.periods[0].start] + [period.end for period in self.periods]

    @property
    def unadjusted_dates(self) -> List[_dt.date]:
        if not self.periods:
            return []
        return [self.periods[0].unadjusted_start] + [
            period.unadjusted_end for period in self.periods
        ]


class MemberClose(NamedTuple):
    calendar_id: str
    timezone: Optional[str]
    local_time: _dt.time


def convention(value) -> BusinessDayConvention:
    if isinstance(value, BusinessDayConvention):
        return value
    try:
        return BusinessDayConvention(str(value).upper())
    except ValueError as error:
        raise ValueError("unknown business-day convention: {!r}".format(value)) from error


class _Tracker:
    def __init__(self, calendar):
        self.calendar = calendar
        self.examined = []
        self._seen = set()
        self.confidence = "CONFIRMED"

    def _include(self, date, require):
        if date not in self._seen:
            self._seen.add(date)
            self.examined.append(date)
        assessment = self.calendar.assessment(date)
        if assessment.business_day_confidence == "UNKNOWN":
            self.confidence = "UNKNOWN"
        elif assessment.business_day_confidence == "PROJECTED" and self.confidence == "CONFIRMED":
            self.confidence = "PROJECTED"
        if require:
            self.calendar._require_resolved(date, "BUSINESS_DAY")

    def identity(self, date):
        self._include(date, False)

    def is_business_day(self, date):
        self._include(date, True)
        return self.calendar.is_business_day(date)

    def require_resolved(self, date):
        self._include(date, True)

    def result(
        self,
        original,
        result,
        operation,
        convention_value=None,
        business_day_offset=None,
        month_offset=None,
        preserve_end_of_month=False,
        tenor=None,
    ):
        return DateOperationResult(
            original,
            result,
            operation.value,
            convention_value.value if convention_value is not None else None,
            business_day_offset,
            month_offset,
            preserve_end_of_month,
            self.confidence,
            tuple(self.examined),
            tenor,
        )


def adjust_detailed(calendar, date, convention_value):
    convention_value = convention(convention_value)
    tracker = _Tracker(calendar)
    result = _adjust(calendar, date, convention_value, tracker)
    return tracker.result(date, result, DateOperation.ADJUST, convention_value)


def business_day_offset_detailed(calendar, date, offset):
    if not isinstance(offset, int) or isinstance(offset, bool):
        raise TypeError("offset must be an integer")
    tracker = _Tracker(calendar)
    if offset == 0:
        tracker.identity(date)
        return tracker.result(
            date, date, DateOperation.BUSINESS_DAY_OFFSET, business_day_offset=0
        )
    remaining = abs(offset)
    step = _dt.timedelta(days=1 if offset > 0 else -1)
    current = date
    guard = 366 * remaining + 366
    while remaining:
        if guard <= 0:
            raise RuntimeError(
                "Could not find {} business days {} {}".format(
                    abs(offset), "after" if offset > 0 else "before", date
                )
            )
        guard -= 1
        current += step
        if tracker.is_business_day(current):
            remaining -= 1
    return tracker.result(
        date,
        current,
        DateOperation.BUSINESS_DAY_OFFSET,
        business_day_offset=offset,
    )


def advance_months_detailed(
    calendar, date, months, convention_value, preserve_end_of_month=False
):
    if not isinstance(months, int) or isinstance(months, bool):
        raise TypeError("months must be an integer")
    convention_value = convention(convention_value)
    if months == 0 and convention_value == BusinessDayConvention.UNADJUSTED and not preserve_end_of_month:
        tracker = _Tracker(calendar)
        tracker.identity(date)
        return tracker.result(
            date,
            date,
            DateOperation.ADVANCE_MONTHS,
            convention_value,
            month_offset=0,
        )
    tracker = _Tracker(calendar)
    tracker.require_resolved(date)
    nominal = _add_months(date, months)
    if preserve_end_of_month and date == _last_business_day(calendar, date, tracker):
        result = _last_business_day(calendar, nominal, tracker)
    elif convention_value == BusinessDayConvention.UNADJUSTED:
        tracker.require_resolved(nominal)
        result = nominal
    else:
        result = _adjust(calendar, nominal, convention_value, tracker)
    return tracker.result(
        date,
        result,
        DateOperation.ADVANCE_MONTHS,
        convention_value,
        month_offset=months,
        preserve_end_of_month=preserve_end_of_month,
    )


def add_tenor_detailed(calendar, date, tenor, convention_value, preserve_end_of_month=False):
    """
    Add a market tenor. Day/week tenors shift calendar days then adjust; month/
    year tenors advance months then adjust; business-day tenors (``T+n``) walk
    business days and ignore the convention.
    """
    tenor = Tenor.parse(tenor)
    convention_value = convention(convention_value)
    if tenor.unit == TenorUnit.BUSINESS_DAYS:
        base = business_day_offset_detailed(calendar, date, tenor.amount)
        convention_out = None
    elif tenor.is_month_based:
        base = advance_months_detailed(
            calendar, date, tenor.months, convention_value, preserve_end_of_month
        )
        convention_out = convention_value.value
    else:
        tracker = _Tracker(calendar)
        nominal = date + _dt.timedelta(days=tenor.days)
        result = _adjust(calendar, nominal, convention_value, tracker)
        base = tracker.result(date, result, DateOperation.ADD_TENOR, convention_value)
        convention_out = convention_value.value
    return base._replace(
        operation=DateOperation.ADD_TENOR.value, convention=convention_out, tenor=str(tenor)
    )


def year_fraction_detailed(calendar, start, end, day_count):
    """A year fraction; BUS/252 counts the calendar's business days in ``[start, end)``."""
    day_count = DayCount.of(day_count)
    if day_count != DayCount.BUS_252:
        return YearFractionResult(
            start,
            end,
            day_count.value,
            day_count.days(start, end),
            day_count.year_fraction(start, end),
            "CONFIRMED",
            (),
        )
    if end < start:
        forward = year_fraction_detailed(calendar, end, start, day_count)
        return forward._replace(
            start=start, end=end, days=-forward.days, year_fraction=-forward.year_fraction
        )
    tracker = _Tracker(calendar)
    count = 0
    day = start
    one = _dt.timedelta(days=1)
    while day < end:
        if tracker.is_business_day(day):
            count += 1
        day += one
    return YearFractionResult(
        start, end, day_count.value, count, count / 252.0, tracker.confidence, tuple(tracker.examined)
    )


_MAX_PERIODS = 10_000


def _roll_from(definition, anchor, periods):
    tenor = definition.frequency.tenor
    if tenor.is_month_based:
        return _roll(definition.roll, anchor, tenor.months * periods)
    return anchor + _dt.timedelta(days=tenor.days * periods)


def unadjusted_schedule_dates(definition):
    """The unadjusted boundary dates of a schedule, start through end."""
    definition = _definition(definition)
    backward = definition.stub in (StubConvention.SHORT_INITIAL, StubConvention.LONG_INITIAL)
    anchor = definition.end if backward else definition.start
    limit = definition.start if backward else definition.end
    direction = -1 if backward else 1
    dates = [anchor]
    rolled = None
    for k in range(1, _MAX_PERIODS + 1):
        rolled = _roll_from(definition, anchor, direction * k)
        if (rolled <= limit) if backward else (rolled >= limit):
            break
        dates.append(rolled)
    if rolled is None or ((rolled > limit) if backward else (rolled < limit)):
        raise ValueError(
            "Schedule from {} to {} exceeds {} periods".format(
                definition.start, definition.end, _MAX_PERIODS
            )
        )
    if rolled != limit:
        if definition.stub == StubConvention.NONE:
            raise ValueError(
                "Schedule end {} is not on the {} cycle from {} and the stub convention is NONE".format(
                    definition.end, definition.frequency.name, definition.start
                )
            )
        if definition.stub in (StubConvention.LONG_FINAL, StubConvention.LONG_INITIAL):
            if len(dates) > 1:
                dates.pop()
    dates.append(limit)
    if backward:
        dates.reverse()
    return dates


def _definition(definition) -> ScheduleDefinition:
    definition = ScheduleDefinition(
        definition.start,
        definition.end,
        Frequency.of(definition.frequency),
        StubConvention(str(definition.stub).upper().split(".")[-1]),
        RollConvention(str(definition.roll).upper().split(".")[-1]),
        convention(definition.convention),
    )
    if definition.start >= definition.end:
        raise ValueError(
            "schedule start {} must precede end {}".format(definition.start, definition.end)
        )
    if not definition.frequency.tenor.is_month_based and definition.roll != RollConvention.DAY_OF_MONTH:
        raise ValueError(
            "{} applies to month-based frequencies only, not {}".format(
                definition.roll.value, definition.frequency.name
            )
        )
    return definition


def _stub_index(definition, dates):
    if len(dates) < 2:
        return -1
    backward = definition.stub in (StubConvention.SHORT_INITIAL, StubConvention.LONG_INITIAL)
    index = 0 if backward else len(dates) - 2
    period_start, period_end = dates[index], dates[index + 1]
    if backward:
        return -1 if _roll_from(definition, period_end, -1) == period_start else index
    return -1 if _roll_from(definition, period_start, 1) == period_end else index


def schedule(calendar, definition):
    """Generate a periodic schedule on ``calendar`` with path confidence."""
    definition = _definition(definition)
    unadjusted = unadjusted_schedule_dates(definition)
    tracker = _Tracker(calendar)
    adjusted = [_adjust(calendar, date, definition.convention, tracker) for date in unadjusted]
    stub_index = _stub_index(definition, unadjusted)
    periods = tuple(
        SchedulePeriod(unadjusted[i], unadjusted[i + 1], adjusted[i], adjusted[i + 1], i == stub_index)
        for i in range(len(unadjusted) - 1)
    )
    return Schedule(definition, periods, tracker.confidence, tuple(tracker.examined))


def last_business_day_of_month_detailed(calendar, date):
    tracker = _Tracker(calendar)
    tracker.require_resolved(date)
    result = _last_business_day(calendar, date, tracker)
    return tracker.result(
        date,
        result,
        DateOperation.LAST_BUSINESS_DAY_OF_MONTH,
    )


def _adjust(calendar, date, convention_value, tracker):
    if convention_value == BusinessDayConvention.UNADJUSTED:
        tracker.identity(date)
        return date
    first_direction = (
        1
        if convention_value
        in (BusinessDayConvention.FOLLOWING, BusinessDayConvention.MODIFIED_FOLLOWING)
        else -1
    )
    first = _search(calendar, date, first_direction, tracker)
    modified = convention_value in (
        BusinessDayConvention.MODIFIED_FOLLOWING,
        BusinessDayConvention.MODIFIED_PRECEDING,
    )
    return (
        _search(calendar, date, -first_direction, tracker)
        if modified and (first.year, first.month) != (date.year, date.month)
        else first
    )


def _search(calendar, date, direction, tracker):
    candidate = date
    step = _dt.timedelta(days=direction)
    for _ in range(366):
        if tracker.is_business_day(candidate):
            return candidate
        candidate += step
    raise RuntimeError("No business day within 366 days of {}".format(date))


def _last_business_day(calendar, date, tracker):
    month_end = _dt.date(date.year, date.month, _calendar.monthrange(date.year, date.month)[1])
    candidate = month_end
    while (candidate.year, candidate.month) == (date.year, date.month):
        if tracker.is_business_day(candidate):
            return candidate
        candidate -= _dt.timedelta(days=1)
    raise RuntimeError("No business day in {:04d}-{:02d}".format(date.year, date.month))


def _add_months(date, months):
    index = date.year * 12 + (date.month - 1) + months
    year, month_index = divmod(index, 12)
    month = month_index + 1
    day = min(date.day, _calendar.monthrange(year, month)[1])
    return _dt.date(year, month, day)
