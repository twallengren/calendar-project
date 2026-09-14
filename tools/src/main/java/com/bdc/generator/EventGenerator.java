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
 *       NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY} cascade past every other closure already placed. A
 *       source that lists {@code displaces} keys treats a slot held only by those keys as
 *       available: it takes the slot and the displaced occurrences re-cascade forward.
 *   <li>Place other occurrences. An EARLY_CLOSE whose date is a weekend day or already CLOSED (a
 *       full closure takes precedence over a partial one) follows its own shift policy: {@code
 *       DROP} (the default) discards it, {@code PREVIOUS_AVAILABLE_BUSINESS_DAY} moves it back to
 *       the nearest earlier session. NOTABLE and PERIOD_MARKER are informational and always kept.
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
      Set<String> displaces = displacesFor(occ, sourcesByKey);
      LocalDate observed = shift(occ.date(), policy, weekend, closed, displaces);
      if (observed != null) {
        place(occ, observed, displaces, weekend, closed, sourcesByKey, 0);
      }
    }

    // 3. Place the rest; CLOSED takes precedence over EARLY_CLOSE on the same date
    List<Occurrence> placed = new ArrayList<>();
    closed.values().forEach(placed::addAll);
    for (Occurrence occ : others) {
      EventType type = ctx.typeOf(occ);
      if (type == EventType.EARLY_CLOSE
          && (weekend.isWeekend(occ.date()) || closed.containsKey(occ.date()))) {
        LocalDate moved =
            shiftEarlyClose(occ.date(), shiftPolicyFor(occ, sourcesByKey, spec), weekend, closed);
        if (moved == null) {
          continue;
        }
        placed.add(occ.observedOn(moved));
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

  private static Set<String> displacesFor(Occurrence occ, Map<String, EventSource> sourcesByKey) {
    EventSource source = sourcesByKey.get(occ.key());
    if (source == null || source.displaces().isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(source.displaces());
  }

  /** Guard against a {@code displaces} cycle turning placement into an infinite chain. */
  private static final int MAX_DISPLACEMENT_DEPTH = 16;

  /**
   * Puts a shifted closure on its observed date, evicting any lower-priority closures it displaces.
   *
   * <p>A displaced occurrence re-cascades to the first date strictly after the slot it lost that is
   * neither a weekend day nor already a closure - {@code NEXT_AVAILABLE_WEEKDAY}'s cascade applied
   * from the taken slot, whatever the displaced event's own policy. Displacement only ever pushes
   * an event later, so {@code NEAREST_WEEKDAY}'s backward branch and the "not observed at all"
   * branches of {@code FORWARD_ONLY} / {@code NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY} never apply.
   * The re-cascade honours the displaced event's own {@code displaces} list, so priorities chain;
   * {@link #MAX_DISPLACEMENT_DEPTH} stops a cycle (which {@code validate} warns about).
   */
  private static void place(
      Occurrence occ,
      LocalDate observed,
      Set<String> displaces,
      WeekendPolicy weekend,
      NavigableMap<LocalDate, List<Occurrence>> closed,
      Map<String, EventSource> sourcesByKey,
      int depth) {
    List<Occurrence> evicted = null;
    if (isDisplaceable(closed.get(observed), displaces) && depth < MAX_DISPLACEMENT_DEPTH) {
      evicted = closed.remove(observed);
    }
    closed.computeIfAbsent(observed, d -> new ArrayList<>()).add(occ.observedOn(observed));
    if (evicted == null) {
      return;
    }
    for (Occurrence displaced : evicted) {
      Set<String> ownDisplaces = displacesFor(displaced, sourcesByKey);
      LocalDate next = nextAvailableWeekday(observed, weekend, closed, ownDisplaces);
      if (next != null) {
        place(displaced, next, ownDisplaces, weekend, closed, sourcesByKey, depth + 1);
      }
    }
  }

  /** True when a slot is occupied and every occupant is one this event is allowed to displace. */
  private static boolean isDisplaceable(List<Occurrence> held, Set<String> displaces) {
    return held != null
        && !held.isEmpty()
        && !displaces.isEmpty()
        && held.stream().allMatch(o -> displaces.contains(o.key()));
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
      NavigableMap<LocalDate, List<Occurrence>> closed) {
    return shift(date, policy, weekend, closed, Set.of());
  }

  /**
   * Computes the observed date for a weekend holiday, or null when the holiday is not observed.
   *
   * @param closed closures already placed, consulted by NEXT_AVAILABLE_WEEKDAY for cascading
   * @param displaces keys whose slots count as available to this event (see {@code displaces})
   */
  static LocalDate shift(
      LocalDate date,
      WeekendShiftPolicy policy,
      WeekendPolicy weekend,
      NavigableMap<LocalDate, List<Occurrence>> closed,
      Set<String> displaces) {
    if (!weekend.isWeekend(date) || policy == WeekendShiftPolicy.NONE) {
      return date;
    }
    if (policy == WeekendShiftPolicy.DROP) {
      return null;
    }
    if (policy == WeekendShiftPolicy.PREVIOUS_AVAILABLE_BUSINESS_DAY) {
      return previousAvailableBusinessDay(date, weekend, closed);
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
      case NONE, DROP, PREVIOUS_AVAILABLE_BUSINESS_DAY -> date; // handled above
      case NEAREST_WEEKDAY -> {
        // Nearest weekday by distance; ties go forward. For a two-day weekend this is
        // first day -> previous weekday, last day -> next weekday.
        long back = ChronoUnit.DAYS.between(first, date) + 1;
        long forward = ChronoUnit.DAYS.between(date, last) + 1;
        yield back < forward ? first.minusDays(1) : last.plusDays(1);
      }
      case FORWARD_ONLY -> date.equals(last) ? last.plusDays(1) : null;
      case NEXT_AVAILABLE_WEEKDAY -> nextAvailableWeekday(last, weekend, closed, displaces);
      case NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY ->
          date.equals(last) ? nextAvailableWeekday(last, weekend, closed, displaces) : null;
    };
  }

  /**
   * The observed date for an EARLY_CLOSE whose nominal date is not a session, or null when it is
   * not observed. Only {@code PREVIOUS_AVAILABLE_BUSINESS_DAY} moves a half day; every other policy
   * - including the {@code DROP} default - discards it.
   */
  private static LocalDate shiftEarlyClose(
      LocalDate date,
      WeekendShiftPolicy policy,
      WeekendPolicy weekend,
      NavigableMap<LocalDate, List<Occurrence>> closed) {
    if (policy != WeekendShiftPolicy.PREVIOUS_AVAILABLE_BUSINESS_DAY) {
      return null;
    }
    return previousAvailableBusinessDay(date, weekend, closed);
  }

  /**
   * The nearest date strictly before {@code date} that is neither a weekend day nor already a
   * closure, searching at most seven days back; null when there is none.
   */
  private static LocalDate previousAvailableBusinessDay(
      LocalDate date, WeekendPolicy weekend, NavigableMap<LocalDate, List<Occurrence>> closed) {
    for (int back = 1; back <= 7; back++) {
      LocalDate candidate = date.minusDays(back);
      if (!weekend.isWeekend(candidate) && !closed.containsKey(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * The first day after the weekend block that is neither a weekend day nor a closure this event is
   * not allowed to displace.
   */
  private static LocalDate nextAvailableWeekday(
      LocalDate lastWeekendDay,
      WeekendPolicy weekend,
      NavigableMap<LocalDate, List<Occurrence>> closed,
      Set<String> displaces) {
    LocalDate candidate = lastWeekendDay.plusDays(1);
    int guard = 0;
    while (guard++ < 60 && !isAvailable(candidate, weekend, closed, displaces)) {
      candidate = candidate.plusDays(1);
    }
    return candidate;
  }

  private static boolean isAvailable(
      LocalDate candidate,
      WeekendPolicy weekend,
      NavigableMap<LocalDate, List<Occurrence>> closed,
      Set<String> displaces) {
    if (weekend.isWeekend(candidate)) {
      return false;
    }
    return !closed.containsKey(candidate) || isDisplaceable(closed.get(candidate), displaces);
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
