package com.bdc.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A calendar's weekend definition, possibly varying over time.
 *
 * <p>WEEKEND rows are ~85% of a published artifact and carry no information this policy does not
 * already hold, so the bundled data drops them and they are rebuilt here. For a given date the
 * <em>last</em> period covering it decides which weekdays are weekend days, and a date covered by
 * no period has none — the same rule the toolchain's resolver applies, and the same one {@code
 * python/bdc_calendars/_weekend.py} mirrors.
 */
final class WeekendPolicy {

  /** One effective-dated slice of a weekend policy. */
  private record Period(Set<DayOfWeek> days, LocalDate from, LocalDate to) {

    boolean contains(LocalDate date) {
      return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }
  }

  private final List<Period> periods;

  private WeekendPolicy(List<Period> periods) {
    this.periods = periods;
  }

  /** Builds a policy from the {@code weekend_policy} block of a bundled {@code metadata.json}. */
  static WeekendPolicy fromJson(Object payload) {
    if (!(payload instanceof Map<?, ?> map)) {
      return new WeekendPolicy(List.of());
    }
    List<Period> periods = new ArrayList<>();
    for (Object entry : asList(map.get("periods"))) {
      Map<?, ?> period = (Map<?, ?>) entry;
      periods.add(
          new Period(days(period.get("days")), date(period.get("from")), date(period.get("to"))));
    }
    if (periods.isEmpty()) {
      Set<DayOfWeek> days = days(map.get("days"));
      if (!days.isEmpty()) {
        periods.add(new Period(days, null, null));
      }
    }
    return new WeekendPolicy(List.copyOf(periods));
  }

  /** The weekend weekdays in effect on a date: the last matching period wins. */
  Set<DayOfWeek> daysOn(LocalDate date) {
    for (int i = periods.size() - 1; i >= 0; i--) {
      if (periods.get(i).contains(date)) {
        return periods.get(i).days();
      }
    }
    return Set.of();
  }

  boolean isWeekend(LocalDate date) {
    return daysOn(date).contains(date.getDayOfWeek());
  }

  private static Set<DayOfWeek> days(Object value) {
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    for (Object day : asList(value)) {
      days.add(DayOfWeek.valueOf(String.valueOf(day).strip().toUpperCase()));
    }
    return days;
  }

  private static List<?> asList(Object value) {
    return value instanceof List<?> list ? list : List.of();
  }

  private static LocalDate date(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).strip();
    return text.isEmpty() ? null : LocalDate.parse(text);
  }
}
