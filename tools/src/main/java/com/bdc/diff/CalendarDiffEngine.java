package com.bdc.diff;

import com.bdc.model.Event;
import com.bdc.model.EventType;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class CalendarDiffEngine {

    public CalendarDiff compare(
        String calendarId,
        List<Event> generated,
        List<Event> blessed,
        LocalDate cutoffDate
    ) {
        // Create maps keyed by date for comparison
        // Events are keyed by date since there should be at most one event per date
        Map<LocalDate, Event> generatedByDate = generated.stream()
            .collect(Collectors.toMap(Event::date, e -> e, (a, b) -> a));

        Map<LocalDate, Event> blessedByDate = blessed.stream()
            .collect(Collectors.toMap(Event::date, e -> e, (a, b) -> a));

        Set<LocalDate> allDates = new HashSet<>();
        allDates.addAll(generatedByDate.keySet());
        allDates.addAll(blessedByDate.keySet());

        List<EventDiff> additions = new ArrayList<>();
        List<EventDiff> removals = new ArrayList<>();
        List<EventDiff> modifications = new ArrayList<>();

        for (LocalDate date : allDates) {
            Event gen = generatedByDate.get(date);
            Event ref = blessedByDate.get(date);

            if (gen != null && ref == null) {
                // Event was added
                additions.add(EventDiff.added(date, gen.type(), gen.description()));
            } else if (gen == null && ref != null) {
                // Event was removed
                removals.add(EventDiff.removed(date, ref.type(), ref.description()));
            } else if (gen != null && ref != null) {
                // Check for modifications
                if (!gen.type().equals(ref.type()) || !gen.description().equals(ref.description())) {
                    modifications.add(EventDiff.modified(
                        date, ref.type(), gen.type(), ref.description(), gen.description()));
                }
            }
        }

        // Sort by date
        additions.sort(Comparator.comparing(EventDiff::date));
        removals.sort(Comparator.comparing(EventDiff::date));
        modifications.sort(Comparator.comparing(EventDiff::date));

        DiffSeverity severity = classifySeverity(additions, removals, modifications, cutoffDate);

        return new CalendarDiff(
            calendarId,
            severity,
            Collections.unmodifiableList(additions),
            Collections.unmodifiableList(removals),
            Collections.unmodifiableList(modifications),
            cutoffDate
        );
    }

    public DiffSeverity classifySeverity(
        List<EventDiff> additions,
        List<EventDiff> removals,
        List<EventDiff> modifications,
        LocalDate cutoffDate
    ) {
        // Any removal = MAJOR
        if (!removals.isEmpty()) {
            return DiffSeverity.MAJOR;
        }

        // Any modification = MAJOR
        if (!modifications.isEmpty()) {
            return DiffSeverity.MAJOR;
        }

        // Historical additions = MAJOR (shouldn't add past events)
        boolean hasHistoricalAdditions = additions.stream()
            .anyMatch(e -> e.isHistorical(cutoffDate));
        if (hasHistoricalAdditions) {
            return DiffSeverity.MAJOR;
        }

        // Future additions only = MINOR
        if (!additions.isEmpty()) {
            return DiffSeverity.MINOR;
        }

        return DiffSeverity.NONE;
    }

    public DiffSeverity aggregateSeverity(Collection<CalendarDiff> diffs) {
        return diffs.stream()
            .map(CalendarDiff::severity)
            .reduce(DiffSeverity.NONE, (a, b) -> a.ordinal() > b.ordinal() ? a : b);
    }
}
