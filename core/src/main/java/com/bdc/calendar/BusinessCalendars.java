package com.bdc.calendar;

import com.bdc.chronology.DateRange;
import com.bdc.emitter.EventsCsvReader;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.stream.CsvDateStream;
import com.bdc.stream.DateStream;
import com.bdc.stream.JointDateStream;
import com.bdc.trust.CoverageInterval;
import com.bdc.trust.CoverageIntervals;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The entry point to the published calendars: {@code BusinessCalendars.of("US-NYSE")}.
 *
 * <pre>{@code
 * DateStream nyse = BusinessCalendars.of("XNYS");
 * nyse.isBusinessDay(LocalDate.of(2021, 12, 31));   // true
 * nyse.closeTime(LocalDate.of(2025, 7, 3));         // Optional[13:00]
 * nyse.nthBusinessDay(LocalDate.of(2026, 2, 25), 2);
 * }</pre>
 *
 * <p>The data comes from the {@code bdc-calendar-data} jar on the classpath, which packs the
 * artifacts published in {@code blessed/}. Weekend rows are rebuilt from each calendar's weekend
 * policy rather than shipped, so the answers are identical to the published {@code events.csv} but
 * the jar is a fraction of the size. Streams are parsed once and cached; they are immutable and
 * safe to share between threads.
 *
 * <p>Calendars are addressed by their canonical id ({@code US-NYSE}), by MIC ({@code XNYS}) or by
 * any case/underscore spelling of either.
 */
public final class BusinessCalendars {

  private static final String RESOURCE_ROOT = "/bdc/calendars";
  private static final String WEEKEND_KEY = "weekend";
  private static final String WEEKEND_MODULE = "weekend_policy";

  private static final Map<String, DateStream> CACHE = new ConcurrentHashMap<>();

  private static volatile Map<String, Object> manifest;

  private BusinessCalendars() {}

  /**
   * The calendar for an id or MIC.
   *
   * @throws IllegalArgumentException if no bundled calendar answers to that name
   */
  public static DateStream of(String idOrMic) {
    return CACHE.computeIfAbsent(resolveId(idOrMic), BusinessCalendars::load);
  }

  /**
   * A stream that is open only where every named calendar is open — settlement across markets.
   *
   * @throws IllegalArgumentException if no ids are given, or one of them is unknown
   */
  public static DateStream joint(String... idsOrMics) {
    if (idsOrMics == null || idsOrMics.length == 0) {
      throw new IllegalArgumentException("joint() needs at least one calendar id");
    }
    List<DateStream> members = new ArrayList<>(idsOrMics.length);
    for (String id : idsOrMics) {
      members.add(of(id));
    }
    return JointDateStream.joint(members);
  }

  /** Every bundled calendar id, sorted. MIC aliases are accepted by {@link #of} but not listed. */
  public static Set<String> available() {
    return Collections.unmodifiableSet(new TreeSet<>(index().keySet()));
  }

  /** The release version of the bundled calendar data, e.g. {@code 11.0.0}. */
  public static String dataVersion() {
    return String.valueOf(manifest().get("data_version"));
  }

  /** The exchange-code aliases the bundled data declares, e.g. {@code XNYS -> US-NYSE}. */
  public static Map<String, String> aliases() {
    Map<String, String> result = new LinkedHashMap<>();
    Object declared = manifest().get("aliases");
    if (declared instanceof Map<?, ?> map) {
      map.forEach((key, value) -> result.put(String.valueOf(key), String.valueOf(value)));
    }
    return Collections.unmodifiableMap(result);
  }

  /** Maps a user-supplied name onto a bundled calendar id. */
  static String resolveId(String idOrMic) {
    if (idOrMic == null || idOrMic.isBlank()) {
      throw unknown(String.valueOf(idOrMic));
    }
    String raw = idOrMic.strip();
    String upper = raw.toUpperCase();
    Map<String, String> aliases = aliases();
    for (String candidate : List.of(raw, upper, upper.replace('_', '-'), upper.replace('-', '_'))) {
      if (index().containsKey(candidate)) {
        return candidate;
      }
      String aliased = aliases.get(candidate);
      if (aliased != null) {
        return aliased;
      }
    }
    throw unknown(raw);
  }

