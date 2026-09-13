package com.bdc.resolver;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.*;
import java.util.*;

/**
 * Flattens a calendar's {@code extends} parents and {@code uses} modules into a {@link
 * ResolvedSpec}.
 *
 * <p>Resolution order: parents (in order, recursively), then modules (depth-first, in order), then
 * the calendar's own content. Event sources are merged by key with later declarations winning;
 * weekend periods are collected in the same order and validated for cross-module conflicts.
 */
public class SpecResolver {

  private final SpecRegistry registry;
  private final ResolutionCache cache;

  public SpecResolver(SpecRegistry registry) {
    this.registry = registry;
    this.cache = new ResolutionCache();
  }

  public ResolvedSpec resolve(String calendarId) {
    if (cache.contains(calendarId)) {
      return cache.get(calendarId);
    }

    Set<String> visited = new LinkedHashSet<>();
    ResolvedSpec resolved = resolveInternal(calendarId, visited);
    cache.put(calendarId, resolved);
    return resolved;
  }

  /** Accumulator for one resolution pass. */
  private static final class Merged {
    final Map<String, EventSource> sources = new LinkedHashMap<>();
    final Map<String, String> origins = new LinkedHashMap<>();
    final Map<String, EventType> classifications = new LinkedHashMap<>();
    final List<Delta> deltas = new ArrayList<>();
    final List<WeekendPeriod> weekendPeriods = new ArrayList<>();
    final List<String> weekendOrigins = new ArrayList<>();
    final List<String> resolutionChain = new ArrayList<>();
    final Map<String, Reference> references = new LinkedHashMap<>();
    WeekendShiftPolicy shiftPolicy = WeekendShiftPolicy.NONE;

    void addWeekendPeriods(List<WeekendPeriod> periods, String origin) {
      for (WeekendPeriod period : periods) {
        int existing = weekendPeriods.indexOf(period);
        if (existing >= 0) {
          continue; // identical period already present (diamond dependency)
        }
        for (int i = 0; i < weekendPeriods.size(); i++) {
          WeekendPeriod other = weekendPeriods.get(i);
          String otherOrigin = weekendOrigins.get(i);
          if (!otherOrigin.equals(origin)
              && other.overlaps(period)
              && !other.days().equals(period.days())) {
            throw new IllegalStateException(
                "Conflicting weekend policies: "
                    + otherOrigin
                    + " declares "
                    + describe(other)
                    + " but "
                    + origin
                    + " declares "
                    + describe(period)
                    + ". Define the weekend history in a single module.");
          }
        }
        weekendPeriods.add(period);
        weekendOrigins.add(origin);
      }
    }

    private static String describe(WeekendPeriod p) {
      String range =
          (p.from() == null ? "..." : p.from().toString())
              + " to "
              + (p.to() == null ? "..." : p.to().toString());
      return p.days() + " [" + range + "]";
    }
  }

  private ResolvedSpec resolveInternal(String calendarId, Set<String> visited) {
    if (visited.contains(calendarId)) {
      throw new IllegalStateException(
          "Circular dependency detected: " + visited + " -> " + calendarId);
    }
    visited.add(calendarId);

    CalendarSpec spec =
        registry
            .getCalendar(calendarId)
            .orElseThrow(() -> new IllegalArgumentException("Calendar not found: " + calendarId));

    Merged merged = new Merged();

    // 1. Resolve extends (parents) in order
    for (String parentId : spec.extendsList()) {
      ResolvedSpec parent = resolveInternal(parentId, new LinkedHashSet<>(visited));
      for (EventSource source : parent.eventSources()) {
        merged.sources.put(source.key(), source);
        merged.origins.put(
            source.key(),
            parent.sourceOrigins().getOrDefault(source.key(), "calendar:" + parentId));
      }
      merged.classifications.putAll(parent.classifications());
      merged.deltas.addAll(parent.deltas());
      merged.addWeekendPeriods(parent.weekendPolicy().periods(), "calendar:" + parentId);
      merged.resolutionChain.addAll(parent.resolutionChain());
      for (Reference ref : parent.references()) {
        merged.references.put(ref.key(), ref);
      }
      if (parent.weekendShiftPolicy() != WeekendShiftPolicy.NONE) {
        merged.shiftPolicy = parent.weekendShiftPolicy();
      }
    }

    // 2. Resolve uses (modules) in order - recursively
    Set<String> inProgress = new LinkedHashSet<>();
    Set<String> done = new LinkedHashSet<>();
    for (String moduleId : spec.uses()) {
      resolveModuleRecursive(moduleId, inProgress, done, merged);
    }

    // 3. Merge local content
    MergeStrategy.mergeInto(
        merged.sources, merged.origins, spec.eventSources(), "calendar:" + calendarId);
    merged.classifications.putAll(spec.classifications());
    merged.deltas.addAll(spec.deltas());
    merged.resolutionChain.add("calendar:" + calendarId);

    // An explicit local policy (including NONE) overrides whatever was inherited
    if (spec.weekendShiftPolicy() != null) {
      merged.shiftPolicy = spec.weekendShiftPolicy();
    }

    // Build weekend policy
    WeekendPolicy policy =
        merged.weekendPeriods.isEmpty()
            ? WeekendPolicy.SAT_SUN
            : WeekendPolicy.ofPeriods(merged.weekendPeriods);

    return new ResolvedSpec(
        calendarId,
        spec.metadata(),
        policy,
        merged.shiftPolicy,
        List.copyOf(merged.references.values()),
        List.copyOf(merged.sources.values()),
        Map.copyOf(merged.classifications),
        List.copyOf(merged.deltas),
        List.copyOf(merged.resolutionChain),
        Map.copyOf(merged.origins));
  }

  private void resolveModuleRecursive(
      String moduleId, Set<String> inProgress, Set<String> done, Merged merged) {

    if (done.contains(moduleId)) {
      // Already processed this module (handles diamonds in dependency graph)
      return;
    }
    if (inProgress.contains(moduleId)) {
      throw new IllegalStateException(
          "Circular module dependency detected: " + inProgress + " -> " + moduleId);
    }

    ModuleSpec module =
        registry
            .getModule(moduleId)
            .orElseThrow(() -> new IllegalArgumentException("Module not found: " + moduleId));

    inProgress.add(moduleId);

    // First, recursively resolve any modules this module uses
    for (String depModuleId : module.uses()) {
      resolveModuleRecursive(depModuleId, inProgress, done, merged);
    }

    // Then add this module's own content
    String origin = "module:" + moduleId;
    if (module.policies() != null && module.policies().weekends() != null) {
      merged.addWeekendPeriods(module.policies().weekends(), origin);
    }
    MergeStrategy.mergeInto(merged.sources, merged.origins, applyModuleSource(module), origin);
    merged.resolutionChain.add(origin);
    if (module.references() != null) {
      for (Reference ref : module.references()) {
        merged.references.put(ref.key(), ref);
      }
    }

    inProgress.remove(moduleId);
    done.add(moduleId);
  }

  /**
   * Event sources of a module, with the module-level {@code source} citation applied as default.
   */
  private static List<EventSource> applyModuleSource(ModuleSpec module) {
    if (module.source().isEmpty()) {
      return module.eventSources();
    }
    List<EventSource> result = new ArrayList<>();
    for (EventSource es : module.eventSources()) {
      if (es.source().isEmpty()) {
        result.add(
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
                module.source()));
      } else {
        result.add(es);
      }
    }
    return result;
  }

  public void clearCache() {
    cache.clear();
  }
}
