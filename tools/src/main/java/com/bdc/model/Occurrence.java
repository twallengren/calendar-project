package com.bdc.model;

import com.bdc.chronology.NativeDate;
import java.time.LocalDate;
import java.util.List;
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
    String key,
    LocalDate date,
    String name,
    String provenance,
    LocalDate observedFrom,
    NativeDate nominalNativeDate,
    List<LocalDate> observationLineage) {
  public Occurrence {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(date, "date must not be null");
    Objects.requireNonNull(name, "name must not be null");
    observationLineage = observationLineage == null ? List.of() : List.copyOf(observationLineage);
  }

  public Occurrence(
      String key, LocalDate date, String name, String provenance, LocalDate observedFrom) {
    this(
        key,
        date,
        name,
        provenance,
        observedFrom,
        null,
        observedFrom == null ? List.of() : List.of(observedFrom, date));
  }

  public Occurrence withNativeDate(NativeDate nativeDate) {
    return new Occurrence(
        key, date, name, provenance, observedFrom, nativeDate, observationLineage);
  }

  public Occurrence(String key, LocalDate date, String name, String provenance) {
    this(key, date, name, provenance, null);
  }

  /** Returns a copy observed on {@code newDate}, remembering the nominal date. */
  public Occurrence observedOn(LocalDate newDate) {
    if (newDate.equals(date)) {
      return this;
    }
    var lineage = new java.util.ArrayList<>(observationLineage);
    if (lineage.isEmpty()) lineage.add(date);
    lineage.add(newDate);
    return new Occurrence(
        key,
        newDate,
        name,
        provenance,
        observedFrom != null ? observedFrom : date,
        nominalNativeDate,
        lineage);
  }
}
