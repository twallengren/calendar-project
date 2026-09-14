package com.bdc.site;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders {@code /compare/<A>/<B>/index.html} for every unordered pair of market calendars, plus
 * the {@code /compare/} index and the settlement self-test page.
 *
 * <h2>What a compare page answers</h2>
 *
 * <p>One question, both ways round: which days does one market trade on while the other is shut?
 * That is the question behind every cross-border settlement date, every "why did my T+2 land there"
 * ticket and every reconciliation break, and no per-market page can answer it because the answer
 * lives between two calendars.
 *
 * <h2>Why pre-rendered, and why small</h2>
 *
 * <p>Nine markets make 36 pairs, so these pages are generated rather than computed in the browser:
 * they work with scripting off, they are indexable, and they are the same artifacts the JSON API
 * publishes. Keeping them small is a constraint, not an aspiration — each page carries only the
 * mismatched days over the intersection of the two coverage ranges, limited to the years both
 * calendars publish a {@code v1} year file for, weekend rows excluded (a weekend mismatch is a
 * property of the weekend policy, restated on every page it would appear on, and would bury the
 * holiday differences that are the point).
 *
 * <p>The T+N settlement helper on each page is the one piece that is not pre-rendered: it is
 * progressive enhancement in {@code site.js} that fetches the two calendars' year files and walks
 * the joint stream in the browser, mirroring {@code JointDateStream} exactly (see {@code
 * spec/SPEC.md#joint-calendars-several-calendars-at-once}). With scripting off the form is simply
 * absent and the page names the CLI command that answers the same question.
 */
public final class ComparePageRenderer {

  private static final HtmlTemplate PAIR =
      HtmlTemplate.of(
          """
          <h1>{{aName}} vs {{bName}}</h1>
          <p class="lede">Where <code>{{a}}</code> and <code>{{b}}</code> disagree: every day one
          market trades and the other is closed, {{window}}. Weekends are excluded — those follow
          from each calendar's weekend policy, not from a holiday.</p>
          <section aria-labelledby="summary-heading">
            <h2 id="summary-heading">Summary</h2>
            {{{summary}}}
          </section>
          <section class="settlement" aria-labelledby="settlement-heading">
            <h2 id="settlement-heading">Settlement date (T+N)</h2>
            <div data-settlement data-a="{{a}}" data-b="{{b}}" data-api="{{api}}"
                 data-min="{{min}}" data-max="{{max}}"></div>
            <p>A trade between these two markets can only settle on a day <em>both</em> are open, so
            T+N counts business days on the joint calendar. From a command line:</p>
            <pre><code>tools query {{a}},{{b}} --settlement T+2 --from {{example}}</code></pre>
          </section>
          <section aria-labelledby="a-open-heading">
            <h2 id="a-open-heading">Open in {{a}}, closed in {{b}}</h2>
            {{{aOpen}}}
          </section>
          <section aria-labelledby="b-open-heading">
            <h2 id="b-open-heading">Open in {{b}}, closed in {{a}}</h2>
            {{{bOpen}}}
          </section>
          <p><a href="../../index.html">All {{pairCount}} market pairs</a></p>
          """);

  private static final HtmlTemplate INDEX =
      HtmlTemplate.of(
          """
          <h1>Compare two markets</h1>
          <p class="lede">{{pairCount}} pairs of the {{marketCount}} published markets, each with
          the days one trades while the other is closed and a T+N settlement helper for trades
          between them.</p>
          {{{sections}}}
          <p class="muted"><a href="settlement-selftest.html">Settlement self-test</a> — runs the
          browser settlement algorithm against a fixture of answers computed in Java. Serve the site
          over HTTP to run it; it fetches the published JSON API.</p>
          """);

  private static final HtmlTemplate SELFTEST =
      HtmlTemplate.of(
          """
          <h1>Settlement self-test</h1>
          <p class="lede">The T+N settlement helper on every compare page is written once, in
          <a href="../site.js"><code>site.js</code></a>, and must agree with
          <code>JointDateStream</code> — the same code the <code>query --settlement</code> CLI
          answers from. This page runs the browser implementation against a fixture of {{caseCount}}
          cases whose answers were computed in Java, and reports the result below.</p>
          <p class="muted">It fetches <code>v1/calendars/&lt;ID&gt;/&lt;year&gt;.json</code> like the
          real form does, so it needs the site served over HTTP
          (<code>python3 -m http.server</code> in the output directory), not opened from
          <code>file://</code>.</p>
          <div data-settlement-selftest data-api="{{api}}" id="selftest">
            <p>Running…</p>
          </div>
          <script type="application/json" id="settlement-fixture">{{{fixture}}}</script>
          """);

  private final SiteContext context;
  private final PageLayout layout;

  public ComparePageRenderer(SiteContext context, PageLayout layout) {
    this.context = context;
    this.layout = layout;
  }

  /** The published calendars whose {@code kind} is {@code market}, in index order. */
  public static List<CalendarData> marketCalendars(List<CalendarData> calendars, Path siteDir)
      throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    List<CalendarData> markets = new ArrayList<>();
    for (CalendarData calendar : calendars) {
      Path manifest =
          siteDir
              .resolve("v1")
              .resolve("calendars")
              .resolve(calendar.id())
              .resolve("manifest.json");
      String kind = "market";
      if (Files.isRegularFile(manifest)) {
        JsonNode node = mapper.readTree(manifest.toFile()).path("kind");
        kind = node.isMissingNode() || node.isNull() ? "market" : node.asText("market");
      }
      if ("market".equals(kind)) {
        markets.add(calendar);
      }
    }
    return List.copyOf(markets);
  }

  /**
   * Every unordered pair, ordered by id so the URL of a pair never depends on how it was reached.
   */
  public static List<List<CalendarData>> pairs(List<CalendarData> markets) {
    List<CalendarData> sorted = new ArrayList<>(markets);
    sorted.sort((x, y) -> x.id().compareTo(y.id()));
    List<List<CalendarData>> pairs = new ArrayList<>();
    for (int i = 0; i < sorted.size(); i++) {
      for (int j = i + 1; j < sorted.size(); j++) {
        pairs.add(List.of(sorted.get(i), sorted.get(j)));
      }
    }
    return List.copyOf(pairs);
  }

  /** Writes the index, one page per pair and the self-test; returns the site-relative paths. */
  public List<String> write(List<CalendarData> markets, Path siteDir) throws IOException {
    List<List<CalendarData>> pairs = pairs(markets);
    Path dir = siteDir.resolve("compare");
    Files.createDirectories(dir);
    List<String> written = new ArrayList<>();

    for (List<CalendarData> pair : pairs) {
      CalendarData a = pair.get(0);
      CalendarData b = pair.get(1);
      Path pairDir = dir.resolve(a.id()).resolve(b.id());
      Files.createDirectories(pairDir);
      Files.writeString(pairDir.resolve("index.html"), renderPair(a, b, pairs.size()));
      written.add("compare/" + a.id() + "/" + b.id() + "/");
    }

    Files.writeString(dir.resolve("index.html"), renderIndex(markets, pairs));
    written.add("compare/");

    String fixture = readFixture();
    if (fixture != null) {
      Files.writeString(dir.resolve("settlement-selftest.html"), renderSelfTest(fixture));
    }
    return written;
  }

  /** The pair page's canonical site-relative path, for links from anywhere else on the site. */
  public static String pairPath(String first, String second) {
    return "compare/" + pairDirectory(first, second);
  }

  /**
   * The pair's directory under {@code compare/}, always in id order so an unordered pair has
   * exactly one URL however it was reached.
   */
  static String pairDirectory(String first, String second) {
    String a = first.compareTo(second) <= 0 ? first : second;
    String b = first.compareTo(second) <= 0 ? second : first;
    return a + "/" + b + "/";
  }

  String renderPair(CalendarData a, CalendarData b, int pairCount) {
    Window window = window(a, b);
    List<Row> aOpen = mismatches(a, b, window);
    List<Row> bOpen = mismatches(b, a, window);
    String title = a.id() + " vs " + b.id() + ": trading day differences";
    String description =
        "Days "
            + a.id()
            + " trades while "
            + b.id()
            + " is closed ("
            + aOpen.size()
            + ") and the reverse ("
            + bOpen.size()
            + "), "
            + window.firstYear()
            + " to "
            + window.lastYear()
            + ", with a T+N settlement helper.";

    String body =
        PAIR.render(
            "a", a.id(),
            "b", b.id(),
            "aName", a.name(),
            "bName", b.name(),
            "window", windowSentence(window),
            "summary", summary(a, b, window, aOpen.size(), bOpen.size()),
            "api", SiteContext.rootPrefix(3) + "v1/calendars/",
            "min", String.valueOf(window.from()),
            "max", String.valueOf(window.to()),
            "example", String.valueOf(exampleTradeDate(window)),
            "aOpen", table(aOpen, a.id(), b.id()),
            "bOpen", table(bOpen, b.id(), a.id()),
            "pairCount", String.valueOf(pairCount));

    return layout.render(
        3,
        pairPath(a.id(), b.id()),
        title,
        description,
        List.of(
            new PageLayout.Crumb("Markets", "../../../index.html"),
            new PageLayout.Crumb("Compare", "../../index.html"),
            new PageLayout.Crumb(a.id() + " vs " + b.id(), null)),
        "",
        body);
  }

  String renderIndex(List<CalendarData> markets, List<List<CalendarData>> pairs) {
    List<CalendarData> sorted = new ArrayList<>(markets);
    sorted.sort((x, y) -> x.id().compareTo(y.id()));
    StringBuilder sections = new StringBuilder(8192);
    for (CalendarData calendar : sorted) {
      sections
          .append("<section aria-labelledby=\"")
          .append(HtmlTemplate.escape(calendar.id()))
          .append("-heading\">\n<h2 id=\"")
          .append(HtmlTemplate.escape(calendar.id()))
          .append("-heading\">")
          .append(HtmlTemplate.escape(calendar.id()))
          .append("</h2>\n<ul class=\"pair-list\">\n");
      for (CalendarData other : sorted) {
        if (other.id().equals(calendar.id())) {
          continue;
        }
        sections
            .append("<li><a href=\"")
            .append(HtmlTemplate.escape(pairDirectory(calendar.id(), other.id())))
            .append("index.html\">")
            .append(HtmlTemplate.escape(calendar.id()))
            .append(" vs ")
            .append(HtmlTemplate.escape(other.id()))
            .append("</a></li>\n");
      }
      sections.append("</ul>\n</section>\n");
    }

    return layout.render(
        1,
        "compare/",
        "Compare two trading calendars",
        "Every pair of published markets, with the days one trades while the other is closed and a"
            + " T+N settlement helper.",
        List.of(
            new PageLayout.Crumb("Markets", "../index.html"),
            new PageLayout.Crumb("Compare", null)),
        "",
        INDEX.render(
            "pairCount", String.valueOf(pairs.size()),
            "marketCount", String.valueOf(markets.size()),
            "sections", sections.toString()));
  }

  String renderSelfTest(String fixture) {
    int caseCount = 0;
    try {
      caseCount = new ObjectMapper().readTree(fixture).path("cases").size();
    } catch (IOException e) {
      caseCount = 0;
    }
    return layout.render(
        1,
        "compare/settlement-selftest.html",
        "Settlement self-test",
        "Runs the browser T+N settlement algorithm against answers computed in Java.",
        List.of(
            new PageLayout.Crumb("Markets", "../index.html"),
            new PageLayout.Crumb("Compare", "index.html"),
            new PageLayout.Crumb("Settlement self-test", null)),
        "",
        SELFTEST.render(
            "caseCount", String.valueOf(caseCount),
            "api", SiteContext.rootPrefix(1) + "v1/calendars/",
            "fixture", fixture.strip()));
  }

  // === Data ===

  /** The dates and years both calendars can answer for. */
  record Window(LocalDate from, LocalDate to, int firstYear, int lastYear) {}

  /**
   * The overlap of the two calendars: the intersection of their coverage ranges, narrowed to the
   * years both publish a {@code v1} year file for. Outside it one of the two streams would be
   * answering "unknown", and an unknown is not a difference.
   */
  static Window window(CalendarData a, CalendarData b) {
    int firstYear = Math.max(a.firstYear(), b.firstYear());
    int lastYear = Math.min(a.lastYear(), b.lastYear());
    LocalDate from = latest(a.coverage().from(), b.coverage().from());
    LocalDate to = earliest(a.coverage().to(), b.coverage().to());
    if (from == null || from.getYear() < firstYear) {
      from = LocalDate.of(firstYear, 1, 1);
    }
    if (to == null || to.getYear() > lastYear) {
      to = LocalDate.of(lastYear, 12, 31);
    }
    return new Window(from, to, firstYear, lastYear);
  }

  /** One mismatched day: open in the first calendar, closed in the second. */
  record Row(LocalDate date, String description, boolean projected) {}

  /**
   * Days {@code open} trades on and {@code closed} does not, weekend rows excluded. Several CLOSED
   * rows on one date (a doubled-up holiday) collapse into one row, descriptions joined, so the
   * table counts days rather than rows.
   */
  static List<Row> mismatches(CalendarData open, CalendarData closed, Window window) {
    Map<LocalDate, List<String>> byDate = new LinkedHashMap<>();
    Map<LocalDate, Boolean> projected = new LinkedHashMap<>();
    for (int year = window.firstYear(); year <= window.lastYear(); year++) {
      for (CalendarData.DayEvent event : closed.nonWeekendEventsIn(year)) {
        LocalDate date = event.date();
        if (!event.isClosed() || date.isBefore(window.from()) || date.isAfter(window.to())) {
          continue;
        }
        // A holiday that lands on the closed market's own weekend is not a difference worth a row:
        // it restates the weekend policy, and Tadawul's Friday Eid days would otherwise crowd out
        // the holidays the page exists to show.
        if (isWeekend(closed.weekendPolicy(), date) || !open.isBusinessDay(date)) {
          continue;
        }
        byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(event.description());
        projected.merge(
            date,
            open.isProjected(event) || closed.isProjected(event),
            (existing, value) -> existing || value);
      }
    }
    List<Row> rows = new ArrayList<>();
    byDate.forEach(
        (date, descriptions) ->
            rows.add(new Row(date, String.join("; ", descriptions), projected.get(date))));
    rows.sort((x, y) -> x.date().compareTo(y.date()));
    return rows;
  }

  /**
   * Whether a date is a weekend day for a calendar, read from its published {@code weekend_policy}
   * rather than from its {@code WEEKEND} rows.
   *
   * <p>The rows cannot answer this: {@code CLOSED} beats {@code WEEKEND} on the same date, so a
   * holiday falling on a weekend leaves no {@code WEEKEND} row at all — Tadawul's Eid al-Fitr 2026
   * covers a Friday and a Saturday and publishes seven {@code CLOSED} rows and no weekend row. The
   * policy is the normative statement and the rows are its expansion, which is why the browser
   * settlement helper reads the policy too (see {@code spec/SPEC.md#settlement-in-the-browser}).
   *
   * <p>The policy is a list of effective-dated periods; the <em>last</em> one covering the date
   * decides, and a date no period covers has no weekend days ({@code WeekendPolicy.daysOn}).
   */
  static boolean isWeekend(JsonNode policy, LocalDate date) {
    if (policy == null || policy.isMissingNode() || policy.isNull()) {
      return false;
    }
    String day = date.getDayOfWeek().name();
    JsonNode periods = policy.path("periods");
    if (periods.isArray() && !periods.isEmpty()) {
      for (int i = periods.size() - 1; i >= 0; i--) {
        JsonNode period = periods.get(i);
        if (covers(period, date)) {
          return contains(period.path("days"), day);
        }
      }
      return false;
    }
    return contains(policy.path("days"), day);
  }

  private static boolean covers(JsonNode period, LocalDate date) {
    JsonNode from = period.path("from");
    JsonNode to = period.path("to");
    if (!from.isMissingNode() && !from.isNull() && date.isBefore(LocalDate.parse(from.asText()))) {
      return false;
    }
    return to.isMissingNode() || to.isNull() || !date.isAfter(LocalDate.parse(to.asText()));
  }

  private static boolean contains(JsonNode days, String day) {
    if (!days.isArray()) {
      return false;
    }
    for (JsonNode value : days) {
      if (day.equals(value.asText())) {
        return true;
      }
    }
    return false;
  }

  // === Rendering ===

  private static String windowSentence(Window window) {
    return window.firstYear() == window.lastYear()
        ? "in " + window.firstYear()
        : "from " + window.firstYear() + " to " + window.lastYear();
  }

  private String summary(CalendarData a, CalendarData b, Window window, int aOpen, int bOpen) {
    LocalDate verified = earliest(a.coverage().verifiedThrough(), b.coverage().verifiedThrough());
    StringBuilder html = new StringBuilder();
    html.append("<dl class=\"facts\">\n");
    html.append("<div><dt>Shared coverage</dt><dd>")
        .append(window.from())
        .append(" to ")
        .append(window.to())
        .append("</dd></div>\n");
    html.append("<div><dt>Joint verified through</dt><dd>")
        .append(
            verified == null
                ? "&mdash;"
                : "<time datetime=\"" + verified + "\">" + verified + "</time>")
        .append("</dd></div>\n");
    html.append("<div><dt>Open in ")
        .append(HtmlTemplate.escape(a.id()))
        .append(", closed in ")
        .append(HtmlTemplate.escape(b.id()))
        .append("</dt><dd>")
        .append(aOpen)
        .append("</dd></div>\n");
    html.append("<div><dt>Open in ")
        .append(HtmlTemplate.escape(b.id()))
        .append(", closed in ")
        .append(HtmlTemplate.escape(a.id()))
        .append("</dt><dd>")
        .append(bOpen)
        .append("</dd></div>\n");
    html.append("</dl>\n");
    html.append(
        "<p class=\"muted\">Dates after the joint verified-through date are projected from the"
            + " rules and have not been checked against a published notice.</p>\n");
    return html.toString();
  }

  private static String table(List<Row> rows, String openId, String closedId) {
    if (rows.isEmpty()) {
      return "<p>No day in the shared window is open in "
          + HtmlTemplate.escape(openId)
          + " and closed in "
          + HtmlTemplate.escape(closedId)
          + ".</p>\n";
    }
    StringBuilder html = new StringBuilder(16 * 1024);
    html.append("<div class=\"table-wrap\">\n<table class=\"events compare\">\n");
    html.append(
        "<thead><tr><th scope=\"col\">Date</th><th scope=\"col\">Day</th>"
            + "<th scope=\"col\">Why "
            + HtmlTemplate.escape(closedId)
            + " is closed</th></tr></thead>\n<tbody>\n");
    int year = Integer.MIN_VALUE;
    for (Row row : rows) {
      if (row.date().getYear() != year) {
        year = row.date().getYear();
        html.append("<tr class=\"year-row\"><th scope=\"rowgroup\" colspan=\"3\">")
            .append(year)
            .append("</th></tr>\n");
      }
      html.append("<tr><td class=\"nowrap\"><a href=\"")
          .append(SiteContext.rootPrefix(3))
          .append(HtmlTemplate.escape(closedId))
          .append("/")
          .append(row.date())
          .append("/index.html\"><time datetime=\"")
          .append(row.date())
          .append("\">")
          .append(row.date())
          .append("</time></a></td>");
      html.append("<td class=\"nowrap\">")
          .append(row.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
          .append("</td>");
      html.append("<td>").append(HtmlTemplate.escape(row.description()));
      if (row.projected()) {
        html.append(" <span class=\"badge projected\">projected</span>");
      }
      html.append("</td></tr>\n");
    }
    html.append("</tbody>\n</table>\n</div>\n");
    return html.toString();
  }

  /** A trade date inside the shared window, for the example command line. */
  static LocalDate exampleTradeDate(Window window) {
    LocalDate candidate = LocalDate.of(Math.min(window.lastYear(), 2026), 2, 25);
    if (candidate.isBefore(window.from())) {
      return window.from();
    }
    return candidate.isAfter(window.to()) ? window.to() : candidate;
  }

  private static LocalDate earliest(LocalDate x, LocalDate y) {
    if (x == null) {
      return y;
    }
    if (y == null) {
      return x;
    }
    return x.isBefore(y) ? x : y;
  }

  private static LocalDate latest(LocalDate x, LocalDate y) {
    if (x == null) {
      return y;
    }
    if (y == null) {
      return x;
    }
    return x.isAfter(y) ? x : y;
  }

  /**
   * The settlement fixture, shipped as a resource so the self-test page is part of any build of the
   * tool rather than of a source checkout. Null when it is not on the classpath, in which case the
   * self-test page is simply not written.
   */
  private static String readFixture() throws IOException {
    try (InputStream in =
        ComparePageRenderer.class.getResourceAsStream("/site/settlement-fixture.json")) {
      return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
