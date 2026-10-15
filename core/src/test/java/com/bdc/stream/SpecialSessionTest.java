package com.bdc.stream;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.DateRange;
import com.bdc.emitter.EventsCsvReader;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageInterval;
import com.bdc.trust.CoverageQuality;
import com.bdc.trust.DayState;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpecialSessionTest {

  private static final LocalDate HOLIDAY = LocalDate.of(2024, 11, 1); // Friday
  private static final LocalDate SATURDAY = LocalDate.of(2024, 3, 2);
  private static final LocalDate MONDAY = LocalDate.of(2024, 3, 4);
  private static final DateRange RANGE =
      new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
  private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

  private static Event session(LocalDate date, String key, String open, String close) {
    return new Event(
        date,
        EventType.SPECIAL_SESSION,
        key,
        "fixture",
        key,
        "module:fixture",
        null,
        LocalTime.parse(close),
        EventStatus.CONFIRMED,
        LocalTime.parse(open));
  }

  private static Event row(LocalDate date, EventType type, String description) {
    return new Event(date, type, description, "fixture");
  }

  private static CsvDateStream stream(List<CoverageInterval> intervals, Event... events) {
    return new CsvDateStream("CAL", List.of(events), RANGE, null, intervals, null, KOLKATA);
  }

  private static CsvDateStream stream(Event... events) {
    return stream(List.of(), events);
  }

  @Test
  void aSessionOnAHolidayMakesATradingDayButNotABusinessDay() {
    CsvDateStream calendar =
        stream(
            row(HOLIDAY, EventType.CLOSED, "Festival"),
            session(HOLIDAY, "evening", "18:00", "19:00"));

    assertFalse(calendar.isBusinessDay(HOLIDAY));
    assertTrue(calendar.isTradingDay(HOLIDAY));
    assertEquals(
        List.of(
            new SpecialSession(
                "CAL",
                KOLKATA,
                LocalTime.of(18, 0),
                LocalTime.of(19, 0),
                "evening",
                "evening",
                EventStatus.CONFIRMED)),
        calendar.specialSessions(HOLIDAY));
    // Date arithmetic never lands on it (the fixture has no weekend rows, so Saturday is next),
    // and its close is not an early close.
    assertEquals(HOLIDAY.plusDays(1), calendar.nextBusinessDay(HOLIDAY.minusDays(1)));
    assertTrue(calendar.closeTime(HOLIDAY).isEmpty());
    assertFalse(calendar.isEarlyClose(HOLIDAY));
    assertEquals(DayState.CLOSED, calendar.assessment(HOLIDAY).scheduledState());
  }

  @Test
  void aSessionOnAWeekendDayIsATradingDay() {
    CsvDateStream calendar =
        stream(
            row(SATURDAY, EventType.WEEKEND, "Saturday"),
            row(SATURDAY.plusDays(1), EventType.WEEKEND, "Sunday"),
            session(SATURDAY, "drill", "09:15", "12:30"));

    assertFalse(calendar.isBusinessDay(SATURDAY));
    assertTrue(calendar.isTradingDay(SATURDAY));
    assertFalse(calendar.isTradingDay(SATURDAY.plusDays(1)));
    assertEquals(1, calendar.specialSessions(SATURDAY).size());
  }

  @Test
  void ordinaryDaysAnswerAsBefore() {
    CsvDateStream calendar = stream(row(SATURDAY, EventType.WEEKEND, "Saturday"));
    assertTrue(calendar.isTradingDay(MONDAY));
    assertFalse(calendar.isTradingDay(SATURDAY));
    assertEquals(List.of(), calendar.specialSessions(MONDAY));
  }

  @Test
  void sessionsAreOrderedByOpenTime() {
    CsvDateStream calendar =
        stream(
            row(SATURDAY, EventType.WEEKEND, "Saturday"),
            session(SATURDAY, "second", "11:30", "12:30"),
            session(SATURDAY, "first", "09:15", "10:00"));
    assertEquals(
        List.of("first", "second"),
        calendar.specialSessions(SATURDAY).stream().map(SpecialSession::key).toList());
  }

  @Test
  void tradingDayNeedsTheBusinessDayScopesAndSessionsNeedAllScopes() {
    List<CoverageInterval> intervals =
        List.of(
            new CoverageInterval(
                CompletenessScope.SCHEDULED_CLOSURES,
                RANGE.start(),
                RANGE.end(),
                CoverageQuality.VERIFIED,
                List.of("list")),
            new CoverageInterval(
                CompletenessScope.UNSCHEDULED_EXCEPTIONS,
                RANGE.start(),
                RANGE.end(),
                CoverageQuality.PROJECTED,
                List.of()),
            new CoverageInterval(
                CompletenessScope.EARLY_CLOSES,
                RANGE.start(),
                RANGE.end(),
                CoverageQuality.INCOMPLETE,
                List.of()));
    CsvDateStream calendar =
        stream(
            intervals,
            row(HOLIDAY, EventType.CLOSED, "Festival"),
            session(HOLIDAY, "evening", "18:00", "19:00"));

    assertTrue(calendar.isTradingDay(HOLIDAY));
    UnresolvedDateException error =
        assertThrows(UnresolvedDateException.class, () -> calendar.specialSessions(HOLIDAY));
    assertEquals(java.util.Set.of(CompletenessScope.EARLY_CLOSES), error.incompleteScopes());
  }

  @Test
  void outsideCoverageThrows() {
    CsvDateStream calendar = stream();
    assertThrows(
        OutsideCoverageException.class, () -> calendar.isTradingDay(LocalDate.of(2025, 1, 1)));
    assertThrows(
        OutsideCoverageException.class, () -> calendar.specialSessions(LocalDate.of(2025, 1, 1)));
  }

  @Test
  void aJointTradingDayNeedsEveryMemberToTrade() {
    CsvDateStream india =
        stream(
            row(HOLIDAY, EventType.CLOSED, "Festival"),
            session(HOLIDAY, "evening", "18:00", "19:00"));
    CsvDateStream other = new CsvDateStream("OTHER", List.of(), RANGE, null, List.of(), null, null);
    CsvDateStream closedOther =
        new CsvDateStream(
            "SHUT",
            List.of(row(HOLIDAY, EventType.CLOSED, "Shut")),
            RANGE,
            null,
            List.of(),
            null,
            null);

    assertTrue(JointDateStream.joint(india, other).isTradingDay(HOLIDAY));
    assertFalse(JointDateStream.joint(india, closedOther).isTradingDay(HOLIDAY));
    List<SpecialSession> sessions = JointDateStream.joint(india, other).specialSessions(HOLIDAY);
    assertEquals(1, sessions.size());
    assertEquals("CAL", sessions.get(0).calendarId());
    assertEquals(KOLKATA, sessions.get(0).timezone());
  }

  @Test
  void theEventRecordRequiresSessionTimes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Event(HOLIDAY, EventType.SPECIAL_SESSION, "No times", "fixture"));
    assertThrows(IllegalArgumentException.class, () -> session(HOLIDAY, "x", "19:00", "18:00"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Event(
                HOLIDAY,
                EventType.CLOSED,
                "Closed",
                "fixture",
                "k",
                null,
                null,
                null,
                null,
                LocalTime.NOON));
  }

  @Test
  void theCsvReaderReadsOpenTimeByHeaderName() {
    List<Event> events =
        new EventsCsvReader()
            .parse(
                List.of(
                    "date,type,description,key,source_module,observed_from,close_time,status,open_time",
                    "2024-11-01,CLOSED,Festival,festival,m,,,CONFIRMED,",
                    "2024-11-01,SPECIAL_SESSION,Evening,evening,m,,19:00,CONFIRMED,18:00"),
                "test");
    assertNull(events.get(0).openTime());
    assertEquals(LocalTime.of(18, 0), events.get(1).openTime());
    assertEquals(LocalTime.of(19, 0), events.get(1).closeTime());
  }
}
