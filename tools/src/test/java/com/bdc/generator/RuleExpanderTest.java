package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.DateRange;
import com.bdc.formula.ReferenceResolver;
import com.bdc.model.Occurrence;
import com.bdc.model.Reference;
import com.bdc.model.Rule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuleExpanderTest {

  private RuleExpander expander;
  private DateRange range2024;

  @BeforeEach
  void setUp() {
    expander = new RuleExpander();
    range2024 = new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
  }

  @Test
  void expandFixedMonthDay() {
    Rule.FixedMonthDay rule = new Rule.FixedMonthDay("christmas", "Christmas Day", 12, 25, "ISO");

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2024, 12, 25), occurrences.get(0).date());
    assertEquals("christmas", occurrences.get(0).key());
  }

  @Test
  void expandNthWeekdayOfMonth_first() {
    // Labor Day: 1st Monday of September
    Rule.NthWeekdayOfMonth rule =
        new Rule.NthWeekdayOfMonth("labor_day", "Labor Day", 9, DayOfWeek.MONDAY, 1);

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2024, 9, 2), occurrences.get(0).date()); // Sept 2, 2024 is 1st Monday
  }

  @Test
  void expandNthWeekdayOfMonth_last() {
    // Memorial Day: Last Monday of May
    Rule.NthWeekdayOfMonth rule =
        new Rule.NthWeekdayOfMonth("memorial_day", "Memorial Day", 5, DayOfWeek.MONDAY, -1);

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    assertEquals(
        LocalDate.of(2024, 5, 27), occurrences.get(0).date()); // May 27, 2024 is last Monday
  }

  @Test
  void expandNthWeekdayOfMonth_fourth() {
    // Thanksgiving: 4th Thursday of November
    Rule.NthWeekdayOfMonth rule =
        new Rule.NthWeekdayOfMonth("thanksgiving", "Thanksgiving Day", 11, DayOfWeek.THURSDAY, 4);

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2024, 11, 28), occurrences.get(0).date()); // Nov 28, 2024
  }

  @Test
  void expandExplicitDates() {
    Rule.ExplicitDates rule =
        new Rule.ExplicitDates(
            "good_friday",
            "Good Friday",
            List.of(
                LocalDate.of(2023, 4, 7), // Outside range
                LocalDate.of(2024, 3, 29), // Inside range
                LocalDate.of(2025, 4, 18) // Outside range
                ));

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2024, 3, 29), occurrences.get(0).date());
  }

  @Test
  void expandHijriFixedMonthDay() {
    // Eid al-Fitr: Shawwal 1
    Rule.FixedMonthDay rule = new Rule.FixedMonthDay("eid_al_fitr", "Eid al-Fitr", 10, 1, "HIJRI");

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    // Should find Eid al-Fitr 1445 (approx April 2024) and 1446 (approx March/April 2025 - outside
    // range)
    assertFalse(occurrences.isEmpty());
    // Eid al-Fitr 1445 falls on approximately April 10, 2024
    assertTrue(
        occurrences.stream()
            .anyMatch(o -> o.date().getYear() == 2024 && o.date().getMonthValue() == 4));
  }

  @Test
  void expandRelativeToReference_goodFriday() {
    // Set up reference resolver with Easter
    ReferenceResolver refResolver = new ReferenceResolver();
    refResolver.resolve(List.of(new Reference("easter", "EASTER_WESTERN")), range2024);
    expander.setReferenceResolver(refResolver);

    Rule.RelativeToReference rule =
        new Rule.RelativeToReference("good_friday", "Good Friday", "easter", -2);

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    // Easter 2024 is March 31, Good Friday is March 29
    assertEquals(LocalDate.of(2024, 3, 29), occurrences.get(0).date());
    assertEquals("good_friday", occurrences.get(0).key());
    assertEquals("Good Friday", occurrences.get(0).name());
  }

  @Test
  void expandRelativeToReference_unknownReference() {
    // Set up reference resolver without the referenced key
    ReferenceResolver refResolver = new ReferenceResolver();
    refResolver.resolve(List.of(new Reference("easter", "EASTER_WESTERN")), range2024);
    expander.setReferenceResolver(refResolver);

    Rule.RelativeToReference rule =
        new Rule.RelativeToReference("test", "Test", "nonexistent", -2);

    assertThrows(IllegalArgumentException.class, () -> expander.expand(rule, range2024, "test"));
  }

  @Test
  void expandRelativeToReference_narrowRange() {
    // Test that narrow date ranges work correctly - querying just Good Friday
    // should still work even though Easter is outside the range
    DateRange goodFridayOnly =
        new DateRange(LocalDate.of(2024, 3, 29), LocalDate.of(2024, 3, 29));

    ReferenceResolver refResolver = new ReferenceResolver();
    refResolver.resolve(List.of(new Reference("easter", "EASTER_WESTERN")), goodFridayOnly);
    expander.setReferenceResolver(refResolver);

    Rule.RelativeToReference rule =
        new Rule.RelativeToReference("good_friday", "Good Friday", "easter", -2);

    List<Occurrence> occurrences = expander.expand(rule, goodFridayOnly, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2024, 3, 29), occurrences.get(0).date());
  }
}
