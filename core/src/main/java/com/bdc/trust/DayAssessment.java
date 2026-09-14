package com.bdc.trust;

import com.bdc.model.EventStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** A non-throwing explanation of the known, scheduled and evidenced state of one date. */
public record DayAssessment(
    LocalDate date,
    DayState state,
    DayState scheduledState,
    EventStatus effectiveConfidence,
    Map<CompletenessScope, CoverageQuality> completeness,
    List<String> evidenceIds,
    List<EventDetails> events) {

  public DayAssessment {
    if (date == null || state == null || scheduledState == null || effectiveConfidence == null) {
      throw new IllegalArgumentException("assessment date, states and confidence must not be null");
    }
    completeness = completeness == null ? Map.of() : Map.copyOf(completeness);
    evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    events = events == null ? List.of() : List.copyOf(events);
  }
}
