package com.bdc.model;

/**
 * Confidence status of an event.
 *
 * <p>{@code CONFIRMED} events are backed by an authoritative announcement (an exchange circular,
 * gazette notice, or published calendar). {@code PROJECTED} events are computed from a rule and may
 * change when the authority announces the actual dates (typical for observation-based calendars
 * such as Umm al-Qura).
 */
public enum EventStatus {
  CONFIRMED,
  PROJECTED
}
