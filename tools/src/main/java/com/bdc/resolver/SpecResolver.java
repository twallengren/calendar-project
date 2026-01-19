package com.bdc.resolver;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.*;

import java.time.DayOfWeek;
import java.util.*;

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

    private ResolvedSpec resolveInternal(String calendarId, Set<String> visited) {
        if (visited.contains(calendarId)) {
            throw new IllegalStateException("Circular dependency detected: " + visited + " -> " + calendarId);
        }
        visited.add(calendarId);

        CalendarSpec spec = registry.getCalendar(calendarId)
            .orElseThrow(() -> new IllegalArgumentException("Calendar not found: " + calendarId));

        List<EventSource> mergedSources = new ArrayList<>();
        Map<String, EventType> mergedClassifications = new HashMap<>();
        List<Delta> mergedDeltas = new ArrayList<>();
        Set<DayOfWeek> weekendDays = new LinkedHashSet<>();
        List<String> resolutionChain = new ArrayList<>();

        // 1. Resolve extends (parents) in order
        for (String parentId : spec.extendsList()) {
            ResolvedSpec parent = resolveInternal(parentId, new LinkedHashSet<>(visited));
            mergedSources.addAll(parent.eventSources());
            mergedClassifications.putAll(parent.classifications());
            mergedDeltas.addAll(parent.deltas());
            weekendDays.addAll(parent.weekendPolicy().weekendDays());
            resolutionChain.addAll(parent.resolutionChain());
        }

        // 2. Resolve uses (modules) in order
        for (String moduleId : spec.uses()) {
            ModuleSpec module = registry.getModule(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Module not found: " + moduleId));

            if (module.policies() != null && module.policies().weekends() != null) {
                weekendDays.addAll(module.policies().weekends());
            }
            mergedSources.addAll(module.eventSources());
            resolutionChain.add("module:" + moduleId);
        }

        // 3. Merge local content
        mergedSources.addAll(spec.eventSources());
        mergedClassifications.putAll(spec.classifications());
        mergedDeltas.addAll(spec.deltas());
        resolutionChain.add("calendar:" + calendarId);

        // Build weekend policy
        WeekendPolicy policy = weekendDays.isEmpty()
            ? WeekendPolicy.SAT_SUN
            : new WeekendPolicy(weekendDays);

        return new ResolvedSpec(
            calendarId,
            spec.metadata(),
            policy,
            List.copyOf(mergedSources),
            Map.copyOf(mergedClassifications),
            List.copyOf(mergedDeltas),
            List.copyOf(resolutionChain)
        );
    }

    public void clearCache() {
        cache.clear();
    }
}
