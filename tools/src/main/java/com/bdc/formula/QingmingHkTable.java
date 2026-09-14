package com.bdc.formula;

import java.time.LocalDate;
import java.util.Map;

/** HKO-published Bright & Clear (Qingming/Ching Ming) dates, copied from annual tables. */
final class QingmingHkTable {
  static final int FROM_YEAR = 2016;
  static final int TO_YEAR = 2029;

  private static final Map<Integer, LocalDate> DATES =
      Map.ofEntries(
          Map.entry(2016, LocalDate.of(2016, 4, 4)),
          Map.entry(2017, LocalDate.of(2017, 4, 4)),
          Map.entry(2018, LocalDate.of(2018, 4, 5)),
          Map.entry(2019, LocalDate.of(2019, 4, 5)),
          Map.entry(2020, LocalDate.of(2020, 4, 4)),
          Map.entry(2021, LocalDate.of(2021, 4, 4)),
          Map.entry(2022, LocalDate.of(2022, 4, 5)),
          Map.entry(2023, LocalDate.of(2023, 4, 5)),
          Map.entry(2024, LocalDate.of(2024, 4, 4)),
          Map.entry(2025, LocalDate.of(2025, 4, 4)),
          Map.entry(2026, LocalDate.of(2026, 4, 5)),
          Map.entry(2027, LocalDate.of(2027, 4, 5)),
          Map.entry(2028, LocalDate.of(2028, 4, 4)),
          Map.entry(2029, LocalDate.of(2029, 4, 4)));

  private QingmingHkTable() {}

  static LocalDate date(int year) {
    LocalDate date = DATES.get(year);
    if (date == null)
      throw new IllegalArgumentException(
          "QINGMING_HK reference table supports "
              + FROM_YEAR
              + ".."
              + TO_YEAR
              + ", requested "
              + year);
    return date;
  }
}
