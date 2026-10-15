package com.bdc.stream;

import com.bdc.chronology.DateRange;
import com.bdc.finance.DayCount;
import com.bdc.finance.Tenor;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageInterval;
import com.bdc.trust.CoverageQuality;
import com.bdc.trust.DateQuestion;
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
   * Whether trades are struck on this date: a business day, or a non-business day that carries a
   * {@link EventType#SPECIAL_SESSION} (Diwali Muhurat trading on an exchange holiday, say). Date
   * arithmetic never uses this; it answers "did the market trade?", not "is this a business day?".
   * It needs the same scopes as {@link #isBusinessDay}, because special sessions are part of the
   * schedule that evidence verifies.
   *
   * @throws OutsideCoverageException if the date lies outside {@link #range()}
   * @throws UnresolvedDateException if a business-day scope is incomplete on the date
   */
  default boolean isTradingDay(LocalDate date) {
    if (isBusinessDay(date)) {
      return true;
    }
    return eventsOn(date).stream().anyMatch(e -> e.type() == EventType.SPECIAL_SESSION);
  }

  /**
   * The special sessions held on a date, ordered by open time; empty on most dates. A session's
   * times are local to the calendar's timezone. Session detail needs the full {@link
   * DateQuestion#SESSION} scopes, like {@link #closeTime}.
   *
   * @throws OutsideCoverageException if the date lies outside {@link #range()}
   * @throws UnresolvedDateException if any scope is incomplete on the date
   */
  default List<SpecialSession> specialSessions(LocalDate date) {
    requireResolved(date);
    return eventsOn(date).stream()
        .filter(e -> e.type() == EventType.SPECIAL_SESSION)
        .sorted(Comparator.comparing(Event::openTime).thenComparing(Event::closeTime))
        .map(
            e ->
                new SpecialSession(
                    calendarId(),
                    timezone().orElse(null),
                    e.openTime(),
                    e.closeTime(),
                    e.key(),
                    e.description(),
                    e.status()))
        .toList();
  }

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

  /**
   * Adds a market tenor ({@code 3M}, {@code 2W}, {@code 1Y}, {@code T+2}) and adjusts the result
   * under {@code convention}. Business-day tenors walk business days and ignore the convention.
   */
  default LocalDate addTenor(LocalDate date, Tenor tenor, BusinessDayConvention convention) {
    return addTenorDetailed(date, tenor, convention, false).resultDate();
  }

  /** {@link #addTenor(LocalDate, Tenor, BusinessDayConvention)} with the tenor as text. */
  default LocalDate addTenor(LocalDate date, String tenor, BusinessDayConvention convention) {
    return addTenor(date, Tenor.parse(tenor), convention);
  }

  /** Rich tenor addition; {@code preserveEndOfMonth} applies to month and year tenors only. */
  default DateOperationResult addTenorDetailed(
      LocalDate date, Tenor tenor, BusinessDayConvention convention, boolean preserveEndOfMonth) {
    return FinancialDateOperations.addTenor(this, date, tenor, convention, preserveEndOfMonth);
  }

  /**
   * The year fraction between two dates under a day-count convention. {@link DayCount#BUS_252}
   * counts this calendar's business days; ACT/ACT ICMA needs a reference period and is evaluated
   * directly on {@link DayCount}.
   */
  default double yearFraction(LocalDate start, LocalDate end, DayCount dayCount) {
    return yearFractionDetailed(start, end, dayCount).yearFraction();
  }

  /** Rich year fraction with the day count and, for BUS/252, the confidence of the counted days. */
  default YearFractionResult yearFractionDetailed(
      LocalDate start, LocalDate end, DayCount dayCount) {
    return FinancialDateOperations.yearFraction(this, start, end, dayCount);
  }

  /**
   * Generates a periodic schedule (coupon, payment, reset or settlement dates) on this calendar:
   * the definition's frequency, stub and roll convention produce unadjusted boundaries and every
   * boundary is then adjusted under its business-day convention. The result carries the weakest
   * business-day confidence of every date examined along the way.
   */
  default Schedule schedule(ScheduleDefinition definition) {
    return ScheduleGenerator.generate(this, definition);
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
   *
   * <p>The result carries two views: {@code state}/{@code effectiveConfidence} for the full {@link
   * DateQuestion#SESSION} question, and {@code businessDayState}/{@code businessDayConfidence} for
   * {@link DateQuestion#BUSINESS_DAY}, which ignores early-close evidence.
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
          List.of(),
          DayState.UNKNOWN,
          EventStatus.UNKNOWN);
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

    boolean legacyProjected = verifiedThrough().map(date::isAfter).orElse(false);
    EventStatus effectiveConfidence =
        confidence(
            completeness,
            DateQuestion.SESSION,
            rawEvents.stream().anyMatch(e -> e.status() == EventStatus.PROJECTED),
            explicit,
            legacyProjected);
    EventStatus businessDayConfidence =
        confidence(
            completeness,
            DateQuestion.BUSINESS_DAY,
            rawEvents.stream()
                .anyMatch(
                    e ->
                        e.status() == EventStatus.PROJECTED
                            && (e.type() == EventType.CLOSED || e.type() == EventType.WEEKEND)),
            explicit,
            legacyProjected);
    DayState actual = effectiveConfidence == EventStatus.UNKNOWN ? DayState.UNKNOWN : scheduled;
    DayState businessDay =
        businessDayConfidence == EventStatus.UNKNOWN
            ? DayState.UNKNOWN
            : scheduled == DayState.CLOSED ? DayState.CLOSED : DayState.OPEN;
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
        details,
        businessDay,
        businessDayConfidence);
  }

  /**
   * The confidence of one question's answer: UNKNOWN when any of its scopes is incomplete,
   * PROJECTED when any is projected or a relevant row is projected, CONFIRMED otherwise. Legacy
   * artifacts without explicit quality fall back to {@code verified_through}.
   */
  private static EventStatus confidence(
      Map<CompletenessScope, CoverageQuality> completeness,
      DateQuestion question,
      boolean rawProjected,
      boolean explicit,
      boolean legacyProjected) {
    if (!explicit) {
      return legacyProjected || rawProjected ? EventStatus.PROJECTED : EventStatus.CONFIRMED;
    }
    boolean incomplete = false;
    boolean projected = false;
    for (CompletenessScope scope : question.scopes()) {
      CoverageQuality quality = completeness.get(scope);
      incomplete |= quality == CoverageQuality.INCOMPLETE;
      projected |= quality == CoverageQuality.PROJECTED;
    }
    return incomplete
        ? EventStatus.UNKNOWN
        : projected || rawProjected ? EventStatus.PROJECTED : EventStatus.CONFIRMED;
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

  /**
   * Refuses an answer to the full {@link DateQuestion#SESSION} question when any explicitly
   * modelled scope is incomplete. Equivalent to {@code requireResolved(date, SESSION)}.
   */
  default void requireResolved(LocalDate date) {
    requireResolved(date, DateQuestion.SESSION);
  }

  /**
   * Refuses an answer to {@code question} when one of the scopes it depends on is incomplete on
   * this date.
   *
   * @throws OutsideCoverageException if the date lies outside {@link #range()}
   * @throws UnresolvedDateException if a relevant scope is incomplete; its {@code
   *     incompleteScopes()} names only the scopes the question needed
   */
  default void requireResolved(LocalDate date, DateQuestion question) {
    if (!range().contains(date)) {
      throw new OutsideCoverageException(calendarId(), date, range());
    }
    Set<CompletenessScope> incomplete = incompleteScopes(date, question);
    if (!incomplete.isEmpty()) {
      throw new UnresolvedDateException(calendarId(), date, range(), incomplete);
    }
  }

  /**
   * The scopes among {@code question}'s that are incomplete on {@code date}, computed from the
   * coverage intervals alone. Equivalent to {@code assessment(date).incompleteScopes(question)}
   * without materialising the assessment, which keeps day-by-day walks cheap.
   */
  default Set<CompletenessScope> incompleteScopes(LocalDate date, DateQuestion question) {
    java.util.EnumSet<CompletenessScope> incomplete =
        java.util.EnumSet.noneOf(CompletenessScope.class);
    if (!range().contains(date)) {
      incomplete.addAll(question.scopes());
      return incomplete;
    }
    List<CoverageInterval> intervals = coverageIntervals();
    if (intervals.isEmpty()) {
      return incomplete;
    }
    for (CompletenessScope scope : question.scopes()) {
      boolean covered = false;
      for (CoverageInterval interval : intervals) {
        if (interval.scope() == scope && interval.contains(date)) {
          covered = true;
          if (interval.quality() == CoverageQuality.INCOMPLETE) {
            incomplete.add(scope);
            break;
          }
        }
      }
      if (!covered) {
        incomplete.add(scope);
      }
    }
    return incomplete;
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
