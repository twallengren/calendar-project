package com.bdc.site;

import com.bdc.diff.CalendarDiff;
import com.bdc.diff.EventDiff;
import com.bdc.site.ChangelogBuilder.Changelog;
import com.bdc.site.ChangelogBuilder.Release;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders the changelog as HTML: an index listing every release, and one page per release with the
 * per-calendar added/removed/modified tables.
 *
 * <p>Rendered through {@link PageLayout}, so the changelog is part of the site rather than a
 * differently-styled island: same masthead, same tables, same light/dark palette.
 *
 * <p>Tables are capped at {@link #MAX_ROWS} rows per section. A single release that renumbers a
 * calendar's whole history produces tens of thousands of rows — v10.0.0 rendered a 1.4 MB page
 * before this cap — which is a bad page and an unusable one on a phone. The cap is a display
 * decision only: {@code v1/changelog.json} always carries the complete, untruncated lists, and each
 * truncated table links to it.
 */
public class ChangelogHtmlRenderer {

  /** Maximum rows rendered per added/removed/modified table on a release page. */
  static final int MAX_ROWS = 100;

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'", Locale.ENGLISH)
          .withZone(ZoneOffset.UTC);

  private static final HtmlTemplate INDEX_BODY =
      HtmlTemplate.of(
          """
          <h1>Changelog</h1>
          <p class="lede">Every published release, newest first, with what changed in each
          calendar. Diffs are computed between consecutive release-history snapshots; the complete
          untruncated data is in <a href="{{root}}v1/changelog.json">changelog.json</a>.</p>
          {{{table}}}
          """);

  private static final HtmlTemplate RELEASE_BODY =
      HtmlTemplate.of(
          """
          <h1>Release v{{version}}</h1>
          <p class="lede">{{summary}}</p>
          <dl class="facts">
            <div><dt>Version</dt><dd>v{{version}}</dd></div>
            <div><dt>Commit</dt><dd><code>{{sha}}</code></dd></div>
            <div><dt>Published</dt><dd><time datetime="{{isoTimestamp}}">{{timestamp}}</time></dd></div>
            <div><dt>Severity</dt><dd><span class="severity-{{severity}}">{{severity}}</span></dd></div>
          </dl>
          {{{calendars}}}
          """);

  private final PageLayout layout;

  /** Default context, for the standalone {@code changelog} command. */
  public ChangelogHtmlRenderer() {
    this(
        new SiteContext(
            "/",
            "Business Day Calendars",
            "https://github.com/twallengren/calendar-project",
            "",
            "",
            "",
            Instant.now()));
  }

  public ChangelogHtmlRenderer(SiteContext context) {
    this.layout = new PageLayout(context);
  }

  /**
   * Writes {@code <outDir>/changelog/index.html} and one {@code
   * <outDir>/changelog/v<version>/index.html} per release.
   */
  public void write(Changelog changelog, Path outDir) throws IOException {
    Path changelogDir = outDir.resolve("changelog");
    Files.createDirectories(changelogDir);
    Files.writeString(changelogDir.resolve("index.html"), renderIndex(changelog));

    for (Release release : changelog.releases()) {
      Path releaseDir = changelogDir.resolve("v" + release.version());
      Files.createDirectories(releaseDir);
      Files.writeString(releaseDir.resolve("index.html"), renderRelease(release));
    }
  }

  String renderIndex(Changelog changelog) {
    StringBuilder table = new StringBuilder(8192);
    table.append("<div class=\"table-wrap\">\n<table class=\"events release-list\">\n");
    table.append(
        """
        <thead><tr>
        <th scope="col">Release</th><th scope="col">Commit</th><th scope="col">Published</th>
        <th scope="col">Severity</th><th scope="col">Added</th><th scope="col">Removed</th>
        <th scope="col">Modified</th><th scope="col">Calendars</th>
        </tr></thead>
        <tbody>
        """);
    for (Release release : changelog.releases()) {
      table.append("<tr>");
      table
          .append("<th scope=\"row\"><a href=\"v")
          .append(HtmlTemplate.escape(release.version()))
          .append("/index.html\">v")
          .append(HtmlTemplate.escape(release.version()))
          .append("</a>")
          .append(release.blessed() ? " <span class=\"badge ok\">current</span>" : "")
          .append("</th>");
      table
          .append("<td><code>")
          .append(HtmlTemplate.escape(shortSha(release.gitSha())))
          .append("</code></td>");
      table
          .append("<td><time datetime=\"")
          .append(release.timestamp())
          .append("\">")
          .append(TIMESTAMP.format(release.timestamp()))
          .append("</time></td>");
      table
          .append("<td><span class=\"severity-")
          .append(release.severity().name())
          .append("\">")
          .append(release.severity().name())
          .append("</span></td>");
      table.append("<td class=\"num\">").append(release.totalAdditions()).append("</td>");
      table.append("<td class=\"num\">").append(release.totalRemovals()).append("</td>");
      table.append("<td class=\"num\">").append(release.totalModifications()).append("</td>");
      table
          .append("<td>")
          .append(HtmlTemplate.escape(String.join(", ", release.calendars().keySet())))
          .append("</td>");
      table.append("</tr>\n");
    }
    table.append("</tbody>\n</table>\n</div>\n");

    String body = INDEX_BODY.render("root", SiteContext.rootPrefix(1), "table", table.toString());
    return layout.render(
        1,
        "changelog/",
        "Changelog",
        "Every published release of the calendar dataset, with the dates added, removed and"
            + " modified in each market.",
        List.of(
            new PageLayout.Crumb("Markets", "../index.html"),
            new PageLayout.Crumb("Changelog", null)),
        "",
        body);
  }

  String renderRelease(Release release) {
    StringBuilder calendars = new StringBuilder(16 * 1024);
    for (var entry : release.calendars().entrySet()) {
      calendars.append(renderCalendarSection(entry.getKey(), entry.getValue()));
    }

    String summary =
        release.totalChanges() == 0
            ? "No calendar data changed in this release."
            : release.totalAdditions()
                + " dates added, "
                + release.totalRemovals()
                + " removed and "
                + release.totalModifications()
                + " modified across "
                + release.calendars().size()
                + (release.calendars().size() == 1 ? " calendar." : " calendars.");

    String body =
        RELEASE_BODY.render(
            "version", release.version(),
            "summary", summary,
            "sha", release.gitSha(),
            "isoTimestamp", release.timestamp().toString(),
            "timestamp", TIMESTAMP.format(release.timestamp()),
            "severity", release.severity().name(),
            "calendars", calendars.toString());

    return layout.render(
        2,
        "changelog/v" + release.version() + "/",
        "Release v" + release.version(),
        summary,
        List.of(
            new PageLayout.Crumb("Markets", "../../index.html"),
            new PageLayout.Crumb("Changelog", "../index.html"),
            new PageLayout.Crumb("v" + release.version(), null)),
        "",
        body);
  }

  private String renderCalendarSection(String calendarId, CalendarDiff diff) {
    String anchor = "cal-" + calendarId;
    StringBuilder html = new StringBuilder(8192);
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

    if (!diff.hasChanges()) {
      html.append("<p>No changes.</p>\n</section>\n");
      return html.toString();
    }

    if (!diff.removals().isEmpty()) {
      html.append("<h3>Removed</h3>\n").append(eventTable(diff.removals(), true));
    }
    if (!diff.additions().isEmpty()) {
      html.append("<h3>Added</h3>\n").append(eventTable(diff.additions(), false));
    }
    if (!diff.modifications().isEmpty()) {
      html.append("<h3>Modified</h3>\n").append(modificationTable(diff.modifications()));
    }
    html.append("</section>\n");
    return html.toString();
  }

  private String eventTable(List<EventDiff> diffs, boolean removed) {
    StringBuilder html = new StringBuilder(8192);
    html.append("<div class=\"table-wrap\">\n<table class=\"events\">\n");
    html.append(
        "<thead><tr><th scope=\"col\">Date</th><th scope=\"col\">Key</th>"
            + "<th scope=\"col\">Type</th><th scope=\"col\">Description</th></tr></thead>\n"
            + "<tbody>\n");
    for (EventDiff d : diffs.subList(0, Math.min(MAX_ROWS, diffs.size()))) {
      html.append("<tr>");
      html.append("<th scope=\"row\"><time datetime=\"")
          .append(d.date())
          .append("\">")
          .append(d.date())
          .append("</time></th>");
      html.append("<td><code>").append(HtmlTemplate.escape(d.key())).append("</code></td>");
      html.append("<td>").append(removed ? d.oldType() : d.newType()).append("</td>");
      html.append("<td>")
          .append(HtmlTemplate.escape(removed ? d.oldDescription() : d.newDescription()))
          .append("</td>");
      html.append("</tr>\n");
    }
    html.append("</tbody>\n</table>\n</div>\n");
    html.append(truncationNote(diffs.size()));
    return html.toString();
  }

  private String modificationTable(List<EventDiff> diffs) {
    StringBuilder html = new StringBuilder(8192);
    html.append("<div class=\"table-wrap\">\n<table class=\"events\">\n");
    html.append(
        "<thead><tr><th scope=\"col\">Date</th><th scope=\"col\">Key</th>"
            + "<th scope=\"col\">Old type</th><th scope=\"col\">New type</th>"
            + "<th scope=\"col\">Old description</th><th scope=\"col\">New description</th>"
            + "</tr></thead>\n<tbody>\n");
    for (EventDiff d : diffs.subList(0, Math.min(MAX_ROWS, diffs.size()))) {
      html.append("<tr>");
      html.append("<th scope=\"row\"><time datetime=\"")
          .append(d.date())
          .append("\">")
          .append(d.date())
          .append("</time></th>");
      html.append("<td><code>").append(HtmlTemplate.escape(d.key())).append("</code></td>");
      html.append("<td>").append(d.oldType()).append("</td>");
      html.append("<td>").append(d.newType()).append("</td>");
      html.append("<td>").append(HtmlTemplate.escape(d.oldDescription())).append("</td>");
      html.append("<td>").append(HtmlTemplate.escape(d.newDescription())).append("</td>");
      html.append("</tr>\n");
    }
    html.append("</tbody>\n</table>\n</div>\n");
    html.append(truncationNote(diffs.size()));
    return html.toString();
  }

  private String truncationNote(int total) {
    if (total <= MAX_ROWS) {
      return "";
    }
    return "<p class=\"truncation\">Showing the first "
        + MAX_ROWS
        + " of "
        + total
        + " rows. The complete list is in <a href=\""
        + SiteContext.rootPrefix(2)
        + "v1/changelog.json\">changelog.json</a>.</p>\n";
  }

  private static String shortSha(String sha) {
    return sha != null && sha.length() > 7 ? sha.substring(0, 7) : String.valueOf(sha);
  }
}
