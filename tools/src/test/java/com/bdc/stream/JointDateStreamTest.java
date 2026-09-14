package com.bdc.stream;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.DateRange;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.resolver.SpecResolver;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Joint (multi-calendar) query semantics: open only where every member is open. */
class JointDateStreamTest {

  private static DateStream nyse;
  private static DateStream tadawul;
  private static DateStream joint;

  // A Thursday/Friday/Saturday/Sunday boundary: Tadawul rests Fri-Sat, the NYSE Sat-Sun.
  private static final LocalDate THURSDAY = LocalDate.of(2026, 2, 26);
  private static final LocalDate FRIDAY = LocalDate.of(2026, 2, 27);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 2, 28);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 3, 1);
  private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

  @BeforeAll
  static void setUp() throws Exception {
    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    SpecResolver resolver = new SpecResolver(registry);
    nyse = new LazyDateStream(resolver.resolve("US-NYSE"));
    tadawul = new LazyDateStream(resolver.resolve("SA-TADAWUL"));
    joint = JointDateStream.joint(List.of(nyse, tadawul));
  }

  @Test
  void jointOfOneStreamIsThatStream() {
    assertSame(nyse, JointDateStream.joint(List.of(nyse)));
  }

  @Test
  void emptyMemberListRejected() {
    assertThrows(IllegalArgumentException.class, () -> JointDateStream.joint(List.of()));
  }

  @Test
  void calendarIdJoinsMemberIdsWithPlus() {
    assertEquals("US-NYSE+SA-TADAWUL", joint.calendarId());
  }

  @Test
  void businessDayOnlyWhereEveryMemberIsOpen() {
    assertTrue(nyse.isBusinessDay(THURSDAY) && tadawul.isBusinessDay(THURSDAY));
    assertTrue(joint.isBusinessDay(THURSDAY));

    // Friday: NYSE trades, Tadawul rests
    assertTrue(nyse.isBusinessDay(FRIDAY));
    assertFalse(tadawul.isBusinessDay(FRIDAY));
    assertFalse(joint.isBusinessDay(FRIDAY));

    // Saturday: both rest
    assertFalse(joint.isBusinessDay(SATURDAY));

    // Sunday: Tadawul trades, NYSE rests
    assertFalse(nyse.isBusinessDay(SUNDAY));
    assertTrue(tadawul.isBusinessDay(SUNDAY));
    assertFalse(joint.isBusinessDay(SUNDAY));

    assertTrue(joint.isBusinessDay(MONDAY));
  }

  @Test
  void nextBusinessDayFromThursdaySkipsTheWholeSplitWeekend() {
    assertEquals(MONDAY, joint.nextBusinessDay(THURSDAY));
    assertEquals(MONDAY, joint.nthBusinessDay(THURSDAY, 1));
    assertEquals(THURSDAY, joint.prevBusinessDay(MONDAY));
  }

  @Test
  void nthBusinessDayCountsOnlyJointlyOpenDays() {
    // Wednesday + 2 joint business days = Thursday, then Monday
    LocalDate wednesday = LocalDate.of(2026, 2, 25);
    assertEquals(MONDAY, joint.nthBusinessDay(wednesday, 2));
    // inclusive of both endpoints: Wed, Thu, Mon
    assertEquals(3, joint.businessDaysInRange(wednesday, MONDAY));
  }

  @Test
  void eventsOnPrefixSourceModuleWithMemberCalendarId() {
    List<Event> events = joint.eventsOn(LocalDate.of(2026, 12, 25));
    assertTrue(
        events.stream().anyMatch(e -> "US-NYSE/module:christmas".equals(e.sourceModule())),
        events.toString());
    assertTrue(
        events.stream()
            .allMatch(
                e ->
                    e.sourceModule().startsWith("US-NYSE/")
                        || e.sourceModule().startsWith("SA-TADAWUL/")),
        events.toString());
  }

  @Test
  void rangeIsTheIntersectionOfMemberRanges() {
    // US-NYSE covers 1900-2030, SA-TADAWUL 2020-2030
    assertEquals(LocalDate.of(2020, 1, 1), joint.range().start());
    assertEquals(LocalDate.of(2030, 12, 31), joint.range().end());
    assertThrows(
        OutsideCoverageException.class, () -> joint.isBusinessDay(LocalDate.of(2019, 6, 3)));
    // the member itself still answers for its own wider range
    assertTrue(nyse.isBusinessDay(LocalDate.of(2019, 6, 3)));
  }

  @Test
  void nonOverlappingMembersRejected() {
    DateStream a =
        new CsvDateStream(
            "A", List.of(), new DateRange(LocalDate.of(2000, 1, 1), LocalDate.of(2005, 12, 31)));
    DateStream b =
        new CsvDateStream(
            "B", List.of(), new DateRange(LocalDate.of(2010, 1, 1), LocalDate.of(2015, 12, 31)));
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> JointDateStream.joint(List.of(a, b)));
    assertTrue(e.getMessage().contains("no overlapping covered range"), e.getMessage());
  }

  @Test
  void verifiedThroughIsTheEarliestMemberValue() {
    // US-NYSE is verified through 2026-12-31, SA-TADAWUL through 2029-12-31
    assertEquals(LocalDate.of(2026, 12, 31), joint.verifiedThrough().orElseThrow());
  }

  @Test
  void statusIsTheWorstMemberStatus() {
    assertEquals(EventStatus.CONFIRMED, joint.status(LocalDate.of(2024, 6, 3)));
    // past the NYSE verified_through but inside both ranges
    assertEquals(EventStatus.PROJECTED, joint.status(LocalDate.of(2028, 6, 1)));
    assertEquals(EventStatus.CONFIRMED, tadawul.status(LocalDate.of(2028, 6, 1)));
    // outside SA-TADAWUL's coverage
    assertEquals(EventStatus.UNKNOWN, joint.status(LocalDate.of(2019, 6, 3)));
    assertEquals(EventStatus.CONFIRMED, nyse.status(LocalDate.of(2019, 6, 3)));
  }

  @Test
  void closeTimeIsTheEarliestMemberEarlyClose() {
    DateStream early =
        new CsvDateStream(
            "EARLY",
            List.of(
                new Event(
                    LocalDate.of(2026, 6, 1),
                    EventType.EARLY_CLOSE,
                    "Half day",
                    "test",
                    "half_day",
                    "module:half_day",
                    null,
                    LocalTime.of(13, 0),
                    EventStatus.CONFIRMED)),
            new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    DateStream earlier =
        new CsvDateStream(
            "EARLIER",
            List.of(
                new Event(
                    LocalDate.of(2026, 6, 1),
                    EventType.EARLY_CLOSE,
                    "Shorter half day",
                    "test",
                    "half_day",
                    "module:half_day",
                    null,
                    LocalTime.of(11, 30),
                    EventStatus.CONFIRMED)),
            new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    DateStream both = JointDateStream.joint(List.of(early, earlier));
    assertTrue(both.isEarlyClose(LocalDate.of(2026, 6, 1)));
    assertEquals(LocalTime.of(11, 30), both.closeTime(LocalDate.of(2026, 6, 1)).orElseThrow());
    assertFalse(both.isEarlyClose(LocalDate.of(2026, 6, 2)));
  }
}
