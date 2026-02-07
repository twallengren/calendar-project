package com.bdc.chronology.generated;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;

/**
 * Generated chronology: Solar Hijri Calendar
 *
 * <p>The Solar Hijri calendar (Persian: گاه‌شماری هجری خورشیدی) is the official calendar of Iran
 * and Afghanistan. It is a solar calendar with 12 months, beginning on the March equinox.
 *
 * <p>The first six months have 31 days, the next five have 30 days, and the last month has 29 days
 * (30 in leap years). This arithmetic version uses a 2820-year cycle approximation for leap years.
 *
 * <p>This class is auto-generated from YAML. Do not edit manually.
 */
public final class PersianChronology implements ChronologyAlgorithm {

  /** Months of the Solar Hijri Calendar. */
  public enum Month {
    FARVARDIN(1, "Farvardin", 31, 31),
    ORDIBEHESHT(2, "Ordibehesht", 31, 31),
    KHORDAD(3, "Khordad", 31, 31),
    TIR(4, "Tir", 31, 31),
    MORDAD(5, "Mordad", 31, 31),
    SHAHRIVAR(6, "Shahrivar", 31, 31),
    MEHR(7, "Mehr", 30, 30),
    ABAN(8, "Aban", 30, 30),
    AZAR(9, "Azar", 30, 30),
    DEY(10, "Dey", 30, 30),
    BAHMAN(11, "Bahman", 30, 30),
    ESFAND(12, "Esfand", 29, 30);

    private final int number;
    private final String displayName;
    private final int days;
    private final int leapDays;

    Month(int number, String displayName, int days, int leapDays) {
      this.number = number;
      this.displayName = displayName;
      this.days = days;
      this.leapDays = leapDays;
    }

    /** Returns the month number (1-based). */
    public int number() {
      return number;
    }

    /** Returns the display name of the month. */
    public String displayName() {
      return displayName;
    }

    /** Returns the number of days in this month for a common year. */
    public int days() {
      return days;
    }

    /** Returns the number of days in this month for a leap year. */
    public int leapDays() {
      return leapDays;
    }

    /** Returns the number of days in this month for the given year type. */
    public int days(boolean isLeapYear) {
      return isLeapYear ? leapDays : days;
    }

    /** Returns the Month for the given number (1-based). */
    public static Month of(int month) {
      if (month < 1 || month > values().length) {
        throw new IllegalArgumentException("Invalid month: " + month);
      }
      return values()[month - 1];
    }
  }

  /** Days of the week in the Solar Hijri Calendar. */
  public enum Day {
    SHANBEH(0, "Shanbeh"),
    YEKSHANBEH(1, "Yekshanbeh"),
    DOSHANBEH(2, "Doshanbeh"),
    SESHHANBEH(3, "Seshhanbeh"),
    CHAHARSHANBEH(4, "Chaharshanbeh"),
    PANJSHANBEH(5, "Panjshanbeh"),
    JOMEH(6, "Jomeh");

    private final int ordinal;
    private final String displayName;

    Day(int ordinal, String displayName) {
      this.ordinal = ordinal;
      this.displayName = displayName;
    }

    /** Returns the day ordinal (0-based, starting from the first day of the week). */
    public int dayOrdinal() {
      return ordinal;
    }

    /** Returns the display name of the day. */
    public String displayName() {
      return displayName;
    }

    /** Returns the Day for the given ordinal (0-based). */
    public static Day of(int dayOrdinal) {
      if (dayOrdinal < 0 || dayOrdinal >= values().length) {
        throw new IllegalArgumentException("Invalid day ordinal: " + dayOrdinal);
      }
      return values()[dayOrdinal];
    }
  }

  public static final String ID = "PERSIAN";
  private static final long EPOCH_JDN = 1948320L;

  private static final int[] DAYS_IN_MONTH = {31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29};
  private static final int[] LEAP_DAYS_IN_MONTH = {31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 30};

  public PersianChronology() {}

  @Override
  public String getChronologyId() {
    return ID;
  }

  @Override
  public boolean isLeapYear(int year) {
    return ((((year - 474) % 2820) + 474 + 38) * 682 % 2816) < 682;
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
