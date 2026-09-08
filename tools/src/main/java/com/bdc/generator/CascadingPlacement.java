package com.bdc.generator;

import com.bdc.chronology.DateArithmetic;
import com.bdc.chronology.DateRange;
import com.bdc.model.*;
import java.time.LocalDate;
import java.util.*;

/** Dependency-aware observation. All indexes and placements belong to a single generation call. */
final class CascadingPlacement {
  static final int MAX_DELAY_DAYS = 366;
  static final int MAX_DEPENDENCY_WORK = 10_000;

  // Per-date ordinal preserves explicit duplicates without depending on expansion boundaries.
  private record Entry(Occurrence occurrence, int source, int ordinal) {}

  private static final Comparator<Entry> ORDER =
      Comparator.comparing((Entry e) -> e.occurrence().date())
          .thenComparingInt(Entry::source)
          .thenComparingInt(Entry::ordinal);

  private final ResolvedSpec spec;
  private final RuleExpander expander;
  private final Set<String> shiftableKeys;
  private final NavigableMap<LocalDate, List<Entry>> byDate = new TreeMap<>();
  private final Map<Entry, LocalDate> placements = new HashMap<>();
  private final Map<Entry, LocalDate> latestPossible = new HashMap<>();

  CascadingPlacement(ResolvedSpec spec, RuleExpander expander, Set<String> shiftableKeys) {
    this.spec = spec;
    this.expander = expander;
    this.shiftableKeys = shiftableKeys;
    if (spec.weekendPolicy().weekendDays().size() == 7) {
      throw new IllegalArgumentException(
          spec.id() + ": NEXT_AVAILABLE_WEEKDAY has no available weekdays");
    }
  }

  List<Occurrence> observedIn(DateRange range) {
    LocalDate start =
        shiftableKeys.isEmpty() || spec.weekendPolicy().weekendDays().isEmpty()
            ? range.start()
            : plus(range.start(), -MAX_DELAY_DAYS);
    List<Entry> roots = entries(start, range.end());
    List<Occurrence> result = new ArrayList<>();
    for (Entry root : roots) {
      Occurrence occurrence = root.occurrence();
      LocalDate date = weekend(occurrence.date()) ? place(root) : occurrence.date();
      if (range.contains(date)) {
        result.add(
            new Occurrence(occurrence.key(), date, occurrence.name(), occurrence.provenance()));
      }
    }
    return result;
  }

  private static final class Frame {
    final Entry entry;
    int delay = 1;
    List<Entry> competitors;
    int competitorIndex;

    Frame(Entry entry) {
      this.entry = entry;
    }

    void advance() {
      delay++;
      competitors = null;
      competitorIndex = 0;
    }
  }

  private LocalDate place(Entry root) {
    if (placements.containsKey(root)) return placements.get(root);
    Set<Entry> work = new HashSet<>();
    Deque<Frame> stack = new ArrayDeque<>();
    stack.push(new Frame(root));
    work.add(root);
    while (!stack.isEmpty()) {
      Frame frame = stack.peek();
      if (frame.delay > MAX_DELAY_DAYS) {
        throw limit(frame.entry, "observation delay limit of 366 calendar days");
      }
      LocalDate candidate = plus(frame.entry.occurrence().date(), frame.delay);
      if (weekend(candidate) || reserved(candidate)) {
        frame.advance();
        continue;
      }
      if (frame.competitors == null) {
        frame.competitors =
            entries(plus(candidate, -MAX_DELAY_DAYS), plus(candidate, -1)).stream()
                .filter(e -> weekend(e.occurrence().date()) && ORDER.compare(e, frame.entry) < 0)
                .toList();
      }
      boolean pending = false;
      while (frame.competitorIndex < frame.competitors.size()) {
        Entry competitor = frame.competitors.get(frame.competitorIndex);
        LocalDate observed = placements.get(competitor);
        if (observed == null && canReach(competitor, candidate)) {
          if (work.add(competitor) && work.size() > MAX_DEPENDENCY_WORK) {
            throw limit(root, "dependency work limit of 10000 distinct occurrence placements");
          }
          stack.push(new Frame(competitor));
          pending = true;
          break;
        }
        frame.competitorIndex++;
        if (candidate.equals(observed)) {
          frame.advance();
          pending = true;
          break;
        }
      }
      if (!pending) {
        placements.put(frame.entry, candidate);
        stack.pop();
      }
    }
    return placements.get(root);
  }

