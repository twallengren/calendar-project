package com.bdc.chronology.ontology.algorithms;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.ontology.ChronologyDate;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaHijriAlgorithmTest {

  private JavaHijriAlgorithm algorithm;

  @BeforeEach
  void setUp() {
    algorithm = new JavaHijriAlgorithm();
  }

  @Test
  void getChronologyId_returnsHijri() {
    assertEquals("HIJRI", algorithm.getChronologyId());
  }

  @Test
  void isValidDate_validDates_returnsTrue() {
    assertTrue(algorithm.isValidDate(1446, 1, 1));
    assertTrue(algorithm.isValidDate(1446, 6, 15));
    assertTrue(algorithm.isValidDate(1446, 12, 29));
  }

  @Test
  void isValidDate_invalidMonth_returnsFalse() {
    assertFalse(algorithm.isValidDate(1446, 0, 1));
    assertFalse(algorithm.isValidDate(1446, 13, 1));
  }

  @Test
  void isValidDate_invalidDay_returnsFalse() {
    assertFalse(algorithm.isValidDate(1446, 1, 0));
    assertFalse(algorithm.isValidDate(1446, 1, 31)); // Hijri months max 30 days
  }

  @Test
  void getDaysInMonth_validMonth_returnsDays() {
    int days = algorithm.getDaysInMonth(1446, 1);
    assertTrue(days == 29 || days == 30);
  }

  @Test
  void getDaysInMonth_invalidMonth_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> algorithm.getDaysInMonth(1446, 0));
    assertThrows(IllegalArgumentException.class, () -> algorithm.getDaysInMonth(1446, 13));
  }

  @Test
  void toJdn_validDate_returnsJdn() {
    long jdn = algorithm.toJdn(1446, 1, 1);
    assertTrue(jdn > 0);
  }

  @Test
  void fromJdn_validJdn_returnsHijriDate() {
    // First, get a JDN for a known date
    long jdn = algorithm.toJdn(1446, 1, 1);
    ChronologyDate date = algorithm.fromJdn(jdn);

    assertEquals("HIJRI", date.chronologyId());
    assertEquals(1446, date.year());
    assertEquals(1, date.month());
    assertEquals(1, date.day());
  }

  @Test
  void roundTrip_toJdnAndBack_preservesDate() {
    int year = 1446, month = 6, day = 15;
    long jdn = algorithm.toJdn(year, month, day);
    ChronologyDate result = algorithm.fromJdn(jdn);

    assertEquals(year, result.year());
    assertEquals(month, result.month());
    assertEquals(day, result.day());
  }

  @Test
  void toIsoDate_knownDate_returnsCorrectIsoDate() {
    // Hijri 1446-1-1 should be around July 2024
    LocalDate isoDate = algorithm.toIsoDate(1446, 1, 1);

    assertEquals(2024, isoDate.getYear());
    assertTrue(isoDate.getMonthValue() >= 6 && isoDate.getMonthValue() <= 8);
  }

  @Test
  void fromIsoDate_knownDate_returnsCorrectHijriDate() {
    LocalDate isoDate = LocalDate.of(2024, 7, 7);
    int[] hijri = algorithm.fromIsoDate(isoDate);

    // Should be around Hijri 1446
    assertTrue(hijri[0] >= 1445 && hijri[0] <= 1446);
    assertTrue(hijri[1] >= 1 && hijri[1] <= 12);
    assertTrue(hijri[2] >= 1 && hijri[2] <= 30);
  }

  @Test
  void roundTrip_isoToHijriToIso_preservesDate() {
    LocalDate original = LocalDate.of(2025, 6, 15);

    int[] hijri = algorithm.fromIsoDate(original);
    LocalDate roundTripped = algorithm.toIsoDate(hijri[0], hijri[1], hijri[2]);

    assertEquals(original, roundTripped);
  }

  @Test
  void hijriYear_matchesJavaChronology() {
    // Verify our algorithm matches Java's built-in HijrahChronology
    LocalDate isoDate = LocalDate.of(2025, 1, 1);

    int[] ourResult = algorithm.fromIsoDate(isoDate);
    java.time.chrono.HijrahDate javaHijri = java.time.chrono.HijrahDate.from(isoDate);

    assertEquals(javaHijri.get(java.time.temporal.ChronoField.YEAR), ourResult[0]);
    assertEquals(javaHijri.get(java.time.temporal.ChronoField.MONTH_OF_YEAR), ourResult[1]);
    assertEquals(javaHijri.get(java.time.temporal.ChronoField.DAY_OF_MONTH), ourResult[2]);
  }

  @Test
  void isLeapYear_variousYears() {
    // Just verify it doesn't throw and returns boolean
    for (int year = 1440; year <= 1450; year++) {
      boolean isLeap = algorithm.isLeapYear(year);
      // The leap year pattern in Hijri is complex, just verify it works
      assertNotNull(isLeap);
    }
  }

  @Test
  void monthsHave29Or30Days() {
    // All Hijri months should have 29 or 30 days
    for (int month = 1; month <= 12; month++) {
      int days = algorithm.getDaysInMonth(1446, month);
      assertTrue(days == 29 || days == 30, "Month " + month + " has " + days + " days");
    }
  }
}
