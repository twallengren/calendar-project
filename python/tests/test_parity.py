"""
Holds the Python package to the answers of the Java reference implementation.

The fixtures in ``fixtures/parity_<ID>.json`` are dumped from the same code path
as ``tools query --as-of blessed`` by ``scripts/generate_parity_fixture.sh``.
They record, per calendar:

* every non-weekend event in the published artifact, field for field;
* a deterministic, evenly spaced sample of ~1000 dates across the covered range
  (so ~2000 dates over the two calendars), each with the business-day test,
  next/previous/nth navigation, status, close time, a 31-day business-day count
  and the full event list for that date — weekend rows included, which is what
  makes the weekend reconstruction testable.

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
CALENDARS = ["US-NYSE", "SA-TADAWUL"]


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
        event.close_time.isoformat("auto")[:5] if event.close_time else None,
        event.status,
    ]


def _fixture_event(row: list) -> list:
    # The Java dump writes LocalTime.toString(), i.e. "13:00"; normalise both
    # sides to HH:MM so the comparison is about content, not formatting.
    row = list(row)
    if row[6]:
        row[6] = row[6][:5]
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

        assert calendar.is_business_day(day) is case["b"], day
        assert calendar.status(day) == case["s"], day
        assert calendar.is_early_close(day) is case["ec"], day

        close = calendar.close_time(day)
        assert (close.isoformat("auto")[:5] if close else None) == (
            case["c"][:5] if case["c"] else None
        ), day

        _assert_navigation(calendar, day, case)

        window_end = dt.date.fromisoformat(case["we"])
        assert calendar.business_days_between(day, window_end) == case["cnt"], day

        ours = [_event_tuple(e) for e in calendar.events_on(day)]
        assert ours == [_fixture_event(row) for row in case["ev"]], day


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
            assert call().isoformat() == expected, (day, key)


def test_sample_covers_both_ends_of_the_range(pair):
    calendar, fixture = pair
    sampled = {case["d"] for case in fixture["queries"]}
    assert calendar.range.start.isoformat() in sampled
    # The last sample is within one step of the end; make sure we get near it.
    assert max(sampled) > (calendar.range.end - dt.timedelta(days=400)).isoformat()
