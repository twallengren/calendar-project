package com.bdc.validation;

import com.bdc.chronology.ontology.ChronologyRegistry;
import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.*;
import com.bdc.resolver.SpecResolver;
import java.time.LocalDate;
import java.util.*;

/**
 * Structural validation of a calendar and everything it composes.
 *
 * <p>Runs before generation. Walks the {@code extends}/{@code uses} graph itself so that each
 * finding can name the file and event source it belongs to, then resolves the calendar to check
 * cross-module properties (weekend conflicts, references, coverage).
 */
public class SpecValidator {

  private final SpecRegistry registry;

  public SpecValidator(SpecRegistry registry) {
    this.registry = registry;
  }

  public ValidationResult validate(String calendarId) {
    ValidationResult result = new ValidationResult(calendarId);

    Optional<CalendarSpec> maybeSpec = registry.getCalendar(calendarId);
    if (maybeSpec.isEmpty()) {
      result.error("UNKNOWN_CALENDAR", "calendar:" + calendarId, "Calendar not found");
      return result;
    }
    CalendarSpec spec = maybeSpec.get();

    // Graph walk: collect (origin -> event sources) and check references between files
    Map<String, List<EventSource>> sourcesByOrigin = new LinkedHashMap<>();
    Set<String> visitedModules = new LinkedHashSet<>();
    Map<String, Reference> declaredReferences = new LinkedHashMap<>();
    walkCalendar(
        calendarId,
        new LinkedHashSet<>(),
        visitedModules,
        sourcesByOrigin,
        declaredReferences,
        result);

    // Formulas and named references, independent of whether resolution succeeds
    for (Reference ref : declaredReferences.values()) {
      if (!Formulas.isKnown(ref.formula())) {
        result.error(
            "UNKNOWN_FORMULA",
            "reference:" + ref.key(),
            "Unknown formula '" + ref.formula() + "'; known: " + Formulas.KNOWN);
      }
    }
    for (var entry : sourcesByOrigin.entrySet()) {
      for (EventSource es : entry.getValue()) {
        if (es.rule() instanceof Rule.RelativeToReference r
            && r.usesNamedReference()
            && !declaredReferences.containsKey(r.reference())) {
          result.error(
              "UNKNOWN_REFERENCE",
              entry.getKey() + "/" + es.key(),
              "Reference '" + r.reference() + "' is not declared by any module in the chain");
        }
      }
    }

    // Duplicate keys across origins (intentional overrides are allowed, but flagged)
    Map<String, List<String>> originsByKey = new LinkedHashMap<>();
    for (var entry : sourcesByOrigin.entrySet()) {
      for (EventSource es : entry.getValue()) {
        originsByKey.computeIfAbsent(es.key(), k -> new ArrayList<>()).add(entry.getKey());
      }
    }
    for (var entry : originsByKey.entrySet()) {
      if (entry.getValue().size() > 1) {
        result.warning(
            "KEY_OVERRIDE",
            "key:" + entry.getKey(),
            "Event source declared by " + entry.getValue() + "; the last declaration wins");
      }
    }

    // Per-source structural checks
    for (var entry : sourcesByOrigin.entrySet()) {
      for (EventSource es : entry.getValue()) {
        checkEventSource(es, entry.getKey(), result);
      }
    }

    // Redundant uses: a module listed directly that is also reachable through another listed one
    checkRedundantUses(spec, result);

    // Resolution-level checks
    ResolvedSpec resolved;
    try {
      resolved = new SpecResolver(registry).resolve(calendarId);
    } catch (RuntimeException e) {
      result.error("RESOLUTION_FAILED", "calendar:" + calendarId, e.getMessage());
      return result;
    }
    checkResolved(spec, resolved, result);
    return result;
  }

