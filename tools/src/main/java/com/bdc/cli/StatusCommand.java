package com.bdc.cli;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.EventSource;
import com.bdc.model.ResolvedSpec;
import com.bdc.model.SourceCitation;
import com.bdc.resolver.SpecResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * A one-row-per-calendar market status scorecard, built from the blessed artifacts: kind, timezone,
 * coverage, event counts, cited sources and cross-validation summary.
 */
@Command(name = "status", description = "Market status scorecard: one row per blessed calendar")
public class StatusCommand implements Callable<Integer> {

  private static final Pattern SOURCE_ROW = Pattern.compile("^\\|\\s*`([^`]+)`\\s*\\|");

  @Option(
      names = {"--format"},
      description = "Output format: markdown or json",
      defaultValue = "markdown")
  private String format;

  @Option(
      names = {"--blessed-dir"},
      description = "Blessed artifacts directory",
      defaultValue = "blessed")
  private Path blessedDir;

  @Option(
      names = {"--sources-dir"},
      description = "Sources directory",
      defaultValue = "sources")
  private Path sourcesDir;

  @Option(
      names = {"--calendars-dir"},
      description =
          "Calendars directory (used to resolve inherited citations; falls back to a"
              + " directory-only source count when absent)",
      defaultValue = "calendars")
  private Path calendarsDir;

  @Option(
      names = {"--modules-dir"},
      description = "Modules directory",
      defaultValue = "modules")
  private Path modulesDir;

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public Integer call() {
    try {
      Path manifestPath = blessedDir.resolve("manifest.json");
      if (!Files.exists(manifestPath)) {
        System.err.println("Status failed: manifest not found: " + manifestPath);
        return 1;
      }
      JsonNode manifest = mapper.readTree(manifestPath.toFile());
      String releaseVersion = manifest.path("release_version").path("semantic").asText("unknown");

      SpecRegistry registry = null;
      SpecResolver resolver = null;
      if (Files.isDirectory(calendarsDir)) {
        registry = new SpecRegistry();
        registry.loadCalendarsFromDirectory(calendarsDir);
        registry.loadModulesFromDirectory(modulesDir);
        resolver = new SpecResolver(registry);
      }

      List<Map<String, Object>> rows = new ArrayList<>();
      JsonNode calendars = manifest.path("calendars");
      List<String> ids = new ArrayList<>();
      Iterator<String> names = calendars.fieldNames();
      while (names.hasNext()) {
        ids.add(names.next());
      }
      for (String id : ids) {
        rows.add(buildRow(id, calendars.get(id), releaseVersion, registry, resolver));
      }
      // Markets first, then base, alphabetically within each group.
      rows.sort(
          Comparator.<Map<String, Object>, Boolean>comparing(r -> !"market".equals(r.get("kind")))
              .thenComparing(r -> (String) r.get("id")));

      if ("json".equalsIgnoreCase(format)) {
        printJson(rows);
      } else {
        printMarkdown(rows);
      }
      return 0;
    } catch (Exception e) {
      System.err.println("Status failed: " + e.getMessage());
      return 1;
    }
  }

  private Map<String, Object> buildRow(
      String id,
      JsonNode manifestEntry,
      String releaseVersion,
      SpecRegistry registry,
      SpecResolver resolver)
      throws Exception {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", id);

    String name = id;
    String timezone = null;
    String kind = manifestEntry.path("kind").asText("market");
    String coverageFrom = null;
    String coverageTo = null;
    String verifiedThrough = null;
    long closures = 0;
    long earlyCloses = 0;
    long projected = 0;

    Path metadataPath = blessedDir.resolve(id).resolve("metadata.json");
    if (Files.exists(metadataPath)) {
      JsonNode metadata = mapper.readTree(metadataPath.toFile());
      name = metadata.path("calendar_name").asText(id);
      timezone = metadata.path("timezone").asText(null);
      kind = metadata.path("kind").asText(kind);
      JsonNode coverage = metadata.path("coverage");
      coverageFrom = coverage.path("from").asText(null);
      coverageTo = coverage.path("to").asText(null);
      verifiedThrough = coverage.path("verified_through").asText(null);
      closures = metadata.path("counts_by_type").path("CLOSED").asLong(0);
      earlyCloses = metadata.path("counts_by_type").path("EARLY_CLOSE").asLong(0);
      projected = metadata.path("counts_by_status").path("PROJECTED").asLong(0);
    }

    row.put("name", name);
    row.put("kind", kind);
    row.put("timezone", timezone);
    Map<String, Object> coverage = new LinkedHashMap<>();
    coverage.put("from", coverageFrom);
    coverage.put("to", coverageTo);
    coverage.put("verified_through", verifiedThrough);
    row.put("coverage", coverage);
    Map<String, Object> counts = new LinkedHashMap<>();
    counts.put("closures", closures);
    counts.put("early_closes", earlyCloses);
    counts.put("projected", projected);
    row.put("counts", counts);

    String basis = "directory";
    Map<String, Object> sources;
    if (registry != null && registry.getCalendar(id).isPresent()) {
      try {
        sources = buildResolvedSources(id, registry, resolver);
        basis = "resolved";
      } catch (RuntimeException e) {
        System.err.println(
            "Warning: "
                + id
                + ": failed to resolve for source citations ("
                + e.getMessage()
                + "),"
                + " falling back to directory-based source count");
        sources = buildDirectorySources(id);
      }
    } else {
      sources = buildDirectorySources(id);
    }
    row.put("sources", sources);
    row.put("sources_basis", basis);

    row.put("cross_validation", readCrossValidation(id));
    row.put("release_version", releaseVersion);
    return row;
  }

