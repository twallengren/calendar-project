package com.bdc.stream;

import com.bdc.model.EventStatus;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * A trading session on a date that is not a business day (see {@link
 * com.bdc.model.EventType#SPECIAL_SESSION}), with the calendar and timezone its local times belong
 * to; timezone is null only for legacy metadata.
 */
public record SpecialSession(
    String calendarId,
    ZoneId timezone,
    LocalTime openTime,
    LocalTime closeTime,
    String key,
    String description,
    EventStatus status) {
  public SpecialSession {
    Objects.requireNonNull(calendarId, "calendarId must not be null");
    Objects.requireNonNull(openTime, "openTime must not be null");
    Objects.requireNonNull(closeTime, "closeTime must not be null");
  }
}
