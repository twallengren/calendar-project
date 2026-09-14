package com.bdc.trust;

import java.time.LocalDate;
import java.util.List;

/** An inclusive, evidence-backed quality interval for one part of a calendar. */
public record CoverageInterval(
    CompletenessScope scope,
    LocalDate from,
    LocalDate to,
    CoverageQuality quality,
    List<String> evidenceIds) {

  public CoverageInterval {
    if (scope == null || from == null || to == null || quality == null) {
      throw new IllegalArgumentException("coverage interval fields must not be null");
    }
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("coverage interval from must not be after to");
    }
    evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    if (quality == CoverageQuality.VERIFIED && evidenceIds.isEmpty()) {
      throw new IllegalArgumentException("VERIFIED coverage needs at least one evidence id");
    }
  }

  public boolean contains(LocalDate date) {
    return !date.isBefore(from) && !date.isAfter(to);
  }
}
