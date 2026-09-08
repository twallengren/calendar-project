package com.bdc.generator;

import com.bdc.chronology.DateArithmetic;
import com.bdc.chronology.DateRange;
import com.bdc.classifier.OccurrenceClassifier;
import com.bdc.formula.ReferenceResolver;
import com.bdc.model.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

public class EventGenerator {

  private final OccurrenceClassifier classifier;

  public EventGenerator() {
    this.classifier = new OccurrenceClassifier();
  }

  public List<Event> generate(ResolvedSpec spec, LocalDate from, LocalDate to) {
    DateRange range = new DateRange(from, to);

    RuleExpander ruleExpander = new RuleExpander();
    ReferenceResolver refResolver = new ReferenceResolver(spec.references());
    ruleExpander.setReferenceResolver(refResolver);

    // Build set of which keys are shiftable, and which are CLOSED (kept on weekends)
    Set<String> shiftableKeys = new HashSet<>();
    Set<String> closedKeys = new HashSet<>();
    for (EventSource source : spec.eventSources()) {
      if (Boolean.TRUE.equals(source.shiftable())) {
        shiftableKeys.add(source.key());
      }
      if (source.defaultClassification() == EventType.CLOSED) {
        closedKeys.add(source.key());
      }
    }

    Set<DayOfWeek> weekendDays = spec.weekendPolicy().weekendDays();
    CascadingPlacement cascading =
        spec.weekendShiftPolicy() == WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY
            ? new CascadingPlacement(spec, ruleExpander, shiftableKeys)
            : null;

    // 1. Expand calculation dependencies, filtering active years on original dates.
    List<Occurrence> occurrences = new ArrayList<>();
    for (EventSource source : spec.eventSources()) {
      Rule rule = source.rule();
      if (rule != null && !(cascading != null && shiftableKeys.contains(source.key()))) {
        String provenance = spec.id() + ":" + source.key();
        DateRange dependencies = range;
        if (spec.weekendShiftPolicy() == WeekendShiftPolicy.NEAREST_WEEKDAY
            && shiftableKeys.contains(source.key())
            && !weekendDays.isEmpty()) {
          dependencies =
              new DateRange(
                  DateArithmetic.plusDays(from, -1, provenance),
                  DateArithmetic.plusDays(to, 1, provenance));
        }
        List<Occurrence> expanded = ruleExpander.expand(rule, dependencies, provenance);
        // Filter by date constraints
        for (Occurrence occ : expanded) {
          if (source.isActiveOn(occ.date())) {
            occurrences.add(occ);
          }
        }
      }
    }

    // 2. Apply weekend shifts for shiftable holidays
    occurrences =
        applyWeekendShifts(
            occurrences,
            spec.weekendShiftPolicy(),
            shiftableKeys,
            closedKeys,
            weekendDays,
            range,
            cascading);

    occurrences = occurrences.stream().filter(o -> range.contains(o.date())).toList();

    // 3. Apply deltas
    occurrences = applyDeltas(occurrences, spec.deltas(), range);

    // 4. Classify occurrences to events
    List<Event> events = new ArrayList<>(classifier.classify(occurrences, spec));

    // 5. Generate weekend events
    if (!weekendDays.isEmpty()) {
      Set<LocalDate> existingDates = events.stream().map(Event::date).collect(Collectors.toSet());
      for (LocalDate date = from; ; date = date.plusDays(1)) {
        DayOfWeek dow = date.getDayOfWeek();
        if (weekendDays.contains(dow) && !existingDates.contains(date)) {
          String dayName = dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
          events.add(new Event(date, EventType.WEEKEND, dayName, "weekend_policy"));
        }
        if (date.equals(to)) break;
      }
    }

    // 6. Provenance breaks otherwise equal ties independently of range-dependent key insertion.
    return events.stream()
        .sorted(
            Comparator.<Event>naturalOrder()
                .thenComparing(Event::provenance, Comparator.nullsFirst(Comparator.naturalOrder())))
        .collect(Collectors.toList());
  }

