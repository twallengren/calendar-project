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

  private static EventSource closedDisplacing(
      String key, int month, int day, WeekendShiftPolicy policy, String... displaces) {
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
        null,
        List.of(displaces));
  }

  private static EventSource earlyClose(String key, int month, int day, WeekendShiftPolicy policy) {
    return new EventSource(
        key,
        key,
        new Rule.FixedMonthDay(null, null, month, day, "ISO"),
        EventType.EARLY_CLOSE,
        null,
        null,
        policy,
        null,
        LocalTime.of(12, 30),
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
  void nextAvailableFromLastWeekendDay_saturdayHolidayIsNotObserved() {
    // Japan does not make up a Saturday holiday: Sat Feb 11 2023 (National Foundation Day) is
    // simply a weekend, with no closure on Fri Feb 10 or Mon Feb 13.
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY,
            closed("jp_national_foundation_day", 2, 11, null));
    List<Event> events =
        nonWeekend(generator.generate(spec, LocalDate.of(2023, 2, 1), LocalDate.of(2023, 2, 28)));
    assertTrue(events.isEmpty(), "Saturday Feb 11 must not be observed: " + events);
  }

  @Test
  void nextAvailableFromLastWeekendDay_sundayHolidayCascadesPastOtherClosures() {
    // Golden Week 2026: Sun May 3 (Constitution Memorial Day) is observed on Wed May 6, past
    // Greenery Day (Mon May 4) and Children's Day (Tue May 5). JPX publishes exactly this.
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY,
            closed("jp_constitution_memorial_day", 5, 3, null),
            closed("jp_greenery_day", 5, 4, null),
            closed("jp_childrens_day", 5, 5, null));
    List<Event> events =
        nonWeekend(generator.generate(spec, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10)));
    assertEquals(
        List.of(LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 6)),
        events.stream().map(Event::date).sorted().toList());
    Event observed =
        events.stream()
            .filter(e -> e.key().equals("jp_constitution_memorial_day"))
            .findFirst()
            .orElseThrow();
    assertEquals(LocalDate.of(2026, 5, 6), observed.date());
    assertEquals(LocalDate.of(2026, 5, 3), observed.observedFrom());
  }

  @Test
  void nextAvailableFromLastWeekendDay_sundayHolidayWithFreeMondayDoesNotCascade() {
    // 2021 Mountain Day: Sun Aug 8 -> Mon Aug 9, no cascade needed.
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY,
            closed("jp_mountain_day", 8, 8, null));
    List<Event> events =
        nonWeekend(generator.generate(spec, LocalDate.of(2021, 8, 1), LocalDate.of(2021, 8, 31)));
    assertEquals(1, events.size());
    assertEquals(LocalDate.of(2021, 8, 9), events.get(0).date());
    assertEquals(LocalDate.of(2021, 8, 8), events.get(0).observedFrom());
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

  // --- EARLY_CLOSE shift policies -------------------------------------------------------

  @Test
  void earlyCloseDefaultsToDropRegardlessOfCalendarPolicy() {
    // A fixed_month_day EARLY_CLOSE is `shiftable` by default, but a half day is never moved by
    // the calendar's CLOSED policy: Sun Dec 24 2028 is simply not observed.
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            earlyClose("christmas_eve", 12, 24, null));
    assertEquals(
        WeekendShiftPolicy.DROP,
        spec.eventSources().get(0).effectiveShiftPolicy(WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY));
    List<Event> events =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2028, 12, 18), LocalDate.of(2028, 12, 31)));
    assertTrue(events.isEmpty(), "Sunday Dec 24 2028 half day must be dropped: " + events);
  }

  @Test
  void dropDiscardsAnEarlyCloseTakenByAClosure() {
    // Christmas 2027 is a Saturday -> observed Mon Dec 27; the Dec 27 half day (if any) is gone.
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            closed("christmas", 12, 25, null),
            earlyClose("year_end_half_day", 12, 27, WeekendShiftPolicy.DROP));
    List<Event> events =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2027, 12, 20), LocalDate.of(2027, 12, 31)));
    assertEquals(1, events.size(), events.toString());
    assertEquals(EventType.CLOSED, events.get(0).type());
    assertEquals(LocalDate.of(2027, 12, 27), events.get(0).date());
  }

  @Test
  void previousAvailableBusinessDay_sundayEveMovesBackToFriday() {
    // LSE: Sun Dec 24 2028 -> Fri Dec 22 2028 (Sat Dec 23 is a weekend day)
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            earlyClose(
                "christmas_eve", 12, 24, WeekendShiftPolicy.PREVIOUS_AVAILABLE_BUSINESS_DAY));
    List<Event> events =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2028, 12, 18), LocalDate.of(2028, 12, 31)));
    assertEquals(1, events.size(), events.toString());
    assertEquals(LocalDate.of(2028, 12, 22), events.get(0).date());
    assertEquals(LocalDate.of(2028, 12, 24), events.get(0).observedFrom());
    assertEquals(EventType.EARLY_CLOSE, events.get(0).type());
    assertEquals(LocalTime.of(12, 30), events.get(0).closeTime());
  }

  @Test
  void previousAvailableBusinessDay_skipsAnObservedClosureOnTheNominalDate() {
    // Christmas Sun Dec 25 2022 is observed Mon Dec 26. A half day nominally on Fri Dec 24 2021
    // is a session, but make the collision explicit: Christmas 2021 observed Fri Dec 24 under
    // NEAREST_WEEKDAY takes the date, so the Dec 24 half day moves back to Thu Dec 23.
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEAREST_WEEKDAY,
            closed("christmas", 12, 25, WeekendShiftPolicy.NEAREST_WEEKDAY),
            earlyClose(
                "christmas_eve", 12, 24, WeekendShiftPolicy.PREVIOUS_AVAILABLE_BUSINESS_DAY));
    List<Event> events =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2021, 12, 20), LocalDate.of(2021, 12, 31)));
    Event closure =
        events.stream().filter(e -> e.type() == EventType.CLOSED).findFirst().orElseThrow();
    assertEquals(LocalDate.of(2021, 12, 24), closure.date());
    Event half =
        events.stream().filter(e -> e.type() == EventType.EARLY_CLOSE).findFirst().orElseThrow();
    assertEquals(LocalDate.of(2021, 12, 23), half.date());
    assertEquals(LocalDate.of(2021, 12, 24), half.observedFrom());
  }

  @Test
  void previousAvailableBusinessDay_droppedWhenNothingFoundWithinSevenDays() {
    // A week-long closure immediately before the half day's nominal date leaves no session
    // within the seven-day search window, so the half day is not observed at all.
    EventSource blockade =
        new EventSource(
            "shutdown",
            "Shutdown",
            new Rule.FixedMonthDay(null, null, 6, 8, "ISO", null, null, 7),
            EventType.CLOSED,
            false,
            null);
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NONE,
            blockade,
            earlyClose("half_day", 6, 15, WeekendShiftPolicy.PREVIOUS_AVAILABLE_BUSINESS_DAY));
    // 2025-06-15 is a Sunday; Jun 8..14 are all CLOSED, so Jun 8..14 and the Sat are unavailable
    List<Event> events =
        nonWeekend(generator.generate(spec, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30)));
    assertTrue(
        events.stream().noneMatch(e -> e.type() == EventType.EARLY_CLOSE),
        "half day must be dropped when no session is found within 7 days: " + events);
  }

  // --- displaces ------------------------------------------------------------------------

  @Test
  void displaces_sundayChristmasTakesMondayAndPushesBoxingDayToTuesday() {
    // TMX 2022: Sun Dec 25 -> Mon Dec 26 "in lieu of Christmas Day"; Boxing Day (nominal Mon
    // Dec 26, an ordinary weekday) re-cascades to Tue Dec 27 "in lieu of Boxing Day".
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            closedDisplacing("ca_christmas", 12, 25, null, "ca_boxing_day"),
            closed("ca_boxing_day", 12, 26, null));
    List<Event> events =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2022, 12, 20), LocalDate.of(2022, 12, 31)));
    Event christmas =
        events.stream().filter(e -> e.key().equals("ca_christmas")).findFirst().orElseThrow();
    Event boxing =
        events.stream().filter(e -> e.key().equals("ca_boxing_day")).findFirst().orElseThrow();
    assertEquals(LocalDate.of(2022, 12, 26), christmas.date());
    assertEquals(LocalDate.of(2022, 12, 25), christmas.observedFrom());
    assertEquals(LocalDate.of(2022, 12, 27), boxing.date());
    assertEquals(LocalDate.of(2022, 12, 26), boxing.observedFrom());
  }

  @Test
  void displaces_saturdayChristmasNeedsNoDisplacement() {
    // TMX 2021: Sat Dec 25 -> Mon Dec 27, Sun Dec 26 -> Tue Dec 28, by ordinary cascading.
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            closedDisplacing("ca_christmas", 12, 25, null, "ca_boxing_day"),
            closed("ca_boxing_day", 12, 26, null));
    List<Event> events =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2021, 12, 20), LocalDate.of(2021, 12, 31)));
    assertEquals(
        List.of(LocalDate.of(2021, 12, 27), LocalDate.of(2021, 12, 28)),
        events.stream().map(Event::date).toList());
  }

  @Test
  void withoutDisplaces_ukOrderingIsUnchanged() {
    // GB-LSE 2022: Boxing Day keeps its own Monday, Christmas cascades past it onto Tuesday.
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            closed("uk_christmas", 12, 25, null),
            closed("uk_boxing_day", 12, 26, null));
    List<Event> events =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2022, 12, 20), LocalDate.of(2022, 12, 31)));
    Event christmas =
        events.stream().filter(e -> e.key().equals("uk_christmas")).findFirst().orElseThrow();
    Event boxing =
        events.stream().filter(e -> e.key().equals("uk_boxing_day")).findFirst().orElseThrow();
    assertEquals(LocalDate.of(2022, 12, 27), christmas.date(), "Christmas cascades to Tuesday");
    assertEquals(LocalDate.of(2022, 12, 26), boxing.date(), "Boxing Day keeps its own Monday");
    assertNull(boxing.observedFrom());
  }

  @Test
  void displaces_onlyTakesASlotHeldExclusivelyByDisplaceableEvents() {
    // The Monday also carries a closure this event may not displace, so the Sunday holiday
    // cascades past it as usual rather than evicting anyone.
    EventSource immovable =
        new EventSource(
            "bank_holiday",
            "Bank Holiday",
            new Rule.ExplicitDates(
                null, null, List.of(new Rule.AnnotatedDate(LocalDate.of(2022, 12, 26)))),
            EventType.CLOSED,
            false,
            null);
    ResolvedSpec spec =
        spec(
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            closedDisplacing("ca_christmas", 12, 25, null, "ca_boxing_day"),
            closed("ca_boxing_day", 12, 26, null),
            immovable);
    List<Event> events =
        nonWeekend(
            generator.generate(spec, LocalDate.of(2022, 12, 20), LocalDate.of(2022, 12, 31)));
    Event christmas =
        events.stream().filter(e -> e.key().equals("ca_christmas")).findFirst().orElseThrow();
    assertEquals(LocalDate.of(2022, 12, 27), christmas.date());
    Event boxing =
        events.stream().filter(e -> e.key().equals("ca_boxing_day")).findFirst().orElseThrow();
    assertEquals(LocalDate.of(2022, 12, 26), boxing.date());
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
