package com.bdc.cli;

import com.bdc.site.ApiEmitter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "site",
    description =
        "Generate the static site (JSON API and, eventually, HTML) from blessed artifacts")
public class SiteCommand implements Callable<Integer> {

  @Option(
      names = {"--api-only"},
      description = "Generate only the /v1/ JSON API and .ics files; the only supported mode today")
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
      names = {"--out", "-o"},
      description = "Output directory for the site",
      defaultValue = "site")
  private Path outDir;

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
    if (!apiOnly) {
      System.err.println(
          "Error: --api-only is the only supported mode today; pass --api-only to generate the"
              + " /v1/ JSON API");
      return 1;
    }
    try {
      Instant at = generatedAt != null ? generatedAt : Instant.now();
      ApiEmitter emitter = new ApiEmitter(blessedDir, releaseHistoryDir, outDir, includeBase, at);
      emitter.emit();
      System.out.println("Generated JSON API v1 site: " + outDir.resolve("v1"));
      return 0;
    } catch (Exception e) {
      System.err.println("Site generation failed: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }
}
