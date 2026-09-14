package com.bdc.site;

import com.bdc.diff.CalendarDiff;
import com.bdc.diff.EventDiff;
import com.bdc.site.CalendarData.DayEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders {@code /<ID>/<year>/index.html} — the page the site exists for. "NYSE holidays 2027"
 * should find this page, so the {@code <h1>}, the meta description and the JSON-LD {@code ItemList}
 * all say exactly that, and the full closure table is in the HTML rather than fetched at runtime.
 */
public final class YearPageRenderer {

  static final DateTimeFormatter LONG_DATE =
      DateTimeFormatter.ofPattern("EEE d MMM uuuu", Locale.ENGLISH);

  private static final HtmlTemplate BODY =
      HtmlTemplate.of(
          """
          <h1>{{name}} holidays {{year}}</h1>
          <p class="lede">{{lede}}</p>
          {{{diffBanner}}}
          {{{yearNav}}}
          <section class="grid-section" aria-labelledby="grid-heading">
            <h2 id="grid-heading">{{year}} at a glance</h2>
            {{{legend}}}
            {{{grid}}}
          </section>
          <section aria-labelledby="table-heading">
            <h2 id="table-heading">Closures and early closes in {{year}}</h2>
            {{{table}}}
          </section>
          <section class="downloads" aria-labelledby="data-heading">
            <h2 id="data-heading">This year as data</h2>
            <ul>
              <li><a href="{{root}}v1/calendars/{{id}}/{{year}}.json">{{year}}.json</a> — every row for {{year}}, including weekends</li>
              <li><a href="{{root}}v1/calendars/{{id}}/holidays.json">holidays.json</a> — every closure and early close, all years</li>
              <li><a href="{{root}}v1/calendars/{{id}}/holidays-recent.ics">holidays-recent.ics</a> — iCalendar feed, 2020 onward</li>
              <li><a href="{{marketHref}}">All {{name}} years</a></li>
            </ul>
          </section>
          """);

  private final SiteContext context;
  private final PageLayout layout;
  private final YearGridRenderer gridRenderer;
  private final Map<String, CalendarDiff> diffs;
  private final ObjectMapper mapper = new ObjectMapper();

  public YearPageRenderer(SiteContext context, PageLayout layout, YearGridRenderer gridRenderer) {
    this(context, layout, gridRenderer, Map.of());
  }

  public YearPageRenderer(
      SiteContext context,
      PageLayout layout,
      YearGridRenderer gridRenderer,
      Map<String, CalendarDiff> diffs) {
    this.context = context;
    this.layout = layout;
    this.gridRenderer = gridRenderer;
    this.diffs = diffs;
  }

  /** Writes every year page for one calendar and returns their site-relative directories. */
  public List<String> writeAll(CalendarData calendar, Path siteDir) throws IOException {
    List<String> paths = new ArrayList<>();
    List<Integer> years = calendar.years();
    for (int i = 0; i < years.size(); i++) {
      int year = years.get(i);
      Integer previous = i > 0 ? years.get(i - 1) : null;
      Integer next = i + 1 < years.size() ? years.get(i + 1) : null;
      Path dir = siteDir.resolve(calendar.id()).resolve(String.valueOf(year));
      Files.createDirectories(dir);
      Files.writeString(dir.resolve("index.html"), render(calendar, year, previous, next));
      paths.add(calendar.id() + "/" + year + "/");
    }
    return paths;
  }

