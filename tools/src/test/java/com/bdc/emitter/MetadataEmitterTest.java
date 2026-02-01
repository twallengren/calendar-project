package com.bdc.emitter;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.CalendarSpec;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataEmitterTest {

  private MetadataEmitter emitter;
  private ObjectMapper mapper;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    emitter = new MetadataEmitter();
    mapper = new ObjectMapper();
  }

  private ResolvedSpec createMinimalSpec(String id, String name) {
    CalendarSpec.Metadata metadata =
        name != null ? new CalendarSpec.Metadata(name, null, null) : null;
    return new ResolvedSpec(
        id, metadata, null, null, null, null, null, null, List.of("calendar:" + id));
  }

  @Test
  void emit_basicSpec_writesCorrectJson() throws Exception {
    ResolvedSpec spec = createMinimalSpec("TEST-CAL", "Test Calendar");
    List<Event> events =
        List.of(new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year", "test"));
    LocalDate from = LocalDate.of(2025, 1, 1);
    LocalDate to = LocalDate.of(2025, 12, 31);
    Path outputPath = tempDir.resolve("metadata.json");

    emitter.emit(spec, events, from, to, outputPath);

    assertTrue(Files.exists(outputPath));
    JsonNode json = mapper.readTree(outputPath.toFile());
    assertEquals("TEST-CAL", json.get("calendar_id").asText());
    assertEquals("Test Calendar", json.get("calendar_name").asText());
    assertEquals(1, json.get("event_count").asInt());
    assertEquals("2025-01-01", json.get("range_start").asText());
    assertEquals("2025-12-31", json.get("range_end").asText());
  }

  @Test
  void emit_withVersionInfo_includesSourceVersion() throws Exception {
    ResolvedSpec spec = createMinimalSpec("TEST", "Test");
    List<Event> events = List.of();
    LocalDate from = LocalDate.of(2025, 1, 1);
    LocalDate to = LocalDate.of(2025, 12, 31);
    Path outputPath = tempDir.resolve("metadata.json");

    emitter.emit(spec, events, from, to, outputPath, "abc123", "1.0.0");

    JsonNode json = mapper.readTree(outputPath.toFile());
    assertTrue(json.has("source_version"));
    assertEquals("abc123", json.get("source_version").get("git_sha").asText());
    assertEquals("1.0.0", json.get("source_version").get("semantic").asText());
  }

  @Test
  void emit_nullGitSha_omitsFromSourceVersion() throws Exception {
    ResolvedSpec spec = createMinimalSpec("TEST", "Test");
    List<Event> events = List.of();
    LocalDate from = LocalDate.of(2025, 1, 1);
    LocalDate to = LocalDate.of(2025, 12, 31);
    Path outputPath = tempDir.resolve("metadata.json");

    emitter.emit(spec, events, from, to, outputPath, null, "1.0.0");

    JsonNode json = mapper.readTree(outputPath.toFile());
    assertTrue(json.has("source_version"));
    assertFalse(json.get("source_version").has("git_sha"));
    assertEquals("1.0.0", json.get("source_version").get("semantic").asText());
  }

  @Test
  void emit_nullReleaseVersion_omitsFromSourceVersion() throws Exception {
    ResolvedSpec spec = createMinimalSpec("TEST", "Test");
    List<Event> events = List.of();
    LocalDate from = LocalDate.of(2025, 1, 1);
    LocalDate to = LocalDate.of(2025, 12, 31);
    Path outputPath = tempDir.resolve("metadata.json");

    emitter.emit(spec, events, from, to, outputPath, "abc123", null);

    JsonNode json = mapper.readTree(outputPath.toFile());
    assertTrue(json.has("source_version"));
    assertEquals("abc123", json.get("source_version").get("git_sha").asText());
    assertFalse(json.get("source_version").has("semantic"));
  }

  @Test
  void emit_nullMetadata_usesIdAsName() throws Exception {
    ResolvedSpec spec = createMinimalSpec("TEST-ID", null);
    List<Event> events = List.of();
    LocalDate from = LocalDate.of(2025, 1, 1);
    LocalDate to = LocalDate.of(2025, 12, 31);
    Path outputPath = tempDir.resolve("metadata.json");

    emitter.emit(spec, events, from, to, outputPath);

    JsonNode json = mapper.readTree(outputPath.toFile());
    assertEquals("TEST-ID", json.get("calendar_name").asText());
  }

  @Test
  void emit_countsByType_aggregatesCorrectly() throws Exception {
    ResolvedSpec spec = createMinimalSpec("TEST", "Test");
    List<Event> events =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Closed1", "test"),
            new Event(LocalDate.of(2025, 1, 2), EventType.CLOSED, "Closed2", "test"),
            new Event(LocalDate.of(2025, 1, 3), EventType.NOTABLE, "Notable", "test"));
    LocalDate from = LocalDate.of(2025, 1, 1);
    LocalDate to = LocalDate.of(2025, 12, 31);
    Path outputPath = tempDir.resolve("metadata.json");

    emitter.emit(spec, events, from, to, outputPath);

    JsonNode json = mapper.readTree(outputPath.toFile());
    JsonNode counts = json.get("counts_by_type");
    assertEquals(2, counts.get("CLOSED").asInt());
    assertEquals(1, counts.get("NOTABLE").asInt());
  }

  @Test
  void emitToString_returnsValidJson() throws Exception {
    ResolvedSpec spec = createMinimalSpec("TEST", "Test Calendar");
    List<Event> events =
        List.of(new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Test", "test"));
    LocalDate from = LocalDate.of(2025, 1, 1);
    LocalDate to = LocalDate.of(2025, 12, 31);

    String result = emitter.emitToString(spec, events, from, to);

    assertNotNull(result);
    JsonNode json = mapper.readTree(result);
    assertEquals("TEST", json.get("calendar_id").asText());
    assertEquals("Test Calendar", json.get("calendar_name").asText());
  }

  @Test
  void emit_emptyEventList_writesZeroCounts() throws Exception {
    ResolvedSpec spec = createMinimalSpec("TEST", "Test");
    List<Event> events = List.of();
    LocalDate from = LocalDate.of(2025, 1, 1);
    LocalDate to = LocalDate.of(2025, 12, 31);
    Path outputPath = tempDir.resolve("metadata.json");

    emitter.emit(spec, events, from, to, outputPath);

    JsonNode json = mapper.readTree(outputPath.toFile());
    assertEquals(0, json.get("event_count").asInt());
    assertTrue(json.get("counts_by_type").isEmpty());
  }

  @Test
  void emit_includesResolutionChain() throws Exception {
    ResolvedSpec spec = createMinimalSpec("TEST", "Test");
    List<Event> events = List.of();
    LocalDate from = LocalDate.of(2025, 1, 1);
    LocalDate to = LocalDate.of(2025, 12, 31);
    Path outputPath = tempDir.resolve("metadata.json");

    emitter.emit(spec, events, from, to, outputPath);

    JsonNode json = mapper.readTree(outputPath.toFile());
    assertTrue(json.has("resolution_chain"));
    assertTrue(json.get("resolution_chain").isArray());
  }

  @Test
  void emit_includesGeneratedAt() throws Exception {
    ResolvedSpec spec = createMinimalSpec("TEST", "Test");
    List<Event> events = List.of();
    LocalDate from = LocalDate.of(2025, 1, 1);
    LocalDate to = LocalDate.of(2025, 12, 31);
    Path outputPath = tempDir.resolve("metadata.json");

    emitter.emit(spec, events, from, to, outputPath);

    JsonNode json = mapper.readTree(outputPath.toFile());
    assertTrue(json.has("generated_at"));
    assertFalse(json.get("generated_at").asText().isEmpty());
  }
}
