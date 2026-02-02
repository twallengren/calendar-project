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
                new Rule.AnnotatedDate(LocalDate.of(2023, 4, 7)), // Outside range
                new Rule.AnnotatedDate(LocalDate.of(2024, 3, 29)), // Inside range
                new Rule.AnnotatedDate(LocalDate.of(2025, 4, 18)) // Outside range
                ));

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2024, 3, 29), occurrences.get(0).date());
    assertEquals("Good Friday", occurrences.get(0).name());
  }

  @Test
  void expandExplicitDates_withComments() {
    Rule.ExplicitDates rule =
        new Rule.ExplicitDates(
            "presidential_funeral",
            "National Day of Mourning",
            List.of(
                new Rule.AnnotatedDate(LocalDate.of(2004, 6, 11), "Ronald Reagan"),
                new Rule.AnnotatedDate(LocalDate.of(2007, 1, 2), "Gerald Ford")));

    DateRange range = new DateRange(LocalDate.of(2000, 1, 1), LocalDate.of(2010, 12, 31));
    List<Occurrence> occurrences = expander.expand(rule, range, "test");

    assertEquals(2, occurrences.size());
    assertEquals("National Day of Mourning (Ronald Reagan)", occurrences.get(0).name());
    assertEquals("National Day of Mourning (Gerald Ford)", occurrences.get(1).name());
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
  void expandRelativeToReference_fixedReference_withWeekdayOffset_electionDay() {
    // Election Day: 1st Tuesday after November 1st
    Rule.WeekdayOffset offset =
        new Rule.WeekdayOffset(DayOfWeek.TUESDAY, 1, Rule.OffsetDirection.AFTER);
    Rule.RelativeToReference rule =
        new Rule.RelativeToReference("election_day", "Election Day", 11, 1, offset);

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    // Nov 1, 2024 is Friday, so 1st Tuesday after is Nov 5, 2024
    assertEquals(LocalDate.of(2024, 11, 5), occurrences.get(0).date());
    assertEquals(DayOfWeek.TUESDAY, occurrences.get(0).date().getDayOfWeek());
  }

  @Test
  void expandRelativeToReference_fixedReference_withWeekdayOffset_multipleYears() {
    // Test election day across multiple years
    // Election Day = 1st Tuesday strictly after November 1st
    DateRange multiYear = new DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 12, 31));

    Rule.WeekdayOffset offset =
        new Rule.WeekdayOffset(DayOfWeek.TUESDAY, 1, Rule.OffsetDirection.AFTER);
    Rule.RelativeToReference rule =
        new Rule.RelativeToReference("election_day", "Election Day", 11, 1, offset);

    List<Occurrence> occurrences = expander.expand(rule, multiYear, "test");

    assertEquals(5, occurrences.size());

    // 2020: Nov 1 is Sunday, 1st Tuesday after is Nov 3
    assertEquals(LocalDate.of(2020, 11, 3), occurrences.get(0).date());
    // 2021: Nov 1 is Monday, 1st Tuesday after is Nov 2
    assertEquals(LocalDate.of(2021, 11, 2), occurrences.get(1).date());
    // 2022: Nov 1 is Tuesday, 1st Tuesday strictly after is Nov 8
    assertEquals(LocalDate.of(2022, 11, 8), occurrences.get(2).date());
    // 2023: Nov 1 is Wednesday, 1st Tuesday after is Nov 7
    assertEquals(LocalDate.of(2023, 11, 7), occurrences.get(3).date());
    // 2024: Nov 1 is Friday, 1st Tuesday after is Nov 5
    assertEquals(LocalDate.of(2024, 11, 5), occurrences.get(4).date());
  }

  @Test
  void expandRelativeToReference_weekdayOffset_before() {
    // Test finding weekday BEFORE a reference date
    // E.g., the Friday before Christmas (Dec 25)
    Rule.WeekdayOffset offset =
        new Rule.WeekdayOffset(DayOfWeek.FRIDAY, 1, Rule.OffsetDirection.BEFORE);
    Rule.RelativeToReference rule =
        new Rule.RelativeToReference(
            "friday_before_xmas", "Friday Before Christmas", 12, 25, offset);

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    // Dec 25, 2024 is Wednesday, Friday before is Dec 20
    assertEquals(LocalDate.of(2024, 12, 20), occurrences.get(0).date());
    assertEquals(DayOfWeek.FRIDAY, occurrences.get(0).date().getDayOfWeek());
  }

  @Test
  void expandRelativeToReference_weekdayOffset_nthBefore() {
    // Test finding 2nd Monday before a reference date
    Rule.WeekdayOffset offset =
        new Rule.WeekdayOffset(DayOfWeek.MONDAY, 2, Rule.OffsetDirection.BEFORE);
    Rule.RelativeToReference rule =
        new Rule.RelativeToReference(
            "second_monday_before", "Second Monday Before", 12, 25, offset);

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    // Dec 25, 2024 is Wednesday
    // 1st Monday before Dec 25 is Dec 23
    // 2nd Monday before Dec 25 is Dec 16
    assertEquals(LocalDate.of(2024, 12, 16), occurrences.get(0).date());
    assertEquals(DayOfWeek.MONDAY, occurrences.get(0).date().getDayOfWeek());
  }

  @Test
  void expandRelativeToReference_weekdayOffset_nthAfter() {
    // Test finding 3rd Thursday after Nov 1 (like Thanksgiving but wrong month marker)
    Rule.WeekdayOffset offset =
        new Rule.WeekdayOffset(DayOfWeek.THURSDAY, 3, Rule.OffsetDirection.AFTER);
    Rule.RelativeToReference rule =
        new Rule.RelativeToReference("third_thursday", "Third Thursday", 11, 1, offset);

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    // Nov 1, 2024 is Friday
    // 1st Thursday after Nov 1 is Nov 7
    // 2nd Thursday is Nov 14
    // 3rd Thursday is Nov 21
    assertEquals(LocalDate.of(2024, 11, 21), occurrences.get(0).date());
    assertEquals(DayOfWeek.THURSDAY, occurrences.get(0).date().getDayOfWeek());
  }

  @Test
  void expandRelativeToReference_fixedReference_withDayOffset() {
    // Test fixed reference with simple day offset (e.g., Nov 1 + 7 days)
    Rule.RelativeToReference rule =
        new Rule.RelativeToReference("week_after_nov1", "Week After Nov 1", null, 7, 11, 1, null);

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    assertEquals(LocalDate.of(2024, 11, 8), occurrences.get(0).date());
  }

  @Test
  void expandRelativeToReference_missingOffset_throws() {
    // Neither offsetDays nor offsetWeekday specified
    Rule.RelativeToReference rule =
        new Rule.RelativeToReference("bad_rule", "Bad Rule", null, null, 11, 1, null);

    assertThrows(IllegalArgumentException.class, () -> expander.expand(rule, range2024, "test"));
  }

  @Test
  void expandRelativeToReference_missingReference_throws() {
    // Neither named reference nor fixed month/day
    Rule.RelativeToReference rule =
        new Rule.RelativeToReference("bad_rule", "Bad Rule", null, 5, null, null, null);

    assertThrows(IllegalArgumentException.class, () -> expander.expand(rule, range2024, "test"));
  }
}
