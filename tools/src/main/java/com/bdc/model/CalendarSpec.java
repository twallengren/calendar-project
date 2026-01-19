package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record CalendarSpec(
    String kind,
    String id,
    Metadata metadata,
    @JsonProperty("extends")
    List<String> extendsList,
    List<String> uses,
    @JsonProperty("event_sources")
    List<EventSource> eventSources,
    Map<String, EventType> classifications,
    List<Delta> deltas
) {
    public CalendarSpec {
        if (!"calendar".equals(kind)) {
            throw new IllegalArgumentException("kind must be 'calendar', got: " + kind);
        }
        if (extendsList == null) extendsList = List.of();
        if (uses == null) uses = List.of();
        if (eventSources == null) eventSources = List.of();
        if (classifications == null) classifications = Map.of();
        if (deltas == null) deltas = List.of();
    }

    public record Metadata(
        String name,
        String description,
        String chronology
    ) {
        public Metadata {
            if (chronology == null) chronology = "ISO";
        }
    }
}
