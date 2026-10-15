package com.bdc.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.cli.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Renders a hand-built calendar with a session on a holiday evening and one on a Saturday, and
 * checks that the session reaches the v1 rows, the ICS feed and the HTML pages without turning
 * either date into a business day.
 */
class SpecialSessionSiteTest {

  private static final String CALENDAR_ID = "XX-SESSIONS";
  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path tempDir;

  @Test
  void specialSessionsAreRenderedButDoNotOpenTheDay() throws Exception {
    Path local = tempDir.resolve("local");
    Path out = tempDir.resolve("site");
    Path calDir = local.resolve(CALENDAR_ID);
    Files.createDirectories(calDir);
    Files.writeString(
        calDir.resolve("events.csv"),
        """
        date,type,description,key,source_module,observed_from,close_time,status,open_time
        2024-03-02,WEEKEND,Saturday,weekend,weekend_policy,,,CONFIRMED,
        2024-03-02,SPECIAL_SESSION,Disaster-Recovery Session,dr_session,module:x,,12:30,CONFIRMED,09:15
        2024-11-01,CLOSED,Diwali,diwali,module:x,,,CONFIRMED,
        2024-11-01,SPECIAL_SESSION,Muhurat Trading,muhurat,module:x,,19:00,CONFIRMED,18:00
        """);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("calendar_id", CALENDAR_ID);
    metadata.put("calendar_name", "Sessions Exchange");
    metadata.put("kind", "market");
    metadata.put("generated_at", "2024-06-01T00:00:00Z");
    metadata.put("range_start", "2024-01-01");
    metadata.put("range_end", "2024-12-31");
    metadata.put("timezone", "Asia/Kolkata");
    metadata.put(
        "coverage",
        Map.of("from", "2024-01-01", "to", "2024-12-31", "verified_through", "2024-12-31"));
    metadata.put("event_count", 4);
    metadata.put("counts_by_type", Map.of("CLOSED", 1, "WEEKEND", 1, "SPECIAL_SESSION", 2));
    metadata.put("counts_by_status", Map.of("CONFIRMED", 4));
    Files.writeString(calDir.resolve("metadata.json"), JSON.writeValueAsString(metadata));

    int exitCode =
        new CommandLine(new Main())
            .execute(
                "site",
                "--blessed-dir",
                local.toString(),
                "--out",
                out.toString(),
                "--sources-dir",
                tempDir.resolve("no-sources").toString(),
                "--release-history-dir",
                tempDir.resolve("no-history").toString(),
                "--generated-at",
                "2024-06-01T00:00:00Z");
    assertEquals(0, exitCode, "site command should succeed");

    JsonNode year =
        JSON.readTree(
            out.resolve("v1/calendars").resolve(CALENDAR_ID).resolve("2024.json").toFile());
    JsonNode muhurat = null;
    for (JsonNode row : year.path("events")) {
      if (row.path("key").asText().equals("muhurat")) muhurat = row;
    }
    assertEquals("SPECIAL_SESSION", muhurat.path("type").asText());
    assertEquals("18:00", muhurat.path("open_time").asText());
    assertEquals("19:00", muhurat.path("close_time").asText());

    String ics = read(out.resolve("v1/calendars").resolve(CALENDAR_ID).resolve("holidays.ics"));
    assertTrue(ics.contains("DTSTART:20241101T123000Z"), ics);
    assertTrue(ics.contains("DTSTART:20240302T034500Z"), ics);

    String holiday = read(out.resolve(CALENDAR_ID).resolve("2024-11-01").resolve("index.html"));
    assertTrue(
        holiday.contains(
            "for Diwali, but holds a special session: Muhurat Trading 18:00\u201319:00."),
        holiday);
    assertTrue(holiday.contains("badge special-session"), holiday);

    String saturday = read(out.resolve(CALENDAR_ID).resolve("2024-03-02").resolve("index.html"));
    assertTrue(saturday.contains("is not open for regular trading on"), saturday);
    assertTrue(saturday.contains("verdict special-session"), saturday);

    String yearPage = read(out.resolve(CALENDAR_ID).resolve("2024").resolve("index.html"));
    assertTrue(
        yearPage.contains("day closed session"), "the holiday cell is closed with a session");
    assertTrue(yearPage.contains("day weekend session"), "the Saturday cell is a weekend session");
    assertTrue(yearPage.contains("(special session 18:00–19:00)"), yearPage);
  }

  private static String read(Path path) throws Exception {
    return Files.readString(path, StandardCharsets.UTF_8);
  }
}
