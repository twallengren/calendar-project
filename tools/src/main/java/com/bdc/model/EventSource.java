package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Defines an event source with optional date constraints.
 *
 * @param key unique identifier for the event
 * @param name display name for the event
 * @param rule the recurrence rule
 * @param defaultClassification event type (CLOSED, EARLY_CLOSE, etc.)
 * @param shiftable whether the event shifts when falling on a weekend
 * @param startDate first date the event is active (inclusive), null means no constraint
 * @param endDate last date the event is active (inclusive), null means no constraint
 */
public record EventSource(
    String key,
    String name,
    Rule rule,
    @JsonProperty("default_classification") EventType defaultClassification,
    Boolean shiftable,
    @JsonProperty("start_date") LocalDate startDate,
    @JsonProperty("end_date") LocalDate endDate) {
  public EventSource {
    if (defaultClassification == null) {
      defaultClassification = EventType.CLOSED;
    }
    // Default: fixed_month_day rules are shiftable, others are not
    if (shiftable == null) {
      shiftable = (rule instanceof Rule.FixedMonthDay);
    }
  }

  /** Check if this event source is active on a given date. */
  public boolean isActiveOn(LocalDate date) {
    if (startDate != null && date.isBefore(startDate)) {
      return false;
    }
    if (endDate != null && date.isAfter(endDate)) {
      return false;
    }
    return true;
  }
}
