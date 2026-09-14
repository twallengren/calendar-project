package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Defines an event source with optional year constraints.
 *
 * @param key unique identifier for the event
 * @param name display name for the event
 * @param rule the recurrence rule
 * @param defaultClassification event type (CLOSED, EARLY_CLOSE, etc.)
 * @param shiftable whether the event shifts when falling on a weekend
 * @param activeYears list of year ranges when this event is active, null means always active
 * @param shiftPolicy per-event weekend shift policy; null inherits the calendar policy when {@code
 *     shiftable}, otherwise NONE
 * @param onlyIfWeekday if set, occurrences are dropped unless they fall on one of these weekdays
 * @param closeTime local close time for EARLY_CLOSE events (e.g. 13:00)
 * @param status CONFIRMED (announced by an authority) or PROJECTED (computed, may change)
 * @param source structured citations for the authoritative source of this event
 * @param displaces keys of CLOSED event sources this event outranks: when this event shifts onto a
 *     weekday slot already held only by events with these keys, it takes the slot and they
 *     re-cascade forward. Empty means no displacement.
 */
public record EventSource(
    String key,
    String name,
    Rule rule,
    @JsonProperty("default_classification") EventType defaultClassification,
    Boolean shiftable,
    @JsonProperty("active_years") @JsonDeserialize(using = YearRangeListDeserializer.class)
        List<YearRange> activeYears,
    @JsonProperty("shift_policy") WeekendShiftPolicy shiftPolicy,
    @JsonProperty("only_if_weekday") List<DayOfWeek> onlyIfWeekday,
    @JsonProperty("close_time") @JsonDeserialize(using = LocalTimeDeserializer.class)
        LocalTime closeTime,
    EventStatus status,
    @JsonDeserialize(using = SourceListDeserializer.class) List<SourceCitation> source,
    List<String> displaces) {

  /** Represents a range of years. If start is null, it means "from inception". */
  public record YearRange(Integer start, Integer end) {
    /** Creates a single-year range. */
    public YearRange(int year) {
      this(year, year);
    }

    /** Check if a year falls within this range. */
    public boolean contains(int year) {
      if (start != null && year < start) {
        return false;
      }
      if (end != null && year > end) {
        return false;
      }
      return true;
    }
  }

  public EventSource {
    if (defaultClassification == null) {
      defaultClassification = EventType.CLOSED;
    }
    // Default: fixed_month_day rules are shiftable, others are not
    if (shiftable == null) {
      shiftable = (rule instanceof Rule.FixedMonthDay);
    }
    if (status == null) {
      status = EventStatus.CONFIRMED;
    }
    if (source == null) {
      source = List.of();
    }
    if (displaces == null) {
      displaces = List.of();
    }
    if (onlyIfWeekday != null && onlyIfWeekday.isEmpty()) {
      onlyIfWeekday = null;
    }
    // The rule's key/name are optional in YAML and inherited from the event source.
    if (rule != null) {
      String ruleKey = rule.key() != null ? rule.key() : key;
      String ruleName = rule.name() != null ? rule.name() : name;
      if (ruleKey != rule.key() || ruleName != rule.name()) {
        rule = rule.withIdentity(ruleKey, ruleName);
      }
    }
  }

  /** Legacy constructor without the per-event policy and provenance fields. */
  public EventSource(
      String key,
      String name,
      Rule rule,
      EventType defaultClassification,
      Boolean shiftable,
      List<YearRange> activeYears) {
    this(
        key,
        name,
        rule,
        defaultClassification,
        shiftable,
        activeYears,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /** Legacy constructor without {@code displaces}. */
  public EventSource(
      String key,
      String name,
      Rule rule,
      EventType defaultClassification,
      Boolean shiftable,
      List<YearRange> activeYears,
      WeekendShiftPolicy shiftPolicy,
      List<DayOfWeek> onlyIfWeekday,
      LocalTime closeTime,
      EventStatus status,
      List<SourceCitation> source) {
    this(
        key,
        name,
        rule,
        defaultClassification,
        shiftable,
        activeYears,
        shiftPolicy,
        onlyIfWeekday,
        closeTime,
        status,
        source,
        null);
  }

  /** Check if this event source is active on a given date. */
  public boolean isActiveOn(LocalDate date) {
    if (activeYears == null || activeYears.isEmpty()) {
      return true;
    }
    int year = date.getYear();
    return activeYears.stream().anyMatch(range -> range.contains(year));
  }

  /**
   * The shift policy that applies to this source: the explicit {@code shift_policy} if given;
   * otherwise {@code DROP} for EARLY_CLOSE sources (a half day whose nominal date is not a session
   * is simply not observed, regardless of {@code shiftable} and of the calendar's {@code
   * weekend_shift_policy}, which only governs full closures); otherwise the calendar default when
   * {@code shiftable}, otherwise NONE.
   */
  public WeekendShiftPolicy effectiveShiftPolicy(WeekendShiftPolicy calendarDefault) {
    if (shiftPolicy != null) {
      return shiftPolicy;
    }
    if (defaultClassification == EventType.EARLY_CLOSE) {
      return WeekendShiftPolicy.DROP;
    }
    if (Boolean.TRUE.equals(shiftable) && calendarDefault != null) {
      return calendarDefault;
    }
    return WeekendShiftPolicy.NONE;
  }

  /** True if the rule declares an identity that disagrees with the event source. */
  public boolean hasIdentityMismatch() {
    return rule != null
        && ((rule.key() != null && !rule.key().equals(key))
            || (rule.name() != null && name != null && !rule.name().equals(name)));
  }
}
