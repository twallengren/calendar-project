package com.bdc.chronology.ontology.algorithms;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.ChronologySpec;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Algorithm implementation for simple formula-based calendars.
 *
 * <p>This algorithm handles calendars that can be defined by:
 *
 * <ul>
 *   <li>An epoch (JDN of year 1, month 1, day 1)
 *   <li>A fixed month structure
 *   <li>A leap year formula
 * </ul>
 *
 * <p>Suitable for calendars like Julian, Persian Solar Hijri, and similar algorithmic calendars.
 */
public class FormulaAlgorithm implements ChronologyAlgorithm {

  private final String chronologyId;
  private final long epochJdn;
  private final List<ChronologySpec.Month> months;
  private final IntPredicate leapYearPredicate;

  /**
   * Creates a FormulaAlgorithm from a ChronologySpec.
   *
   * @param spec the chronology specification
   */
  public FormulaAlgorithm(ChronologySpec spec) {
    this.chronologyId = spec.id();
    this.epochJdn = spec.structure().epochJdn();
    this.months = spec.structure().months();
    this.leapYearPredicate = parseLeapYearFormula(spec.algorithms().leapYear());
  }

  /**
   * Creates a FormulaAlgorithm with explicit parameters.
   *
   * @param chronologyId the chronology identifier
   * @param epochJdn the JDN of the epoch
   * @param months the month definitions
   * @param leapYearFormula the leap year formula
   */
  public FormulaAlgorithm(
      String chronologyId,
      long epochJdn,
      List<ChronologySpec.Month> months,
      String leapYearFormula) {
    this.chronologyId = chronologyId;
    this.epochJdn = epochJdn;
    this.months = months;
    this.leapYearPredicate = parseLeapYearFormula(leapYearFormula);
  }

  @Override
  public String getChronologyId() {
    return chronologyId;
  }

  @Override
  public long toJdn(int year, int month, int day) {
    // Calculate days from epoch to start of year
    long days = daysBeforeYear(year);

    // Add days for complete months
    for (int m = 1; m < month; m++) {
      days += getDaysInMonth(year, m);
    }

    // Add days within the month
    days += day - 1;

    return epochJdn + days;
  }

  @Override
  public ChronologyDate fromJdn(long jdn) {
    long daysSinceEpoch = jdn - epochJdn;

    // Estimate year (may need adjustment)
    int year = estimateYear(daysSinceEpoch);

    // Adjust year if necessary
    while (daysBeforeYear(year + 1) <= daysSinceEpoch) {
      year++;
    }
    while (daysBeforeYear(year) > daysSinceEpoch) {
      year--;
    }

    // Calculate remaining days after start of year
    long remainingDays = daysSinceEpoch - daysBeforeYear(year);

    // Find month
    int month = 1;
    while (month <= months.size()) {
      int daysInMonth = getDaysInMonth(year, month);
      if (remainingDays < daysInMonth) {
        break;
      }
      remainingDays -= daysInMonth;
      month++;
    }

    int day = (int) remainingDays + 1;

    return new ChronologyDate(chronologyId, year, month, day);
  }

  @Override
  public boolean isValidDate(int year, int month, int day) {
    if (month < 1 || month > months.size()) {
      return false;
    }
    if (day < 1) {
      return false;
    }
    return day <= getDaysInMonth(year, month);
  }

  @Override
  public int getDaysInMonth(int year, int month) {
    if (month < 1 || month > months.size()) {
      throw new IllegalArgumentException("Invalid month: " + month);
    }
    return months.get(month - 1).getDays(isLeapYear(year));
  }

  @Override
  public boolean isLeapYear(int year) {
    return leapYearPredicate.test(year);
  }

  /**
   * Calculates the number of days from epoch to the start of the given year.
   *
   * @param year the year
   * @return days before the start of the year
   */
  private long daysBeforeYear(int year) {
    if (year <= 1) {
      return 0;
    }

    long days = 0;
    for (int y = 1; y < year; y++) {
      days += getDaysInYear(y);
    }
    return days;
  }

