package com.bdc.chronology.ontology;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * YAML model for chronology definition.
 *
 * <p>This record represents the structure of a chronology YAML file, which defines a calendar
 * system including its metadata, structure (months, weeks), and conversion algorithms.
 *
 * @param kind the kind of specification (should be "chronology")
 * @param id the unique identifier for this chronology
 * @param metadata descriptive information about the chronology
 * @param structure the structural definition of the calendar (months, weeks)
 * @param algorithms the algorithm configuration for date conversion
 */
public record ChronologySpec(
    String kind, String id, Metadata metadata, Structure structure, Algorithms algorithms) {

  /**
   * Metadata about the chronology.
   *
   * @param name the display name
   * @param description a description of the chronology
   */
  public record Metadata(String name, String description) {}

  /**
   * Structural definition of the calendar.
   *
   * @param epochJdn the Julian Day Number of the calendar's epoch
   * @param week week configuration
   * @param months list of month definitions
   */
  public record Structure(
      @JsonProperty("epoch_jdn") Long epochJdn, Week week, List<Month> months) {}

  /**
   * Week configuration.
   *
   * @param days list of day names in order (e.g., ["Sunday", "Monday", ...])
   * @param firstDay the first day of the week (must be one of the day names)
   */
  public record Week(List<String> days, @JsonProperty("first_day") String firstDay) {

    /** Returns the number of days in the week. */
    public int daysPerWeek() {
      return days != null ? days.size() : 7;
    }
  }

  /**
   * Month definition.
   *
   * @param name the month name
   * @param days the number of days in a common year
   * @param leapDays the number of days in a leap year (null if same as days)
   */
  public record Month(String name, int days, @JsonProperty("leap_days") Integer leapDays) {

    /**
     * Returns the number of days in this month for the given leap year status.
     *
     * @param isLeapYear true if the year is a leap year
     * @return the number of days
     */
    public int getDays(boolean isLeapYear) {
      if (isLeapYear && leapDays != null) {
        return leapDays;
      }
      return days;
    }
  }

  /**
   * Algorithm configuration.
   *
   * @param type the algorithm type (FORMULA, LOOKUP_TABLE, METONIC_CYCLE)
   * @param leapYear the leap year formula expression (for FORMULA type)
   * @param table the lookup table name (for LOOKUP_TABLE type)
   * @param fallback the fallback algorithm ID (for LOOKUP_TABLE type)
   * @param cycleLength the cycle length in years (for METONIC_CYCLE type)
   * @param leapYears list of leap years within the cycle (for METONIC_CYCLE type)
   * @param months lookup table entries for LOOKUP_TABLE type
   * @param extraParams additional algorithm-specific parameters
   */
  public record Algorithms(
      String type,
      @JsonProperty("leap_year") String leapYear,
      String table,
      String fallback,
      @JsonProperty("cycle_length") Integer cycleLength,
      @JsonProperty("leap_years") List<Integer> leapYears,
      List<MonthEntry> months,
      @JsonProperty("extra") Map<String, Object> extraParams) {}

  /**
   * Lookup table entry for a specific month.
   *
   * @param year the year in the chronology
   * @param month the month (1-based)
   * @param jdn the Julian Day Number of the first day of this month
   * @param length the number of days in this month
   */
  public record MonthEntry(int year, int month, long jdn, int length) {}
}
