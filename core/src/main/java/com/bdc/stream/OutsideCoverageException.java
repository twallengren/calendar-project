package com.bdc.stream;

import com.bdc.chronology.DateRange;
import java.time.LocalDate;

/**
 * Thrown when a date falls outside the window a {@link DateStream} can answer for: the declared
 * {@code coverage} of a YAML-backed calendar or the generated range of a published artifact.
 *
 * <p>Outside that window the absence of a closure row means "not known", not "open", so the API
 * refuses to answer rather than implying a trading day. {@link DateStream#status(LocalDate)}
 * returns {@code UNKNOWN} for the same dates without throwing.
 *
 * <p>It extends {@link IllegalArgumentException} so existing callers that catch bad arguments keep
 * working.
 */
public class OutsideCoverageException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  private final String calendarId;
  private final LocalDate date;
  private final transient DateRange range;

  public OutsideCoverageException(String calendarId, LocalDate date, DateRange range) {
    super(
        date
            + " is outside the covered range of "
            + calendarId
            + " ("
            + range.start()
            + " to "
            + range.end()
            + ")");
    this.calendarId = calendarId;
    this.date = date;
    this.range = range;
  }

  /** The calendar that could not answer. */
  public String calendarId() {
    return calendarId;
  }

  /** The date that was asked for. */
  public LocalDate date() {
    return date;
  }

  /** The range the calendar can answer for. */
  public DateRange range() {
    return range;
  }
}
