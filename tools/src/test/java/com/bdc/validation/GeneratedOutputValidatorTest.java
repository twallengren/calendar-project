package com.bdc.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.DateRange;
import com.bdc.model.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeneratedOutputValidatorTest {

  private final GeneratedOutputValidator validator = new GeneratedOutputValidator();

  private static ResolvedSpec spec(CalendarSpec.Coverage coverage, EventSource... sources) {
    return new ResolvedSpec(
        "T",
        new CalendarSpec.Metadata("T", null, "ISO", null, coverage),
        WeekendPolicy.SAT_SUN,
        WeekendShiftPolicy.NONE,
        List.of(),
        List.of(sources),
        Map.of(),
        List.of(),
        List.of());
  }

  @Test
  void closedAndEarlyCloseOnSameDateIsAnError() {
    LocalDate d = LocalDate.of(2021, 12, 24);
    List<Event> events =
        List.of(
            new Event(d, EventType.CLOSED, "Christmas", "t", "christmas", null, null, null, null),
            new Event(d, EventType.EARLY_CLOSE, "Eve", "t", "eve", null, null, null, null));
    ValidationResult r =
        validator.validate(spec(null), events, new DateRange(d.minusDays(5), d.plusDays(5)));
    assertTrue(r.hasErrors());
    assertEquals("SAME_DATE_CONFLICT", r.errors().get(0).code());
  }

  @Test
  void aSpecialSessionMustFallOnANonBusinessDay() {
    LocalDate friday = LocalDate.of(2024, 11, 1);
    LocalDate saturday = LocalDate.of(2024, 11, 2);
    LocalDate monday = LocalDate.of(2024, 11, 4);
    java.time.LocalTime open = java.time.LocalTime.of(18, 0);
    java.time.LocalTime close = java.time.LocalTime.of(19, 0);
    List<Event> events =
        List.of(
            new Event(
                friday, EventType.CLOSED, "Festival", "t", "festival", null, null, null, null),
            new Event(
                friday,
                EventType.SPECIAL_SESSION,
                "Evening",
                "t",
                "s",
                null,
                null,
                close,
                null,
                open),
            new Event(
                saturday, EventType.WEEKEND, "Saturday", "t", "weekend", null, null, null, null),
            new Event(
                saturday,
                EventType.SPECIAL_SESSION,
                "Drill",
                "t",
                "s",
                null,
                null,
                close,
                null,
                open),
            new Event(
                monday,
                EventType.SPECIAL_SESSION,
                "Ordinary",
                "t",
                "s",
                null,
                null,
                close,
                null,
                open));
    ValidationResult r = validator.validate(spec(null), events, new DateRange(friday, monday));
    assertEquals(1, r.errors().size(), r.issues().toString());
    assertEquals("SPECIAL_SESSION_ON_BUSINESS_DAY", r.errors().get(0).code());
    assertEquals("date:" + monday, r.errors().get(0).location());
  }

  @Test
  void deadRuleAndProjectedBeforeVerifiedAreWarnings() {
    EventSource dead =
        new EventSource(
            "dead",
            "Dead",
            new Rule.FixedMonthDay(null, null, 1, 1, "ISO"),
            EventType.CLOSED,
            false,
            null);
    CalendarSpec.Coverage coverage =
        new CalendarSpec.Coverage(
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), LocalDate.of(2024, 12, 31));
    List<Event> events =
        List.of(
            new Event(
                LocalDate.of(2024, 6, 1),
                EventType.CLOSED,
                "Projected",
                "t",
                "p",
                null,
                null,
                null,
                EventStatus.PROJECTED));
    ValidationResult r =
        validator.validate(
            spec(coverage, dead), events, new DateRange(coverage.from(), coverage.to()));
    List<String> codes = r.issues().stream().map(ValidationIssue::code).toList();
    assertTrue(codes.contains("DEAD_RULE"), codes.toString());
    assertTrue(codes.contains("PROJECTED_BEFORE_VERIFIED"), codes.toString());
    assertFalse(r.hasErrors());
  }
}
