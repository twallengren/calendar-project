package com.bdc.site;

import com.bdc.diff.CalendarDiff;
import com.bdc.diff.EventDiff;
import com.bdc.site.ChangelogBuilder.Changelog;
import com.bdc.site.ChangelogBuilder.Release;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders the changelog as plain, dependency-free HTML: an index page listing every release with a
 * summary table, and one page per release with the full per-calendar tables.
 *
 * <p>Markup is deliberately semantic ({@code <table>}, {@code <section>}, headings) so that a
 * shared template can restyle it later without changing structure.
 */
public class ChangelogHtmlRenderer {

  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_INSTANT;

  private static final String STYLE =
      """
      <style>
        body { font-family: -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; margin: 2rem; color: #1a1a1a; }
        h1, h2, h3 { color: #111; }
        table { border-collapse: collapse; width: 100%; margin-bottom: 1.5rem; }
        th, td { border: 1px solid #ddd; padding: 0.4rem 0.6rem; text-align: left; font-size: 0.9rem; }
        th { background: #f4f4f4; }
        tr:nth-child(even) { background: #fafafa; }
        .severity-NONE { color: #2e7d32; }
        .severity-MINOR { color: #b8860b; }
        .severity-MAJOR { color: #c62828; font-weight: bold; }
        code { background: #f0f0f0; padding: 0.1rem 0.3rem; border-radius: 3px; }
        section { margin-bottom: 2rem; }
        nav a { margin-right: 1rem; }
      </style>
      """;

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

  private String renderIndex(Changelog changelog) {
    StringBuilder html = new StringBuilder();
    html.append("<title>Calendar Changelog</title>\n").append(STYLE);
    html.append("<h1>Calendar Changelog</h1>\n");
    html.append("<section>\n<table>\n");
    html.append(
        "<tr><th>Version</th><th>Git SHA</th><th>Timestamp</th><th>Severity</th>"
            + "<th>Added</th><th>Removed</th><th>Modified</th></tr>\n");
    for (Release release : changelog.releases()) {
      html.append("<tr>");
      html.append("<td><a href=\"v")
          .append(escape(release.version()))
          .append("/index.html\">v")
          .append(escape(release.version()))
          .append("</a>")
          .append(release.blessed() ? " (blessed)" : "")
          .append("</td>");
      html.append("<td><code>").append(escape(shortSha(release.gitSha()))).append("</code></td>");
      html.append("<td>").append(TIMESTAMP_FORMAT.format(release.timestamp())).append("</td>");
      html.append("<td class=\"severity-")
          .append(release.severity().name())
          .append("\">")
          .append(release.severity().name())
          .append("</td>");
      html.append("<td>").append(release.totalAdditions()).append("</td>");
      html.append("<td>").append(release.totalRemovals()).append("</td>");
      html.append("<td>").append(release.totalModifications()).append("</td>");
      html.append("</tr>\n");
    }
    html.append("</table>\n</section>\n");
    return html.toString();
  }

  private String renderRelease(Release release) {
    StringBuilder html = new StringBuilder();
    html.append("<title>Calendar Changelog v")
        .append(escape(release.version()))
        .append("</title>\n")
        .append(STYLE);
    html.append("<nav><a href=\"../index.html\">&larr; All releases</a></nav>\n");
    html.append("<h1>v").append(escape(release.version())).append("</h1>\n");
    html.append("<p><code>")
        .append(escape(release.gitSha()))
        .append("</code> &middot; ")
        .append(TIMESTAMP_FORMAT.format(release.timestamp()))
        .append(release.blessed() ? " &middot; blessed" : "")
        .append(" &middot; <span class=\"severity-")
        .append(release.severity().name())
        .append("\">")
        .append(release.severity().name())
        .append("</span></p>\n");

    for (var entry : release.calendars().entrySet()) {
      html.append(renderCalendarSection(entry.getKey(), entry.getValue()));
    }
    return html.toString();
  }

  private String renderCalendarSection(String calendarId, CalendarDiff diff) {
    StringBuilder html = new StringBuilder();
    html.append("<section>\n<h2>")
        .append(escape(calendarId))
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
      html.append("<h3>Removed</h3>\n");
      html.append(eventTable(diff.removals(), true));
    }
    if (!diff.additions().isEmpty()) {
      html.append("<h3>Added</h3>\n");
      html.append(eventTable(diff.additions(), false));
    }
    if (!diff.modifications().isEmpty()) {
      html.append("<h3>Modified</h3>\n");
      html.append(modificationTable(diff.modifications()));
    }
    html.append("</section>\n");
    return html.toString();
  }

  private String eventTable(List<EventDiff> diffs, boolean removed) {
    StringBuilder html = new StringBuilder();
    html.append("<table>\n<tr><th>Date</th><th>Key</th><th>Type</th><th>Description</th></tr>\n");
    for (EventDiff d : diffs) {
      html.append("<tr>");
      html.append("<td>").append(d.date()).append("</td>");
      html.append("<td>").append(escape(d.key())).append("</td>");
      html.append("<td>").append(removed ? d.oldType() : d.newType()).append("</td>");
      html.append("<td>")
          .append(escape(removed ? d.oldDescription() : d.newDescription()))
          .append("</td>");
      html.append("</tr>\n");
    }
    html.append("</table>\n");
    return html.toString();
  }

  private String modificationTable(List<EventDiff> diffs) {
    StringBuilder html = new StringBuilder();
    html.append(
        "<table>\n<tr><th>Date</th><th>Key</th><th>Old Type</th><th>New Type</th>"
            + "<th>Old Description</th><th>New Description</th></tr>\n");
    for (EventDiff d : diffs) {
      html.append("<tr>");
      html.append("<td>").append(d.date()).append("</td>");
      html.append("<td>").append(escape(d.key())).append("</td>");
      html.append("<td>").append(d.oldType()).append("</td>");
      html.append("<td>").append(d.newType()).append("</td>");
      html.append("<td>").append(escape(d.oldDescription())).append("</td>");
      html.append("<td>").append(escape(d.newDescription())).append("</td>");
      html.append("</tr>\n");
    }
    html.append("</table>\n");
    return html.toString();
  }

  private static String shortSha(String sha) {
    return sha.length() > 7 ? sha.substring(0, 7) : sha;
  }

  private static String escape(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
