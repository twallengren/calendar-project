package com.bdc.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single dated occurrence of an event source, before classification.
 *
 * @param key the event source key
 * @param date the (possibly observed) date
 * @param name the display name
 * @param provenance where the occurrence came from (calendar:key, delta:add, ...)
 * @param observedFrom the nominal date when the occurrence was shifted off a weekend, else null
 */
public record Occurrence(
    String key, LocalDate date, String name, String provenance, LocalDate observedFrom) {
  public Occurrence {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(date, "date must not be null");
    Objects.requireNonNull(name, "name must not be null");
  }

  public Occurrence(String key, LocalDate date, String name, String provenance) {
    this(key, date, name, provenance, null);
  }

  /** Returns a copy observed on {@code newDate}, remembering the nominal date. */
  public Occurrence observedOn(LocalDate newDate) {
    if (newDate.equals(date)) {
      return this;
    }
    return new Occurrence(
        key, newDate, name, provenance, observedFrom != null ? observedFrom : date);
  }
}
