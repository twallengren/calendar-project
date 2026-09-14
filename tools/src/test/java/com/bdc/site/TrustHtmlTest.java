package com.bdc.site;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrustHtmlTest {
  @Test
  void unresolvedCalendarShowsScheduledNativeEventWithoutOpenClaimOrNavigation() throws Exception {
    LocalDate date = LocalDate.of(2025, 9, 23);
    var event =
        new CalendarData.DayEvent(
            date, "CLOSED", "New year", "new_year", "native", null, null, "CONFIRMED");
    var assessment =
        new ObjectMapper()
            .readTree(
                """
        {"state":"UNKNOWN","scheduled_state":"CLOSED","effective_confidence":"UNKNOWN",
        "completeness":{"UNSCHEDULED_EXCEPTIONS":"INCOMPLETE"},"events":[{
        "description":"New year","nominal_native_date":{"chronology_id":"HEBREW","year":5786,"month_code":"TISHRI","day":1},
        "chronology_profile":"fixed-arithmetic-hebrew-civil","chronology_provider":"ICU4J 78.3","evidence_ids":["notice"]}]}
        """);
    var calendar =
        new CalendarData(
            "TEST",
            "Test",
            "Asia/Jerusalem",
            "XTAE",
            List.of(),
            new CalendarData.Coverage(date, date, date),
            Map.of(),
            Map.of(),
            "",
            List.of(2025),
            null,
            Map.of(2025, List.of(event)),
            Map.of(date, assessment));
    var layout =
        new PageLayout(
            new SiteContext(
                "/calendar-project/",
                "Calendars",
                "https://example.test/repo",
                "12.0.0",
                "sha",
                "2026-09-14",
                Instant.EPOCH));
    String page = new DatePageRenderer(layout).render(calendar, date, List.of(event));
    assertTrue(page.contains("Actual trading state is unknown"));
    assertTrue(page.contains("HEBREW 5786 TISHRI 1"));
    assertTrue(page.contains("ICU4J 78.3"));
    assertTrue(page.contains("notice"));
    assertTrue(page.contains("unresolved or outside coverage"));
    assertFalse(page.contains("Test is closed on"));
    assertThrows(IllegalArgumentException.class, () -> calendar.isBusinessDay(date));
    String grid = new YearGridRenderer().render(calendar, 2025, "");
    assertTrue(grid.contains("class=\"day unknown\" id=\"d2025-09-23\""));
    assertFalse(grid.contains("class=\"day open\" id=\"d2025-09-24\""));
  }
}
