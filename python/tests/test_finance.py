"""Tenors, day counts, IMM dates and schedules; values mirror the Java core tests."""

import datetime as dt

import pytest

import bdc_calendars as bdc
from bdc_calendars import DayCount, Frequency, RollConvention, ScheduleDefinition, StubConvention, Tenor

d = dt.date


def test_tenor_parsing_and_units():
    assert Tenor.parse("3M") == Tenor(3, "M")
    assert Tenor.parse(" -2w ") == Tenor(-2, "W")
    assert Tenor.parse("T+2") == Tenor(2, "B") and str(Tenor.parse("t-1")) == "-1B"
    assert Tenor.parse("1y").months == 12 and Tenor.parse("2W").days == 14
    assert Tenor.parse("6M").is_month_based and not Tenor.parse("6D").is_month_based
    for bad in ("3X", "M", "", "T+", "3 M", "1.5Y"):
        with pytest.raises(ValueError):
            Tenor.parse(bad)
    with pytest.raises(ValueError):
        Tenor.parse("3M").days
    assert Frequency.of("3M") is Frequency.QUARTERLY and Frequency.of("quarterly") is Frequency.QUARTERLY
    assert Frequency.QUARTERLY.periods_per_year == 4


def test_imm_dates():
    assert bdc.imm_date(2024, 3) == d(2024, 3, 20)
    assert bdc.imm_date(2024, 6) == d(2024, 6, 19)
    assert bdc.is_imm(d(2024, 3, 20)) and not bdc.is_imm(d(2024, 3, 21))
    assert bdc.next_imm(d(2024, 3, 20)) == d(2024, 4, 17)
    assert bdc.next_imm(d(2024, 3, 19)) == d(2024, 3, 20)
    assert bdc.next_quarterly_imm(d(2024, 3, 20)) == d(2024, 6, 19)


def test_day_counts_match_isda_definitions():
    assert DayCount.ACT_360.days(d(2024, 1, 31), d(2024, 2, 29)) == 29
    assert DayCount.ACT_360.year_fraction(d(2024, 1, 31), d(2024, 2, 29)) == 29 / 360
    assert DayCount.ACT_365F.year_fraction(d(2024, 1, 1), d(2025, 1, 1)) == 366 / 365
    assert DayCount.ACT_ACT_ISDA.year_fraction(d(2023, 11, 15), d(2024, 2, 15)) == pytest.approx(47 / 365 + 45 / 366)
    assert DayCount.ACT_ACT_ISDA.year_fraction(d(2022, 6, 30), d(2025, 6, 30)) == pytest.approx(3.0)
    feb_end, mar_end = d(2024, 2, 29), d(2024, 3, 31)
    assert DayCount.THIRTY_360_BOND.days(feb_end, mar_end) == 32
    assert DayCount.THIRTY_E_360.days(feb_end, mar_end) == 31
    assert DayCount.THIRTY_E_360_ISDA.days(feb_end, mar_end) == 30
    assert DayCount.THIRTY_360_BOND.days(d(2024, 1, 31), mar_end) == 60
    assert DayCount.THIRTY_360_BOND.year_fraction(d(2023, 1, 15), d(2023, 4, 15)) == 0.25
    assert DayCount.THIRTY_E_360_ISDA.year_fraction(d(2023, 1, 31), d(2024, 2, 29), end_is_termination=True) == pytest.approx(389 / 360)
    assert DayCount.THIRTY_E_360_ISDA.year_fraction(d(2023, 1, 31), d(2024, 2, 29)) == pytest.approx(390 / 360)
    assert DayCount.ACT_ACT_ICMA.year_fraction(
        d(2024, 2, 15), d(2024, 5, 15), period_start=d(2024, 2, 15), period_end=d(2024, 8, 15), frequency="SEMI_ANNUAL"
    ) == pytest.approx(90 / (2 * 182))
    with pytest.raises(ValueError):
        DayCount.ACT_ACT_ICMA.year_fraction(d(2024, 2, 15), d(2024, 5, 15))
    with pytest.raises(ValueError):
        DayCount.BUS_252.year_fraction(d(2024, 2, 15), d(2024, 5, 15))
    assert DayCount.of("act/360") is DayCount.ACT_360
    for day_count in DayCount:
        if day_count.is_calendar_free:
            assert day_count.year_fraction(d(2024, 2, 15), d(2023, 11, 15)) == pytest.approx(
                -day_count.year_fraction(d(2023, 11, 15), d(2024, 2, 15))
            )


