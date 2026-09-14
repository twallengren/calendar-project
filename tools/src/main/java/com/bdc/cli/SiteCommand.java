package com.bdc.cli;

import com.bdc.artifact.ReleaseHistoryStore;
import com.bdc.diff.BlessedArtifactLoader;
import com.bdc.site.ApiEmitter;
import com.bdc.site.ChangelogBuilder;
import com.bdc.site.ChangelogHtmlRenderer;
import com.bdc.site.ChangelogJsonEmitter;
import com.bdc.site.DeterministicChangelog;
import com.bdc.site.SiteContext;
import com.bdc.site.SiteGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Builds the published static site: the {@code /v1/} JSON API, the changelog (JSON and HTML) and
 * the browsable HTML pages, in that order — the HTML renders from the JSON the API emitter just
 * wrote, so the published contract is exercised by the site itself.
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
      description = "Path to blessed artifacts directory",
      defaultValue = "blessed")
  private Path blessedDir;

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
          "Include calendars whose kind is not 'market' (e.g. base/foundational calendars) in"
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

      ApiEmitter emitter = new ApiEmitter(blessedDir, releaseHistoryDir, outDir, includeBase, at);
      emitter.emit();
      System.out.println("Generated JSON API v1: " + outDir.resolve("v1"));
      if (apiOnly) {
        return 0;
      }

      SiteContext context = SiteGenerator.readContext(outDir, baseUrl, siteName, repoUrl, at);

      int releases = writeChangelog(at, context);
      System.out.println("Generated changelog for " + releases + " release(s)");

      SiteGenerator.Result result =
          new SiteGenerator(context, outDir, blessedDir, sourcesDir).generate();
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
}
