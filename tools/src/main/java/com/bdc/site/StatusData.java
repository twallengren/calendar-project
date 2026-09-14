package com.bdc.site;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two accuracy signals the JSON API does not publish: the cross-validation summary from {@code
 * blessed/<ID>/cross_validation.json} and the cited source ids from {@code sources/<ID>/README.md}.
 *
 * <p>Same inputs and same shape as the {@code status} scorecard command (see {@code
 * spec/SPEC.md#status-and-cross-validation-artifacts}); read here rather than shelling out so the
 * site build stays a single process.
 */
public record StatusData(List<Reference> crossValidation, List<String> sourceIds) {

  /** One reference implementation the calendar was compared against. */
  public record Reference(String source, String status, int allowlisted, int unexplained) {
    public boolean ok() {
      return "ok".equals(status);
    }
  }

  private static final Pattern SOURCE_ROW = Pattern.compile("^\\|\\s*`([^`]+)`\\s*\\|");

  public static final StatusData EMPTY = new StatusData(List.of(), List.of());

  public boolean hasCrossValidation() {
    return !crossValidation.isEmpty();
  }

  /** True when every reference compared clean. */
  public boolean allClean() {
    return hasCrossValidation() && crossValidation.stream().allMatch(Reference::ok);
  }

  /** Reads the status artifacts for every calendar id, keyed by id. */
  public static Map<String, StatusData> readAll(
      Path blessedDir, Path sourcesDir, List<String> calendarIds) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, StatusData> byId = new LinkedHashMap<>();
    for (String id : calendarIds) {
      byId.put(id, read(mapper, blessedDir, sourcesDir, id));
    }
    return byId;
  }

  private static StatusData read(ObjectMapper mapper, Path blessedDir, Path sourcesDir, String id)
      throws IOException {
    List<Reference> references = new ArrayList<>();
    Path crossValidationFile = blessedDir.resolve(id).resolve("cross_validation.json");
    if (Files.exists(crossValidationFile)) {
      JsonNode document = mapper.readTree(crossValidationFile.toFile());
      for (JsonNode result : document.path("results")) {
        references.add(
            new Reference(
                result.path("source").asText(),
                result.path("status").asText(),
                result.path("counts").path("allowlisted").asInt(0),
                result.path("counts").path("unexplained").asInt(0)));
      }
    }

    List<String> sourceIds = new ArrayList<>();
    Path readme = sourcesDir.resolve(id).resolve("README.md");
    if (Files.exists(readme)) {
      for (String line : Files.readAllLines(readme)) {
        Matcher matcher = SOURCE_ROW.matcher(line.strip());
        if (matcher.find()) {
          sourceIds.add(matcher.group(1));
        }
      }
    }
    return new StatusData(List.copyOf(references), List.copyOf(sourceIds));
  }
}
