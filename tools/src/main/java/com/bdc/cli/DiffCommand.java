package com.bdc.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "diff", description = "Compare two artifact versions")
public class DiffCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Type of artifact: 'resolved' or 'generated'")
  private String artifactType;

  @Parameters(index = "1", description = "The calendar ID")
  private String calendarId;

  @Option(
      names = {"--tx1"},
      description = "First transaction timestamp",
      required = true)
  private String tx1;

  @Option(
      names = {"--tx2"},
      description = "Second transaction timestamp",
      required = true)
  private String tx2;

  @Option(
      names = {"--valid-from"},
      description = "Valid from date (for generated artifacts)")
  private LocalDate validFrom;

  @Option(
      names = {"--valid-to"},
      description = "Valid to date (for generated artifacts)")
  private LocalDate validTo;

  @Option(
      names = {"--valid-range"},
      description = "Year shortcut for valid range (e.g., 2024)")
  private Integer validRangeYear;

  @Option(
      names = {"--artifacts-dir"},
      description = "Artifacts directory",
      defaultValue = "artifacts")
  private Path artifactsDir;

  @Override
  public Integer call() {
    try {
      return switch (artifactType.toLowerCase()) {
        case "resolved" -> diffResolved();
        case "generated" -> diffGenerated();
        default -> {
          System.err.println("Unknown artifact type: " + artifactType);
          System.err.println("Use 'resolved' or 'generated'");
          yield 1;
        }
      };
    } catch (Exception e) {
      System.err.println("Diff failed: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }

  private int diffResolved() throws IOException {
    Path path1 = artifactsDir.resolve("resolved").resolve(calendarId).resolve(tx1 + ".yaml");
    Path path2 = artifactsDir.resolve("resolved").resolve(calendarId).resolve(tx2 + ".yaml");

    if (!Files.exists(path1)) {
      System.err.println("First artifact not found: " + path1);
      return 1;
    }
    if (!Files.exists(path2)) {
      System.err.println("Second artifact not found: " + path2);
      return 1;
    }

    System.out.println("Comparing resolved specs for " + calendarId);
    System.out.println("  Version 1: " + tx1);
    System.out.println("  Version 2: " + tx2);
    System.out.println();

    String content1 = Files.readString(path1);
    String content2 = Files.readString(path2);

    if (content1.equals(content2)) {
      System.out.println("No differences found.");
    } else {
      showLineDiff(content1, content2);
    }
    return 0;
  }

  private int diffGenerated() throws IOException {
    LocalDate from = validFrom;
    LocalDate to = validTo;

    if (validRangeYear != null) {
      from = LocalDate.of(validRangeYear, 1, 1);
      to = LocalDate.of(validRangeYear, 12, 31);
    }

    if (from == null || to == null) {
      System.err.println(
          "Error: --valid-from and --valid-to (or --valid-range) are required for generated artifacts");
      return 1;
    }

    String validRange = from.toString() + "_" + to.toString();
    Path basePath = artifactsDir.resolve("generated").resolve(calendarId).resolve(validRange);

    Path path1 = basePath.resolve(tx1).resolve("events.csv");
    Path path2 = basePath.resolve(tx2).resolve("events.csv");

    if (!Files.exists(path1)) {
      System.err.println("First artifact not found: " + path1);
      return 1;
    }
    if (!Files.exists(path2)) {
      System.err.println("Second artifact not found: " + path2);
      return 1;
    }

    System.out.println("Comparing generated events for " + calendarId);
    System.out.println("  Valid range: " + from + " to " + to);
    System.out.println("  Version 1: " + tx1);
    System.out.println("  Version 2: " + tx2);
    System.out.println();

    List<String> lines1 = Files.readAllLines(path1);
    List<String> lines2 = Files.readAllLines(path2);

    // Skip header for comparison
    Set<String> events1 = new HashSet<>(lines1.subList(1, lines1.size()));
    Set<String> events2 = new HashSet<>(lines2.subList(1, lines2.size()));

    Set<String> added = new HashSet<>(events2);
    added.removeAll(events1);

    Set<String> removed = new HashSet<>(events1);
    removed.removeAll(events2);

    if (added.isEmpty() && removed.isEmpty()) {
      System.out.println("No differences found.");
    } else {
      System.out.println("Summary:");
      System.out.println("  Events in version 1: " + events1.size());
      System.out.println("  Events in version 2: " + events2.size());
      System.out.println("  Added: " + added.size());
      System.out.println("  Removed: " + removed.size());
      System.out.println();

      if (!removed.isEmpty()) {
        System.out.println("Removed events (in v1 but not v2):");
        removed.stream().sorted().forEach(e -> System.out.println("  - " + e));
        System.out.println();
      }

      if (!added.isEmpty()) {
        System.out.println("Added events (in v2 but not v1):");
        added.stream().sorted().forEach(e -> System.out.println("  + " + e));
      }
    }
    return 0;
  }

  private void showLineDiff(String content1, String content2) {
    String[] lines1 = content1.split("\n");
    String[] lines2 = content2.split("\n");

    System.out.println("Line-by-line differences:");
    System.out.println();

    int maxLen = Math.max(lines1.length, lines2.length);
    int diffCount = 0;

    for (int i = 0; i < maxLen; i++) {
      String line1 = i < lines1.length ? lines1[i] : "";
      String line2 = i < lines2.length ? lines2[i] : "";

      if (!line1.equals(line2)) {
        diffCount++;
        if (i < lines1.length && i >= lines2.length) {
          System.out.println("- Line " + (i + 1) + ": " + line1);
        } else if (i >= lines1.length && i < lines2.length) {
          System.out.println("+ Line " + (i + 1) + ": " + line2);
        } else {
          System.out.println("~ Line " + (i + 1) + ":");
          System.out.println("  - " + line1);
          System.out.println("  + " + line2);
        }
      }
    }

    System.out.println();
    System.out.println("Total: " + diffCount + " line(s) different");
  }
}
