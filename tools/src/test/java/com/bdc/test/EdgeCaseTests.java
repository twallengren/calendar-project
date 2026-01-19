package com.bdc.test;

import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for calendar generation.
 * Tests boundary conditions and unusual scenarios.
 */
class EdgeCaseTests {

    private static SpecRegistry testRegistry;
    private static SpecRegistry prodRegistry;
    private SpecResolver resolver;
    private EventGenerator generator;

    @BeforeAll
    static void setupRegistry() throws Exception {
        testRegistry = new SpecRegistry();
        testRegistry.loadCalendarsFromDirectory(Path.of("tools/src/test/resources/test-calendars/calendars"));
        testRegistry.loadModulesFromDirectory(Path.of("tools/src/test/resources/test-calendars/modules"));

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

        List<Event> events = generator.generate(spec, date, date);

        // Should find the New Year's Day event
        assertEquals(1, events.size());
        assertEquals(LocalDate.of(2024, 1, 1), events.get(0).date());
    }

    @Test
    @DisplayName("Single day range with no event returns empty list")
    void singleDayRangeNoEvent() {
        ResolvedSpec spec = resolver.resolve("SIMPLE");
        // Test Holiday is June 15, pick a different day
        LocalDate date = LocalDate.of(2024, 3, 15);

        List<Event> events = generator.generate(spec, date, date);

        assertTrue(events.isEmpty());
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

        // Should have exactly 1 event (Test Holiday on June 15)
        assertEquals(1, events.size());
        assertEquals(LocalDate.of(year, 6, 15), events.get(0).date());
    }

    @ParameterizedTest
    @DisplayName("Historical years should work")
    @ValueSource(ints = {1990, 1995, 2000, 2010})
    void historicalYearsWork(int year) {
        ResolvedSpec spec = resolver.resolve("SIMPLE");
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<Event> events = generator.generate(spec, from, to);

        assertEquals(1, events.size());
        assertEquals(LocalDate.of(year, 6, 15), events.get(0).date());
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

        assertEquals(1, events.size());
        assertEquals(LocalDate.of(year, 2, 29), events.get(0).date());
    }

    @ParameterizedTest
    @DisplayName("Non-leap years have no Feb 29 event")
    @ValueSource(ints = {1999, 2001, 2023, 2025, 2100})
    void nonLeapYearsNoFeb29Event(int year) {
        ResolvedSpec spec = resolver.resolve("LEAP-YEAR");
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<Event> events = generator.generate(spec, from, to);

        assertTrue(events.isEmpty(), "Non-leap year " + year + " should have no Feb 29 event");
    }

    // === Nth Weekday Edge Cases ===

    static Stream<Arguments> fifthWeekdayTestCases() {
        return Stream.of(
            // year, month, expectedFifthMondays
            Arguments.of(2024, 1, true),   // January 2024 has 5 Mondays (1, 8, 15, 22, 29)
            Arguments.of(2024, 2, false),  // February 2024 has 4 Mondays
            Arguments.of(2024, 4, true),   // April 2024 has 5 Mondays
            Arguments.of(2024, 7, true),   // July 2024 has 5 Mondays
            Arguments.of(2024, 9, true),   // September 2024 has 5 Mondays
            Arguments.of(2024, 12, true)   // December 2024 has 5 Mondays
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

        // Our FIFTH-WEEKDAY calendar uses February, so only Feb months matter
        if (month == 2) {
            assertEquals(hasFifthMonday ? 1 : 0, events.size(),
                String.format("Year %d Feb should %shave 5th Monday",
                    year, hasFifthMonday ? "" : "not "));
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

        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e ->
            e.date().equals(LocalDate.of(2024, 12, 31))),
            "Should have New Year's Eve 2024");
        assertTrue(events.stream().anyMatch(e ->
            e.date().equals(LocalDate.of(2025, 1, 1))),
            "Should have New Year's Day 2025");
    }

    @Test
    @DisplayName("Multi-year range generates correct events")
    void multiYearRange() {
        ResolvedSpec spec = resolver.resolve("YEAR-BOUNDARY");
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        List<Event> events = generator.generate(spec, from, to);

        // 3 years * 2 events/year = 6 events
        assertEquals(6, events.size());

        // Verify years
        assertEquals(3, events.stream()
            .filter(e -> e.description().equals("New Year's Day"))
            .count());
        assertEquals(3, events.stream()
            .filter(e -> e.description().equals("New Year's Eve"))
            .count());
    }

    // === Hijri Calendar Tests ===

    @Test
    @DisplayName("Hijri dates drift across years")
    void hijriDatesDrift() {
        ResolvedSpec spec = resolver.resolve("USES-MODULES");

        // Get Hijri events over a 3-year span to catch multiple occurrences
        List<Event> events = generator.generate(spec,
            LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31));

        // Find all Hijri holidays
        List<Event> hijriEvents = events.stream()
            .filter(e -> e.description().contains("Hijri"))
            .sorted()
            .toList();

        // Should have multiple occurrences over 3 Gregorian years
        // The Hijri year is ~354 days, so we should see ~3+ occurrences
        assertTrue(hijriEvents.size() >= 3,
            "Should have at least 3 Hijri holidays over 3 Gregorian years, found: " + hijriEvents.size());

        // Verify the dates are in different parts of the year (demonstrating drift)
        // The same Hijri date drifts ~11 days earlier each Gregorian year
        boolean hasDifferentMonths = hijriEvents.stream()
            .map(e -> e.date().getMonthValue())
            .distinct()
            .count() > 1;
        assertTrue(hasDifferentMonths,
            "Hijri holidays should fall in different Gregorian months due to drift");
    }

    // === Production Calendar Tests ===

    @Test
    @DisplayName("US-MARKET-BASE has consistent events across years")
    void usMarketBaseConsistency() {
        SpecResolver prodResolver = new SpecResolver(prodRegistry);
        ResolvedSpec spec = prodResolver.resolve("US-MARKET-BASE");

        // Core US holidays should exist every year
        for (int year = 2020; year <= 2030; year++) {
            List<Event> events = generator.generate(spec,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));

            // Should have 6 events: New Year's, Memorial Day, Independence Day,
            // Labor Day, Thanksgiving, Christmas
            assertEquals(6, events.size(),
                "US-MARKET-BASE should have 6 events in " + year);

            // Verify key holidays
            assertTrue(events.stream().anyMatch(e ->
                e.description().equals("New Year's Day")),
                "Should have New Year's Day in " + year);
            assertTrue(events.stream().anyMatch(e ->
                e.description().equals("Christmas Day")),
                "Should have Christmas Day in " + year);
        }
    }

    // === Inverted Range Test ===

    @Test
    @DisplayName("Inverted range (from > to) throws IllegalArgumentException")
    void invertedRangeThrows() {
        ResolvedSpec spec = resolver.resolve("SIMPLE");
        LocalDate from = LocalDate.of(2024, 12, 31);
        LocalDate to = LocalDate.of(2024, 1, 1);

        assertThrows(IllegalArgumentException.class, () ->
            generator.generate(spec, from, to),
            "Inverted range should throw IllegalArgumentException");
    }
}
