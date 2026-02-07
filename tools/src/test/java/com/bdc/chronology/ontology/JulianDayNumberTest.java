package com.bdc.chronology.ontology;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JulianDayNumberTest {

  @Test
  void fromLocalDate_knownDate_returnsCorrectJdn() {
    // November 24, -4713 (Julian calendar) = JDN 0
    // January 1, 2000 = JDN 2451545
    LocalDate y2k = LocalDate.of(2000, 1, 1);
    assertEquals(2451545L, JulianDayNumber.fromLocalDate(y2k));
  }

  @Test
  void toLocalDate_knownJdn_returnsCorrectDate() {
    // JDN 2451545 = January 1, 2000
    LocalDate result = JulianDayNumber.toLocalDate(2451545L);
    assertEquals(LocalDate.of(2000, 1, 1), result);
  }

  @Test
  void roundTrip_localDateToJdnAndBack_preservesDate() {
    LocalDate original = LocalDate.of(2025, 6, 15);
    long jdn = JulianDayNumber.fromLocalDate(original);
    LocalDate roundTripped = JulianDayNumber.toLocalDate(jdn);
    assertEquals(original, roundTripped);
  }

  @ParameterizedTest
  @CsvSource({
    "2000, 1, 1, 2451545", // Y2K
    "1970, 1, 1, 2440588", // Unix epoch
    "1582, 10, 15, 2299161", // First day of Gregorian calendar
    "2024, 7, 7, 2460499", // Recent date
    "1, 1, 1, 1721426", // Gregorian epoch
  })
  void fromGregorian_knownDates_returnsCorrectJdn(int year, int month, int day, long expectedJdn) {
    assertEquals(expectedJdn, JulianDayNumber.fromGregorian(year, month, day));
  }

  @ParameterizedTest
  @CsvSource({
    "2451545, 2000, 1, 1",
    "2440588, 1970, 1, 1",
    "2299161, 1582, 10, 15",
    "1721426, 1, 1, 1",
  })
  void toGregorian_knownJdn_returnsCorrectDate(
      long jdn, int expectedYear, int expectedMonth, int expectedDay) {
    int[] result = JulianDayNumber.toGregorian(jdn);
    assertEquals(expectedYear, result[0]);
    assertEquals(expectedMonth, result[1]);
    assertEquals(expectedDay, result[2]);
  }

  @Test
  void fromJulian_knownDate_returnsCorrectJdn() {
    // Julian calendar October 4, 1582 = JDN 2299160
    // (last day before Gregorian reform)
    assertEquals(2299160L, JulianDayNumber.fromJulian(1582, 10, 4));
  }

  @Test
  void toJulian_knownJdn_returnsCorrectDate() {
    // JDN 2299160 = Julian October 4, 1582
    int[] result = JulianDayNumber.toJulian(2299160L);
    assertEquals(1582, result[0]);
    assertEquals(10, result[1]);
    assertEquals(4, result[2]);
  }

  @Test
  void julianAndGregorian_sameJdn_differentDates() {
    // October 15, 1582 Gregorian = October 5, 1582 Julian
    long jdn = JulianDayNumber.fromGregorian(1582, 10, 15);
    int[] julian = JulianDayNumber.toJulian(jdn);

    assertEquals(1582, julian[0]);
    assertEquals(10, julian[1]);
    assertEquals(5, julian[2]);
  }

  @Test
  void gregorianEpochConstant_isCorrect() {
    // January 1, 1 CE (Gregorian) should match the constant
    assertEquals(JulianDayNumber.GREGORIAN_EPOCH, JulianDayNumber.fromGregorian(1, 1, 1));
  }

  @Test
  void julianEpochConstant_isCorrect() {
    // January 1, 1 CE (Julian) should match the constant
    assertEquals(JulianDayNumber.JULIAN_EPOCH, JulianDayNumber.fromJulian(1, 1, 1));
  }

  @Test
  void roundTrip_julianCalendar_preservesDate() {
    long jdn = JulianDayNumber.fromJulian(1500, 3, 15);
    int[] result = JulianDayNumber.toJulian(jdn);

    assertEquals(1500, result[0]);
    assertEquals(3, result[1]);
    assertEquals(15, result[2]);
  }

  @Test
  void leapYear_gregorian_handledCorrectly() {
    // 2000 is a leap year (divisible by 400)
    LocalDate feb29_2000 = LocalDate.of(2000, 2, 29);
    long jdn = JulianDayNumber.fromLocalDate(feb29_2000);
    LocalDate result = JulianDayNumber.toLocalDate(jdn);
    assertEquals(feb29_2000, result);

    // 1900 is NOT a leap year (divisible by 100 but not 400)
    // So Feb 28, 1900 + 1 day = Mar 1, 1900
    long jdnFeb28 = JulianDayNumber.fromGregorian(1900, 2, 28);
    int[] nextDay = JulianDayNumber.toGregorian(jdnFeb28 + 1);
    assertEquals(1900, nextDay[0]);
    assertEquals(3, nextDay[1]);
    assertEquals(1, nextDay[2]);
  }

  @Test
  void consecutiveDays_haveConsecutiveJdn() {
    LocalDate day1 = LocalDate.of(2025, 1, 1);
    LocalDate day2 = LocalDate.of(2025, 1, 2);
    LocalDate day3 = LocalDate.of(2025, 1, 3);

    long jdn1 = JulianDayNumber.fromLocalDate(day1);
    long jdn2 = JulianDayNumber.fromLocalDate(day2);
    long jdn3 = JulianDayNumber.fromLocalDate(day3);

    assertEquals(jdn1 + 1, jdn2);
    assertEquals(jdn2 + 1, jdn3);
  }
}
