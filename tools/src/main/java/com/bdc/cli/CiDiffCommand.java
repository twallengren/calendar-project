package com.bdc.cli;

import com.bdc.diff.*;
import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "ci-diff",
    description = "Compare generated calendars against blessed reference artifacts for CI")
public class CiDiffCommand implements Callable<Integer> {

  @Option(
      names = {"--blessed-dir", "-b"},
      description = "Path to blessed artifacts directory",
      defaultValue = "blessed")
  private Path blessedDir;

  @Option(
      names = {"--calendars", "-c"},
      description = "Comma-separated calendar IDs or 'all'",
      defaultValue = "all")
  private String calendars;

  @Option(
      names = {"--from"},
      description = "Override range start date")
  private LocalDate fromOverride;

  @Option(
      names = {"--to"},
      description = "Override range end date")
  private LocalDate toOverride;

  @Option(
      names = {"--output-format", "-f"},
      description = "Output format: json or markdown",
      defaultValue = "json")
  private String outputFormat;

  @Option(
      names = {"--cutoff-date"},
      description = "Cutoff date for historical vs future (default: today)")
  private LocalDate cutoffDate;

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
      names = {"--current-sha"},
      description = "Current git SHA for report")
  private String currentSha;

  // Exit codes
  private static final int EXIT_NO_CHANGES = 0;
  private static final int EXIT_MINOR_CHANGES = 1;
  private static final int EXIT_MAJOR_CHANGES = 2;
  private static final int EXIT_ERROR = 3;

  @Override
  public Integer call() {
    try {
      if (cutoffDate == null) {
        cutoffDate = LocalDate.now();
      }

      // Load blessed manifest
      BlessedArtifactLoader loader = new BlessedArtifactLoader();
      BlessedArtifactLoader.BlessedManifest manifest = loader.loadManifest(blessedDir);

      // Determine which calendars to check
      Set<String> calendarIds;
      if ("all".equalsIgnoreCase(calendars)) {
        calendarIds = manifest.calendars().keySet();
      } else {
        calendarIds = new LinkedHashSet<>(Arrays.asList(calendars.split(",")));
      }

      // Load spec registry
      SpecRegistry registry = new SpecRegistry();
      registry.loadCalendarsFromDirectory(calendarsDir);
      registry.loadModulesFromDirectory(modulesDir);

      SpecResolver resolver = new SpecResolver(registry);
      EventGenerator generator = new EventGenerator();
      CalendarDiffEngine diffEngine = new CalendarDiffEngine();

      Map<String, CalendarDiff> diffs = new LinkedHashMap<>();

      for (String calendarId : calendarIds) {
        BlessedArtifactLoader.CalendarInfo calInfo = manifest.calendars().get(calendarId);
        if (calInfo == null) {
          System.err.println(
              "Warning: Calendar " + calendarId + " not found in manifest, skipping");
          continue;
        }

        LocalDate rangeStart = fromOverride != null ? fromOverride : calInfo.rangeStart();
        LocalDate rangeEnd = toOverride != null ? toOverride : calInfo.rangeEnd();

        // Load blessed events
        List<Event> blessedEvents = loader.loadBlessedEvents(blessedDir, calendarId);

        // Generate current events
        ResolvedSpec resolved = resolver.resolve(calendarId);
        List<Event> generatedEvents = generator.generate(resolved, rangeStart, rangeEnd);

        // Compare - use the blessed calendar's original range for severity classification
        CalendarDiff diff =
            diffEngine.compare(
                calendarId,
                generatedEvents,
                blessedEvents,
                cutoffDate,
                calInfo.rangeStart(),
                calInfo.rangeEnd());
        diffs.put(calendarId, diff);
      }

      // Create report
      String sha = currentSha != null ? currentSha : "unknown";
      DiffReport report = DiffReport.create(diffs, manifest.releaseVersion().semantic(), sha);

      // Format and output
      DiffReportFormatter formatter = new DiffReportFormatter();
      String output;
      if ("markdown".equalsIgnoreCase(outputFormat)) {
        output = formatter.formatAsMarkdown(report);
      } else {
        output = formatter.formatAsJson(report);
      }
      System.out.println(output);

      // Return appropriate exit code
      return switch (report.overallSeverity()) {
        case NONE -> EXIT_NO_CHANGES;
        case MINOR -> EXIT_MINOR_CHANGES;
        case MAJOR -> EXIT_MAJOR_CHANGES;
      };

    } catch (Exception e) {
      System.err.println("CI diff failed: " + e.getMessage());
      e.printStackTrace();
      return EXIT_ERROR;
    }
  }
}
