package com.bdc.validation;

import java.util.Set;

/** The reference formulas the generator knows how to compute. */
public final class Formulas {

  public static final Set<String> KNOWN = Set.of("EASTER_WESTERN", "THANKSGIVING_US");

  private Formulas() {}

  public static boolean isKnown(String formula) {
    return formula != null && KNOWN.contains(formula);
  }
}