  /**
   * Returns the number of days in a year.
   *
   * @param year the year
   * @return days in the year
   */
  private int getDaysInYear(int year) {
    int days = 0;
    for (int m = 1; m <= months.size(); m++) {
      days += getDaysInMonth(year, m);
    }
    return days;
  }

  /**
   * Estimates the year for a given number of days since epoch.
   *
   * @param daysSinceEpoch days since the epoch
   * @return estimated year
   */
  private int estimateYear(long daysSinceEpoch) {
    // Use average year length for estimation
    double avgYearLength = 365.25; // Approximate
    return Math.max(1, (int) (daysSinceEpoch / avgYearLength) + 1);
  }

  /**
   * Parses a leap year formula string into a predicate.
   *
   * <p>Supports simple expressions like:
   *
   * <ul>
   *   <li>"year % 4 == 0" (Julian)
   *   <li>"(year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)" (Gregorian)
   *   <li>"false" (no leap years)
   * </ul>
   *
   * @param formula the formula string
   * @return the predicate
   */
  private IntPredicate parseLeapYearFormula(String formula) {
    if (formula == null || formula.isBlank()) {
      return year -> false;
    }

    String trimmed = formula.trim();

    // Handle literal boolean
    if ("true".equalsIgnoreCase(trimmed)) {
      return year -> true;
    }
    if ("false".equalsIgnoreCase(trimmed)) {
      return year -> false;
    }

    // Parse the formula
    return new FormulaParser(trimmed)::evaluate;
  }

  /**
   * Simple expression parser for leap year formulas.
   *
   * <p>Supports: year, integers, %, ==, !=, &&, ||, (), and basic arithmetic.
   */
  private static class FormulaParser {
    private final String formula;

    FormulaParser(String formula) {
      this.formula = formula;
    }

    boolean evaluate(int year) {
      return evaluateExpression(formula.replace("year", String.valueOf(year)));
    }

    private boolean evaluateExpression(String expr) {
      expr = expr.trim();

      // Handle parentheses first
      while (expr.contains("(")) {
        int start = expr.lastIndexOf('(');
        int end = expr.indexOf(')', start);
        if (end == -1) {
          throw new IllegalArgumentException("Unmatched parenthesis in: " + formula);
        }
        String inner = expr.substring(start + 1, end);
        String result = String.valueOf(evaluateExpression(inner));
        expr = expr.substring(0, start) + result + expr.substring(end + 1);
      }

      // Handle || (lowest precedence)
      if (expr.contains("||")) {
        String[] parts = expr.split("\\|\\|", 2);
        return evaluateExpression(parts[0]) || evaluateExpression(parts[1]);
      }

      // Handle &&
      if (expr.contains("&&")) {
        String[] parts = expr.split("&&", 2);
        return evaluateExpression(parts[0]) && evaluateExpression(parts[1]);
      }

      // Handle comparisons
      if (expr.contains("==")) {
        String[] parts = expr.split("==", 2);
        return evaluateArithmetic(parts[0]) == evaluateArithmetic(parts[1]);
      }
      if (expr.contains("!=")) {
        String[] parts = expr.split("!=", 2);
        return evaluateArithmetic(parts[0]) != evaluateArithmetic(parts[1]);
      }

      // Handle boolean literal
      expr = expr.trim();
      if ("true".equalsIgnoreCase(expr)) {
        return true;
      }
      if ("false".equalsIgnoreCase(expr)) {
        return false;
      }

      throw new IllegalArgumentException("Cannot parse expression: " + expr);
    }

    private long evaluateArithmetic(String expr) {
      expr = expr.trim();

      // Handle modulo
      if (expr.contains("%")) {
        String[] parts = expr.split("%", 2);
        return evaluateArithmetic(parts[0]) % evaluateArithmetic(parts[1]);
      }

      // Parse as integer
      return Long.parseLong(expr.trim());
    }
  }
}
