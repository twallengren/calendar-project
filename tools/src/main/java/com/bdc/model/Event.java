package com.bdc.model;

import java.time.LocalDate;
import java.util.Objects;

public record Event(LocalDate date, EventType type, String description, String provenance)
    implements Comparable<Event> {
  public Event {
    Objects.requireNonNull(date, "date must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(description, "description must not be null");
  }

  @Override
  public int compareTo(Event other) {
    int dateCompare = this.date.compareTo(other.date);
    if (dateCompare != 0) return dateCompare;
    int typeCompare = this.type.compareTo(other.type);
    if (typeCompare != 0) return typeCompare;
    return this.description.compareTo(other.description);
  }
}
