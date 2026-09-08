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

class DiffMultisetIntegrationTest {
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

  private void artifact(String version, String csv) throws Exception {
    Path dir = temp.resolve("generated/TEST/2024-01-01_2024-12-31").resolve(version);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("events.csv"), csv);
  }

  private int generated(String before, String after) throws Exception {
    artifact("v1", before);
    artifact("v2", after);
    stdout.reset();
    stderr.reset();
    return new CommandLine(new DiffCommand())
        .execute(
            "generated",
            "TEST",
            "--tx1",
            "v1",
            "--tx2",
            "v2",
            "--valid-range",
            "2024",
            "--artifacts-dir",
            temp.toString());
  }

  @Test
  void generatedPreservesCountsAndPrintsEveryOccurrence() throws Exception {
    assertEquals(0, generated(HEADER + A + A + A + B, HEADER + A + B + B + B));
    String output = stdout.toString();
    assertTrue(output.contains("Events in version 1: 4"), output);
    assertTrue(output.contains("Events in version 2: 4"), output);
    assertTrue(output.contains("Added: 2"), output);
    assertTrue(output.contains("Removed: 2"), output);
    assertEquals(2, output.lines().filter(s -> s.equals("  - " + A.strip())).count());
    assertEquals(2, output.lines().filter(s -> s.equals("  + " + B.strip())).count());
    assertEquals(0, generated(HEADER + B + A + A + A, HEADER + B + A + B + B));
    assertEquals(output, stdout.toString());
  }

  @Test
  void generatedIgnoresQuotingAndOrderButPreservesMultilineRecordCounts() throws Exception {
    String multiline = "2024-01-01,CLOSED,\"name, \"\"quoted\"\"\nsecond line\"\n";
    assertEquals(
        0,
        generated(
            HEADER + A + multiline + multiline,
            "\uFEFF\"date\",type,description\n"
                + multiline
                + "\"2024-01-01\",CLOSED,\"A\"\n"
                + multiline));
    assertTrue(stdout.toString().contains("No differences found."));
    assertEquals(0, generated(HEADER + multiline + multiline, HEADER + multiline));
    assertTrue(stdout.toString().contains("Events in version 1: 2"));
    assertTrue(stdout.toString().contains("Events in version 2: 1"));
    assertTrue(stdout.toString().contains("Removed: 1"));
    assertTrue(stdout.toString().contains("  - " + multiline.strip()));
    assertEquals(0, generated(HEADER, HEADER));
    assertTrue(stdout.toString().contains("No differences found."));
  }

  @Test
  void generatedIncludesAlternateDatesAndRejectsIncompatibleHeadersOrMalformedRecords()
      throws Exception {
    String alternate = "date,hijri_date,type,description\n";
    assertEquals(
        0,
        generated(
            alternate + "2024-01-01,1445-06-19,CLOSED,A\n",
            alternate + "2024-01-01,1445-06-20,CLOSED,A\n"));
    assertTrue(stdout.toString().contains("Added: 1"));
    assertTrue(stdout.toString().contains("Removed: 1"));
    assertEquals(1, generated(HEADER + A, alternate + "2024-01-01,1445-06-19,CLOSED,A\n"));
    assertTrue(stderr.toString().contains("Incompatible CSV headers"));
    assertEquals(1, generated(HEADER + A, HEADER + "2024-01-01,CLOSED\n"));
    assertTrue(stderr.toString().contains("events.csv: CSV record 2"));
  }

  private void ciFixture(String baseline) throws Exception {
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
    ciFixture(baseline);
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
  void ciDetectsPreviouslyHiddenSameDateChangesWithExactExitCodesAndReports() throws Exception {
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
  void ciReadsColumnsByNameAndKeepsMissingBaselineAndErrorBehavior() throws Exception {
    assertEquals(
        0,
        ci(
            "description,type,date,hijri_date\n\"B\",CLOSED,2024-01-01,ignored\nA,CLOSED,2024-01-01,\n",
            "json"));
    assertEquals("NONE", calendar().path("severity").asText());
    assertEquals(1, ci(null, "json"));
    assertEquals("MINOR", calendar().path("severity").asText());
    assertEquals(2, calendar().path("additions").size());
    assertEquals(3, ci(HEADER + "2024-02-30,CLOSED,invalid\n", "json"));
    assertTrue(
        stderr.toString().contains("events.csv: CSV record 2: invalid ISO date"),
        stderr.toString());
  }
}
