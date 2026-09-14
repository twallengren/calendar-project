package com.bdc.site;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

  public SiteGenerator(SiteContext context, Path siteDir, Path blessedDir, Path sourcesDir) {
    this.context = context;
    this.siteDir = siteDir;
    this.blessedDir = blessedDir;
    this.sourcesDir = sourcesDir;
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
    SourceRegistry sources = SourceRegistry.read(sourcesDir, blessedDir, ids);
    List<CalendarData> markets = ComparePageRenderer.marketCalendars(calendars, siteDir);

    copyAssets();

    PageLayout layout = new PageLayout(context);
    YearGridRenderer gridRenderer = new YearGridRenderer();
    HomePageRenderer homeRenderer = new HomePageRenderer(context, layout);
    MarketPageRenderer marketRenderer =
        new MarketPageRenderer(context, layout, gridRenderer, sources, markets);
    YearPageRenderer yearRenderer = new YearPageRenderer(context, layout, gridRenderer);
    DatePageRenderer dateRenderer = new DatePageRenderer(layout);

    homeRenderer.write(calendars, status, siteDir);
    for (CalendarData calendar : calendars) {
      marketRenderer.write(calendar, status.getOrDefault(calendar.id(), StatusData.EMPTY), siteDir);
      yearRenderer.writeAll(calendar, siteDir);
      dateRenderer.writeAll(calendar, siteDir);
    }

    new ComparePageRenderer(context, layout).write(markets, siteDir);
    new SourcesPageRenderer(context, layout).write(sources, siteDir);

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
