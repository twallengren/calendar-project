package com.bdc.stream;

import com.bdc.generator.EventGenerator;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * A lazy (compute-on-demand) implementation of DateStream.
 *
 * <p>Events are generated on-the-fly when requested, using the provided ResolvedSpec. This is
 * efficient for queries but may be slower for repeated access patterns. A small cache is maintained
 * to improve performance for repeated queries.
 */
public class LazyDateStream implements DateStream {

  private final ResolvedSpec spec;
  private final EventGenerator generator;
  private final Set<DayOfWeek> weekendDays;

  // Simple cache for recent queries
  private final Map<LocalDate, List<Event>> dayCache =
      new LinkedHashMap<>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LocalDate, List<Event>> eldest) {
          return size() > 365; // Cache up to 1 year of daily lookups
        }
      };

  public LazyDateStream(ResolvedSpec spec) {
    this.spec = spec;
    this.generator = new EventGenerator();
    this.weekendDays =
        spec.weekendPolicy() != null
            ? spec.weekendPolicy().weekendDays()
            : Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
  }

  @Override
  public String calendarId() {
    return spec.id();
  }

  @Override
  public List<Event> eventsInRange(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    return generator.generate(spec, from, to);
  }

  @Override
  public Optional<Event> eventOn(LocalDate date) {
    List<Event> events = eventsOn(date);
    return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
  }

  @Override
  public List<Event> eventsOn(LocalDate date) {
    return dayCache.computeIfAbsent(date, d -> generator.generate(spec, d, d));
  }

  @Override
  public boolean isBusinessDay(LocalDate date) {
    // First check if it's a weekend
    if (weekendDays.contains(date.getDayOfWeek())) {
      return false;
    }

    // Then check if there's a CLOSED event on this date
    List<Event> events = eventsOn(date);
    return events.stream().noneMatch(e -> e.type() == EventType.CLOSED);
  }

  @Override
  public LocalDate nextBusinessDay(LocalDate from) {
    LocalDate candidate = from.plusDays(1);
    // Safety limit to prevent infinite loop
    int maxDays = 30;
    while (!isBusinessDay(candidate) && maxDays > 0) {
      candidate = candidate.plusDays(1);
      maxDays--;
    }
    if (maxDays == 0 && !isBusinessDay(candidate)) {
      throw new IllegalStateException("Could not find a business day within 30 days after " + from);
    }
    return candidate;
  }

  @Override
  public LocalDate prevBusinessDay(LocalDate from) {
    LocalDate candidate = from.minusDays(1);
    // Safety limit to prevent infinite loop
    int maxDays = 30;
    while (!isBusinessDay(candidate) && maxDays > 0) {
      candidate = candidate.minusDays(1);
      maxDays--;
    }
    if (maxDays == 0 && !isBusinessDay(candidate)) {
      throw new IllegalStateException(
          "Could not find a business day within 30 days before " + from);
    }
    return candidate;
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

    // Get all CLOSED events in range
    List<Event> closedEvents =
        eventsInRange(from, to).stream().filter(e -> e.type() == EventType.CLOSED).toList();

    Set<LocalDate> closedDates = new HashSet<>();
    for (Event event : closedEvents) {
      closedDates.add(event.date());
    }

    long count = 0;
    LocalDate current = from;
    while (!current.isAfter(to)) {
      if (!weekendDays.contains(current.getDayOfWeek()) && !closedDates.contains(current)) {
        count++;
      }
      current = current.plusDays(1);
    }

    return count;
  }

  @Override
  public long eventCountInRange(LocalDate from, LocalDate to) {
    return eventsInRange(from, to).size();
  }

  /** Clear the internal cache. */
  public void clearCache() {
    dayCache.clear();
  }
}
