package com.bdc.reference;

import static org.junit.jupiter.api.Assertions.fail;

import com.bdc.emitter.EventsCsvReader;
import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Cross-validates generated calendars against third-party reference data committed under {@code
 * tools/src/test/resources/reference/<CALENDAR>/<source>.csv} (produced by {@code
 * scripts/reference/export_reference_calendars.py}).
 *
 * <p>For the overlapping date range, the set of {@code (date, type)} pairs for CLOSED and
 * EARLY_CLOSE events must match the reference exactly, except for rows listed in {@code
 * allowlist.csv} next to the reference files. Every allowlist row must still be a real difference;
 * stale rows fail the test so the allowlist never hides a fix.
 *
 * <p>Weekend days are excluded on both sides (reference files omit them; our weekend definition may
 * legitimately differ from a library that only models Monday-Friday sessions).
 */
class ReferenceCrossValidationTest {

  private static final Path REFERENCE_DIR = Path.of("tools/src/test/resources/reference");

  static Stream<Path> referenceFiles() throws IOException {
    if (!Files.isDirectory(REFERENCE_DIR)) {
      return Stream.empty();
    }
    try (Stream<Path> paths = Files.walk(REFERENCE_DIR)) {
      return paths
          .filter(p -> p.toString().endsWith(".csv"))
          .filter(p -> !p.getFileName().toString().equals("allowlist.csv"))
          .sorted()
          .toList()
          .stream();
    }
  }

  /** date|type|close_time -> reason, for one side. */
  record AllowEntry(String side, LocalDate date, EventType type, String reason) {}

  @ParameterizedTest(name = "{0}")
  @MethodSource("referenceFiles")
  void matchesReference(Path referenceFile) throws Exception {
    String calendarId = referenceFile.getParent().getFileName().toString();
    String sourceName = referenceFile.getFileName().toString().replace(".csv", "");

    List<Event> reference = new EventsCsvReader().read(referenceFile, "reference");
    if (reference.isEmpty()) {
      return;
    }
    LocalDate from = reference.stream().map(Event::date).min(Comparator.naturalOrder()).get();
    LocalDate to = reference.stream().map(Event::date).max(Comparator.naturalOrder()).get();
    // Honour the declared range and the event types the reference models
    Set<EventType> comparedTypes = EnumSet.of(EventType.CLOSED, EventType.EARLY_CLOSE);
    for (String line : Files.readAllLines(referenceFile)) {
      if (line.startsWith("# range:")) {
        String[] parts = line.substring("# range:".length()).trim().split("\\s+");
        from = LocalDate.parse(parts[0]);
        to = LocalDate.parse(parts[2]);
      } else if (line.startsWith("# types:")) {
        comparedTypes = EnumSet.noneOf(EventType.class);
        String spec = line.substring("# types:".length()).split("\\(")[0];
        for (String t : spec.split(",")) {
          if (!t.isBlank()) {
            comparedTypes.add(EventType.valueOf(t.strip()));
          }
        }
      }
    }

    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    registry.assertNoLoadErrors();
    ResolvedSpec spec = new SpecResolver(registry).resolve(calendarId);
    if (spec.coverage() != null) {
      if (spec.coverage().from() != null && spec.coverage().from().isAfter(from)) {
        from = spec.coverage().from();
      }
      if (spec.coverage().to() != null && spec.coverage().to().isBefore(to)) {
        to = spec.coverage().to();
      }
    }
    List<Event> ours = new EventGenerator().generate(spec, from, to);

    Map<String, Event> ourRows = new TreeMap<>();
    for (Event e : ours) {
      if (comparedTypes.contains(e.type()) && !spec.weekendPolicy().isWeekend(e.date())) {
        ourRows.put(e.date() + "|" + e.type(), e);
      }
    }
    Map<String, Event> refRows = new TreeMap<>();
    for (Event e : reference) {
      if (comparedTypes.contains(e.type())
          && !e.date().isBefore(from)
          && !e.date().isAfter(to)
          && !spec.weekendPolicy().isWeekend(e.date())) {
        refRows.put(e.date() + "|" + e.type(), e);
      }
    }

    Map<String, AllowEntry> allow = loadAllowlist(referenceFile.getParent(), sourceName);

    List<String> problems = new ArrayList<>();
    Set<String> usedAllow = new HashSet<>();
    for (String id : ourRows.keySet()) {
      if (!refRows.containsKey(id)) {
        String allowKey = "ours|" + id;
        if (allow.containsKey(allowKey)) {
          usedAllow.add(allowKey);
        } else {
          Event e = ourRows.get(id);
          problems.add("ours-only  " + id + "  " + e.description() + " [" + e.key() + "]");
        }
      }
    }
    for (String id : refRows.keySet()) {
      if (!ourRows.containsKey(id)) {
        String allowKey = "theirs|" + id;
        if (allow.containsKey(allowKey)) {
          usedAllow.add(allowKey);
        } else {
          problems.add("theirs-only " + id);
        }
      }
    }
    for (String key : allow.keySet()) {
      if (!usedAllow.contains(key)) {
        problems.add("stale allowlist entry (no longer differs): " + key);
      }
    }

    if (!problems.isEmpty()) {
      StringBuilder sb =
          new StringBuilder(
              calendarId
                  + " vs "
                  + sourceName
                  + " ("
                  + from
                  + " to "
                  + to
                  + "): "
                  + problems.size()
                  + " discrepancies\n");
      problems.stream().limit(200).forEach(p -> sb.append("  ").append(p).append('\n'));
      if (problems.size() > 200) {
        sb.append("  ... and ").append(problems.size() - 200).append(" more\n");
      }
      fail(sb.toString());
    }
  }

  /**
   * Reads {@code allowlist.csv}: columns {@code source,side,date,type,reason}. {@code source} is
   * the reference file name without extension (or {@code *} for all); {@code side} is {@code ours}
   * (we have it, they do not) or {@code theirs}.
   */
  private static Map<String, AllowEntry> loadAllowlist(Path dir, String sourceName)
      throws IOException {
    Map<String, AllowEntry> result = new LinkedHashMap<>();
    Path path = dir.resolve("allowlist.csv");
    if (!Files.exists(path)) {
      return result;
    }
    for (String line : Files.readAllLines(path)) {
      String trimmed = line.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("source,")) {
        continue;
      }
      String[] parts = trimmed.split(",", 5);
      if (parts.length < 5) {
        throw new IllegalArgumentException("allowlist row needs 5 columns: " + line);
      }
      if (!parts[0].equals("*") && !parts[0].equals(sourceName)) {
        continue;
      }
      AllowEntry entry =
          new AllowEntry(
              parts[1].strip(),
              LocalDate.parse(parts[2].strip()),
              EventType.valueOf(parts[3].strip()),
              parts[4].strip());
      result.put(entry.side() + "|" + entry.date() + "|" + entry.type(), entry);
    }
    return result;
  }
}
