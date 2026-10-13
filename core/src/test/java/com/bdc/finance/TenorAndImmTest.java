package com.bdc.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TenorAndImmTest {

  @Test
  void parsesMarketSpellings() {
    assertEquals(new Tenor(3, Tenor.Unit.MONTHS), Tenor.parse("3M"));
    assertEquals(new Tenor(-2, Tenor.Unit.WEEKS), Tenor.parse(" -2w "));
    assertEquals(new Tenor(1, Tenor.Unit.YEARS), Tenor.parse("1y"));
    assertEquals(new Tenor(10, Tenor.Unit.DAYS), Tenor.parse("10D"));
    assertEquals(new Tenor(2, Tenor.Unit.BUSINESS_DAYS), Tenor.parse("T+2"));
    assertEquals(new Tenor(-1, Tenor.Unit.BUSINESS_DAYS), Tenor.parse("t-1"));
    assertEquals(new Tenor(5, Tenor.Unit.BUSINESS_DAYS), Tenor.parse("5B"));
    for (String bad : new String[] {"3X", "M", "", "T+", "3 M", "1.5Y"}) {
      assertThrows(IllegalArgumentException.class, () -> Tenor.parse(bad), bad);
    }
  }

  @Test
  void convertsUnits() {
    assertEquals(12, Tenor.parse("1Y").months());
    assertEquals(14, Tenor.parse("2W").days());
    assertTrue(Tenor.parse("6M").isMonthBased());
    assertFalse(Tenor.parse("6D").isMonthBased());
    assertThrows(IllegalStateException.class, () -> Tenor.parse("3M").days());
    assertEquals("-2W", Tenor.parse("-2w").toString());
    assertEquals("2B", Tenor.parse("T+2").toString());
    assertEquals(Frequency.QUARTERLY, Frequency.ofTenor(Tenor.parse("3M")));
    assertEquals(4, Frequency.QUARTERLY.periodsPerYear());
  }

  @Test
  void immDatesAreThirdWednesdays() {
    assertEquals(LocalDate.of(2024, 3, 20), ImmDates.inMonth(2024, 3));
    assertEquals(LocalDate.of(2024, 6, 19), ImmDates.inMonth(2024, 6));
    assertTrue(ImmDates.isImm(LocalDate.of(2024, 3, 20)));
    assertFalse(ImmDates.isImm(LocalDate.of(2024, 3, 21)));
    assertEquals(LocalDate.of(2024, 4, 17), ImmDates.next(LocalDate.of(2024, 3, 20)));
    assertEquals(LocalDate.of(2024, 3, 20), ImmDates.next(LocalDate.of(2024, 3, 19)));
    assertEquals(LocalDate.of(2024, 6, 19), ImmDates.nextQuarterly(LocalDate.of(2024, 3, 20)));
    assertTrue(ImmDates.isQuarterly(LocalDate.of(2024, 6, 19)));
    assertFalse(ImmDates.isQuarterly(LocalDate.of(2024, 4, 17)));
  }

  @Test
  void rollConventionsClipFromTheAnchor() {
    LocalDate jan31 = LocalDate.of(2024, 1, 31);
    assertEquals(LocalDate.of(2024, 2, 29), RollConvention.DAY_OF_MONTH.roll(jan31, 1));
    assertEquals(LocalDate.of(2024, 3, 31), RollConvention.DAY_OF_MONTH.roll(jan31, 2));
    assertEquals(LocalDate.of(2024, 4, 30), RollConvention.EOM.roll(LocalDate.of(2024, 1, 15), 3));
    assertEquals(LocalDate.of(2024, 6, 19), RollConvention.IMM.roll(LocalDate.of(2024, 3, 20), 3));
  }
}
