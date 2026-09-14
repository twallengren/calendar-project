package com.bdc.cli;

import com.bdc.emitter.CsvEmitter;
import com.bdc.emitter.JsonEventsEmitter;
import com.bdc.emitter.MetadataEmitter;
import com.bdc.emitter.SpecEmitter;
import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.CalendarSpec;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "generate", description = "Generate calendar events for a date range")
public class GenerateCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "The calendar ID to generate")
  private String calendarId;

  @Option(
      names = {"--from", "-f"},
      description = "Start date (ISO format)",
      required = true)
  private LocalDate from;

  @Option(
      names = {"--to", "-t"},
      description = "End date (ISO format)",
      required = true)
  private LocalDate to;

  @Option(
      names = {"--out", "-o"},
      description = "Output directory")
  private Path outputDir;

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

  @Option(
      names = {"--source-version"},
      description = "Source version (e.g., git SHA)")
  private String sourceVersion;

  @Option(
      names = {"--release-version"},
      description = "Release version (e.g., 5.0.0)")
  private String releaseVersion;

  @Option(
      names = {"--output-chronology"},
      description = "Add an alternate date column in the specified chronology (e.g., UMM_AL_QURA)")
  private String outputChronology;

  @Option(
      names = {"--generated-at"},
      description =
          "Timestamp to record as generated_at (ISO instant). Pass a fixed value to make"
              + " metadata.json reproducible")
  private Instant generatedAt;

  @Option(
      names = {"--include-specs"},
      description = "Include calendar.yaml and resolved.yaml in output")
  private boolean includeSpecs;

  @Override
  public Integer call() {
    try {
      if (outputDir == null) {
        System.err.println("Error: --out must be specified");
        return 1;
      }

      SpecRegistry registry = new SpecRegistry();
      registry.loadCalendarsFromDirectory(calendarsDir);
      registry.loadModulesFromDirectory(modulesDir);
      registry.assertNoLoadErrors();

      SpecResolver resolver = new SpecResolver(registry);
      ResolvedSpec resolved = resolver.resolve(calendarId);

      EventGenerator generator = new EventGenerator();
      var details = generator.generateWithDetails(resolved, from, to);
      List<Event> events = details.stream().map(com.bdc.generator.CompiledEvent::event).toList();

      // Emit to specified output directory
      Files.createDirectories(outputDir);

      // Emit CSV
      CsvEmitter csvEmitter = new CsvEmitter();
      Path csvPath = outputDir.resolve("events.csv");
      csvEmitter.emit(events, csvPath, outputChronology);

      // Emit JSON events
      JsonEventsEmitter jsonEmitter = new JsonEventsEmitter();
      Path jsonPath = outputDir.resolve("events.json");
      jsonEmitter.emit(resolved, events, from, to, jsonPath);

      // Emit metadata
      MetadataEmitter metadataEmitter = new MetadataEmitter(generatedAt);
      Path metadataPath = outputDir.resolve("metadata.json");
      metadataEmitter.emitWithDetails(
          resolved, details, from, to, metadataPath, sourceVersion, releaseVersion);

      System.out.println("Generated " + events.size() + " events");
      System.out.println("  CSV: " + csvPath);
      System.out.println("  JSON: " + jsonPath);
      System.out.println("  Metadata: " + metadataPath);

      // Emit spec files if requested
      if (includeSpecs) {
        SpecEmitter specEmitter = new SpecEmitter();
        CalendarSpec calendarSpec = registry.getCalendar(calendarId).orElse(null);

        if (calendarSpec != null) {
          Path calendarPath = outputDir.resolve("calendar.yaml");
          specEmitter.emitCalendarSpec(calendarSpec, calendarPath);
          System.out.println("  Calendar spec: " + calendarPath);
        } else {
          System.err.println(
              "  Warning: Calendar spec not found in registry, skipping calendar.yaml");
        }

        Path resolvedPath = outputDir.resolve("resolved.yaml");
        specEmitter.emitResolvedSpec(resolved, resolvedPath);
        System.out.println("  Resolved spec: " + resolvedPath);
      }

      return 0;
    } catch (Exception e) {
      System.err.println("Generation failed: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }
}
