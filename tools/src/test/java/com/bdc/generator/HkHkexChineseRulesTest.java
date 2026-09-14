package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.resolver.SpecResolver;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HkHkexChineseRulesTest {
  private static com.bdc.model.ResolvedSpec hkex;

  @BeforeAll
  static void loadCalendar() throws Exception {
    var registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    registry.assertNoLoadErrors();
    hkex = new SpecResolver(registry).resolve("HK-HKEX");
  }

  @Test
  void authoritative2027ExceptionHasExactCompleteRowsAndNoFalseNativeOrigin() {
    LocalDate from = LocalDate.of(2027, 2, 6);
    LocalDate to = LocalDate.of(2027, 2, 10);
    var generator = new EventGenerator();
    var actual = generator.generateWithDetails(hkex, from, to);
    assertEquals(
        List.of(
            new Event(
                LocalDate.of(2027, 2, 6),
                EventType.WEEKEND,
                "Saturday",
                "weekend_policy",
                "weekend",
                "weekend_policy",
                null,
                null,
                EventStatus.CONFIRMED),
            new Event(
                LocalDate.of(2027, 2, 7),
                EventType.WEEKEND,
                "Sunday",
                "weekend_policy",
                "weekend",
                "weekend_policy",
                null,
                null,
                EventStatus.CONFIRMED),
            new Event(
                LocalDate.of(2027, 2, 8),
                EventType.CLOSED,
                "Lunar New Year (The third day of Lunar New Year)",
                "delta:add",
                "hk_lunar_new_year",
                "module:hk_lunar_new_year",
                null,
                null,
                EventStatus.CONFIRMED),
            new Event(
                LocalDate.of(2027, 2, 9),
                EventType.CLOSED,
                "Lunar New Year (The fourth day of Lunar New Year)",
                "delta:add",
                "hk_lunar_new_year",
                "module:hk_lunar_new_year",
                null,
                null,
                EventStatus.CONFIRMED)),
        actual.stream().map(CompiledEvent::event).toList());
    for (CompiledEvent row : actual) {
      if (!"hk_lunar_new_year".equals(row.event().key())) continue;
      assertNull(row.provenance().nominalNativeDate());
      assertTrue(row.provenance().evidenceIds().contains("hko-gregorian-lunar-conversion"));
      assertTrue(row.provenance().evidenceIds().contains("govhk-general-holidays"));
      assertTrue(row.provenance().evidenceIds().contains("hkex-calendar"));
    }
  }

  @Test
  void hkexNativeAndReferenceRulesAreRangeConsistent() {
    var generator = new EventGenerator();
    LocalDate from = LocalDate.of(2021, 4, 1);
    LocalDate to = LocalDate.of(2027, 2, 10);
    var wide = generator.generate(hkex, LocalDate.of(2018, 1, 1), LocalDate.of(2027, 12, 31));
    assertEquals(
        wide.stream()
            .filter(event -> !event.date().isBefore(from) && !event.date().isAfter(to))
            .toList(),
        generator.generate(hkex, from, to));
  }
}
