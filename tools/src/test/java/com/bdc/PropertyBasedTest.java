package com.bdc;

import com.bdc.emitter.CsvEmitter;
import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PropertyBasedTest {

    private static final Path TEST_CALENDARS_DIR = Path.of("tools/src/test/resources/test-calendars/calendars");
    private static final Path TEST_MODULES_DIR = Path.of("tools/src/test/resources/test-calendars/modules");

    private SpecRegistry createTestRegistry() throws Exception {
        SpecRegistry registry = new SpecRegistry();
        registry.loadCalendarsFromDirectory(TEST_CALENDARS_DIR);
        registry.loadModulesFromDirectory(TEST_MODULES_DIR);
        return registry;
    }

    @Provide
    Arbitrary<String> validCalendarIds() {
        return Arbitraries.of(
            "SIMPLE",
            "INHERITS-SIMPLE",
            "USES-MODULES",
            "DEEP-INHERITANCE",
            "DIAMOND",
            "LEAP-YEAR",
            "YEAR-BOUNDARY",
            "EMPTY"
        );
    }

    @Provide
    Arbitrary<Integer> years() {
        return Arbitraries.integers().between(1990, 2099);
    }

    @Property(tries = 50)
    void resolvedSpecIsIdempotent(@ForAll("validCalendarIds") String calendarId) throws Exception {
        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);

        ResolvedSpec r1 = resolver.resolve(calendarId);

        // Clear cache and resolve again
        resolver.clearCache();
        ResolvedSpec r2 = resolver.resolve(calendarId);

        assertEquals(r1.id(), r2.id());
        assertEquals(r1.eventSources().size(), r2.eventSources().size());
        assertEquals(r1.weekendPolicy(), r2.weekendPolicy());
        assertEquals(r1.classifications(), r2.classifications());
        assertEquals(r1.deltas().size(), r2.deltas().size());
    }

    @Property(tries = 100)
    void eventsAreSortedByDate(
            @ForAll("validCalendarIds") String calendarId,
            @ForAll("years") int year) throws Exception {

        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);
        EventGenerator generator = new EventGenerator();

        ResolvedSpec spec = resolver.resolve(calendarId);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<Event> events = generator.generate(spec, from, to);

        // Verify events are sorted
        List<Event> sorted = events.stream()
            .sorted()
            .toList();

        assertEquals(sorted, events, "Events should be sorted");
    }

    @Property(tries = 100)
    void noEventsOutsideRange(
            @ForAll("validCalendarIds") String calendarId,
            @ForAll @IntRange(min = 2000, max = 2050) int year,
            @ForAll @IntRange(min = 1, max = 12) int startMonth,
            @ForAll @IntRange(min = 1, max = 12) int endMonth) throws Exception {

        // Ensure valid range
        int actualStartMonth = Math.min(startMonth, endMonth);
        int actualEndMonth = Math.max(startMonth, endMonth);

        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);
        EventGenerator generator = new EventGenerator();

        ResolvedSpec spec = resolver.resolve(calendarId);
        LocalDate from = LocalDate.of(year, actualStartMonth, 1);
        LocalDate to = LocalDate.of(year, actualEndMonth, 1).plusMonths(1).minusDays(1);

        List<Event> events = generator.generate(spec, from, to);

        for (Event event : events) {
            assertTrue(
                !event.date().isBefore(from) && !event.date().isAfter(to),
                "Event date " + event.date() + " should be within range [" + from + ", " + to + "]"
            );
        }
    }

    @Property(tries = 50)
    void deterministicOutput(
            @ForAll("validCalendarIds") String calendarId,
            @ForAll("years") int year) throws Exception {

        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);
        EventGenerator generator = new EventGenerator();
        CsvEmitter emitter = new CsvEmitter();

        ResolvedSpec spec = resolver.resolve(calendarId);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<Event> events1 = generator.generate(spec, from, to);
        List<Event> events2 = generator.generate(spec, from, to);

        String csv1 = emitter.emitToString(events1);
        String csv2 = emitter.emitToString(events2);

        assertEquals(csv1, csv2, "Output should be deterministic");
    }

    @Property(tries = 20)
    void diamondDependencyProducesUniqueEvents() throws Exception {
        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);
        EventGenerator generator = new EventGenerator();

        ResolvedSpec spec = resolver.resolve("DIAMOND");
        List<Event> events = generator.generate(
            spec,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // shared_module should only contribute one event, not two (from branch_a and branch_b)
        long sharedCount = events.stream()
            .filter(e -> e.description().equals("Shared Holiday"))
            .count();

        assertEquals(1, sharedCount,
            "Shared module's holiday should appear exactly once despite diamond dependency");
    }

    @Property(tries = 20)
    void deepInheritancePreservesAllEvents() throws Exception {
        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);
        EventGenerator generator = new EventGenerator();

        ResolvedSpec spec = resolver.resolve("DEEP-INHERITANCE");
        List<Event> events = generator.generate(
            spec,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // Should have events from all three levels
        assertTrue(events.stream().anyMatch(e -> e.description().equals("Level A Event")),
            "Should have Level A Event from base");
        assertTrue(events.stream().anyMatch(e -> e.description().equals("Level B Event")),
            "Should have Level B Event from middle");
        assertTrue(events.stream().anyMatch(e -> e.description().equals("Deep Inheritance Event")),
            "Should have Deep Inheritance Event from child");
    }

    @Property(tries = 10)
    void emptyCalendarProducesNoEvents() throws Exception {
        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);
        EventGenerator generator = new EventGenerator();

        ResolvedSpec spec = resolver.resolve("EMPTY");

        for (int year = 2020; year <= 2030; year++) {
            List<Event> events = generator.generate(
                spec,
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
            );
            assertTrue(events.isEmpty(),
                "Empty calendar should produce no events in year " + year);
        }
    }

    @Property(tries = 20)
    void leapYearHandling() throws Exception {
        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);
        EventGenerator generator = new EventGenerator();

        ResolvedSpec spec = resolver.resolve("LEAP-YEAR");

        // Leap years: 2024, 2028
        List<Event> events2024 = generator.generate(
            spec,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );
        assertEquals(1, events2024.size(), "Leap year 2024 should have Feb 29 event");
        assertEquals(LocalDate.of(2024, 2, 29), events2024.get(0).date());

        // Non-leap years: 2023, 2025
        List<Event> events2023 = generator.generate(
            spec,
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 12, 31)
        );
        assertTrue(events2023.isEmpty(), "Non-leap year 2023 should have no Feb 29 event");
    }

    @Property(tries = 10)
    void yearBoundaryEventsInSingleYearRange() throws Exception {
        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);
        EventGenerator generator = new EventGenerator();

        ResolvedSpec spec = resolver.resolve("YEAR-BOUNDARY");

        List<Event> events2024 = generator.generate(
            spec,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // Should have Jan 1 2024 and Dec 31 2024
        assertTrue(events2024.stream().anyMatch(e ->
            e.date().equals(LocalDate.of(2024, 1, 1))),
            "Should have New Year's Day 2024");
        assertTrue(events2024.stream().anyMatch(e ->
            e.date().equals(LocalDate.of(2024, 12, 31))),
            "Should have New Year's Eve 2024");
    }

    @Property(tries = 10)
    void yearBoundaryEventsAcrossYears() throws Exception {
        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);
        EventGenerator generator = new EventGenerator();

        ResolvedSpec spec = resolver.resolve("YEAR-BOUNDARY");

        // Range crossing year boundary
        List<Event> events = generator.generate(
            spec,
            LocalDate.of(2024, 12, 1),
            LocalDate.of(2025, 1, 31)
        );

        // Should have Dec 31 2024 and Jan 1 2025
        assertTrue(events.stream().anyMatch(e ->
            e.date().equals(LocalDate.of(2024, 12, 31))),
            "Should have New Year's Eve 2024");
        assertTrue(events.stream().anyMatch(e ->
            e.date().equals(LocalDate.of(2025, 1, 1))),
            "Should have New Year's Day 2025");
    }

    @Property(tries = 30)
    void fifthWeekdayMayNotExist() throws Exception {
        SpecRegistry registry = createTestRegistry();
        SpecResolver resolver = new SpecResolver(registry);
        EventGenerator generator = new EventGenerator();

        ResolvedSpec spec = resolver.resolve("FIFTH-WEEKDAY");

        // February rarely has 5 Mondays
        // 2024: Feb has 5 Thursdays but only 4 Mondays (starts on Thursday)
        List<Event> events2024 = generator.generate(
            spec,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // The fifth Monday rule for February should produce 0 events in 2024
        assertTrue(events2024.isEmpty(),
            "Fifth Monday of February 2024 doesn't exist (Feb 2024 starts on Thursday)");

        // 2028: February starts on Tuesday, has 5 Tuesdays but only 4 Mondays
        List<Event> events2028 = generator.generate(
            spec,
            LocalDate.of(2028, 1, 1),
            LocalDate.of(2028, 12, 31)
        );
        assertTrue(events2028.isEmpty(),
            "Fifth Monday of February 2028 doesn't exist");
    }
}
