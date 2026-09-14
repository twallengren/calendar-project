package com.bdc.chronology;

/** Conversion is outside the provider's declared support, not an absent recurring date. */
public final class UnsupportedChronologyRangeException extends IllegalArgumentException {
  public UnsupportedChronologyRangeException(String message) {
    super(message);
  }
}
