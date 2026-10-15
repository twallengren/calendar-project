"""
SPECIAL_SESSION events: trading sessions on dates that are not business days.

Ported from ``core/src/test/java/com/bdc/stream/SpecialSessionTest.java``. No
bundled calendar has a special session yet, so these run on synthetic data fed
through the same CSV loader as the bundled files.
"""

from __future__ import annotations

import copy
import datetime as dt
import pickle

import pytest

import bdc_calendars as bdc
from bdc_calendars._loader import CalendarData, _parse_events
from bdc_calendars.calendar import JointCalendar, SingleCalendar

HOLIDAY = dt.date(2024, 11, 1)  # Friday
SATURDAY = dt.date(2024, 3, 2)
MONDAY = dt.date(2024, 3, 4)
KOLKATA = "Asia/Kolkata"
HEADER = "date,type,description,key,source_module,observed_from,close_time,status,open_time"
SPAN = {"from": "2024-01-01", "to": "2024-12-31"}
WEEKENDS = {"days": ["SATURDAY", "SUNDAY"]}


def _calendar(rows, calendar_id="CAL", timezone=KOLKATA, quality=None):
    metadata = {"range_start": "2024-01-01", "range_end": "2024-12-31", "weekend_policy": WEEKENDS}
    if timezone:
        metadata["timezone"] = timezone
    if quality:
        metadata["coverage"] = {"quality": quality}
    text = "\n".join([HEADER] + list(rows)) + "\n"
    return SingleCalendar(CalendarData(calendar_id, metadata, _parse_events(text)))


def _quality(scheduled="VERIFIED", unscheduled="VERIFIED", early="VERIFIED"):
    return [
        dict(scope="SCHEDULED_CLOSURES", **SPAN, quality=scheduled, evidence_ids=["list"]),
        dict(scope="UNSCHEDULED_EXCEPTIONS", **SPAN, quality=unscheduled, evidence_ids=[]),
        dict(scope="EARLY_CLOSES", **SPAN, quality=early, evidence_ids=[]),
    ]


def _closed(day, description="Festival"):
    return "{},CLOSED,{},festival,module:fixture,,,CONFIRMED,".format(day, description)


def _session(day, key, open_time, close_time, status="CONFIRMED"):
    return "{},SPECIAL_SESSION,{},{},module:fixture,,{},{},{}".format(
        day, key, key, close_time, status, open_time
    )


def test_a_session_on_a_holiday_makes_a_trading_day_but_not_a_business_day():
    calendar = _calendar([_closed(HOLIDAY), _session(HOLIDAY, "evening", "18:00", "19:00")])

    assert calendar.is_business_day(HOLIDAY) is False
    assert calendar.is_trading_day(HOLIDAY) is True
    assert calendar.special_sessions(HOLIDAY) == [
        bdc.SpecialSession(
            "CAL", KOLKATA, dt.time(18), dt.time(19), "evening", "evening", "CONFIRMED"
        )
    ]
    # Date arithmetic never lands on it, and its close is not an early close.
    assert calendar.next_business_day(HOLIDAY - dt.timedelta(days=1)) == dt.date(2024, 11, 4)
    assert calendar.close_time(HOLIDAY) is None
    assert calendar.is_early_close(HOLIDAY) is False
    assert calendar.member_closes(HOLIDAY) == []
    assert calendar.assessment(HOLIDAY).scheduled_state == "CLOSED"


def test_a_session_on_a_weekend_day_is_a_trading_day():
    calendar = _calendar([_session(SATURDAY, "drill", "09:15", "12:30")])

    assert calendar.is_business_day(SATURDAY) is False
    assert calendar.is_trading_day(SATURDAY) is True
    assert calendar.is_trading_day(SATURDAY + dt.timedelta(days=1)) is False
    assert len(calendar.special_sessions(SATURDAY)) == 1
    # The reconstructed WEEKEND row coexists with the session.
    assert sorted(e.type for e in calendar.events_on(SATURDAY)) == ["SPECIAL_SESSION", "WEEKEND"]


def test_ordinary_days_answer_as_before():
    calendar = _calendar([])
    assert calendar.is_trading_day(MONDAY) is True
    assert calendar.is_trading_day(SATURDAY) is False
    assert calendar.special_sessions(MONDAY) == []


def test_sessions_are_ordered_by_open_then_close_time():
    calendar = _calendar(
        [
            _session(SATURDAY, "second", "11:30", "12:30"),
            _session(SATURDAY, "first-long", "09:15", "11:00"),
            _session(SATURDAY, "first", "09:15", "10:00"),
        ]
    )
    assert [s.key for s in calendar.special_sessions(SATURDAY)] == ["first", "first-long", "second"]


