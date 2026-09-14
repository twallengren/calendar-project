package com.bdc.formula;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Checks EQUINOX_VERNAL_JP and EQUINOX_AUTUMNAL_JP against the Cabinet Office's published list of
 * national holidays (`cao-shukujitsu`, https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv,
 * retrieved 2026-09-14), which covers 1955 through 2027. Every year the list covers that is inside
 * the JP-JPX coverage range (2010-2030) plus the earlier years back to 2000 is asserted here; past
 * 2027 the formula is a projection and JP-JPX marks those occurrences {@code status: PROJECTED}.
 */
class EquinoxCalculatorTest {

  /** Day-of-March of 春分の日 per the Cabinet Office list, 2000-2027. */
  private static final Map<Integer, Integer> VERNAL_FROM_CABINET_OFFICE = new LinkedHashMap<>();

  /** Day-of-September of 秋分の日 per the Cabinet Office list, 2000-2027. */
  private static final Map<Integer, Integer> AUTUMNAL_FROM_CABINET_OFFICE = new LinkedHashMap<>();

  static {
    int[][] vernal = {
      {2000, 20}, {2001, 20}, {2002, 21}, {2003, 21}, {2004, 20}, {2005, 20}, {2006, 21},
      {2007, 21}, {2008, 20}, {2009, 20}, {2010, 21}, {2011, 21}, {2012, 20}, {2013, 20},
      {2014, 21}, {2015, 21}, {2016, 20}, {2017, 20}, {2018, 21}, {2019, 21}, {2020, 20},
      {2021, 20}, {2022, 21}, {2023, 21}, {2024, 20}, {2025, 20}, {2026, 20}, {2027, 21},
    };
    int[][] autumnal = {
      {2000, 23}, {2001, 23}, {2002, 23}, {2003, 23}, {2004, 23}, {2005, 23}, {2006, 23},
      {2007, 23}, {2008, 23}, {2009, 23}, {2010, 23}, {2011, 23}, {2012, 22}, {2013, 23},
      {2014, 23}, {2015, 23}, {2016, 22}, {2017, 23}, {2018, 23}, {2019, 23}, {2020, 22},
      {2021, 23}, {2022, 23}, {2023, 23}, {2024, 22}, {2025, 23}, {2026, 23}, {2027, 23},
    };
    for (int[] row : vernal) {
      VERNAL_FROM_CABINET_OFFICE.put(row[0], row[1]);
    }
    for (int[] row : autumnal) {
      AUTUMNAL_FROM_CABINET_OFFICE.put(row[0], row[1]);
    }
  }

  @Test
  void vernalEquinoxMatchesCabinetOfficeListForEveryCoveredYear() {
    VERNAL_FROM_CABINET_OFFICE.forEach(
        (year, day) ->
            assertEquals(
                LocalDate.of(year, 3, day),
                ReferenceResolver.vernalEquinoxJp(year),
                "vernal equinox " + year));
  }

  @Test
  void autumnalEquinoxMatchesCabinetOfficeListForEveryCoveredYear() {
    AUTUMNAL_FROM_CABINET_OFFICE.forEach(
        (year, day) ->
            assertEquals(
                LocalDate.of(year, 9, day),
                ReferenceResolver.autumnalEquinoxJp(year),
                "autumnal equinox " + year));
  }

  @Test
  void projectedYearsPastTheCabinetOfficeListStayInRange() {
    // Not verifiable against a source yet; only assert the formula stays inside the month.
    for (int year = 2028; year <= 2030; year++) {
      assertEquals(3, ReferenceResolver.vernalEquinoxJp(year).getMonthValue());
      assertEquals(9, ReferenceResolver.autumnalEquinoxJp(year).getMonthValue());
    }
    assertEquals(LocalDate.of(2028, 3, 20), ReferenceResolver.vernalEquinoxJp(2028));
    assertEquals(LocalDate.of(2028, 9, 22), ReferenceResolver.autumnalEquinoxJp(2028));
  }

  @Test
  void outsideTheApproximationsValidityIsAnError() {
    assertThrows(IllegalArgumentException.class, () -> ReferenceResolver.vernalEquinoxJp(1899));
    assertThrows(IllegalArgumentException.class, () -> ReferenceResolver.autumnalEquinoxJp(2100));
  }
}
