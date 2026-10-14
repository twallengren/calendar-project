"""Additive trust and completeness values for enriched calendar queries."""

from __future__ import annotations

import datetime as _dt
from typing import TYPE_CHECKING, Dict, List, NamedTuple, Optional

if TYPE_CHECKING:
    from ._loader import Event

SCHEDULED_CLOSURES = "SCHEDULED_CLOSURES"
EARLY_CLOSES = "EARLY_CLOSES"
UNSCHEDULED_EXCEPTIONS = "UNSCHEDULED_EXCEPTIONS"
COMPLETENESS_SCOPES = (SCHEDULED_CLOSURES, EARLY_CLOSES, UNSCHEDULED_EXCEPTIONS)

#: The question being asked about a date decides which scopes must be resolved first.
#: Whether a market trades depends only on scheduled closures and unscheduled exceptions:
#: an early close is still a business day, so incomplete early-close evidence cannot change
#: a business-day answer. Session detail (close times) additionally needs early closes.
BUSINESS_DAY_QUESTION = "BUSINESS_DAY"
SESSION_QUESTION = "SESSION"
QUESTION_SCOPES = {
    BUSINESS_DAY_QUESTION: (SCHEDULED_CLOSURES, UNSCHEDULED_EXCEPTIONS),
    SESSION_QUESTION: COMPLETENESS_SCOPES,
}

VERIFIED = "VERIFIED"
PROJECTED = "PROJECTED"
INCOMPLETE = "INCOMPLETE"

OPEN = "OPEN"
CLOSED = "CLOSED"
EARLY_CLOSE = "EARLY_CLOSE"
UNKNOWN = "UNKNOWN"


class NativeDate(NamedTuple):
    chronology_id: str
    year: int
    month_code: str
    day: int


class CoverageInterval(NamedTuple):
    scope: str
    start: _dt.date
    end: _dt.date
    quality: str
    evidence_ids: List[str]

    def contains(self, date: _dt.date) -> bool:
        return self.start <= date <= self.end


class EventDetails(NamedTuple):
    event: Event
    raw_status: str
    effective_status: str
    evidence_ids: List[str]
    nominal_native_date: Optional[NativeDate]
    chronology_profile: Optional[str]
    chronology_provider: Optional[str]
    observation_lineage: List[_dt.date]


class DayAssessment(NamedTuple):
    """
    A non-throwing explanation of one date.

    ``state``/``effective_confidence`` answer the full session question and are
    ``UNKNOWN`` as soon as *any* scope is incomplete. ``business_day_state``
    (``OPEN``, ``CLOSED`` or ``UNKNOWN``, never ``EARLY_CLOSE``) and
    ``business_day_confidence`` answer only "is this a business day?", which
    ignores early-close evidence.
    """

    date: _dt.date
    state: str
    scheduled_state: str
    effective_confidence: str
    completeness: Dict[str, str]
    evidence_ids: List[str]
    events: List[EventDetails]
    business_day_state: str
    business_day_confidence: str

    @property
    def is_business_day_known(self) -> bool:
        return self.business_day_state != UNKNOWN

    def incomplete_scopes(self, question: str = SESSION_QUESTION) -> List[str]:
        """The scopes among ``question``'s that are incomplete on this date."""
        return [
            scope
            for scope in QUESTION_SCOPES[question]
            if self.completeness.get(scope, INCOMPLETE) == INCOMPLETE
        ]
