package com.bdc.stream;

import com.bdc.chronology.DateRange;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import java.time.LocalDate;
import java.util.*;

/**
 * A materialized {@link DateStream} over a fixed list of events (a blessed or archived artifact).
 *
 * <p>A date is a business day when it carries neither a WEEKEND nor a CLOSED row. Queries outside
 * the artifact's generated range throw, because absence of a row there means "unknown", not "open".
 */
public class CsvDateStream implements DateStream {

  private static final int MAX_SEARCH_DAYS = 366;

  private final String calendarId;
  private final DateRange range;
  private final NavigableMap<LocalDate, List<Event>> byDate = new TreeMap<>();

  public CsvDateStream(String calendarId, List<Event> events, DateRange range) {
    this.calendarId = calendarId;
    this.range = range;
    for (Event e : events) {
      byDate.computeIfAbsent(e.date(), d -> new ArrayList<>()).add(e);
    }
  }

  public DateRange range() {
    return range;
  }

  private void checkRange(LocalDate date) {
    if (!range.contains(date)) {
      throw new IllegalArgumentException(
          date + " is outside the artifact range " + range.start() + " to " + range.end());
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
  public Optional<Event> eventOn(LocalDate date) {
    List<Event> events = eventsOn(date);
    return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
  }

  @Override
  public List<Event> eventsOn(LocalDate date) {
    checkRange(date);
    return byDate.getOrDefault(date, List.of());
  }

  @Override
  public boolean isBusinessDay(LocalDate date) {
    checkRange(date);
    return eventsOn(date).stream()
        .noneMatch(e -> e.type() == EventType.CLOSED || e.type() == EventType.WEEKEND);
  }

  @Override
  public LocalDate nextBusinessDay(LocalDate from) {
    LocalDate candidate = from.plusDays(1);
    for (int i = 0; i < MAX_SEARCH_DAYS; i++) {
      if (isBusinessDay(candidate)) {
        return candidate;
      }
      candidate = candidate.plusDays(1);
    }
    throw new IllegalStateException(
        "No business day within " + MAX_SEARCH_DAYS + " days after " + from);
  }

  @Override
  public LocalDate prevBusinessDay(LocalDate from) {
    LocalDate candidate = from.minusDays(1);
    for (int i = 0; i < MAX_SEARCH_DAYS; i++) {
      if (isBusinessDay(candidate)) {
        return candidate;
      }
      candidate = candidate.minusDays(1);
    }
    throw new IllegalStateException(
        "No business day within " + MAX_SEARCH_DAYS + " days before " + from);
  }

  @Override
  public LocalDate nthBusinessDay(LocalDate from, int n) {
    if (n == 0) {
      return from;
    }
    LocalDate current = from;
    int remaining = Math.abs(n);
    boolean forward = n > 0;
    while (remaining > 0) {
      current = forward ? current.plusDays(1) : current.minusDays(1);
      if (isBusinessDay(current)) {
        remaining--;
      }
    }
    return current;
  }

  @Override
  public long businessDaysInRange(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    long count = 0;
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      if (isBusinessDay(d)) {
        count++;
      }
    }
    return count;
  }

  @Override
  public long eventCountInRange(LocalDate from, LocalDate to) {
    return eventsInRange(from, to).size();
  }

  @Override
  public String calendarId() {
    return calendarId;
  }
}
