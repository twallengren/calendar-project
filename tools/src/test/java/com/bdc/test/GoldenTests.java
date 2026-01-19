package com.bdc.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Golden tests for calendar generation.
 *
 * These tests compare generated output against pre-recorded expected outputs.
 * To update the golden files, run:
 *   ./gradlew test -DupdateGoldens=true
 * or set the environment variable UPDATE_GOLDENS=true
 */
class GoldenTests {

    private static GoldenTestRunner testCalendarRunner;
    private static GoldenTestRunner productionCalendarRunner;

    @BeforeAll
    static void setup() throws Exception {
        testCalendarRunner = GoldenTestRunner.forTestCalendars();
        productionCalendarRunner = GoldenTestRunner.forProductionCalendars();
    }

    // Test calendar golden tests

    @Test
    void simpleCalendar2024() throws IOException {
        testCalendarRunner.assertCsvGoldenMatch("SIMPLE", 2024);
    }

    @Test
    void usesModulesCalendar2024() throws IOException {
        testCalendarRunner.assertCsvGoldenMatch("USES-MODULES", 2024);
    }

    @Test
    void deepInheritanceCalendar2024() throws IOException {
        testCalendarRunner.assertCsvGoldenMatch("DEEP-INHERITANCE", 2024);
    }

    @Test
    void diamondCalendar2024() throws IOException {
        testCalendarRunner.assertCsvGoldenMatch("DIAMOND", 2024);
    }

    @Test
    void yearBoundaryCalendar2024() throws IOException {
        testCalendarRunner.assertCsvGoldenMatch("YEAR-BOUNDARY", 2024);
    }

    @Test
    void leapYearCalendar2024() throws IOException {
        testCalendarRunner.assertCsvGoldenMatch("LEAP-YEAR", 2024);
    }

    @Test
    void emptyCalendar2024() throws IOException {
        testCalendarRunner.assertCsvGoldenMatch("EMPTY", 2024);
    }

    // Production calendar golden tests

    @Test
    void usMarketBase2024() throws IOException {
        productionCalendarRunner.assertCsvGoldenMatch("US-MARKET-BASE", 2024);
    }

    @Test
    void usNyse2024() throws IOException {
        productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 2024);
    }

    // Multi-year range tests

    @Test
    void usMarketBase2024to2025() throws IOException {
        productionCalendarRunner.assertCsvGoldenMatch("US-MARKET-BASE", 2024, 2025);
    }

    // Metadata golden tests (with normalized timestamps)

    @Test
    void usMarketBase2024Metadata() throws IOException {
        productionCalendarRunner.assertMetadataGoldenMatch("US-MARKET-BASE", 2024);
    }
}
