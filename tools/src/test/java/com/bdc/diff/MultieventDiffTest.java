package com.bdc.diff;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.Event;
import com.bdc.model.EventType;
import java.time.LocalDate;
import java.util.*;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

class MultieventDiffTest {
  private static final LocalDate DAY = LocalDate.of(2024, 1, 1);
  private final CalendarDiffEngine engine = new CalendarDiffEngine();

  private Event event(String name) {
    return new Event(DAY, EventType.CLOSED, name, "test");
  }

  private CalendarDiff compare(List<Event> before, List<Event> after) {
    return engine.compare("TEST", after, before, DAY, DAY, DAY.plusYears(1));
  }

  @Test
  void unchangedFirstEventCannotHideAnotherEventsChanges() {
    Event a = event("A");
    Event b = event("B");
    Event c = event("C");
    CalendarDiff added = compare(List.of(a), List.of(a, b));
    assertEquals(List.of(EventDiff.added(DAY, b.type(), "B")), added.additions());
    assertEquals(DiffSeverity.MAJOR, added.severity());
    CalendarDiff removed = compare(List.of(a, b), List.of(a));
    assertEquals(List.of(EventDiff.removed(DAY, b.type(), "B")), removed.removals());
    CalendarDiff changed = compare(List.of(a, b), List.of(a, c));
    assertEquals(
        List.of(EventDiff.modified(DAY, b.type(), c.type(), "B", "C")), changed.modifications());
  }

  @Test
  void cancelsExactDuplicatesBeforeConsideringModifications() {
    Event a = event("A");
    Event b = event("B");
    assertEquals(2, compare(List.of(a), List.of(a, a, a)).additions().size());
    assertEquals(2, compare(List.of(a, a, a), List.of(a)).removals().size());
    CalendarDiff single = compare(List.of(a, a), List.of(a, b));
    assertEquals(
        List.of(EventDiff.modified(DAY, a.type(), b.type(), "A", "B")), single.modifications());
    CalendarDiff ambiguous = compare(List.of(a, a), List.of(b, b));
    assertEquals(2, ambiguous.additions().size());
    assertEquals(2, ambiguous.removals().size());
    assertTrue(ambiguous.modifications().isEmpty());
    assertEquals(4, ambiguous.totalChanges());
  }

  @Test
  void ambiguousGroupsAreNotPairedByNameOrType() {
    Event a = event("A");
    Event b = event("B");
    Event changedA = new Event(DAY, EventType.NOTABLE, "A", "other");
    CalendarDiff diff = compare(List.of(a, b), List.of(changedA, event("C")));
    assertTrue(diff.modifications().isEmpty());
    assertEquals(2, diff.removals().size());
    assertEquals(2, diff.additions().size());
    assertEquals(2, compare(List.of(a), List.of(changedA, event("C"))).additions().size());
  }

  @Test
  void movesAreRemoveAndAddAndProvenanceIsIgnored() {
    Event a = event("A");
    assertFalse(
        compare(List.of(a), List.of(new Event(DAY, a.type(), "A", "blessed"))).hasChanges());
    CalendarDiff moved =
        compare(List.of(a), List.of(new Event(DAY.plusDays(1), a.type(), "A", "test")));
    assertEquals(1, moved.additions().size());
    assertEquals(1, moved.removals().size());
    assertTrue(moved.modifications().isEmpty());
    for (LocalDate outside : List.of(DAY.minusDays(1), DAY.plusYears(1).plusDays(1))) {
      Event value = new Event(outside, a.type(), "A", "test");
      assertEquals(DiffSeverity.MINOR, compare(List.of(), List.of(value, value)).severity());
    }
    Event boundary = new Event(DAY.plusYears(1), a.type(), "A", "test");
    assertEquals(DiffSeverity.MAJOR, compare(List.of(), List.of(boundary, boundary)).severity());
  }

  @Property(tries = 200)
  void permutationEqualityAndReconstructionLaws(@ForAll long seed) {
    Random random = new Random(seed);
    List<Event> before = randomEvents(random);
    List<Event> after = randomEvents(random);
    CalendarDiff diff = compare(before, after);
    assertEquals(counts(before).equals(counts(after)), !diff.hasChanges());
    Collections.shuffle(before, random);
    Collections.shuffle(after, random);
    assertEquals(diff, compare(before, after));
    Map<List<Object>, Integer> rebuilt = counts(before);
    for (EventDiff d : diff.removals())
      subtract(rebuilt, List.of(d.date(), d.oldType(), d.oldDescription()));
    for (EventDiff d : diff.modifications()) {
      subtract(rebuilt, List.of(d.date(), d.oldType(), d.oldDescription()));
      rebuilt.merge(List.of(d.date(), d.newType(), d.newDescription()), 1, Integer::sum);
    }
    for (EventDiff d : diff.additions())
      rebuilt.merge(List.of(d.date(), d.newType(), d.newDescription()), 1, Integer::sum);
    assertEquals(counts(after), rebuilt);
  }

  private List<Event> randomEvents(Random random) {
    List<Event> result = new ArrayList<>();
    for (int i = random.nextInt(70); i > 0; i--) {
      result.add(
          new Event(
              DAY.plusDays(random.nextInt(4)),
              EventType.values()[random.nextInt(3)],
              "event " + random.nextInt(4),
              "source " + random.nextInt(3)));
    }
    return result;
  }

  private Map<List<Object>, Integer> counts(List<Event> events) {
    Map<List<Object>, Integer> result = new HashMap<>();
    for (Event e : events)
      result.merge(List.of(e.date(), e.type(), e.description()), 1, Integer::sum);
    return result;
  }

  private void subtract(Map<List<Object>, Integer> counts, List<Object> row) {
    assertTrue(counts.getOrDefault(row, 0) > 0, "Cannot remove absent row " + row);
    if (counts.get(row) == 1) counts.remove(row);
    else counts.put(row, counts.get(row) - 1);
  }
}
