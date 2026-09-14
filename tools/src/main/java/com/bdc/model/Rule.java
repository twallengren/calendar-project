package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Rule.NativeFixedMonthDay.class, name = "native_fixed_month_day"),
  @JsonSubTypes.Type(value = Rule.NativeExplicitDates.class, name = "native_explicit_dates"),
  @JsonSubTypes.Type(value = Rule.NativeNthWeekday.class, name = "native_nth_weekday"),
  @JsonSubTypes.Type(
      value = Rule.NativeRelativeToReference.class,
      name = "native_relative_to_reference"),
  @JsonSubTypes.Type(value = Rule.ExplicitDates.class, name = "explicit_dates"),
  @JsonSubTypes.Type(value = Rule.FixedMonthDay.class, name = "fixed_month_day"),
  @JsonSubTypes.Type(value = Rule.NthWeekdayOfMonth.class, name = "nth_weekday_of_month"),
  @JsonSubTypes.Type(value = Rule.RelativeToReference.class, name = "relative_to_reference")
})
public sealed interface Rule
    permits Rule.ExplicitDates,
        Rule.FixedMonthDay,
        Rule.NthWeekdayOfMonth,
        Rule.RelativeToReference,
        Rule.NativeRecurring,
        Rule.NativeExplicitDates {

  /** Native-year filter intersects the enclosing source's nominal ISO active_years. */
  sealed interface NativeRecurring extends Rule
      permits NativeFixedMonthDay, NativeNthWeekday, NativeRelativeToReference {
    String chronology();

    List<String> monthCodes();

    List<EventSource.YearRange> nativeYears();

    default boolean includesYear(int year) {
      return nativeYears() == null
          || nativeYears().isEmpty()
          || nativeYears().stream().anyMatch(r -> r.contains(year));
    }
  }

  record NativeFixedMonthDay(
      String key,
      String name,
      String chronology,
      @JsonProperty("month_codes") List<String> monthCodes,
      int day,
      @JsonProperty("native_years") List<EventSource.YearRange> nativeYears,
      @JsonProperty("duration_days") Integer durationDays)
      implements NativeRecurring {
    public NativeFixedMonthDay {
      monthCodes = validateNative(chronology, monthCodes, nativeYears, durationDays);
      if (nativeYears != null) nativeYears = List.copyOf(nativeYears);
      if (day < 1 || day > 31) throw new IllegalArgumentException("day must be 1..31");
    }

    public int spanDays() {
      return durationDays == null ? 1 : durationDays;
    }

    public NativeFixedMonthDay withIdentity(String key, String name) {
      return new NativeFixedMonthDay(
          key, name, chronology, monthCodes, day, nativeYears, durationDays);
    }
  }

  record NativeNthWeekday(
      String key,
      String name,
      String chronology,
      @JsonProperty("month_codes") List<String> monthCodes,
      DayOfWeek weekday,
      int nth,
      @JsonProperty("native_years") List<EventSource.YearRange> nativeYears,
      @JsonProperty("duration_days") Integer durationDays)
      implements NativeRecurring {
    public NativeNthWeekday {
      monthCodes = validateNative(chronology, monthCodes, nativeYears, durationDays);
      if (nativeYears != null) nativeYears = List.copyOf(nativeYears);
      if (weekday == null || nth == 0 || nth < -1 || nth > 5)
        throw new IllegalArgumentException("weekday and nth 1..5 or -1 required");
    }

    public int spanDays() {
      return durationDays == null ? 1 : durationDays;
    }

    public NativeNthWeekday withIdentity(String key, String name) {
      return new NativeNthWeekday(
          key, name, chronology, monthCodes, weekday, nth, nativeYears, durationDays);
    }
  }

  record NativeRelativeToReference(
      String key,
      String name,
      String chronology,
      @JsonProperty("month_codes") List<String> monthCodes,
      int day,
      @JsonProperty("offset_days") int offsetDays,
      @JsonProperty("native_years") List<EventSource.YearRange> nativeYears,
      @JsonProperty("duration_days") Integer durationDays)
      implements NativeRecurring {
    public NativeRelativeToReference {
      monthCodes = validateNative(chronology, monthCodes, nativeYears, durationDays);
      if (nativeYears != null) nativeYears = List.copyOf(nativeYears);
      if (day < 1 || day > 31) throw new IllegalArgumentException("day must be 1..31");
    }

    public int spanDays() {
      return durationDays == null ? 1 : durationDays;
    }

    public NativeRelativeToReference withIdentity(String key, String name) {
      return new NativeRelativeToReference(
          key, name, chronology, monthCodes, day, offsetDays, nativeYears, durationDays);
    }
  }

  record NativeExplicitDates(String key, String name, List<com.bdc.chronology.NativeDate> dates)
      implements Rule {
    public NativeExplicitDates {
      dates = List.copyOf(dates);
      if (dates.isEmpty())
        throw new IllegalArgumentException("native_explicit_dates must not be empty");
    }

    public NativeExplicitDates withIdentity(String key, String name) {
      return new NativeExplicitDates(key, name, dates);
    }
  }

  private static List<String> validateNative(
      String chronology, List<String> codes, List<EventSource.YearRange> years, Integer duration) {
    java.util.Objects.requireNonNull(chronology, "chronology required");
    if (codes == null || codes.isEmpty())
      throw new IllegalArgumentException(
          "month_codes must explicitly select at least one exact month identity");
    if (new java.util.HashSet<>(codes).size() != codes.size())
      throw new IllegalArgumentException("duplicate month_codes");
    if (duration != null && duration < 1)
      throw new IllegalArgumentException("duration_days must be >= 1");
    if (years != null
        && years.stream()
            .anyMatch(r -> r.start() != null && r.end() != null && r.start() > r.end()))
      throw new IllegalArgumentException("native_years start exceeds end");
    return List.copyOf(codes);
  }

  /** The event key this rule produces. May be null in YAML; inherited from the event source. */
  String key();

  /** The display name this rule produces. May be null in YAML; inherited from the event source. */
  String name();

  /** Returns a copy of this rule with the given key and name. */
  Rule withIdentity(String key, String name);

  /**
   * Number of consecutive days each occurrence spans (1 = a single day). Rules that do not support
   * spans return 1.
   */
  default int spanDays() {
    return 1;
  }

  /**
   * A date with an optional comment/annotation. Supports both plain dates ("2024-01-01") and
   * annotated dates ({date: "2024-01-01", comment: "New Year"}) in YAML.
   */
  @JsonDeserialize(using = AnnotatedDateDeserializer.class)
  record AnnotatedDate(LocalDate date, String comment) {
    public AnnotatedDate(LocalDate date) {
      this(date, null);
    }

    /** Returns the effective name: base name with comment appended if present. */
    public String effectiveName(String baseName) {
      if (comment == null || comment.isBlank()) {
        return baseName;
      }
      return baseName + " (" + comment + ")";
    }
  }

  /** Direction for weekday offset calculation. */
  enum OffsetDirection {
    BEFORE,
    AFTER
  }

  /**
   * Defines a weekday-based offset from a reference date. For example: "1st Tuesday after November
   * 1st" would be weekday=TUESDAY, nth=1, direction=AFTER.
   */
  record WeekdayOffset(DayOfWeek weekday, int nth, OffsetDirection direction) {}

  record ExplicitDates(String key, String name, List<AnnotatedDate> dates) implements Rule {
    public ExplicitDates {
      if (dates == null) dates = List.of();
    }

    @Override
    public ExplicitDates withIdentity(String key, String name) {
      return new ExplicitDates(key, name, dates);
    }
  }

  /**
   * A fixed month/day in a chronology, optionally spanning several days.
   *
   * <p>A span is expressed either as {@code end_month}/{@code end_day} (inclusive, in the same
   * chronology; if the end falls before the start it is taken in the following chronology year) or
   * as {@code duration_days}. The two forms are mutually exclusive.
   */
  record FixedMonthDay(
      String key,
      String name,
      int month,
      int day,
      String chronology,
      @JsonProperty("end_month") Integer endMonth,
      @JsonProperty("end_day") Integer endDay,
      @JsonProperty("duration_days") Integer durationDays)
      implements Rule {
    public FixedMonthDay {
      if (chronology == null) chronology = "ISO";
      if ((endMonth == null) != (endDay == null)) {
        throw new IllegalArgumentException("end_month and end_day must be given together");
      }
      if (endMonth != null && durationDays != null) {
        throw new IllegalArgumentException("end_month/end_day and duration_days are exclusive");
      }
      if (durationDays != null && durationDays < 1) {
        throw new IllegalArgumentException("duration_days must be >= 1, got: " + durationDays);
      }
    }

    /** Single-day rule (legacy constructor). */
    public FixedMonthDay(String key, String name, int month, int day, String chronology) {
      this(key, name, month, day, chronology, null, null, null);
    }

    public boolean hasEndDate() {
      return endMonth != null;
    }

    @Override
    public int spanDays() {
      return durationDays == null ? 1 : durationDays;
    }

    @Override
    public FixedMonthDay withIdentity(String key, String name) {
      return new FixedMonthDay(key, name, month, day, chronology, endMonth, endDay, durationDays);
    }
  }

  record NthWeekdayOfMonth(
      String key,
      String name,
      int month,
      DayOfWeek weekday,
      int nth,
      @JsonProperty("duration_days") Integer durationDays)
      implements Rule {
    public NthWeekdayOfMonth {
      if (durationDays != null && durationDays < 1) {
        throw new IllegalArgumentException("duration_days must be >= 1, got: " + durationDays);
      }
    }

    /** Single-day rule (legacy constructor). */
    public NthWeekdayOfMonth(String key, String name, int month, DayOfWeek weekday, int nth) {
      this(key, name, month, weekday, nth, null);
    }

    @Override
    public int spanDays() {
      return durationDays == null ? 1 : durationDays;
    }

    @Override
    public NthWeekdayOfMonth withIdentity(String key, String name) {
      return new NthWeekdayOfMonth(key, name, month, weekday, nth, durationDays);
    }
  }

  /**
   * A rule that calculates dates relative to a reference point.
   *
   * <p>The reference can be specified in two ways:
   *
   * <ul>
   *   <li>Named reference (e.g., "easter") - references a pre-defined formula
   *   <li>Fixed month/day (referenceMonth + referenceDay) - a fixed date in each year
   * </ul>
   *
   * <p>The offset can be specified in two ways:
   *
   * <ul>
   *   <li>offsetDays - simple day offset (e.g., -2 for Good Friday relative to Easter)
   *   <li>offsetWeekday - nth weekday before/after reference (e.g., 1st Tuesday after Nov 1st)
   * </ul>
   */
  record RelativeToReference(
      String key,
      String name,
      String reference,
      @JsonProperty("offset_days") Integer offsetDays,
      @JsonProperty("reference_month") Integer referenceMonth,
      @JsonProperty("reference_day") Integer referenceDay,
      @JsonProperty("offset_weekday") WeekdayOffset offsetWeekday,
      @JsonProperty("duration_days") Integer durationDays)
      implements Rule {

    public RelativeToReference {
      if (durationDays != null && durationDays < 1) {
        throw new IllegalArgumentException("duration_days must be >= 1, got: " + durationDays);
      }
    }

    /** Single-day rule (legacy constructor). */
    public RelativeToReference(
        String key,
        String name,
        String reference,
        Integer offsetDays,
        Integer referenceMonth,
        Integer referenceDay,
        WeekdayOffset offsetWeekday) {
      this(key, name, reference, offsetDays, referenceMonth, referenceDay, offsetWeekday, null);
    }

    /** Legacy constructor for simple day offset with named reference. */
    public RelativeToReference(String key, String name, String reference, int offsetDays) {
      this(key, name, reference, offsetDays, null, null, null, null);
    }

    /** Constructor for weekday offset with fixed month/day reference. */
    public RelativeToReference(
        String key,
        String name,
        int referenceMonth,
        int referenceDay,
        WeekdayOffset offsetWeekday) {
      this(key, name, null, null, referenceMonth, referenceDay, offsetWeekday, null);
    }

    /** Returns true if this rule uses a named reference (like "easter"). */
    public boolean usesNamedReference() {
      return reference != null && !reference.isBlank();
    }

    /** Returns true if this rule uses a fixed month/day reference. */
    public boolean usesFixedReference() {
      return referenceMonth != null && referenceDay != null;
    }

    /** Returns true if this rule uses a weekday offset. */
    public boolean usesWeekdayOffset() {
      return offsetWeekday != null;
    }

    @Override
    public int spanDays() {
      return durationDays == null ? 1 : durationDays;
    }

    @Override
    public RelativeToReference withIdentity(String key, String name) {
      return new RelativeToReference(
          key,
          name,
          reference,
          offsetDays,
          referenceMonth,
          referenceDay,
          offsetWeekday,
          durationDays);
    }
  }
}
