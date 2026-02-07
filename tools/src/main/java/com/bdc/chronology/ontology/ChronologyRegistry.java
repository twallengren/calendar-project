package com.bdc.chronology.ontology;

import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;
import com.bdc.chronology.ontology.algorithms.IsoAlgorithm;
import com.bdc.chronology.ontology.algorithms.JavaHijriAlgorithm;
import com.bdc.chronology.ontology.algorithms.JulianAlgorithm;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for chronology algorithms.
 *
 * <p>This singleton class manages the registration and lookup of chronology algorithms. It provides
 * a central access point for converting dates between different calendar systems using Julian Day
 * Number as a universal pivot.
 *
 * <p>Built-in algorithms are registered automatically:
 *
 * <ul>
 *   <li>ISO - Gregorian/ISO calendar
 *   <li>HIJRI - Islamic (Hijri) calendar via Java's HijrahChronology
 *   <li>JULIAN - Julian calendar
 * </ul>
 */
public class ChronologyRegistry {

  private static final ChronologyRegistry INSTANCE = new ChronologyRegistry();

  private final Map<String, ChronologyAlgorithm> algorithms = new ConcurrentHashMap<>();

  private ChronologyRegistry() {
    // Register built-in algorithms
    registerBuiltInAlgorithms();
  }

  private void registerBuiltInAlgorithms() {
    register(new IsoAlgorithm());
    register(new JavaHijriAlgorithm());
    register(new JulianAlgorithm());
  }

  /**
   * Returns the singleton instance of the registry.
   *
   * @return the registry instance
   */
  public static ChronologyRegistry getInstance() {
    return INSTANCE;
  }

  /**
   * Registers a chronology algorithm.
   *
   * @param algorithm the algorithm to register
   * @throws IllegalArgumentException if an algorithm with the same ID is already registered
   */
  public void register(ChronologyAlgorithm algorithm) {
    String id = algorithm.getChronologyId().toUpperCase();
    algorithms.put(id, algorithm);
  }

  /**
   * Returns the algorithm for the specified chronology.
   *
   * @param chronologyId the chronology identifier (case-insensitive)
   * @return the algorithm
   * @throws IllegalArgumentException if the chronology is not registered
   */
  public ChronologyAlgorithm getAlgorithm(String chronologyId) {
    String id = chronologyId.toUpperCase();
    ChronologyAlgorithm algorithm = algorithms.get(id);
    if (algorithm == null) {
      throw new IllegalArgumentException("Unknown chronology: " + chronologyId);
    }
    return algorithm;
  }

  /**
   * Checks if a chronology is registered.
   *
   * @param chronologyId the chronology identifier (case-insensitive)
   * @return true if the chronology is registered
   */
  public boolean hasChronology(String chronologyId) {
    return algorithms.containsKey(chronologyId.toUpperCase());
  }

  /**
   * Returns the set of registered chronology IDs.
   *
   * @return set of chronology identifiers
   */
  public Set<String> getRegisteredChronologies() {
    return Set.copyOf(algorithms.keySet());
  }

  /**
   * Converts a date to Julian Day Number.
   *
   * @param date the ChronologyDate to convert
   * @return the Julian Day Number
   */
  public long toJdn(ChronologyDate date) {
    return getAlgorithm(date.chronologyId()).toJdn(date.year(), date.month(), date.day());
  }

  /**
   * Converts a Julian Day Number to a date in the specified chronology.
   *
   * @param jdn the Julian Day Number
   * @param chronologyId the target chronology
   * @return the ChronologyDate
   */
  public ChronologyDate fromJdn(long jdn, String chronologyId) {
    return getAlgorithm(chronologyId).fromJdn(jdn);
  }

  /**
   * Converts a date from one chronology to an ISO LocalDate.
   *
   * @param year the year in the source chronology
   * @param month the month
   * @param day the day
   * @param chronology the source chronology (null or "ISO" for Gregorian)
   * @return the corresponding ISO LocalDate
   */
  public LocalDate toIsoDate(int year, int month, int day, String chronology) {
    if (chronology == null || "ISO".equalsIgnoreCase(chronology)) {
      return LocalDate.of(year, month, day);
    }
    long jdn = getAlgorithm(chronology).toJdn(year, month, day);
    return JulianDayNumber.toLocalDate(jdn);
  }

  /**
   * Converts an ISO LocalDate to a date in the specified chronology.
   *
   * @param isoDate the ISO date
   * @param chronologyId the target chronology
   * @return the ChronologyDate
   */
  public ChronologyDate fromIsoDate(LocalDate isoDate, String chronologyId) {
    long jdn = JulianDayNumber.fromLocalDate(isoDate);
    return fromJdn(jdn, chronologyId);
  }

  /**
   * Converts a date from one chronology to another.
   *
   * @param date the source date
   * @param targetChronologyId the target chronology
   * @return the date in the target chronology
   */
  public ChronologyDate convert(ChronologyDate date, String targetChronologyId) {
    if (date.chronologyId().equalsIgnoreCase(targetChronologyId)) {
      return date;
    }
    long jdn = toJdn(date);
    return fromJdn(jdn, targetChronologyId);
  }

  /**
   * Gets the year range for a given ISO date range in the specified chronology.
   *
   * @param startIso the start of the ISO date range
   * @param endIso the end of the ISO date range
   * @param chronologyId the target chronology
   * @return array of [startYear, endYear] in the target chronology
   */
  public int[] getYearRange(LocalDate startIso, LocalDate endIso, String chronologyId) {
    ChronologyDate startDate = fromIsoDate(startIso, chronologyId);
    ChronologyDate endDate = fromIsoDate(endIso, chronologyId);
    return new int[] {startDate.year(), endDate.year()};
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
  public boolean isValidDate(int year, int month, int day, String chronologyId) {
    return getAlgorithm(chronologyId).isValidDate(year, month, day);
  }

  /**
   * Clears all registered algorithms and re-registers built-in ones.
   *
   * <p>This is primarily useful for testing.
   */
  public void reset() {
    algorithms.clear();
    registerBuiltInAlgorithms();
  }
}
