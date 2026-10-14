package com.bdc.stream;

import com.bdc.finance.DayCount;
import com.bdc.model.EventStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A year fraction with the convention's day count and, for calendar-dependent conventions, the
 * confidence of every business day that was counted. Calendar-free conventions report {@code
 * CONFIRMED} and examine no dates: their value is arithmetic, not evidence.
 */
public record YearFractionResult(
    LocalDate start,
    LocalDate end,
    DayCount dayCount,
    long days,
    double yearFraction,
    EventStatus effectiveConfidence,
    List<LocalDate> examinedDates) {

  public YearFractionResult {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
    Objects.requireNonNull(dayCount, "dayCount must not be null");
    Objects.requireNonNull(effectiveConfidence, "effectiveConfidence must not be null");
    examinedDates = examinedDates == null ? List.of() : List.copyOf(examinedDates);
  }
}
