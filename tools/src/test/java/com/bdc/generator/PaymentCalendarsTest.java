package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import com.bdc.stream.LazyDateStream;
import com.bdc.stream.OutsideCoverageException;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageQuality;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PaymentCalendarsTest {
  private static Map<String, ResolvedSpec> payments;

  @BeforeAll
  static void loadCalendars() throws Exception {
    var registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    registry.assertNoLoadErrors();
    var resolver = new SpecResolver(registry);
    payments =
        List.of("EU-TARGET", "US-FEDWIRE", "GB-CHAPS").stream()
            .collect(Collectors.toMap(id -> id, resolver::resolve));
  }

  @Test
  void metadataDeclaresBoundedDateOnlyPaymentCoverage() {
    for (ResolvedSpec payment : payments.values()) {
      assertEquals("payment", payment.metadata().kind());
      assertNull(payment.metadata().mic());
      assertEquals(LocalDate.of(2026, 1, 1), payment.coverage().from());
      assertEquals(LocalDate.of(2027, 12, 31), payment.coverage().to());
      assertEquals(LocalDate.of(2027, 12, 31), payment.coverage().verifiedThrough());
      assertEquals(3, payment.coverage().quality().size());
      assertQuality(payment, CompletenessScope.SCHEDULED_CLOSURES, CoverageQuality.VERIFIED);
      assertQuality(payment, CompletenessScope.EARLY_CLOSES, CoverageQuality.PROJECTED);
      assertQuality(payment, CompletenessScope.UNSCHEDULED_EXCEPTIONS, CoverageQuality.PROJECTED);
    }
  }

  @Test
  void projectedOrdinaryDayBaselineIsQueryableAndBoundsAreEnforced() {
    for (ResolvedSpec payment : payments.values()) {
      var stream = new LazyDateStream(payment);
      assertTrue(stream.isBusinessDay(LocalDate.of(2026, 7, 2)), payment.id());
      assertFalse(stream.isBusinessDay(LocalDate.of(2026, 7, 4)), payment.id());
      assertFalse(stream.isEarlyClose(LocalDate.of(2026, 7, 2)), payment.id());
      assertEquals(EventStatus.PROJECTED, stream.status(LocalDate.of(2026, 7, 2)), payment.id());
      assertEquals(
          LocalDate.of(2026, 7, 2), stream.nextBusinessDay(LocalDate.of(2026, 7, 1)), payment.id());
      assertEquals(
          EventStatus.PROJECTED,
          stream.status(stream.nextBusinessDay(LocalDate.of(2026, 7, 1))),
          payment.id());
      assertThrows(
          OutsideCoverageException.class,
          () -> stream.isBusinessDay(LocalDate.of(2028, 1, 3)),
          payment.id());
      assertThrows(
          OutsideCoverageException.class,
          () -> stream.nextBusinessDay(LocalDate.of(2027, 12, 31)),
          payment.id());
    }
  }

  @Test
  void targetUsesEuroRtgsClosingDaysWithoutWeekendSubstitutes() {
    assertEquals(
        dates("2026-01-01", "2026-04-03", "2026-04-06", "2026-05-01", "2026-12-25"),
        closedDates("EU-TARGET", 2026));
    assertEquals(dates("2027-01-01", "2027-03-26", "2027-03-29"), closedDates("EU-TARGET", 2027));
    assertTrue(eventsOn("EU-TARGET", "2027-05-03").isEmpty());
  }

  @Test
  void fedwireImplementsPublishedSaturdayAndSundayRules() {
    assertEquals(
        dates(
            "2026-01-01",
            "2026-01-19",
            "2026-02-16",
            "2026-05-25",
            "2026-06-19",
            "2026-09-07",
            "2026-10-12",
            "2026-11-11",
            "2026-11-26",
            "2026-12-25"),
        closedDates("US-FEDWIRE", 2026));
    assertEquals(
        dates(
            "2027-01-01",
            "2027-01-18",
            "2027-02-15",
            "2027-05-31",
            "2027-07-05",
            "2027-09-06",
            "2027-10-11",
            "2027-11-11",
            "2027-11-25"),
        closedDates("US-FEDWIRE", 2027));
    assertTrue(eventsOn("US-FEDWIRE", "2026-07-03").isEmpty());
    assertTrue(eventsOn("US-FEDWIRE", "2027-06-18").isEmpty());

    List<Event> observed = eventsOn("US-FEDWIRE", "2027-07-05");
    assertEquals(1, observed.size());
    assertEquals("fedwire_independence_day", observed.getFirst().key());
    assertEquals(LocalDate.of(2027, 7, 4), observed.getFirst().observedFrom());
  }

  @Test
  void chapsUsesEveryPublishedEnglandAndWalesHoliday() {
    assertEquals(
        dates(
            "2026-01-01",
            "2026-04-03",
            "2026-04-06",
            "2026-05-04",
            "2026-05-25",
            "2026-08-31",
            "2026-12-25",
            "2026-12-28"),
        closedDates("GB-CHAPS", 2026));
    assertEquals(
        dates(
            "2027-01-01",
            "2027-03-26",
            "2027-03-29",
            "2027-05-03",
            "2027-05-31",
            "2027-08-30",
            "2027-12-27",
            "2027-12-28"),
        closedDates("GB-CHAPS", 2027));
  }

  @Test
  void allPaymentEventsAreConfirmedClosuresOrWeekendsAndRangesAreConsistent() {
    var generator = new EventGenerator();
    LocalDate narrowFrom = LocalDate.of(2026, 12, 20);
    LocalDate narrowTo = LocalDate.of(2027, 1, 5);
    for (ResolvedSpec payment : payments.values()) {
      List<Event> wide =
          generator.generate(payment, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31));
      assertFalse(wide.isEmpty());
      assertTrue(
          wide.stream()
              .allMatch(
                  event ->
                      event.status() == EventStatus.CONFIRMED
                          && (event.type() == EventType.CLOSED
                              || event.type() == EventType.WEEKEND)));
      assertEquals(
          wide.stream()
              .filter(
                  event -> !event.date().isBefore(narrowFrom) && !event.date().isAfter(narrowTo))
              .toList(),
          generator.generate(payment, narrowFrom, narrowTo),
          payment.id());
    }
  }

  private static void assertQuality(
      ResolvedSpec payment, CompletenessScope scope, CoverageQuality quality) {
    var interval =
        payment.coverage().quality().stream()
            .filter(value -> value.scope() == scope)
            .findFirst()
            .orElseThrow();
    assertEquals(LocalDate.of(2026, 1, 1), interval.from());
    assertEquals(LocalDate.of(2027, 12, 31), interval.to());
    assertEquals(quality, interval.quality());
  }

  private static List<LocalDate> closedDates(String id, int year) {
    return new EventGenerator()
        .generate(payments.get(id), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
            .filter(event -> event.type() == EventType.CLOSED)
            .map(Event::date)
            .toList();
  }

  private static List<Event> eventsOn(String id, String date) {
    LocalDate parsed = LocalDate.parse(date);
    return new EventGenerator().generate(payments.get(id), parsed, parsed);
  }

  private static List<LocalDate> dates(String... values) {
    return java.util.Arrays.stream(values).map(LocalDate::parse).toList();
  }
}
