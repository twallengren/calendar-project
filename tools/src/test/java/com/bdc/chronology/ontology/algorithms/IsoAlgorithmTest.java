package com.bdc.chronology.ontology.algorithms;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.ontology.ChronologyDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IsoAlgorithmTest {

  private IsoAlgorithm algorithm;

  @BeforeEach
  void setUp() {
    algorithm = new IsoAlgorithm();
  }

  @Test
  void getChronologyId_returnsIso() {
    assertEquals("ISO", algorithm.getChronologyId());
  }

  @ParameterizedTest
  @CsvSource({
    "2000, true", // Divisible by 400
    "1900, false", // Divisible by 100 but not 400
    "2004, true", // Divisible by 4, not by 100
    "2001, false", // Not divisible by 4
    "2100, false", // Divisible by 100 but not 400
    "2400, true", // Divisible by 400
  })
  void isLeapYear_variousYears_correctResult(int year, boolean expected) {
    assertEquals(expected, algorithm.isLeapYear(year));
  }

  @ParameterizedTest
  @CsvSource({
    "2000, 1, 31", // January
    "2000, 2, 29", // February in leap year
    "2001, 2, 28", // February in non-leap year
    "2000, 4, 30", // April
    "2000, 7, 31", // July
    "2000, 12, 31", // December
  })
  void getDaysInMonth_variousMonths_correctDays(int year, int month, int expectedDays) {
    assertEquals(expectedDays, algorithm.getDaysInMonth(year, month));
  }

  @Test
  void getDaysInMonth_invalidMonth_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> algorithm.getDaysInMonth(2025, 0));
    assertThrows(IllegalArgumentException.class, () -> algorithm.getDaysInMonth(2025, 13));
  }

  @Test
  void isValidDate_validDates_returnsTrue() {
    assertTrue(algorithm.isValidDate(2025, 1, 1));
    assertTrue(algorithm.isValidDate(2025, 12, 31));
    assertTrue(algorithm.isValidDate(2024, 2, 29)); // Leap year
  }

  @Test
  void isValidDate_invalidDates_returnsFalse() {
    assertFalse(algorithm.isValidDate(2025, 2, 29)); // Not a leap year
    assertFalse(algorithm.isValidDate(2025, 4, 31)); // April has 30 days
    assertFalse(algorithm.isValidDate(2025, 13, 1)); // Invalid month
    assertFalse(algorithm.isValidDate(2025, 0, 1)); // Invalid month
    assertFalse(algorithm.isValidDate(2025, 1, 0)); // Invalid day
    assertFalse(algorithm.isValidDate(2025, 1, 32)); // Invalid day
  }

  @Test
  void toJdn_knownDate_returnsCorrectJdn() {
    // January 1, 2000 = JDN 2451545
    assertEquals(2451545L, algorithm.toJdn(2000, 1, 1));
  }

  @Test
  void fromJdn_knownJdn_returnsCorrectDate() {
    ChronologyDate date = algorithm.fromJdn(2451545L);

    assertEquals("ISO", date.chronologyId());
    assertEquals(2000, date.year());
    assertEquals(1, date.month());
    assertEquals(1, date.day());
  }

  @Test
  void roundTrip_toJdnAndBack_preservesDate() {
    int year = 2025, month = 6, day = 15;
    long jdn = algorithm.toJdn(year, month, day);
    ChronologyDate result = algorithm.fromJdn(jdn);

    assertEquals(year, result.year());
    assertEquals(month, result.month());
    assertEquals(day, result.day());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 1000, 1582, 2000, 2025, 3000})
  void roundTrip_variousYears_preservesDate(int year) {
    for (int month = 1; month <= 12; month++) {
      int day = Math.min(15, algorithm.getDaysInMonth(year, month));
      long jdn = algorithm.toJdn(year, month, day);
      ChronologyDate result = algorithm.fromJdn(jdn);

      assertEquals(year, result.year());
      assertEquals(month, result.month());
      assertEquals(day, result.day());
    }
  }

  @Test
  void toJdn_chronologyDate_works() {
    ChronologyDate date = new ChronologyDate("ISO", 2000, 1, 1);
    assertEquals(2451545L, algorithm.toJdn(date));
  }

  @Test
  void toJdn_wrongChronology_throwsException() {
    ChronologyDate hijriDate = new ChronologyDate("HIJRI", 1446, 1, 1);
    assertThrows(IllegalArgumentException.class, () -> algorithm.toJdn(hijriDate));
  }

  @Test
  void getYear_fromJdn_works() {
    assertEquals(2000, algorithm.getYear(2451545L));
  }

  @Test
  void getMonth_fromJdn_works() {
    assertEquals(1, algorithm.getMonth(2451545L));
  }

  @Test
  void getDay_fromJdn_works() {
    assertEquals(1, algorithm.getDay(2451545L));
  }
}
