package com.bdc.finance;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** How a month-based schedule picks the day within each rolled month. */
public enum RollConvention {
  /** Keep the anchor date's day of month, clipped to the destination month's length. */
  DAY_OF_MONTH,
  /** Roll to the last day of each month. */
  EOM,
  /** Roll to the third Wednesday of each month (IMM dates). */
  IMM;

  /**
   * The unadjusted date {@code months} months from {@code anchor} under this convention. Day
   * clipping is always computed from the anchor, never from a previously rolled date, so a 31st
   * anchor returns to the 31st in long months.
   */
  public LocalDate roll(LocalDate anchor, int months) {
    LocalDate shifted = anchor.plusMonths(months);
    return switch (this) {
      case DAY_OF_MONTH -> shifted;
      case EOM -> shifted.with(TemporalAdjusters.lastDayOfMonth());
      case IMM -> ImmDates.inMonth(shifted.getYear(), shifted.getMonthValue());
    };
  }
}
