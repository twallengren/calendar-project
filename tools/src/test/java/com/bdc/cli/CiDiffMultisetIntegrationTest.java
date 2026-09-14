package com.bdc.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * End-to-end cover for diff completeness: two events on one date must both be compared, duplicate
 * counts must survive, and the reported severity and exit code must follow.
 */
class CiDiffMultisetIntegrationTest {
  @TempDir Path temp;
  private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private PrintStream originalOut;
  private PrintStream originalErr;
  private static final String HEADER = "date,type,description\n";
  private static final String A = "2024-01-01,CLOSED,A\n";
  private static final String B = "2024-01-01,CLOSED,B\n";

  @BeforeEach
  void capture() {
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(stdout));
    System.setErr(new PrintStream(stderr));
  }

  @AfterEach
  void restore() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  private void fixture(String baseline) throws Exception {
    Files.createDirectories(temp.resolve("blessed/TEST"));
    Files.createDirectories(temp.resolve("calendars"));
    Files.createDirectories(temp.resolve("modules"));
    Files.writeString(
        temp.resolve("blessed/manifest.json"),
        """
        {"release_version":{"semantic":"test","git_sha":"test"},
         "calendars":{"TEST":{"range_start":"2024-01-01","range_end":"2024-01-01"}}}
        """);
    Files.writeString(
        temp.resolve("calendars/TEST.yaml"),
        """
        kind: calendar
        id: TEST
        event_sources:
          - key: a
            name: A
            rule:
              type: explicit_dates
              key: a
              name: A
              dates: [2024-01-01]
          - key: b
            name: B
            rule:
              type: explicit_dates
              key: b
              name: B
              dates: [2024-01-01]
        """);
    if (baseline != null) Files.writeString(temp.resolve("blessed/TEST/events.csv"), baseline);
    else Files.deleteIfExists(temp.resolve("blessed/TEST/events.csv"));
  }

  private int ci(String baseline, String format) throws Exception {
    fixture(baseline);
    stdout.reset();
    stderr.reset();
    return new CommandLine(new CiDiffCommand())
        .execute(
            "--blessed-dir",
            temp.resolve("blessed").toString(),
            "--calendars-dir",
            temp.resolve("calendars").toString(),
            "--modules-dir",
            temp.resolve("modules").toString(),
            "--output-format",
            format,
            "--current-sha",
            "test",
            "--cutoff-date",
            "2024-01-01");
  }

  private JsonNode calendar() throws Exception {
    return new ObjectMapper().readTree(stdout.toString()).path("calendars").path("TEST");
  }

  @Test
  void detectsPreviouslyHiddenSameDateChangesWithExactExitCodesAndReports() throws Exception {
    assertEquals(2, ci(HEADER + A, "json"), stderr.toString());
    assertEquals("MAJOR", calendar().path("severity").asText());
    assertEquals(1, calendar().path("additions").size());
    assertEquals("B", calendar().path("additions").get(0).path("new_description").asText());
    assertEquals(0, calendar().path("removals").size());

    assertEquals(2, ci(HEADER + A + B + B + B, "json"));
    assertEquals(2, calendar().path("removals").size());
    assertEquals(calendar().path("removals").get(0), calendar().path("removals").get(1));

    assertEquals(2, ci(HEADER + A + "2024-01-01,NOTABLE,C\n", "json"));
    assertEquals(1, calendar().path("modifications").size());
    assertEquals("C", calendar().path("modifications").get(0).path("old_description").asText());
    assertEquals("B", calendar().path("modifications").get(0).path("new_description").asText());

    assertEquals(2, ci(HEADER + A + B + B + B, "markdown"));
    assertTrue(
        stdout.toString().contains("| TEST | :red_circle: MAJOR | 0 | 2 | 0 |"), stdout.toString());
    assertEquals(
        2,
        stdout
            .toString()
            .lines()
            .filter(s -> s.equals("| 2024-01-01 | CLOSED | B | Yes |"))
            .count());
  }

  @Test
  void readsColumnsByNameAndKeepsMissingBaselineAndErrorBehavior() throws Exception {
    assertEquals(
        0,
        ci("description,type,date\n\"B\",CLOSED,2024-01-01\nA,CLOSED,2024-01-01\n", "json"),
        stderr.toString());
    assertEquals("NONE", calendar().path("severity").asText());

    assertEquals(1, ci(null, "json"));
    assertEquals("MINOR", calendar().path("severity").asText());
    assertEquals(2, calendar().path("additions").size());

    assertEquals(3, ci(HEADER + "2024-02-30,CLOSED,invalid\n", "json"));
    assertTrue(
        stderr.toString().contains("events.csv: Malformed events CSV at line 2"),
        stderr.toString());
  }
}
