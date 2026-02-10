package com.bdc.chronology;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.ChronologyRegistry;
import java.time.LocalDate;

/**
 * Facade for converting dates between different chronologies.
 *
 * <p>This class provides a simple API for date conversion, delegating to the {@link
 * ChronologyRegistry} for the actual conversion logic. It maintains backward compatibility with the
 * original API while leveraging the new ontology-based infrastructure.
 */
public class ChronologyTranslator {

  public static final String ISO = "ISO";
  public static final String HIJRI = "HIJRI";
  public static final String JULIAN = "JULIAN";

  private static final ChronologyRegistry registry = ChronologyRegistry.getInstance();

  /**
   * Converts a date from the specified chronology to an ISO LocalDate.
   *
   * @param year the year in the source chronology
   * @param month the month (1-based)
   * @param day the day of month (1-based)
   * @param chronology the source chronology identifier (null defaults to ISO)
   * @return the corresponding ISO LocalDate
   * @throws IllegalArgumentException if the chronology is not supported
   */
  public static LocalDate toIsoDate(int year, int month, int day, String chronology) {
    return registry.toIsoDate(year, month, day, chronology);
  }

  /**
   * Converts a Hijri date to an ISO LocalDate.
   *
   * @param hijriYear the Hijri year
   * @param hijriMonth the Hijri month (1-12)
   * @param hijriDay the Hijri day
   * @return the corresponding ISO LocalDate
   */
  public static LocalDate hijriToIso(int hijriYear, int hijriMonth, int hijriDay) {
    return registry.toIsoDate(hijriYear, hijriMonth, hijriDay, HIJRI);
  }

  /**
   * Converts an ISO LocalDate to a Hijri ChronologyDate.
   *
   * @param isoDate the ISO date
   * @return the corresponding Hijri date as ChronologyDate
   */
  public static ChronologyDate isoToHijri(LocalDate isoDate) {
    return registry.fromIsoDate(isoDate, HIJRI);
  }

  /**
   * Gets the Hijri year for an ISO date.
   *
   * @param isoDate the ISO date
   * @return the Hijri year
   */
  public static int getHijriYear(LocalDate isoDate) {
    return registry.fromIsoDate(isoDate, HIJRI).year();
  }

  /**
   * Gets the Hijri month for an ISO date.
   *
   * @param isoDate the ISO date
   * @return the Hijri month (1-12)
   */
  public static int getHijriMonth(LocalDate isoDate) {
    return registry.fromIsoDate(isoDate, HIJRI).month();
  }

  /**
   * Gets the Hijri day for an ISO date.
   *
   * @param isoDate the ISO date
   * @return the Hijri day of month
   */
  public static int getHijriDay(LocalDate isoDate) {
    return registry.fromIsoDate(isoDate, HIJRI).day();
  }

  /**
   * Checks if a Hijri date is valid.
   *
   * @param year the Hijri year
   * @param month the Hijri month (1-12)
   * @param day the Hijri day
   * @return true if the date is valid
   */
  public static boolean isValidHijriDate(int year, int month, int day) {
    return registry.isValidDate(year, month, day, HIJRI);
  }

  /**
   * Converts an ISO date to a ChronologyDate in the specified chronology.
   *
   * @param isoDate the ISO date
   * @param chronologyId the target chronology
   * @return the ChronologyDate
   */
  public static ChronologyDate toChronologyDate(LocalDate isoDate, String chronologyId) {
    return registry.fromIsoDate(isoDate, chronologyId);
  }

  /**
   * Gets the year in the specified chronology for an ISO date.
   *
   * @param isoDate the ISO date
   * @param chronologyId the target chronology
   * @return the year in the target chronology
   */
  public static int getYear(LocalDate isoDate, String chronologyId) {
    return registry.fromIsoDate(isoDate, chronologyId).year();
  }

  /**
   * Gets the month in the specified chronology for an ISO date.
   *
   * @param isoDate the ISO date
   * @param chronologyId the target chronology
   * @return the month in the target chronology (1-based)
   */
  public static int getMonth(LocalDate isoDate, String chronologyId) {
    return registry.fromIsoDate(isoDate, chronologyId).month();
  }

  /**
   * Gets the day in the specified chronology for an ISO date.
   *
   * @param isoDate the ISO date
   * @param chronologyId the target chronology
   * @return the day of month in the target chronology
   */
  public static int getDay(LocalDate isoDate, String chronologyId) {
    return registry.fromIsoDate(isoDate, chronologyId).day();
  }

  /**
   * Checks if a date is valid in the specified chronology.
   *
   * @param year the year
   * @param month the month
   * @param day the day
   * @param chronologyId the chronology
   * @return true if the date is valid
   */
  public static boolean isValidDate(int year, int month, int day, String chronologyId) {
    return registry.isValidDate(year, month, day, chronologyId);
  }

  /**
   * Gets the year range for a given ISO date range in the specified chronology.
   *
   * @param startIso the start of the ISO date range
   * @param endIso the end of the ISO date range
   * @param chronologyId the target chronology
   * @return array of [startYear, endYear] in the target chronology
   */
  public static int[] getYearRange(LocalDate startIso, LocalDate endIso, String chronologyId) {
    return registry.getYearRange(startIso, endIso, chronologyId);
  }
}
