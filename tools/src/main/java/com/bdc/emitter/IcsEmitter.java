package com.bdc.emitter;

import com.bdc.model.Event;
import com.bdc.model.EventType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes events as an RFC 5545 iCalendar (.ics) document.
 *
 * <p>One {@code VEVENT} is emitted per {@code CLOSED}/{@code EARLY_CLOSE} row (other event types
 * are informational and have no calendar-app meaning). Output uses CRLF line endings and folds
 * content lines at 75 octets, per section 3.1 of the RFC.
 */
public class IcsEmitter {

  private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
  private static final int MAX_OCTETS = 75;

  /**
   * @param calendarId the calendar id (e.g. {@code US-NYSE})
   * @param calendarName the display name (e.g. {@code NYSE Trading Calendar})
   * @param releaseSemantic the release version to embed in {@code PRODID} (e.g. {@code 11.0.0})
   * @param generationDate the release generation date; used as the fixed {@code DTSTAMP} for every
   *     event so that regenerating the site without a new release produces byte-identical output
   * @param events the events to consider; only {@code CLOSED}/{@code EARLY_CLOSE} rows become
   *     {@code VEVENT}s, in their given order
   */
  public String emit(
      String calendarId,
      String calendarName,
      String releaseSemantic,
      LocalDate generationDate,
      List<Event> events) {
    String stamp = generationDate.atStartOfDay(ZoneOffset.UTC).format(STAMP);
    String idLower = calendarId.toLowerCase(Locale.ROOT);

    StringBuilder out = new StringBuilder();
    line(out, "BEGIN:VCALENDAR");
    line(out, "VERSION:2.0");
    line(out, "PRODID:-//bdc-calendars//" + releaseSemantic + "//EN");
    line(out, "CALSCALE:GREGORIAN");
    line(out, "X-WR-CALNAME:" + escapeText(calendarName + " holidays"));

    for (Event event : events) {
      if (event.type() != EventType.CLOSED && event.type() != EventType.EARLY_CLOSE) {
        continue;
      }
      String key = event.key() != null ? event.key() : "event";
      String uid = idLower + "-" + event.date() + "-" + key + "@bdc-calendars";

      line(out, "BEGIN:VEVENT");
      line(out, "UID:" + uid);
      line(out, "DTSTAMP:" + stamp);
      line(out, "DTSTART;VALUE=DATE:" + event.date().format(DATE));
      line(out, "DTEND;VALUE=DATE:" + event.date().plusDays(1).format(DATE));
      line(out, "SUMMARY:" + escapeText(summaryOf(calendarName, event)));
      line(out, "DESCRIPTION:" + escapeText(descriptionOf(event)));
      line(out, "END:VEVENT");
    }

    line(out, "END:VCALENDAR");
    return out.toString();
  }

  private static String summaryOf(String calendarName, Event event) {
    if (event.type() == EventType.EARLY_CLOSE) {
      String closeTime = event.closeTime() != null ? TIME.format(event.closeTime()) : "";
      return calendarName + " early close " + closeTime + ": " + event.description();
    }
    return calendarName + " closed: " + event.description();
  }

  private static String descriptionOf(Event event) {
    return "Key: "
        + nullToEmpty(event.key())
        + "\nSource: "
        + nullToEmpty(event.sourceModule())
        + "\nObserved from: "
        + (event.observedFrom() != null ? event.observedFrom().toString() : "(none)")
        + "\nStatus: "
        + (event.status() != null ? event.status().name() : "");
  }

  private static String nullToEmpty(String s) {
    return s != null ? s : "";
  }

  private static void line(StringBuilder out, String content) {
    out.append(fold(content)).append("\r\n");
  }

  /**
   * Folds a content line to at most {@value #MAX_OCTETS} octets per physical line, per RFC 5545
   * 3.1: continuation lines are introduced by CRLF followed by a single leading space, which itself
   * counts toward the following line's octet budget.
   */
  static String fold(String content) {
    if (content.getBytes(StandardCharsets.UTF_8).length <= MAX_OCTETS) {
      return content;
    }
    StringBuilder folded = new StringBuilder();
    int i = 0;
    boolean first = true;
    int length = content.length();
    while (i < length) {
      int budget = first ? MAX_OCTETS : MAX_OCTETS - 1;
      int used = 0;
      int end = i;
      while (end < length) {
        int cp = content.codePointAt(end);
        int cpChars = Character.charCount(cp);
        int cpOctets = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
        if (used + cpOctets > budget) {
          break;
        }
        used += cpOctets;
        end += cpChars;
      }
      if (end == i) {
        // A single code point exceeds the budget (shouldn't happen for our ASCII content); take
        // it anyway to guarantee forward progress.
        end = i + Character.charCount(content.codePointAt(i));
      }
      if (!first) {
        folded.append("\r\n ");
      }
      folded.append(content, i, end);
      i = end;
      first = false;
    }
    return folded.toString();
  }

  private static String escapeText(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder escaped = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\\' -> escaped.append("\\\\");
        case ';' -> escaped.append("\\;");
        case ',' -> escaped.append("\\,");
        case '\n' -> escaped.append("\\n");
        default -> escaped.append(c);
      }
    }
    return escaped.toString();
  }

  /** Exposed for tests that want to check folding without building a full document. */
  static List<String> lines(String document) {
    List<String> result = new ArrayList<>();
    int start = 0;
    while (start <= document.length()) {
      int idx = document.indexOf("\r\n", start);
      if (idx < 0) {
        break;
      }
      result.add(document.substring(start, idx));
      start = idx + 2;
    }
    return result;
  }
}
