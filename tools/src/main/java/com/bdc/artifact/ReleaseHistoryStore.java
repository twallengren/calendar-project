package com.bdc.artifact;

import com.bdc.chronology.DateRange;
import com.bdc.emitter.EventsCsvReader;
import com.bdc.model.Event;
import com.bdc.stream.CsvDateStream;
import com.bdc.trust.CoverageInterval;
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
      String calendarId,
      String id,
      Instant archivedAt,
      String gitSha,
      String version,
      Path dir,
      Optional<Instant> validFrom,
      Optional<Instant> validUntil) {
    public Snapshot {
      validFrom = validFrom == null ? Optional.empty() : validFrom;
      validUntil = validUntil == null ? Optional.empty() : validUntil;
      if (validFrom.isPresent()
          && validUntil.isPresent()
          && !validFrom.orElseThrow().isBefore(validUntil.orElseThrow())) {
        throw new IllegalArgumentException("snapshot valid_from must be before valid_until");
      }
    }

    public boolean isBlessed() {
      return "blessed".equals(id);
    }

    /** Whether this publication is evidenced as current at {@code instant}. */
    public boolean contains(Instant instant) {
      return validFrom.map(from -> !instant.isBefore(from)).orElse(false)
          && validUntil.map(until -> instant.isBefore(until)).orElse(true);
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
    List<Publication> publications = publications();
    Path calDir = releaseHistoryDir.resolve(calendarId);
    if (Files.isDirectory(calDir)) {
      try (var dirs = Files.list(calDir)) {
        for (Path dir : dirs.filter(Files::isDirectory).toList()) {
          Matcher m = SNAPSHOT_DIR.matcher(dir.getFileName().toString());
          if (!m.matches()) {
            continue;
          }
          Instant ts = Instant.from(DIR_TIMESTAMP.parse(m.group(1)));
          Optional<Instant> validFrom = publicationInstant(dir, "valid_from", "published_at");
          Optional<Instant> validUntil =
              publicationInstant(dir, "valid_until").or(() -> Optional.of(ts));
          snapshots.add(
              withPublication(
                  new Snapshot(
                      calendarId,
                      dir.getFileName().toString(),
                      ts,
                      m.group(2),
                      m.group(3),
                      dir,
                      validFrom,
                      validUntil),
                  publications));
        }
      }
    }
    blessedSnapshot(calendarId).ifPresent(snapshots::add);
    Set<String> boundVersions = new HashSet<>();
    for (Snapshot snapshot : snapshots) {
      if (publications.stream()
              .anyMatch(publication -> publication.version().equals(snapshot.version()))
          && !boundVersions.add(snapshot.version()))
        throw new IllegalArgumentException(
            "Multiple local snapshots claim authenticated version " + snapshot.version());
    }
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
    Optional<Instant> validFrom =
        firstInstant(root, "valid_from", "published_at")
            .or(() -> firstInstant(root.path("release_version"), "valid_from", "published_at"));
    if (validFrom.isEmpty()) {
      validFrom = publicationInstant(calDir, "valid_from", "published_at");
    }
    return Optional.of(
        withPublication(
            new Snapshot(
                calendarId,
                "blessed",
                blessedAt,
                sha,
                version,
                calDir,
                validFrom,
                Optional.empty()),
            publications()));
  }

  private record Publication(
      String version,
      String sourceSha,
      Instant publishedAt,
      Optional<Instant> observedCurrentAt,
      Set<String> calendarIds) {}

  private List<Publication> publications() throws IOException {
    Path ledger = releaseHistoryDir.resolve("publications.json");
    if (!Files.exists(ledger)) return List.of();
    JsonNode root = mapper.readTree(ledger.toFile());
    if (!"1.0".equals(root.path("schema_version").asText()) || !root.path("releases").isArray())
      throw new IllegalArgumentException("Malformed publication evidence ledger: " + ledger);
    List<Publication> result = new ArrayList<>();
    Set<String> versions = new HashSet<>();
    Set<Instant> instants = new HashSet<>();
    for (JsonNode row : root.path("releases")) {
      String version = row.path("data_version").asText();
      String sha = row.path("source_sha").asText();
      Instant instant = Instant.parse(row.path("published_at").asText());
      if (!version.matches("\\d+\\.\\d+\\.\\d+")
          || !sha.matches("[0-9a-f]{40}")
          || !versions.add(version)
          || !instants.add(instant))
        throw new IllegalArgumentException("Conflicting publication evidence in " + ledger);
      if (!row.path("atomic_dataset").asBoolean(false) || !row.path("calendar_ids").isArray())
        throw new IllegalArgumentException(
            "Publication must identify the complete dataset inventory");
      Set<String> calendarIds = new HashSet<>();
      for (JsonNode id : row.path("calendar_ids")) {
        if (!id.isTextual() || id.asText().isBlank() || !calendarIds.add(id.asText()))
          throw new IllegalArgumentException("Invalid publication calendar inventory");
      }
      Optional<Instant> observed = firstInstant(row, "observed_current_at");
      if (observed.isPresent() && observed.orElseThrow().isBefore(instant))
        throw new IllegalArgumentException("Currentness observation precedes publication");
      result.add(new Publication(version, sha, instant, observed, Set.copyOf(calendarIds)));
    }
    result.sort(Comparator.comparing(Publication::publishedAt));
    return result;
  }

  private Snapshot withPublication(Snapshot snapshot, List<Publication> publications) {
    for (int index = 0; index < publications.size(); index++) {
      Publication publication = publications.get(index);
      if (!publication.version().equals(snapshot.version())) continue;
      if (!publication.sourceSha().startsWith(snapshot.gitSha()) || snapshot.gitSha().length() < 7)
        throw new IllegalArgumentException(
            "Publication source SHA conflicts with snapshot " + snapshot.id());
      if (!publication.calendarIds().contains(snapshot.calendarId()))
        throw new IllegalArgumentException(
            "Calendar was absent from authenticated publication: " + snapshot.calendarId());
      Optional<Instant> until =
          index + 1 < publications.size()
              ? Optional.of(publications.get(index + 1).publishedAt())
              : publication.observedCurrentAt().map(instant -> instant.plusNanos(1));
      // A receipt proves first publication; an unbounded assertion of continued currentness
      // would incorrectly select the old release inside a later, not-yet-evidenced package.
      Optional<Instant> from =
          until.isPresent() ? Optional.of(publication.publishedAt()) : Optional.empty();
      return new Snapshot(
          snapshot.calendarId(),
          snapshot.id(),
          snapshot.archivedAt(),
          snapshot.gitSha(),
          snapshot.version(),
          snapshot.dir(),
          from,
          until);
    }
    return snapshot;
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
    // An archive timestamp proves when a snapshot stopped being current; it does not prove when
    // that release became current. Date-based history therefore uses only explicit publication
    // evidence. Legacy snapshots remain available through their exact version or directory id.
    return snapshots.stream()
        .filter(snapshot -> snapshot.contains(asOf))
        .max(Comparator.comparing(snapshot -> snapshot.validFrom().orElse(Instant.MIN)));
  }

  private Optional<Instant> publicationInstant(Path snapshotDir, String... fields)
      throws IOException {
    for (String name : List.of("publication.json", "release.json")) {
      Path descriptor = snapshotDir.resolve(name);
      if (Files.exists(descriptor)) {
        Optional<Instant> value = firstInstant(mapper.readTree(descriptor.toFile()), fields);
        if (value.isPresent()) {
          return value;
        }
      }
    }
    Path metadata = snapshotDir.resolve("metadata.json");
    if (Files.exists(metadata)) {
      JsonNode root = mapper.readTree(metadata.toFile());
      Optional<Instant> value = firstInstant(root.path("publication"), fields);
      if (value.isPresent()) {
        return value;
      }
    }
    return Optional.empty();
  }

  private static Optional<Instant> firstInstant(JsonNode node, String... fields) {
    for (String field : fields) {
      if (node.hasNonNull(field)) {
        return Optional.of(Instant.parse(node.path(field).asText()));
      }
    }
    return Optional.empty();
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
        verifiedThrough(snapshot).orElse(null),
        coverageIntervals(snapshot),
        com.bdc.trust.PublishedEventDetails.read(
            mapper.convertValue(metadata(snapshot).get("event_details"), Object.class)));
  }

  /** Explicit scope-specific quality intervals recorded in metadata, or empty for legacy data. */
  public List<CoverageInterval> coverageIntervals(Snapshot snapshot) throws IOException {
    var coverage =
        com.bdc.trust.CoverageIntervals.coverageObject(
            mapper.convertValue(metadata(snapshot).get("coverage"), Object.class));
    return com.bdc.trust.CoverageIntervals.fromJson(coverage.get("quality"));
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
