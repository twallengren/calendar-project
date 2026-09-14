package com.bdc.chronology;

import java.util.Locale;
import java.util.Objects;

/** Exact civil date with stable month identity, including intercalary months. */
public record NativeDate(String chronologyId, int year,
    String monthCode, int day) {
  public NativeDate {
    chronologyId = Objects.requireNonNull(chronologyId, "chronology_id").toUpperCase(Locale.ROOT);
    monthCode = Objects.requireNonNull(monthCode, "month_code").toUpperCase(Locale.ROOT);
    if (monthCode.isBlank() || day < 1 || day > 31) throw new IllegalArgumentException("Invalid native date");
  }
}
