package com.bdc.test;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Edge case tests for calendar generation. Tests boundary conditions and unusual scenarios. */
class EdgeCaseTests {

  /** Filter to exclude WEEKEND events when testing holiday-specific logic. */
  private static final Predicate<Event> NON_WEEKEND = e -> e.type() != EventType.WEEKEND;

  private static SpecRegistry testRegistry;
  private static SpecRegistry prodRegistry;
  private SpecResolver resolver;
  private EventGenerator generator;

  @BeforeAll
  static void setupRegistry() throws Exception {
    testRegistry = new SpecRegistry();
    testRegistry.loadCalendarsFromDirectory(
        Path.of("tools/src/test/resources/test-calendars/calendars"));
    testRegistry.loadModulesFromDirectory(
        Path.of("tools/src/test/resources/test-calendars/modules"));

    prodRegistry = new SpecRegistry();
    prodRegistry.loadCalendarsFromDirectory(Path.of("calendars"));
    prodRegistry.loadModulesFromDirectory(Path.of("modules"));
  }

  @BeforeEach
  void setup() {
    resolver = new SpecResolver(testRegistry);
    generator = new EventGenerator();
  }

  // === Date Range Tests ===

  @Test
  @DisplayName("Empty date range (from == to) should work")
  void emptyDateRangeWorks() {
    ResolvedSpec spec = resolver.resolve("YEAR-BOUNDARY");
    LocalDate date = LocalDate.of(2024, 1, 1);
    Event expectedEvent =
        new Event(date, EventType.CLOSED, "New Year's Day", "YEAR-BOUNDARY:new_years_day");

    List<Event> events = generator.generate(spec, date, date);

    // Should find the New Year's Day event
    assertEquals(1, events.size());
    Event newYearsDay = events.getFirst();
    assertEquals(expectedEvent, newYearsDay);
  }

  @Test
  @DisplayName("Single day range with no event returns empty list")
  void singleDayRangeNoEvent() {
    ResolvedSpec spec = resolver.resolve("SIMPLE");
    // Test Holiday is June 15, pick a different day (Tuesday, not a weekend)
    LocalDate date = LocalDate.of(2024, 3, 12);

    List<Event> events = generator.generate(spec, date, date);

    List<Event> nonWeekendEvents = events.stream().filter(NON_WEEKEND).toList();
    assertTrue(nonWeekendEvents.isEmpty());
  }

  @Test
  @DisplayName("Single day range with event returns that event")
  void singleDayRangeWithEvent() {
    ResolvedSpec spec = resolver.resolve("SIMPLE");
    LocalDate date = LocalDate.of(2024, 6, 15); // Test Holiday date

    List<Event> events = generator.generate(spec, date, date);

    assertEquals(1, events.size());
    assertEquals("Test Holiday", events.get(0).description());
  }

  // === Extreme Year Tests ===

  @ParameterizedTest
  @DisplayName("Far future years should work")
  @ValueSource(ints = {2050, 2075, 2099})
  void farFutureYearsWork(int year) {
    ResolvedSpec spec = resolver.resolve("SIMPLE");
    LocalDate from = LocalDate.of(year, 1, 1);
    LocalDate to = LocalDate.of(year, 12, 31);

    List<Event> events = generator.generate(spec, from, to);
    List<Event> nonWeekendEvents = events.stream().filter(NON_WEEKEND).toList();

    // Should have exactly 1 non-weekend event (Test Holiday on June 15)
    assertEquals(1, nonWeekendEvents.size());
    assertEquals(LocalDate.of(year, 6, 15), nonWeekendEvents.get(0).date());
  }

  @ParameterizedTest
  @DisplayName("Historical years should work")
  @ValueSource(ints = {1990, 1995, 2000, 2010})
  void historicalYearsWork(int year) {
    ResolvedSpec spec = resolver.resolve("SIMPLE");
    LocalDate from = LocalDate.of(year, 1, 1);
    LocalDate to = LocalDate.of(year, 12, 31);

    List<Event> events = generator.generate(spec, from, to);
    List<Event> nonWeekendEvents = events.stream().filter(NON_WEEKEND).toList();

    assertEquals(1, nonWeekendEvents.size());
    assertEquals(LocalDate.of(year, 6, 15), nonWeekendEvents.get(0).date());
  }

  // === Leap Year Tests ===

