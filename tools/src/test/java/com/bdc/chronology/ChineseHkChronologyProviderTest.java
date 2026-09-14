package com.bdc.chronology;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChineseHkChronologyProviderTest {
  private final ChronologyProvider chinese = ChronologyProviders.get("CHINESE_HK");

  @Test
  void descriptorPinsThePublicProfile() {
    assertTrue(ChronologyProviders.available().contains("CHINESE_HK"));
    assertEquals("modern-chinese-fixed-utc+08:00", chinese.descriptor().profile());
    assertEquals("ICU4J 78.3", chinese.descriptor().provider());
    assertEquals(LocalDate.of(1929, 1, 1), chinese.descriptor().supportedFrom());
    assertEquals(LocalDate.of(2100, 12, 31), chinese.descriptor().supportedTo());
  }

  @Test
  void matchesPinnedProfileExpectationsForSelectedHkoRows() throws Exception {
    for (String row :
        Files.readAllLines(Path.of("tools/src/test/resources/chronology/chinese-hk-hko.tsv"))) {
      if (row.startsWith("#") || row.isBlank()) continue;
      String[] fields = row.split("\\t", 8);
      LocalDate iso = LocalDate.parse(fields[0]);
      NativeDate expected =
          new NativeDate(
              "CHINESE_HK", Integer.parseInt(fields[4]), fields[5], Integer.parseInt(fields[6]));
      NativeDate hko =
          new NativeDate(
              "CHINESE_HK", Integer.parseInt(fields[1]), fields[2], Integer.parseInt(fields[3]));
      if (iso.equals(LocalDate.of(2027, 2, 6))
          || iso.equals(LocalDate.of(2027, 2, 7))
          || iso.equals(LocalDate.of(2057, 9, 28))
          || iso.equals(LocalDate.of(2057, 9, 29))) assertNotEquals(hko, expected, fields[7]);
      else assertEquals(hko, expected, fields[7]);
      assertEquals(expected, chinese.fromIso(iso), row);
      assertEquals(iso, chinese.toIso(expected), row);
    }
  }

  @Test
  void roundTripsEverySupportedCivilDate() {
    LocalDate first = chinese.descriptor().supportedFrom();
    LocalDate last = chinese.descriptor().supportedTo();
    for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
      assertEquals(date, chinese.toIso(chinese.fromIso(date)), date.toString());
    }
  }

  @Test
  void monthIdentityAndExactValidationStayStrict() {
    assertEquals(
        List.of(
            "M01", "M02", "M02L", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10", "M11",
            "M12"),
        chinese.months(2023));
    assertEquals(
        List.of(
            "M01", "M02", "M03", "M04", "M05", "M06", "M06L", "M07", "M08", "M09", "M10", "M11",
            "M12"),
        chinese.months(2025));
    assertThrows(
        IllegalArgumentException.class,
        () -> chinese.toIso(new NativeDate("CHINESE_HK", 2024, "M02L", 1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> chinese.toIso(new NativeDate("CHINESE_HK", 2024, "M01", 31)));
    assertThrows(IllegalArgumentException.class, () -> chinese.validateMonthCode("M1"));
  }

  @Test
  void preciseSupportBoundariesAreEnforced() {
    assertThrows(
        UnsupportedChronologyRangeException.class,
        () -> chinese.fromIso(LocalDate.of(1928, 12, 31)));
    assertThrows(
        UnsupportedChronologyRangeException.class, () -> chinese.fromIso(LocalDate.of(2101, 1, 1)));
    assertThrows(
        UnsupportedChronologyRangeException.class,
        () -> chinese.toIso(new NativeDate("CHINESE_HK", 1928, "M11", 20)));
    assertThrows(
        UnsupportedChronologyRangeException.class,
        () -> chinese.toIso(new NativeDate("CHINESE_HK", 2100, "M12", 2)));
  }

  @Test
  void recurringExpansionClipsToBothPreciseSupportEdges() {
    var first =
        new com.bdc.model.Rule.NativeFixedMonthDay(
            "first", "first", "CHINESE_HK", List.of("M11"), 21, null, null);
    var last =
        new com.bdc.model.Rule.NativeFixedMonthDay(
            "last", "last", "CHINESE_HK", List.of("M12"), 1, null, null);
    var expander = new com.bdc.generator.RuleExpander();
    assertEquals(
        List.of(LocalDate.of(1929, 1, 1)),
        expander
            .expand(
                first, new DateRange(LocalDate.of(1929, 1, 1), LocalDate.of(1929, 1, 1)), "test")
            .stream()
            .map(com.bdc.model.Occurrence::date)
            .toList());
    assertEquals(
        List.of(LocalDate.of(2100, 12, 31)),
        expander
            .expand(
                last, new DateRange(LocalDate.of(2100, 12, 31), LocalDate.of(2100, 12, 31)), "test")
            .stream()
            .map(com.bdc.model.Occurrence::date)
            .toList());
  }
}
