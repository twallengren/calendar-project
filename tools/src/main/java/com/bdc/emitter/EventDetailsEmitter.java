package com.bdc.emitter;

import com.bdc.generator.CompiledEvent;
import com.bdc.model.EventType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wire representation of each complete non-weekend occurrence and its compiler provenance. */
public final class EventDetailsEmitter {
  private EventDetailsEmitter() {}

  public static List<Map<String, Object>> rows(List<CompiledEvent> details) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (CompiledEvent detail : details) {
      var event = detail.event();
      if (event.type() == EventType.WEEKEND) continue;
      var provenance = detail.provenance();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("date", event.date().toString());
      row.put("type", event.type().name());
      row.put("description", event.description());
      row.put("key", event.key());
      row.put("source_module", event.sourceModule());
      row.put(
          "observed_from", event.observedFrom() == null ? null : event.observedFrom().toString());
      row.put("close_time", event.closeTime() == null ? null : event.closeTime().toString());
      row.put("status", event.status().name());
      row.put("evidence_ids", provenance.evidenceIds());
      row.put(
          "observation_lineage",
          provenance.observationLineage().stream().map(Object::toString).toList());
      var nativeDate = provenance.nominalNativeDate();
      if (nativeDate != null) {
        row.put("nominal_native_date", NativeDateFields.of(nativeDate));
        row.put("chronology_profile", provenance.chronologyProfile());
        row.put("chronology_provider", provenance.chronologyProvider());
      }
      rows.add(row);
    }
    return List.copyOf(rows);
  }
}
