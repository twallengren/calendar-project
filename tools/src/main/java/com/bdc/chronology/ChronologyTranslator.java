package com.bdc.chronology;

import java.time.LocalDate;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

public class ChronologyTranslator {

    public static final String ISO = "ISO";
    public static final String HIJRI = "HIJRI";

    public static LocalDate toIsoDate(int year, int month, int day, String chronology) {
        if (chronology == null || ISO.equalsIgnoreCase(chronology)) {
            return LocalDate.of(year, month, day);
        }

        if (HIJRI.equalsIgnoreCase(chronology)) {
            HijrahDate hijrahDate = HijrahChronology.INSTANCE.date(year, month, day);
            return LocalDate.from(hijrahDate);
        }

        throw new IllegalArgumentException("Unsupported chronology: " + chronology);
    }

    public static LocalDate hijriToIso(int hijriYear, int hijriMonth, int hijriDay) {
        HijrahDate hijrahDate = HijrahChronology.INSTANCE.date(hijriYear, hijriMonth, hijriDay);
        return LocalDate.from(hijrahDate);
    }

    public static HijrahDate isoToHijri(LocalDate isoDate) {
        return HijrahDate.from(isoDate);
    }

    public static int getHijriYear(LocalDate isoDate) {
        return isoToHijri(isoDate).get(ChronoField.YEAR);
    }

    public static int getHijriMonth(LocalDate isoDate) {
        return isoToHijri(isoDate).get(ChronoField.MONTH_OF_YEAR);
    }

    public static int getHijriDay(LocalDate isoDate) {
        return isoToHijri(isoDate).get(ChronoField.DAY_OF_MONTH);
    }

    public static boolean isValidHijriDate(int year, int month, int day) {
        try {
            HijrahChronology.INSTANCE.date(year, month, day);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
