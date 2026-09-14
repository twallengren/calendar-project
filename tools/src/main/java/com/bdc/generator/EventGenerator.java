package com.bdc.generator;

import com.bdc.chronology.DateRange;
import com.bdc.classifier.OccurrenceClassifier;
import com.bdc.formula.ReferenceResolver;
import com.bdc.model.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns a resolved spec into dated events.
 *
 * <p>Pipeline:
 *
 * <ol>
 *   <li>Expand every rule over a padded range (one year either side) so that shifts and offsets
 *       that cross the requested boundaries are computed correctly; filter by {@code active_years}
 *       (on the nominal, pre-shift year) and {@code only_if_weekday}.
 *   <li>Place CLOSED occurrences. Those that fall on a weekend and have a non-NONE shift policy are
 *       moved according to that policy; {@code NEXT_AVAILABLE_WEEKDAY} and {@code
 *       NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY} cascade past every other closure already placed.
 *   <li>Place other occurrences. EARLY_CLOSE is dropped on weekends and on dates that are CLOSED (a
 *       full closure takes precedence over a partial one). NOTABLE and PERIOD_MARKER are
 *       informational and always kept.
 *   <li>Apply deltas against final (observed) dates, classify, add WEEKEND rows for weekend dates
 *       without a CLOSED event, filter to the requested range, sort.
 * </ol>
 */
public class EventGenerator {

  private final RuleExpander ruleExpander;
  private final OccurrenceClassifier classifier;

  public EventGenerator() {
    this.ruleExpander = new RuleExpander();
    this.classifier = new OccurrenceClassifier();
  }

  public List<Event> generate(ResolvedSpec spec, LocalDate from, LocalDate to) {
    DateRange requested = new DateRange(from, to);
    DateRange padded = new DateRange(from.minusYears(1), to.plusYears(1));

    ReferenceResolver refResolver = new ReferenceResolver();
    refResolver.resolve(spec.references(), padded);
    ruleExpander.setReferenceResolver(refResolver);

    OccurrenceClassifier.Context ctx = classifier.context(spec);
    WeekendPolicy weekend = spec.weekendPolicy();

    // 1. Expand all rules to occurrences (nominal dates)
    List<Occurrence> occurrences = new ArrayList<>();
    Map<String, EventSource> sourcesByKey = new HashMap<>();
    for (EventSource source : spec.eventSources()) {
      sourcesByKey.put(source.key(), source);
      Rule rule = source.rule();
      if (rule == null) {
        continue;
      }
      String provenance = spec.id() + ":" + source.key();
      for (Occurrence occ : ruleExpander.expand(rule, padded, provenance)) {
        if (!source.isActiveOn(occ.date())) {
          continue;
        }
        if (source.onlyIfWeekday() != null
            && !source.onlyIfWeekday().contains(occ.date().getDayOfWeek())) {
          continue;
        }
        occurrences.add(occ);
      }
    }

    // 2. Place CLOSED occurrences, shifting weekend ones per their policy
    NavigableMap<LocalDate, List<Occurrence>> closed = new TreeMap<>();
    List<Occurrence> pendingShift = new ArrayList<>();
    List<Occurrence> others = new ArrayList<>();
    for (Occurrence occ : occurrences) {
      if (ctx.typeOf(occ) != EventType.CLOSED) {
        others.add(occ);
        continue;
      }
      WeekendShiftPolicy policy = shiftPolicyFor(occ, sourcesByKey, spec);
      if (policy != WeekendShiftPolicy.NONE && weekend.isWeekend(occ.date())) {
        pendingShift.add(occ);
      } else {
        closed.computeIfAbsent(occ.date(), d -> new ArrayList<>()).add(occ);
      }
    }
    pendingShift.sort(Comparator.comparing(Occurrence::date)); // stable: keeps declaration order
    for (Occurrence occ : pendingShift) {
      WeekendShiftPolicy policy = shiftPolicyFor(occ, sourcesByKey, spec);
      LocalDate observed = shift(occ.date(), policy, weekend, closed);
      if (observed != null) {
        closed.computeIfAbsent(observed, d -> new ArrayList<>()).add(occ.observedOn(observed));
      }
    }

    // 3. Place the rest; CLOSED takes precedence over EARLY_CLOSE on the same date
    List<Occurrence> placed = new ArrayList<>();
    closed.values().forEach(placed::addAll);
    for (Occurrence occ : others) {
      EventType type = ctx.typeOf(occ);
      if (type == EventType.EARLY_CLOSE
          && (weekend.isWeekend(occ.date()) || closed.containsKey(occ.date()))) {
        continue;
      }
      placed.add(occ);
    }

    // 4. Apply deltas against observed dates
    placed = applyDeltas(placed, spec.deltas(), requested);

    // 5. Classify
    List<Event> events = new ArrayList<>();
    Set<LocalDate> closedDates = new HashSet<>();
    for (Occurrence occ : placed) {
      if (!requested.contains(occ.date())) {
        continue;
      }
      Event event = ctx.toEvent(occ);
      events.add(event);
      if (event.type() == EventType.CLOSED) {
        closedDates.add(event.date());
      }
    }

    // 6. Weekend rows for weekend dates that are not full closures
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      if (weekend.isWeekend(date) && !closedDates.contains(date)) {
        DayOfWeek dow = date.getDayOfWeek();
        String dayName = dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        events.add(
            new Event(
                date,
                EventType.WEEKEND,
                dayName,
                "weekend_policy",
                "weekend",
                "weekend_policy",
                null,
                null,
                EventStatus.CONFIRMED));
      }
    }

