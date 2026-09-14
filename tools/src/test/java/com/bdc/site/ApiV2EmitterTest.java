package com.bdc.site;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiV2EmitterTest {
  @TempDir Path temp;
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void explicitUnknownAndRelativeLinksSurviveArtifactToWire() throws Exception {
    Path blessed = temp.resolve("blessed");
    Path calendar = blessed.resolve("TEST");
    Files.createDirectories(calendar);
    json.writeValue(
        blessed.resolve("manifest.json").toFile(),
        Map.of(
            "release_version",
            Map.of("semantic", "12.0.0"),
            "calendars",
            Map.of("TEST", Map.of("kind", "market"))));
    json.writeValue(
        calendar.resolve("metadata.json").toFile(),
        Map.of(
            "calendar_id",
            "TEST",
            "range_start",
            "2026-09-14",
            "range_end",
            "2026-09-15",
            "coverage",
            Map.of(
                "quality",
                List.of(
                    Map.of(
                        "scope",
                        "SCHEDULED_CLOSURES",
                        "from",
                        "2026-09-14",
                        "to",
                        "2026-09-15",
                        "quality",
                        "INCOMPLETE",
                        "evidence_ids",
                        List.of("gap"))))));
    Files.writeString(
        calendar.resolve("events.csv"),
        "date,type,description,key,source_module,observed_from,close_time,status\n2026-09-14,CLOSED,Holiday,test,fixture,,,CONFIRMED\n");
    Path out = temp.resolve("site");
    new ApiV2Emitter().emit(blessed, temp.resolve("history"), out, false, Instant.EPOCH);
    var index = json.readTree(out.resolve("v2/index.json").toFile());
    assertEquals("2.0", index.path("schema_version").asText());
    for (String prefix : List.of("/", "/calendar-project/")) {
      var indexUri = java.net.URI.create("https://example.test" + prefix + "v2/index.json");
      var manifestUri = indexUri.resolve(index.path("calendars").get(0).path("href").asText());
      var manifest =
          json.readTree(out.resolve(manifestUri.getPath().substring(prefix.length())).toFile());
      assertTrue(manifest.path("explicit_quality").asBoolean());
      var yearUri = manifestUri.resolve(manifest.path("years").get(0).path("href").asText());
      var days =
          json.readTree(out.resolve(yearUri.getPath().substring(prefix.length())).toFile())
              .path("days");
      assertEquals(2, days.size());
      assertEquals("UNKNOWN", days.get(0).path("state").asText());
      assertEquals("CLOSED", days.get(0).path("scheduled_state").asText());
      assertEquals("OPEN", days.get(1).path("scheduled_state").asText());
      assertEquals("UNKNOWN", days.get(1).path("effective_confidence").asText());
      assertEquals("CONFIRMED", days.get(0).path("events").get(0).path("raw_status").asText());
      assertEquals("UNKNOWN", days.get(0).path("events").get(0).path("effective_status").asText());
    }
  }
}
