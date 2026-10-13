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
import com.bdc.trust.DateQuestion;
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
    // UNSCHEDULED_EXCEPTIONS is missing too, so the business-day question is also unresolved.
    assertThrows(UnresolvedDateException.class, () -> stream.isBusinessDay(DAY));
  }

  @Test
  void incompleteEarlyClosesDoNotBlockTheBusinessDayQuestion() {
    Event earlyClose =
        new Event(
            DAY,
            EventType.EARLY_CLOSE,
            "Half day",
            null,
            "half_day",
            "fixture",
            null,
            java.time.LocalTime.of(13, 0),
            EventStatus.CONFIRMED);
    CsvDateStream stream =
        new CsvDateStream(
            "CAL",
            List.of(earlyClose),
            RANGE,
            DAY,
            List.of(
                interval(CompletenessScope.SCHEDULED_CLOSURES, CoverageQuality.VERIFIED),
                interval(CompletenessScope.EARLY_CLOSES, CoverageQuality.INCOMPLETE),
                interval(CompletenessScope.UNSCHEDULED_EXCEPTIONS, CoverageQuality.VERIFIED)));

    var assessment = stream.assessment(DAY);
    assertEquals(DayState.UNKNOWN, assessment.state(), "session state needs early closes");
    assertEquals(EventStatus.UNKNOWN, assessment.effectiveConfidence());
    assertEquals(DayState.OPEN, assessment.businessDayState());
    assertEquals(EventStatus.CONFIRMED, assessment.businessDayConfidence());
    assertTrue(assessment.isBusinessDayKnown());
    assertEquals(
        Set.of(CompletenessScope.EARLY_CLOSES), assessment.incompleteScopes(DateQuestion.SESSION));
    assertEquals(Set.of(), assessment.incompleteScopes(DateQuestion.BUSINESS_DAY));

    assertTrue(stream.isBusinessDay(DAY));
    assertEquals(DAY, stream.nextBusinessDay(DAY.minusDays(1)));
    assertEquals(1, stream.businessDaysInRange(DAY, DAY));
    assertEquals(
        EventStatus.CONFIRMED,
        stream.businessDayOffsetDetailed(DAY.minusDays(1), 1).effectiveConfidence());
    assertEquals(EventStatus.UNKNOWN, stream.status(DAY), "status() keeps the strict view");

    UnresolvedDateException error =
        assertThrows(UnresolvedDateException.class, () -> stream.closeTime(DAY));
    assertEquals(Set.of(CompletenessScope.EARLY_CLOSES), error.incompleteScopes());
    assertThrows(UnresolvedDateException.class, () -> stream.isEarlyClose(DAY));
    assertThrows(UnresolvedDateException.class, () -> stream.memberCloses(DAY));
  }

  @Test
  void incompleteUnscheduledExceptionsBlockTheBusinessDayQuestion() {
    CsvDateStream stream =
        new CsvDateStream(
            "CAL",
            List.of(),
            RANGE,
            DAY,
            List.of(
                interval(CompletenessScope.SCHEDULED_CLOSURES, CoverageQuality.VERIFIED),
                interval(CompletenessScope.EARLY_CLOSES, CoverageQuality.VERIFIED),
                interval(CompletenessScope.UNSCHEDULED_EXCEPTIONS, CoverageQuality.INCOMPLETE)));

    var assessment = stream.assessment(DAY);
    assertEquals(DayState.UNKNOWN, assessment.businessDayState());
    assertEquals(EventStatus.UNKNOWN, assessment.businessDayConfidence());
    UnresolvedDateException error =
        assertThrows(UnresolvedDateException.class, () -> stream.isBusinessDay(DAY));
    assertEquals(Set.of(CompletenessScope.UNSCHEDULED_EXCEPTIONS), error.incompleteScopes());
  }

  @Test
  void projectedEarlyCloseRowsDoNotLowerBusinessDayConfidence() {
    Event projectedHalfDay =
        new Event(
            DAY,
            EventType.EARLY_CLOSE,
            "Half day",
            null,
            "half_day",
            "fixture",
            null,
            java.time.LocalTime.of(13, 0),
            EventStatus.PROJECTED);
    Event projectedClosure =
        new Event(
            DAY.plusDays(1),
            EventType.CLOSED,
            "Eid",
            null,
            "eid",
            "fixture",
            null,
            null,
            EventStatus.PROJECTED);
    CsvDateStream stream =
        new CsvDateStream(
            "CAL",
            List.of(projectedHalfDay, projectedClosure),
            RANGE,
            RANGE.end(),
            List.of(
                interval(CompletenessScope.SCHEDULED_CLOSURES, CoverageQuality.VERIFIED),
                interval(CompletenessScope.EARLY_CLOSES, CoverageQuality.VERIFIED),
                interval(CompletenessScope.UNSCHEDULED_EXCEPTIONS, CoverageQuality.VERIFIED)));

    assertEquals(EventStatus.PROJECTED, stream.assessment(DAY).effectiveConfidence());
    assertEquals(EventStatus.CONFIRMED, stream.assessment(DAY).businessDayConfidence());
    assertEquals(EventStatus.PROJECTED, stream.assessment(DAY.plusDays(1)).businessDayConfidence());
    assertEquals(DayState.CLOSED, stream.assessment(DAY.plusDays(1)).businessDayState());
  }

  @Test
  void jointBusinessDayViewIsTheWorstMember() {
    var open =
        new CsvDateStream(
            "OPEN",
            List.of(),
            RANGE,
            RANGE.end(),
            List.of(
                interval(CompletenessScope.SCHEDULED_CLOSURES, CoverageQuality.VERIFIED),
                interval(CompletenessScope.EARLY_CLOSES, CoverageQuality.INCOMPLETE),
                interval(CompletenessScope.UNSCHEDULED_EXCEPTIONS, CoverageQuality.PROJECTED)));
    var closed =
        new CsvDateStream(
            "CLOSED",
            List.of(new Event(DAY, EventType.CLOSED, "Holiday", "fixture")),
            RANGE,
            RANGE.end(),
            List.of(
                interval(CompletenessScope.SCHEDULED_CLOSURES, CoverageQuality.VERIFIED),
                interval(CompletenessScope.EARLY_CLOSES, CoverageQuality.VERIFIED),
                interval(CompletenessScope.UNSCHEDULED_EXCEPTIONS, CoverageQuality.VERIFIED)));
    var joint = JointDateStream.joint(List.of(open, closed));

    var assessment = joint.assessment(DAY);
    assertEquals(DayState.CLOSED, assessment.businessDayState());
    assertEquals(EventStatus.PROJECTED, assessment.businessDayConfidence());
    assertEquals(DayState.UNKNOWN, assessment.state());
    assertFalse(joint.isBusinessDay(DAY));
    assertThrows(UnresolvedDateException.class, () -> joint.memberCloses(DAY));
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

  @Test
  void closedFirstMemberCannotHideUnknownSecondMember() {
    var closed =
        new CsvDateStream(
            "CLOSED", List.of(new Event(DAY, EventType.CLOSED, "Holiday", "fixture")), RANGE);
    var unknown =
        new CsvDateStream(
            "UNKNOWN",
            List.of(),
            RANGE,
            null,
            List.of(interval(CompletenessScope.SCHEDULED_CLOSURES, CoverageQuality.INCOMPLETE)));
    var joint = JointDateStream.joint(List.of(closed, unknown));
    assertThrows(UnresolvedDateException.class, () -> joint.isBusinessDay(DAY));
    assertEquals(DayState.UNKNOWN, joint.assessment(DAY).state());
  }

  @Test
  void overlappingIntervalsUseTheLeastCertainQuality() {
    CsvDateStream stream =
        new CsvDateStream(
            "CAL",
            List.of(),
            RANGE,
            DAY,
            List.of(
                interval(CompletenessScope.SCHEDULED_CLOSURES, CoverageQuality.VERIFIED),
                new CoverageInterval(
                    CompletenessScope.SCHEDULED_CLOSURES,
                    DAY,
                    DAY,
                    CoverageQuality.INCOMPLETE,
                    List.of("disputed-date")),
                interval(CompletenessScope.EARLY_CLOSES, CoverageQuality.VERIFIED),
                interval(CompletenessScope.UNSCHEDULED_EXCEPTIONS, CoverageQuality.VERIFIED)));

    assertEquals(DayState.UNKNOWN, stream.assessment(DAY).state());
  }

  @Test
  void malformedQualityCannotFallBackToLegacyOpenAnswers() {
    for (Object malformed : List.of(Map.of(), "INCOMPLETE", 0)) {
      assertThrows(IllegalArgumentException.class, () -> CoverageIntervals.fromJson(malformed));
    }
    assertEquals(List.of(), CoverageIntervals.fromJson(null));
    for (Object malformed : List.of(List.of(), "", 0, false)) {
      assertThrows(
          IllegalArgumentException.class, () -> CoverageIntervals.coverageObject(malformed));
    }
  }

  private static CoverageInterval interval(CompletenessScope scope, CoverageQuality quality) {
    return new CoverageInterval(
        scope, RANGE.start(), RANGE.end(), quality, List.of("fixture-source"));
  }
}
