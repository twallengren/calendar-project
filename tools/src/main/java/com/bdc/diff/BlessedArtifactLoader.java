package com.bdc.diff;

import com.bdc.csv.EventCsvReader;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

public class BlessedArtifactLoader {

  private final ObjectMapper mapper = new ObjectMapper();

  public record BlessedManifest(
      String schemaVersion,
      String blessedAt,
      String blessedBy,
      Map<String, CalendarInfo> calendars,
      ReleaseVersion releaseVersion) {}

  public record CalendarInfo(
      LocalDate rangeStart, LocalDate rangeEnd, int eventCount, String checksum) {}

  public record ReleaseVersion(String semantic, String gitSha, String generationDate) {}

  public BlessedManifest loadManifest(Path blessedDir) throws IOException {
    Path manifestPath = blessedDir.resolve("manifest.json");
    if (!Files.exists(manifestPath)) {
      throw new IOException("Manifest not found: " + manifestPath);
    }

    JsonNode root = mapper.readTree(manifestPath.toFile());

    String schemaVersion = root.path("schema_version").asText();
    String blessedAt = root.path("blessed_at").asText();
    String blessedBy = root.path("blessed_by").asText();

    Map<String, CalendarInfo> calendars = new LinkedHashMap<>();
    JsonNode calsNode = root.path("calendars");
    Iterator<String> calNames = calsNode.fieldNames();
    while (calNames.hasNext()) {
      String calId = calNames.next();
      JsonNode calNode = calsNode.get(calId);
      CalendarInfo info =
          new CalendarInfo(
              LocalDate.parse(calNode.path("range_start").asText()),
              LocalDate.parse(calNode.path("range_end").asText()),
              calNode.path("event_count").asInt(),
              calNode.path("checksum").asText());
      calendars.put(calId, info);
    }

    JsonNode versionNode = root.path("release_version");
    ReleaseVersion releaseVersion =
        new ReleaseVersion(
            versionNode.path("semantic").asText(),
            versionNode.path("git_sha").asText(),
            versionNode.path("generation_date").asText());

    return new BlessedManifest(schemaVersion, blessedAt, blessedBy, calendars, releaseVersion);
  }

  public List<Event> loadBlessedEvents(Path blessedDir, String calendarId) throws IOException {
    Path csvPath = blessedDir.resolve(calendarId).resolve("events.csv");
    if (!Files.exists(csvPath)) {
      // New calendar with no blessed artifacts yet — treat as empty baseline
      return List.of();
    }

    EventCsvReader.Table table = new EventCsvReader().read(csvPath);
    int dateColumn = table.header().indexOf("date");
    int typeColumn = table.header().indexOf("type");
    int descriptionColumn = table.header().indexOf("description");
    return table.records().stream()
        .map(
            row ->
                new Event(
                    LocalDate.parse(row.get(dateColumn)),
                    EventType.valueOf(row.get(typeColumn)),
                    row.get(descriptionColumn),
                    "blessed"))
        .toList();
  }
}