  String render(CalendarData calendar, int year, Integer previous, Integer next) {
    List<DayEvent> events = calendar.nonWeekendEventsIn(year);
    long closures = events.stream().filter(DayEvent::isClosed).count();
    long earlyCloses = events.stream().filter(DayEvent::isEarlyClose).count();

    String path = calendar.id() + "/" + year + "/";
    String title = calendar.name() + " holidays " + year;
    String description =
        calendar.name()
            + " ("
            + calendar.id()
            + ") is closed on "
            + closures
            + (closures == 1 ? " day" : " days")
            + " in "
            + year
            + (earlyCloses > 0 ? " and closes early on " + earlyCloses + " more" : "")
            + ". Full dates, early-close times and sources.";

    String head =
        new StringBuilder()
            .append(prevNextLink("prev", previous))
            .append(prevNextLink("next", next))
            .append(jsonLd(calendar, year, events))
            .toString();

    String root = SiteContext.rootPrefix(2);
    String body =
        BODY.render(
            "name", calendar.name(),
            "year", String.valueOf(year),
            "id", calendar.id(),
            "lede", lede(calendar, year, closures, earlyCloses),
            "diffBanner", diffBanner(calendar, year, root),
            "yearNav", yearNav(calendar, year, previous, next),
            "legend", gridRenderer.renderLegend(),
            "grid", gridRenderer.render(calendar, year, "../"),
            "table", table(calendar, events),
            "root", root,
            "marketHref", "../index.html");

    return layout.render(
        2,
        path,
        title,
        description,
        List.of(
            new PageLayout.Crumb("Markets", "../../index.html"),
            new PageLayout.Crumb(calendar.id(), "../index.html"),
            new PageLayout.Crumb(String.valueOf(year), null)),
        head,
        body);
  }

  private String lede(CalendarData calendar, int year, long closures, long earlyCloses) {
    StringBuilder text = new StringBuilder();
    text.append(calendar.name())
        .append(" (")
        .append(calendar.id())
        .append(") observes ")
        .append(closures)
        .append(closures == 1 ? " full-day closure" : " full-day closures");
    if (earlyCloses > 0) {
      text.append(" and ")
          .append(earlyCloses)
          .append(earlyCloses == 1 ? " early close" : " early closes");
    }
    text.append(" in ").append(year).append(".");
    LocalDate verified = calendar.coverage().verifiedThrough();
    if (verified != null && verified.getYear() < year) {
      text.append(" Dates after ")
          .append(verified)
          .append(" are projected from the published rules and not yet confirmed by the exchange.");
    }
    return text.toString();
  }

  /**
   * The "Changes vs blessed" banner for this year page, scoped to the changes whose date falls in
   * {@code year}. Empty when the calendar has no {@code --compare-to} diff, or the diff has nothing
   * in this year.
   */
  private String diffBanner(CalendarData calendar, int year, String root) {
    CalendarDiff diff = diffs.get(calendar.id());
    if (diff == null) {
      return "";
    }
    int added = countInYear(diff.additions(), year);
    int removed = countInYear(diff.removals(), year);
    int modified = countInYear(diff.modifications(), year);
    return ChangesRenderer.banner(diff, added, removed, modified, root + "changes/index.html");
  }

  private static int countInYear(List<EventDiff> diffs, int year) {
    return (int) diffs.stream().filter(d -> d.date().getYear() == year).count();
  }

  private String yearNav(CalendarData calendar, int year, Integer previous, Integer next) {
    StringBuilder html = new StringBuilder();
    html.append("<nav class=\"year-nav\" aria-label=\"Year\">");
    if (previous != null) {
      html.append("<a class=\"step prev\" rel=\"prev\" href=\"../")
          .append(previous)
          .append("/index.html\">&larr; ")
          .append(previous)
          .append("</a>");
    } else {
      html.append("<span class=\"step disabled\"></span>");
    }
    html.append("<span class=\"current\">").append(year).append("</span>");
    if (next != null) {
      html.append("<a class=\"step next\" rel=\"next\" href=\"../")
          .append(next)
          .append("/index.html\">")
          .append(next)
          .append(" &rarr;</a>");
    } else {
      html.append("<span class=\"step disabled\"></span>");
    }
    html.append("</nav>\n");
    html.append(yearLinks(calendar, year, "../"));
    return html.toString();
  }

