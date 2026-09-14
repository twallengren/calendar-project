package com.bdc.artifact;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.stream.CsvDateStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseHistoryStoreTest {

  @TempDir Path dir;
  private Path history;
  private Path blessed;

  private void snapshot(Path base, String csv, String rangeStart, String rangeEnd)
      throws Exception {
    Files.createDirectories(base);
    Files.writeString(base.resolve("events.csv"), csv);
    Files.writeString(
        base.resolve("metadata.json"),
        "{\"calendar_id\":\"CAL\",\"range_start\":\""
            + rangeStart
            + "\",\"range_end\":\""
            + rangeEnd
            + "\"}");
  }

  @BeforeEach
  void setUp() throws Exception {
    history = dir.resolve("release-history");
    blessed = dir.resolve("blessed");
    // v5 (old, wrong: Dec 31 2021 closed) archived 2026-02-01; v10 archived 2026-02-16; blessed v11
    snapshot(
        history.resolve("CAL/2026-02-01T15-31-34Z_eff570b_v5.0.0"),
        "date,type,description\n2021-12-31,CLOSED,New Year's Day\n2022-01-01,WEEKEND,Saturday\n",
        "2021-01-01",
        "2022-12-31");
    snapshot(
        history.resolve("CAL/2026-02-16T21-43-18Z_98bac4f_v10.1.0"),
        "date,type,description\n2021-12-31,CLOSED,New Year's Day\n2022-01-01,WEEKEND,Saturday\n",
        "2021-01-01",
        "2022-12-31");
    snapshot(
        blessed.resolve("CAL"),
        "date,type,description,key,source_module,observed_from,close_time,status\n2022-01-01,WEEKEND,Saturday,weekend,weekend_policy,,,CONFIRMED\n",
        "2021-01-01",
        "2022-12-31");
    Files.writeString(
        blessed.resolve("manifest.json"),
        """
        {"blessed_at":"2026-03-01T10:00:00Z","calendars":{"CAL":{"range_start":"2021-01-01","range_end":"2022-12-31"}},
         "release_version":{"semantic":"11.0.0","git_sha":"3765bcb"}}
        """);
  }

  @Test
  void listsNewestFirstIncludingBlessed() throws Exception {
    ReleaseHistoryStore store = new ReleaseHistoryStore(history, blessed);
    List<ReleaseHistoryStore.Snapshot> snapshots = store.list("CAL");
    assertEquals(3, snapshots.size());
    assertEquals("11.0.0", snapshots.get(0).version());
    assertTrue(snapshots.get(0).isBlessed());
    assertEquals("10.1.0", snapshots.get(1).version());
    assertEquals("5.0.0", snapshots.get(2).version());
  }

  @Test
  void resolvesByVersionAndAsOfDate() throws Exception {
    ReleaseHistoryStore store = new ReleaseHistoryStore(history, blessed);
    assertEquals("5.0.0", store.resolve("CAL", "v5.0.0").orElseThrow().version());
    assertEquals("10.1.0", store.resolve("CAL", "10.1.0").orElseThrow().version());
    assertEquals("11.0.0", store.resolve("CAL", "blessed").orElseThrow().version());
    // v5 was current until it was archived on 2026-02-01; v10.1.0 until 2026-02-16; then blessed
    assertEquals("5.0.0", store.resolve("CAL", "2026-01-20").orElseThrow().version());
    assertEquals("10.1.0", store.resolve("CAL", "2026-02-10").orElseThrow().version());
    assertEquals("11.0.0", store.resolve("CAL", "2026-02-20").orElseThrow().version());
    assertTrue(store.resolve("CAL", "v99.0.0").isEmpty());
  }

  @Test
  void asOfQueryAnswersFromTheSnapshot() throws Exception {
    ReleaseHistoryStore store = new ReleaseHistoryStore(history, blessed);
    ReleaseHistoryStore.Snapshot old = store.resolve("CAL", "v10.1.0").orElseThrow();
    List<Event> events = store.loadEvents(old);
    CsvDateStream oldStream = new CsvDateStream("CAL", events, store.range(old));
    assertFalse(oldStream.isBusinessDay(LocalDate.of(2021, 12, 31)));

    ReleaseHistoryStore.Snapshot current = store.resolve("CAL", "blessed").orElseThrow();
    CsvDateStream currentStream =
        new CsvDateStream("CAL", store.loadEvents(current), store.range(current));
    assertTrue(currentStream.isBusinessDay(LocalDate.of(2021, 12, 31)));
    assertFalse(currentStream.isBusinessDay(LocalDate.of(2022, 1, 1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> currentStream.isBusinessDay(LocalDate.of(2030, 1, 1)));
  }

  @Test
  void streamCarriesVerifiedThroughAndStatusFromMetadata() throws Exception {
    Path base = history.resolve("CAL/2026-02-20T10-00-00Z_abc1234_v10.2.0");
    Files.createDirectories(base);
    Files.writeString(
        base.resolve("events.csv"), "date,type,description\n2022-01-01,WEEKEND,Saturday\n");
    Files.writeString(
        base.resolve("metadata.json"),
        "{\"calendar_id\":\"CAL\",\"range_start\":\"2021-01-01\",\"range_end\":\"2022-12-31\","
            + "\"coverage\":{\"from\":\"2021-01-01\",\"to\":\"2022-12-31\","
            + "\"verified_through\":\"2021-12-31\"}}");

    ReleaseHistoryStore store = new ReleaseHistoryStore(history, blessed);
    CsvDateStream stream = store.stream(store.resolve("CAL", "v10.2.0").orElseThrow());

    assertEquals(LocalDate.of(2021, 12, 31), stream.verifiedThrough().orElseThrow());
    assertEquals(EventStatus.CONFIRMED, stream.status(LocalDate.of(2021, 6, 1)));
    assertEquals(EventStatus.PROJECTED, stream.status(LocalDate.of(2022, 6, 1)));
    assertEquals(EventStatus.UNKNOWN, stream.status(LocalDate.of(2030, 1, 1)));
  }

  @Test
  void snapshotWithoutCoverageHasNoVerifiedThrough() throws Exception {
    ReleaseHistoryStore store = new ReleaseHistoryStore(history, blessed);
    assertTrue(
        store.stream(store.resolve("CAL", "blessed").orElseThrow()).verifiedThrough().isEmpty());
  }
}
