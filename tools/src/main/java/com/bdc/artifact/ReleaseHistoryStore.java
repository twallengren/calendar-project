package com.bdc.artifact;

import com.bdc.chronology.DateRange;
import com.bdc.emitter.EventsCsvReader;
import com.bdc.model.Event;
import com.bdc.stream.CsvDateStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the published artifact history: {@code blessed/} (the current release) and {@code
 * release-history/<CAL>/<timestamp>_<sha>_v<version>/} (previous releases, written by the release
 * workflow).
 *
 * <p>Snapshots are selected by release version ({@code v11.0.0} or {@code 11.0.0}), by the literal
 * {@code blessed}, or by an ISO date/instant meaning "the release that was current at that moment"
 * (transaction-time as-of query).
 */
public class ReleaseHistoryStore {

  private static final Pattern SNAPSHOT_DIR =
      Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}Z)_([0-9a-f]+)_v(.+)$");
  private static final DateTimeFormatter DIR_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

  /** One published version of one calendar. */
  public record Snapshot(
      String calendarId, String id, Instant archivedAt, String gitSha, String version, Path dir) {
    public boolean isBlessed() {
      return "blessed".equals(id);
    }
  }

  private final Path releaseHistoryDir;
  private final Path blessedDir;
  private final ObjectMapper mapper = new ObjectMapper();

  public ReleaseHistoryStore(Path releaseHistoryDir, Path blessedDir) {
    this.releaseHistoryDir = releaseHistoryDir;
    this.blessedDir = blessedDir;
  }

  /** Snapshots of a calendar, newest first; the blessed release is included when present. */
  public List<Snapshot> list(String calendarId) throws IOException {
    List<Snapshot> snapshots = new ArrayList<>();
    Path calDir = releaseHistoryDir.resolve(calendarId);
    if (Files.isDirectory(calDir)) {
      try (var dirs = Files.list(calDir)) {
        for (Path dir : dirs.filter(Files::isDirectory).toList()) {
          Matcher m = SNAPSHOT_DIR.matcher(dir.getFileName().toString());
          if (!m.matches()) {
            continue;
          }
          Instant ts = Instant.from(DIR_TIMESTAMP.parse(m.group(1)));
          snapshots.add(
              new Snapshot(
                  calendarId, dir.getFileName().toString(), ts, m.group(2), m.group(3), dir));
        }
      }
    }
    blessedSnapshot(calendarId).ifPresent(snapshots::add);
    snapshots.sort(Comparator.comparing(Snapshot::archivedAt).reversed());
    return snapshots;
  }

  /** The current blessed release as a snapshot, if the calendar has one. */
  public Optional<Snapshot> blessedSnapshot(String calendarId) throws IOException {
    Path calDir = blessedDir.resolve(calendarId);
    Path manifest = blessedDir.resolve("manifest.json");
    if (!Files.isDirectory(calDir) || !Files.exists(manifest)) {
      return Optional.empty();
    }
    JsonNode root = mapper.readTree(manifest.toFile());
    if (!root.path("calendars").has(calendarId)) {
      return Optional.empty();
    }
    Instant blessedAt =
        root.hasNonNull("blessed_at")
            ? Instant.parse(root.path("blessed_at").asText())
            : Instant.EPOCH;
    String sha = root.path("release_version").path("git_sha").asText("unknown");
    String version = root.path("release_version").path("semantic").asText("unknown");
    return Optional.of(new Snapshot(calendarId, "blessed", blessedAt, sha, version, calDir));
  }

  /**
   * Resolves a selector to a snapshot.
   *
   * @param selector {@code blessed}, a version ({@code v10.1.0} / {@code 10.1.0}), a snapshot
   *     directory name, an ISO date (the release current at the end of that day, UTC) or an ISO
   *     instant
   */
  public Optional<Snapshot> resolve(String calendarId, String selector) throws IOException {
    List<Snapshot> snapshots = list(calendarId);
    if (selector == null
        || selector.equalsIgnoreCase("blessed")
        || selector.equalsIgnoreCase("latest")) {
      return snapshots.stream().filter(Snapshot::isBlessed).findFirst();
    }
    String version = selector.startsWith("v") ? selector.substring(1) : selector;
    for (Snapshot s : snapshots) {
      if (s.id().equals(selector) || s.version().equals(version)) {
        return Optional.of(s);
      }
    }
    Instant asOf = parseInstant(selector);
    if (asOf == null) {
      return Optional.empty();
    }
    // Snapshots are archived when they are superseded: a snapshot archived at time T was the
    // current release from the previous archive time up to T. The blessed release is current from
    // the last archive time onward.
    List<Snapshot> chronological = new ArrayList<>(snapshots);
    chronological.sort(Comparator.comparing(Snapshot::archivedAt));
    for (Snapshot s : chronological) {
      if (s.isBlessed()) {
        continue;
      }
      if (!asOf.isAfter(s.archivedAt())) {
        return Optional.of(s);
      }
    }
    return snapshots.stream().filter(Snapshot::isBlessed).findFirst();
  }

  private static Instant parseInstant(String selector) {
    try {
      return Instant.parse(selector);
    } catch (RuntimeException ignored) {
    }
    try {
      return LocalDate.parse(selector)
          .plusDays(1)
          .atStartOfDay()
          .toInstant(ZoneOffset.UTC)
          .minusMillis(1);
    } catch (RuntimeException ignored) {
    }
    return null;
  }

  public List<Event> loadEvents(Snapshot snapshot) throws IOException {
    Path csv = snapshot.dir().resolve("events.csv");
    if (!Files.exists(csv)) {
      throw new IOException("Snapshot has no events.csv: " + snapshot.dir());
    }
    return new EventsCsvReader().read(csv, "release:" + snapshot.id());
  }

  /** A queryable stream over the snapshot: its events, range and verified_through. */
  public CsvDateStream stream(Snapshot snapshot) throws IOException {
    return new CsvDateStream(
        snapshot.calendarId(),
        loadEvents(snapshot),
        range(snapshot),
        verifiedThrough(snapshot).orElse(null));
  }

  /** The {@code coverage.verified_through} recorded in the snapshot's metadata.json, if any. */
  public Optional<LocalDate> verifiedThrough(Snapshot snapshot) throws IOException {
    JsonNode verified = metadata(snapshot).path("coverage").path("verified_through");
    return verified.isMissingNode() || verified.isNull()
        ? Optional.empty()
        : Optional.of(LocalDate.parse(verified.asText()));
  }

  /** The date range the snapshot was generated for, from its metadata.json. */
  public DateRange range(Snapshot snapshot) throws IOException {
    JsonNode root = metadata(snapshot);
    return new DateRange(
        LocalDate.parse(root.path("range_start").asText()),
        LocalDate.parse(root.path("range_end").asText()));
  }

  private JsonNode metadata(Snapshot snapshot) throws IOException {
    Path metadata = snapshot.dir().resolve("metadata.json");
    if (!Files.exists(metadata)) {
      throw new IOException("Snapshot has no metadata.json: " + snapshot.dir());
    }
    return mapper.readTree(metadata.toFile());
  }
}
