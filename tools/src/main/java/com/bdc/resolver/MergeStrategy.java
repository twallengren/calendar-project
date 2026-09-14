package com.bdc.resolver;

import com.bdc.model.EventSource;
import com.bdc.model.EventType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merge rules used by the resolver. Event sources are keyed by {@code key}; a later declaration
 * (parent → module → local calendar) replaces an earlier one with the same key.
 */
public class MergeStrategy {

  public static List<EventSource> mergeEventSources(
      List<EventSource> base, List<EventSource> overlay) {
    Map<String, EventSource> merged = new LinkedHashMap<>();

    for (EventSource source : base) {
      merged.put(source.key(), source);
    }
    for (EventSource source : overlay) {
      merged.put(source.key(), source);
    }

    return new ArrayList<>(merged.values());
  }

  /**
   * Merges {@code overlay} into {@code target} (later wins by key) and records the declaring origin
   * of each key in {@code origins}.
   */
  public static void mergeInto(
      Map<String, EventSource> target,
      Map<String, String> origins,
      List<EventSource> overlay,
      String origin) {
    for (EventSource source : overlay) {
      target.put(source.key(), source);
      origins.put(source.key(), origin);
    }
  }

  public static Map<String, EventType> mergeClassifications(
      Map<String, EventType> base, Map<String, EventType> overlay) {
    Map<String, EventType> merged = new LinkedHashMap<>(base);
    merged.putAll(overlay);
    return merged;
  }
}
