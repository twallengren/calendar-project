package com.bdc.chronology.ontology.algorithms;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.ChronologySpec;
import java.util.List;

/**
 * Algorithm implementation for lookup-table-based calendars.
 *
 * <p>This algorithm handles calendars whose month boundaries are defined by a pre-computed table of
 * (year, month, jdn, length) entries. Suitable for calendars like Umm al-Qura where month
 * boundaries are determined by observation or official decree rather than a formula.
 *
 * <p>Data is stored in flat arrays indexed by {@code (year - minYear) * 12 + (month - 1)}.
 */
public class LookupTableAlgorithm implements ChronologyAlgorithm {

  private final String chronologyId;
  private final int minYear;
  private final int maxYear;
  private final long[] monthJdn;
  private final int[] monthLen;

  /**
   * Creates a LookupTableAlgorithm from a ChronologySpec.
   *
   * @param spec the chronology specification containing month entries in algorithms.months
   */
  public LookupTableAlgorithm(ChronologySpec spec) {
    this.chronologyId = spec.id();

    List<ChronologySpec.MonthEntry> months = spec.algorithms().months();
    if (months == null || months.isEmpty()) {
      throw new IllegalArgumentException(
          "LOOKUP_TABLE algorithm requires non-empty months list for " + spec.id());
    }

    this.minYear = months.stream().mapToInt(ChronologySpec.MonthEntry::year).min().orElse(1);
    this.maxYear = months.stream().mapToInt(ChronologySpec.MonthEntry::year).max().orElse(1);

    int yearCount = maxYear - minYear + 1;
    this.monthJdn = new long[yearCount * 12];
    this.monthLen = new int[yearCount * 12];

    for (ChronologySpec.MonthEntry entry : months) {
      int idx = idx(entry.year(), entry.month());
      monthJdn[idx] = entry.jdn();
      monthLen[idx] = entry.length();
    }
  }

  private int idx(int year, int month) {
    return (year - minYear) * 12 + (month - 1);
  }

  @Override
  public String getChronologyId() {
    return chronologyId;
  }

  @Override
  public long toJdn(int year, int month, int day) {
    if (!isValidDate(year, month, day)) {
      throw new IllegalArgumentException(
          "Invalid date: " + year + "-" + month + "-" + day + " in " + chronologyId);
    }
    return monthJdn[idx(year, month)] + day - 1;
  }

  @Override
  public ChronologyDate fromJdn(long jdn) {
    if (jdn < monthJdn[0]) {
      throw new IllegalArgumentException("JDN " + jdn + " is before supported range");
    }
    // Linear scan for year
    int year = minYear;
    for (int y = minYear; y <= maxYear; y++) {
      if (monthJdn[(y - minYear) * 12] > jdn) break;
      year = y;
    }
    // Find month
    int base = (year - minYear) * 12;
    for (int m = 0; m < 12; m++) {
      long monthStart = monthJdn[base + m];
      int len = monthLen[base + m];
      if (jdn >= monthStart && jdn < monthStart + len) {
        int day = (int) (jdn - monthStart) + 1;
        return new ChronologyDate(chronologyId, year, m + 1, day);
      }
    }
    throw new IllegalArgumentException("JDN " + jdn + " outside supported range");
  }

  @Override
  public boolean isValidDate(int year, int month, int day) {
    if (year < minYear || year > maxYear) return false;
    if (month < 1 || month > 12) return false;
    if (day < 1) return false;
    return day <= getDaysInMonth(year, month);
  }

  @Override
  public int getDaysInMonth(int year, int month) {
    if (month < 1 || month > 12) {
      throw new IllegalArgumentException("Invalid month: " + month);
    }
    if (year < minYear || year > maxYear) {
      throw new IllegalArgumentException(
          "Year " + year + " outside supported range [" + minYear + ", " + maxYear + "]");
    }
    return monthLen[idx(year, month)];
  }

  @Override
  public boolean isLeapYear(int year) {
    if (year < minYear || year > maxYear) return false;
    int total = 0;
    int base = (year - minYear) * 12;
    for (int m = 0; m < 12; m++) {
      total += monthLen[base + m];
    }
    return total == 355;
  }
}
