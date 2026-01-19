package com.bdc.resolver;

import com.bdc.model.EventSource;
import com.bdc.model.EventType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MergeStrategy {

    public static List<EventSource> mergeEventSources(List<EventSource> base, List<EventSource> overlay) {
        Map<String, EventSource> merged = new LinkedHashMap<>();

        for (EventSource source : base) {
            merged.put(source.key(), source);
        }
        for (EventSource source : overlay) {
            merged.put(source.key(), source);
        }

        return new ArrayList<>(merged.values());
    }

    public static Map<String, EventType> mergeClassifications(
            Map<String, EventType> base,
            Map<String, EventType> overlay) {
        Map<String, EventType> merged = new LinkedHashMap<>(base);
        merged.putAll(overlay);
        return merged;
    }
}
