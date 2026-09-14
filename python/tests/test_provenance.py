import datetime as dt

import pytest

from bdc_calendars._loader import CalendarData, Event
from bdc_calendars.calendar import SingleCalendar
from bdc_calendars.trust import NativeDate


DAY = dt.date(2023, 9, 18)
EVENT = Event(DAY, "CLOSED", "New year", "new_year", "fixture", dt.date(2023, 9, 16))


def metadata():
    row = dict(date=str(DAY), type="CLOSED", description="New year", key="new_year",
               source_module="fixture", observed_from="2023-09-16", status="CONFIRMED",
               evidence_ids=["a"], observation_lineage=["2023-09-16", "2023-09-18"],
               nominal_native_date=dict(chronology_id="HEBREW", year=5784, month_code="TISHRI", day=1),
               chronology_profile="HEBREW_ARITHMETIC", chronology_provider="ICU4J 78.3")
    return dict(range_start=str(DAY), range_end=str(DAY), event_details=[row, dict(row, evidence_ids=["b"])])


def test_complete_duplicate_occurrences_retain_individual_native_evidence():
    calendar = SingleCalendar(CalendarData("TEST", metadata(), [EVENT, EVENT]))
    result = calendar.assessment(DAY)
    assert len(result.events) == 2
    assert result.evidence_ids == ["a", "b"]
    assert result.events[0].nominal_native_date == NativeDate("HEBREW", 5784, "TISHRI", 1)
    assert result.events[0].observation_lineage == [dt.date(2023, 9, 16), DAY]


@pytest.mark.parametrize("rows", [[], [EVENT], [EVENT, EVENT, EVENT]])
def test_missing_and_extra_provenance_are_rejected(rows):
    with pytest.raises(ValueError, match="provenance"):
        CalendarData("TEST", metadata(), rows)


def test_closed_first_joint_member_cannot_hide_unknown_second_member():
    from bdc_calendars.calendar import JointCalendar
    from bdc_calendars.errors import UnresolvedDateError

    closed = SingleCalendar(CalendarData("CLOSED", metadata(), [EVENT, EVENT]))
    unknown_metadata = dict(range_start=str(DAY), range_end=str(DAY), coverage=dict(quality=[
        dict(scope="SCHEDULED_CLOSURES", **{"from": str(DAY), "to": str(DAY)}, quality="INCOMPLETE", evidence_ids=[])]))
    unknown = SingleCalendar(CalendarData("UNKNOWN", unknown_metadata, []))
    joint = JointCalendar([closed, unknown])
    with pytest.raises(UnresolvedDateError):
        joint.is_business_day(DAY)
