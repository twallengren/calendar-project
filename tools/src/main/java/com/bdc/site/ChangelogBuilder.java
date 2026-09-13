package com.bdc.site;

import com.bdc.artifact.ReleaseHistoryStore;
import com.bdc.artifact.ReleaseHistoryStore.Snapshot;
import com.bdc.chronology.DateRange;
import com.bdc.diff.CalendarDiff;
import com.bdc.diff.CalendarDiffEngine;
import com.bdc.diff.DiffSeverity;
import com.bdc.model.Event;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a repository-wide {@link Changelog} from {@link ReleaseHistoryStore} snapshots.
 *
 * <p>For each calendar, snapshots are listed in chronological order (oldest first), snapshots that
 * share a {@code (gitSha, version)} pair are deduped (the later archive wins), and {@link
 * CalendarDiffEngine} is run over each consecutive pair, ending with the current blessed release.
 * Snapshots are then grouped across calendars by their {@code (version, gitSha)} into {@link
 * Release} entries, newest first.
 *
 * <p>Older snapshots (pre-dating {@code metadata.json}/{@code resolved.yaml}) are handled
 * gracefully: when a snapshot has no {@code metadata.json}, its comparison range is derived from
 * the min/max date of its own events rather than failing.
 */
public class ChangelogBuilder {

  private final CalendarDiffEngine diffEngine = new CalendarDiffEngine();
  private final LocalDate cutoffDate;

  public ChangelogBuilder() {
    this(LocalDate.now());
  }

  public ChangelogBuilder(LocalDate cutoffDate) {
    this.cutoffDate = cutoffDate;
  }

  /** One published version of the repository, aggregating each calendar's diff into it. */
  public record Release(
      String version,
      String gitSha,
      Instant timestamp,
      boolean blessed,
      DiffSeverity severity,
      int totalAdditions,
      int totalRemovals,
      int totalModifications,
      Map<String, CalendarDiff> calendars) {

    public int totalChanges() {
      return totalAdditions + totalRemovals + totalModifications;
    }
  }

  /** The full changelog: every release with a computable diff, newest first. */
  public record Changelog(List<Release> releases) {}

  private record LoadedSnapshot(List<Event> events, DateRange range) {}

  private static final class ReleaseAccumulator {
    final String version;
    final String gitSha;
    final Instant timestamp;
    final boolean blessed;
    final Map<String, CalendarDiff> calendarDiffs = new LinkedHashMap<>();

    ReleaseAccumulator(String version, String gitSha, Instant timestamp, boolean blessed) {
      this.version = version;
      this.gitSha = gitSha;
      this.timestamp = timestamp;
      this.blessed = blessed;
    }

    Release toRelease() {
      DiffSeverity severity =
          calendarDiffs.values().stream()
              .map(CalendarDiff::severity)
              .reduce(DiffSeverity.NONE, (a, b) -> a.ordinal() > b.ordinal() ? a : b);
      int additions = calendarDiffs.values().stream().mapToInt(d -> d.additions().size()).sum();
      int removals = calendarDiffs.values().stream().mapToInt(d -> d.removals().size()).sum();
      int modifications =
          calendarDiffs.values().stream().mapToInt(d -> d.modifications().size()).sum();
      return new Release(
          version,
          gitSha,
          timestamp,
          blessed,
          severity,
          additions,
          removals,
          modifications,
          Map.copyOf(calendarDiffs));
    }
  }

  /**
   * Builds the changelog for the given calendars, reading snapshots from {@code store}.
   *
   * @param calendarIds calendar ids to include (typically every calendar in the blessed manifest)
   */
  public Changelog build(ReleaseHistoryStore store, Collection<String> calendarIds)
      throws IOException {
    Map<String, ReleaseAccumulator> accumulators = new LinkedHashMap<>();

    for (String calendarId : calendarIds) {
      List<Snapshot> ordered = dedupe(chronological(store.list(calendarId)));
      if (ordered.size() < 2) {
        continue;
      }
      LoadedSnapshot previous = load(store, ordered.get(0));
      for (int i = 1; i < ordered.size(); i++) {
        Snapshot newerSnapshot = ordered.get(i);
        LoadedSnapshot newer = load(store, newerSnapshot);

        CalendarDiff diff =
            diffEngine.compare(
                calendarId,
                newer.events(),
                previous.events(),
                cutoffDate,
                previous.range().start(),
                previous.range().end());

        String key = newerSnapshot.version() + "@" + newerSnapshot.gitSha();
        ReleaseAccumulator acc =
            accumulators.computeIfAbsent(
                key,
                k ->
                    new ReleaseAccumulator(
                        newerSnapshot.version(),
                        newerSnapshot.gitSha(),
                        newerSnapshot.archivedAt(),
                        newerSnapshot.isBlessed()));
        acc.calendarDiffs.put(calendarId, diff);

        previous = newer;
      }
    }

    List<Release> releases =
        accumulators.values().stream()
            .map(ReleaseAccumulator::toRelease)
            .sorted(Comparator.comparing(Release::timestamp).reversed())
            .toList();
    return new Changelog(releases);
  }

  private LoadedSnapshot load(ReleaseHistoryStore store, Snapshot snapshot) throws IOException {
    List<Event> events = store.loadEvents(snapshot);
    DateRange range;
    try {
      range = store.range(snapshot);
    } catch (IOException missingMetadata) {
      range = deriveRange(events);
    }
    return new LoadedSnapshot(events, range);
  }

  private static DateRange deriveRange(List<Event> events) {
    if (events.isEmpty()) {
      LocalDate today = LocalDate.now();
      return new DateRange(today, today);
    }
    LocalDate min = events.stream().map(Event::date).min(Comparator.naturalOrder()).orElseThrow();
    LocalDate max = events.stream().map(Event::date).max(Comparator.naturalOrder()).orElseThrow();
    return new DateRange(min, max);
  }

  private static List<Snapshot> chronological(List<Snapshot> snapshots) {
    List<Snapshot> copy = new ArrayList<>(snapshots);
    copy.sort(Comparator.comparing(Snapshot::archivedAt));
    return copy;
  }

  /** Dedupes snapshots sharing a {@code (gitSha, version)} pair, keeping the later archive. */
  private static List<Snapshot> dedupe(List<Snapshot> chronologicalSnapshots) {
    Map<String, Snapshot> byKey = new LinkedHashMap<>();
    for (Snapshot s : chronologicalSnapshots) {
      String key = s.gitSha() + "|" + s.version();
      byKey.merge(key, s, (a, b) -> a.archivedAt().isAfter(b.archivedAt()) ? a : b);
    }
    List<Snapshot> result = new ArrayList<>(byKey.values());
    result.sort(Comparator.comparing(Snapshot::archivedAt));
    return result;
  }
}