  private void walkCalendar(
      String calendarId,
      Set<String> calendarStack,
      Set<String> visitedModules,
      Map<String, List<EventSource>> sourcesByOrigin,
      Map<String, Reference> declaredReferences,
      ValidationResult result) {
    if (calendarStack.contains(calendarId)) {
      result.error(
          "CIRCULAR_EXTENDS", "calendar:" + calendarId, "Circular extends: " + calendarStack);
      return;
    }
    Optional<CalendarSpec> maybe = registry.getCalendar(calendarId);
    if (maybe.isEmpty()) {
      result.error("UNKNOWN_CALENDAR", "calendar:" + calendarId, "Parent calendar not found");
      return;
    }
    CalendarSpec spec = maybe.get();
    calendarStack.add(calendarId);
    for (String parent : spec.extendsList()) {
      walkCalendar(
          parent, calendarStack, visitedModules, sourcesByOrigin, declaredReferences, result);
    }
    for (String moduleId : spec.uses()) {
      walkModule(
          moduleId,
          new LinkedHashSet<>(),
          visitedModules,
          sourcesByOrigin,
          declaredReferences,
          result);
    }
    sourcesByOrigin.put("calendar:" + calendarId, spec.eventSources());
    calendarStack.remove(calendarId);
  }

  private void walkModule(
      String moduleId,
      Set<String> moduleStack,
      Set<String> visitedModules,
      Map<String, List<EventSource>> sourcesByOrigin,
      Map<String, Reference> declaredReferences,
      ValidationResult result) {
    if (moduleStack.contains(moduleId)) {
      result.error("CIRCULAR_USES", "module:" + moduleId, "Circular uses: " + moduleStack);
      return;
    }
    if (visitedModules.contains(moduleId)) {
      return;
    }
    Optional<ModuleSpec> maybe = registry.getModule(moduleId);
    if (maybe.isEmpty()) {
      result.error("UNKNOWN_MODULE", "module:" + moduleId, "Module not found");
      return;
    }
    visitedModules.add(moduleId);
    ModuleSpec module = maybe.get();
    moduleStack.add(moduleId);
    for (String dep : module.uses()) {
      walkModule(dep, moduleStack, visitedModules, sourcesByOrigin, declaredReferences, result);
    }
    moduleStack.remove(moduleId);
    for (Reference ref : module.references()) {
      declaredReferences.put(ref.key(), ref);
    }
    List<EventSource> sources = new ArrayList<>();
    for (EventSource es : module.eventSources()) {
      // apply module-level source as default, as the resolver does
      if (es.source().isEmpty() && !module.source().isEmpty()) {
        sources.add(
            new EventSource(
                es.key(),
                es.name(),
                es.rule(),
                es.defaultClassification(),
                es.shiftable(),
                es.activeYears(),
                es.shiftPolicy(),
                es.onlyIfWeekday(),
                es.closeTime(),
                es.status(),
                module.source(),
                es.displaces()));
      } else {
        sources.add(es);
      }
    }
    sourcesByOrigin.put("module:" + moduleId, sources);
  }

