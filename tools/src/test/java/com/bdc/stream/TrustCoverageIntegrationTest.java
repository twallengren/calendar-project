package com.bdc.stream;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.EventStatus;
import com.bdc.resolver.SpecResolver;
import com.bdc.trust.CoverageQuality;
import com.bdc.trust.DayState;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TrustCoverageIntegrationTest {

  private static SpecResolver resolver;

  @BeforeAll
  static void loadSpecs() throws Exception {
    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    registry.assertNoLoadErrors();
    resolver = new SpecResolver(registry);
  }

  @Test
  void tadawul2020IsUnknownWherePrimaryHolidayEvidenceIsMissing() {
    DateStream stream = new LazyDateStream(resolver.resolve("SA-TADAWUL"));
    LocalDate date = LocalDate.of(2020, 7, 30);

    var assessment = stream.assessment(date);
    assertEquals(DayState.UNKNOWN, assessment.state());
    assertEquals(DayState.OPEN, assessment.scheduledState());
    assertEquals(EventStatus.UNKNOWN, assessment.effectiveConfidence());
    assertThrows(UnresolvedDateException.class, () -> stream.isBusinessDay(date));
  }

  @Test
  void hkexWeatherEraIsUnknownWhileScheduledStateRemainsVisible() {
    DateStream stream = new LazyDateStream(resolver.resolve("HK-HKEX"));
    LocalDate closure = LocalDate.of(2020, 10, 13);

    var assessment = stream.assessment(closure);
    assertEquals(DayState.UNKNOWN, assessment.state());
    assertEquals(DayState.OPEN, assessment.scheduledState());
    assertEquals(
        CoverageQuality.INCOMPLETE,
        assessment.completeness().get(com.bdc.trust.CompletenessScope.UNSCHEDULED_EXCEPTIONS));
    assertThrows(UnresolvedDateException.class, () -> stream.isBusinessDay(closure));

    var afterPolicyChange = stream.assessment(LocalDate.of(2024, 9, 23));
    assertNotEquals(DayState.UNKNOWN, afterPolicyChange.state());
    assertEquals(EventStatus.PROJECTED, afterPolicyChange.effectiveConfidence());
  }
}
