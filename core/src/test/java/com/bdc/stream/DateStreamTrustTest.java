package com.bdc.stream;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.DateRange;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageInterval;
import com.bdc.trust.CoverageIntervals;
import com.bdc.trust.CoverageQuality;
import com.bdc.trust.DayState;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DateStreamTrustTest {

  private static final LocalDate DAY = LocalDate.of(2020, 7, 30);
  private static final DateRange RANGE =
      new DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));

  @Test
  void incompleteScopePreservesScheduledStateButRefusesBooleanAnswer() {
    Event closure = new Event(DAY, EventType.CLOSED, "Scheduled holiday", "fixture");
    CsvDateStream stream =
        new CsvDateStream(
            "CAL",
            List.of(closure),
            RANGE,
            DAY,
            List.of(
                interval(CompletenessScope.SCHEDULED_CLOSURES, CoverageQuality.INCOMPLETE),
                interval(CompletenessScope.EARLY_CLOSES, CoverageQuality.PROJECTED),
                interval(CompletenessScope.UNSCHEDULED_EXCEPTIONS, CoverageQuality.PROJECTED)));

    var assessment = stream.assessment(DAY);
    assertEquals(DayState.UNKNOWN, assessment.state());
    assertEquals(DayState.CLOSED, assessment.scheduledState());
    assertEquals(EventStatus.UNKNOWN, assessment.effectiveConfidence());
    assertEquals(List.of("fixture-source"), assessment.evidenceIds());
    assertEquals(EventStatus.CONFIRMED, assessment.events().get(0).rawStatus());

    UnresolvedDateException error =
        assertThrows(UnresolvedDateException.class, () -> stream.isBusinessDay(DAY));
    assertInstanceOf(OutsideCoverageException.class, error);
    assertEquals(Set.of(CompletenessScope.SCHEDULED_CLOSURES), error.incompleteScopes());
  }

  @Test
  void aMissingExplicitScopeIsIncomplete() {
    CsvDateStream stream =
        new CsvDateStream(
            "CAL",
            List.of(),
            RANGE,
            DAY,
            List.of(interval(CompletenessScope.SCHEDULED_CLOSURES, CoverageQuality.VERIFIED)));

    assertEquals(
        CoverageQuality.INCOMPLETE,
        stream.assessment(DAY).completeness().get(CompletenessScope.EARLY_CLOSES));
    assertThrows(UnresolvedDateException.class, () -> stream.closeTime(DAY));
    assertThrows(UnresolvedDateException.class, () -> stream.eventCountInRange(DAY, DAY));
  }

  @Test
  void legacyArtifactsRemainQueryableAndExposeTheirCompatibilityLimit() {
    CsvDateStream stream = new CsvDateStream("CAL", List.of(), RANGE, DAY);

    assertTrue(stream.isBusinessDay(DAY));
    assertEquals(EventStatus.CONFIRMED, stream.status(DAY));
    assertEquals(
        CoverageQuality.PROJECTED,
        stream.assessment(DAY).completeness().get(CompletenessScope.SCHEDULED_CLOSURES));
  }

  @Test
  void bundledMetadataParserReadsSnakeCaseEvidenceIds() {
    List<CoverageInterval> parsed =
        CoverageIntervals.fromJson(
            List.of(
                Map.of(
                    "scope", "SCHEDULED_CLOSURES",
                    "from", "2020-01-01",
                    "to", "2020-12-31",
                    "quality", "VERIFIED",
                    "evidence_ids", List.of("exchange-calendar"))));

    assertEquals(List.of("exchange-calendar"), parsed.get(0).evidenceIds());
  }

  private static CoverageInterval interval(CompletenessScope scope, CoverageQuality quality) {
    return new CoverageInterval(
        scope, RANGE.start(), RANGE.end(), quality, List.of("fixture-source"));
  }
}