  private Map<String, Object> buildDirectorySources(String calendarId) throws Exception {
    List<String> sourceIds = readSourceIds(calendarId);
    Map<String, Object> sources = new LinkedHashMap<>();
    sources.put("ids", sourceIds);
    sources.put("count", sourceIds.size());
    return sources;
  }

  /**
   * The citations actually in effect for a calendar: every distinct citation id (or, for citations
   * without an id, title/url/file) attached to a resolved event source, unioned with every id
   * documented in the calendar's own {@code sources/<CAL>/README.md} or one belonging to a calendar
   * in its {@code extends} chain. The union means a README can document background sources that no
   * single event cites directly (e.g. archival context) without being dropped, while a citation id
   * that isn't documented anywhere in the chain is reported as unresolved.
   */
  private Map<String, Object> buildResolvedSources(
      String calendarId, SpecRegistry registry, SpecResolver resolver) throws Exception {
    ResolvedSpec resolved = resolver.resolve(calendarId);

    LinkedHashSet<String> citedIds = new LinkedHashSet<>();
    LinkedHashSet<String> citedLabels = new LinkedHashSet<>();
    for (EventSource eventSource : resolved.eventSources()) {
      for (SourceCitation citation : eventSource.source()) {
        if (notBlank(citation.id())) {
          citedIds.add(citation.id());
        } else if (notBlank(citation.title())) {
          citedLabels.add(citation.title());
        } else if (notBlank(citation.url())) {
          citedLabels.add(citation.url());
        } else if (notBlank(citation.file())) {
          citedLabels.add(citation.file());
        }
      }
    }

    Map<String, String> readmeIds = new LinkedHashMap<>();
    collectReadmeIds(calendarId, registry, readmeIds, new LinkedHashSet<>());

    List<String> ids = new ArrayList<>(readmeIds.keySet());
    List<String> unresolved = new ArrayList<>();
    for (String citedId : citedIds) {
      if (!readmeIds.containsKey(citedId)) {
        ids.add(citedId);
        unresolved.add(citedId);
      }
    }
    ids.addAll(citedLabels);

    Map<String, Object> sources = new LinkedHashMap<>();
    sources.put("ids", ids);
    sources.put("count", ids.size());
    sources.put("readmes", readmeIds);
    sources.put("unresolved", unresolved);
    return sources;
  }

