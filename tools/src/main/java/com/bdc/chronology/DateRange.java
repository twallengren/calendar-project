package com.bdc.chronology;

import java.time.LocalDate;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.stream.Stream;

public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start must not be after end");
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public Stream<LocalDate> stream() {
        return start.datesUntil(end.plusDays(1));
    }

    public int[] hijriYearRange() {
        HijrahDate startHijri = HijrahDate.from(start);
        HijrahDate endHijri = HijrahDate.from(end);
        int startYear = startHijri.get(ChronoField.YEAR);
        int endYear = endHijri.get(ChronoField.YEAR);
        return new int[] { startYear, endYear };
    }

    public int[] isoYearRange() {
        return new int[] { start.getYear(), end.getYear() };
    }
}
