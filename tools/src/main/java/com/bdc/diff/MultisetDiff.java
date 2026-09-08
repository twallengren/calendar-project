package com.bdc.diff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact matching with multiplicity. Callers sort the remaining occurrences for presentation. */
public record MultisetDiff<T>(List<T> additions, List<T> removals) {
  public MultisetDiff {
    additions = List.copyOf(additions);
    removals = List.copyOf(removals);
  }

  public static <T> MultisetDiff<T> compare(List<T> before, List<T> after) {
    Map<T, Integer> remaining = new HashMap<>();
    for (T value : before) remaining.merge(value, 1, Integer::sum);
    List<T> additions = new ArrayList<>();
    for (T value : after) {
      int count = remaining.getOrDefault(value, 0);
      if (count == 0) additions.add(value);
      else if (count == 1) remaining.remove(value);
      else remaining.put(value, count - 1);
    }
    List<T> removals = new ArrayList<>();
    remaining.forEach(
        (value, count) -> {
          for (int i = 0; i < count; i++) removals.add(value);
        });
    return new MultisetDiff<>(additions, removals);
  }
}
