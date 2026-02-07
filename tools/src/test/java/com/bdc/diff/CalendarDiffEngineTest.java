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
  // Blessed range: 2020-01-01 to 2026-12-31
  private LocalDate blessedRangeStart;
  private LocalDate blessedRangeEnd;

  @BeforeEach
  void setUp() {
    engine = new CalendarDiffEngine();
    cutoffDate = LocalDate.of(2026, 1, 22); // "Today" for tests
    blessedRangeStart = LocalDate.of(2020, 1, 1);
    blessedRangeEnd = LocalDate.of(2026, 12, 31);
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

    CalendarDiff diff =
        engine.compare("TEST", generated, blessed, cutoffDate, blessedRangeStart, blessedRangeEnd);

    assertEquals(DiffSeverity.NONE, diff.severity());
    assertFalse(diff.hasChanges());
    assertEquals(0, diff.additions().size());
    assertEquals(0, diff.removals().size());
    assertEquals(0, diff.modifications().size());
  }

  @Test
  void futureAddition_outsideBlessedRange_returnsMinorSeverity() {
    List<Event> blessed =
        List.of(new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"));
    List<Event> generated =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(
                LocalDate.of(2027, 7, 4),
                EventType.CLOSED,
                "Independence Day",
                "test") // After blessed range end
            );

    CalendarDiff diff =
        engine.compare("TEST", generated, blessed, cutoffDate, blessedRangeStart, blessedRangeEnd);

    assertEquals(DiffSeverity.MINOR, diff.severity());
    assertTrue(diff.hasChanges());
    assertEquals(1, diff.additions().size());
    assertEquals(0, diff.removals().size());
    assertEquals(LocalDate.of(2027, 7, 4), diff.additions().get(0).date());
  }

  @Test
  void backfillAddition_beforeBlessedRange_returnsMinorSeverity() {
    // Adding events before the blessed range start (backfilling) should be MINOR
    List<Event> blessed =
        List.of(new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"));
    List<Event> generated =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(
                LocalDate.of(2019, 7, 4),
                EventType.CLOSED,
                "Independence Day 2019",
                "test") // Before blessed range start
            );

    CalendarDiff diff =
        engine.compare("TEST", generated, blessed, cutoffDate, blessedRangeStart, blessedRangeEnd);

    assertEquals(DiffSeverity.MINOR, diff.severity());
    assertTrue(diff.hasChanges());
    assertEquals(1, diff.additions().size());
    assertEquals(LocalDate.of(2019, 7, 4), diff.additions().get(0).date());
  }

  @Test
  void additionWithinBlessedRange_returnsMajorSeverity() {
    // Adding events within the existing blessed range is MAJOR (unexpected change)
    List<Event> blessed =
        List.of(new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"));
    List<Event> generated =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(
                LocalDate.of(2024, 7, 4),
                EventType.CLOSED,
                "Independence Day",
                "test") // Within blessed range
            );

    CalendarDiff diff =
        engine.compare("TEST", generated, blessed, cutoffDate, blessedRangeStart, blessedRangeEnd);

    assertEquals(DiffSeverity.MAJOR, diff.severity());
    assertTrue(diff.hasChanges());
    assertEquals(1, diff.additions().size());
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

    CalendarDiff diff =
        engine.compare("TEST", generated, blessed, cutoffDate, blessedRangeStart, blessedRangeEnd);

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

    CalendarDiff diff =
        engine.compare("TEST", generated, blessed, cutoffDate, blessedRangeStart, blessedRangeEnd);

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
                "test") // Future addition outside range (MINOR)
            );

    CalendarDiff diff =
        engine.compare("TEST", generated, blessed, cutoffDate, blessedRangeStart, blessedRangeEnd);

    // Should be MAJOR because removal is present
    assertEquals(DiffSeverity.MAJOR, diff.severity());
    assertEquals(1, diff.additions().size());
    assertEquals(1, diff.removals().size());
  }

  @Test
  void aggregateSeverity_returnsHighest() {
    CalendarDiff minor =
        new CalendarDiff(
            "CAL1",
            DiffSeverity.MINOR,
            List.of(),
            List.of(),
            List.of(),
            cutoffDate,
            blessedRangeStart,
            blessedRangeEnd);
    CalendarDiff none =
        new CalendarDiff(
            "CAL2",
            DiffSeverity.NONE,
            List.of(),
            List.of(),
            List.of(),
            cutoffDate,
            blessedRangeStart,
            blessedRangeEnd);
    CalendarDiff major =
        new CalendarDiff(
            "CAL3",
            DiffSeverity.MAJOR,
            List.of(),
            List.of(),
            List.of(),
            cutoffDate,
            blessedRangeStart,
            blessedRangeEnd);

    assertEquals(DiffSeverity.MAJOR, engine.aggregateSeverity(List.of(minor, none, major)));
    assertEquals(DiffSeverity.MINOR, engine.aggregateSeverity(List.of(minor, none)));
    assertEquals(DiffSeverity.NONE, engine.aggregateSeverity(List.of(none)));
  }

  @Test
  void emptyLists_returnsNone() {
    CalendarDiff diff =
        engine.compare(
            "TEST", List.of(), List.of(), cutoffDate, blessedRangeStart, blessedRangeEnd);

    assertEquals(DiffSeverity.NONE, diff.severity());
    assertFalse(diff.hasChanges());
  }

  @Test
  void backfillMultipleYears_returnsMinorSeverity() {
    // Backfilling a large range of historical data should be MINOR
    List<Event> blessed =
        List.of(new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"));
    List<Event> generated =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            // Multiple backfilled events before blessed range
            new Event(LocalDate.of(2019, 1, 1), EventType.CLOSED, "New Year's Day 2019", "test"),
            new Event(LocalDate.of(2018, 1, 1), EventType.CLOSED, "New Year's Day 2018", "test"),
            new Event(LocalDate.of(1900, 1, 1), EventType.CLOSED, "New Year's Day 1900", "test"));

    CalendarDiff diff =
        engine.compare("TEST", generated, blessed, cutoffDate, blessedRangeStart, blessedRangeEnd);

    assertEquals(DiffSeverity.MINOR, diff.severity());
    assertEquals(3, diff.additions().size());
  }

  @Test
  void mixedBackfillAndWithinRange_returnsMajorSeverity() {
    // If there's any addition within the existing range, it's MAJOR even with backfills
    List<Event> blessed =
        List.of(new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"));
    List<Event> generated =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(
                LocalDate.of(2019, 7, 4),
                EventType.CLOSED,
                "Independence Day 2019",
                "test"), // Before range (MINOR)
            new Event(
                LocalDate.of(2024, 7, 4),
                EventType.CLOSED,
                "Independence Day 2024",
                "test") // Within range (MAJOR)
            );

    CalendarDiff diff =
        engine.compare("TEST", generated, blessed, cutoffDate, blessedRangeStart, blessedRangeEnd);

    assertEquals(DiffSeverity.MAJOR, diff.severity());
    assertEquals(2, diff.additions().size());
  }
}
