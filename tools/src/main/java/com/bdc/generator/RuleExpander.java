package com.bdc.generator;

import com.bdc.chronology.ChronologyTranslator;
import com.bdc.chronology.DateArithmetic;
import com.bdc.chronology.DateRange;
import com.bdc.chronology.ontology.ChronologyRegistry;
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

public class RuleExpander {

  private ReferenceResolver referenceResolver;

  public void setReferenceResolver(ReferenceResolver referenceResolver) {
    this.referenceResolver = referenceResolver;
  }

  public List<Occurrence> expand(Rule rule, DateRange range, String provenance) {
    try {
      return switch (rule) {
        case Rule.ExplicitDates r -> expandExplicitDates(r, range, provenance);
        case Rule.FixedMonthDay r -> expandFixedMonthDay(r, range, provenance);
        case Rule.NthWeekdayOfMonth r -> expandNthWeekday(r, range, provenance);
        case Rule.RelativeToReference r -> expandRelativeToReference(r, range, provenance);
      };
    } catch (DateTimeException | ArithmeticException e) {
      throw new IllegalArgumentException(
          provenance + ": required rule date outside representable range for " + range, e);
    }
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

    // Get the year range in the target chronology
    int[] years;
    try {
      years =
          "ISO".equalsIgnoreCase(chronology) ? range.isoYearRange() : range.yearRange(chronology);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          provenance
              + ": required dependency range "
              + range
              + " outside supported chronology "
              + chronology,
          e);
    }
    for (int year = years[0]; year <= years[1]; year++) {
      // Invalid month days (e.g. February 29) are absent; conversion failures are errors.
      if (ChronologyRegistry.getInstance()
          .getAlgorithm(chronology)
          .isValidDate(year, rule.month(), rule.day())) {
        // Convert from target chronology to ISO date
        LocalDate isoDate =
            ChronologyTranslator.toIsoDate(year, rule.month(), rule.day(), chronology);
        if (range.contains(isoDate)) {
          occurrences.add(new Occurrence(rule.key(), isoDate, rule.name(), provenance));
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
    long minOffset;
    long maxOffset;
    if (rule.usesWeekdayOffset()) {
      Rule.WeekdayOffset offset = rule.offsetWeekday();
      if (offset.nth() < 1) {
        throw new IllegalArgumentException(
            "WeekdayOffset nth must be at least 1, got: " + offset.nth());
      }
      minOffset = 7L * (offset.nth() - 1) + 1;
      maxOffset = 7L * offset.nth();
      if (offset.direction() == Rule.OffsetDirection.BEFORE) {
        long oldMin = minOffset;
        minOffset = -maxOffset;
        maxOffset = -oldMin;
      }
    } else if (rule.offsetDays() != null) {
      minOffset = maxOffset = rule.offsetDays();
    } else {
      throw new IllegalArgumentException(
          "RelativeToReference must have either offsetDays or offsetWeekday");
    }
    DateRange referenceRange =
        new DateRange(
            DateArithmetic.plusDays(range.start(), -maxOffset, provenance),
            DateArithmetic.plusDays(range.end(), -minOffset, provenance));
    List<LocalDate> refDates;

    if (rule.usesNamedReference()) {
      // Named reference (e.g., "easter")
      if (referenceResolver == null) {
        throw new IllegalStateException("ReferenceResolver not set");
      }
      if (!referenceResolver.hasReference(rule.reference())) {
        throw new IllegalArgumentException("Unknown reference: " + rule.reference());
      }
      refDates = referenceResolver.getDates(rule.reference(), referenceRange);
    } else if (rule.usesFixedReference()) {
      // Fixed month/day reference - generate for each year in range
      refDates = new ArrayList<>();
      int[] years = referenceRange.isoYearRange();
      for (int year = years[0]; year <= years[1]; year++) {
        if (YearMonth.of(year, rule.referenceMonth()).isValidDay(rule.referenceDay())) {
          LocalDate refDate = LocalDate.of(year, rule.referenceMonth(), rule.referenceDay());
          if (referenceRange.contains(refDate)) refDates.add(refDate);
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
        date = DateArithmetic.plusDays(refDate, rule.offsetDays(), provenance);
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

    int sign = direction == Rule.OffsetDirection.AFTER ? 1 : -1;
    int distance =
        Math.floorMod(sign * (targetWeekday.getValue() - refDate.getDayOfWeek().getValue()), 7);
    if (distance == 0) distance = 7;
    return DateArithmetic.plusDays(
        refDate, sign * (distance + 7L * (nth - 1)), "Weekday reference offset");
  }
}
