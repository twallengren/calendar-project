package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
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
  void nativeRulesReproduceEveryOfficial2025ClosureAndShortSession() {
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

    assertEquals(
        Set.of(
            LocalDate.of(2025, 4, 14),
            LocalDate.of(2025, 4, 15),
            LocalDate.of(2025, 4, 16),
            LocalDate.of(2025, 4, 17),
            LocalDate.of(2025, 10, 8),
            LocalDate.of(2025, 10, 9),
            LocalDate.of(2025, 10, 12)),
        datesOf(events, EventType.EARLY_CLOSE));
    assertTrue(
        events.stream()
            .filter(event -> event.type() == EventType.EARLY_CLOSE)
            .allMatch(event -> event.closeTime().equals(LocalTime.of(14, 30))));
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
  }

  @Test
  void exactHebrewDatesMapToTheTwoIndependentlyAnnounced2026Closures() {
    List<Event> events =
        generator.generate(spec, LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23));

    assertEquals(
        Set.of(LocalDate.of(2026, 4, 21), LocalDate.of(2026, 4, 22)),
        datesOf(events, EventType.CLOSED));
  }

  private static Set<LocalDate> datesOf(List<Event> events, EventType type) {
    return events.stream()
        .filter(event -> event.type() == type)
        .map(Event::date)
        .collect(Collectors.toSet());
  }
}
