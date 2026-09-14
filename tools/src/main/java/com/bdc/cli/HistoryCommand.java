package com.bdc.cli;

import com.bdc.artifact.ReleaseHistoryStore;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "history", description = "View history of published calendar releases")
public class HistoryCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Type of history to show: 'releases'")
  private String artifactType;

  @Parameters(index = "1", description = "The calendar ID")
  private String calendarId;

  @Option(
      names = {"--blessed-dir"},
      description = "Blessed artifacts directory (for 'releases')",
      defaultValue = "blessed")
  private Path blessedDir;

  @Option(
      names = {"--release-history-dir"},
      description = "Release history directory (for 'releases')",
      defaultValue = "release-history")
  private Path releaseHistoryDir;

  @Option(
      names = {"--limit", "-n"},
      description = "Limit number of results",
      defaultValue = "10")
  private int limit;

  @Override
  public Integer call() {
    try {
      if (!"releases".equalsIgnoreCase(artifactType)) {
        System.err.println("Unknown artifact type: " + artifactType);
        System.err.println("Use 'releases'");
        return 1;
      }

      showReleases();
      return 0;
    } catch (Exception e) {
      System.err.println("History query failed: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }

  private void showReleases() throws Exception {
    ReleaseHistoryStore store = new ReleaseHistoryStore(releaseHistoryDir, blessedDir);
    List<ReleaseHistoryStore.Snapshot> snapshots = store.list(calendarId);
    if (snapshots.isEmpty()) {
      System.out.println("No published releases found for " + calendarId);
      return;
    }
    System.out.println("Published releases for " + calendarId + " (newest first):");
    System.out.println(
        "  (showing "
            + Math.min(limit, snapshots.size())
            + " of "
            + snapshots.size()
            + " versions)");
    System.out.println();
    int count = 0;
    for (ReleaseHistoryStore.Snapshot s : snapshots) {
      if (count >= limit) break;
      String range;
      try {
        var r = store.range(s);
        range = r.start() + " to " + r.end();
      } catch (Exception e) {
        range = "range unknown";
      }
      System.out.println(
          "  v"
              + s.version()
              + "  "
              + s.archivedAt()
              + "  "
              + s.gitSha()
              + "  "
              + range
              + (s.isBlessed() ? "  (blessed)" : ""));
      count++;
    }
    System.out.println();
    System.out.println("Query a release with: query " + calendarId + " --as-of v<version>");
  }
}
