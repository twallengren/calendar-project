package com.bdc.classifier;

import com.bdc.model.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps occurrences to typed events.
 *
 * <p>Type precedence: delta reclassification for the exact key+date, then the calendar's {@code
 * classifications}, then the event source's {@code default_classification}, then CLOSED.
 */
public class OccurrenceClassifier {

  public List<Event> classify(List<Occurrence> occurrences, ResolvedSpec spec) {
    Context ctx = context(spec);
    List<Event> events = new ArrayList<>(occurrences.size());
    for (Occurrence occ : occurrences) {
      events.add(ctx.toEvent(occ));
    }
    return events;
  }

  /** Builds a reusable classification context for one resolved spec. */
  public Context context(ResolvedSpec spec) {
    return new Context(spec);
  }

  /** Classification lookups for one resolved spec. */
  public static final class Context {
    private final ResolvedSpec spec;
    private final Map<String, EventType> classifications;
    private final Map<String, EventType> sourceDefaults = new HashMap<>();
    private final Map<String, EventSource> sourcesByKey = new HashMap<>();
    private final Map<String, Map<LocalDate, EventType>> deltaReclassifications;

    private Context(ResolvedSpec spec) {
      this.spec = spec;
      this.classifications = spec.classifications();
      this.deltaReclassifications = buildReclassificationMap(spec.deltas());
      for (EventSource source : spec.eventSources()) {
        sourceDefaults.put(source.key(), source.defaultClassification());
        sourcesByKey.put(source.key(), source);
      }
    }

    /** The event type an occurrence will be classified as. */
    public EventType typeOf(Occurrence occ) {
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

    /** Classifies and enriches an occurrence into an event. */
    public Event toEvent(Occurrence occ) {
      EventType type = typeOf(occ);
      EventSource source = sourcesByKey.get(occ.key());
      LocalTime closeTime =
          type == EventType.EARLY_CLOSE && source != null ? source.closeTime() : null;
      EventStatus status = source != null ? source.status() : EventStatus.CONFIRMED;
      String sourceModule = spec.sourceOrigins().get(occ.key());
      if (sourceModule == null
          && occ.provenance() != null
          && occ.provenance().startsWith("delta:")) {
        sourceModule = "delta";
      }
      return new Event(
          occ.date(),
          type,
          occ.name(),
          occ.provenance(),
          occ.key(),
          sourceModule,
          occ.observedFrom(),
          closeTime,
          status);
    }
  }

  private static Map<String, Map<LocalDate, EventType>> buildReclassificationMap(
      List<Delta> deltas) {
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
