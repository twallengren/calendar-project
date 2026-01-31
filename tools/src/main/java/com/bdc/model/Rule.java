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
  @JsonSubTypes.Type(value = Rule.RelativeToReference.class, name = "relative_to_reference")
})
public sealed interface Rule
    permits Rule.ExplicitDates,
        Rule.FixedMonthDay,
        Rule.NthWeekdayOfMonth,
        Rule.RelativeToReference {

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
}
