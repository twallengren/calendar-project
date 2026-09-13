package com.bdc.model;

import java.util.List;
import java.util.Map;

/**
 * A fully resolved calendar: parents and modules flattened, event sources deduplicated by key.
 *
 * @param sourceOrigins for each event source key, the resolution-chain entry that declared it (e.g.
 *     {@code module:christmas} or {@code calendar:US-NYSE}); later declarations win
 */
public record ResolvedSpec(
    String id,
    CalendarSpec.Metadata metadata,
    WeekendPolicy weekendPolicy,
    WeekendShiftPolicy weekendShiftPolicy,
    List<Reference> references,
    List<EventSource> eventSources,
    Map<String, EventType> classifications,
    List<Delta> deltas,
    List<String> resolutionChain,
    Map<String, String> sourceOrigins) {
  public ResolvedSpec {
    if (weekendPolicy == null) weekendPolicy = WeekendPolicy.SAT_SUN;
    if (weekendShiftPolicy == null) weekendShiftPolicy = WeekendShiftPolicy.NONE;
    if (references == null) references = List.of();
    if (eventSources == null) eventSources = List.of();
    if (classifications == null) classifications = Map.of();
    if (deltas == null) deltas = List.of();
    if (resolutionChain == null) resolutionChain = List.of();
    if (sourceOrigins == null) sourceOrigins = Map.of();
  }

  /** Legacy constructor without source origins. */
  public ResolvedSpec(
      String id,
      CalendarSpec.Metadata metadata,
      WeekendPolicy weekendPolicy,
      WeekendShiftPolicy weekendShiftPolicy,
      List<Reference> references,
      List<EventSource> eventSources,
      Map<String, EventType> classifications,
      List<Delta> deltas,
      List<String> resolutionChain) {
    this(
        id,
        metadata,
        weekendPolicy,
        weekendShiftPolicy,
        references,
        eventSources,
        classifications,
        deltas,
        resolutionChain,
        null);
  }

  /** Looks up an event source by key. */
  public EventSource eventSource(String key) {
    for (EventSource source : eventSources) {
      if (source.key().equals(key)) {
        return source;
      }
    }
    return null;
  }

  /** The declared timezone, or null. */
  public String timezone() {
    return metadata != null ? metadata.timezone() : null;
  }

  /** The declared coverage, or null. */
  public CalendarSpec.Coverage coverage() {
    return metadata != null ? metadata.coverage() : null;
  }
}
