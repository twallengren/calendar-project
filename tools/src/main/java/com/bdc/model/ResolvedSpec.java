package com.bdc.model;

import java.util.List;
import java.util.Map;

public record ResolvedSpec(
    String id,
    CalendarSpec.Metadata metadata,
    WeekendPolicy weekendPolicy,
    List<Reference> references,
    List<EventSource> eventSources,
    Map<String, EventType> classifications,
    List<Delta> deltas,
    List<String> resolutionChain) {
  public ResolvedSpec {
    if (weekendPolicy == null) weekendPolicy = WeekendPolicy.SAT_SUN;
    if (references == null) references = List.of();
    if (eventSources == null) eventSources = List.of();
    if (classifications == null) classifications = Map.of();
    if (deltas == null) deltas = List.of();
    if (resolutionChain == null) resolutionChain = List.of();
  }
}
