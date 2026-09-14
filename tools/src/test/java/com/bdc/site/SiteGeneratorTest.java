package com.bdc.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bdc.cli.Main;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Runs the real {@code site} command against the real {@code blessed/} and {@code release-history/}
 * directories and checks the published result: the pages a reader lands on, every internal link,
 * the page-weight budget, the sitemap, and byte-for-byte reproducibility.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SiteGeneratorTest {

  private static final String GENERATED_AT = "2026-06-01T00:00:00Z";
  private static final String BASE_URL = "https://example.test/calendars/";
  private static final long MAX_PAGE_BYTES = 400 * 1024L;

  /** href/src values, which is every link the browser would follow or fetch. */
  private static final Pattern LINK = Pattern.compile("(?:href|src)=\"([^\"]+)\"");

  private static final Pattern SITEMAP_LOC = Pattern.compile("<loc>([^<]+)</loc>");

  @TempDir static Path first;

  @TempDir static Path second;

  @BeforeAll
  void generate() {
    assertEquals(0, runSite(first), "site command failed");
  }

  private static int runSite(Path outDir) {
    return new CommandLine(new Main())
        .execute(
            "site",
            "--out",
            outDir.toString(),
            "--base-url",
            BASE_URL,
            "--site-name",
            "Business Day Calendars",
            "--generated-at",
            GENERATED_AT);
  }

  @Test
  void homePageListsEveryMarket() throws IOException {
    String home = read(first.resolve("index.html"));
    assertTrue(home.contains("NYSE Trading Calendar"), "home page should name the NYSE calendar");
    assertTrue(home.contains("US-NYSE"), "home page should link the NYSE market page");
    assertTrue(home.contains("SA-TADAWUL"), "home page should link the Tadawul market page");
    assertTrue(
        home.contains("href=\"US-NYSE/index.html\""), "home page should link the market page");
    assertTrue(home.contains("webcal://example.test/"), "home page should offer a subscribe link");
  }

  @Test
  void yearPageCarriesTheObservedClosureAndTheEarlyClose() throws IOException {
    String page = read(first.resolve("US-NYSE/2027/index.html"));
    assertTrue(
        page.contains("<h1>NYSE Trading Calendar holidays 2027</h1>"),
        "year page should lead with the searched-for phrase");
    assertTrue(page.contains("rel=\"canonical\""), "year page should declare a canonical URL");
    assertTrue(
        page.contains(BASE_URL + "US-NYSE/2027/"), "canonical URL should use the configured base");

    String table = eventsTable(page);

    // 2027-07-04 is a Sunday: the closure is observed on Monday the 5th, and the row must say so.
    String july5Row = rowContaining(table, "2027-07-05");
    assertTrue(july5Row.contains("Independence Day"), "July 5 row should name Independence Day");
    assertTrue(
        july5Row.contains("datetime=\"2027-07-04\""),
        "July 5 row should record 2027-07-04 as the date it is observed for");

    String dayAfterThanksgiving = rowContaining(table, "2027-11-26");
    assertTrue(dayAfterThanksgiving.contains("Early close"), "Nov 26 should be an early close");
    assertTrue(dayAfterThanksgiving.contains("13:00"), "Nov 26 should close at 13:00");
  }

  @Test
  void everyYearInCoverageHasAPage() throws IOException {
    assertTrue(Files.exists(first.resolve("US-NYSE/1900/index.html")), "first covered year");
    assertTrue(Files.exists(first.resolve("US-NYSE/2030/index.html")), "last covered year");
    assertTrue(Files.exists(first.resolve("SA-TADAWUL/2020/index.html")), "Tadawul first year");
  }

  @Test
  void datePageExistsForAnObservedClosure() throws IOException {
    Path page = first.resolve("US-NYSE/2027-07-05/index.html");
    assertTrue(Files.exists(page), "a permalink should exist for 2027-07-05");
    String html = read(page);
    assertTrue(html.contains("Independence Day"), "date page should name the holiday");
    assertTrue(html.contains("is closed on"), "date page should answer open/closed directly");
    assertTrue(
        html.contains("Previous business day"), "date page should link nearby business days");
  }

  @Test
  void staticAssetsAreCopied() {
    assertTrue(Files.exists(first.resolve("styles.css")), "styles.css should be published");
    assertTrue(Files.exists(first.resolve("site.js")), "site.js should be published");
  }

  /**
   * The budget includes the business-date helper's per-scope coverage checks and traversal
   * confidence. It is still a hand-written, unminified, comment-carrying file served as-is, and it
   * is still optional — every page works with it blocked — but it now carries a real algorithm, so
   * the ceiling is set where an accidental framework or a pasted library would break through it
   * rather than where a comment would.
   */
  @Test
  void siteJsStaysSmallEnoughToBeOptional() throws IOException {
    assertTrue(
        Files.size(first.resolve("site.js")) < 20 * 1024,
        "site.js is progressive enhancement only and must stay under 20 KB");
  }

  @Test
  void changelogIsRenderedIntoTheSameOutput() throws IOException {
    assertTrue(Files.exists(first.resolve("changelog/index.html")), "changelog index");
    assertTrue(Files.exists(first.resolve("v1/changelog.json")), "changelog JSON");
    String index = read(first.resolve("changelog/index.html"));
    assertTrue(index.contains("styles.css"), "changelog should use the shared stylesheet");
    assertTrue(
        index.contains("Business Day Calendars"), "changelog should use the shared masthead");
  }

  @Test
  void everyInternalLinkResolvesToAFileInTheOutput() throws IOException {
    List<String> broken = new ArrayList<>();
    int checked = 0;
    for (Path page : htmlPages(first)) {
      String html = read(page);
      Matcher matcher = LINK.matcher(html);
      while (matcher.find()) {
        String href = strip(matcher.group(1));
        if (href.isEmpty() || isExternal(href)) {
          continue;
        }
        checked++;
        Path target = page.getParent().resolve(href).normalize();
        if (href.endsWith("/") || Files.isDirectory(target)) {
          target = target.resolve("index.html");
        }
        if (!Files.isRegularFile(target)) {
          broken.add(first.relativize(page) + " -> " + href);
        }
      }
    }
    assertTrue(checked > 1000, "expected the link checker to find plenty of links, got " + checked);
    assertTrue(broken.isEmpty(), "broken internal links: " + head(broken));
  }

  @Test
  void noPageExceedsTheWeightBudget() throws IOException {
    List<String> tooBig = new ArrayList<>();
    for (Path page : htmlPages(first)) {
      long size = Files.size(page);
      if (size > MAX_PAGE_BYTES) {
        tooBig.add(first.relativize(page) + " (" + size + " bytes)");
      }
    }
    assertTrue(tooBig.isEmpty(), "pages over " + MAX_PAGE_BYTES + " bytes: " + head(tooBig));
  }

  @Test
  void sitemapListsEveryPage() throws IOException {
    Set<String> expected = new TreeSet<>();
    for (Path page : htmlPages(first)) {
      String dir = first.relativize(page.getParent()).toString().replace('\\', '/');
      expected.add(BASE_URL + (dir.isEmpty() ? "" : dir + "/"));
    }

    Set<String> listed = new LinkedHashSet<>();
    Matcher matcher = SITEMAP_LOC.matcher(read(first.resolve("sitemap.xml")));
    while (matcher.find()) {
      listed.add(matcher.group(1));
    }

    Set<String> missing = new TreeSet<>(expected);
    missing.removeAll(listed);
    assertTrue(missing.isEmpty(), "pages missing from sitemap.xml: " + head(missing));

    Set<String> extra = new TreeSet<>(listed);
    extra.removeAll(expected);
    assertTrue(extra.isEmpty(), "sitemap.xml lists URLs with no page: " + head(extra));

    String robots = read(first.resolve("robots.txt"));
    assertTrue(robots.contains("Sitemap: " + BASE_URL + "sitemap.xml"), "robots.txt sitemap line");
  }

  @Test
  void outputIsByteIdenticalAcrossRuns() throws IOException {
    assertEquals(0, runSite(second), "second site run failed");

    List<String> firstFiles = relativeFiles(first);
    List<String> secondFiles = relativeFiles(second);
    assertEquals(firstFiles, secondFiles, "the two runs wrote different file sets");

    List<String> differing = new ArrayList<>();
    for (String relative : firstFiles) {
      if (Files.mismatch(first.resolve(relative), second.resolve(relative)) != -1L) {
        differing.add(relative);
      }
    }
    assertTrue(differing.isEmpty(), "files differ between two identical runs: " + head(differing));
    assertFalse(firstFiles.isEmpty(), "expected the site run to write files");
  }

  private static List<Path> htmlPages(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".html"))
          .sorted()
          .toList();
    }
  }

  private static List<String> relativeFiles(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .map(path -> root.relativize(path).toString().replace('\\', '/'))
          .sorted()
          .toList();
    }
  }

  /** Drops the fragment and query, leaving the path a link checker can resolve on disk. */
  private static String strip(String href) {
    String path = href;
    int hash = path.indexOf('#');
    if (hash >= 0) {
      path = path.substring(0, hash);
    }
    int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }
    return path;
  }

  private static boolean isExternal(String href) {
    return href.startsWith("//") || href.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
  }

  private static String read(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  /** The SEO closure table, so a date assertion is not satisfied by the month grid above it. */
  private static String eventsTable(String html) {
    int start = html.indexOf("<table class=\"events\">");
    assertTrue(start >= 0, "expected a closure table on the page");
    int end = html.indexOf("</table>", start);
    assertTrue(end > start, "expected the closure table to be closed");
    return html.substring(start, end);
  }

  /** The table row containing {@code needle}, so assertions can be scoped to one date. */
  private static String rowContaining(String html, String needle) {
    int index = html.indexOf(needle);
    assertTrue(index >= 0, "expected the page to mention " + needle);
    int start = html.lastIndexOf("<tr>", index);
    int end = html.indexOf("</tr>", index);
    assertTrue(start >= 0 && end > start, "expected " + needle + " to sit in a table row");
    return html.substring(start, end);
  }

  private static String head(java.util.Collection<String> values) {
    return values.stream().limit(10).toList() + " (" + values.size() + " total)";
  }
}
