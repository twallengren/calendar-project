package com.bdc.stream;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One period of a {@link Schedule}: its unadjusted boundaries as the roll produced them and the
 * boundaries after the business-day convention, plus whether it is the irregular stub.
 */
public record SchedulePeriod(
    LocalDate unadjustedStart,
    LocalDate unadjustedEnd,
    LocalDate start,
    LocalDate end,
    boolean stub) {

  public SchedulePeriod {
    Objects.requireNonNull(unadjustedStart, "unadjustedStart must not be null");
    Objects.requireNonNull(unadjustedEnd, "unadjustedEnd must not be null");
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
  }
}
