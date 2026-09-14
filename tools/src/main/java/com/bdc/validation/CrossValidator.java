package com.bdc.validation;

import com.bdc.emitter.EventsCsvReader;
import com.bdc.generator.EventGenerator;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.bdc.model.WeekendPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compares generated calendar events against a third-party reference CSV, applying the allowlist of
 * known/explained differences that sits next to it.
 *
 * <p>Factored out of {@code ReferenceCrossValidationTest} so the same comparison can back the
 * {@code crossvalidate} CLI command; the test is now a thin wrapper that fails when a result is not
 * {@linkplain CrossValidationResult#isClean() clean}.
 *
 * <p>The reference CSV format: {@code date,type,close_time} columns (weekends omitted), with
 * optional {@code # generated-by:}, {@code # range:} and {@code # types:} comment headers. See
 * {@code tools/src/test/resources/reference/README.md}.
 */
public class CrossValidator {

  /** One allowlist row: {@code side} ({@code ours}/{@code theirs}) + date + type -> reason. */
  private record AllowEntry(String side, LocalDate date, EventType type, String reason) {}

  private record Header(
      String sourceName,
      String libraryVersion,
      LocalDate from,
      LocalDate to,
      Set<EventType> comparedTypes) {}

  /**
   * Compares {@code calendarId} against one reference file, regenerating events with {@code
   * generator} over the range declared by the reference file's header, narrowed to the spec's
   * declared coverage (if any).
   */
  public CrossValidationResult compare(
      String calendarId, ResolvedSpec spec, EventGenerator generator, Path referenceFile)
      throws IOException {
    List<Event> reference = new EventsCsvReader().read(referenceFile, "reference");
    if (reference.isEmpty()) {
      return emptyResult(calendarId, referenceFile);
    }
    Header header = readHeader(referenceFile, reference);
    LocalDate from = header.from();
    LocalDate to = header.to();
    if (spec.coverage() != null) {
      if (spec.coverage().from() != null && spec.coverage().from().isAfter(from)) {
        from = spec.coverage().from();
      }
      if (spec.coverage().to() != null && spec.coverage().to().isBefore(to)) {
        to = spec.coverage().to();
      }
    }
    List<Event> ours = generator.generate(spec, from, to);
    return compare(
        calendarId, ours, spec.weekendPolicy(), from, to, header, reference, referenceFile);
  }

  /**
   * Compares {@code calendarId} using an already-generated list of events (which must cover at
   * least the range declared by the reference file's header). Useful when the caller already has
   * events for a wider range and wants to cross-validate against several reference files without
   * regenerating each time.
   */
  public CrossValidationResult compare(
      String calendarId, List<Event> ours, WeekendPolicy weekendPolicy, Path referenceFile)
      throws IOException {
    List<Event> reference = new EventsCsvReader().read(referenceFile, "reference");
    if (reference.isEmpty()) {
      return emptyResult(calendarId, referenceFile);
    }
    Header header = readHeader(referenceFile, reference);
    return compare(
        calendarId,
        ours,
        weekendPolicy,
        header.from(),
        header.to(),
        header,
        reference,
        referenceFile);
  }

  private CrossValidationResult compare(
      String calendarId,
      List<Event> ours,
      WeekendPolicy weekendPolicy,
      LocalDate from,
      LocalDate to,
      Header header,
      List<Event> reference,
      Path referenceFile)
      throws IOException {
    Set<EventType> comparedTypes = header.comparedTypes();

    Map<String, Event> ourRows = new TreeMap<>();
    for (Event e : ours) {
      if (comparedTypes.contains(e.type())
          && !e.date().isBefore(from)
          && !e.date().isAfter(to)
          && !weekendPolicy.isWeekend(e.date())) {
        ourRows.put(e.date() + "|" + e.type(), e);
      }
    }
    Map<String, Event> refRows = new TreeMap<>();
    for (Event e : reference) {
      if (comparedTypes.contains(e.type())
          && !e.date().isBefore(from)
          && !e.date().isAfter(to)
          && !weekendPolicy.isWeekend(e.date())) {
        refRows.put(e.date() + "|" + e.type(), e);
      }
    }

    Map<String, AllowEntry> allow = loadAllowlist(referenceFile.getParent(), header.sourceName());

    List<String> unexplained = new ArrayList<>();
    Set<String> usedAllow = new HashSet<>();
    int matched = 0;

    for (var entry : ourRows.entrySet()) {
      String id = entry.getKey();
      if (refRows.containsKey(id)) {
        matched++;
        continue;
      }
      String allowKey = "ours|" + id;
      if (allow.containsKey(allowKey)) {
        usedAllow.add(allowKey);
      } else {
        Event e = entry.getValue();
        unexplained.add("ours-only  " + id + "  " + e.description() + " [" + e.key() + "]");
      }
    }
    for (String id : refRows.keySet()) {
      if (ourRows.containsKey(id)) {
        continue; // already counted as matched above
      }
      String allowKey = "theirs|" + id;
      if (allow.containsKey(allowKey)) {
        usedAllow.add(allowKey);
      } else {
        unexplained.add("theirs-only " + id);
      }
    }

    List<String> stale = new ArrayList<>();
    for (String key : allow.keySet()) {
      if (!usedAllow.contains(key)) {
        stale.add("stale allowlist entry (no longer differs): " + key);
      }
    }

    return new CrossValidationResult(
        calendarId,
        header.sourceName(),
        header.libraryVersion(),
        from,
        to,
        comparedTypes,
        matched,
        usedAllow.size(),
        unexplained,
        stale);
  }

  private static CrossValidationResult emptyResult(String calendarId, Path referenceFile) {
    return new CrossValidationResult(
        calendarId,
        sourceNameOf(referenceFile),
        null,
        null,
        null,
        Set.of(),
        0,
        0,
        List.of(),
        List.of());
  }

  private static String sourceNameOf(Path referenceFile) {
    return referenceFile.getFileName().toString().replace(".csv", "");
  }

  private static Header readHeader(Path referenceFile, List<Event> reference) throws IOException {
    LocalDate from = reference.stream().map(Event::date).min(Comparator.naturalOrder()).get();
    LocalDate to = reference.stream().map(Event::date).max(Comparator.naturalOrder()).get();
    Set<EventType> comparedTypes = EnumSet.of(EventType.CLOSED, EventType.EARLY_CLOSE);
    String libraryVersion = null;
    for (String line : Files.readAllLines(referenceFile)) {
      if (line.startsWith("# generated-by:")) {
        libraryVersion = line.substring("# generated-by:".length()).strip();
      } else if (line.startsWith("# range:")) {
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
    return new Header(sourceNameOf(referenceFile), libraryVersion, from, to, comparedTypes);
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
