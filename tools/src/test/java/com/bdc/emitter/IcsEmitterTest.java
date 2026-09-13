package com.bdc.emitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IcsEmitterTest {

  private final IcsEmitter emitter = new IcsEmitter();

  private static Event closed(LocalDate date, String key, String description) {
    return new Event(
        date,
        EventType.CLOSED,
        description,
        "test",
        key,
        "module:" + key,
        null,
        null,
        EventStatus.CONFIRMED);
  }

  private static Event earlyClose(LocalDate date, String key, String description, LocalTime time) {
    return new Event(
        date,
        EventType.EARLY_CLOSE,
        description,
        "test",
        key,
        "module:" + key,
        null,
        time,
        EventStatus.CONFIRMED);
  }

  private static Event weekend(LocalDate date) {
    return new Event(
        date, EventType.WEEKEND, "Sunday", "test", "weekend", "weekend_policy", null, null, null);
  }

  /** Minimal RFC 5545 parser: unfolds continuation lines and groups VEVENT blocks. */
  private record Ics(List<String> unfoldedLines, List<Map<String, String>> vevents) {}

  private static Ics parse(String document) {
    assertTrue(document.contains("\r\n"), "should use CRLF line endings");
    String[] physicalLines = document.split("\r\n", -1);
    for (String line : physicalLines) {
      if (line.isEmpty()) {
        continue;
      }
      int octets = line.getBytes(StandardCharsets.UTF_8).length;
      assertTrue(octets <= 75, "line exceeds 75 octets (" + octets + "): " + line);
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

    List<Map<String, String>> vevents = new ArrayList<>();
    Map<String, String> current = null;
    for (String line : unfolded) {
      if (line.equals("BEGIN:VEVENT")) {
        current = new java.util.LinkedHashMap<>();
      } else if (line.equals("END:VEVENT")) {
        vevents.add(current);
        current = null;
      } else if (current != null) {
        int colon = line.indexOf(':');
        int semicolon = line.indexOf(';');
        int sep = semicolon >= 0 && semicolon < colon ? semicolon : colon;
        if (sep > 0) {
          current.put(line.substring(0, sep), line.substring(colon + 1));
        }
      }
    }
    return new Ics(unfolded, vevents);
  }

  @Test
  void emit_wrapsInVcalendarWithRequiredHeaders() {
    String doc =
        emitter.emit(
            "US-NYSE", "NYSE Trading Calendar", "11.0.0", LocalDate.of(2026, 2, 16), List.of());
    Ics ics = parse(doc);
    assertTrue(ics.unfoldedLines().contains("BEGIN:VCALENDAR"));
    assertTrue(ics.unfoldedLines().contains("VERSION:2.0"));
    assertTrue(ics.unfoldedLines().contains("PRODID:-//bdc-calendars//11.0.0//EN"));
    assertTrue(ics.unfoldedLines().contains("X-WR-CALNAME:NYSE Trading Calendar holidays"));
    assertTrue(ics.unfoldedLines().contains("END:VCALENDAR"));
  }

  @Test
  void emit_onlyClosedAndEarlyCloseBecomeVevents() {
    List<Event> events =
        List.of(
            weekend(LocalDate.of(2026, 1, 4)),
            closed(LocalDate.of(2026, 1, 1), "new_years_day", "New Year's Day"),
            earlyClose(
                LocalDate.of(2026, 7, 3),
                "independence_day_eve",
                "Day before Independence Day",
                LocalTime.of(13, 0)));

    String doc =
        emitter.emit(
            "US-NYSE", "NYSE Trading Calendar", "11.0.0", LocalDate.of(2026, 2, 16), events);
    Ics ics = parse(doc);

    assertEquals(2, ics.vevents().size());
  }

  @Test
  void emit_veventsHaveUidDtstartAndSummary() {
    List<Event> events =
        List.of(closed(LocalDate.of(2026, 1, 1), "new_years_day", "New Year's Day"));
    String doc =
        emitter.emit(
            "US-NYSE", "NYSE Trading Calendar", "11.0.0", LocalDate.of(2026, 2, 16), events);
    Ics ics = parse(doc);

    assertEquals(1, ics.vevents().size());
    Map<String, String> vevent = ics.vevents().get(0);
    assertTrue(vevent.containsKey("UID"));
    assertTrue(vevent.containsKey("DTSTART"));
    assertTrue(vevent.containsKey("SUMMARY"));
    assertEquals("us-nyse-2026-01-01-new_years_day@bdc-calendars", vevent.get("UID"));
    assertEquals("20260101", vevent.get("DTSTART"));
    assertEquals("20260102", vevent.get("DTEND"));
    assertEquals("NYSE Trading Calendar closed: New Year's Day", vevent.get("SUMMARY"));
    assertTrue(vevent.get("DESCRIPTION").contains("Key: new_years_day"));
  }

  @Test
  void emit_earlyCloseSummaryIncludesCloseTime() {
    List<Event> events =
        List.of(
            earlyClose(
                LocalDate.of(2026, 7, 3),
                "independence_day_eve",
                "Day before Independence Day",
                LocalTime.of(13, 0)));
    String doc =
        emitter.emit(
            "US-NYSE", "NYSE Trading Calendar", "11.0.0", LocalDate.of(2026, 2, 16), events);
    Ics ics = parse(doc);

    assertEquals(
        "NYSE Trading Calendar early close 13:00: Day before Independence Day",
        ics.vevents().get(0).get("SUMMARY"));
  }

  @Test
  void emit_dtstampIsFixedToGenerationDateRegardlessOfEventDate() {
    List<Event> events =
        List.of(
            closed(LocalDate.of(1950, 1, 1), "new_years_day", "New Year's Day"),
            closed(LocalDate.of(2030, 12, 25), "christmas", "Christmas"));
    String doc =
        emitter.emit(
            "US-NYSE", "NYSE Trading Calendar", "11.0.0", LocalDate.of(2026, 2, 16), events);
    Ics ics = parse(doc);

    for (String line : ics.unfoldedLines()) {
      if (line.startsWith("DTSTAMP:")) {
        assertEquals("DTSTAMP:20260216T000000Z", line);
      }
    }
  }

  @Test
  void emit_longSummaryIsFoldedAndUnfoldsBack() {
    String longDescription =
        "A Very Long Holiday Description That Should Require Line Folding Because It Exceeds"
            + " Seventy Five Octets By Quite A Margin";
    List<Event> events = List.of(closed(LocalDate.of(2026, 1, 1), "long_key", longDescription));
    String doc =
        emitter.emit(
            "US-NYSE", "NYSE Trading Calendar", "11.0.0", LocalDate.of(2026, 2, 16), events);

    assertTrue(doc.contains("\r\n "), "expected a folded continuation line");
    Ics ics = parse(doc);
    assertEquals(
        "NYSE Trading Calendar closed: " + longDescription, ics.vevents().get(0).get("SUMMARY"));
  }

  @Test
  void emit_escapesSpecialCharactersInText() {
    List<Event> events =
        List.of(closed(LocalDate.of(2026, 1, 1), "k", "Special, chars; here\\and a backslash"));
    String doc =
        emitter.emit(
            "US-NYSE", "NYSE Trading Calendar", "11.0.0", LocalDate.of(2026, 2, 16), events);
    Ics ics = parse(doc);

    assertEquals(
        "NYSE Trading Calendar closed: Special\\, chars\\; here\\\\and a backslash",
        ics.vevents().get(0).get("SUMMARY"));
  }

  @Test
  void emit_emptyEventsProducesNoVevents() {
    String doc =
        emitter.emit(
            "US-NYSE", "NYSE Trading Calendar", "11.0.0", LocalDate.of(2026, 2, 16), List.of());
    assertFalse(doc.contains("BEGIN:VEVENT"));
  }
}
