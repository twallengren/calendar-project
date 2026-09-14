package com.bdc.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.cli.Main;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Exercises the contributor-preview flow end to end: a calendar generated locally with one closure
 * missing relative to a hand-built "blessed" baseline, rendered with {@code tools site
 * --blessed-dir <local> --compare-to <baseline>}.
 *
 * <p>Both directories are hand-built rather than produced by the real generator/resolver, because
 * the feature under test — {@link com.bdc.diff.CalendarDiffEngine} comparison plus the banner and
 * {@code changes/} rendering — only cares about the shape on disk (events.csv, metadata.json), not
 * how the events got there. The local directory deliberately has no {@code manifest.json}, matching
 * a bare {@code generate --include-specs} output.
 */
class SitePreviewTest {

  private static final String CALENDAR_ID = "US-TEST-PREVIEW";
  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path tempDir;

  @Test
  void previewBannerAndChangesPageReportTheRemovedClosureAsMajor() throws Exception {
    Path baseline = tempDir.resolve("baseline");
    Path local = tempDir.resolve("local");
    Path out = tempDir.resolve("site");

    writeCalendarDir(
        baseline.resolve(CALENDAR_ID),
        """
        date,type,description,key,source_module,observed_from,close_time,status
        2024-01-01,CLOSED,New Year's Day,new_years_day,module:new_years_day,,,CONFIRMED
        2024-07-04,CLOSED,Independence Day,independence_day,module:independence_day,,,CONFIRMED
        2024-12-25,CLOSED,Christmas Day,christmas_day,module:christmas_day,,,CONFIRMED
        """);
    writeManifest(baseline);

    // The local, contributor-generated output: same range, but a delta removed Christmas Day.
    writeCalendarDir(
        local.resolve(CALENDAR_ID),
        """
        date,type,description,key,source_module,observed_from,close_time,status
        2024-01-01,CLOSED,New Year's Day,new_years_day,module:new_years_day,,,CONFIRMED
        2024-07-04,CLOSED,Independence Day,independence_day,module:independence_day,,,CONFIRMED
        """);
    // Deliberately no manifest.json under `local` — the site command must synthesise one.
    assertTrue(
        Files.notExists(local.resolve("manifest.json")),
        "test setup: local dir must start without a manifest.json");

    int exitCode =
        new CommandLine(new Main())
            .execute(
                "site",
                "--blessed-dir",
                local.toString(),
                "--compare-to",
                baseline.toString(),
                "--out",
                out.toString(),
                "--sources-dir",
                tempDir.resolve("no-sources").toString(),
                "--release-history-dir",
                tempDir.resolve("no-history").toString(),
                "--generated-at",
                "2024-06-01T00:00:00Z");
    assertEquals(0, exitCode, "site command should succeed");

    // Local dir now carries a synthesised manifest.json.
    assertTrue(Files.exists(local.resolve("manifest.json")), "manifest.json should be synthesised");

    String yearPage = read(out.resolve(CALENDAR_ID).resolve("2024").resolve("index.html"));
    assertTrue(
        yearPage.contains("Changes vs blessed: 0 added, 1 removed, 0 modified; severity MAJOR"),
        "year page should carry the diff banner with the right counts and severity");

    String changesPage = read(out.resolve("changes").resolve("index.html"));
    assertTrue(changesPage.contains("2024-12-25"), "changes page should mention the removed date");
    assertTrue(
        changesPage.contains("Christmas Day"), "changes page should mention the removed holiday");
    assertTrue(changesPage.contains("MAJOR"), "changes page should report MAJOR severity");
    assertTrue(changesPage.contains(CALENDAR_ID), "changes page should name the calendar");
  }

  private void writeCalendarDir(Path calDir, String csv) throws IOException {
    Files.createDirectories(calDir);
    Files.writeString(calDir.resolve("events.csv"), csv);

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("calendar_id", CALENDAR_ID);
    metadata.put("calendar_name", "US Test Preview");
    metadata.put("kind", "market");
    metadata.put("generated_at", "2024-06-01T00:00:00Z");
    metadata.put("range_start", "2024-01-01");
    metadata.put("range_end", "2024-12-31");
    metadata.put("timezone", "America/New_York");
    Map<String, String> coverage = new LinkedHashMap<>();
    coverage.put("from", "2024-01-01");
    coverage.put("to", "2024-12-31");
    coverage.put("verified_through", "2024-12-31");
    metadata.put("coverage", coverage);
    long closed = csv.lines().filter(l -> l.contains(",CLOSED,")).count();
    metadata.put("event_count", (int) closed);
    metadata.put("counts_by_type", Map.of("CLOSED", closed));
    metadata.put("counts_by_status", Map.of("CONFIRMED", closed));
    Files.writeString(calDir.resolve("metadata.json"), JSON.writeValueAsString(metadata));
  }

  private void writeManifest(Path dir) throws IOException {
    Map<String, Object> calendars = new LinkedHashMap<>();
    Map<String, Object> calInfo = new LinkedHashMap<>();
    calInfo.put("kind", "market");
    calInfo.put("range_start", "2024-01-01");
    calInfo.put("range_end", "2024-12-31");
    calInfo.put("event_count", 3);
    calInfo.put("checksum", "sha256:deadbeef");
    calendars.put(CALENDAR_ID, calInfo);

    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("schema_version", "1.0");
    manifest.put("blessed_at", "2024-01-01T00:00:00Z");
    manifest.put("blessed_by", "test");
    manifest.put("calendars", calendars);
    Map<String, String> releaseVersion = new LinkedHashMap<>();
    releaseVersion.put("semantic", "1.0.0");
    releaseVersion.put("git_sha", "abc123");
    releaseVersion.put("generation_date", "2024-01-01");
    manifest.put("release_version", releaseVersion);

    Files.createDirectories(dir);
    Files.writeString(dir.resolve("manifest.json"), JSON.writeValueAsString(manifest));
  }

  private static String read(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8);
  }
}
