package com.bdc.cli;

import com.bdc.source.SourceRegister;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Scaffolds the files a new market needs: a calendar spec, a weekend policy (reused or new), a
 * holiday group and one example holiday, a canonical source register and generated README, and a
 * reference-export entry. Published artifacts are updated only by release preparation.
 *
 * <p>Refuses to overwrite anything that already exists unless {@code --force} is given; {@code
 * --dry-run} reports the plan without writing anything.
 */
@Command(name = "scaffold", description = "Scaffold the files needed to onboard a new market")
public class ScaffoldCommand implements Callable<Integer> {

  @Option(
      names = {"--market"},
      required = true,
      description = "Calendar id for the new market, e.g. GB-LSE")
  private String market;

  @Option(
      names = {"--name"},
      required = true,
      description = "Human-readable market name, e.g. \"London Stock Exchange\"")
  private String name;

  @Option(
      names = {"--timezone"},
      required = true,
      description = "IANA timezone id, e.g. Europe/London")
  private String timezone;

  @Option(
      names = {"--mic"},
      required = true,
      description = "Market Identifier Code, e.g. XLON")
  private String mic;

  @Option(
      names = {"--weekend"},
      defaultValue = "SAT_SUN",
      description = "SAT_SUN, FRI_SAT, or custom (writes a new weekend policy module)")
  private String weekend;

  @Option(
      names = {"--from"},
      defaultValue = "2020-01-01",
      description = "Coverage start date")
  private LocalDate from;

  @Option(
      names = {"--to"},
      defaultValue = "2030-12-31",
      description = "Coverage end date")
  private LocalDate to;

  @Option(
      names = {"--root"},
      defaultValue = ".",
      description = "Repository root")
  private Path root;

  @Option(
      names = {"--dry-run"},
      description = "Print the plan without writing or modifying any files")
  private boolean dryRun;

