package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.emitter.CsvEmitter;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeneratorGoldenTest {

  /** Filter to exclude WEEKEND events when testing holiday-specific logic. */
  private static final Predicate<Event> NON_WEEKEND = e -> e.type() != EventType.WEEKEND;

  private SpecRegistry registry;
  private SpecResolver resolver;
  private EventGenerator generator;
  private CsvEmitter emitter;

  @BeforeEach
  void setUp() throws Exception {
    registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    resolver = new SpecResolver(registry);
    generator = new EventGenerator();
    emitter = new CsvEmitter();
  }

  @Test
  void generateUSMarketBase2024() {
    ResolvedSpec spec = resolver.resolve("US-MARKET-BASE");
    List<Event> events =
        generator.generate(spec, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
    List<Event> nonWeekendEvents = events.stream().filter(NON_WEEKEND).toList();

    // Should have 6 non-weekend events for US-MARKET-BASE in 2024:
    // New Year's Day, Memorial Day, Independence Day, Labor Day, Thanksgiving, Christmas
    assertEquals(6, nonWeekendEvents.size());

    // Verify specific dates
    assertTrue(
        nonWeekendEvents.stream()
            .anyMatch(
                e ->
                    e.date().equals(LocalDate.of(2024, 1, 1))
                        && e.description().equals("New Year's Day")));
    assertTrue(
        nonWeekendEvents.stream()
            .anyMatch(
                e ->
                    e.date().equals(LocalDate.of(2024, 5, 27))
                        && e.description().equals("Memorial Day")));
    assertTrue(
        nonWeekendEvents.stream()
            .anyMatch(
                e ->
                    e.date().equals(LocalDate.of(2024, 7, 4))
                        && e.description().equals("Independence Day")));
    assertTrue(
        nonWeekendEvents.stream()
            .anyMatch(
                e ->
                    e.date().equals(LocalDate.of(2024, 9, 2))
                        && e.description().equals("Labor Day")));
    assertTrue(
        nonWeekendEvents.stream()
            .anyMatch(
                e ->
                    e.date().equals(LocalDate.of(2024, 11, 28))
                        && e.description().equals("Thanksgiving Day")));
    assertTrue(
        nonWeekendEvents.stream()
            .anyMatch(
                e ->
                    e.date().equals(LocalDate.of(2024, 12, 25))
                        && e.description().equals("Christmas Day")));
  }

  @Test
  void generateDeterministicOutput() {
    ResolvedSpec spec = resolver.resolve("US-MARKET-BASE");
    LocalDate from = LocalDate.of(2024, 1, 1);
    LocalDate to = LocalDate.of(2024, 12, 31);

    List<Event> events1 = generator.generate(spec, from, to);
    List<Event> events2 = generator.generate(spec, from, to);

    String csv1 = emitter.emitToString(events1);
    String csv2 = emitter.emitToString(events2);

    assertEquals(csv1, csv2, "Output should be deterministic");
  }

  @Test
  void generateNYSECalendar() {
    ResolvedSpec spec = resolver.resolve("US-NYSE");
    List<Event> events =
        generator.generate(spec, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
    List<Event> nonWeekendEvents = events.stream().filter(NON_WEEKEND).toList();

    // NYSE has more holidays than base (6)
    assertTrue(nonWeekendEvents.size() > 6);

    // Should include MLK Day (3rd Monday of January)
    assertTrue(
        nonWeekendEvents.stream()
            .anyMatch(
                e ->
                    e.date().equals(LocalDate.of(2024, 1, 15))
                        && e.description().equals("Martin Luther King Jr. Day")));

    // Should include Good Friday
    assertTrue(
        nonWeekendEvents.stream()
            .anyMatch(
                e ->
                    e.date().equals(LocalDate.of(2024, 3, 29))
                        && e.description().equals("Good Friday")));
  }
}
