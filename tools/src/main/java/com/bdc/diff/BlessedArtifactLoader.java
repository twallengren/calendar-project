package com.bdc.diff;

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
        ReleaseVersion releaseVersion
    ) {}

    public record CalendarInfo(
        LocalDate rangeStart,
        LocalDate rangeEnd,
        int eventCount,
        String checksum
    ) {}

    public record ReleaseVersion(
        String semantic,
        String gitSha,
        String generationDate
    ) {}

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
            CalendarInfo info = new CalendarInfo(
                LocalDate.parse(calNode.path("range_start").asText()),
                LocalDate.parse(calNode.path("range_end").asText()),
                calNode.path("event_count").asInt(),
                calNode.path("checksum").asText()
            );
            calendars.put(calId, info);
        }

        JsonNode versionNode = root.path("release_version");
        ReleaseVersion releaseVersion = new ReleaseVersion(
            versionNode.path("semantic").asText(),
            versionNode.path("git_sha").asText(),
            versionNode.path("generation_date").asText()
        );

        return new BlessedManifest(schemaVersion, blessedAt, blessedBy, calendars, releaseVersion);
    }

    public List<Event> loadBlessedEvents(Path blessedDir, String calendarId) throws IOException {
        Path csvPath = blessedDir.resolve(calendarId).resolve("events.csv");
        if (!Files.exists(csvPath)) {
            throw new IOException("Blessed CSV not found: " + csvPath);
        }

        List<Event> events = new ArrayList<>();
        List<String> lines = Files.readAllLines(csvPath);

        // Skip header
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(",", 3);
            if (parts.length < 3) continue;

            LocalDate date = LocalDate.parse(parts[0]);
            EventType type = EventType.valueOf(parts[1]);
            String description = parts[2];

            events.add(new Event(date, type, description, "blessed"));
        }

        return events;
    }
}
