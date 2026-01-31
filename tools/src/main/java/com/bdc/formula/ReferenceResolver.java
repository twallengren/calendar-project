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

  private LocalDate nthWeekdayOfMonth(int year, int month, DayOfWeek weekday, int nth) {
    LocalDate firstOfMonth = LocalDate.of(year, month, 1);
    LocalDate firstOccurrence = firstOfMonth.with(TemporalAdjusters.firstInMonth(weekday));
    return firstOccurrence.plusWeeks(nth - 1);
  }
}
