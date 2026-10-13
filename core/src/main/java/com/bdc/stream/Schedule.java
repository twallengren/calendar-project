package com.bdc.stream;

import com.bdc.model.EventStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A generated periodic schedule with the confidence of every adjusted date along it. {@code
 * effectiveConfidence} is the weakest business-day confidence among all dates examined while
 * adjusting the boundaries, so an unverified holiday anywhere in the schedule is visible here.
 */
public record Schedule(
    ScheduleDefinition definition,
    List<SchedulePeriod> periods,
    EventStatus effectiveConfidence,
    List<LocalDate> examinedDates) {

  public Schedule {
    Objects.requireNonNull(definition, "definition must not be null");
    Objects.requireNonNull(effectiveConfidence, "effectiveConfidence must not be null");
    periods = periods == null ? List.of() : List.copyOf(periods);
    examinedDates = examinedDates == null ? List.of() : List.copyOf(examinedDates);
  }

  /** The adjusted boundary dates, first start through last end. */
  public List<LocalDate> dates() {
    if (periods.isEmpty()) {
      return List.of();
    }
    List<LocalDate> dates = new java.util.ArrayList<>();
    dates.add(periods.get(0).start());
    for (SchedulePeriod period : periods) {
      dates.add(period.end());
    }
    return List.copyOf(dates);
  }

  /** The unadjusted boundary dates, first start through last end. */
  public List<LocalDate> unadjustedDates() {
    if (periods.isEmpty()) {
      return List.of();
    }
    List<LocalDate> dates = new java.util.ArrayList<>();
    dates.add(periods.get(0).unadjustedStart());
    for (SchedulePeriod period : periods) {
      dates.add(period.unadjustedEnd());
    }
    return List.copyOf(dates);
  }
}
