package com.bdc.emitter;

import com.bdc.model.CalendarSpec;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes events as a single JSON document with calendar context, for consumers that prefer a typed
 * contract over CSV.
 */
public class JsonEventsEmitter {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  public void emit(
      ResolvedSpec spec, List<Event> events, LocalDate from, LocalDate to, Path outputPath)
      throws IOException {
    Path parent = outputPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    mapper.writeValue(outputPath.toFile(), toDocument(spec, events, from, to));
  }

  public String emitToString(ResolvedSpec spec, List<Event> events, LocalDate from, LocalDate to)
      throws IOException {
    return mapper.writeValueAsString(toDocument(spec, events, from, to));
  }

  Map<String, Object> toDocument(
      ResolvedSpec spec, List<Event> events, LocalDate from, LocalDate to) {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("calendar_id", spec.id());
    doc.put("calendar_name", spec.metadata() != null ? spec.metadata().name() : spec.id());
    doc.put("timezone", spec.timezone());
    Map<String, String> range = new LinkedHashMap<>();
    range.put("from", from.toString());
    range.put("to", to.toString());
    doc.put("range", range);
    CalendarSpec.Coverage coverage = spec.coverage();
    if (coverage != null) {
      Map<String, Object> cov = new LinkedHashMap<>();
      cov.put("from", str(coverage.from()));
      cov.put("to", str(coverage.to()));
      cov.put("verified_through", str(coverage.verifiedThrough()));
      if (!coverage.quality().isEmpty()) {
        cov.put("quality", CoverageSerialization.toMaps(coverage.quality()));
      }
      doc.put("coverage", cov);
    }
    List<Map<String, Object>> rows = new ArrayList<>(events.size());
    for (Event event : events) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("date", event.date().toString());
      row.put("type", event.type().name());
      row.put("description", event.description());
      row.put("key", event.key());
      row.put("source_module", event.sourceModule());
      row.put("observed_from", str(event.observedFrom()));
      row.put("close_time", event.closeTime() != null ? TIME.format(event.closeTime()) : null);
      row.put("status", event.status() != null ? event.status().name() : null);
      rows.add(row);
    }
    doc.put("event_count", rows.size());
    doc.put("events", rows);
    return doc;
  }

  private static String str(LocalDate d) {
    return d != null ? d.toString() : null;
  }
}
