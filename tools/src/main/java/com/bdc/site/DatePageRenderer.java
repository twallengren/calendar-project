package com.bdc.site;

import com.bdc.diff.CalendarDiff;
import com.bdc.diff.EventDiff;
import com.bdc.site.CalendarData.DayEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders {@code /<ID>/<date>/index.html} — one permalink per non-weekend event date, so "is the
 * NYSE open on 5 July 2027?" has an answer with its own shareable URL.
 *
 * <p>The previous/next business-day links are derived from the published rows (a business day is a
 * date with no {@code WEEKEND} and no {@code CLOSED} row), never from re-running weekend or shift
 * policy.
 */
public final class DatePageRenderer {

  private static final HtmlTemplate BODY =
      HtmlTemplate.of(
          """
          <h1>{{name}} on {{longDate}}</h1>
          <p class="verdict {{verdictClass}}">{{verdict}}</p>
          {{{diffBanner}}}
          {{{events}}}
          {{{assessment}}}
          <section aria-labelledby="around-heading">
            <h2 id="around-heading">Around this date</h2>
            {{{around}}}
          </section>
          <section class="downloads" aria-labelledby="data-heading">
            <h2 id="data-heading">This date as data</h2>
            <ul>
              <li><a href="{{root}}v1/calendars/{{id}}/{{year}}.json">{{year}}.json</a> — every {{year}} row for {{id}}</li>
              <li><a href="{{root}}v1/calendars/{{id}}/holidays.json">holidays.json</a> — every closure and early close</li>
              <li><a href="../index.html">{{name}} calendar</a></li>
            </ul>
          </section>
          """);

  private final PageLayout layout;
  private final Map<String, CalendarDiff> diffs;

  public DatePageRenderer(PageLayout layout) {
    this(layout, Map.of());
  }

  public DatePageRenderer(PageLayout layout, Map<String, CalendarDiff> diffs) {
    this.layout = layout;
    this.diffs = diffs;
  }

  /** Writes one page per non-weekend event date; returns their site-relative directories. */
  public List<String> writeAll(CalendarData calendar, Path siteDir) throws IOException {
    List<String> paths = new ArrayList<>();
    for (Map.Entry<LocalDate, List<DayEvent>> entry :
        calendar.nonWeekendEventsByDate().entrySet()) {
      LocalDate date = entry.getKey();
      Path dir = siteDir.resolve(calendar.id()).resolve(date.toString());
      Files.createDirectories(dir);
      Files.writeString(dir.resolve("index.html"), render(calendar, date, entry.getValue()));
      paths.add(calendar.id() + "/" + date + "/");
    }
    return paths;
  }

  String render(CalendarData calendar, LocalDate date, List<DayEvent> events) {
    String longDate = YearPageRenderer.LONG_DATE.format(date);
    boolean closed = events.stream().anyMatch(DayEvent::isClosed);
    boolean earlyClose = events.stream().anyMatch(DayEvent::isEarlyClose);
    String headline = events.get(0).description();

    String verdict;
    String verdictClass;
    if (closed) {
      verdict = calendar.name() + " is closed on " + longDate + " for " + headline + ".";
      verdictClass = "closed";
    } else if (earlyClose) {
      String closeTime =
          events.stream()
              .filter(DayEvent::isEarlyClose)
              .map(DayEvent::closeTime)
              .filter(t -> t != null)
              .findFirst()
              .orElse(null);
      verdict =
          calendar.name()
              + " is open on "
              + longDate
              + " but closes early"
              + (closeTime != null ? " at " + closeTime : "")
              + " for "
              + headline
              + ".";
      verdictClass = "early-close";
    } else {
      verdict = calendar.name() + " has a recorded event on " + longDate + ": " + headline + ".";
      verdictClass = "open";
    }

    if (calendar.isUnknown(date)) {
      verdict =
          "Actual trading state is unknown on "
              + longDate
              + ". Scheduled events are listed below; coverage is incomplete.";
      verdictClass = "unknown";
    }
    String description =
        verdict
            + " Source module, observance rule and the surrounding business days for "
            + calendar.id()
            + ".";

    String root = SiteContext.rootPrefix(2);
    String body =
        BODY.render(
            "name", calendar.name(),
            "longDate", longDate,
            "verdict", verdict,
            "verdictClass", verdictClass,
            "diffBanner", diffBanner(calendar, date, root),
            "events", eventTable(calendar, events),
            "assessment", assessment(calendar, date),
            "around", around(calendar, date),
            "root", root,
            "id", calendar.id(),
            "year", String.valueOf(date.getYear()));

    return layout.render(
        2,
        calendar.id() + "/" + date + "/",
        calendar.name() + ": " + headline + ", " + longDate,
        description,
        List.of(
            new PageLayout.Crumb("Markets", "../../index.html"),
            new PageLayout.Crumb(calendar.id(), "../index.html"),
            new PageLayout.Crumb(
                String.valueOf(date.getYear()), "../" + date.getYear() + "/index.html"),
            new PageLayout.Crumb(date.toString(), null)),
        "",
        body);
  }

  /**
   * The "Changes vs blessed" banner for this date page, scoped to changes on exactly {@code date}.
   * Empty when the calendar has no {@code --compare-to} diff, or the diff has nothing on this date.
   */
  private String diffBanner(CalendarData calendar, LocalDate date, String root) {
    CalendarDiff diff = diffs.get(calendar.id());
    if (diff == null) {
      return "";
    }
    int added = countOnDate(diff.additions(), date);
    int removed = countOnDate(diff.removals(), date);
    int modified = countOnDate(diff.modifications(), date);
    return ChangesRenderer.banner(diff, added, removed, modified, root + "changes/index.html");
  }

