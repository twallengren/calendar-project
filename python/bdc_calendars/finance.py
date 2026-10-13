"""
Calendar-free financial date vocabulary: tenors, frequencies, stub and roll
conventions, IMM dates and day-count conventions.

Mirrors the Java ``com.bdc.finance`` package field for field; the calendar-
dependent operations (schedules, tenor addition, BUS/252) live in
:mod:`bdc_calendars.operations` and on :class:`bdc_calendars.BusinessCalendar`.
"""

from __future__ import annotations

import calendar as _calendar
import datetime as _dt
import re
from enum import Enum
from typing import NamedTuple, Optional

__all__ = [
    "Tenor",
    "TenorUnit",
    "Frequency",
    "StubConvention",
    "RollConvention",
    "DayCount",
    "imm_date",
    "is_imm",
    "is_quarterly_imm",
    "next_imm",
    "next_quarterly_imm",
    "add_months",
    "roll",
]


class TenorUnit(str, Enum):
    DAYS = "D"
    WEEKS = "W"
    MONTHS = "M"
    YEARS = "Y"
    BUSINESS_DAYS = "B"


_TENOR = re.compile(r"([+-]?\d{1,5})([DWMYB])")
_SETTLEMENT = re.compile(r"T([+-]\d{1,5})")


class Tenor(NamedTuple):
    """A market tenor such as ``3M``, ``2W``, ``1Y``, ``10D`` or ``T+2`` (``2B``)."""

    amount: int
    unit: TenorUnit

    @classmethod
    def parse(cls, text) -> "Tenor":
        if isinstance(text, Tenor):
            return text
        value = str(text).strip().upper()
        settlement = _SETTLEMENT.fullmatch(value)
        if settlement:
            return cls(int(settlement.group(1)), TenorUnit.BUSINESS_DAYS)
        match = _TENOR.fullmatch(value)
        if not match:
            raise ValueError(
                "Not a tenor: {!r} (expected e.g. 3M, 2W, 1Y, 10D, 2B or T+2)".format(text)
            )
        return cls(int(match.group(1)), TenorUnit(match.group(2)))

    @property
    def is_month_based(self) -> bool:
        return self.unit in (TenorUnit.MONTHS, TenorUnit.YEARS)

    @property
    def months(self) -> int:
        if self.unit == TenorUnit.MONTHS:
            return self.amount
        if self.unit == TenorUnit.YEARS:
            return self.amount * 12
        raise ValueError("{} is not month-based".format(self))

    @property
    def days(self) -> int:
        if self.unit == TenorUnit.DAYS:
            return self.amount
        if self.unit == TenorUnit.WEEKS:
            return self.amount * 7
        raise ValueError("{} is not day-based".format(self))

    def __str__(self) -> str:
        return "{}{}".format(self.amount, self.unit.value)


class Frequency(Enum):
    """A regular period frequency with its tenor and periods per year."""

    ANNUAL = (Tenor(1, TenorUnit.YEARS), 1)
    SEMI_ANNUAL = (Tenor(6, TenorUnit.MONTHS), 2)
    QUARTERLY = (Tenor(3, TenorUnit.MONTHS), 4)
    BI_MONTHLY = (Tenor(2, TenorUnit.MONTHS), 6)
    MONTHLY = (Tenor(1, TenorUnit.MONTHS), 12)
    FOUR_WEEKLY = (Tenor(4, TenorUnit.WEEKS), 13)
    BI_WEEKLY = (Tenor(2, TenorUnit.WEEKS), 26)
    WEEKLY = (Tenor(1, TenorUnit.WEEKS), 52)

    def __init__(self, tenor: Tenor, periods_per_year: int) -> None:
        self.tenor = tenor
        self.periods_per_year = periods_per_year

    @classmethod
    def of(cls, value) -> "Frequency":
        if isinstance(value, Frequency):
            return value
        try:
            return cls[str(value).upper()]
        except KeyError:
            tenor = Tenor.parse(value)
            for frequency in cls:
                if frequency.tenor == tenor:
                    return frequency
            raise ValueError("No standard frequency with tenor {}".format(tenor)) from None


class StubConvention(str, Enum):
    NONE = "NONE"
    SHORT_FINAL = "SHORT_FINAL"
    LONG_FINAL = "LONG_FINAL"
    SHORT_INITIAL = "SHORT_INITIAL"
    LONG_INITIAL = "LONG_INITIAL"


class RollConvention(str, Enum):
    DAY_OF_MONTH = "DAY_OF_MONTH"
    EOM = "EOM"
    IMM = "IMM"


def add_months(date: _dt.date, months: int) -> _dt.date:
    """Civil month arithmetic with the day clipped to the destination month."""
    index = date.year * 12 + (date.month - 1) + months
    year, month_index = divmod(index, 12)
    month = month_index + 1
    return _dt.date(year, month, min(date.day, _calendar.monthrange(year, month)[1]))


def imm_date(year: int, month: int) -> _dt.date:
    """The third Wednesday of the month."""
    first = _dt.date(year, month, 1)
    offset = (2 - first.weekday()) % 7  # Wednesday is weekday 2
    return first + _dt.timedelta(days=offset + 14)


def is_imm(date: _dt.date) -> bool:
    return date == imm_date(date.year, date.month)


def is_quarterly_imm(date: _dt.date) -> bool:
    return is_imm(date) and date.month % 3 == 0


def next_imm(date: _dt.date) -> _dt.date:
    """The first IMM date strictly after ``date``."""
    candidate = imm_date(date.year, date.month)
    if candidate <= date:
        following = add_months(date, 1)
        candidate = imm_date(following.year, following.month)
    return candidate


