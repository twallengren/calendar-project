package com.bdc.trust;

import com.bdc.model.EventStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A non-throwing explanation of the known, scheduled and evidenced state of one date.
 *
 * <p>Two views are reported side by side. {@code state} and {@code effectiveConfidence} answer the
 * full {@link DateQuestion#SESSION} question and are {@code UNKNOWN} as soon as <em>any</em> scope
 * is incomplete. {@code businessDayState} and {@code businessDayConfidence} answer only {@link
 * DateQuestion#BUSINESS_DAY}: they ignore early-close evidence, because an early close is still a
 * business day, so a calendar whose only gap is its shortened sessions still answers "open" or
 * "closed" with its real confidence. {@code businessDayState} is never {@code EARLY_CLOSE}.
 */
public record DayAssessment(
    LocalDate date,
    DayState state,
    DayState scheduledState,
    EventStatus effectiveConfidence,
    Map<CompletenessScope, CoverageQuality> completeness,
    List<String> evidenceIds,
    List<EventDetails> events,
    DayState businessDayState,
    EventStatus businessDayConfidence) {

  public DayAssessment {
    if (date == null
        || state == null
        || scheduledState == null
        || effectiveConfidence == null
        || businessDayState == null
        || businessDayConfidence == null) {
      throw new IllegalArgumentException("assessment date, states and confidence must not be null");
    }
    if (businessDayState == DayState.EARLY_CLOSE) {
      throw new IllegalArgumentException("business-day state is OPEN, CLOSED or UNKNOWN");
    }
    completeness = completeness == null ? Map.of() : Map.copyOf(completeness);
    evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    events = events == null ? List.of() : List.copyOf(events);
  }

  /** Whether the business-day scopes are resolved, i.e. {@link #businessDayState} is known. */
  public boolean isBusinessDayKnown() {
    return businessDayState != DayState.UNKNOWN;
  }

  /** The scopes among {@code question}'s that are incomplete on this date. */
  public java.util.Set<CompletenessScope> incompleteScopes(DateQuestion question) {
    java.util.EnumSet<CompletenessScope> incomplete =
        java.util.EnumSet.noneOf(CompletenessScope.class);
    for (CompletenessScope scope : question.scopes()) {
      if (completeness.getOrDefault(scope, CoverageQuality.INCOMPLETE)
          == CoverageQuality.INCOMPLETE) {
        incomplete.add(scope);
      }
    }
    return incomplete;
  }
}
