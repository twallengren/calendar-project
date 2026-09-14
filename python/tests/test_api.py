"""The API contract from ``spec/SPEC.md``, including its edge semantics."""

from __future__ import annotations

import datetime as dt
import importlib.util

import pytest

import bdc_calendars as bdc

NYSE = "US-NYSE"
TADAWUL = "SA-TADAWUL"

HAS_PANDAS = importlib.util.find_spec("pandas") is not None


@pytest.fixture(scope="module")
def nyse():
    return bdc.get_calendar(NYSE)


@pytest.fixture(scope="module")
def tadawul():
    return bdc.get_calendar(TADAWUL)


# --- Lookup ------------------------------------------------------------------


@pytest.mark.parametrize("spelling", ["US-NYSE", "us-nyse", "XNYS", "xnys", "NYSE", "US_NYSE"])
def test_get_calendar_accepts_ids_and_aliases(spelling):
    assert bdc.get_calendar(spelling).calendar_id == NYSE


def test_get_calendar_accepts_the_tadawul_mic():
    assert bdc.get_calendar("XSAU").calendar_id == TADAWUL


def test_unknown_id_raises():
    with pytest.raises(bdc.CalendarNotFoundError) as excinfo:
        bdc.get_calendar("XXXX")
    assert "XXXX" in str(excinfo.value)


def test_list_calendars_includes_the_markets():
    ids = bdc.list_calendars()
    assert NYSE in ids and TADAWUL in ids
    assert ids == sorted(ids)


def test_aliases_all_resolve():
    for alias, target in bdc.list_aliases().items():
        assert bdc.get_calendar(alias).calendar_id == target


def test_get_calendar_accepts_the_lse_mic():
    assert bdc.get_calendar("XLON").is_business_day(dt.date(2026, 12, 28)) is False


def test_get_calendar_accepts_the_jpx_segment_mic_alias():
    assert bdc.get_calendar("XJPX").calendar_id == "JP-JPX"


def test_every_market_calendar_is_reachable_by_its_mic():
    aliases_by_target = {}
    for alias, target in bdc.list_aliases().items():
        aliases_by_target.setdefault(target, []).append(alias)
    for calendar_id in bdc.list_calendars():
        if bdc.get_calendar(calendar_id).kind != "market":
            continue  # base calendars are building blocks, not tradable venues, and carry no MIC
        mics = [
            alias
            for alias in aliases_by_target.get(calendar_id, [])
            if len(alias) == 4 and alias.isupper() and alias.isalnum()
        ]
        assert mics, "{} has no MIC alias in the bundled manifest".format(calendar_id)
        for mic in mics:
            assert bdc.get_calendar(mic).calendar_id == calendar_id


def test_instances_are_cached(nyse):
    assert bdc.get_calendar("XNYS") is nyse


# --- Business days -----------------------------------------------------------


def test_is_business_day(nyse):
    assert nyse.is_business_day(dt.date(2021, 12, 31)) is True  # Friday, open
    assert nyse.is_business_day(dt.date(2022, 1, 1)) is False  # Saturday
    assert nyse.is_business_day(dt.date(2025, 12, 25)) is False  # Christmas
    assert nyse.is_business_day(dt.date(2025, 7, 3)) is True  # early close still trades


def test_dates_accept_several_spellings(nyse):
    assert nyse.is_business_day("2021-12-31") is True
    assert nyse.is_business_day(dt.datetime(2021, 12, 31, 15, 30)) is True


def test_saudi_weekend_moved_in_2013(tadawul):
    # Friday/Saturday since 2013-06-29; the range starts in 2020, so every
    # Thursday inside it trades and every Friday does not.
    assert tadawul.is_business_day(dt.date(2024, 3, 7)) is True  # Thursday
    assert tadawul.is_business_day(dt.date(2024, 3, 8)) is False  # Friday
    assert tadawul.is_business_day(dt.date(2024, 3, 9)) is False  # Saturday
    assert tadawul.is_business_day(dt.date(2024, 3, 10)) is True  # Sunday


def test_nyse_saturday_trading_before_1952(nyse):
    # Until 1952-05-30 the weekend was Sunday only (with summer-Saturday breaks).
    assert nyse.is_business_day(dt.date(1930, 1, 4)) is True  # Saturday
    assert nyse.is_business_day(dt.date(1930, 1, 5)) is False  # Sunday
    assert nyse.is_business_day(dt.date(1960, 1, 2)) is False  # Saturday, after the change


def test_nyse_summer_saturday_closure(nyse):
    # 1951-06-02 .. 1951-09-29 was a Saturday-and-Sunday period.
    assert nyse.is_business_day(dt.date(1951, 6, 9)) is False  # Saturday, inside
    assert nyse.is_business_day(dt.date(1951, 5, 26)) is True  # Saturday, before