  private void checkEventSource(EventSource es, String origin, ValidationResult result) {
    String loc = origin + "/" + es.key();
    if (es.key() == null || es.key().isBlank()) {
      result.error("MISSING_KEY", origin, "Event source without a key");
      return;
    }
    if (es.rule() == null) {
      result.error("MISSING_RULE", loc, "Event source has no rule");
      return;
    }
    if (es.hasIdentityMismatch()) {
      if (es.rule().key() != null && !es.rule().key().equals(es.key())) {
        result.error(
            "RULE_IDENTITY_MISMATCH",
            loc,
            "rule.key '" + es.rule().key() + "' differs from event source key '" + es.key() + "'");
      } else {
        result.warning(
            "RULE_NAME_MISMATCH",
            loc,
            "rule.name '"
                + es.rule().name()
                + "' differs from event source name '"
                + es.name()
                + "'");
      }
    }
    if (es.source().stream().noneMatch(SourceCitation::isResolvable)) {
      result.warning("MISSING_SOURCE", loc, "No source citation (id, url or file)");
    }
    if (es.defaultClassification() == EventType.EARLY_CLOSE && es.closeTime() == null) {
      result.warning("EARLY_CLOSE_WITHOUT_TIME", loc, "EARLY_CLOSE event has no close_time");
    }
    if (es.closeTime() != null && es.defaultClassification() != EventType.EARLY_CLOSE) {
      result.warning(
          "CLOSE_TIME_IGNORED", loc, "close_time is only emitted for EARLY_CLOSE events");
    }
    if (es.shiftPolicy() == WeekendShiftPolicy.DROP
        && es.defaultClassification() != EventType.EARLY_CLOSE) {
      result.error(
          "INVALID_SHIFT_POLICY",
          loc,
          "shift_policy DROP applies to EARLY_CLOSE events only; a CLOSED event on a weekend is "
              + "either shifted or observed on the weekend day (use NONE for the latter)");
    }
    if (!es.displaces().isEmpty() && es.defaultClassification() != EventType.CLOSED) {
      result.warning(
          "DISPLACES_IGNORED",
          loc,
          "displaces only affects CLOSED events; it is ignored on " + es.defaultClassification());
    }
    if (es.displaces().contains(es.key())) {
      result.error("DISPLACES_SELF", loc, "displaces lists its own key '" + es.key() + "'");
    }

    switch (es.rule()) {
      case Rule.NativeRecurring r -> {
        try {
          var provider = com.bdc.chronology.ChronologyProviders.get(r.chronology());
          r.monthCodes().forEach(provider::validateMonthCode);
        } catch (IllegalArgumentException e) {
          result.error("INVALID_RULE", loc, e.getMessage());
        }
      }
      case Rule.NativeExplicitDates r -> {
        for (var date : r.dates()) {
          try {
            com.bdc.chronology.ChronologyProviders.get(date.chronologyId()).toIso(date);
          } catch (IllegalArgumentException e) {
            result.error("INVALID_RULE", loc, e.getMessage());
          }
        }
      }
      case Rule.FixedMonthDay r -> {
        checkChronology(r.chronology(), loc, result);
        if (r.month() < 1 || r.month() > 12) {
          result.error("INVALID_RULE", loc, "month out of range: " + r.month());
        }
        if (r.day() < 1 || r.day() > 31) {
          result.error("INVALID_RULE", loc, "day out of range: " + r.day());
        }
        if (r.hasEndDate()) {
          if (r.endMonth() < 1 || r.endMonth() > 12 || r.endDay() < 1 || r.endDay() > 31) {
            result.error("INVALID_RULE", loc, "end_month/end_day out of range");
          }
        }
      }
      case Rule.NthWeekdayOfMonth r -> {
        if (r.month() < 1 || r.month() > 12) {
          result.error("INVALID_RULE", loc, "month out of range: " + r.month());
        }
        if (r.nth() == 0 || r.nth() < -1 || r.nth() > 5) {
          result.error("INVALID_RULE", loc, "nth must be 1..5 or -1 (last), got: " + r.nth());
        }
        if (r.weekday() == null) {
          result.error("INVALID_RULE", loc, "weekday is required");
        }
      }
      case Rule.ExplicitDates r -> {
        if (r.dates().isEmpty()) {
          result.error("INVALID_RULE", loc, "explicit_dates has no dates");
        }
        Set<LocalDate> seen = new HashSet<>();
        LocalDate previous = null;
        boolean unsorted = false;
        for (Rule.AnnotatedDate ad : r.dates()) {
          if (!seen.add(ad.date())) {
            result.error("INVALID_RULE", loc, "duplicate explicit date " + ad.date());
          }
          if (previous != null && ad.date().isBefore(previous)) {
            unsorted = true;
          }
          previous = ad.date();
        }
        if (unsorted) {
          result.warning("UNSORTED_DATES", loc, "explicit_dates are not in ascending order");
        }
      }
      case Rule.RelativeToReference r -> {
        if (!r.usesNamedReference() && !r.usesFixedReference()) {
          result.error(
              "INVALID_RULE", loc, "relative_to_reference needs reference or reference_month/day");
        }
        if (r.offsetDays() == null && !r.usesWeekdayOffset()) {
          result.error(
              "INVALID_RULE", loc, "relative_to_reference needs offset_days or offset_weekday");
        }
        if (r.usesWeekdayOffset() && r.offsetWeekday().nth() < 1) {
          result.error("INVALID_RULE", loc, "offset_weekday.nth must be >= 1");
        }
        if (r.usesFixedReference()
            && (r.referenceMonth() < 1
                || r.referenceMonth() > 12
                || r.referenceDay() < 1
                || r.referenceDay() > 31)) {
          result.error("INVALID_RULE", loc, "reference_month/reference_day out of range");
        }
      }
    }

    if (es.activeYears() != null) {
      for (EventSource.YearRange yr : es.activeYears()) {
        if (yr.start() != null && yr.end() != null && yr.start() > yr.end()) {
          result.error("INVALID_ACTIVE_YEARS", loc, "active_years range start after end: " + yr);
        }
      }
    }
  }

