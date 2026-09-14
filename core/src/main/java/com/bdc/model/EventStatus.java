package com.bdc.model;

/**
 * Confidence status of an event, and of a query answer.
 *
 * <p>{@code CONFIRMED} events are backed by an authoritative announcement (an exchange circular,
 * gazette notice, or published calendar). {@code PROJECTED} events are computed from a rule and may
 * change when the authority announces the actual dates (typical for observation-based calendars
 * such as Umm al-Qura).
 *
 * <p>{@code UNKNOWN} is never carried by a generated event: it is only produced by {@link
 * com.bdc.stream.DateStream#status(java.time.LocalDate)} for a date outside the calendar's covered
 * range, where the absence of a closure means "not known", not "open".
 */
public enum EventStatus {
  CONFIRMED,
  PROJECTED,
  UNKNOWN
}
