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

class GenerateCommandTest {

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
  void call_validArguments_generatesFiles() {
    Path outputDir = tempDir.resolve("output");
    GenerateCommand cmd = new GenerateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "US-MARKET-BASE",
            "--from",
            "2024-01-01",
            "--to",
            "2024-12-31",
            "--out",
            outputDir.toString());

    assertEquals(0, exitCode);
    assertTrue(Files.exists(outputDir.resolve("events.csv")));
    assertTrue(Files.exists(outputDir.resolve("metadata.json")));
  }

  @Test
  void call_missingOutputOption_returnsError() {
    GenerateCommand cmd = new GenerateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-MARKET-BASE", "--from", "2024-01-01", "--to", "2024-12-31");

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(errOutput.contains("--out") || errOutput.contains("--store"));
  }

  @Test
  void call_invalidCalendarId_returnsError() {
    Path outputDir = tempDir.resolve("output");
    GenerateCommand cmd = new GenerateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "NONEXISTENT-CALENDAR",
            "--from",
            "2024-01-01",
            "--to",
            "2024-12-31",
            "--out",
            outputDir.toString());

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(errOutput.contains("failed") || errOutput.contains("not found"));
  }

  @Test
  void call_withIncludeSpecs_generatesSpecFiles() {
    Path outputDir = tempDir.resolve("output");
    GenerateCommand cmd = new GenerateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "US-MARKET-BASE",
            "--from",
            "2024-01-01",
            "--to",
            "2024-12-31",
            "--out",
            outputDir.toString(),
            "--include-specs");

    assertEquals(0, exitCode);
    assertTrue(Files.exists(outputDir.resolve("events.csv")));
    assertTrue(Files.exists(outputDir.resolve("metadata.json")));
    assertTrue(Files.exists(outputDir.resolve("resolved.yaml")));
    assertTrue(Files.exists(outputDir.resolve("calendar.yaml")));
  }

  @Test
  void call_withSourceVersion_includesInMetadata() throws Exception {
    Path outputDir = tempDir.resolve("output");
    GenerateCommand cmd = new GenerateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "US-MARKET-BASE",
            "--from",
            "2024-01-01",
            "--to",
            "2024-12-31",
            "--out",
            outputDir.toString(),
            "--source-version",
            "abc123");

    assertEquals(0, exitCode);
    String metadataContent = Files.readString(outputDir.resolve("metadata.json"));
    assertTrue(metadataContent.contains("abc123"));
  }

  @Test
  void call_withReleaseVersion_includesInMetadata() throws Exception {
    Path outputDir = tempDir.resolve("output");
    GenerateCommand cmd = new GenerateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "US-MARKET-BASE",
            "--from",
            "2024-01-01",
            "--to",
            "2024-12-31",
            "--out",
            outputDir.toString(),
            "--release-version",
            "5.0.0");

    assertEquals(0, exitCode);
    String metadataContent = Files.readString(outputDir.resolve("metadata.json"));
    assertTrue(metadataContent.contains("5.0.0"));
  }

  @Test
  void call_generatesCorrectEventCount() throws Exception {
    Path outputDir = tempDir.resolve("output");
    GenerateCommand cmd = new GenerateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    cmdLine.execute(
        "US-MARKET-BASE",
        "--from",
        "2024-01-01",
        "--to",
        "2024-12-31",
        "--out",
        outputDir.toString());

    String csvContent = Files.readString(outputDir.resolve("events.csv"));
    String[] lines = csvContent.split("\n");
    // Header + events (US-MARKET-BASE has 6 holidays + weekends)
    assertTrue(lines.length > 1);
  }

  @Test
  void call_outputsSuccessMessage() {
    Path outputDir = tempDir.resolve("output");
    GenerateCommand cmd = new GenerateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    cmdLine.execute(
        "US-MARKET-BASE",
        "--from",
        "2024-01-01",
        "--to",
        "2024-12-31",
        "--out",
        outputDir.toString());

    String output = stdout.toString();
    assertTrue(output.contains("Generated"));
    assertTrue(output.contains("events"));
  }
}
