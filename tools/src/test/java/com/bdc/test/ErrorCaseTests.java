package com.bdc.test;

import com.bdc.generator.EventGenerator;
import com.bdc.generator.RuleExpander;
import com.bdc.chronology.DateRange;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.ResolvedSpec;
import com.bdc.model.Rule;
import com.bdc.resolver.SpecResolver;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Error case tests for calendar generation.
 * Tests that the system properly handles and reports errors.
 */
class ErrorCaseTests {

    private static SpecRegistry testRegistry;
    private SpecResolver resolver;

    @BeforeAll
    static void setupRegistry() throws Exception {
        testRegistry = new SpecRegistry();
        testRegistry.loadCalendarsFromDirectory(Path.of("tools/src/test/resources/test-calendars/calendars"));
        testRegistry.loadModulesFromDirectory(Path.of("tools/src/test/resources/test-calendars/modules"));
    }

    @BeforeEach
    void setup() {
        resolver = new SpecResolver(testRegistry);
    }

    // === Missing Reference Tests ===

    @Test
    @DisplayName("Missing calendar reference throws IllegalArgumentException")
    void missingCalendarReference() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            resolver.resolve("NONEXISTENT-CALENDAR"),
            "Should throw for missing calendar");

        assertTrue(ex.getMessage().contains("Calendar not found") ||
                   ex.getMessage().contains("NONEXISTENT"),
            "Error message should indicate calendar not found: " + ex.getMessage());
    }

    @Test
    @DisplayName("Calendar with missing extends reference throws")
    void missingExtendsReference() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            resolver.resolve("MISSING-EXTENDS-REF"),
            "Should throw for missing extends reference");

        assertTrue(ex.getMessage().contains("Calendar not found") ||
                   ex.getMessage().contains("NONEXISTENT"),
            "Error message should indicate calendar not found: " + ex.getMessage());
    }

    @Test
    @DisplayName("Calendar with missing module reference throws")
    void missingModuleReference() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            resolver.resolve("MISSING-MODULE-REF"),
            "Should throw for missing module reference");

        assertTrue(ex.getMessage().contains("Module not found") ||
                   ex.getMessage().contains("nonexistent"),
            "Error message should indicate module not found: " + ex.getMessage());
    }

    // === Circular Dependency Tests ===

    @Test
    @DisplayName("Circular extends throws IllegalStateException")
    void circularExtendsThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            resolver.resolve("CIRCULAR-EXTENDS-A"),
            "Should throw for circular extends");

        assertTrue(ex.getMessage().toLowerCase().contains("circular"),
            "Error message should mention circular dependency: " + ex.getMessage());
    }

    @Test
    @DisplayName("Circular module uses throws IllegalStateException")
    void circularUsesThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            resolver.resolve("CIRCULAR-USES"),
            "Should throw for circular module uses");

        assertTrue(ex.getMessage().toLowerCase().contains("circular"),
            "Error message should mention circular dependency: " + ex.getMessage());
    }

    // === Invalid Date Range Tests ===

    @Test
    @DisplayName("Inverted date range throws IllegalArgumentException")
    void invertedDateRangeThrows() {
        ResolvedSpec spec = resolver.resolve("SIMPLE");
        EventGenerator generator = new EventGenerator();

        LocalDate from = LocalDate.of(2024, 12, 31);
        LocalDate to = LocalDate.of(2024, 1, 1); // Before 'from'

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            generator.generate(spec, from, to),
            "Should throw for inverted date range");

        assertTrue(ex.getMessage().contains("start must not be after end"),
            "Error message should mention invalid range: " + ex.getMessage());
    }

    // === Invalid Rule Parameter Tests ===

    @Test
    @DisplayName("Invalid month in fixed_month_day rule")
    void invalidMonthInFixedMonthDay() {
        RuleExpander expander = new RuleExpander();
        DateRange range = new DateRange(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // Month 13 is invalid
        Rule.FixedMonthDay invalidRule = new Rule.FixedMonthDay(
            "invalid", "Invalid Holiday", 13, 1, "ISO"
        );

        // The rule expander should handle this gracefully (skip invalid dates)
        // or throw an exception - let's test the current behavior
        var occurrences = expander.expand(invalidRule, range, "test");

        // Invalid month should result in no valid occurrences
        assertTrue(occurrences.isEmpty(),
            "Invalid month should result in no occurrences");
    }

    @Test
    @DisplayName("Invalid day in fixed_month_day rule")
    void invalidDayInFixedMonthDay() {
        RuleExpander expander = new RuleExpander();
        DateRange range = new DateRange(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // February 31 is invalid
        Rule.FixedMonthDay invalidRule = new Rule.FixedMonthDay(
            "invalid", "Invalid Holiday", 2, 31, "ISO"
        );

        var occurrences = expander.expand(invalidRule, range, "test");

        // Invalid day should result in no valid occurrences
        assertTrue(occurrences.isEmpty(),
            "Invalid day should result in no occurrences");
    }

    @Test
    @DisplayName("Nth weekday that doesn't exist returns empty")
    void nthWeekdayDoesntExist() {
        RuleExpander expander = new RuleExpander();
        // February 2024 starts on Thursday, so it has only 4 Mondays
        DateRange range = new DateRange(
            LocalDate.of(2024, 2, 1),
            LocalDate.of(2024, 2, 29)
        );

        // 5th Monday of February 2024 doesn't exist
        Rule.NthWeekdayOfMonth rule = new Rule.NthWeekdayOfMonth(
            "fifth_monday", "Fifth Monday", 2, DayOfWeek.MONDAY, 5
        );

        var occurrences = expander.expand(rule, range, "test");

        assertTrue(occurrences.isEmpty(),
            "5th Monday of February 2024 should not exist");
    }

    @Test
    @DisplayName("Nth value of 0 returns empty")
    void nthWeekdayZeroReturnsEmpty() {
        RuleExpander expander = new RuleExpander();
        DateRange range = new DateRange(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // nth = 0 is invalid (should be 1, 2, 3, etc. or -1 for last)
        Rule.NthWeekdayOfMonth rule = new Rule.NthWeekdayOfMonth(
            "zeroth_monday", "Zeroth Monday", 1, DayOfWeek.MONDAY, 0
        );

        var occurrences = expander.expand(rule, range, "test");

        assertTrue(occurrences.isEmpty(),
            "nth=0 should result in no occurrences");
    }

    // === Null and Empty Input Tests ===

    @Test
    @DisplayName("Null calendar ID throws")
    void nullCalendarIdThrows() {
        assertThrows(Exception.class, () ->
            resolver.resolve(null),
            "Should throw for null calendar ID");
    }

    @Test
    @DisplayName("Empty calendar ID throws")
    void emptyCalendarIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            resolver.resolve(""),
            "Should throw for empty calendar ID");
    }

    // === Resolved Spec Integrity Tests ===

    @Test
    @DisplayName("Resolved spec has non-null required fields")
    void resolvedSpecIntegrity() {
        ResolvedSpec spec = resolver.resolve("SIMPLE");

        assertNotNull(spec.id(), "Spec ID should not be null");
        assertNotNull(spec.eventSources(), "Event sources should not be null");
        assertNotNull(spec.weekendPolicy(), "Weekend policy should not be null");
        assertNotNull(spec.classifications(), "Classifications should not be null");
        assertNotNull(spec.deltas(), "Deltas should not be null");
        assertNotNull(spec.resolutionChain(), "Resolution chain should not be null");

        assertFalse(spec.id().isEmpty(), "Spec ID should not be empty");
    }
}
