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

    Rule.RelativeToReference rule = new Rule.RelativeToReference("test", "Test", "nonexistent", -2);

    assertThrows(IllegalArgumentException.class, () -> expander.expand(rule, range2024, "test"));
  }

  @Test
  void expandRelativeToReference_narrowRange() {
    // Test that narrow date ranges work correctly - querying just Good Friday
    // should still work even though Easter is outside the range
    DateRange goodFridayOnly = new DateRange(LocalDate.of(2024, 3, 29), LocalDate.of(2024, 3, 29));

    ReferenceResolver refResolver = new ReferenceResolver();
    refResolver.resolve(List.of(new Reference("easter", "EASTER_WESTERN")), goodFridayOnly);
    expander.setReferenceResolver(refResolver);

    Rule.RelativeToReference rule =
        new Rule.RelativeToReference("good_friday", "Good Friday", "easter", -2);

    List<Occurrence> occurrences = expander.expand(rule, goodFridayOnly, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2024, 3, 29), occurrences.get(0).date());
  }

  @Test
  void expandObservedHoliday_saturdayShiftToFriday() {
    // July 4, 2020 falls on Saturday - should shift to Friday July 3
    DateRange range2020 = new DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));

    Rule.FixedMonthDay baseRule =
        new Rule.FixedMonthDay("independence_day_base", "Independence Day", 7, 4, "ISO");
    Rule.ObservedHoliday rule =
        new Rule.ObservedHoliday(
            "independence_day",
            "Independence Day",
            baseRule,
            Rule.ShiftPolicy.FRIDAY,
            Rule.ShiftPolicy.MONDAY);

    List<Occurrence> occurrences = expander.expand(rule, range2020, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2020, 7, 3), occurrences.get(0).date()); // Friday
    assertEquals("independence_day", occurrences.get(0).key());
  }

  @Test
  void expandObservedHoliday_sundayShiftToMonday() {
    // June 19, 2022 falls on Sunday - should shift to Monday June 20
    DateRange range2022 = new DateRange(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31));

    Rule.FixedMonthDay baseRule =
        new Rule.FixedMonthDay("juneteenth_base", "Juneteenth", 6, 19, "ISO");
    Rule.ObservedHoliday rule =
        new Rule.ObservedHoliday(
            "juneteenth",
            "Juneteenth",
            baseRule,
            Rule.ShiftPolicy.FRIDAY,
            Rule.ShiftPolicy.MONDAY);

    List<Occurrence> occurrences = expander.expand(rule, range2022, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2022, 6, 20), occurrences.get(0).date()); // Monday
  }

  @Test
  void expandObservedHoliday_noShiftOnWeekday() {
    // Christmas 2024 falls on Wednesday - no shift needed
    Rule.FixedMonthDay baseRule =
        new Rule.FixedMonthDay("christmas_base", "Christmas Day", 12, 25, "ISO");
    Rule.ObservedHoliday rule =
        new Rule.ObservedHoliday(
            "christmas",
            "Christmas Day",
            baseRule,
            Rule.ShiftPolicy.FRIDAY,
            Rule.ShiftPolicy.MONDAY);

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2024, 12, 25), occurrences.get(0).date()); // Wednesday, no shift
  }

  @Test
  void expandObservedHoliday_saturdayShiftNone_skips() {
    // Christmas Eve 2022 falls on Saturday - with NONE policy, should be skipped
    DateRange range2022 = new DateRange(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31));

    Rule.FixedMonthDay baseRule =
        new Rule.FixedMonthDay("christmas_eve_base", "Christmas Eve", 12, 24, "ISO");
    Rule.ObservedHoliday rule =
        new Rule.ObservedHoliday(
            "christmas_eve",
            "Christmas Eve",
            baseRule,
            Rule.ShiftPolicy.NONE, // Don't observe if on weekend
            Rule.ShiftPolicy.NONE);

    List<Occurrence> occurrences = expander.expand(rule, range2022, "test");

    assertEquals(0, occurrences.size()); // Skipped because Dec 24, 2022 is Saturday
  }

  @Test
  void expandObservedHoliday_newYears2022_saturdayShiftsToPreviousYearFriday() {
    // Jan 1, 2022 falls on Saturday - should shift to Friday Dec 31, 2021
    DateRange range = new DateRange(LocalDate.of(2021, 12, 1), LocalDate.of(2022, 1, 31));

    Rule.FixedMonthDay baseRule =
        new Rule.FixedMonthDay("new_years_base", "New Year's Day", 1, 1, "ISO");
    Rule.ObservedHoliday rule =
        new Rule.ObservedHoliday(
            "new_years_day",
            "New Year's Day",
            baseRule,
            Rule.ShiftPolicy.FRIDAY,
            Rule.ShiftPolicy.MONDAY);

    List<Occurrence> occurrences = expander.expand(rule, range, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2021, 12, 31), occurrences.get(0).date()); // Friday Dec 31, 2021
  }
}
