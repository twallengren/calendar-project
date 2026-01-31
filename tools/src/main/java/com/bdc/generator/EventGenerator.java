package com.bdc.generator;

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

  private final RuleExpander ruleExpander;
  private final OccurrenceClassifier classifier;

  public EventGenerator() {
    this.ruleExpander = new RuleExpander();
    this.classifier = new OccurrenceClassifier();
  }

  public List<Event> generate(ResolvedSpec spec, LocalDate from, LocalDate to) {
    DateRange range = new DateRange(from, to);

    ReferenceResolver refResolver = new ReferenceResolver();
    refResolver.resolve(spec.references(), range);
    ruleExpander.setReferenceResolver(refResolver);

    // Build map of which keys are shiftable
    Set<String> shiftableKeys = new HashSet<>();
    for (EventSource source : spec.eventSources()) {
      if (Boolean.TRUE.equals(source.shiftable())) {
        shiftableKeys.add(source.key());
      }
    }

    // 1. Expand all rules to occurrences
    List<Occurrence> occurrences = new ArrayList<>();
    for (EventSource source : spec.eventSources()) {
      Rule rule = source.rule();
      if (rule != null) {
        String provenance = spec.id() + ":" + source.key();
        List<Occurrence> expanded = ruleExpander.expand(rule, range, provenance);
        occurrences.addAll(expanded);
      }
    }

    // 2. Apply weekend shifts for shiftable holidays
    occurrences = applyWeekendShifts(occurrences, spec.weekendShiftPolicy(), shiftableKeys, range);

    // 3. Apply deltas
    occurrences = applyDeltas(occurrences, spec.deltas(), range);

    // 4. Classify occurrences to events
    List<Event> events = new ArrayList<>(classifier.classify(occurrences, spec));

    // 5. Generate weekend events
    Set<DayOfWeek> weekendDays = spec.weekendPolicy().weekendDays();
    if (!weekendDays.isEmpty()) {
      Set<LocalDate> existingDates = events.stream().map(Event::date).collect(Collectors.toSet());
      for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
        DayOfWeek dow = date.getDayOfWeek();
        if (weekendDays.contains(dow) && !existingDates.contains(date)) {
          String dayName = dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
          events.add(new Event(date, EventType.WEEKEND, dayName, "weekend_policy"));
        }
      }
    }

    // 6. Sort deterministically
    return events.stream().sorted().collect(Collectors.toList());
  }

  private List<Occurrence> applyWeekendShifts(
      List<Occurrence> occurrences,
      WeekendShiftPolicy policy,
      Set<String> shiftableKeys,
      DateRange range) {

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
        // Non-shiftable occurrences on weekends are filtered out
        // (e.g., Christmas Eve on Saturday has no early close)
        DayOfWeek dow = occ.date().getDayOfWeek();
        if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
          nonShiftable.add(occ);
        }
      }
    }

    // Apply shifts based on policy
    List<Occurrence> shifted =
        switch (policy) {
          case NONE -> shiftable; // Already handled above, but for completeness
          case NEAREST_WEEKDAY -> applyNearestWeekdayShifts(shiftable, range);
          case NEXT_AVAILABLE_WEEKDAY -> applyNextAvailableWeekdayShifts(shiftable, range);
        };

    // Combine results
    List<Occurrence> result = new ArrayList<>(nonShiftable);
    result.addAll(shifted);
    return result;
  }

  private List<Occurrence> applyNearestWeekdayShifts(
      List<Occurrence> occurrences, DateRange range) {
    List<Occurrence> result = new ArrayList<>();

    for (Occurrence occ : occurrences) {
      DayOfWeek dow = occ.date().getDayOfWeek();
      LocalDate shiftedDate = occ.date();

      if (dow == DayOfWeek.SATURDAY) {
        shiftedDate = occ.date().minusDays(1); // Friday
      } else if (dow == DayOfWeek.SUNDAY) {
        shiftedDate = occ.date().plusDays(1); // Monday
      }

      if (range.contains(shiftedDate)) {
        result.add(new Occurrence(occ.key(), shiftedDate, occ.name(), occ.provenance()));
      }
    }

    return result;
  }

  private List<Occurrence> applyNextAvailableWeekdayShifts(
      List<Occurrence> occurrences, DateRange range) {
    // Sort by date to process in chronological order
    List<Occurrence> sorted = new ArrayList<>(occurrences);
    sorted.sort(Comparator.comparing(Occurrence::date));

    // Track which dates are already claimed
    Set<LocalDate> claimedDates = new HashSet<>();

    // First, add all non-weekend dates to claimed set
    for (Occurrence occ : sorted) {
      DayOfWeek dow = occ.date().getDayOfWeek();
      if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
        claimedDates.add(occ.date());
      }
    }

    List<Occurrence> result = new ArrayList<>();

    for (Occurrence occ : sorted) {
      DayOfWeek dow = occ.date().getDayOfWeek();

      if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
        // Not a weekend, keep as-is
        result.add(occ);
      } else {
        // Find next available weekday
        LocalDate candidate = occ.date().plusDays(1);
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY
            || candidate.getDayOfWeek() == DayOfWeek.SUNDAY
            || claimedDates.contains(candidate)) {
          candidate = candidate.plusDays(1);
        }

        // Claim this date
        claimedDates.add(candidate);

        if (range.contains(candidate)) {
          result.add(new Occurrence(occ.key(), candidate, occ.name(), occ.provenance()));
        }
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
