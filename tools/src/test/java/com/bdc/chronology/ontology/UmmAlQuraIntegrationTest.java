package com.bdc.chronology.ontology;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Integration tests for the Umm al-Qura chronology, validating conversions against known dates from
 * Java's HijrahChronology.
 */
class UmmAlQuraIntegrationTest {

  private final ChronologyRegistry registry = ChronologyRegistry.getInstance();

  @Test
  void ummAlQura_isRegistered() {
    assertTrue(registry.hasChronology("UMM_AL_QURA"));
  }

  @ParameterizedTest
  @CsvSource({
    // Ramadan 1, 1446 -> 2025-03-01
    "1446, 9, 1, 2025-03-01",
    // Shawwal 1, 1446 (Eid al-Fitr) -> 2025-03-30
    "1446, 10, 1, 2025-03-30",
    // Dhul Hijjah 10, 1446 (Eid al-Adha) -> 2025-06-06
    "1446, 12, 10, 2025-06-06",
    // Muharram 1, 1447 (Islamic New Year) -> 2025-06-26
    "1447, 1, 1, 2025-06-26",
    // Known historical: Muharram 1, 1400 -> 1979-11-21
    "1400, 1, 1, 1979-11-21",
  })
  void toIsoDate_knownDates(int year, int month, int day, String expectedIso) {
    LocalDate expected = LocalDate.parse(expectedIso);
    LocalDate actual = registry.toIsoDate(year, month, day, "UMM_AL_QURA");
    assertEquals(expected, actual, "UMM_AL_QURA " + year + "-" + month + "-" + day);
  }

  @ParameterizedTest
  @CsvSource({
    // 2025-03-01 -> Ramadan 1, 1446
    "2025-03-01, 1446, 9, 1",
    // 2025-03-30 -> Shawwal 1, 1446
    "2025-03-30, 1446, 10, 1",
    // 2025-06-06 -> Dhul Hijjah 10, 1446
    "2025-06-06, 1446, 12, 10",
  })
  void fromIsoDate_knownDates(String isoStr, int expectedYear, int expectedMonth, int expectedDay) {
    LocalDate isoDate = LocalDate.parse(isoStr);
    ChronologyDate result = registry.fromIsoDate(isoDate, "UMM_AL_QURA");
    assertEquals(expectedYear, result.year());
    assertEquals(expectedMonth, result.month());
    assertEquals(expectedDay, result.day());
  }

  @Test
  void roundTrip_allDatesIn1446() {
    // Test round-trip for a full year
    var algo = registry.getAlgorithm("UMM_AL_QURA");
    for (int month = 1; month <= 12; month++) {
      int days = algo.getDaysInMonth(1446, month);
      for (int day = 1; day <= days; day++) {
        long jdn = algo.toJdn(1446, month, day);
        ChronologyDate result = algo.fromJdn(jdn);
        assertEquals(1446, result.year());
        assertEquals(month, result.month());
        assertEquals(day, result.day());
      }
    }
  }

  @Test
  void ummAlQura_matchesJavaHijrahChronology() {
    // Cross-validate a sample of dates against Java's built-in HijrahChronology
    java.time.chrono.HijrahChronology javaChrono = java.time.chrono.HijrahChronology.INSTANCE;

    for (int year = 1440; year <= 1450; year++) {
      for (int month = 1; month <= 12; month++) {
        java.time.chrono.HijrahDate hd = javaChrono.date(year, month, 1);
        LocalDate expectedIso = LocalDate.from(hd);

        LocalDate actualIso = registry.toIsoDate(year, month, 1, "UMM_AL_QURA");
        assertEquals(
            expectedIso, actualIso, "Mismatch for UMM_AL_QURA " + year + "-" + month + "-1");
      }
    }
  }
}
