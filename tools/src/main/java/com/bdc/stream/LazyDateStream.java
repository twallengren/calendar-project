package com.bdc.stream;

import com.bdc.chronology.DateRange;
import com.bdc.generator.EventGenerator;
import com.bdc.model.CalendarSpec;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.bdc.model.WeekendPolicy;
import com.bdc.trust.CoverageInterval;
import java.time.LocalDate;
import java.util.*;

/**
 * A lazy (compute-on-demand) implementation of {@link DateStream}.
 *
 * <p>Events are generated on-the-fly from the provided ResolvedSpec. Single-day lookups generate a
 * window of roughly three months around the requested date and cache every day of it, so walking
 * forwards or backwards day by day (next/previous business day) does not regenerate per day.
 *
 * <p>The out-of-range contract is enforced only when the resolved spec declares {@code coverage}; a
 * calendar without a coverage block is unbounded and answers for any date the rules can be expanded
 * to.
 */
public class LazyDateStream implements DateStream {

  private static final int WINDOW_DAYS = 45;

  private final ResolvedSpec spec;
  private final EventGenerator generator;
  private final WeekendPolicy weekend;
  private final DateRange range;
  private final LocalDate verifiedThrough;
  private final List<CoverageInterval> coverageIntervals;

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
    CalendarSpec.Coverage coverage = spec.coverage();
    if (coverage == null) {
      this.range = new DateRange(LocalDate.MIN, LocalDate.MAX);
      this.verifiedThrough = null;
      this.coverageIntervals = List.of();
    } else {
      this.range =
          new DateRange(
              coverage.from() != null ? coverage.from() : LocalDate.MIN,
              coverage.to() != null ? coverage.to() : LocalDate.MAX);
      this.verifiedThrough = coverage.verifiedThrough();
      this.coverageIntervals = coverage.quality();
    }
  }

  @Override
  public String calendarId() {
    return spec.id();
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
      throw new OutsideCoverageException(spec.id(), date, range);
    }
  }

  @Override
  public List<Event> eventsInRange(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    checkRange(from);
    checkRange(to);
    return generator.generate(spec, from, to);
  }

  @Override
  public List<Event> eventsOn(LocalDate date) {
    checkRange(date);
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
    checkRange(date);
    requireResolved(date);
    if (weekend.isWeekend(date)) {
      return false;
    }
    return eventsOn(date).stream().noneMatch(e -> e.type() == EventType.CLOSED);
  }

  /** Clear the internal cache. */
  public void clearCache() {
    dayCache.clear();
  }
}
