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
      throw new IOException(
          "Reference contains no events and cannot establish coverage: " + referenceFile);
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
      throw new IOException(
          "Reference contains no events and cannot establish coverage: " + referenceFile);
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

    Map<String, Integer> ourRows = countedRows(ours, weekendPolicy, from, to, comparedTypes);
    Map<String, Integer> refRows = countedRows(reference, weekendPolicy, from, to, comparedTypes);
    Map<String, Integer> allow = loadAllowlist(referenceFile.getParent(), header.sourceName());
    List<String> unexplained = new ArrayList<>();
    int matched = 0;
    int allowlisted = 0;
    Set<String> identities = new java.util.TreeSet<>(ourRows.keySet());
    identities.addAll(refRows.keySet());
    for (String id : identities) {
      int oursCount = ourRows.getOrDefault(id, 0);
      int theirsCount = refRows.getOrDefault(id, 0);
      int common = Math.min(oursCount, theirsCount);
      matched += common;
      allowlisted += explain("ours", id, oursCount - common, allow, unexplained);
      allowlisted += explain("theirs", id, theirsCount - common, allow, unexplained);
    }
    List<String> stale = new ArrayList<>();
    allow.forEach(
        (key, count) -> {
          for (int i = 0; i < count; i++) {
            stale.add("stale allowlist entry (no longer differs): " + key);
          }
        });

    return new CrossValidationResult(
        calendarId,
        header.sourceName(),
        header.libraryVersion(),
        from,
        to,
        comparedTypes,
        matched,
        allowlisted,
        unexplained,
        stale);
  }

  private static Map<String, Integer> countedRows(
      List<Event> events,
      WeekendPolicy weekendPolicy,
      LocalDate from,
      LocalDate to,
      Set<EventType> comparedTypes) {
    Map<String, Integer> rows = new TreeMap<>();
    for (Event e : events) {
      if (comparedTypes.contains(e.type())
          && !e.date().isBefore(from)
          && !e.date().isAfter(to)
          && !weekendPolicy.isWeekend(e.date())) {
        String id =
            e.date()
                + "|"
                + e.type()
                + "|"
                + (e.closeTime() == null ? "" : e.closeTime().toString());
        rows.merge(id, 1, Integer::sum);
      }
    }
    return rows;
  }

  private static int explain(
      String side, String id, int count, Map<String, Integer> allow, List<String> unexplained) {
    // Legacy allowlist rows omit close_time. Each row excuses exactly one occurrence;
    // an extra duplicate must still be reviewed, even if its date is already allowlisted.
    String exactKey = side + "|" + id;
    String legacyKey = side + "|" + id.substring(0, id.lastIndexOf('|'));
    int exact = Math.min(count, allow.getOrDefault(exactKey, 0));
    allow.computeIfPresent(exactKey, (k, remaining) -> remaining - exact);
    int legacy = Math.min(count - exact, allow.getOrDefault(legacyKey, 0));
    allow.computeIfPresent(legacyKey, (k, remaining) -> remaining - legacy);
    int used = exact + legacy;
    for (int i = used; i < count; i++) {
      unexplained.add(side + "-only " + id);
    }
    return used;
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
  private static Map<String, Integer> loadAllowlist(Path dir, String sourceName)
      throws IOException {
    Map<String, Integer> result = new TreeMap<>();
    Path path = dir.resolve("allowlist.csv");
    if (!Files.exists(path)) {
      return result;
    }
    boolean withCloseTime = false;
    for (String line : Files.readAllLines(path)) {
      String trimmed = line.strip();
      if (trimmed.startsWith("source,")) {
        withCloseTime = trimmed.equals("source,side,date,type,close_time,reason");
        if (!withCloseTime && !trimmed.equals("source,side,date,type,reason")) {
          throw new IllegalArgumentException("Unknown allowlist header: " + trimmed);
        }
        continue;
      }
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      String[] parts = trimmed.split(",", withCloseTime ? 6 : 5);
      if (parts.length < (withCloseTime ? 6 : 5)) {
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
              parts[withCloseTime ? 5 : 4].strip());
      if (!Set.of("ours", "theirs").contains(entry.side()) || entry.reason().isBlank()) {
        throw new IllegalArgumentException("allowlist row needs a valid side and reason: " + line);
      }
      String closeTime =
          withCloseTime && !parts[4].isBlank()
              ? java.time.LocalTime.parse(parts[4].strip()).toString()
              : "";
      String key =
          entry.side()
              + "|"
              + entry.date()
              + "|"
              + entry.type()
              + (withCloseTime ? "|" + closeTime : "");
      result.merge(key, 1, Integer::sum);
    }
    return result;
  }
}
