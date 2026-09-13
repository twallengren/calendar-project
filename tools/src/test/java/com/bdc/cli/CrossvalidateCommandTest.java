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

class CrossvalidateCommandTest {

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

  /**
   * A reference file for the real US-MARKET-BASE calendar (loaded from the repo's own
   * calendars/modules): New Year's Day 2024-01-01 is a genuine CLOSED event.
   */
  private Path writeReference(String calendarId, String fileName, String range, String... extraRows)
      throws Exception {
    Path dir = tempDir.resolve("reference").resolve(calendarId);
    Files.createDirectories(dir);
    Path file = dir.resolve(fileName);
    StringBuilder sb = new StringBuilder();
    sb.append("# generated-by: test-lib 1.0, 2026-01-01\n");
    sb.append("# columns: date,type,close_time\n");
    sb.append("# range: ").append(range).append('\n');
    sb.append("# types: CLOSED\n");
    sb.append("date,type,close_time\n");
    sb.append("2024-01-01,CLOSED,\n");
    for (String row : extraRows) {
      sb.append(row).append('\n');
    }
    Files.writeString(file, sb.toString());
    return dir;
  }

  @Test
  void call_matchingReference_returnsZeroAndOk() throws Exception {
    writeReference("US-MARKET-BASE", "testlib.csv", "2024-01-01 to 2024-01-01");

    CrossvalidateCommand cmd = new CrossvalidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "US-MARKET-BASE", "--reference-dir", tempDir.resolve("reference").toString());

    assertEquals(0, exitCode);
    assertTrue(stdout.toString().contains("ok"));
  }

  @Test
  void call_discrepancy_returnsOneAndListsIt() throws Exception {
    // 2024-01-02 is not a real closure in US-MARKET-BASE, so this is a theirs-only discrepancy.
    writeReference(
        "US-MARKET-BASE", "testlib.csv", "2024-01-01 to 2024-01-02", "2024-01-02,CLOSED,");

    CrossvalidateCommand cmd = new CrossvalidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "US-MARKET-BASE", "--reference-dir", tempDir.resolve("reference").toString());

    assertEquals(1, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("discrepancies"));
    assertTrue(output.contains("2024-01-02"));
  }

  @Test
  void call_jsonFormat_producesValidJsonWithCounts() throws Exception {
    writeReference("US-MARKET-BASE", "testlib.csv", "2024-01-01 to 2024-01-01");

    CrossvalidateCommand cmd = new CrossvalidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    cmdLine.execute(
        "US-MARKET-BASE",
        "--reference-dir",
        tempDir.resolve("reference").toString(),
        "--format",
        "json");

    String output = stdout.toString();
    assertTrue(output.contains("\"status\""));
    assertTrue(output.contains("\"matched\""));
    assertTrue(output.contains("testlib"));
  }

  @Test
  void call_withOut_writesCrossValidationJson() throws Exception {
    writeReference("US-MARKET-BASE", "testlib.csv", "2024-01-01 to 2024-01-01");
    Path outDir = tempDir.resolve("blessed-out");

    CrossvalidateCommand cmd = new CrossvalidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "US-MARKET-BASE",
            "--reference-dir",
            tempDir.resolve("reference").toString(),
            "--out",
            outDir.toString());

    assertEquals(0, exitCode);
    Path written = outDir.resolve("US-MARKET-BASE").resolve("cross_validation.json");
    assertTrue(Files.exists(written));
    String content = Files.readString(written);
    assertTrue(content.contains("\"status\" : \"ok\""));
    assertTrue(content.contains("\"results\""));
  }

  @Test
  void call_all_discoversCalendarsWithReferenceData() throws Exception {
    writeReference("US-MARKET-BASE", "testlib.csv", "2024-01-01 to 2024-01-01");

    CrossvalidateCommand cmd = new CrossvalidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute("--all", "--reference-dir", tempDir.resolve("reference").toString());

    assertEquals(0, exitCode);
    assertTrue(stdout.toString().contains("US-MARKET-BASE"));
  }

  @Test
  void call_noCalendarNoAll_returnsError() {
    CrossvalidateCommand cmd = new CrossvalidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("--reference-dir", tempDir.resolve("reference").toString());

    assertEquals(1, exitCode);
    assertTrue(stderr.toString().contains("specify a calendar ID or --all"));
  }
}
