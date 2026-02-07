package com.bdc.chronology.generated;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;

/**
 * Generated chronology: Islamic Calendar (Tabular)
 *
 * <p>The tabular Islamic calendar is an arithmetic approximation of the lunar Hijri calendar. It
 * uses a 30-year cycle with 11 leap years to approximate the lunar month cycle. Months alternate
 * between 30 and 29 days, with the 12th month (Dhu al-Hijjah) having 30 days in leap years.
 *
 * <p>This is suitable for civil and computational purposes. For religious observances, the actual
 * Hijri calendar based on moon sighting may differ by 1-2 days.
 *
 * <p>This class is auto-generated from YAML. Do not edit manually.
 */
public final class HijriChronology implements ChronologyAlgorithm {

  public static final String ID = "HIJRI";
  private static final long EPOCH_JDN = 1948440L;

  private static final int[] DAYS_IN_MONTH = {30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 29};
  private static final int[] LEAP_DAYS_IN_MONTH = {30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 30};

  public HijriChronology() {}

  @Override
  public String getChronologyId() {
    return ID;
  }

  @Override
  public boolean isLeapYear(int year) {
    return ((11 * year + 14) % 30) < 11;
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
