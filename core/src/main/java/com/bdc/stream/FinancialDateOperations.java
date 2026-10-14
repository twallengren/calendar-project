package com.bdc.stream;

import com.bdc.finance.DayCount;
import com.bdc.finance.Tenor;
import com.bdc.model.EventStatus;
import com.bdc.trust.DateQuestion;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashSet;

/** Shared implementation behind the additive {@link DateStream} financial operations. */
final class FinancialDateOperations {
  private FinancialDateOperations() {}

  static DateOperationResult adjust(
      DateStream stream, LocalDate date, BusinessDayConvention convention) {
    if (convention == null) throw new IllegalArgumentException("convention must not be null");
    Tracker tracker = new Tracker(stream);
    LocalDate result = adjust(stream, date, convention, tracker);
    return tracker.result(date, result, DateOperation.ADJUST, convention, null, null, false);
  }

  static DateOperationResult offset(DateStream stream, LocalDate date, int offset) {
    Tracker tracker = new Tracker(stream);
    if (offset == 0) {
      tracker.observeIdentity(date);
      return tracker.result(date, date, DateOperation.BUSINESS_DAY_OFFSET, null, 0, null, false);
    }
    long remaining = Math.abs((long) offset);
    int direction = offset > 0 ? 1 : -1;
    LocalDate current = date;
    long guard =
        Math.addExact(
            Math.multiplyExact((long) DateStream.MAX_SEARCH_DAYS, remaining),
            DateStream.MAX_SEARCH_DAYS);
    while (remaining > 0) {
      if (guard-- <= 0) {
        throw new IllegalStateException(
            "Could not find "
                + Math.abs((long) offset)
                + " business days "
                + (direction > 0 ? "after " : "before ")
                + date);
      }
      current = current.plusDays(direction);
      if (tracker.isBusinessDay(current)) remaining--;
    }
    return tracker.result(
        date, current, DateOperation.BUSINESS_DAY_OFFSET, null, offset, null, false);
  }

  static DateOperationResult advanceMonths(
      DateStream stream,
      LocalDate date,
      int months,
      BusinessDayConvention convention,
      boolean preserveEndOfMonth) {
    if (convention == null) throw new IllegalArgumentException("convention must not be null");
    if (months == 0 && convention == BusinessDayConvention.UNADJUSTED && !preserveEndOfMonth) {
      Tracker identity = new Tracker(stream);
      identity.observeIdentity(date);
      return identity.result(date, date, DateOperation.ADVANCE_MONTHS, convention, null, 0, false);
    }
    Tracker tracker = new Tracker(stream);
    tracker.requireResolved(date);
    LocalDate nominal = date.plusMonths(months);
    LocalDate result;
    if (preserveEndOfMonth && date.equals(lastBusinessDay(stream, YearMonth.from(date), tracker))) {
      result = lastBusinessDay(stream, YearMonth.from(nominal), tracker);
    } else if (convention == BusinessDayConvention.UNADJUSTED) {
      tracker.requireResolved(nominal);
      result = nominal;
    } else {
      result = adjust(stream, nominal, convention, tracker);
    }
    return tracker.result(
        date, result, DateOperation.ADVANCE_MONTHS, convention, null, months, preserveEndOfMonth);
  }

  /**
   * Adds a tenor. Day and week tenors shift by calendar days and then adjust; month and year tenors
   * advance months (with optional business-month-end preservation) and then adjust; business-day
   * tenors walk business days and ignore the convention. A zero tenor is an identity operation.
   */
  static DateOperationResult addTenor(
      DateStream stream,
      LocalDate date,
      Tenor tenor,
      BusinessDayConvention convention,
      boolean preserveEndOfMonth) {
    if (tenor == null) throw new IllegalArgumentException("tenor must not be null");
    if (convention == null) throw new IllegalArgumentException("convention must not be null");
    DateOperationResult base =
        switch (tenor.unit()) {
          case BUSINESS_DAYS -> offset(stream, date, tenor.amount());
          case MONTHS, YEARS ->
              advanceMonths(stream, date, tenor.months(), convention, preserveEndOfMonth);
          case DAYS, WEEKS -> {
            Tracker tracker = new Tracker(stream);
            LocalDate nominal = date.plusDays(tenor.days());
            LocalDate result = adjust(stream, nominal, convention, tracker);
            yield tracker.result(
                date, result, DateOperation.ADD_TENOR, convention, null, null, false);
          }
        };
    return new DateOperationResult(
        base.originalDate(),
        base.resultDate(),
        DateOperation.ADD_TENOR,
        tenor.unit() == Tenor.Unit.BUSINESS_DAYS ? null : convention,
        base.businessDayOffset(),
        base.monthOffset(),
        base.preserveEndOfMonth(),
        base.effectiveConfidence(),
        base.examinedDates(),
        tenor.toString());
  }

