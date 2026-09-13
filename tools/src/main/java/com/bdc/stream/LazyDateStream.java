package com.bdc.stream;

import com.bdc.generator.EventGenerator;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.bdc.model.WeekendPolicy;
import java.time.LocalDate;
import java.util.*;

/**
 * A lazy (compute-on-demand) implementation of DateStream.
 *
 * <p>Events are generated on-the-fly from the provided ResolvedSpec. Single-day lookups generate a
 * window of roughly three months around the requested date and cache every day of it, so walking
 * forwards or backwards day by day (next/previous business day) does not regenerate per day.
 */
public class LazyDateStream implements DateStream {

  private static final int WINDOW_DAYS = 45;
  private static final int MAX_SEARCH_DAYS = 366;

  private final ResolvedSpec spec;
  private final EventGenerator generator;
  private final WeekendPolicy weekend;

  // Cache of per-day events for recently generated windows
  private final Map<LocalDate, List<Event>> dayCache =
      new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LocalDate, List<Event>> eldest) {
          return size() > 2000;
        }
      };

  public LazyDateStream(ResolvedSpec spec) {
    this.spec = spec;
    this.generator = new EventGenerator();
    this.weekend = spec.weekendPolicy() != null ? spec.weekendPolicy() : WeekendPolicy.SAT_SUN;
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
    List<Event> cached = dayCache.get(date);
    if (cached != null) {
      return cached;
    }
    LocalDate from = date.minusDays(WINDOW_DAYS);
    LocalDate to = date.plusDays(WINDOW_DAYS);
    Map<LocalDate, List<Event>> byDate = new HashMap<>();
    for (Event event : generator.generate(spec, from, to)) {
      byDate.computeIfAbsent(event.date(), d -> new ArrayList<>()).add(event);
    }
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      dayCache.put(d, List.copyOf(byDate.getOrDefault(d, List.of())));
    }
    return dayCache.get(date);
  }

  @Override
  public boolean isBusinessDay(LocalDate date) {
    if (weekend.isWeekend(date)) {
      return false;
    }
    return eventsOn(date).stream().noneMatch(e -> e.type() == EventType.CLOSED);
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
        "Could not find a business day within " + MAX_SEARCH_DAYS + " days after " + from);
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
        "Could not find a business day within " + MAX_SEARCH_DAYS + " days before " + from);
  }

  @Override
  public LocalDate nthBusinessDay(LocalDate from, int n) {
    if (n == 0) {
      return from;
    }

    LocalDate current = from;
    int remaining = Math.abs(n);
    boolean forward = n > 0;
    long guard = (long) MAX_SEARCH_DAYS * remaining + MAX_SEARCH_DAYS;

    while (remaining > 0) {
      if (guard-- <= 0) {
        throw new IllegalStateException(
            "Could not find "
                + Math.abs(n)
                + " business days "
                + (forward ? "after " : "before ")
                + from);
      }
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

    Set<LocalDate> closedDates = new HashSet<>();
    for (Event event : eventsInRange(from, to)) {
      if (event.type() == EventType.CLOSED) {
        closedDates.add(event.date());
      }
    }

    long count = 0;
    for (LocalDate current = from; !current.isAfter(to); current = current.plusDays(1)) {
      if (!weekend.isWeekend(current) && !closedDates.contains(current)) {
        count++;
      }
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
