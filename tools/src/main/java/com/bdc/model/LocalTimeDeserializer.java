package com.bdc.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Deserializes a local time written as {@code "HH:mm"} or {@code "HH:mm:ss"}. YAML treats an
 * unquoted {@code 13:00} as a sexagesimal integer, so the value must be quoted; an integer here is
 * rejected with a hint.
 */
public class LocalTimeDeserializer extends JsonDeserializer<LocalTime> {

  @Override
  public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.getCodec().readTree(p);
    if (node.isNull()) {
      return null;
    }
    if (node.isNumber()) {
      throw new IOException(
          "time value "
              + node.asText()
              + " was parsed as a number; quote it in YAML, e.g. close_time: \"13:00\"");
    }
    String text = node.asText().trim();
    try {
      return LocalTime.parse(text);
    } catch (DateTimeParseException e) {
      throw new IOException("Invalid time '" + text + "': expected HH:mm or HH:mm:ss", e);
    }
  }
}
