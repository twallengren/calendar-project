package com.bdc.site;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders {@code /<ID>/index.html}: what the calendar covers, how far it has been verified, what
 * its weekend policy is (including the effective-dated periods that make NYSE's Saturday history
 * legible), this year's grid, the full year index, and every download.
 */
public final class MarketPageRenderer {

  private static final HtmlTemplate BODY =
      HtmlTemplate.of(
          """
          <h1>{{name}}</h1>
          <p class="lede">{{lede}}</p>
          <section aria-labelledby="facts-heading">
            <h2 id="facts-heading">At a glance</h2>
            {{{facts}}}
          </section>
          <section class="grid-section" aria-labelledby="grid-heading">
            <h2 id="grid-heading">{{currentYear}}</h2>
            <p><a class="cta" href="{{currentYear}}/index.html">See every {{currentYear}} closure, with dates and sources &rarr;</a></p>
            {{{legend}}}
            {{{grid}}}
          </section>
          <section aria-labelledby="years-heading">
            <h2 id="years-heading">All years</h2>
            <p>{{coverageSentence}}</p>
            {{{yearLinks}}}
          </section>
          <section aria-labelledby="weekend-heading">
            <h2 id="weekend-heading">Weekend policy</h2>
            {{{weekend}}}
          </section>
          <section aria-labelledby="sources-heading">
            <h2 id="sources-heading">Sources and cross-validation</h2>
            {{{sources}}}
          </section>
          <section class="downloads" aria-labelledby="downloads-heading">
            <h2 id="downloads-heading">Downloads and API</h2>
            {{{downloads}}}
          </section>
          """);

  private final SiteContext context;
  private final PageLayout layout;
  private final YearGridRenderer gridRenderer;

  public MarketPageRenderer(SiteContext context, PageLayout layout, YearGridRenderer gridRenderer) {
    this.context = context;
    this.layout = layout;
    this.gridRenderer = gridRenderer;
  }