def test_trading_day_needs_the_business_day_scopes_and_sessions_need_all_scopes():
    calendar = _calendar(
        [_closed(HOLIDAY), _session(HOLIDAY, "evening", "18:00", "19:00")],
        quality=_quality(unscheduled="PROJECTED", early="INCOMPLETE"),
    )

    assert calendar.is_trading_day(HOLIDAY) is True
    with pytest.raises(bdc.UnresolvedDateError) as caught:
        calendar.special_sessions(HOLIDAY)
    assert caught.value.incomplete_scopes == ["EARLY_CLOSES"]


def test_an_unresolved_business_day_scope_blocks_the_trading_day_question():
    calendar = _calendar(
        [_session(SATURDAY, "drill", "09:15", "12:30")], quality=_quality(unscheduled="INCOMPLETE")
    )
    with pytest.raises(bdc.UnresolvedDateError) as caught:
        calendar.is_trading_day(SATURDAY)
    assert caught.value.incomplete_scopes == ["UNSCHEDULED_EXCEPTIONS"]


def test_outside_coverage_raises():
    calendar = _calendar([])
    with pytest.raises(bdc.OutsideCoverageError):
        calendar.is_trading_day(dt.date(2025, 1, 1))
    with pytest.raises(bdc.OutsideCoverageError):
        calendar.special_sessions(dt.date(2025, 1, 1))


def test_a_joint_trading_day_needs_every_member_to_trade():
    india = _calendar([_closed(HOLIDAY), _session(HOLIDAY, "evening", "18:00", "19:00")])
    other = _calendar([], calendar_id="OTHER", timezone=None)
    shut = _calendar([_closed(HOLIDAY, "Shut")], calendar_id="SHUT", timezone=None)

    assert JointCalendar([india, other]).is_trading_day(HOLIDAY) is True
    assert JointCalendar([india, shut]).is_trading_day(HOLIDAY) is False
    assert JointCalendar([india, other]).is_business_day(HOLIDAY) is False
    sessions = JointCalendar([other, india]).special_sessions(HOLIDAY)
    assert [(s.calendar_id, s.timezone, s.key) for s in sessions] == [("CAL", KOLKATA, "evening")]
    # Joint events keep the open time through the member relabelling.
    (joint_session,) = [
        e for e in JointCalendar([india, other]).events_on(HOLIDAY) if e.type == "SPECIAL_SESSION"
    ]
    assert joint_session.source_module == "CAL/module:fixture"
    assert joint_session.open_time == dt.time(18)


def test_joint_sessions_are_listed_in_member_order():
    first = _calendar([_session(SATURDAY, "late", "15:00", "16:00")], calendar_id="FIRST")
    second = _calendar(
        [_session(SATURDAY, "early", "09:00", "10:00")], calendar_id="SECOND", timezone="Asia/Tokyo"
    )
    sessions = JointCalendar([first, second]).special_sessions(SATURDAY)
    assert [(s.calendar_id, s.timezone, s.key) for s in sessions] == [
        ("FIRST", KOLKATA, "late"),
        ("SECOND", "Asia/Tokyo", "early"),
    ]
    assert JointCalendar([first, second]).is_trading_day(SATURDAY) is True


def test_joint_refuses_outside_coverage_and_unresolved_sessions():
    joint = JointCalendar([_calendar([]), _calendar([], "GAP", quality=_quality(early="INCOMPLETE"))])
    with pytest.raises(bdc.OutsideCoverageError):
        joint.is_trading_day(dt.date(2025, 1, 1))
    with pytest.raises(bdc.OutsideCoverageError):
        joint.special_sessions(dt.date(2025, 1, 1))
    assert joint.is_trading_day(MONDAY) is True
    with pytest.raises(bdc.UnresolvedDateError) as caught:
        joint.special_sessions(MONDAY)
    assert caught.value.incomplete_scopes == ["EARLY_CLOSES"]
    blocked = JointCalendar([_calendar([]), _calendar([], "GAP", quality=_quality(unscheduled="INCOMPLETE"))])
    with pytest.raises(bdc.UnresolvedDateError):
        blocked.is_trading_day(MONDAY)


