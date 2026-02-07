package com.bdc.chronology.ontology.algorithms;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.JulianDayNumber;

/**
 * Algorithm implementation for the Julian calendar.
 *
 * <p>The Julian calendar was introduced by Julius Caesar in 45 BCE. It is a solar calendar with a
 * simpler leap year rule than the Gregorian calendar: every year divisible by 4 is a leap year.
 *
 * <p>The Julian calendar was the predominant calendar in the Western world until the Gregorian
 * reform in 1582. It is still used by some Eastern Orthodox churches for calculating the date of
 * Easter.
 */
public class JulianAlgorithm implements ChronologyAlgorithm {

  public static final String ID = "JULIAN";

  private static final int[] DAYS_IN_MONTH = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

  @Override
  public String getChronologyId() {
    return ID;
  }

  @Override
  public long toJdn(int year, int month, int day) {
    return JulianDayNumber.fromJulian(year, month, day);
  }

  @Override
  public ChronologyDate fromJdn(long jdn) {
    int[] ymd = JulianDayNumber.toJulian(jdn);
    return new ChronologyDate(ID, ymd[0], ymd[1], ymd[2]);
  }

  @Override
  public boolean isValidDate(int year, int month, int day) {
    if (month < 1 || month > 12) {
      return false;
    }
    if (day < 1) {
      return false;
    }
    return day <= getDaysInMonth(year, month);
  }

  @Override
  public int getDaysInMonth(int year, int month) {
    if (month < 1 || month > 12) {
      throw new IllegalArgumentException("Invalid month: " + month);
    }
    if (month == 2 && isLeapYear(year)) {
      return 29;
    }
    return DAYS_IN_MONTH[month];
  }

  @Override
  public boolean isLeapYear(int year) {
    // Julian calendar: every 4th year is a leap year
    return year % 4 == 0;
  }
}
