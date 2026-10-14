package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.chronology.NativeDate;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import com.bdc.stream.DateStream;
import com.bdc.stream.LazyDateStream;
import com.bdc.stream.UnresolvedDateException;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageQuality;
import com.bdc.trust.DayState;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaseCalendarTest {
  private EventGenerator generator;
  private ResolvedSpec spec;

  @BeforeEach
  void setUp() throws Exception {
    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    registry.assertNoLoadErrors();
    generator = new EventGenerator();
    spec = new SpecResolver(registry).resolve("IL-TASE");
  }

  @Test
  void nativeRulesReproduceEveryOfficial2025ClosureWithoutAssumingAnUnapprovedCloseTime() {
    List<Event> events =
        generator.generate(spec, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

    assertEquals(
        Set.of(
            LocalDate.of(2025, 3, 14),
            LocalDate.of(2025, 4, 13),
            LocalDate.of(2025, 4, 18),
            LocalDate.of(2025, 4, 30),
            LocalDate.of(2025, 5, 1),
            LocalDate.of(2025, 6, 1),
            LocalDate.of(2025, 6, 2),
            LocalDate.of(2025, 8, 3),
            LocalDate.of(2025, 9, 22),
            LocalDate.of(2025, 9, 23),
            LocalDate.of(2025, 9, 24),
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2025, 10, 2),
            LocalDate.of(2025, 10, 6),
            LocalDate.of(2025, 10, 7),
            LocalDate.of(2025, 10, 13),
            LocalDate.of(2025, 10, 14)),
        datesOf(events, EventType.CLOSED));

    assertTrue(datesOf(events, EventType.EARLY_CLOSE).isEmpty());
  }

  @Test
  void tradingWeekChangesOnTheDocumentedEffectiveDate() {
    List<Event> events =
        generator.generate(spec, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12));

    Set<LocalDate> weekends = datesOf(events, EventType.WEEKEND);
    assertTrue(weekends.contains(LocalDate.of(2026, 1, 2)));
    assertTrue(weekends.contains(LocalDate.of(2026, 1, 3)));
    assertTrue(
        weekends.contains(LocalDate.of(2026, 1, 4)),
        "TASE: as of January 4th, 2026 trading takes place Monday to Friday");
    assertFalse(weekends.contains(LocalDate.of(2026, 1, 5)));
    assertTrue(weekends.contains(LocalDate.of(2026, 1, 10)));
    assertTrue(weekends.contains(LocalDate.of(2026, 1, 11)));

    Event friday =
        events.stream()
            .filter(event -> event.date().equals(LocalDate.of(2026, 1, 9)))
            .filter(event -> event.type() == EventType.EARLY_CLOSE)
            .findFirst()
            .orElseThrow();
    assertEquals(LocalTime.of(13, 50), friday.closeTime());

    var fridayAssessment = new LazyDateStream(spec).assessment(LocalDate.of(2026, 1, 9));
    assertEquals(DayState.EARLY_CLOSE, fridayAssessment.scheduledState());
    assertEquals(
        CoverageQuality.VERIFIED,
        fridayAssessment.completeness().get(CompletenessScope.EARLY_CLOSES));
  }

  @Test
  void exactHebrewDatesMapToTheTwoIndependentlyAnnounced2026Closures() {
    List<Event> events =
        generator.generate(spec, LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23));

    assertEquals(
        Set.of(LocalDate.of(2026, 4, 21), LocalDate.of(2026, 4, 22)),
        datesOf(events, EventType.CLOSED));
  }

  @Test
  void assessmentRetainsNativeProvenanceWhileActualDayStateFailsClosed() {
    DateStream stream = new LazyDateStream(spec);

    var passover = stream.assessment(LocalDate.of(2025, 4, 13));
    assertEquals(DayState.CLOSED, passover.scheduledState());
    assertEquals(DayState.UNKNOWN, passover.state());
    assertEquals(
        CoverageQuality.VERIFIED,
        passover.completeness().get(CompletenessScope.SCHEDULED_CLOSURES));
    assertEquals(
        CoverageQuality.VERIFIED,
        passover.completeness().get(CompletenessScope.UNSCHEDULED_EXCEPTIONS));
    assertEquals(
        CoverageQuality.INCOMPLETE,
        passover.completeness().get(CompletenessScope.EARLY_CLOSES),
        "no operative 2025 close time is sourced, so the session question stays open");
    var passoverEvent =
        passover.events().stream()
            .filter(detail -> detail.event().key().equals("tase_passover_2025"))
            .findFirst()
            .orElseThrow();
    assertEquals(new NativeDate("HEBREW", 5785, "NISAN", 15), passoverEvent.nominalNativeDate());
    assertTrue(passoverEvent.evidenceIds().contains("tase-vacation-schedule-2025"));
    assertFalse(stream.isBusinessDay(LocalDate.of(2025, 4, 13)));
    assertEquals(DayState.CLOSED, passover.businessDayState());
    assertThrows(UnresolvedDateException.class, () -> stream.closeTime(LocalDate.of(2025, 4, 13)));

    var memorial = stream.assessment(LocalDate.of(2026, 4, 21));
    assertEquals(DayState.CLOSED, memorial.scheduledState());
    assertEquals(
        CoverageQuality.VERIFIED,
        memorial.completeness().get(CompletenessScope.SCHEDULED_CLOSURES));
    assertTrue(
        memorial.events().stream()
            .anyMatch(
                detail ->
                    new NativeDate("HEBREW", 5786, "IYAR", 4).equals(detail.nominalNativeDate())));
  }

  /** Every 2026-2028 closure in TASE's vacation schedule tables, and nothing else. */
  @Test
  void nativeRulesReproduceEveryOfficial2026To2028Closure() {
    List<Event> events =
        generator.generate(spec, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31));
    Set<LocalDate> closed = datesOf(events, EventType.CLOSED);
    Set<LocalDate> expected =
        Set.of(
                "2026-03-03",
                "2026-04-01",
                "2026-04-02",
                "2026-04-07",
                "2026-04-08",
                "2026-04-21",
                "2026-04-22",
                "2026-05-21",
                "2026-05-22",
                "2026-07-23",
                "2026-09-11",
                "2026-09-12",
                "2026-09-13",
                "2026-09-18",
                "2026-09-20",
                "2026-09-21",
                "2026-09-25",
                "2026-09-26",
                "2026-10-02",
                "2026-10-03",
                "2026-10-27",
                "2027-03-23",
                "2027-04-21",
                "2027-04-22",
                "2027-04-27",
                "2027-04-28",
                "2027-05-11",
                "2027-05-12",
                "2027-06-10",
                "2027-06-11",
                "2027-08-12",
                "2027-10-01",
                "2027-10-02",
                "2027-10-03",
                "2027-10-08",
                "2027-10-10",
                "2027-10-11",
                "2027-10-15",
                "2027-10-16",
                "2027-10-22",
                "2027-10-23",
                "2028-03-10",
                "2028-03-12",
                "2028-04-10",
                "2028-04-11",
                "2028-04-16",
                "2028-04-17",
                "2028-05-01",
                "2028-05-02",
                "2028-05-30",
                "2028-05-31",
                "2028-08-01",
                "2028-09-20",
                "2028-09-21",
                "2028-09-22",
                "2028-09-29",
                "2028-09-30",
                "2028-10-04",
                "2028-10-05",
                "2028-10-11",
                "2028-10-12")
            .stream()
            .map(LocalDate::parse)
            .collect(Collectors.toSet());
    assertEquals(expected, closed);

    // Friday 13:50 sessions: 46 in 2026 (from 9 January), 48 in 2027; never on a closed Friday.
    List<Event> fridays =
        events.stream()
            .filter(
                e ->
                    e.type() == EventType.EARLY_CLOSE && e.closeTime().equals(LocalTime.of(13, 50)))
            .toList();
    assertEquals(46, fridays.stream().filter(e -> e.date().getYear() == 2026).count());
    assertEquals(48, fridays.stream().filter(e -> e.date().getYear() == 2027).count());
    assertTrue(
        fridays.stream().allMatch(e -> e.date().getDayOfWeek() == java.time.DayOfWeek.FRIDAY));
    assertTrue(fridays.stream().noneMatch(e -> closed.contains(e.date())));
    assertTrue(fridays.stream().noneMatch(e -> e.date().isBefore(LocalDate.of(2026, 1, 9))));

    // Intermediate days at 14:30: the non-Friday weekdays of TASE's stated ranges.
    Set<LocalDate> intermediate =
        events.stream()
            .filter(
                e ->
                    e.type() == EventType.EARLY_CLOSE && e.closeTime().equals(LocalTime.of(14, 30)))
            .map(Event::date)
            .collect(Collectors.toSet());
    assertEquals(
        Set.of(
                "2026-04-06",
                "2026-09-28",
                "2026-09-29",
                "2026-09-30",
                "2026-10-01",
                "2027-04-26",
                "2027-10-18",
                "2027-10-19",
                "2027-10-20",
                "2027-10-21",
                "2028-04-12",
                "2028-04-13",
                "2028-10-09",
                "2028-10-10")
            .stream()
            .map(LocalDate::parse)
            .collect(Collectors.toSet()),
        intermediate);

    DateStream stream = new LazyDateStream(spec);
    // Dates before 2026-10-14 have incomplete unscheduled-exception coverage, so read the
    // scheduled state; later dates answer the business-day question with PROJECTED confidence.
    assertEquals(
        DayState.CLOSED,
        stream.assessment(LocalDate.of(2026, 10, 27)).scheduledState(),
        "Knesset Election Day");
    assertEquals(
        DayState.OPEN,
        stream.assessment(LocalDate.of(2026, 3, 4)).scheduledState(),
        "Shushan Purim trades");
    assertTrue(stream.isBusinessDay(LocalDate.of(2027, 1, 1)), "1 January is a trading Friday");
    assertEquals(
        java.util.Optional.of(LocalTime.of(13, 50)), stream.closeTime(LocalDate.of(2027, 1, 1)));
    assertEquals(
        LocalTime.of(13, 50),
        stream.eventsOn(LocalDate.of(2026, 4, 3)).get(0).closeTime(),
        "intermediate Friday keeps the Friday schedule");
    assertEquals(
        CoverageQuality.VERIFIED,
        stream
            .assessment(LocalDate.of(2027, 6, 1))
            .completeness()
            .get(CompletenessScope.SCHEDULED_CLOSURES));
  }

  @Test
  void unscheduledExceptionsAreVerifiedFromTheSessionRecordAndProjectedForTheFuture() {
    DateStream stream = new LazyDateStream(spec);
    LocalDate warDay = LocalDate.of(2025, 6, 15);
    var assessment = stream.assessment(warDay);
    assertEquals(
        CoverageQuality.VERIFIED,
        assessment.completeness().get(CompletenessScope.UNSCHEDULED_EXCEPTIONS));
    assertTrue(assessment.evidenceIds().contains("tase-ta35-eod-sessions"));
    assertTrue(stream.isBusinessDay(warDay), "TASE traded every scheduled day of June 2025");
    assertEquals(EventStatus.CONFIRMED, assessment.businessDayConfidence());
    assertEquals(EventStatus.UNKNOWN, stream.status(warDay), "2025 close times are unsourced");

    LocalDate future = LocalDate.of(2027, 6, 1);
    assertEquals(
        CoverageQuality.PROJECTED,
        stream.assessment(future).completeness().get(CompletenessScope.UNSCHEDULED_EXCEPTIONS));
    assertTrue(stream.isBusinessDay(future));
    assertEquals(EventStatus.PROJECTED, stream.assessment(future).businessDayConfidence());
  }

  private static Set<LocalDate> datesOf(List<Event> events, EventType type) {
    return events.stream()
        .filter(event -> event.type() == type)
        .map(Event::date)
        .collect(Collectors.toSet());
  }
}