  /**
   * The complete list of years as links. {@code site.js} collapses this into a {@code <select>};
   * with scripting off it stays a (wrapped, compact) list of links, which is the accessible and
   * crawlable form anyway.
   */
  static String yearLinks(CalendarData calendar, Integer current, String prefix) {
    StringBuilder html = new StringBuilder(4096);
    html.append(
            "<nav class=\"year-picker\" aria-label=\"All years\" data-year-picker data-label=\"")
        .append(HtmlTemplate.escape(calendar.name()))
        .append(" year\">\n<ul>\n");
    for (int year : calendar.years()) {
      html.append("<li><a href=\"")
          .append(prefix)
          .append(year)
          .append("/index.html\"")
          .append(current != null && current == year ? " aria-current=\"page\"" : "")
          .append(">")
          .append(year)
          .append("</a></li>\n");
    }
    html.append("</ul>\n</nav>\n");
    return html.toString();
  }

  private String table(CalendarData calendar, List<DayEvent> events) {
    if (events.isEmpty()) {
      return "<p class=\"empty\">No closures or early closes are recorded for this year.</p>\n";
    }
    StringBuilder html = new StringBuilder(8192);
    html.append("<div class=\"table-wrap\">\n<table class=\"events\">\n");
    html.append(
        """
        <thead><tr>
        <th scope="col">Date</th><th scope="col">Status</th><th scope="col">Holiday</th>
        <th scope="col">Close</th><th scope="col">Observed for</th><th scope="col">Source module</th>
        </tr></thead>
        <tbody>
        """);
    for (DayEvent event : events) {
      boolean projected = calendar.isProjected(event);
      html.append("<tr>");
      html.append("<th scope=\"row\"><a href=\"../")
          .append(event.date())
          .append("/index.html\"><time datetime=\"")
          .append(event.date())
          .append("\">")
          .append(LONG_DATE.format(event.date()))
          .append("</time></a></th>");
      html.append("<td><span class=\"badge ")
          .append(event.isClosed() ? "closed" : "early-close")
          .append("\">")
          .append(event.isClosed() ? "Closed" : "Early close")
          .append("</span>")
          .append(projected ? " <span class=\"badge projected\">Projected</span>" : "")
          .append("</td>");
      html.append("<td>").append(HtmlTemplate.escape(event.description())).append("</td>");
      html.append("<td>")
          .append(event.closeTime() != null ? HtmlTemplate.escape(event.closeTime()) : "&mdash;")
          .append("</td>");
      html.append("<td>");
      if (event.observedFrom() != null) {
        html.append("<time datetime=\"")
            .append(event.observedFrom())
            .append("\">")
            .append(LONG_DATE.format(event.observedFrom()))
            .append("</time>");
      } else {
        html.append("&mdash;");
      }
      html.append("</td>");
      html.append("<td><code>")
          .append(HtmlTemplate.escape(event.sourceModule()))
          .append("</code></td>");
      html.append("</tr>\n");
    }
    html.append("</tbody>\n</table>\n</div>\n");
    return html.toString();
  }

  private String prevNextLink(String rel, Integer year) {
    return year == null ? "" : "<link rel=\"" + rel + "\" href=\"../" + year + "/index.html\">\n";
  }

  /** A JSON-LD {@code ItemList} of the year's closures, for rich results. */
  private String jsonLd(CalendarData calendar, int year, List<DayEvent> events) {
    List<Map<String, Object>> items = new ArrayList<>();
    int position = 1;
    for (DayEvent event : events) {
      if (!event.isClosed()) {
        continue;
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("@type", "ListItem");
      item.put("position", position++);
      item.put("name", event.description());
      item.put("url", context.canonical(calendar.id() + "/" + event.date() + "/"));
      items.add(item);
    }
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("@context", "https://schema.org");
    root.put("@type", "ItemList");
    root.put("name", calendar.name() + " holidays " + year);
    root.put("numberOfItems", items.size());
    root.put("itemListElement", items);
    try {
      String json = mapper.writeValueAsString(root).replace("<", "\\u003C");
      return "<script type=\"application/ld+json\">" + json + "</script>\n";
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize JSON-LD for " + calendar.id(), e);
    }
  }
}
