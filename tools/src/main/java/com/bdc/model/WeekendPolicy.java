package com.bdc.model;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record WeekendPolicy(Set<DayOfWeek> weekendDays) {

  public static final WeekendPolicy SAT_SUN =
      new WeekendPolicy(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));

  public static final WeekendPolicy NONE = new WeekendPolicy(EnumSet.noneOf(DayOfWeek.class));

  public WeekendPolicy(List<DayOfWeek> days) {
    this(days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days));
  }

  public boolean isWeekend(DayOfWeek day) {
    return weekendDays.contains(day);
  }
}
