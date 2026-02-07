package com.bdc.chronology.ontology.algorithms;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.JulianDayNumber;
import java.time.LocalDate;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/**
 * Algorithm implementation for the Islamic (Hijri) calendar using Java's built-in HijrahChronology.
 *
 * <p>This implementation wraps Java's {@link HijrahChronology} which uses the Umm al-Qura calendar
 * variant. The Umm al-Qura calendar is used in Saudi Arabia and is based on astronomical
 * calculations rather than lunar observation.
 *
 * <p>The Hijri calendar is a lunar calendar consisting of 12 months in a year of 354 or 355 days.
 * Months alternate between 29 and 30 days, with the 12th month (Dhu al-Hijjah) having 30 days in
 * leap years.
 */
public class JavaHijriAlgorithm implements ChronologyAlgorithm {

  public static final String ID = "HIJRI";

  @Override
  public String getChronologyId() {
    return ID;
  }

  @Override
  public long toJdn(int year, int month, int day) {
    HijrahDate hijrahDate = HijrahChronology.INSTANCE.date(year, month, day);
    LocalDate isoDate = LocalDate.from(hijrahDate);
    return JulianDayNumber.fromLocalDate(isoDate);
  }

  @Override
  public ChronologyDate fromJdn(long jdn) {
    LocalDate isoDate = JulianDayNumber.toLocalDate(jdn);
    HijrahDate hijrahDate = HijrahDate.from(isoDate);
    return new ChronologyDate(
        ID,
        hijrahDate.get(ChronoField.YEAR),
        hijrahDate.get(ChronoField.MONTH_OF_YEAR),
        hijrahDate.get(ChronoField.DAY_OF_MONTH));
  }

  @Override
  public boolean isValidDate(int year, int month, int day) {
    try {
      HijrahChronology.INSTANCE.date(year, month, day);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public int getDaysInMonth(int year, int month) {
    if (month < 1 || month > 12) {
      throw new IllegalArgumentException("Invalid month: " + month);
    }
    // Create the first day of the month and get month length
    HijrahDate firstOfMonth = HijrahChronology.INSTANCE.date(year, month, 1);
    return (int) firstOfMonth.lengthOfMonth();
  }

  @Override
  public boolean isLeapYear(int year) {
    // Create any date in the year and check if it's a leap year
    try {
      HijrahDate date = HijrahChronology.INSTANCE.date(year, 1, 1);
      return date.isLeapYear();
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Converts a Hijri date to ISO LocalDate.
   *
   * @param year Hijri year
   * @param month Hijri month (1-12)
   * @param day Hijri day
   * @return the corresponding ISO LocalDate
   */
  public LocalDate toIsoDate(int year, int month, int day) {
    HijrahDate hijrahDate = HijrahChronology.INSTANCE.date(year, month, day);
    return LocalDate.from(hijrahDate);
  }

  /**
   * Converts an ISO LocalDate to Hijri components.
   *
   * @param isoDate the ISO date
   * @return array of [year, month, day] in Hijri calendar
   */
  public int[] fromIsoDate(LocalDate isoDate) {
    HijrahDate hijrahDate = HijrahDate.from(isoDate);
    return new int[] {
      hijrahDate.get(ChronoField.YEAR),
      hijrahDate.get(ChronoField.MONTH_OF_YEAR),
      hijrahDate.get(ChronoField.DAY_OF_MONTH)
    };
  }
}
