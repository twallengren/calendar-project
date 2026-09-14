package com.bdc.stream;

import com.bdc.chronology.DateRange;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageInterval;
import com.bdc.trust.CoverageQuality;
import com.bdc.trust.DayAssessment;
import com.bdc.trust.DayState;
import com.bdc.trust.EventDetails;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 *       {@link #isEarlyClose}, {@link #adjust}, {@link #businessDayOffset}, {@link #advanceMonths},
 *       {@link #lastBusinessDayOfMonth}) throws {@link OutsideCoverageException}, which carries the
 *       calendar id, the offending date and the range.
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

  /** Explicit completeness intervals. An empty list identifies a legacy artifact. */
  default List<CoverageInterval> coverageIntervals() {
    return List.of();
  }

  /** IANA timezone for local close times, absent when legacy metadata did not declare one. */
  default Optional<ZoneId> timezone() {
    return Optional.empty();
  }

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
    long remaining = Math.abs((long) n);
    boolean forward = n > 0;
    long guard = (long) MAX_SEARCH_DAYS * remaining + MAX_SEARCH_DAYS;
    while (remaining > 0) {
      if (guard-- <= 0) {
        throw new IllegalStateException(
            "Could not find "
                + Math.abs((long) n)
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

  /** Adjusts a date under a standard business-day convention. */
  default LocalDate adjust(LocalDate date, BusinessDayConvention convention) {
    return adjustDetailed(date, convention).resultDate();
  }

  /** Rich form of {@link #adjust}, including confidence across the complete search path. */
  default DateOperationResult adjustDetailed(LocalDate date, BusinessDayConvention convention) {
    return FinancialDateOperations.adjust(this, date, convention);
  }

  /** Moves by a number of business dates; zero preserves the legacy identity behavior. */
  default LocalDate businessDayOffset(LocalDate date, int offset) {
    return businessDayOffsetDetailed(date, offset).resultDate();
  }

  /** Rich business-date offset with every examined date and its aggregate confidence. */
  default DateOperationResult businessDayOffsetDetailed(LocalDate date, int offset) {
    return FinancialDateOperations.offset(this, date, offset);
  }

  /** Advances by calendar months, clips the nominal day, then applies {@code convention}. */
  default LocalDate advanceMonths(LocalDate date, int months, BusinessDayConvention convention) {
    return advanceMonths(date, months, convention, false);
  }

  /** Advances by calendar months, with an explicit business-month-end preservation choice. */
  default LocalDate advanceMonths(
      LocalDate date, int months, BusinessDayConvention convention, boolean preserveEndOfMonth) {
    return advanceMonthsDetailed(date, months, convention, preserveEndOfMonth).resultDate();
  }

  /** Rich month advancement including source/destination month-end decision paths. */
  default DateOperationResult advanceMonthsDetailed(
      LocalDate date, int months, BusinessDayConvention convention) {
    return advanceMonthsDetailed(date, months, convention, false);
  }

  /** Rich month advancement with an explicit business-month-end preservation choice. */
  default DateOperationResult advanceMonthsDetailed(
      LocalDate date, int months, BusinessDayConvention convention, boolean preserveEndOfMonth) {
    return FinancialDateOperations.advanceMonths(
        this, date, months, convention, preserveEndOfMonth);
  }

  /** The last resolved business date in the input date's calendar month. */
  default LocalDate lastBusinessDayOfMonth(LocalDate date) {
    return lastBusinessDayOfMonthDetailed(date).resultDate();
  }

  /** Rich last-business-day query, including the backwards search path. */
  default DateOperationResult lastBusinessDayOfMonthDetailed(LocalDate date) {
    return FinancialDateOperations.lastBusinessDayOfMonth(this, date);
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
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      requireResolved(date);
    }
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
    requireResolved(date);
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
   * Member-specific local early closes. A missing legacy timezone remains null. Prefer this over
   * comparing the local wall-clock values returned by a joint stream's {@link #closeTime}.
   */
  default List<MemberClose> memberCloses(LocalDate date) {
    Optional<LocalTime> close = closeTime(date);
    return close
        .map(value -> List.of(new MemberClose(calendarId(), timezone().orElse(null), value)))
        .orElseGet(List::of);
  }

  /**
   * How much confidence the data for this date deserves. Never throws.
   *
   * <ul>
   *   <li>{@code UNKNOWN} when the date lies outside {@link #range()};
   *   <li>the assessment's effective confidence when explicit scope quality is present;
   *   <li>{@code PROJECTED} when the date is after {@link #verifiedThrough()}, whatever the rows
   *       say, or when any event on the date is {@code PROJECTED};
   *   <li>{@code CONFIRMED} otherwise.
   * </ul>
   */
  default EventStatus status(LocalDate date) {
    if (!range().contains(date)) {
      return EventStatus.UNKNOWN;
    }
    if (!coverageIntervals().isEmpty()) {
      return assessment(date).effectiveConfidence();
    }
    if (verifiedThrough().map(date::isAfter).orElse(false)) {
      return EventStatus.PROJECTED;
    }
    return eventsOn(date).stream().anyMatch(e -> e.status() == EventStatus.PROJECTED)
        ? EventStatus.PROJECTED
        : EventStatus.CONFIRMED;
  }

  /**
   * Explains the actual and scheduled state of a date without throwing. Missing explicit quality
   * for any scope is incomplete. Legacy artifacts stay query-compatible, but their enriched
   * completeness is reported as PROJECTED because they do not prove scope-specific evidence.
   */
  default DayAssessment assessment(LocalDate date) {
    if (!range().contains(date)) {
      Map<CompletenessScope, CoverageQuality> unknown = new EnumMap<>(CompletenessScope.class);
      for (CompletenessScope scope : CompletenessScope.values()) {
        unknown.put(scope, CoverageQuality.INCOMPLETE);
      }
      return new DayAssessment(
          date,
          DayState.UNKNOWN,
          DayState.UNKNOWN,
          EventStatus.UNKNOWN,
          unknown,
          List.of(),
          List.of());
    }

    List<Event> rawEvents = eventsOn(date);
    DayState scheduled = scheduledState(rawEvents);
    Map<CompletenessScope, CoverageQuality> completeness = new EnumMap<>(CompletenessScope.class);
    Set<String> evidence = new LinkedHashSet<>();
    boolean explicit = !coverageIntervals().isEmpty();
    for (CompletenessScope scope : CompletenessScope.values()) {
      CoverageQuality quality = null;
      if (!explicit) {
        quality = CoverageQuality.PROJECTED;
      } else {
        for (CoverageInterval interval : coverageIntervals()) {
          if (interval.scope() == scope && interval.contains(date)) {
            quality = strongerUncertainty(quality, interval.quality());
            evidence.addAll(interval.evidenceIds());
          }
        }
      }
      completeness.put(scope, quality == null ? CoverageQuality.INCOMPLETE : quality);
    }

    boolean incomplete = completeness.containsValue(CoverageQuality.INCOMPLETE);
    boolean projected = completeness.containsValue(CoverageQuality.PROJECTED);
    boolean rawProjected = rawEvents.stream().anyMatch(e -> e.status() == EventStatus.PROJECTED);
    EventStatus confidence =
        incomplete
            ? EventStatus.UNKNOWN
            : projected || rawProjected ? EventStatus.PROJECTED : EventStatus.CONFIRMED;
    if (!explicit) {
      confidence =
          verifiedThrough().map(date::isAfter).orElse(false) || rawProjected
              ? EventStatus.PROJECTED
              : EventStatus.CONFIRMED;
    }
    EventStatus effectiveConfidence = confidence;
    DayState actual = incomplete ? DayState.UNKNOWN : scheduled;
    List<EventDetails> details =
        eventDetailsOn(date).stream()
            .map(
                detail -> {
                  return new EventDetails(
                      detail.event(),
                      detail.rawStatus(),
                      effectiveConfidence,
                      detail.evidenceIds(),
                      detail.nominalNativeDate(),
                      detail.chronologyProfile(),
                      detail.chronologyProvider(),
                      detail.observationLineage());
                })
            .toList();
    details.forEach(detail -> evidence.addAll(detail.evidenceIds()));
    return new DayAssessment(
        date,
        actual,
        scheduled,
        effectiveConfidence,
        completeness,
        evidence.stream().sorted().toList(),
        details);
  }

  /** Raw event provenance, with assessment() supplying effective day confidence separately. */
  default List<EventDetails> eventDetailsOn(LocalDate date) {
    return eventsOn(date).stream()
        .map(
            event ->
                new EventDetails(
                    event,
                    event.status(),
                    event.status(),
                    List.of(),
                    null,
                    null,
                    null,
                    event.observedFrom() == null
                        ? List.of()
                        : List.of(event.observedFrom(), event.date())))
        .toList();
  }

  /** Refuses boolean/session answers when an explicitly modelled scope is incomplete. */
  default void requireResolved(LocalDate date) {
    DayAssessment assessment = assessment(date);
    if (assessment.state() != DayState.UNKNOWN) {
      return;
    }
    if (!range().contains(date)) {
      throw new OutsideCoverageException(calendarId(), date, range());
    }
    Set<CompletenessScope> incomplete =
        assessment.completeness().entrySet().stream()
            .filter(entry -> entry.getValue() == CoverageQuality.INCOMPLETE)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    throw new UnresolvedDateException(calendarId(), date, range(), incomplete);
  }

  private static CoverageQuality strongerUncertainty(
      CoverageQuality current, CoverageQuality candidate) {
    if (current == null) {
      return candidate;
    }
    if (current == CoverageQuality.INCOMPLETE || candidate == CoverageQuality.INCOMPLETE) {
      return CoverageQuality.INCOMPLETE;
    }
    return current == CoverageQuality.PROJECTED || candidate == CoverageQuality.PROJECTED
        ? CoverageQuality.PROJECTED
        : CoverageQuality.VERIFIED;
  }

  private static DayState scheduledState(List<Event> events) {
    if (events.stream()
        .anyMatch(e -> e.type() == EventType.CLOSED || e.type() == EventType.WEEKEND)) {
      return DayState.CLOSED;
    }
    if (events.stream().anyMatch(e -> e.type() == EventType.EARLY_CLOSE)) {
      return DayState.EARLY_CLOSE;
    }
    return DayState.OPEN;
  }
}
