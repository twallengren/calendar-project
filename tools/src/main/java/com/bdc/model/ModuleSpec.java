package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record ModuleSpec(
    String kind,
    String id,
    List<String> uses,
    Policies policies,
    List<Reference> references,
    @JsonProperty("event_sources") List<EventSource> eventSources,
    @JsonDeserialize(using = SourceListDeserializer.class) List<SourceCitation> source) {
  public ModuleSpec {
    if (!"module".equals(kind)) {
      throw new IllegalArgumentException("kind must be 'module', got: " + kind);
    }
    if (uses == null) uses = List.of();
    if (references == null) references = List.of();
    if (eventSources == null) eventSources = List.of();
    if (source == null) source = List.of();
  }

  /** Legacy constructor without a module-level source citation. */
  public ModuleSpec(
      String kind,
      String id,
      List<String> uses,
      Policies policies,
      List<Reference> references,
      List<EventSource> eventSources) {
    this(kind, id, uses, policies, references, eventSources, null);
  }

  /**
   * Module policies.
   *
   * @param weekends effective-dated weekend periods (legacy flat weekday lists are accepted and
   *     become one open-ended period)
   */
  public record Policies(
      @JsonDeserialize(using = WeekendPeriodListDeserializer.class) List<WeekendPeriod> weekends) {
    public Policies {
      if (weekends == null) weekends = List.of();
    }

    /** Creates a single open-ended weekend period from a list of weekdays. */
    public static Policies ofWeekendDays(List<DayOfWeek> days) {
      Set<DayOfWeek> set = days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days);
      return new Policies(List.of(WeekendPeriod.always(set)));
    }

    /** Union of weekend days across all periods. */
    public Set<DayOfWeek> weekendDays() {
      Set<DayOfWeek> union = EnumSet.noneOf(DayOfWeek.class);
      for (WeekendPeriod period : weekends) {
        union.addAll(period.days());
      }
      return union;
    }
  }
}
