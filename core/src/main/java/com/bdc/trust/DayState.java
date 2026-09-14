package com.bdc.trust;

/** The effective business-day state, or UNKNOWN when completeness is unresolved. */
public enum DayState {
  OPEN,
  CLOSED,
  EARLY_CLOSE,
  UNKNOWN
}
