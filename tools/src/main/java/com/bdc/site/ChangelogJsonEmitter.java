package com.bdc.site;

import com.bdc.diff.CalendarDiff;
import com.bdc.diff.EventDiff;
import com.bdc.site.ChangelogBuilder.Changelog;
import com.bdc.site.ChangelogBuilder.Release;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes the full changelog as {@code v1/changelog.json}: minified, releases newest first, with
 * complete (untruncated) added/removed/modified lists per calendar.
 */
public class ChangelogJsonEmitter {

  private final ObjectMapper mapper;

  public ChangelogJsonEmitter() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    // Deliberately no INDENT_OUTPUT: this file is written minified.
  }

  /** Writes {@code <outDir>/v1/changelog.json}, creating directories as needed. */
  public Path write(Changelog changelog, Path outDir) throws IOException {
    Path file = outDir.resolve("v1").resolve("changelog.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, toJson(changelog));
    return file;
  }

  public String toJson(Changelog changelog) {
    try {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("releases", changelog.releases().stream().map(this::releaseJson).toList());
      return mapper.writeValueAsString(root);
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize changelog", e);
    }
  }

  private Map<String, Object> releaseJson(Release release) {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("version", release.version());
    json.put("git_sha", release.gitSha());
    json.put("timestamp", release.timestamp().toString());
    json.put("blessed", release.blessed());
    json.put("severity", release.severity().name());
    json.put(
        "counts",
        counts(release.totalAdditions(), release.totalRemovals(), release.totalModifications()));

    Map<String, Object> calendars = new LinkedHashMap<>();
    for (var entry : release.calendars().entrySet()) {
      calendars.put(entry.getKey(), calendarJson(entry.getValue()));
    }
    json.put("calendars", calendars);
    return json;
  }

  private Map<String, Object> calendarJson(CalendarDiff diff) {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("severity", diff.severity().name());
    json.put(
        "counts",
        counts(diff.additions().size(), diff.removals().size(), diff.modifications().size()));
    json.put("additions", eventDiffs(diff.additions()));
    json.put("removals", eventDiffs(diff.removals()));
    json.put("modifications", eventDiffs(diff.modifications()));
    return json;
  }

  private Map<String, Object> counts(int additions, int removals, int modifications) {
    Map<String, Object> counts = new LinkedHashMap<>();
    counts.put("additions", additions);
    counts.put("removals", removals);
    counts.put("modifications", modifications);
    return counts;
  }

  private List<Map<String, Object>> eventDiffs(List<EventDiff> diffs) {
    return diffs.stream().map(this::eventDiffJson).collect(Collectors.toList());
  }

  private Map<String, Object> eventDiffJson(EventDiff d) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("date", d.date().toString());
    if (d.key() != null) map.put("key", d.key());
    if (d.oldType() != null) map.put("old_type", d.oldType().name());
    if (d.newType() != null) map.put("new_type", d.newType().name());
    if (d.oldDescription() != null) map.put("old_description", d.oldDescription());
    if (d.newDescription() != null) map.put("new_description", d.newDescription());
    return map;
  }
}
