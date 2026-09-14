package com.bdc.formula;

import com.bdc.chronology.DateRange;
import com.bdc.model.Reference;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReferenceResolver {
  private final Map<String, List<LocalDate>> resolved = new HashMap<>();

  public void resolve(List<Reference> references, DateRange range) {
    int[] years = range.isoYearRange();
    for (Reference ref : references) {
      List<LocalDate> dates = new ArrayList<>();
      for (int year = years[0]; year <= years[1]; year++) {
        LocalDate date =
            switch (ref.formula()) {
              case "EASTER_WESTERN" -> EasterCalculator.westernEaster(year);
              case "THANKSGIVING_US" -> nthWeekdayOfMonth(year, 11, DayOfWeek.THURSDAY, 4);
              case "EQUINOX_VERNAL_JP" -> vernalEquinoxJp(year);
              case "EQUINOX_AUTUMNAL_JP" -> autumnalEquinoxJp(year);
              default -> throw new IllegalArgumentException("Unknown formula: " + ref.formula());
            };
        // Don't filter by range here - the reference date (e.g., Easter) may be
        // outside the query range while derived dates (e.g., Good Friday = Easter - 2)
        // are inside. Let expandRelativeToReference filter after applying offsets.
        dates.add(date);
      }
      resolved.put(ref.key(), dates);
    }
  }

  public List<LocalDate> getDates(String key) {
    return resolved.getOrDefault(key, List.of());
  }

  public boolean hasReference(String key) {
    return resolved.containsKey(key);
  }

  /**
   * Vernal Equinox Day (春分の日), the Japanese national holiday.
   *
   * <p>The date is fixed each February by the Cabinet Office from the National Astronomical
   * Observatory's almanac and therefore cannot be computed exactly in advance. This is the standard
   * approximation, valid for 1900-2099:
   *
   * <pre>day = floor(20.8431 + 0.242194 * (year - 1980) - floor((year - 1980) / 4))</pre>
   *
   * <p>{@code EquinoxCalculatorTest} checks every year from 2000 to 2027 against the Cabinet
   * Office's published list. Years past that list are projections and the calendar marks them
   * {@code status: PROJECTED}.
   */
  static LocalDate vernalEquinoxJp(int year) {
    return LocalDate.of(year, 3, equinoxDay(20.8431, year));
  }

  /**
   * Autumnal Equinox Day (秋分の日), the Japanese national holiday. Same caveats as {@link
   * #vernalEquinoxJp(int)}; the constant is 23.2488.
   */
  static LocalDate autumnalEquinoxJp(int year) {
    return LocalDate.of(year, 9, equinoxDay(23.2488, year));
  }

  private static int equinoxDay(double base, int year) {
    if (year < 1900 || year > 2099) {
      throw new IllegalArgumentException(
          "Japanese equinox approximation is only valid for 1900-2099, got " + year);
    }
    return (int) Math.floor(base + 0.242194 * (year - 1980) - Math.floorDiv(year - 1980, 4));
  }

  private LocalDate nthWeekdayOfMonth(int year, int month, DayOfWeek weekday, int nth) {
    LocalDate firstOfMonth = LocalDate.of(year, month, 1);
    LocalDate firstOccurrence = firstOfMonth.with(TemporalAdjusters.firstInMonth(weekday));
    return firstOccurrence.plusWeeks(nth - 1);
  }
}
