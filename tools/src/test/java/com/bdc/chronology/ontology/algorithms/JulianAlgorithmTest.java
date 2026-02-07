package com.bdc.chronology.ontology.algorithms;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.ontology.ChronologyDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JulianAlgorithmTest {

  private JulianAlgorithm algorithm;

  @BeforeEach
  void setUp() {
    algorithm = new JulianAlgorithm();
  }

  @Test
  void getChronologyId_returnsJulian() {
    assertEquals("JULIAN", algorithm.getChronologyId());
  }

  @ParameterizedTest
  @CsvSource({
    "2000, true", // Divisible by 4
    "1900, true", // Divisible by 4 (Julian doesn't have century exception)
    "2004, true", // Divisible by 4
    "2001, false", // Not divisible by 4
    "2100, true", // Divisible by 4 (Julian differs from Gregorian here)
    "1, false", // Year 1 is not divisible by 4
    "4, true", // Year 4 is divisible by 4
  })
  void isLeapYear_variousYears_correctResult(int year, boolean expected) {
    assertEquals(expected, algorithm.isLeapYear(year));
  }

  @Test
  void isLeapYear_differsfromGregorian() {
    // 1900 is NOT a leap year in Gregorian but IS in Julian
    IsoAlgorithm gregorian = new IsoAlgorithm();

    assertFalse(gregorian.isLeapYear(1900));
    assertTrue(algorithm.isLeapYear(1900));

    assertFalse(gregorian.isLeapYear(2100));
    assertTrue(algorithm.isLeapYear(2100));
  }

  @ParameterizedTest
  @CsvSource({
    "1900, 1, 31", // January
    "1900, 2, 29", // February in leap year (1900 IS leap in Julian)
    "2001, 2, 28", // February in non-leap year
    "2000, 4, 30", // April
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
    assertTrue(algorithm.isValidDate(1900, 2, 29)); // Leap year in Julian
  }

  @Test
  void isValidDate_invalidDates_returnsFalse() {
    assertFalse(algorithm.isValidDate(2025, 2, 29)); // 2025 not leap
    assertFalse(algorithm.isValidDate(2025, 4, 31)); // April has 30 days
    assertFalse(algorithm.isValidDate(2025, 13, 1)); // Invalid month
  }

  @Test
  void toJdn_knownDate_returnsCorrectJdn() {
    // Julian October 4, 1582 = JDN 2299160 (last day before Gregorian reform)
    assertEquals(2299160L, algorithm.toJdn(1582, 10, 4));
  }

  @Test
  void fromJdn_knownJdn_returnsCorrectDate() {
    // JDN 2299160 = Julian October 4, 1582
    ChronologyDate date = algorithm.fromJdn(2299160L);

    assertEquals("JULIAN", date.chronologyId());
    assertEquals(1582, date.year());
    assertEquals(10, date.month());
    assertEquals(4, date.day());
  }

  @Test
  void julianAndGregorian_sameJdn_differentDates() {
    // October 15, 1582 Gregorian = October 5, 1582 Julian
    IsoAlgorithm gregorian = new IsoAlgorithm();
    long jdn = gregorian.toJdn(1582, 10, 15);
    ChronologyDate julianDate = algorithm.fromJdn(jdn);

    assertEquals(1582, julianDate.year());
    assertEquals(10, julianDate.month());
    assertEquals(5, julianDate.day());
  }

  @Test
  void roundTrip_toJdnAndBack_preservesDate() {
    int year = 1500, month = 3, day = 15;
    long jdn = algorithm.toJdn(year, month, day);
    ChronologyDate result = algorithm.fromJdn(jdn);

    assertEquals(year, result.year());
    assertEquals(month, result.month());
    assertEquals(day, result.day());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 500, 1000, 1500, 1582, 2000})
  void roundTrip_variousYears_preservesDate(int year) {
    for (int month = 1; month <= 12; month++) {
      int day = Math.min(15, algorithm.getDaysInMonth(year, month));
      long jdn = algorithm.toJdn(year, month, day);
      ChronologyDate result = algorithm.fromJdn(jdn);

      assertEquals(year, result.year(), "Year mismatch for " + year + "-" + month + "-" + day);
      assertEquals(month, result.month(), "Month mismatch for " + year + "-" + month + "-" + day);
      assertEquals(day, result.day(), "Day mismatch for " + year + "-" + month + "-" + day);
    }
  }

  @Test
  void julianEpoch_isCorrect() {
    // January 1, 1 CE Julian
    long jdn = algorithm.toJdn(1, 1, 1);
    ChronologyDate date = algorithm.fromJdn(jdn);

    assertEquals(1, date.year());
    assertEquals(1, date.month());
    assertEquals(1, date.day());
  }

  @Test
  void driftFromGregorian_increases_overTime() {
    // The difference between Julian and Gregorian increases over time
    IsoAlgorithm gregorian = new IsoAlgorithm();

    // In year 100, difference is about 1 day
    // In year 1582, difference is 10 days
    // In year 2000, difference is 13 days

    // Same calendar date in Julian and Gregorian gives different JDN
    // Julian January 1, 2000 is actually 13 days later than Gregorian January 1, 2000
    // because Julian has accumulated extra leap days over centuries
    long julianJdn = algorithm.toJdn(2000, 1, 1);
    long gregorianJdn = gregorian.toJdn(2000, 1, 1);

    // Julian calendar is 13 days ahead (has more days) due to extra leap years
    assertEquals(13, julianJdn - gregorianJdn);
  }
}
