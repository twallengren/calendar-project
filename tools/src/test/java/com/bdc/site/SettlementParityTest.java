package com.bdc.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.artifact.ReleaseHistoryStore;
import com.bdc.stream.DateStream;
import com.bdc.stream.JointDateStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the settlement fixture the browser self-test page is scored against.
 *
 * <h2>Why a fixture, and not a JS runtime</h2>
 *
 * <p>The T+N helper on every compare page is written in {@code site.js} and must give the same
 * answer as {@link JointDateStream}, which is what {@code query --settlement} answers from. There
 * is no JavaScript engine on the JVM classpath here and adding one to test twenty lines of
 * arithmetic would be a poor trade, so the two implementations meet at a file instead: {@code
 * tools/src/main/resources/site/settlement-fixture.json} holds at least two (pair, trade date, N)
 * cases per market pair (never fewer than fifty) with the answer <em>and</em> the skipped-day
 * breakdown.
 *
 * <p>That splits the problem in two, and both halves are checked:
 *
 * <ul>
 *   <li><b>Here</b>, in Java: every answer in the fixture is recomputed from {@link
 *       JointDateStream} over the blessed artifacts and must match, so the fixture cannot drift
 *       away from the engine it claims to describe. Without this the fixture would be an unguarded
 *       second source of truth — a JS bug could be "fixed" by editing the fixture.
 *   <li><b>In a browser</b>, on {@code /compare/settlement-selftest.html}, which ships this same
 *       fixture, runs {@code site.js}'s algorithm against the published {@code v1} JSON and reports
 *       pass/fail in the DOM.
 * </ul>
 *
 * <p>Regenerate with {@code ./gradlew :tools:test -DupdateGoldens=true}. The cases are generated
 * deterministically (pair index, trade date and N are all functions of the case number), so
 * regenerating on unchanged data rewrites the same bytes.
 */
class SettlementParityTest {

  private static final Path FIXTURE =
      Path.of("tools/src/main/resources/site/settlement-fixture.json");

  /** Trade dates cycle through this many days after {@link #FIRST_TRADE_DATE} (through 2027). */
  private static final int TRADE_DATE_SPAN_DAYS = 1450;

  private static final LocalDate FIRST_TRADE_DATE = LocalDate.of(2024, 1, 1);
  private static final int MAX_N = 10;

  /** One fixture case: the question, the answer, and the closures walked past on the way. */
  record Case(
      String a,
      String b,
      LocalDate tradeDate,
      int n,
      LocalDate settles,
      List<Skip> skipped,
      LocalDate errorDate) {}

  /** A day the walk did not count, and which of the two calendars were shut on it. */
  record Skip(LocalDate date, List<String> closed) {}

  @BeforeAll
  static void updateFixtureWhenRequested() throws IOException {
    if (Boolean.getBoolean("updateGoldens")) {
      List<String> markets = marketIds();
      Files.createDirectories(FIXTURE.getParent());
      Files.writeString(
          FIXTURE, toJson(generate(markets, streams(markets))), StandardCharsets.UTF_8);
    }
  }

  @Test
  void fixtureAnswersMatchJointDateStream() throws IOException {
    List<String> markets = marketIds();
    Map<String, DateStream> streams = streams(markets);

    assertTrue(
        Files.isRegularFile(FIXTURE),
        FIXTURE + " is missing; regenerate with ./gradlew :tools:test -DupdateGoldens=true");

    List<Case> cases = readFixture();
    int expectedCount = caseCount(pairs(marketIds()).size());
    assertEquals(expectedCount, cases.size(), "fixture should hold " + expectedCount + " cases");

    List<String> mismatches = new ArrayList<>();
    for (Case expected : cases) {
      Case actual = answer(streams, expected.a(), expected.b(), expected.tradeDate(), expected.n());
      if (!java.util.Objects.equals(actual.settles(), expected.settles())
          || !java.util.Objects.equals(actual.errorDate(), expected.errorDate())) {
        mismatches.add(
            expected.a()
                + "+"
                + expected.b()
                + " T+"
                + expected.n()
                + " from "
                + expected.tradeDate()
                + ": fixture says "
                + expected.settles()
                + ", JointDateStream says "
                + actual.settles());
      }
      if (!actual.skipped().equals(expected.skipped())) {
        mismatches.add(
            expected.a()
                + "+"
                + expected.b()
                + " T+"
                + expected.n()
                + " from "
                + expected.tradeDate()
                + ": skipped days differ ("
                + expected.skipped()
                + " vs "
                + actual.skipped()
                + ")");
      }
    }
    assertTrue(mismatches.isEmpty(), "fixture disagrees with JointDateStream: " + mismatches);
  }

