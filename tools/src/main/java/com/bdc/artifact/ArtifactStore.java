package com.bdc.artifact;

import com.bdc.emitter.CsvEmitter;
import com.bdc.model.BitemporalMeta;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Manages storage and retrieval of bitemporal calendar artifacts.
 *
 * <p>Directory structure: artifacts/ ├── resolved/ │ └── {calendar-id}/ │ └──
 * {transaction-timestamp}.yaml ├── generated/ │ └── {calendar-id}/ │ └── {valid-from}_{valid-to}/ │
 * └── {transaction-timestamp}/ │ ├── events.csv │ └── metadata.json └── index.json
 */
public class ArtifactStore {

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

  private final Path artifactsRoot;
  private final ObjectMapper jsonMapper;
  private final ObjectMapper yamlMapper;
  private final CsvEmitter csvEmitter;

  public ArtifactStore(Path artifactsRoot) {
    this.artifactsRoot = artifactsRoot;
    this.jsonMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.yamlMapper =
        new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.csvEmitter = new CsvEmitter();
  }

  /**
   * Store a resolved spec artifact.
   *
   * @return the path where the artifact was stored
   */
  public Path storeResolvedSpec(ResolvedSpec spec, BitemporalMeta meta) throws IOException {
    String timestamp = TIMESTAMP_FORMAT.format(meta.transactionTime());
    Path dir = artifactsRoot.resolve("resolved").resolve(spec.id());
    Files.createDirectories(dir);

    Path outputPath = dir.resolve(timestamp + ".yaml");

    Map<String, Object> artifact = new LinkedHashMap<>();

    // _meta section
    Map<String, Object> metaSection = new LinkedHashMap<>();
    metaSection.put("kind", "resolved_calendar");
    metaSection.put("id", spec.id());
    metaSection.put("transaction_time", meta.transactionTime().toString());
    metaSection.put("source_version", meta.sourceVersion());
    metaSection.put("tool_version", meta.toolVersion());
    metaSection.put("resolution_chain", spec.resolutionChain());
    artifact.put("_meta", metaSection);

    // metadata section
    if (spec.metadata() != null) {
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("name", spec.metadata().name());
      metadata.put("description", spec.metadata().description());
      metadata.put("chronology", spec.metadata().chronology());
      artifact.put("metadata", metadata);
    }

    // weekend_policy section
    if (spec.weekendPolicy() != null && !spec.weekendPolicy().weekendDays().isEmpty()) {
      Map<String, Object> weekendPolicy = new LinkedHashMap<>();
      weekendPolicy.put(
          "days", spec.weekendPolicy().weekendDays().stream().map(Enum::name).toList());
      artifact.put("weekend_policy", weekendPolicy);
    }

    // event_sources section
    List<Map<String, Object>> eventSources = new ArrayList<>();
    for (var source : spec.eventSources()) {
      Map<String, Object> sourceMap = new LinkedHashMap<>();
      sourceMap.put("key", source.key());
      sourceMap.put("name", source.name());
      if (source.defaultClassification() != null) {
        sourceMap.put("classification", source.defaultClassification().name());
      }
      if (source.rule() != null) {
        sourceMap.put("rule", ruleToMap(source.rule()));
      }
      eventSources.add(sourceMap);
    }
    artifact.put("event_sources", eventSources);

    // deltas section
    if (!spec.deltas().isEmpty()) {
      List<Map<String, Object>> deltas = new ArrayList<>();
      for (var delta : spec.deltas()) {
        deltas.add(deltaToMap(delta));
      }
      artifact.put("deltas", deltas);
    }

    yamlMapper.writeValue(outputPath.toFile(), artifact);

    // Update index
    updateIndex();

    return outputPath;
  }

  /**
   * Store generated calendar events.
   *
   * @return the directory where artifacts were stored
   */
  public Path storeGeneratedEvents(
      String calendarId,
      LocalDate validFrom,
      LocalDate validTo,
      List<Event> events,
      ResolvedSpec spec,
      BitemporalMeta meta)
      throws IOException {

    String timestamp = TIMESTAMP_FORMAT.format(meta.transactionTime());
    String validRange = validFrom.toString() + "_" + validTo.toString();
    Path dir =
        artifactsRoot
            .resolve("generated")
            .resolve(calendarId)
            .resolve(validRange)
            .resolve(timestamp);
    Files.createDirectories(dir);

    // Store events.csv
    Path csvPath = dir.resolve("events.csv");
    csvEmitter.emit(events, csvPath);

    // Store metadata.json with bitemporal info
    Path metadataPath = dir.resolve("metadata.json");
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("calendar_id", calendarId);
    metadata.put("valid_from", validFrom.toString());
    metadata.put("valid_to", validTo.toString());
    metadata.put("transaction_time", meta.transactionTime().toString());
    metadata.put(
        "resolved_spec_ref",
        "resolved/" + calendarId + "/" + TIMESTAMP_FORMAT.format(meta.transactionTime()) + ".yaml");
    metadata.put("source_version", meta.sourceVersion());
    metadata.put("tool_version", meta.toolVersion());
    metadata.put("generated_by", meta.generatedBy());
    metadata.put("event_count", events.size());

    Map<String, Long> countsByType = new LinkedHashMap<>();
    for (Event event : events) {
      countsByType.merge(event.type().name(), 1L, Long::sum);
    }
    metadata.put("counts_by_type", countsByType);

    metadata.put("resolution_chain", spec.resolutionChain());

    // Compute checksum
    String csvContent = Files.readString(csvPath);
    metadata.put("checksum", "sha256:" + sha256(csvContent));

    jsonMapper.writeValue(metadataPath.toFile(), metadata);

    // Update index
    updateIndex();

    return dir;
  }

