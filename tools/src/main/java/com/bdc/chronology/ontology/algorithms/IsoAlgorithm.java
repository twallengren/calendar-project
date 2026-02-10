package com.bdc.chronology.ontology.algorithms;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.JulianDayNumber;

/**
 * Algorithm implementation for the ISO/Gregorian calendar.
 *
 * <p>The Gregorian calendar is the internationally accepted civil calendar, introduced by Pope
 * Gregory XIII in October 1582. It is a solar calendar based on a 365-day common year divided into
 * 12 months.
 *
 * <p>Leap year rule: A year is a leap year if it is divisible by 4, except for years divisible by
 * 100, which are not leap years unless they are also divisible by 400.
 */
public class IsoAlgorithm implements ChronologyAlgorithm {

  public static final String ID = "ISO";

  private static final int[] DAYS_IN_MONTH = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

  @Override
  public String getChronologyId() {
    return ID;
  }

  @Override
  public long toJdn(int year, int month, int day) {
    return JulianDayNumber.fromGregorian(year, month, day);
  }

  @Override
  public ChronologyDate fromJdn(long jdn) {
    int[] ymd = JulianDayNumber.toGregorian(jdn);
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
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
  }
}