def test_tenor_addition_on_a_real_calendar():
    nyse = bdc.get_calendar("US-NYSE")
    assert nyse.add_tenor(d(2024, 1, 31), "3M", "MODIFIED_FOLLOWING") == d(2024, 4, 30)
    assert nyse.add_tenor(d(2024, 1, 12), "T+2", "UNADJUSTED") == d(2024, 1, 17), "MLK Monday is closed"
    assert nyse.add_tenor(d(2024, 7, 3), "1D", "FOLLOWING") == d(2024, 7, 5), "Independence Day"
    assert nyse.add_tenor(d(2024, 7, 3), "1D", "PRECEDING") == d(2024, 7, 3)
    result = nyse.add_tenor_detailed(d(2024, 7, 3), "1D", "FOLLOWING")
    assert result.operation == "ADD_TENOR" and result.tenor == "1D" and result.convention == "FOLLOWING"
    assert result.examined_dates == (d(2024, 7, 4), d(2024, 7, 5))
    business = nyse.add_tenor_detailed(d(2024, 1, 12), "T+2", "FOLLOWING")
    assert business.business_day_offset == 2 and business.convention is None and business.tenor == "2B"
    months = nyse.add_tenor_detailed(d(2024, 1, 31), "1M", "MODIFIED_FOLLOWING", preserve_end_of_month=True)
    assert months.month_offset == 1 and months.preserve_end_of_month and months.result_date == d(2024, 2, 29)
    assert nyse.adjust_detailed(d(2024, 1, 2), "FOLLOWING").tenor is None


def test_bus_252_counts_business_days():
    nyse = bdc.get_calendar("US-NYSE")
    result = nyse.year_fraction_detailed(d(2024, 1, 1), d(2024, 2, 1), "BUS_252")
    assert result.days == 21 and result.year_fraction == 21 / 252
    assert len(result.examined_dates) == 31
    assert nyse.year_fraction_detailed(d(2024, 2, 1), d(2024, 1, 1), DayCount.BUS_252).days == -21
    act = nyse.year_fraction_detailed(d(2024, 1, 1), d(2024, 2, 1), "ACT_360")
    assert act.days == 31 and act.year_fraction == 31 / 360 and act.examined_dates == ()
    assert act.effective_confidence == "CONFIRMED"
    assert nyse.year_fraction(d(2024, 1, 1), d(2024, 2, 1), "ACT_360") == 31 / 360


def test_schedule_generation():
    nyse = bdc.get_calendar("US-NYSE")
    regular = nyse.generate_schedule(ScheduleDefinition(d(2024, 1, 16), d(2025, 1, 16), Frequency.QUARTERLY))
    assert regular.dates == [d(2024, 1, 16), d(2024, 4, 16), d(2024, 7, 16), d(2024, 10, 16), d(2025, 1, 16)]
    assert not any(p.stub for p in regular.periods)

    monthly = nyse.generate_schedule(ScheduleDefinition(d(2024, 1, 31), d(2024, 7, 31), "MONTHLY"))
    assert monthly.unadjusted_dates == [
        d(2024, 1, 31), d(2024, 2, 29), d(2024, 3, 31), d(2024, 4, 30), d(2024, 5, 31), d(2024, 6, 30), d(2024, 7, 31),
    ]
    assert monthly.dates == [
        d(2024, 1, 31), d(2024, 2, 29), d(2024, 3, 28), d(2024, 4, 30), d(2024, 5, 31), d(2024, 6, 28), d(2024, 7, 31),
    ], "Good Friday 29 March and Sunday 30 June roll back under MODIFIED_FOLLOWING"

    base = ScheduleDefinition(d(2024, 1, 15), d(2024, 12, 1), Frequency.QUARTERLY)
    short_final = nyse.generate_schedule(base)
    assert short_final.unadjusted_dates == [d(2024, 1, 15), d(2024, 4, 15), d(2024, 7, 15), d(2024, 10, 15), d(2024, 12, 1)]
    assert short_final.dates[0] == d(2024, 1, 16), "MLK Day"
    assert [p.stub for p in short_final.periods] == [False, False, False, True]
    long_final = nyse.generate_schedule(base._replace(stub=StubConvention.LONG_FINAL))
    assert long_final.unadjusted_dates == [d(2024, 1, 15), d(2024, 4, 15), d(2024, 7, 15), d(2024, 12, 1)]
    short_initial = nyse.generate_schedule(base._replace(stub="SHORT_INITIAL"))
    assert short_initial.unadjusted_dates == [d(2024, 1, 15), d(2024, 3, 1), d(2024, 6, 1), d(2024, 9, 1), d(2024, 12, 1)]
    assert [p.stub for p in short_initial.periods] == [True, False, False, False]
    long_initial = nyse.generate_schedule(base._replace(stub=StubConvention.LONG_INITIAL))
    assert long_initial.unadjusted_dates == [d(2024, 1, 15), d(2024, 6, 1), d(2024, 9, 1), d(2024, 12, 1)]
    with pytest.raises(ValueError):
        nyse.generate_schedule(base._replace(stub=StubConvention.NONE))

    eom = nyse.generate_schedule(
        ScheduleDefinition(d(2024, 2, 29), d(2024, 8, 31), Frequency.QUARTERLY, roll=RollConvention.EOM, convention="UNADJUSTED")
    )
    assert eom.dates == [d(2024, 2, 29), d(2024, 5, 31), d(2024, 8, 31)]
    imm = nyse.generate_schedule(ScheduleDefinition(d(2024, 3, 20), d(2024, 9, 18), "QUARTERLY", roll="IMM"))
    assert imm.unadjusted_dates == [d(2024, 3, 20), d(2024, 6, 19), d(2024, 9, 18)]
    assert imm.dates == [d(2024, 3, 20), d(2024, 6, 20), d(2024, 9, 18)], "Juneteenth"
    weekly = nyse.generate_schedule(ScheduleDefinition(d(2024, 1, 2), d(2024, 1, 23), Frequency.WEEKLY))
    assert weekly.dates == [d(2024, 1, 2), d(2024, 1, 9), d(2024, 1, 16), d(2024, 1, 23)]
    with pytest.raises(ValueError):
        nyse.generate_schedule(ScheduleDefinition(d(2024, 1, 2), d(2024, 1, 23), Frequency.WEEKLY, roll=RollConvention.EOM))
    with pytest.raises(ValueError):
        nyse.generate_schedule(ScheduleDefinition(d(2024, 1, 23), d(2024, 1, 2), Frequency.WEEKLY))

    projected = nyse.generate_schedule(ScheduleDefinition(d(2028, 1, 18), d(2029, 1, 18), Frequency.QUARTERLY))
    assert projected.effective_confidence == "PROJECTED"
    assert d(2028, 1, 18) in projected.examined_dates


