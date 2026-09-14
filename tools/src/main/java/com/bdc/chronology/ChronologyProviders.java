package com.bdc.chronology;

import com.bdc.chronology.ontology.ChronologyRegistry;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic compiler providers. New profiles never silently replace numeric algorithms. */
public final class ChronologyProviders {
  private static final ChronologyProvider HEBREW = new HebrewChronologyProvider();

  private ChronologyProviders() {}

  public static ChronologyProvider get(String id) {
    String normalized = id.toUpperCase(Locale.ROOT);
    if (normalized.equals("HEBREW")) return HEBREW;
    return new NumericChronologyProvider(ChronologyRegistry.getInstance().getAlgorithm(normalized));
  }

  public static Set<String> available() {
    Set<String> ids = new TreeSet<>(ChronologyRegistry.getInstance().getRegisteredChronologies());
    ids.add("HEBREW");
    return java.util.Collections.unmodifiableSet(ids);
  }
}
