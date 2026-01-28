package com.bdc.resolver;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.ResolvedSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResolverTest {

  private SpecRegistry registry;
  private SpecResolver resolver;

  @BeforeEach
  void setUp() throws Exception {
    registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    resolver = new SpecResolver(registry);
  }

  @Test
  void resolveBaseCalendar() {
    ResolvedSpec resolved = resolver.resolve("US-MARKET-BASE");

    assertNotNull(resolved);
    assertEquals("US-MARKET-BASE", resolved.id());
    assertFalse(resolved.eventSources().isEmpty());
    assertTrue(resolved.resolutionChain().contains("module:weekend_sat_sun"));
    assertTrue(resolved.resolutionChain().contains("calendar:US-MARKET-BASE"));
  }

  @Test
  void resolveExtendedCalendar() {
    ResolvedSpec resolved = resolver.resolve("US-NYSE");

    assertNotNull(resolved);
    assertEquals("US-NYSE", resolved.id());

    // Should have events from parent
    assertTrue(resolved.eventSources().stream().anyMatch(es -> "new_years_day".equals(es.key())));

    // Should have its own events
    assertTrue(resolved.eventSources().stream().anyMatch(es -> "mlk_day".equals(es.key())));
  }

  @Test
  void resolveCalendarWithModules() {
    ResolvedSpec resolved = resolver.resolve("US-CORP-IN-VISIBILITY");

    assertNotNull(resolved);

    // Should have diwali from module
    assertTrue(resolved.eventSources().stream().anyMatch(es -> "diwali".equals(es.key())));
  }

  @Test
  void throwsOnMissingCalendar() {
    assertThrows(IllegalArgumentException.class, () -> resolver.resolve("NONEXISTENT"));
  }
}
