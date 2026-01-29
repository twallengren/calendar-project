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

    // 2. Apply deltas
    occurrences = applyDeltas(occurrences, spec.deltas(), range);

    // 3. Classify occurrences to events
    List<Event> events = new ArrayList<>(classifier.classify(occurrences, spec));

    // 4. Generate weekend events
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

    // 5. Sort deterministically
    return events.stream().sorted().collect(Collectors.toList());
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
