package com.bdc.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Renders a {@link JsonNode} tree in jq's default style: 2-space indent, no space before {@code :},
 * no trailing newline added twice. Used for the JSON files this CLI hand-edits alongside {@code
 * jq}-based shell scripts ({@code blessed/manifest.json}, ...) so re-running a script that mixes jq
 * and this CLI never produces a spurious diff from formatting alone.
 */
final class JqStyleJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JqStyleJson() {}

  static String render(JsonNode node) {
    StringBuilder sb = new StringBuilder();
    writeNode(sb, node, 0);
    sb.append('\n');
    return sb.toString();
  }

  private static void writeNode(StringBuilder sb, JsonNode node, int depth) {
    String pad = "  ".repeat(depth);
    String padIn = "  ".repeat(depth + 1);
    if (node.isObject()) {
      if (node.isEmpty()) {
        sb.append("{}");
        return;
      }
      sb.append("{\n");
      List<String> keys = new ArrayList<>();
      Iterator<String> names = node.fieldNames();
      names.forEachRemaining(keys::add);
      for (int i = 0; i < keys.size(); i++) {
        String key = keys.get(i);
        sb.append(padIn).append(jsonString(key)).append(": ");
        writeNode(sb, node.get(key), depth + 1);
        if (i < keys.size() - 1) sb.append(',');
        sb.append('\n');
      }
      sb.append(pad).append('}');
    } else if (node.isArray()) {
      if (node.isEmpty()) {
        sb.append("[]");
        return;
      }
      sb.append("[\n");
      for (int i = 0; i < node.size(); i++) {
        sb.append(padIn);
        writeNode(sb, node.get(i), depth + 1);
        if (i < node.size() - 1) sb.append(',');
        sb.append('\n');
      }
      sb.append(pad).append(']');
    } else if (node.isTextual()) {
      sb.append(jsonString(node.asText()));
    } else if (node.isNull()) {
      sb.append("null");
    } else {
      sb.append(node.toString());
    }
  }

  private static String jsonString(String s) {
    try {
      return MAPPER.writeValueAsString(s);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