  private static final java.util.regex.Pattern MIC_PATTERN =
      java.util.regex.Pattern.compile("^[A-Z0-9]{4}$");

  /**
   * Checks {@code metadata.mic}/{@code metadata.aliases}: a market-kind calendar without a mic is a
   * warning, a mic that doesn't look like an ISO 10383 code is an error, and a mic or alias reused
   * by another calendar (case-insensitively) is an error naming the other calendar.
   */
  private void checkMicAndAliases(CalendarSpec spec, ValidationResult result) {
    String calLoc = "calendar:" + spec.id();
    CalendarSpec.Metadata metadata = spec.metadata();
    String mic = metadata != null ? metadata.mic() : null;
    List<String> aliases = metadata != null ? metadata.aliases() : List.of();
    String kind =
        metadata != null && metadata.kind() != null
            ? metadata.kind()
            : CalendarSpec.Metadata.KIND_MARKET;

    if (mic == null && CalendarSpec.Metadata.KIND_MARKET.equals(kind)) {
      result.warning(
          "MISSING_MIC",
          calLoc,
          "market calendar has no metadata.mic (ISO 10383 Market Identifier Code)");
    }
    if (mic != null && !MIC_PATTERN.matcher(mic).matches()) {
      result.error("INVALID_MIC", calLoc, "metadata.mic '" + mic + "' must match ^[A-Z0-9]{4}$");
    }
    if (mic != null && CalendarSpec.Metadata.KIND_PAYMENT.equals(kind)) {
      result.error(
          "PAYMENT_MIC", calLoc, "payment calendars use system identifiers, not a market MIC");
    }

    Set<String> ownTokensSeen = new HashSet<>();
    if (mic != null) {
      ownTokensSeen.add(mic.toUpperCase(Locale.ROOT));
    }
    for (String alias : aliases) {
      if (alias == null || alias.isBlank()) {
        result.error("INVALID_ALIAS", calLoc, "metadata.aliases contains a blank entry");
        continue;
      }
      if (!ownTokensSeen.add(alias.toUpperCase(Locale.ROOT))) {
        result.error(
            "DUPLICATE_ALIAS", calLoc, "metadata.aliases contains '" + alias + "' more than once");
      }
    }

    for (var entry : registry.getAllCalendars().entrySet()) {
      String otherId = entry.getKey();
      if (otherId.equals(spec.id())) {
        continue;
      }
      CalendarSpec.Metadata otherMetadata = entry.getValue().metadata();
      if (otherMetadata == null) {
        continue;
      }
      List<String> otherTokens = new ArrayList<>();
      if (otherMetadata.mic() != null) {
        otherTokens.add(otherMetadata.mic());
      }
      otherTokens.addAll(otherMetadata.aliases());
      for (String otherToken : otherTokens) {
        if (otherToken == null) {
          continue;
        }
        if (mic != null && otherToken.equalsIgnoreCase(mic)) {
          result.error(
              "DUPLICATE_MIC",
              calLoc,
              "metadata.mic '" + mic + "' is also used by calendar '" + otherId + "'");
        }
        for (String alias : aliases) {
          if (otherToken.equalsIgnoreCase(alias)) {
            result.error(
                "DUPLICATE_ALIAS",
                calLoc,
                "metadata.aliases entry '"
                    + alias
                    + "' is also used by calendar '"
                    + otherId
                    + "'");
          }
        }
      }
    }
  }

  private void checkChronology(String chronology, String loc, ValidationResult result) {
    if (chronology != null && !ChronologyRegistry.getInstance().hasChronology(chronology)) {
      result.error("UNKNOWN_CHRONOLOGY", loc, "Unknown chronology: " + chronology);
    }
  }

  private void checkRedundantUses(CalendarSpec spec, ValidationResult result) {
    Map<String, Set<String>> reachable = new HashMap<>();
    for (String moduleId : spec.uses()) {
      reachable.put(moduleId, transitiveUses(moduleId, new HashSet<>()));
    }
    for (String moduleId : spec.uses()) {
      for (String other : spec.uses()) {
        if (!other.equals(moduleId) && reachable.get(other).contains(moduleId)) {
          result.warning(
              "REDUNDANT_USES",
              "calendar:" + spec.id(),
              "Module '" + moduleId + "' is already included through '" + other + "'");
          break;
        }
      }
    }
  }

