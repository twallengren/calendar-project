package com.bdc.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Checks the compare pages against the real published site: that every pair exists exactly once,
 * that a known difference shows up on the right side of the right page, and that the settlement
 * helper degrades to a CLI command when scripting is off.
 */
class ComparePageTest {

  @Test
  void everyUnorderedPairOfMarketsHasExactlyOnePage() throws IOException {
    Path compare = GeneratedSite.get().resolve("compare");
    List<String> pages;
    try (Stream<Path> files = Files.walk(compare)) {
      pages =
          files
              .filter(path -> path.getFileName().toString().equals("index.html"))
              .map(path -> compare.relativize(path.getParent()).toString().replace('\\', '/'))
              .filter(path -> !path.isEmpty())
              .sorted()
              .toList();
    }

    int markets = GeneratedSite.get().resolve("v1/calendars").toFile().list().length;
    assertEquals(
        markets * (markets - 1) / 2, pages.size(), "one page per unordered pair: " + pages);
    for (String page : pages) {
      String[] ids = page.split("/");
      assertEquals(2, ids.length, "a pair page lives two levels down: " + page);
      assertTrue(
          ids[0].compareTo(ids[1]) < 0,
          "a pair page is stored in id order so it has one URL, not two: " + page);
    }
  }

  @Test
  void comparePageIndexListsBothDirectionsOfEveryPair() throws IOException {
    String index = GeneratedSite.read("compare/index.html");
    assertTrue(
        index.contains("href=\"SA-TADAWUL/US-NYSE/index.html\">SA-TADAWUL vs US-NYSE</a>"),
        "index should link the pair from the first market's section");
    assertTrue(
        index.contains("href=\"SA-TADAWUL/US-NYSE/index.html\">US-NYSE vs SA-TADAWUL</a>"),
        "index should link the same page from the second market's section");
  }

  /**
   * Thanksgiving 2026 is a full NYSE closure and an ordinary Tadawul trading day (a Thursday, which
   * stopped being a Saudi weekend day in 2013); Saudi National Day 2026 is the mirror image. One
   * page has to carry both, each on its own side.
   */
  @Test
  void knownDifferencesAppearOnTheCorrectSide() throws IOException {
    String page = GeneratedSite.read("compare/SA-TADAWUL/US-NYSE/index.html");
    String tadawulOpen = section(page, "a-open-heading", "b-open-heading");
    String nyseOpen = section(page, "b-open-heading", "All 36 market pairs");

    assertTrue(
        tadawulOpen.contains("2026-11-26") && tadawulOpen.contains("Thanksgiving Day"),
        "Thanksgiving 2026 is a Tadawul trading day and an NYSE closure");
    assertFalse(
        nyseOpen.contains("Thanksgiving Day"), "Thanksgiving must not appear in the other table");

    assertTrue(
        nyseOpen.contains("2026-09-23") && nyseOpen.contains("Saudi National Day"),
        "Saudi National Day 2026 is an NYSE trading day and a Tadawul closure");
    assertFalse(
        tadawulOpen.contains("Saudi National Day"),
        "Saudi National Day must not appear in the other table");
  }

  /**
   * A holiday that falls on the closed market's own weekend restates the weekend policy rather than
   * showing a holiday difference. Eid al-Fitr 2026 runs 17–23 March; the Friday, 20 March, is a
   * Saudi weekend day and must not be listed, while the Tuesday and Monday must be.
   */
  @Test
  void holidaysOnTheClosedMarketsOwnWeekendAreNotListed() throws IOException {
    String page = GeneratedSite.read("compare/SA-TADAWUL/US-NYSE/index.html");
    String nyseOpen = section(page, "b-open-heading", "All 36 market pairs");
    assertTrue(nyseOpen.contains("2026-03-17"), "17 March 2026 is a Tuesday Eid closure");
    assertTrue(nyseOpen.contains("2026-03-23"), "23 March 2026 is a Monday Eid closure");
    assertFalse(
        nyseOpen.contains("2026-03-20"), "20 March 2026 is a Saudi weekend day, not a difference");
  }

  @Test
  void pairPageSummarisesCountsAndTheJointVerifiedThroughDate() throws IOException {
    String page = GeneratedSite.read("compare/SA-TADAWUL/US-NYSE/index.html");
    // SA-TADAWUL is verified through 2029-12-31 and US-NYSE through 2026-12-31; the joint answer is
    // only as good as its worst member, so the page must show the earlier of the two.
    assertTrue(
        page.contains("<dt>Joint verified through</dt><dd><time datetime=\"2026-12-31\">"),
        "joint verified_through should be the minimum of the two calendars");
    assertTrue(
        page.contains("<dt>Shared coverage</dt><dd>2020-01-01 to 2030-12-31"),
        "shared coverage should be the intersection of the two ranges");
    assertTrue(
        page.contains("<dt>Open in SA-TADAWUL, closed in US-NYSE</dt>")
            && page.contains("<dt>Open in US-NYSE, closed in SA-TADAWUL</dt>"),
        "summary should count both directions");
  }

  @Test
  void withoutScriptingTheSettlementFormIsAbsentAndTheCliCommandIsNamed() throws IOException {
    String page = GeneratedSite.read("compare/SA-TADAWUL/US-NYSE/index.html");
    assertFalse(page.contains("<form"), "the settlement form is built by site.js, not rendered");
    assertTrue(
        page.contains("tools query SA-TADAWUL,US-NYSE --settlement T+2 --from"),
        "the page should name the CLI command that answers the same question");
    assertTrue(
        page.contains("data-settlement data-a=\"SA-TADAWUL\" data-b=\"US-NYSE\""),
        "the mount point should name both calendars for the script to pick up");
  }

  @Test
  void homeAndMarketPagesLinkTheComparePages() throws IOException {
    assertTrue(
        GeneratedSite.read("index.html").contains("href=\"compare/index.html\""),
        "home page should link the compare index");
    String market = GeneratedSite.read("US-NYSE/index.html");
    assertTrue(
        market.contains("href=\"../compare/SA-TADAWUL/US-NYSE/index.html\""),
        "market page should link its pair pages by their canonical path");
    assertTrue(
        market.contains("href=\"../compare/index.html\""), "market page should link the index");
  }

  @Test
  void selfTestPageShipsTheFixtureItScoresAgainst() throws IOException {
    String page = GeneratedSite.read("compare/settlement-selftest.html");
    assertTrue(
        page.contains("<script type=\"application/json\" id=\"settlement-fixture\">"),
        "the self-test page should embed the fixture");
    assertTrue(page.contains("data-settlement-selftest"), "the script needs a mount point");
    assertTrue(
        page.contains("\"trade_date\""), "the embedded fixture should carry the generated cases");
  }

  /** The slice of the page between two markers, so an assertion cannot match the other table. */
  private static String section(String html, String from, String to) {
    int start = html.indexOf(from);
    assertTrue(start >= 0, "expected the page to contain " + from);
    int end = html.indexOf(to, start);
    assertTrue(end > start, "expected the page to contain " + to + " after " + from);
    return html.substring(start, end);
  }
}
