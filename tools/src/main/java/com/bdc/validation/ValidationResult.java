package com.bdc.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Accumulates validation issues for one calendar. */
public class ValidationResult {

  private final String calendarId;
  private final List<ValidationIssue> issues = new ArrayList<>();

  public ValidationResult(String calendarId) {
    this.calendarId = calendarId;
  }

  public String calendarId() {
    return calendarId;
  }

  public void add(ValidationIssue issue) {
    issues.add(issue);
  }

  public void addAll(ValidationResult other) {
    issues.addAll(other.issues);
  }

  public void error(String code, String location, String message) {
    issues.add(ValidationIssue.error(code, location, message));
  }

  public void warning(String code, String location, String message) {
    issues.add(ValidationIssue.warning(code, location, message));
  }

  public List<ValidationIssue> issues() {
    return Collections.unmodifiableList(issues);
  }

  public List<ValidationIssue> errors() {
    return issues.stream().filter(i -> i.severity() == Severity.ERROR).toList();
  }

  public List<ValidationIssue> warnings() {
    return issues.stream().filter(i -> i.severity() == Severity.WARNING).toList();
  }

  public boolean hasErrors() {
    return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
  }

  public boolean hasWarnings() {
    return issues.stream().anyMatch(i -> i.severity() == Severity.WARNING);
  }

  public boolean isClean() {
    return issues.isEmpty();
  }
}
