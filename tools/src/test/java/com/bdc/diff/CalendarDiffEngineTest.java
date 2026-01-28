package com.bdc.diff;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.Event;
import com.bdc.model.EventType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalendarDiffEngineTest {

  private CalendarDiffEngine engine;
  private LocalDate cutoffDate;

  @BeforeEach
  void setUp() {
    engine = new CalendarDiffEngine();
    cutoffDate = LocalDate.of(2026, 1, 22); // "Today" for tests
  }

  @Test
  void noChanges_returnsNoneSeverity() {
    List<Event> blessed =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(LocalDate.of(2025, 12, 25), EventType.CLOSED, "Christmas", "test"));
    List<Event> generated =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(LocalDate.of(2025, 12, 25), EventType.CLOSED, "Christmas", "test"));

    CalendarDiff diff = engine.compare("TEST", generated, blessed, cutoffDate);

    assertEquals(DiffSeverity.NONE, diff.severity());
    assertFalse(diff.hasChanges());
    assertEquals(0, diff.additions().size());
    assertEquals(0, diff.removals().size());
    assertEquals(0, diff.modifications().size());
  }

  @Test
  void futureAddition_returnsMinorSeverity() {
    List<Event> blessed =
        List.of(new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"));
    List<Event> generated =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(
                LocalDate.of(2027, 7, 4), EventType.CLOSED, "Independence Day", "test") // Future
            );

    CalendarDiff diff = engine.compare("TEST", generated, blessed, cutoffDate);

    assertEquals(DiffSeverity.MINOR, diff.severity());
    assertTrue(diff.hasChanges());
    assertEquals(1, diff.additions().size());
    assertEquals(0, diff.removals().size());
    assertEquals(LocalDate.of(2027, 7, 4), diff.additions().get(0).date());
  }

  @Test
  void historicalAddition_returnsMajorSeverity() {
    List<Event> blessed =
        List.of(new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"));
    List<Event> generated =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(
                LocalDate.of(2024, 7, 4),
                EventType.CLOSED,
                "Independence Day",
                "test") // Historical
            );

    CalendarDiff diff = engine.compare("TEST", generated, blessed, cutoffDate);

    assertEquals(DiffSeverity.MAJOR, diff.severity());
    assertTrue(diff.hasChanges());
    assertEquals(1, diff.additions().size());
    assertTrue(diff.additions().get(0).isHistorical(cutoffDate));
  }

  @Test
  void removal_returnsMajorSeverity() {
    List<Event> blessed =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(LocalDate.of(2025, 12, 25), EventType.CLOSED, "Christmas", "test"));
    List<Event> generated =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test")
            // Christmas removed
            );

    CalendarDiff diff = engine.compare("TEST", generated, blessed, cutoffDate);

    assertEquals(DiffSeverity.MAJOR, diff.severity());
    assertTrue(diff.hasChanges());
    assertEquals(0, diff.additions().size());
    assertEquals(1, diff.removals().size());
    assertEquals(LocalDate.of(2025, 12, 25), diff.removals().get(0).date());
  }

  @Test
  void modification_returnsMajorSeverity() {
    List<Event> blessed =
        List.of(new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"));
    List<Event> generated =
        List.of(
            new Event(
                LocalDate.of(2025, 1, 1), EventType.NOTABLE, "New Year's Day (Modified)", "test"));

    CalendarDiff diff = engine.compare("TEST", generated, blessed, cutoffDate);

    assertEquals(DiffSeverity.MAJOR, diff.severity());
    assertTrue(diff.hasChanges());
    assertEquals(0, diff.additions().size());
    assertEquals(0, diff.removals().size());
    assertEquals(1, diff.modifications().size());

    EventDiff mod = diff.modifications().get(0);
    assertEquals(EventType.CLOSED, mod.oldType());
    assertEquals(EventType.NOTABLE, mod.newType());
    assertEquals("New Year's Day", mod.oldDescription());
    assertEquals("New Year's Day (Modified)", mod.newDescription());
  }

  @Test
  void mixedChanges_returnsHighestSeverity() {
    List<Event> blessed =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(LocalDate.of(2025, 7, 4), EventType.CLOSED, "Independence Day", "test"));
    List<Event> generated =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            // Independence Day removed (MAJOR)
            new Event(
                LocalDate.of(2027, 12, 25),
                EventType.CLOSED,
                "Christmas",
                "test") // Future addition (MINOR)
            );

    CalendarDiff diff = engine.compare("TEST", generated, blessed, cutoffDate);

    // Should be MAJOR because removal is present
    assertEquals(DiffSeverity.MAJOR, diff.severity());
    assertEquals(1, diff.additions().size());
    assertEquals(1, diff.removals().size());
  }

  @Test
  void aggregateSeverity_returnsHighest() {
    CalendarDiff minor =
        new CalendarDiff("CAL1", DiffSeverity.MINOR, List.of(), List.of(), List.of(), cutoffDate);
    CalendarDiff none =
        new CalendarDiff("CAL2", DiffSeverity.NONE, List.of(), List.of(), List.of(), cutoffDate);
    CalendarDiff major =
        new CalendarDiff("CAL3", DiffSeverity.MAJOR, List.of(), List.of(), List.of(), cutoffDate);

    assertEquals(DiffSeverity.MAJOR, engine.aggregateSeverity(List.of(minor, none, major)));
    assertEquals(DiffSeverity.MINOR, engine.aggregateSeverity(List.of(minor, none)));
    assertEquals(DiffSeverity.NONE, engine.aggregateSeverity(List.of(none)));
  }

  @Test
  void emptyLists_returnsNone() {
    CalendarDiff diff = engine.compare("TEST", List.of(), List.of(), cutoffDate);

    assertEquals(DiffSeverity.NONE, diff.severity());
    assertFalse(diff.hasChanges());
  }
}
