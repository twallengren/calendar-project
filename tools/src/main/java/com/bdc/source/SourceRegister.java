package com.bdc.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Canonical machine-readable evidence register; prose coverage notes confer no confidence. */
public final class SourceRegister {
  private static final List<String> COLUMNS =
      List.of("id", "title", "publisher", "location", "retrieved", "covers", "notes");
  private final JsonNode root;

  private SourceRegister(JsonNode root) {
    this.root = root;
  }

  public static SourceRegister read(Path path, Path sourcesRoot) throws IOException {
    JsonNode root = new ObjectMapper().readTree(path.toFile());
    if (!"1.0".equals(root.path("schema_version").asText()) || !root.path("entries").isArray()) {
      throw new IOException(path + ": unsupported source register schema");
    }
    requireFields(root, Set.of("schema_version", "entries"), path);
    Set<String> ids = new java.util.HashSet<>();
    for (JsonNode entry : root.path("entries")) {
      requireFields(
          entry,
          Set.of(
              "id",
              "title",
              "publisher",
              "location",
              "retrieved",
              "covers",
              "notes",
              "local_files",
              "support_intervals"),
          path);
      for (String array : List.of("local_files", "support_intervals"))
        if (!entry.path(array).isArray())
          throw new IOException(path + ": " + array + " must be an array");
      String id = entry.path("id").asText();
      if (id.isBlank() || !ids.add(id))
        throw new IOException(path + ": missing/duplicate source id " + id);
      for (String field : COLUMNS) {
        if (!entry.path(field).isTextual())
          throw new IOException(path + ": missing text field " + field);
      }
      for (JsonNode file : entry.path("local_files")) {
        requireFields(file, Set.of("path", "sha256"), path);
        Path local = sourcesRoot.resolve(file.path("path").asText()).normalize();
        if (!local.toAbsolutePath().startsWith(sourcesRoot.toAbsolutePath().normalize())
            || !Files.isRegularFile(local)
            || !local.toRealPath().startsWith(sourcesRoot.toRealPath())) {
          throw new IOException(path + ": missing or escaping evidence file " + local);
        }
        try {
          String hash =
              HexFormat.of()
                  .formatHex(
                      MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(local)));
          if (!hash.equals(file.path("sha256").asText()))
            throw new IOException(path + ": evidence checksum mismatch " + local);
        } catch (java.security.NoSuchAlgorithmException e) {
          throw new IllegalStateException(e);
        }
      }
      for (JsonNode interval : entry.path("support_intervals")) {
        requireFields(interval, Set.of("from", "to", "scope"), path);
        try {
          com.bdc.trust.CompletenessScope.valueOf(interval.path("scope").asText());
        } catch (IllegalArgumentException e) {
          throw new IOException(path + ": invalid support scope", e);
        }
        LocalDate from = LocalDate.parse(interval.path("from").asText());
        LocalDate to = LocalDate.parse(interval.path("to").asText());
        if (from.isAfter(to) || interval.path("scope").asText().isBlank()) {
          throw new IOException(path + ": invalid support interval for " + id);
        }
      }
    }
    return new SourceRegister(root);
  }

  private static void requireFields(JsonNode node, Set<String> allowed, Path path)
      throws IOException {
    if (!node.isObject()) throw new IOException(path + ": expected object");
    var fields = node.fieldNames();
    while (fields.hasNext()) {
      String field = fields.next();
      if (!allowed.contains(field))
        throw new IOException(path + ": unknown source schema field " + field);
    }
  }

  public List<com.bdc.chronology.DateRange> support(
      String id, com.bdc.trust.CompletenessScope scope) {
    List<com.bdc.chronology.DateRange> result = new ArrayList<>();
    for (JsonNode entry : root.path("entries")) {
      if (!id.equals(entry.path("id").asText())) continue;
      for (JsonNode interval : entry.path("support_intervals")) {
        if (scope.name().equals(interval.path("scope").asText()))
          result.add(
              new com.bdc.chronology.DateRange(
                  LocalDate.parse(interval.path("from").asText()),
                  LocalDate.parse(interval.path("to").asText())));
      }
    }
    return List.copyOf(result);
  }

  public boolean contains(String id) {
    for (JsonNode entry : root.path("entries"))
      if (id.equals(entry.path("id").asText())) return true;
    return false;
  }

  public List<List<String>> rows() {
    List<List<String>> rows = new ArrayList<>();
    for (JsonNode entry : root.path("entries")) {
      List<String> cells = new ArrayList<>();
      for (String column : COLUMNS) cells.add(entry.path(column).asText());
      cells.set(0, "`" + cells.getFirst() + "`");
      rows.add(List.copyOf(cells));
    }
    return List.copyOf(rows);
  }

  /** Replace only the generated table; the surrounding authored explanation is preserved. */
  public String markdown(String narrative) {
    StringBuilder table =
        new StringBuilder(
            "| id | title | publisher | url / file | retrieved | covers | notes |\n|----|-------|-----------|------------|-----------|--------|-------|\n");
    for (List<String> row : rows())
      table.append("| ").append(String.join(" | ", row)).append(" |\n");
    return narrative.replaceFirst(
        "(?m)^\\| id \\|[^\\n]*\\n(?:\\|[^\\n]*\\n)+",
        java.util.regex.Matcher.quoteReplacement(table.toString()));
  }
}
