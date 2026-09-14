package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.*;
import com.bdc.emitter.SpecEmitter;
import com.bdc.loader.YamlLoader;
import com.bdc.model.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeRuleTest {
  private static LocalDate date(String text) {
    return LocalDate.parse(text);
  }

  private static Rule.NativeFixedMonthDay purim() {
    return new Rule.NativeFixedMonthDay(
        "purim", "Purim", "HEBREW", List.of("ADAR", "ADAR_II"), 14, null, null);
  }

  @Test
  void recurringAdarSelectionAndExactInvalidDateDiffer() {
    var expander = new RuleExpander();
    var range = new DateRange(date("2024-01-01"), date("2025-12-31"));
    assertEquals(
        List.of(date("2024-03-24"), date("2025-03-14")),
        expander.expand(purim(), range, "test").stream().map(Occurrence::date).toList());
    var exact =
        new Rule.NativeExplicitDates("p", "p", List.of(new NativeDate("HEBREW", 5784, "ADAR", 14)));
    assertThrows(IllegalArgumentException.class, () -> expander.expand(exact, range, "test"));
    var typo = new Rule.NativeFixedMonthDay("p", "p", "HEBREW", List.of("ADR"), 14, null, null);
    assertThrows(IllegalArgumentException.class, () -> expander.expand(typo, range, "test"));
  }

  @Test
  void nativeYearFilterAndIsoYearFilterIntersect() {
    var rule =
        new Rule.NativeFixedMonthDay(
            "p",
            "p",
            "HEBREW",
            List.of("TISHRI"),
            1,
            List.of(new EventSource.YearRange(5785, 5785)),
            null);
    var source =
        new EventSource(
            "p",
            "p",
            rule,
            EventType.CLOSED,
            false,
            List.of(new EventSource.YearRange(2025, 2025)),
            null,
            null,
            null,
            null,
            null,
            null);
    var spec = RangeConsistencyTest.spec(WeekendShiftPolicy.NONE, source);
    assertTrue(
        new EventGenerator()
            .generate(spec, date("2024-01-01"), date("2025-12-31")).stream()
                .noneMatch(e -> e.type() == EventType.CLOSED));
  }

  @Test
  void fullGenerationPreservesNestedRangesForLongNativeOffsetsAndSpans() {
    var rule =
        new Rule.NativeRelativeToReference(
            "long", "long", "HEBREW", List.of("ADAR", "ADAR_II"), 14, 800, null, 430);
    var spec =
        RangeConsistencyTest.spec(
            WeekendShiftPolicy.NEAREST_WEEKDAY, RangeConsistencyTest.source("long", rule, true));
    var generator = new EventGenerator();
    var wide = generator.generate(spec, date("2024-01-01"), date("2030-12-31"));
    var from = date("2027-01-01");
    var to = date("2027-02-28");
    assertEquals(RangeConsistencyTest.within(wide, from, to), generator.generate(spec, from, to));
    assertFalse(wide.stream().filter(e -> e.type() == EventType.CLOSED).toList().isEmpty());
  }

  @Test
  void lastWeekdayUsesActualNativeMonthBoundary() {
    var rule =
        new Rule.NativeNthWeekday(
            "last", "last", "HEBREW", List.of("ADAR_II"), DayOfWeek.FRIDAY, -1, null, null);
    var found =
        new RuleExpander()
            .expand(rule, new DateRange(date("2024-01-01"), date("2024-12-31")), "test");
    assertEquals(List.of(date("2024-04-05")), found.stream().map(Occurrence::date).toList());
  }

  @Test
  void boundaryMonthMayBeginBeforeTheSupportedInterval() {
    var rule =
        new Rule.NativeNthWeekday(
            "last", "last", "HEBREW", List.of("TEVET"), DayOfWeek.FRIDAY, -1, null, null);
    var provider = ChronologyProviders.get("HEBREW");
    var last =
        provider
            .monthStart(5661, "TEVET")
            .plusDays(provider.monthLength(5661, "TEVET") - 1)
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
    var spec =
        RangeConsistencyTest.spec(
            WeekendShiftPolicy.NONE, RangeConsistencyTest.source("last", rule, false));
    assertTrue(
        new EventGenerator()
            .generate(spec, date("1901-01-01"), date("1901-01-31")).stream()
                .anyMatch(e -> e.type() == EventType.CLOSED && e.date().equals(last)));
  }

  @Test
  void unsupportedGenerationFailsInsteadOfProducingOpenDays() {
    var spec =
        RangeConsistencyTest.spec(
            WeekendShiftPolicy.NONE, RangeConsistencyTest.source("purim", purim(), false));
    assertThrows(
        UnsupportedChronologyRangeException.class,
        () -> new EventGenerator().generate(spec, date("1900-01-01"), date("1900-12-31")));
  }

  @Test
  void nativeYamlRoundTripsAndExplicitDatesPreserveMultiplicity() throws Exception {
    var mapper = new YamlLoader().getMapper();
    String yaml =
        "type: native_explicit_dates\nkey: p\nname: p\ndates:\n  - {chronology_id: HEBREW, year: 5786, month_code: TISHRI, day: 1}\n  - {chronology_id: HEBREW, year: 5786, month_code: TISHRI, day: 1}\n";
    Rule rule = mapper.readValue(yaml, Rule.class);
    var map = SpecEmitter.ruleToMap(rule);
    map.put("key", "p");
    map.put("name", "p");
    assertEquals(rule, mapper.readValue(mapper.writeValueAsString(map), Rule.class));
    assertEquals(
        2,
        new RuleExpander()
            .expand(rule, new DateRange(date("2025-09-23"), date("2025-09-23")), "test")
            .size());
  }
}
