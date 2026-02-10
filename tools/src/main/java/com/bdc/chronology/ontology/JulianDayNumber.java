package com.bdc.chronology.ontology;

import java.time.LocalDate;

/**
 * Utility class for Julian Day Number (JDN) calculations.
 *
 * <p>Julian Day Number is a continuous count of days since the beginning of the Julian Period on
 * January 1, 4713 BCE (proleptic Julian calendar). It serves as a universal pivot for converting
 * between different calendar systems.
 *
 * <p>This implementation uses the algorithm from the Astronomical Algorithms by Jean Meeus, which
 * is valid for dates from November 23, -4713 (proleptic Gregorian) onward.
 */
public final class JulianDayNumber {

  private JulianDayNumber() {}

  /**
   * Converts an ISO LocalDate to Julian Day Number.
   *
   * @param date the ISO date to convert
   * @return the Julian Day Number
   */
  public static long fromLocalDate(LocalDate date) {
    return fromGregorian(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  /**
   * Converts a Gregorian calendar date to Julian Day Number.
   *
   * <p>Uses the algorithm from "Astronomical Algorithms" by Jean Meeus.
   *
   * @param year the Gregorian year (negative for BCE, with year 0 = 1 BCE)
   * @param month the month (1-12)
   * @param day the day of month (1-31)
   * @return the Julian Day Number
   */
  public static long fromGregorian(int year, int month, int day) {
    // Adjust for months before March (treat Jan/Feb as months 13/14 of previous year)
    int a = (14 - month) / 12;
    int y = year + 4800 - a;
    int m = month + 12 * a - 3;

    // Julian Day Number calculation for Gregorian calendar
    return day + (153 * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045;
  }

  /**
   * Converts a Julian calendar date to Julian Day Number.
   *
   * @param year the Julian year (negative for BCE, with year 0 = 1 BCE)
   * @param month the month (1-12)
   * @param day the day of month (1-31)
   * @return the Julian Day Number
   */
  public static long fromJulian(int year, int month, int day) {
    // Adjust for months before March
    int a = (14 - month) / 12;
    int y = year + 4800 - a;
    int m = month + 12 * a - 3;

    // Julian Day Number calculation for Julian calendar (no century correction)
    return day + (153 * m + 2) / 5 + 365L * y + y / 4 - 32083;
  }

  /**
   * Converts a Julian Day Number to an ISO LocalDate.
   *
   * @param jdn the Julian Day Number
   * @return the corresponding ISO LocalDate
   */
  public static LocalDate toLocalDate(long jdn) {
    int[] ymd = toGregorian(jdn);
    return LocalDate.of(ymd[0], ymd[1], ymd[2]);
  }

  /**
   * Converts a Julian Day Number to Gregorian calendar date components.
   *
   * @param jdn the Julian Day Number
   * @return array of [year, month, day]
   */
  public static int[] toGregorian(long jdn) {
    long a = jdn + 32044;
    long b = (4 * a + 3) / 146097;
    long c = a - (146097 * b) / 4;
    long d = (4 * c + 3) / 1461;
    long e = c - (1461 * d) / 4;
    long m = (5 * e + 2) / 153;

    int day = (int) (e - (153 * m + 2) / 5 + 1);
    int month = (int) (m + 3 - 12 * (m / 10));
    int year = (int) (100 * b + d - 4800 + m / 10);

    return new int[] {year, month, day};
  }

  /**
   * Converts a Julian Day Number to Julian calendar date components.
   *
   * @param jdn the Julian Day Number
   * @return array of [year, month, day]
   */
  public static int[] toJulian(long jdn) {
    long b = 0;
    long c = jdn + 32082;
    long d = (4 * c + 3) / 1461;
    long e = c - (1461 * d) / 4;
    long m = (5 * e + 2) / 153;

    int day = (int) (e - (153 * m + 2) / 5 + 1);
    int month = (int) (m + 3 - 12 * (m / 10));
    int year = (int) (100 * b + d - 4800 + m / 10);

    return new int[] {year, month, day};
  }

  /** JDN of the Gregorian epoch: January 1, 1 CE (proleptic Gregorian). */
  public static final long GREGORIAN_EPOCH = 1721426L;

  /** JDN of the Julian epoch: January 1, 1 CE (Julian calendar). */
  public static final long JULIAN_EPOCH = 1721424L;

  /** JDN of the Hijri epoch: July 16, 622 CE (Julian calendar) = July 19, 622 CE (Gregorian). */
  public static final long HIJRI_EPOCH = 1948440L;
}
