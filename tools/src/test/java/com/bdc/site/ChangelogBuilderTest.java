package com.bdc.site;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.artifact.ReleaseHistoryStore;
import com.bdc.diff.CalendarDiff;
import com.bdc.diff.DiffSeverity;
import com.bdc.diff.EventDiff;
import com.bdc.site.ChangelogBuilder.Changelog;
import com.bdc.site.ChangelogBuilder.Release;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangelogBuilderTest {

  private static final String CAL = "TEST-CAL";

  @TempDir Path dir;

  private void snapshot(Path snapshotDir, String csv, String rangeStart, String rangeEnd)
      throws IOException {
    Files.createDirectories(snapshotDir);
    Files.writeString(snapshotDir.resolve("events.csv"), csv);
    if (rangeStart != null) {
      Files.writeString(
          snapshotDir.resolve("metadata.json"),
          "{\"calendar_id\":\""
              + CAL
              + "\",\"range_start\":\""
              + rangeStart
              + "\",\"range_end\":\""
              + rangeEnd
              + "\"}");
    }
    // rangeStart == null models an older snapshot with no metadata.json/resolved.yaml at all.
  }

  /**
   * Builds a fixture with:
   *
   * <ul>
   *   <li>v1.0.0 (oldest): no metadata.json -- must degrade to deriving its range from events
   *   <li>v2.0.0: archived twice with the SAME (gitSha, version) -- the earlier archive is stale
   *       and must be dropped in favor of the later one
   *   <li>v3.0.0: a normal snapshot
   *   <li>blessed (v4.0.0): the current release
   * </ul>
   */
  private ReleaseHistoryStore buildFixture() throws IOException {
    Path history = dir.resolve("release-history");
    Path blessed = dir.resolve("blessed");

    // v1.0.0 -- no metadata.json (pre-dates it). Range must derive from event dates:
    // 2020-01-01 .. 2020-07-04.
    snapshot(
        history.resolve(CAL).resolve("2024-01-01T00-00-00Z_aaa1111_v1.0.0"),
        "date,type,description\n"
            + "2020-01-01,CLOSED,New Year's Day\n"
            + "2020-07-04,CLOSED,Independence Day\n",
        null,
        null);

    // v2.0.0, archived twice under the same (gitSha, version). The earlier one is stale
    // (wrong description) and must be discarded in favor of the later archive.
    snapshot(
        history.resolve(CAL).resolve("2024-02-01T00-00-00Z_bbb2222_v2.0.0"),
        "date,type,description\n"
            + "2020-01-01,CLOSED,New Year's Day\n"
            + "2020-07-04,CLOSED,Independence Day\n"
            + "2020-12-25,CLOSED,STALE Christmas\n",
        "2020-01-01",
        "2020-12-31");
    snapshot(
        history.resolve(CAL).resolve("2024-02-01T06-00-00Z_bbb2222_v2.0.0"),
        "date,type,description\n"
            + "2020-01-01,CLOSED,New Year's Day\n"
            + "2020-07-04,CLOSED,Independence Day\n"
            + "2020-12-25,CLOSED,Christmas Day\n",
        "2020-01-01",
        "2020-12-31");

    // v3.0.0: adds Thanksgiving.
    snapshot(
        history.resolve(CAL).resolve("2024-03-01T00-00-00Z_ccc3333_v3.0.0"),
        "date,type,description\n"
            + "2020-01-01,CLOSED,New Year's Day\n"
            + "2020-07-04,CLOSED,Independence Day\n"
            + "2020-12-25,CLOSED,Christmas Day\n"
            + "2020-11-26,CLOSED,Thanksgiving\n",
        "2020-01-01",
        "2020-12-31");

    // blessed v4.0.0: drops Independence Day (removal), adds Memorial Day (addition).
    snapshot(
        blessed.resolve(CAL),
        "date,type,description\n"
            + "2020-01-01,CLOSED,New Year's Day\n"
            + "2020-05-25,CLOSED,Memorial Day\n"
            + "2020-12-25,CLOSED,Christmas Day\n"
            + "2020-11-26,CLOSED,Thanksgiving\n",
        "2020-01-01",
        "2020-12-31");
    Files.writeString(
        blessed.resolve("manifest.json"),
        """
        {"blessed_at":"2024-04-01T00:00:00Z",
         "calendars":{"TEST-CAL":{"range_start":"2020-01-01","range_end":"2020-12-31"}},
         "release_version":{"semantic":"4.0.0","git_sha":"ddd4444full"}}
        """);

    return new ReleaseHistoryStore(history, blessed);
  }

  @Test
  void pairsDedupesAndDegradesAcrossReleases() throws IOException {
    ReleaseHistoryStore store = buildFixture();
    Changelog changelog = new ChangelogBuilder(LocalDate.of(2024, 6, 1)).build(store, Set.of(CAL));

    // v1.0.0 has no predecessor, so only 3 releases have a computable diff.
    assertEquals(3, changelog.releases().size());
    List<String> versions = changelog.releases().stream().map(Release::version).toList();
    assertEquals(List.of("4.0.0", "3.0.0", "2.0.0"), versions, "releases must be newest first");

    Release v4 = changelog.releases().get(0);
    assertTrue(v4.blessed());
    CalendarDiff v4Diff = v4.calendars().get(CAL);
    assertEquals(DiffSeverity.MAJOR, v4Diff.severity(), "a removal must classify as MAJOR");
    assertEquals(
        List.of("Independence Day"),
        v4Diff.removals().stream().map(EventDiff::oldDescription).toList());
    assertEquals(
        List.of("Memorial Day"),
        v4Diff.additions().stream().map(EventDiff::newDescription).toList());

    Release v3 = changelog.releases().get(1);
    CalendarDiff v3Diff = v3.calendars().get(CAL);
    assertEquals(
        List.of("Thanksgiving"),
        v3Diff.additions().stream().map(EventDiff::newDescription).toList());

    // The v2.0.0 diff must have used the LATER of the two same-(sha,version) archives: its
    // addition is "Christmas Day", not the stale duplicate's "STALE Christmas".
    Release v2 = changelog.releases().get(2);
    CalendarDiff v2Diff = v2.calendars().get(CAL);
    assertEquals(
        List.of("Christmas Day"),
        v2Diff.additions().stream().map(EventDiff::newDescription).toList(),
        "dedupe must keep the later archive of a (gitSha, version) pair");

    // v2.0.0's diff compares against v1.0.0 (older), which has no metadata.json: its range must
    // be derived from v1.0.0's own event dates (2020-01-01 .. 2020-07-04), not fail.
    assertEquals(LocalDate.of(2020, 1, 1), v2Diff.blessedRangeStart());
    assertEquals(LocalDate.of(2020, 7, 4), v2Diff.blessedRangeEnd());
  }

  @Test
  void jsonEmitterWritesUntruncatedNewestFirstReleases() throws IOException {
    ReleaseHistoryStore store = buildFixture();
    Changelog changelog = new ChangelogBuilder(LocalDate.of(2024, 6, 1)).build(store, Set.of(CAL));

    Path outDir = dir.resolve("site-out");
    Path written = new ChangelogJsonEmitter().write(changelog, outDir);
    assertTrue(Files.exists(written));

    String json = Files.readString(written);
    assertFalse(json.contains("\n  "), "changelog.json must be minified");

    com.fasterxml.jackson.databind.ObjectMapper mapper =
        new com.fasterxml.jackson.databind.ObjectMapper();
    var root = mapper.readTree(written.toFile());
    var releases = root.get("releases");
    assertEquals(3, releases.size());
    assertEquals("4.0.0", releases.get(0).get("version").asText());
    assertEquals("3.0.0", releases.get(1).get("version").asText());
    assertEquals("2.0.0", releases.get(2).get("version").asText());

    var v4Calendar = releases.get(0).get("calendars").get(CAL);
    assertEquals(1, v4Calendar.get("removals").size());
    assertEquals(
        "Independence Day", v4Calendar.get("removals").get(0).get("old_description").asText());
    assertEquals(1, v4Calendar.get("additions").size());
    assertEquals("MAJOR", v4Calendar.get("severity").asText());
  }

  /**
   * Regression test against the real repository history: the "fix saudi calendar" (#17) release
   * also changed {@code EventGenerator}'s weekend-shift handling, which removed the shifted New
   * Year's Day closure on 2021-12-31 from US-NYSE between v10.1.0 and blessed v11.0.0. Assert that
   * this specific, known removal is present in the real changelog.
   */
  @Test
  void realReleaseHistoryNyseDiffIncludesKnownRemoval() throws IOException {
    Path realBlessed = Path.of("blessed");
    Path realHistory = Path.of("release-history");
    org.junit.jupiter.api.Assumptions.assumeTrue(
        Files.isDirectory(realBlessed) && Files.isDirectory(realHistory),
        "requires running from the repo root with real blessed/ and release-history/");

    ReleaseHistoryStore store = new ReleaseHistoryStore(realHistory, realBlessed);
    Changelog changelog = new ChangelogBuilder().build(store, Set.of("US-NYSE"));

    Release v11 =
        changelog.releases().stream()
            .filter(r -> r.version().equals("11.0.0"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected a v11.0.0 release in the changelog"));

    CalendarDiff nyseDiff = v11.calendars().get("US-NYSE");
    assertNotNull(nyseDiff);
    assertEquals(DiffSeverity.MAJOR, nyseDiff.severity());

    boolean removedNewYearsEve2021 =
        nyseDiff.removals().stream()
            .anyMatch(
                e ->
                    e.date().equals(LocalDate.of(2021, 12, 31))
                        && "New Year's Day".equals(e.oldDescription()));
    assertTrue(
        removedNewYearsEve2021,
        "expected US-NYSE v10.1.0->v11.0.0 diff to remove the shifted 2021-12-31 New Year's Day"
            + " closure; actual removals: "
            + nyseDiff.removals());
  }
}
