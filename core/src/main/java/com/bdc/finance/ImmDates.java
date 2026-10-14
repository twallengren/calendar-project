package com.bdc.finance;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * IMM dates: the third Wednesday of a month. The quarterly IMM cycle is March, June, September and
 * December; {@link #isQuarterly} and {@link #nextQuarterly} restrict to it.
 */
public final class ImmDates {
  private ImmDates() {}

  /** The third Wednesday of the given month. */
  public static LocalDate inMonth(int year, int month) {
    return LocalDate.of(year, month, 1)
        .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY));
  }

  /** Whether the date is the third Wednesday of its month. */
  public static boolean isImm(LocalDate date) {
    return date.equals(inMonth(date.getYear(), date.getMonthValue()));
  }

  /** Whether the date is an IMM date in March, June, September or December. */
  public static boolean isQuarterly(LocalDate date) {
    return isImm(date) && date.getMonthValue() % 3 == 0;
  }

  /** The first IMM date strictly after {@code date}, in any month. */
  public static LocalDate next(LocalDate date) {
    LocalDate candidate = inMonth(date.getYear(), date.getMonthValue());
    if (!candidate.isAfter(date)) {
      LocalDate following = date.plusMonths(1);
      candidate = inMonth(following.getYear(), following.getMonthValue());
    }
    return candidate;
  }

  /** The first quarterly IMM date (Mar/Jun/Sep/Dec) strictly after {@code date}. */
  public static LocalDate nextQuarterly(LocalDate date) {
    LocalDate candidate = next(date);
    while (candidate.getMonthValue() % 3 != 0) {
      candidate = next(candidate);
    }
    return candidate;
  }
}
