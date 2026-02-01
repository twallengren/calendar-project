package com.bdc.diff;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiffReportFormatterTest {

  private DiffReportFormatter formatter;
  private ObjectMapper mapper;
  private LocalDate cutoffDate;

  @BeforeEach
  void setUp() {
    formatter = new DiffReportFormatter();
    mapper = new ObjectMapper();
    cutoffDate = LocalDate.of(2026, 1, 22);
  }

  private DiffReport createEmptyReport() {
    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "TEST",
        new CalendarDiff("TEST", DiffSeverity.NONE, List.of(), List.of(), List.of(), cutoffDate));
    return new DiffReport(DiffSeverity.NONE, 1, 0, Instant.now(), "1.0.0", "abc123", calendars);
  }

  private DiffReport createReportWithChanges() {
    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    CalendarDiff diff =
        new CalendarDiff(
            "TEST-CAL",
            DiffSeverity.MAJOR,
            List.of(EventDiff.added(LocalDate.of(2027, 1, 1), EventType.CLOSED, "New Holiday")),
            List.of(
                EventDiff.removed(LocalDate.of(2025, 7, 4), EventType.CLOSED, "Removed Holiday")),
            List.of(
                EventDiff.modified(
                    LocalDate.of(2025, 12, 25),
                    EventType.CLOSED,
                    EventType.EARLY_CLOSE,
                    "Christmas",
                    "Christmas (Early Close)")),
            cutoffDate);
    calendars.put("TEST-CAL", diff);
    return new DiffReport(DiffSeverity.MAJOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);
  }

  @Test
  void formatAsJson_emptyReport_returnsValidJson() throws Exception {
    DiffReport report = createEmptyReport();

    String json = formatter.formatAsJson(report);

    assertNotNull(json);
    JsonNode node = mapper.readTree(json);
    assertEquals("NONE", node.get("summary").get("overall_severity").asText());
    assertEquals(1, node.get("summary").get("total_calendars").asInt());
    assertEquals(0, node.get("summary").get("calendars_with_changes").asInt());
  }

  @Test
  void formatAsJson_withChanges_includesAllFields() throws Exception {
    DiffReport report = createReportWithChanges();

    String json = formatter.formatAsJson(report);

    JsonNode node = mapper.readTree(json);
    assertEquals("MAJOR", node.get("summary").get("overall_severity").asText());
    assertEquals("1.0.0", node.get("summary").get("blessed_version").asText());
    assertEquals("abc123", node.get("summary").get("current_sha").asText());

    JsonNode calendarNode = node.get("calendars").get("TEST-CAL");
    assertNotNull(calendarNode);
    assertEquals("MAJOR", calendarNode.get("severity").asText());
  }

  @Test
  void formatAsJson_additions_hasNullOldType() throws Exception {
    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "TEST",
        new CalendarDiff(
            "TEST",
            DiffSeverity.MINOR,
            List.of(EventDiff.added(LocalDate.of(2027, 1, 1), EventType.CLOSED, "Added")),
            List.of(),
            List.of(),
            cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MINOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String json = formatter.formatAsJson(report);

    JsonNode node = mapper.readTree(json);
    JsonNode addition = node.get("calendars").get("TEST").get("additions").get(0);
    assertFalse(addition.has("old_type"));
    assertEquals("CLOSED", addition.get("new_type").asText());
  }

  @Test
  void formatAsJson_removals_hasNullNewType() throws Exception {
    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "TEST",
        new CalendarDiff(
            "TEST",
            DiffSeverity.MAJOR,
            List.of(),
            List.of(EventDiff.removed(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Removed")),
            List.of(),
            cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MAJOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String json = formatter.formatAsJson(report);

    JsonNode node = mapper.readTree(json);
    JsonNode removal = node.get("calendars").get("TEST").get("removals").get(0);
    assertEquals("CLOSED", removal.get("old_type").asText());
    assertFalse(removal.has("new_type"));
  }

  @Test
  void formatAsMarkdown_emptyReport_showsNoChanges() {
    DiffReport report = createEmptyReport();

    String markdown = formatter.formatAsMarkdown(report);

    assertTrue(markdown.contains("Calendar Diff Report"));
    assertTrue(markdown.contains("NONE"));
    assertTrue(markdown.contains(":white_check_mark:"));
  }

  @Test
  void formatAsMarkdown_withChanges_includesSummaryTable() {
    DiffReport report = createReportWithChanges();

    String markdown = formatter.formatAsMarkdown(report);

    assertTrue(markdown.contains("### Summary"));
    assertTrue(markdown.contains("| Calendar | Severity | Added | Removed | Modified |"));
    assertTrue(markdown.contains("TEST-CAL"));
    assertTrue(markdown.contains("MAJOR"));
  }

  @Test
  void formatAsMarkdown_severityIcons_mapCorrectly() {
    // Test NONE severity
    DiffReport noneReport = createEmptyReport();
    String noneMarkdown = formatter.formatAsMarkdown(noneReport);
    assertTrue(noneMarkdown.contains(":white_check_mark:"));

    // Test MINOR severity
    Map<String, CalendarDiff> minorCalendars = new LinkedHashMap<>();
    minorCalendars.put(
        "TEST",
        new CalendarDiff(
            "TEST",
            DiffSeverity.MINOR,
            List.of(EventDiff.added(LocalDate.of(2027, 1, 1), EventType.CLOSED, "Future")),
            List.of(),
            List.of(),
            cutoffDate));
    DiffReport minorReport =
        new DiffReport(DiffSeverity.MINOR, 1, 1, Instant.now(), "1.0.0", "abc123", minorCalendars);
    String minorMarkdown = formatter.formatAsMarkdown(minorReport);
    assertTrue(minorMarkdown.contains(":yellow_circle:"));

    // Test MAJOR severity
    DiffReport majorReport = createReportWithChanges();
    String majorMarkdown = formatter.formatAsMarkdown(majorReport);
    assertTrue(majorMarkdown.contains(":red_circle:"));
  }

  @Test
  void formatAsMarkdown_majorSeverity_includesApprovalNote() {
    DiffReport report = createReportWithChanges();

    String markdown = formatter.formatAsMarkdown(report);

    assertTrue(markdown.contains("MAJOR changes require explicit approval"));
    assertTrue(markdown.contains("calendar-change-approved"));
  }

  @Test
  void formatAsMarkdown_minorSeverity_includesConsiderNote() {
    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "TEST",
        new CalendarDiff(
            "TEST",
            DiffSeverity.MINOR,
            List.of(EventDiff.added(LocalDate.of(2027, 1, 1), EventType.CLOSED, "Future Event")),
            List.of(),
            List.of(),
            cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MINOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String markdown = formatter.formatAsMarkdown(report);

    assertTrue(markdown.contains("MINOR changes detected"));
    assertTrue(markdown.contains("Consider adding"));
  }

  @Test
  void formatAsMarkdown_historicalChanges_markedCorrectly() {
    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    // Use a date before the cutoff (2026-01-22) to make it historical
    calendars.put(
        "TEST",
        new CalendarDiff(
            "TEST",
            DiffSeverity.MAJOR,
            List.of(EventDiff.added(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Historical")),
            List.of(),
            List.of(),
            cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MAJOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String markdown = formatter.formatAsMarkdown(report);

    assertTrue(markdown.contains("Historical?"));
    assertTrue(markdown.contains("| Yes |"));
  }

  @Test
  void formatAsMarkdown_removedEvents_showWarning() {
    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "TEST",
        new CalendarDiff(
            "TEST",
            DiffSeverity.MAJOR,
            List.of(),
            List.of(EventDiff.removed(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Removed")),
            List.of(),
            cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MAJOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String markdown = formatter.formatAsMarkdown(report);

    assertTrue(markdown.contains("Removed Events :warning:"));
  }

  @Test
  void formatAsMarkdown_modifiedEvents_showDetails() {
    DiffReport report = createReportWithChanges();

    String markdown = formatter.formatAsMarkdown(report);

    assertTrue(markdown.contains("Modified Events"));
    assertTrue(markdown.contains("Old Type"));
    assertTrue(markdown.contains("New Type"));
    assertTrue(markdown.contains("CLOSED"));
    assertTrue(markdown.contains("EARLY_CLOSE"));
  }

  @Test
  void formatAsJson_handlesMultipleCalendars() throws Exception {
    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "CAL1",
        new CalendarDiff("CAL1", DiffSeverity.NONE, List.of(), List.of(), List.of(), cutoffDate));
    calendars.put(
        "CAL2",
        new CalendarDiff(
            "CAL2",
            DiffSeverity.MINOR,
            List.of(EventDiff.added(LocalDate.of(2027, 1, 1), EventType.CLOSED, "Event")),
            List.of(),
            List.of(),
            cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MINOR, 2, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String json = formatter.formatAsJson(report);

    JsonNode node = mapper.readTree(json);
    assertEquals(2, node.get("summary").get("total_calendars").asInt());
    assertEquals(1, node.get("summary").get("calendars_with_changes").asInt());
    assertTrue(node.get("calendars").has("CAL1"));
    assertTrue(node.get("calendars").has("CAL2"));
  }

  @Test
  void constructor_withCustomMaxDiffs_usesCustomValue() {
    DiffReportFormatter customFormatter = new DiffReportFormatter(5);

    // Create a report with more additions than the custom limit
    List<EventDiff> additions =
        List.of(
            EventDiff.added(LocalDate.of(2027, 1, 1), EventType.CLOSED, "Event 1"),
            EventDiff.added(LocalDate.of(2027, 1, 2), EventType.CLOSED, "Event 2"),
            EventDiff.added(LocalDate.of(2027, 1, 3), EventType.CLOSED, "Event 3"),
            EventDiff.added(LocalDate.of(2027, 1, 4), EventType.CLOSED, "Event 4"),
            EventDiff.added(LocalDate.of(2027, 1, 5), EventType.CLOSED, "Event 5"),
            EventDiff.added(LocalDate.of(2027, 1, 6), EventType.CLOSED, "Event 6"),
            EventDiff.added(LocalDate.of(2027, 1, 7), EventType.CLOSED, "Event 7"),
            EventDiff.added(LocalDate.of(2027, 1, 8), EventType.CLOSED, "Event 8"));

    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "TEST",
        new CalendarDiff("TEST", DiffSeverity.MINOR, additions, List.of(), List.of(), cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MINOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String markdown = customFormatter.formatAsMarkdown(report);

    // Should show exactly 5 events and indicate 3 more
    assertTrue(markdown.contains("Event 1"));
    assertTrue(markdown.contains("Event 5"));
    assertFalse(markdown.contains("Event 6"));
    assertTrue(markdown.contains("...and 3 more added events"));
  }

  @Test
  void formatAsMarkdown_truncatesAdditions_showsRemainingCount() {
    // Use default formatter (15 max per section)
    List<EventDiff> additions = new java.util.ArrayList<>();
    for (int i = 1; i <= 20; i++) {
      additions.add(EventDiff.added(LocalDate.of(2027, 1, i), EventType.CLOSED, "Event " + i));
    }

    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "TEST",
        new CalendarDiff("TEST", DiffSeverity.MINOR, additions, List.of(), List.of(), cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MINOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String markdown = formatter.formatAsMarkdown(report);

    // Should show 15 events (default) and indicate 5 more
    assertTrue(markdown.contains("Event 1"));
    assertTrue(markdown.contains("Event 15"));
    assertFalse(markdown.contains("Event 16"));
    assertTrue(markdown.contains("...and 5 more added events"));
  }

  @Test
  void formatAsMarkdown_truncatesRemovals_showsRemainingCount() {
    DiffReportFormatter customFormatter = new DiffReportFormatter(3);

    List<EventDiff> removals =
        List.of(
            EventDiff.removed(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Removed 1"),
            EventDiff.removed(LocalDate.of(2025, 1, 2), EventType.CLOSED, "Removed 2"),
            EventDiff.removed(LocalDate.of(2025, 1, 3), EventType.CLOSED, "Removed 3"),
            EventDiff.removed(LocalDate.of(2025, 1, 4), EventType.CLOSED, "Removed 4"),
            EventDiff.removed(LocalDate.of(2025, 1, 5), EventType.CLOSED, "Removed 5"));

    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "TEST",
        new CalendarDiff("TEST", DiffSeverity.MAJOR, List.of(), removals, List.of(), cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MAJOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String markdown = customFormatter.formatAsMarkdown(report);

    assertTrue(markdown.contains("Removed 1"));
    assertTrue(markdown.contains("Removed 3"));
    assertFalse(markdown.contains("Removed 4"));
    assertTrue(markdown.contains("...and 2 more removed events"));
  }

  @Test
  void formatAsMarkdown_truncatesModifications_showsRemainingCount() {
    DiffReportFormatter customFormatter = new DiffReportFormatter(2);

    List<EventDiff> modifications =
        List.of(
            EventDiff.modified(
                LocalDate.of(2025, 1, 1),
                EventType.CLOSED,
                EventType.EARLY_CLOSE,
                "Mod 1",
                "Mod 1 New"),
            EventDiff.modified(
                LocalDate.of(2025, 1, 2),
                EventType.CLOSED,
                EventType.EARLY_CLOSE,
                "Mod 2",
                "Mod 2 New"),
            EventDiff.modified(
                LocalDate.of(2025, 1, 3),
                EventType.CLOSED,
                EventType.EARLY_CLOSE,
                "Mod 3",
                "Mod 3 New"),
            EventDiff.modified(
                LocalDate.of(2025, 1, 4),
                EventType.CLOSED,
                EventType.EARLY_CLOSE,
                "Mod 4",
                "Mod 4 New"));

    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "TEST",
        new CalendarDiff(
            "TEST", DiffSeverity.MAJOR, List.of(), List.of(), modifications, cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MAJOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String markdown = customFormatter.formatAsMarkdown(report);

    assertTrue(markdown.contains("Mod 1"));
    assertTrue(markdown.contains("Mod 2"));
    assertFalse(markdown.contains("Mod 3"));
    assertTrue(markdown.contains("...and 2 more modified events"));
  }

  @Test
  void formatAsMarkdown_noTruncation_whenBelowLimit() {
    DiffReportFormatter customFormatter = new DiffReportFormatter(10);

    List<EventDiff> additions =
        List.of(
            EventDiff.added(LocalDate.of(2027, 1, 1), EventType.CLOSED, "Event 1"),
            EventDiff.added(LocalDate.of(2027, 1, 2), EventType.CLOSED, "Event 2"),
            EventDiff.added(LocalDate.of(2027, 1, 3), EventType.CLOSED, "Event 3"));

    Map<String, CalendarDiff> calendars = new LinkedHashMap<>();
    calendars.put(
        "TEST",
        new CalendarDiff("TEST", DiffSeverity.MINOR, additions, List.of(), List.of(), cutoffDate));
    DiffReport report =
        new DiffReport(DiffSeverity.MINOR, 1, 1, Instant.now(), "1.0.0", "abc123", calendars);

    String markdown = customFormatter.formatAsMarkdown(report);

    // All events should be shown
    assertTrue(markdown.contains("Event 1"));
    assertTrue(markdown.contains("Event 2"));
    assertTrue(markdown.contains("Event 3"));
    // No truncation message
    assertFalse(markdown.contains("...and"));
    assertFalse(markdown.contains("more added events"));
  }
}