  @Test
  void fixtureExercisesEveryPairAndEverySettlementDepth() throws IOException {
    List<Case> cases = readFixture();
    TreeSet<String> pairs = new TreeSet<>();
    TreeSet<Integer> depths = new TreeSet<>();
    for (Case one : cases) {
      assertTrue(one.a().compareTo(one.b()) < 0, "pairs should be stored in id order: " + one);
      pairs.add(one.a() + "+" + one.b());
      depths.add(one.n());
    }
    assertEquals(
        pairs(marketIds()).size(), pairs.size(), "every market pair should appear in the fixture");
    for (int n = 0; n <= MAX_N; n++) {
      assertTrue(depths.contains(n), "fixture should cover T+" + n);
    }
  }

  @Test
  void fixtureCoversDaysBothMarketsAreShut() throws IOException {
    List<Case> cases = readFixture();
    assertTrue(
        cases.stream()
            .flatMap(one -> one.skipped().stream())
            .anyMatch(skip -> skip.closed().size() == 2),
        "resolved cases must exercise a day when both members are closed");
  }

  // === Cases ===

  /**
   * The fixture's cases, as a pure function of the case number: pair index, trade date and N all
   * derive from {@code i}, so the fixture is reproducible and its coverage is visible here rather
   * than frozen into a file nobody can regenerate.
   */
  private static List<Case> generate(List<String> markets, Map<String, DateStream> streams) {
    List<List<String>> pairs = pairs(markets);
    List<Case> cases = new ArrayList<>();
    for (int i = 0; i < caseCount(pairs.size()); i++) {
      List<String> pair = pairs.get(i % pairs.size());
      LocalDate tradeDate = FIRST_TRADE_DATE.plusDays((i * 29L) % TRADE_DATE_SPAN_DAYS);
      int n = i % (MAX_N + 1);
      cases.add(answer(streams, pair.get(0), pair.get(1), tradeDate, n));
    }
    return cases;
  }

  /** Two cases per pair so every pair is exercised with two depths, and at least fifty overall. */
  static int caseCount(int pairs) {
    return Math.max(50, 2 * pairs);
  }

  /** The joint T+N answer, and which calendars were shut on each day the walk did not count. */
  private static Case answer(
      Map<String, DateStream> streams, String a, String b, LocalDate tradeDate, int n) {
    JointDateStream joint =
        (JointDateStream) JointDateStream.joint(List.of(streams.get(a), streams.get(b)));
    LocalDate settles;
    try {
      settles = joint.nthBusinessDay(tradeDate, n);
    } catch (com.bdc.stream.OutsideCoverageException error) {
      return new Case(a, b, tradeDate, n, null, List.of(), error.date());
    }
    List<Skip> skipped = new ArrayList<>();
    for (LocalDate date = tradeDate.plusDays(1); !date.isAfter(settles); date = date.plusDays(1)) {
      List<String> closed = joint.closedMembers(date).stream().map(DateStream::calendarId).toList();
      if (!closed.isEmpty()) {
        skipped.add(new Skip(date, closed));
      }
    }
    return new Case(a, b, tradeDate, n, settles, List.copyOf(skipped), null);
  }

  static List<List<String>> pairs(List<String> markets) {
    List<List<String>> pairs = new ArrayList<>();
    for (int i = 0; i < markets.size(); i++) {
      for (int j = i + 1; j < markets.size(); j++) {
        pairs.add(List.of(markets.get(i), markets.get(j)));
      }
    }
    return List.copyOf(pairs);
  }