def test_event_tuples_are_unchanged_and_open_time_is_an_attribute():
    calendar = _calendar([_closed(HOLIDAY), _session(HOLIDAY, "evening", "18:00", "19:00")])
    closed, session = calendar.events_on(HOLIDAY)

    assert type(closed) is bdc.Event and closed.open_time is None
    assert isinstance(session, bdc.Event) and isinstance(session, bdc.SpecialSessionEvent)
    assert len(closed) == len(session) == 8
    date, kind, _description, key, _module, _observed, close_time, status = session
    assert (date, kind, key, close_time, status) == (
        HOLIDAY, "SPECIAL_SESSION", "evening", dt.time(19), "CONFIRMED"
    )
    assert session.open_time == dt.time(18)
    assert "open_time" not in session._asdict()
    # The open time takes part in equality and hashing, and survives copies.
    later = session._replace(open_time=dt.time(18, 15))
    assert later != session and later.open_time == dt.time(18, 15)
    assert bdc.Event(*session) != session and session != bdc.Event(*session)
    assert len({session, later, bdc.Event(*session)}) == 3
    assert pickle.loads(pickle.dumps(session)) == session
    assert copy.deepcopy(session).open_time == dt.time(18)
    assert "open_time=datetime.time(18, 0)" in repr(session)


def test_the_loader_validates_session_times():
    with pytest.raises(ValueError):
        _parse_events(HEADER + "\n2024-11-01,SPECIAL_SESSION,No times,k,m,,,CONFIRMED,\n")
    with pytest.raises(ValueError):
        _parse_events(HEADER + "\n" + _session(HOLIDAY, "x", "19:00", "18:00") + "\n")
    with pytest.raises(ValueError):
        _parse_events(HEADER + "\n2024-11-01,CLOSED,Closed,k,m,,,CONFIRMED,12:00\n")


def test_the_csv_reader_reads_open_time_by_header_name():
    events = _parse_events(
        "\n".join(
            [
                "open_time,date,type,description,key,source_module,observed_from,close_time,status",
                ",2024-11-01,CLOSED,Festival,festival,m,,,CONFIRMED",
                "18:00,2024-11-01,SPECIAL_SESSION,Evening,evening,m,,19:00,CONFIRMED",
            ]
        )
    )
    assert events[0].open_time is None
    assert (events[1].open_time, events[1].close_time) == (dt.time(18), dt.time(19))
    # Pre-2.1 data has no open_time column at all.
    legacy = _parse_events(
        "date,type,description,key,source_module,observed_from,close_time,status\n"
        "2024-11-01,CLOSED,Festival,festival,m,,,CONFIRMED\n"
    )
    assert legacy[0].open_time is None


def test_event_details_match_special_session_rows_by_open_time():
    rows = [
        _closed(HOLIDAY),
        _session(HOLIDAY, "evening", "18:00", "19:00"),
        _session(HOLIDAY, "evening", "17:00", "19:00"),
    ]
    common = {"date": "2024-11-01", "source_module": "module:fixture", "status": "CONFIRMED"}
    session = dict(common, type="SPECIAL_SESSION", description="evening", key="evening", close_time="19:00")
    details = [
        dict(common, type="CLOSED", description="Festival", key="festival", evidence_ids=["c"]),
        dict(session, open_time="17:00", evidence_ids=["early"]),
        dict(session, open_time="18:00", evidence_ids=["late"]),
    ]
    metadata = {
        "range_start": "2024-01-01",
        "range_end": "2024-12-31",
        "timezone": KOLKATA,
        "event_details": details,
    }
    text = "\n".join([HEADER] + rows) + "\n"
    calendar = SingleCalendar(CalendarData("CAL", metadata, _parse_events(text)))
    evidence = {
        d.event.open_time: d.evidence_ids
        for d in calendar.event_details_on(HOLIDAY)
        if d.event.type == "SPECIAL_SESSION"
    }
    assert evidence == {dt.time(18): ["late"], dt.time(17): ["early"]}


def test_assessment_events_carry_open_time_only_on_sessions():
    from bdc_calendars.mcp.server import _assessment_dict, _special_session_dict

    calendar = _calendar([_closed(HOLIDAY), _session(HOLIDAY, "evening", "18:00", "19:00")])
    events = _assessment_dict(calendar.assessment(HOLIDAY))["events"]
    assert "open_time" not in events[0]
    assert events[1]["open_time"] == "18:00"
    assert _special_session_dict(calendar.special_sessions(HOLIDAY)[0]) == {
        "calendar_id": "CAL",
        "timezone": KOLKATA,
        "open_time": "18:00",
        "close_time": "19:00",
        "key": "evening",
        "description": "evening",
        "status": "CONFIRMED",
    }

