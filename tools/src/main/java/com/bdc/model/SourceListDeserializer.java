package com.bdc.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserializes the {@code source:} field, which may be a single citation object, a list of citation
 * objects, or a bare string (interpreted as a citation id).
 */
public class SourceListDeserializer extends JsonDeserializer<List<SourceCitation>> {

  @Override
  public List<SourceCitation> deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    ObjectMapper mapper = (ObjectMapper) p.getCodec();
    JsonNode node = mapper.readTree(p);
    List<SourceCitation> result = new ArrayList<>();
    if (node.isArray()) {
      for (JsonNode item : node) {
        result.add(parseOne(mapper, item));
      }
    } else {
      result.add(parseOne(mapper, node));
    }
    return result;
  }

  private SourceCitation parseOne(ObjectMapper mapper, JsonNode node) throws IOException {
    if (node.isTextual()) {
      return SourceCitation.ofId(node.asText());
    }
    if (node.isObject()) {
      return mapper.treeToValue(node, SourceCitation.class);
    }
    throw new IOException(
        "source must be a citation object, a list of citation objects, or an id string; got "
            + node.getNodeType());
  }
}
