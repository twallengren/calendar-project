package com.bdc.chronology.generated;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;

/**
 * Generated chronology: Julian Calendar
 *
 * <p>The Julian calendar was introduced by Julius Caesar in 45 BCE as a reform of the Roman
 * calendar. It is a solar calendar with a simpler leap year rule than the Gregorian calendar: every
 * year divisible by 4 is a leap year.
 *
 * <p>The Julian calendar was the predominant calendar in the Western world until the Gregorian
 * reform in 1582. It is still used by some Eastern Orthodox churches for calculating the date of
 * Easter.
 *
 * <p>This class is auto-generated from YAML. Do not edit manually.
 */
public final class JulianChronology implements ChronologyAlgorithm {

  public static final String ID = "JULIAN";
  private static final long EPOCH_JDN = 1721424L;

  private static final int[] DAYS_IN_MONTH = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
  private static final int[] LEAP_DAYS_IN_MONTH = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

  public JulianChronology() {}

  @Override
  public String getChronologyId() {
    return ID;
  }

  @Override
  public boolean isLeapYear(int year) {
    return year % 4 == 0;
  }

  @Override
  public int getDaysInMonth(int year, int month) {
    if (month < 1 || month > 12) {
      throw new IllegalArgumentException("Invalid month: " + month);
    }
    return isLeapYear(year) ? LEAP_DAYS_IN_MONTH[month - 1] : DAYS_IN_MONTH[month - 1];
  }

  @Override
  public boolean isValidDate(int year, int month, int day) {
    if (month < 1 || month > 12) return false;
    if (day < 1) return false;
    return day <= getDaysInMonth(year, month);
  }

  @Override
  public long toJdn(int year, int month, int day) {
    long days = daysBeforeYear(year);
    for (int m = 1; m < month; m++) {
      days += getDaysInMonth(year, m);
    }
    days += day - 1;
    return EPOCH_JDN + days;
  }

  @Override
  public ChronologyDate fromJdn(long jdn) {
    long daysSinceEpoch = jdn - EPOCH_JDN;
    int year = estimateYear(daysSinceEpoch);
    while (daysBeforeYear(year + 1) <= daysSinceEpoch) year++;
    while (daysBeforeYear(year) > daysSinceEpoch) year--;
    long remaining = daysSinceEpoch - daysBeforeYear(year);
    int month = 1;
    while (month <= 12) {
      int dim = getDaysInMonth(year, month);
      if (remaining < dim) break;
      remaining -= dim;
      month++;
    }
    int day = (int) remaining + 1;
    return new ChronologyDate(ID, year, month, day);
  }

  private long daysBeforeYear(int year) {
    if (year <= 1) return 0;
    long days = 0;
    for (int y = 1; y < year; y++) {
      for (int m = 1; m <= 12; m++) {
        days += getDaysInMonth(y, m);
      }
    }
    return days;
  }

  private int estimateYear(long daysSinceEpoch) {
    return Math.max(1, (int) (daysSinceEpoch / 365) + 1);
  }
}
