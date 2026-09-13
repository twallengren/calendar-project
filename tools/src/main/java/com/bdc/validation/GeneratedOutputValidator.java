package com.bdc.validation;

import com.bdc.chronology.DateRange;
import com.bdc.model.*;
import java.time.LocalDate;
import java.util.*;

/** Checks generated events for properties that only show up after expansion and shifting. */
public class GeneratedOutputValidator {

  public ValidationResult validate(ResolvedSpec spec, List<Event> events, DateRange range) {
    ValidationResult result = new ValidationResult(spec.id());
    String calLoc = "calendar:" + spec.id();

    Map<LocalDate, List<Event>> byDate = new TreeMap<>();
    Map<String, Integer> countByKey = new HashMap<>();
    for (Event e : events) {
      byDate.computeIfAbsent(e.date(), d -> new ArrayList<>()).add(e);
      if (e.key() != null) {
        countByKey.merge(e.key(), 1, Integer::sum);
      }
    }

    for (var entry : byDate.entrySet()) {
      List<Event> day = entry.getValue();
      long closed = day.stream().filter(e -> e.type() == EventType.CLOSED).count();
      long early = day.stream().filter(e -> e.type() == EventType.EARLY_CLOSE).count();
      if (closed > 0 && early > 0) {
        result.error(
            "SAME_DATE_CONFLICT",
            "date:" + entry.getKey(),
            "Both CLOSED and EARLY_CLOSE on the same date: " + describe(day));
      } else if (early > 1) {
        result.error(
            "SAME_DATE_CONFLICT",
            "date:" + entry.getKey(),
            "Several EARLY_CLOSE events on the same date: " + describe(day));
      }
      if (closed > 1) {
        result.warning(
            "MULTIPLE_CLOSURES",
            "date:" + entry.getKey(),
            "More than one CLOSED event on the same date: " + describe(day));
      }
    }

    CalendarSpec.Coverage coverage = spec.coverage();
    if (coverage != null) {
      for (EventSource es : spec.eventSources()) {
        if (es.rule() == null || countByKey.getOrDefault(es.key(), 0) > 0) {
          continue;
        }
        if (isActiveSomewhere(es, range)) {
          String loc = spec.sourceOrigins().getOrDefault(es.key(), calLoc) + "/" + es.key();
          result.warning(
              "DEAD_RULE",
              loc,
              "Rule produced no events between " + range.start() + " and " + range.end());
        }
      }
      if (coverage.verifiedThrough() != null) {
        for (Event e : events) {
          if (e.status() == EventStatus.PROJECTED
              && !e.date().isAfter(coverage.verifiedThrough())) {
            result.warning(
                "PROJECTED_BEFORE_VERIFIED",
                "date:" + e.date() + "/" + e.key(),
                "PROJECTED event on or before coverage.verified_through "
                    + coverage.verifiedThrough());
          }
        }
      }
    }
    return result;
  }

  private static boolean isActiveSomewhere(EventSource es, DateRange range) {
    if (es.activeYears() == null || es.activeYears().isEmpty()) {
      return true;
    }
    for (int year = range.start().getYear(); year <= range.end().getYear(); year++) {
      if (es.isActiveOn(LocalDate.of(year, 1, 1))) {
        return true;
      }
    }
    return false;
  }

  private static String describe(List<Event> day) {
    StringBuilder sb = new StringBuilder();
    for (Event e : day) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(e.type()).append(" ").append(e.description());
      if (e.key() != null) sb.append(" (").append(e.key()).append(")");
    }
    return sb.toString();
  }
}
