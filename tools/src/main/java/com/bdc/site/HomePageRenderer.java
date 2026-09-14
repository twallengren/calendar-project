package com.bdc.site;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Renders {@code /index.html}: what this dataset is, and one row per published market with its
 * coverage, verification status, counts, cross-validation summary and every download link.
 */
public final class HomePageRenderer {

  private static final HtmlTemplate BODY =
      HtmlTemplate.of(
          """
          <h1>{{siteName}}</h1>
          <p class="lede">Open, version-controlled trading calendars. Every closure cites an
          authoritative source, every change is tracked between releases, and everything on this
          site is generated from the same artifacts the JSON API serves.</p>
          <section aria-labelledby="markets-heading">
            <h2 id="markets-heading">Markets</h2>
            {{{table}}}
          </section>
          <section aria-labelledby="use-heading">
            <h2 id="use-heading">Using the data</h2>
            <ul>
              <li><a href="v1/index.json">JSON API v1</a> — static, versioned, CORS-free to fetch;
              per-year files live at <code>v1/calendars/{id}/{year}.json</code>.</li>
              <li>iCalendar feeds per market, for subscribing in a calendar app.</li>
              <li><a href="changelog/index.html">Changelog</a> — what changed in every release,
              generated from the release history.</li>
              <li><a href="{{sourcesUrl}}">Sources</a> — the register of gazettes, circulars and
              exchange notices behind each calendar.</li>
              <li><a href="{{contributingUrl}}">Contributing</a> — how to add a market or correct
              a date.</li>
            </ul>
          </section>
          """);

  private final SiteContext context;
  private final PageLayout layout;

  public HomePageRenderer(SiteContext context, PageLayout layout) {
    this.context = context;
    this.layout = layout;
  }

  /** Writes {@code index.html} and returns its site-relative directory. */
  public String write(List<CalendarData> calendars, Map<String, StatusData> status, Path siteDir)
      throws IOException {
    Files.createDirectories(siteDir);
    Files.writeString(siteDir.resolve("index.html"), render(calendars, status));
    return "";
  }

  String render(List<CalendarData> calendars, Map<String, StatusData> status) {
    String description =
        "Open trading-calendar data for "
            + calendars.size()
            + (calendars.size() == 1 ? " market" : " markets")
            + ": holiday dates, early closes and sources, browsable by year and available as JSON"
            + " and iCalendar.";
    String body =
        BODY.render(
            "siteName", context.siteName(),
            "table", table(calendars, status),
            "sourcesUrl", context.repoTree("sources"),
            "contributingUrl", context.repoBlob("CONTRIBUTING.md"));
    return layout.render(0, "", context.siteName(), description, List.of(), "", body);
  }

  private String table(List<CalendarData> calendars, Map<String, StatusData> status) {
    StringBuilder html = new StringBuilder(8192);
    html.append("<div class=\"table-wrap\">\n<table class=\"markets\">\n");
    html.append(
        """
        <thead><tr>
        <th scope="col">Market</th><th scope="col">Timezone</th><th scope="col">Coverage</th>
        <th scope="col">Verified through</th><th scope="col">Closures</th>
        <th scope="col">Early closes</th><th scope="col">Projected</th>
        <th scope="col">Cross-validation</th><th scope="col">Release</th>
        <th scope="col">Data</th>
        </tr></thead>
        <tbody>
        """);
    for (CalendarData calendar : calendars) {
      StatusData calendarStatus = status.getOrDefault(calendar.id(), StatusData.EMPTY);
      html.append("<tr>");
      html.append("<th scope=\"row\"><a href=\"")
          .append(calendar.id())
          .append("/index.html\">")
          .append(HtmlTemplate.escape(calendar.name()))
          .append("</a><br><code>")
          .append(HtmlTemplate.escape(calendar.id()))
          .append("</code></th>");
      html.append("<td>")
          .append(HtmlTemplate.escape(calendar.timezone() == null ? "—" : calendar.timezone()))
          .append("</td>");
      html.append("<td class=\"nowrap\"><time datetime=\"")
          .append(calendar.coverage().from())
          .append("\">")
          .append(calendar.coverage().from())
          .append("</time> to <time datetime=\"")
          .append(calendar.coverage().to())
          .append("\">")
          .append(calendar.coverage().to())
          .append("</time></td>");
      html.append("<td class=\"nowrap\">");
      if (calendar.coverage().verifiedThrough() != null) {
        html.append("<time datetime=\"")
            .append(calendar.coverage().verifiedThrough())
            .append("\">")
            .append(calendar.coverage().verifiedThrough())
            .append("</time>");
      } else {
        html.append("&mdash;");
      }
      html.append("</td>");
      html.append("<td class=\"num\">").append(calendar.closures()).append("</td>");
      html.append("<td class=\"num\">").append(calendar.earlyCloses()).append("</td>");
      html.append("<td class=\"num\">").append(calendar.projected()).append("</td>");
      html.append("<td class=\"nowrap\">")
          .append(compactCrossValidation(calendarStatus))
          .append("</td>");
      html.append("<td class=\"nowrap\">v")
          .append(HtmlTemplate.escape(context.releaseVersion()))
          .append("</td>");
      html.append("<td class=\"links\">").append(dataLinks(calendar)).append("</td>");
      html.append("</tr>\n");
    }
    html.append("</tbody>\n</table>\n</div>\n");
    return html.toString();
  }

  /**
   * A one-badge summary, because ten columns do not leave room for a list of reference names. The
   * per-reference detail (and its allowlist counts) is on the market page; the full source names
   * stay available here in the {@code title}.
   */
  private static String compactCrossValidation(StatusData status) {
    if (!status.hasCrossValidation()) {
      return "<span class=\"badge none\">none</span>";
    }
    long clean = status.crossValidation().stream().filter(StatusData.Reference::ok).count();
    int total = status.crossValidation().size();
    String sources =
        String.join(
            ", ", status.crossValidation().stream().map(StatusData.Reference::source).toList());
    return "<span class=\"badge "
        + (status.allClean() ? "ok" : "warn")
        + "\" title=\""
        + HtmlTemplate.escape(sources)
        + "\">"
        + clean
        + "/"
        + total
        + " clean</span>";
  }

  private String dataLinks(CalendarData calendar) {
    String api = "v1/calendars/" + calendar.id() + "/";
    StringBuilder html = new StringBuilder();
    html.append("<a href=\"").append(api).append("holidays.json\">JSON</a> ");
    html.append("<a href=\"")
        .append(HtmlTemplate.escape(context.repoBlob("blessed/" + calendar.id() + "/events.csv")))
        .append("\">CSV</a> ");
    String webcal = context.webcal(api + "holidays-recent.ics");
    if (webcal != null) {
      html.append("<a href=\"").append(HtmlTemplate.escape(webcal)).append("\">Subscribe</a>");
    } else {
      html.append("<a href=\"").append(api).append("holidays-recent.ics\">iCal</a>");
    }
    return html.toString();
  }
}
