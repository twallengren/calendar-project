package com.bdc.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Checks the source register pages: that a citation row renders with a linkable id, that a venue
 * calendar's citations resolve to the register of the calendar it extends, and that every link the
 * new pages emit lands on a file in the published site.
 */
class SourcesPageTest {

  private static final Pattern LINK = Pattern.compile("(?:href|src)=\"([^\"]+)\"");

  @Test
  void everySourceDirectoryGetsAPage() throws IOException {
    Path sources = GeneratedSite.get().resolve("sources");
    assertTrue(Files.isRegularFile(sources.resolve("index.html")), "sources index");
    for (String id :
        List.of("US-NYSE", "EU-EURONEXT", "SA-TADAWUL", "GB-LSE", "CA-TSX", "DE-XETRA")) {
      assertTrue(
          Files.isRegularFile(sources.resolve(id).resolve("index.html")),
          "a page for sources/" + id + "/");
    }
  }

  @Test
  void aCitationRowRendersWithALinkableId() throws IOException {
    String page = GeneratedSite.read("sources/US-NYSE/index.html");
    assertTrue(
        page.contains("<tr id=\"nyse-history-2008\">"),
        "a citation row carries its id so /sources/US-NYSE/#nyse-history-2008 lands on it");
    assertTrue(
        page.contains("History of New York Stock Exchange Holidays"),
        "the row should carry the document title");
    assertTrue(page.contains("<code>nyse-history-2008</code>"), "the id renders as inline code");
    assertTrue(
        page.contains("<a href=\"https://www.nyse.com/markets/hours-calendars\">"),
        "a bare URL in a cell should be linked");
    assertFalse(page.contains("| id |"), "the pipe table should be rendered, not printed");
  }

  @Test
  void theSourcesIndexListsEveryRegistersRows() throws IOException {
    String index = GeneratedSite.read("sources/index.html");
    assertTrue(
        index.contains("href=\"US-NYSE/index.html#nyse-hours\""),
        "the index should link each citation into its register");
    assertTrue(index.contains("NYSE Holidays &amp; Trading Hours"), "with the document title");
    assertTrue(
        index.contains("<h2 id=\"EU-EURONEXT-heading\">"),
        "one section per sources/ directory, including base calendars");
  }

  /**
   * FR-EURONEXT-PARIS has no {@code sources/} directory of its own: it extends EU-EURONEXT and
   * cites the Euronext notices, which are documented once in the base's register. The market page
   * must link there rather than at a directory that does not exist.
   */
  @Test
  void euronextVenueCitationsResolveToTheBaseRegister() throws IOException {
    for (String venue :
        List.of(
            "FR-EURONEXT-PARIS",
            "NL-EURONEXT-AMSTERDAM",
            "BE-EURONEXT-BRUSSELS",
            "PT-EURONEXT-LISBON")) {
      String page = GeneratedSite.read(venue + "/index.html");
      assertTrue(
          page.contains("href=\"../sources/EU-EURONEXT/index.html#euronext-hours-holidays\""),
          venue + " should link its citation to the EU-EURONEXT register");
      assertTrue(
          page.contains("href=\"../sources/EU-EURONEXT/index.html\">Source register for"),
          venue + " should point at the register it inherits");
      assertFalse(page.contains("sources/" + venue), venue + " has no register of its own");
    }
  }

  @Test
  void aMarketWithItsOwnRegisterLinksItsOwnCitations() throws IOException {
    String page = GeneratedSite.read("US-NYSE/index.html");
    assertTrue(
        page.contains("href=\"../sources/US-NYSE/index.html#nyse-legacy-model-2022\""),
        "US-NYSE cites its own register");
    assertFalse(page.contains("unresolved"), "every US-NYSE citation is documented");
  }

