package com.bdc.trust;

import com.bdc.chronology.NativeDate;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * An event together with trust and provenance that do not alter the stable {@link Event} record.
 */
public record EventDetails(
    Event event,
    EventStatus rawStatus,
    EventStatus effectiveStatus,
    List<String> evidenceIds,
    NativeDate nominalNativeDate,
    String chronologyProfile,
    String chronologyProvider,
    List<LocalDate> observationLineage) {

  public EventDetails {
    if (event == null || rawStatus == null || effectiveStatus == null) {
      throw new IllegalArgumentException("event and statuses must not be null");
    }
    evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    observationLineage = observationLineage == null ? List.of() : List.copyOf(observationLineage);
  }
}
