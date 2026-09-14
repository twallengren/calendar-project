package com.bdc.emitter;

import com.bdc.trust.DayAssessment;
import com.bdc.trust.EventDetails;
import java.util.LinkedHashMap;
import java.util.Map;

/** The shared v2, CLI and browser trust representation; never exports private loader labels. */
public final class AssessmentEmitter {
  private AssessmentEmitter() {}

  public static Map<String, Object> row(DayAssessment day) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("date", day.date().toString());
    result.put("state", day.state().name());
    result.put("scheduled_state", day.scheduledState().name());
    result.put("effective_confidence", day.effectiveConfidence().name());
    Map<String, String> completeness = new LinkedHashMap<>();
    day.completeness().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> completeness.put(entry.getKey().name(), entry.getValue().name()));
    result.put("completeness", completeness);
    result.put("evidence_ids", day.evidenceIds());
    result.put("events", day.events().stream().map(AssessmentEmitter::event).toList());
    return result;
  }

  private static Map<String, Object> event(EventDetails detail) {
    var event = detail.event();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("date", event.date().toString());
    row.put("type", event.type().name());
    row.put("description", event.description());
    row.put("key", event.key());
    row.put("source_module", event.sourceModule());
    row.put("observed_from", event.observedFrom() == null ? null : event.observedFrom().toString());
    row.put("close_time", event.closeTime() == null ? null : event.closeTime().toString());
    row.put("raw_status", detail.rawStatus().name());
    row.put("effective_status", detail.effectiveStatus().name());
    row.put("evidence_ids", detail.evidenceIds());
    row.put(
        "observation_lineage", detail.observationLineage().stream().map(Object::toString).toList());
    var nativeDate = detail.nominalNativeDate();
    row.put("nominal_native_date", NativeDateFields.of(nativeDate));
    row.put("chronology_profile", detail.chronologyProfile());
    row.put("chronology_provider", detail.chronologyProvider());
    return row;
  }
}
