package com.bdc.generator;

import com.bdc.chronology.ChronologyTranslator;
import com.bdc.chronology.DateRange;
import com.bdc.chronology.ontology.ChronologyRegistry;
import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;
import com.bdc.formula.ReferenceResolver;
import com.bdc.model.Occurrence;
import com.bdc.model.Rule;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Expands rules into dated occurrences within a range.
 *
 * <p>Multi-day rules ({@code end_month}/{@code end_day} or {@code duration_days}) yield one
 * occurrence per day, all sharing the rule's key and name. Dates that do not exist in a given year
 * (Feb 29, the 30th of a short lunar month) are skipped; everything else is an error.
 */
public class RuleExpander {

  private ReferenceResolver referenceResolver;

  public void setReferenceResolver(ReferenceResolver referenceResolver) {
    this.referenceResolver = referenceResolver;
  }

  public List<Occurrence> expand(Rule rule, DateRange range, String provenance) {
    return switch (rule) {
      case Rule.NativeRecurring r -> expandNativeRecurring(r, range, provenance);
      case Rule.NativeExplicitDates r -> expandNativeExplicit(r, range, provenance);
      case Rule.ExplicitDates r -> expandExplicitDates(r, range, provenance);
      case Rule.FixedMonthDay r -> expandFixedMonthDay(r, range, provenance);
      case Rule.NthWeekdayOfMonth r -> expandNthWeekday(r, range, provenance);
      case Rule.RelativeToReference r -> expandRelativeToReference(r, range, provenance);
    };
  }

  private List<Occurrence> expandNativeExplicit(
      Rule.NativeExplicitDates rule, DateRange range, String provenance) {
    List<Occurrence> result = new ArrayList<>();
    for (var nativeDate : rule.dates()) {
      var date =
          com.bdc.chronology.ChronologyProviders.get(nativeDate.chronologyId()).toIso(nativeDate);
      if (range.contains(date))
        result.add(new Occurrence(rule.key(), date, rule.name(), provenance));
    }
    return result;
  }

  private List<Occurrence> expandNativeRecurring(
      Rule.NativeRecurring rule, DateRange range, String provenance) {
    var provider = com.bdc.chronology.ChronologyProviders.get(rule.chronology());
    rule.monthCodes().forEach(provider::validateMonthCode);
    var descriptor = provider.descriptor();
    LocalDate first =
        range.start().isBefore(descriptor.supportedFrom())
            ? descriptor.supportedFrom()
            : range.start();
    LocalDate last =
        range.end().isAfter(descriptor.supportedTo()) ? descriptor.supportedTo() : range.end();
    if (last.isBefore(first)) return List.of();
    int firstYear = provider.fromIso(first).year(), lastYear = provider.fromIso(last).year();
    List<Occurrence> result = new ArrayList<>();
    for (int year = firstYear; year <= lastYear; year++) {
      if (!rule.includesYear(year)) continue;
      for (String month : rule.monthCodes()) {
        if (!provider.months(year).contains(month)) continue;
        int day =
            switch (rule) {
              case Rule.NativeFixedMonthDay r -> r.day();
              case Rule.NativeRelativeToReference r -> r.day();
              case Rule.NativeNthWeekday r -> 1;
            };
        if (day > provider.maximumDayOfMonth(month))
          throw new IllegalArgumentException("Day cannot exist in chronology: " + day);
        if (day > provider.monthLength(year, month)) continue;
        LocalDate date = provider.monthStart(year, month).plusDays(day - 1);
        if (rule instanceof Rule.NativeRelativeToReference r) date = date.plusDays(r.offsetDays());
        if (rule instanceof Rule.NativeNthWeekday r) {
          LocalDate end = date.plusDays(provider.monthLength(year, month) - 1);
          date =
              r.nth() == -1
                  ? end.with(TemporalAdjusters.previousOrSame(r.weekday()))
                  : date.with(TemporalAdjusters.nextOrSame(r.weekday())).plusWeeks(r.nth() - 1);
          if (date.isAfter(end)) continue;
        }
        addSpan(result, rule, date, date.plusDays(rule.spanDays() - 1), range, provenance);
      }
    }
    return result;
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
    ChronologyRegistry registry = ChronologyRegistry.getInstance();
    if (!registry.hasChronology(chronology)) {
      throw new IllegalArgumentException(
          "Unknown chronology '" + chronology + "' in rule '" + rule.key() + "'");
    }

    int[] years = clampedYearRange(range, chronology);
    for (int year = years[0]; year <= years[1]; year++) {
      LocalDate start;
      try {
        start = ChronologyTranslator.toIsoDate(year, rule.month(), rule.day(), chronology);
      } catch (IllegalArgumentException | DateTimeException e) {
        // Date does not exist this year (Feb 29, day 30 of a 29-day lunar month)
        continue;
      }

      LocalDate end = start;
      if (rule.hasEndDate()) {
        try {
          end = ChronologyTranslator.toIsoDate(year, rule.endMonth(), rule.endDay(), chronology);
          if (end.isBefore(start)) {
            end =
                ChronologyTranslator.toIsoDate(
                    year + 1, rule.endMonth(), rule.endDay(), chronology);
          }
        } catch (IllegalArgumentException | DateTimeException e) {
          continue;
        }
      } else if (rule.spanDays() > 1) {
        end = start.plusDays(rule.spanDays() - 1);
      }
      addSpan(occurrences, rule, start, end, range, provenance);
    }

    return occurrences;
  }