    // 7. Sort deterministically
    return events.stream().sorted().collect(Collectors.toList());
  }

  private static WeekendShiftPolicy shiftPolicyFor(
      Occurrence occ, Map<String, EventSource> sourcesByKey, ResolvedSpec spec) {
    EventSource source = sourcesByKey.get(occ.key());
    if (source == null) {
      return WeekendShiftPolicy.NONE;
    }
    return source.effectiveShiftPolicy(spec.weekendShiftPolicy());
  }

  /**
   * Computes the observed date for a weekend holiday, or null when the holiday is not observed.
   *
   * @param closed closures already placed, consulted by NEXT_AVAILABLE_WEEKDAY for cascading
   */
  static LocalDate shift(
      LocalDate date,
      WeekendShiftPolicy policy,
      WeekendPolicy weekend,
      NavigableMap<LocalDate, ?> closed) {
    if (policy == WeekendShiftPolicy.NONE || !weekend.isWeekend(date)) {
      return date;
    }

    // Find the contiguous weekend block containing the date (bounded to a week)
    LocalDate first = date;
    while (weekend.isWeekend(first.minusDays(1)) && ChronoUnit.DAYS.between(first, date) < 7) {
      first = first.minusDays(1);
    }
    LocalDate last = date;
    while (weekend.isWeekend(last.plusDays(1)) && ChronoUnit.DAYS.between(date, last) < 7) {
      last = last.plusDays(1);
    }
    long size = ChronoUnit.DAYS.between(first, last) + 1;
    if (size >= 7) {
      throw new IllegalStateException(
          "Every day of the week is a weekend day around " + date + "; cannot shift");
    }

    return switch (policy) {
      case NONE -> date;
      case NEAREST_WEEKDAY -> {
        // Nearest weekday by distance; ties go forward. For a two-day weekend this is
        // first day -> previous weekday, last day -> next weekday.
        long back = ChronoUnit.DAYS.between(first, date) + 1;
        long forward = ChronoUnit.DAYS.between(date, last) + 1;
        yield back < forward ? first.minusDays(1) : last.plusDays(1);
      }
      case FORWARD_ONLY -> date.equals(last) ? last.plusDays(1) : null;
      case NEXT_AVAILABLE_WEEKDAY -> nextAvailableWeekday(last, weekend, closed);
      case NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY ->
          date.equals(last) ? nextAvailableWeekday(last, weekend, closed) : null;
    };
  }

  /** The first day after the weekend block that is neither a weekend day nor already a closure. */
  private static LocalDate nextAvailableWeekday(
      LocalDate lastWeekendDay, WeekendPolicy weekend, NavigableMap<LocalDate, ?> closed) {
    LocalDate candidate = lastWeekendDay.plusDays(1);
    int guard = 0;
    while ((weekend.isWeekend(candidate) || closed.containsKey(candidate)) && guard++ < 60) {
      candidate = candidate.plusDays(1);
    }
    return candidate;
  }

  private List<Occurrence> applyDeltas(
      List<Occurrence> occurrences, List<Delta> deltas, DateRange range) {
    if (deltas.isEmpty()) {
      return occurrences;
    }
    Map<String, Map<LocalDate, Occurrence>> byKeyAndDate = new LinkedHashMap<>();

    // Index existing occurrences (a key may legitimately have several dates, e.g. a span)
    for (Occurrence occ : occurrences) {
      byKeyAndDate.computeIfAbsent(occ.key(), k -> new LinkedHashMap<>()).put(occ.date(), occ);
    }

    // Apply deltas
    for (Delta delta : deltas) {
      switch (delta) {
        case Delta.Add add -> {
          if (range.contains(add.date())) {
            Occurrence occ = new Occurrence(add.key(), add.date(), add.name(), "delta:add");
            byKeyAndDate
                .computeIfAbsent(add.key(), k -> new LinkedHashMap<>())
                .put(add.date(), occ);
          }
        }
        case Delta.Remove remove -> {
          Map<LocalDate, Occurrence> byDate = byKeyAndDate.get(remove.key());
          if (byDate != null) {
            byDate.remove(remove.date());
          }
        }
        case Delta.Reclassify reclassify -> {
          // Reclassify is handled at classification time, not here
        }
      }
    }

    // Flatten back to list
    return byKeyAndDate.values().stream()
        .flatMap(m -> m.values().stream())
        .collect(Collectors.toList());
  }
}
