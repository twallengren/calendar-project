package com.bdc.resolver;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Key deduplication, explicit NONE override, weekend period conflicts, module cycles. */
class ResolverMergeTest {

  @TempDir Path dir;

  private SpecRegistry load(String calendars, String modules) throws Exception {
    Path cal = dir.resolve("calendars");
    Path mod = dir.resolve("modules");
    Files.createDirectories(cal);
    Files.createDirectories(mod);
    int i = 0;
    for (String doc : calendars.split("---\n")) {
      if (!doc.isBlank()) Files.writeString(cal.resolve("c" + (i++) + ".yaml"), doc);
    }
    for (String doc : modules.split("---\n")) {
      if (!doc.isBlank()) Files.writeString(mod.resolve("m" + (i++) + ".yaml"), doc);
    }
    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(cal);
    registry.loadModulesFromDirectory(mod);
    registry.assertNoLoadErrors();
    return registry;
  }

  @Test
  void laterDeclarationWinsByKey() throws Exception {
    SpecRegistry registry =
        load(
            """
            kind: calendar
            id: CAL
            uses: [base, override]
            """,
            """
            kind: module
            id: base
            event_sources:
              - key: xmas
                name: Christmas (base)
                rule: {type: fixed_month_day, month: 12, day: 25}
            ---
            kind: module
            id: override
            event_sources:
              - key: xmas
                name: Christmas (override)
                rule: {type: fixed_month_day, month: 12, day: 26}
            """);
    ResolvedSpec resolved = new SpecResolver(registry).resolve("CAL");
    assertEquals(1, resolved.eventSources().size());
    assertEquals("Christmas (override)", resolved.eventSources().get(0).name());
    assertEquals("module:override", resolved.sourceOrigins().get("xmas"));
  }

  @Test
  void explicitNoneOverridesInheritedPolicy() throws Exception {
    SpecRegistry registry =
        load(
            """
            kind: calendar
            id: PARENT
            weekend_shift_policy: NEAREST_WEEKDAY
            ---
            kind: calendar
            id: CHILD
            extends: [PARENT]
            weekend_shift_policy: NONE
            ---
            kind: calendar
            id: INHERITS
            extends: [PARENT]
            """,
            "");
    SpecResolver resolver = new SpecResolver(registry);
    assertEquals(WeekendShiftPolicy.NONE, resolver.resolve("CHILD").weekendShiftPolicy());
    assertEquals(
        WeekendShiftPolicy.NEAREST_WEEKDAY, resolver.resolve("INHERITS").weekendShiftPolicy());
  }

  @Test
  void conflictingWeekendModulesFail() throws Exception {
    SpecRegistry registry =
        load(
            """
            kind: calendar
            id: CAL
            uses: [satsun, frisat]
            """,
            """
            kind: module
            id: satsun
            policies:
              weekends: [SATURDAY, SUNDAY]
            ---
            kind: module
            id: frisat
            policies:
              weekends: [FRIDAY, SATURDAY]
            """);
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> new SpecResolver(registry).resolve("CAL"));
    assertTrue(ex.getMessage().contains("Conflicting weekend policies"), ex.getMessage());
  }

  @Test
  void effectiveDatedWeekendPeriodsResolveInOrder() throws Exception {
    SpecRegistry registry =
        load(
            """
            kind: calendar
            id: CAL
            uses: [history]
            """,
            """
            kind: module
            id: history
            policies:
              weekends:
                - {days: [THURSDAY, FRIDAY], to: 2013-06-28}
                - {days: [FRIDAY, SATURDAY], from: 2013-06-29}
            """);
    WeekendPolicy policy = new SpecResolver(registry).resolve("CAL").weekendPolicy();
    assertTrue(policy.isEffectiveDated());
    assertTrue(policy.isWeekend(LocalDate.of(2013, 6, 27))); // Thursday, old weekend
    assertFalse(policy.isWeekend(LocalDate.of(2013, 7, 4))); // Thursday, new weekend
    assertTrue(policy.isWeekend(LocalDate.of(2013, 7, 6))); // Saturday, new weekend
  }

  @Test
  void moduleCycleThrows() throws Exception {
    SpecRegistry registry =
        load(
            """
            kind: calendar
            id: CAL
            uses: [a]
            """,
            """
            kind: module
            id: a
            uses: [b]
            ---
            kind: module
            id: b
            uses: [a]
            """);
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> new SpecResolver(registry).resolve("CAL"));
    assertTrue(ex.getMessage().toLowerCase().contains("circular"), ex.getMessage());
  }

  @Test
  void moduleLevelSourceAppliesToItsEventSources() throws Exception {
    SpecRegistry registry =
        load(
            """
            kind: calendar
            id: CAL
            uses: [m]
            """,
            """
            kind: module
            id: m
            source: {id: doc-1, title: Some document}
            event_sources:
              - key: a
                name: A
                rule: {type: fixed_month_day, month: 1, day: 1}
              - key: b
                name: B
                source: other-doc
                rule: {type: fixed_month_day, month: 2, day: 1}
            """);
    ResolvedSpec resolved = new SpecResolver(registry).resolve("CAL");
    assertEquals("doc-1", resolved.eventSource("a").source().get(0).id());
    assertEquals("other-doc", resolved.eventSource("b").source().get(0).id());
  }

  @Test
  void unknownPropertyIsALoadError() throws Exception {
    Path cal = dir.resolve("calendars");
    Files.createDirectories(cal);
    Files.writeString(
        cal.resolve("bad.yaml"),
        """
        kind: calendar
        id: BAD
        weekend_shift_polcy: NEAREST_WEEKDAY
        """);
    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(cal);
    assertEquals(1, registry.loadErrors().size());
    assertTrue(registry.loadErrors().get(0).message().contains("weekend_shift_polcy"));
    assertThrows(IllegalStateException.class, registry::assertNoLoadErrors);
  }
}