def test_schedule_on_a_joint_calendar_and_coverage_errors():
    joint = bdc.get_joint_calendar(["US-NYSE", "GB-LSE"])
    schedule = joint.generate_schedule(ScheduleDefinition(d(2024, 5, 24), d(2024, 8, 24), Frequency.MONTHLY, convention="FOLLOWING"))
    assert schedule.dates == [d(2024, 5, 24), d(2024, 6, 24), d(2024, 7, 24), d(2024, 8, 27)], (
        "24 Aug is a Saturday; Monday 26 Aug is the UK summer bank holiday"
    )
    from bdc_calendars._loader import CalendarData
    from bdc_calendars.calendar import SingleCalendar

    metadata = dict(range_start="2020-01-01", range_end="2021-12-31", coverage=dict(quality=[
        dict(scope="SCHEDULED_CLOSURES", **{"from": "2020-01-01", "to": "2021-12-31"}, quality="VERIFIED", evidence_ids=["x"]),
        dict(scope="EARLY_CLOSES", **{"from": "2020-01-01", "to": "2021-12-31"}, quality="VERIFIED", evidence_ids=["x"]),
        dict(scope="UNSCHEDULED_EXCEPTIONS", **{"from": "2020-01-01", "to": "2020-07-30"}, quality="VERIFIED", evidence_ids=["x"]),
        dict(scope="UNSCHEDULED_EXCEPTIONS", **{"from": "2020-07-31", "to": "2021-12-31"}, quality="INCOMPLETE", evidence_ids=[]),
    ]))
    unresolved = SingleCalendar(CalendarData("SYNTHETIC", metadata, []))
    with pytest.raises(bdc.UnresolvedDateError):
        unresolved.generate_schedule(ScheduleDefinition(d(2020, 6, 1), d(2020, 12, 1), Frequency.QUARTERLY))
    with pytest.raises(bdc.OutsideCoverageError):
        bdc.get_calendar("US-NYSE").generate_schedule(ScheduleDefinition(d(2030, 10, 1), d(2031, 4, 1), Frequency.QUARTERLY))


def test_long_stubs_with_a_single_period_and_backward_rolls():
    nyse = bdc.get_calendar("US-NYSE")
    for stub in (StubConvention.SHORT_FINAL, StubConvention.LONG_FINAL, StubConvention.SHORT_INITIAL, StubConvention.LONG_INITIAL):
        schedule = nyse.generate_schedule(ScheduleDefinition(d(2024, 1, 16), d(2024, 2, 15), Frequency.QUARTERLY, stub))
        assert schedule.dates == [d(2024, 1, 16), d(2024, 2, 15)], stub
        assert schedule.periods[0].stub
    backward = nyse.generate_schedule(
        ScheduleDefinition(d(2024, 2, 1), d(2024, 5, 31), Frequency.MONTHLY, StubConvention.SHORT_INITIAL, convention="UNADJUSTED")
    )
    assert backward.unadjusted_dates == [d(2024, 2, 1), d(2024, 2, 29), d(2024, 3, 31), d(2024, 4, 30), d(2024, 5, 31)]
    assert [p.stub for p in backward.periods] == [True, False, False, False]
