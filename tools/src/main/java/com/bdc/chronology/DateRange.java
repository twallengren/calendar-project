package com.bdc.chronology;

import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.stream.Stream;

/**
 * Represents a date range with utility methods for chronology-aware operations.
 *
 * @param start the start date (inclusive)
 * @param end the end date (inclusive)
 */
public record DateRange(LocalDate start, LocalDate end) {

  public DateRange {
    if (start.isAfter(end)) {
      throw new IllegalArgumentException("start must not be after end");
    }
  }

  /**
   * Checks if this range contains the specified date.
   *
   * @param date the date to check
   * @return true if the date is within the range (inclusive)
   */
  public boolean contains(LocalDate date) {
    return !date.isBefore(start) && !date.isAfter(end);
  }

  /**
   * Returns a stream of all dates in this range.
   *
   * @return stream of LocalDate from start to end (inclusive)
   */
  public Stream<LocalDate> stream() {
    return start.datesUntil(end.plusDays(1));
  }

  /**
   * Returns the year range in the specified chronology.
   *
   * @param chronologyId the chronology identifier (e.g., "ISO", "HIJRI", "JULIAN")
   * @return array of [startYear, endYear] in the specified chronology
   */
  public int[] yearRange(String chronologyId) {
    return ChronologyTranslator.getYearRange(start, end, chronologyId);
  }

  /**
   * Returns the Hijri year range for this date range.
   *
   * @return array of [startYear, endYear] in Hijri calendar
   * @deprecated Use {@link #yearRange(String)} with "HIJRI" instead
   */
  @Deprecated
  public int[] hijriYearRange() {
    HijrahDate startHijri = HijrahDate.from(start);
    HijrahDate endHijri = HijrahDate.from(end);
    int startYear = startHijri.get(ChronoField.YEAR);
    int endYear = endHijri.get(ChronoField.YEAR);
    return new int[] {startYear, endYear};
  }

  /**
   * Returns the ISO year range for this date range.
   *
   * @return array of [startYear, endYear] in ISO calendar
   */
  public int[] isoYearRange() {
    return new int[] {start.getYear(), end.getYear()};
  }
}
