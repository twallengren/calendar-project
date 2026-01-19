package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

public record ModuleSpec(
    String kind,
    String id,
    Policies policies,
    @JsonProperty("event_sources")
    List<EventSource> eventSources
) {
    public ModuleSpec {
        if (!"module".equals(kind)) {
            throw new IllegalArgumentException("kind must be 'module', got: " + kind);
        }
        if (eventSources == null) eventSources = List.of();
    }

    public record Policies(
        List<DayOfWeek> weekends
    ) {
        public Policies {
            if (weekends == null) weekends = List.of();
        }
    }
}
