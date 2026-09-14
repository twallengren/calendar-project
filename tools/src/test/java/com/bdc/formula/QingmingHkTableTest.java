package com.bdc.formula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bdc.chronology.DateRange;
import com.bdc.model.Reference;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rows transcribed from HKO annual Gregorian-Lunar conversion text tables. */
class QingmingHkTableTest {
  @Test
  void returnsEveryBoundedHkoBrightAndClearDate() {
    int[] days = {4, 4, 5, 5, 4, 4, 5, 5, 4, 4, 5, 5, 4, 4};
    for (int year = 2016; year <= 2029; year++) {
      assertEquals(LocalDate.of(year, 4, days[year - 2016]), QingmingHkTable.date(year));
    }
  }

  @Test
  void referenceResolverUsesTableAndRejectsOutsideIt() {
    var resolver = new ReferenceResolver();
    resolver.resolve(
        List.of(new Reference("ching_ming", "QINGMING_HK")),
        new DateRange(LocalDate.of(2018, 1, 1), LocalDate.of(2027, 12, 31)));
    assertEquals(10, resolver.getDates("ching_ming").size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            resolver.resolve(
                List.of(new Reference("ching_ming", "QINGMING_HK")),
                new DateRange(LocalDate.of(2015, 1, 1), LocalDate.of(2016, 12, 31))));
    assertThrows(IllegalArgumentException.class, () -> QingmingHkTable.date(2030));
  }
}
