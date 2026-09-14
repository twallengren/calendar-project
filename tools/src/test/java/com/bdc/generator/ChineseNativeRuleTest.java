package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bdc.chronology.DateRange;
import com.bdc.chronology.NativeDate;
import com.bdc.model.Occurrence;
import com.bdc.model.Rule;
import com.bdc.model.WeekendShiftPolicy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChineseNativeRuleTest {
  private static LocalDate date(String value) {
    return LocalDate.parse(value);
  }

  @Test
  void ordinaryAndIntercalarySelectorsStayDistinct() {
    var both =
        new Rule.NativeFixedMonthDay(
            "m2", "m2", "CHINESE_HK", List.of("M02", "M02L"), 1, null, null);
    assertEquals(
        List.of(date("2023-02-20"), date("2023-03-22"), date("2024-03-10")),
        new RuleExpander()
            .expand(both, new DateRange(date("2023-01-01"), date("2024-12-31")), "test").stream()
                .map(Occurrence::date)
                .toList());

    var leapOnly =
        new Rule.NativeFixedMonthDay("leap", "leap", "CHINESE_HK", List.of("M02L"), 1, null, null);
    assertEquals(
        List.of(date("2023-03-22")),
        new RuleExpander()
                .expand(leapOnly, new DateRange(date("2023-01-01"), date("2024-12-31")), "test")
                .stream()
                .map(Occurrence::date)
                .toList());
  }

  @Test
  void exactAbsentLeapMonthAndShortMonthDayFail() {
    var absent =
        new Rule.NativeExplicitDates(
            "x", "x", List.of(new NativeDate("CHINESE_HK", 2024, "M02L", 1)));
    var tooLong =
        new Rule.NativeExplicitDates(
            "x", "x", List.of(new NativeDate("CHINESE_HK", 2024, "M01", 30)));
    var range = new DateRange(date("2024-01-01"), date("2024-12-31"));
    assertThrows(
        IllegalArgumentException.class, () -> new RuleExpander().expand(absent, range, "test"));
    assertThrows(
        IllegalArgumentException.class, () -> new RuleExpander().expand(tooLong, range, "test"));
  }

  @Test
  void longOffsetsPreserveNestedGenerationRanges() {
    var rule =
        new Rule.NativeRelativeToReference(
            "long", "long", "CHINESE_HK", List.of("M01"), 1, 800, null, 430);
    var spec =
        RangeConsistencyTest.spec(
            WeekendShiftPolicy.FORWARD_ONLY, RangeConsistencyTest.source("long", rule, true));
    var generator = new EventGenerator();
    var wide = generator.generate(spec, date("2024-01-01"), date("2030-12-31"));
    LocalDate from = date("2027-01-01");
    LocalDate to = date("2027-04-30");
    assertEquals(RangeConsistencyTest.within(wide, from, to), generator.generate(spec, from, to));
    assertFalse(wide.isEmpty());
  }
}
