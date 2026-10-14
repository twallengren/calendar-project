package com.bdc.stream;

import com.bdc.model.EventStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A financial date result together with every date that established its confidence. {@code tenor}
 * is the canonical tenor spelling for {@link DateOperation#ADD_TENOR} and null otherwise.
 */
public record DateOperationResult(
    LocalDate originalDate,
    LocalDate resultDate,
    DateOperation operation,
    BusinessDayConvention convention,
    Integer businessDayOffset,
    Integer monthOffset,
    boolean preserveEndOfMonth,
    EventStatus effectiveConfidence,
    List<LocalDate> examinedDates,
    String tenor) {

  /** The pre-tenor form; {@code tenor} is null. */
  public DateOperationResult(
      LocalDate originalDate,
      LocalDate resultDate,
      DateOperation operation,
      BusinessDayConvention convention,
      Integer businessDayOffset,
      Integer monthOffset,
      boolean preserveEndOfMonth,
      EventStatus effectiveConfidence,
      List<LocalDate> examinedDates) {
    this(
        originalDate,
        resultDate,
        operation,
        convention,
        businessDayOffset,
        monthOffset,
        preserveEndOfMonth,
        effectiveConfidence,
        examinedDates,
        null);
  }

  public DateOperationResult {
    Objects.requireNonNull(originalDate, "originalDate must not be null");
    Objects.requireNonNull(resultDate, "resultDate must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(effectiveConfidence, "effectiveConfidence must not be null");
    examinedDates = examinedDates == null ? List.of() : List.copyOf(examinedDates);
  }
}
