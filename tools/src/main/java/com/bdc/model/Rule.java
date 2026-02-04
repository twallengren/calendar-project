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
  @JsonSubTypes.Type(value = Rule.ExplicitDates.class, name = "explicit_dates"),
  @JsonSubTypes.Type(value = Rule.FixedMonthDay.class, name = "fixed_month_day"),
  @JsonSubTypes.Type(value = Rule.NthWeekdayOfMonth.class, name = "nth_weekday_of_month"),
  @JsonSubTypes.Type(value = Rule.RelativeToReference.class, name = "relative_to_reference")
})
public sealed interface Rule
    permits Rule.ExplicitDates,
        Rule.FixedMonthDay,
        Rule.NthWeekdayOfMonth,
        Rule.RelativeToReference {

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

  record ExplicitDates(String key, String name, List<AnnotatedDate> dates) implements Rule {}

  record FixedMonthDay(String key, String name, int month, int day, String chronology)
      implements Rule {
    public FixedMonthDay {
      if (chronology == null) chronology = "ISO";
    }
  }

  record NthWeekdayOfMonth(String key, String name, int month, DayOfWeek weekday, int nth)
      implements Rule {}

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
      @JsonProperty("offset_weekday") WeekdayOffset offsetWeekday)
      implements Rule {

    /** Legacy constructor for simple day offset with named reference. */
    public RelativeToReference(String key, String name, String reference, int offsetDays) {
      this(key, name, reference, offsetDays, null, null, null);
    }

    /** Constructor for weekday offset with fixed month/day reference. */
    public RelativeToReference(
        String key,
        String name,
        int referenceMonth,
        int referenceDay,
        WeekdayOffset offsetWeekday) {
      this(key, name, null, null, referenceMonth, referenceDay, offsetWeekday);
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
  }
}
