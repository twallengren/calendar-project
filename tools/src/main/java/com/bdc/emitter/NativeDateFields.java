package com.bdc.emitter;

import com.bdc.chronology.NativeDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable native-date field order for reproducible YAML and JSON artifacts. */
final class NativeDateFields {
  private NativeDateFields() {}

  static Map<String, Object> of(NativeDate date) {
    if (date == null) return null;
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("chronology_id", date.chronologyId());
    fields.put("year", date.year());
    fields.put("month_code", date.monthCode());
    fields.put("day", date.day());
    return fields;
  }
}
