package com.bdc.diff;

public enum DiffSeverity {
  NONE, // No changes
  MINOR, // Only future date additions (non-breaking)
  MAJOR // Any other change (breaking)
}
