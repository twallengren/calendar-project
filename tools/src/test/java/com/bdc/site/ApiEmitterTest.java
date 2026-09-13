package com.bdc.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.emitter.EventsCsvReader;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.test.GoldenTestRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs {@link ApiEmitter} against the real {@code blessed/} and {@code release-history/}
 * directories (not fixtures) so these tests exercise the actual published artifacts.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiEmitterTest {

  private static final Instant FIXED_GENERATED_AT = Instant.parse("2026-06-01T00:00:00Z");

  @TempDir static Path outDir;
  private Path v1;
  private final ObjectMapper mapper = new ObjectMapper();
  private GoldenTestRunner goldenRunner;

  @BeforeAll
  void generateSite() throws Exception {
    ApiEmitter emitter =
        new ApiEmitter(
            Path.of("blessed"), Path.of("release-history"), outDir, false, FIXED_GENERATED_AT);
    emitter.emit();
    v1 = outDir.resolve("v1");
    goldenRunner = GoldenTestRunner.forProductionCalendars();
  }

  @Test
  void writesExpectedFileSetForNyse() {
    assertTrue(Files.exists(v1.resolve("index.json")));
    Path calDir = v1.resolve("calendars/US-NYSE");
    assertTrue(Files.exists(calDir.resolve("manifest.json")));
    assertTrue(Files.exists(calDir.resolve("2026.json")));
    assertTrue(Files.exists(calDir.resolve("holidays.json")));
    assertTrue(Files.exists(calDir.resolve("all.json")));
    assertTrue(Files.exists(calDir.resolve("holidays.ics")));
    assertTrue(Files.exists(calDir.resolve("holidays-recent.ics")));
  }

  @Test
  void indexJsonListsExactlyTheMarketCalendars() throws Exception {
    JsonNode index = mapper.readTree(v1.resolve("index.json").toFile());
    JsonNode manifest = mapper.readTree(Path.of("blessed/manifest.json").toFile());

    List<String> expectedIds = new ArrayList<>();
    manifest.path("calendars").fieldNames().forEachRemaining(expectedIds::add);
    java.util.Collections.sort(expectedIds);

    List<String> actualIds =
        java.util.stream.StreamSupport.stream(index.path("calendars").spliterator(), false)
            .map(n -> n.path("id").asText())
            .sorted()
            .collect(Collectors.toList());

    // Every calendar in blessed/manifest.json currently has no explicit "kind", which per the
    // compatibility contract defaults to "market" — so all of them should appear.
    assertEquals(expectedIds, actualIds);
    assertEquals("1.0", index.path("schema_version").asText());
    assertEquals("v1", index.path("api_version").asText());
    assertTrue(index.has("release"));
  }

  @Test
  void yearFileHasRowsOfBlessed2026() throws Exception {
    List<Event> blessedEvents = new EventsCsvReader().read(Path.of("blessed/US-NYSE/events.csv"));
    List<Event> blessed2026 =
        blessedEvents.stream().filter(e -> e.date().getYear() == 2026).sorted().toList();

    JsonNode doc = mapper.readTree(v1.resolve("calendars/US-NYSE/2026.json").toFile());
    assertEquals("US-NYSE", doc.path("calendar_id").asText());
    assertEquals(blessed2026.size(), doc.path("event_count").asInt());
    assertEquals(blessed2026.size(), doc.path("events").size());

    for (int i = 0; i < blessed2026.size(); i++) {
      Event expected = blessed2026.get(i);
      JsonNode row = doc.path("events").get(i);
      assertEquals(expected.date().toString(), row.path("date").asText());
      assertEquals(expected.type().name(), row.path("type").asText());
      assertEquals(expected.description(), row.path("description").asText());
      assertEquals(expected.status().name(), row.path("status").asText());
    }
  }

  @Test
  void holidaysJsonHasNoWeekendRows() throws Exception {
    JsonNode doc = mapper.readTree(v1.resolve("calendars/US-NYSE/holidays.json").toFile());
    for (JsonNode row : doc.path("events")) {
      assertFalse(row.path("type").asText().equals(EventType.WEEKEND.name()));
    }
    assertTrue(doc.path("event_count").asInt() > 0);
  }

  @Test
  void pinnedReleaseFileExistsForV10_1_0() {
    Path pinned = v1.resolve("releases/10.1.0/calendars/US-NYSE/2026.json");
    assertTrue(Files.exists(pinned), "expected a pinned v10.1.0 file at " + pinned);
  }

  @Test
  void icsParsesAndCountsMatchHolidayCounts() throws Exception {
    byte[] bytes = Files.readAllBytes(v1.resolve("calendars/US-NYSE/holidays.ics"));
    String doc = new String(bytes, StandardCharsets.UTF_8);
    Ics ics = parseIcs(doc);

    JsonNode metadata = mapper.readTree(Path.of("blessed/US-NYSE/metadata.json").toFile());
    JsonNode countsByType = metadata.path("counts_by_type");
    int expectedHolidayCount =
        countsByType.path("CLOSED").asInt(0) + countsByType.path("EARLY_CLOSE").asInt(0);

    assertEquals(expectedHolidayCount, ics.veventCount());
    assertTrue(ics.everyVeventHasUidDtstartSummary());
  }

  // --- A small, local RFC 5545 parser, used only to validate emitted .ics files in tests. ---

  private record Ics(int veventCount, boolean everyVeventHasUidDtstartSummary) {}

  private static Ics parseIcs(String document) {
    assertTrue(document.contains("\r\n"), "expected CRLF line endings");
    String[] physicalLines = document.split("\r\n", -1);
    for (String line : physicalLines) {
      if (!line.isEmpty()) {
        assertTrue(
            line.getBytes(StandardCharsets.UTF_8).length <= 75, "line exceeds 75 octets: " + line);
      }
    }

    List<String> unfolded = new ArrayList<>();
    for (String line : physicalLines) {
      if (line.isEmpty()) {
        continue;
      }
      if ((line.startsWith(" ") || line.startsWith("\t")) && !unfolded.isEmpty()) {
        int last = unfolded.size() - 1;
        unfolded.set(last, unfolded.get(last) + line.substring(1));
      } else {
        unfolded.add(line);
      }
    }

    int veventCount = 0;
    boolean allValid = true;
    boolean hasUid = false;
    boolean hasDtstart = false;
    boolean hasSummary = false;
    boolean inVevent = false;
    for (String line : unfolded) {
      if (line.equals("BEGIN:VEVENT")) {
        inVevent = true;
        hasUid = false;
        hasDtstart = false;
        hasSummary = false;
      } else if (line.equals("END:VEVENT")) {
        veventCount++;
        allValid = allValid && hasUid && hasDtstart && hasSummary;
        inVevent = false;
      } else if (inVevent) {
        if (line.startsWith("UID:")) hasUid = true;
        if (line.startsWith("DTSTART")) hasDtstart = true;
        if (line.startsWith("SUMMARY:")) hasSummary = true;
      }
    }
    return new Ics(veventCount, allValid);
  }

  // --- Golden tests (fixed generated_at, so output is byte-for-byte reproducible). ---

  @Test
  void indexJsonMatchesGolden() throws Exception {
    String actual = Files.readString(v1.resolve("index.json"));
    goldenRunner.assertGoldenMatch("site/index.json", prettyPrint(actual));
  }

  @Test
  void nyse2026JsonMatchesGolden() throws Exception {
    String actual = Files.readString(v1.resolve("calendars/US-NYSE/2026.json"));
    goldenRunner.assertGoldenMatch("site/US-NYSE/2026.json", prettyPrint(actual));
  }

  @Test
  void nyseHolidaysRecentIcsMatchesGolden() throws Exception {
    List<String> allLines = Files.readAllLines(v1.resolve("calendars/US-NYSE/holidays-recent.ics"));
    String first40 = String.join("\n", allLines.subList(0, Math.min(40, allLines.size()))) + "\n";
    goldenRunner.assertGoldenMatch("site/US-NYSE/holidays-recent.ics", first40);
  }

  private String prettyPrint(String minifiedJson) throws Exception {
    Object value = mapper.readValue(minifiedJson, Object.class);
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n";
  }
}
