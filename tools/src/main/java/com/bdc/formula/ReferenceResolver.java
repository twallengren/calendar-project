package com.bdc.formula;

import com.bdc.chronology.DateRange;
import com.bdc.model.Reference;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReferenceResolver {
    private final Map<String, List<LocalDate>> resolved = new HashMap<>();

    public void resolve(List<Reference> references, DateRange range) {
        if (references == null) return;

        int[] years = range.isoYearRange();
        for (Reference ref : references) {
            List<LocalDate> dates = new ArrayList<>();
            for (int year = years[0]; year <= years[1]; year++) {
                LocalDate date = switch (ref.formula()) {
                    case "EASTER_WESTERN" -> EasterCalculator.westernEaster(year);
                    default -> throw new IllegalArgumentException("Unknown formula: " + ref.formula());
                };
                if (range.contains(date)) {
                    dates.add(date);
                }
            }
            resolved.put(ref.key(), dates);
        }
    }

    public List<LocalDate> getDates(String key) {
        return resolved.getOrDefault(key, List.of());
    }
}