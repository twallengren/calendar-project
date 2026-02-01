package com.bdc.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class QueryCommandTest {

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
  void call_isBusinessDay_weekday_printsBusinessDay() {
    QueryCommand cmd = new QueryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    // 2024-03-06 is a Wednesday
    int exitCode = cmdLine.execute("US-MARKET-BASE", "--is-business-day", "2024-03-06");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("is a business day"));
  }

  @Test
  void call_isBusinessDay_weekend_printsNotBusinessDay() {
    QueryCommand cmd = new QueryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    // 2024-03-02 is a Saturday
    int exitCode = cmdLine.execute("US-MARKET-BASE", "--is-business-day", "2024-03-02");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("NOT a business day"));
    // The output includes "WEEKEND" as the event type
    assertTrue(output.contains("WEEKEND") || output.contains("Weekend"));
  }

  @Test
  void call_isBusinessDay_holiday_printsNotBusinessDay() {
    QueryCommand cmd = new QueryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    // 2024-12-25 is Christmas
    int exitCode = cmdLine.execute("US-MARKET-BASE", "--is-business-day", "2024-12-25");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("NOT a business day"));
    assertTrue(output.contains("Christmas"));
  }

  @Test
  void call_nextBusinessDay_printsCorrectDate() {
    QueryCommand cmd = new QueryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    // Friday 2024-03-08 -> Monday 2024-03-11
    int exitCode = cmdLine.execute("US-MARKET-BASE", "--next-business-day", "2024-03-08");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("2024-03-11"));
  }

  @Test
  void call_prevBusinessDay_printsCorrectDate() {
    QueryCommand cmd = new QueryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    // Monday 2024-03-11 -> Friday 2024-03-08
    int exitCode = cmdLine.execute("US-MARKET-BASE", "--prev-business-day", "2024-03-11");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("2024-03-08"));
  }

  @Test
  void call_invalidCalendarId_returnsError() {
    QueryCommand cmd = new QueryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("NONEXISTENT-CALENDAR", "--is-business-day", "2024-03-06");

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(errOutput.contains("failed"));
  }

  @Test
  void call_eventsOn_printsEvents() {
    QueryCommand cmd = new QueryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    // 2024-12-25 is Christmas
    int exitCode = cmdLine.execute("US-MARKET-BASE", "--events-on", "2024-12-25");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("Christmas"));
    assertTrue(output.contains("CLOSED"));
  }

  @Test
  void call_noQuerySpecified_showsUsageHelp() {
    QueryCommand cmd = new QueryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode = cmdLine.execute("US-MARKET-BASE");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("No query specified"));
    assertTrue(output.contains("--is-business-day"));
  }

  @Test
  void call_businessDaysInRange_countsCorrectly() {
    QueryCommand cmd = new QueryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    // 2024-03-04 to 2024-03-08 is Mon-Fri = 5 business days
    int exitCode =
        cmdLine.execute(
            "US-MARKET-BASE",
            "--business-days-from",
            "2024-03-04",
            "--business-days-to",
            "2024-03-08");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("5"));
    assertTrue(output.contains("Business days"));
  }

  @Test
  void call_nthBusinessDay_calculatesCorrectly() {
    QueryCommand cmd = new QueryCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    // 2 business days after 2024-03-04 (Monday) = 2024-03-06 (Wednesday)
    int exitCode =
        cmdLine.execute("US-MARKET-BASE", "--nth-business-day", "2", "--from", "2024-03-04");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("2024-03-06"));
  }
}
