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
    date: _dt.date
    state: str
    scheduled_state: str
    effective_confidence: str
    completeness: Dict[str, str]
    evidence_ids: List[str]
    events: List[EventDetails]
