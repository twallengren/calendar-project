package com.bdc.cli;

import com.bdc.artifact.ReleaseHistoryStore;
import com.bdc.diff.BlessedArtifactLoader;
import com.bdc.diff.CalendarDiff;
import com.bdc.diff.CalendarDiffEngine;
import com.bdc.diff.DiffSeverity;
import com.bdc.diff.EventDiff;
import com.bdc.model.Event;
import com.bdc.site.ApiEmitter;
import com.bdc.site.ApiV2Emitter;
import com.bdc.site.ChangelogBuilder;
import com.bdc.site.ChangelogHtmlRenderer;
import com.bdc.site.ChangelogJsonEmitter;
import com.bdc.site.DeterministicChangelog;
import com.bdc.site.SiteContext;
import com.bdc.site.SiteGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Builds the published static site: the {@code /v1/} JSON API, the changelog (JSON and HTML) and
 * the browsable HTML pages, in that order — the HTML renders from the JSON the API emitter just
 * wrote, so the published contract is exercised by the site itself.
 *
 * <p>{@code --blessed-dir} does not have to be the published {@code blessed/} directory: pointed at
 * a local {@code generate --include-specs} output (which has the same per-calendar layout — {@code
 * <ID>/events.csv}, {@code metadata.json}, {@code resolved.yaml} — but usually only some calendars
 * and no top-level {@code manifest.json}) this becomes a contributor preview. A missing {@code
 * manifest.json} is synthesised from whichever calendar directories are present rather than treated
 * as an error. Passing {@code --compare-to <dir>} additionally diffs each locally generated
 * calendar against that directory (normally {@code blessed/}) and renders a "Changes vs blessed"
 * banner on every affected year and date page, plus a {@code changes/index.html} listing every row.
 */
@Command(
    name = "site",
    description = "Generate the static site (JSON API, changelog and HTML pages) from artifacts")
public class SiteCommand implements Callable<Integer> {

  @Option(
      names = {"--api-only"},
      description = "Generate only the /v1/ JSON API and .ics files, skipping changelog and HTML")
  private boolean apiOnly;

  @Option(
      names = {"--blessed-dir"},
      description =
          "Path to blessed artifacts directory, or a local `generate --include-specs` output for a"
              + " contributor preview",
      defaultValue = "blessed")
  private Path blessedDir;

  @Option(
      names = {"--compare-to"},
      description =
          "Diff each calendar under --blessed-dir against its counterpart in this directory"
              + " (normally blessed/) and render a \"Changes vs blessed\" banner on the affected"
              + " year and date pages, plus changes/index.html")
  private Path compareTo;

  @Option(
      names = {"--release-history-dir"},
      description = "Path to release history directory",
      defaultValue = "release-history")
  private Path releaseHistoryDir;

  @Option(
      names = {"--sources-dir"},
      description = "Path to the source register directory",
      defaultValue = "sources")
  private Path sourcesDir;

  @Option(
      names = {"--out", "-o"},
      description = "Output directory for the site",
      defaultValue = "site")
  private Path outDir;

  @Option(
      names = {"--base-url"},
      description =
          "Absolute URL the site will be served from, used for canonical links, the sitemap and"
              + " webcal:// subscribe links. Internal links stay relative either way.",
      defaultValue = "/")
  private String baseUrl;

  @Option(
      names = {"--site-name"},
      description = "Name shown in the masthead and on the home page",
      defaultValue = "Business Day Calendars")
  private String siteName;

  @Option(
      names = {"--repo-url"},
      description = "Repository URL, for source, CSV and contributing links",
      defaultValue = "https://github.com/twallengren/calendar-project")
  private String repoUrl;

  @Option(
      names = {"--include-base"},
      description =
          "Include base/foundational calendars alongside market and payment calendars in"
              + " the index and per-calendar output")
  private boolean includeBase;

  @Option(
      names = {"--generated-at"},
      description =
          "Timestamp to record as generated_at in index.json (ISO instant); defaults to now. Pass"
              + " a fixed value for reproducible output")
  private Instant generatedAt;

