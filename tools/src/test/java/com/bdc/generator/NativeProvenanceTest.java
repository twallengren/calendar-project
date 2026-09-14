package com.bdc.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.emitter.EventDetailsEmitter;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.EventType;
import com.bdc.resolver.SpecResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeProvenanceTest {
  @TempDir Path temp;

  @Test
  void nativeOriginsSurviveObservationDuplicatesAndNestedRanges() throws Exception {
    Files.writeString(
        temp.resolve("TEST.yaml"),
        """
        kind: calendar
        id: TEST
        weekend_shift_policy: NEXT_AVAILABLE_WEEKDAY
        event_sources:
          - key: new_year
            name: Hebrew new year
            shiftable: true
            source: [{id: independent-notice}]
            rule:
              type: native_explicit_dates
              key: new_year
              name: Hebrew new year
              dates:
                - {chronology_id: HEBREW, year: 5784, month_code: TISHRI, day: 1}
                - {chronology_id: HEBREW, year: 5784, month_code: TISHRI, day: 1}
        """);
    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(temp);
    registry.assertNoLoadErrors();
    var spec = new SpecResolver(registry).resolve("TEST");
    var from = LocalDate.of(2023, 9, 18);
    var to = LocalDate.of(2023, 9, 19);
    var generator = new EventGenerator();
    var narrow = generator.generateWithDetails(spec, from, to);
    var wide =
        generator.generateWithDetails(spec, from.minusYears(1), to.plusYears(1)).stream()
            .filter(e -> !e.event().date().isBefore(from) && !e.event().date().isAfter(to))
            .toList();
    assertEquals(narrow, wide);
    assertEquals(2, narrow.size());
    for (var detail : narrow) {
      assertEquals(EventType.CLOSED, detail.event().type());
      assertEquals("TISHRI", detail.provenance().nominalNativeDate().monthCode());
      assertEquals(1, detail.provenance().nominalNativeDate().day());
      assertEquals("fixed-arithmetic-hebrew-civil", detail.provenance().chronologyProfile());
      assertEquals(java.util.List.of("independent-notice"), detail.provenance().evidenceIds());
      assertEquals(LocalDate.of(2023, 9, 16), detail.provenance().observationLineage().getFirst());
      assertEquals(detail.event().date(), detail.provenance().observationLineage().getLast());
    }
    var rows = EventDetailsEmitter.rows(narrow);
    assertEquals(2, rows.size());
    assertTrue(rows.getFirst().containsKey("nominal_native_date"));
    assertEquals(
        narrow.stream().map(CompiledEvent::event).toList(), generator.generate(spec, from, to));
    // A delta targeting another date must not collapse equal occurrences sharing one identity.
    String yaml =
        Files.readString(temp.resolve("TEST.yaml")).replace("shiftable: true", "shiftable: false")
            + "deltas:\n  - action: remove\n    key: new_year\n    date: 2023-09-01\n";
    Files.writeString(temp.resolve("TEST.yaml"), yaml);
    SpecRegistry withDelta = new SpecRegistry();
    withDelta.loadCalendarsFromDirectory(temp);
    withDelta.assertNoLoadErrors();
    var nominal = LocalDate.of(2023, 9, 16);
    var duplicates =
        generator.generateWithDetails(
            new SpecResolver(withDelta).resolve("TEST"), nominal, nominal);
    assertEquals(2, duplicates.size());
    assertEquals(duplicates.getFirst(), duplicates.getLast());
  }
}
