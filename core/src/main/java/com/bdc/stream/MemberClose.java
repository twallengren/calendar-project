package com.bdc.stream;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/** One calendar's local early-close time; timezone is null for honest legacy metadata. */
public record MemberClose(String calendarId, ZoneId timezone, LocalTime localTime) {
  public MemberClose {
    Objects.requireNonNull(calendarId, "calendarId must not be null");
    Objects.requireNonNull(localTime, "localTime must not be null");
  }
}
