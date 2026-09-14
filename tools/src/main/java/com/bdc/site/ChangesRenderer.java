package com.bdc.site;

import com.bdc.diff.CalendarDiff;
import com.bdc.diff.DiffSeverity;
import com.bdc.diff.EventDiff;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Renders {@code /changes/index.html} — the contributor-preview diff report produced when {@code
 * tools site} is run with {@code --compare-to <dir>}: every date {@link
 * com.bdc.diff.CalendarDiffEngine} found added, removed or modified between the locally generated
 * calendars and the comparison baseline (normally {@code blessed/}), grouped by calendar, with a
 * link to each date's page.
 *
 * <p>Also supplies the "Changes vs blessed" banner embedded on the affected year and date pages
 * ({@link YearPageRenderer}, {@link DatePageRenderer}) so the wording matches this page exactly.
 */
public final class ChangesRenderer {

  private static final HtmlTemplate BODY =
      HtmlTemplate.of(
          """
          <h1>Changes vs blessed</h1>
          <p class="lede">{{lede}}</p>
          {{{sections}}}
          """);

  private final PageLayout layout;

  public ChangesRenderer(PageLayout layout) {
    this.layout = layout;
  }

  /**
   * The banner embedded on an affected year or date page. Empty when nothing in {@code diff} falls
   * within the page's scope (the page is not "affected" and no banner is shown).
   *
   * @param diff the whole-calendar diff against the comparison baseline
   * @param added count of additions in the scope of this page (a year, or a single date)
   * @param removed count of removals in the scope of this page
   * @param modified count of modifications in the scope of this page
   * @param changesHref site-relative href to {@code changes/index.html} from this page
   */
  public static String banner(
      CalendarDiff diff, int added, int removed, int modified, String changesHref) {
    if (added == 0 && removed == 0 && modified == 0) {
      return "";
    }
    return "<p class=\"diff-banner severity-"
        + diff.severity().name()
        + "\">Changes vs blessed: "
        + added
        + " added, "
        + removed
        + " removed, "
        + modified
        + " modified; severity "
        + diff.severity().name()
        + ". <a href=\""
        + HtmlTemplate.escape(changesHref)
        + "\">See all changes</a>.</p>\n";
  }

