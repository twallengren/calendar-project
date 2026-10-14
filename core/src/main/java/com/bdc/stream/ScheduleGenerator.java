package com.bdc.stream;

import com.bdc.finance.StubConvention;
import com.bdc.finance.Tenor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rolls a {@link ScheduleDefinition} into unadjusted periods, then adjusts them on a calendar. */
final class ScheduleGenerator {
  private ScheduleGenerator() {}

  /** At most this many periods; a weekly schedule over a century is still well inside it. */
  private static final int MAX_PERIODS = 10_000;

  static Schedule generate(DateStream stream, ScheduleDefinition definition) {
    List<LocalDate> unadjusted = unadjustedDates(definition);
    FinancialDateOperations.Tracker tracker = new FinancialDateOperations.Tracker(stream);
    List<LocalDate> adjusted = new ArrayList<>(unadjusted.size());
    for (LocalDate date : unadjusted) {
      adjusted.add(FinancialDateOperations.adjust(stream, date, definition.convention(), tracker));
    }
    int stubIndex = stubIndex(definition, unadjusted);
    List<SchedulePeriod> periods = new ArrayList<>(unadjusted.size() - 1);
    for (int i = 0; i + 1 < unadjusted.size(); i++) {
      periods.add(
          new SchedulePeriod(
              unadjusted.get(i),
              unadjusted.get(i + 1),
              adjusted.get(i),
              adjusted.get(i + 1),
              i == stubIndex));
    }
    return new Schedule(definition, periods, tracker.confidence(), tracker.examined());
  }

  /**
   * The unadjusted boundary dates, start through end. Forward stubs roll from the start and put any
   * irregular period last; backward stubs roll from the end and put it first.
   */
  static List<LocalDate> unadjustedDates(ScheduleDefinition definition) {
    boolean backward =
        definition.stub() == StubConvention.SHORT_INITIAL
            || definition.stub() == StubConvention.LONG_INITIAL;
    LocalDate anchor = backward ? definition.end() : definition.start();
    LocalDate limit = backward ? definition.start() : definition.end();
    int direction = backward ? -1 : 1;
    List<LocalDate> dates = new ArrayList<>();
    dates.add(anchor);
    LocalDate rolled = null;
    for (int k = 1; k <= MAX_PERIODS; k++) {
      rolled = roll(definition, anchor, direction * k);
      if (backward ? !rolled.isAfter(limit) : !rolled.isBefore(limit)) {
        break;
      }
      dates.add(rolled);
    }
    if (rolled == null || (backward ? rolled.isAfter(limit) : rolled.isBefore(limit))) {
      throw new IllegalArgumentException(
          "Schedule from "
              + definition.start()
              + " to "
              + definition.end()
              + " exceeds "
              + MAX_PERIODS
              + " periods");
    }
    if (!rolled.equals(limit)) {
      switch (definition.stub()) {
        case NONE ->
            throw new IllegalArgumentException(
                "Schedule end "
                    + definition.end()
                    + " is not on the "
                    + definition.frequency()
                    + " cycle from "
                    + definition.start()
                    + " and the stub convention is NONE");
        case LONG_FINAL, LONG_INITIAL -> {
          if (dates.size() > 1) {
            dates.remove(dates.size() - 1);
          }
        }
        default -> {}
      }
    }
    dates.add(limit);
    if (backward) {
      Collections.reverse(dates);
    }
    return dates;
  }

  private static LocalDate roll(ScheduleDefinition definition, LocalDate anchor, int periods) {
    Tenor tenor = definition.tenor();
    if (tenor.isMonthBased()) {
      return definition.roll().roll(anchor, Math.multiplyExact(tenor.months(), periods));
    }
    return anchor.plusDays(Math.multiplyExact((long) tenor.days(), periods));
  }

  /** The index of the irregular period, or -1 when every period is regular. */
  private static int stubIndex(ScheduleDefinition definition, List<LocalDate> dates) {
    if (dates.size() < 2) {
      return -1;
    }
    boolean backward =
        definition.stub() == StubConvention.SHORT_INITIAL
            || definition.stub() == StubConvention.LONG_INITIAL;
    int index = backward ? 0 : dates.size() - 2;
    LocalDate periodStart = dates.get(index);
    LocalDate periodEnd = dates.get(index + 1);
    LocalDate regular =
        backward ? roll(definition, periodEnd, -1) : roll(definition, periodStart, 1);
    return (backward ? regular.equals(periodStart) : regular.equals(periodEnd)) ? -1 : index;
  }
}
