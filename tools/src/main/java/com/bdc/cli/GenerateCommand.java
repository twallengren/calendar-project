package com.bdc.cli;

import com.bdc.artifact.ArtifactStore;
import com.bdc.emitter.CsvEmitter;
import com.bdc.emitter.MetadataEmitter;
import com.bdc.emitter.SpecEmitter;
import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.BitemporalMeta;
import com.bdc.model.CalendarSpec;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import java.nio.file.Files;
import java.nio.file.Path;
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
      description = "Output directory (mutually exclusive with --store)")
  private Path outputDir;

  @Option(
      names = {"--store", "-s"},
      description = "Store as a timestamped artifact in artifacts/generated/")
  private boolean store;

  @Option(
      names = {"--artifacts-dir"},
      description = "Artifacts directory",
      defaultValue = "artifacts")
  private Path artifactsDir;

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
      names = {"--include-specs"},
      description = "Include calendar.yaml and resolved.yaml in output")
  private boolean includeSpecs;

  @Override
  public Integer call() {
    try {
      if (!store && outputDir == null) {
        System.err.println("Error: Either --out or --store must be specified");
        return 1;
      }

      SpecRegistry registry = new SpecRegistry();
      registry.loadCalendarsFromDirectory(calendarsDir);
      registry.loadModulesFromDirectory(modulesDir);

      SpecResolver resolver = new SpecResolver(registry);
      ResolvedSpec resolved = resolver.resolve(calendarId);

      EventGenerator generator = new EventGenerator();
      List<Event> events = generator.generate(resolved, from, to);

      if (store) {
        // Store as bitemporal artifact
        BitemporalMeta meta =
            sourceVersion != null
                ? BitemporalMeta.now(
                    sourceVersion,
                    BitemporalMeta.now().toolVersion(),
                    System.getProperty("user.name", "unknown"))
                : BitemporalMeta.now();

        ArtifactStore artifactStore = new ArtifactStore(artifactsDir);

        // Also store the resolved spec
        Path resolvedPath = artifactStore.storeResolvedSpec(resolved, meta);

        // Store generated events
        Path storedDir =
            artifactStore.storeGeneratedEvents(calendarId, from, to, events, resolved, meta);

        System.out.println("Generated " + events.size() + " events");
        System.out.println("  Resolved spec: " + resolvedPath);
        System.out.println("  Events stored at: " + storedDir);
        System.out.println("  Transaction time: " + meta.transactionTime());
        System.out.println("  Valid range: " + from + " to " + to);
      } else {
        // Emit to specified output directory
        Files.createDirectories(outputDir);

        // Emit CSV
        CsvEmitter csvEmitter = new CsvEmitter();
        Path csvPath = outputDir.resolve("events.csv");
        csvEmitter.emit(events, csvPath);

        // Emit metadata
        MetadataEmitter metadataEmitter = new MetadataEmitter();
        Path metadataPath = outputDir.resolve("metadata.json");
        metadataEmitter.emit(resolved, events, from, to, metadataPath);

        System.out.println("Generated " + events.size() + " events");
        System.out.println("  CSV: " + csvPath);
        System.out.println("  Metadata: " + metadataPath);

        // Emit spec files if requested
        if (includeSpecs) {
          SpecEmitter specEmitter = new SpecEmitter();
          CalendarSpec calendarSpec = registry.getCalendar(calendarId).orElse(null);

          if (calendarSpec != null) {
            Path calendarPath = outputDir.resolve("calendar.yaml");
            specEmitter.emitCalendarSpec(calendarSpec, calendarPath);
            System.out.println("  Calendar spec: " + calendarPath);
          }

          Path resolvedPath = outputDir.resolve("resolved.yaml");
          specEmitter.emitResolvedSpec(resolved, resolvedPath);
          System.out.println("  Resolved spec: " + resolvedPath);
        }
      }

      return 0;
    } catch (Exception e) {
      System.err.println("Generation failed: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }
}
