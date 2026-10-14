package com.bdc.trust;

import java.util.EnumSet;
import java.util.Set;

/**
 * The kind of question being asked about a date, and therefore which {@link CompletenessScope}s
 * must be resolved before it can be answered.
 *
 * <p>Whether a market trades on a date depends only on its scheduled closures and on unscheduled
 * exceptions; an early close is still a business day, so incomplete early-close evidence cannot
 * change a business-day answer. Session detail (close times) additionally needs early-close
 * evidence.
 */
public enum DateQuestion {
  /** "Is this a business day?" and everything derived from it: navigation, counts, offsets. */
  BUSINESS_DAY(
      EnumSet.of(CompletenessScope.SCHEDULED_CLOSURES, CompletenessScope.UNSCHEDULED_EXCEPTIONS)),
  /** "What exactly happens on this date?": the business-day scopes plus early closes. */
  SESSION(EnumSet.allOf(CompletenessScope.class));

  private final Set<CompletenessScope> scopes;

  DateQuestion(Set<CompletenessScope> scopes) {
    this.scopes = Set.copyOf(scopes);
  }

  /** The scopes that must be complete before this question has a trustworthy answer. */
  public Set<CompletenessScope> scopes() {
    return scopes;
  }
}
