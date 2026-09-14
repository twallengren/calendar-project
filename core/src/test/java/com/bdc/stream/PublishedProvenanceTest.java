package com.bdc.stream;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.DateRange;
import com.bdc.chronology.NativeDate;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.trust.PublishedEventDetails;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublishedProvenanceTest {
  private static final LocalDate DAY = LocalDate.of(2023, 9, 18);
  private static final Event EVENT =
      new Event(
          DAY,
          EventType.CLOSED,
          "New year",
          "csv",
          "new_year",
          "fixture",
          LocalDate.of(2023, 9, 16),
          null,
          EventStatus.CONFIRMED);

  private Map<String, Object> row(String evidence) {
    return Map.ofEntries(
        Map.entry("date", DAY.toString()),
        Map.entry("type", "CLOSED"),
        Map.entry("description", "New year"),
        Map.entry("key", "new_year"),
        Map.entry("source_module", "fixture"),
        Map.entry("observed_from", "2023-09-16"),
        Map.entry("status", "CONFIRMED"),
        Map.entry("evidence_ids", List.of(evidence)),
        Map.entry("observation_lineage", List.of("2023-09-16", "2023-09-18")),
        Map.entry(
            "nominal_native_date",
            Map.of("chronology_id", "HEBREW", "year", 5784, "month_code", "TISHRI", "day", 1)),
        Map.entry("chronology_profile", "HEBREW_ARITHMETIC"),
        Map.entry("chronology_provider", "ICU4J 78.3"));
  }

  @Test
  void duplicateRowsRetainSeparateEvidenceAndNativeOrigins() {
    var details = PublishedEventDetails.read(List.of(row("a"), row("b")));
    var stream =
        new CsvDateStream(
            "TEST", List.of(EVENT, EVENT), new DateRange(DAY, DAY), null, List.of(), details);
    var assessed = stream.assessment(DAY);
    assertEquals(2, assessed.events().size());
    assertEquals(List.of("a", "b"), assessed.evidenceIds());
    assertEquals(
        new NativeDate("HEBREW", 5784, "TISHRI", 1),
        assessed.events().getFirst().nominalNativeDate());
    assertEquals(
        List.of(LocalDate.of(2023, 9, 16), DAY), assessed.events().getFirst().observationLineage());
  }

  @Test
  void missingOrExtraOccurrencesCannotBeSilentlyAttached() {
    var details = PublishedEventDetails.read(List.of(row("a")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CsvDateStream(
                "TEST", List.of(EVENT, EVENT), new DateRange(DAY, DAY), null, List.of(), details));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CsvDateStream(
                "TEST", List.of(), new DateRange(DAY, DAY), null, List.of(), details));
  }
}
