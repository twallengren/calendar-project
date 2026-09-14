package com.bdc.stream;

import com.bdc.chronology.DateRange;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.trust.CoverageInterval;
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
    this.calendarId = calendarId;
    this.range = range;
    this.verifiedThrough = verifiedThrough;
    this.coverageIntervals = coverageIntervals == null ? List.of() : List.copyOf(coverageIntervals);
    for (Event e : events) {
      byDate.computeIfAbsent(e.date(), d -> new ArrayList<>()).add(e);
    }
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