  /** An id no register documents is shown, and marked, rather than linked into nowhere. */
  @Test
  void anUndocumentedCitationIsMarkedUnresolved() throws IOException {
    SourceRegistry registry =
        SourceRegistry.read(Path.of("sources"), Path.of("blessed"), List.of("US-NYSE"));
    List<SourceRegistry.Citation> citations = registry.citationsFor("US-NYSE");
    assertFalse(citations.isEmpty(), "US-NYSE should cite sources");
    for (SourceRegistry.Citation citation : citations) {
      assertTrue(citation.resolved(), citation.id() + " should resolve");
    }

    SourceRegistry.Citation missing = new SourceRegistry.Citation("no-such-source", null, null);
    assertFalse(missing.resolved(), "an id no register documents does not resolve");
    assertNull(registry.registerFor("XX-NOWHERE"), "a calendar with no register resolves to none");
  }

  @Test
  void everyLinkOnTheNewPagesResolvesInTheSite() throws IOException {
    Path site = GeneratedSite.get();
    List<Path> pages = new ArrayList<>();
    for (String directory : List.of("sources", "compare")) {
      try (Stream<Path> files = Files.walk(site.resolve(directory))) {
        files.filter(path -> path.getFileName().toString().endsWith(".html")).forEach(pages::add);
      }
    }
    assertTrue(pages.size() > 40, "expected the compare and sources trees, got " + pages.size());

    List<String> broken = new ArrayList<>();
    int checked = 0;
    for (Path page : pages) {
      Matcher matcher = LINK.matcher(Files.readString(page));
      while (matcher.find()) {
        String href = strip(matcher.group(1));
        if (href.isEmpty()
            || href.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
            || href.startsWith("//")) {
          continue;
        }
        checked++;
        Path target = page.getParent().resolve(href).normalize();
        if (href.endsWith("/") || Files.isDirectory(target)) {
          target = target.resolve("index.html");
        }
        if (!Files.isRegularFile(target)) {
          broken.add(site.relativize(page) + " -> " + href);
        }
      }
    }
    assertTrue(checked > 500, "expected plenty of links to check, got " + checked);
    assertEquals(List.of(), broken, "broken internal links on the compare/sources pages");
  }

  // === The Markdown subset ===

  @Test
  void markdownSubsetRendersTheConstructsRegistersUse() {
    String html =
        MarkdownRenderer.render(
            """
            # Title

            Some **bold** prose with `code`, a [link](https://example.test/a) and a bare
            https://example.test/b, plus an escaped \\_underscore\\_.

            | id | title |
            |----|-------|
            | `a-source` | A &  B |

            - first item
              wrapped onto a second line
            - second item

            ```yaml
            source:
              - id: a-source
            ```
            """);

    assertTrue(html.contains("<h1 id=\"title\">Title</h1>"), "heading with a slug id");
    assertTrue(html.contains("<strong>bold</strong>"), "bold");
    assertTrue(html.contains("<code>code</code>"), "inline code");
    assertTrue(html.contains("<a href=\"https://example.test/a\">link</a>"), "inline link");
    assertTrue(
        html.contains("<a href=\"https://example.test/b\">https://example.test/b</a>"),
        "bare URL autolink, without the trailing comma");
    assertTrue(html.contains("_underscore_"), "backslash escapes are unescaped");
    assertTrue(html.contains("<tr id=\"a-source\">"), "table row anchored on its citation id");
    assertTrue(html.contains("A &amp;  B"), "cell text is HTML-escaped");
    assertTrue(
        html.contains("<li>first item wrapped onto a second line</li>"), "wrapped bullet items");
    assertTrue(html.contains("<pre><code>source:"), "fenced code block");
    assertFalse(html.contains("```"), "the fence itself should not survive");
  }

  @Test
  void markdownNeverEmitsMarkupFromDocumentText() {
    String html = MarkdownRenderer.render("A <script>alert(1)</script> in `<b>code</b>` too.");
    assertFalse(html.contains("<script>"), "document text must not become markup");
    assertTrue(html.contains("&lt;script&gt;"), "it is escaped instead");
    assertTrue(
        html.contains("<code>&lt;b&gt;code&lt;/b&gt;</code>"), "including inside code spans");
  }

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
}
