package com.bdc.model;

/**
 * How an event whose nominal date is unavailable is observed.
 *
 * <p>Set at the calendar level as the default for shiftable CLOSED event sources, and optionally
 * overridden per event source via {@code shift_policy}.
 *
 * <p>Most values answer "a CLOSED holiday fell on a weekend day, where is it observed?". {@link
 * #DROP} and {@link #PREVIOUS_AVAILABLE_BUSINESS_DAY} answer the EARLY_CLOSE question instead: "the
 * nominal date of a half day is not a session (it is a weekend day, or a full closure took it),
 * what happens to the half day?". {@code DROP} is the default for every EARLY_CLOSE source.
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
  NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY,

  /**
   * EARLY_CLOSE only: the occurrence is simply not observed when its nominal date is not a session
   * (a weekend day, or a date a CLOSED event has taken). This is the default for every EARLY_CLOSE
   * event source and the behaviour every early close had before the policy became explicit. It is a
   * validation error on a CLOSED source.
   */
  DROP,

  /**
   * The occurrence moves back to the nearest earlier date that is neither a weekend day nor already
   * a CLOSED event, searching at most seven days; if none is found it is not observed. Used for
   * EARLY_CLOSE half days that an exchange brings forward rather than cancels: the London Stock
   * Exchange observes the 24/31 December 12:30 half day on the preceding business day when the
   * nominal date falls on a weekend.
   */
  PREVIOUS_AVAILABLE_BUSINESS_DAY
}
