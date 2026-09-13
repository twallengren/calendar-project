package com.bdc.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.WeekendPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossValidatorTest {

  @TempDir Path tempDir;

  private Event closed(String date) {
    return new Event(LocalDate.parse(date), EventType.CLOSED, "Test Closure", "test");
  }

  private Path writeReference(String... rows) throws Exception {
    Path referenceFile = tempDir.resolve("test-source.csv");
    StringBuilder sb = new StringBuilder();
    sb.append("# generated-by: test-lib 1.0, 2026-01-01\n");
    sb.append("# columns: date,type,close_time\n");
    sb.append("date,type,close_time\n");
    for (String row : rows) {
      sb.append(row).append('\n');
    }
    Files.writeString(referenceFile, sb.toString());
    return referenceFile;
  }

  private void writeAllowlist(String... rows) throws Exception {
    StringBuilder sb = new StringBuilder("source,side,date,type,reason\n");
    for (String row : rows) {
      sb.append(row).append('\n');
    }
    Files.writeString(tempDir.resolve("allowlist.csv"), sb.toString());
  }

  @Test
  void matchedAllowlistedUnexplainedAndStale() throws Exception {
    // Weekdays only: 2024-01-01 Mon, 01-03 Wed, 01-04 Thu, 01-05 Fri, 01-08 Mon, 01-09 Tue.
    Path referenceFile =
        writeReference("2024-01-01,CLOSED,", "2024-01-05,CLOSED,", "2024-01-08,CLOSED,");
    writeAllowlist(
        "test-source,ours,2024-01-04,CLOSED,explained ours-only",
        "test-source,theirs,2024-01-08,CLOSED,explained theirs-only",
        "test-source,ours,2024-01-09,CLOSED,stale - no longer differs");

    List<Event> ours = List.of(closed("2024-01-01"), closed("2024-01-03"), closed("2024-01-04"));

    CrossValidationResult result =
        new CrossValidator().compare("TEST-CAL", ours, WeekendPolicy.SAT_SUN, referenceFile);

    assertEquals("TEST-CAL", result.calendarId());
    assertEquals("test-source", result.source());
    assertEquals("test-lib 1.0, 2026-01-01", result.libraryVersion());
    assertEquals(LocalDate.of(2024, 1, 1), result.from());
    assertEquals(LocalDate.of(2024, 1, 8), result.to());
    assertEquals(1, result.matchedCount(), "01-01 matches on both sides");
    assertEquals(2, result.allowlistedCount(), "01-04 (ours) and 01-08 (theirs) are allowlisted");
    assertEquals(2, result.unexplainedRows().size(), "01-03 ours-only and 01-05 theirs-only");
    assertTrue(result.unexplainedRows().stream().anyMatch(r -> r.contains("2024-01-03")));
    assertTrue(result.unexplainedRows().stream().anyMatch(r -> r.contains("2024-01-05")));
    assertEquals(1, result.staleAllowlistRows().size());
    assertTrue(result.staleAllowlistRows().get(0).contains("2024-01-09"));
    assertFalse(result.isClean());
    assertEquals("discrepancies", result.status());
  }

  @Test
  void cleanWhenEverythingMatches() throws Exception {
    Path referenceFile = writeReference("2024-01-01,CLOSED,");
    List<Event> ours = List.of(closed("2024-01-01"));

    CrossValidationResult result =
        new CrossValidator().compare("TEST-CAL", ours, WeekendPolicy.SAT_SUN, referenceFile);

    assertEquals(1, result.matchedCount());
    assertEquals(0, result.allowlistedCount());
    assertTrue(result.unexplainedRows().isEmpty());
    assertTrue(result.staleAllowlistRows().isEmpty());
    assertTrue(result.isClean());
    assertEquals("ok", result.status());
  }

  @Test
  void weekendRowsAreExcludedOnBothSides() throws Exception {
    // 2024-01-06 is a Saturday.
    Path referenceFile = writeReference("2024-01-01,CLOSED,", "2024-01-06,CLOSED,");
    List<Event> ours = List.of(closed("2024-01-01"), closed("2024-01-06"));

    CrossValidationResult result =
        new CrossValidator().compare("TEST-CAL", ours, WeekendPolicy.SAT_SUN, referenceFile);

    assertEquals(1, result.matchedCount());
    assertTrue(result.isClean());
  }

  @Test
  void emptyReferenceFileYieldsEmptyCleanResult() throws Exception {
    Path referenceFile = tempDir.resolve("empty-source.csv");
    Files.writeString(referenceFile, "date,type,close_time\n");

    CrossValidationResult result =
        new CrossValidator()
            .compare(
                "TEST-CAL", List.of(closed("2024-01-01")), WeekendPolicy.SAT_SUN, referenceFile);

    assertTrue(result.isClean());
    assertEquals(0, result.matchedCount());
    assertNull(result.from());
    assertNull(result.to());
  }
}
