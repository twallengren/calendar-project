package com.bdc.resolver;

import com.bdc.model.ResolvedSpec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ResolutionCache {

    private final Map<String, ResolvedSpec> cache = new ConcurrentHashMap<>();

    public boolean contains(String calendarId) {
        return cache.containsKey(calendarId);
    }

    public ResolvedSpec get(String calendarId) {
        return cache.get(calendarId);
    }

    public void put(String calendarId, ResolvedSpec spec) {
        cache.put(calendarId, spec);
    }

    public void clear() {
        cache.clear();
    }
}
