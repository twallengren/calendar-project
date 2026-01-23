package com.bdc.diff;

import com.bdc.model.EventType;

import java.time.LocalDate;

public record EventDiff(
    LocalDate date,
    EventType oldType,
    EventType newType,
    String oldDescription,
    String newDescription,
    DiffKind kind
) {
    public enum DiffKind {
        ADDED,
        REMOVED,
        MODIFIED
    }

    public static EventDiff added(LocalDate date, EventType type, String description) {
        return new EventDiff(date, null, type, null, description, DiffKind.ADDED);
    }

    public static EventDiff removed(LocalDate date, EventType type, String description) {
        return new EventDiff(date, type, null, description, null, DiffKind.REMOVED);
    }

    public static EventDiff modified(LocalDate date, EventType oldType, EventType newType,
                                     String oldDescription, String newDescription) {
        return new EventDiff(date, oldType, newType, oldDescription, newDescription, DiffKind.MODIFIED);
    }

    public boolean isHistorical(LocalDate cutoffDate) {
        return !date.isAfter(cutoffDate);
    }
}
