package com.bdc.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.chronology.DateRange;
import com.bdc.emitter.EventsCsvReader;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.stream.CsvDateStream;
import com.bdc.stream.DateStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BusinessCalendarsTest {

  @Test
  void nyseTradesOnNewYearsEve2021() {
    assertTrue(BusinessCalendars.of("US-NYSE").isBusinessDay(LocalDate.of(2021, 12, 31)));
  }

  @Test
  void independenceDayEveClosesEarly() {
    DateStream nyse = BusinessCalendars.of("US-NYSE");
    LocalDate july3 = LocalDate.of(2025, 7, 3);
    assertEquals(Optional.of(LocalTime.of(13, 0)), nyse.closeTime(july3));
    assertTrue(nyse.isEarlyClose(july3));
    assertTrue(nyse.isBusinessDay(july3), "an early close is still a business day");
  }

  @Test
  void datesAfterVerifiedThroughAreProjected() {
    DateStream nyse = BusinessCalendars.of("US-NYSE");
    LocalDate verified = nyse.verifiedThrough().orElseThrow();
    assertEquals(EventStatus.CONFIRMED, nyse.status(verified));
    assertEquals(EventStatus.PROJECTED, nyse.status(verified.plusDays(1)));
  }

  @Test
  void statusOutsideTheRangeIsUnknown() {
    assertEquals(
        EventStatus.UNKNOWN, BusinessCalendars.of("US-NYSE").status(LocalDate.of(2099, 1, 1)));
  }

  @Test
  void jointSettlementSkipsBothWeekends() {
    // Wednesday 2026-02-25 + 2 business days: Thursday counts, Friday is a Tadawul weekend and
    // Saturday/Sunday are an NYSE weekend, so T+2 lands on Monday.
    DateStream joint = BusinessCalendars.joint("US-NYSE", "SA-TADAWUL");
    assertEquals(LocalDate.of(2026, 3, 2), joint.nthBusinessDay(LocalDate.of(2026, 2, 25), 2));
    assertFalse(joint.isBusinessDay(LocalDate.of(2026, 2, 27)), "Friday: Tadawul weekend");
  }

  @Test
  void micAliasesResolve() {
    assertEquals("US-NYSE", BusinessCalendars.of("XNYS").calendarId());
    assertEquals("US-NYSE", BusinessCalendars.of("xnys").calendarId());
    assertEquals("SA-TADAWUL", BusinessCalendars.of("XSAU").calendarId());
    assertEquals("US-NYSE", BusinessCalendars.of("us_nyse").calendarId());
    assertEquals("GB-LSE", BusinessCalendars.of("XLON").calendarId());
    assertEquals("HK-HKEX", BusinessCalendars.of("xhkg").calendarId());
    assertEquals("JP-JPX", BusinessCalendars.of("XTKS").calendarId());
    assertEquals("JP-JPX", BusinessCalendars.of("XJPX").calendarId(), "segment MIC alias");
    assertEquals("US-NYSE", BusinessCalendars.of("NYSE").calendarId());
    assertEquals("SA-TADAWUL", BusinessCalendars.of("TADAWUL").calendarId());
  }

  @Test
  void unknownCalendarNamesTheOnesThatExist() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> BusinessCalendars.of("XX-NOPE"));
    assertTrue(thrown.getMessage().contains("XX-NOPE"), thrown.getMessage());
    assertTrue(thrown.getMessage().contains("US-NYSE"), thrown.getMessage());
  }

  @Test
  void availableCalendarsAndDataVersionComeFromTheManifest() {
    assertTrue(BusinessCalendars.available().contains("US-NYSE"));
    assertFalse(
        BusinessCalendars.available().contains("US-MARKET-BASE"),
        "base calendars are not bundled by default");
    assertTrue(
        BusinessCalendars.dataVersion().matches("\\d+\\.\\d+\\.\\d+"),
        BusinessCalendars.dataVersion());
  }

  /**
   * The facade must answer exactly as the published artifact does.
   *
   * <p>The bundled data drops the WEEKEND rows and rebuilds them from the weekend policy; this
   * replays every date of 2000-2030 for every bundled calendar against a stream built from the full
   * {@code blessed/<ID>/events.csv}, which is the only proof that the reconstruction is lossless.
   */
  @Test
  void everyCalendarAnswersAsThePublishedArtifact() throws IOException {
    Path blessed = Path.of(System.getProperty("bdc.blessedDir", "blessed"));
    assertTrue(Files.isDirectory(blessed), "blessed dir not found at " + blessed.toAbsolutePath());

    int calendarsChecked = 0;
    long datesChecked = 0;
    for (String calendarId : BusinessCalendars.available()) {
      DateStream bundled = BusinessCalendars.of(calendarId);
      DateStream published = publishedStream(blessed, calendarId);

      LocalDate from = max(bundled.range().start(), LocalDate.of(2000, 1, 1));
      LocalDate to = min(bundled.range().end(), LocalDate.of(2030, 12, 31));
      assertEquals(published.range(), bundled.range(), calendarId + ": range");
      assertEquals(
          published.verifiedThrough(), bundled.verifiedThrough(), calendarId + ": verifiedThrough");

      for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
        String where = calendarId + " on " + date;
        assertEquals(published.isBusinessDay(date), bundled.isBusinessDay(date), where);
        assertEquals(published.isEarlyClose(date), bundled.isEarlyClose(date), where);
        assertEquals(published.closeTime(date), bundled.closeTime(date), where);
        assertEquals(published.status(date), bundled.status(date), where);
        assertEquals(
            published.eventsOn(date).size(),
            bundled.eventsOn(date).size(),
            where + ": event count");
        datesChecked++;
      }
      calendarsChecked++;
    }
    assertTrue(calendarsChecked >= 10, "expected the bundled markets, got " + calendarsChecked);
    assertTrue(datesChecked > 80_000, "expected a full replay, got " + datesChecked + " dates");
  }

  private static DateStream publishedStream(Path blessed, String calendarId) throws IOException {
    Map<String, Object> metadata =
        Json.parseObject(Files.readString(blessed.resolve(calendarId).resolve("metadata.json")));
    List<Event> events =
        new ArrayList<>(
            new EventsCsvReader().read(blessed.resolve(calendarId).resolve("events.csv")));
    DateRange range =
        new DateRange(
            LocalDate.parse(String.valueOf(metadata.get("range_start"))),
            LocalDate.parse(String.valueOf(metadata.get("range_end"))));
    LocalDate verifiedThrough = null;
    if (metadata.get("coverage") instanceof Map<?, ?> coverage
        && coverage.get("verified_through") != null) {
      verifiedThrough = LocalDate.parse(String.valueOf(coverage.get("verified_through")));
    }
    return new CsvDateStream(calendarId, events, range, verifiedThrough);
  }

  private static LocalDate max(LocalDate a, LocalDate b) {
    return a.isAfter(b) ? a : b;
  }

  private static LocalDate min(LocalDate a, LocalDate b) {
    return a.isBefore(b) ? a : b;
  }
}
