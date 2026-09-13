package com.bdc.diff;

import com.bdc.model.Event;
import java.time.LocalDate;
import java.util.*;

/**
 * Compares generated events against a blessed baseline.
 *
 * <p>Events are matched by identity. When the baseline carries event keys (any artifact produced
 * after the key column was introduced) the identity is {@code (date, key)}, so several events on
 * one date are compared individually and a changed description or type is reported as a
 * modification. Legacy baselines without keys fall back to date-only identity, pairing events on
 * the same date in order.
 */
public class CalendarDiffEngine {

  public CalendarDiff compare(
      String calendarId,
      List<Event> generated,
      List<Event> blessed,
      LocalDate cutoffDate,
      LocalDate blessedRangeStart,
      LocalDate blessedRangeEnd) {
    boolean useKeys =
        !blessed.isEmpty()
            && blessed.stream().allMatch(e -> e.key() != null)
            && generated.stream().allMatch(e -> e.key() != null);

    Map<String, List<Event>> generatedById = index(generated, useKeys);
    Map<String, List<Event>> blessedById = index(blessed, useKeys);

    Set<String> allIds = new LinkedHashSet<>();
    allIds.addAll(blessedById.keySet());
    allIds.addAll(generatedById.keySet());

    List<EventDiff> additions = new ArrayList<>();
    List<EventDiff> removals = new ArrayList<>();
    List<EventDiff> modifications = new ArrayList<>();

    for (String id : allIds) {
      List<Event> gens = generatedById.getOrDefault(id, List.of());
      List<Event> refs = blessedById.getOrDefault(id, List.of());
      int n = Math.max(gens.size(), refs.size());
      for (int i = 0; i < n; i++) {
        Event gen = i < gens.size() ? gens.get(i) : null;
        Event ref = i < refs.size() ? refs.get(i) : null;
        if (gen != null && ref == null) {
          additions.add(EventDiff.added(gen.date(), gen.type(), gen.description(), gen.key()));
        } else if (gen == null && ref != null) {
          removals.add(EventDiff.removed(ref.date(), ref.type(), ref.description(), ref.key()));
        } else if (gen != null) {
          if (!gen.type().equals(ref.type()) || !gen.description().equals(ref.description())) {
            modifications.add(
                EventDiff.modified(
                    gen.date(),
                    ref.type(),
                    gen.type(),
                    ref.description(),
                    gen.description(),
                    gen.key()));
          }
        }
      }
    }

    Comparator<EventDiff> order =
        Comparator.comparing(EventDiff::date).thenComparing(d -> d.key() == null ? "" : d.key());
    additions.sort(order);
    removals.sort(order);
    modifications.sort(order);

    DiffSeverity severity =
        classifySeverity(additions, removals, modifications, blessedRangeStart, blessedRangeEnd);

    return new CalendarDiff(
        calendarId,
        severity,
        Collections.unmodifiableList(additions),
        Collections.unmodifiableList(removals),
        Collections.unmodifiableList(modifications),
        cutoffDate,
        blessedRangeStart,
        blessedRangeEnd);
  }

  private static Map<String, List<Event>> index(List<Event> events, boolean useKeys) {
    Map<String, List<Event>> byId = new LinkedHashMap<>();
    for (Event e : events) {
      String id = useKeys ? e.date() + "|" + e.key() : e.date().toString();
      byId.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
    }
    return byId;
  }

  /**
   * Classify the severity of changes: - MAJOR: Any removal, modification, or addition within the
   * existing blessed range - MINOR: Additions outside the blessed range (backfilling or future
   * extensions) - NONE: No changes
   */
  public DiffSeverity classifySeverity(
      List<EventDiff> additions,
      List<EventDiff> removals,
      List<EventDiff> modifications,
      LocalDate blessedRangeStart,
      LocalDate blessedRangeEnd) {
    if (!removals.isEmpty()) {
      return DiffSeverity.MAJOR;
    }
    if (!modifications.isEmpty()) {
      return DiffSeverity.MAJOR;
    }
    boolean hasAdditionsWithinExistingRange =
        additions.stream()
            .anyMatch(e -> isWithinRange(e.date(), blessedRangeStart, blessedRangeEnd));
    if (hasAdditionsWithinExistingRange) {
      return DiffSeverity.MAJOR;
    }
    if (!additions.isEmpty()) {
      return DiffSeverity.MINOR;
    }
    return DiffSeverity.NONE;
  }

  private boolean isWithinRange(LocalDate date, LocalDate rangeStart, LocalDate rangeEnd) {
    return !date.isBefore(rangeStart) && !date.isAfter(rangeEnd);
  }

  public DiffSeverity aggregateSeverity(Collection<CalendarDiff> diffs) {
    return diffs.stream()
        .map(CalendarDiff::severity)
        .reduce(DiffSeverity.NONE, (a, b) -> a.ordinal() > b.ordinal() ? a : b);
  }
}
