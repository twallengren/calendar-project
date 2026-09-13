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

    // The resolved document is the same faithful form the SpecEmitter produces
    Map<String, Object> resolvedDoc = com.bdc.emitter.SpecEmitter.resolvedToMap(spec);
    resolvedDoc.remove("kind");
    resolvedDoc.remove("id");
    resolvedDoc.remove("resolution_chain");
    artifact.putAll(resolvedDoc);

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