  @Option(
      names = {"--force"},
      description = "Overwrite existing files and entries")
  private boolean force;

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  @Override
  public Integer call() {
    try {
      String weekendMode = weekend.toUpperCase(Locale.ROOT);
      if (!weekendMode.equals("SAT_SUN")
          && !weekendMode.equals("FRI_SAT")
          && !weekendMode.equals("CUSTOM")) {
        System.err.println("Error: --weekend must be one of SAT_SUN, FRI_SAT, custom");
        return 1;
      }
      if (from.isAfter(to)) {
        System.err.println("Error: --from must not be after --to");
        return 1;
      }

      String id = market;
      String slug = id.toLowerCase(Locale.ROOT).replace('-', '_');
      boolean customWeekend = weekendMode.equals("CUSTOM");
      String weekendModuleId =
          customWeekend ? slug + "_weekends" : defaultWeekendModule(weekendMode);
      String groupModuleId = slug + "_holidays";
      String holidayModuleId = slug + "_new_years_day";
      String sourceId = "TODO-" + slug + "-primary";

      Path calendarsDir = root.resolve("calendars");
      Path modulesDir = root.resolve("modules");
      Path sourcesDir = root.resolve("sources");
      Path blessedManifest = root.resolve("blessed").resolve("manifest.json");
      Path exportScript =
          root.resolve("scripts").resolve("reference").resolve("export_reference_calendars.py");

      Path calendarPath = calendarsDir.resolve(id + ".yaml");
      Path groupPath = modulesDir.resolve("groups").resolve(groupModuleId + ".yaml");
      Path holidayPath = modulesDir.resolve("holidays").resolve(holidayModuleId + ".yaml");
      Path weekendPath = modulesDir.resolve("policies").resolve(weekendModuleId + ".yaml");
      Path sourcesReadme = sourcesDir.resolve(id).resolve("README.md");
      Path sourceRegister = sourcesDir.resolve(id).resolve("register.json");

      List<Path> newFiles = new ArrayList<>();
      newFiles.add(calendarPath);
      newFiles.add(groupPath);
      newFiles.add(holidayPath);
      newFiles.add(sourceRegister);
      newFiles.add(sourcesReadme);
      if (customWeekend) {
        newFiles.add(weekendPath);
      }

      List<String> conflicts = new ArrayList<>();
      for (Path p : newFiles) {
        if (Files.exists(p)) {
          conflicts.add(root.relativize(p) + " (already exists)");
        }
      }
      boolean manifestExists = Files.exists(blessedManifest);
      if (manifestExists && !force) {
        ObjectNode manifestRoot = (ObjectNode) JSON_MAPPER.readTree(blessedManifest.toFile());
        JsonNode calendars = manifestRoot.get("calendars");
        if (calendars != null && calendars.has(id)) {
          conflicts.add(
              root.relativize(blessedManifest) + " (already has an entry for " + id + ")");
        }
      }
      boolean exportScriptExists = Files.exists(exportScript);
      if (exportScriptExists && !force) {
        List<String> lines = Files.readAllLines(exportScript);
        for (String line : lines) {
          if (line.contains("\"" + id + "\"")) {
            conflicts.add(root.relativize(exportScript) + " (already has an entry for " + id + ")");
            break;
          }
        }
      }

      if (!conflicts.isEmpty() && !force) {
        System.err.println("Refusing to overwrite existing files:");
        for (String c : conflicts) {
          System.err.println("  " + c);
        }
        System.err.println("Pass --force to overwrite.");
        return 1;
      }

      if (dryRun) {
        System.out.println("[dry-run] Would write:");
        for (Path p : newFiles) {
          System.out.println("  " + root.relativize(p));
        }
        if (exportScriptExists) {
          System.out.println("[dry-run] Would update: " + root.relativize(exportScript));
        }
        printNextSteps(id, slug, groupModuleId);
        return 0;
      }

      Files.createDirectories(calendarPath.getParent());
      Files.writeString(calendarPath, calendarYaml(id, weekendModuleId, groupModuleId, mic));

      Files.createDirectories(groupPath.getParent());
      Files.writeString(groupPath, groupYaml(groupModuleId, holidayModuleId));

      Files.createDirectories(holidayPath.getParent());
      Files.writeString(holidayPath, holidayYaml(holidayModuleId, sourceId));

      if (customWeekend) {
        Files.createDirectories(weekendPath.getParent());
        Files.writeString(weekendPath, weekendYaml(weekendModuleId, sourceId));
      }

      Files.createDirectories(sourcesReadme.getParent());
      Files.writeString(
          sourceRegister,
          JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sourceRegister(sourceId))
              + System.lineSeparator());
      String narrative = sourcesReadme(id);
      Files.writeString(
          sourcesReadme, SourceRegister.read(sourceRegister, sourcesDir).markdown(narrative));

      if (exportScriptExists) {
        updateExportScript(exportScript, id, mic);
      } else {
        System.out.println("Note: " + root.relativize(exportScript) + " not found; skipping.");
      }

      System.out.println("Scaffolded " + id + ":");
      for (Path p : newFiles) {
        System.out.println("  " + root.relativize(p));
      }
      if (exportScriptExists) {
        System.out.println("  (updated) " + root.relativize(exportScript));
      }
      System.out.println();
      printNextSteps(id, slug, groupModuleId);

      return 0;
    } catch (Exception e) {
      System.err.println("Scaffold failed: " + e.getMessage());
      return 1;
    }
  }

  private static String defaultWeekendModule(String weekendMode) {
    return weekendMode.equals("FRI_SAT") ? "weekend_fri_sat" : "weekend_sat_sun";
  }

  private String calendarYaml(String id, String weekendModuleId, String groupModuleId, String mic) {
    return """
        kind: calendar
        id: %s

        metadata:
          name: %s
          description: "TODO: describe this market's trading calendar"
          chronology: ISO
          timezone: %s
          mic: %s
          coverage:
            from: %s
            to: %s
            quality:
              - {scope: SCHEDULED_CLOSURES, from: %s, to: %s, quality: INCOMPLETE, evidence_ids: []}
              - {scope: EARLY_CLOSES, from: %s, to: %s, quality: INCOMPLETE, evidence_ids: []}
              - {scope: UNSCHEDULED_EXCEPTIONS, from: %s, to: %s, quality: INCOMPLETE, evidence_ids: []}

        # NONE | NEAREST_WEEKDAY | NEXT_AVAILABLE_WEEKDAY | FORWARD_ONLY
        weekend_shift_policy: NONE

        uses:
          - %s
          - %s
        """
        .formatted(
            id,
            name,
            timezone,
            mic,
            from,
            to,
            from,
            to,
            from,
            to,
            from,
            to,
            weekendModuleId,
            groupModuleId);
  }

  private String weekendYaml(String weekendModuleId, String sourceId) {
    return """
        kind: module
        id: %s

        # TODO: describe this market's weekend definition (and any historical change of weekend
        # days) and cite the authoritative source, following the pattern in
        # modules/policies/saudi_weekends.yaml.

        source:
          - id: %s

        policies:
          weekends:
            - {days: [SATURDAY, SUNDAY], to: %s}    # TODO: replace with the real effective-dated weekend periods
            - {days: [SATURDAY, SUNDAY], from: %s}
        """
        .formatted(weekendModuleId, sourceId, from, from);
  }

  private String groupYaml(String groupModuleId, String holidayModuleId) {
    return """
        kind: module
        id: %s

        uses:
          - %s
          # TODO: add the rest of this market's holiday modules here
        """
        .formatted(groupModuleId, holidayModuleId);
  }

  private String holidayYaml(String holidayModuleId, String sourceId) {
    return """
        kind: module
        id: %s

        # TODO: New Year's Day is a placeholder to get this market validating end to end. Replace
        # it with the market's real holidays, and cite each authoritative source with an id from
        # sources/<MARKET>/register.json. Regenerate the README table from that register.

        event_sources:
          - key: %s
            name: New Year's Day
            default_classification: CLOSED
            # shift_policy: NONE  # TODO: set NEAREST_WEEKDAY | NEXT_AVAILABLE_WEEKDAY | FORWARD_ONLY if this holiday is observed when it falls on a weekend
            source: {id: %s}
            rule:
              type: fixed_month_day
              month: 1
              day: 1
        """
        .formatted(holidayModuleId, holidayModuleId, sourceId);
  }

  private ObjectNode sourceRegister(String sourceId) {
    ObjectNode register = JSON_MAPPER.createObjectNode();
    register.put("schema_version", "1.0");
    ObjectNode entry = register.putArray("entries").addObject();
    entry.put("id", sourceId);
    entry.put("title", "TODO: title");
    entry.put("publisher", "TODO: publisher");
    entry.put("location", "TODO: original URL or source file");
    entry.put("retrieved", "TODO: YYYY-MM-DD");
    entry.put("covers", "TODO: describe the source's scope");
    entry.put("notes", "TODO: describe what the source establishes and its limits");
    entry.putArray("local_files");
    entry.putArray("support_intervals");
    return register;
  }

  private String sourcesReadme(String id) {
    return """
        # %s sources

        | id | title | publisher | url / file | retrieved | covers | notes |
        |----|-------|-----------|------------|-----------|--------|-------|
        | placeholder | placeholder | placeholder | placeholder | placeholder | placeholder | placeholder |

        ## Modelling decisions recorded against these sources

        - TODO: record the weekend definition and holiday observance rules, with the source used
          for each decision.

        ## Completeness and exception review

        - TODO: identify which authority is checked for unscheduled exceptions, who maintains the
          calendar, and when the evidence should be refreshed.
        """
        .formatted(id);
  }

  private void printNextSteps(String id, String slug, String groupModuleId) {
    String testName = camelCase(id);
    int year = LocalDate.now().getYear();
    System.out.println("Paste this into tools/src/test/java/com/bdc/test/GoldenTests.java:");
    System.out.println();
    System.out.println("  @Test");
    System.out.println("  void " + testName + year + "() throws IOException {");
    System.out.println(
        "    productionCalendarRunner.assertCsvGoldenMatch(\"" + id + "\", " + year + ");");
    System.out.println("  }");
    System.out.println();
    System.out.println("Next steps (see CONTRIBUTING.md):");
    System.out.println("  Release preparation adds reviewed calendars to the published manifest.");
    System.out.println(
        "  1. Complete sources/"
            + id
            + "/register.json with real citations, preserved evidence files and checksums; add"
            + " support_intervals only where reviewed evidence supports the full scope.");
    System.out.println(
        "  2. Regenerate the source table with python3 scripts/sources.py, then record the"
            + " exception authority, maintenance owner and review cadence in sources/"
            + id
            + "/README.md.");
    System.out.println(
        "  3. Replace the TODO placeholders in modules/holidays/"
            + slug
            + "_new_years_day.yaml and modules/groups/"
            + groupModuleId
            + ".yaml. Coverage starts INCOMPLETE for SCHEDULED_CLOSURES, EARLY_CLOSES, and"
            + " UNSCHEDULED_EXCEPTIONS; change a scope only when its cited evidence supports the"
            + " full interval.");
    System.out.println(
        "  4. Validate:   ./gradlew :tools:run --args=\"validate " + id + " --strict\"");
    System.out.println(
        "  5. Generate:   ./gradlew :tools:run --args=\"generate "
            + id
            + " --from "
            + from
            + " --to "
            + to
            + " --out generated/"
            + id
            + "\"");
    System.out.println(
        "  6. Add the golden test above, then run ./gradlew :tools:test -DupdateGoldens=true and"
            + " review the diff before committing.");
    System.out.println(
        "  7. Cross-validation: regenerate tools/src/test/resources/reference/"
            + id
            + "/*.csv with scripts/reference/export_reference_calendars.py and add an"
            + " allowlist.csv for any explained differences (see"
            + " tools/src/test/resources/reference/README.md).");
  }

  private static String camelCase(String id) {
    StringBuilder sb = new StringBuilder();
    String[] parts = id.split("-");
    for (int i = 0; i < parts.length; i++) {
      String lower = parts[i].toLowerCase(Locale.ROOT);
      if (i == 0) {
        sb.append(lower);
      } else {
        sb.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
      }
    }
    return sb.toString();
  }

  private void updateExportScript(Path scriptPath, String id, String mic) throws IOException {
    List<String> lines = new ArrayList<>(Files.readAllLines(scriptPath));
    int start = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).equals("EXPORTS = [")) {
        start = i;
        break;
      }
    }
    if (start < 0) {
      System.out.println("Note: could not find 'EXPORTS = [' in " + scriptPath + "; skipping.");
      return;
    }
    int end = -1;
    for (int i = start + 1; i < lines.size(); i++) {
      if (lines.get(i).equals("]")) {
        end = i;
        break;
      }
    }
    if (end < 0) {
      System.out.println(
          "Note: could not find closing ']' for EXPORTS in " + scriptPath + "; skipping.");
      return;
    }
    // Remove any existing entry for this id (force re-run case) before appending the new one.
    for (int i = end - 1; i > start; i--) {
      if (lines.get(i).contains("\"" + id + "\"")) {
        lines.remove(i);
        end--;
      }
    }
    lines.add(end, "    (\"" + id + "\", \"" + mic + "\", \"" + from + "\", \"" + to + "\"),");
    Files.write(scriptPath, lines, java.nio.charset.StandardCharsets.UTF_8);
    // Preserve a trailing newline if the original file had one.
  }
}
