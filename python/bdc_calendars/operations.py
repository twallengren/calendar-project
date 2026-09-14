"""Financial date conventions shared by single and joint business calendars."""

from __future__ import annotations

import calendar as _calendar
import datetime as _dt
from enum import Enum
from typing import NamedTuple, Optional, Tuple


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
        if assessment.effective_confidence == "UNKNOWN":
            self.confidence = "UNKNOWN"
        elif assessment.effective_confidence == "PROJECTED" and self.confidence == "CONFIRMED":
            self.confidence = "PROJECTED"
        if require:
            self.calendar._require_resolved(date)

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
