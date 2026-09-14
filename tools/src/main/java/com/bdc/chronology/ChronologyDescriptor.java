package com.bdc.chronology;

import java.time.LocalDate;

/** Inclusive supported civil interval and reproducible calculation profile. */
public record ChronologyDescriptor(
    String id,
    String profile,
    String provider,
    LocalDate supportedFrom,
    LocalDate supportedTo,
    int maximumYearDays) {
  public void requireSupported(LocalDate date) {
    if (date.isBefore(supportedFrom) || date.isAfter(supportedTo)) {
      throw new UnsupportedChronologyRangeException(
          id + " supports " + supportedFrom + ".." + supportedTo + ", requested " + date);
    }
  }
}