  /** Writes {@code changes/index.html} and returns its site-relative directory. */
  public String write(
      Map<String, CalendarDiff> diffs, Map<String, CalendarData> calendars, Path siteDir)
      throws IOException {
    Path dir = siteDir.resolve("changes");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("index.html"), render(diffs, calendars));
    return "changes/";
  }

  String render(Map<String, CalendarDiff> diffs, Map<String, CalendarData> calendars) {
    List<Map.Entry<String, CalendarDiff>> withChanges =
        diffs.entrySet().stream()
            .filter(e -> e.getValue().hasChanges())
            .sorted(Map.Entry.comparingByKey())
            .toList();

    String lede =
        withChanges.isEmpty()
            ? "No differences were found between the locally generated calendars and the"
                + " comparison baseline."
            : "The locally generated output differs from the comparison baseline for "
                + withChanges.size()
                + (withChanges.size() == 1 ? " calendar" : " calendars")
                + ". A "
                + DiffSeverity.MAJOR.name()
                + " change inside a calendar's already-published range needs the"
                + " calendar-change-approved label before merging.";

    StringBuilder sections = new StringBuilder(8192);
    for (Map.Entry<String, CalendarDiff> entry : withChanges) {
      sections.append(
          renderCalendarSection(entry.getKey(), entry.getValue(), calendars.get(entry.getKey())));
    }

    String body = BODY.render("lede", lede, "sections", sections.toString());
    return layout.render(
        1,
        "changes/",
        "Changes vs blessed",
        "Preview diff between the locally generated calendars and the comparison baseline.",
        List.of(
            new PageLayout.Crumb("Markets", "../index.html"),
            new PageLayout.Crumb("Changes", null)),
        "",
        body);
  }

  private String renderCalendarSection(
      String calendarId, CalendarDiff diff, CalendarData calendar) {
    String anchor = "cal-" + calendarId;
    StringBuilder html = new StringBuilder(4096);
    html.append("<section aria-labelledby=\"")
        .append(HtmlTemplate.escape(anchor))
        .append("\">\n<h2 id=\"")
        .append(HtmlTemplate.escape(anchor))
        .append("\">")
        .append(HtmlTemplate.escape(calendarId))
        .append(" <span class=\"severity-")
        .append(diff.severity().name())
        .append("\">")
        .append(diff.severity().name())
        .append("</span></h2>\n");

    if (!diff.removals().isEmpty()) {
      html.append("<h3>Removed</h3>\n")
          .append(table(calendarId, calendar, diff.removals(), Kind.REMOVED));
    }
    if (!diff.additions().isEmpty()) {
      html.append("<h3>Added</h3>\n")
          .append(table(calendarId, calendar, diff.additions(), Kind.ADDED));
    }
    if (!diff.modifications().isEmpty()) {
      html.append("<h3>Modified</h3>\n")
          .append(table(calendarId, calendar, diff.modifications(), Kind.MODIFIED));
    }
    html.append("</section>\n");
    return html.toString();
  }

  private enum Kind {
    ADDED,
    REMOVED,
    MODIFIED
  }

  private String table(String calendarId, CalendarData calendar, List<EventDiff> diffs, Kind kind) {
    StringBuilder html = new StringBuilder(4096);
    html.append("<div class=\"table-wrap\">\n<table class=\"events\">\n");
    if (kind == Kind.MODIFIED) {
      html.append(
          "<thead><tr><th scope=\"col\">Date</th><th scope=\"col\">Old type</th>"
              + "<th scope=\"col\">New type</th><th scope=\"col\">Old description</th>"
              + "<th scope=\"col\">New description</th></tr></thead>\n<tbody>\n");
    } else {
      html.append(
          "<thead><tr><th scope=\"col\">Date</th><th scope=\"col\">Type</th>"
              + "<th scope=\"col\">Description</th></tr></thead>\n<tbody>\n");
    }
    for (EventDiff d : diffs) {
      html.append("<tr>");
      html.append("<th scope=\"row\">")
          .append(dateCell(calendarId, calendar, d.date()))
          .append("</th>");
      if (kind == Kind.MODIFIED) {
        html.append("<td>").append(d.oldType()).append("</td>");
        html.append("<td>").append(d.newType()).append("</td>");
        html.append("<td>").append(HtmlTemplate.escape(d.oldDescription())).append("</td>");
        html.append("<td>").append(HtmlTemplate.escape(d.newDescription())).append("</td>");
      } else {
        boolean removed = kind == Kind.REMOVED;
        html.append("<td>").append(removed ? d.oldType() : d.newType()).append("</td>");
        html.append("<td>")
            .append(HtmlTemplate.escape(removed ? d.oldDescription() : d.newDescription()))
            .append("</td>");
      }
      html.append("</tr>\n");
    }
    html.append("</tbody>\n</table>\n</div>\n");
    return html.toString();
  }

  /**
   * Links a diff row's date to its own page when the locally generated calendar published one (true
   * for an addition or modification), or to its anchored cell on the year page otherwise (true for
   * a removal, which by definition has no page in the newly generated data); falls back to plain
   * text when neither exists (the year itself was not generated locally).
   */
  private String dateCell(String calendarId, CalendarData calendar, LocalDate date) {
    String text = date.toString();
    if (calendar == null || !calendar.years().contains(date.getYear())) {
      return "<time datetime=\"" + date + "\">" + text + "</time>";
    }
    boolean hasOwnPage =
        calendar.eventsIn(date.getYear()).stream()
            .anyMatch(e -> e.date().equals(date) && !e.isWeekend());
    String href =
        hasOwnPage
            ? "../" + calendarId + "/" + date + "/index.html"
            : "../" + calendarId + "/" + date.getYear() + "/index.html#d" + date;
    return "<a href=\""
        + HtmlTemplate.escape(href)
        + "\"><time datetime=\""
        + date
        + "\">"
        + text
        + "</time></a>";
  }
}