# --- Navigation --------------------------------------------------------------


def test_next_and_previous_are_strict(nyse):
    friday = dt.date(2026, 2, 27)
    assert nyse.next_business_day(friday) == dt.date(2026, 3, 2)
    assert nyse.previous_business_day(friday) == dt.date(2026, 2, 26)
    # A closed date still navigates from where it stands.
    assert nyse.next_business_day(dt.date(2025, 12, 25)) == dt.date(2025, 12, 26)


def test_nth_never_counts_the_starting_date(nyse):
    friday = dt.date(2026, 2, 27)
    assert nyse.add_business_days(friday, 1) == dt.date(2026, 3, 2)
    assert nyse.add_business_days(friday, 0) == friday
    # n == 0 returns the date unchanged even when it is not a business day.
    closed = dt.date(2025, 12, 25)
    assert nyse.add_business_days(closed, 0) == closed
    assert nyse.add_business_days(friday, -1) == dt.date(2026, 2, 26)


def test_nth_is_the_settlement_primitive(nyse):
    # T+2 from a Wednesday before Thanksgiving 2025 (closed 27th, early 28th).
    assert nyse.add_business_days(dt.date(2025, 11, 26), 2) == dt.date(2025, 12, 1)


def test_aliases_are_the_same_functions(nyse):
    day = dt.date(2026, 2, 27)
    assert nyse.nth_business_day(day, 3) == nyse.add_business_days(day, 3)
    assert nyse.prev_business_day(day) == nyse.previous_business_day(day)
    assert nyse.business_days_in_range(day, day) == nyse.business_days_between(day, day)


# --- Counting ----------------------------------------------------------------


def test_business_days_between_includes_both_endpoints(nyse):
    monday = dt.date(2026, 3, 2)
    friday = dt.date(2026, 3, 6)
    assert nyse.business_days_between(monday, friday) == 5
    assert nyse.business_days_between(monday, monday) == 1
    assert nyse.business_days_between(dt.date(2026, 3, 7), dt.date(2026, 3, 7)) == 0


def test_business_days_between_rejects_reversed_range(nyse):
    with pytest.raises(ValueError):
        nyse.business_days_between(dt.date(2026, 3, 6), dt.date(2026, 3, 2))


def test_event_count_in_range_includes_weekends(nyse):
    count = nyse.event_count_in_range(dt.date(2025, 1, 1), dt.date(2025, 12, 31))
    events = nyse.events_in_range(dt.date(2025, 1, 1), dt.date(2025, 12, 31))
    weekends = sum(1 for e in events if e.type == "WEEKEND")
    assert weekends > 100
    assert count > weekends


# --- Session detail ----------------------------------------------------------


def test_close_time(nyse):
    assert nyse.close_time(dt.date(2025, 7, 3)) == dt.time(13, 0)
    assert nyse.is_early_close(dt.date(2025, 7, 3)) is True
    assert nyse.close_time(dt.date(2025, 7, 2)) is None  # regular session
    assert nyse.close_time(dt.date(2025, 7, 4)) is None  # closed
    assert nyse.is_early_close(dt.date(2025, 7, 4)) is False


def test_early_close_is_still_a_business_day(nyse):
    assert nyse.is_business_day(dt.date(2025, 7, 3)) is True


# --- Events ------------------------------------------------------------------


def test_events_on(nyse):
    (event,) = nyse.events_on(dt.date(2025, 12, 25))
    assert event.type == "CLOSED"
    assert event.description == "Christmas Day"
    assert event.source_module == "module:christmas"
    assert nyse.event_on(dt.date(2025, 12, 25)) == event


def test_weekend_rows_are_reconstructed(nyse):
    (event,) = nyse.events_on(dt.date(2022, 1, 1))
    assert (event.type, event.description, event.key, event.source_module, event.status) == (
        "WEEKEND",
        "Saturday",
        "weekend",
        "weekend_policy",
        "CONFIRMED",
    )


def test_a_closure_replaces_the_weekend_row(tadawul):
    # Eid al-Fitr 2020 spanned a Saudi weekend; the closure is the published row.
    (event,) = tadawul.events_on(dt.date(2020, 5, 23))  # Saturday
    assert event.type == "CLOSED"
    assert event.description == "Eid al-Fitr"


def test_events_in_range_is_ordered_and_bounded(nyse):
    events = nyse.events_in_range(dt.date(2025, 12, 24), dt.date(2025, 12, 28))
    # 2025-12-26 is a plain session and carries no row at all.
    assert [(e.date, e.type) for e in events] == [
        (dt.date(2025, 12, 24), "EARLY_CLOSE"),
        (dt.date(2025, 12, 25), "CLOSED"),
        (dt.date(2025, 12, 27), "WEEKEND"),
        (dt.date(2025, 12, 28), "WEEKEND"),
    ]
    with pytest.raises(ValueError):
        nyse.events_in_range(dt.date(2025, 12, 26), dt.date(2025, 12, 24))


