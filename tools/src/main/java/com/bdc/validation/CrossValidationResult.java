package com.bdc.validation;

import com.bdc.model.EventType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * The outcome of comparing one calendar's generated events against one third-party reference CSV
 * for the overlapping, allowlist-adjusted date range.
 *
 * @param calendarId the calendar compared
 * @param source the reference file name without extension (e.g. {@code quantlib-nyse})
 * @param libraryVersion the library/version string from the reference file's {@code #
 *     generated-by:} header, or null if the file has none
 * @param from start of the compared range (inclusive)
 * @param to end of the compared range (inclusive)
 * @param comparedTypes the event types honoured on both sides
 * @param matchedCount rows present on both sides
 * @param allowlistedCount rows that differ but are covered by a (non-stale) allowlist entry
 * @param unexplainedRows differences with no matching allowlist entry, human-readable
 * @param staleAllowlistRows allowlist entries that no longer correspond to a real difference
 */
public record CrossValidationResult(
    String calendarId,
    String source,
    String libraryVersion,
    LocalDate from,
    LocalDate to,
    Set<EventType> comparedTypes,
    int matchedCount,
    int allowlistedCount,
    List<String> unexplainedRows,
    List<String> staleAllowlistRows) {

  public CrossValidationResult {
    comparedTypes = comparedTypes == null ? Set.of() : Set.copyOf(comparedTypes);
    unexplainedRows = unexplainedRows == null ? List.of() : List.copyOf(unexplainedRows);
    staleAllowlistRows = staleAllowlistRows == null ? List.of() : List.copyOf(staleAllowlistRows);
  }

  /** Total problems: real differences without an allowlist entry, plus stale entries. */
  public int unexplainedCount() {
    return unexplainedRows.size() + staleAllowlistRows.size();
  }

  /** True when there is nothing left unexplained (no discrepancies, no stale allowlist rows). */
  public boolean isClean() {
    return unexplainedCount() == 0;
  }

  /** {@code "ok"} or {@code "discrepancies"}, for reporting. */
  public String status() {
    return isClean() ? "ok" : "discrepancies";
  }
}
