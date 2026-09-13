package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Weekend shifting, same-date precedence, effective-dated weekends and span rules. */
class ShiftPolicyTest {

  private final EventGenerator generator = new EventGenerator();

  private static EventSource closed(String key, int month, int day, WeekendShiftPolicy policy) {
    return new EventSource(
        key,
        key,
        new Rule.FixedMonthDay(null, null, month, day, "ISO"),
        EventType.CLOSED,
        true,
        null,
        policy,
        null,
        null,
        null,
        null);
  }

  private static ResolvedSpec spec(
      WeekendPolicy weekend, WeekendShiftPolicy defaultPolicy, EventSource... sources) {
    return new ResolvedSpec(
        "T",
        null,
        weekend,
        defaultPolicy,
        List.of(),
        List.of(sources),
        Map.of(),
        List.of(),
        List.of());
  }

  private static List<Event> nonWeekend(List<Event> events) {
    return events.stream().filter(e -> e.type() != EventType.WEEKEND).toList();
  }

  @Test
  void forwardOnly_saturdayHolidayIsNotObserved() {
    // Jan 1 2022 is a Saturday: NYSE stays open on Fri Dec 31 2021
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEAREST_WEEKDAY,
            closed("new_years_day", 1, 1, WeekendShiftPolicy.FORWARD_ONLY));
    List<Event> events =
        nonWeekend(generator.generate(spec, LocalDate.of(2021, 12, 1), LocalDate.of(2022, 1, 31)));
    assertTrue(events.isEmpty(), "Saturday Jan 1 must not be observed: " + events);
  }

  @Test
  void forwardOnly_sundayHolidayObservedMonday() {
    // Jan 1 2023 is a Sunday -> Monday Jan 2
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NONE,
            closed("new_years_day", 1, 1, WeekendShiftPolicy.FORWARD_ONLY));
    List<Event> events =
        nonWeekend(generator.generate(spec, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31)));
    assertEquals(1, events.size());
    assertEquals(LocalDate.of(2023, 1, 2), events.get(0).date());
    assertEquals(LocalDate.of(2023, 1, 1), events.get(0).observedFrom());
    assertEquals("new_years_day", events.get(0).key());
  }

  @Test
  void perEventPolicyOverridesCalendarDefault() {
    // Christmas Sat Dec 25 2021: NEAREST -> Fri Dec 24; calendar default is NONE
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NONE,
            closed("christmas", 12, 25, WeekendShiftPolicy.NEAREST_WEEKDAY));
    List<Event> events =
        nonWeekend(generator.generate(spec, LocalDate.of(2021, 12, 1), LocalDate.of(2021, 12, 31)));
    assertEquals(List.of(LocalDate.of(2021, 12, 24)), events.stream().map(Event::date).toList());
  }

  @Test
  void closedTakesPrecedenceOverEarlyCloseOnSameDate() {
    EventSource eve =
        new EventSource(
            "christmas_eve",
            "Christmas Eve",
            new Rule.FixedMonthDay(null, null, 12, 24, "ISO"),
            EventType.EARLY_CLOSE,
            false,
            null,
            null,
            null,
            LocalTime.of(13, 0),
            null,
            null);
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEAREST_WEEKDAY,
            closed("christmas", 12, 25, null),
            eve);
    List<Event> events =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2021, 12, 20), LocalDate.of(2021, 12, 31)));
    assertEquals(1, events.size(), "exactly one row on Dec 24 2021: " + events);
    assertEquals(EventType.CLOSED, events.get(0).type());
    assertEquals(LocalDate.of(2021, 12, 24), events.get(0).date());

    // In 2024 (Christmas on Wednesday) the early close is emitted with its time
    List<Event> events2024 =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2024, 12, 20), LocalDate.of(2024, 12, 31)));
    Event earlyClose =
        events2024.stream()
            .filter(e -> e.type() == EventType.EARLY_CLOSE)
            .findFirst()
            .orElseThrow();
    assertEquals(LocalTime.of(13, 0), earlyClose.closeTime());
    assertEquals(LocalDate.of(2024, 12, 24), earlyClose.date());
  }

  @Test
  void onlyIfWeekday_dropsOtherWeekdays() {
    // July 3 early close only Mon/Tue/Thu: 2026-07-03 is a Friday -> dropped
    EventSource eve =
        new EventSource(
            "independence_day_eve",
            "Independence Day Eve",
            new Rule.FixedMonthDay(null, null, 7, 3, "ISO"),
            EventType.EARLY_CLOSE,
            false,
            null,
            null,
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            LocalTime.of(13, 0),
            null,
            null);
    ResolvedSpec spec = spec(WeekendPolicy.SAT_SUN, WeekendShiftPolicy.NONE, eve);
    assertTrue(
        nonWeekend(generator.generate(spec, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10)))
            .isEmpty());
    // 2025-07-03 is a Thursday -> kept
    assertEquals(
        1,
        nonWeekend(generator.generate(spec, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 10)))
            .size());
  }

  @Test
  void nextAvailableWeekday_cascadesPastOtherClosures() {
    // UK 2021: Christmas Sat -> Mon 27, Boxing Day Sun -> Tue 28
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            closed("christmas", 12, 25, null),
            closed("boxing_day", 12, 26, null));
    List<Event> events =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2021, 12, 20), LocalDate.of(2021, 12, 31)));
    assertEquals(
        List.of(LocalDate.of(2021, 12, 27), LocalDate.of(2021, 12, 28)),
        events.stream().map(Event::date).toList());
  }

  @Test
  void nextAvailableWeekday_seesNonShiftingClosures() {
    // A fixed closure on the Monday: the Sunday holiday must skip to Tuesday
    EventSource fixedMonday =
        new EventSource(
            "fixed",
            "Fixed",
            new Rule.ExplicitDates(
                null, null, List.of(new Rule.AnnotatedDate(LocalDate.of(2022, 6, 20)))),
            EventType.CLOSED,
            false,
            null);
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            fixedMonday,
            closed("juneteenth", 6, 19, null));
    List<Event> events =
        nonWeekend(generator.generate(spec, LocalDate.of(2022, 6, 15), LocalDate.of(2022, 6, 25)));
    assertEquals(
        List.of(LocalDate.of(2022, 6, 20), LocalDate.of(2022, 6, 21)),
        events.stream().map(Event::date).toList());
  }

  @Test
  void nearestWeekday_friSatWeekend() {
    // Saudi: Fri Sep 23 2022 -> Thu Sep 22; Sat Sep 23 2023 -> Sun Sep 24
    WeekendPolicy friSat = new WeekendPolicy(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));
    ResolvedSpec spec =
        spec(friSat, WeekendShiftPolicy.NEAREST_WEEKDAY, closed("national_day", 9, 23, null));
    assertEquals(
        LocalDate.of(2022, 9, 22),
        nonWeekend(generator.generate(spec, LocalDate.of(2022, 9, 1), LocalDate.of(2022, 9, 30)))
            .get(0)
            .date());
    assertEquals(
        LocalDate.of(2023, 9, 24),
        nonWeekend(generator.generate(spec, LocalDate.of(2023, 9, 1), LocalDate.of(2023, 9, 30)))
            .get(0)
            .date());
  }

  @Test
  void effectiveDatedWeekend_saturdayTradingDayBeforeCutover() {
    WeekendPolicy nyse =
        WeekendPolicy.ofPeriods(
            List.of(
                new WeekendPeriod(EnumSet.of(DayOfWeek.SUNDAY), null, LocalDate.of(1952, 5, 30)),
                new WeekendPeriod(
                    EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                    LocalDate.of(1952, 5, 31),
                    null)));
    assertFalse(nyse.isWeekend(LocalDate.of(1952, 5, 24))); // Saturday, still a session
    assertTrue(nyse.isWeekend(LocalDate.of(1952, 5, 31))); // first weekend Saturday
    assertTrue(nyse.isWeekend(LocalDate.of(1952, 5, 25))); // Sunday always

    // A Saturday holiday before the cutover stays on the Saturday as CLOSED (no weekend row)
    ResolvedSpec spec =
        spec(nyse, WeekendShiftPolicy.NEAREST_WEEKDAY, closed("christmas", 12, 25, null));
    List<Event> events =
        generator.generate(spec, LocalDate.of(1948, 12, 20), LocalDate.of(1948, 12, 31));
    assertTrue(
        events.stream()
            .anyMatch(
                e -> e.date().equals(LocalDate.of(1948, 12, 25)) && e.type() == EventType.CLOSED));
    assertTrue(events.stream().noneMatch(e -> e.date().equals(LocalDate.of(1948, 12, 24))));
    List<Event> weekendRows = events.stream().filter(e -> e.type() == EventType.WEEKEND).toList();
    assertFalse(weekendRows.isEmpty());
    assertTrue(
        weekendRows.stream().allMatch(e -> e.date().getDayOfWeek() == DayOfWeek.SUNDAY),
        "only Sundays are weekend rows before the cutover: " + weekendRows);
  }

  @Test
  void weekendRowEmittedAlongsideNotableEvent() {
    EventSource notable =
        new EventSource(
            "diwali",
            "Diwali",
            new Rule.ExplicitDates(
                null, null, List.of(new Rule.AnnotatedDate(LocalDate.of(2026, 11, 8)))),
            EventType.NOTABLE,
            false,
            null);
    ResolvedSpec spec = spec(WeekendPolicy.SAT_SUN, WeekendShiftPolicy.NEAREST_WEEKDAY, notable);
    List<Event> events =
        generator.generate(spec, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 8));
    assertEquals(2, events.size(), events.toString());
    assertTrue(events.stream().anyMatch(e -> e.type() == EventType.NOTABLE));
    assertTrue(events.stream().anyMatch(e -> e.type() == EventType.WEEKEND));
  }

  @Test
  void spanRuleEmitsOneOccurrencePerDayAcrossChronologyMonths() {
    // Ramadan 28 - Shawwal 4, AH 1451 (Umm al-Qura): 8 consecutive days in early 2030
    EventSource eid =
        new EventSource(
            "eid",
            "Eid al-Fitr",
            new Rule.FixedMonthDay(null, null, 9, 28, "UMM_AL_QURA", 10, 4, null),
            EventType.CLOSED,
            false,
            null,
            null,
            null,
            null,
            EventStatus.PROJECTED,
            null);
    ResolvedSpec spec = spec(WeekendPolicy.NONE, WeekendShiftPolicy.NONE, eid);
    List<Event> events =
        generator.generate(spec, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31));
    // 28..29/30 Ramadan plus 1..4 Shawwal: 6 or 7 consecutive days
    assertTrue(events.size() == 6 || events.size() == 7, events.toString());
    for (int i = 1; i < events.size(); i++) {
      assertEquals(events.get(i - 1).date().plusDays(1), events.get(i).date());
    }
    assertEquals(EventStatus.PROJECTED, events.get(0).status());
    assertEquals(
        com.bdc.chronology.ChronologyTranslator.toIsoDate(1451, 9, 28, "UMM_AL_QURA"),
        events.get(0).date());
    assertEquals(
        com.bdc.chronology.ChronologyTranslator.toIsoDate(1451, 10, 4, "UMM_AL_QURA"),
        events.get(events.size() - 1).date());
  }

  @Test
  void durationDaysSpan() {
    EventSource span =
        new EventSource(
            "golden_week",
            "Golden Week",
            new Rule.FixedMonthDay(null, null, 5, 3, "ISO", null, null, 3),
            EventType.CLOSED,
            false,
            null);
    ResolvedSpec spec = spec(WeekendPolicy.NONE, WeekendShiftPolicy.NONE, span);
    List<Event> events =
        generator.generate(spec, LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 10));
    assertEquals(
        List.of(LocalDate.of(2024, 5, 3), LocalDate.of(2024, 5, 4), LocalDate.of(2024, 5, 5)),
        events.stream().map(Event::date).toList());
  }

  @Test
  void shiftedHolidayCrossingRangeStartIsIncluded() {
    // Generating from Jan 1 2022 must still see nothing on Dec 31 2021; generating Dec 2021 must
    // see Christmas observed Dec 24 even when the range starts after the nominal date's month.
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEAREST_WEEKDAY,
            closed("christmas", 12, 25, null));
    List<Event> dec24 =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2021, 12, 24), LocalDate.of(2021, 12, 24)));
    assertEquals(1, dec24.size());
    assertEquals(LocalDate.of(2021, 12, 25), dec24.get(0).observedFrom());
  }

  @Test
  void ummAlQuraRangePastTableEndIsClamped() {
    EventSource eid =
        new EventSource(
            "eid",
            "Eid",
            new Rule.FixedMonthDay(null, null, 10, 1, "UMM_AL_QURA"),
            EventType.CLOSED,
            false,
            null);
    ResolvedSpec spec = spec(WeekendPolicy.NONE, WeekendShiftPolicy.NONE, eid);
    // Table ends in AH 1500 (~2076); a range to 2090 must not throw and must stop at the table end
    List<Event> events =
        generator.generate(spec, LocalDate.of(2070, 1, 1), LocalDate.of(2090, 12, 31));
    assertFalse(events.isEmpty());
    assertTrue(events.stream().allMatch(e -> e.date().getYear() <= 2077));
  }

  @Test
  void unknownChronologyFails() {
    EventSource bad =
        new EventSource(
            "bad",
            "Bad",
            new Rule.FixedMonthDay(null, null, 1, 1, "MARTIAN"),
            EventType.CLOSED,
            false,
            null);
    ResolvedSpec spec = spec(WeekendPolicy.NONE, WeekendShiftPolicy.NONE, bad);
    assertThrows(
        IllegalArgumentException.class,
        () -> generator.generate(spec, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));
  }
}
