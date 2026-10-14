package com.bdc.finance;

import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Day-count conventions, following the 2006 ISDA Definitions, Section 4.16, for computing the
 * fraction of a year between two dates.
 *
 * <p>Conventions that need no calendar are evaluated here. {@link #BUS_252} needs a business
 * calendar and is evaluated by {@code DateStream.yearFractionDetailed}; {@link #ACT_ACT_ICMA} needs
 * the regular period it belongs to and is evaluated by {@link #yearFraction(LocalDate, LocalDate,
 * LocalDate, LocalDate, Frequency)}. Every method returns a negative fraction when {@code end}
 * precedes {@code start}, symmetric with the forward value.
 */
public enum DayCount {
  /** Actual days over 360 (4.16(e)). Money markets: USD, EUR, CHF deposits. */
  ACT_360,
  /** Actual days over a fixed 365 (4.16(d)). GBP, CAD, AUD money markets. */
  ACT_365F,
  /** Actual/Actual ISDA (4.16(b)): days in leap years over 366, other days over 365. */
  ACT_ACT_ISDA,
  /**
   * Actual/Actual ICMA (4.16(c)): actual days over (periods per year times the actual days of the
   * regular period the dates belong to). Bonds; needs the reference period.
   */
  ACT_ACT_ICMA,
  /** 30/360 Bond Basis (4.16(f)), also called 30U/360 without the February rules. USD bonds. */
  THIRTY_360_BOND,
  /** 30E/360 Eurobond Basis (4.16(g)): both 31sts become 30. */
  THIRTY_E_360,
  /**
   * 30E/360 ISDA (4.16(h)): the last day of any month becomes 30, except the end date when it is
   * the termination date and falls in February. Evaluate with {@link #yearFraction(LocalDate,
   * LocalDate, boolean)}; the two-argument form assumes the end is not the termination date.
   */
  THIRTY_E_360_ISDA,
  /**
   * Business days over 252 (Brazilian BUS/252): the number of business days in {@code [start, end)}
   * over 252. Needs a calendar: see {@code DateStream.yearFractionDetailed}.
   */
  BUS_252;

  /** Whether this convention can be evaluated from the two dates alone. */
  public boolean isCalendarFree() {
    return this != BUS_252 && this != ACT_ACT_ICMA;
  }

  /**
   * The day count the convention attributes to {@code [start, end]}: actual days for the ACT
   * conventions and the 30/360 count for the 30/360 family. Negative when {@code end} precedes
   * {@code start}.
   *
   * @throws IllegalArgumentException for {@link #BUS_252}, which needs a calendar
   */
  public long days(LocalDate start, LocalDate end) {
    return days(start, end, false);
  }

  private long days(LocalDate start, LocalDate end, boolean endIsTermination) {
    if (end.isBefore(start)) {
      return -days(end, start, endIsTermination);
    }
    return switch (this) {
      case ACT_360, ACT_365F, ACT_ACT_ISDA, ACT_ACT_ICMA -> ChronoUnit.DAYS.between(start, end);
      case THIRTY_360_BOND -> {
        int d1 = Math.min(start.getDayOfMonth(), 30);
        int d2 = end.getDayOfMonth() == 31 && d1 == 30 ? 30 : end.getDayOfMonth();
        yield thirty360(start, end, d1, d2);
      }
      case THIRTY_E_360 ->
          thirty360(
              start, end, Math.min(start.getDayOfMonth(), 30), Math.min(end.getDayOfMonth(), 30));
      case THIRTY_E_360_ISDA -> {
        int d1 = isLastDayOfMonth(start) ? 30 : start.getDayOfMonth();
        boolean endFebruary = end.getMonthValue() == 2;
        int d2 =
            end.getDayOfMonth() == 31
                    || (isLastDayOfMonth(end) && !(endIsTermination && endFebruary))
                ? 30
                : end.getDayOfMonth();
        yield thirty360(start, end, d1, d2);
      }
      case BUS_252 ->
          throw new IllegalArgumentException(
              "BUS/252 needs a business calendar: use DateStream.yearFractionDetailed");
    };
  }

  /**
   * The year fraction for {@code [start, end]} under a calendar-free convention.
   *
   * @throws IllegalArgumentException for {@link #BUS_252} and {@link #ACT_ACT_ICMA}, which need
   *     more context
   */
  public double yearFraction(LocalDate start, LocalDate end) {
    return switch (this) {
      case ACT_360, THIRTY_360_BOND, THIRTY_E_360 -> days(start, end) / 360.0;
      case ACT_365F -> days(start, end) / 365.0;
      case THIRTY_E_360_ISDA -> yearFraction(start, end, false);
      case ACT_ACT_ISDA -> actActIsda(start, end);
      case ACT_ACT_ICMA ->
          throw new IllegalArgumentException(
              "ACT/ACT ICMA needs the reference period: use yearFraction(start, end, periodStart, periodEnd, frequency)");
      case BUS_252 ->
          throw new IllegalArgumentException(
              "BUS/252 needs a business calendar: use DateStream.yearFractionDetailed");
    };
  }

  /**
   * 30E/360 ISDA with an explicit statement of whether {@code end} is the termination date of the
   * instrument (which keeps a February end date at its actual day). Other conventions ignore the
   * flag.
   */
  public double yearFraction(LocalDate start, LocalDate end, boolean endIsTermination) {
    if (this != THIRTY_E_360_ISDA) {
      return yearFraction(start, end);
    }
    return days(start, end, endIsTermination) / 360.0;
  }

  /**
   * ACT/ACT ICMA for a regular period: actual days in {@code [start, end]} over {@code
   * frequency.periodsPerYear()} times the actual days of the regular period {@code [periodStart,
   * periodEnd]} the dates lie in. For a stub, pass the notional regular period as the reference.
   * Other conventions ignore the reference period.
   */
  public double yearFraction(
      LocalDate start,
      LocalDate end,
      LocalDate periodStart,
      LocalDate periodEnd,
      Frequency frequency) {
    if (this != ACT_ACT_ICMA) {
      return yearFraction(start, end);
    }
    long periodDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
    if (periodDays <= 0) {
      throw new IllegalArgumentException("ACT/ACT ICMA reference period must be non-empty");
    }
    return ChronoUnit.DAYS.between(start, end) / ((double) frequency.periodsPerYear() * periodDays);
  }

  private static double actActIsda(LocalDate start, LocalDate end) {
    if (end.isBefore(start)) {
      return -actActIsda(end, start);
    }
    if (start.getYear() == end.getYear()) {
      return ChronoUnit.DAYS.between(start, end) / (double) start.lengthOfYear();
    }
    double fraction = 0;
    LocalDate cursor = start;
    while (cursor.getYear() < end.getYear()) {
      LocalDate nextYear = LocalDate.of(cursor.getYear() + 1, 1, 1);
      fraction +=
          ChronoUnit.DAYS.between(cursor, nextYear) / (double) Year.of(cursor.getYear()).length();
      cursor = nextYear;
    }
    fraction += ChronoUnit.DAYS.between(cursor, end) / (double) end.lengthOfYear();
    return fraction;
  }

  private static long thirty360(LocalDate start, LocalDate end, int d1, int d2) {
    return 360L * (end.getYear() - start.getYear())
        + 30L * (end.getMonthValue() - start.getMonthValue())
        + (d2 - d1);
  }

  private static boolean isLastDayOfMonth(LocalDate date) {
    return date.equals(date.with(TemporalAdjusters.lastDayOfMonth()));
  }
}
