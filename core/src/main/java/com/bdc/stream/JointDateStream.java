package com.bdc.stream;

import com.bdc.chronology.DateRange;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageQuality;
import com.bdc.trust.DayAssessment;
import com.bdc.trust.DayState;
import com.bdc.trust.EventDetails;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Several calendars queried as one: a date is a business day only when <em>every</em> member trades
 * on it.
 *
 * <h2>Why there is only one factory</h2>
 *
 * <p>Two descriptions of this stream are often given separate names — "intersection" (open only
 * where all members are open) and "union of closures" / "closed in any" (closed wherever any member
 * is closed). They are the same predicate: {@code open(A) && open(B)} is the negation of {@code
 * closed(A) || closed(B)}. Shipping two factory names for one behaviour invites callers to believe
 * they differ, so this class exposes exactly one: {@link #joint(List)}. A genuinely different
 * predicate (for example "open in any member") would be a different class with a different name;
 * none is provided.
 *
 * <p>This is the settlement/operations semantic: a trade between two markets can only settle on a
 * day both are open, so T+N is counted on the joint stream.
 *
 * <h2>Composed answers</h2>
 *
 * <ul>
 *   <li>{@link #calendarId()} — member ids joined with {@code +}, in member order ({@code
 *       US-NYSE+SA-TADAWUL}).
 *   <li>{@link #eventsOn} / {@link #eventsInRange} — every member's events concatenated in member
 *       order (range queries then stably sorted by date), each with its {@code sourceModule}
 *       prefixed by the member calendar id ({@code US-NYSE/module:christmas}); a member event with
 *       no source module gets the bare member id.
 *   <li>{@link #range()} — the intersection of the member ranges; constructing a joint stream whose
 *       members do not overlap is an error.
 *   <li>{@link #verifiedThrough()} — the earliest of the members that declare one (members without
 *       a declared value do not constrain it); empty when no member declares one.
 *   <li>{@link #status} — {@code UNKNOWN} if any member is UNKNOWN, else {@code PROJECTED} if any
 *       member is PROJECTED, else {@code CONFIRMED}: the joint answer is only as good as its worst
 *       member.
 *   <li>{@link #closeTime} — the earliest early close declared by any member on that date. A joint
 *       date can carry an early close and still not be a business day (another member is closed);
 *       check {@link #isBusinessDay} first.
 * </ul>
 */
public final class JointDateStream implements DateStream {

  private final List<DateStream> members;
  private final String calendarId;
  private final DateRange range;

  private JointDateStream(List<DateStream> members) {
    this.members = List.copyOf(members);
    this.calendarId = String.join("+", this.members.stream().map(DateStream::calendarId).toList());
    LocalDate start = LocalDate.MIN;
    LocalDate end = LocalDate.MAX;
    for (DateStream member : this.members) {
      DateRange r = member.range();
      if (r.start().isAfter(start)) {
        start = r.start();
      }
      if (r.end().isBefore(end)) {
        end = r.end();
      }
    }
    if (start.isAfter(end)) {
      throw new IllegalArgumentException(
          "Calendars "
              + calendarId
              + " have no overlapping covered range: "
              + this.members.stream()
                  .map(m -> m.calendarId() + " " + m.range().start() + ".." + m.range().end())
                  .toList());
    }
    this.range = new DateRange(start, end);
  }

  /**
   * A stream over all the given calendars: open only on dates every member is open on.
   *
   * <p>A single-member list is returned unwrapped — the joint of one calendar is that calendar, and
   * keeping its own id and events avoids pointless {@code US-NYSE} to {@code US-NYSE} relabelling.
   *
   * @throws IllegalArgumentException if the list is empty or the member ranges do not overlap
   */
  public static DateStream joint(List<DateStream> members) {
    if (members == null || members.isEmpty()) {
      throw new IllegalArgumentException("joint() needs at least one calendar");
    }
    if (members.size() == 1) {
      return members.get(0);
    }
    return new JointDateStream(members);
  }

  /**
   * @see #joint(List)
   */
  public static DateStream joint(DateStream... members) {
    return joint(List.of(members));
  }

  /** The member streams, in the order they were given. */
  public List<DateStream> members() {
    return members;
  }

  /** The members that do not trade on the given date, in member order (possibly empty). */
  public List<DateStream> closedMembers(LocalDate date) {
    return members.stream().filter(m -> !m.isBusinessDay(date)).toList();
  }

  @Override
  public String calendarId() {
    return calendarId;
  }

  @Override
  public DateRange range() {
    return range;
  }

  @Override
  public Optional<LocalDate> verifiedThrough() {
    return members.stream()
        .map(DateStream::verifiedThrough)
        .flatMap(Optional::stream)
        .min(Comparator.naturalOrder());
  }

  @Override
  public List<Event> eventsInRange(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    checkRange(from);
    checkRange(to);
    List<Event> all = new ArrayList<>();
    for (DateStream member : members) {
      for (Event e : member.eventsInRange(from, to)) {
        all.add(qualify(member, e));
      }
    }
    all.sort(Comparator.comparing(Event::date));
    return all;
  }

  @Override
  public List<Event> eventsOn(LocalDate date) {
    checkRange(date);
    List<Event> all = new ArrayList<>();
    for (DateStream member : members) {
      for (Event e : member.eventsOn(date)) {
        all.add(qualify(member, e));
      }
    }
    return all;
  }

  @Override
  public boolean isBusinessDay(LocalDate date) {
    checkRange(date);
    for (DateStream member : members) {
      if (!member.isBusinessDay(date)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Optional<LocalTime> closeTime(LocalDate date) {
    checkRange(date);
    return members.stream()
        .map(m -> m.closeTime(date))
        .flatMap(Optional::stream)
        .min(Comparator.naturalOrder());
  }

  @Override
  public EventStatus status(LocalDate date) {
    boolean projected = false;
    for (DateStream member : members) {
      EventStatus s = member.status(date);
      if (s == EventStatus.UNKNOWN) {
        return EventStatus.UNKNOWN;
      }
      if (s == EventStatus.PROJECTED) {
        projected = true;
      }
    }
    return projected ? EventStatus.PROJECTED : EventStatus.CONFIRMED;
  }

  @Override
  public DayAssessment assessment(LocalDate date) {
    if (!range.contains(date)) {
      return DateStream.super.assessment(date);
    }
    List<DayAssessment> assessments =
        members.stream().map(member -> member.assessment(date)).toList();
    Map<CompletenessScope, CoverageQuality> completeness = new EnumMap<>(CompletenessScope.class);
    for (CompletenessScope scope : CompletenessScope.values()) {
      List<CoverageQuality> qualities =
          assessments.stream().map(a -> a.completeness().get(scope)).toList();
      completeness.put(
          scope,
          qualities.contains(CoverageQuality.INCOMPLETE)
              ? CoverageQuality.INCOMPLETE
              : qualities.contains(CoverageQuality.PROJECTED)
                  ? CoverageQuality.PROJECTED
                  : CoverageQuality.VERIFIED);
    }
    LinkedHashSet<String> evidence = new LinkedHashSet<>();
    assessments.forEach(assessment -> evidence.addAll(assessment.evidenceIds()));
    DayState scheduled =
        assessments.stream().anyMatch(a -> a.scheduledState() == DayState.CLOSED)
            ? DayState.CLOSED
            : assessments.stream().anyMatch(a -> a.scheduledState() == DayState.EARLY_CLOSE)
                ? DayState.EARLY_CLOSE
                : DayState.OPEN;
    EventStatus confidence =
        assessments.stream().anyMatch(a -> a.effectiveConfidence() == EventStatus.UNKNOWN)
            ? EventStatus.UNKNOWN
            : assessments.stream().anyMatch(a -> a.effectiveConfidence() == EventStatus.PROJECTED)
                ? EventStatus.PROJECTED
                : EventStatus.CONFIRMED;
    List<EventDetails> details = new ArrayList<>();
    for (int i = 0; i < members.size(); i++) {
      DateStream member = members.get(i);
      for (EventDetails detail : assessments.get(i).events()) {
        details.add(
            new EventDetails(
                qualify(member, detail.event()),
                detail.rawStatus(),
                detail.effectiveStatus(),
                detail.evidenceIds(),
                detail.nominalNativeDate(),
                detail.chronologyProfile(),
                detail.chronologyProvider(),
                detail.observationLineage()));
      }
    }
    return new DayAssessment(
        date,
        confidence == EventStatus.UNKNOWN ? DayState.UNKNOWN : scheduled,
        scheduled,
        confidence,
        completeness,
        List.copyOf(evidence),
        details);
  }

  private void checkRange(LocalDate date) {
    if (!range.contains(date)) {
      throw new OutsideCoverageException(calendarId, date, range);
    }
  }

  /** Re-labels a member event so its origin stays visible in the joint stream. */
  private static Event qualify(DateStream member, Event event) {
    String sourceModule =
        event.sourceModule() == null
            ? member.calendarId()
            : member.calendarId() + "/" + event.sourceModule();
    return new Event(
        event.date(),
        event.type(),
        event.description(),
        event.provenance(),
        event.key(),
        sourceModule,
        event.observedFrom(),
        event.closeTime(),
        event.status());
  }
}
