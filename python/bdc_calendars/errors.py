"""Errors raised by :mod:`bdc_calendars`."""

from __future__ import annotations

import datetime as _dt
from typing import Optional

__all__ = [
    "BdcCalendarError",
    "CalendarNotFoundError",
    "OutsideCoverageError",
]


class BdcCalendarError(Exception):
    """Base class for every error this package raises."""


class CalendarNotFoundError(BdcCalendarError, KeyError):
    """No calendar (or alias) with the requested id is bundled with this release."""

    def __init__(self, calendar_id: str, known: Optional[list] = None) -> None:
        self.calendar_id = calendar_id
        self.known = list(known or [])
        message = "Unknown calendar id: {!r}".format(calendar_id)
        if self.known:
            message += ". Known ids: " + ", ".join(self.known)
        BdcCalendarError.__init__(self, message)
        self.args = (message,)

    def __str__(self) -> str:  # KeyError would repr() the message otherwise
        return self.args[0]


class OutsideCoverageError(BdcCalendarError, ValueError):
    """
    The date falls outside the window a calendar can answer for.

    Outside a calendar's range the absence of a closure row means "not known",
    never "open", so every operation except :meth:`BusinessCalendar.status`
    refuses to answer rather than implying a trading day. This mirrors
    ``OutsideCoverageException`` in the Java toolchain and, like it, is an
    invalid-argument error (:class:`ValueError`).
    """

    def __init__(
        self,
        calendar_id: str,
        date: _dt.date,
        range_from: _dt.date,
        range_to: _dt.date,
    ) -> None:
        self.calendar_id = calendar_id
        self.date = date
        self.range_from = range_from
        self.range_to = range_to
        message = "{} is outside the covered range of {} ({} to {})".format(
            date, calendar_id, range_from, range_to
        )
        ValueError.__init__(self, message)
