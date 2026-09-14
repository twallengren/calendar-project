package com.bdc.cli;

import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import com.bdc.validation.CrossValidationResult;
import com.bdc.validation.CrossValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "crossvalidate",
    description =
        "Cross-validate generated calendars against third-party reference data (exchange_calendars,"
            + " QuantLib)")
public class CrossvalidateCommand implements Callable<Integer> {

  @Parameters(index = "0", arity = "0..1", description = "The calendar ID to cross-validate")
  private String calendarId;

  @Option(
      names = {"--all"},
      description = "Cross-validate every calendar that has reference data")
  private boolean all;

  @Option(
      names = {"--reference-dir"},
      description = "Reference CSV directory",
      defaultValue = "tools/src/test/resources/reference")
  private Path referenceDir;

  @Option(
      names = {"--format"},
      description = "Output format: text or json",
      defaultValue = "text")
  private String format;

  @Option(
      names = {"--out"},
      description =
          "Directory under which to write <ID>/cross_validation.json for each cross-validated"
              + " calendar (e.g. 'blessed')")
  private Path outDir;

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
        System.err.println("Cross-validation failed: specify a calendar ID or --all");
        return 1;
      }

      SpecRegistry registry = new SpecRegistry();
      registry.loadCalendarsFromDirectory(calendarsDir);
      registry.loadModulesFromDirectory(modulesDir);
      registry.assertNoLoadErrors();

      List<String> ids = all ? discoverCalendarIds(referenceDir) : List.of(calendarId);

      SpecResolver resolver = new SpecResolver(registry);
      EventGenerator generator = new EventGenerator();
      CrossValidator validator = new CrossValidator();

      Map<String, List<CrossValidationResult>> resultsByCalendar = new LinkedHashMap<>();
      boolean anyUnexplained = false;

      for (String id : ids) {
        List<Path> referenceFiles = referenceFilesFor(referenceDir, id);
        List<CrossValidationResult> results = new ArrayList<>();
        if (referenceFiles.isEmpty()) {
          if (!all) {
            System.err.println("No reference data for " + id + " under " + referenceDir);
          }
        } else if (registry.getCalendar(id).isEmpty()) {
          System.err.println(
              "Warning: " + id + " has reference data but is not a known calendar, skipping");
        } else {
          ResolvedSpec spec = resolver.resolve(id);
          for (Path referenceFile : referenceFiles) {
            CrossValidationResult result = validator.compare(id, spec, generator, referenceFile);
            results.add(result);
            if (!result.isClean()) {
              anyUnexplained = true;
            }
          }
        }
        resultsByCalendar.put(id, results);

        if (outDir != null) {
          writeResult(outDir, id, results);
        }
      }

      if ("json".equalsIgnoreCase(format)) {
        printJson(resultsByCalendar);
      } else {
        printText(resultsByCalendar);
      }

      return anyUnexplained ? 1 : 0;
    } catch (Exception e) {
      System.err.println("Cross-validation failed: " + e.getMessage());
      return 1;
    }
  }

  /** Calendar ids with at least one non-allowlist CSV directly under {@code referenceDir}. */
  private static List<String> discoverCalendarIds(Path referenceDir) throws Exception {
    Set<String> ids = new LinkedHashSet<>();
    if (Files.isDirectory(referenceDir)) {
      try (Stream<Path> dirs = Files.list(referenceDir)) {
        for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
          if (!referenceFilesFor(referenceDir, dir.getFileName().toString()).isEmpty()) {
            ids.add(dir.getFileName().toString());
          }
        }
      }
    }
    return new ArrayList<>(ids);
  }

  private static List<Path> referenceFilesFor(Path referenceDir, String calendarId)
      throws Exception {
    Path dir = referenceDir.resolve(calendarId);
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(p -> p.toString().endsWith(".csv"))
          .filter(p -> !p.getFileName().toString().equals("allowlist.csv"))
          .sorted()
          .toList();
    }
  }

  private void writeResult(Path outDir, String calendarId, List<CrossValidationResult> results)
      throws Exception {
    Path dir = outDir.resolve(calendarId);
    Files.createDirectories(dir);
    Map<String, Object> doc = calendarDoc(calendarId, results);
    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    Files.writeString(dir.resolve("cross_validation.json"), mapper.writeValueAsString(doc) + "\n");
  }

  private static Map<String, Object> calendarDoc(
      String calendarId, List<CrossValidationResult> results) {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("calendar_id", calendarId);
    String status;
    if (results.isEmpty()) {
      status = "no-reference";
    } else {
      status = results.stream().allMatch(CrossValidationResult::isClean) ? "ok" : "discrepancies";
    }
    doc.put("status", status);
    doc.put("results", results.stream().map(CrossvalidateCommand::resultToMap).toList());
    return doc;
  }

  private static Map<String, Object> resultToMap(CrossValidationResult r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("source", r.source());
    m.put("library_version", r.libraryVersion());
    m.put("from", r.from() != null ? r.from().toString() : null);
    m.put("to", r.to() != null ? r.to().toString() : null);
    m.put("compared_types", r.comparedTypes().stream().map(Enum::name).sorted().toList());
    Map<String, Object> counts = new LinkedHashMap<>();
    counts.put("matched", r.matchedCount());
    counts.put("allowlisted", r.allowlistedCount());
    counts.put("unexplained", r.unexplainedCount());
    m.put("counts", counts);
    m.put("unexplained_rows", r.unexplainedRows());
    m.put("stale_allowlist_rows", r.staleAllowlistRows());
    m.put("status", r.status());
    return m;
  }

  private void printText(Map<String, List<CrossValidationResult>> resultsByCalendar) {
    for (var entry : resultsByCalendar.entrySet()) {
      String id = entry.getKey();
      List<CrossValidationResult> results = entry.getValue();
      if (results.isEmpty()) {
        System.out.println(id + ": no-reference");
        continue;
      }
      for (CrossValidationResult r : results) {
        System.out.println(
            id
                + " vs "
                + r.source()
                + " ("
                + r.libraryVersion()
                + "): "
                + r.from()
                + " to "
                + r.to()
                + " matched="
                + r.matchedCount()
                + " allowlisted="
                + r.allowlistedCount()
                + " unexplained="
                + r.unexplainedCount()
                + " ["
                + r.status()
                + "]");
        Stream.concat(r.unexplainedRows().stream(), r.staleAllowlistRows().stream())
            .limit(50)
            .forEach(p -> System.out.println("    " + p));
        if (r.unexplainedCount() > 50) {
          System.out.println("    ... and " + (r.unexplainedCount() - 50) + " more");
        }
      }
    }
  }

  private void printJson(Map<String, List<CrossValidationResult>> resultsByCalendar)
      throws Exception {
    Map<String, Object> doc = new LinkedHashMap<>();
    for (var entry : resultsByCalendar.entrySet()) {
      doc.put(entry.getKey(), calendarDoc(entry.getKey(), entry.getValue()));
    }
    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    System.out.println(mapper.writeValueAsString(doc));
  }
}
