package com.bdc.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ValidateCommandTest {

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
  void call_validCalendar_returnsSuccess() {
    ValidateCommand cmd = new ValidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-MARKET-BASE");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("valid"));
    assertTrue(output.contains("US-MARKET-BASE"));
  }

  @Test
  void call_invalidCalendarId_returnsError() {
    ValidateCommand cmd = new ValidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("NONEXISTENT-CALENDAR");

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(errOutput.contains("failed") || errOutput.contains("not found"));
  }

  @Test
  void call_calendarWithModules_showsExtendsList() {
    ValidateCommand cmd = new ValidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-NYSE");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("Extends:"));
    // US-NYSE uses modules rather than extends
    assertTrue(output.contains("Uses:"));
  }

  @Test
  void call_calendarWithModules_showsUses() {
    ValidateCommand cmd = new ValidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-MARKET-BASE");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("Uses:"));
  }

  @Test
  void call_outputsEventSourceCount() {
    ValidateCommand cmd = new ValidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-MARKET-BASE");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("Event sources:"));
  }

  @Test
  void call_outputsCalendarName() {
    ValidateCommand cmd = new ValidateCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-MARKET-BASE");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("Name:"));
  }
}
