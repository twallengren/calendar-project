package com.bdc.chronology;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.JulianDayNumber;
import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** Adapts existing numeric algorithms without changing their profiles or YAML meanings. */
final class NumericChronologyProvider implements ChronologyProvider {
  private final ChronologyAlgorithm algorithm;
  private final ChronologyDescriptor descriptor;

  NumericChronologyProvider(ChronologyAlgorithm algorithm) {
    this.algorithm = algorithm;
    LocalDate from = LocalDate.of(1, 1, 1), to = LocalDate.of(9999, 12, 31);
    var supported = algorithm.supportedYearRange();
    if (supported.isPresent()) {
      int first = supported.get()[0], last = supported.get()[1];
      from = JulianDayNumber.toLocalDate(algorithm.toJdn(first, 1, 1));
      to =
          JulianDayNumber.toLocalDate(
              algorithm.toJdn(last, 12, algorithm.getDaysInMonth(last, 12)));
    } else if (!"ISO".equals(algorithm.getChronologyId())
        && !"JULIAN".equals(algorithm.getChronologyId())) {
      from = JulianDayNumber.toLocalDate(algorithm.toJdn(1, 1, 1));
    }
    descriptor =
        new ChronologyDescriptor(
            algorithm.getChronologyId(),
            "yaml-" + algorithm.getChronologyId().toLowerCase(java.util.Locale.ROOT),
            "committed generated algorithm",
            from,
            to,
            366);
  }

  public ChronologyDescriptor descriptor() {
    return descriptor;
  }

  public void validateMonthCode(String code) {
    if (!code.matches("M(0[1-9]|1[0-2])"))
      throw new IllegalArgumentException("Expected numeric month code M01..M12: " + code);
  }

  private int month(String code) {
    validateMonthCode(code);
    return Integer.parseInt(code.substring(1));
  }

  public List<String> months(int year) {
    return IntStream.rangeClosed(1, 12)
        .mapToObj(i -> String.format(java.util.Locale.ROOT, "M%02d", i))
        .toList();
  }

  public int monthLength(int year, String code) {
    return algorithm.getDaysInMonth(year, month(code));
  }

  public LocalDate monthStart(int year, String monthCode) {
    return JulianDayNumber.toLocalDate(algorithm.toJdn(year, month(monthCode), 1));
  }

  public LocalDate toIso(NativeDate date) {
    if (date.year() < 1)
      throw new UnsupportedChronologyRangeException(
          "Numeric compiler provider requires native year >= 1");
    if (!descriptor.id().equals(date.chronologyId()))
      throw new IllegalArgumentException("Chronology mismatch");
    LocalDate iso =
        JulianDayNumber.toLocalDate(
            algorithm.toJdn(date.year(), month(date.monthCode()), date.day()));
    descriptor.requireSupported(iso);
    return iso;
  }

  public NativeDate fromIso(LocalDate date) {
    descriptor.requireSupported(date);
    ChronologyDate result = algorithm.fromJdn(JulianDayNumber.fromLocalDate(date));
    return new NativeDate(
        descriptor.id(),
        result.year(),
        String.format(java.util.Locale.ROOT, "M%02d", result.month()),
        result.day());
  }
}
