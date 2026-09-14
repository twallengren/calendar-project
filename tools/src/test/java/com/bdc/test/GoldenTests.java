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

  // Euronext Paris years that exercise the no-weekend-observance rule and the year-end half
  // days: 2022 Sat Jan 1 and Sun Christmas with no substitute weekday and no Dec 24/31 half
  // days (both Saturdays); 2026 Sat Boxing Day not observed, Dec 24 and Dec 31 half days at
  // 14:05.
  @Test
  void frEuronextParis2022() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("FR-EURONEXT-PARIS", 2022);
  }

  @Test
  void frEuronextParis2026() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("FR-EURONEXT-PARIS", 2026);
  }

  // JP-JPX years that exercise the Japanese substitute-holiday rule and the one-off moves:
  // 2021 Olympic moves (Marine Day Jul 22, Sports Day Jul 23, Mountain Day Sun Aug 8 observed
  // Mon Aug 9) and Jan 2/Jan 3 market holidays on a weekend; 2026 citizens' holiday (Sep 22)
  // and a Sunday Constitution Memorial Day cascading past May 4 and May 5 to May 6.
  @Test
  void jpJpx2021() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("JP-JPX", 2021);
  }

  @Test
  void jpJpx2026() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("JP-JPX", 2026);
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

  // GB-LSE years that exercise UK weekend observance and early-close rules:
  // 2022 Sat Christmas -> Mon 27 Dec (cascade with Sun Boxing Day -> Tue 28 Dec), Sat 1 Jan not
  // observed until 3 Jan, Spring bank holiday moved for the Platinum Jubilee plus the extra
  // Jubilee day, and the State Funeral of Queen Elizabeth II; 2028 Sun Dec 24/31 shifted
  // half-day closes (Fri 22 Dec, Fri 29 Dec) and Sun 1 Jan 2028 -> Mon 3 Jan.
  @Test
  void gbLse2022() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("GB-LSE", 2022);
  }

  @Test
  void gbLse2028() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("GB-LSE", 2028);
  }

  // DE-XETRA years: 2017 exercises the discretionary Whit Monday and German Unity Day
  // closures plus the one-off 2017 Reformation Day; 2021 has Christmas Day on a Saturday
  // (weekend_shift_policy: NONE means it is not observed on an adjacent weekday).
  @Test
  void deXetra2017() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("DE-XETRA", 2017);
  }

  @Test
  void deXetra2021() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("DE-XETRA", 2021);
  }

  // CA-TSX golden tests
  // 2026: ordinary year exercising every rule type (Family Day, Good Friday, Victoria Day,
  // Canada Day, Civic Holiday, Labour Day, Thanksgiving, Christmas Eve early close, Christmas
  // Day, Boxing Day) with no weekend shifting needed.
  // 2021: Christmas Day falls on Saturday, exercising the weekend-cascade case: Christmas
  // observed Monday Dec 27, Boxing Day (nominal Sunday) observed Tuesday Dec 28, plus the
  // Dec 24 early close.

  @Test
  void caTsx2026() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("CA-TSX", 2026);
  }

  @Test
  void caTsx2021() throws IOException {
    productionCalendarRunner.assertCsvGoldenMatch("CA-TSX", 2021);
  }
}
