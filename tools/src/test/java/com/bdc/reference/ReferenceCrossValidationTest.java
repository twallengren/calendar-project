package com.bdc.reference;

import static org.junit.jupiter.api.Assertions.fail;

import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import com.bdc.validation.CrossValidationResult;
import com.bdc.validation.CrossValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Cross-validates generated calendars against third-party reference data committed under {@code
 * tools/src/test/resources/reference/<CALENDAR>/<source>.csv} (produced by {@code
 * scripts/reference/export_reference_calendars.py}).
 *
 * <p>The comparison itself lives in {@link CrossValidator} (also used by the {@code crossvalidate}
 * CLI command); this test simply fails whenever a result is not {@linkplain
 * CrossValidationResult#isClean() clean} — i.e. any unexplained difference or stale allowlist
 * entry.
 *
 * <p>Weekend days are excluded on both sides (reference files omit them; our weekend definition may
 * legitimately differ from a library that only models Monday-Friday sessions).
 */
@Tag("cross-validation")
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("referenceFiles")
  void matchesReference(Path referenceFile) throws Exception {
    String calendarId = referenceFile.getParent().getFileName().toString();

    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    registry.assertNoLoadErrors();
    ResolvedSpec spec = new SpecResolver(registry).resolve(calendarId);

    CrossValidationResult result =
        new CrossValidator().compare(calendarId, spec, new EventGenerator(), referenceFile);

    if (!result.isClean()) {
      StringBuilder sb =
          new StringBuilder(
              calendarId
                  + " vs "
                  + result.source()
                  + " ("
                  + result.from()
                  + " to "
                  + result.to()
                  + "): "
                  + result.unexplainedCount()
                  + " discrepancies\n");
      Stream.concat(result.unexplainedRows().stream(), result.staleAllowlistRows().stream())
          .limit(200)
          .forEach(p -> sb.append("  ").append(p).append('\n'));
      if (result.unexplainedCount() > 200) {
        sb.append("  ... and ").append(result.unexplainedCount() - 200).append(" more\n");
      }
      fail(sb.toString());
    }
  }
}
