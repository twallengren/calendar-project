package com.bdc.validation;

import com.bdc.model.Delta;
import com.bdc.model.ResolvedSpec;
import com.bdc.model.SourceCitation;
import com.bdc.source.SourceRegister;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Resolves rule and delta citations and checks canonical local evidence before generation. */
public final class SourceRegisterValidator {
  private final Path sourcesRoot;
  private final List<SourceRegister> registers = new ArrayList<>();
  private final java.util.Map<String, SourceRegister> byCalendar = new java.util.LinkedHashMap<>();

  public SourceRegisterValidator(Path sourcesRoot) throws IOException {
    this.sourcesRoot = sourcesRoot;
    if (!Files.isDirectory(sourcesRoot))
      throw new IOException("Missing sources directory: " + sourcesRoot);
    try (var paths = Files.list(sourcesRoot)) {
      for (Path dir : paths.filter(Files::isDirectory).sorted().toList()) {
        Path register = dir.resolve("register.json");
        if (!Files.isRegularFile(register))
          throw new IOException("Missing canonical source register: " + register);
        SourceRegister loaded = SourceRegister.read(register, sourcesRoot);
        registers.add(loaded);
        byCalendar.put(dir.getFileName().toString(), loaded);
      }
    }
  }

  public void validate(ResolvedSpec spec, ValidationResult result) {
    if (spec.coverage() != null) {
      for (var interval : spec.coverage().quality()) {
        if (interval.quality() == com.bdc.trust.CoverageQuality.VERIFIED) {
          List<com.bdc.chronology.DateRange> support = new ArrayList<>();
          for (String id : interval.evidenceIds())
            for (var register : select(spec, id, result))
              support.addAll(register.support(id, interval.scope()));
          support.sort(java.util.Comparator.comparing(com.bdc.chronology.DateRange::start));
          java.time.LocalDate cursor = interval.from();
          boolean covered = false;
          for (var range : support) {
            if (range.end().isBefore(cursor)) continue;
            if (range.start().isAfter(cursor)) break;
            if (!range.end().isBefore(interval.to())) {
              covered = true;
              break;
            }
            cursor = range.end().plusDays(1);
          }
          if (!covered)
            result.error(
                "UNSUPPORTED_COVERAGE_CLAIM",
                spec.id(),
                "Verified "
                    + interval.scope()
                    + " interval "
                    + interval.from()
                    + ".."
                    + interval.to()
                    + " exceeds cited source support intervals");
        }
        for (String id : interval.evidenceIds()) {
          if (select(spec, id, result).isEmpty())
            result.error(
                "UNRESOLVED_COVERAGE_SOURCE",
                spec.id(),
                "Coverage evidence id is not registered: " + id);
        }
      }
    }
    for (var event : spec.eventSources()) {
      for (SourceCitation source : event.source())
        check(spec, source, spec.id() + "/" + event.key(), result);
    }
    for (Delta delta : spec.deltas()) {
      if (delta.source().isEmpty())
        result.warning("MISSING_DELTA_SOURCE", spec.id(), "Delta has no citation: " + delta);
      for (SourceCitation source : delta.source())
        check(spec, source, spec.id() + "/delta", result);
    }
  }

  private List<SourceRegister> select(ResolvedSpec spec, String id, ValidationResult result) {
    SourceRegister own = byCalendar.get(spec.id());
    if (own != null && own.contains(id)) return List.of(own);
    for (String origin : spec.resolutionChain().reversed()) {
      if (!origin.startsWith("calendar:")) continue;
      SourceRegister parent = byCalendar.get(origin.substring("calendar:".length()));
      if (parent != null && parent.contains(id)) return List.of(parent);
    }
    var candidates = registers.stream().filter(register -> register.contains(id)).toList();
    if (candidates.size() > 1) {
      result.error(
          "AMBIGUOUS_SOURCE", spec.id(), "Source id resolves to unrelated registers: " + id);
      return List.of();
    }
    return candidates;
  }

  private void check(
      ResolvedSpec spec, SourceCitation source, String location, ValidationResult result) {
    if (source.id() != null
        && !source.id().isBlank()
        && select(spec, source.id(), result).isEmpty()) {
      result.error("UNRESOLVED_SOURCE", location, "Source id is not registered: " + source.id());
    }
    if (source.file() != null && !source.file().isBlank()) {
      Path relative = Path.of(source.file());
      Path path =
          source.file().startsWith("sources/")
              ? sourcesRoot.toAbsolutePath().getParent().resolve(relative)
              : sourcesRoot.toAbsolutePath().resolve(relative);
      try {
        if (!Files.isRegularFile(path) || !path.toRealPath().startsWith(sourcesRoot.toRealPath())) {
          result.error(
              "MISSING_SOURCE_FILE",
              location,
              "Evidence file missing or outside source register: " + source.file());
        }
      } catch (IOException e) {
        result.error("MISSING_SOURCE_FILE", location, e.getMessage());
      }
    }
  }
}
