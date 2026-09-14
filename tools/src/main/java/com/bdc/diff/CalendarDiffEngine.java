package com.bdc.diff;

import com.bdc.model.Event;
import com.bdc.model.EventType;
import java.time.LocalDate;
import java.util.*;

/**
 * Compares generated events against a blessed baseline.
 *
 * <p>Events are matched by identity. When the baseline carries event keys (any artifact produced
 * after the key column was introduced) the identity is {@code (date, key)}, so several events on
 * one date are compared individually and a changed description or type is reported as a
 * modification. Legacy baselines without keys fall back to date-only identity.
 *
 * <p>Within one identity, occurrences are compared as a multiset of {@code (type, description)}:
 * exact matches cancel one occurrence at a time, so no event can hide another on the same date and
 * duplicate-dated events keep their counts. If exactly one occurrence remains on each side the pair
 * is reported as a modification; otherwise every leftover is reported individually as a removal or
 * an addition - similar types or names are never used to guess a pairing. Provenance and the other
 * columns a published artifact does not retain take no part in the comparison, and the result does
 * not depend on the order the events arrive in.
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
      LocalDate date = !gens.isEmpty() ? gens.getFirst().date() : refs.getFirst().date();
      String key = !gens.isEmpty() ? gens.getFirst().key() : refs.getFirst().key();

      List<EventValue> unmatchedAdditions = new ArrayList<>();
      List<EventValue> unmatchedRemovals = cancelExactMatches(refs, gens, unmatchedAdditions);

      if (unmatchedRemovals.size() == 1 && unmatchedAdditions.size() == 1) {
        EventValue before = unmatchedRemovals.getFirst();
        EventValue after = unmatchedAdditions.getFirst();
        modifications.add(
            EventDiff.modified(
                date, before.type(), after.type(), before.description(), after.description(), key));
      } else {
        for (EventValue value : unmatchedAdditions) {
          additions.add(EventDiff.added(date, value.type(), value.description(), key));
        }
        for (EventValue value : unmatchedRemovals) {
          removals.add(EventDiff.removed(date, value.type(), value.description(), key));
        }
      }
    }

    additions.sort(DIFF_ORDER);
    removals.sort(DIFF_ORDER);
    modifications.sort(DIFF_ORDER);

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

  /** The fields a published artifact retains, and so the only ones a comparison may look at. */
  private record EventValue(EventType type, String description) {}

  /**
   * Cancels occurrences present on both sides, one at a time. Fills {@code additionsOut} with the
   * occurrences only {@code after} has and returns those only {@code before} has.
   */
  private static List<EventValue> cancelExactMatches(
      List<Event> before, List<Event> after, List<EventValue> additionsOut) {
    Map<EventValue, Integer> remaining = new LinkedHashMap<>();
    for (Event e : before) {
      remaining.merge(new EventValue(e.type(), e.description()), 1, Integer::sum);
    }
    for (Event e : after) {
      EventValue value = new EventValue(e.type(), e.description());
      int count = remaining.getOrDefault(value, 0);
      if (count == 0) {
        additionsOut.add(value);
      } else if (count == 1) {
        remaining.remove(value);
      } else {
        remaining.put(value, count - 1);
      }
    }
    List<EventValue> removals = new ArrayList<>();
    remaining.forEach(
        (value, count) -> {
          for (int i = 0; i < count; i++) {
            removals.add(value);
          }
        });
    return removals;
  }

  /** Total order over everything a diff reports, so the output never depends on input order. */
  private static final Comparator<EventDiff> DIFF_ORDER =
      Comparator.comparing(EventDiff::date)
          .thenComparing(d -> d.key() == null ? "" : d.key())
          .thenComparing(EventDiff::oldType, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(EventDiff::newType, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(
              EventDiff::oldDescription, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(
              EventDiff::newDescription, Comparator.nullsFirst(Comparator.naturalOrder()));

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
