package com.bdc.stream;

import com.bdc.chronology.DateRange;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The query API over a calendar: the one surface every distribution channel (CLI, static JSON API,
 * language bindings, MCP server) mirrors.
 *
 * <p>Implementations supply four primitives — {@link #calendarId()}, {@link #range()}, {@link
 * #verifiedThrough()}, {@link #eventsInRange}, {@link #eventsOn} and {@link #isBusinessDay} — and
 * inherit every derived operation from this interface, so all implementations answer identically.
 *
 * <h2>Out-of-range contract</h2>
 *
 * <p>Every stream has a {@link #range()}: the window in which absence of an event means "open", not
 * "unknown". Outside it:
 *
 * <ul>
 *   <li>{@link #status(LocalDate)} returns {@link EventStatus#UNKNOWN} — it never throws, so it is
 *       the safe way to probe a date first;
 *   <li>every other query ({@link #eventsOn}, {@link #eventsInRange}, {@link #isBusinessDay},
 *       {@link #nextBusinessDay}, {@link #prevBusinessDay}, {@link #nthBusinessDay}, {@link
 *       #businessDaysInRange}, {@link #eventCountInRange}, {@link #eventOn}, {@link #closeTime},
 *       {@link #isEarlyClose}) throws {@link OutsideCoverageException}, which carries the calendar
 *       id, the offending date and the range.
 * </ul>
 *
 * <p>A navigation query whose bounded search walks past the end of the range therefore throws
 * rather than guessing. A stream that declares no bounds (a YAML-backed stream whose calendar has
 * no {@code coverage} block) reports the unbounded range {@code LocalDate.MIN..LocalDate.MAX} and
 * never throws for being out of range.
 *
 * <h2>Status</h2>
 *
 * <p>{@link #status(LocalDate)} answers how much confidence the data for a date deserves: {@code
 * UNKNOWN} outside the range, {@code PROJECTED} after {@link #verifiedThrough()} (regardless of
 * what the underlying rows say, because nobody has checked that far) or when any event on the date
 * is itself {@code PROJECTED}, else {@code CONFIRMED}.
 */
public interface DateStream {

  /** Bounded searches never walk further than this many days for a single business day. */
  int MAX_SEARCH_DAYS = 366;

  // === Identity and bounds ===

  /** The calendar id this stream answers for. */
  String calendarId();

  /**
   * The window in which this stream's answers are meaningful: the declared {@code coverage} for a
   * YAML-backed stream, the generated range for an artifact-backed one, and {@code
   * LocalDate.MIN..LocalDate.MAX} for a stream with no declared bounds.
   */
  DateRange range();

  /**
   * The last date up to which the data has been checked against authoritative sources, when the
   * calendar declares one. Dates after it are {@link EventStatus#PROJECTED}.
   */
  Optional<LocalDate> verifiedThrough();

  // === Core queries ===

  /**
   * Every event in the range (inclusive), ordered by date.
   *
   * @throws IllegalArgumentException if {@code from} is after {@code to}
   * @throws OutsideCoverageException if either endpoint lies outside {@link #range()}
   */
  List<Event> eventsInRange(LocalDate from, LocalDate to);

  /**
   * Every event on a date (possibly empty).
   *
   * @throws OutsideCoverageException if the date lies outside {@link #range()}
   */
  List<Event> eventsOn(LocalDate date);

  /**
   * Whether the market trades on this date: not a weekend, no CLOSED event. An EARLY_CLOSE day is
   * still a business day.
   *
   * @throws OutsideCoverageException if the date lies outside {@link #range()}
   */
  boolean isBusinessDay(LocalDate date);

  /**
   * The first event on a date, if any. Where several events share a date the order is the order of
   * {@link #eventsOn}.
   *
   * @throws OutsideCoverageException if the date lies outside {@link #range()}
   */
  default Optional<Event> eventOn(LocalDate date) {
    List<Event> events = eventsOn(date);
    return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
  }

  // === Navigation ===

  /**
   * The first business day strictly after {@code from}.
   *
   * @throws OutsideCoverageException if the search leaves {@link #range()}
   * @throws IllegalStateException if no business day is found within {@value #MAX_SEARCH_DAYS} days
   */
  default LocalDate nextBusinessDay(LocalDate from) {
    LocalDate candidate = from.plusDays(1);
    for (int i = 0; i < MAX_SEARCH_DAYS; i++) {
      if (isBusinessDay(candidate)) {
        return candidate;
      }
      candidate = candidate.plusDays(1);
    }
    throw new IllegalStateException(
        "No business day within " + MAX_SEARCH_DAYS + " days after " + from);
  }

  /**
   * The last business day strictly before {@code from}.
   *
   * @throws OutsideCoverageException if the search leaves {@link #range()}
   * @throws IllegalStateException if no business day is found within {@value #MAX_SEARCH_DAYS} days
   */
  default LocalDate prevBusinessDay(LocalDate from) {
    LocalDate candidate = from.minusDays(1);
    for (int i = 0; i < MAX_SEARCH_DAYS; i++) {
      if (isBusinessDay(candidate)) {
        return candidate;
      }
      candidate = candidate.minusDays(1);
    }
    throw new IllegalStateException(
        "No business day within " + MAX_SEARCH_DAYS + " days before " + from);
  }

  /**
   * The {@code n}th business day from {@code from}, counting forward for positive {@code n} and
   * backward for negative {@code n}. {@code n == 0} returns {@code from} unchanged, whether or not
   * it is a business day; otherwise the starting date is never counted, so T+1 from a Friday is the
   * following Monday.
   *
   * @throws OutsideCoverageException if the search leaves {@link #range()}
   * @throws IllegalStateException if the walk exceeds {@code 366 * |n| + 366} days
   */
  default LocalDate nthBusinessDay(LocalDate from, int n) {
    if (n == 0) {
      return from;
    }
    LocalDate current = from;
    int remaining = Math.abs(n);
    boolean forward = n > 0;
    long guard = (long) MAX_SEARCH_DAYS * remaining + MAX_SEARCH_DAYS;
    while (remaining > 0) {
      if (guard-- <= 0) {
        throw new IllegalStateException(
            "Could not find "
                + Math.abs(n)
                + " business days "
                + (forward ? "after " : "before ")
                + from);
      }
      current = forward ? current.plusDays(1) : current.minusDays(1);
      if (isBusinessDay(current)) {
        remaining--;
      }
    }
    return current;
  }

  // === Counting ===

  /**
   * The number of business days in the range (both endpoints included).
   *
   * @throws OutsideCoverageException if either endpoint lies outside {@link #range()}
   */
  default long businessDaysInRange(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    long count = 0;
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      if (isBusinessDay(d)) {
        count++;
      }
    }
    return count;
  }

  /**
   * The number of events in the range (both endpoints included), counting every event, weekend rows
   * included where the stream carries them.
   *
   * @throws OutsideCoverageException if either endpoint lies outside {@link #range()}
   */
  default long eventCountInRange(LocalDate from, LocalDate to) {
    return eventsInRange(from, to).size();
  }

  // === Session detail ===

  /**
   * The local close time of a shortened session, if the date carries an EARLY_CLOSE event with a
   * declared time. Where several apply, the earliest wins. The time is local to the calendar's
   * declared timezone.
   *
   * @throws OutsideCoverageException if the date lies outside {@link #range()}
   */
  default Optional<LocalTime> closeTime(LocalDate date) {
    return eventsOn(date).stream()
        .filter(e -> e.type() == EventType.EARLY_CLOSE && e.closeTime() != null)
        .map(Event::closeTime)
        .min(Comparator.naturalOrder());
  }

  /**
   * Whether the date is a shortened session: equivalent to {@code closeTime(date).isPresent()}. A
   * fully closed date is not an early close (CLOSED beats EARLY_CLOSE on the same date).
   *
   * @throws OutsideCoverageException if the date lies outside {@link #range()}
   */
  default boolean isEarlyClose(LocalDate date) {
    return closeTime(date).isPresent();
  }

  /**
   * How much confidence the data for this date deserves. Never throws.
   *
   * <ul>
   *   <li>{@code UNKNOWN} when the date lies outside {@link #range()};
   *   <li>{@code PROJECTED} when the date is after {@link #verifiedThrough()}, whatever the rows
   *       say, or when any event on the date is {@code PROJECTED};
   *   <li>{@code CONFIRMED} otherwise.
   * </ul>
   */
  default EventStatus status(LocalDate date) {
    if (!range().contains(date)) {
      return EventStatus.UNKNOWN;
    }
    if (verifiedThrough().map(date::isAfter).orElse(false)) {
      return EventStatus.PROJECTED;
    }
    return eventsOn(date).stream().anyMatch(e -> e.status() == EventStatus.PROJECTED)
        ? EventStatus.PROJECTED
        : EventStatus.CONFIRMED;
  }
}
