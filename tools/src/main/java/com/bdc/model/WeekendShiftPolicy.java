package com.bdc.model;

public enum WeekendShiftPolicy {
  /** No shifting - weekend holidays stay on weekends */
  NONE,

  /** US-style: Saturday shifts to Friday, Sunday shifts to Monday (independent) */
  NEAREST_WEEKDAY,

  /** UK-style: Weekend holidays shift to next available weekday (cascading) */
  NEXT_AVAILABLE_WEEKDAY
}
