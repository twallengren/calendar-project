package com.bdc.loader;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.CalendarSpec;
import com.bdc.model.ModuleSpec;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
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
  void loadCalendar_invalidKind_throwsValueInstantiationException() throws Exception {
    Path invalid = tempDir.resolve("invalid.yaml");
    Files.writeString(invalid, """
        kind: invalid_kind
        id: TEST
        """);

    ValueInstantiationException ex =
        assertThrows(ValueInstantiationException.class, () -> loader.loadCalendar(invalid));

    // Verify the root cause is the validation error from CalendarSpec
    Throwable cause = ex.getCause();
    assertInstanceOf(IllegalArgumentException.class, cause);
    assertTrue(cause.getMessage().contains("kind must be 'calendar'"));
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

  @Test
  void loadModule_withActiveYears_singleYears() throws Exception {
    Path moduleFile = tempDir.resolve("active-years.yaml");
    Files.writeString(
        moduleFile,
        """
        kind: module
        id: test_module
        event_sources:
          - key: election_day
            name: Election Day
            default_classification: CLOSED
            active_years:
              - 1972
              - 1976
              - 1980
            rule:
              type: fixed_month_day
              month: 11
              day: 5
        """);

    ModuleSpec spec = loader.loadModule(moduleFile);

    assertNotNull(spec);
    assertEquals(1, spec.eventSources().size());
    var source = spec.eventSources().get(0);
    assertNotNull(source.activeYears());
    assertEquals(3, source.activeYears().size());
    assertEquals(Integer.valueOf(1972), source.activeYears().get(0).start());
    assertEquals(Integer.valueOf(1972), source.activeYears().get(0).end());
    assertEquals(Integer.valueOf(1976), source.activeYears().get(1).start());
    assertEquals(Integer.valueOf(1980), source.activeYears().get(2).start());
  }

  @Test
  void loadModule_withActiveYears_rangesAndYears() throws Exception {
    Path moduleFile = tempDir.resolve("active-years-mixed.yaml");
    Files.writeString(
        moduleFile,
        """
        kind: module
        id: test_module
        event_sources:
          - key: election_day
            name: Election Day
            default_classification: CLOSED
            active_years:
              - [null, 1968]
              - 1972
              - 1976
              - [1990, 2000]
            rule:
              type: fixed_month_day
              month: 11
              day: 5
        """);

    ModuleSpec spec = loader.loadModule(moduleFile);

    assertNotNull(spec);
    var source = spec.eventSources().get(0);
    assertNotNull(source.activeYears());
    assertEquals(4, source.activeYears().size());

    // [null, 1968] - from inception through 1968
    assertNull(source.activeYears().get(0).start());
    assertEquals(Integer.valueOf(1968), source.activeYears().get(0).end());

    // 1972
    assertEquals(Integer.valueOf(1972), source.activeYears().get(1).start());
    assertEquals(Integer.valueOf(1972), source.activeYears().get(1).end());

    // 1976
    assertEquals(Integer.valueOf(1976), source.activeYears().get(2).start());

    // [1990, 2000]
    assertEquals(Integer.valueOf(1990), source.activeYears().get(3).start());
    assertEquals(Integer.valueOf(2000), source.activeYears().get(3).end());
  }

  @Test
  void loadModule_withRelativeToReference_weekdayOffset() throws Exception {
    Path moduleFile = tempDir.resolve("weekday-offset.yaml");
    Files.writeString(
        moduleFile,
        """
        kind: module
        id: test_module
        event_sources:
          - key: election_day
            name: Election Day
            default_classification: CLOSED
            rule:
              type: relative_to_reference
              key: election_day
              name: Election Day
              reference_month: 11
              reference_day: 1
              offset_weekday:
                weekday: TUESDAY
                nth: 1
                direction: AFTER
        """);

    ModuleSpec spec = loader.loadModule(moduleFile);

    assertNotNull(spec);
    var source = spec.eventSources().get(0);
    assertInstanceOf(com.bdc.model.Rule.RelativeToReference.class, source.rule());
    var rule = (com.bdc.model.Rule.RelativeToReference) source.rule();

    assertTrue(rule.usesFixedReference());
    assertFalse(rule.usesNamedReference());
    assertTrue(rule.usesWeekdayOffset());

    assertEquals(Integer.valueOf(11), rule.referenceMonth());
    assertEquals(Integer.valueOf(1), rule.referenceDay());
    assertNotNull(rule.offsetWeekday());
    assertEquals(java.time.DayOfWeek.TUESDAY, rule.offsetWeekday().weekday());
    assertEquals(1, rule.offsetWeekday().nth());
    assertEquals(com.bdc.model.Rule.OffsetDirection.AFTER, rule.offsetWeekday().direction());
  }
}
