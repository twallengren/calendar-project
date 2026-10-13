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
  void tadawul2020EidAlAdhaComesFromTheExchangesOwnNotice() {
    DateStream stream = new LazyDateStream(resolver.resolve("SA-TADAWUL"));
    LocalDate date = LocalDate.of(2020, 7, 30);

    var assessment = stream.assessment(date);
    assertEquals(DayState.CLOSED, assessment.state());
    assertEquals(
        CoverageQuality.VERIFIED,
        assessment.completeness().get(CompletenessScope.SCHEDULED_CLOSURES));
    assertFalse(stream.isBusinessDay(date));
    assertTrue(assessment.evidenceIds().contains("tadawul-eid-al-adha-2020-notice"));
    assertTrue(stream.isBusinessDay(LocalDate.of(2020, 7, 28)), "last trading day before Eid");
    assertTrue(
        stream.isBusinessDay(LocalDate.of(2020, 8, 5)), "trading resumed Wednesday 5 August");
    assertFalse(stream.isBusinessDay(LocalDate.of(2020, 9, 23)), "National Day");
  }

  @Test
  void hkexSevereWeatherEraIsEnumeratedAndVerified() {
    DateStream stream = new LazyDateStream(resolver.resolve("HK-HKEX"));
    LocalDate closure = LocalDate.of(2020, 10, 13);

    var assessment = stream.assessment(closure);
    assertEquals(DayState.CLOSED, assessment.state());
    assertEquals(
        CoverageQuality.VERIFIED,
        assessment.completeness().get(com.bdc.trust.CompletenessScope.UNSCHEDULED_EXCEPTIONS));
    assertEquals(EventStatus.CONFIRMED, assessment.businessDayConfidence());
    assertFalse(stream.isBusinessDay(closure));
    assertTrue(assessment.evidenceIds().contains("hkex-no-trading-2020-10-13"));

    // The two September 2023 cancellations third-party libraries miss.
    assertFalse(stream.isBusinessDay(LocalDate.of(2023, 9, 1)));
    assertFalse(stream.isBusinessDay(LocalDate.of(2023, 9, 8)));
    // A morning-only cancellation is an ordinary business day with a regular close.
    assertTrue(stream.isBusinessDay(LocalDate.of(2023, 10, 9)));
    assertFalse(stream.isEarlyClose(LocalDate.of(2023, 10, 9)));
    // A Signal No. 8 issued at 13:40 terminated trading at 13:55.
    assertEquals(
        java.util.Optional.of(java.time.LocalTime.of(13, 55)),
        stream.closeTime(LocalDate.of(2022, 11, 2)));
    assertTrue(stream.isBusinessDay(LocalDate.of(2022, 11, 2)));

    var afterPolicyChange = stream.assessment(LocalDate.of(2024, 9, 23));
    assertNotEquals(DayState.UNKNOWN, afterPolicyChange.state());
    assertEquals(EventStatus.PROJECTED, afterPolicyChange.effectiveConfidence());
  }

  @Test
  void additionalAuditedEarlyCloseGapsAreExplicitButDoNotBlockBusinessDayAnswers() {
    DateStream tsx = new LazyDateStream(resolver.resolve("CA-TSX"));
    LocalDate tsxGap = LocalDate.of(2027, 12, 24);
    assertThrows(UnresolvedDateException.class, () -> tsx.closeTime(tsxGap));
    assertEquals(DayState.UNKNOWN, tsx.assessment(tsxGap).state());
    assertTrue(tsx.isBusinessDay(tsxGap), "an early-close gap cannot make a weekday unknown");
    assertEquals(DayState.OPEN, tsx.assessment(tsxGap).businessDayState());
    assertEquals(
        java.util.Optional.of(java.time.LocalTime.of(13, 0)),
        tsx.closeTime(LocalDate.of(2012, 12, 24)),
        "TMX's 2012 accounting carries the Christmas Eve half day");
    assertNotEquals(DayState.UNKNOWN, tsx.assessment(LocalDate.of(2025, 12, 24)).state());
    // The 2001 and 2008 exceptions are the exchange's own releases.
    assertFalse(tsx.isBusinessDay(LocalDate.of(2001, 9, 12)));
    assertFalse(tsx.isBusinessDay(LocalDate.of(2008, 12, 17)));
    assertTrue(tsx.isBusinessDay(LocalDate.of(2001, 9, 11)));
    assertEquals(
        java.util.Optional.of(java.time.LocalTime.of(13, 39)),
        tsx.closeTime(LocalDate.of(2018, 4, 27)));
    assertEquals(
        EventStatus.PROJECTED, tsx.assessment(LocalDate.of(2001, 9, 11)).businessDayConfidence());
    assertEquals(
        EventStatus.CONFIRMED, tsx.assessment(LocalDate.of(2020, 2, 27)).businessDayConfidence());

    DateStream xetra = new LazyDateStream(resolver.resolve("DE-XETRA"));
    LocalDate xetraGap = LocalDate.of(2026, 12, 30);
    assertThrows(UnresolvedDateException.class, () -> xetra.closeTime(xetraGap));
    assertTrue(xetra.isBusinessDay(xetraGap));
    assertFalse(xetra.isBusinessDay(LocalDate.of(2026, 12, 24)));
    assertEquals(EventStatus.UNKNOWN, xetra.status(xetraGap));
    assertEquals(EventStatus.PROJECTED, xetra.assessment(xetraGap).businessDayConfidence());

    DateStream nyse = new LazyDateStream(resolver.resolve("US-NYSE"));
    LocalDate nyseGap = LocalDate.of(1973, 12, 24);
    assertThrows(UnresolvedDateException.class, () -> nyse.closeTime(nyseGap));
    assertTrue(nyse.isBusinessDay(nyseGap));
    assertTrue(nyse.isEarlyClose(LocalDate.of(1975, 12, 24)));
    assertEquals(
        LocalDate.of(1975, 12, 24), nyse.eventsOn(LocalDate.of(1975, 12, 24)).get(0).date());
  }

  @Test
  void sourcePrecisionAndPublicationGapsRemainUnknown() {
    DateStream euronext = new LazyDateStream(resolver.resolve("FR-EURONEXT-PARIS"));
    assertThrows(
        UnresolvedDateException.class, () -> euronext.closeTime(LocalDate.of(2010, 12, 24)));
    assertTrue(euronext.isBusinessDay(LocalDate.of(2010, 12, 24)));
    assertEquals(
        CoverageQuality.VERIFIED,
        euronext
            .assessment(LocalDate.of(2014, 12, 24))
            .completeness()
            .get(CompletenessScope.EARLY_CLOSES));

    DateStream lse = new LazyDateStream(resolver.resolve("GB-LSE"));
    assertThrows(UnresolvedDateException.class, () -> lse.closeTime(LocalDate.of(2005, 12, 23)));
    assertTrue(lse.isBusinessDay(LocalDate.of(2005, 12, 23)));
    assertEquals(
        CoverageQuality.VERIFIED,
        lse.assessment(LocalDate.of(2022, 12, 23))
            .completeness()
            .get(CompletenessScope.EARLY_CLOSES));

    DateStream jpx = new LazyDateStream(resolver.resolve("JP-JPX"));
    assertEquals(
        CoverageQuality.VERIFIED,
        jpx.assessment(LocalDate.of(2018, 1, 4))
            .completeness()
            .get(CompletenessScope.SCHEDULED_CLOSURES),
        "JPX's own monthly counts verify the schedule from 2010");
    assertEquals(
        CoverageQuality.PROJECTED,
        jpx.assessment(LocalDate.of(2028, 1, 4))
            .completeness()
            .get(CompletenessScope.SCHEDULED_CLOSURES));
    assertEquals(
        CoverageQuality.VERIFIED,
        jpx.assessment(LocalDate.of(2019, 1, 4))
            .completeness()
            .get(CompletenessScope.SCHEDULED_CLOSURES));
  }
}
