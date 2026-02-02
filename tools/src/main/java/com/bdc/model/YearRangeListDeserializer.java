package com.bdc.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom deserializer for List of YearRange that supports flexible year notation.
 *
 * <p>Supports the following formats:
 *
 * <pre>
 * active_years:
 *   - [null, 1968]    # from inception through 1968
 *   - 1972            # single year
 *   - [1990, 2000]    # range from 1990 to 2000
 * </pre>
 */
public class YearRangeListDeserializer extends JsonDeserializer<List<EventSource.YearRange>> {

  @Override
  public List<EventSource.YearRange> deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    JsonNode node = p.getCodec().readTree(p);

    if (!node.isArray()) {
      throw new IOException("active_years must be an array");
    }

    List<EventSource.YearRange> ranges = new ArrayList<>();

    for (JsonNode element : node) {
      EventSource.YearRange range = parseYearRange(element);
      ranges.add(range);
    }

    return ranges;
  }

  private EventSource.YearRange parseYearRange(JsonNode node) throws IOException {
    if (node.isInt() || node.isNumber()) {
      // Single year: 1972
      int year = node.asInt();
      return new EventSource.YearRange(year, year);
    } else if (node.isArray()) {
      // Range: [start, end] where either can be null
      if (node.size() != 2) {
        throw new IOException("Year range array must have exactly 2 elements, got: " + node.size());
      }

      JsonNode startNode = node.get(0);
      JsonNode endNode = node.get(1);

      Integer start = startNode.isNull() ? null : startNode.asInt();
      Integer end = endNode.isNull() ? null : endNode.asInt();

      return new EventSource.YearRange(start, end);
    } else {
      throw new IOException(
          "Year range must be an integer or [start, end] array, got: " + node.getNodeType());
    }
  }
}
