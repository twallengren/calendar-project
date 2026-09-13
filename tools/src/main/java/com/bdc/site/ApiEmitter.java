package com.bdc.site;

import com.bdc.artifact.ReleaseHistoryStore;
import com.bdc.emitter.EventsCsvReader;
import com.bdc.emitter.IcsEmitter;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the {@code /v1/} JSON API and per-calendar {@code .ics} files from published artifacts.
 *
 * <p>Reads {@code blessed/} (current release, via {@link EventsCsvReader}) and {@code
 * release-history/} (previous releases, via {@link ReleaseHistoryStore}) and writes minified JSON
 * plus RFC 5545 calendars under an output directory. See {@code spec/SPEC.md#json-api-v1} for the
 * URL layout and compatibility contract.
 */
public class ApiEmitter {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
  private static final LocalDate ICS_RECENT_FROM = LocalDate.of(2020, 1, 1);
  private static final String DEFAULT_KIND = "market";

  /**
   * Lower bound for the granular per-year/{@code all.json}/pinned-release JSON files. A calendar's
   * full multi-century history (e.g. US-NYSE back to 1900) is already published in full at {@code
   * blessed/<ID>/events.json}; duplicating that across per-year chunks, a whole-history {@code
   * all.json}, and pinned copies for every retained release would put the site well over budget
   * (measured: minified full-history events for the four current market calendars already total
   * ~7.3 MB for a single copy, before any duplication). The granular v1 endpoints instead cover the
   * actively-relevant window from here through each calendar's coverage end, matching the cutoff
   * already used for {@code holidays-recent.ics}. {@code holidays.json} is the exception: it
   * excludes WEEKEND rows so full history is cheap and is kept complete.
   */
  private static final LocalDate GRANULAR_FROM = LocalDate.of(2020, 1, 1);

  private final Path blessedDir;
  private final Path releaseHistoryDir;
  private final Path outDir;
  private final boolean includeBase;
  private final Instant generatedAt;

  private final ObjectMapper json = new ObjectMapper();
  private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
  private final EventsCsvReader csvReader = new EventsCsvReader();
  private final IcsEmitter icsEmitter = new IcsEmitter();

  public ApiEmitter(
      Path blessedDir,
      Path releaseHistoryDir,
      Path outDir,
      boolean includeBase,
      Instant generatedAt) {
    this.blessedDir = blessedDir;
    this.releaseHistoryDir = releaseHistoryDir;
    this.outDir = outDir;
    this.includeBase = includeBase;
    this.generatedAt = generatedAt;
  }

  public void emit() throws IOException {
    JsonNode manifest = json.readTree(blessedDir.resolve("manifest.json").toFile());
    JsonNode releaseVersionNode = manifest.path("release_version");
    String releaseSemantic = textOrNull(releaseVersionNode, "semantic");
    String releaseGitSha = textOrNull(releaseVersionNode, "git_sha");
    String releaseGenerationDateStr = textOrNull(releaseVersionNode, "generation_date");
    LocalDate releaseGenerationDate =
        releaseGenerationDateStr != null
            ? LocalDate.parse(releaseGenerationDateStr)
            : LocalDate.now();

    ReleaseHistoryStore historyStore = new ReleaseHistoryStore(releaseHistoryDir, blessedDir);

    Path v1Dir = outDir.resolve("v1");
    Files.createDirectories(v1Dir);

    List<String> ids = new ArrayList<>();
    manifest.path("calendars").fieldNames().forEachRemaining(ids::add);
    Collections.sort(ids);

    List<Map<String, Object>> indexCalendars = new ArrayList<>();

    for (String id : ids) {
      Path calDir = blessedDir.resolve(id);
      Path metadataFile = calDir.resolve("metadata.json");
      if (!Files.exists(metadataFile)) {
        continue;
      }
      JsonNode metadataNode = json.readTree(metadataFile.toFile());
      String kind = kindOf(metadataNode, manifest.path("calendars").path(id));
      if (!includeBase && !DEFAULT_KIND.equals(kind)) {
        continue;
      }

      List<Event> events = csvReader.read(calDir.resolve("events.csv"));
      Collections.sort(events);

      Map<String, String> coverage = coverageOf(metadataNode);
      String checksum = textOrNull(manifest.path("calendars").path(id), "checksum");
      String name = metadataNode.path("calendar_name").asText(id);
      String timezone = textOrNull(metadataNode, "timezone");

      Path calOutDir = v1Dir.resolve("calendars").resolve(id);

      List<Event> granularEvents =
          events.stream().filter(e -> !e.date().isBefore(GRANULAR_FROM)).toList();
      Map<Integer, List<Event>> byYear = groupByYear(granularEvents);
      List<Integer> years = new ArrayList<>(byYear.keySet());
      Map<String, String> fullRange = fullRangeOf(metadataNode);
      LocalDate granularFrom =
          GRANULAR_FROM.isAfter(LocalDate.parse(fullRange.get("from")))
              ? GRANULAR_FROM
              : LocalDate.parse(fullRange.get("from"));
      LocalDate granularTo = LocalDate.parse(fullRange.get("to"));

      for (Integer year : years) {
        writeJson(
            calOutDir.resolve(year + ".json"),
            document(
                id,
                releaseSemantic,
                releaseGitSha,
                byYear.get(year),
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31),
                coverage));
      }

      List<Event> nonWeekend = events.stream().filter(e -> e.type() != EventType.WEEKEND).toList();
      writeJson(
          calOutDir.resolve("holidays.json"),
          document(
              id,
              releaseSemantic,
              releaseGitSha,
              nonWeekend,
              LocalDate.parse(fullRange.get("from")),
              LocalDate.parse(fullRange.get("to")),
              coverage));
      writeJson(
          calOutDir.resolve("all.json"),
          document(
              id,
              releaseSemantic,
              releaseGitSha,
              granularEvents,
              granularFrom,
              granularTo,
              coverage));

      writeText(
          calOutDir.resolve("holidays.ics"),
          icsEmitter.emit(id, name, releaseSemantic, releaseGenerationDate, events));
      List<Event> recent =
          events.stream().filter(e -> !e.date().isBefore(ICS_RECENT_FROM)).toList();
      writeText(
          calOutDir.resolve("holidays-recent.ics"),
          icsEmitter.emit(id, name, releaseSemantic, releaseGenerationDate, recent));

      writeJson(
          calOutDir.resolve("manifest.json"), calendarManifest(id, metadataNode, calDir, years));

      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("id", id);
      entry.put("name", name);
      entry.put("timezone", timezone);
      entry.put("coverage", coverage);
      entry.put("counts_by_type", toObject(metadataNode.path("counts_by_type")));
      entry.put("counts_by_status", toObject(metadataNode.path("counts_by_status")));
      entry.put("checksum", checksum);
      entry.put(
          "years",
          years.isEmpty() ? List.of() : List.of(Collections.min(years), Collections.max(years)));
      entry.put("href", "/v1/calendars/" + id + "/manifest.json");
      indexCalendars.add(entry);

      writePinnedReleases(historyStore, id, coverage, v1Dir);
    }

    Map<String, Object> index = new LinkedHashMap<>();
    index.put("calendars", indexCalendars);
    Map<String, String> release = new LinkedHashMap<>();
    release.put("semantic", releaseSemantic);
    release.put("git_sha", releaseGitSha);
    release.put("generation_date", releaseGenerationDateStr);
    index.put("release", release);
    index.put("generated_at", generatedAt.toString());
    index.put("schema_version", "1.0");
    index.put("api_version", "v1");
    writeJson(v1Dir.resolve("index.json"), index);
  }

  private void writePinnedReleases(
      ReleaseHistoryStore historyStore, String id, Map<String, String> coverage, Path v1Dir)
      throws IOException {
    List<ReleaseHistoryStore.Snapshot> snapshots = historyStore.list(id);
    Map<String, ReleaseHistoryStore.Snapshot> byVersion = new LinkedHashMap<>();
    for (ReleaseHistoryStore.Snapshot snapshot : snapshots) {
      byVersion.putIfAbsent(snapshot.version(), snapshot);
    }
    for (Map.Entry<String, ReleaseHistoryStore.Snapshot> versionEntry : byVersion.entrySet()) {
      String version = versionEntry.getKey();
      ReleaseHistoryStore.Snapshot snapshot = versionEntry.getValue();
      List<Event> snapshotEvents = historyStore.loadEvents(snapshot);
      Collections.sort(snapshotEvents);
      List<Event> granularSnapshotEvents =
          snapshotEvents.stream().filter(e -> !e.date().isBefore(GRANULAR_FROM)).toList();
      Map<Integer, List<Event>> byYear = groupByYear(granularSnapshotEvents);
      Path releaseOutDir =
          v1Dir.resolve("releases").resolve(version).resolve("calendars").resolve(id);
      for (Map.Entry<Integer, List<Event>> yearEntry : byYear.entrySet()) {
        writeJson(
            releaseOutDir.resolve(yearEntry.getKey() + ".json"),
            document(
                id,
                version,
                snapshot.gitSha(),
                yearEntry.getValue(),
                LocalDate.of(yearEntry.getKey(), 1, 1),
                LocalDate.of(yearEntry.getKey(), 12, 31),
                coverage));
      }
    }
  }

  private Map<String, Object> calendarManifest(
      String id, JsonNode metadataNode, Path calDir, List<Integer> years) throws IOException {
    Map<String, Object> manifest = new LinkedHashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = metadataNode.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      manifest.put(field.getKey(), toObject(field.getValue()));
    }
    manifest.put("weekend_policy", weekendPolicyOf(calDir));
    manifest.put("years", years);

    Map<String, Object> links = new LinkedHashMap<>();
    links.put("year_template", "/v1/calendars/" + id + "/{year}.json");
    links.put("holidays", "/v1/calendars/" + id + "/holidays.json");
    links.put("all", "/v1/calendars/" + id + "/all.json");
    links.put("ics", "/v1/calendars/" + id + "/holidays.ics");
    links.put("ics_recent", "/v1/calendars/" + id + "/holidays-recent.ics");
    manifest.put("links", links);
    return manifest;
  }

  private Object weekendPolicyOf(Path calDir) throws IOException {
    Path resolvedYaml = calDir.resolve("resolved.yaml");
    if (!Files.exists(resolvedYaml)) {
      return null;
    }
    JsonNode root = yaml.readTree(resolvedYaml.toFile());
    JsonNode weekendPolicy = root.path("weekend_policy");
    if (weekendPolicy.isMissingNode() || weekendPolicy.isNull()) {
      return null;
    }
    return toObject(weekendPolicy);
  }

  private static String kindOf(JsonNode metadataNode, JsonNode manifestEntry) {
    String kind = textOrNull(metadataNode, "kind");
    if (kind != null) {
      return kind;
    }
    kind = textOrNull(manifestEntry, "kind");
    return kind != null ? kind : DEFAULT_KIND;
  }

  private Map<String, Object> document(
      String id,
      String semantic,
      String gitSha,
      List<Event> events,
      LocalDate from,
      LocalDate to,
      Map<String, String> coverage) {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("calendar_id", id);
    Map<String, String> version = new LinkedHashMap<>();
    version.put("semantic", semantic);
    version.put("git_sha", gitSha);
    doc.put("version", version);
    Map<String, String> range = new LinkedHashMap<>();
    range.put("from", from.toString());
    range.put("to", to.toString());
    doc.put("range", range);
    doc.put("coverage", coverage);
    doc.put("event_count", events.size());
    doc.put("events", rows(events));
    return doc;
  }

  /**
   * Builds row objects with the same fields as {@link com.bdc.emitter.JsonEventsEmitter}, but
   * omitting {@code observed_from}/{@code close_time} when null rather than writing an explicit
   * {@code null}, to keep the minified per-calendar files reasonably sized. Consumers should treat
   * a missing key the same as an explicit null (see the compatibility contract in {@code
   * spec/SPEC.md}).
   */
  private static List<Map<String, Object>> rows(List<Event> events) {
    List<Map<String, Object>> rows = new ArrayList<>(events.size());
    for (Event event : events) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("date", event.date().toString());
      row.put("type", event.type().name());
      row.put("description", event.description());
      row.put("key", event.key());
      row.put("source_module", event.sourceModule());
      if (event.observedFrom() != null) {
        row.put("observed_from", event.observedFrom().toString());
      }
      String closeTime = formatCloseTime(event);
      if (closeTime != null) {
        row.put("close_time", closeTime);
      }
      row.put("status", event.status() != null ? event.status().name() : null);
      rows.add(row);
    }
    return rows;
  }

  private static String formatCloseTime(Event event) {
    LocalTime closeTime = event.closeTime();
    return closeTime != null ? TIME.format(closeTime) : null;
  }

  private static Map<Integer, List<Event>> groupByYear(List<Event> events) {
    Map<Integer, List<Event>> byYear = new TreeMap<>();
    for (Event event : events) {
      byYear.computeIfAbsent(event.date().getYear(), k -> new ArrayList<>()).add(event);
    }
    return byYear;
  }

  private static Map<String, String> coverageOf(JsonNode metadataNode) {
    JsonNode coverage = metadataNode.path("coverage");
    Map<String, String> result = new LinkedHashMap<>();
    result.put("from", textOrNull(coverage, "from"));
    result.put("to", textOrNull(coverage, "to"));
    result.put("verified_through", textOrNull(coverage, "verified_through"));
    return result;
  }

  private static Map<String, String> fullRangeOf(JsonNode metadataNode) {
    Map<String, String> result = new LinkedHashMap<>();
    result.put("from", textOrNull(metadataNode, "range_start"));
    result.put("to", textOrNull(metadataNode, "range_end"));
    return result;
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  private Object toObject(JsonNode node) {
    return json.convertValue(node, Object.class);
  }

  private void writeJson(Path path, Object value) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, json.writeValueAsString(value));
  }

  private void writeText(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
  }
}
