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

  @Test
  void call_withArtifacts_listsHistory() throws Exception {
    // Create resolved artifacts
    Path resolvedDir = tempDir.resolve("artifacts/resolved/TEST-CAL");
    Files.createDirectories(resolvedDir);
    Files.writeString(resolvedDir.resolve("2024-01-01T00:00:00Z.yaml"), "id: TEST\n");
    Files.writeString(resolvedDir.resolve("2024-01-02T00:00:00Z.yaml"), "id: TEST\n");

    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "resolved", "TEST-CAL", "--artifacts-dir", tempDir.resolve("artifacts").toString());

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("history") || output.contains("versions"));
    assertTrue(output.contains("TEST-CAL"));
  }

  @Test
  void call_emptyArtifacts_printsNone() throws Exception {
    // Create artifacts directory but no resolved artifacts for this calendar
    Path resolvedDir = tempDir.resolve("artifacts/resolved/NONEXISTENT");
    Files.createDirectories(resolvedDir.getParent());

    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "resolved", "NONEXISTENT", "--artifacts-dir", tempDir.resolve("artifacts").toString());

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("No") || output.contains("not found"));
  }

  @Test
  void call_limitOption_limitsOutput() throws Exception {
    // Create multiple resolved artifacts
    Path resolvedDir = tempDir.resolve("artifacts/resolved/TEST-CAL");
    Files.createDirectories(resolvedDir);
    for (int i = 1; i <= 20; i++) {
      Files.writeString(
          resolvedDir.resolve(String.format("2024-01-%02dT00:00:00Z.yaml", i)), "id: TEST\n");
    }

    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "resolved",
            "TEST-CAL",
            "--artifacts-dir",
            tempDir.resolve("artifacts").toString(),
            "--limit",
            "5");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("5 of 20") || output.contains("showing"));
  }

  @Test
  void call_generatedWithoutValidRange_showsAllRanges() throws Exception {
    // Create generated artifacts with multiple ranges
    Path generatedDir1 = tempDir.resolve("artifacts/generated/TEST-CAL/2024-01-01_2024-12-31");
    Path generatedDir2 = tempDir.resolve("artifacts/generated/TEST-CAL/2025-01-01_2025-12-31");
    Files.createDirectories(generatedDir1.resolve("version1"));
    Files.createDirectories(generatedDir2.resolve("version1"));

    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "generated", "TEST-CAL", "--artifacts-dir", tempDir.resolve("artifacts").toString());

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("ranges") || output.contains("2024") || output.contains("2025"));
  }

  @Test
  void call_generatedWithValidRange_showsVersions() throws Exception {
    // Create generated artifacts
    Path generatedDir =
        tempDir.resolve("artifacts/generated/TEST-CAL/2024-01-01_2024-12-31/2024-01-01T00:00:00Z");
    Files.createDirectories(generatedDir);
    Files.writeString(generatedDir.resolve("events.csv"), "date,type,description\n");

    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "generated",
            "TEST-CAL",
            "--artifacts-dir",
            tempDir.resolve("artifacts").toString(),
            "--valid-from",
            "2024-01-01",
            "--valid-to",
            "2024-12-31");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(
        output.contains("history") || output.contains("versions") || output.contains("2024"));
  }

  @Test
  void call_generatedWithValidRangeYear_usesYearShortcut() throws Exception {
    // Create generated artifacts
    Path generatedDir =
        tempDir.resolve("artifacts/generated/TEST-CAL/2024-01-01_2024-12-31/2024-01-01T00:00:00Z");
    Files.createDirectories(generatedDir);
    Files.writeString(generatedDir.resolve("events.csv"), "date,type,description\n");

    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "generated",
            "TEST-CAL",
            "--artifacts-dir",
            tempDir.resolve("artifacts").toString(),
            "--valid-range",
            "2024");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(
        output.contains("2024-01-01") || output.contains("history") || output.contains("versions"));
  }

  @Test
  void call_invalidArtifactType_returnsError() {
    HistoryCommand cmd = new HistoryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "invalid-type", "TEST-CAL", "--artifacts-dir", tempDir.resolve("artifacts").toString());

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(errOutput.contains("Unknown artifact type") || errOutput.contains("resolved"));
  }
}