  /**
   * The first cycle in the {@code displaces} graph reachable from {@code start} that comes back to
   * {@code start}, as the path that closes it, or null when there is none.
   */
  private static List<String> findDisplacementCycle(String start, Map<String, List<String>> graph) {
    Deque<String> path = new ArrayDeque<>();
    if (walkDisplacement(start, start, graph, new HashSet<>(), path)) {
      List<String> cycle = new ArrayList<>(path);
      cycle.add(start);
      return cycle;
    }
    return null;
  }

  private static boolean walkDisplacement(
      String current,
      String start,
      Map<String, List<String>> graph,
      Set<String> visited,
      Deque<String> path) {
    if (!visited.add(current)) {
      return false;
    }
    path.addLast(current);
    for (String next : graph.getOrDefault(current, List.of())) {
      if (next.equals(start)) {
        return true;
      }
      if (walkDisplacement(next, start, graph, visited, path)) {
        return true;
      }
    }
    path.removeLast();
    return false;
  }

  private Set<String> transitiveUses(String moduleId, Set<String> seen) {
    Optional<ModuleSpec> maybe = registry.getModule(moduleId);
    if (maybe.isEmpty()) {
      return seen;
    }
    for (String dep : maybe.get().uses()) {
      if (seen.add(dep)) {
        transitiveUses(dep, seen);
      }
    }
    return seen;
  }

