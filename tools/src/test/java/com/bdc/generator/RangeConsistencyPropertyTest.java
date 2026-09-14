package com.bdc.generator;

import static com.bdc.generator.RangeConsistencyTest.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.*;
import com.bdc.stream.LazyDateStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import net.jqwik.api.*;

class RangeConsistencyPropertyTest {
  @Property(tries = 80)
  void nestedRangesAndAdjacentPartitionsPreserveCompleteEventLists(@ForAll long seed) {
    Random random = new Random(seed);
    LocalDate from = LocalDate.of(2020 + random.nextInt(8), 12, 1);
    LocalDate to = from.plusDays(100);
    LocalDate narrowFrom = from.plusDays(random.nextInt(70));
    LocalDate narrowTo = narrowFrom.plusDays(random.nextInt(30));
    LocalDate split = from.plusDays(random.nextInt(100));
    List<EventSource> sources =
        new ArrayList<>(
            List.of(
                fixed("newyear", 1, 1),
                fixed("christmas", 12, 25),
                fixed("boxing", 12, 26),
                fixed("end", 12, 31),
                fixed("leap", 2, 29),
                source(
                    "nth",
                    new Rule.NthWeekdayOfMonth("nth", "nth", 1, DayOfWeek.MONDAY, 3),
                    random.nextBoolean()),
                source(
                    "relative",
                    new Rule.RelativeToReference("relative", "relative", "easter", -100),
                    true),
                source(
                    "long",
                    new Rule.RelativeToReference("long", "long", null, 800, 11, 1, null),
                    true),
                source(
                    "weekday",
                    new Rule.RelativeToReference(
                        "weekday",
                        "weekday",
                        12,
                        31,
                        new Rule.WeekdayOffset(DayOfWeek.FRIDAY, 2, Rule.OffsetDirection.AFTER)),
                    true)));
    for (int i = 0; i < 12; i++) {
      LocalDate original = from.plusDays(random.nextInt(120) - 10);
      sources.add(explicit("explicit" + i, random.nextBoolean(), original, original));
    }
    // Equal-looking events with distinct provenance must remain duplicated in full-list
    // comparisons.
    sources.add(
        source("duplicateA", new Rule.FixedMonthDay("duplicateA", "duplicate", 1, 1, "ISO"), true));
    sources.add(
        source("duplicateB", new Rule.FixedMonthDay("duplicateB", "duplicate", 1, 1, "ISO"), true));
    List<Delta> deltas =
        List.of(
            new Delta.Remove(
                "newyear", from.withYear(from.getYear() + 1).withMonth(1).withDayOfMonth(2)),
            new Delta.Add("added", "added", narrowFrom, EventType.CLOSED),
            new Delta.Reclassify("boxing", from.withDayOfMonth(28), EventType.NOTABLE));
    for (WeekendShiftPolicy policy : WeekendShiftPolicy.values()) {
      for (WeekendPolicy weekend :
          List.of(
              WeekendPolicy.SAT_SUN,
              WeekendPolicy.NONE,
              new WeekendPolicy(List.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)))) {
        ResolvedSpec spec = spec(policy, weekend, sources, deltas);
        EventGenerator generator = new EventGenerator();
        List<Event> wide = generator.generate(spec, from, to);
        assertEquals(
            within(wide, narrowFrom, narrowTo),
            generator.generate(spec, narrowFrom, narrowTo),
            "nested: " + policy + " " + weekend + " seed=" + seed);
        List<Event> partition = new ArrayList<>(generator.generate(spec, from, split));
        partition.addAll(generator.generate(spec, split.plusDays(1), to));
        assertEquals(wide, partition, "partition: " + policy + " " + weekend + " seed=" + seed);
        assertTrue(wide.stream().allMatch(e -> !e.date().isBefore(from) && !e.date().isAfter(to)));
        LazyDateStream stream = new LazyDateStream(spec);
        assertEquals(within(wide, narrowFrom, narrowFrom), stream.eventsOn(narrowFrom));
        long expectedCount =
            narrowFrom
                .datesUntil(narrowTo.plusDays(1))
                .filter(d -> !weekend.isWeekend(d.getDayOfWeek()))
                .filter(
                    d -> within(wide, d, d).stream().noneMatch(e -> e.type() == EventType.CLOSED))
                .count();
        assertEquals(expectedCount, stream.businessDaysInRange(narrowFrom, narrowTo));
      }
    }
  }
}
