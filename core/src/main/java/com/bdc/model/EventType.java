package com.bdc.model;

public enum EventType {
  CLOSED,
  EARLY_CLOSE,
  NOTABLE,
  PERIOD_MARKER,
  WEEKEND,
  /**
   * A trading session on a date that is otherwise not a business day (a holiday or a weekend day),
   * with its own open and close time. It coexists with that date's CLOSED or WEEKEND row and does
   * not make the date a business day.
   */
  SPECIAL_SESSION
}
