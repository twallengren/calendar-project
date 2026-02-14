package com.bdc.chronology.ontology.algorithms;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.ChronologySpec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LookupTableAlgorithmTest {

  private LookupTableAlgorithm algorithm;

  @BeforeEach
  void setUp() {
    // Create a small test lookup table: 2 years, 12 months each
    // Mimics a Hijri-like calendar with alternating 30/29 day months
    List<ChronologySpec.MonthEntry> entries = new ArrayList<>();

    // Year 1400: 354 days (common year)
    long jdn = 2000000L;
    int[] lengths1400 = {30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 29};
    for (int m = 1; m <= 12; m++) {
      entries.add(new ChronologySpec.MonthEntry(1400, m, jdn, lengths1400[m - 1]));
      jdn += lengths1400[m - 1];
    }

    // Year 1401: 355 days (leap year - last month has 30 days)
    int[] lengths1401 = {30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 30};
    for (int m = 1; m <= 12; m++) {
      entries.add(new ChronologySpec.MonthEntry(1401, m, jdn, lengths1401[m - 1]));
      jdn += lengths1401[m - 1];
    }

    ChronologySpec spec =
        new ChronologySpec(
            "chronology",
            "TEST_LOOKUP",
            new ChronologySpec.Metadata("Test", "Test lookup table"),
            new ChronologySpec.Structure(null, null, null),
            new ChronologySpec.Algorithms(
                "LOOKUP_TABLE", null, null, null, null, null, entries, null));

    algorithm = new LookupTableAlgorithm(spec);
  }

  @Test
  void getChronologyId_returnsCorrectId() {
    assertEquals("TEST_LOOKUP", algorithm.getChronologyId());
  }

  @Test
  void toJdn_firstDayOfFirstMonth_returnsBaseJdn() {
    assertEquals(2000000L, algorithm.toJdn(1400, 1, 1));
  }

  @Test
  void toJdn_lastDayOfFirstMonth_returnsCorrectJdn() {
    assertEquals(2000029L, algorithm.toJdn(1400, 1, 30));
  }

  @Test
  void toJdn_firstDayOfSecondMonth_returnsCorrectJdn() {
    assertEquals(2000030L, algorithm.toJdn(1400, 2, 1));
  }

  @Test
  void fromJdn_firstDay_returnsCorrectDate() {
    ChronologyDate date = algorithm.fromJdn(2000000L);
    assertEquals(1400, date.year());
    assertEquals(1, date.month());
    assertEquals(1, date.day());
  }

  @Test
  void fromJdn_lastDayOfMonth_returnsCorrectDate() {
    ChronologyDate date = algorithm.fromJdn(2000029L);
    assertEquals(1400, date.year());
    assertEquals(1, date.month());
    assertEquals(30, date.day());
  }

  @Test
  void fromJdn_firstDayOfSecondMonth_returnsCorrectDate() {
    ChronologyDate date = algorithm.fromJdn(2000030L);
    assertEquals(1400, date.year());
    assertEquals(2, date.month());
    assertEquals(1, date.day());
  }

  @Test
  void toJdn_fromJdn_roundTrip() {
    // Test round-trip for various dates
    for (int year = 1400; year <= 1401; year++) {
      for (int month = 1; month <= 12; month++) {
        int days = algorithm.getDaysInMonth(year, month);
        for (int day = 1; day <= days; day++) {
          long jdn = algorithm.toJdn(year, month, day);
          ChronologyDate result = algorithm.fromJdn(jdn);
          assertEquals(year, result.year(), "Year mismatch for " + year + "-" + month + "-" + day);
          assertEquals(
              month, result.month(), "Month mismatch for " + year + "-" + month + "-" + day);
          assertEquals(day, result.day(), "Day mismatch for " + year + "-" + month + "-" + day);
        }
      }
    }
  }

  @Test
  void isLeapYear_commonYear_returnsFalse() {
    assertFalse(algorithm.isLeapYear(1400)); // 354 days
  }

  @Test
  void isLeapYear_leapYear_returnsTrue() {
    assertTrue(algorithm.isLeapYear(1401)); // 355 days
  }

  @Test
  void getDaysInMonth_returnsCorrectValues() {
    assertEquals(30, algorithm.getDaysInMonth(1400, 1));
    assertEquals(29, algorithm.getDaysInMonth(1400, 2));
    assertEquals(29, algorithm.getDaysInMonth(1400, 12)); // Common year
    assertEquals(30, algorithm.getDaysInMonth(1401, 12)); // Leap year
  }

  @Test
  void isValidDate_validDates_returnsTrue() {
    assertTrue(algorithm.isValidDate(1400, 1, 1));
    assertTrue(algorithm.isValidDate(1400, 1, 30));
    assertTrue(algorithm.isValidDate(1401, 12, 30));
  }

  @Test
  void isValidDate_invalidDates_returnsFalse() {
    assertFalse(algorithm.isValidDate(1399, 1, 1)); // Year out of range
    assertFalse(algorithm.isValidDate(1402, 1, 1)); // Year out of range
    assertFalse(algorithm.isValidDate(1400, 0, 1)); // Invalid month
    assertFalse(algorithm.isValidDate(1400, 13, 1)); // Invalid month
    assertFalse(algorithm.isValidDate(1400, 1, 0)); // Invalid day
    assertFalse(algorithm.isValidDate(1400, 1, 31)); // Day too large
    assertFalse(algorithm.isValidDate(1400, 2, 30)); // 29-day month
  }

  @Test
  void toJdn_invalidDate_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> algorithm.toJdn(1400, 1, 31));
  }

  @Test
  void fromJdn_beforeRange_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> algorithm.fromJdn(1999999L));
  }

  @Test
  void fromJdn_afterRange_throwsException() {
    // JDN after last day of year 1401
    long lastJdn = algorithm.toJdn(1401, 12, 30);
    assertThrows(IllegalArgumentException.class, () -> algorithm.fromJdn(lastJdn + 1));
  }

  @Test
  void getDaysInMonth_outOfRange_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> algorithm.getDaysInMonth(1399, 1));
    assertThrows(IllegalArgumentException.class, () -> algorithm.getDaysInMonth(1400, 0));
    assertThrows(IllegalArgumentException.class, () -> algorithm.getDaysInMonth(1400, 13));
  }

  @Test
  void constructor_emptyMonths_throwsException() {
    ChronologySpec spec =
        new ChronologySpec(
            "chronology",
            "EMPTY",
            new ChronologySpec.Metadata("Empty", "Empty"),
            new ChronologySpec.Structure(null, null, null),
            new ChronologySpec.Algorithms(
                "LOOKUP_TABLE", null, null, null, null, null, List.of(), null));

    assertThrows(IllegalArgumentException.class, () -> new LookupTableAlgorithm(spec));
  }
}
