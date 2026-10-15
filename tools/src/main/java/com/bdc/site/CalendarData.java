package com.bdc.site;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything the HTML pages know about one calendar, read back out of the generated {@code /v1/}
 * JSON API — never out of {@code blessed/} directly.
 *
 * <p>That indirection is deliberate: the site is the API's first consumer, so if a field is missing
 * or mis-shaped in the published contract, a page breaks and a test catches it. The only things the
 * site reads outside {@code /v1/} are the status artifacts ({@code cross_validation.json} and the
 * {@code sources/} README), which the API does not publish; see {@link StatusData}.
 */
public record CalendarData(
    String id,
    String name,
    String timezone,
    String mic,
    List<String> aliases,
    Coverage coverage,
    Map<String, Integer> countsByType,
    Map<String, Integer> countsByStatus,
    String checksum,
    List<Integer> years,
    JsonNode weekendPolicy,
    Map<Integer, List<DayEvent>> eventsByYear,
    Map<LocalDate, JsonNode> assessments,
    String kind) {

  public CalendarData(
      String id,
      String name,
      String timezone,
      String mic,
      List<String> aliases,
      Coverage coverage,
      Map<String, Integer> countsByType,
      Map<String, Integer> countsByStatus,
      String checksum,
      List<Integer> years,
      JsonNode weekendPolicy,
      Map<Integer, List<DayEvent>> eventsByYear,
      Map<LocalDate, JsonNode> assessments) {
    this(
        id,
        name,
        timezone,
        mic,
        aliases,
        coverage,
        countsByType,
        countsByStatus,
        checksum,
        years,
        weekendPolicy,
        eventsByYear,
        assessments,
        "market");
  }

  public CalendarData(
      String id,
      String name,
      String timezone,
      String mic,
      List<String> aliases,
      Coverage coverage,
      Map<String, Integer> countsByType,
      Map<String, Integer> countsByStatus,
      String checksum,
      List<Integer> years,
      JsonNode weekendPolicy,
      Map<Integer, List<DayEvent>> eventsByYear) {
    this(
        id,
        name,
        timezone,
        mic,
        aliases,
        coverage,
        countsByType,
        countsByStatus,
        checksum,
        years,
        weekendPolicy,
        eventsByYear,
        Map.of());
  }

  /**
   * Whether the business-day answer is unknown on this date: outside coverage, or the scheduled
   * closure / unscheduled exception scopes are incomplete. An early-close gap alone does not make a
   * date unknown (see {@link #isSessionUnknown}).
   */
  public boolean isUnknown(LocalDate date) {
    if ((coverage.from() != null && date.isBefore(coverage.from()))
        || (coverage.to() != null && date.isAfter(coverage.to()))) return true;
    if (assessments.isEmpty()) return false;
    JsonNode day = assessments.get(date);
    if (day == null) return true;
    JsonNode businessDay = day.path("business_day_state");
    return businessDay.isMissingNode()
        ? day.path("state").asText().equals("UNKNOWN")
        : businessDay.asText().equals("UNKNOWN");
  }

  /** Whether the full session answer (close time included) is unknown on this date. */
  public boolean isSessionUnknown(LocalDate date) {
    if (isUnknown(date)) return true;
    JsonNode day = assessments.get(date);
    return day != null && day.path("state").asText().equals("UNKNOWN");
  }

  /** The business-day confidence of a date, falling back to the full confidence for old JSON. */
  public String businessDayConfidence(LocalDate date) {
    JsonNode day = assessments.get(date);
    if (day == null) return "UNKNOWN";
    JsonNode value = day.path("business_day_confidence");
    return value.isMissingNode() ? day.path("effective_confidence").asText() : value.asText();
  }

  /**
   * Completeness of the business-day answer across the published dates, with the number of days
   * whose close time is additionally unresolved.
   */
  public String coverageSummary() {
    if (assessments.isEmpty()) return "Completeness not recorded";
    int incomplete = 0;
    int projected = 0;
    int sessionIncomplete = 0;
    for (JsonNode day : assessments.values()) {
      String confidence =
          day.path("business_day_confidence").isMissingNode()
              ? day.path("effective_confidence").asText()
              : day.path("business_day_confidence").asText();
      if (confidence.equals("UNKNOWN")) incomplete++;
      else if (confidence.equals("PROJECTED")) projected++;
      if (!confidence.equals("UNKNOWN")
          && day.path("effective_confidence").asText().equals("UNKNOWN")) sessionIncomplete++;
    }
    String summary =
        incomplete
            + " incomplete; "
            + projected
            + " projected; "
            + (assessments.size() - incomplete - projected)
            + " verified business days";
    if (sessionIncomplete > 0) {
      summary += " (session detail incomplete on " + sessionIncomplete + ")";
    }
    return summary;
  }

  /** The published coverage window, plus the date through which the data has been verified. */
  public record Coverage(LocalDate from, LocalDate to, LocalDate verifiedThrough) {}

  /** One row of a {@code <year>.json} document. */
  public record DayEvent(
      LocalDate date,
      String type,
      String description,
      String key,
      String sourceModule,
      LocalDate observedFrom,
      String closeTime,
      String status,
      String openTime) {

    /** A row without an open time (every type except SPECIAL_SESSION). */
    public DayEvent(
        LocalDate date,
        String type,
        String description,
        String key,
        String sourceModule,
        LocalDate observedFrom,
        String closeTime,
        String status) {
      this(date, type, description, key, sourceModule, observedFrom, closeTime, status, null);
    }

    public boolean isWeekend() {
      return "WEEKEND".equals(type);
    }

    public boolean isClosed() {
      return "CLOSED".equals(type);
    }

    public boolean isEarlyClose() {
      return "EARLY_CLOSE".equals(type);
    }

    public boolean isSpecialSession() {
      return "SPECIAL_SESSION".equals(type);
    }

    /** {@code 18:15–19:15} for a special session, else the close time (or null). */
    public String timeLabel() {
      if (isSpecialSession() && openTime != null && closeTime != null) {
        return openTime + "\u2013" + closeTime;
      }
      return closeTime;
    }

    /** The CSS class of this row's badge. */
    public String badgeClass() {
      return isClosed() ? "closed" : isSpecialSession() ? "special-session" : "early-close";
    }

    /** The text of this row's badge. */
    public String badgeLabel() {
      return isClosed() ? "Closed" : isSpecialSession() ? "Special session" : "Early close";
    }
  }

  public int closures() {
    return countsByType.getOrDefault("CLOSED", 0);
  }

  public int earlyCloses() {
    return countsByType.getOrDefault("EARLY_CLOSE", 0);
  }

  public int projected() {
    return countsByStatus.getOrDefault("PROJECTED", 0);
  }

  public int firstYear() {
    return years.get(0);
  }

  public int lastYear() {
    return years.get(years.size() - 1);
  }

  public List<DayEvent> eventsIn(int year) {
    return eventsByYear.getOrDefault(year, List.of());
  }

  /**
   * The effective status of a row, applying the {@code v1} compatibility contract: a date after
   * {@code coverage.verified_through} is projected regardless of the row's own status.
   */
  public boolean isProjected(DayEvent event) {
    if ("PROJECTED".equals(event.status())) {
      return true;
    }
    return coverage.verifiedThrough() != null && event.date().isAfter(coverage.verifiedThrough());
  }

  /** A date is a non-business day when it carries a WEEKEND or CLOSED row. */
  public boolean isBusinessDay(LocalDate date) {
    if (isUnknown(date))
      throw new IllegalArgumentException("Unresolved date " + date + " for " + id);
    for (DayEvent event : eventsIn(date.getYear())) {
      if (event.date().equals(date) && (event.isWeekend() || event.isClosed())) {
        return false;
      }
    }
    return true;
  }

  /**
   * The nearest business day strictly before or after {@code date} (step {@code -1} or {@code +1}),
   * or null when the walk leaves the calendar's published year range.
   *
   * <p>Weekends and closures come straight out of the published rows, so this never reimplements
   * weekend or shift policy — it only reads what the generator already decided.
   */
  public LocalDate adjacentBusinessDay(LocalDate date, int step) {
    LocalDate first = LocalDate.of(firstYear(), 1, 1);
    LocalDate last = LocalDate.of(lastYear(), 12, 31);
    LocalDate cursor = date.plusDays(step);
    while (!cursor.isBefore(first) && !cursor.isAfter(last)) {
      if (isUnknown(cursor)) return null;
      if (isBusinessDay(cursor)) {
        return cursor;
      }
      cursor = cursor.plusDays(step);
    }
    return null;
  }

  /** Every non-weekend event in the calendar, grouped by date, oldest first. */
  public Map<LocalDate, List<DayEvent>> nonWeekendEventsByDate() {
    Map<LocalDate, List<DayEvent>> byDate = new TreeMap<>();
    for (int year : years) {
      for (DayEvent event : eventsIn(year)) {
        if (!event.isWeekend()) {
          byDate.computeIfAbsent(event.date(), k -> new ArrayList<>()).add(event);
        }
      }
    }
    return byDate;
  }

  /** Non-weekend events for one year, oldest first. */
  public List<DayEvent> nonWeekendEventsIn(int year) {
    List<DayEvent> events = new ArrayList<>();
    for (DayEvent event : eventsIn(year)) {
      if (!event.isWeekend()) {
        events.add(event);
      }
    }
    return events;
  }

  /** Reads every published calendar out of {@code <siteDir>/v1/}. */
  public static List<CalendarData> readAll(Path siteDir) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    Path v1 = siteDir.resolve("v1");
    JsonNode index = mapper.readTree(v1.resolve("index.json").toFile());
    List<CalendarData> calendars = new ArrayList<>();
    for (JsonNode entry : index.path("calendars")) {
      calendars.add(read(mapper, v1, entry));
    }
    return calendars;
  }

  private static CalendarData read(ObjectMapper mapper, Path v1, JsonNode indexEntry)
      throws IOException {
    String id = indexEntry.path("id").asText();
    Path calDir = v1.resolve("calendars").resolve(id);
    JsonNode manifest = mapper.readTree(calDir.resolve("manifest.json").toFile());

    List<Integer> years = new ArrayList<>();
    for (JsonNode year : manifest.path("years")) {
      years.add(year.asInt());
    }
    Collections.sort(years);

    Map<Integer, List<DayEvent>> eventsByYear = new TreeMap<>();
    for (int year : years) {
      Path yearFile = calDir.resolve(year + ".json");
      if (!Files.exists(yearFile)) {
        continue;
      }
      JsonNode document = mapper.readTree(yearFile.toFile());
      List<DayEvent> events = new ArrayList<>();
      for (JsonNode row : document.path("events")) {
        events.add(
            new DayEvent(
                LocalDate.parse(row.path("date").asText()),
                row.path("type").asText(),
                row.path("description").asText(""),
                row.path("key").asText(""),
                row.path("source_module").asText(""),
                dateOrNull(row, "observed_from"),
                textOrNull(row, "close_time"),
                row.path("status").asText("PROJECTED"),
                textOrNull(row, "open_time")));
      }
      eventsByYear.put(year, List.copyOf(events));
    }

    Map<LocalDate, JsonNode> assessments = new TreeMap<>();
    Path v2 = v1.getParent().resolve("v2/calendars").resolve(id);
    if (manifest.path("coverage").path("quality").size() > 0
        && !Files.exists(v2.resolve("manifest.json")))
      throw new IOException("Explicit coverage requires v2 assessments for " + id);
    for (int year : years) {
      Path yearFile = v2.resolve(year + ".json");
      if (!Files.exists(yearFile)) continue;
      for (JsonNode day : mapper.readTree(yearFile.toFile()).path("days"))
        assessments.put(LocalDate.parse(day.path("date").asText()), day);
    }
    return new CalendarData(
        id,
        indexEntry.path("name").asText(id),
        textOrNull(indexEntry, "timezone"),
        textOrNull(indexEntry, "mic"),
        aliasesOf(indexEntry),
        new Coverage(
            dateOrNull(indexEntry.path("coverage"), "from"),
            dateOrNull(indexEntry.path("coverage"), "to"),
            dateOrNull(indexEntry.path("coverage"), "verified_through")),
        counts(indexEntry.path("counts_by_type")),
        counts(indexEntry.path("counts_by_status")),
        textOrNull(indexEntry, "checksum"),
        List.copyOf(years),
        manifest.path("weekend_policy"),
        Collections.unmodifiableMap(eventsByYear),
        Collections.unmodifiableMap(assessments),
        manifest.path("kind").asText("market"));
  }

  private static List<String> aliasesOf(JsonNode indexEntry) {
    JsonNode aliases = indexEntry.path("aliases");
    if (!aliases.isArray()) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (JsonNode alias : aliases) {
      result.add(alias.asText());
    }
    return List.copyOf(result);
  }

  private static Map<String, Integer> counts(JsonNode node) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    node.fields().forEachRemaining(field -> counts.put(field.getKey(), field.getValue().asInt()));
    return Collections.unmodifiableMap(counts);
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  private static LocalDate dateOrNull(JsonNode node, String field) {
    String text = textOrNull(node, field);
    return text == null || text.isEmpty() ? null : LocalDate.parse(text);
  }
}
