package com.bdc.chronology;

import java.time.DateTimeException;
import java.time.LocalDate;

/** Checked arithmetic for calculation dependencies, which must never be silently truncated. */
public final class DateArithmetic {
  private DateArithmetic() {}

  public static LocalDate plusDays(LocalDate date, long days, String context) {
    try {
      return date.plusDays(days);
    } catch (DateTimeException | ArithmeticException e) {
      throw new IllegalArgumentException(
          context
              + ": required date outside representable range ("
              + date
              + " + "
              + days
              + " days)",
          e);
    }
  }
}