  /** Writes the market page and returns its site-relative directory. */
  public String write(CalendarData calendar, StatusData status, Path siteDir) throws IOException {
    Path dir = siteDir.resolve(calendar.id());
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("index.html"), render(calendar, status));
    return calendar.id() + "/";
  }

  String render(CalendarData calendar, StatusData status) {
    int currentYear = currentYear(calendar);
    String title = calendar.name() + " holidays and trading calendar";
    String description =
        calendar.name()
            + " ("
            + calendar.id()
            + ") holiday calendar: "
            + calendar.closures()
            + " closures and "
            + calendar.earlyCloses()
            + " early closes from "
            + calendar.firstYear()
            + " to "
            + calendar.lastYear()
            + ", with sources and a JSON API.";

    String body =
        BODY.render(
            "name", calendar.name(),
            "lede", description,
            "facts", facts(calendar, status),
            "currentYear", String.valueOf(currentYear),
            "legend", gridRenderer.renderLegend(),
            "grid", gridRenderer.render(calendar, currentYear, ""),
            "coverageSentence",
                "One page per year from "
                    + calendar.firstYear()
                    + " to "
                    + calendar.lastYear()
                    + ", each with a month grid and a dated closure table.",
            "yearLinks", YearPageRenderer.yearLinks(calendar, currentYear, ""),
            "weekend", weekendPolicy(calendar),
            "sources", sources(calendar, status),
            "downloads", downloads(calendar));

    return layout.render(
        1,
        calendar.id() + "/",
        title,
        description,
        List.of(
            new PageLayout.Crumb("Markets", "../index.html"),
            new PageLayout.Crumb(calendar.id(), null)),
        "",
        body);
  }

  /** The year whose grid is embedded: today's year, clamped into the published range. */
  int currentYear(CalendarData calendar) {
    int year = context.generatedAt().atZone(java.time.ZoneOffset.UTC).getYear();
    return Math.max(calendar.firstYear(), Math.min(calendar.lastYear(), year));
  }

  private String facts(CalendarData calendar, StatusData status) {
    StringBuilder html = new StringBuilder();
    html.append("<dl class=\"facts\">\n");
    fact(html, "Calendar id", "<code>" + HtmlTemplate.escape(calendar.id()) + "</code>");
    fact(html, "Timezone", HtmlTemplate.escape(orDash(calendar.timezone())));
    fact(html, "Coverage", calendar.coverage().from() + " to " + calendar.coverage().to());
    fact(
        html,
        "Verified through",
        calendar.coverage().verifiedThrough() != null
            ? "<time datetime=\""
                + calendar.coverage().verifiedThrough()
                + "\">"
                + calendar.coverage().verifiedThrough()
                + "</time>"
            : "&mdash;");
    fact(html, "Full-day closures", String.valueOf(calendar.closures()));
    fact(html, "Early closes", String.valueOf(calendar.earlyCloses()));
    fact(html, "Projected rows", String.valueOf(calendar.projected()));
    fact(html, "Cross-validation", crossValidationSummary(status));
    fact(html, "Release", "v" + HtmlTemplate.escape(context.releaseVersion()));
    fact(
        html,
        "Checksum",
        "<code class=\"checksum\">" + HtmlTemplate.escape(orDash(calendar.checksum())) + "</code>");
    html.append("</dl>\n");
    return html.toString();
  }

  private static void fact(StringBuilder html, String term, String rawValue) {
    html.append("<div><dt>")
        .append(HtmlTemplate.escape(term))
        .append("</dt><dd>")
        .append(rawValue)
        .append("</dd></div>\n");
  }

  static String crossValidationSummary(StatusData status) {
    if (!status.hasCrossValidation()) {
      return "<span class=\"badge none\">none</span>";
    }
    StringBuilder html = new StringBuilder();
    for (StatusData.Reference reference : status.crossValidation()) {
      html.append("<span class=\"badge ")
          .append(reference.ok() ? "ok" : "warn")
          .append("\" title=\"")
          .append(reference.allowlisted())
          .append(" allowlisted, ")
          .append(reference.unexplained())
          .append(" unexplained\">")
          .append(HtmlTemplate.escape(reference.source()))
          .append(": ")
          .append(HtmlTemplate.escape(reference.status()))
          .append("</span> ");
    }
    return html.toString().strip();
  }

  private String weekendPolicy(CalendarData calendar) {
    JsonNode policy = calendar.weekendPolicy();
    if (policy == null || policy.isMissingNode() || policy.isNull()) {
      return "<p>No weekend policy is published for this calendar.</p>\n";
    }
    StringBuilder html = new StringBuilder();
    List<String> days = days(policy.path("days"));
    if (!days.isEmpty()) {
      html.append("<p>Today the market is closed every ")
          .append(HtmlTemplate.escape(join(days)))
          .append(".</p>\n");
    }
    JsonNode periods = policy.path("periods");
    if (periods.isArray() && !periods.isEmpty()) {
      html.append(
          "<p>The weekend has not always been the same. Each period below overrides the default"
              + " for the dates it covers:</p>\n");
      html.append("<div class=\"table-wrap\">\n<table class=\"events\">\n");
      html.append(
          "<thead><tr><th scope=\"col\">From</th><th scope=\"col\">To</th>"
              + "<th scope=\"col\">Non-trading days</th></tr></thead>\n<tbody>\n");
      for (JsonNode period : periods) {
        html.append("<tr><td>")
            .append(dateCell(period.path("from"), "start of coverage"))
            .append("</td><td>")
            .append(dateCell(period.path("to"), "present"))
            .append("</td><td>")
            .append(HtmlTemplate.escape(join(days(period.path("days")))))
            .append("</td></tr>\n");
      }
      html.append("</tbody>\n</table>\n</div>\n");
    }
    return html.toString();
  }

  private static String dateCell(JsonNode node, String fallback) {
    if (node.isMissingNode() || node.isNull()) {
      return "<span class=\"muted\">" + HtmlTemplate.escape(fallback) + "</span>";
    }
    String date = node.asText();
    return "<time datetime=\""
        + HtmlTemplate.escape(date)
        + "\">"
        + HtmlTemplate.escape(date)
        + "</time>";
  }

  private static List<String> days(JsonNode node) {
    List<String> days = new ArrayList<>();
    if (node.isArray()) {
      for (JsonNode day : node) {
        String name = day.asText();
        days.add(
            name.isEmpty() ? name : name.charAt(0) + name.substring(1).toLowerCase(Locale.ENGLISH));
      }
    }
    return days;
  }

  private static String join(List<String> values) {
    if (values.isEmpty()) {
      return "";
    }
    if (values.size() == 1) {
      return values.get(0);
    }
    return String.join(", ", values.subList(0, values.size() - 1))
        + " and "
        + values.get(values.size() - 1);
  }

  private String sources(CalendarData calendar, StatusData status) {
    StringBuilder html = new StringBuilder();
    if (status.sourceIds().isEmpty()) {
      html.append("<p>No source register is published for this calendar yet.</p>\n");
    } else {
      html.append("<p>Every closure on this calendar cites one of ")
          .append(status.sourceIds().size())
          .append(" registered sources:</p>\n<ul class=\"source-ids\">\n");
      for (String id : status.sourceIds()) {
        html.append("<li><code>").append(HtmlTemplate.escape(id)).append("</code></li>\n");
      }
      html.append("</ul>\n");
    }
    html.append("<p><a href=\"")
        .append(HtmlTemplate.escape(context.repoTree("sources/" + calendar.id())))
        .append("\">Source register for ")
        .append(HtmlTemplate.escape(calendar.id()))
        .append("</a></p>\n");
    if (status.hasCrossValidation()) {
      html.append("<p>Cross-validated against ")
          .append(status.crossValidation().size())
          .append(status.crossValidation().size() == 1 ? " reference" : " references")
          .append(": ")
          .append(crossValidationSummary(status))
          .append("</p>\n");
    }
    return html.toString();
  }

  private String downloads(CalendarData calendar) {
    String root = SiteContext.rootPrefix(1);
    String api = root + "v1/calendars/" + calendar.id() + "/";
    StringBuilder html = new StringBuilder();
    html.append("<ul>\n");
    html.append("<li><a href=\"")
        .append(api)
        .append("holidays.json\">holidays.json</a> — every closure and early close</li>\n");
    html.append("<li><a href=\"")
        .append(api)
        .append("all.json\">all.json</a> — every row, weekends included</li>\n");
    html.append("<li><a href=\"")
        .append(api)
        .append("manifest.json\">manifest.json</a> — metadata, weekend policy and links</li>\n");
    html.append("<li><a href=\"")
        .append(api)
        .append("holidays.ics\">holidays.ics</a> — iCalendar, every year</li>\n");
    String webcal = context.webcal("v1/calendars/" + calendar.id() + "/holidays-recent.ics");
    if (webcal != null) {
      html.append("<li><a href=\"")
          .append(HtmlTemplate.escape(webcal))
          .append("\">Subscribe in your calendar app</a> — live feed, 2020 onward</li>\n");
    } else {
      html.append("<li><a href=\"")
          .append(api)
          .append("holidays-recent.ics\">holidays-recent.ics</a> — iCalendar, 2020 onward</li>\n");
    }
    html.append("<li><a href=\"")
        .append(HtmlTemplate.escape(context.repoBlob("blessed/" + calendar.id() + "/events.csv")))
        .append("\">events.csv</a> — the blessed CSV release in the repository</li>\n");
    html.append("</ul>\n");
    html.append(
        "<p class=\"muted\">Per-year JSON lives at <code>"
            + HtmlTemplate.escape(api + "{year}.json")
            + "</code>.</p>\n");
    return html.toString();
  }

  private static String orDash(String value) {
    return value == null || value.isEmpty() ? "—" : value;
  }
}
