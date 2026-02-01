package com.bdc.loader;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.CalendarSpec;
import com.bdc.model.ModuleSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlLoaderTest {

  private YamlLoader loader;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    loader = new YamlLoader();
  }

  @Test
  void loadCalendar_validFile_returnsCalendarSpec() throws Exception {
    Path calendarFile = tempDir.resolve("calendar.yaml");
    Files.writeString(
        calendarFile,
        """
        kind: calendar
        id: TEST-CAL
        metadata:
          name: Test Calendar
          description: A test calendar
        """);

    CalendarSpec spec = loader.loadCalendar(calendarFile);

    assertNotNull(spec);
    assertEquals("TEST-CAL", spec.id());
    assertEquals("Test Calendar", spec.metadata().name());
  }

  @Test
  void loadCalendar_missingFile_throwsException() {
    Path nonExistent = tempDir.resolve("missing.yaml");

    assertThrows(IOException.class, () -> loader.loadCalendar(nonExistent));
  }

  @Test
  void loadCalendar_malformedYaml_throwsException() throws Exception {
    Path malformed = tempDir.resolve("malformed.yaml");
    Files.writeString(malformed, """
        kind: calendar
        id: [invalid yaml
        """);

    assertThrows(IOException.class, () -> loader.loadCalendar(malformed));
  }

  @Test
  void loadCalendar_missingRequiredFields_throwsException() throws Exception {
    Path invalid = tempDir.resolve("invalid.yaml");
    Files.writeString(invalid, """
        kind: invalid_kind
        id: TEST
        """);

    // Jackson wraps the IllegalArgumentException in a ValueInstantiationException
    assertThrows(Exception.class, () -> loader.loadCalendar(invalid));
  }

  @Test
  void loadModule_validFile_returnsModuleSpec() throws Exception {
    Path moduleFile = tempDir.resolve("module.yaml");
    Files.writeString(
        moduleFile,
        """
        kind: module
        id: test_module
        event_sources: []
        """);

    ModuleSpec spec = loader.loadModule(moduleFile);

    assertNotNull(spec);
    assertEquals("test_module", spec.id());
  }

  @Test
  void loadModule_missingFile_throwsException() {
    Path nonExistent = tempDir.resolve("missing_module.yaml");

    assertThrows(IOException.class, () -> loader.loadModule(nonExistent));
  }

  @Test
  void detectKind_calendarFile_returnsCalendar() throws Exception {
    Path calendarFile = tempDir.resolve("calendar.yaml");
    Files.writeString(calendarFile, """
        kind: calendar
        id: TEST
        """);

    String kind = loader.detectKind(calendarFile);

    assertEquals("calendar", kind);
  }

  @Test
  void detectKind_moduleFile_returnsModule() throws Exception {
    Path moduleFile = tempDir.resolve("module.yaml");
    Files.writeString(moduleFile, """
        kind: module
        id: test
        """);

    String kind = loader.detectKind(moduleFile);

    assertEquals("module", kind);
  }

  @Test
  void detectKind_missingKindField_returnsNull() throws Exception {
    Path noKind = tempDir.resolve("nokind.yaml");
    Files.writeString(noKind, """
        id: test
        name: No Kind Field
        """);

    String kind = loader.detectKind(noKind);

    assertNull(kind);
  }

  @Test
  void loadCalendar_withEventSources_parsesCorrectly() throws Exception {
    Path calendarFile = tempDir.resolve("with-events.yaml");
    Files.writeString(
        calendarFile,
        """
        kind: calendar
        id: WITH-EVENTS
        metadata:
          name: Calendar with Events
        event_sources:
          - key: holiday1
            name: Holiday One
            default_classification: CLOSED
            rule:
              type: fixed_month_day
              month: 1
              day: 1
        """);

    CalendarSpec spec = loader.loadCalendar(calendarFile);

    assertNotNull(spec);
    assertEquals(1, spec.eventSources().size());
    assertEquals("holiday1", spec.eventSources().get(0).key());
  }

  @Test
  void getMapper_returnsConfiguredMapper() {
    assertNotNull(loader.getMapper());
  }
}