  @ParameterizedTest
  @DisplayName("Leap years have Feb 29 event")
  @ValueSource(ints = {2000, 2004, 2020, 2024, 2028, 2096})
  void leapYearsHaveFeb29Event(int year) {
    ResolvedSpec spec = resolver.resolve("LEAP-YEAR");
    LocalDate from = LocalDate.of(year, 1, 1);
    LocalDate to = LocalDate.of(year, 12, 31);

    List<Event> events = generator.generate(spec, from, to);
    List<Event> nonWeekendEvents = events.stream().filter(NON_WEEKEND).toList();

    assertEquals(1, nonWeekendEvents.size());
    assertEquals(LocalDate.of(year, 2, 29), nonWeekendEvents.get(0).date());
  }

  @ParameterizedTest
  @DisplayName("Non-leap years have no Feb 29 event")
  @ValueSource(ints = {1999, 2001, 2023, 2025, 2100})
  void nonLeapYearsNoFeb29Event(int year) {
    ResolvedSpec spec = resolver.resolve("LEAP-YEAR");
    LocalDate from = LocalDate.of(year, 1, 1);
    LocalDate to = LocalDate.of(year, 12, 31);

    List<Event> events = generator.generate(spec, from, to);
    List<Event> nonWeekendEvents = events.stream().filter(NON_WEEKEND).toList();

    assertTrue(
        nonWeekendEvents.isEmpty(), "Non-leap year " + year + " should have no Feb 29 event");
  }

  // === Nth Weekday Edge Cases ===

  static Stream<Arguments> fifthWeekdayTestCases() {
    return Stream.of(
        // year, month, expectedFifthMondays
        Arguments.of(2024, 1, true), // January 2024 has 5 Mondays (1, 8, 15, 22, 29)
        Arguments.of(2024, 2, false), // February 2024 has 4 Mondays
        Arguments.of(2024, 4, true), // April 2024 has 5 Mondays
        Arguments.of(2024, 7, true), // July 2024 has 5 Mondays
        Arguments.of(2024, 9, true), // September 2024 has 5 Mondays
        Arguments.of(2024, 12, true) // December 2024 has 5 Mondays
        );
  }

  @ParameterizedTest
  @DisplayName("5th weekday of month edge cases")
  @MethodSource("fifthWeekdayTestCases")
  void fifthWeekdayEdgeCases(int year, int month, boolean hasFifthMonday) {
    // FIFTH-WEEKDAY calendar has "5th Monday of February" rule
    // This tests if our generator handles "doesn't exist" scenarios
    ResolvedSpec spec = resolver.resolve("FIFTH-WEEKDAY");
    LocalDate from = LocalDate.of(year, month, 1);
    LocalDate to = from.plusMonths(1).minusDays(1);

    List<Event> events = generator.generate(spec, from, to);
    List<Event> nonWeekendEvents = events.stream().filter(NON_WEEKEND).toList();

    // Our FIFTH-WEEKDAY calendar uses February, so only Feb months matter
    if (month == 2) {
      assertEquals(
          hasFifthMonday ? 1 : 0,
          nonWeekendEvents.size(),
          String.format(
              "Year %d Feb should %shave 5th Monday", year, hasFifthMonday ? "" : "not "));
    }
  }

  // === Year Boundary Tests ===

  @Test
  @DisplayName("Events spanning year boundary")
  void eventsSpanningYearBoundary() {
    ResolvedSpec spec = resolver.resolve("YEAR-BOUNDARY");
    LocalDate from = LocalDate.of(2024, 12, 15);
    LocalDate to = LocalDate.of(2025, 1, 15);

    List<Event> events = generator.generate(spec, from, to);
    List<Event> nonWeekendEvents = events.stream().filter(NON_WEEKEND).toList();

    assertEquals(2, nonWeekendEvents.size());
    assertTrue(
        nonWeekendEvents.stream().anyMatch(e -> e.date().equals(LocalDate.of(2024, 12, 31))),
        "Should have New Year's Eve 2024");
    assertTrue(
        nonWeekendEvents.stream().anyMatch(e -> e.date().equals(LocalDate.of(2025, 1, 1))),
        "Should have New Year's Day 2025");
  }

