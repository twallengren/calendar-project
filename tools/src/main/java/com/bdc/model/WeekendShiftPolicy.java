package com.bdc.model;

/**
 * How a holiday that falls on a weekend day is observed.
 *
 * <p>Set at the calendar level as the default for shiftable event sources, and optionally
 * overridden per event source via {@code shift_policy}.
 */
public enum WeekendShiftPolicy {
  /** No shifting - weekend holidays stay on weekends. */
  NONE,

  /**
   * US-style: the first day of a two-day weekend shifts backward, the last day shifts forward
   * (Saturday to Friday, Sunday to Monday for a Sat-Sun weekend). Does not cascade.
   */
  NEAREST_WEEKDAY,

  /**
   * UK-style: weekend holidays shift to the next weekday that is not already a closure (cascading,
   * e.g. Christmas Saturday to Monday and Boxing Day Sunday to Tuesday).
   */
  NEXT_AVAILABLE_WEEKDAY,

  /**
   * Shift forward only when the holiday falls on the last day of the weekend block (Sunday to
   * Monday for a Sat-Sun weekend); on any other weekend day the holiday is not observed. This is
   * the NYSE rule for New Year's Day: a Saturday January 1 is not observed on Friday December 31.
   */
  FORWARD_ONLY,

  /**
   * Japanese-style substitute holiday (振替休日): shifts only when the holiday falls on the last day of
   * the weekend block (Sunday for a Sat-Sun weekend), to the next weekday that is not already a
   * closure. On any other weekend day the holiday is not observed - a Saturday holiday does not
   * move to the preceding Friday and is not made up on the following Monday.
   *
   * <p>This is {@link #FORWARD_ONLY}'s "last weekend day only" test combined with {@link
   * #NEXT_AVAILABLE_WEEKDAY}'s cascade: Japan's Act on National Holidays observes a Sunday holiday
   * on "the closest following day that is not a national holiday", so Sunday May 3 2026 is observed
   * on Wednesday May 6, past the May 4 and May 5 holidays.
   */
  NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY
}
