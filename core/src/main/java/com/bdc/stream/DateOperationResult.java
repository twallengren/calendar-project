package com.bdc.stream;

import com.bdc.model.EventStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** A financial date result together with every date that established its confidence. */
public record DateOperationResult(
    LocalDate originalDate,
    LocalDate resultDate,
    DateOperation operation,
    BusinessDayConvention convention,
    Integer businessDayOffset,
    Integer monthOffset,
    boolean preserveEndOfMonth,
    EventStatus effectiveConfidence,
    List<LocalDate> examinedDates) {

  public DateOperationResult {
    Objects.requireNonNull(originalDate, "originalDate must not be null");
    Objects.requireNonNull(resultDate, "resultDate must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(effectiveConfidence, "effectiveConfidence must not be null");
    examinedDates = examinedDates == null ? List.of() : List.copyOf(examinedDates);
  }
}
