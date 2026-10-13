package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.DateRange;
import com.bdc.model.Occurrence;
import com.bdc.model.Rule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyRuleTest {

  private final RuleExpander expander = new RuleExpander();

  @Test
  void everyWeekdayInTheRange() {
    Rule.Weekly rule = new Rule.Weekly("fri", "Friday", DayOfWeek.FRIDAY, null, null);
    List<Occurrence> occurrences =
        expander.expand(
            rule, new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), "p");
    assertEquals(
        List.of(
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 9),
            LocalDate.of(2026, 1, 16),
            LocalDate.of(2026, 1, 23),
            LocalDate.of(2026, 1, 30)),
        occurrences.stream().map(Occurrence::date).toList());
    assertEquals("Friday", occurrences.get(0).name());
    assertEquals("fri", occurrences.get(0).key());
  }

  @Test
  void fromAndToBoundTheRule() {
    Rule.Weekly rule =
        new Rule.Weekly(
            "fri", "Friday", DayOfWeek.FRIDAY, LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 23));
    List<Occurrence> occurrences =
        expander.expand(
            rule, new DateRange(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28)), "p");
    assertEquals(
        List.of(LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 23)),
        occurrences.stream().map(Occurrence::date).toList());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Rule.Weekly(
                "x", "x", DayOfWeek.MONDAY, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
  }

  @Test
  void emptyWhenTheBoundsMissTheRange() {
    Rule.Weekly rule =
        new Rule.Weekly("mon", "Monday", DayOfWeek.MONDAY, LocalDate.of(2030, 1, 1), null);
    assertTrue(
        expander
            .expand(rule, new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), "p")
            .isEmpty());
  }
}
