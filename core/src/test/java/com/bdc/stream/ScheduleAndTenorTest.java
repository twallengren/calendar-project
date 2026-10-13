package com.bdc.stream;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.DateRange;
import com.bdc.finance.DayCount;
import com.bdc.finance.Frequency;
import com.bdc.finance.RollConvention;
import com.bdc.finance.StubConvention;
import com.bdc.finance.Tenor;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageInterval;
import com.bdc.trust.CoverageQuality;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleAndTenorTest {

  private static final DateRange RANGE =
      new DateRange(LocalDate.of(2022, 1, 1), LocalDate.of(2026, 12, 31));

  private static LocalDate d(String iso) {
    return LocalDate.parse(iso);
  }

  /** Saturday/Sunday weekends plus the given closures, fully verified. */
  private static CsvDateStream stream(List<CoverageInterval> quality, String... closed) {
    List<Event> events = new ArrayList<>();
    for (LocalDate day = RANGE.start(); !day.isAfter(RANGE.end()); day = day.plusDays(1)) {
      if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
        events.add(new Event(day, EventType.WEEKEND, day.getDayOfWeek().toString(), "fixture"));
      }
    }
    for (String iso : closed) {
      events.add(new Event(d(iso), EventType.CLOSED, "Holiday " + iso, "fixture"));
    }
    return new CsvDateStream("CAL", events, RANGE, RANGE.end(), quality);
  }

  private static List<CoverageInterval> verified() {
    List<CoverageInterval> quality = new ArrayList<>();
    for (CompletenessScope scope : CompletenessScope.values()) {
      quality.add(
          new CoverageInterval(
              scope, RANGE.start(), RANGE.end(), CoverageQuality.VERIFIED, List.of("src")));
    }
    return quality;
  }

  private static List<LocalDate> dates(String... isos) {
    return List.of(isos).stream().map(LocalDate::parse).toList();
  }

  @Test
  void regularQuarterlyScheduleHasNoStub() {
    Schedule schedule =
        stream(verified())
            .schedule(ScheduleDefinition.of(d("2024-01-15"), d("2025-01-15"), Frequency.QUARTERLY));
    assertEquals(
        dates("2024-01-15", "2024-04-15", "2024-07-15", "2024-10-15", "2025-01-15"),
        schedule.dates());
    assertEquals(4, schedule.periods().size());
    assertTrue(schedule.periods().stream().noneMatch(SchedulePeriod::stub));
    assertEquals(EventStatus.CONFIRMED, schedule.effectiveConfidence());
  }

  @Test
  void monthlyScheduleClipsFromTheAnchorAndAdjustsModifiedFollowing() {
    Schedule schedule =
        stream(verified())
            .schedule(ScheduleDefinition.of(d("2024-01-31"), d("2024-07-31"), Frequency.MONTHLY));
    assertEquals(
        dates(
            "2024-01-31",
            "2024-02-29",
            "2024-03-31",
            "2024-04-30",
            "2024-05-31",
            "2024-06-30",
            "2024-07-31"),
        schedule.unadjustedDates());
    // Sunday 31 March and Sunday 30 June roll back under MODIFIED_FOLLOWING
    assertEquals(
        dates(
            "2024-01-31",
            "2024-02-29",
            "2024-03-29",
            "2024-04-30",
            "2024-05-31",
            "2024-06-28",
            "2024-07-31"),
        schedule.dates());
    assertTrue(
        schedule.examinedDates().contains(d("2024-04-01")), "the crossing date was examined");
  }

  @Test
  void stubConventionsPlaceTheIrregularPeriod() {
    ScheduleDefinition base =
        ScheduleDefinition.of(d("2024-01-15"), d("2024-12-01"), Frequency.QUARTERLY);
    CsvDateStream stream = stream(verified());

    Schedule shortFinal = stream.schedule(base.withStub(StubConvention.SHORT_FINAL));
    assertEquals(
        dates("2024-01-15", "2024-04-15", "2024-07-15", "2024-10-15", "2024-12-02"),
        shortFinal.dates());
    assertEquals(d("2024-12-01"), shortFinal.periods().get(3).unadjustedEnd());
    assertTrue(shortFinal.periods().get(3).stub());
    assertFalse(shortFinal.periods().get(2).stub());

    Schedule longFinal = stream.schedule(base.withStub(StubConvention.LONG_FINAL));
    assertEquals(dates("2024-01-15", "2024-04-15", "2024-07-15", "2024-12-02"), longFinal.dates());
    assertTrue(longFinal.periods().get(2).stub());

    Schedule shortInitial = stream.schedule(base.withStub(StubConvention.SHORT_INITIAL));
    assertEquals(
        dates("2024-01-15", "2024-03-01", "2024-06-03", "2024-09-02", "2024-12-02"),
        shortInitial.dates());
    assertEquals(
        dates("2024-01-15", "2024-03-01", "2024-06-01", "2024-09-01", "2024-12-01"),
        shortInitial.unadjustedDates());
    assertTrue(shortInitial.periods().get(0).stub());
    assertFalse(shortInitial.periods().get(1).stub());

    Schedule longInitial = stream.schedule(base.withStub(StubConvention.LONG_INITIAL));
    assertEquals(
        dates("2024-01-15", "2024-06-03", "2024-09-02", "2024-12-02"), longInitial.dates());
    assertTrue(longInitial.periods().get(0).stub());

    assertThrows(
        IllegalArgumentException.class, () -> stream.schedule(base.withStub(StubConvention.NONE)));
    assertEquals(
        5,
        stream
            .schedule(
                ScheduleDefinition.of(d("2024-01-15"), d("2025-01-15"), Frequency.QUARTERLY)
                    .withStub(StubConvention.NONE))
            .dates()
            .size());
  }

  @Test
  void longStubsWithASinglePeriodAndBackwardRollsClipFromTheEnd() {
    CsvDateStream stream = stream(verified());
    // Shorter than one period: every stub convention except NONE yields exactly one period.
    for (StubConvention stub :
        List.of(
            StubConvention.SHORT_FINAL,
            StubConvention.LONG_FINAL,
            StubConvention.SHORT_INITIAL,
            StubConvention.LONG_INITIAL)) {
      Schedule schedule =
          stream.schedule(
              ScheduleDefinition.of(d("2024-01-15"), d("2024-02-15"), Frequency.QUARTERLY)
                  .withStub(stub));
      assertEquals(dates("2024-01-15", "2024-02-15"), schedule.dates(), stub.name());
      assertTrue(schedule.periods().get(0).stub(), stub.name());
    }
    // Backward rolls clip from the end anchor: 31 May back one month is 30 April, back two is 31
    // March.
    Schedule backward =
        stream.schedule(
            ScheduleDefinition.of(d("2024-02-01"), d("2024-05-31"), Frequency.MONTHLY)
                .withStub(StubConvention.SHORT_INITIAL)
                .withConvention(BusinessDayConvention.UNADJUSTED));
    assertEquals(
        dates("2024-02-01", "2024-02-29", "2024-03-31", "2024-04-30", "2024-05-31"),
        backward.unadjustedDates());
    assertTrue(backward.periods().get(0).stub());
    assertFalse(backward.periods().get(1).stub());
  }

  @Test
  void eomImmAndWeeklyRolls() {
    CsvDateStream stream = stream(verified());
    Schedule eom =
        stream.schedule(
            ScheduleDefinition.of(d("2024-02-29"), d("2024-08-31"), Frequency.QUARTERLY)
                .withRoll(RollConvention.EOM)
                .withConvention(BusinessDayConvention.UNADJUSTED));
    assertEquals(dates("2024-02-29", "2024-05-31", "2024-08-31"), eom.dates());
    assertTrue(eom.periods().stream().noneMatch(SchedulePeriod::stub));

    Schedule imm =
        stream.schedule(
            ScheduleDefinition.of(d("2024-03-20"), d("2024-09-18"), Frequency.QUARTERLY)
                .withRoll(RollConvention.IMM));
    assertEquals(dates("2024-03-20", "2024-06-19", "2024-09-18"), imm.dates());

    Schedule weekly =
        stream.schedule(ScheduleDefinition.of(d("2024-01-01"), d("2024-01-22"), Frequency.WEEKLY));
    assertEquals(dates("2024-01-01", "2024-01-08", "2024-01-15", "2024-01-22"), weekly.dates());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ScheduleDefinition.of(d("2024-01-01"), d("2024-01-22"), Frequency.WEEKLY)
                .withRoll(RollConvention.EOM));
  }

  @Test
  void scheduleConfidenceIsTheWeakestAdjustedDate() {
    List<CoverageInterval> quality = verified();
    quality.add(
        new CoverageInterval(
            CompletenessScope.SCHEDULED_CLOSURES,
            d("2024-07-15"),
            d("2024-07-15"),
            CoverageQuality.PROJECTED,
            List.of()));
    Schedule schedule =
        stream(quality)
            .schedule(ScheduleDefinition.of(d("2024-01-15"), d("2025-01-15"), Frequency.QUARTERLY));
    assertEquals(EventStatus.PROJECTED, schedule.effectiveConfidence());

    quality.add(
        new CoverageInterval(
            CompletenessScope.UNSCHEDULED_EXCEPTIONS,
            d("2024-10-15"),
            d("2024-10-15"),
            CoverageQuality.INCOMPLETE,
            List.of()));
    assertThrows(
        UnresolvedDateException.class,
        () ->
            stream(quality)
                .schedule(
                    ScheduleDefinition.of(d("2024-01-15"), d("2025-01-15"), Frequency.QUARTERLY)));
    // an incomplete early-close scope never matters to a schedule
    List<CoverageInterval> earlyCloseGap = verified();
    earlyCloseGap.add(
        new CoverageInterval(
            CompletenessScope.EARLY_CLOSES,
            RANGE.start(),
            RANGE.end(),
            CoverageQuality.INCOMPLETE,
            List.of()));
    assertEquals(
        EventStatus.CONFIRMED,
        stream(earlyCloseGap)
            .schedule(ScheduleDefinition.of(d("2024-01-15"), d("2025-01-15"), Frequency.QUARTERLY))
            .effectiveConfidence());
  }

  @Test
  void tenorsAddCalendarOrBusinessDays() {
    CsvDateStream stream = stream(verified(), "2024-01-08");
    assertEquals(
        d("2024-04-30"),
        stream.addTenor(d("2024-01-31"), "3M", BusinessDayConvention.MODIFIED_FOLLOWING));
    assertEquals(
        d("2024-01-10"),
        stream.addTenor(d("2024-01-05"), "T+2", BusinessDayConvention.UNADJUSTED),
        "Mon 8th is closed");
    assertEquals(
        d("2024-01-09"), stream.addTenor(d("2024-01-01"), "1W", BusinessDayConvention.FOLLOWING));
    assertEquals(
        d("2024-01-05"),
        stream.addTenor(d("2024-01-05"), Tenor.parse("2D"), BusinessDayConvention.PRECEDING));
    assertEquals(
        d("2025-01-06"),
        stream.addTenor(d("2024-01-05"), "1Y", BusinessDayConvention.FOLLOWING),
        "Sun 5 Jan 2025");

    DateOperationResult result =
        stream.addTenorDetailed(
            d("2024-01-05"), Tenor.parse("2D"), BusinessDayConvention.FOLLOWING, false);
    assertEquals(DateOperation.ADD_TENOR, result.operation());
    assertEquals("2D", result.tenor());
    assertEquals(d("2024-01-09"), result.resultDate(), "Sun 7th, Mon 8th closed");
    assertEquals(
        List.of(d("2024-01-07"), d("2024-01-08"), d("2024-01-09")), result.examinedDates());
    assertEquals(BusinessDayConvention.FOLLOWING, result.convention());

    DateOperationResult business =
        stream.addTenorDetailed(
            d("2024-01-05"), Tenor.parse("T+2"), BusinessDayConvention.FOLLOWING, false);
    assertEquals(2, business.businessDayOffset());
    assertNull(business.convention(), "business-day tenors ignore the convention");
    assertEquals("2B", business.tenor());

    DateOperationResult months =
        stream.addTenorDetailed(
            d("2024-01-31"), Tenor.parse("1M"), BusinessDayConvention.MODIFIED_FOLLOWING, true);
    assertEquals(1, months.monthOffset());
    assertTrue(months.preserveEndOfMonth());
    assertEquals(d("2024-02-29"), months.resultDate());

    assertEquals(
        d("2024-01-05"), stream.addTenor(d("2024-01-05"), "0D", BusinessDayConvention.FOLLOWING));
  }

  @Test
  void bus252CountsBusinessDaysWithConfidence() {
    CsvDateStream stream = stream(verified(), "2024-01-01");
    YearFractionResult result =
        stream.yearFractionDetailed(d("2024-01-01"), d("2024-01-08"), DayCount.BUS_252);
    assertEquals(4, result.days(), "Mon 1st closed, Tue-Fri open, weekend, end exclusive");
    assertEquals(4 / 252.0, result.yearFraction());
    assertEquals(EventStatus.CONFIRMED, result.effectiveConfidence());
    assertEquals(7, result.examinedDates().size());
    assertEquals(
        -4, stream.yearFractionDetailed(d("2024-01-08"), d("2024-01-01"), DayCount.BUS_252).days());

    YearFractionResult act =
        stream.yearFractionDetailed(d("2024-01-01"), d("2024-01-08"), DayCount.ACT_360);
    assertEquals(7, act.days());
    assertEquals(7 / 360.0, act.yearFraction());
    assertTrue(act.examinedDates().isEmpty());
    assertEquals(
        7 / 360.0, stream.yearFraction(d("2024-01-01"), d("2024-01-08"), DayCount.ACT_360));

    List<CoverageInterval> projected = verified();
    projected.add(
        new CoverageInterval(
            CompletenessScope.SCHEDULED_CLOSURES,
            d("2024-01-03"),
            d("2024-01-03"),
            CoverageQuality.PROJECTED,
            List.of()));
    assertEquals(
        EventStatus.PROJECTED,
        stream(projected)
            .yearFractionDetailed(d("2024-01-01"), d("2024-01-08"), DayCount.BUS_252)
            .effectiveConfidence());
  }
}
