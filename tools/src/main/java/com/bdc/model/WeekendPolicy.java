package com.bdc.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The resolved weekend definition of a calendar.
 *
 * <p>A policy is a list of {@link WeekendPeriod}s. For a given date the <em>last</em> period in the
 * list that covers the date decides which weekdays are weekend days; a date covered by no period
 * has no weekend days. {@link #weekendDays()} is the union of all periods' days and is kept for
 * callers that only need a summary (emitters, legacy tests).
 *
 * @param weekendDays union of weekend days across all periods
 * @param periods the effective-dated periods, in precedence order (last wins)
 */
public record WeekendPolicy(Set<DayOfWeek> weekendDays, List<WeekendPeriod> periods) {

  public static final WeekendPolicy SAT_SUN =
      new WeekendPolicy(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));

  public static final WeekendPolicy NONE = new WeekendPolicy(EnumSet.noneOf(DayOfWeek.class));

  public WeekendPolicy {
    if (periods == null || periods.isEmpty()) {
      Set<DayOfWeek> days =
          weekendDays == null || weekendDays.isEmpty()
              ? EnumSet.noneOf(DayOfWeek.class)
              : EnumSet.copyOf(weekendDays);
      periods = List.of(WeekendPeriod.always(days));
    } else {
      periods = List.copyOf(periods);
    }
    Set<DayOfWeek> union = EnumSet.noneOf(DayOfWeek.class);
    for (WeekendPeriod period : periods) {
      union.addAll(period.days());
    }
    weekendDays = union;
  }

  /** Creates a policy with a single open-ended period. */
  public WeekendPolicy(Set<DayOfWeek> days) {
    this(days, null);
  }

  /** Creates a policy with a single open-ended period. */
  public WeekendPolicy(List<DayOfWeek> days) {
    this(days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days), null);
  }

  /** Creates a policy from effective-dated periods (last period wins for overlapping dates). */
  public static WeekendPolicy ofPeriods(Collection<WeekendPeriod> periods) {
    return new WeekendPolicy(null, List.copyOf(periods));
  }

  /** Returns the weekend days in effect on the given date. */
  public Set<DayOfWeek> daysOn(LocalDate date) {
    Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
    for (int i = periods.size() - 1; i >= 0; i--) {
      WeekendPeriod period = periods.get(i);
      if (period.contains(date)) {
        result.addAll(period.days());
        return result;
      }
    }
    return result;
  }

  /** True if the given date is a weekend day under the period in effect on that date. */
  public boolean isWeekend(LocalDate date) {
    return daysOn(date).contains(date.getDayOfWeek());
  }

  /**
   * True if the weekday is a weekend day in any period. Prefer {@link #isWeekend(LocalDate)} for
   * date-specific decisions.
   */
  public boolean isWeekend(DayOfWeek day) {
    return weekendDays.contains(day);
  }

  /** True if the weekend definition varies over time. */
  public boolean isEffectiveDated() {
    return periods.size() > 1 || periods.get(0).isBounded();
  }
}
