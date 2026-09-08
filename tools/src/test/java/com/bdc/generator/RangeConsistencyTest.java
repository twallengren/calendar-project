package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.ChronologyTranslator;
import com.bdc.chronology.DateRange;
import com.bdc.formula.EasterCalculator;
import com.bdc.formula.ReferenceResolver;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.*;
import com.bdc.resolver.SpecResolver;
import com.bdc.stream.LazyDateStream;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RangeConsistencyTest {
  private final EventGenerator generator = new EventGenerator();

  static LocalDate date(String text) {
    return LocalDate.parse(text);
  }

  static EventSource source(String key, Rule rule, boolean shiftable) {
    return new EventSource(key, key, rule, EventType.CLOSED, shiftable, null);
  }

  static EventSource fixed(String key, int month, int day) {
    return source(key, new Rule.FixedMonthDay(key, key, month, day, "ISO"), true);
  }

  static EventSource explicit(String key, boolean shiftable, LocalDate... dates) {
    return source(
        key,
        new Rule.ExplicitDates(
            key, key, Arrays.stream(dates).map(Rule.AnnotatedDate::new).toList()),
        shiftable);
  }

  static ResolvedSpec spec(
      WeekendShiftPolicy policy,
      WeekendPolicy weekend,
      List<EventSource> sources,
      List<Delta> deltas) {
    return new ResolvedSpec(
        "TEST",
        null,
        weekend,
        policy,
        List.of(
            new Reference("easter", "EASTER_WESTERN"), new Reference("thanks", "THANKSGIVING_US")),
        sources,
        Map.of(),
        deltas,
        null);
  }

  static ResolvedSpec spec(WeekendShiftPolicy policy, EventSource... sources) {
    return spec(policy, WeekendPolicy.SAT_SUN, List.of(sources), List.of());
  }

  static List<Event> within(List<Event> events, LocalDate start, LocalDate end) {
    return events.stream()
        .filter(e -> !e.date().isBefore(start) && !e.date().isAfter(end))
        .toList();
  }

  private void observed(ResolvedSpec spec, String original, String target) {
    LocalDate day = date(target);
    List<Event> narrow = generator.generate(spec, day, day);
    assertEquals(List.of(new Event(day, EventType.CLOSED, "holiday", "TEST:holiday")), narrow);
    assertEquals(
        narrow,
        within(
            generator.generate(spec, date(original).minusDays(10), date(original).plusDays(10)),
            day,
            day));
  }

  @Test
  void equalLookingRowsRetainTheirOrderWhenEarlierOccurrencesChangeKeyInsertionOrder() {
    LocalDate target = date("2024-03-01");
    EventSource a =
        source(
            "a",
            new Rule.ExplicitDates(
                "a",
                "same",
                List.of(
                    new Rule.AnnotatedDate(date("2024-02-01")), new Rule.AnnotatedDate(target))),
            true);
    EventSource b =
        source(
            "b",
            new Rule.ExplicitDates(
                "b",
                "same",
                List.of(
                    new Rule.AnnotatedDate(date("2024-01-01")), new Rule.AnnotatedDate(target))),
            true);
    for (WeekendShiftPolicy policy : WeekendShiftPolicy.values()) {
      ResolvedSpec spec = spec(policy, a, b);
      List<Event> narrow = generator.generate(spec, target, target);
      assertEquals(2, narrow.size());
      assertEquals(
          within(generator.generate(spec, date("2024-01-01"), target), target, target), narrow);
    }
  }

  @Test
  void nearestShiftsAcrossBothBoundariesAndWeekendConventions() {
    observed(
        spec(WeekendShiftPolicy.NEAREST_WEEKDAY, fixed("holiday", 7, 4)),
        "2026-07-04",
        "2026-07-03");
    observed(
        spec(WeekendShiftPolicy.NEAREST_WEEKDAY, fixed("holiday", 7, 4)),
        "2027-07-04",
        "2027-07-05");
    observed(
        spec(WeekendShiftPolicy.NEAREST_WEEKDAY, fixed("holiday", 1, 1)),
        "2022-01-01",
        "2021-12-31");
    observed(
        spec(WeekendShiftPolicy.NEAREST_WEEKDAY, fixed("holiday", 12, 31)),
        "2023-12-31",
        "2024-01-01");
    WeekendPolicy friSat = new WeekendPolicy(List.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));
    observed(
        spec(
            WeekendShiftPolicy.NEAREST_WEEKDAY, friSat, List.of(fixed("holiday", 1, 1)), List.of()),
        "2021-01-01",
        "2020-12-31");
    observed(
        spec(
            WeekendShiftPolicy.NEAREST_WEEKDAY,
            friSat,
            List.of(fixed("holiday", 12, 31)),
            List.of()),
        "2022-12-31",
        "2023-01-01");
  }

  @Test
  void nyseSingleDayExportNavigationAndCountsAgree() throws Exception {
    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    ResolvedSpec nyse = new SpecResolver(registry).resolve("US-NYSE");
    LazyDateStream stream = new LazyDateStream(nyse);
    LocalDate july3 = date("2026-07-03");
    assertTrue(
        stream.eventsOn(july3).stream()
            .anyMatch(
                e -> e.type() == EventType.CLOSED && e.description().equals("Independence Day")));
    assertFalse(stream.isBusinessDay(july3));
    assertEquals(
        within(stream.eventsInRange(date("2026-01-01"), date("2026-12-31")), july3, july3),
        stream.eventsOn(july3));
    assertEquals(date("2026-07-06"), stream.nextBusinessDay(date("2026-07-02")));
    assertEquals(date("2026-07-02"), stream.prevBusinessDay(date("2026-07-06")));
    assertEquals(date("2026-07-07"), stream.nthBusinessDay(date("2026-07-02"), 2));
    assertEquals(date("2026-07-01"), stream.nthBusinessDay(date("2026-07-06"), -2));
    assertEquals(2, stream.businessDaysInRange(date("2026-07-02"), date("2026-07-06")));
  }

  @Test
  void cascadesRespectReservedWeekdaysAndStableSameDateOrder() {
    LocalDate christmas = date("2021-12-25");
    EventSource duplicates =
        source(
            "same",
            new Rule.ExplicitDates(
                "same",
                "same",
                List.of(
                    new Rule.AnnotatedDate(date("2020-01-01"), "outside"),
                    new Rule.AnnotatedDate(christmas, "first"),
                    new Rule.AnnotatedDate(christmas, "second"))),
            true);
    ResolvedSpec spec =
        spec(
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            fixed("christmas", 12, 25),
            duplicates,
            fixed("boxing", 12, 26),
            fixed("reserved", 12, 27),
            explicit("nonshiftable", false, date("2021-12-28")));
    List<Event> events = generator.generate(spec, date("2021-12-28"), date("2022-01-03"));
    assertEquals(
        List.of("christmas", "nonshiftable"),
        within(events, date("2021-12-28"), date("2021-12-28")).stream()
            .map(Event::description)
            .toList());
    assertEquals(
        "same (first)",
        within(events, date("2021-12-29"), date("2021-12-29")).getFirst().description());
    assertEquals(
        "same (second)",
        within(events, date("2021-12-30"), date("2021-12-30")).getFirst().description());
    assertEquals(
        "boxing", within(events, date("2021-12-31"), date("2021-12-31")).getFirst().description());
    for (LocalDate day = date("2021-12-28");
        !day.isAfter(date("2022-01-03"));
        day = day.plusDays(1)) {
      assertEquals(within(events, day, day), generator.generate(spec, day, day));
    }
  }

  @Test
  void longCascadeCrossesYearAndMoreThanOneWeek() {
    List<EventSource> sources = new ArrayList<>();
    for (int i = 0; i < 15; i++) sources.add(explicit("h" + i, true, date("2022-12-24")));
    sources.add(fixed("newyear", 1, 1));
    sources.add(explicit("reserved", true, date("2022-12-26"), date("2023-01-02")));
    ResolvedSpec spec =
        spec(WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY, sources.toArray(EventSource[]::new));
    List<Event> events = generator.generate(spec, date("2022-12-20"), date("2023-02-01"));
    assertEquals(
        "h14", within(events, date("2023-01-17"), date("2023-01-17")).getFirst().description());
    assertEquals(
        "newyear", within(events, date("2023-01-18"), date("2023-01-18")).getFirst().description());
    assertEquals(
        within(events, date("2023-01-10"), date("2023-01-20")),
        generator.generate(spec, date("2023-01-10"), date("2023-01-20")));
  }

  @Test
  void activeYearsUseOriginalDateAndDeltasRunAfterObservation() {
    EventSource active =
        new EventSource(
            "newyear",
            "newyear",
            fixed("newyear", 1, 1).rule(),
            EventType.CLOSED,
            true,
            List.of(new EventSource.YearRange(2022)));
    ResolvedSpec nearest =
        spec(
            WeekendShiftPolicy.NEAREST_WEEKDAY,
            WeekendPolicy.SAT_SUN,
            List.of(active),
            List.of(new Delta.Reclassify("newyear", date("2021-12-31"), EventType.EARLY_CLOSE)));
    assertEquals(
        EventType.EARLY_CLOSE,
        generator.generate(nearest, date("2021-12-31"), date("2021-12-31")).getFirst().type());
    assertTrue(generator.generate(nearest, date("2023-01-02"), date("2023-01-02")).isEmpty());
    ResolvedSpec cascade =
        spec(
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            WeekendPolicy.SAT_SUN,
            List.of(fixed("christmas", 12, 25), fixed("boxing", 12, 26), fixed("reserved", 12, 27)),
            List.of(
                new Delta.Remove("reserved", date("2021-12-27")),
                new Delta.Remove("christmas", date("2021-12-28")),
                new Delta.Reclassify("boxing", date("2021-12-29"), EventType.NOTABLE),
                new Delta.Add("added", "added", date("2021-12-29"), EventType.CLOSED)));
    List<Event> events = generator.generate(cascade, date("2021-12-27"), date("2021-12-29"));
    assertEquals(
        List.of(
            new Event(date("2021-12-29"), EventType.CLOSED, "added", "delta:add"),
            new Event(date("2021-12-29"), EventType.NOTABLE, "boxing", "TEST:boxing")),
        events);
    assertEquals(events, generator.generate(cascade, date("2021-12-29"), date("2021-12-29")));
  }

  @Test
  void dayOffsetsUseReferenceYearsInBothDirectionsIncludingMoreThanAYear() {
    for (int offset : new int[] {-800, -366, -40, 40, 366, 800}) {
      for (boolean named : new boolean[] {false, true}) {
        LocalDate ref = named ? EasterCalculator.westernEaster(2024) : date("2024-01-01");
        Rule rule =
            named
                ? new Rule.RelativeToReference("relative", "relative", "easter", offset)
                : new Rule.RelativeToReference("relative", "relative", null, offset, 1, 1, null);
        ResolvedSpec spec = spec(WeekendShiftPolicy.NONE, source("relative", rule, false));
        LocalDate target = ref.plusDays(offset);
        List<Event> single = generator.generate(spec, target, target);
        assertTrue(
            single.stream().anyMatch(e -> e.description().equals("relative")), "offset=" + offset);
        assertEquals(
            single,
            within(
                generator.generate(spec, target.minusYears(3), target.plusYears(3)),
                target,
                target));
      }
    }
  }

  @Test
  void weekdayOffsetsUseInverseDisplacementInBothDirections() {
    for (Rule.OffsetDirection direction : Rule.OffsetDirection.values()) {
      for (int nth : new int[] {1, 2, 60}) {
        for (boolean named : new boolean[] {false, true}) {
          Rule.WeekdayOffset offset = new Rule.WeekdayOffset(DayOfWeek.MONDAY, nth, direction);
          LocalDate reference = named ? EasterCalculator.westernEaster(2024) : date("2024-01-01");
          int step = direction == Rule.OffsetDirection.AFTER ? 1 : -1;
          LocalDate expected = reference;
          for (int found = 0; found < nth; ) {
            expected = expected.plusDays(step);
            if (expected.getDayOfWeek() == DayOfWeek.MONDAY) found++;
          }
          Rule rule =
              named
                  ? new Rule.RelativeToReference(
                      "relative", "relative", "easter", null, null, null, offset)
                  : new Rule.RelativeToReference("relative", "relative", 1, 1, offset);
          ResolvedSpec spec = spec(WeekendShiftPolicy.NONE, source("relative", rule, false));
          List<Event> single = generator.generate(spec, expected, expected);
          assertEquals(
              List.of(new Event(expected, EventType.CLOSED, "relative", "TEST:relative")), single);
          assertEquals(
              single,
              within(
                  generator.generate(spec, expected.minusYears(2), expected.plusYears(2)),
                  expected,
                  expected));
        }
      }
    }
  }

  @Test
  void legacyReferenceLookupStillReturnsWholeYears() {
    ReferenceResolver resolver = new ReferenceResolver();
    DateRange range = new DateRange(date("2024-01-01"), date("2024-01-01"));
    resolver.resolve(List.of(new Reference("easter", "EASTER_WESTERN")), range);
    assertEquals(List.of(date("2024-03-31")), resolver.getDates("easter"));
    assertTrue(resolver.getDates("easter", range).isEmpty());
    assertEquals(
        List.of(date("2025-04-20")),
        resolver.getDates("easter", new DateRange(date("2025-04-01"), date("2025-04-30"))));
    assertEquals(List.of(date("2024-03-31")), resolver.getDates("easter"));
  }

  @Test
  void delayLimitIsInclusiveAndErrorsIdentifyTheOccurrence() {
    LocalDate original = date("2022-01-01");
    LocalDate limit = original.plusDays(366); // Monday
    List<LocalDate> reserved =
        original
            .plusDays(1)
            .datesUntil(limit)
            .filter(d -> d.getDayOfWeek().getValue() < 6)
            .toList();
    EventSource holiday = explicit("holiday", true, original);
    ResolvedSpec succeeds =
        spec(
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            holiday,
            explicit("reserved", true, reserved.toArray(LocalDate[]::new)));
    assertTrue(
        generator.generate(succeeds, limit, limit).stream()
            .anyMatch(e -> e.description().equals("holiday")));
    List<LocalDate> blocked = new ArrayList<>(reserved);
    blocked.add(limit);
    ResolvedSpec fails =
        spec(
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            holiday,
            explicit("reserved", true, blocked.toArray(LocalDate[]::new)));
    String message =
        assertThrows(IllegalArgumentException.class, () -> generator.generate(fails, limit, limit))
            .getMessage();
    assertTrue(
        message.contains("TEST")
            && message.contains("holiday")
            && message.contains("2022-01-01")
            && message.contains("366 calendar days"),
        message);
    assertEquals(
        message,
        assertThrows(IllegalArgumentException.class, () -> generator.generate(fails, limit, limit))
            .getMessage());
  }

  @Test
  void cascadingDependenciesExtendBeyondTheInitialLookbackAndCachedRootsRemainUsable() {
    LocalDate first = date("1800-01-04");
    LocalDate[] dates =
        IntStream.range(0, 1000).mapToObj(i -> first.plusWeeks(i)).toArray(LocalDate[]::new);
    ResolvedSpec spec =
        spec(WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY, explicit("weekly", true, dates));
    LocalDate target = dates[dates.length - 1].plusDays(2);
    List<Event> expected = List.of(new Event(target, EventType.CLOSED, "weekly", "TEST:weekly"));
    assertEquals(expected, generator.generate(spec, target, target));
    assertEquals(expected, within(generator.generate(spec, first, target), target, target));
    // The same generator must not retain placements belonging to another calendar.
    ResolvedSpec reserved =
        spec(
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            explicit("weekly", true, dates),
            explicit("reserved", true, target));
    assertEquals(
        List.of(new Event(target.plusDays(1), EventType.CLOSED, "weekly", "TEST:weekly")),
        generator.generate(reserved, target.plusDays(1), target.plusDays(1)));
    assertEquals(expected, generator.generate(spec, target, target));
  }

  @Test
  void anOldExtraOccurrencePropagatesThroughYearsOfWeeklyCollisions() {
    LocalDate first = date("2000-01-01");
    List<Rule.AnnotatedDate> dates = new ArrayList<>();
    dates.add(new Rule.AnnotatedDate(first, "initial extra"));
    for (int i = 0; i < 1000; i++)
      dates.add(new Rule.AnnotatedDate(first.plusWeeks(i), "week " + i));
    EventSource weekly = source("weekly", new Rule.ExplicitDates("weekly", "weekly", dates), true);
    WeekendPolicy onlyMondayIsAvailable =
        new WeekendPolicy(EnumSet.range(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY));
    ResolvedSpec spec =
        spec(
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            onlyMondayIsAvailable,
            List.of(weekly),
            List.of());
    LocalDate target = first.plusWeeks(999).plusDays(2);
    List<Event> expected =
        List.of(new Event(target, EventType.CLOSED, "weekly (week 998)", "TEST:weekly"));
    assertEquals(expected, generator.generate(spec, target, target));
    assertEquals(expected, within(generator.generate(spec, first, target), target, target));
  }

  @Test
  void allSupportedChronologiesPreserveObservedDatesInSingleDayQueries() {
    for (String chronology : List.of("ISO", "HIJRI", "JULIAN", "PERSIAN", "UMM_AL_QURA")) {
      for (WeekendShiftPolicy policy : WeekendShiftPolicy.values()) {
        EventSource source =
            source("annual", new Rule.FixedMonthDay("annual", "annual", 1, 1, chronology), true);
        ResolvedSpec spec = spec(policy, source);
        List<Event> wide = generator.generate(spec, date("2024-01-01"), date("2026-12-31"));
        List<Event> holidays = wide.stream().filter(e -> e.type() == EventType.CLOSED).toList();
        assertFalse(holidays.isEmpty(), chronology);
        for (Event holiday : holidays) {
          assertEquals(
              List.of(holiday),
              generator.generate(spec, holiday.date(), holiday.date()),
              chronology + " " + policy);
        }
      }
    }
  }

  @Test
  void dependencyWorkLimitFailsDeterministicallyWithoutUsingTheJavaCallStack() {
    LocalDate first = date("1800-01-04"); // Saturday
    LocalDate[] dates =
        IntStream.range(0, 11000).mapToObj(i -> first.plusWeeks(i)).toArray(LocalDate[]::new);
    ResolvedSpec spec =
        spec(WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY, explicit("weekly", true, dates));
    LocalDate target = dates[dates.length - 1].plusDays(2);
    String message =
        assertThrows(IllegalArgumentException.class, () -> generator.generate(spec, target, target))
            .getMessage();
    assertTrue(
        message.contains("10000 distinct occurrence placements")
            && message.contains("TEST")
            && message.contains("weekly")
            && message.contains("original date"),
        message);
    assertEquals(
        message,
        assertThrows(IllegalArgumentException.class, () -> generator.generate(spec, target, target))
            .getMessage());
  }

  @Test
  void cascadingRejectsCalendarWithNoWeekdays() {
    ResolvedSpec spec =
        spec(
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            new WeekendPolicy(EnumSet.allOf(DayOfWeek.class)),
            List.of(fixed("holiday", 1, 1)),
            List.of());
    assertTrue(
        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(spec, date("2024-01-01"), date("2024-01-01")))
            .getMessage()
            .contains("no available weekdays"));
  }

  @Test
  void dependenciesOutsideChronologyCoverageFailExplicitly() {
    LocalDate first = ChronologyTranslator.toIsoDate(1356, 1, 1, "UMM_AL_QURA");
    LocalDate last =
        ChronologyTranslator.toIsoDate(
            1500,
            12,
            com.bdc.chronology.ontology.ChronologyRegistry.getInstance()
                .getAlgorithm("UMM_AL_QURA")
                .getDaysInMonth(1500, 12),
            "UMM_AL_QURA");
    EventSource lunar =
        source("lunar", new Rule.FixedMonthDay("lunar", "lunar", 1, 1, "UMM_AL_QURA"), true);
    ResolvedSpec plain = spec(WeekendShiftPolicy.NONE, lunar);
    assertFalse(generator.generate(plain, first, first).isEmpty());
    for (WeekendShiftPolicy policy :
        List.of(WeekendShiftPolicy.NEAREST_WEEKDAY, WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY)) {
      String message =
          assertThrows(
                  IllegalArgumentException.class,
                  () -> generator.generate(spec(policy, lunar), first, first))
              .getMessage();
      assertTrue(
          message.contains("UMM_AL_QURA") && message.contains("supported chronology"), message);
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> generator.generate(spec(WeekendShiftPolicy.NEAREST_WEEKDAY, lunar), last, last));
    assertThrows(
        IllegalArgumentException.class, () -> generator.generate(plain, first.minusDays(1), first));
  }

  @Test
  void overflowingDependenciesFailAndInclusiveMaxDateCanBeGeneratedWithoutDependencies() {
    ResolvedSpec empty =
        new ResolvedSpec(
            "TEST",
            null,
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NONE,
            null,
            List.of(),
            null,
            null,
            null);
    assertDoesNotThrow(() -> generator.generate(empty, LocalDate.MAX, LocalDate.MAX));
    for (LocalDate boundary : List.of(LocalDate.MIN, LocalDate.MAX)) {
      ResolvedSpec nearest =
          new ResolvedSpec(
              "TEST",
              null,
              null,
              WeekendShiftPolicy.NEAREST_WEEKDAY,
              null,
              List.of(fixed("holiday", 1, 1)),
              null,
              null,
              null);
      assertTrue(
          assertThrows(
                  IllegalArgumentException.class,
                  () -> generator.generate(nearest, boundary, boundary))
              .getMessage()
              .contains("representable range"));
      int offset = boundary.equals(LocalDate.MIN) ? 1 : -1;
      Rule rule = new Rule.RelativeToReference("relative", "relative", null, offset, 1, 1, null);
      ResolvedSpec relative =
          new ResolvedSpec(
              "TEST",
              null,
              WeekendPolicy.NONE,
              WeekendShiftPolicy.NONE,
              null,
              List.of(source("relative", rule, false)),
              null,
              null,
              null);
      assertTrue(
          assertThrows(
                  IllegalArgumentException.class,
                  () -> generator.generate(relative, boundary, boundary))
              .getMessage()
              .contains("representable range"));
    }
  }
}
