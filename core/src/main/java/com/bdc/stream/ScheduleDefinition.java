package com.bdc.stream;

import com.bdc.finance.Frequency;
import com.bdc.finance.RollConvention;
import com.bdc.finance.StubConvention;
import com.bdc.finance.Tenor;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What a periodic schedule should look like before a calendar adjusts it: the unadjusted start and
 * end, the period frequency, where any stub goes, how month-based periods pick their day, and the
 * business-day convention applied to every boundary date.
 */
public record ScheduleDefinition(
    LocalDate start,
    LocalDate end,
    Frequency frequency,
    StubConvention stub,
    RollConvention roll,
    BusinessDayConvention convention) {

  public ScheduleDefinition {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
    Objects.requireNonNull(frequency, "frequency must not be null");
    Objects.requireNonNull(stub, "stub must not be null");
    Objects.requireNonNull(roll, "roll must not be null");
    Objects.requireNonNull(convention, "convention must not be null");
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("schedule start " + start + " must precede end " + end);
    }
    if (!frequency.tenor().isMonthBased() && roll != RollConvention.DAY_OF_MONTH) {
      throw new IllegalArgumentException(
          roll + " applies to month-based frequencies only, not " + frequency);
    }
  }

  /** A regular schedule with no stub, day-of-month rolling and {@code MODIFIED_FOLLOWING}. */
  public static ScheduleDefinition of(LocalDate start, LocalDate end, Frequency frequency) {
    return new ScheduleDefinition(
        start,
        end,
        frequency,
        StubConvention.SHORT_FINAL,
        RollConvention.DAY_OF_MONTH,
        BusinessDayConvention.MODIFIED_FOLLOWING);
  }

  public ScheduleDefinition withStub(StubConvention stub) {
    return new ScheduleDefinition(start, end, frequency, stub, roll, convention);
  }

  public ScheduleDefinition withRoll(RollConvention roll) {
    return new ScheduleDefinition(start, end, frequency, stub, roll, convention);
  }

  public ScheduleDefinition withConvention(BusinessDayConvention convention) {
    return new ScheduleDefinition(start, end, frequency, stub, roll, convention);
  }

  /** The period tenor, a convenience for {@code frequency().tenor()}. */
  public Tenor tenor() {
    return frequency.tenor();
  }
}
