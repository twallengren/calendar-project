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

class ResolveCommandTest {

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
  void call_simpleCalendar_outputsResolvedYaml() {
    ResolveCommand cmd = new ResolveCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-MARKET-BASE");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("id:"));
    assertTrue(output.contains("US-MARKET-BASE"));
    assertTrue(output.contains("weekend_policy:") || output.contains("weekendPolicy:"));
  }

  @Test
  void call_withExtends_flattensInheritance() {
    ResolveCommand cmd = new ResolveCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-NYSE");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    // Should contain events from parent (US-MARKET-BASE)
    assertTrue(output.contains("new_years_day") || output.contains("New Year"));
    // Should contain its own events
    assertTrue(output.contains("resolution") || output.contains("chain"));
  }

  @Test
  void call_invalidCalendarId_returnsError() {
    ResolveCommand cmd = new ResolveCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("NONEXISTENT-CALENDAR");

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(errOutput.contains("failed") || errOutput.contains("Resolution"));
  }

  @Test
  void call_toFile_writesOutput() throws Exception {
    Path outputFile = tempDir.resolve("resolved.yaml");
    ResolveCommand cmd = new ResolveCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-MARKET-BASE", "--out", outputFile.toString());

    assertEquals(0, exitCode);
    assertTrue(Files.exists(outputFile));
    String content = Files.readString(outputFile);
    assertTrue(content.contains("US-MARKET-BASE"));
  }

  @Test
  void call_outputContainsResolutionChain() {
    ResolveCommand cmd = new ResolveCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-NYSE");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    // Resolution chain should show the inheritance path
    assertTrue(output.contains("US-NYSE") || output.contains("calendar:US-NYSE"));
  }

  @Test
  void call_outputContainsEventSources() {
    ResolveCommand cmd = new ResolveCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-MARKET-BASE");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    // The resolved output uses camelCase "eventSources"
    assertTrue(output.contains("eventSources"));
  }
}
