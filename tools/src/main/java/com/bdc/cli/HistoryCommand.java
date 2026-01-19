package com.bdc.cli;

import com.bdc.artifact.ArtifactStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "history",
    description = "View history of stored artifacts"
)
public class HistoryCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Type of artifact: 'resolved' or 'generated'")
    private String artifactType;

    @Parameters(index = "1", description = "The calendar ID")
    private String calendarId;

    @Option(names = {"--valid-from"}, description = "Valid from date (for generated artifacts)")
    private LocalDate validFrom;

    @Option(names = {"--valid-to"}, description = "Valid to date (for generated artifacts)")
    private LocalDate validTo;

    @Option(names = {"--valid-range"}, description = "Year shortcut for valid range (e.g., 2024)")
    private Integer validRangeYear;

    @Option(names = {"--artifacts-dir"}, description = "Artifacts directory", defaultValue = "artifacts")
    private Path artifactsDir;

    @Option(names = {"--limit", "-n"}, description = "Limit number of results", defaultValue = "10")
    private int limit;

    @Override
    public Integer call() {
        try {
            ArtifactStore store = new ArtifactStore(artifactsDir);

            switch (artifactType.toLowerCase()) {
                case "resolved" -> showResolvedHistory(store);
                case "generated" -> showGeneratedHistory(store);
                default -> {
                    System.err.println("Unknown artifact type: " + artifactType);
                    System.err.println("Use 'resolved' or 'generated'");
                    return 1;
                }
            }

            return 0;
        } catch (Exception e) {
            System.err.println("History query failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private void showResolvedHistory(ArtifactStore store) throws Exception {
        List<String> versions = store.listResolvedVersions(calendarId);

        if (versions.isEmpty()) {
            System.out.println("No resolved artifacts found for " + calendarId);
            return;
        }

        System.out.println("Resolved spec history for " + calendarId + ":");
        System.out.println("  (showing " + Math.min(limit, versions.size()) + " of " + versions.size() + " versions)");
        System.out.println();

        int count = 0;
        for (String version : versions) {
            if (count >= limit) break;
            System.out.println("  " + version);
            count++;
        }

        System.out.println();
        System.out.println("Path: " + artifactsDir.resolve("resolved").resolve(calendarId));
    }

    private void showGeneratedHistory(ArtifactStore store) throws Exception {
        LocalDate from = validFrom;
        LocalDate to = validTo;

        // Handle year shortcut
        if (validRangeYear != null) {
            from = LocalDate.of(validRangeYear, 1, 1);
            to = LocalDate.of(validRangeYear, 12, 31);
        }

        if (from == null || to == null) {
            // Show all valid ranges for this calendar
            Path calDir = artifactsDir.resolve("generated").resolve(calendarId);
            if (!calDir.toFile().exists()) {
                System.out.println("No generated artifacts found for " + calendarId);
                return;
            }

            System.out.println("Generated artifact ranges for " + calendarId + ":");
            System.out.println();

            try (var ranges = java.nio.file.Files.list(calDir)) {
                ranges.filter(java.nio.file.Files::isDirectory)
                    .sorted()
                    .forEach(rangeDir -> {
                        String rangeName = rangeDir.getFileName().toString();
                        try (var versions = java.nio.file.Files.list(rangeDir)) {
                            long count = versions.filter(java.nio.file.Files::isDirectory).count();
                            System.out.println("  " + rangeName + " (" + count + " versions)");
                        } catch (Exception e) {
                            System.out.println("  " + rangeName + " (error reading)");
                        }
                    });
            }

            System.out.println();
            System.out.println("Use --valid-from and --valid-to (or --valid-range) to see versions for a specific range");
            return;
        }

        List<String> versions = store.listGeneratedVersions(calendarId, from, to);

        if (versions.isEmpty()) {
            System.out.println("No generated artifacts found for " + calendarId +
                " (range: " + from + " to " + to + ")");
            return;
        }

        System.out.println("Generated artifact history for " + calendarId +
            " (range: " + from + " to " + to + "):");
        System.out.println("  (showing " + Math.min(limit, versions.size()) + " of " + versions.size() + " versions)");
        System.out.println();

        int count = 0;
        for (String version : versions) {
            if (count >= limit) break;
            System.out.println("  " + version);
            count++;
        }

        System.out.println();
        String validRange = from.toString() + "_" + to.toString();
        System.out.println("Path: " + artifactsDir.resolve("generated").resolve(calendarId).resolve(validRange));
    }
}
