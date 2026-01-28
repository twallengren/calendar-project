package com.bdc.classifier;

import com.bdc.model.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OccurrenceClassifier {

  public List<Event> classify(List<Occurrence> occurrences, ResolvedSpec spec) {
    Map<String, EventType> classifications = spec.classifications();
    Map<String, Map<LocalDate, EventType>> deltaReclassifications =
        buildReclassificationMap(spec.deltas());

    // Build a map of event source keys to their default classifications
    Map<String, EventType> sourceDefaults = new HashMap<>();
    for (EventSource source : spec.eventSources()) {
      sourceDefaults.put(source.key(), source.defaultClassification());
    }

    List<Event> events = new ArrayList<>();

    for (Occurrence occ : occurrences) {
      EventType type = determineType(occ, classifications, sourceDefaults, deltaReclassifications);
      events.add(new Event(occ.date(), type, occ.name(), occ.provenance()));
    }

    return events;
  }

  private EventType determineType(
      Occurrence occ,
      Map<String, EventType> classifications,
      Map<String, EventType> sourceDefaults,
      Map<String, Map<LocalDate, EventType>> deltaReclassifications) {

    // 1. Check delta reclassifications first (highest priority)
    Map<LocalDate, EventType> byDate = deltaReclassifications.get(occ.key());
    if (byDate != null && byDate.containsKey(occ.date())) {
      return byDate.get(occ.date());
    }

    // 2. Check explicit classifications in spec
    if (classifications.containsKey(occ.key())) {
      return classifications.get(occ.key());
    }

    // 3. Fall back to event source default
    if (sourceDefaults.containsKey(occ.key())) {
      return sourceDefaults.get(occ.key());
    }

    // 4. Ultimate default
    return EventType.CLOSED;
  }

  private Map<String, Map<LocalDate, EventType>> buildReclassificationMap(List<Delta> deltas) {
    Map<String, Map<LocalDate, EventType>> result = new HashMap<>();

    for (Delta delta : deltas) {
      if (delta instanceof Delta.Reclassify r) {
        result.computeIfAbsent(r.key(), k -> new HashMap<>()).put(r.date(), r.newClassification());
      } else if (delta instanceof Delta.Add a && a.classification() != null) {
        result.computeIfAbsent(a.key(), k -> new HashMap<>()).put(a.date(), a.classification());
      }
    }

    return result;
  }
}
