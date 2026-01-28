package com.bdc.stream;

import com.bdc.model.Event;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A queryable representation of calendar events.
 *
 * <p>DateStream provides methods for querying calendar events and business days. Implementations
 * may be lazy (compute on demand) or materialized (pre-computed).
 */
public interface DateStream {

  // === Core Queries ===

  /** Get all events in the given date range (inclusive). */
  List<Event> eventsInRange(LocalDate from, LocalDate to);

  /**
   * Get the event on a specific date, if any. If multiple events exist on the same date, returns
   * the first one.
   */
  Optional<Event> eventOn(LocalDate date);

  /** Get all events on a specific date. */
  List<Event> eventsOn(LocalDate date);

  /** Check if the given date is a business day (not a weekend, not a CLOSED event). */
  boolean isBusinessDay(LocalDate date);

  // === Navigation ===

  /**
   * Find the next business day after the given date (exclusive).
   *
   * @param from the starting date (not included in search)
   * @return the next business day
   */
  LocalDate nextBusinessDay(LocalDate from);

  /**
   * Find the previous business day before the given date (exclusive).
   *
   * @param from the starting date (not included in search)
   * @return the previous business day
   */
  LocalDate prevBusinessDay(LocalDate from);

  /**
   * Find the nth business day relative to the given date. Positive n moves forward, negative n
   * moves backward.
   *
   * @param from the starting date
   * @param n the number of business days to move (positive = forward, negative = backward)
   * @return the nth business day
   */
  LocalDate nthBusinessDay(LocalDate from, int n);

  // === Counting ===

  /** Count the number of business days in the given range (inclusive). */
  long businessDaysInRange(LocalDate from, LocalDate to);

  /** Count the total number of events in the given range (inclusive). */
  long eventCountInRange(LocalDate from, LocalDate to);

  // === Factory Methods ===

  /** Get the calendar ID this stream is based on. */
  String calendarId();
}
