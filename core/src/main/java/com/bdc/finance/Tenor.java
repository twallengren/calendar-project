package com.bdc.finance;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A market tenor such as {@code 3M}, {@code 2W}, {@code 1Y}, {@code 10D} or {@code T+2}.
 *
 * <p>Calendar units ({@code D}, {@code W}, {@code M}, {@code Y}) are civil periods that a business
 * calendar then adjusts; {@code B} (also spelled {@code T+n} / {@code T-n}) counts business days
 * directly. Amounts may be negative.
 */
public record Tenor(int amount, Unit unit) {

  /** The unit of a tenor. */
  public enum Unit {
    DAYS("D"),
    WEEKS("W"),
    MONTHS("M"),
    YEARS("Y"),
    BUSINESS_DAYS("B");

    private final String code;

    Unit(String code) {
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  private static final Pattern FORM = Pattern.compile("([+-]?\\d{1,5})([DWMYB])");
  private static final Pattern SETTLEMENT = Pattern.compile("T([+-]\\d{1,5})");

  public Tenor {
    Objects.requireNonNull(unit, "unit must not be null");
  }

  public static Tenor of(int amount, Unit unit) {
    return new Tenor(amount, unit);
  }

  /**
   * Parses {@code 3M}, {@code -2W}, {@code 1Y}, {@code 0D}, {@code 2B} or {@code T+2} (case
   * insensitive, surrounding whitespace ignored).
   *
   * @throws IllegalArgumentException for any other spelling
   */
  public static Tenor parse(String text) {
    String value =
        Objects.requireNonNull(text, "tenor must not be null").strip().toUpperCase(Locale.ROOT);
    Matcher settlement = SETTLEMENT.matcher(value);
    if (settlement.matches()) {
      return new Tenor(Integer.parseInt(settlement.group(1)), Unit.BUSINESS_DAYS);
    }
    Matcher form = FORM.matcher(value);
    if (!form.matches()) {
      throw new IllegalArgumentException(
          "Not a tenor: '" + text + "' (expected e.g. 3M, 2W, 1Y, 10D, 2B or T+2)");
    }
    int amount = Integer.parseInt(form.group(1));
    Unit unit =
        switch (form.group(2)) {
          case "D" -> Unit.DAYS;
          case "W" -> Unit.WEEKS;
          case "M" -> Unit.MONTHS;
          case "Y" -> Unit.YEARS;
          default -> Unit.BUSINESS_DAYS;
        };
    return new Tenor(amount, unit);
  }

  /** Whether this tenor is expressed in whole months ({@code M} or {@code Y}). */
  public boolean isMonthBased() {
    return unit == Unit.MONTHS || unit == Unit.YEARS;
  }

  /** The tenor in months; only meaningful when {@link #isMonthBased()}. */
  public int months() {
    return switch (unit) {
      case MONTHS -> amount;
      case YEARS -> Math.multiplyExact(amount, 12);
      default -> throw new IllegalStateException(this + " is not month-based");
    };
  }

  /** The tenor in calendar days; only meaningful for {@code D} and {@code W}. */
  public int days() {
    return switch (unit) {
      case DAYS -> amount;
      case WEEKS -> Math.multiplyExact(amount, 7);
      default -> throw new IllegalStateException(this + " is not day-based");
    };
  }

  /** The canonical spelling: {@code 3M}, {@code -2W}, {@code 2B}. */
  @Override
  public String toString() {
    return amount + unit.code();
  }
}
