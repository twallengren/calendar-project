package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventGeneratorTest {

  private EventGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new EventGenerator();
  }

  @Test
  void nearestWeekday_saturdayShiftsToFriday() {
    // July 4, 2020 falls on Saturday - should shift to Friday July 3
    EventSource source =
        new EventSource(
            "independence_day",
            "Independence Day",
            new Rule.FixedMonthDay("independence_day", "Independence Day", 7, 4, "ISO"),
            EventType.CLOSED,
            true);

    ResolvedSpec spec =
        new ResolvedSpec(
            "test",
            null,
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEAREST_WEEKDAY,
            List.of(),
            List.of(source),
            Map.of(),
            List.of(),
            List.of());

    List<Event> events =
        generator.generate(spec, LocalDate.of(2020, 7, 1), LocalDate.of(2020, 7, 10));

    Event holiday = events.stream().filter(e -> e.type() == EventType.CLOSED).findFirst().orElse(null);
    assertNotNull(holiday);
    assertEquals(LocalDate.of(2020, 7, 3), holiday.date()); // Friday
    assertEquals("Independence Day", holiday.description());
  }

  @Test
  void nearestWeekday_sundayShiftsToMonday() {
    // June 19, 2022 falls on Sunday - should shift to Monday June 20
    EventSource source =
        new EventSource(
            "juneteenth",
            "Juneteenth",
            new Rule.FixedMonthDay("juneteenth", "Juneteenth", 6, 19, "ISO"),
            EventType.CLOSED,
            true);

    ResolvedSpec spec =
        new ResolvedSpec(
            "test",
            null,
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEAREST_WEEKDAY,
            List.of(),
            List.of(source),
            Map.of(),
            List.of(),
            List.of());

    List<Event> events =
        generator.generate(spec, LocalDate.of(2022, 6, 15), LocalDate.of(2022, 6, 25));

    Event holiday = events.stream().filter(e -> e.type() == EventType.CLOSED).findFirst().orElse(null);
    assertNotNull(holiday);
    assertEquals(LocalDate.of(2022, 6, 20), holiday.date()); // Monday
  }

  @Test
  void nearestWeekday_noShiftOnWeekday() {
    // Christmas 2024 falls on Wednesday - no shift needed
    EventSource source =
        new EventSource(
            "christmas",
            "Christmas Day",
            new Rule.FixedMonthDay("christmas", "Christmas Day", 12, 25, "ISO"),
            EventType.CLOSED,
            true);

    ResolvedSpec spec =
        new ResolvedSpec(
            "test",
            null,
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEAREST_WEEKDAY,
            List.of(),
            List.of(source),
            Map.of(),
            List.of(),
            List.of());

    List<Event> events =
        generator.generate(spec, LocalDate.of(2024, 12, 20), LocalDate.of(2024, 12, 31));

    Event holiday = events.stream().filter(e -> e.type() == EventType.CLOSED).findFirst().orElse(null);
    assertNotNull(holiday);
    assertEquals(LocalDate.of(2024, 12, 25), holiday.date()); // Wednesday, no shift
  }

  @Test
  void nonShiftableHoliday_skippedOnWeekend() {
    // Christmas Eve 2022 falls on Saturday - with shiftable=false, should be skipped entirely
    // (no early close needed when market is already closed)
    EventSource source =
        new EventSource(
            "christmas_eve",
            "Christmas Eve",
            new Rule.FixedMonthDay("christmas_eve", "Christmas Eve", 12, 24, "ISO"),
            EventType.EARLY_CLOSE,
            false); // Not shiftable

    ResolvedSpec spec =
        new ResolvedSpec(
            "test",
            null,
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEAREST_WEEKDAY,
            List.of(),
            List.of(source),
            Map.of(),
            List.of(),
            List.of());

    List<Event> events =
        generator.generate(spec, LocalDate.of(2022, 12, 20), LocalDate.of(2022, 12, 31));

    // Christmas Eve on Saturday should NOT appear as EARLY_CLOSE - it should be a WEEKEND
    Event saturday = events.stream()
        .filter(e -> e.date().equals(LocalDate.of(2022, 12, 24)))
        .findFirst()
        .orElse(null);
    assertNotNull(saturday);
    assertEquals(EventType.WEEKEND, saturday.type()); // Just a normal weekend day
  }

  @Test
  void nextAvailableWeekday_cascadesCorrectly() {
    // Christmas (Dec 25) and Boxing Day (Dec 26) both on weekend
    // 2021: Dec 25 = Saturday, Dec 26 = Sunday
    // Expected: Christmas → Mon Dec 27, Boxing Day → Tue Dec 28
    EventSource christmas =
        new EventSource(
            "christmas",
            "Christmas Day",
            new Rule.FixedMonthDay("christmas", "Christmas Day", 12, 25, "ISO"),
            EventType.CLOSED,
            true);
    EventSource boxingDay =
        new EventSource(
            "boxing_day",
            "Boxing Day",
            new Rule.FixedMonthDay("boxing_day", "Boxing Day", 12, 26, "ISO"),
            EventType.CLOSED,
            true);

    ResolvedSpec spec =
        new ResolvedSpec(
            "test",
            null,
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            List.of(),
            List.of(christmas, boxingDay),
            Map.of(),
            List.of(),
            List.of());

    List<Event> events =
        generator.generate(spec, LocalDate.of(2021, 12, 20), LocalDate.of(2021, 12, 31));

    List<Event> holidays = events.stream().filter(e -> e.type() == EventType.CLOSED).toList();
    assertEquals(2, holidays.size());

    Event christmasEvent = holidays.stream()
        .filter(e -> e.description().equals("Christmas Day"))
        .findFirst()
        .orElse(null);
    Event boxingDayEvent = holidays.stream()
        .filter(e -> e.description().equals("Boxing Day"))
        .findFirst()
        .orElse(null);

    assertNotNull(christmasEvent);
    assertNotNull(boxingDayEvent);
    assertEquals(LocalDate.of(2021, 12, 27), christmasEvent.date()); // Monday
    assertEquals(LocalDate.of(2021, 12, 28), boxingDayEvent.date()); // Tuesday
  }

  @Test
  void nextAvailableWeekday_christmasSundayBoxingMonday() {
    // 2022: Dec 25 = Sunday, Dec 26 = Monday
    // Boxing Day is already on Monday (no shift needed)
    // Christmas shifts to Tuesday Dec 27 (Monday is taken)
    EventSource christmas =
        new EventSource(
            "christmas",
            "Christmas Day",
            new Rule.FixedMonthDay("christmas", "Christmas Day", 12, 25, "ISO"),
            EventType.CLOSED,
            true);
    EventSource boxingDay =
        new EventSource(
            "boxing_day",
            "Boxing Day",
            new Rule.FixedMonthDay("boxing_day", "Boxing Day", 12, 26, "ISO"),
            EventType.CLOSED,
            true);

    ResolvedSpec spec =
        new ResolvedSpec(
            "test",
            null,
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NEXT_AVAILABLE_WEEKDAY,
            List.of(),
            List.of(christmas, boxingDay),
            Map.of(),
            List.of(),
            List.of());

    List<Event> events =
        generator.generate(spec, LocalDate.of(2022, 12, 20), LocalDate.of(2022, 12, 31));

    List<Event> holidays = events.stream().filter(e -> e.type() == EventType.CLOSED).toList();
    assertEquals(2, holidays.size());

    Event christmasEvent = holidays.stream()
        .filter(e -> e.description().equals("Christmas Day"))
        .findFirst()
        .orElse(null);
    Event boxingDayEvent = holidays.stream()
        .filter(e -> e.description().equals("Boxing Day"))
        .findFirst()
        .orElse(null);

    assertNotNull(christmasEvent);
    assertNotNull(boxingDayEvent);
    assertEquals(LocalDate.of(2022, 12, 26), boxingDayEvent.date()); // Monday (no shift)
    assertEquals(LocalDate.of(2022, 12, 27), christmasEvent.date()); // Tuesday (shifted past Boxing Day)
  }

  @Test
  void noShiftPolicy_holidaysStayOnWeekends() {
    // July 4, 2020 falls on Saturday - with NONE policy, stays on Saturday
    EventSource source =
        new EventSource(
            "independence_day",
            "Independence Day",
            new Rule.FixedMonthDay("independence_day", "Independence Day", 7, 4, "ISO"),
            EventType.CLOSED,
            true);

    ResolvedSpec spec =
        new ResolvedSpec(
            "test",
            null,
            WeekendPolicy.SAT_SUN,
            WeekendShiftPolicy.NONE,
            List.of(),
            List.of(source),
            Map.of(),
            List.of(),
            List.of());

    List<Event> events =
        generator.generate(spec, LocalDate.of(2020, 7, 1), LocalDate.of(2020, 7, 10));

    Event holiday = events.stream()
        .filter(e -> e.type() == EventType.CLOSED)
        .findFirst()
        .orElse(null);
    assertNotNull(holiday);
    assertEquals(LocalDate.of(2020, 7, 4), holiday.date()); // Saturday, no shift
  }
}
