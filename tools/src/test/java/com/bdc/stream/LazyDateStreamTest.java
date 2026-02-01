package com.bdc.stream;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LazyDateStreamTest {

  private static SpecRegistry registry;
  private static SpecResolver resolver;
  private LazyDateStream stream;

  @BeforeAll
  static void setupRegistry() throws Exception {
    registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(
        Path.of("tools/src/test/resources/test-calendars/calendars"));
    registry.loadModulesFromDirectory(Path.of("tools/src/test/resources/test-calendars/modules"));
    resolver = new SpecResolver(registry);
  }

  @BeforeEach
  void setUp() {
    ResolvedSpec spec = resolver.resolve("SIMPLE");
    stream = new LazyDateStream(spec);
  }

  @Test
  void calendarId_returnsCorrectId() {
    assertEquals("SIMPLE", stream.calendarId());
  }

  @Test
  void eventsInRange_validRange_returnsEvents() {
    // SIMPLE calendar has test_holiday on June 15
    LocalDate from = LocalDate.of(2025, 6, 1);
    LocalDate to = LocalDate.of(2025, 6, 30);

    List<Event> events = stream.eventsInRange(from, to);

    assertNotNull(events);
    assertTrue(events.stream().anyMatch(e -> e.date().equals(LocalDate.of(2025, 6, 15))));
  }

  @Test
  void eventsInRange_emptyRange_returnsEmpty() {
    // Single day with no event
    LocalDate date = LocalDate.of(2025, 3, 3); // A random weekday

    List<Event> events = stream.eventsInRange(date, date);

    assertTrue(events.isEmpty());
  }

  @Test
  void eventsInRange_invertedRange_throwsException() {
    LocalDate from = LocalDate.of(2025, 6, 30);
    LocalDate to = LocalDate.of(2025, 6, 1);

    assertThrows(IllegalArgumentException.class, () -> stream.eventsInRange(from, to));
  }

  @Test
  void eventOn_dateWithEvent_returnsFirstEvent() {
    // June 15 has the test_holiday
    LocalDate date = LocalDate.of(2025, 6, 15);

    Optional<Event> event = stream.eventOn(date);

    assertTrue(event.isPresent());
    assertEquals(date, event.get().date());
  }

  @Test
  void eventOn_dateWithoutEvent_returnsEmpty() {
    LocalDate date = LocalDate.of(2025, 3, 5); // Random weekday with no event

    Optional<Event> event = stream.eventOn(date);

    assertTrue(event.isEmpty());
  }

  @Test
  void eventsOn_dateWithEvent_returnsAll() {
    LocalDate date = LocalDate.of(2025, 6, 15);

    List<Event> events = stream.eventsOn(date);

    assertFalse(events.isEmpty());
    assertTrue(events.stream().allMatch(e -> e.date().equals(date)));
  }

  @Test
  void isBusinessDay_weekday_returnsTrue() {
    // Wednesday, March 5, 2025 - should be a business day
    LocalDate wednesday = LocalDate.of(2025, 3, 5);

    assertTrue(stream.isBusinessDay(wednesday));
  }

  @Test
  void isBusinessDay_weekend_returnsFalse() {
    // Saturday
    LocalDate saturday = LocalDate.of(2025, 3, 1);
    assertEquals(DayOfWeek.SATURDAY, saturday.getDayOfWeek());

    assertFalse(stream.isBusinessDay(saturday));
  }

  @Test
  void isBusinessDay_holiday_returnsFalse() {
    // June 15, 2025 is the test_holiday - it's a Sunday in 2025, but in 2024 it's a Saturday
    // Let's use 2026 where June 15 is a Monday
    LocalDate holiday = LocalDate.of(2026, 6, 15);

    assertFalse(stream.isBusinessDay(holiday));
  }

  @Test
  void nextBusinessDay_fromWeekday_returnsNextBusinessDay() {
    // Wednesday -> Thursday
    LocalDate wednesday = LocalDate.of(2025, 3, 5);
    LocalDate thursday = LocalDate.of(2025, 3, 6);

    assertEquals(thursday, stream.nextBusinessDay(wednesday));
  }

  @Test
  void nextBusinessDay_fromFriday_skipsWeekend() {
    // Friday -> Monday
    LocalDate friday = LocalDate.of(2025, 3, 7);
    LocalDate monday = LocalDate.of(2025, 3, 10);

    assertEquals(monday, stream.nextBusinessDay(friday));
  }

  @Test
  void prevBusinessDay_fromMonday_returnsFriday() {
    LocalDate monday = LocalDate.of(2025, 3, 10);
    LocalDate friday = LocalDate.of(2025, 3, 7);

    assertEquals(friday, stream.prevBusinessDay(monday));
  }

  @Test
  void prevBusinessDay_fromWeekday_returnsPrevious() {
    LocalDate thursday = LocalDate.of(2025, 3, 6);
    LocalDate wednesday = LocalDate.of(2025, 3, 5);

    assertEquals(wednesday, stream.prevBusinessDay(thursday));
  }

  @Test
  void nthBusinessDay_positive_movesForward() {
    LocalDate monday = LocalDate.of(2025, 3, 3);
    LocalDate expected = LocalDate.of(2025, 3, 5); // 2 business days later

    assertEquals(expected, stream.nthBusinessDay(monday, 2));
  }

  @Test
  void nthBusinessDay_negative_movesBackward() {
    LocalDate friday = LocalDate.of(2025, 3, 7);
    LocalDate expected = LocalDate.of(2025, 3, 5); // 2 business days before

    assertEquals(expected, stream.nthBusinessDay(friday, -2));
  }

  @Test
  void nthBusinessDay_zero_returnsSameDate() {
    LocalDate date = LocalDate.of(2025, 3, 5);

    assertEquals(date, stream.nthBusinessDay(date, 0));
  }

  @Test
  void businessDaysInRange_countsCorrectly() {
    // March 3-7, 2025: Mon-Fri = 5 business days
    LocalDate from = LocalDate.of(2025, 3, 3);
    LocalDate to = LocalDate.of(2025, 3, 7);

    long count = stream.businessDaysInRange(from, to);

    assertEquals(5, count);
  }

  @Test
  void businessDaysInRange_withWeekend_excludesWeekend() {
    // March 7-10, 2025: Fri, Sat, Sun, Mon = 2 business days
    LocalDate from = LocalDate.of(2025, 3, 7);
    LocalDate to = LocalDate.of(2025, 3, 10);

    long count = stream.businessDaysInRange(from, to);

    assertEquals(2, count);
  }

  @Test
  void businessDaysInRange_invertedRange_throwsException() {
    LocalDate from = LocalDate.of(2025, 3, 10);
    LocalDate to = LocalDate.of(2025, 3, 3);

    assertThrows(IllegalArgumentException.class, () -> stream.businessDaysInRange(from, to));
  }

  @Test
  void eventCountInRange_countsEvents() {
    LocalDate from = LocalDate.of(2025, 6, 1);
    LocalDate to = LocalDate.of(2025, 6, 30);

    long count = stream.eventCountInRange(from, to);

    assertTrue(count > 0);
  }

  @Test
  void clearCache_resetsCache() {
    // Query to populate cache
    stream.eventsOn(LocalDate.of(2025, 6, 15));

    // Clear should not throw
    assertDoesNotThrow(() -> stream.clearCache());

    // Should still work after clearing
    List<Event> events = stream.eventsOn(LocalDate.of(2025, 6, 15));
    assertNotNull(events);
  }
}
