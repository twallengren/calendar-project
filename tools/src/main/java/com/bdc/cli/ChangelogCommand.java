package com.bdc.cli;

import com.bdc.artifact.ReleaseHistoryStore;
import com.bdc.diff.BlessedArtifactLoader;
import com.bdc.site.ChangelogBuilder;
import com.bdc.site.ChangelogHtmlRenderer;
import com.bdc.site.ChangelogJsonEmitter;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "changelog",
    description = "Generate a changelog site from blessed and release-history artifacts")
public class ChangelogCommand implements Callable<Integer> {

  @Option(
      names = {"--blessed-dir", "-b"},
      description = "Path to blessed artifacts directory",
      defaultValue = "blessed")
  private Path blessedDir;

  @Option(
      names = {"--release-history-dir", "-r"},
      description = "Path to release-history directory",
      defaultValue = "release-history")
  private Path releaseHistoryDir;

  @Option(
      names = {"--out", "-o"},
      description = "Output site directory",
      defaultValue = "site")
  private Path outDir;

  @Option(
      names = {"--format", "-f"},
      description = "Output format: json, html, or both",
      defaultValue = "both")
  private String format;

  @Override
  public Integer call() {
    try {
      BlessedArtifactLoader manifestLoader = new BlessedArtifactLoader();
      BlessedArtifactLoader.BlessedManifest manifest = manifestLoader.loadManifest(blessedDir);
      Set<String> calendarIds = manifest.calendars().keySet();

      ReleaseHistoryStore store = new ReleaseHistoryStore(releaseHistoryDir, blessedDir);
      ChangelogBuilder builder = new ChangelogBuilder();
      ChangelogBuilder.Changelog changelog = builder.build(store, calendarIds);

      boolean wantsJson = "json".equalsIgnoreCase(format) || "both".equalsIgnoreCase(format);
      boolean wantsHtml = "html".equalsIgnoreCase(format) || "both".equalsIgnoreCase(format);
      if (!wantsJson && !wantsHtml) {
        System.err.println("Unknown format: " + format + " (expected json, html, or both)");
        return 2;
      }

      if (wantsJson) {
        new ChangelogJsonEmitter().write(changelog, outDir);
      }
      if (wantsHtml) {
        new ChangelogHtmlRenderer().write(changelog, outDir);
      }

      System.out.println(
          "Wrote changelog for " + changelog.releases().size() + " release(s) to " + outDir);
      return 0;
    } catch (Exception e) {
      System.err.println("changelog failed: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }
}