  /** List all resolved spec versions for a calendar. */
  public List<String> listResolvedVersions(String calendarId) throws IOException {
    Path dir = artifactsRoot.resolve("resolved").resolve(calendarId);
    if (!Files.exists(dir)) {
      return List.of();
    }
    try (var stream = Files.list(dir)) {
      return stream
          .filter(p -> p.toString().endsWith(".yaml"))
          .map(p -> p.getFileName().toString().replace(".yaml", ""))
          .sorted(Comparator.reverseOrder())
          .toList();
    }
  }

  /** List all generated versions for a calendar and valid range. */
  public List<String> listGeneratedVersions(
      String calendarId, LocalDate validFrom, LocalDate validTo) throws IOException {
    String validRange = validFrom.toString() + "_" + validTo.toString();
    Path dir = artifactsRoot.resolve("generated").resolve(calendarId).resolve(validRange);
    if (!Files.exists(dir)) {
      return List.of();
    }
    try (var stream = Files.list(dir)) {
      return stream
          .filter(Files::isDirectory)
          .map(p -> p.getFileName().toString())
          .sorted(Comparator.reverseOrder())
          .toList();
    }
  }

  private void updateIndex() throws IOException {
    Path indexPath = artifactsRoot.resolve("index.json");
    Map<String, Object> index = new LinkedHashMap<>();
    index.put("updated_at", Instant.now().toString());

    // Index resolved specs
    Path resolvedDir = artifactsRoot.resolve("resolved");
    if (Files.exists(resolvedDir)) {
      Map<String, List<String>> resolvedIndex = new LinkedHashMap<>();
      try (var calendars = Files.list(resolvedDir)) {
        for (var calDir : calendars.filter(Files::isDirectory).toList()) {
          String calId = calDir.getFileName().toString();
          resolvedIndex.put(calId, listResolvedVersions(calId));
        }
      }
      index.put("resolved", resolvedIndex);
    }

    // Index generated events
    Path generatedDir = artifactsRoot.resolve("generated");
    if (Files.exists(generatedDir)) {
      Map<String, Map<String, List<String>>> generatedIndex = new LinkedHashMap<>();
      try (var calendars = Files.list(generatedDir)) {
        for (var calDir : calendars.filter(Files::isDirectory).toList()) {
          String calId = calDir.getFileName().toString();
          Map<String, List<String>> rangeIndex = new LinkedHashMap<>();
          try (var ranges = Files.list(calDir)) {
            for (var rangeDir : ranges.filter(Files::isDirectory).toList()) {
              String range = rangeDir.getFileName().toString();
              try (var versions = Files.list(rangeDir)) {
                List<String> versionList =
                    versions
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .sorted(Comparator.reverseOrder())
                        .toList();
                rangeIndex.put(range, versionList);
              }
            }
          }
          generatedIndex.put(calId, rangeIndex);
        }
      }
      index.put("generated", generatedIndex);
    }

    jsonMapper.writeValue(indexPath.toFile(), index);
  }

  private Map<String, Object> ruleToMap(com.bdc.model.Rule rule) {
    Map<String, Object> map = new LinkedHashMap<>();
    switch (rule) {
      case com.bdc.model.Rule.FixedMonthDay r -> {
        map.put("type", "fixed_month_day");
        map.put("month", r.month());
        map.put("day", r.day());
        if (r.chronology() != null && !"ISO".equals(r.chronology())) {
          map.put("chronology", r.chronology());
        }
      }
      case com.bdc.model.Rule.NthWeekdayOfMonth r -> {
        map.put("type", "nth_weekday_of_month");
        map.put("month", r.month());
        map.put("weekday", r.weekday().name());
        map.put("nth", r.nth());
      }
      case com.bdc.model.Rule.ExplicitDates r -> {
        map.put("type", "explicit_dates");
        map.put("dates", r.dates().stream().map(LocalDate::toString).toList());
      }
    }
    return map;
  }

  private Map<String, Object> deltaToMap(com.bdc.model.Delta delta) {
    Map<String, Object> map = new LinkedHashMap<>();
    switch (delta) {
      case com.bdc.model.Delta.Add d -> {
        map.put("action", "add");
        map.put("key", d.key());
        map.put("name", d.name());
        map.put("date", d.date().toString());
        map.put("classification", d.classification().name());
      }
      case com.bdc.model.Delta.Remove d -> {
        map.put("action", "remove");
        map.put("key", d.key());
        map.put("date", d.date().toString());
      }
      case com.bdc.model.Delta.Reclassify d -> {
        map.put("action", "reclassify");
        map.put("key", d.key());
        map.put("date", d.date().toString());
        map.put("new_classification", d.newClassification().name());
      }
    }
    return map;
  }

  private String sha256(String content) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (Exception e) {
      return "unknown";
    }
  }
}
