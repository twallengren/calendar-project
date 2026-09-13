package com.bdc.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

      List<Map<String, Object>> rows = new ArrayList<>();
      JsonNode calendars = manifest.path("calendars");
      List<String> ids = new ArrayList<>();
      Iterator<String> names = calendars.fieldNames();
      while (names.hasNext()) {
        ids.add(names.next());
      }
      for (String id : ids) {
        rows.add(buildRow(id, calendars.get(id), releaseVersion));
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

  private Map<String, Object> buildRow(String id, JsonNode manifestEntry, String releaseVersion)
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

    List<String> sourceIds = readSourceIds(id);
    Map<String, Object> sources = new LinkedHashMap<>();
    sources.put("ids", sourceIds);
    sources.put("count", sourceIds.size());
    row.put("sources", sources);

    row.put("cross_validation", readCrossValidation(id));
    row.put("release_version", releaseVersion);
    return row;
  }

  private List<String> readSourceIds(String calendarId) throws Exception {
    Path readme = sourcesDir.resolve(calendarId).resolve("README.md");
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
          .append(sources.get("count"))
          .append(" | ")
          .append(crossValidationCell)
          .append(" | ")
          .append(row.get("release_version"))
          .append(" |\n");
    }
    System.out.print(sb);
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