  /**
   * The chronology years touched by {@code range}, clamped to the chronology's supported range for
   * table-based chronologies. Returns an empty range ({@code [1, 0]}) when the request lies
   * entirely outside the table.
   */
  static int[] clampedYearRange(DateRange range, String chronology) {
    ChronologyRegistry registry = ChronologyRegistry.getInstance();
    try {
      return ChronologyTranslator.getYearRange(range.start(), range.end(), chronology);
    } catch (IllegalArgumentException outOfTable) {
      ChronologyAlgorithm algorithm = registry.getAlgorithm(chronology);
      Optional<int[]> supported = algorithm.supportedYearRange();
      if (supported.isEmpty()) {
        throw outOfTable;
      }
      int min = supported.get()[0];
      int max = supported.get()[1];
      LocalDate tableStart = registry.toIsoDate(min, 1, 1, chronology);
      LocalDate tableEnd =
          registry.toIsoDate(max, 12, algorithm.getDaysInMonth(max, 12), chronology);
      if (range.end().isBefore(tableStart) || range.start().isAfter(tableEnd)) {
        return new int[] {1, 0};
      }
      int startYear =
          range.start().isBefore(tableStart)
              ? min
              : registry.fromIsoDate(range.start(), chronology).year();
      int endYear =
          range.end().isAfter(tableEnd)
              ? max
              : registry.fromIsoDate(range.end(), chronology).year();
      return new int[] {startYear, endYear};
    }
  }

  private List<Occurrence> expandNthWeekday(
      Rule.NthWeekdayOfMonth rule, DateRange range, String provenance) {
    List<Occurrence> occurrences = new ArrayList<>();
    int[] years = range.isoYearRange();

    for (int year = years[0]; year <= years[1]; year++) {
      LocalDate date = nthWeekdayOfMonth(year, rule.month(), rule.weekday(), rule.nth());
      if (date != null) {
        addSpan(occurrences, rule, date, date.plusDays(rule.spanDays() - 1), range, provenance);
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

    // nth == 0 or nth < -1 is rejected by validation; produce nothing here
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
          refDates.add(LocalDate.of(year, rule.referenceMonth(), rule.referenceDay()));
        } catch (DateTimeException e) {
          // Skip invalid dates (e.g., Feb 29 in non-leap years)
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
      addSpan(occurrences, rule, date, date.plusDays(rule.spanDays() - 1), range, provenance);
    }
    return occurrences;
  }

  private static void addSpan(
      List<Occurrence> occurrences,
      Rule rule,
      LocalDate start,
      LocalDate end,
      DateRange range,
      String provenance) {
    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      if (range.contains(d)) {
        occurrences.add(new Occurrence(rule.key(), d, rule.name(), provenance));
      }
    }
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
      LocalDate current = refDate.plusDays(1);
      while (current.getDayOfWeek() != targetWeekday) {
        current = current.plusDays(1);
      }
      return current.plusWeeks(nth - 1);
    } else {
      LocalDate current = refDate.minusDays(1);
      while (current.getDayOfWeek() != targetWeekday) {
        current = current.minusDays(1);
      }
      return current.minusWeeks(nth - 1);
    }
  }
}
