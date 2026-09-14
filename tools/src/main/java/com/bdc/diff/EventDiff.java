package com.bdc.diff;

import com.bdc.model.EventType;
import java.time.LocalDate;

public record EventDiff(
    LocalDate date,
    EventType oldType,
    EventType newType,
    String oldDescription,
    String newDescription,
    DiffKind kind,
    String key) {
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
