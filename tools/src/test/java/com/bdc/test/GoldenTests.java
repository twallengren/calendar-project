package com.bdc.test;

import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Golden tests for calendar generation.
 *
 * <p>These tests compare generated output against pre-recorded expected outputs. To update the
 * golden files, run: ./gradlew test -DupdateGoldens=true or set the environment variable
 * UPDATE_GOLDENS=true
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

  // NYSE years that exercise weekend observance and early-close rules:
  // 2020 Sat Jul 4 -> Fri Jul 3; 2021 Sat Christmas -> Fri Dec 24 and Dec 31 open;
  // 2022 Sat Jan 1 not observed, Sun Juneteenth/Christmas -> Monday; 2023 Mon Jul 3 early close;
  // 2025 Carter closure, Thu Jul 3 early close; 2027 Sat Christmas; 2028 Sat Jan 1;
  // 2012 Hurricane Sandy; 2018 Bush closure; 1952 last Saturday sessions; 1968 paperwork crisis.
  @Test
  void usNyse2020() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 2020);
  }

  @Test
  void usNyse2021() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 2021);
  }

  @Test
  void usNyse2022() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 2022);
  }

  @Test
  void usNyse2023() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 2023);
  }

  @Test
  void usNyse2025() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 2025);
  }

  @Test
  void usNyse2027() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 2027);
  }

  @Test
  void usNyse2028() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 2028);
  }

  @Test
  void usNyse2012() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 2012);
  }

  @Test
  void usNyse2018() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 2018);
  }

  @Test
  void usNyse1952() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 1952);
  }

  @Test
  void usNyse1968() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("US-NYSE", 1968);
  }

  @Test
  void saTadawul2026to2030() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("SA-TADAWUL", 2026, 2030);
  }

  @Test
  void saTadawul2026to2030Metadata() throws IOException {
    productionCalendarRunner.assertMetadataGoldenMatch("SA-TADAWUL", 2026, 2030);
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