  /**
   * Merges the citation ids documented in {@code sources/<calendarId>/README.md} into {@code
   * idToReadme} (id -&gt; the README path it came from), then recurses into every calendar {@code
   * calendarId} extends. Earlier (closer to {@code calendarId}) declarations win on id collision;
   * {@code visited} guards against revisiting a calendar reachable via more than one path.
   */
  private void collectReadmeIds(
      String calendarId, SpecRegistry registry, Map<String, String> idToReadme, Set<String> visited)
      throws Exception {
    if (!visited.add(calendarId)) {
      return;
    }
    Path readme = sourcesDir.resolve(calendarId).resolve("README.md");
    if (Files.exists(readme)) {
      for (String sourceId : parseReadmeIds(readme)) {
        idToReadme.putIfAbsent(sourceId, readme.toString());
      }
    }
    registry
        .getCalendar(calendarId)
        .ifPresent(
            spec -> {
              for (String parentId : spec.extendsList()) {
                try {
                  collectReadmeIds(parentId, registry, idToReadme, visited);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }
            });
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private List<String> readSourceIds(String calendarId) throws Exception {
    return parseReadmeIds(sourcesDir.resolve(calendarId).resolve("README.md"));
  }

  private List<String> parseReadmeIds(Path readme) throws Exception {
    List<String> ids = new ArrayList<>();
    if (!Files.exists(readme)) {
      return ids;
    }
    for (String line : Files.readAllLines(readme)) {
      Matcher matcher = SOURCE_ROW.matcher(line.strip());
      if (matcher.find()) {
        ids.add(matcher.group(1));
      }
    }
    return ids;
  }

  /**
   * Per-reference ok/discrepancies + allowlisted count, from a prior {@code crossvalidate --out}.
   */
  private Object readCrossValidation(String calendarId) throws Exception {
    Path path = blessedDir.resolve(calendarId).resolve("cross_validation.json");
    if (!Files.exists(path)) {
      return "none";
    }
    JsonNode doc = mapper.readTree(path.toFile());
    Map<String, Object> summary = new LinkedHashMap<>();
    for (JsonNode result : doc.path("results")) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("status", result.path("status").asText());
      entry.put("allowlisted", result.path("counts").path("allowlisted").asInt(0));
      summary.put(result.path("source").asText(), entry);
    }
    return summary.isEmpty() ? "none" : summary;
  }

  private void printJson(List<Map<String, Object>> rows) throws Exception {
    ObjectMapper out = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    System.out.println(out.writeValueAsString(Map.of("calendars", rows)));
  }

  @SuppressWarnings("unchecked")
  private void printMarkdown(List<Map<String, Object>> rows) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        "| ID | Name | Kind | Timezone | Coverage | Verified Through | Closures | Early Closes"
            + " | Projected | Sources | Cross-validation | Release |\n");
    sb.append(
        "|----|------|------|----------|----------|-------------------|----------|"
            + "---------------|-----------|---------|-------------------|---------|\n");
    List<String> warnings = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      Map<String, Object> coverage = (Map<String, Object>) row.get("coverage");
      Map<String, Object> counts = (Map<String, Object>) row.get("counts");
      Map<String, Object> sources = (Map<String, Object>) row.get("sources");
      String coverageCell = orDash(coverage.get("from")) + " to " + orDash(coverage.get("to"));
      String crossValidationCell = formatCrossValidation(row.get("cross_validation"));
      sb.append("| ")
          .append(row.get("id"))
          .append(" | ")
          .append(row.get("name"))
          .append(" | ")
          .append(row.get("kind"))
          .append(" | ")
          .append(orDash(row.get("timezone")))
          .append(" | ")
          .append(coverageCell)
          .append(" | ")
          .append(orDash(coverage.get("verified_through")))
          .append(" | ")
          .append(counts.get("closures"))
          .append(" | ")
          .append(counts.get("early_closes"))
          .append(" | ")
          .append(counts.get("projected"))
          .append(" | ")
          .append(formatSourcesCell(sources))
          .append(" | ")
          .append(crossValidationCell)
          .append(" | ")
          .append(row.get("release_version"))
          .append(" |\n");

      List<String> unresolved = (List<String>) sources.get("unresolved");
      if (unresolved != null && !unresolved.isEmpty()) {
        warnings.add(
            "Warning: "
                + row.get("id")
                + " cites unresolved source id(s) (no README row backs them): "
                + String.join(", ", unresolved));
      }
    }
    System.out.print(sb);
    for (String warning : warnings) {
      System.err.println(warning);
    }
  }

  @SuppressWarnings("unchecked")
  private static String formatSourcesCell(Map<String, Object> sources) {
    Object countObj = sources.get("count");
    List<String> unresolved = (List<String>) sources.get("unresolved");
    if (unresolved != null && !unresolved.isEmpty()) {
      return countObj + " (" + unresolved.size() + " unresolved)";
    }
    return String.valueOf(countObj);
  }

  @SuppressWarnings("unchecked")
  private static String formatCrossValidation(Object crossValidation) {
    if (!(crossValidation instanceof Map<?, ?> map) || map.isEmpty()) {
      return "none";
    }
    List<String> parts = new ArrayList<>();
    for (var entry : ((Map<String, Object>) map).entrySet()) {
      Map<String, Object> detail = (Map<String, Object>) entry.getValue();
      parts.add(
          entry.getKey()
              + ": "
              + detail.get("status")
              + " (allowlisted="
              + detail.get("allowlisted")
              + ")");
    }
    return String.join("; ", parts);
  }

  private static String orDash(Object value) {
    return value == null ? "-" : value.toString();
  }
}
