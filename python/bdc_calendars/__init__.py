"""
bdc-calendars: business-day and trading calendars with zero dependencies.

The data is generated from the YAML specs in the `calendar-project
<https://github.com/twallengren/calendar-project>`_ repository and shipped
inside this wheel, so queries are offline, deterministic and reproducible for a
given package version.

    >>> import datetime, bdc_calendars
    >>> nyse = bdc_calendars.get_calendar("XNYS")
    >>> nyse.is_business_day(datetime.date(2021, 12, 31))
    True
    >>> nyse.close_time(datetime.date(2025, 7, 3))
    datetime.time(13, 0)
    >>> nyse.status(datetime.date(2028, 6, 1))
    'PROJECTED'

Versioning
----------
``__version__`` is ``0.<data major>.<data minor>``: it tracks the release
version of the calendar data, which is exposed in full as :data:`data_version`
together with the :data:`data_git_sha` it was generated from.
"""

from ._loader import Event
from ._version import DATA_GENERATION_DATE, DATA_GIT_SHA, DATA_VERSION, __version__
from ._weekend import WeekendPeriod, WeekendPolicy
from .calendar import (
    CONFIRMED,
    PROJECTED,
    UNKNOWN,
    BusinessCalendar,
    DateRange,
    JointCalendar,
    SingleCalendar,
    get_calendar,
    get_joint_calendar,
    list_aliases,
    list_calendars,
)
from .errors import BdcCalendarError, CalendarNotFoundError, OutsideCoverageError

#: Release version of the bundled calendar data, e.g. ``"11.0.0"``.
data_version = DATA_VERSION

#: Git SHA of the calendar-project commit the data was generated from.
data_git_sha = DATA_GIT_SHA

#: Date the bundled data was generated, ``YYYY-MM-DD``.
data_generation_date = DATA_GENERATION_DATE

__all__ = [
    "__version__",
    "data_version",
    "data_git_sha",
    "data_generation_date",
    "get_calendar",
    "get_joint_calendar",
    "list_calendars",
    "list_aliases",
    "BusinessCalendar",
    "SingleCalendar",
    "JointCalendar",
    "DateRange",
    "Event",
    "WeekendPolicy",
    "WeekendPeriod",
    "BdcCalendarError",
    "CalendarNotFoundError",
    "OutsideCoverageError",
    "CONFIRMED",
    "PROJECTED",
    "UNKNOWN",
]
