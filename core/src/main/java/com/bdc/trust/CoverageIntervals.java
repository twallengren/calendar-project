package com.bdc.trust;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Dependency-free parser for coverage quality embedded in bundled metadata JSON. */
public final class CoverageIntervals {
  private CoverageIntervals() {}

  public static List<CoverageInterval> fromJson(Object value) {
    if (!(value instanceof List<?> rows)) {
      return List.of();
    }
    List<CoverageInterval> result = new ArrayList<>();
    for (Object row : rows) {
      if (!(row instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException("coverage.quality entries must be objects");
      }
      Object evidence =
          map.containsKey("evidence_ids") ? map.get("evidence_ids") : map.get("evidenceIds");
      List<String> evidenceIds =
          evidence instanceof List<?> ids ? ids.stream().map(String::valueOf).toList() : List.of();
      result.add(
          new CoverageInterval(
              CompletenessScope.valueOf(String.valueOf(map.get("scope"))),
              LocalDate.parse(String.valueOf(map.get("from"))),
              LocalDate.parse(String.valueOf(map.get("to"))),
              CoverageQuality.valueOf(String.valueOf(map.get("quality"))),
              evidenceIds));
    }
    return List.copyOf(result);
  }
}
