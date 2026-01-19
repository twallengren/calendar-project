package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EventSource(
    String key,
    String name,
    Rule rule,
    @JsonProperty("default_classification")
    EventType defaultClassification
) {
    public EventSource {
        if (defaultClassification == null) {
            defaultClassification = EventType.CLOSED;
        }
    }
}
