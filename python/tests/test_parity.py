"""
Holds the Python package to the answers of the Java reference implementation.

The fixtures in ``fixtures/parity_<ID>.json`` are dumped from the same code path
as ``tools query --as-of blessed`` by ``scripts/generate_parity_fixture.sh``.
They record, per calendar:

* every non-weekend event in the published artifact, field for field;
* a deterministic, evenly spaced sample of ~1000 dates across the covered range
  (for every bundled calendar), each with the business-day test,
  next/previous/nth navigation, status, close time, a 31-day business-day count
  and the full event list for that date — weekend rows included, which is what
  makes the weekend reconstruction testable;
* enriched assessments and typed coverage failures, including the exact date where traversal stopped.

Regenerate them (and re-run ``scripts/sync_data.py``) whenever the blessed data
changes; a mismatch here means the two implementations have diverged.
"""

from __future__ import annotations

import datetime as dt
import json
import os

import pytest

import bdc_calendars as bdc

FIXTURE_DIR = os.path.join(os.path.dirname(__file__), "fixtures")
CALENDARS = sorted(name[7:-5] for name in os.listdir(FIXTURE_DIR) if name.startswith("parity_") and name.endswith(".json"))


def _load(calendar_id: str) -> dict:
    path = os.path.join(FIXTURE_DIR, "parity_{}.json".format(calendar_id))
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


@pytest.fixture(scope="module", params=CALENDARS)
def pair(request):
    fixture = _load(request.param)
    return bdc.get_calendar(request.param), fixture


def _event_tuple(event) -> list:
    return [
        event.date.isoformat(),
        event.type,
        event.description,
        event.key,
        event.source_module,
        event.observed_from.isoformat() if event.observed_from else None,
        _time(event.close_time),
        event.status,
    ]


def _time(value):
    if value is None:
        return None
    if isinstance(value, str):
        value = dt.time.fromisoformat(value)
    return value.isoformat(timespec="minutes" if value.second == 0 and value.microsecond == 0 else "auto")


def _fixture_event(row: list) -> list:
    row = list(row)
    if row[6]:
        row[6] = _time(row[6])
    return row


def test_identity_and_bounds(pair):
    calendar, fixture = pair
    assert calendar.calendar_id == fixture["calendar_id"]
    assert [calendar.range.start.isoformat(), calendar.range.end.isoformat()] == fixture["range"]
    expected = fixture["verified_through"]
    actual = calendar.verified_through
    assert (actual.isoformat() if actual else None) == expected


def test_data_version_matches_fixture(pair):
    _calendar, fixture = pair
    assert bdc.data_version == fixture["version"]


def test_every_non_weekend_event_matches(pair):
    calendar, fixture = pair
    start, end = calendar.range
    ours = [_event_tuple(e) for e in calendar.events_in_range(start, end) if e.type != "WEEKEND"]
    theirs = [_fixture_event(row) for row in fixture["events"]]
    assert len(ours) == len(theirs)
    assert ours == theirs


def test_sampled_queries_match(pair):
    calendar, fixture = pair
    queries = fixture["queries"]
    assert len(queries) > 500, "fixture looks truncated"

    for case in queries:
        day = dt.date.fromisoformat(case["d"])

        _assert_query(lambda: calendar.is_business_day(day), case["b"], day)
        assert calendar.status(day) == case["s"], day
        _assert_query(lambda: calendar.is_early_close(day), case["ec"], day)

        expected_close = case["c"] if isinstance(case["c"], dict) else _time(case["c"])
        _assert_query(lambda: _time(calendar.close_time(day)), expected_close, day)
        if "assessment" in case:
            from bdc_calendars.mcp.server import _assessment_dict
            actual = _assessment_dict(calendar.assessment(day))
            expected = case["assessment"]
            for event in actual["events"]:
                event.pop("status")
                event["close_time"] = _time(event["close_time"])
            assert actual == expected, day

        _assert_navigation(calendar, day, case)

        window_end = dt.date.fromisoformat(case["we"])
        _assert_query(lambda: calendar.business_days_between(day, window_end), case["cnt"], day)

        ours = [_event_tuple(e) for e in calendar.events_on(day)]
        assert ours == [_fixture_event(row) for row in case["ev"]], day


def _assert_query(call, expected, context):
    if isinstance(expected, dict) and "error" in expected:
        error_type = getattr(bdc, expected["error"])
        with pytest.raises(error_type) as caught:
            call()
        assert type(caught.value) is error_type, context
        assert caught.value.date.isoformat() == expected["date"], context
    else:
        assert call() == expected, context


def test_financial_operations_match(pair):
    calendar, fixture = pair
    if "financial_queries" not in fixture:
        pytest.skip("legacy fixture predates financial operations")

    def normalized(call):
        result = call()._asdict()
        for key in ("original_date", "result_date"):
            result[key] = result[key].isoformat()
        result["examined_dates"] = [day.isoformat() for day in result["examined_dates"]]
        return result

    for case in fixture["financial_queries"]:
        day = dt.date.fromisoformat(case["d"])
        for convention in bdc.BusinessDayConvention:
            _assert_query(
                lambda: normalized(lambda: calendar.adjust_detailed(day, convention)),
                case["adjust_" + convention.value], (day, convention),
            )
            _assert_query(
                lambda: normalized(lambda: calendar.advance_months_detailed(day, 1, convention, False)),
                case["months_" + convention.value], (day, convention),
            )
        for offset in (-5, 0, 5):
            _assert_query(
                lambda: normalized(lambda: calendar.business_day_offset_detailed(day, offset)),
                case["offset_" + str(offset)], (day, offset),
            )
        _assert_query(
            lambda: normalized(lambda: calendar.advance_months_detailed(day, -1, "MODIFIED_FOLLOWING", True)),
            case["eom"], (day, "eom"),
        )
        _assert_query(
            lambda: normalized(lambda: calendar.last_business_day_of_month_detailed(day)),
            case["last"], (day, "last"),
        )


def _assert_navigation(calendar, day: dt.date, case: dict) -> None:
    for key, call in (
        ("n", lambda: calendar.next_business_day(day)),
        ("p", lambda: calendar.previous_business_day(day)),
        ("f5", lambda: calendar.add_business_days(day, 5)),
        ("b5", lambda: calendar.add_business_days(day, -5)),
    ):
        expected = case[key]
        if expected is None:
            # Java refused (the bounded search walked out of the covered range).
            with pytest.raises((bdc.OutsideCoverageError, RuntimeError)):
                call()
        else:
            _assert_query(lambda: call().isoformat(), expected, (day, key))


def test_sample_covers_both_ends_of_the_range(pair):
    calendar, fixture = pair
    sampled = {case["d"] for case in fixture["queries"]}
    assert calendar.range.start.isoformat() in sampled
    # The last sample is within one step of the end; make sure we get near it.
    assert max(sampled) > (calendar.range.end - dt.timedelta(days=400)).isoformat()