  private static int countOnDate(List<EventDiff> diffs, LocalDate date) {
    return (int) diffs.stream().filter(d -> d.date().equals(date)).count();
  }

  private String eventTable(CalendarData calendar, List<DayEvent> events) {
    StringBuilder html = new StringBuilder();
    html.append("<div class=\"table-wrap\">\n<table class=\"events\">\n");
    html.append(
        """
        <thead><tr>
        <th scope="col">Event</th><th scope="col">Status</th><th scope="col">Close</th>
        <th scope="col">Observed for</th><th scope="col">Source module</th>
        </tr></thead>
        <tbody>
        """);
    for (DayEvent event : events) {
      boolean projected = calendar.isProjected(event);
      html.append("<tr>");
      html.append("<th scope=\"row\">")
          .append(HtmlTemplate.escape(event.description()))
          .append("</th>");
      html.append("<td><span class=\"badge ")
          .append(event.isClosed() ? "closed" : "early-close")
          .append("\">")
          .append(event.isClosed() ? "Closed" : "Early close")
          .append("</span>")
          .append(projected ? " <span class=\"badge projected\">Projected</span>" : "")
          .append("</td>");
      html.append("<td>")
          .append(event.closeTime() != null ? HtmlTemplate.escape(event.closeTime()) : "&mdash;")
          .append("</td>");
      html.append("<td>");
      if (event.observedFrom() != null) {
        html.append("<time datetime=\"")
            .append(event.observedFrom())
            .append("\">")
            .append(YearPageRenderer.LONG_DATE.format(event.observedFrom()))
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

  private String assessment(CalendarData calendar, LocalDate date) {
    var day = calendar.assessments().get(date);
    if (day == null) return "";
    StringBuilder html =
        new StringBuilder(
            "<section><h2>Confidence and native origins</h2><p>Effective confidence: ");
    html.append(HtmlTemplate.escape(day.path("effective_confidence").asText()))
        .append(". Scheduled state: ")
        .append(HtmlTemplate.escape(day.path("scheduled_state").asText()))
        .append(".</p><ul>");
    day.path("completeness")
        .fields()
        .forEachRemaining(
            field ->
                html.append("<li>")
                    .append(HtmlTemplate.escape(field.getKey()))
                    .append(": ")
                    .append(HtmlTemplate.escape(field.getValue().asText()))
                    .append("</li>"));
    html.append("</ul>");
    for (var event : day.path("events")) {
      var nativeDate = event.path("nominal_native_date");
      if (nativeDate.isNull() || nativeDate.isMissingNode()) continue;
      html.append("<p>")
          .append(HtmlTemplate.escape(event.path("description").asText()))
          .append(": <code>")
          .append(HtmlTemplate.escape(nativeDate.path("chronology_id").asText()))
          .append(" ")
          .append(nativeDate.path("year").asInt())
          .append(" ")
          .append(HtmlTemplate.escape(nativeDate.path("month_code").asText()))
          .append(" ")
          .append(nativeDate.path("day").asInt())
          .append("</code> → ")
          .append(date)
          .append(". Profile: ")
          .append(HtmlTemplate.escape(event.path("chronology_profile").asText()))
          .append(" (")
          .append(HtmlTemplate.escape(event.path("chronology_provider").asText()))
          .append("). Evidence: ");
      List<String> evidence = new ArrayList<>();
      event.path("evidence_ids").forEach(id -> evidence.add(id.asText()));
      html.append(HtmlTemplate.escape(String.join(", ", evidence))).append(".</p>");
    }
    return html.append("<p><a href=\"../../v2/calendars/")
        .append(HtmlTemplate.escape(calendar.id()))
        .append("/")
        .append(date.getYear())
        .append(".json\">Full assessment and provenance as JSON</a></p></section>")
        .toString();
  }

  private String around(CalendarData calendar, LocalDate date) {
    LocalDate previous = calendar.adjacentBusinessDay(date, -1);
    LocalDate next = calendar.adjacentBusinessDay(date, 1);
    StringBuilder html = new StringBuilder();
    html.append("<nav class=\"around\" aria-label=\"Nearby business days\">\n");
    html.append(businessDayLink(calendar, previous, "Previous business day", "prev"));
    html.append("<a class=\"step year\" href=\"../")
        .append(date.getYear())
        .append("/index.html#d")
        .append(date)
        .append("\">All ")
        .append(date.getYear())
        .append(" dates</a>\n");
    html.append(businessDayLink(calendar, next, "Next business day", "next"));
    html.append("</nav>\n");
    return html.toString();
  }

  /**
   * Links to the business day's own permalink when it has one (an early close does), and otherwise
   * to its anchored cell on the year grid — every day of the year has an anchor there.
   */
  private String businessDayLink(
      CalendarData calendar, LocalDate date, String label, String direction) {
    if (date == null) {
      return "<span class=\"step disabled\">"
          + HtmlTemplate.escape(label)
          + ": unresolved or outside coverage</span>\n";
    }
    boolean hasOwnPage =
        calendar.eventsIn(date.getYear()).stream()
            .anyMatch(e -> e.date().equals(date) && !e.isWeekend());
    String href =
        hasOwnPage ? "../" + date + "/index.html" : "../" + date.getYear() + "/index.html#d" + date;
    return "<a class=\"step "
        + direction
        + "\" href=\""
        + href
        + "\"><span class=\"label\">"
        + HtmlTemplate.escape(label)
        + "</span><time datetime=\""
        + date
        + "\">"
        + YearPageRenderer.LONG_DATE.format(date)
        + "</time></a>\n";
  }
}
