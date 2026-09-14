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
  void assessmentFromArtifactIsOneParseableJsonDocument() throws Exception {
    int code =
        new CommandLine(new QueryCommand())
            .execute("US-NYSE", "--as-of", "blessed", "--assess-day", "2025-09-23");
    assertEquals(0, code);
    var day = new com.fasterxml.jackson.databind.ObjectMapper().readTree(stdout.toString());
    assertEquals("2025-09-23", day.path("date").asText());
    assertTrue(stderr.toString().contains("Using artifact"));
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

  // === Joint (multi-calendar) queries ===
  // 2026-02-26 is a Thursday: Tadawul rests Fri-Sat, the NYSE Sat-Sun, so the joint calendar is
  // shut from Friday through Sunday and the next joint business day is Monday 2026-03-02.

  @Test
  void call_jointCalendars_fridayIsClosedInTadawulOnly() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode = cmdLine.execute("US-NYSE,SA-TADAWUL", "--is-business-day", "2026-02-27");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("NOT a business day"), output);
    assertTrue(output.contains("Closed in: SA-TADAWUL"), output);
  }

  @Test
  void call_jointCalendars_sundayIsClosedInNyseOnly() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode = cmdLine.execute("US-NYSE,SA-TADAWUL", "--is-business-day", "2026-03-01");

    assertEquals(0, exitCode);
    assertTrue(stdout.toString().contains("NOT a business day"), stdout.toString());
  }

  @Test
  void call_jointCalendars_thursdayIsABusinessDayAndNextIsMonday() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode =
        cmdLine.execute(
            "US-NYSE,SA-TADAWUL",
            "--is-business-day",
            "2026-02-26",
            "--next-business-day",
            "2026-02-26");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("2026-02-26 is a business day"), output);
    assertTrue(output.contains("Next business day after 2026-02-26: 2026-03-02"), output);
  }

  @Test
  void call_settlementT1FromThursday_landsOnMonday() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode =
        cmdLine.execute("US-NYSE,SA-TADAWUL", "--settlement", "T+1", "--from", "2026-02-26");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("T+1 settles 2026-03-02"), output);
    assertTrue(output.contains("2026-02-27 Fri: closed in SA-TADAWUL"), output);
    assertTrue(output.contains("2026-02-28 Sat: closed in US-NYSE, SA-TADAWUL"), output);
    assertTrue(output.contains("2026-03-01 Sun: closed in US-NYSE"), output);
  }

  @Test
  void call_settlementT2_countsOnlyJointBusinessDays() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    // Wednesday trade date: Thursday is the first joint business day, Monday the second
    int exitCode =
        cmdLine.execute("US-NYSE,SA-TADAWUL", "--settlement", "T+2", "--from", "2026-02-25");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("T+2 settles 2026-03-02"), output);
    assertTrue(output.contains("2026-02-26 Thu: business day 1 of 2"), output);
    assertTrue(output.contains("2026-03-02 Mon: business day 2 of 2"), output);
  }

  @Test
  void call_settlementWithoutFrom_fails() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode = cmdLine.execute("US-NYSE", "--settlement", "T+2");

    assertEquals(1, exitCode);
    assertTrue(stderr.toString().contains("--from"), stderr.toString());
  }

  @Test
  void call_openInClosedIn_listsDatesOpenInOneAndClosedInTheOther() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode =
        cmdLine.execute(
            "--open-in",
            "US-NYSE",
            "--closed-in",
            "SA-TADAWUL",
            "--from",
            "2026-02-20",
            "--to",
            "2026-03-10");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    // Fridays: the NYSE trades, Tadawul rests
    assertTrue(output.contains("2026-02-20 Fri"), output);
    assertTrue(output.contains("2026-02-27 Fri"), output);
    assertTrue(output.contains("2026-03-06 Fri"), output);
    assertTrue(output.contains("3 dates"), output);
  }

  // === Status, early close and coverage ===

  @Test
  void call_status_pastVerifiedThrough_isProjected() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    // US-NYSE is verified through 2026-12-31
    int exitCode = cmdLine.execute("US-NYSE", "--status", "2028-06-01");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("PROJECTED"), output);
    assertTrue(output.contains("verified_through 2026-12-31"), output);
  }

  @Test
  void call_status_withLegacyVerifiedRangeButProjectedScopedQuality_isProjected() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode = cmdLine.execute("US-NYSE", "--status", "2024-06-03");

    assertEquals(0, exitCode);
    assertTrue(stdout.toString().contains("PROJECTED"), stdout.toString());
  }

  @Test
  void call_status_outsideCoverage_isUnknownAndDoesNotFail() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode = cmdLine.execute("US-NYSE,SA-TADAWUL", "--status", "2019-06-03");

    assertEquals(0, exitCode);
    assertTrue(stdout.toString().contains("UNKNOWN"), stdout.toString());
  }

  @Test
  void call_outsideCoverage_exitsOneWithAClearMessage() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    // SA-TADAWUL coverage starts 2020-01-01, so the joint calendar cannot answer for 2019
    int exitCode = cmdLine.execute("US-NYSE,SA-TADAWUL", "--is-business-day", "2019-06-03");

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(
        errOutput.contains("2019-06-03 is outside the covered range of US-NYSE+SA-TADAWUL"),
        errOutput);
    assertTrue(errOutput.contains("2020-01-01 to 2030-12-31"), errOutput);
    assertFalse(errOutput.contains("\tat "), "no stack trace for an expected error: " + errOutput);
  }

  @Test
  void call_verifiedThrough_printsRangeAndVerifiedDate() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode = cmdLine.execute("US-NYSE,SA-TADAWUL", "--verified-through");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("covered range: 2020-01-01 to 2030-12-31"), output);
    // the earliest of the members
    assertTrue(output.contains("verified through: 2026-12-31"), output);
  }

  @Test
  void call_isEarlyClose_printsCloseTime() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode =
        cmdLine.execute("US-NYSE", "--is-early-close", "2026-11-27", "--close-time", "2026-11-27");

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("2026-11-27 is an early close"), output);
    assertTrue(output.contains("13:00"), output);
  }

  @Test
  void call_closeTime_regularSession() {
    CommandLine cmdLine = new CommandLine(new QueryCommand());

    int exitCode = cmdLine.execute("US-NYSE", "--close-time", "2026-11-25");

    assertEquals(0, exitCode);
    assertTrue(stdout.toString().contains("regular session"), stdout.toString());
  }
}
