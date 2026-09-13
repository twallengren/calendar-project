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

class HistoryCommandTest {

  @TempDir Path tempDir;

  private ByteArrayOutputStream stdout;
  private ByteArrayOutputStream stderr;
  private PrintStream originalOut;
  private PrintStream originalErr;
  private Path blessedDir;
  private Path releaseHistoryDir;

  private void snapshot(Path base, String rangeStart, String rangeEnd) throws Exception {
    Files.createDirectories(base);
    Files.writeString(base.resolve("events.csv"), "date,type,description\n");
    Files.writeString(
        base.resolve("metadata.json"),
        "{\"calendar_id\":\"TEST-CAL\",\"range_start\":\""
            + rangeStart
            + "\",\"range_end\":\""
            + rangeEnd
            + "\"}");
  }

  @BeforeEach
  void setUp() throws Exception {
    stdout = new ByteArrayOutputStream();
    stderr = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(stdout));
    System.setErr(new PrintStream(stderr));

    blessedDir = tempDir.resolve("blessed");
    releaseHistoryDir = tempDir.resolve("release-history");

    snapshot(
        releaseHistoryDir.resolve("TEST-CAL/2024-01-01T00-00-00Z_abc1234_v1.0.0"),
        "2024-01-01",
        "2024-12-31");

    snapshot(blessedDir.resolve("TEST-CAL"), "2024-01-01", "2024-12-31");
    Files.writeString(
        blessedDir.resolve("manifest.json"),
        """
        {"blessed_at":"2024-06-01T00:00:00Z","calendars":{"TEST-CAL":{"range_start":"2024-01-01","range_end":"2024-12-31"}},
         "release_version":{"semantic":"2.0.0","git_sha":"def5678"}}
        """);
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  void call_releases_listsPublishedVersionsNewestFirst() {
    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "releases",
            "TEST-CAL",
            "--blessed-dir",
            blessedDir.toString(),
            "--release-history-dir",
            releaseHistoryDir.toString());

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("TEST-CAL"));
    assertTrue(output.contains("v2.0.0"));
    assertTrue(output.contains("v1.0.0"));
    assertTrue(output.contains("(blessed)"));
    assertTrue(output.indexOf("v2.0.0") < output.indexOf("v1.0.0"));
  }

  @Test
  void call_releases_noSnapshots_printsNone() {
    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "releases",
            "NONEXISTENT",
            "--blessed-dir",
            blessedDir.toString(),
            "--release-history-dir",
            releaseHistoryDir.toString());

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("No published releases found for NONEXISTENT"));
  }

  @Test
  void call_releases_limitOption_limitsOutput() {
    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "releases",
            "TEST-CAL",
            "--blessed-dir",
            blessedDir.toString(),
            "--release-history-dir",
            releaseHistoryDir.toString(),
            "--limit",
            "1");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("showing 1 of 2"));
  }

  @Test
  void call_invalidArtifactType_returnsError() {
    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("invalid-type", "TEST-CAL");

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(errOutput.contains("Unknown artifact type"));
    assertTrue(errOutput.contains("releases"));
  }
}
