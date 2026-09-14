package com.bdc.stream;

import com.bdc.chronology.DateRange;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.trust.CoverageInterval;
import com.bdc.trust.EventDetails;
import com.bdc.trust.PublishedEventDetails;
import java.time.LocalDate;
import java.util.*;

/**
 * A materialized {@link DateStream} over a fixed list of events (a blessed or archived artifact).
 *
 * <p>A date is a business day when it carries neither a WEEKEND nor a CLOSED row. Queries outside
 * the artifact's generated range throw {@link OutsideCoverageException}, because absence of a row
 * there means "unknown", not "open".
 */
public class CsvDateStream implements DateStream {

  private final String calendarId;
  private final DateRange range;
  private final LocalDate verifiedThrough;
  private final List<CoverageInterval> coverageIntervals;
  private final Map<LocalDate, List<EventDetails>> detailsByDate = new TreeMap<>();
  private final NavigableMap<LocalDate, List<Event>> byDate = new TreeMap<>();

  public CsvDateStream(String calendarId, List<Event> events, DateRange range) {
    this(calendarId, events, range, null);
  }

  /**
   * @param verifiedThrough the {@code coverage.verified_through} recorded in the artifact's
   *     metadata, or null when it declares none
   */
  public CsvDateStream(
      String calendarId, List<Event> events, DateRange range, LocalDate verifiedThrough) {
    this(calendarId, events, range, verifiedThrough, List.of());
  }

  public CsvDateStream(
      String calendarId,
      List<Event> events,
      DateRange range,
      LocalDate verifiedThrough,
      List<CoverageInterval> coverageIntervals) {
    this(calendarId, events, range, verifiedThrough, coverageIntervals, null);
  }

  public CsvDateStream(
      String calendarId,
      List<Event> events,
      DateRange range,
      LocalDate verifiedThrough,
      List<CoverageInterval> coverageIntervals,
      List<EventDetails> publishedDetails) {
    this.calendarId = calendarId;
    this.range = range;
    this.verifiedThrough = verifiedThrough;
    this.coverageIntervals = coverageIntervals == null ? List.of() : List.copyOf(coverageIntervals);
    for (Event e : events) {
      byDate.computeIfAbsent(e.date(), d -> new ArrayList<>()).add(e);
    }
    if (publishedDetails != null) {
      Map<Event, Deque<EventDetails>> remaining = new HashMap<>();
      for (EventDetails detail : publishedDetails)
        remaining
            .computeIfAbsent(
                PublishedEventDetails.identity(detail.event()), key -> new ArrayDeque<>())
            .add(detail);
      for (Event event : events) {
        if (event.type() == EventType.WEEKEND) {
          detailsByDate
              .computeIfAbsent(event.date(), key -> new ArrayList<>())
              .add(
                  new EventDetails(
                      event,
                      event.status(),
                      event.status(),
                      List.of(),
                      null,
                      null,
                      null,
                      List.of()));
          continue;
        }
        Deque<EventDetails> matches = remaining.get(PublishedEventDetails.identity(event));
        if (matches == null || matches.isEmpty())
          throw new IllegalArgumentException("Missing event provenance occurrence: " + event);
        EventDetails detail = matches.removeFirst();
        detailsByDate
            .computeIfAbsent(event.date(), key -> new ArrayList<>())
            .add(
                new EventDetails(
                    event,
                    event.status(),
                    event.status(),
                    detail.evidenceIds(),
                    detail.nominalNativeDate(),
                    detail.chronologyProfile(),
                    detail.chronologyProvider(),
                    detail.observationLineage()));
      }
      if (remaining.values().stream().anyMatch(rows -> !rows.isEmpty()))
        throw new IllegalArgumentException("Event provenance contains extra occurrences");
    }
  }

  @Override
  public List<EventDetails> eventDetailsOn(LocalDate date) {
    checkRange(date);
    return detailsByDate.containsKey(date)
        ? List.copyOf(detailsByDate.get(date))
        : DateStream.super.eventDetailsOn(date);
  }

  @Override
  public String calendarId() {
    return calendarId;
  }

  @Override
  public DateRange range() {
    return range;
  }

  @Override
  public Optional<LocalDate> verifiedThrough() {
    return Optional.ofNullable(verifiedThrough);
  }

  @Override
  public List<CoverageInterval> coverageIntervals() {
    return coverageIntervals;
  }

  private void checkRange(LocalDate date) {
    if (!range.contains(date)) {
      throw new OutsideCoverageException(calendarId, date, range);
    }
  }

  @Override
  public List<Event> eventsInRange(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    checkRange(from);
    checkRange(to);
    List<Event> result = new ArrayList<>();
    byDate.subMap(from, true, to, true).values().forEach(result::addAll);
    return result;
  }

  @Override
  public List<Event> eventsOn(LocalDate date) {
    checkRange(date);
    return byDate.getOrDefault(date, List.of());
  }

  @Override
  public boolean isBusinessDay(LocalDate date) {
    checkRange(date);
    requireResolved(date);
    return eventsOn(date).stream()
        .noneMatch(e -> e.type() == EventType.CLOSED || e.type() == EventType.WEEKEND);
  }
}
