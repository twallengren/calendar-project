package com.bdc.stream;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.DateRange;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageInterval;
import com.bdc.trust.CoverageQuality;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialDateOperationsTest {
  private static final DateRange RANGE =
      new DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2030, 12, 31));

  @Test
  void unknownCannotBeUsedAsRawEventStatus() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Event(
                LocalDate.of(2025, 1, 1),
                EventType.CLOSED,
                "Closed",
                "fixture",
                null,
                null,
                null,
                null,
                EventStatus.UNKNOWN));
  }

  @Test
  void conventionsAndModifiedReversalRetainTheWholeConfidencePath() {
    LocalDate date = LocalDate.of(2021, 1, 31);
    CsvDateStream stream =
        stream(
            List.of(
                closed(date),
                closed(LocalDate.of(2021, 2, 1)),
                closed(LocalDate.of(2021, 2, 2)),
                closed(LocalDate.of(2021, 2, 3)),
                closed(LocalDate.of(2021, 2, 4))),
            LocalDate.of(2021, 1, 31),
            null);

    assertEquals(date, stream.adjust(date, BusinessDayConvention.UNADJUSTED));
    assertEquals(LocalDate.of(2021, 2, 5), stream.adjust(date, BusinessDayConvention.FOLLOWING));
    DateOperationResult modified =
        stream.adjustDetailed(date, BusinessDayConvention.MODIFIED_FOLLOWING);
    assertEquals(LocalDate.of(2021, 1, 30), modified.resultDate());
    assertEquals(EventStatus.PROJECTED, modified.effectiveConfidence());
    assertEquals(
        List.of(
            date,
            LocalDate.of(2021, 2, 1),
            LocalDate.of(2021, 2, 2),
            LocalDate.of(2021, 2, 3),
            LocalDate.of(2021, 2, 4),
            LocalDate.of(2021, 2, 5),
            LocalDate.of(2021, 1, 30)),
        modified.examinedDates());
    assertEquals(LocalDate.of(2021, 1, 30), stream.adjust(date, BusinessDayConvention.PRECEDING));
  }

  @Test
  void modifiedPrecedingReversesFromAClosedMonthBoundary() {
    LocalDate date = LocalDate.of(2021, 3, 1);
    CsvDateStream stream =
        stream(
            List.of(
                closed(date),
                closed(LocalDate.of(2021, 2, 28)),
                closed(LocalDate.of(2021, 2, 27)),
                closed(LocalDate.of(2021, 2, 26))),
            null,
            null);
    DateOperationResult result =
        stream.adjustDetailed(date, BusinessDayConvention.MODIFIED_PRECEDING);
    assertEquals(LocalDate.of(2021, 3, 2), result.resultDate());
    assertEquals(
        List.of(
            date,
            LocalDate.of(2021, 2, 28),
            LocalDate.of(2021, 2, 27),
            LocalDate.of(2021, 2, 26),
            LocalDate.of(2021, 2, 25),
            LocalDate.of(2021, 3, 2)),
        result.examinedDates());
  }

  @Test
  void modifiedFollowingTreatsTheSameMonthInTheNextYearAsCrossingTheMonth() {
    LocalDate date = LocalDate.of(2021, 1, 31);
    List<Event> closed = new ArrayList<>();
    for (LocalDate day = date; !day.isAfter(LocalDate.of(2022, 1, 1)); day = day.plusDays(1)) {
      closed.add(closed(day));
    }
    CsvDateStream stream = stream(closed, null, null);

    DateOperationResult result =
        stream.adjustDetailed(date, BusinessDayConvention.MODIFIED_FOLLOWING);

    assertEquals(LocalDate.of(2021, 1, 30), result.resultDate());
    assertTrue(result.examinedDates().contains(LocalDate.of(2022, 1, 2)));
    assertEquals(LocalDate.of(2021, 1, 30), result.examinedDates().getLast());
  }

  @Test
  void unknownDateInFirstModifiedDirectionFailsBeforeReversal() {
    LocalDate date = LocalDate.of(2021, 1, 31);
    List<CoverageInterval> quality = new ArrayList<>();
    for (CompletenessScope scope : CompletenessScope.values()) {
      quality.add(
          new CoverageInterval(
              scope, RANGE.start(), date, CoverageQuality.VERIFIED, List.of("fixture")));
    }
    CsvDateStream stream = stream(List.of(closed(date)), null, quality);
    assertThrows(
        UnresolvedDateException.class,
        () -> stream.adjust(date, BusinessDayConvention.MODIFIED_FOLLOWING));
  }

  @Test
  void zeroOffsetAndUnadjustedAreIdentityWithoutClaimingBusinessState() {
    LocalDate unknown = LocalDate.of(2040, 1, 1);
    CsvDateStream stream = stream(List.of(), null, null);
    DateOperationResult offset = stream.businessDayOffsetDetailed(unknown, 0);
    assertEquals(unknown, offset.resultDate());
    assertEquals(EventStatus.UNKNOWN, offset.effectiveConfidence());
    assertEquals(List.of(unknown), offset.examinedDates());
    assertEquals(unknown, stream.nthBusinessDay(unknown, 0));

    DateOperationResult unadjusted =
        stream.adjustDetailed(unknown, BusinessDayConvention.UNADJUSTED);
    assertEquals(unknown, unadjusted.resultDate());
    assertEquals(EventStatus.UNKNOWN, unadjusted.effectiveConfidence());
  }

  @Test
  void monthAdvanceClipsLeapFebruaryAndPreservesExplicitBusinessMonthEnd() {
    List<Event> weekends =
        List.of(
            closed(LocalDate.of(2021, 1, 31)),
            closed(LocalDate.of(2021, 2, 27)),
            closed(LocalDate.of(2021, 2, 28)));
    CsvDateStream stream = stream(weekends, null, null);
    LocalDate sourceLastBusiness = LocalDate.of(2021, 1, 30);
    assertEquals(
        LocalDate.of(2021, 2, 26),
        stream.advanceMonths(sourceLastBusiness, 1, BusinessDayConvention.FOLLOWING, true));
    assertEquals(
        LocalDate.of(2021, 3, 1),
        stream.advanceMonths(LocalDate.of(2021, 1, 31), 1, BusinessDayConvention.FOLLOWING, true));
    assertEquals(
        LocalDate.of(2024, 2, 29),
        stream.advanceMonths(
            LocalDate.of(2024, 3, 31), -1, BusinessDayConvention.UNADJUSTED, false));
  }

  @Test
  void lastBusinessDayAndMinimumIntegerOffsetDoNotOverflow() {
    CsvDateStream stream =
        stream(
            List.of(closed(LocalDate.of(2021, 2, 27)), closed(LocalDate.of(2021, 2, 28))),
            null,
            null);
    assertEquals(
        LocalDate.of(2021, 2, 26), stream.lastBusinessDayOfMonth(LocalDate.of(2021, 2, 10)));
    LocalDate start = LocalDate.of(2020, 1, 1);
    assertThrows(
        OutsideCoverageException.class, () -> stream.nthBusinessDay(start, Integer.MIN_VALUE));
  }

  @Test
  void lastBusinessDayCannotEscapeAFullyClosedMonth() {
    List<Event> closed = new ArrayList<>();
    for (LocalDate day = LocalDate.of(2021, 2, 1);
        !day.isAfter(LocalDate.of(2021, 2, 28));
        day = day.plusDays(1)) {
      closed.add(closed(day));
    }
    CsvDateStream stream = stream(closed, null, null);
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> stream.lastBusinessDayOfMonth(LocalDate.of(2021, 2, 10)));
    assertTrue(failure.getMessage().contains("2021-02"));
  }

  @Test
  void nonzeroUnadjustedMonthAdvanceStillRequiresAResolvedDestination() {
    CsvDateStream stream = stream(List.of(), null, null);
    assertThrows(
        OutsideCoverageException.class,
        () ->
            stream.advanceMonths(LocalDate.of(2030, 12, 15), 1, BusinessDayConvention.UNADJUSTED));
  }

  @Test
  void jointMemberClosesRetainTimezoneAndUnknownMembersCannotBeHidden() {
    LocalDate day = LocalDate.of(2025, 7, 3);
    CsvDateStream ny =
        stream(
            List.of(early(day, LocalTime.of(13, 0))),
            null,
            null,
            ZoneId.of("America/New_York"),
            "NY");
    CsvDateStream london =
        stream(
            List.of(early(day, LocalTime.of(12, 30))),
            null,
            null,
            ZoneId.of("Europe/London"),
            "LDN");
    DateStream joint = JointDateStream.joint(ny, london);
    assertEquals(
        List.of(
            new MemberClose("NY", ZoneId.of("America/New_York"), LocalTime.of(13, 0)),
            new MemberClose("LDN", ZoneId.of("Europe/London"), LocalTime.of(12, 30))),
        joint.memberCloses(day));

    CsvDateStream unknown =
        stream(
            List.of(),
            null,
            List.of(
                new CoverageInterval(
                    CompletenessScope.SCHEDULED_CLOSURES,
                    RANGE.start(),
                    RANGE.end(),
                    CoverageQuality.INCOMPLETE,
                    List.of("gap"))),
            null,
            "UNKNOWN");
    assertThrows(
        UnresolvedDateException.class, () -> JointDateStream.joint(ny, unknown).memberCloses(day));
  }

  private static CsvDateStream stream(
      List<Event> events, LocalDate verifiedThrough, List<CoverageInterval> quality) {
    return stream(events, verifiedThrough, quality, null, "CAL");
  }

  private static CsvDateStream stream(
      List<Event> events,
      LocalDate verifiedThrough,
      List<CoverageInterval> quality,
      ZoneId timezone,
      String id) {
    return new CsvDateStream(id, events, RANGE, verifiedThrough, quality, null, timezone);
  }

  private static Event closed(LocalDate date) {
    return new Event(date, EventType.CLOSED, "Closed", "fixture");
  }

  private static Event early(LocalDate date, LocalTime time) {
    return new Event(
        date,
        EventType.EARLY_CLOSE,
        "Early",
        "fixture",
        null,
        null,
        null,
        time,
        EventStatus.CONFIRMED);
  }
}