  private void checkResolved(CalendarSpec spec, ResolvedSpec resolved, ValidationResult result) {
    String calLoc = "calendar:" + spec.id();

    if (spec.metadata() != null) {
      checkChronology(spec.metadata().chronology(), calLoc, result);
    }
    checkMicAndAliases(spec, result);

    Set<String> sourceKeys = new HashSet<>();
    boolean anyCloseTime = false;
    for (EventSource es : resolved.eventSources()) {
      sourceKeys.add(es.key());
      String loc = resolved.sourceOrigins().getOrDefault(es.key(), calLoc) + "/" + es.key();
      if (es.closeTime() != null) {
        anyCloseTime = true;
      }
      if (Boolean.TRUE.equals(es.shiftable())
          && es.shiftPolicy() == null
          && es.defaultClassification() == EventType.CLOSED
          && resolved.weekendShiftPolicy() == WeekendShiftPolicy.NONE) {
        result.warning(
            "SHIFTABLE_UNDER_NONE",
            loc,
            "Shiftable holiday in a calendar whose effective weekend_shift_policy is NONE; "
                + "weekend occurrences will be emitted as CLOSED on the weekend day");
      }
    }

    // displaces: every named key must exist, and the priority graph must be acyclic
    Map<String, List<String>> displacementGraph = new LinkedHashMap<>();
    for (EventSource es : resolved.eventSources()) {
      if (es.displaces().isEmpty()) {
        continue;
      }
      String loc = resolved.sourceOrigins().getOrDefault(es.key(), calLoc) + "/" + es.key();
      for (String target : es.displaces()) {
        if (!sourceKeys.contains(target)) {
          result.error(
              "UNKNOWN_DISPLACES",
              loc,
              "displaces key '" + target + "' matches no event source in this calendar");
        }
      }
      displacementGraph.put(es.key(), es.displaces());
    }
    for (String start : displacementGraph.keySet()) {
      List<String> cycle = findDisplacementCycle(start, displacementGraph);
      if (cycle != null) {
        String loc = resolved.sourceOrigins().getOrDefault(start, calLoc) + "/" + start;
        result.warning(
            "DISPLACES_CYCLE",
            loc,
            "displaces forms a cycle: "
                + String.join(" -> ", cycle)
                + "; placement stops displacing after a bounded number of steps, so the resulting "
                + "observed dates depend on declaration order");
      }
    }

    // Classifications and deltas that match nothing
    for (String key : spec.classifications().keySet()) {
      if (!sourceKeys.contains(key)) {
        result.warning(
            "DEAD_CLASSIFICATION", calLoc, "classifications." + key + " matches no event source");
      }
    }
    for (Delta delta : spec.deltas()) {
      String key =
          switch (delta) {
            case Delta.Add a -> null;
            case Delta.Remove r -> r.key();
            case Delta.Reclassify r -> r.key();
          };
      if (key != null && !sourceKeys.contains(key)) {
        result.warning("DEAD_DELTA", calLoc, "delta on key '" + key + "' matches no event source");
      }
      if (delta instanceof Delta.Add a && a.classification() == null) {
        result.warning(
            "DELTA_ADD_UNCLASSIFIED", calLoc, "delta add " + a.key() + " has no classification");
      }
    }

    // Weekend policy sanity
    if (resolved.weekendPolicy().weekendDays().isEmpty()) {
      result.warning("NO_WEEKEND", calLoc, "Resolved weekend policy has no weekend days");
    }

    // Timezone / coverage
    if (anyCloseTime && resolved.timezone() == null) {
      result.warning(
          "MISSING_TIMEZONE", calLoc, "close_time is used but metadata.timezone is not set");
    }
    CalendarSpec.Coverage coverage = resolved.coverage();
    if (coverage == null) {
      result.warning("MISSING_COVERAGE", calLoc, "metadata.coverage is not declared");
    } else {
      for (EventSource es : resolved.eventSources()) {
        if (es.activeYears() == null || es.activeYears().isEmpty()) {
          continue;
        }
        boolean anyInside = false;
        for (EventSource.YearRange yr : es.activeYears()) {
          int start = yr.start() != null ? yr.start() : Integer.MIN_VALUE;
          int end = yr.end() != null ? yr.end() : Integer.MAX_VALUE;
          int covStart = coverage.from() != null ? coverage.from().getYear() : Integer.MIN_VALUE;
          int covEnd = coverage.to() != null ? coverage.to().getYear() : Integer.MAX_VALUE;
          if (start <= covEnd && end >= covStart) {
            anyInside = true;
          }
        }
        if (!anyInside) {
          String loc = resolved.sourceOrigins().getOrDefault(es.key(), calLoc) + "/" + es.key();
          result.warning(
              "ACTIVE_YEARS_OUTSIDE_COVERAGE", loc, "active_years lie entirely outside coverage");
        }
      }
      // Lookup-table chronologies must cover the coverage range
      for (EventSource es : resolved.eventSources()) {
        if (es.rule() instanceof Rule.FixedMonthDay r
            && ChronologyRegistry.getInstance().hasChronology(r.chronology())) {
          ChronologyAlgorithm alg = ChronologyRegistry.getInstance().getAlgorithm(r.chronology());
          alg.supportedYearRange()
              .ifPresent(
                  supported -> {
                    LocalDate tableEnd =
                        ChronologyRegistry.getInstance()
                            .toIsoDate(
                                supported[1],
                                12,
                                alg.getDaysInMonth(supported[1], 12),
                                r.chronology());
                    LocalDate tableStart =
                        ChronologyRegistry.getInstance()
                            .toIsoDate(supported[0], 1, 1, r.chronology());
                    LocalDate needFrom = coverage.from();
                    LocalDate needTo = coverage.to();
                    if (es.activeYears() != null && !es.activeYears().isEmpty()) {
                      Integer minYear = null;
                      for (EventSource.YearRange yr : es.activeYears()) {
                        if (yr.start() == null) {
                          minYear = null;
                          break;
                        }
                        minYear = minYear == null ? yr.start() : Math.min(minYear, yr.start());
                      }
                      if (minYear != null && needFrom != null && minYear > needFrom.getYear()) {
                        needFrom = LocalDate.of(minYear, 1, 1);
                      }
                    }
                    String loc =
                        resolved.sourceOrigins().getOrDefault(es.key(), calLoc) + "/" + es.key();
                    if ((needTo != null && needTo.isAfter(tableEnd))
                        || (needFrom != null && needFrom.isBefore(tableStart))) {
                      result.error(
                          "CHRONOLOGY_RANGE",
                          loc,
                          "Chronology "
                              + r.chronology()
                              + " table covers "
                              + tableStart
                              + " to "
                              + tableEnd
                              + " but the calendar needs "
                              + needFrom
                              + " to "
                              + needTo);
                    }
                  });
        }
      }
    }
  }
}
