package com.bdc.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Custom deserializer for AnnotatedDate that supports both plain dates and annotated dates.
 *
 * <p>Plain date format: "2024-01-01"
 *
 * <p>Annotated date format: {date: "2024-01-01", comment: "Description"}
 */
public class AnnotatedDateDeserializer extends JsonDeserializer<Rule.AnnotatedDate> {

  @Override
  public Rule.AnnotatedDate deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    JsonNode node = p.getCodec().readTree(p);

    if (node.isTextual()) {
      // Plain date string: "2024-01-01"
      LocalDate date = LocalDate.parse(node.asText());
      return new Rule.AnnotatedDate(date, null);
    } else if (node.isObject()) {
      // Annotated date object: {date: "2024-01-01", comment: "Description"}
      JsonNode dateNode = node.get("date");
      JsonNode commentNode = node.get("comment");

      if (dateNode == null) {
        throw new IOException("Annotated date object must have a 'date' field");
      }

      LocalDate date = LocalDate.parse(dateNode.asText());
      String comment = commentNode != null ? commentNode.asText() : null;
      return new Rule.AnnotatedDate(date, comment);
    } else {
      throw new IOException(
          "Expected date string or annotated date object, got: " + node.getNodeType());
    }
  }
}
