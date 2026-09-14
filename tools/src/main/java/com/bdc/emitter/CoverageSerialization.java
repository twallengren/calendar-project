package com.bdc.emitter;

import com.bdc.trust.CoverageInterval;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CoverageSerialization {
  private CoverageSerialization() {}

  static List<Map<String, Object>> toMaps(List<CoverageInterval> intervals) {
    return intervals.stream()
        .map(
            interval -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("scope", interval.scope().name());
              row.put("from", interval.from().toString());
              row.put("to", interval.to().toString());
              row.put("quality", interval.quality().name());
              row.put("evidence_ids", interval.evidenceIds());
              return row;
            })
        .toList();
  }
}
