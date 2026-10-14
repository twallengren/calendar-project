package com.bdc.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Values hand-computed from the 2006 ISDA Definitions, Section 4.16. */
class DayCountTest {

  private static LocalDate d(String iso) {
    return LocalDate.parse(iso);
  }

  @Test
  void actualConventions() {
    assertEquals(29, DayCount.ACT_360.days(d("2024-01-31"), d("2024-02-29")));
    assertEquals(29 / 360.0, DayCount.ACT_360.yearFraction(d("2024-01-31"), d("2024-02-29")));
    assertEquals(1.0, DayCount.ACT_365F.yearFraction(d("2023-01-01"), d("2024-01-01")));
    assertEquals(366 / 365.0, DayCount.ACT_365F.yearFraction(d("2024-01-01"), d("2025-01-01")));
  }

  @Test
  void actActIsdaSplitsAtYearBoundaries() {
    // 47 days of 2023 (365) and 45 days of 2024 (366)
    assertEquals(
        47 / 365.0 + 45 / 366.0,
        DayCount.ACT_ACT_ISDA.yearFraction(d("2023-11-15"), d("2024-02-15")),
        1e-12);
    assertEquals(182 / 366.0, DayCount.ACT_ACT_ISDA.yearFraction(d("2024-01-01"), d("2024-07-01")));
    // whole years, leap or not, are exactly one each
    assertEquals(3.0, DayCount.ACT_ACT_ISDA.yearFraction(d("2022-06-30"), d("2025-06-30")), 1e-12);
    assertEquals(92, DayCount.ACT_ACT_ISDA.days(d("2023-11-15"), d("2024-02-15")));
  }

  @Test
  void thirty360FamilyDiffersOnMonthEnds() {
    LocalDate febEnd = d("2024-02-29");
    LocalDate marEnd = d("2024-03-31");
    // Bond basis: D2 only becomes 30 when D1 is 30 or 31
    assertEquals(32, DayCount.THIRTY_360_BOND.days(febEnd, marEnd));
    // Eurobond: both 31sts become 30
    assertEquals(31, DayCount.THIRTY_E_360.days(febEnd, marEnd));
    // ISDA: every month end becomes 30
    assertEquals(30, DayCount.THIRTY_E_360_ISDA.days(febEnd, marEnd));

    assertEquals(60, DayCount.THIRTY_360_BOND.days(d("2024-01-31"), marEnd));
    assertEquals(60, DayCount.THIRTY_E_360.days(d("2024-01-31"), marEnd));
    assertEquals(90, DayCount.THIRTY_360_BOND.days(d("2023-01-15"), d("2023-04-15")));
    assertEquals(0.25, DayCount.THIRTY_360_BOND.yearFraction(d("2023-01-15"), d("2023-04-15")));
  }

  @Test
  void thirtyE360IsdaKeepsAFebruaryTerminationDate() {
    LocalDate start = d("2023-01-31");
    LocalDate end = d("2024-02-29");
    assertEquals(390 / 360.0, DayCount.THIRTY_E_360_ISDA.yearFraction(start, end, false), 1e-12);
    assertEquals(389 / 360.0, DayCount.THIRTY_E_360_ISDA.yearFraction(start, end, true), 1e-12);
    // the flag is ignored by every other convention
    assertEquals(
        DayCount.ACT_360.yearFraction(start, end), DayCount.ACT_360.yearFraction(start, end, true));
  }

  @Test
  void actActIcmaUsesTheReferencePeriod() {
    LocalDate periodStart = d("2024-02-15");
    LocalDate periodEnd = d("2024-08-15"); // 182 days
    assertEquals(
        90 / (2 * 182.0),
        DayCount.ACT_ACT_ICMA.yearFraction(
            periodStart, d("2024-05-15"), periodStart, periodEnd, Frequency.SEMI_ANNUAL),
        1e-12);
    assertEquals(
        0.5,
        DayCount.ACT_ACT_ICMA.yearFraction(
            periodStart, periodEnd, periodStart, periodEnd, Frequency.SEMI_ANNUAL),
        1e-12);
    assertThrows(
        IllegalArgumentException.class,
        () -> DayCount.ACT_ACT_ICMA.yearFraction(periodStart, periodEnd));
    assertThrows(
        IllegalArgumentException.class,
        () -> DayCount.BUS_252.yearFraction(periodStart, periodEnd));
    assertFalse(DayCount.BUS_252.isCalendarFree());
    assertTrue(DayCount.ACT_360.isCalendarFree());
  }

  @Test
  void reversedDatesAreNegativeAndSymmetric() {
    for (DayCount dayCount : DayCount.values()) {
      if (!dayCount.isCalendarFree()) continue;
      assertEquals(
          -dayCount.yearFraction(d("2023-11-15"), d("2024-02-15")),
          dayCount.yearFraction(d("2024-02-15"), d("2023-11-15")),
          1e-12,
          dayCount.name());
      assertEquals(0.0, dayCount.yearFraction(d("2024-02-15"), d("2024-02-15")), dayCount.name());
    }
  }
}
