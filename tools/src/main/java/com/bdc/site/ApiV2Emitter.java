package com.bdc.site;

import com.bdc.artifact.ReleaseHistoryStore;
import com.bdc.emitter.AssessmentEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit daily assessments under a new wire version; v1 remains available unchanged. */
public final class ApiV2Emitter {
  private final ObjectMapper json =
      new ObjectMapper()
          .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  public void emit(Path blessed, Path history, Path out, boolean includeBase, Instant generatedAt)
      throws IOException {
    var manifest = json.readTree(blessed.resolve("manifest.json").toFile());
    var store = new ReleaseHistoryStore(history, blessed);
    List<String> ids = new ArrayList<>();
    manifest.path("calendars").fieldNames().forEachRemaining(ids::add);
    ids.sort(String::compareTo);
    List<Map<String, Object>> index = new ArrayList<>();
    String version = manifest.path("release_version").path("semantic").asText();
    for (String id : ids) {
      var metadataPath = blessed.resolve(id).resolve("metadata.json");
      if (!Files.exists(metadataPath)) continue;
      var metadata = json.readTree(metadataPath.toFile());
      String kind =
          metadata
              .path("kind")
              .asText(manifest.path("calendars").path(id).path("kind").asText("market"));
      if (!includeBase && !kind.equals("market") && !kind.equals("payment")) continue;
      var snapshot =
          store.resolve(id, "latest").orElseThrow(() -> new IOException("No artifact for " + id));
      var stream = store.stream(snapshot);
      var range = stream.range();
      Path directory = out.resolve("v2/calendars").resolve(id);
      List<Map<String, Object>> years = new ArrayList<>();
      for (int year = range.start().getYear(); year <= range.end().getYear(); year++) {
        LocalDate start =
            LocalDate.of(year, 1, 1).isBefore(range.start())
                ? range.start()
                : LocalDate.of(year, 1, 1);
        LocalDate end =
            LocalDate.of(year, 12, 31).isAfter(range.end())
                ? range.end()
                : LocalDate.of(year, 12, 31);
        List<Map<String, Object>> days =
            start
                .datesUntil(end.plusDays(1))
                .map(stream::assessment)
                .map(AssessmentEmitter::row)
                .toList();
        write(
            directory.resolve(year + ".json"),
            Map.of(
                "schema_version", "2.0", "calendar_id", id, "data_version", version, "days", days));
        years.add(Map.of("year", year, "href", year + ".json"));
      }
      Map<String, Object> calendar = new LinkedHashMap<>();
      calendar.put("schema_version", "2.0");
      calendar.put("calendar_id", id);
      calendar.put("data_version", version);
      calendar.put("kind", kind);
      calendar.put(
          "timezone",
          metadata.path("timezone").isMissingNode() ? null : metadata.path("timezone").asText());
      calendar.put("range_start", range.start().toString());
      calendar.put("range_end", range.end().toString());
      calendar.put("coverage", metadata.get("coverage"));
      calendar.put("explicit_quality", !stream.coverageIntervals().isEmpty());
      calendar.put("years", years);
      write(directory.resolve("manifest.json"), calendar);
      index.add(Map.of("id", id, "href", "calendars/" + id + "/manifest.json"));
    }
    write(
        out.resolve("v2/index.json"),
        Map.of(
            "schema_version",
            "2.0",
            "data_version",
            version,
            "generated_at",
            generatedAt.toString(),
            "calendars",
            index));
  }

  private void write(Path file, Object value) throws IOException {
    Files.createDirectories(file.getParent());
    json.writeValue(file.toFile(), value);
  }
}