  @Test
  @DisplayName("Multi-year range generates correct events")
  void multiYearRange() {
    ResolvedSpec spec = resolver.resolve("YEAR-BOUNDARY");
    LocalDate from = LocalDate.of(2024, 1, 1);
    LocalDate to = LocalDate.of(2026, 12, 31);

    List<Event> events = generator.generate(spec, from, to);
    List<Event> nonWeekendEvents = events.stream().filter(NON_WEEKEND).toList();

    // 3 years * 2 events/year = 6 events
    assertEquals(6, nonWeekendEvents.size());

    // Verify years
    assertEquals(
        3, nonWeekendEvents.stream().filter(e -> e.description().equals("New Year's Day")).count());
    assertEquals(
        3, nonWeekendEvents.stream().filter(e -> e.description().equals("New Year's Eve")).count());
  }

  // === Hijri Calendar Tests ===

  @Test
  @DisplayName("Hijri dates drift across years")
  void hijriDatesDrift() {
    ResolvedSpec spec = resolver.resolve("USES-MODULES");

    // Get Hijri events over a 3-year span to catch multiple occurrences
    List<Event> events =
        generator.generate(spec, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31));

    // Find all Hijri holidays
    List<Event> hijriEvents =
        events.stream().filter(e -> e.description().contains("Hijri")).sorted().toList();

    // Should have multiple occurrences over 3 Gregorian years
    // The Hijri year is ~354 days, so we should see ~3+ occurrences
    assertTrue(
        hijriEvents.size() >= 3,
        "Should have at least 3 Hijri holidays over 3 Gregorian years, found: "
            + hijriEvents.size());

    // Verify the dates are in different parts of the year (demonstrating drift)
    // The same Hijri date drifts ~11 days earlier each Gregorian year
    boolean hasDifferentMonths =
        hijriEvents.stream().map(e -> e.date().getMonthValue()).distinct().count() > 1;
    assertTrue(
        hasDifferentMonths,
        "Hijri holidays should fall in different Gregorian months due to drift");
  }

  // === Production Calendar Tests ===

  @Test
  @DisplayName("US-MARKET-BASE has consistent events across years")
  void usMarketBaseConsistency() {
    SpecResolver prodResolver = new SpecResolver(prodRegistry);
    ResolvedSpec spec = prodResolver.resolve("US-MARKET-BASE");

    // Core US holidays should exist every year, but observed holidays can shift across
    // year boundaries (e.g., New Year's Day on Saturday shifts to Fri Dec 31 of previous year).
    // Instead of checking exact count per calendar year, verify we have the right total
    // events over a multi-year period and that key holidays appear.

    // Generate over the full range
    List<Event> allEvents =
        generator.generate(spec, LocalDate.of(2020, 1, 1), LocalDate.of(2030, 12, 31));
    List<Event> nonWeekendEvents = allEvents.stream().filter(NON_WEEKEND).toList();

    // Over 11 years, we should have roughly 66 events (6 holidays * 11 years)
    // Some years may have ±1 due to observed holiday shifts across year boundaries
    assertTrue(
        nonWeekendEvents.size() >= 64 && nonWeekendEvents.size() <= 68,
        "US-MARKET-BASE should have ~66 events over 11 years, got " + nonWeekendEvents.size());

    // Verify each key holiday type appears approximately 11 times
    long newYears =
        nonWeekendEvents.stream().filter(e -> e.description().equals("New Year's Day")).count();
    long christmas =
        nonWeekendEvents.stream().filter(e -> e.description().equals("Christmas Day")).count();
    long thanksgiving =
        nonWeekendEvents.stream().filter(e -> e.description().equals("Thanksgiving Day")).count();

    assertEquals(11, newYears, "Should have 11 New Year's Day observations");
    assertEquals(11, christmas, "Should have 11 Christmas Day observations");
    assertEquals(11, thanksgiving, "Should have 11 Thanksgiving Day observations");
  }

  // === Inverted Range Test ===

  @Test
  @DisplayName("Inverted range (from > to) throws IllegalArgumentException")
  void invertedRangeThrows() {
    ResolvedSpec spec = resolver.resolve("SIMPLE");
    LocalDate from = LocalDate.of(2024, 12, 31);
    LocalDate to = LocalDate.of(2024, 1, 1);

    assertThrows(
        IllegalArgumentException.class,
        () -> generator.generate(spec, from, to),
        "Inverted range should throw IllegalArgumentException");
  }
}
