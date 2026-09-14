package com.bdc.site;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the source register: {@code /sources/index.html} (every market directory and its citation
 * rows) and {@code /sources/<ID>/index.html} (that directory's README, rendered).
 *
 * <p>These pages exist so a citation can be <em>linked</em>. Before them, a market page could say
 * that a closure cites {@code euronext-hours-holidays} but the only way to see what that is was to
 * open the repository. Every row rendered here carries the citation id as its {@code id} attribute,
 * which is what makes {@code /sources/EU-EURONEXT/#euronext-hours-holidays} land on the row.
 */
public final class SourcesPageRenderer {

  private static final HtmlTemplate INDEX =
      HtmlTemplate.of(
          """
          <h1>Sources</h1>
          <p class="lede">Every closure in this dataset is transcribed from an authoritative
          document — an exchange notice, a gazette, a rule-book circular — and cites it by id. This
          is the register of those documents, one section per market directory in
          <a href="{{repoSourcesUrl}}"><code>sources/</code></a>.</p>
          {{{sections}}}
          """);

  private static final HtmlTemplate DETAIL =
      HtmlTemplate.of(
          """
          <h1>{{id}} sources</h1>
          <p class="lede">The source register for <code>{{id}}</code>, rendered from
          <a href="{{repoUrl}}"><code>sources/{{id}}/README.md</code></a>. Each row is linkable by
          its citation id.</p>
          <div class="markdown">
          {{{body}}}
          </div>
          """);

  private final SiteContext context;
  private final PageLayout layout;

  public SourcesPageRenderer(SiteContext context, PageLayout layout) {
    this.context = context;
    this.layout = layout;
  }

  /** Writes the index and one page per market directory; returns the paths written. */
  public List<String> write(SourceRegistry registry, Path siteDir) throws IOException {
    List<String> written = new ArrayList<>();
    Path dir = siteDir.resolve("sources");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("index.html"), renderIndex(registry));
    written.add("sources/");
    for (SourceRegistry.Market market : registry.markets()) {
      Path marketDir = dir.resolve(market.id());
      Files.createDirectories(marketDir);
      Files.writeString(marketDir.resolve("index.html"), renderMarket(market));
      written.add("sources/" + market.id() + "/");
    }
    return written;
  }

  String renderIndex(SourceRegistry registry) {
    StringBuilder sections = new StringBuilder(16 * 1024);
    if (registry.markets().isEmpty()) {
      sections.append("<p>No source register has been published yet.</p>\n");
    }
    for (SourceRegistry.Market market : registry.markets()) {
      sections
          .append("<section aria-labelledby=\"")
          .append(HtmlTemplate.escape(market.id()))
          .append("-heading\">\n<h2 id=\"")
          .append(HtmlTemplate.escape(market.id()))
          .append("-heading\"><a href=\"")
          .append(HtmlTemplate.escape(market.id()))
          .append("/index.html\">")
          .append(HtmlTemplate.escape(market.id()))
          .append("</a></h2>\n")
          .append(citationTable(market))
          .append("</section>\n");
    }
    return layout.render(
        1,
        "sources/",
        "Sources",
        "The register of exchange notices, gazettes and circulars every closure in these trading"
            + " calendars is transcribed from.",
        List.of(
            new PageLayout.Crumb("Markets", "../index.html"),
            new PageLayout.Crumb("Sources", null)),
        "",
        INDEX.render(
            "repoSourcesUrl", context.repoTree("sources"), "sections", sections.toString()));
  }

  /**
   * The citation rows of one register, trimmed to the columns that identify a document. The full
   * row — including the modelling notes, which are the longest cell by far — is on the detail page;
   * repeating them here would make the index the heaviest page on the site for no gain.
   */
  private static String citationTable(SourceRegistry.Market market) {
    if (market.rows().isEmpty()) {
      return "<p class=\"muted\">This register documents no citations in a table.</p>\n";
    }
    List<String> headers = market.headers();
    StringBuilder html = new StringBuilder(4096);
    html.append("<div class=\"table-wrap\">\n<table class=\"events\">\n<thead><tr>");
    for (int column = 0; column < Math.min(headers.size(), 3); column++) {
      html.append("<th scope=\"col\">")
          .append(HtmlTemplate.escape(headers.get(column)))
          .append("</th>");
    }
    html.append("</tr></thead>\n<tbody>\n");
    for (List<String> row : market.rows()) {
      String citationId = row.isEmpty() ? null : MarkdownRenderer.rowAnchor(row.get(0));
      html.append("<tr>");
      for (int column = 0; column < Math.min(headers.size(), 3); column++) {
        String cell = column < row.size() ? row.get(column) : "";
        html.append("<td>");
        if (column == 0 && citationId != null) {
          html.append("<a href=\"")
              .append(HtmlTemplate.escape(market.id()))
              .append("/index.html#")
              .append(HtmlTemplate.escape(citationId))
              .append("\"><code>")
              .append(HtmlTemplate.escape(citationId))
              .append("</code></a>");
        } else {
          html.append(MarkdownRenderer.inline(cell));
        }
        html.append("</td>");
      }
      html.append("</tr>\n");
    }
    html.append("</tbody>\n</table>\n</div>\n");
    return html.toString();
  }

  String renderMarket(SourceRegistry.Market market) {
    // The page supplies its own <h1>, so the README's title line is dropped and everything below it
    // moves one level down: one heading outline per page, not two.
    String body = MarkdownRenderer.render(withoutTitle(market.markdown()), 1);
    return layout.render(
        2,
        "sources/" + market.id() + "/",
        market.id() + " sources",
        "The documents the "
            + market.id()
            + " trading calendar is transcribed from, with the"
            + " citation id each closure refers to.",
        List.of(
            new PageLayout.Crumb("Markets", "../../index.html"),
            new PageLayout.Crumb("Sources", "../index.html"),
            new PageLayout.Crumb(market.id(), null)),
        "",
        DETAIL.render(
            "id", market.id(),
            "repoUrl", context.repoBlob("sources/" + market.id() + "/README.md"),
            "body", body));
  }

  /** Drops a leading level-1 heading, which the page renders itself. */
  private static String withoutTitle(String markdown) {
    String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
    int start = 0;
    while (start < lines.length && lines[start].isBlank()) {
      start++;
    }
    if (start < lines.length && lines[start].strip().matches("^#\\s+.*")) {
      start++;
    }
    return String.join("\n", List.of(lines).subList(Math.min(start, lines.length), lines.length));
  }
}