  /**
   * A conservative upper bound avoids chasing unrelated holidays indefinitely into the past. Only
   * earlier weekend occurrences within 366 days of this original date can take a slot after it.
   * Each takes at most one slot. With N such occurrences, this one must occupy one of the first N+1
   * weekdays not reserved by an unshifted event (or exceed the delay limit). Skipping a dependency
   * using this bound is valid whenever generation succeeds.
   */
  private boolean canReach(Entry entry, LocalDate candidate) {
    LocalDate cached = latestPossible.get(entry);
    if (cached != null) return !cached.isBefore(candidate);
    LocalDate original = entry.occurrence().date();
    long slots =
        entries(plus(original, -MAX_DELAY_DAYS), original).stream()
                .filter(e -> weekend(e.occurrence().date()) && ORDER.compare(e, entry) < 0)
                .count()
            + 1;
    // Only inspect dates before this candidate; a bound must not request future coverage
    // that the placement itself might never need.
    for (LocalDate date = plus(original, 1); date.isBefore(candidate); date = plus(date, 1)) {
      if (!weekend(date) && !reserved(date) && --slots == 0) {
        latestPossible.put(entry, date);
        return false;
      }
    }
    return true;
  }

  private boolean weekend(LocalDate date) {
    return spec.weekendPolicy().isWeekend(date.getDayOfWeek());
  }

  private boolean reserved(LocalDate date) {
    return !entries(date, date).isEmpty();
  }

  private LocalDate plus(LocalDate date, long days) {
    return DateArithmetic.plusDays(date, days, spec.id() + ": cascading observation dependency");
  }

  private IllegalArgumentException limit(Entry entry, String exceeded) {
    Occurrence o = entry.occurrence();
    return new IllegalArgumentException(
        spec.id()
            + ": event "
            + o.key()
            + " ("
            + o.name()
            + "), original date "
            + o.date()
            + ", exceeded "
            + exceeded);
  }

  /** Expand contiguous missing intervals once; empty dates are cached too. */
  private List<Entry> entries(LocalDate start, LocalDate end) {
    for (LocalDate date = start; ; ) {
      if (!byDate.containsKey(date)) {
        LocalDate missingEnd = date;
        while (missingEnd.isBefore(end) && !byDate.containsKey(missingEnd.plusDays(1))) {
          missingEnd = missingEnd.plusDays(1);
        }
        expand(new DateRange(date, missingEnd));
        date = missingEnd;
      }
      if (date.equals(end)) break;
      date = date.plusDays(1);
    }
    return byDate.subMap(start, true, end, true).values().stream()
        .flatMap(List::stream)
        .sorted(ORDER)
        .toList();
  }

  private void expand(DateRange range) {
    for (LocalDate date = range.start(); ; date = date.plusDays(1)) {
      byDate.put(date, new ArrayList<>());
      if (date.equals(range.end())) break;
    }
    for (int sourceIndex = 0; sourceIndex < spec.eventSources().size(); sourceIndex++) {
      EventSource source = spec.eventSources().get(sourceIndex);
      if (source.rule() == null || !shiftableKeys.contains(source.key())) continue;
      Map<LocalDate, Integer> ordinals = new HashMap<>();
      for (Occurrence occurrence :
          expander.expand(source.rule(), range, spec.id() + ":" + source.key())) {
        int ordinal = ordinals.merge(occurrence.date(), 1, Integer::sum) - 1;
        if (source.isActiveOn(occurrence.date())) {
          byDate.get(occurrence.date()).add(new Entry(occurrence, sourceIndex, ordinal));
        }
      }
    }
  }
}
