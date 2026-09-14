package com.bdc.trust;

import com.bdc.chronology.NativeDate;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Dependency-free decoder for the additive metadata event_details array. */
public final class PublishedEventDetails {
  private PublishedEventDetails() {}

  public static List<EventDetails> read(Object value) {
    if (value == null) return null; // Legacy artifacts did not retain this information.
    if (!(value instanceof List<?> rows))
      throw new IllegalArgumentException("event_details must be an array");
    List<EventDetails> result = new ArrayList<>();
    for (Object item : rows) {
      if (!(item instanceof Map<?, ?> row))
        throw new IllegalArgumentException("event_details row must be an object");
      Event event =
          new Event(
              LocalDate.parse(text(row, "date")),
              EventType.valueOf(text(row, "type")),
              text(row, "description"),
              "metadata",
              text(row, "key"),
              text(row, "source_module"),
              date(row, "observed_from"),
              text(row, "close_time") == null ? null : LocalTime.parse(text(row, "close_time")),
              EventStatus.valueOf(text(row, "status")));
      NativeDate nativeDate = null;
      if (row.get("nominal_native_date") instanceof Map<?, ?> nativeRow) {
        nativeDate =
            new NativeDate(
                text(nativeRow, "chronology_id"),
                ((Number) nativeRow.get("year")).intValue(),
                text(nativeRow, "month_code"),
                ((Number) nativeRow.get("day")).intValue());
      }
      List<String> evidence = strings(row.get("evidence_ids"));
      List<LocalDate> lineage =
          strings(row.get("observation_lineage")).stream().map(LocalDate::parse).toList();
      result.add(
          new EventDetails(
              event,
              event.status(),
              event.status(),
              evidence,
              nativeDate,
              text(row, "chronology_profile"),
              text(row, "chronology_provider"),
              lineage));
    }
    return List.copyOf(result);
  }

  private static String text(Map<?, ?> row, String key) {
    Object value = row.get(key);
    return value == null ? null : value.toString();
  }

  private static LocalDate date(Map<?, ?> row, String key) {
    String value = text(row, key);
    return value == null ? null : LocalDate.parse(value);
  }

  private static List<String> strings(Object value) {
    if (value == null) return List.of();
    if (!(value instanceof List<?> values))
      throw new IllegalArgumentException("Provenance list must be an array");
    return values.stream().map(Object::toString).toList();
  }

  /** Equality of complete published fields, excluding a loader's private provenance label. */
  public static Event identity(Event event) {
    return new Event(
        event.date(),
        event.type(),
        event.description(),
        "published",
        event.key(),
        event.sourceModule(),
        event.observedFrom(),
        event.closeTime(),
        event.status());
  }
}