  private static IllegalArgumentException unknown(String requested) {
    Set<String> known = new TreeSet<>(index().keySet());
    known.addAll(aliases().keySet());
    return new IllegalArgumentException(
        "Unknown calendar: " + requested + ". Available: " + String.join(", ", known));
  }

  private static DateStream load(String calendarId) {
    Map<String, Object> metadata =
        Json.parseObject(readResource(RESOURCE_ROOT + "/" + calendarId + "/metadata.json"));
    DateRange range =
        new DateRange(
            LocalDate.parse(text(metadata, "range_start")),
            LocalDate.parse(text(metadata, "range_end")));

    List<String> lines =
        List.of(readResource(RESOURCE_ROOT + "/" + calendarId + "/holidays.csv").split("\n", -1));
    List<Event> events = new ArrayList<>(new EventsCsvReader().parse(lines, "bundled"));
    events.addAll(weekends(metadata.get("weekend_policy"), range, events));
    Collections.sort(events);

    LocalDate verifiedThrough = null;
    List<CoverageInterval> coverageIntervals = List.of();
    if (metadata.get("coverage") instanceof Map<?, ?> coverage) {
      Object declared = coverage.get("verified_through");
      if (declared != null) {
        verifiedThrough = LocalDate.parse(String.valueOf(declared));
      }
      coverageIntervals = CoverageIntervals.fromJson(coverage.get("quality"));
    }
    return new CsvDateStream(calendarId, events, range, verifiedThrough, coverageIntervals);
  }

  /**
   * Rebuilds the WEEKEND rows the bundled data drops.
   *
   * <p>The emitter writes a WEEKEND row on every policy weekend date <em>except</em> those already
   * carrying a closure — a CLOSED event wins over the weekend row when both fall on the same day
   * (an Eid spanning a Saudi weekend, say) — so that is the rule reproduced here. Restoring them
   * makes {@code eventsOn} and {@code eventCountInRange} answer exactly as the published artifact
   * does, not just the business-day predicate.
   */
  private static List<Event> weekends(Object policyJson, DateRange range, List<Event> events) {
    WeekendPolicy policy = WeekendPolicy.fromJson(policyJson);
    Set<LocalDate> closed = new HashSet<>();
    for (Event event : events) {
      if (event.type() == EventType.CLOSED) {
        closed.add(event.date());
      }
    }
    List<Event> weekends = new ArrayList<>();
    for (LocalDate date = range.start(); !date.isAfter(range.end()); date = date.plusDays(1)) {
      if (!policy.isWeekend(date) || closed.contains(date)) {
        continue;
      }
      weekends.add(
          new Event(
              date,
              EventType.WEEKEND,
              dayName(date),
              WEEKEND_MODULE,
              WEEKEND_KEY,
              WEEKEND_MODULE,
              null,
              null,
              EventStatus.CONFIRMED));
    }
    return weekends;
  }

  /** {@code Saturday} — the description the emitter writes on a weekend row. */
  private static String dayName(LocalDate date) {
    String name = date.getDayOfWeek().name();
    return name.charAt(0) + name.substring(1).toLowerCase();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> index() {
    Object calendars = manifest().get("calendars");
    if (!(calendars instanceof Map)) {
      throw new IllegalStateException("Bundled manifest declares no calendars");
    }
    return (Map<String, Object>) calendars;
  }

  private static Map<String, Object> manifest() {
    Map<String, Object> local = manifest;
    if (local == null) {
      synchronized (BusinessCalendars.class) {
        local = manifest;
        if (local == null) {
          local = Json.parseObject(readResource(RESOURCE_ROOT + "/manifest.json"));
          manifest = local;
        }
      }
    }
    return local;
  }

  private static String text(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) {
      throw new IllegalStateException("Bundled metadata is missing '" + key + "'");
    }
    return String.valueOf(value);
  }

  private static String readResource(String path) {
    try (InputStream in = BusinessCalendars.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException(
            "No bundled calendar data at "
                + path
                + ". Add the bdc-calendar-data jar to the classpath.");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read bundled calendar data at " + path, e);
    }
  }
}
