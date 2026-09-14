package com.bdc.validation;

/**
 * One validation finding.
 *
 * @param severity ERROR blocks; WARNING is advisory unless {@code --strict}
 * @param code stable machine-readable code (e.g. {@code UNKNOWN_CHRONOLOGY})
 * @param location where the issue is, e.g. {@code module:christmas/christmas} or a file path
 * @param message human-readable explanation
 */
public record ValidationIssue(Severity severity, String code, String location, String message) {

  public static ValidationIssue error(String code, String location, String message) {
    return new ValidationIssue(Severity.ERROR, code, location, message);
  }

  public static ValidationIssue warning(String code, String location, String message) {
    return new ValidationIssue(Severity.WARNING, code, location, message);
  }

  @Override
  public String toString() {
    return severity + " " + code + " [" + location + "] " + message;
  }
}
