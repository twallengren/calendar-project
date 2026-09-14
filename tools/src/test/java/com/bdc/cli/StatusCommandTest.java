package com.bdc.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StatusCommandTest {

  @TempDir Path tempDir;

  private ByteArrayOutputStream stdout;
  private ByteArrayOutputStream stderr;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void setUp() {
    stdout = new ByteArrayOutputStream();
    stderr = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(stdout));
    System.setErr(new PrintStream(stderr));
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  private Path blessedDir;
  private Path sourcesDir;

  private void createBlessedStructure() throws Exception {
    blessedDir = tempDir.resolve("blessed");
    Files.createDirectories(blessedDir);
    Files.writeString(
        blessedDir.resolve("manifest.json"),
        """
        {
          "schema_version": "1.0",
          "blessed_at": "2026-01-01T00:00:00Z",
          "blessed_by": "test",
          "calendars": {
            "TEST-MARKET": {
              "kind": "market",
              "range_start": "2020-01-01",
              "range_end": "2030-12-31",
              "event_count": 10
            },
            "TEST-BASE": {
              "kind": "base",
              "range_start": "2020-01-01",
              "range_end": "2030-12-31",
              "event_count": 5
            }
          },
          "release_version": { "semantic": "1.2.3", "git_sha": "abc123" }
        }
        """);

    Path marketDir = blessedDir.resolve("TEST-MARKET");
    Files.createDirectories(marketDir);
    Files.writeString(
        marketDir.resolve("metadata.json"),
        """
        {
          "calendar_id": "TEST-MARKET",
          "calendar_name": "Test Market Calendar",
          "kind": "market",
          "timezone": "America/New_York",
          "coverage": { "from": "2020-01-01", "to": "2030-12-31", "verified_through": "2026-12-31" },
          "event_count": 10,
          "counts_by_type": { "CLOSED": 6, "EARLY_CLOSE": 2, "WEEKEND": 2 },
          "counts_by_status": { "CONFIRMED": 9, "PROJECTED": 1 }
        }
        """);
    Files.writeString(
        marketDir.resolve("cross_validation.json"),
        """
        {
          "calendar_id": "TEST-MARKET",
          "status": "discrepancies",
          "results": [
            { "source": "some-lib", "status": "discrepancies", "counts": { "matched": 5, "allowlisted": 1, "unexplained": 2 } }
          ]
        }
        """);

    Path baseDir = blessedDir.resolve("TEST-BASE");
    Files.createDirectories(baseDir);
    Files.writeString(
        baseDir.resolve("metadata.json"),
        """
        {
          "calendar_id": "TEST-BASE",
          "calendar_name": "Test Base Calendar",
          "kind": "base",
          "timezone": "America/New_York",
          "coverage": { "from": "2020-01-01", "to": "2030-12-31", "verified_through": "2026-12-31" },
          "event_count": 5,
          "counts_by_type": { "CLOSED": 5 },
          "counts_by_status": { "CONFIRMED": 5 }
        }
        """);

    sourcesDir = tempDir.resolve("sources");
    Path marketSources = sourcesDir.resolve("TEST-MARKET");
    Files.createDirectories(marketSources);
    Files.writeString(
        marketSources.resolve("README.md"),
        """
        # TEST-MARKET sources

        | id | title | publisher | url / file | retrieved | covers | notes |
        |----|-------|-----------|------------|-----------|--------|-------|
        | `test-source-1` | Test Source One | Test Publisher | test.txt | 2026-01-01 | 2020-2030 | note |
        | `test-source-2` | Test Source Two | Test Publisher | test2.txt | 2026-01-01 | 2020-2030 | note |
        """);
    // TEST-BASE has no sources/ directory of its own.
  }

  @Test
  void call_markdown_listsMarketsFirstThenBase() throws Exception {
    createBlessedStructure();

    StatusCommand cmd = new StatusCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "--blessed-dir",
            blessedDir.toString(),
            "--sources-dir",
            sourcesDir.toString(),
            "--format",
            "markdown");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("| ID | Name | Kind"));
    assertTrue(output.contains("TEST-MARKET"));
    assertTrue(output.contains("TEST-BASE"));
    assertTrue(output.contains("America/New_York"));
    assertTrue(output.contains("1.2.3"));
    // Markets before base.
    assertTrue(output.indexOf("TEST-MARKET") < output.indexOf("TEST-BASE"));
    // Source count and cross-validation summary show up.
    assertTrue(output.contains("some-lib: discrepancies"));
  }

  @Test
  void call_json_includesCountsAndSources() throws Exception {
    createBlessedStructure();

    StatusCommand cmd = new StatusCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "--blessed-dir",
            blessedDir.toString(),
            "--sources-dir",
            sourcesDir.toString(),
            "--format",
            "json");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("\"id\" : \"TEST-MARKET\""));
    assertTrue(output.contains("\"closures\" : 6"));
    assertTrue(output.contains("\"count\" : 2"));
    assertTrue(output.contains("test-source-1"));
    assertTrue(output.contains("\"release_version\" : \"1.2.3\""));
  }

  @Test
  void call_calendarWithNoCrossValidationFile_showsNone() throws Exception {
    createBlessedStructure();

    StatusCommand cmd = new StatusCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    cmdLine.execute(
        "--blessed-dir",
        blessedDir.toString(),
        "--sources-dir",
        sourcesDir.toString(),
        "--format",
        "markdown");

    String output = stdout.toString();
    // TEST-BASE row has no cross_validation.json and no sources/ dir.
    String baseRow = output.lines().filter(l -> l.contains("TEST-BASE")).findFirst().orElseThrow();
    assertTrue(baseRow.contains("none"));
  }

  @Test
  void call_missingManifest_returnsError() {
    StatusCommand cmd = new StatusCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("--blessed-dir", tempDir.resolve("nonexistent").toString());

    assertEquals(1, exitCode);
    assertTrue(stderr.toString().contains("manifest not found"));
  }

  /**
   * A base calendar with its own citation, and a market calendar that {@code extends} it and adds
   * no citations of its own: the extending calendar must report the inherited citation instead of
   * zero, resolved via {@code --calendars-dir}/{@code --modules-dir} rather than by looking for a
   * {@code sources/<extending-calendar>/README.md} that doesn't exist.
   */
  private Path calendarsDir;

  private Path modulesDir;

  private void createExtendsFixture() throws Exception {
    blessedDir = tempDir.resolve("blessed");
    Files.createDirectories(blessedDir);
    Files.writeString(
        blessedDir.resolve("manifest.json"),
        """
        {
          "schema_version": "1.0",
          "blessed_at": "2026-01-01T00:00:00Z",
          "blessed_by": "test",
          "calendars": {
            "TEST-BASE": {
              "kind": "base",
              "range_start": "2020-01-01",
              "range_end": "2030-12-31",
              "event_count": 1
            },
            "TEST-EXTENDING-MARKET": {
              "kind": "market",
              "range_start": "2020-01-01",
              "range_end": "2030-12-31",
              "event_count": 1
            }
          },
          "release_version": { "semantic": "1.2.3", "git_sha": "abc123" }
        }
        """);

    for (String calId : List.of("TEST-BASE", "TEST-EXTENDING-MARKET")) {
      Path calDir = blessedDir.resolve(calId);
      Files.createDirectories(calDir);
      Files.writeString(
          calDir.resolve("metadata.json"),
          """
          {
            "calendar_id": "%s",
            "calendar_name": "%s",
            "kind": "%s",
            "timezone": "America/New_York",
            "coverage": { "from": "2020-01-01", "to": "2030-12-31", "verified_through": "2026-12-31" },
            "event_count": 1,
            "counts_by_type": { "CLOSED": 1 },
            "counts_by_status": { "CONFIRMED": 1 }
          }
          """
              .formatted(calId, calId, calId.equals("TEST-BASE") ? "base" : "market"));
    }

    calendarsDir = tempDir.resolve("calendars");
    Files.createDirectories(calendarsDir);
    Files.writeString(
        calendarsDir.resolve("TEST-BASE.yaml"),
        """
        kind: calendar
        id: TEST-BASE
        metadata:
          name: Test Base Calendar
          kind: base
          timezone: America/New_York
          coverage:
            from: 2020-01-01
            to: 2030-12-31
        uses:
          - test_shared_holiday
        """);
    Files.writeString(
        calendarsDir.resolve("TEST-EXTENDING-MARKET.yaml"),
        """
        kind: calendar
        id: TEST-EXTENDING-MARKET
        metadata:
          name: Test Extending Market Calendar
          kind: market
          timezone: America/New_York
          coverage:
            from: 2020-01-01
            to: 2030-12-31
        extends:
          - TEST-BASE
        """);

    modulesDir = tempDir.resolve("modules");
    Files.createDirectories(modulesDir);
    Files.writeString(
        modulesDir.resolve("test_shared_holiday.yaml"),
        """
        kind: module
        id: test_shared_holiday
        event_sources:
          - key: test_shared_holiday
            name: Test Shared Holiday
            default_classification: CLOSED
            source:
              - id: shared-source-1
            rule:
              type: fixed_month_day
              month: 7
              day: 4
        """);

    sourcesDir = tempDir.resolve("sources");
    Path baseSources = sourcesDir.resolve("TEST-BASE");
    Files.createDirectories(baseSources);
    Files.writeString(
        baseSources.resolve("README.md"),
        """
        # TEST-BASE sources

        | id | title | publisher | url / file | retrieved | covers | notes |
        |----|-------|-----------|------------|-----------|--------|-------|
        | `shared-source-1` | Shared Source | Test Publisher | test.txt | 2026-01-01 | 2020-2030 | note |
        """);
    // TEST-EXTENDING-MARKET has no sources/ directory of its own.
  }

  @Test
  void call_json_extendingCalendarReportsInheritedSourceViaResolver() throws Exception {
    createExtendsFixture();

    StatusCommand cmd = new StatusCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "--blessed-dir",
            blessedDir.toString(),
            "--sources-dir",
            sourcesDir.toString(),
            "--calendars-dir",
            calendarsDir.toString(),
            "--modules-dir",
            modulesDir.toString(),
            "--format",
            "json");

    assertEquals(0, exitCode, stderr.toString());
    String output = stdout.toString();
    assertTrue(output.contains("\"id\" : \"TEST-EXTENDING-MARKET\""));
    // The extending calendar has no sources/ dir of its own, but resolves the base's citation.
    String extendingSection =
        output.substring(output.indexOf("\"id\" : \"TEST-EXTENDING-MARKET\""));
    assertTrue(extendingSection.contains("\"count\" : 1"));
    assertTrue(extendingSection.contains("shared-source-1"));
    assertTrue(extendingSection.contains("\"sources_basis\" : \"resolved\""));
    assertTrue(extendingSection.contains("sources/TEST-BASE/README.md"));
    assertTrue(extendingSection.contains("\"unresolved\" : [ ]"));
  }

  @Test
  void call_markdown_unresolvedCitationIsFlagged() throws Exception {
    createExtendsFixture();
    // Add a citation id to the base module that no README documents.
    Files.writeString(
        modulesDir.resolve("test_shared_holiday.yaml"),
        """
        kind: module
        id: test_shared_holiday
        event_sources:
          - key: test_shared_holiday
            name: Test Shared Holiday
            default_classification: CLOSED
            source:
              - id: shared-source-1
              - id: undocumented-source
            rule:
              type: fixed_month_day
              month: 7
              day: 4
        """);

    StatusCommand cmd = new StatusCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "--blessed-dir",
            blessedDir.toString(),
            "--sources-dir",
            sourcesDir.toString(),
            "--calendars-dir",
            calendarsDir.toString(),
            "--modules-dir",
            modulesDir.toString(),
            "--format",
            "markdown");

    assertEquals(0, exitCode, stderr.toString());
    String marketRow =
        stdout
            .toString()
            .lines()
            .filter(l -> l.contains("TEST-EXTENDING-MARKET"))
            .findFirst()
            .orElseThrow();
    assertTrue(marketRow.contains("2 (1 unresolved)"));
    assertTrue(stderr.toString().contains("TEST-EXTENDING-MARKET"));
    assertTrue(stderr.toString().contains("undocumented-source"));
  }
}
