package com.bdc.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventSourceTest {

  @Test
  void isActiveOn_withActiveYears_singleYear_matchesYear() {
    EventSource source =
        new EventSource(
            "test",
            "Test",
            null,
            EventType.CLOSED,
            false,
            List.of(new EventSource.YearRange(2020, 2020)));

    assertTrue(source.isActiveOn(LocalDate.of(2020, 6, 15)));
    assertFalse(source.isActiveOn(LocalDate.of(2019, 6, 15)));
    assertFalse(source.isActiveOn(LocalDate.of(2021, 6, 15)));
  }

  @Test
  void isActiveOn_withActiveYears_multipleYears() {
    // active_years: [1972, 1976, 1980]
    EventSource source =
        new EventSource(
            "test",
            "Test",
            null,
            EventType.CLOSED,
            false,
            List.of(
                new EventSource.YearRange(1972, 1972),
                new EventSource.YearRange(1976, 1976),
                new EventSource.YearRange(1980, 1980)));

    assertTrue(source.isActiveOn(LocalDate.of(1972, 11, 7)));
    assertTrue(source.isActiveOn(LocalDate.of(1976, 11, 2)));
    assertTrue(source.isActiveOn(LocalDate.of(1980, 11, 4)));
    assertFalse(source.isActiveOn(LocalDate.of(1974, 11, 5)));
    assertFalse(source.isActiveOn(LocalDate.of(1968, 11, 5)));
  }

  @Test
  void isActiveOn_withActiveYears_openEndedRange_fromInception() {
    // active_years: [[null, 1968]] - from inception through 1968
    EventSource source =
        new EventSource(
            "test",
            "Test",
            null,
            EventType.CLOSED,
            false,
            List.of(new EventSource.YearRange(null, 1968)));

    assertTrue(source.isActiveOn(LocalDate.of(1960, 11, 8)));
    assertTrue(source.isActiveOn(LocalDate.of(1965, 11, 2)));
    assertTrue(source.isActiveOn(LocalDate.of(1968, 11, 5)));
    assertFalse(source.isActiveOn(LocalDate.of(1969, 11, 4)));
    assertFalse(source.isActiveOn(LocalDate.of(1972, 11, 7)));
  }

  @Test
  void isActiveOn_withActiveYears_openEndedRange_toFuture() {
    // active_years: [[2020, null]] - from 2020 onwards
    EventSource source =
        new EventSource(
            "test",
            "Test",
            null,
            EventType.CLOSED,
            false,
            List.of(new EventSource.YearRange(2020, null)));

    assertFalse(source.isActiveOn(LocalDate.of(2019, 6, 15)));
    assertTrue(source.isActiveOn(LocalDate.of(2020, 6, 15)));
    assertTrue(source.isActiveOn(LocalDate.of(2025, 6, 15)));
    assertTrue(source.isActiveOn(LocalDate.of(2050, 6, 15)));
  }

  @Test
  void isActiveOn_withActiveYears_mixedRangesAndYears() {
    // active_years: [[null, 1968], 1972, 1976, 1980]
    // This is the Election Day pattern
    EventSource source =
        new EventSource(
            "test",
            "Test",
            null,
            EventType.CLOSED,
            false,
            List.of(
                new EventSource.YearRange(null, 1968),
                new EventSource.YearRange(1972, 1972),
                new EventSource.YearRange(1976, 1976),
                new EventSource.YearRange(1980, 1980)));

    // Should be active through 1968
    assertTrue(source.isActiveOn(LocalDate.of(1960, 11, 8)));
    assertTrue(source.isActiveOn(LocalDate.of(1968, 11, 5)));

    // Should be active only on specific years after 1968
    assertFalse(source.isActiveOn(LocalDate.of(1969, 11, 4)));
    assertFalse(source.isActiveOn(LocalDate.of(1970, 11, 3)));
    assertTrue(source.isActiveOn(LocalDate.of(1972, 11, 7)));
    assertFalse(source.isActiveOn(LocalDate.of(1974, 11, 5)));
    assertTrue(source.isActiveOn(LocalDate.of(1976, 11, 2)));
    assertFalse(source.isActiveOn(LocalDate.of(1978, 11, 7)));
    assertTrue(source.isActiveOn(LocalDate.of(1980, 11, 4)));
    assertFalse(source.isActiveOn(LocalDate.of(1984, 11, 6)));
  }

  @Test
  void isActiveOn_withActiveYears_closedRange() {
    // active_years: [[1990, 2000]]
    EventSource source =
        new EventSource(
            "test",
            "Test",
            null,
            EventType.CLOSED,
            false,
            List.of(new EventSource.YearRange(1990, 2000)));

    assertFalse(source.isActiveOn(LocalDate.of(1989, 6, 15)));
    assertTrue(source.isActiveOn(LocalDate.of(1990, 6, 15)));
    assertTrue(source.isActiveOn(LocalDate.of(1995, 6, 15)));
    assertTrue(source.isActiveOn(LocalDate.of(2000, 6, 15)));
    assertFalse(source.isActiveOn(LocalDate.of(2001, 6, 15)));
  }

  @Test
  void isActiveOn_noConstraints_alwaysActive() {
    EventSource source = new EventSource("test", "Test", null, EventType.CLOSED, false, null);

    assertTrue(source.isActiveOn(LocalDate.of(1900, 1, 1)));
    assertTrue(source.isActiveOn(LocalDate.of(2024, 6, 15)));
    assertTrue(source.isActiveOn(LocalDate.of(3000, 12, 31)));
  }

  @Test
  void yearRange_contains_singleYear() {
    EventSource.YearRange range = new EventSource.YearRange(2020);

    assertFalse(range.contains(2019));
    assertTrue(range.contains(2020));
    assertFalse(range.contains(2021));
  }

  @Test
  void yearRange_contains_nullStart() {
    EventSource.YearRange range = new EventSource.YearRange(null, 1968);

    assertTrue(range.contains(1900));
    assertTrue(range.contains(1968));
    assertFalse(range.contains(1969));
  }

  @Test
  void yearRange_contains_nullEnd() {
    EventSource.YearRange range = new EventSource.YearRange(2020, null);

    assertFalse(range.contains(2019));
    assertTrue(range.contains(2020));
    assertTrue(range.contains(3000));
  }
}