def test_no_events_on_a_plain_session(nyse):
    assert nyse.events_on(dt.date(2026, 3, 3)) == []
    assert nyse.event_on(dt.date(2026, 3, 3)) is None


def test_holidays(nyse):
    holidays = nyse.holidays(2026)
    assert all(e.type == "CLOSED" for e in holidays)
    assert [e.date for e in holidays] == sorted(e.date for e in holidays)
    names = [e.description for e in holidays]
    assert "Christmas Day" in names
    assert "Independence Day" in names
    assert 9 <= len(holidays) <= 12


def test_holidays_outside_coverage_raises(nyse):
    with pytest.raises(bdc.OutsideCoverageError):
        nyse.holidays(2031)


# --- Status and coverage -----------------------------------------------------


def test_status(nyse):
    assert nyse.verified_through == dt.date(2026, 12, 31)
    assert nyse.status(dt.date(2025, 7, 4)) == "CONFIRMED"
    assert nyse.status(dt.date(2026, 12, 31)) == "CONFIRMED"
    assert nyse.status(dt.date(2028, 6, 1)) == "PROJECTED"
    assert nyse.status(dt.date(2031, 1, 1)) == "UNKNOWN"
    assert nyse.status(dt.date(1899, 12, 31)) == "UNKNOWN"


def test_status_never_raises(nyse):
    assert nyse.status(dt.date(1000, 1, 1)) == "UNKNOWN"
    assert nyse.status(dt.date(3000, 1, 1)) == "UNKNOWN"


def test_projected_rows_are_reported(tadawul):
    projected = [
        e for e in tadawul.events_in_range(*tadawul.range) if e.status == "PROJECTED"
    ]
    assert projected, "SA-TADAWUL should carry projected (rule-derived) closures"
    assert tadawul.status(projected[0].date) == "PROJECTED"


def test_range(nyse):
    start, end = nyse.range
    assert start == dt.date(1900, 1, 1)
    assert end == dt.date(2030, 12, 31)
    assert nyse.range.contains(dt.date(2000, 1, 1))


# --- Out-of-range contract ---------------------------------------------------


@pytest.mark.parametrize(
    "call",
    [
        lambda c: c.is_business_day(dt.date(2031, 1, 1)),
        lambda c: c.events_on(dt.date(2031, 1, 1)),
        lambda c: c.event_on(dt.date(2031, 1, 1)),
        lambda c: c.events_in_range(dt.date(2030, 1, 1), dt.date(2031, 1, 1)),
        lambda c: c.close_time(dt.date(2031, 1, 1)),
        lambda c: c.is_early_close(dt.date(2031, 1, 1)),
        lambda c: c.business_days_between(dt.date(2030, 12, 30), dt.date(2031, 1, 5)),
        lambda c: c.sessions_in_range(dt.date(2030, 12, 30), dt.date(2031, 1, 5)),
        lambda c: c.next_business_day(dt.date(2030, 12, 31)),
        lambda c: c.previous_business_day(dt.date(1900, 1, 1)),
        lambda c: c.add_business_days(dt.date(2030, 12, 31), 1),
    ],
)
def test_every_operation_but_status_refuses_outside_coverage(nyse, call):
    with pytest.raises(bdc.OutsideCoverageError):
        call(nyse)


def test_outside_coverage_error_carries_context(nyse):
    with pytest.raises(bdc.OutsideCoverageError) as excinfo:
        nyse.is_business_day(dt.date(2031, 1, 1))
    error = excinfo.value
    assert error.calendar_id == NYSE
    assert error.date == dt.date(2031, 1, 1)
    assert (error.range_from, error.range_to) == (dt.date(1900, 1, 1), dt.date(2030, 12, 31))
    assert isinstance(error, ValueError)
    assert "outside the covered range" in str(error)


# --- Joint calendars ---------------------------------------------------------


@pytest.fixture(scope="module")
def joint():
    return bdc.get_joint_calendar(NYSE, TADAWUL)


def test_joint_identity_and_range(joint):
    assert joint.calendar_id == "US-NYSE+SA-TADAWUL"
    assert joint.range == (dt.date(2020, 1, 1), dt.date(2030, 12, 31))
    # The earliest declared verified_through wins.
    assert joint.verified_through == dt.date(2026, 12, 31)


def test_joint_is_open_only_where_every_member_is(joint, nyse, tadawul):
    friday = dt.date(2026, 2, 27)
    assert nyse.is_business_day(friday) is True
    assert tadawul.is_business_day(friday) is False
    assert joint.is_business_day(friday) is False

    sunday = dt.date(2026, 3, 1)
    assert tadawul.is_business_day(sunday) is True
    assert nyse.is_business_day(sunday) is False
    assert joint.is_business_day(sunday) is False

    monday = dt.date(2026, 3, 2)
    assert joint.is_business_day(monday) is True


