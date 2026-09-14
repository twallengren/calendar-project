package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public record CalendarSpec(
    String kind,
    String id,
    Metadata metadata,
    @JsonProperty("extends") List<String> extendsList,
    List<String> uses,
    @JsonProperty("event_sources") List<EventSource> eventSources,
    Map<String, EventType> classifications,
    List<Delta> deltas,
    @JsonProperty("weekend_shift_policy") WeekendShiftPolicy weekendShiftPolicy) {
  public CalendarSpec {
    if (!"calendar".equals(kind)) {
      throw new IllegalArgumentException("kind must be 'calendar', got: " + kind);
    }
    if (extendsList == null) extendsList = List.of();
    if (uses == null) uses = List.of();
    if (eventSources == null) eventSources = List.of();
    if (classifications == null) classifications = Map.of();
    if (deltas == null) deltas = List.of();
    // weekendShiftPolicy stays null when omitted so that a child can explicitly reset an
    // inherited policy to NONE; the resolver applies the default.
  }

  /**
   * Calendar metadata.
   *
   * @param timezone IANA zone id of the market (e.g. America/New_York); required when any event
   *     source declares a close_time
   * @param coverage the date range this calendar is maintained for, and how far it is verified
   * @param kind what the calendar is for: {@code market} (a tradable venue, the default) or {@code
   *     base} (a building block that composes into market calendars and is not itself a venue)
   * @param mic the market's ISO 10383 Market Identifier Code (e.g. XLON), or null; {@code validate}
   *     warns under {@code --strict} when a {@code market}-kind calendar has none
   * @param aliases other spellings callers may already use to look this calendar up (e.g. legacy
   *     exchange_calendars ids, or a segment MIC distinct from the primary {@code mic})
   */
  public record Metadata(
      String name,
      String description,
      String chronology,
      String timezone,
      Coverage coverage,
      String kind,
      String mic,
      List<String> aliases) {

    /** The default {@link #kind()} when a calendar declares none. */
    public static final String KIND_MARKET = "market";

    /** A composition building block rather than a tradable venue. */
    public static final String KIND_BASE = "base";

    public Metadata {
      if (chronology == null) chronology = "ISO";
      if (timezone != null) {
        try {
          ZoneId.of(timezone);
        } catch (Exception e) {
          throw new IllegalArgumentException("Invalid timezone: " + timezone, e);
        }
      }
      if (kind == null) kind = KIND_MARKET;
      if (!KIND_MARKET.equals(kind) && !KIND_BASE.equals(kind)) {
        throw new IllegalArgumentException(
            "metadata.kind must be '" + KIND_MARKET + "' or '" + KIND_BASE + "', got: " + kind);
      }
      if (aliases == null) aliases = List.of();
    }

    /** Legacy constructor without mic/aliases. */
    public Metadata(
        String name,
        String description,
        String chronology,
        String timezone,
        Coverage coverage,
        String kind) {
      this(name, description, chronology, timezone, coverage, kind, null, null);
    }

    /** Legacy constructor without timezone, coverage, kind, mic and aliases. */
    public Metadata(
        String name, String description, String chronology, String timezone, Coverage coverage) {
      this(name, description, chronology, timezone, coverage, null, null, null);
    }

    /** Legacy constructor without timezone, coverage, kind, mic and aliases. */
    public Metadata(String name, String description, String chronology) {
      this(name, description, chronology, null, null, null, null, null);
    }
  }

  /**
   * Declares the range a calendar is maintained for.
   *
   * @param from first date covered (inclusive)
   * @param to last date covered (inclusive)
   * @param verifiedThrough last date up to which the data has been checked against sources
   */
  public record Coverage(
      LocalDate from, LocalDate to, @JsonProperty("verified_through") LocalDate verifiedThrough) {
    public Coverage {
      if (from != null && to != null && from.isAfter(to)) {
        throw new IllegalArgumentException("coverage.from must not be after coverage.to");
      }
      if (verifiedThrough != null && to != null && verifiedThrough.isAfter(to)) {
        throw new IllegalArgumentException(
            "coverage.verified_through must not be after coverage.to");
      }
    }

    public boolean contains(LocalDate date) {
      if (from != null && date.isBefore(from)) return false;
      if (to != null && date.isAfter(to)) return false;
      return true;
    }
  }
}
