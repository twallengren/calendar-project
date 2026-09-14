package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.chronology.NativeDate;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
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
    assertFalse(weekends.contains(LocalDate.of(2026, 1, 4)));
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
        CoverageQuality.INCOMPLETE,
        passover.completeness().get(CompletenessScope.UNSCHEDULED_EXCEPTIONS));
    var passoverEvent =
        passover.events().stream()
            .filter(detail -> detail.event().key().equals("tase_passover_2025"))
            .findFirst()
            .orElseThrow();
    assertEquals(new NativeDate("HEBREW", 5785, "NISAN", 15), passoverEvent.nominalNativeDate());
    assertTrue(passoverEvent.evidenceIds().contains("tase-vacation-schedule-2025"));
    assertThrows(
        UnresolvedDateException.class, () -> stream.isBusinessDay(LocalDate.of(2025, 4, 13)));

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

  @Test
  void incompleteGapsAndDisputedTransitionDayRejectBooleanQueries() {
    DateStream stream = new LazyDateStream(spec);

    LocalDate transition = LocalDate.of(2026, 1, 4);
    var transitionAssessment = stream.assessment(transition);
    assertEquals(DayState.OPEN, transitionAssessment.scheduledState());
    assertEquals(DayState.UNKNOWN, transitionAssessment.state());
    assertEquals(
        CoverageQuality.INCOMPLETE,
        transitionAssessment.completeness().get(CompletenessScope.SCHEDULED_CLOSURES));
    assertThrows(UnresolvedDateException.class, () -> stream.isBusinessDay(transition));

    LocalDate gap = LocalDate.of(2026, 4, 20);
    assertEquals(
        CoverageQuality.INCOMPLETE,
        stream.assessment(gap).completeness().get(CompletenessScope.SCHEDULED_CLOSURES));
    assertThrows(UnresolvedDateException.class, () -> stream.isBusinessDay(gap));
  }

  private static Set<LocalDate> datesOf(List<Event> events, EventType type) {
    return events.stream()
        .filter(event -> event.type() == type)
        .map(Event::date)
        .collect(Collectors.toSet());
  }
}
