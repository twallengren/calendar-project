package com.bdc.chronology.ontology.algorithms;

import com.bdc.chronology.ontology.ChronologyDate;

/**
 * Interface for calendar algorithms that convert between calendar-specific dates and Julian Day
 * Numbers.
 *
 * <p>Implementations of this interface provide the core conversion logic for a specific calendar
 * system, enabling bidirectional conversion between the calendar's date representation and the
 * universal Julian Day Number.
 */
public interface ChronologyAlgorithm {

  /**
   * Returns the chronology identifier this algorithm handles.
   *
   * @return the chronology ID (e.g., "ISO", "HIJRI", "JULIAN")
   */
  String getChronologyId();

  /**
   * Converts a date in this chronology to Julian Day Number.
   *
   * @param year the year in this chronology
   * @param month the month (1-based)
   * @param day the day of month (1-based)
   * @return the Julian Day Number
   * @throws IllegalArgumentException if the date is invalid
   */
  long toJdn(int year, int month, int day);

  /**
   * Converts a ChronologyDate to Julian Day Number.
   *
   * @param date the date to convert
   * @return the Julian Day Number
   * @throws IllegalArgumentException if the date is invalid or chronology doesn't match
   */
  default long toJdn(ChronologyDate date) {
    if (!getChronologyId().equalsIgnoreCase(date.chronologyId())) {
      throw new IllegalArgumentException(
          "Date chronology "
              + date.chronologyId()
              + " doesn't match algorithm "
              + getChronologyId());
    }
    return toJdn(date.year(), date.month(), date.day());
  }

  /**
   * Converts a Julian Day Number to a date in this chronology.
   *
   * @param jdn the Julian Day Number
   * @return the ChronologyDate
   */
  ChronologyDate fromJdn(long jdn);

  /**
   * Returns the year component from a Julian Day Number.
   *
   * @param jdn the Julian Day Number
   * @return the year in this chronology
   */
  default int getYear(long jdn) {
    return fromJdn(jdn).year();
  }

  /**
   * Returns the month component from a Julian Day Number.
   *
   * @param jdn the Julian Day Number
   * @return the month in this chronology (1-based)
   */
  default int getMonth(long jdn) {
    return fromJdn(jdn).month();
  }

  /**
   * Returns the day component from a Julian Day Number.
   *
   * @param jdn the Julian Day Number
   * @return the day of month in this chronology (1-based)
   */
  default int getDay(long jdn) {
    return fromJdn(jdn).day();
  }

  /**
   * Checks if a date is valid in this chronology.
   *
   * @param year the year
   * @param month the month (1-based)
   * @param day the day (1-based)
   * @return true if the date is valid
   */
  boolean isValidDate(int year, int month, int day);

  /**
   * Returns the number of days in a given month.
   *
   * @param year the year
   * @param month the month (1-based)
   * @return the number of days in the month
   */
  int getDaysInMonth(int year, int month);

  /**
   * Checks if a year is a leap year in this chronology.
   *
   * @param year the year
   * @return true if it's a leap year
   */
  boolean isLeapYear(int year);
}
