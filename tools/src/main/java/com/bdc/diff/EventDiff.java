package com.bdc.diff;

import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import java.time.LocalDate;
import java.time.LocalTime;

public record EventDiff(
    LocalDate date,
    EventType oldType,
    EventType newType,
    String oldDescription,
    String newDescription,
    DiffKind kind,
    String key,
    PublishedFields oldFields,
    PublishedFields newFields) {
  public EventDiff(
      LocalDate date,
      EventType oldType,
      EventType newType,
      String oldDescription,
      String newDescription,
      DiffKind kind,
      String key) {
    this(
        date,
        oldType,
        newType,
        oldDescription,
        newDescription,
        kind,
        key,
        oldType == null ? null : new PublishedFields(null, null, null, EventStatus.CONFIRMED),
        newType == null ? null : new PublishedFields(null, null, null, EventStatus.CONFIRMED));
  }

  /** Published enrichment, excluding the reader's synthetic in-memory provenance label. */
  public record PublishedFields(
      String sourceModule, LocalDate observedFrom, LocalTime closeTime, EventStatus status) {
    public static PublishedFields of(Event event) {
      return new PublishedFields(
          event.sourceModule(), event.observedFrom(), event.closeTime(), event.status());
    }
  }

  public enum DiffKind {
    ADDED,
    REMOVED,
    MODIFIED
  }

  public static EventDiff added(LocalDate date, EventType type, String description) {
    return added(date, type, description, null);
  }

  public static EventDiff added(LocalDate date, EventType type, String description, String key) {
    return new EventDiff(date, null, type, null, description, DiffKind.ADDED, key);
  }

  public static EventDiff removed(LocalDate date, EventType type, String description) {
    return removed(date, type, description, null);
  }

  public static EventDiff removed(LocalDate date, EventType type, String description, String key) {
    return new EventDiff(date, type, null, description, null, DiffKind.REMOVED, key);
  }

  public static EventDiff modified(
      LocalDate date,
      EventType oldType,
      EventType newType,
      String oldDescription,
      String newDescription) {
    return modified(date, oldType, newType, oldDescription, newDescription, null);
  }

  public static EventDiff modified(
      LocalDate date,
      EventType oldType,
      EventType newType,
      String oldDescription,
      String newDescription,
      String key) {
    return new EventDiff(
        date, oldType, newType, oldDescription, newDescription, DiffKind.MODIFIED, key);
  }

  public boolean isHistorical(LocalDate cutoffDate) {
    return !date.isAfter(cutoffDate);
  }
}
