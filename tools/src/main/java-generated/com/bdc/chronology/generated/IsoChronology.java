package com.bdc.chronology.generated;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;

/**
 * Generated chronology: Gregorian Calendar
 *
 * <p>The Gregorian calendar is the internationally accepted civil calendar, introduced by Pope
 * Gregory XIII in October 1582 as a reform of the Julian calendar. It is a solar calendar based on
 * a 365-day common year divided into 12 months.
 *
 * <p>This class is auto-generated from YAML. Do not edit manually.
 */
public final class IsoChronology implements ChronologyAlgorithm {

  /** Months of the Gregorian Calendar. */
  public enum Month {
    JANUARY(1, "January", 31, 31),
    FEBRUARY(2, "February", 28, 29),
    MARCH(3, "March", 31, 31),
    APRIL(4, "April", 30, 30),
    MAY(5, "May", 31, 31),
    JUNE(6, "June", 30, 30),
    JULY(7, "July", 31, 31),
    AUGUST(8, "August", 31, 31),
    SEPTEMBER(9, "September", 30, 30),
    OCTOBER(10, "October", 31, 31),
    NOVEMBER(11, "November", 30, 30),
    DECEMBER(12, "December", 31, 31);

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

  /** Days of the week in the Gregorian Calendar. */
  public enum Day {
    MONDAY(0, "Monday"),
    TUESDAY(1, "Tuesday"),
    WEDNESDAY(2, "Wednesday"),
    THURSDAY(3, "Thursday"),
    FRIDAY(4, "Friday"),
    SATURDAY(5, "Saturday"),
    SUNDAY(6, "Sunday");

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

  public static final String ID = "ISO";
  private static final long EPOCH_JDN = 1721426L;

  private static final int[] DAYS_IN_MONTH = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
  private static final int[] LEAP_DAYS_IN_MONTH = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

  public IsoChronology() {}

  @Override
  public String getChronologyId() {
    return ID;
  }

  @Override
  public boolean isLeapYear(int year) {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
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
