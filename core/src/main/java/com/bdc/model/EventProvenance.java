package com.bdc.model;

import com.bdc.chronology.NativeDate;
import java.time.LocalDate;
import java.util.List;

/** Additive published provenance, kept separate from the stable Event constructor contract. */
public record EventProvenance(
    NativeDate nominalNativeDate,
    String chronologyProfile,
    String chronologyProvider,
    List<String> evidenceIds,
    List<LocalDate> observationLineage) {
  public EventProvenance {
    evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    observationLineage = observationLineage == null ? List.of() : List.copyOf(observationLineage);
  }
}
