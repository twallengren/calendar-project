package com.bdc.site;

import com.bdc.site.CalendarData.DayEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders one year as twelve month tables — the visual core of the site.
 *
 * <p>Every day cell carries {@code open}, {@code closed}, {@code early-close} or {@code weekend},
 * plus {@code projected} when the row is not yet verified, so the CSS grid needs no inline styles
 * and the markup stays a plain set of {@code <table>}s that reads correctly in a screen reader, in
 * a text browser and on paper. Cells are anchored ({@code id="d2027-07-05"}) so any date can be
 * linked to directly on the year page.
 */
public final class YearGridRenderer {

  private static final List<DayOfWeek> WEEK =
      List.of(
          DayOfWeek.MONDAY,
          DayOfWeek.TUESDAY,
          DayOfWeek.WEDNESDAY,
          DayOfWeek.THURSDAY,
          DayOfWeek.FRIDAY,
          DayOfWeek.SATURDAY,
          DayOfWeek.SUNDAY);

  /**
   * @param calendar the calendar being rendered
   * @param year the year to render
   * @param datePathPrefix relative prefix that turns a date directory name into a link from the
   *     page being rendered, e.g. {@code "../"} from a year page or {@code ""} from a market page
   */
  public String render(CalendarData calendar, int year, String datePathPrefix) {
    Map<LocalDate, List<DayEvent>> byDate = new LinkedHashMap<>();
    for (DayEvent event : calendar.eventsIn(year)) {
      byDate.computeIfAbsent(event.date(), k -> new ArrayList<>()).add(event);
    }

    StringBuilder html = new StringBuilder(64 * 1024);
    html.append("<div class=\"year-grid\">\n");
    for (int month = 1; month <= 12; month++) {
      html.append(renderMonth(calendar, YearMonth.of(year, month), byDate, datePathPrefix));
    }
    html.append("</div>\n");
    return html.toString();
  }

  /** The shared legend for the day-cell classes. */
  public String renderLegend() {
    return """
        <ul class="legend">
          <li><span class="swatch open"></span>Open</li>
          <li><span class="swatch unknown"></span>Unknown (incomplete coverage)</li>
          <li><span class="swatch closed"></span>Closed</li>
          <li><span class="swatch early-close"></span>Early close</li>
          <li><span class="swatch weekend"></span>Weekend</li>
          <li><span class="swatch projected"></span>Projected (not yet verified)</li>
        </ul>
        """;
  }

  private String renderMonth(
      CalendarData calendar,
      YearMonth month,
      Map<LocalDate, List<DayEvent>> byDate,
      String datePathPrefix) {
    String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    StringBuilder html = new StringBuilder(4096);
    html.append("<table class=\"month\">\n<caption>")
        .append(HtmlTemplate.escape(monthName))
        .append("</caption>\n<thead><tr>");
    for (DayOfWeek day : WEEK) {
      html.append("<th scope=\"col\"><abbr title=\"")
          .append(HtmlTemplate.escape(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)))
          .append("\">")
          .append(HtmlTemplate.escape(day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)))
          .append("</abbr></th>");
    }
    html.append("</tr></thead>\n<tbody>\n");

    LocalDate first = month.atDay(1);
    int lead = WEEK.indexOf(first.getDayOfWeek());
    int length = month.lengthOfMonth();
    int cell = 0;
    html.append("<tr>");
    for (int i = 0; i < lead; i++, cell++) {
      html.append("<td class=\"pad\"></td>");
    }
    for (int day = 1; day <= length; day++, cell++) {
      if (cell % 7 == 0 && cell > 0) {
        html.append("</tr>\n<tr>");
      }
      html.append(renderDay(calendar, month.atDay(day), byDate, datePathPrefix));
    }
    while (cell % 7 != 0) {
      html.append("<td class=\"pad\"></td>");
      cell++;
    }
    html.append("</tr>\n</tbody>\n</table>\n");
    return html.toString();
  }

  private String renderDay(
      CalendarData calendar,
      LocalDate date,
      Map<LocalDate, List<DayEvent>> byDate,
      String datePathPrefix) {
    List<DayEvent> events = byDate.getOrDefault(date, List.of());
    String state = "open";
    boolean projected = false;
    boolean linkable = false;
    List<String> titles = new ArrayList<>();
    for (DayEvent event : events) {
      if (event.isClosed()) {
        state = "closed";
      } else if (event.isEarlyClose() && !"closed".equals(state)) {
        state = "early-close";
      } else if (event.isWeekend() && "open".equals(state)) {
        state = "weekend";
      }
      if (!event.isWeekend()) {
        linkable = true;
        titles.add(
            event.description()
                + (event.closeTime() != null ? " (closes " + event.closeTime() + ")" : ""));
        projected |= calendar.isProjected(event);
      }
    }

    if (calendar.isUnknown(date)) {
      titles.add(0, "Unknown actual state; scheduled " + state);
      state = "unknown";
      projected = false;
    } else if (calendar.assessments().containsKey(date)) {
      projected =
          calendar
              .assessments()
              .get(date)
              .path("effective_confidence")
              .asText()
              .equals("PROJECTED");
    }
    StringBuilder html = new StringBuilder(160);
    html.append("<td class=\"day ")
        .append(state)
        .append(projected ? " projected" : "")
        .append("\" id=\"d")
        .append(date)
        .append('"');
    if (!titles.isEmpty()) {
      html.append(" title=\"").append(HtmlTemplate.escape(String.join("; ", titles))).append('"');
    }
    html.append('>');

    String inner = "<time datetime=\"" + date + "\">" + date.getDayOfMonth() + "</time>";
    if (linkable) {
      html.append("<a href=\"")
          .append(datePathPrefix)
          .append(date)
          .append("/index.html\">")
          .append(inner)
          .append("</a>");
    } else {
      html.append(inner);
    }
    html.append("</td>");
    return html.toString();
  }
}