  /**
   * A year fraction. Calendar-free conventions are pure arithmetic and examine nothing; BUS/252
   * counts the business days in {@code [start, end)} and carries their confidence.
   */
  static YearFractionResult yearFraction(
      DateStream stream, LocalDate start, LocalDate end, DayCount dayCount) {
    if (dayCount == null) throw new IllegalArgumentException("dayCount must not be null");
    if (dayCount != DayCount.BUS_252) {
      return new YearFractionResult(
          start,
          end,
          dayCount,
          dayCount.days(start, end),
          dayCount.yearFraction(start, end),
          EventStatus.CONFIRMED,
          java.util.List.of());
    }
    if (end.isBefore(start)) {
      YearFractionResult forward = yearFraction(stream, end, start, dayCount);
      return new YearFractionResult(
          start,
          end,
          dayCount,
          -forward.days(),
          -forward.yearFraction(),
          forward.effectiveConfidence(),
          forward.examinedDates());
    }
    Tracker tracker = new Tracker(stream);
    long count = 0;
    for (LocalDate day = start; day.isBefore(end); day = day.plusDays(1)) {
      if (tracker.isBusinessDay(day)) count++;
    }
    return new YearFractionResult(
        start, end, dayCount, count, count / 252.0, tracker.confidence(), tracker.examined());
  }

  static DateOperationResult lastBusinessDayOfMonth(DateStream stream, LocalDate date) {
    Tracker tracker = new Tracker(stream);
    tracker.requireResolved(date);
    LocalDate result = lastBusinessDay(stream, YearMonth.from(date), tracker);
    return tracker.result(
        date, result, DateOperation.LAST_BUSINESS_DAY_OF_MONTH, null, null, null, false);
  }

  static LocalDate adjust(
      DateStream stream, LocalDate date, BusinessDayConvention convention, Tracker tracker) {
    if (convention == BusinessDayConvention.UNADJUSTED) {
      tracker.observeIdentity(date);
      return date;
    }
    int firstDirection =
        convention == BusinessDayConvention.FOLLOWING
                || convention == BusinessDayConvention.MODIFIED_FOLLOWING
            ? 1
            : -1;
    LocalDate first = search(stream, date, firstDirection, tracker);
    boolean modified =
        convention == BusinessDayConvention.MODIFIED_FOLLOWING
            || convention == BusinessDayConvention.MODIFIED_PRECEDING;
    return modified && !YearMonth.from(first).equals(YearMonth.from(date))
        ? search(stream, date, -firstDirection, tracker)
        : first;
  }

  private static LocalDate lastBusinessDay(DateStream stream, YearMonth month, Tracker tracker) {
    LocalDate candidate = month.atEndOfMonth();
    while (YearMonth.from(candidate).equals(month)) {
      if (tracker.isBusinessDay(candidate)) return candidate;
      candidate = candidate.minusDays(1);
    }
    throw new IllegalStateException("No business day in " + month);
  }

  private static LocalDate search(
      DateStream stream, LocalDate date, int direction, Tracker tracker) {
    LocalDate candidate = date;
    for (int i = 0; i < DateStream.MAX_SEARCH_DAYS; i++) {
      if (tracker.isBusinessDay(candidate)) return candidate;
      candidate = candidate.plusDays(direction);
    }
    throw new IllegalStateException(
        "No business day within " + DateStream.MAX_SEARCH_DAYS + " days of " + date);
  }

  static final class Tracker {
    private final DateStream stream;
    private final LinkedHashSet<LocalDate> examined = new LinkedHashSet<>();
    private EventStatus confidence = EventStatus.CONFIRMED;

    Tracker(DateStream stream) {
      this.stream = stream;
    }

    EventStatus confidence() {
      return confidence;
    }

    java.util.List<LocalDate> examined() {
      return examined.stream().toList();
    }

    private void observeIdentity(LocalDate date) {
      examined.add(date);
      include(stream.assessment(date).businessDayConfidence());
    }

    private void requireResolved(LocalDate date) {
      examined.add(date);
      var assessment = stream.assessment(date);
      include(assessment.businessDayConfidence());
      stream.requireResolved(date, DateQuestion.BUSINESS_DAY);
    }

    private boolean isBusinessDay(LocalDate date) {
      requireResolved(date);
      return stream.isBusinessDay(date);
    }

    private void include(EventStatus value) {
      if (value == EventStatus.UNKNOWN) confidence = EventStatus.UNKNOWN;
      else if (value == EventStatus.PROJECTED && confidence == EventStatus.CONFIRMED) {
        confidence = EventStatus.PROJECTED;
      }
    }

    private DateOperationResult result(
        LocalDate original,
        LocalDate result,
        DateOperation operation,
        BusinessDayConvention convention,
        Integer businessDays,
        Integer months,
        boolean preserveEndOfMonth) {
      return new DateOperationResult(
          original,
          result,
          operation,
          convention,
          businessDays,
          months,
          preserveEndOfMonth,
          confidence,
          examined.stream().toList());
    }
  }
}
