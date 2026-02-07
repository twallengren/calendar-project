package com.bdc.chronology.ontology;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * YAML model for chronology lookup tables.
 *
 * <p>This record represents the structure of a lookup table YAML file, which contains precomputed
 * data for observation-based calendars like the Islamic Hijri calendar.
 *
 * @param kind the kind of specification (should be "chronology_table")
 * @param id the unique identifier for this table
 * @param metadata descriptive information about the table
 * @param entries the table entries
 */
public record ChronologyTable(String kind, String id, Metadata metadata, List<Entry> entries) {

  /**
   * Metadata about the table.
   *
   * @param name the display name
   * @param description a description of the table
   * @param source the data source or authority
   * @param validFrom the earliest date this table is valid for
   * @param validTo the latest date this table is valid for
   */
  public record Metadata(
      String name,
      String description,
      String source,
      @JsonProperty("valid_from") String validFrom,
      @JsonProperty("valid_to") String validTo) {}

  /**
   * A single table entry representing a year or month boundary.
   *
   * <p>The exact interpretation depends on the table format:
   *
   * <ul>
   *   <li>Year tables: Maps chronology year to JDN of first day
   *   <li>Month tables: Maps year/month to JDN of first day and month length
   * </ul>
   *
   * @param year the year in the chronology
   * @param month the month (1-based, null for year tables)
   * @param jdn the Julian Day Number of the first day
   * @param length the number of days (for month tables)
   */
  public record Entry(int year, Integer month, long jdn, Integer length) {}

  /**
   * Finds the entry for a specific year.
   *
   * @param year the year to find
   * @return the entry, or null if not found
   */
  public Entry findByYear(int year) {
    return entries.stream()
        .filter(e -> e.year() == year && e.month() == null)
        .findFirst()
        .orElse(null);
  }

  /**
   * Finds the entry for a specific year and month.
   *
   * @param year the year
   * @param month the month (1-based)
   * @return the entry, or null if not found
   */
  public Entry findByYearMonth(int year, int month) {
    return entries.stream()
        .filter(e -> e.year() == year && e.month() != null && e.month() == month)
        .findFirst()
        .orElse(null);
  }

  /**
   * Finds the entry containing a specific JDN.
   *
   * @param jdn the Julian Day Number
   * @return the entry containing this JDN, or null if not found
   */
  public Entry findByJdn(long jdn) {
    Entry result = null;
    for (Entry entry : entries) {
      if (entry.jdn() <= jdn) {
        if (result == null || entry.jdn() > result.jdn()) {
          result = entry;
        }
      }
    }
    return result;
  }
}
