package com.bdc.diff;

import java.time.LocalDate;
import java.util.List;

public record CalendarDiff(
    String calendarId,
    DiffSeverity severity,
    List<EventDiff> additions,
    List<EventDiff> removals,
    List<EventDiff> modifications,
    LocalDate cutoffDate,
    LocalDate blessedRangeStart,
    LocalDate blessedRangeEnd) {
  public int totalChanges() {
    return additions.size() + removals.size() + modifications.size();
  }

  public boolean hasChanges() {
    return totalChanges() > 0;
  }
}
