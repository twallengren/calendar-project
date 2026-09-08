package com.bdc.diff;

import com.bdc.model.Event;
import com.bdc.model.EventType;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class CalendarDiffEngine {

  // Published artifacts contain these fields, but do not retain source keys or provenance.
  private record EventValue(EventType type, String description) {}

  private static final Comparator<EventDiff> DIFF_ORDER =
      Comparator.comparing(EventDiff::date)
          .thenComparing(EventDiff::oldType, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(EventDiff::newType, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(
              EventDiff::oldDescription, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(
              EventDiff::newDescription, Comparator.nullsFirst(Comparator.naturalOrder()));

  private Map<LocalDate, List<EventValue>> byDate(List<Event> events) {
    return events.stream()
        .collect(
            Collectors.groupingBy(
                Event::date,
                Collectors.mapping(
                    e -> new EventValue(e.type(), e.description()), Collectors.toList())));
  }

  public CalendarDiff compare(
      String calendarId,
      List<Event> generated,
      List<Event> blessed,
      LocalDate cutoffDate,
      LocalDate blessedRangeStart,
      LocalDate blessedRangeEnd) {
    Map<LocalDate, List<EventValue>> generatedByDate = byDate(generated);
    Map<LocalDate, List<EventValue>> blessedByDate = byDate(blessed);
    Set<LocalDate> allDates = new HashSet<>(generatedByDate.keySet());
    allDates.addAll(blessedByDate.keySet());

    List<EventDiff> additions = new ArrayList<>();
    List<EventDiff> removals = new ArrayList<>();
    List<EventDiff> modifications = new ArrayList<>();
    for (LocalDate date : allDates) {
      MultisetDiff<EventValue> unmatched =
          MultisetDiff.compare(
              blessedByDate.getOrDefault(date, List.of()),
              generatedByDate.getOrDefault(date, List.of()));
      if (unmatched.removals().size() == 1 && unmatched.additions().size() == 1) {
        EventValue oldEvent = unmatched.removals().getFirst();
        EventValue newEvent = unmatched.additions().getFirst();
        modifications.add(
            EventDiff.modified(
                date,
                oldEvent.type(),
                newEvent.type(),
                oldEvent.description(),
                newEvent.description()));
      } else {
        for (EventValue value : unmatched.additions()) {
          additions.add(EventDiff.added(date, value.type(), value.description()));
        }
        for (EventValue value : unmatched.removals()) {
          removals.add(EventDiff.removed(date, value.type(), value.description()));
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
    // Any removal = MAJOR
    if (!removals.isEmpty()) {
      return DiffSeverity.MAJOR;
    }

    // Any modification = MAJOR
    if (!modifications.isEmpty()) {
      return DiffSeverity.MAJOR;
    }

    // Check additions - only MAJOR if within the blessed calendar's existing range
    boolean hasAdditionsWithinExistingRange =
        additions.stream()
            .anyMatch(e -> isWithinRange(e.date(), blessedRangeStart, blessedRangeEnd));
    if (hasAdditionsWithinExistingRange) {
      return DiffSeverity.MAJOR;
    }

    // Additions outside the existing range (backfilling or future) = MINOR
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
