package com.bdc.finance;

/** A regular period frequency for schedules and ACT/ACT ICMA, with its periods per year. */
public enum Frequency {
  ANNUAL(Tenor.of(1, Tenor.Unit.YEARS), 1),
  SEMI_ANNUAL(Tenor.of(6, Tenor.Unit.MONTHS), 2),
  QUARTERLY(Tenor.of(3, Tenor.Unit.MONTHS), 4),
  BI_MONTHLY(Tenor.of(2, Tenor.Unit.MONTHS), 6),
  MONTHLY(Tenor.of(1, Tenor.Unit.MONTHS), 12),
  FOUR_WEEKLY(Tenor.of(4, Tenor.Unit.WEEKS), 13),
  BI_WEEKLY(Tenor.of(2, Tenor.Unit.WEEKS), 26),
  WEEKLY(Tenor.of(1, Tenor.Unit.WEEKS), 52);

  private final Tenor tenor;
  private final int periodsPerYear;

  Frequency(Tenor tenor, int periodsPerYear) {
    this.tenor = tenor;
    this.periodsPerYear = periodsPerYear;
  }

  /** The tenor of one period. */
  public Tenor tenor() {
    return tenor;
  }

  /** Periods per year, as used by ACT/ACT ICMA. */
  public int periodsPerYear() {
    return periodsPerYear;
  }

  /** The frequency with the given tenor, if one is defined. */
  public static Frequency ofTenor(Tenor tenor) {
    for (Frequency frequency : values()) {
      if (frequency.tenor.equals(tenor)) {
        return frequency;
      }
    }
    throw new IllegalArgumentException("No standard frequency with tenor " + tenor);
  }
}
