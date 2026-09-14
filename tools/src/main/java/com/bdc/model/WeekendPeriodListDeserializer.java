package com.bdc.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deserializes {@code policies.weekends}, accepting either the legacy flat form or the
 * effective-dated form:
 *
 * <pre>
 * weekends: [SATURDAY, SUNDAY]                       # one open-ended period
 *
 * weekends:
 *   - {days: [SUNDAY], to: 1952-09-27}
 *   - {days: [SATURDAY, SUNDAY], from: 1952-09-29}
 * </pre>
 *
 * Later entries take precedence over earlier ones for dates they both cover.
 */
public class WeekendPeriodListDeserializer extends JsonDeserializer<List<WeekendPeriod>> {

  private static final Set<String> ALLOWED_KEYS = Set.of("days", "from", "to");

  @Override
  public List<WeekendPeriod> deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    JsonNode node = p.getCodec().readTree(p);
    if (!node.isArray()) {
      throw new IOException("policies.weekends must be a list");
    }
    if (node.isEmpty()) {
      return List.of();
    }

    boolean allStrings = true;
    boolean allObjects = true;
    for (JsonNode item : node) {
      allStrings &= item.isTextual();
      allObjects &= item.isObject();
    }
    if (allStrings) {
      Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
      for (JsonNode item : node) {
        days.add(parseDay(item));
      }
      return List.of(WeekendPeriod.always(days));
    }
    if (!allObjects) {
      throw new IOException(
          "policies.weekends must be either a list of weekday names or a list of"
              + " {days, from, to} objects, not a mix");
    }

    List<WeekendPeriod> periods = new ArrayList<>();
    for (JsonNode item : node) {
      periods.add(parsePeriod(item));
    }
    return periods;
  }

  private WeekendPeriod parsePeriod(JsonNode item) throws IOException {
    Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
    while (fields.hasNext()) {
      String field = fields.next().getKey();
      if (!ALLOWED_KEYS.contains(field)) {
        throw new IOException(
            "Unknown field '" + field + "' in weekend period; allowed: " + ALLOWED_KEYS);
      }
    }
    JsonNode daysNode = item.get("days");
    if (daysNode == null || !daysNode.isArray()) {
      throw new IOException("weekend period requires a 'days' list");
    }
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    for (JsonNode d : daysNode) {
      days.add(parseDay(d));
    }
    LocalDate from = parseDate(item.get("from"), "from");
    LocalDate to = parseDate(item.get("to"), "to");
    try {
      return new WeekendPeriod(days, from, to);
    } catch (IllegalArgumentException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  private DayOfWeek parseDay(JsonNode node) throws IOException {
    if (!node.isTextual()) {
      throw new IOException("weekday must be a name such as SATURDAY, got: " + node);
    }
    try {
      return DayOfWeek.valueOf(node.asText().trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IOException("Unknown weekday: " + node.asText());
    }
  }

  private LocalDate parseDate(JsonNode node, String field) throws IOException {
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      return LocalDate.parse(node.asText());
    } catch (Exception e) {
      throw new IOException("weekend period '" + field + "' must be an ISO date: " + node.asText());
    }
  }
}
