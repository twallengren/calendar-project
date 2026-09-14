package com.bdc.site;

import com.bdc.diff.CalendarDiff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the browsable HTML site from an already-emitted {@code /v1/} JSON API.
 *
 * <p>Run order matters and is enforced by {@code SiteCommand}: {@link ApiEmitter} writes {@code
 * v1/}, the changelog is written into the same directory, and then this class reads {@code v1/}
 * back and renders pages from it. Reading the published API rather than {@code blessed/} means the
 * site is the API's first consumer: if the contract in {@code spec/SPEC.md#json-api-v1} drifts, a
 * page breaks and {@code SiteGeneratorTest} says so. The only inputs from outside {@code v1/} are
 * the status artifacts the API does not publish (see {@link StatusData}).
 *
 * <p>Output is a function of its inputs plus {@code generatedAt}: no timestamps of convenience, no
 * hash-keyed filenames, no iteration over unordered maps. Two runs with the same {@code
 * --generated-at} produce byte-identical trees.
 */
public class SiteGenerator {

  /** What was written, for the CLI to report. */
  public record Result(int calendars, int pages) {}

  private static final String[] ASSETS = {"styles.css", "site.js"};

  private final SiteContext context;
  private final Path siteDir;
  private final Path blessedDir;
  private final Path sourcesDir;
  private final Map<String, CalendarDiff> diffs;

  public SiteGenerator(SiteContext context, Path siteDir, Path blessedDir, Path sourcesDir) {
    this(context, siteDir, blessedDir, sourcesDir, null);
  }

  /**
   * @param diffs per-calendar diffs against a {@code --compare-to} baseline, or null when no
   *     comparison was requested (the default {@code site} run against {@code blessed/} alone). A
   *     non-null map — even an empty one — turns on the "Changes vs blessed" banners and writes
   *     {@code changes/index.html}; null skips both entirely so ordinary site generation is
   *     unaffected.
   */
  public SiteGenerator(
      SiteContext context,
      Path siteDir,
      Path blessedDir,
      Path sourcesDir,
      Map<String, CalendarDiff> diffs) {
    this.context = context;
    this.siteDir = siteDir;
    this.blessedDir = blessedDir;
    this.sourcesDir = sourcesDir;
    this.diffs = diffs;
  }

  /**
   * Builds the shared context, taking the release identity from the published {@code v1/index.json}
   * so that the site, the changelog and the API all name the same release.
   */
  public static SiteContext readContext(
      Path siteDir, String baseUrl, String siteName, String repoUrl, Instant generatedAt)
      throws IOException {
    JsonNode index =
        new ObjectMapper().readTree(siteDir.resolve("v1").resolve("index.json").toFile());
    JsonNode release = index.path("release");
    return new SiteContext(
        baseUrl,
        siteName,
        repoUrl,
        release.path("semantic").asText("unknown"),
        release.path("git_sha").asText(""),
        release.path("generation_date").asText(""),
        generatedAt);
  }

  public Result generate() throws IOException {
    List<CalendarData> calendars = CalendarData.readAll(siteDir);
    List<String> ids = calendars.stream().map(CalendarData::id).toList();
    Map<String, StatusData> status = StatusData.readAll(blessedDir, sourcesDir, ids);

    copyAssets();

    Map<String, CalendarDiff> pageDiffs = diffs != null ? diffs : Map.of();

    PageLayout layout = new PageLayout(context);
    YearGridRenderer gridRenderer = new YearGridRenderer();
    HomePageRenderer homeRenderer = new HomePageRenderer(context, layout);
    MarketPageRenderer marketRenderer = new MarketPageRenderer(context, layout, gridRenderer);
    YearPageRenderer yearRenderer = new YearPageRenderer(context, layout, gridRenderer, pageDiffs);
    DatePageRenderer dateRenderer = new DatePageRenderer(layout, pageDiffs);
    ChangesRenderer changesRenderer = new ChangesRenderer(layout);

    homeRenderer.write(calendars, status, siteDir);
    for (CalendarData calendar : calendars) {
      marketRenderer.write(calendar, status.getOrDefault(calendar.id(), StatusData.EMPTY), siteDir);
      yearRenderer.writeAll(calendar, siteDir);
      dateRenderer.writeAll(calendar, siteDir);
    }

    if (diffs != null) {
      Map<String, CalendarData> calendarsById = new LinkedHashMap<>();
      for (CalendarData calendar : calendars) {
        calendarsById.put(calendar.id(), calendar);
      }
      changesRenderer.write(diffs, calendarsById, siteDir);
    }

    List<String> allPages = new SitemapEmitter(context).write(siteDir);
    return new Result(calendars.size(), allPages.size());
  }

  /**
   * Copies {@code styles.css} and {@code site.js} out of the jar. They are hand-written files
   * shipped as-is — no bundler, no minifier, no fingerprinting — so that what is served is exactly
   * what is in the repository.
   */
  private void copyAssets() throws IOException {
    Files.createDirectories(siteDir);
    for (String asset : ASSETS) {
      String resource = "/site/" + asset;
      try (InputStream in = SiteGenerator.class.getResourceAsStream(resource)) {
        if (in == null) {
          throw new IOException("Missing bundled site asset: " + resource);
        }
        Files.write(siteDir.resolve(asset), in.readAllBytes());
      }
    }
  }
}
