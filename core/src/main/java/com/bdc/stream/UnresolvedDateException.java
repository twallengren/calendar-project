package com.bdc.stream;

import com.bdc.chronology.DateRange;
import com.bdc.trust.CompletenessScope;
import java.time.LocalDate;
import java.util.Set;

/** Thrown when a date is in range but one or more required completeness scopes are unresolved. */
public class UnresolvedDateException extends OutsideCoverageException {

  private static final long serialVersionUID = 1L;
  private final Set<CompletenessScope> incompleteScopes;

  public UnresolvedDateException(
      String calendarId, LocalDate date, DateRange range, Set<CompletenessScope> incompleteScopes) {
    super(
        calendarId,
        date,
        range,
        date + " has incomplete calendar coverage for " + calendarId + ": " + incompleteScopes);
    this.incompleteScopes = Set.copyOf(incompleteScopes);
  }

  public Set<CompletenessScope> incompleteScopes() {
    return incompleteScopes;
  }
}
