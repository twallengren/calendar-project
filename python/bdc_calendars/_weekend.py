"""
Effective-dated weekend policies.

Weekend rows are not shipped in the data files (they would multiply their size
by an order of magnitude for no information); they are reconstructed here from
the calendar's declared policy, which the sync script verifies reproduces the
generated weekend rows exactly.

The semantics mirror ``com.bdc.model.WeekendPolicy``: for a given date the
*last* period in the list that covers it decides which weekdays are weekend
days, and a date covered by no period has no weekend days.
"""

from __future__ import annotations

import datetime as _dt
from typing import Any, Dict, FrozenSet, List, Mapping, Optional, Sequence

__all__ = ["WeekendPeriod", "WeekendPolicy", "DAY_NAMES"]

# date.weekday(): Monday == 0 ... Sunday == 6
DAY_NAMES = (
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
    "SUNDAY",
)

_DAY_INDEX: Dict[str, int] = {name: i for i, name in enumerate(DAY_NAMES)}


def _parse_day(name: str) -> int:
    try:
        return _DAY_INDEX[name.strip().upper()]
    except KeyError:  # pragma: no cover - guards malformed data files
        raise ValueError("Unknown weekday: {!r}".format(name)) from None


def _parse_date(value: Optional[str]) -> Optional[_dt.date]:
    if value is None or value == "":
        return None
    return _dt.date.fromisoformat(value)


class WeekendPeriod:
    """One effective-dated slice of a weekend policy."""

    __slots__ = ("days", "start", "end")

    def __init__(
        self,
        days: Sequence[int],
        start: Optional[_dt.date] = None,
        end: Optional[_dt.date] = None,
    ) -> None:
        self.days: FrozenSet[int] = frozenset(days)
        self.start = start
        self.end = end

    def contains(self, date: _dt.date) -> bool:
        if self.start is not None and date < self.start:
            return False
        if self.end is not None and date > self.end:
            return False
        return True

    def __repr__(self) -> str:  # pragma: no cover - debugging aid
        return "WeekendPeriod(days={}, start={}, end={})".format(
            sorted(DAY_NAMES[d] for d in self.days), self.start, self.end
        )


class WeekendPolicy:
    """A calendar's weekend definition, possibly varying over time."""

    __slots__ = ("periods", "days")

    def __init__(self, periods: Sequence[WeekendPeriod]) -> None:
        self.periods: List[WeekendPeriod] = list(periods)
        if not self.periods:
            self.periods = [WeekendPeriod(())]
        union: set = set()
        for period in self.periods:
            union |= period.days
        self.days: FrozenSet[int] = frozenset(union)

    @classmethod
    def from_json(cls, payload: Optional[Mapping[str, Any]]) -> "WeekendPolicy":
        """Builds a policy from the ``weekend_policy`` block of ``metadata.json``."""
        if not payload:
            return cls([])
        raw_periods = payload.get("periods") or []
        if raw_periods:
            periods = [
                WeekendPeriod(
                    [_parse_day(d) for d in (p.get("days") or ())],
                    _parse_date(p.get("from")),
                    _parse_date(p.get("to")),
                )
                for p in raw_periods
            ]
        else:
            periods = [WeekendPeriod([_parse_day(d) for d in (payload.get("days") or ())])]
        return cls(periods)

    def days_on(self, date: _dt.date) -> FrozenSet[int]:
        """The weekend weekdays in effect on ``date`` (last matching period wins)."""
        for period in reversed(self.periods):
            if period.contains(date):
                return period.days
        return frozenset()

    def is_weekend(self, date: _dt.date) -> bool:
        return date.weekday() in self.days_on(date)

    def is_effective_dated(self) -> bool:
        if len(self.periods) > 1:
            return True
        only = self.periods[0]
        return only.start is not None or only.end is not None

    def __repr__(self) -> str:  # pragma: no cover - debugging aid
        return "WeekendPolicy({!r})".format(self.periods)
