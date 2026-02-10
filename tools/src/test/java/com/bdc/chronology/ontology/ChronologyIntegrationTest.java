package com.bdc.chronology.ontology;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.ChronologyTranslator;
import com.bdc.chronology.DateRange;
import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the chronology ontology system.
 *
 * <p>These tests verify that YAML-defined chronologies integrate correctly with the existing
 * ChronologyTranslator and DateRange classes.
 */
class ChronologyIntegrationTest {

  private ChronologyRegistry registry;
  private ChronologyLoader loader;

  @BeforeEach
  void setUp() {
    registry = ChronologyRegistry.getInstance();
    registry.reset();
    loader = new ChronologyLoader();
  }

  @Test
  void translatorUsesRegistry_forIso() {
    LocalDate date = ChronologyTranslator.toIsoDate(2025, 6, 15, "ISO");
    assertEquals(LocalDate.of(2025, 6, 15), date);
  }

  @Test
  void translatorUsesRegistry_forHijri() {
    LocalDate date = ChronologyTranslator.toIsoDate(1446, 1, 1, "HIJRI");
    assertNotNull(date);
    assertEquals(2024, date.getYear());
  }

  @Test
  void translatorUsesRegistry_forJulian() {
    // Julian and Gregorian dates should differ
    LocalDate isoDate = ChronologyTranslator.toIsoDate(2000, 1, 1, "ISO");
    LocalDate julianDate = ChronologyTranslator.toIsoDate(2000, 1, 1, "JULIAN");

    // January 1, 2000 Julian = January 14, 2000 Gregorian
    // (Julian is 13 days behind Gregorian in 2000)
    assertNotEquals(isoDate, julianDate);
    assertEquals(14, julianDate.getDayOfMonth());
  }

  @Test
  void dateRangeYearRange_worksWithRegistry() {
    DateRange range = new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31));

    int[] isoYears = range.yearRange("ISO");
    assertEquals(2024, isoYears[0]);
    assertEquals(2025, isoYears[1]);

    int[] hijriYears = range.yearRange("HIJRI");
    assertTrue(hijriYears[0] <= 1445);
    assertTrue(hijriYears[1] >= 1447);

    int[] julianYears = range.yearRange("JULIAN");
    assertEquals(2023, julianYears[0]); // Julian year starts later
    assertEquals(2025, julianYears[1]);
  }

  @Test
  void yamlLoadedChronology_worksWithTranslator() throws IOException {
    // Load a custom Julian-like calendar from YAML
    String yaml =
        """
        kind: chronology
        id: CUSTOM_JULIAN
        metadata:
          name: Custom Julian Calendar
          description: A custom Julian-like calendar for testing
        structure:
          epoch_jdn: 1721424
          months:
            - {name: January, days: 31}
            - {name: February, days: 28, leap_days: 29}
            - {name: March, days: 31}
            - {name: April, days: 30}
            - {name: May, days: 31}
            - {name: June, days: 30}
            - {name: July, days: 31}
            - {name: August, days: 31}
            - {name: September, days: 30}
            - {name: October, days: 31}
            - {name: November, days: 30}
            - {name: December, days: 31}
        algorithms:
          type: FORMULA
          leap_year: "year % 4 == 0"
        """;

    InputStream is = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    ChronologySpec spec = loader.loadSpec(is);
    ChronologyAlgorithm algorithm = loader.createAlgorithm(spec);
    registry.register(algorithm);

    // Now the translator should work with our custom chronology
    LocalDate date = ChronologyTranslator.toIsoDate(2000, 1, 1, "CUSTOM_JULIAN");
    assertNotNull(date);

    // Should match the built-in Julian
    LocalDate julianDate = ChronologyTranslator.toIsoDate(2000, 1, 1, "JULIAN");
    assertEquals(julianDate, date);
  }

  @Test
  void crossCalendarConversion_isoToHijriToIso() {
    LocalDate original = LocalDate.of(2025, 6, 15);

    // Convert to Hijri
    ChronologyDate hijri = registry.fromIsoDate(original, "HIJRI");
    assertEquals("HIJRI", hijri.chronologyId());

    // Convert back to ISO
    LocalDate roundTripped = registry.toIsoDate(hijri.year(), hijri.month(), hijri.day(), "HIJRI");
    assertEquals(original, roundTripped);
  }

  @Test
  void crossCalendarConversion_isoToJulianToIso() {
    LocalDate original = LocalDate.of(2025, 6, 15);

    // Convert to Julian
    ChronologyDate julian = registry.fromIsoDate(original, "JULIAN");
    assertEquals("JULIAN", julian.chronologyId());

    // Convert back to ISO
    LocalDate roundTripped =
        registry.toIsoDate(julian.year(), julian.month(), julian.day(), "JULIAN");
    assertEquals(original, roundTripped);
  }

  @Test
  void translatorNewMethods_workCorrectly() {
    LocalDate isoDate = LocalDate.of(2025, 6, 15);

    // Test getYear
    int hijriYear = ChronologyTranslator.getYear(isoDate, "HIJRI");
    assertTrue(hijriYear >= 1446);

    // Test getMonth
    int hijriMonth = ChronologyTranslator.getMonth(isoDate, "HIJRI");
    assertTrue(hijriMonth >= 1 && hijriMonth <= 12);

    // Test getDay
    int hijriDay = ChronologyTranslator.getDay(isoDate, "HIJRI");
    assertTrue(hijriDay >= 1 && hijriDay <= 30);

    // Test isValidDate
    assertTrue(ChronologyTranslator.isValidDate(2025, 6, 15, "ISO"));
    assertFalse(ChronologyTranslator.isValidDate(2025, 2, 29, "ISO")); // Not a leap year
  }

  @Test
  void translatorGetYearRange_matchesDateRange() {
    LocalDate start = LocalDate.of(2024, 1, 1);
    LocalDate end = LocalDate.of(2025, 12, 31);

    int[] rangeFromTranslator = ChronologyTranslator.getYearRange(start, end, "HIJRI");
    int[] rangeFromDateRange = new DateRange(start, end).yearRange("HIJRI");

    assertEquals(rangeFromDateRange[0], rangeFromTranslator[0]);
    assertEquals(rangeFromDateRange[1], rangeFromTranslator[1]);
  }

  @Test
  void deprecatedHijriYearRange_stillWorks() {
    DateRange range = new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

    @SuppressWarnings("deprecation")
    int[] deprecated = range.hijriYearRange();
    int[] newMethod = range.yearRange("HIJRI");

    assertEquals(deprecated[0], newMethod[0]);
    assertEquals(deprecated[1], newMethod[1]);
  }

  @Test
  void jdnRoundTrip_acrossChronologies() {
    // A specific JDN should represent the same instant in all chronologies
    long jdn = 2460000L; // A date around 2023

    ChronologyDate isoDate = registry.fromJdn(jdn, "ISO");
    ChronologyDate hijriDate = registry.fromJdn(jdn, "HIJRI");
    ChronologyDate julianDate = registry.fromJdn(jdn, "JULIAN");

    // All should convert back to the same JDN
    assertEquals(jdn, registry.toJdn(isoDate));
    assertEquals(jdn, registry.toJdn(hijriDate));
    assertEquals(jdn, registry.toJdn(julianDate));

    // All should convert to the same ISO date
    LocalDate expectedIso = JulianDayNumber.toLocalDate(jdn);
    assertEquals(expectedIso, isoDate.toIsoDate());
    assertEquals(expectedIso, hijriDate.toIsoDate());
    assertEquals(expectedIso, julianDate.toIsoDate());
  }
}