  // === Inputs ===

  /** Published market and payment calendars, sorted by id. */
  static List<String> marketIds() throws IOException {
    JsonNode manifest = new ObjectMapper().readTree(Path.of("blessed/manifest.json").toFile());
    List<String> ids = new ArrayList<>();
    manifest
        .path("calendars")
        .fields()
        .forEachRemaining(
            entry -> {
              String kind = entry.getValue().path("kind").asText("market");
              if ("market".equals(kind) || "payment".equals(kind)) {
                ids.add(entry.getKey());
              }
            });
    ids.sort(String::compareTo);
    return List.copyOf(ids);
  }

  private static Map<String, DateStream> streams(List<String> ids) throws IOException {
    ReleaseHistoryStore store =
        new ReleaseHistoryStore(Path.of("release-history"), Path.of("blessed"));
    Map<String, DateStream> streams = new LinkedHashMap<>();
    for (String id : ids) {
      ReleaseHistoryStore.Snapshot snapshot =
          store
              .blessedSnapshot(id)
              .orElseThrow(() -> new IllegalStateException("No blessed artifact for " + id));
      streams.put(id, store.stream(snapshot));
    }
    return streams;
  }

  // === Fixture I/O ===

  static List<Case> readFixture() throws IOException {
    JsonNode root = new ObjectMapper().readTree(FIXTURE.toFile());
    List<Case> cases = new ArrayList<>();
    for (JsonNode node : root.path("cases")) {
      List<Skip> skipped = new ArrayList<>();
      for (JsonNode skip : node.path("skipped")) {
        List<String> closed = new ArrayList<>();
        skip.path("closed").forEach(id -> closed.add(id.asText()));
        skipped.add(new Skip(LocalDate.parse(skip.path("date").asText()), List.copyOf(closed)));
      }
      cases.add(
          new Case(
              node.path("a").asText(),
              node.path("b").asText(),
              LocalDate.parse(node.path("trade_date").asText()),
              node.path("n").asInt(),
              node.path("settles").isNull() ? null : LocalDate.parse(node.path("settles").asText()),
              List.copyOf(skipped),
              node.hasNonNull("error_date")
                  ? LocalDate.parse(node.path("error_date").asText())
                  : null));
    }
    return List.copyOf(cases);
  }

  /**
   * One case per line. The file is embedded verbatim into the self-test page, so it is written
   * compactly by hand rather than through a pretty-printer whose defaults could change under us.
   */
  static String toJson(List<Case> cases) {
    StringBuilder json = new StringBuilder(16 * 1024);
    json.append("{\n");
    json.append("\"note\": \"Generated by SettlementParityTest with -DupdateGoldens=true.")
        .append(" Answers come from JointDateStream over the blessed artifacts;")
        .append(" /compare/settlement-selftest.html scores site.js against them.\",\n");
    json.append("\"cases\": [\n");
    for (int i = 0; i < cases.size(); i++) {
      Case one = cases.get(i);
      json.append("{\"a\":\"")
          .append(one.a())
          .append("\",\"b\":\"")
          .append(one.b())
          .append("\",\"trade_date\":\"")
          .append(one.tradeDate())
          .append("\",\"n\":")
          .append(one.n())
          .append(",\"settles\":")
          .append(one.settles() == null ? "null" : "\"" + one.settles() + "\"");
      if (one.errorDate() != null)
        json.append(",\"error_date\":\"").append(one.errorDate()).append("\"");
      json.append(",\"skipped\":[");
      for (int s = 0; s < one.skipped().size(); s++) {
        Skip skip = one.skipped().get(s);
        json.append(s == 0 ? "" : ",")
            .append("{\"date\":\"")
            .append(skip.date())
            .append("\",\"closed\":[");
        for (int c = 0; c < skip.closed().size(); c++) {
          json.append(c == 0 ? "" : ",").append("\"").append(skip.closed().get(c)).append("\"");
        }
        json.append("]}");
      }
      json.append("]}").append(i == cases.size() - 1 ? "" : ",").append("\n");
    }
    json.append("]\n}\n");
    return json.toString();
  }
}
