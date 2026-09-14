package com.bdc.stream;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.CalendarSpec;
import com.bdc.model.EventStatus;
import com.bdc.resolver.SpecResolver;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageQuality;
import com.bdc.trust.DayState;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TrustCoverageIntegrationTest {

  private static SpecRegistry registry;
  private static SpecResolver resolver;

  @BeforeAll
  static void loadSpecs() throws Exception {
    registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    registry.assertNoLoadErrors();
    resolver = new SpecResolver(registry);
  }

  @Test
  void everyAdvertisedDateHasAnExplicitQualityForEveryScope() {
    for (CalendarSpec spec : registry.getAllCalendars().values()) {
      var coverage = spec.metadata().coverage();
      assertFalse(coverage.quality().isEmpty(), spec.id());
      for (CompletenessScope scope : CompletenessScope.values()) {
        for (LocalDate date = coverage.from();
            !date.isAfter(coverage.to());
            date = date.plusDays(1)) {
          LocalDate checked = date;
          assertTrue(
              coverage.quality().stream()
                  .anyMatch(interval -> interval.scope() == scope && interval.contains(checked)),
              () -> spec.id() + " has no " + scope + " quality on " + checked);
        }
      }
    }
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

  @Test
  void additionalAuditedEarlyCloseGapsAreExplicit() {
    DateStream tsx = new LazyDateStream(resolver.resolve("CA-TSX"));
    assertThrows(
        UnresolvedDateException.class, () -> tsx.isBusinessDay(LocalDate.of(2012, 12, 24)));
    assertNotEquals(DayState.UNKNOWN, tsx.assessment(LocalDate.of(2025, 12, 24)).state());

    DateStream xetra = new LazyDateStream(resolver.resolve("DE-XETRA"));
    assertThrows(
        UnresolvedDateException.class, () -> xetra.isBusinessDay(LocalDate.of(2026, 12, 30)));

    DateStream nyse = new LazyDateStream(resolver.resolve("US-NYSE"));
    assertThrows(
        UnresolvedDateException.class, () -> nyse.isBusinessDay(LocalDate.of(1973, 12, 24)));
    assertTrue(nyse.isEarlyClose(LocalDate.of(1975, 12, 24)));
    assertEquals(
        LocalDate.of(1975, 12, 24), nyse.eventsOn(LocalDate.of(1975, 12, 24)).get(0).date());
  }

  @Test
  void sourcePrecisionAndPublicationGapsRemainUnknown() {
    DateStream euronext = new LazyDateStream(resolver.resolve("FR-EURONEXT-PARIS"));
    assertThrows(
        UnresolvedDateException.class, () -> euronext.isBusinessDay(LocalDate.of(2010, 12, 24)));
    assertEquals(
        CoverageQuality.VERIFIED,
        euronext
            .assessment(LocalDate.of(2014, 12, 24))
            .completeness()
            .get(CompletenessScope.EARLY_CLOSES));

    DateStream lse = new LazyDateStream(resolver.resolve("GB-LSE"));
    assertThrows(
        UnresolvedDateException.class, () -> lse.isBusinessDay(LocalDate.of(2005, 12, 23)));
    assertEquals(
        CoverageQuality.VERIFIED,
        lse.assessment(LocalDate.of(2022, 12, 23))
            .completeness()
            .get(CompletenessScope.EARLY_CLOSES));

    DateStream jpx = new LazyDateStream(resolver.resolve("JP-JPX"));
    assertEquals(
        CoverageQuality.PROJECTED,
        jpx.assessment(LocalDate.of(2018, 1, 4))
            .completeness()
            .get(CompletenessScope.SCHEDULED_CLOSURES));
    assertEquals(
        CoverageQuality.VERIFIED,
        jpx.assessment(LocalDate.of(2019, 1, 4))
            .completeness()
            .get(CompletenessScope.SCHEDULED_CLOSURES));
  }
}
