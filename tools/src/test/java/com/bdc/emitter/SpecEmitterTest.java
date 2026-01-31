package com.bdc.emitter;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.CalendarSpec;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecEmitterTest {

  private static SpecRegistry prodRegistry;
  private static SpecResolver prodResolver;
  private static SpecRegistry testRegistry;
  private static SpecResolver testResolver;

  @TempDir Path tempDir;

  @BeforeAll
  static void setupRegistry() throws Exception {
    prodRegistry = new SpecRegistry();
    prodRegistry.loadCalendarsFromDirectory(Path.of("calendars"));
    prodRegistry.loadModulesFromDirectory(Path.of("modules"));
    prodResolver = new SpecResolver(prodRegistry);

    testRegistry = new SpecRegistry();
    testRegistry.loadCalendarsFromDirectory(Path.of("tools/src/test/resources/test-calendars/calendars"));
    testRegistry.loadModulesFromDirectory(Path.of("tools/src/test/resources/test-calendars/modules"));
    testResolver = new SpecResolver(testRegistry);
  }

  @Test
  void emitCalendarSpec_producesValidYaml() throws Exception {
    CalendarSpec spec = prodRegistry.getCalendar("US-NYSE").orElseThrow();
    SpecEmitter emitter = new SpecEmitter();

    Path outputPath = tempDir.resolve("calendar.yaml");
    emitter.emitCalendarSpec(spec, outputPath);

    String content = Files.readString(outputPath);

    // Verify basic structure
    assertTrue(content.contains("kind: calendar"), "Should contain kind");
    assertTrue(content.contains("id: US-NYSE"), "Should contain id");
    assertTrue(content.contains("metadata:"), "Should contain metadata section");
    assertTrue(content.contains("uses:"), "Should contain uses section");
  }

  @Test
  void emitResolvedSpec_producesValidYaml() throws Exception {
    ResolvedSpec spec = prodResolver.resolve("US-NYSE");
    SpecEmitter emitter = new SpecEmitter();

    Path outputPath = tempDir.resolve("resolved.yaml");
    emitter.emitResolvedSpec(spec, outputPath);

    String content = Files.readString(outputPath);

    // Verify basic structure
    assertTrue(content.contains("kind: resolved_calendar"), "Should contain kind");
    assertTrue(content.contains("id: US-NYSE"), "Should contain id");
    assertTrue(content.contains("metadata:"), "Should contain metadata section");
    assertTrue(content.contains("weekend_policy:"), "Should contain weekend_policy section");
    assertTrue(content.contains("resolution_chain:"), "Should contain resolution_chain");
    assertTrue(content.contains("event_sources:"), "Should contain event_sources");
  }

  @Test
  void emitResolvedSpec_containsWeekendDays() throws Exception {
    ResolvedSpec spec = prodResolver.resolve("US-MARKET-BASE");
    SpecEmitter emitter = new SpecEmitter();

    Path outputPath = tempDir.resolve("resolved.yaml");
    emitter.emitResolvedSpec(spec, outputPath);

    String content = Files.readString(outputPath);

    assertTrue(content.contains("SATURDAY"), "Should contain SATURDAY");
    assertTrue(content.contains("SUNDAY"), "Should contain SUNDAY");
  }

  @Test
  void emitResolvedSpec_containsEventSources() throws Exception {
    ResolvedSpec spec = prodResolver.resolve("US-MARKET-BASE");
    SpecEmitter emitter = new SpecEmitter();

    Path outputPath = tempDir.resolve("resolved.yaml");
    emitter.emitResolvedSpec(spec, outputPath);

    String content = Files.readString(outputPath);

    // Verify some known holidays are present
    assertTrue(content.contains("new_years_day"), "Should contain New Year's Day");
    assertTrue(content.contains("christmas"), "Should contain Christmas");
    assertTrue(content.contains("thanksgiving"), "Should contain Thanksgiving");
  }

  @Test
  void emitResolvedSpec_containsReferences() throws Exception {
    ResolvedSpec spec = prodResolver.resolve("US-NYSE");
    SpecEmitter emitter = new SpecEmitter();

    Path outputPath = tempDir.resolve("resolved.yaml");
    emitter.emitResolvedSpec(spec, outputPath);

    String content = Files.readString(outputPath);

    // US-NYSE includes Good Friday which uses Easter reference
    assertTrue(content.contains("references:"), "Should contain references section");
    assertTrue(content.contains("easter"), "Should contain easter reference");
    assertTrue(content.contains("EASTER_WESTERN"), "Should contain Easter formula");
  }

  @Test
  void emitResolvedSpec_containsDeltas() throws Exception {
    ResolvedSpec spec = testResolver.resolve("WITH-DELTAS");
    SpecEmitter emitter = new SpecEmitter();

    Path outputPath = tempDir.resolve("resolved.yaml");
    emitter.emitResolvedSpec(spec, outputPath);

    String content = Files.readString(outputPath);

    // WITH-DELTAS has deltas for testing
    assertTrue(content.contains("deltas:"), "Should contain deltas section");
    assertTrue(content.contains("action: add"), "Should contain add action");
  }

  @Test
  void emitCalendarSpec_isDeterministic() throws Exception {
    CalendarSpec spec = prodRegistry.getCalendar("US-NYSE").orElseThrow();
    SpecEmitter emitter = new SpecEmitter();

    Path path1 = tempDir.resolve("calendar1.yaml");
    Path path2 = tempDir.resolve("calendar2.yaml");

    emitter.emitCalendarSpec(spec, path1);
    emitter.emitCalendarSpec(spec, path2);

    assertEquals(
        Files.readString(path1), Files.readString(path2), "Output should be deterministic");
  }

  @Test
  void emitResolvedSpec_isDeterministic() throws Exception {
    ResolvedSpec spec = prodResolver.resolve("US-NYSE");
    SpecEmitter emitter = new SpecEmitter();

    Path path1 = tempDir.resolve("resolved1.yaml");
    Path path2 = tempDir.resolve("resolved2.yaml");

    emitter.emitResolvedSpec(spec, path1);
    emitter.emitResolvedSpec(spec, path2);

    assertEquals(
        Files.readString(path1), Files.readString(path2), "Output should be deterministic");
  }
}
