package com.bdc.generator;

import com.bdc.chronology.ChronologyTranslator;
import com.bdc.chronology.DateRange;
import com.bdc.formula.ReferenceResolver;
import com.bdc.model.Occurrence;
import com.bdc.model.Rule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public class RuleExpander {

  private ReferenceResolver referenceResolver;

  public void setReferenceResolver(ReferenceResolver referenceResolver) {
    this.referenceResolver = referenceResolver;
  }

  public List<Occurrence> expand(Rule rule, DateRange range, String provenance) {
    return switch (rule) {
      case Rule.ExplicitDates r -> expandExplicitDates(r, range, provenance);
      case Rule.FixedMonthDay r -> expandFixedMonthDay(r, range, provenance);
      case Rule.NthWeekdayOfMonth r -> expandNthWeekday(r, range, provenance);
      case Rule.RelativeToReference r -> expandRelativeToReference(r, range, provenance);
    };
  }

  private List<Occurrence> expandExplicitDates(
      Rule.ExplicitDates rule, DateRange range, String provenance) {
    List<Occurrence> occurrences = new ArrayList<>();
    for (Rule.AnnotatedDate annotatedDate : rule.dates()) {
      if (range.contains(annotatedDate.date())) {
        String effectiveName = annotatedDate.effectiveName(rule.name());
        occurrences.add(
            new Occurrence(rule.key(), annotatedDate.date(), effectiveName, provenance));
      }
    }
    return occurrences;
  }

  private List<Occurrence> expandFixedMonthDay(
      Rule.FixedMonthDay rule, DateRange range, String provenance) {
    List<Occurrence> occurrences = new ArrayList<>();
    String chronology = rule.chronology();

    if (ChronologyTranslator.HIJRI.equalsIgnoreCase(chronology)) {
      int[] hijriYears = range.hijriYearRange();
      for (int year = hijriYears[0]; year <= hijriYears[1]; year++) {
        try {
          LocalDate isoDate = ChronologyTranslator.hijriToIso(year, rule.month(), rule.day());
          if (range.contains(isoDate)) {
            occurrences.add(new Occurrence(rule.key(), isoDate, rule.name(), provenance));
          }
        } catch (Exception e) {
          // Skip invalid dates (e.g., month 12 day 30 in some Hijri years)
        }
      }
    } else {
      // ISO chronology
      int[] isoYears = range.isoYearRange();
      for (int year = isoYears[0]; year <= isoYears[1]; year++) {
        try {
          LocalDate date = LocalDate.of(year, rule.month(), rule.day());
          if (range.contains(date)) {
            occurrences.add(new Occurrence(rule.key(), date, rule.name(), provenance));
          }
        } catch (Exception e) {
          // Skip invalid dates (e.g., Feb 29 in non-leap years)
        }
      }
    }

    return occurrences;
  }

  private List<Occurrence> expandNthWeekday(
      Rule.NthWeekdayOfMonth rule, DateRange range, String provenance) {
    List<Occurrence> occurrences = new ArrayList<>();
    int[] years = range.isoYearRange();

    for (int year = years[0]; year <= years[1]; year++) {
      LocalDate date = nthWeekdayOfMonth(year, rule.month(), rule.weekday(), rule.nth());
      if (date != null && range.contains(date)) {
        occurrences.add(new Occurrence(rule.key(), date, rule.name(), provenance));
      }
    }

    return occurrences;
  }

  private LocalDate nthWeekdayOfMonth(int year, int month, DayOfWeek weekday, int nth) {
    YearMonth ym = YearMonth.of(year, month);
    LocalDate first = ym.atDay(1);

    if (nth > 0) {
      // nth occurrence (1st, 2nd, 3rd, etc.)
      LocalDate firstOccurrence = first.with(TemporalAdjusters.firstInMonth(weekday));
      LocalDate result = firstOccurrence.plusWeeks(nth - 1);
      return result.getMonth() == first.getMonth() ? result : null;
    } else if (nth == -1) {
      // Last occurrence
      return first.with(TemporalAdjusters.lastInMonth(weekday));
    }

    return null;
  }

  private List<Occurrence> expandRelativeToReference(
      Rule.RelativeToReference rule, DateRange range, String provenance) {
    List<LocalDate> refDates;

    if (rule.usesNamedReference()) {
      // Named reference (e.g., "easter")
      if (referenceResolver == null) {
        throw new IllegalStateException("ReferenceResolver not set");
      }
      if (!referenceResolver.hasReference(rule.reference())) {
        throw new IllegalArgumentException("Unknown reference: " + rule.reference());
      }
      refDates = referenceResolver.getDates(rule.reference());
    } else if (rule.usesFixedReference()) {
      // Fixed month/day reference - generate for each year in range
      refDates = new ArrayList<>();
      int[] years = range.isoYearRange();
      for (int year = years[0]; year <= years[1]; year++) {
        try {
          LocalDate refDate = LocalDate.of(year, rule.referenceMonth(), rule.referenceDay());
          refDates.add(refDate);
        } catch (Exception e) {
          // Skip invalid dates
        }
      }
    } else {
      throw new IllegalArgumentException(
          "RelativeToReference must have either a named reference or referenceMonth/referenceDay");
    }

    List<Occurrence> occurrences = new ArrayList<>();
    for (LocalDate refDate : refDates) {
      LocalDate date;
      if (rule.usesWeekdayOffset()) {
        date = calculateWeekdayOffset(refDate, rule.offsetWeekday());
      } else if (rule.offsetDays() != null) {
        date = refDate.plusDays(rule.offsetDays());
      } else {
        throw new IllegalArgumentException(
            "RelativeToReference must have either offsetDays or offsetWeekday");
      }

      if (range.contains(date)) {
        occurrences.add(new Occurrence(rule.key(), date, rule.name(), provenance));
      }
    }
    return occurrences;
  }

  /**
   * Calculate the nth weekday before or after a reference date.
   *
   * <p>For example, "1st Tuesday after November 1st" for Election Day. Note that this finds the
   * weekday strictly after (or before) the reference date, not including the reference date itself.
   *
   * @throws IllegalArgumentException if nth is less than 1
   */
  private LocalDate calculateWeekdayOffset(LocalDate refDate, Rule.WeekdayOffset offset) {
    DayOfWeek targetWeekday = offset.weekday();
    int nth = offset.nth();
    Rule.OffsetDirection direction = offset.direction();

    if (nth < 1) {
      throw new IllegalArgumentException("WeekdayOffset nth must be at least 1, got: " + nth);
    }

    if (direction == Rule.OffsetDirection.AFTER) {
      // Find the nth occurrence of weekday strictly after refDate
      // Start from the day after refDate
      LocalDate current = refDate.plusDays(1);

      // Find the first occurrence of the weekday after refDate
      while (current.getDayOfWeek() != targetWeekday) {
        current = current.plusDays(1);
      }

      // Then advance by (nth - 1) weeks
      return current.plusWeeks(nth - 1);
    } else {
      // BEFORE: Find the nth occurrence of weekday strictly before refDate
      LocalDate current = refDate.minusDays(1);

      // Find the first occurrence of the weekday before refDate
      while (current.getDayOfWeek() != targetWeekday) {
        current = current.minusDays(1);
      }

      // Then go back by (nth - 1) weeks
      return current.minusWeeks(nth - 1);
    }
  }
}
