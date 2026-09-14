package com.bdc.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Objects;

/**
 * A generated calendar event.
 *
 * @param date the date the event is observed on
 * @param type the event type
 * @param description display text
 * @param provenance internal origin string (not emitted)
 * @param key the event source key ({@code weekend} for weekend rows)
 * @param sourceModule the module or calendar that declared the event source
 * @param observedFrom the nominal date when the event was shifted off a weekend, else null
 * @param closeTime local close time for EARLY_CLOSE events, else null
 * @param status CONFIRMED or PROJECTED
 */
public record Event(
    LocalDate date,
    EventType type,
    String description,
    String provenance,
    String key,
    String sourceModule,
    LocalDate observedFrom,
    LocalTime closeTime,
    EventStatus status)
    implements Comparable<Event> {

  private static final Comparator<String> NULLS_LAST =
      Comparator.nullsLast(Comparator.naturalOrder());

  public Event {
    Objects.requireNonNull(date, "date must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(description, "description must not be null");
    if (status == null) status = EventStatus.CONFIRMED;
    if (status == EventStatus.UNKNOWN) {
      throw new IllegalArgumentException("event status must be CONFIRMED or PROJECTED");
    }
  }

  /** Legacy constructor without enrichment fields. */
  public Event(LocalDate date, EventType type, String description, String provenance) {
    this(date, type, description, provenance, null, null, null, null, null);
  }

  /** True if this event is a full-day closure. */
  public boolean isClosure() {
    return type == EventType.CLOSED;
  }

  @Override
  public int compareTo(Event other) {
    int dateCompare = this.date.compareTo(other.date);
    if (dateCompare != 0) return dateCompare;
    int typeCompare = this.type.compareTo(other.type);
    if (typeCompare != 0) return typeCompare;
    int descCompare = this.description.compareTo(other.description);
    if (descCompare != 0) return descCompare;
    return NULLS_LAST.compare(this.key, other.key);
  }
}
