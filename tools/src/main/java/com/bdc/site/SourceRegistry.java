package com.bdc.site;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The {@code sources/} register, read once and indexed two ways: by directory (what the sources
 * pages render) and by citation id (what a market page's source list links to).
 *
 * <h2>Why the citation id has to be looked up, not assumed</h2>
 *
 * <p>A citation id is global, but the register that documents it lives in one market directory. A
 * venue calendar that {@code extends} a base calendar cites the base's sources: every Euronext
 * venue cites {@code euronext-hours-holidays}, which is documented once, in {@code
 * sources/EU-EURONEXT/README.md}. So resolving an id for a calendar walks the calendar's resolution
 * chain — itself first, then its ancestor calendars nearest-first — and only then falls back to any
 * directory that documents it. An id no register documents resolves to nothing and is rendered with
 * a visible "unresolved" marker rather than a dead link: {@code validate --strict} is the gate that
 * should have caught it, and a silent omission on the site would hide the gap.
 */
public final class SourceRegistry {

  /** One {@code sources/<ID>/} directory: its README, parsed once. */
  public record Market(String id, String markdown, List<String> headers, List<List<String>> rows) {

    /** The citation ids documented here, in document order. */
    public List<String> citationIds() {
      List<String> ids = new ArrayList<>();
      for (List<String> row : rows) {
        String id = row.isEmpty() ? null : MarkdownRenderer.rowAnchor(row.get(0));
        if (id != null) {
          ids.add(id);
        }
      }
      return ids;
    }
  }

  /**
   * A citation as a market page shows it: the id, the directory documenting it (null when nothing
   * does) and the row's title cell, for the link text's {@code title} attribute.
   */
  public record Citation(String id, String market, String title) {
    public boolean resolved() {
      return market != null;
    }
  }

  private final Map<String, Market> markets;
  private final Map<String, String> documentedIn;
  private final Map<String, Map<String, List<String>>> titles;
  private final Map<String, List<String>> citedBy;
  private final Map<String, List<String>> ancestors;

  private SourceRegistry(
      Map<String, Market> markets,
      Map<String, String> documentedIn,
      Map<String, Map<String, List<String>>> titles,
      Map<String, List<String>> citedBy,
      Map<String, List<String>> ancestors) {
    this.markets = markets;
    this.documentedIn = documentedIn;
    this.titles = titles;
    this.citedBy = citedBy;
    this.ancestors = ancestors;
  }

  /** The parsed {@code sources/<ID>/} directories, sorted by id. */
  public List<Market> markets() {
    return List.copyOf(markets.values());
  }

  public Market market(String id) {
    return markets.get(id);
  }

  /**
   * The citations a calendar's resolved event sources carry, in citation-id order, each resolved to
   * the register that documents it.
   */
  public List<Citation> citationsFor(String calendarId) {
    List<Citation> citations = new ArrayList<>();
    for (String id : citedBy.getOrDefault(calendarId, List.of())) {
      String market = resolve(calendarId, id);
      String title = market == null ? null : titleOf(market, id);
      citations.add(new Citation(id, market, title));
    }
    return citations;
  }

  /**
   * The {@code sources/} directory a market page should link to for its own register: its own
   * directory when it has one, else the nearest ancestor calendar that does, else null.
   */
  public String registerFor(String calendarId) {
    if (markets.containsKey(calendarId)) {
      return calendarId;
    }
    for (String ancestor : ancestors.getOrDefault(calendarId, List.of())) {
      if (markets.containsKey(ancestor)) {
        return ancestor;
      }
    }
    return null;
  }

  private String resolve(String calendarId, String citationId) {
    Market own = markets.get(calendarId);
    if (own != null && own.citationIds().contains(citationId)) {
      return calendarId;
    }
    for (String ancestor : ancestors.getOrDefault(calendarId, List.of())) {
      Market market = markets.get(ancestor);
      if (market != null && market.citationIds().contains(citationId)) {
        return ancestor;
      }
    }
    return documentedIn.get(citationId);
  }

  private String titleOf(String market, String citationId) {
    List<String> row = titles.getOrDefault(market, Map.of()).get(citationId);
    return row == null || row.size() < 2 ? null : stripInlineCode(row.get(1));
  }

  private static String stripInlineCode(String cell) {
    return cell.replace("`", "").strip();
  }

  /**
   * Reads every {@code sources/<ID>/README.md} and, for each blessed calendar, the citation ids and
   * ancestor calendars recorded in its {@code resolved.yaml}.
   *
   * @param sourcesDir the {@code sources/} directory
   * @param blessedDir the {@code blessed/} directory, for the resolved specs
   * @param calendarIds the published calendars whose citations should be indexed
   */
  public static SourceRegistry read(Path sourcesDir, Path blessedDir, List<String> calendarIds)
      throws IOException {
    Map<String, Market> markets = new TreeMap<>();
    Map<String, String> documentedIn = new LinkedHashMap<>();
    Map<String, Map<String, List<String>>> titles = new LinkedHashMap<>();
    if (Files.isDirectory(sourcesDir)) {
      List<Path> dirs;
      try (Stream<Path> entries = Files.list(sourcesDir)) {
        dirs = entries.filter(Files::isDirectory).sorted().toList();
      }
      for (Path dir : dirs) {
        Path readme = dir.resolve("README.md");
        if (!Files.isRegularFile(readme)) {
          continue;
        }
        String id = dir.getFileName().toString();
        String markdown = Files.readString(readme, StandardCharsets.UTF_8);
        Path register = dir.resolve("register.json");
        if (Files.isRegularFile(register)) {
          markdown = com.bdc.source.SourceRegister.read(register, sourcesDir).markdown(markdown);
        }
        Market market = parse(id, markdown);
        markets.put(id, market);
        for (List<String> row : market.rows()) {
          String citationId = row.isEmpty() ? null : MarkdownRenderer.rowAnchor(row.get(0));
          if (citationId != null) {
            documentedIn.putIfAbsent(citationId, id);
            titles.computeIfAbsent(id, k -> new LinkedHashMap<>()).putIfAbsent(citationId, row);
          }
        }
      }
    }

    Map<String, List<String>> citedBy = new LinkedHashMap<>();
    Map<String, List<String>> ancestors = new LinkedHashMap<>();
    ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    for (String calendarId : calendarIds) {
      Path resolved = blessedDir.resolve(calendarId).resolve("resolved.yaml");
      if (!Files.isRegularFile(resolved)) {
        continue;
      }
      JsonNode root = yaml.readTree(resolved.toFile());
      Set<String> ids = new LinkedHashSet<>();
      for (JsonNode eventSource : root.path("event_sources")) {
        for (JsonNode source : eventSource.path("source")) {
          String id = source.path("id").asText("");
          if (!id.isEmpty()) {
            ids.add(id);
          }
        }
      }
      for (JsonNode delta : root.path("deltas")) {
        for (JsonNode source : delta.path("source")) {
          String id = source.path("id").asText("");
          if (!id.isEmpty()) ids.add(id);
        }
      }
      List<String> sorted = new ArrayList<>(ids);
      sorted.sort(String::compareTo);
      citedBy.put(calendarId, List.copyOf(sorted));
      ancestors.put(calendarId, ancestorCalendars(root, calendarId));
    }

    return new SourceRegistry(markets, documentedIn, titles, citedBy, ancestors);
  }

  /**
   * The calendars this one inherits from, nearest first. {@code resolution_chain} lists them oldest
   * first ({@code calendar:EU-EURONEXT}, …, {@code calendar:FR-EURONEXT-PARIS}), so reversing it
   * and dropping the calendar itself gives the {@code extends} walk in lookup order.
   */
  private static List<String> ancestorCalendars(JsonNode resolved, String calendarId) {
    List<String> chain = new ArrayList<>();
    for (JsonNode entry : resolved.path("resolution_chain")) {
      String value = entry.asText("");
      if (value.startsWith("calendar:")) {
        String id = value.substring("calendar:".length());
        if (!id.equals(calendarId)) {
          chain.add(id);
        }
      }
    }
    List<String> nearestFirst = new ArrayList<>(chain);
    java.util.Collections.reverse(nearestFirst);
    return List.copyOf(nearestFirst);
  }

  /** Pulls the first pipe table out of a source register README, alongside the raw Markdown. */
  static Market parse(String id, String markdown) {
    List<String> headers = List.of();
    List<List<String>> rows = new ArrayList<>();
    String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
    for (int i = 0; i + 1 < lines.length; i++) {
      if (!lines[i].strip().startsWith("|") || !isDivider(lines[i + 1])) {
        continue;
      }
      headers = splitRow(lines[i]);
      for (int j = i + 2; j < lines.length && lines[j].strip().startsWith("|"); j++) {
        rows.add(splitRow(lines[j]));
      }
      break;
    }
    return new Market(id, markdown, headers, List.copyOf(rows));
  }

  private static boolean isDivider(String line) {
    String trimmed = line.strip();
    return trimmed.startsWith("|") && trimmed.chars().allMatch(c -> "|-: \t".indexOf(c) >= 0);
  }

  private static List<String> splitRow(String row) {
    String body = row.strip();
    if (body.startsWith("|")) {
      body = body.substring(1);
    }
    if (body.endsWith("|")) {
      body = body.substring(0, body.length() - 1);
    }
    List<String> cells = new ArrayList<>();
    for (String cell : body.split("\\|", -1)) {
      cells.add(cell.strip());
    }
    return List.copyOf(cells);
  }
}