  private List<Occurrence> applyWeekendShifts(
      List<Occurrence> occurrences,
      WeekendShiftPolicy policy,
      Set<String> shiftableKeys,
      Set<String> closedKeys,
      Set<DayOfWeek> weekendDays,
      DateRange range,
      CascadingPlacement cascading) {

    if (policy == WeekendShiftPolicy.NONE) {
      return occurrences;
    }

    // Separate shiftable from non-shiftable occurrences
    List<Occurrence> shiftable = new ArrayList<>();
    List<Occurrence> nonShiftable = new ArrayList<>();

    for (Occurrence occ : occurrences) {
      if (shiftableKeys.contains(occ.key())) {
        shiftable.add(occ);
      } else {
        // Non-shiftable CLOSED events are kept on weekends (e.g., Eid closures
        // that span weekends). Other types (EARLY_CLOSE, NOTABLE) are filtered
        // out on weekends since they are meaningless on non-trading days.
        DayOfWeek dow = occ.date().getDayOfWeek();
        if (!weekendDays.contains(dow) || closedKeys.contains(occ.key())) {
          nonShiftable.add(occ);
        }
      }
    }

    // Apply shifts based on policy
    List<Occurrence> shifted =
        switch (policy) {
          case NONE -> shiftable; // Already handled above, but for completeness
          case NEAREST_WEEKDAY -> applyNearestWeekdayShifts(shiftable, weekendDays, range);
          case NEXT_AVAILABLE_WEEKDAY -> cascading.observedIn(range);
        };

    // Combine results
    List<Occurrence> result = new ArrayList<>(nonShiftable);
    result.addAll(shifted);
    return result;
  }

  private List<Occurrence> applyNearestWeekdayShifts(
      List<Occurrence> occurrences, Set<DayOfWeek> weekendDays, DateRange range) {
    List<Occurrence> result = new ArrayList<>();

    for (Occurrence occ : occurrences) {
      DayOfWeek dow = occ.date().getDayOfWeek();
      LocalDate shiftedDate = occ.date();

      if (weekendDays.contains(dow)) {
        // If the next day is also a weekend day, this is the "first" weekend day
        // → shift backward. Otherwise it's the "last" → shift forward.
        // e.g., Fri-Sat weekend: Fri→Thu (back), Sat→Sun (forward)
        // e.g., Sat-Sun weekend: Sat→Fri (back), Sun→Mon (forward)
        if (weekendDays.contains(dow.plus(1))) {
          shiftedDate = DateArithmetic.plusDays(occ.date(), -1, occ.provenance());
        } else {
          shiftedDate = DateArithmetic.plusDays(occ.date(), 1, occ.provenance());
        }
      }

      if (range.contains(shiftedDate)) {
        result.add(new Occurrence(occ.key(), shiftedDate, occ.name(), occ.provenance()));
      }
    }

    return result;
  }

  private List<Occurrence> applyDeltas(
      List<Occurrence> occurrences, List<Delta> deltas, DateRange range) {
    Map<String, Map<LocalDate, Occurrence>> byKeyAndDate = new LinkedHashMap<>();

    // Index existing occurrences
    for (Occurrence occ : occurrences) {
      byKeyAndDate.computeIfAbsent(occ.key(), k -> new LinkedHashMap<>()).put(occ.date(), occ);
    }

    // Apply deltas
    for (Delta delta : deltas) {
      switch (delta) {
        case Delta.Add add -> {
          if (range.contains(add.date())) {
            Occurrence occ = new Occurrence(add.key(), add.date(), add.name(), "delta:add");
            byKeyAndDate
                .computeIfAbsent(add.key(), k -> new LinkedHashMap<>())
                .put(add.date(), occ);
          }
        }
        case Delta.Remove remove -> {
          Map<LocalDate, Occurrence> byDate = byKeyAndDate.get(remove.key());
          if (byDate != null) {
            byDate.remove(remove.date());
          }
        }
        case Delta.Reclassify reclassify -> {
          // Reclassify is handled at classification time, not here
        }
      }
    }

    // Flatten back to list
    return byKeyAndDate.values().stream()
        .flatMap(m -> m.values().stream())
        .collect(Collectors.toList());
  }
}
