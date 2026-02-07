package com.bdc.chronology.ontology.algorithms;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.ChronologySpec;
import com.bdc.chronology.ontology.JulianDayNumber;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FormulaAlgorithmTest {

  @Test
  void formulaAlgorithm_withJulianSpec_matchesBuiltIn() {
    // Create a formula algorithm that matches Julian calendar
    List<ChronologySpec.Month> months =
        List.of(
            new ChronologySpec.Month("January", 31, null),
            new ChronologySpec.Month("February", 28, 29),
            new ChronologySpec.Month("March", 31, null),
            new ChronologySpec.Month("April", 30, null),
            new ChronologySpec.Month("May", 31, null),
            new ChronologySpec.Month("June", 30, null),
            new ChronologySpec.Month("July", 31, null),
            new ChronologySpec.Month("August", 31, null),
            new ChronologySpec.Month("September", 30, null),
            new ChronologySpec.Month("October", 31, null),
            new ChronologySpec.Month("November", 30, null),
            new ChronologySpec.Month("December", 31, null));

    FormulaAlgorithm formula =
        new FormulaAlgorithm("TEST_JULIAN", JulianDayNumber.JULIAN_EPOCH, months, "year % 4 == 0");

    JulianAlgorithm builtin = new JulianAlgorithm();

    // Compare leap years
    for (int year = 1; year <= 100; year++) {
      assertEquals(
          builtin.isLeapYear(year),
          formula.isLeapYear(year),
          "Leap year mismatch for year " + year);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "year % 4 == 0, 2000, true",
    "year % 4 == 0, 2001, false",
    "year % 4 == 0, 2004, true",
    "(year % 4 == 0 && year % 100 != 0) || (year % 400 == 0), 2000, true",
    "(year % 4 == 0 && year % 100 != 0) || (year % 400 == 0), 1900, false",
    "(year % 4 == 0 && year % 100 != 0) || (year % 400 == 0), 2100, false",
    "(year % 4 == 0 && year % 100 != 0) || (year % 400 == 0), 2004, true",
    "false, 2000, false",
    "true, 2001, true",
  })
  void leapYearFormula_variousFormulas_correctResult(String formula, int year, boolean expected) {
    List<ChronologySpec.Month> months = List.of(new ChronologySpec.Month("M1", 30, null));

    FormulaAlgorithm algorithm = new FormulaAlgorithm("TEST", 0, months, formula);

    assertEquals(expected, algorithm.isLeapYear(year));
  }

  @Test
  void getDaysInMonth_respectsLeapDays() {
    List<ChronologySpec.Month> months =
        List.of(
            new ChronologySpec.Month("January", 31, null),
            new ChronologySpec.Month("February", 28, 29));

    FormulaAlgorithm algorithm = new FormulaAlgorithm("TEST", 0, months, "year % 4 == 0");

    assertEquals(31, algorithm.getDaysInMonth(2000, 1));
    assertEquals(29, algorithm.getDaysInMonth(2000, 2)); // Leap year
    assertEquals(28, algorithm.getDaysInMonth(2001, 2)); // Non-leap year
  }

  @Test
  void isValidDate_checksMonthDays() {
    List<ChronologySpec.Month> months =
        List.of(
            new ChronologySpec.Month("January", 31, null),
            new ChronologySpec.Month("February", 28, 29));

    FormulaAlgorithm algorithm = new FormulaAlgorithm("TEST", 0, months, "year % 4 == 0");

    assertTrue(algorithm.isValidDate(2000, 1, 31));
    assertTrue(algorithm.isValidDate(2000, 2, 29)); // Leap year
    assertFalse(algorithm.isValidDate(2001, 2, 29)); // Non-leap year
    assertFalse(algorithm.isValidDate(2000, 1, 32));
    assertFalse(algorithm.isValidDate(2000, 3, 1)); // Month doesn't exist
  }

  @Test
  void toJdnAndFromJdn_roundTrip_preservesDate() {
    List<ChronologySpec.Month> months =
        List.of(
            new ChronologySpec.Month("January", 31, null),
            new ChronologySpec.Month("February", 28, 29),
            new ChronologySpec.Month("March", 31, null),
            new ChronologySpec.Month("April", 30, null),
            new ChronologySpec.Month("May", 31, null),
            new ChronologySpec.Month("June", 30, null),
            new ChronologySpec.Month("July", 31, null),
            new ChronologySpec.Month("August", 31, null),
            new ChronologySpec.Month("September", 30, null),
            new ChronologySpec.Month("October", 31, null),
            new ChronologySpec.Month("November", 30, null),
            new ChronologySpec.Month("December", 31, null));

    FormulaAlgorithm algorithm =
        new FormulaAlgorithm("TEST", JulianDayNumber.JULIAN_EPOCH, months, "year % 4 == 0");

    // Test various dates
    int[][] testDates = {
      {1, 1, 1},
      {1, 6, 15},
      {100, 3, 20},
      {500, 12, 31},
      {1000, 7, 4},
    };

    for (int[] date : testDates) {
      int year = date[0], month = date[1], day = date[2];
      long jdn = algorithm.toJdn(year, month, day);
      ChronologyDate result = algorithm.fromJdn(jdn);

      assertEquals(year, result.year(), "Year mismatch for " + year + "-" + month + "-" + day);
      assertEquals(month, result.month(), "Month mismatch for " + year + "-" + month + "-" + day);
      assertEquals(day, result.day(), "Day mismatch for " + year + "-" + month + "-" + day);
    }
  }

  @Test
  void fromChronologySpec_createsWorkingAlgorithm() {
    ChronologySpec spec =
        new ChronologySpec(
            "chronology",
            "TEST_CALENDAR",
            new ChronologySpec.Metadata("Test Calendar", "A test calendar"),
            new ChronologySpec.Structure(
                JulianDayNumber.JULIAN_EPOCH,
                new ChronologySpec.Week(
                    List.of(
                        "Sunday",
                        "Monday",
                        "Tuesday",
                        "Wednesday",
                        "Thursday",
                        "Friday",
                        "Saturday"),
                    "Monday"),
                List.of(
                    new ChronologySpec.Month("January", 31, null),
                    new ChronologySpec.Month("February", 28, 29),
                    new ChronologySpec.Month("March", 31, null),
                    new ChronologySpec.Month("April", 30, null),
                    new ChronologySpec.Month("May", 31, null),
                    new ChronologySpec.Month("June", 30, null),
                    new ChronologySpec.Month("July", 31, null),
                    new ChronologySpec.Month("August", 31, null),
                    new ChronologySpec.Month("September", 30, null),
                    new ChronologySpec.Month("October", 31, null),
                    new ChronologySpec.Month("November", 30, null),
                    new ChronologySpec.Month("December", 31, null))),
            new ChronologySpec.Algorithms(
                "FORMULA", "year % 4 == 0", null, null, null, null, null, null));

    FormulaAlgorithm algorithm = new FormulaAlgorithm(spec);

    assertEquals("TEST_CALENDAR", algorithm.getChronologyId());
    assertTrue(algorithm.isLeapYear(2000));
    assertFalse(algorithm.isLeapYear(2001));
  }

  @Test
  void getChronologyId_matchesSpecId() {
    ChronologySpec spec =
        new ChronologySpec(
            "chronology",
            "MY_CALENDAR",
            new ChronologySpec.Metadata("My Calendar", ""),
            new ChronologySpec.Structure(
                0L, null, List.of(new ChronologySpec.Month("Month1", 30, null))),
            new ChronologySpec.Algorithms("FORMULA", "false", null, null, null, null, null, null));

    FormulaAlgorithm algorithm = new FormulaAlgorithm(spec);
    assertEquals("MY_CALENDAR", algorithm.getChronologyId());
  }

  @Test
  void monthGetDays_withAndWithoutLeap() {
    ChronologySpec.Month regularMonth = new ChronologySpec.Month("January", 31, null);
    ChronologySpec.Month leapMonth = new ChronologySpec.Month("February", 28, 29);

    assertEquals(31, regularMonth.getDays(false));
    assertEquals(31, regularMonth.getDays(true));
    assertEquals(28, leapMonth.getDays(false));
    assertEquals(29, leapMonth.getDays(true));
  }
}
