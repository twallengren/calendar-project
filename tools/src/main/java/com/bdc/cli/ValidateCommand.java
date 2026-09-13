package com.bdc.cli;

import com.bdc.chronology.DateRange;
import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.CalendarSpec;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import com.bdc.validation.GeneratedOutputValidator;
import com.bdc.validation.Severity;
import com.bdc.validation.SpecValidator;
import com.bdc.validation.ValidationIssue;
import com.bdc.validation.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "validate",
    description =
        "Validate calendar specifications: structure, references, chronologies, weekend policies,"
            + " sources, and generated-output consistency")
public class ValidateCommand implements Callable<Integer> {

  @Parameters(index = "0", arity = "0..1", description = "The calendar ID to validate")
  private String calendarId;

  @Option(
      names = {"--all"},
      description = "Validate every calendar in the calendars directory")
  private boolean all;

  @Option(
      names = {"--strict"},
      description = "Treat warnings as errors (exit code 2 when only warnings are present)")
  private boolean strict;

  @Option(
      names = {"--format"},
      description = "Output format: text or json",
      defaultValue = "text")
  private String format;

  @Option(
      names = {"--skip-generation"},
      description = "Skip the generation-time checks (same-date conflicts, dead rules)")
  private boolean skipGeneration;

  @Option(
      names = {"--calendars-dir"},
      description = "Calendars directory",
      defaultValue = "calendars")
  private Path calendarsDir;

  @Option(
      names = {"--modules-dir"},
      description = "Modules directory",
      defaultValue = "modules")
  private Path modulesDir;

  @Override
  public Integer call() {
    try {
      if (calendarId == null && !all) {
        System.err.println("Validation failed: specify a calendar ID or --all");
        return 1;
      }
      SpecRegistry registry = new SpecRegistry();
      registry.loadCalendarsFromDirectory(calendarsDir);
      registry.loadModulesFromDirectory(modulesDir);

      List<String> ids;
      if (all) {
        ids = new ArrayList<>(registry.getAllCalendars().keySet());
        Collections.sort(ids);
      } else {
        if (registry.getCalendar(calendarId).isEmpty()) {
          System.err.println("Validation failed: Calendar not found: " + calendarId);
          return 1;
        }
        ids = List.of(calendarId);
      }

      List<ValidationIssue> loadIssues = new ArrayList<>();
      for (SpecRegistry.LoadError error : registry.loadErrors()) {
        loadIssues.add(
            ValidationIssue.error("LOAD_ERROR", error.path().toString(), error.message()));
      }

      SpecValidator specValidator = new SpecValidator(registry);
      GeneratedOutputValidator outputValidator = new GeneratedOutputValidator();
      Map<String, ValidationResult> results = new LinkedHashMap<>();
      for (String id : ids) {
        ValidationResult result = specValidator.validate(id);
        if (!result.hasErrors() && !skipGeneration) {
          try {
            SpecResolver resolver = new SpecResolver(registry);
            ResolvedSpec resolved = resolver.resolve(id);
            DateRange range = generationRange(resolved);
            List<Event> events =
                new EventGenerator().generate(resolved, range.start(), range.end());
            result.addAll(outputValidator.validate(resolved, events, range));
          } catch (RuntimeException e) {
            result.error("GENERATION_FAILED", "calendar:" + id, e.getMessage());
          }
        }
        results.put(id, result);
      }

      boolean anyErrors =
          !loadIssues.isEmpty() || results.values().stream().anyMatch(ValidationResult::hasErrors);
      boolean anyWarnings = results.values().stream().anyMatch(ValidationResult::hasWarnings);

      if ("json".equalsIgnoreCase(format)) {
        printJson(registry, loadIssues, results, anyErrors, anyWarnings);
      } else {
        printText(registry, loadIssues, results);
      }

      if (anyErrors) {
        return 1;
      }
      if (strict && anyWarnings) {
        return 2;
      }
      return 0;
    } catch (Exception e) {
      System.err.println("Validation failed: " + e.getMessage());
      return 1;
    }
  }

  /** Coverage if declared, otherwise the current year plus one either side. */
  private static DateRange generationRange(ResolvedSpec resolved) {
    CalendarSpec.Coverage coverage = resolved.coverage();
    LocalDate today = LocalDate.now();
    LocalDate from = LocalDate.of(today.getYear() - 1, 1, 1);
    LocalDate to = LocalDate.of(today.getYear() + 1, 12, 31);
    if (coverage != null) {
      if (coverage.from() != null) from = coverage.from();
      if (coverage.to() != null) to = coverage.to();
    }
    return new DateRange(from, to);
  }

  private void printText(
      SpecRegistry registry,
      List<ValidationIssue> loadIssues,
      Map<String, ValidationResult> results) {
    for (ValidationIssue issue : loadIssues) {
      System.out.println(issue);
    }
    for (var entry : results.entrySet()) {
      String id = entry.getKey();
      ValidationResult result = entry.getValue();
      CalendarSpec spec = registry.getCalendar(id).orElse(null);
      if (result.hasErrors()) {
        System.out.println("Calendar '" + id + "' is INVALID.");
      } else if (result.hasWarnings()) {
        System.out.println("Calendar '" + id + "' is valid with warnings.");
      } else {
        System.out.println("Calendar '" + id + "' is valid.");
      }
      if (spec != null) {
        System.out.println("  Name: " + (spec.metadata() != null ? spec.metadata().name() : "N/A"));
        System.out.println("  Extends: " + spec.extendsList());
        System.out.println("  Uses: " + spec.uses());
        System.out.println("  Event sources: " + spec.eventSources().size());
      }
      for (ValidationIssue issue : result.issues()) {
        System.out.println("  " + issue);
      }
      System.out.println(
          "  Summary: "
              + result.errors().size()
              + " error(s), "
              + result.warnings().size()
              + " warning(s)");
    }
  }

  private void printJson(
      SpecRegistry registry,
      List<ValidationIssue> loadIssues,
      Map<String, ValidationResult> results,
      boolean anyErrors,
      boolean anyWarnings)
      throws Exception {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("ok", !anyErrors && !(strict && anyWarnings));
    doc.put("strict", strict);
    doc.put("load_errors", loadIssues.stream().map(ValidateCommand::issueToMap).toList());
    Map<String, Object> calendars = new LinkedHashMap<>();
    for (var entry : results.entrySet()) {
      ValidationResult r = entry.getValue();
      Map<String, Object> cal = new LinkedHashMap<>();
      cal.put("errors", r.errors().size());
      cal.put("warnings", r.warnings().size());
      cal.put("issues", r.issues().stream().map(ValidateCommand::issueToMap).toList());
      calendars.put(entry.getKey(), cal);
    }
    doc.put("calendars", calendars);
    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    System.out.println(mapper.writeValueAsString(doc));
  }

  private static Map<String, Object> issueToMap(ValidationIssue issue) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("severity", issue.severity().name());
    m.put("code", issue.code());
    m.put("location", issue.location());
    m.put("message", issue.message());
    return m;
  }

  static boolean isError(ValidationIssue issue) {
    return issue.severity() == Severity.ERROR;
  }
}