def next_quarterly_imm(date: _dt.date) -> _dt.date:
    candidate = next_imm(date)
    while candidate.month % 3 != 0:
        candidate = next_imm(candidate)
    return candidate


def roll(convention: RollConvention, anchor: _dt.date, months: int) -> _dt.date:
    """The unadjusted date ``months`` months from ``anchor`` under a roll convention."""
    shifted = add_months(anchor, months)
    if convention == RollConvention.DAY_OF_MONTH:
        return shifted
    if convention == RollConvention.EOM:
        return _dt.date(
            shifted.year, shifted.month, _calendar.monthrange(shifted.year, shifted.month)[1]
        )
    return imm_date(shifted.year, shifted.month)


def _is_last_day_of_month(date: _dt.date) -> bool:
    return date.day == _calendar.monthrange(date.year, date.month)[1]


def _thirty_360(start: _dt.date, end: _dt.date, d1: int, d2: int) -> int:
    return 360 * (end.year - start.year) + 30 * (end.month - start.month) + (d2 - d1)


class DayCount(str, Enum):
    """Day-count conventions per the 2006 ISDA Definitions, Section 4.16."""

    ACT_360 = "ACT_360"
    ACT_365F = "ACT_365F"
    ACT_ACT_ISDA = "ACT_ACT_ISDA"
    ACT_ACT_ICMA = "ACT_ACT_ICMA"
    THIRTY_360_BOND = "THIRTY_360_BOND"
    THIRTY_E_360 = "THIRTY_E_360"
    THIRTY_E_360_ISDA = "THIRTY_E_360_ISDA"
    BUS_252 = "BUS_252"

    @classmethod
    def of(cls, value) -> "DayCount":
        if isinstance(value, DayCount):
            return value
        try:
            return cls(str(value).upper().replace("/", "_"))
        except ValueError:
            raise ValueError("unknown day-count convention: {!r}".format(value)) from None

    @property
    def is_calendar_free(self) -> bool:
        return self not in (DayCount.BUS_252, DayCount.ACT_ACT_ICMA)

    def days(self, start: _dt.date, end: _dt.date, end_is_termination: bool = False) -> int:
        """The convention's day count for ``[start, end]``; negative when reversed."""
        if end < start:
            return -self.days(end, start, end_is_termination)
        if self in (
            DayCount.ACT_360,
            DayCount.ACT_365F,
            DayCount.ACT_ACT_ISDA,
            DayCount.ACT_ACT_ICMA,
        ):
            return (end - start).days
        if self == DayCount.THIRTY_360_BOND:
            d1 = min(start.day, 30)
            d2 = 30 if end.day == 31 and d1 == 30 else end.day
            return _thirty_360(start, end, d1, d2)
        if self == DayCount.THIRTY_E_360:
            return _thirty_360(start, end, min(start.day, 30), min(end.day, 30))
        if self == DayCount.THIRTY_E_360_ISDA:
            d1 = 30 if _is_last_day_of_month(start) else start.day
            keep_february = end_is_termination and end.month == 2
            d2 = (
                30
                if end.day == 31 or (_is_last_day_of_month(end) and not keep_february)
                else end.day
            )
            return _thirty_360(start, end, d1, d2)
        raise ValueError("BUS/252 needs a business calendar: use BusinessCalendar.year_fraction")

    def year_fraction(
        self,
        start: _dt.date,
        end: _dt.date,
        end_is_termination: bool = False,
        period_start: Optional[_dt.date] = None,
        period_end: Optional[_dt.date] = None,
        frequency: Optional[Frequency] = None,
    ) -> float:
        """
        The year fraction for ``[start, end]``.

        ``end_is_termination`` matters only to 30E/360 ISDA; ``period_start``,
        ``period_end`` and ``frequency`` are required by ACT/ACT ICMA (the regular
        period the dates lie in, or the notional period of a stub). BUS/252 needs
        a calendar: use :meth:`BusinessCalendar.year_fraction`.
        """
        if self in (DayCount.ACT_360, DayCount.THIRTY_360_BOND, DayCount.THIRTY_E_360):
            return self.days(start, end) / 360.0
        if self == DayCount.ACT_365F:
            return self.days(start, end) / 365.0
        if self == DayCount.THIRTY_E_360_ISDA:
            return self.days(start, end, end_is_termination) / 360.0
        if self == DayCount.ACT_ACT_ISDA:
            return _act_act_isda(start, end)
        if self == DayCount.ACT_ACT_ICMA:
            if period_start is None or period_end is None or frequency is None:
                raise ValueError(
                    "ACT/ACT ICMA needs period_start, period_end and frequency"
                )
            period_days = (period_end - period_start).days
            if period_days <= 0:
                raise ValueError("ACT/ACT ICMA reference period must be non-empty")
            return (end - start).days / (
                float(Frequency.of(frequency).periods_per_year) * period_days
            )
        raise ValueError("BUS/252 needs a business calendar: use BusinessCalendar.year_fraction")


def _act_act_isda(start: _dt.date, end: _dt.date) -> float:
    if end < start:
        return -_act_act_isda(end, start)
    if start.year == end.year:
        return (end - start).days / float(_year_length(start.year))
    fraction = 0.0
    cursor = start
    while cursor.year < end.year:
        next_year = _dt.date(cursor.year + 1, 1, 1)
        fraction += (next_year - cursor).days / float(_year_length(cursor.year))
        cursor = next_year
    return fraction + (end - cursor).days / float(_year_length(end.year))


def _year_length(year: int) -> int:
    return 366 if _calendar.isleap(year) else 365
