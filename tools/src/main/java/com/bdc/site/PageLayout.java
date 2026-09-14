package com.bdc.site;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The one page shell every HTML file on the site is poured into: {@code <head>}, skip link,
 * masthead nav, breadcrumbs, {@code <main>} and footer.
 *
 * <p>Pages are complete without JavaScript; {@code site.js} is loaded with {@code defer} and only
 * upgrades a year {@code <select>} and the copy-link buttons.
 */
public final class PageLayout {

  private static final DateTimeFormatter FOOTER_DATE =
      DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH).withZone(ZoneOffset.UTC);

  private static final HtmlTemplate DOCUMENT =
      HtmlTemplate.of(
          """
          <!doctype html>
          <html lang="en">
          <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>{{title}}</title>
          <meta name="description" content="{{description}}">
          <link rel="canonical" href="{{canonical}}">
          <link rel="stylesheet" href="{{root}}styles.css">
          {{{head}}}
          </head>
          <body>
          <a class="skip-link" href="#main">Skip to content</a>
          <header class="masthead">
            <a class="wordmark" href="{{root}}index.html">{{siteName}}</a>
            <nav aria-label="Site">
              <a href="{{root}}index.html">Markets</a>
              <a href="{{root}}changelog/index.html">Changelog</a>
              <a href="{{sourcesUrl}}">Sources</a>
              <a href="{{root}}v1/index.json">API</a>
              <a href="{{repoUrl}}">GitHub</a>
            </nav>
          </header>
          {{{breadcrumbs}}}
          <main id="main">
          {{{main}}}
          </main>
          <footer class="site-footer">
            <p>Generated from release <strong>v{{release}}</strong> ({{releaseDate}}) on {{builtOn}}.
            Calendar data is published under the repository's data licence; every closure cites a
            source.</p>
            <p><a href="{{root}}v1/index.json">JSON API v1</a> &middot;
            <a href="{{root}}changelog/index.html">Changelog</a> &middot;
            <a href="{{contributingUrl}}">Contributing</a> &middot;
            <a href="{{root}}sitemap.xml">Sitemap</a></p>
          </footer>
          <script src="{{root}}site.js" defer></script>
          </body>
          </html>
          """);

  private final SiteContext context;

  public PageLayout(SiteContext context) {
    this.context = context;
  }

  /** One breadcrumb step: a label, and a relative href (null for the current page). */
  public record Crumb(String label, String href) {}

  /**
   * Renders a full page.
   *
   * @param depth how many directory levels below the site root this page lives
   * @param path the site-relative path of the page directory, e.g. {@code "US-NYSE/2027/"}
   * @param head extra raw markup for {@code <head>} (JSON-LD, prev/next links); may be empty
   * @param main the raw page body
   */
  public String render(
      int depth,
      String path,
      String title,
      String description,
      List<Crumb> crumbs,
      String head,
      String main) {
    String root = SiteContext.rootPrefix(depth);
    return DOCUMENT.render(
        "title", title,
        "description", description,
        "canonical", context.canonical(path),
        "root", root,
        "head", head,
        "siteName", context.siteName(),
        "sourcesUrl", context.repoTree("sources"),
        "repoUrl", context.repoUrl(),
        "contributingUrl", context.repoBlob("CONTRIBUTING.md"),
        "breadcrumbs", breadcrumbs(crumbs),
        "main", main,
        "release", context.releaseVersion(),
        "releaseDate", context.releaseDate(),
        "builtOn", FOOTER_DATE.format(context.generatedAt()));
  }

  private static String breadcrumbs(List<Crumb> crumbs) {
    if (crumbs == null || crumbs.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder("<nav class=\"breadcrumbs\" aria-label=\"Breadcrumb\">");
    html.append("<ol>");
    for (Crumb crumb : crumbs) {
      html.append("<li>");
      if (crumb.href() == null) {
        html.append("<span aria-current=\"page\">")
            .append(HtmlTemplate.escape(crumb.label()))
            .append("</span>");
      } else {
        html.append("<a href=\"")
            .append(HtmlTemplate.escape(crumb.href()))
            .append("\">")
            .append(HtmlTemplate.escape(crumb.label()))
            .append("</a>");
      }
      html.append("</li>");
    }
    html.append("</ol></nav>");
    return html.toString();
  }
}
