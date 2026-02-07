package com.bdc.chronology;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import org.junit.jupiter.api.Test;

class ChronologyTranslatorTest {

  @Test
  void toIsoDate_isoChronology_returnsSameDate() {
    LocalDate result = ChronologyTranslator.toIsoDate(2025, 6, 15, "ISO");

    assertEquals(LocalDate.of(2025, 6, 15), result);
  }

  @Test
  void toIsoDate_nullChronology_defaultsToIso() {
    LocalDate result = ChronologyTranslator.toIsoDate(2025, 6, 15, null);

    assertEquals(LocalDate.of(2025, 6, 15), result);
  }

  @Test
  void toIsoDate_hijriChronology_convertsCorrectly() {
    // Hijri 1446-06-15 should convert to some ISO date
    LocalDate result = ChronologyTranslator.toIsoDate(1446, 6, 15, "HIJRI");

    assertNotNull(result);
    // Verify by converting back using our consistent tabular implementation
    assertEquals(1446, ChronologyTranslator.getYear(result, "HIJRI"));
    assertEquals(6, ChronologyTranslator.getMonth(result, "HIJRI"));
    assertEquals(15, ChronologyTranslator.getDay(result, "HIJRI"));
  }

  @Test
  void toIsoDate_caseInsensitive_works() {
    LocalDate lower = ChronologyTranslator.toIsoDate(2025, 1, 1, "iso");
    LocalDate upper = ChronologyTranslator.toIsoDate(2025, 1, 1, "ISO");
    LocalDate mixed = ChronologyTranslator.toIsoDate(2025, 1, 1, "Iso");

    assertEquals(lower, upper);
    assertEquals(upper, mixed);
  }

  @Test
  void toIsoDate_invalidChronology_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ChronologyTranslator.toIsoDate(2025, 1, 1, "INVALID"));
  }

  @Test
  void hijriToIso_knownDate_convertsCorrectly() {
    // Known conversion: Hijri 1 Muharram 1446 = ISO 2024-07-07 (approximately)
    LocalDate result = ChronologyTranslator.hijriToIso(1446, 1, 1);

    assertNotNull(result);
    // The result should be in 2024
    assertEquals(2024, result.getYear());
  }

  @Test
  void isoToHijri_knownDate_convertsCorrectly() {
    LocalDate isoDate = LocalDate.of(2024, 7, 7);

    HijrahDate result = ChronologyTranslator.isoToHijri(isoDate);

    assertNotNull(result);
    // Should be around Hijri 1446
    assertTrue(result.get(java.time.temporal.ChronoField.YEAR) >= 1445);
  }

  @Test
  void roundTrip_isoToHijriToIso_preservesDate() {
    LocalDate original = LocalDate.of(2025, 6, 15);

    // Use our consistent tabular implementation for the round trip
    int hijriYear = ChronologyTranslator.getYear(original, "HIJRI");
    int hijriMonth = ChronologyTranslator.getMonth(original, "HIJRI");
    int hijriDay = ChronologyTranslator.getDay(original, "HIJRI");

    LocalDate roundTripped = ChronologyTranslator.hijriToIso(hijriYear, hijriMonth, hijriDay);

    assertEquals(original, roundTripped);
  }

  @Test
  void getHijriYear_extractsCorrectly() {
    LocalDate isoDate = LocalDate.of(2025, 1, 1);

    int hijriYear = ChronologyTranslator.getHijriYear(isoDate);

    // 2025-01-01 should be around Hijri 1446
    assertTrue(hijriYear >= 1446 && hijriYear <= 1447);
  }

  @Test
  void getHijriMonth_extractsCorrectly() {
    LocalDate isoDate = LocalDate.of(2025, 1, 1);

    int hijriMonth = ChronologyTranslator.getHijriMonth(isoDate);

    assertTrue(hijriMonth >= 1 && hijriMonth <= 12);
  }

  @Test
  void getHijriDay_extractsCorrectly() {
    LocalDate isoDate = LocalDate.of(2025, 1, 1);

    int hijriDay = ChronologyTranslator.getHijriDay(isoDate);

    assertTrue(hijriDay >= 1 && hijriDay <= 30);
  }

  @Test
  void isValidHijriDate_validDate_returnsTrue() {
    assertTrue(ChronologyTranslator.isValidHijriDate(1446, 1, 1));
    assertTrue(ChronologyTranslator.isValidHijriDate(1446, 6, 15));
    assertTrue(ChronologyTranslator.isValidHijriDate(1446, 12, 29));
  }

  @Test
  void isValidHijriDate_invalidMonth_returnsFalse() {
    assertFalse(ChronologyTranslator.isValidHijriDate(1446, 0, 1));
    assertFalse(ChronologyTranslator.isValidHijriDate(1446, 13, 1));
  }

  @Test
  void isValidHijriDate_invalidDay_returnsFalse() {
    assertFalse(ChronologyTranslator.isValidHijriDate(1446, 1, 0));
    assertFalse(ChronologyTranslator.isValidHijriDate(1446, 1, 31)); // Hijri months max 30 days
  }

  @Test
  void toIsoDate_hijriCaseInsensitive_works() {
    LocalDate lower = ChronologyTranslator.toIsoDate(1446, 1, 1, "hijri");
    LocalDate upper = ChronologyTranslator.toIsoDate(1446, 1, 1, "HIJRI");
    LocalDate mixed = ChronologyTranslator.toIsoDate(1446, 1, 1, "Hijri");

    assertEquals(lower, upper);
    assertEquals(upper, mixed);
  }
}
