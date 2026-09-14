package com.bdc.chronology;

import java.time.LocalDate;
import java.util.List;

/** Compiler-only conversion boundary. Month lists are in civil-year order. */
public interface ChronologyProvider {
  ChronologyDescriptor descriptor();

  List<String> months(int year);

  int monthLength(int year, String monthCode);

  /** Structural month boundary; may surround the precise supported ISO interval. */
  LocalDate monthStart(int year, String monthCode);

  default int maximumDayOfMonth(String monthCode) {
    validateMonthCode(monthCode);
    return 31;
  }

  LocalDate toIso(NativeDate date);

  NativeDate fromIso(LocalDate date);

  /** Reject malformed selectors; a valid but absent leap month may then be skipped. */
  void validateMonthCode(String monthCode);

  default NativeDate convert(NativeDate date, ChronologyProvider target) {
    return target.fromIso(toIso(date));
  }
}
