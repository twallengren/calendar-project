package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;
import java.util.List;

/**
 * Defines an event source with optional year constraints.
 *
 * @param key unique identifier for the event
 * @param name display name for the event
 * @param rule the recurrence rule
 * @param defaultClassification event type (CLOSED, EARLY_CLOSE, etc.)
 * @param shiftable whether the event shifts when falling on a weekend
 * @param activeYears list of year ranges when this event is active, null means always active
 */
public record EventSource(
    String key,
    String name,
    Rule rule,
    @JsonProperty("default_classification") EventType defaultClassification,
    Boolean shiftable,
    @JsonProperty("active_years") @JsonDeserialize(using = YearRangeListDeserializer.class)
        List<YearRange> activeYears) {

  /** Represents a range of years. If start is null, it means "from inception". */
  public record YearRange(Integer start, Integer end) {
    /** Creates a single-year range. */
    public YearRange(int year) {
      this(year, year);
    }

    /** Check if a year falls within this range. */
    public boolean contains(int year) {
      if (start != null && year < start) {
        return false;
      }
      if (end != null && year > end) {
        return false;
      }
      return true;
    }
  }

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
    if (activeYears == null || activeYears.isEmpty()) {
      return true;
    }
    int year = date.getYear();
    return activeYears.stream().anyMatch(range -> range.contains(year));
  }
}
