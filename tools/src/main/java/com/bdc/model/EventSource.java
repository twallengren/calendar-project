package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EventSource(
    String key,
    String name,
    Rule rule,
    @JsonProperty("default_classification") EventType defaultClassification,
    Boolean shiftable) {
  public EventSource {
    if (defaultClassification == null) {
      defaultClassification = EventType.CLOSED;
    }
    // Default: fixed_month_day rules are shiftable, others are not
    if (shiftable == null) {
      shiftable = (rule instanceof Rule.FixedMonthDay);
    }
  }
}
