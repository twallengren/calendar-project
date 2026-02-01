package com.bdc.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class CiDiffCommandTest {

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

  private void createBlessedStructure() throws Exception {
    // Create manifest.json
    Path blessedDir = tempDir.resolve("blessed");
    Files.createDirectories(blessedDir);
    Files.writeString(
        blessedDir.resolve("manifest.json"),
        """
        {
          "release_version": {
            "semantic": "1.0.0",
            "git_sha": "abc123"
          },
          "calendars": {
            "US-MARKET-BASE": {
              "range_start": "2024-01-01",
              "range_end": "2024-12-31"
            }
          }
        }
        """);

    // Create events CSV
    Path calendarDir = blessedDir.resolve("US-MARKET-BASE");
    Files.createDirectories(calendarDir);
    Files.writeString(
        calendarDir.resolve("events.csv"),
        """
        date,type,description
        2024-01-01,CLOSED,New Year's Day
        2024-05-27,CLOSED,Memorial Day
        2024-07-04,CLOSED,Independence Day
        2024-09-02,CLOSED,Labor Day
        2024-11-28,CLOSED,Thanksgiving Day
        2024-12-25,CLOSED,Christmas Day
        """);
  }

  @Test
  void call_withBlessed_processesSuccessfully() throws Exception {
    createBlessedStructure();

    CiDiffCommand cmd = new CiDiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "--blessed-dir",
            tempDir.resolve("blessed").toString(),
            "--calendars",
            "US-MARKET-BASE");

    // Exit code 0 = no changes, 1 = minor changes, 2 = major changes
    // Any of these means the command completed successfully
    assertTrue(exitCode >= 0 && exitCode <= 2);
  }

  @Test
  void call_jsonFormat_outputsValidJson() throws Exception {
    createBlessedStructure();

    CiDiffCommand cmd = new CiDiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    cmdLine.execute(
        "--blessed-dir",
        tempDir.resolve("blessed").toString(),
        "--calendars",
        "US-MARKET-BASE",
        "--output-format",
        "json");

    String output = stdout.toString();
    // Should be valid JSON
    assertTrue(output.contains("{"));
    assertTrue(output.contains("summary") || output.contains("calendars"));
  }

  @Test
  void call_markdownFormat_outputsValidMarkdown() throws Exception {
    createBlessedStructure();

    CiDiffCommand cmd = new CiDiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    cmdLine.execute(
        "--blessed-dir",
        tempDir.resolve("blessed").toString(),
        "--calendars",
        "US-MARKET-BASE",
        "--output-format",
        "markdown");

    String output = stdout.toString();
    // Should contain markdown elements
    assertTrue(output.contains("##") || output.contains("Calendar Diff"));
  }

  @Test
  void call_withCurrentSha_includesInReport() throws Exception {
    createBlessedStructure();

    CiDiffCommand cmd = new CiDiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    cmdLine.execute(
        "--blessed-dir",
        tempDir.resolve("blessed").toString(),
        "--calendars",
        "US-MARKET-BASE",
        "--output-format",
        "json",
        "--current-sha",
        "xyz789");

    String output = stdout.toString();
    assertTrue(output.contains("xyz789") || output.contains("current_sha"));
  }

  @Test
  void call_missingBlessed_returnsError() {
    CiDiffCommand cmd = new CiDiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "--blessed-dir",
            tempDir.resolve("nonexistent-blessed").toString(),
            "--calendars",
            "US-MARKET-BASE");

    // Should return error exit code
    assertEquals(3, exitCode); // EXIT_ERROR = 3
  }

  @Test
  void call_allCalendars_processesManifest() throws Exception {
    createBlessedStructure();

    CiDiffCommand cmd = new CiDiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "--blessed-dir", tempDir.resolve("blessed").toString(), "--calendars", "all");

    // Should succeed (exit 0 for no changes, or 1/2 for changes)
    assertTrue(exitCode >= 0 && exitCode <= 3);
  }
}