  @Override
  public Integer call() {
    try {
      Instant at = generatedAt != null ? generatedAt : Instant.now();

      synthesizeManifestIfMissing(blessedDir, at);

      ApiEmitter emitter = new ApiEmitter(blessedDir, releaseHistoryDir, outDir, includeBase, at);
      emitter.emit();
      new ApiV2Emitter().emit(blessedDir, releaseHistoryDir, outDir, includeBase, at);
      System.out.println("Generated JSON APIs v1 and v2: " + outDir.resolve("v1"));
      if (apiOnly) {
        return 0;
      }

      SiteContext context = SiteGenerator.readContext(outDir, baseUrl, siteName, repoUrl, at);

      int releases = writeChangelog(at, context);
      System.out.println("Generated changelog for " + releases + " release(s)");

      Map<String, CalendarDiff> diffs = compareTo != null ? computeDiffs(at) : null;
      if (diffs != null) {
        System.out.println(
            "Compared "
                + diffs.size()
                + " calendar(s) against "
                + compareTo
                + "; overall severity "
                + new CalendarDiffEngine().aggregateSeverity(diffs.values()));
      }

      SiteGenerator.Result result =
          new SiteGenerator(context, outDir, blessedDir, sourcesDir, diffs).generate();
      System.out.println(
          "Generated "
              + result.pages()
              + " HTML pages for "
              + result.calendars()
              + " calendar(s) in "
              + outDir);
      return 0;
    } catch (Exception e) {
      System.err.println("Site generation failed: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }

  /**
   * Writes {@code v1/changelog.json} and the changelog HTML into the same output directory.
   *
   * <p>The diff cutoff is taken from {@code generatedAt} rather than the wall clock so that a build
   * pinned with {@code --generated-at} is reproducible.
   */
  private int writeChangelog(Instant at, SiteContext context) throws Exception {
    BlessedArtifactLoader.BlessedManifest manifest =
        new BlessedArtifactLoader().loadManifest(blessedDir);
    Set<String> calendarIds = manifest.calendars().keySet();
    ReleaseHistoryStore store = new ReleaseHistoryStore(releaseHistoryDir, blessedDir);
    LocalDate cutoff = LocalDate.ofInstant(at, ZoneOffset.UTC);
    ChangelogBuilder.Changelog changelog =
        DeterministicChangelog.sorted(new ChangelogBuilder(cutoff).build(store, calendarIds));
    new ChangelogJsonEmitter().write(changelog, outDir);
    new ChangelogHtmlRenderer(context).write(changelog, outDir);
    return changelog.releases().size();
  }

  /**
   * Writes a {@code manifest.json} into {@code dir} when it has none, by scanning its immediate
   * subdirectories for a {@code metadata.json} — this is what turns a bare {@code generate
   * --include-specs} output directory into something {@link ApiEmitter} and {@link
   * BlessedArtifactLoader} (which both require a manifest) can read. A directory that already has a
   * manifest (the real {@code blessed/}) is never touched.
   */
  private void synthesizeManifestIfMissing(Path dir, Instant at) throws IOException {
    Path manifestPath = dir.resolve("manifest.json");
    if (Files.exists(manifestPath)) {
      return;
    }
    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    Map<String, Object> calendars = new LinkedHashMap<>();
    if (Files.isDirectory(dir)) {
      List<Path> subdirs;
      try (var entries = Files.list(dir)) {
        subdirs = entries.filter(Files::isDirectory).sorted().toList();
      }
      for (Path calDir : subdirs) {
        Path metadataFile = calDir.resolve("metadata.json");
        if (!Files.exists(metadataFile)) {
          continue;
        }
        JsonNode metadata = mapper.readTree(metadataFile.toFile());
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("kind", metadata.path("kind").asText("market"));
        info.put("range_start", metadata.path("range_start").asText());
        info.put("range_end", metadata.path("range_end").asText());
        info.put("event_count", metadata.path("event_count").asInt(0));
        info.put("checksum", "");
        calendars.put(calDir.getFileName().toString(), info);
      }
    }

    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("schema_version", "1.0");
    manifest.put("blessed_at", at.toString());
    manifest.put("blessed_by", "contributor-preview");
    manifest.put("calendars", calendars);
    Map<String, String> releaseVersion = new LinkedHashMap<>();
    releaseVersion.put("semantic", "0.0.0-dev");
    releaseVersion.put("git_sha", "");
    releaseVersion.put("generation_date", LocalDate.ofInstant(at, ZoneOffset.UTC).toString());
    manifest.put("release_version", releaseVersion);

    Files.createDirectories(dir);
    Files.writeString(manifestPath, mapper.writeValueAsString(manifest));
  }

  /**
   * Diffs every calendar found under {@code --blessed-dir} against its counterpart under {@code
   * --compare-to}, the same way {@code ci-diff} diffs a freshly generated range against a blessed
   * baseline: both sides are restricted to the range the local calendar was actually generated over
   * (its own {@code metadata.json} range, read back via the manifest), so a preview built for a
   * narrow window — {@code generate --from 2024-01-01 --to 2027-12-31}, say — is not swamped by
   * "removed" rows for every blessed date outside that window. A calendar absent from the baseline
   * is treated as brand new (every row a MINOR addition — nothing published to break yet) rather
   * than compared date-by-date.
   */
  private Map<String, CalendarDiff> computeDiffs(Instant at) throws IOException {
    BlessedArtifactLoader loader = new BlessedArtifactLoader();
    CalendarDiffEngine engine = new CalendarDiffEngine();
    LocalDate cutoff = LocalDate.ofInstant(at, ZoneOffset.UTC);

    BlessedArtifactLoader.BlessedManifest localManifest = loader.loadManifest(blessedDir);
    BlessedArtifactLoader.BlessedManifest baseline = loader.loadManifest(compareTo);

    Map<String, CalendarDiff> diffs = new LinkedHashMap<>();
    for (String id : discoverCalendarIds(blessedDir)) {
      BlessedArtifactLoader.CalendarInfo localInfo = localManifest.calendars().get(id);
      LocalDate rangeStart = localInfo != null ? localInfo.rangeStart() : cutoff;
      LocalDate rangeEnd = localInfo != null ? localInfo.rangeEnd() : cutoff;

      List<Event> localEvents =
          inRange(loader.loadBlessedEvents(blessedDir, id), rangeStart, rangeEnd);

      boolean hasBaseline =
          baseline.calendars().containsKey(id)
              && Files.exists(compareTo.resolve(id).resolve("events.csv"));
      if (!hasBaseline) {
        List<EventDiff> additions =
            localEvents.stream()
                .map(e -> EventDiff.added(e.date(), e.type(), e.description(), e.key()))
                .toList();
        diffs.put(
            id,
            new CalendarDiff(
                id,
                DiffSeverity.MINOR,
                additions,
                List.of(),
                List.of(),
                cutoff,
                rangeStart,
                rangeEnd));
        continue;
      }

      List<Event> baselineEvents =
          inRange(loader.loadBlessedEvents(compareTo, id), rangeStart, rangeEnd);
      diffs.put(id, engine.compare(id, localEvents, baselineEvents, cutoff, rangeStart, rangeEnd));
    }
    return diffs;
  }

  private static List<Event> inRange(List<Event> events, LocalDate from, LocalDate to) {
    return events.stream().filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to)).toList();
  }

  /** Calendar ids present under {@code dir}: immediate subdirectories that have an events.csv. */
  private static List<String> discoverCalendarIds(Path dir) throws IOException {
    List<String> ids = new ArrayList<>();
    if (Files.isDirectory(dir)) {
      try (var entries = Files.list(dir)) {
        for (Path calDir : entries.filter(Files::isDirectory).sorted().toList()) {
          if (Files.exists(calDir.resolve("events.csv"))) {
            ids.add(calDir.getFileName().toString());
          }
        }
      }
    }
    return ids;
  }
}
