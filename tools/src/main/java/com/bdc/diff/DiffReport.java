package com.bdc.diff;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DiffReport(
    DiffSeverity overallSeverity,
    int totalCalendars,
    int calendarsWithChanges,
    Instant generatedAt,
    String blessedVersion,
    String currentSha,
    Map<String, CalendarDiff> calendars
) {
    public static DiffReport create(
        Map<String, CalendarDiff> calendars,
        String blessedVersion,
        String currentSha
    ) {
        DiffSeverity overall = calendars.values().stream()
            .map(CalendarDiff::severity)
            .reduce(DiffSeverity.NONE, DiffReport::maxSeverity);

        int withChanges = (int) calendars.values().stream()
            .filter(CalendarDiff::hasChanges)
            .count();

        return new DiffReport(
            overall,
            calendars.size(),
            withChanges,
            Instant.now(),
            blessedVersion,
            currentSha,
            calendars
        );
    }

    private static DiffSeverity maxSeverity(DiffSeverity a, DiffSeverity b) {
        return a.ordinal() > b.ordinal() ? a : b;
    }
}
