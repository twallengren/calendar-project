package com.bdc.chronology;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.cli.Main;
import com.bdc.emitter.CsvEmitter;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class NativeChronologyTest {
  private final ChronologyProvider hebrew = ChronologyProviders.get("HEBREW");

  @Test
  void independentAuthorityDates() throws Exception {
    // Bank of Israel 2024/2025 services calendar; fixture register explains date identities.
    for (String row :
        Files.readAllLines(Path.of("tools/src/test/resources/chronology/hebrew-authority.tsv"))) {
      if (row.startsWith("#") || row.isBlank()) continue;
      String[] fields = row.split("\\t");
      NativeDate nativeDate =
          new NativeDate(
              "HEBREW", Integer.parseInt(fields[0]), fields[1], Integer.parseInt(fields[2]));
      LocalDate iso = LocalDate.parse(fields[3]);
      assertEquals(iso, hebrew.toIso(nativeDate), row);
      assertEquals(nativeDate, hebrew.fromIso(iso), row);
    }
  }

  @Test
  void everySupportedCivilDateRoundTrips() {
    for (LocalDate date = hebrew.descriptor().supportedFrom();
        !date.isAfter(hebrew.descriptor().supportedTo());
        date = date.plusDays(1)) {
      NativeDate nativeDate = hebrew.fromIso(date);
      assertEquals(date, hebrew.toIso(nativeDate));
      assertTrue(hebrew.months(nativeDate.year()).contains(nativeDate.monthCode()));
      assertTrue(nativeDate.day() <= hebrew.monthLength(nativeDate.year(), nativeDate.monthCode()));
    }
  }

  @Test
  void leapMonthsNeverAliasEachOther() {
    assertEquals(13, hebrew.months(5784).size());
    assertEquals(12, hebrew.months(5785).size());
    assertThrows(
        IllegalArgumentException.class,
        () -> hebrew.toIso(new NativeDate("HEBREW", 5784, "ADAR", 14)));
    assertThrows(
        IllegalArgumentException.class,
        () -> hebrew.toIso(new NativeDate("HEBREW", 5785, "ADAR_I", 14)));
    assertThrows(
        IllegalArgumentException.class,
        () -> hebrew.toIso(new NativeDate("HEBREW", 5785, "ADAR_II", 14)));
    assertThrows(
        IllegalArgumentException.class,
        () -> hebrew.toIso(new NativeDate("HEBREW", 5785, "TYPO", 1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> hebrew.toIso(new NativeDate("HEBREW", 5785, "ADAR", 30)));
  }

  @Test
  void supportBoundariesAreDistinctFailures() {
    assertThrows(
        UnsupportedChronologyRangeException.class,
        () -> hebrew.fromIso(LocalDate.of(1900, 12, 31)));
    assertThrows(
        UnsupportedChronologyRangeException.class, () -> hebrew.fromIso(LocalDate.of(2101, 1, 1)));
    assertEquals("CHINESE_HK", ChronologyProviders.get("CHINESE_HK").descriptor().id());
  }

  @Test
  void converterProducesStructuredNativeIdentity() throws Exception {
    CommandLine cli = new CommandLine(new Main());
    StringWriter out = new StringWriter();
    cli.setOut(new PrintWriter(out));
    assertEquals(
        0,
        cli.execute(
            "convert",
            "--from-chronology",
            "HEBREW",
            "--year",
            "5786",
            "--month-code",
            "TISHRI",
            "--day",
            "1",
            "--to-chronology",
            "ISO",
            "--format",
            "json"));
    var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(out.toString());
    assertEquals("2025-09-23", json.path("iso_date").asText());
    assertEquals("ICU4J 78.3", json.path("source_provider").asText());
  }

  @Test
  void failedAlternateConversionPreservesExistingOutput(@TempDir Path directory) throws Exception {
    Path output = directory.resolve("events.csv");
    Files.writeString(output, "previous artifact");
    var events =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "supported", "test"),
            new Event(LocalDate.of(2101, 1, 1), EventType.CLOSED, "outside", "test"));
    assertThrows(
        UnsupportedChronologyRangeException.class,
        () -> new CsvEmitter().emit(events, output, "HEBREW"));
    assertEquals("previous artifact", Files.readString(output));
    assertThrows(
        IllegalArgumentException.class, () -> new CsvEmitter().emit(List.of(), output, "UNKNOWN"));
    assertEquals("previous artifact", Files.readString(output));
  }

  @Test
  void persianProfileRetainsArithmeticLeapRuleWithCorrectEpoch() {
    var persian = ChronologyProviders.get("PERSIAN");
    assertEquals(LocalDate.of(622, 3, 22), persian.toIso(new NativeDate("PERSIAN", 1, "M01", 1)));
    assertEquals(
        LocalDate.of(2024, 3, 20), persian.toIso(new NativeDate("PERSIAN", 1403, "M01", 1)));
    // This profile differs from Iran's astronomical civil calendar in 1403/1404.
    assertEquals(29, persian.monthLength(1403, "M12"));
    assertEquals(30, persian.monthLength(1404, "M12"));
  }
}
