import datetime as dt

import pytest

import bdc_calendars as bdc
from bdc_calendars._loader import CalendarData, Event, _parse_events
from bdc_calendars.calendar import JointCalendar, SingleCalendar


START = dt.date(2020, 1, 1)
END = dt.date(2030, 12, 31)


def calendar(events=(), *, calendar_id="CAL", timezone=None, quality=None, verified=None):
    coverage = {}
    if quality is not None:
        coverage["quality"] = quality
    if verified is not None:
        coverage["verified_through"] = str(verified)
    metadata = {
        "range_start": str(START),
        "range_end": str(END),
        "weekend_policy": {"days": []},
        "timezone": timezone,
        "coverage": coverage,
    }
    return SingleCalendar(CalendarData(calendar_id, metadata, list(events)))


def closed(day):
    return Event(day, "CLOSED", "Closed", source_module="fixture")


def early(day, time):
    return Event(day, "EARLY_CLOSE", "Early", source_module="fixture", close_time=time)


def test_modified_following_retains_failed_direction_and_path_confidence():
    day = dt.date(2021, 1, 31)
    cal = calendar(
        [closed(day)] + [closed(dt.date(2021, 2, n)) for n in range(1, 5)],
        verified=day,
    )

    assert cal.adjust(day, bdc.BusinessDayConvention.FOLLOWING) == dt.date(2021, 2, 5)
    result = cal.adjust_detailed(day, bdc.BusinessDayConvention.MODIFIED_FOLLOWING)

    assert result.result_date == dt.date(2021, 1, 30)
    assert result.effective_confidence == "PROJECTED"
    assert result.examined_dates == (
        day,
        dt.date(2021, 2, 1),
        dt.date(2021, 2, 2),
        dt.date(2021, 2, 3),
        dt.date(2021, 2, 4),
        dt.date(2021, 2, 5),
        dt.date(2021, 1, 30),
    )


def test_modified_preceding_reverses_across_month_boundary():
    day = dt.date(2021, 3, 1)
    cal = calendar(
        [closed(day)] + [closed(dt.date(2021, 2, n)) for n in (28, 27, 26)]
    )
    result = cal.adjust_detailed(day, "MODIFIED_PRECEDING")
    assert result.result_date == dt.date(2021, 3, 2)
    assert result.examined_dates == (
        day,
        dt.date(2021, 2, 28),
        dt.date(2021, 2, 27),
        dt.date(2021, 2, 26),
        dt.date(2021, 2, 25),
        dt.date(2021, 3, 2),
    )


def test_modified_following_same_month_next_year_still_reverses():
    source = dt.date(2021, 1, 31)
    last_closed = dt.date(2022, 1, 1)
    closed_days = []
    day = source
    while day <= last_closed:
        closed_days.append(closed(day))
        day += dt.timedelta(days=1)
    result = calendar(closed_days).adjust_detailed(source, "MODIFIED_FOLLOWING")
    assert result.result_date == dt.date(2021, 1, 30)
    assert dt.date(2022, 1, 2) in result.examined_dates
    assert result.examined_dates[-1] == dt.date(2021, 1, 30)


def test_unknown_first_direction_cannot_be_hidden_by_modified_reversal():
    day = dt.date(2021, 1, 31)
    quality = [
        {
            "scope": scope,
            "from": str(START),
            "to": str(day),
            "quality": "VERIFIED",
            "evidence_ids": ["fixture"],
        }
        for scope in ("SCHEDULED_CLOSURES", "EARLY_CLOSES", "UNSCHEDULED_EXCEPTIONS")
    ]
    cal = calendar([closed(day)], quality=quality)
    with pytest.raises(bdc.UnresolvedDateError):
        cal.adjust(day, bdc.BusinessDayConvention.MODIFIED_FOLLOWING)


def test_identity_operations_do_not_claim_business_state():
    day = dt.date(2040, 1, 1)
    cal = calendar()
    result = cal.business_day_offset_detailed(day, 0)
    assert result.result_date == day
    assert result.effective_confidence == "UNKNOWN"
    assert result.examined_dates == (day,)
    assert cal.adjust_detailed(day, "UNADJUSTED").effective_confidence == "UNKNOWN"


def test_month_advance_clips_and_only_preserves_actual_business_month_end():
    cal = calendar(
        [
            closed(dt.date(2021, 1, 31)),
            closed(dt.date(2021, 2, 27)),
            closed(dt.date(2021, 2, 28)),
        ]
    )
    assert cal.advance_months(dt.date(2021, 1, 30), 1, "FOLLOWING", True) == dt.date(
        2021, 2, 26
    )
    assert cal.advance_months(dt.date(2021, 1, 31), 1, "FOLLOWING", True) == dt.date(
        2021, 3, 1
    )
    assert cal.advance_months(dt.date(2024, 3, 31), -1, "UNADJUSTED") == dt.date(
        2024, 2, 29
    )
    assert cal.last_business_day_of_month(dt.date(2021, 2, 10)) == dt.date(2021, 2, 26)


def test_last_business_day_cannot_escape_fully_closed_month():
    events = [closed(dt.date(2021, 2, day)) for day in range(1, 29)]
    with pytest.raises(RuntimeError, match="2021-02"):
        calendar(events).last_business_day_of_month(dt.date(2021, 2, 10))


def test_nonzero_unadjusted_month_advance_requires_resolved_destination():
    with pytest.raises(bdc.OutsideCoverageError):
        calendar().advance_months(dt.date(2030, 12, 15), 1, "UNADJUSTED")


def test_member_closes_keep_member_timezone_and_joint_unknown_fails():
    day = dt.date(2025, 7, 3)
    ny = calendar([early(day, dt.time(13))], calendar_id="NY", timezone="America/New_York")
    london = calendar([early(day, dt.time(12, 30))], calendar_id="LDN", timezone="Europe/London")
    joint = JointCalendar([ny, london])
    assert joint.member_closes(day) == [
        bdc.MemberClose("NY", "America/New_York", dt.time(13)),
        bdc.MemberClose("LDN", "Europe/London", dt.time(12, 30)),
    ]

    unknown = calendar(
        calendar_id="UNKNOWN",
        quality=[
            {
                "scope": "SCHEDULED_CLOSURES",
                "from": str(START),
                "to": str(END),
                "quality": "INCOMPLETE",
                "evidence_ids": ["gap"],
            }
        ],
    )
    with pytest.raises(bdc.UnresolvedDateError):
        JointCalendar([ny, unknown]).member_closes(day)


def test_csv_reader_preserves_quoted_newlines_quotes_and_duplicate_records():
    text = (
        "date,type,description,key,source_module,observed_from,close_time,status\r\n"
        '2025-01-02,CLOSED,"Line one, says ""hello""\r\nline two",key,module,,,CONFIRMED\r\n'
        '2025-01-02,CLOSED,"Line one, says ""hello""\r\nline two",key,module,,,CONFIRMED\r\n'
    )
    events = _parse_events(text)
    assert len(events) == 2
    assert events[0] == events[1]
    assert events[0].description == 'Line one, says "hello"\r\nline two'


def test_loader_rejects_unknown_as_a_raw_event_status():
    text = "date,type,description,status\n2025-01-02,CLOSED,Closed,UNKNOWN\n"
    with pytest.raises(ValueError, match="CONFIRMED or PROJECTED"):
        _parse_events(text)
