package com.bdc.chronology;

import java.time.LocalDate;
import java.util.stream.Stream;

/**
 * An inclusive range of civil dates.
 *
 * <p>Year ranges in a non-Gregorian chronology come from {@code ChronologyTranslator.getYearRange}
 * in the toolchain: this record stays free of the chronology machinery so it can ship in the
 * dependency-free core jar.
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
   * Returns the ISO year range for this date range.
   *
   * @return array of [startYear, endYear] in ISO calendar
   */
  public int[] isoYearRange() {
    return new int[] {start.getYear(), end.getYear()};
  }
}
