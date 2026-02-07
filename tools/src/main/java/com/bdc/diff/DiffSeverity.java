package com.bdc.diff;

public enum DiffSeverity {
  NONE, // No changes
  MINOR, // Additions outside existing range only (backfilling or future extensions)
  MAJOR // Modifications, removals, or additions within existing date range
}
