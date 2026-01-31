package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Rule.ExplicitDates.class, name = "explicit_dates"),
  @JsonSubTypes.Type(value = Rule.FixedMonthDay.class, name = "fixed_month_day"),
  @JsonSubTypes.Type(value = Rule.NthWeekdayOfMonth.class, name = "nth_weekday_of_month"),
  @JsonSubTypes.Type(value = Rule.RelativeToReference.class, name = "relative_to_reference"),
  @JsonSubTypes.Type(value = Rule.ObservedHoliday.class, name = "observed_holiday")
})
public sealed interface Rule
    permits Rule.ExplicitDates,
        Rule.FixedMonthDay,
        Rule.NthWeekdayOfMonth,
        Rule.RelativeToReference,
        Rule.ObservedHoliday {

  record ExplicitDates(String key, String name, List<LocalDate> dates) implements Rule {}

  record FixedMonthDay(String key, String name, int month, int day, String chronology)
      implements Rule {
    public FixedMonthDay {
      if (chronology == null) chronology = "ISO";
    }
  }

  record NthWeekdayOfMonth(String key, String name, int month, DayOfWeek weekday, int nth)
      implements Rule {}

  record RelativeToReference(String key, String name, String reference, int offsetDays)
      implements Rule {}

  enum ShiftPolicy {
    FRIDAY,
    MONDAY,
    NONE
  }

  record ObservedHoliday(
      String key,
      String name,
      Rule baseRule,
      ShiftPolicy saturdayShift,
      ShiftPolicy sundayShift)
      implements Rule {
    public ObservedHoliday {
      if (saturdayShift == null) saturdayShift = ShiftPolicy.FRIDAY;
      if (sundayShift == null) sundayShift = ShiftPolicy.MONDAY;
    }
  }
}
