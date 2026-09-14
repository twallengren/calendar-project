package com.bdc.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A set of weekend days effective over an optional date interval.
 *
 * <p>{@code from} and {@code to} are inclusive; {@code null} means open-ended. A period with both
 * bounds {@code null} applies to every date.
 *
 * @param days the weekend days during this period (may be empty to declare "no weekend")
 * @param from first effective date (inclusive), or null for open start
 * @param to last effective date (inclusive), or null for open end
 */
public record WeekendPeriod(Set<DayOfWeek> days, LocalDate from, LocalDate to) {

  public WeekendPeriod {
    Objects.requireNonNull(days, "days must not be null");
    days = days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days);
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException(
          "weekend period 'from' (" + from + ") must not be after 'to' (" + to + ")");
    }
  }

  /** Creates an open-ended period (applies to all dates). */
  public static WeekendPeriod always(Set<DayOfWeek> days) {
    return new WeekendPeriod(days, null, null);
  }

  /** True if the period is in effect on the given date. */
  public boolean contains(LocalDate date) {
    if (from != null && date.isBefore(from)) return false;
    if (to != null && date.isAfter(to)) return false;
    return true;
  }

  /** True if both periods are in effect on at least one common date. */
  public boolean overlaps(WeekendPeriod other) {
    boolean startsBeforeOtherEnds = other.to == null || from == null || !from.isAfter(other.to);
    boolean otherStartsBeforeEnds = to == null || other.from == null || !other.from.isAfter(to);
    return startsBeforeOtherEnds && otherStartsBeforeEnds;
  }

  /** True if this period has a bounded start or end. */
  public boolean isBounded() {
    return from != null || to != null;
  }
}