def test_joint_events_are_labelled_by_member(joint):
    events = joint.events_on(dt.date(2026, 2, 27))
    assert [e.source_module for e in events] == ["SA-TADAWUL/weekend_policy"]


def test_joint_events_in_range_is_sorted(joint):
    events = joint.events_in_range(dt.date(2026, 2, 25), dt.date(2026, 3, 3))
    assert [e.date for e in events] == sorted(e.date for e in events)
    assert all("/" in e.source_module or e.source_module in (NYSE, TADAWUL) for e in events)


def test_joint_settlement_crosses_both_markets(joint):
    # T+2 from Wednesday 2026-02-25: Thursday is the only shared session that
    # week (Friday is a Saudi weekend, Saturday/Sunday split the two), so the
    # second joint business day lands the following week.
    assert joint.add_business_days(dt.date(2026, 2, 25), 2) == dt.date(2026, 3, 2)


def test_joint_status_is_as_good_as_its_worst_member(joint):
    assert joint.status(dt.date(2026, 3, 2)) == "CONFIRMED"
    assert joint.status(dt.date(2028, 6, 1)) == "PROJECTED"  # past the NYSE horizon
    assert joint.status(dt.date(2019, 1, 1)) == "UNKNOWN"  # outside SA-TADAWUL


def test_joint_refuses_outside_the_intersection(joint):
    with pytest.raises(bdc.OutsideCoverageError):
        joint.is_business_day(dt.date(2019, 12, 31))


def test_joint_close_time_is_the_earliest(joint, nyse):
    day = dt.date(2025, 7, 3)
    assert nyse.close_time(day) == dt.time(13, 0)
    assert joint.close_time(day) == dt.time(13, 0)


def test_joint_closed_members(joint):
    closed = joint.closed_members(dt.date(2026, 2, 27))
    assert [c.calendar_id for c in closed] == [TADAWUL]
    assert joint.closed_members(dt.date(2026, 3, 2)) == []


def test_joint_of_one_is_that_calendar(nyse):
    assert bdc.get_joint_calendar(NYSE) is nyse


def test_joint_accepts_calendar_objects(nyse, tadawul):
    assert bdc.get_joint_calendar(nyse, tadawul).calendar_id == "US-NYSE+SA-TADAWUL"


def test_joint_needs_a_calendar():
    with pytest.raises(ValueError):
        bdc.get_joint_calendar()


def test_joint_is_a_business_calendar(joint):
    assert isinstance(joint, bdc.BusinessCalendar)


# --- exchange_calendars-style surface ----------------------------------------


def test_session_aliases(nyse):
    day = dt.date(2026, 2, 27)
    assert nyse.is_session(day) == nyse.is_business_day(day)
    assert nyse.next_session(day) == nyse.next_business_day(day)
    assert nyse.previous_session(day) == nyse.previous_business_day(day)


def test_sessions_in_range(nyse):
    sessions = nyse.sessions_in_range(dt.date(2025, 12, 22), dt.date(2026, 1, 2))
    assert sessions == [
        dt.date(2025, 12, 22),
        dt.date(2025, 12, 23),
        dt.date(2025, 12, 24),
        dt.date(2025, 12, 26),
        dt.date(2025, 12, 29),
        dt.date(2025, 12, 30),
        dt.date(2025, 12, 31),
        dt.date(2026, 1, 2),
    ]


@pytest.mark.skipif(not HAS_PANDAS, reason="pandas is not installed")
def test_schedule(tadawul):
    schedule = tadawul.schedule
    assert len(schedule) == len(tadawul.sessions_in_range(*tadawul.range))
    assert list(schedule.columns) == ["close_time", "is_early_close"]


@pytest.mark.skipif(HAS_PANDAS, reason="pandas is installed")
def test_schedule_absent_without_pandas(nyse):
    assert not hasattr(nyse, "schedule")


# --- Metadata ----------------------------------------------------------------


def test_calendar_metadata(nyse, tadawul):
    assert nyse.name == "NYSE Trading Calendar"
    assert nyse.kind == "market"
    assert nyse.timezone == "America/New_York"
    assert tadawul.timezone == "Asia/Riyadh"
    assert nyse.metadata["calendar_id"] == NYSE


def test_weekend_policy_is_effective_dated(nyse, tadawul):
    assert nyse.weekend_policy.is_effective_dated()
    assert tadawul.weekend_policy.is_effective_dated()


def test_repr(nyse):
    assert repr(nyse) == "<SingleCalendar US-NYSE 1900-01-01 to 2030-12-31>"
