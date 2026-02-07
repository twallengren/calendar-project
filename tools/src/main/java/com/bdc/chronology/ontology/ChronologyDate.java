package com.bdc.chronology.ontology;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A chronology-agnostic date representation.
 *
 * <p>This record represents a date in any calendar system by storing the year, month, and day along
 * with the chronology identifier. It provides methods to convert to/from Julian Day Number and ISO
 * LocalDate.
 *
 * @param chronologyId the identifier of the chronology (e.g., "ISO", "HIJRI", "JULIAN")
 * @param year the year in the chronology
 * @param month the month (1-based)
 * @param day the day of month (1-based)
 */
public record ChronologyDate(String chronologyId, int year, int month, int day) {

  public ChronologyDate {
    Objects.requireNonNull(chronologyId, "chronologyId must not be null");
    if (month < 1 || month > 13) {
      throw new IllegalArgumentException("month must be between 1 and 13");
    }
    if (day < 1 || day > 31) {
      throw new IllegalArgumentException("day must be between 1 and 31");
    }
  }

  /**
   * Converts this date to Julian Day Number.
   *
   * @return the Julian Day Number
   * @throws IllegalStateException if the chronology is not registered
   */
  public long toJdn() {
    return ChronologyRegistry.getInstance().toJdn(this);
  }

  /**
   * Creates a ChronologyDate from a Julian Day Number.
   *
   * @param jdn the Julian Day Number
   * @param chronologyId the target chronology identifier
   * @return the ChronologyDate in the specified chronology
   * @throws IllegalArgumentException if the chronology is not registered
   */
  public static ChronologyDate fromJdn(long jdn, String chronologyId) {
    return ChronologyRegistry.getInstance().fromJdn(jdn, chronologyId);
  }

  /**
   * Converts this date to an ISO LocalDate.
   *
   * @return the corresponding ISO LocalDate
   */
  public LocalDate toIsoDate() {
    long jdn = toJdn();
    return JulianDayNumber.toLocalDate(jdn);
  }

  /**
   * Creates a ChronologyDate from an ISO LocalDate.
   *
   * @param date the ISO LocalDate
   * @param chronologyId the target chronology identifier
   * @return the ChronologyDate in the specified chronology
   */
  public static ChronologyDate fromIsoDate(LocalDate date, String chronologyId) {
    long jdn = JulianDayNumber.fromLocalDate(date);
    return fromJdn(jdn, chronologyId);
  }

  /**
   * Creates a ChronologyDate for an ISO date.
   *
   * @param year the year
   * @param month the month
   * @param day the day
   * @return the ChronologyDate
   */
  public static ChronologyDate iso(int year, int month, int day) {
    return new ChronologyDate("ISO", year, month, day);
  }

  /**
   * Converts this date to another chronology.
   *
   * @param targetChronologyId the target chronology identifier
   * @return the ChronologyDate in the target chronology
   */
  public ChronologyDate toChronology(String targetChronologyId) {
    if (chronologyId.equals(targetChronologyId)) {
      return this;
    }
    return fromJdn(toJdn(), targetChronologyId);
  }

  @Override
  public String toString() {
    return String.format("%s[%04d-%02d-%02d]", chronologyId, year, month, day);
  }
}
